package org.matsim.mosaik2.chemistryDriver;

import org.apache.log4j.Logger;
import org.matsim.contrib.analysis.time.TimeBinMap;
import ucar.ma2.*;
import ucar.nc2.Attribute;
import ucar.nc2.NetcdfFileWriter;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class PalmChemistryInput2 {

    private static final Logger logger = Logger.getLogger(PalmChemistryInput2.class);

    public static final String TIME = "time";
    public static final String Z = "z";
    public static final String X = "x";
    public static final String Y = "y";
    public static final String SPECIES = "nspecies";
    public static final String FIELD_LEN = "field_length";
    public static final String EMISSION_NAME = "emission_name";
    public static final String EMISSION_INDEX = "emission_index";
    public static final String TIMESTAMP = "timestamp";
    public static final String EMISSION_VALUES = "emission_values";

    public static void writeNetCdfFile(String outputFile, TimeBinMap<Map<String, Raster>> data) {
        writeNetCdfFile(outputFile, data, "2017-07-31", 1);
    }

    public static void writeNetCdfFile(String outputFile, TimeBinMap<Map<String, Raster>> data, String date, int numberOfDays) {

        // get the observed pollutants from first valid time bin
        var observedPollutants = data.getTimeBins().iterator().next().getValue().keySet();

        // get the very first raster for dimensions. from first valid time bin
        var raster = data.getTimeBins().iterator().next().getValue().values().iterator().next();

        try (var writer = NetcdfFileWriter.createNew(NetcdfFileWriter.Version.netcdf3, outputFile)) {

            writeDimensions(writer, observedPollutants, raster);
            writeVariables(writer);
            writeAttributes(writer);
            writeGlobalAttributes(writer);
            writer.create();

            writeData(writer, data, observedPollutants, raster, date, numberOfDays);

        } catch (IOException | InvalidRangeException e) {
            throw new RuntimeException(e);
        }
    }

    private static void writeData(NetcdfFileWriter writer, TimeBinMap<Map<String, Raster>> data, Set<String> observedPollutants, Raster raster, String date, int numberOfDays) throws IOException, InvalidRangeException {

        var pollutantToIndex = new ArrayList<>(observedPollutants);
        var emissionIndex = new ArrayInt.D1(pollutantToIndex.size(), false);
        for (var i = 0; i < pollutantToIndex.size(); i++) {
            emissionIndex.set(i, i + 1);
        }

        var emissionNames = new ArrayChar.D2(pollutantToIndex.size(), 64);
        for (var i = 0; i < pollutantToIndex.size(); i++) {
            emissionNames.setString(i, pollutantToIndex.get(i));
        }

        var zValues = new ArrayDouble.D1(1);
        zValues.set(0, 1.0); // the original file sets this to 1 as well
        var xValues = writeDoubleArray(raster.getCellSize() / 2, raster.getCellSize(), raster.getXLength());
        var yValues = writeDoubleArray(raster.getCellSize() / 2 , raster.getCellSize(), raster.getYLength());

        //int numberOfConsecutiveTimeBins = (int) ((data.getEndTimeOfLastBin() - data.getStartTime()) / data.getBinSize());
        var numberOfTimeslices = 24 * numberOfDays;
        var times = new ArrayInt.D1(numberOfTimeslices, false);
        var timestamps = new ArrayChar.D2(numberOfTimeslices, 64);
        var emissionValues = new ArrayFloat.D5(numberOfTimeslices, 1, raster.getYLength(), raster.getXLength(), pollutantToIndex.size());

       // palm needs some time to warm up. We put the same day into the input file twice
        // cut of the emissions after 24 hours.
        for (var day = 0; day < numberOfDays; day++ ) {
            for (var i = 0; i < 24; i++) {

                var bin = getTimeBin(data, i);
                var timeIndex = i + 24 * day; // use 24 hours of one day and then keep on going for each additional day.

                var timestamp = getTimestamp(date, bin.getStartTime());
                logger.info("writing timestep: " + timestamp);
                timestamps.setString(timeIndex, timestamp);

                // continue writing data after the first day has finished.
                times.set(timeIndex, (int) bin.getStartTime() + day * 24 * 3600);
                for (var pollutantEntry : bin.getValue().entrySet()) {

                    var pollutantRaster = pollutantEntry.getValue();
                    pollutantRaster.forEachIndex(((xi, yi, value) -> {
                        var p = pollutantToIndex.indexOf(pollutantEntry.getKey());
                        emissionValues.set(timeIndex, 0, yi, xi, p, (float) value);
                    }));
                }
            }
        }

        // still don't know why we need two of these indices
        writer.write(writer.findVariable(SPECIES), emissionIndex);
        writer.write(writer.findVariable(EMISSION_INDEX), emissionIndex);

        writer.write(writer.findVariable(EMISSION_NAME), emissionNames);
        writer.write(writer.findVariable(TIMESTAMP), timestamps);
        writer.write(writer.findVariable(TIME), times);
        writer.write(writer.findVariable(Z), zValues);
        writer.write(writer.findVariable(Y), yValues);
        writer.write(writer.findVariable(X), xValues);
        writer.write(writer.findVariable(EMISSION_VALUES), emissionValues);
    }

    private static void writeDimensions(NetcdfFileWriter writer, Set<String> observedPollutants, Raster raster) {

        writer.addUnlimitedDimension(TIME);
        writer.addDimension(X, raster.getXLength());
        writer.addDimension(Y, raster.getYLength());
        writer.addDimension(Z, 1);
        writer.addDimension(SPECIES, observedPollutants.size());
        // this seems to be necessary to encode strings. I guess each string has 64 bits reserved. I also guess that this means strings may only be 64 bits long.
        writer.addDimension(FIELD_LEN, 64);
    }

    private static void writeVariables(NetcdfFileWriter writer) {

        writer.addVariable(SPECIES, DataType.INT, SPECIES);
        writer.addVariable(EMISSION_NAME, DataType.CHAR,
                List.of(writer.findDimension(SPECIES), writer.findDimension(FIELD_LEN)));
        writer.addVariable(EMISSION_INDEX, DataType.FLOAT, SPECIES);
        writer.addVariable(TIMESTAMP, DataType.CHAR, List.of(
                writer.findDimension(TIME), writer.findDimension(FIELD_LEN)
        ));
        writer.addVariable(TIME, DataType.INT, TIME);
        writer.addVariable(Z, DataType.DOUBLE, Z);
        writer.addVariable(Y, DataType.DOUBLE, Y);
        writer.addVariable(X, DataType.DOUBLE, X);
        writer.addVariable(EMISSION_VALUES, DataType.FLOAT,
                // order of the dimensions is important, since access is simply index based
                List.of(writer.findDimension(TIME), writer.findDimension(Z), writer.findDimension(Y), writer.findDimension(X), writer.findDimension(SPECIES))
        );
    }

    private static void writeAttributes(NetcdfFileWriter writer) {
        writer.findVariable(SPECIES).addAttribute(new Attribute("long_name", "nspecies"));
        writer.findVariable(EMISSION_NAME).addAttribute(new Attribute("long_name", "emission species name"));
        writer.findVariable(EMISSION_INDEX).addAttribute(new Attribute("long_name", "emission species index"));
        writer.findVariable(EMISSION_INDEX).addAttribute(new Attribute("_Fill_Value", -9999.9F));
        writer.findVariable(TIMESTAMP).addAttribute(new Attribute("long_name", "time stamp"));
        writer.findVariable(TIME).addAttribute(new Attribute("long_name", "time"));
        writer.findVariable(TIME).addAttribute(new Attribute("units", "s"));
        writer.findVariable(X).addAttribute(new Attribute("units", "m"));
        writer.findVariable(Y).addAttribute(new Attribute("units", "m"));
        writer.findVariable(Z).addAttribute(new Attribute("units", "m"));
        writer.findVariable(EMISSION_VALUES).addAttribute(new Attribute("long_name", "emission values"));
        writer.findVariable(EMISSION_VALUES).addAttribute(new Attribute("_Fill_Value", -999.9F));
        writer.findVariable(EMISSION_VALUES).addAttribute(new Attribute("units", "g/m2/hour"));
    }

    private static void writeGlobalAttributes(NetcdfFileWriter writer) {
        writer.addGlobalAttribute("description", "PALM Chemistry Data");
        writer.addGlobalAttribute("author", "VSP - TU Berlin");
        writer.addGlobalAttribute("lod", 2);
        writer.addGlobalAttribute("legacy_mode", "yes");
    }

    private static ArrayDouble.D1 writeDoubleArray(double min, double intervalSize, int size) {
        var result = new ArrayDouble.D1(size);

        for(int i = 0; i < size; i++) {

            var value = min + i * intervalSize;
            result.set(i, value);
        }
        return result;
    }

    private static TimeBinMap.TimeBin<Map<String, Raster>> getTimeBin(TimeBinMap<Map<String, Raster>> data, int index) {

        var bin = data.getTimeBin(data.getStartTime() + index * data.getBinSize());

        if (!bin.hasValue()) {
            bin.setValue(Map.of());
        }

        return bin;
    }

    public static String getTimestamp(String date, double time) {
        var startTimeDuration = Duration.ofSeconds((long) time);
        return String.format(date + " %02d:%02d:%02d +001", startTimeDuration.toHours(), startTimeDuration.toMinutesPart(), startTimeDuration.toSecondsPart());
    }
}
