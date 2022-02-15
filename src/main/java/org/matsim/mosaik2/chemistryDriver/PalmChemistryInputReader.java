package org.matsim.mosaik2.chemistryDriver;

import lombok.extern.log4j.Log4j2;
import org.matsim.contrib.analysis.time.TimeBinMap;
import ucar.ma2.*;
import ucar.nc2.Dimension;
import ucar.nc2.NetcdfFile;
import ucar.nc2.Variable;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Log4j2
public class PalmChemistryInputReader {

    public static TimeBinMap<Map<String, Raster>> read(String filename) {
        return read(filename, 0, Integer.MAX_VALUE);
    }

    public static TimeBinMap<Map<String, Raster>> read(String filename, int fromTimeIndex, int toTimeIndex) {

        log.info("Try opening NetcdfFile at: " + filename);
        try (var file = NetcdfFile.open(filename)) {

            List<Integer> times = toIntList(file.findVariable(PalmChemistryInput2.TIME));
            List<Double> x = toDoubleArray(file.findVariable(PalmChemistryInput2.X));
            List<Double> y = toDoubleArray(file.findVariable(PalmChemistryInput2.Y));
            List<String> emissionNames = toStringArray(file.findVariable(PalmChemistryInput2.EMISSION_NAME));
            var timestampVariable = file.findVariable(PalmChemistryInput2.TIMESTAMP);

            // the input file we've received from stuttgart doesn"t have timestamps. If not available, just guess them
            List<String> timestamps = timestampVariable != null ? toStringArray(timestampVariable) : getTimestamps(times);

            Variable emissionValues = file.findVariable(PalmChemistryInput2.EMISSION_VALUES);
            Dimension zDimension = new Dimension(PalmChemistryInput2.Z, 1);
            emissionValues = emissionValues.reduce(List.of(zDimension));// remove z dimension, since it is not used

            TimeBinMap<Map<String, Raster>> emissions = createTimeBinMap(times, fromTimeIndex);
            Raster.Bounds bounds = createBounds(x, y);
            double cellSize = getCellSize(x, y);

            for (int ti = fromTimeIndex; ti < times.size() && ti <= toTimeIndex; ti++) {
                log.info("Parsing timestep: " + timestamps.get(ti));

                var timeBin = emissions.getTimeBin(times.get(ti));

                if (!timeBin.hasValue()) {
                    timeBin.setValue(new HashMap<>());
                }
                for (int ei = 0; ei < emissionNames.size(); ei++) {

                    var emissionName = emissionNames.get(ei);
                    var raster = new Raster(bounds, cellSize);

                    // as described here https://www.unidata.ucar.edu/software/netcdf-java/v4.6/tutorial/NetcdfFile.html (Reading data from a Variable)
                    // we read the data for one timestep and one pollutant but all cells of the raster
                    // the documentation suggest to use 'reduce' to eliminate dimensions with a length of 1. We can't use this here
                    // because we might have grids with a width of one tile
                    ArrayFloat.D4 emissionData = (ArrayFloat.D4) emissionValues.read(new int[]{ti, 0, 0, ei}, new int[]{1, y.size(), x.size(), 1});

                    // now, iterate over all cells of the raster and write the values into our raster data structure
                    for (int xi = 0; xi < x.size(); xi++) {
                        for (int yi = 0; yi < y.size(); yi++) {
                            float value = emissionData.get(0, yi, xi, 0);
                            raster.adjustValueForIndex(xi, yi, value);
                        }
                    }

                    // put the created raster into the correct time bin and associate it with the correct pollutant name
                    timeBin.getValue().put(emissionName, raster);
                }
            }

            log.info("Finished reading NetcdfFile");
            log.info("");
            return emissions;

        } catch (IOException | InvalidRangeException e) {
            throw new RuntimeException(e);
        }
    }

