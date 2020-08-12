package org.matsim.mosaik2.prepare;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import org.apache.log4j.Logger;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.prep.PreparedGeometry;
import org.locationtech.jts.geom.prep.PreparedGeometryFactory;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.network.NetworkWriter;
import org.matsim.contrib.osm.networkReader.SupersonicOsmNetworkReader;
import org.matsim.core.network.algorithms.NetworkCleaner;
import org.matsim.core.utils.geometry.CoordinateTransformation;
import org.matsim.core.utils.geometry.geotools.MGC;
import org.matsim.core.utils.geometry.transformations.TransformationFactory;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TransferQueue;

public class CreateNetwork {

    private static GeometryFactory gFactory = new GeometryFactory();
    private static PreparedGeometryFactory pFactory = new PreparedGeometryFactory();
    private static CoordinateTransformation transformation = TransformationFactory.getCoordinateTransformation("EPSG:4326", "EPSG:25832");
    private static Logger logger = Logger.getLogger(CreateNetwork.class);

    private static final Path osmFilePath = Paths.get("projects/mosaik-2/raw-data/osm/germany-20200715.osm.pbf");
    private static final Path outputFilePath = Paths.get("matsim/scenarios/countries/de/stuttgart/stuttgart-v0.1-1pct/input-stuttgart-v0.1-1pct/network.xml.gz");


    @Parameter(names = {"-sharedSvn"}, required = true)
    private String sharedSvn = "";

    @Parameter(names = {"-publicSvn"}, required = true)
    private String publicSvn = "";

    public static void main(String[] args) {

        var creator = new CreateNetwork();
        JCommander.newBuilder().addObject(creator).build().parse(args);
        creator.run();

    }

    private void run() {

        var bbox = gFactory.createPolygon(new Coordinate[]{
                new Coordinate(395063.25, 5564101.5), new Coordinate(660442.25, 5564101.5),
                new Coordinate(660442.25, 5348301), new Coordinate(395063.25, 5348301),
                new Coordinate(395063.25, 5564101.5)
        });

        var preparedBbox = pFactory.create(bbox);
        var osmFile = Paths.get(sharedSvn).resolve(osmFilePath);
        var network = new SupersonicOsmNetworkReader.Builder()
                .setCoordinateTransformation(transformation)
                .setIncludeLinkAtCoordWithHierarchy((coord, integer) -> isInBbox(coord, preparedBbox))
                .build()
                .read(osmFile);

        new NetworkCleaner().run(network);

        new NetworkWriter(network).write(Paths.get(publicSvn).resolve(outputFilePath).toString());
    }

    private static boolean isInBbox(Coord coord, PreparedGeometry geometry) {

        return geometry.covers(MGC.coord2Point(coord));
    }
}
