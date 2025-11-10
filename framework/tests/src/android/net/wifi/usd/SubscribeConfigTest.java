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
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.net.wifi.util.Environment;
import android.os.Parcel;

import androidx.test.filters.SmallTest;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


/**
 * Unit test harness for SubscribeConfig class.
 */
@SmallTest
public class SubscribeConfigTest {
    private static final String USD_SERVICE_NAME = "USD_UNIT_TEST";
    private static final byte[] TEST_SSI = new byte[]{1, 2, 3, 4};
    private static final int TEST_TTL_SECONDS = 3000;
    private static final int TEST_QUERY_PERIOD_MILLIS = 200;
    private static final int[] TEST_FREQUENCIES = new int[]{2412, 2437, 2462};
    private List<byte[]> mFilter;
    private static final byte[] TEST_SELF_DEV_IK = new byte[16];
    private static final List<byte[]> TEST_PEER_DEV_IKS = new ArrayList<>();
    static {
        Arrays.fill(TEST_SELF_DEV_IK, (byte) 0x0B);
        byte[] peerIk1 = new byte[16];
        Arrays.fill(peerIk1, (byte) 0x0C);
        TEST_PEER_DEV_IKS.add(peerIk1);
    }

    @Before
    public void setUp() throws Exception {
        mFilter = new ArrayList<>();
        mFilter.add(new byte[]{10, 11});
        mFilter.add(new byte[]{12, 13, 14});
    }

    /**
     * Tests set and get for SubscribeConfig.
     */
    @Test
    public void testSubscribeConfig() {
        SubscribeConfig subscribeConfig = new SubscribeConfig.Builder(
                USD_SERVICE_NAME).setQueryPeriodMillis(TEST_QUERY_PERIOD_MILLIS).setSubscribeType(
                SubscribeConfig.SUBSCRIBE_TYPE_ACTIVE).setServiceProtoType(
                SubscribeConfig.SERVICE_PROTO_TYPE_GENERIC).setTtlSeconds(
                TEST_TTL_SECONDS).setRecommendedOperatingFrequenciesMhz(
                TEST_FREQUENCIES).setServiceSpecificInfo(TEST_SSI).setTxMatchFilter(
                mFilter).setRxMatchFilter(mFilter).setOperatingFrequenciesMhz(
                TEST_FREQUENCIES).build();
        assertArrayEquals(USD_SERVICE_NAME.getBytes(), subscribeConfig.getServiceName());
        assertEquals(200, subscribeConfig.getQueryPeriodMillis());
        assertEquals(SubscribeConfig.SUBSCRIBE_TYPE_ACTIVE, subscribeConfig.getSubscribeType());
        assertEquals(SubscribeConfig.SERVICE_PROTO_TYPE_GENERIC,
                subscribeConfig.getServiceProtoType());
        assertEquals(TEST_TTL_SECONDS, subscribeConfig.getTtlSeconds());
        assertArrayEquals(TEST_FREQUENCIES,
                subscribeConfig.getRecommendedOperatingFrequenciesMhz());
        assertArrayEquals(TEST_SSI, subscribeConfig.getServiceSpecificInfo());
        assertEquals(mFilter.size(), subscribeConfig.getRxMatchFilter().size());
        assertEquals(mFilter.size(), subscribeConfig.getTxMatchFilter().size());
        for (int i = 0; i < mFilter.size(); i++) {
            assertArrayEquals(mFilter.get(i), subscribeConfig.getRxMatchFilter().get(i));
            assertArrayEquals(mFilter.get(i), subscribeConfig.getTxMatchFilter().get(i));
        }
        assertArrayEquals(TEST_FREQUENCIES, subscribeConfig.getOperatingFrequenciesMhz());
    }

    /**
     * Tests SubscribeConfig with invalid arguments.
     */
    @Test
    public void testSubscribeConfigWithInvalidArgs() {
        assertThrows(NullPointerException.class, () -> new SubscribeConfig.Builder(null));
        assertThrows(IllegalArgumentException.class, () -> new SubscribeConfig.Builder(""));
        assertThrows(IllegalArgumentException.class,
                () -> new SubscribeConfig.Builder("a".repeat(258)));
        SubscribeConfig.Builder builder = new SubscribeConfig.Builder(USD_SERVICE_NAME);
        assertThrows(IllegalArgumentException.class, () -> builder.setQueryPeriodMillis(-1));
        assertThrows(IllegalArgumentException.class, () -> builder.setSubscribeType(4));
        assertThrows(IllegalArgumentException.class, () -> builder.setServiceProtoType(4));
        assertThrows(IllegalArgumentException.class, () -> builder.setTtlSeconds(-1));
        assertThrows(NullPointerException.class,
                () -> builder.setRecommendedOperatingFrequenciesMhz(null));
        assertThrows(IllegalArgumentException.class,
                () -> builder.setRecommendedOperatingFrequenciesMhz(new int[]{1, 2, 3}));
        assertThrows(IllegalArgumentException.class,
                () -> builder.setRecommendedOperatingFrequenciesMhz(
                        new int[Config.MAX_NUM_OF_OPERATING_FREQUENCIES + 1]));
        assertThrows(IllegalArgumentException.class, () -> builder.setQueryPeriodMillis(-1));
        assertThrows(NullPointerException.class, () -> builder.setServiceSpecificInfo(null));
        assertThrows(NullPointerException.class, () -> builder.setRxMatchFilter(null));
        assertThrows(NullPointerException.class, () -> builder.setTxMatchFilter(null));
        assertThrows(NullPointerException.class, () -> builder.setOperatingFrequenciesMhz(null));
        assertThrows(IllegalArgumentException.class,
                () -> builder.setOperatingFrequenciesMhz(new int[]{1, 2, 3}));
        assertThrows(IllegalArgumentException.class, () -> builder.setOperatingFrequenciesMhz(
                new int[Config.MAX_NUM_OF_OPERATING_FREQUENCIES + 1]));
    }

