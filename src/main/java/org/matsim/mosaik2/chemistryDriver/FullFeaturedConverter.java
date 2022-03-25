package org.matsim.mosaik2.chemistryDriver;

import lombok.Builder;
import lombok.extern.log4j.Log4j2;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.NetworkWriter;
import org.matsim.contrib.analysis.time.TimeBinMap;
import org.matsim.contrib.emissions.Pollutant;
import org.matsim.contrib.emissions.events.EmissionEventsReader;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.events.EventsUtils;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.utils.geometry.CoordinateTransformation;
import org.matsim.core.utils.geometry.transformations.IdentityTransformation;

import java.time.LocalDateTime;
import java.util.Map;

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

    private final LocalDateTime date;

    private final int numberOfDays;

    private final int offset;

    @Builder
    public FullFeaturedConverter(String networkFile, String emissionEventsFile, String outputFile, double cellSize, double timeBinSize, double scaleFactor, Raster.Bounds bounds, CoordinateTransformation transformation, PollutantToPalmNameConverter pollutantConverter, LocalDateTime date, int numberOfDays, int offset) {
        this.networkFile = networkFile;
        this.emissionEventsFile = emissionEventsFile;
        this.outputFile = outputFile;
        this.cellSize = cellSize;
        this.timeBinSize = timeBinSize;
        this.scaleFactor = scaleFactor;
        this.bounds = bounds;
        this.transformation = transformation == null ? new IdentityTransformation() : transformation;
        this.pollutantConverter = pollutantConverter == null ? new PollutantToPalmNameConverter() : pollutantConverter;
        this.date = date == null ? LocalDateTime.of(2017, 7, 31, 0, 0) : date;
        this.numberOfDays = numberOfDays == 0 ? 1 : numberOfDays;
        this.offset = offset;
    }

    public void write() {

        var rawNetwork = NetworkUtils.readNetwork(networkFile, ConfigUtils.createConfig().network(), transformation);

        // read network, transform to destination crs and filter only links that are within bounds
        var network = rawNetwork.getLinks().values().stream()
                .filter(link -> isCoveredBy(link, bounds))
                .collect(NetworkUtils.getCollector(ConfigUtils.createConfig()));

        log.info("Unsimplifying network");
        var link2Segments = NetworkUnsimplifier.unsimplifyNetwork(network, transformation);

        log.info("Converting segment map to network");
        var segmentNetwork = NetworkUnsimplifier.segmentsToNetwork(link2Segments);

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

        rasteredEmissions = cuttofulldays(rasteredEmissions, numberOfDays, offset);

        PalmChemistryInput2.writeNetCdfFile(outputFile, rasteredEmissions, date);
    }

    private static TimeBinMap<Map<String, Raster>> cuttofulldays(TimeBinMap<Map<String, Raster>> rasteredEmissions, int numberOfDays, int offset) {

        TimeBinMap<Map<String, Raster>> result = new TimeBinMap<>(3600);
        for (int day = 0; day < numberOfDays; day++) {
            for (int hour = 0; hour < 24; hour++) {

                // the seconds for the resulting time bin map have to go on through the whole time period
                var resultSeconds = hour * 3600 + 86400 * day;
                // the input time marks the time bin from which we take emissions. This should always be within the first 24hours
                // since we are copying the first 24h to the consecutive days. Also, since the stuttgart palm run runs with
                // global utc time, we have to incorporate an offset of 2hours. We start with the matsim output
                // from 2am and append the first two hours of input to the end of the day. This is really messy, but it was
                // necessary because this became apparent only in the last minute when the evaluation run had to be started.
                var inputSeconds = getInputSeconds(hour, offset);
                var bin = rasteredEmissions.getTimeBin(inputSeconds);
                Map<String, Raster> value = bin.hasValue() ? bin.getValue() : Map.of();
                result.getTimeBin(resultSeconds).setValue(value);
            }
        }

        return result;
    }

    /**
     * This really kinda hard codes the 24h thing into this converter. On the other hand, maybe that's just what we want
     */
    private static int getInputSeconds(int hour, int offset) {
        if (hour < 24 - offset) {
            return (hour + offset) * 3600;
        } else {
            return (hour - 24 + offset) * 3600;
        }
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
