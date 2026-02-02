/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.server.wifi.nl80211;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.net.wifi.ScanResult;

import com.android.modules.utils.build.SdkLevel;

import org.junit.Before;
import org.junit.Test;

public class DeviceWiphyCapabilitiesTest {

    private static final int TEST_TX_STREAMS = 2;
    private static final int TEST_RX_STREAMS = 3;
    private static final int TEST_AKMS = 5;

    // Wificond version of DeviceWiphyCapabilities for copy constructor testing
    private android.net.wifi.nl80211.DeviceWiphyCapabilities mWificondCaps;

    @Before
    public void setUp() {
        mWificondCaps = new android.net.wifi.nl80211.DeviceWiphyCapabilities();
        mWificondCaps.setWifiStandardSupport(ScanResult.WIFI_STANDARD_11N, true);
        mWificondCaps.setWifiStandardSupport(ScanResult.WIFI_STANDARD_11AC, true);
        mWificondCaps.setWifiStandardSupport(ScanResult.WIFI_STANDARD_11AX, true);
        mWificondCaps.setWifiStandardSupport(ScanResult.WIFI_STANDARD_11BE, true);
        mWificondCaps.setChannelWidthSupported(ScanResult.CHANNEL_WIDTH_160MHZ, true);
        mWificondCaps.setChannelWidthSupported(ScanResult.CHANNEL_WIDTH_80MHZ_PLUS_MHZ, true);
        mWificondCaps.setChannelWidthSupported(ScanResult.CHANNEL_WIDTH_320MHZ, true);
        mWificondCaps.setMaxNumberTxSpatialStreams(TEST_TX_STREAMS);
        mWificondCaps.setMaxNumberRxSpatialStreams(TEST_RX_STREAMS);
        if (SdkLevel.isAtLeastV()) {
            mWificondCaps.setMaxNumberAkms(TEST_AKMS);
        }
    }

    @Test
    public void testDefaultConstructor() {
        DeviceWiphyCapabilities caps = new DeviceWiphyCapabilities.Builder().build();

        assertTrue(caps.isWifiStandardSupported(ScanResult.WIFI_STANDARD_LEGACY));
        assertFalse(caps.isWifiStandardSupported(ScanResult.WIFI_STANDARD_11N));
        assertFalse(caps.isWifiStandardSupported(ScanResult.WIFI_STANDARD_11AC));
        assertFalse(caps.isWifiStandardSupported(ScanResult.WIFI_STANDARD_11AX));
        assertFalse(caps.isWifiStandardSupported(ScanResult.WIFI_STANDARD_11BE));

        assertTrue(caps.isChannelWidthSupported(ScanResult.CHANNEL_WIDTH_20MHZ));
        assertFalse(caps.isChannelWidthSupported(ScanResult.CHANNEL_WIDTH_40MHZ));
        assertFalse(caps.isChannelWidthSupported(ScanResult.CHANNEL_WIDTH_80MHZ));
        assertFalse(caps.isChannelWidthSupported(ScanResult.CHANNEL_WIDTH_160MHZ));
        assertFalse(caps.isChannelWidthSupported(ScanResult.CHANNEL_WIDTH_80MHZ_PLUS_MHZ));
        assertFalse(caps.isChannelWidthSupported(ScanResult.CHANNEL_WIDTH_320MHZ));

        assertEquals(1, caps.getMaxNumberTxSpatialStreams());
        assertEquals(1, caps.getMaxNumberRxSpatialStreams());
        if (SdkLevel.isAtLeastV()) {
            assertEquals(1, caps.getMaxNumberAkms());
        }
    }

    @Test
    public void testCreateFromWificondCapabilities() {
        DeviceWiphyCapabilities caps =
                DeviceWiphyCapabilities.Builder.createFromWificondCapabilities(mWificondCaps)
                        .build();

        assertTrue(caps.isWifiStandardSupported(ScanResult.WIFI_STANDARD_LEGACY));
        assertTrue(caps.isWifiStandardSupported(ScanResult.WIFI_STANDARD_11N));
        assertTrue(caps.isWifiStandardSupported(ScanResult.WIFI_STANDARD_11AC));
        assertTrue(caps.isWifiStandardSupported(ScanResult.WIFI_STANDARD_11AX));
        if (SdkLevel.isAtLeastT()) {
            assertTrue(caps.isWifiStandardSupported(ScanResult.WIFI_STANDARD_11BE));
        }

        assertTrue(caps.isChannelWidthSupported(ScanResult.CHANNEL_WIDTH_20MHZ));
        // 40MHz and 80MHz are true because 11N/AC/AX/BE are supported
        assertTrue(caps.isChannelWidthSupported(ScanResult.CHANNEL_WIDTH_40MHZ));
        assertTrue(caps.isChannelWidthSupported(ScanResult.CHANNEL_WIDTH_80MHZ));
        assertTrue(caps.isChannelWidthSupported(ScanResult.CHANNEL_WIDTH_160MHZ));
        assertTrue(caps.isChannelWidthSupported(ScanResult.CHANNEL_WIDTH_80MHZ_PLUS_MHZ));
        if (SdkLevel.isAtLeastT()) {
            assertTrue(caps.isChannelWidthSupported(ScanResult.CHANNEL_WIDTH_320MHZ));
        }

        assertEquals(TEST_TX_STREAMS, caps.getMaxNumberTxSpatialStreams());
        assertEquals(TEST_RX_STREAMS, caps.getMaxNumberRxSpatialStreams());
        if (SdkLevel.isAtLeastV()) {
            assertEquals(TEST_AKMS, caps.getMaxNumberAkms());
        }
    }

