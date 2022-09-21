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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Log4j2
public class PalmCsvOutput {

	public static void write(Path output, TimeBinMap<DoubleRaster> palmData) {

		log.info("Writing t,x,y,value data to: " + output);

		var counter = new AtomicInteger();
		try (var writer = Files.newBufferedWriter(output); var printer = createWriteFormat().print(writer)) {

			for (var bin : palmData.getTimeBins()) {
				var time = bin.getStartTime();
				var raster = bin.getValue();

				raster.forEachCoordinate((x, y, value) -> {

					if (value <= 0) return;

					var count = counter.incrementAndGet();
					if (count % 100000 == 0) {
						log.info("Printed " + count);
					}
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
		var count = 0;

		log.info("Reading palm output csv with data info: " + dataInfo);

		try (var reader = Files.newBufferedReader(input); var parser = CSVParser.parse(reader, createReadFormat())) {

			for (var record : parser) {
				count++;
				if (count % 100000 == 0) {
					log.info("Parsed " + count);
				}

				var time = Double.parseDouble(record.get("time"));
				var x = Double.parseDouble(record.get("x"));
				var y = Double.parseDouble(record.get("y"));
				var value = Double.parseDouble(record.get("value"));

				var bin = getRasterForTime(time, result, dataInfo.getRasterInfo());
				bin.adjustValueForCoord(x, y, value);
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		}

		return result;
	}

	public static DataInfo readDataInfo(Path input) {

		double currentTimeStep = -1;
		Coordinates coordinates = new Coordinates();

		log.info("Reading data info of file " + input.toString());
		try (var reader = Files.newBufferedReader(input); var parser = CSVParser.parse(reader, createReadFormat())) {

			for (var record : parser) {

				var time = Double.parseDouble(record.get("time"));

				// make sure to only parse the first timestep
				if (currentTimeStep < 0.) {
					currentTimeStep = time;
				} else if (!Precision.equals(currentTimeStep, time)) {
					return new DataInfo(coordinates.createRasterInfo(), time - currentTimeStep);
				}

				var x = Double.parseDouble(record.get("x"));
				var y = Double.parseDouble(record.get("y"));
				coordinates.add(x, y);
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		}

		// if we reach this point, there is only one timestep
		return new DataInfo(coordinates.createRasterInfo(), 1);
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
			bin.setValue(new DoubleRaster(rasterInfo.getBounds(), rasterInfo.getCellSize()));
		}

		return bin.getValue();
	}

	/**
	 * This collects x and y values in two arrays. Converts those into a AbstractRaster.Bounds object.
	 * Before doing so, the values in the arrays are sorted. This only works for CRS, where x and y values
	 * have the same signum
	 */
	@Getter
	private static class Coordinates {

		private final DoubleList xValues = new DoubleArrayList();
		private final DoubleList yValues = new DoubleArrayList();

		private void add(double x, double y) {
			xValues.add(x);
			yValues.add(y);
		}

		private RasterInfo createRasterInfo() {

			var xDistinct = xValues.stream()
					.distinct()
					.sorted()
					.collect(Collectors.toCollection(DoubleArrayList::new));

			var yDistinct = yValues.stream()
					.distinct()
					.sorted()
					.collect(Collectors.toCollection(DoubleArrayList::new));

			var minX = xDistinct.getDouble(0);
			var maxX = xDistinct.getDouble(xDistinct.size() - 1);
			var minY = yDistinct.getDouble(0);
			var maxY = yDistinct.getDouble(yDistinct.size() - 1);
			var cellSize = xDistinct.getDouble(1) - xDistinct.getDouble(0);

			return new RasterInfo(new DoubleRaster.Bounds(minX, minY, maxX, maxY), cellSize);
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