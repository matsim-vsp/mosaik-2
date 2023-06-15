package org.matsim.mosaik2.analysis.run;

import org.matsim.core.config.ConfigUtils;
import org.matsim.core.population.PopulationUtils;

import java.util.Random;

public class Bla {

	public static void main(String[] args) {

			var rand = new Random();
			var pop = PopulationUtils.readPopulation("");
			var out = PopulationUtils.createPopulation(ConfigUtils.createConfig());
			pop.getPersons().values().stream()
					.filter(p -> rand.nextDouble() < 0.001)
					.forEach(out::addPerson);

			PopulationUtils.writePopulation(out, "");

	}

}