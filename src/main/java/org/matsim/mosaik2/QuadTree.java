package org.matsim.mosaik2;

import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.prep.PreparedGeometry;
import org.locationtech.jts.index.quadtree.Quadtree;

import java.util.HashSet;
import java.util.Set;

public class QuadTree<T> {

	private final Quadtree index = new Quadtree();

	public void insert(Geometry geometry, T item) {
		index.insert(geometry.getEnvelopeInternal(), new IndexItem<>(geometry, item));
	}

	public Set<T> intersects(PreparedGeometry geometry) {

		Set<T> result = new HashSet<>();
		index.query(geometry.getGeometry().getEnvelopeInternal(), entry -> {
			@SuppressWarnings("unchecked") // suppress warning, since we know that entry is an IndexItem<T>
			IndexItem<T> indexItem = (IndexItem<T>) entry;
			if (geometry.intersects(indexItem.geom())) {
				result.add(indexItem.item);
			}
		});
		return result;
	}

	public Set<T> coveredBy(PreparedGeometry geometry) {
		Set<T> result = new HashSet<>();
		index.query(geometry.getGeometry().getEnvelopeInternal(), entry -> {
			@SuppressWarnings("unchecked") // suppress warning, since we know that entry is an IndexItem<T>
			IndexItem<T> indexItem = (IndexItem<T>) entry;
			if (geometry.covers(indexItem.geom())) {
				result.add(indexItem.item());
			}
		});
		return result;
	}

	/**
	 * '
	 * Return all items that are covered by the geometry supplied as argument
	 *
	 * @param geometry the spatial filter by which items in the index are filtered
	 * @return all covered items
	 */
	public Set<T> coveredBy(Geometry geometry) {

		Set<T> result = new HashSet<>();
		index.query(geometry.getEnvelopeInternal(), entry -> {
			@SuppressWarnings("unchecked") // suppress warning, since we know that entry is an IndexItem<T>
			IndexItem<T> indexItem = (IndexItem<T>) entry;
			if (geometry.covers(indexItem.geom())) {
				result.add(indexItem.item());
			}
		});
		return result;
	}

	/**
	 * Inverse of {@link #coveredBy(Geometry)}
	 * Finds items which cover the supplied geometry.
	 */
	public Set<T> allCover(Geometry geometry) {
		Set<T> result = new HashSet<>();
		index.query(geometry.getEnvelopeInternal(), entry -> {
			@SuppressWarnings("unchecked") // suppress warning, since we know that entry is an IndexItem<T>
			IndexItem<T> indexItem = (IndexItem<T>) entry;
			if (indexItem.geom().covers(geometry)) {
				result.add(indexItem.item());
			}
		});
		return result;
	}

	private record IndexItem<T>(Geometry geom, T item) {
	}
}