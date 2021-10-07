package org.matsim.mosaik2.agentEmissions;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import org.apache.commons.lang3.StringUtils;
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
import org.matsim.contrib.emissions.events.ColdEmissionEvent;
import org.matsim.contrib.emissions.events.WarmEmissionEvent;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.config.groups.ControlerConfigGroup;
import org.matsim.core.config.groups.QSimConfigGroup;
import org.matsim.core.controler.AbstractModule;
import org.matsim.core.controler.OutputDirectoryHierarchy;
import org.matsim.core.controler.events.AfterMobsimEvent;
import org.matsim.core.controler.events.BeforeMobsimEvent;
import org.matsim.core.controler.events.ShutdownEvent;
import org.matsim.core.controler.listener.AfterMobsimListener;
import org.matsim.core.controler.listener.BeforeMobsimListener;
import org.matsim.core.controler.listener.ShutdownListener;
import org.matsim.core.events.EventsManagerImpl;
import org.matsim.core.events.algorithms.EventWriter;
import org.matsim.core.utils.geometry.CoordinateTransformation;
import org.matsim.core.utils.geometry.geotools.MGC;
import org.matsim.core.utils.geometry.transformations.TransformationFactory;
import org.matsim.mosaik2.events.FilterEventsWriter;
import org.matsim.run.RunBerlinScenario;
import org.matsim.vehicles.VehicleUtils;
import org.matsim.vis.snapshotwriters.PositionEvent;
import org.matsim.vis.snapshotwriters.SnapshotWritersModule;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;

public class RunBerlinSetUp {

    private static final CoordinateTransformation transformation = TransformationFactory.getCoordinateTransformation("EPSG:31468", "EPSG:25833");
    private static final String configPath = "matsim/scenarios/countries/de/berlin/berlin-v5.5-1pct/input/berlin-v5.5-1pct.config.xml";

    private static class ConfigPath {

        @Parameter(names = "-config", required = true)
        private String path;
    }

    private static class OutputPath {

        @Parameter(names = "-output")
        private String outputPath;
    }

    public static void main(String[] args) {

        var sharedSvn = new Utils.SharedSvnArg();
        var configPath = new ConfigPath();
        var outputPath = new OutputPath();

        JCommander.newBuilder()
                .addObject(sharedSvn)
                .addObject(configPath)
                .addObject(outputPath)
                .build().parse(args);

        var emissionsConfig = Utils.createUpEmissionsConfigGroup(sharedSvn.getSharedSvn());
        var positionEmissionNetcdfConfig = Utils.createNetcdfEmissionWriterConfigGroup();
        var config = RunBerlinScenario.prepareConfig(
                new String[]{ configPath.path },
                emissionsConfig, positionEmissionNetcdfConfig);

        config.controler().setOverwriteFileSetting(OutputDirectoryHierarchy.OverwriteFileSetting.deleteDirectoryIfExists);
        if (!StringUtils.isBlank(outputPath.outputPath))
            config.controler().setOutputDirectory(outputPath.outputPath);

        // tell the default event writer to not write any events
        config.controler().setWriteEventsInterval(0);
        config.controler().setFirstIteration(0);
        config.controler().setLastIteration(0);

        Utils.applySnapshotSettings(config);

        var scenario = RunBerlinScenario.prepareScenario(config);

        // work with UTM-33
        applyCoordinateTransformation(scenario, transformation);
        Utils.applyNetworkAttributes(scenario.getNetwork());

        // we only want to generate postion events in the filter area
        applyNetworkFilter(scenario.getNetwork());
        config.qsim().setFilterSnapshots(QSimConfigGroup.FilterSnapshots.withLinkAttributes);

        // add engine information
        for (var vehicleType : scenario.getVehicles().getVehicleTypes().values()) {
            Utils.applyVehicleInformation(vehicleType);

            if (vehicleType.getId().toString().equals("freight")) {
                Utils.applyVehicleInformation(vehicleType);
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
        controler.addOverridingModule(new EventWriterModule());

        controler.addOverridingModule(new AbstractModule() {
            @Override
            public void install() {
                // we need single threaded events manager because other wise it doesn't work
                bind(EventsManager.class).to(EventsManagerImpl.class).in(Singleton.class);
            }
        });

        controler.run();

        // write things to a csv file so we can look at it in via
        var it = config.controler().getLastIteration();
        var runId = config.controler().getRunId();
        var folder = Paths.get(config.controler().getOutputDirectory())
                .resolve("ITERS")
                .resolve("it." + it);
        var netCdfName = folder.resolve(runId + "." + it + ".position-emissions.nc");
        var vehicleIndexName = folder.resolve(runId + "." + it + ".position-emissions-vehicleIdIndex.csv");

        AgentEmissionNetCdfReader.translateToCsv(
                netCdfName.toString(),
                vehicleIndexName.toString(),
                config.controler().getOutputDirectory() + "/position-emission-no2.csv");
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
                .forEach(link -> link.getAttributes().putAttribute(SnapshotWritersModule.GENERATE_SNAPSHOT_FOR_LINK_KEY, true));

        network.getLinks().values().parallelStream()
                .filter(link -> link.getAttributes().getAttribute(SnapshotWritersModule.GENERATE_SNAPSHOT_FOR_LINK_KEY) == null)
                .forEach(link -> link.getAttributes().putAttribute(SnapshotWritersModule.GENERATE_SNAPSHOT_FOR_LINK_KEY, false));
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

    private static class EventWriterModule extends AbstractModule {

        @Override
        public void install() {
            addControlerListenerBinding().to(WriterSetUp.class);
        }
    }

    private static class WriterSetUp implements BeforeMobsimListener, AfterMobsimListener, ShutdownListener {

        @Inject
        private OutputDirectoryHierarchy outputDirectoryHierarchy;

        @Inject
        private EventsManager eventsManager;

        @Inject
        private ControlerConfigGroup controlerConfig;

        private final Set<EventWriter> writers = new HashSet<>();

        @Override
        public void notifyBeforeMobsim(BeforeMobsimEvent event) {

            if (event.getIteration() != controlerConfig.getLastIteration()) return;


            var eventsFile = outputDirectoryHierarchy.getIterationFilename(event.getIteration(), "events.xml.gz");
            var emissionEventsFile = outputDirectoryHierarchy.getIterationFilename(event.getIteration(), "position-emission-events.xml.gz");

            // write everything except: positions, position-emissions, warm-emissions, cold-emissions
            var normalWriter = new FilterEventsWriter(
                    e -> (
                            !e.getEventType().equals(PositionEvent.EVENT_TYPE)
                            && !e.getEventType().equals(PositionEmissionsModule.PositionEmissionEvent.EVENT_TYPE)
                            && !e.getEventType().equals(WarmEmissionEvent.EVENT_TYPE)
                            && !e.getEventType().equals(ColdEmissionEvent.EVENT_TYPE)

                    ), eventsFile);

            // write only position-emissions
            var emissionWriter = new FilterEventsWriter(e -> e.getEventType().equals(PositionEmissionsModule.PositionEmissionEvent.EVENT_TYPE), emissionEventsFile);

            eventsManager.addHandler(normalWriter);
            eventsManager.addHandler(emissionWriter);
            writers.add(normalWriter);
            writers.add(emissionWriter);
        }

        @Override
        public void notifyAfterMobsim(AfterMobsimEvent event) {
            closeWriters();
        }

        @Override
        public void notifyShutdown(ShutdownEvent event) {
            if (event.isUnexpected()) {
                closeWriters();
            }
        }

        private void closeWriters() {
            for (EventWriter writer : writers) {
                writer.closeFile();
            }
        }
    }
}
