package org.matsim.mosaik2.agentEmissions;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.contrib.emissions.EmissionModule;
import org.matsim.contrib.emissions.PositionEmissionsModule;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.QSimConfigGroup;
import org.matsim.core.controler.Controler;
import org.matsim.core.controler.OutputDirectoryHierarchy;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.examples.ExamplesUtils;
import org.matsim.vehicles.Vehicle;
import org.matsim.vehicles.VehicleType;
import org.matsim.vehicles.VehicleUtils;

import java.util.Map;

public class RunEquilSetUp {

    public static void main(String[] args) {

        var configPath = ExamplesUtils.getTestScenarioURL("equil").toString() + "/config.xml";
        var emissionConfig = Utils.createUpEmissionsConfigGroup();
        var netcdfConfig = Utils.createNetcdfEmissionWriterConfigGroup();
        var config = ConfigUtils.loadConfig(configPath, emissionConfig, netcdfConfig);

        config.controler().setOverwriteFileSetting(OutputDirectoryHierarchy.OverwriteFileSetting.deleteDirectoryIfExists);
        config.controler().setOutputDirectory("test/output/position-emission");
        config.qsim().setVehiclesSource(QSimConfigGroup.VehiclesSource.fromVehiclesData);
        Utils.applySnapshotSettings(config);

        config.controler().setLastIteration(10);


        var scenario = ScenarioUtils.loadScenario(config);
        Utils.applyNetworkAttributes(scenario.getNetwork());

        var vehicleType = scenario.getVehicles().getFactory().createVehicleType(Id.create("average-vehicle", VehicleType.class));
        scenario.getVehicles().addVehicleType(vehicleType);
        Utils.applyVehicleInformation(vehicleType);

        Utils.createAndAddVehicles(scenario, vehicleType);
        var controler = new Controler(scenario);

        controler.addOverridingModule(new PositionEmissionsModule());
        controler.addOverridingModule(new PositionEmissionNetcdfModule());



        controler.run();
    }
}
