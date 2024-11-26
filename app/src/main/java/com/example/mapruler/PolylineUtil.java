package com.example.mapruler;

import com.google.android.gms.maps.model.LatLng;
import java.util.ArrayList;
import java.util.List;

public class PolylineUtil {

    /**
     * Decodes a Google Maps encoded polyline string into a list of LatLng points.
     * @param encoded The encoded polyline string.
     * @return A list of LatLng points.
     */
    public static List<LatLng> decodePolyline(String encoded) {
        List<LatLng> decodedPath = new ArrayList<>();

        int index = 0;
        int len = encoded.length();
        int lat = 0;
        int lng = 0;

        while (index < len) {
            // Decode latitude
            int shift = 0;
            int result = 0;
            while (true) {
                int b = encoded.charAt(index++) - 63;
                result |= (b & 0x1F) << shift;
                shift += 5;
                if (b < 0x20) {
                    break;
                }
            }
            lat += ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));

            // Decode longitude
            shift = 0;
            result = 0;
            while (true) {
                int b = encoded.charAt(index++) - 63;
                result |= (b & 0x1F) << shift;
                shift += 5;
                if (b < 0x20) {
                    break;
                }
            }
            lng += ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));

            // Add the decoded latitude and longitude to the list
            decodedPath.add(new LatLng((lat / 1E5), (lng / 1E5)));
        }

        return decodedPath;
    }

}
