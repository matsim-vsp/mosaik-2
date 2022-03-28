package org.matsim.mosaik2.chemistryDriver;

import org.junit.Rule;
import org.junit.Test;
import org.matsim.contrib.analysis.time.TimeBinMap;
import org.matsim.mosaik2.raster.Raster;
import org.matsim.testcases.MatsimTestUtils;

import java.util.Map;

import static org.junit.Assert.*;

public class PalmChemistryInputReaderTest {

    @Rule
    public MatsimTestUtils testUtils = new MatsimTestUtils();

    /**
     * This writes three time steps with one pollutant/species distributed onto a 3x3 raster. Only the center cell has
     * pollution. Then the reader reads the file again and the created data is compared. This is kind of a circular proof,
     * but at least it tests whether both, the reader and the writer facility do what we expect them to do.
     */
    @Test
    public void readSimpleFile() {

        var filename = testUtils.getOutputDirectory() + "test.nc";
        var species = "some-name";

        TimeBinMap<Map<String, Raster>> timeBinMap = new TimeBinMap<>(10, 10);
        var bounds = new Raster.Bounds(-10, -10, 10, 10);

        var raster = new Raster(bounds, 10);
        raster.adjustValueForCoord(0, 0, 100);
        var map = Map.of(species, raster);

        // generate three time steps with the same pollution values
        timeBinMap.getTimeBin(11).setValue(map);
        timeBinMap.getTimeBin(21).setValue(map);
        timeBinMap.getTimeBin(31).setValue(map);

        // write it to file
        PalmChemistryInput2.writeNetCdfFile(filename, timeBinMap);

        var readResult = PalmChemistryInputReader.read(filename);

        assertEquals(timeBinMap.getStartTime(), readResult.getStartTime(), 0.00000001);
        assertEquals(timeBinMap.getBinSize(), readResult.getBinSize(), 0.00000001);
        assertEquals(timeBinMap.getTimeBins().size(), readResult.getTimeBins().size());

        for (var expectedBin: timeBinMap.getTimeBins()) {

            var readTimeBin = readResult.getTimeBin(expectedBin.getStartTime());
            assertEquals(expectedBin.getStartTime(), readTimeBin.getStartTime(), 0.000001);

            var expectedPollution = expectedBin.getValue();
            var actualPollution = readTimeBin.getValue();

            var expectedRaster = expectedPollution.get(species);
            var actualRaster = actualPollution.get(species);

            expectedRaster.forEachIndex((xi, yi, expectedValue) -> {
                var actualValue = actualRaster.getValueByIndex(xi, yi);
                assertEquals(expectedValue, actualValue, 0.00000001);
            });
        }
    }
}