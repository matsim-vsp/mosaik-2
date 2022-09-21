package org.matsim.mosaik2.agentEmissions;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.matsim.api.core.v01.events.Event;
import org.matsim.contrib.emissions.PositionEmissionsModule;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.events.EventsUtils;
import org.matsim.core.events.MatsimEventsReader;
import org.matsim.core.events.handler.BasicEventHandler;
import org.matsim.core.utils.io.IOUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Log4j2
public class PositionEmissionsToCSVHandler implements BasicEventHandler {

	final String separator = ";";
	private final String[] HEADER = {"time", "vehicle_id", "x", "y", "CO",
			"CO2", "NOx", "NO2", "PM10", "NO"};
	private final List<List<String>> row2columns = new ArrayList<>();


	public static void main(String[] args) {
		PositionEmissionsToCSVHandler.Input input = new PositionEmissionsToCSVHandler.Input();
		JCommander.newBuilder().addObject(input).build().parse(args);
		log.info("Input events file: " + input.eventsFile);
		log.info("Output csv file: " + input.outputCsvFile);

		PositionEmissionsToCSVHandler csvHandler = new PositionEmissionsToCSVHandler();
		EventsManager events = EventsUtils.createEventsManager();
		events.addHandler(csvHandler);

		MatsimEventsReader reader = new MatsimEventsReader(events);
		events.initProcessing();
		reader.readFile(input.eventsFile);
		events.finishProcessing();

		csvHandler.print(input.outputCsvFile);


	}

	@Override
	public void handleEvent(Event event) {

		// Catch all position emission events
		if (event.getEventType().equals(PositionEmissionsModule.PositionEmissionEvent.EVENT_TYPE)) {

			// The reader does not know PositionEmission Events
			// Thus, generic events are created

			List<String> rowElements = new ArrayList<>();
			Map<String, String> attributes = event.getAttributes();

			rowElements.add(attributes.get("time"));
			rowElements.add(attributes.get("vehicleId"));
			rowElements.add(attributes.get("x"));
			rowElements.add(attributes.get("y"));
			rowElements.add(attributes.get("CO"));
			rowElements.add(attributes.get("CO2_TOTAL"));
			rowElements.add(attributes.get("NOx"));
			rowElements.add(attributes.get("NO2"));
			rowElements.add(attributes.get("PM2_5"));

			String no = String.valueOf(Double.parseDouble(attributes.get("NOx")) - Double.parseDouble(attributes.get("NO2")));
			rowElements.add(no);
			row2columns.add(rowElements);

		}


	}

	private void print(String outputCsvFile) {

		try {


			CSVPrinter csvPrinter = new CSVPrinter(IOUtils.getBufferedWriter(outputCsvFile),
					CSVFormat.DEFAULT.withDelimiter(separator.charAt(0)).withHeader(HEADER));

			for (var row : row2columns) {
				csvPrinter.printRecord(row);
			}


			csvPrinter.close();

			log.info("Output written to: " + outputCsvFile);

		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private static class Input {

		@Parameter(names = "-events")
		private String eventsFile;

		@Parameter(names = "-outputCsvFile")
		private String outputCsvFile;

	}
}