package org.matsim.mosaik2.agentEmissions;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.QSimConfigGroup;
import org.matsim.core.controler.OutputDirectoryHierarchy;
import org.matsim.core.utils.geometry.CoordUtils;
import org.matsim.examples.ExamplesUtils;
import org.matsim.vehicles.VehicleType;
import org.matsim.vis.snapshotwriters.SnapshotWritersModule;

import java.nio.file.Paths;

public class RunEquilSetUp extends AbstractRunPositionEmissions{

	public static void main(String[] args) {
		new RunEquilSetUp().run(args);
	}

	void run(String[] args) {

		var config = ConfigUtils.loadConfig(ExamplesUtils.getTestScenarioURL("equil") + "config.xml", getConfigGroups());
		var hbefaConfig = (HbefaConfigGroup) config.getModules().get(HbefaConfigGroup.GROUP_NAME);
		hbefaConfig.setHbefaDirectory("C:\\Users\\Janek\\repos\\shared-svn\\projects\\matsim-germany\\hbefa\\hbefa-files\\v4.1");
		applyConfig(config);
		config.controler().setOverwriteFileSetting(OutputDirectoryHierarchy.OverwriteFileSetting.deleteDirectoryIfExists);
		config.transit().setUseTransit(true);
		config.controler().setLastIteration(10);
		config.qsim().setVehiclesSource(QSimConfigGroup.VehiclesSource.modeVehicleTypesFromVehiclesData);

		var scenario = loadScenario(config);
		// use euclidean length for links
		for (Link link : scenario.getNetwork().getLinks().values()) {
			link.setLength(CoordUtils.calcEuclideanDistance(link.getFromNode().getCoord(), link.getToNode().getCoord()));
			link.getAttributes().putAttribute("hbefa_road_type", "URB/Access/30");

			// only generate snapshots for link 6
			if (link.getId().equals(Id.createLinkId("6"))) {
				link.getAttributes().putAttribute(SnapshotWritersModule.GENERATE_SNAPSHOT_FOR_LINK_KEY, "");
			}
		}

		// not sure whether vehicle types are defined for this one.
		var vehicleType = scenario.getVehicles().getFactory().createVehicleType(Id.create("car", VehicleType.class));
		scenario.getVehicles().addVehicleType(vehicleType);
		Utils.applyVehicleInformation(vehicleType);

		var controler = loadControler(scenario);
		controler.run();

		var it = Paths.get(config.controler().getOutputDirectory()).resolve("ITERS").resolve("it." + config.controler().getLastIteration());
		var iteration = config.controler().getLastIteration();

		AgentEmissionNetCdfReader.translateToCsv(
				it.resolve(iteration + ".position-emissions.nc").toString(),
				it.resolve(iteration + ".position-emissions-vehicleIdIndex.csv").toString(),
				it.resolve(iteration + ".position-emission.csv").toString());
	}
}
