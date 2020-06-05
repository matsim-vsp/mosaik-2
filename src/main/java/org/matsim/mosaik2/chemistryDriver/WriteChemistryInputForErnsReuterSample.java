package org.matsim.mosaik2.chemistryDriver;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import gnu.trove.map.TObjectDoubleMap;
import gnu.trove.map.hash.TObjectDoubleHashMap;
import org.apache.log4j.Logger;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.prep.PreparedGeometry;
import org.locationtech.jts.geom.prep.PreparedGeometryFactory;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.NetworkFactory;
import org.matsim.contrib.analysis.time.TimeBinMap;
import org.matsim.contrib.emissions.Pollutant;
import org.matsim.contrib.emissions.events.EmissionEventsReader;
import org.matsim.core.events.EventsUtils;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.utils.collections.Tuple;
import org.matsim.core.utils.geometry.CoordUtils;
import org.matsim.core.utils.geometry.CoordinateTransformation;
import org.matsim.core.utils.geometry.geotools.MGC;
import org.matsim.core.utils.geometry.transformations.TransformationFactory;
import org.matsim.mosaik2.events.RawEmissionEventsReader;

import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

public class WriteChemistryInputForErnsReuterSample {

    private static final Logger logger = Logger.getLogger(WriteChemistryInputForErnsReuterSample.class);
    private static final CoordinateTransformation transformation = TransformationFactory.getCoordinateTransformation("EPSG:31468", "EPSG:25833");

    private static final String TIME = "time";
    private static final String Z = "z";
    private static final String X = "x";
    private static final String Y = "y";
    private static final String SPECIES = "nspecies";
    private static final String FIELD_LEN = "field_length";
    private static final String EMISSION_NAME = "emission_name";
    private static final String EMISSION_INDEX = "emission_index";
    private static final String TIMESTAMP = "timestamp";
    private static final String EMISSION_VALUES = "emission_values";

    private static final double cellSize = 10;
    private static final double timeBinSize = 3600;

    @Parameter(names = "-n", required = true)
    private String networkFile = "";

    @Parameter(names = "-e", required = true)
    private String emissionEventsFile = "";

    @Parameter(names = "-o", required = true)
    private String outputFile = "";

    public static void main(String[] args) {

        var writer = new WriteChemistryInputForErnsReuterSample();
        JCommander.newBuilder().addObject(writer).build().parse(args);
        writer.write2();
    }

    private void write2() {

        var network = NetworkUtils.readNetwork(networkFile);
        var bounds = createBoundingBox();
        var originUTM33 = new Coord(385761.5, 5819224.0);

        // transform network onto UTM-33 and relative to the orign coord, so that the raster has origin = (0,0)
        var filteredNetwork = network.getLinks().values().parallelStream()
                .map(link -> transformLink(link, originUTM33, network.getFactory()))
                .filter(link -> isCoveredBy(link, bounds))
                .collect(NetworkUtils.getCollector());

        // read emissions into time bins sorted by pollutant and link id
        TimeBinMap<Map<Pollutant, TObjectDoubleMap<Id<Link>>>> timeBinMap = new TimeBinMap<>(3600);
        new RawEmissionEventsReader((time, linkId, vehicleId, pollutant, value) -> {

            var id = Id.createLinkId(linkId);
            if (filteredNetwork.getLinks().containsKey(id)) {

                var timeBin = timeBinMap.getTimeBin(time);
                if (!timeBin.hasValue()) {
                    timeBin.setValue(new HashMap<>());
                }
                var emissionsByPollutant = timeBin.getValue();
                var linkEmissions = emissionsByPollutant.computeIfAbsent(pollutant, p -> new TObjectDoubleHashMap<>());
                linkEmissions.adjustOrPutValue(id, value, value);
            }
        }).readFile(emissionEventsFile);

        // transform emissions by link into emissions on a raster
        TimeBinMap<Map<Pollutant, Raster>> rasterTimeBinMap = new TimeBinMap<>(3600);
        for (var bin : timeBinMap.getTimeBins()) {

            Map<Pollutant, Raster> rasterByPollutant = new HashMap<>();
            for (var emissionByPollutant : bin.getValue().entrySet()) {

                var emissions = emissionByPollutant.getValue();
                var raster = Bresenham.rasterizeNetwork(filteredNetwork, new Raster.Bounds(0, 0, 36 * cellSize, 36 * cellSize), emissions, cellSize);
                rasterByPollutant.put(emissionByPollutant.getKey(), raster);
            }
            rasterTimeBinMap.getTimeBin(bin.getStartTime()).setValue(rasterByPollutant);
        }

        PalmChemistryInput2.writeNetCdfFile(outputFile, rasterTimeBinMap);
    }

