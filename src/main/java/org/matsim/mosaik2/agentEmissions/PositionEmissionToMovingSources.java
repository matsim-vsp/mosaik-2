package org.matsim.mosaik2.agentEmissions;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.java.Log;
import lombok.extern.log4j.Log4j2;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.events.Event;
import org.matsim.api.core.v01.events.VehicleEntersTrafficEvent;
import org.matsim.api.core.v01.events.VehicleLeavesTrafficEvent;
import org.matsim.contrib.emissions.Pollutant;
import org.matsim.contrib.emissions.PositionEmissionsModule;
import org.matsim.core.events.EventsUtils;
import org.matsim.core.events.MatsimEventsReader;
import org.matsim.core.events.algorithms.EventWriter;
import org.matsim.core.events.handler.BasicEventHandler;
import org.matsim.vehicles.Vehicle;
import ucar.ma2.*;
import ucar.nc2.Attribute;
import ucar.nc2.Dimension;
import ucar.nc2.write.NetcdfFileFormat;
import ucar.nc2.write.NetcdfFormatWriter;

import java.io.IOException;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Log4j2
public class PositionEmissionToMovingSources {

    public static void main(String[] args) throws InvalidRangeException, IOException {

        log.info("Starting PositionEmissionToMovingSources version 0.0.1");
        var inArgs = new InputArgs();
        JCommander.newBuilder().addObject(inArgs).build().parse(args);

        run(inArgs);
    }

    private static void run(InputArgs inputArgs) throws IOException {

        // split this into two methods so that the metadata collector goes out of scope.
        var netcdfWriter = readFirstPass(inputArgs);
        readSecondPass(inputArgs, netcdfWriter);
    }

    private static NetCDFWriter readFirstPass(InputArgs inputArgs) throws IOException {
        var manager = EventsUtils.createEventsManager();
        var metadata = new MetadataCollector();
        manager.addHandler(metadata);
        var reader = new MatsimEventsReader(manager);
        reader.addCustomEventMapper(PositionEmissionsModule.PositionEmissionEvent.EVENT_TYPE, PositionEmissionsModule.PositionEmissionEvent.getEventMapper());
        reader.readFile(inputArgs.positionEmissionEventsFile);

        metadata.observedVehicles.values().removeIf(List::isEmpty);
        metadata.orig2Num.values().removeIf(v -> !metadata.observedVehicles.containsKey(v));

        var localDate = LocalDate.parse(inputArgs.date);
        var localTime = LocalTime.of(0, 0, 0);
        var localDateTime = LocalDateTime.of(localDate, localTime);

        var zoneId = ZoneId.of(inputArgs.zone);
        var zoneOffset = zoneId.getRules().getOffset(localDateTime);
        var utcDateTime = localDateTime.atOffset(zoneOffset);
        return new NetCDFWriter(inputArgs.netCdfOutput, metadata.observedVehicles, metadata.orig2Num, inputArgs.species, utcDateTime);
    }

    private static void readSecondPass(InputArgs inputArgs, NetCDFWriter writer) {
        var manager = EventsUtils.createEventsManager();
        manager.addHandler(writer);
        var reader = new MatsimEventsReader(manager);
        reader.addCustomEventMapper(PositionEmissionsModule.PositionEmissionEvent.EVENT_TYPE, PositionEmissionsModule.PositionEmissionEvent.getEventMapper());
        reader.readFile(inputArgs.positionEmissionEventsFile);
        writer.closeFile();
    }

    private enum ObservationType {Position, VehicleEnter, VehicleLeaves}

    private record VehicleObservation(ObservationType type, int time) {
    }

    private static class MetadataCollector implements BasicEventHandler {

        private final Map<Id<Vehicle>, List<VehicleObservation>> observedVehicles = new HashMap<>();
        // initially, we thought it might make sense to keep track of original matsim vehicle ids, and map them to
        // the numbered ids in palm. We are currently not using this feature. Keep this here, so that we remember
        // that we already had this idea.
        //private final Map<Id<Vehicle>, Id<Vehicle>> numToOriginalId = new HashMap<>();
        private final Map<Id<Vehicle>, Id<Vehicle>> orig2Num = new HashMap<>();

