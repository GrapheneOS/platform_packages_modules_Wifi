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

package android.net.wifi.usd;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;

import android.net.wifi.ScanResult;
import android.os.Parcel;

import androidx.test.filters.SmallTest;

import org.junit.Test;

/**
 * Unit test harness for ProximityRangingInfo class.
 */
@SmallTest
public class ProximityRangingInfoTest {

    private static final String TEST_DEVICE_NAME = "TestDevice";
    private static final boolean TEST_80211MC_BASED_RANGING_SUPPORTED = true;
    private static final boolean TEST_NTB_NON_SECURE_LTF_RANGING_SUPPORTED = false;
    private static final boolean TEST_NTB_SECURE_LTF_RANGING_SUPPORTED = true;
    private static final boolean TEST_UNAUTHENTICATED_PASN_MODE_SUPPORTED = false;
    private static final boolean TEST_AUTHENTICATED_PASN_MODE_SUPPORTED = true;
    private static final boolean TEST_80211MC_BASED_ISTA_ROLE_SUPPORTED = false;
    private static final boolean TEST_80211MC_BASED_RSTA_ROLE_SUPPORTED = true;
    private static final boolean TEST_NTB_ISTA_ROLE_SUPPORTED = false;
    private static final boolean TEST_NTB_RSTA_ROLE_SUPPORTED = true;
    private static final int TEST_MAX_SUPPORTED_PACKET_WIDTH_80211MC_BASED =
            ScanResult.CHANNEL_WIDTH_80MHZ;
    private static final int TEST_MAX_SUPPORTED_PREAMBLE_80211MC_BASED =
            ScanResult.PREAMBLE_VHT;
    private static final int TEST_MAX_SUPPORTED_PACKET_WIDTH_NTB =
            ScanResult.CHANNEL_WIDTH_160MHZ;
    private static final int TEST_MAX_SUPPORTED_PREAMBLE_NTB = ScanResult.PREAMBLE_HE;
    private static final boolean TEST_6GHZ_SUPPORTED = true;

    @Test
    public void testBuilderAndGetters() {
        ProximityRangingInfo info = new ProximityRangingInfo.Builder()
                .setDeviceName(TEST_DEVICE_NAME)
                .set80211mcBasedRangingSupported(TEST_80211MC_BASED_RANGING_SUPPORTED)
                .setNtbNonSecureLtfRangingSupported(TEST_NTB_NON_SECURE_LTF_RANGING_SUPPORTED)
                .setNtbSecureLtfRangingSupported(TEST_NTB_SECURE_LTF_RANGING_SUPPORTED)
                .setUnauthenticatedPasnModeSupported(TEST_UNAUTHENTICATED_PASN_MODE_SUPPORTED)
                .setAuthenticatedPasnModeSupported(TEST_AUTHENTICATED_PASN_MODE_SUPPORTED)
                .set80211mcBasedIstaRoleSupported(TEST_80211MC_BASED_ISTA_ROLE_SUPPORTED)
                .set80211mcBasedRstaRoleSupported(TEST_80211MC_BASED_RSTA_ROLE_SUPPORTED)
                .setNtbIstaRoleSupported(TEST_NTB_ISTA_ROLE_SUPPORTED)
                .setNtbRstaRoleSupported(TEST_NTB_RSTA_ROLE_SUPPORTED)
                .setMaxSupportedPacketWidth80211mcBased(
                        TEST_MAX_SUPPORTED_PACKET_WIDTH_80211MC_BASED)
                .setMaxSupportedPreamble80211mcBased(TEST_MAX_SUPPORTED_PREAMBLE_80211MC_BASED)
                .setMaxSupportedPacketWidthNtb(TEST_MAX_SUPPORTED_PACKET_WIDTH_NTB)
                .setMaxSupportedPreambleNtb(TEST_MAX_SUPPORTED_PREAMBLE_NTB)
                .set6GHzSupported(TEST_6GHZ_SUPPORTED)
                .build();

        assertNotNull(info);
        assertEquals(TEST_DEVICE_NAME, info.getDeviceName());
        assertEquals(TEST_80211MC_BASED_RANGING_SUPPORTED, info.is80211mcBasedRangingSupported());
        assertEquals(TEST_NTB_NON_SECURE_LTF_RANGING_SUPPORTED,
                info.isNtbNonSecureLtfRangingSupported());
        assertEquals(TEST_NTB_SECURE_LTF_RANGING_SUPPORTED, info.isNtbSecureLtfRangingSupported());
        assertEquals(TEST_UNAUTHENTICATED_PASN_MODE_SUPPORTED,
                info.isUnauthenticatedPasnModeSupported());
        assertEquals(TEST_AUTHENTICATED_PASN_MODE_SUPPORTED,
                info.isAuthenticatedPasnModeSupported());
        assertEquals(TEST_80211MC_BASED_ISTA_ROLE_SUPPORTED,
                info.is80211mcBasedIstaRoleSupported());
        assertEquals(TEST_80211MC_BASED_RSTA_ROLE_SUPPORTED,
                info.is80211mcBasedRstaRoleSupported());
        assertEquals(TEST_NTB_ISTA_ROLE_SUPPORTED, info.isNtbIstaRoleSupported());
        assertEquals(TEST_NTB_RSTA_ROLE_SUPPORTED, info.isNtbRstaRoleSupported());
        assertEquals(TEST_MAX_SUPPORTED_PACKET_WIDTH_80211MC_BASED,
                info.getMaxSupportedPacketWidth80211mcBased());
        assertEquals(TEST_MAX_SUPPORTED_PREAMBLE_80211MC_BASED,
                info.getMaxSupportedPreamble80211mcBased());
        assertEquals(TEST_MAX_SUPPORTED_PACKET_WIDTH_NTB, info.getMaxSupportedPacketWidthNtb());
        assertEquals(TEST_MAX_SUPPORTED_PREAMBLE_NTB, info.getMaxSupportedPreambleNtb());
        assertEquals(TEST_6GHZ_SUPPORTED, info.is6GHzSupported());
    }

