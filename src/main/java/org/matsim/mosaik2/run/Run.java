package org.matsim.mosaik2.run;

import ch.sbb.matsim.routing.pt.raptor.SwissRailRaptorModule;
import lombok.extern.log4j.Log4j2;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigGroup;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.PlansCalcRouteConfigGroup;
import org.matsim.core.controler.AbstractModule;
import org.matsim.core.controler.Controler;
import org.matsim.core.controler.OutputDirectoryLogging;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.mosaik2.Utils;
import org.matsim.vehicles.VehicleType;
import org.matsim.vehicles.VehiclesFactory;

@Log4j2
public class Run {

    public static void main(String[] args) {

        Config config = loadConfig(args);
        Scenario scenario = loadScenario(config);
        Controler controler = loadControler(scenario);
        controler.run();
    }

    public static Config loadConfig(String[] args, ConfigGroup... modules) {

        OutputDirectoryLogging.catchLogEntries();

        Config config = ConfigUtils.loadConfig(args, modules);

        config.plansCalcRoute().setAccessEgressType(PlansCalcRouteConfigGroup.AccessEgressType.accessEgressModeToLink);
        config.qsim().setUsingTravelTimeCheckInTeleportation(true);
        config.qsim().setUsePersonIdForMissingVehicleId(false);
        config.subtourModeChoice().setProbaForRandomSingleTripMode(0.5);

        final long minDuration = 600;
        final long maxDuration = 3600 * 27;
        final long difference = 600;

        Utils.createTypicalDurations("home", minDuration, maxDuration, difference).forEach(params -> config.planCalcScore().addActivityParams(params));
        Utils.createTypicalDurations("work", minDuration, maxDuration, difference).forEach(params -> config.planCalcScore().addActivityParams(params));
        Utils.createTypicalDurations("leiseure", minDuration, maxDuration, difference).forEach(params -> config.planCalcScore().addActivityParams(params));
        Utils.createTypicalDurations("shopping", minDuration, maxDuration, difference).forEach(params -> config.planCalcScore().addActivityParams(params));
        Utils.createTypicalDurations("errands", minDuration, maxDuration, difference).forEach(params -> config.planCalcScore().addActivityParams(params));
        Utils.createTypicalDurations("business", minDuration, maxDuration, difference).forEach(params -> config.planCalcScore().addActivityParams(params));
        Utils.createTypicalDurations("educ_secondary", minDuration, maxDuration, difference).forEach(params -> config.planCalcScore().addActivityParams(params));
        Utils.createTypicalDurations("educ_higher", minDuration, maxDuration, difference).forEach(params -> config.planCalcScore().addActivityParams(params));

        return config;
    }

    public static Scenario loadScenario(Config config) {

        return ScenarioUtils.loadScenario(config);
    }

    public static Controler loadControler(Scenario scenario) {

        Controler controler = new Controler(scenario);
        if (!controler.getConfig().transit().isUsingTransitInMobsim())
            throw new RuntimeException("Public transit will be teleported and not simulated in the mobsim! "
                    + "This will have a significant effect on pt-related parameters (travel times, modal split, and so on). "
                    + "Should only be used for testing or car-focused studies with fixed modal split.");

        controler.addOverridingModule(new SwissRailRaptorModule());
        // use the (congested) car travel time for the teleported ride mode
        controler.addOverridingModule(new AbstractModule() {
            @Override
            public void install() {
                addTravelTimeBinding(TransportMode.ride).to(networkTravelTime());
                addTravelDisutilityFactoryBinding(TransportMode.ride).to(carTravelDisutilityFactoryKey());
            }
        });
        return controler;
    }

    private static VehicleType createVehicleType(String id, double length, double maxV, double pce, VehiclesFactory factory) {
        var vehicleType = factory.createVehicleType(Id.create(id, VehicleType.class));
        vehicleType.setNetworkMode(id);
        vehicleType.setPcuEquivalents(pce);
        vehicleType.setLength(length);
        vehicleType.setMaximumVelocity(maxV);
        vehicleType.setWidth(1.0);
        return vehicleType;
    }
}