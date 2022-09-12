package org.matsim.mosaik2;

import com.beust.jcommander.Parameter;
import lombok.Getter;
import org.apache.commons.csv.CSVFormat;
import org.matsim.core.config.groups.PlanCalcScoreConfigGroup;

import java.util.ArrayList;
import java.util.List;

public class Utils {

	public static List<PlanCalcScoreConfigGroup.ActivityParams> createTypicalDurations(String type, long minDurationInSeconds, long maxDurationInSeconds, long durationDifferenceInSeconds) {

		List<PlanCalcScoreConfigGroup.ActivityParams> result = new ArrayList<>();
		for (long duration = minDurationInSeconds; duration <= maxDurationInSeconds; duration += durationDifferenceInSeconds) {
			final PlanCalcScoreConfigGroup.ActivityParams params = new PlanCalcScoreConfigGroup.ActivityParams(type + "_" + duration + ".0");
			params.setTypicalDuration(duration);
			result.add(params);
		}
		return result;
	}

	public static CSVFormat createReadFormat() {
		return CSVFormat.DEFAULT.builder()
				.setSkipHeaderRecord(true)
				.setHeader()
				.build();
	}

	public static CSVFormat createWriteFormat(String... header) {
		return CSVFormat.DEFAULT.builder()
				.setHeader(header)
				.setSkipHeaderRecord(false)
				.build();
	}

	public static class SharedSvnARg {

		@Getter
		@Parameter(names = "-sharedSvn", required = true)
		private String sharedSvn;
	}
}