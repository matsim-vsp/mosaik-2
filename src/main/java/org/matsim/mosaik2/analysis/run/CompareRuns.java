package org.matsim.mosaik2.analysis.run;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import it.unimi.dsi.fastutil.objects.Object2DoubleArrayMap;
import it.unimi.dsi.fastutil.objects.Object2DoubleMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.csv.CSVRecord;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.prep.PreparedGeometry;
import org.locationtech.jts.geom.prep.PreparedGeometryFactory;
import org.matsim.contrib.analysis.time.TimeBinMap;
import org.matsim.core.utils.collections.Tuple;
import org.matsim.core.utils.gis.ShapeFileReader;
import org.matsim.core.utils.misc.Time;
import org.matsim.mosaik2.QuadTree;
import org.matsim.mosaik2.palm.PalmStaticDriverReader;
import org.opengis.referencing.FactoryException;
import ucar.nc2.NetcdfFiles;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.matsim.mosaik2.analysis.run.CSVUtils.*;

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
                .map(entry -> Tuple.of(entry.getKey(), entry.getValue().modalSplit(shapeFilter)))
                .collect(Collectors.toMap(Tuple::getFirst, Tuple::getSecond, (a, b) -> b, ConcurrentHashMap::new));

        var sums = modalSplits.entrySet().parallelStream()
                .map(entry -> {
                    var sum = entry.getValue().values().stream()
                            .mapToDouble(v -> v)
                            .sum();
                    return Tuple.of(entry.getKey(), sum);
                })
                .collect(Collectors.toMap(Tuple::getFirst, Tuple::getSecond));

        var modalShares = modalSplits.entrySet().parallelStream()
                .map(entry -> {
                    var shares = entry.getValue().object2DoubleEntrySet().stream()
                            .map(de -> Tuple.of(de.getKey(), de.getDoubleValue() / sums.get(entry.getKey()).doubleValue()))
                            .collect(Collectors.toMap(Tuple::getFirst, Tuple::getSecond));
                    return Tuple.of(entry.getKey(), shares);
                })
                .collect(Collectors.toMap(Tuple::getFirst, Tuple::getSecond));

        var modes = new ArrayList<>(modalSplits.values().iterator().next().keySet().stream().sorted().toList());

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

        writeTable(modalShares.entrySet(), Paths.get(input.outputFolder).resolve("modal-share.csv"), headerValues, (p, e) -> {
            var name = e.getKey();
            print(p, name);

            for (var mode : modes) {
                Double value = e.getValue().get(mode);
                print(p, value);
            }
            println(p);

        });

        var splitByHour = tables.entrySet().parallelStream()
                .map(entry -> Tuple.of(entry.getKey(), entry.getValue().modalSplitByHour(shapeFilter)))
                .collect(Collectors.toMap(Tuple::getFirst, Tuple::getSecond));

        var shareByHour = tables.entrySet().parallelStream()
                .map(entry -> Tuple.of(entry.getKey(), entry.getValue().modalShareByHour(shapeFilter)))
                .collect(Collectors.toMap(Tuple::getFirst, Tuple::getSecond));

        var tidyHeaders = List.of("name", "time", "mode", "value");
        writeTable(splitByHour.entrySet(), Paths.get(input.outputFolder).resolve("modal-split-hour.csv"), tidyHeaders, (p, e) -> {

            var name = e.getKey();

            for (var bin : e.getValue().getTimeBins()) {
                var time = bin.getStartTime();
                for (var mode : modes) {
                    var value = bin.getValue().getDouble(mode);
                    printRecord(p, name, time, mode, value);
                }
            }
        });

        writeTable(shareByHour.entrySet(), Paths.get(input.outputFolder).resolve("modal-share-hour.csv"), tidyHeaders, (p, e) -> {

            var name = e.getKey();

            for (var bin : e.getValue().getTimeBins()) {
                var time = bin.getStartTime();
                for (var mode : modes) {
                    var value = bin.getValue().getDouble(mode);
                    printRecord(p, name, time, mode, value);
                }
            }
        });
    }

    private static TripsTable readTable(Path path) {

        var parsedRecords = CSVUtils.readTable(path, TripRecord::fromCsvRecord);
        return new TripsTable(parsedRecords);
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
        } else {
            return null;
        }
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
        private final double depTime;
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
            var depTime = Time.parseTime(csvRecord.get("dep_time"));

            return new TripRecord(mainMode, travelledDistance, euclideanDistance, depTime, line);
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

        public Object2DoubleMap<String> modalSplit(PreparedGeometry filter) {
            if (filter == null) {
                return records.stream()
                        .collect(Collectors.toMap(r -> r.mainMode, r -> 10.0, Double::sum, Object2DoubleArrayMap::new));
            }

            return spatialIndex.intersects(filter).stream()
                    .collect(Collectors.toMap(r -> r.mainMode, r -> 10., Double::sum, Object2DoubleArrayMap::new));
        }

        public TimeBinMap<Object2DoubleMap<String>> modalSplitByHour(PreparedGeometry filter) {

            var r = filter == null ? records : spatialIndex.intersects(filter);

            TimeBinMap<Object2DoubleMap<String>> result = new TimeBinMap<>(3600);

            for (var record : r) {
                var bin = result.getTimeBin(record.depTime);
                var map = bin.computeIfAbsent(Object2DoubleArrayMap::new);
                map.mergeDouble(record.mainMode, 10, Double::sum);
            }

            return result;
        }

        public TimeBinMap<Object2DoubleMap<String>> modalShareByHour(PreparedGeometry filter) {

            var split = modalSplitByHour(filter);

            TimeBinMap<Object2DoubleMap<String>> result = new TimeBinMap<>(3600);

            for (var splitBin : split.getTimeBins()) {

                var sum = splitBin.getValue().values().stream().mapToDouble(v -> v).sum();
                var bin = result.getTimeBin(splitBin.getStartTime());
                var map = bin.computeIfAbsent(Object2DoubleArrayMap::new);
                for (var entry : splitBin.getValue().object2DoubleEntrySet()) {
                    map.put(entry.getKey(), entry.getDoubleValue() / sum);
                }
            }

            return result;
        }
    }

}