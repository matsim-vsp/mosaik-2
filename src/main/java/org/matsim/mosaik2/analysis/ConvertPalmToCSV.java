package org.matsim.mosaik2.analysis;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import org.matsim.mosaik2.palm.PalmOutputReader;
import org.matsim.mosaik2.raster.ConvertToCSV;

public class ConvertPalmToCSV {

    @Parameter(names = "-p", required = true)
    private String palmFile;

    @Parameter(names = "-o" , required = true)
    private String outputTemplate;

    public static void main(String[] args) {

        var converter = new ConvertPalmToCSV();
        JCommander.newBuilder().addObject(converter).build().parse(args);
        converter.run();
    }

    private void run() {

        // get data at 8am
        var palmOutput = PalmOutputReader.read(palmFile, 85, 85);
        //get pm 10
        var pm10Raster = palmOutput.getTimeBins().iterator().next().getValue().get("PM10");

        var outputfile = outputTemplate + "_915pm.csv";
        ConvertToCSV.convert(pm10Raster, outputfile);

        // transform this for kepler.gl
        var transformArgs = new String[] { "-sCRS", "EPSG:25833", "-tCRS", "EPSG:4326", "-i", outputfile, "-o", outputTemplate + "8am-4326.csv"};
        TransformCSV.main(transformArgs);
    }
}
