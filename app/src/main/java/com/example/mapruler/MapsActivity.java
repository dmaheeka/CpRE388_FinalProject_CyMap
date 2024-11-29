package com.example.mapruler;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.location.Address;
import android.location.Geocoder;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
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
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
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
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MapsActivity extends FragmentActivity implements OnMapReadyCallback {

    private GoogleMap mMap;
    private FusedLocationProviderClient fusedLocationClient;
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1;

    private ListView routesListView;
    private TextView buildingNameTextView;
    private TextView buildingInfoTextView;
    private TextView stepsTextView;
    private ArrayList<Route> routesList;
    private ArrayAdapter<Route> routesAdapter;
    private EditText searchBar, startEditText, multiStopEditText;// Search bar for manual location search

    private Button findRouteButton, toDestinationButton, fromStartToDestinationButton, multiStopRouteButton;

    private LinearLayout inputLayout;

    private boolean isToDestinationMode = true;
    private boolean isMultiStopMode = false;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);

        // Initialize the ListView and the ArrayList
        startEditText = findViewById(R.id.startLocationEditText);
        routesListView = findViewById(R.id.routesListView);
        routesList = new ArrayList<>();
        buildingNameTextView = findViewById(R.id.buildingNameTextView);
        buildingInfoTextView = findViewById(R.id.buildingInfoTextView);
        toDestinationButton = findViewById(R.id.toDestinationButton);
        fromStartToDestinationButton = findViewById(R.id.fromStartToDestinationButton);
        multiStopRouteButton = findViewById(R.id.multiStopRouteButton);
        inputLayout = findViewById(R.id.inputLayout);
        // Initialize the search bar
        multiStopEditText = findViewById(R.id.multiStopEditText);
        searchBar = findViewById(R.id.locationEditText); // Make sure this ID matches the XML // destination text
        stepsTextView = findViewById(R.id.stepsTextView);
        Button findDistanceButton = findViewById(R.id.findDistanceButton);

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
//        Button addRouteButton = findViewById(R.id.addRouteButton);
//        addRouteButton.setOnClickListener(v -> {
//            // Capture the address from the search bar
//            String query = searchBar.getText().toString().trim();
//
//            if (!query.isEmpty()) {
//                // If search bar is not empty, geocode the address and add the route
//                geocodeAndAddRoute(query, true);
//            } else {
//                // If the search bar is empty, show a toast message
//                Toast.makeText(MapsActivity.this, "Please enter an address", Toast.LENGTH_SHORT).show();
//            }
//        });

        // Fetch routes from Firebase
        fetchRoutesFromFirebase();

        // Handle click events on list items
        routesListView.setOnItemClickListener((parent, view, position, id) -> {
            Route selectedRoute = routesList.get(position);
            // When a route is clicked, use the selected route's name and address
            fetchRouteDataFromFirebase(selectedRoute);
        });

        // Initialize the FusedLocationProviderClient
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        // Set up the map fragment
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        // Set mode selection listeners
        toDestinationButton.setOnClickListener(v -> {
            isToDestinationMode = true;
            isMultiStopMode = false;
            //inputLayout.setVisibility(View.VISIBLE);
            searchBar.setVisibility(View.VISIBLE);
            findDistanceButton.setVisibility(View.VISIBLE);
            multiStopEditText.setVisibility(View.GONE);
            startEditText.setVisibility(View.GONE); // Hide start location input
        });

        fromStartToDestinationButton.setOnClickListener(v -> {
            isToDestinationMode = false;
            isMultiStopMode = false;
            searchBar.setVisibility(View.VISIBLE);
            findDistanceButton.setVisibility(View.VISIBLE);
            multiStopEditText.setVisibility(View.GONE);
            //inputLayout.setVisibility(View.VISIBLE);
            startEditText.setVisibility(View.VISIBLE); // Show start location input
            // Optionally, set a hint or update text to make it clearer
            searchBar.setHint("Enter Destination Location");
            startEditText.setHint("Enter Start Location");
        });

        multiStopRouteButton.setOnClickListener(v -> {
            isToDestinationMode = false;
            isMultiStopMode = true;
            searchBar.setVisibility(View.VISIBLE);
            findDistanceButton.setVisibility(View.VISIBLE);
            multiStopEditText.setVisibility(View.VISIBLE);

            //inputLayout.setVisibility(View.VISIBLE);
            startEditText.setVisibility(View.VISIBLE); // Show start location input
            // Optionally, set a hint or update text to make it clearer
            searchBar.setHint("Enter Destination Location");
            multiStopEditText.setHint("Enter MultiStops");
            startEditText.setHint("Enter Start Location");
        });


        // Find Route Button Listener
        findDistanceButton.setOnClickListener(v -> {
            if (isToDestinationMode) {
                String destination = searchBar.getText().toString();
                if (!destination.isEmpty()) {
                    geocodeAddress(destination); // For To Destination mode
                    searchLocation(destination);
                }
            } else if(isMultiStopMode){
                String startLocation = startEditText.getText().toString().trim();
                String destinationLocation = searchBar.getText().toString().trim();
                String multiStopsInput = multiStopEditText.getText().toString().trim();

                if (startLocation.isEmpty() || destinationLocation.isEmpty()) {
                    Toast.makeText(MapsActivity.this, "Start and destination are required", Toast.LENGTH_SHORT).show();
                    return;
                }
                // Split multiple stops into a list (comma-separated values)
                List<String> stopLocations = new ArrayList<>();
                if (!multiStopsInput.isEmpty()) {
                    stopLocations = Arrays.asList(multiStopsInput.split(","));
                }

                // Convert user input to LatLng using geocodeAddress
                LatLng startLatLng = geocodeAddress2(startLocation);
                LatLng destinationLatLng = geocodeAddress2(destinationLocation);
                List<LatLng> stopLatLngs = new ArrayList<>();

                // Convert stop locations to LatLng using geocodeAddress
                for (String stop : stopLocations) {
                    stopLatLngs.add(geocodeAddress2(stop.trim()));
                }

                // Call the function to draw the multi-stop route
                drawMultiStopRouteAndSaveToFirebase(stopLatLngs, startLatLng, destinationLatLng, "User Route");
            } else {
                String start = startEditText.getText().toString();
                String destination = searchBar.getText().toString();
                if (!start.isEmpty() && !destination.isEmpty()) {
                    geocodeAndDrawRoute(start, destination); // Geocode start first
                    searchStartAndDestination(start, destination);
                }
            }
        });

