/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.server.wifi.mainline_supplicant;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.net.wifi.util.Environment;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.ServiceSpecificException;
import android.system.wifi.mainline_supplicant.IMainlineSupplicant;
import android.system.wifi.mainline_supplicant.IStaInterface;
import android.system.wifi.mainline_supplicant.IStaInterfaceCallback;
import android.util.Log;

import com.android.internal.annotations.VisibleForTesting;
import com.android.server.wifi.WifiNative;
import com.android.server.wifi.WifiThreadRunner;
import com.android.wifi.flags.Flags;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Allows us to bring up, tear down, and make calls into the mainline supplicant process.
 * <p>
 * The mainline supplicant is a separate wpa_supplicant binary stored in the Wifi mainline module,
 * which provides specific functionalities such as USD.
 */
public class MainlineSupplicant {
    private static final String TAG = "MainlineSupplicant";
    private static final String MAINLINE_SUPPLICANT_SERVICE_NAME = "wifi_mainline_supplicant";
    private static final long WAIT_FOR_DEATH_TIMEOUT_MS = 50L;

    private IMainlineSupplicant mIMainlineSupplicant;
    private final Object mLock = new Object();
    private final WifiThreadRunner mWifiThreadRunner;
    private SupplicantDeathRecipient mServiceDeathRecipient;
    private WifiNative.SupplicantDeathEventHandler mFrameworkDeathHandler;
    private CountDownLatch mWaitForDeathLatch;
    private final boolean mIsServiceAvailable;
    private Map<String, IStaInterface> mActiveStaIfaces = new HashMap<>();
    private Map<String, IStaInterfaceCallback> mStaIfaceCallbacks = new HashMap<>();

    public MainlineSupplicant(@NonNull WifiThreadRunner wifiThreadRunner) {
        mWifiThreadRunner = wifiThreadRunner;
        mServiceDeathRecipient = new SupplicantDeathRecipient();
        mIsServiceAvailable = canServiceBeAccessed();
    }

    @VisibleForTesting
    protected IMainlineSupplicant getNewServiceBinderMockable() {
        return IMainlineSupplicant.Stub.asInterface(
                ServiceManagerWrapper.waitForService(MAINLINE_SUPPLICANT_SERVICE_NAME));
    }

    private @Nullable IBinder getCurrentServiceBinder() {
        synchronized (mLock) {
            if (mIMainlineSupplicant == null) {
                return null;
            }
            return mIMainlineSupplicant.asBinder();
        }
    }

    private class SupplicantDeathRecipient implements IBinder.DeathRecipient {
        @Override
        public void binderDied() {
        }

        @Override
        public void binderDied(@NonNull IBinder who) {
            synchronized (mLock) {
                IBinder currentBinder = getCurrentServiceBinder();
                Log.i(TAG, "Death notification received. who=" + who
                        + ", currentBinder=" + currentBinder);
                if (currentBinder == null || currentBinder != who) {
                    Log.i(TAG, "Ignoring stale death notification");
                    return;
                }
                if (mWaitForDeathLatch != null) {
                    // Latch indicates that this event was triggered by stopService
                    mWaitForDeathLatch.countDown();
                }
                clearState();
                if (mFrameworkDeathHandler != null) {
                    mFrameworkDeathHandler.onDeath();
                }
                Log.i(TAG, "Service death was handled successfully");
            }
        }
    }

    /**
     * Check whether the mainline supplicant service can be accessed.
     */
    private boolean canServiceBeAccessed() {
        // Requires an Android B+ Selinux policy and a copy of the binary.
        return Environment.isSdkAtLeastB() && Flags.mainlineSupplicant()
                && Environment.isMainlineSupplicantBinaryInWifiApex();
    }

    /**
     * Returns true if the mainline supplicant service is available on this device.
     */
    public boolean isAvailable() {
        return mIsServiceAvailable;
    }

    /**
     * Reset the internal state for this instance.
     */
    private void clearState() {
        synchronized (mLock) {
            mIMainlineSupplicant = null;
            mActiveStaIfaces.clear();
            mStaIfaceCallbacks.clear();
        }
    }

    /**
     * Start the mainline supplicant process.
     *
     * @return true if the process was started, false otherwise.
     */
    public boolean startService() {
        synchronized (mLock) {
            if (!Environment.isSdkAtLeastB()) {
                Log.e(TAG, "Service is not available before Android B");
                return false;
            }
            if (mIMainlineSupplicant != null) {
                Log.i(TAG, "Service has already been started");
                return true;
            }

            mIMainlineSupplicant = getNewServiceBinderMockable();
            if (mIMainlineSupplicant == null) {
                Log.e(TAG, "Unable to retrieve binder from the ServiceManager");
                return false;
            }

            try {
                mWaitForDeathLatch = null;
                mIMainlineSupplicant.asBinder()
                        .linkToDeath(mServiceDeathRecipient, /* flags= */  0);
            } catch (RemoteException e) {
                handleRemoteException(e, "startService");
                return false;
            }

            Log.i(TAG, "Service was started successfully");
            return true;
        }
    }

    /**
     * Check whether this instance is active.
     */
    public boolean isActive() {
        synchronized (mLock) {
            return mIMainlineSupplicant != null;
        }
    }

