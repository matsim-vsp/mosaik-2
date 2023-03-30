package org.matsim.mosaik2.chemistryDriver;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import lombok.extern.log4j.Log4j2;
import org.matsim.api.core.v01.Coord;
import org.matsim.contrib.analysis.time.TimeBinMap;
import org.matsim.mosaik2.palm.PalmCsvOutput;
import org.matsim.mosaik2.raster.DoubleRaster;
import ucar.ma2.InvalidRangeException;
import ucar.nc2.NetcdfFiles;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

@Log4j2
public class PalmChemistryInput2Csv {

	public static void main(String[] args) {

		var input = new InputArgs();
		JCommander.newBuilder().addObject(input).build().parse(args);

		if (input.staticDriver == null) {
			run(Paths.get(input.inputFile), Paths.get(input.outputFile), input.species, new Coord(0, 0));
		} else {
			run(Paths.get(input.inputFile), Paths.get(input.staticDriver), Paths.get(input.outputFile), input.species);
		}
	}

	public static void run(Path input, Path staticDriver, Path output, String species) {

		log.info("trying to find origin from static driver file. ");
		try (var staticDriverFile = NetcdfFiles.open(staticDriver.toString())) {

			var eastingVar = staticDriverFile.findVariable("E_UTM");
			var northingVar = staticDriverFile.findVariable("N_UTM");

			var originY = eastingVar.read(new int[]{0, 0}, new int[]{1, 1}).getFloat(0);
			var originX = northingVar.read(new int[]{0, 0}, new int[]{1, 1}).getFloat(0);
			var origin = new Coord(originX, originY);

			log.info("Origin is at: " + origin);

			run(input, output, species, origin);
		} catch (IOException | InvalidRangeException e) {
			throw new RuntimeException(e);
		}
	}

	public static void run(Path input, Path output, String species, Coord origin) {

		var emission = PalmChemistryInputReader.read(input.toString());
		TimeBinMap<DoubleRaster> singleSpecies = new TimeBinMap<>(emission.getBinSize(), emission.getStartTime());

		for (var bin : emission.getTimeBins()) {

			var speciesRaster = bin.getValue().get(species);

			// the chemistry driver only has x and y coordinates which start at 0. To display this on a map we need to
			// translate this from an origin point and then copy into a new raster.
			var bounds = new DoubleRaster.Bounds(
					origin.getX(), origin.getY(),
					origin.getX() + (speciesRaster.getBounds().getMaxX() - speciesRaster.getBounds().getMinX()),
					origin.getY() + (speciesRaster.getBounds().getMaxY() - speciesRaster.getBounds().getMinY())
			);
			var raster = new DoubleRaster(bounds, speciesRaster.getCellSize());
			raster.setValueForEachIndex(speciesRaster::getValueByIndex);

			singleSpecies.getTimeBin(bin.getStartTime()).setValue(raster);
		}

		PalmCsvOutput.write(output, singleSpecies);
	}

	private static class InputArgs {

		@Parameter(names = "-input", required = true)
		private String inputFile;
		@Parameter(names = "-output", required = true)
		private String outputFile;
		@Parameter(names = "-staticDriver")
		private String staticDriver;
		@Parameter(names = "-species", required = true)
		private String species;

		private InputArgs() {
		}
	}
}