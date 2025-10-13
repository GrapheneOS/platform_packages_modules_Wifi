/*
 * Copyright (C) 2024 The Android Open Source Project
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

package android.net.wifi.p2p;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assume.assumeTrue;

import android.net.wifi.ScanResult;
import android.net.wifi.util.Environment;

import androidx.test.filters.SmallTest;

import org.junit.Assert;
import org.junit.Test;
/**
 * Unit tests for {@link WifiP2pUsdBasedServiceDiscoveryConfig}
 */
@SmallTest
public final class WifiP2pUsdBasedServiceDiscoveryConfigTest {
    private static final int[] TEST_USD_DISCOVERY_CHANNEL_FREQUENCIES_MHZ = {2412, 2437, 2462};
    private static final int TEST_USD_DEFAULT_DISCOVERY_CHANNEL_MHZ = 2437;
    @Test
    public void testWifiP2pUsdBasedServiceDiscoveryConfig() {
        assumeTrue(Environment.isSdkAtLeastB());
        WifiP2pUsdBasedServiceDiscoveryConfig serviceDiscoveryConfig =
                new WifiP2pUsdBasedServiceDiscoveryConfig.Builder()
                        .setFrequenciesMhz(TEST_USD_DISCOVERY_CHANNEL_FREQUENCIES_MHZ).build();
        assertNotNull(serviceDiscoveryConfig);
        assertArrayEquals(TEST_USD_DISCOVERY_CHANNEL_FREQUENCIES_MHZ,
                serviceDiscoveryConfig.getFrequenciesMhz());
        assertEquals(ScanResult.UNSPECIFIED, serviceDiscoveryConfig.getBand());
    }

    @Test
    public void testBuilderDefaultBehavior() {
        assumeTrue(Environment.isSdkAtLeastB());
        WifiP2pUsdBasedServiceDiscoveryConfig config =
                new WifiP2pUsdBasedServiceDiscoveryConfig.Builder().build();

        assertNotNull(config);
        assertEquals(ScanResult.UNSPECIFIED, config.getBand());
        assertArrayEquals(new int[]{TEST_USD_DEFAULT_DISCOVERY_CHANNEL_MHZ},
                config.getFrequenciesMhz());
    }

    @Test
    public void testSetBandClearsFrequencies() {
        assumeTrue(Environment.isSdkAtLeastB());
        WifiP2pUsdBasedServiceDiscoveryConfig config =
                new WifiP2pUsdBasedServiceDiscoveryConfig.Builder()
                        .setBand(ScanResult.WIFI_BAND_5_GHZ)
                        .build();

        assertNotNull(config);
        assertEquals(ScanResult.WIFI_BAND_5_GHZ, config.getBand());
        Assert.assertNull(config.getFrequenciesMhz());
    }


    @Test
    public void testSetFrequenciesClearsBand() {
        assumeTrue(Environment.isSdkAtLeastB());
        WifiP2pUsdBasedServiceDiscoveryConfig config =
                new WifiP2pUsdBasedServiceDiscoveryConfig.Builder()
                        .setBand(ScanResult.WIFI_BAND_5_GHZ) // Set band first
                        .setFrequenciesMhz(TEST_USD_DISCOVERY_CHANNEL_FREQUENCIES_MHZ) // Then
                        // set freqs
                        .build();

        assertNotNull(config);
        assertEquals(ScanResult.UNSPECIFIED, config.getBand());
        assertArrayEquals(TEST_USD_DISCOVERY_CHANNEL_FREQUENCIES_MHZ, config.getFrequenciesMhz());
    }

    @Test
    public void testSetBandAfterFrequencies() {
        assumeTrue(Environment.isSdkAtLeastB());
        WifiP2pUsdBasedServiceDiscoveryConfig config =
                new WifiP2pUsdBasedServiceDiscoveryConfig.Builder()
                        .setFrequenciesMhz(TEST_USD_DISCOVERY_CHANNEL_FREQUENCIES_MHZ) // Set
                        // freqs first
                        .setBand(ScanResult.WIFI_BAND_6_GHZ) // Then set band
                        .build();

        assertNotNull(config);
        assertEquals(ScanResult.WIFI_BAND_6_GHZ, config.getBand());
        Assert.assertNull(config.getFrequenciesMhz());
    }

    @Test
    public void testBuildWithInvalidBandThrowsException() {
        assumeTrue(Environment.isSdkAtLeastB());
        WifiP2pUsdBasedServiceDiscoveryConfig.Builder builder =
                new WifiP2pUsdBasedServiceDiscoveryConfig.Builder();
        assertThrows(IllegalArgumentException.class, () -> builder.setBand(99));
    }
}
