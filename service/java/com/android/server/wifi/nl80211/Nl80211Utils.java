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
import static com.android.server.wifi.nl80211.NetlinkConstants.ANDROID_NL80211_SUBCMD_GET_PWRSTATS;
import static com.android.server.wifi.nl80211.NetlinkConstants.ANDROID_OUI;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_ATTR_BSS;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_ATTR_CIPHER_SUITES;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_ATTR_COOKIE;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_ATTR_EXT_FEATURES;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_ATTR_FEATURE_FLAGS;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_ATTR_FRAME;
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
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_ATTR_STA_INFO;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_ATTR_VENDOR_DATA;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_ATTR_WIPHY;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_ATTR_WIPHY_ANTENNA_AVAIL_RX;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_ATTR_WIPHY_ANTENNA_AVAIL_TX;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_ATTR_WIPHY_ANTENNA_RX;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_ATTR_WIPHY_ANTENNA_TX;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_ATTR_WIPHY_BANDS;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_BAND_ATTR_FREQS;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_BAND_ATTR_HT_AMPDU_DENSITY;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_BAND_ATTR_HT_AMPDU_FACTOR;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_BAND_ATTR_HT_CAPA;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_BAND_ATTR_HT_MCS_SET;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_BAND_ATTR_IFTYPE_DATA;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_BAND_ATTR_VHT_CAPA;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_BAND_ATTR_VHT_MCS_SET;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_BAND_IFTYPE_ATTR_EHT_CAP_MAC;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_BAND_IFTYPE_ATTR_EHT_CAP_MCS_SET;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_BAND_IFTYPE_ATTR_EHT_CAP_PHY;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_BAND_IFTYPE_ATTR_EHT_CAP_PPE;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_BAND_IFTYPE_ATTR_HE_6GHZ_CAPA;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_BAND_IFTYPE_ATTR_HE_CAP_MAC;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_BAND_IFTYPE_ATTR_HE_CAP_MCS_SET;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_BAND_IFTYPE_ATTR_HE_CAP_PHY;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_BAND_IFTYPE_ATTR_HE_CAP_PPE;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_BAND_IFTYPE_ATTR_IFTYPES;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_BAND_IFTYPE_ATTR_VENDOR_ELEMS;
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
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_CMD_FRAME;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_CMD_GET_INTERFACE;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_CMD_GET_PROTOCOL_FEATURES;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_CMD_GET_REG;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_CMD_GET_SCAN;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_CMD_GET_STATION;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_CMD_GET_WIPHY;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_CMD_NEW_SCAN_RESULTS;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_CMD_NEW_STATION;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_CMD_NEW_WIPHY;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_CMD_START_SCHED_SCAN;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_CMD_STOP_SCHED_SCAN;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_CMD_TRIGGER_SCAN;
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
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_RATE_INFO_BITRATE32;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_SCAN_FLAG_LOW_POWER;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_SCAN_FLAG_RANDOM_ADDR;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_SCHED_SCAN_MATCH_ATTR_SSID;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_SCHED_SCAN_PLAN_INTERVAL;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_SCHED_SCAN_PLAN_ITERATIONS;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_STA_INFO_RX_BITRATE;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_STA_INFO_SIGNAL;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_STA_INFO_TX_BITRATE;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_STA_INFO_TX_FAILED;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_STA_INFO_TX_PACKETS;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiScanner;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.Log;
import android.util.Pair;
import android.util.SparseArray;

