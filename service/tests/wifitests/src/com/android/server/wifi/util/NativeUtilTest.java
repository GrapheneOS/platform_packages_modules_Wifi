/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.server.wifi.util;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.net.MacAddress;
import android.net.wifi.SecurityParams;
import android.net.wifi.WifiConfiguration;

import androidx.test.filters.SmallTest;

import com.android.server.wifi.SupplicantStaIfaceHal.StaIfaceReasonCode;
import com.android.server.wifi.WifiBaseTest;
import com.android.server.wifi.WifiConfigurationTestUtil;
import com.android.server.wifi.WifiGlobals;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;

/**
 * Unit tests for {@link com.android.server.wifi.util.NativeUtil}.
 */
@SmallTest
public class NativeUtilTest extends WifiBaseTest {
    /**
     * Test that parsing a typical colon-delimited MAC address works.
     */
    @Test
    public void testMacAddressToByteArray() throws Exception {
        assertArrayEquals(new byte[]{0x61, 0x52, 0x43, 0x34, 0x25, 0x16},
                NativeUtil.macAddressToByteArray("61:52:43:34:25:16"));
    }

    /**
     * Test that parsing an empty MAC address works.
     */
    @Test
    public void testEmptyMacAddressToByteArray() throws Exception {
        assertArrayEquals(new byte[]{0, 0, 0, 0, 0, 0},
                NativeUtil.macAddressToByteArray(""));
        assertArrayEquals(new byte[]{0, 0, 0, 0, 0, 0},
                NativeUtil.macAddressToByteArray(null));
    }

    /**
     * Test that converting a colon delimited MAC address to android.net.MacAddress works. Also test
     * invalid input exception is handled by NativeUtil#getMacAddressOrNull() and returns 'null'.
     */
    @Test
    public void testGetMacAddressOrNull() throws Exception {
        String macAddressStr = "11:22:33:44:55:66";
        assertEquals(MacAddress.fromString(macAddressStr),
                NativeUtil.getMacAddressOrNull(macAddressStr));
        assertNull(NativeUtil.getMacAddressOrNull(":44:55:66"));
        assertNull(NativeUtil.getMacAddressOrNull(null));
    }

    /**
     * Test that conversion of byte array of mac address to typical colon-delimited MAC address
     * works.
     */
    @Test
    public void testByteArrayToMacAddress() throws Exception {
        assertEquals("61:52:43:34:25:16",
                NativeUtil.macAddressFromByteArray(new byte[]{0x61, 0x52, 0x43, 0x34, 0x25, 0x16}));
    }

    /**
     * Test that parsing a typical colon-delimited MAC OUI address works.
     */
    @Test
    public void testMacAddressOuiToByteArray() throws Exception {
        assertArrayEquals(new byte[]{0x61, 0x52, 0x43},
                NativeUtil.macAddressOuiToByteArray("61:52:43"));
    }

    /**
     * Test that parsing a hex string to byte array works.
     */
    @Test
    public void testHexStringToByteArray() throws Exception {
        assertArrayEquals(new byte[]{0x45, 0x12, 0x23, 0x34},
                NativeUtil.hexStringToByteArray("45122334"));
    }

    /**
     * Test that conversion of byte array to hexstring works.
     */
    @Test
    public void testHexStringFromByteArray() throws Exception {
        assertEquals("45122334",
                NativeUtil.hexStringFromByteArray(new byte[]{0x45, 0x12, 0x23, 0x34}));
    }

    /**
     * Test that conversion of ssid bytes to quoted ASCII string ssid works.
     */
    @Test
    public void testAsciiSsidDecode() throws Exception {
        assertEquals(
                new ArrayList<>(
                        Arrays.asList((byte) 's', (byte) 's', (byte) 'i', (byte) 'd', (byte) '_',
                                (byte) 't', (byte) 'e', (byte) 's', (byte) 't', (byte) '1',
                                (byte) '2', (byte) '3')),
                NativeUtil.decodeSsid("\"ssid_test123\""));
    }

    /**
     * Test that conversion of ssid bytes to quoted UTF8 string ssid works.
     */
    @Test
    public void testUtf8SsidDecode() throws Exception {
        assertEquals(
                new ArrayList<>(
                        Arrays.asList((byte) 0x41, (byte) 0x6e, (byte) 0x64, (byte) 0x72,
                                (byte) 0x6f, (byte) 0x69, (byte) 0x64, (byte) 0x41, (byte) 0x50,
                                (byte) 0xe3, (byte) 0x81, (byte) 0x8f, (byte) 0xe3, (byte) 0x81,
                                (byte) 0xa0, (byte) 0xe3, (byte) 0x81, (byte) 0x95, (byte) 0xe3,
                                (byte) 0x81, (byte) 0x84)),
                NativeUtil.decodeSsid("\"AndroidAPください\""));
    }

