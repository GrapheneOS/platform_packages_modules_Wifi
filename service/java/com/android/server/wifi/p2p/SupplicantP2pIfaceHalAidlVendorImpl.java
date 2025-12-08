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

package com.android.server.wifi.p2p;

import android.os.IBinder;
import android.os.IBinder.DeathRecipient;
import android.os.RemoteException;
import android.util.Log;

import com.android.server.wifi.WifiInjector;

/**
 * Implementation of Supplicant P2P Iface HAL using the vendor AIDL service.
 */
public class SupplicantP2pIfaceHalAidlVendorImpl extends SupplicantP2pIfaceHalAidlBase {
    private static final String TAG = "SupplicantP2pIfaceHalAidlVendorImpl";
    private final DeathRecipient mSupplicantDeathRecipient =
            () -> {
                Log.d(TAG, "ISupplicant/ISupplicantP2pIface died");
                synchronized (mLock) {
                    if (mWaitForDeathLatch != null) {
                        mWaitForDeathLatch.countDown();
                    }
                    supplicantServiceDiedHandler();
                }
            };

    public SupplicantP2pIfaceHalAidlVendorImpl(WifiP2pMonitor monitor, WifiInjector wifiInjector) {
        super(monitor, wifiInjector, false);
    }

    /**
     * Retrieve the ISupplicant service and link to service death.
     * @return true if successful, false otherwise
     */
    @Override
    public boolean initialize() {
        synchronized (mLock) {
            final String methodStr = "initialize";
            if (mISupplicant != null) {
                Log.i(TAG, "Service is already initialized.");
                return true;
            }
            mInitializationStarted = true;
            mISupplicantP2pIface = null;
            mISupplicant = getSupplicantMockable();
            if (mISupplicant == null) {
                Log.e(TAG, "Unable to obtain ISupplicant binder.");
                return false;
            }
            Log.i(TAG, "Obtained ISupplicant binder.");

            try {
                IBinder serviceBinder = getCurrentServiceBinderMockable();
                if (serviceBinder == null) {
                    return false;
                }
                serviceBinder.linkToDeath(mSupplicantDeathRecipient, /* flags= */  0);
                return true;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
                return false;
            }
        }
    }

    @Override
    protected IBinder getCurrentServiceBinderMockable() {
        synchronized (mLock) {
            if (mISupplicant == null) {
                return null;
            }
            return mISupplicant.asBinder();
        }
    }

    /**
     * Signals whether initialization started successfully.
     */
    @Override
    public boolean isInitializationStarted() {
        synchronized (mLock) {
            return mInitializationStarted;
        }
    }

    /**
     * Signals whether initialization completed successfully.
     */
    @Override
    public boolean isInitializationComplete() {
        synchronized (mLock) {
            return mISupplicant != null;
        }
    }
}
