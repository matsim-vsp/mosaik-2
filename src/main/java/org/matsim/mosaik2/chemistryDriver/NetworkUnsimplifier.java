package org.matsim.mosaik2.chemistryDriver;

import de.topobyte.osm4j.core.access.OsmHandler;
import de.topobyte.osm4j.core.access.OsmInputException;
import de.topobyte.osm4j.core.model.iface.OsmBounds;
import de.topobyte.osm4j.core.model.iface.OsmNode;
import de.topobyte.osm4j.core.model.iface.OsmRelation;
import de.topobyte.osm4j.core.model.iface.OsmWay;
import de.topobyte.osm4j.core.model.impl.Way;
import de.topobyte.osm4j.pbf.seq.PbfReader;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.geotools.referencing.CRS;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.NetworkFactory;
import org.matsim.api.core.v01.network.Node;
import org.matsim.core.network.LinkFactory;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.utils.collections.Tuple;
import org.matsim.core.utils.geometry.CoordinateTransformation;
import org.matsim.core.utils.geometry.transformations.TransformationFactory;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.operation.MathTransform;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.groupingBy;

@RequiredArgsConstructor
public class NetworkUnsimplifier {

    static Map<Id<Link>, List<Link>> filterNodesFromOsmFile(String osmFile, Network network, String destinationCrs) throws FileNotFoundException, OsmInputException {

        var file = new File(osmFile);
        var transformation = TransformationFactory.getCoordinateTransformation("EPSG:4326", destinationCrs);

        // find some stützstellen
        var originalIds = network.getLinks().values().stream()
                .map(link -> {
                    var origId = (long)link.getAttributes().getAttribute("origid");
                    return Tuple.of(origId, link.getId());
                })
                .collect(groupingBy(Tuple::getFirst, Collectors.mapping(Tuple::getSecond, Collectors.toSet())));
                //.collect(Collectors.toMap(Tuple::getFirst, Tuple::getSecond));

        // set up first pass for ways
        var reader = new PbfReader(file, false);
        var nodeReferencesCollector = new CollectNodeReferences(originalIds);
        reader.setHandler(nodeReferencesCollector);
        reader.read();

        // second pass to read nodes
        reader = new PbfReader(file, false);
        var nodesCollector = new CollectNodes(nodeReferencesCollector.getNodesReferencingWays(), transformation);
        reader.setHandler(nodesCollector);
        reader.read();

        var nodes = nodesCollector.getNodes();

        Map<Id<Link>, List<Link>> result = new HashMap<>();

        for (var linkId2OsmWay : nodeReferencesCollector.linkIdToWayReference.entrySet()) {

            var link = network.getLinks().get(linkId2OsmWay.getKey());
            var way = linkId2OsmWay.getValue();

            if (linkId2OsmWay.getKey().equals(Id.createLinkId("1819242340021f"))) {
                var stop = "it";
            }

            var startIndex = getNodeIndex(link.getFromNode().getId(), way);
            var endIndex = getNodeIndex(link.getToNode().getId(), way);

            // determine the direction we have to iterate. The matsim network contains forward and backwards links for
            // the same way
            var direction = getDirection(startIndex, endIndex);

            if (isLoop(linkId2OsmWay.getValue()) && wrapsAround(way, startIndex, endIndex, direction)) {

                var links = createLinksWithWrapAround(link, way, startIndex, endIndex, direction * -1, nodes, network.getFactory());
                result.computeIfAbsent(link.getId(), id -> new ArrayList<>()).addAll(links);

            } else {
                var links = createLinks(link, way, startIndex, endIndex, direction, nodes, network.getFactory());
                result.computeIfAbsent(linkId2OsmWay.getKey(), id -> new ArrayList<>()).addAll(links);
            }
        }

        return result;
    }

    private static int getNodeIndex(Id<Node> fromNode, OsmWay way) {

        for (var i = 0; i < way.getNumberOfNodes(); i++) {

            if (fromNode.equals(Id.createNodeId(way.getNodeId(i))))
                return i;
        }

        throw new RuntimeException("this is not expected");
    }

    private static int getDirection(int startIndex, int endIndex) {
        return endIndex - startIndex > 0 ? 1 : -1;
    }

    private static boolean isLoop(OsmWay way) {
        return way.getNodeId(0) == way.getNodeId(way.getNumberOfNodes() - 1);
    }

    private static List<Link> createLinks(Link simpleLink, OsmWay osmWay, int startIndex, int endIndex, int direction, Map<Id<Node>, Node> nodes, NetworkFactory factory) {

        List<Link> result = new ArrayList<>();
        // iterate over all nodes between start and end index
        // depending on the direction iteration is conducted for- or backwards
        // the terminal condition will stop one iteration before i is equal to end index
        for(var i = startIndex; i != endIndex; i += direction) {

            var newLink = createLinkFromWay(osmWay, simpleLink, i, direction, nodes, factory);
            result.add(newLink);
        }

        return result;
    }

