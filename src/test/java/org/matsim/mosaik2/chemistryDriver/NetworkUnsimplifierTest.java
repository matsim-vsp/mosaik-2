package org.matsim.mosaik2.chemistryDriver;

import de.topobyte.osm4j.core.access.OsmInputException;
import org.junit.Rule;
import org.junit.Test;
import org.matsim.contrib.osm.networkReader.SupersonicOsmNetworkReader;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.utils.geometry.transformations.TransformationFactory;
import org.matsim.testcases.MatsimTestUtils;

import java.io.FileNotFoundException;
import java.util.Collection;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;

public class NetworkUnsimplifierTest {

    @Rule
    public MatsimTestUtils testUtils = new MatsimTestUtils();

    @Test
    public void testAndorra() throws FileNotFoundException, OsmInputException {

        var inputFile = testUtils.getInputDirectory() + "andorra.osm.pbf";
        var transformation = TransformationFactory.getCoordinateTransformation("EPSG:4326", "EPSG:25833");

        var simplifiedNetwork = new SupersonicOsmNetworkReader.Builder()
                .setCoordinateTransformation(transformation)
                .build()
                .read(inputFile);

        //new NetworkWriter(simplifiedNetwork).write("C:/Users/Janek/Desktop/simplified.xml.gz");

        var completeNetwork = new SupersonicOsmNetworkReader.Builder()
                .setCoordinateTransformation(transformation)
                .setPreserveNodeWithId(id -> true) // keep all nodes, hence no simplification of the network
                .build()
                .read(inputFile);

        //  new NetworkWriter(completeNetwork).write("C:/Users/Janek/Desktop/original.xml.gz");

        var result = NetworkUnsimplifier.filterNodesFromOsmFile(inputFile, simplifiedNetwork, "EPSG:25833");

        var resultNetwork = result.values().stream()
                .flatMap(Collection::stream)
                .collect(NetworkUtils.getCollector());

        //    new NetworkWriter(resultNetwork).write("C:/Users/Janek/Desktop/unsimplified.xml.gz");

        // now, test that each matsim link has the same number of original links attached as we can find matsim links with the same
        // orig id in the unsimplified network
        var unsimplifiedGroupedByOrigId = completeNetwork.getLinks().values().stream()
                .collect(Collectors.groupingBy(link -> (long) link.getAttributes().getAttribute("origid")));

        var resultGroupedByOrigId = result.values().stream()
                .flatMap(Collection::stream)
                .collect(Collectors.groupingBy(link -> (long) link.getAttributes().getAttribute("origid")));

        for (var entry : resultGroupedByOrigId.entrySet()) {

            var linksForOrigId = unsimplifiedGroupedByOrigId.get(entry.getKey());
            if (linksForOrigId.size() != entry.getValue().size()) {
                var stop = "it";
            }
            assertEquals(linksForOrigId.size(), entry.getValue().size());
        }
    }

    @Test
    public void writeWithoutSimplification() {

        // read in andorra first because it is small
        var inputFile = "C:\\Users\\Janekdererste\\Downloads\\andorra-latest.osm.pbf";
        var transformation = TransformationFactory.getCoordinateTransformation("EPSG:4326", "EPSG:25833");

        var network = new SupersonicOsmNetworkReader.Builder()
                .setCoordinateTransformation(transformation)
                .setPreserveNodeWithId(id -> true)
                .build()
                .read(inputFile);

        NetworkUtils.writeNetwork(network, "C:/Users/Janekdererste/Desktop/original-network.xml.gz");
    }

}