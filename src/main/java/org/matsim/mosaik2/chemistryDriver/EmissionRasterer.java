package org.matsim.mosaik2.chemistryDriver;

import lombok.RequiredArgsConstructor;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.contrib.analysis.time.TimeBinMap;
import org.matsim.contrib.emissions.Pollutant;
import org.matsim.core.utils.collections.Tuple;
import org.matsim.core.utils.geometry.geotools.MGC;
import org.matsim.mosaik2.SpatialIndex;
import org.matsim.mosaik2.raster.DoubleRaster;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
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

    static <T> TimeBinMap<Map<T, DoubleRaster>> rasterWithBuffer(TimeBinMap<Map<T, Map<Id<Link>, Double>>> timeBinMap, Network network, DoubleRaster streetTypes) {

        Set<Geometry> geometries = new HashSet<>();
        var factory = new GeometryFactory();
        var halfCellSize = streetTypes.getCellSize() / 2;
        streetTypes.forEachCoordinate((x, y, value) -> {

            var cell = factory.createPolygon(new Coordinate[]{
                    new Coordinate(x - halfCellSize, y - halfCellSize), new Coordinate(x + halfCellSize, y - halfCellSize),
                    new Coordinate(x + halfCellSize, y + halfCellSize), new Coordinate(x - halfCellSize, y + halfCellSize),
                    new Coordinate(x - halfCellSize, y - halfCellSize)
            });
            geometries.add(cell);
        });
        SpatialIndex<Coordinate> index = new SpatialIndex<>(geometries, g -> g.getCentroid().getCoordinate());

        var bufferedNetwork = network.getLinks().values().stream()
                .map(link -> {
                    var lineString = factory.createLineString(new Coordinate[]{
                            MGC.coord2Coordinate(link.getFromNode().getCoord()), MGC.coord2Coordinate(link.getToNode().getCoord())
                    });
                    // think about extrusion or something
                    var buffer = lineString.buffer(link.getNumberOfLanes() * 3.5);
                    return Tuple.of(link.getId(), buffer);
                })
                .collect(Collectors.toMap(Tuple::getFirst, Tuple::getSecond));

        // TODO work on.
    }
}
