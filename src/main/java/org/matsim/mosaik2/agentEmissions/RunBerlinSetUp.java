package org.matsim.mosaik2.agentEmissions;

import com.beust.jcommander.JCommander;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.prep.PreparedGeometry;
import org.locationtech.jts.geom.prep.PreparedGeometryFactory;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.contrib.emissions.HbefaVehicleCategory;
import org.matsim.contrib.emissions.PositionEmissionsModule;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.controler.AbstractModule;
import org.matsim.core.controler.OutputDirectoryHierarchy;
import org.matsim.core.events.EventsManagerImpl;
import org.matsim.core.utils.geometry.CoordinateTransformation;
import org.matsim.core.utils.geometry.geotools.MGC;
import org.matsim.core.utils.geometry.transformations.TransformationFactory;
import org.matsim.run.RunBerlinScenario;
import org.matsim.vehicles.VehicleUtils;
import org.matsim.vis.snapshotwriters.SnapshotWritersModule;

import javax.inject.Singleton;
import java.nio.file.Paths;

public class RunBerlinSetUp {

    private static final CoordinateTransformation transformation = TransformationFactory.getCoordinateTransformation("EPSG:31468", "EPSG:25833");
    private static final String configPath = "matsim/scenarios/countries/de/berlin/berlin-v5.5-1pct/input/berlin-v5.5-1pct.config.xml";

    public static void main(String[] args) {

        var sharedSvn = new Utils.SharedSvnArg();
        var publicSvn = new Utils.PublicSvnArg();
        JCommander.newBuilder()
                .addObject(sharedSvn)
                .addObject(publicSvn)
                .build().parse(args);

        var emissionsConfig = Utils.createUpEmissionsConfigGroup(sharedSvn.getSharedSvn());
        var positionEmissionNetcdfConfig = Utils.createNetcdfEmissionWriterConfigGroup();
        var config = RunBerlinScenario.prepareConfig(
                new String[]{Paths.get(publicSvn.getPublicSvn()).resolve(configPath).toString()},
                emissionsConfig, positionEmissionNetcdfConfig);

        config.controler().setOverwriteFileSetting(OutputDirectoryHierarchy.OverwriteFileSetting.deleteDirectoryIfExists);
        config.controler().setOutputDirectory("test/output/berlin-position-emissions");
        config.controler().setLastIteration(1);

        Utils.applySnapshotSettings(config, 1);

        var scenario = RunBerlinScenario.prepareScenario(config);

        // work with UTM-33
        applyCoordinateTransformation(scenario, transformation);
        Utils.applyNetworkAttributes(scenario.getNetwork());
        applyNetworkFilter(scenario.getNetwork());

        // add engine information
        for (var vehicleType : scenario.getVehicles().getVehicleTypes().values()) {
            Utils.applyVehicleInformation(vehicleType);

            if (vehicleType.getId().toString().equals("freight")) {
                VehicleUtils.setHbefaVehicleCategory(vehicleType.getEngineInformation(), HbefaVehicleCategory.HEAVY_GOODS_VEHICLE.toString());
            }
        }

        for (var transitVehicleType : scenario.getTransitVehicles().getVehicleTypes().values()) {
            Utils.applyVehicleInformation(transitVehicleType);
            VehicleUtils.setHbefaVehicleCategory(transitVehicleType.getEngineInformation(), HbefaVehicleCategory.NON_HBEFA_VEHICLE.toString());
        }

        // the following two options make vehicles travel on the center of a link without offsett to the right
        config.qsim().setLinkWidthForVis(0);
        scenario.getNetwork().setEffectiveLaneWidth(0);


        var controler = RunBerlinScenario.prepareControler(scenario);

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

        // write things to a csv file so we can look at it in via
        var folder = config.controler().getOutputDirectory() + "/ITERS/it." + config.controler().getLastIteration() + "/";
        var it = config.controler().getLastIteration();
        AgentEmissionNetCdfReader.translateToCsv(folder + it + ".position-emissions.nc", folder + it + ".position-emissions-vehicleIdIndex.csv", config.controler().getOutputDirectory() + "/position-emission-no2.csv");
    }

    private static void applyCoordinateTransformation(Scenario scenario, CoordinateTransformation transformation) {
        scenario.getNetwork().getNodes().values().parallelStream()
                .forEach(node -> node.setCoord(transformation.transform(node.getCoord())));

        scenario.getPopulation().getPersons().values().parallelStream()
                .flatMap(person -> person.getPlans().stream())
                .flatMap(plan -> plan.getPlanElements().stream())
                .filter(element -> element instanceof Activity)
                .map(element -> (Activity) element)
                .filter(activity -> activity.getCoord() != null)
                .forEach(activity -> activity.setCoord(transformation.transform(activity.getCoord())));

        if (!scenario.getActivityFacilities().getFacilities().isEmpty()) {
            scenario.getActivityFacilities().getFacilities().values().parallelStream()
                    .filter(facility -> facility.getCoord() != null)
                    .forEach(facility -> facility.setCoord(transformation.transform(facility.getCoord())));
        }

        scenario.getTransitSchedule().getFacilities().values().parallelStream()
                .filter(transitStopFacility -> transitStopFacility.getCoord() != null)
                .forEach(tf -> tf.setCoord(transformation.transform(tf.getCoord())));
    }

    private static void applyNetworkFilter(Network network) {

        var bbox = createBoundingBox();
        network.getLinks().values().parallelStream()
                .filter(link -> isCoveredBy(link, bbox))
                .forEach(link -> link.getAttributes().putAttribute(SnapshotWritersModule.GENERATE_SNAPSHOT_FOR_LINK_KEY, ""));
    }

    private static PreparedGeometry createBoundingBox() {

        var geometry = new GeometryFactory().createPolygon(
                new Coordinate[]{
                        new Coordinate(385030.5, 5818413.0), new Coordinate(385030.5, 5820459),
                        new Coordinate(387076.5, 5820459), new Coordinate(385030.5, 5820459),
                        new Coordinate(385030.5, 5818413.0)
                }
        );
        return new PreparedGeometryFactory().create(geometry);
    }


    private static boolean isCoveredBy(Link link, PreparedGeometry geometry) {
        return geometry.covers(MGC.coord2Point(link.getFromNode().getCoord()))
                && geometry.covers(MGC.coord2Point(link.getToNode().getCoord()));
    }
}
