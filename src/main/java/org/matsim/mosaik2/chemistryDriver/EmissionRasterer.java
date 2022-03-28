package org.matsim.mosaik2.chemistryDriver;

import lombok.RequiredArgsConstructor;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.contrib.analysis.time.TimeBinMap;
import org.matsim.contrib.emissions.Pollutant;
import org.matsim.core.utils.collections.Tuple;
import org.matsim.mosaik2.raster.DoubleRaster;

import java.util.Map;
import java.util.stream.Collectors;

@RequiredArgsConstructor
public class EmissionRasterer {

    private final Map<Pollutant, String> pollutantToPalmName;
    private final Network network;
    private final DoubleRaster.Bounds bounds;
    private final double cellSize;

    static <T> TimeBinMap<Map<T, DoubleRaster>> raster(TimeBinMap<Map<T, Map<Id<Link>, Double>>> timeBinMap, Network network, DoubleRaster.Bounds bounds, double cellSize) {

        TimeBinMap<Map<T, DoubleRaster>> rasterTimeBinMap = new TimeBinMap<>(timeBinMap.getBinSize());

        for (var bin : timeBinMap.getTimeBins()) {

            var rasterByPollutant = bin.getValue().entrySet().stream()
                    .map(entry -> {
                        var emissionsByLink = entry.getValue();
                        var raster = Bresenham.rasterizeNetwork(network, bounds, emissionsByLink, cellSize);
                        return Tuple.of(entry.getKey(), raster);
                    })
                    .collect(Collectors.toMap(Tuple::getFirst, Tuple::getSecond));

            rasterTimeBinMap.getTimeBin(bin.getStartTime()).setValue(rasterByPollutant);
        }
        return rasterTimeBinMap;
    }
}
