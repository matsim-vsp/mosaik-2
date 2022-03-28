package org.matsim.mosaik2.chemistryDriver;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import org.matsim.contrib.emissions.Pollutant;
import org.matsim.core.utils.geometry.transformations.IdentityTransformation;
import org.matsim.mosaik2.raster.Raster;

import java.time.LocalDateTime;
import java.util.Map;

public class WriteChemistryForStuttgartScenario {

    @Parameter(names = "-n", required = true)
    private String networkFile = "";

    @Parameter(names = "-e", required = true)
    private String emissionEventsFile = "";

    @Parameter(names = {"-o3", "-outputNest3"}, required = true)
    private String nest3OutputFile = "";

    @Parameter(names = {"-o2", "-outputNest2"}, required = true)
    private String nest2OutputFile = "";

    @Parameter(names = {"-o1", "-outputNest1"}, required = true)
    private String nest1OutputFile;

    @Parameter(names = "-s")
    private double scaleFactor = 10;

    public static void main(String[] args) {

        var writer = new WriteChemistryForStuttgartScenario();
        JCommander.newBuilder().addObject(writer).build().parse(args);
        writer.writeNest3();
        writer.writeNest2();
        writer.writeNest1();
    }

    /**
        Writes the most detailed domain of the stuttgart scenario
     */
    void writeNest3() {

        var cellSize = 2;
        var originX = 513000.0;
        var originY = 5402360.0;
        var bounds = new Raster.Bounds(originX, originY, originX + 960 * cellSize, originY + 1200 * cellSize);
        write(bounds, cellSize, nest3OutputFile);
    }

    /**
     * Write the middle domain
     */
    void writeNest2() {
        var cellSize = 10;
        var originX = 501000.0;
        var originY = 5392000.0;
        var bounds = new Raster.Bounds(originX, originY, originX + 2400 * cellSize, originY + 2296 * cellSize);
        write(bounds, cellSize, nest2OutputFile);
    }

    /**
     * Write parent domain
     */
    void writeNest1() {

     var cellSize = 40;
     var originX = 488020.0;
     var originY = 5378820.0;
     var bounds = new Raster.Bounds(originX, originY, originX + 1248 * cellSize, originY + 1232 * cellSize);
     write(bounds, cellSize, nest1OutputFile);
    }

    void write(Raster.Bounds bounds, double cellSize, String outputFileName) {

        var nameConverter = new PollutantToPalmNameConverter(Map.of(Pollutant.NO2, "NO2", Pollutant.NOx, "NOx", Pollutant.PM, "PM10", Pollutant.PM_non_exhaust, "PM10"));
        // everything is in EPSG:25832
        var transformation = new IdentityTransformation();

        var converter = FullFeaturedConverter.builder()
                .networkFile(networkFile)
                .emissionEventsFile(emissionEventsFile)
                .outputFile(outputFileName)
                .pollutantConverter(nameConverter)
                .bounds(bounds)
                .transformation(transformation)
                .cellSize(cellSize)
                .scaleFactor(scaleFactor)
                .timeBinSize(3600)
                .date(LocalDateTime.of(2018, 7, 8, 0, 0))
                .numberOfDays(2)
                .offset(2)
                .build();

        converter.write();
    }


}
