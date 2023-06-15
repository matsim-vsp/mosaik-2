package org.matsim.mosaik2.analysis.run;

import com.beust.jcommander.JCommander;
import lombok.extern.log4j.Log4j2;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.prep.PreparedGeometry;
import org.locationtech.jts.geom.prep.PreparedGeometryFactory;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Identifiable;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.population.routes.NetworkRoute;
import org.matsim.core.router.TripStructureUtils;
import org.matsim.core.utils.geometry.geotools.MGC;
import org.matsim.core.utils.gis.ShapeFileReader;
import org.matsim.mosaik2.palm.PalmStaticDriverReader;
import ucar.nc2.NetcdfFiles;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Log4j2
public class Blup {

	public static void main(String[] args) {

		var input = new ModalSplitsEvents.InputArgs();
		JCommander.newBuilder().addObject(input).build().parse(args);


		var filter = createSpatialFilter(input);
		var net = NetworkUtils.readNetwork("C:\\Users\\Janekdererste\\Documents\\work\\berlin-roadpricing\\output_roadpricing\\network.xml.gz");
		var pop = PopulationUtils.readPopulation("C:\\Users\\Janekdererste\\Documents\\work\\berlin-roadpricing\\output_base\\berlin-with-geometry-attributes.output_plans_001.xml.gz");

		log.info("filter network");
		var filteredNodes = net.getLinks().values().parallelStream()
				.filter(link -> filter.covers(MGC.coord2Point(link.getCoord())))
				.map(Identifiable::getId)
				.collect(Collectors.toSet());

		List<Entry> entries = new ArrayList<>();
		for (var person : pop.getPersons().values()) {
			var plan = person.getSelectedPlan();
			var trips = TripStructureUtils.getTrips(plan);
			for (var trip : trips) {
				for (var leg : trip.getLegsOnly()) {
					if (leg.getRoute() instanceof NetworkRoute netRoute) {
						if (netRoute.getLinkIds().stream().anyMatch(filteredNodes::contains)) {
							entries.add(new Entry(person.getId(), leg.getMode(), leg.getDepartureTime().seconds()));
						}
					}
				}
			}
		}

		CSVUtils.writeTable(entries, Paths.get("C:\\Users\\Janekdererste\\Documents\\work\\berlin-roadpricing\\output_roadpricing\\test.csv"), List.of("run", "time", "mode", "id"), (p, e) -> {
			CSVUtils.printRecord(p, "test", e.time, e.mode, e.id);
		});
	}

	record Entry(Id<Person> id, String mode, double time) {}

	private static PreparedGeometry createSpatialFilter(ModalSplitsEvents.InputArgs inputArgs) {

		var prepFact = new PreparedGeometryFactory();
		if (!inputArgs.shpFile.isBlank()) {
			return ShapeFileReader.getAllFeatures(inputArgs.shpFile).stream()
					.limit(1)
					.map(feature -> (Geometry) feature.getDefaultGeometry())
					.map(prepFact::create)
					.toList()
					.get(0);
		} else if (!inputArgs.staticDriver.isBlank()) {
			try (var file = NetcdfFiles.open(inputArgs.staticDriver)) {
				var bbox = PalmStaticDriverReader.createTarget(file).getBounds().toGeometry();
				return prepFact.create(bbox);
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		} else {
			return null;
		}
	}
}