        @Override
        public void handleEvent(Event event) {

            if (event instanceof PositionEmissionsModule.PositionEmissionEvent e) {

                var numId = orig2Num.get(e.getVehicleId());
                observedVehicles.get(numId).add(new VehicleObservation(ObservationType.Position, (int) e.getTime()));
            } else if (event instanceof VehicleEntersTrafficEvent e) {
                // netcdf treats / and . as special characters. Just use clean numbers as id.
                var numId = orig2Num.computeIfAbsent(e.getVehicleId(), k -> Id.createVehicleId(observedVehicles.size()));
                //numToOriginalId.putIfAbsent(numId, e.getVehicleId());
                observedVehicles.computeIfAbsent(numId, k -> new ArrayList<>()).add(new VehicleObservation(ObservationType.VehicleEnter, (int) e.getTime()));
            } else if (event instanceof VehicleLeavesTrafficEvent e) {
                var numId = orig2Num.get(e.getVehicleId());
                var trajectory = observedVehicles.get(numId);

                // remove the trajectory, in case we have just recorded the vehicle enters traffic event so far.
                if (trajectory.get(trajectory.size() - 1).type.equals(ObservationType.VehicleEnter)) {
                    trajectory.remove(trajectory.size() - 1);
                } else {
                    trajectory.add(new VehicleObservation(ObservationType.VehicleLeaves, (int) e.getTime()));
                }
            }
        }
    }

    @Log4j2
    private static class NetCDFWriter implements BasicEventHandler, EventWriter {

        private static final int FIELD_LENGTH = 29;
        // This parameter adjusts onto how many raster tiles the emission is distributed. 1 -> Point sources, everything
        // is released at a single point. 9 -> The position of the source and the neighbouring raster tiles. This would
        // require smoothing the emissions onto surrounding tiles with a gauss blur for example. We have 1 now, because
        // it is simple. If required we can increase to another number later, because having all the emissions released in
        // one raster tile might lead to numerical artefacts in PALM.
        private static final int NVSCRS_LENGTH = 1;

        private final NetcdfFormatWriter internalWriter;

        private final Map<Id<Vehicle>, VehicleCache> vehCache = new HashMap<>();

        private final Object2IntMap<String> speciesMapping;

        private final Map<Id<Vehicle>, Id<Vehicle>> orig2num;

        NetCDFWriter(String outputFile, Map<Id<Vehicle>, List<VehicleObservation>> observedVehicles, Map<Id<Vehicle>, Id<Vehicle>> orig2num, Collection<String> species, OffsetDateTime utcDate) throws IOException {

            this.orig2num = orig2num;

            var builder = NetcdfFormatWriter.builder()
                    .setFormat(NetcdfFileFormat.NETCDF3)
                    .setLocation(outputFile)
                    .addAttribute(new Attribute("author", "Janek Laudan"))
                    .addAttribute(new Attribute("lod", "2"))
                    .addAttribute(new Attribute("num_emission_path", (double) observedVehicles.size()));

            reserveVehicleData(builder, observedVehicles, species);
            log.info("Calling builder.build()");
            this.internalWriter = builder.build();

            writeMetadata(observedVehicles, species, utcDate);
            this.speciesMapping = initSpeciesMapping(species);
        }

        private Object2IntMap<String> initSpeciesMapping(Collection<String> species) {

            var result = new Object2IntOpenHashMap<String>();
            var counter = 0;
            for (var s : species) {
                result.putIfAbsent(s, counter);
                counter++;
            }

            return result;
        }

