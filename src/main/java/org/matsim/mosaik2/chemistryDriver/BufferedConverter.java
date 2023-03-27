package org.matsim.mosaik2.chemistryDriver;

import lombok.Builder;
import lombok.extern.log4j.Log4j2;
import org.matsim.contrib.analysis.time.TimeBinMap;
import org.matsim.contrib.emissions.Pollutant;
import org.matsim.contrib.emissions.events.EmissionEventsReader;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.events.EventsUtils;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.utils.geometry.CoordinateTransformation;
import org.matsim.core.utils.geometry.transformations.IdentityTransformation;
import org.matsim.mosaik2.raster.DoubleRaster;

import java.time.LocalDateTime;
import java.util.Map;

@Log4j2
public class BufferedConverter {

    private final String networkFile;

    private final String emissionEventsFile;

    private final String outputFile;

    private final DoubleRaster buildings;

    private final double timeBinSize;

    private final double scaleFactor;

    private final CoordinateTransformation transformation;

    private final PollutantToPalmNameConverter pollutantConverter;

    private final LocalDateTime date;

    private final int numberOfDays;

    private final int utcOffset;

    @Builder
    public BufferedConverter(
            String networkFile,
            String emissionEventsFile,
            String outputFile,
            DoubleRaster buildings,
            double timeBinSize,
            double scaleFactor,
            CoordinateTransformation coordinateTransformation,
            PollutantToPalmNameConverter pollutantConverter,
            LocalDateTime date,
            int numberOfDays,
            int utcOffset
    ) {
        this.networkFile = networkFile;
        this.emissionEventsFile = emissionEventsFile;
        this.outputFile = outputFile;
        this.buildings = buildings;
        this.timeBinSize = timeBinSize;
        this.scaleFactor = scaleFactor;
        this.transformation = coordinateTransformation == null ? new IdentityTransformation() : coordinateTransformation;
        this.pollutantConverter = pollutantConverter == null ? new PollutantToPalmNameConverter() : pollutantConverter;
        this.date = date == null ? LocalDateTime.of(2017, 7, 31, 0, 0) : date;
        this.numberOfDays = numberOfDays == 0 ? 1 : numberOfDays;
        this.utcOffset = utcOffset;
    }

    public void write() {

        var network = NetworkUtils.readNetwork(networkFile, ConfigUtils.createConfig().network(), transformation).getLinks().values().stream()
                .filter(link -> FullFeaturedConverter.isCoveredBy(link, buildings.getBounds()))
                .collect(NetworkUtils.getCollector());

        log.info("Unsimplifying network");
        var link2Segments = NetworkUnsimplifier.unsimplifyNetwork(network, transformation);


        // read the emission events
        var manager = EventsUtils.createEventsManager();
        var handler = new AggregateEmissionsByTimeAndOrigGeometryHandler(link2Segments, pollutantConverter.getPollutants(), timeBinSize, scaleFactor);
        manager.addHandler(handler);
        new EmissionEventsReader(manager).readFile(emissionEventsFile);

        var emissions = handler.getTimeBinMap();
        var linksWithEmissions = handler.getLinksWithEmissions();

        // convert pollutants to palm names
        var palmEmissions = pollutantConverter.convert(emissions);

        log.info("Converting segment map to network");
        var segmentNetwork = NetworkUnsimplifier.segmentsToNetwork(link2Segments).getLinks().values().stream()
                .filter(link -> linksWithEmissions.contains(link.getId()))
                .collect(NetworkUtils.getCollector(ConfigUtils.createConfig()));
        var rasteredEmissions = EmissionRasterer.rasterWithBuffer(palmEmissions, segmentNetwork, buildings);

        addNoIfPossible(rasteredEmissions);

        rasteredEmissions = FullFeaturedConverter.cutToFullDays(rasteredEmissions, numberOfDays, utcOffset);

        PalmChemistryInput2.writeNetCdfFile(outputFile, rasteredEmissions, date);
    }

    private void addNoIfPossible(TimeBinMap<Map<String, DoubleRaster>> timeBinMap) {

        if (pollutantConverter.getPollutants().contains(Pollutant.NO2) && pollutantConverter.getPollutants().contains(Pollutant.NOx)) {

            for (var timeBin : timeBinMap.getTimeBins()) {

                var no2 = timeBin.getValue().get(pollutantConverter.getPalmName(Pollutant.NO2));
                var nox = timeBin.getValue().get(pollutantConverter.getPalmName(Pollutant.NOx));
                var no = new DoubleRaster(no2.getBounds(), no2.getCellSize());

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
