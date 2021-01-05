package org.matsim.mosaik2.chemistryDriver;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import de.topobyte.osm4j.core.access.OsmInputException;
import gnu.trove.map.TObjectDoubleMap;
import gnu.trove.map.hash.TObjectDoubleHashMap;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.log4j.Logger;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.NetworkFactory;
import org.matsim.contrib.analysis.time.TimeBinMap;
import org.matsim.contrib.emissions.Pollutant;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.utils.collections.Tuple;
import org.matsim.core.utils.geometry.CoordUtils;
import org.matsim.core.utils.geometry.CoordinateTransformation;
import org.matsim.core.utils.geometry.geotools.MGC;
import org.matsim.core.utils.geometry.transformations.TransformationFactory;
import org.matsim.mosaik2.events.RawEmissionEventsReader;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * For the ernst reuter sample scenario this used the following input params:
 * -n open-berlin 5.4 network file
 * -e open-berlin 5.4 10% events file
 * -osm berlin latest from https://downloads.geofabrik.de
 * -o local output file
 * -s scale factor of 10 (since 10% scenario size)
 * -cs cell size of 10m
 * <p>
 * For the scenario Björn Maronga runs for us we've used the same parameters as above but
 * -cs cell size of 2m
 * -dimX 1024
 * -dimY 1024
 * -origX
 * 385029.5
 * -origY
 * 5818412.0
 */
@SuppressWarnings("FieldMayBeFinal")
public class WriteChemistryInputForErnsReuterSample {

    private static final Logger logger = Logger.getLogger(WriteChemistryInputForErnsReuterSample.class);
    private static final CoordinateTransformation transformation = TransformationFactory.getCoordinateTransformation("EPSG:31468", "EPSG:25833");
    private static final Map<Pollutant, String> pollutants = Map.of(
            Pollutant.NO2, "NO2",
            Pollutant.CO2_TOTAL, "CO2",
            Pollutant.PM, "PM10",
            Pollutant.CO, "CO",
            Pollutant.NOx, "NOx"
    );// NO is missing for now, it would have to be calculated from NOx - NO2

    private static final double timeBinSize = 3600;

    @Parameter(names = "-n", required = true)
    private String networkFile = "";

    @Parameter(names = "-e", required = true)
    private String emissionEventsFile = "";

    @Parameter(names = "-osm", required = true)
    private String osmFile = "";

    @Parameter(names = "-o", required = true)
    private String outputFile = "";

    @Parameter(names = "-s")
    private int scaleFactor = 100; // by default we assume that one runs this script with emissions from a 1pct sample

    @Parameter(names = "-cs") // by default this is 10
    private double cellSize = 10;

    @Parameter(names = "-dimX")
    private double xDimension = 36;

    @Parameter(names = "-dimY")
    private double yDimension = 36;

    @Parameter(names = "-origX")
    private double origX = 385761.5;

    @Parameter(names = "-origY")
    private double origY = 5819224.0;

    public static void main(String[] args) throws FileNotFoundException, OsmInputException {

        var writer = new WriteChemistryInputForErnsReuterSample();
        JCommander.newBuilder().addObject(writer).build().parse(args);
        writer.write2();
    }

    private static Link transformLink(Link link, Coord origin, NetworkFactory factory) {

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

        for (var attr : link.getAttributes().getAsMap().entrySet()) {
            linkTransformed.getAttributes().putAttribute(attr.getKey(), attr.getValue());
        }

        return linkTransformed;
    }

    private static boolean isCoveredBy(Link link, Geometry geometry) {
        return geometry.covers(MGC.coord2Point(link.getFromNode().getCoord())) && geometry.covers(MGC.coord2Point(link.getToNode().getCoord()));
    }

    private Geometry createBoundingBox() {

        final var originX = 0; // southern boundary
        final var originY = 0; // western boundary

        final var maxX = originX + xDimension * cellSize;
        final var maxY = originY + yDimension * cellSize;

        return new GeometryFactory().createPolygon(new Coordinate[]{
                new Coordinate(originX, originY), new Coordinate(originX, maxY),
                new Coordinate(maxX, maxY), new Coordinate(originX, maxY),
                new Coordinate(originX, originY) // close ring
        });
    }

