package org.matsim.mosaik2.analysis;

import it.unimi.dsi.fastutil.objects.AbstractObject2DoubleMap;
import it.unimi.dsi.fastutil.objects.Object2DoubleOpenHashMap;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.junit.Test;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.contrib.emissions.Pollutant;
import org.matsim.contrib.emissions.events.EmissionEventsReader;
import org.matsim.core.events.EventsUtils;
import org.matsim.core.network.NetworkUtils;
import org.matsim.mosaik2.chemistryDriver.AggregateEmissionsByTimeHandler;
import org.matsim.mosaik2.palm.PalmOutputReader;
import org.matsim.mosaik2.raster.DoubleRaster;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

@Log4j2
public class AverageSmoothingRadiusEstimateTest {

	private static void printValue(double x, double y, double value, CSVPrinter printer) {
		try {
			printer.printRecord(x, y, value);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private static Link getLink(String id, Coord from, Coord to) {

		var network = NetworkUtils.createNetwork();
		var fromNode = network.getFactory().createNode(Id.createNodeId(id + "_from"), from);
		var toNode = network.getFactory().createNode(Id.createNodeId(id + "_to"), to);
		return network.getFactory().createLink(Id.createLinkId(id), fromNode, toNode);
	}

	@Test
	public void testCollectR() {

		Coord from = new Coord(40, 50);
		Coord to = new Coord(60, 50);
		double E = 10;
		double R = 23;
		final var emissions = new Object2DoubleOpenHashMap<>(Map.of(getLink("link", from, to), E));
		var raster = new DoubleRaster(new DoubleRaster.Bounds(0, 0, 110, 110), 10);

		raster.setValueForEachCoordinate((x, y) -> E * NumericSmoothingRadiusEstimate.calculateWeight(from, to, new Coord(x, y), 20, R));

		AverageSmoothingRadiusEstimate
				.collectR(raster, emissions)
				.forEachCoordinate((x, y, value) -> assertEquals(R, value, 10E-3));
	}

	@Test
	public void testWithOriginalRaster() {

		log.info("loading palm data.");
		// load the 8 o clock time slice
		var palmOutput = PalmOutputReader.read("C:/Users/janek/repos/shared-svn/projects/mosaik-2/data/berlin-chemistry-driver-results/run_B2_chem_w3_lod2_masked_M01.merged.nc", 8 * 4, 8 * 4, "PM10");
		// take the pm10 raster
		var pm10Raster = palmOutput.getTimeBins().iterator().next().getValue().get("PM");

		// cut 100x100 m in the center of the area.
		var smallBounds = new DoubleRaster.Bounds(pm10Raster.getBounds().getMinX() + 950, pm10Raster.getBounds().getMinY() + 950, pm10Raster.getBounds().getMaxX() - 950, pm10Raster.getBounds().getMaxY() - 950);
		var smallPm10Raster = new DoubleRaster(smallBounds, pm10Raster.getCellSize());
		smallPm10Raster.setValueForEachIndex(pm10Raster::getValueByIndex);

		log.info("Finished loading palm data");
		log.info("Start loading Network.");
		// collect emissions
		var network = NetworkUtils.readNetwork("C:\\Users\\janek\\repos\\runs-svn\\mosaik-2\\berlin\\mosaik-2-berlin-with-geometry-attributes\\output\\berlin-with-geometry-attributes.output_network.xml.gz");
		var handler = new AggregateEmissionsByTimeHandler(network, Set.of(Pollutant.PM), 3600, 1000);
		var manager = EventsUtils.createEventsManager();
		manager.addHandler(handler);

		log.info("Start parsing emission events");
		new EmissionEventsReader(manager).readFile("C:\\Users\\janek\\repos\\runs-svn\\mosaik-2\\berlin\\mosaik-2-berlin-with-geometry-attributes\\output\\berlin-with-geometry-attributes.output_only_001_emission_events.xml.gz");

		log.info("Start converting collected emissions. Take time slice form 8am, filter all links without emissions");
		var emissionsByTime = handler.getTimeBinMap();
		var emissionsById = emissionsByTime.getTimeBin(8 * 3600).getValue().get(Pollutant.PM);
		var emissions = emissionsById.object2DoubleEntrySet().stream()
				.filter(idEntry -> idEntry.getDoubleValue() >= 0)
				.map(idEntry -> new AbstractObject2DoubleMap.BasicEntry<Link>(network.getLinks().get(idEntry.getKey()), idEntry.getDoubleValue()))
				.collect(Collectors.toMap(AbstractObject2DoubleMap.BasicEntry::getKey, AbstractObject2DoubleMap.BasicEntry::getDoubleValue, Double::sum, Object2DoubleOpenHashMap::new));

		log.info("call collectR");
		var rasterOfRs = AverageSmoothingRadiusEstimate.collectR(smallPm10Raster, emissions);
		log.info("after collectR");
		assertNotNull(rasterOfRs);

		var filename = "C:\\Users\\janek\\Desktop\\r-values.csv";
		log.info("writing csv to: " + filename);
		try (var writer = Files.newBufferedWriter(Paths.get(filename)); var printer = CSVFormat.DEFAULT.withDelimiter(',').withHeader("x", "y", "R").print(writer)) {
			rasterOfRs.forEachCoordinate((x, y, value) -> printValue(x, y, value, printer));
		} catch (IOException e) {
			e.printStackTrace();
		}
		log.info("done.");
	}
}