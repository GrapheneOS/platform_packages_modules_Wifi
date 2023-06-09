/*
 * Copyright (C) 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.server.wifi;

import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.wifi.WifiContext;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

import com.android.internal.annotations.VisibleForTesting;
import com.android.modules.utils.HandlerExecutor;
import com.android.modules.utils.build.SdkLevel;
import com.android.server.wifi.hal.WifiChip;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * Class that handles interactions with the AFC server and passing its response to the driver
 */
public class AfcManager {
    private static final String TAG = "WifiAfcManager";
    private static final long MINUTE_IN_MILLIS = 60 * 1000;
    private static final long LOCATION_MIN_TIME_MILLIS = 1 * MINUTE_IN_MILLIS;
    private static final float LOCATION_MIN_DISTANCE_METERS = 200;
    private final HandlerThread mWifiHandlerThread;
    private final WifiInjector mWifiInjector;
    private final WifiContext mContext;
    private final WifiNative mWifiNative;
    private final WifiGlobals mWifiGlobals;
    private final Clock mClock;
    private final LocationListener mLocationListener;
    private Location mLastKnownLocation;
    private String mLastKnownCountryCode;
    private LocationManager mLocationManager;
    private long mLastAfcServerQueryTime;
    private Location mLastKnownLocationUsedInAfcServerQuery;
    private String mProviderForLocationRequest = "";
    private boolean mIsAfcSupportedForCurrentCountry = false;

    public AfcManager(WifiContext context, WifiInjector wifiInjector) {
        mContext = context;
        mWifiInjector = wifiInjector;
        mClock = wifiInjector.getClock();

        mWifiHandlerThread = mWifiInjector.getWifiHandlerThread();
        mWifiGlobals = mWifiInjector.getWifiGlobals();
        mWifiNative = mWifiInjector.getWifiNative();

        mLocationListener = new LocationListener() {
            @Override
            public void onLocationChanged(Location location) {
                onLocationChange(location);
            }
        };
    }

    /**
     * This method starts listening to location changes which help determine whether to query the
     * AFC server. This should only be called when AFC is available in the country that the device
     * is in.
     */
    private void listenForLocationChanges() {
        List<String> locationProviders = mLocationManager.getProviders(true);

        // find a provider we can use for location updates, then request to listen for updates
        for (String provider : locationProviders) {
            if (isAcceptableProviderForLocationUpdates(provider)) {
                try {
                    mLocationManager.requestLocationUpdates(
                            provider,
                            LOCATION_MIN_TIME_MILLIS,
                            LOCATION_MIN_DISTANCE_METERS,
                            mLocationListener,
                            mWifiHandlerThread.getLooper()
                    );
                } catch (Exception e) {
                    Log.e(TAG, e.toString());
                }
                break;
            }
        }
    }

    /**
     * Stops listening to location changes for the current location listener.
     */
    private void stopListeningForLocationChanges() {
        mLocationManager.removeUpdates(mLocationListener);
    }

    /**
     * Returns whether this is a location provider we want to use when requesting location updates.
     */
    private boolean isAcceptableProviderForLocationUpdates(String provider) {
        // We don't want to actively initiate a location fix here (with gps or network providers).
        return LocationManager.PASSIVE_PROVIDER.equals(provider);
    }

    /**
     * Check if the current location is outside the bounds of the most recent boundary we provided
     * to the AFC server, and if so, then perform a re-query.
     */
    public void onLocationChange(Location location) {
        mLastKnownLocation = location;

        if (location == null) {
            Log.e(TAG, "Location is null");
            return;
        }

        /*
        Todo: query the AFC server if device has moved outside of location boundary,
            or too much time has passed since the last query was made
        */
    }

    /**
     * Sends the allowed AFC channels and frequencies to the driver.
     */
    private boolean setAfcChannelAllowance(WifiChip.AfcChannelAllowance afcChannelAllowance) {
        return mWifiNative.setAfcChannelAllowance(afcChannelAllowance);
    }

    /**
     * Query the AFC server to get allowed AFC frequencies and channels, then update the driver with
     * these values.
     */
    private void queryServerAndInformDriver(Location location) {
        mLastAfcServerQueryTime = mClock.getElapsedSinceBootMillis();
        mLastKnownLocationUsedInAfcServerQuery = location;

        /*
        Todo: To be implemented in Milestones 1 and 2:
             - convert Location -> AfcLocation object
             - create a JSON object for the AFC request in the required form
             - make API call to the AFC server and get response
             - parse response, and update the driver with the allowed channels and frequencies
         */
    }

