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
import static org.junit.Assert.assertNotNull;

import android.os.Parcel;

import androidx.test.filters.SmallTest;

import org.junit.Test;

/**
 * Unit test harness for {@link ProximityDetectionConfig}.
 */
@SmallTest
public class ProximityDetectionConfigTest {

    private static final int TEST_RANGING_SERVICE_ROLE = ProximityDetectionConfig
            .RANGING_SERVICE_ROLE_SEEKER;
    private static final int TEST_DISCOVERY_CHANNEL_FREQUENCY_MHZ = 2412;
    private static final int TEST_PREFERRED_RANGING_CHANNEL_FREQUENCY_MHZ = 5180;
    private static final boolean TEST_ADVERTISER_REQUIRE_RANGE_RESULT = true;
    private static final int TEST_PREFERRED_RANGING_MEASUREMENT_ROLE = ProximityDetectionConfig
            .RANGING_MEASUREMENT_ROLE_ISTA;
    private static final int TEST_CONTINUOUS_RANGING_INTERVAL_MS = 500;
    private static final int TEST_EGRESS_DISTANCE_MM = 1000;
    private static final int TEST_INGRESS_DISTANCE_MM = 500;

    @Test
    public void testBuilderAndGetters() {
        ProximityDetectionConfig config = new ProximityDetectionConfig
                .Builder(TEST_RANGING_SERVICE_ROLE)
                .setDiscoveryChannelFrequencyMhz(TEST_DISCOVERY_CHANNEL_FREQUENCY_MHZ)
                .setPreferredRangingChannelFrequencyMhz(
                        TEST_PREFERRED_RANGING_CHANNEL_FREQUENCY_MHZ)
                .setAdvertiserRequireRangeResult(TEST_ADVERTISER_REQUIRE_RANGE_RESULT)
                .setPreferredRangingMeasurementRole(TEST_PREFERRED_RANGING_MEASUREMENT_ROLE)
                .setContinuousRangingIntervalMillis(TEST_CONTINUOUS_RANGING_INTERVAL_MS)
                .setEgressDistanceMm(TEST_EGRESS_DISTANCE_MM)
                .setIngressDistanceMm(TEST_INGRESS_DISTANCE_MM)
                .build();

        assertNotNull(config);
        assertEquals(TEST_RANGING_SERVICE_ROLE, config.getRangingServiceRole());
        assertEquals(TEST_DISCOVERY_CHANNEL_FREQUENCY_MHZ,
                config.getDiscoveryChannelFrequencyMhz());
        assertEquals(TEST_PREFERRED_RANGING_CHANNEL_FREQUENCY_MHZ,
                config.getPreferredRangingChannelFrequencyMhz());
        assertEquals(TEST_ADVERTISER_REQUIRE_RANGE_RESULT,
                config.isAdvertiserRequireRangeResult());
        assertEquals(TEST_PREFERRED_RANGING_MEASUREMENT_ROLE,
                config.getPreferredRangingMeasurementRole());
        assertEquals(TEST_CONTINUOUS_RANGING_INTERVAL_MS,
                config.getContinuousRangingIntervalMillis());
        assertEquals(TEST_EGRESS_DISTANCE_MM, config.getEgressDistanceMm());
        assertEquals(TEST_INGRESS_DISTANCE_MM, config.getIngressDistanceMm());
    }

    @Test
    public void testParcelAndUnparcel() {
        ProximityDetectionConfig originalConfig =
                new ProximityDetectionConfig.Builder(TEST_RANGING_SERVICE_ROLE)
                .setDiscoveryChannelFrequencyMhz(TEST_DISCOVERY_CHANNEL_FREQUENCY_MHZ)
                .setPreferredRangingChannelFrequencyMhz(
                        TEST_PREFERRED_RANGING_CHANNEL_FREQUENCY_MHZ)
                .setAdvertiserRequireRangeResult(TEST_ADVERTISER_REQUIRE_RANGE_RESULT)
                .setPreferredRangingMeasurementRole(TEST_PREFERRED_RANGING_MEASUREMENT_ROLE)
                .setContinuousRangingIntervalMillis(TEST_CONTINUOUS_RANGING_INTERVAL_MS)
                .setEgressDistanceMm(TEST_EGRESS_DISTANCE_MM)
                .setIngressDistanceMm(TEST_INGRESS_DISTANCE_MM)
                .build();

        Parcel parcel = Parcel.obtain();
        originalConfig.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);

        ProximityDetectionConfig unparcelledConfig =
                ProximityDetectionConfig.CREATOR.createFromParcel(parcel);
        parcel.recycle();

        assertNotNull(unparcelledConfig);
        assertEquals(originalConfig, unparcelledConfig);
        assertEquals(originalConfig.hashCode(), unparcelledConfig.hashCode());
    }

    @Test
    public void testEqualsAndHashCode() {
        ProximityDetectionConfig config1 =
                new ProximityDetectionConfig.Builder(TEST_RANGING_SERVICE_ROLE)
                .setDiscoveryChannelFrequencyMhz(TEST_DISCOVERY_CHANNEL_FREQUENCY_MHZ)
                .build();
        ProximityDetectionConfig config2 =
                new ProximityDetectionConfig.Builder(TEST_RANGING_SERVICE_ROLE)
                .setDiscoveryChannelFrequencyMhz(TEST_DISCOVERY_CHANNEL_FREQUENCY_MHZ)
                .build();
        ProximityDetectionConfig config3 =
                new ProximityDetectionConfig
                        .Builder(ProximityDetectionConfig.RANGING_SERVICE_ROLE_ADVERTISER)
                .setDiscoveryChannelFrequencyMhz(TEST_DISCOVERY_CHANNEL_FREQUENCY_MHZ)
                .build();

        assertEquals(config1, config2);
        assertEquals(config1.hashCode(), config2.hashCode());
        assertNotEquals(config1, config3);
        assertNotEquals(config1.hashCode(), config3.hashCode());
    }

    @Test(expected = IllegalStateException.class)
    public void testGetEgressDistanceMmNotSet() {
        ProximityDetectionConfig config =
                new ProximityDetectionConfig.Builder(TEST_RANGING_SERVICE_ROLE).build();
        config.getEgressDistanceMm();
    }

    @Test(expected = IllegalStateException.class)
    public void testGetIngressDistanceMmNotSet() {
        ProximityDetectionConfig config =
                new ProximityDetectionConfig.Builder(TEST_RANGING_SERVICE_ROLE).build();
        config.getIngressDistanceMm();
    }

    @Test
    public void testDescribeContents() {
        ProximityDetectionConfig config =
                new ProximityDetectionConfig.Builder(TEST_RANGING_SERVICE_ROLE).build();
        assertEquals(0, config.describeContents());
    }
}
