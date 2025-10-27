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
import android.annotation.Nullable;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.wifi.util.BuildProperties;
import android.net.wifi.util.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.IBinder.DeathRecipient;
import android.os.RemoteException;
import android.os.ServiceSpecificException;
import android.system.wifi.mainline_supplicant.IMainlineSupplicant;
import android.util.Log;

import com.android.internal.annotations.VisibleForTesting;
import com.android.server.wifi.mainline_supplicant.ServiceManagerWrapper;
import com.android.wifi.flags.Flags;

/**
 * Implementation of Supplicant STA Iface HAL using the mainline AIDL service.
 */
public class SupplicantStaIfaceHalAidlMainlineImpl extends SupplicantStaIfaceHalAidlBase {
    private static final String TAG = "SupplicantStaIfaceHalAidlMainlineImpl";
    private static final String MAINLINE_SUPPLICANT_SERVICE_NAME = "wifi_mainline_supplicant";

    private IMainlineSupplicant mIMainlineSupplicant;
    private final boolean mIsServiceAvailable;
    private SupplicantDeathRecipient mSupplicantDeathRecipient;

    public SupplicantStaIfaceHalAidlMainlineImpl(Context context, WifiMonitor monitor,
            Handler handler, Clock clock, WifiMetrics wifiMetrics, WifiGlobals wifiGlobals,
            @NonNull SsidTranslator ssidTranslator, WifiInjector wifiInjector) {
        super(context, monitor, handler, clock, wifiMetrics, wifiGlobals, ssidTranslator,
                wifiInjector);
        mSupplicantDeathRecipient = new SupplicantDeathRecipient();
        mIsServiceAvailable = isServiceAvailableMockable(context);
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

    private void supplicantServiceDiedHandler() {
        synchronized (mLock) {
            clearState();
            if (mDeathEventHandler != null) {
                mDeathEventHandler.onDeath();
            }
        }
    }

    @Override
    protected void clearState() {
        synchronized (mLock) {
            super.clearState();
            mIMainlineSupplicant = null;
        }
    }

    @Override
    public boolean initialize() {
        synchronized (mLock) {
            if (isInitializationComplete()) {
                Log.i(TAG, "Service is already initialized, skipping initialize method");
                return true;
            }
            if (mVerboseLoggingEnabled) {
                Log.i(TAG, "Checking for IMainlineSupplicant service.");
            }
            mISupplicantStaIfaces.clear();
            return mIsServiceAvailable;
        }
    }

    @VisibleForTesting
    protected boolean isServiceAvailableMockable(Context context) {
        return isServiceAvailable(context);
    }

    /**
     * Check whether the mainline supplicant service can be accessed.
     */
    public static boolean isServiceAvailable(Context context) {
        // Requires an Android 17+ Selinux policy, a copy of the binary, and device support.
        boolean isEnabledInOverlay = context.getResources().getBoolean(
                        com.android.wifi.resources.R.bool.config_wifiMainlineSupplicantEnabled);
        // TODO (b/421247744): Remove the user build check once ready to deploy to user devices.
        BuildProperties buildProperties = BuildProperties.getInstance();
        // TODO (b/421247744): Change the SDK check so that this only runs on Android 17+.
        return isEnabledInOverlay && Environment.isSdkAtLeastB() && Flags.mainlineSupplicant()
                && Environment.isMainlineSupplicantBinaryInWifiApex()
                && !isUnsupportedDevice(context) && !buildProperties.isUserBuild();
    }

    private static boolean isUnsupportedDevice(Context context) {
        // Avoid starting the process on resource-constrained devices.
        PackageManager packageManager = context.getPackageManager();
        return packageManager.hasSystemFeature(PackageManager.FEATURE_WATCH)
                        || packageManager.hasSystemFeature(PackageManager.FEATURE_EMBEDDED)
                        || packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
                        || packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE);
    }

    @Override
    public boolean startDaemon() {
        synchronized (mLock) {
            final String methodStr = "startDaemon";
            if (!mIsServiceAvailable) {
                Log.e(TAG, "Service cannot be accessed.");
                return false;
            }
            if (isInitializationComplete()) {
                Log.i(TAG, "Service is already initialized, skipping " + methodStr);
                return true;
            }

            clearState();
            mIMainlineSupplicant = getNewServiceBinderMockable();
            if (mIMainlineSupplicant == null) {
                Log.e(TAG, "Unable to retrieve binder from the ServiceManager");
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
                        .linkToDeath(mSupplicantDeathRecipient, /* flags= */  0);
                setLogLevel(mVerboseHalLoggingEnabled);
                registerNonStandardCertCallback();

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
