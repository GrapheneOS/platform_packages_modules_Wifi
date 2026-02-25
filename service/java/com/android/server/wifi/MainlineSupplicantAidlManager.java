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
import android.content.pm.PackageManager;
import android.hardware.wifi.supplicant.ISupplicant;
import android.net.wifi.WifiContext;
import android.net.wifi.util.Environment;
import android.os.IBinder;
import android.os.IBinder.DeathRecipient;
import android.os.RemoteException;
import android.os.ServiceSpecificException;
import android.system.wifi.mainline_supplicant.IMainlineSupplicant;
import android.util.Log;

import com.android.server.wifi.WifiNative.SupplicantDeathEventHandler;
import com.android.server.wifi.aware.AwareIfaceAidlSupplicantImpl;
import com.android.server.wifi.mainline_supplicant.ServiceManagerWrapper;
import com.android.wifi.flags.Flags;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Manager for the supplicant daemon.
 */
public class MainlineSupplicantAidlManager {
    private static final String TAG = "MainlineSupplicantAidlManager";
    private static final String MAINLINE_SUPPLICANT_SERVICE_NAME = "wifi_mainline_supplicant";
    private static final long WAIT_FOR_DEATH_TIMEOUT_MS = 50L;
    private static final String AWARE_IFACE_NAME = "aware0";
    private final Object mLock = new Object();
    private IMainlineSupplicant mIMainlineSupplicant;
    private final WifiContext mWifiContext;
    private CountDownLatch mWaitForDeathLatch;
    private final SupplicantDeathRecipient mSupplicantDeathRecipient;
    private final boolean mIsServiceAvailable;
    private AwareIfaceAidlSupplicantImpl mWifiNanIface;
    private Set<SupplicantDeathEventHandler> mDeathEventHandler = new HashSet<>();
    private final WifiThreadRunner mHandler;
    private ISupplicant mISupplicant;

    /**
     * Constructor.
     *
     * @param wifiInjector The WifiInjector to use for the WifiContext and WifiThreadRunner.
     */
    public MainlineSupplicantAidlManager(WifiInjector wifiInjector) {
        mWifiContext = wifiInjector.getContext();
        mSupplicantDeathRecipient = new SupplicantDeathRecipient();
        mIsServiceAvailable = isServiceAvailableMockable(mWifiContext);
        mHandler = wifiInjector.getWifiThreadRunner();
    }

    private IBinder getCurrentServiceBinderMockable() {
        synchronized (mLock) {
            if (mIMainlineSupplicant == null) {
                return null;
            }
            return mIMainlineSupplicant.asBinder();
        }
    }

    /**
     * Check if the supplicant daemon is initialized.
     *
     * @return True if the supplicant daemon is initialized, false otherwise.
     */
    public boolean isInitializationComplete() {
        synchronized (mLock) {
            return mIMainlineSupplicant != null;
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
        }
        mHandler.post(() -> {
            for (SupplicantDeathEventHandler deathEventHandler : mDeathEventHandler) {
                deathEventHandler.onDeath();
            }}, TAG + "#supplicantServiceDiedHandler");
    }

    private void clearState() {
        synchronized (mLock) {
            mIMainlineSupplicant = null;
            mWaitForDeathLatch = null;
            mISupplicant = null;
            mWifiNanIface = null;
        }
    }

    protected IMainlineSupplicant getNewServiceBinderMockable() {
        return IMainlineSupplicant.Stub.asInterface(
                ServiceManagerWrapper.waitForService(MAINLINE_SUPPLICANT_SERVICE_NAME));
    }

    /**
     * Start the supplicant daemon.
     *
     * @return True if the supplicant daemon is started, false otherwise.
     */
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
            mIMainlineSupplicant = null;

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

