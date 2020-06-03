package org.matsim.mosaik2.chemistryDriver;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;

public class WriteChemistryInputForErnsReuterSample {

    @Parameter
    private String networkFile = "";

    @Parameter
    private String emissionEventsFile = "";

    public static void main(String[] args) {

        var writer = new WriteChemistryInputForErnsReuterSample();
        JCommander.newBuilder().addObject(writer).build().parse(args);
        writer.write();
    }

    private void write() {


    }
}
