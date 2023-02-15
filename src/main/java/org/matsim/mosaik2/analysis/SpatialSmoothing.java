package org.matsim.mosaik2.analysis;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.google.common.util.concurrent.AtomicDouble;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.locationtech.jts.geom.Geometry;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.application.options.ShpOptions;
import org.matsim.contrib.analysis.time.TimeBinMap;
import org.matsim.contrib.emissions.events.EmissionEventsReader;
import org.matsim.core.events.EventsUtils;
import org.matsim.core.utils.gis.ShapeFileReader;
import org.matsim.mosaik2.DoubleToDoubleFunction;
import org.matsim.mosaik2.chemistryDriver.AggregateEmissionsByTimeHandler;
import org.matsim.mosaik2.chemistryDriver.PollutantToPalmNameConverter;
import org.matsim.mosaik2.palm.PalmCsvOutput;
import org.matsim.mosaik2.raster.DoubleRaster;
import org.matsim.mosaik2.raster.ObjectRaster;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.operation.TransformException;

import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Log4j2
@RequiredArgsConstructor
public class SpatialSmoothing {

	private final String species;
	private final Path emissionEvents;
	private final Path networkPath;
	private final Path boundsFile;
	private final Path buildingsFile;

	private final Path palmFile;
	private final Path outputFile;
	private final double r;
	private final int cellSize;
	private final int timeBinSize;
	private final double scaleFactor;

	private final DoubleToDoubleFunction fittingFunction;

	public static void main(String[] args) throws FactoryException, TransformException {

		var inputArgs = new InputArgs();
		JCommander.newBuilder().addObject(inputArgs).build().parse(args);

		var fittingFunction = getFittingFunction(inputArgs.species, inputArgs.fitting);

		new SpatialSmoothing(
				inputArgs.species, inputArgs.emissionEvents, inputArgs.networkPath, inputArgs.boundsFile, inputArgs.buildingsFile,
				inputArgs.palmFile, inputArgs.outputFile, inputArgs.r, inputArgs.cellSize, inputArgs.timeBinSize, inputArgs.scaleFactor,
				fittingFunction
		).run();
	}

	private static DoubleToDoubleFunction getFittingFunction(String species, String fitting) {
		return switch (species) {
			case "PM10" -> getFittingFunctionPM10(fitting);
			case "NO2" -> getFittingFunctionNO2(fitting);
			default -> throw new RuntimeException(species + " not supported.");
		};
	}

	private static DoubleToDoubleFunction getFittingFunctionPM10(String fitting) {
		return switch (fitting) {
			case "linear" -> x -> 0.01386929 * x + 2.597027e-6;
			case "quad" -> x -> -25.50912 * x * x + 0.02219018 * x + 2.517183e-06;
			case "none" -> x -> x;
			default -> throw new RuntimeException(fitting + " is not supported.");
		};
	}

	private static DoubleToDoubleFunction getFittingFunctionNO2(String fitting) {
		return switch (fitting) {
			case "linear" -> x -> 0.01444081 * x + 4.003327e-5;
			case "quad" -> x -> -3.15757 * x * x + 0.0205 * x + 3.970247e-5;
			case "none" -> x -> x;
			default -> throw new RuntimeException(fitting + " is not supported.");
		};
	}

	/**
	 * This aligns the two rasters, so that all raster tiles are on the same grid. This is necessary, because we want
	 * the smoothed and the palm data on the same grid.
	 */
	DoubleRaster.Bounds createAlignedBounds(DoubleRaster.Bounds bounds, DoubleRaster.Bounds alignTo, double cellSize) {

		var howManyCellsX = Math.round((bounds.getMinX() - alignTo.getMinX()) / cellSize);
		var alignedMinX = alignTo.getMinX() + (howManyCellsX * cellSize);
		var lengthX = bounds.getMaxX() - bounds.getMinX();
		var alignedMaxX = alignedMinX + lengthX;
		var howManyCellsY = Math.round((bounds.getMinY() - alignTo.getMinY()) / cellSize);
		var alignedMinY = alignTo.getMinY() + (howManyCellsY * cellSize);
		var lengthY = bounds.getMaxY() - bounds.getMinY();
		var alignedMaxY = alignedMinY + lengthY;

		return new DoubleRaster.Bounds(alignedMinX, alignedMinY, alignedMaxX, alignedMaxY);
	}

