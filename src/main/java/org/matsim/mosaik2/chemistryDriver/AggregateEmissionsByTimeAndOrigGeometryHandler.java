package org.matsim.mosaik2.chemistryDriver;

import it.unimi.dsi.fastutil.objects.Object2DoubleOpenHashMap;
import lombok.Getter;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.events.Event;
import org.matsim.api.core.v01.network.Link;
import org.matsim.contrib.analysis.time.TimeBinMap;
import org.matsim.contrib.emissions.Pollutant;
import org.matsim.contrib.emissions.events.ColdEmissionEvent;
import org.matsim.contrib.emissions.events.WarmEmissionEvent;
import org.matsim.core.events.handler.BasicEventHandler;
import org.matsim.core.utils.collections.Tuple;

import java.util.*;

import static org.matsim.mosaik2.chemistryDriver.NetworkUnsimplifier.LENGTH_FRACTION_KEY;

public class AggregateEmissionsByTimeAndOrigGeometryHandler implements BasicEventHandler {

    @Getter
    private final TimeBinMap<Map<Pollutant, Map<Id<Link>, Double>>> timeBinMap;
    @Getter
    private final Set<Id<Link>> linksWithEmissions = new HashSet<>();
    private final Map<Id<Link>, List<Link>> links;
    private final Set<Pollutant> pollutantsOfInterest;
    private final double scaleFactor;

    public AggregateEmissionsByTimeAndOrigGeometryHandler(Map<Id<Link>, List<Link>> links, Set<Pollutant> pollutantsOfInterest, double timeBinSize, double scaleFactor) {
        this.timeBinMap = new TimeBinMap<>(timeBinSize);
        this.links = links;
        this.pollutantsOfInterest = pollutantsOfInterest;
        this.scaleFactor = scaleFactor;
    }

    @Override
    public void handleEvent(Event event) {

        if (WarmEmissionEvent.EVENT_TYPE.equals(event.getEventType())) {
            var warmEmissionEvent = (WarmEmissionEvent) event;
            handleEmissions(event.getTime(), warmEmissionEvent.getLinkId(), warmEmissionEvent.getWarmEmissions());
        } else if (ColdEmissionEvent.EVENT_TYPE.equals(event.getEventType())) {
            var coldEmissionEvent = (ColdEmissionEvent) event;
            handleEmissions(event.getTime(), coldEmissionEvent.getLinkId(), coldEmissionEvent.getColdEmissions());
        }
    }

    private void handleEmissions(double time, Id<Link> linkId, Map<Pollutant, Double> emissions) {

        if (!links.containsKey(linkId)) return;

        var timeBin = timeBinMap.getTimeBin(time);
        if (!timeBin.hasValue()) {
            timeBin.setValue(new HashMap<>());
        }

        var emissionByPollutant = timeBin.getValue();
        var segments = links.get(linkId);

        emissions.entrySet().stream()
                .filter(entry -> pollutantsOfInterest.contains(entry.getKey()))
                .filter(entry -> entry.getValue() > 0.0)
                .flatMap(entry -> segments.stream().map(segment -> Tuple.of(segment, entry)))
                .forEach(tuple -> {
                    var key = tuple.getSecond().getKey();
                    var emissionFromEvent = tuple.getSecond().getValue();
                    var segment = tuple.getFirst();
                    var lengthFraction = (double) tuple.getFirst().getAttributes().getAttribute(LENGTH_FRACTION_KEY);
                    var value = emissionFromEvent * scaleFactor * lengthFraction;
                    var linkEmissions = emissionByPollutant.computeIfAbsent(key, p -> new Object2DoubleOpenHashMap<>());
                    linkEmissions.merge(segment.getId(), value, Double::sum);
                    linksWithEmissions.add(segment.getId());
                });
    }
}