    private static TimeBinMap<Map<String, Raster>> createTimeBinMap(List<Integer> fromTimes, int fromTimeIndex) {

        int interval = -1;
        int startTime = fromTimes.get(fromTimeIndex); // assuming the list is populated

        for (int i = 1; i < fromTimes.size(); i++) {

            var newInterval = fromTimes.get(i) - fromTimes.get(i - 1);
            if (interval >= 0 && newInterval != interval) {
                throw new RuntimeException("found varying time intervals in chemistry input. The code currently assumes constant time intervals");
            }
            interval = newInterval;
        }
        return new TimeBinMap<>(interval, startTime);
    }

    private static Raster.Bounds createBounds(List<Double> xValues, List<Double> yValues) {

        if (!isConstantInterval(xValues))
            throw new RuntimeException("found varying intervals on the x-Axis. This code currently assumes a constant grid");
        if (!isConstantInterval(yValues))
            throw new RuntimeException("found varying intervals on the y-Axis. This code currently assumes a constant grid");

        return new Raster.Bounds(xValues.get(0), yValues.get(0), xValues.get(xValues.size() - 1), yValues.get(yValues.size() - 1));
    }

    private static boolean isConstantInterval(List<Double> numbers) {

        double interval = -1;
        for (int i = 1; i < numbers.size(); i++) {
            var newInterval = numbers.get(i) - numbers.get(i - 1);
            if (interval >= 0 && newInterval != interval) return false;
            interval = newInterval;
        }
        return true;
    }

    private static double getCellSize(List<Double> x, List<Double> y) {

        double xInterval = getInterval(x);
        double yInterval = getInterval(y);

        if (xInterval > 0 && yInterval > 0 && xInterval != yInterval)
            throw new RuntimeException("x and y interval are not equal. The code currently assumes square grid");

        return xInterval;
    }

    private static double getInterval(List<Double> numbers) {

        double interval = -1;
        for (int i = 1; i < numbers.size(); i++) {
            var newInterval = numbers.get(i) - numbers.get(i - 1);
            if (interval >= 0 && newInterval != interval) throw new RuntimeException("found varying interval.");
            interval = newInterval;
        }
        return interval;
    }

    private static List<Integer> toIntList(Variable oneDimensionalVariable) throws IOException {

        if (oneDimensionalVariable.getRank() != 1 || oneDimensionalVariable.getDataType() != DataType.INT)
            throw new IllegalArgumentException("only 1 dimensional variables in this method");

        int[] values = (int[]) oneDimensionalVariable.read().copyTo1DJavaArray();
        return Arrays.stream(values).boxed().collect(Collectors.toList());
    }

    private static List<Double> toDoubleArray(Variable oneDimensionalVariable) throws IOException {

        if (oneDimensionalVariable.getRank() != 1)
            throw new IllegalArgumentException("only 1 dimensional variables in this method");
        if (oneDimensionalVariable.getDataType() != DataType.DOUBLE) {
            // try parsing as integer
            var integers = toIntList(oneDimensionalVariable);
            return integers.stream()
                    .map(Integer::doubleValue)
                    .collect(Collectors.toList());
        }

        double[] values = (double[]) oneDimensionalVariable.read().copyTo1DJavaArray();
        return Arrays.stream(values).boxed().collect(Collectors.toList());
    }

    private static List<String> toStringArray(Variable oneDimensionalVariable) throws IOException {

        if (oneDimensionalVariable.getRank() != 2 || oneDimensionalVariable.getDataType() != DataType.CHAR)
            throw new IllegalArgumentException("only 1 dimensional variables in this method");

        ArrayChar stringArray = (ArrayChar) oneDimensionalVariable.read();
        List<String> result = new ArrayList<>();
        for (String s : stringArray) {
            result.add(s);
        }
        return result;
    }

    private static List<String> getTimestamps(List<Integer> times) {

        var date = LocalDateTime.now();
        return times.stream().map(time -> PalmChemistryInput2.getTimestamp(date, time)).collect(Collectors.toList());
    }
}
