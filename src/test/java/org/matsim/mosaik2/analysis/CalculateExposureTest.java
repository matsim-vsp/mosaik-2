package org.matsim.mosaik2.analysis;

import lombok.extern.log4j.Log4j2;
import org.junit.Rule;
import org.junit.Test;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.events.ActivityEndEvent;
import org.matsim.api.core.v01.events.ActivityStartEvent;
import org.matsim.contrib.analysis.time.TimeBinMap;
import org.matsim.core.events.EventsUtils;
import org.matsim.core.events.algorithms.EventWriterXML;
import org.matsim.core.population.PopulationUtils;
import org.matsim.facilities.ActivityFacility;
import org.matsim.mosaik2.palm.PalmCsvOutput;
import org.matsim.mosaik2.raster.DoubleRaster;
import org.matsim.testcases.MatsimTestUtils;

import java.nio.file.Paths;
import java.util.List;

import static org.junit.Assert.assertEquals;

@Log4j2
public class CalculateExposureTest {

	@Rule
	public MatsimTestUtils testUtils = new MatsimTestUtils();

	@Test
	public void tile_pushToPerforming_noStartTime() {

		var activity = PopulationUtils.getFactory().createActivityFromCoord("test", new Coord(10, 10));
		activity.setEndTime(5000);
		var tile = new CalculateExposure.Tile();
		tile.add(activity);

		assertEquals(1, tile.getActivities().size());
		assertEquals(0, tile.getCurrentlyPerforming().size());

		tile.pushAllActToPerforming(0);

		assertEquals(0, tile.getActivities().size());
		assertEquals(1, tile.getCurrentlyPerforming().size());
	}

	@Test
	public void tile_pushToPerforming_withStartTime() {

		var activity = PopulationUtils.getFactory().createActivityFromCoord("test", new Coord(10, 10));
		activity.setStartTime(5000);
		var tile = new CalculateExposure.Tile();
		tile.add(activity);

		assertEquals(1, tile.getActivities().size());
		assertEquals(0, tile.getCurrentlyPerforming().size());

		tile.pushAllActToPerforming(4999);

		assertEquals(1, tile.getActivities().size());
		assertEquals(0, tile.getCurrentlyPerforming().size());

		tile.pushAllActToPerforming(5000);

		assertEquals(0, tile.getActivities().size());
		assertEquals(1, tile.getCurrentlyPerforming().size());
	}

	@Test
	public void tile_calculateSpentTime_cutStartTime() {

		var activity = PopulationUtils.getFactory().createActivityFromCoord("test", new Coord(10, 10));
		activity.setStartTime(1000);
		activity.setEndTime(2000);
		var tile = new CalculateExposure.Tile();
		tile.add(activity);
		tile.pushAllActToPerforming(1000);

		var time = tile.calculateSpentTime(500, 1500);

		assertEquals(500, time, 1e-8);
	}

	@Test
	public void tile_calculateSpentTime_cutEndTime() {

		var activity = PopulationUtils.getFactory().createActivityFromCoord("test", new Coord(10, 10));
		activity.setStartTime(1000);
		activity.setEndTime(2000);
		var tile = new CalculateExposure.Tile();
		tile.add(activity);
		tile.pushAllActToPerforming(1000);

		var time = tile.calculateSpentTime(1600, 2800);

		assertEquals(400, time, 1e-8);
	}

	@Test
	public void tile_calculateSpentTime_activityShorterThanPeriod() {

		var activity = PopulationUtils.getFactory().createActivityFromCoord("test", new Coord(10, 10));
		activity.setStartTime(1000);
		activity.setEndTime(2000);
		var tile = new CalculateExposure.Tile();
		tile.add(activity);
		tile.pushAllActToPerforming(1000);

		var time = tile.calculateSpentTime(800, 2800);

		assertEquals(1000, time, 1e-8);
	}

	@Test
	public void tile_calculateSpentTime_periodShorterThanAct() {

		var activity = PopulationUtils.getFactory().createActivityFromCoord("test", new Coord(10, 10));
		activity.setStartTime(1000);
		activity.setEndTime(2000);
		var tile = new CalculateExposure.Tile();
		tile.add(activity);
		tile.pushAllActToPerforming(1000);

		var time = tile.calculateSpentTime(1200, 1800);

		assertEquals(600, time, 1e-8);
	}

