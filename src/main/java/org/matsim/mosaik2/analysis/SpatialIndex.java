package org.matsim.mosaik2.analysis;

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

import java.util.HashSet;
import java.util.Set;

@Log4j2
public class SpatialIndex {
	private static final GeometryFactory geomFact = new GeometryFactory();

	private final HPRtree index = new HPRtree();

	SpatialIndex(Network network, double bufferDist, Geometry bounds) {

		log.info("Creating spatial index.");
		var prepFactory = new PreparedGeometryFactory();
		var preparedBounds = prepFactory.create(bounds);

		network.getLinks().values().parallelStream()
				.map(link -> {
					var line = geomFact.createLineString(new Coordinate[]{
							MGC.coord2Coordinate(link.getFromNode().getCoord()), MGC.coord2Coordinate(link.getToNode().getCoord())
					});
					var lineRect = line.buffer(bufferDist, 1, BufferParameters.CAP_SQUARE);
					var preparedRect = prepFactory.create(lineRect);
					return new IndexLine(link.getId(), preparedRect);
				})
				.filter(indexLine -> preparedBounds.covers(indexLine.geometry.getGeometry()))
				.forEach(indexLine -> index.insert(indexLine.geometry.getGeometry().getEnvelopeInternal(), indexLine));

		log.info("Spatial Index loaded. Calling index::build");

		// this is important since otherwise build is implicitly called on index::query. build has side effects though
		// and leads to crashes when query is called in parallel.
		index.build();
		log.info("Finished building index. ");
	}

	Set<Id<Link>> query(double x, double y) {

		var point = geomFact.createPoint(new Coordinate(x, y));
		Set<Id<Link>> result = new HashSet<>();
		index.query(point.getEnvelopeInternal(), item -> {

			var indexLine = (IndexLine) item;
			if (indexLine.geometry.intersects(point))
				result.add(indexLine.id);
		});
		return result;
	}

	record IndexLine(Id<Link> id, PreparedGeometry geometry) {
	}
}