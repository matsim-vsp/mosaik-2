package org.matsim.mosaik2.chemistryDriver;

import org.junit.Test;
import org.matsim.contrib.analysis.time.TimeBinMap;
import org.matsim.contrib.emissions.Pollutant;

import java.util.Map;

import static org.junit.Assert.*;

public class PollutantToPalmNameConverterTest {

    @Test
    public void convertMap() {

        var emissions = Map.of(Pollutant.NO2, 1.0, Pollutant.CO2_TOTAL, 10.0);
        var converter = new PollutantToPalmNameConverter();

        var converted = converter.convert(emissions);

        assertTrue(converted.containsKey("NO2"));
        assertTrue(converted.containsKey("CO2"));

        assertEquals(emissions.get(Pollutant.NO2), converted.get("NO2"), 0.0000001);
        assertEquals(emissions.get(Pollutant.CO2_TOTAL), converted.get("CO2"), 0.0000001);
    }

    @Test
    public void convertMapUnknownPollutant() {

        var emissions = Map.of(Pollutant.NO2, 1.0, Pollutant.CO2_TOTAL, 10.0);
        var palmKey = "Some-random-string";
        var converter = new PollutantToPalmNameConverter(Map.of(Pollutant.NO2, palmKey));

        var converted = converter.convert(emissions);

        assertEquals(1, converted.size());
        assertTrue(converted.containsKey(palmKey));
        assertFalse(converted.containsKey("CO2"));

        assertEquals(emissions.get(Pollutant.NO2), converted.get(palmKey), 0.0000001);
    }

    @Test
    public void convertTimeBinMap() {

        var emissions = Map.of(Pollutant.NO2, 1.0, Pollutant.CO2_TOTAL, 10.0);
        TimeBinMap<Map<Pollutant, Double>> timeBinMap = new TimeBinMap<>(10);
        timeBinMap.getTimeBin(1).setValue(emissions);
        var converter = new PollutantToPalmNameConverter();

        var convertedTimeBinMap = converter.convert(timeBinMap);

        assertEquals(1, convertedTimeBinMap.getTimeBins().size());
        var timeBin = convertedTimeBinMap.getTimeBin(1);
        assertTrue(timeBin.hasValue());
        var convertedMap = timeBin.getValue();
        assertEquals(emissions.size(), convertedMap.size());
        assertTrue(convertedMap.containsKey("NO2"));
        assertTrue(convertedMap.containsKey("CO2"));
        assertEquals(emissions.get(Pollutant.NO2), convertedMap.get("NO2"), 0.0000001);
        assertEquals(emissions.get(Pollutant.CO2_TOTAL), convertedMap.get("CO2"), 0.0000001);
    }
}