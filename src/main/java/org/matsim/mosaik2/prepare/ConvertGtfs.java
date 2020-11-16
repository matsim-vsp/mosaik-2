package org.matsim.mosaik2.prepare;

import org.matsim.api.core.v01.Scenario;
import org.matsim.contrib.gtfs.GtfsConverter;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.network.io.NetworkWriter;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.geometry.CoordinateTransformation;
import org.matsim.core.utils.geometry.transformations.TransformationFactory;
import org.matsim.pt.transitSchedule.api.TransitScheduleWriter;
import org.matsim.pt.utils.CreatePseudoNetwork;
import org.matsim.vehicles.MatsimVehicleWriter;

import java.nio.file.Paths;
import java.time.LocalDate;

public class ConvertGtfs {

    private static final CoordinateTransformation transformation = TransformationFactory.getCoordinateTransformation("EPSG:4326", "EPSG:25832");

    public static void main(String[] args) {

        var scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());

        GtfsConverter.newBuilder()
                .setScenario(scenario)
                .setTransform(transformation)
                .setDate(LocalDate.now())
                .setFeed(Paths.get("C:\\Users\\Janekdererste\\Downloads\\vvs_gtfs.zip"))
                .build()
                .convert();

        new CreatePseudoNetwork(scenario.getTransitSchedule(), scenario.getNetwork(), "pt_").createNetwork();
        new NetworkWriter(scenario.getNetwork()).write("C:\\Users\\Janekdererste\\Desktop\\vvs_gtfs_pt_network.xml.gz");
        writeScheduleAndVehicles(scenario, "C:\\Users\\Janekdererste\\Desktop\\vvs_gtfs_pt_schedule.xml.gz", "C:\\Users\\Janekdererste\\Desktop\\vvs_gtfs_pt_vehicles.xml.gz");
    }

    private static void writeScheduleAndVehicles(Scenario scenario, String scheduleFile, String vehiclesFile) {

        new TransitScheduleWriter(scenario.getTransitSchedule()).writeFile(scheduleFile);
        new MatsimVehicleWriter(scenario.getTransitVehicles()).writeFile(vehiclesFile);
    }
}
