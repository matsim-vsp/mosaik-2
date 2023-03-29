package org.matsim.mosaik2.chemistryDriver;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.awt.image.DataBufferInt;

public class FxTest {



    @Test
    public void rasterLine() throws JsonProcessingException {

        var mapper = new ObjectMapper();

        BufferedImage bufferedImage = new BufferedImage(
                10, 10, BufferedImage.TYPE_INT_RGB
        );
        var context = bufferedImage.createGraphics();
        context.setStroke(new BasicStroke(3));
        context.setColor(Color.BLACK);
        context.fillRect(3, 5, 7, 5);

        /*
        for (var i = 0; i < 10; i++) {
            for (var j = 0; j < 10; j++) {
                int[] result = bufferedImage.getRaster().getPixel(i, j, new int[3]);
                System.out.println(mapper.writeValueAsString(result));
            }
        }

         */

        /*
        var data = ((DataBufferInt) bufferedImage.getRaster().getDataBuffer()).getData();
        for (var datum : data) {
            System.out.println(datum);
        }

         */

var bands = bufferedImage.getRaster().getNumBands();
        for (var i = 0; i < 10; i++) {
            for (var j = 0; j < 10; j++) {
                System.out.println("(" + i + ", " + j + "): " + bufferedImage.getRGB(i, j));
            }
        }


    }
}
