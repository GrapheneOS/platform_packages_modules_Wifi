/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.server.wifi.aware;

import android.annotation.NonNull;
import android.os.Handler;
import android.os.WorkSource;
import android.util.Log;

import com.android.internal.annotations.VisibleForTesting;
import com.android.server.wifi.HalDeviceManager;
import com.android.server.wifi.MainlineSupplicantAidlManager;
import com.android.server.wifi.WifiInjector;
import com.android.server.wifi.WifiNative;
import com.android.server.wifi.hal.WifiNanIface;
import com.android.wifi.flags.FeatureFlags;

import java.io.FileDescriptor;
import java.io.PrintWriter;

/**
 * Manages the interface to the Wi-Fi Aware HAL.
 */
public class WifiAwareNativeManager {
    private static final String TAG = "WifiAwareNativeManager";
    private boolean mVerboseLoggingEnabled = false;

    private final WifiAwareStateManager mWifiAwareStateManager;
    private final HalDeviceManager mHalDeviceManager;
    private final WifiNative mWifiNative;
    private final MainlineSupplicantAidlManager mMainlineSupplicant;
    private Handler mHandler;
    private final WifiAwareNativeCallback mWifiAwareNativeCallback;
    private final FeatureFlags mFeatureFlags;
    private WifiNanIface mVendorHalNanIface = null;
    private WifiNative.Iface mWifiNativeNanIface;
    private AwareIfaceAidlSupplicantImpl mSupplicantNanIface;
    private InterfaceDestroyedListener mInterfaceDestroyedListener;
    private final SupplicantDeathHandler mSupplicantDeathHandler = new SupplicantDeathHandler();
    private int mReferenceCount = 0;

    WifiAwareNativeManager(WifiAwareStateManager awareStateManager,
            HalDeviceManager halDeviceManager,
            WifiAwareNativeCallback wifiAwareNativeCallback,
            WifiNative wifiNative,
            FeatureFlags featureFlags,
            WifiInjector wifiInjector) {
        mWifiAwareStateManager = awareStateManager;
        mHalDeviceManager = halDeviceManager;
        mWifiNative = wifiNative;
        mFeatureFlags = featureFlags;
        mWifiAwareNativeCallback = wifiAwareNativeCallback;
        mMainlineSupplicant = wifiInjector.getMainlineSupplicantAidlManager();
    }

    /**
     * Enable/Disable verbose logging.
     */
    public void enableVerboseLogging(boolean verboseEnabled, boolean halVerboseEnabled) {
        mVerboseLoggingEnabled = verboseEnabled;
        if (mVendorHalNanIface != null) {
            mVendorHalNanIface.enableVerboseLogging(halVerboseEnabled);
        }
        if (mSupplicantNanIface != null) {
            mSupplicantNanIface.enableVerboseLogging(halVerboseEnabled);
        }
    }

    /**
     * Get the NAN interface.
     */
    public AwareIfaceAidlSupplicantImpl getSupplicantNanIface() {
        return mSupplicantNanIface;
    }

    /**
     * Initialize the class - intended for late initialization.
     *
     * @param handler Handler on which to execute interface available callbacks.
     */
    public void start(Handler handler) {
        mHandler = handler;
        mHalDeviceManager.initialize();
        mHalDeviceManager.registerStatusListener(
                new HalDeviceManager.ManagerStatusListener() {
                    @Override
                    public void onStatusChanged() {
                        if (mVerboseLoggingEnabled) Log.v(TAG, "onStatusChanged");
                        // only care about isStarted (Wi-Fi started) not isReady - since if not
                        // ready then Wi-Fi will also be down.
                        if (mHalDeviceManager.isStarted()) {
                            mWifiAwareStateManager.tryToGetAwareCapability();
                        } else {
                            awareIsDown(mWifiAwareStateManager.isD2dAllowedWhenStaDisabled());
                        }
                    }
                }, mHandler);
        if (mHalDeviceManager.isStarted()) {
            mWifiAwareStateManager.tryToGetAwareCapability();
        }
    }

