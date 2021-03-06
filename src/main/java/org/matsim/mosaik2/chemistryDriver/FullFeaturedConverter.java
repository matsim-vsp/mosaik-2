package org.matsim.mosaik2.chemistryDriver;

import lombok.Builder;
import lombok.extern.log4j.Log4j2;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.NetworkWriter;
import org.matsim.contrib.analysis.time.TimeBinMap;
import org.matsim.contrib.emissions.Pollutant;
import org.matsim.contrib.emissions.events.EmissionEventsReader;
import org.matsim.core.events.EventsUtils;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.utils.geometry.CoordinateTransformation;

import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

@Log4j2
public class FullFeaturedConverter {

    private final String networkFile;

    private final String emissionEventsFile;

    private final String outputFile;

    private final double cellSize;

    private final double timeBinSize;

    private final double scaleFactor;

    private final Raster.Bounds bounds;

    private final CoordinateTransformation transformation;

    private final PollutantToPalmNameConverter pollutantConverter;

    private final String date;

    private final int numberOfDays;

    @Builder
    public FullFeaturedConverter(String networkFile, String emissionEventsFile, String outputFile, double cellSize, double timeBinSize, double scaleFactor, Raster.Bounds bounds, CoordinateTransformation transformation, PollutantToPalmNameConverter pollutantConverter, String date, int numberOfDays) {
        this.networkFile = networkFile;
        this.emissionEventsFile = emissionEventsFile;
        this.outputFile = outputFile;
        this.cellSize = cellSize;
        this.timeBinSize = timeBinSize;
        this.scaleFactor = scaleFactor;
        this.bounds = bounds;
        this.transformation = transformation;
        this.pollutantConverter = pollutantConverter;
        this.date = date == null ? "2017-07-31" : date;
        this.numberOfDays = numberOfDays == 0 ? 1 : numberOfDays;
    }

    public void write() {

        // read network, transform to destination crs and filter only links that are within bounds
        var network = NetworkUtils.readNetwork(networkFile, transformation).getLinks().values().stream()
                .filter(link -> isCoveredBy(link, bounds))
                .collect(NetworkUtils.getCollector());

        log.info("Unsimplifying network");
        var link2Segments = NetworkUnsimplifier.unsimplifyNetwork(network, transformation);

        log.info("Converting segment map to network");
        var segmentNetwork = NetworkUnsimplifier.segmentsToNetwork(link2Segments);

        log.info("writing network!");
        new NetworkWriter(segmentNetwork).write("C:\\Users\\Janekdererste\\Desktop\\segment-network.xml.gz");

        // read the emission events
        var manager = EventsUtils.createEventsManager();
        var handler = new AggregateEmissionsByTimeAndOrigGeometryHandler(link2Segments, pollutantConverter.getPollutants(), timeBinSize, scaleFactor);
        manager.addHandler(handler);
        new EmissionEventsReader(manager).readFile(emissionEventsFile);

        var emissions = handler.getTimeBinMap();

        // convert pollutants to palm names
        var palmEmissions = pollutantConverter.convert(emissions);

        // put emissions onto a raster


        var rasteredEmissions = EmissionRasterer.raster(palmEmissions, segmentNetwork, bounds, cellSize);

        //var rasteredEmissions = EmissionRasterer.raster(palmEmissions, network, bounds, cellSize);
        addNoIfPossible(rasteredEmissions);

        PalmChemistryInput2.writeNetCdfFile(outputFile, rasteredEmissions, date, numberOfDays);
    }

    private static boolean isCoveredBy(Link link, Raster.Bounds bounds) {
        return bounds.covers(link.getFromNode().getCoord()) && bounds.covers(link.getToNode().getCoord());
    }

    private void addNoIfPossible(TimeBinMap<Map<String, Raster>> timeBinMap) {

        if (pollutantConverter.getPollutants().contains(Pollutant.NO2) && pollutantConverter.getPollutants().contains(Pollutant.NOx)) {

            for (var timeBin : timeBinMap.getTimeBins()) {

                var no2 = timeBin.getValue().get(pollutantConverter.getPalmName(Pollutant.NO2));
                var nox = timeBin.getValue().get(pollutantConverter.getPalmName(Pollutant.NOx));
                var no = new Raster(no2.getBounds(), no2.getCellSize());

                nox.forEachCoordinate((x, y, noxValue) -> {
                    var no2Value = no2.getValueByCoord(x, y);
                    var noValue = noxValue - no2Value;
                    no.adjustValueForCoord(x, y, noValue);
                });

                timeBin.getValue().put("NO", no);
                timeBin.getValue().remove("NOx");
            }
        }

    }
}
