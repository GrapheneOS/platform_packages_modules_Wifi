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

package android.net.wifi.rtt;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import android.os.Bundle;
import android.os.Parcel;

import androidx.test.filters.SmallTest;

import org.junit.Before;
import org.junit.Test;

/**
 * Unit test harness for ProximityDetectionCharacteristics class.
 */
@SmallTest
public class ProximityDetectionCharacteristicsTest {

    private static final int TEST_MAX_NUM_CONTINUOUS_RANGING_SEEKER_SESSIONS = 10;
    private static final int TEST_MAX_NUM_CONTINUOUS_RANGING_ADVERTISER_SESSIONS = 5;
    private static final boolean TEST_CONCURRENT_ISTA_RSTA_OPERATION_SUPPORTED = true;
    private static final int TEST_MIN_ALLOWED_RANGING_INTERVAL_80211MC_MS = 100;
    private static final int TEST_MIN_ALLOWED_RANGING_INTERVAL_NTB_MS = 200;
    private static final String TEST_PROXIMITY_DETECTION_DEVICE_NAME = "TestDevice";
    private static final boolean TEST_80211MC_BASED_RANGING_SUPPORTED = true;
    private static final boolean TEST_NTB_SECURE_HE_LTF_RANGING_SUPPORTED = true;
    private static final boolean TEST_NTB_NON_SECURE_HE_LTF_RANGING_SUPPORTED = false;
    private static final boolean TEST_80211MC_BASED_ISTA_ROLE = true;
    private static final boolean TEST_80211MC_BASED_RSTA_ROLE = false;
    private static final boolean TEST_NTB_ISTA_ROLE = true;
    private static final boolean TEST_NTB_RSTA_ROLE = false;
    private static final int TEST_MAX_SUPPORTED_PACKET_WIDTH_80211MC_BASED = 80;
    private static final int TEST_MAX_SUPPORTED_PREAMBLE_80211MC_BASED = 2;
    private static final int TEST_MAX_SUPPORTED_PACKET_WIDTH_NTB = 160;
    private static final int TEST_MAX_SUPPORTED_PREAMBLE_NTB = 3;
    private static final boolean TEST_UNAUTHENTICATED_PASN = true;
    private static final boolean TEST_AUTHENTICATED_PASN = false;

    private Bundle mCharacteristicsBundle;

