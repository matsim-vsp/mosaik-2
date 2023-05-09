package org.matsim.mosaik2;

import com.beust.jcommander.Parameter;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.csv.CSVFormat;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.prep.PreparedGeometry;
import org.locationtech.jts.geom.prep.PreparedGeometryFactory;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.core.config.groups.PlanCalcScoreConfigGroup;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.utils.geometry.geotools.MGC;

import java.util.ArrayList;
import java.util.List;

@Log4j2
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

	public static Network loadFilteredNetwork(String networkPath, Geometry bounds) {

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


	public static class SharedSvnARg {

		@Getter
		@Parameter(names = "-sharedSvn", required = true)
		private String sharedSvn;
	}
}