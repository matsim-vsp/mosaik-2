package org.matsim.mosaik2.agentEmissions;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.events.Event;
import org.matsim.api.core.v01.events.VehicleEntersTrafficEvent;
import org.matsim.api.core.v01.events.VehicleLeavesTrafficEvent;
import org.matsim.contrib.emissions.PositionEmissionsModule;
import org.matsim.core.events.EventsUtils;
import org.matsim.core.events.MatsimEventsReader;
import org.matsim.core.events.handler.BasicEventHandler;
import org.matsim.mosaik2.analysis.run.ModalSplitsEvents;
import org.matsim.vehicles.Vehicle;
import ucar.nc2.Attribute;
import ucar.nc2.Variable;
import ucar.nc2.write.NetcdfFileFormat;
import ucar.nc2.write.NetcdfFormatWriter;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import static ucar.nc2.write.NetcdfFormatWriter.createNewNetcdf3;

public class PositionEmissionToMovingSources {

    public static void main(String[] args) {

        var inArgs = new InputArgs();
        JCommander.newBuilder().addObject(inArgs).build().parse(args);

        run(inArgs);

    }

    private static void run(InputArgs inputArgs) {

        var manager = EventsUtils.createEventsManager();

        var reader = new MatsimEventsReader(manager);
        reader.addCustomEventMapper(PositionEmissionsModule.PositionEmissionEvent.EVENT_TYPE, PositionEmissionsModule.PositionEmissionEvent.getEventMapper());
        reader.readFile(inputArgs.positionEmissionEventsFile);

    }

    private static class NetCDFWriter implements BasicEventHandler {

        private double currentTimeStep = 0;

        private final NetcdfFormatWriter internalWriter;

        NetCDFWriter(String outputFile) throws IOException {
            this.internalWriter = NetcdfFormatWriter.builder()
                    .setFormat(NetcdfFileFormat.NETCDF4)
                    .setLocation(outputFile)
                    .addAttribute(new Attribute("author", "Janek Laudan"))
                    .addAttribute(new Attribute("lod", "2"))
                    .build();

            internalWriter.updateAttribute(null, new Attribute("num_emission_path", 60.));
        }

        private Set<Id<Vehicle>> vehiclesOnRoute = new HashSet<>();

        @Override
        public void handleEvent(Event event) {

            prepareTimestep(event.getTime());

            if (event instanceof PositionEmissionsModule.PositionEmissionEvent) {
                // do something here.
            } else if (event instanceof VehicleEntersTrafficEvent entersTrafficEvent) {
                vehiclesOnRoute.add(entersTrafficEvent.getVehicleId());
            } else if (event instanceof VehicleLeavesTrafficEvent leavesTrafficEvent) {
                vehiclesOnRoute.remove(leavesTrafficEvent.getVehicleId());
            }
        }

        private void prepareTimestep(double time) {
            if (time == this.currentTimeStep) return;

            this.currentTimeStep = time;
        }
    }

    private static class InputArgs {
        @Parameter(names = "-pee", required = true)
        private String positionEmissionEventsFile;

        @Parameter(names = "-netCdfOutput", required = true)
        private String netCdfOutput;

    }
}
