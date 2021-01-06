package org.matsim.mosaik2.agentEmissions;

import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
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

import javax.inject.Inject;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class RunEmissionsFromPosition {
    public static void main(String[] args) {

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

        private WriterHandler handler;

        @Override
        public void notifyAfterMobsim(AfterMobsimEvent event) {
            eventsManager.removeHandler(handler);
            handler.closeFile();
            handler = null;
        }

        @Override
        public void notifyBeforeMobsim(BeforeMobsimEvent event) {
            if (event.isLastIteration()) {
                handler = new WriterHandler(outputDirectoryHierarchy.getIterationFilename(event.getIteration(), "position-emissions.xml.gz"));
                eventsManager.addHandler(handler);
            }
        }

        @Override
        public void notifyShutdown(ShutdownEvent event) {
            if (event.isUnexpected() && handler != null)
                handler.closeFile();
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

            if (event.getEventType().equals(PositionEmissionsModule.EmissionPositionEvent.EVENT_TYPE)) {
                delegate.handleEvent(event);
            }
        }

        @Override
        public void closeFile() {
            delegate.closeFile();
        }
    }
}
