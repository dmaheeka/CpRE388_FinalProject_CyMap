package com.example.mapruler;

import android.location.Location;

import com.google.android.gms.maps.model.LatLng;

import java.util.ArrayList;

public class Route {

    public String name;
    public ArrayList<LatLng> coordinates;
    public ArrayList<String> addresses;
    public boolean fromCurrentLocation;

    // Constructor
    public Route(String name, ArrayList<LatLng> coordinates, ArrayList<String> addresses, boolean fromCurrentLocation) {
        this.name = name;

        // Deep copy coordinates and addresses
        this.coordinates = new ArrayList<LatLng>();
        this.coordinates.addAll(coordinates);
        this.addresses = new ArrayList<String>();
        this.addresses.addAll(addresses);

        this.fromCurrentLocation = fromCurrentLocation;
    }

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

    // Getter methods
    public String getName() {
        return name;
    }

    public ArrayList<LatLng> getCoordinates(){
        return coordinates;
    }

    public ArrayList<String> getAddresses() {
        return addresses;
    }

    // Override toString() to display the name of the route
    @Override
    public String toString() {
        return name;  // This will display the route name in the ListView
    }

}
