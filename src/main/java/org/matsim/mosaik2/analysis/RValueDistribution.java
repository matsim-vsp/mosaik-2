package org.matsim.mosaik2.analysis;

import com.beust.jcommander.JCommander;
import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import it.unimi.dsi.fastutil.doubles.DoubleList;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.csv.CSVFormat;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.core.utils.collections.Tuple;
import org.matsim.mosaik2.raster.DoubleRaster;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Log4j2
public class RValueDistribution {

	public static void main(String[] args) {

		var input = new SmoothingRadiusEstimate.InputArgs();
		JCommander.newBuilder().addObject(input).build().parse(args);

		var estimator = new SmoothingRadiusEstimate(input);
		estimator.collectRForEachTimeslice((i, raster) -> doSomethingWithRaster(raster, estimator));

	}

	private static void doSomethingWithRaster(DoubleRaster raster, SmoothingRadiusEstimate estimator) {

		log.info("Do something with raster");
		var filteredNetwork = estimator.getNetwork();

		Map<String, DoubleList> result = new ConcurrentHashMap<>();

		var counter = new AtomicInteger();
		var size = raster.getYLength() * raster.getXLength();

		raster.forEachCoordinateParallel((x, y, rValue) -> {
			if (rValue <= 0.0) return;

			var currentCount = counter.incrementAndGet();
			if (currentCount % 100000 == 0) {
				log.info("Collect r-valuse #" + currentCount + " / " + size);
			}

			var links = filterLinksWithWeightGreaterZero(filteredNetwork, new Coord(x, y), rValue);
			for (var link : links) {
				var type = (String) link.getAttributes().getAttribute("type");
				result.compute(type, (key, value) -> {
					if (value == null) {
						value = new DoubleArrayList();
					}
					value.add(rValue);
					return value;
				});
				// result.computeIfAbsent(type, key -> new DoubleArrayList()).add(rValue);
			}
		});

		var output = Paths.get(estimator.getInput().getOutputFile()).getParent().resolve("r-values-by-type.csv");
		log.info("Start writing output file.");
		try (var writer = Files.newBufferedWriter(output); var printer = CSVFormat.DEFAULT.withDelimiter(',').withHeader("type", "value").print(writer)) {

			for (var entry : result.entrySet()) {
				for (var it = entry.getValue().iterator(); it.hasNext(); ) {
					printer.printRecord(entry.getKey(), it.nextDouble());
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		log.info("done");
	}

	private static Set<Link> filterLinksWithWeightGreaterZero(Network network, Coord receiverPoint, double rValue) {

		return network.getLinks().values().stream()
				.map(link -> Tuple.of(link, NumericSmoothingRadiusEstimate.calculateWeight(
						link.getFromNode().getCoord(),
						link.getToNode().getCoord(),
						receiverPoint,
						link.getLength(),
						rValue

				)))
				.filter(tuple -> tuple.getSecond() > 0.0)
				.map(Tuple::getFirst)
				.collect(Collectors.toSet());
	}
}