    @Before
    public void setUp() throws Exception {
        mCharacteristicsBundle = new Bundle();
        mCharacteristicsBundle.putInt(
                ProximityDetectionCharacteristics
                        .KEY_INT_MAX_NUM_CONTINUOUS_RANGING_SEEKER_SESSIONS,
                TEST_MAX_NUM_CONTINUOUS_RANGING_SEEKER_SESSIONS);
        mCharacteristicsBundle.putInt(
                ProximityDetectionCharacteristics
                        .KEY_INT_MAX_NUM_CONTINUOUS_RANGING_ADVERTISER_SESSIONS,
                TEST_MAX_NUM_CONTINUOUS_RANGING_ADVERTISER_SESSIONS);
        mCharacteristicsBundle.putBoolean(
                ProximityDetectionCharacteristics
                        .KEY_BOOLEAN_CONCURRENT_ISTA_RSTA_OPERATION_SUPPORTED,
                TEST_CONCURRENT_ISTA_RSTA_OPERATION_SUPPORTED);
        mCharacteristicsBundle.putInt(
                ProximityDetectionCharacteristics.KEY_INT_MIN_ALLOWED_RANGING_INTERVAL_80211MC_MS,
                TEST_MIN_ALLOWED_RANGING_INTERVAL_80211MC_MS);
        mCharacteristicsBundle.putInt(
                ProximityDetectionCharacteristics.KEY_INT_MIN_ALLOWED_RANGING_INTERVAL_NTB_MS,
                TEST_MIN_ALLOWED_RANGING_INTERVAL_NTB_MS);
        mCharacteristicsBundle.putString(
                ProximityDetectionCharacteristics.KEY_STRING_PROXIMITY_DETECTION_DEVICE_NAME,
                TEST_PROXIMITY_DETECTION_DEVICE_NAME);
        mCharacteristicsBundle.putBoolean(
                ProximityDetectionCharacteristics.KEY_BOOLEAN_80211MC_BASED_RANGING_SUPPORTED,
                TEST_80211MC_BASED_RANGING_SUPPORTED);
        mCharacteristicsBundle.putBoolean(
                ProximityDetectionCharacteristics.KEY_BOOLEAN_NTB_SECURE_HE_LTF_RANGING_SUPPORTED,
                TEST_NTB_SECURE_HE_LTF_RANGING_SUPPORTED);
        mCharacteristicsBundle.putBoolean(
                ProximityDetectionCharacteristics
                        .KEY_BOOLEAN_NTB_NON_SECURE_HE_LTF_RANGING_SUPPORTED,
                TEST_NTB_NON_SECURE_HE_LTF_RANGING_SUPPORTED);
        mCharacteristicsBundle.putBoolean(
                ProximityDetectionCharacteristics.KEY_BOOLEAN_80211MC_BASED_ISTA_ROLE,
                TEST_80211MC_BASED_ISTA_ROLE);
        mCharacteristicsBundle.putBoolean(
                ProximityDetectionCharacteristics.KEY_BOOLEAN_80211MC_BASED_RSTA_ROLE,
                TEST_80211MC_BASED_RSTA_ROLE);
        mCharacteristicsBundle.putBoolean(
                ProximityDetectionCharacteristics.KEY_BOOLEAN_NTB_ISTA_ROLE,
                TEST_NTB_ISTA_ROLE);
        mCharacteristicsBundle.putBoolean(
                ProximityDetectionCharacteristics.KEY_BOOLEAN_NTB_RSTA_ROLE,
                TEST_NTB_RSTA_ROLE);
        mCharacteristicsBundle.putInt(
                ProximityDetectionCharacteristics.KEY_INT_MAX_SUPPORTED_PACKET_WIDTH_80211MC_BASED,
                TEST_MAX_SUPPORTED_PACKET_WIDTH_80211MC_BASED);
        mCharacteristicsBundle.putInt(
                ProximityDetectionCharacteristics.KEY_INT_MAX_SUPPORTED_PREAMBLE_80211MC_BASED,
                TEST_MAX_SUPPORTED_PREAMBLE_80211MC_BASED);
        mCharacteristicsBundle.putInt(
                ProximityDetectionCharacteristics.KEY_INT_MAX_SUPPORTED_PACKET_WIDTH_NTB,
                TEST_MAX_SUPPORTED_PACKET_WIDTH_NTB);
        mCharacteristicsBundle.putInt(
                ProximityDetectionCharacteristics.KEY_INT_MAX_SUPPORTED_PREAMBLE_NTB,
                TEST_MAX_SUPPORTED_PREAMBLE_NTB);
        mCharacteristicsBundle.putBoolean(
                ProximityDetectionCharacteristics.KEY_BOOLEAN_UNAUTHENTICATED_PASN,
                TEST_UNAUTHENTICATED_PASN);
        mCharacteristicsBundle.putBoolean(
                ProximityDetectionCharacteristics.KEY_BOOLEAN_AUTHENTICATED_PASN,
                TEST_AUTHENTICATED_PASN);
    }

    @Test
    public void testConstructor() {
        ProximityDetectionCharacteristics characteristics =
                new ProximityDetectionCharacteristics(mCharacteristicsBundle);
        assertNotNull(characteristics);
    }

    @Test
    public void testParcelAndUnparcel() {
        ProximityDetectionCharacteristics originalCharacteristics =
                new ProximityDetectionCharacteristics(mCharacteristicsBundle);

        Parcel parcel = Parcel.obtain();
        originalCharacteristics.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);

        ProximityDetectionCharacteristics unparcelledCharacteristics =
                ProximityDetectionCharacteristics.CREATOR.createFromParcel(parcel);
        parcel.recycle();

