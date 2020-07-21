package org.matsim.mosaik2.prepare;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

public class CreateCounts {

    private static final Logger logger = Logger.getLogger(CreateCounts.class);

    private static final Path networkPath = Paths.get("projects/mosaik-2/matsim-input-files/stuttgartPLZ70173/optimizedNetwork.xml.gz");
    private static final Path longTermCountsRoot = Paths.get("projects/mosaik-2/raw-data/calibration-data/long-term-counts.txt");
    private static final Path longTermCountsIdMapping = Paths.get("projects/mosaik-2/raw-data/calibration-data/countstation-osm-node-matching.csv");


    public static void main(String[] args) throws IOException {

        var input = new InputArguments();
        JCommander.newBuilder().addObject(input).build().parse(args);

        var creator = new NodeMatcher();

        var result = creator.parseNodeMatching(input.sharedSvn + longTermCountsIdMapping);

        var longTerm = new ReadBAStCount();

        var resultLongTerm = longTerm.readBAStData(input.sharedSvn + longTermCountsRoot);

    }

    static class InputArguments {

        @Parameter(names = {"-sharedsvn", "-s"}, description = "Path to the sharedSVN folder", required = true)
        private final String sharedSvn = "";

    }

/*
    private void run() {

        var svnPath = Paths.get(sharedSvn);
        var network = NetworkUtils.readNetwork(svnPath.resolve(networkPath).toString());


        var longTermCounts = new LongTermCountsCreator.Builder()
                .withNetwork(network)
                .withRootDir(svnPath.resolve(longTermCountsRoot).toString())
                .withIdMapping(svnPath.resolve(longTermCountsIdMapping).toString())
                .build()
                .run();

    }


 */


}
