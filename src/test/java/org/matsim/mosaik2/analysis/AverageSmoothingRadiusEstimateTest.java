package org.matsim.mosaik2.analysis;

import lombok.extern.log4j.Log4j2;
import org.junit.Test;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.core.network.NetworkUtils;
import org.matsim.mosaik2.chemistryDriver.Raster;

import java.util.Map;

import static org.junit.Assert.*;

@Log4j2
public class AverageSmoothingRadiusEstimateTest {

    @Test
    public void testCollectR() {

        var bounds = new Raster.Bounds(0,0,110, 110);
        var raster = new Raster(bounds, 2);
        var link = getLink("link", new Coord(40, 50), new Coord(60, 50)); // try to put the link into the center of the grid. Spanning 3 cells from centroid to centroid

        raster.setValueForEachCoordinate((x, y) -> SmoothingRadiusEstimate.f(10000., link.getFromNode().getCoord(), link.getToNode().getCoord(), new Coord(x, y), 2, link.getLength()));

        var rs = AverageSmoothingRadiusEstimate.collectR(raster, Map.of(link, 100000.0));

        for (double r : rs) {
            log.info(r);
        }
    }

    private static Link getLink(String id, Coord from, Coord to) {

        var network = NetworkUtils.createNetwork();
        var fromNode = network.getFactory().createNode(Id.createNodeId(id + "_from"), from);
        var toNode = network.getFactory().createNode(Id.createNodeId(id + "_to"), to);
        return network.getFactory().createLink(Id.createLinkId(id), fromNode, toNode);
    }

}