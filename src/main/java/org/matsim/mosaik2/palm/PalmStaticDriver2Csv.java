package org.matsim.mosaik2.palm;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import org.matsim.mosaik2.raster.DoubleRasterWriter;

import java.nio.file.Paths;

public class PalmStaticDriver2Csv {

	@Parameter(names = "-s")
	private String staticFile;

	@Parameter(names = "-o")
	private String outputFile;

	@Parameter(names = "-v")
	private String variable;

	public static void main(String[] args) {

		var converter = new PalmStaticDriver2Csv();
		JCommander.newBuilder().addObject(converter).build().parse(args);
		converter.convert();
	}

	private void convert() {

		var data = PalmStaticDriverReader.read(Paths.get(staticFile), variable);
		DoubleRasterWriter.writeToCsv(Paths.get(outputFile), data, 0);
	}
}