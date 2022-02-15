package org.matsim.mosaik2.analysis;

import it.unimi.dsi.fastutil.objects.Object2DoubleOpenHashMap;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.network.Link;
import org.matsim.mosaik2.chemistryDriver.Raster;

import java.util.Map;

public class AverageSmoothingRadiusEstimate {

    public static Map<Coord, Double> collectR(Raster raster, Map<Link, Double> emissions) {

        var Rs = new Object2DoubleOpenHashMap<Coord>();

        raster.forEachCoordinate((x, y, value) -> {
            var coord = new Coord(x, y);
            var R = SmoothingRadiusEstimate.estimateR(emissions, coord, 20, value);
            Rs.put(coord, R);
        });

        return Rs;
    }
}
