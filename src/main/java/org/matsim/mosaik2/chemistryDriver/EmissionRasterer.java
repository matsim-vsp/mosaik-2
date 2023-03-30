package org.matsim.mosaik2.chemistryDriver;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.operation.buffer.BufferParameters;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.contrib.analysis.time.TimeBinMap;
import org.matsim.core.utils.collections.Tuple;
import org.matsim.mosaik2.SpatialIndex;
import org.matsim.mosaik2.raster.DoubleRaster;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Log4j2
public class EmissionRasterer {

	public static final double LANE_WIDTH = 3.5;

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

	static <T> TimeBinMap<Map<T, DoubleRaster>> rasterWithSwing(TimeBinMap<Map<T, Map<Id<Link>, Double>>> timeBinMap, Network network, DoubleRaster buildings) {

		var rasterizer = new SwingRasterizer(buildings.getBounds(), buildings.getCellSize(), 5);
		// init result map here, so we can rasterize concurrently
		TimeBinMap<Map<T, DoubleRaster>> result = new TimeBinMap<>(timeBinMap.getBinSize());
		for (var bin : timeBinMap.getTimeBins()) {
			var startTime = bin.getStartTime();
			result.getTimeBin(startTime);
		}

		result.getTimeBins().stream().forEach(resultBin -> {

			var bin = timeBinMap.getTimeBin(resultBin.getStartTime());
			log.info("Processing time slice: " + bin.getStartTime());
			HashMap<T, DoubleRaster> pollutant2Raster = new HashMap<>();

			for (var pollutantEntry : bin.getValue().entrySet()) {
				var emissionsByLink = pollutantEntry.getValue();
				var raster = new DoubleRaster(buildings.getBounds(), buildings.getCellSize());

				for (var linkEmission : emissionsByLink.entrySet()) {
					var link = network.getLinks().get(linkEmission.getKey());
					rasterizer.rasterLink(link, linkEmission.getValue(), raster, buildings);
				}

				pollutant2Raster.put(pollutantEntry.getKey(), raster);
			}
			resultBin.setValue(pollutant2Raster);
		});
		return result;
	}

