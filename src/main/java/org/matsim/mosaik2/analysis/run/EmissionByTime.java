package org.matsim.mosaik2.analysis.run;

import com.beust.jcommander.Parameter;
import it.unimi.dsi.fastutil.objects.Object2DoubleArrayMap;
import it.unimi.dsi.fastutil.objects.Object2DoubleMap;
import lombok.RequiredArgsConstructor;
import org.matsim.api.core.v01.events.Event;
import org.matsim.contrib.analysis.time.TimeBinMap;
import org.matsim.contrib.emissions.Pollutant;
import org.matsim.contrib.emissions.events.ColdEmissionEvent;
import org.matsim.contrib.emissions.events.EmissionEventsReader;
import org.matsim.contrib.emissions.events.WarmEmissionEvent;
import org.matsim.core.events.EventsUtils;
import org.matsim.core.events.handler.BasicEventHandler;
import org.matsim.mosaik2.chemistryDriver.PollutantToPalmNameConverter;

import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class EmissionByTime {

    public static void main(String[] args) {

        //   var input = new InputArgs();
        //     JCommander.newBuilder().addObject(input).build().parse(args);

        var converter = PollutantToPalmNameConverter.createForSpecies(List.of("NOx", "PM10"));
        var handler = new Handler(converter.getPollutants());
        var manager = EventsUtils.createEventsManager();
        var reader = new EmissionEventsReader(manager);
        manager.addHandler(handler);
        reader.readFile("C:\\Users\\janek\\Documents\\work\\palm\\berlin_with_geometry_attributes\\output\\berlin-with-geometry-attributes.output_only_emission_events.xml.gz");

        CSVUtils.writeTable(handler.summedEmissions.getTimeBins(), Paths.get("./").resolve("hourly-matsim-emissions.csv"), List.of("time", "species", "sum"), (p, b) -> {

            var time = b.getStartTime();
            for (var e : b.getValue().object2DoubleEntrySet()) {
                var species = e.getKey();
                var sum = e.getDoubleValue();
                CSVUtils.printRecord(p, time, species, sum);
            }
        });
    }

    @RequiredArgsConstructor
    private static class Handler implements BasicEventHandler {

        private final TimeBinMap<Object2DoubleMap<Pollutant>> summedEmissions = new TimeBinMap<>(3600);
        private final Set<Pollutant> species;

        @Override
        public void handleEvent(Event e) {
            switch (e.getEventType()) {
                case WarmEmissionEvent.EVENT_TYPE ->
                        handleEvent(e.getTime(), ((WarmEmissionEvent) e).getWarmEmissions());
                case ColdEmissionEvent.EVENT_TYPE ->
                        handleEvent(e.getTime(), ((ColdEmissionEvent) e).getColdEmissions());
            }
        }

        private void handleEvent(double time, Map<Pollutant, Double> emissions) {

            var bin = summedEmissions.getTimeBin(time);
            var map = bin.computeIfAbsent(Object2DoubleArrayMap::new);

            emissions.entrySet().stream()
                    .filter(e -> species.contains(e.getKey()))
                    .forEach(e -> {
                        // combine pm10 and pm10 non exhaust
                        var species = e.getKey() == Pollutant.PM_non_exhaust ? Pollutant.PM : e.getKey();
                        map.mergeDouble(species, e.getValue(), Double::sum);
                    });
        }
    }

    private static class InputArgs {

        @Parameter(names = "-e")
        private List<String> eventsFile;

        @Parameter(names = "-n")
        private List<String> names;

        private List<String> species;

        @Parameter(names = "-o")
        private String outputFolder;
    }
}
