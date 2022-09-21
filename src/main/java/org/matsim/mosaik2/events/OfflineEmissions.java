package org.matsim.mosaik2.events;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.events.Event;
import org.matsim.api.core.v01.network.Link;
import org.matsim.contrib.emissions.EmissionModule;
import org.matsim.contrib.emissions.HbefaVehicleCategory;
import org.matsim.contrib.emissions.events.ColdEmissionEvent;
import org.matsim.contrib.emissions.events.WarmEmissionEvent;
import org.matsim.contrib.emissions.utils.EmissionsConfigGroup;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.controler.AbstractModule;
import org.matsim.core.controler.Injector;
import org.matsim.core.events.EventsUtils;
import org.matsim.core.events.MatsimEventsReader;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.mosaik2.Utils;
import org.matsim.vehicles.EngineInformation;
import org.matsim.vehicles.VehicleType;
import org.matsim.vehicles.VehicleUtils;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Random;
import java.util.function.Predicate;

public class OfflineEmissions {

	private static final String hbefaAverageWarm = "projects\\matsim-germany\\hbefa\\hbefa-files\\v4.1\\EFA_HOT_Vehcat_2020_Average.csv";
	private static final String hbefaAverageCold = "projects\\matsim-germany\\hbefa\\hbefa-files\\v4.1\\EFA_ColdStart_Concept_2020_detailed_perTechAverage_withHGVetc.csv";

	private static String getOutputFile(Path outputDir, String runId, String filename) {
		return outputDir.resolve(runId + ".output_" + filename + ".xml.gz").toString();
	}

