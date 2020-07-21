package org.matsim.mosaik2.prepare;

import org.apache.log4j.Logger;
import org.junit.Test;

import java.io.IOException;

public class NodeMatcherTest {

    private static final Logger logger = Logger.getLogger(NodeMatcherTest.class);

    @Test
    public void parseNodeMatching() throws IOException {

        String path = "/Users/friedrich/SVN/shared-svn/projects/mosaik-2/raw-data/calibration-data/countstation-osm-node-matching.csv";
        NodeMatcher testObject = new NodeMatcher();
        var result = testObject.parseNodeMatching(path);

        /*

        for (ParseBAStCount.MatchedCount record : result) {

            logger.info(record.getStationID() + ", " + record.getFromID() + ", " + record.getToID() + ", " + record.getLinkID());

        }

         */

    }

}