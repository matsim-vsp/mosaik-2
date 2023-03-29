package org.matsim.mosaik2.chemistryDriver;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;

import java.awt.*;
import java.awt.image.BufferedImage;

public class FxTest {

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


	@Test
	public void rasterLine() throws JsonProcessingException {

		var mapper = new ObjectMapper();

		int width = 10;
		int height = 10;

		// input
		int w = 4;
		int x0 = 1;
		int x1 = 5;
		int y0 = 1;
		int y1 = 4;

		// vars to calculate with
		int padding = Math.round(w / 2);
		int minX = minWithPadding(x0, x1, padding, 0);
		int maxX = maxWithPadding(x0, x1, padding, width);
		int minY = minWithPadding(y0, y1, padding, 0);
		int maxY = maxWithPadding(y0, y1, padding, height);

		var img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		var ctx = img.createGraphics();
		ctx.setStroke(new BasicStroke(w));
		ctx.setColor(Color.BLACK);
		ctx.drawLine(x0, y0, x1, y1);

		for (var xi = minX; xi <= maxX; xi++) {
			for (var yi = minY; yi <= maxY; yi++) {
				Object result = img.getRaster().getDataElements(xi, yi, null);
				System.out.println("(" + xi + "," + yi + "): " + mapper.writeValueAsString(result));
			}
		}

		// clear the buffer
		var dataElement = new int[]{0};
		for (var xi = minX; xi <= maxX; xi++) {
			for (var yi = minY; yi <= maxY; yi++) {
				img.getRaster().setDataElements(xi, yi, dataElement);
			}
		}
		System.out.println("\nAfter clear\n");
		for (var xi = 0; xi < width; xi++) {
			for (var yi = 0; yi < height; yi++) {
				Object result = img.getRaster().getDataElements(xi, yi, null);
				System.out.println("(" + xi + "," + yi + "): " + mapper.writeValueAsString(result));
			}
		}
	}
}