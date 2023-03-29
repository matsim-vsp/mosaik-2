package org.matsim.mosaik2.chemistryDriver;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.mosaik2.raster.AbstractRaster;
import org.matsim.mosaik2.raster.DoubleRaster;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static com.jogamp.opengl.math.FloatUtil.sqrt;
import static java.lang.Math.abs;

public class ThickBresenham {

    static DoubleRaster rasterizeNetwork(final Network network, final AbstractRaster.Bounds bounds, final Map<Id<Link>, Double> emissions, final double cellSize) {

        var raster = new DoubleRaster(bounds, cellSize);
        final var area = cellSize * cellSize;

        for (var entry : emissions.entrySet()) {

            var link = network.getLinks().get(entry.getKey());
            var line = Line.fromLink(link, raster);
            final double value = entry.getValue();

            var counter = new AtomicInteger();
            plotLineWidth(
                    line.x0, line.x1, line.y0, line.y1, 5, ((xi, yi) -> counter.incrementAndGet())
            );
            plotLineWidth(
                    line.x0, line.x1, line.y0, line.y1, 5, (xi, yi) -> {
                        raster.adjustValueForIndex(xi, yi, value / counter.get() / area);
                    }
            );
        }

        return raster;
    }

    static void rasterizeLink(Line line, double cellSize, double width, IntBinaryConsumer pixelConsumer) {

        int x0 = line.x0;
        int x1 = line.x1;
        int y0 = line.y0;
        int y1 = line.y1;
        int dx = abs(line.x1 - line.x0);
        int dy = -abs(line.y1 - line.y0);
        int err = dx + dy, e2;
        double ed = dx + dy == 0 ? 1 : Math.sqrt((float) dx * dx + (float) dy * dy);
        int x2, y2;

        int sx = line.x0 < line.x1 ? 1 : -1;
        int sy = line.y0 < line.y1 ? 1 : -1;

        //TODO think about whether this is still necessary
       /* if (dx == 0 && dy == 0) {
            // the algorithm doesn't really support lines shorter than the cell size.
            // do avoid complicated computation within the loop, catch this case here
            coordConsumer.accept(line.x0 * cellSize, line.y0 * cellSize);
        }

        */

        do {
            pixelConsumer.accept(x0, y0);
            e2 = err + err;
            x2 = x0;
            if (e2 >= dx) {
                for (e2 += dy, y2 = y0; e2 < ed * width && (y1 != y2 || dx > dy); e2 += dx) {
                    pixelConsumer.accept(x0, y2);
                }
            }
        } while (true);

    }

    public static void plotLine(int x0, int y0, int x1, int y1) {
        int dx = abs(x1 - x0);
        int sx = x0 < x1 ? 1 : -1;
        int dy = -abs(y1 - y0);
        int sy = y0 < y1 ? 1 : -1;
        int err = dx + dy, e2; /* error value e_xy */

        for (; ; ) { /* loop */
            setPixel(x0, y0);
            e2 = 2 * err;
            if (e2 >= dy) { /* e_xy+e_x > 0 */
                if (x0 == x1) break;
                err += dy;
                x0 += sx;
            }
            if (e2 <= dx) { /* e_xy+e_y < 0 */
                if (y0 == y1) break;
                err += dx;
                y0 += sy;
            }
        }
    }

    public static void setPixel(int xi, int yi) {
        System.out.println("Setting pixel: " + xi + "," + yi);
    }

    static void plotLineWithOffset(int x0, int y0, int x1, int y1, float th, IntBinaryConsumer setPixel) {
        // move line to the right
        var offset = th / 2;
        var u1 = x1 - x0;
        var u2 = y1 - y0;

        var v1 = offset * u2 / (Math.sqrt(u1 * u1 + u2 * u2));
        var v2 = v1 == 0 ? 1 : -u1 * v1 / u2;

        x0 += v1;
        x1 += v1;
        y0 += v2;
        y1 += v2;

        plotLineWidth(x0, y0, x1, y1, th, setPixel);
    }

    static void plotLineWidth(int x0, int y0, int x1, int y1, float th, IntBinaryConsumer setPixel) { /* plot an anti-aliased line of thickness th */
        int dx = abs(x1 - x0), sx = x0 < x1 ? 1 : -1;
        int dy = abs(y1 - y0), sy = y0 < y1 ? 1 : -1;
        int err = dx - dy, e2, x2, y2; /* error value e_xy */
        float ed = dx + dy == 0 ? 1 : sqrt((float) dx * dx + (float) dy * dy);
        for (th = (th + 1) / 2; ; ) { /* pixel loop */
            setPixel.accept(x0, y0);
            e2 = err;
            x2 = x0;
            if (2 * e2 >= -dx) { /* x step */
                for (e2 += dy, y2 = y0; e2 < ed * th && (y1 != y2 || dx > dy); e2 += dx)
                    setPixel.accept(x0, y2 += sy);
                if (x0 == x1) break;
                e2 = err;
                err -= dy;
                x0 += sx;
            }
            if (2 * e2 <= dy) { /* y step */
                for (e2 = dx - e2; e2 < ed * th && (x1 != x2 || dx < dy); e2 += dy)
                    setPixel.accept(x2 += sx, y0);
                if (y0 == y1) break;
                err += dx;
                y0 += sy;
            }
        }
    }

    record Line(int x0, int x1, int y0, int y1) {

        static Line fromLink(Link link, AbstractRaster targetRaster) {

            return new Line(
                    targetRaster.getXIndex(link.getFromNode().getCoord().getX()),
                    targetRaster.getXIndex(link.getToNode().getCoord().getX()),
                    targetRaster.getYIndex(link.getFromNode().getCoord().getY()),
                    targetRaster.getYIndex(link.getToNode().getCoord().getY())
            );
        }
    }

    @FunctionalInterface
    interface IntBinaryConsumer {
        void accept(int xi, int yi);
    }
}
