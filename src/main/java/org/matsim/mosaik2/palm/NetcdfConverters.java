package org.matsim.mosaik2.palm;

import org.matsim.contrib.analysis.time.TimeBinMap;
import org.matsim.mosaik2.raster.DoubleRaster;
import ucar.ma2.ArrayChar;
import ucar.ma2.DataType;
import ucar.ma2.InvalidRangeException;
import ucar.nc2.Variable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class NetcdfConverters {

    public static int[] toIntArray(Variable oneDimensionalVariable) throws IOException {
        if (oneDimensionalVariable.getRank() != 1 || oneDimensionalVariable.getDataType() != DataType.INT)
            throw new IllegalArgumentException("only 1 dimensional variables in this method");

        return (int[]) oneDimensionalVariable.read().copyTo1DJavaArray();
    }

    public static List<Integer> toIntList(Variable oneDimensionalVariable) throws IOException {

        int[] values = toIntArray(oneDimensionalVariable);
        return Arrays.stream(values).boxed().collect(Collectors.toList());
    }

    public static double[] toDoubleArray(Variable oneDimensionalVariable) throws IOException {
        if (oneDimensionalVariable.getRank() != 1)
            throw new IllegalArgumentException("only 1 dimensional variables in this method");
        if (oneDimensionalVariable.getDataType() != DataType.DOUBLE) {
            return Arrays.stream(toIntArray(oneDimensionalVariable)).asDoubleStream().toArray();
        }

        return (double[]) oneDimensionalVariable.read().copyTo1DJavaArray();
    }

    public static double[] varToDoubleArray(Variable multiDimensionalVariable, int[] shape) throws IOException {


        try {
            var result = multiDimensionalVariable.read(new int[]{0, 0}, shape);
            return IntStream.range(0, (int) result.getSize()).mapToDouble(result::getDouble).toArray();

        } catch (InvalidRangeException e) {
            throw new RuntimeException(e);
        }
    }

    public static List<Double> toDoubleList(Variable oneDimensionalVariable) throws IOException {

        double[] values = toDoubleArray(oneDimensionalVariable);
        return Arrays.stream(values).boxed().collect(Collectors.toList());
    }

    public static List<String> toStringArray(Variable oneDimensionalVariable) throws IOException {

        if (oneDimensionalVariable.getRank() != 2 || oneDimensionalVariable.getDataType() != DataType.CHAR)
            throw new IllegalArgumentException("only 1 dimensional variables in this method");

        ArrayChar stringArray = (ArrayChar) oneDimensionalVariable.read();
        List<String> result = new ArrayList<>();
        for (String s : stringArray) {
            result.add(s);
        }
        return result;
    }

    public static <T> TimeBinMap<T> createTimeBinMap(double[] fromTimes, int fromTimeIndex) {

        // round all the values to the next 10, this means we can only process tings which are at least 10 seconds.
        // most of the stuff we do has time bins of 3600s though.
        double endTimeFirstBin = Math.round(fromTimes[fromTimeIndex] / 10) * 10; // assuming the list is populated
        double interval = fromTimes.length > 1 ? Math.round((fromTimes[1] - fromTimes[0]) / 10) * 10 : 1;

        double startTime = endTimeFirstBin - interval;
        return new TimeBinMap<>(interval, startTime);
    }

    public static double getCellSize(double[] x, double[] y) {

        double xInterval = getInterval(x);
        double yInterval = getInterval(y);

        if (xInterval > 0 && yInterval > 0 && xInterval != yInterval)
            throw new RuntimeException("x and y interval are not equal. The code currently assumes square grid");

        return xInterval;
    }

    private static double getInterval(double[] numbers) {

        double interval = -1;
        for (int i = 1; i < numbers.length; i++) {
            var newInterval = numbers[i] - numbers[i - 1];
            if (interval >= 0 && newInterval != interval) throw new RuntimeException("found varying interval.");
            interval = newInterval;
        }
        return interval;
    }

    public static DoubleRaster.Bounds createBounds(double[] xValues, double[] yValues) {

        if (!isConstantInterval(xValues))
            throw new RuntimeException("found varying intervals on the x-Axis. This code currently assumes a constant grid");
        if (!isConstantInterval(yValues))
            throw new RuntimeException("found varying intervals on the y-Axis. This code currently assumes a constant grid");

        return new DoubleRaster.Bounds(xValues[0], yValues[0], xValues[xValues.length - 1], yValues[yValues.length - 1]);
    }

    private static boolean isConstantInterval(double[] numbers) {

        double interval = -1;
        for (int i = 1; i < numbers.length; i++) {
            var newInterval = numbers[i] - numbers[i - 1];
            if (interval >= 0 && newInterval != interval) return false;
            interval = newInterval;
        }
        return true;
    }
}