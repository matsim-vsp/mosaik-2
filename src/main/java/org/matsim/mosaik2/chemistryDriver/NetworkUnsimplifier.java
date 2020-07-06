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
            var nodesForLink = linkEntry.getValue();
            for (var i = 1; i < nodesForLink.size(); i++) {

                var from = nodesForLink.get(i - 1);
                var to = nodesForLink.get(i);

                var link = network.getFactory().createLink(Id.createLinkId(linkEntry.getKey().toString() + "_" + i), from, to);
                result.computeIfAbsent(linkEntry.getKey(), id -> new ArrayList<>()).add(link);
            }
        }

        return result;
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
