package org.matsim.mosaik2.analysis;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import lombok.extern.log4j.Log4j2;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.contrib.analysis.time.TimeBinMap;
import org.matsim.core.events.EventsUtils;
import org.matsim.core.scoring.EventsToActivities;
import org.matsim.core.utils.collections.QuadTree;
import org.matsim.mosaik2.palm.PalmCsvOutput;
import org.matsim.mosaik2.raster.DoubleRaster;
import org.matsim.mosaik2.raster.ObjectRaster;

import java.nio.file.Paths;
import java.util.*;

@Log4j2
public class CalculateExposure {

	@Parameter(names = "-palmData", required = true)
	private String palmData;

	@Parameter(names = "-eventsData", required = true)
	private String eventsData;

	@Parameter(names = "-output", required = true)
	private String outputPath;

	public static void main(String[] args) {

		var calculator = new CalculateExposure();
		JCommander.newBuilder().addObject(calculator).build().parse(args);
		calculator.run();
	}

	private static double getStartTime(Activity activity) {
		if (activity.getStartTime().isDefined()) return activity.getStartTime().seconds();
		if (activity.getEndTime().isDefined() && activity.getMaximumDuration().isDefined())
			return activity.getEndTime().seconds() - activity.getMaximumDuration().seconds();

		// we don't really know the start time. use 0 here, since we assume that most simulations start no earlier than 0 seconds
		return 0.;
	}

	private static double getEndTime(Activity activity) {
		if (activity.getEndTime().isDefined()) return activity.getEndTime().seconds();
		if (activity.getStartTime().isDefined() && activity.getMaximumDuration().isDefined())
			return activity.getStartTime().seconds() + activity.getMaximumDuration().seconds();

		// we don't really know the end time. Treat as if activity would last forever
		return Double.POSITIVE_INFINITY;
	}

	private void run() {

		var inputPath = Paths.get(palmData);
		var dataInfo = PalmCsvOutput.readDataInfo(inputPath);
		var palmData = PalmCsvOutput.read(inputPath, dataInfo);

		log.info("Create Spacial Index.");
		// create spacial index for palmdata
		QuadTree<Coord> index = new QuadTree<>(
				dataInfo.getRasterInfo().getBounds().getMinX(),
				dataInfo.getRasterInfo().getBounds().getMinY(),
				dataInfo.getRasterInfo().getBounds().getMaxX(),
				dataInfo.getRasterInfo().getBounds().getMaxY()
		);

		var firstRaster = palmData.getTimeBins().iterator().next().getValue();
		firstRaster.forEachCoordinate((x, y, value) -> index.put(x, y, new Coord(x, y)));


		// 2.  a. go through each palm time slice and find activities starting during that slice
		//	   b. store those activities as "currently performed" sorted by earliest end time (Priority Queue - inverse ordering)
		//     c. go through all currently performed activities and calculate duration for this time slice - remove activities which finsh during this time slice
		//     d. store exposure value for raster tile

		// 1. map all activities onto a raster cell
		// create data structure that holds the activity info and populate it
		var activityRaster = new ObjectRaster<Tile>(dataInfo.getRasterInfo().getBounds(), dataInfo.getRasterInfo().getCellSize());
		activityRaster.setValueForEachIndex((xi, yi) -> new Tile());

		log.info("Map all activities onto raster tiles for which PALM calculated a concentration value.");
		var events2Activities = new EventsToActivities();
		events2Activities.addActivityHandler(act -> {
			if (activityRaster.getBounds().covers(act.getActivity().getCoord()) && !act.getActivity().getType().contains(" interaction")) {
				var closestTile = index.getClosest(act.getActivity().getCoord().getX(), act.getActivity().getCoord().getY());
				var tile = activityRaster.getValueByCoord(closestTile.getX(), closestTile.getY());
				tile.add(act.getActivity());
			}
		});
		var manager = EventsUtils.createEventsManager();
		manager.addHandler(events2Activities);
		EventsUtils.readEvents(manager, eventsData);

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
				tile.pushAllActToPerforming(endTime);
				var spentTime = tile.calculateSpentTime(startTime, endTime);
				var concentration = emissions.getValueByIndex(xi, yi);
				return spentTime * concentration; // this is the exposure value
			});

			resultMap.getTimeBin(startTime).setValue(exposureRaster);
		}
		log.info("Finished exposure calculation.");
		PalmCsvOutput.write(Paths.get(outputPath), resultMap);
	}

	private static class Tile {
		private static final Comparator<Activity> startTimeComparator = (act1, act2) -> Double.compare(getStartTime(act2), getStartTime(act1));

		private final Queue<Activity> activities = new PriorityQueue<>(startTimeComparator);
		private final Collection<Activity> currentlyPerforming = new ArrayList<>();
		private final double exposureValue = 0;

		void add(Activity activity) {
			activities.add(activity);
		}

		boolean nextActStartsBefore(double time) {
			return !activities.isEmpty() && getStartTime(activities.peek()) < time;
		}

		//b. store those activities as "currently performed" sorted by earliest end time (Priority Queue - inverse ordering)
		void pushAllActToPerforming(double beforeTime) {

			while (nextActStartsBefore(beforeTime)) {
				var act = activities.poll();
				currentlyPerforming.add(act);
			}
		}

		//     c. go through all currently performed activities and calculate duration for this time slice - remove activities which finsh during this time slice
		double calculateSpentTime(double fromTime, double toTime) {

			var accDurations = 0;
			for (var it = currentlyPerforming.iterator(); it.hasNext(); ) {
				var act = it.next();
				var startTime = Math.max(fromTime, getStartTime(act));
				var endTime = toTime;
				if (getEndTime(act) < toTime) {
					endTime = getEndTime(act);
					it.remove();
				}
				var duration = endTime - startTime;
				accDurations += duration;
			}
			return accDurations;
		}
	}
}