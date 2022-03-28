package org.matsim.mosaik2.chemistryDriver;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import lombok.extern.log4j.Log4j2;
import org.matsim.contrib.analysis.time.TimeBinMap;
import org.matsim.mosaik2.raster.Raster;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Log4j2
public class MergeBackgroundAndTrafficEmissions {

    // the date of the stuttgart run. This date is chosen by the team of LUH
    private static final LocalDateTime dateOfStudy = LocalDateTime.of(2018, 7, 8, 0, 0);

    @Parameter(names = "-backgroundFile", required = true)
    private String backgroundFile;

    @Parameter(names = "-trafficFile", required = true)
    private String trafficFile;

    @Parameter(names = "-outputFile", required = true)
    private String outputFile;

    public static void main(String[] args) {

        var merger = new MergeBackgroundAndTrafficEmissions();
        JCommander.newBuilder().addObject(merger).build().parse(args);
        merger.merge();
    }

    void merge() {

        var backgroundEmissions = PalmChemistryInputReader.read(this.backgroundFile);
        var trafficEmissions = PalmChemistryInputReader.read(this.trafficFile);

        TimeBinMap<Map<String, Raster>> mergeResult = new TimeBinMap<>(backgroundEmissions.getBinSize(), 0);

        // merge the values
        for (TimeBinMap.TimeBin<Map<String, Raster>> backgroundEmissionsTimeBin : backgroundEmissions.getTimeBins()) {
            var trafficEmissionsTimeBin = trafficEmissions.getTimeBin(backgroundEmissionsTimeBin.getStartTime());
            var mergedBin = mergeResult.getTimeBin(backgroundEmissionsTimeBin.getStartTime());
            mergedBin.setValue(new HashMap<>());

            for (var pollutant : trafficEmissionsTimeBin.getValue().entrySet()) {
                if (!backgroundEmissionsTimeBin.getValue().containsKey(pollutant.getKey())) {
                    log.warn("could not find: " + pollutant.getKey() + " in background emissions");
                }
                else {
                    var backgroundRaster = backgroundEmissionsTimeBin.getValue().get(pollutant.getKey());
                    var trafficRaster = pollutant.getValue();
                    var mergedRaster = new Raster(backgroundRaster.getBounds(), backgroundRaster.getCellSize());

                    mergedRaster.setValueForEachIndex((xi, yi) -> {
                        var backgroundValue = backgroundRaster.getValueByIndex(xi, yi);
                        var trafficValue = trafficRaster.getValueByIndex(xi, yi);
                        return backgroundValue + trafficValue;
                    });

                    mergedBin.getValue().put(pollutant.getKey(), mergedRaster);
                }
            }
        }

        PalmChemistryInput2.writeNetCdfFile(outputFile, mergeResult, dateOfStudy);
    }
}
