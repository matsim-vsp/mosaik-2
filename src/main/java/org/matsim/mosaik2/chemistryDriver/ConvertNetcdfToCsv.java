package org.matsim.mosaik2.chemistryDriver;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.matsim.api.core.v01.Coord;
import org.matsim.core.utils.geometry.CoordinateTransformation;
import org.matsim.core.utils.geometry.transformations.TransformationFactory;
import ucar.ma2.InvalidRangeException;
import ucar.nc2.NetcdfFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

@Log4j2
public class ConvertNetcdfToCsv {

    // transform from utm-33 to gk4 which is used in the berlin scenario
    private static final CoordinateTransformation transformation = TransformationFactory.getCoordinateTransformation("EPSG:25833", "EPSG:31468");

    @Parameter(names = "-input")
    public String netcdfFile;

    @Parameter(names = "-output")
    public String outputfile;

    @Parameter(names = "-pollutant")
    public String pollutant;


    public static void main(String[] args) {

        var converter = new ConvertNetcdfToCsv();
        JCommander.newBuilder().addObject(converter).build().parse(args);
        log.info("converting netcdf to csv. Crs transformation is: EPSG:25833 -> EPSG:31468");
        converter.run();
    }

    private void run() {

        log.info("opening Netcdf file: " + this.netcdfFile);
        log.info("opening output file: " + this.outputfile);

        try (var netcdfFile = NetcdfFile.open(this.netcdfFile); var csvFile = Files.newBufferedWriter(Paths.get(outputfile)); var printer = new CSVPrinter(csvFile, CSVFormat.DEFAULT)) {

            // print csv header
            printer.printRecord("time", "x", "y", pollutant);

            var xValues = NetcdfUtils.toDoubleArray(netcdfFile.findVariable("Eu_UTM"));
            var yValues = NetcdfUtils.toDoubleArray(netcdfFile.findVariable("Nu_UTM"));
            var times = NetcdfUtils.toDoubleArray(netcdfFile.findVariable("time"));

            /*
            float kc_NO(time=1440, ku_above_surf=1, y=36, x=36);
             */
            var noValues = netcdfFile.findVariable("kc_" + pollutant);

            for (int ti = 0; ti < times.size(); ti++) {
                for(int yi = 0; yi < yValues.size(); yi++) {
                    for(int xi = 0; xi < xValues.size(); xi++) {

                        var noValue = noValues.read(new int[] {ti, 0, yi, xi}, new int[]{1, 1, 1, 1}).getFloat(0);

                        if (noValue > 0) {
                            var transformedCoord = transformation.transform(new Coord(xValues.get(xi), yValues.get(yi)));
                            printer.printRecord(times.get(ti), transformedCoord.getX(), transformedCoord.getY(), noValue);
                        }
                    }
                }
            }
        } catch (IOException | InvalidRangeException e) {
            e.printStackTrace();
        }
    }
}
