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

import static android.system.OsConstants.EBUSY;
import static android.system.OsConstants.EINVAL;
import static android.system.OsConstants.ENODEV;
import static android.system.OsConstants.ENOENT;

import static com.android.net.module.util.netlink.StructNlMsgHdr.NLM_F_ACK;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_ATTR_BSS;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_ATTR_EXT_FEATURES;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_ATTR_FEATURE_FLAGS;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_ATTR_IFINDEX;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_ATTR_IFNAME;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_ATTR_MAC;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_ATTR_MAX_MATCH_SETS;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_ATTR_MAX_NUM_AKM_SUITES;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_ATTR_MAX_NUM_SCAN_SSIDS;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_ATTR_MAX_NUM_SCHED_SCAN_PLANS;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_ATTR_MAX_NUM_SCHED_SCAN_SSIDS;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_ATTR_MAX_SCAN_PLAN_INTERVAL;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_ATTR_MAX_SCAN_PLAN_ITERATIONS;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_ATTR_PROTOCOL_FEATURES;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_ATTR_REG_ALPHA2;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_ATTR_SCAN_FLAGS;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_ATTR_SCAN_FREQUENCIES;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_ATTR_SCAN_SSIDS;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_ATTR_SCHED_SCAN_INTERVAL;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_ATTR_SCHED_SCAN_MATCH;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_ATTR_SCHED_SCAN_PLANS;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_ATTR_SPLIT_WIPHY_DUMP;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_ATTR_WIPHY;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_ATTR_WIPHY_BANDS;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_BAND_ATTR_FREQS;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_BAND_ATTR_HT_CAPA;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_BAND_ATTR_HT_MCS_SET;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_BAND_ATTR_IFTYPE_DATA;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_BAND_ATTR_VHT_CAPA;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_BAND_ATTR_VHT_MCS_SET;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_BAND_IFTYPE_ATTR_EHT_CAP_PHY;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_BAND_IFTYPE_ATTR_HE_CAP_MCS_SET;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_BAND_IFTYPE_ATTR_HE_CAP_PHY;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_BSS_BEACON_TSF;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_BSS_BSSID;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_BSS_CAPABILITY;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_BSS_CHAIN_SIGNAL;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_BSS_FREQUENCY;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_BSS_INFORMATION_ELEMENTS;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_BSS_LAST_SEEN_BOOTTIME;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_BSS_SIGNAL_MBM;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_BSS_STATUS;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_BSS_STATUS_ASSOCIATED;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_BSS_STATUS_AUTHENTICATED;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_BSS_TSF;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_CMD_ABORT_SCAN;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_CMD_GET_INTERFACE;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_CMD_GET_PROTOCOL_FEATURES;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_CMD_GET_REG;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_CMD_GET_SCAN;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_CMD_GET_WIPHY;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_CMD_NEW_SCAN_RESULTS;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_CMD_NEW_WIPHY;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_CMD_START_SCHED_SCAN;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_CMD_STOP_SCHED_SCAN;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_CMD_TRIGGER_SCAN;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_DFS_AVAILABLE;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_DFS_USABLE;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_EXT_FEATURE_HIGH_ACCURACY_SCAN;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_EXT_FEATURE_LOW_POWER_SCAN;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_EXT_FEATURE_LOW_SPAN_SCAN;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_EXT_FEATURE_SCHED_SCAN_RELATIVE_RSSI;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_FEATURE_SCAN_RANDOM_MAC_ADDR;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_FEATURE_SCHED_SCAN_RANDOM_MAC_ADDR;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_FREQUENCY_ATTR_DFS_STATE;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_FREQUENCY_ATTR_DISABLED;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_FREQUENCY_ATTR_FREQ;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_FREQUENCY_ATTR_NO_IR;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_PROTOCOL_FEATURE_SPLIT_WIPHY_DUMP;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_SCAN_FLAG_LOW_POWER;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_SCAN_FLAG_RANDOM_ADDR;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_SCHED_SCAN_MATCH_ATTR_SSID;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_SCHED_SCAN_PLAN_INTERVAL;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_SCHED_SCAN_PLAN_ITERATIONS;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiScanner;
import android.util.ArrayMap;
import android.util.Log;
import android.util.Pair;
import android.util.SparseArray;

import com.android.internal.annotations.VisibleForTesting;
import com.android.net.module.util.netlink.StructNlAttr;
import com.android.net.module.util.netlink.StructNlMsgHdr;

import java.net.NetworkInterface;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Provides nl80211 utility functions, similar to the C++ wificond's netlink_utils.
 * This class is responsible for building requests, sending them via Nl80211Proxy,
 * and parsing the responses into structured Java objects.
 */
public class Nl80211Utils {
    private static final String TAG = "Nl80211Utils";

    // PHY capability constants
    private static final int HT_MCS_SET_NUM_BYTE = 16;
    private static final int VHT_MCS_SET_NUM_BYTE = 8;
    private static final int HE_MCS_SET_NUM_BYTE_MIN = 4;
    private static final int MAX_SPACIAL_STREAMS = 8;
    private static final byte VHT_160MHZ_BIT_MASK = 0x4;
    private static final byte VHT_80P80MHZ_BIT_MASK = 0x8;
    private static final int HE_CAP_PHY_NUM_BYTE = 9;
    private static final byte HE_160MHZ_BIT_MASK = 0x8;
    private static final byte HE_80P80MHZ_BIT_MASK = 0x10;
    private static final int EHT_CAP_PHY_NUM_BYTE = 8;
    private static final byte EHT_320MHZ_BIT_MASK = 0x2;

    private final Nl80211Proxy mNl80211Proxy;
    private boolean mSupportsSplitWiphyDump;

    // Data structures to hold parsed information.

    public static class WiphyFeatures {
        public final boolean supportsRandomMacOneShotScan;
        public final boolean supportsRandomMacSchedScan;
        public final boolean supportsLowSpanOneShotScan;
        public final boolean supportsLowPowerOneShotScan;
        public final boolean supportsHighAccuracyOneShotScan;
        public final boolean supportsTxMgmtFrameMcs;
        public final boolean supportsExtSchedScanRelativeRssi;

        private WiphyFeatures(Builder builder) {
            this.supportsRandomMacOneShotScan = builder.mSupportsRandomMacOneShotScan;
            this.supportsRandomMacSchedScan = builder.mSupportsRandomMacSchedScan;
            this.supportsLowSpanOneShotScan = builder.mSupportsLowSpanOneShotScan;
            this.supportsLowPowerOneShotScan = builder.mSupportsLowPowerOneShotScan;
            this.supportsHighAccuracyOneShotScan = builder.mSupportsHighAccuracyOneShotScan;
            this.supportsTxMgmtFrameMcs = builder.mSupportsTxMgmtFrameMcs;
            this.supportsExtSchedScanRelativeRssi = builder.mSupportsExtSchedScanRelativeRssi;
        }

        public static class Builder {
            private boolean mSupportsRandomMacOneShotScan = false;
            private boolean mSupportsRandomMacSchedScan = false;
            private boolean mSupportsLowSpanOneShotScan = false;
            private boolean mSupportsLowPowerOneShotScan = false;
            private boolean mSupportsHighAccuracyOneShotScan = false;
            private boolean mSupportsTxMgmtFrameMcs = false;
            private boolean mSupportsExtSchedScanRelativeRssi = false;

            /**
             * Sets whether the wiphy supports MAC randomization for one-shot scans.
             */
            public Builder setSupportsRandomMacOneShotScan(boolean val) {
                mSupportsRandomMacOneShotScan = val;
                return this;
            }

            /**
             * Sets whether the wiphy supports MAC randomization for scheduled scans.
             */
            public Builder setSupportsRandomMacSchedScan(boolean val) {
                mSupportsRandomMacSchedScan = val;
                return this;
            }

            /**
             * Sets whether the wiphy supports low span one-shot scans.
             */
            public Builder setSupportsLowSpanOneShotScan(boolean val) {
                mSupportsLowSpanOneShotScan = val;
                return this;
            }