	void run() throws FactoryException, TransformException {

		var berlinGeometry = ShapeFileReader.getAllFeatures(boundsFile.toString()).stream()
				.map(simpleFeature -> (Geometry) simpleFeature.getDefaultGeometry())
				.findAny()
				.orElseThrow(() -> new RuntimeException("Couldn't find feature for berlin"));
		var berlinBounds = new ObjectRaster.Bounds(berlinGeometry);
		var palmInfo = PalmCsvOutput.readDataInfo(palmFile);
		var bounds = createAlignedBounds(berlinBounds, palmInfo.getRasterInfo().getBounds(), palmInfo.getRasterInfo().getCellSize());

		var network = CalculateRValues.loadNetwork(networkPath.toString(), berlinGeometry);

		log.info("Creating spatial link index");
		// with a distance of 3*r, 99% of emissions of a link get distributet into the raster.
		var linkIndex = new SpatialIndex(network, r * 3, berlinGeometry);
		var linkIndexRaster = new ObjectRaster<Set<Id<Link>>>(bounds, cellSize);

		log.info("Creating raster cache with link ids.");
		linkIndexRaster.setValueForEachCoordinate(linkIndex::query);

		log.info("Creating spatial building index");
		var options = new ShpOptions(buildingsFile, "EPSG:4326", Charset.defaultCharset());
		var buildingIndex = options.createIndex("EPSG:25833", "");

		log.info("Creating raster with building data.");
		var rasteredBuildings = new DoubleRaster(bounds, cellSize);
		rasteredBuildings.setValueForEachCoordinate((x, y) -> buildingIndex.contains(new Coord(x, y)) ? -1 : 0);

		var manager = EventsUtils.createEventsManager();
		var converter = PollutantToPalmNameConverter.createForSingleSpecies(species);
		var handler = new AggregateEmissionsByTimeHandler(network, converter.getPollutants(), timeBinSize, scaleFactor);
		manager.addHandler(handler);

		log.info("Start parsing emission events.");
		new EmissionEventsReader(manager).readFile(emissionEvents.toString());

		log.info("Sort collected emissions by link");
		TimeBinMap<Map<Id<Link>, LinkEmission>> emissionByLink = new TimeBinMap<>(timeBinSize);
		handler.getTimeBinMap().getTimeBins().forEach(bin -> {

			var resultBin = emissionByLink.getTimeBin(bin.getStartTime());
			var emissionResultMap = resultBin.computeIfAbsent(HashMap::new);

			var emissionByPollutant = bin.getValue();
			for (var pollutantEntry : emissionByPollutant.entrySet()) {
				var emissionMap = pollutantEntry.getValue();
				for (var idEntry : emissionMap.object2DoubleEntrySet()) {

					var link = network.getLinks().get(idEntry.getKey());
					emissionResultMap.computeIfAbsent(link.getId(), id -> new LinkEmission(link)).add(idEntry.getDoubleValue());
				}
			}
		});

		log.info("Start calculating concentrations.");
		TimeBinMap<DoubleRaster> rasterTimeSeries = new TimeBinMap<>(timeBinSize);

		// the normalization factor gives the ratio between cell area and area under the gauss function
		// cell area = cellSize^2 (obviously), area under function = PI * r^2. This is described in Kickhoefer 2014
		var normalizationFactor = cellSize * cellSize / (Math.PI * r * r);

		for (var bin : emissionByLink.getTimeBins()) {

			var startTime = bin.getStartTime();
			log.info("Calculating concentrations for: [" + startTime + ", " + (bin.getStartTime() + timeBinSize) + "]");
			var raster = new DoubleRaster(bounds, cellSize);
			var linkEmissions = bin.getValue();

			// f(x) = -4.82253e-7*(x-43200)^2+1000 use curve which starts at 100m and has its peak at 1000m at noon and then
			//                            goes back to 100 at midnight as approximation for boundary layer height into
			//                            which we release the pollutant
			//var heightBoundaryLayer = -4.82253e-7 * Math.pow(startTime - 43200, 2) + 1000;
			var heightBoundaryLayer = cellSize;
			var cellVolume = cellSize * cellSize * heightBoundaryLayer;

			raster.setValueForEachCoordinate((x, y) -> {

				// this means this point is covered by a building
				if (rasteredBuildings.getValueByCoord(x, y) < 0) return -1;

				// instead of calling sumf in the NumericSmoothing class we re-implement the logic here.
				// this saves us one stream/collect in the inner loop here.
				var linkIds = linkIndexRaster.getValueByCoord(x, y);

				return linkIds.stream()
						.map(linkEmissions::get)
						.filter(Objects::nonNull)
						.mapToDouble(linkEmission -> {
							var link = linkEmission.link;
							var weight = NumericSmoothingRadiusEstimate.calculateWeight(
									link.getFromNode().getCoord(),
									link.getToNode().getCoord(),
									new Coord(x, y),
									link.getLength(),
									r
							);
							var emission = linkEmission.value;
							var concentration = emission * weight * normalizationFactor / cellVolume;
							return fittingFunction.applyAsDouble(concentration);
						})
						.sum();
			});

			rasterTimeSeries.getTimeBin(bin.getStartTime()).setValue(raster);
		}

		var linkEmissionSum = sumUpLinkEmissions(emissionByLink);
		var rasterEmissionSum = sumUpRasterEmissions(rasterTimeSeries);

		log.info("link emissions: " + linkEmissionSum + ", raster emissions: " + rasterEmissionSum);

		PalmCsvOutput.write(outputFile, rasterTimeSeries);
	}

