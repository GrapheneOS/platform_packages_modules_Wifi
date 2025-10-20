/*
 * Copyright (C) 2019 The Android Open Source Project
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

import static android.net.wifi.p2p.WifiP2pConfig.GROUP_CLIENT_IP_PROVISIONING_MODE_IPV4_DHCP;
import static android.net.wifi.p2p.WifiP2pConfig.GROUP_CLIENT_IP_PROVISIONING_MODE_IPV6_LINK_LOCAL;
import static android.net.wifi.p2p.WifiP2pConfig.P2P_VERSION_2;
import static android.net.wifi.p2p.WifiP2pConfig.PCC_MODE_CONNECTION_TYPE_LEGACY_OR_R2;
import static android.net.wifi.p2p.WifiP2pConfig.PCC_MODE_CONNECTION_TYPE_R2_ONLY;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import android.net.MacAddress;
import android.net.wifi.OuiKeyedDataUtil;
import android.net.wifi.util.Environment;
import android.os.Parcel;

import androidx.test.filters.SmallTest;

import com.android.modules.utils.build.SdkLevel;

import org.junit.Test;

/**
 * Unit test harness for {@link android.net.wifi.p2p.WifiP2pConfig}
 */
@SmallTest
public class WifiP2pConfigTest {

    private static final String DEVICE_ADDRESS = "aa:bb:cc:dd:ee:ff";
    private static final String TEST_NETWORK_NAME = "DIRECT-xy-Android";
    private static final String TEST_PASSPHRASE = "password";
    private static final String TEST_PIN = "123456";
    private static final int TEST_DISCOVERY_CHANNEL_MHZ = 2437;
    /**
     * Check network name setter
     */
    @Test
    public void testBuilderInvalidNetworkName() throws Exception {
        WifiP2pConfig.Builder b = new WifiP2pConfig.Builder();

        // sunny case
        try {
            b.setNetworkName("DIRECT-ab-Hello");
        } catch (IllegalArgumentException e) {
            fail("Unexpected IllegalArgumentException");
        }

        // sunny case, no trailing string
        try {
            b.setNetworkName("DIRECT-WR");
        } catch (IllegalArgumentException e) {
            fail("Unexpected IllegalArgumentException");
        }

        // sunny case with maximum bytes for the network name
        try {
            b.setNetworkName("DIRECT-abcdefghijklmnopqrstuvwxy");
        } catch (IllegalArgumentException e) {
            fail("Unexpected IllegalArgumentException");
        }

        // less than 9 characters.
        try {
            b.setNetworkName("DIRECT-z");
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException e) { }

        // not starts with DIRECT-xy.
        try {
            b.setNetworkName("ABCDEFGHIJK");
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException e) { }

        // not starts with uppercase DIRECT-xy
        try {
            b.setNetworkName("direct-ab");
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException e) { }

        // x and y are not selected from upper case letters, lower case letters or
        // numbers.
        try {
            b.setNetworkName("direct-a?");
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException e) { }

        // over maximum bytes
        try {
            b.setNetworkName("DIRECT-abcdefghijklmnopqrstuvwxyz");
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException e) { }
    }

