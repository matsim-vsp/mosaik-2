package org.matsim.mosaik2.analysis.run;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import it.unimi.dsi.fastutil.objects.Object2DoubleMap;
import it.unimi.dsi.fastutil.objects.Object2DoubleOpenHashMap;
import lombok.Getter;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.events.*;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.population.Person;
import org.matsim.contrib.analysis.time.TimeBinMap;
import org.matsim.core.events.EventsUtils;
import org.matsim.core.events.handler.BasicEventHandler;
import org.matsim.core.utils.collections.Tuple;
import org.matsim.vehicles.Vehicle;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class CompareTollChared {

    public static void main(String[] args) {

        var input = new InputArgs();
        JCommander.newBuilder().addObject(input).build().parse(args);

        var tables = Stream.iterate(0, i -> i + 1).parallel()
                .limit(input.eventsFile.size())
                .map(i -> Tuple.of(input.names.get(i), input.eventsFile.get(i)))
                .map(t -> {
                    var handler = new TollHandler();
                    var manager = EventsUtils.createEventsManager();
                    manager.addHandler(handler);
                    EventsUtils.readEvents(manager, t.getSecond());
                    return Tuple.of(t.getFirst(), handler.flatTollPerLink());
                })
                .collect(Collectors.toMap(Tuple::getFirst, Tuple::getSecond, (a, b) -> b, ConcurrentHashMap::new));

        tables.entrySet().parallelStream()
                .forEach(e -> writeTable(e.getValue(), Paths.get(input.outputFolder).resolve(e.getKey() + ".csv")));

        var amountPerRun = tables.entrySet().stream()
                .map(e -> {
                    var sum = e.getValue().stream().mapToDouble(t -> t.amount).sum();
                    return Tuple.of(e.getKey(), sum);
                })
                .toList();

        CSVUtils.writeTable(
                amountPerRun,
                Paths.get(input.outputFolder).resolve("sums.csv"),
                List.of("run-id", "toll-sum"),
                (printer, entry) -> CSVUtils.printRecord(printer, entry.getFirst(), entry.getSecond())
        );
    }

    private static void writeTable(Collection<TollPerLink> data, Path output) {
        CSVUtils.writeTable(data, output, List.of("time", "id", "amount"), (printer, tollPerLink) ->
                CSVUtils.printRecord(printer, tollPerLink.time, tollPerLink.linkId, tollPerLink.amount));
    }

    private static class TollHandler implements BasicEventHandler {

        private final Set<Id<Person>> transitDrivers = new HashSet<>();
        private final Map<Id<Person>, Id<Vehicle>> person2veh = new HashMap<>();
        private final Map<Id<Vehicle>, Id<Link>> veh2link = new HashMap<>();
        @Getter
        private final TimeBinMap<Object2DoubleMap<Id<Link>>> tollPerLink = new TimeBinMap<>(3600);

        Collection<TollPerLink> flatTollPerLink() {
            return getTollPerLink().getTimeBins().stream()
                    .flatMap(bin -> bin.getValue().object2DoubleEntrySet().stream()
                            .map(entry -> new TollPerLink(entry.getKey(), entry.getDoubleValue(), bin.getStartTime()))
                    )
                    .toList();
        }

        @Override
        public void handleEvent(Event event) {
            switch (event.getEventType()) {
                case PersonMoneyEvent.EVENT_TYPE -> handlePersonMoney((PersonMoneyEvent) event);
                case PersonEntersVehicleEvent.EVENT_TYPE -> handleEnterVehicle((PersonEntersVehicleEvent) event);
                case PersonLeavesVehicleEvent.EVENT_TYPE -> handleLeaveVehicle((PersonLeavesVehicleEvent) event);
                case LinkEnterEvent.EVENT_TYPE -> handleEnterLink((LinkEnterEvent) event);
                case LinkLeaveEvent.EVENT_TYPE -> handleLeaveLink((LinkLeaveEvent) event);
                case TransitDriverStartsEvent.EVENT_TYPE -> handleTransitDriverStarts((TransitDriverStartsEvent) event);
            }
        }

        private void handlePersonMoney(PersonMoneyEvent e) {

            var person = e.getPersonId();
            var veh = person2veh.get(person);
            var link = veh2link.get(veh);
            tollPerLink.getTimeBin(e.getTime()).computeIfAbsent(Object2DoubleOpenHashMap::new).mergeDouble(link, e.getAmount(), Double::sum);
        }

        private void handleEnterVehicle(PersonEntersVehicleEvent e) {

            if (transitDrivers.contains(e.getPersonId())) return;

            person2veh.put(e.getPersonId(), e.getVehicleId());
        }

        private void handleLeaveVehicle(PersonLeavesVehicleEvent e) {
            if (transitDrivers.contains(e.getPersonId())) return;

            person2veh.remove(e.getPersonId());
        }

        private void handleEnterLink(LinkEnterEvent e) {
            veh2link.put(e.getVehicleId(), e.getLinkId());
        }

        private void handleLeaveLink(LinkLeaveEvent e) {
            veh2link.remove(e.getVehicleId());
        }

        private void handleTransitDriverStarts(TransitDriverStartsEvent e) {
            transitDrivers.add(e.getDriverId());
        }
    }

    record TollPerLink(Id<Link> linkId, double amount, double time) {
    }

    private static class InputArgs {

        @Parameter(names = "-e")
        private List<String> eventsFile;

        @Parameter(names = "-n")
        private List<String> names;

        @Parameter(names = "-o")
        private String outputFolder;
    }
}
