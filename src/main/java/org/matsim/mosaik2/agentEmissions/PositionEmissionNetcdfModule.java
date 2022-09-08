package org.matsim.mosaik2.agentEmissions;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.events.Event;
import org.matsim.contrib.emissions.Pollutant;
import org.matsim.contrib.emissions.PositionEmissionsModule;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.config.ReflectiveConfigGroup;
import org.matsim.core.controler.AbstractModule;
import org.matsim.core.controler.OutputDirectoryHierarchy;
import org.matsim.core.controler.events.AfterMobsimEvent;
import org.matsim.core.controler.events.BeforeMobsimEvent;
import org.matsim.core.controler.events.ShutdownEvent;
import org.matsim.core.controler.listener.AfterMobsimListener;
import org.matsim.core.controler.listener.BeforeMobsimListener;
import org.matsim.core.controler.listener.ShutdownListener;
import org.matsim.core.events.algorithms.EventWriter;
import org.matsim.core.events.handler.BasicEventHandler;
import org.matsim.core.utils.collections.Tuple;
import org.matsim.vehicles.Vehicle;
import ucar.ma2.*;
import ucar.nc2.Attribute;
import ucar.nc2.NetcdfFileWriter;

import javax.inject.Inject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

@Log4j2
public class PositionEmissionNetcdfModule extends AbstractModule {

	@Override
	public void install() {
		addControlerListenerBinding().to(MobsimHandler.class);
	}

	static class NetcdfEmissionWriterConfig extends ReflectiveConfigGroup {

		public static final String GOUP_NAME = "netcdfPositionEmission";

		private EnumMap<Pollutant, String> pollutantMapping = new EnumMap<>(Pollutant.class);
		private boolean calculateNOFromNOxAndNO2 = true;

		public NetcdfEmissionWriterConfig() {
			super(GOUP_NAME);
		}

		public boolean getCalculateNOFromNOxAndNO2() {
			return calculateNOFromNOxAndNO2;
		}

		public void setCalculateNOFromNOxAndNO2(boolean value) {
			this.calculateNOFromNOxAndNO2 = value;
		}

		@StringGetter("pollutants")
		public String getPollutantsAsString() {
			return pollutantMapping.entrySet().stream()
					.map(entry -> entry.getKey() + "->" + entry.getValue())
					.collect(Collectors.joining(", "));
		}

		public Map<Pollutant, String> getPollutantMapping() {
			return pollutantMapping;
		}

		@StringSetter("pollutants")
		public void setPollutantMapping(String valuesAsString) {
			var map = Arrays.stream(valuesAsString.split(","))
					.map(String::trim)
					.map(keyValue -> {
						var splitted = keyValue.split("->");
						if (splitted.length != 2) throw new IllegalArgumentException("NO!");
						return Tuple.of(Pollutant.valueOf(splitted[0]), splitted[1]);
					})
					.collect(Collectors.toMap(Tuple::getFirst, Tuple::getSecond));
			this.pollutantMapping = new EnumMap<>(map);
		}

		public void setPollutants(Map<Pollutant, String> pollutants) {
			this.pollutantMapping = new EnumMap<>(pollutants);
		}
	}

	private static class MobsimHandler implements BeforeMobsimListener, AfterMobsimListener, ShutdownListener {

		@Inject
		private EventsManager eventsManager;

		@Inject
		private OutputDirectoryHierarchy outputDirectoryHierarchy;

		@Inject
		private Scenario scenario;

		@Inject
		private NetcdfEmissionWriterConfig config;

		private NetcdfWriterHandler netcdfHandler;

		@Override
		public void notifyAfterMobsim(AfterMobsimEvent event) {

			if (netcdfHandler != null) {
				eventsManager.removeHandler(netcdfHandler);
				netcdfHandler.closeFile();
				netcdfHandler.getVehicleIdIndex().writeToFile(outputDirectoryHierarchy.getIterationFilename(event.getIteration(), "position-emissions-vehicleIdIndex.csv"));
				netcdfHandler = null;

			}
		}

		@Override
		public void notifyBeforeMobsim(BeforeMobsimEvent event) {
			if (event.isLastIteration()) {
				try {
					netcdfHandler = new NetcdfWriterHandler(outputDirectoryHierarchy.getIterationFilename(event.getIteration(), "position-emissions.nc"),
							scenario.getPopulation().getPersons().size(), -1, config.getPollutantMapping(), config.getCalculateNOFromNOxAndNO2());
					eventsManager.addHandler(netcdfHandler);
				} catch (IOException e) {
					throw new RuntimeException(e);
				}
			}
		}

		@Override
		public void notifyShutdown(ShutdownEvent event) {
			if (event.isUnexpected() && netcdfHandler != null)
				netcdfHandler.closeFile();
		}
	}

