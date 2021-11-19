package org.matsim.mosaik2.agentEmissions;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.lang3.StringUtils;
import org.locationtech.jts.geom.prep.PreparedGeometry;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.events.ActivityEndEvent;
import org.matsim.api.core.v01.events.ActivityStartEvent;
import org.matsim.api.core.v01.events.PersonDepartureEvent;
import org.matsim.api.core.v01.events.handler.ActivityEndEventHandler;
import org.matsim.api.core.v01.events.handler.ActivityStartEventHandler;
import org.matsim.api.core.v01.events.handler.PersonDepartureEventHandler;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.events.EventsUtils;
import org.matsim.core.mobsim.framework.MobsimAgent;
import org.matsim.core.mobsim.qsim.interfaces.DepartureHandler;
import org.matsim.core.utils.collections.Tuple;
import org.matsim.core.utils.geometry.CoordinateTransformation;
import org.matsim.core.utils.geometry.geotools.MGC;
import org.matsim.core.utils.geometry.transformations.IdentityTransformation;
import org.matsim.core.utils.geometry.transformations.TransformationFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Log4j2
public class ExtractStartAndEndPoints {

    @Parameter(names = "-events", required = true)
    private String eventsFile;

    @Parameter(names = "-output", required = true)
    private String outputFile;

    @Parameter(names = "-targetCrs")
    String targetCrs;
    @Parameter(names = "-sourceCrs")
    String sourceCrs;

    public static void main(String[] args) {

        var extracter = new ExtractStartAndEndPoints();
        JCommander.newBuilder().addObject(extracter).build().parse(args);
        extracter.run();
    }

    private void run() {

        var handler = new Handler();
        var manager = EventsUtils.createEventsManager();
        manager.addHandler(handler);
        EventsUtils.readEvents(manager, eventsFile);

        var walkLegs = handler.getWalkLegs();
        var bbox = RunBerlinSetUp.createBoundingBox();
        var transformation = getTransformation();

        log.info("Start writing Legs");

        try (var writer = Files.newBufferedWriter(Paths.get(outputFile)); var printer = CSVFormat.DEFAULT.withHeader("agentId", "tStart", "xStart", "yStart", "tEnd", "xEnd", "yEnd").print(writer)){
            for (var leg : walkLegs) {
                if (isCovered(bbox, leg.getStartCoord(), leg.getEndCoord())) {

                    var startCoord = transformation.transform(leg.getStartCoord());
                    var endCoord = transformation.transform(leg.getEndCoord());
                    printer.printRecord(leg.getPersonId(), leg.getStartTime(), startCoord.getX(), startCoord.getY(), leg.getEndTime(), endCoord.getX(), endCoord.getY());
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        log.info("Finished writing all legs.");
    }

    private CoordinateTransformation getTransformation() {
        if (StringUtils.isBlank(sourceCrs) || StringUtils.isBlank(targetCrs)) {
            log.info("Source and target crs were not provided. Using identity transformation");
            return new IdentityTransformation();
        } else {
            log.info("Using transformation from: " + sourceCrs + " to: " + targetCrs);
            return TransformationFactory.getCoordinateTransformation(sourceCrs, targetCrs);
        }
    }

    private static boolean isCovered(PreparedGeometry geometry, Coord startCoord, Coord endCoord) {
        return geometry.covers(MGC.coord2Point(startCoord)) && geometry.covers(MGC.coord2Point(endCoord));
    }

    static class Handler implements ActivityEndEventHandler, ActivityStartEventHandler, PersonDepartureEventHandler {

        private final Map<Id<Person>, Leg> currentLegs = new HashMap<>();

        @Getter
        private final List<Leg> walkLegs = new ArrayList<>();


        @Override
        public void handleEvent(ActivityEndEvent event) {
            var leg = new Leg();
            leg.setStartCoord(event.getCoord());
            leg.setStartTime(event.getTime());
            leg.setPersonId(event.getPersonId());
            currentLegs.put(event.getPersonId(), leg);
        }

        @Override
        public void handleEvent(ActivityStartEvent event) {
            if (currentLegs.containsKey(event.getPersonId())) {
                var leg = currentLegs.remove(event.getPersonId());
                leg.setEndCoord(event.getCoord());
                leg.setEndTime(event.getTime());
                walkLegs.add(leg);
            }
        }

        @Override
        public void handleEvent(PersonDepartureEvent event) {
            if (currentLegs.containsKey(event.getPersonId())){
                if (!event.getLegMode().equals(TransportMode.walk)) {
                    currentLegs.remove(event.getPersonId());
                }
            }
        }
    }

    @Getter@Setter
    static class Leg {

        private Coord startCoord;
        private Coord endCoord;
        private double startTime;
        private double endTime;
        private Id<Person> personId;
    }
}
