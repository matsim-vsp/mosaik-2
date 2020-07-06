package org.matsim.mosaik2.chemistryDriver;

import de.topobyte.osm4j.core.access.OsmInputException;
import org.junit.Test;
import org.matsim.contrib.osm.networkReader.SupersonicOsmNetworkReader;
import org.matsim.core.utils.geometry.transformations.TransformationFactory;

import java.io.FileNotFoundException;

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


        var result = NetworkUnsimplifier.filterNodesFromOsmFile(inputFile, network, "EPSG:25833");

        assertNotNull(result);
    }

}