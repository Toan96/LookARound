package com.example.antonio.arprova.my_location;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.ResultReceiver;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

import com.example.antonio.arprova.MapFragment;
import com.example.antonio.arprova.R;
import com.example.antonio.arprova.UpdateUICallback;
import com.example.antonio.arprova.Utils;

/**
 * Created by Antonio on 19/01/2018.
 * .
 */

public class MyGPSLocation {

    private static final long MIN_TIME = 15 * 1000;
    private static final long MIN_DISTANCE = 10;
    private static String TAG = "MyGPSLocation";
    private static boolean first = true;
    private Context context;
    private LocationManager locationManager;
    private AddressResultReceiver mResultReceiver = new AddressResultReceiver(new Handler());
    private Runnable runnable;
    private Handler handler = null;
    private UpdateUICallback updateUICallback = null;
    private AlertDialog.Builder dialog = null;

    //listener per gps updates
    private LocationListener locationListener = new LocationListener() {
        @Override
        public void onLocationChanged(Location location) {
            Log.d("gps: ", "location changed");
            startIntentService(location);
            updateUICallback.updateGpsTv(Utils.formattedValues(location));
            if (first) {
                updateUICallback.updateSeekZoom(MapFragment.MAX_ZOOM_SEEK);
                first = false;
            }
            MapFragment.setCamera(location);
        }

        @Override
        public void onStatusChanged(String s, int i, Bundle bundle) {
            Log.d("gps: ", "status changed");
            takeLocationUpdates();
        }

        @Override
        public void onProviderEnabled(String s) {
            Log.d("gps: ", "enabled");
            takeLocationUpdates();
        }

        @Override
        public void onProviderDisabled(String s) {
            Log.d("gps: ", "disabled");
            String string = context.getString(R.string.tvGpsValuesHint);
            updateUICallback.updateGpsTv(string);
            Log.d(TAG, "updated tvGpsValues");
            Toast.makeText(context.getApplicationContext(), R.string.dialogLocation_Title, Toast.LENGTH_SHORT).show();
        }
    };

    public MyGPSLocation(Context context, UpdateUICallback uiCallback) {
        this.context = context;
        this.updateUICallback = uiCallback;
        this.locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
    }

    //metodi per gps
    @SuppressLint("MissingPermission")
    public void takeLocationUpdates() {
        if (!checkLocation())
            return;
        Log.d(TAG, "takeLocationUpdates");
        Criteria criteria = new Criteria();
        criteria.setAccuracy(Criteria.ACCURACY_FINE);
        criteria.setAltitudeRequired(true);
        criteria.setBearingRequired(true);
        criteria.setCostAllowed(true);
        criteria.setPowerRequirement(Criteria.POWER_MEDIUM);
        String provider = locationManager.getBestProvider(criteria, true);
        if (provider != null) {
            //forse qui veniva chiamato troppe volte inutilmente(impossibile vedere). removeupdates per evitare (spero).
            locationManager.removeUpdates(locationListener);
            locationManager.requestLocationUpdates(provider, MIN_TIME, MIN_DISTANCE, locationListener);
            Log.d("Best location provider", provider);
        }
    }

    //metodi per verificare la presenza della geolocalizzazione e abilitarla in caso negativo.
    private boolean checkLocation() {
        if (!isLocationEnabled())
            showAlert();
        return isLocationEnabled();
    }

    private void showAlert() {
        if (null == dialog) {
            dialog = new AlertDialog.Builder(context);
            dialog.setTitle(R.string.dialogLocation_Title)
                    .setMessage(R.string.dialogLocation_Message)
                    .setPositiveButton(R.string.dialogLocation_Positive, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface paramDialogInterface, int paramInt) {
                            Intent myIntent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                            context.startActivity(myIntent);
                        }
                    });
        }
        dialog.setNegativeButton(R.string.dialogLocation_Negative, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface paramDialogInterface, int paramInt) {
                handler = new Handler();
                runnable = new Runnable() {
                    @Override
                    public void run() {
                        takeLocationUpdates();
                    }
                };
                handler.postDelayed(runnable, 7000);

            }
        });
        dialog.setCancelable(false);
        dialog.show();
    }

    private boolean isLocationEnabled() {
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
    }

    public void removeHandler() {
        if (handler != null && runnable != null) {
            handler.removeCallbacks(runnable);
        }
    }

    public void stopUpdates() {
        locationManager.removeUpdates(locationListener);
    }

    @SuppressLint("MissingPermission")
    public Location getBestLastKnownLocation() {
        Location lastKnown = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
        if (lastKnown != null) {
            return lastKnown;
        }
        lastKnown = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
        if (lastKnown != null) {
            return lastKnown;
        }
        return null;
    }

    //methods to take address

    /**
     * Creates an intent, adds location data to it as an extra, and starts the intent service for
     * fetching an address.
     */
    public void startIntentService(Location location) {
        // Create an intent for passing to the intent service responsible for fetching the address.
        Intent intent = new Intent(context, FetchAddressIntentService.class);

        // Pass the result receiver as an extra to the service.
        intent.putExtra(FetchAddressIntentService.RECEIVER, mResultReceiver);

        // Pass the location data as an extra to the service.
        intent.putExtra(FetchAddressIntentService.LOCATION_DATA_EXTRA, location);

        // Start the service. If the service isn't already running, it is instantiated and started
        // (creating a process for it if needed); if it is running then it remains running. The
        // service kills itself automatically once all intents are processed.
        context.startService(intent);
    }

    private class AddressResultReceiver extends ResultReceiver {
        AddressResultReceiver(Handler handler) {
            super(handler);
        }

        /**
         * Receives data sent from FetchAddressIntentService and updates the UI in MainActivity.
         */
        @Override
        protected void onReceiveResult(int resultCode, Bundle resultData) {

            // Display the address string or an error message sent from the intent service.
            String mAddressOutput = resultData.getString(FetchAddressIntentService.RESULT_DATA_KEY);

            if (resultCode == FetchAddressIntentService.SUCCESS_RESULT) {
                updateUICallback.updateGpsTv(mAddressOutput);
                Log.d(TAG, context.getString(R.string.address_found));
            }
        }
    }
}