	private static double sumUpLinkEmissions(TimeBinMap<Map<Id<Link>, LinkEmission>> emissionByLink) {

		return emissionByLink.getTimeBins().stream()
				.map(TimeBinMap.TimeBin::getValue)
				.flatMap(idMap -> idMap.values().stream())
				.mapToDouble(entry -> entry.value)
				.sum();
	}

	private static double sumUpRasterEmissions(TimeBinMap<DoubleRaster> emissionRaster) {

		var sum = new AtomicDouble();
		emissionRaster.getTimeBins().stream()
				.map(TimeBinMap.TimeBin::getValue)
				.forEach(raster -> raster.forEachIndex((xi, yi, value) -> sum.getAndAdd(value)));
		return sum.get();
	}

	@SuppressWarnings("FieldMayBeFinal")
	private static class InputArgs {

		@Parameter(names = "-s", required = true)
		private String species;
		@Parameter(names = "-e", required = true)
		private Path emissionEvents;
		@Parameter(names = "-n", required = true)
		private Path networkPath;
		@Parameter(names = "-bounds", required = true)
		private Path boundsFile;
		@Parameter(names = "-buildings", required = true)
		private Path buildingsFile;
		@Parameter(names = "-palm", required = true)
		private Path palmFile;
		@Parameter(names = "-o", required = true)
		private Path outputFile;
		@Parameter(names = "-r")
		private double r = 11;
		@Parameter(names = "-c")
		private int cellSize = 10;
		@Parameter(names = "-t")
		private int timeBinSize = 3600;
		@Parameter(names = "-f")
		private double scaleFactor = 10;
		@Parameter(names = "-fitting")
		private String fitting = "none";

		private InputArgs() {
		}
	}

	@RequiredArgsConstructor
	private static class LinkEmission {

		private final Link link;
		private double value;


		void add(double emission) {
			this.value += emission;
		}
	}
}