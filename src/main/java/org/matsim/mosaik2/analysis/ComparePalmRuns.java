package org.matsim.mosaik2.analysis;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;
import org.matsim.mosaik2.analysis.run.CSVUtils;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

public class ComparePalmRuns {

    public static void main(String[] args) {

        var input = new InputArgs();
        JCommander.newBuilder().addObject(input).build().parse(args);

        var baseTable = readTable(Paths.get(input.base));
        var compareTable = readTable(Paths.get(input.compare));

        var diffMap = compareTable.stream()
                .collect(Collectors.toMap(RasterTileRecord::key, RasterTileRecord::values));
        for (RasterTileRecord record : baseTable) {
            // substract base from compare, so that we get positive values if more emissions in a raster tile
            // and negative values if less emissions.
            diffMap.merge(record.key(), record.values(), Values::substract);
        }

        CSVUtils.writeTable(diffMap.entrySet(), Paths.get(input.outputFile), List.of("time", "x", "y", "PM10", "NOx"), (p, r) -> {
            var nox = r.getValue().no2 + r.getValue().no;
            CSVUtils.printRecord(p, r.getKey().time, r.getKey().x, r.getKey().y, r.getValue().pm, nox);
        });
    }

    private static Collection<RasterTileRecord> readTable(Path file) {
        return CSVUtils.readTable(file, r -> new RasterTileRecord(
                new Key(Double.parseDouble(r.get("time")),
                        Double.parseDouble(r.get("x")),
                        Double.parseDouble(r.get("y"))),
                new Values(Double.parseDouble(r.get("PM10")),
                        Double.parseDouble(r.get("NO")),
                        Double.parseDouble(r.get("NO2")),
                        Double.parseDouble(r.get("O3")))
        ));
    }

    record RasterTileRecord(Key key, Values values) {
    }

    @EqualsAndHashCode(callSuper = false)
    @RequiredArgsConstructor
    private static class Key {
        final double time;
        final double x;
        final double y;
    }

    record Values(double pm, double no, double no2, double o3) {

        double nox() {
            return no2 + no;
        }

        static Values substract(Values base, Values compare) {
            return new Values(
                    base.pm - compare.pm,
                    base.no - compare.no,
                    base.no2 - compare.no2,
                    base.o3 - compare.o3
            );
        }
    }

    private static class InputArgs {

        @Parameter(names = "-b", required = true)
        private String base;

        @Parameter(names = "-c", required = true)
        private String compare;

        @Parameter(names = "-o", required = true)
        private String outputFile;
    }
}
