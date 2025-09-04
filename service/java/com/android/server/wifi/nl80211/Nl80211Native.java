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

import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_ATTR_IFNAME;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_CMD_GET_INTERFACE;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.util.Log;

import com.android.net.module.util.netlink.StructNlMsgHdr;

import java.util.ArrayList;
import java.util.List;

/**
 * Provides functionalities that are implemented natively using Nl80211.
 */
public class Nl80211Native {
    private static final String TAG = "Nl80211Native";

    private final @NonNull Nl80211Proxy mNl80211Proxy;
    private boolean mIsInitialized;

    public Nl80211Native(@NonNull Nl80211Proxy nl80211Proxy) {
        mNl80211Proxy = nl80211Proxy;
    }

    /**
     * Initialize this instance of the class.
     *
     * @return true if successful, false otherwise.
     */
    public boolean initialize() {
        if (mIsInitialized) return true;
        mIsInitialized = mNl80211Proxy.initialize();
        Log.i(TAG, "Initialization status: " + mIsInitialized);
        return mIsInitialized;
    }

    /**
     * Check whether this instance has been initialized.
     */
    public boolean isInitialized() {
        return mIsInitialized;
    }

    /**
     * Get the names of all interfaces available on this device.
     *
     * @return List of interface names, or null if an error occurred.
     */
    public @Nullable List<String> getInterfaceNames() {
        if (!mIsInitialized) return null;
        GenericNetlinkMsg request =
                mNl80211Proxy.createNl80211Request(
                        NL80211_CMD_GET_INTERFACE, StructNlMsgHdr.NLM_F_DUMP);
        if (request == null) {
            Log.e(TAG, "Failed to create Nl80211 request");
            return null;
        }
        List<GenericNetlinkMsg> responses = mNl80211Proxy.sendMessageAndReceiveResponses(request);
        if (responses == null) {
            Log.e(TAG, "Failed to get interface names");
            return null;
        }
        List<String> interfaceNames = new ArrayList<>();
        for (GenericNetlinkMsg response : responses) {
            if (response.getAttribute(NL80211_ATTR_IFNAME) != null) {
                interfaceNames.add(response.getAttribute(NL80211_ATTR_IFNAME).getValueAsString());
            }
        }
        return interfaceNames;
    }
}
