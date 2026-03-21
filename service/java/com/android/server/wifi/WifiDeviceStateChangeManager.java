/*
 * Copyright (C) 2023 The Android Open Source Project
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

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.util.Environment;
import android.os.PowerManager;
import android.security.Flags;
import android.security.advancedprotection.AdvancedProtectionManager;
import android.text.TextUtils;
import android.util.ArraySet;

import com.android.internal.annotations.VisibleForTesting;
import com.android.modules.utils.HandlerExecutor;
import com.android.wifi.flags.FeatureFlags;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Set;

/** A centralized manager to handle all the device state changes */
public class WifiDeviceStateChangeManager {
    // check screen state change every 1 hour if no updates had been received
    public static final long SCREEN_STATE_CHANGE_ACTIVE_QUERY_INTERVAL_MS = 60 * 60 * 1000;
    private static final String TAG = "WifiDeviceStateChangeManager";
    private final WifiThreadRunner mWifiThreadRunner;
    private final Context mContext;

    private final PowerManager mPowerManager;
    private final WifiInjector mWifiInjector;
    private final Clock mClock;
    private AdvancedProtectionManager mAdvancedProtectionManager;
    private FeatureFlags mFeatureFlags;

    private final Set<StateChangeCallback> mChangeCallbackList = new ArraySet<>();
    private boolean mIsWifiServiceStarted = false;
    private boolean mLastScreenOn;
    private String mLastActiveScreenStateQuery = "";
    /**
     * Callback to receive the device state change event. Caller should implement the method to
     * listen to the interested event
     */
    public interface StateChangeCallback {
        /**
         * Called when the screen state changes
         *
         * @param screenOn true for ON, false otherwise
         */
        default void onScreenStateChanged(boolean screenOn) {}

        /**
         * Called when the Advanced protection mode state changes
         *
         * @param apmOn true for ON, false otherwise
         */
        default void onAdvancedProtectionModeStateChanged(boolean apmOn) {}
    }

    /** Create the instance of WifiDeviceStateChangeManager. */
    public WifiDeviceStateChangeManager(Context context, WifiThreadRunner threadRunner,
            WifiInjector wifiInjector) {
        mWifiThreadRunner = threadRunner;
        mContext = context;
        mWifiInjector = wifiInjector;
        mPowerManager = mContext.getSystemService(PowerManager.class);
        mClock = wifiInjector.getClock();
    }

    @VisibleForTesting
    public Runnable mQueryScreenStateIfNeededRunnable = new Runnable() {
        @Override
        public void run() {
            boolean screenOn = mPowerManager.isInteractive();
            mLastActiveScreenStateQuery = LocalDateTime.now(ZoneId.systemDefault())
                    + " - " + "screenOn=" + screenOn;
            if (screenOn != mLastScreenOn) {
                // If screen state changed then trigger the registered callbacks
                handleScreenStateChanged(screenOn);
                return;
            }
            // Otherwise, just schedule the next query
            maybeScheduleDelayedScreenStateQuery(screenOn);
        }
    };

    /** Handle the boot completed event. Start to register the receiver and callback. */
    @SuppressLint("NewApi")
    public void handleBootCompleted() {
        mFeatureFlags = mWifiInjector.getDeviceConfigFacade().getFeatureFlags();
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);

