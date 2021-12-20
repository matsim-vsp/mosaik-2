package org.matsim.mosaik2.chemistryDriver;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.math3.util.CombinatoricsUtils;
import org.matsim.contrib.analysis.time.TimeBinMap;
import org.matsim.core.utils.collections.Tuple;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Log4j2
public class BlurBackgroundEmissions {

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

    @Parameter(names = "-radius", required = true)
    private int radius;

    public static void main(String[] args) {

        var blurrer = new BlurBackgroundEmissions();
        JCommander.newBuilder().addObject(blurrer).build().parse(args);
        blurrer.blur();
    }

    void blur() {

        log.info("Using date fo Background-Emissions of: " + dateOfBackgroundEmissions.toString());

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
            var blurredValue = timeBin.getValue().entrySet().stream()
                    .filter(entry -> pollutants.contains(entry.getKey()))
                    .map(entry -> {
                        var blurredRaster = blurMultipleTimes(entry.getValue(), radius);
                        return Tuple.of(entry.getKey(), blurredRaster);
                    })
                    .collect(Collectors.toMap(Tuple::getFirst, Tuple::getSecond));

            var resultBin = result.getTimeBin(timeBin.getStartTime());
            resultBin.setValue(blurredValue);
        }

        PalmChemistryInput2.writeNetCdfFile(output, result, dateOfStudy);
    }

    static Raster blurMultipleTimes(Raster raster, int radius) {

        var result = raster;

        for (int i = 0; i < 100; i++) {
            result = blur(result, radius);
        }

        return result;
    }

    static Raster blur(Raster raster, int radius) {

        var kernel = createKernel(radius * 2 + 1);

        var result = new Raster(raster.getBounds(), raster.getCellSize());

        var firstPassRaster = new Raster(raster.getBounds(), raster.getCellSize());

        // smooth horizontally
        firstPassRaster.setValueForEachIndex((x, y) ->
                calculateBlurredValue(y, x, firstPassRaster.getXLength(), kernel, (yf, xv) -> raster.getValueByIndex(xv, yf))
        );

        // smooth vertically
        result.setValueForEachIndex((x, y) ->
                calculateBlurredValue(x, y, result.getYLength(), kernel, firstPassRaster::getValueByIndex)
        );

        return result;
    }

    private static double calculateBlurredValue(int fixedIndex, int volatileIndex, int volatileLength, double[] kernel, GetValue getValue) {

        var halfKernelLength = kernel.length / 2;
        var value = 0.;
        var startIndex = (volatileIndex - halfKernelLength < 0) ? halfKernelLength - volatileIndex : 0;
        var endIndex = (volatileIndex + halfKernelLength >= volatileLength) ? volatileLength - 1 - volatileIndex + halfKernelLength : kernel.length;

        for (var ki = startIndex; ki < endIndex; ki++) {
            var kernelValue = kernel[ki];
            var originalValue = getValue.forIndex(fixedIndex, volatileIndex + ki - halfKernelLength);
            value += originalValue * kernelValue;
        }
        return value;
    }

    /**
     * It might make sense to cut the edges of the distribution if we have a larger number of taps
     *
     * @param taps Length of the kernel
     * @return Gaussian Kernel
     */
    private static double[] createKernel(int taps) {

        var result = new double[taps];
        var binomialIndex = taps - 1;
        var sum = Math.pow(2, binomialIndex);

        for (var i = 0; i < taps; i++) {
            var coefficient = CombinatoricsUtils.binomialCoefficient(binomialIndex, i);
            result[i] = coefficient / sum;
        }
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

    @FunctionalInterface
    private interface GetValue {
        double forIndex(int fixedIndex, int volatileIndex);
    }
}
