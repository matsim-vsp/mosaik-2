package org.matsim.mosaik2.events;

import com.google.common.util.concurrent.AtomicDouble;
import org.apache.log4j.Logger;
import org.matsim.api.core.v01.events.Event;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.events.EventsManagerImpl;
import org.matsim.core.events.SingleHandlerEventsManager;
import org.matsim.core.events.handler.EventHandler;

import javax.inject.Inject;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class ReactiveEventsManager implements EventsManager {

    private static final Logger log = Logger.getLogger(ReactiveEventsManager.class);


    private final Map<EventHandler, SubscriberManager> subscribers = new HashMap<>();
    private final Phaser phaser = new Phaser(1);

    private final AtomicBoolean isInitialized = new AtomicBoolean(false);
    private final AtomicDouble currentTimestep = new AtomicDouble(Double.NEGATIVE_INFINITY);
    private final AtomicBoolean hasThrown = new AtomicBoolean(false);

    // this delegate is used to handle events synchronously during un-initialized state
    private final EventsManager delegate = new EventsManagerImpl();

    private SubmissionPublisher<Event> publisher;

    public ReactiveEventsManager(int parallelism) {
        this();
    }

    @Inject
    public ReactiveEventsManager() {
        log.info("creating publisher");
        publisher = new SubmissionPublisher<>();
    }

    @Override
    public void processEvent(Event event) {

        // only test for order, if we are initialized. Some code in some contribs emits unordered events in between iterations
        if (event.getTime() < currentTimestep.get() && isInitialized.get()) {
            throw new RuntimeException("Event with time step: " + event.getTime() + " was submitted. But current timestep was: " + currentTimestep + ". Events must be ordered chronologically");
        }

        setCurrentTimestep(event.getTime());

        // the phaser register and de-register around the submission of events is necessary to get the finish processing right
        phaser.register();

        if (isInitialized.get()) {
           /* for (var subscriber : subscribers.values()) {
                //log.info("register for: " + event.getEventType() + ", " + event.getTime());
                subscriber.register();
            }

            */
            log.info("submit event " + event.getEventType() + ", " + event.getTime());
            publisher.submit(event);
        }
        else {
            log.info("process events submit to delegate: " + event.getEventType() + ", " + event.getTime());
            delegate.processEvent(event);
        }

        phaser.arriveAndDeregister();
    }

    private void setCurrentTimestep(double time) {

        while (time > currentTimestep.get()) {
            var previousTime = currentTimestep.get();
            // wait for event handlers to process all events from previous time step including events emitted after 'afterSimStep' was called
            awaitProcessingOfEvents();
            currentTimestep.compareAndSet(previousTime, time);
        }
    }

    @Override
    public void addHandler(EventHandler handler) {

        var manager = new SingleHandlerEventsManager(handler);
        var subscriber = new SubscriberManager(manager, phaser);
        subscribers.put(handler, subscriber);
        publisher.subscribe(subscriber);

        delegate.addHandler(handler);
    }

    @Override
    public void removeHandler(EventHandler handler) {

        if (isInitialized.get()) throw new RuntimeException("don't remove handlers while parallel events processing happens");

        if (subscribers.containsKey(handler)) {
            var subscriber = subscribers.remove(handler);
            subscriber.cancelSubscription();
        }
    }

    @Override
    public void resetHandlers(int iteration) {
        for (var subscriber : subscribers.values())
            subscriber.resetHandlers(iteration);
    }

    @Override
    public void initProcessing() {

        reCreatePublisher();
        isInitialized.set(true);
        currentTimestep.set(Double.NEGATIVE_INFINITY);
        delegate.initProcessing();
    }

    @Override
    public void afterSimStep(double time) {
        awaitProcessingOfEvents();
    }

    @Override
    public void finishProcessing() {
        log.info("before finish processing");
        // setting isInitialized before waiting for finishing of all events
        isInitialized.set(false);
        publisher.close();
        phaser.arriveAndAwaitAdvance();

        if (!hasThrown.get())
            throwExceptionIfAnyThreadCrashed();

        delegate.finishProcessing();
    }

    private void awaitProcessingOfEvents() {
        publisher.close();
        log.info("closed publisher. Waiting for handlers to finish");
        phaser.arriveAndAwaitAdvance();
        throwExceptionIfAnyThreadCrashed();
        reCreatePublisher();
    }

    private void reCreatePublisher() {
        log.info("setting up new publisher");
        var start = System.nanoTime();
        this.publisher = new SubmissionPublisher<>();
        for (var subscription : subscribers.values()) {
            publisher.subscribe(subscription);
        }
        log.info("Re-creating took: " + (System.nanoTime() - start) / 1000000.0 + "s");
    }

    private void throwExceptionIfAnyThreadCrashed() {
        subscribers.values().stream()
                .filter(SubscriberManager::hadException)
                .findAny()
                .ifPresent(process -> {
                    hasThrown.set(true);
                    throw new RuntimeException(process.getCaughtException());
                });
    }

    private static class SubscriberManager implements Flow.Subscriber<Event> {

        private final EventsManager manager;
        private final Phaser phaser;

        private Flow.Subscription subscription;
        private Exception caughtException;

        private SubscriberManager(EventsManager manager, Phaser phaser) {
            this.manager = manager;
            // create a tiered phaser
            this.phaser = phaser;
            phaser.register();
        }

       /* private void register() {
            phaser.register();
        }

        */

        private void resetHandlers(int iteration) {
            manager.resetHandlers(iteration);
        }

        private void cancelSubscription() {
            if (subscription != null)
                subscription.cancel();
        }

        private boolean hadException() {
            return caughtException != null;
        }

        private Exception getCaughtException() {
            return caughtException;
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            log.info("subscribed");
            this.subscription = subscription;
            this.subscription.request(1);
        }

        @Override
        public void onNext(Event item) {
            tryProcessEvent(item);
           // log.info("processed event " + item.getEventType() + ", " + item.getTime());
            subscription.request(1);
        }

        private void tryProcessEvent(Event event) {
            try {
             //   log.info("process event");
                manager.processEvent(event);
            } catch (Exception e) {
                log.info("caught exception");
                e.printStackTrace();
                caughtException = e;
            } /*finally {
                phaser.arriveAndDeregister();
            }
            */
        }

        @Override
        public void onError(Throwable throwable) {
            log.info("on error: " + throwable.getMessage());
        }

        @Override
        public void onComplete() {
            log.info("on complete");
            phaser.arrive();
        }
    }
}
