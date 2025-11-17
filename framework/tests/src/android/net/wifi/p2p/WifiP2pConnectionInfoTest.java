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

        WifiP2pConnectionInfo info = new WifiP2pConnectionInfo(
                wifiStandard, channelWidth, txNss, rxNss);

        assertEquals(wifiStandard, info.getWifiStandard());
        assertEquals(channelWidth, info.getChannelWidth());
        assertEquals(txNss, info.getTxNss());
        assertEquals(rxNss, info.getRxNss());
    }

    @Test
    public void testParcelOperation() {
        assumeTrue(Environment.isSdkNewerThanB());
        WifiP2pConnectionInfo info = new WifiP2pConnectionInfo(
                ScanResult.WIFI_STANDARD_11AX,
                ScanResult.CHANNEL_WIDTH_80MHZ,
                2,
                2);

        Parcel parcelW = Parcel.obtain();
        info.writeToParcel(parcelW, 0);
        byte[] bytes = parcelW.marshall();
        parcelW.recycle();

        Parcel parcelR = Parcel.obtain();
        parcelR.unmarshall(bytes, 0, bytes.length);
        parcelR.setDataPosition(0);
        WifiP2pConnectionInfo fromParcel = WifiP2pConnectionInfo.CREATOR.createFromParcel(parcelR);

        assertEquals(info.getWifiStandard(), fromParcel.getWifiStandard());
        assertEquals(info.getChannelWidth(), fromParcel.getChannelWidth());
        assertEquals(info.getTxNss(), fromParcel.getTxNss());
        assertEquals(info.getRxNss(), fromParcel.getRxNss());
    }
}
