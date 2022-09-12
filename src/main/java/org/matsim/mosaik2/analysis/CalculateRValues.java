package org.matsim.mosaik2.analysis;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import it.unimi.dsi.fastutil.objects.Object2DoubleMap;
import it.unimi.dsi.fastutil.objects.Object2DoubleOpenHashMap;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.prep.PreparedGeometry;
import org.locationtech.jts.geom.prep.PreparedGeometryFactory;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.contrib.analysis.time.TimeBinMap;
import org.matsim.contrib.emissions.events.EmissionEventsReader;
import org.matsim.core.events.EventsUtils;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.utils.collections.Tuple;
import org.matsim.core.utils.geometry.geotools.MGC;
import org.matsim.mosaik2.chemistryDriver.AggregateEmissionsByTimeHandler;
import org.matsim.mosaik2.chemistryDriver.PollutantToPalmNameConverter;
import org.matsim.mosaik2.palm.PalmCsvOutput;
import org.matsim.mosaik2.raster.DoubleRaster;
import org.matsim.mosaik2.raster.ObjectRaster;

import java.nio.file.Paths;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Log4j2
public class CalculateRValues {

	private final InputArgs input;
	private final ObjectRaster<Set<Id<Link>>> linkCache;
	private final TimeBinMap<Object2DoubleMap<Link>> emissions;

	CalculateRValues(InputArgs inputArgs) {
		var info = PalmCsvOutput.readDataInfo(Paths.get(inputArgs.palmFile));
		Network network = loadNetwork(inputArgs.networkFile, info.getRasterInfo().getBounds().toGeometry());
		this.input = inputArgs;
		this.linkCache = createCache(network, info.getRasterInfo().getBounds(), info.getRasterInfo().getCellSize());
		this.emissions = parseEmissions(network, inputArgs, info);
	}

	public static void main(String[] args) {

		var input = new InputArgs();
		JCommander.newBuilder().addObject(input).build().parse(args);

		var calculation = new CalculateRValues(input);
		calculation.run();
	}

	private static ObjectRaster<Set<Id<Link>>> createCache(Network network, ObjectRaster.Bounds bounds, double cellSize) {

		var geomFac = new GeometryFactory();
		var prepGeomFac = new PreparedGeometryFactory();
		var raster = new ObjectRaster<Set<Id<Link>>>(bounds, cellSize);

		log.info("Create Link cache. Create buffer geometries for each link");
		var buffers = network.getLinks().values().parallelStream()
				.map(link -> {
					var lineString = geomFac.createLineString(new Coordinate[]{
							MGC.coord2Coordinate(link.getFromNode().getCoord()),
							MGC.coord2Coordinate(link.getToNode().getCoord())
					});
					// we use a buffer of 1000m, because links wich are further away don't really add emissions to a receiver
					// point.
					return Tuple.of(link.getId(), prepGeomFac.create(lineString.buffer(1000)));
				})
				.collect(Collectors.toList());

		var counter = new AtomicInteger();
		var size = raster.getXLength() * raster.getYLength();

		log.info("Created Buffer geometries. Start creating populating link cache");
		raster.setValueForEachCoordinate((x, y) -> {
			var point = MGC.xy2Point(x, y);

			var currentCount = counter.incrementAndGet();
			if (currentCount % 100000 == 0) {
				log.info("create link cache #" + currentCount + " / " + size);
			}

			return buffers.stream()
					.filter(tuple -> tuple.getSecond().covers(point))
					.map(Tuple::getFirst)
					.collect(Collectors.toSet());
		});

		return raster;
	}

	private static Network loadNetwork(String networkPath, Geometry bounds) {

		var preparedGeometryFactory = new PreparedGeometryFactory();
		var originalNetwork = NetworkUtils.readNetwork(networkPath);

		// use study area with +500m on each side
		log.info("Filter Network for Bounds: " + bounds.toString());
		var preparedBounds = preparedGeometryFactory.create(bounds.buffer(500));
		return originalNetwork.getLinks().values().stream()
				.filter(link -> !link.getId().toString().startsWith("pt"))
				.filter(link -> coversLink(preparedBounds, link))
				.collect(NetworkUtils.getCollector());
	}

	private static boolean coversLink(PreparedGeometry geometry, Link link) {
		return coversCoord(geometry, link.getFromNode().getCoord()) || coversCoord(geometry, link.getToNode().getCoord());
	}