        private static void reserveVehicleData(NetcdfFormatWriter.Builder builder, Map<Id<Vehicle>, List<VehicleObservation>> observedVehicles, Collection<String> species) {

            log.info("Create NetCDF file and reserve space for all vehicles observed in first pass of events file.");
            var fieldLengthDim = builder.addDimension(Dimension.builder("field_length", FIELD_LENGTH).build());

            var counter = 0;

            for (var entry : observedVehicles.entrySet()) {

                if (entry.getValue().isEmpty()) {
                    continue; // don't reserve space for empty trajectories
                }

                if (counter % observedVehicles.size() == 1000) {
                    log.info("Reserving vehicle data: " + counter + "/" + observedVehicles.size());
                }

                var id = entry.getKey().toString();
                // create dimensions
                var nspeciesDim = builder.addDimension(Dimension.builder("nspecies" + id, species.size()).build());
                var ntimeDim = builder.addDimension(Dimension.builder("ntime" + id, entry.getValue().size()).build());
                var nvsrcDim = builder.addDimension(Dimension.builder("nvsrc" + id, NVSCRS_LENGTH).build()); // set this to 1 for now

                // create variables
                builder.addVariable("nspecies" + id, DataType.INT, List.of(nspeciesDim));
                builder.addVariable("ntime" + id, DataType.INT, List.of(ntimeDim));
                builder.addVariable("nvsrc" + id, DataType.INT, List.of(nvsrcDim));
                builder.addVariable("species" + id, DataType.CHAR, List.of(nspeciesDim, fieldLengthDim));
                builder.addVariable("timestamp" + id, DataType.CHAR, List.of(ntimeDim, fieldLengthDim));
                builder.addVariable("vsrc" + id + "_eutm", DataType.FLOAT, List.of(ntimeDim, nvsrcDim));
                builder.addVariable("vsrc" + id + "_nutm", DataType.FLOAT, List.of(ntimeDim, nvsrcDim));
                builder.addVariable("vsrc" + id + "_zag", DataType.FLOAT, List.of(ntimeDim, nvsrcDim));

                // add variables for all pollutants
                for (var s : species) {
                    builder.addVariable("vsrc" + id + "_" + s, DataType.FLOAT, List.of(ntimeDim, nvsrcDim));
                }

                counter++;
            }

            log.info("Reserved vehicle data for: " + counter + " vehicles. ");
        }

        private void writeMetadata(Map<Id<Vehicle>, List<VehicleObservation>> observedVehicles, Collection<String> species, OffsetDateTime utcDate) {

            log.info("Starting to write metadata.");
            var counter = 0;
            for (var entry : observedVehicles.entrySet()) {

                if (entry.getValue().isEmpty()) continue; // don't write metadata for empty trajectories

                if (counter % observedVehicles.size() == 1000) {
                    log.info("Writing metadata: " + counter + "/" + observedVehicles.size());
                }

                var id = entry.getKey().toString();
                try {
                    internalWriter.write("nspecies" + id, nSpeciesArray(species.size()));
                    internalWriter.write("ntime" + id, timesArray(entry.getValue()));
                    internalWriter.write("nvsrc" + id, new ArrayInt.D1(NVSCRS_LENGTH, false)); // this will probably be something else once I have understood this variable
                    internalWriter.write("species" + id, speciesArray(species));
                    internalWriter.write("timestamp" + id, timestampArray(entry.getValue(), utcDate));
                    internalWriter.write("vsrc" + id + "_zag", zagArray(entry.getValue()));
                } catch (IOException | NullPointerException | InvalidRangeException e) {
                    log.error(e);
                    throw new RuntimeException("Error while writing id: " + id);
                }
                counter++;
            }

            log.info("Wrote Metadata for " + counter + " vehicles");
        }

        private ArrayInt.D1 nSpeciesArray(int size) {
            var result = new ArrayInt.D1(size, false);
            for (var i = 0; i < size; i++) {
                result.set(i, i);
            }
            return result;
        }

        private ArrayInt.D1 timesArray(List<VehicleObservation> vehicleObservations) {
            var result = new ArrayInt.D1(vehicleObservations.size(), false);
            for (var i = 0; i < vehicleObservations.size(); i++) {
                result.set(i, vehicleObservations.get(i).time());
            }
            return result;
        }