        assertNotNull(unparcelledCharacteristics);
        assertEquals(TEST_MAX_NUM_CONTINUOUS_RANGING_SEEKER_SESSIONS,
                unparcelledCharacteristics.getMaxNumContinuousRangingSeekerSessions());
        assertEquals(TEST_MAX_NUM_CONTINUOUS_RANGING_ADVERTISER_SESSIONS,
                unparcelledCharacteristics.getMaxNumContinuousRangingAdvertiserSessions());
        assertEquals(TEST_CONCURRENT_ISTA_RSTA_OPERATION_SUPPORTED,
                unparcelledCharacteristics.isConcurrentIstaRstaOperationSupported());
        assertEquals(TEST_MIN_ALLOWED_RANGING_INTERVAL_80211MC_MS,
                unparcelledCharacteristics.getMinAllowedRangingInterval80211mcMillis());
        assertEquals(TEST_MIN_ALLOWED_RANGING_INTERVAL_NTB_MS,
                unparcelledCharacteristics.getMinAllowedRangingIntervalNtbMillis());
        assertEquals(TEST_PROXIMITY_DETECTION_DEVICE_NAME,
                unparcelledCharacteristics.getProximityDetectionDeviceName());
        assertEquals(TEST_80211MC_BASED_RANGING_SUPPORTED,
                unparcelledCharacteristics.is80211mcBasedRangingSupported());
        assertEquals(TEST_NTB_SECURE_HE_LTF_RANGING_SUPPORTED,
                unparcelledCharacteristics.isNtbSecureLtfRangingSupported());
        assertEquals(TEST_NTB_NON_SECURE_HE_LTF_RANGING_SUPPORTED,
                unparcelledCharacteristics.isNtbNonSecureLtfRangingSupported());
        assertEquals(TEST_80211MC_BASED_ISTA_ROLE,
                unparcelledCharacteristics.is80211mcBasedIstaRoleSupported());
        assertEquals(TEST_80211MC_BASED_RSTA_ROLE,
                unparcelledCharacteristics.is80211mcBasedRstaRoleSupported());
        assertEquals(TEST_NTB_ISTA_ROLE,
                unparcelledCharacteristics.isNtbIstaRoleSupported());
        assertEquals(TEST_NTB_RSTA_ROLE,
                unparcelledCharacteristics.isNtbRstaRoleSupported());
        assertEquals(TEST_MAX_SUPPORTED_PACKET_WIDTH_80211MC_BASED,
                unparcelledCharacteristics.getMaxSupportedPacketWidth80211mcBased());
        assertEquals(TEST_MAX_SUPPORTED_PREAMBLE_80211MC_BASED,
                unparcelledCharacteristics.getMaxSupportedPreamble80211mcBased());
        assertEquals(TEST_MAX_SUPPORTED_PACKET_WIDTH_NTB,
                unparcelledCharacteristics.getMaxSupportedPacketWidthNtb());
        assertEquals(TEST_MAX_SUPPORTED_PREAMBLE_NTB,
                unparcelledCharacteristics.getMaxSupportedPreambleNtb());
        assertEquals(TEST_UNAUTHENTICATED_PASN,
                unparcelledCharacteristics.isUnauthenticatedPasnModeSupported());
        assertEquals(TEST_AUTHENTICATED_PASN,
                unparcelledCharacteristics.isAuthenticatedPasnModeSupported());
    }

    @Test
    public void testGetMaxNumContinuousRangingSeekerSessions() {
        ProximityDetectionCharacteristics characteristics =
                new ProximityDetectionCharacteristics(mCharacteristicsBundle);
        assertEquals(TEST_MAX_NUM_CONTINUOUS_RANGING_SEEKER_SESSIONS,
                characteristics.getMaxNumContinuousRangingSeekerSessions());
    }

    @Test
    public void testGetMaxNumContinuousRangingAdvertiserSessions() {
        ProximityDetectionCharacteristics characteristics =
                new ProximityDetectionCharacteristics(mCharacteristicsBundle);
        assertEquals(TEST_MAX_NUM_CONTINUOUS_RANGING_ADVERTISER_SESSIONS,
                characteristics.getMaxNumContinuousRangingAdvertiserSessions());
    }

    @Test
    public void testIsConcurrentIstaRstaOperationSupported() {
        ProximityDetectionCharacteristics characteristics =
                new ProximityDetectionCharacteristics(mCharacteristicsBundle);
        assertEquals(TEST_CONCURRENT_ISTA_RSTA_OPERATION_SUPPORTED,
                characteristics.isConcurrentIstaRstaOperationSupported());
    }

    @Test
    public void testGetMinAllowedRangingInterval80211mcMillis() {
        ProximityDetectionCharacteristics characteristics =
                new ProximityDetectionCharacteristics(mCharacteristicsBundle);
        assertEquals(TEST_MIN_ALLOWED_RANGING_INTERVAL_80211MC_MS,
                characteristics.getMinAllowedRangingInterval80211mcMillis());
    }

    @Test
    public void testGetMinAllowedRangingIntervalNtbMillis() {
        ProximityDetectionCharacteristics characteristics =
                new ProximityDetectionCharacteristics(mCharacteristicsBundle);
        assertEquals(TEST_MIN_ALLOWED_RANGING_INTERVAL_NTB_MS,
                characteristics.getMinAllowedRangingIntervalNtbMillis());
    }

    @Test
    public void testGetProximityDetectionDeviceName() {
        ProximityDetectionCharacteristics characteristics =
                new ProximityDetectionCharacteristics(mCharacteristicsBundle);
        assertEquals(TEST_PROXIMITY_DETECTION_DEVICE_NAME,
                characteristics.getProximityDetectionDeviceName());
    }

    @Test
    public void testIs80211mcBasedRangingSupported() {
        ProximityDetectionCharacteristics characteristics =
                new ProximityDetectionCharacteristics(mCharacteristicsBundle);
        assertEquals(TEST_80211MC_BASED_RANGING_SUPPORTED,
                characteristics.is80211mcBasedRangingSupported());
    }

    @Test
    public void testIsNtbSecureLtfRangingSupported() {
        ProximityDetectionCharacteristics characteristics =
                new ProximityDetectionCharacteristics(mCharacteristicsBundle);
        assertEquals(TEST_NTB_SECURE_HE_LTF_RANGING_SUPPORTED,
                characteristics.isNtbSecureLtfRangingSupported());
    }

    @Test
    public void testIsNtbNonSecureLtfRangingSupported() {
        ProximityDetectionCharacteristics characteristics =
                new ProximityDetectionCharacteristics(mCharacteristicsBundle);
        assertEquals(TEST_NTB_NON_SECURE_HE_LTF_RANGING_SUPPORTED,
                characteristics.isNtbNonSecureLtfRangingSupported());
    }

    @Test
    public void testIs80211mcBasedIstaRoleSupported() {
        ProximityDetectionCharacteristics characteristics =
                new ProximityDetectionCharacteristics(mCharacteristicsBundle);
        assertEquals(TEST_80211MC_BASED_ISTA_ROLE,
                characteristics.is80211mcBasedIstaRoleSupported());
    }

    @Test
    public void testIs80211mcBasedRstaRoleSupported() {
        ProximityDetectionCharacteristics characteristics =
                new ProximityDetectionCharacteristics(mCharacteristicsBundle);
        assertEquals(TEST_80211MC_BASED_RSTA_ROLE,
                characteristics.is80211mcBasedRstaRoleSupported());
    }

    @Test
    public void testIsNtbIstaRoleSupported() {
        ProximityDetectionCharacteristics characteristics =
                new ProximityDetectionCharacteristics(mCharacteristicsBundle);
        assertEquals(TEST_NTB_ISTA_ROLE,
                characteristics.isNtbIstaRoleSupported());
    }

    @Test
    public void testIsNtbRstaRoleSupported() {
        ProximityDetectionCharacteristics characteristics =
                new ProximityDetectionCharacteristics(mCharacteristicsBundle);
        assertEquals(TEST_NTB_RSTA_ROLE,
                characteristics.isNtbRstaRoleSupported());
    }

    @Test
    public void testGetMaxSupportedPacketWidth80211mcBased() {
        ProximityDetectionCharacteristics characteristics =
                new ProximityDetectionCharacteristics(mCharacteristicsBundle);
        assertEquals(TEST_MAX_SUPPORTED_PACKET_WIDTH_80211MC_BASED,
                characteristics.getMaxSupportedPacketWidth80211mcBased());
    }

    @Test
    public void testGetMaxSupportedPreamble80211mcBased() {
        ProximityDetectionCharacteristics characteristics =
                new ProximityDetectionCharacteristics(mCharacteristicsBundle);
        assertEquals(TEST_MAX_SUPPORTED_PREAMBLE_80211MC_BASED,
                characteristics.getMaxSupportedPreamble80211mcBased());
    }

    @Test
    public void testGetMaxSupportedPacketWidthNtb() {
        ProximityDetectionCharacteristics characteristics =
                new ProximityDetectionCharacteristics(mCharacteristicsBundle);
        assertEquals(TEST_MAX_SUPPORTED_PACKET_WIDTH_NTB,
                characteristics.getMaxSupportedPacketWidthNtb());
    }

    @Test
    public void testGetMaxSupportedPreambleNtb() {
        ProximityDetectionCharacteristics characteristics =
                new ProximityDetectionCharacteristics(mCharacteristicsBundle);
        assertEquals(TEST_MAX_SUPPORTED_PREAMBLE_NTB,
                characteristics.getMaxSupportedPreambleNtb());
    }

    @Test
    public void testIsUnauthenticatedPasnModeSupported() {
        ProximityDetectionCharacteristics characteristics =
                new ProximityDetectionCharacteristics(mCharacteristicsBundle);
        assertEquals(TEST_UNAUTHENTICATED_PASN,
                characteristics.isUnauthenticatedPasnModeSupported());
    }

    @Test
    public void testIsAuthenticatedPasnModeSupported() {
        ProximityDetectionCharacteristics characteristics =
                new ProximityDetectionCharacteristics(mCharacteristicsBundle);
        assertEquals(TEST_AUTHENTICATED_PASN,
                characteristics.isAuthenticatedPasnModeSupported());
    }

    @Test
    public void testDescribeContents() {
        ProximityDetectionCharacteristics characteristics =
                new ProximityDetectionCharacteristics(mCharacteristicsBundle);
        assertEquals(0, characteristics.describeContents());
    }
}
