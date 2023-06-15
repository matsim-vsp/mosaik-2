package org.matsim.mosaik2.analysis.run;

import com.beust.jcommander.Parameter;
import it.unimi.dsi.fastutil.objects.Object2DoubleArrayMap;
import it.unimi.dsi.fastutil.objects.Object2DoubleMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.prep.PreparedGeometry;
import org.locationtech.jts.geom.prep.PreparedGeometryFactory;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.events.Event;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.contrib.analysis.time.TimeBinMap;
import org.matsim.contrib.emissions.Pollutant;
import org.matsim.contrib.emissions.events.ColdEmissionEvent;
import org.matsim.contrib.emissions.events.EmissionEventsReader;
import org.matsim.contrib.emissions.events.WarmEmissionEvent;
import org.matsim.core.events.EventsUtils;
import org.matsim.core.events.handler.BasicEventHandler;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.utils.geometry.geotools.MGC;
import org.matsim.core.utils.gis.ShapeFileReader;
import org.matsim.mosaik2.chemistryDriver.PollutantToPalmNameConverter;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Log4j2
public class EmissionByTime {

    public static void main(String[] args) {

        //   var input = new InputArgs();
        //     JCommander.newBuilder().addObject(input).build().parse(args);

        var prepFact = new PreparedGeometryFactory();
        var converter = PollutantToPalmNameConverter.createForSpecies(List.of("NOx", "PM10"));
        var network = NetworkUtils.readNetwork("C:\\Users\\Janekdererste\\Documents\\work\\berlin-roadpricing\\output-rp-time-berlin-100\\berlin-with-geometries-rp-time-berlin-100.output_network.xml.gz");
        var filter = ShapeFileReader.getAllFeatures("C:\\Users\\Janekdererste\\Documents\\work\\berlin-roadpricing\\berlin-epsg25833.shp").stream()
                .limit(1)
                .map(feature -> (Geometry) feature.getDefaultGeometry())
                .map(prepFact::create)
                .toList()
                .get(0);
        var handler = new Handler(converter.getPollutants(), network, filter);
        var manager = EventsUtils.createEventsManager();
        var reader = new EmissionEventsReader(manager);
        manager.addHandler(handler);
        reader.readFile("C:\\Users\\Janekdererste\\Documents\\work\\berlin-roadpricing\\output-rp-time-berlin-100\\berlin-with-geometries-rp-time-berlin-100.output_only_emission_events.xml.gz");

        Path root = Paths.get("C:\\Users\\Janekdererste\\Documents\\work\\berlin-roadpricing\\output-rp-time-berlin-100\\");
        CSVUtils.writeTable(handler.summedEmissions.getTimeBins(), root.resolve("hourly-matsim-emissions.csv"), List.of("time", "species", "sum"), (p, b) -> {

            var time = b.getStartTime();
            for (var e : b.getValue().object2DoubleEntrySet()) {
                var species = e.getKey();
                var sum = e.getDoubleValue();
                CSVUtils.printRecord(p, time, species, sum);
            }
        });

        CSVUtils.writeTable(handler.summedEmissionsInFilter.getTimeBins(), root.resolve("hourly-matsim-emissions-in-filter.csv"), List.of("time", "species", "sum"), (p, b) -> {

            var time = b.getStartTime();
            for (var e : b.getValue().object2DoubleEntrySet()) {
                var species = e.getKey();
                var sum = e.getDoubleValue();
                CSVUtils.printRecord(p, time, species, sum);
            }
        });

        CSVUtils.writeTable(handler.emissionPerMeter.getTimeBins(), root.resolve("hourly-emissions-per-meter.csv"), List.of("time", "link", "species", "value"), (p, b) -> {

            var time = b.getStartTime();
            for (var s : b.getValue().entrySet()) {

                var species = s.getKey();
                for (var l : s.getValue().entrySet()) {
                    var link = l.getKey();
                    var value = l.getValue().getAvg();
                    CSVUtils.printRecord(p, time, link, species, value);
                }
            }
        });
    }

    @RequiredArgsConstructor
    private static class Handler implements BasicEventHandler {

        private final TimeBinMap<Object2DoubleMap<Pollutant>> summedEmissions = new TimeBinMap<>(3600);
        private final TimeBinMap<Object2DoubleMap<Pollutant>> summedEmissionsInFilter = new TimeBinMap<>(3600);
        private final TimeBinMap<Map<Pollutant, Map<Id<Link>, LinkCollector>>> emissionPerMeter = new TimeBinMap<>(3600);
        private final Set<Pollutant> species;
        private final Network network;
        private final PreparedGeometry filter;

        private int counter = 0;

        @Override
        public void handleEvent(Event e) {
            switch (e.getEventType()) {
                case WarmEmissionEvent.EVENT_TYPE ->
                        handleEvent(e.getTime(), ((WarmEmissionEvent) e).getLinkId(), ((WarmEmissionEvent) e).getWarmEmissions());
                case ColdEmissionEvent.EVENT_TYPE ->
                        handleEvent(e.getTime(), ((ColdEmissionEvent) e).getLinkId(), ((ColdEmissionEvent) e).getColdEmissions());
            }
        }

        private void handleEvent(double time, Id<Link> linkId, Map<Pollutant, Double> emissions) {

            counter++;
            var sumBin = summedEmissions.getTimeBin(time);
            var sumMap = sumBin.computeIfAbsent(Object2DoubleArrayMap::new);
            var filterSumBin = summedEmissionsInFilter.getTimeBin(time);
            var filterSumMap = filterSumBin.computeIfAbsent(Object2DoubleArrayMap::new);
            var meterBin = emissionPerMeter.getTimeBin(time);
            var meterMap = meterBin.computeIfAbsent(HashMap::new);

            emissions.entrySet().stream()
                    .filter(e -> species.contains(e.getKey()))
                    .forEach(e -> {
                        // combine pm10 and pm10 non exhaust
                        var species = e.getKey() == Pollutant.PM_non_exhaust ? Pollutant.PM : e.getKey();
                        sumMap.mergeDouble(species, e.getValue(), Double::sum);

                        var link = network.getLinks().get(linkId);
                        var linkMap = meterMap.computeIfAbsent(species, s -> new HashMap<>());
                        var collector = linkMap.computeIfAbsent(linkId, id -> new LinkCollector());
                        collector.add(link.getLength(), e.getValue());

                        if (filter.contains(MGC.coord2Point(link.getCoord()))) {
                            filterSumMap.mergeDouble(species, e.getValue(), Double::sum);
                        }

                        if (counter % 100000 == 0)
                            log.info(species + " " + e.getValue() + " " + link.getLength() + " " + collector.getAvg());
                    });
        }
    }

    private static class LinkCollector {
        double accLength;
        double accEmission;

        void add(double length, double value) {
            this.accLength += length;
            this.accEmission += value;
        }

        double getAvg() {
            return accEmission / accLength;
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