import com.android.internal.annotations.VisibleForTesting;
import com.android.net.module.util.netlink.StructNlAttr;
import com.android.net.module.util.netlink.StructNlMsgHdr;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
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

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            WiphyFeatures that = (WiphyFeatures) o;
            return supportsRandomMacOneShotScan == that.supportsRandomMacOneShotScan
                    && supportsRandomMacSchedScan == that.supportsRandomMacSchedScan
                    && supportsLowSpanOneShotScan == that.supportsLowSpanOneShotScan
                    && supportsLowPowerOneShotScan == that.supportsLowPowerOneShotScan
                    && supportsHighAccuracyOneShotScan == that.supportsHighAccuracyOneShotScan
                    && supportsTxMgmtFrameMcs == that.supportsTxMgmtFrameMcs
                    && supportsExtSchedScanRelativeRssi == that.supportsExtSchedScanRelativeRssi;
        }

        @Override
        public int hashCode() {
            return Objects.hash(supportsRandomMacOneShotScan, supportsRandomMacSchedScan,
                    supportsLowSpanOneShotScan, supportsLowPowerOneShotScan,
                    supportsHighAccuracyOneShotScan, supportsTxMgmtFrameMcs,
                    supportsExtSchedScanRelativeRssi);
        }

        @Override
        public String toString() {
            return "WiphyFeatures {"
                    + "\n  supportsRandomMacOneShotScan: " + supportsRandomMacOneShotScan
                    + "\n  supportsRandomMacSchedScan: " + supportsRandomMacSchedScan
                    + "\n  supportsLowSpanOneShotScan: " + supportsLowSpanOneShotScan
                    + "\n  supportsLowPowerOneShotScan: " + supportsLowPowerOneShotScan
                    + "\n  supportsHighAccuracyOneShotScan: " + supportsHighAccuracyOneShotScan
                    + "\n  supportsTxMgmtFrameMcs: " + supportsTxMgmtFrameMcs
                    + "\n  supportsExtSchedScanRelativeRssi: " + supportsExtSchedScanRelativeRssi
                    + "\n}";
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

    /**
     * A container class for detailed frequency information.
     */
    public static class FrequencyInfo {
        public final int frequencyMhz;
        public final boolean disabled;
        public final boolean noIr;
        @Nullable public final Integer dfsState;

        private FrequencyInfo(Builder builder) {
            this.frequencyMhz = builder.mFrequencyMhz;
            this.disabled = builder.mDisabled;
            this.noIr = builder.mNoIr;
            this.dfsState = builder.mDfsState;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            FrequencyInfo that = (FrequencyInfo) o;
            return frequencyMhz == that.frequencyMhz
                    && disabled == that.disabled
                    && noIr == that.noIr
                    && Objects.equals(dfsState, that.dfsState);
        }

        @Override
        public int hashCode() {
            return Objects.hash(frequencyMhz, disabled, noIr, dfsState);
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append(frequencyMhz).append(" MHz");
            if (disabled) sb.append(" [DISABLED]");
            if (noIr) sb.append(" [NO_IR]");
            if (dfsState != null) {
                sb.append(" [DFS: ");
                switch (dfsState.intValue()) {
                    case (int) NetlinkConstants.NL80211_DFS_USABLE:
                        sb.append("USABLE");
                        break;
                    case (int) NetlinkConstants.NL80211_DFS_AVAILABLE:
                        sb.append("AVAILABLE");
                        break;
                    case (int) NetlinkConstants.NL80211_DFS_UNAVAILABLE:
                        sb.append("UNAVAILABLE");
                        break;
                    default:
                        sb.append(dfsState);
                }
                sb.append("]");
            }
            return sb.toString();
        }

        public static class Builder {
            private int mFrequencyMhz;
            private boolean mDisabled;
            private boolean mNoIr;
            private Integer mDfsState;

            /**
             * Sets the frequency in MHz.
             * See {@link NetlinkConstants#NL80211_FREQUENCY_ATTR_FREQ}
             */
            public Builder setFrequencyMhz(int val) {
                mFrequencyMhz = val;
                return this;
            }

            /**
             * Sets whether the frequency is disabled.
             * See {@link NetlinkConstants#NL80211_FREQUENCY_ATTR_DISABLED}
             */
            public Builder setDisabled(boolean val) {
                mDisabled = val;
                return this;
            }

            /**
             * Sets whether the frequency is No-IR (No Initial Radiation).
             * See {@link NetlinkConstants#NL80211_FREQUENCY_ATTR_NO_IR}
             */
            public Builder setNoIr(boolean val) {
                mNoIr = val;
                return this;
            }

            /**
             * Sets the DFS state.
             * See {@link NetlinkConstants#NL80211_FREQUENCY_ATTR_DFS_STATE}
             */
            public Builder setDfsState(@Nullable Integer val) {
                mDfsState = val;
                return this;
            }

            /** Builds a new FrequencyInfo object. */
            public FrequencyInfo build() {
                return new FrequencyInfo(this);
            }
        }
    }

    /**
     * A container class for per-band capabilities.
     */
    public static class BandCapabilities {
        public final int bandIndex;
        public final boolean isHtSupported;
        public final boolean isVhtSupported;
        public final boolean isHeSupported;
        public final boolean isEhtSupported;

        @Nullable public final byte[] iftypes;
        @Nullable public final byte[] htCap;
        @Nullable public final byte[] htMcsSet;
        @Nullable public final Byte htAmpduFactor;
        @Nullable public final Byte htAmpduDensity;
        @Nullable public final Integer vhtCap;
        @Nullable public final byte[] vhtMcsSet;
        @Nullable public final byte[] heCapMac;
        @Nullable public final byte[] heCapPhy;
        @Nullable public final byte[] heMcsSet;
        @Nullable public final byte[] heCapPpe;
        @Nullable public final byte[] he6ghzCapa;
        @Nullable public final byte[] vendorElems;
        @Nullable public final byte[] ehtCapMac;
        @Nullable public final byte[] ehtCapPhy;
        @Nullable public final byte[] ehtMcsSet;
        @Nullable public final byte[] ehtCapPpe;

        public final int htMaxTxStreams;
        public final int htMaxRxStreams;
        public final int vhtMaxTxStreams;
        public final int vhtMaxRxStreams;
        public final int heMaxTxStreams;
        public final int heMaxRxStreams;
        public final int ehtMaxTxStreams;
        public final int ehtMaxRxStreams;

        @NonNull public final List<FrequencyInfo> frequencies;

        private BandCapabilities(Builder builder) {
            this.bandIndex = builder.mBandIndex;
            this.isHtSupported = builder.mIsHtSupported;
            this.isVhtSupported = builder.mIsVhtSupported;
            this.isHeSupported = builder.mIsHeSupported;
            this.isEhtSupported = builder.mIsEhtSupported;
            this.iftypes = builder.mIftypes;
            this.htCap = builder.mHtCap;
            this.htMcsSet = builder.mHtMcsSet;
            this.htAmpduFactor = builder.mHtAmpduFactor;
            this.htAmpduDensity = builder.mHtAmpduDensity;
            this.vhtCap = builder.mVhtCap;
            this.vhtMcsSet = builder.mVhtMcsSet;
            this.heCapMac = builder.mHeCapMac;
            this.heCapPhy = builder.mHeCapPhy;
            this.heMcsSet = builder.mHeMcsSet;
            this.heCapPpe = builder.mHeCapPpe;
            this.he6ghzCapa = builder.mHe6ghzCapa;
            this.vendorElems = builder.mVendorElems;
            this.ehtCapMac = builder.mEhtCapMac;
            this.ehtCapPhy = builder.mEhtCapPhy;
            this.ehtMcsSet = builder.mEhtMcsSet;
            this.ehtCapPpe = builder.mEhtCapPpe;
            this.htMaxTxStreams = builder.mHtMaxTxStreams;
            this.htMaxRxStreams = builder.mHtMaxRxStreams;
            this.vhtMaxTxStreams = builder.mVhtMaxTxStreams;
            this.vhtMaxRxStreams = builder.mVhtMaxRxStreams;
            this.heMaxTxStreams = builder.mHeMaxTxStreams;
            this.heMaxRxStreams = builder.mHeMaxRxStreams;
            this.ehtMaxTxStreams = builder.mEhtMaxTxStreams;
            this.ehtMaxRxStreams = builder.mEhtMaxRxStreams;
            this.frequencies = builder.mFrequencies;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            BandCapabilities that = (BandCapabilities) o;
            return bandIndex == that.bandIndex
                    && isHtSupported == that.isHtSupported
                    && isVhtSupported == that.isVhtSupported
                    && isHeSupported == that.isHeSupported
                    && isEhtSupported == that.isEhtSupported
                    && htMaxTxStreams == that.htMaxTxStreams
                    && htMaxRxStreams == that.htMaxRxStreams
                    && vhtMaxTxStreams == that.vhtMaxTxStreams
                    && vhtMaxRxStreams == that.vhtMaxRxStreams
                    && heMaxTxStreams == that.heMaxTxStreams
                    && heMaxRxStreams == that.heMaxRxStreams
                    && ehtMaxTxStreams == that.ehtMaxTxStreams
                    && ehtMaxRxStreams == that.ehtMaxRxStreams
                    && Arrays.equals(iftypes, that.iftypes)
                    && Arrays.equals(htCap, that.htCap)
                    && Arrays.equals(htMcsSet, that.htMcsSet)
                    && Objects.equals(htAmpduFactor, that.htAmpduFactor)
                    && Objects.equals(htAmpduDensity, that.htAmpduDensity)
                    && Objects.equals(vhtCap, that.vhtCap)
                    && Arrays.equals(vhtMcsSet, that.vhtMcsSet)
                    && Arrays.equals(heCapMac, that.heCapMac)
                    && Arrays.equals(heCapPhy, that.heCapPhy)
                    && Arrays.equals(heMcsSet, that.heMcsSet)
                    && Arrays.equals(heCapPpe, that.heCapPpe)
                    && Arrays.equals(he6ghzCapa, that.he6ghzCapa)
                    && Arrays.equals(vendorElems, that.vendorElems)
                    && Arrays.equals(ehtCapMac, that.ehtCapMac)
                    && Arrays.equals(ehtCapPhy, that.ehtCapPhy)
                    && Arrays.equals(ehtMcsSet, that.ehtMcsSet)
                    && Arrays.equals(ehtCapPpe, that.ehtCapPpe)
                    && Objects.equals(frequencies, that.frequencies);
        }

        @Override
        public int hashCode() {
            int result = Objects.hash(bandIndex, isHtSupported, isVhtSupported, isHeSupported,
                    isEhtSupported, htAmpduFactor, htAmpduDensity, vhtCap, htMaxTxStreams,
                    htMaxRxStreams, vhtMaxTxStreams, vhtMaxRxStreams, heMaxTxStreams,
                    heMaxRxStreams, ehtMaxTxStreams, ehtMaxRxStreams, frequencies);
            result = 31 * result + Arrays.hashCode(iftypes);
            result = 31 * result + Arrays.hashCode(htCap);
            result = 31 * result + Arrays.hashCode(htMcsSet);
            result = 31 * result + Arrays.hashCode(vhtMcsSet);
            result = 31 * result + Arrays.hashCode(heCapMac);
            result = 31 * result + Arrays.hashCode(heCapPhy);
            result = 31 * result + Arrays.hashCode(heMcsSet);
            result = 31 * result + Arrays.hashCode(heCapPpe);
            result = 31 * result + Arrays.hashCode(he6ghzCapa);
            result = 31 * result + Arrays.hashCode(vendorElems);
            result = 31 * result + Arrays.hashCode(ehtCapMac);
            result = 31 * result + Arrays.hashCode(ehtCapPhy);
            result = 31 * result + Arrays.hashCode(ehtMcsSet);
            result = 31 * result + Arrays.hashCode(ehtCapPpe);
            return result;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("Band ").append(getBandName(bandIndex)).append(" {")
                    .append("\n    Iftypes: ").append(toHexString(iftypes))
                    .append("\n    HT: ").append(isHtSupported);
            if (isHtSupported) {
                sb.append(" [Cap: ").append(toHexString(htCap))
                        .append(", MCSSet: ").append(toHexString(htMcsSet))
                        .append(", AMPDUFactor: ").append(htAmpduFactor)
                        .append(", AMPDUDensity: ").append(htAmpduDensity)
                        .append(", TxNSS: ").append(htMaxTxStreams)
                        .append(", RxNSS: ").append(htMaxRxStreams).append("]");
            }
            sb.append("\n    VHT: ").append(isVhtSupported);
            if (isVhtSupported) {
                sb.append(" [Cap: 0x").append(Integer.toHexString(vhtCap))
                        .append(", MCSSet: ").append(toHexString(vhtMcsSet))
                        .append(", TxNSS: ").append(vhtMaxTxStreams)
                        .append(", RxNSS: ").append(vhtMaxRxStreams).append("]");
            }
            sb.append("\n    HE: ").append(isHeSupported);
            if (isHeSupported) {
                sb.append(" [CapMAC: ").append(toHexString(heCapMac))
                        .append(", CapPHY: ").append(toHexString(heCapPhy))
                        .append(", MCSSet: ").append(toHexString(heMcsSet))
                        .append(", CapPPE: ").append(toHexString(heCapPpe))
                        .append(", 6GHzCapa: ").append(toHexString(he6ghzCapa))
                        .append(", TxNSS: ").append(heMaxTxStreams)
                        .append(", RxNSS: ").append(heMaxRxStreams).append("]");
            }
            sb.append("\n    EHT: ").append(isEhtSupported);
            if (isEhtSupported) {
                sb.append(" [CapMAC: ").append(toHexString(ehtCapMac))
                        .append(", CapPHY: ").append(toHexString(ehtCapPhy))
                        .append(", MCSSet: ").append(toHexString(ehtMcsSet))
                        .append(", CapPPE: ").append(toHexString(ehtCapPpe))
                        .append(", TxNSS: ").append(ehtMaxTxStreams)
                        .append(", RxNSS: ").append(ehtMaxRxStreams)
                        .append("]");
            }
            sb.append("\n    VendorElems: ").append(toHexString(vendorElems));
            sb.append("\n    Frequencies: [");
            for (FrequencyInfo freq : frequencies) {
                sb.append("\n      ").append(freq);
            }
            sb.append("\n    ]\n  }");
            return sb.toString();
        }

        private String getBandName(int index) {
            switch (index) {
                case NetlinkConstants.NL80211_BAND_2GHZ:
                    return "2.4GHz";
                case NetlinkConstants.NL80211_BAND_5GHZ:
                    return "5GHz";
                case NetlinkConstants.NL80211_BAND_6GHZ:
                    return "6GHz";
                case NetlinkConstants.NL80211_BAND_60GHZ:
                    return "60GHz";
                default:
                    return String.valueOf(index);
            }
        }

        public static class Builder {
            private int mBandIndex;
            private boolean mIsHtSupported;
            private boolean mIsVhtSupported;
            private boolean mIsHeSupported;
            private boolean mIsEhtSupported;
            private byte[] mIftypes;
            private byte[] mHtCap;
            private byte[] mHtMcsSet;
            private Byte mHtAmpduFactor;
            private Byte mHtAmpduDensity;
            private Integer mVhtCap;
            private byte[] mVhtMcsSet;
            private byte[] mHeCapMac;
            private byte[] mHeCapPhy;
            private byte[] mHeMcsSet;
            private byte[] mHeCapPpe;
            private byte[] mHe6ghzCapa;
            private byte[] mVendorElems;
            private byte[] mEhtCapMac;
            private byte[] mEhtCapPhy;
            private byte[] mEhtMcsSet;
            private byte[] mEhtCapPpe;
            private int mHtMaxTxStreams;
            private int mHtMaxRxStreams;
            private int mVhtMaxTxStreams;
            private int mVhtMaxRxStreams;
            private int mHeMaxTxStreams;
            private int mHeMaxRxStreams;
            private int mEhtMaxTxStreams;
            private int mEhtMaxRxStreams;
            @NonNull private final List<FrequencyInfo> mFrequencies = new ArrayList<>();

            /**
             * Sets the band index.
             * See {@link NetlinkConstants#NL80211_ATTR_WIPHY_BANDS}
             */
            public Builder setBandIndex(int val) {
                mBandIndex = val;
                return this;
            }

            /**
             * Sets the interface types.
             * See {@link NetlinkConstants#NL80211_BAND_IFTYPE_ATTR_IFTYPES}
             */
            public Builder setIftypes(@Nullable byte[] val) {
                mIftypes = val;
                return this;
            }

            /**
             * Sets whether HT is supported.
             * See {@link NetlinkConstants#NL80211_BAND_ATTR_HT_CAPA}
             */
            public Builder setIsHtSupported(boolean val) {
                mIsHtSupported = val;
                return this;
            }

            /**
             * Sets whether VHT is supported.
             * See {@link NetlinkConstants#NL80211_BAND_ATTR_VHT_CAPA}
             */
            public Builder setIsVhtSupported(boolean val) {
                mIsVhtSupported = val;
                return this;
            }

            /**
             * Sets whether HE is supported.
             * See {@link NetlinkConstants#NL80211_BAND_ATTR_IFTYPE_DATA}
             */
            public Builder setIsHeSupported(boolean val) {
                mIsHeSupported = val;
                return this;
            }

            /**
             * Sets whether EHT is supported.
             * See {@link NetlinkConstants#NL80211_BAND_ATTR_IFTYPE_DATA}
             */
            public Builder setIsEhtSupported(boolean val) {
                mIsEhtSupported = val;
                return this;
            }

            /**
             * Sets HT capability.
             * See {@link NetlinkConstants#NL80211_BAND_ATTR_HT_CAPA}
             */
            public Builder setHtCap(@Nullable byte[] val) {
                mHtCap = val;
                return this;
            }

            /**
             * Sets HT MCS set.
             * See {@link NetlinkConstants#NL80211_BAND_ATTR_HT_MCS_SET}
             */
            public Builder setHtMcsSet(@Nullable byte[] val) {
                mHtMcsSet = val;
                return this;
            }

            /**
             * Sets HT A-MPDU factor.
             * See {@link NetlinkConstants#NL80211_BAND_ATTR_HT_AMPDU_FACTOR}
             */
            public Builder setHtAmpduFactor(@Nullable Byte val) {
                mHtAmpduFactor = val;
                return this;
            }

            /**
             * Sets HT A-MPDU density.
             * See {@link NetlinkConstants#NL80211_BAND_ATTR_HT_AMPDU_DENSITY}
             */
            public Builder setHtAmpduDensity(@Nullable Byte val) {
                mHtAmpduDensity = val;
                return this;
            }

            /**
             * Sets VHT capability.
             * See {@link NetlinkConstants#NL80211_BAND_ATTR_VHT_CAPA}
             */
            public Builder setVhtCap(@Nullable Integer val) {
                mVhtCap = val;
                return this;
            }

            /**
             * Sets VHT MCS set.
             * See {@link NetlinkConstants#NL80211_BAND_ATTR_VHT_MCS_SET}
             */
            public Builder setVhtMcsSet(@Nullable byte[] val) {
                mVhtMcsSet = val;
                return this;
            }

            /**
             * Sets HE MAC capability.
             * See {@link NetlinkConstants#NL80211_BAND_IFTYPE_ATTR_HE_CAP_MAC}
             */
            public Builder setHeCapMac(@Nullable byte[] val) {
                mHeCapMac = val;
                return this;
            }

            /**
             * Sets HE PHY capability.
             * See {@link NetlinkConstants#NL80211_BAND_IFTYPE_ATTR_HE_CAP_PHY}
             */
            public Builder setHeCapPhy(@Nullable byte[] val) {
                mHeCapPhy = val;
                return this;
            }

            /**
             * Sets HE MCS set.
             * See {@link NetlinkConstants#NL80211_BAND_IFTYPE_ATTR_HE_CAP_MCS_SET}
             */
            public Builder setHeMcsSet(@Nullable byte[] val) {
                mHeMcsSet = val;
                return this;
            }

            /**
             * Sets HE PPE capability.
             * See {@link NetlinkConstants#NL80211_BAND_IFTYPE_ATTR_HE_CAP_PPE}
             */
            public Builder setHeCapPpe(@Nullable byte[] val) {
                mHeCapPpe = val;
                return this;
            }

            /**
             * Sets HE 6GHz capability.
             * See {@link NetlinkConstants#NL80211_BAND_IFTYPE_ATTR_HE_6GHZ_CAPA}
             */
            public Builder setHe6ghzCapa(@Nullable byte[] val) {
                mHe6ghzCapa = val;
                return this;
            }

            /**
             * Sets Vendor Elements.
             * See {@link NetlinkConstants#NL80211_BAND_IFTYPE_ATTR_VENDOR_ELEMS}
             */
            public Builder setVendorElems(@Nullable byte[] val) {
                mVendorElems = val;
                return this;
            }

            /**
             * Sets EHT MAC capability.
             * See {@link NetlinkConstants#NL80211_BAND_IFTYPE_ATTR_EHT_CAP_MAC}
             */
            public Builder setEhtCapMac(@Nullable byte[] val) {
                mEhtCapMac = val;
                return this;
            }

            /**
             * Sets EHT PHY capability.
             * See {@link NetlinkConstants#NL80211_BAND_IFTYPE_ATTR_EHT_CAP_PHY}
             */
            public Builder setEhtCapPhy(@Nullable byte[] val) {
                mEhtCapPhy = val;
                return this;
            }

            /**
             * Sets EHT MCS set.
             * See {@link NetlinkConstants#NL80211_BAND_IFTYPE_ATTR_EHT_CAP_MCS_SET}
             */
            public Builder setEhtMcsSet(@Nullable byte[] val) {
                mEhtMcsSet = val;
                return this;
            }

            /**
             * Sets EHT PPE capability.
             * See {@link NetlinkConstants#NL80211_BAND_IFTYPE_ATTR_EHT_CAP_PPE}
             */
            public Builder setEhtCapPpe(@Nullable byte[] val) {
                mEhtCapPpe = val;
                return this;
            }

            /**
             * Sets HT max TX streams.
             * See {@link NetlinkConstants#NL80211_BAND_ATTR_HT_MCS_SET}
             */
            public Builder setHtMaxTxStreams(int val) {
                mHtMaxTxStreams = val;
                return this;
            }

            /**
             * Sets HT max RX streams.
             * See {@link NetlinkConstants#NL80211_BAND_ATTR_HT_MCS_SET}
             */
            public Builder setHtMaxRxStreams(int val) {
                mHtMaxRxStreams = val;
                return this;
            }

            /**
             * Sets VHT max TX streams.
             * See {@link NetlinkConstants#NL80211_BAND_ATTR_VHT_MCS_SET}
             */
            public Builder setVhtMaxTxStreams(int val) {
                mVhtMaxTxStreams = val;
                return this;
            }

            /**
             * Sets VHT max RX streams.
             * See {@link NetlinkConstants#NL80211_BAND_ATTR_VHT_MCS_SET}
             */
            public Builder setVhtMaxRxStreams(int val) {
                mVhtMaxRxStreams = val;
                return this;
            }

            /**
             * Sets HE max TX streams.
             * See {@link NetlinkConstants#NL80211_BAND_IFTYPE_ATTR_HE_CAP_MCS_SET}
             */
            public Builder setHeMaxTxStreams(int val) {
                mHeMaxTxStreams = val;
                return this;
            }

            /**
             * Sets HE max RX streams.
             * See {@link NetlinkConstants#NL80211_BAND_IFTYPE_ATTR_HE_CAP_MCS_SET}
             */
            public Builder setHeMaxRxStreams(int val) {
                mHeMaxRxStreams = val;
                return this;
            }

            /**
             * Sets EHT max TX streams.
             * See {@link NetlinkConstants#NL80211_BAND_IFTYPE_ATTR_EHT_CAP_MCS_SET}
             */
            public Builder setEhtMaxTxStreams(int val) {
                mEhtMaxTxStreams = val;
                return this;
            }

            /**
             * Sets EHT max RX streams.
             * See {@link NetlinkConstants#NL80211_BAND_IFTYPE_ATTR_EHT_CAP_MCS_SET}
             */
            public Builder setEhtMaxRxStreams(int val) {
                mEhtMaxRxStreams = val;
                return this;
            }

            /**
             * Adds a frequency to this band.
             * See {@link NetlinkConstants#NL80211_BAND_ATTR_FREQS}
             */
            public Builder addFrequency(@NonNull FrequencyInfo val) {
                if (val == null) {
                    Log.e(TAG, "BandCapabilities.Builder.addFrequency: Ignoring null val");
                    return this;
                }
                mFrequencies.add(val);
                return this;
            }

            /** Builds a new BandCapabilities object. */
            public BandCapabilities build() {
                return new BandCapabilities(this);
            }
        }
    }

    public static class BandInfo {
        @NonNull public final List<Integer> band2g;
        @NonNull public final List<Integer> band5g;
        @NonNull public final List<Integer> band6g;
        @NonNull public final List<Integer> band60g;
        @NonNull public final List<Integer> bandDfs;
        public final boolean is80211nSupported;
        public final boolean is80211acSupported;
        public final boolean is80211axSupported;
        public final boolean is80211beSupported;
        public final boolean is160MhzSupported;
        public final boolean is80p80MhzSupported;
        public final boolean is320MhzSupported;
        public final int maxTxStreams;
        public final int maxRxStreams;
        @NonNull public final Map<Integer, BandCapabilities> perBandCapabilities;

        private BandInfo(Builder builder) {
            this.perBandCapabilities = builder.mPerBandCapabilities;

            List<Integer> band2g = new ArrayList<>();
            List<Integer> band5g = new ArrayList<>();
            List<Integer> bandDfs = new ArrayList<>();
            List<Integer> band6g = new ArrayList<>();
            List<Integer> band60g = new ArrayList<>();
            boolean is80211nSupported = false;
            boolean is80211acSupported = false;
            boolean is80211axSupported = false;
            boolean is80211beSupported = false;
            boolean is160MhzSupported = false;
            boolean is80p80MhzSupported = false;
            boolean is320MhzSupported = false;
            int maxTxStreams = 0;
            int maxRxStreams = 0;

            for (BandCapabilities caps : perBandCapabilities.values()) {
                if (caps.isHtSupported) is80211nSupported = true;
                if (caps.isVhtSupported) is80211acSupported = true;
                if (caps.isHeSupported) is80211axSupported = true;
                if (caps.isEhtSupported) is80211beSupported = true;

                maxTxStreams = Math.max(maxTxStreams, caps.htMaxTxStreams);
                maxTxStreams = Math.max(maxTxStreams, caps.vhtMaxTxStreams);
                maxTxStreams = Math.max(maxTxStreams, caps.heMaxTxStreams);
                maxTxStreams = Math.max(maxTxStreams, caps.ehtMaxTxStreams);

                maxRxStreams = Math.max(maxRxStreams, caps.htMaxRxStreams);
                maxRxStreams = Math.max(maxRxStreams, caps.vhtMaxRxStreams);
                maxRxStreams = Math.max(maxRxStreams, caps.heMaxRxStreams);
                maxRxStreams = Math.max(maxRxStreams, caps.ehtMaxRxStreams);

                if (caps.vhtCap != null) {
                    Pair<Boolean, Boolean> vht = parseVhtCapAttribute(caps.vhtCap);
                    if (vht.first) is160MhzSupported = true;
                    if (vht.second) is80p80MhzSupported = true;
                }
                if (caps.heCapPhy != null) {
                    Pair<Boolean, Boolean> he = parseHeCapPhyAttribute(caps.heCapPhy);
                    if (he.first) is160MhzSupported = true;
                    if (he.second) is80p80MhzSupported = true;
                }
                if (caps.ehtCapPhy != null) {
                    if (parseEhtCapPhyAttribute(caps.ehtCapPhy)) is320MhzSupported = true;
                }

                for (FrequencyInfo freq : caps.frequencies) {
                    if (freq.disabled) continue;
                    int f = freq.frequencyMhz;
                    if (ScanResult.is24GHz(f)) {
                        band2g.add(f);
                    } else if (ScanResult.is5GHz(f)) {
                        if (isDfs(freq)) {
                            bandDfs.add(f);
                        } else {
                            band5g.add(f);
                        }
                    } else if (ScanResult.is6GHz(f)) {
                        band6g.add(f);
                    } else if (ScanResult.is60GHz(f)) {
                        band60g.add(f);
                    }
                }
            }

            this.band2g = band2g;
            this.band5g = band5g;
            this.band6g = band6g;
            this.band60g = band60g;
            this.bandDfs = bandDfs;
            this.is80211nSupported = is80211nSupported;
            this.is80211acSupported = is80211acSupported;
            this.is80211axSupported = is80211axSupported;
            this.is80211beSupported = is80211beSupported;
            this.is160MhzSupported = is160MhzSupported;
            this.is80p80MhzSupported = is80p80MhzSupported;
            this.is320MhzSupported = is320MhzSupported;
            this.maxTxStreams = maxTxStreams;
            this.maxRxStreams = maxRxStreams;
        }

        private static boolean isDfs(@NonNull FrequencyInfo freq) {
            return (freq.dfsState != null
                    && (freq.dfsState == (int) NetlinkConstants.NL80211_DFS_AVAILABLE
                        || freq.dfsState == (int) NetlinkConstants.NL80211_DFS_USABLE))
                    || freq.noIr;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            BandInfo bandInfo = (BandInfo) o;
            return Objects.equals(perBandCapabilities, bandInfo.perBandCapabilities);
        }

        @Override
        public int hashCode() {
            return Objects.hash(perBandCapabilities);
        }

        @Override
        public String toString() {
            return "BandInfo {"
                    + "\n  band2g: " + band2g
                    + "\n  band5g: " + band5g
                    + "\n  band6g: " + band6g
                    + "\n  band60g: " + band60g
                    + "\n  bandDfs: " + bandDfs
                    + "\n  is80211nSupported: " + is80211nSupported
                    + "\n  is80211acSupported: " + is80211acSupported
                    + "\n  is80211axSupported: " + is80211axSupported
                    + "\n  is80211beSupported: " + is80211beSupported
                    + "\n  is160MhzSupported: " + is160MhzSupported
                    + "\n  is80p80MhzSupported: " + is80p80MhzSupported
                    + "\n  is320MhzSupported: " + is320MhzSupported
                    + "\n  maxTxStreams: " + maxTxStreams
                    + "\n  maxRxStreams: " + maxRxStreams
                    + "\n  perBandCapabilities: " + perBandCapabilities
                    + "\n}";
        }

        public static class Builder {
            @NonNull private final Map<Integer, BandCapabilities> mPerBandCapabilities =
                    new ArrayMap<>();

            /**
             * Adds per-band capabilities.
             * See {@link NetlinkConstants#NL80211_ATTR_WIPHY_BANDS}
             * @param bandIndex One of the nl80211_band values (e.g.,
             *                  {@link NetlinkConstants#NL80211_BAND_2GHZ}).
             * @param val The capabilities for this band.
             * @return This builder.
             */
            public Builder addBandCapabilities(int bandIndex, @NonNull BandCapabilities val) {
                if (val == null) {
                    Log.e(TAG, "BandInfo.Builder.addBandCapabilities: Ignoring null val");
                    return this;
                }
                mPerBandCapabilities.put(bandIndex, val);
                return this;
            }

            /** Builds a new BandInfo object. */
            public BandInfo build() {
                return new BandInfo(this);
            }
        }
    }

    public static class ScanCapabilities {
        public final int maxNumScanSsids;
        public final int maxNumSchedScanSsids;
        public final int maxMatchSets;
        public final int maxNumScanPlans;
        public final int maxScanPlanIntervalSeconds;
        public final int maxScanPlanIterations;

        private ScanCapabilities(Builder builder) {
            this.maxNumScanSsids = builder.mMaxNumScanSsids;
            this.maxNumSchedScanSsids = builder.mMaxNumSchedScanSsids;
            this.maxMatchSets = builder.mMaxMatchSets;
            this.maxNumScanPlans = builder.mMaxNumScanPlans;
            this.maxScanPlanIntervalSeconds = builder.mMaxScanPlanIntervalSeconds;
            this.maxScanPlanIterations = builder.mMaxScanPlanIterations;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ScanCapabilities that = (ScanCapabilities) o;
            return maxNumScanSsids == that.maxNumScanSsids
                    && maxNumSchedScanSsids == that.maxNumSchedScanSsids
                    && maxMatchSets == that.maxMatchSets
                    && maxNumScanPlans == that.maxNumScanPlans
                    && maxScanPlanIntervalSeconds == that.maxScanPlanIntervalSeconds
                    && maxScanPlanIterations == that.maxScanPlanIterations;
        }

        @Override
        public int hashCode() {
            return Objects.hash(maxNumScanSsids, maxNumSchedScanSsids, maxMatchSets,
                    maxNumScanPlans, maxScanPlanIntervalSeconds, maxScanPlanIterations);
        }

        @Override
        public String toString() {
            return "ScanCapabilities {"
                    + "\n  maxNumScanSsids: " + maxNumScanSsids
                    + "\n  maxNumSchedScanSsids: " + maxNumSchedScanSsids
                    + "\n  maxMatchSets: " + maxMatchSets
                    + "\n  maxNumScanPlans: " + maxNumScanPlans
                    + "\n  maxScanPlanIntervalSeconds: " + maxScanPlanIntervalSeconds
                    + "\n  maxScanPlanIterations: " + maxScanPlanIterations
                    + "\n}";
        }

        public static class Builder {
            private int mMaxNumScanSsids;
            private int mMaxNumSchedScanSsids;
            private int mMaxMatchSets;
            private int mMaxNumScanPlans;
            private int mMaxScanPlanIntervalSeconds;
            private int mMaxScanPlanIterations;

            /** Sets the maximum number of scan SSIDs. */
            public Builder setMaxNumScanSsids(int val) {
                mMaxNumScanSsids = val;
                return this;
            }

            /** Sets the maximum number of scheduled scan SSIDs. */
            public Builder setMaxNumSchedScanSsids(int val) {
                mMaxNumSchedScanSsids = val;
                return this;
            }

            /** Sets the maximum number of match sets. */
            public Builder setMaxMatchSets(int val) {
                mMaxMatchSets = val;
                return this;
            }

            /** Sets the maximum number of scan plans. */
            public Builder setMaxNumScanPlans(int val) {
                mMaxNumScanPlans = val;
                return this;
            }

            /** Sets the maximum scan plan interval in seconds. */
            public Builder setMaxScanPlanIntervalSeconds(int val) {
                mMaxScanPlanIntervalSeconds = val;
                return this;
            }

            /** Sets the maximum scan plan iterations. */
            public Builder setMaxScanPlanIterations(int val) {
                mMaxScanPlanIterations = val;
                return this;
            }

            /** Builds a new ScanCapabilities object. */
            public ScanCapabilities build() {
                return new ScanCapabilities(this);
            }
        }
    }

    public static class DriverCapabilities {
        public final int maxNumAkmSuites;
        @NonNull public final Set<Integer> supportedCipherSuites;

        private DriverCapabilities(Builder builder) {
            this.maxNumAkmSuites = builder.mMaxNumAkmSuites;
            this.supportedCipherSuites = builder.mSupportedCipherSuites;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            DriverCapabilities that = (DriverCapabilities) o;
            return maxNumAkmSuites == that.maxNumAkmSuites
                    && Objects.equals(supportedCipherSuites, that.supportedCipherSuites);
        }

        @Override
        public int hashCode() {
            return Objects.hash(maxNumAkmSuites, supportedCipherSuites);
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("DriverCapabilities {")
                    .append("\n  maxNumAkmSuites: ").append(maxNumAkmSuites)
                    .append("\n  supportedCipherSuites: [");
            boolean first = true;
            for (Integer c : supportedCipherSuites) {
                if (!first) {
                    sb.append(", ");
                }
                sb.append("0x").append(Integer.toHexString(c));
                first = false;
            }
            sb.append("]\n}");
            return sb.toString();
        }

        public static class Builder {
            private int mMaxNumAkmSuites;
            @NonNull private Set<Integer> mSupportedCipherSuites = Collections.emptySet();

            /**
             * Sets the maximum number of AKM suites.
             * See {@link NetlinkConstants#NL80211_ATTR_MAX_NUM_AKM_SUITES}
             */
            public Builder setMaxNumAkmSuites(int val) {
                mMaxNumAkmSuites = val;
                return this;
            }

            /**
             * Sets the supported cipher suites.
             * See {@link NetlinkConstants#NL80211_ATTR_CIPHER_SUITES}
             */
            public Builder setSupportedCipherSuites(@NonNull Set<Integer> val) {
                if (val == null) {
                    Log.e(TAG, "DriverCapabilities.Builder.setSupportedCipherSuites:"
                            + " Ignoring null val");
                    return this;
                }
                mSupportedCipherSuites = val;
                return this;
            }

            /** Builds a new DriverCapabilities object. */
            public DriverCapabilities build() {
                return new DriverCapabilities(this);
            }
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
        public final int availableAntennasTx;
        public final int availableAntennasRx;
        public final int configuredAntennasTx;
        public final int configuredAntennasRx;

        private WiphyInfo(Builder builder) {
            this.bandInfo = builder.mBandInfo;
            this.scanCapabilities = builder.mScanCapabilities;
            this.wiphyFeatures = builder.mWiphyFeatures;
            this.driverCapabilities = builder.mDriverCapabilities;
            this.availableAntennasTx = builder.mAvailableAntennasTx;
            this.availableAntennasRx = builder.mAvailableAntennasRx;
            this.configuredAntennasTx = builder.mConfiguredAntennasTx;
            this.configuredAntennasRx = builder.mConfiguredAntennasRx;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            WiphyInfo wiphyInfo = (WiphyInfo) o;
            return availableAntennasTx == wiphyInfo.availableAntennasTx
                    && availableAntennasRx == wiphyInfo.availableAntennasRx
                    && configuredAntennasTx == wiphyInfo.configuredAntennasTx
                    && configuredAntennasRx == wiphyInfo.configuredAntennasRx
                    && Objects.equals(bandInfo, wiphyInfo.bandInfo)
                    && Objects.equals(scanCapabilities, wiphyInfo.scanCapabilities)
                    && Objects.equals(wiphyFeatures, wiphyInfo.wiphyFeatures)
                    && Objects.equals(driverCapabilities, wiphyInfo.driverCapabilities);
        }

        @Override
        public int hashCode() {
            return Objects.hash(bandInfo, scanCapabilities, wiphyFeatures, driverCapabilities,
                    availableAntennasTx, availableAntennasRx, configuredAntennasTx,
                    configuredAntennasRx);
        }

        @Override
        public String toString() {
            return "WiphyInfo {"
                    + "\n  bandInfo: " + bandInfo.toString().replace("\n", "\n  ")
                    + "\n  scanCapabilities: "
                    + scanCapabilities.toString().replace("\n", "\n  ")
                    + "\n  wiphyFeatures: " + wiphyFeatures.toString().replace("\n", "\n  ")
                    + "\n  driverCapabilities: "
                    + driverCapabilities.toString().replace("\n", "\n  ")
                    + "\n  availableAntennasTx: 0x" + Integer.toHexString(availableAntennasTx)
                    + "\n  availableAntennasRx: 0x" + Integer.toHexString(availableAntennasRx)
                    + "\n  configuredAntennasTx: 0x" + Integer.toHexString(configuredAntennasTx)
                    + "\n  configuredAntennasRx: 0x" + Integer.toHexString(configuredAntennasRx)
                    + "\n}";
        }

        public static class Builder {
            @NonNull private BandInfo mBandInfo = new BandInfo.Builder().build();
            @NonNull private ScanCapabilities mScanCapabilities =
                    new ScanCapabilities.Builder().build();
            @NonNull private WiphyFeatures mWiphyFeatures = new WiphyFeatures.Builder().build();
            @NonNull private DriverCapabilities mDriverCapabilities =
                    new DriverCapabilities.Builder().build();
            private int mAvailableAntennasTx = 0;
            private int mAvailableAntennasRx = 0;
            private int mConfiguredAntennasTx = 0;
            private int mConfiguredAntennasRx = 0;

            /** Sets the BandInfo. */
            public Builder setBandInfo(@NonNull BandInfo val) {
                if (val == null) {
                    Log.e(TAG, "WiphyInfo.Builder.setBandInfo: Ignoring null val");
                    return this;
                }
                mBandInfo = val;
                return this;
            }

            /** Sets the ScanCapabilities. */
            public Builder setScanCapabilities(@NonNull ScanCapabilities val) {
                if (val == null) {
                    Log.e(TAG, "WiphyInfo.Builder.setScanCapabilities: Ignoring null val");
                    return this;
                }
                mScanCapabilities = val;
                return this;
            }

            /** Sets the WiphyFeatures. */
            public Builder setWiphyFeatures(@NonNull WiphyFeatures val) {
                if (val == null) {
                    Log.e(TAG, "WiphyInfo.Builder.setWiphyFeatures: Ignoring null val");
                    return this;
                }
                mWiphyFeatures = val;
                return this;
            }

            /** Sets the DriverCapabilities. */
            public Builder setDriverCapabilities(@NonNull DriverCapabilities val) {
                if (val == null) {
                    Log.e(TAG, "WiphyInfo.Builder.setDriverCapabilities: Ignoring null val");
                    return this;
                }
                mDriverCapabilities = val;
                return this;
            }

            /** Sets the available TX antennas bitmap. */
            public Builder setAvailableAntennasTx(int val) {
                mAvailableAntennasTx = val;
                return this;
            }

            /** Sets the available RX antennas bitmap. */
            public Builder setAvailableAntennasRx(int val) {
                mAvailableAntennasRx = val;
                return this;
            }

            /** Sets the configured TX antennas bitmap. */
            public Builder setConfiguredAntennasTx(int val) {
                mConfiguredAntennasTx = val;
                return this;
            }

            /** Sets the configured RX antennas bitmap. */
            public Builder setConfiguredAntennasRx(int val) {
                mConfiguredAntennasRx = val;
                return this;
            }

            /** Builds a new WiphyInfo object. */
            public WiphyInfo build() {
                return new WiphyInfo(this);
            }
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

    private static final char[] HEX_ARRAY = "0123456789abcdef".toCharArray();

    /**
     * Helper method to convert a byte array to a hex string.
     */
    private static String toHexString(@Nullable byte[] bytes) {
        if (bytes == null) return "null";
        char[] hexChars = new char[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = HEX_ARRAY[v >>> 4];
            hexChars[j * 2 + 1] = HEX_ARRAY[v & 0x0F];
        }
        return new String(hexChars);
    }

    /**
     * A class to hold station information retrieved from nl80211.
     * Use the {@link Builder} to create instances.
     */
    public static class StationInfo {
        /** Number of successfully transmitted packets. */
        public final int txPackets;
        /** Number of failed packet transmissions. */
        public final int txFailed;
        /** Current signal strength in dBm. */
        public final int signalDbm;
        /** Current transmit bitrate in 100 Kbps. */
        public final int txBitrate100Kbps;
        /** Current receive bitrate in 100 Kbps. */
        public final int rxBitrate100Kbps;

        private StationInfo(int txPackets, int txFailed, int signalDbm, int txBitrate100Kbps,
                int rxBitrate100Kbps) {
            this.txPackets = txPackets;
            this.txFailed = txFailed;
            this.signalDbm = signalDbm;
            this.txBitrate100Kbps = txBitrate100Kbps;
            this.rxBitrate100Kbps = rxBitrate100Kbps;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            StationInfo that = (StationInfo) o;
            return txPackets == that.txPackets
                    && txFailed == that.txFailed
                    && signalDbm == that.signalDbm
                    && txBitrate100Kbps == that.txBitrate100Kbps
                    && rxBitrate100Kbps == that.rxBitrate100Kbps;
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(txPackets, txFailed, signalDbm, txBitrate100Kbps,
                    rxBitrate100Kbps);
        }

        @Override
        public String toString() {
            return "StationInfo {"
                    + "\n  txPackets: " + txPackets
                    + "\n  txFailed: " + txFailed
                    + "\n  signalDbm: " + signalDbm
                    + "\n  txBitrate100Kbps: " + txBitrate100Kbps
                    + "\n  rxBitrate100Kbps: " + rxBitrate100Kbps
                    + "\n}";
        }

        /**
         * Builder for {@link StationInfo}.
         */
        public static class Builder {
            private int mTxPackets;
            private int mTxFailed;
            private int mSignalDbm;
            private int mTxBitrate100Kbps;
            private int mRxBitrate100Kbps;

            /**
             * Sets the number of successfully transmitted packets.
             */
            public Builder setTxPackets(int txPackets) {
                mTxPackets = txPackets;
                return this;
            }

            /**
             * Sets the number of failed packet transmissions.
             */
            public Builder setTxFailed(int txFailed) {
                mTxFailed = txFailed;
                return this;
            }

            /**
             * Sets the current signal strength in dBm.
             */
            public Builder setSignalDbm(int signalDbm) {
                mSignalDbm = signalDbm;
                return this;
            }

            /**
             * Sets the current transmit bitrate in 100 Kbps.
             */
            public Builder setTxBitrate100Kbps(int txBitrate100Kbps) {
                mTxBitrate100Kbps = txBitrate100Kbps;
                return this;
            }

            /**
             * Sets the current receive bitrate in 100 Kbps.
             */
            public Builder setRxBitrate100Kbps(int rxBitrate100Kbps) {
                mRxBitrate100Kbps = rxBitrate100Kbps;
                return this;
            }

            /**
             * Build the StationInfo object.
             */
            public StationInfo build() {
                return new StationInfo(mTxPackets, mTxFailed, mSignalDbm, mTxBitrate100Kbps,
                        mRxBitrate100Kbps);
            }
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

        @Override
        public String toString() {
            return "PnoScanPlan {"
                    + "\n  intervalMs: " + intervalMs
                    + "\n  iterations: " + iterations
                    + "\n}";
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

        InterfaceInfo ifaceInfo = getInterfaceInfo(ifaceName);
        if (ifaceInfo == null) {
            Log.e(TAG, "Failed to get interface info for " + ifaceName);
            return -1;
        }

        return ifaceInfo.ifIndex;
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

        InterfaceInfo info = getInterfaceInfo(ifaceName);
        if (info == null) {
            return -1;
        }

        mCachedWiphyIndexes.put(ifaceName, info.wiphyIndex);
        return info.wiphyIndex;
    }

    /**
     * Parses a GET_WIPHY response message to create a WiphyInfo object.
     * This method extracts band information, scanning capabilities, wiphy features,
     * and driver capabilities including supported cipher suites and AKM suites.
     *
     * <p>Attributes utilized:
     * <ul>
     *   <li>{@link NetlinkConstants#NL80211_ATTR_WIPHY_BANDS}</li>
     *   <li>{@link NetlinkConstants#NL80211_ATTR_FEATURE_FLAGS}</li>
     *   <li>{@link NetlinkConstants#NL80211_ATTR_EXT_FEATURES}</li>
     *   <li>{@link NetlinkConstants#NL80211_ATTR_MAX_NUM_AKM_SUITES}</li>
     *   <li>{@link NetlinkConstants#NL80211_ATTR_CIPHER_SUITES}</li>
     *   <li>{@link NetlinkConstants#NL80211_ATTR_WIPHY_ANTENNA_AVAIL_TX}</li>
     *   <li>{@link NetlinkConstants#NL80211_ATTR_WIPHY_ANTENNA_AVAIL_RX}</li>
     *   <li>{@link NetlinkConstants#NL80211_ATTR_WIPHY_ANTENNA_TX}</li>
     *   <li>{@link NetlinkConstants#NL80211_ATTR_WIPHY_ANTENNA_RX}</li>
     * </ul>
     *
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
        Set<Integer> supportedCipherSuites = new ArraySet<>();
        Integer availTx = null;
        Integer availRx = null;
        Integer confTx = null;
        Integer confRx = null;

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
            List<Integer> suites = parseCipherSuites(packet);
            if (suites != null) {
                supportedCipherSuites.addAll(suites);
            }
            if (availTx == null) {
                availTx = packet.getAttributeValueAsInteger(NL80211_ATTR_WIPHY_ANTENNA_AVAIL_TX);
            }
            if (availRx == null) {
                availRx = packet.getAttributeValueAsInteger(NL80211_ATTR_WIPHY_ANTENNA_AVAIL_RX);
            }
            if (confTx == null) {
                confTx = packet.getAttributeValueAsInteger(NL80211_ATTR_WIPHY_ANTENNA_TX);
            }
            if (confRx == null) {
                confRx = packet.getAttributeValueAsInteger(NL80211_ATTR_WIPHY_ANTENNA_RX);
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
        DriverCapabilities driverCapabilities = new DriverCapabilities.Builder()
                .setMaxNumAkmSuites(maxNumAkms)
                .setSupportedCipherSuites(supportedCipherSuites)
                .build();

        return new WiphyInfo.Builder()
                .setBandInfo(bandInfo)
                .setScanCapabilities(scanCapabilities)
                .setWiphyFeatures(wiphyFeatures)
                .setDriverCapabilities(driverCapabilities)
                .setAvailableAntennasTx(availTx != null ? availTx : 0)
                .setAvailableAntennasRx(availRx != null ? availRx : 0)
                .setConfiguredAntennasTx(confTx != null ? confTx : 0)
                .setConfiguredAntennasRx(confRx != null ? confRx : 0)
                .build();
    }

    /**
     * Parses the NL80211_ATTR_WIPHY_BANDS attribute from a packet.
     * @return A populated BandInfo object, or null on failure.
     */
    @Nullable
    private BandInfo parseBandInfo(@NonNull List<GenericNetlinkMsg> packets) {
        BandInfo.Builder bandInfoBuilder = new BandInfo.Builder();
        Map<Short, BandCapabilities.Builder> perBandBuilders = new ArrayMap<>();
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
                short bandIndex = bandAttrEntry.getKey();
                BandCapabilities.Builder bandCapsBuilder = perBandBuilders.computeIfAbsent(
                        bandIndex, k -> new BandCapabilities.Builder().setBandIndex(k));
                // Each of the nested attributes of NL80211_ATTR_WIPHY_BANDS contain further nested
                // attributes indexed by one of NL80211_BAND_ATTR_*.
                parseNestedBandAttr(bandAttrEntry.getValue(), bandCapsBuilder);
            }
        }

        if (!foundBandAttr) {
            Log.e(TAG, "parseBandInfo: failed to get mandatory NL80211_ATTR_WIPHY_BANDS");
            return null;
        }

        for (Map.Entry<Short, BandCapabilities.Builder> entry : perBandBuilders.entrySet()) {
            bandInfoBuilder.addBandCapabilities(entry.getKey().intValue(),
                    entry.getValue().build());
        }

        return bandInfoBuilder.build();
    }

    /**
     * Parse the contents of a nested entry of NL80211_ATTR_WIPHY_BANDS.
     */
    private void parseNestedBandAttr(StructNlAttr nestedBandAttr,
            BandCapabilities.Builder bandCapsBuilder) {
        Map<Short, StructNlAttr> innerBandAttrs =
                GenericNetlinkMsg.getInnerNestedAttributes(nestedBandAttr);
        if (innerBandAttrs == null) {
            return;
        }

        // Frequencies
        StructNlAttr freqsAttr = innerBandAttrs.get(NL80211_BAND_ATTR_FREQS);
        if (freqsAttr != null) {
            parseFrequencies(freqsAttr, bandCapsBuilder);
        }

        // PHY Standard Support & Caps
        StructNlAttr htCapaAttr = innerBandAttrs.get(NL80211_BAND_ATTR_HT_CAPA);
        if (htCapaAttr != null) {
            bandCapsBuilder.setIsHtSupported(true);
            bandCapsBuilder.setHtCap(htCapaAttr.nla_value);
        }
        StructNlAttr vhtCapaAttr = innerBandAttrs.get(NL80211_BAND_ATTR_VHT_CAPA);
        if (vhtCapaAttr != null) {
            bandCapsBuilder.setIsVhtSupported(true);
            bandCapsBuilder.setVhtCap(vhtCapaAttr.getValueAsInteger());
        }

        // Detailed PHY Capabilities (MCS, Channel Width, etc.)
        parseBandCapabilities(innerBandAttrs, bandCapsBuilder);
    }

    private void parseFrequencies(StructNlAttr freqsAttr,
            BandCapabilities.Builder bandCapsBuilder) {
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

            boolean disabled = freqProperties.containsKey(NL80211_FREQUENCY_ATTR_DISABLED);
            boolean noIr = freqProperties.containsKey(NL80211_FREQUENCY_ATTR_NO_IR);
            StructNlAttr dfsStateAttr = freqProperties.get(NL80211_FREQUENCY_ATTR_DFS_STATE);
            Integer dfsState = (dfsStateAttr != null) ? dfsStateAttr.getValueAsInteger() : null;

            bandCapsBuilder.addFrequency(new FrequencyInfo.Builder()
                    .setFrequencyMhz(frequencyValue)
                    .setDisabled(disabled)
                    .setNoIr(noIr)
                    .setDfsState(dfsState)
                    .build());
        }
    }

    private void parseBandCapabilities(Map<Short, StructNlAttr> bandProperties,
            BandCapabilities.Builder bandCapsBuilder) {
        // HT/VHT parsing
        StructNlAttr htMcsAttr = bandProperties.get(NL80211_BAND_ATTR_HT_MCS_SET);
        if (htMcsAttr != null) {
            bandCapsBuilder.setHtMcsSet(htMcsAttr.nla_value);
            Pair<Integer, Integer> htStreams = parseHtMcsSetAttribute(htMcsAttr.nla_value);
            bandCapsBuilder.setHtMaxTxStreams(htStreams.first);
            bandCapsBuilder.setHtMaxRxStreams(htStreams.second);
        }

        StructNlAttr htAmpduFactorAttr = bandProperties.get(NL80211_BAND_ATTR_HT_AMPDU_FACTOR);
        if (htAmpduFactorAttr != null) {
            bandCapsBuilder.setHtAmpduFactor(htAmpduFactorAttr.getValueAsByte((byte) 0));
        }

        StructNlAttr htAmpduDensityAttr = bandProperties.get(NL80211_BAND_ATTR_HT_AMPDU_DENSITY);
        if (htAmpduDensityAttr != null) {
            bandCapsBuilder.setHtAmpduDensity(htAmpduDensityAttr.getValueAsByte((byte) 0));
        }

        StructNlAttr vhtMcsAttr = bandProperties.get(NL80211_BAND_ATTR_VHT_MCS_SET);
        if (vhtMcsAttr != null) {
            bandCapsBuilder.setVhtMcsSet(vhtMcsAttr.nla_value);
            Pair<Integer, Integer> vhtStreams = parseVhtMcsSetAttribute(vhtMcsAttr.nla_value);
            bandCapsBuilder.setVhtMaxTxStreams(vhtStreams.first);
            bandCapsBuilder.setVhtMaxRxStreams(vhtStreams.second);
        }

        StructNlAttr vhtCapaAttr = bandProperties.get(NL80211_BAND_ATTR_VHT_CAPA);
        if (vhtCapaAttr != null) {
            bandCapsBuilder.setVhtCap(vhtCapaAttr.getValueAsInteger());
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

        StructNlAttr iftypesAttr = iftypeProperties.get(NL80211_BAND_IFTYPE_ATTR_IFTYPES);
        if (iftypesAttr != null) {
            bandCapsBuilder.setIftypes(iftypesAttr.nla_value);
        }

        StructNlAttr heCapaMacAttr = iftypeProperties.get(NL80211_BAND_IFTYPE_ATTR_HE_CAP_MAC);
        if (heCapaMacAttr != null) {
            bandCapsBuilder.setHeCapMac(heCapaMacAttr.nla_value);
        }
        StructNlAttr heCapaPhyAttr = iftypeProperties.get(NL80211_BAND_IFTYPE_ATTR_HE_CAP_PHY);
        if (heCapaPhyAttr != null) {
            bandCapsBuilder.setIsHeSupported(true);
            bandCapsBuilder.setHeCapPhy(heCapaPhyAttr.nla_value);
        }

        StructNlAttr heMcsAttr = iftypeProperties.get(NL80211_BAND_IFTYPE_ATTR_HE_CAP_MCS_SET);
        if (heMcsAttr != null) {
            bandCapsBuilder.setHeMcsSet(heMcsAttr.nla_value);
            Pair<Integer, Integer> heStreams = parseHeMcsSetAttribute(heMcsAttr.nla_value);
            bandCapsBuilder.setHeMaxTxStreams(heStreams.first);
            bandCapsBuilder.setHeMaxRxStreams(heStreams.second);
        }

        StructNlAttr heCapPpeAttr = iftypeProperties.get(NL80211_BAND_IFTYPE_ATTR_HE_CAP_PPE);
        if (heCapPpeAttr != null) {
            bandCapsBuilder.setHeCapPpe(heCapPpeAttr.nla_value);
        }

        StructNlAttr he6ghzCapaAttr = iftypeProperties.get(NL80211_BAND_IFTYPE_ATTR_HE_6GHZ_CAPA);
        if (he6ghzCapaAttr != null) {
            bandCapsBuilder.setHe6ghzCapa(he6ghzCapaAttr.nla_value);
        }

        StructNlAttr vendorElemsAttr = iftypeProperties.get(NL80211_BAND_IFTYPE_ATTR_VENDOR_ELEMS);
        if (vendorElemsAttr != null) {
            bandCapsBuilder.setVendorElems(vendorElemsAttr.nla_value);
        }

        StructNlAttr ehtCapaMacAttr = iftypeProperties.get(NL80211_BAND_IFTYPE_ATTR_EHT_CAP_MAC);
        if (ehtCapaMacAttr != null) {
            bandCapsBuilder.setEhtCapMac(ehtCapaMacAttr.nla_value);
        }
        StructNlAttr ehtCapaPhyAttr = iftypeProperties.get(NL80211_BAND_IFTYPE_ATTR_EHT_CAP_PHY);
        if (ehtCapaPhyAttr != null) {
            bandCapsBuilder.setIsEhtSupported(true);
            bandCapsBuilder.setEhtCapPhy(ehtCapaPhyAttr.nla_value);
        }
        StructNlAttr ehtMcsAttr = iftypeProperties.get(NL80211_BAND_IFTYPE_ATTR_EHT_CAP_MCS_SET);
        if (ehtMcsAttr != null) {
            bandCapsBuilder.setEhtMcsSet(ehtMcsAttr.nla_value);
            Pair<Integer, Integer> ehtStreams = parseEhtMcsSetAttribute(ehtMcsAttr.nla_value);
            bandCapsBuilder.setEhtMaxTxStreams(ehtStreams.first);
            bandCapsBuilder.setEhtMaxRxStreams(ehtStreams.second);
        }
        StructNlAttr ehtCapPpeAttr = iftypeProperties.get(NL80211_BAND_IFTYPE_ATTR_EHT_CAP_PPE);
        if (ehtCapPpeAttr != null) {
            bandCapsBuilder.setEhtCapPpe(ehtCapPpeAttr.nla_value);
        }
    }

    @NonNull
    private static Pair<Integer, Integer> parseHtMcsSetAttribute(byte[] htMcsSet) {
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
    private static Pair<Integer, Integer> parseVhtMcsSetAttribute(byte[] vhtMcsSet) {
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
    private static Pair<Integer, Integer> parseHeMcsSetAttribute(byte[] heMcsSet) {
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

    private static int parseMcsMap(int mcsMap) {
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
    private static Pair<Boolean, Boolean> parseVhtCapAttribute(Integer vhtCap) {
        if (vhtCap == null) return new Pair<>(false, false);
        boolean is160Mhz = (vhtCap & VHT_160MHZ_BIT_MASK) != 0;
        boolean is80p80Mhz = (vhtCap & VHT_80P80MHZ_BIT_MASK) != 0;
        return new Pair<>(is160Mhz, is80p80Mhz);
    }

    @NonNull
    private static Pair<Boolean, Boolean> parseHeCapPhyAttribute(byte[] heCapPhy) {
        if (heCapPhy == null || heCapPhy.length < HE_CAP_PHY_NUM_BYTE) {
            return new Pair<>(false, false);
        }
        boolean is160Mhz = (heCapPhy[0] & HE_160MHZ_BIT_MASK) != 0;
        boolean is80p80Mhz = (heCapPhy[0] & HE_80P80MHZ_BIT_MASK) != 0;
        return new Pair<>(is160Mhz, is80p80Mhz);
    }

    private static boolean parseEhtCapPhyAttribute(byte[] ehtCapPhy) {
        if (ehtCapPhy == null || ehtCapPhy.length < EHT_CAP_PHY_NUM_BYTE) return false;
        return (ehtCapPhy[0] & EHT_320MHZ_BIT_MASK) != 0;
    }

    @NonNull
    private static Pair<Integer, Integer> parseEhtMcsSetAttribute(byte[] ehtMcsSet) {
        if (ehtMcsSet == null || ehtMcsSet.length < 4) {
            return new Pair<>(0, 0);
        }
        ByteBuffer buffer = ByteBuffer.wrap(ehtMcsSet).order(ByteOrder.LITTLE_ENDIAN);

        int ehtMcsMapRx = buffer.getShort(0) & 0xFFFF;
        int maxRxStreamsEht = parseMcsMap(ehtMcsMapRx);

        int ehtMcsMapTx = buffer.getShort(2) & 0xFFFF;
        int maxTxStreamsEht = parseMcsMap(ehtMcsMapTx);

        return new Pair<>(maxTxStreamsEht, maxRxStreamsEht);
    }

    /**
     * Parses supported cipher suites from a netlink message.
     * See {@link NetlinkConstants#NL80211_ATTR_CIPHER_SUITES}
     *
     * @param packet The netlink message containing cipher suite attributes.
     * @return A list of supported cipher suites, or null if the attribute is missing or malformed.
     */
    @VisibleForTesting
    @Nullable
    protected List<Integer> parseCipherSuites(@NonNull GenericNetlinkMsg packet) {
        StructNlAttr attr = packet.getAttribute(NL80211_ATTR_CIPHER_SUITES);
        if (attr == null || attr.nla_value == null || attr.nla_value.length == 0) {
            return null;
        }
        if (attr.nla_value.length % Integer.BYTES != 0) {
            Log.e(TAG, "parseCipherSuites: Invalid length: " + attr.nla_value.length);
            return null;
        }
        List<Integer> cipherSuites = new ArrayList<>();
        ByteBuffer buffer = attr.getValueAsByteBuffer();
        while (buffer.hasRemaining()) {
            cipherSuites.add(buffer.getInt());
        }
        return cipherSuites;
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

        return new ScanCapabilities.Builder()
                .setMaxNumScanSsids(maxNumScanSsids)
                .setMaxNumSchedScanSsids(maxNumSchedScanSsids)
                .setMaxMatchSets(maxMatchSets)
                .setMaxNumScanPlans(maxNumScanPlans)
                .setMaxScanPlanIntervalSeconds(maxScanPlanIntervalSeconds)
                .setMaxScanPlanIterations(maxScanPlanIterations)
                .build();
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
     * Gets information about all interfaces.
     * @return A list of {@link InterfaceInfo} objects, or null on failure.
     */
    @Nullable
    public List<InterfaceInfo> getInterfaces() {
        GenericNetlinkMsg request = mNl80211Proxy.createNl80211Request(NL80211_CMD_GET_INTERFACE,
                StructNlMsgHdr.NLM_F_DUMP);

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
        List<Nl80211Utils.InterfaceInfo> interfaces = getInterfaces();
        if (interfaces == null) {
            Log.e(TAG, "Failed to get interfaces");
            return null;
        }

        for (Nl80211Utils.InterfaceInfo info : interfaces) {
            if (ifaceName.equals(info.name)) {
                return info;
            }
        }
        return null;
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
        if (hiddenNetworkSSIDs != null) {
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
     * Retrieves station information for a given interface index and MAC address.
     * @param ifIndex The index of the network interface.
     * @param macAddress The MAC address of the station.
     * @return A {@link StationInfo} object if successful, or null on failure.
     */
    public StationInfo getStationInfo(int ifIndex, @NonNull byte[] macAddress) {
        Objects.requireNonNull(macAddress);

        GenericNetlinkMsg request = mNl80211Proxy.createNl80211Request(NL80211_CMD_GET_STATION);

        if (request == null) {
            Log.e(TAG, "Failed to create GET_STATION request");
            return null;
        }

        // Add interface index and MAC address
        request.addAttribute(new StructNlAttr(NL80211_ATTR_IFINDEX, ifIndex));
        request.addAttribute(new StructNlAttr(NL80211_ATTR_MAC, macAddress));

        Nl80211Response response = mNl80211Proxy.sendMessageAndReceiveResponse(request);
        if (response == null || response.isError() || response.getMessage() == null) {
            Log.e(TAG, "Failed to send NL80211_CMD_GET_STATION or received error response");
            return null;
        }

        GenericNetlinkMsg msg = response.getMessage();
        if (msg.getCommand() != NL80211_CMD_NEW_STATION) {
            Log.e(TAG, "Wrong command in response to a get station request: " + msg.getCommand());
            return null;
        }

        StructNlAttr staInfoAttr = msg.getAttribute(NL80211_ATTR_STA_INFO);
        if (staInfoAttr == null) {
            Log.e(TAG, "Failed to get NL80211_ATTR_STA_INFO");
            return null;
        }
        Map<Short, StructNlAttr> staInfoNestedAttrs =
                GenericNetlinkMsg.getInnerNestedAttributes(staInfoAttr);
        if (staInfoNestedAttrs == null) {
            Log.e(TAG, "Empty STA_INFO attribute");
            return null;
        }

        StructNlAttr txPacketsAttr = staInfoNestedAttrs.get(NL80211_STA_INFO_TX_PACKETS);
        StructNlAttr txFailedAttr = staInfoNestedAttrs.get(NL80211_STA_INFO_TX_FAILED);
        StructNlAttr currentRssiAttr = staInfoNestedAttrs.get(NL80211_STA_INFO_SIGNAL);

        Integer txPackets = txPacketsAttr != null ? txPacketsAttr.getValueAsInteger() : null;
        Integer txFailed = txFailedAttr != null ? txFailedAttr.getValueAsInteger() : null;
        Byte currentRssi = currentRssiAttr != null
                ? getByteBufferAsByte(currentRssiAttr.getValueAsByteBuffer()) : null;

        if (txPackets == null || txFailed == null || currentRssi == null) {
            Log.e(TAG, "Failed to get mandatory station info attributes from " + msg);
            return null;
        }

        StationInfo.Builder staInfoBuilder = new StationInfo.Builder()
                .setTxPackets(txPackets)
                .setTxFailed(txFailed)
                .setSignalDbm(currentRssi);

        Integer txBitrate = getBitrateFromStaAttr(staInfoNestedAttrs, NL80211_STA_INFO_TX_BITRATE);
        if (txBitrate != null) {
            staInfoBuilder.setTxBitrate100Kbps(txBitrate);
        }

        Integer rxBitrate = getBitrateFromStaAttr(staInfoNestedAttrs, NL80211_STA_INFO_RX_BITRATE);
        if (rxBitrate != null) {
            staInfoBuilder.setRxBitrate100Kbps(rxBitrate);
        }

        return staInfoBuilder.build();
    }

    private Integer getBitrateFromStaAttr(Map<Short, StructNlAttr> staInfo, short attrId) {
        StructNlAttr bitrateAttr = staInfo.get(attrId);
        if (bitrateAttr == null) return null;

        Map<Short, StructNlAttr> nestedAttrs =
                GenericNetlinkMsg.getInnerNestedAttributes(bitrateAttr);
        if (nestedAttrs == null) return null;

        StructNlAttr bitrate32Attr = nestedAttrs.get(NL80211_RATE_INFO_BITRATE32);
        if (bitrate32Attr == null) return null;

        return bitrate32Attr.getValueAsInteger();
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
     * Sends a management frame on the given interface and returns a cookie upon success.
     * @param ifaceIndex Index of interface to send the frame on.
     * @param frame The raw byte array of the management frame to transmit.
     * @param mcs The MCS (modulation and coding scheme), i.e. rate, at which to transmit the
     *            frame. Specified per IEEE 802.11.
     * @return Cookie upon success, or null on failure.
     */
    public Long sendMgmtFrame(int ifaceIndex, @NonNull byte[] frame, int mcs) {
        GenericNetlinkMsg request = mNl80211Proxy.createNl80211Request(NL80211_CMD_FRAME);
        request.addAttribute(new StructNlAttr(NL80211_ATTR_IFINDEX, ifaceIndex));
        request.addAttribute(new StructNlAttr(NL80211_ATTR_FRAME, frame));
        if (mcs >= 0) {
            // Left unimplemented to match wificond. Note that the only user of sendMgmtFrame comes
            // from WifiShellCommand, which passes a hardcoded value of -1.
        }

        Nl80211Response response = mNl80211Proxy.sendMessageAndReceiveResponse(request);
        if (response == null || response.isError() || response.getMessage() == null) {
            Log.e(TAG, "Failed to send NL80211_CMD_FRAME");
            return null;
        }

        Long cookie = response.getMessage().getAttributeValueAsLong(NL80211_ATTR_COOKIE);
        if (cookie == null) {
            Log.e(TAG, "Failed to get cookie from NL80211_CMD_FRAME");
            return null;
        }

        return cookie;
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

    /**
     * Retrieves Wi-Fi chip statistics from the driver via a vendor-specific Netlink command.
     *
     * @return A {@link ByteBuffer} containing the raw chip statistics payload from the driver,
     */
    public @Nullable ByteBuffer getWifiChipStats(String interfaceName) {
        final int vendorId = ANDROID_OUI;
        final int subcmd = ANDROID_NL80211_SUBCMD_GET_PWRSTATS;
        InterfaceInfo info = getInterfaceInfo(interfaceName);
        if (info == null) {
            Log.e(TAG, "Failed to get interface info for " + interfaceName);
            return null;
        }
        final int ifIndex = info.ifIndex;

        GenericNetlinkMsg vendorRequest = mNl80211Proxy.createVendorRequest(
                ifIndex, vendorId, subcmd);

        if (vendorRequest == null) {
            Log.e(TAG, "Failed to create vendor request for chip statistics.");
            return null;
        }

        Nl80211Response vendorResponse = mNl80211Proxy.sendMessageAndReceiveResponse(vendorRequest);

        if (vendorResponse == null || vendorResponse.isError()
                || vendorResponse.getMessage() == null) {
            Log.e(TAG, "Failed to send Wi-Fi chip statistics vendor command.");
            return null;
        }

        GenericNetlinkMsg msg = vendorResponse.getMessage();
        StructNlAttr payloadAttr = msg.getAttribute(NL80211_ATTR_VENDOR_DATA);

        if (payloadAttr == null) {
            Log.e(TAG, "NL80211_ATTR_VENDOR_DATA attribute not found in vendor response.");
            return null;
        }
        ByteBuffer payloadBuffer = payloadAttr.getValueAsByteBuffer();

        if (payloadBuffer == null) {
            Log.e(TAG, "Chip statistics payload is null in the vendor data attribute.");
            return null;
        }

        return payloadBuffer;
    }
}