        private ArrayChar.D2 speciesArray(Collection<String> species) {

            var result = new ArrayChar.D2(species.size(), FIELD_LENGTH);
            var iterator = species.iterator();
            var i = 0;
            while (iterator.hasNext()) {
                var item = iterator.next();
                result.setString(i, item);
                i++;
            }

            return result;
        }

        private static ArrayChar.D2 timestampArray(List<VehicleObservation> vehicleObservations, OffsetDateTime utcDate) {

            var result = new ArrayChar.D2(vehicleObservations.size(), FIELD_LENGTH);
            for (var i = 0; i < vehicleObservations.size(); i++) {
                var adjustedTime = utcDate.plusSeconds(vehicleObservations.get(i).time());
                result.setString(i, adjustedTime.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
            }
            return result;
        }

        private ArrayFloat.D2 zagArray(List<VehicleObservation> vehicleObservations) {
            var result = new ArrayFloat.D2(vehicleObservations.size(), NVSCRS_LENGTH);
            for (var a = 0; a < vehicleObservations.size(); a++) {
                for (var b = 0; b < NVSCRS_LENGTH; b++)
                    result.set(a, b, 0.3F); // set height of sources to 30cm. Which we assume is the height of an exhaustion pipe
            }
            return result;
        }

        @Override
        public void handleEvent(Event event) {

            if (event instanceof PositionEmissionsModule.PositionEmissionEvent pee) {
                handlePositionEmissionEvent(pee);
            } else if (event instanceof VehicleEntersTrafficEvent e) {
                handleVehicleEntersTraffic(e);
            } else if (event instanceof VehicleLeavesTrafficEvent e) {
                handleVehicleLeavesTraffic(e);
            }
        }

        private void handleVehicleEntersTraffic(VehicleEntersTrafficEvent e) {
            if (!orig2num.containsKey(e.getVehicleId())) return;

            var cacheItem = getCacheItem(orig2num.get(e.getVehicleId()));
            cacheItem.startTrajectory();
        }

        private void handleVehicleLeavesTraffic(VehicleLeavesTrafficEvent e) {
            if (!orig2num.containsKey(e.getVehicleId())) return;

            var id = orig2num.get(e.getVehicleId());
            var cacheItem = getCacheItem(id);
            cacheItem.finishTrajectory();

            if (cacheItem.isFinished()) {
                // remove the cache item from the map
                vehCache.remove(id);
                // assuming we have seen all events, also remove the vehicle from the orig2num map
                orig2num.remove(e.getVehicleId());

                // write all values into the file
                write(cacheItem, id);
            }
        }

        private void handlePositionEmissionEvent(PositionEmissionsModule.PositionEmissionEvent pee) {
            var id = orig2num.get(pee.getVehicleId());
            var cacheItem = getCacheItem(id);

            cacheItem.addPosition(pee.getCoord().getX(), pee.getCoord().getY());

            for (var s : speciesMapping.object2IntEntrySet()) {
                if (s.getKey().equals("NO")) {
                    var nox = pee.getEmissions().get(Pollutant.NOx);
                    var no2 = pee.getEmissions().get(Pollutant.NO2);
                    var no = nox - no2;
                    cacheItem.addEmissionValue(no, s.getIntValue());
                } else if (s.getKey().equals("PM10")) {
                    var pm = pee.getEmissions().get(Pollutant.PM);
                    var pmNonExhaust = pee.getEmissions().get(Pollutant.PM_non_exhaust);
                    cacheItem.addEmissionValue(pm + pmNonExhaust, s.getIntValue());
                } else {
                    var value = pee.getEmissions().get(Pollutant.valueOf(s.getKey()));
                    cacheItem.addEmissionValue(value, s.getIntValue());
                }
            }
            cacheItem.incrCurrentIndex();
        }

        private void write(VehicleCache cacheItem, Id<Vehicle> id) {

            try {
                internalWriter.write("vsrc" + id + "_eutm", cacheItem.eutm);
                internalWriter.write("vsrc" + id + "_nutm", cacheItem.nutm);
                for (var s : speciesMapping.object2IntEntrySet()) {
                    var species = s.getKey();
                    internalWriter.write("vsrc" + id + "_" + species, cacheItem.emissions.get(s.getIntValue()));
                }

            } catch (IOException | InvalidRangeException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public void closeFile() {
            try {
                internalWriter.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        private VehicleCache getCacheItem(Id<Vehicle> id) {
            return this.vehCache.computeIfAbsent(id, k -> {

                try {
                    var ntimeDim = internalWriter.findDimension("ntime" + id);
                    var nvsrcDim = internalWriter.findDimension("nvsrc" + id);
                    return VehicleCache.init(ntimeDim.getLength(), nvsrcDim.getLength(), this.speciesMapping);
                } catch (NullPointerException e) {
                    throw new RuntimeException(e);
                }
            });
        }
    }

    private static class InputArgs {
        @Parameter(names = {"-positionEmissions", "-p"}, required = true)
        private String positionEmissionEventsFile;

        @Parameter(names = {"-netCdfOutput", "-o"}, required = true)
        private String netCdfOutput;

        @Parameter(names = {"-species", "-s"})
        private List<String> species = List.of("NO", "NO2", "PM10");

        @Parameter(names = {"-date", "-d"}, description = "Date of the simulation run in the format of 'yyyy-mm-dd' (ISO-8601)", required = true)
        private String date;

        @Parameter(names = {"-timeZone", "-z"}, description = "Time Zone for example 'Europe/Paris'")
        private String zone;
    }

    @RequiredArgsConstructor
    @Log4j2
    private static class VehicleCache {

        enum TrajectoryState {STARTED, RECEIVED_POSITION, ENDED}

        final List<ArrayFloat.D2> emissions;
        final ArrayFloat.D2 eutm;
        final ArrayFloat.D2 nutm;
        final int length;

        int curr_index = 0;
        TrajectoryState state = TrajectoryState.ENDED;

        static VehicleCache init(int ntime, int nvsrc, Object2IntMap<String> speciesMapping) {

            var emissionsData = speciesMapping.keySet().stream()
                    .map(k -> new ArrayFloat.D2(ntime, nvsrc))
                    .toList();

            return new VehicleCache(
                    emissionsData,
                    new ArrayFloat.D2(ntime, nvsrc),
                    new ArrayFloat.D2(ntime, nvsrc),
                    ntime);
        }

        void addEmissionValue(double value, int index) {
            emissions.get(index).set(curr_index, 0, (float) value);
        }

        void addPosition(double e, double n) {
            try {
                if (this.state.equals(TrajectoryState.STARTED)) {
                    eutm.set(curr_index - 1, 0, (float) e);
                    nutm.set(curr_index - 1, 0, (float) n);
                    this.state = TrajectoryState.RECEIVED_POSITION;
                }
                eutm.set(curr_index, 0, (float) e);
                nutm.set(curr_index, 0, (float) n);
            } catch (ArrayIndexOutOfBoundsException exception) {
                log.error("Failed to add position for ({},{})", e,n);
                log.error("State of the cache item: Index: {}/{}, Trajectory state: {}", this.curr_index,this.length, this.state);
                throw new RuntimeException(exception);
            }
        }

        float prevN() {
            return nutm.get(curr_index - 1, 0);
        }

        float prevE() {
            return eutm.get(curr_index - 1, 0);
        }

        void startTrajectory() {
            this.state = TrajectoryState.STARTED;
            this.incrCurrentIndex();
        }

        void finishTrajectory() {
            if (this.state.equals(TrajectoryState.STARTED)) {
                // no positions were written. This trajectory was outside the filter.
                // don't write anything, reset the cursor to the before the trajectory
                // started
                this.curr_index--;
            } else if (this.state.equals(TrajectoryState.RECEIVED_POSITION)) {
                // We have had some positions. Set the last position of this trajectory
                // to the same position we've seen last.
                this.addPosition(this.prevE(), this.prevN());
                this.incrCurrentIndex();
                this.state = TrajectoryState.ENDED;
            }
        }

        void incrCurrentIndex() {
            curr_index++;
        }

        boolean isFinished() {
            return curr_index == length;
        }
    }
}
