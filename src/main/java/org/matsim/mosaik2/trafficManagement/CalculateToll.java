package org.matsim.mosaik2.trafficManagement;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.matsim.contrib.analysis.time.TimeBinMap;
import org.matsim.contrib.roadpricing.RoadPricingSchemeImpl;
import org.matsim.contrib.roadpricing.RoadPricingWriterXMLv1;
import org.matsim.mosaik2.DoubleToDoubleFunction;
import org.matsim.mosaik2.Utils;
import org.matsim.mosaik2.analysis.run.CSVUtils;
import org.matsim.mosaik2.palm.PalmMergedOutputReader;
import org.matsim.mosaik2.raster.DoubleRaster;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

@Log4j2
public class CalculateToll {
    private static final String AV_MASKED_PALM_TEMPLATE = "%s_av_masked_M01.%s.nc";
    private static final String AV_MASKED_DAY2_CSV_TEMPLATE = "%s_av_masked_M01.day2-si-units.xyt.csv";
    private static final String CONCENTRATION_TOLL_OUTPUT_TEMPLATE = "%s.day2-link-concentration-tolls.xml";
    private static final String EXPOSURE_TOLL_OUTPUT_TEMPLATE = "%s.day2-link-exposure-tolls.xml";
    private static final String CONCENTRATION_TOLL_OUTPUT_CSV_TEMPLATE = "%s.day2-link-concentration-tolls.csv";
    private static final String EXPOSURE_TOLL_OUTPUT_CSV_TEMPLATE = "%s.day2-link-exposure-tolls.csv";
    private static final String CONCENTRATION_CONTRIBUTION_CSV_TEMPLATE = "%s.day2-link-concentration-contributions.csv";
    private static final String EXPOSURE_CONTRIBUTION_CSV_TEMPLATE = "%s.day2-link-exposure-contributions.csv";

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
        var network = Utils.loadFilteredNetwork(inputArgs.networkFile, allEmissions.getTimeBins().iterator().next().getValue().values().iterator().next().getBounds().toGeometry());
        var linkContributions = LinkContributions.calculate(secondDayEmissions, network);

        var cellVolume = calculateCellVolume(secondDayEmissions);
        var scheme = LinkContributions.createRoadPricingScheme(linkContributions, cellVolume, inputArgs.scaleFactor);
        LinkContributions.writeToCsv(getConcentrationContributionsPath(inputArgs.root, inputArgs.palmRunId), linkContributions, inputArgs.species);
        new RoadPricingWriterXMLv1(scheme).writeFile(getConcentrationTollOutputPath(inputArgs.root, inputArgs.palmRunId).toString());
        writeTollToCsv(getConcentrationTollCsvOutputPath(inputArgs.root, inputArgs.palmRunId), scheme, linkContributions);

