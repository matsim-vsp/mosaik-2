package org.matsim.mosaik2.prepare;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import lombok.extern.log4j.Log4j2;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.prep.PreparedGeometry;
import org.locationtech.jts.geom.prep.PreparedGeometryFactory;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.NetworkWriter;
import org.matsim.contrib.osm.networkReader.SupersonicOsmNetworkReader;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.network.io.MatsimNetworkReader;
import org.matsim.core.utils.geometry.CoordinateTransformation;
import org.matsim.core.utils.geometry.geotools.MGC;
import org.matsim.core.utils.geometry.transformations.TransformationFactory;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;

@Log4j2
public class CreateNetwork {

    private static final CoordinateTransformation transformation = TransformationFactory.getCoordinateTransformation("EPSG:4326", "EPSG:25832");
    private static final String senozonNetworkPath = "projects\\mosaik-2\\matsim-input-files\\stuttgart-inkl-umland\\optimizedNetwork.xml.gz";
    private static final String outputNetwork = "projects\\mosaik-2\\matsim-input-files\\stuttgart-inkl-umland-vsp\\network-stuttgart.xml.gz";
    private static final String osmFile = "projects\\mosaik-2\\raw-data\\osm\\germany-20200715.osm.pbf";

    public static void main(String[] args) {

        var arguments = new InputArgs();
        JCommander.newBuilder().addObject(arguments).build().parse(args);

        var network = createNetwork(Paths.get(arguments.sharedSvn));
        writeNetwork(network, Paths.get(arguments.sharedSvn));
    }

    public static Network createNetwork(Path svnPath) {

        var bbox = getBBox(svnPath);

        log.info("Starting to parse osm network. This will not output anything for a while until it reaches the interesting part of the osm file.");

        var allowedModes = Set.of(TransportMode.car, TransportMode.ride); // maybe bike as well?

        var network = new SupersonicOsmNetworkReader.Builder()
                .setCoordinateTransformation(transformation)
                .setIncludeLinkAtCoordWithHierarchy((coord, id) -> bbox.covers(MGC.coord2Point(coord)))
                .setAfterLinkCreated((link, map, direction) -> link.setAllowedModes(allowedModes))
                .build()
                .read(svnPath.resolve(osmFile));

        log.info("Done parsing osm file. ");
        return network;
    }

    public static void writeNetwork(Network network, Path svn) {
        log.info("Writing network to " + svn.resolve(outputNetwork));
        new NetworkWriter(network).write(svn.resolve(outputNetwork).toString());

        log.info("");
        log.info("Finished \uD83C\uDF89");
    }

    private static PreparedGeometry getBBox(Path sharedSvn) {

        log.info("Reading senozon network");
        var senozonNetwork = NetworkUtils.createNetwork();
        new MatsimNetworkReader(senozonNetwork).readFile(sharedSvn.resolve(senozonNetworkPath).toString());

        return BoundingBox.fromNetwork(senozonNetwork).toGeometry();
    }

    private static PreparedGeometry createGeometry(BoundingBox bbox) {

        var gFactory = new GeometryFactory();
        var geometry = gFactory.createPolygon(new Coordinate[]{
                new Coordinate(bbox.getMinX(), bbox.getMinY()),
                new Coordinate(bbox.getMaxX(), bbox.getMinY()),
                new Coordinate(bbox.getMaxX(), bbox.getMaxY()),
                new Coordinate(bbox.getMinX(), bbox.getMaxY()),
                new Coordinate(bbox.getMinX(), bbox.getMinY())
        });

        var pFactory = new PreparedGeometryFactory();
        return pFactory.create(geometry);
    }

    private static class InputArgs {

        @Parameter(names = {"-sharedSvn"}, required = true)
        String sharedSvn = "https://svn.vsp.tu-berlin.de/repos/shared-svn/";
    }
}
