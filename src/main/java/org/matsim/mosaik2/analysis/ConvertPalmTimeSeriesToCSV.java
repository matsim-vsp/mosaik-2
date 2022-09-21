package org.matsim.mosaik2.analysis;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import lombok.extern.log4j.Log4j2;
import org.matsim.mosaik2.palm.PalmCsvOutput;
import org.matsim.mosaik2.palm.PalmOutputReader;

import java.nio.file.Paths;

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

	private void run() {

		var palmData = PalmOutputReader.readAll(palmFile, species);
		PalmCsvOutput.write(Paths.get(outputFile), palmData);
	}
}