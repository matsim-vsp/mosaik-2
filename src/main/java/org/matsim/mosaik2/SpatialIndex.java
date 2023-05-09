package org.matsim.mosaik2;

import lombok.extern.log4j.Log4j2;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.prep.PreparedGeometry;
import org.locationtech.jts.geom.prep.PreparedGeometryFactory;
import org.locationtech.jts.index.hprtree.HPRtree;
import org.locationtech.jts.operation.buffer.BufferParameters;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.core.utils.geometry.geotools.MGC;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

@Log4j2
public class SpatialIndex<T> {

	private static final PreparedGeometryFactory preparedFact = new PreparedGeometryFactory();
	private static final GeometryFactory geometryFact = new GeometryFactory();

	private final HPRtree index = new HPRtree();

	public SpatialIndex(Collection<GeometryItem<T>> geometries) {

		log.info("Create Spatial index for " + geometries.size() + " geometries.");
		geometries.stream()
				.map(geomItem -> new Entry<>(geomItem.item, preparedFact.create(geomItem.geom)))
				.forEach(entry -> index.insert(entry.prepGeom.getGeometry().getEnvelopeInternal(), entry));

		index.build();
		log.info("Finished creating spatial index.");
	}

	public Set<T> intersects(double x, double y) {
		var point = geometryFact.createPoint(new Coordinate(x, y));
		return intersects(point);
	}

	public Set<T> intersects(Geometry geom) {

		Set<T> result = new HashSet<>();
		index.query(geom.getEnvelopeInternal(), rawEntry -> {
			@SuppressWarnings("unchecked") // we are confident that the entry is what we have put inside.
			var entry = (Entry<T>) rawEntry;
			if (entry.prepGeom.intersects(geom)) {
				result.add(entry.item());
			}
		});
		return result;
	}

	record Entry<T>(T item, PreparedGeometry prepGeom) {
	}

	public record GeometryItem<T>(T item, Geometry geom) {
	}

	public static SpatialIndex<Id<Link>> create(Network net, double bufferDist, Geometry bounds) {

		log.info("Creating spatial index from Network.");
		var preparedBounds = preparedFact.create(bounds);
		var itemCollection = net.getLinks().values().stream()
				.map(link -> {
					var line = geometryFact.createLineString(new Coordinate[]{
							MGC.coord2Coordinate(link.getFromNode().getCoord()), MGC.coord2Coordinate(link.getToNode().getCoord())
					});
					var buffered = line.buffer(bufferDist, 1, BufferParameters.CAP_SQUARE);
					return new GeometryItem<>(link.getId(), buffered);
				})
				.filter(entry -> preparedBounds.covers(entry.geom))
				.collect(Collectors.toSet());

		return new SpatialIndex<>(itemCollection);
	}
}