            /**
             * Sets whether the wiphy supports low power one-shot scans.
             */
            public Builder setSupportsLowPowerOneShotScan(boolean val) {
                mSupportsLowPowerOneShotScan = val;
                return this;
            }

            /**
             * Sets whether the wiphy supports high accuracy one-shot scans.
             */
            public Builder setSupportsHighAccuracyOneShotScan(boolean val) {
                mSupportsHighAccuracyOneShotScan = val;
                return this;
            }

            /**
             * Sets whether the wiphy supports TX management frame MCS.
             */
            public Builder setSupportsTxMgmtFrameMcs(boolean val) {
                mSupportsTxMgmtFrameMcs = val;
                return this;
            }

            /**
             * Sets whether the wiphy supports extended scheduled scan relative RSSI.
             */
            public Builder setSupportsExtSchedScanRelativeRssi(boolean val) {
                mSupportsExtSchedScanRelativeRssi = val;
                return this;
            }

            /**
             * Builds a new WiphyFeatures object.
             */
            public WiphyFeatures build() {
                return new WiphyFeatures(this);
            }
        }
    }

    public static class BandInfo {
        @NonNull
        public List<Integer> band2g = new ArrayList<>();
        @NonNull
        public List<Integer> band5g = new ArrayList<>();
        @NonNull
        public List<Integer> band6g = new ArrayList<>();
        @NonNull
        public List<Integer> band60g = new ArrayList<>();
        @NonNull
        public List<Integer> bandDfs = new ArrayList<>();
        public boolean is80211nSupported = false;
        public boolean is80211acSupported = false;
        public boolean is80211axSupported = false;
        public boolean is80211beSupported = false;
        public boolean is160MhzSupported = false;
        public boolean is80p80MhzSupported = false;
        public boolean is320MhzSupported = false;
        public int maxTxStreams = 0;
        public int maxRxStreams = 0;
    }

    public static class ScanCapabilities {
        public final int maxNumScanSsids;
        public final int maxNumSchedScanSsids;
        public final int maxMatchSets;
        public final int maxNumScanPlans;
        public final int maxScanPlanIntervalSeconds;
        public final int maxScanPlanIterations;

        public ScanCapabilities(int maxNumScanSsids, int maxNumSchedScanSsids,
                int maxMatchSets, int maxNumScanPlans, int maxScanPlanIntervalSeconds,
                int maxScanPlanIterations) {
            this.maxNumScanSsids = maxNumScanSsids;
            this.maxNumSchedScanSsids = maxNumSchedScanSsids;
            this.maxMatchSets = maxMatchSets;
            this.maxNumScanPlans = maxNumScanPlans;
            this.maxScanPlanIntervalSeconds = maxScanPlanIntervalSeconds;
            this.maxScanPlanIterations = maxScanPlanIterations;
        }
    }

    public static class DriverCapabilities {
        public final int maxNumAkmSuites;

        public DriverCapabilities(int maxNumAkmSuites) {
            this.maxNumAkmSuites = maxNumAkmSuites;
        }
    }

    /**
     * A container class for all information related to a wiphy device.
     */
    public static class WiphyInfo {
        @NonNull
        public final BandInfo bandInfo;
        @NonNull
        public final ScanCapabilities scanCapabilities;
        @NonNull
        public final WiphyFeatures wiphyFeatures;
        @NonNull
        public final DriverCapabilities driverCapabilities;

        public WiphyInfo(
                @NonNull BandInfo bandInfo,
                @NonNull ScanCapabilities scanCapabilities,
                @NonNull WiphyFeatures wiphyFeatures,
                @NonNull DriverCapabilities driverCapabilities) {
            this.bandInfo = bandInfo;
            this.scanCapabilities = scanCapabilities;
            this.wiphyFeatures = wiphyFeatures;
            this.driverCapabilities = driverCapabilities;
        }
    }

    public static class InterfaceInfo {
        public final int ifIndex;
        public final int wiphyIndex;
        @NonNull
        public final String name;
        @NonNull
        public final byte[] macAddress;

        public InterfaceInfo(int ifIndex, int wiphyIndex, @NonNull String name,
                @NonNull byte[] macAddress) {
            this.ifIndex = ifIndex;
            this.wiphyIndex = wiphyIndex;
            this.name = name;
            this.macAddress = macAddress;
        }
    }

    /**
     * Scan plan settings for a PNO scan.
     */
    public static class PnoScanPlan {
        /**
         * Amount of time in milliseconds between each scan.
         */
        public int intervalMs;
        /**
         * Number of iterations to perform for this plan. This is ignored for the last plan.
         */
        public int iterations;

        public PnoScanPlan(int intervalMs, int iterations) {
            this.intervalMs = intervalMs;
            this.iterations = iterations;
        }
    }

    private final Map<String, Integer> mCachedWiphyIndexes = new ArrayMap<>();
    // TODO(b/394409845): The cached band info must be refreshed when the country code changes.
    private final SparseArray<WiphyInfo> mCachedWiphyInfo = new SparseArray<>();

    public Nl80211Utils(@NonNull Nl80211Proxy nl80211Proxy) {
        mNl80211Proxy = Objects.requireNonNull(nl80211Proxy);
    }

    /**
     * Initialize this instance of Nl80211Utils after Nl80211Proxy has been initialized.
     */
    public void initialize() {
        Integer features = getProtocolFeatures();
        mSupportsSplitWiphyDump = features != null
                && (features & NL80211_PROTOCOL_FEATURE_SPLIT_WIPHY_DUMP) != 0;
    }

    /**
     * @return true if the kernel supports split wiphy dump.
     */
    public boolean isSplitWiphyDumpSupported() {
        return mSupportsSplitWiphyDump;
    }

    /**
     * Get the device phy capabilities for a given interface.
     */
    @Nullable
    public WiphyInfo getWiphyInfo(int wiphyIndex) {
        if (mCachedWiphyInfo.contains(wiphyIndex)) {
            return mCachedWiphyInfo.get(wiphyIndex);
        }

        Nl80211Response response;
        GenericNetlinkMsg request;
        StructNlAttr wiphyIndexAttr = new StructNlAttr(NL80211_ATTR_WIPHY, wiphyIndex);
        if (isSplitWiphyDumpSupported()) {
            StructNlAttr splitWiphyFlagAttr = new StructNlAttr(NL80211_ATTR_SPLIT_WIPHY_DUMP,
                    new byte[0]);
            request = mNl80211Proxy.createNl80211Request(NL80211_CMD_GET_WIPHY,
                    StructNlMsgHdr.NLM_F_DUMP, wiphyIndexAttr, splitWiphyFlagAttr);
        } else {
            request = mNl80211Proxy.createNl80211Request(NL80211_CMD_GET_WIPHY,
                    wiphyIndexAttr);
        }

        response = mNl80211Proxy.sendMessageAndReceiveResponse(request);
        if (response == null || response.isError()) {
            Log.e(TAG, "getWiphyInfo: Failed to send NL80211_CMD_GET_WIPHY");
            return null;
        }

        // Verify that the response is for the wiphy we requested.
        for (GenericNetlinkMsg msg : response.getMessages()) {
            StructNlAttr attr = msg.getAttribute(NL80211_ATTR_WIPHY);
            if (attr == null) {
                Log.e(TAG, "getWiphyInfo: wiphy dump did not contain wiphy attr");
                return null;
            }

            Integer responseWiphyIndex = attr.getValueAsInteger();
            if (responseWiphyIndex == null || responseWiphyIndex != wiphyIndex) {
                Log.e(TAG, "getWiphyInfo: wiphy index for wiphy dump was "
                        + responseWiphyIndex + " but expected " + wiphyIndex);
                return null;
            }
        }

        WiphyInfo info = parseWiphyInfo(response.getMessages());
        if (info != null) {
            mCachedWiphyInfo.put(wiphyIndex, info);
            return info;
        }

        return null;
    }

    private int getIfaceIndex(@NonNull String ifaceName) {
        Objects.requireNonNull(ifaceName);

        try {
            NetworkInterface netIface = NetworkInterface.getByName(ifaceName);
            if (netIface == null) {
                Log.e(TAG, "Failed to get NetworkInterface for " + ifaceName);
                return -1;
            }
            return netIface.getIndex();
        } catch (SocketException e) {
            Log.e(TAG, "Failed to get iface index for " + ifaceName, e);
            return -1;
        }
    }

