package org.matsim.mosaik2.raster;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.matsim.api.core.v01.Coord;

import java.util.Set;

@Getter
public class AbstractRaster {

	private final Bounds bounds;
	private final double cellSize;
	private final int xLength;
	private final int yLength;

	protected AbstractRaster(Bounds bounds, double cellSize) {

		if ((bounds.maxX - bounds.minX) % cellSize != 0 || (bounds.maxY - bounds.minY) % cellSize != 0)
			throw new IllegalArgumentException("bounds must be a multiple of cell size");

		this.bounds = bounds;
		this.cellSize = cellSize;
		this.xLength = getXIndex(bounds.maxX) + 1;
		this.yLength = getYIndex(bounds.maxY) + 1;
	}

	/**
	 * Transform x-coordinate into index
	 *
	 * @param x x-value of a coordinate
	 * @return x-index
	 */
	public int getXIndex(double x) {
		return (int) ((x + cellSize / 2 - bounds.minX) / cellSize);
	}

	/**
	 * Transform y-coordinate into index
	 *
	 * @param y y-value of a coordinate
	 * @return y-index
	 */
	public int getYIndex(double y) {
		return (int) ((y + cellSize / 2 - bounds.minY) / cellSize);
	}

	int getIndex(int xi, int yi) {
		return yi * xLength + xi;
	}

	int getIndexForCoord(double x, double y) {
		var xi = getXIndex(x);
		var yi = getYIndex(y);

		return getIndex(xi, yi);
	}

	double getCentroidXForIndex(int xi) {
		return xi * cellSize + bounds.minX;
	}

	double getCentroidYForIndex(int yi) {
		return yi * cellSize + bounds.minY;
	}

	@EqualsAndHashCode
	@ToString
	public static class Bounds {
		private double minX = Double.POSITIVE_INFINITY;
		private double minY = Double.POSITIVE_INFINITY;
		private double maxX = Double.NEGATIVE_INFINITY;
		private double maxY = Double.NEGATIVE_INFINITY;

		public Bounds(double minX, double minY, double maxX, double maxY) {
			this.minX = minX;
			this.minY = minY;
			this.maxX = maxX;
			this.maxY = maxY;
		}

		public Bounds(Set<Coord> coords) {
			for (Coord coord : coords) {
				if (coord.getX() < minX) minX = coord.getX();
				if (coord.getY() < minY) minY = coord.getY();
				if (coord.getX() > maxX) maxX = coord.getX();
				if (coord.getY() > maxY) maxY = coord.getY();
			}
		}

		public Bounds(Geometry geometry) {
			this.minX = geometry.getEnvelopeInternal().getMinX();
			this.minY = geometry.getEnvelopeInternal().getMinY();
			this.maxX = geometry.getEnvelopeInternal().getMaxX();
			this.maxY = geometry.getEnvelopeInternal().getMaxY();
		}

		public double getMinX() {
			return minX;
		}

		public double getMinY() {
			return minY;
		}

		public double getMaxX() {
			return maxX;
		}

		public double getMaxY() {
			return maxY;
		}

		public boolean covers(Coord coord) {
			return covers(coord.getX(), coord.getY());
		}

		public boolean covers(double x, double y) {
			return minX <= x && x <= maxX && minY <= y && y <= maxY;
		}

		public Geometry toGeometry() {
			var factory = new GeometryFactory();
			var coordinates = new Coordinate[]{
					new Coordinate(minX, minY), new Coordinate(maxX, minY),
					new Coordinate(maxX, maxY), new Coordinate(minX, maxY),
					new Coordinate(minX, minY)
			};
			return factory.createPolygon(coordinates);
		}
	}
}