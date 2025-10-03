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

import com.android.internal.annotations.VisibleForTesting;

import java.util.Objects;

/**
 * A class representing the radio chains of the Wi-Fi modems. Use to provide raw information about
 * signals received on different radio chains.
 *
 * This class was copied from {@link android.net.wifi.nl80211.RadioChainInfo}
 */
public final class RadioChainInfo {
    private static final String TAG = "RadioChainInfo";

    @VisibleForTesting
    public int chainId;
    @VisibleForTesting
    public int level;

    /**
     * Construct an empty RadioChainInfo.
     */
    public RadioChainInfo() { }

    /**
     * Return an identifier for this radio chain. This is an arbitrary ID which is consistent for
     * the same device.
     *
     * @return The radio chain ID.
     */
    public int getChainId() {
        return chainId;
    }

    /**
     * Returns the detected signal level on this radio chain in dBm (aka RSSI).
     *
     * @return A signal level in dBm.
     */
    public int getLevelDbm() {
        return level;
    }

    /**
     * Construct a RadioChainInfo.
     */
    public RadioChainInfo(int chainId, int level) {
        this.chainId = chainId;
        this.level = level;
    }

    /** Copy constructor to convert Wificond RadioChainInfo */
    public RadioChainInfo(android.net.wifi.nl80211.RadioChainInfo wificondRadioChainInfo) {
        this.chainId = wificondRadioChainInfo.getChainId();
        this.level = wificondRadioChainInfo.getLevelDbm();
    }

    @Override
    public boolean equals(Object rhs) {
        if (this == rhs) return true;
        if (!(rhs instanceof RadioChainInfo)) {
            return false;
        }
        RadioChainInfo chainInfo = (RadioChainInfo) rhs;
        if (chainInfo == null) {
            return false;
        }
        return chainId == chainInfo.chainId && level == chainInfo.level;
    }

    @Override
    public int hashCode() {
        return Objects.hash(chainId, level);
    }
}
