package org.matsim.mosaik2.agentEmissions;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.log4j.Logger;
import org.matsim.core.utils.io.IOUtils;
import ucar.ma2.Array;
import ucar.nc2.NetcdfFile;
import ucar.nc2.Variable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author dwedekind
 */

public class PositionEmissionNetCdfToCSV {
    private static final Logger log = Logger.getLogger(PositionEmissionNetCdfToCSV.class);
    private final NetcdfFile ncFile;
    private final String[] HEADER = {"time", "vehicle_id", "x", "y", "CO",
            "CO2", "NOx", "NO2", "PM10", "NO"};


    public static void main(String[] args) {
        PositionEmissionNetCdfToCSV.Input input = new PositionEmissionNetCdfToCSV.Input();
        JCommander.newBuilder().addObject(input).build().parse(args);
        log.info("Input netcdf file: " + input.netCdfFile);
        PositionEmissionNetCdfToCSV converter = new PositionEmissionNetCdfToCSV( input.netCdfFile );
        List<String> csvRows = converter.processNetCdfToCSVLines();
        converter.printResults(csvRows, input.outputCsvFile);

    }


    PositionEmissionNetCdfToCSV( String filename){
        NetcdfFile ncfile = null;
        try {
            ncfile = NetcdfFile.open(filename);
        } catch (IOException ioe) {
            log.error("trying to open " + filename, ioe);
        }
        this.ncFile = ncfile;
    }


    public void printResults(List<String> csvRows, String csvfileName) {

        try {

            String separator = ";";
            CSVPrinter csvPrinter = new CSVPrinter(IOUtils.getBufferedWriter(csvfileName),
                    CSVFormat.DEFAULT.withDelimiter(separator.charAt(0)).withHeader(HEADER));

            for (var row: csvRows){
                csvPrinter.printRecord(row);
            }

            csvPrinter.close();
            log.info("Netcdf written to: " + csvfileName);

        } catch (IOException e) {
            e.printStackTrace();
        }

    }


    private List<String> processNetCdfToCSVLines() {
        List<String> csvRows = new ArrayList<>();

        Map<Integer, Map<Integer, String>> vehicleIds = returnId2ValueMap("vehicle_id");
        Map<Integer, Map<Integer, String>> xs = returnId2ValueMap("x");
        Map<Integer, Map<Integer, String>> ys = returnId2ValueMap("y");
        Map<Integer, Map<Integer, String>> cos = returnId2ValueMap("CO");
        Map<Integer, Map<Integer, String>> co2s = returnId2ValueMap("CO2");
        Map<Integer, Map<Integer, String>> noxs = returnId2ValueMap("NOx");
        Map<Integer, Map<Integer, String>> no2s = returnId2ValueMap("NO2");
        Map<Integer, Map<Integer, String>> pm10s = returnId2ValueMap("PM10");
        Map<Integer, Map<Integer, String>> nos = returnId2ValueMap("NO");

        for (var time2VehicleAndValue: vehicleIds.entrySet()){
            for (var vehicleAndValue: time2VehicleAndValue.getValue().entrySet()){
                List<CharSequence> oneLine = new ArrayList<>();
                oneLine.add(String.valueOf(time2VehicleAndValue.getKey()));
                oneLine.add(vehicleIds.get(time2VehicleAndValue.getKey()).get(vehicleAndValue.getKey()));
                oneLine.add(xs.get(time2VehicleAndValue.getKey()).get(vehicleAndValue.getKey()));
                oneLine.add(ys.get(time2VehicleAndValue.getKey()).get(vehicleAndValue.getKey()));
                oneLine.add(cos.get(time2VehicleAndValue.getKey()).get(vehicleAndValue.getKey()));
                oneLine.add(co2s.get(time2VehicleAndValue.getKey()).get(vehicleAndValue.getKey()));
                oneLine.add(noxs.get(time2VehicleAndValue.getKey()).get(vehicleAndValue.getKey()));
                oneLine.add(no2s.get(time2VehicleAndValue.getKey()).get(vehicleAndValue.getKey()));
                oneLine.add(pm10s.get(time2VehicleAndValue.getKey()).get(vehicleAndValue.getKey()));
                oneLine.add(nos.get(time2VehicleAndValue.getKey()).get(vehicleAndValue.getKey()));
                csvRows.add(String.join(",", oneLine));

            }

        }

        return csvRows;

    }


    private Map<Integer, Map<Integer, String>> returnId2ValueMap(String variableName){
        Map<Integer, Map<Integer, String>> time2VehicleAndValue = new HashMap<>();
        int index = 0;

        Variable var = null;
        try {
            var = ncFile.findVariable(variableName);
        } catch (Exception e){
            log.error(String.format("Variable '%s' was not found in file: %s", variableName, e.getMessage()));
        }

        assert var != null;
        int rank = var.getRank();
        int[] shape = var.getShape();
        int nRows = shape[0];
        int nCols = shape[1];

        int[] readOrigin = new int[rank];
        int[] readShape = new int[rank];
        for (int iRow = 0; iRow < nRows; iRow++) {
            // set up to read the entire row, but just one row.
            readOrigin[0] = iRow;  // rows are numbered from zero
            readOrigin[1] = 0;     // columns are numbered from zero
            readShape[0]  = 1;     // read one row
            readShape[1]  = nCols; // read the entire set of columns for that row

            time2VehicleAndValue.put(iRow, new HashMap<>());
            try {

                Array array = var.read(readOrigin, readShape);
                for (int iCol = 0; iCol < nCols; iCol++) {
                    time2VehicleAndValue.get(iRow).put(iCol, String.valueOf(array.getObject(iCol)));
                }
                

            } catch (Exception e) {
                log.error(e);
            }

        }

        return time2VehicleAndValue;
    }


    private static class Input {

        @Parameter(names = "-netCdfFile")
        private String netCdfFile;

        @Parameter(names = "-outputCsvFile")
        private String outputCsvFile;

    }
    
    private class ValueMatcher {
        int id;
        double time;
        
        ValueMatcher(int id, double time){
            this.id = id;
            this.time = time;
        }

        public int getId() {
            return id;
        }

        public double getTime() {
            return time;
        }
    }



}
