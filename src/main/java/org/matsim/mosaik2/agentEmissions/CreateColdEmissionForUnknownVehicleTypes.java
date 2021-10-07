package org.matsim.mosaik2.agentEmissions;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.io.input.BOMInputStream;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.contrib.emissions.EmissionUtils;
import org.matsim.contrib.emissions.HbefaVehicleCategory;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

public class CreateColdEmissionForUnknownVehicleTypes {

    @Parameter(names = "-input", required = true)
    private String coldEmissions = "";

    @Parameter(names = "-output", required = true)
    private String outputFile = "";

    public static void main(String[] args) {

        var bla = new CreateColdEmissionForUnknownVehicleTypes();
        JCommander.newBuilder().addObject(bla).build().parse(args);
        bla.run();
    }

    private void run() {

        var header = List.of("Case", "VehCat", "Year", "TrafficScenario", "Component", "RoadCat", "AmbientCondPattern", "EFA_weighted", "EFA_km_weighted", "EFA_WTT_weighted", "EFA_WTT_km_weighted", "EFA_WTW_weighted", "EFA_WTW_km_weighted");

        try (var writer = Files.newBufferedWriter(Paths.get(outputFile)); var csvPrinter = CSVFormat
                .DEFAULT
                .withDelimiter(';')
                .withHeader("Case", "VehCat", "Year", "TrafficScenario", "Component", "RoadCat", "AmbientCondPattern", "EFA_weighted", "EFA_km_weighted", "EFA_WTT_weighted", "EFA_WTT_km_weighted", "EFA_WTW_weighted", "EFA_WTW_km_weighted").print(writer)) {
            // collect the current data. Use a BOMInputStream, since the original file contains such a bom. See https://commons.apache.org/proper/commons-csv/user-guide.html#Handling_Byte_Order_Marks
            try (final Reader reader = new InputStreamReader(new BOMInputStream(Paths.get(coldEmissions).toUri().toURL().openStream()), StandardCharsets.UTF_8);
                 var csvParser = new CSVParser(reader, CSVFormat.DEFAULT.withDelimiter(';').withHeader())) {

                for (var record : csvParser) {
                    // copy the existing value into the new csv
                    csvPrinter.printRecord(record);

                    // now, if it is passenger car, copy value with 0 emissions for all other vehicle categories except passenger car and LCV since they are already present
                    if (record.get("VehCat").equals("pass. car")) {

                        var recordMap = record.toMap();
                        recordMap.put("EFA_weighted", "0.0");
                        for (var category : HbefaVehicleCategory.values()) {
                            if (ignoreCategory(category)) continue;

                            // copy all the values
                            var vehCat = EmissionUtils.mapHbefaVehicleCategory2String(category);
                            recordMap.put("VehCat", vehCat);
                            for (var key : header) {
                                csvPrinter.print(recordMap.get(key));
                            }
                            csvPrinter.println();
                        }
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    static boolean ignoreCategory(HbefaVehicleCategory category) {
        return HbefaVehicleCategory.PASSENGER_CAR.equals(category) || HbefaVehicleCategory.LIGHT_COMMERCIAL_VEHICLE.equals(category) || HbefaVehicleCategory.NON_HBEFA_VEHICLE.equals(category);
    }
}
