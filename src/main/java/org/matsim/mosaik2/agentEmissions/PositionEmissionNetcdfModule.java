package org.matsim.mosaik2.agentEmissions;

import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.events.Event;
import org.matsim.contrib.emissions.Pollutant;
import org.matsim.contrib.emissions.PositionEmissionsModule;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.config.ReflectiveConfigGroup;
import org.matsim.core.controler.AbstractModule;
import org.matsim.core.controler.OutputDirectoryHierarchy;
import org.matsim.core.controler.events.AfterMobsimEvent;
import org.matsim.core.controler.events.BeforeMobsimEvent;
import org.matsim.core.controler.events.ShutdownEvent;
import org.matsim.core.controler.listener.AfterMobsimListener;
import org.matsim.core.controler.listener.BeforeMobsimListener;
import org.matsim.core.controler.listener.ShutdownListener;
import org.matsim.core.events.algorithms.EventWriter;
import org.matsim.core.events.algorithms.EventWriterXML;
import org.matsim.core.events.handler.BasicEventHandler;
import org.matsim.core.utils.collections.Tuple;
import org.matsim.vehicles.Vehicle;
import ucar.ma2.*;
import ucar.nc2.Attribute;
import ucar.nc2.NetcdfFileWriter;

import javax.inject.Inject;
import java.io.IOException;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

public class PositionEmissionNetcdfModule extends AbstractModule {

    private static final Logger log = Logger.getLogger(PositionEmissionNetcdfModule.class);

    @Override
    public void install() {
        addControlerListenerBinding().to(MobsimHandler.class);
    }

    static class NetcdfEmissionWriterConfig extends ReflectiveConfigGroup {

        public static final String GOUP_NAME = "netcdPositionEmission";

        private EnumMap<Pollutant, String> pollutantMapping = new EnumMap<>(Pollutant.class);
        private boolean calculateNOFromNOxAndNO2 = true;

        public NetcdfEmissionWriterConfig() {
            super(GOUP_NAME);
        }

        public boolean getCalculateNOFromNOxAndNO2() {
            return calculateNOFromNOxAndNO2;
        }

        public void setCalculateNOFromNOxAndNO2(boolean value) {
            this.calculateNOFromNOxAndNO2 = value;
        }

        @StringGetter("pollutants")
        public String getPollutantsAsString() {
            return pollutantMapping.entrySet().stream()
                    .map(entry -> entry.getKey() + "->" + entry.getValue())
                    .collect(Collectors.joining(", "));
        }

        public Map<Pollutant, String> getPollutantMapping() {
            return pollutantMapping;
        }

        @StringSetter("pollutants")
        public void setPollutantMapping(String valuesAsString) {
            var map = Arrays.stream(valuesAsString.split(","))
                    .map(String::trim)
                    .map(keyValue -> {
                        var splitted = keyValue.split("->");
                        if (splitted.length != 2) throw new IllegalArgumentException("NO!");
                        return Tuple.of(Pollutant.valueOf(splitted[0]), splitted[1]);
                    })
                    .collect(Collectors.toMap(Tuple::getFirst, Tuple::getSecond));
            this.pollutantMapping = new EnumMap<>(map);
        }

        public void setPollutants(Map<Pollutant, String> pollutants) {
            this.pollutantMapping = new EnumMap<>(pollutants);
        }
    }

    private static class MobsimHandler implements BeforeMobsimListener, AfterMobsimListener, ShutdownListener {

        @Inject
        private EventsManager eventsManager;

        @Inject
        private OutputDirectoryHierarchy outputDirectoryHierarchy;

        @Inject
        private Scenario scenario;

        @Inject
        private NetcdfEmissionWriterConfig config;

        private NetcdfWriterHandler netcdfHandler;

        @Override
        public void notifyAfterMobsim(AfterMobsimEvent event) {

            if (netcdfHandler != null) {
                eventsManager.removeHandler(netcdfHandler);
                netcdfHandler.closeFile();
                netcdfHandler = null;
            }
        }

