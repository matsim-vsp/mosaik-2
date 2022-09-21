package org.matsim.mosaik2.raster;

import lombok.extern.log4j.Log4j2;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.util.concurrent.atomic.AtomicInteger;

@Log4j2
public class ConvertToCSV {

    private static DecimalFormat format = new DecimalFormat("#");

    public static void convert(DoubleRaster raster, String outputFile) {

        log.info("Writing Raster to: " + outputFile);
        var counter = new AtomicInteger();
        var size = raster.getYLength() * raster.getXLength();

        try(var writer = Files.newBufferedWriter(Paths.get(outputFile)); var printer = CSVFormat.DEFAULT.withHeader("x", "y", "value").print(writer)) {

            raster.forEachCoordinate((x, y, value) -> {
                var count = counter.incrementAndGet();
                if (count % 100000 == 0) {
                    log.info("Printed " + count + "/" + size);
                }
                printRecord(x, y, value, printer);
            });

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        log.info("Finished writing Raster.");
    }

    private static void printRecord(double x, double y, double value, CSVPrinter printer) {
        try {
            printer.printRecord(x,y, value);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
