package org.matsim.mosaik2.agentEmissions;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.event.EventUtils;
import org.junit.Rule;
import org.junit.Test;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.events.Event;
import org.matsim.api.core.v01.network.Link;
import org.matsim.contrib.emissions.HbefaVehicleCategory;
import org.matsim.contrib.emissions.Pollutant;
import org.matsim.contrib.emissions.PositionEmissionsModule;
import org.matsim.contrib.emissions.utils.EmissionsConfigGroup;
import org.matsim.contrib.otfvis.OTFVisLiveModule;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.QSimConfigGroup;
import org.matsim.core.controler.AbstractModule;
import org.matsim.core.controler.Controler;
import org.matsim.core.events.EventsManagerImpl;
import org.matsim.core.events.EventsUtils;
import org.matsim.core.events.handler.BasicEventHandler;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.geometry.CoordUtils;
import org.matsim.examples.ExamplesUtils;
import org.matsim.testcases.MatsimTestUtils;
import org.matsim.vehicles.EngineInformation;
import org.matsim.vehicles.VehicleType;
import org.matsim.vehicles.VehicleUtils;

import javax.inject.Singleton;
import java.lang.module.Configuration;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

import static org.junit.Assert.*;

@Log4j2
public class

PositionEmissionNetcdfModuleTest {

    @Rule
    public MatsimTestUtils testUtils = new MatsimTestUtils();

    @Test
    public void testSimpleSetUp() {

        runTest(testUtils.getClassInputDirectory() + "config.xml", scenario -> {

            var defaultVehicleType = VehicleUtils.createVehicleType(Id.create(TransportMode.car, VehicleType.class));
            EngineInformation carEngineInformation = defaultVehicleType.getEngineInformation();
            VehicleUtils.setHbefaVehicleCategory( carEngineInformation, HbefaVehicleCategory.PASSENGER_CAR.toString());
            VehicleUtils.setHbefaTechnology( carEngineInformation, "average" );
            VehicleUtils.setHbefaSizeClass( carEngineInformation, "average" );
            VehicleUtils.setHbefaEmissionsConcept( carEngineInformation, "average" );

            scenario.getVehicles().addVehicleType(defaultVehicleType);

            // use euclidean length for links
            for (Link link : scenario.getNetwork().getLinks().values()) {
                link.getAttributes().putAttribute("hbefa_road_type", "URB/Access/30");
            }
        });
    }

    @Test
    public void testEquilSetup() {
        var configPath = ExamplesUtils.getTestScenarioURL("equil") + "config.xml";
        runTest(configPath, scenario -> {

            var defaultVehicleType = VehicleUtils.createVehicleType(Id.create(TransportMode.car, VehicleType.class));
            EngineInformation carEngineInformation = defaultVehicleType.getEngineInformation();
            VehicleUtils.setHbefaVehicleCategory( carEngineInformation, HbefaVehicleCategory.PASSENGER_CAR.toString());
            VehicleUtils.setHbefaTechnology( carEngineInformation, "average" );
            VehicleUtils.setHbefaSizeClass( carEngineInformation, "average" );
            VehicleUtils.setHbefaEmissionsConcept( carEngineInformation, "average" );

            scenario.getVehicles().addVehicleType(defaultVehicleType);

            // use euclidean length for links
            for (Link link : scenario.getNetwork().getLinks().values()) {
                link.setLength(CoordUtils.calcEuclideanDistance(link.getFromNode().getCoord(), link.getToNode().getCoord()));
                link.getAttributes().putAttribute("hbefa_road_type", "URB/Access/30");
            }

            // use only one agent
           /* var person1 = scenario.getPopulation().getPersons().get(Id.createPersonId(1));
            scenario.getPopulation().getPersons().clear();
            scenario.getPopulation().addPerson(person1);

            */
        });
    }

    private void runTest(String configPath, Consumer<Scenario> scenarioLoaded) {

        //--------------- prepare and run the set up -----------------------//
        var netCdfEmissionWriterConfigGroup = Utils.createNetcdfEmissionWriterConfigGroup();
        var emissionConfigGroup = Utils.createUpEmissionsConfigGroup("C:\\Users\\Janekdererste\\repos\\shared-svn");
        var config = ConfigUtils.loadConfig(configPath, emissionConfigGroup, netCdfEmissionWriterConfigGroup);
        Utils.applySnapshotSettings(config);
        config.controler().setOutputDirectory(testUtils.getOutputDirectory());

       // config.controler().setLastIteration(0);
        config.qsim().setVehiclesSource(QSimConfigGroup.VehiclesSource.modeVehicleTypesFromVehiclesData);

        var scenario = ScenarioUtils.loadScenario(config);

        // the following two options make vehicles travel on the center of a link without offsett to the right
        config.qsim().setLinkWidthForVis(0);
        scenario.getNetwork().setEffectiveLaneWidth(0);
        scenarioLoaded.accept(scenario);


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

        controler.addOverridingModule(new OTFVisLiveModule());

        controler.run();

        //---------------- now load and compare the events and netcdf file---------------//

        var folder = testUtils.getOutputDirectory() + "ITERS/it." + config.controler().getLastIteration() + "/";
        var it = config.controler().getLastIteration();

        var netCdfResult = AgentEmissionNetCdfReader.readToRecord(folder + it + ".position-emissions.nc", folder + it + ".position-emissions-vehicleIdIndex.csv");

        var manager = EventsUtils.createEventsManager();
        var handler = new PositionEmissionRecordHandler();
        manager.addHandler(handler);
        EventsUtils.readEvents(manager, folder + it + ".events.xml.gz");

        assertTrue(netCdfResult.containsAll(handler.getExpectedRecords()));

        AgentEmissionNetCdfReader.translateToCsv(folder + it + ".position-emissions.nc", folder + it + ".position-emissions-vehicleIdIndex.csv", testUtils.getOutputDirectory() + "position-emission-no2.csv");
    }

    @RequiredArgsConstructor
    private static class PositionEmissionRecordHandler implements BasicEventHandler {

        @Getter
        private final Set<String> expectedRecords = new HashSet<>();

        @Override
        public void handleEvent(Event event) {
            if (event.getEventType().equals(PositionEmissionsModule.PositionEmissionEvent.EVENT_TYPE)) {

                if (event.getAttributes().get("emissionType").equals("cold")) return; // ignore cold events for now, but think about it later

                var record = "time=" + event.getTime() +
                        ",vehicleId=" + event.getAttributes().get("vehicleId") +
                        ",x=" + event.getAttributes().get("x") +
                        ",y=" + event.getAttributes().get("y") +
                        ",no2=" + event.getAttributes().get(Pollutant.NO2.toString());
                expectedRecords.add(record);
            }
        }
    }

}