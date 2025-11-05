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
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.net.wifi.util.Environment;
import android.os.Parcel;

import androidx.test.filters.SmallTest;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Unit test harness for {@link PublishConfig}.
 */
@SmallTest
public class PublishConfigTest {

    private static final String TEST_SERVICE_NAME = "TestService";
    private static final byte[] TEST_SELF_DEV_IK = new byte[16];
    private static final List<byte[]> TEST_PEER_DEV_IKS = new ArrayList<>();

    static {
        Arrays.fill(TEST_SELF_DEV_IK, (byte) 0x0B);
        byte[] peerIk1 = new byte[16];
        Arrays.fill(peerIk1, (byte) 0x0C);
        TEST_PEER_DEV_IKS.add(peerIk1);
    }

    @Test
    public void testBuilderAndGettersForProximityRangingEnabled() {
        assumeTrue(Environment.isSdkNewerThanB());
        PublishConfig config = new PublishConfig.Builder(TEST_SERVICE_NAME)
                .setProximityRangingEnabled(true)
                .setSelfDeviceIdentityKey(TEST_SELF_DEV_IK)
                .setPeerDeviceIdentityKeys(TEST_PEER_DEV_IKS)
                .setPublishType(Config.PUBLISH_TYPE_SOLICITED)
                .build();

        assertTrue(config.isProximityRangingEnabled());
        assertArrayEquals(TEST_SELF_DEV_IK, config.getSelfDeviceIdentityKey());
        assertEquals(TEST_PEER_DEV_IKS, config.getPeerDeviceIdentityKeys());
        assertEquals(Config.PUBLISH_TYPE_SOLICITED, config.getPublishType());
    }

    @Test
    public void testParcelAndUnparcelForProximityRangingEnabled() {
        assumeTrue(Environment.isSdkNewerThanB());
        PublishConfig originalConfig = new PublishConfig.Builder(TEST_SERVICE_NAME)
                .setProximityRangingEnabled(true)
                .setSelfDeviceIdentityKey(TEST_SELF_DEV_IK)
                .setPeerDeviceIdentityKeys(TEST_PEER_DEV_IKS)
                .setPublishType(Config.PUBLISH_TYPE_SOLICITED)
                .build();

        Parcel parcel = Parcel.obtain();
        originalConfig.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);

        PublishConfig unparcelledConfig = PublishConfig.CREATOR.createFromParcel(parcel);
        parcel.recycle();

        assertEquals(originalConfig, unparcelledConfig);
        assertEquals(originalConfig.hashCode(), unparcelledConfig.hashCode());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidSelfDeviceIdentityKey() {
        assumeTrue(Environment.isSdkNewerThanB());
        new PublishConfig.Builder(TEST_SERVICE_NAME)
                .setSelfDeviceIdentityKey(new byte[15]);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidPeerDeviceIdentityKey() {
        assumeTrue(Environment.isSdkNewerThanB());
        List<byte[]> invalidPeerIks = new ArrayList<>();
        invalidPeerIks.add(new byte[15]);
        new PublishConfig.Builder(TEST_SERVICE_NAME)
                .setPeerDeviceIdentityKeys(invalidPeerIks);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testRangingWithUnsolicitedPublish() {
        assumeTrue(Environment.isSdkNewerThanB());
        new PublishConfig.Builder(TEST_SERVICE_NAME)
                .setProximityRangingEnabled(true)
                .setPublishType(Config.PUBLISH_TYPE_UNSOLICITED)
                .build();
    }
}
