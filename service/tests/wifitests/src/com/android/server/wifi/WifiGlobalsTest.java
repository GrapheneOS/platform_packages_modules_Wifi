/*
 * Copyright (C) 2020 The Android Open Source Project
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

import static junit.framework.Assert.assertEquals;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import android.content.pm.PackageManager;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiContext;
import android.net.wifi.util.WifiResourceCache;

import androidx.test.filters.SmallTest;

import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.wifi.flags.Flags;
import com.android.wifi.resources.R;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;

import java.util.List;


/** Unit tests for {@link WifiGlobals} */
@SmallTest
public class WifiGlobalsTest extends WifiBaseTest {

    private WifiGlobals mWifiGlobals;
    private MockResources mResources;
    private WifiResourceCache mWifiResourceCache;

    @Mock private WifiContext mContext;
    @Mock private PackageManager mPackageManager;
    private MockitoSession mSession;

    private static final int TEST_NETWORK_ID = 54;
    private static final String TEST_SSID = "\"GoogleGuest\"";

    @Before
    public void setUp() {
        mSession = ExtendedMockito.mockitoSession()
                .initMocks(this)
                .strictness(Strictness.LENIENT)
                .mockStatic(Flags.class, withSettings().lenient())
                .startMocking();

        mResources = new MockResources();
        mResources.setInteger(R.integer.config_wifiPollRssiIntervalMilliseconds, 3000);
        mResources.setInteger(R.integer.config_wifiClientModeImplNumLogRecs, 200);
        mResources.setBoolean(R.bool.config_wifiSaveFactoryMacToWifiConfigStore, true);
        mResources.setStringArray(R.array.config_wifiForceDisableMacRandomizationSsidPrefixList,
                new String[] {TEST_SSID});
        mResources.setStringArray(R.array.config_wifiAfcServerUrlsForCountry, new String[] {});
        when(mContext.getResources()).thenReturn(mResources);
        mWifiResourceCache = new WifiResourceCache(mContext);
        when(mContext.getResourceCache()).thenReturn(mWifiResourceCache);
        when(mContext.getPackageManager()).thenReturn(mPackageManager);

        mWifiGlobals = new WifiGlobals(mContext);
    }

    @After
    public void tearDown() {
        if (mSession != null) {
            mSession.finishMocking();
        }
    }

    /** Test that the interval for poll RSSI is read from config overlay correctly. */
    @Test
    public void testPollRssiIntervalIsSetCorrectly() throws Exception {
        assertEquals(3000, mWifiGlobals.getPollRssiIntervalMillis());
        mResources.setInteger(R.integer.config_wifiPollRssiIntervalMilliseconds, 9000);
        mWifiResourceCache.reset();
        assertEquals(9000, new WifiGlobals(mContext).getPollRssiIntervalMillis());
    }

    /** Verify that Bluetooth active is set correctly with BT state/connection state changes */
    @Test
    public void verifyBluetoothStateAndConnectionStateChanges() {
        mWifiGlobals.setBluetoothEnabled(true);
        assertThat(mWifiGlobals.isBluetoothConnected()).isFalse();

        mWifiGlobals.setBluetoothConnected(true);
        assertThat(mWifiGlobals.isBluetoothConnected()).isTrue();

        mWifiGlobals.setBluetoothEnabled(false);
        assertThat(mWifiGlobals.isBluetoothConnected()).isFalse();

        mWifiGlobals.setBluetoothEnabled(true);
        assertThat(mWifiGlobals.isBluetoothConnected()).isFalse();

        mWifiGlobals.setBluetoothConnected(true);
        assertThat(mWifiGlobals.isBluetoothConnected()).isTrue();

        mWifiGlobals.setBluetoothConnected(false);
        assertThat(mWifiGlobals.isBluetoothConnected()).isFalse();

        mWifiGlobals.setBluetoothConnected(true);
        assertThat(mWifiGlobals.isBluetoothConnected()).isTrue();
    }

    /** Verify SAE Hash-to-Element overlay. */
    @Test
    public void testSaeH2eSupportOverlay() {
        mResources.setBoolean(R.bool.config_wifiSaeH2eSupported, false);
        mWifiGlobals = new WifiGlobals(mContext);
        assertFalse(mWifiGlobals.isWpa3SaeH2eSupported());

        mResources.setBoolean(R.bool.config_wifiSaeH2eSupported, true);
        mWifiResourceCache.reset();
        mWifiGlobals = new WifiGlobals(mContext);
        assertTrue(mWifiGlobals.isWpa3SaeH2eSupported());
    }

