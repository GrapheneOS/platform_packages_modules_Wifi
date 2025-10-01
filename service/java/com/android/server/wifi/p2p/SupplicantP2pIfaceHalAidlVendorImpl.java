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

import android.annotation.NonNull;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.IBinder;
import android.os.IBinder.DeathRecipient;
import android.os.RemoteException;
import android.util.Log;

import com.android.server.wifi.WifiInjector;
import com.android.server.wifi.WifiNative;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Implementation of Supplicant P2P Iface HAL using the vendor AIDL service.
 */
public class SupplicantP2pIfaceHalAidlVendorImpl extends SupplicantP2pIfaceHalAidlBase {
    private static final String TAG = "SupplicantP2pIfaceHalAidlVendorImpl";
    private CountDownLatch mWaitForDeathLatch;
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
        super(monitor, wifiInjector);
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
                IBinder serviceBinder = getServiceBinderMockable();
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

    /**
     * Terminate the supplicant daemon & wait for its death.
     */
    @Override
    public void terminate() {
        synchronized (mLock) {
            final String methodStr = "terminate";
            if (!checkSupplicantAndLogFailure(methodStr)) {
                return;
            }
            Log.i(TAG, "Terminate supplicant service");
            try {
                mWaitForDeathLatch = new CountDownLatch(1);
                mISupplicant.terminate();
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            }
        }

        // Wait for death recipient to confirm the service death.
        try {
            if (!mWaitForDeathLatch.await(WAIT_FOR_DEATH_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                Log.w(TAG, "Timed out waiting for confirmation of supplicant death");
                supplicantServiceDiedHandler();
            } else {
                Log.d(TAG, "Got service death confirmation");
            }
        } catch (InterruptedException e) {
            Log.w(TAG, "Failed to wait for supplicant death");
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
