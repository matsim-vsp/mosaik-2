package org.matsim.mosaik2.prepare;

import org.apache.log4j.Logger;
import org.junit.Test;

import java.io.IOException;
import java.util.HashMap;

import static org.junit.Assert.assertEquals;

public class GetCountDataTest {

    private static final Logger logger = Logger.getLogger(NodeMatcherTest.class);

    @Test
    public void countData() throws IOException {

        // Setup
        String matchingPath = "/Users/friedrich/SVN/shared-svn/projects/mosaik-2/raw-data/calibration-data/countstation-osm-node-matching.csv";
        NodeMatcher matchingObject = new NodeMatcher();
        String countPath1 = "/Users/friedrich/SVN/shared-svn/projects/mosaik-2/raw-data/calibration-data/long-term-counts-federal-road.txt";
        String countPath2 = "/Users/friedrich/SVN/shared-svn/projects/mosaik-2/raw-data/calibration-data/long-term-counts-highway.txt";
        GetCountData countObject = new GetCountData();

        // Excecution
        var matchingResult = matchingObject.parseNodeMatching(matchingPath);
        var countResult = countObject.countData(countPath1, countPath2, (HashMap) matchingResult);

        // Überprüfen
        var resultTest10 = countResult.get("8054_R1");
        // assertEquals(117, countResult.size());
        assertEquals("8054_R1", resultTest10.getStationID());
        assertEquals("81363955", resultTest10.getLinkID());
        assertEquals("354", resultTest10.getSpecificHour(0));
        assertEquals("156", resultTest10.getCountHour());

    }

}