        @Override
        public void notifyBeforeMobsim(BeforeMobsimEvent event) {
            if (event.isLastIteration()) {
                try {
                    netcdfHandler = new NetcdfWriterHandler(outputDirectoryHierarchy.getIterationFilename(event.getIteration(), "position-emissions.nc"),
                            scenario.getPopulation().getPersons().size(), config.getPollutantMapping(), config.getCalculateNOFromNOxAndNO2());
                    eventsManager.addHandler(netcdfHandler);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }

        @Override
        public void notifyShutdown(ShutdownEvent event) {
            if (event.isUnexpected() && netcdfHandler != null)
                netcdfHandler.closeFile();
        }
    }

    private static class NetcdfWriterHandler implements BasicEventHandler, EventWriter {

        private final NetcdfFileWriter writer;
        private final Object2IntOpenHashMap<Id<Vehicle>> idMapping = new Object2IntOpenHashMap<>();
        private final Map<Pollutant, String> pollutants;
        private final boolean calculateNO;

        // only try time and ids for now
        private int[] currentTimeIndex = new int[] {-1};
        private double currentTimeStep = Double.NEGATIVE_INFINITY;

        private int currentPersonIndex = 0;
        private int lastIntId = 0;

        private final Array timeData = Array.factory(DataType.DOUBLE, new int[] {1});
        private final Array numberOfVehicles = Array.factory(DataType.INT, new int[] {1});
        private ArrayInt.D2 vehicleIds;
        private ArrayDouble.D2 x;
        private ArrayDouble.D2 y;
        private final Map<String, ArrayDouble.D2> emissions = new HashMap<>();


        private NetcdfWriterHandler(String filename, int numberOfAgents, Map<Pollutant, String> pollutants, boolean calculateNO) throws IOException {
            log.info("Opening Netcdf Writer at: " + filename);
            writer = NetcdfFileWriter.createNew(NetcdfFileWriter.Version.netcdf3, filename);
            writer.setFill(true);
            this.pollutants = pollutants;
            this.calculateNO = calculateNO;
            writeDimensions(numberOfAgents);
            writeVariables();
            writeAttributes();
            writer.create();
        }

        private void writeDimensions(int numberOfAgents) {

            writer.addUnlimitedDimension("time");
            writer.addDimension("agents", numberOfAgents);
        }

        private void writeVariables() {

            writer.addVariable("time", DataType.DOUBLE, "time");
            writer.addVariable("number_of_vehicles", DataType.INT, "time");
            writer.addVariable("vehicle_id", DataType.INT, "time agents");
            writer.addVariable("x", DataType.DOUBLE, "time agents");
            writer.addVariable("y", DataType.DOUBLE, "time agents");

            // next think about emissions
            for (var pollutant : pollutants.entrySet()) {
                writer.addVariable(pollutant.getValue(), DataType.DOUBLE, "time agents");
            }

            if (calculateNO) {
                writer.addVariable("NO", DataType.DOUBLE, "time agents");
            }
        }

        private void writeAttributes() {

            writer.findVariable("time").addAttribute(new Attribute("unit", "s"));

            var fillValue = new Attribute("_Fill_Value", -9999.9D);
            writer.findVariable("vehicle_id").addAttribute(fillValue);
            writer.findVariable("x").addAttribute(fillValue);
            writer.findVariable("y").addAttribute(fillValue);

            // next think about emissions
            for (var pollutant : pollutants.entrySet()) {
                writer.findVariable(pollutant.getValue()).addAttribute(fillValue);
            }

            if (calculateNO) {
                writer.findVariable("NO").addAttribute(fillValue);
            }
        }

        @Override
        public void closeFile() {
            try {
                // write data from last timestep
                log.info("Closing file: First write the last available chunk of data.");
                writeData();

                log.info("Now actually close the file.");
                writer.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public void handleEvent(Event event) {
            if (event.getEventType().equals(PositionEmissionsModule.PositionEmissionEvent.EVENT_TYPE)) {

                var positionEmissionEvent = (PositionEmissionsModule.PositionEmissionEvent)event;

                if (positionEmissionEvent.getEmissionType().equals("cold")) return; // ignore cold events for now, but think about it later

                adjustTime(positionEmissionEvent.getTime());

                int intId = idMapping.computeIfAbsent(positionEmissionEvent.getVehicleId(), id -> {
                    lastIntId++;
                    return lastIntId;
                });

                try {
                    vehicleIds.set(0, currentPersonIndex, intId);
                    x.set(0, currentPersonIndex, positionEmissionEvent.getCoord().getX());
                    y.set(0, currentPersonIndex, positionEmissionEvent.getCoord().getY());

                    for (var eventEmissions : positionEmissionEvent.getEmissions().entrySet()) {
                        if (pollutants.containsKey(eventEmissions.getKey())) {

                            var dataKey = pollutants.get(eventEmissions.getKey());
                            var data = emissions.get(dataKey);
                            data.set(0, currentPersonIndex, eventEmissions.getValue());
                        }
                    }

                    if (calculateNO) {
                        var nox = positionEmissionEvent.getEmissions().get(Pollutant.NOx);
                        var no2 = positionEmissionEvent.getEmissions().get(Pollutant.NO2);
                        var no = nox - no2;
                        var data = emissions.get("NO");
                        data.set(0, currentPersonIndex, no);
                    }

                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                currentPersonIndex++;
            }
        }

        @Override
        public void reset(int iteration) {
            currentTimeStep = 0;
            currentPersonIndex = 0;
            currentTimeIndex = new int[] {-1};
        }

        private void adjustTime(double time) {
            if (time > currentTimeStep) {

                // assuming that only positive time values are valid
                if (currentTimeStep >= 0)
                    writeData();

                beforeTimestep(time);
            }
        }

        private void writeData() {
            // write all the stuff
            try {
                timeData.setDouble(timeData.getIndex(), currentTimeStep);
                writer.write("time", currentTimeIndex, timeData);

                numberOfVehicles.setInt(numberOfVehicles.getIndex(), currentPersonIndex);
                writer.write("number_of_vehicles", currentTimeIndex, numberOfVehicles);

                writer.write("vehicle_id", getCurrentIndex(), vehicleIds);
                writer.write("x", getCurrentIndex(), x);
                writer.write("y", getCurrentIndex(), y);

                for (var emission : emissions.entrySet()) {
                    writer.write(emission.getKey(), getCurrentIndex(), emission.getValue() );
                }

                if (calculateNO)
                    writer.write("NO", getCurrentIndex(), emissions.get("NO"));

            } catch (IOException | InvalidRangeException e) {
                throw new RuntimeException(e);
            }
        }

        private int[] getCurrentIndex() {
            return new int[] { currentTimeIndex[0], 0};
        }

        private void beforeTimestep(double time) {

            // reset all the state
            currentTimeStep = time;
            currentPersonIndex = 0;
            currentTimeIndex[0] = currentTimeIndex[0] + 1; // increase time index by 1
            // person data for the next time slice. Of dimension 1 for timestep and of number of agents for agents
            vehicleIds = new ArrayInt.D2(1, writer.findDimension("agents").getLength(), false);
            x = new ArrayDouble.D2(1, writer.findDimension("agents").getLength());
            y = new ArrayDouble.D2(1, writer.findDimension("agents").getLength());

            for (var pollutant : pollutants.entrySet()) {
                emissions.put(pollutant.getValue(), new ArrayDouble.D2(1, writer.findDimension("agents").getLength()));
            }

            if (calculateNO)
                emissions.put("NO", new ArrayDouble.D2(1, writer.findDimension("agents").getLength()));
        }
    }
}