//        // Set up the search button functionality
//        findViewById(R.id.findDistanceButton).setOnClickListener(v -> {
//            String query = searchBar.getText().toString().trim();
//            if (!query.isEmpty()) {
//                // Search for the location without adding it to Firebase
//                searchLocation(query);
//            } else {
//                // If the search bar is empty, show a toast message
//                Toast.makeText(MapsActivity.this, "Please enter an address to search", Toast.LENGTH_SHORT).show();
//            }
//        });

        //click listeners for markers
     //   mMap.setOnMarkerClickListener(marker -> {
            // Check if this marker is the invisible one (you can use the tag to identify it)
       //     if ("clickable_marker".equals(marker.getTag())) {
                // Handle click on the invisible marker (show building info, etc.)
         //       Toast.makeText(MapsActivity.this, "Invisible marker clicked!", Toast.LENGTH_SHORT).show();
                // You can display building info in the TextViews or perform other actions here
           //     fetchLocationData(marker.getTitle()); // Assuming the marker title is the building name or address
             //   return true;  // Return true to indicate the click was handled
            //}
            //return false; // If it's not the invisible marker, return false to let the default behavior happen
        //});

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

        // Enable map gestures individually for more control
        mMap.getUiSettings().setZoomGesturesEnabled(true);    // Pinch-to-zoom
        mMap.getUiSettings().setZoomControlsEnabled(true);    // + and - buttons for zoom
        mMap.getUiSettings().setScrollGesturesEnabled(true);  // Allow dragging the map
        mMap.getUiSettings().setTiltGesturesEnabled(true);    // Allow tilting the map
        mMap.getUiSettings().setRotateGesturesEnabled(true);  // Allow rotating the map

        // Load the custom map style
        mMap.setMapStyle(MapStyleOptions.loadRawResourceStyle(this, R.raw.mapstyle_mine));

        // Move camera to the current location
        getLastKnownLocation();
    }


