package org.matsim.mosaik2.analysis;

import it.unimi.dsi.fastutil.doubles.Double2ObjectMap;
import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import it.unimi.dsi.fastutil.objects.Object2DoubleMap;
import it.unimi.dsi.fastutil.objects.Object2DoubleOpenHashMap;
import lombok.extern.log4j.Log4j2;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.network.Link;
import org.matsim.mosaik2.chemistryDriver.Raster;

import java.util.Map;

@Log4j2
public class AverageSmoothingRadiusEstimate {

    public static Raster collectR(Raster raster, Object2DoubleMap<Link> emissions) {

        var result = new Raster(raster.getBounds(), raster.getCellSize());

        log.info("Starting to calculate Rs. This will be " + (raster.getYLength() * raster.getXLength() * emissions.size()) + " operations.");
        result.setValueForEachCoordinate((x, y) -> {
            var receiverPoint = new Coord(x,y);
            var value = raster.getValueByCoord(x, y);
            return value <= 0 ? 0.0 : NumericSmoothingRadiusEstimate.estimateRWithBisect(emissions, receiverPoint, value);
        });
        log.info("Finished R calculation");

        return result;
    }
}
