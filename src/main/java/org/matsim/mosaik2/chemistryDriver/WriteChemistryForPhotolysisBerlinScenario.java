package org.matsim.mosaik2.chemistryDriver;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import org.matsim.core.utils.geometry.transformations.IdentityTransformation;

import java.time.LocalDateTime;

/**
 * Ran this class with:
 * -n
 * "runs-svn/mosaik-2/berlin/mosaik-2-berlin-with-geometry-attributes/output/berlin-with-geometry-attributes.output_network.xml.gz"
 * -e
 * "runs-svn/mosaik-2/berlin/mosaik-2-berlin-with-geometry-attributes/output/berlin-with-geometry-attributes.output_only_emission_events.xml.gz"
 * -o
 * "runs-svn/mosaik-2/berlin/mosaik-2-berlin-with-geometry-attributes/output/berlin-with-geometry-attributes.output_emission_raster.nc"
 *
 */
public class WriteChemistryForPhotolysisBerlinScenario {

    @Parameter(names = "-n", required = true)
    private String networkFile = "";

    @Parameter(names = "-e", required = true)
    private String emissionEventsFile = "";

    @Parameter(names = "-o", required = true)
    private String outputFile;

    @Parameter(names = "-s")
    private double scaleFactor = 10;

    public static void main(String[] args) {

        var writer = new WriteChemistryForPhotolysisBerlinScenario();
        JCommander.newBuilder().addObject(writer).build().parse(args);
        writer.write();
    }

    private void write() {

        var nameConverter = new PollutantToPalmNameConverter();
        //everything should be in UTM-33
        var transformation = new IdentityTransformation();
        var cellSize = 10;
        var originX = 5816919.0;
        var originY = 382756.5;
        var numberOfCells = 671;
        var bounds = new Raster.Bounds(originX, originY, originX + numberOfCells * cellSize, originY + numberOfCells * cellSize);

        var converter = FullFeaturedConverter.builder()
                .networkFile(networkFile)
                .emissionEventsFile(emissionEventsFile)
                .outputFile(outputFile)
                .pollutantConverter(nameConverter)
                .bounds(bounds)
                .transformation(transformation)
                .cellSize(cellSize)
                .scaleFactor(scaleFactor)
                .timeBinSize(3600)
                .date(LocalDateTime.of(2017, 7, 17, 0, 0))
                .numberOfDays(2)
                .offset(2)
                .build();

        converter.write();
    }
}
