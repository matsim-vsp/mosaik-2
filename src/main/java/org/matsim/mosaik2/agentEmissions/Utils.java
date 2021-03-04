package org.matsim.mosaik2.agentEmissions;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.contrib.emissions.HbefaVehicleCategory;
import org.matsim.contrib.emissions.Pollutant;
import org.matsim.contrib.emissions.utils.EmissionsConfigGroup;
import org.matsim.core.config.Config;
import org.matsim.core.config.groups.ControlerConfigGroup;
import org.matsim.core.config.groups.QSimConfigGroup;
import org.matsim.vehicles.EngineInformation;
import org.matsim.vehicles.Vehicle;
import org.matsim.vehicles.VehicleType;
import org.matsim.vehicles.VehicleUtils;

import java.util.Map;
import java.util.Set;

public class Utils {

    static EmissionsConfigGroup createUpEmissionsConfigGroup() {
        var emissionConfig = new EmissionsConfigGroup();
        emissionConfig.setHbefaVehicleDescriptionSource(EmissionsConfigGroup.HbefaVehicleDescriptionSource.asEngineInformationAttributes);
        emissionConfig.setDetailedVsAverageLookupBehavior(EmissionsConfigGroup.DetailedVsAverageLookupBehavior.tryDetailedThenTechnologyAverageThenAverageTable);
        // vsp default is to let shared-svn reside next to all git projects in the git directory. This should find the mosaik-2 project folder and go from there to shared-svn
        emissionConfig.setDetailedColdEmissionFactorsFile( System.getProperty("user.dir") + "/../shared-svn/projects/matsim-germany/hbefa/hbefa-files/v4.1/EFA_ColdStart_Concept_2020_detailed_perTechAverage_Bln_carOnly.csv");
        emissionConfig.setDetailedWarmEmissionFactorsFile( System.getProperty("user.dir") + "/../shared-svn/projects/matsim-germany/hbefa/hbefa-files/v4.1/EFA_HOT_Concept_2020_detailed_perTechAverage_Bln_carOnly.csv");
        emissionConfig.setAverageColdEmissionFactorsFile( System.getProperty("user.dir") + "/../shared-svn/projects/matsim-germany/hbefa/hbefa-files/v4.1/EFA_ColdStart_Vehcat_2020_Average.csv");
        emissionConfig.setAverageWarmEmissionFactorsFile( System.getProperty("user.dir") + "/../shared-svn/projects/matsim-germany/hbefa/hbefa-files/v4.1/EFA_HOT_Vehcat_2020_Average.csv");
        emissionConfig.setHbefaRoadTypeSource(EmissionsConfigGroup.HbefaRoadTypeSource.fromLinkAttributes);
        return emissionConfig;
    }

    static PositionEmissionNetcdfModule.NetcdfEmissionWriterConfig createNetcdfEmissionWriterConfigGroup() {
        var netcdfWriterConfig = new PositionEmissionNetcdfModule.NetcdfEmissionWriterConfig();
        netcdfWriterConfig.setPollutants(Map.of(
                Pollutant.NO2, "NO2",
                Pollutant.CO2_TOTAL, "CO2",
                Pollutant.PM, "PM10",
                Pollutant.CO, "CO",
                Pollutant.NOx, "NOx"
        ));
        netcdfWriterConfig.setCalculateNOFromNOxAndNO2(true);
        return netcdfWriterConfig;
    }

    static void applySnapshotSettings(Config config) {
        // activate snapshots
        config.qsim().setSnapshotPeriod(1);
        config.qsim().setSnapshotStyle(QSimConfigGroup.SnapshotStyle.kinematicWaves);
        config.qsim().setLinkWidthForVis(0);
        config.controler().setWriteSnapshotsInterval(1);
        config.controler().setSnapshotFormat(Set.of(ControlerConfigGroup.SnapshotFormat.positionevents));
        config.controler().setFirstIteration(0);
        config.controler().setLastIteration(0);
        // we want simstepparalleleventsmanagerimpl
        config.parallelEventHandling().setSynchronizeOnSimSteps(true);
    }

    static void applyNetworkAttributes(Network network) {
        // network
        for (Link link : network.getLinks().values()) {

            double freespeed;

            if (link.getFreespeed() <= 13.888889) {
                freespeed = link.getFreespeed() * 2;
                // for non motorway roads, the free speed level was reduced
            } else {
                freespeed = link.getFreespeed();
                // for motorways, the original speed levels seems ok.
            }

            if (freespeed <= 8.333333333) { //30kmh
                link.getAttributes().putAttribute("hbefa_road_type", "URB/Access/30");
            } else if (freespeed <= 11.111111111) { //40kmh
                link.getAttributes().putAttribute("hbefa_road_type", "URB/Access/40");
            } else if (freespeed <= 13.888888889) { //50kmh
                double lanes = link.getNumberOfLanes();
                if (lanes <= 1.0) {
                    link.getAttributes().putAttribute("hbefa_road_type", "URB/Local/50");
                } else if (lanes <= 2.0) {
                    link.getAttributes().putAttribute("hbefa_road_type", "URB/Distr/50");
                } else if (lanes > 2.0) {
                    link.getAttributes().putAttribute("hbefa_road_type", "URB/Trunk-City/50");
                } else {
                    throw new RuntimeException("NoOfLanes not properly defined");
                }
            } else if (freespeed <= 16.666666667) { //60kmh
                double lanes = link.getNumberOfLanes();
                if (lanes <= 1.0) {
                    link.getAttributes().putAttribute("hbefa_road_type", "URB/Local/60");
                } else if (lanes <= 2.0) {
                    link.getAttributes().putAttribute("hbefa_road_type", "URB/Trunk-City/60");
                } else if (lanes > 2.0) {
                    link.getAttributes().putAttribute("hbefa_road_type", "URB/MW-City/60");
                } else {
                    throw new RuntimeException("NoOfLanes not properly defined");
                }
            } else if (freespeed <= 19.444444444) { //70kmh
                link.getAttributes().putAttribute("hbefa_road_type", "URB/MW-City/70");
            } else if (freespeed <= 22.222222222) { //80kmh
                link.getAttributes().putAttribute("hbefa_road_type", "URB/MW-Nat./80");
            } else if (freespeed > 22.222222222) { //faster
                link.getAttributes().putAttribute("hbefa_road_type", "RUR/MW/>130");
            } else {
                throw new RuntimeException("Link not considered...");
            }
        }
    }

    static void applyVehicleInformation(VehicleType vehicleType) {

        EngineInformation engineInformation = vehicleType.getEngineInformation();
        VehicleUtils.setHbefaVehicleCategory( engineInformation, HbefaVehicleCategory.PASSENGER_CAR.toString());
        VehicleUtils.setHbefaTechnology( engineInformation, "average" );
        VehicleUtils.setHbefaSizeClass( engineInformation, "average" );
        VehicleUtils.setHbefaEmissionsConcept( engineInformation, "average" );
    }

    public static void createAndAddVehicles(Scenario scenario, VehicleType type) {

        for (var person : scenario.getPopulation().getPersons().values()) {
            Vehicle vehicle = VehicleUtils.createVehicle(Id.createVehicleId(person.getId().toString() + "_car"), type);
            scenario.getVehicles().addVehicle(vehicle);
            VehicleUtils.insertVehicleIdsIntoAttributes(person, Map.of("car", vehicle.getId()));
        }
    }
}
