package org.matsim.mosaik2.raster;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

public class ObjectRaster<T> extends AbstractRaster {

    private List<T> data;

    protected ObjectRaster(Bounds bounds, double cellSize) {
        super(bounds, cellSize);
        this.data = new ArrayList<>(getXLength() * getYLength());
    }

    /**
     * This iterates over the x and y index of the raster and supplies the corresponding value into the acceptor function
     * At the moment this iteration is done sequentially. But this may change in the future.
     *
     * @param consumer Accepts x and y index and the current value within the raster.
     */
    public void forEachIndex(IndexObjectConsumer<T> consumer) {
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
    public void forEachCoordinate(CoordObjectConsumer<T> consumer) {

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
    public void setValueForEachIndex(IndexToObjectFunction<T> valueSupplier) {

        IntStream.range(0, getXLength()).parallel().forEach(xi ->
                IntStream.range(0, getYLength()).forEach(yi -> {
                    var value = valueSupplier.apply(xi, yi);
                    adjustValueForIndex(xi, yi, value);
                }));
    }

    public void setValueForEachCoordinate(CoordToObjectFunction<T> valueSupplier) {
        setValueForEachIndex((xi, yi) -> {
            var x = xi * getCellSize() + getBounds().getMinX();
            var y = yi * getCellSize() + getBounds().getMinY();
            return valueSupplier.apply(x, y);
        });
    }

    public T getValueByIndex(int xi, int yi) {

        var index = getIndex(xi, yi);
        return data.get(index);
    }

    public void adjustValueForIndex(int xi, int yi, T value) {
        var index = getIndex(xi, yi);
        data.set(index, value);
    }

    @FunctionalInterface
    public interface IndexObjectConsumer<T> {
        void consume(int xi, int yi, T value);
    }

    @FunctionalInterface
    public interface CoordObjectConsumer<T> {
        void consume(double x, double y, T value);
    }

    @FunctionalInterface
    public interface IndexToObjectFunction<T> {
        T apply(int xi, int yi);
    }

    @FunctionalInterface
    public interface CoordToObjectFunction<T> {
        T apply(double x, double y);
    }
}
