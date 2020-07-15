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
import org.geotools.referencing.CRS;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
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

        Map<Id<Link>, List<Link>> result = new HashMap<>();
        for (var linkEntry : nodesCollector.getOrigIdToNodes().entrySet()) {

            if (linkEntry.getKey().equals(Id.createLinkId("61657210001f"))) {
                var stop = "it";
            }
            var nodesForLink = linkEntry.getValue();
            var originalLink = network.getLinks().get(linkEntry.getKey());

            // ways might be longer thant the matsim link. Therefore we must find out which osm node is the matsim
            // link's start and end node.
            var startIndex = -1;
            var endIndex = -1;
            for (var i = 0; i < nodesForLink.size(); i++) {

                var node = nodesForLink.get(i);

                if (node.getId().equals(originalLink.getFromNode().getId())) {
                    startIndex = i;
                    if (endIndex >= 0) break; // stop searching if end index was already found
                }
                if (node.getId().equals(originalLink.getToNode().getId())) {
                    endIndex = i;
                    if (startIndex >= 0) break; // stop searching if start index was already found
                }
            }

            // determine the direction we have to iterate. The matsim network contains foreward and backwards links for
            // the same way
            var direction = endIndex - startIndex > 0 ? 1 : -1;


            // iterate over all nodes between start and end index
            // depending on the direction iteration is conducted for- or backwards
            // the terminal condition will stop one iteration before i is equal to end index
            for (var i = startIndex; i != endIndex; i += direction) {

                try {
                    var from = nodesForLink.get(i);
                    var to = nodesForLink.get(i + direction);
                    var link = network.getFactory().createLink(Id.createLinkId(linkEntry.getKey().toString() + "_" + i), from, to);
                    result.computeIfAbsent(linkEntry.getKey(), id -> new ArrayList<>()).add(link);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        }

        return result;
    }

    private static boolean matchesAnyNodeOnLink(Node node, Link link) {
        return node.getId().equals(link.getFromNode().getId()) || node.getId().equals(link.getToNode().getId());
    }



    @RequiredArgsConstructor
    private static class CollectNodeReferences implements OsmHandler {

        @Getter
        private final Map<Long, Set<Id<Link>>> nodesReferencingWays = new HashMap<>();

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

                var linkIds = originalIds.get(way.getId());
                for (int i = 0; i < way.getNumberOfNodes(); i++) {

                    var nodeId = way.getNodeId(i);
                    nodesReferencingWays.computeIfAbsent(nodeId, id -> new HashSet<>()).addAll(linkIds);
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

        private final Map<Long, Set<Id<Link>>> nodesReferencingWays;
        private final CoordinateTransformation transformation;

        @Getter
        private final Map<Id<Link>, List<Node>> origIdToNodes = new HashMap<>();

        @Override
        public void handle(OsmBounds bounds) {

        }

        @Override
        public void handle(OsmNode node) {

            if (nodesReferencingWays.containsKey(node.getId())) {

                var coord = transformation.transform(new Coord(node.getLongitude(), node.getLatitude()));
                var matsimNode = NetworkUtils.createNode(Id.createNodeId(node.getId()), coord);

                for (var linkId : nodesReferencingWays.get(node.getId())) {
                    origIdToNodes.computeIfAbsent(linkId, id -> new ArrayList<>()).add(matsimNode);
                }
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
