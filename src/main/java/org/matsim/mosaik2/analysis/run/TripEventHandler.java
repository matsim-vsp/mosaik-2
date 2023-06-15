package org.matsim.mosaik2.analysis.run;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.locationtech.jts.geom.*;
import org.locationtech.jts.geom.impl.CoordinateArraySequence;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.events.*;
import org.matsim.api.core.v01.events.handler.*;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.router.MainModeIdentifier;
import org.matsim.core.router.StageActivityTypeIdentifier;
import org.matsim.core.utils.geometry.geotools.MGC;
import org.matsim.facilities.ActivityFacility;
import org.matsim.vehicles.Vehicle;

import java.util.*;
import java.util.function.Predicate;

public class TripEventHandler implements
		ActivityEndEventHandler,
		ActivityStartEventHandler,
		PersonDepartureEventHandler,
		PersonArrivalEventHandler,
		PersonStuckEventHandler,
		PersonEntersVehicleEventHandler,
		PersonLeavesVehicleEventHandler,
		TransitDriverStartsEventHandler,
		LinkLeaveEventHandler {

	private final Set<Id<Person>> drivers = new HashSet<>();
	private final Map<Id<Person>, List<TripEventHandler.Trip>> tripToPerson = new HashMap<>();

	private final Map<Id<Vehicle>, Id<Person>> vehicle2Person = new HashMap<>();
	private final MainModeIdentifier mainModeIdentifier;
	private final Predicate<Id<Person>> agentFilter;
	private final Set<Id<Person>> stuck = new HashSet<>();

	public TripEventHandler(MainModeIdentifier mainModeIdentifier, Predicate<Id<Person>> agentFilter) {

		this.mainModeIdentifier = mainModeIdentifier;
		this.agentFilter = agentFilter;
	}

	public Map<Id<Person>, List<TripEventHandler.Trip>> getTrips() {
		return new HashMap<>(tripToPerson);
	}

	public Set<Id<Person>> getStuckPersons() {
		return new HashSet<>(stuck);
	}

	@Override
	public void handleEvent(TransitDriverStartsEvent event) {

		drivers.add(event.getDriverId());
	}

	@Override
	public void handleEvent(ActivityEndEvent event) {
		if (StageActivityTypeIdentifier.isStageActivity(event.getActType()) || drivers.contains(event.getPersonId()) || !agentFilter.test(event.getPersonId()))
			return;

		// maybe handle drt? Drt drivers have their own activities

		var trips = tripToPerson.computeIfAbsent(event.getPersonId(), id -> new ArrayList<>());

		// we have to put in the trip here, since the activity end lets us know whether we have a main activity or a
		// staging acitivity
		TripEventHandler.Trip trip = new TripEventHandler.Trip(event.getPersonId(), trips.size());
		trip.departureTime = event.getTime();
		trip.departureLink = event.getLinkId();
		trip.departureFacility = event.getFacilityId();
		trip.departureCoord = event.getCoord();

		trips.add(trip);
	}

	@Override
	public void handleEvent(ActivityStartEvent event) {

		// Don't end the trip until we have a real activity
		if (StageActivityTypeIdentifier.isStageActivity(event.getActType()) || !tripToPerson.containsKey(event.getPersonId()))
			return;

		TripEventHandler.Trip trip = getCurrentTrip(event.getPersonId());
		trip.arrivalLink = event.getLinkId();
		trip.arrivalTime = event.getTime();
		trip.arrivalFacility = event.getFacilityId();
		trip.arrivalCoord = event.getCoord();

		try {
			trip.mainMode = mainModeIdentifier.identifyMainMode(trip.legs);
		} catch (Exception e) {
			// the default main mode identifier can't handle non-network-walk only
			trip.mainMode = TransportMode.non_network_walk;
		}
	}

	@Override
	public void handleEvent(PersonArrivalEvent event) {

		if (!tripToPerson.containsKey(event.getPersonId())) return;

		TripEventHandler.Trip trip = getCurrentTrip(event.getPersonId());
		Leg leg = trip.legs.get(trip.legs.size() - 1);
		leg.setTravelTime(event.getTime() - leg.getDepartureTime().seconds());
	}

	@Override
	public void handleEvent(PersonDepartureEvent event) {

		if (!tripToPerson.containsKey(event.getPersonId())) return;

		// a new leg is started
		Leg leg = PopulationUtils.createLeg(event.getLegMode());
		leg.setDepartureTime(event.getTime());
		leg.setMode(event.getLegMode());
		TripEventHandler.Trip trip = getCurrentTrip(event.getPersonId());
		trip.legs.add(leg);
	}

	@Override
	public void handleEvent(PersonStuckEvent event) {

		tripToPerson.remove(event.getPersonId());
		stuck.add(event.getPersonId());
	}

	@Override
	public void reset(final int iteration) {
		tripToPerson.clear();
	}

	private TripEventHandler.Trip getCurrentTrip(Id<Person> personId) {

		List<TripEventHandler.Trip> trips = tripToPerson.get(personId);
		return trips.get(trips.size() - 1);
	}

	@Override
	public void handleEvent(LinkLeaveEvent event) {

		var personId = vehicle2Person.get(event.getVehicleId());
		if (personId == null) return;

		var trip = getCurrentTrip(personId);
		trip.route.add(event.getLinkId());
	}

	@Override
	public void handleEvent(PersonEntersVehicleEvent event) {
		if (!tripToPerson.containsKey(event.getPersonId())) return;

		vehicle2Person.put(event.getVehicleId(), event.getPersonId());
	}

	@Override
	public void handleEvent(PersonLeavesVehicleEvent event) {

		vehicle2Person.remove(event.getPersonId());
	}

	@Getter
	@RequiredArgsConstructor
	public static class Trip {

		private static final GeometryFactory gf = new GeometryFactory();

		private final Id<Person> personId;
		private final int tripNumber;
		private Id<Link> departureLink;
		private Id<Link> arrivalLink;
		private final List<Id<Link>> route = new ArrayList<>();
		private double departureTime;
		private double arrivalTime;
		private Id<ActivityFacility> departureFacility;
		private Coord departureCoord;
		private Coord arrivalCoord;
		private Id<ActivityFacility> arrivalFacility;
		private String mainMode = TransportMode.other;

		private final List<Leg> legs = new ArrayList<>();

		private Geometry cachedGeometry;

		public Geometry toGeometry(Network network) {

			if (cachedGeometry != null) return cachedGeometry;

			var size = 2 + this.route.size();
			var coordinates = new Coordinate[size];
			coordinates[0] = MGC.coord2Coordinate(departureCoord);
			coordinates[size - 1] = fromLinkId(arrivalLink, network);

			var index = 1;
			for (var id : route) {
				coordinates[index] = fromLinkId(id, network);
				index++;
			}

			cachedGeometry = gf.createLineString(coordinates);
			return cachedGeometry;
		}

		public String identifier() {
			return personId+"_"+tripNumber;
		}

		static Coordinate fromLinkId(Id<Link> id, Network network) {
			return MGC.coord2Coordinate(network.getLinks().get(id).getToNode().getCoord());
		}
	}
}