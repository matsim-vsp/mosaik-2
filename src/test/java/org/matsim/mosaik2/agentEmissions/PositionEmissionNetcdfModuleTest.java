package org.matsim.mosaik2.agentEmissions;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.event.EventUtils;
import org.junit.Rule;
import org.junit.Test;
import org.matsim.api.core.v01.events.Event;
import org.matsim.contrib.emissions.PositionEmissionsModule;
import org.matsim.contrib.emissions.utils.EmissionsConfigGroup;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.controler.AbstractModule;
import org.matsim.core.controler.Controler;
import org.matsim.core.events.EventsManagerImpl;
import org.matsim.core.events.EventsUtils;
import org.matsim.core.events.handler.BasicEventHandler;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.testcases.MatsimTestUtils;

import javax.inject.Singleton;
import java.lang.module.Configuration;
import java.util.Set;

import static org.junit.Assert.*;

@Log4j2
public class

PositionEmissionNetcdfModuleTest {

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

        var folder = testUtils.getOutputDirectory() + "ITERS/it." + config.controler().getLastIteration() + "/";
        var it = config.controler().getLastIteration();

        var netCdfReader = new AgentEmissionNetCdfReader();
        netCdfReader.read(folder + it + ".position-emissions.nc", folder + it + ".position-emissions-vehicleIdIndex.csv");
        var netcdfResult = netCdfReader.getEventRecord();

        var manager = EventsUtils.createEventsManager();
        var handler = new PositionEmissionRecordHandler(netcdfResult);
        manager.addHandler(handler);
        EventsUtils.readEvents(manager, folder + it + ".events.xml.gz");

        assertTrue(netcdfResult.containsAll(handler.getExpectedRecords()));
    }

    @RequiredArgsConstructor
    private static class PositionEmissionRecordHandler implements BasicEventHandler {

        @Getter
        private final Set<String> expectedRecords;

        @Override
        public void handleEvent(Event event) {
            if (event.getEventType().equals(PositionEmissionsModule.PositionEmissionEvent.EVENT_TYPE)) {

                if (event.getAttributes().get("emissionType").equals("cold")) return; // ignore cold events for now, but think about it later

                var record = "time=" + event.getTime() +
                        "vehicleId=" + event.getAttributes().get("vehicleId");

                expectedRecords.add(record);
            }
        }
    }

}