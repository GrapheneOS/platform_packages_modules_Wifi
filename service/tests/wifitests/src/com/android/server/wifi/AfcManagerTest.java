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

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.wifi.WifiContext;
import android.os.HandlerThread;
import android.os.Looper;

import androidx.test.filters.SmallTest;

import com.android.modules.utils.HandlerExecutor;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.function.Consumer;

/**
 * Unit tests for {@link com.android.server.wifi.AfcManager}.
 */
@SmallTest
public class AfcManagerTest extends WifiBaseTest {
    @Mock Clock mClock;
    @Mock WifiContext mContext;
    @Mock Location mLocation;
    @Mock LocationManager mLocationManager;
    @Mock Looper mLooper;
    @Mock HandlerThread mWifiHandlerThread;
    @Mock WifiInjector mWifiInjector;
    @Mock WifiNative mWifiNative;
    @Mock WifiGlobals mWifiGlobals;
    private AfcManager mAfcManager;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        when(mContext.getSystemService(LocationManager.class)).thenReturn(mLocationManager);
        when(mWifiInjector.getWifiHandlerThread()).thenReturn(mWifiHandlerThread);
        when(mWifiInjector.getClock()).thenReturn(mClock);
        when(mWifiInjector.getWifiNative()).thenReturn(mWifiNative);
        when(mWifiInjector.getWifiGlobals()).thenReturn(mWifiGlobals);
        when(mWifiHandlerThread.getLooper()).thenReturn(mLooper);
        when(mWifiGlobals.isAfcSupportedOnDevice()).thenReturn(true);
        when(mWifiGlobals.getAfcServerUrlForCountry(anyString())).thenReturn(
                "https://example.com/");

        when(mLocationManager.getProviders(anyBoolean())).thenReturn(List.of(
                LocationManager.FUSED_PROVIDER, LocationManager.PASSIVE_PROVIDER,
                LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER));
    }

    AfcManager makeAfcManager() {
        return new AfcManager(mContext, mWifiInjector);
    }

    /**
     * Verify that a crash does not occur if the AFC Manager is provided with a null location on
     * a call to {@link AfcManager#onLocationChange}.
     */
    @Test
    public void testNoCrashForNullLocation() {
        mAfcManager = makeAfcManager();
        when(mClock.getElapsedSinceBootMillis()).thenReturn(9999L);

        mAfcManager.onLocationChange(null);
        assertThat(mAfcManager.getLastKnownLocation()).isEqualTo(null);
    }

    /**
     * Verify that a when a location change occurs, we update the lastKnownLocation variable.
     */
    @Test
    public void testUpdateLastKnownLocationOnLocationChange() {
        mAfcManager = makeAfcManager();
        when(mClock.getElapsedSinceBootMillis()).thenReturn(9999L);

        mAfcManager.onLocationChange(mLocation);
        assertThat(mAfcManager.getLastKnownLocation()).isEqualTo(mLocation);
        assertThat(mAfcManager.getLocationManager()).isEqualTo(mLocationManager);
    }

    /**
     * Verify that we fetch a new location and re-query the server when the country code changes.
     */
    @Test
    public void testGetLocationAfterCountryCodeChange() {
        mAfcManager = makeAfcManager();
        long now = 9999L;
        when(mClock.getElapsedSinceBootMillis()).thenReturn(now);
        mAfcManager.onCountryCodeChange("US");

        ArgumentCaptor<Consumer<Location>> consumerArgumentCaptor =
                ArgumentCaptor.forClass(Consumer.class);

        // check that we fetch a new location
        verify(mLocationManager).getCurrentLocation(anyString(), any(),
                any(HandlerExecutor.class), consumerArgumentCaptor.capture());

        // run the consumer's callback, then verify that we updated the lastKnownLocation
        Consumer<Location> locationConsumer = consumerArgumentCaptor.getValue();
        locationConsumer.accept(mLocation);
        assertThat(mAfcManager.getLastKnownLocation()).isEqualTo(mLocation);

        // check that when a query is sent to the server, we are caching information
        // about the request
        assertThat(mAfcManager.getLastAfcServerQueryTime()).isEqualTo(now);
        assertThat(mAfcManager.getLastLocationUsedInAfcServerQuery()).isEqualTo(mLocation);

        // verify we are setting lastKnownLocation to null when a null location is passed in
        locationConsumer.accept(null);
        assertThat(mAfcManager.getLastKnownLocation()).isEqualTo(null);
    }

    /**
     * Verify that the AFC Manager is created correctly and loads the clock and handler thread from
     * the {@link WifiInjector}
     */
    @Test
    public void testAfcManagerCorrectlyCreated() {
        mAfcManager = makeAfcManager();
        verify(mWifiInjector).getClock();
        verify(mWifiInjector).getWifiHandlerThread();
        verify(mWifiInjector).getWifiGlobals();
    }

    /**
     * Verify that when AFC is disabled, we do not query the server after a country code change.
     */
    @Test
    public void testNoQueryWhenAfcNotEnabled() {
        mAfcManager = makeAfcManager();
        when(mWifiGlobals.isAfcSupportedOnDevice()).thenReturn(false);
        mAfcManager.onCountryCodeChange("US");

        verifyNoMoreInteractions(mLocationManager);
    }

    /**
     * Verify that the network provider is used to get the current location if the fused provider
     * is not available (it is only available from API level 31 onwards).
     */
    @Test
    public void testCorrectProviderUsedForGetLocation() {
        mAfcManager = makeAfcManager();
        // list doesn't include the fused provider
        when(mLocationManager.getProviders(anyBoolean())).thenReturn(List.of(
                LocationManager.PASSIVE_PROVIDER, LocationManager.NETWORK_PROVIDER,
                LocationManager.GPS_PROVIDER));

        mAfcManager.onCountryCodeChange("US");
        ArgumentCaptor<String> providerArgumentCaptor = ArgumentCaptor.forClass(String.class);
        verify(mLocationManager).getCurrentLocation(providerArgumentCaptor.capture(), any(),
                any(HandlerExecutor.class), any(Consumer.class));

        // capture the provider, and check that it is the expected provider
        String provider = providerArgumentCaptor.getValue();
        assertThat(provider).isEqualTo(LocationManager.NETWORK_PROVIDER);
    }

    /**
     * Verify that when AFC is not available in a country, the location listener is no longer
     * informed of location updates.
     */
    @Test
    public void testStopListeningForLocationUpdates() {
        mAfcManager = makeAfcManager();
        ArgumentCaptor<LocationListener> locationListenerCaptor =
                ArgumentCaptor.forClass(LocationListener.class);

        mAfcManager.onCountryCodeChange("US");
        // check that we listen for location updates
        verify(mLocationManager).requestLocationUpdates(anyString(), anyLong(),
                anyFloat(), locationListenerCaptor.capture(), any(Looper.class));
        LocationListener locationListener = locationListenerCaptor.getValue();

        // Mock another country code change, this time with AFC not available, and check that the
        // location listener is removed from location updates.
        when(mWifiGlobals.getAfcServerUrlForCountry(anyString())).thenReturn(null);
        mAfcManager.onCountryCodeChange("00");

        verify(mLocationManager).removeUpdates(locationListener);
    }
}
