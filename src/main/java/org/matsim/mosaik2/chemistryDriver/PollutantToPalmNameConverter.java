package org.matsim.mosaik2.chemistryDriver;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.matsim.contrib.analysis.time.TimeBinMap;
import org.matsim.contrib.emissions.Pollutant;
import org.matsim.core.utils.collections.Tuple;
import visad.browser.Convert;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RequiredArgsConstructor
public class PollutantToPalmNameConverter {

    private final Map<Pollutant, String> pollutantToName;

    Set<Pollutant> getPollutants() {
        return pollutantToName.keySet();
    }

    String getPalmName(Pollutant matsimPollutant) {
        return pollutantToName.get(matsimPollutant);
    }

    /**
     * Sets default values for emission names
     */
    PollutantToPalmNameConverter() {
        pollutantToName = Map.of(
                Pollutant.NO2, "NO2",
                Pollutant.CO2_TOTAL, "CO2",
                Pollutant.PM, "PM10",
                Pollutant.CO, "CO",
                Pollutant.NOx, "NOx"
        );
    }

    <T> Map<String, T> convert(Map<Pollutant, T> map) {
        return map.entrySet().stream()
                .filter(entry -> pollutantToName.containsKey(entry.getKey()))
                .map(entry -> Tuple.of(pollutantToName.get(entry.getKey()), entry.getValue()))
                .collect(Collectors.toMap(Tuple::getFirst, Tuple::getSecond));
    }

    <T> TimeBinMap<Map<String, T>> convert(TimeBinMap<Map<Pollutant, T>> timeBinMap) {

        TimeBinMap<Map<String, T>> result = new TimeBinMap<>(timeBinMap.getBinSize(), timeBinMap.getStartTime());

        for (var bin: timeBinMap.getTimeBins()) {

            var convertedMap = convert(bin.getValue());
            result.getTimeBin(bin.getStartTime()).setValue(convertedMap);
        }
        return result;
    }
}
