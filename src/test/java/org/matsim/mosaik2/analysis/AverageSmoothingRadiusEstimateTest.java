package org.matsim.mosaik2.analysis;

import it.unimi.dsi.fastutil.objects.Object2DoubleArrayMap;
import it.unimi.dsi.fastutil.objects.Object2DoubleOpenHashMap;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.junit.Test;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.core.network.NetworkUtils;
import org.matsim.mosaik2.chemistryDriver.Raster;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;

import static org.junit.Assert.*;

@Log4j2
public class AverageSmoothingRadiusEstimateTest {

    @Test
    public void testCollectR() {

        Coord from = new Coord(40, 50);
        Coord to = new Coord(60, 50);
        double E = 10;
        double R = 23;
        final var emissions = new Object2DoubleOpenHashMap<>(Map.of(getLink("link", from, to), E));
        var raster = new Raster(new Raster.Bounds(0,0,110,110), 10);

        raster.setValueForEachCoordinate((x, y) -> E * NumericSmoothingRadiusEstimate.calculateWeight(from, to, new Coord(x, y), 20, R));

        AverageSmoothingRadiusEstimate
                .collectR(raster, emissions)
                .forEachCoordinate((x, y, value) -> assertEquals(R, value, 10E-3));
    }

    private static Link getLink(String id, Coord from, Coord to) {

        var network = NetworkUtils.createNetwork();
        var fromNode = network.getFactory().createNode(Id.createNodeId(id + "_from"), from);
        var toNode = network.getFactory().createNode(Id.createNodeId(id + "_to"), to);
        return network.getFactory().createLink(Id.createLinkId(id), fromNode, toNode);
    }
}