    /**
     * Gets the wiphy index for a given interface name.
     * @param ifaceName The name of the interface (e.g., "wlan0").
     * @return The wiphy index, or -1 on failure.
     */
    public int getWiphyIndex(@NonNull String ifaceName) {
        Objects.requireNonNull(ifaceName);

        if (mCachedWiphyIndexes.containsKey(ifaceName)) {
            return mCachedWiphyIndexes.get(ifaceName);
        }

        int ifIndex = getIfaceIndex(ifaceName);
        if (ifIndex == -1) {
            return -1;
        }

        StructNlAttr ifIndexAttr = new StructNlAttr(NL80211_ATTR_IFINDEX, ifIndex);

        GenericNetlinkMsg request = mNl80211Proxy.createNl80211Request(NL80211_CMD_GET_WIPHY,
                StructNlMsgHdr.NLM_F_DUMP, ifIndexAttr);

        if (request == null) {
            Log.e(TAG, "Failed to create GET_WIPHY request");
            return -1;
        }

        Nl80211Response response = mNl80211Proxy.sendMessageAndReceiveResponse(request);
        if (response == null || response.isError()) {
            Log.e(TAG, "Failed to get response for GET_WIPHY");
            return -1;
        }

        for (GenericNetlinkMsg msg : response.getMessages()) {
            if (msg.getCommand() != NL80211_CMD_NEW_WIPHY) {
                Log.e(TAG, "Wrong command in response: " + msg.getCommand());
                continue;
            }
            Integer wiphyIndex = msg.getAttributeValueAsInteger(NL80211_ATTR_WIPHY);
            if (wiphyIndex != null) {
                mCachedWiphyIndexes.put(ifaceName, wiphyIndex);
                return wiphyIndex;
            }
        }

        Log.e(TAG, "Failed to get wiphy index from reply message");
        return -1;
    }

    /**
     * Parses a GET_WIPHY response message to create a WiphyInfo object.
     * @param packets The list of NL80211_CMD_NEW_WIPHY messages.
     * @return A populated WiphyInfo object, or null on failure.
     */
    @Nullable
    public WiphyInfo parseWiphyInfo(@NonNull List<GenericNetlinkMsg> packets) {
        Objects.requireNonNull(packets);
        if (packets.isEmpty()) {
            return null;
        }

        for (GenericNetlinkMsg packet : packets) {
            if (packet.getCommand() != NL80211_CMD_NEW_WIPHY) {
                Log.e(TAG, "parseWiphyInfo: Received unexpected command: " + packet.getCommand());
                return null;
            }
        }

        BandInfo bandInfo = parseBandInfo(packets);
        if (bandInfo == null) {
            Log.e(TAG, "Failed to parse band info");
            return null;
        }

        ScanCapabilities scanCapabilities = parseScanCapabilities(packets);
        if (scanCapabilities == null) {
            Log.e(TAG, "Failed to parse scan capabilities");
            return null;
        }

        Integer featureFlags = null;
        byte[] extFeatureFlagsBytes = null;
        Short maxNumAkms = null;

        for (GenericNetlinkMsg packet : packets) {
            if (featureFlags == null) {
                featureFlags = packet.getAttributeValueAsInteger(NL80211_ATTR_FEATURE_FLAGS);
            }
            if (extFeatureFlagsBytes == null) {
                extFeatureFlagsBytes = packet.getAttributeValueAsByteArray(
                        NL80211_ATTR_EXT_FEATURES);
            }
            if (maxNumAkms == null) {
                maxNumAkms = packet.getAttributeValueAsShort(NL80211_ATTR_MAX_NUM_AKM_SUITES);
            }
        }

        if (featureFlags == null) {
            Log.e(TAG, "Failed to get NL80211_ATTR_FEATURE_FLAGS");
            return null;
        }

        if (extFeatureFlagsBytes == null) {
            extFeatureFlagsBytes = new byte[0];
        }
        WiphyFeatures wiphyFeatures = createWiphyFeatures(featureFlags, extFeatureFlagsBytes);

        if (maxNumAkms == null) {
            maxNumAkms = 1; // Default value
        }
        DriverCapabilities driverCapabilities = new DriverCapabilities(maxNumAkms);

        return new WiphyInfo(bandInfo, scanCapabilities, wiphyFeatures, driverCapabilities);
    }

    /**
     * Parses the NL80211_ATTR_WIPHY_BANDS attribute from a packet.
     * @return A populated BandInfo object, or null on failure.
     */
    @Nullable
    private BandInfo parseBandInfo(List<GenericNetlinkMsg> packets) {
        BandInfo bandInfo = new BandInfo();
        boolean foundBandAttr = false;
        for (GenericNetlinkMsg packet : packets) {
            StructNlAttr bandsAttr = packet.getAttribute(NL80211_ATTR_WIPHY_BANDS);
            if (bandsAttr == null) {
                continue;
            }

            // NL80211_ATTR_WIPHY_BANDS specifies nested attributes for each band, indexed by one
            // of nl80211_band in kernel/uapi/linux/nl80211.h.
            Map<Short, StructNlAttr> nestedBandsAttrs =
                    GenericNetlinkMsg.getInnerNestedAttributes(bandsAttr);
            if (nestedBandsAttrs == null) {
                continue;
            }
            foundBandAttr = true;

            for (Map.Entry<Short, StructNlAttr> bandAttrEntry : nestedBandsAttrs.entrySet()) {
                // Each of the nested attributes of NL80211_ATTR_WIPHY_BANDS contain further nested
                // attributes indexed by one of NL80211_BAND_ATTR_*.
                parseNestedBandAttr(bandAttrEntry.getValue(), bandInfo);
            }
        }

        if (!foundBandAttr) {
            Log.e(TAG, "parseBandInfo: failed to get mandatory NL80211_ATTR_WIPHY_BANDS");
            return null;
        }

        return bandInfo;
    }

    /**
     * Parse the contents of a nested entry of NL80211_ATTR_WIPHY_BANDS.
     */
    private void parseNestedBandAttr(StructNlAttr nestedBandAttr, BandInfo bandInfo) {
        Map<Short, StructNlAttr> innerBandAttrs =
                GenericNetlinkMsg.getInnerNestedAttributes(nestedBandAttr);
        if (innerBandAttrs == null) {
            return;
        }

        // Frequencies
        StructNlAttr freqsAttr = innerBandAttrs.get(NL80211_BAND_ATTR_FREQS);
        if (freqsAttr != null) {
            parseFrequencies(freqsAttr, bandInfo);
        }

        // PHY Standard Support
        if (innerBandAttrs.containsKey(NL80211_BAND_ATTR_HT_CAPA)) {
            bandInfo.is80211nSupported = true;
        }
        if (innerBandAttrs.containsKey(NL80211_BAND_ATTR_VHT_CAPA)) {
            bandInfo.is80211acSupported = true;
        }

        // Detailed PHY Capabilities (MCS, Channel Width, etc.)
        parseBandCapabilities(innerBandAttrs, bandInfo);
    }

    private void add5gFrequency(int frequency, Map<Short, StructNlAttr> freqProperties,
            BandInfo bandInfo) {
        StructNlAttr dfsStateAttr =
                freqProperties.get(NL80211_FREQUENCY_ATTR_DFS_STATE);
        Integer dfsState = (dfsStateAttr != null) ? dfsStateAttr.getValueAsInteger() : null;

        boolean isDfs =
                (dfsState != null
                        && (dfsState == NL80211_DFS_AVAILABLE || dfsState == NL80211_DFS_USABLE))
                || freqProperties.containsKey(NL80211_FREQUENCY_ATTR_NO_IR);

        if (isDfs) {
            bandInfo.bandDfs.add(frequency);
        } else {
            bandInfo.band5g.add(frequency);
        }
    }

