package org.matsim.mosaik2.chemistryDriver;

import de.topobyte.osm4j.core.access.OsmInputException;
import lombok.extern.slf4j.Slf4j;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.NetworkWriter;
import org.matsim.api.core.v01.network.Node;
import org.matsim.contrib.osm.networkReader.SupersonicOsmNetworkReader;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.utils.geometry.CoordinateTransformation;
import org.matsim.core.utils.geometry.geotools.MGC;
import org.matsim.core.utils.geometry.transformations.TransformationFactory;
import org.matsim.mosaik2.utils.TestUtils;
import org.matsim.testcases.MatsimTestUtils;

import java.io.FileNotFoundException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.Assert.*;
import static org.matsim.mosaik2.chemistryDriver.NetworkUnsimplifier.LENGTH_FRACTION_KEY;

@Slf4j
public class NetworkUnsimplifierTest {

    @Rule
    public MatsimTestUtils testUtils = new MatsimTestUtils();

    private static void transformNode(Node node, CoordinateTransformation transformation) {

        var transformed = transformation.transform(node.getCoord());
        node.setCoord(transformed);
    }

    private static boolean isCoveredBy(Link link, Geometry geometry) {
        return geometry.covers(MGC.coord2Point(link.getFromNode().getCoord())) || geometry.covers(MGC.coord2Point(link.getToNode().getCoord()));
    }

    private static Geometry createBoundingBox(Coord origin, double cellSize) {

        // these are taken from the ernst-reuter-example we've received from the FU-Berlin
        final var originX = origin.getX(); // southern boundary
        final var originY = origin.getY(); // western boundary
        final var numberOfCells = 36;

        final var maxX = originX + numberOfCells * cellSize;
        final var maxY = originY + numberOfCells * cellSize;

        return new GeometryFactory().createPolygon(new Coordinate[]{
                new Coordinate(originX, originY), new Coordinate(originX, maxY),
                new Coordinate(maxX, maxY), new Coordinate(originX, maxY),
                new Coordinate(originX, originY) // close ring
        });
    }

    @Test
    public void testLinkMappingFromGeometryAttributes() {

        var network = TestUtils.createSingleLinkNetwork(new Coord(0,0), new Coord(100, 0), List.of(new Coord(0, 10), new Coord(100, 10)));
        var link = network.getLinks().get(Id.createLinkId("link"));

        var result = NetworkUnsimplifier.unsimplifyNetwork(network);

        assertEquals(network.getLinks().size(), result.size());
        assertTrue(result.containsKey(link.getId()));

        var resultSegments = result.get(link.getId());
        assertEquals(3, resultSegments.size());

        for (var segment : resultSegments) {
            if (segment.getId().equals(Id.createLinkId("link_1"))) {
                assertEquals(100, segment.getLength(), 0.0001);
            }
            else {
                assertEquals(10, segment.getLength(), 0.0001);
            }
            assertNotNull(segment.getAttributes().getAttribute(LENGTH_FRACTION_KEY));
            assertEquals(segment.getLength() / link.getLength(), (double)segment.getAttributes().getAttribute(LENGTH_FRACTION_KEY), 0.001);
        }
    }

