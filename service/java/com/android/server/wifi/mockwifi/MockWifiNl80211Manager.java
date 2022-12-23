/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.server.wifi.mockwifi;

import android.content.Context;
import android.net.wifi.nl80211.WifiNl80211Manager;
import android.os.IBinder;

/**
 * Mocked WifiNl80211Manager
 */
public class MockWifiNl80211Manager {
    private static final String TAG = "MockWifiNl80211Manager";

    private Context mContext;
    private WifiNl80211Manager mMockWifiNl80211Manager;

    public MockWifiNl80211Manager(IBinder wificondBinder, Context context) {
        mContext = context;
        mMockWifiNl80211Manager = new WifiNl80211Manager(mContext, wificondBinder);
    }

    public WifiNl80211Manager getWifiNl80211Manager() {
        return mMockWifiNl80211Manager;
    }
}
