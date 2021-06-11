package org.matsim.mosaik2.chemistryDriver;

import it.unimi.dsi.fastutil.objects.Object2DoubleMap;
import it.unimi.dsi.fastutil.objects.Object2DoubleOpenHashMap;
import lombok.extern.log4j.Log4j2;
import org.junit.Test;
import org.matsim.contrib.analysis.time.TimeBinMap;

import java.util.Comparator;
import java.util.Map;
import java.util.stream.Collectors;

@Log4j2
public class CompareEmissionsWithBanzhaf {

    @Test
    public void test() {

        var matsimEmisisons = PalmChemistryInputReader.read("C:/Users/Janekdererste/Desktop/emisionRaster_berlin-comparison-10pct.nc");
        var banzhafEmissions = PalmChemistryInputReader.read("C:/Users/Janekdererste/Downloads/wetransfer-99a78b/gmdPP/gmdPP_chemistry.nc");

        var aggregateMatsimEmissions = aggregateEmissions(matsimEmisisons);
        var aggregateBanzhafEmissions = aggregateEmissions(banzhafEmissions);

        log.info("");
        log.info("comparing results left Matsim, right FU emissions");

        for (var entry : aggregateMatsimEmissions.entrySet()) {

            log.info(entry.getKey() + ":\t" + entry.getValue() + "\t\t" + aggregateBanzhafEmissions.get(entry.getKey()));
        }

        var timeSlicedMatsim = aggregateEmissionsIntoTimeSlices(matsimEmisisons);
        var timeSlicedBanzhaf = aggregateEmissionsIntoTimeSlices(banzhafEmissions);

        log.info("");
        log.info("comparing Matsim and FU emissions in 4hour slices. Left Matsim right FU emissions");

        timeSlicedMatsim.entrySet().stream()
                .filter(e -> timeSlicedBanzhaf.containsKey(e.getKey()))
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> log.info(entry.getKey() + ":\t" + entry.getValue() + "\t\t\t" + timeSlicedBanzhaf.get(entry.getKey())));
    }

    private Map<String, Double> aggregateEmissionsIntoTimeSlices(TimeBinMap<Map<String, Raster>> emissionRastersByTime) {

        Object2DoubleMap<String> result = new Object2DoubleOpenHashMap<>();

        int i = 0;
        for (TimeBinMap.TimeBin<Map<String, Raster>> timeBin : emissionRastersByTime.getTimeBins()) {

            for(var entry : timeBin.getValue().entrySet()) {
                var key = getKey(entry.getKey(), i);
                entry.getValue().forEachIndex((xi, yi, value) -> result.mergeDouble(key, value, Double::sum));
            }

            i++;
        }

        return result;
    }

    private String getKey(String pollutant, int index) {
        if (index < 4) return "00:00-04:00 " + pollutant;
        if (index < 8) return "04:00-08:00 " + pollutant;
        if (index < 12) return "08:00-12:00 " + pollutant;
        if (index < 16) return "12:00-16:00 " + pollutant;
        if (index < 20) return "16:00-20:00 " + pollutant;
        if (index < 24) return "20:00-24:00 " + pollutant;
        return "late";
    }



    private Map<String, Double> aggregateEmissions(TimeBinMap<Map<String, Raster>> emissionRastersByTime) {

        Object2DoubleMap<String> result = new Object2DoubleOpenHashMap<>();

        for (var bin : emissionRastersByTime.getTimeBins()) {
            for(var entry : bin.getValue().entrySet()) {
                entry.getValue().forEachIndex((xi, yi, value) -> result.mergeDouble(entry.getKey(), value, Double::sum));
            }
        }
        return result;
    }
}