	static <T> TimeBinMap<Map<T, DoubleRaster>> rasterWithBuffer(TimeBinMap<Map<T, Map<Id<Link>, Double>>> timeBinMap, Network network, DoubleRaster buildings) {

		log.info("Starting raster process of buffered link geometries.");
		Set<Geometry> geometries = new HashSet<>();
		var factory = new GeometryFactory();
		var halfCellSize = buildings.getCellSize() / 2;

		log.info("Create geometries for raster cells.");
		buildings.forEachCoordinate((x, y, value) -> {

			// don't create cells for buildings.
			if (value > 0) return;

			var cell = factory.createPolygon(new Coordinate[]{
					new Coordinate(x - halfCellSize, y - halfCellSize), new Coordinate(x + halfCellSize, y - halfCellSize),
					new Coordinate(x + halfCellSize, y + halfCellSize), new Coordinate(x - halfCellSize, y + halfCellSize),
					new Coordinate(x - halfCellSize, y - halfCellSize)
			});
			geometries.add(cell);
		});

		SpatialIndex<Coordinate> index = new SpatialIndex<>(geometries, g -> g.getCentroid().getCoordinate());

		log.info("Create geometries for links.");
		var bufferdLinks = network.getLinks().values().stream()
				.map(link -> {
					var lineString = factory.createLineString(new Coordinate[]{
							intoOffsetCoordinate(link.getFromNode().getCoord()), intoOffsetCoordinate(link.getToNode().getCoord())
					});
					var buffer = lineString.buffer(link.getNumberOfLanes() * 3.5, 0, BufferParameters.CAP_FLAT);
					return Tuple.of(link.getId(), buffer);
				})
				.collect(Collectors.toMap(Tuple::getFirst, Tuple::getSecond));
		log.info("Finished creating link geometries");

		// init result map here, so we can rasterize concurrently
		TimeBinMap<Map<T, DoubleRaster>> result = new TimeBinMap<>(timeBinMap.getBinSize());
		for (var bin : timeBinMap.getTimeBins()) {

			// only take the first 24h
			if (bin.getStartTime() > 86401) break;

			var startTime = bin.getStartTime();
			result.getTimeBin(startTime);
		}

		log.info("Start rasterizing emissions. ");
		result.getTimeBins().parallelStream().forEach(resultBin -> {

			log.info("Rastering timestep: " + resultBin.getStartTime());
			var bin = timeBinMap.getTimeBin(resultBin.getStartTime());
			var rasterByPollutant = bin.getValue().entrySet().stream()
					.map(entry -> {
						var emissionsByLink = entry.getValue();
						var raster = new DoubleRaster(buildings.getBounds(), buildings.getCellSize());

						for (var linkEmission : emissionsByLink.entrySet()) {
							var bufferedLinkGeometry = bufferdLinks.get(linkEmission.getKey());
							var cells = index.intersects(bufferedLinkGeometry);
							for (var cell : cells) {
								var value = linkEmission.getValue();
								raster.adjustValueForCoord(cell.getX(), cell.getY(), value / cells.size());
							}
						}
						return Tuple.of(entry.getKey(), raster);
					})
					.collect(Collectors.toMap(Tuple::getFirst, Tuple::getSecond));
			resultBin.setValue(rasterByPollutant);
		});




        /*

        for (var bin : timeBinMap.getTimeBins()) {

            var rasterByPollutant = bin.getValue().entrySet().stream()
                    .map(entry -> {
                        var emissionsByLink = entry.getValue();
                        var raster = new DoubleRaster(buildings.getBounds(), buildings.getCellSize());

                        for (var linkEmission : emissionsByLink.entrySet()) {
                            var bufferedLinkGeometry = bufferdLinks.get(linkEmission.getKey());
                            var cells = index.intersects(bufferedLinkGeometry);
                            for (var cell : cells) {
                                var value = linkEmission.getValue();
                                raster.adjustValueForCoord(cell.getX(), cell.getY(), value / cells.size());
                            }
                        }
                        return Tuple.of(entry.getKey(), raster);
                    })
                    .collect(Collectors.toMap(Tuple::getFirst, Tuple::getSecond));
            result.getTimeBin(bin.getStartTime()).setValue(rasterByPollutant);
        }
        log.info("Finished rasterizing emissions.");

         */
		return result;
	}

	static Coordinate intoOffsetCoordinate(Coord coord) {

		// edge case: if y is 0, we have to swap variables because otherwise we divide by 0
		// at the end of the method we have to swap back and change the sign
		var u1 = coord.getY() != 0 ? coord.getX() : coord.getY();
		var u2 = coord.getY() != 0 ? coord.getY() : coord.getX();

		// we'll calculate a perpendicular vector to coord (which is a vector as well) with a fixed length (halve lane width)
		// we do this based on the dot product 0 = U dot V -> 0 = u1v1 + u2v2 <-> v1 = -u2v2/u1 (1)
		// also the magnitude of the vector is l = sqrt(v1^2 + v2^2) -> v1 = sqrt(l^2 - v1^2)   (2)
		// put (2) into (1) -> v1 = lu2 / sqrt(u1^2 + u2^2)
		// then dot product for v2: v2 = -u1v1/u2
		var offset = LANE_WIDTH / 2;
		var v1 = offset * u2 / (Math.sqrt(u1 * u1 + u2 * u2));
		var v2 = -u1 * v1 / u2;

		var deltaX = coord.getY() != 0 ? v1 : -v2;
		var deltaY = coord.getY() != 0 ? v2 : -v1;

		return new Coordinate(coord.getX() + deltaX, coord.getY() + deltaY);
	}
}