package org.matsim.mosaik2.agentEmissions;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import lombok.Getter;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.events.Event;
import org.matsim.contrib.emissions.Pollutant;
import org.matsim.contrib.emissions.PositionEmissionsModule;
import org.matsim.core.events.EventsUtils;
import org.matsim.core.events.MatsimEventsReader;
import org.matsim.core.events.handler.BasicEventHandler;
import org.matsim.vehicles.Vehicle;

import java.io.IOException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

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

		// do a second pass where we actually read the emissions and put them into a netcdf file
		var numberOfVehicles = counterHandler.getVehicles().size();
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
					netCdfOutput, numberOfVehicles, pollutants, true
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

		@Getter
		private final Set<Id<Vehicle>> vehicles = new HashSet<>();


		@Override
		public void handleEvent(Event event) {

			if (event.getEventType().equals(PositionEmissionsModule.PositionEmissionEvent.EVENT_TYPE)) {
				var positionEmissionEvent = (PositionEmissionsModule.PositionEmissionEvent)event;

				if (positionEmissionEvent.getEmissionType().equals("cold")) return; // ignore cold events for now, but think about it later

				vehicles.add(positionEmissionEvent.getVehicleId());
			}
		}
	}
}
