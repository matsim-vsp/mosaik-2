package org.matsim.mosaik2.analysis;

import it.unimi.dsi.fastutil.objects.Object2DoubleOpenHashMap;
import org.junit.Test;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.contrib.analysis.spatial.SpatialInterpolation;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.utils.geometry.geotools.MGC;

import java.util.Map;

import static org.junit.Assert.*;

public class NumericSmoothingRadiusEstimateTest {

    private final double le = 20;
    private final double E = 10;
    private final double R = 23;
    private final Coord from = new Coord(50, 10);
    private final Coord to = new Coord(70, 10);
    private final Coord receiverPoint = new Coord(60,20);

    @Test
    public void testWeight() {

        // The old code calculates a weight for each receiver point before applying the emissions. Since we need the emissions
        // in our derived function, we pass it directly
        var result = NumericSmoothingRadiusEstimate.calculateWeight(from, to, receiverPoint, le, R);
        var oldResult = SpatialInterpolation.calculateWeightFromLine(MGC.coord2Coordinate(from), MGC.coord2Coordinate(to), MGC.coord2Coordinate(receiverPoint), R);

        assertEquals(oldResult, result, 10E-10);
    }

    @Test
    public void testF() {

        // first calcuate xj with f - this is the forward equation
        var xj = NumericSmoothingRadiusEstimate.calculateWeight(from, to, receiverPoint, le, R) * E;

        // now, use xj with F, which should yield 0
        var result = NumericSmoothingRadiusEstimate.F(from, to, receiverPoint, le, R, E, xj);

        assertEquals(0, result, 10E-10);
    }

    @Test
    public void testEstimateR() {

        final var emissions = new Object2DoubleOpenHashMap<Link>(Map.of(getLink("link", from, to), E));

        // first calculate xj with f - this is the forward equation
        var xj = NumericSmoothingRadiusEstimate.calculateWeight(from, to, receiverPoint, le, R) * E;

        // now, use xj in estimate R. This should yield the same R which we have used for the forward pass
        var estimatedR = NumericSmoothingRadiusEstimate.estimateR(emissions, receiverPoint, xj, 25);

        assertEquals(R, estimatedR, 10E-6);
    }

    private static Link getLink(String id, Coord from, Coord to) {

        var network = NetworkUtils.createNetwork();
        var fromNode = network.getFactory().createNode(Id.createNodeId(id + "_from"), from);
        var toNode = network.getFactory().createNode(Id.createNodeId(id + "_to"), to);
        return network.getFactory().createLink(Id.createLinkId(id), fromNode, toNode);
    }
}