    @Test
    public void testBuilderWifiStandardSupport() {
        DeviceWiphyCapabilities caps = new DeviceWiphyCapabilities.Builder()
                .setWifiStandardSupport(ScanResult.WIFI_STANDARD_11N, true)
                .build();
        assertTrue(caps.isWifiStandardSupported(ScanResult.WIFI_STANDARD_11N));

        caps = DeviceWiphyCapabilities.Builder.createFromDeviceWiphyCapabilities(caps)
                .setWifiStandardSupport(ScanResult.WIFI_STANDARD_11N, false)
                .build();
        assertFalse(caps.isWifiStandardSupported(ScanResult.WIFI_STANDARD_11N));

        // Test 11AC
        caps = new DeviceWiphyCapabilities.Builder()
                .setWifiStandardSupport(ScanResult.WIFI_STANDARD_11AC, true)
                .build();
        assertTrue(caps.isWifiStandardSupported(ScanResult.WIFI_STANDARD_11AC));
        caps = DeviceWiphyCapabilities.Builder.createFromDeviceWiphyCapabilities(caps)
                .setWifiStandardSupport(ScanResult.WIFI_STANDARD_11AC, false)
                .build();
        assertFalse(caps.isWifiStandardSupported(ScanResult.WIFI_STANDARD_11AC));

        // Test 11AX
        caps = new DeviceWiphyCapabilities.Builder()
                .setWifiStandardSupport(ScanResult.WIFI_STANDARD_11AX, true)
                .build();
        assertTrue(caps.isWifiStandardSupported(ScanResult.WIFI_STANDARD_11AX));
        caps = DeviceWiphyCapabilities.Builder.createFromDeviceWiphyCapabilities(caps)
                .setWifiStandardSupport(ScanResult.WIFI_STANDARD_11AX, false)
                .build();
        assertFalse(caps.isWifiStandardSupported(ScanResult.WIFI_STANDARD_11AX));

        // Test 11BE
        caps = new DeviceWiphyCapabilities.Builder()
                .setWifiStandardSupport(ScanResult.WIFI_STANDARD_11BE, true)
                .build();
        assertTrue(caps.isWifiStandardSupported(ScanResult.WIFI_STANDARD_11BE));
        caps = DeviceWiphyCapabilities.Builder.createFromDeviceWiphyCapabilities(caps)
                .setWifiStandardSupport(ScanResult.WIFI_STANDARD_11BE, false)
                .build();
        assertFalse(caps.isWifiStandardSupported(ScanResult.WIFI_STANDARD_11BE));

        // Test legacy (always true)
        caps = new DeviceWiphyCapabilities.Builder().build();
        assertTrue(caps.isWifiStandardSupported(ScanResult.WIFI_STANDARD_LEGACY));

        // Test invalid standard
        assertFalse(caps.isWifiStandardSupported(-1));
    }

