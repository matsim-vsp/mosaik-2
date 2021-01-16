package org.matsim.mosaik2.agentEmissions;

import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.events.Event;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.PopulationFactory;
import org.matsim.contrib.emissions.EmissionUtils;
import org.matsim.contrib.emissions.PositionEmissionsModule;
import org.matsim.contrib.emissions.utils.EmissionsConfigGroup;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.ControlerConfigGroup;
import org.matsim.core.config.groups.PlanCalcScoreConfigGroup;
import org.matsim.core.config.groups.QSimConfigGroup;
import org.matsim.core.config.groups.StrategyConfigGroup;
import org.matsim.core.controler.AbstractModule;
import org.matsim.core.controler.Controler;
import org.matsim.core.controler.OutputDirectoryHierarchy;
import org.matsim.core.controler.events.AfterMobsimEvent;
import org.matsim.core.controler.events.BeforeMobsimEvent;
import org.matsim.core.controler.events.ShutdownEvent;
import org.matsim.core.controler.listener.AfterMobsimListener;
import org.matsim.core.controler.listener.BeforeMobsimListener;
import org.matsim.core.controler.listener.ShutdownListener;
import org.matsim.core.events.SimStepParallelEventsManagerImpl;
import org.matsim.core.events.algorithms.EventWriter;
import org.matsim.core.events.algorithms.EventWriterXML;
import org.matsim.core.events.handler.BasicEventHandler;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.population.routes.RouteUtils;
import org.matsim.core.scenario.MutableScenario;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.vehicles.EngineInformation;
import org.matsim.vehicles.Vehicle;
import org.matsim.vehicles.VehicleType;
import org.matsim.vehicles.VehicleUtils;
import ucar.ma2.*;
import ucar.nc2.NetcdfFileWriter;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class RunEmissionsFromPosition {
    public static void main(String[] args) throws IOException {

        var emissionConfig = new EmissionsConfigGroup();
        emissionConfig.setHbefaVehicleDescriptionSource(EmissionsConfigGroup.HbefaVehicleDescriptionSource.fromVehicleTypeDescription);
        emissionConfig.setDetailedVsAverageLookupBehavior(EmissionsConfigGroup.DetailedVsAverageLookupBehavior.tryDetailedThenTechnologyAverageThenAverageTable);
        emissionConfig.setDetailedColdEmissionFactorsFile("C:\\Users\\Janekdererste\\repos\\shared-svn\\projects\\matsim-germany\\hbefa\\hbefa-files\\v4.1\\EFA_ColdStart_Concept_2020_detailed_perTechAverage_Bln_carOnly.csv");
        emissionConfig.setDetailedWarmEmissionFactorsFile("C:\\Users\\Janekdererste\\repos\\shared-svn\\projects\\matsim-germany\\hbefa\\hbefa-files\\v4.1\\EFA_HOT_Concept_2020_detailed_perTechAverage_Bln_carOnly.csv");
        emissionConfig.setAverageColdEmissionFactorsFile("C:\\Users\\Janekdererste\\repos\\shared-svn\\projects\\matsim-germany\\hbefa\\hbefa-files\\v4.1\\EFA_ColdStart_Vehcat_2020_Average.csv");
        emissionConfig.setAverageWarmEmissionFactorsFile("C:\\Users\\Janekdererste\\repos\\shared-svn\\projects\\matsim-germany\\hbefa\\hbefa-files\\v4.1\\EFA_HOT_Vehcat_2020_Average.csv");
        emissionConfig.setHbefaRoadTypeSource(EmissionsConfigGroup.HbefaRoadTypeSource.fromLinkAttributes);

        var config = ConfigUtils.createConfig(emissionConfig);
        config.controler().setOverwriteFileSetting(OutputDirectoryHierarchy.OverwriteFileSetting.deleteDirectoryIfExists);
        config.controler().setOutputDirectory("C:\\Users\\Janekdererste\\Desktop\\position-emission");

        final PlanCalcScoreConfigGroup.ActivityParams homeParams = new PlanCalcScoreConfigGroup.ActivityParams("home")
                .setTypicalDuration(20);
        config.planCalcScore().addActivityParams(homeParams);
        final PlanCalcScoreConfigGroup.ActivityParams workParams = new PlanCalcScoreConfigGroup.ActivityParams("work")
                .setTypicalDuration(20);
        config.planCalcScore().addActivityParams(workParams);

        var strategy = new StrategyConfigGroup.StrategySettings();
        strategy.setStrategyName("ChangeExpBeta");
        strategy.setWeight(1.0);

        config.strategy().addParameterSet(strategy);

        // activate snapshots
        config.qsim().setSnapshotPeriod(1);
        config.qsim().setSnapshotStyle(QSimConfigGroup.SnapshotStyle.queue);
        config.controler().setWriteSnapshotsInterval(1);
        config.controler().setSnapshotFormat(Set.of(ControlerConfigGroup.SnapshotFormat.positionevents));
        config.controler().setFirstIteration(0);
        config.controler().setLastIteration(0);

        config.qsim().setVehiclesSource(QSimConfigGroup.VehiclesSource.fromVehiclesData);

        // create a scenario:
        final MutableScenario scenario = ScenarioUtils.createMutableScenario(config);
        scenario.setNetwork(createSingleLinkNetwork());

        var vehicleType = createVehicleType();
        scenario.getVehicles().addVehicleType(vehicleType);
        Vehicle vehicle = VehicleUtils.createVehicle(Id.createVehicleId("1"), vehicleType);
        scenario.getVehicles().addVehicle(vehicle);
        var person = createPerson(scenario.getPopulation().getFactory());
        scenario.getPopulation().addPerson(person);
        VehicleUtils.insertVehicleIdsIntoAttributes(person, Map.of(vehicle.getType().getNetworkMode(), vehicle.getId()));

        var controler = new Controler(scenario);

        controler.addOverridingModule(new PositionEmissionsModule());

        controler.addOverridingModule(new AbstractModule() {
            @Override
            public void install() {

                addControlerListenerBinding().to(MobsimHandler.class);
                bind(EventsManager.class).to(SimStepParallelEventsManagerImpl.class).in(Singleton.class);
            }
        });

        controler.run();
    }

    private static VehicleType createVehicleType() {
        VehicleType vehicleType = VehicleUtils.createVehicleType(Id.create("dieselCarFullSpecified", VehicleType.class));
        EngineInformation engineInformation = vehicleType.getEngineInformation();
        VehicleUtils.setHbefaVehicleCategory(engineInformation, "PASSENGER_CAR");
        VehicleUtils.setHbefaTechnology(engineInformation, "diesel");
        VehicleUtils.setHbefaEmissionsConcept(engineInformation, "PC-D-Euro-3");
        VehicleUtils.setHbefaSizeClass(engineInformation, ">1,4L");
        vehicleType.setMaximumVelocity(10);

        return vehicleType;
    }

    /**
     * Creates network with three links, to make sure the main link is traversed completely
     */
    private static Network createSingleLinkNetwork() {

        var network = NetworkUtils.createNetwork();
        addLink(network, "start-link",
                network.getFactory().createNode(Id.createNodeId("1"), new Coord(-100, 0)),
                network.getFactory().createNode(Id.createNodeId("2"), new Coord(0, 0)));
        addLink(network, "link",
                network.getFactory().createNode(Id.createNodeId("2"), new Coord(0, 0)),
                network.getFactory().createNode(Id.createNodeId("3"), new Coord(1000, 0)));
        addLink(network, "end-link",
                network.getFactory().createNode(Id.createNodeId("3"), new Coord(1000, 0)),
                network.getFactory().createNode(Id.createNodeId("4"), new Coord(1100, 0)));
        return network;
    }

    private static void addLink(Network network, String id, Node from, Node to) {

        if (!network.getNodes().containsKey(from.getId()))
            network.addNode(from);
        if (!network.getNodes().containsKey(to.getId()))
            network.addNode(to);

        var link = network.getFactory().createLink(Id.createLinkId(id), from, to);
        EmissionUtils.setHbefaRoadType(link, "URB/Local/50");
        link.setFreespeed(10);
        network.addLink(link);
    }

    private static Person createPerson(PopulationFactory factory) {

        var plan = factory.createPlan();
        var home = PopulationUtils.createAndAddActivityFromCoord(plan, "home", new Coord(-100, 0));
        home.setEndTime(1);

        var leg = PopulationUtils.createLeg(TransportMode.car);
        leg.setRoute(RouteUtils.createNetworkRoute(List.of(Id.createLinkId("start"), Id.createLinkId("link"), Id.createLinkId("end"))));
        plan.addLeg(leg);

        var work = PopulationUtils.createAndAddActivityFromCoord(plan, "work", new Coord(1100, 0));
        work.setEndTime(3600);

        var person = factory.createPerson(Id.createPersonId("person"));
        person.addPlan(plan);
        person.setSelectedPlan(plan);
        return person;
    }

    private static class MobsimHandler implements BeforeMobsimListener, AfterMobsimListener, ShutdownListener {

        @Inject
        private EventsManager eventsManager;

        @Inject
        private OutputDirectoryHierarchy outputDirectoryHierarchy;

        @Inject
        private Scenario scenario;

        private WriterHandler xmlHandler;
        private NetcdfWriterHandler netcdfHandler;



        @Override
        public void notifyAfterMobsim(AfterMobsimEvent event) {
            eventsManager.removeHandler(xmlHandler);
            xmlHandler.closeFile();
            xmlHandler = null;

            eventsManager.removeHandler(netcdfHandler);
            netcdfHandler.closeFile();
            netcdfHandler = null;
        }

        @Override
        public void notifyBeforeMobsim(BeforeMobsimEvent event) {
            if (event.isLastIteration()) {
                xmlHandler = new WriterHandler(outputDirectoryHierarchy.getIterationFilename(event.getIteration(), "position-emissions.xml.gz"));
                eventsManager.addHandler(xmlHandler);
                try {
                    netcdfHandler = new NetcdfWriterHandler(outputDirectoryHierarchy.getIterationFilename(event.getIteration(), "position-emissions.nc"), scenario.getPopulation().getPersons().size());
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                eventsManager.addHandler(netcdfHandler);
            }
        }

        @Override
        public void notifyShutdown(ShutdownEvent event) {
            if (event.isUnexpected() && xmlHandler != null)
                xmlHandler.closeFile();
        }
    }

    private static class WriterHandler implements BasicEventHandler, EventWriter {

        // this will be re-placed with a net-cdf writer
        private final EventWriterXML delegate;

        private WriterHandler(String filename) {
            this.delegate = new EventWriterXML(filename);
        }

        @Override
        public void handleEvent(Event event) {

            if (event.getEventType().equals(PositionEmissionsModule.PositionEmissionEvent.EVENT_TYPE)) {
                delegate.handleEvent(event);
            }
        }

        @Override
        public void closeFile() {
            delegate.closeFile();
        }
    }

    private static class NetcdfWriterHandler implements BasicEventHandler, EventWriter {

        private final NetcdfFileWriter writer;
        private final Object2IntOpenHashMap<Id<Vehicle>> idMapping = new Object2IntOpenHashMap<>();

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


        private NetcdfWriterHandler(String filename, int numberOfAgents) throws IOException {
            writer = NetcdfFileWriter.createNew(NetcdfFileWriter.Version.netcdf3, filename);
            writeDimensions(numberOfAgents);
            writeVariables();
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
        }

        @Override
        public void closeFile() {
            try {
                // write data from last timestep
                writeData();
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

                var time = positionEmissionEvent.getTime();
                adjustTime(time);
                int intId = idMapping.computeIfAbsent(positionEmissionEvent.getVehicleId(), id -> {
                    lastIntId++;
                    return lastIntId;
                });

                try {
                    vehicleIds.set(0, currentPersonIndex, intId);
                    x.set(0, currentPersonIndex, positionEmissionEvent.getCoord().getX());
                    y.set(0, currentPersonIndex, positionEmissionEvent.getCoord().getY());
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

                numberOfVehicles.setInt(numberOfVehicles.getIndex(), (int) vehicleIds.getSize());
                writer.write("number_of_vehicles", currentTimeIndex, numberOfVehicles);

                writer.write("vehicle_id", new int[] {currentTimeIndex[0], 0}, vehicleIds);
                writer.write("x", new int[] { currentTimeIndex[0], 0}, x);
                writer.write("y", new int[] { currentTimeIndex[0], 0}, y);
            } catch (IOException | InvalidRangeException e) {
                throw new RuntimeException(e);
            }
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
        }
    }
}
