package org.matsim.mosaik2.raster;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class ObjectRasterTest {

    @Test
    public void testConstructor() {

        var length = 10;
        var cellSize = 2;
        var bounds = new AbstractRaster.Bounds(0, 0, length, length);

        var raster = new ObjectRaster<>(bounds, cellSize);

        assertEquals(length / cellSize + 1, raster.getXLength());
        assertEquals(length / cellSize + 1, raster.getYLength());
        assertEquals(cellSize, raster.getCellSize(), Double.MIN_VALUE);
        assertEquals(bounds, raster.getBounds());
        raster.forEachIndex((xi, yi, value) -> assertNull(value));
    }

    @Test
    public void testSetValue() {

        var length = 10;
        var cellSize = 2;
        var bounds = new AbstractRaster.Bounds(0, 0, length, length);
        var raster = new ObjectRaster<TestClass>(bounds, cellSize);

        raster.setValueForEachIndex((xi, yi) -> new TestClass(xi + "_" + yi));

        raster.forEachIndex((xi, yi, value) -> assertEquals(xi + "_" + yi, value.getProperty()));
    }

    @Getter
    @RequiredArgsConstructor
    private static class TestClass {

        private final String property;
    }
}