    /**
     * Check passphrase setter
     */
    @Test
    public void testBuilderInvalidPassphrase() throws Exception {
        WifiP2pConfig.Builder b = new WifiP2pConfig.Builder();
        // sunny case
        try {
            b.setPassphrase(TEST_PASSPHRASE);
        } catch (IllegalArgumentException e) {
            throw new AssertionError("the test failed", e);
        }

        // sunny case - password length of less than 128bytes
        try {
            b.setPassphrase("abed");
        } catch (IllegalArgumentException e) {
            throw new AssertionError("the test failed", e);
        }

        // null string.
        try {
            b.setPassphrase(null);
            fail("should throw IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected exception.
        } catch (NullPointerException e) {
            // expected exception.
        }

        // empty string.
        try {
            b.setPassphrase("");
            fail("should throw IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected exception.
        }

        // Password length of more than 128bytes .
        try {
            b.setPassphrase("j7YxZqK2gD5fT8rN9bW6hL0vQ3pO1mK4jU7iY9zX8cV5bN2hG1fS6dJ3kH0g"
                    + "L9wQ8rP7oM6nN5lK4mJ3iO2uY1tX0zW9vU8hG7fS6eD5cR4baa7YxZqK2gD5fT8rN9"
                    + "bW6hL0vQ2sweder");
            fail("should throw IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected exception.
        }

        WifiP2pConfig.Builder c = new WifiP2pConfig.Builder();

        // sunny case
        try {
            c.setDeviceAddress(MacAddress.fromString(DEVICE_ADDRESS))
                    .setNetworkName(TEST_NETWORK_NAME)
                    .setPassphrase(TEST_PASSPHRASE)
                    .build();
        } catch (IllegalArgumentException e) {
            throw new AssertionError("the test failed", e);
        }

        // less than 8 characters.
        try {
            c.setDeviceAddress(MacAddress.fromString(DEVICE_ADDRESS))
                    .setNetworkName(TEST_NETWORK_NAME)
                    .setPassphrase("12abide")
                    .build();
            fail("should throw IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected exception.
        }

        // more than 63 characters.
        try {
            c.setDeviceAddress(MacAddress.fromString(DEVICE_ADDRESS))
                    .setNetworkName(TEST_NETWORK_NAME)
                    .setPassphrase("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQ"
                            + "RSTUVWXYZ1234567890+/")
                    .build();
            fail("should throw IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected exception.
        }
    }

    /**
     * Check Pcc Mode passphrase setter
     */
    @Test
    public void testPccModeBuilderSetterInvalidPassphrase() throws Exception {
        assumeTrue(Environment.isSdkAtLeastB());

        WifiP2pConfig.Builder c = new WifiP2pConfig.Builder();

        // sunny case
        try {
            c.setDeviceAddress(MacAddress.fromString(DEVICE_ADDRESS))
                    .setNetworkName(TEST_NETWORK_NAME)
                    .setPassphrase(TEST_PASSPHRASE)
                    .setPccModeConnectionType(PCC_MODE_CONNECTION_TYPE_LEGACY_OR_R2)
                    .build();
        } catch (IllegalArgumentException e) {
            throw new AssertionError("the test failed", e);
        }

        // more than 63 characters in PCC Mode is not allowed.
        try {
            c.setDeviceAddress(MacAddress.fromString(DEVICE_ADDRESS))
                    .setNetworkName(TEST_NETWORK_NAME)
                    .setPassphrase("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQ"
                            + "RSTUVWXYZ1234567890+/")
                    .setPccModeConnectionType(PCC_MODE_CONNECTION_TYPE_LEGACY_OR_R2)
                    .build();
            fail("should throw IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected exception.
        }

        // less than 8 characters in PCC Mode is not allowed.
        try {
            c.setDeviceAddress(MacAddress.fromString(DEVICE_ADDRESS))
                    .setNetworkName(TEST_NETWORK_NAME)
                    .setPassphrase("12abcde")
                    .setPccModeConnectionType(PCC_MODE_CONNECTION_TYPE_LEGACY_OR_R2)
                    .build();
            fail("should throw IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected exception.
        }

        // less than 8 characters is allowed in R2 only mode.
        try {
            c.setDeviceAddress(MacAddress.fromString(DEVICE_ADDRESS))
                    .setNetworkName(TEST_NETWORK_NAME)
                    .setPassphrase("12")
                    .setPccModeConnectionType(PCC_MODE_CONNECTION_TYPE_R2_ONLY)
                    .build();
        } catch (IllegalArgumentException e) {
            throw new AssertionError("the test failed", e);
        }

        // more than 8 characters is allowed in R2 only mode.
        try {
            c.setDeviceAddress(MacAddress.fromString(DEVICE_ADDRESS))
                    .setNetworkName(TEST_NETWORK_NAME)
                    .setPassphrase("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQ"
                            + "RSTUVWXYZ1234567890+/")
                    .setPccModeConnectionType(PCC_MODE_CONNECTION_TYPE_R2_ONLY)
                    .build();
        } catch (IllegalArgumentException e) {
            throw new AssertionError("the test failed", e);
        }
    }

    /** Verify that a default config can be built. */
    @Test
    public void testBuildDefaultConfig() {
        WifiP2pConfig c = new WifiP2pConfig.Builder()
                .setDeviceAddress(MacAddress.fromString(DEVICE_ADDRESS)).build();
        assertEquals(c.deviceAddress, DEVICE_ADDRESS);
    }

    /** Verify that a non-persistent config can be built. */
    @Test
    public void testBuildNonPersistentConfig() throws Exception {
        WifiP2pConfig c = new WifiP2pConfig.Builder()
                .setDeviceAddress(MacAddress.fromString(DEVICE_ADDRESS))
                .enablePersistentMode(false).build();
        assertEquals(c.deviceAddress, DEVICE_ADDRESS);
        assertEquals(WifiP2pGroup.NETWORK_ID_TEMPORARY, c.netId);
    }

    /** Verify that a config by default has group client IP provisioning with DHCP IPv4. */
    @Test
    public void testBuildConfigWithGroupClientIpProvisioningModeDefault() throws Exception {
        WifiP2pConfig c = new WifiP2pConfig.Builder()
                .setDeviceAddress(MacAddress.fromString(DEVICE_ADDRESS))
                .build();
        assertEquals(c.deviceAddress, DEVICE_ADDRESS);
        assertEquals(c.getGroupClientIpProvisioningMode(),
                GROUP_CLIENT_IP_PROVISIONING_MODE_IPV4_DHCP);
    }

    /** Verify that a config with group client IP provisioning with IPv4 DHCP can be built. */
    @Test
    public void testBuildConfigWithGroupClientIpProvisioningModeIpv4Dhcp() throws Exception {
        assumeTrue(SdkLevel.isAtLeastT());
        WifiP2pConfig c = new WifiP2pConfig.Builder()
                .setDeviceAddress(MacAddress.fromString(DEVICE_ADDRESS))
                .setGroupClientIpProvisioningMode(GROUP_CLIENT_IP_PROVISIONING_MODE_IPV4_DHCP)
                .build();
        assertEquals(c.deviceAddress, DEVICE_ADDRESS);
        assertEquals(c.getGroupClientIpProvisioningMode(),
                GROUP_CLIENT_IP_PROVISIONING_MODE_IPV4_DHCP);
    }

    /** Verify that a config with group client IP provisioning with IPv6 link-local can be built. */
    @Test
    public void testBuildConfigWithGroupClientIpProvisioningModeIpv6LinkLocal() throws Exception {
        assumeTrue(SdkLevel.isAtLeastT());
        WifiP2pConfig c = new WifiP2pConfig.Builder()
                .setDeviceAddress(MacAddress.fromString(DEVICE_ADDRESS))
                .setGroupClientIpProvisioningMode(GROUP_CLIENT_IP_PROVISIONING_MODE_IPV6_LINK_LOCAL)
                .build();
        assertEquals(c.deviceAddress, DEVICE_ADDRESS);
        assertEquals(c.getGroupClientIpProvisioningMode(),
                GROUP_CLIENT_IP_PROVISIONING_MODE_IPV6_LINK_LOCAL);
    }

    /**
     * Verify that the builder throws IllegalArgumentException if invalid group client IP
     * provisioning mode is set.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testBuilderWithInvalidGroupClientIpProvisioningMode()
            throws Exception {
        assumeTrue(SdkLevel.isAtLeastT());
        WifiP2pConfig c = new WifiP2pConfig.Builder().setGroupClientIpProvisioningMode(5).build();
    }

    /**
     * Verify that the builder throws IllegalStateException if none of
     * network name, passphrase, and device address is set.
     */
    @Test(expected = IllegalStateException.class)
    public void testBuildThrowIllegalStateExceptionWithoutNetworkNamePassphraseDeviceAddress()
            throws Exception {
        WifiP2pConfig c = new WifiP2pConfig.Builder().build();
    }

    /**
     * Verify that the builder throws IllegalStateException if only network name is set.
     */
    @Test(expected = IllegalStateException.class)
    public void testBuildThrowIllegalStateExceptionWithOnlyNetworkName()
            throws Exception {
        WifiP2pConfig c = new WifiP2pConfig.Builder().setNetworkName("DIRECT-ab-Hello").build();
    }

    /**
     * Verify that the builder throws IllegalStateException if only passphrase is set.
     */
    @Test(expected = IllegalStateException.class)
    public void testBuildThrowIllegalStateExceptionWithOnlyPassphrase()
            throws Exception {
        WifiP2pConfig c = new WifiP2pConfig.Builder().setPassphrase("12345677").build();
    }


    /** Verify that a config by default has join existing group field set to false */
    @Test
    public void testBuildConfigWithJoinExistingGroupDefault() throws Exception {
        WifiP2pConfig c = new WifiP2pConfig.Builder()
                .setDeviceAddress(MacAddress.fromString(DEVICE_ADDRESS))
                .build();
        assertEquals(c.deviceAddress, DEVICE_ADDRESS);
        assertFalse(c.isJoinExistingGroup());
    }

    /** Verify that a config with join existing group field can be built. */
    @Test
    public void testBuildConfigWithJoinExistingGroupSet() throws Exception {
        WifiP2pConfig c = new WifiP2pConfig.Builder()
                .setDeviceAddress(MacAddress.fromString(DEVICE_ADDRESS))
                .setJoinExistingGroup(true)
                .build();
        assertEquals(c.deviceAddress, DEVICE_ADDRESS);
        assertTrue(c.isJoinExistingGroup());
    }

    @Test
    /*
     * Verify WifiP2pConfig basic operations
     */
    public void testWifiP2pConfig() throws Exception {
        WifiP2pConfig config = new WifiP2pConfig();
        config.deviceAddress = DEVICE_ADDRESS;
        if (SdkLevel.isAtLeastV()) {
            config.setVendorData(OuiKeyedDataUtil.createTestOuiKeyedDataList(5));
        }

        WifiP2pConfig copiedConfig = new WifiP2pConfig(config);
        // no equals operator, use toString for comparison.
        assertEquals(config.toString(), copiedConfig.toString());

        Parcel parcelW = Parcel.obtain();
        config.writeToParcel(parcelW, 0);
        byte[] bytes = parcelW.marshall();
        parcelW.recycle();

        Parcel parcelR = Parcel.obtain();
        parcelR.unmarshall(bytes, 0, bytes.length);
        parcelR.setDataPosition(0);
        WifiP2pConfig configFromParcel = WifiP2pConfig.CREATOR.createFromParcel(parcelR);

        if (SdkLevel.isAtLeastV()) {
            assertTrue(config.getVendorData().equals(configFromParcel.getVendorData()));
        }
        // no equals operator, use toString for comparison.
        assertEquals(config.toString(), configFromParcel.toString());
    }

    @Test
    /*
     * Verify WifiP2pConfig invalidate API
     */
    public void testInvalidate() throws Exception {
        WifiP2pConfig config = new WifiP2pConfig();
        config.deviceAddress = DEVICE_ADDRESS;
        config.invalidate();
        assertEquals("", config.deviceAddress);
    }

    /** Verify that a config with the PCC Mode connection type field can be built. */
    @Test
    public void testBuildConfigWithPccModeConnectionType() throws Exception {
        assumeTrue(Environment.isSdkAtLeastB());
        WifiP2pConfig c = new WifiP2pConfig.Builder()
                .setDeviceAddress(MacAddress.fromString(DEVICE_ADDRESS))
                .setPccModeConnectionType(PCC_MODE_CONNECTION_TYPE_LEGACY_OR_R2)
                .build();
        assertEquals(c.deviceAddress, DEVICE_ADDRESS);
        assertEquals(PCC_MODE_CONNECTION_TYPE_LEGACY_OR_R2, c.getPccModeConnectionType());
    }

    /** Verify that a config with the group owner version field can be built. */
    @Test
    public void testBuildConfigWithGroupOwnerVersion() throws Exception {
        assumeTrue(Environment.isSdkAtLeastB());
        WifiP2pConfig c = new WifiP2pConfig.Builder()
                .setDeviceAddress(MacAddress.fromString(DEVICE_ADDRESS))
                .build();
        c.setGroupOwnerVersion(P2P_VERSION_2);
        assertEquals(c.deviceAddress, DEVICE_ADDRESS);
        assertEquals(PCC_MODE_CONNECTION_TYPE_LEGACY_OR_R2, c.getGroupOwnerVersion());
    }

    /** Verify that a config pairing bootstrapping configuration can be built. */
    @Test
    public void testBuildConfigWithPairingBootstrappingConfig() throws Exception {
        assumeTrue(Environment.isSdkAtLeastB());
        WifiP2pPairingBootstrappingConfig expectedPairingBootstrappingConfig =
                new WifiP2pPairingBootstrappingConfig(WifiP2pPairingBootstrappingConfig
                .PAIRING_BOOTSTRAPPING_METHOD_DISPLAY_PINCODE, TEST_PIN);
        WifiP2pConfig c = new WifiP2pConfig.Builder()
                .setDeviceAddress(MacAddress.fromString(DEVICE_ADDRESS))
                .setPairingBootstrappingConfig(expectedPairingBootstrappingConfig)
                .build();
        assertEquals(c.deviceAddress, DEVICE_ADDRESS);
        WifiP2pPairingBootstrappingConfig pairingBootstrappingConfig =
                c.getPairingBootstrappingConfig();
        assertNotNull(pairingBootstrappingConfig);
        assertEquals(expectedPairingBootstrappingConfig, pairingBootstrappingConfig);
        assertEquals(c.getGroupClientIpProvisioningMode(),
                GROUP_CLIENT_IP_PROVISIONING_MODE_IPV6_LINK_LOCAL);
    }

    /**
     * Verify that a config with the request to authorize a connection request from a peer device
     * can be built.
     */
    @Test
    public void testBuildConfigWithAuthorizeConnectionFromPeer() throws Exception {
        assumeTrue(Environment.isSdkAtLeastB());
        WifiP2pPairingBootstrappingConfig expectedPairingBootstrappingConfig =
                new WifiP2pPairingBootstrappingConfig(WifiP2pPairingBootstrappingConfig
                        .PAIRING_BOOTSTRAPPING_METHOD_OUT_OF_BAND, TEST_PASSPHRASE);
        WifiP2pConfig c = new WifiP2pConfig.Builder()
                .setDeviceAddress(MacAddress.fromString(DEVICE_ADDRESS))
                .setPairingBootstrappingConfig(expectedPairingBootstrappingConfig)
                .setGroupOperatingFrequency(2437)
                .setAuthorizeConnectionFromPeerEnabled(true)
                .build();
        WifiP2pPairingBootstrappingConfig pairingBootstrappingConfig =
                c.getPairingBootstrappingConfig();
        assertNotNull(pairingBootstrappingConfig);
        assertEquals(expectedPairingBootstrappingConfig, pairingBootstrappingConfig);
        assertTrue(c.isAuthorizeConnectionFromPeerEnabled());
    }

    /** Verify that a config with the discovery channel can be built. */
    @Test
    public void testBuildConfigWithDiscoveryChannelForOobPairingBootstrapping() throws Exception {
        assumeTrue(Environment.isSdkNewerThanB());
        WifiP2pPairingBootstrappingConfig expectedPairingBootstrappingConfig =
                new WifiP2pPairingBootstrappingConfig(WifiP2pPairingBootstrappingConfig
                        .PAIRING_BOOTSTRAPPING_METHOD_OUT_OF_BAND, TEST_PASSPHRASE);
        WifiP2pConfig c = new WifiP2pConfig.Builder()
                .setDeviceAddress(MacAddress.fromString(DEVICE_ADDRESS))
                .setPairingBootstrappingConfig(expectedPairingBootstrappingConfig)
                .setPairingDiscoveryChannelFrequencyMhz(TEST_DISCOVERY_CHANNEL_MHZ)
                .build();
        assertEquals(TEST_DISCOVERY_CHANNEL_MHZ, c.getPairingDiscoveryChannelFrequencyMhz());
    }

    /**
     * Verify that the builder throws IllegalStateException if discovery channel is set without
     * a pairing bootstrapping config.
     */
    @Test(expected = IllegalStateException.class)
    public void testBuildThrowIllegalStateExceptionWithDiscoveryChannelWithoutPairingConfig()
            throws Exception {
        assumeTrue(Environment.isSdkNewerThanB());
        new WifiP2pConfig.Builder()
                .setDeviceAddress(MacAddress.fromString(DEVICE_ADDRESS))
                .setPairingDiscoveryChannelFrequencyMhz(TEST_DISCOVERY_CHANNEL_MHZ)
                .build();
    }

    /**
     * Verify that the builder throws IllegalStateException if discovery channel is set with a
     * pairing method other than OUT_OF_BAND.
     */
    @Test(expected = IllegalStateException.class)
    public void testBuildThrowIllegalStateExceptionWithDiscoveryChannelWithInvalidMethod()
            throws Exception {
        assumeTrue(Environment.isSdkNewerThanB());
        WifiP2pPairingBootstrappingConfig expectedPairingBootstrappingConfig =
                new WifiP2pPairingBootstrappingConfig(WifiP2pPairingBootstrappingConfig
                        .PAIRING_BOOTSTRAPPING_METHOD_DISPLAY_PINCODE, TEST_PIN);
        new WifiP2pConfig.Builder()
                .setDeviceAddress(MacAddress.fromString(DEVICE_ADDRESS))
                .setPairingBootstrappingConfig(expectedPairingBootstrappingConfig)
                .setPairingDiscoveryChannelFrequencyMhz(TEST_DISCOVERY_CHANNEL_MHZ)
                .build();
    }
}
