package org.matsim.mosaik2.agentEmissions;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import io.jawg.geojson.Position;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.csv.CSVFormat;
import org.geotools.data.DataUtilities;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.feature.DefaultFeatureCollection;
import org.geotools.feature.FeatureCollection;
import org.geotools.feature.SchemaException;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.geotools.feature.simple.SimpleFeatureTypeImpl;
import org.geotools.geojson.feature.FeatureJSON;
import org.geotools.geojson.geom.GeometryJSON;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.CoordinateXYZM;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.matsim.api.core.v01.Id;
import org.matsim.vehicles.Vehicle;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.feature.type.FeatureType;
import ucar.ma2.*;
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

    static class Input {

        @Parameter(names = "-netcdf", required = true)
        String netCdfFile;
        @Parameter(names = "-vehIndex", required = true)
        String vehicleIndexFile;
        @Parameter(names = "-output", required = true)
        String outputFile;
    }
    
    public static void main(String... args) throws SchemaException, IOException {

    /*    var input = new Input();
        JCommander.newBuilder().addObject(input).build().parse(args);
        log.info("translating from netcdf to csv");

        translateToCsv(
                input.netCdfFile,
                input.vehicleIndexFile,
                input.outputFile
        );
        log.info("Done.");

     */

        translateToGeoJson();
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
            log.info("Done reading.");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        log.info("Done translating.");
    }

    public static void translateToGeoJson() throws SchemaException, IOException {

        SimpleFeatureTypeBuilder builder = new SimpleFeatureTypeBuilder();
        builder.add("agentId", String.class);
        builder.add("geometry", LineString.class);
        builder.setDefaultGeometry("geometry");
        builder.setName("feature-type-name");

        var TYPE = builder.buildFeatureType();
        var geometry = new GeometryFactory().createLineString(new Coordinate[] {
                new CoordinateXYZM(0,0,0,0),
                new CoordinateXYZM(10, 10, 0, 20),
                new CoordinateXYZM(50,50,0,30),
        });

        var featureBuilder = new SimpleFeatureBuilder(TYPE);
        featureBuilder.set("agentId", "test-id");
        featureBuilder.set("geometry", geometry);
        var feature = featureBuilder.buildFeature("test-feature-id");
        var featureCollection = new DefaultFeatureCollection();
        featureCollection.add(feature);

        var bla = new FeatureJSON();
        bla.writeFeatureCollection(featureCollection, "C:/Users/Janek/Desktop/test.json");
    }

    public static void read(String filename, String indexFilename, Consumer<AgentEmissionRecord> consumer) {

        var index = PositionEmissionNetcdfModule.VehicleIdIndex.readFromFile(indexFilename);

        try (var file = NetcdfFile.open(filename)) {

            float[] times = toFloatArray(file.findVariable("time"));
            int[] numberOfVehiclesPerTimestep = toInArray(file.findVariable("number_of_vehicles") );

            Variable vehicleId = file.findVariable("vehicle_id");
            Variable xVar = file.findVariable("x");
            Variable yVar = file.findVariable("y");
            Variable no2Var = file.findVariable("NO2");

            for (int ti = 0; ti < times.length; ti++) {

                var time = times[ti];
                var numberOfVehicles = numberOfVehiclesPerTimestep[ti];

                var position = new int[] {ti, 0};
                var shape = new int[] {1, numberOfVehicles};
                ArrayInt.D2 vehicleIds = (ArrayInt.D2) vehicleId.read(position, shape);
                ArrayFloat.D2 x = (ArrayFloat.D2) xVar.read(position, shape);
                ArrayFloat.D2 y = (ArrayFloat.D2) yVar.read(position, shape);
                ArrayFloat.D2 no2 = (ArrayFloat.D2) no2Var.read(position, shape);

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
            throw new RuntimeException(e);
        }
    }

    private static int[] toInArray(Variable oneDimensionalVariable) throws IOException {

        if (oneDimensionalVariable.getRank() != 1 || oneDimensionalVariable.getDataType() != DataType.INT)
            throw new IllegalArgumentException("variable was either not one dimensional or not Integer");

        return (int[]) oneDimensionalVariable.read().copyTo1DJavaArray();
    }

    private static float[] toFloatArray(Variable oneDimensionalVariable) throws IOException {
         if (oneDimensionalVariable.getRank() != 1 || oneDimensionalVariable.getDataType() != DataType.FLOAT)
            throw new IllegalArgumentException("variable was either not one dimensional or not Double");

         return (float[]) oneDimensionalVariable.read().copyTo1DJavaArray();
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
