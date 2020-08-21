package org.matsim.mosaik2.prepare;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.population.io.PopulationReader;
import org.matsim.core.population.io.PopulationWriter;
import org.matsim.core.scenario.ScenarioUtils;

import java.nio.file.Paths;

public class CleanPopulation {

    private static final String inputPopulation = "projects\\mosaik-2\\matsim-input-files\\stuttgart-inkl-umland\\optimizedPopulation.xml.gz";
    private static final String outputPopulation = "projects\\mosaik-2\\matsim-input-files\\stuttgart-inkl-umland-vsp\\population-25pct-stuttgart.xml.gz";

    public static void main(String[] args) {

        var arguments = new InputArgs();
        JCommander.newBuilder().addObject(arguments).build().parse(args);
        var svn = Paths.get(arguments.sharedSvn);

        var scenario = ScenarioUtils.createMutableScenario(ConfigUtils.createConfig());
        new PopulationReader(scenario).readFile(svn.resolve(inputPopulation).toString());

        scenario.getPopulation().getPersons().values().parallelStream()
                .forEach(person -> {

                    var plan = person.getSelectedPlan();
                    plan.setScore(null);

                    // clear plans
                    person.getPlans().clear();
                    person.setSelectedPlan(null);

                    // remove network information of plan elements
                    for (PlanElement element : plan.getPlanElements()) {

                        if (element instanceof Activity) {
                            var activity = (Activity) element;
                            activity.setFacilityId(null);
                            activity.setLinkId(null);
                        } else if (element instanceof Leg) {
                            var leg = (Leg) element;
                            leg.setRoute(null);
                        }
                    }

                    // will also set the plan to be selected plan
                    person.addPlan(plan);
                });

        new PopulationWriter(scenario.getPopulation()).write(svn.resolve(outputPopulation).toString());
    }

    private static class InputArgs {

        @Parameter(names = {"-sharedSvn"}, required = true)
        String sharedSvn = "https://svn.vsp.tu-berlin.de/repos/shared-svn/";
    }
}