    @Test
    public void testAndorra() throws FileNotFoundException, OsmInputException {

        // in some cases the unsimplification doesn't properly work. This is the case for paths which have a loop on their end with an intersectio in-between
        // this is a rare edge case which I only observed for hiking trails in the mountains. Therefore, the ways with the following ids are not tested
        // in the final assertion
        final var exceptionsForAssertion = Set.of(6185090L, 369541317L, 6620940L, 25767814L, 284249739L, 681494098L, 24915566L);

        var inputFile = testUtils.getInputDirectory() + "andorra.osm.pbf";
        var transformation = TransformationFactory.getCoordinateTransformation("EPSG:4326", "EPSG:25833");

        // read in a simplified network as it would be the case for a network used in a normal simulation
        var simplifiedNetwork = new SupersonicOsmNetworkReader.Builder()
                .setCoordinateTransformation(transformation)
                .build()
                .read(inputFile);

        // read in a network but with no simplification. This network is taken as comparison
        var completeNetwork = new SupersonicOsmNetworkReader.Builder()
                .setCoordinateTransformation(transformation)
                .setPreserveNodeWithId(id -> true) // keep all nodes, hence no simplification of the network
                .build()
                .read(inputFile);

        var result = NetworkUnsimplifier.unsimplifyNetwork(simplifiedNetwork, inputFile, "EPSG:25833");

        // now, test that each matsim link has the same number of original links attached as we can find matsim links with the same
        // orig id in the unsimplified network
        var unsimplifiedGroupedByOrigId = completeNetwork.getLinks().values().stream()
                .collect(Collectors.groupingBy(link -> (long) link.getAttributes().getAttribute("origid")));

        var resultGroupedByOrigId = result.values().stream()
                .flatMap(Collection::stream)
                .collect(Collectors.groupingBy(link -> (long) link.getAttributes().getAttribute("origid")));

        // assert that the network from the osm file and the unsimplified network have the same number of sub links
        // for each osm way
        for (var entry : resultGroupedByOrigId.entrySet()) {

            var linksForOrigId = unsimplifiedGroupedByOrigId.get(entry.getKey());

            if (!exceptionsForAssertion.contains(entry.getKey()))
                assertEquals(linksForOrigId.size(), entry.getValue().size());
        }

        // make sure the length share is correct by summing the shares up
        // exclude the exceptional ways as with the number of links
        for (Map.Entry<Id<Link>, List<Link>> entry : result.entrySet()) {


            var simplifiedLink = simplifiedNetwork.getLinks().get(entry.getKey());
            var origId = (long) simplifiedLink.getAttributes().getAttribute("origid");

            if (!exceptionsForAssertion.contains(origId)) {
                var summedLength = entry.getValue().stream()
                        .map(Link::getLength)
                        .reduce(Double::sum)
                        .orElseThrow();
                assertEquals(simplifiedLink.getLength(), summedLength, 0.0000001);
            }
        }
    }

    @Test
    @Ignore
    public void testBerlin() throws FileNotFoundException, OsmInputException {

        var simplifiedNetworkFromScenario = NetworkUtils.readNetwork("https://svn.vsp.tu-berlin.de/repos/public-svn/matsim/scenarios/countries/de/berlin/berlin-v5.4-10pct/output-berlin-v5.4-10pct/berlin-v5.4-10pct.output_network.xml.gz");

        var originUTM33 = new Coord(385761.5, 5819224.0);
        var bounds = createBoundingBox(originUTM33, 10);
        final CoordinateTransformation transformation = TransformationFactory.getCoordinateTransformation("EPSG:31468", "EPSG:25833");

        // transform network
        simplifiedNetworkFromScenario.getNodes().values().parallelStream()
                .forEach(node -> transformNode(node, transformation));

        // filter transformed network
        var filteredNetwork = simplifiedNetworkFromScenario.getLinks().values().parallelStream()
                .filter(link -> isCoveredBy(link, bounds))
                .collect(NetworkUtils.getCollector());

        new NetworkWriter(filteredNetwork).write("C:\\Users\\Janek\\Desktop\\berlin-simplified.xml.gz");

        final var osmFilePath = "C:\\Users\\Janek\\Downloads\\berlin-latest.osm.pbf";


        var result = NetworkUnsimplifier.unsimplifyNetwork(filteredNetwork, osmFilePath, "EPSG:25833");

        var resultNetwork = result.values().stream()
                .flatMap(Collection::stream)
                .collect(NetworkUtils.getCollector());

        new NetworkWriter(resultNetwork).write("C:\\Users\\Janek\\Desktop\\berlin-unsimplified.xml.gz");
    }

    @Test
    @Ignore
    public void writeBerlinComplete() {

        final var osmFilePath = "C:\\Users\\Janek\\Downloads\\berlin-latest.osm.pbf";
        var originUTM33 = new Coord(385761.5, 5819224.0);
        var bounds = createBoundingBox(originUTM33, 10);

        // read in a network but with no simplification. This network is taken as comparison
        var completeNetwork = new SupersonicOsmNetworkReader.Builder()
                .setCoordinateTransformation(TransformationFactory.getCoordinateTransformation("EPSG:4326", "EPSG:25833"))
                .setPreserveNodeWithId(id -> true) // keep all nodes, hence no simplification of the network
                .setIncludeLinkAtCoordWithHierarchy((coord, integer) -> bounds.covers(MGC.coord2Point(coord))) // keep everything within bounds
                .build()
                .read(osmFilePath);

        new NetworkWriter(completeNetwork).write("C:\\Users\\Janek\\Desktop\\berlin-complete.xml.gz");
    }
}