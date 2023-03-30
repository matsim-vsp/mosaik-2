package org.matsim.mosaik2.chemistryDriver;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import org.apache.commons.lang3.StringUtils;
import org.matsim.core.utils.geometry.CoordinateTransformation;
import org.matsim.core.utils.geometry.transformations.IdentityTransformation;
import org.matsim.core.utils.geometry.transformations.TransformationFactory;
import org.matsim.mosaik2.palm.PalmStaticDriverReader;

import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;

public class RunChemistryConverter {

    @Parameter(names = {"--nf", "--network-file"}, required = true)
    private String network;

    @Parameter(names = {"--eef", "--emission-events-file"}, required = true)
    private String emissionEventsFile = "";

    @Parameter(names = {"--sdf", "--static-driver-file"}, required = true)
    private String staticDriver = "";

    @Parameter(names = {"--cdf", "--chemistry-driver-file"}, required = true)
    private String outputFile;

    @Parameter(names = {"--d", "--date"}, required = true)
    private String date;

    @Parameter(names = {"--uo", "--utc-offset"})
    private int utcOffset = 0;

    @Parameter(names = {"--ct", "--coordinate-transformation"})
    private String crsTransformation = "";

    @Parameter(names = {"--sf", "--scale-factor"})
    private double scaleFactor = 10;

    @Parameter(names = {"--nd", "--number-of-days"})
    private int numberOfDays = 2;

    @Parameter(names = {"--s", "--species"})
    private List<String> species = List.of("PM10", "NO2");

    @Parameter(names = {"--lw", "--lane-width"})
    private double laneWidth = 5;

    @Parameter(names = {"--rm", "--raster-method"})
    private String rasterMethod = "WithLaneWidth";

    public static void main(String[] args) {

        var converter = new RunChemistryConverter();
        JCommander.newBuilder().addObject(converter).build().parse(args);
        converter.convert();
    }

    private void convert() {

        var buildings = PalmStaticDriverReader.read(Paths.get(staticDriver), "buildings_2d");
        var names = PollutantToPalmNameConverter.createForSpecies(species);
        var dateTime = LocalDateTime.parse(date);
        var transformation = createTransformation(crsTransformation);
        var rasterMethod = EmissionRasterer.RasterMethod.valueOf(this.rasterMethod);

        BufferedConverter.builder()
                .networkFile(network)
                .emissionEventsFile(emissionEventsFile)
                .pollutantConverter(names)
                .outputFile(outputFile)
                .buildings(buildings)
                .scaleFactor(scaleFactor)
                .timeBinSize(3600)// this could be configurable
                .date(dateTime)
                .utcOffset(utcOffset)
                .transformation(transformation)
                .numberOfDays(numberOfDays)
                .laneWidth(laneWidth)
                .rasterMethod(rasterMethod)
                .build()
                .write();
    }

    private static CoordinateTransformation createTransformation(String input) {
        if (StringUtils.isBlank(input)) return new IdentityTransformation();

        var split = input.split("->");

        if (split.length != 2)
            throw new IllegalArgumentException("Could not read coordinate transformation. required format is: EPSG:25832->EPSG:25833 for example.");

        return TransformationFactory.getCoordinateTransformation(split[0], split[1]);
    }
}