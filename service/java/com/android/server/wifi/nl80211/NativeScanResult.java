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

import android.annotation.IntDef;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.SuppressLint;
import android.net.MacAddress;
import android.util.Log;

import com.android.internal.annotations.VisibleForTesting;

import java.io.PrintWriter;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Raw scan result data from nl80211 daemon.
 *
 * This class was copied from {@link android.net.wifi.nl80211.NativeScanResult}
 */
public final class NativeScanResult {
    private static final String TAG = "NativeScanResult";

    @VisibleForTesting
    @NonNull
    public byte[] ssid;
    @VisibleForTesting
    @NonNull
    public byte[] bssid;
    @VisibleForTesting
    @NonNull
    public byte[] infoElement;
    @VisibleForTesting
    public int frequency;
    @VisibleForTesting
    public int signalMbm;
    @VisibleForTesting
    public long tsf;
    @VisibleForTesting
    @BssCapabilityBits public int capability;
    @VisibleForTesting
    public boolean associated;
    @VisibleForTesting
    @NonNull
    public List<RadioChainInfo> radioChainInfos;

    /**
     * Returns the SSID raw byte array of the AP represented by this scan result.
     *
     * @return A byte array.
     */
    @NonNull public byte[] getSsid() {
        return ssid;
    }

    /**
     * Returns the MAC address (BSSID) of the AP represented by this scan result.
     *
     * @return a MacAddress or null on error.
     */
    @Nullable public MacAddress getBssid() {
        try {
            return MacAddress.fromBytes(bssid);
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "Illegal argument " + Arrays.toString(bssid), e);
            return null;
        }
    }

    /**
     * Returns the raw bytes of the information element advertised by the AP represented by this
     * scan result.
     *
     * @return A byte array, possibly null or containing an invalid TLV configuration.
     */
    @NonNull public byte[] getInformationElements() {
        return infoElement;
    }

    /**
     * Returns the frequency (in MHz) on which the AP represented by this scan result was observed.
     *
     * @return The frequency in MHz.
     */
    public int getFrequencyMhz() {
        return frequency;
    }

    /**
     * Return the signal strength of probe response/beacon in (100 * dBm).
     *
     * @return Signal strenght in (100 * dBm).
     */
    public int getSignalMbm() {
        return signalMbm;
    }

    /**
     * Returns the TSF (Timing Synchronization Function) from the most recently received
     * beacon or probe response frame.
     *
     * @return The TSF timestamp in microseconds.
     */
    public long getTsf() {
        return tsf;
    }

    /**
     * Return a boolean indicating whether or not we're associated to the AP represented by this
     * scan result.
     *
     * @return A boolean indicating association.
     */
    public boolean isAssociated() {
        return associated;
    }

    @Retention(RetentionPolicy.SOURCE)
    @IntDef(flag = true, prefix = {"BSS_CAPABILITY_"},
            value = {BSS_CAPABILITY_ESS,
                    BSS_CAPABILITY_IBSS,
                    BSS_CAPABILITY_CF_POLLABLE,
                    BSS_CAPABILITY_CF_POLL_REQUEST,
                    BSS_CAPABILITY_PRIVACY,
                    BSS_CAPABILITY_SHORT_PREAMBLE,
                    BSS_CAPABILITY_PBCC,
                    BSS_CAPABILITY_CHANNEL_AGILITY,
                    BSS_CAPABILITY_SPECTRUM_MANAGEMENT,
                    BSS_CAPABILITY_QOS,
                    BSS_CAPABILITY_SHORT_SLOT_TIME,
                    BSS_CAPABILITY_APSD,
                    BSS_CAPABILITY_RADIO_MANAGEMENT,
                    BSS_CAPABILITY_DSSS_OFDM,
                    BSS_CAPABILITY_DELAYED_BLOCK_ACK,
                    BSS_CAPABILITY_IMMEDIATE_BLOCK_ACK,
                    BSS_CAPABILITY_DMG_ESS,
                    BSS_CAPABILITY_DMG_IBSS
            })
    public @interface BssCapabilityBits { }

    /**
     * BSS capability bit (see IEEE Std 802.11: 9.4.1.4): ESS.
     */
    public static final int BSS_CAPABILITY_ESS = 0x1 << 0;
    /**
     * BSS capability bit (see IEEE Std 802.11: 9.4.1.4): IBSS.
     */
    public static final int BSS_CAPABILITY_IBSS = 0x1 << 1;
    /**
     * BSS capability bit (see IEEE Std 802.11: 9.4.1.4): CF Pollable.
     */
    public static final int BSS_CAPABILITY_CF_POLLABLE = 0x1 << 2;
    /**
     * BSS capability bit (see IEEE Std 802.11: 9.4.1.4): CF-Poll Request.
     */
    public static final int BSS_CAPABILITY_CF_POLL_REQUEST = 0x1 << 3;
    /**
     * BSS capability bit (see IEEE Std 802.11: 9.4.1.4): Privacy.
     */
    public static final int BSS_CAPABILITY_PRIVACY = 0x1 << 4;
    /**
     * BSS capability bit (see IEEE Std 802.11: 9.4.1.4): Short Preamble.
     */
    public static final int BSS_CAPABILITY_SHORT_PREAMBLE = 0x1 << 5;
    /**
     * BSS capability bit (see IEEE Std 802.11: 9.4.1.4): PBCC.
     */
    public static final int BSS_CAPABILITY_PBCC = 0x1 << 6;
    /**
     * BSS capability bit (see IEEE Std 802.11: 9.4.1.4): Channel Agility.
     */
    public static final int BSS_CAPABILITY_CHANNEL_AGILITY = 0x1 << 7;
    /**
     * BSS capability bit (see IEEE Std 802.11: 9.4.1.4): Spectrum Management.
     */
    public static final int BSS_CAPABILITY_SPECTRUM_MANAGEMENT = 0x1 << 8;
    /**
     * BSS capability bit (see IEEE Std 802.11: 9.4.1.4): QoS.
     */
    public static final int BSS_CAPABILITY_QOS = 0x1 << 9;
    /**
     * BSS capability bit (see IEEE Std 802.11: 9.4.1.4): Short Slot Time.
     */
    public static final int BSS_CAPABILITY_SHORT_SLOT_TIME = 0x1 << 10;
    /**
     * BSS capability bit (see IEEE Std 802.11: 9.4.1.4): APSD.
     */
    public static final int BSS_CAPABILITY_APSD = 0x1 << 11;
    /**
     * BSS capability bit (see IEEE Std 802.11: 9.4.1.4): Radio Management.
     */
    public static final int BSS_CAPABILITY_RADIO_MANAGEMENT = 0x1 << 12;
    /**
     * BSS capability bit (see IEEE Std 802.11: 9.4.1.4): DSSS-OFDM.
     */
    public static final int BSS_CAPABILITY_DSSS_OFDM = 0x1 << 13;
    /**
     * BSS capability bit (see IEEE Std 802.11: 9.4.1.4): Delayed Block Ack.
     */
    public static final int BSS_CAPABILITY_DELAYED_BLOCK_ACK = 0x1 << 14;
    /**
     * BSS capability bit (see IEEE Std 802.11: 9.4.1.4): Immediate Block Ack.
     */
    public static final int BSS_CAPABILITY_IMMEDIATE_BLOCK_ACK = 0x1 << 15;
    /**
     * BSS capability bit (see IEEE Std 802.11: 9.4.1.4): DMG ESS.
     * In DMG bits 0 and 1 are parsed together, where ESS=0x3 and IBSS=0x1
     */
    public static final int BSS_CAPABILITY_DMG_ESS = 0x3;
    /**
     * BSS capability bit (see IEEE Std 802.11: 9.4.1.4): DMG IBSS.
     */
    public static final int BSS_CAPABILITY_DMG_IBSS = 0x1;

    /**
     *  Returns the capabilities of the AP represented by this scan result as advertised in the
     *  received probe response or beacon.
     *
     *  This is a bit mask describing the capabilities of a BSS. See IEEE Std 802.11: 9.4.1.4: one
     *  of the {@code BSS_CAPABILITY_*} flags.
     *
     * @return a bit mask of capabilities.
     */
    @BssCapabilityBits public int getCapabilities() {
        return capability;
    }

    /**
     * Returns details of the signal received on each radio chain for the AP represented by this
     * scan result in a list of {@link RadioChainInfo} elements.
     *
     * @return A list of {@link RadioChainInfo} - possibly empty in case of error.
     */
    @NonNull public List<RadioChainInfo> getRadioChainInfos() {
        return radioChainInfos;
    }

    /**
     * Construct an empty native scan result.
     */
    public NativeScanResult() {
        this.ssid = new byte[0];
        this.bssid = new byte[0];
        this.infoElement = new byte[0];
        this.radioChainInfos = new ArrayList<>();
    }

    /** Copy constructor to convert Wificond NativeScanResult */
    @SuppressLint("WrongConstant")
    public NativeScanResult(
            android.net.wifi.nl80211.NativeScanResult wificondScanResult) {
        this.ssid = wificondScanResult.getSsid();
        // getSsid() is marked @NonNull but may actually be null.
        if (this.ssid == null) this.ssid = new byte[0];

        if (wificondScanResult.getBssid() != null) {
            this.bssid = wificondScanResult.getBssid().toByteArray();
        }
        if (this.bssid == null) this.ssid = new byte[0];

        this.infoElement = wificondScanResult.getInformationElements();
        // getInformationElements() is marked @NonNull but may actually be null.
        if (this.infoElement == null) this.infoElement = new byte[0];

        this.frequency = wificondScanResult.getFrequencyMhz();
        this.signalMbm = wificondScanResult.getSignalMbm();
        this.tsf = wificondScanResult.getTsf();
        this.capability = wificondScanResult.getCapabilities();
        this.associated = wificondScanResult.isAssociated();
        this.radioChainInfos = new ArrayList<>();
        if (wificondScanResult.getRadioChainInfos() != null) {
            for (android.net.wifi.nl80211.RadioChainInfo chainInfo :
                    wificondScanResult.getRadioChainInfos()) {
                this.radioChainInfos.add(new RadioChainInfo(chainInfo));
            }
        }
    }

    /**
     * Dumps the contents of a list of scan results.
     */
    public static void dumpList(
            @NonNull PrintWriter pw, @NonNull List<NativeScanResult> nativeResults) {

        // Updated header to include IE Length and Associated status
        pw.printf("%-18s %-10s %-7s %-12s %-10s %-8s %-8s %-10s %-10s %s\n",
                "BSSID", "Frequency", "Signal", "TSF", "Capability", "ESS", "Privacy", "IE Length",
                "Associated", "SSID");

        for (NativeScanResult result : nativeResults) {
            // BSSID
            String bssidStr = "XX:XX:XX:XX:XX:XX";
            if (result.bssid != null && result.bssid.length == 6) {
                bssidStr = MacAddress.fromBytes(result.bssid).toString();
            }

            // SSID
            String ssidStr = "";
            if (result.ssid != null) {
                // Use StandardCharsets.UTF_8 for robust decoding
                ssidStr = new String(result.ssid, StandardCharsets.UTF_8);
            }

            // Signal (convert mBm to dBm)
            int signalDbm = result.signalMbm / 100;

            // TSF
            long tsf = result.tsf;

            // Capability (as hex for debugging)
            String capabilityHex = Integer.toHexString(result.capability);

            // ESS/IBSS check
            String essStatus;
            if ((result.capability & BSS_CAPABILITY_ESS) != 0) {
                essStatus = "ESS";
            } else if ((result.capability & BSS_CAPABILITY_IBSS) != 0) {
                essStatus = "IBSS";
            } else {
                essStatus = "N/A";
            }

            // Privacy check (WPA/WPA2/etc. indicator)
            String privacyStatus = (result.capability & BSS_CAPABILITY_PRIVACY) != 0 ? "Yes" : "No";

            // Associated check
            String associatedStatus = result.associated ? "Yes" : "No";

            // IE Length
            int ieLength = result.infoElement != null ? result.infoElement.length : 0;


            // Print main BSS line with new columns
            pw.printf("%-18s %-10d %-7d %-12d %-10s %-8s %-8s %-10d %-10s %s\n",
                    bssidStr,
                    result.frequency,
                    signalDbm,
                    tsf,
                    capabilityHex,
                    essStatus,
                    privacyStatus,
                    ieLength,       // IE Length column
                    associatedStatus, // Associated column
                    ssidStr);

            // Print Radio Chain Info (indented on subsequent lines)
            if (result.radioChainInfos != null && !result.radioChainInfos.isEmpty()) {
                // Small header for the chain info
                pw.printf("  %-16s %-6s %-6s\n", "Chain Info:", "ID", "RSSI");
                for (RadioChainInfo chainInfo : result.radioChainInfos) {
                    // Indented line showing ID and RSSI
                    pw.printf("  %-16s %-6d %-6d\n", "", chainInfo.getChainId(),
                            chainInfo.getLevelDbm());
                }
            }
        }
    }
}