    private void write2() {

        var network = NetworkUtils.readNetwork(networkFile);
        var bounds = createBoundingBox();
        var originUTM33 = new Coord(origX, origY);

        // transform network onto UTM-33 and relative to the origin coord, so that the raster has origin = (0,0)
        var filteredNetwork = network.getLinks().values().parallelStream()
                .map(link -> transformLink(link, originUTM33, network.getFactory()))
                .filter(link -> isCoveredBy(link, bounds))
                .collect(NetworkUtils.getCollector());

        // var linkToUnsimplifiedLinks = NetworkUnsimplifier.unsimplifyNetwork(filteredNetwork, osmFile, "EPSG:25833");

        // read emissions into time bins sorted by pollutant and link id
        TimeBinMap<Map<Pollutant, TObjectDoubleMap<Id<Link>>>> timeBinMap = new TimeBinMap<>(timeBinSize);
        var pollutantsOfInterest = pollutants.keySet();

        new RawEmissionEventsReader((time, linkId, vehicleId, pollutant, value) -> {

            // ignore everything we're not interested in
            if (!pollutantsOfInterest.contains(pollutant))
                return;

            var id = Id.createLinkId(linkId);
            if (filteredNetwork.getLinks().containsKey(id)) {

                var timeBin = timeBinMap.getTimeBin(time);
                if (!timeBin.hasValue()) {
                    timeBin.setValue(new HashMap<>());
                }
                var emissionsByPollutant = timeBin.getValue();
                var linkEmissions = emissionsByPollutant.computeIfAbsent(pollutant, p -> new TObjectDoubleHashMap<>());

                // distribute the emissions from the simplified link onto the more detailed sub links from osm
              /*  for (Link link : linkToUnsimplifiedLinks.get(id)) {
                    var lengthFraction = (double) link.getAttributes().getAttribute(NetworkUnsimplifier.LENGTH_FRACTION_KEY);
                    // scaleFactor to compensate for scenario sample size
                    // lengthFraction to weight the unsimplified sub-links according to their share of the overall length of the simplified link
                    // divide by 1000 because the palm input is expected to be in kg/m2/dt
                    var linkEmission = value * scaleFactor * lengthFraction / 1000;
                    linkEmissions.adjustOrPutValue(link.getId(), linkEmission, linkEmission);
                }

               */

                // do it unsimplified
                var linkEmission = value * scaleFactor / 1000;
                linkEmissions.adjustOrPutValue(id, linkEmission, linkEmission);
            }
        }).readFile(emissionEventsFile);

        // create a network out of the unsimplified links
       /* var unsimplifiedNetwork = linkToUnsimplifiedLinks.values().stream()
                .flatMap(Collection::stream)
                .collect(NetworkUtils.getCollector());

        // fit the unsimplified network onto the raster, which has the (0,0) as origin
        for (Node node : unsimplifiedNetwork.getNodes().values()) {
            var relativeToOrigin = CoordUtils.minus(node.getCoord(), originUTM33);
            node.setCoord(relativeToOrigin);
        }
        */

        // unsimplified network is already adjusted to origin

        // transform emissions by link into emissions on a raster
        TimeBinMap<Map<String, Raster>> rasterTimeBinMap = new TimeBinMap<>(timeBinSize);
        for (var bin : timeBinMap.getTimeBins()) {

            logger.info("Writing emissions to raster for timestep: " + bin.getStartTime());

            var rasterByPollutant = bin.getValue().entrySet().parallelStream()
                    .map(entry -> {

                        var emissions = entry.getValue();
                        //var raster = Bresenham.rasterizeNetwork(unsimplifiedNetwork, new Raster.Bounds(0, 0, xDimension * cellSize, yDimension * cellSize), emissions, cellSize);
                        // do it unsimplified
                        var raster = Bresenham.rasterizeNetwork(filteredNetwork, new Raster.Bounds(0, 0, xDimension * cellSize, yDimension * cellSize), emissions, cellSize);
                        var palmPollutantKey = pollutants.get(entry.getKey());
                        return Tuple.of(palmPollutantKey, raster);
                    })
                    .collect(Collectors.toMap(Tuple::getFirst, Tuple::getSecond));
            rasterTimeBinMap.getTimeBin(bin.getStartTime()).setValue(rasterByPollutant);
        }

        // calculate no
        for (TimeBinMap.TimeBin<Map<String, Raster>> timeBin : rasterTimeBinMap.getTimeBins()) {

            var no2 = timeBin.getValue().get(pollutants.get(Pollutant.NO2));
            var nox = timeBin.getValue().get(pollutants.get(Pollutant.NOx));
            var no = new Raster(no2.getBounds(), no2.getCellSize());

            nox.forEachCoordinate((x, y, noxValue) -> {
                var no2Value = no2.getValueByCoord(x, y);
                var noValue = noxValue - no2Value;
                no.adjustValueForCoord(x, y, noValue);
            });

            timeBin.getValue().put("NO", no);
            timeBin.getValue().remove("NOx");
        }

        PalmChemistryInput2.writeNetCdfFile(outputFile, rasterTimeBinMap);

        logger.info("Writing csv file to: C:\\Users\\Janekdererste\\Desktop\\ernst_reuter_input.csv");
        writeCSV(Paths.get("C:\\Users\\Janekdererste\\Desktop\\ernst_reuter_input.csv"), rasterTimeBinMap);
    }

    public void writeCSV(Path file, TimeBinMap<Map<String, Raster>> map) {

        try (var writer = Files.newBufferedWriter(file); var printer = new CSVPrinter(writer, CSVFormat.DEFAULT)) {

            // print header
            printer.printRecord("time", "x", "y", "NO2");

            for (TimeBinMap.TimeBin<Map<String, Raster>> timeBin : map.getTimeBins()) {

                var time = timeBin.getStartTime();
                var raster = timeBin.getValue().get("NO2");
                raster.forEachCoordinate((x, y, value) -> {
                    // meh, have to delegate this to the outside
                    try {
                        printer.printRecord(time, x, y, value);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
