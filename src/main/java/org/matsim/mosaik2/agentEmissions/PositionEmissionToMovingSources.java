package org.matsim.mosaik2.agentEmissions;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import lombok.RequiredArgsConstructor;
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
import org.matsim.core.network.NetworkUtils;
import org.matsim.vehicles.Vehicle;
import ucar.ma2.*;
import ucar.nc2.Attribute;
import ucar.nc2.Dimension;
import ucar.nc2.write.NetcdfFileFormat;
import ucar.nc2.write.NetcdfFormatWriter;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PositionEmissionToMovingSources {

    public static void main(String[] args) throws InvalidRangeException, IOException {

        var inArgs = new InputArgs();
        JCommander.newBuilder().addObject(inArgs).build().parse(args);

        run(inArgs);

    }

    private static void run(InputArgs inputArgs) throws InvalidRangeException, IOException {

        // split this into two methods so that the metadata collector goes out of scope.
        var netcdfWriter = readFirstPass(inputArgs);
        readSecondPass(inputArgs, netcdfWriter);
    }

    private static NetCDFWriter readFirstPass(InputArgs inputArgs) throws InvalidRangeException, IOException {
        var manager = EventsUtils.createEventsManager();
        var metadata = new MetadataCollector();
        manager.addHandler(metadata);
        var reader = new MatsimEventsReader(manager);
        reader.addCustomEventMapper(PositionEmissionsModule.PositionEmissionEvent.EVENT_TYPE, PositionEmissionsModule.PositionEmissionEvent.getEventMapper());
        reader.readFile(inputArgs.positionEmissionEventsFile);

        return new NetCDFWriter(inputArgs.netCdfOutput, metadata.observedVehicles, inputArgs.species);
    }

    private static void readSecondPass(InputArgs inputArgs, NetCDFWriter writer) {
        var manager = EventsUtils.createEventsManager();
        manager.addHandler(writer);
        var reader = new MatsimEventsReader(manager);
        reader.addCustomEventMapper(PositionEmissionsModule.PositionEmissionEvent.EVENT_TYPE, PositionEmissionsModule.PositionEmissionEvent.getEventMapper());
        reader.readFile(inputArgs.positionEmissionEventsFile);
    }

    private static class MetadataCollector implements BasicEventHandler {

        private final Map<Id<Vehicle>, IntArrayList> observedVehicles = new HashMap<>();

        @Override
        public void handleEvent(Event event) {

            if (event instanceof PositionEmissionsModule.PositionEmissionEvent e) {
                handle(e.getVehicleId(), e.getTime());
            } else if (event instanceof VehicleEntersTrafficEvent e) {
                handle(e.getVehicleId(), e.getTime() + 1);
            } else if (event instanceof VehicleLeavesTrafficEvent e) {
                handle(e.getVehicleId(), e.getTime());
            }
        }

        private void handle(Id<Vehicle> id, double time) {
            observedVehicles.computeIfAbsent(id, k -> new IntArrayList()).add((int) time);
        }
    }

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

        NetCDFWriter(String outputFile, Map<Id<Vehicle>, IntArrayList> observedVehicles, Collection<String> species) throws IOException, InvalidRangeException {
            var builder = NetcdfFormatWriter.builder()
                    .setFormat(NetcdfFileFormat.NETCDF3)
                    .setLocation(outputFile)
                    .addAttribute(new Attribute("author", "Janek Laudan"))
                    .addAttribute(new Attribute("lod", "2"))
                    .addAttribute(new Attribute("num_emission_path", (double) observedVehicles.size()));

            reserveVehicleData(builder, observedVehicles, species);
            this.internalWriter = builder.build();

            writeMetadata(observedVehicles, species);
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

        private void reserveVehicleData(NetcdfFormatWriter.Builder builder, Map<Id<Vehicle>, IntArrayList> observedVehicles, Collection<String> species) {

            var fieldLengthDim = builder.addDimension(Dimension.builder("field_length", FIELD_LENGTH).build());

            for (var entry : observedVehicles.entrySet()) {

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
            }
        }

        private void writeMetadata(Map<Id<Vehicle>, IntArrayList> observedVehicles, Collection<String> species) throws InvalidRangeException, IOException {

            for (var entry : observedVehicles.entrySet()) {

                var id = entry.getKey().toString();

                internalWriter.write("nspecies" + id, nSpeciesArray(species.size()));
                internalWriter.write("ntime" + id, timesArray(entry.getValue()));
                internalWriter.write("nvsrc" + id, new ArrayInt.D1(1, false)); // this will probably be something else once I have understood this variable
                internalWriter.write("species" + id, speciesArray(species));
                internalWriter.write("timestamp" + id, timestampArray(entry.getValue()));
                internalWriter.write("vsrc" + id + "_zag", zagArray(entry.getValue()));
            }
        }

        private ArrayInt.D1 nSpeciesArray(int size) {
            var result = new ArrayInt.D1(size, false);
            for (var i = 0; i < size; i++) {
                result.set(i, i);
            }
            return result;
        }

        private ArrayInt.D1 timesArray(IntArrayList ints) {
            var result = new ArrayInt.D1(ints.size(), false);
            for (var i = 0; i < ints.size(); i++) {
                result.set(i, ints.getInt(i));
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

        private ArrayChar.D2 timestampArray(IntArrayList times) {

            var result = new ArrayChar.D2(times.size(), FIELD_LENGTH);
            for (var i = 0; i < times.size(); i++) {
                result.setString(i, "Dummy Timestamp");
            }
            return result;
        }

        private ArrayFloat.D2 zagArray(IntArrayList times) {
            var result = new ArrayFloat.D2(times.size(), NVSCRS_LENGTH);
            for (var a = 0; a < times.size(); a++) {
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
                var cacheItem = getCacheItem(e.getVehicleId());
                cacheItem.markTripStart();
                cacheItem.incrPosition();
            } else if (event instanceof VehicleLeavesTrafficEvent e) {
                var cacheItem = getCacheItem(e.getVehicleId());
                var prevE = cacheItem.prevE();
                var prevN = cacheItem.prevN();
                cacheItem.addPosition(prevE, prevN);
                cacheItem.incrPosition();
                if (cacheItem.isFinished()) {
                    // remove the cache item from the map
                    vehCache.remove(e.getVehicleId());

                    // write all values into the file
                    write(cacheItem, e.getVehicleId());
                }
            }
        }

        private void handlePositionEmissionEvent(PositionEmissionsModule.PositionEmissionEvent pee) {
            var cacheItem = getCacheItem(pee.getVehicleId());

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
            cacheItem.incrPosition();
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

                var ntimeDim = internalWriter.findDimension("ntime" + id);
                var nvsrcDim = internalWriter.findDimension("nvsrc" + id);
                return VehicleCache.init(ntimeDim.getLength(), nvsrcDim.getLength(), this.speciesMapping);
            });
        }
    }

    private static class InputArgs {
        @Parameter(names = "-pee", required = true)
        private String positionEmissionEventsFile;

        @Parameter(names = "-n", required = true)
        private String networkFile;

        @Parameter(names = "-netCdfOutput", required = true)
        private String netCdfOutput;

        @Parameter(names = {"-species", "-s"})
        private List<String> species = List.of("NO", "NO2", "PM10");

    }

    @RequiredArgsConstructor
    private static class VehicleCache {

        final List<ArrayFloat.D2> emissions;
        final ArrayFloat.D2 eutm;
        final ArrayFloat.D2 nutm;
        final int length;

        int counter = 0;

        boolean setPrevIndexZero = false;

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
            emissions.get(index).set(counter, 0, (float) value);
        }

        void addPosition(double e, double n) {
            if (setPrevIndexZero) {
                eutm.set(counter - 1, 0, (float) e);
                nutm.set(counter - 1, 0, (float) n);
                setPrevIndexZero = false;
            }
            eutm.set(counter, 0, (float) e);
            nutm.set(counter, 0, (float) n);
        }

        float prevN() {
            return nutm.get(counter - 1, 0);
        }

        float prevE() {
            return eutm.get(counter - 1, 0);
        }

        void markTripStart() {
            this.setPrevIndexZero = true;
        }

        void incrPosition() {
            counter++;
        }

        boolean isFinished() {
            return counter == length;
        }
    }
}
