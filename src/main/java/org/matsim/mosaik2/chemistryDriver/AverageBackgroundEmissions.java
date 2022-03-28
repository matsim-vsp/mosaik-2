package org.matsim.mosaik2.chemistryDriver;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.google.common.util.concurrent.AtomicDouble;
import lombok.extern.log4j.Log4j2;
import org.matsim.contrib.analysis.time.TimeBinMap;
import org.matsim.core.utils.collections.Tuple;
import org.matsim.mosaik2.raster.Raster;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Log4j2
public class AverageBackgroundEmissions {

    // the date of the stuttgart run. This date is chosen by the team of LUH
    private static final LocalDateTime dateOfStudy = LocalDateTime.of(2018, 7, 8, 0, 0);

    // the background emissions are from 2015. Since the week days have other dates than 2018 we have picked this date which is also
    // a sunday during summer. Since we are simulating 48 hours. We will also have a workday within the PALM simulation run.
    // since we are simulating the run in utc time, we have a two hour offset compared to the utc-000 time zone. Hence, the
    // 2 hour value
    private static final LocalDateTime dateOfBackgroundEmissions = LocalDateTime.of(2015, 6, 5, 2, 0);

    private static final Set<String> pollutants = Set.of("NO2", "NO", "PM10");
    //private static final Set<String> pollutants = Set.of("NO2"); // use only one pollutant for debugging

    @Parameter(names = "-input", required = true)
    private String input;

    @Parameter(names = "-output", required = true)
    private String output;

    public static void main(String[] args) {

        var averager = new AverageBackgroundEmissions();
        JCommander.newBuilder().addObject(averager).build().parse(args);
        averager.average();
    }

    void average() {
        var fromTime = getSecondsSinceBeginningOfYear(dateOfBackgroundEmissions);
        var toTime = fromTime + 47 * 3600; // take 48 hours (starting at 0 I guess)
        //var toTime = fromTime + 1 * 3600; // take one hour for debugging

        var fromTimeIndex = (int) fromTime / 3600;
        var toTimeIndex = (int) toTime / 3600;

        var rawBackgroundEmissions = PalmChemistryInputReader.read(this.input, fromTimeIndex, toTimeIndex);
        var backgroundEmissions = setTimesRelativeToDay(rawBackgroundEmissions);
        TimeBinMap<Map<String, Raster>> result = new TimeBinMap<>(rawBackgroundEmissions.getBinSize(), 0);

        for (var timeBin : backgroundEmissions.getTimeBins()) {
            log.info("blurring values for: " + timeBin.getStartTime());
            var avaragedValue = timeBin.getValue().entrySet().stream()
                    .filter(entry -> pollutants.contains(entry.getKey()))
                    .map(entry -> {
                        var averagedRaster = average(entry.getValue());
                        return Tuple.of(entry.getKey(), averagedRaster);
                    })
                    .collect(Collectors.toMap(Tuple::getFirst, Tuple::getSecond));

            var resultBin = result.getTimeBin(timeBin.getStartTime());
            resultBin.setValue(avaragedValue);
        }

        PalmChemistryInput2.writeNetCdfFile(output, result, dateOfStudy);
    }

    Raster average(Raster raster) {

        // sum up all values
        AtomicDouble sum = new AtomicDouble();
        raster.forEachIndex((xi, yi, value) -> sum.getAndAdd(value));
        int numberOfEntries = raster.getXLength() * raster.getYLength();
        double average = sum.doubleValue() / numberOfEntries;

        var result = new Raster(raster.getBounds(), raster.getCellSize());
        result.setValueForEachIndex((xi, yi) -> average);
        return result;
    }

    static long getSecondsSinceBeginningOfYear(LocalDateTime date) {

        var year = date.getYear();
        var beginningOfYear = LocalDateTime.of(year, 1, 1, 0, 0);
        var duration = Duration.between(beginningOfYear, date);
        return duration.getSeconds();
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
}
