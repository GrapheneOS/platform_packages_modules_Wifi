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
import static org.junit.Assume.assumeTrue;

import android.net.MacAddress;
import android.net.wifi.usd.DiscoveryResult;
import android.net.wifi.util.Environment;
import android.os.Parcel;

import androidx.test.filters.SmallTest;

import org.junit.Test;

import java.util.Arrays;

/**
 * Unit test harness for {@link ResponderConfig}.
 */
@SmallTest
public class ResponderConfigTest {

    private static final MacAddress TEST_MAC_ADDRESS =
            MacAddress.fromString("00:11:22:33:44:55");
    private static final int TEST_USD_PEER_ID = 123;
    private static final byte[] TEST_DEV_IK = new byte[16];
    private static final byte[] TEST_PMK = new byte[32];

    static {
        Arrays.fill(TEST_DEV_IK, (byte) 0x0A);
        Arrays.fill(TEST_PMK, (byte) 0x0B);
    }

    private DiscoveryResult createTestDiscoveryResult() {
        return new DiscoveryResult.Builder(TEST_USD_PEER_ID)
                .setDeviceIdentityKey(TEST_DEV_IK)
                .build();
    }

    private ProximityDetectionConfig createTestProximityDetectionConfig() {
        return new ProximityDetectionConfig.Builder(
                ProximityDetectionConfig.RANGING_SERVICE_ROLE_SEEKER)
                .setDiscoveryChannelFrequencyMhz(2412)
                .build();
    }

    private SecureRangingConfig createTestSecureRangingConfig() {
        PasnConfig pasnConfig = new PasnConfig.Builder(
                PasnConfig.AKM_SAE, PasnConfig.CIPHER_GCMP_128)
                .setPmk(TEST_PMK)
                .setProximityDetectionSeekerDeviceIdentityKey(TEST_DEV_IK)
                .build();
        return new SecureRangingConfig.Builder(pasnConfig).build();
    }

    @Test
    public void testBuilderAndGettersWithProximityDetection() {
        assumeTrue(Environment.isSdkNewerThanB());
        ProximityDetectionConfig pdConfig = createTestProximityDetectionConfig();
        ResponderConfig config = new ResponderConfig.Builder()
                .setUsdPeerId(TEST_USD_PEER_ID)
                .setProximityDetectionConfig(pdConfig)
                .build();

        assertEquals(TEST_USD_PEER_ID, config.getUsdPeerId());
        assertEquals(pdConfig, config.getProximityDetectionConfig());
    }

    @Test
    public void testParcelAndUnparcelWithProximityDetection() {
        assumeTrue(Environment.isSdkNewerThanB());
        ProximityDetectionConfig pdConfig = createTestProximityDetectionConfig();
        ResponderConfig originalConfig = new ResponderConfig.Builder()
                .setUsdPeerId(TEST_USD_PEER_ID)
                .setProximityDetectionConfig(pdConfig)
                .build();

        Parcel parcel = Parcel.obtain();
        originalConfig.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);

        ResponderConfig unparcelledConfig = ResponderConfig.CREATOR.createFromParcel(parcel);
        parcel.recycle();

        assertEquals(originalConfig, unparcelledConfig);
        assertEquals(originalConfig.hashCode(), unparcelledConfig.hashCode());
    }

    @Test
    public void testFromProximityDetectionPeer() {
        assumeTrue(Environment.isSdkNewerThanB());
        DiscoveryResult discoveryResult = createTestDiscoveryResult();
        ProximityDetectionConfig pdConfig = createTestProximityDetectionConfig();
        SecureRangingConfig secureRangingConfig = createTestSecureRangingConfig();

        ResponderConfig config = ResponderConfig.fromProximityDetectionPeer(
                discoveryResult, pdConfig, secureRangingConfig);

        assertNotNull(config);
        // This is a placeholder. The actual implementation will populate fields.
        // For now, we just check that it returns a non-null object.
    }

    @Test(expected = IllegalArgumentException.class)
    public void testBuilderWithNoIdentifier() {
        new ResponderConfig.Builder().build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testBuilderWithMultipleIdentifiers() {
        assumeTrue(Environment.isSdkNewerThanB());
        new ResponderConfig.Builder()
                .setMacAddress(TEST_MAC_ADDRESS)
                .setUsdPeerId(TEST_USD_PEER_ID)
                .build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testBuilderWithStaResponderAndNoProximityDetectionConfig() {
        assumeTrue(Environment.isSdkNewerThanB());
        new ResponderConfig.Builder()
                .setUsdPeerId(TEST_USD_PEER_ID)
                .setResponderType(ResponderConfig.RESPONDER_STA)
                .build();
    }
}
