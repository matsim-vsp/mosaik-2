package org.matsim.mosaik2.analysis;

import lombok.extern.log4j.Log4j2;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.math3.special.Erf;
import org.junit.Rule;
import org.junit.Test;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.contrib.analysis.spatial.SpatialInterpolation;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.utils.geometry.geotools.MGC;
import org.matsim.mosaik2.chemistryDriver.Raster;
import org.matsim.testcases.MatsimTestUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

@Log4j2
public class SmoothingRadiusEstimateTest {

    @Rule
    public MatsimTestUtils testUtils = new MatsimTestUtils();

    private final double le = 20;
    private final double E = 10;
    private final double R = 23;
    private final Coord from = new Coord(50, 10);
    private final Coord to = new Coord(70, 10);
    private final Coord receiverPoint = new Coord(60,20);

    @Test
    public void testF() {

        // The old code calculates a weight for each receiver point before applying the emissions. Since we need the emissions
        // in our derived function, we pass it directly
        var result = SmoothingRadiusEstimate.f(E, from, to, receiverPoint, R, le);
        var oldResult = SpatialInterpolation.calculateWeightFromLine(MGC.coord2Coordinate(from), MGC.coord2Coordinate(to), MGC.coord2Coordinate(receiverPoint), R);

        assertEquals(oldResult * E, result, 1.0E-10);
    }

    @Test
    public void testWithRaster() {

        var bounds = new Raster.Bounds(0,0,110, 110);
        var raster = new Raster(bounds, 2);
        var link = getLink("link", new Coord(40, 50), new Coord(60, 50)); // try to put the link into the center of the grid. Spanning 3 cells from centroid to centroid

        raster.setValueForEachCoordinate((x, y) -> SmoothingRadiusEstimate.f(10000., link.getFromNode().getCoord(), link.getToNode().getCoord(), new Coord(x, y), 10, link.getLength()));

        try(var writer = Files.newBufferedWriter(Paths.get(testUtils.getOutputDirectory()).resolve("values.csv")); var printer = CSVFormat.DEFAULT.withHeader("x", "y", "value").print(writer)) {

            raster.forEachCoordinate((x, y, value) -> {
                try {
                    log.info(x + " " + y + " " + value + " " + SmoothingRadiusEstimate.f(10000000000., link.getFromNode().getCoord(), link.getToNode().getCoord(), new Coord(x, y), 10, link.getLength()));
                    printer.printRecord(x, y, value);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        var network = NetworkUtils.createNetwork();
        network.addNode(link.getFromNode());
        network.addNode(link.getToNode());
        network.addLink(link);
        NetworkUtils.writeNetwork(network, testUtils.getOutputDirectory() + "network.xml");
    }

    @Test
    public void testFMultipleValues() {

        var receiverPoints = List.of(new Coord(60, 20), new Coord(60, 10), new Coord(60, 0), new Coord(70, 20), new Coord(70, 10), new Coord(70, 0), new Coord(75, 20), new Coord(75, 10), new Coord(75, 0));

        for(var coord : receiverPoints) {
            var result = SmoothingRadiusEstimate.f(1E200, from, to, coord, R, 20);
            log.info(coord.toString() + " Weight old: " + SpatialInterpolation.calculateWeightFromLine(MGC.coord2Coordinate(from), MGC.coord2Coordinate(to), MGC.coord2Coordinate(coord), R));

        }
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