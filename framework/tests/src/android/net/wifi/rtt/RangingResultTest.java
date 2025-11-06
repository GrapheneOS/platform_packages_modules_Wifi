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
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.net.MacAddress;
import android.net.wifi.ScanResult;
import android.os.Parcel;

import androidx.test.filters.SmallTest;

import org.junit.Test;

/**
 * Unit tests for {@link android.net.wifi.rtt.RangingResult}.
 */
@SmallTest
public class RangingResultTest {
    private static final int TEST_USD_PEER_ID = 123;
    private static final long TEST_AVAILABILITY_WINDOW_DURATION_MILLIS = 1000L;
    private static final long TEST_NOMINAL_TIME_MILLIS = 500L;
    private static final int TEST_NUM_NTB_REPETITIONS_PER_MEASUREMENT = 5;
    private static final boolean TEST_IS_DELAYED_LMR_ENABLED = true;

    @Test
    public void testRangingResultBusyTryLaterStatus() {
        final int retryAfterDurationMillis = 1000;
        RangingResult rangingResult = new RangingResult.Builder()
                .setMacAddress(MacAddress.fromString("00:11:22:33:44:55"))
                .setStatus(RangingResult.STATUS_BUSY_TRY_LATER)
                .setRetryAfterDurationMillis(retryAfterDurationMillis)
                .build();

        assertEquals(RangingResult.STATUS_BUSY_TRY_LATER, rangingResult.getStatus());
        assertEquals(retryAfterDurationMillis, rangingResult.getRetryAfterDurationMillis());

        try {
            rangingResult.getDistanceMm();
            fail("getDistanceMm should throw IllegalStateException for STATUS_BUSY_TRY_LATER");
        } catch (IllegalStateException e) {
            // expected
        }

        try {
            rangingResult.getDistanceStdDevMm();
            fail("getDistanceStdDevMm should throw IllegalStateException for "
                    + "STATUS_BUSY_TRY_LATER");
        } catch (IllegalStateException e) {
            // expected
        }

        try {
            rangingResult.getRssi();
            fail("getRssi should throw IllegalStateException for STATUS_BUSY_TRY_LATER");
        } catch (IllegalStateException e) {
            // expected
        }

        RangingResult successResult = new RangingResult.Builder()
                .setMacAddress(MacAddress.fromString("00:11:22:33:44:55"))
                .setStatus(RangingResult.STATUS_SUCCESS)
                .build();
        try {
            successResult.getRetryAfterDurationMillis();
            fail("getRetryAfterDurationMillis should throw IllegalStateException for non-busy "
                    + "status");
        } catch (IllegalStateException e) {
            // expected
        }
    }

    @Test
    public void testParcelReadWrite() {
        final int retryAfterDurationMillis = 1000;
        RangingResult rangingResult = new RangingResult.Builder()
                .setMacAddress(MacAddress.fromString("00:11:22:33:44:55"))
                .setStatus(RangingResult.STATUS_BUSY_TRY_LATER)
                .setRetryAfterDurationMillis(retryAfterDurationMillis)
                .build();

        Parcel parcel = Parcel.obtain();
        rangingResult.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        RangingResult unparceledResult = RangingResult.CREATOR.createFromParcel(parcel);

        assertEquals(rangingResult, unparceledResult);
    }

    @Test
    public void testEqualsAndHashCode() {
        final MacAddress mac = MacAddress.fromString("00:11:22:33:44:55");
        final byte[] lci = new byte[] { 0x1, 0x2 };
        final byte[] lcr = new byte[] { 0x3, 0x4 };

        RangingResult rangingResult1 = new RangingResult.Builder()
                .setMacAddress(mac)
                .setStatus(RangingResult.STATUS_SUCCESS)
                .setDistanceMm(1000)
                .setDistanceStdDevMm(10)
                .setRssi(-50)
                .setNumAttemptedMeasurements(10)
                .setNumSuccessfulMeasurements(8)
                .setLci(lci)
                .setLcr(lcr)
                .setRangingTimestampMillis(12345L)
                .set80211mcMeasurement(true)
                .setMeasurementChannelFrequencyMHz(5220)
                .setMeasurementBandwidth(ScanResult.CHANNEL_WIDTH_80MHZ)
                .build();

        RangingResult rangingResult2 = new RangingResult.Builder()
                .setMacAddress(mac)
                .setStatus(RangingResult.STATUS_SUCCESS)
                .setDistanceMm(1000)
                .setDistanceStdDevMm(10)
                .setRssi(-50)
                .setNumAttemptedMeasurements(10)
                .setNumSuccessfulMeasurements(8)
                .setLci(lci)
                .setLcr(lcr)
                .setRangingTimestampMillis(12345L)
                .set80211mcMeasurement(true)
                .setMeasurementChannelFrequencyMHz(5220)
                .setMeasurementBandwidth(ScanResult.CHANNEL_WIDTH_80MHZ)
                .build();

        RangingResult rangingResult3 = new RangingResult.Builder()
                .setMacAddress(mac)
                .setStatus(RangingResult.STATUS_SUCCESS)
                .setDistanceMm(2000) // different distance
                .setDistanceStdDevMm(10)
                .setRssi(-50)
                .setNumAttemptedMeasurements(10)
                .setNumSuccessfulMeasurements(8)
                .setLci(lci)
                .setLcr(lcr)
                .setRangingTimestampMillis(12345L)
                .set80211mcMeasurement(true)
                .setMeasurementChannelFrequencyMHz(5220)
                .setMeasurementBandwidth(ScanResult.CHANNEL_WIDTH_80MHZ)
                .build();

        assertEquals(rangingResult1, rangingResult2);
        assertEquals(rangingResult1.hashCode(), rangingResult2.hashCode());
        assertNotEquals(rangingResult1, rangingResult3);
    }

