package org.matsim.mosaik2.chemistryDriver;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import lombok.extern.log4j.Log4j2;
import org.matsim.contrib.analysis.time.TimeBinMap;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Log4j2
public class MergeBackgroundAndTrafficEmissions {

    // the date of the stuttgart run. This date is chosen by the team of LUH
    private static final LocalDateTime dateOfStudy = LocalDateTime.of(2018, 7, 8, 0, 0);

    // the background emissions are from 2015. Since the week days have other dates than 2018 we have picked this date which is also
    // a sunday during summer. Since we are simulating 48 hours. We will also have a workday within the PALM simulation run.
    private static final LocalDateTime dateOfBackgroundEmissions = LocalDateTime.of(2015, 6, 5, 0, 0);

    @Parameter(names = "-backgroundFile", required = true)
    private String backgroundFile;

    @Parameter(names = "-trafficFile", required = true)
    private String trafficFile;

    @Parameter(names = "-outputFile", required = true)
    private String outputFile;

    public static void main(String[] args) {

        var merger = new MergeBackgroundAndTrafficEmissions();
        JCommander.newBuilder().addObject(merger).build().parse(args);
        merger.merge();
    }

    void merge() {

        var fromTime = getSecondsSinceBeginningOfYear(dateOfBackgroundEmissions);
        var toTime = fromTime + 47 * 3600; // take 48 hours (starting at 0 I guess)

        var fromTimeIndex = (int) fromTime / 3600;
        var toTimeIndex = (int) toTime / 3600;
        var rawBackgroundEmissions = PalmChemistryInputReader.read(this.backgroundFile, fromTimeIndex, toTimeIndex);

        var backgroundEmissions = setTimesRelativeToDay(rawBackgroundEmissions);

        var trafficEmissions = PalmChemistryInputReader.read(this.trafficFile, 0, Integer.MAX_VALUE);

        TimeBinMap<Map<String, Raster>> mergeResult = new TimeBinMap<>(rawBackgroundEmissions.getBinSize(), 0);

        // merge the values
        for (TimeBinMap.TimeBin<Map<String, Raster>> backgroundEmissionsTimeBin : backgroundEmissions.getTimeBins()) {
            var trafficEmissionsTimeBin = trafficEmissions.getTimeBin(backgroundEmissionsTimeBin.getStartTime());
            var mergedBin = mergeResult.getTimeBin(backgroundEmissionsTimeBin.getStartTime());
            mergedBin.setValue(new HashMap<>());

            for (var pollutant : trafficEmissionsTimeBin.getValue().entrySet()) {
                if (!backgroundEmissionsTimeBin.getValue().containsKey(pollutant.getKey())) {
                    log.warn("could not find: " + pollutant.getKey() + " in background emissions");
                }
                else {
                    var backgroundRaster = backgroundEmissionsTimeBin.getValue().get(pollutant.getKey());
                    var trafficRaster = pollutant.getValue();
                    var mergedRaster = new Raster(backgroundRaster.getBounds(), backgroundRaster.getCellSize());

                    mergedRaster.setValueForEachIndex((xi, yi) -> {
                        var backgroundValue = backgroundRaster.getValueByIndex(xi, yi);
                        var trafficValue = trafficRaster.getValueByIndex(xi, yi);
                        return backgroundValue + trafficValue;
                    });

                    mergedBin.getValue().put(pollutant.getKey(), mergedRaster);
                }
            }
        }

        PalmChemistryInput2.writeNetCdfFile(outputFile, mergeResult, dateOfStudy);
    }

    static TimeBinMap<Map<String, Raster>> setTimesRelativeToDay(TimeBinMap<Map<String, Raster>> source) {
        TimeBinMap<Map<String, Raster>> result = new TimeBinMap<>(source.getBinSize(), 0); // we start at 0

        for (var sourceBin : source.getTimeBins()) {

            var newTime = sourceBin.getStartTime() - source.getStartTime();
            var copyBin = result.getTimeBin(newTime);
            copyBin.setValue(sourceBin.getValue());
        }

        return result;
    }

    static long getSecondsSinceBeginningOfYear(LocalDateTime date) {

        var year = date.getYear();
        var beginningOfYear = LocalDateTime.of(year, 1, 1, 0, 0);
        var duration = Duration.between(beginningOfYear, date);
        return duration.getSeconds();
    }
}