    /** Verify P2P device name customization. */
    @Test
    public void testP2pDeviceNameCustomization() {
        final String customPrefix = "Custom-";
        final int customPostfixDigit = 5;
        mResources.setString(R.string.config_wifiP2pDeviceNamePrefix, customPrefix);
        mResources.setInteger(R.integer.config_wifiP2pDeviceNamePostfixNumDigits,
                customPostfixDigit);
        mWifiGlobals = new WifiGlobals(mContext);
        assertEquals(customPrefix, mWifiGlobals.getWifiP2pDeviceNamePrefix());
        assertEquals(customPostfixDigit, mWifiGlobals.getWifiP2pDeviceNamePostfixNumDigits());
    }

    /** Test that the number of log records is read from config overlay correctly. */
    @Test
    public void testNumLogRecsNormalIsSetCorrectly() throws Exception {
        assertEquals(200, mWifiGlobals.getClientModeImplNumLogRecs());
    }

    @Test
    public void testSaveFactoryMacToConfigStoreEnabled() throws Exception {
        assertEquals(true, mWifiGlobals.isSaveFactoryMacToConfigStoreEnabled());
    }

    /**
     * Verify background scan is supported
     */
    @Test
    public void testBackgroundScanSupported() throws Exception {
        mResources.setBoolean(R.bool.config_wifi_background_scan_support, false);
        mWifiGlobals = new WifiGlobals(mContext);
        assertFalse(mWifiGlobals.isBackgroundScanSupported());

        when(mContext.getResourceCache()).thenReturn(new WifiResourceCache(mContext));
        mResources.setBoolean(R.bool.config_wifi_background_scan_support, true);
        mWifiGlobals = new WifiGlobals(mContext);
        assertTrue(mWifiGlobals.isBackgroundScanSupported());
    }

    @Test
    public void testQuotedStringSsidPrefixParsedCorrectly() throws Exception {
        assertEquals(1, mWifiGlobals.getMacRandomizationUnsupportedSsidPrefixes().size());
        assertTrue(mWifiGlobals.getMacRandomizationUnsupportedSsidPrefixes()
                .contains(TEST_SSID.substring(0, TEST_SSID.length() - 1)));
    }

    @Test
    public void testLoadCarrierSpecificEapFailureConfigMap() throws Exception {
        // Test by default there's no override data
        assertEquals(0, mWifiGlobals.getCarrierSpecificEapFailureConfigMapSize());

        // Test config with too few items don't get added.
        mResources.setStringArray(R.array.config_wifiEapFailureConfig,
                new String[] {"1, 2, 3"});
        mWifiResourceCache.reset();
        mWifiGlobals = new WifiGlobals(mContext);
        assertEquals(0, mWifiGlobals.getCarrierSpecificEapFailureConfigMapSize());

        // Test config that fail to parse to int don't get added.
        mResources.setStringArray(R.array.config_wifiEapFailureConfig,
                new String[] {"1839, bad_config,  1, 1, 1440"});
        mWifiResourceCache.reset();
        mWifiGlobals = new WifiGlobals(mContext);
        assertEquals(0, mWifiGlobals.getCarrierSpecificEapFailureConfigMapSize());

        // Test correct config
        mResources.setStringArray(R.array.config_wifiEapFailureConfig,
                new String[] {"1839, 1031,  1, 1, 1440"});
        mWifiResourceCache.reset();
        mWifiGlobals = new WifiGlobals(mContext);
        assertEquals(1, mWifiGlobals.getCarrierSpecificEapFailureConfigMapSize());
        WifiBlocklistMonitor.CarrierSpecificEapFailureConfig config =
                mWifiGlobals.getCarrierSpecificEapFailureConfig(1839, 1031);
        assertTrue(config.displayNotification);
        assertEquals(1, config.threshold);
        assertEquals(1440 * 60 * 1000, config.durationMs);

        // Getting CarrierSpecificEapFailureConfig for an not added reason should return null.
        assertNull(mWifiGlobals.getCarrierSpecificEapFailureConfig(1839, 999));
    }


