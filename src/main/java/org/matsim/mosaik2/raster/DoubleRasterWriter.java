package org.matsim.mosaik2.raster;

import lombok.extern.log4j.Log4j2;
import org.apache.commons.csv.CSVPrinter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.matsim.mosaik2.Utils.createWriteFormat;

@Log4j2
public class DoubleRasterWriter {

	public static void writeToCsv(Path file, DoubleRaster raster, double minValue) {

		log.info("Writing x,y,value data to: " + file);

		try (var writer = Files.newBufferedWriter(file); var printer = createWriteFormat("x", "y", "value").print(writer)) {

			raster.forEachCoordinate((x, y, value) -> {
				if (value <= minValue) return;
				printRecord(x, y, value, printer);
			});
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private static void printRecord(double x, double y, double value, CSVPrinter printer) {
		try {
			printer.printRecord(x, y, value);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
}