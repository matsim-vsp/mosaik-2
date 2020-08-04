package org.matsim.mosaik2.prepare;

import org.junit.Test;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.counts.Count;
import org.matsim.counts.Counts;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

public class GetCountDataAndCreateCountsTest extends GetCountDataAndCreateCounts {

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
        var longTerm = new GetCountData();
        var matchingResult = matchingObject.parseNodeMatching(matchingPath);
        var longTermResult = longTerm.countData(countPath1, countPath2, (HashMap) matchingResult);

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

        }

        // Überprüfen
        /*
        var resultTest8045_R1 = countsResult.get("8045_R1");
        assertEquals("103697098", resultTest8045_R1.getId());
        assertEquals(Integer.valueOf(530), resultTest8045_R1.getVolume(0));
        assertEquals(Integer.valueOf(899), resultTest8045_R1.getVolume(23));
        assertEquals(Integer.valueOf(3201), resultTest8045_R1.getVolume(10));

        var resultTest8045_R2 = countsResult.get("8045_R2");
        assertEquals("6480509", resultTest8045_R2.getId());
        assertEquals(Integer.valueOf(598), resultTest8045_R2.getVolume(0));
        assertEquals(Integer.valueOf(817), resultTest8045_R2.getVolume(23));

        var resultTest8018_R1 = countsResult.get("8018_R1");
        assertEquals("49720477", resultTest8018_R1.getId());
        assertEquals(Integer.valueOf(1929), resultTest8018_R1.getVolume(0));
        assertEquals(Integer.valueOf(3157), resultTest8018_R1.getVolume(23));

        var resultTest8018_R2 = countsResult.get("8018_R2");
        assertEquals("8489595", resultTest8018_R2.getId());
        assertEquals(Integer.valueOf(1954), resultTest8018_R2.getVolume(0));
        assertEquals(Integer.valueOf(2601), resultTest8018_R2.getVolume(23));
        assertEquals(Integer.valueOf(2449), resultTest8018_R2.getVolume(4));
         */

    }

}