    /**
     * Test that isDeprecatedSecurityTypeNetwork returns true due to WEP network
     */
    @Test
    public void testDeprecatedNetworkSecurityTypeWep()
            throws Exception {
        mResources.setBoolean(R.bool.config_wifiWepDeprecated, true);
        mWifiGlobals = new WifiGlobals(mContext);
        assertTrue(mWifiGlobals.isWepDeprecated());

        WifiConfiguration config = new WifiConfiguration();
        config.networkId = TEST_NETWORK_ID;
        config.SSID = TEST_SSID;
        config.setSecurityParams(WifiConfiguration.SECURITY_TYPE_WEP);

        assertTrue(mWifiGlobals.isDeprecatedSecurityTypeNetwork(config));
    }

    /**
     * Test that isDeprecatedSecurityTypeNetwork returns true due to WPA-Personal network
     */
    @Test
    public void testDeprecatedNetworkSecurityTypeWpaPersonal()
            throws Exception {
        mResources.setBoolean(R.bool.config_wifiWpaPersonalDeprecated, true);
        mWifiGlobals = new WifiGlobals(mContext);
        assertTrue(mWifiGlobals.isWpaPersonalDeprecated());

        WifiConfiguration config = new WifiConfiguration();
        config.networkId = TEST_NETWORK_ID;
        config.SSID = TEST_SSID;
        config.setSecurityParams(WifiConfiguration.SECURITY_TYPE_PSK);
        config.allowedProtocols.set(WifiConfiguration.Protocol.WPA);
        config.allowedProtocols.clear(WifiConfiguration.Protocol.RSN);

        assertTrue(mWifiGlobals.isDeprecatedSecurityTypeNetwork(config));
    }

    /**
     * Test that the correct AFC server URLs are returned for a country.
     */
    @Test
    public void testAfcServerUrlByCountry() {
        String afcServerUS1 = "https://example.com/";
        String afcServerUS2 = "https://www.google.com/";
        String afcServerUS3 = "https://www.android.com/";
        mResources.setStringArray(R.array.config_wifiAfcServerUrlsForCountry,
                new String[] {"US," + afcServerUS1 + "," + afcServerUS2 + "," + afcServerUS3});
        mWifiResourceCache.reset();
        mWifiGlobals = new WifiGlobals(mContext);
        List<String> afcServersForUS = mWifiGlobals.getAfcServerUrlsForCountry("US");
        assertEquals(3, afcServersForUS.size());
        assertEquals(afcServerUS1, afcServersForUS.get(0));
        assertEquals(afcServerUS2, afcServersForUS.get(1));
        assertEquals(afcServerUS3, afcServersForUS.get(2));
    }

    /**
     * Verify that null is returned when attempting to access the AFC server URL list of a country
     * where AFC is not available.
     */
    @Test
    public void testAfcServerUrlCountryUnavailable() {
        mResources.setStringArray(R.array.config_wifiAfcServerUrlsForCountry, new String[] {});
        mWifiGlobals = new WifiGlobals(mContext);
        assertNull(mWifiGlobals.getAfcServerUrlsForCountry("US"));
    }

    @Test
    public void testSetWepAllowedWhenWepIsDeprecated() {
        mResources.setBoolean(R.bool.config_wifiWepDeprecated, true);
        mWifiGlobals = new WifiGlobals(mContext);
        assertTrue(mWifiGlobals.isWepDeprecated());
        assertFalse(mWifiGlobals.isWepSupported());

        mWifiGlobals.setWepAllowed(true);
        assertTrue(mWifiGlobals.isWepDeprecated());
        assertTrue(mWifiGlobals.isWepAllowed());

        mWifiGlobals.setWepAllowed(false);
        assertTrue(mWifiGlobals.isWepDeprecated());
        assertFalse(mWifiGlobals.isWepAllowed());
    }

    @Test
    public void testSetWepAllowedWhenWepIsNotDeprecated() {
        mResources.setBoolean(R.bool.config_wifiWepAllowedControlSupported, true);
        assertTrue(mWifiGlobals.isWepSupported());
        // Default is not allow
        assertFalse(mWifiGlobals.isWepAllowed());
        assertTrue(mWifiGlobals.isWepDeprecated());
        mWifiGlobals.setWepAllowed(true);
        assertFalse(mWifiGlobals.isWepDeprecated());
        assertTrue(mWifiGlobals.isWepAllowed());

        mWifiGlobals.setWepAllowed(false);
        assertTrue(mWifiGlobals.isWepDeprecated());
        assertFalse(mWifiGlobals.isWepAllowed());

        // Test WEP allowed control is NOT supported.
        mResources.setBoolean(R.bool.config_wifiWepAllowedControlSupported, false);
        mWifiResourceCache.reset();
        // Default is not allow, but don't care it since control is not supported.
        assertFalse(mWifiGlobals.isWepAllowed());
        // But we won't consider WEP is allowed since control is NOT supported.
        // So WEP should be NOT deprecated since config_wifiWepDeprecated is false.
        assertFalse(mWifiGlobals.isWepDeprecated());
    }


