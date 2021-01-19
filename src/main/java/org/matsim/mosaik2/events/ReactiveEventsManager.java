package org.matsim.mosaik2.events;

import com.google.common.util.concurrent.AtomicDouble;
import org.apache.log4j.Logger;
import org.matsim.api.core.v01.events.Event;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.events.EventsManagerImpl;
import org.matsim.core.events.SingleHandlerEventsManager;
import org.matsim.core.events.handler.EventHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.Flow;
import java.util.concurrent.Phaser;
import java.util.concurrent.SubmissionPublisher;
import java.util.concurrent.atomic.AtomicBoolean;

public class ReactiveEventsManager implements EventsManager {

    private static final Logger log = Logger.getLogger(ReactiveEventsManager.class);

    private final SubmissionPublisher<Event> publisher;
    private final List<SubscriberManager> subscribers = new ArrayList<>();
    private final Phaser phaser = new Phaser(1);

    private final AtomicBoolean isInitialized = new AtomicBoolean(false);
    private final AtomicDouble currentTimestep = new AtomicDouble(Double.NEGATIVE_INFINITY);

    // this delegate is used to handle events synchronously during un-initialized state
    private final EventsManager delegate = new EventsManagerImpl();

    public ReactiveEventsManager(int parallelism) {

        publisher = new SubmissionPublisher<>(Executors.newWorkStealingPool(parallelism), 1024);
    }


    @Override
    public void processEvent(Event event) {

        // only test for order, if we are initialized. Some code in some contribs emits unordered events in between iterations
        if (event.getTime() < currentTimestep.get() && isInitialized.get()) {
            throw new RuntimeException("Event with time step: " + event.getTime() + " was submitted. But current timestep was: " + currentTimestep + ". Events must be ordered chronologically");
        }

        setCurrentTimestep(event.getTime());

        if (isInitialized.get()) {
            for (var subscriber : subscribers) {
                log.info("register for: " + event.getEventType() + ", " + event.getTime());
                subscriber.register();
            }
            publisher.submit(event);
        }
        else {
            log.info("process events submit to delegate: " + event.getEventType() + ", " + event.getTime());
            delegate.processEvent(event);
        }

    }

    private void setCurrentTimestep(double time) {

        while (time > currentTimestep.get()) {
            var previousTime = currentTimestep.get();
            // wait for event handlers to process all events from previous time step including events emitted after 'afterSimStep' was called
            phaser.arriveAndAwaitAdvance();
            currentTimestep.compareAndSet(previousTime, time);
        }
    }

    @Override
    public void addHandler(EventHandler handler) {

        var manager = new SingleHandlerEventsManager(handler);
        var subscriber = new SubscriberManager(manager, phaser);
        subscribers.add(subscriber);
        publisher.subscribe(subscriber);

        delegate.addHandler(handler);
    }

    @Override
    public void removeHandler(EventHandler handler) {

    }

    @Override
    public void resetHandlers(int iteration) {

    }

    @Override
    public void initProcessing() {
        // wait for processing of events which were emitted in between iterations
        //phaser.arriveAndAwaitAdvance();
        isInitialized.set(true);
        currentTimestep.set(Double.NEGATIVE_INFINITY);
        delegate.initProcessing();

    }

    @Override
    public void afterSimStep(double time) {
        phaser.arriveAndAwaitAdvance();
    }

    @Override
    public void finishProcessing() {
        log.info("before finish processing");
        // setting isInitialized before waiting for finishing of all events
        isInitialized.set(false);
        phaser.arriveAndAwaitAdvance();


        delegate.finishProcessing();
        log.info("after finish processing");
    }

    private static class SubscriberManager implements Flow.Subscriber<Event> {

        private final EventsManager manager;
        private final Phaser phaser;

        private Flow.Subscription subscription;

        private SubscriberManager(EventsManager manager, Phaser phaser) {
            this.manager = manager;
            // create a tiered phaser
            this.phaser = new Phaser(phaser);
        }

        private void register() {

            phaser.register();
        }


        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            log.info("subscribed");
            this.subscription = subscription;
            this.subscription.request(1);
        }

        @Override
        public void onNext(Event item) {
            try {
                //log.info("onNext: " + item.getEventType() + ": " + item.getTime());
                manager.processEvent(item);
            } finally {
                //log.info("onNext arriveAndDeregister");
                phaser.arriveAndDeregister();
            }
            subscription.request(1);
        }

        @Override
        public void onError(Throwable throwable) {

        }

        @Override
        public void onComplete() {

        }
    }
}