	public static void main(String[] args) {

		var sharedSvnArgs = new Utils.SharedSvnARg();
		var outputArgs = new OutputArgs();
		JCommander.newBuilder()
				.addObject(sharedSvnArgs)
				.addObject(outputArgs)
				.build()
				.parse(args);

		var sharedSvn = Paths.get(sharedSvnArgs.getSharedSvn());
		var outputDir = Paths.get(outputArgs.outputDir);
		var runId = outputArgs.runId;

		var config = ConfigUtils.createConfig();
		var emissionConfigGroup = new EmissionsConfigGroup();
		config.addModule(emissionConfigGroup);

		emissionConfigGroup.setDetailedVsAverageLookupBehavior(EmissionsConfigGroup.DetailedVsAverageLookupBehavior.directlyTryAverageTable);
		emissionConfigGroup.setHbefaRoadTypeSource(EmissionsConfigGroup.HbefaRoadTypeSource.fromLinkAttributes);
		emissionConfigGroup.setAverageColdEmissionFactorsFile(sharedSvn.resolve(hbefaAverageCold).toString());
		emissionConfigGroup.setAverageWarmEmissionFactorsFile(sharedSvn.resolve(hbefaAverageWarm).toString());
		emissionConfigGroup.setNonScenarioVehicles(EmissionsConfigGroup.NonScenarioVehicles.ignore);
		emissionConfigGroup.setHbefaTableConsistencyCheckingLevel(EmissionsConfigGroup.HbefaTableConsistencyCheckingLevel.none);

		// we need the generated vehicles of the matsim run

		config.vehicles().setVehiclesFile(getOutputFile(outputDir, runId, "vehicles"));
		config.network().setInputFile(getOutputFile(outputDir, runId, "network"));
		config.network().setInputCRS("EPSG:25832");
		config.global().setCoordinateSystem("EPSG:25832");
		// config.plans().setInputFile(getOutputFile(outputDir, runId, "plans"));
		// config.facilities().setInputFile(getOutputFile(outputDir, runId, "facilities"));
		config.transit().setTransitScheduleFile(getOutputFile(outputDir, runId, "transitSchedule"));
		config.transit().setVehiclesFile(getOutputFile(outputDir, runId, "transitVehicles"));

		var scenario = ScenarioUtils.loadScenario(config);


		for (Link link : scenario.getNetwork().getLinks().values()) {

			double freespeed;

			if (link.getFreespeed() <= 13.888889) {
				freespeed = link.getFreespeed() * 2;
				// for non motorway roads, the free speed level was reduced
			} else {
				freespeed = link.getFreespeed();
				// for motorways, the original speed levels seems ok.
			}

			if (freespeed <= 8.333333333) { //30kmh
				link.getAttributes().putAttribute("hbefa_road_type", "URB/Access/30");
			} else if (freespeed <= 11.111111111) { //40kmh
				link.getAttributes().putAttribute("hbefa_road_type", "URB/Access/40");
			} else if (freespeed <= 13.888888889) { //50kmh
				double lanes = link.getNumberOfLanes();
				if (lanes <= 1.0) {
					link.getAttributes().putAttribute("hbefa_road_type", "URB/Local/50");
				} else if (lanes <= 2.0) {
					link.getAttributes().putAttribute("hbefa_road_type", "URB/Distr/50");
				} else if (lanes > 2.0) {
					link.getAttributes().putAttribute("hbefa_road_type", "URB/Trunk-City/50");
				} else {
					throw new RuntimeException("NoOfLanes not properly defined");
				}
			} else if (freespeed <= 16.666666667) { //60kmh
				double lanes = link.getNumberOfLanes();
				if (lanes <= 1.0) {
					link.getAttributes().putAttribute("hbefa_road_type", "URB/Local/60");
				} else if (lanes <= 2.0) {
					link.getAttributes().putAttribute("hbefa_road_type", "URB/Trunk-City/60");
				} else if (lanes > 2.0) {
					link.getAttributes().putAttribute("hbefa_road_type", "URB/MW-City/60");
				} else {
					throw new RuntimeException("NoOfLanes not properly defined");
				}
			} else if (freespeed <= 19.444444444) { //70kmh
				link.getAttributes().putAttribute("hbefa_road_type", "URB/MW-City/70");
			} else if (freespeed <= 22.222222222) { //80kmh
				link.getAttributes().putAttribute("hbefa_road_type", "URB/MW-Nat./80");
			} else if (freespeed > 22.222222222) { //faster
				link.getAttributes().putAttribute("hbefa_road_type", "RUR/MW/>130");
			} else {
				throw new RuntimeException("Link not considered...");
			}
		}



       /* var carVehicleType = VehicleUtils.createVehicleType(carVehicleTypeId);
        carVehicleType.setMaximumVelocity(100/3.6);
        carVehicleType.setNetworkMode(TransportMode.car);
        carVehicleType.setLength(7.5);
        carVehicleType.setPcuEquivalents(1.0);


        */
		Id<VehicleType> carVehicleTypeId = Id.create("car", VehicleType.class);
		var carVehicleType = scenario.getVehicles().getVehicleTypes().get(carVehicleTypeId);
		Id<VehicleType> freightVehicleTypeId = Id.create("freight", VehicleType.class);
		VehicleType freightVehicleType = scenario.getVehicles().getVehicleTypes().get(freightVehicleTypeId);

		EngineInformation carEngineInformation = carVehicleType.getEngineInformation();
		VehicleUtils.setHbefaVehicleCategory(carEngineInformation, HbefaVehicleCategory.PASSENGER_CAR.toString());
		VehicleUtils.setHbefaTechnology(carEngineInformation, "average");
		VehicleUtils.setHbefaSizeClass(carEngineInformation, "average");
		VehicleUtils.setHbefaEmissionsConcept(carEngineInformation, "average");

		EngineInformation freightEngineInformation = freightVehicleType.getEngineInformation();
		VehicleUtils.setHbefaVehicleCategory(freightEngineInformation, HbefaVehicleCategory.HEAVY_GOODS_VEHICLE.toString());
		VehicleUtils.setHbefaTechnology(freightEngineInformation, "average");
		VehicleUtils.setHbefaSizeClass(freightEngineInformation, "average");
		VehicleUtils.setHbefaEmissionsConcept(freightEngineInformation, "average");

		Id<VehicleType> bikeVehicleTypeId = Id.create("bike", VehicleType.class);
		if (scenario.getVehicles().getVehicleTypes().containsKey(bikeVehicleTypeId)) {
			// bikes don't have emissions
			VehicleType bikeVehicleType = scenario.getVehicles().getVehicleTypes().get(bikeVehicleTypeId);
			EngineInformation bikeEngineInformation = bikeVehicleType.getEngineInformation();
			VehicleUtils.setHbefaVehicleCategory(bikeEngineInformation, HbefaVehicleCategory.NON_HBEFA_VEHICLE.toString());
			VehicleUtils.setHbefaTechnology(bikeEngineInformation, "average");
			VehicleUtils.setHbefaSizeClass(bikeEngineInformation, "average");
			VehicleUtils.setHbefaEmissionsConcept(bikeEngineInformation, "average");
		}

		// public transit vehicles should be considered as non-hbefa vehicles
		for (VehicleType type : scenario.getTransitVehicles().getVehicleTypes().values()) {
			EngineInformation engineInformation = type.getEngineInformation();
			// TODO: Check! Is this a zero emission vehicle?!
			VehicleUtils.setHbefaVehicleCategory(engineInformation, HbefaVehicleCategory.NON_HBEFA_VEHICLE.toString());
			VehicleUtils.setHbefaTechnology(engineInformation, "average");
			VehicleUtils.setHbefaSizeClass(engineInformation, "average");
			VehicleUtils.setHbefaEmissionsConcept(engineInformation, "average");
		}

		// the following is copy paste from the example...

		EventsManager eventsManager = EventsUtils.createEventsManager();

		AbstractModule module = new AbstractModule() {
			@Override
			public void install() {
				bind(Scenario.class).toInstance(scenario);
				bind(EventsManager.class).toInstance(eventsManager);
				bind(EmissionModule.class);
			}
		};

		com.google.inject.Injector injector = Injector.createInjector(config, module);

		EmissionModule emissionModule = injector.getInstance(EmissionModule.class);

		var onlyEmissionEventsWriter = new FilterEventsWriter(new DownsamplingEmissionEventsFilter(1.0), getOutputFile(outputDir, runId, "only_emission_events"));
		var sample10pctEmissionEventsWriter = new FilterEventsWriter(new DownsamplingEmissionEventsFilter(0.1), getOutputFile(outputDir, runId, "only_01_emission_events"));
		var sample1pctEmissionEventsWriter = new FilterEventsWriter(new DownsamplingEmissionEventsFilter(0.01), getOutputFile(outputDir, runId, "only_001_emission_events"));

		emissionModule.getEmissionEventsManager().addHandler(onlyEmissionEventsWriter);
		emissionModule.getEmissionEventsManager().addHandler(sample10pctEmissionEventsWriter);
		emissionModule.getEmissionEventsManager().addHandler(sample1pctEmissionEventsWriter);

		eventsManager.initProcessing();
		MatsimEventsReader matsimEventsReader = new MatsimEventsReader(eventsManager);
		matsimEventsReader.readFile(getOutputFile(outputDir, runId, "events"));
		eventsManager.finishProcessing();

		onlyEmissionEventsWriter.closeFile();
		sample10pctEmissionEventsWriter.closeFile();
		sample1pctEmissionEventsWriter.closeFile();
	}

	private static class OutputArgs {

		@Parameter(names = "-runId")
		private String runId;

		@Parameter(names = "-outputDir")
		private String outputDir;
	}

	private static class DownsamplingEmissionEventsFilter implements Predicate<Event> {

		private final Random random = new Random();
		private final double scaleFactor;
		private final Predicate<Event> filter = e -> e.getEventType().equals(WarmEmissionEvent.EVENT_TYPE) || e.getEventType().equals(ColdEmissionEvent.EVENT_TYPE);

		public DownsamplingEmissionEventsFilter(double scaleFactor) {
			this.scaleFactor = scaleFactor;
		}

		@Override
		public boolean test(Event event) {
			return random.nextDouble() <= scaleFactor && filter.test(event);
		}
	}
}