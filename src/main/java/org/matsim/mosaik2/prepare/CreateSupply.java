package org.matsim.mosaik2.prepare;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.scenario.ScenarioUtils;

import java.nio.file.Paths;

public class CreateSupply {

	public static void main(String[] args) {

		var arguments = new InputArgs();
		JCommander.newBuilder().addObject(arguments).build().parse(args);
		var svn = Paths.get(arguments.sharedSvn);

		// parse network from osm
		var network = CreateNetwork.createNetwork(svn);

		var scenario = ScenarioUtils.createMutableScenario(ConfigUtils.createConfig());
		scenario.setNetwork(network);
		CreatePt.createSchedule(scenario, svn);
		CreatePt.writeScheduleAndVehicles(scenario, svn);
		CreateNetwork.writeNetwork(scenario.getNetwork(), svn);
	}

	private static class InputArgs {

		@Parameter(names = {"-sharedSvn"}, required = true)
		String sharedSvn = "https://svn.vsp.tu-berlin.de/repos/shared-svn/";
	}
}