    private void parseFrequencies(StructNlAttr freqsAttr, BandInfo bandInfo) {
        // NL80211_BAND_ATTR_FREQS contains a nested array of attributes, where each attribute
        // is sequentially indexed and represents a single frequency.
        Map<Short, StructNlAttr> freqs = GenericNetlinkMsg.getInnerNestedAttributes(freqsAttr);
        if (freqs == null) return;

        for (StructNlAttr freq : freqs.values()) {
            // Each of these indexed attributes contains a nested list of attributes indexed by
            // one of NL80211_FREQUENCY_ATTR_*.
            Map<Short, StructNlAttr> freqProperties =
                    GenericNetlinkMsg.getInnerNestedAttributes(freq);
            if (freqProperties == null) continue;

            StructNlAttr freqValueAttr = freqProperties.get(NL80211_FREQUENCY_ATTR_FREQ);
            if (freqValueAttr == null) continue;
            Integer frequencyValue = freqValueAttr.getValueAsInteger();
            if (frequencyValue == null) continue;

            if (freqProperties.containsKey(NL80211_FREQUENCY_ATTR_DISABLED)) {
                continue;
            }

            if (ScanResult.is24GHz(frequencyValue)) {
                bandInfo.band2g.add(frequencyValue);
            } else if (ScanResult.is5GHz(frequencyValue)) {
                add5gFrequency(frequencyValue, freqProperties, bandInfo);
            } else if (ScanResult.is6GHz(frequencyValue)) {
                bandInfo.band6g.add(frequencyValue);
            } else if (ScanResult.is60GHz(frequencyValue)) {
                bandInfo.band60g.add(frequencyValue);
            }
        }
    }

    private void parseBandCapabilities(Map<Short, StructNlAttr> bandProperties,
            BandInfo bandInfo) {
        // HT/VHT parsing
        StructNlAttr htMcsAttr = bandProperties.get(NL80211_BAND_ATTR_HT_MCS_SET);
        if (htMcsAttr != null) {
            Pair<Integer, Integer> htStreams = parseHtMcsSetAttribute(htMcsAttr.nla_value);
            bandInfo.maxTxStreams = Math.max(bandInfo.maxTxStreams, htStreams.first);
            bandInfo.maxRxStreams = Math.max(bandInfo.maxRxStreams, htStreams.second);
        }

        StructNlAttr vhtMcsAttr = bandProperties.get(NL80211_BAND_ATTR_VHT_MCS_SET);
        if (vhtMcsAttr != null) {
            Pair<Integer, Integer> vhtStreams = parseVhtMcsSetAttribute(vhtMcsAttr.nla_value);
            bandInfo.maxTxStreams = Math.max(bandInfo.maxTxStreams, vhtStreams.first);
            bandInfo.maxRxStreams = Math.max(bandInfo.maxRxStreams, vhtStreams.second);
        }

        StructNlAttr vhtCapaAttr = bandProperties.get(NL80211_BAND_ATTR_VHT_CAPA);
        if (vhtCapaAttr != null) {
            Pair<Boolean, Boolean> vhtCaps = parseVhtCapAttribute(vhtCapaAttr.getValueAsInteger());
            if (vhtCaps.first) bandInfo.is160MhzSupported = true;
            if (vhtCaps.second) bandInfo.is80p80MhzSupported = true;
        }

        // HE/EHT parsing from IFTYPE_DATA
        StructNlAttr iftypeDataRootAttr = bandProperties.get(NL80211_BAND_ATTR_IFTYPE_DATA);
        if (iftypeDataRootAttr == null) return;

        Map<Short, StructNlAttr> iftypeDataList =
                GenericNetlinkMsg.getInnerNestedAttributes(iftypeDataRootAttr);
        if (iftypeDataList == null || iftypeDataList.isEmpty()) return;

        // We only care about the first iftype data block
        StructNlAttr iftypeData = iftypeDataList.values().iterator().next();
        Map<Short, StructNlAttr> iftypeProperties =
                GenericNetlinkMsg.getInnerNestedAttributes(iftypeData);
        if (iftypeProperties == null) return;

        StructNlAttr heCapaPhyAttr = iftypeProperties.get(NL80211_BAND_IFTYPE_ATTR_HE_CAP_PHY);
        if (heCapaPhyAttr != null) {
            bandInfo.is80211axSupported = true;
            Pair<Boolean, Boolean> heCaps = parseHeCapPhyAttribute(heCapaPhyAttr.nla_value);
            if (heCaps.first) bandInfo.is160MhzSupported = true;
            if (heCaps.second) bandInfo.is80p80MhzSupported = true;
        }

        StructNlAttr heMcsAttr = iftypeProperties.get(NL80211_BAND_IFTYPE_ATTR_HE_CAP_MCS_SET);
        if (heMcsAttr != null) {
            Pair<Integer, Integer> heStreams = parseHeMcsSetAttribute(heMcsAttr.nla_value);
            bandInfo.maxTxStreams = Math.max(bandInfo.maxTxStreams, heStreams.first);
            bandInfo.maxRxStreams = Math.max(bandInfo.maxRxStreams, heStreams.second);
        }

        StructNlAttr ehtCapaPhyAttr = iftypeProperties.get(NL80211_BAND_IFTYPE_ATTR_EHT_CAP_PHY);
        if (ehtCapaPhyAttr != null) {
            bandInfo.is80211beSupported = true;
            if (parseEhtCapPhyAttribute(ehtCapaPhyAttr.nla_value)) {
                bandInfo.is320MhzSupported = true;
            }
        }
    }

    @NonNull
    private Pair<Integer, Integer> parseHtMcsSetAttribute(byte[] htMcsSet) {
        if (htMcsSet == null || htMcsSet.length < HT_MCS_SET_NUM_BYTE) {
            return new Pair<>(0, 0);
        }

        int maxRxStreams = 1;
        for (int i = 4; i >= 1; i--) {
            if (htMcsSet[i - 1] != 0) {
                maxRxStreams = i;
                break;
            }
        }

        int maxTxStreams = maxRxStreams;
        byte supportedTxMcsSet = htMcsSet[12];
        boolean txMcsSetDefined = (supportedTxMcsSet & 0x1) != 0;
        boolean txRxMcsSetNotEqual = ((supportedTxMcsSet >> 1) & 0x1) != 0;

        if (txMcsSetDefined && txRxMcsSetNotEqual) {
            int maxNssTxFieldValue = (supportedTxMcsSet >> 2) & 0x3;
            maxTxStreams = maxNssTxFieldValue + 1;
        }
        return new Pair<>(maxTxStreams, maxRxStreams);
    }

    @NonNull
    private Pair<Integer, Integer> parseVhtMcsSetAttribute(byte[] vhtMcsSet) {
        if (vhtMcsSet == null || vhtMcsSet.length < VHT_MCS_SET_NUM_BYTE) {
            return new Pair<>(0, 0);
        }
        ByteBuffer buffer = ByteBuffer.wrap(vhtMcsSet).order(ByteOrder.LITTLE_ENDIAN);

        int vhtMcsSetRx = buffer.getShort(0) & 0xFFFF;
        int maxRxStreamsVht = parseMcsMap(vhtMcsSetRx);

        int vhtMcsSetTx = buffer.getShort(4) & 0xFFFF;
        int maxTxStreamsVht = parseMcsMap(vhtMcsSetTx);

        return new Pair<>(maxTxStreamsVht, maxRxStreamsVht);
    }

    @NonNull
    private Pair<Integer, Integer> parseHeMcsSetAttribute(byte[] heMcsSet) {
        if (heMcsSet == null || heMcsSet.length < HE_MCS_SET_NUM_BYTE_MIN) {
            return new Pair<>(0, 0);
        }
        ByteBuffer buffer = ByteBuffer.wrap(heMcsSet).order(ByteOrder.LITTLE_ENDIAN);

        int heMcsMapRx = buffer.getShort(0) & 0xFFFF;
        int maxRxStreamsHe = parseMcsMap(heMcsMapRx);

        int heMcsMapTx = buffer.getShort(2) & 0xFFFF;
        int maxTxStreamsHe = parseMcsMap(heMcsMapTx);

        return new Pair<>(maxTxStreamsHe, maxRxStreamsHe);
    }

