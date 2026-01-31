/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.server.wifi;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Collections;

/**
 * Unit tests for {@link com.android.server.wifi.WifiChipStats}.
 */
public class WifiChipStatsTest {

    @Test
    public void testWifiChipStatsConstructionAndToString() {
        // Test CoreRadioStats
        WifiChipStats.CoreRadioStats coreRadioStats = new WifiChipStats.CoreRadioStats(
                0, 100, 20, 30, 2, new int[]{10, 20});
        assertEquals(0, coreRadioStats.coreIndex);
        assertEquals(100, coreRadioStats.radioOnTimeMs);
        assertTrue(coreRadioStats.toString().contains("coreIndex=0"));

        // Test RateInfo
        WifiChipStats.RateInfo rateInfo = new WifiChipStats.RateInfo(1, 2, 3, 4, 5);
        assertEquals(1, rateInfo.rateIndex);
        assertEquals(5, rateInfo.count);
        assertTrue(rateInfo.toString().contains("idx=1"));

        // Test TxRateStats
        WifiChipStats.TxRateStats txRateStats = new WifiChipStats.TxRateStats(
                0, Collections.singletonList(rateInfo));
        assertEquals(0, txRateStats.coreIndex);
        assertEquals(1, txRateStats.rates.size());
        assertTrue(txRateStats.toString().contains("coreIndex=0"));

        // Test RxRateStats
        WifiChipStats.RxRateStats rxRateStats = new WifiChipStats.RxRateStats(
                0, Collections.singletonList(rateInfo));
        assertEquals(0, rxRateStats.coreIndex);
        assertEquals(1, rxRateStats.rates.size());
        assertTrue(rxRateStats.toString().contains("coreIndex=0"));

        // Test ChipPowerState
        WifiChipStats.ChipPowerState chipPowerState = new WifiChipStats.ChipPowerState(
                1000, 2, new int[]{100, 200});
        assertEquals(1000, chipPowerState.wlanPwrOnTimeMs);
        assertEquals(2, chipPowerState.sleepLevelsNum);
        assertTrue(chipPowerState.toString().contains("onTimeMs=1000"));

        // Test WifiChipStats factory and top-level toString
        WifiChipStats wifiChipStats = WifiChipStats.makeWifiChipStats(
                1,
                new WifiChipStats.CoreRadioStats[]{coreRadioStats},
                new WifiChipStats.TxRateStats[]{txRateStats},
                new WifiChipStats.RxRateStats[]{rxRateStats},
                chipPowerState);

        assertEquals(1, wifiChipStats.numWifiCore);
        assertEquals(coreRadioStats, wifiChipStats.coreStats[0]);
        assertEquals(txRateStats, wifiChipStats.txRateStats[0]);
        assertEquals(rxRateStats, wifiChipStats.rxRateStats[0]);
        assertEquals(chipPowerState, wifiChipStats.chipPowerState);
        assertTrue(wifiChipStats.toString().contains("numWifiCore=1"));
    }
}
