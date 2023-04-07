package org.matsim.mosaik2.analysis;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import it.unimi.dsi.fastutil.objects.Object2DoubleMap;
import it.unimi.dsi.fastutil.objects.Object2DoubleOpenHashMap;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.csv.CSVPrinter;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.contrib.analysis.time.TimeBinMap;
import org.matsim.mosaik2.Utils;
import org.matsim.mosaik2.palm.XYTValueCsvData;
import org.matsim.mosaik2.raster.ObjectRaster;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;

@Log4j2
public class CalculateLinkExposure {

    private final Path exposureFile;
    private final Path outputFile;

    private final Network network;

    private final Method method;
    private final ObjectRaster<Set<Id<Link>>> linkCache;

    private final double r;

    public CalculateLinkExposure(InputArgs args) {
        this(Paths.get(args.exposureFile), Paths.get(args.networkFile), Paths.get(args.outputFile), args.r, args.method);
    }

    public CalculateLinkExposure(Path exposureFile, Path networkFile, Path outputFile, double r, Method method) {

        var info = XYTValueCsvData.readDataInfo(exposureFile);
        network = CalculateRValues.loadNetwork(networkFile.toString(), info.getRasterInfo().getBounds().toGeometry());
        //linkCache = CalculateRValues.createCache(network, info.getRasterInfo().getBounds(), info.getRasterInfo().getCellSize());\
        log.info("Create spatial index");
        var linkIndex = new SpatialIndex(network, r * 5, info.getRasterInfo().getBounds().toGeometry());
        linkCache = new ObjectRaster<>(info.getRasterInfo().getBounds(), info.getRasterInfo().getCellSize());
        log.info("Creating raster cache with link ids");
        linkCache.setValueForEachCoordinate(linkIndex::query);
        this.exposureFile = exposureFile;
        this.r = r;
        this.outputFile = outputFile;
        this.method = method;
    }

    public static void main(String[] args) {

        var input = new InputArgs();
        JCommander.newBuilder().addObject(input).build().parse(args);
        new CalculateLinkExposure(input).run();
    }

    void run() {

        var dataInfo = XYTValueCsvData.readDataInfo(exposureFile);
        var exposureData = XYTValueCsvData.read(exposureFile, dataInfo);
        var result = new TimeBinMap<Object2DoubleMap<Id<Link>>>(exposureData.getBinSize());

        log.info("Starting to calculate exposure values for links");

        // populate result map, so that we can work on the bins in parallel
        for (var bin : exposureData.getTimeBins()) {
            result.getTimeBin(bin.getStartTime()).computeIfAbsent(Object2DoubleOpenHashMap::new);
        }

        var cellSize = dataInfo.getRasterInfo().getCellSize();
        var cellVolume = cellSize * cellSize * cellSize;

        exposureData.getTimeBins().parallelStream().forEach(bin -> {
            log.info("Calculating impact values for Time Slice: [" + bin.getStartTime() + ", " + (bin.getStartTime() + exposureData.getBinSize()) + "]");
            var exposurePerLink = result
                    .getTimeBin(bin.getStartTime())
                    .getValue();
            var exposureSlice = bin.getValue();
            exposureSlice.forEachCoordinate((x, y, value) -> {

                if (value <= 0.0) return; // no need to do anything here.

                var linkIds = linkCache.getValueByCoord(x, y);
                linkIds.stream()
                        .map(id -> network.getLinks().get(id))
                        .forEach(link -> {
                            var weight = NumericSmoothingRadiusEstimate.calculateWeight(
                                    link.getFromNode().getCoord(),
                                    link.getToNode().getCoord(),
                                    new Coord(x, y),
                                    link.getLength(),
                                    r
                            );
                            if (weight > 0.0) {
                                var impactValue = value * weight;
                                exposurePerLink.mergeDouble(link.getId(), impactValue, Double::sum);
                            }
                        });
            });
        });

        log.info("Finished Calculation");

        log.info("Writing to : " + outputFile);
        var valueHeader = method.equals(Method.Concentrations) ? "emissions [g/m3]" : "exposure-impact [g*s]";
        try (var writer = Files.newBufferedWriter(outputFile);
             var printer = new CSVPrinter(writer, Utils.createWriteFormat("id", "time", valueHeader))) {

            for (var bin : result.getTimeBins()) {

                var time = bin.getStartTime();
                for (var entry : bin.getValue().object2DoubleEntrySet()) {
                    var id = entry.getKey();
                    var exposureImpact = entry.getDoubleValue();
                    printer.printRecord(id, time, exposureImpact);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static class InputArgs {

        @Parameter(names = "-e", required = true)
        public String exposureFile;
        @Parameter(names = "-n", required = true)
        public String networkFile;
        @Parameter(names = "-o", required = true)
        public String outputFile;
        @Parameter(names = "-r", required = true)
        public double r;
        @Parameter(names = "-m")
        public Method method = Method.Concentrations;
    }

    static enum Method {
        Concentrations,
        Exposures
    }
}