	private static boolean coversCoord(PreparedGeometry geometry, Coord coord) {
		return geometry.covers(MGC.coord2Point(coord));
	}

	private static TimeBinMap<Object2DoubleMap<Link>> parseEmissions(Network network, InputArgs inputArgs, PalmCsvOutput.DataInfo dataInfo) {

		var converter = PollutantToPalmNameConverter.createForSingleSpecies(inputArgs.species);
		var handler = new AggregateEmissionsByTimeHandler(network, converter.getPollutants(), dataInfo.getTimeInterval(), inputArgs.scaleFactor);
		var manager = EventsUtils.createEventsManager();
		manager.addHandler(handler);

		log.info("Starting to parse emission events");
		new EmissionEventsReader(manager).readFile(inputArgs.emissionEventsFile);

		log.info("Start converting collected emissions");
		TimeBinMap<Object2DoubleMap<Link>> result = new TimeBinMap<>(dataInfo.getTimeInterval());
		var handlerMap = handler.getTimeBinMap();

		for (var bin : handlerMap.getTimeBins()) {

			var resultBin = result.getTimeBin(bin.getStartTime());
			var emissionResultMap = resultBin.getValue(Object2DoubleOpenHashMap::new);

			for (var pollutantEntry : bin.getValue().entrySet()) {
				var emissionMap = pollutantEntry.getValue();
				for (var idEntry : emissionMap.object2DoubleEntrySet()) {
					var link = network.getLinks().get(idEntry.getKey());

					// we must use merge here, since pm and pm_non_exhaust map to pm10 in palm
					emissionResultMap.mergeDouble(link, idEntry.getDoubleValue(), Double::sum);
				}
			}
		}
		return result;
	}

	void run() {

		var palmData = PalmCsvOutput.read(Paths.get(input.palmFile));
		var result = new TimeBinMap<DoubleRaster>(palmData.getBinSize(), palmData.getStartTime());

		// do it for only the first bin for debugging
		//var binCount = 0;

		for (var bin : palmData.getTimeBins()) {

			//if (binCount > 1) break; // This will go away eventually, we want all time slices.

			log.info("Calculating R-Values for time: [" + bin.getStartTime() + ", " + (bin.getStartTime() + palmData.getBinSize()) + "]");

			var palmRaster = bin.getValue();
			var size = palmRaster.getYLength() * palmRaster.getXLength();
			var resultBin = result.getTimeBin(bin.getStartTime());
			var resultRaster = resultBin.getValue(() -> new DoubleRaster(palmRaster.getBounds(), palmRaster.getCellSize(), -1));
			var emissionsForTimeSlice = emissions.getTimeBin(bin.getStartTime()).getValue();

			var counter = new AtomicInteger();

			resultRaster.setValueForEachCoordinate((x, y) -> {
				var value = palmRaster.getValueByCoord(x, y);
				if (value <= 0.0) return -2; // short circuit right here, if there is no emission value anyway.

				var receiverPoint = new Coord(x, y);
				var cachedLinks = linkCache.getValueByCoord(x, y);
				var filteredEmissions = emissionsForTimeSlice.object2DoubleEntrySet().stream()
						.filter(entry -> cachedLinks.contains(entry.getKey().getId()))
						.collect(Collectors.toMap(Map.Entry::getKey, Object2DoubleMap.Entry::getDoubleValue, (a, b) -> b, Object2DoubleOpenHashMap::new));

				var r = NumericSmoothingRadiusEstimate.estimateRWithBisect(filteredEmissions, receiverPoint, value);

				var currentCount = counter.incrementAndGet();
				if (currentCount % 100000 == 0) {
					log.info("Calculated " + currentCount + "/" + size + " R-Values. Last value was: " + r);
				}
				return r;
			});
			//binCount++;
		}

		PalmCsvOutput.write(Paths.get(input.outputFile), result);
	}


	@SuppressWarnings("FieldMayBeFinal")
	@AllArgsConstructor
	@NoArgsConstructor
	static class InputArgs {

		@Parameter(names = "-e", required = true)
		private String emissionEventsFile;

		@Parameter(names = "-n", required = true)
		private String networkFile;

		@Parameter(names = "-p", required = true)
		private String palmFile;

		@Parameter(names = "-o", required = true)
		private String outputFile;

		@Parameter(names = "-sp", required = true)
		private String species;

		@Parameter(names = "-s")
		private int scaleFactor = 10;
	}
}