package org.matsim.mosaik2.chemistryDriver;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import org.matsim.contrib.emissions.Pollutant;
import org.matsim.core.utils.geometry.transformations.IdentityTransformation;

import java.util.Map;

public class WriteChemistryForStuttgartScenario {

    private static final Map<Pollutant, String> pollutants = Map.of(
            Pollutant.NO2, "NO2",
            Pollutant.CO2_TOTAL, "CO2",
            Pollutant.PM, "PM10",
            Pollutant.CO, "CO",
            Pollutant.NOx, "NOx"
    );// NO is missing for now, it would have to be calculated from NOx - NO2

    @Parameter(names = "-n", required = true)
    private String networkFile = "";

    @Parameter(names = "-e", required = true)
    private String emissionEventsFile = "";

    @Parameter(names = {"-o3", "-outputNest3"}, required = true)
    private String nest3OutputFile = "";

    @Parameter(names = "-s")
    private double scaleFactor = 10;

    public static void main(String[] args) {

        var writer = new WriteChemistryForStuttgartScenario();
        JCommander.newBuilder().addObject(writer).build().parse(args);
        writer.writeNest3();
    }

    /*
    Writes the most detailed domain of the stuttgart scenario
     */
    void writeNest3() {

        var cellSize = 2;
        var nameConverter = new PollutantToPalmNameConverter(Map.of(Pollutant.NO2, "NO2", Pollutant.NOx, "NOx"));
        var bounds = new Raster.Bounds(513000.0, 5402360.0, 513000.0 + 960 * cellSize, 5402360.0 + 1200 * cellSize);
        var transformation = new IdentityTransformation();

        var converter = FullFeaturedConverter.builder()
                .networkFile(networkFile)
                .emissionEventsFile(emissionEventsFile)
                .outputFile(nest3OutputFile)
                .pollutantConverter(nameConverter)
                .bounds(bounds)
                .transformation(transformation)
                .cellSize(1)
                .scaleFactor(scaleFactor)
                .timeBinSize(3600)
                .date("2019-07-02")
                .numberOfDays(1)
                .build();

        converter.write();
    }
}
