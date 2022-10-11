package org.matsim.mosaik2.analysis;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.prep.PreparedGeometry;
import org.locationtech.jts.geom.prep.PreparedGeometryFactory;
import org.locationtech.jts.index.hprtree.HPRtree;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.application.options.ShpOptions;
import org.matsim.contrib.analysis.time.TimeBinMap;
import org.matsim.contrib.emissions.events.EmissionEventsReader;
import org.matsim.core.events.EventsUtils;
import org.matsim.core.utils.geometry.geotools.MGC;
import org.matsim.core.utils.gis.ShapeFileReader;
import org.matsim.mosaik2.chemistryDriver.AggregateEmissionsByTimeHandler;
import org.matsim.mosaik2.chemistryDriver.PollutantToPalmNameConverter;
import org.matsim.mosaik2.palm.PalmCsvOutput;
import org.matsim.mosaik2.raster.DoubleRaster;
import org.matsim.mosaik2.raster.ObjectRaster;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.operation.TransformException;

import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.*;

@Log4j2
@RequiredArgsConstructor
public class SpatialSmoothing {

    private final String species;
    private final Path emissionEvents;
    private final Path networkPath;
    private final Path boundsFile;

    private final Path buildingsFile;
    private final Path outputFile;
    private final int r;
    private final int cellSize;
    private final int timeBinSize;
    private final double scaleFactor;

    public static void main(String[] args) throws FactoryException, TransformException {

        var inputArgs = new InputArgs();
        JCommander.newBuilder().addObject(inputArgs).build().parse(args);

        new SpatialSmoothing(
                inputArgs.species, inputArgs.emissionEvents, inputArgs.networkPath, inputArgs.boundsFile, inputArgs.buildingsFile,
                inputArgs.outputFile, inputArgs.r, inputArgs.cellSize, inputArgs.timeBinSize, inputArgs.scaleFactor
        ).run();
    }

    void run() throws FactoryException, TransformException {

        var berlinGeometry = ShapeFileReader.getAllFeatures(boundsFile.toString()).stream()
                .map(simpleFeature -> (Geometry) simpleFeature.getDefaultGeometry())
                .findAny()
                .orElseThrow(() -> new RuntimeException("Couldn't find feature for berlin"));
        var bounds = new ObjectRaster.Bounds(berlinGeometry);

        var network = CalculateRValues.loadNetwork(networkPath.toString(), berlinGeometry);

        log.info("Creating spatial link index");
        // with a distance of 3*r, 99% of emissions of a link get distributet into the raster.
        var linkIndex = new Index(network, r * 5, berlinGeometry);
        var linkIndexRaster = new ObjectRaster<Set<Id<Link>>>(bounds, cellSize);

        log.info("Creating raster cache with link ids.");
        linkIndexRaster.setValueForEachCoordinate(linkIndex::query);

        log.info("Creating spatial building index");
        var options = new ShpOptions(buildingsFile, "EPSG:4326", Charset.defaultCharset());
        var buildingIndex = options.createIndex("EPSG:25833", "");

        log.info("Creating raster with building data.");
        var rasteredBuildings = new DoubleRaster(bounds, cellSize);
        rasteredBuildings.setValueForEachCoordinate((x, y) -> buildingIndex.contains(new Coord(x, y)) ? -1 : 0);

        var manager = EventsUtils.createEventsManager();
        var converter = PollutantToPalmNameConverter.createForSingleSpecies(species);
        var handler = new AggregateEmissionsByTimeHandler(network, converter.getPollutants(), timeBinSize, scaleFactor);
        manager.addHandler(handler);

        log.info("Start parsing emission events.");
        new EmissionEventsReader(manager).readFile(emissionEvents.toString());

        log.info("Sort collected emissions by link");
        TimeBinMap<Map<Id<Link>, LinkEmission>> emissionByLink = new TimeBinMap<>(timeBinSize);
        handler.getTimeBinMap().getTimeBins().forEach(bin -> {

            var resultBin = emissionByLink.getTimeBin(bin.getStartTime());
            var emissionResultMap = resultBin.computeIfAbsent(HashMap::new);

            var emissionByPollutant = bin.getValue();
            for (var pollutantEntry : emissionByPollutant.entrySet()) {
                var emissionMap = pollutantEntry.getValue();
                for (var idEntry : emissionMap.object2DoubleEntrySet()) {

                    var link = network.getLinks().get(idEntry.getKey());
                    emissionResultMap.computeIfAbsent(link.getId(), id -> new LinkEmission(link)).add(idEntry.getDoubleValue());
                }
            }
        });

        log.info("Start calculating concentrations.");
        TimeBinMap<DoubleRaster> rasterTimeSeries = new TimeBinMap<>(timeBinSize);
        var counter = 0;
        for (var bin : emissionByLink.getTimeBins()) {

            if (counter > 9) break;
            counter++;

            log.info("Calculating concentrations for: [" + bin.getStartTime() + ", " + (bin.getStartTime() + timeBinSize) + "]");
            var raster = new DoubleRaster(bounds, cellSize);
            var linkEmissions = bin.getValue();

            raster.setValueForEachCoordinate((x, y) -> {

                // this means this point is covered by a building
                if (rasteredBuildings.getValueByCoord(x, y) < 0) return -1;

                // instead of calling sumf in the NumericSmoothing class we re-implement the logic here.
                // this saves us one stream/collect in the inner loop here.
                var normalizationFactor = cellSize / (Math.PI * r * r);
                var linkIds = linkIndexRaster.getValueByCoord(x, y);

                return linkIds.stream()
                        .map(linkEmissions::get)
                        .filter(Objects::nonNull)
                        .mapToDouble(linkEmission -> {
                            var link = linkEmission.link;
                            var weight = NumericSmoothingRadiusEstimate.calculateWeight(
                                    link.getFromNode().getCoord(),
                                    link.getToNode().getCoord(),
                                    new Coord(x, y),
                                    link.getLength(),
                                    r
                            );
                            var emission = linkEmission.value;
                            return emission * weight * normalizationFactor;
                        })
                        .sum();
            });

            rasterTimeSeries.getTimeBin(bin.getStartTime()).setValue(raster);
        }

        PalmCsvOutput.write(outputFile, rasterTimeSeries);
    }

