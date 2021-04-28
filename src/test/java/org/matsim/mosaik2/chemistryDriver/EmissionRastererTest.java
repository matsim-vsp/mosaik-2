package org.matsim.mosaik2.chemistryDriver;

import lombok.extern.log4j.Log4j2;
import org.junit.Test;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.contrib.analysis.time.TimeBinMap;
import org.matsim.contrib.emissions.Pollutant;
import org.matsim.core.network.NetworkUtils;
import org.matsim.mosaik2.utils.TestUtils;

import java.util.Map;

import static org.junit.Assert.*;

@Log4j2
public class EmissionRastererTest {

    /**
     * This test has a 3x12 cell grid ranging from (-10,-10) to (110,10). It has a single link
     * which reaches from (5,0) to (95,0). This means the link covers 10 grid cells. The expected
     * values for each covered cell is emissionValue / numberOfCells / cellArea. In our case this
     * would be: 1000 / 10 / 100 = 1
     */
    @Test
    public void raster() {

        var network = singleLinkNetwork();
        var bounds = new Raster.Bounds(-10, -10, 110, 10);
        var cellSize = 10;
        var linkEmission = Map.of(Id.createLinkId("link"), 1000.0);
        var linkEmissionByPollutant = Map.of(Pollutant.NO2, linkEmission);
        var timeBinMap = new TimeBinMap<Map<Pollutant, Map<Id<Link>, Double>>>(10);
        timeBinMap.getTimeBin(1).setValue(linkEmissionByPollutant);

        var result = EmissionRasterer.raster(timeBinMap, network, bounds, cellSize);

        assertEquals(1, result.getTimeBins().size());
        var timeBin = result.getTimeBin(1);
        assertTrue(timeBin.hasValue());
        var map = timeBin.getValue();
        assertEquals(1, map.size());
        assertTrue(map.containsKey(Pollutant.NO2));
        var raster = map.get(Pollutant.NO2);
        assertEquals(bounds, raster.getBounds());

        raster.forEachCoordinate((x, y, value) -> {
            if (y == 0.0 && x >= 0 && x < 100) {
                assertEquals(1.0, value, 0.00000001);
            } else {
                assertEquals(0.0, value, 0.00000001);
            }
        });
    }

    private Network singleLinkNetwork() {

        var network = NetworkUtils.createNetwork();
        var fromCoord = new Coord(5,0);
        var toCoord = new Coord(95, 0);
        var fromNode = NetworkUtils.createAndAddNode(network, Id.createNodeId("from"), fromCoord);
        var toNode = NetworkUtils.createAndAddNode(network, Id.createNodeId("to"), toCoord);
        var link = network.getFactory().createLink(Id.createLinkId("link"), fromNode, toNode);
        network.addLink(link);

        return network;
    }
}