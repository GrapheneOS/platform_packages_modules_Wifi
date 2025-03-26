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

package com.google.snippet.wifi;

import static android.net.wifi.DeauthenticationReasonCode.REASON_UNKNOWN;

import android.content.Context;
import android.net.wifi.SoftApConfiguration;
import android.net.wifi.SoftApInfo;
import android.net.wifi.WifiClient;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.PollingCheck;
import com.android.compatibility.common.util.ShellIdentityUtils;
import com.android.wifi.flags.Flags;

import com.google.android.mobly.snippet.Snippet;
import com.google.android.mobly.snippet.event.EventCache;
import com.google.android.mobly.snippet.event.SnippetEvent;
import com.google.android.mobly.snippet.rpc.AsyncRpc;
import com.google.android.mobly.snippet.rpc.Rpc;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;
import java.util.concurrent.TimeUnit;

/** Snippet class for WifiManager. */
public class WifiManagerSnippet implements Snippet {

    private static final String TAG = "WifiManagerSnippet";
    private static final long POLLING_TIMEOUT_MS = TimeUnit.SECONDS.toMillis(10);

    private final WifiManager mWifiManager;
    private final Handler mHandler;
    private final Object mLock = new Object();

    private WifiManagerSnippet.SnippetSoftApCallback mSoftApCallback;
    private WifiManager.LocalOnlyHotspotReservation mLocalOnlyHotspotReservation;

    /** Callback to listen in and verify events to SoftAp. */
    private static class SnippetSoftApCallback implements WifiManager.SoftApCallback {
        private final String mCallbackId;

        SnippetSoftApCallback(String callbackId) {
            mCallbackId = callbackId;
        }

        @Override
        public void onConnectedClientsChanged(@NonNull SoftApInfo info,
                @NonNull List<WifiClient> clients) {
            Log.d(TAG, "onConnectedClientsChanged, info=" + info + ", clients=" + clients);
            SnippetEvent event = new SnippetEvent(mCallbackId, "onConnectedClientsChanged");
            event.getData().putInt("connectedClientsCount", clients.size());
            String macAddress = null;
            if (!clients.isEmpty()) {
                // In our Mobly test cases, there is only ever one other device.
                WifiClient client = clients.getFirst();
                macAddress = client.getMacAddress().toString();
            }
            event.getData().putString("clientMacAddress", macAddress);
            EventCache.getInstance().postEvent(event);
        }

        @Override
        public void onClientsDisconnected(@NonNull SoftApInfo info,
                @NonNull List<WifiClient> clients) {
            Log.d(TAG, "onClientsDisconnected, info=" + info + ", clients=" + clients);
            SnippetEvent event = new SnippetEvent(mCallbackId, "onClientsDisconnected");
            event.getData().putInt("disconnectedClientsCount", clients.size());
            String macAddress = null;
            int disconnectReason = REASON_UNKNOWN;
            if (!clients.isEmpty()) {
                // In our Mobly test cases, there is only ever one other device.
                WifiClient client = clients.getFirst();
                macAddress = client.getMacAddress().toString();
                disconnectReason = client.getDisconnectReason();
            }
            event.getData().putString("clientMacAddress", macAddress);
            event.getData().putInt("clientDisconnectReason", disconnectReason);
            EventCache.getInstance().postEvent(event);
        }
    }

    /** Callback class to get the results of local hotspot start. */
    private class SnippetLocalOnlyHotspotCallback extends WifiManager.LocalOnlyHotspotCallback {
        private final String mCallbackId;

        SnippetLocalOnlyHotspotCallback(String callbackId) {
            mCallbackId = callbackId;
        }

        @Override
        public void onStarted(WifiManager.LocalOnlyHotspotReservation reservation) {
            Log.d(TAG, "Local-only hotspot onStarted");
            synchronized (mLock) {
                mLocalOnlyHotspotReservation = reservation;
            }
            SoftApConfiguration currentConfiguration = reservation.getSoftApConfiguration();
            SnippetEvent event = new SnippetEvent(mCallbackId, "onStarted");
            event.getData().putString("ssid",
                    WifiJsonConverter.trimQuotationMarks(
                            currentConfiguration.getWifiSsid().toString()));
            event.getData()
                    .putString(
                            "passphrase",
                            currentConfiguration.getPassphrase());
            EventCache.getInstance().postEvent(event);
        }
    }

