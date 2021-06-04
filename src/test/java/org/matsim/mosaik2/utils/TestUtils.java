package org.matsim.mosaik2.utils;

import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.NetworkFactory;
import org.matsim.api.core.v01.network.Node;
import org.matsim.contrib.emissions.Pollutant;
import org.matsim.contrib.emissions.events.ColdEmissionEvent;
import org.matsim.contrib.emissions.events.WarmEmissionEvent;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.events.EventsUtils;
import org.matsim.core.events.algorithms.EventWriter;
import org.matsim.core.events.algorithms.EventWriterXML;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.utils.geometry.CoordUtils;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class TestUtils {

    public static void writeWarmEventsToFile(Path eventsFile, Network network, Pollutant pollutant, double pollutionPerEvent, int fromTime, int toTime) {

        EventsManager eventsManager = EventsUtils.createEventsManager();
        EventWriter writer = new EventWriterXML(eventsFile.toString());
        eventsManager.addHandler(writer);

        eventsManager.initProcessing();

        network.getLinks().values().forEach(link -> {
            for (int i = fromTime; i <= toTime; i++) {
                eventsManager.processEvent(createWarmEmissionEvent(i, link, pollutant, pollutionPerEvent));
            }
        });
        eventsManager.finishProcessing();
        writer.closeFile();
    }

    public static WarmEmissionEvent createWarmEmissionEvent(double time, Link link, Pollutant pollutant, double pollutionPerEvent) {
        Map<Pollutant, Double> emissions = new HashMap<>();
        emissions.put(pollutant, pollutionPerEvent);
        return new WarmEmissionEvent(time, link.getId(), Id.createVehicleId(UUID.randomUUID().toString()), emissions);
    }

    public static void writeColdEventsToFile(Path eventsFile, Network network, Pollutant pollutant, double pollutionPerEvent, int fromTime, int toTime) {

        EventsManager eventsManager = EventsUtils.createEventsManager();
        EventWriter writer = new EventWriterXML(eventsFile.toString());
        eventsManager.addHandler(writer);

        eventsManager.initProcessing();

        network.getLinks().values().forEach(link -> {
            for (int i = fromTime; i <= toTime; i++) {
                eventsManager.processEvent(createColdEmissionEvent(i, link, pollutant, pollutionPerEvent));
            }
        });
        eventsManager.finishProcessing();
        writer.closeFile();
    }

    public static ColdEmissionEvent createColdEmissionEvent(double time, Link link, Pollutant pollutant, double pollutionPerEvent) {
        Map<Pollutant, Double> emissions = new HashMap<>();
        emissions.put(pollutant, pollutionPerEvent);
        return new ColdEmissionEvent(time, link.getId(), Id.createVehicleId(UUID.randomUUID().toString()), emissions);
    }

    public static Network createRandomNetwork(int numberOfLinks, double maxX, double maxY) {

        Network network = NetworkUtils.createNetwork();

        for (long i = 0; i < numberOfLinks; i++) {

            Link link = createRandomLink(network.getFactory(), maxX, maxY);
            network.addNode(link.getFromNode());
            network.addNode(link.getToNode());
            network.addLink(link);
        }
        return network;
    }

    /**
     * creates a network with one link from (5, 0) to (95, 0) this can be used to test the raster algorithm
     *
     * @return network with single link
     */
    public static Network createSingleLinkNetwork() {

        var network = NetworkUtils.createNetwork();
        var fromCoord = new Coord(5, 0);
        var toCoord = new Coord(95, 0);

        return createSingleLinkNetwork(fromCoord, toCoord, List.of());
    }

    public static Network createSingleLinkNetwork(Coord from, Coord to, List<Coord> origGeometry) {

        var network = NetworkUtils.createNetwork();
        var fromNode = NetworkUtils.createAndAddNode(network, Id.createNodeId("from"), from);
        var toNode = NetworkUtils.createAndAddNode(network, Id.createNodeId("to"), to);
        var link = network.getFactory().createLink(Id.createLinkId("link"), fromNode, toNode);
        network.addLink(link);

        addOriginalGeometry(link, origGeometry);

        return network;
    }

    private static Link createRandomLink(NetworkFactory factory, double maxX, double maxY) {
        Node fromNode = createRandomNode(factory, maxX, maxY);
        Node toNode = createRandomNode(factory, maxX, maxY);
        var link = factory.createLink(Id.createLinkId(UUID.randomUUID().toString()), fromNode, toNode);
        addOriginalGeometry(link, List.of(new Coord(getRandomValue(maxX), getRandomValue(maxY))));
        return link;
    }

    private static Node createRandomNode(NetworkFactory factory, double maxX, double maxY) {
        Coord coord = new Coord(getRandomValue(maxX), getRandomValue(maxY));
        return factory.createNode(Id.createNodeId(UUID.randomUUID().toString()), coord);
    }

    private static double getRandomValue(double upperBounds) {
        return Math.random() * upperBounds;
    }

    private static void addOriginalGeometry(Link link, List<Coord> origGeometry) {
        if (!origGeometry.isEmpty()) {
            var builder = new StringBuilder();
            var prevCoord = link.getFromNode().getCoord();
            var length = 0.0;

            for (var i = 0; i < origGeometry.size(); i++) {

                var coord = origGeometry.get(i);
                builder
                        .append("intermediate_").append(i).append(",")
                        .append(coord.getX()).append(",")
                        .append(coord.getY()).append(" ");

                length += CoordUtils.calcEuclideanDistance(prevCoord, coord);
                prevCoord = coord;
            }
            length += CoordUtils.calcEuclideanDistance(prevCoord, link.getToNode().getCoord()); // add last bit of length
            link.getAttributes().putAttribute(NetworkUtils.ORIG_GEOM, builder.toString());
            link.setLength(length);
        }
    }
}
