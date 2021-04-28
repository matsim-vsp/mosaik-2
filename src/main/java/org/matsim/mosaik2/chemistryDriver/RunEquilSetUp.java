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
import org.matsim.core.events.EventsUtils;
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

    RunEquilSetUp() {

    }

    RunEquilSetUp(String networkFile, String emissionEventsFile, String outputFile, double cellSize) {
        this.cellSize = cellSize;
        this.emissionEventsFile = emissionEventsFile;
        this.networkFile = networkFile;
        this.outputFile = outputFile;
    }

    private void write() {

        // get the network
        var network = NetworkUtils.readNetwork(networkFile);

        // read the emission events
        var manager = EventsUtils.createEventsManager();
        var handler = new AggregateEmissionsByTimeHandler(network, pollutants.keySet(), timeBinSize, 1.0);
        EventsUtils.readEvents(manager, emissionEventsFile);
        var emissions = handler.getTimeBinMap();

        // convert pollutants to palm names
        var converter = new PollutantToPalmNameConverter(pollutants);
        var palmEmissions = converter.convert(emissions);

        // put emissions onto a raster
        var bounds = getBounds(network);
        var rasteredEmissions = EmissionRasterer.raster(palmEmissions, network, bounds, cellSize);

        PalmChemistryInput2.writeNetCdfFile(outputFile, rasteredEmissions);
    }

    private static Raster.Bounds getBounds(Network network) {

        var coords = network.getNodes().values().stream().map(BasicLocation::getCoord).collect(Collectors.toSet());
        return new Raster.Bounds(coords);
    }
}
