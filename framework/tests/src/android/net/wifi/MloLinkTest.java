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

package android.net.wifi;

import static android.net.wifi.MloLink.getStateString;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import android.net.MacAddress;
import android.os.Parcel;

import androidx.test.filters.SmallTest;

import org.junit.Test;

/**
 * Unit tests for {@link android.net.wifi.MloLink}.
 */
@SmallTest
public class MloLinkTest {
    private static final int LINK_ID = 1;
    private static final MacAddress AP_MAC = MacAddress.fromString("00:11:22:33:44:55");
    private static final MacAddress STA_MAC = MacAddress.fromString("66:77:88:99:AA:BB");
    private static final int STATE = MloLink.MLO_LINK_STATE_ACTIVE;
    private static final int RSSI = -60;
    private static final int RX_SPEED = 100;
    private static final int MAX_RX_SPEED = 120;
    private static final int TX_SPEED = 150;
    private static final int MAX_TX_SPEED = 180;

    /**
     * Tests the default constructor to ensure fields are initialized correctly.
     */
    @Test
    public void testMloLinkConstructor() {
        MloLink link = new MloLink();
        assertEquals(MloLink.INVALID_MLO_LINK_ID, link.getLinkId());
        assertEquals(MloLink.MLO_LINK_STATE_UNASSOCIATED, link.getState());
        assertEquals(WifiInfo.INVALID_RSSI, link.getRssi());
        assertEquals(WifiInfo.LINK_SPEED_UNKNOWN, link.getRxLinkSpeedMbps());
        assertEquals(WifiInfo.LINK_SPEED_UNKNOWN, link.getMaxSupportedRxLinkSpeedMbps());
        assertEquals(WifiInfo.LINK_SPEED_UNKNOWN, link.getTxLinkSpeedMbps());
        assertEquals(WifiInfo.LINK_SPEED_UNKNOWN, link.getMaxSupportedTxLinkSpeedMbps());
        assertEquals(null, link.getApMacAddress());
        assertEquals(null, link.getStaMacAddress());
    }

    /**
     * Tests the copy constructor to ensure all fields are copied correctly.
     */
    @Test
    public void testMloLinkCopyConstructor() {
        MloLink original = new MloLink();
        original.setLinkId(LINK_ID);
        original.setApMacAddress(AP_MAC);
        original.setStaMacAddress(STA_MAC);
        original.setState(STATE);
        original.setRssi(RSSI);
        original.setRxLinkSpeedMbps(RX_SPEED);
        original.setMaxSupportedRxLinkSpeedMbps(MAX_RX_SPEED);
        original.setTxLinkSpeedMbps(TX_SPEED);
        original.setMaxSupportedTxLinkSpeedMbps(MAX_TX_SPEED);

        MloLink copy = new MloLink(original, 0L);
        assertEquals(original, copy);
    }

    /**
     * Tests the getter and setter methods for all fields.
     */
    @Test
    public void testMloLinkGetSet() {
        MloLink link = new MloLink();

        link.setLinkId(LINK_ID);
        assertEquals(LINK_ID, link.getLinkId());

        link.setApMacAddress(AP_MAC);
        assertEquals(AP_MAC, link.getApMacAddress());

        link.setStaMacAddress(STA_MAC);
        assertEquals(STA_MAC, link.getStaMacAddress());

        link.setState(STATE);
        assertEquals(STATE, link.getState());

        link.setRssi(RSSI);
        assertEquals(RSSI, link.getRssi());

        link.setRxLinkSpeedMbps(RX_SPEED);
        assertEquals(RX_SPEED, link.getRxLinkSpeedMbps());

        link.setMaxSupportedRxLinkSpeedMbps(MAX_RX_SPEED);
        assertEquals(MAX_RX_SPEED, link.getMaxSupportedRxLinkSpeedMbps());

        link.setTxLinkSpeedMbps(TX_SPEED);
        assertEquals(TX_SPEED, link.getTxLinkSpeedMbps());

        link.setMaxSupportedTxLinkSpeedMbps(MAX_TX_SPEED);
        assertEquals(MAX_TX_SPEED, link.getMaxSupportedTxLinkSpeedMbps());
    }