    @Test
    public void isSwPnoEnabled() {
        mResources.setBoolean(R.bool.config_wifiSwPnoEnabled, true);
        assertTrue(mWifiGlobals.isSwPnoEnabled());
        mResources.setBoolean(R.bool.config_wifiSwPnoEnabled, false);
        mWifiResourceCache.reset();
        assertFalse(mWifiGlobals.isSwPnoEnabled());
    }

    @Test
    public void testIsD2dSupportedWhenInfraStaDisabled() {
        mResources.setBoolean(R.bool.config_wifiD2dAllowedControlSupportedWhenInfraStaDisabled,
                false);
        mWifiGlobals.setD2dStaConcurrencySupported(true);
        assertFalse(mWifiGlobals.isD2dSupportedWhenInfraStaDisabled());
        mWifiGlobals.setD2dStaConcurrencySupported(false);
        assertFalse(mWifiGlobals.isD2dSupportedWhenInfraStaDisabled());

        mResources.setBoolean(R.bool.config_wifiD2dAllowedControlSupportedWhenInfraStaDisabled,
                true);
        mWifiResourceCache.reset();
        mWifiGlobals.setD2dStaConcurrencySupported(true);
        assertFalse(mWifiGlobals.isD2dSupportedWhenInfraStaDisabled());
        mWifiGlobals.setD2dStaConcurrencySupported(false);
        assertTrue(mWifiGlobals.isD2dSupportedWhenInfraStaDisabled());

        when(Flags.allowD2dWithoutStaOnXr()).thenReturn(true);

        when(mPackageManager.hasSystemFeature(PackageManager.FEATURE_XR_PERIPHERAL))
                .thenReturn(true);
        mWifiGlobals = new WifiGlobals(mContext);
        mWifiGlobals.setD2dStaConcurrencySupported(true);
        assertTrue(mWifiGlobals.isD2dSupportedWhenInfraStaDisabled());

        // Test for non-XR device with allowD2dWithoutStaOnXr flag true
        when(mPackageManager.hasSystemFeature(PackageManager.FEATURE_XR_PERIPHERAL))
                .thenReturn(false);
        mWifiGlobals = new WifiGlobals(mContext);
        mWifiGlobals.setD2dStaConcurrencySupported(true);
        assertFalse(mWifiGlobals.isD2dSupportedWhenInfraStaDisabled());
        mWifiGlobals.setD2dStaConcurrencySupported(false);
        assertTrue(mWifiGlobals.isD2dSupportedWhenInfraStaDisabled());

        // Test for config_wifiD2dAllowedControlSupportedWhenInfraStaDisabled is false
        // with allowD2dWithoutStaOnXr flag true
        mResources.setBoolean(R.bool.config_wifiD2dAllowedControlSupportedWhenInfraStaDisabled,
                false);
        mWifiResourceCache.reset();
        when(mPackageManager.hasSystemFeature(PackageManager.FEATURE_XR_PERIPHERAL))
                .thenReturn(true);
        mWifiGlobals = new WifiGlobals(mContext);
        assertFalse(mWifiGlobals.isD2dSupportedWhenInfraStaDisabled());

        when(Flags.allowD2dWithoutStaOnXr()).thenReturn(false);
        mWifiGlobals = new WifiGlobals(mContext);
        mWifiGlobals.setD2dStaConcurrencySupported(true);
        assertFalse(mWifiGlobals.isD2dSupportedWhenInfraStaDisabled());
    }

    @Test
    public void testIsMLDApSupported() {
        assertFalse(mWifiGlobals.isMLDApSupported());
        mWifiResourceCache.reset();
        mResources.setInteger(R.integer.config_wifiSoftApMaxNumberMLDSupported, 1);
        assertTrue(mWifiGlobals.isMLDApSupported());
    }

    @Test
    public void isPreEvaluationEnabled() {
        mResources.setBoolean(R.bool.config_preEvaluationEnabled, true);
        assertTrue(mWifiGlobals.isPreEvaluationEnabled());
        mWifiResourceCache.reset();
        mResources.setBoolean(R.bool.config_preEvaluationEnabled, false);
        assertFalse(mWifiGlobals.isPreEvaluationEnabled());
    }
}
