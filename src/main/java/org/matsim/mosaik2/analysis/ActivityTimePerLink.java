package org.matsim.mosaik2.analysis;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.events.ActivityEndEvent;
import org.matsim.api.core.v01.events.ActivityStartEvent;
import org.matsim.api.core.v01.events.handler.ActivityEndEventHandler;
import org.matsim.api.core.v01.events.handler.ActivityStartEventHandler;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.core.events.EventsUtils;
import org.matsim.core.scoring.EventsToActivities;
import org.matsim.mosaik2.analysis.run.CSVUtils;

import java.nio.file.Paths;
import java.util.*;

public class ActivityTimePerLink {
	public static void main(String[] args) {

		var input = new InputArgs();
		JCommander.newBuilder().addObject(input).build().parse(args);
		run(input);
	}

	private static void run(InputArgs input) {

		Map<Id<Link>, Collection<Activity>> link2Activity = new HashMap<>();
		var events2Activities = new EventsToActivities();
		events2Activities.addActivityHandler(act -> {

			var linkId = act.getActivity().getLinkId();
			var activity = act.getActivity();
			link2Activity.computeIfAbsent(linkId, id -> new ArrayList<>()).add(activity);
		});
		var timeHandler = new StartEndTimeHandler();
		var eventsManager = EventsUtils.createEventsManager();
		eventsManager.addHandler(events2Activities);
		eventsManager.addHandler(timeHandler);
		EventsUtils.readEvents(eventsManager, input.eventsFile);
		events2Activities.finish();

		List<LinkActivityTime> result = new ArrayList<>();
		for (var t = timeHandler.firstEventTime; t < timeHandler.lastEventTime; t += input.timeStepSize) {

			var toTime = t + input.timeStepSize;

			for (var entry : link2Activity.entrySet()) {

				var timeSpent = calculateSpentTime(entry.getValue(), t, toTime);
				result.add(new LinkActivityTime(t, entry.getKey(), timeSpent));
			}
		}

		CSVUtils.writeTable(result, Paths.get(input.outputFile), List.of("time", "id", "value"), (p, record) -> {
			CSVUtils.printRecord(p, record.time, record.id, record.value);
		});
	}

	private static double calculateSpentTime(Collection<Activity> activities, double fromTime, double toTime) {

		return activities.stream()
				.filter(act -> CalculateExposure.getStartTime(act) < toTime)
				.filter(act -> CalculateExposure.getEndTime(act) > fromTime)
				.mapToDouble(act -> {
					var start = Math.max(fromTime, CalculateExposure.getStartTime(act));
					var end = Math.min(toTime, CalculateExposure.getEndTime(act));
					return end - start;
				})
				.sum();
	}

	private static class InputArgs {

		@Parameter(names = "-e", required = true)
		private String eventsFile;

		@Parameter(names = "-o", required = true)
		private String outputFile;

		@Parameter(names = "-ts")
		private double timeStepSize = 3600;
	}

	private static class StartEndTimeHandler implements ActivityStartEventHandler, ActivityEndEventHandler {

		private double firstEventTime = Double.POSITIVE_INFINITY;
		private double lastEventTime;

		@Override
		public void handleEvent(ActivityEndEvent event) {
			lastEventTime = event.getTime();
		}

		@Override
		public void handleEvent(ActivityStartEvent event) {
			if (event.getTime() < firstEventTime)
				firstEventTime = event.getTime();
		}
	}

	private record LinkActivityTime(double time, Id<Link> id, double value) {
	}
}