    /**
     * Tests the Parcelable implementation for correct marshalling and unmarshalling.
     */
    @Test
    public void testMloLinkParcelable() {
        MloLink link = new MloLink();
        link.setLinkId(LINK_ID);
        link.setApMacAddress(AP_MAC);
        link.setStaMacAddress(STA_MAC);
        link.setState(STATE);
        link.setRssi(RSSI);
        link.setRxLinkSpeedMbps(RX_SPEED);
        link.setMaxSupportedRxLinkSpeedMbps(MAX_RX_SPEED);
        link.setTxLinkSpeedMbps(TX_SPEED);
        link.setMaxSupportedTxLinkSpeedMbps(MAX_TX_SPEED);

        Parcel parcel = Parcel.obtain();
        link.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        MloLink fromParcel = MloLink.CREATOR.createFromParcel(parcel);

        assertEquals(link, fromParcel);
        parcel.recycle();
    }

    /**
     * Tests the equals() and hashCode() methods for correctness.
     */
    @Test
    public void testEqualsAndHashCode() {
        MloLink link1 = new MloLink();
        link1.setLinkId(LINK_ID);
        link1.setApMacAddress(AP_MAC);
        link1.setStaMacAddress(STA_MAC);
        link1.setState(STATE);
        link1.setRssi(RSSI);
        link1.setRxLinkSpeedMbps(RX_SPEED);
        link1.setMaxSupportedRxLinkSpeedMbps(MAX_RX_SPEED);
        link1.setTxLinkSpeedMbps(TX_SPEED);
        link1.setMaxSupportedTxLinkSpeedMbps(MAX_TX_SPEED);

        MloLink link2 = new MloLink(link1, 0L);
        assertEquals(link1, link2);
        assertEquals(link1.hashCode(), link2.hashCode());

        link2.setRssi(-70);
        assertNotEquals(link1, link2);

        link2.setRssi(RSSI);
        link2.setMaxSupportedTxLinkSpeedMbps(200);
        assertNotEquals(link1, link2);
    }

    /**
     * Tests the toString() method to ensure it includes all relevant fields.
     */
    @Test
    public void testToString() {
        MloLink link = new MloLink();
        link.setLinkId(LINK_ID);
        link.setApMacAddress(AP_MAC);
        link.setStaMacAddress(STA_MAC);
        link.setState(STATE);
        link.setRssi(RSSI);
        link.setRxLinkSpeedMbps(RX_SPEED);
        link.setMaxSupportedRxLinkSpeedMbps(MAX_RX_SPEED);
        link.setTxLinkSpeedMbps(TX_SPEED);
        link.setMaxSupportedTxLinkSpeedMbps(MAX_TX_SPEED);

        String linkStr = link.toString();
        assertTrue(linkStr.contains("id: " + LINK_ID));
        assertTrue(linkStr.contains("state: " + getStateString(STATE)));
        assertTrue(linkStr.contains("RSSI: " + RSSI));
        assertTrue(linkStr.contains("Rx Link speed: " + RX_SPEED + WifiInfo.LINK_SPEED_UNITS));
        assertTrue(linkStr.contains(
                "Max Supported Rx Link speed: " + MAX_RX_SPEED + WifiInfo.LINK_SPEED_UNITS));
        assertTrue(linkStr.contains("Tx Link speed: " + TX_SPEED + WifiInfo.LINK_SPEED_UNITS));
        assertTrue(linkStr.contains(
                "Max Supported Tx Link speed: " + MAX_TX_SPEED + WifiInfo.LINK_SPEED_UNITS));
        assertTrue(linkStr.contains("AP MAC Address: " + AP_MAC.toString()));
        assertTrue(linkStr.contains("STA MAC Address: " + STA_MAC.toString()));
    }
}
