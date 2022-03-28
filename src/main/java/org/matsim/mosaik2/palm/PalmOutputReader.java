package org.matsim.mosaik2.palm;

import lombok.extern.log4j.Log4j2;
import org.matsim.contrib.analysis.time.TimeBinMap;
import org.matsim.contrib.emissions.Pollutant;
import org.matsim.mosaik2.raster.DoubleRaster;
import ucar.ma2.ArrayFloat;
import ucar.ma2.InvalidRangeException;
import ucar.nc2.NetcdfFiles;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@Log4j2
public class PalmOutputReader {

    public static TimeBinMap<Map<String, DoubleRaster>> read(String filename) {
        return read(filename, 0, Integer.MAX_VALUE);
    }

    public static TimeBinMap<Map<String, DoubleRaster>> read(String filename, int fromTimeIndex, int toTimeIndex) {

        log.info("Try opening Netcdf file at: " + filename);

        try ( var file = NetcdfFiles.open(filename)) {

            var timeVar = file.findVariable("time");

            // x and y are supposed to be in the correct coordinate system. For Berlin those should be in UTM-33
            var xVar = file.findVariable("E_UTM");
            var yVar = file.findVariable("N_UTM");
            var kcPm10Var = Objects.requireNonNull(file.findVariable("kc_PM10"));

            var times = NetcdfConverters.toDoubleArray(Objects.requireNonNull(timeVar));
            var x = NetcdfConverters.toDoubleArray(Objects.requireNonNull(xVar));
            var y = NetcdfConverters.toDoubleArray(Objects.requireNonNull(yVar));

          //  Dimension kuAbove = new Dimension(" ku_above_surf", 1);
          //  kcPm10Var.reduce(List.of(kuAbove)); // remove ku_above_surf, since we are looking at the first layer above surface. This reduces the dimension of the values array

            var emissions = NetcdfConverters.createTimeBinMap(times, fromTimeIndex);
            var bounds = NetcdfConverters.createBounds(x, y);
            var cellSize = NetcdfConverters.getCellSize(x, y);
            var shapeForReadOperation = new int[] { 1, 1, y.length, x.length };

            for (int ti = fromTimeIndex; ti < times.length && ti <= toTimeIndex; ti++) {

                var timestep = times[ti];
                log.info("Parsing timestep " + timestep);

                var timeBin = emissions.getTimeBin(timestep);
                var raster = new DoubleRaster(bounds, cellSize);
                ArrayFloat.D4 emissionData = (ArrayFloat.D4) kcPm10Var.read(new int[] { ti, 0, 0, 0 }, shapeForReadOperation);

                if (!timeBin.hasValue()) {
                    timeBin.setValue(new HashMap<>());
                }

                for (int xi = 0; xi < x.length; xi++) {
                    for (int yi = 0; yi < y.length; yi++) {

                        float value = emissionData.get(0, 0, yi, xi);
                        raster.adjustValueForIndex(xi, yi, value);
                    }
                }

                timeBin.getValue().put(Pollutant.PM.toString(), raster);
            }
            log.info("Finished reading NetcdfFile");
            log.info("");
            return emissions;

        } catch (IOException | InvalidRangeException e) {
            throw new RuntimeException(e);
        }
    }
}
