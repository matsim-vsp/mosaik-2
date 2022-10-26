package org.matsim.mosaik2.analysis;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import org.apache.commons.lang3.StringUtils;
import org.matsim.contrib.analysis.time.TimeBinMap;
import org.matsim.mosaik2.palm.PalmCsvOutput;
import org.matsim.mosaik2.raster.DoubleRaster;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class ExposurePipeline {

	private static final String AV_MASKED_PALM_TEMPLATE = "%s_av_masked_M01.%s.nc";
	private static final String AV_MASKED_CSV_TEMPLATE = "%s_av_masked_M01.%s-%s.xyt.csv";
	private static final String AV_MASKED_MERGED_CSV_TEMPLATE = "%s_av_masked_M01.all-%s.xyt.csv";
	private static final String AV_MASKED_DAY2_CSV_TEMPLATE = "%s_av_masked_M01.day2-si-units-%s.xyt.csv";
	private static final String AV_MASKED_DAY2_EXPOSURE_CSV_TEMPLATE = "%s_av_masked_M01.day2-%s-exposure.xyt.csv";
	private static final String AV_MASKED_DAY2_R_VALUES_CSV_TEMPLATE = "%s_av_masked_M01.day2-%s-r-values.xyt.csv";

	public static void main(String[] args) {

		var input = new InputArgs();
		JCommander.newBuilder().addObject(input).build().parse(args);

		for (var species : input.species) {

			// convert netcdf to csv
			for (var i = 0; i < input.numFileParts; i++) {

				var palmFile = getPalmMaskedFilePath(input.root, input.palmRunId, i);
				var outputFile = getCSVMaskedFilePath(input.root, input.palmRunId, i, species);
				new ConvertPalmTimeSeriesToCSV(palmFile, outputFile, species).run();
			}

			//merge file parts to single file
			var merged = new TimeBinMap<DoubleRaster>(3600);
			for (var i = 0; i < input.numFileParts; i++) {

				var csvFile = getCSVMaskedFilePath(input.root, input.palmRunId, i, species);
				var palmData = PalmCsvOutput.read(csvFile);
				for (var bin : palmData.getTimeBins()) {
					var mergedBin = merged.getTimeBin(getStartTime(bin.getStartTime(), input.utcOffset));
					mergedBin.setValue(bin.getValue());
				}
			}
			var mergedFile = getMergedCSVPath(input.root, input.palmRunId, species);
			PalmCsvOutput.write(mergedFile, merged);

			// cut out second day
			var day2File = getDay2CSVPath(input.root, input.palmRunId, species);
			var conversion = getConverterFunction(species);
			new ConvertPalmCsvOutputToSparse(mergedFile, day2File, conversion).run();

			// calculate exposure
			new CalculateExposure(day2File, Paths.get(input.eventsFile), getExposureCSVPath(input.root, input.palmRunId, species)).run();

			// calculate r-values
			var rValueFile = getRValuesCSVPath(input.root, input.palmRunId, species);
			var rValueInput = new CalculateRValues.InputArgs(
					input.emissionEvents, input.networkFile, day2File.toString(),
					rValueFile.toString(), species, input.scaleFactor
			);
			new CalculateRValues(rValueInput).run();
		}
	}

	private static String getPalmMaskedFileName(String runId, int number) {
		var partNumber = StringUtils.leftPad(Integer.toString(number), 3, '0');
		return String.format(AV_MASKED_PALM_TEMPLATE, runId, partNumber);
	}

	private static Path getPalmMaskedFilePath(String root, String runId, int number) {
		return Paths.get(root).resolve(getPalmMaskedFileName(runId, number));
	}

	private static Path getCSVMaskedFilePath(String root, String runid, int number, String species) {

		var partNumber = StringUtils.leftPad(Integer.toString(number), 3, '0');
		var name = String.format(AV_MASKED_CSV_TEMPLATE, runid, partNumber, species);
		return Paths.get(root).resolve(name);
	}

	private static Path getMergedCSVPath(String root, String runId, String species) {
		var name = String.format(AV_MASKED_MERGED_CSV_TEMPLATE, runId, species);
		return Paths.get(root).resolve(name);
	}

	private static Path getDay2CSVPath(String root, String runId, String species) {
		var name = String.format(AV_MASKED_DAY2_CSV_TEMPLATE, runId, species);
		return Paths.get(root).resolve(name);
	}

	private static Path getExposureCSVPath(String root, String runId, String species) {
		var name = String.format(AV_MASKED_DAY2_EXPOSURE_CSV_TEMPLATE, runId, species);
		return Paths.get(root).resolve(name);
	}

	private static Path getRValuesCSVPath(String root, String runId, String species) {
		var name = String.format(AV_MASKED_DAY2_R_VALUES_CSV_TEMPLATE, runId, species);
		return Paths.get(root).resolve(name);
	}

	private static double getStartTime(double startTime, double offset) {

		// the berlin run is UTC+2, Example:
		// 	PALM second 0 is 7200 (2am) in MATSim
		//  PALM av second 3600 means 0am - 1am UTC corresponding to 2am - 3am (7200 - 10800s)
		// the hours after 12pm were prepended to the beginning of the palm run. Reverse this
		// here now.
		// use (48h + 1h) * 3600 as threshold to wrap around. (Palm seconds are slightly off, so use the start value
		// of the next hour to be save.
		// See FullFeaturedConverter::getInputSeconds
		var time = startTime + offset;
		return time >= 176400 ? time - 86400 : time;
	}

	private static ConvertPalmCsvOutputToSparse.DoubleToDoubleFunction getConverterFunction(String species) {
		return switch (species) {
			case "PM10" ->
				// converts palm's kg/m3 into g/m3
					value -> value * 1000;
			case "NO2" ->
				// converts palm's ppm into g/m3. We use Normal Temperature and Pressure (NTP) where 1 mole of gas is
				// equal to 24.45L of gas. Then we divide by 1000 to convert mg into g
				// value * molecularWeight / 22.45 / 1000
					value -> value * 46.01 / 22.45 / 1000;
			default -> throw new RuntimeException("No conversion implemented for species: " + species);
		};
	}

	@SuppressWarnings({"FieldMayBeFinal", "unused"})
	static class InputArgs {

		@Parameter(names = "-palmRunId", required = true)
		private String palmRunId;

		@Parameter(names = "-root", required = true)
		private String root;

		@Parameter(names = "-network", required = true)
		private String networkFile;

		@Parameter(names = "-emissionEvents", required = true)
		private String emissionEvents;

		@Parameter(names = "-events", required = true)
		private String eventsFile;

		@Parameter(names = "-numFileParts")
		private int numFileParts = 5;

		@Parameter(names = "-offset")
		private double utcOffset = 7200;

		@Parameter(names = "-s")
		private int scaleFactor = 10;

		@Parameter(names = "-species")
		private List<String> species = List.of("NO2", "PM10");
	}
}