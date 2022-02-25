package org.matsim.mosaik2.analysis;

import org.junit.Test;
import org.matsim.api.core.v01.Coord;
import org.matsim.contrib.analysis.spatial.SpatialInterpolation;
import org.matsim.core.utils.geometry.geotools.MGC;

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
}