    /**
     * Test that conversion of non utf-8 SSID string to bytes fail.
     */
    @Test
    public void testNonUtf8SsidDecodeFails() throws Exception {
        try {
            NativeUtil.decodeSsid("\"\ud800\"");
            fail("Expected ssid decode to fail");
        } catch (IllegalArgumentException e) {
        }
    }

    /**
     * Test that conversion of SSID string with len > 32 to bytes fail.
     */
    @Test
    public void testLargeSsidDecodeFails() throws Exception {
        try {
            NativeUtil.decodeSsid("\"asdrewqdfgyuiopldsqwertyuiolhdergcv\"");
            fail("Expected ssid decode to fail");
        } catch (IllegalArgumentException e) {
        }
    }

    /**
     * Test that conversion of ssid bytes to hex string ssid works.
     */
    @Test
    public void testNonSsidDecode() throws Exception {
        assertEquals(
                new ArrayList<>(
                        Arrays.asList((byte) 0xf5, (byte) 0xe4, (byte) 0xab, (byte) 0x78,
                                (byte) 0xab, (byte) 0x34, (byte) 0x32, (byte) 0x43, (byte) 0x9a)),
                NativeUtil.decodeSsid("f5e4ab78ab3432439a"));
    }

    /**
     * Test that conversion of ssid bytes to quoted string ssid works.
     */
    @Test
    public void testSsidEncode() throws Exception {
        assertEquals(
                "\"ssid_test123\"",
                NativeUtil.encodeSsid(new ArrayList<>(
                        Arrays.asList((byte) 's', (byte) 's', (byte) 'i', (byte) 'd', (byte) '_',
                                (byte) 't', (byte) 'e', (byte) 's', (byte) 't', (byte) '1',
                                (byte) '2', (byte) '3'))));
    }

    /**
     * Test that conversion of byte array to hex string works when the byte array contains 0.
     */
    @Test
    public void testNullCharInSsidEncode() throws Exception {
        assertEquals(
                "007369645f74657374313233",
                NativeUtil.encodeSsid(new ArrayList<>(
                        Arrays.asList((byte) 0x00, (byte) 's', (byte) 'i', (byte) 'd', (byte) '_',
                                (byte) 't', (byte) 'e', (byte) 's', (byte) 't', (byte) '1',
                                (byte) '2', (byte) '3'))));
    }

    /**
     * Test that conversion of byte array to hex string ssid works.
     */
    @Test
    public void testNonSsidEncode() throws Exception {
        assertEquals(
                "f5e4ab78ab3432439a",
                NativeUtil.encodeSsid(new ArrayList<>(
                        Arrays.asList((byte) 0xf5, (byte) 0xe4, (byte) 0xab, (byte) 0x78,
                                (byte) 0xab, (byte) 0x34, (byte) 0x32, (byte) 0x43, (byte) 0x9a))));
    }

    /**
     * Test that conversion of SSID bytes with len > 32 to string fail.
     */
    @Test
    public void testLargeSsidEncodeFails() throws Exception {
        try {
            NativeUtil.encodeSsid(new ArrayList<>(
                    Arrays.asList((byte) 0xf5, (byte) 0xe4, (byte) 0xab, (byte) 0x78, (byte) 0x78,
                            (byte) 0xab, (byte) 0x34, (byte) 0x32, (byte) 0x43, (byte) 0x9a,
                            (byte) 0xab, (byte) 0x34, (byte) 0x32, (byte) 0x43, (byte) 0x9a,
                            (byte) 0xab, (byte) 0x34, (byte) 0x32, (byte) 0x43, (byte) 0x9a,
                            (byte) 0xab, (byte) 0x34, (byte) 0x32, (byte) 0x43, (byte) 0x9a,
                            (byte) 0xab, (byte) 0x34, (byte) 0x32, (byte) 0x43, (byte) 0x9a,
                            (byte) 0xab, (byte) 0x34, (byte) 0x32, (byte) 0x43, (byte) 0x9a)));
            fail("Expected ssid encode to fail");
        } catch (IllegalArgumentException e) {
        }
    }

