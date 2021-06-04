package org.matsim.mosaik2.agentEmissions;

import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import ucar.ma2.ArrayInt;
import ucar.ma2.DataType;
import ucar.ma2.InvalidRangeException;
import ucar.nc2.NetcdfFile;
import ucar.nc2.Variable;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@Log4j2
public class AgentEmissionNetCdfReader {

    @Getter
    private final Set<String> eventRecord = new HashSet<>();

    public void read(String filename, String indexFilename) {

        var index = PositionEmissionNetcdfModule.VehicleIdIndex.readFromFile(indexFilename);

        try (var file = NetcdfFile.open(filename)) {

            List<Double> times = toDoubleList(file.findVariable("time"));
            List<Integer> numberOfVehiclesPerTimestep = toIntList(file.findVariable("number_of_vehicles") );

            Variable vehicleId = file.findVariable("vehicle_id");

            for (int ti = 0; ti < times.size(); ti++) {

                var time = times.get(ti);
                var numberOfVehicles = numberOfVehiclesPerTimestep.get(ti);

                ArrayInt.D2 vehicleIds = (ArrayInt.D2) vehicleId.read(new int[] {ti, 0}, new int[] {1, numberOfVehicles});
                for (int vi = 0; vi < numberOfVehicles; vi++) {
                    int intId = vehicleIds.get(0, vi);
                    var record = "time=" + time +
                            "vehicleId=" + index.get(intId);
                    eventRecord.add(record);
                }
            }
        } catch (IOException | InvalidRangeException e) {
            e.printStackTrace();
        }
    }

    private static List<Integer> toIntList(Variable oneDimensionalVariable) throws IOException {

        if (oneDimensionalVariable.getRank() != 1 || oneDimensionalVariable.getDataType() != DataType.INT)
            throw new IllegalArgumentException("variable was either not one dimensional or not Integer");

        int[] values = (int[]) oneDimensionalVariable.read().copyTo1DJavaArray();
        return Arrays.stream(values).boxed().collect(Collectors.toList());
    }

    private static List<Double> toDoubleList(Variable oneDimensionalVariable) throws IOException {
         if (oneDimensionalVariable.getRank() != 1 || oneDimensionalVariable.getDataType() != DataType.DOUBLE)
            throw new IllegalArgumentException("variable was either not one dimensional or not Double");

         double[] values = (double[]) oneDimensionalVariable.read().copyTo1DJavaArray();
         return Arrays.stream(values).boxed().collect(Collectors.toList());
    }
}
