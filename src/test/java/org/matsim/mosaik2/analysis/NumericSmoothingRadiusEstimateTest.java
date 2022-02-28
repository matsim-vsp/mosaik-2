package org.matsim.mosaik2.analysis;

import it.unimi.dsi.fastutil.objects.Object2DoubleOpenHashMap;
import lombok.extern.java.Log;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.csv.CSVFormat;
import org.junit.Ignore;
import org.junit.Test;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.contrib.analysis.spatial.SpatialInterpolation;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.utils.geometry.geotools.MGC;
import org.matsim.mosaik2.chemistryDriver.Raster;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;

import static org.junit.Assert.*;

@Log4j2
public class NumericSmoothingRadiusEstimateTest {

    private final double le = 20;
    private final double E = 10;
    private final double R = 23;
    private final Coord from = new Coord(40, 50);
    private final Coord to = new Coord(60, 50);
    private final Coord receiverPoint = new Coord(60,20);

    @Test
    public void testWeight() {

        // The old code calculates a weight for each receiver point before applying the emissions. Since we need the emissions
        // in our derived function, we pass it directly
        var result = NumericSmoothingRadiusEstimate.calculateWeight(from, to, receiverPoint, le, R);
        var oldResult = SpatialInterpolation.calculateWeightFromLine(MGC.coord2Coordinate(from), MGC.coord2Coordinate(to), MGC.coord2Coordinate(receiverPoint), R);

        assertEquals(oldResult, result, 10E-10);
    }

    /**
     * Used this to plot the weighting function
     */
    @Test@Ignore
    public void testWeightWithPlot() {

        try (var writer = Files.newBufferedWriter(Paths.get("C:\\Users\\Janekdererste\\Desktop\\smoothing-estimates\\weight-results.csv")); var printer =CSVFormat.DEFAULT.withHeader("x", "value").print(writer)) {

            for (int i = 0; i < 100; i++) {

                var xj = NumericSmoothingRadiusEstimate.calculateWeight(from, to, new Coord(60, 10 + i), le, R) * E;
                printer.printRecord(i, xj);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
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

        final var emissions = new Object2DoubleOpenHashMap<>(Map.of(getLink("link", from, to), E));

        // first calculate xj with f - this is the forward equation
        var xj = NumericSmoothingRadiusEstimate.calculateWeight(from, to, receiverPoint, le, R) * E;

        // now, use xj in estimate R. This should yield the same R which we have used for the forward pass
        var estimatedR = NumericSmoothingRadiusEstimate.estimateR(emissions, receiverPoint, xj, 10);

        assertEquals(R, estimatedR, 10E-3);
    }

    @Test
    public void testEstimateRWithBisect() {

        final var emissions = new Object2DoubleOpenHashMap<>(Map.of(getLink("link", from, to), E));

        // first calculate xj with f - this is the forward equation
        var xj = NumericSmoothingRadiusEstimate.calculateWeight(from, to, receiverPoint, le, R) * E;

        var estimatedR = NumericSmoothingRadiusEstimate.estimateRWithBisect(emissions, receiverPoint, xj);

        assertEquals(R, estimatedR, 10E-3);

        // test other values
        var coordX2 = new Coord(23, 54);
        var x2 = NumericSmoothingRadiusEstimate.calculateWeight(from, to, coordX2, le, R) * E;
        var estimatedR2 = NumericSmoothingRadiusEstimate.estimateRWithBisect(emissions, coordX2, x2);

        assertEquals(R, estimatedR2, 10E-3);
    }

    /**
     * Used this one to plot the function
     */
    @Test@Ignore
    public void testWithPlot() {

        // first calcuate xj with f - this is the forward equation
        var xj = NumericSmoothingRadiusEstimate.calculateWeight(from, to, receiverPoint, le, R) * E;

        try (var writer = Files.newBufferedWriter(Paths.get("C:\\Users\\Janekdererste\\Desktop\\smoothing-estimates\\F-results.csv")); var printer =CSVFormat.DEFAULT.withHeader("R", "value").print(writer)) {
            for(int i = -100; i < 100; i++) {
                var result = NumericSmoothingRadiusEstimate.F(from, to, receiverPoint, le, i, E, xj);
                printer.printRecord(i, result);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static Link getLink(String id, Coord from, Coord to) {

        var network = NetworkUtils.createNetwork();
        var fromNode = network.getFactory().createNode(Id.createNodeId(id + "_from"), from);
        var toNode = network.getFactory().createNode(Id.createNodeId(id + "_to"), to);
        return network.getFactory().createLink(Id.createLinkId(id), fromNode, toNode);
    }
}