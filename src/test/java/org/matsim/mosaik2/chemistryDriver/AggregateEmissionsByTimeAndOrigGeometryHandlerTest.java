package org.matsim.mosaik2.chemistryDriver;

import org.junit.Test;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.contrib.emissions.Pollutant;
import org.matsim.contrib.emissions.events.ColdEmissionEvent;
import org.matsim.contrib.emissions.events.WarmEmissionEvent;
import org.matsim.mosaik2.utils.TestUtils;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.*;

public class AggregateEmissionsByTimeAndOrigGeometryHandlerTest {

    @Test
    public void addSingleWarmEmissionEvent() {

        var network = TestUtils.createSingleLinkNetwork(new Coord(0,0), new Coord(80, 0), List.of(new Coord(0, 10), new Coord(80, 10)));
        var linkMap = NetworkUnsimplifier.unsimplifyNetwork(network);
        var linkId = network.getLinks().values().iterator().next().getId();
        var pollutants = Set.of(Pollutant.NOx);
        var emissions = Map.of(Pollutant.NOx, 1.0);
        var handler = new AggregateEmissionsByTimeAndOrigGeometryHandler(linkMap, pollutants, 10, 1.0);
        var event = new WarmEmissionEvent(1, linkId, Id.createVehicleId("vehicle"), emissions);

        handler.handleEvent(event);

        var timeBinMap = handler.getTimeBinMap();
        assertEquals(1, timeBinMap.getTimeBins().size());

        for (Pollutant pollutant : pollutants) {
            var linkEmissions = timeBinMap.getTimeBin(1).getValue().get(pollutant);

            for (var segment : linkMap.get(linkId) ) {

                var linkEmission = linkEmissions.get(segment.getId());
                var expectedEmission = segment.getLength() / network.getLinks().get(linkId).getLength() * emissions.get(pollutant);
                assertEquals(expectedEmission, linkEmission, 0.0001);
            }
        }
    }
    @Test
    public void addSingleColdEmissionEvent() {
        var network = TestUtils.createSingleLinkNetwork(new Coord(0,0), new Coord(80, 0), List.of(new Coord(0, 10), new Coord(80, 10)));
        var linkMap = NetworkUnsimplifier.unsimplifyNetwork(network);
        var linkId = network.getLinks().values().iterator().next().getId();
        var pollutants = Set.of(Pollutant.NOx);
        var emissions = Map.of(Pollutant.NOx, 1.0);
        var handler = new AggregateEmissionsByTimeAndOrigGeometryHandler(linkMap, pollutants, 10, 1.0);
        var event = new ColdEmissionEvent(1, linkId, Id.createVehicleId("vehicle"), emissions);

        handler.handleEvent(event);

        var timeBinMap = handler.getTimeBinMap();
        assertEquals(1, timeBinMap.getTimeBins().size());

        for (Pollutant pollutant : pollutants) {
            var linkEmissions = timeBinMap.getTimeBin(1).getValue().get(pollutant);

            for (var segment : linkMap.get(linkId) ) {

                var linkEmission = linkEmissions.get(segment.getId());
                var expectedEmission = segment.getLength() / network.getLinks().get(linkId).getLength() * emissions.get(pollutant);
                assertEquals(expectedEmission, linkEmission, 0.0001);
            }
        }
    }

    @Test
    public void addSingleEventWithScaleFactor() {

        var network = TestUtils.createSingleLinkNetwork(new Coord(0,0), new Coord(80, 0), List.of(new Coord(0, 10), new Coord(80, 10)));
        var linkMap = NetworkUnsimplifier.unsimplifyNetwork(network);
        var linkId = network.getLinks().values().iterator().next().getId();
        var pollutants = Set.of(Pollutant.NOx);
        var emissions = Map.of(Pollutant.NOx, 1.0);
        var scaleFactor = 10.0;
        var handler = new AggregateEmissionsByTimeAndOrigGeometryHandler(linkMap, pollutants, 10, scaleFactor);
        var event = new WarmEmissionEvent(1, linkId, Id.createVehicleId("vehicle"), emissions);

        handler.handleEvent(event);

        var timeBinMap = handler.getTimeBinMap();
        assertEquals(1, timeBinMap.getTimeBins().size());

        for (Pollutant pollutant : pollutants) {
            var linkEmissions = timeBinMap.getTimeBin(1).getValue().get(pollutant);

            for (var segment : linkMap.get(linkId) ) {

                var linkEmission = linkEmissions.get(segment.getId());
                var expectedEmission = segment.getLength() / network.getLinks().get(linkId).getLength() * scaleFactor * emissions.get(pollutant);
                assertEquals(expectedEmission, linkEmission, 0.0001);
            }
        }
    }

