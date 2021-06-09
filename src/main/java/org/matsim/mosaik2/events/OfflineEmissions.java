package org.matsim.mosaik2.events;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
import org.matsim.contrib.emissions.EmissionModule;
import org.matsim.contrib.emissions.HbefaVehicleCategory;
import org.matsim.contrib.emissions.utils.EmissionsConfigGroup;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.controler.AbstractModule;
import org.matsim.core.controler.Injector;
import org.matsim.core.events.EventsUtils;
import org.matsim.core.events.MatsimEventsReader;
import org.matsim.core.events.algorithms.EventWriterXML;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.vehicles.EngineInformation;
import org.matsim.vehicles.VehicleType;
import org.matsim.vehicles.VehicleUtils;

public class OfflineEmissions {

    public static void main(String[] args) {

        if (args.length != 1) {
            throw new RuntimeException("path to config required");
        }

        var config = ConfigUtils.loadConfig(args[0]);

        var configGroup = new EmissionsConfigGroup();
        config.addModule(configGroup);

        configGroup.setDetailedVsAverageLookupBehavior(EmissionsConfigGroup.DetailedVsAverageLookupBehavior.directlyTryAverageTable);
        configGroup.setHbefaRoadTypeSource(EmissionsConfigGroup.HbefaRoadTypeSource.fromLinkAttributes);
        configGroup.setAverageColdEmissionFactorsFile("C:\\Users\\Janekdererste\\repos\\shared-svn\\projects\\matsim-germany\\hbefa\\hbefa-files\\v4.1\\EFA_ColdStart_Vehcat_2020_Average.csv");
        configGroup.setAverageWarmEmissionFactorsFile("C:\\Users\\Janekdererste\\repos\\shared-svn\\projects\\matsim-germany\\hbefa\\hbefa-files\\v4.1\\EFA_HOT_Vehcat_2020_Average.csv");
        configGroup.setNonScenarioVehicles(EmissionsConfigGroup.NonScenarioVehicles.ignore);

        // we need the generated vehicles of the matsim run
        config.vehicles().setVehiclesFile("Z:\\berlin\\output-berlin-v5.5.2-10pct-last-iteration\\berlin-v5.5-10pct.output_vehicles.xml.gz");

        var scenario = ScenarioUtils.loadScenario(config);

        for (Link link : scenario.getNetwork().getLinks().values()) {

            double freespeed;

            if (link.getFreespeed() <= 13.888889) {
                freespeed = link.getFreespeed() * 2;
                // for non motorway roads, the free speed level was reduced
            } else {
                freespeed = link.getFreespeed();
                // for motorways, the original speed levels seems ok.
            }

            if(freespeed <= 8.333333333){ //30kmh
                link.getAttributes().putAttribute("hbefa_road_type", "URB/Access/30");
            } else if(freespeed <= 11.111111111){ //40kmh
                link.getAttributes().putAttribute("hbefa_road_type", "URB/Access/40");
            } else if(freespeed <= 13.888888889){ //50kmh
                double lanes = link.getNumberOfLanes();
                if(lanes <= 1.0){
                    link.getAttributes().putAttribute("hbefa_road_type", "URB/Local/50");
                } else if(lanes <= 2.0){
                    link.getAttributes().putAttribute("hbefa_road_type", "URB/Distr/50");
                } else if(lanes > 2.0){
                    link.getAttributes().putAttribute("hbefa_road_type", "URB/Trunk-City/50");
                } else{
                    throw new RuntimeException("NoOfLanes not properly defined");
                }
            } else if(freespeed <= 16.666666667){ //60kmh
                double lanes = link.getNumberOfLanes();
                if(lanes <= 1.0){
                    link.getAttributes().putAttribute("hbefa_road_type", "URB/Local/60");
                } else if(lanes <= 2.0){
                    link.getAttributes().putAttribute("hbefa_road_type", "URB/Trunk-City/60");
                } else if(lanes > 2.0){
                    link.getAttributes().putAttribute("hbefa_road_type", "URB/MW-City/60");
                } else{
                    throw new RuntimeException("NoOfLanes not properly defined");
                }
            } else if(freespeed <= 19.444444444){ //70kmh
                link.getAttributes().putAttribute("hbefa_road_type", "URB/MW-City/70");
            } else if(freespeed <= 22.222222222){ //80kmh
                link.getAttributes().putAttribute("hbefa_road_type", "URB/MW-Nat./80");
            } else if(freespeed > 22.222222222){ //faster
                link.getAttributes().putAttribute("hbefa_road_type", "RUR/MW/>130");
            } else{
                throw new RuntimeException("Link not considered...");
            }
        }



       /* var carVehicleType = VehicleUtils.createVehicleType(carVehicleTypeId);
        carVehicleType.setMaximumVelocity(100/3.6);
        carVehicleType.setNetworkMode(TransportMode.car);
        carVehicleType.setLength(7.5);
        carVehicleType.setPcuEquivalents(1.0);


        */
        Id<VehicleType> carVehicleTypeId = Id.create("car", VehicleType.class);
        var carVehicleType = scenario.getVehicles().getVehicleTypes().get(carVehicleTypeId);
        Id<VehicleType> freightVehicleTypeId = Id.create("freight", VehicleType.class);
        VehicleType freightVehicleType = scenario.getVehicles().getVehicleTypes().get(freightVehicleTypeId);

        EngineInformation carEngineInformation = carVehicleType.getEngineInformation();
        VehicleUtils.setHbefaVehicleCategory( carEngineInformation, HbefaVehicleCategory.PASSENGER_CAR.toString());
        VehicleUtils.setHbefaTechnology( carEngineInformation, "average" );
        VehicleUtils.setHbefaSizeClass( carEngineInformation, "average" );
        VehicleUtils.setHbefaEmissionsConcept( carEngineInformation, "average" );

        EngineInformation freightEngineInformation = freightVehicleType.getEngineInformation();
        VehicleUtils.setHbefaVehicleCategory( freightEngineInformation, HbefaVehicleCategory.HEAVY_GOODS_VEHICLE.toString());
        VehicleUtils.setHbefaTechnology( freightEngineInformation, "average" );
        VehicleUtils.setHbefaSizeClass( freightEngineInformation, "average" );
        VehicleUtils.setHbefaEmissionsConcept( freightEngineInformation, "average" );

        // public transit vehicles should be considered as non-hbefa vehicles
        for (VehicleType type : scenario.getTransitVehicles().getVehicleTypes().values()) {
            EngineInformation engineInformation = type.getEngineInformation();
            // TODO: Check! Is this a zero emission vehicle?!
            VehicleUtils.setHbefaVehicleCategory( engineInformation, HbefaVehicleCategory.NON_HBEFA_VEHICLE.toString());
            VehicleUtils.setHbefaTechnology( engineInformation, "average" );
            VehicleUtils.setHbefaSizeClass( engineInformation, "average" );
            VehicleUtils.setHbefaEmissionsConcept( engineInformation, "average" );
        }

        // the following is copy paste from the example...

        EventsManager eventsManager = EventsUtils.createEventsManager();

        AbstractModule module = new AbstractModule(){
            @Override
            public void install(){
                bind( Scenario.class ).toInstance( scenario );
                bind( EventsManager.class ).toInstance( eventsManager );
                bind( EmissionModule.class ) ;
            }
        };

        com.google.inject.Injector injector = Injector.createInjector(config, module);

        EmissionModule emissionModule = injector.getInstance(EmissionModule.class);

        EventWriterXML emissionEventWriter = new EventWriterXML("C:\\Users\\Janekdererste\\Desktop\\emissionEvents_berlin_10pct.xml.gz");
        emissionModule.getEmissionEventsManager().addHandler(emissionEventWriter);

        eventsManager.initProcessing();
        MatsimEventsReader matsimEventsReader = new MatsimEventsReader(eventsManager);
        matsimEventsReader.readFile("Z:\\berlin\\output-berlin-v5.5.2-10pct-last-iteration\\berlin-v5.5-10pct.output_events.xml.gz");
        eventsManager.finishProcessing();

        emissionEventWriter.closeFile();
    }
}