    private void write() {

        var network = NetworkUtils.readNetwork(networkFile);
        var bounds = createBoundingBox();
        var filteredNetwork = network.getLinks().values().parallelStream()
                .map(link -> {

                    var from = link.getFromNode();
                    var to = link.getToNode();
                    var fromCoordTransformed = transformation.transform(from.getCoord());
                    var toCoordTransformed = transformation.transform(to.getCoord());

                    var fromTransformed = NetworkUtils.createNode(from.getId(), fromCoordTransformed);
                    var toTransformed = NetworkUtils.createNode(to.getId(), toCoordTransformed);
                    var linkTransformed = network.getFactory().createLink(link.getId(), fromTransformed, toTransformed);
                    linkTransformed.setAllowedModes(link.getAllowedModes());
                    linkTransformed.setCapacity(link.getCapacity());
                    linkTransformed.setFreespeed(link.getFreespeed());
                    linkTransformed.setLength(link.getLength());
                    linkTransformed.setNumberOfLanes(link.getNumberOfLanes());
                    // not copying attributes
                    return linkTransformed;
                })
                .filter(link -> isCoveredBy(link, bounds))
                .collect(NetworkUtils.getCollector());

        var rasteredNetwork = Bresenham.rasterizeNetwork(filteredNetwork, bounds, cellSize);
        var handler = new EmissionsToRasterHandler(rasteredNetwork, timeBinSize, cellSize);

        var manager = EventsUtils.createEventsManager();
        manager.addHandler(handler);
        new EmissionEventsReader(manager).readFile(emissionEventsFile);

        var chemistryInput = handler.getPalmChemistryInput();
        chemistryInput.writeToFile(Paths.get(outputFile));
    }

    private static boolean isCoveredBy(Link link, Geometry geometry) {
        return geometry.covers(MGC.coord2Point(link.getFromNode().getCoord())) || geometry.covers(MGC.coord2Point(link.getToNode().getCoord()));
    }

    private static Geometry createBoundingBox() {

        // these are taken from the ernst-reuter-example we've received from the FU-Berlin
        final var originX = 0; // southern boundary
        final var originY = 0; // western boundary
        final var numberOfCells = 36;

        final var maxX = originX + numberOfCells * cellSize;
        final var maxY = originY + numberOfCells * cellSize;

        return new GeometryFactory().createPolygon(new Coordinate[] {
                new Coordinate(originX, originY), new Coordinate(originX, maxY),
                new Coordinate(maxX, maxY), new Coordinate(originX, maxY),
                new Coordinate(originX, originY) // close ring
        });
    }

    private static Link transformLink(Link link, Coord origin,  NetworkFactory factory) {

        var from = link.getFromNode();
        var to = link.getToNode();
        var fromCoordTransformed = CoordUtils.minus(transformation.transform(from.getCoord()), origin);
        var toCoordTransformed = CoordUtils.minus(transformation.transform(to.getCoord()), origin);

        var fromTransformed = NetworkUtils.createNode(from.getId(), fromCoordTransformed);
        var toTransformed = NetworkUtils.createNode(to.getId(), toCoordTransformed);
        var linkTransformed = factory.createLink(link.getId(), fromTransformed, toTransformed);
        linkTransformed.setAllowedModes(link.getAllowedModes());
        linkTransformed.setCapacity(link.getCapacity());
        linkTransformed.setFreespeed(link.getFreespeed());
        linkTransformed.setLength(link.getLength());
        linkTransformed.setNumberOfLanes(link.getNumberOfLanes());
        // not copying attributes
        return linkTransformed;
    }

    private static Coord getCoordRelativeToOrigin(Coord coord, Coord origin) {

        return CoordUtils.minus(coord, origin);
    }
}
