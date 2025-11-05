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
import android.annotation.Nullable;
import android.net.wifi.WifiContext;
import android.os.IBinder;
import android.os.IBinder.DeathRecipient;
import android.os.RemoteException;
import android.os.ServiceSpecificException;
import android.system.wifi.mainline_supplicant.IMainlineSupplicant;
import android.util.Log;

import com.android.internal.annotations.VisibleForTesting;
import com.android.server.wifi.SupplicantStaIfaceHalAidlMainlineImpl;
import com.android.server.wifi.WifiInjector;
import com.android.server.wifi.mainline_supplicant.ServiceManagerWrapper;

/**
 * Implementation of Supplicant P2P Iface HAL using the mainline AIDL service.
 */
public class SupplicantP2pIfaceHalAidlMainlineImpl extends SupplicantP2pIfaceHalAidlBase {
    private static final String TAG = "SupplicantP2pIfaceHalAidlMainlineImpl";
    private static final String MAINLINE_SUPPLICANT_SERVICE_NAME = "wifi_mainline_supplicant";

    private final WifiContext mWifiContext;
    private IMainlineSupplicant mIMainlineSupplicant;
    private final boolean mIsServiceAvailable;
    private SupplicantDeathRecipient mSupplicantDeathRecipient;

    public SupplicantP2pIfaceHalAidlMainlineImpl(WifiP2pMonitor monitor, WifiInjector wifiInjector)
    {
        super(monitor, wifiInjector);
        mWifiContext = wifiInjector.getContext();
        mIsServiceAvailable = isServiceAvailableMockable(mWifiContext);
        mSupplicantDeathRecipient = new SupplicantDeathRecipient();
    }

    @Override
    protected @Nullable IBinder getCurrentServiceBinderMockable() {
        synchronized (mLock) {
            if (mIMainlineSupplicant == null) {
                return null;
            }
            return mIMainlineSupplicant.asBinder();
        }
    }

    @VisibleForTesting
    protected IMainlineSupplicant getNewServiceBinderMockable() {
        return IMainlineSupplicant.Stub.asInterface(
                ServiceManagerWrapper.waitForService(MAINLINE_SUPPLICANT_SERVICE_NAME));
    }

    private class SupplicantDeathRecipient implements DeathRecipient {
        @Override
        public void binderDied() {
            // This is deprecated.
        }

        @Override
        public void binderDied(@NonNull IBinder who) {
            synchronized (mLock) {
                IBinder supplicantBinder = getCurrentServiceBinderMockable();
                Log.w(TAG, "IMainlineSupplicant binder died. who=" + who + ", service="
                        + supplicantBinder);

                if (supplicantBinder == null) {
                    Log.w(TAG, "Mainline Supplicant Death EventHandler called"
                            + " when service is already cleared");
                } else if (supplicantBinder != who) {
                    Log.w(TAG, "Ignoring stale death recipient notification");
                    return;
                }
                if (mWaitForDeathLatch != null) {
                    // Latch indicates that this event was triggered by stopService
                    mWaitForDeathLatch.countDown();
                }
                Log.w(TAG, "Handle mainline supplicant death");
                supplicantServiceDiedHandler();
            }
        }
    }

    @Override
    protected void supplicantServiceDiedHandler() {
        synchronized (mLock) {
            super.supplicantServiceDiedHandler();
            // Clear the mainline supplicant reference when the service dies.
            mIMainlineSupplicant = null;
        }
    }

    @VisibleForTesting
    protected boolean isServiceAvailableMockable(WifiContext context) {
        return SupplicantStaIfaceHalAidlMainlineImpl.isServiceAvailable(context);
    }

    @Override
    public boolean initialize() {
        synchronized (mLock) {
            final String methodStr = "initialize";
            if (!mIsServiceAvailable) {
                Log.e(TAG, "Service cannot be accessed.");
                return false;
            }

            if (isInitializationComplete()) {
                Log.i(TAG, "Service is already initialized, skipping " + methodStr);
                return true;
            }
            mInitializationStarted = true;
            mISupplicantP2pIface = null;
            mIMainlineSupplicant = null;
            mISupplicant = null;

            mIMainlineSupplicant = getNewServiceBinderMockable();
            if (mIMainlineSupplicant == null) {
                Log.e(TAG, "Unable to retrieve binder from the ServiceManager.");
                return false;
            }

            try {
                mISupplicant = mIMainlineSupplicant.getVendorSupplicant();
                if (mISupplicant == null) {
                    Log.e(TAG, "Unable to obtain ISupplicant binder.");
                    mIMainlineSupplicant = null;
                    return false;
                }
                Log.i(TAG, "Obtained ISupplicant binder.");

                mWaitForDeathLatch = null;
                mIMainlineSupplicant.asBinder()
                        .linkToDeath(mSupplicantDeathRecipient, /* flags= */ 0);
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
                return false;
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
                return false;
            }
            Log.i(TAG, "Service was started successfully");
            return true;
        }
    }

    @Override
    public boolean isInitializationStarted() {
        synchronized (mLock) {
            return mIsServiceAvailable;
        }
    }

    @Override
    public boolean isInitializationComplete() {
        synchronized (mLock) {
            return mIMainlineSupplicant != null && mISupplicant != null;
        }
    }
}
