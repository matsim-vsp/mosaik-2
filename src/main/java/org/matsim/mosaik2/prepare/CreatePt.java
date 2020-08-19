package org.matsim.mosaik2.prepare;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import org.locationtech.jts.geom.prep.PreparedGeometry;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Scenario;
import org.matsim.contrib.gtfs.GtfsConverter;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.network.io.MatsimNetworkReader;
import org.matsim.core.network.io.NetworkWriter;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.geometry.CoordinateTransformation;
import org.matsim.core.utils.geometry.geotools.MGC;
import org.matsim.core.utils.geometry.transformations.TransformationFactory;
import org.matsim.pt.transitSchedule.api.TransitScheduleWriter;
import org.matsim.pt.utils.CreatePseudoNetwork;
import org.matsim.pt.utils.CreateVehiclesForSchedule;
import org.matsim.vehicles.MatsimVehicleWriter;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;

public class CreatePt {

    private static final CoordinateTransformation transformation = TransformationFactory.getCoordinateTransformation("EPSG:4326", "EPSG:25832");
    private static final LocalDate date = LocalDate.now(); // TODO fixed value

    private static final String regionalSchedule = "projects\\mosaik-2\\raw-data\\gtfs\\regional_sbahn_gtfs_20200818.zip";
    private static final String localSchedule = "projects\\mosaik-2\\raw-data\\gtfs\\nahverkehr_gtfs_20200818.zip";
    private static final String networkFile = "projects\\mosaik-2\\matsim-input-files\\stuttgart-inkl-umland-vsp\\network-stuttgart.xml.gz";
    private static final String outputSchedule = "projects\\mosaik-2\\matsim-input-files\\stuttgart-inkl-umland-vsp\\transit-schedule-stuttgart.xml.gz";
    private static final String outputVehicles = "projects\\mosaik-2\\matsim-input-files\\stuttgart-inkl-umland-vsp\\transit-vehicles-stuttgart.xml.gz";
    private static final String outputNetwork = "projects\\mosaik-2\\matsim-input-files\\stuttgart-inkl-umland-vsp\\network-with-pt-stuttgart.xml.gz";

    public static void main(String[] args) {

        var arguments = new InputArgs();
        JCommander.newBuilder().addObject(arguments).build().parse(args);

        var scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        new MatsimNetworkReader(scenario.getNetwork()).readFile(arguments.sharedSvn + networkFile);

        var bbox = BoundingBox.fromNetwork(scenario.getNetwork()).toGeometry();

        parseGtfsFeed(scenario, Paths.get(arguments.sharedSvn, localSchedule), bbox);
        parseGtfsFeed(scenario, Paths.get(arguments.sharedSvn, regionalSchedule), bbox);

        new CreatePseudoNetwork(scenario.getTransitSchedule(), scenario.getNetwork(), "pt_").createNetwork();
        new CreateVehiclesForSchedule(scenario.getTransitSchedule(), scenario.getTransitVehicles()).run();

        new TransitScheduleWriter(scenario.getTransitSchedule()).writeFile(arguments.sharedSvn + outputSchedule);
        new MatsimVehicleWriter(scenario.getTransitVehicles()).writeFile(arguments.sharedSvn + outputVehicles);
        new NetworkWriter(scenario.getNetwork()).write(arguments.sharedSvn + outputNetwork);
    }

    private static void parseGtfsFeed(Scenario scenario, Path feed, PreparedGeometry bbox) {

        GtfsConverter.newBuilder()
                .setScenario(scenario)
                .setTransform(transformation)
                .setDate(date)
                .setFeed(feed)
                .setIncludeStop(stop -> {

                    var coord = transformation.transform(new Coord(stop.stop_lon, stop.stop_lat));
                    return bbox.covers(MGC.coord2Point(coord));
                })
                .setMergeStops(true)
                .build()
                .convert();
    }

    private static class InputArgs {

        @Parameter(names = {"-sharedSvn"}, required = true)
        String sharedSvn = "https://svn.vsp.tu-berlin.de/repos/shared-svn/";
    }
}
