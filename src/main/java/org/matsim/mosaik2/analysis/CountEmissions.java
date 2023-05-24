package org.matsim.mosaik2.analysis;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import it.unimi.dsi.fastutil.objects.Object2DoubleMap;
import it.unimi.dsi.fastutil.objects.Object2DoubleOpenHashMap;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.tuple.Triple;
import org.locationtech.jts.geom.Geometry;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.contrib.analysis.time.TimeBinMap;
import org.matsim.contrib.emissions.events.EmissionEventsReader;
import org.matsim.core.events.EventsUtils;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.utils.gis.ShapeFileReader;
import org.matsim.mosaik2.Utils;
import org.matsim.mosaik2.analysis.run.CSVUtils;
import org.matsim.mosaik2.chemistryDriver.AggregateEmissionsByTimeHandler;
import org.matsim.mosaik2.chemistryDriver.PollutantToPalmNameConverter;
import org.matsim.mosaik2.palm.PalmStaticDriverReader;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Log4j2
public class CountEmissions {

	@SuppressWarnings("FieldMayBeFinal")
	private static class InputArgs {

		@Parameter(names = "-s", required = true)
		private List<String> species;
		@Parameter(names = "-e", required = true)
		private Path emissionEvents;
		@Parameter(names = "-n", required = true)
		private Path networkPath;
		@Parameter(names = "-bounds", required = false)
		private Path boundsFile;
		@Parameter(names = "-sd", required = false)
		private Path staticDriver;
		@Parameter(names = "-t")
		private int timeBinSize = 3600;
		@Parameter(names = "-f")
		private double scaleFactor = 10;
	}

	private static Network loadNetwork(Path networkPath, Path shapePath, Path staticDriver) throws IOException {

		if (shapePath != null) {
			var berlinGeometry = ShapeFileReader.getAllFeatures(shapePath.toString()).stream()
					.map(simpleFeature -> (Geometry) simpleFeature.getDefaultGeometry())
					.findAny()
					.orElseThrow(() -> new RuntimeException("Couldn't find feature for berlin"));

			return Utils.loadFilteredNetwork(networkPath.toString(), berlinGeometry);
		}
		if (staticDriver != null) {
			var target = PalmStaticDriverReader.read(staticDriver, "buildings_2d");
			var geometry = target.getBounds().toGeometry();
			return Utils.loadFilteredNetwork(networkPath.toString(), geometry);
		}
		return NetworkUtils.readNetwork(networkPath.toString());
	}
	public static void main(String[] args) throws IOException {

		var inputArgs = new InputArgs();
		JCommander.newBuilder().addObject(inputArgs).build().parse(args);

		var network = loadNetwork(inputArgs.networkPath, inputArgs.boundsFile, inputArgs.staticDriver);
		var manager = EventsUtils.createEventsManager();
		var converter = PollutantToPalmNameConverter.createForSpecies(inputArgs.species);
		var handler = new AggregateEmissionsByTimeHandler(network, converter.getPollutants(), inputArgs.timeBinSize, inputArgs.scaleFactor);
		manager.addHandler(handler);

		log.info("Start parsing emission events.");
		new EmissionEventsReader(manager).readFile(inputArgs.emissionEvents.toString());

		log.info("Aggregate PM10");
		TimeBinMap<Map<String, Object2DoubleMap<Id<Link>>>> aggregatedTimeBins = new TimeBinMap<>(inputArgs.timeBinSize);

		for (var bin : handler.getTimeBinMap().getTimeBins()) {
			var time = bin.getStartTime();
			var aggregatedBin = aggregatedTimeBins.getTimeBin(time);
			var aggregatedPollutants = aggregatedBin.computeIfAbsent(HashMap::new);

			for (var pollutantEntry : bin.getValue().entrySet()) {
				var pollutant = converter.getPalmName(pollutantEntry.getKey());
				for (var idEntry : pollutantEntry.getValue().object2DoubleEntrySet()) {

					var linkMap = aggregatedPollutants.computeIfAbsent(pollutant, p -> new Object2DoubleOpenHashMap<>());
					linkMap.mergeDouble(idEntry.getKey(), idEntry.getDoubleValue(), Double::sum);
				}
			}
		}
		log.info("Collect Emission sum per hour");
		var sums = aggregatedTimeBins.getTimeBins().stream().flatMap(bin -> {
			var result = new ArrayList<Triple<Double, String, Double>>();
			for (var entry : bin.getValue().entrySet()) {
				var species = entry.getKey();
				var sum = entry.getValue().values().stream()
						.mapToDouble(d -> d)
						.sum();
				result.add(Triple.of(bin.getStartTime(), species, sum));
			}
			return result.stream();
		}).toList();

		CSVUtils.writeTable(sums, Paths.get("./sums.csv"), List.of("hour", "species", "value"), (p, record) -> {
			log.info("time " + record.getLeft() + " : " + record.getMiddle() + " sum is: " + record.getRight() + "g");
			CSVUtils.printRecord(p, record.getLeft() / inputArgs.timeBinSize, record.getMiddle(), record.getRight());
		});

		log.info("Collect Emission average per hour per m.");
		var averages = aggregatedTimeBins.getTimeBins().stream().flatMap(bin -> {
					var result = new ArrayList<Triple<Double, String, Double>>();
					for (var entry : bin.getValue().entrySet()) {
						var averagePerMeter = entry.getValue().object2DoubleEntrySet().stream()
								.mapToDouble(linkEntry -> {
									var link = network.getLinks().get(linkEntry.getKey());
									return linkEntry.getDoubleValue() / link.getLength();
								}).average()
								.orElseThrow();
						result.add(Triple.of(bin.getStartTime(), entry.getKey(), averagePerMeter));
					}
					return result.stream();
				})
				.toList();

		CSVUtils.writeTable(averages, Paths.get("./averages.csv"), List.of("hour", "species", "value"), (p, record) -> {
			log.info("time " + record.getLeft() + " : " + record.getMiddle() + " average per m is: " + record.getRight() + "g/m");
			CSVUtils.printRecord(p, record.getLeft() / inputArgs.timeBinSize, record.getMiddle(), record.getRight());
		});
	}
}