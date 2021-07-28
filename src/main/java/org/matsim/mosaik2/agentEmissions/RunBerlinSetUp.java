package org.matsim.mosaik2.agentEmissions;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.prep.PreparedGeometry;
import org.locationtech.jts.geom.prep.PreparedGeometryFactory;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.core.utils.geometry.CoordinateTransformation;
import org.matsim.core.utils.geometry.geotools.MGC;
import org.matsim.core.utils.geometry.transformations.TransformationFactory;
import org.matsim.run.RunBerlinScenario;
import org.matsim.vis.snapshotwriters.SnapshotWritersModule;

public class RunBerlinSetUp extends AbstractRunPositionEmissions {

    private static final CoordinateTransformation transformation = TransformationFactory.getCoordinateTransformation("EPSG:31468", "EPSG:25833");
    private static final String configPath = "matsim/scenarios/countries/de/berlin/berlin-v5.5-1pct/input/berlin-v5.5-1pct.config.xml";

    public void run(String[] args) {

        var config = RunBerlinScenario.prepareConfig(args, getConfigGroups());
        applyConfig(config);

        config.controler().setFirstIteration(0);
        config.controler().setLastIteration(0);

        var scenario = RunBerlinScenario.prepareScenario(config);
        applyScenario(scenario);

        // work with UTM-33
        applyCoordinateTransformation(scenario, transformation);
        applyNetworkFilter(scenario.getNetwork());

        var controler = RunBerlinScenario.prepareControler(scenario);
        applyControler(controler);
        controler.run();
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
