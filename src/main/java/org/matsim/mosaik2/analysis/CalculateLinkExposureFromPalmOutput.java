package org.matsim.mosaik2.analysis;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import it.unimi.dsi.fastutil.objects.Object2DoubleArrayMap;
import it.unimi.dsi.fastutil.objects.Object2DoubleMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.lang3.StringUtils;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.contrib.analysis.time.TimeBinMap;
import org.matsim.mosaik2.DoubleToDoubleFunction;
import org.matsim.mosaik2.palm.PalmMergedOutputReader;
import org.matsim.mosaik2.raster.DoubleRaster;
import org.matsim.mosaik2.raster.ObjectRaster;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Stream;

import static org.matsim.mosaik2.Utils.createWriteFormat;

@Log4j2
public class CalculateLinkExposureFromPalmOutput {

    private static final String AV_MASKED_PALM_TEMPLATE = "%s_av_masked_M01.%s.nc";
    private static final String AV_MASKED_DAY2_CSV_TEMPLATE = "%s_av_masked_M01.day2-si-units.xyt.csv";

    public static void main(String[] args) {

        var inputArgs = new InputArgs();
        JCommander.newBuilder().addObject(inputArgs).build().parse(args);
        run(inputArgs);

    }

    private static void run(InputArgs inputArgs) {

        var files = Stream.iterate(0, i -> i + 1)
                .map(i -> getPalmMaskedFilePath(inputArgs.root, inputArgs.palmRunId, i))
                .limit(inputArgs.numFileParts)
                .toList();

        // this might be worth writing to either csv or netcdf
        var allEmissions = PalmMergedOutputReader.readFiles(files, inputArgs.species);

        // we only want the second day
        var secondDayEmissions = getSecondDayInLocalTime(allEmissions, inputArgs.startTime, inputArgs.utcOffset);
        convertToSiUnits(secondDayEmissions);
        writePalmOutputToCsv(getDay2CSVPath(inputArgs.root, inputArgs.palmRunId), secondDayEmissions, inputArgs.species);

        // calculate link contributions to pollution
        var network = CalculateRValues.loadNetwork(inputArgs.networkFile, allEmissions.getTimeBins().iterator().next().getValue().values().iterator().next().getBounds().toGeometry());
        var linkContributions = calculateLinkContributions(secondDayEmissions, network);

        writeLinkContributionsToCsv(Paths.get(inputArgs.root).resolve("link-contributions.csv"), linkContributions, inputArgs.species);
    }

    private static TimeBinMap<Map<Id<Link>, LinkValue>> calculateLinkContributions(TimeBinMap<Map<String, DoubleRaster>> data, Network network) {

        // assuming we have at least one time bin with one raster.
        var exampleRaster = data.getTimeBins().iterator().next().getValue().values().iterator().next();

        var linkIndex = new SpatialIndex(network, 250, exampleRaster.getBounds().toGeometry());
        ObjectRaster<Set<Id<Link>>> linkCache = new ObjectRaster<>(exampleRaster.getBounds(), exampleRaster.getCellSize());
        log.info("Creating raster cache with link ids");
        linkCache.setValueForEachCoordinate(linkIndex::query);

        var result = new TimeBinMap<Map<Id<Link>, LinkValue>>(data.getBinSize());
        for (var bin : data.getTimeBins()) {
            result.getTimeBin(bin.getStartTime()).computeIfAbsent(HashMap::new);
        }

        data.getTimeBins().parallelStream().forEach(bin -> {

            log.info("Calculating link contributions for time step: " + bin.getStartTime());
            var resultBin = result.getTimeBin(bin.getStartTime());
            for (var speciesEntry : bin.getValue().entrySet()) {

                exampleRaster.forEachCoordinate((x, y, exampleValue) -> {

                    if (exampleValue <= 0.) return; // nothing to do here

                    var value = speciesEntry.getValue().getValueByCoord(x, y);
                    var linkIds = linkCache.getValueByCoord(x, y);
                    linkIds.stream()
                            .map(id -> network.getLinks().get(id))
                            .forEach(link -> {

                                var weight = NumericSmoothingRadiusEstimate.calculateWeight(
                                        link.getFromNode().getCoord(),
                                        link.getToNode().getCoord(),
                                        new Coord(x, y),
                                        link.getLength(),
                                        50 // TODO make configurable
                                );
                                if (weight > 0.0) {
                                    var impactValue = value * weight;
                                    resultBin.getValue().computeIfAbsent(link.getId(), _id -> new LinkValue()).addValue(speciesEntry.getKey(), impactValue);
                                }
                            });
                });
            }
        });
        return result;
    }

