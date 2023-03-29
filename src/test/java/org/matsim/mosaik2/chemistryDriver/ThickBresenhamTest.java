package org.matsim.mosaik2.chemistryDriver;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.junit.Test;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.core.network.NetworkUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

public class ThickBresenhamTest {

    @Test
    public void shortLine() {
        ThickBresenham.plotLineWidth(3, 5, 7, 5, 6, (xi, yi) -> System.out.println(xi + ", " + yi));
    }

    @Test
    public void singleLine() {

        var network = NetworkUtils.createNetwork();
        var node1 = network.getFactory().createNode(Id.createNodeId("from1"), new Coord(30, 100));
        var node2 = network.getFactory().createNode(Id.createNodeId("to2"), new Coord(70, 100));
        var link1 = network.getFactory().createLink(Id.createLinkId("link1"), node1, node2);
        network.addNode(node1);
        network.addNode(node2);
        network.addLink(link1);

        var node3 = network.getFactory().createNode(Id.createNodeId("from2"), new Coord(70, 200));
        var node4 = network.getFactory().createNode(Id.createNodeId("to3"), new Coord(30, 200));
        var link2 = network.getFactory().createLink(Id.createLinkId("link2"), node3, node4);
        network.addNode(node3);
        network.addNode(node4);
        network.addLink(link2);

        var node5 = network.getFactory().createNode(Id.createNodeId("from5"), new Coord(200, 30));
        var node6 = network.getFactory().createNode(Id.createNodeId("to6"), new Coord(200, 70));
        var link3 = network.getFactory().createLink(Id.createLinkId("link3"), node5, node6);
        network.addNode(node5);
        network.addNode(node6);
        network.addLink(link3);

        var node7 = network.getFactory().createNode(Id.createNodeId("from7"), new Coord(300, 70));
        var node8 = network.getFactory().createNode(Id.createNodeId("to8"), new Coord(300, 30));
        var link4 = network.getFactory().createLink(Id.createLinkId("link4"), node7, node8);
        network.addNode(node7);
        network.addNode(node8);
        network.addLink(link4);

        var node9 = network.getFactory().createNode(Id.createNodeId("from9"), new Coord(0, 0));
        var node10 = network.getFactory().createNode(Id.createNodeId("to10"), new Coord(50, 40));
        var link5 = network.getFactory().createLink(Id.createLinkId("link5"), node9, node10);
        network.addNode(node9);
        network.addNode(node10);
        network.addLink(link5);

        var node11 = network.getFactory().createNode(Id.createNodeId("from11"), new Coord(100, 300));
        var node12 = network.getFactory().createNode(Id.createNodeId("to12"), new Coord(0, 500));
        var link6 = network.getFactory().createLink(Id.createLinkId("link6"), node11, node12);
        network.addNode(node11);
        network.addNode(node12);
        network.addLink(link6);

        NetworkUtils.writeNetwork(network, "C:\\Users\\janek\\Desktop\\network-lines.xml");

        try (var writer = Files.newBufferedWriter(Paths.get("C:\\Users\\janek\\Desktop\\lines.csv")); var printer = CSVFormat.DEFAULT.withHeader("x", "y").print(writer)) {

            for (Link link : network.getLinks().values()) {
                ThickBresenham.plotLineWidth(
                        (int) link.getFromNode().getCoord().getX(),
                        (int) link.getFromNode().getCoord().getY(),
                        (int) link.getToNode().getCoord().getX(),
                        (int) link.getToNode().getCoord().getY(),
                        4,
                        (xi, yi) -> print(printer, xi, yi)
                );
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void print(CSVPrinter printer, int xi, int yi) {
        try {
            printer.printRecord(xi, yi
            );
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}