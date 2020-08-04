package org.matsim.mosaik2.prepare;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.prep.PreparedGeometry;
import org.locationtech.jts.geom.prep.PreparedGeometryFactory;
import org.matsim.api.core.v01.network.NetworkWriter;
import org.matsim.contrib.osm.networkReader.SupersonicOsmNetworkReader;
import org.matsim.core.utils.geometry.CoordinateTransformation;
import org.matsim.core.utils.geometry.geotools.MGC;
import org.matsim.core.utils.geometry.transformations.TransformationFactory;

public class CreateNetwork {

	private static final CoordinateTransformation transformation = TransformationFactory.getCoordinateTransformation("EPSG:4326", "EPSG:25832");

	public static void main(String[] args) {

		var arguments = new InputArgs();
		JCommander.newBuilder().addObject(arguments).build().parse(args);

		var bounds = CreateNetwork.getBounds();

		var network = new SupersonicOsmNetworkReader.Builder()
				.setCoordinateTransformation(transformation)
				.setIncludeLinkAtCoordWithHierarchy((coord, id) -> bounds.covers(MGC.coord2Point(coord)))
				.build()
				.read(arguments.inputFile);

		new NetworkWriter(network).write(arguments.outputFile);
	}

	private static PreparedGeometry getBounds() {

		var gFactory = new GeometryFactory();
		var geometry = gFactory.createPolygon(new Coordinate[]{
				new Coordinate(395063.25, 5564101.5),
				new Coordinate(395063.25, 5348301),
				new Coordinate(660442.25, 5348301),
				new Coordinate(660442.25, 395063.25),
				new Coordinate(395063.25, 5564101.5)
		});

		var pFactory = new PreparedGeometryFactory();
		return pFactory.create(geometry);
	}

	private static class InputArgs {

		@Parameter(names = {"-input"}, required = true)
		String inputFile = "Input/europe-latest.osm.pbf";

		@Parameter(names = {"-output"}, required = true)
		String outputFile = "Output/Network.xml.gz";
	}
}
