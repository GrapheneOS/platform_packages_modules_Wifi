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

import android.annotation.DurationMillisLong;
import android.annotation.NonNull;

import com.android.modules.utils.build.SdkLevel;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Configuration for a PNO (preferred network offload). A mechanism by which scans are offloaded
 * from the host device to the Wi-Fi chip.
 *
 * This class was copied from {@link android.net.wifi.nl80211.PnoSettings}
 */
public final class PnoSettings {
    private long mIntervalMs;
    private int mMin2gRssi;
    private int mMin5gRssi;
    private int mMin6gRssi;
    private int mScanIterations;
    private int mScanIntervalMultiplier;
    @NonNull
    private List<PnoNetwork> mPnoNetworks;

    /** Construct an uninitialized PnoSettings object */
    public PnoSettings() {
        mPnoNetworks = new ArrayList<>();
    }

    /**
     * Get the requested PNO scan interval in milliseconds.
     *
     * @return An interval in milliseconds.
     */
    public @DurationMillisLong long getIntervalMillis() {
        return mIntervalMs;
    }

    /**
     * Set the requested PNO scan interval in milliseconds.
     *
     * @param intervalMillis An interval in milliseconds.
     */
    public void setIntervalMillis(@DurationMillisLong long intervalMillis) {
        this.mIntervalMs = intervalMillis;
    }

    /**
     * Get the requested minimum RSSI threshold (in dBm) for APs to report in scan results in the
     * 2.4GHz band.
     *
     * @return An RSSI value in dBm.
     */
    public int getMin2gRssiDbm() {
        return mMin2gRssi;
    }

    /**
     * Set the requested minimum RSSI threshold (in dBm) for APs to report in scan scan results in
     * the 2.4GHz band.
     *
     * @param min2gRssiDbm An RSSI value in dBm.
     */
    public void setMin2gRssiDbm(int min2gRssiDbm) {
        this.mMin2gRssi = min2gRssiDbm;
    }

    /**
     * Get the requested minimum RSSI threshold (in dBm) for APs to report in scan results in the
     * 5GHz band.
     *
     * @return An RSSI value in dBm.
     */
    public int getMin5gRssiDbm() {
        return mMin5gRssi;
    }

    /**
     * Set the requested minimum RSSI threshold (in dBm) for APs to report in scan scan results in
     * the 5GHz band.
     *
     * @param min5gRssiDbm An RSSI value in dBm.
     */
    public void setMin5gRssiDbm(int min5gRssiDbm) {
        this.mMin5gRssi = min5gRssiDbm;
    }

    /**
     * Get the requested minimum RSSI threshold (in dBm) for APs to report in scan results in the
     * 6GHz band.
     *
     * @return An RSSI value in dBm.
     */
    public int getMin6gRssiDbm() {
        return mMin6gRssi;
    }

    /**
     * Set the requested minimum RSSI threshold (in dBm) for APs to report in scan scan results in
     * the 6GHz band.
     *
     * @param min6gRssiDbm An RSSI value in dBm.
     */
    public void setMin6gRssiDbm(int min6gRssiDbm) {
        this.mMin6gRssi = min6gRssiDbm;
    }

    /**
     * Get the requested PNO scan iterations.
     *
     * @return PNO scan iterations.
     */
    public int getScanIterations() {
        return mScanIterations;
    }

    /**
     * Set the requested PNO scan iterations.
     *
     * @param scanIterations the PNO scan iterations.
     */
    public void setScanIterations(int scanIterations) {
        this.mScanIterations = scanIterations;
    }

    /**
     * Get the requested PNO scan interval multiplier.
     *
     * @return PNO scan interval multiplier.
     */
    public int getScanIntervalMultiplier() {
        return mScanIntervalMultiplier;
    }

    /**
     * Set the requested PNO scan interval multiplier.
     *
     * @param scanIntervalMultiplier the PNO scan interval multiplier.
     */
    public void setScanIntervalMultiplier(int scanIntervalMultiplier) {
        this.mScanIntervalMultiplier = scanIntervalMultiplier;
    }

    /**
     * Return the configured list of specific networks to search for in a PNO scan.
     *
     * @return A list of {@link PnoNetwork} objects, possibly empty if non configured.
     */
    @NonNull public List<PnoNetwork> getPnoNetworks() {
        return mPnoNetworks;
    }

    /**
     * Set the list of specified networks to scan for in a PNO scan. The networks (APs) are
     * specified using {@link PnoNetwork}s. An empty list indicates that all networks are scanned
     * for.
     *
     * @param pnoNetworks A (possibly empty) list of {@link PnoNetwork} objects.
     */
    public void setPnoNetworks(@NonNull List<PnoNetwork> pnoNetworks) {
        this.mPnoNetworks = pnoNetworks;
    }

    @Override
    public boolean equals(Object rhs) {
        if (this == rhs) return true;
        if (!(rhs instanceof PnoSettings)) {
            return false;
        }
        PnoSettings settings = (PnoSettings) rhs;
        if (settings == null) {
            return false;
        }
        return mIntervalMs == settings.mIntervalMs
                && mMin2gRssi == settings.mMin2gRssi
                && mMin5gRssi == settings.mMin5gRssi
                && mMin6gRssi == settings.mMin6gRssi
                && mScanIterations == settings.mScanIterations
                && mScanIntervalMultiplier == settings.mScanIntervalMultiplier
                && mPnoNetworks.equals(settings.mPnoNetworks);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mIntervalMs, mMin2gRssi, mMin5gRssi, mMin6gRssi,
                mScanIterations, mScanIntervalMultiplier, mPnoNetworks);
    }

    /**
     * Returns this object as a legacy {@link android.net.wifi.nl80211.PnoSettings}.
     */
    public android.net.wifi.nl80211.PnoSettings toWificondPnoSettings() {
        android.net.wifi.nl80211.PnoSettings wificondPnoSettings =
                new android.net.wifi.nl80211.PnoSettings();
        wificondPnoSettings.setIntervalMillis(this.getIntervalMillis());
        wificondPnoSettings.setMin2gRssiDbm(this.getMin2gRssiDbm());
        wificondPnoSettings.setMin5gRssiDbm(this.getMin5gRssiDbm());
        wificondPnoSettings.setMin6gRssiDbm(this.getMin6gRssiDbm());
        if (SdkLevel.isAtLeastU()) {
            wificondPnoSettings.setScanIterations(this.getScanIterations());
            wificondPnoSettings.setScanIntervalMultiplier(this.getScanIntervalMultiplier());
        }

        List<android.net.wifi.nl80211.PnoNetwork> wificondPnoNetworks = new ArrayList<>();
        for (PnoNetwork pnoNetwork : this.getPnoNetworks()) {
            android.net.wifi.nl80211.PnoNetwork wificondPnoNetwork =
                    new android.net.wifi.nl80211.PnoNetwork();
            wificondPnoNetwork.setSsid(pnoNetwork.getSsid());
            wificondPnoNetwork.setHidden(pnoNetwork.isHidden());
            wificondPnoNetwork.setFrequenciesMhz(pnoNetwork.getFrequenciesMhz());
            wificondPnoNetworks.add(wificondPnoNetwork);
        }
        wificondPnoSettings.setPnoNetworks(wificondPnoNetworks);

        return wificondPnoSettings;
    }
}
