package com.example.mapruler;

import com.google.firebase.firestore.GeoPoint;

import java.util.List;

public class MultiRoute {
    private String routeId;
    private GeoPoint startLocation;
    private List<GeoPoint> stops;
    private GeoPoint destination;
    private double totalDistance;
    private String routeName;

    public MultiRoute(String routeId, GeoPoint startLocation, List<GeoPoint> stops, GeoPoint destination, double totalDistance, String routeName) {
        this.routeId = routeId;
        this.startLocation = startLocation;
        this.stops = stops;
        this.destination = destination;
        this.totalDistance = totalDistance;
        this.routeName = routeName;
    }

    // Getters and Setters
    public String getRouteId() {
        return routeId;
    }

    public void setRouteId(String routeId) {
        this.routeId = routeId;
    }

    public GeoPoint getStartLocation() {
        return startLocation;
    }

    public void setStartLocation(GeoPoint startLocation) {
        this.startLocation = startLocation;
    }

    public List<GeoPoint> getStops() {
        return stops;
    }

    public void setStops(List<GeoPoint> stops) {
        this.stops = stops;
    }

    public GeoPoint getDestination() {
        return destination;
    }

    public void setDestination(GeoPoint destination) {
        this.destination = destination;
    }

    public double getTotalDistance() {
        return totalDistance;
    }

    public void setTotalDistance(double totalDistance) {
        this.totalDistance = totalDistance;
    }

    public String getRouteName() {
        return routeName;
    }

    public void setRouteName(String routeName) {
        this.routeName = routeName;
    }
}

