package org.matsim.mosaik2.analysis;

import it.unimi.dsi.fastutil.objects.Object2DoubleOpenHashMap;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.csv.CSVFormat;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.contrib.analysis.spatial.SpatialInterpolation;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.utils.geometry.CoordUtils;
import org.matsim.core.utils.geometry.geotools.MGC;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;

import static org.junit.Assert.*;

@Log4j2
@RunWith(Parameterized.class)
public class NumericSmoothingRadiusEstimateTest {

    private final double le;
    private final double E;
    private final double R;
    private final Coord from;
    private final Coord to;
    private final Coord receiverPoint;

    public NumericSmoothingRadiusEstimateTest(double le, double E, double R, Coord from, Coord to, Coord receiverPoint) {
        this.le = le;
        this.E = E;
        this.R = R;
        this.from = from;
        this.to = to;
        this.receiverPoint = receiverPoint;
    }

    @Parameterized.Parameters
    public static Collection<Object> primeNumbers() {
        return Arrays.asList(
                new Object[][] {
                        // some chosen number with which we did our hand calculations as well
                        {20, 10, 10, new Coord(40, 50), new Coord(60, 50), new Coord(50, 60)},
                        // some arbitrary odd numbers, with different link length than euclidean distance for example
                        {1000, 10003, 150, new Coord(23, 23), new Coord(54, 110), new Coord(74, 98)},
                        // R = 1 is an edge case as well
                        {20, 10, 1, new Coord(40, 50), new Coord(60, 50), new Coord(50, 60)},
                        // having 0 Emissions is an edge case we should handle as well
                        {20, 0, 59, new Coord(40, 50), new Coord(60, 50), new Coord(50, 60)}
                }
        );
    }

    @Test
    public void testWeight() {

        // compare the weight function to the old implementation. The old implementation uses different interfaces, which I want to avoid here. Hence, the slightly
        // varying implementation.

        // Use eucledian distance here, since the old implementation does this as well. (Which is not correct)
        var length = CoordUtils.calcEuclideanDistance(from, to);
        var result = NumericSmoothingRadiusEstimate.calculateWeight(from, to, receiverPoint, length, R);
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
    public void testEstimateR() {

        final var emissions = new Object2DoubleOpenHashMap<>(Map.of(getLink("link", from, to, le), E));

        // first calculate xj with f - this is the forward equation
        var xj = NumericSmoothingRadiusEstimate.calculateWeight(from, to, receiverPoint, le, R) * E;

        // now, use xj in estimate R. This should yield the same R which we have used for the forward pass
        var estimatedR = NumericSmoothingRadiusEstimate.estimateR(emissions, receiverPoint, xj, R + 5);

        // This code doesn't work for zero emissions. This will be filtered elsewhere.
        if (xj == 0)
            assertEquals(Double.NaN, estimatedR, 0.001);
        else
            assertEquals(R, estimatedR, 10E-5);
    }

    @Test
    public void testEstimateRWithBisect() {

        final var emissions = new Object2DoubleOpenHashMap<>(Map.of(
                getLink("link-1", from, to, le), E
        ));

        // first calculate xj with f - this is the forward equation
        var xj = NumericSmoothingRadiusEstimate.calculateWeight(from, to, receiverPoint, le, R) * E;

        try {
            var estimatedR = NumericSmoothingRadiusEstimate.estimateRWithBisect(emissions, receiverPoint, xj);
            assertEquals(R, estimatedR, 10E-5);
        } catch (Exception e) {
            if (xj != 0) throw new RuntimeException(e);
            // otherwise, this exception is expected
        }
    }

    @Test
    public void testEstimateRWithBisect2Links() {

        final var emissions = new Object2DoubleOpenHashMap<>(Map.of(
                getLink("link-1", new Coord(10, 10), new Coord(90, 10), 80), E,
                getLink("link-2", new Coord(10, 50), new Coord(90, 50), 80), E
        ));
        final var receiverPoint = new Coord(50, 30);

        var xj = emissions.object2DoubleEntrySet().stream()
                .mapToDouble(entry -> NumericSmoothingRadiusEstimate.calculateWeight(
                        entry.getKey().getFromNode().getCoord(),
                        entry.getKey().getToNode().getCoord(),
                        receiverPoint,
                        entry.getKey().getLength(),
                        R) * entry.getDoubleValue())
                .sum();

        try {
            var estimatedR = NumericSmoothingRadiusEstimate.estimateRWithBisect(emissions, receiverPoint, xj);
            assertEquals(R, estimatedR, 10E-5);
        } catch (Exception e) {
            if (xj != 0) throw new RuntimeException(e);
            // otherwise, this exception is expected
        }
    }

    /**
     * Used this one to plot the function
     */
    @Test@Ignore
    public void testWithPlot() {

        // first calcuate xj with f - this is the forward equation
        var xj = NumericSmoothingRadiusEstimate.calculateWeight(from, to, receiverPoint, le, R) * E;

        try (var writer = Files.newBufferedWriter(Paths.get("C:\\Users\\Janekdererste\\Desktop\\smoothing-estimates\\F-results-" + R + ".csv")); var printer =CSVFormat.DEFAULT.withHeader("R", "value").print(writer)) {
            for(int i = -1000; i < 1000; i++) {
                var result = E * NumericSmoothingRadiusEstimate.calculateWeight(from, to, receiverPoint, le, i) - xj;
                printer.printRecord(i, result);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static Link getLink(String id, Coord from, Coord to, double length) {

        var network = NetworkUtils.createNetwork();
        var fromNode = network.getFactory().createNode(Id.createNodeId(id + "_from"), from);
        var toNode = network.getFactory().createNode(Id.createNodeId(id + "_to"), to);
        var link =  network.getFactory().createLink(Id.createLinkId(id), fromNode, toNode);
        link.setLength(length);
        return link;
    }
}