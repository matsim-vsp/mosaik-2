package org.matsim.mosaik2.chemistryDriver;

import de.topobyte.osm4j.core.access.OsmHandler;
import de.topobyte.osm4j.core.access.OsmInputException;
import de.topobyte.osm4j.core.model.iface.OsmBounds;
import de.topobyte.osm4j.core.model.iface.OsmNode;
import de.topobyte.osm4j.core.model.iface.OsmRelation;
import de.topobyte.osm4j.core.model.iface.OsmWay;
import de.topobyte.osm4j.pbf.seq.PbfReader;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.NetworkFactory;
import org.matsim.api.core.v01.network.Node;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.utils.collections.Tuple;
import org.matsim.core.utils.geometry.CoordinateTransformation;
import org.matsim.core.utils.geometry.transformations.TransformationFactory;
import org.matsim.pt.transitSchedule.TransitScheduleUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.*;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.groupingBy;

@RequiredArgsConstructor
public class NetworkUnsimplifier {

    public static final String LENGTH_FRACTION_KEY = "length_fraction";
    public static final String NOT_MATCHED_KEY = "NOT_MATCHED";
    private static final Logger log = Logger.getLogger(NetworkUnsimplifier.class);

    static Map<Id<Link>, List<Link>> unsimplifyNetwork(final Network network, CoordinateTransformation transformation) {

        return network.getLinks().values().parallelStream()
                .map(link -> Tuple.of(link.getId(), createLinkSegments(link, transformation, network.getFactory())))
                .collect(Collectors.toMap(Tuple::getFirst, Tuple::getSecond));
    }

    static List<Link> createLinkSegments(Link link, CoordinateTransformation transformation, NetworkFactory factory) {

        var nodes = NetworkUtils.getOriginalGeometry(link);

        List<Link> segments = new ArrayList<>();
        var fromNode = nodes.get(0);
        var i = 1;

        for (; i < nodes.size() - 1; i++) {

            var toNode = getNodeTransformed(nodes, i, transformation);
            segments.add(createSegment(link, fromNode, toNode, i - 1, factory));
            fromNode = toNode;
        }

        // we need to add one more segment from last intermediate to to-Node
        segments.add(createSegment(link, fromNode, link.getToNode(), i - 1, factory));

        return segments;
    }

    static Link createSegment(Link link, Node from, Node to, int segmentIndex, NetworkFactory factory) {
        var id = Id.createLinkId(link.getId().toString() + "_" + segmentIndex);
        var segment = factory.createLink(id, from, to);
        segment.getAttributes().putAttribute(LENGTH_FRACTION_KEY, segment.getLength() / link.getLength());
        segment.setAllowedModes(link.getAllowedModes());
        segment.setCapacity(link.getCapacity());
        segment.setFreespeed(link.getFreespeed());
        segment.setNumberOfLanes(link.getNumberOfLanes());
        return segment;
    }

    static Node getNodeTransformed(List<Node> nodes, int index, CoordinateTransformation transformation) {

        var node = nodes.get(index);
        var transformedCoord = transformation.transform(node.getCoord());
        node.setCoord(transformedCoord);
        return node;
    }

    static Network segmentsToNetwork(Map<Id<Link>, List<Link>> segments) {
        return segments.values().stream()
                .flatMap(Collection::stream)
                .collect(NetworkUtils.getCollector());
    }

    static Map<Id<Link>, List<Link>> unsimplifyNetwork(final Network network, final String osmFile, final String destinationCrs) throws FileNotFoundException, OsmInputException {

        var file = new File(osmFile);
        var transformation = TransformationFactory.getCoordinateTransformation("EPSG:4326", destinationCrs);

        log.info("Collecting original ids from network");
        // collect the original ids from the network
        var originalIds = network.getLinks().values().stream()
                .filter(link -> link.getAllowedModes().contains(TransportMode.car))
                .map(link -> {
                    var origId = parseOrigId(link);
                    return Tuple.of(origId, link.getId());
                })
                .collect(groupingBy(Tuple::getFirst, Collectors.mapping(Tuple::getSecond, Collectors.toSet())));

        log.info("Start parsing ways from osm file");
        // read in the ways from the supplied osm file
        var reader = new PbfReader(file, false);
        var nodeReferencesCollector = new CollectNodeReferences(originalIds);
        reader.setHandler(nodeReferencesCollector);
        reader.read();

        log.info("Start parsing nodes from osm file");
        // read in the nodes from the supplied osm file
        reader = new PbfReader(file, false);
        var nodesCollector = new CollectNodes(nodeReferencesCollector.getNodesReferencingWays(), transformation);
        reader.setHandler(nodesCollector);
        reader.read();

        final var nodes = nodesCollector.getNodes();

        log.info("Start matching matsim links to osm ways");
        var result = nodeReferencesCollector.getLinkIdToWayReference().entrySet().stream()
                .map(linkId2OsmWay -> {
                    var link = network.getLinks().get(linkId2OsmWay.getKey());
                    var way = linkId2OsmWay.getValue();

                    if (link.getId().equals(Id.createLinkId("14923"))) {
                        log.info("stop it");
                    }

                    var indices = getIndices(link.getFromNode().getId(), link.getToNode().getId(), way);

                    // determine the direction we have to iterate. The matsim network contains forward and backwards links for
                    // the same way
                    var direction = getDirection(indices);

                    return Tuple.of(linkId2OsmWay.getKey(), createLinks(link, way, indices, direction, nodes, network.getFactory()));
                })
                .collect(Collectors.toMap(Tuple::getFirst, Tuple::getSecond));

        // find ways which didn't have a corresponding osm way
        Map<Id<Link>, List<Link>> linksWithNoOsmWay = network.getLinks().entrySet().stream()
                .filter(entry -> !result.containsKey(entry.getKey()))
                .peek(entry -> {
                    entry.getValue().getAttributes().putAttribute(LENGTH_FRACTION_KEY, 1.0);
                    entry.getValue().getAttributes().putAttribute(NOT_MATCHED_KEY, true);
                })
                .collect(Collectors.toMap(Map.Entry::getKey, e -> List.of(e.getValue())));

        // put them into the result as well so that emissions can be calculated for them directly
        result.putAll(linksWithNoOsmWay);

        var unmatched = result.values().stream()
                .filter(list -> list.size() <= 1)
                .flatMap(Collection::stream)
                .filter(link -> link.getAttributes().getAttribute(NOT_MATCHED_KEY) != null)
                .count();

        log.info(unmatched + "/" + network.getLinks().size() + " couldn't be matched :-(");

        return result;
    }

