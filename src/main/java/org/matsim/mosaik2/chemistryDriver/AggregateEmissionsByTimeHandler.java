package org.matsim.mosaik2.chemistryDriver;

import it.unimi.dsi.fastutil.objects.Object2DoubleMap;
import it.unimi.dsi.fastutil.objects.Object2DoubleOpenHashMap;
import lombok.Getter;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.events.Event;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.contrib.analysis.time.TimeBinMap;
import org.matsim.contrib.emissions.Pollutant;
import org.matsim.contrib.emissions.events.ColdEmissionEvent;
import org.matsim.contrib.emissions.events.WarmEmissionEvent;
import org.matsim.core.events.handler.BasicEventHandler;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class AggregateEmissionsByTimeHandler implements BasicEventHandler {

    @Getter
    private final TimeBinMap<Map<Pollutant, Object2DoubleMap<Id<Link>>>> timeBinMap;
    private final Network network;
    private final Set<Pollutant> pollutantsOfInterest;
    private final double scaleFactor;

    public AggregateEmissionsByTimeHandler(Network network, Set<Pollutant> pollutantsOfInterest, double timeBinSize, double scaleFactor) {
        this.network = network;
        this.pollutantsOfInterest = pollutantsOfInterest;
        timeBinMap = new TimeBinMap<>(timeBinSize);
        this.scaleFactor = scaleFactor;
    }

    @Override
    public void handleEvent(Event event) {

        if (WarmEmissionEvent.EVENT_TYPE.equals(event.getEventType())) {
            var warmEmissionEvent = (WarmEmissionEvent) event;
            handleEmissions(event.getTime(), warmEmissionEvent.getLinkId(), warmEmissionEvent.getWarmEmissions());
        } else if (ColdEmissionEvent.EVENT_TYPE.equals(event.getEventType())) {
            var coldEmissionEvent = (ColdEmissionEvent)event;
            handleEmissions(event.getTime(), coldEmissionEvent.getLinkId(), coldEmissionEvent.getColdEmissions());
        }
    }

    private void handleEmissions(double time, Id<Link> linkId, Map<Pollutant, Double> emissions) {

        if (network.getLinks().containsKey(linkId)) {
            var timeBin = timeBinMap.getTimeBin(time);
            if (!timeBin.hasValue()) {
                timeBin.setValue(new HashMap<>());
            }
            var emissionByPollutant = timeBin.getValue();

            emissions.entrySet().stream()
                    .filter(entry -> pollutantsOfInterest.contains(entry.getKey()))
                    .forEach(entry -> {
                        var linkEmissions = emissionByPollutant.computeIfAbsent(entry.getKey(), p -> new Object2DoubleOpenHashMap<>());
                        var value = entry.getValue() * scaleFactor;
                        linkEmissions.mergeDouble(linkId, value, Double::sum);
                    });
        }
    }
}