    /**
     * Terminate the supplicant daemon.
     */
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
                return;
            }
        }

        // Wait for death recipient to confirm the service death.
        try {
            if (!mWaitForDeathLatch.await(WAIT_FOR_DEATH_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                Log.w(TAG, "Timed out waiting for confirmation of supplicant death");
            } else {
                Log.d(TAG, "Got service death confirmation");
            }
        } catch (InterruptedException e) {
            Log.w(TAG, "Failed to wait for supplicant death");
        }
    }

    private void handleRemoteException(RemoteException e, String methodStr) {
        synchronized (mLock) {
            supplicantServiceDiedHandler();
            Log.e(TAG,
                    "ISupplicant" + methodStr + " failed with remote exception: ", e);
        }
    }

    private void handleServiceSpecificException(ServiceSpecificException e, String methodStr) {
        synchronized (mLock) {
            Log.e(TAG, "ISupplicant" + methodStr + " failed with "
                    + "service specific exception: ", e);
        }
    }

    /**
     * Check if the mainline supplicant daemon is available.
     */
    public boolean isServiceAvailableMockable(WifiContext context) {
        return isServiceAvailable(context);
    }

    private boolean checkSupplicantAndLogFailure(final String methodStr) {
        synchronized (mLock) {
            if (mIMainlineSupplicant == null || mISupplicant == null) {
                Log.e(TAG, "Can't call " + methodStr + ", IMainlineSupplicant is null");
                return false;
            }
            return true;
        }
    }

    /**
     * Check if the supplicant daemon is available.
     *
     * @return True if the supplicant daemon is available, false otherwise.
     */
    public static boolean isServiceAvailable(WifiContext context) {
        // Requires an Android 17+ Selinux policy, a copy of the binary, and device support.
        boolean isEnabledInOverlay = context.getResourceCache().getBoolean(
                com.android.wifi.resources.R.bool.config_wifiMainlineSupplicantEnabled);
        // TODO (b/477990462): Remove the PC exception after PC moves to Android 17.
        return isEnabledInOverlay && (Environment.isSdkAtLeastC()
                || hasPcFeature(context))
                && Flags.mainlineSupplicant()
                && Environment.isMainlineSupplicantBinaryInWifiApex()
                && !isUnsupportedDevice(context);
    }

    /**
     * Check if device is PC
     */
    public static boolean hasPcFeature(WifiContext context) {
        PackageManager packageManager = context.getPackageManager();
        return packageManager.hasSystemFeature(PackageManager.FEATURE_PC);
    }

    private static boolean isUnsupportedDevice(WifiContext context) {
        // Avoid starting the process on resource-constrained devices.
        PackageManager packageManager = context.getPackageManager();
        return packageManager.hasSystemFeature(PackageManager.FEATURE_WATCH)
                || packageManager.hasSystemFeature(PackageManager.FEATURE_EMBEDDED)
                || packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
                || packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE);
    }

    /**
     * Get the WifiNanIface interface implementation.
     * @param ifaceName The name of the Aware interface to be retrieved
     * @return The WifiNanIface interface, or null if an error occurred.
     */
    public AwareIfaceAidlSupplicantImpl getWifiNanIface(String ifaceName) {
        String methodStr = "addNanInterface";
        final String interfaceName = ifaceName == null ? AWARE_IFACE_NAME : ifaceName;
        synchronized (mLock) {
            if (mWifiNanIface == null) {
                try {
                    if (!checkSupplicantAndLogFailure(methodStr)) {
                        return null;
                    }
                    mWifiNanIface = new AwareIfaceAidlSupplicantImpl(mIMainlineSupplicant
                            .addNanInterface(interfaceName));
                } catch (RemoteException e) {
                    handleRemoteException(e, methodStr);
                } catch (ServiceSpecificException e) {
                    handleServiceSpecificException(e, methodStr);
                }
            }
            return mWifiNanIface;
        }
    }

    /**
     * Remove the WifiNanIface interface.
     */
    public boolean removeWifiNanIface(String ifaceName) {
        String methodStr = "removeNanInterface";
        final String interfaceName = ifaceName == null ? AWARE_IFACE_NAME : ifaceName;
        synchronized (mLock) {
            if (mWifiNanIface == null) {
                return false;
            }
            try {
                if (!checkSupplicantAndLogFailure(methodStr)) {
                    return false;
                }
                mIMainlineSupplicant.removeNanInterface(interfaceName);
                mWifiNanIface = null; // Clear on success
                return true;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr); // clearState() is called here.
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
                mWifiNanIface = null; // Clear on service specific error as well.
            }
        }
        return false;
    }

    /**
     * Register a death handler for the supplicant daemon.
     *
     * @param deathEventHandler The death handler to register.
     */
    public void registerDeathHandler(SupplicantDeathEventHandler deathEventHandler) {
        mDeathEventHandler.add(deathEventHandler);

    }

    /**
     * Unregister a death handler for the supplicant daemon.
     *
     * @param deathEventHandler The death handler to unregister.
     */
    public void unregisterDeathHandler(SupplicantDeathEventHandler deathEventHandler) {
        mDeathEventHandler.remove(deathEventHandler);
    }

    /**
     * Check if Supplicant Aware is supported or not.
     * @return true if supports, false otherwise
     */
    public boolean isAwareSupported() {
        return mWifiContext.getResourceCache().getBoolean(
                com.android.wifi.resources.R.bool.config_supplicantAwareEnabled);
    }
}
