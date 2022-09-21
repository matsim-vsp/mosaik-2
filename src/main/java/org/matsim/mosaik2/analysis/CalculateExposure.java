package org.matsim.mosaik2.analysis;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.geotools.referencing.CRS;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Person;
import org.matsim.contrib.analysis.time.TimeBinMap;
import org.matsim.core.events.EventsUtils;
import org.matsim.core.scoring.EventsToActivities;
import org.matsim.core.utils.collections.QuadTree;
import org.matsim.core.utils.geometry.geotools.MGC;
import org.matsim.core.utils.gis.ShapeFileWriter;
import org.matsim.mosaik2.palm.PalmCsvOutput;
import org.matsim.mosaik2.raster.DoubleRaster;
import org.matsim.mosaik2.raster.ObjectRaster;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.referencing.FactoryException;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Log4j2
@AllArgsConstructor
public class CalculateExposure {

	private final Path palmPath;
	private final Path eventsData;
	private final Path outputPath;

	public static void main(String[] args) {

		var inputArgs = new InputArgs();
		JCommander.newBuilder().addObject(inputArgs).build().parse(args);
		new CalculateExposure(
				Paths.get(inputArgs.palmData), Paths.get(inputArgs.eventsData), Paths.get(inputArgs.outputPath)
		).run();
	}

	private static double getStartTime(Activity activity) {
		if (activity.getStartTime().isDefined()) return activity.getStartTime().seconds();
		if (activity.getEndTime().isDefined() && activity.getMaximumDuration().isDefined())
			return activity.getEndTime().seconds() - activity.getMaximumDuration().seconds();

		// we don't really know the start time. use 0 here, since we assume that most simulations start no earlier than 0 seconds
		return Double.NEGATIVE_INFINITY;
	}

	private static double getEndTime(Activity activity) {
		if (activity.getEndTime().isDefined()) return activity.getEndTime().seconds();
		if (activity.getStartTime().isDefined() && activity.getMaximumDuration().isDefined())
			return activity.getStartTime().seconds() + activity.getMaximumDuration().seconds();

		// we don't really know the end time. Treat as if activity would last forever
		return Double.POSITIVE_INFINITY;
	}

	private static SimpleFeatureType getFeatureType() {
		var b = new SimpleFeatureTypeBuilder();
		b.setName("movement");
		b.add("agentId", String.class);
		b.add("the_geom", LineString.class);
		try {
			b.setCRS(CRS.decode("EPSG:25833"));
		} catch (FactoryException e) {
			throw new RuntimeException(e);
		}
		return b.buildFeatureType();
	}

	private static SimpleFeature createFeature(Coord from, Coord to, Id<Person> agentId, int featureId, GeometryFactory f, SimpleFeatureBuilder b) {
		var line = f.createLineString(new Coordinate[]{
				MGC.coord2Coordinate(from),
				MGC.coord2Coordinate(to)
		});
		var feature = b.buildFeature(Integer.toString(featureId));
		feature.setAttribute("agentId", agentId.toString());
		feature.setAttribute("the_geom", line); // somehow setDefaultGeometry won't do the trick. This internal string works though 🙄
		return feature;
	}

	private static void addActToRaster(ObjectRaster<Tile> raster, Coord tileCoord, Activity act) {
		var tile = raster.getValueByCoord(tileCoord.getX(), tileCoord.getY());
		if (tile == null) {
			tile = new Tile();
			raster.setValueForCoord(tileCoord.getX(), tileCoord.getY(), tile);
		}
		tile.add(act);
	}

	private static Path getMovementDataPath(Path exposureOutput) {

		var name = exposureOutput.getFileName().toString();
		name = name.substring(0, name.lastIndexOf('.'));
		var shapeFileName = name + ".shp";
		return exposureOutput.resolveSibling(shapeFileName);
	}

