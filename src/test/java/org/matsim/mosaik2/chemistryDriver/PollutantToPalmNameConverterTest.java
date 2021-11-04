package org.matsim.mosaik2.chemistryDriver;

import org.junit.Test;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.contrib.analysis.time.TimeBinMap;
import org.matsim.contrib.emissions.Pollutant;

import java.util.Map;

import static org.junit.Assert.*;

public class PollutantToPalmNameConverterTest {

    @Test
    public void convertMap() {

        var id = Id.createLinkId("id");
        var emissions = Map.of(Pollutant.NO2, Map.of(id, 1.0), Pollutant.CO2_TOTAL, Map.of(id, 10.0));
        var converter = new PollutantToPalmNameConverter();

        var converted = converter.convert(emissions);

        // test converted keys
        assertTrue(converted.containsKey("NO2"));
        assertTrue(converted.containsKey("CO2"));

        // test values as well
        assertEquals(emissions.get(Pollutant.NO2).get(id), converted.get("NO2").get(id), 0.000001);
        assertEquals( emissions.get(Pollutant.CO2_TOTAL).get(id), converted.get("CO2").get(id),0.000001);
    }

    @Test
    public void convertMapUnknownPollutant() {

        var id = Id.createLinkId("id");
        var emissions = Map.of(Pollutant.NO2, Map.of(id, 1.0), Pollutant.CO2_TOTAL, Map.of(id, 10.0));
        var palmKey = "Some-random-string";
        var converter = new PollutantToPalmNameConverter(Map.of(Pollutant.NO2, palmKey));

        var converted = converter.convert(emissions);

        assertEquals(1, converted.size());
        assertTrue(converted.containsKey(palmKey));
        assertFalse(converted.containsKey("CO2"));

        assertEquals(emissions.get(Pollutant.NO2).get(id), converted.get(palmKey).get(id), 0.0000001);
    }

    @Test
    public void convertMultipleLinkIds() {

        var id = Id.createLinkId("id");
        var otherId = Id.createLinkId("other-id");
        var emissions = Map.of(Pollutant.NO2, Map.of(id, 1.0, otherId, 10.0));
        var converter = new PollutantToPalmNameConverter();

        var converted = converter.convert(emissions);

        assertTrue(converted.containsKey("NO2"));
        assertTrue(converted.get("NO2").containsKey(id));
        assertTrue(converted.get("NO2").containsKey(otherId));

        assertEquals(emissions.get(Pollutant.NO2).get(id), converted.get("NO2").get(id), 0.000001);
        assertEquals(emissions.get(Pollutant.NO2).get(otherId), converted.get("NO2").get(otherId), 0.000001);
    }

    @Test
    public void convertMergePm10() {

        var id = Id.createLinkId("id");
        var emissions = Map.of(Pollutant.PM, Map.of(id, 1.0), Pollutant.PM_non_exhaust, Map.of(id, 10.0));
        var converter = new PollutantToPalmNameConverter();

        var converted = converter.convert(emissions);

        // it is expected that pm and pm_non_exhaust are merged
        assertTrue(converted.containsKey("PM10"));
        assertEquals(converted.size(), 1);
        assertEquals( 11.0,converted.get("PM10").get(id), 0.00001);
    }

    @Test
    public void convertTimeBinMap() {

        var id = Id.createLinkId("id");
        var emissions = Map.of(Pollutant.NO2, Map.of(id, 1.0), Pollutant.CO2_TOTAL, Map.of(id, 10.0));
        TimeBinMap<Map<Pollutant, Map<Id<Link>, Double>>> timeBinMap = new TimeBinMap<>(10);
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
        assertEquals(emissions.get(Pollutant.NO2).get(id), convertedMap.get("NO2").get(id), 0.0000001);
        assertEquals(emissions.get(Pollutant.CO2_TOTAL).get(id), convertedMap.get("CO2").get(id), 0.0000001);
    }
}