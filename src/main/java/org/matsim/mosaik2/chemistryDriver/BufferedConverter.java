package org.matsim.mosaik2.chemistryDriver;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.extern.log4j.Log4j2;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
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
@AllArgsConstructor
@Builder
public class BufferedConverter {

	private final String networkFile;

	private final String emissionEventsFile;

	private final String outputFile;

	private final DoubleRaster buildings;

	@Builder.Default
	private final double timeBinSize = 3600;
	@Builder.Default
	private final double scaleFactor = 1;
	@Builder.Default
	private final CoordinateTransformation transformation = new IdentityTransformation();
	@Builder.Default
	private final PollutantToPalmNameConverter pollutantConverter = new PollutantToPalmNameConverter();
	@Builder.Default
	private final LocalDateTime date = LocalDateTime.of(2017, 7, 31, 0, 0);
	@Builder.Default
	private final int numberOfDays = 1;
	@Builder.Default
	private final int utcOffset = 0;
	@Builder.Default
	private final double laneWidth = 5;
	@Builder.Default
	private final EmissionRasterer.RasterMethod rasterMethod = EmissionRasterer.RasterMethod.WithLaneWidth;

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

		var rasteredEmissions = raster(palmEmissions, segmentNetwork);

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

	private TimeBinMap<Map<String, DoubleRaster>> raster(TimeBinMap<Map<String, Map<Id<Link>, Double>>> palmEmissions, Network segmentNetwork) {

		if (rasterMethod.equals(EmissionRasterer.RasterMethod.WithLaneWidth)) {
			return EmissionRasterer.rasterWithSwing(palmEmissions, segmentNetwork, buildings, laneWidth);
		} else {
			return EmissionRasterer.raster(palmEmissions, segmentNetwork, buildings.getBounds(), laneWidth);
		}
	}
}