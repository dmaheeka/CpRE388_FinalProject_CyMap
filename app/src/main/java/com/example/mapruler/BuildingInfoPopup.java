/**
 * Class that collects building data from Firebase and displays it in a popup window.
 */
package com.example.mapruler;

import androidx.fragment.app.DialogFragment;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import java.util.ArrayList;

public class BuildingInfoPopup extends DialogFragment {


    // Views for building name and business hours
    private TextView buildingNameTextView;
    private TextView buildingInfoTextView;
    // Arguments passed to the dialog fragment
    private String buildingName;
    private ArrayList<String> businessHours;

    /**
     * Creates a new building information popup
     *
     * @param buildingName public name of the building
     * @param businessHours ArrayList of the lines of the hours text.
     *
     * @return BuildingInfoPopup fragment with the given information.
     */
    public static BuildingInfoPopup newInstance(String buildingName, ArrayList<String> businessHours) {
        BuildingInfoPopup fragment = new BuildingInfoPopup();
        Bundle args = new Bundle();
        args.putString("name", buildingName);
        args.putStringArrayList("hours", businessHours);
        fragment.setArguments(args);
        return fragment;
    }

    @SuppressLint("MissingInflatedId")
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        // Inflate the layout
        View view = inflater.inflate(R.layout.building_popup, container, false);

        // Initialize views
        buildingNameTextView = view.findViewById(R.id.buildingNameTextView);
        buildingInfoTextView = view.findViewById(R.id.buildingInfoTextView);

        // Get the arguments passed to the fragment
        if (getArguments() != null) {
            buildingName = getArguments().getString("name");
            businessHours = getArguments().getStringArrayList("hours");

            // Set the building name and business hours to the TextViews
            buildingNameTextView.setText(buildingName);

            // Format the business hours array into a single string
            // Format the business hours array into a single string
            StringBuilder hoursString = new StringBuilder();
            if (businessHours != null) {
                for (String hour : businessHours) {
                    // Split only by the first colon (":") to separate day and time
                    String[] parts = hour.split(":", 2);
                    if (parts.length == 2) {
                        String day = parts[0].trim();
                        Log.d(day,"Day: " + day);
                        String times = parts[1].trim();
                        Log.d(times,"Times: " + times);

                        hoursString.append(String.format("%-10s %s\n", day + ":" , times));
                    }
                }
            }

            buildingInfoTextView.setText(hoursString.toString());
        } else {
            // Handle the case where arguments are missing
            buildingNameTextView.setText("No building name found");
            buildingInfoTextView.setText("No business hours found");
        }
        // Handle the close/dismiss button
        Button closeButton = view.findViewById(R.id.closeButton);
        closeButton.setOnClickListener(v -> dismiss()); // Dismiss the fragment when the button is clicked

        return view;
    }
}
