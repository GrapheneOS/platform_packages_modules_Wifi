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

package android.net.wifi.p2p;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assume.assumeTrue;

import android.net.wifi.ScanResult;
import android.net.wifi.util.Environment;
import android.os.Parcel;

import androidx.test.filters.SmallTest;

import org.junit.Test;

/**
 * Unit tests for {@link WifiP2pConnectionInfo}
 */
@SmallTest
public final class WifiP2pConnectionInfoTest {
    @Test
    public void testWifiP2pConnectionInfo() {
        assumeTrue(Environment.isSdkNewerThanB());
        int wifiStandard = ScanResult.WIFI_STANDARD_11AX;
        int channelWidth = ScanResult.CHANNEL_WIDTH_80MHZ;
        int txNss = 2;
        int rxNss = 2;

        WifiP2pConnectionInfo info = new WifiP2pConnectionInfo.Builder(wifiStandard, channelWidth)
                .setTxNss(2)
                .setRxNss(2).build();

        assertEquals(wifiStandard, info.getWifiStandard());
        assertEquals(channelWidth, info.getChannelWidth());
        assertEquals(txNss, info.getTxNss());
        assertEquals(rxNss, info.getRxNss());
    }

    @Test
    public void testBuilderWithDefaultValues() {
        assumeTrue(Environment.isSdkNewerThanB());
        int wifiStandard = ScanResult.WIFI_STANDARD_11AC;
        int channelWidth = ScanResult.CHANNEL_WIDTH_160MHZ;

        WifiP2pConnectionInfo info = new WifiP2pConnectionInfo.Builder(
                wifiStandard, channelWidth).build();

        assertEquals(wifiStandard, info.getWifiStandard());
        assertEquals(channelWidth, info.getChannelWidth());
        assertEquals(WifiP2pConnectionInfo.UNSPECIFIED, info.getTxNss());
        assertEquals(WifiP2pConnectionInfo.UNSPECIFIED, info.getRxNss());
    }

    @Test
    public void testBuilderSetTxNssThrowsException() {
        assumeTrue(Environment.isSdkNewerThanB());
        WifiP2pConnectionInfo.Builder builder = new WifiP2pConnectionInfo.Builder(
                ScanResult.WIFI_STANDARD_11N, ScanResult.CHANNEL_WIDTH_20MHZ);
        assertThrows(IllegalArgumentException.class, () -> builder.setTxNss(-2));
        assertThrows(IllegalArgumentException.class, () -> builder.setTxNss(5));
    }

    @Test
    public void testBuilderSetRxNssThrowsException() {
        assumeTrue(Environment.isSdkNewerThanB());
        WifiP2pConnectionInfo.Builder builder = new WifiP2pConnectionInfo.Builder(
                ScanResult.WIFI_STANDARD_11N, ScanResult.CHANNEL_WIDTH_20MHZ);
        assertThrows(IllegalArgumentException.class, () -> builder.setRxNss(-2));
        assertThrows(IllegalArgumentException.class, () -> builder.setRxNss(5));
    }

    @Test
    public void testBuilderWithUnspecifiedNss() {
        assumeTrue(Environment.isSdkNewerThanB());
        WifiP2pConnectionInfo info = new WifiP2pConnectionInfo.Builder(
                ScanResult.WIFI_STANDARD_11AX, ScanResult.CHANNEL_WIDTH_80MHZ)
                .setTxNss(WifiP2pConnectionInfo.UNSPECIFIED)
                .setRxNss(WifiP2pConnectionInfo.UNSPECIFIED)
                .build();
        assertEquals(WifiP2pConnectionInfo.UNSPECIFIED, info.getTxNss());
        assertEquals(WifiP2pConnectionInfo.UNSPECIFIED, info.getRxNss());
    }

    @Test
    public void testEqualsAndHashCode() {
        assumeTrue(Environment.isSdkNewerThanB());
        WifiP2pConnectionInfo info1 = new WifiP2pConnectionInfo.Builder(
                ScanResult.WIFI_STANDARD_11AX, ScanResult.CHANNEL_WIDTH_80MHZ)
                .setTxNss(2)
                .setRxNss(2)
                .build();

        WifiP2pConnectionInfo info2 = new WifiP2pConnectionInfo.Builder(
                ScanResult.WIFI_STANDARD_11AX, ScanResult.CHANNEL_WIDTH_80MHZ)
                .setTxNss(2)
                .setRxNss(2)
                .build();

        WifiP2pConnectionInfo info3 = new WifiP2pConnectionInfo.Builder(
                ScanResult.WIFI_STANDARD_11AC, ScanResult.CHANNEL_WIDTH_80MHZ)
                .setTxNss(2)
                .setRxNss(2)
                .build();

        assertEquals(info1, info2);
        assertEquals(info1.hashCode(), info2.hashCode());
        assertNotEquals(info1, info3);
    }

    @Test
    public void testToString() {
        assumeTrue(Environment.isSdkNewerThanB());
        int wifiStandard = ScanResult.WIFI_STANDARD_11AX;
        int channelWidth = ScanResult.CHANNEL_WIDTH_80MHZ;
        int txNss = 2;
        int rxNss = 2;

        WifiP2pConnectionInfo info = new WifiP2pConnectionInfo.Builder(wifiStandard, channelWidth)
                .setTxNss(txNss)
                .setRxNss(rxNss)
                .build();

        String expected = "WifiP2pConnectionInfo:"
                + "\n wifiStandard: " + wifiStandard
                + "\n channelWidth: " + channelWidth
                + "\n txNss: " + txNss
                + "\n rxNss: " + rxNss;
        assertEquals(expected, info.toString());
    }

    @Test
    public void testParcelOperation() {
        assumeTrue(Environment.isSdkNewerThanB());
        WifiP2pConnectionInfo info = new WifiP2pConnectionInfo.Builder(
                ScanResult.WIFI_STANDARD_11AX,
                ScanResult.CHANNEL_WIDTH_80MHZ)
                .setTxNss(2)
                .setRxNss(2).build();

        Parcel parcelW = Parcel.obtain();
        info.writeToParcel(parcelW, 0);
        byte[] bytes = parcelW.marshall();
        parcelW.recycle();

        Parcel parcelR = Parcel.obtain();
        parcelR.unmarshall(bytes, 0, bytes.length);
        parcelR.setDataPosition(0);
        WifiP2pConnectionInfo fromParcel = WifiP2pConnectionInfo.CREATOR.createFromParcel(parcelR);

        assertEquals(info, fromParcel);
    }
}