    @Test
    public void addMultipleEmissionEvents() {

        var network = TestUtils.createSingleLinkNetwork(new Coord(0,0), new Coord(80, 0), List.of(new Coord(0, 10), new Coord(80, 10)));
        var linkMap = NetworkUnsimplifier.unsimplifyNetwork(network);
        var linkId = network.getLinks().values().iterator().next().getId();
        var pollutants = Set.of(Pollutant.NOx);
        var emissions = Map.of(Pollutant.NOx, 1.0);
        var handler = new AggregateEmissionsByTimeAndOrigGeometryHandler(linkMap, pollutants, 10, 1.0);

        handler.handleEvent(new WarmEmissionEvent(1, linkId, Id.createVehicleId("vehicle"), emissions));
        handler.handleEvent(new WarmEmissionEvent(1, linkId, Id.createVehicleId("vehicle"), emissions));
        handler.handleEvent(new WarmEmissionEvent(2, linkId, Id.createVehicleId("vehicle"), emissions));
        handler.handleEvent(new WarmEmissionEvent(2, linkId, Id.createVehicleId("vehicle"), emissions));
        handler.handleEvent(new WarmEmissionEvent(3, linkId, Id.createVehicleId("vehicle"), emissions));

        handler.handleEvent(new WarmEmissionEvent(15, linkId, Id.createVehicleId("vehicle"), emissions));
        handler.handleEvent(new WarmEmissionEvent(16, linkId, Id.createVehicleId("vehicle"), emissions));
        handler.handleEvent(new WarmEmissionEvent(16, linkId, Id.createVehicleId("vehicle"), emissions));

        var timeBinMap = handler.getTimeBinMap();
        assertEquals(2, timeBinMap.getTimeBins().size());

        for (var timeBin : timeBinMap.getTimeBins()) {

            // not very elegant but will do the job
            var numberOfEvents = timeBin.getStartTime() == 0 ? 5 : 3;
            for (Pollutant pollutant : pollutants) {
                var linkEmissions = timeBin.getValue().get(pollutant);

                for (var segment : linkMap.get(linkId) ) {

                    var linkEmission = linkEmissions.get(segment.getId());
                    var expectedEmission = segment.getLength() / network.getLinks().get(linkId).getLength() * numberOfEvents * emissions.get(pollutant);
                    assertEquals(expectedEmission, linkEmission, 0.0001);
                }
            }
        }
    }

    @Test
    public void addMultipleEmissionEventsForDifferentLinks() {

        var network = TestUtils.createRandomNetwork(10, 100, 100);
        var linkMap = NetworkUnsimplifier.unsimplifyNetwork(network);
        var pollutants = Set.of(Pollutant.NOx);
        var emissions = Map.of(Pollutant.NOx, 1.0);
        var handler = new AggregateEmissionsByTimeAndOrigGeometryHandler(linkMap, pollutants, 10, 1.0);

        for (var link : network.getLinks().values()){
            handler.handleEvent(new WarmEmissionEvent(1, link.getId(), Id.createVehicleId("bla"), emissions));
            handler.handleEvent(new WarmEmissionEvent(2, link.getId(), Id.createVehicleId("bla"), emissions));
        }

        var timeBin = handler.getTimeBinMap().getTimeBin(1);

        for (var pollutant : pollutants) {
            var linkEmissions = timeBin.getValue().get(pollutant);
            for (var link : network.getLinks().values()) {
                for (var segment : linkMap.get(link.getId())) {
                    var linkEmission = linkEmissions.get(segment.getId());
                    var expectedEmission = segment.getLength() / link.getLength() * 2 *  emissions.get(pollutant); // times 2 because 2 events per link
                    assertEquals(expectedEmission, linkEmission, 0.0001);
                }
            }
        }
    }
}