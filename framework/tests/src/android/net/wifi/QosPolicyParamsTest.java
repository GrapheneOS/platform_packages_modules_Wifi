/*
 * Copyright (C) 2023 The Android Open Source Project
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

package android.net.wifi;


import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.net.DscpPolicy;
import android.net.MacAddress;
import android.os.Parcel;

import com.android.modules.utils.build.SdkLevel;

import org.junit.Before;
import org.junit.Test;

/**
 * Unit tests for {@link QosPolicyParams}.
 */
public class QosPolicyParamsTest {
    private static final int TEST_POLICY_ID = 127;
    private static final int TEST_DIRECTION = QosPolicyParams.DIRECTION_DOWNLINK;
    private static final int TEST_DSCP = 7;
    private static final int TEST_USER_PRIORITY = QosPolicyParams.USER_PRIORITY_VIDEO_LOW;
    private static final int TEST_SOURCE_PORT = 15;
    private static final int TEST_PROTOCOL = QosPolicyParams.PROTOCOL_TCP;
    private static final int[] TEST_DESTINATION_PORT_RANGE = new int[]{17, 20};
    private static final MacAddress TEST_SOURCE_ADDRESS =
            MacAddress.fromString("aa:bb:cc:dd:ee:ff");
    private static final MacAddress TEST_DESTINATION_ADDRESS =
            MacAddress.fromString("00:11:22:33:44:55");

    @Before
    public void setUp() {
        assumeTrue(SdkLevel.isAtLeastU());
    }

    /**
     * Creates a QosPolicyParams object will all fields assigned to a default test value.
     */
    private QosPolicyParams createTestQosPolicyParams() {
        return new QosPolicyParams.Builder(TEST_POLICY_ID, TEST_DIRECTION)
                .setUserPriority(TEST_USER_PRIORITY)
                .setDscp(TEST_DSCP)
                .setSourcePort(TEST_SOURCE_PORT)
                .setProtocol(TEST_PROTOCOL)
                .setDestinationPortRange(
                        TEST_DESTINATION_PORT_RANGE[0], TEST_DESTINATION_PORT_RANGE[1])
                .setSourceAddress(TEST_SOURCE_ADDRESS)
                .setDestinationAddress(TEST_DESTINATION_ADDRESS)
                .build();
    }

    /**
     * Check that all fields in the provided QosPolicyParams object match the default test values.
     */
    private void verifyTestQosPolicyParams(QosPolicyParams params) {
        assertEquals(TEST_POLICY_ID, params.getPolicyId());
        assertEquals(TEST_DIRECTION, params.getDirection());
        assertEquals(TEST_USER_PRIORITY, params.getUserPriority());
        assertEquals(TEST_DSCP, params.getDscp());
        assertEquals(TEST_SOURCE_PORT, params.getSourcePort());
        assertEquals(TEST_PROTOCOL, params.getProtocol());
        assertArrayEquals(TEST_DESTINATION_PORT_RANGE, params.getDestinationPortRange());
        assertTrue(TEST_SOURCE_ADDRESS.equals(params.getSourceAddress()));
        assertTrue(TEST_DESTINATION_ADDRESS.equals(params.getDestinationAddress()));
    }

    /**
     * Tests that the default parameters are set if they are not assigned by the user.
     */
    @Test
    public void testDefaultParamsSet() {
        QosPolicyParams params =
                new QosPolicyParams.Builder(TEST_POLICY_ID, QosPolicyParams.DIRECTION_DOWNLINK)
                        .setUserPriority(TEST_USER_PRIORITY)
                        .build();
        assertEquals(QosPolicyParams.DSCP_ANY, params.getDscp());
        assertEquals(QosPolicyParams.PROTOCOL_ANY, params.getProtocol());
        assertEquals(DscpPolicy.SOURCE_PORT_ANY, params.getSourcePort());
    }

    /**
     * Test that if we set all the parameters in the Builder, the resulting QosPolicyParams
     * object contains the expected values.
     */
    @Test
    public void testSetAllParams() {
        QosPolicyParams params = createTestQosPolicyParams();
        verifyTestQosPolicyParams(params);
    }

    /**
     * Tests that the Builder throws an exception if an invalid parameter is set.
     */
    @Test
    public void testBuilderWithInvalidParam() {
        assertThrows(IllegalArgumentException.class, () ->
                new QosPolicyParams.Builder(TEST_POLICY_ID, QosPolicyParams.DIRECTION_DOWNLINK)
                        .setUserPriority(TEST_USER_PRIORITY)
                        .setDscp(120)   // DSCP should be <= 63
                        .build());
    }

    /**
     * Tests that the Builder throws an exception if a null Mac Address is set.
     */
    @Test
    public void testBuilderWithNullMacAddress() {
        assertThrows(NullPointerException.class, () ->
                new QosPolicyParams.Builder(TEST_POLICY_ID, QosPolicyParams.DIRECTION_DOWNLINK)
                        .setUserPriority(TEST_USER_PRIORITY)
                        .setSourceAddress(null)
                        .build());
    }

    /**
     * Tests that the Builder throws an exception if a direction-specific error is found.
     */
    @Test
    public void testBuilderWithDirectionSpecificError() {
        assertThrows(IllegalArgumentException.class, () ->
                // Policies for downlink are required to have a User Priority.
                new QosPolicyParams.Builder(TEST_POLICY_ID, QosPolicyParams.DIRECTION_DOWNLINK)
                        .build());
    }

    /**
     * Tests that the parceling logic can properly read and write from a Parcel.
     */
    @Test
    public void testParcelReadWrite() {
        QosPolicyParams params = createTestQosPolicyParams();
        Parcel parcel = Parcel.obtain();
        params.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);    // Rewind data position back to the beginning for read.
        QosPolicyParams unparceledParams = QosPolicyParams.CREATOR.createFromParcel(parcel);
        verifyTestQosPolicyParams(unparceledParams);
    }

    /**
     * Tests that the overridden equality and hashCode operators properly compare two objects.
     */
    @Test
    public void testObjectComparison() {
        QosPolicyParams params1 = createTestQosPolicyParams();
        QosPolicyParams params2 = createTestQosPolicyParams();
        assertTrue(params1.equals(params2));
        assertEquals(params1.hashCode(), params2.hashCode());
    }
}