    private int parseMcsMap(int mcsMap) {
        int maxNss = 1;
        for (int i = MAX_SPACIAL_STREAMS; i >= 1; i--) {
            int streamMap = (mcsMap >> ((i - 1) * 2)) & 0b11;
            if (streamMap != 0b11 /* unsupported */) {
                maxNss = i;
                break;
            }
        }
        return maxNss;
    }

    @NonNull
    private Pair<Boolean, Boolean> parseVhtCapAttribute(Integer vhtCap) {
        if (vhtCap == null) return new Pair<>(false, false);
        boolean is160Mhz = (vhtCap & VHT_160MHZ_BIT_MASK) != 0;
        boolean is80p80Mhz = (vhtCap & VHT_80P80MHZ_BIT_MASK) != 0;
        return new Pair<>(is160Mhz, is80p80Mhz);
    }

    @NonNull
    private Pair<Boolean, Boolean> parseHeCapPhyAttribute(byte[] heCapPhy) {
        if (heCapPhy == null || heCapPhy.length < HE_CAP_PHY_NUM_BYTE) {
            return new Pair<>(false, false);
        }
        boolean is160Mhz = (heCapPhy[0] & HE_160MHZ_BIT_MASK) != 0;
        boolean is80p80Mhz = (heCapPhy[0] & HE_80P80MHZ_BIT_MASK) != 0;
        return new Pair<>(is160Mhz, is80p80Mhz);
    }

    private boolean parseEhtCapPhyAttribute(byte[] ehtCapPhy) {
        if (ehtCapPhy == null || ehtCapPhy.length < EHT_CAP_PHY_NUM_BYTE) return false;
        return (ehtCapPhy[0] & EHT_320MHZ_BIT_MASK) != 0;
    }

    @Nullable
    private ScanCapabilities parseScanCapabilities(List<GenericNetlinkMsg> packets) {
        Byte maxNumScanSsidsAttr = null;
        Byte maxNumSchedScanSsidsAttr = null;
        Byte maxMatchSetsAttr = null;
        Integer maxScanPlansAttr = null;
        Integer maxScanPlanIntervalAttr = null;
        Integer maxScanPlanIterationsAttr = null;

        for (GenericNetlinkMsg packet : packets) {
            if (maxNumScanSsidsAttr == null) {
                maxNumScanSsidsAttr = packet.getAttributeValueAsByte(
                        NL80211_ATTR_MAX_NUM_SCAN_SSIDS);
            }
            if (maxNumSchedScanSsidsAttr == null) {
                maxNumSchedScanSsidsAttr = packet.getAttributeValueAsByte(
                        NL80211_ATTR_MAX_NUM_SCHED_SCAN_SSIDS);
            }
            if (maxMatchSetsAttr == null) {
                maxMatchSetsAttr = packet.getAttributeValueAsByte(NL80211_ATTR_MAX_MATCH_SETS);
            }
            if (maxScanPlansAttr == null) {
                maxScanPlansAttr = packet.getAttributeValueAsInteger(
                        NL80211_ATTR_MAX_NUM_SCHED_SCAN_PLANS);
            }
            if (maxScanPlanIntervalAttr == null) {
                maxScanPlanIntervalAttr = packet.getAttributeValueAsInteger(
                        NL80211_ATTR_MAX_SCAN_PLAN_INTERVAL);
            }
            if (maxScanPlanIterationsAttr == null) {
                maxScanPlanIterationsAttr = packet.getAttributeValueAsInteger(
                        NL80211_ATTR_MAX_SCAN_PLAN_ITERATIONS);
            }
        }

        if (maxNumScanSsidsAttr == null
                || maxNumSchedScanSsidsAttr == null
                || maxMatchSetsAttr == null) {
            Log.e(TAG, "parseScanCapabilities: Failed to get mandatory scan capabilities");
            return null;
        }
        int maxNumScanSsids = Byte.toUnsignedInt(maxNumScanSsidsAttr);
        int maxNumSchedScanSsids = Byte.toUnsignedInt(maxNumSchedScanSsidsAttr);
        int maxMatchSets = Byte.toUnsignedInt(maxMatchSetsAttr);

        int maxNumScanPlans = 0;
        if (maxScanPlansAttr != null) {
            maxNumScanPlans = maxScanPlansAttr;
        }

        int maxScanPlanIntervalSeconds = 0;
        if (maxScanPlanIntervalAttr != null) {
            maxScanPlanIntervalSeconds = maxScanPlanIntervalAttr;
        }

        int maxScanPlanIterations = 0;
        if (maxScanPlanIterationsAttr != null) {
            maxScanPlanIterations = maxScanPlanIterationsAttr;
        }

        return new ScanCapabilities(maxNumScanSsids, maxNumSchedScanSsids, maxMatchSets,
                maxNumScanPlans, maxScanPlanIntervalSeconds, maxScanPlanIterations);
    }

    @Nullable
    private Integer getProtocolFeatures() {
        GenericNetlinkMsg request = mNl80211Proxy.createNl80211Request(
                NL80211_CMD_GET_PROTOCOL_FEATURES);
        if (request == null) return null;
        Nl80211Response response = mNl80211Proxy.sendMessageAndReceiveResponse(request);
        if (response == null || response.isError() || response.getMessage() == null) {
            Log.e(TAG, "Failed to send GET_PROTOCOL_FEATURES");
            return null;
        }

        return response.getMessage().getAttributeValueAsInteger(NL80211_ATTR_PROTOCOL_FEATURES);
    }

    private WiphyFeatures createWiphyFeatures(int featureFlags, byte[] extFeatureFlagsBytes) {
        boolean supportsRandomMacOneShotScan =
                (featureFlags & NL80211_FEATURE_SCAN_RANDOM_MAC_ADDR) != 0;
        boolean supportsRandomMacSchedScan =
                (featureFlags & NL80211_FEATURE_SCHED_SCAN_RANDOM_MAC_ADDR) != 0;

        boolean supportsLowSpanOneShotScan = isExtFeatureFlagSet(extFeatureFlagsBytes,
                NL80211_EXT_FEATURE_LOW_SPAN_SCAN);
        boolean supportsLowPowerOneShotScan = isExtFeatureFlagSet(extFeatureFlagsBytes,
                NL80211_EXT_FEATURE_LOW_POWER_SCAN);
        boolean supportsHighAccuracyOneShotScan = isExtFeatureFlagSet(extFeatureFlagsBytes,
                NL80211_EXT_FEATURE_HIGH_ACCURACY_SCAN);
        boolean supportsExtSchedScanRelativeRssi = isExtFeatureFlagSet(extFeatureFlagsBytes,
                NL80211_EXT_FEATURE_SCHED_SCAN_RELATIVE_RSSI);
        boolean supportsTxMgmtFrameMcs = false;

        return new WiphyFeatures.Builder()
                .setSupportsRandomMacOneShotScan(supportsRandomMacOneShotScan)
                .setSupportsRandomMacSchedScan(supportsRandomMacSchedScan)
                .setSupportsLowSpanOneShotScan(supportsLowSpanOneShotScan)
                .setSupportsLowPowerOneShotScan(supportsLowPowerOneShotScan)
                .setSupportsHighAccuracyOneShotScan(supportsHighAccuracyOneShotScan)
                .setSupportsExtSchedScanRelativeRssi(supportsExtSchedScanRelativeRssi)
                .setSupportsTxMgmtFrameMcs(supportsTxMgmtFrameMcs)
                .build();
    }

    private boolean isExtFeatureFlagSet(
            byte[] extFeatureFlags,
            short flagIndex) {
        if (extFeatureFlags == null) return false;
        int bytePos = flagIndex / 8;
        int bitPos = flagIndex % 8;
        if (bytePos >= extFeatureFlags.length) {
            return false;
        }
        return (extFeatureFlags[bytePos] & (1 << bitPos)) != 0;
    }

