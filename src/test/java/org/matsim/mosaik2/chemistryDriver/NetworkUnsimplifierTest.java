package org.matsim.mosaik2.chemistryDriver;

import de.topobyte.osm4j.core.access.OsmInputException;
import org.junit.Test;
import org.matsim.contrib.osm.networkReader.SupersonicOsmNetworkReader;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.utils.geometry.transformations.TransformationFactory;

import java.io.FileNotFoundException;
import java.util.Collection;

import static org.junit.Assert.*;

public class NetworkUnsimplifierTest {

    @Test
    public void testAndorra() throws FileNotFoundException, OsmInputException {

        // read in andorra first because it is small
        var inputFile = "C:\\Users\\Janekdererste\\Downloads\\andorra-latest.osm.pbf";
        var transformation = TransformationFactory.getCoordinateTransformation("EPSG:4326", "EPSG:25833");

        var network = new SupersonicOsmNetworkReader.Builder()
                .setCoordinateTransformation(transformation)
                .build()
                .read(inputFile);

        NetworkUtils.writeNetwork(network, "C:/Users/Janekdererste/Desktop/simplified-andorra.xml.gz");

        var result = NetworkUnsimplifier.filterNodesFromOsmFile(inputFile, network, "EPSG:25833");

        var unsimplifiedNetwork = result.values().stream()
                .flatMap(Collection::stream)
                .collect(NetworkUtils.getCollector());

        NetworkUtils.writeNetwork(unsimplifiedNetwork, "C:/Users/Janekdererste/Desktop/unsimplified-andorra.xml.gz");
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