    private static void writeLinkContributionsToCsv(Path output, TimeBinMap<Map<Id<Link>, LinkValue>> data, Collection<String> species) {

        log.info("Writing link contributions to : " + output);

        var header = new java.util.ArrayList<>(List.of("id", "time"));
        header.addAll(species);

        try (var writer = Files.newBufferedWriter(output);
             var printer = new CSVPrinter(writer, createWriteFormat(header.toArray(new String[0])))) {

            for (var bin : data.getTimeBins()) {

                var time = bin.getStartTime();
                for (var entry : bin.getValue().entrySet()) {
                    var id = entry.getKey();
                    printer.print(id);
                    printer.print(time);
                    for (var speciesName : species) {
                        var value = entry.getValue().get(speciesName);
                        printer.print(value);
                    }
                    printer.println();
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void writePalmOutputToCsv(Path output, TimeBinMap<Map<String, DoubleRaster>> data, Collection<String> species) {
        log.info("Writing t,x,y,value data to: " + output);

        // assuming we have at least one time bin with one raster.
        var rasterToIterate = data.getTimeBins().iterator().next().getValue().values().iterator().next();
        var header = new java.util.ArrayList<>(List.of("time", "x", "y"));
        header.addAll(species);
        try (var writer = Files.newBufferedWriter(output); var printer = createWriteFormat(header.toArray(new String[0])).print(writer)) {

            for (var bin : data.getTimeBins()) {
                var time = bin.getStartTime();

                log.info("Writing time slices: [" + time + ", " + (time + data.getBinSize()) + "]");

                rasterToIterate.forEachCoordinate((x, y, value) -> {
                    if (value < 0) return; // this means this raster point is a building

                    print(printer, time);
                    print(printer, x);
                    print(printer, y);
                    for (var speciesName : species) {
                        var speciesRaster = bin.getValue().get(speciesName);
                        var speciesValue = speciesRaster.getValueByCoord(x, y);
                        print(printer, speciesValue);
                    }
                    println(printer); // new line
                });

            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        log.info("Finished writing to: " + output);
    }

    private static void print(CSVPrinter printer, Object value) {

        try {
            printer.print(value);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void println(CSVPrinter printer) {

        try {
            printer.println();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * This method mutates the data within the data map!
     */
    private static void convertToSiUnits(TimeBinMap<Map<String, DoubleRaster>> data) {

        log.info("Converting to SI-Units");
        for (var bin : data.getTimeBins()) {
            for (var speciesEntry : bin.getValue().entrySet()) {

                var converter = getConverterFunction(speciesEntry.getKey());
                var raster = speciesEntry.getValue();
                raster.transformEachValue(converter);
            }
        }
    }

    private static TimeBinMap<Map<String, DoubleRaster>> getSecondDayInLocalTime(TimeBinMap<Map<String, DoubleRaster>> source, double startTime, double utcOffset) {

        log.info("Converting time bins into local time. Utc Offset is: " + utcOffset + " Taking only time slices after " + startTime);
        TimeBinMap<Map<String, DoubleRaster>> result = new TimeBinMap<>(source.getBinSize());

        for (var bin : source.getTimeBins()) {
            var localTime = utcToLocalTimeWithWrapAround(bin.getStartTime(), utcOffset);
            var localTimeFromStart = localTime - startTime;

            if (localTime >= startTime) {
                result.getTimeBin(localTimeFromStart).setValue(bin.getValue());
            }
        }
        return result;
    }

    private static Path getPalmMaskedFilePath(String root, String runId, int number) {
        return Paths.get(root).resolve(getPalmMaskedFileName(runId, number));
    }

    private static String getPalmMaskedFileName(String runId, int number) {
        var partNumber = StringUtils.leftPad(Integer.toString(number), 3, '0');
        return String.format(AV_MASKED_PALM_TEMPLATE, runId, partNumber);
    }

    private static Path getDay2CSVPath(String root, String runId) {
        var name = String.format(AV_MASKED_DAY2_CSV_TEMPLATE, runId);
        return Paths.get(root).resolve(name);
    }

    private static DoubleToDoubleFunction getConverterFunction(String species) {
        return switch (species) {
            case "PM10" ->
                // converts palm's kg/m3 into g/m3
                    value -> value * 1000;
            case "NO2" ->
                // converts palm's ppm into g/m3. We use Normal Temperature and Pressure (NTP) where 1 mole of gas is
                // equal to 24.45L of gas. Then we divide by 1000 to convert mg into g
                // value * molecularWeight / 22.45 / 1000
                    value -> value * 46.01 / 22.45 / 1000;
            default -> throw new RuntimeException("No conversion implemented for species: " + species);
        };
    }

    private static double utcToLocalTimeWithWrapAround(double startTime, double utcOffset) {

        // the berlin run is UTC+2, Example:
        // 	PALM second 0 is 7200 (2am) in MATSim
        //  PALM av second 3600 means 0am - 1am UTC corresponding to 2am - 3am (7200 - 10800s)
        // the hours after 12pm were prepended to the beginning of the palm run. Reverse this
        // here now.
        // use (48h + 1h) * 3600 as threshold to wrap around. (Palm seconds are slightly off, so use the start value
        // of the next hour to be save.
        // See FullFeaturedConverter::getInputSeconds
        var localTime = startTime + utcOffset;
        return localTime >= 176400 ? localTime - 86400 : localTime;
    }

    @RequiredArgsConstructor
    private static class LinkValue {

        private final Object2DoubleMap<String> values = new Object2DoubleArrayMap<>();

        void addValue(String species, double value) {
            values.mergeDouble(species, value, Double::sum);
        }

        double get(String species) {
            return values.getDouble(species);
        }
    }

    @SuppressWarnings({"FieldMayBeFinal", "unused"})
    static class InputArgs {

        @Parameter(names = "-palmRunId", required = true)
        private String palmRunId;

        @Parameter(names = "-root", required = true)
        private String root;

        @Parameter(names = "-network", required = true)
        private String networkFile;

        @Parameter(names = "-numFileParts")
        private int numFileParts = 5;

        @Parameter(names = "-offset")
        private double utcOffset = 7200;

        @Parameter(names = "-s")
        private int scaleFactor = 10;

        @Parameter(names = "-start-time")
        private double startTime = 86400;

        @Parameter(names = "-species")
        private List<String> species = List.of("NO2", "PM10");
    }
}