	void run() {

		var dataInfo = PalmCsvOutput.readDataInfo(palmPath);
		var palmData = PalmCsvOutput.read(palmPath, dataInfo);

		log.info("Create Spacial Index.");
		// create spacial index for palmdata
		QuadTree<Coord> index = new QuadTree<>(
				dataInfo.getRasterInfo().getBounds().getMinX(),
				dataInfo.getRasterInfo().getBounds().getMinY(),
				dataInfo.getRasterInfo().getBounds().getMaxX(),
				dataInfo.getRasterInfo().getBounds().getMaxY()
		);

		var firstRaster = palmData.getTimeBins().iterator().next().getValue();
		firstRaster.forEachCoordinate((x, y, value) -> {
			if (value >= 0.0)
				index.put(x, y, new Coord(x, y));
		});

		// 1. map all activities onto a raster cell
		// create data structure that holds the activity info and populate it
		var activityRaster = new ObjectRaster<Tile>(dataInfo.getRasterInfo().getBounds(), dataInfo.getRasterInfo().getCellSize());
		//activityRaster.setValueForEachIndex((xi, yi) -> new Tile());

		log.info("Map all activities onto raster tiles for which PALM calculated a concentration value.");
		var fact = new GeometryFactory();
		var featureBuilder = new SimpleFeatureBuilder(getFeatureType());
		List<SimpleFeature> lines = new ArrayList<>();
		var events2Activities = new EventsToActivities();
		events2Activities.addActivityHandler(act -> {

			if (activityRaster.getBounds().covers(act.getActivity().getCoord()) && !act.getActivity().getType().contains(" interaction")) {
				// find the closes raster tile for which PALM has created emissions (not in buildings)
				var closestTile = index.getClosest(act.getActivity().getCoord().getX(), act.getActivity().getCoord().getY());
				// For Debugging: Create a line between activity and tile
				lines.add(createFeature(act.getActivity().getCoord(), closestTile, act.getAgentId(), lines.size(), fact, featureBuilder));
				// put the activity into the corresponding raster tile.
				addActToRaster(activityRaster, closestTile, act.getActivity());
			}
		});
		var manager = EventsUtils.createEventsManager();
		manager.addHandler(events2Activities);
		// this actually invokes the events parsing and the sorting activities into tiles method above
		EventsUtils.readEvents(manager, eventsData.toString());
		events2Activities.finish(); // this is necessary to flush activities with end time undefined

		// write the feature list after all activities are parsed
		ShapeFileWriter.writeGeometries(lines, getMovementDataPath(outputPath).toString());

		log.info("Start exposure calculation");
		var resultMap = new TimeBinMap<DoubleRaster>(palmData.getBinSize(), palmData.getStartTime());
		for (var bin : palmData.getTimeBins()) {

			var exposureRaster = new DoubleRaster(dataInfo.getRasterInfo().getBounds(), dataInfo.getRasterInfo().getCellSize());
			var emissions = bin.getValue();
			var startTime = bin.getStartTime();
			var endTime = startTime + palmData.getBinSize();

			log.info("Calculating Exposure for time slice: [" + startTime + ", " + endTime + "]");
			exposureRaster.setValueForEachIndex((xi, yi) -> {
				var tile = activityRaster.getValueByIndex(xi, yi);
				if (tile == null) return -1;

				var spentTime = tile.calculateSpentTime(startTime, endTime);
				var concentration = emissions.getValueByIndex(xi, yi);
				return spentTime * concentration; // this is the exposure value
			});

			resultMap.getTimeBin(startTime).setValue(exposureRaster);
		}
		log.info("Finished exposure calculation.");
		PalmCsvOutput.write(outputPath, resultMap);
	}

	private static class InputArgs {
		@Parameter(names = "-palmData", required = true)
		private String palmData;

		@Parameter(names = "-eventsData", required = true)
		private String eventsData;

		@Parameter(names = "-output", required = true)
		private String outputPath;

		private InputArgs() {
		}
	}

	// make package private for testing
	@Getter
	static class Tile {

		private final Collection<Activity> activities = new ArrayList<>();

		void add(Activity activity) {
			activities.add(activity);
		}

		double calculateSpentTime(double fromTime, double toTime) {

			return activities.stream()
					.filter(act -> getStartTime(act) < toTime)
					.filter(act -> getEndTime(act) > fromTime)
					.mapToDouble(act -> {
						var start = Math.max(fromTime, getStartTime(act));
						var end = Math.min(toTime, getEndTime(act));
						return end - start;
					})
					.sum();
		}
	}
}