package org.matsim.mosaik2.analysis;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import org.matsim.mosaik2.palm.PalmOutputReader;
import org.matsim.mosaik2.raster.ConvertToCSV;

public class ConvertPalmTimestepToCSV {

	@Parameter(names = "-p", required = true)
	private String palmFile;

	@Parameter(names = "-o", required = true)
	private String outputTemplate;

	@SuppressWarnings("FieldMayBeFinal")
	@Parameter(names = "-index")
	private int index = 0;

	@Parameter(names = "-species", required = true)
	private String species;


	public static void main(String[] args) {

		var converter = new ConvertPalmTimestepToCSV();
		JCommander.newBuilder().addObject(converter).build().parse(args);
		converter.run();
	}

	private void run() {

		// get data at 8am
		var palmOutput = PalmOutputReader.read(palmFile, index, index, species);
		//get pm 10
		var pm10Raster = palmOutput.getTimeBins().iterator().next().getValue().get(species);

		var outputfile = outputTemplate + "_" + index + ".csv";
		ConvertToCSV.convert(pm10Raster, outputfile);

		// transform this for kepler.gl
		var transformArgs = new String[]{"-sCRS", "EPSG:25833", "-tCRS", "EPSG:4326", "-i", outputfile, "-o", outputTemplate + "_" + index + "-4326.csv"};
		TransformCSV.main(transformArgs);
	}
}