package org.matsim.mosaik2.palm;

import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import it.unimi.dsi.fastutil.doubles.DoubleList;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.math3.util.Precision;
import org.matsim.contrib.analysis.time.TimeBinMap;
import org.matsim.mosaik2.raster.DoubleRaster;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Collectors;

@Log4j2
public class PalmCsvOutput {

	public static void write(Path output, TimeBinMap<DoubleRaster> palmData) {
		write(output, palmData, 0.0);
	}

	public static void write(Path output, TimeBinMap<DoubleRaster> palmData, double minValue) {

		log.info("Writing t,x,y,value data to: " + output);

		try (var writer = Files.newBufferedWriter(output); var printer = createWriteFormat().print(writer)) {

			for (var bin : palmData.getTimeBins()) {
				var time = bin.getStartTime();

				log.info("Writing time slices: [" + time + ", " + (time + palmData.getBinSize()) + "]");
				var raster = bin.getValue();

				raster.forEachCoordinate((x, y, value) -> {
					if (value <= minValue) return;
					printRecord(time, x, y, value, printer);
				});
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		}

		log.info("Finished writing to: " + output);
	}

	public static TimeBinMap<DoubleRaster> read(Path input) {

		var info = readDataInfo(input);
		return read(input, info);
	}

	public static TimeBinMap<DoubleRaster> read(Path input, DataInfo dataInfo) {
		TimeBinMap<DoubleRaster> result = new TimeBinMap<>(dataInfo.getTimeInterval());
		double lastTime = -1;

		log.info("Reading palm output csv with data info: " + dataInfo);

		try (var reader = Files.newBufferedReader(input); var parser = CSVParser.parse(reader, createReadFormat())) {

			for (var record : parser) {

				var time = Double.parseDouble(record.get("time"));

				if (time != lastTime) {
					lastTime = time;
					log.info("Parsing Time Slice: [" + time + ", " + (time + dataInfo.timeInterval) + "]");
				}

				var x = Double.parseDouble(record.get("x"));
				var y = Double.parseDouble(record.get("y"));
				var value = Double.parseDouble(record.get("value"));

				var bin = getRasterForTime(time, result, dataInfo.getRasterInfo());
				bin.setValueForCoord(x, y, value);
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		}

		return result;
	}

	public static DataInfo readDataInfo(Path input) {

		double lastTimeStep = -1;
		double timeStepSize = -1;
		MaxBounds bounds = new MaxBounds();
		Coordinates coordinates = new Coordinates();
		double cellSize = -1;

		log.info("Reading data info of file " + input.toString());
		try (var reader = Files.newBufferedReader(input); var parser = CSVParser.parse(reader, createReadFormat())) {

			for (var record : parser) {

				var x = Double.parseDouble(record.get("x"));
				var y = Double.parseDouble(record.get("y"));
				bounds.adjustTo(x, y);
				if (cellSize < 0.)
					coordinates.add(x, y);

				var time = Double.parseDouble(record.get("time"));


				if (!Precision.equals(lastTimeStep, time)) {
					log.info("lasttimestep: " + lastTimeStep + " time: " + time);
					timeStepSize = time - lastTimeStep;
					if (lastTimeStep > 0)
						cellSize = coordinates.getCellSize();
					lastTimeStep = time;
				}
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		}

		return new DataInfo(new RasterInfo(bounds.createBounds(), cellSize), timeStepSize);
	}

	private static void printRecord(double time, double x, double y, double value, CSVPrinter printer) {
		try {
			printer.printRecord(time, x, y, value);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private static CSVFormat createReadFormat() {
		return CSVFormat.DEFAULT.builder()
				.setHeader("time", "x", "y", "value")
				.setSkipHeaderRecord(true)
				.build();
	}

	private static CSVFormat createWriteFormat() {
		return CSVFormat.DEFAULT.builder()
				.setHeader("time", "x", "y", "value")
				.setSkipHeaderRecord(false)
				.build();
	}

	private static DoubleRaster getRasterForTime(double time, TimeBinMap<DoubleRaster> map, RasterInfo rasterInfo) {

		var bin = map.getTimeBin(time);

		if (!bin.hasValue()) {
			bin.setValue(new DoubleRaster(rasterInfo.getBounds(), rasterInfo.getCellSize(), -1.));
		}

		return bin.getValue();
	}

	/**
	 * This collects x values in an array. Converts it into a cell size
	 */
	@Getter
	private static class Coordinates {

		private final DoubleList xValues = new DoubleArrayList();

		private void add(double x, double y) {
			xValues.add(x);
		}

		private double getCellSize() {

			var xDistinct = xValues.stream()
					.distinct()
					.sorted()
					.collect(Collectors.toCollection(DoubleArrayList::new));

			return xDistinct.getDouble(1) - xDistinct.getDouble(0);
		}
	}

	public static class MaxBounds {

		private double minX = Double.MAX_VALUE;
		private double maxX = Double.MIN_VALUE;
		private double minY = Double.MAX_VALUE;
		private double maxY = Double.MIN_VALUE;

		void adjustTo(double x, double y) {
			this.minX = Math.min(x, minX);
			this.maxX = Math.max(x, maxX);
			this.minY = Math.min(y, minY);
			this.maxY = Math.max(y, maxY);
		}

		DoubleRaster.Bounds createBounds() {
			return new DoubleRaster.Bounds(minX, minY, maxX, maxY);
		}
	}

	@RequiredArgsConstructor
	@Getter
	@ToString
	public static class RasterInfo {
		private final DoubleRaster.Bounds bounds;
		private final double cellSize;
	}

	@Getter
	@RequiredArgsConstructor
	@ToString
	public static class DataInfo {

		private final RasterInfo rasterInfo;
		private final double timeInterval;
	}
}