    /**
     * Tests SubscribeConfig object is correctly serialized and deserialized when using parcel.
     */
    @Test
    public void testSubscribeConfigParcel() {
        // Create SubscribeConfig
        SubscribeConfig subscribeConfig = new SubscribeConfig.Builder(
                USD_SERVICE_NAME).setQueryPeriodMillis(TEST_QUERY_PERIOD_MILLIS).setSubscribeType(
                SubscribeConfig.SUBSCRIBE_TYPE_ACTIVE).setServiceProtoType(
                SubscribeConfig.SERVICE_PROTO_TYPE_GENERIC).setTtlSeconds(
                TEST_TTL_SECONDS).setRecommendedOperatingFrequenciesMhz(
                TEST_FREQUENCIES).setServiceSpecificInfo(TEST_SSI).setTxMatchFilter(
                mFilter).setRxMatchFilter(mFilter).setOperatingFrequenciesMhz(
                TEST_FREQUENCIES).build();
        // Serialize SubscribeConfig to parcel
        Parcel parcel = Parcel.obtain();
        subscribeConfig.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        // Deserialize SubscribeConfig from parcel
        SubscribeConfig deserializedSubscribeConfig = SubscribeConfig.CREATOR.createFromParcel(
                parcel);
        // Validate deserialized SubscribeConfig is equal to original SubscribeConfig
        assertEquals(subscribeConfig, deserializedSubscribeConfig);
        assertEquals(subscribeConfig.hashCode(), deserializedSubscribeConfig.hashCode());
        // Release the parcel
        parcel.recycle();
    }

    @Test
    public void testBuilderAndGettersForProximityRangingEnabled() {
        assumeTrue(Environment.isSdkNewerThanB());
        SubscribeConfig config = new SubscribeConfig.Builder(USD_SERVICE_NAME)
                .setProximityRangingEnabled(true)
                .setSelfDeviceIdentityKey(TEST_SELF_DEV_IK)
                .setPeerDeviceIdentityKeys(TEST_PEER_DEV_IKS)
                .setSubscribeType(Config.SUBSCRIBE_TYPE_ACTIVE)
                .build();

        assertTrue(config.isProximityRangingEnabled());
        assertArrayEquals(TEST_SELF_DEV_IK, config.getSelfDeviceIdentityKey());
        assertEquals(TEST_PEER_DEV_IKS, config.getPeerDeviceIdentityKeys());
    }

    @Test
    public void testParcelAndUnparcelForProximityRangingEnabled() {
        assumeTrue(Environment.isSdkNewerThanB());
        SubscribeConfig originalConfig = new SubscribeConfig.Builder(USD_SERVICE_NAME)
                .setProximityRangingEnabled(true)
                .setSelfDeviceIdentityKey(TEST_SELF_DEV_IK)
                .setPeerDeviceIdentityKeys(TEST_PEER_DEV_IKS)
                .setSubscribeType(Config.SUBSCRIBE_TYPE_ACTIVE)
                .build();

        Parcel parcel = Parcel.obtain();
        originalConfig.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);

        SubscribeConfig unparcelledConfig = SubscribeConfig.CREATOR.createFromParcel(parcel);
        parcel.recycle();

        assertEquals(originalConfig, unparcelledConfig);
        assertEquals(originalConfig.hashCode(), unparcelledConfig.hashCode());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidSelfDeviceIdentityKey() {
        assumeTrue(Environment.isSdkNewerThanB());
        new SubscribeConfig.Builder(USD_SERVICE_NAME)
                .setSelfDeviceIdentityKey(new byte[15]);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidPeerDeviceIdentityKey() {
        assumeTrue(Environment.isSdkNewerThanB());
        List<byte[]> invalidPeerIks = new ArrayList<>();
        invalidPeerIks.add(new byte[15]);
        new SubscribeConfig.Builder(USD_SERVICE_NAME)
                .setPeerDeviceIdentityKeys(invalidPeerIks);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testRangingWithPassiveSubscribe() {
        assumeTrue(Environment.isSdkNewerThanB());
        new SubscribeConfig.Builder(USD_SERVICE_NAME)
                .setProximityRangingEnabled(true)
                .setSubscribeType(Config.SUBSCRIBE_TYPE_PASSIVE)
                .build();
    }
}
