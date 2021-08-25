package org.matsim.mosaik2.agentEmissions;

import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.events.Event;
import org.matsim.contrib.emissions.HbefaVehicleCategory;
import org.matsim.contrib.emissions.PositionEmissionsModule;
import org.matsim.contrib.emissions.events.ColdEmissionEvent;
import org.matsim.contrib.emissions.events.WarmEmissionEvent;
import org.matsim.contrib.emissions.utils.EmissionsConfigGroup;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigGroup;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.ReflectiveConfigGroup;
import org.matsim.core.config.groups.ControlerConfigGroup;
import org.matsim.core.controler.AbstractModule;
import org.matsim.core.controler.Controler;
import org.matsim.core.controler.OutputDirectoryHierarchy;
import org.matsim.core.controler.events.BeforeMobsimEvent;
import org.matsim.core.controler.listener.BeforeMobsimListener;
import org.matsim.core.events.EventsManagerImpl;
import org.matsim.core.events.algorithms.EventWriter;
import org.matsim.core.events.algorithms.EventWriterXML;
import org.matsim.core.events.handler.BasicEventHandler;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.vehicles.VehicleUtils;
import org.matsim.vis.snapshotwriters.PositionEvent;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

public class AbstractRunPositionEmissions {

	ConfigGroup[] getConfigGroups() {
		return new ConfigGroup[] {new EmissionsConfigGroup(), new PositionEmissionNetcdfModule.NetcdfEmissionWriterConfig(), new HbefaConfigGroup() };
	}

	Config loadConfig(String[] args) {

		var modules = getConfigGroups();
		var config = ConfigUtils.loadConfig(args, modules);
		applyConfig(config);
		return config;
	}

	Scenario loadScenario(Config config) {

		var scenario = ScenarioUtils.loadScenario(config);
		applyScenario(scenario);
		return scenario;
	}

	void applyScenario(Scenario scenario) {
		Utils.applyNetworkAttributes(scenario.getNetwork());

		// add engine information
		for (var vehicleType : scenario.getVehicles().getVehicleTypes().values()) {
			Utils.applyVehicleInformation(vehicleType);

			if (vehicleType.getId().toString().equals("freight")) {
				VehicleUtils.setHbefaVehicleCategory(vehicleType.getEngineInformation(), HbefaVehicleCategory.HEAVY_GOODS_VEHICLE.toString());
			}
		}

		for (var transitVehicleType : scenario.getTransitVehicles().getVehicleTypes().values()) {
			Utils.applyVehicleInformation(transitVehicleType);
			VehicleUtils.setHbefaVehicleCategory(transitVehicleType.getEngineInformation(), HbefaVehicleCategory.NON_HBEFA_VEHICLE.toString());
		}

		// the following two options make vehicles travel on the center of a link without offsett to the right
		scenario.getNetwork().setEffectiveLaneWidth(0);
	}

	Controler loadControler(Scenario scenario) {

		var controler = new Controler(scenario);
		applyControler(controler);
		return controler;
	}

	void applyControler(Controler controler) {
		controler.addOverridingModule(new PositionEmissionsModule());
		controler.addOverridingModule(new PositionEmissionNetcdfModule());
		controler.addOverridingModule(new EventWriterModule());

		controler.addOverridingModule(new AbstractModule() {
			@Override
			public void install() {
				// we need single threaded events manager because other wise it doesn't work
				bind(EventsManager.class).to(EventsManagerImpl.class).in(Singleton.class);
			}
		});
	}

	void applyConfig(Config config) {

		var hbefaConfig = (HbefaConfigGroup) config.getModules().get(HbefaConfigGroup.GROUP_NAME);
		var emissionsConfig = (EmissionsConfigGroup) config.getModules().get(EmissionsConfigGroup.GROUP_NAME);
		var netCdfConfig = (PositionEmissionNetcdfModule.NetcdfEmissionWriterConfig) config.getModules().get(PositionEmissionNetcdfModule.NetcdfEmissionWriterConfig.GOUP_NAME);

		Utils.applyEmissionSettings(emissionsConfig, Paths.get(hbefaConfig.getHbefaDirectory()));
		Utils.applyNetCdfWriterConfig(netCdfConfig);
		Utils.applySnapshotSettings(config);

		// disable default events writer
		config.controler().setWriteEventsInterval(0);
		config.controler().setOverwriteFileSetting(OutputDirectoryHierarchy.OverwriteFileSetting.deleteDirectoryIfExists);


		config.qsim().setLinkWidthForVis(0);


	}

	static class HbefaConfigGroup extends ReflectiveConfigGroup {

		public static final String GROUP_NAME = "hbefa";

		private String hbefaDirectory = "";

		public HbefaConfigGroup() {
			super(GROUP_NAME);
		}

		@StringGetter("directory")
		public String getHbefaDirectory() {
			return hbefaDirectory;
		}

		@StringSetter("directory")
		public void setHbefaDirectory(String hbefaDirectory) {
			this.hbefaDirectory = hbefaDirectory;
		}
	}

	private static class EventWriterModule extends AbstractModule {

		@Override
		public void install() {
			addControlerListenerBinding().to(WriterSetUp.class);
		}
	}

	private static class WriterSetUp implements BeforeMobsimListener {

		@Inject
		private OutputDirectoryHierarchy outputDirectoryHierarchy;

		@Inject
		private EventsManager eventsManager;

		@Inject
		private ControlerConfigGroup controlerConfig;

		@Override
		public void notifyBeforeMobsim(BeforeMobsimEvent event) {

			if (event.isLastIteration()) return;

			var eventsFile = outputDirectoryHierarchy.getIterationFilename(event.getIteration(), "events.xml.gz");
			var emissionEventsFile = outputDirectoryHierarchy.getIterationFilename(event.getIteration(), "poition-emission-events.xml.gz");

			// write everything except: positions, position-emissions, warm-emissions, cold-emissions
			var normalWriter = new FilterEventsWriter(
					e -> (
							 !e.getEventType().equals(PositionEvent.EVENT_TYPE)
									&& !e.getEventType().equals(PositionEmissionsModule.PositionEmissionEvent.EVENT_TYPE)
									&& !e.getEventType().equals(WarmEmissionEvent.EVENT_TYPE)
									&& !e.getEventType().equals(ColdEmissionEvent.EVENT_TYPE)

					), eventsFile);

			// write only position-emissions
			var emissionWriter = new FilterEventsWriter(e -> e.getEventType().equals(PositionEmissionsModule.PositionEmissionEvent.EVENT_TYPE), emissionEventsFile);

			eventsManager.addHandler(normalWriter);
			eventsManager.addHandler(emissionWriter);
		}
	}

	private static class FilterEventsWriter implements BasicEventHandler, EventWriter {

		private final Predicate<Event> filter;
		private final EventWriterXML writer;
		private final String filename;

		private final AtomicInteger counter = new AtomicInteger();

		public FilterEventsWriter(Predicate<Event> filter, String outFilename) {
			this.filter = filter;
			this.writer = new EventWriterXML(outFilename);
			this.filename = outFilename;
		}

		@Override
		public void closeFile() {
			writer.closeFile();
		}

		@Override
		public void handleEvent(Event event) {

			if (filter.test(event)){
				if (counter.incrementAndGet() % 1000 == 0) {
					System.out.println(filename + ": " + counter);
				}
				writer.handleEvent(event);
			}
		}
	}
}