        BroadcastReceiver screenChangedReceiver =
                new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context, Intent intent) {
                        String action = intent.getAction();
                        if (TextUtils.equals(action, Intent.ACTION_SCREEN_ON)
                                || TextUtils.equals(action, Intent.ACTION_SCREEN_OFF)) {
                            mWifiThreadRunner.post(() ->
                                    handleScreenStateChanged(TextUtils.equals(action,
                                            Intent.ACTION_SCREEN_ON)), TAG + "SCREEN_CHANGE");
                        }
                    }
                };
        if (mFeatureFlags.monitorIntentForAllUsers() && Environment.isSdkAtLeastC()) {
            mContext.registerReceiverForAllUsers(screenChangedReceiver, filter,
                    null, null);
        } else {
            mContext.registerReceiver(screenChangedReceiver, filter);
        }
        handleScreenStateChanged(mPowerManager.isInteractive());
        if (Environment.isSdkAtLeastB() && isAapmApiFlagEnabled()) {
            mAdvancedProtectionManager =
                    mContext.getSystemService(AdvancedProtectionManager.class);
            if (mAdvancedProtectionManager != null) {
                mAdvancedProtectionManager.registerAdvancedProtectionCallback(
                        new HandlerExecutor(mWifiThreadRunner.getHandler()),
                        state -> {
                            handleAdvancedProtectionModeStateChanged(state);
                        });
                handleAdvancedProtectionModeStateChanged(
                        mAdvancedProtectionManager.isAdvancedProtectionEnabled());
            } else {
                handleAdvancedProtectionModeStateChanged(false);
            }
        } else {
            handleAdvancedProtectionModeStateChanged(false);
        }
        mIsWifiServiceStarted = true;
    }

    @VisibleForTesting
    public boolean isAapmApiFlagEnabled() {
        return Flags.aapmApi();
    }

    /**
     * Checks if advanced protection is enabled on the device.
     *
     * @return {@code true} if advanced protection is enabled, {@code false} otherwise.
     */
    @SuppressLint("NewApi")
    public boolean isAapmEnabled() {
        if (Environment.isSdkAtLeastB() && mAdvancedProtectionManager != null) {
            return mAdvancedProtectionManager.isAdvancedProtectionEnabled();
        }
        return false;
    }
    /**
     * Register a state change callback. When the state is changed, caller with receive the callback
     * event
     */
    @SuppressLint("NewApi")
    public void registerStateChangeCallback(StateChangeCallback callback) {
        mChangeCallbackList.add(callback);
        if (!mIsWifiServiceStarted) return;
        callback.onScreenStateChanged(mPowerManager.isInteractive());
        if (Environment.isSdkAtLeastB() && mAdvancedProtectionManager != null) {
            callback.onAdvancedProtectionModeStateChanged(
                    mAdvancedProtectionManager.isAdvancedProtectionEnabled());
        } else {
            callback.onAdvancedProtectionModeStateChanged(false);
        }
    }

    /**
     * Unregister a state change callback when caller is not interested the state change anymore.
     */
    public void unregisterStateChangeCallback(StateChangeCallback callback) {
        mChangeCallbackList.remove(callback);
    }

    /**
     * Dump the local logs.
     */
    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println("Dump of WifiDeviceStateChangeManager");
        pw.println("WifiDeviceStateChangeManager - Log Begin ----");
        pw.println("mLastActiveScreenStateQuery: " + mLastActiveScreenStateQuery);
        pw.println("mLastScreenOn: " + mLastScreenOn);
        pw.println("WifiDeviceStateChangeManager - Log End ----");
    }

    private void maybeScheduleDelayedScreenStateQuery(boolean screenOn) {
        mWifiThreadRunner.removeCallbacks(mQueryScreenStateIfNeededRunnable);
        if (screenOn) {
            mWifiThreadRunner.postDelayed(mQueryScreenStateIfNeededRunnable,
                    SCREEN_STATE_CHANGE_ACTIVE_QUERY_INTERVAL_MS,
                    TAG + "#queryScreenStateIfNeeded", mQueryScreenStateIfNeededRunnable);
        }
    }

    private void handleScreenStateChanged(boolean screenOn) {
        mLastScreenOn = screenOn;
        for (StateChangeCallback callback : mChangeCallbackList) {
            callback.onScreenStateChanged(screenOn);
        }
        maybeScheduleDelayedScreenStateQuery(screenOn);
    }

    private void handleAdvancedProtectionModeStateChanged(boolean apmOn) {
        for (StateChangeCallback callback : mChangeCallbackList) {
            callback.onAdvancedProtectionModeStateChanged(apmOn);
        }
    }
}