    /**
     * Test that parsing of quoted SSID to byte array and vice versa works.
     */
    @Test
    public void testSsidEncodeDecode() throws Exception {
        String ssid = "\"ssid_test123\"";
        assertEquals(ssid, NativeUtil.encodeSsid(NativeUtil.decodeSsid(ssid)));
    }

    /**
     * Test that the enclosing quotes are removed properly.
     */
    @Test
    public void testRemoveEnclosingQuotes() throws Exception {
        assertEquals("abcdefgh", NativeUtil.removeEnclosingQuotes("\"abcdefgh\""));
        assertEquals("abcdefgh", NativeUtil.removeEnclosingQuotes("abcdefgh"));
    }

    /**
     * Test PMF is disable when SAE is selected when SAE auto-upgrade offload is supported.
     */
    @Test
    public void testPmfIsDisableWhenPmfEnabledAndAutoUpgradeOffloadSupported() throws Exception {
        WifiGlobals globals = mock(WifiGlobals.class);
        when(globals.isWpa3SaeUpgradeOffloadEnabled()).thenReturn(true);
        assertFalse(NativeUtil.getOptimalPmfSettingForConfig(
                    WifiConfigurationTestUtil.createPskSaeNetwork(),
                    true, globals));
    }

    /**
     * Test that PMF is disabled for a WPA2/WPA3 Enterprise transition mode network
     * configuration even when the default value is enabled.
     */
    @Test
    public void testPmfIsDisableWhenPmfEnabledForWpa2Wpa3EnterpriseNetwork() throws Exception {
        WifiGlobals globals = mock(WifiGlobals.class);
        assertFalse(NativeUtil.getOptimalPmfSettingForConfig(
                WifiConfigurationTestUtil.createWpa2Wpa3EnterpriseNetwork(),
                true, globals));
    }

