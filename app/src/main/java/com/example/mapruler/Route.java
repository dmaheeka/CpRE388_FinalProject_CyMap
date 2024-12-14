package com.example.mapruler;

import com.google.android.gms.maps.model.LatLng;
import java.util.ArrayList;

/**
 * Data object used to store all of the information for a route.  Contains all of the destinations
 * and stops.  Able to be directly stored in firebase.
 */
public class Route {

    public String name;
    public ArrayList<LatLng> coordinates;
    public ArrayList<String> addresses;
    public boolean fromCurrentLocation;

    /**
     * Public constructor for the Route object, creates new, fully filled out
     *
     * @param name visible name for the route, will show in the list view
     * @param coordinates ArrayList of LatLng points that the route will go through
     * @param addresses ArrayList of the addresses/names associated with the coordinates
     * @param fromCurrentLocation whether the current route starts at a given location or current
     *                            location.
     */
    public Route(String name, ArrayList<LatLng> coordinates, ArrayList<String> addresses, boolean fromCurrentLocation) {
        this.name = name;

        // Deep copy coordinates and addresses
        this.coordinates = new ArrayList<LatLng>();
        this.coordinates.addAll(coordinates);
        this.addresses = new ArrayList<String>();
        this.addresses.addAll(addresses);

        this.fromCurrentLocation = fromCurrentLocation;
    }

    /**
     * Method used to create a new route object by prepending the given current location to the
     * beginning of a route that starts at current location.  Will do nothing if the route is
     * already starting from a given location.
     *
     * @param currLatLon LatLng object of the current location of the user
     * @return new Route object with currLatLon prepended.
     */
    public Route addCurrentLocation(LatLng currLatLon){
        if (!this.fromCurrentLocation){
            return this;
        }
        ArrayList<LatLng> coordinates = new ArrayList<LatLng>();
        coordinates.add(new LatLng(currLatLon.latitude, currLatLon.longitude));
        coordinates.addAll(this.coordinates);

        ArrayList<String> addresses = new ArrayList<String>();
        addresses.add("Starting Location");
        addresses.addAll(this.addresses);

        return new Route(this.name, coordinates, addresses, false);
    }

    /**
     * returns the public facing name of the route, e.g "coover hall to parks library"
     *
     * @return name of the route
     */
    public String getName() {
        return name;
    }

    /**
     * returns a reference to the ArrayList of the coordinates of the start/stops/destination of the
     * route.
     *
     * @return ArrayList of route coordinates.
     */
    public ArrayList<LatLng> getCoordinates(){
        return coordinates;
    }

    /**
     * returns a reference of the ArrayList of the addresses/names of the start/stops/destination of
     * the route.
     *
     * @return ArrayList of route location names.
     */
    public ArrayList<String> getAddresses() {
        return addresses;
    }
}
