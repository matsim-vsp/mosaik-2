package org.matsim.mosaik2.agentEmissions;

import lombok.extern.log4j.Log4j2;
import ucar.ma2.ArrayInt;
import ucar.ma2.DataType;
import ucar.ma2.InvalidRangeException;
import ucar.nc2.NetcdfFile;
import ucar.nc2.Variable;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Log4j2
public class AgentEmissionNetCdfReader {

    private final Set<String> eventRecord = new HashSet<>();

    public void read(String filename) {

        try (var file = NetcdfFile.open(filename)) {

            List<Integer> times = toIntList(file.findVariable("time"));
            List<Integer> numberOfVehiclesPerTimestep = toIntList(file.findVariable("number_of_vehicles") );

            Variable vehicleId = file.findVariable("vehicle_id");

            for (int ti = 0; ti < times.size(); ti++) {
                var time = times.get(ti);
                log.info("Parsing timestep: " + time);

                var numberOfVehicles = numberOfVehiclesPerTimestep.get(ti);

                ArrayInt.D2 vehicleIds = (ArrayInt.D2) vehicleId.read(new int[] {ti, 0}, new int[] {1, numberOfVehicles});
                for (int vi = 0; vi < numberOfVehicles; vi++) {
                    int id = vehicleIds.get(0, vi);
                    log.info("id: " + id);
                }
            }
        } catch (IOException | InvalidRangeException e) {
            e.printStackTrace();
        }
    }

    private static List<Integer> toIntList(Variable oneDimensionalVariable) throws IOException {

        if (oneDimensionalVariable.getRank() != 1 || oneDimensionalVariable.getDataType() != DataType.INT)
            throw new IllegalArgumentException("only 1 dimensional variables in this method");

        int[] values = (int[]) oneDimensionalVariable.read().copyTo1DJavaArray();
        return Arrays.stream(values).boxed().collect(Collectors.toList());
    }
}
