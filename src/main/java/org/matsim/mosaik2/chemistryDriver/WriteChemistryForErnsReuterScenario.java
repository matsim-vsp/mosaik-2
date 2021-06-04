package org.matsim.mosaik2.chemistryDriver;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import org.matsim.contrib.emissions.Pollutant;
import org.matsim.core.utils.geometry.transformations.TransformationFactory;

import java.util.Map;

public class WriteChemistryForErnsReuterScenario {

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

    @Parameter(names = "-o", required = true)
    private String outputFile = "";

    public static void main(String[] args) {

        var writer = new WriteChemistryForErnsReuterScenario();
        JCommander.newBuilder().addObject(writer).build().parse(args);
        writer.write();
    }

    void write() {

        var nameConverter = new PollutantToPalmNameConverter(pollutants);
        var bounds = new Raster.Bounds(385029.5, 5818412.0, 387075.5, 5820458);
        var transformation = TransformationFactory.getCoordinateTransformation("EPSG:31468", "EPSG:25833");


        var converter = FullFeaturedConverter.builder()
                .networkFile(networkFile)
                .emissionEventsFile(emissionEventsFile)
                .outputFile(outputFile)
                .pollutantConverter(nameConverter)
                .bounds(bounds)
                .transformation(transformation)
                .cellSize(2)
                .scaleFactor(100)
                .timeBinSize(3600)
                .date("2017-07-31")
                .build();

        converter.write();
    }
}
