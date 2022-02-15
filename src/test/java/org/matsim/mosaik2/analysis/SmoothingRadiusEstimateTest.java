package org.matsim.mosaik2.analysis;

import org.apache.commons.math3.special.Erf;
import org.junit.Test;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.contrib.analysis.spatial.SpatialInterpolation;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.utils.geometry.geotools.MGC;

import java.util.Map;

import static org.junit.Assert.*;

public class SmoothingRadiusEstimateTest {

    private final double le = 100;
    private final double E = 10;
    private final double R = 1;
    private final Coord from = new Coord(10, 10);
    private final Coord to = new Coord(110, 10);
    private final Coord receiverPoint = new Coord(60,20);

    @Test
    public void testF() {

        // The old code calculates a weight for each receiver point before applying the emissions. Since we need the emissions
        // in our derived function, we pass it directly
        var result = SmoothingRadiusEstimate.f(E, from, to, receiverPoint, R, le);
        var oldResult = SpatialInterpolation.calculateWeightFromLine(MGC.coord2Coordinate(from), MGC.coord2Coordinate(to), MGC.coord2Coordinate(receiverPoint), R);

        assertEquals(oldResult * E, result, 1.0E-45);
    }

    @Test
    public void testFDerived() {

        var result = SmoothingRadiusEstimate.fDerived(E, from, to, receiverPoint, R, le);

        // we don't have old code for the derived function. This compares the result to a calculation by hand.
        assertEquals(1.325E-42, result, 1.0E-42);
    }

    @Test
    public void estimateR() {

        // now, take the emissions and values of the previous example for estimating R
        var emissions = Map.of(getLink("link", from, to), E);
        var xj = 6.593662989359226E-45;

        var result = SmoothingRadiusEstimate.estimateR(emissions, receiverPoint, 20, xj);

        assertEquals(R, result, 0.1);
    }

    private static Link getLink(String id, Coord from, Coord to) {

        var network = NetworkUtils.createNetwork();
        var fromNode = network.getFactory().createNode(Id.createNodeId(id + "_from"), from);
        var toNode = network.getFactory().createNode(Id.createNodeId(id + "_to"), to);
        return network.getFactory().createLink(Id.createLinkId(id), fromNode, toNode);
    }
}