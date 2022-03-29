package org.matsim.mosaik2.analysis;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import it.unimi.dsi.fastutil.objects.AbstractObject2DoubleMap;
import it.unimi.dsi.fastutil.objects.Object2DoubleMap;
import it.unimi.dsi.fastutil.objects.Object2DoubleOpenHashMap;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.network.Link;
import org.matsim.contrib.emissions.Pollutant;
import org.matsim.contrib.emissions.events.EmissionEventsReader;
import org.matsim.core.events.EventsUtils;
import org.matsim.core.network.NetworkUtils;
import org.matsim.mosaik2.chemistryDriver.AggregateEmissionsByTimeHandler;
import org.matsim.mosaik2.palm.PalmOutputReader;
import org.matsim.mosaik2.raster.DoubleRaster;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Log4j2
public class AverageSmoothingRadiusEstimate {

    public static DoubleRaster collectR(DoubleRaster raster, Object2DoubleMap<Link> emissions) {

        var result = new DoubleRaster(raster.getBounds(), raster.getCellSize());
        var size = raster.getYLength() * raster.getXLength();
        var counter = new AtomicInteger();

        log.info("Starting to calculate R for each cell. We have " + size + " cells.");

        result.setValueForEachCoordinate((x, y) -> {
            var receiverPoint = new Coord(x, y);
            var value = raster.getValueByCoord(x, y);
            // assuming that receiver points with 0 emissions are palm-buildings
            var r = value <= -1 ? 0.0 : NumericSmoothingRadiusEstimate.estimateRWithBisect(emissions, receiverPoint, value);
            var currentCount = counter.incrementAndGet();
            if (currentCount % 100000 == 0) {
                log.info("Calculated " + currentCount + "/" + size + " R-Values. Last value was: " + r);
            }
            return r;
        });
        log.info("Finished R calculation");

        return result;
    }

    public static void main(String[] args) {

        var input = new InputArgs();
        JCommander.newBuilder().addObject(input).build().parse(args);

        log.info("loading palm data. Time slice is 8:00 - 8:15");
        // load the 8 o clock time slice
        var palmOutput = PalmOutputReader.read(input.palmOutputFile, 8 * 4, 8 * 4);
        // take the pm10 raster
        var pm10Raster = palmOutput.getTimeBins().iterator().next().getValue().get("PM10");

        // cut 100x100 m in the center of the area.
       // var smallBounds = new Raster.Bounds(pm10Raster.getBounds().getMinX() + 950, pm10Raster.getBounds().getMinY() + 950, pm10Raster.getBounds().getMaxX() - 950, pm10Raster.getBounds().getMaxY() - 950);
       // var smallPm10Raster = new Raster(smallBounds, pm10Raster.getCellSize());
       // smallPm10Raster.setValueForEachIndex(pm10Raster::getValueByIndex);

        log.info("Finished loading palm data");
        log.info("Start loading Network.");
        // collect emissions
        var network = NetworkUtils.readNetwork(input.networkFile);
        var handler = new AggregateEmissionsByTimeHandler(network, Set.of(Pollutant.PM), 3600, input.scaleFactor);
        var manager = EventsUtils.createEventsManager();
        manager.addHandler(handler);

        log.info("Start parsing emission events");
        new EmissionEventsReader(manager).readFile(input.emissionEventsFile);

        log.info("Start converting collected emissions. Take time slice form 8am, filter all links without emissions");
        var emissionsByTime = handler.getTimeBinMap();
        var emissionsById = emissionsByTime.getTimeBin(8 * 3600).getValue().get(Pollutant.PM);
        var emissions = emissionsById.object2DoubleEntrySet().stream()
                .filter(idEntry -> idEntry.getDoubleValue() >= 0)
                .map(idEntry -> new AbstractObject2DoubleMap.BasicEntry<Link>(network.getLinks().get(idEntry.getKey()), idEntry.getDoubleValue()))
                .collect(Collectors.toMap(AbstractObject2DoubleMap.BasicEntry::getKey, AbstractObject2DoubleMap.BasicEntry::getDoubleValue, Double::sum, Object2DoubleOpenHashMap::new));

        log.info("call collectR");
        var rasterOfRs = AverageSmoothingRadiusEstimate.collectR(pm10Raster, emissions);
        log.info("after collectR");
        log.info("writing csv to: " + input.outputFile);
        try (var writer = Files.newBufferedWriter(Paths.get(input.outputFile)); var printer = CSVFormat.DEFAULT.withDelimiter(',').withHeader("x", "y", "R").print(writer) ) {
            rasterOfRs.forEachCoordinate((x, y, value) -> printValue(x, y, value, printer));
        } catch (IOException e) {
            e.printStackTrace();
        }
        log.info("done.");
    }

    private static void printValue(double x, double y, double value, CSVPrinter printer) {
        try {
            printer.printRecord(x, y, value);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
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
        private final int scaleFactor = 10;
    }
}
