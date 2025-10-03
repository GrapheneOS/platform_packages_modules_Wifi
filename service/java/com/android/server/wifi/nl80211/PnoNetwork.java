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

import android.annotation.NonNull;

import java.util.Arrays;
import java.util.Objects;

/**
 * Configuration for a PNO (preferred network offload) network used in {@link PnoSettings}. A PNO
 * network allows configuration of a specific network to search for.
 *
 * This class was copied from {@link android.net.wifi.nl80211.PnoNetwork}
 */
public final class PnoNetwork {
    private boolean mIsHidden;
    @NonNull
    private byte[] mSsid;
    @NonNull
    private int[] mFrequencies;

    /**
     * Indicates whether the PNO network configuration is for a hidden SSID - i.e. a network which
     * does not broadcast its SSID and must be queried explicitly.
     *
     * @return True if the configuration is for a hidden network, false otherwise.
     */
    public boolean isHidden() {
        return mIsHidden;
    }

    /**
     * Configure whether the PNO network configuration is for a hidden SSID - i.e. a network which
     * does not broadcast its SSID and must be queried explicitly.
     *
     * @param isHidden True if the configuration is for a hidden network, false otherwise.
     */
    public void setHidden(boolean isHidden) {
        mIsHidden = isHidden;
    }

    /**
     * Get the raw bytes for the SSID of the PNO network being scanned for.
     *
     * @return A byte array.
     */
    @NonNull public byte[] getSsid() {
        return mSsid;
    }

    /**
     * Set the raw bytes for the SSID of the PNO network being scanned for.
     *
     * @param ssid A byte array.
     */
    public void setSsid(@NonNull byte[] ssid) {
        if (ssid == null) {
            throw new IllegalArgumentException("null argument");
        }
        this.mSsid = ssid;
    }

    /**
     * Get the frequencies (in MHz) on which to PNO scan for the current network is being searched
     * for. An empty return (i.e. no frequencies configured) indicates that the network is search
     * for on all supported frequencies.
     *
     * @return A array of frequencies (in MHz).
     */
    @NonNull public int[] getFrequenciesMhz() {
        return mFrequencies;
    }

    /**
     * Set the frequencies (in MHz) on which to PNO scan for the current network is being searched
     * for. An empty configuration (i.e. no frequencies configured) indicates that the network is
     * search for on all supported frequencies.
     *
     * @param frequenciesMhz an array of frequencies (in MHz), empty indicating no configured
     *                       frequencies.
     */
    public void setFrequenciesMhz(@NonNull int[] frequenciesMhz) {
        if (frequenciesMhz == null) {
            throw new IllegalArgumentException("null argument");
        }
        this.mFrequencies = frequenciesMhz;
    }

    /** Construct an uninitialized PnoNetwork object */
    public PnoNetwork() {
        mSsid = new byte[0];
        mFrequencies = new int[0];
    }

    @Override
    public boolean equals(Object rhs) {
        if (this == rhs) return true;
        if (!(rhs instanceof PnoNetwork)) {
            return false;
        }
        PnoNetwork network = (PnoNetwork) rhs;
        return Arrays.equals(mSsid, network.mSsid)
                && Arrays.equals(mFrequencies, network.mFrequencies)
                && mIsHidden == network.mIsHidden;
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                mIsHidden,
                Arrays.hashCode(mSsid),
                Arrays.hashCode(mFrequencies));
    }
}
