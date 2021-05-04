package org.matsim.mosaik2.chemistryDriver;

import org.junit.Test;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.contrib.emissions.Pollutant;
import org.matsim.contrib.emissions.events.ColdEmissionEvent;
import org.matsim.contrib.emissions.events.WarmEmissionEvent;
import org.matsim.mosaik2.utils.TestUtils;

import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.*;

public class AggregateEmissionsByTimeHandlerTest {

    @Test
    public void addSingleWarmEmissionEvent() {

        var network = TestUtils.createRandomNetwork(1, 100, 100);
        var linkId = network.getLinks().values().iterator().next().getId();
        var pollutants = Set.of(Pollutant.NOx);
        var emissions = Map.of(Pollutant.NOx, 1.0);

        var handler = new AggregateEmissionsByTimeHandler(network, pollutants, 10, 1.0);
        var event = new WarmEmissionEvent(1, linkId, Id.createVehicleId("vehicle"), emissions);

        handler.handleEvent(event);

        var timeBinMap = handler.getTimeBinMap();

        assertEquals(1, timeBinMap.getTimeBins().size());
        assertEquals(emissions.get(Pollutant.NOx), timeBinMap.getTimeBin(1).getValue().get(Pollutant.NOx).get(linkId), 0.000001);
    }

    @Test
    public void addSingleColdEmissionEvent() {
        var network = TestUtils.createRandomNetwork(1, 100, 100);
        var linkId = network.getLinks().values().iterator().next().getId();
        var pollutants = Set.of(Pollutant.NOx);
        var emissions = Map.of(Pollutant.NOx, 1.0);

        var handler = new AggregateEmissionsByTimeHandler(network, pollutants, 10, 1.0);
        var event = new ColdEmissionEvent(1, linkId, Id.createVehicleId("vehicle"), emissions);

        handler.handleEvent(event);

        var timeBinMap = handler.getTimeBinMap();

        assertEquals(1, timeBinMap.getTimeBins().size());
        assertEquals(emissions.get(Pollutant.NOx), timeBinMap.getTimeBin(1).getValue().get(Pollutant.NOx).get(linkId), .00001);
    }

    @Test
    public void addSingleEventWithScaleFactor() {

        var network = TestUtils.createRandomNetwork(1, 100, 100);
        var linkId = network.getLinks().values().iterator().next().getId();
        var pollutants = Set.of(Pollutant.NOx);
        var emissions = Map.of(Pollutant.NOx, 1.0);
        var scaleFactor = 10.0;
        var handler = new AggregateEmissionsByTimeHandler(network, pollutants, 10, scaleFactor);

        handler.handleEvent(new ColdEmissionEvent(1, linkId, Id.createVehicleId("vehicle"), emissions));

        var timeBinMap = handler.getTimeBinMap();

        assertEquals(1, timeBinMap.getTimeBins().size());
        assertEquals(emissions.get(Pollutant.NOx) * scaleFactor, timeBinMap.getTimeBin(1).getValue().get(Pollutant.NOx).get(linkId), 0.00001);
    }

    @Test
    public void addMultipleEmissionEvents() {

        var network = TestUtils.createRandomNetwork(1, 100, 100);
        var linkId = network.getLinks().values().iterator().next().getId();
        var pollutants = Set.of(Pollutant.NOx);
        var emissions = Map.of(Pollutant.NOx, 1.0, Pollutant.NO2, 20.0);

        var handler = new AggregateEmissionsByTimeHandler(network, pollutants, 10, 1.0);

        handler.handleEvent(new ColdEmissionEvent(1, linkId, Id.createVehicleId("bla"), emissions));
        handler.handleEvent(new ColdEmissionEvent(2, linkId, Id.createVehicleId("bla"), emissions));
        handler.handleEvent(new WarmEmissionEvent(2, linkId, Id.createVehicleId("bla"), emissions));
        handler.handleEvent(new ColdEmissionEvent(3, linkId, Id.createVehicleId("bla"), emissions));
        handler.handleEvent(new WarmEmissionEvent(3, linkId, Id.createVehicleId("bla"), emissions));

        handler.handleEvent(new ColdEmissionEvent(15, linkId, Id.createVehicleId("bla"), emissions));
        handler.handleEvent(new ColdEmissionEvent(15, linkId, Id.createVehicleId("bla"), emissions));
        handler.handleEvent(new WarmEmissionEvent(16, linkId, Id.createVehicleId("bla"), emissions));

        var timeBinMap = handler.getTimeBinMap();

        assertEquals(2, timeBinMap.getTimeBins().size());

            var firstTimeBin = timeBinMap.getTimeBin(1);
            assertEquals(emissions.get(Pollutant.NOx) * 5, firstTimeBin.getValue().get(Pollutant.NOx).get(linkId), 0.0005);
            assertNull(firstTimeBin.getValue().get(Pollutant.NO2)); // since this is not in the pollutants set it should be ignored

            var secondTimeBin = timeBinMap.getTimeBin(11);
            assertEquals(emissions.get(Pollutant.NOx) * 3, secondTimeBin.getValue().get(Pollutant.NOx).get(linkId), 0.0005);
    }

    @Test
    public void addMultipleEmissionEventsForDifferentLinks() {

        var network = TestUtils.createRandomNetwork(10, 100, 100);
        var pollutants = Set.of(Pollutant.NOx, Pollutant.NO2);
        var emissions = Map.of(Pollutant.NOx, 1.0, Pollutant.NO2, 20.0);

        var handler = new AggregateEmissionsByTimeHandler(network, pollutants, 10,1.0);

        for (var link : network.getLinks().values()) {

            handler.handleEvent(new WarmEmissionEvent(1, link.getId(), Id.createVehicleId("bla"), emissions));
            handler.handleEvent(new WarmEmissionEvent(2, link.getId(), Id.createVehicleId("bla"), emissions));
        }

        var timeBin = handler.getTimeBinMap().getTimeBin(1);

        for (var link: network.getLinks().values()) {

            for (Pollutant pollutant : pollutants) {
                var emissionByPollutant = timeBin.getValue().get(pollutant);
                assertEquals(emissions.get(pollutant) * 2, emissionByPollutant.get(link.getId()), 0.00005);
            }
        }
    }
}