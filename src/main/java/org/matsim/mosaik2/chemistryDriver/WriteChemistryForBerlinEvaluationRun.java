package org.matsim.mosaik2.chemistryDriver;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import org.matsim.contrib.emissions.Pollutant;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * This class writes emission data for the berlin evaluation run. The palm run has three domains: 1 Parent domain with
 * a cell size of 16m and two child domains with a 2m grid size. The Palm run as well as the Matsim run we are deriving
 * the emissions of has UTM-33 (EPSG:25833) as CRS. Hence, no transformation is necessary. The matsim input (network and
 * events file can be found at: runs-svn/mosaik-2/berlin/mosaik-2-berlin-with-geometry-attributes/output
 * The output is written to: shared-svn/projects/mosaik-2/data/valm02_v04
 *
 * The matsim scenario is a 10% sample. The default scale factor of 10 was used.
 */
public class WriteChemistryForBerlinEvaluationRun {

    @Parameter(names = "-n", required = true)
    private String networkFile = "";

    @Parameter(names = "-e", required = true)
    private String emissionEventsFile = "";

    @Parameter(names = {"-o2", "-outputNest3"}, required = true)
    private String nest2OutputFile = "";

    @Parameter(names = {"-o3", "-outputNest2"}, required = true)
    private String nest3OutputFile = "";

    @Parameter(names = {"-op", "-outputNest1"}, required = true)
    private String parentOutputFile;

    @Parameter(names = "-s")
    private double scaleFactor = 10;

    public static void main(String[] args) {
        var writer = new WriteChemistryForBerlinEvaluationRun();
        JCommander.newBuilder().addObject(writer).build().parse(args);
        writer.writeParentDomain();
        writer.writeNest2();
        writer.writeNest3();
    }

    private void writeParentDomain() {

        var cellSize = 16;
        var originX = 369349.5;
        var originY = 5798930.0;
        var numberOfCellsX = 2943;
        var numberOfCellsY = 2439;
        var bounds = new Raster.Bounds(originX, originY, originX + numberOfCellsX * cellSize, originY + numberOfCellsY * cellSize);

        write(cellSize, bounds, parentOutputFile);
    }

    private void writeNest2() {

        var cellSize = 2;
        var originX = 385198.5;
        var originY = 5818429.0;
        var numberOfCells = 1439;
        var bounds = new Raster.Bounds(originX, originY, originX + numberOfCells * cellSize, originY + numberOfCells * cellSize);

        write(cellSize, bounds, nest2OutputFile);
    }

    private void writeNest3() {

        var cellSize = 2;
        var originX = 384686.5;
        var originY = 5812189.0;
        var numberOfCells = 1439;
        var bounds = new Raster.Bounds(originX, originY, originX + numberOfCells * cellSize, originY + numberOfCells * cellSize);

        write(cellSize, bounds, nest3OutputFile);
    }

    private void write(double cellSize, Raster.Bounds bounds, String outputFileName) {

        var names = new PollutantToPalmNameConverter(Map.of(
                Pollutant.NO2, "NO2",
                Pollutant.PM, "PM10",
                Pollutant.PM_non_exhaust, "PM10",
                Pollutant.NOx, "NOx"
        ));

        var converter = FullFeaturedConverter.builder()
                .networkFile(networkFile)
                .emissionEventsFile(emissionEventsFile)
                .pollutantConverter(names)
                .outputFile(outputFileName)
                .bounds(bounds)
                .cellSize(cellSize)
                .scaleFactor(scaleFactor)
                .timeBinSize(3600)
                .date(LocalDateTime.of(2018, 7, 16, 0, 0, 0))
                .numberOfDays(3)
                .offset(2)
                .build();

        converter.write();
    }
}
