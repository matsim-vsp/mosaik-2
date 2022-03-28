package org.matsim.mosaik2.chemistryDriver;

import org.junit.Rule;
import org.junit.Test;
import org.matsim.contrib.emissions.Pollutant;
import org.matsim.core.utils.geometry.transformations.IdentityTransformation;
import org.matsim.mosaik2.raster.DoubleRaster;
import org.matsim.testcases.MatsimTestUtils;

import java.util.Map;

import static org.junit.Assert.*;

public class FullFeaturedConverterTest {

    @Rule
    public MatsimTestUtils testUtils = new MatsimTestUtils();

    @Test
    public void singleLink() {

        var testFilePath = testUtils.getOutputDirectory() + "output.nc";
        FullFeaturedConverter.builder()
                .networkFile(testUtils.getClassInputDirectory() + "network.xml")
                .emissionEventsFile(testUtils.getClassInputDirectory() + "emissionEvents.xml")
                .cellSize(10)
                .pollutantConverter(new PollutantToPalmNameConverter(Map.of(Pollutant.NO2, "NO2")))
                .outputFile(testFilePath)
                .scaleFactor(1.0)
                .timeBinSize(3600)
                .transformation(new IdentityTransformation())
                .bounds(new DoubleRaster.Bounds(-5, -5, 24, 24))
                .build()
                .write();

        var readResult = PalmChemistryInputReader.read(testFilePath);

        assertEquals(3, readResult.getTimeBins().size());
        assertEquals(3600, readResult.getBinSize(), 0.0001);
        assertEquals(0, readResult.getStartTime(), 0.0001);

        for (var timeBin : readResult.getTimeBins()) {
            timeBin.getValue().get("NO2").forEachCoordinate((x, y, value) -> {
                if (y == 15) {
                    if (x == 5) {
                        assertEquals(1.0, value, 0.00001);
                    } else {
                        assertEquals(2.0, value, 0.00001);
                    }
                } else if(x != 5 ) {
                    assertEquals(1.0, value, 0.00001);
                } else {
                    assertEquals(0.0, value, 0.0000);
                }
            });
        }
    }
}