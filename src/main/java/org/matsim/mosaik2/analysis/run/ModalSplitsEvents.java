package org.matsim.mosaik2.analysis.run;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import it.unimi.dsi.fastutil.objects.Object2DoubleMap;
import it.unimi.dsi.fastutil.objects.Object2DoubleOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import lombok.extern.log4j.Log4j2;
import org.geotools.geojson.geom.GeometryJSON;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.prep.PreparedGeometry;
import org.locationtech.jts.geom.prep.PreparedGeometryFactory;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.core.events.EventsUtils;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.router.MainModeIdentifier;
import org.matsim.core.router.MainModeIdentifierImpl;
import org.matsim.core.utils.geometry.geotools.MGC;
import org.matsim.core.utils.gis.ShapeFileReader;
import org.matsim.mosaik2.palm.PalmStaticDriverReader;
import ucar.nc2.NetcdfFiles;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.stream.Collectors;

@Log4j2
public class ModalSplitsEvents {

	public static void main(String[] args) {

		var input = new InputArgs();
		JCommander.newBuilder().addObject(input).build().parse(args);

		var network = NetworkUtils.readNetwork(input.network);
		var filter = createSpatialFilter(input);

		log.info("Filter network.");
		var filteredIds = network.getLinks().values().parallelStream()
				.filter(l -> filter.covers(MGC.coord2Point(l.getCoord())))
				.collect(Collectors.toSet());

		log.info("Start reading events");
		var trips = input.runIds.parallelStream()
				.map(id -> analyze(input.folder, id))
				.flatMap(Collection::stream)
				.collect(Collectors.toSet());

		log.info("Start creating intersecting trips.");
		var intersecting = trips.parallelStream()
				.filter(trip -> trip.getRoute().stream().anyMatch(filteredIds::contains))
				.collect(Collectors.toMap(TripEventHandler.Trip::identifier, trip -> trip));

		//log.info("Start creating non intersecting trips.");
		//var nonIntersecting = trips.parallelStream()
		//		.filter(trip -> !intersecting.containsKey(trip.identifier()))
		//		.collect(Collectors.toMap(TripEventHandler.Trip::identifier, trip -> trip));

		log.info("Start writing to csv");

		var mapper = new GeometryJSON();

		CSVUtils.writeTable(intersecting.values(), Paths.get(input.outputFolder).resolve("trips-intersecting.csv"), List.of("time", "mode", "id", "geom"), (p,r) -> {
			CSVUtils.printRecord(p, r.getDepartureTime(), r.getMainMode(), r.identifier(), mapper.toString(r.toGeometry(network)));
		});
		//CSVUtils.writeTable(nonIntersecting.values(), Paths.get(input.outputFolder).resolve("trips-not-intersecting.csv"), List.of("time", "mode", "id", "geom"), (p,r) -> {
	//		CSVUtils.printRecord(p, r.getDepartureTime(), r.getMainMode(), r.identifier(), mapper.toString(r.toGeometry(network)));
	//	});
	}

	private static Collection<TripEventHandler.Trip> analyze(String folder, String id) {
		var trips = readOutput(folder, id);
		return trips.stream()
				.filter(trip -> trip.getDepartureTime() > 86400) // cut off at 24h
				.collect(Collectors.toSet());
	}

	private static Collection<TripEventHandler.Trip> readOutput(String folder, String id) {
		var handler = new TripEventHandler(new MainModeIdentifier(), a -> true);
		var manager = EventsUtils.createEventsManager();
		manager.addHandler(handler);

		log.info("Start parsing events for run id: " + id);
		EventsUtils.readEvents(manager, getFilename(folder, id, "events"));

		return handler.getTrips().values().stream().flatMap(Collection::stream).collect(Collectors.toSet());
	}

	private static String getFilename(String folder, String runId, String filetype) {
		return Paths.get(folder).resolve(runId + ".output_" + filetype + ".xml.gz").toString();
	}

	private static PreparedGeometry createSpatialFilter(InputArgs inputArgs) {

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

	static class MainModeIdentifier implements org.matsim.core.router.MainModeIdentifier {

		final Object2IntMap<String> ranking = new Object2IntOpenHashMap<>();

		MainModeIdentifier() {
			ranking.put(TransportMode.pt, 0);
			ranking.put(TransportMode.car, 1);
			ranking.put(TransportMode.ride, 1);
			ranking.put(TransportMode.bike, 2);
			ranking.put(TransportMode.walk, 3);
			ranking.put("freight", 4);

		}
		@Override
		public String identifyMainMode(List<? extends PlanElement> tripElements) {

			var currMode = TransportMode.non_network_walk;
			var currRank = 100.;

			for (var e : tripElements) {
				if (e instanceof Leg l) {
					var rank = ranking.getOrDefault(l.getMode(), 100);
					if (rank < currRank) {
						currRank = rank;
						currMode = l.getMode();
					}
				}
			}

			return currMode;
		}
	}

	public static class InputArgs {

		@Parameter(names = "--f", required = true)
		private String folder;

		@Parameter(names = "--n", required = true)
		private List<String> runIds;

		@Parameter(names = "--net", required = true)
		private String network;

		@Parameter(names = "--shp")
		public String shpFile = "";

		@Parameter(names = "--static")
		public String staticDriver = "";

		@Parameter(names = "--of")
		private String outputFolder;
	}
}