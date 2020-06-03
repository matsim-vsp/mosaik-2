package org.matsim.mosaik2.chemistryDriver;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.prep.PreparedGeometry;
import org.locationtech.jts.geom.prep.PreparedGeometryFactory;
import org.matsim.api.core.v01.network.Link;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.utils.geometry.geotools.MGC;
import org.matsim.mosaik2.events.RawEmissionEventsReader;

public class WriteChemistryInputForErnsReuterSample {

    @Parameter
    private String networkFile = "";

    @Parameter
    private String emissionEventsFile = "";

    public static void main(String[] args) {

        var writer = new WriteChemistryInputForErnsReuterSample();
        JCommander.newBuilder().addObject(writer).build().parse(args);
        writer.write();
    }

    private void write() {

        var network = NetworkUtils.readNetwork(networkFile);
        var bounds = createBoundingBox();

        var filteredNetwork = network.getLinks().values().parallelStream()
                .filter(link -> isCoveredBy(link, bounds))
                .collect(NetworkUtils.getCollector());

        new RawEmissionEventsReader((time, linkId, vehicleId, pollutant, value) -> {

        }).readFile(emissionEventsFile);

        var input = new PalmChemistryInput(3600, 10);


    }

    private static boolean isCoveredBy(Link link, PreparedGeometry geometry) {
        return geometry.covers(MGC.coord2Point(link.getFromNode().getCoord())) || geometry.covers(MGC.coord2Point(link.getToNode().getCoord()));
    }

    private static PreparedGeometry createBoundingBox() {

        // these are taken from the ernst-reuter-example we've received from the FU-Berlin
        final var originX = 385761.5; // southern boundary
        final var originY = 5819224.0; // western boundary
        final var numberOfCells = 36;
        final var cellSize = 10; // each cell is 10 meters

        final var maxX = originX + numberOfCells * cellSize;
        final var maxY = originY + numberOfCells * cellSize;

        var bounds = new GeometryFactory().createPolygon(new Coordinate[] {
                new Coordinate(originX, originY), new Coordinate(originX, maxY),
                new Coordinate(maxX, maxY), new Coordinate(originX, maxY),
                new Coordinate(originX, originY) // close ring
        });
        return new PreparedGeometryFactory().create(bounds);
    }
}