    private static List<Link> createLinksWithWrapAround(Link simpleLink, OsmWay osmWay, int startIndex, int endIndex, int direction, Map<Id<Node>, Node> nodes, NetworkFactory factory) {

        List<Link> result = new ArrayList<>();
        for (var i = startIndex; keepGoingWithWrapAround(i, startIndex, endIndex, direction); i += direction) {

            if (i + direction >= osmWay.getNumberOfNodes()) i = 0;
            else if (i + direction < 0) i = osmWay.getNumberOfNodes() - 1;

            var newLink = createLinkFromWay(osmWay, simpleLink, i, direction, nodes, factory);
            result.add(newLink);
        }
        return result;
    }

    private static Link createLinkFromWay(OsmWay osmWay, Link simpleLink, int fromIndex, int direction, Map<Id<Node>, Node> nodes, NetworkFactory factory) {

        var fromNode = nodes.get(Id.createNodeId(osmWay.getNodeId(fromIndex)));
        var toNode = nodes.get(Id.createNodeId(osmWay.getNodeId(fromIndex + direction)));

        return factory.createLink(Id.createLinkId(simpleLink.getId().toString() + "_" + fromIndex), fromNode, toNode);
    }

    private static boolean keepGoingWithWrapAround(int currentIndex, int startIndex, int endIndex, int direction) {

        if (direction > 0 ) {
            // if i has wrapped around yet, test whether we have reached the end index. Same in other direction
            if (currentIndex >= startIndex) return true;
            return currentIndex < endIndex;
        }
        else {
            if (currentIndex <= startIndex) return true;
            return currentIndex > endIndex;
        }
    }

    private static boolean wrapsAround(OsmWay osmWay, int startIndex, int endIndex, int direction) {

        // figure out the direction, whether we have to wrap around the array
        var normalDistance = direction > 0 ? endIndex - startIndex : startIndex - endIndex;
        var wrapAroundDistance = 0;
        if (direction > 0 ) {
            wrapAroundDistance = osmWay.getNumberOfNodes() - endIndex + startIndex;
        } else {
            wrapAroundDistance = osmWay.getNumberOfNodes() - startIndex + endIndex;
        }

        return wrapAroundDistance < normalDistance;
    }



    @RequiredArgsConstructor
    private static class CollectNodeReferences implements OsmHandler {

        @Getter
        private final Map<Long, Set<OsmWay>> nodesReferencingWays = new HashMap<>();
        @Getter
        private final Map<Id<Link>, OsmWay> linkIdToWayReference = new HashMap<>();

        private final Map<Long, Set<Id<Link>>> originalIds;


        @Override
        public void handle(OsmBounds bounds) {

        }

        @Override
        public void handle(OsmNode node) {

        }

        @Override
        public void handle(OsmWay way) {

            if (originalIds.containsKey(way.getId())) {

                var wayReferences = originalIds.get(way.getId()).stream()
                        .map(id -> Tuple.of(id, way))
                        .collect(Collectors.toMap(Tuple::getFirst, Tuple::getSecond));

                // keep a reference of a matsim link to the original osm link
                linkIdToWayReference.putAll(wayReferences);

                for (int i = 0; i < way.getNumberOfNodes(); i++) {

                    var nodeId = way.getNodeId(i);

                    nodesReferencingWays.computeIfAbsent(nodeId, id -> new HashSet<>()).addAll(wayReferences.values());
                }
            }
        }

        @Override
        public void handle(OsmRelation relation) {

        }

        @Override
        public void complete() {

        }
    }


    @RequiredArgsConstructor
    private static class CollectNodes implements OsmHandler {

        private final Map<Long, Set<OsmWay>> nodesReferencingWays;
        private final CoordinateTransformation transformation;

        @Getter
        private final Map<Id<Node>, Node> nodes = new HashMap<>();

        @Override
        public void handle(OsmBounds bounds) {

        }

        @Override
        public void handle(OsmNode node) {

            if (nodesReferencingWays.containsKey(node.getId())) {

                var coord = transformation.transform(new Coord(node.getLongitude(), node.getLatitude()));
                var matsimNode = NetworkUtils.createNode(Id.createNodeId(node.getId()), coord);
                this.nodes.put(matsimNode.getId(), matsimNode);
            }
        }

        @Override
        public void handle(OsmWay way) {

        }

        @Override
        public void handle(OsmRelation relation) {

        }

        @Override
        public void complete() {

        }
    }
}
