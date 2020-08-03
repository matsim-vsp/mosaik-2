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

        // Setup for Windows
        String matchingPath = "C:\\Users\\Friedrich\\shared-svn\\projects\\mosaik-2\\raw-data\\calibration-data\\countstation-osm-node-matching.csv";
        NodeMatcher matchingObject = new NodeMatcher();
        String countPath1 = "C:\\Users\\Friedrich\\shared-svn\\projects\\mosaik-2\\raw-data\\calibration-data\\long-term-counts-federal-road.txt";
        String countPath2 = "C:\\Users\\Friedrich\\shared-svn\\projects\\mosaik-2\\raw-data\\calibration-data\\long-term-counts-highway.txt";
        GetCountData countObject = new GetCountData();

        // Setup for Mac OS
        /*
        String matchingPath = "projects/mosaik-2/raw-data/calibration-data/countstation-osm-node-matching.csv";
        NodeMatcher matchingObject = new NodeMatcher();
        String countPath1 = "projects/mosaik-2/raw-data/calibration-data/long-term-counts-federal-road.txt";
        String countPath2 = "projects/mosaik-2/raw-data/calibration-data/long-term-counts-highway.txt";
        GetCountData countObject = new GetCountData();
         */

        // Excecution
        var matchingResult = matchingObject.parseNodeMatching(matchingPath);
        var countResult = countObject.countData(countPath1, countPath2, (HashMap) matchingResult);

        // Überprüfen
        var resultTest8045_R1 = countResult.get("8045_R1");
        assertEquals("8045_R1", resultTest8045_R1.getStationID());
        assertEquals("103697098", resultTest8045_R1.getLinkID());
        assertEquals(Integer.valueOf(530), resultTest8045_R1.getSpecificHour(0));
        assertEquals(Integer.valueOf(899), resultTest8045_R1.getSpecificHour(23));
        assertEquals(Integer.valueOf(3201), resultTest8045_R1.getSpecificHour(10));

        var resultTest8045_R2 = countResult.get("8045_R2");
        assertEquals("8045_R2", resultTest8045_R2.getStationID());
        assertEquals("6480509", resultTest8045_R2.getLinkID());
        assertEquals(Integer.valueOf(598), resultTest8045_R2.getSpecificHour(0));
        assertEquals(Integer.valueOf(817), resultTest8045_R2.getSpecificHour(23));

        var resultTest8018_R1 = countResult.get("8018_R1");
        assertEquals("8018_R1", resultTest8018_R1.getStationID());
        assertEquals("49720477", resultTest8018_R1.getLinkID());
        assertEquals(Integer.valueOf(1929), resultTest8018_R1.getSpecificHour(0));
        assertEquals(Integer.valueOf(3157), resultTest8018_R1.getSpecificHour(23));

        var resultTest8018_R2 = countResult.get("8018_R2");
        assertEquals("8018_R2", resultTest8018_R2.getStationID());
        assertEquals("8489595", resultTest8018_R2.getLinkID());
        assertEquals(Integer.valueOf(1954), resultTest8018_R2.getSpecificHour(0));
        assertEquals(Integer.valueOf(2601), resultTest8018_R2.getSpecificHour(23));
        assertEquals(Integer.valueOf(2449), resultTest8018_R2.getSpecificHour(4));

    }

}