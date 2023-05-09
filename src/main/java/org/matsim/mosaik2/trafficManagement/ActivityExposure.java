package org.matsim.mosaik2.trafficManagement;

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
import org.matsim.core.scoring.PersonExperiencedActivity;
import org.matsim.core.utils.collections.QuadTree;
import org.matsim.core.utils.geometry.geotools.MGC;
import org.matsim.mosaik2.raster.AbstractRaster;
import org.matsim.mosaik2.raster.DoubleRaster;
import org.matsim.mosaik2.raster.ObjectRaster;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.referencing.FactoryException;

import java.nio.file.Path;
import java.util.*;

@Log4j2
public class ActivityExposure {

	public static TimeBinMap<Map<String, DoubleRaster>> calculate(Path eventsFile, TimeBinMap<Map<String, DoubleRaster>> emissions) {

		var exampleRaster = emissions.getTimeBins().iterator().next().getValue().values().iterator().next();
		var events2Act = new EventsToActivities();
		var actHandler = new Handler(exampleRaster.getBounds(), exampleRaster.getCellSize());
		events2Act.addActivityHandler(actHandler);
		var manager = EventsUtils.createEventsManager();
		manager.addHandler(events2Act);
		EventsUtils.readEvents(manager, eventsFile.toString());
		events2Act.finish();

		log.info("Start exposure calculation");

		var resultMap = new TimeBinMap<Map<String, DoubleRaster>>(emissions.getBinSize(), emissions.getStartTime());
		for (var bin : emissions.getTimeBins()) {

			var emissionsBySpecies = bin.getValue();
			var startTime = bin.getStartTime();
			var endTime = startTime + emissions.getBinSize();
			Map<String, DoubleRaster> timeSliceData = new HashMap<>();

			log.info("Calculating Exposure for time slice: [" + startTime + ", " + endTime + "]");
			for (var entry : emissionsBySpecies.entrySet()) {

				var concentrations = entry.getValue();
				var species = entry.getKey();
				var exposures = new DoubleRaster(concentrations.getBounds(), concentrations.getCellSize());
				exposures.setValueForEachIndex((xi, yi) -> {
					var tile = actHandler.activityRaster.getValueByIndex(xi, yi);
					if (tile == null) return -1;

					var spentTime = tile.calculateSpentTime(startTime, endTime);
					var concentration = concentrations.getValueByIndex(xi, yi);
					return spentTime * concentration;
				});
				timeSliceData.put(species, exposures);
			}
			resultMap.getTimeBin(bin.getStartTime()).setValue(timeSliceData);
		}
		log.info("Finished exposure calculation.");
		return resultMap;
	}

	static class Handler implements EventsToActivities.ActivityHandler {

		private static final GeometryFactory gf = new GeometryFactory();

		private static final SimpleFeatureBuilder featureBuilder = new SimpleFeatureBuilder(getFeatureType());
		private final ObjectRaster<Tile> activityRaster;

		// use matsim quad tree because it has getClosest
		private final QuadTree<Coord> spatialIndex;

		private final List<SimpleFeature> lines = new ArrayList<>();

		Handler(AbstractRaster.Bounds bounds, double cellSize) {
			this.activityRaster = new ObjectRaster<>(bounds, cellSize);
			this.spatialIndex = new QuadTree<>(
					bounds.getMinX(),
					bounds.getMinY(),
					bounds.getMaxX(),
					bounds.getMaxY()
			);
		}

		@Override
		public void handleActivity(PersonExperiencedActivity expAct) {
			if (activityRaster.getBounds().covers(expAct.getActivity().getCoord()) && !expAct.getActivity().getType().contains(" interaction")) {

				var act = expAct.getActivity();
				var closest = spatialIndex.getClosest(act.getCoord().getX(), act.getCoord().getY());
				lines.add(createFeature(act.getCoord(), closest, expAct.getAgentId(), lines.size()));
				addActToRaster(closest, act);
			}
		}

		private void addActToRaster(Coord tileCoord, Activity act) {
			var tile = activityRaster.getValueByCoord(tileCoord.getX(), tileCoord.getY());
			if (tile == null) {
				tile = new Tile();
				activityRaster.setValueForCoord(tileCoord.getX(), tileCoord.getY(), tile);
			}
			tile.add(act);
		}

		private static SimpleFeature createFeature(Coord from, Coord to, Id<Person> agentId, int featureId) {
			var line = gf.createLineString(new Coordinate[]{
					MGC.coord2Coordinate(from),
					MGC.coord2Coordinate(to)
			});
			var feature = featureBuilder.buildFeature(Integer.toString(featureId));
			feature.setAttribute("agentId", agentId.toString());
			feature.setAttribute("the_geom", line); // somehow setDefaultGeometry won't do the trick. This internal string works though 🙄
			return feature;
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

	public static double getStartTime(Activity activity) {
		if (activity.getStartTime().isDefined()) return activity.getStartTime().seconds();
		if (activity.getEndTime().isDefined() && activity.getMaximumDuration().isDefined())
			return activity.getEndTime().seconds() - activity.getMaximumDuration().seconds();

		// we don't really know the start time. use 0 here, since we assume that most simulations start no earlier than 0 seconds
		return Double.NEGATIVE_INFINITY;
	}

	public static double getEndTime(Activity activity) {
		if (activity.getEndTime().isDefined()) return activity.getEndTime().seconds();
		if (activity.getStartTime().isDefined() && activity.getMaximumDuration().isDefined())
			return activity.getStartTime().seconds() + activity.getMaximumDuration().seconds();

		// we don't really know the end time. Treat as if activity would last forever
		return Double.POSITIVE_INFINITY;
	}
}