package org.matsim.mosaik2.events;

import org.matsim.api.core.v01.events.Event;
import org.matsim.core.events.algorithms.EventWriter;
import org.matsim.core.events.algorithms.EventWriterXML;
import org.matsim.core.events.handler.BasicEventHandler;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

public class FilterEventsWriter implements BasicEventHandler, EventWriter {

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
            if (counter.incrementAndGet() % 10000 == 0) {
                System.out.println(filename + ": " + counter);
            }
            writer.handleEvent(event);
        }
    }
}