    @Test
    public void testToString() {
        RangingResult rangingResult = new RangingResult.Builder()
                .setMacAddress(MacAddress.fromString("00:11:22:33:44:55"))
                .setStatus(RangingResult.STATUS_SUCCESS)
                .setDistanceMm(1000)
                .build();

        String toString = rangingResult.toString();
        assertTrue(toString.contains("distanceMm=1000"));
        assertTrue(toString.contains("status=0"));
        assertTrue(toString.contains("mac=00:11:22:33:44:55"));
    }

    @Test
    public void testBuilderAndGettersWithProximityDetection() {
        RangingResult result = new RangingResult.Builder()
                .setStatus(RangingResult.STATUS_SUCCESS)
                .setUsdPeerId(TEST_USD_PEER_ID)
                .setAvailabilityWindowDurationMillis(TEST_AVAILABILITY_WINDOW_DURATION_MILLIS)
                .setNominalTimeMillis(TEST_NOMINAL_TIME_MILLIS)
                .build();

        assertEquals(TEST_USD_PEER_ID, result.getUsdPeerId());
        assertEquals(TEST_AVAILABILITY_WINDOW_DURATION_MILLIS, result
                .getAvailabilityWindowDurationMillis());
        assertEquals(TEST_NOMINAL_TIME_MILLIS, result.getNominalTimeMillis());
    }

    @Test
    public void testParcelableRoundTripWithProximityDetection() {
        RangingResult originalResult = new RangingResult.Builder()
                .setStatus(RangingResult.STATUS_SUCCESS)
                .setUsdPeerId(TEST_USD_PEER_ID)
                .setAvailabilityWindowDurationMillis(TEST_AVAILABILITY_WINDOW_DURATION_MILLIS)
                .setNominalTimeMillis(TEST_NOMINAL_TIME_MILLIS)
                .build();

        Parcel parcel = Parcel.obtain();
        originalResult.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);

