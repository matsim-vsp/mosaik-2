package org.matsim.mosaik2.prepare;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.counts.Count;
import org.matsim.counts.Counts;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

public class CreateCounts {

    private static final Logger logger = Logger.getLogger(CreateCounts.class);

    private static final Path longTermCountsRootFederalRoad = Paths.get("projects/mosaik-2/raw-data/calibration-data/long-term-counts-federal-road.txt");
    private static final Path longTermCountsRootHighway = Paths.get("projects/mosaik-2/raw-data/calibration-data/long-term-counts-highway.txt");
    private static final Path longTermCountsIdMapping = Paths.get("projects/mosaik-2/raw-data/calibration-data/countstation-osm-node-matching.csv");

    public static void main(String[] args) throws IOException {

        logger.info("Program starts!");

        var input = new InputArguments();
        JCommander.newBuilder().addObject(input).build().parse(args);

        var matching = new NodeMatcher();
        var matchingResult = matching.parseNodeMatching(input.sharedSvn + longTermCountsIdMapping);

        logger.info("Finished with matching nodes.");

        if (false) {

            // GetCountDataAndCreateCounts()
            var getCounts = new GetCountDataAndCreateCounts();
            var getCountsResult = getCounts.countData(input.sharedSvn + longTermCountsRootFederalRoad, input.sharedSvn + longTermCountsRootHighway, (HashMap) matchingResult);

            logger.info("Finish");

        } else {

            // GetCountData()
            var longTerm = new GetCountData();
            var longTermResult = longTerm.countData(input.sharedSvn + longTermCountsRootFederalRoad, input.sharedSvn + longTermCountsRootHighway, (HashMap) matchingResult);

            Map<String, Count<Link>> countsResult = new HashMap<>();

            for (Map.Entry<String, GetCountData.CountingData> longTermBAStCounts : longTermResult.entrySet()) {

                Count<Link> count;
                var counts = new Counts<Link>();
                counts.setYear(2018);

                String stationID = longTermBAStCounts.getKey();
                GetCountData.CountingData value = longTermBAStCounts.getValue();

                count = counts.createAndAddCount(Id.createLinkId(value.getLinkID()), stationID);

                for (int i = 0; i < 24; i++) {

                    count.createVolume((i + 1), value.getSpecificHour(i));

                }

                countsResult.put(stationID, count);

                System.out.println(longTermBAStCounts.getValue());

            }

            logger.info("All Counts were imported!");

        }

    }

    static class InputArguments {

        @Parameter(names = {"-sharedsvn", "-s"}, description = "Path to the sharedSVN folder", required = true)
        private final String sharedSvn = "";

    }

}
