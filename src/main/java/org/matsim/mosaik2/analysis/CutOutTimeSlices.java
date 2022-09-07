package org.matsim.mosaik2.analysis;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collection;

@SuppressWarnings("FieldMayBeFinal")
@Log4j2
public class CutOutTimeSlices {

	@Parameter(names = "-xCol")
	private String xColumnName = "x";

	@Parameter(names = "-yCol")
	private String yColumnName = "y";

	@Parameter(names = "-timeCol")
	private String timeColumnName = "time";

	@Parameter(names = "-i", required = true)
	private String inputFile;

	@Parameter(names = "-o", required = true)
	private String outputFile;

	@Parameter(names = "-startTime")
	private double startTime = 86400;

	@Parameter(names = "-endTime")
	private double endTime = Double.MAX_VALUE;


	public static void main(String[] args) {

		var transformer = new CutOutTimeSlices();
		JCommander.newBuilder().addObject(transformer).build().parse(args);
		transformer.run();
	}

	private static void checkForHeaders(Collection<String> headers, String columnName) {
		headers.stream().filter(columnName::equals).findAny().orElseThrow(() -> new RuntimeException("'" + columnName + "' was not present as header in the source csv."));
	}

	private void run() {

		var inputPath = Paths.get(inputFile);
		var outputPath = Paths.get(outputFile);
		var counter = 0;

		try (var reader = Files.newBufferedReader(inputPath); var parser = CSVParser.parse(reader, CSVFormat.DEFAULT.withFirstRecordAsHeader())) {

			checkForHeaders(parser.getHeaderNames(), xColumnName);
			checkForHeaders(parser.getHeaderNames(), yColumnName);
			var headers = parser.getHeaderNames().toArray(new String[0]);

			try (var writer = Files.newBufferedWriter(outputPath); var printer = CSVFormat.DEFAULT.withHeader(headers).print(writer)) {
				for (var record : parser) {

					var values = record.toMap();
					var time = Double.parseDouble(values.get(timeColumnName));
					values.put(timeColumnName, Double.toString(time - startTime));

					if (time >= startTime && time <= endTime) {
						for (var column : headers) {
							printer.print(values.get(column));
						}
						printer.println();
					}

					counter++;
					if (counter % 100000 == 0) {
						log.info("Parsed #" + counter);
					}
				}
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
}