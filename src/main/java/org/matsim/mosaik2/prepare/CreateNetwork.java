package org.matsim.mosaik2.prepare;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import lombok.Getter;
import lombok.ToString;
import lombok.extern.log4j.Log4j2;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.prep.PreparedGeometry;
import org.locationtech.jts.geom.prep.PreparedGeometryFactory;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.NetworkWriter;
import org.matsim.api.core.v01.network.Node;
import org.matsim.contrib.osm.networkReader.SupersonicOsmNetworkReader;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.network.io.MatsimNetworkReader;
import org.matsim.core.utils.geometry.CoordinateTransformation;
import org.matsim.core.utils.geometry.geotools.MGC;
import org.matsim.core.utils.geometry.transformations.TransformationFactory;

import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collector;

@Log4j2
public class CreateNetwork {

    private static final CoordinateTransformation transformation = TransformationFactory.getCoordinateTransformation("EPSG:4326", "EPSG:25832");
    private static final String senozonNetworkPath = "projects\\mosaik-2\\matsim-input-files\\stuttgart-inkl-umland\\optimizedNetwork.xml.gz";
    private static final String outputNetwork = "projects\\mosaik-2\\matsim-input-files\\stuttgart-inkl-umland-vsp\\stuttgart-network-xml.gz";
    private static final String osmFile = "projects\\mosaik-2\\raw-data\\osm\\germany-20200715.osm.pbf";

    public static void main(String[] args) {

        var arguments = new InputArgs();
        JCommander.newBuilder().addObject(arguments).build().parse(args);

        var bbox = getBBox(arguments.sharedSvn);
        var bounds = CreateNetwork.createGeometry(bbox);

        log.info("Starting to parse osm network. This will not output anything for a while until it reaches the interesting part of the osm file.");

        var allowedModes = Set.of(TransportMode.car, TransportMode.ride); // maybe bike as well?

        var network = new SupersonicOsmNetworkReader.Builder()
                .setCoordinateTransformation(transformation)
                .setIncludeLinkAtCoordWithHierarchy((coord, id) -> bounds.covers(MGC.coord2Point(coord)))
                .setAfterLinkCreated((link, map, direction) -> link.setAllowedModes(allowedModes))
                .build()
                .read(arguments.sharedSvn + osmFile);

        log.info("Done parsing osm file. ");
        log.info("Writing network to " + arguments.sharedSvn + outputNetwork);
        new NetworkWriter(network).write(arguments.sharedSvn + outputNetwork);

        log.info("");
        log.info("Finished \uD83C\uDF89");
    }

    private static BoundingBox getBBox(String sharedSvn) {

        log.info("Reading senozon network");
        var senozonNetwork = NetworkUtils.createNetwork();
        new MatsimNetworkReader(senozonNetwork).readFile(sharedSvn + senozonNetworkPath);

        log.info("Done reading senozon network");
        log.info("Calculating bounding box of car network");
        // take the bounding box of the senozon network
        var bbox = new BoundingBox();
        for (Link link : senozonNetwork.getLinks().values()) {

            if (link.getAllowedModes().contains("car")) {
                bbox.adjust(link.getFromNode().getCoord());
                bbox.adjust(link.getToNode().getCoord());
            }
        }

        log.info("Done calculating bounding box of car network.");
        log.info("Bbox is: " + bbox.toString());

        return bbox;
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

    @Getter
    @ToString
    public static class BoundingBox {

        private double minX = Double.POSITIVE_INFINITY;
        private double minY = Double.POSITIVE_INFINITY;
        private double maxX = Double.NEGATIVE_INFINITY;
        private double maxY = Double.NEGATIVE_INFINITY;

        public synchronized void adjust(Coord coord) {
            if (minX > coord.getX()) minX = coord.getX();
            if (minY > coord.getY()) minY = coord.getY();
            if (maxX < coord.getX()) maxX = coord.getX();
            if (maxY < coord.getY()) maxY = coord.getY();
        }
    }

    private static class InputArgs {

        @Parameter(names = {"-sharedSvn"}, required = true)
        String sharedSvn = "https://svn.vsp.tu-berlin.de/repos/shared-svn/";
    }
}