    /**
     * Gets information about all interfaces associated with a given wiphy.
     * @param wiphyIndex The index of the wiphy device, or -1 to query all wiphys.
     * @return A list of {@link InterfaceInfo} objects, or null on failure.
     */
    @Nullable
    public List<InterfaceInfo> getInterfaces(int wiphyIndex) {
        GenericNetlinkMsg request;
        if (wiphyIndex != -1) {
            StructNlAttr wiphyIndexAttr = new StructNlAttr(NL80211_ATTR_WIPHY, wiphyIndex);
            request = mNl80211Proxy.createNl80211Request(NL80211_CMD_GET_INTERFACE,
                    StructNlMsgHdr.NLM_F_DUMP,
                    wiphyIndexAttr);
        } else {
            request = mNl80211Proxy.createNl80211Request(NL80211_CMD_GET_INTERFACE,
                    StructNlMsgHdr.NLM_F_DUMP);
        }

        if (request == null) {
            Log.e(TAG, "Failed to create GET_INTERFACE request");
            return null;
        }

        Nl80211Response response = mNl80211Proxy.sendMessageAndReceiveResponse(request);
        if (response == null || response.isError()) {
            Log.e(TAG, "Failed to get response for NL80211_CMD_GET_INTERFACE");
            return null;
        }

        List<InterfaceInfo> interfaceInfos = new ArrayList<>();
        for (GenericNetlinkMsg msg : response.getMessages()) {
            if (msg.getCommand() != NetlinkConstants.NL80211_CMD_NEW_INTERFACE) {
                Log.e(TAG, "Wrong command in response to GET_INTERFACE: " + msg.getCommand());
                continue;
            }

            Integer replyWiphyIndex = msg.getAttributeValueAsInteger(NL80211_ATTR_WIPHY);
            Integer ifIndex = msg.getAttributeValueAsInteger(NL80211_ATTR_IFINDEX);
            String ifName = msg.getAttributeValueAsString(NL80211_ATTR_IFNAME);
            byte[] macAddress = msg.getAttributeValueAsByteArray(NL80211_ATTR_MAC);

            if (ifIndex == null || ifName == null || macAddress == null) {
                Log.w(TAG, "Malformed NEW_INTERFACE response: missing attributes");
                continue;
            }

            interfaceInfos.add(new InterfaceInfo(ifIndex, replyWiphyIndex, ifName, macAddress));
        }

        return interfaceInfos;
    }

    /**
     * Returns the InterfaceInfo for a given interface.
     */
    @Nullable
    public InterfaceInfo getInterfaceInfo(@NonNull String ifaceName) {
        int wiphyIndex = getWiphyIndex(ifaceName);
        if (wiphyIndex == -1) {
            Log.e(TAG, "Failed to get wiphy index for " + ifaceName);
            return null;
        }

        List<Nl80211Utils.InterfaceInfo> interfaces = getInterfaces(wiphyIndex);
        if (interfaces == null) {
            Log.e(TAG, "Failed to get interfaces for wiphy " + wiphyIndex);
            return null;
        }

        Nl80211Utils.InterfaceInfo foundInterface = null;
        for (Nl80211Utils.InterfaceInfo info : interfaces) {
            if (ifaceName.equals(info.name)) {
                foundInterface = info;
                break;
            }
        }

        return foundInterface;
    }

    /**
     * Returns a list of scan results retrieved by NL80211_CMD_GET_SCAN for the given interface.
     */
    @NonNull
    public List<NativeScanResult> getScanResults(@NonNull String ifaceName) {
        Objects.requireNonNull(ifaceName);

        int ifIndex = getIfaceIndex(ifaceName);
        if (ifIndex == -1) {
            return new ArrayList<>();
        }

        GenericNetlinkMsg request = mNl80211Proxy.createNl80211Request(
                NL80211_CMD_GET_SCAN,
                StructNlMsgHdr.NLM_F_DUMP,
                new StructNlAttr(NL80211_ATTR_IFINDEX, ifIndex));
        if (request == null) {
            Log.e(TAG, "Failed to create GET_SCAN request");
            return new ArrayList<>();
        }

        Nl80211Response response = mNl80211Proxy.sendMessageAndReceiveResponse(request);
        if (response == null || response.isError()) {
            Log.e(TAG, "Failed to send GET_SCAN");
            return new ArrayList<>();
        }

        // Each response contains one scan result.
        List<NativeScanResult> results = new ArrayList<>();
        for (GenericNetlinkMsg msg : response.getMessages()) {
            if (msg.getCommand() != NL80211_CMD_NEW_SCAN_RESULTS) {
                continue;
            }

            NativeScanResult result = parseScanResult(msg);
            if (result != null) {
                results.add(result);
            }
        }
        return results;
    }

    @Nullable
    private NativeScanResult parseScanResult(GenericNetlinkMsg response) {
        StructNlAttr bssAttr = response.getAttribute(NL80211_ATTR_BSS);
        if (bssAttr == null) {
            Log.e(TAG, "No BSS attribute in scan result");
            return null;
        }
        Map<Short, StructNlAttr> bssNestedAttrs =
                GenericNetlinkMsg.getInnerNestedAttributes(bssAttr);
        if (bssNestedAttrs == null) {
            Log.e(TAG, "Empty BSS attribute in scan result");
            return null;
        }

        // BSSID
        StructNlAttr bssidAttr = bssNestedAttrs.get(NL80211_BSS_BSSID);
        if (bssidAttr == null) {
            Log.e(TAG, "No BSSID attribute in scan result");
            return null;
        }
        ByteBuffer bssidBuf = bssidAttr.getValueAsByteBuffer();
        if (bssidBuf == null) {
            Log.e(TAG, "Failed to parse BSSID attribute");
            return null;
        }
        byte[] bssid = bssidBuf.array();

        // Frequency
        StructNlAttr freqAttr = bssNestedAttrs.get(NL80211_BSS_FREQUENCY);
        if (freqAttr == null) {
            Log.e(TAG, "No FREQUENCY attribute in scan result");
            return null;
        }
        Integer freq = freqAttr.getValueAsInteger();
        if (freq == null) {
            Log.e(TAG, "Failed to parse FREQUENCY attribute");
            return null;
        }

        // Information Elements
        StructNlAttr ieAttr = bssNestedAttrs.get(NL80211_BSS_INFORMATION_ELEMENTS);
        if (ieAttr == null) {
            Log.e(TAG, "No INFORMATION_ELEMENTS attribute in scan result");
            return null;
        }
        ByteBuffer ieBuf = ieAttr.getValueAsByteBuffer();
        if (ieBuf == null) {
            Log.e(TAG, "Failed to parse INFORMATION_ELEMENTS attribute");
            return null;
        }
        byte[] ies = ieBuf.array();

        // SSID
        byte[] ssid = getSsidFromIe(ies);
        if (ssid == null) {
            Log.d(TAG, "SSID not found in IE");
            return null;
        }

        // Timestamp
        Long timestampMicroseconds = getBssTimestampMicroseconds(bssNestedAttrs);
        if (timestampMicroseconds == null) {
            Log.e(TAG, "Failed to parse timestamp");
            return null;
        }

        // Signal MBM
        StructNlAttr signalMbmAttr = bssNestedAttrs.get(NL80211_BSS_SIGNAL_MBM);
        if (signalMbmAttr == null) {
            Log.e(TAG, "No SIGNAL_MBM attribute in scan result");
            return null;
        }
        Integer signalMbm = signalMbmAttr.getValueAsInteger();
        if (signalMbm == null) {
            Log.e(TAG, "Failed to parse SIGNAL_MBM attribute");
            return null;
        }

        // Capability
        StructNlAttr capabilityAttr = bssNestedAttrs.get(NL80211_BSS_CAPABILITY);
        if (capabilityAttr == null) {
            Log.e(TAG, "No SIGNAL_MBM attribute in scan result");
            return null;
        }
        Short capability = getByteBufferAsShort(capabilityAttr.getValueAsByteBuffer());
        if (capability == null) {
            Log.e(TAG, "Failed to parse SIGNAL_MBM attribute");
            return null;
        }

        // Association Status
        boolean associated = false;
        StructNlAttr statusAttr = bssNestedAttrs.get(NL80211_BSS_STATUS);
        if (statusAttr != null) {
            Integer status = statusAttr.getValueAsInteger();
            associated = status != null
                    && status == NL80211_BSS_STATUS_ASSOCIATED
                    || status == NL80211_BSS_STATUS_AUTHENTICATED;
        }

        // Radio Chain Infos
        List<RadioChainInfo> radioChainInfos = parseRadioChainInfos(bssNestedAttrs);

        NativeScanResult result = new NativeScanResult();
        result.ssid = ssid;
        result.bssid = bssid;
        result.infoElement = ies;
        result.frequency = freq;
        result.signalMbm = signalMbm;
        result.tsf = timestampMicroseconds;
        result.capability = (char) capability.shortValue();
        result.associated = associated;
        result.radioChainInfos = radioChainInfos;
        return result;
    }

