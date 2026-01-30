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

import java.util.Arrays;
import java.util.List;

/**
 * WifiChipStats represents a snapshot of Wi-Fi chip statistics,
 * including per-core radio and rate information, and overall chip power state.
 */
public class WifiChipStats {

    public final int numWifiCore;
    public final CoreRadioStats[] coreStats;
    public final TxRateStats[] txRateStats;
    public final RxRateStats[] rxRateStats;
    public final ChipPowerState chipPowerState;

    private WifiChipStats(
            int numWifiCore,
            CoreRadioStats[] coreStats,
            TxRateStats[] txRateStats, RxRateStats[] rxRateStats,
            ChipPowerState chipPowerState
    ) {
        this.numWifiCore = numWifiCore;
        this.coreStats = coreStats;
        this.txRateStats = txRateStats;
        this.rxRateStats = rxRateStats;
        this.chipPowerState = chipPowerState;
    }

    /**
     * Factory method to create a new, immutable instance of WifiChipStats.
     *
     * @param numWifiCore The number of Wi-Fi cores reported by the driver.
     * @param coreStats Array of per-core radio usage statistics.
     * @param txRateStats Array of per-core TX rate statistics.
     * @param rxRateStats Array of per-core RX rate statistics.
     * @param chipPowerState Overall chip power and sleep level durations.
     * @return A new {@code WifiChipStats} instance populated with the parsed data.
     */
    public static WifiChipStats makeWifiChipStats(
            int numWifiCore,
            CoreRadioStats[] coreStats,
            TxRateStats[] txRateStats, RxRateStats[] rxRateStats,
            ChipPowerState chipPowerState
    ) {
        return new WifiChipStats(numWifiCore, coreStats, txRateStats, rxRateStats, chipPowerState);
    }

    @Override
    public String toString() {
        return "WifiChipStats{"
                + "numWifiCore=" + numWifiCore
                + ", coreStats=" + Arrays.toString(coreStats)
                + ", txRateStats=" + Arrays.toString(txRateStats)
                + ", rxRateStats=" + Arrays.toString(rxRateStats)
                + ", chipPowerState=" + chipPowerState
                + '}';
    }

    /**
     * This class defines radio statistics for a single Wi-Fi core.
     */
    public static class CoreRadioStats {
        // Radio Stats (per core) parameters
        public final int coreIndex;         // e.g., Main core, Aux core
        public final int radioOnTimeMs;     // Duration the radio is on (unit: ms)
        public final int txTimeMs;          // Duration the radio is in transmit state (unit: ms)
        public final int rxTimeMs;    // Duration the radio is in receiving data state (unit: ms)
        public final int rxListeningLevelsNum; // Number of receiving idle/listening levels
        public final int[] rxListeningTimeMsPerLevels; // Duration in each rx idle/listening
                                                       // state (unit: ms)

        /**
         * Constructor for CoreRadioStats.
         */
        public CoreRadioStats(int coreIndex, int radioOnTimeMs, int txTimeMs, int rxTimeMs,
                                     int rxListeningLevelsNum, int[] rxListeningTimeMsPerLevels) {
            this.coreIndex = coreIndex;
            this.radioOnTimeMs = radioOnTimeMs;
            this.txTimeMs = txTimeMs;
            this.rxTimeMs = rxTimeMs;
            this.rxListeningLevelsNum = rxListeningLevelsNum;
            this.rxListeningTimeMsPerLevels = rxListeningTimeMsPerLevels;
        }

        @Override
        public String toString() {
            return "CoreStats{"
                    + "coreIndex=" + coreIndex
                    + ", radioOnTimeMs=" + radioOnTimeMs
                    + ", txTimeMs=" + txTimeMs
                    + ", rxTimeMs=" + rxTimeMs
                    + ", rxListenLvlNum=" + rxListeningLevelsNum
                    + ", rxListenLvlTimeMs=" + Arrays.toString(rxListeningTimeMsPerLevels)
                    + '}';
        }
    }

    /**
     * This class defines the detailed information for a single transmit or receive data rate.
     */
    public static class RateInfo {
        public final int rateIndex; // Index representing a specific data rate (CCK/OFDM, MCS rates)
        public final int band;      // Operating band (0: 2.4 GHz, 1: 5 GHz, 2: 6 GHz)
        public final int bw;        // Bandwidth (0: 20 MHz, 1: 40 MHz, 2: 80 MHz, etc.)
        public final int nss;       // Number of spatial streams (0: NSS1, 1: NSS2, etc.)
        public final int count;     // Number of frames (or packets) transmitted or received
                                    // using this specific rate combination.

        /**
         * Constructor for RateInfo.
         */
        public RateInfo(int rateIndex, int band, int bw, int nss, int count) {
            this.rateIndex = rateIndex;
            this.band = band;
            this.bw = bw;
            this.nss = nss;
            this.count = count;
        }

        @Override
        public String toString() {
            return "RateInfo{idx=" + rateIndex
                    + ", band=" + band
                    + ", bw=" + bw
                    + ", nss=" + nss
                    + ", count=" + count
                    + '}';
        }
    }

    /**
     * TX rate statistics for a single Wi-Fi core.
     */
    public static class TxRateStats {
        public final int coreIndex;
        public final List<RateInfo> rates;

        public TxRateStats(int coreIndex, List<RateInfo> rates) {
            this.coreIndex = coreIndex;
            this.rates = rates;
        }

        @Override
        public String toString() {
            return "TxRateStats{"
                    + "coreIndex=" + coreIndex
                    + ", rates=" + rates
                    + '}';
        }
    }

    /**
     * RX rate statistics for a single Wi-Fi core.
     */
    public static class RxRateStats {
        public final int coreIndex;
        public final List<RateInfo> rates;

        public RxRateStats(int coreIndex, List<RateInfo> rates) {
            this.coreIndex = coreIndex;
            this.rates = rates;
        }

        @Override
        public String toString() {
            return "RxRateStats{"
                    + "coreIndex=" + coreIndex
                    + ", rates=" + rates
                    + '}';
        }
    }

    /**
     * This class defines the overall power state parameters for the Wi-Fi chip.
     */
    public static class ChipPowerState {
        public final int wlanPwrOnTimeMs;        // Total time the chip is powered on (unit: ms)
        public final int sleepLevelsNum;        // The number of sleep levels
        public final int[] sleepTimeMsPerLevels; // Total time in each sleep level (unit: ms)

        /**
         * Constructor for ChipPowerState.
         */
        public ChipPowerState(int wlanPwrOnTimeMs, int sleepLevelsNum, int[] sleepTimeMsPerLevels) {
            this.wlanPwrOnTimeMs = wlanPwrOnTimeMs;
            this.sleepLevelsNum = sleepLevelsNum;
            this.sleepTimeMsPerLevels = sleepTimeMsPerLevels;
        }

        @Override
        public String toString() {
            return "ChipPowerState{onTimeMs=" + wlanPwrOnTimeMs
                    + ", sleepLvlNum=" + sleepLevelsNum
                    + ", sleepTimeMs=" + Arrays.toString(sleepTimeMsPerLevels)
                    + '}';
        }
    }
}

