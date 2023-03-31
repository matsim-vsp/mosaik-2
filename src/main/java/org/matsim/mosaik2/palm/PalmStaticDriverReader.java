package org.matsim.mosaik2.palm;

import lombok.extern.log4j.Log4j2;
import org.matsim.mosaik2.raster.DoubleRaster;
import ucar.ma2.ArrayByte;
import ucar.ma2.ArrayFloat;
import ucar.nc2.NetcdfFile;
import ucar.nc2.NetcdfFiles;
import ucar.nc2.Variable;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;

@Log4j2
public class PalmStaticDriverReader {
	public static DoubleRaster read(Path filePath, String fieldName) {

		log.info("Try opening Netcdf file at: " + filePath);

		try (var file = NetcdfFiles.open(filePath.toString())) {

			var raster = createTarget(file);

			var variableVar = Objects.requireNonNull(file.findVariable(fieldName));
			var data = readIntoMemory(variableVar);

			raster.setValueForEachIndex((xi, yi) -> data.get(yi, xi));

			return raster;

		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public static DoubleRaster createTarget(NetcdfFile file) throws IOException {
		var xVar = Objects.requireNonNull(file.findVariable("E_UTM"));
		var yVar = Objects.requireNonNull(file.findVariable("N_UTM"));

		var x = NetcdfConverters.varToDoubleArray(xVar, new int[]{1, yVar.getDimension(1).getLength()});
		var y = NetcdfConverters.varToDoubleArray(yVar, new int[]{yVar.getDimension(0).getLength(), 1});

		var cellSize = NetcdfConverters.getCellSize(x, y);

		// we have some static files which have the coords in the wrong order. If we detect a cell size of 0,
		// we try the wrong format instead.
		if (cellSize == 0.0) {
			xVar = Objects.requireNonNull(file.findVariable("N_UTM"));
			yVar = Objects.requireNonNull(file.findVariable("E_UTM"));
			x = NetcdfConverters.varToDoubleArray(xVar, new int[]{1, xVar.getDimension(1).getLength()});
			y = NetcdfConverters.varToDoubleArray(yVar, new int[]{yVar.getDimension(0).getLength(), 1});
			cellSize = NetcdfConverters.getCellSize(x, y);
		}

		if (cellSize == 0.0) {
			throw new RuntimeException("Detected a cell size of 0 for the static driver file. This is probably not what was intended. Fix your driver File!");
		}

		var bounds = NetcdfConverters.createBounds(x, y);
		return new DoubleRaster(bounds, cellSize);
	}

	private static GetValue readIntoMemory(Variable var) throws IOException {

		return switch (var.getDataType()) {
			case FLOAT -> new ArrayFloat2D((ArrayFloat.D2) var.read());
			case BYTE -> new ArrayByte2D((ArrayByte.D2) var.read());
			default -> throw new RuntimeException("Data Type not supported: " + var.getDataType());
		};
	}

	private record ArrayByte2D(ArrayByte.D2 data) implements GetValue {

		@Override
		public double get(int yi, int xi) {
			return data.get(yi, xi);
		}
	}

	private record ArrayFloat2D(ArrayFloat.D2 data) implements GetValue {

		@Override
		public double get(int yi, int xi) {
			return data.get(yi, xi);
		}
	}

	@FunctionalInterface
	interface GetValue {

		double get(int yi, int xi);
	}
}