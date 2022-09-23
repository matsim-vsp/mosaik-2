package org.matsim.mosaik2.analysis;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import lombok.RequiredArgsConstructor;
import org.matsim.contrib.analysis.time.TimeBinMap;
import org.matsim.mosaik2.chemistryDriver.PalmChemistryInputReader;
import org.matsim.mosaik2.palm.PalmCsvOutput;
import org.matsim.mosaik2.raster.DoubleRaster;

import java.nio.file.Path;
import java.nio.file.Paths;

@RequiredArgsConstructor
public class ConvertChemistryInputToCsv {

	private final Path chemistryDriver;
	private final Path output;
	private final String species;
	private final int utcOffset;

	public static void main(String[] args) {

		var input = new InputArgs();
		JCommander.newBuilder().addObject(input).build().parse(args);

		new ConvertChemistryInputToCsv(Paths.get(input.chemistryDriverFile), Paths.get(input.output), input.species, input.utcOffset).run();

	}

	private void run() {

		var driverData = PalmChemistryInputReader.read(chemistryDriver.toString());
		TimeBinMap<DoubleRaster> singleSpecies = new TimeBinMap<>(driverData.getBinSize(), driverData.getStartTime() + utcOffset);
		for (var bin : driverData.getTimeBins()) {
			var startTime = bin.getStartTime() + utcOffset;
			var raster = bin.getValue().get(species);
			singleSpecies.getTimeBin(startTime).setValue(raster);
		}
		PalmCsvOutput.write(output, singleSpecies);
	}

	@SuppressWarnings("FieldMayBeFinal")
	private static class InputArgs {

		@Parameter(names = "-chem", required = true)
		private String chemistryDriverFile;

		@Parameter(names = "-output", required = true)
		private String output;

		@Parameter(names = "-species", required = true)
		private String species;

		@Parameter(names = "-utcOffset")
		private int utcOffset = 7200;
	}
}