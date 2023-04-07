package org.matsim.mosaik2.analysis;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import lombok.RequiredArgsConstructor;
import org.apache.commons.csv.CSVPrinter;
import org.matsim.mosaik2.Utils;
import org.matsim.mosaik2.palm.XYTValueCsvData;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicInteger;

@RequiredArgsConstructor
public class LinearFit {

    private final Path matsimPath;
    private final Path palmPath;
    private final Path output;

    public static void main(String[] args) throws IOException {

        var input = new InputArgs();
        JCommander.newBuilder().addObject(input).build().parse(args);

        new LinearFit(
                Paths.get(input.matsimValues), Paths.get(input.palmValues), Paths.get(input.output)
        ).run();
    }

    private static void print(int id, double time, double x, double y, double xVal, double yVal, CSVPrinter printer) {
        try {
            printer.printRecord(id, x, y, time, xVal, yVal);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void run() throws IOException {

        var xValues = XYTValueCsvData.read(this.matsimPath);
        var yValues = XYTValueCsvData.read(this.palmPath);

        try (var writer = Files.newBufferedWriter(this.output); var printer = Utils.createWriteFormat("id", "x", "y", "time", "matsim", "palm").print(writer)) {

            var counter = new AtomicInteger();
            for (var bin : yValues.getTimeBins()) {

                var yRaster = bin.getValue();
                var xBin = xValues.getTimeBin(bin.getStartTime());
                var xRaster = xBin.getValue();
                var time = bin.getStartTime();

                yRaster.forEachCoordinate((x, y, yValue) -> {

                    if (!xRaster.getBounds().covers(x, y)) return;

                    var xValue = xRaster.getValueByCoord(x, y);

                    if (xValue < 0 || yValue < 0) return;

                    var id = counter.incrementAndGet();
                    print(id, time, x, y, xValue, yValue, printer);
                });
            }
        }
    }

    private static class InputArgs {

        @Parameter(names = "-matsim", required = true)
        private String matsimValues;
        @Parameter(names = "-palm", required = true)
        private String palmValues;
        @Parameter(names = "-o", required = true)
        private String output;
    }
}