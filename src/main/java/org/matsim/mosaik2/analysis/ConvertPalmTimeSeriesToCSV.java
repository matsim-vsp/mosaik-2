package org.matsim.mosaik2.analysis;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.matsim.mosaik2.palm.PalmCsvOutput;
import org.matsim.mosaik2.palm.PalmOutputReader;

import java.nio.file.Path;
import java.nio.file.Paths;

@Log4j2
@AllArgsConstructor
public class ConvertPalmTimeSeriesToCSV {

	private Path palmFile;
	private Path outputFile;
	private String species;

	public static void main(String[] args) {

		var inputArgs = new InputArgs();
		JCommander.newBuilder().addObject(inputArgs).build().parse(args);
		new ConvertPalmTimeSeriesToCSV(Paths.get(inputArgs.palmFile), Paths.get(inputArgs.outputFile), inputArgs.species).run();
	}

	void run() {

		var palmData = PalmOutputReader.readAll(palmFile.toString(), species);
		PalmCsvOutput.write(outputFile, palmData);
	}

	private static class InputArgs {
		@Parameter(names = "-p", required = true)
		private String palmFile;
		@Parameter(names = "-o", required = true)
		private String outputFile;
		@Parameter(names = "-species", required = true)
		private String species;
	}
}