    /**
     * Returns the WifiNanIface through which commands to the NAN HAL are dispatched.
     * Return may be null if not initialized/available.
     */
    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PACKAGE)
    public WifiNanIface getWifiNanIface() {
        return mVendorHalNanIface;
    }

    /**
     * Attempt to obtain the HAL NAN interface.
     */
    public void tryToGetAware(@NonNull WorkSource requestorWs) {
        if (mVerboseLoggingEnabled) {
            Log.d(TAG, "tryToGetAware: mWifiNanIface=" + mVendorHalNanIface
                    + ", mReferenceCount=" + mReferenceCount + ", requestorWs=" + requestorWs);
        }

        if (mVendorHalNanIface != null) {
            mReferenceCount++;
            return;
        }
        if (mHalDeviceManager == null) {
            Log.e(TAG, "tryToGetAware: mHalDeviceManager is null!?");
            awareIsDown(mWifiAwareStateManager.isD2dAllowedWhenStaDisabled());
            return;
        }

        //TODO(448421897): check the supplicant capability
        boolean useSupplicant = mFeatureFlags.wifiAwareSupplicantSolution()
                && mMainlineSupplicant.isAwareSupported();

        mInterfaceDestroyedListener = new InterfaceDestroyedListener();
        mWifiNativeNanIface = mWifiNative.createNanIface(mInterfaceDestroyedListener,
                mHandler, requestorWs, useSupplicant);
        if (mWifiNativeNanIface != null) {
            mVendorHalNanIface = (WifiNanIface) mWifiNativeNanIface.iface;
        }
        if (mVendorHalNanIface == null) {
            Log.e(TAG, "Was not able to obtain a WifiNanIface (even though enabled!?)");
            awareIsDown(true);
            return;
        }
        if (mVerboseLoggingEnabled) Log.v(TAG, "Obtained a WifiNanIface");
        if (useSupplicant) {
            mMainlineSupplicant.registerDeathHandler(mSupplicantDeathHandler);
            if (!mMainlineSupplicant.isInitializationComplete()) {
                if (!mMainlineSupplicant.startDaemon()) {
                    Log.e(TAG, "Unable to start the supplicant daemon");
                    mHalDeviceManager.removeIface(mVendorHalNanIface);
                    awareIsDown(true);
                    return;
                }
            }
            mSupplicantNanIface = mMainlineSupplicant.getWifiNanIface(mWifiNativeNanIface.name);
            if (mSupplicantNanIface == null) {
                Log.e(TAG, "Unable to get WifiNanIface from the supplicant daemon");
                mHalDeviceManager.removeIface(mVendorHalNanIface);
                awareIsDown(true);
                return;
            }
            if (!mSupplicantNanIface.registerFrameworkCallback(mWifiAwareNativeCallback)) {
                Log.e(TAG, "Unable to register callback with WifiNanIface");
                mSupplicantNanIface = null;
                mHalDeviceManager.removeIface(mVendorHalNanIface);
                awareIsDown(true);
                return;
            }
            mSupplicantNanIface.enableVerboseLogging(mVerboseLoggingEnabled);
        } else if (!mVendorHalNanIface.registerFrameworkCallback(mWifiAwareNativeCallback)) {
            Log.e(TAG, "Unable to register callback with WifiNanIface");
            mHalDeviceManager.removeIface(mVendorHalNanIface);
            awareIsDown(mWifiAwareStateManager.isD2dAllowedWhenStaDisabled());
            return;
        }
        mReferenceCount = 1;
        mVendorHalNanIface.enableVerboseLogging(mVerboseLoggingEnabled);
    }

    /**
     * Release the HAL NAN interface.
     */
    public void releaseAware() {
        if (mVerboseLoggingEnabled) {
            Log.d(TAG, "releaseAware: mWifiNanIface=" + mVendorHalNanIface + ", mReferenceCount="
                    + mReferenceCount);
        }

        if (mVendorHalNanIface == null) {
            return;
        }
        if (mHalDeviceManager == null) {
            Log.e(TAG, "releaseAware: mHalDeviceManager is null!?");
            return;
        }

        mReferenceCount--;
        if (mReferenceCount != 0) {
            return;
        }
        if (mSupplicantNanIface != null) {
            mMainlineSupplicant.removeWifiNanIface();
            mSupplicantNanIface = null;
        }
        mInterfaceDestroyedListener.active = false;
        mInterfaceDestroyedListener = null;
        mHalDeviceManager.removeIface(mVendorHalNanIface);
        if (mWifiNativeNanIface != null) {
            final int nanIfaceId = mWifiNativeNanIface.id;
                // HAL may be stop when Nan is toredown,
                // clean mNanIface first to avoid infinite loop in clean up
            mWifiNativeNanIface = null;
            mWifiNative.teardownNanIface(nanIfaceId);
        }
        mVendorHalNanIface = null;
        mWifiAwareNativeCallback.resetChannelInfo();
    }

    /**
     * Replace requestorWs in-place when iface is already enabled.
     */
    public boolean replaceRequestorWs(@NonNull WorkSource requestorWs) {
        if (mVerboseLoggingEnabled) {
            Log.d(TAG, "replaceRequestorWs: mWifiNanIface=" + mVendorHalNanIface
                    + ", mReferenceCount=" + mReferenceCount + ", requestorWs=" + requestorWs);
        }

        if (mVendorHalNanIface == null) {
            return false;
        }
        if (mHalDeviceManager == null) {
            Log.e(TAG, "tryToGetAware: mHalDeviceManager is null!?");
            awareIsDown(mWifiAwareStateManager.isD2dAllowedWhenStaDisabled());
            return false;
        }

        return mHalDeviceManager.replaceRequestorWsForNanIface(mVendorHalNanIface, requestorWs);
    }

    private void awareIsDown(boolean markAsAvailable) {
        if (mVerboseLoggingEnabled) {
            Log.d(TAG, "awareIsDown: mWifiNanIface=" + mVendorHalNanIface
                    + ", mReferenceCount =" + mReferenceCount);
        }
        if (mWifiNativeNanIface != null) {
            final int nanIfaceId = mWifiNativeNanIface.id;
            // HAL may be stop when Nan is toredown,
            // clean mNanIface first to avoid infinite loop in clean up
            mWifiNativeNanIface = null;
            mWifiNative.teardownNanIface(nanIfaceId);
        }
        if (mSupplicantNanIface != null) {
            mMainlineSupplicant.removeWifiNanIface();
            mSupplicantNanIface = null;
        }
        mMainlineSupplicant.unregisterDeathHandler(mSupplicantDeathHandler);
        mVendorHalNanIface = null;
        mReferenceCount = 0;
        mWifiAwareStateManager.disableUsage(markAsAvailable);
    }

    private class InterfaceDestroyedListener implements
            HalDeviceManager.InterfaceDestroyedListener {
        public boolean active = true;

        @Override
        public void onDestroyed(@NonNull String ifaceName) {
            if (mVerboseLoggingEnabled) {
                Log.d(TAG, "Interface was destroyed: mWifiNanIface=" + mVendorHalNanIface
                        + ", active=" + active);
            }
            if (active && mVendorHalNanIface != null) {
                awareIsDown(true);
            } // else: we released it locally so no need to disable usage
        }
    }

    private class SupplicantDeathHandler implements WifiNative.SupplicantDeathEventHandler {
        @Override
        public void onDeath() {
            if (mVerboseLoggingEnabled) {
                Log.d(TAG, "Supplicant death handler called");
            }
            if (mSupplicantNanIface != null) {
                mHalDeviceManager.removeIface(mVendorHalNanIface);
                awareIsDown(mWifiAwareStateManager.isD2dAllowedWhenStaDisabled());
            }
        }
    }

    /**
     * Dump the internal state of the class.
     */
    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println("WifiAwareNativeManager:");
        pw.println("  mWifiNanIface: " + mVendorHalNanIface);
        pw.println("  mReferenceCount: " + mReferenceCount);
        mWifiAwareNativeCallback.dump(fd, pw, args);
    }
}
