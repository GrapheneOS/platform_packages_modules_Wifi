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

import android.util.Log;

import com.android.server.wifi.nl80211.Nl80211Native;
import com.android.wifi.flags.Flags;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.List;

/**
 * WifiPowerStatsManager handles the business logic for retrieving and processing
 * Wi-Fi power consumption statistics using vendor commands.
 * It is responsible for calling the native layer, handling errors, and transforming
 * raw data into the immutable WifiChipStats data model.
 */
public class WifiPowerStatsManager {

    private static final String TAG = "WifiPwrStatsManager";

    private static final int MAX_HISTORY_SIZE = 10;
    private final ArrayDeque<String> mHistoryList = new ArrayDeque<>(MAX_HISTORY_SIZE);
    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("MM-dd HH:mm:ss.SSS").withZone(ZoneId.systemDefault());

    private final Nl80211Native mNl80211Native;
    private final ActiveModeWarden mActiveModeWarden;

    private static final class PwrStatAttribute {
        /* Reserved for INVALID = 0; */
        static final int NUM_WIFI_CORE = 1;
        static final int RADIO_INFO = 2;
        static final int TX_RATE_INFO = 3;
        static final int RX_RATE_INFO = 4;
        static final int CHIP_POWER_STATS = 5;
        /* Reserved for STATS_MAX = 6; */
    }

    private static final int TLV_HEADER_SIZE = 4;

    /**
     * Constructs a new WifiPowerStatsManager.
     */
    public WifiPowerStatsManager(Nl80211Native nl80211Native,
            ActiveModeWarden activeModeWarden) {
        this.mNl80211Native = nl80211Native;
        this.mActiveModeWarden = activeModeWarden;
        Log.i(TAG, "WifiPowerStatsManager initialized.");
    }

    /**
     * Retrieves the interface name for fetching power statistics.
     * Prioritizes primary client, then tethered/local-only SoftAP.
     *
     * Note: The driver reports chip-wide statistics across all interfaces
     * regardless of the specific interface used.
     *
     * @return The interface name if found, or null otherwise.
     */
    private String getInterfaceName() {
        String interfaceName = mActiveModeWarden.getPrimaryClientModeManager()
                .getInterfaceName();
        if (interfaceName != null) {
            return interfaceName;
        }

        SoftApManager softApManager = mActiveModeWarden.getTetheredSoftApManager();
        if (softApManager != null) {
            interfaceName = softApManager.getInterfaceName();
            if (interfaceName != null) {
                return interfaceName;
            }
        }
        softApManager = mActiveModeWarden.getLocalOnlySoftApManager();
        if (softApManager != null) {
            interfaceName = softApManager.getInterfaceName();
            if (interfaceName != null) {
                return interfaceName;
            }
        }
        return null;
    }

    /**
     * Executes the vendor command to retrieve Wi-Fi power statistics from the driver.
     *
     * @return An immutable WifiChipStats snapshot upon success, or null on failure.
     */
    public WifiChipStats getWlanPwrStats() {
        String interfaceName = getInterfaceName();

        if (interfaceName == null) {
            Log.e(TAG, "Interface name is null, cannot retrieve power stats.");
            return null;
        }

        if (!Flags.powerStatsApi()) {
            Log.e(TAG, "Power stats API is not enabled, returning empty stats.");
            return WifiChipStats.makeWifiChipStats(0, new WifiChipStats.CoreRadioStats[0],
                    new WifiChipStats.TxRateStats[0], new WifiChipStats.RxRateStats[0],
                    new WifiChipStats.ChipPowerState(0, 0, new int[0]));
        }
        Log.i(TAG, "Executing getWlanPwrStats() via Nl80211poxy for iface: " + interfaceName);

        if (mNl80211Native == null) {
            Log.e(TAG, "Nl80211Native dependency is null. Cannot proceed.");
            return null;
        }

        ByteBuffer rawDataBuffer = mNl80211Native.getWifiChipStats(interfaceName);

        if (rawDataBuffer == null || rawDataBuffer.limit() == 0) {
            Log.e(TAG, "Vendor Command failed or returned empty data for iface: " + interfaceName);
            return null;
        }

        WifiChipStats stats = transformBufferToStats(rawDataBuffer);

        if (stats == null) {
            Log.e(TAG, "Failed to transform/parse power stats data.");
            return null;
        }

        addToHistory(stats.toString());
        Log.i(TAG, "Successfully retrieved power stats: " + stats);

        return stats;
    }

