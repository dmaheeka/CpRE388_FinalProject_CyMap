/**
 * Main activity for the map.
 */
package com.example.mapruler;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
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
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.MapStyleOptions;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.GeoPoint;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.maps.android.SphericalUtil;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.IOException;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.List;

//imports for sensors:
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

public class MapsActivity extends FragmentActivity implements OnMapReadyCallback, SensorEventListener {

    private GoogleMap mMap;
    private FusedLocationProviderClient fusedLocationClient;
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1;
    //sensor detection
    private SensorManager sensorManager;
    private Sensor rotationVectorSensor;
    private Marker userDirectionMarker; // To show the user's direction as an arrow
    private float currentBearing = 0f; // Store the current direction
    private List<Marker> routeMarkers = new ArrayList<>();
    List<Polyline> polylineList = new ArrayList<>();
    private ListView routesListView;
    private TextView stepsTextView;
    private TextView directionsTextView;
    private ArrayList<Route> routesList;
    private ArrayAdapter<Route> routesAdapter;
    private EditText startSearchBar;
    private EditText destSearchBar;
    private EditText buildingSearchBar;

    private Route currRoute;

    private LocationRequest locationRequest;
    private LocationCallback locationCallback;

    private List<Marker> poiMarkers = new ArrayList<>();


    private FirebaseFirestore datab;

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
        //sensors:
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        rotationVectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);

        if (rotationVectorSensor == null) {
            Toast.makeText(this, "Device does not have a rotation vector sensor.", Toast.LENGTH_SHORT).show();
        }


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

        datab = FirebaseFirestore.getInstance();  // Initialize Firestore

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

        Button btnShowPOIs = findViewById(R.id.btnShowPOIs);
        btnShowPOIs.setOnClickListener(v -> {
            // Clear existing POI markers
            clearPOIMarkers();

            poiMarkers.clear();
            showPOIsAlongRoute();  // Show POIs within the specified range
        });
    }

    private void clearPOIMarkers() {
        for (Marker marker : poiMarkers) {
            marker.remove(); // Remove the marker from the map
        }
        poiMarkers.clear(); // Clear the list
    }

    private void showPOIsAlongRoute() {
        // Fetch the POIs from Firestore
        datab.collection("POIs")
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        for (QueryDocumentSnapshot document : task.getResult()) {
                            String name = document.getString("name");
                            GeoPoint location = document.getGeoPoint("location"); // Get GeoPoint from Firestore
                            String type = document.getString("type"); // Get type of POI (e.g., cafe, bus_stop)

                            if (location != null) {
                                double lat = location.getLatitude();
                                double lng = location.getLongitude();
                                LatLng poiLatLng = new LatLng(lat, lng);

                                // Iterate through each polyline in the polylineList
                                for (Polyline polyline : polylineList) {
                                    List<LatLng> polylinePoints = polyline.getPoints(); // Get the points of the polyline

                                    // Iterate over the points in the polyline
                                    for (LatLng polylinePoint : polylinePoints) {
                                        // Check if the POI is within range of this polyline point
                                        if (isWithinRange(poiLatLng, polylinePoint)) {
                                            // Choose the correct marker icon based on POI type
                                            int markerIcon;
                                            if ("cafe".equals(type)) {
                                                markerIcon = R.drawable.ic_cafe_marker; // Cafe icon (replace with actual resource)
                                            } else if ("bus_stop".equals(type)) {
                                                markerIcon = R.drawable.ic_bus_stop_marker; // Bus stop icon (replace with actual resource)
                                            } else {
                                                markerIcon = R.drawable.ic_default_marker; // Default icon for other types
                                            }

                                            // Add marker for POI on the map
                                            Marker marker = mMap.addMarker(new MarkerOptions()
                                                    .position(poiLatLng)
                                                    .title(name)
                                                    .icon(BitmapDescriptorFactory.fromResource(markerIcon)));
                                            // Add marker to POI markers list
                                            poiMarkers.add(marker);
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        Log.e("Firestore", "Error getting documents: ", task.getException());
                    }
                });
    }


    private boolean isWithinRange(LatLng poiLatLng, LatLng polylinePoint) {
        double radius = 161.0;  // 0.2 miles in meters

        // Calculate the distance between the polyline point and the POI
        float[] results = new float[1];
        Location.distanceBetween(polylinePoint.latitude, polylinePoint.longitude,
                poiLatLng.latitude, poiLatLng.longitude,
                results);

        return results[0] <= radius;  // Return true if the distance is within 0.2 miles
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

    }

    @Override
    protected void onResume() {
        super.onResume();
        if (rotationVectorSensor != null) {
            sensorManager.registerListener(this, rotationVectorSensor, SensorManager.SENSOR_DELAY_UI);
        }

        // Request location updates when the activity is resumed
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            locationRequest = LocationRequest.create();
            locationRequest.setInterval(10000);  // Request location every 10 seconds
            locationRequest.setFastestInterval(5000);  // Fastest interval: 5 seconds
            locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

            locationCallback = new LocationCallback() {
                @Override
                public void onLocationResult(LocationResult locationResult) {
                    if (locationResult != null && locationResult.getLastLocation() != null) {
                        LatLng currentLocation = new LatLng(locationResult.getLastLocation().getLatitude(),
                                locationResult.getLastLocation().getLongitude());
                        ensureUserDirectionMarker(currentLocation);
                    }
                }
            };

            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, null);
        }
    }




    @Override
    protected void onPause() {
        super.onPause();
        if (rotationVectorSensor != null) {
            sensorManager.unregisterListener(this);
        }

        // Stop location updates to save resources
        if (fusedLocationClient != null && locationCallback != null) {
            fusedLocationClient.removeLocationUpdates(locationCallback);
        }
    }


    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ROTATION_VECTOR) {
            float[] rotationMatrix = new float[9];
            float[] orientationAngles = new float[3];

            // Convert rotation vector to a rotation matrix
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values);

            // Get orientation angles from the rotation matrix
            SensorManager.getOrientation(rotationMatrix, orientationAngles);

            // Convert radians to degrees
            float bearing = (float) Math.toDegrees(orientationAngles[0]);

            // Normalize the bearing to 0-360
            if (bearing < 0) {
                bearing += 360;
            }

            currentBearing = bearing;

            if (userDirectionMarker != null) {
                userDirectionMarker.setRotation(currentBearing); // Update arrow direction
            }
        }
    }


    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // No action needed for now
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
        // Check if permission to access fine location is granted
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            // If permission is not granted, request it
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);  // 1 is a request code
            return; // Exit the method until permission is granted
        }

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

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == 1) {  // 1 is the request code
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted
                searchRoute(startSearchBar.getText().toString(), destSearchBar.getText().toString());
            } else {
                // Permission denied
                Toast.makeText(this, "Permission denied to access location", Toast.LENGTH_SHORT).show();
            }
        }
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

        //panToCurrentLocation();
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
            //updateDirectionMarker();
            //updateLocationAndBearing(startLocation);
        }
    }

    private void drawRoute(Route route) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            // If permission is not granted, request it
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);  // 1 is the request code
            return; // Exit the method until permission is granted
        }

        clearPOIMarkers();

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


                            // Clear all polylines
                            for (Polyline polyline : polylineList) {
                                polyline.remove();  // Remove each polyline from the map
                            }

                            // Clear the list after removing all polylines
                            polylineList.clear();

                            // Create PolylineOptions and add the decoded path
                            PolylineOptions polylineOptions = new PolylineOptions()
                                    .addAll(decodedPath)  // Add decoded LatLng points to polyline
                                    .width(5)
                                    .color(getResources().getColor(R.color.colorPrimary));
                            // Add the polyline to the map and store it in the list
                            Polyline polyline = mMap.addPolyline(polylineOptions);
                            polylineList.add(polyline);  // Keep track of the polyline
                            // Add the polyline to the map
                            //mMap.clear();  // Clear previous route
                            // Clear route markers
                            for (Marker marker : routeMarkers) {
                                marker.remove();
                            }
                            routeMarkers.clear();

                            //mMap.addPolyline(polylineOptions);

                            //  zoom the map to fit the polyline
                            LatLngBounds.Builder builder = new LatLngBounds.Builder();
                            for(LatLng latLng : route.getCoordinates()){
                                builder.include(latLng);
                            }

                            // add markers
                            ArrayList<LatLng> coords = route.getCoordinates();
                            ArrayList<String> addresses = route.getAddresses();
                            for (int i = 0; i < route.getCoordinates().size(); i++) {
                                Marker marker = mMap.addMarker(new MarkerOptions().position(coords.get(i)).title(addresses.get(i)));
                                routeMarkers.add(marker);
                            }

                            // **Add or update userDirectionMarker**
                            Log.d("Location", "Before getting location");
                            fusedLocationClient.getLastLocation().addOnSuccessListener(MapsActivity.this, location -> {
                                if (location != null) {
                                    LatLng currentLatLng = new LatLng(location.getLatitude(), location.getLongitude());
                                    Log.d("Location", "Location found: " + currentLatLng.toString());

                                    // Only add the direction marker if it doesn't exist
                                    if (userDirectionMarker == null) {
                                        Log.d("Marker", "Adding new user direction marker");
                                        userDirectionMarker = mMap.addMarker(new MarkerOptions()
                                                .position(currentLatLng)
                                                .icon(BitmapDescriptorFactory.fromResource(R.drawable.arrow_icon))  // Use your arrow icon
                                                .anchor(0.5f, 0.5f)
                                                .flat(true));  // Flat marker for rotation

                                        Log.d("MarkerDetails", "New marker added at position: " + currentLatLng);
                                    } else {
                                        // Update position and bearing of the existing marker
                                        Log.d("Marker", "Updating user direction marker");
                                        userDirectionMarker.setPosition(currentLatLng);
                                        userDirectionMarker.setRotation(currentBearing);
                                    }

                                    // Optionally, move camera to current location
                                    mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 15));
                                } else {
                                    Log.d("Location", "Location is null");
                                }
                            });

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
                            // Update the direction marker after drawing the route
                            //updateDirectionMarker();
                            // Update current location marker

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

    // Function to ensure user direction marker exists or update it
    private void ensureUserDirectionMarker(LatLng currentLocation) {
        // Only add the direction marker if it doesn't exist
        if (userDirectionMarker == null) {
            Log.d("Marker", "Adding new user direction marker");
            userDirectionMarker = mMap.addMarker(new MarkerOptions()
                    .position(currentLocation)
                    .icon(BitmapDescriptorFactory.fromResource(R.drawable.arrow_icon))  // Use your arrow icon
                    .anchor(0.5f, 0.5f)
                    .flat(true));  // Flat marker for rotation

            Log.d("MarkerDetails", "New marker added at position: " + currentLocation);
        } else {
            // Update position and bearing of the existing marker
            Log.d("Marker", "Updating user direction marker");
            userDirectionMarker.setPosition(currentLocation);
            userDirectionMarker.setRotation(currentBearing);  // Ensure currentBearing is updated properly
        }

        // Optionally, move camera to current location
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(currentLocation, 15));
    }


}