package org.matsim.mosaik2.agentEmissions;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import lombok.Getter;
import org.matsim.api.core.v01.events.Event;
import org.matsim.contrib.emissions.Pollutant;
import org.matsim.contrib.emissions.PositionEmissionsModule;
import org.matsim.core.events.EventsUtils;
import org.matsim.core.events.MatsimEventsReader;
import org.matsim.core.events.handler.BasicEventHandler;

import java.io.IOException;
import java.util.Map;

public class PositionEmissionsToNetCdfPostprocessing {

	@Parameter(names = "-pee", required = true)
	private String positionEmissionEventsFile;

	@Parameter(names = "-netCdfOutput", required = true)
	private String netCdfOutput;

	@Parameter(names = "-vehicleIndexOutput", required = true)
	private String vehicleIndexOutput;

	public static void main(String[] args) throws IOException {

		var processor = new PositionEmissionsToNetCdfPostprocessing();
		JCommander.newBuilder().addObject(processor).build().parse(args);
		processor.run();
	}

	public void run() throws IOException {

		var positionEventMapper = PositionEmissionsModule.PositionEmissionEvent.getEventMapper();

		// do a first pass to count the number of vehicles
		var counterHandler = new VehicleCounter();
		var manager = EventsUtils.createEventsManager();
		manager.addHandler(counterHandler);
		var reader = new MatsimEventsReader(manager);
		reader.addCustomEventMapper(PositionEmissionsModule.PositionEmissionEvent.EVENT_TYPE, positionEventMapper);
		reader.readFile(positionEmissionEventsFile);
		counterHandler.finish();

		// do a second pass where we actually read the emissions and put them into a netcdf file
		var numberOfVehicles = counterHandler.getMaxVehCount();
		var numberOfTimesteps = counterHandler.getTimestepCount();
		var pollutants = Map.of(
				Pollutant.NO2, "NO2",
				Pollutant.CO2_TOTAL, "CO2",
				Pollutant.PM, "PM10",
				Pollutant.CO, "CO",
				Pollutant.NOx, "NOx"
		);

		PositionEmissionNetcdfModule.NetcdfWriterHandler netCdfWriter = null;
		try {
			netCdfWriter = new PositionEmissionNetcdfModule.NetcdfWriterHandler(
					netCdfOutput, numberOfVehicles, numberOfTimesteps, pollutants, true
			);
			manager = EventsUtils.createEventsManager();
			manager.addHandler(netCdfWriter);
			reader = new MatsimEventsReader(manager);
			reader.addCustomEventMapper(PositionEmissionsModule.PositionEmissionEvent.EVENT_TYPE, positionEventMapper);
			reader.readFile(positionEmissionEventsFile);
		} catch (Exception e) {
			throw new RuntimeException(e);
		} finally {
			if (netCdfWriter != null) {
				netCdfWriter.closeFile();
				netCdfWriter.getVehicleIdIndex().writeToFile(vehicleIndexOutput);
			}
		}
	}

	private static class VehicleCounter implements BasicEventHandler {

		private double currentTime = 0;
		private int vehCountForCurrentTime;

		@Getter
		private int maxVehCount = 0;

		@Getter
		private int timestepCount = 0;

		@Override
		public void handleEvent(Event event) {

			if (event.getTime() != currentTime) {
				onNextTimestep(event.getTime());
			}

			if (event.getEventType().equals(PositionEmissionsModule.PositionEmissionEvent.EVENT_TYPE)) {
				var positionEmissionEvent = (PositionEmissionsModule.PositionEmissionEvent)event;

				if (positionEmissionEvent.getEmissionType().equals("cold")) return; // ignore cold events for now, but think about it later

				vehCountForCurrentTime++;
			}
		}

		public void finish() {
			// make sure the count of the last timestep is considered in case it had the most vehicles
			onNextTimestep(Double.POSITIVE_INFINITY);
		}

		private void onNextTimestep(double nextTimestep) {
			if (vehCountForCurrentTime > maxVehCount) {
				maxVehCount = vehCountForCurrentTime;
			}
			vehCountForCurrentTime = 0;
			currentTime = nextTimestep;
			timestepCount++;
		}
	}
}