	@Test
	public void tile_priorityByStartTime() {

		var act1 = PopulationUtils.getFactory().createActivityFromCoord("home1", new Coord(10, 10));
		act1.setEndTime(1000);
		var act2 = PopulationUtils.getFactory().createActivityFromCoord("other", new Coord(20, 20));
		act2.setStartTime(1500);
		act2.setEndTime(2500);
		var act3 = PopulationUtils.getFactory().createActivityFromCoord("home2", new Coord(10, 10));
		act3.setStartTime(3000);
		var tile = new CalculateExposure.Tile();
		// insert in unexpected order
		tile.add(act2);
		tile.add(act1);
		tile.add(act3);
		var expectedOrder = List.of(act1, act2, act3);

		var index = 0;
		for (var actFromTile : tile.getActivities()) {
			var expectedAct = expectedOrder.get(index);
			assertEquals(expectedAct, actFromTile);
			index++;
		}
	}

	@Test
	public void tile_calculateSpentTime_multipleActPerforming() {

		var act1 = PopulationUtils.getFactory().createActivityFromCoord("some", new Coord(10, 10));
		act1.setEndTime(2000);
		var act2 = PopulationUtils.getFactory().createActivityFromCoord("other", new Coord(10, 10));
		act2.setStartTime(1000);
		act2.setEndTime(2000);
		var act3 = PopulationUtils.getFactory().createActivityFromCoord("activity", new Coord(10, 10));
		act3.setStartTime(1000);
		var tile = new CalculateExposure.Tile();
		// insert in unexpected order
		tile.add(act2);
		tile.add(act1);
		tile.add(act3);
		tile.pushAllActToPerforming(1000);

		var spentTime = tile.calculateSpentTime(500, 2500);
		// we expect act1 -> 1500 + act2 -> 1000 + act3 -> 1500 = 4000
		assertEquals(4000, spentTime, 1e-8);
	}

	@Test
	public void integration_single_activity_short() {

		// create palm data for 3 hours with only one raster point which has emissions
		TimeBinMap<DoubleRaster> palmData = new TimeBinMap<>(3600);
		palmData.getTimeBin(0).setValue(createRaster());
		palmData.getTimeBin(3600).computeIfAbsent(() -> {
			var raster = createRaster();
			raster.setValueForCoord(50, 50, 1000);
			return raster;
		});
		palmData.getTimeBin(7200).setValue(createRaster());
		var palmPath = Paths.get(testUtils.getOutputDirectory()).resolve("palm-data.csv");
		PalmCsvOutput.write(palmPath, palmData);

		// create events file
		var eventsPath = Paths.get(testUtils.getOutputDirectory()).resolve("events.xml");
		var manager = EventsUtils.createEventsManager();
		var writer = new EventWriterXML(eventsPath.toString());
		manager.addHandler(writer);
		manager.processEvent(new ActivityStartEvent(4000, Id.createPersonId("p1"), Id.createLinkId("link"), Id.create("f", ActivityFacility.class), "type", new Coord(40, 40)));
		manager.processEvent(new ActivityEndEvent(5000, Id.createPersonId("p1"), Id.createLinkId("link"), Id.create("f", ActivityFacility.class), "type", new Coord(40, 40)));
		writer.closeFile();

		// create and run calculator
		var outputPath = Paths.get(testUtils.getOutputDirectory()).resolve("exposure.csv");
		var calculator = new CalculateExposure(palmPath, eventsPath, outputPath);
		calculator.run();

		// load results and check
		var raster = createRaster();
		var dataInfo = new PalmCsvOutput.DataInfo(
				new PalmCsvOutput.RasterInfo(raster.getBounds(), raster.getCellSize()),
				3600
		);
		var resultData = PalmCsvOutput.read(outputPath, dataInfo);
		assertEquals(1, resultData.getTimeBins().size());
		var resultRaster = resultData.getTimeBin(3600).getValue();
		resultRaster.forEachCoordinateParallel((x, y, value) -> {
			if (x == 50 && y == 50) {
				assertEquals(1000000, value, 1e-8);
			} else {
				assertEquals(-1, value, 1e-8);
			}
		});
	}

	@Test
	public void integration_3_persons_from_berlin() {

		var eventsPath = Paths.get(testUtils.getInputDirectory()).resolve("events-3-agent-sample.xml");
		var palmPath = Paths.get("C:\\Users\\Janekdererste\\repos\\runs-svn\\mosaik-2\\berlin\\mosaik-2-berlin-with-geometry-attributes\\palm-output\\photoshade_6km10m_lod2_av_masked_M01.day2-PM10.csv");
		var outputPath = Paths.get(testUtils.getOutputDirectory()).resolve("exposure.csv");

		new CalculateExposure(palmPath, eventsPath, outputPath).run();

		log.info("done");
	}

	private DoubleRaster createRaster() {

		var bounds = new DoubleRaster.Bounds(0, 0, 100, 100);
		var raster = new DoubleRaster(bounds, 10, -1);
		// set these values so that the raster gets written to the file correctly
		raster.setValueForCoord(0, 0, 1);
		raster.setValueForCoord(100, 100, 1);

		// set the middle value to some value
		raster.setValueForCoord(50, 50, 1);
		return raster;
	}
}