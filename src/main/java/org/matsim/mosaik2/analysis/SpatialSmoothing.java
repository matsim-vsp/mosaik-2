package org.matsim.mosaik2.analysis;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import it.unimi.dsi.fastutil.objects.Object2DoubleMap;
import it.unimi.dsi.fastutil.objects.Object2DoubleOpenHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.locationtech.jts.geom.Geometry;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.contrib.analysis.time.TimeBinMap;
import org.matsim.contrib.emissions.events.EmissionEventsReader;
import org.matsim.core.events.EventsUtils;
import org.matsim.core.utils.collections.QuadTree;
import org.matsim.core.utils.gis.ShapeFileReader;
import org.matsim.mosaik2.chemistryDriver.AggregateEmissionsByTimeHandler;
import org.matsim.mosaik2.chemistryDriver.PollutantToPalmNameConverter;
import org.matsim.mosaik2.palm.PalmCsvOutput;
import org.matsim.mosaik2.raster.DoubleRaster;
import org.matsim.mosaik2.raster.ObjectRaster;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.operation.TransformException;

import java.nio.file.Path;
import java.util.HashSet;

@Log4j2
@RequiredArgsConstructor
public class SpatialSmoothing {

    private final String species;
    private final Path emissionEvents;
    private final Path networkPath;
    private final Path shapeFile;
    private final Path outputFile;
    private final int r;
    private final int cellSize;
    private final int timeBinSize;
    private final double scaleFactor;

    public static void main(String[] args) throws FactoryException, TransformException {

        var inputArgs = new InputArgs();
        JCommander.newBuilder().addObject(inputArgs).build().parse(args);

        new SpatialSmoothing(
                inputArgs.species, inputArgs.emissionEvents, inputArgs.networkPath, inputArgs.shapeFile,
                inputArgs.outputFile, inputArgs.r, inputArgs.cellSize, inputArgs.timeBinSize, inputArgs.scaleFactor
        ).run();
    }

    private static QuadTree<Id<Link>> createLinkQuadTree(Network network, Geometry bounds) {
        var envelope = bounds.getEnvelopeInternal();
        var q = new QuadTree<Id<Link>>(
                envelope.getMinX(), envelope.getMinY(),
                envelope.getMaxX(), envelope.getMaxY()
        );

        for (Link link : network.getLinks().values()) {

            if (link.getCoord().getX() > q.getMinEasting() &&
                    link.getCoord().getX() < q.getMaxEasting() &&
                    link.getCoord().getY() > q.getMinNorthing() &&
                    link.getCoord().getY() < q.getMaxNorthing()
            ) {
                q.put(link.getCoord().getX(), link.getCoord().getY(), link.getId());
            }
        }
        return q;
    }

    void run() throws FactoryException, TransformException {

        var berlinGeometry = ShapeFileReader.getAllFeatures(shapeFile.toString()).stream()
                .map(simpleFeature -> (Geometry) simpleFeature.getDefaultGeometry())
                .findAny()
                .orElseThrow(() -> new RuntimeException("Couldn't find feature for berlin"));

        var network = CalculateRValues.loadNetwork(networkPath.toString(), berlinGeometry);

        // create a quad tree which contains link ids. This attaches link ids to the centroids of the
        // corresponding links. We use this as a quick cache to reduce the number of calculations
        // necessary when calculating concentration values for the entire grid. This is less precise
        // than using from and to nodes, but speeds up the computation by 50%.
        var quadTree = createLinkQuadTree(network, berlinGeometry);
        var bounds = new ObjectRaster.Bounds(berlinGeometry);

        var manager = EventsUtils.createEventsManager();
        var converter = PollutantToPalmNameConverter.createForSingleSpecies(species);
        var handler = new AggregateEmissionsByTimeHandler(network, converter.getPollutants(), timeBinSize, scaleFactor);
        manager.addHandler(handler);

        log.info("Start parsing emission events.");
        new EmissionEventsReader(manager).readFile(emissionEvents.toString());

        log.info("Sort collected emissions by link");
        TimeBinMap<Object2DoubleMap<Link>> emissionByLink = new TimeBinMap<>(timeBinSize);

        // use lambda for each in case we want to run concurrent
        handler.getTimeBinMap().getTimeBins().forEach(bin -> {

            log.info("Converting link emissions for for: [" + bin.getStartTime() + ", " + (bin.getStartTime() + timeBinSize) + "]");
            var resultBin = emissionByLink.getTimeBin(bin.getStartTime());
            var emissionResultMap = resultBin.computeIfAbsent(Object2DoubleOpenHashMap::new);

            var emissionByPollutant = bin.getValue();
            for (var pollutantEntry : emissionByPollutant.entrySet()) {
                var emissionMap = pollutantEntry.getValue();
                for (var idEntry : emissionMap.object2DoubleEntrySet()) {

                    var link = network.getLinks().get(idEntry.getKey());
                    emissionResultMap.mergeDouble(link, idEntry.getDoubleValue(), Double::sum);
                }
            }
        });

        TimeBinMap<DoubleRaster> rasterTimeSeries = new TimeBinMap<>(timeBinSize);
        for (var bin : emissionByLink.getTimeBins()) {

            log.info("Calculating concentrations for: [" + bin.getStartTime() + ", " + (bin.getStartTime() + timeBinSize) + "]");
            var raster = new DoubleRaster(bounds, cellSize);
            var linkEmissions = bin.getValue();

            raster.setValueForEachCoordinate((x, y) -> {

                var linkIds = new HashSet<>(quadTree.getDisk(x, y, 1000));

                // instead of calling sumf in the NumericSmoothing class we re-implement the logic here.
                // this saves us one stream/collect in the inner loop here.
                var normalizationFactor = cellSize / (Math.PI * r * r);
                return linkEmissions.object2DoubleEntrySet().stream()
                        .filter(entry -> linkIds.contains(entry.getKey().getId()))
                        .mapToDouble(entry -> {
                            var link = entry.getKey();
                            var weight = NumericSmoothingRadiusEstimate.calculateWeight(
                                    link.getFromNode().getCoord(),
                                    link.getToNode().getCoord(),
                                    new Coord(x, y),
                                    link.getLength(),
                                    r
                            );
                            var emission = entry.getDoubleValue();
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
        @Parameter(names = "-shp", required = true)
        private Path shapeFile;
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
}