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
import com.google.firebase.firestore.auth.User;
import com.google.maps.android.SphericalUtil;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class MapsActivity extends FragmentActivity implements OnMapReadyCallback {

    private GoogleMap mMap;
    private FusedLocationProviderClient fusedLocationClient;
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1;

    private ListView routesListView;
    private TextView stepsTextView;
    private ArrayList<Route> routesList;
    private ArrayAdapter<Route> routesAdapter;
    private EditText searchBar;// Search bar for manual location search
    private EditText buildingsearchbar;

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);

        // Initialize the ListView and the ArrayList
        routesListView = findViewById(R.id.routesListView);
        routesList = new ArrayList<>();
        stepsTextView = findViewById(R.id.stepsTextView);

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
            // Capture the address from the search bar
            String query = searchBar.getText().toString().trim();

            if (!query.isEmpty()) {
                // If search bar is not empty, geocode the address and add the route
                geocodeAndAddRoute(query);
            } else {
                // If the search bar is empty, show a toast message
                Toast.makeText(MapsActivity.this, "Please enter an address", Toast.LENGTH_SHORT).show();
            }
        });

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

        // Initialize the search bar
        searchBar = findViewById(R.id.locationEditText);

        // Set up the search button functionality
        findViewById(R.id.findDistanceButton).setOnClickListener(v -> {
            String query = searchBar.getText().toString().trim();
            if (!query.isEmpty()) {
                // Search for the location without adding it to Firebase
                searchLocation(query);
            } else {
                // If the search bar is empty, show a toast message
                Toast.makeText(MapsActivity.this, "Please enter an address to search", Toast.LENGTH_SHORT).show();
            }
        });

        buildingsearchbar = findViewById(R.id.buildingEditText);

        // set up searchbar for building info
        findViewById(R.id.findBuildingButton).setOnClickListener(v -> {
            String query = buildingsearchbar.getText().toString().trim();
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
        getLastKnownLocation();
    }


    private void geocodeAndAddRoute(String address) {
        Geocoder geocoder = new Geocoder(this);
        try {
            // Geocode the address entered by the user
            List<Address> addresses = geocoder.getFromLocationName(address, 1);

            if (addresses != null && !addresses.isEmpty()) {
                android.location.Address location = addresses.get(0);
                double lat = location.getLatitude();
                double lng = location.getLongitude();

                // Now add this route to Firebase
                addRouteToDatabase(address, address, lat, lng);  // Using the same address for name and address fields

            } else {
                Toast.makeText(this, "Address not found", Toast.LENGTH_SHORT).show();
            }
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "Geocoding failed", Toast.LENGTH_SHORT).show();
        }
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
        // need to switch to Firestore reference instead of realtime
        DatabaseReference routesRef = FirebaseDatabase.getInstance().getReference("routes");

        // Retrieve data from Firebase
        routesRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                routesList.clear();  // Clear the previous data
                for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                    if(snapshot.child("uid").getValue(String.class).equals(UserData.getInstance().getUid())) {
                        String name = snapshot.child("name").getValue(String.class);
                        String address = snapshot.child("address").getValue(String.class);
                        double latitude = snapshot.child("latitude").getValue(Double.class);
                        double longitude = snapshot.child("longitude").getValue(Double.class);

                        // Add the route to the list
                        routesList.add(new Route(RouteType.TEST_ROUTE, name, address));
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
        buildingsearchbar.setText("");
    }

    private void fetchRouteDataFromFirebase(Route route) {
        // Combine name and address to form a full address
        String address = route.getName() + " " + route.getAddress();
        geocodeAddress(address);// Call geocodeAddress() to get coordinates
        //fetchLocationData(route.getName());

    }
    private void addRouteToDatabase(String name, String address, double latitude, double longitude) {
      //need to switch to firebase reference instead of realtime
        DatabaseReference routesRef = FirebaseDatabase.getInstance().getReference("routes");

        // Create a new entry in the "routes" collection
        DatabaseReference newRouteRef = routesRef.push();
        newRouteRef.child("name").setValue(name);
        newRouteRef.child("address").setValue(address);
        newRouteRef.child("latitude").setValue(latitude);
        newRouteRef.child("longitude").setValue(longitude);
        newRouteRef.child("uid").setValue(UserData.getInstance().getUid());

        // Show a toast message to confirm the route was added
        Toast.makeText(MapsActivity.this, "Route added to Firebase", Toast.LENGTH_SHORT).show();

        // update the ListView
        routesList.add(new Route(RouteType.TEST_ROUTE, name, address));
        routesAdapter.notifyDataSetChanged();
    }

    private void searchLocation(String query) {
        // Geocode the entered search query (manual address search)
        geocodeAddress(query);

        //set the building info from the database
       // fetchLocationData(query);

        // Clear the search bar
        searchBar.setText("");

    }

    private void geocodeAddress(String address) {
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
                // Add the marker for the geocoded location
                mMap.clear();  // Clear previous markers
                mMap.addMarker(new MarkerOptions().position(geocodedLocation).title(address));

                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(geocodedLocation, 15));

                drawRouteFromCurrentLocation(lat, lng);
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

                // Add a marker on the current location
                mMap.addMarker(new MarkerOptions()
                        .position(currentLocation)
                        .title("Your Current Location"));


                // Move the camera to the current location
                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(currentLocation, 15));  // Zoom to current location

                // Build the GraphHopper API URL to request a walking route
                String url = "https://graphhopper.com/api/1/route?point=" + location.getLatitude() + "," + location.getLongitude() +
                        "&point=" + destinationLat + "," + destinationLon +
                        "&type=json&vehicle=foot&key=8d7f64ec-867f-4134-858d-cbc5a09ef9dc";

                LatLng newLocation = new LatLng(destinationLat, destinationLon);

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
                                    mMap.addPolyline(polylineOptions);

                                    //  zoom the map to fit the polyline
                                    LatLngBounds.Builder builder = new LatLngBounds.Builder();
                                    builder.include(currentLocation);  // Include current location
                                    builder.include(new LatLng(destinationLat, destinationLon));  // Include destination

                                    // Include all points in the polyline
                                    for (LatLng latLng : decodedPath) {
                                        builder.include(latLng);
                                    }
                                    //calculate the distance in meters, steps, and ETA
                                    double distance = SphericalUtil.computeDistanceBetween(currentLocation, newLocation);
                                    @SuppressLint("DefaultLocale") String distanceFormatted = String.format("%.2f",distance);
                                    stepsTextView.setText("distance: " + distanceFormatted +
                                                          " meters\n" + "steps: " + (int)(distance / .762)  +
                                                          "\nMinutes: " + Math.round(distance / 0.95 /60));

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
}