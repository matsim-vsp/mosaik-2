package org.matsim.mosaik2.raster;

import java.util.stream.IntStream;

/**
 * Raster holding (x,y, value) values
 * The data is stored within a double[]. The class only offers convenient methods to access this data by (x,y)-coordinates
 */
public class Raster extends AbstractRaster {

    private final double[] data;

    public Raster(Bounds bounds, double cellSize) {
        super(bounds, cellSize);
        this.data = new double[getXLength() * getYLength()];
    }

    /**
     * This iterates over the x and y index of the raster and supplies the corresponding value into the acceptor function
     * At the moment this iteration is done sequentially. But this may change in the future.
     *
     * @param consumer Accepts x and y index and the current value within the raster.
     */
    public void forEachIndex(IndexDoubleConsumer consumer) {
        IntStream.range(0, getXLength()).forEach(xi -> IntStream.range(0, getYLength())
                .forEach(yi -> {
                    var value = getValueByIndex(xi, yi);
                    consumer.consume(xi, yi, value);
                }));
    }

    /**
     * This iterates over the x and y coordinates of the raster and supplies the corresponding value into the acceptor function
     * At the moment this iteration is done sequentially. But this may change in the future.
     *
     * @param consumer Accepts x and y coordinates and the current value within the raster.
     */
    public void forEachCoordinate(CoordDoubleConsumer consumer) {

        IntStream.range(0, getXLength()).forEach(xi -> IntStream.range(0, getYLength())
                .forEach(yi -> {
                    var value = getValueByIndex(xi, yi);
                    var x = xi * getCellSize() + getBounds().getMinX();
                    var y = yi * getCellSize() + getBounds().getMinY();
                    consumer.consume(x, y, value);
                }));
    }

    /**
     * This iterates over the x and y index of the raster. The iteration is done in parallel. The result of the valueSupplier
     * will be set on the corresponding pixel of the raster. This manipulates the state of the raster. Make sure to not alter
     * the state during the execution of this method from outside.
     *
     * @param valueSupplier Function which takes an x and a y index and supplies a double value which is written into
     *                      The corresponding pixel of the raster
     */
    public void setValueForEachIndex(IndexToDoubleFunction valueSupplier) {

        IntStream.range(0, getXLength()).parallel().forEach(xi ->
                IntStream.range(0, getYLength()).forEach(yi -> {
                    var value = valueSupplier.applyAsDouble(xi, yi);
                    adjustValueForIndex(xi, yi, value);
                }));
    }

    public void setValueForEachCoordinate(CoordToDoubleFunction valueSupplier) {
        setValueForEachIndex((xi, yi) -> {
            var x = xi * getCellSize() + getBounds().getMinX();
            var y = yi * getCellSize() + getBounds().getMinY();
            return valueSupplier.applyAsDouble(x, y);
        });
    }

    public double getValueByIndex(int xi, int yi) {

        var index = getIndex(xi, yi);
        return data[index];
    }

    public double getValueByCoord(double x, double y) {
        var index = getIndexForCoord(x, y);
        return data[index];
    }

    public double adjustValueForCoord(double x, double y, double value) {

        var index = getIndexForCoord(x, y);
        return data[index] += value;
    }

    public void adjustValueForIndex(int xi, int yi, double value) {
        var index = getIndex(xi, yi);
        data[index] += value;
    }

    @FunctionalInterface
    public interface IndexDoubleConsumer {
        void consume(int xi, int yi, double value);
    }

    @FunctionalInterface
    public interface CoordDoubleConsumer {
        void consume(double x, double y, double value);
    }

    @FunctionalInterface
    public interface IndexToDoubleFunction {
        double applyAsDouble(int xi, int yi);
    }

    @FunctionalInterface
    public interface CoordToDoubleFunction {
        double applyAsDouble(double x, double y);
    }
}
