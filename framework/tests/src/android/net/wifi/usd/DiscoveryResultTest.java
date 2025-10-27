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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assume.assumeTrue;

import android.net.wifi.util.Environment;

import androidx.test.filters.SmallTest;

import org.junit.Test;

import java.util.Arrays;

/**
 * Unit test harness for {@link DiscoveryResult}.
 */
@SmallTest
public class DiscoveryResultTest {

    private static final int TEST_PEER_ID = 123;
    private static final byte[] TEST_SERVICE_SPECIFIC_INFO = new byte[]{1, 2, 3};
    private static final int TEST_SERVICE_PROTO_TYPE = Config.SERVICE_PROTO_TYPE_GENERIC;
    private static final boolean TEST_IS_FSD_ENABLED = true;
    private static final byte[] TEST_DEV_IK = new byte[16];

    static {
        Arrays.fill(TEST_DEV_IK, (byte) 0x0A);
    }

    private ProximityRangingInfo createTestProximityRangingInfo() {
        return new ProximityRangingInfo.Builder()
                .setDeviceName("TestDevice")
                .build();
    }

    @Test
    public void testBuilderAndGetters() {
        assumeTrue(Environment.isSdkNewerThanB());
        ProximityRangingInfo rangingInfo = createTestProximityRangingInfo();
        DiscoveryResult result = new DiscoveryResult.Builder(TEST_PEER_ID)
                .setServiceSpecificInfo(TEST_SERVICE_SPECIFIC_INFO)
                .setServiceProtoType(TEST_SERVICE_PROTO_TYPE)
                .setFsdEnabled(TEST_IS_FSD_ENABLED)
                .setProximityRangingInfo(rangingInfo)
                .setDeviceIdentityKey(TEST_DEV_IK)
                .build();

        assertNotNull(result);
        assertEquals(TEST_PEER_ID, result.getPeerId());
        assertArrayEquals(TEST_SERVICE_SPECIFIC_INFO, result.getServiceSpecificInfo());
        assertEquals(TEST_SERVICE_PROTO_TYPE, result.getServiceProtoType());
        assertEquals(TEST_IS_FSD_ENABLED, result.isFsdEnabled());
        assertEquals(rangingInfo, result.getProximityRangingInfo());
        assertArrayEquals(TEST_DEV_IK, result.getDeviceIdentityKey());
    }

    @Test
    public void testEqualsAndHashCode() {
        assumeTrue(Environment.isSdkNewerThanB());
        ProximityRangingInfo rangingInfo = createTestProximityRangingInfo();
        DiscoveryResult result1 = new DiscoveryResult.Builder(TEST_PEER_ID)
                .setProximityRangingInfo(rangingInfo)
                .setDeviceIdentityKey(TEST_DEV_IK)
                .build();
        DiscoveryResult result2 = new DiscoveryResult.Builder(TEST_PEER_ID)
                .setProximityRangingInfo(rangingInfo)
                .setDeviceIdentityKey(TEST_DEV_IK)
                .build();
        DiscoveryResult result3 = new DiscoveryResult.Builder(TEST_PEER_ID + 1)
                .setProximityRangingInfo(rangingInfo)
                .setDeviceIdentityKey(TEST_DEV_IK)
                .build();

        assertEquals(result1, result2);
        assertEquals(result1.hashCode(), result2.hashCode());
        assertNotEquals(result1, result3);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSetInvalidDeviceIdentityKey() {
        assumeTrue(Environment.isSdkNewerThanB());
        new DiscoveryResult.Builder(TEST_PEER_ID)
                .setDeviceIdentityKey(new byte[15]);
    }
}