	public static class VehicleIdIndex {

		private final Object2IntOpenHashMap<Id<Vehicle>> idMapping = new Object2IntOpenHashMap<>();

		static List<Id<Vehicle>> readFromFile(String filename) {

			List<Id<Vehicle>> result = new ArrayList<>();
			try (var reader = Files.newBufferedReader(Paths.get(filename)); var csvParser = new CSVParser(reader, CSVFormat.DEFAULT.withHeader())) {

				for (var record : csvParser) {
					// var index = Integer.parseInt(record.get("index"));
					var id = record.get("vehicleId");
					result.add(Id.createVehicleId(id));
				}
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
			return result;
		}

		int addIfNecessary(Id<Vehicle> id) {
			return idMapping.computeIfAbsent(id, key -> idMapping.size());
		}

		void writeToFile(String filename) {

			try (var writer = Files.newBufferedWriter(Paths.get(filename)); var printer = CSVFormat.DEFAULT.withHeader("index", "vehicleId").print(writer)) {

				idMapping.object2IntEntrySet().stream()
						.sorted(Comparator.comparingInt(Object2IntMap.Entry::getIntValue))
						.forEach(entry -> print(printer, entry));

			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		private void print(CSVPrinter printer, Object2IntMap.Entry<?> entry) {
			try {
				printer.printRecord(entry.getIntValue(), entry.getKey());
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	static class NetcdfWriterHandler implements BasicEventHandler, EventWriter {

		private final NetcdfFileWriter writer;

		// Mapping of string based matsim ids to successive integer (due to netcdf file format)
		//private final Object2IntOpenHashMap<Id<Vehicle>> idMapping = new Object2IntOpenHashMap<>();
		@Getter
		private final VehicleIdIndex vehicleIdIndex = new VehicleIdIndex();

		private final Map<Pollutant, String> pollutants;
		private final boolean calculateNO;
		private final Array timeData = Array.factory(DataType.DOUBLE, new int[]{1});
		private final Array numberOfVehicles = Array.factory(DataType.INT, new int[]{1});
		private final Map<String, ArrayDouble.D2> emissions = new HashMap<>();
		// only try time and ids for now
		private int[] currentTimeIndex = new int[]{-1};
		private double currentTimeStep = Double.NEGATIVE_INFINITY;
		private int currentVehicleIndex = 0;
		private ArrayInt.D2 vehicleIds;
		private ArrayDouble.D2 x;
		private ArrayDouble.D2 y;


		/**
		 * @param filename          output file path
		 * @param numberOfAgents    max number of agents the file should make room for. To achieve small file sizes this should be the maximum number of vehicles on the network per timestep
		 * @param numberOfTimesteps number of timesteps. To achieve faster read speeds of the netCdf file it is better to have limited dimensions. If the number of timesteps is known in advance
		 *                          this should be set. Pass -1 if not known in advance. The time dimension will be unlimited then.
		 * @param pollutants        Mapping of MATSim pollutants to string identifiers used by PALM
		 * @param calculateNO       flag to decide whether NO should be calculated from NOx and NO2. Only works if NOx and NO2 are present in the pollutant mapping
		 * @throws IOException The constructor immediately creates a file. This is why an IOException might be thrown here.
		 */
		NetcdfWriterHandler(String filename, int numberOfAgents, int numberOfTimesteps, Map<Pollutant, String> pollutants, boolean calculateNO) throws IOException {
			log.info("Opening Netcdf Writer at: " + filename);
			writer = NetcdfFileWriter.createNew(NetcdfFileWriter.Version.netcdf3, filename);
			writer.setFill(true);
			this.pollutants = pollutants;
			this.calculateNO = calculateNO;
			writeDimensions(numberOfAgents, numberOfTimesteps);
			writeVariables();
			writeAttributes();
			writer.create();
		}

		private void writeDimensions(int numberOfAgents, int numberOfTimesteps) {

			if (numberOfTimesteps >= 0) {
				writer.addDimension("time", numberOfTimesteps);
			} else {
				writer.addUnlimitedDimension("time");
			}
			writer.addDimension("agents", numberOfAgents);
		}

		private void writeVariables() {

			writer.addVariable("time", DataType.FLOAT, "time");
			writer.addVariable("number_of_vehicles", DataType.INT, "time");
			writer.addVariable("vehicle_id", DataType.INT, "time agents");
			writer.addVariable("x", DataType.FLOAT, "time agents");
			writer.addVariable("y", DataType.FLOAT, "time agents");

			// next think about emissions
			for (var pollutant : pollutants.entrySet()) {
				writer.addVariable(pollutant.getValue(), DataType.FLOAT, "time agents");
			}

			if (calculateNO) {
				writer.addVariable("NO", DataType.FLOAT, "time agents");
			}
		}

		private void writeAttributes() {

			writer.findVariable("time").addAttribute(new Attribute("unit", "s"));

			var fillValue = new Attribute("_Fill_Value", -9999.9D);
			writer.findVariable("vehicle_id").addAttribute(fillValue);
			writer.findVariable("x").addAttribute(fillValue);
			writer.findVariable("y").addAttribute(fillValue);

			// next think about emissions
			for (var pollutant : pollutants.entrySet()) {
				writer.findVariable(pollutant.getValue()).addAttribute(fillValue);
			}

			if (calculateNO) {
				writer.findVariable("NO").addAttribute(fillValue);
			}
		}

		@Override
		public void closeFile() {
			try {
				// write data from last timestep
				log.info("Closing file: First write the last available chunk of data.");
				writeData();

				log.info("Now actually close the file.");
				writer.close();
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}

		@Override
		public void handleEvent(Event event) {
			if (event.getEventType().equals(PositionEmissionsModule.PositionEmissionEvent.EVENT_TYPE)) {

				var positionEmissionEvent = (PositionEmissionsModule.PositionEmissionEvent) event;

				if (positionEmissionEvent.getEmissionType().equals("cold"))
					return; // ignore cold events for now, but think about it later

				// Set currentTimeStep to current event time if necessary
				adjustTime(positionEmissionEvent.getTime());

				int intId = vehicleIdIndex.addIfNecessary(positionEmissionEvent.getVehicleId());

				try {
					vehicleIds.set(0, currentVehicleIndex, intId);
					x.set(0, currentVehicleIndex, positionEmissionEvent.getCoord().getX());
					y.set(0, currentVehicleIndex, positionEmissionEvent.getCoord().getY());

					for (var eventEmissions : positionEmissionEvent.getEmissions().entrySet()) {
						if (pollutants.containsKey(eventEmissions.getKey())) {

							var dataKey = pollutants.get(eventEmissions.getKey());
							var data = emissions.get(dataKey);
							data.set(0, currentVehicleIndex, eventEmissions.getValue());
						}
					}

					if (calculateNO) {
						var nox = positionEmissionEvent.getEmissions().get(Pollutant.NOx);
						var no2 = positionEmissionEvent.getEmissions().get(Pollutant.NO2);
						var no = nox - no2;
						var data = emissions.get("NO");
						data.set(0, currentVehicleIndex, no);
					}

				} catch (Exception e) {
					throw new RuntimeException(e);
				}
				currentVehicleIndex++;
			}
		}

		@Override
		public void reset(int iteration) {
			currentTimeStep = 0;
			currentVehicleIndex = 0;
			currentTimeIndex = new int[]{-1};
		}

		private void adjustTime(double time) {
			if (time > currentTimeStep) {

				// assuming that only positive time values are valid
				if (currentTimeStep >= 0)
					writeData();

				beforeTimestep(time);
			}
		}

		private void writeData() {
			try {
				timeData.setDouble(timeData.getIndex(), currentTimeStep);
				writer.write("time", currentTimeIndex, timeData);

				numberOfVehicles.setInt(numberOfVehicles.getIndex(), currentVehicleIndex);
				writer.write("number_of_vehicles", currentTimeIndex, numberOfVehicles);

				writer.write("vehicle_id", getCurrentIndex(), vehicleIds);
				writer.write("x", getCurrentIndex(), x);
				writer.write("y", getCurrentIndex(), y);

				for (var emission : emissions.entrySet()) {
					writer.write(emission.getKey(), getCurrentIndex(), emission.getValue());
				}

				if (calculateNO)
					writer.write("NO", getCurrentIndex(), emissions.get("NO"));

			} catch (IOException | InvalidRangeException e) {
				throw new RuntimeException(e);
			}
		}

		private int[] getCurrentIndex() {
			return new int[]{currentTimeIndex[0], 0};
		}

		private void beforeTimestep(double time) {

			// reset all the state
			currentTimeStep = time;
			currentVehicleIndex = 0;
			currentTimeIndex[0] = currentTimeIndex[0] + 1; // increase time index by 1
			// person data for the next time slice. Of dimension 1 for timestep and of number of agents for agents
			vehicleIds = new ArrayInt.D2(1, writer.findDimension("agents").getLength(), false);
			x = new ArrayDouble.D2(1, writer.findDimension("agents").getLength());
			y = new ArrayDouble.D2(1, writer.findDimension("agents").getLength());

			for (var pollutant : pollutants.entrySet()) {
				emissions.put(pollutant.getValue(), new ArrayDouble.D2(1, writer.findDimension("agents").getLength()));
			}

			if (calculateNO)
				emissions.put("NO", new ArrayDouble.D2(1, writer.findDimension("agents").getLength()));
		}
	}
}