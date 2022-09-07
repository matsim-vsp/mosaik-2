package org.matsim.mosaik2.analysis;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.matsim.mosaik2.palm.PalmOutputReader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicInteger;

@Log4j2
public class ConvertPalmTimeSeriesToCSV {

	// the berlin run is UTC+2, Example:
	// 	PALM second 0 is 7200 (2am) in MATSim
	//  PALM av second 3600 means 0am - 1am UTC corresponding to 2am - 3am (7200 - 10800s)
	@SuppressWarnings("FieldMayBeFinal")
	@Parameter(names = "-timeOffset")
	private double offset = 7200;
	@Parameter(names = "-p", required = true)
	private String palmFile;
	@Parameter(names = "-o", required = true)
	private String outputFile;
	@Parameter(names = "-species", required = true)
	private String species;

	public static void main(String[] args) {

		var converter = new ConvertPalmTimeSeriesToCSV();
		JCommander.newBuilder().addObject(converter).build().parse(args);
		converter.run();
	}

	private static void printRecord(double time, double x, double y, double value, CSVPrinter printer) {
		try {
			printer.printRecord(time, x, y, value);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private void run() {

		var palmData = PalmOutputReader.readAll(palmFile, species);
		var counter = new AtomicInteger();

		try (var writer = Files.newBufferedWriter(Paths.get(outputFile)); var printer = CSVFormat.DEFAULT.withHeader("time", "x", "y", "value").print(writer)) {

			for (var bin : palmData.getTimeBins()) {
				var time = bin.getStartTime();
				var raster = bin.getValue().get(species);

				raster.forEachCoordinate((x, y, value) -> {
					var count = counter.incrementAndGet();
					if (count % 100000 == 0) {
						log.info("Printed " + count);
					}
					printRecord(time, x, y, value, printer);
				});
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
}