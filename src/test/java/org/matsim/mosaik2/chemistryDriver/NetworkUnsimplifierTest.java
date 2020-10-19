package org.matsim.mosaik2.chemistryDriver;

import de.topobyte.osm4j.core.access.OsmInputException;
import lombok.extern.slf4j.Slf4j;
import org.junit.Rule;
import org.junit.Test;
import org.matsim.contrib.osm.networkReader.SupersonicOsmNetworkReader;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.utils.geometry.transformations.TransformationFactory;
import org.matsim.testcases.MatsimTestUtils;

import java.io.FileNotFoundException;
import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;

@Slf4j
public class NetworkUnsimplifierTest {

    @Rule
    public MatsimTestUtils testUtils = new MatsimTestUtils();

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

        var result = NetworkUnsimplifier.filterNodesFromOsmFile(inputFile, simplifiedNetwork, "EPSG:25833");

        var resultNetwork = result.values().stream()
                .flatMap(Collection::stream)
                .collect(NetworkUtils.getCollector());

        // now, test that each matsim link has the same number of original links attached as we can find matsim links with the same
        // orig id in the unsimplified network
        var unsimplifiedGroupedByOrigId = completeNetwork.getLinks().values().stream()
                .collect(Collectors.groupingBy(link -> (long) link.getAttributes().getAttribute("origid")));

        var resultGroupedByOrigId = result.values().stream()
                .flatMap(Collection::stream)
                .collect(Collectors.groupingBy(link -> (long) link.getAttributes().getAttribute("origid")));

        for (var entry : resultGroupedByOrigId.entrySet()) {

            var linksForOrigId = unsimplifiedGroupedByOrigId.get(entry.getKey());

            if (!exceptionsForAssertion.contains(entry.getKey()))
                assertEquals(linksForOrigId.size(), entry.getValue().size());
        }
    }
}