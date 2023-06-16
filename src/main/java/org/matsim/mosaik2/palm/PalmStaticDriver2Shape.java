package org.matsim.mosaik2.palm;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.geotools.referencing.CRS;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Polygon;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.utils.geometry.geotools.MGC;
import org.matsim.core.utils.gis.ShapeFileWriter;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.referencing.FactoryException;

import java.nio.file.Paths;
import java.util.Set;

public class PalmStaticDriver2Shape {

    @Parameter(names = "-s")
    private String staticFile;

    @Parameter(names = "-o")
    private String outputFile;

    @Parameter(names = "-v")
    private String variable = "buildings_2d";

    public static void main(String[] args) {

        var converter = new PalmStaticDriver2Shape();
        JCommander.newBuilder().addObject(converter).build().parse(args);
        converter.convert();
    }

    private void convert() {

        var data = PalmStaticDriverReader.read(Paths.get(staticFile), variable);
        var geometry = data.getBounds().toGeometry();
        var builder = new SimpleFeatureBuilder(getFeatureType());
        var feature = builder.buildFeature("Domain Boundary");
        feature.setAttribute("the_geom", geometry);
        ShapeFileWriter.writeGeometries(Set.of(feature), outputFile);
    }

    private static SimpleFeatureType getFeatureType() {
        var b = new SimpleFeatureTypeBuilder();
        b.setName("Domain Boundary");
        b.add("the_geom", Polygon.class);
        try {
            b.setCRS(CRS.decode("EPSG:25833"));
        } catch (FactoryException e) {
            throw new RuntimeException(e);
        }
        return b.buildFeatureType();
    }

    private static SimpleFeature createFeature(Coord from, Coord to, Id<Person> agentId, int featureId, GeometryFactory f, SimpleFeatureBuilder b) {
        var line = f.createLineString(new Coordinate[]{
                MGC.coord2Coordinate(from),
                MGC.coord2Coordinate(to)
        });
        var feature = b.buildFeature(Integer.toString(featureId));
        feature.setAttribute("agentId", agentId.toString());
        feature.setAttribute("the_geom", line); // somehow setDefaultGeometry won't do the trick. This internal string works though 🙄
        return feature;
    }
}
