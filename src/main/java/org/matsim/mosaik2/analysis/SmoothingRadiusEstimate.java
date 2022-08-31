package org.matsim.mosaik2.analysis;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import it.unimi.dsi.fastutil.objects.Object2DoubleMap;
import it.unimi.dsi.fastutil.objects.Object2DoubleOpenHashMap;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.prep.PreparedGeometryFactory;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.contrib.analysis.time.TimeBinMap;
import org.matsim.contrib.emissions.Pollutant;
import org.matsim.contrib.emissions.events.EmissionEventsReader;
import org.matsim.core.events.EventsUtils;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.utils.collections.Tuple;
import org.matsim.core.utils.geometry.geotools.MGC;
import org.matsim.mosaik2.chemistryDriver.AggregateEmissionsByTimeHandler;
import org.matsim.mosaik2.chemistryDriver.PollutantToPalmNameConverter;
import org.matsim.mosaik2.palm.PalmOutputReader;
import org.matsim.mosaik2.raster.ObjectRaster;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;


@SuppressWarnings("ALL")
@Log4j2
public class SmoothingRadiusEstimate {

    public static void main(String[] args) {

        var input = new InputArgs();
        JCommander.newBuilder().addObject(input).build().parse(args);

        var estimator = new SmoothingRadiusEstimate(input);
        // 4. run collect r for each time slice in the palm file and write a csv
        estimator.collectRForEachTimeslice();
    }

    private final InputArgs input;
    private final ObjectRaster<Set<Id<Link>>> linkCache;
    private final Network network;
    private final TimeBinMap<Map<String, Object2DoubleMap<Link>>> emissions;

    SmoothingRadiusEstimate(InputArgs input) {
        this.input = input;
        // 1. create a cash with links which are considered for each receiver point
        // 1.1 remember all the links which are considered at all, so that the emission handler also receives a reduced
        //     number of links
        this.linkCache = setUpLinkCash();
        this.network = loadNetworkAndPopulateLinkCashe();

        // 2. read the events file and create an emission map.
        // 3. convert handler data into the correct data structure
        this.emissions = parseEmissions();
    }

    private Network loadNetworkAndPopulateLinkCashe() {

        var originalNetwork = NetworkUtils.readNetwork(input.networkFile);
        var geometryFactory = new GeometryFactory();
        var preparedGeometryFactory = new PreparedGeometryFactory();

        log.info("Put buffer of 30m around each link. Then filter for buffers within bounds of study area. #Links: " + originalNetwork.getLinks().size());
        var boundsGeometry = preparedGeometryFactory.create(linkCache.getBounds().toGeometry());
        var buffers = originalNetwork.getLinks().values().parallelStream()
                // currently pt doesn't have emissions.
                .filter(link -> !link.getId().toString().startsWith("pt"))
                .map(link -> {
                    var lineString = geometryFactory.createLineString(new Coordinate[]{
                            MGC.coord2Coordinate(link.getFromNode().getCoord()),
                            MGC.coord2Coordinate(link.getToNode().getCoord())
                    });
                    // we use a buffer of 30m, because links wich are further away don't really add emissions to a receiver
                    // point.
                    return Tuple.of(link.getId(), lineString.buffer(30));
                })
                // select all the links wich buffer 'touches' the study area.
                .filter(tuple -> boundsGeometry.intersects(tuple.getSecond()))
                // make prepared geometries, to speed up the query when filling the link cache
                .map(tuple -> Tuple.of(tuple.getFirst(), preparedGeometryFactory.create(tuple.getSecond())))
                .collect(Collectors.toSet());

        log.info("creating link cache. Size of buffer list after filtering for bounds: " + buffers.size());
        var counter = new AtomicInteger();
        var size = linkCache.getXLength() * linkCache.getYLength();
        linkCache.setValueForEachCoordinate((x, y) -> {
            var point = MGC.xy2Point(x, y);

            var currentCount = counter.incrementAndGet();
            if (currentCount % 100000 == 0) {
                log.info("create link cache #" + currentCount + " / " + size);
            }

            // store all the link ids for a receiver point which are covered by the areas around the link
            return buffers.stream()
                    .filter(tuple -> tuple.getSecond().covers(point))
                    .map(Tuple::getFirst)
                    .collect(Collectors.toSet());
        });

        log.info("Create set of links which should be in the network.");
        var linkList = new HashSet<Id<Link>>();
        linkCache.forEachIndex((xi, yi, value) -> linkList.addAll(value));

        log.info("Filter Network for links within buffer range. Collecting " + linkList.size() + " links.");
        return originalNetwork.getLinks().values().stream()
                .filter(link -> linkList.contains(link.getId()))
                .collect(NetworkUtils.getCollector());
    }

    private ObjectRaster<Set<Id<Link>>> setUpLinkCash() {

        log.info("Peek into palm file to populate the link cash raster");
        var palmOutput = PalmOutputReader.read(input.palmOutputFile, 0, 0);
        var raster = palmOutput.getTimeBins().iterator().next().getValue().values().iterator().next();

        return new ObjectRaster<>(raster.getBounds(), raster.getCellSize());
    }

