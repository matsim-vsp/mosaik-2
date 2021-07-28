package org.matsim.mosaik2.agentEmissions;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.csv.CSVFormat;
import org.matsim.api.core.v01.Id;
import org.matsim.vehicles.Vehicle;
import ucar.ma2.ArrayDouble;
import ucar.ma2.ArrayInt;
import ucar.ma2.DataType;
import ucar.ma2.InvalidRangeException;
import ucar.nc2.NetcdfFile;
import ucar.nc2.Variable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Log4j2
public class AgentEmissionNetCdfReader {
    
    public static void main(String... args) {

        log.info("translating from netcdf to csv");
        
        translateToCsv(
                "C:/Users/Janek/Desktop/berlin-test/ITERS/it.0/berlin-v5.5-1pct.0.position-emissions.nc",
                "C:/Users/Janek/Desktop/berlin-test/ITERS/it.0/berlin-v5.5-1pct.0.position-emissions-vehicleIdIndex.csv",
                "C:/Users/Janek/Desktop/berlin-test/ITERS/it.0/berlin-v5.5-1pct.0.position-emissions.csv"
                );
    }

    public static Set<String> readToRecord(String filename, String indexFilename) {

        Set<String> result = new HashSet<>();
        read(filename, indexFilename, record -> {

            var message = "time=" + record.getTime() +
                    ",vehicleId=" + record.getVehicleId() +
                    ",x=" + record.getX() +
                    ",y=" + record.getY() +
                    ",no2=" + record.getNo2();
            result.add(message);
        });
        return result;
    }

    public static void translateToCsv(String filename, String indexFilename, String csvOutput) {

        var counter = new AtomicInteger();

        try(var writer = Files.newBufferedWriter(Paths.get(csvOutput)); var printer = CSVFormat.DEFAULT.withHeader("time", "vehicleId", "x", "y", "no2").print(writer)) {

            read(filename, indexFilename, record -> {
                try {
                    var count = counter.incrementAndGet();
                    if (count % 100000 == 0) {
                        log.info("Record # " + count);
                    }
                    printer.printRecord(record.getTime(), record.getVehicleId(), record.getX(), record.getY(), record.getNo2());
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void read(String filename, String indexFilename, Consumer<AgentEmissionRecord> consumer) {

        var index = PositionEmissionNetcdfModule.VehicleIdIndex.readFromFile(indexFilename);

        try (var file = NetcdfFile.open(filename)) {

            List<Double> times = toDoubleList(file.findVariable("time"));
            List<Integer> numberOfVehiclesPerTimestep = toIntList(file.findVariable("number_of_vehicles") );

            Variable vehicleId = file.findVariable("vehicle_id");
            Variable xVar = file.findVariable("x");
            Variable yVar = file.findVariable("y");
            Variable no2Var = file.findVariable("NO2");

            for (int ti = 0; ti < times.size(); ti++) {

                var time = times.get(ti);
                var numberOfVehicles = numberOfVehiclesPerTimestep.get(ti);

                var position = new int[] {ti, 0};
                var shape = new int[] {1, numberOfVehicles};
                ArrayInt.D2 vehicleIds = (ArrayInt.D2) vehicleId.read(position, shape);
                ArrayDouble.D2 x = (ArrayDouble.D2) xVar.read(position, shape);
                ArrayDouble.D2 y = (ArrayDouble.D2) yVar.read(position, shape);
                ArrayDouble.D2 no2 = (ArrayDouble.D2) no2Var.read(position, shape);

                for (int vi = 0; vi < numberOfVehicles; vi++) {

                    var intId = vehicleIds.get(0, vi);
                    var record = new AgentEmissionRecord(
                            time,
                            index.get(intId),
                            x.get(0, vi),
                            y.get(0, vi),
                            no2.get(0, vi)
                    );
                    consumer.accept(record);
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

    @RequiredArgsConstructor
    @Getter
    private static class AgentEmissionRecord {

        private final double time;
        private final Id<Vehicle> vehicleId;
        private final double x;
        private final double y;
        private final double no2;
    }
}
