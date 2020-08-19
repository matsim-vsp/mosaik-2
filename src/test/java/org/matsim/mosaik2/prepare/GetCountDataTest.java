package org.matsim.mosaik2.prepare;

import org.junit.Ignore;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class GetCountDataTest {

    @Test
    @Ignore
    public void countData() throws IOException {

        String svnPath = "/Users/friedrich/SVN/shared-svn/";

        String matchingNode = "projects/mosaik-2/raw-data/calibration-data/countstation-osm-node-matching.csv";
        String countPath1 = "projects/mosaik-2/raw-data/calibration-data/long-term-counts-federal-road.txt";
        String countPath2 = "projects/mosaik-2/raw-data/calibration-data/long-term-counts-highway.txt";

        var matching = new NodeMatcher();
        var matchingResult = matching.parseNodeMatching(svnPath + matchingNode);

        var longTerm = new GetCountData();
        var longTermResult = longTerm.countData(svnPath + countPath1, svnPath + countPath2, matchingResult);

        var resultTest8018_R1 = longTermResult.get("8018_R1");
        assertEquals("8018_R1", resultTest8018_R1.getStationId());
        assertEquals("49720477", resultTest8018_R1.getLinkId());
        assertEquals(Integer.valueOf(334), resultTest8018_R1.getResult().get("01"));
        assertEquals(Integer.valueOf(2333), resultTest8018_R1.getResult().get("11"));
        assertEquals(Integer.valueOf(1251), resultTest8018_R1.getResult().get("22"));

        assertTrue(Files.exists(Paths.get(svnPath + matchingNode)));
        assertTrue(Files.exists(Paths.get(svnPath + countPath1)));
        assertTrue(Files.exists(Paths.get(svnPath + countPath2)));
    }
}