    @SuppressWarnings("FieldMayBeFinal")
    private static class InputArgs {

        @Parameter(names = "-s", required = true)
        private String species;
        @Parameter(names = "-e", required = true)
        private Path emissionEvents;
        @Parameter(names = "-n", required = true)
        private Path networkPath;
        @Parameter(names = "-bounds", required = true)
        private Path boundsFile;
        @Parameter(names = "-buildings", required = true)
        private Path buildingsFile;
        @Parameter(names = "-o", required = true)
        private Path outputFile;
        @Parameter(names = "-r")
        private int r = 11;
        @Parameter(names = "-c")
        private int cellSize = 10;
        @Parameter(names = "-t")
        private int timeBinSize = 3600;
        @Parameter(names = "-f")
        private double scaleFactor = 10;

        private InputArgs() {
        }
    }

    private static class Index {
        private static final GeometryFactory geomFact = new GeometryFactory();

        private final HPRtree index = new HPRtree();

        Index(Network network, double bufferDist, Geometry bounds) {

            var prepFactory = new PreparedGeometryFactory();
            var preparedBounds = prepFactory.create(bounds);

            network.getLinks().values().stream()
                    .map(link -> {
                        var line = geomFact.createLineString(new Coordinate[]{
                                MGC.coord2Coordinate(link.getFromNode().getCoord()), MGC.coord2Coordinate(link.getToNode().getCoord())
                        });
                        var lineRect = line.buffer(bufferDist);
                        var preparedRect = prepFactory.create(lineRect);
                        return new IndexLine(link.getId(), preparedRect);
                    })
                    .filter(indexLine -> preparedBounds.covers(indexLine.geometry.getGeometry()))
                    .forEach(indexLine -> index.insert(indexLine.geometry.getGeometry().getEnvelopeInternal(), indexLine));
        }

        Set<Id<Link>> query(double x, double y) {

            var point = geomFact.createPoint(new Coordinate(x, y));
            Set<Id<Link>> result = new HashSet<>();
            index.query(point.getEnvelopeInternal(), item -> {
                var indexLine = (IndexLine) item;
                if (indexLine.geometry.intersects(point))
                    result.add(indexLine.id);
            });
            return result;
        }
    }

    record IndexLine(Id<Link> id, PreparedGeometry geometry) {
    }

    @RequiredArgsConstructor
    private static class LinkEmission {

        private final Link link;
        private double value;


        void add(double emission) {
            this.value += emission;
        }
    }
}