//    private void geocodeAndAddRoute(String address, boolean isDestination) {
//        Geocoder geocoder = new Geocoder(this);
//        try {
//            // Geocode the address entered by the user (only fetch 1 result)
//            List<Address> addresses = geocoder.getFromLocationName(address, 1);
//
//            if (addresses != null && !addresses.isEmpty()) {
//                android.location.Address location = addresses.get(0);
//                double lat = location.getLatitude();
//                double lng = location.getLongitude();
//
//                // Plot markers for start and destination locations
//                LatLng startLatLng = new LatLng(startLat, startLng);
//                LatLng destinationLatLng = new LatLng(destLat, destLng);
//
//
//                // Now add this route to Firebase
//                addRouteToDatabase(address, address, lat, lng);  // Using the same address for name and address fields
//                if (isToDestinationMode && isDestination) {
//                    drawRouteFromCurrentLocation(lat, lng);
//                } else if (!isToDestinationMode) {
//                    if (isDestination) {
//                        drawRoute(lat, lng, lat, lng);
//                    } else {
//                        startEditText.setTag(latLng); // Temporarily store start location
//                        geocodeAddress(searchBar.getText().toString());
//                    }
//                }
//
//            } else {
//                Toast.makeText(this, "Address not found", Toast.LENGTH_SHORT).show();
//            }
//        } catch (IOException e) {
//            e.printStackTrace();
//            Toast.makeText(this, "Geocoding failed", Toast.LENGTH_SHORT).show();
//        }
//    }

    private void addInvisibleMarkerToMap(double latitude, double longitude) {
        LatLng position = new LatLng(latitude, longitude);
        Bitmap coloredBitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
        coloredBitmap.eraseColor(getResources().getColor(R.color.teal_200));

        BitmapDescriptor coloredIcon = BitmapDescriptorFactory.fromBitmap(coloredBitmap);
        // Create a transparent marker by setting an invisible icon
        mMap.addMarker(new MarkerOptions()
                        .position(position)
                        .icon(coloredIcon)  // Invisible marker
                        .title(buildingNameTextView.getText().toString()))
                .setTag("clickable_marker");  // You can set a tag to identify markers if needed
    }


    // Get last known location or request it if not available
    private void getLastKnownLocation() {
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
        // Reference to the "routes" node in the Firebase Realtime Database
        DatabaseReference routesRef = FirebaseDatabase.getInstance().getReference("routes");

        // Retrieve data from Firebase
        routesRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                routesList.clear();  // Clear the previous data
                for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                    String name = snapshot.child("name").getValue(String.class);
                    String address = snapshot.child("address").getValue(String.class);
                   // double latitude = snapshot.child("latitude").getValue(Double.class);
                   // double longitude = snapshot.child("longitude").getValue(Double.class);

                   // addInvisibleMarkerToMap(latitude, longitude);

                    // Add the route to the list
                    routesList.add(new Route(name, address));
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
    private void fetchLocationData(String location) {
        // Reference to the "locations" node in Firebase
        DatabaseReference locationsRef = FirebaseDatabase.getInstance().getReference("locations");

        locationsRef.child(location).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                if (dataSnapshot.exists()) {
                    // Retrieve the building name and info
                    String buildingName = dataSnapshot.child("building_name").getValue(String.class);
                    String buildingInfo = dataSnapshot.child("building_info").getValue(String.class);

                    // Log the retrieved values to check
                    Log.d("Firebase", "Building Name: " + buildingName);
                    Log.d("Firebase", "Building Info: " + buildingInfo);

                    // Set the retrieved data to the TextViews
                    buildingNameTextView.setText(buildingName != null ? buildingName : "No building name found");
                    buildingInfoTextView.setText(buildingInfo != null ? buildingInfo : "No building info found");

                    // Optionally, handle geocoding here if needed
                    double latitude = dataSnapshot.child("latitude").getValue(Double.class);
                    double longitude = dataSnapshot.child("longitude").getValue(Double.class);

                    // Create a LatLng object for the coordinates
                    LatLng buildingLocation = new LatLng(latitude, longitude);
                   // addInvisibleMarkerToMap(latitude, longitude);

                    // Log the coordinates (for debugging)
                    Log.d("Firebase", "Latitude: " + latitude + ", Longitude: " + longitude);

                } else {
                    // If no data found for the location
                    Log.d("Firebase", "No data found for the location: " + location);
                    buildingNameTextView.setText("Building not found");
                    buildingInfoTextView.setText("No additional info available");
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                // Handle errors, if any
                Toast.makeText(MapsActivity.this, "Error fetching building data", Toast.LENGTH_SHORT).show();
            }
        });
    }



    private void fetchRouteDataFromFirebase(Route route) {
        // Combine name and address to form a full address
        String address = route.getName() + " " + route.getAddress();
        geocodeAddress(address);// Call geocodeAddress() to get coordinates
        fetchLocationData(route.getName());

    }
    private void addRouteToDatabase(String name, String address, double latitude, double longitude) {
        // Reference to the "routes" node in Firebase
        DatabaseReference routesRef = FirebaseDatabase.getInstance().getReference("routes");

        // Create a new entry in the "routes" collection
        DatabaseReference newRouteRef = routesRef.push();
        newRouteRef.child("name").setValue(name);
        newRouteRef.child("address").setValue(address);
        newRouteRef.child("latitude").setValue(latitude);
        newRouteRef.child("longitude").setValue(longitude);

        // Show a toast message to confirm the route was added
        Toast.makeText(MapsActivity.this, "Route added to Firebase", Toast.LENGTH_SHORT).show();

        // update the ListView : this currently crashes the app
        //routesList.add(new Route(name, address));
    }

    private void searchLocation(String query) {
        // Geocode the entered search query (manual address search)
        geocodeAddress(query);
        buildingNameTextView.setText(query);
        //set the building info from the database
        fetchLocationData(query);
        // buildingInfoTextView.setText("No additional info available");
        // Clear the search bar
        searchBar.setText("");

    }

    private void searchStartAndDestination(String startQuery, String destinationQuery) {
        // Geocode the start location
        geocodeAddress(startQuery); // Geocodes and potentially adds a marker for the start location
        buildingNameTextView.setText("Start: " + startQuery); // Update building name for start location
        fetchLocationData(startQuery); // Fetch additional info for the start location

        // Geocode the destination location
        geocodeAddress(destinationQuery); // Geocodes and potentially adds a marker for the destination location
        buildingNameTextView.append("\nDestination: " + destinationQuery); // Update building name for destination location
        fetchLocationData(destinationQuery); // Fetch additional info for the destination location

        // Optionally, draw a route between the start and destination
        geocodeAndDrawRoute(startQuery, destinationQuery);

        // Clear the input fields for both start and destination
        startEditText.setText("");
        searchBar.setText("");
    }

    private void geocodeAddress(String address) {
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

                // Now you have the latitude and longitude for the address
                LatLng geocodedLocation = new LatLng(lat, lng);
                // Add the marker for the geocoded location
                mMap.clear();  // Clear previous markers


                        mMap.addMarker(new MarkerOptions().position(geocodedLocation).title(address));

                        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(geocodedLocation, 15));

                // Optionally, you can call drawRouteFromCurrentLocation() here if needed
                drawRouteFromCurrentLocation(lat, lng);

                // Save the route to Firebase
                addRouteToDatabase(address, address, lat, lng);
            } else {
                Log.e("Geocoder", "Address not found: " + address);  // Log error if not found
                Toast.makeText(this, "Address not found", Toast.LENGTH_SHORT).show();
            }
        } catch (IOException e) {
            e.printStackTrace();
            Log.e("Geocoder", "Geocoding failed", e);  // Log the exception for debugging
            Toast.makeText(this, "Geocoding failed", Toast.LENGTH_SHORT).show();
        }
    }

    private void drawRouteFromCurrentLocation(double destinationLat, double destinationLon) {
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
                LatLng currentLocation = new LatLng(location.getLatitude(), location.getLongitude());

                // Log the current location for debugging
                Log.d("Current Location", "Lat: " + currentLocation.latitude + ", Lng: " + currentLocation.longitude);

                // Add a marker on the current location (default red marker)
                mMap.addMarker(new MarkerOptions()
                        .position(currentLocation)
                        .title("Your Current Location"));


                // Move the camera to the current location
                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(currentLocation, 15));  // Zoom to current location

                // Build the GraphHopper API URL to request a walking route
                String url = "https://graphhopper.com/api/1/route?point=" + location.getLatitude() + "," + location.getLongitude() +
                        "&point=" + destinationLat + "," + destinationLon +
                        "&type=json&vehicle=foot&key=8d7f64ec-867f-4134-858d-cbc5a09ef9dc";// Replace with your GraphHopper API Key

                LatLng newLocation = new LatLng(destinationLat, destinationLon);

                // Make HTTP request to the GraphHopper Directions API using Volley
                StringRequest stringRequest = new StringRequest(Request.Method.GET, url,
                        new Response.Listener<String>() {
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
                                    // totalNumSteps = decodedPath.size() ;
                                    // stepsTextView.setText("steps: " + totalNumSteps);

                                    // Create PolylineOptions and add the decoded path
                                    PolylineOptions polylineOptions = new PolylineOptions()
                                            .addAll(decodedPath)  // Add decoded LatLng points to polyline
                                            .width(5)
                                            .color(getResources().getColor(R.color.colorPrimary));// Customize polyline color


                                    // Add the polyline to the map
                                    mMap.addPolyline(polylineOptions);



                                    // Optionally, zoom the map to fit the polyline
                                    LatLngBounds.Builder builder = new LatLngBounds.Builder();
                                    builder.include(currentLocation);  // Include current location
                                    builder.include(new LatLng(destinationLat, destinationLon));  // Include destination

                                    // Include all points in the polyline
                                    for (LatLng latLng : decodedPath) {
                                        builder.include(latLng);
                                    }
                                    double distance = SphericalUtil.computeDistanceBetween(currentLocation, newLocation);
                                    String distanceFormatted = String.format("%.2f",distance);
                                    stepsTextView.setText("distance: " + distanceFormatted + " meters\n" + "steps: " + (int)(distance / .762)+"\n"+ "Minutes: " + Math.round(distance / 0.95 /60));
                                    //stepsTextView.setText("distance: " + distanceFormatted + " meters\n" + "steps: " + (int)(distance / .762));

                                    // Build LatLngBounds and move the camera to fit the route
                                    LatLngBounds bounds = builder.build();
                                    mMap.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, 250));


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
            } else {
                // Handle the case where current location is null
                Toast.makeText(MapsActivity.this, "Current location is unavailable", Toast.LENGTH_SHORT).show();
                Log.e("LocationError", "Current location is null.");
            }
        });
    }















    private void geocodeAndDrawRoute(String startAddress, String destinationAddress) {
        Geocoder geocoder = new Geocoder(this);
        try {
            List<Address> startAddresses = geocoder.getFromLocationName(startAddress, 1);
            List<Address> destinationAddresses = geocoder.getFromLocationName(destinationAddress, 1);

            if (startAddresses != null && !startAddresses.isEmpty() && destinationAddresses != null && !destinationAddresses.isEmpty()) {
                Address startLocation = startAddresses.get(0);
                Address destinationLocation = destinationAddresses.get(0);

                double startLat = startLocation.getLatitude();
                double startLng = startLocation.getLongitude();
                double destLat = destinationLocation.getLatitude();
                double destLng = destinationLocation.getLongitude();

                // Plot markers for start and destination locations
                LatLng startLatLng = new LatLng(startLat, startLng);
                LatLng destinationLatLng = new LatLng(destLat, destLng);

                mMap.clear(); // Clear previous markers
                mMap.addMarker(new MarkerOptions().position(startLatLng).title("Start: " + startAddress));
                mMap.addMarker(new MarkerOptions().position(destinationLatLng).title("Destination: " + destinationAddress));
                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(startLatLng, 15));

                // Draw route between start and destination
                drawRoute(startLat, startLng, destLat, destLng);

                // Add the route to Firebase
                addRouteToDatabase("Route from " + startAddress + " to " + destinationAddress, destinationAddress, destLat, destLng);

            } else {
                Toast.makeText(this, "Address not found", Toast.LENGTH_SHORT).show();
            }
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "Geocoding failed", Toast.LENGTH_SHORT).show();
        }
    }


    private void drawRoute(double startLat, double startLng, double destLat, double destLng) {

        // Add markers for start and destination locations
        LatLng startLocation = new LatLng(startLat, startLng);
        LatLng destinationLocation = new LatLng(destLat, destLng);

        // Add a marker at the start location
        mMap.addMarker(new MarkerOptions()
                .position(startLocation)
                .title("Start Location")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE))); // Optional: Custom color for the marker

        // Add a marker at the destination
        mMap.addMarker(new MarkerOptions()
                .position(destinationLocation)
                .title("Destination Location")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))); // Optional: Custom color for the marker

        // Zoom to include both markers in the view
        LatLngBounds.Builder boundsBuilder = new LatLngBounds.Builder();
        boundsBuilder.include(startLocation);
        boundsBuilder.include(destinationLocation);
        LatLngBounds bounds = boundsBuilder.build();

        int padding = 100; // Padding around the markers
        mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, padding));


        // Build the GraphHopper API URL to request a walking route between the start and destination points
        String url = "https://graphhopper.com/api/1/route?point=" + startLat + "," + startLng +
                "&point=" + destLat + "," + destLng +
                "&type=json&vehicle=foot&key=YOUR_GRAPH_HOPPER_API_KEY"; // Replace with your GraphHopper API Key

        // Make HTTP request to the GraphHopper Directions API using Volley
        StringRequest stringRequest = new StringRequest(Request.Method.GET, url,
                new Response.Listener<String>() {
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

                            // Create PolylineOptions and add the decoded path
                            PolylineOptions polylineOptions = new PolylineOptions()
                                    .addAll(decodedPath)  // Add decoded LatLng points to polyline
                                    .width(5)
                                    .color(getResources().getColor(R.color.colorPrimary)); // Customize polyline color

                            // Add the polyline to the map
                            mMap.addPolyline(polylineOptions);

                            // Optionally, zoom the map to fit the polyline
                            LatLngBounds.Builder builder = new LatLngBounds.Builder();
                            builder.include(new LatLng(startLat, startLng));  // Include start location
                            builder.include(new LatLng(destLat, destLng));  // Include destination location

                            // Include all points in the polyline
                            for (LatLng latLng : decodedPath) {
                                builder.include(latLng);
                            }


                            // Calculate the distance and steps (assumes 0.762 meters per step)
                            double distance = SphericalUtil.computeDistanceBetween(startLocation, destinationLocation);
                            String distanceFormatted = String.format("%.2f", distance);
                          //  stepsTextView.setText("Distance: " + distanceFormatted + " meters\n" +
                             //       "Steps: " + (int)(distance / .762)); // Approximate steps

                            stepsTextView.setText("distance: " + distanceFormatted + " meters\n" + "steps: " + (int)(distance / .762)+"\n"+ "Minutes: " + Math.round(distance / 0.95 /60));
                            // Build LatLngBounds and move the camera to fit the route
                            LatLngBounds bounds = builder.build();
                            mMap.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, 250));

                        } catch (Exception e) {
                            e.printStackTrace();
                            Toast.makeText(MapsActivity.this, "Error fetching route", Toast.LENGTH_SHORT).show();
                        }
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        error.printStackTrace();
                        Toast.makeText(MapsActivity.this, "Error fetching route", Toast.LENGTH_SHORT).show();
                    }
                });

        // Add the request to the Volley RequestQueue
        Volley.newRequestQueue(MapsActivity.this).add(stringRequest);
    }

    // Method to draw a multi-stop route and save it to Firebase
    private void drawMultiStopRouteAndSaveToFirebase(List<LatLng> stops, LatLng start, LatLng destination, String routeName) {
        // Initialize the map (ensure you have a GoogleMap object initialized)
        mMap.clear();  // Clear any existing routes

        // Add a marker at the start location
        mMap.addMarker(new MarkerOptions().position(start).title("Start: " + routeName));

        // Add a marker at the destination
        mMap.addMarker(new MarkerOptions().position(destination).title("Destination: " + routeName));

        // Create a PolylineOptions object for the route
        PolylineOptions polylineOptions = new PolylineOptions()
                .width(5)
                .color(Color.BLUE)
                .add(start);  // Start with the first point (start location)

        // Add each stop to the polyline
        for (LatLng stop : stops) {
            polylineOptions.add(stop);  // Add each stop point to the polyline
        }

        // Add the destination point to the polyline
        polylineOptions.add(destination);

        // Draw the polyline on the map
        mMap.addPolyline(polylineOptions);

        // Move the camera to fit the entire route (including stops)
        LatLngBounds.Builder builder = new LatLngBounds.Builder();
        builder.include(start);  // Include the start location
        builder.include(destination);  // Include the destination location

        // Include all stops
        for (LatLng stop : stops) {
            builder.include(stop);
        }
        LatLngBounds bounds = builder.build();
        mMap.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, 100));

        // Calculate the total distance (optional, depends on your use case)
        double totalDistance = 0.0;
        totalDistance += SphericalUtil.computeDistanceBetween(start, stops.get(0));
        for (int i = 0; i < stops.size() - 1; i++) {
            totalDistance += SphericalUtil.computeDistanceBetween(stops.get(i), stops.get(i + 1));
        }
        totalDistance += SphericalUtil.computeDistanceBetween(stops.get(stops.size() - 1), destination);

        // Update steps and building information
        updateStepsAndBuildingInfo(start, stops, destination, totalDistance);

        // Save the route to Firebase
        saveRouteToFirebase(stops, start, destination, totalDistance, routeName);
    }

    // Method to save the multi-stop route to Firebase
    private void saveRouteToFirebase(List<LatLng> stops, LatLng start, LatLng destination, double totalDistance, String routeName) {
        // Generate a unique route ID (can use Firebase push ID)
        String routeId = FirebaseFirestore.getInstance().collection("routes").document().getId();

        // Convert LatLng to GeoPoint (Firebase Firestore uses GeoPoint)
        GeoPoint startGeoPoint = new GeoPoint(start.latitude, start.longitude);
        GeoPoint destinationGeoPoint = new GeoPoint(destination.latitude, destination.longitude);

        List<GeoPoint> stopsGeoPoints = new ArrayList<>();
        for (LatLng stop : stops) {
            stopsGeoPoints.add(new GeoPoint(stop.latitude, stop.longitude));
        }

        // Create a Route object to save to Firebase
        MultiRoute route = new MultiRoute(routeId, startGeoPoint, stopsGeoPoints, destinationGeoPoint, totalDistance, routeName);

        // Save the route to Firebase Firestore
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        db.collection("routes")
                .document(routeId)
                .set(route)
                .addOnSuccessListener(aVoid -> {
                    // Show success message
                    Toast.makeText(MapsActivity.this, "Route saved successfully!", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    // Show error message
                    Toast.makeText(MapsActivity.this, "Failed to save route: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    // Method to update steps and building info text
    private void updateStepsAndBuildingInfo(LatLng start, List<LatLng> stops, LatLng destination, double totalDistance) {
        // Update the steps TextView with the calculated distance and estimated steps

        String formattedDistance = String.format("%.2f", totalDistance);  // Format the total distance
        int totalSteps = (int) (totalDistance / 0.762);  // Assuming average step length of 0.762 meters
        stepsTextView.setText("distance: " + formattedDistance + " meters\n" + "steps: " + totalSteps+"\n"+ "Minutes: " + Math.round(totalDistance / 0.95 /60));
      //  stepsTextView.setText("Distance: " + formattedDistance + " meters\nSteps: " + totalSteps);

        // Update building info (this can be more specific depending on your data, for now it's a placeholder)
        String buildingInfo = "Building Information: \n";
        buildingInfo += "Start Location: " + start.latitude + ", " + start.longitude + "\n";
        buildingInfo += "Destination Location: " + destination.latitude + ", " + destination.longitude + "\n";

        // Adding intermediate stop info (optional)
        for (int i = 0; i < stops.size(); i++) {
            buildingInfo += "Stop " + (i + 1) + ": " + stops.get(i).latitude + ", " + stops.get(i).longitude + "\n";
        }

        buildingInfoTextView.setText(buildingInfo);  // Set the building info text
    }

    private LatLng geocodeAddress2(String address) {
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

                // Return the LatLng object
                return new LatLng(lat, lng);
            } else {
                Log.e("Geocoder", "Address not found: " + address);  // Log error if not found
                Toast.makeText(this, "Address not found", Toast.LENGTH_SHORT).show();
                return null;  // Return null if geocoding fails
            }
        } catch (IOException e) {
            e.printStackTrace();
            Log.e("Geocoder", "Geocoding failed", e);  // Log the exception for debugging
            Toast.makeText(this, "Geocoding failed", Toast.LENGTH_SHORT).show();
            return null;  // Return null if there's an exception
        }
    }



}