    @Nullable
    private Long getBssTimestampMicroseconds(@NonNull Map<Short, StructNlAttr> bssNestedAttrs) {
        StructNlAttr lastSeenAttr =
                bssNestedAttrs.get(NL80211_BSS_LAST_SEEN_BOOTTIME);
        if (lastSeenAttr != null) {
            Long lastSeenBootTime = lastSeenAttr.getValueAsLong();
            if (lastSeenBootTime != null) {
                return lastSeenBootTime / 1000;
            }
        }

        // Fallback to TSF
        StructNlAttr tsfAttr = bssNestedAttrs.get(NL80211_BSS_TSF);
        if (tsfAttr == null) {
            Log.e(TAG, "Failed to get TSF from scan result");
            return null;
        }
        Long tsf = tsfAttr.getValueAsLong();
        if (tsf == null) {
            Log.e(TAG, "Failed to parse TSF attr");
            return null;
        }

        // Use the beacon TSF if it is newer
        StructNlAttr beaconTsfAttr = bssNestedAttrs.get(NL80211_BSS_BEACON_TSF);
        if (beaconTsfAttr != null) {
            Long beaconTsf = beaconTsfAttr.getValueAsLong();
            if (beaconTsf != null) {
                return Math.max(tsf, beaconTsf);
            }
        }

        return tsf;
    }

    @NonNull
    private List<RadioChainInfo> parseRadioChainInfos(@NonNull Map<Short, StructNlAttr> bssInfo) {
        StructNlAttr chainSignalAttr = bssInfo.get(NL80211_BSS_CHAIN_SIGNAL);
        if (chainSignalAttr == null) {
            return new ArrayList<>();
        }

        Map<Short, StructNlAttr> chainInfos =
                GenericNetlinkMsg.getInnerNestedAttributes(chainSignalAttr);
        if (chainInfos == null) {
            Log.e(TAG, "Failed to get nested radio chain info attributes");
            return new ArrayList<>();
        }

        List<RadioChainInfo> results = new ArrayList<>();
        for (Map.Entry<Short, StructNlAttr> entry : chainInfos.entrySet()) {
            Short chainId = entry.getKey();
            Byte level = getByteBufferAsByte(entry.getValue().getValueAsByteBuffer());
            if (level != null) {
                results.add(new RadioChainInfo(chainId, level));
            }
        }
        return results;
    }

    @Nullable
    private byte[] getSsidFromIe(byte[] ies) {
        if (ies == null) return null;
        ByteBuffer buffer = ByteBuffer.wrap(ies).order(ByteOrder.LITTLE_ENDIAN);
        while (buffer.remaining() > 1) {
            int type = buffer.get() & 0xFF;
            int length = buffer.get() & 0xFF;
            if (length > buffer.remaining()) {
                Log.e(TAG, "Invalid IE length");
                return null; // Malformed IE
            }
            if (type == ScanResult.InformationElement.EID_SSID) {
                byte[] ssid = new byte[length];
                buffer.get(ssid);
                return ssid;
            }
            buffer.position(buffer.position() + length);
        }
        return null; // SSID IE not found
    }

    /**
     * Triggers a scan on the given interface.
     * @return a WifiScanner.REASON_ code indicating the success status of the request.
     */
    public int triggerScan(int ifIndex,
            int scanFlags,
            @Nullable Set<Integer> freqs,
            @Nullable List<byte[]> hiddenNetworkSSIDs,
            @Nullable byte[] vendorIes) {
        GenericNetlinkMsg request =
                mNl80211Proxy.createNl80211Request(NL80211_CMD_TRIGGER_SCAN, NLM_F_ACK);

        // Interface index
        StructNlAttr ifIndexAttr = new StructNlAttr(NL80211_ATTR_IFINDEX, ifIndex);
        request.addAttribute(ifIndexAttr);

        // SSIDs
        if (hiddenNetworkSSIDs != null && !hiddenNetworkSSIDs.isEmpty()) {
            StructNlAttr ssidsAttr = createNestedSsidsAttribute(hiddenNetworkSSIDs);
            request.addAttribute(ssidsAttr);
        }

        // Frequencies
        // Note: An absence of NL80211_ATTR_SCAN_FREQUENCIES will scan all supported frequencies.
        if (freqs != null && !freqs.isEmpty()) {
            StructNlAttr freqsAttr = createNestedFrequenciesAttribute(new ArrayList<>(freqs));
            request.addAttribute(freqsAttr);
        }

        request.addAttribute(new StructNlAttr(NL80211_ATTR_SCAN_FLAGS, scanFlags));

        Nl80211Response response = mNl80211Proxy.sendMessageAndReceiveResponse(request);
        if (response == null) {
            Log.e(TAG, "Failed to send NL80211_CMD_TRIGGER_SCAN");
            return WifiScanner.REASON_UNSPECIFIED;
        }

        return convertStdErrNumToScanStatus(response.getErrorCode());
    }

    private int convertStdErrNumToScanStatus(int stdErr) {
        // Note: OsConstant error codes are not statically known at compile time, so we must use
        // an else-if chain instead of the more sensible switch/case.
        if (stdErr == EINVAL) {
            return WifiScanner.REASON_INVALID_ARGS;
        } else if (stdErr == EBUSY) {
            return WifiScanner.REASON_BUSY;
        } else if (stdErr == ENODEV) {
            return WifiScanner.REASON_NO_DEVICE;
        } else if (stdErr != 0) {
            return WifiScanner.REASON_UNSPECIFIED;
        }

        return WifiScanner.REASON_SUCCEEDED;
    }

    /**
     * Aborts a scan on the given interface.
     */
    public void abortScan(int ifIndex) {
        GenericNetlinkMsg request = mNl80211Proxy.createNl80211Request(
                NL80211_CMD_ABORT_SCAN, NLM_F_ACK);
        request.addAttribute(new StructNlAttr(NL80211_ATTR_IFINDEX, ifIndex));
        if (request == null) {
            Log.e(TAG, "Failed to create ABORT_SCAN request");
            return;
        }

        Nl80211Response response = mNl80211Proxy.sendMessageAndReceiveResponse(request);
        if (response == null) {
            Log.e(TAG, "Failed to send ABORT_SCAN");
            return;
        }

        if (response.isError()) {
            Log.e(TAG, "ABORT_SCAN failed with error: " + response.getErrorCode());
        }
    }