    @Test
    public void testBuilderChannelWidthSupport() {
        DeviceWiphyCapabilities caps = new DeviceWiphyCapabilities.Builder().build();

        // 20MHz is always supported
        assertTrue(caps.isChannelWidthSupported(ScanResult.CHANNEL_WIDTH_20MHZ));

        // Test 40MHz depends on 11N/AC/AX/BE
        caps = new DeviceWiphyCapabilities.Builder()
                .setWifiStandardSupport(ScanResult.WIFI_STANDARD_11N, true)
                .build();
        assertTrue(caps.isChannelWidthSupported(ScanResult.CHANNEL_WIDTH_40MHZ));
        caps = DeviceWiphyCapabilities.Builder.createFromDeviceWiphyCapabilities(caps)
                .setWifiStandardSupport(ScanResult.WIFI_STANDARD_11N, false)
                .build();
        assertFalse(caps.isChannelWidthSupported(ScanResult.CHANNEL_WIDTH_40MHZ));

        // Test 80MHz depends on 11AC/AX/BE
        caps = new DeviceWiphyCapabilities.Builder()
                .setWifiStandardSupport(ScanResult.WIFI_STANDARD_11AC, true)
                .build();
        assertTrue(caps.isChannelWidthSupported(ScanResult.CHANNEL_WIDTH_80MHZ));
        caps = DeviceWiphyCapabilities.Builder.createFromDeviceWiphyCapabilities(caps)
                .setWifiStandardSupport(ScanResult.WIFI_STANDARD_11AC, false)
                .build();
        assertFalse(caps.isChannelWidthSupported(ScanResult.CHANNEL_WIDTH_80MHZ));

        // Test 160MHz
        caps = new DeviceWiphyCapabilities.Builder()
                .setChannelWidthSupported(ScanResult.CHANNEL_WIDTH_160MHZ, true)
                .build();
        assertTrue(caps.isChannelWidthSupported(ScanResult.CHANNEL_WIDTH_160MHZ));
        caps = DeviceWiphyCapabilities.Builder.createFromDeviceWiphyCapabilities(caps)
                .setChannelWidthSupported(ScanResult.CHANNEL_WIDTH_160MHZ, false)
                .build();
        assertFalse(caps.isChannelWidthSupported(ScanResult.CHANNEL_WIDTH_160MHZ));

        // Test 80+80MHz
        caps = new DeviceWiphyCapabilities.Builder()
                .setChannelWidthSupported(ScanResult.CHANNEL_WIDTH_80MHZ_PLUS_MHZ, true)
                .build();
        assertTrue(caps.isChannelWidthSupported(ScanResult.CHANNEL_WIDTH_80MHZ_PLUS_MHZ));
        caps = DeviceWiphyCapabilities.Builder.createFromDeviceWiphyCapabilities(caps)
                .setChannelWidthSupported(ScanResult.CHANNEL_WIDTH_80MHZ_PLUS_MHZ, false)
                .build();
        assertFalse(caps.isChannelWidthSupported(ScanResult.CHANNEL_WIDTH_80MHZ_PLUS_MHZ));

        // Test 320MHz
        caps = new DeviceWiphyCapabilities.Builder()
                .setChannelWidthSupported(ScanResult.CHANNEL_WIDTH_320MHZ, true)
                .build();
        assertTrue(caps.isChannelWidthSupported(ScanResult.CHANNEL_WIDTH_320MHZ));
        caps = DeviceWiphyCapabilities.Builder.createFromDeviceWiphyCapabilities(caps)
                .setChannelWidthSupported(ScanResult.CHANNEL_WIDTH_320MHZ, false)
                .build();
        assertFalse(caps.isChannelWidthSupported(ScanResult.CHANNEL_WIDTH_320MHZ));

        // Test invalid channel width
        assertFalse(caps.isChannelWidthSupported(-1));
    }

    @Test
    public void testBuilderSpatialStreams() {
        DeviceWiphyCapabilities caps = new DeviceWiphyCapabilities.Builder()
                .setMaxNumberTxSpatialStreams(TEST_TX_STREAMS)
                .setMaxNumberRxSpatialStreams(TEST_RX_STREAMS)
                .build();

        assertEquals(TEST_TX_STREAMS, caps.getMaxNumberTxSpatialStreams());
        assertEquals(TEST_RX_STREAMS, caps.getMaxNumberRxSpatialStreams());
    }

    @Test
    public void testBuilderAkms() {
        assumeTrue(SdkLevel.isAtLeastV());
        DeviceWiphyCapabilities caps = new DeviceWiphyCapabilities.Builder()
                .setMaxNumberAkms(TEST_AKMS)
                .build();

        assertEquals(TEST_AKMS, caps.getMaxNumberAkms());
    }

    @Test
    public void testEqualsAndHashCode() {
        DeviceWiphyCapabilities caps1 = new DeviceWiphyCapabilities.Builder().build();
        DeviceWiphyCapabilities caps2 = new DeviceWiphyCapabilities.Builder().build();

        // Initially equal
        assertEquals(caps1, caps2);
        assertEquals(caps1.hashCode(), caps2.hashCode());

        // Change one field in caps1
        caps1 = DeviceWiphyCapabilities.Builder.createFromDeviceWiphyCapabilities(caps1)
                .setWifiStandardSupport(ScanResult.WIFI_STANDARD_11N, true)
                .build();
        assertNotEquals(caps1, caps2);
        assertNotEquals(caps1.hashCode(), caps2.hashCode()); // Hash codes should differ

        // Make caps2 equal to caps1
        caps2 = DeviceWiphyCapabilities.Builder.createFromDeviceWiphyCapabilities(caps2)
                .setWifiStandardSupport(ScanResult.WIFI_STANDARD_11N, true)
                .build();
        assertEquals(caps1, caps2);
        assertEquals(caps1.hashCode(), caps2.hashCode());

        // Change another field
        caps1 = DeviceWiphyCapabilities.Builder.createFromDeviceWiphyCapabilities(caps1)
                .setMaxNumberTxSpatialStreams(TEST_TX_STREAMS)
                .build();
        assertNotEquals(caps1, caps2);
        assertNotEquals(caps1.hashCode(), caps2.hashCode());

        // Test with null
        assertNotEquals(caps1, null);

        // Test with different class
        assertNotEquals(caps1, new Object());
    }
}
