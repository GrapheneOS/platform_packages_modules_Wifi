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

package com.android.server.wifi.nl80211;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import android.net.MacAddress;

import androidx.test.filters.SmallTest;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Unit tests for {@link com.android.server.wifi.nl80211.NativeScanResult}.
 */
@SmallTest
public class NativeScanResultTest {
    private static final byte[] SSID = new byte[] {'t', 'e', 's', 't'};
    private static final byte[] BSSID = new byte[] {0x1, 0x2, 0x3, 0x4, 0x5, 0x6};
    private static final byte[] IE = new byte[] {'i', 'e'};
    private static final int FREQUENCY = 2412;
    private static final int SIGNAL_MBM = -4000;
    private static final long TSF = 12345;
    private static final int CAPABILITY = 0x11;
    private static final boolean ASSOCIATED = true;
    private static final int RADIO_CHAIN_ID = 0;
    private static final int RADIO_CHAIN_LEVEL = -80;


    /**
     * Verify that the copy constructor works as expected.
     */
    @Test
    public void testCopyConstructor() {
        // Create a wificond NativeScanResult
        android.net.wifi.nl80211.NativeScanResult wificondScanResult =
                new android.net.wifi.nl80211.NativeScanResult();
        wificondScanResult.ssid = SSID;
        wificondScanResult.bssid = BSSID;
        wificondScanResult.infoElement = IE;
        wificondScanResult.frequency = FREQUENCY;
        wificondScanResult.signalMbm = SIGNAL_MBM;
        wificondScanResult.tsf = TSF;
        wificondScanResult.capability = CAPABILITY;
        wificondScanResult.associated = ASSOCIATED;
        List<android.net.wifi.nl80211.RadioChainInfo> wificondRadioChainInfos = new ArrayList<>();
        android.net.wifi.nl80211.RadioChainInfo wificondRadioChainInfo =
                new android.net.wifi.nl80211.RadioChainInfo(RADIO_CHAIN_ID, RADIO_CHAIN_LEVEL);
        wificondRadioChainInfos.add(wificondRadioChainInfo);
        wificondScanResult.radioChainInfos = wificondRadioChainInfos;

        // Copy it to the server-side NativeScanResult
        NativeScanResult nativeScanResult = new NativeScanResult(wificondScanResult);

        // Verify that all fields are copied correctly
        assertArrayEquals(SSID, nativeScanResult.getSsid());
        assertEquals(MacAddress.fromBytes(BSSID), nativeScanResult.getBssid());
        assertArrayEquals(IE, nativeScanResult.getInformationElements());
        assertEquals(FREQUENCY, nativeScanResult.getFrequencyMhz());
        assertEquals(SIGNAL_MBM, nativeScanResult.getSignalMbm());
        assertEquals(TSF, nativeScanResult.getTsf());
        assertEquals(CAPABILITY, nativeScanResult.getCapabilities());
        assertEquals(ASSOCIATED, nativeScanResult.isAssociated());
        assertNotNull(nativeScanResult.getRadioChainInfos());
        assertEquals(1, nativeScanResult.getRadioChainInfos().size());
        assertEquals(RADIO_CHAIN_ID, nativeScanResult.getRadioChainInfos().get(0).getChainId());
        assertEquals(RADIO_CHAIN_LEVEL, nativeScanResult.getRadioChainInfos().get(0).level);
    }
}