    /**
     * On a country code change, check if AFC is supported in this country. If it is, start
     * listening to location updates if we aren't already, and query the AFC server. If it isn't,
     * stop listening to location updates and send an AfcChannelAllowance object with empty
     * frequency and channel lists to the driver.
     */
    public void onCountryCodeChange(String countryCode) {
        if (!mWifiGlobals.isAfcSupportedOnDevice() || countryCode.equals(mLastKnownCountryCode)) {
            return;
        }
        mLastKnownCountryCode = countryCode;

        String afcServerUrlForCountry = mWifiGlobals.getAfcServerUrlForCountry(countryCode);
        // Todo: set the AFC server URL based on country code

        // if AFC support has not changed, we do not need to do anything else
        if (mIsAfcSupportedForCurrentCountry == (afcServerUrlForCountry != null)) return;

        mIsAfcSupportedForCurrentCountry = !mIsAfcSupportedForCurrentCountry;
        getLocationManager();

        if (mLocationManager == null) {
            Log.e(TAG, "Location Manager should not be null.");
            return;
        }

        if (!mIsAfcSupportedForCurrentCountry) {
            stopListeningForLocationChanges();

            // send driver AFC allowance with empty frequency and channel arrays
            WifiChip.AfcChannelAllowance afcChannelAllowance = new WifiChip.AfcChannelAllowance();
            afcChannelAllowance.availableAfcFrequencyInfos = new ArrayList<>();
            afcChannelAllowance.availableAfcChannelInfos = new ArrayList<>();
            setAfcChannelAllowance(afcChannelAllowance);

            return;
        }

        listenForLocationChanges();
        getProviderForLocationRequest();
        if (mProviderForLocationRequest.isEmpty()) return;

        mLocationManager.getCurrentLocation(
                mProviderForLocationRequest, null,
                new HandlerExecutor(new Handler(mWifiHandlerThread.getLooper())),
                currentLocation -> {
                    mLastKnownLocation = currentLocation;

                    if (currentLocation == null) {
                        Log.e(TAG, "Current location is null.");
                        return;
                    }

                /*
                Todo: Call a function that queries the AFC server with the updated location
                   to get allowed frequencies and channels
                 */
                    queryServerAndInformDriver(currentLocation);
                });
    }

    /**
     * Returns the preferred provider to use for getting the current location, or an empty string
     * if none are present.
     */
    private String getProviderForLocationRequest() {
        if (!mProviderForLocationRequest.isEmpty() || mLocationManager == null) {
            return mProviderForLocationRequest;
        }

        // Order in which location providers are preferred. A lower index means a higher preference.
        String[] preferredProvidersInOrder;
        // FUSED_PROVIDER is only available from API level 31 onwards
        if (SdkLevel.isAtLeastS()) {
            preferredProvidersInOrder = new String[] { LocationManager.FUSED_PROVIDER,
                    LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER };
        } else {
            preferredProvidersInOrder = new String[] { LocationManager.NETWORK_PROVIDER,
                    LocationManager.GPS_PROVIDER };
        }

        List<String> availableLocationProviders = mLocationManager.getProviders(true);

        // return the first preferred provider that is available
        for (String preferredProvider : preferredProvidersInOrder) {
            if (availableLocationProviders.contains(preferredProvider)) {
                mProviderForLocationRequest = preferredProvider;
                break;
            }
        }
        return mProviderForLocationRequest;
    }

    /**
     * Dump the internal state of AfcManager.
     */
    public void dump(PrintWriter pw) {
        pw.println("Dump of AfcManager");
        if (!mWifiGlobals.isAfcSupportedOnDevice()) {
            pw.println("AfcManager - AFC is not supported on this device.");
            return;
        }
        pw.println("AfcManager - AFC is supported on this device.");

        if (!mIsAfcSupportedForCurrentCountry) {
            pw.println("AfcManager - AFC is not available in this country, with country code: "
                    + mLastKnownCountryCode);
        } else {
            pw.println("AfcManager - AFC is available in this country, with country code: "
                    + mLastKnownCountryCode);
        }

        pw.println("AfcManager - Last time the server was queried: " + mLastAfcServerQueryTime);
    }

    @VisibleForTesting
    LocationManager getLocationManager() {
        if (mLocationManager == null) {
            mLocationManager = mContext.getSystemService(LocationManager.class);
        }
        return mLocationManager;
    }

    @VisibleForTesting
    Location getLastKnownLocation() {
        return mLastKnownLocation;
    }

    @VisibleForTesting
    long getLastAfcServerQueryTime() {
        return mLastAfcServerQueryTime;
    }

    @VisibleForTesting
    Location getLastLocationUsedInAfcServerQuery() {
        return mLastKnownLocationUsedInAfcServerQuery;
    }
}
