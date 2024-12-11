/**
 * Main activity for the map.
 */
package com.example.mapruler;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.FragmentActivity;
import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.MapStyleOptions;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.GeoPoint;
import com.google.maps.android.SphericalUtil;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.IOException;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.List;

public class MapsActivity extends FragmentActivity implements OnMapReadyCallback {

    private GoogleMap mMap;
    private FusedLocationProviderClient fusedLocationClient;
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1;

    private ListView routesListView;
    private TextView stepsTextView;
    private TextView directionsTextView;
    private ArrayList<Route> routesList;
    private ArrayAdapter<Route> routesAdapter;
    private EditText startSearchBar;
    private EditText destSearchBar;
    private EditText buildingSearchBar;

    private Route currRoute;

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);

        // Initialize the ListView and the ArrayList
        routesListView = findViewById(R.id.routesListView);
        routesList = new ArrayList<>();
        stepsTextView = findViewById(R.id.stepsTextView);
        directionsTextView = findViewById(R.id.directionsTextView);

        // Set up the adapter for the ListView
        routesAdapter = new ArrayAdapter<Route>(this, android.R.layout.simple_list_item_1, routesList) {
            @Override
            public View getView(int position, View convertView, android.view.ViewGroup parent) {
                View view = super.getView(position, convertView, parent);
                TextView text = view.findViewById(android.R.id.text1);
                Route route = getItem(position);
                text.setText(route.getName());  // Display route name in the ListView
                return view;
            }
        };

        // Set the adapter to the ListView
        routesListView.setAdapter(routesAdapter);

        // Button to add route
        Button addRouteButton = findViewById(R.id.addRouteButton);
        addRouteButton.setOnClickListener(v -> {

            if (currRoute != null) {
                addRouteToDatabase(currRoute);
            } else {
                // If no active route, show a toast message
                Toast.makeText(MapsActivity.this, "No current route to save", Toast.LENGTH_SHORT).show();
            }
        });

        // Fetch routes from Firebase
        fetchRoutesFromFirebase();

        // Handle click events on list items
        routesListView.setOnItemClickListener((parent, view, position, id) -> {
            Route selectedRoute = routesList.get(position);
            // When a route is clicked, use the selected route's name and address
            applyRoute(selectedRoute);
        });

        // Initialize the FusedLocationProviderClient
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        // Set up the map fragment
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        // Initialize the search bar
        startSearchBar = findViewById(R.id.startLocationEditText);
        destSearchBar = findViewById(R.id.destLocationEditText);


        // Set up the search button functionality
        findViewById(R.id.findDistanceButton).setOnClickListener(v -> {
            String startQuery = startSearchBar.getText().toString().trim().toLowerCase();
            String destQuery = destSearchBar.getText().toString().trim().toLowerCase();
            if (!destQuery.isEmpty()) {
                // Search for the location without adding it to Firebase
                searchRoute(startQuery, destQuery);
            } else {
                // If the search bar is empty, show a toast message
                Toast.makeText(MapsActivity.this, "Please enter a destination to search", Toast.LENGTH_SHORT).show();
            }
        });

        buildingSearchBar = findViewById(R.id.buildingEditText);

        // set up searchbar for building info
        findViewById(R.id.findBuildingButton).setOnClickListener(v -> {
            String query = buildingSearchBar.getText().toString().trim();
            if (!query.isEmpty()) {
                // Search for the location without adding it to Firebase
                fetchLocationData(query);
            } else {
                // If the search bar is empty, show a toast message
                Toast.makeText(MapsActivity.this, "Please enter a valid building name", Toast.LENGTH_SHORT).show();
            }
        });
    }


    @SuppressLint("PotentialBehaviorOverride")
    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;

        // Enable my location layer if permission is granted
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            mMap.setMyLocationEnabled(true);
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    LOCATION_PERMISSION_REQUEST_CODE);
            return;
        }

        // Enable map gestures. Once we zoom in, we still can't zoom out
        mMap.getUiSettings().setZoomGesturesEnabled(true);    // Pinch-to-zoom
        mMap.getUiSettings().setZoomControlsEnabled(true);    // + and - buttons for zoom
        mMap.getUiSettings().setScrollGesturesEnabled(true);  // dragging the map
        mMap.getUiSettings().setTiltGesturesEnabled(true);    // tilting the map
        mMap.getUiSettings().setRotateGesturesEnabled(true);  // rotating the map

        // custom map style from MapRuler lab. Not exactly needed
        mMap.setMapStyle(MapStyleOptions.loadRawResourceStyle(this, R.raw.mapstyle_mine));

        // Move camera to the current location
        panToCurrentLocation();
    }

    // Get last known location or request it if not available
    private void panToCurrentLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        fusedLocationClient.getLastLocation()
                .addOnSuccessListener(this, location -> {
                    if (location != null) {
                        LatLng currentLocation = new LatLng(location.getLatitude(), location.getLongitude());
                        mMap.addMarker(new MarkerOptions().position(currentLocation).title("You are here"));
                        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(currentLocation, 15));
                    } else {
                        Toast.makeText(MapsActivity.this, "Current location is unavailable.", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void fetchRoutesFromFirebase() {
        // need to switch to Firestore reference instead of realtime
        DatabaseReference routesRef = FirebaseDatabase.getInstance().getReference("routes");

        // Retrieve data from Firebase
        routesRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                routesList.clear();  // Clear the previous data
                for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                    try {
                        if(snapshot.child("uid").getValue(String.class).equals(UserData.getInstance().getUid())) {
                            // Add the route to the list
                            DataSnapshot routeObj = snapshot.child("routeObj");

                            ArrayList<LatLng> coordinates = new ArrayList<LatLng>();
                            for (DataSnapshot coordinate : routeObj.child("coordinates").getChildren()){
                                coordinates.add(new LatLng(coordinate.child("latitude").getValue(Double.class), coordinate.child("longitude").getValue(Double.class)));
                            }

                            ArrayList<String> addresses = new ArrayList<String>();
                            for (DataSnapshot address : routeObj.child("addresses").getChildren()){
                                addresses.add(address.getValue(String.class));
                            }

                            boolean fromCurrentLocation = routeObj.child("fromCurrentLocation").getValue(Boolean.class);

                            String name = routeObj.child("name").getValue(String.class);

                            Route route = new Route(name, coordinates, addresses, fromCurrentLocation);

                            routesList.add(route);

                        }
                    } catch (Exception e) {
                        //skip invalid entry
                        Log.d("Fetch Exception: ", e.getMessage());
                    }

                }
                // Notify the adapter that data has been updated
                routesAdapter.notifyDataSetChanged();
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                // Handle errors, if any
                Toast.makeText(MapsActivity.this, "Error fetching routes", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void fetchLocationData(String locationId) {
        //tim and make lowercase
        locationId = locationId.toLowerCase().trim();
        // Get a reference to the Firestore database
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        db.collection("locations")
                .document(locationId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        // Retrieve building name
                        String buildingName = documentSnapshot.getString("name");
                        //buildingNameTextView.setText(buildingName != null ? buildingName : "No building name found");

                        // Retrieve GeoPoint and display latitude and longitude
                        GeoPoint geoPoint = documentSnapshot.getGeoPoint("location");

                        // Retrieve hours array
                        ArrayList<String> businessHours = (ArrayList<String>) documentSnapshot.get("hours");
                        // Create and show the DialogFragment
                         BuildingInfoPopup dialogFragment = BuildingInfoPopup.newInstance(buildingName, businessHours);
                        dialogFragment.show(getSupportFragmentManager(), "LocationDetailsDialog");
                    } else {
                        Toast.makeText(MapsActivity.this, "Location not found", Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(MapsActivity.this, "Error fetching location data", Toast.LENGTH_SHORT).show();
                });
        buildingSearchBar.setText("");
    }

    private void addRouteToDatabase(Route route) {
        //need to switch to firebase reference instead of realtime
        DatabaseReference routesRef = FirebaseDatabase.getInstance().getReference("routes");

        // Create a new entry in the "routes" collection
        DatabaseReference newRouteRef = routesRef.push();
        newRouteRef.child("uid").setValue(UserData.getInstance().getUid());
        newRouteRef.child("routeObj").setValue(route);

        // Show a toast message to confirm the route was added
        Toast.makeText(MapsActivity.this, "Route added to Firebase", Toast.LENGTH_SHORT).show();

        // update the ListView
        routesList.add(route);
        routesAdapter.notifyDataSetChanged();
    }

    private void searchRoute(String startQuery, String destQuery) {
        // Geocode the entered search query (manual address search)
        LatLng startCoord = geocodeAddress(startQuery);
        LatLng destCoord = geocodeAddress(destQuery);

        // Clear the search bars
        startSearchBar.setText("");
        destSearchBar.setText("");

        boolean fromCurrLoc = true;
        if(!startQuery.isEmpty()){
            fromCurrLoc = false;
        }

        ArrayList<LatLng> coords = new ArrayList<LatLng>();
        ArrayList<String> addresses = new ArrayList<String>();
        if(!fromCurrLoc){
            coords.add(new LatLng(startCoord.latitude, startCoord.longitude));
            addresses.add(startQuery);
        }

        coords.add(new LatLng(destCoord.latitude, destCoord.longitude));
        addresses.add(destQuery);
        //TODO: add multi-stop functionality, indexing already implemented

        String name;
        if (fromCurrLoc) {
            name = "Curr Loc to " + destQuery;
        } else {
            name = startQuery + " to " + destQuery;
        }
        Route newRoute = new Route(name, coords, addresses, fromCurrLoc);
        applyRoute(newRoute);
    }

    private LatLng geocodeAddress(String address) {
        // Add Ames, Iowa to the address so that the user doesn't have to
        address += " ames iowa";

        Geocoder geocoder = new Geocoder(this);
        try {
            Log.d("Geocoder", "Geocoding address: " + address);  // Log the address being searched
            List<Address> addresses = geocoder.getFromLocationName(address, 1);  // Only retrieve 1 result

            if (addresses != null && !addresses.isEmpty()) {
                Address location = addresses.get(0);
                double lat = location.getLatitude();
                double lng = location.getLongitude();


                // Log the geocoded results for debugging
                Log.d("Geocoder", "Geocoded location: " + lat + ", " + lng);

                LatLng geocodedLocation = new LatLng(lat, lng);

                return geocodedLocation;

            } else {
                Log.e("Geocoder", "Address not found: " + address);  // Log error if not found
                Toast.makeText(this, "Address not found", Toast.LENGTH_SHORT).show();
            }
        } catch (IOException e) {
            e.printStackTrace();
            Log.e("Geocoder", "Geocoding failed", e);  // Log the exception for debugging
            Toast.makeText(this, "Geocoding failed", Toast.LENGTH_SHORT).show();
        }

        return null;
    }

    private String formatRouteString(Route route){
        String ret = "route?point=";
        ArrayList<LatLng> coords = route.getCoordinates();
        ret = ret + coords.get(0).latitude + "," + coords.get(0).longitude;
        for (int i = 1; i < coords.size(); i++){
            ret = ret + "&point=" + coords.get(i).latitude + "," + coords.get(i).longitude;
        }
        return ret;
    }

    private void applyRoute(Route route) {
        currRoute = route;
        if(route.fromCurrentLocation){
            // Check if permission to access fine location is granted
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED) {
                // If not, request the permission (handle this in your permissions callback)
                return;
            }

            // Get current location using FusedLocationProviderClient
            fusedLocationClient.getLastLocation().addOnSuccessListener(this, location -> {
                if (location != null) {
                    // Get current location coordinates
                    LatLng startLocation = new LatLng(location.getLatitude(), location.getLongitude());

                    // Log the current location for debugging
                    Log.d("Current Location", "Lat: " + startLocation.latitude + ", Lng: " + startLocation.longitude);

                    Route newRoute = route.addCurrentLocation(startLocation);
                    drawRoute(newRoute);

                } else {
                    // Handle the case where current location is null
                    Toast.makeText(MapsActivity.this, "Current location is unavailable", Toast.LENGTH_SHORT).show();
                    Log.e("LocationError", "Current location is null.");
                }
            });
        } else {
            drawRoute(route);
        }
    }

    private void drawRoute(Route route) {
        // Build the GraphHopper API URL to request a walking route
        String url = "https://graphhopper.com/api/1/" + formatRouteString(route) +
                "&type=json&vehicle=foot&key=8d7f64ec-867f-4134-858d-cbc5a09ef9dc";

        // HTTP request to the GraphHopper
        StringRequest stringRequest = new StringRequest(Request.Method.GET, url,
                new Response.Listener<String>() {
                    @SuppressLint("SetTextI18n")
                    @Override
                    public void onResponse(String response) {
                        try {
                            // Parse the JSON response to get the encoded polyline
                            JSONObject jsonResponse = new JSONObject(response);
                            JSONArray paths = jsonResponse.getJSONArray("paths");
                            JSONObject path = paths.getJSONObject(0);
                            String encodedPolyline = path.getString("points");

                            // Decode the polyline into LatLng points using PolylineUtil.decodePolyline
                            List<LatLng> decodedPath = PolylineUtil.decodePolyline(encodedPolyline);
                            Log.d("Polyline", "Decoded path size: " + decodedPath.size());

                            // Create PolylineOptions and add the decoded path
                            PolylineOptions polylineOptions = new PolylineOptions()
                                    .addAll(decodedPath)  // Add decoded LatLng points to polyline
                                    .width(5)
                                    .color(getResources().getColor(R.color.colorPrimary));

                            // Add the polyline to the map
                            mMap.clear();  // Clear previous route
                            mMap.addPolyline(polylineOptions);

                            //  zoom the map to fit the polyline
                            LatLngBounds.Builder builder = new LatLngBounds.Builder();
                            for(LatLng latLng : route.getCoordinates()){
                                builder.include(latLng);
                            }

                            // add markers
                            ArrayList<LatLng> coords = route.getCoordinates();
                            ArrayList<String> addresses = route.getAddresses();
                            for (int i = 0; i < route.getCoordinates().size(); i++) {
                                mMap.addMarker(new MarkerOptions().position(coords.get(i)).title(addresses.get(i)));
                            }

                            // TODO: add loop to calculate actual distance of polyline
                            //calculate the distance in meters, steps, and ETA
                            double distance = SphericalUtil.computeDistanceBetween(route.getCoordinates().get(0), route.getCoordinates().get(route.getCoordinates().size() - 1));
                            @SuppressLint("DefaultLocale") String distanceFormatted = String.format("%.2f",distance);
                            stepsTextView.setText("distance: " + distanceFormatted +
                                    " meters\n" + "steps: " + (int)(distance / .762)  +
                                    "\nMinutes: " + Math.round(distance / 0.95 /60));

                            // Build LatLngBounds and move the camera to fit the route
                            LatLngBounds bounds = builder.build();
                            mMap.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, 250));

                            //turn-by-turn directions
                            JSONArray instructionsArray = path.getJSONArray("instructions");
                            Log.d("Instructions", instructionsArray.toString());
                            StringBuilder directions = new StringBuilder();

                            for (int i = 0; i < instructionsArray.length(); i++) {
                                JSONObject instruction = instructionsArray.getJSONObject(i);
                                String text = instruction.getString("text");
                                double distanceToNext = instruction.getDouble("distance");

                                String distanceToNextFormatted = text + " in " + Math.round(distanceToNext) + " m";
                                directions.append(i + 1).append(". ").append(distanceToNextFormatted).append("\n");
                            }

                            directionsTextView.setText(directions.toString());
                            Log.d("Directions", directions.toString());

                        } catch (Exception e) {
                            e.printStackTrace();
                            Log.e("RouteError", "Error parsing route response: " + e.getMessage());
                        }
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        error.printStackTrace();
                        Log.e("VolleyError", "Error fetching route: " + error.getMessage());
                    }
                });
        // Add the request to the Volley RequestQueue
        Volley.newRequestQueue(MapsActivity.this).add(stringRequest);
    }
}