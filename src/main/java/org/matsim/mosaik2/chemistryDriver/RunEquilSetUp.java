package org.matsim.mosaik2.chemistryDriver;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import gnu.trove.map.TObjectDoubleMap;
import gnu.trove.map.hash.TObjectDoubleHashMap;
import org.locationtech.jts.geom.Geometry;
import org.matsim.api.core.v01.BasicLocation;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.contrib.analysis.time.TimeBinMap;
import org.matsim.contrib.emissions.Pollutant;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.utils.collections.Tuple;
import org.matsim.mosaik2.events.RawEmissionEventsReader;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

public class RunEquilSetUp {

    private static final double timeBinSize = 3600;
    private static final Map<Pollutant, String> pollutants = Map.of(
            Pollutant.NO2, "NO2",
            Pollutant.CO2_TOTAL, "CO2",
            Pollutant.PM, "PM10",
            Pollutant.CO, "CO",
            Pollutant.NOx, "NOx"
    );

    @Parameter(names = "-n", required = true)
    private String networkFile = "";

    @Parameter(names = "-e", required = true)
    private String emissionEventsFile = "";

    @Parameter(names = "-o", required = true)
    private String outputFile = "";

    @Parameter(names = "-cs") // by default this is 10
    private double cellSize = 10;

    public static void main(String[] args) {

        var writer = new RunEquilSetUp();
        JCommander.newBuilder().addObject(writer).build().parse(args);
        writer.write();
    }

    private void write() {

        var network = NetworkUtils.readNetwork(networkFile);

        TimeBinMap<Map<Pollutant, TObjectDoubleMap<Id<Link>>>> timeBinMap = new TimeBinMap<>(timeBinSize);

        new RawEmissionEventsReader((time, linkId, vehicleId, pollutant, value) -> {
            if (!pollutants.containsKey(pollutant)) return;

            var id = Id.createLinkId(linkId);

            if (network.getLinks().containsKey(id)) {

                var timeBin = timeBinMap.getTimeBin(time);
                if (!timeBin.hasValue()) {
                    timeBin.setValue(new HashMap<>());
                }
                var emissionsByPollutant = timeBin.getValue();
                var linkEmissions = emissionsByPollutant.computeIfAbsent(pollutant, p -> new TObjectDoubleHashMap<>());
                var linkEmissionValue = value * 1000000;
                linkEmissions.adjustOrPutValue(id, linkEmissionValue, linkEmissionValue);
            }
        }).readFile(emissionEventsFile);

        TimeBinMap<Map<String, Raster>> rasterTimeBinMap = new TimeBinMap<>(timeBinSize);
        var bounds = getBounds(network);

        for (var bin: timeBinMap.getTimeBins()) {

            var rasterByPollutant = bin.getValue().entrySet().parallelStream()
                    .map(entry -> {
                        var emissions = entry.getValue();
                        var raster = Bresenham.rasterizeNetwork(network, bounds, emissions, cellSize);
                        var palmPollutantKey = pollutants.get(entry.getKey());
                        return Tuple.of(palmPollutantKey, raster);
                    })
                    .collect(Collectors.toMap(Tuple::getFirst, Tuple::getSecond));
            rasterTimeBinMap.getTimeBin(bin.getStartTime()).setValue(rasterByPollutant);
        }

        PalmChemistryInput2.writeNetCdfFile(outputFile, rasterTimeBinMap);
    }

    private static Raster.Bounds getBounds(Network network) {

        var coords = network.getNodes().values().stream().map(BasicLocation::getCoord).collect(Collectors.toSet());
        return new Raster.Bounds(coords);
    }


}
