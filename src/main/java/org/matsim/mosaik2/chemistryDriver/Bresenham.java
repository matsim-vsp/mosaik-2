package org.matsim.mosaik2.chemistryDriver;

import gnu.trove.map.TObjectDoubleMap;
import org.locationtech.jts.geom.Geometry;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.core.utils.collections.Tuple;
import org.matsim.core.utils.geometry.geotools.MGC;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public abstract class Bresenham {

    /**
     * rasterizes link onto cells it crosses. The 'line' which is drawn is 1 cell wide. The emissions of the link are
     * equally distributed into the cells. Additionally the emissions per link are divided by cell area. If a link has
     * an emission value of 100g and the cellSize is 10m and the link covers 2 cells the resulting value per cell is
     * 100g / 2 / (10m * 10m) = 0.5g/m2
     */
    static Raster rasterizeNetwork(final Network network, final Raster.Bounds bounds, final TObjectDoubleMap<Id<Link>> emissions, final double cellSize) {

        var raster = new Raster(bounds, cellSize);
        final var area = cellSize * cellSize;

        emissions.forEachEntry((linkId, value) -> {

            var link = network.getLinks().get(linkId);
            // first count number of cells
            var counter = new AtomicInteger();
            rasterizeLink(link, cellSize, (x, y) -> counter.incrementAndGet());
            // second pass for actually writing the emission values
            rasterizeLink(link, cellSize, (x, y) -> {

                if (bounds.covers(x, y))
                    raster.adjustValueForCoord(x, y, value / counter.get() / area);
            });

            return true;
        });

        return raster;
    }

    /**
     * rasterizes link onto cells it crosses. The 'line' which is drawn is 1 cell wide. The emissions of the link are
     * equally distributed into the cells. Additionally the emissions per link are divided by cell area. If a link has
     * an emission value of 100g and the cellSize is 10m and the link covers 2 cells the resulting value per cell is
     * 100g / 2 / (10m * 10m) = 0.5g/m2
     *
     * Duplicate this to use with normal Map<Id<Link>, Double> backed by a fast utils Object2DoubleMap
     */
    static Raster rasterizeNetwork(final Network network, final Raster.Bounds bounds, final Map<Id<Link>, Double> emissions, final double cellSize) {

        var raster = new Raster(bounds, cellSize);
        final var area = cellSize * cellSize;


        for (var entry : emissions.entrySet()) {

            var link = network.getLinks().get(entry.getKey());
            final double value = entry.getValue();

            // first count number of cells
            var counter = new AtomicInteger();
            rasterizeLink(link, cellSize, (x, y) -> counter.incrementAndGet());
            // second pass for actually writing the emission values
            rasterizeLink(link, cellSize, (x, y) -> {

                if (bounds.covers(x, y))
                    raster.adjustValueForCoord(x, y, value / counter.get() / area);
            });
        }

        return raster;
    }

    static Map<Id<Link>, List<Coord>> rasterizeNetwork(final Network network, final Geometry bounds, final double cellSize) {

        return network.getLinks().values().parallelStream()
                // wrap this in a stream to achieve cheap parallelism
                .map(link -> {
                    List<Coord> cells = new ArrayList<>();
                    rasterizeLink(link, cellSize, (x, y) ->  {
                        var coord = new Coord(x, y);
                        if (bounds.covers(MGC.coord2Point(coord))) {
                            cells.add(coord);
                        }
                    });
                    return Tuple.of(link.getId(), cells);
                })
                .collect(Collectors.toMap(Tuple::getFirst, Tuple::getSecond));
    }

    private static void rasterizeLink(Link link, double cellSize, DoubleBinaryConsumer coordConsumer) {

        int x0 = (int) (link.getFromNode().getCoord().getX() / cellSize);
        int x1 = (int) (link.getToNode().getCoord().getX() / cellSize);
        int y0 = (int) (link.getFromNode().getCoord().getY() / cellSize);
        int y1 = (int) (link.getToNode().getCoord().getY() / cellSize);
        int dx = Math.abs(x1 - x0);
        int dy = -Math.abs(y1 - y0);
        int err = dx + dy, e2;

        int sx = x0 < x1 ? 1 : -1;
        int sy = y0 < y1 ? 1 : -1;

        if (dx == 0 && dy == 0) {
            // the algorithm doesn't really support lines shorter than the cell size.
            // do avoid complicated computation within the loop, catch this case here
            coordConsumer.accept(x0 * cellSize , y0 * cellSize);
        }

        do {
            coordConsumer.accept(x0 * cellSize , y0 * cellSize);

            e2 = err + err;
            if (e2 >= dy) {
                err += dy;
                x0 += sx;
            }
            if (e2 <= dx) {
                err += dx;
                y0 += sy;
            }
            // have this condition in separate method because we want to get one more cell than the original algorithm
            // but then the direction of the line requires different conditions
        } while (keepRasterizing(x0, x1, sx) && keepRasterizing(y0, y1, sy));
    }

    private static boolean keepRasterizing(int value, int endCondition, int direction) {

        if (direction > 0) return value <= endCondition;
        else return value >= endCondition;
    }

    @FunctionalInterface
    private interface DoubleBinaryConsumer {
        void accept(double x, double y);
    }
}
