package org.matsim.mosaik2.chemistryDriver;

import org.junit.Test;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.core.network.NetworkUtils;
import org.matsim.mosaik2.raster.AbstractRaster;
import org.matsim.mosaik2.raster.DoubleRaster;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SwingRasterizerTest {

	@Test
	public void testLineOffset() {

		var raster = new DoubleRaster(new AbstractRaster.Bounds(0, 0, 10, 10), 1);
		var network = NetworkUtils.createNetwork();
		var from = network.getFactory().createNode(Id.createNodeId("from"), new Coord(3, 5));
		var to = network.getFactory().createNode(Id.createNodeId("to"), new Coord(7, 5));
		var link = network.getFactory().createLink(Id.createLinkId("link"), from, to);

		var line = SwingRasterizer.PixelLine.fromLinkWithOffset(link, 4, raster);

		assertEquals(3, line.x0());
		assertEquals(3, line.y0());
		assertEquals(7, line.x1());
		assertEquals(3, line.y1());
	}

	@Test
	public void testLineOffsetReverse() {

		var raster = new DoubleRaster(new AbstractRaster.Bounds(0, 0, 10, 10), 1);
		var network = NetworkUtils.createNetwork();
		var from = network.getFactory().createNode(Id.createNodeId("from"), new Coord(7, 5));
		var to = network.getFactory().createNode(Id.createNodeId("to"), new Coord(3, 5));
		var link = network.getFactory().createLink(Id.createLinkId("link"), from, to);

		var line = SwingRasterizer.PixelLine.fromLinkWithOffset(link, 4, raster);

		assertEquals(7, line.x0());
		assertEquals(7, line.y0());
		assertEquals(3, line.x1());
		assertEquals(7, line.y1());
	}

	@Test
	public void testLineOffsetUp() {

		var raster = new DoubleRaster(new AbstractRaster.Bounds(0, 0, 10, 10), 1);
		var network = NetworkUtils.createNetwork();
		var from = network.getFactory().createNode(Id.createNodeId("from"), new Coord(5, 3));
		var to = network.getFactory().createNode(Id.createNodeId("to"), new Coord(5, 7));
		var link = network.getFactory().createLink(Id.createLinkId("link"), from, to);

		var line = SwingRasterizer.PixelLine.fromLinkWithOffset(link, 4, raster);

		assertEquals(7, line.x0());
		assertEquals(3, line.y0());
		assertEquals(7, line.x1());
		assertEquals(7, line.y1());
	}

	@Test
	public void testLineOffsetDown() {

		var raster = new DoubleRaster(new AbstractRaster.Bounds(0, 0, 10, 10), 1);
		var network = NetworkUtils.createNetwork();
		var from = network.getFactory().createNode(Id.createNodeId("from"), new Coord(5, 7));
		var to = network.getFactory().createNode(Id.createNodeId("to"), new Coord(5, 3));
		var link = network.getFactory().createLink(Id.createLinkId("link"), from, to);

		var line = SwingRasterizer.PixelLine.fromLinkWithOffset(link, 4, raster);

		assertEquals(3, line.x0());
		assertEquals(7, line.y0());
		assertEquals(3, line.x1());
		assertEquals(3, line.y1());
	}

	@Test
	public void singleLinkOneLane() {

		var target = new DoubleRaster(new AbstractRaster.Bounds(0, 0, 100, 100), 5);

		// make one very small building
		var buildings = new DoubleRaster(target.getBounds(), target.getCellSize());
		buildings.setValueForCoord(50, 50, 100);

		var network = NetworkUtils.createNetwork();
		var from = network.getFactory().createNode(Id.createNodeId("from"), new Coord(30, 50));
		var to = network.getFactory().createNode(Id.createNodeId("to"), new Coord(70, 50));
		var link = network.getFactory().createLink(Id.createLinkId("link"), from, to);
		link.setNumberOfLanes(1);

		var rasterizer = new SwingRasterizer(target.getBounds(), target.getCellSize(), 3.5);
		rasterizer.rasterLink(link, 100, target, buildings);

		target.forEachCoordinate((x, y, value) -> {
			if (x == 50 && y == 50) assertEquals(0, value, 0.001); // the building
			else if (x >= 30 && x <= 70 && y == 50) assertTrue(value > 0); // all the other cells under the link
			else assertEquals(0, value, 0.00001); // all cells not part of the link
		});
	}

	@Test
	public void singleLinkMultipleLanes() {

		var target = new DoubleRaster(new AbstractRaster.Bounds(0, 0, 100, 100), 5);

		// make one very small building
		var buildings = new DoubleRaster(target.getBounds(), target.getCellSize());
		buildings.setValueForCoord(50, 50, 100);

		var network = NetworkUtils.createNetwork();
		var from = network.getFactory().createNode(Id.createNodeId("from"), new Coord(30, 50));
		var to = network.getFactory().createNode(Id.createNodeId("to"), new Coord(70, 50));
		var link = network.getFactory().createLink(Id.createLinkId("link"), from, to);
		link.setNumberOfLanes(6);

		var rasterizer = new SwingRasterizer(target.getBounds(), target.getCellSize(), 3.5);
		rasterizer.rasterLink(link, 10000, target, buildings);

		target.forEachCoordinate((x, y, value) -> {
			if (x == 50 && y == 50) assertEquals(0, value, 0.001); // the building
			else if (x >= 20 && x <= 75 && y >= 35 && y <= 50)
				assertTrue(value > 0); // all the other cells under the link
			else assertEquals(0, value, 0.00001); // all cells not part of the link
		});
	}
}