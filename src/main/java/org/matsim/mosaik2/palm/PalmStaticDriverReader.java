package org.matsim.mosaik2.palm;

import lombok.extern.log4j.Log4j2;
import org.matsim.mosaik2.raster.DoubleRaster;
import ucar.ma2.ArrayByte;
import ucar.nc2.NetcdfFiles;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;

@Log4j2
public class PalmStaticDriverReader {
	public static DoubleRaster read(Path filePath, String fieldName) {

		log.info("Try opening Netcdf file at: " + filePath);

		try (var file = NetcdfFiles.open(filePath.toString())) {

			// somehow the x values are in N_UTM and vice versa. 🤷‍♀️
			var xVar = Objects.requireNonNull(file.findVariable("N_UTM"));
			var yVar = Objects.requireNonNull(file.findVariable("E_UTM"));
			var variableVar = Objects.requireNonNull(file.findVariable(fieldName));

			var x = NetcdfConverters.varToDoubleArray(xVar, new int[]{1, xVar.getDimension(1).getLength()});
			var y = NetcdfConverters.varToDoubleArray(yVar, new int[]{yVar.getDimension(0).getLength(), 1});

			var bounds = NetcdfConverters.createBounds(x, y);
			var cellSize = NetcdfConverters.getCellSize(x, y);
			var raster = new DoubleRaster(bounds, cellSize);

			ArrayByte.D2 streetTypeData = (ArrayByte.D2) variableVar.read();

			for (int xi = 0; xi < x.length; xi++) {
				for (int yi = 0; yi < y.length; yi++) {
					var byteValue = streetTypeData.get(yi, xi);
					// hm, ther shold be a way without boxing this.
					var doubleValue = Double.valueOf(byteValue);
					raster.adjustValueForIndex(xi, yi, doubleValue);
				}
			}
			return raster;

		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
}