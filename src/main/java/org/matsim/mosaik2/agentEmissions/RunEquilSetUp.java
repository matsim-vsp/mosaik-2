package org.matsim.mosaik2.agentEmissions;

import com.beust.jcommander.JCommander;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
import org.matsim.contrib.emissions.PositionEmissionsModule;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.QSimConfigGroup;
import org.matsim.core.controler.AbstractModule;
import org.matsim.core.controler.Controler;
import org.matsim.core.controler.OutputDirectoryHierarchy;
import org.matsim.core.events.EventsManagerImpl;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.geometry.CoordUtils;
import org.matsim.examples.ExamplesUtils;
import org.matsim.vehicles.VehicleType;

import javax.inject.Singleton;

public class RunEquilSetUp {

    public static void main(String[] args) {

        var arguments = new Utils.Args();
        JCommander.newBuilder().addObject(arguments).build().parse(args);

        var configPath = ExamplesUtils.getTestScenarioURL("equil").toString() + "config.xml";
        var emissionConfig = Utils.createUpEmissionsConfigGroup(arguments.getSharedSvn());
        var netcdfConfig = Utils.createNetcdfEmissionWriterConfigGroup();
        var config = ConfigUtils.loadConfig(configPath, emissionConfig, netcdfConfig);

        config.controler().setOverwriteFileSetting(OutputDirectoryHierarchy.OverwriteFileSetting.deleteDirectoryIfExists);
        config.controler().setOutputDirectory("test/output/position-emission");
        config.qsim().setVehiclesSource(QSimConfigGroup.VehiclesSource.modeVehicleTypesFromVehiclesData);
        Utils.applySnapshotSettings(config);

        config.controler().setLastIteration(10);

        var scenario = ScenarioUtils.loadScenario(config);

        // use euclidean length for links
        for (Link link : scenario.getNetwork().getLinks().values()) {
            link.setLength(CoordUtils.calcEuclideanDistance(link.getFromNode().getCoord(), link.getToNode().getCoord()));
            link.getAttributes().putAttribute("hbefa_road_type", "URB/Access/30");
        }

        Utils.applyNetworkAttributes(scenario.getNetwork());

        var vehicleType = scenario.getVehicles().getFactory().createVehicleType(Id.create(TransportMode.car, VehicleType.class));
        scenario.getVehicles().addVehicleType(vehicleType);
        Utils.applyVehicleInformation(vehicleType);

        //Utils.createAndAddVehicles(scenario, vehicleType);

        // the following two options make vehicles travel on the center of a link without offsett to the right
        config.qsim().setLinkWidthForVis(0);
        scenario.getNetwork().setEffectiveLaneWidth(0);

        var controler = new Controler(scenario);

        controler.addOverridingModule(new PositionEmissionsModule());
        controler.addOverridingModule(new PositionEmissionNetcdfModule());

        controler.addOverridingModule(new AbstractModule() {
            @Override
            public void install() {
                // we need single threaded events manager because other wise it doesn't work
                bind(EventsManager.class).to(EventsManagerImpl.class).in(Singleton.class);
            }
        });

        controler.run();

        var folder = config.controler().getOutputDirectory() + "/ITERS/it." + config.controler().getLastIteration() + "/";
        var it = config.controler().getLastIteration();
        AgentEmissionNetCdfReader.translateToCsv(folder + it + ".position-emissions.nc", folder + it + ".position-emissions-vehicleIdIndex.csv", config.controler().getOutputDirectory() + "/position-emission-no2.csv");
    }
}
