package org.matsim.mosaik2.agentEmissions;

import org.junit.Rule;
import org.junit.Test;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
import org.matsim.contrib.emissions.HbefaVehicleCategory;
import org.matsim.contrib.emissions.PositionEmissionsModule;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.QSimConfigGroup;
import org.matsim.core.controler.AbstractModule;
import org.matsim.core.controler.Controler;
import org.matsim.core.events.EventsManagerImpl;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.geometry.CoordUtils;
import org.matsim.examples.ExamplesUtils;
import org.matsim.testcases.MatsimTestUtils;
import org.matsim.vehicles.EngineInformation;
import org.matsim.vehicles.VehicleType;
import org.matsim.vehicles.VehicleUtils;
import ucar.ma2.InvalidRangeException;

import javax.inject.Singleton;
import java.io.IOException;

public class PositionEmissionToMovingSourcesTest {
    @Rule
    public MatsimTestUtils testUtils = new MatsimTestUtils();

    @Test
    public void testEquilSetup() throws InvalidRangeException, IOException {

        var configPath = ExamplesUtils.getTestScenarioURL("equil") + "config.xml";

        //--------------- prepare and run the set up -----------------------//
        var netCdfEmissionWriterConfigGroup = Utils.createNetcdfEmissionWriterConfigGroup();
        var emissionConfigGroup = Utils.createUpEmissionsConfigGroup("/Users/janek/repos/shared-svn");
        var config = ConfigUtils.loadConfig(configPath, emissionConfigGroup, netCdfEmissionWriterConfigGroup);
        Utils.applySnapshotSettings(config);
        config.controler().setOutputDirectory(testUtils.getOutputDirectory());
        config.controler().setLastIteration(0);
        config.qsim().setVehiclesSource(QSimConfigGroup.VehiclesSource.modeVehicleTypesFromVehiclesData);

        var scenario = ScenarioUtils.loadScenario(config);
        // the following two options make vehicles travel on the center of a link without offsett to the right
        config.qsim().setLinkWidthForVis(0);
        scenario.getNetwork().setEffectiveLaneWidth(0);

        var defaultVehicleType = VehicleUtils.createVehicleType(Id.create(TransportMode.car, VehicleType.class));
        EngineInformation carEngineInformation = defaultVehicleType.getEngineInformation();
        VehicleUtils.setHbefaVehicleCategory(carEngineInformation, HbefaVehicleCategory.PASSENGER_CAR.toString());
        VehicleUtils.setHbefaTechnology(carEngineInformation, "average");
        VehicleUtils.setHbefaSizeClass(carEngineInformation, "average");
        VehicleUtils.setHbefaEmissionsConcept(carEngineInformation, "average");

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
        var controler = new Controler(scenario);
        controler.addOverridingModule(new PositionEmissionsModule());
        controler.addOverridingModule(new AbstractModule() {
            @Override
            public void install() {
                // we need single threaded events manager because other wise it doesn't work
                bind(EventsManager.class).to(EventsManagerImpl.class).in(Singleton.class);
            }
        });
        controler.run();


        // now take the events file and convert it into netcdf

        PositionEmissionToMovingSources.main(new String[]{
                "-pee", testUtils.getOutputDirectory() + "output_events.xml.gz",
                "-n", testUtils.getOutputDirectory() + "output_network.xml.gz",
                "-netCdfOutput", testUtils.getOutputDirectory() + "output_positions.nc",
                "-s", "PM10",
                "-s", "NO"
        });
    }

}