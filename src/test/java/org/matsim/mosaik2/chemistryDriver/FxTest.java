package org.matsim.mosaik2.chemistryDriver;

import org.junit.Test;

import java.awt.*;
import java.awt.image.BufferedImage;

public class FxTest {

    @Test
    public void rasterLine() {

        BufferedImage bufferedImage = new BufferedImage(
                10, 10, BufferedImage.TYPE_INT_RGB
        );
        var context = bufferedImage.createGraphics();
        context.setStroke(new BasicStroke(3));
        context.setColor(Color.BLACK);
        context.fillRect(3, 5, 7, 5);

        System.out.println(bufferedImage.getRaster().getDataBuffer().toString());

        for (var i = 0; i < 10; i++) {
            for (var j = 0; j < 10; j++) {
                System.out.println(bufferedImage.getRGB(i, j));
            }
        }
    }
}
