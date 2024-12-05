package com.example.mapruler;
public class Route {

    private RouteType routeType;
    private String name;
    private String address;
    private double latitude;
    private double longitude;

    // Constructor
    public Route(RouteType routeType, String name, String address) {
        this.routeType = routeType;
        this.name = name;
        this.latitude = latitude;
        this.longitude = longitude;
        this.address = address;
    }

    // Getter methods
    public String getName() {
        return name;
    }

    public double getLatitude() {
        return latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    // Override toString() to display the name of the route
    @Override
    public String toString() {
        return name;  // This will display the route name in the ListView
    }

    public String getAddress() {
        return address;
    }


}