    private TimeBinMap<Map<String, Object2DoubleMap<Link>>> parseEmissions() {

        var handler = new AggregateEmissionsByTimeHandler(network, Set.of(Pollutant.PM), input.timeBinSize, input.scaleFactor);
        var manager = EventsUtils.createEventsManager();
        manager.addHandler(handler);

        log.info("Start parsing emission events");
        new EmissionEventsReader(manager).readFile(input.emissionEventsFile);

        log.info("Start converting collected emissions.");
        TimeBinMap<Map<String, Object2DoubleMap<Link>>> result = new TimeBinMap<>(input.timeBinSize);
        var handlerMap = handler.getTimeBinMap();
        var nameConverter = new PollutantToPalmNameConverter(Map.of(
                Pollutant.NO2, "NO2",
                Pollutant.CO2_TOTAL, "CO2",
                Pollutant.PM, "PM10",
                Pollutant.PM_non_exhaust, "PM10_non_exhaust",
                Pollutant.CO, "CO",
                Pollutant.NOx, "NOx"
        ));

        for (var bin : handlerMap.getTimeBins()) {

            TimeBinMap.TimeBin<Map<String, Object2DoubleMap<Link>>> resultBin = result.getTimeBin(bin.getStartTime());
            if (!resultBin.hasValue()) {
                resultBin.setValue(new HashMap<>());
            }
            Map<String, Object2DoubleMap<Link>> pollutantMap = resultBin.getValue();

            for (var pollutantEntry : bin.getValue().entrySet()) {

                var palmKey = nameConverter.getPalmName(pollutantEntry.getKey());

                var emissionResultMap = pollutantMap.computeIfAbsent(palmKey, key -> new Object2DoubleOpenHashMap<>());
                var emissionMap = pollutantEntry.getValue();
                for (Object2DoubleMap.Entry<Id<Link>> idEntry : emissionMap.object2DoubleEntrySet()) {
                    var link = network.getLinks().get(idEntry.getKey());

                    // we must use merge here, since pm and pm_non_exhaust map to pm10 in palm
                    emissionResultMap.mergeDouble(link, idEntry.getDoubleValue(), Double::sum);
                }
                pollutantMap.put(palmKey, emissionResultMap);
            }
        }
        return result;
    }

    private void collectRForEachTimeslice() {

        // set the index manually for now. Maybe this is sufficient already
        int startIndex = input.startIndex;
        int endIndex = input.endIndex;

        for (int i = startIndex; i <= endIndex; i++) {
            log.info("calculate r for time index: " + i);
            // read a single time slice at a time
            var palmOutput = PalmOutputReader.read(input.palmOutputFile, i, i);
            // lets start with pm 10 only.
            var pm10Raster = palmOutput.getTimeBins().iterator().next().getValue().get("PM10");

            // cut in the center of the area
            //var smallBounds = new DoubleRaster.Bounds(3000, 2000, 4000,3000);
            //var smallPm10Raster = new DoubleRaster(smallBounds, pm10Raster.getCellSize());
           //smallPm10Raster.setValueForEachIndex(pm10Raster::getValueByIndex);
            //pm10Raster = smallPm10Raster;

            // get the corresponding time slice from the emissions
            var emissionBin = emissions.getTimeBin((i + input.indexOffset) * input.timeBinSize + 1);

            // if we have a small sample, there might be no emissions at all.
            if (emissionBin == null || !emissionBin.hasValue()) {
                continue;
            }

            var pm10Emissions = emissionBin.getValue().get("PM10");

            log.info("call collectR");
            var rasterOfRs = AverageSmoothingRadiusEstimate.collectR(pm10Raster, pm10Emissions);

            log.info("after collectR");
            log.info("writing csv to: " + input.outputFile);
            try (var writer = Files.newBufferedWriter(Paths.get(input.outputFile + "_" + i + ".csv")); var printer = CSVFormat.DEFAULT.withDelimiter(',').withHeader("x", "y", "R").print(writer)) {
                rasterOfRs.forEachCoordinate((x, y, value) -> printValue(x, y, value, printer));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        log.info("done.");
    }

    private static class InputArgs {

        @Parameter(names = "-e", required = true)
        private String emissionEventsFile;

        @Parameter(names = "-n", required = true)
        private String networkFile;

        @Parameter(names = "-p", required = true)
        private String palmOutputFile;

        @Parameter(names = "-o", required = true)
        private String outputFile;

        @Parameter(names = "-s")
        private int scaleFactor = 10;

        @Parameter(names = "-si")
        private int startIndex = 0;

        @Parameter(names = "-ei")
        private int endIndex = Integer.MAX_VALUE;

        @Parameter(names = "-io")
        private int indexOffset = 0;

        @Parameter(names = "-ts")
        private int timeBinSize = 3600;
    }

    private static void printValue(double x, double y, double value, CSVPrinter printer) {
        try {
            printer.printRecord(x, y, value);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
