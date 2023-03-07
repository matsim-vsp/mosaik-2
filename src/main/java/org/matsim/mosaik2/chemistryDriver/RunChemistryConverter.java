package org.matsim.mosaik2.chemistryDriver;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import org.apache.commons.lang3.StringUtils;
import org.matsim.contrib.emissions.Pollutant;
import org.matsim.core.utils.geometry.CoordinateTransformation;
import org.matsim.core.utils.geometry.transformations.IdentityTransformation;
import org.matsim.core.utils.geometry.transformations.TransformationFactory;
import org.matsim.mosaik2.palm.PalmStaticDriverReader;
import org.matsim.mosaik2.raster.DoubleRaster;

import java.time.LocalDateTime;
import java.util.Map;

public class RunChemistryConverter {

    @Parameter(names = {"-n", "-network"}, required = true)
    private String network;

    @Parameter(names = {"-e", "-emissions"}, required = true)
    private String emissionEventsFile = "";

    @Parameter(names = {"-s", "-static-driver"}, required = true)
    private String staticDriver = "";

    @Parameter(names = {"-o", "-output-file"}, required = true)
    private String outputFile;

    @Parameter(names = {"-d", "-date"}, required = true)
    private String date;

    @Parameter(names = {"-uo", "-utc-offset"})
    private int utcOffset = 0;

    @Parameter(names = {"-ct", "coordinate-transformation"})
    private String crsTransformation = "";

    @Parameter(names = {"-s", "-scale-factor"})
    private double scaleFactor = 10;

    @Parameter(names = {"-nd", "-number-of-days"})
    private int numberOfDays = 2;

    public static void main(String[] args) {

        var converter = new RunChemistryConverter();
        JCommander.newBuilder().addObject(converter).build().parse(args);

        var streetTypeData = PalmStaticDriverReader.read()

        converter.convert();
    }

    private void convert(DoubleRaster streetTypes) {

        //TODO make this configurable via command line
        var names = new PollutantToPalmNameConverter(Map.of(
                Pollutant.NO2, "NO2",
                Pollutant.PM, "PM10",
                Pollutant.PM_non_exhaust, "PM10",
                Pollutant.NOx, "NOx"
        ));

        var dateTime = LocalDateTime.parse(date);

        var transformation = createTransformation(crsTransformation);
        BufferedConverter.builder()
                .networkFile(network)
                .emissionEventsFile(emissionEventsFile)
                .pollutantConverter(names)
                .outputFile(outputFile)
                .streetTypes(streetTypes)
                .scaleFactor(scaleFactor)
                .timeBinSize(3600)// this could be configurable
                .date(dateTime)
                .utcOffset(utcOffset)
                .coordinateTransformation(transformation)
                .numberOfDays(numberOfDays)
                .build()
                .write();
    }

    private static CoordinateTransformation createTransformation(String input) {
        if (StringUtils.isBlank(input)) return new IdentityTransformation();

        var split = input.split("->");

        if (split.length != 2) throw new IllegalArgumentException("Could not read coordinate transformation. required format is: EPSG:25832->EPSG:25833 for example.");

        return TransformationFactory.getCoordinateTransformation(split[0], split[1]);
    }
}
