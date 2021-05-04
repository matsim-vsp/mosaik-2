package org.matsim.mosaik2.agentEmissions;

import org.junit.Rule;
import org.junit.Test;
import org.matsim.contrib.emissions.PositionEmissionsModule;
import org.matsim.contrib.emissions.utils.EmissionsConfigGroup;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.controler.AbstractModule;
import org.matsim.core.controler.Controler;
import org.matsim.core.events.EventsManagerImpl;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.testcases.MatsimTestUtils;

import javax.inject.Singleton;
import java.lang.module.Configuration;

import static org.junit.Assert.*;

public class PositionEmissionNetcdfModuleTest {

    @Rule
    public MatsimTestUtils testUtils = new MatsimTestUtils();

    @Test
    public void testSimpleSetUp() {

        //--------------- prepare and run the set up -----------------------//
        var netCdfEmissionWriterConfigGroup = Utils.createNetcdfEmissionWriterConfigGroup();
        var config = ConfigUtils.loadConfig(testUtils.getClassInputDirectory() + "config.xml", new EmissionsConfigGroup(), netCdfEmissionWriterConfigGroup);
        config.controler().setOutputDirectory(testUtils.getOutputDirectory());

        var scenario = ScenarioUtils.loadScenario(config);

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

        //---------------- now load and compare the events and netcdf file---------------//
    }

}