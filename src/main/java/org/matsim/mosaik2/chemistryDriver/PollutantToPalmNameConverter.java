package org.matsim.mosaik2.chemistryDriver;

import it.unimi.dsi.fastutil.objects.Object2DoubleMap;
import lombok.RequiredArgsConstructor;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.contrib.analysis.time.TimeBinMap;
import org.matsim.contrib.emissions.Pollutant;
import org.matsim.core.utils.collections.Tuple;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@RequiredArgsConstructor
public class PollutantToPalmNameConverter {

    private final Map<Pollutant, String> pollutantToName;

    /**
     * Sets default values for emission names
     */
    public PollutantToPalmNameConverter() {
        pollutantToName = Map.of(
                Pollutant.NO2, "NO2",
                Pollutant.CO2_TOTAL, "CO2",
                Pollutant.PM, "PM10",
                Pollutant.PM_non_exhaust, "PM10",
                Pollutant.CO, "CO",
                Pollutant.NOx, "NOx"
        );
    }

    public static PollutantToPalmNameConverter createForSingleSpecies(String species) {

        var mapping = mapSpecies(species);
        return new PollutantToPalmNameConverter(mapping);
    }

    public static PollutantToPalmNameConverter createForSpecies(Collection<String> species) {

        var mapping = species.stream()
                .map(PollutantToPalmNameConverter::mapSpecies)
                .flatMap(speciesMapping -> speciesMapping.entrySet().stream())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        return new PollutantToPalmNameConverter(mapping);
    }

    private static Map<Pollutant, String> mapSpecies(String speciesName) {
        return switch (speciesName) {
            case "PM10" -> Map.of(
                    Pollutant.PM, "PM10",
                    Pollutant.PM_non_exhaust, "PM10"
            );
            case "NO2" -> Map.of(Pollutant.NO2, "NO2");
            case "NOx" -> Map.of(Pollutant.NO2, "NO2", Pollutant.NOx, "NOx");
            default -> throw new IllegalStateException("Unexpected value: " + speciesName);
        };
    }


    public Set<Pollutant> getPollutants() {
        return pollutantToName.keySet();
    }

    public String getPalmName(Pollutant matsimPollutant) {
        return pollutantToName.get(matsimPollutant);
    }

    Map<String, Map<Id<Link>, Double>> convert(Map<Pollutant, Map<Id<Link>, Double>> map) {
        return map.entrySet().stream()
                .filter(entry -> pollutantToName.containsKey(entry.getKey()))
                .map(entry -> Tuple.of(pollutantToName.get(entry.getKey()), entry.getValue()))
                .collect(Collectors.toMap(Tuple::getFirst, Tuple::getSecond, this::merge));
    }

    Map<String, Map<Id<Link>, Double>> convertWithDoubleMap(Map<Pollutant, Object2DoubleMap<Id<Link>>> map) {

        return map.entrySet().stream()
                .filter(entry -> pollutantToName.containsKey(entry.getKey()))
                .map(entry -> Tuple.of(pollutantToName.get(entry.getKey()), entry.getValue()))
                .collect(Collectors.toMap(Tuple::getFirst, Tuple::getSecond, this::merge));
    }

    Map<Id<Link>, Double> merge(Map<Id<Link>, Double> map1, Map<Id<Link>, Double> map2) {
        return Stream.concat(map1.entrySet().stream(), map2.entrySet().stream())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, Double::sum));
    }

    TimeBinMap<Map<String, Map<Id<Link>, Double>>> convert(TimeBinMap<Map<Pollutant, Map<Id<Link>, Double>>> timeBinMap) {

        TimeBinMap<Map<String, Map<Id<Link>, Double>>> result = new TimeBinMap<>(timeBinMap.getBinSize(), timeBinMap.getStartTime());

        for (var bin : timeBinMap.getTimeBins()) {

            var convertedMap = convert(bin.getValue());
            result.getTimeBin(bin.getStartTime()).setValue(convertedMap);
        }
        return result;
    }

    TimeBinMap<Map<String, Map<Id<Link>, Double>>> convertWithDoubleMap(TimeBinMap<Map<Pollutant, Object2DoubleMap<Id<Link>>>> timeBinMap) {

        TimeBinMap<Map<String, Map<Id<Link>, Double>>> result = new TimeBinMap<>(timeBinMap.getBinSize(), timeBinMap.getStartTime());

        for (var bin : timeBinMap.getTimeBins()) {

            var convertedMap = convertWithDoubleMap(bin.getValue());
            result.getTimeBin(bin.getStartTime()).setValue(convertedMap);
        }
        return result;
    }
}