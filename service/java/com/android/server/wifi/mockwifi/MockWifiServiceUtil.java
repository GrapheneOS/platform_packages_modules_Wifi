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

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.util.Log;

/** This class provides wrapper APIs for binding interfaces to mock service. */
public class MockWifiServiceUtil {
    private static final String TAG = "MockWifiModemUtil";

    public static final int MIN_SERVICE_IDX = 0; // no mocked HAL service now
    public static final int NUM_SERVICES = 0;
    public static final int BINDER_RETRY_MILLIS = 3 * 100;
    public static final int BINDER_MAX_RETRY = 3;

    private Context mContext;
    private String mServiceName;
    private String mPackageName;

    public MockWifiServiceUtil(Context context, String serviceName) {
        mContext = context;
        String[] componentInfo = serviceName.split("/", 2);
        mPackageName = componentInfo[0];
        mServiceName = componentInfo[1];
    }

    private class MockModemConnection implements ServiceConnection {
        private int mService;

        MockModemConnection(int module) {
            mService = module;
        }

        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            Log.d(TAG, "Wifi mock Service " + getModuleName(mService) + "  - onServiceConnected");
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.d(TAG, "Wifi mock Service " + getModuleName(mService)
                    + "  - onServiceDisconnected");
        }
    }

    private boolean bindModuleToMockModemService(
            String actionName, ServiceConnection serviceConnection) {
        boolean status = false;

        Intent intent = new Intent();
        intent.setComponent(new ComponentName(mPackageName, mServiceName));
        intent.setAction(actionName);

        status = mContext.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
        return status;
    }

    /** waitForBinder */
    public IBinder getServiceBinder(int service) {
        switch (service) {
            default:
                return null;
        }
    }

    /** Binding interfaces with mock modem service */
    public void bindAllMockModemService() {
        for (int service = MIN_SERVICE_IDX; service < NUM_SERVICES; service++) {
            bindToMockModemService(service);
        }
    }

    /** bindToMockModemService */
    public void bindToMockModemService(int service) {
        // Based on {@code service} to get each mocked HAL binder
    }

    public String getServiceName() {
        return mServiceName;
    }

    private ServiceConnection getServiceConnection(int service) {
        switch (service) {
            default:
                return null;
        }
    }

    /**
     * Returns name of the provided service.
     */
    public String getModuleName(int service) {
        switch (service) {
            default:
                return "none";
        }
    }
}
