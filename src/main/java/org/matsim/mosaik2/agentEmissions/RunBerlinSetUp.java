package org.matsim.mosaik2.agentEmissions;

import org.locationtech.jts.geom.prep.PreparedGeometry;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.contrib.emissions.HbefaVehicleCategory;
import org.matsim.contrib.emissions.PositionEmissionsModule;
import org.matsim.core.config.groups.QSimConfigGroup;
import org.matsim.core.controler.OutputDirectoryHierarchy;
import org.matsim.core.utils.geometry.CoordinateTransformation;
import org.matsim.core.utils.geometry.transformations.TransformationFactory;
import org.matsim.run.RunBerlinScenario;
import org.matsim.vehicles.VehicleType;
import org.matsim.vehicles.VehicleUtils;

public class RunBerlinSetUp {

    private static final CoordinateTransformation transformation = TransformationFactory.getCoordinateTransformation("EPSG:31468", "EPSG:25833");
    public static void main(String[] args) {

        var emissionsConfig = Utils.createUpEmissionsConfigGroup();
        var positionEmissionNetcdfConfig = Utils.createNetcdfEmissionWriterConfigGroup();
        var config = RunBerlinScenario.prepareConfig(args, emissionsConfig, positionEmissionNetcdfConfig);

        config.controler().setOverwriteFileSetting(OutputDirectoryHierarchy.OverwriteFileSetting.deleteDirectoryIfExists);
        config.controler().setOutputDirectory("test/output/berlin-position-emissions");

        Utils.applySnapshotSettings(config);

        var scenario = RunBerlinScenario.prepareScenario(config);

        // work with UTM-33
        scenario.getNetwork().getNodes().values().parallelStream()
                .forEach(node -> node.setCoord(transformation.transform(node.getCoord())));

        scenario.getPopulation().getPersons().values().parallelStream()
                .flatMap(person -> person.getPlans().stream())
                .flatMap(plan -> plan.getPlanElements().stream())
                .filter(element -> element instanceof Activity)
                .map(element -> (Activity)element)
                .filter(activity -> activity.getCoord() != null)
                .forEach(activity -> activity.setCoord(transformation.transform(activity.getCoord())));

        if (!scenario.getActivityFacilities().getFacilities().isEmpty()) {
            scenario.getActivityFacilities().getFacilities().values().parallelStream()
                    .filter(facility -> facility.getCoord() != null)
                    .forEach(facility -> facility.setCoord(transformation.transform(facility.getCoord())));
        }



        //var vehicleType = scenario.getVehicles().getFactory().createVehicleType(Id.create("average-vehicle", VehicleType.class));
        //scenario.getVehicles().addVehicleType(vehicleType);
        //Utils.applyVehicleInformation(vehicleType);

        //Utils.createAndAddVehicles(scenario, vehicleType);

        for (var vehicleType : scenario.getVehicles().getVehicleTypes().values()) {
            Utils.applyVehicleInformation(vehicleType);

            if (vehicleType.getId().toString().equals("freight")) {
                VehicleUtils.setHbefaVehicleCategory( vehicleType.getEngineInformation(), HbefaVehicleCategory.HEAVY_GOODS_VEHICLE.toString());
            }
        }

        for (var transitVehicleType : scenario.getTransitVehicles().getVehicleTypes().values()) {
            Utils.applyVehicleInformation(transitVehicleType);
            VehicleUtils.setHbefaVehicleCategory( transitVehicleType.getEngineInformation(), HbefaVehicleCategory.NON_HBEFA_VEHICLE.toString());
        }


        var controler = RunBerlinScenario.prepareControler(scenario);

        controler.addOverridingModule(new PositionEmissionsModule());
        controler.addOverridingModule(new PositionEmissionNetcdfModule());

        controler.run();
    }

   /* private PreparedGeometry createBoundingBox() {


        //var origin = new Coord(385029.5, 5818412.0);
    }

    */
}
