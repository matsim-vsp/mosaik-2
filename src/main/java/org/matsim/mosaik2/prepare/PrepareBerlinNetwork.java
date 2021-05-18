package org.matsim.mosaik2.prepare;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.geotools.geometry.jts.JTS;
import org.geotools.referencing.CRS;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.prep.PreparedGeometry;
import org.locationtech.jts.geom.prep.PreparedGeometryFactory;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.contrib.osm.networkReader.LinkProperties;
import org.matsim.contrib.osm.networkReader.SupersonicOsmNetworkReader;
import org.matsim.core.network.algorithms.NetworkCleaner;
import org.matsim.core.network.algorithms.NetworkSimplifier;
import org.matsim.core.network.io.NetworkWriter;
import org.matsim.core.utils.geometry.CoordinateTransformation;
import org.matsim.core.utils.geometry.geotools.MGC;
import org.matsim.core.utils.geometry.transformations.TransformationFactory;
import org.matsim.core.utils.gis.ShapeFileReader;
import org.matsim.core.utils.io.tabularFileParser.TabularFileHandler;
import org.matsim.core.utils.io.tabularFileParser.TabularFileParser;
import org.matsim.core.utils.io.tabularFileParser.TabularFileParserConfig;
import org.matsim.mosaik2.Utils;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.operation.TransformException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;

public class PrepareBerlinNetwork {

    private static final String countNodesMapping = "studies/countries/de/open_berlin_scenario/be_3/counts/counts_OSM-nodes.csv";
    private static final String osmFile = "projects\\mosaik-2\\raw-data\\osm\\berlin-brandenburg-200507.osm.pbf";
    private static final String stateShapeFile = "projects\\mosaik-2\\raw-data\\shapes\\bundeslaender\\VG250_LAN.shp";
    private static final String outputFile = "projects\\mosaik-2\\matsim-input-files\\berlin\\berlin-5.5.2-network-with-geometries.xml.gz";

    private static final CoordinateTransformation transformation = TransformationFactory.getCoordinateTransformation(TransformationFactory.WGS84, TransformationFactory.DHDN_GK4);
    private static final Set<String> networkModes = Set.of(TransportMode.car, TransportMode.ride, "freight");

    public static void main(String[] args) {

        var svnArg = Utils.parseSharedSvnArg(args);
        var svnPath = Paths.get(svnArg.getSharedSvn());
        var nodeIdsToKeep = readNodeIds(svnPath.resolve(countNodesMapping));

        var berlin = getBerlinShape(svnPath.resolve(stateShapeFile));

        var network = new SupersonicOsmNetworkReader.Builder()
                .setCoordinateTransformation(transformation)
                .setPreserveNodeWithId(nodeIdsToKeep::contains)
                .setStoreOriginalGeometry(true)
                .setIncludeLinkAtCoordWithHierarchy((coord, hierarchy) -> {
                    if (berlin.covers(MGC.coord2Point(coord))) return true;

                    return (hierarchy <= LinkProperties.LEVEL_TERTIARY);
                })
                .setAfterLinkCreated((link, map, direction) -> {
                    link.setAllowedModes(networkModes);
                })
                .build()
                .read(Paths.get(svnArg.getSharedSvn()).resolve(osmFile));

        new NetworkCleaner().run(network);

        new NetworkWriter(network).write(Paths.get(svnArg.getSharedSvn()).resolve(outputFile).toString());

    }

    private static Set<Long> readNodeIds(Path mappingFile) {

        Set<Long> result = new LongOpenHashSet();
        TabularFileParserConfig config = new TabularFileParserConfig();
        config.setDelimiterTags(new String[] {";"});
        config.setFileName(mappingFile.toString());
        new TabularFileParser().parse(config, new TabularFileHandler() {
            boolean header = true;
            @Override
            public void startRow(String[] row) {
                if(!header){
                    if( !(row[1].equals("") || row[2].equals("") ) ){
                        result.add( Long.parseLong(row[1]));
                        result.add( Long.parseLong(row[2]));
                    }
                }
                header = false;
            }
        });

        return result;
    }

    private static PreparedGeometry getBerlinShape(Path stateShapeFile) {

        var berlinGeometry = ShapeFileReader.getAllFeatures(stateShapeFile.toString()).stream()
                .filter(feature -> feature.getAttribute("GEN").equals("Berlin"))
                .map(feature -> (Geometry)feature.getDefaultGeometry())
                .findAny()
                .orElseThrow();

        try {
            var sourceCRS = CRS.decode("EPSG:25832");
            var targetCRS = CRS.decode("EPSG:31468");

            var mathTransform = CRS.findMathTransform(sourceCRS, targetCRS);
            var transformed = JTS.transform(berlinGeometry, mathTransform);
            var geometryFactory = new PreparedGeometryFactory();
            return geometryFactory.create(transformed);
        } catch (FactoryException | TransformException e) {
            throw new RuntimeException(e);
        }
    }
}