    private void addToHistory(String msg) {
        synchronized (mHistoryList) {
            if (mHistoryList.size() >= MAX_HISTORY_SIZE) {
                mHistoryList.removeFirst();
            }
            String timestamp = DATE_TIME_FORMATTER.format(Instant.now());
            mHistoryList.addLast(timestamp + " " + msg);
        }
    }

    /**
     * Dumps the power statistics history.
     */
    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        if (!Flags.powerStatsApi()) {
            pw.println("WifiPowerStats: Power stats API is disabled.");
            return;
        }
        pw.println("Dump of WifiPowerStatsManager:");
        pw.println("--- WifiPowerStats History ---");
        synchronized (mHistoryList) {
            for (String log : mHistoryList) {
                pw.println(log);
            }
        }
        pw.println("--- End of WifiPowerStats History ---");
    }

    private WifiChipStats transformBufferToStats(ByteBuffer rawDataBuffer) {
        try {
            Log.i(TAG, "Parsing Vendor Data (Length: " + rawDataBuffer.limit() + " bytes):");

            rawDataBuffer.order(java.nio.ByteOrder.LITTLE_ENDIAN);
            rawDataBuffer.rewind();

            int numWifiCore = 0;

            ByteBuffer radioInfoBuffer = null;
            ByteBuffer txRateInfoBuffer = null;
            ByteBuffer rxRateInfoBuffer = null;
            ByteBuffer chipPowerStatsBuffer = null;

            while (rawDataBuffer.remaining() >= TLV_HEADER_SIZE) {

                int length = rawDataBuffer.getShort() & 0xFFFF;
                int attributeType = rawDataBuffer.getShort() & 0xFFFF;

                int valueLength = length - TLV_HEADER_SIZE;

                if (valueLength < 0 || rawDataBuffer.remaining() < valueLength) {
                    Log.e(TAG, "Invalid TLV length (" + length + ") or buffer underflow.");
                    return null;
                }

                if (attributeType == PwrStatAttribute.NUM_WIFI_CORE) {
                    if (valueLength < Integer.BYTES) {
                        Log.e(TAG, "NUM_WIFI_CORE value too short.");
                        return null;
                    }
                    numWifiCore = rawDataBuffer.getInt();
                    rawDataBuffer.position(rawDataBuffer.position()
                                + (valueLength - Integer.BYTES));
                } else if (attributeType == PwrStatAttribute.RADIO_INFO) {
                    radioInfoBuffer = rawDataBuffer.slice();
                    radioInfoBuffer.limit(valueLength);
                    rawDataBuffer.position(rawDataBuffer.position() + valueLength);
                } else if (attributeType == PwrStatAttribute.TX_RATE_INFO) {
                    txRateInfoBuffer = rawDataBuffer.slice();
                    txRateInfoBuffer.limit(valueLength);
                    rawDataBuffer.position(rawDataBuffer.position() + valueLength);
                } else if (attributeType == PwrStatAttribute.RX_RATE_INFO) {
                    rxRateInfoBuffer = rawDataBuffer.slice();
                    rxRateInfoBuffer.limit(valueLength);
                    rawDataBuffer.position(rawDataBuffer.position() + valueLength);
                } else if (attributeType == PwrStatAttribute.CHIP_POWER_STATS) {
                    chipPowerStatsBuffer = rawDataBuffer.slice();
                    chipPowerStatsBuffer.limit(valueLength);
                    rawDataBuffer.position(rawDataBuffer.position() + valueLength);
                } else {
                    Log.w(TAG, "Ignoring unknown attribute type: " + attributeType);
                    rawDataBuffer.position(rawDataBuffer.position() + valueLength);
                }
            }

            if (numWifiCore <= 0 || radioInfoBuffer == null || txRateInfoBuffer == null
                    || rxRateInfoBuffer == null || chipPowerStatsBuffer == null) {
                Log.e(TAG, "Missing essential power stats attributes.");
                return null;
            }

            return createPowerStatsFromBuffer(radioInfoBuffer, txRateInfoBuffer, rxRateInfoBuffer,
                    chipPowerStatsBuffer, numWifiCore);
        } catch (BufferUnderflowException e) {
            Log.e(TAG, "BufferUnderflow during power stats parsing.", e);
            return null;
        } catch (Exception e) {
            Log.e(TAG, "Generic error during power stats transformation.", e);
            return null;
        }
    }

    private WifiChipStats createPowerStatsFromBuffer(
            ByteBuffer radioInfoBuffer, ByteBuffer txRateInfoBuffer, ByteBuffer rxRateInfoBuffer,
            ByteBuffer chipPowerStatsBuffer, int numWifiCore) {

        WifiChipStats.CoreRadioStats[] coreStatsArray = parseCoreStatsArray(
                radioInfoBuffer, numWifiCore);
        if (coreStatsArray == null) {
            Log.e(TAG, "Failed to parse CoreRadioStats array from buffer.");
            return null;
        }

        WifiChipStats.TxRateStats[] txRateStats = parseTxRateStatsArray(
                txRateInfoBuffer, numWifiCore);
        if (txRateStats == null) {
            Log.e(TAG, "Failed to parse TxRateStats array from buffer.");
            return null;
        }

        WifiChipStats.RxRateStats[] rxRateStats = parseRxRateStatsArray(
                rxRateInfoBuffer, numWifiCore);
        if (rxRateStats == null) {
            Log.e(TAG, "Failed to parse RxRateStats array from buffer.");
            return null;
        }

        WifiChipStats.ChipPowerState chipPowerState = parseChipPowerState(chipPowerStatsBuffer);
        if (chipPowerState == null) {
            Log.e(TAG, "Failed to parse ChipPowerState from buffer.");
            return null;
        }

        return WifiChipStats.makeWifiChipStats(
                numWifiCore, coreStatsArray, txRateStats, rxRateStats, chipPowerState);
    }

    private List<List<WifiChipStats.RateInfo>> parseRateStatsList(
            ByteBuffer rateInfoBuffer, int numCores) {
        try {
            rateInfoBuffer.order(java.nio.ByteOrder.LITTLE_ENDIAN);
            rateInfoBuffer.rewind();

            List<List<WifiChipStats.RateInfo>> allCoreRateStats = new java.util.ArrayList<>();

            for (int i = 0; i < numCores; i++) {
                int coreIndex = rateInfoBuffer.getInt();
                if (coreIndex < 0 || coreIndex >= numCores) {
                    Log.e(TAG, "Invalid core index " + coreIndex
                            + " read for expected numCores: " + numCores);
                    return null;
                }

                int numRates = rateInfoBuffer.getInt();

                List<WifiChipStats.RateInfo> rateInfoList = new java.util.ArrayList<>(numRates);
                for (int j = 0; j < numRates; j++) {
                    int rateIndex = rateInfoBuffer.getInt();
                    int band = rateInfoBuffer.getInt();
                    int bw = rateInfoBuffer.getInt();
                    int nss = rateInfoBuffer.getInt();
                    int count = rateInfoBuffer.getInt();
                    rateInfoList.add(new WifiChipStats.RateInfo(rateIndex, band, bw, nss, count));
                }
                allCoreRateStats.add(rateInfoList);
            }
            return allCoreRateStats;
        } catch (Exception e) {
            Log.e(TAG, "Failed to parse RateStats list.", e);
            return null;
        }
    }

    private WifiChipStats.TxRateStats[] parseTxRateStatsArray(
            ByteBuffer rateInfoBuffer, int numCores) {
        List<List<WifiChipStats.RateInfo>> allCoreRateStats =
                parseRateStatsList(rateInfoBuffer, numCores);
        if (allCoreRateStats == null) {
            return null;
        }

        WifiChipStats.TxRateStats[] txRateStatsArray = new WifiChipStats.TxRateStats[numCores];
        for (int i = 0; i < numCores; i++) {
            txRateStatsArray[i] = new WifiChipStats.TxRateStats(i, allCoreRateStats.get(i));
        }
        return txRateStatsArray;
    }

    private WifiChipStats.RxRateStats[] parseRxRateStatsArray(
            ByteBuffer rateInfoBuffer, int numCores) {
        List<List<WifiChipStats.RateInfo>> allCoreRateStats =
                parseRateStatsList(rateInfoBuffer, numCores);
        if (allCoreRateStats == null) {
            return null;
        }

        WifiChipStats.RxRateStats[] rxRateStatsArray = new WifiChipStats.RxRateStats[numCores];
        for (int i = 0; i < numCores; i++) {
            rxRateStatsArray[i] = new WifiChipStats.RxRateStats(i, allCoreRateStats.get(i));
        }
        return rxRateStatsArray;
    }

    private WifiChipStats.ChipPowerState parseChipPowerState(ByteBuffer chipPwrInfoBuffer) {
        try {
            chipPwrInfoBuffer.order(java.nio.ByteOrder.LITTLE_ENDIAN);
            chipPwrInfoBuffer.rewind();

            int wlanPwrOnTimeMs = chipPwrInfoBuffer.getInt();
            int sleepLevelsNum = chipPwrInfoBuffer.getInt();

            int[] sleepTimeMsPerLevels = new int[sleepLevelsNum];
            for (int i = 0; i < sleepLevelsNum; i++) {
                sleepTimeMsPerLevels[i] = chipPwrInfoBuffer.getInt();
            }

            return new WifiChipStats.ChipPowerState(
                wlanPwrOnTimeMs, sleepLevelsNum, sleepTimeMsPerLevels
            );

        } catch (Exception e) {
            Log.e(TAG, "Failed to parse ChipPowerState from buffer.", e);
            return null;
        }
    }

    private WifiChipStats.CoreRadioStats[] parseCoreStatsArray(
            ByteBuffer radioInfoBuffer, int numCores) {

        try {
            radioInfoBuffer.order(java.nio.ByteOrder.LITTLE_ENDIAN);
            radioInfoBuffer.rewind();

            WifiChipStats.CoreRadioStats[] coreStatsArray =
                    new WifiChipStats.CoreRadioStats[numCores];

            for (int i = 0; i < numCores; i++) {
                int coreIndex = radioInfoBuffer.getInt();
                int radioOnTimeMs = radioInfoBuffer.getInt();
                int txTimeMs = radioInfoBuffer.getInt();
                int rxTimeMs = radioInfoBuffer.getInt();
                int rxListeningLevelsNum = radioInfoBuffer.getInt();

                int[] rxListeningTimeMsPerLevels = new int[rxListeningLevelsNum];
                for (int j = 0; j < rxListeningLevelsNum; j++) {
                    rxListeningTimeMsPerLevels[j] = radioInfoBuffer.getInt();
                }

                coreStatsArray[i] = new WifiChipStats.CoreRadioStats(
                    coreIndex, radioOnTimeMs, txTimeMs, rxTimeMs,
                    rxListeningLevelsNum, rxListeningTimeMsPerLevels
                );
            }

            return coreStatsArray;

        } catch (Exception e) {
            Log.e(TAG, "Failed to parse CoreRadioStats array.", e);
            return null;
        }
    }
}
