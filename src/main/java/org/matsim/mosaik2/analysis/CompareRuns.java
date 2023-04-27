package org.matsim.mosaik2.analysis;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import it.unimi.dsi.fastutil.objects.Object2DoubleArrayMap;
import it.unimi.dsi.fastutil.objects.Object2DoubleMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.prep.PreparedGeometry;
import org.locationtech.jts.geom.prep.PreparedGeometryFactory;
import org.matsim.core.utils.collections.Tuple;
import org.matsim.core.utils.gis.ShapeFileReader;
import org.matsim.mosaik2.QuadTree;
import org.matsim.mosaik2.palm.PalmStaticDriverReader;
import org.opengis.referencing.FactoryException;
import ucar.nc2.NetcdfFiles;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;

@Log4j2
public class CompareRuns {

	public static void main(String[] args) throws FactoryException {

		var input = new InputArgs();
		JCommander.newBuilder().addObject(input).build().parse(args);


		var shapeFilter = createSpatialFilter(input);

		var tables = Stream.iterate(0, i -> i + 1).parallel()
				.limit(input.files.size())
				.map(i -> Tuple.of(input.names.get(i), Paths.get(input.files.get(i))))
				.map(entry -> Tuple.of(entry.getFirst(), readTable(entry.getSecond())))
				.collect(Collectors.toMap(Tuple::getFirst, Tuple::getSecond, (a, b) -> b, ConcurrentHashMap::new));

		log.info("Computing modal splits");
		var modalSplits = tables.entrySet().parallelStream()
				.map(entry -> Tuple.of(entry.getKey(), entry.getValue().modalShare(shapeFilter)))
				.collect(Collectors.toMap(Tuple::getFirst, Tuple::getSecond, (a, b) -> b, ConcurrentHashMap::new));

		var modes = new ArrayList<>(modalSplits.values().iterator().next().keySet().stream().toList());

		var headerValues = new ArrayList<>(modes);
		headerValues.add(0, "name");
		var outputPath = Paths.get(input.outputFolder).resolve("modal-split.csv");

		writeTable(modalSplits.entrySet(), outputPath, headerValues, (printer, entry) -> {
			var name = entry.getKey();
			print(printer, name);

			for (var mode : modes) {
				var value = entry.getValue().getDouble(mode);
				print(printer, value);
			}
			println(printer);
		});
	}

	private static TripsTable readTable(Path path) {

		log.info("read csv from: " + path);

		List<TripRecord> result = new ArrayList<>();
		var csvFormat = CSVFormat.DEFAULT.builder()
				.setSkipHeaderRecord(true)
				.setDelimiter(';')
				.setHeader()
				.build();
		try (var reader = createReader(path); var parser = CSVParser.parse(reader, csvFormat)) {

			for (var record : parser) {
				var tripRecord = TripRecord.fromCsvRecord(record);
				result.add(tripRecord);
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		}

		log.info("Finsihed reading csv from: " + path + " parsed " + result.size() + " records");

		return new TripsTable(result);
	}

	private static <I> void writeTable(Collection<I> data, Path path, Collection<String> headers, BiConsumer<CSVPrinter, I> printLine) {
		var format = CSVFormat.DEFAULT.builder()
				.setHeader(headers.toArray(new String[0]))
				.setSkipHeaderRecord(false)
				.build();

		log.info("Writing to: " + path);
		try (var writer = Files.newBufferedWriter(path); var printer = new CSVPrinter(writer, format)) {
			for (var item : data) {
				printLine.accept(printer, item);
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private static void print(CSVPrinter printer, Object o) {
		try {
			printer.print(o);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private static void println(CSVPrinter printer) {
		try {
			printer.println();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}


	private static Reader createReader(Path path) throws IOException {

		if (path.getFileName().toString().endsWith(".gz")) {
			return new InputStreamReader(new GZIPInputStream(new BufferedInputStream(new FileInputStream(path.toFile()))));
		} else {
			return Files.newBufferedReader(path);
		}
	}

	private static PreparedGeometry createSpatialFilter(InputArgs inputArgs) {

		var prepFact = new PreparedGeometryFactory();
		if (!inputArgs.shpFile.isBlank()) {
			return ShapeFileReader.getAllFeatures(inputArgs.shpFile).stream()
					.limit(1)
					.map(feature -> (Geometry) feature.getDefaultGeometry())
					.map(prepFact::create)
					.toList()
					.get(0);
		} else if (!inputArgs.staticDriver.isBlank()) {
			try (var file = NetcdfFiles.open(inputArgs.staticDriver)) {
				var bbox = PalmStaticDriverReader.createTarget(file).getBounds().toGeometry();
				return prepFact.create(bbox);
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}
		throw new RuntimeException("Either shp file or static file must be specified.");
	}

	static class InputArgs {

		@Parameter(names = "--f", required = true)
		private List<String> files;

		@Parameter(names = "--n", required = true)
		private List<String> names;

		@Parameter(names = "--shp")
		private String shpFile = "";

		@Parameter(names = "--static")
		private String staticDriver = "";

		@Parameter(names = "--of")
		private String outputFolder;
	}

	@RequiredArgsConstructor
	private static class TripRecord {

		private final String mainMode;
		private final double travelledDistance;
		private final double euclideanDistance;
		private final LineString line;

		public static TripRecord fromCsvRecord(CSVRecord csvRecord) {
			var mainMode = csvRecord.get("main_mode");
			var travelledDistance = Double.parseDouble(csvRecord.get("traveled_distance"));
			var euclideanDistance = Double.parseDouble(csvRecord.get("euclidean_distance"));
			var startX = Double.parseDouble(csvRecord.get("start_x"));
			var startY = Double.parseDouble(csvRecord.get("start_y"));
			var endX = Double.parseDouble(csvRecord.get("end_x"));
			var endY = Double.parseDouble(csvRecord.get("end_x"));
			var line = new GeometryFactory().createLineString(new Coordinate[]{
					new Coordinate(startX, startY), new Coordinate(endX, endY)
			});

			return new TripRecord(mainMode, travelledDistance, euclideanDistance, line);
		}
	}

	private static class TripsTable {

		private final Collection<TripRecord> records;
		private final QuadTree<TripRecord> spatialIndex;

		TripsTable(Collection<TripRecord> records) {
			this.records = records;
			spatialIndex = new QuadTree<>();
			for (var record : records) {
				spatialIndex.insert(record.line, record);
			}
		}

		public Object2DoubleMap<String> modalShare(PreparedGeometry filter) {
			return spatialIndex.intersects(filter).stream()
					.collect(Collectors.toMap(r -> r.mainMode, r -> 1., Double::sum, Object2DoubleArrayMap::new));
		}
	}

}