    public WifiManagerSnippet() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        mWifiManager = context.getSystemService(WifiManager.class);
        HandlerThread handlerThread = new HandlerThread(getClass().getSimpleName());
        handlerThread.start();
        mHandler = new Handler(handlerThread.getLooper());
    }

    /**
     * Starts local-only hotspot.
     *
     * @param callbackId A unique identifier assigned automatically by Mobly.
     */
    @AsyncRpc(description = "Call to start local-only hotspot.")
    public void wifiStartLocalOnlyHotspot(String callbackId) {
        mWifiManager.startLocalOnlyHotspot(new SnippetLocalOnlyHotspotCallback(callbackId),
                mHandler);
    }

    /**
     * Stop local-only hotspot.
     */
    @Rpc(description = "Call to stop local-only hotspot.")
    public void wifiStopLocalOnlyHotspot() {
        synchronized (mLock) {
            if (mLocalOnlyHotspotReservation == null) {
                Log.w(TAG, "Requested to stop local-only hotspot which was already stopped.");
                return;
            }

            mLocalOnlyHotspotReservation.close();
            mLocalOnlyHotspotReservation = null;
        }
    }

    /**
     * Registers a callback for Soft AP.
     *
     * @param callbackId A unique identifier assigned automatically by Mobly.
     */
    @AsyncRpc(description = "Call to register SoftApCallback.")
    public void wifiRegisterSoftApCallback(String callbackId) {
        if (mSoftApCallback == null) {
            mSoftApCallback = new SnippetSoftApCallback(callbackId);
            mWifiManager.registerSoftApCallback(mHandler::post, mSoftApCallback);
        }
    }


    /**
     * Registers a callback for local-only hotspot.
     *
     * @param callbackId A unique identifier assigned automatically by Mobly.
     */
    @AsyncRpc(description = "Call to register SoftApCallback for local-only hotspot.")
    public void wifiRegisterLocalOnlyHotspotSoftApCallback(String callbackId) {
        if (mSoftApCallback == null) {
            mSoftApCallback = new SnippetSoftApCallback(callbackId);
            mWifiManager.registerLocalOnlyHotspotSoftApCallback(mHandler::post,
                    mSoftApCallback);
        }
    }

    /**
     * Checks if the device supports portable hotspot.
     *
     * @return {@code true} if the device supports portable hotspot, {@code false} otherwise.
     */
    @Rpc(description = "Check if the device supports portable hotspot.")
    public boolean wifiIsPortableHotspotSupported() {
        return mWifiManager.isPortableHotspotSupported();
    }

    /**
     * Unregisters soft AP callback function.
     */
    @Rpc(description = "Unregister soft AP callback function.")
    public void wifiUnregisterSoftApCallback() {
        if (mSoftApCallback == null) {
            return;
        }

        mWifiManager.unregisterSoftApCallback(mSoftApCallback);
        mSoftApCallback = null;
    }

    /**
     * Unregisters soft AP callback function.
     */
    @Rpc(description = "Unregister soft AP callback function.")
    public void wifiUnregisterLocalOnlyHotspotSoftApCallback() {
        if (mSoftApCallback == null) {
            return;
        }

        mWifiManager.unregisterLocalOnlyHotspotSoftApCallback(mSoftApCallback);
        mSoftApCallback = null;
    }

    /**
     * Enables all saved networks.
     */
    @Rpc(description = "Enable all saved networks.")
    public void wifiEnableAllSavedNetworks() {
        for (WifiConfiguration savedNetwork : mWifiManager.getConfiguredNetworks()) {
            mWifiManager.enableNetwork(savedNetwork.networkId, false);
        }
    }

    /**
     * Disables all saved networks.
     */
    @Rpc(description = "Disable all saved networks.")
    public void wifiDisableAllSavedNetworks() {
        for (WifiConfiguration savedNetwork : mWifiManager.getConfiguredNetworks()) {
            mWifiManager.disableNetwork(savedNetwork.networkId);
        }
    }

    /**
     * Checks the softap_disconnect_reason flag.
     *
     * @return {@code true} if the softap_disconnect_reason flag is enabled, {@code false}
     * otherwise.
     */
    @Rpc(description = "Checks SoftApDisconnectReason flag.")
    public boolean wifiCheckSoftApDisconnectReasonFlag() {
        return Flags.softapDisconnectReason();
    }

    /**
     * Gets the Wi-Fi tethered AP Configuration.
     *
     * @return AP details in {@link SoftApConfiguration} as JSON format.
     */
    @Rpc(description = "Get current SoftApConfiguration.")
    public JSONObject wifiGetSoftApConfiguration() throws JSONException {
        return WifiJsonConverter.serialize(mWifiManager.getSoftApConfiguration());
    }

    /**
     * Waits for tethering to be disabled.
     *
     * @return {@code true} if tethering is disabled within the timeout, {@code false} otherwise.
     */
    @Rpc(description = "Call to wait for tethering to be disabled.")
    public boolean wifiWaitForTetheringDisabled() {
        try {
            PollingCheck.check("Tethering NOT disabled", POLLING_TIMEOUT_MS,
                    () -> !mWifiManager.isWifiApEnabled());
        } catch (Exception e) {
            return false;
        }
        return true;
    }

    /**
     * Enable/Disable the Auto join global.
     */
    @Rpc(description = "Call to enable/disable auto join global")
    public void wifiAllowAutojoinGlobal(boolean enable) {
        ShellIdentityUtils.invokeWithShellPermissions(
                () -> mWifiManager.allowAutojoinGlobal(enable));
    }
}
