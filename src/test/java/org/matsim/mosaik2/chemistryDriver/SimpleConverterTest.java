package org.matsim.mosaik2.chemistryDriver;

import com.google.common.util.concurrent.AtomicDouble;
import org.junit.Rule;
import org.junit.Test;
import org.matsim.api.core.v01.events.Event;
import org.matsim.contrib.analysis.time.TimeBinMap;
import org.matsim.contrib.emissions.Pollutant;
import org.matsim.contrib.emissions.events.ColdEmissionEvent;
import org.matsim.contrib.emissions.events.EmissionEventsReader;
import org.matsim.contrib.emissions.events.WarmEmissionEvent;
import org.matsim.core.events.EventsUtils;
import org.matsim.core.events.handler.BasicEventHandler;
import org.matsim.mosaik2.raster.Raster;
import org.matsim.testcases.MatsimTestUtils;

import java.util.Map;

import static org.junit.Assert.*;

public class SimpleConverterTest {

    @Rule
    public MatsimTestUtils testUtils = new MatsimTestUtils();

    @Test
    public void singleLink() {

        var testFilePath = testUtils.getOutputDirectory() + "output.nc";
        SimpleConverter.Props props = new SimpleConverter.Props(
                testUtils.getClassInputDirectory() + "network.xml",
                testUtils.getClassInputDirectory() + "emissionEvents.xml",
                testFilePath,
                10,
                new Raster.Bounds(0, -10, 100, 10)
        );

        SimpleConverter.write(props);

        var readResult = PalmChemistryInputReader.read(testFilePath, 0, Integer.MAX_VALUE);

        assertEquals(3, readResult.getTimeBins().size());
        assertEquals(3600, readResult.getBinSize(), 0.000001);
        assertEquals( 0, readResult.getStartTime(), 0.00001);

        for (var timeBin : readResult.getTimeBins()) {

            // the events file we parse contains only NO2 and one event with a value of 1000.0 for each hour
            timeBin.getValue().get("NO2").forEachCoordinate((x, y, value) -> {
                if (y == 0.0 && x >= 0 && x < 100) {
                    assertEquals(1.0, value, 0.00000001);
                } else {
                    assertEquals(0.0, value, 0.00000001);
                }
            });
        }
    }

    @Test
    public void compareMasses() {

        var testFilePath = testUtils.getOutputDirectory() + "output.nc";
        var emissionEventsFile = testUtils.getClassInputDirectory() + "emissionEvents.xml";
        SimpleConverter.Props props = new SimpleConverter.Props(
                testUtils.getClassInputDirectory() + "network.xml",
                emissionEventsFile,
                testFilePath,
                10,
                new Raster.Bounds(0, -10, 100, 10
                )
        );

        SimpleConverter.write(props);

        var readResult = PalmChemistryInputReader.read(testFilePath);
        var handler = new NO2MassCollector();
        var manager = EventsUtils.createEventsManager();
        manager.addHandler(handler);
        new EmissionEventsReader(manager).readFile(emissionEventsFile);

        double readSum = readResult.getTimeBins().stream()
                .map(TimeBinMap.TimeBin::getValue)
                .flatMap(map -> map.values().stream())
                .mapToDouble(raster -> {
                    AtomicDouble mass = new AtomicDouble();
                    raster.forEachIndex((xi, yi, value) -> mass.addAndGet(value * raster.getCellSize() * raster.getCellSize())); // add polltion value times area
                    return mass.get();
                })
                .sum();

        assertEquals(handler.collectedNO2, readSum, 0.000000001);
    }

    private static class NO2MassCollector implements BasicEventHandler {

        private double collectedNO2 = 0;

        @Override
        public void handleEvent(Event event) {
            if (WarmEmissionEvent.EVENT_TYPE.equals(event.getEventType())) {
                var warmEmissionEvent = (WarmEmissionEvent) event;
                handleEmissions(warmEmissionEvent.getWarmEmissions());
            } else if (ColdEmissionEvent.EVENT_TYPE.equals(event.getEventType())) {
                var coldEmissionEvent = (ColdEmissionEvent)event;
                handleEmissions(coldEmissionEvent.getColdEmissions());
            }
        }

        private void handleEmissions(Map<Pollutant, Double> emissions) {
            collectedNO2  += emissions.get(Pollutant.NO2);
        }
    }

}