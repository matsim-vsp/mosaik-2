package org.matsim.mosaik2.trafficManagement;

import it.unimi.dsi.fastutil.objects.Object2DoubleArrayMap;
import it.unimi.dsi.fastutil.objects.Object2DoubleMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.contrib.analysis.time.TimeBinMap;
import org.matsim.contrib.roadpricing.RoadPricingScheme;
import org.matsim.contrib.roadpricing.RoadPricingSchemeImpl;
import org.matsim.contrib.roadpricing.RoadPricingUtils;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.mosaik2.analysis.NumericSmoothingRadiusEstimate;
import org.matsim.mosaik2.analysis.run.CSVUtils;
import org.matsim.mosaik2.raster.DoubleRaster;
import org.matsim.mosaik2.raster.ObjectRaster;

import java.nio.file.Path;
import java.util.*;

@Log4j2
public class LinkContributions {

    public static TimeBinMap<Map<Id<Link>, LinkValue>> calculate(TimeBinMap<Map<String, DoubleRaster>> data, Network network) {
        // assuming we have at least one time bin with one raster.
        var exampleRaster = data.getTimeBins().iterator().next().getValue().values().iterator().next();

        var linkIndex = org.matsim.mosaik2.SpatialIndex.create(network, 250, exampleRaster.getBounds().toGeometry());
        ObjectRaster<Set<Id<Link>>> linkCache = new ObjectRaster<>(exampleRaster.getBounds(), exampleRaster.getCellSize());
        log.info("Creating raster cache with link ids");
        linkCache.setValueForEachCoordinate(linkIndex::intersects);

        var result = new TimeBinMap<Map<Id<Link>, LinkValue>>(data.getBinSize());
        for (var bin : data.getTimeBins()) {
            result.getTimeBin(bin.getStartTime()).computeIfAbsent(HashMap::new);
        }

        data.getTimeBins().parallelStream().forEach(bin -> {

            log.info("Calculating link contributions for time step: " + bin.getStartTime());
            var resultBin = result.getTimeBin(bin.getStartTime());
            for (var speciesEntry : bin.getValue().entrySet()) {

                exampleRaster.forEachCoordinate((x, y, exampleValue) -> {

                    if (exampleValue <= 0.) return; // nothing to do here

                    var value = speciesEntry.getValue().getValueByCoord(x, y);
                    var linkIds = linkCache.getValueByCoord(x, y);
                    linkIds.stream()
                            .map(id -> network.getLinks().get(id))
                            .forEach(link -> {

                                var weight = NumericSmoothingRadiusEstimate.calculateWeight(
                                        link.getFromNode().getCoord(),
                                        link.getToNode().getCoord(),
                                        new Coord(x, y),
                                        link.getLength(),
                                        50 // TODO make configurable
                                );
                                if (weight > 0.0) {
                                    var impactValue = value * weight;
                                    resultBin.getValue().computeIfAbsent(link.getId(), _id -> new LinkValue()).addValue(speciesEntry.getKey(), impactValue);
                                }
                            });
                });
            }
        });
        return result;
    }

    public static RoadPricingSchemeImpl createRoadPricingScheme(TimeBinMap<Map<Id<Link>, LinkValue>> data, double volume, double scaleFactor) {
        log.info("Creating RoadPricingScheme");

        RoadPricingSchemeImpl scheme = RoadPricingUtils.addOrGetMutableRoadPricingScheme(ScenarioUtils.createScenario(ConfigUtils.createConfig()));
        RoadPricingUtils.setType(scheme, RoadPricingScheme.TOLL_TYPE_LINK);
        RoadPricingUtils.setName(scheme, "Toll_from_PALM");
        RoadPricingUtils.setDescription(scheme, "Tolls are calculated from concentrations retrieved from a PALM simulation run.");

        for (var bin : data.getTimeBins()) {

            var time = bin.getStartTime();
            for (var linkEntry : bin.getValue().entrySet()) {

                var id = linkEntry.getKey();

                var tollOverAllSpecies = linkEntry.getValue().values.object2DoubleEntrySet().stream()
                        .mapToDouble(entry -> calculateTollForSpecies(entry, volume))
                        .map(toll -> toll * scaleFactor)
                        .sum();


                RoadPricingUtils.addLinkSpecificCost(scheme, id, time, time + data.getBinSize(), tollOverAllSpecies);
            }
        }

        return scheme;
    }

    public static void writeToCsv(Path output, TimeBinMap<Map<Id<Link>, LinkValue>> data, Collection<String> species) {

        log.info("Writing link contributions to : " + output);

        var header = new java.util.ArrayList<>(List.of("id", "time"));
        header.addAll(species);

        CSVUtils.writeTable(data.getTimeBins(), output, header, (p, bin) -> {
            for (var entry : bin.getValue().entrySet()) {
                var id = entry.getKey();
                CSVUtils.print(p, id);
                CSVUtils.print(p, bin.getStartTime());
                for (var speciesName : species) {
                    var value = entry.getValue().get(speciesName);
                    CSVUtils.print(p, value);
                }
                CSVUtils.println(p);
            }
        });
    }

    private static double calculateTollForSpecies(Object2DoubleMap.Entry<String> speciesEntry, final double volume) {
        var factor = getCostFactor(speciesEntry.getKey());
        var value = speciesEntry.getDoubleValue();
        // value is [g/m3] so do [g/m3] * [m3] * [€/g] = €
        // originally we thought we would have to divide by link length. However, the contribution value is already parameterized
        // with regard to the link length. So, don't do it.
        return value * volume * factor;
    }

    private static double getCostFactor(String species) {
        // factors from “Handbook on the External Costs of Transport, Version 2019.” https://paperpile.com/app/p/9cd641c8-bb39-0cf0-a9ab-9b4fe386282c
        // Table 14 (p54, ff.) Germany in €/kg. All factors divided by 1000 to get €/g
        return switch (species) {
            case "NO2" -> 36.8 / 1000; // NOx transport city
            case "PM10" -> 39.6 / 1000; // PM10 average
            default -> throw new RuntimeException("No cost factor defined for species: " + species);
        };
    }

    @RequiredArgsConstructor
    private static class LinkValue {

        private final Object2DoubleMap<String> values = new Object2DoubleArrayMap<>();

        void addValue(String species, double value) {
            values.mergeDouble(species, value, Double::sum);
        }

        double get(String species) {
            return values.getDouble(species);
        }
    }
}