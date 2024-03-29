package org.matsim.mosaik2.analysis;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.matsim.mosaik2.palm.XYTValueCsvData;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Log4j2
public class MergeRasters {

    private final List<Path> files;
    private final Path output;

    public static void main(String[] args) {

        var input = new InputArgs();
        JCommander.newBuilder().addObject(input).build().parse(args);

        var paths = input.files.stream()
                .map(Paths::get)
                .collect(Collectors.toList());
        new MergeRasters(paths, Paths.get(input.output)).run();
    }

    private void run() {

        var first = this.files.get(0);
        var firstInfo = XYTValueCsvData.readDataInfo(first);
        var firstTimeSeries = XYTValueCsvData.read(first, firstInfo);

        for (int i = 1; i < files.size(); i++) {

            var path = files.get(i);
            var data = XYTValueCsvData.read(path);

            for (var bin : firstTimeSeries.getTimeBins()) {
                if (data.getTimeBin(bin.getStartTime()).hasValue()) {

                    log.info("Merging time bin [" + bin.getStartTime() + ", " + (bin.getStartTime() + data.getBinSize()) + "]");

                    var rasterToMerge = data.getTimeBin(bin.getStartTime()).getValue();
                    var rasterToMergeInto = bin.getValue();

                    rasterToMerge.forEachCoordinate(rasterToMergeInto::setValueForCoord);
                }
            }
        }

        XYTValueCsvData.write(output, firstTimeSeries);
    }

    private static class InputArgs {

        @Parameter(names = "-f", required = true)
        private List<String> files;

        @Parameter(names = "-output", required = true)
        private String output;
    }
}