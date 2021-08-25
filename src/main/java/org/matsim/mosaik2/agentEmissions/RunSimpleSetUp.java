package org.matsim.mosaik2.agentEmissions;

import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.PopulationFactory;
import org.matsim.contrib.emissions.EmissionUtils;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.PlanCalcScoreConfigGroup;
import org.matsim.core.config.groups.QSimConfigGroup;
import org.matsim.core.config.groups.StrategyConfigGroup;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.population.routes.RouteUtils;
import org.matsim.core.scenario.MutableScenario;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.vehicles.EngineInformation;
import org.matsim.vehicles.Vehicle;
import org.matsim.vehicles.VehicleType;
import org.matsim.vehicles.VehicleUtils;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public class RunSimpleSetUp extends AbstractRunPositionEmissions{

    public static void main(String[] args) throws IOException {

        new RunSimpleSetUp().run(args);
    }

    void run(String[] args) {

        var config = ConfigUtils.createConfig(getConfigGroups());
        var hbefaConfig = (HbefaConfigGroup) config.getModules().get(HbefaConfigGroup.GROUP_NAME);
        hbefaConfig.setHbefaDirectory("C:\\Users\\Janek\\repos\\shared-svn\\projects\\matsim-germany\\hbefa\\hbefa-files\\v4.1");
        applyConfig(config);

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

        config.controler().setOutputDirectory("test/output/position-emission");
        config.controler().setLastIteration(0);
        config.qsim().setVehiclesSource(QSimConfigGroup.VehiclesSource.fromVehiclesData);
        config.transit().setUseTransit(true);

        // create a scenario:
        final MutableScenario scenario = ScenarioUtils.createMutableScenario(config);
        scenario.setNetwork(createSingleLinkNetwork());

        var vehicleType = createVehicleType();
        scenario.getVehicles().addVehicleType(vehicleType);
        Vehicle vehicle = VehicleUtils.createVehicle(Id.createVehicleId("1"), vehicleType);
        scenario.getVehicles().addVehicle(vehicle);
        Vehicle vehicle2 = VehicleUtils.createVehicle(Id.createVehicleId("2"), vehicleType);
        scenario.getVehicles().addVehicle(vehicle2);
        var person = createPerson("person1", scenario.getPopulation().getFactory());
        scenario.getPopulation().addPerson(person);
        var person2 = createPerson("person2", scenario.getPopulation().getFactory());
        scenario.getPopulation().addPerson(person2);
        VehicleUtils.insertVehicleIdsIntoAttributes(person, Map.of(vehicle.getType().getNetworkMode(), vehicle.getId()));
        VehicleUtils.insertVehicleIdsIntoAttributes(person2, Map.of(vehicle.getType().getNetworkMode(), vehicle2.getId()));

        scenario.setTransitVehicles(VehicleUtils.createVehiclesContainer());

        applyScenario(scenario);

        var controler = loadControler(scenario);
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
        network.setCapacityPeriod(1);
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

    private static Person createPerson(String id, PopulationFactory factory) {

        var plan = factory.createPlan();
        var home = PopulationUtils.createAndAddActivityFromCoord(plan, "home", new Coord(-100, 0));
        home.setEndTime(1);

        var leg = PopulationUtils.createLeg(TransportMode.car);
        leg.setRoute(RouteUtils.createNetworkRoute(List.of(Id.createLinkId("start"), Id.createLinkId("link"), Id.createLinkId("end"))));
        plan.addLeg(leg);

        var work = PopulationUtils.createAndAddActivityFromCoord(plan, "work", new Coord(1100, 0));
        work.setEndTime(3600);

        var person = factory.createPerson(Id.createPersonId(id));
        person.addPlan(plan);
        person.setSelectedPlan(plan);
        return person;
    }
}