        // calculate link contributions to exposure
        var exposureData = ActivityExposure.calculate(Paths.get(inputArgs.eventsFile), secondDayEmissions);
        var exposureContributions = LinkContributions.calculate(exposureData, network);
        //TODO this is perhaps not using the right cost factors
        var exposureScheme = LinkContributions.createRoadPricingScheme(exposureContributions, cellVolume, inputArgs.scaleFactor);
        LinkContributions.writeToCsv(getExposureContributionsPath(inputArgs.root, inputArgs.palmRunId), exposureContributions, inputArgs.species);
        new RoadPricingWriterXMLv1(exposureScheme).writeFile(getExposureTollOutputPath(inputArgs.root, inputArgs.palmRunId).toString());
        writeTollToCsv(getExposureTollCsvOutputPath(inputArgs.root, inputArgs.palmRunId), exposureScheme, exposureContributions);
    }

    private static <T> void writeTollToCsv(Path output, RoadPricingSchemeImpl scheme, TimeBinMap<T> timeBinMap) {

        log.info("Writing road pricing scheme to csv");
        CSVUtils.writeTable(timeBinMap.getTimeBins(), output, List.of("id", "time", "toll"), (p, bin) -> {
            var time = bin.getStartTime();
            for (var id : scheme.getTolledLinkIds()) {

                var cost = scheme.getTypicalLinkCostInfo(id, time);
                if (cost == null) continue;

                CSVUtils.printRecord(p, id, time, cost.amount);
            }
        });
    }

    private static void writePalmOutputToCsv(Path output, TimeBinMap<Map<String, DoubleRaster>> data, Collection<String> species) {
        log.info("Writing t,x,y,value data to: " + output);

        // assuming we have at least one time bin with one raster.
        var rasterToIterate = data.getTimeBins().iterator().next().getValue().values().iterator().next();
        var header = new java.util.ArrayList<>(List.of("time", "x", "y"));
        header.addAll(species);

        CSVUtils.writeTable(data.getTimeBins(), output, header, (p, bin) -> {
            var time = bin.getStartTime();

            log.info("Writing time slices: [" + time + ", " + (time + data.getBinSize()) + "]");

            rasterToIterate.forEachCoordinate((x, y, value) -> {
                if (value < 0) return; // this means this raster point is a building

                CSVUtils.print(p, time);
                CSVUtils.print(p, x);
                CSVUtils.print(p, y);
                for (var speciesName : species) {
                    var speciesRaster = bin.getValue().get(speciesName);
                    var speciesValue = speciesRaster.getValueByCoord(x, y);
                    CSVUtils.print(p, speciesValue);
                }
                CSVUtils.println(p); // new line
            });
        });
        log.info("Finished writing to: " + output);
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

    private static Path getConcentrationTollOutputPath(String root, String runId) {
        var name = String.format(CONCENTRATION_TOLL_OUTPUT_TEMPLATE, runId);
        return Paths.get(root).resolve(name);
    }

    private static Path getConcentrationTollCsvOutputPath(String root, String runId) {
        var name = String.format(CONCENTRATION_TOLL_OUTPUT_CSV_TEMPLATE, runId);
        return Paths.get(root).resolve(name);
    }

    private static Path getConcentrationContributionsPath(String root, String runId) {
        var name = String.format(CONCENTRATION_CONTRIBUTION_CSV_TEMPLATE, runId);
        return Paths.get(root).resolve(name);
    }

    private static Path getExposureTollOutputPath(String root, String runId) {
        var name = String.format(EXPOSURE_TOLL_OUTPUT_TEMPLATE, runId);
        return Paths.get(root).resolve(name);
    }

    private static Path getExposureTollCsvOutputPath(String root, String runId) {
        var name = String.format(EXPOSURE_TOLL_OUTPUT_CSV_TEMPLATE, runId);
        return Paths.get(root).resolve(name);
    }

    private static Path getExposureContributionsPath(String root, String runId) {
        var name = String.format(EXPOSURE_CONTRIBUTION_CSV_TEMPLATE, runId);
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
            case "NO" -> value -> value * 30.0061 / 22.5 / 1000;
            case "O3" -> value -> value * 47.9982 / 22.5 / 1000;
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

    private static double calculateCellVolume(TimeBinMap<Map<String, DoubleRaster>> data) {
        var cellSize = data.getTimeBins().iterator().next().getValue().values().iterator().next().getCellSize();
        return cellSize * cellSize * cellSize;
    }

    @SuppressWarnings({"FieldMayBeFinal", "unused"})
    static class InputArgs {

        @Parameter(names = "-palmRunId", required = true)
        private String palmRunId;

        @Parameter(names = "-root", required = true)
        private String root;

        @Parameter(names = "-network", required = true)
        private String networkFile;

        @Parameter(names = "-events", required = true)
        private String eventsFile;

        @Parameter(names = "-numFileParts")
        private int numFileParts = 5;

        @Parameter(names = "-offset")
        private double utcOffset = 7200;

        @Parameter(names = "-s")
        private int scaleFactor = 1;

        @Parameter(names = "-start-time")
        private double startTime = 86400;

        @Parameter(names = "-species")
        private List<String> species = List.of("NO2", "PM10");
    }
}