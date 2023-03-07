package org.matsim.mosaik2.chemistryDriver;

import lombok.extern.log4j.Log4j2;
import org.junit.Test;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.contrib.analysis.time.TimeBinMap;
import org.matsim.contrib.emissions.Pollutant;
import org.matsim.mosaik2.raster.DoubleRaster;
import org.matsim.mosaik2.utils.TestUtils;

import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@Log4j2
public class EmissionRastererTest {

    /**
     * This test has a 3x12 cell grid ranging from (-10,-10) to (110,10). It has a single link
     * which reaches from (5,0) to (95,0). This means the link covers 10 grid cells. The expected
     * values for each covered cell is emissionValue / numberOfCells / cellArea. In our case this
     * would be: 1000 / 10 / 100 = 1
     */
    @Test
    public void raster() {

        var network = TestUtils.createSingleLinkNetwork();
        var bounds = new DoubleRaster.Bounds(-10, -10, 110, 10);
        var cellSize = 10;
        var linkEmission = Map.of(Id.createLinkId("link"), 1000.0);
        var linkEmissionByPollutant = Map.of(Pollutant.NO2, linkEmission);
        var timeBinMap = new TimeBinMap<Map<Pollutant, Map<Id<Link>, Double>>>(10);
        timeBinMap.getTimeBin(1).setValue(linkEmissionByPollutant);

        var result = EmissionRasterer.raster(timeBinMap, network, bounds, cellSize);

        assertEquals(1, result.getTimeBins().size());
        var timeBin = result.getTimeBin(1);
        assertTrue(timeBin.hasValue());
        var map = timeBin.getValue();
        assertEquals(1, map.size());
        assertTrue(map.containsKey(Pollutant.NO2));
        var raster = map.get(Pollutant.NO2);
        assertEquals(bounds, raster.getBounds());

        raster.forEachCoordinate((x, y, value) -> {
            if (y == 0.0 && x >= 0 && x < 100) {
                assertEquals(1.0, value, 0.00000001);
            } else {
                assertEquals(0.0, value, 0.00000001);
            }
        });
    }

    @Test
    public void rasterWithBuffer() {

        var network = TestUtils.createSingleLinkNetwork();
        var bounds = new DoubleRaster.Bounds(-10, -10, 110, 10);
        var cellSize = 2;
        var linkEmission = Map.of(Id.createLinkId("link"), 1000.0);
        var linkEmissionByPollutant = Map.of(Pollutant.NO2, linkEmission);
        var timeBinMap = new TimeBinMap<Map<Pollutant, Map<Id<Link>, Double>>>(10);
        timeBinMap.getTimeBin(1).setValue(linkEmissionByPollutant);
        var streetTypes = new DoubleRaster(bounds, cellSize);
        streetTypes.setValueForEachCoordinate((x, y) -> (x == 6) ? 1 : -1);

        var result = EmissionRasterer.rasterWithBuffer(timeBinMap, network, streetTypes);

        result.getTimeBins().iterator().next().getValue().get(Pollutant.NO2).forEachCoordinate((x, y, value) -> {
            // the link should intersect x=6 and all cells between -6 and 2
            if (x == 6 && -6 <= y && y <= 2) {
                assertEquals(200, value, 0.001);
            } else {
                assertEquals(0, value, 0.0001);
            }
        });
    }

    @Test
    public void testOffsetUpwards() {

        var vector = new Coord(0, 10);

        var result = EmissionRasterer.intoOffsetCoordinate(vector);

        assertEquals(EmissionRasterer.LANE_WIDTH / 2, result.getX(), 0.0000001);
        assertEquals(vector.getY(), result.getY(), 0.000001);
    }

    @Test
    public void testOffsetDownwards() {

        var vector = new Coord(0, -10);

        var result = EmissionRasterer.intoOffsetCoordinate(vector);

        assertEquals(-EmissionRasterer.LANE_WIDTH / 2, result.getX(), 0.0000001);
        assertEquals(vector.getY(), result.getY(), 0.000001);
    }

    @Test
    public void testOffsetHorizontal() {
        var vector = new Coord(10, 0);

        var result = EmissionRasterer.intoOffsetCoordinate(vector);

        assertEquals(vector.getX(), result.getX(), 0.0000001);
        assertEquals(-EmissionRasterer.LANE_WIDTH / 2, result.getY(), 0.000001);
    }

    @Test
    public void testOffsetHorizontalReverse() {
        var vector = new Coord(-10, 0);

        var result = EmissionRasterer.intoOffsetCoordinate(vector);

        assertEquals(vector.getX(), result.getX(), 0.0000001);
        assertEquals(EmissionRasterer.LANE_WIDTH / 2, result.getY(), 0.000001);
    }

    @Test
    public void testOffsetAlmostHorizontal() {

        var vector = new Coord(10, 0.00001);

        var result = EmissionRasterer.intoOffsetCoordinate(vector);

        // this should be almost the same as with horizontal. Do this by givin a large delta
        assertEquals(vector.getX(), result.getX(), 0.1);
        assertEquals(-EmissionRasterer.LANE_WIDTH / 2, result.getY(), 0.1);
    }
}