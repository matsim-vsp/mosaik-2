package org.matsim.mosaik2.events;

import org.matsim.contrib.emissions.Pollutant;
import org.matsim.contrib.emissions.events.*;
import org.matsim.core.events.EventsUtils;
import org.matsim.core.utils.collections.Tuple;
import org.matsim.mosaik2.analysis.run.CSVUtils;

import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CompareAlgorithms {

    public static void main(String[] args) {

        var manager1 = EventsUtils.createEventsManager();
        var handler1 = new EmissionsCollector();
        manager1.addHandler(handler1);
        var file_and_manager1 = Tuple.of("/Users/janek/Documents/palm/berlin_with_geometry_attributes/output/berlin-with-geometry-attributes.output_only_emission_events.xml.gz", manager1);

        var manager2 = EventsUtils.createEventsManager();
        var handler2 = new EmissionsCollector();
        manager2.addHandler(handler2);
        var file_and_manager2 = Tuple.of("/Users/janek/Documents/palm/berlin_with_geometry_attributes/output/berlin-with-geometry-attributes.output_stop_and_go_only_emission_events.xml.gz", manager2);

        // read events files in parallel
        List.of(file_and_manager1, file_and_manager2).parallelStream()
                .forEach(tuple -> {

                    var reader = new EmissionEventsReader(tuple.getSecond());
                    reader.readFile(tuple.getFirst());
                });

        var diffs = handler1.events.entrySet().stream()
                .filter(event -> handler2.events.containsKey(event.getKey()))
                .map(event -> {
                    var event2 = handler2.events.get(event.getKey());
                    return new DiffEmissions(event.getKey(), event.getValue(), event2);
                }).toList();

        var headers = List.of("key", "nox_avg", "nox_sng");

        CSVUtils.writeTable(diffs, Paths.get("/Users/janek/Documents/palm/berlin_with_geometry_attributes/output/diff_algorithm_01.csv"),headers, (csvPrinter, diffEmissions) -> CSVUtils.printRecord(
                csvPrinter,
                diffEmissions.key,
                diffEmissions.emissions1.nox,
                diffEmissions.emissions2.nox
        ));
    }

    private static class EmissionsCollector implements WarmEmissionEventHandler, ColdEmissionEventHandler {

        Map<Key, Emissions> events = new HashMap<>();

        @Override
        public void handleEvent(ColdEmissionEvent event) {
            var key = new Key(event.getTime(), event.getLinkId().index(), event.getVehicleId().index());
            var emissions = new Emissions(
                    event.getColdEmissions().get(Pollutant.NOx)
            );
            events.put(key, emissions);
        }

        @Override
        public void handleEvent(WarmEmissionEvent event) {
            var key = new Key(event.getTime(), event.getLinkId().index(), event.getVehicleId().index());
            var emissions = new Emissions(
                    event.getWarmEmissions().get(Pollutant.NOx)
            );
            events.put(key, emissions);
        }
    }

    record Key(double time, int link_id, int veh_id) {}
    record Emissions(double nox){}
    record DiffEmissions(Key key, Emissions emissions1, Emissions emissions2){}
}
