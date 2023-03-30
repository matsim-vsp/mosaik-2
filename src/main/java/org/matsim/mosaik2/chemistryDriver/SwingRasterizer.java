package org.matsim.mosaik2.chemistryDriver;

import lombok.extern.log4j.Log4j2;
import org.matsim.api.core.v01.network.Link;
import org.matsim.mosaik2.raster.AbstractRaster;
import org.matsim.mosaik2.raster.DoubleRaster;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.concurrent.atomic.AtomicInteger;

@Log4j2
public class SwingRasterizer {

	private final BufferedImage img;
	private final Graphics2D graphics;

	private final int laneWidth;

	SwingRasterizer(AbstractRaster.Bounds bounds, double cellSize, double laneWidth) {

		var width = (bounds.getMaxX() - bounds.getMinX()) / cellSize;
		var height = (bounds.getMaxY() - bounds.getMinY()) / cellSize;
		this.img = new BufferedImage((int) width, (int) height, BufferedImage.TYPE_INT_RGB);
		this.graphics = img.createGraphics();
		graphics.setColor(Color.BLACK);
		this.laneWidth = (int) Math.round(laneWidth / cellSize);
	}

	void rasterLink(Link link, double emissionValue, DoubleRaster target, DoubleRaster buildings) {

		var pixelLine = PixelLine.fromLinkWithOffset(link, this.laneWidth / 2, target);
		var strokeWidth = Math.max(1, (int) link.getNumberOfLanes() * this.laneWidth);
		var area = target.getCellSize() * target.getCellSize();

		// first pass for counting the raster tiles
		var counter = new AtomicInteger();
		rasterPixelLine(pixelLine, strokeWidth, (xi, yi) -> {

			if (!isBuilding(xi, yi, buildings))
				counter.incrementAndGet();
		});

		// second pass for writing raster values
		rasterPixelLine(pixelLine, strokeWidth, (xi, yi) -> {
			if (!isBuilding(xi, yi, buildings))
				target.adjustValueForIndex(xi, yi, emissionValue / counter.get() / area);
		});
	}

	void rasterPixelLine(PixelLine line, int strokeWidth, ThickBresenham.IntBinaryConsumer setPixel) {

		// draw the line onto the image
		this.graphics.setStroke(new BasicStroke(strokeWidth));
		this.graphics.drawLine(line.x0, line.y0, line.x1, line.y1);

		// figure out a bounding box
		int padding = Math.round(strokeWidth >> 1);
		int minX = minWithPadding(line.x0, line.x1, padding, 0);
		int maxX = maxWithPadding(line.x0, line.x1, padding, img.getWidth() - 1);
		int minY = minWithPadding(line.y0, line.y1, padding, 0);
		int maxY = maxWithPadding(line.y0, line.y1, padding, img.getHeight() - 1);

		// read the pixels which were drawn within the bounding box
		for (var xi = minX; xi <= maxX; xi++) {
			for (var yi = minY; yi <= maxY; yi++) {

				int[] result = (int[]) img.getRaster().getDataElements(xi, yi, null);
				if (result[0] != 0) setPixel.accept(xi, yi);
			}
		}

		// clear the image buffer within the bbox
		var dataElement = new int[]{0};
		for (var xi = minX; xi <= maxX; xi++) {
			for (var yi = minY; yi <= maxY; yi++) {
				img.getRaster().setDataElements(xi, yi, dataElement);
			}
		}
	}

	static int minWithPadding(int val1, int val2, int padding, int minLimit) {
		int delta = val2 - val1;
		int min = delta >= 0 ? val1 : val2;
		min -= padding;
		return Math.max(minLimit, min);
	}

	static int maxWithPadding(int val1, int val2, int padding, int maxLimit) {
		int delta = val2 - val1;
		int max = delta >= 0 ? val2 : val1;
		max += padding;
		return Math.min(maxLimit, max);
	}

	static boolean isBuilding(int xi, int yi, DoubleRaster buildings) {
		var value = buildings.getValueByIndex(xi, yi);
		//return value > 0;
		return false;
	}

	record PixelLine(int x0, int y0, int x1, int y1) {
		static PixelLine fromLinkWithOffset(Link link, double offset, AbstractRaster target) {

			var x0 = link.getFromNode().getCoord().getX();
			var y0 = link.getFromNode().getCoord().getY();
			var x1 = link.getToNode().getCoord().getX();
			var y1 = link.getToNode().getCoord().getY();

			var dx = x1 - x0;
			var dy = y1 - y0;
			var v1 = offset * dy / (Math.sqrt(dx * dx + dy * dy));
			var v2 = dy == 0 ? -offset * Math.signum(dx) : -dx * v1 / dy;

			var xi0 = target.getXIndex(x0 + v1);
			var yi0 = target.getYIndex(y0 + v2);
			var xi1 = target.getXIndex(x1 + v1);
			var yi1 = target.getYIndex(y1 + v2);

			//return new PixelLine(xi0, yi0, xi1, yi1);
			return new PixelLine(
					target.getXIndex(x0),
					target.getYIndex(y0),
					target.getXIndex(x1),
					target.getYIndex(y1)
			);
		}
	}
}