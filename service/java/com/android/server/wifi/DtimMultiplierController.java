/*
 * Copyright (C) 2023 The Android Open Source Project
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

import android.annotation.NonNull;
import android.content.Context;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.util.Log;

import com.android.wifi.resources.R;

import java.net.Inet4Address;
import java.net.Inet6Address;

/**
 * DTIM multiplier controller
 * When Wifi STA is in the power saving mode and the system is suspended, the wakeup interval will
 * be set to:
 *    1) multiplier * AP's DTIM period if multiplier > 0.
 *    2) the driver default value if multiplier <= 0.
 * Some implementations may apply an additional cap to wakeup interval in the case of 1).
 */
public class DtimMultiplierController {
    private static final String TAG = "DtimMultiplierController";
    private final Context mContext;
    private final WifiNative mWifiNative;
    private final String mInterfaceName;
    private static final int DTIM_MULTIPLIER_RESET = 0;
    private boolean mMulticastLockEnabled;
    private boolean mHasIpv4Addr;
    private boolean mHasIpv6Addr;
    private int mDtimMultiplier = DTIM_MULTIPLIER_RESET;
    private boolean mVerboseLoggingEnabled;
    public DtimMultiplierController(Context context, String interfaceName, WifiNative wifiNative) {
        mContext = context;
        mWifiNative = wifiNative;
        mInterfaceName = interfaceName;
    }

    /**
     * Reset internal variables after disconnection
     */
    public void reset() {
        mMulticastLockEnabled = false;
        mHasIpv4Addr = false;
        mHasIpv6Addr = false;
        mDtimMultiplier = DTIM_MULTIPLIER_RESET;
    }

    /**
     * Enable/disable verbose logging
     */
    public void enableVerboseLogging(boolean verboseLoggingEnabled) {
        mVerboseLoggingEnabled = verboseLoggingEnabled;
    }

    /**
     * Set multicast lock enable/disable status
     */
    public void setMulticastLock(boolean enabled) {
        mMulticastLockEnabled = enabled;
        if (mVerboseLoggingEnabled) {
            Log.d(TAG, "mMulticastLockEnabled " + mMulticastLockEnabled);
        }
        updateDtimMultiplier();
    }

    /**
     * Update link properties which contain IP addresses
     */
    public void updateLinkProperties(@NonNull LinkProperties newLp) {
        mHasIpv4Addr = false;
        mHasIpv6Addr = false;
        for (LinkAddress addr : newLp.getLinkAddresses()) {
            if (addr.getAddress() instanceof Inet4Address) {
                mHasIpv4Addr = true;
            } else if (addr.getAddress() instanceof Inet6Address) {
                mHasIpv6Addr = true;
            }
        }
        updateDtimMultiplier();
    }

    private void updateDtimMultiplier() {
        if (!mHasIpv4Addr && !mHasIpv6Addr) {
            return;
        }
        if (!mContext.getResources().getBoolean(R.bool.config_wifiDtimMultiplierConfigEnabled)) {
            return;
        }
        int multiplier = deriveDtimMultiplier();

        if (mDtimMultiplier == multiplier) {
            return;
        }
        mDtimMultiplier = multiplier;
        boolean success = mWifiNative.setDtimMultiplier(mInterfaceName, multiplier);
        if (mVerboseLoggingEnabled) {
            StringBuilder sb = new StringBuilder()
                    .append("Set DtimMultiplier to ").append(mDtimMultiplier)
                    .append(" success ").append(success)
                    .append(" mMulticastLockEnabled ").append(mMulticastLockEnabled)
                    .append(" mHasIpv4Addr ").append(mHasIpv4Addr)
                    .append(" mHasIpv6Addr ").append(mHasIpv6Addr);
            if (!success) {
                Log.e(TAG, sb.toString());
            } else {
                Log.d(TAG, sb.toString());
            }
        }
    }

    private int deriveDtimMultiplier() {
        if (mMulticastLockEnabled) {
            return mContext.getResources().getInteger(
                    R.integer.config_wifiDtimMultiplierMulticastLockEnabled);
        }
        if (mHasIpv4Addr && !mHasIpv6Addr) {
            return mContext.getResources().getInteger(
                    R.integer.config_wifiDtimMultiplierIpv4Only);
        } else if (!mHasIpv4Addr && mHasIpv6Addr) {
            return mContext.getResources().getInteger(
                    R.integer.config_wifiDtimMultiplierIpv6Only);
        } else {
            return mContext.getResources().getInteger(
                    R.integer.config_wifiDtimMultiplierIpv6Ipv4);
        }
    }
}