    private static long parseOrigId(Link link) {

        Object attr = link.getAttributes().getAttribute("origid");
        if (attr instanceof String) {
            return Long.parseLong((String) attr);
        } else if (attr instanceof Long) {
            return (long) attr;
        }

        throw new RuntimeException("attribute origid is expected to be either of type String or long");
    }

    private static int getDirection(IndexContainer indices) {
        return indices.getEndIndex() - indices.getStartIndex() > 0 ? 1 : -1;
    }

    private static List<Link> createLinks(Link simpleLink, OsmWay osmWay, IndexContainer indices, int direction, Map<Id<Node>, Node> nodes, NetworkFactory factory) {

        try {
            List<Link> result = new ArrayList<>();

            // iterate over all nodes between start and end index
            // depending on the direction iteration is conducted for- or backwards
            // the terminal condition will stop one iteration before i is equal to end index
            for (var i = indices.getStartIndex(); i != indices.getEndIndex(); i += direction) {

                var newLink = createLinkFromWay(osmWay, simpleLink, i, direction, nodes, factory);
                result.add(newLink);
            }
            return result;
        } catch (Exception e) {
            // if anything goes wrong i.e. the nodes within the osm way don't match the simple link simply use the simple link
            // as a fallback. The simple link accounts for all emissions of an emissions event
            simpleLink.getAttributes().putAttribute(LENGTH_FRACTION_KEY, 1.0);
            simpleLink.getAttributes().putAttribute(NOT_MATCHED_KEY, true);
            return List.of(simpleLink);
        }
    }

    private static Link createLinkFromWay(OsmWay osmWay, Link simpleLink, int fromIndex, int direction, Map<Id<Node>, Node> nodes, NetworkFactory factory) {


        var fromNode = nodes.get(Id.createNodeId(osmWay.getNodeId(fromIndex)));
        var toNode = nodes.get(Id.createNodeId(osmWay.getNodeId(fromIndex + direction)));
        var link = factory.createLink(Id.createLinkId(simpleLink.getId().toString() + "_" + fromIndex), fromNode, toNode);

        link.getAttributes().putAttribute("origid", simpleLink.getAttributes().getAttribute("origid"));

        // add this attribute so that the emissions generated for the simpleLink can be distributed onto the unsimplified links correctly
        link.getAttributes().putAttribute(LENGTH_FRACTION_KEY, link.getLength() / simpleLink.getLength());
        return link;

    }

    private static IndexContainer getIndices(Id<Node> fromNode, Id<Node> toNode, OsmWay way) {

        // figure out whether there are multiple candidates for the start end end node in the nodes collection of the osm-way
        var firstFromNodeIndex = getNodeIndex(fromNode, way, 0);
        var firstToNodeIndex = getNodeIndex(toNode, way, 0);
        var secondFromNodeIndex = getNodeIndex(fromNode, way, firstFromNodeIndex + 1);
        var secondToNodeIndex = getNodeIndex(toNode, way, firstToNodeIndex + 1);

        // remember the first candidates in every case
        List<IndexContainer> indexList = new ArrayList<>();
        indexList.add(new IndexContainer(firstFromNodeIndex, firstToNodeIndex));

        // if there are secondary candidates also remember those and all possible combinations
        if (secondFromNodeIndex != -1)
            indexList.add(new IndexContainer(secondFromNodeIndex, firstToNodeIndex));
        if (secondToNodeIndex != -1)
            indexList.add(new IndexContainer(firstFromNodeIndex, secondToNodeIndex));
        if (secondFromNodeIndex != -1 && secondToNodeIndex != -1)
            indexList.add(new IndexContainer(secondFromNodeIndex, secondToNodeIndex));

        // select the candidate pair with the fewest nodes in between
        // maybe move comparator into static variable so it doesn't have to be rebuild each time
        indexList.sort(Comparator.comparingInt(indexPair -> Math.abs(indexPair.getStartIndex() - indexPair.getEndIndex())));
        return indexList.get(0);
    }

    private static int getNodeIndex(Id<Node> fromNode, OsmWay way, int startIndex) {

        for (var i = startIndex; i < way.getNumberOfNodes(); i++) {

            if (fromNode.equals(Id.createNodeId(way.getNodeId(i))))
                return i;
        }

        // id was not found
        return -1;
    }

    @RequiredArgsConstructor
    private static class IndexContainer {

        @Getter
        private final int startIndex;

        @Getter
        private final int endIndex;
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
