package org.matsim.mosaik2.analysis;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.csv.CSVParser;
import org.matsim.mosaik2.DoubleToDoubleFunction;
import org.matsim.mosaik2.Utils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;

@SuppressWarnings("FieldMayBeFinal")
@Log4j2
@AllArgsConstructor
public class ConvertPalmCsvOutputToSparse {

	private Path inputFile;
	private Path outputFile;
	private String xColumnName = "x";
	private String yColumnName = "y";
	private String timeColumnName = "time";
	private String valueColumnName = "value";
	private double startTime = 86400;
	private double endTime = Double.MAX_VALUE;
	private double minValue = 0.0;
	private DoubleToDoubleFunction converter;

	private ConvertPalmCsvOutputToSparse(InputArgs args) {
		this(
				Paths.get(args.inputFile),
				Paths.get(args.outputFile),
				args.xColumnName,
				args.yColumnName,
				args.timeColumnName,
				args.valueColumnName,
				args.startTime,
				args.endTime,
				args.minValue,
				value -> value
		);
	}

	ConvertPalmCsvOutputToSparse(Path inputFile, Path outputFile, DoubleToDoubleFunction valueConverter) {
		this.inputFile = inputFile;
		this.outputFile = outputFile;
		this.converter = valueConverter;
	}

	public static void main(String[] args) {

		var input = new InputArgs();
		JCommander.newBuilder().addObject(input).build().parse(args);
		new ConvertPalmCsvOutputToSparse(input).run();
	}

	private static void checkForHeaders(Collection<String> headers, String columnName) {
		headers.stream().filter(columnName::equals).findAny().orElseThrow(() -> new RuntimeException("'" + columnName + "' was not present as header in the source csv."));
	}

	void run() {

		var lastTime = -1.;

		try (var reader = Files.newBufferedReader(inputFile); var parser = CSVParser.parse(reader, Utils.createReadFormat())) {

			checkForHeaders(parser.getHeaderNames(), xColumnName);
			checkForHeaders(parser.getHeaderNames(), yColumnName);
			checkForHeaders(parser.getHeaderNames(), valueColumnName);
			var headers = parser.getHeaderNames().toArray(new String[0]);

			try (var writer = Files.newBufferedWriter(outputFile); var printer = Utils.createWriteFormat(headers).print(writer)) {
				for (var record : parser) {

					var values = record.toMap();

					// adjust time according to offset
					var time = Double.parseDouble(values.get(timeColumnName));
					values.put(timeColumnName, Double.toString(time - startTime));

					// parse value
					var parsedValue = Double.parseDouble(values.get(valueColumnName));
					var convertedValues = this.converter.applyAsDouble(parsedValue);
					values.put(valueColumnName, Double.toString(convertedValues));

					if (convertedValues >= minValue && time >= startTime && time <= endTime) {
						for (var column : headers) {
							printer.print(values.get(column));
						}
						printer.println();
						if (lastTime != time) {
							lastTime = time;
							log.info("Parsed and printed time: " + time);
						}
					} else if (lastTime != time) {
						lastTime = time;
						log.info("Parsed time: " + time);
					}
				}
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private static class InputArgs {
		@Parameter(names = "-xCol")
		private String xColumnName = "x";

		@Parameter(names = "-yCol")
		private String yColumnName = "y";

		@Parameter(names = "-timeCol")
		private String timeColumnName = "time";

		@Parameter(names = "-valueCol")
		private String valueColumnName = "value";

		@Parameter(names = "-i", required = true)
		private String inputFile;

		@Parameter(names = "-o", required = true)
		private String outputFile;

		@Parameter(names = "-startTime")
		private double startTime = 86400;

		@Parameter(names = "-endTime")
		private double endTime = Double.MAX_VALUE;

		@Parameter(names = "-minValue")
		private double minValue = 0.0;
	}
}