    /**
     * Set up a STA interface with the specified iface name.
     *
     * @param ifaceName Name of the interface.
     * @return true on success, false otherwise.
     */
    public boolean addStaInterface(@NonNull String ifaceName) {
        synchronized (mLock) {
            final String methodName = "addStaInterface";
            if (!checkIsActiveAndLogError(methodName)) {
                return false;
            }
            if (ifaceName == null) {
                return false;
            }
            if (mActiveStaIfaces.containsKey(ifaceName)) {
                Log.i(TAG, "STA interface " + ifaceName + " already exists");
                return true;
            }

            try {
                IStaInterface staIface = mIMainlineSupplicant.addStaInterface(ifaceName);
                IStaInterfaceCallback callback = new MainlineSupplicantStaIfaceCallback(
                        this, ifaceName, mWifiThreadRunner);
                if (!registerStaIfaceCallback(staIface, callback)) {
                    Log.i(TAG, "Unable to register callback with interface " + ifaceName);
                    return false;
                }
                mActiveStaIfaces.put(ifaceName, staIface);
                // Keep callback in a store to avoid recycling by the garbage collector
                mStaIfaceCallbacks.put(ifaceName, callback);
                Log.i(TAG, "Added STA interface " + ifaceName);
                return true;
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodName);
            } catch (RemoteException e) {
                handleRemoteException(e, methodName);
            }
            return false;
        }
    }

    /**
     * Tear down the STA interface with the specified iface name.
     *
     * @param ifaceName Name of the interface.
     * @return true on success, false otherwise.
     */
    public boolean removeStaInterface(@NonNull String ifaceName) {
        synchronized (mLock) {
            final String methodName = "removeStaInterface";
            if (!checkIsActiveAndLogError(methodName)) {
                return false;
            }
            if (ifaceName == null) {
                return false;
            }
            if (!mActiveStaIfaces.containsKey(ifaceName)) {
                Log.i(TAG, "STA interface " + ifaceName + " does not exist");
                return false;
            }

            try {
                mIMainlineSupplicant.removeStaInterface(ifaceName);
                mActiveStaIfaces.remove(ifaceName);
                mStaIfaceCallbacks.remove(ifaceName);
                Log.i(TAG, "Removed STA interface " + ifaceName);
                return true;
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodName);
            } catch (RemoteException e) {
                handleRemoteException(e, methodName);
            }
            return false;
        }
    }

    /**
     * Register a callback with the provided STA interface.
     *
     * @return true if the registration was successful, false otherwise.
     */
    private boolean registerStaIfaceCallback(@NonNull IStaInterface iface,
            @NonNull IStaInterfaceCallback callback) {
        synchronized (mLock) {
            final String methodName = "registerStaIfaceCallback";
            if (iface == null || callback == null) {
                return false;
            }
            try {
                iface.registerCallback(callback);
                return true;
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodName);
            } catch (RemoteException e) {
                handleRemoteException(e, methodName);
            }
            return false;
        }
    }

    /**
     * Stop the mainline supplicant process.
     */
    public void stopService() {
        synchronized (mLock) {
            if (mIMainlineSupplicant == null) {
                Log.i(TAG, "Service has already been stopped");
                return;
            }
            try {
                Log.i(TAG, "Attempting to stop the service");
                mWaitForDeathLatch = new CountDownLatch(1);
                mIMainlineSupplicant.terminate();
            } catch (RemoteException e) {
                handleRemoteException(e, "stopService");
                return;
            }
        }

        // Wait for latch to confirm the service death
        try {
            if (mWaitForDeathLatch.await(WAIT_FOR_DEATH_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                Log.i(TAG, "Service death confirmation was received");
            } else {
                Log.e(TAG, "Timed out waiting for confirmation of service death");
            }
        } catch (InterruptedException e) {
            Log.e(TAG, "Failed to wait for service death");
        }
    }

    /**
     * Register a WifiNative death handler to receive service death notifications.
     */
    public void registerFrameworkDeathHandler(
            @NonNull WifiNative.SupplicantDeathEventHandler deathHandler) {
        if (deathHandler == null) {
            Log.e(TAG, "Attempted to register a null death handler");
            return;
        }
        synchronized (mLock) {
            if (mFrameworkDeathHandler != null) {
                Log.i(TAG, "Replacing the existing death handler");
            }
            mFrameworkDeathHandler = deathHandler;
        }
    }

    /**
     * Unregister an existing WifiNative death handler, for instance to avoid receiving a
     * death notification during a solicited terminate.
     */
    public void unregisterFrameworkDeathHandler() {
        synchronized (mLock) {
            if (mFrameworkDeathHandler == null) {
                Log.e(TAG, "Framework death handler has already been unregistered");
                return;
            }
            mFrameworkDeathHandler = null;
        }
    }

    private void handleServiceSpecificException(ServiceSpecificException e, String methodName) {
        Log.e(TAG, methodName + " encountered ServiceSpecificException " + e);
    }

    private boolean checkIsActiveAndLogError(String methodName) {
        if (!isActive()) {
            Log.e(TAG, "Unable to call " + methodName + " since the instance is not active");
            return false;
        }
        return true;
    }

    private void handleRemoteException(RemoteException e, String methodName) {
        synchronized (mLock) {
            Log.e(TAG, methodName + " encountered RemoteException " + e);
            clearState();
        }
    }
}
