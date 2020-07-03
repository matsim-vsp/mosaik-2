package org.matsim.mosaik2.chemistryDriver;

import ucar.ma2.DataType;
import ucar.nc2.Variable;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class NetcdfUtils {

    public static List<Double> toDoubleArray(Variable oneDimensionalVariable) throws IOException {

        if (oneDimensionalVariable.getRank() != 1 || oneDimensionalVariable.getDataType() != DataType.DOUBLE)
            throw new IllegalArgumentException("only 1 dimensional variables in this method");

        double[] values = (double[]) oneDimensionalVariable.read().copyTo1DJavaArray();
        return Arrays.stream(values).boxed().collect(Collectors.toList());
    }
}
