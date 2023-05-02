package org.matsim.mosaik2.analysis.run;

import lombok.extern.log4j.Log4j2;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.zip.GZIPInputStream;

@Log4j2
public class CSVUtils {

    public static <I> void writeTable(Collection<I> data, Path path, Collection<String> headers, BiConsumer<CSVPrinter, I> printLine) {
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

    public static <T> Collection<T> readTable(Path path, Function<CSVRecord, T> record2Item) {

        log.info("read csv from: " + path);

        List<T> result = new ArrayList<>();
        var csvFormat = CSVFormat.DEFAULT.builder()
                .setSkipHeaderRecord(true)
                .setDelimiter(';')
                .setHeader()
                .build();
        try (var reader = createReader(path); var parser = CSVParser.parse(reader, csvFormat)) {

            for (var record : parser) {
                var tripRecord = record2Item.apply(record);
                result.add(tripRecord);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        log.info("Finsihed reading csv from: " + path + " parsed " + result.size() + " records");

        return result;
    }

    public static Reader createReader(Path path) throws IOException {

        if (path.getFileName().toString().endsWith(".gz")) {
            return new InputStreamReader(new GZIPInputStream(new BufferedInputStream(new FileInputStream(path.toFile()))));
        } else {
            return Files.newBufferedReader(path);
        }
    }

    public static void print(CSVPrinter printer, Object o) {
        try {
            printer.print(o);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void println(CSVPrinter printer) {
        try {
            printer.println();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void printRecord(CSVPrinter printer, Object... data) {
        try {
            printer.printRecord(data);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}


