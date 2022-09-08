package org.matsim.mosaik2.palm;

import lombok.extern.log4j.Log4j2;
import org.matsim.contrib.analysis.time.TimeBinMap;
import org.matsim.mosaik2.raster.DoubleRaster;
import ucar.ma2.ArrayFloat;
import ucar.ma2.InvalidRangeException;
import ucar.nc2.NetcdfFiles;

import java.io.IOException;
import java.util.Objects;

/**
 * This class reads a masked averaged output file of PALM.
 */
@Log4j2
public class PalmOutputReader {

	public static TimeBinMap<DoubleRaster> read(String filename) {
		return read(filename, 0, Integer.MAX_VALUE, "PM10");
	}

	public static TimeBinMap<DoubleRaster> readAll(String filename, String species) {
		return read(filename, 0, Integer.MAX_VALUE, species);
	}

	public static TimeBinMap<DoubleRaster> read(String filename, int fromTimeIndex, int toTimeIndex, String species) {

		log.info("Try opening Netcdf file at: " + filename);


		try (var file = NetcdfFiles.open(filename)) {

			var timeVar = file.findVariable("time");

			// x and y are supposed to be in the correct coordinate system. For Berlin those should be in UTM-33
			var xVar = file.findVariable("E_UTM");
			var yVar = file.findVariable("N_UTM");
			var kcPm10Var = Objects.requireNonNull(file.findVariable("kc_" + species));

			var times = NetcdfConverters.toDoubleArray(Objects.requireNonNull(timeVar));
			var x = NetcdfConverters.toDoubleArray(Objects.requireNonNull(xVar));
			var y = NetcdfConverters.toDoubleArray(Objects.requireNonNull(yVar));

			//  Dimension kuAbove = new Dimension(" ku_above_surf", 1);
			//  kcPm10Var.reduce(List.of(kuAbove)); // remove ku_above_surf, since we are looking at the first layer above surface. This reduces the dimension of the values array

			var emissions = NetcdfConverters.createTimeBinMap(times, fromTimeIndex);
			var bounds = NetcdfConverters.createBounds(x, y);
			var cellSize = NetcdfConverters.getCellSize(x, y);
			var shapeForReadOperation = new int[]{1, 1, y.length, x.length};

			for (int ti = fromTimeIndex; ti < times.length && ti <= toTimeIndex; ti++) {

				var timestep = times[ti];
				var startTime = timestep - emissions.getBinSize();
				log.info("Parsing timestep [" + startTime + ", " + timestep + "]");

				var raster = new DoubleRaster(bounds, cellSize);
				ArrayFloat.D4 emissionData = (ArrayFloat.D4) kcPm10Var.read(new int[]{ti, 0, 0, 0}, shapeForReadOperation);

				for (int xi = 0; xi < x.length; xi++) {
					for (int yi = 0; yi < y.length; yi++) {

						float value = emissionData.get(0, 0, yi, xi);
						raster.adjustValueForIndex(xi, yi, value);
					}
				}

				emissions.getTimeBin(startTime).setValue(raster);
			}
			log.info("Finished reading NetcdfFile");
			log.info("");
			return emissions;

		} catch (IOException | InvalidRangeException e) {
			throw new RuntimeException(e);
		}
	}
}