package org.matsim.mosaik2.palm;

import lombok.extern.log4j.Log4j2;
import org.matsim.contrib.analysis.time.TimeBinMap;
import org.matsim.mosaik2.raster.DoubleRaster;
import ucar.ma2.ArrayFloat;
import ucar.ma2.InvalidRangeException;
import ucar.nc2.NetcdfFiles;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@Log4j2
public class PalmMergedOutputReader {

    public static TimeBinMap<Map<String, DoubleRaster>> readFiles(Collection<Path> files, Collection<String> species) {

        // get a time bin map from the first file
        var iterator = files.iterator();
        var firstFile = iterator.next();
        var resultMap = readFile(firstFile, species);

        while (iterator.hasNext()) {
            var fileName = iterator.next();
            var emissions = readFile(fileName, species);

            log.info("Merging " + emissions.getTimeBins().size() + " time bins into result map.");
            for (var bin : emissions.getTimeBins()) {
                // this assumes that all files contain distinct time steps
                resultMap.getTimeBin(bin.getStartTime()).setValue(bin.getValue());
            }
        }

        return resultMap;
    }

    public static TimeBinMap<Map<String, DoubleRaster>> readFile(Path fileName, Collection<String> species) {

        log.info("Try opening Netcdf file at: " + fileName);

        try (var file = NetcdfFiles.open(fileName.toString())) {

            // get the vars
            var timeVar = file.findVariable("time");
            // x and y are supposed to be in the correct coordinate system. For Berlin those should be in UTM-33
            var xVar = file.findVariable("E_UTM");
            var yVar = file.findVariable("N_UTM");

            // get the actual values
            var times = NetcdfConverters.toDoubleArray(Objects.requireNonNull(timeVar));
            var x = NetcdfConverters.toDoubleArray(Objects.requireNonNull(xVar));
            var y = NetcdfConverters.toDoubleArray(Objects.requireNonNull(yVar));


            TimeBinMap<Map<String, DoubleRaster>> emissions = NetcdfConverters.createTimeBinMap(times, 0);
            var bounds = NetcdfConverters.createBounds(x, y);
            var cellSize = NetcdfConverters.getCellSize(x, y);
            var shapeForReadOperation = new int[]{1, 1, y.length, x.length};

            for (var speciesName : species) {
                var speciesVar = Objects.requireNonNull(file.findVariable("kc_" + speciesName));

                for (var ti = 0; ti < times.length; ti++) {

                    double timeStep = Math.round(times[ti]);
                    double startTime = timeStep - emissions.getBinSize();

                    log.info("Parsing timestep [" + startTime + ", " + timeStep + "] for species: " + speciesName);
                    ArrayFloat.D4 speciesData = (ArrayFloat.D4) speciesVar.read(new int[]{ti, 0, 0, 0}, shapeForReadOperation);
                    var raster = new DoubleRaster(bounds, cellSize);
                    // this copies the data into the result raster
                    raster.setValueForEachIndex((xi, yi) -> speciesData.get(0, 0, yi, xi));

                    // store the raster into the time bin map
                    emissions.getTimeBin(startTime).computeIfAbsent(HashMap::new).put(speciesName, raster);
                }
            }
            return emissions;

        } catch (IOException | InvalidRangeException e) {
            throw new RuntimeException(e);
        }
    }
}
