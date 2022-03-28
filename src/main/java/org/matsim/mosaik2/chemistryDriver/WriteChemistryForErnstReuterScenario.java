package org.matsim.mosaik2.chemistryDriver;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import org.matsim.contrib.emissions.Pollutant;
import org.matsim.core.utils.geometry.transformations.TransformationFactory;
import org.matsim.mosaik2.raster.Raster;

import java.time.LocalDateTime;
import java.util.Map;

public class WriteChemistryForErnstReuterScenario {

    private static final Map<Pollutant, String> pollutants = Map.of(
            Pollutant.NO2, "NO2",
            Pollutant.CO2_TOTAL, "CO2",
            Pollutant.PM, "PM10",
            Pollutant.PM_non_exhaust, "PM10",
            Pollutant.CO, "CO",
            Pollutant.NOx, "NOx"
    );// NO is missing for now, it would have to be calculated from NOx - NO2

    @Parameter(names = "-n", required = true)
    private String networkFile = "";

    @Parameter(names = "-e", required = true)
    private String emissionEventsFile = "";

    @Parameter(names = "-o", required = true)
    private String outputFile = "";

    @Parameter(names = "-s")
    private double scaleFactor = 10;

    public static void main(String[] args) {

        var writer = new WriteChemistryForErnstReuterScenario();
        JCommander.newBuilder().addObject(writer).build().parse(args);
        writer.write();
    }

    void write() {

        var nameConverter = new PollutantToPalmNameConverter(pollutants);
        var bounds = new Raster.Bounds(385030.5, 5818413.0, 387076.5, 5820459);
        var transformation = TransformationFactory.getCoordinateTransformation("EPSG:31468", "EPSG:25833");


        var converter = FullFeaturedConverter.builder()
                .networkFile(networkFile)
                .emissionEventsFile(emissionEventsFile)
                .outputFile(outputFile)
                .pollutantConverter(nameConverter)
                .bounds(bounds)
                .transformation(transformation)
                .cellSize(2)
                .scaleFactor(scaleFactor)
                .timeBinSize(3600)
                .date(LocalDateTime.of(2019,7,2,0,0))
                .numberOfDays(2)
                .build();

        converter.write();
    }
}