    /**
     * Test pairwise & group cihpers are merged when SAE auto-upgrade offload is supported.
     */
    @Test
    public void testPairwiseCiphersMergedForSaeAutoUpgradeOffloadSupported() throws Exception {
        WifiGlobals globals = mock(WifiGlobals.class);
        when(globals.isWpa3SaeUpgradeOffloadEnabled()).thenReturn(true);

        SecurityParams saeParams = SecurityParams.createSecurityParamsBySecurityType(
                WifiConfiguration.SECURITY_TYPE_SAE);
        BitSet expectedPairwiseCiphers = new BitSet();
        expectedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.TKIP);
        expectedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.CCMP);
        expectedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.GCMP_128);
        expectedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.GCMP_256);

        BitSet optimalPairwiseCiphers = NativeUtil.getOptimalPairwiseCiphersForConfig(
                WifiConfigurationTestUtil.createPskSaeNetwork(),
                saeParams.getAllowedPairwiseCiphers(), globals);
        assertEquals(expectedPairwiseCiphers, optimalPairwiseCiphers);

        BitSet expectedGroupCiphers = new BitSet();
        expectedGroupCiphers.set(WifiConfiguration.GroupCipher.WEP40);
        expectedGroupCiphers.set(WifiConfiguration.GroupCipher.WEP104);
        expectedGroupCiphers.set(WifiConfiguration.GroupCipher.TKIP);
        expectedGroupCiphers.set(WifiConfiguration.GroupCipher.CCMP);
        expectedGroupCiphers.set(WifiConfiguration.GroupCipher.GCMP_128);
        expectedGroupCiphers.set(WifiConfiguration.GroupCipher.GCMP_256);
        BitSet optimalGroupCiphers = NativeUtil.getOptimalGroupCiphersForConfig(
                WifiConfigurationTestUtil.createPskSaeNetwork(),
                saeParams.getAllowedGroupCiphers(), globals);
        assertEquals(expectedGroupCiphers, optimalGroupCiphers);
    }

    /**
     * Test pairwise and group cihpers are not merged when SAE auto-upgrade offload
     * is not supported.
     */
    @Test
    public void testPairwiseCiphersNotMergedForSaeAutoUpgradeOffloadNotSupported()
            throws Exception {
        WifiGlobals globals = mock(WifiGlobals.class);
        when(globals.isWpa3SaeUpgradeOffloadEnabled()).thenReturn(false);

        SecurityParams saeParams = SecurityParams.createSecurityParamsBySecurityType(
                WifiConfiguration.SECURITY_TYPE_SAE);
        BitSet expectedPairwiseCiphers = new BitSet();
        expectedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.CCMP);
        expectedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.GCMP_128);
        expectedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.GCMP_256);

        BitSet optimalPairwiseCiphers = NativeUtil.getOptimalPairwiseCiphersForConfig(
                WifiConfigurationTestUtil.createPskSaeNetwork(),
                saeParams.getAllowedPairwiseCiphers(), globals);
        assertEquals(expectedPairwiseCiphers, optimalPairwiseCiphers);

        BitSet expectedGroupCiphers = new BitSet();
        expectedGroupCiphers.set(WifiConfiguration.GroupCipher.CCMP);
        expectedGroupCiphers.set(WifiConfiguration.GroupCipher.GCMP_128);
        expectedGroupCiphers.set(WifiConfiguration.GroupCipher.GCMP_256);
        BitSet optimalGroupCiphers = NativeUtil.getOptimalGroupCiphersForConfig(
                WifiConfigurationTestUtil.createPskSaeNetwork(),
                saeParams.getAllowedGroupCiphers(), globals);
        assertEquals(expectedGroupCiphers, optimalGroupCiphers);
    }

    /**
     * Verifies that a valid WPS device type byte array is correctly converted to a string.
     * Uses the example provided in the method's documentation.
     */
    @Test
    public void testWpsDevTypeStringFromByteArray_validInput_documentationExample() {
        // Example from documentation: { 0, 1, 2, -1, 4, 5, 6, 7 } --> "1-02FF0405-1543"
        byte[] devType = {0, 1, 2, (byte) 0xFF, 4, 5, 6, 7};
        String expected = "1-02FF0405-1543"; // Assumes HexEncoding produces uppercase
        String actual = NativeUtil.wpsDevTypeStringFromByteArray(devType);
        assertEquals(expected, actual);
    }

    /**
     * Verifies conversion with a different set of valid WPS device type bytes.
     * Example: Category: 6 (Computer), OUI: 0050F204, Sub Category: 1 (PC)
     */
    @Test
    public void testWpsDevTypeStringFromByteArray_validInput_computerPcExample() {
        byte[] devType = {(byte) 0x00, (byte) 0x06, // Primary Category ID = 6
                (byte) 0x00, (byte) 0x50, (byte) 0xF2, (byte) 0x04, // OUI = 0050F204
                (byte) 0x00, (byte) 0x01  // Sub Category ID = 1
        };
        String expected = "6-0050F204-1"; // Assumes HexEncoding produces uppercase
        String actual = NativeUtil.wpsDevTypeStringFromByteArray(devType);
        assertEquals(expected, actual);
    }

    /**
     * Verifies conversion when all bytes in the device type array are zero.
     */
    @Test
    public void testWpsDevTypeStringFromByteArray_allZeroesInput() {
        byte[] devType = {0, 0, 0, 0, 0, 0, 0, 0};
        String expected = "0-00000000-0"; // Assumes HexEncoding produces uppercase
        String actual = NativeUtil.wpsDevTypeStringFromByteArray(devType);
        assertEquals(expected, actual);
    }

    /**
     * Verifies conversion when all bytes in the device type array are set to their maximum value
     * (0xFF).
     */
    @Test
    public void testWpsDevTypeStringFromByteArray_maxValuesInput() {
        byte[] devType =
                {(byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF,
                        (byte) 0xFF, (byte) 0xFF};
        String expected = "65535-FFFFFFFF-65535"; // Assumes HexEncoding produces uppercase
        String actual = NativeUtil.wpsDevTypeStringFromByteArray(devType);
        assertEquals(expected, actual);
    }

    /**
     * Verifies that passing a null device type array throws an IllegalArgumentException.
     */
    @Test
    public void testWpsDevTypeStringFromByteArray_nullInput_throwsIllegalArgumentException() {
        try {
            NativeUtil.wpsDevTypeStringFromByteArray(null);
            fail("Expected IllegalArgumentException was not thrown for null input.");
        } catch (IllegalArgumentException e) {
            assertEquals("Device type array must be non-null and 8 bytes long", e.getMessage());
        }
    }

    /**
     * Verifies that passing a device type array shorter than 8 bytes throws an
     * IllegalArgumentException.
     */
    @Test
    public void testWpsDevTypeStringFromByteArray_inputTooShort_throwsIllegalArgumentException() {
        byte[] devType = {0, 1, 2, 3, 4, 5, 6}; // 7 bytes
        try {
            NativeUtil.wpsDevTypeStringFromByteArray(devType);
            fail("Expected IllegalArgumentException was not thrown for short input array.");
        } catch (IllegalArgumentException e) {
            assertEquals("Device type array must be non-null and 8 bytes long", e.getMessage());
        }
    }

    /**
     * Verifies that passing a device type array longer than 8 bytes throws an
     * IllegalArgumentException.
     */
    @Test
    public void testWpsDevTypeStringFromByteArray_inputTooLong_throwsIllegalArgumentException() {
        byte[] devType = {0, 1, 2, 3, 4, 5, 6, 7, 8}; // 9 bytes
        try {
            NativeUtil.wpsDevTypeStringFromByteArray(devType);
            fail("Expected IllegalArgumentException was not thrown for long input array.");
        } catch (IllegalArgumentException e) {
            assertEquals("Device type array must be non-null and 8 bytes long", e.getMessage());
        }
    }

    /**
     * Test isEapol4WayHandshakeFailureDueToWrongPassword.
     */
    @Test
    public void testIsEapol4WayHandshakeFailureDueToWrongPassword() throws Exception {
        WifiConfiguration config = mock(WifiConfiguration.class);
        WifiConfiguration.NetworkSelectionStatus networkSelectionStatus =
                mock(WifiConfiguration.NetworkSelectionStatus.class);
        SecurityParams securityParams = mock(SecurityParams.class);

        when(config.getNetworkSelectionStatus()).thenReturn(networkSelectionStatus);

        // Case 1: PSK network (via candidate security params), locally generated,
        // IE_IN_4WAY_DIFFERS -> false
        when(networkSelectionStatus.getCandidateSecurityParams()).thenReturn(securityParams);
        when(securityParams.isSecurityType(WifiConfiguration.SECURITY_TYPE_PSK)).thenReturn(true);
        assertFalse(NativeUtil.isEapol4WayHandshakeFailureDueToWrongPassword(config, true,
                StaIfaceReasonCode.IE_IN_4WAY_DIFFERS));

        // Case 2: PSK network, not locally generated, DISASSOC_AP_BUSY -> false
        assertFalse(NativeUtil.isEapol4WayHandshakeFailureDueToWrongPassword(config, false,
                StaIfaceReasonCode.DISASSOC_AP_BUSY));

        // Case 3: PSK network, locally generated, some other reason -> true
        assertTrue(NativeUtil.isEapol4WayHandshakeFailureDueToWrongPassword(config, true,
                StaIfaceReasonCode.UNSPECIFIED));

        // Case 4: Not PSK network -> false
        when(securityParams.isSecurityType(WifiConfiguration.SECURITY_TYPE_PSK)).thenReturn(false);
        when(securityParams.isSecurityType(WifiConfiguration.SECURITY_TYPE_WAPI_PSK))
                .thenReturn(false);
        assertFalse(NativeUtil.isEapol4WayHandshakeFailureDueToWrongPassword(config, true,
                StaIfaceReasonCode.UNSPECIFIED));

        // Case 5: WAPI_PSK network -> true
        when(securityParams.isSecurityType(WifiConfiguration.SECURITY_TYPE_WAPI_PSK))
                .thenReturn(true);
        assertTrue(NativeUtil.isEapol4WayHandshakeFailureDueToWrongPassword(config, true,
                StaIfaceReasonCode.UNSPECIFIED));

        // Case 6: candidate security params is null, fallback to config.isSecurityType
        when(networkSelectionStatus.getCandidateSecurityParams()).thenReturn(null);
        when(config.isSecurityType(WifiConfiguration.SECURITY_TYPE_PSK)).thenReturn(true);
        assertTrue(NativeUtil.isEapol4WayHandshakeFailureDueToWrongPassword(config, true,
                StaIfaceReasonCode.UNSPECIFIED));

        // Case 7: candidate security params is null, fallback to config.isSecurityType,
        // not PSK -> false
        when(config.isSecurityType(WifiConfiguration.SECURITY_TYPE_PSK)).thenReturn(false);
        when(config.isSecurityType(WifiConfiguration.SECURITY_TYPE_WAPI_PSK)).thenReturn(false);
        assertFalse(NativeUtil.isEapol4WayHandshakeFailureDueToWrongPassword(config, true,
                StaIfaceReasonCode.UNSPECIFIED));
    }
}
