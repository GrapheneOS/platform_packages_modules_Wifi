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

package com.android.server.wifi;

import android.annotation.NonNull;
import android.content.Context;
import android.hardware.wifi.supplicant.ISupplicant;
import android.os.Handler;
import android.os.IBinder;
import android.os.IBinder.DeathRecipient;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.util.Log;

import com.android.internal.annotations.VisibleForTesting;
import com.android.modules.utils.build.SdkLevel;

import java.io.PrintWriter;

/**
 * Implementation of Supplicant STA Iface HAL using the vendor AIDL service.
 */
public class SupplicantStaIfaceHalAidlVendorImpl extends SupplicantStaIfaceHalAidlBase {
    private static final String TAG = "SupplicantStaIfaceHalAidlVendorImpl";
    private static final String HAL_INSTANCE_NAME = ISupplicant.DESCRIPTOR + "/default";

    private SupplicantDeathRecipient mSupplicantDeathRecipient;
    private boolean mServiceDeclared = false;

    public SupplicantStaIfaceHalAidlVendorImpl(Context context, WifiMonitor monitor,
            Handler handler, Clock clock, WifiMetrics wifiMetrics, WifiGlobals wifiGlobals,
            @NonNull SsidTranslator ssidTranslator, WifiInjector wifiInjector) {
        super(context, monitor, handler, clock, wifiMetrics, wifiGlobals, ssidTranslator,
                wifiInjector);
        mSupplicantDeathRecipient = new SupplicantDeathRecipient();
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

    private class SupplicantDeathRecipient implements DeathRecipient {
        @Override
        public void binderDied() {
            // This is deprecated.
        }

        @Override
        public void binderDied(@NonNull IBinder who) {
            synchronized (mLock) {
                IBinder supplicantBinder = getCurrentServiceBinderMockable();
                Log.w(TAG, "ISupplicant binder died. who=" + who + ", service="
                        + supplicantBinder);
                if (supplicantBinder == null) {
                    Log.w(TAG, "Supplicant Death EventHandler called"
                            + " when ISupplicant/binder service is already cleared");
                } else if (supplicantBinder != who) {
                    Log.w(TAG, "Ignoring stale death recipient notification");
                    return;
                }
                if (mWaitForDeathLatch != null) {
                    mWaitForDeathLatch.countDown();
                }
                Log.w(TAG, "Handle supplicant death");
                supplicantServiceDiedHandler();
            }
        }
    }

    private void supplicantServiceDiedHandler() {
        synchronized (mLock) {
            clearState();
            if (mDeathEventHandler != null) {
                mDeathEventHandler.onDeath();
            }
        }
    }

    @Override
    public boolean initialize() {
        synchronized (mLock) {
            if (mISupplicant != null) {
                Log.i(TAG, "Service is already initialized, skipping initialize method");
                return true;
            }
            if (mVerboseLoggingEnabled) {
                Log.i(TAG, "Checking for ISupplicant service.");
            }
            mISupplicantStaIfaces.clear();
            mServiceDeclared = serviceDeclared();
            return mServiceDeclared;
        }
    }

    public static boolean serviceDeclared() {
        // Service Manager API ServiceManager#isDeclared supported after T.
        if (!SdkLevel.isAtLeastT()) {
            return false;
        }
        return ServiceManager.isDeclared(HAL_INSTANCE_NAME);
    }

    @Override
    public boolean startDaemon() {
        synchronized (mLock) {
            final String methodStr = "startDaemon";
            if (mISupplicant != null) {
                Log.i(TAG, "Service is already initialized, skipping " + methodStr);
                return true;
            }

            clearState();
            mISupplicant = getSupplicantMockable();
            if (mISupplicant == null) {
                Log.e(TAG, "Unable to obtain ISupplicant binder.");
                return false;
            }
            Log.i(TAG, "Obtained ISupplicant binder.");
            Log.i(TAG, "Local Version: " + ISupplicant.VERSION);

            try {
                getServiceVersion();
                Log.i(TAG, "Remote Version: " + mServiceVersion);
                IBinder serviceBinder = getCurrentServiceBinderMockable();
                if (serviceBinder == null) {
                    return false;
                }
                mWaitForDeathLatch = null;
                serviceBinder.linkToDeath(mSupplicantDeathRecipient, /* flags= */ 0);
                setLogLevel(mVerboseHalLoggingEnabled);
                registerNonStandardCertCallback();
                return true;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
                return false;
            }
        }
    }

    /**
     * Wrapper function to access HAL objects, created to be mockable in unit tests
     */
    @VisibleForTesting
    protected ISupplicant getSupplicantMockable() {
        synchronized (mLock) {
            try {
                if (SdkLevel.isAtLeastT()) {
                    return ISupplicant.Stub.asInterface(
                            ServiceManager.waitForDeclaredService(HAL_INSTANCE_NAME));
                } else {
                    return null;
                }
            } catch (Exception e) {
                Log.e(TAG, "Unable to get ISupplicant service, " + e);
                return null;
            }
        }
    }

    private void getServiceVersion() throws RemoteException {
        synchronized (mLock) {
            if (mISupplicant == null) return;
            if (mServiceVersion == -1) {
                int serviceVersion = mISupplicant.getInterfaceVersion();
                mWifiInjector.getSettingsConfigStore().put(
                        WifiSettingsConfigStore.SUPPLICANT_HAL_AIDL_SERVICE_VERSION,
                        serviceVersion);
                mServiceVersion = serviceVersion;
                Log.i(TAG, "Remote service version was cached");
            }
        }
    }

    @Override
    public boolean isInitializationStarted() {
        synchronized (mLock) {
            return mServiceDeclared;
        }
    }

    @Override
    public boolean isInitializationComplete() {
        synchronized (mLock) {
            return mISupplicant != null;
        }
    }

    /**
     * Dump information about the internal state
     *
     * @param pw PrintWriter to write the dump to
     */
    public void dump(PrintWriter pw) {
        pw.println("Dump of " + TAG);
        pw.println("Local Version: " + ISupplicant.VERSION);
        pw.println("Service Version: " + mServiceVersion);
        super.dump(pw);
    }
}