    /**
     * Starts a PNO scan.
     * @return a stderr code indicating the success status of the request.
     */
    public int startPnoScan(
            int ifIndex,
            @NonNull List<PnoScanPlan> scanPlans,
            long singleScanIntervalMs,
            int min2gRssiDbm,
            int min5gRssiDbm,
            boolean requestRandomMac,
            boolean requestLowPower,
            boolean requestSchedScanRelativeRssi,
            @NonNull List<byte[]> scanSsids,
            @NonNull List<byte[]> matchSsids,
            @NonNull List<Integer> freqs) {
        GenericNetlinkMsg request = mNl80211Proxy.createNl80211Request(
                NL80211_CMD_START_SCHED_SCAN, NLM_F_ACK);
        if (request == null) {
            Log.e(TAG, "Failed to create START_SCHED_SCAN request");
            return WifiScanner.REASON_UNSPECIFIED;
        }

        // Interface index
        request.addAttribute(new StructNlAttr(NL80211_ATTR_IFINDEX, ifIndex));

        // Scan plans & interval
        if (!scanPlans.isEmpty()) {
            request.addAttribute(createSchedScanIntervalAttribute(scanPlans));
        } else {
            request.addAttribute(
                    new StructNlAttr(NL80211_ATTR_SCHED_SCAN_INTERVAL, (int) singleScanIntervalMs));
        }

        // Scan SSIDs.
        if (scanSsids.isEmpty()) {
            // If empty, add a wildcard SSID since Nl80211 expects at least one scan SSID attr.
            scanSsids.add(new byte[0]);
        }
        request.addAttribute(createNestedSsidsAttribute(scanSsids));

        // Match SSIDs
        if (!matchSsids.isEmpty()) {
            request.addAttribute(createSchedScanMatchAttribute(matchSsids, min5gRssiDbm));
        }

        // Frequencies
        if (!freqs.isEmpty()) {
            request.addAttribute(createNestedFrequenciesAttribute(freqs));
        }

        // Set the relative threshold between 2Ghz and the default 5GHz threshold we set in the scan
        // match attribute
        if (requestSchedScanRelativeRssi) {
            byte[] rssiAdjust = new byte[8];
            ByteBuffer buf = ByteBuffer.wrap(rssiAdjust).order(ByteOrder.nativeOrder());
            buf.putInt(NetlinkConstants.NL80211_BAND_2GHZ);
            buf.put((byte) (min2gRssiDbm - min5gRssiDbm));
            request.addAttribute(new StructNlAttr(
                    NetlinkConstants.NL80211_ATTR_SCHED_SCAN_RSSI_ADJUST, rssiAdjust));
        }

        // Scan flags
        int scanFlags = 0;
        if (requestRandomMac) {
            scanFlags |= NL80211_SCAN_FLAG_RANDOM_ADDR;
        }
        if (requestLowPower) {
            scanFlags |= NL80211_SCAN_FLAG_LOW_POWER;
        }
        if (scanFlags != 0) {
            request.addAttribute(new StructNlAttr(NL80211_ATTR_SCAN_FLAGS, scanFlags));
        }

        Nl80211Response response = mNl80211Proxy.sendMessageAndReceiveResponse(request);
        if (response == null) {
            Log.e(TAG, "Failed to send NL80211_CMD_START_SCHED_SCAN");
            return WifiScanner.REASON_UNSPECIFIED;
        }
        return convertStdErrNumToScanStatus(response.getErrorCode());
    }

    private StructNlAttr createNestedSsidsAttribute(@NonNull List<byte[]> ssids) {
        StructNlAttr[] nestedSsids = new StructNlAttr[ssids.size()];
        short index = 0;
        for (byte[] ssid : ssids) {
            nestedSsids[index] = new StructNlAttr(index, ssid);
            index++;
        }
        return new StructNlAttr(NL80211_ATTR_SCAN_SSIDS, nestedSsids);
    }

    private StructNlAttr createNestedFrequenciesAttribute(@NonNull List<Integer> freqs) {
        StructNlAttr[] nestedFreqs = new StructNlAttr[freqs.size()];
        short index = 0;
        for (int freq : freqs) {
            nestedFreqs[index] = new StructNlAttr(index, freq);
            index++;
        }
        return new StructNlAttr(NL80211_ATTR_SCAN_FREQUENCIES, nestedFreqs);
    }

    private StructNlAttr createSchedScanMatchAttribute(@NonNull List<byte[]> ssids,
            int rssiThreshold) {
        StructNlAttr[] nestedMatches = new StructNlAttr[ssids.size()];
        short index = 0;
        for (byte[] ssid : ssids) {
            StructNlAttr matchSsidAttr = new StructNlAttr(
                    NL80211_SCHED_SCAN_MATCH_ATTR_SSID, ssid);
            StructNlAttr matchRssiAttr = new StructNlAttr(
                    NetlinkConstants.NL80211_SCHED_SCAN_MATCH_ATTR_RSSI, rssiThreshold);
            nestedMatches[index] = new StructNlAttr(index, matchSsidAttr, matchRssiAttr);
            index++;
        }
        return new StructNlAttr(NL80211_ATTR_SCHED_SCAN_MATCH, nestedMatches);
    }

    private StructNlAttr createSchedScanIntervalAttribute(
            @NonNull List<PnoScanPlan> scanPlans) {
        StructNlAttr[] nestedPlansAttr = new StructNlAttr[scanPlans.size()];

        // Add everything but the last plan with both interval and iterations.
        for (short index = 0; index < scanPlans.size() - 1; index++) {
            PnoScanPlan plan = scanPlans.get(index);
            StructNlAttr intervalAttr = new StructNlAttr(
                    NL80211_SCHED_SCAN_PLAN_INTERVAL, plan.intervalMs / 1000);
            StructNlAttr iterationsAttr = new StructNlAttr(
                    NL80211_SCHED_SCAN_PLAN_ITERATIONS, plan.iterations);
            nestedPlansAttr[index] = new StructNlAttr(index, intervalAttr, iterationsAttr);
        }

        if (!scanPlans.isEmpty()) {
            // The last scan plan must not specify an interval since it continues indefinitely.
            PnoScanPlan lastPlan = scanPlans.get(scanPlans.size() - 1);
            StructNlAttr lastIntervalAttr = new StructNlAttr(
                    NL80211_SCHED_SCAN_PLAN_INTERVAL,
                    lastPlan.intervalMs / 1000);
            short lastIndex = (short) (scanPlans.size() - 1);
            nestedPlansAttr[lastIndex] = new StructNlAttr(lastIndex, lastIntervalAttr);
        }

        return new StructNlAttr(NL80211_ATTR_SCHED_SCAN_PLANS, nestedPlansAttr);
    }

    /**
     * Stop a PNO scan on the given interface.
     * @return true if successful, otherwise false.
     */
    public boolean stopPnoScan(int ifIndex) {
        GenericNetlinkMsg request = mNl80211Proxy.createNl80211Request(
                NL80211_CMD_STOP_SCHED_SCAN, NLM_F_ACK);
        if (request == null) {
            Log.e(TAG, "Failed to create STOP_SCHED_SCAN request");
            return false;
        }

        // Interface index
        request.addAttribute(new StructNlAttr(NL80211_ATTR_IFINDEX, ifIndex));

        Nl80211Response response = mNl80211Proxy.sendMessageAndReceiveResponse(request);
        if (response == null) {
            Log.e(TAG, "Failed to send NL80211_CMD_STOP_SCHED_SCAN");
            return false;
        }

        if (response.isError()) {
            if (response.getErrorCode() == ENOENT) {
                Log.w(TAG, "Scheduled scan is not running!");
            } else {
                Log.e(TAG, "STOP_SCHED_SCAN failed with error: " + response.getErrorCode());
            }
            return false;
        }

        return true;
    }

    /**
     * Queries the current country code via NL80211_CMD_GET_REG.
     */
    public @Nullable String getCountryCode(int wiphyIndex) {
        GenericNetlinkMsg request = mNl80211Proxy.createNl80211Request(NL80211_CMD_GET_REG);
        request.addAttribute(new StructNlAttr(NL80211_ATTR_WIPHY, wiphyIndex));

        Nl80211Response response = mNl80211Proxy.sendMessageAndReceiveResponse(request);
        if (response == null || response.isError() || response.getMessage() == null) {
            Log.e(TAG, "Failed to send NL80211_CMD_GET_REG");
            return null;
        }

        String countryCode =
                response.getMessage().getAttributeValueAsString(NL80211_ATTR_REG_ALPHA2);
        if (countryCode == null) {
            Log.e(TAG, "Failed to get NL80211_ATTR_REG_ALPHA2");
        }

        return countryCode;
    }

    /**
     * Clears the internal wiphy information caches.
     */
    @VisibleForTesting
    public void clearWiphyInfoCaches() {
        mCachedWiphyInfo.clear();
        mCachedWiphyIndexes.clear();
    }

    @Nullable
    private Byte getByteBufferAsByte(@Nullable ByteBuffer buf) {
        if (buf == null || buf.remaining() != Byte.BYTES) {
            return null;
        }

        return buf.get();
    }

    @Nullable
    private Short getByteBufferAsShort(@Nullable ByteBuffer buf) {
        if (buf == null || buf.remaining() != Short.BYTES) {
            return null;
        }

        return buf.getShort();
    }
}
