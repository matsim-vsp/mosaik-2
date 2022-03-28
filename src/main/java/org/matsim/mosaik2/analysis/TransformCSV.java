package org.matsim.mosaik2.analysis;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.matsim.api.core.v01.Coord;
import org.matsim.core.utils.geometry.transformations.TransformationFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collection;

@Log4j2
public class TransformCSV {

    @Parameter(names = "-sCRS", required = true)
    private String sourceCrs;

    @Parameter(names = "-tCRS", required = true)
    private String targetCrs;

    @Parameter(names = "-xCol")
    private String xColumnName = "x";

    @Parameter(names = "-yCol")
    private String yColumnName = "y";

    @Parameter(names = "-i", required = true)
    private String inputFile;

    @Parameter(names = "-o", required = true)
    private String outputFile;

    public static void main(String[] args) {

        var transformer = new TransformCSV();
        JCommander.newBuilder().addObject(transformer).build().parse(args);
        transformer.transform();
    }

    private void transform() {

        var transformation = TransformationFactory.getCoordinateTransformation(sourceCrs, targetCrs);
        var inputPath = Paths.get(inputFile);
        var outputPath = Paths.get(outputFile);
        var counter = 0;

        try (var reader = Files.newBufferedReader(inputPath); var parser = CSVParser.parse(reader, CSVFormat.DEFAULT.withFirstRecordAsHeader())) {

            checkForHeaders(parser.getHeaderNames(), xColumnName);
            checkForHeaders(parser.getHeaderNames(), yColumnName);
            var headers = parser.getHeaderNames().toArray(new String[0]);

            try (var writer = Files.newBufferedWriter(outputPath); var printer = CSVFormat.DEFAULT.withHeader(headers).print(writer)) {

                for (CSVRecord record : parser) {

                    var values = record.toMap();

                    var x = Double.parseDouble(values.get(xColumnName));
                    var y = Double.parseDouble(values.get(yColumnName));
                    var transformed = transformation.transform(new Coord(x, y));
                    values.put(xColumnName, Double.toString(transformed.getX()));
                    values.put(yColumnName, Double.toString(transformed.getY()));

                    for (var column : headers) {
                        printer.print(values.get(column));
                    }
                    printer.println();
                    counter++;

                    if (counter % 10000 == 0) {
                        log.info("Parsed #" + counter);
                    }
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void checkForHeaders(Collection<String> headers, String columnName) {
        headers.stream().filter(columnName::equals).findAny().orElseThrow(() -> new RuntimeException("'" + columnName + "' was not present as header in the source csv."));
    }
}
