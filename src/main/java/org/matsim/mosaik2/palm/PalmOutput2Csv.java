package org.matsim.mosaik2.palm;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.matsim.contrib.analysis.time.TimeBinMap;
import org.matsim.mosaik2.DoubleToDoubleFunction;
import org.matsim.mosaik2.analysis.run.CSVUtils;
import org.matsim.mosaik2.raster.DoubleRaster;


import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

@Log4j2
public class PalmOutput2Csv {

	private static final String AV_MASKED_PALM_TEMPLATE = "%s_av_masked_M01.%s.nc";

	private static final String AV_MASKED_DAY2_CSV_TEMPLATE = "%s_av_masked_M01.day2-si-units.xyt.csv";

	private static final String AV_MASKED_DAY2_CSV_SPECIES_TEMPLATE = "%s_av_masked_M01.%s_day2-si-units.xyt.csv";

	public static void main(String[] args) {

		var inputArgs = new InputArgs();
		JCommander.newBuilder().addObject(inputArgs).build().parse(args);

		var files = Stream.iterate(0, i -> i + 1)
				.map(i -> getPalmMaskedFilePath(inputArgs.root, inputArgs.palmRunId, i))
				.limit(inputArgs.numFileParts)
				.toList();

		// this might be worth writing to either csv or netcdf
		var allEmissions = PalmMergedOutputReader.readFiles(files, inputArgs.species);

		// we only want the second day
		var secondDayEmissions = getSecondDayInLocalTime(allEmissions, inputArgs.startTime, inputArgs.utcOffset);
		convertToSiUnits(secondDayEmissions);
		inputArgs.species = calculateNOx(secondDayEmissions, inputArgs.species);
		writePalmOutputToCsv(getDay2CSVPath(inputArgs.root, inputArgs.palmRunId), secondDayEmissions, inputArgs.species);

		if (inputArgs.filePerSpecies) {
			for (var s : inputArgs.species) {
				writePalmOutputToCsv(getDay2SpeciesCSVPath(inputArgs.root, inputArgs.palmRunId, s), secondDayEmissions, List.of(s));
			}
		}
	}

	private static void writePalmOutputToCsv(Path output, TimeBinMap<Map<String, DoubleRaster>> data, Collection<String> species) {
		log.info("Writing t,x,y,value data to: " + output);

		// assuming we have at least one time bin with one raster.
		var rasterToIterate = data.getTimeBins().iterator().next().getValue().values().iterator().next();
		var header = new java.util.ArrayList<>(List.of("time", "x", "y"));

		// hack this in here, so that things are compatible with simwrapper
		if (species.size() == 1) {
			header.add("value");
		} else {
			header.addAll(species);
		}

		CSVUtils.writeTable(data.getTimeBins(), output, header, (p, bin) -> {
			var time = bin.getStartTime();

			log.info("Writing time slices: [" + time + ", " + (time + data.getBinSize()) + "]");

			rasterToIterate.forEachCoordinate((x, y, value) -> {
				if (value < 0) return; // this means this raster point is a building

				CSVUtils.print(p, time);
				CSVUtils.print(p, x);
				CSVUtils.print(p, y);
				for (var speciesName : species) {
					var speciesRaster = bin.getValue().get(speciesName);
					var speciesValue = speciesRaster.getValueByCoord(x, y);
					CSVUtils.print(p, speciesValue);
				}
				CSVUtils.println(p); // new line
			});
		});
		log.info("Finished writing to: " + output);
	}

	private static Path getPalmMaskedFilePath(String root, String runId, int number) {
		return Paths.get(root).resolve(getPalmMaskedFileName(runId, number));
	}

	private static String getPalmMaskedFileName(String runId, int number) {
		var partNumber = StringUtils.leftPad(Integer.toString(number), 3, '0');
		return String.format(AV_MASKED_PALM_TEMPLATE, runId, partNumber);
	}

	private static Path getDay2CSVPath(String root, String runId) {
		var name = String.format(AV_MASKED_DAY2_CSV_TEMPLATE, runId);
		return Paths.get(root).resolve(name);
	}

	private static Path getDay2SpeciesCSVPath(String root, String runId, String species) {
		var name = String.format(AV_MASKED_DAY2_CSV_SPECIES_TEMPLATE, runId, species);
		return Paths.get(root).resolve(name);
	}