    @Test
    public void testParcelAndUnparcel() {
        ProximityRangingInfo originalInfo = new ProximityRangingInfo.Builder()
                .setDeviceName(TEST_DEVICE_NAME)
                .set80211mcBasedRangingSupported(TEST_80211MC_BASED_RANGING_SUPPORTED)
                .setNtbNonSecureLtfRangingSupported(TEST_NTB_NON_SECURE_LTF_RANGING_SUPPORTED)
                .setNtbSecureLtfRangingSupported(TEST_NTB_SECURE_LTF_RANGING_SUPPORTED)
                .setUnauthenticatedPasnModeSupported(TEST_UNAUTHENTICATED_PASN_MODE_SUPPORTED)
                .setAuthenticatedPasnModeSupported(TEST_AUTHENTICATED_PASN_MODE_SUPPORTED)
                .set80211mcBasedIstaRoleSupported(TEST_80211MC_BASED_ISTA_ROLE_SUPPORTED)
                .set80211mcBasedRstaRoleSupported(TEST_80211MC_BASED_RSTA_ROLE_SUPPORTED)
                .setNtbIstaRoleSupported(TEST_NTB_ISTA_ROLE_SUPPORTED)
                .setNtbRstaRoleSupported(TEST_NTB_RSTA_ROLE_SUPPORTED)
                .setMaxSupportedPacketWidth80211mcBased(
                        TEST_MAX_SUPPORTED_PACKET_WIDTH_80211MC_BASED)
                .setMaxSupportedPreamble80211mcBased(TEST_MAX_SUPPORTED_PREAMBLE_80211MC_BASED)
                .setMaxSupportedPacketWidthNtb(TEST_MAX_SUPPORTED_PACKET_WIDTH_NTB)
                .setMaxSupportedPreambleNtb(TEST_MAX_SUPPORTED_PREAMBLE_NTB)
                .set6GHzSupported(TEST_6GHZ_SUPPORTED)
                .build();

        Parcel parcel = Parcel.obtain();
        originalInfo.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);

        ProximityRangingInfo unparcelledInfo =
                ProximityRangingInfo.CREATOR.createFromParcel(parcel);
        parcel.recycle();

        assertNotNull(unparcelledInfo);
        assertEquals(originalInfo, unparcelledInfo);
        assertEquals(originalInfo.hashCode(), unparcelledInfo.hashCode());
    }

    @Test
    public void testEqualsAndHashCode() {
        ProximityRangingInfo info1 = new ProximityRangingInfo.Builder()
                .setDeviceName(TEST_DEVICE_NAME)
                .set80211mcBasedRangingSupported(TEST_80211MC_BASED_RANGING_SUPPORTED)
                .build();

        ProximityRangingInfo info2 = new ProximityRangingInfo.Builder()
                .setDeviceName(TEST_DEVICE_NAME)
                .set80211mcBasedRangingSupported(TEST_80211MC_BASED_RANGING_SUPPORTED)
                .build();

        ProximityRangingInfo info3 = new ProximityRangingInfo.Builder()
                .setDeviceName("AnotherDevice")
                .set80211mcBasedRangingSupported(TEST_80211MC_BASED_RANGING_SUPPORTED)
                .build();

        assertEquals(info1, info2);
        assertEquals(info1.hashCode(), info2.hashCode());
        assertNotEquals(info1, info3);
        assertNotEquals(info1.hashCode(), info3.hashCode());
    }
}