        RangingResult fromParcel = RangingResult.CREATOR.createFromParcel(parcel);
        assertEquals(originalResult, fromParcel);
        assertEquals(originalResult.hashCode(), fromParcel.hashCode());
    }

    @Test
    public void testEqualsAndHashCodeWithProximityDetection() {
        RangingResult result1 = new RangingResult.Builder()
                .setStatus(RangingResult.STATUS_SUCCESS)
                .setUsdPeerId(TEST_USD_PEER_ID)
                .build();
        RangingResult result2 = new RangingResult.Builder()
                .setStatus(RangingResult.STATUS_SUCCESS)
                .setUsdPeerId(TEST_USD_PEER_ID)
                .build();
        RangingResult result3 = new RangingResult.Builder()
                .setStatus(RangingResult.STATUS_SUCCESS)
                .setUsdPeerId(TEST_USD_PEER_ID + 1)
                .build();

        assertEquals(result1, result2);
        assertEquals(result1.hashCode(), result2.hashCode());
        assertNotEquals(result1, result3);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testBuilderWithInvalidUsdPeerId() {
        new RangingResult.Builder().setUsdPeerId(-2);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testBuilderWithNoIdentifier() {
        new RangingResult.Builder().build();
    }

    @Test
    public void testBuilderWithMultipleIdentifiers() {
        new RangingResult.Builder()
                .setMacAddress(MacAddress.fromString("00:11:22:33:44:55"))
                .setUsdPeerId(TEST_USD_PEER_ID)
                .build();
    }

    @Test
    public void testToStringWithProximityDetection() {
        RangingResult result = new RangingResult.Builder()
                .setStatus(RangingResult.STATUS_SUCCESS)
                .setUsdPeerId(TEST_USD_PEER_ID)
                .setAvailabilityWindowDurationMillis(TEST_AVAILABILITY_WINDOW_DURATION_MILLIS)
                .setNominalTimeMillis(TEST_NOMINAL_TIME_MILLIS)
                .build();

        String toString = result.toString();
        assertTrue(toString.contains("usdPeerId=" + TEST_USD_PEER_ID));
        assertTrue(toString.contains("availabilityWindowDurationMillis="
                + TEST_AVAILABILITY_WINDOW_DURATION_MILLIS));
        assertTrue(toString.contains("nominalTimeMillis=" + TEST_NOMINAL_TIME_MILLIS));
    }

    @Test
    public void testBuilderAndGettersWithNtbRanging() {
        RangingResult result = new RangingResult.Builder()
                .setStatus(RangingResult.STATUS_SUCCESS)
                .setUsdPeerId(TEST_USD_PEER_ID)
                .setNumNtbRepetitionsPerMeasurement(TEST_NUM_NTB_REPETITIONS_PER_MEASUREMENT)
                .setLmrDelayed(TEST_IS_DELAYED_LMR_ENABLED)
                .build();

        assertEquals(TEST_NUM_NTB_REPETITIONS_PER_MEASUREMENT,
                result.getNumNtbRepetitionsPerMeasurement());
        assertEquals(TEST_IS_DELAYED_LMR_ENABLED, result.isLmrDelayed());
    }

    @Test
    public void testParcelableRoundTripWithNtbRanging() {
        RangingResult originalResult = new RangingResult.Builder()
                .setStatus(RangingResult.STATUS_SUCCESS)
                .setUsdPeerId(TEST_USD_PEER_ID)
                .setNumNtbRepetitionsPerMeasurement(TEST_NUM_NTB_REPETITIONS_PER_MEASUREMENT)
                .setLmrDelayed(TEST_IS_DELAYED_LMR_ENABLED)
                .build();

        Parcel parcel = Parcel.obtain();
        originalResult.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);

        RangingResult fromParcel = RangingResult.CREATOR.createFromParcel(parcel);
        assertEquals(originalResult, fromParcel);
        assertEquals(originalResult.hashCode(), fromParcel.hashCode());
    }

    @Test
    public void testEqualsAndHashCodeWithNtbRanging() {
        RangingResult result1 = new RangingResult.Builder()
                .setStatus(RangingResult.STATUS_SUCCESS)
                .setUsdPeerId(TEST_USD_PEER_ID)
                .setNumNtbRepetitionsPerMeasurement(TEST_NUM_NTB_REPETITIONS_PER_MEASUREMENT)
                .setLmrDelayed(TEST_IS_DELAYED_LMR_ENABLED)
                .build();
        RangingResult result2 = new RangingResult.Builder()
                .setStatus(RangingResult.STATUS_SUCCESS)
                .setUsdPeerId(TEST_USD_PEER_ID)
                .setNumNtbRepetitionsPerMeasurement(TEST_NUM_NTB_REPETITIONS_PER_MEASUREMENT)
                .setLmrDelayed(TEST_IS_DELAYED_LMR_ENABLED)
                .build();
        RangingResult result3 = new RangingResult.Builder()
                .setStatus(RangingResult.STATUS_SUCCESS)
                .setUsdPeerId(TEST_USD_PEER_ID)
                .setNumNtbRepetitionsPerMeasurement(TEST_NUM_NTB_REPETITIONS_PER_MEASUREMENT + 1)
                .setLmrDelayed(!TEST_IS_DELAYED_LMR_ENABLED)
                .build();

        assertEquals(result1, result2);
        assertEquals(result1.hashCode(), result2.hashCode());
        assertNotEquals(result1, result3);
    }

    @Test
    public void testToStringWithNtbRanging() {
        RangingResult result = new RangingResult.Builder()
                .setStatus(RangingResult.STATUS_SUCCESS)
                .setUsdPeerId(TEST_USD_PEER_ID)
                .setNumNtbRepetitionsPerMeasurement(TEST_NUM_NTB_REPETITIONS_PER_MEASUREMENT)
                .setLmrDelayed(TEST_IS_DELAYED_LMR_ENABLED)
                .build();

        String toString = result.toString();
        assertTrue(toString.contains("numNtbRepetitionsPerMeasurement="
                + TEST_NUM_NTB_REPETITIONS_PER_MEASUREMENT));
        assertTrue(toString.contains("isLmrDelayed=" + TEST_IS_DELAYED_LMR_ENABLED));
    }
}
