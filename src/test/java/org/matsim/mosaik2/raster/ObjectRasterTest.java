package org.matsim.mosaik2.raster;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.junit.Test;

import static org.junit.Assert.*;

public class ObjectRasterTest {

    @Test
    public void testConstructor() {

        var length = 10;
        var cellSize = 2;
        var bounds = new AbstractRaster.Bounds(0, 0, length, length);

        var raster = new ObjectRaster<>(bounds, cellSize);

        assertEquals((length / cellSize) + 1, raster.getXLength());
        assertEquals((length / cellSize) + 1, raster.getYLength());
        assertEquals(cellSize, raster.getCellSize(), Double.MIN_VALUE);
        assertEquals(bounds, raster.getBounds());
        raster.forEachIndex((xi, yi, value) -> assertNull(value));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConstructorInvalidArgs() {
        var length = 11;
        var cellSize = 2;
        var bounds = new AbstractRaster.Bounds(0, 0, length, length);

        new ObjectRaster<>(bounds, cellSize);

        fail("Expected IllegalArgumentException");
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

    @Test
    public void testCorrectCoords() {

        var min = 10;
        var length = 10;
        var cellSize = 2;
        var bounds = new AbstractRaster.Bounds(min, min, min + length, min + length);
        var raster = new ObjectRaster<TestClass>(bounds, cellSize);

        // test lower bounds
        assertEquals(0, raster.getIndexForCoord(9, 9));

        // test upper bounds
        assertEquals(((long) raster.getXLength() * raster.getYLength()) - 1, raster.getIndexForCoord(20.9, 20.9));

        // test some centroid in between
        assertEquals(14, raster.getIndexForCoord(14, 14));
    }

    @Test
    public void getXCentroid() {

        var min = 10;
        var length = 10;
        var cellSize = 2;
        var bounds = new AbstractRaster.Bounds(min, min, min + length, min + length);
        var raster = new ObjectRaster<TestClass>(bounds, cellSize);

        var xMin = raster.getCentroidXForIndex(0);
        assertEquals(min, xMin, 0.00001);

        var xMax = raster.getCentroidXForIndex(5);
        assertEquals(min + length, xMax, 0.00001);

        var xSome = raster.getCentroidXForIndex(2);
        assertEquals(14, xSome, 0.000001);
    }

    @Test
    public void getYCentroid() {
        var min = 10;
        var length = 10;
        var cellSize = 2;
        var bounds = new AbstractRaster.Bounds(min, min, min + length, min + length);
        var raster = new ObjectRaster<TestClass>(bounds, cellSize);

        var yMin = raster.getCentroidYForIndex(0);
        assertEquals(min, yMin, 0.00001);

        var yMax = raster.getCentroidYForIndex(5);
        assertEquals(min + length, yMax, 0.00001);

        var ySome = raster.getCentroidYForIndex(2);
        assertEquals(14, ySome, 0.000001);
    }

    @Getter
    @RequiredArgsConstructor
    private static class TestClass {

        private final String property;
    }
}