	private static TimeBinMap<Map<String, DoubleRaster>> getSecondDayInLocalTime(TimeBinMap<Map<String, DoubleRaster>> source, double startTime, double utcOffset) {

		log.info("Converting time bins into local time. Utc Offset is: " + utcOffset + " Taking only time slices after " + startTime);
		TimeBinMap<Map<String, DoubleRaster>> result = new TimeBinMap<>(source.getBinSize());

		for (var bin : source.getTimeBins()) {
			var localTime = utcToLocalTimeWithWrapAround(bin.getStartTime(), utcOffset);
			var localTimeFromStart = localTime - startTime;

			if (localTime >= startTime) {
				result.getTimeBin(localTimeFromStart).setValue(bin.getValue());
			}
		}
		return result;
	}

	private static double utcToLocalTimeWithWrapAround(double startTime, double utcOffset) {

		// the berlin run is UTC+2, Example:
		// 	PALM second 0 is 7200 (2am) in MATSim
		//  PALM av second 3600 means 0am - 1am UTC corresponding to 2am - 3am (7200 - 10800s)
		// the hours after 12pm were prepended to the beginning of the palm run. Reverse this
		// here now.
		// use (48h + 1h) * 3600 as threshold to wrap around. (Palm seconds are slightly off, so use the start value
		// of the next hour to be save.
		// See FullFeaturedConverter::getInputSeconds
		var localTime = startTime + utcOffset;
		return localTime >= 176400 ? localTime - 86400 : localTime;
	}

	private static void convertToSiUnits(TimeBinMap<Map<String, DoubleRaster>> data) {

		log.info("Converting to SI-Units");
		for (var bin : data.getTimeBins()) {
			for (var speciesEntry : bin.getValue().entrySet()) {

				var converter = getConverterFunction(speciesEntry.getKey());
				var raster = speciesEntry.getValue();
				raster.transformEachValue(converter);
			}
		}
	}

	private static List<String> calculateNOx(TimeBinMap<Map<String, DoubleRaster>> data, List<String> species) {

		var first = data.getTimeBins().iterator().next().getValue();
		if (first.containsKey("NO") && first.containsKey("NO2")) {
			log.info("Calculating NOx");
			for (var bin : data.getTimeBins()) {
				var emissions = bin.getValue();
				var no = emissions.get("NO");
				var no2 = emissions.get("NO2");
				var nox = new DoubleRaster(no.getBounds(), no.getCellSize());
				nox.setValueForEachIndex((xi, yi) -> {
					var noVal = no.getValueByIndex(xi, yi);
					var no2Val = no2.getValueByIndex(xi, yi);
					return noVal + no2Val;
				});
				emissions.put("NOx", nox);
			}
			var speciesCopy = new ArrayList<>(species);
			speciesCopy.add("NOx");
			return speciesCopy;
		}
		return species;
	}

	private static DoubleToDoubleFunction getConverterFunction(String species) {
		return switch (species) {
			case "PM10" ->
				// converts palm's kg/m3 into g/m3
					value -> value * 1000;
			case "NO2" ->
				// converts palm's ppm into g/m3. We use Normal Temperature and Pressure (NTP) where 1 mole of gas is
				// equal to 24.45L of gas. Then we divide by 1000 to convert mg into g
				// value * molecularWeight / 22.45 / 1000
					value -> value * 46.01 / 22.45 / 1000;
			case "NO" -> value -> value * 30.0061 / 22.5 / 1000;
			case "O3" -> value -> value * 47.9982 / 22.5 / 1000;
			default -> throw new RuntimeException("No conversion implemented for species: " + species);
		};
	}

	static class InputArgs {
		@Parameter(names = "-palmRunId", required = true)
		private String palmRunId;

		@Parameter(names = "-root", required = true)
		private String root;

		@Parameter(names = "-numFileParts")
		private int numFileParts = 5;

		@Parameter(names = "-offset")
		private double utcOffset = 7200;

		@Parameter(names = "-start-time")
		private double startTime = 86400;

		@Parameter(names = "-species")
		private List<String> species = List.of("NO2", "NO", "O3", "PM10");

		@Parameter(names = "-file-per-species")
		private boolean filePerSpecies = false;
	}
}