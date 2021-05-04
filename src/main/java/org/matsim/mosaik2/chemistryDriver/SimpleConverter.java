package org.matsim.mosaik2.chemistryDriver;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import org.matsim.api.core.v01.BasicLocation;
import org.matsim.api.core.v01.network.Network;
import org.matsim.contrib.emissions.Pollutant;
import org.matsim.contrib.emissions.events.EmissionEventsReader;
import org.matsim.core.events.EventsUtils;
import org.matsim.core.network.NetworkUtils;

import java.util.Map;
import java.util.stream.Collectors;

public class SimpleConverter {

    @AllArgsConstructor
    @NoArgsConstructor
    static class Props {
        @Parameter(names = "-n", required = true)
        private String networkFile = "";

        @Parameter(names = "-e", required = true)
        private String emissionEventsFile = "";

        @Parameter(names = "-o", required = true)
        private String outputFile = "";

        @Parameter(names = "-cs") // by default this is 10
        private double cellSize = 10;

        private Raster.Bounds bounds;
    }

    private static final double timeBinSize = 3600;
    private static final Map<Pollutant, String> pollutants = Map.of(
            Pollutant.NO2, "NO2",
            Pollutant.CO2_TOTAL, "CO2",
            Pollutant.PM, "PM10",
            Pollutant.CO, "CO",
            Pollutant.NOx, "NOx"
    );

    public static void main(String[] args) {

        var props = new Props();
        JCommander.newBuilder().addObject(props).build().parse(args);
        write(props);
    }

    public static void write(Props props) {

        // get the network
        var network = NetworkUtils.readNetwork(props.networkFile);

        // read the emission events
        var manager = EventsUtils.createEventsManager();
        var handler = new AggregateEmissionsByTimeHandler(network, pollutants.keySet(), timeBinSize, 1.0);
        manager.addHandler(handler);
        new EmissionEventsReader(manager).readFile(props.emissionEventsFile);

        var emissions = handler.getTimeBinMap();

        // convert pollutants to palm names
        var converter = new PollutantToPalmNameConverter(pollutants);
        var palmEmissions = converter.convert(emissions);

        // put emissions onto a raster
        var bounds = props.bounds == null ? getBounds(network) : props.bounds;
        var rasteredEmissions = EmissionRasterer.raster(palmEmissions, network, bounds, props.cellSize);

        PalmChemistryInput2.writeNetCdfFile(props.outputFile, rasteredEmissions);
    }

    private static Raster.Bounds getBounds(Network network) {

        var coords = network.getNodes().values().stream().map(BasicLocation::getCoord).collect(Collectors.toSet());
        return new Raster.Bounds(coords);
    }
}
