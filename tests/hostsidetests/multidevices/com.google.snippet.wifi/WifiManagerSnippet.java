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

import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.stream.Collectors.joining;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.NetworkInfo.DetailedState;
import android.net.wifi.ScanResult;
import android.net.wifi.SoftApCapability;
import android.net.wifi.SoftApConfiguration;
import android.net.wifi.SoftApInfo;
import android.net.wifi.SupplicantState;
import android.net.wifi.WifiAvailableChannel;
import android.net.wifi.WifiClient;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiNetworkSuggestion;
import android.net.wifi.WifiScanner;
import android.net.wifi.WifiScanner.ScanData;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.PollingCheck;
import com.android.compatibility.common.util.ShellIdentityUtils;
import com.android.wifi.flags.Flags;

import com.google.android.mobly.snippet.Snippet;
import com.google.android.mobly.snippet.bundled.utils.JsonDeserializer;
import com.google.android.mobly.snippet.bundled.utils.Utils;
import com.google.android.mobly.snippet.event.EventCache;
import com.google.android.mobly.snippet.event.SnippetEvent;
import com.google.android.mobly.snippet.rpc.AsyncRpc;
import com.google.android.mobly.snippet.rpc.Rpc;
import com.google.snippet.wifi.aware.WifiAwareJsonDeserializer;
import com.google.snippet.wifi.aware.WifiAwareSnippetConverter;
import com.google.snippet.wifi.softap.WifiSapJsonDeserializer;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * Snippet class for WifiManager.
 */
public class WifiManagerSnippet extends WifiShellPermissionSnippet implements Snippet {

    private static final String TAG = "WifiManagerSnippet";
    private static final long POLLING_TIMEOUT_MS = TimeUnit.SECONDS.toMillis(10);
    private static final int CONNECT_TIMEOUT_IN_SEC = 90;
    private static final int TOGGLE_STATE_TIMEOUT_IN_SEC = 30;
    // Network Suggestions
    private static final String EVENT_KEY_CB_NAME = "CallbackName";
    private static final String EVENT_KEY_CONNECTION_STATUS = "ConnectionStatus";
    private static final String EVENT_KEY_USER_APPROVAL_STATUS = "UserApprovalStatus";
    private static final String EVENT_KEY_SUGGESTION = "Suggestion";

    private final Context mContext;
    private final WifiManager mWifiManager;
    private final ConnectivityManager mConnectivityManager;
    private final Handler mHandler;
    private final Object mLock = new Object();
    private WifiManagerSnippet.SnippetSoftApCallback mSoftApCallback;
    private WifiManager.LocalOnlyHotspotReservation mLocalOnlyHotspotReservation;
    private BroadcastReceiver mWifiStateReceiver;
    private WifiManager.SuggestionConnectionStatusListener mSuggestionConnectionStatusListener;
    private WifiManager.SuggestionUserApprovalStatusListener mSuggestionUserApprovalStatusListener;
    private BroadcastReceiver mNetworkSuggestionPostConnectionReceiver;
    private volatile boolean mIsScanResultAvailable = false;


    /**
     * Callback to listen in and verify events to SoftAp.
     */
    private static class SnippetSoftApCallback implements WifiManager.SoftApCallback {
        private final String mCallbackId;
        private int mConnectedClientsCount = 0;
        private final List<SoftApInfo> mAllSoftApInfoList = new ArrayList<>();
        private final List<Integer> mStateList = new ArrayList<>();

        SnippetSoftApCallback(String callbackId) {
            mCallbackId = callbackId;
        }

        @Override
        public void onConnectedClientsChanged(@NonNull List<WifiClient> clients) {
            Log.d(TAG, "onConnectedClientsChanged, clients=" + clients);
            SnippetEvent event = new SnippetEvent(mCallbackId, "onConnectedClientsChanged");
            mConnectedClientsCount = clients.size();
            event.getData().putInt("connectedClientsCount", mConnectedClientsCount);
            String macAddress = null;
            if (!clients.isEmpty()) {
                // In our Mobly test cases, there is only ever one other device.
                WifiClient client = clients.get(0);
                macAddress = client.getMacAddress().toString();
            }
            event.getData().putString("clientMacAddress", macAddress);
            EventCache.getInstance().postEvent(event);
        }

        @Override
        public void onConnectedClientsChanged(@NonNull SoftApInfo info,
                @NonNull List<WifiClient> clients) {
            Log.d(TAG, "onConnectedClientsChanged, info=" + info + ", clients=" + clients);
            SnippetEvent event = new SnippetEvent(mCallbackId, "onConnectedClientsChanged");
            mConnectedClientsCount = clients.size();
            event.getData().putInt("connectedClientsCount", mConnectedClientsCount);
            String macAddress = null;
            if (!clients.isEmpty()) {
                // In our Mobly test cases, there is only ever one other device.
                WifiClient client = clients.get(0);
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
                WifiClient client = clients.get(0);
                macAddress = client.getMacAddress().toString();
                disconnectReason = client.getDisconnectReason();
            }
            event.getData().putString("clientMacAddress", macAddress);
            event.getData().putInt("clientDisconnectReason", disconnectReason);
            EventCache.getInstance().postEvent(event);
        }

        @Override
        public void onStateChanged(int state, int failureReason) {
            SnippetEvent event = new SnippetEvent(mCallbackId, "onStateChanged");
            Log.d(TAG,
                    "SoftApCallback - onStateChanged, state "
                    + state + ", failureReason " + failureReason);
            mStateList.add(state);
            Bundle msg = new Bundle();
            msg.putInt("State", state);
            msg.putInt("FailureReason", failureReason);
            event.getData().putBundle("onStateChanged", msg);
            EventCache.getInstance().postEvent(event);
        }

        @Override
        public void onInfoChanged(List<SoftApInfo> softApInfoList) {
            SnippetEvent event = new SnippetEvent(mCallbackId, "onInfoChanged");
            String softApInfoString = softApInfoList.stream().map(
                    SoftApInfo::toString).collect(joining(", "));
            Pattern frequencyPattern = Pattern.compile("frequency=\\s*(\\d+)");
            Pattern bandwidthPattern = Pattern.compile("bandwidth=\\s*(\\d+)");
            Pattern macAddressPattern = Pattern.compile("mMldAddress\\s*=\\s*([0-9a-fA-F:]{17})");
            Matcher frequencyMatcher = frequencyPattern.matcher(softApInfoString);
            Matcher bandwidthMatcher = bandwidthPattern.matcher(softApInfoString);
            Matcher macAddresshMatcher = macAddressPattern.matcher(softApInfoString);
            int frequency = 0;
            int bandwidth = 0;
            String macAddress = null;
            if (frequencyMatcher.find()) {
                frequency = Integer.parseInt(frequencyMatcher.group(1));
            }
            if (bandwidthMatcher.find()) {
                bandwidth = Integer.parseInt(bandwidthMatcher.group(1));
            }
            if (macAddresshMatcher.find()) {
                macAddress = macAddresshMatcher.group(1);
            }
            this.mAllSoftApInfoList.clear();
            this.mAllSoftApInfoList.addAll(softApInfoList);

            Log.d(TAG,
                    "SoftApCallback - onInfoChanged, size "
                    + this.mAllSoftApInfoList.size()
                    + ", info "
                    + softApInfoList.stream().map(SoftApInfo::toString).collect(joining(", ")));
            event.getData().putInt("frequency", frequency);
            event.getData().putInt("bandwidth", bandwidth);
            event.getData().putString("clientMacAddress", macAddress);
            event.getData().putInt("connectedClientsCount", this.mAllSoftApInfoList.size());
            EventCache.getInstance().postEvent(event);
        }

        @Override
        public void onCapabilityChanged(SoftApCapability softApCapability) {
            SnippetEvent event = new SnippetEvent(mCallbackId, "OnCapabilityChanged");
            event.getData().putBoolean("clientForceDisconnectSupported",
                    softApCapability.areFeaturesSupported(
                        softApCapability.SOFTAP_FEATURE_CLIENT_FORCE_DISCONNECT));
            Log.d(TAG,
                    "SoftApCallback - onCapabilityChanged, softApCapability " + softApCapability);
            event.getData().putInt("MaximumSupportedClientNumber",
                    softApCapability.getMaxSupportedClients());
            event.getData().putIntArray("SupportedChannelListIn24g",
                    softApCapability.getSupportedChannelList(SoftApConfiguration.BAND_2GHZ));
            event.getData().putIntArray("SupportedChannelListIn5g",
                    softApCapability.getSupportedChannelList(SoftApConfiguration.BAND_5GHZ));
            event.getData().putIntArray("SupportedChannelListIn6g",
                    softApCapability.getSupportedChannelList(SoftApConfiguration.BAND_6GHZ));
            event.getData().putIntArray("SupportedChannelListIn60g",
                    softApCapability.getSupportedChannelList(SoftApConfiguration.BAND_60GHZ));
            EventCache.getInstance().postEvent(event);
        }

        @Override
        public void onBlockedClientConnecting(WifiClient client, int blockedReason) {
            SnippetEvent event = new SnippetEvent(mCallbackId, "OnBlockedClientConnecting");

            Log.d(TAG,
                    "SoftApCallback - onBlockedClientConnecting, client "
                    + client
                    + ", blockedReason "
                    + blockedReason);
            event.getData().putString("WifiClient", client.getMacAddress().toString());
            event.getData().putInt("BlockedReason", blockedReason);
            EventCache.getInstance().postEvent(event);
        }

        /**
        * Gets the number of connected clients.
        *
        * @return the number of connected clients
        */
        public int getConnectedClientsCount() {
            return this.mConnectedClientsCount;
        }
    }

    /**
   * Gets the number of connected clients.
   *
   * @throws WifiManagerSnippetException if softApCallback is not registered
   * @return the number of connected clients
   */
    @Rpc(description = "Get the number of connected clients.")
    public int wifiGetSoftApConnectedClientsCount() throws WifiManagerSnippetException {
        if (mSoftApCallback == null) {
            throw new WifiManagerSnippetException("SoftApCallback is not registered.", null);
        }
        return mSoftApCallback.getConnectedClientsCount();
    }

    /**
     * Callback class to get the results of local hotspot start.
     */
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
            event.getData().putString(
                    "passphrase",
                    currentConfiguration.getPassphrase());
            EventCache.getInstance().postEvent(event);
        }
    }

    /**
     * Listener for Wi-Fi Network Suggestion connection status updates.
     */
    private class SuggestionConnectionStatusListener implements
            WifiManager.SuggestionConnectionStatusListener{
        private final String mCallbackId;

        SuggestionConnectionStatusListener(String callbackId) {
            mCallbackId = callbackId;
        }

        @Override
        public void onConnectionStatus(@NonNull WifiNetworkSuggestion suggestion, int status) {
            Log.d(TAG, "onConnectionStatus, suggestion=" + suggestion.getSsid() + ", status="
                    + status);
            SnippetEvent event = new SnippetEvent(mCallbackId, "onConnectionStatus");
            event.getData().putInt(EVENT_KEY_CONNECTION_STATUS, status);
            try {
                event.getData().putString(EVENT_KEY_SUGGESTION,
                        WifiJsonConverter.serializeWifiNetworkSuggestion(suggestion).toString());
            } catch (JSONException e) {
                Log.e(TAG, "Failed to serialize WifiNetworkSuggestion", e);
            }
            EventCache.getInstance().postEvent(event);
        }
    }

    /**
     * Listener for Wi-Fi Network Suggestion user approval status updates.
     */
    private class SuggestionUserApprovalStatusListener implements
            WifiManager.SuggestionUserApprovalStatusListener {
        private final String mCallbackId;
        SuggestionUserApprovalStatusListener(String callbackId) {
            mCallbackId = callbackId;
        }

        @Override
        public void onUserApprovalStatusChange(int approvalStatus) {
            Log.d(TAG, "onUserApprovalStatusChange: approvalStatus=" + approvalStatus);
            SnippetEvent event = new SnippetEvent(mCallbackId, "onUserApprovalStatusChange");
            event.getData().putInt(EVENT_KEY_USER_APPROVAL_STATUS, approvalStatus);
            EventCache.getInstance().postEvent(event);
        }
    }

    /**
     * BroadcastReceiver for ACTION_WIFI_NETWORK_SUGGESTION_POST_CONNECTION.
     */
    private class NetworkSuggestionPostConnectionReceiver extends BroadcastReceiver {
        private final String mCallbackId;
        private final EventCache mEventCache = EventCache.getInstance();

        NetworkSuggestionPostConnectionReceiver(String callbackId) {
            this.mCallbackId = callbackId;
        }

        @Override
        public void onReceive(Context ctx, Intent intent) {
            String action = intent.getAction();
            if (WifiManager.ACTION_WIFI_NETWORK_SUGGESTION_POST_CONNECTION.equals(action)) {
                Log.d(TAG, "Received ACTION_WIFI_NETWORK_SUGGESTION_POST_CONNECTION broadcast.");
                SnippetEvent event = new SnippetEvent(mCallbackId, action);
                NetworkInfo networkInfo = intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO);

                if (networkInfo != null) {
                    event.getData().putString("networkInfoDetailedState",
                            networkInfo.getDetailedState().name());
                    event.getData().putString("networkInfoState", networkInfo.getState().name());
                }
                mEventCache.postEvent(event);
            }
        }
    }

    public WifiManagerSnippet() {
        mContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        mWifiManager = mContext.getSystemService(WifiManager.class);
        mConnectivityManager = mContext.getSystemService(ConnectivityManager.class);
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
     * Adds a listener for WifiNetworkSuggestion connection status.
     *
     * @param callbackId A unique identifier assigned automatically by Mobly.
     */
    @AsyncRpc(description = "Adds a listener for WifiNetworkSuggestion connection status.")
    public void wifiAddSuggestionConnectionStatusListener(String callbackId) {
        if (mSuggestionConnectionStatusListener != null) {
            throw new RuntimeException("Another SuggestionConnectionStatusListener is added. "
                    + "Only one listener is supported.");
        }
        mSuggestionConnectionStatusListener =
                new SuggestionConnectionStatusListener(callbackId);
        mWifiManager.addSuggestionConnectionStatusListener(Executors.newSingleThreadExecutor(),
                mSuggestionConnectionStatusListener);
    }

    /**
     * Removes the WifiNetworkSuggestion connection status listener.
     */
    @Rpc(description = "Removes the WifiNetworkSuggestion connection status listener.")
    public void wifiRemoveSuggestionConnectionStatusListener() {
        if (mSuggestionConnectionStatusListener == null) {
            Log.d(TAG, "No active SuggestionConnectionStatusListener.");
            return;
        }
        mWifiManager.removeSuggestionConnectionStatusListener(
                mSuggestionConnectionStatusListener);
        mSuggestionConnectionStatusListener = null;
        Log.d(TAG, "Removed SuggestionConnectionStatusListener.");
    }

    /**
     * Adds a listener for WifiNetworkSuggestion user approval status.
     *
     * @param callbackId A unique identifier assigned automatically by Mobly.
     */
    @AsyncRpc(description = "Adds a listener for WifiNetworkSuggestion user approval status.")
    public void wifiAddSuggestionUserApprovalStatusListener(String callbackId) {
        if (mSuggestionUserApprovalStatusListener != null) {
            throw new RuntimeException("Another SuggestionUserApprovalStatusListener is added. "
                    + "Only one listener is supported.");
        }
        mSuggestionUserApprovalStatusListener =
                new SuggestionUserApprovalStatusListener(callbackId);
        mWifiManager.addSuggestionUserApprovalStatusListener(
                Executors.newSingleThreadExecutor(), mSuggestionUserApprovalStatusListener);

    }

    /**
     * Removes the WifiNetworkSuggestion user approval status listener.
     */
    @Rpc(description = "Removes the WifiNetworkSuggestion user approval status listener.")
    public void wifiRemoveSuggestionUserApprovalStatusListener() {
        if (mSuggestionUserApprovalStatusListener == null) {
            Log.d(TAG, "No active SuggestionUserApprovalStatusListener.");
            return;
        }
        mWifiManager.removeSuggestionUserApprovalStatusListener(
                mSuggestionUserApprovalStatusListener);
        mSuggestionUserApprovalStatusListener = null;
        Log.d(TAG, "Removed SuggestionUserApprovalStatusListener.");
    }

    /**
     * Adds a BroadcastReceiver for ACTION_WIFI_NETWORK_SUGGESTION_POST_CONNECTION.
     *
     * @param callbackId A unique identifier assigned automatically by Mobly.
     */
    @AsyncRpc(description = "Adds a BroadcastReceiver for "
            + "ACTION_WIFI_NETWORK_SUGGESTION_POST_CONNECTION.")
    public void wifiAddNetworkSuggestionPostConnectionReceiver(String callbackId) {
        if (mNetworkSuggestionPostConnectionReceiver != null) {
            throw new RuntimeException("Another NetworkSuggestionPostConnectionReceiver is added. "
                    + "Only one receiver is supported");
        }
        mNetworkSuggestionPostConnectionReceiver =
                new NetworkSuggestionPostConnectionReceiver(callbackId);
        IntentFilter filter = new IntentFilter(
                WifiManager.ACTION_WIFI_NETWORK_SUGGESTION_POST_CONNECTION);
        mContext.registerReceiver(mNetworkSuggestionPostConnectionReceiver, filter);
        Log.d(TAG, "Added NetworkSuggestionPostConnectionReceiver with ID: " + callbackId);
    }

    /**
     * Removes the BroadcastReceiver for ACTION_WIFI_NETWORK_SUGGESTION_POST_CONNECTION.
     */
    @Rpc(description = "Removes the BroadcastReceiver for "
            + "ACTION_WIFI_NETWORK_SUGGESTION_POST_CONNECTION.")
    public void wifiRemoveNetworkSuggestionPostConnectionReceiver() {
        if (mNetworkSuggestionPostConnectionReceiver == null) {
            Log.d(TAG, "No active NetworkSuggestionPostConnectionReceiver.");
            return;
        }
        mContext.unregisterReceiver(mNetworkSuggestionPostConnectionReceiver);
        mNetworkSuggestionPostConnectionReceiver = null;
        Log.d(TAG, "Removed NetworkSuggestionPostConnectionReceiver.");
    }

    /**
     * Adds Wi-Fi network suggestion.
     *
     * @param jsonSuggestions A JSONArray of WifiNetworkSuggestion objects.
     * @return The status of the operation, as defined by WifiManager.STATUS_*.
     */
    @Rpc(description = "Adds a list of Wi-Fi network suggestions.")
    public int wifiAddNetworkSuggestions(JSONArray jsonSuggestions) throws JSONException {
        List<WifiNetworkSuggestion> suggestions = new ArrayList<>();
        for (int i = 0; i < jsonSuggestions.length(); i++) {
            JSONObject jsonSuggestion = jsonSuggestions.getJSONObject(i);
            suggestions.add(WifiAwareJsonDeserializer.jsonToWifiNetworkSuggestion(jsonSuggestion));
        }
        int status = mWifiManager.addNetworkSuggestions(suggestions);
        Log.i(TAG, "Added network suggestion, status: " + status);
        return status;
    }

    /**
     * Removes Wi-Fi network suggestion.
     *
     * @param jsonSuggestions A JSONArray of WifiNetworkSuggestion objects to be removed.
     * @return The status of the operation, as defined by WifiManager.STATUS_*.
     */
    @Rpc(description = "Removes a list of Wi-Fi network suggestions.")
    public int wifiRemoveNetworkSuggestions(JSONArray jsonSuggestions) throws JSONException {
        List<WifiNetworkSuggestion> suggestions = new ArrayList<>();
        for (int i = 0; i < jsonSuggestions.length(); i++) {
            JSONObject jsonSuggestion = jsonSuggestions.getJSONObject(i);
            suggestions.add(WifiAwareJsonDeserializer.jsonToWifiNetworkSuggestion(jsonSuggestion));
        }
        int status = mWifiManager.removeNetworkSuggestions(suggestions);
        Log.i(TAG, "Removed network suggestion, status: " + status);
        return status;
    }

    /**
     * Retrieves the list of network suggestions currently active from this app.
     *
     * @return A JSONArray of WifiNetworkSuggestion objects in JSON format.
     */
    @Rpc(description = "Retrieves the list of network suggestions currently active from this app.")
    public JSONArray wifiGetNetworkSuggestions() throws JSONException {
        List<WifiNetworkSuggestion> suggestions = mWifiManager.getNetworkSuggestions();
        JSONArray jsonSuggestions = new JSONArray();
        for (WifiNetworkSuggestion suggestion : suggestions) {
            jsonSuggestions.put(WifiJsonConverter.serializeWifiNetworkSuggestion(suggestion));
        }
        Log.i(TAG, "Fetched " + suggestions.size() + " network suggestions.");
        return jsonSuggestions;
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
     * Gets the Wi-Fi tethered AP Configuration.
     *
     * @return AP details in {@link SoftApConfiguration} as JSON format.
     */
    @Rpc(description = "Get current SApConfiguration.")
    public JSONObject wifiGetSapConfiguration() throws JSONException {
        return executeWithShellPermission(
            () -> WifiJsonConverter.serialize(mWifiManager.getSoftApConfiguration()));
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

    /**
     * Set SoftAp Configuration with SoftApConfiguration.
     */
    @Rpc(description = "Set SoftAp Configuration.")
    public boolean wifiSetWifiApConfiguration(JSONObject configJson)
            throws JSONException, Throwable {
        SoftApConfiguration.Builder builder = new SoftApConfiguration.Builder();
        return executeWithShellPermission(
            () -> mWifiManager.setSoftApConfiguration(
                WifiSapJsonDeserializer.jsonToSoftApConfiguration(configJson, builder)),
                 "android.permission.NETWORK_SETTINGS");
    }

    @AsyncRpc(description = "Start track for WiFi supplicant state change.")
    public void wifiStartTrackForStateChange(String callbackId) {
        IntentFilter filter = new IntentFilter(mWifiManager.NETWORK_STATE_CHANGED_ACTION);
        filter.addAction(mWifiManager.SUPPLICANT_STATE_CHANGED_ACTION);
        filter.addAction(mWifiManager.SUPPLICANT_CONNECTION_CHANGE_ACTION);
        filter.addAction(mWifiManager.WIFI_STATE_CHANGED_ACTION);

        mWifiStateReceiver = new WifiSupplicantStateReceiver(callbackId);
        mContext.registerReceiver(mWifiStateReceiver, filter);
    }

    @Rpc(description = "Stop track for WfFi supplicant state change.")
    public void wifiStopTrackForStateChange() {

        mContext.unregisterReceiver(mWifiStateReceiver);
    }

    /**
     * Register a receiver for WiFi supplicant state change event.
     */
    public class WifiSupplicantStateReceiver extends BroadcastReceiver {
        private final String mCallbackId;
        private final EventCache mEventCache = EventCache.getInstance();

        public WifiSupplicantStateReceiver(String mCallbackId) {
            this.mCallbackId = mCallbackId;
        }

        @Override
        public void onReceive(Context ctx, Intent intent) {
            String action = intent.getAction();
            if (action.equals(mWifiManager.NETWORK_STATE_CHANGED_ACTION)) {
                Log.d(TAG, "debug> Wifi network state changed.");
                NetworkInfo nInfo = intent.getParcelableExtra(mWifiManager.EXTRA_NETWORK_INFO);
                Log.d(TAG, "debug> NetworkInfo " + nInfo);
                // If network info is of type wifi, send wifi events.
                if (nInfo.getType() == ConnectivityManager.TYPE_WIFI) {
                    if (nInfo.getDetailedState().equals(DetailedState.DISCONNECTED)) {
                        String eventName = "WifiNetworkDisconnected";
                        SnippetEvent event = new SnippetEvent(mCallbackId, eventName);
                        mEventCache.postEvent(event);
                        Log.d(TAG, "debug> NetworkCallback State changed called for " + eventName);
                    } else if (nInfo.getDetailedState().equals(DetailedState.CONNECTED)) {
                        String eventName = "WifiNetworkConnected";
                        WifiInfo currentConnectionInfo = wifiGetCurrentConnectionInfo();
                        SnippetEvent event = new SnippetEvent(mCallbackId, eventName);
                        event.getData().putString("SSID",
                                WifiJsonConverter.trimQuotationMarks(
                                    currentConnectionInfo.getSSID()));
                        Log.d(TAG, "debug> connection info=" + currentConnectionInfo.getSSID());
                        mEventCache.postEvent(event);
                        Log.d(TAG, "debug> NetworkCallback State changed called for " + eventName);
                        Log.d(TAG, "debug> network info=" + nInfo);
                        Log.d(TAG, "debug> connection info=" + currentConnectionInfo);
                    }
                }
            } else if (action.equals(mWifiManager.WIFI_STATE_CHANGED_ACTION)) {
                SnippetEvent event = new SnippetEvent(mCallbackId, "WifiStateChanged");
                int state = intent.getIntExtra(
                        mWifiManager.EXTRA_WIFI_STATE, mWifiManager.WIFI_STATE_DISABLED);
                Log.d(TAG, "Wifi state changed to " + state);
                boolean enabled;
                if (state == mWifiManager.WIFI_STATE_DISABLED) {
                    enabled = false;
                } else if (state == mWifiManager.WIFI_STATE_ENABLED) {
                    enabled = true;
                } else {
                    // we only care about enabled/disabled.
                    Log.v(TAG, "Ignoring intermediate wifi state change event...");
                    return;
                }
                event.getData().putBoolean("enabled", enabled);
                mEventCache.postEvent(event);
            }
        }
    }

    /**
     * Gets the current connected Wi-Fi information
     *
     * @return WifiInfo
     */
    @Rpc(description = "Gets the current connected Wi-Fi connection information.")
    public WifiInfo wifiGetCurrentConnectionInfo() {
        return executeWithShellPermission(mWifiManager::getConnectionInfo);
    }

    private static class WifiActionListener implements WifiManager.ActionListener {
        private final CountDownLatch mLatch;
        WifiActionListener(CountDownLatch latch) {
            this.mLatch = latch;
        }

        @Override
        public void onSuccess() {
            Log.d(TAG, "debug> WifiActionListener onSuccess callback is triggered.");
            mLatch.countDown();
        }

        @Override
        public void onFailure(int reason) {
            Log.d(TAG, "debug> WifiActionListener onFailure callback is triggered.");
            throw new RuntimeException(
                "WifiActionListener onFailure callback is triggered: " + reason, null);
        }
    }
    private boolean latchWrapper(CountDownLatch latch, Runnable runnable, int timeoutSec) {
        runnable.run();
        try {
            Log.d(TAG, "Latch wrapper waits till operation is done.");
            return latch.await(timeoutSec, SECONDS);
        } catch (InterruptedException e) {
            Log.e(TAG, "Latch wrapper gets interrupted.", e);
            return false;
        }
    }

    /**
     * Forget a wifi network by networkId.
     */
    @Rpc(description = "Forget a wifi network by networkId")
    public void wifiForgetNetwork(Integer networkId) {
        Log.d(TAG, "debug> networkdId: " + networkId);

        CountDownLatch latch = new CountDownLatch(1);
        WifiActionListener listener =  new WifiActionListener(latch);
        executeWithShellPermission(
                () -> latchWrapper(latch, () -> mWifiManager.forget(networkId, listener), 10));
    }

    @Rpc(description = "Checks Wifi state. True if Wifi is enabled.")
    public Boolean wifiCheckState() {
        return mWifiManager.getWifiState() == WifiManager.WIFI_STATE_ENABLED;
    }

    /**
     * Adds a WiFi network.
     *
     * @param configJson {@link JSONObject} network config
     */
    @Rpc(description = "Add network Configuration.")
    public Integer wifiAddNetwork(JSONObject configJson) throws JSONException, Throwable {
        return wifiAddOrUpdateNetwork(configJson);
    }

    /**
     * Adds or updates a WiFi network.
     *
     * @param configJson {@link JSONObject} network config
     */
    @Rpc(description = "Add or update a WiFi network.")
    public Integer wifiAddOrUpdateNetwork(JSONObject configJson) throws JSONException, Throwable {
        WifiConfiguration wifiNetworkConfig = JsonDeserializer.jsonToWifiConfig(configJson);
        return executeWithShellPermission(
            () -> mWifiManager.addNetwork(wifiNetworkConfig));
    }

    @Rpc(description = "Enable/disable auto join for a network.")
    public void wifiEnableAutojoin(int netId, boolean enableAutojoin) {
        // invoke signature-protected `allowAutojoin` API via reflection
        executeWithShellPermission(() -> mWifiManager.allowAutojoin(netId, enableAutojoin));
    }

    @Rpc(description = "Resets all WifiManager settings.")
    public void wifiFactoryReset() {
        executeWithShellPermission(() -> mWifiManager.factoryReset());
    }

    /**
     * Returns the WiFi connection standard.
     *
     * @return WiFi connection standard
     */
    @Rpc(description = "Get the WiFi connection standard, e.g. 802.11N, 802.11AC etc.")
    public Integer wifiGetConnectionStandard() {
        return executeWithShellPermission(
            () -> mWifiManager.getConnectionInfo().getWifiStandard());
    }

    @Rpc(description = "Check if wifi scanner is supported on this device.")
    public Boolean wifiIsScannerSupported() {
        return executeWithShellPermission(() -> mWifiManager.isWifiScannerSupported());
    }

    @Rpc(description = "Enable a configured network."
            + " Initiate a connection if disableOthers is true, True if the operation succeeded.")
    public Boolean
            wifiEnableNetwork(Integer netId, Boolean disableOthers) {
        return executeWithShellPermission(() -> mWifiManager.enableNetwork(netId, disableOthers));
    }

    /**
     * Stop WifisetScanThrottleEnabled.
     */
    @Rpc(description = "Stop WifisetScanThrottleEnabled.")
    public void wifiSetScanThrottleDisable() {
        ShellIdentityUtils.invokeWithShellPermissions(
                () -> mWifiManager.setScanThrottleEnabled(false));
    }

    /**
     * Enables/disables Wi-Fi scan throttling.
     */
    @Rpc(description = "Enable/disable wifi scan throttling.")
    public void wifiSetScanThrottleState(boolean enabled) {
        ShellIdentityUtils.invokeWithShellPermissions(
                () -> mWifiManager.setScanThrottleEnabled(enabled));
    }

    /**
     * Gets Wi-Fi scan throttling state.
     */
    @Rpc(description = "Get Wi-Fi scan throttle state.")
    public boolean wifiIsScanThrottleEnabled() {
        return ShellIdentityUtils.invokeWithShellPermissions(
            () -> mWifiManager.isScanThrottleEnabled());
    }

    /**
     * Scan listener passed to WiFiScanner APIs.
     *
     * <p>With different types of events triggered when executing WiFiScanner APIs, corresponding
     * method will be invoked. The method and its caller leverage latch to achieve synchronized
     * call.
     */
    private static class WifiScanListener implements WifiScanner.ScanListener {
        private static final int STATE_NONE = 0;
        private static final int STATE_CMD_SUCCESS = 1;
        private static final int STATE_CMD_FAILURE = 2;
        private static final int STATE_SCAN_SUCCESS = 3;

        private int mState;
        private CountDownLatch mLatch;
        private long mStartTime;
        private long mEndTime;
        private List<ScanResult> mResult;

        void reset(CountDownLatch latch) {
            this.mState = STATE_NONE;
            this.mLatch = latch;
            this.mStartTime = SystemClock.elapsedRealtime();
            this.mEndTime = 0;
            this.mResult = new ArrayList<ScanResult>();
        }

        @Override
        public void onSuccess() {
            Log.d(TAG, "WifiScanListener onSuccess callback.");
            this.mState = STATE_CMD_SUCCESS;
            this.mLatch.countDown();
        }

        @Override
        public void onFailure(int reason, String description) {
            Log.d(TAG, "WifiScanListener onFailure callback.");
            this.mState = STATE_CMD_FAILURE;
            this.mLatch.countDown();
        }

        @Override
        public void onPeriodChanged(int periodInMs) {
            Log.d(TAG, "WifiScanListener onPeriodChanged callback.");
        }

        @Override
        public void onResults(ScanData[] results) {
            Log.d(TAG, "WifiScanListener onResults callback.");
            this.mState = STATE_SCAN_SUCCESS;
            this.mEndTime = SystemClock.elapsedRealtime();
            for (ScanData scanData : results) {
                this.mResult.addAll(Arrays.asList(scanData.getResults()));
            }
        }

        @Override
        public void onFullResult(ScanResult fullScanResult) {
            Log.d(TAG, "WifiScanListener onFullResult callback.");
        }
    }
    private static class WifiManagerSnippetException extends Exception {

        WifiManagerSnippetException(String msg, Throwable err) {
            super(msg, err);
        }
    }

    /**
     * Connects to the network with the given configuration.
     *
     * @param jsonConfig {@link JSONObject} of WiFi connection parameters.
     * @throws Throwable
     */
    @Rpc(description = "Connect to the network with the given configuration.")
    public void wifiConnecting(JSONObject jsonConfig) throws Throwable {
        Log.d(TAG, "Got network config: " + jsonConfig);
        WifiConfiguration wifiConfig = JsonDeserializer.jsonToWifiConfig(jsonConfig);
        CountDownLatch latch = new CountDownLatch(1);
        WifiInfo connectionInfo = wifiGetCurrentConnectionInfo();
        if (connectionInfo.getNetworkId() != -1
                && connectionInfo.getSSID().equals(wifiConfig.SSID)
                && connectionInfo.getSupplicantState().equals(SupplicantState.COMPLETED)) {
            if (wifiConfig.BSSID == null
                    || wifiConfig
                    .BSSID
                    .toLowerCase(Locale.getDefault())
                    .equals(connectionInfo.getBSSID().toLowerCase(Locale.getDefault()))) {
                Log.d(TAG,
                        "Network "
                        + connectionInfo.getSSID()
                        + " is already connected. ConnectionInfo: "
                        + connectionInfo);
                return;
            }
        }

        WifiActionListener listener = new WifiActionListener(latch);

        executeWithShellPermission(
                () -> {
                mWifiManager.connect(wifiConfig, listener);
                try {
                    if (!latch.await(10, SECONDS)) {
                        throw new TimeoutException("WiFi connection timeouts.");
                    }
                } catch (Exception e) {
                    throw new RuntimeException("WiFi connection fails.", e);
                }
            });

        if (!Utils.waitUntil(
                () -> {
                    WifiInfo currentConnectionInfo = wifiGetCurrentConnectionInfo();
                    return currentConnectionInfo.getNetworkId() != -1
                        && currentConnectionInfo.getSSID().equals(wifiConfig.SSID)
                        && currentConnectionInfo.getSupplicantState().equals(
                            SupplicantState.COMPLETED);
                },
                CONNECT_TIMEOUT_IN_SEC)) {
            throw new WifiManagerSnippetException(
                "Failed to connect to '"
                    + jsonConfig
                    + "', timeout! Current connection: '"
                    + wifiGetCurrentConnectionInfo().getSSID()
                    + "'",
                null);
        }
    }

    /**
   * Toggles WiFi on and off.
   *
   * @param enabled {@code true} to turn on, {@code false} to turn off
   */
    @Rpc(description = "Toggle WiFi on and off.")
    public void wifiToggleState(boolean enabled) throws WifiManagerSnippetException {
        int expectedWifiState =
                enabled ? WifiManager.WIFI_STATE_ENABLED : WifiManager.WIFI_STATE_DISABLED;

        if (mWifiManager.getWifiState() == expectedWifiState) {
            return;
        }

        int wifiStateNeededToWait =
                enabled ? WifiManager.WIFI_STATE_DISABLING : WifiManager.WIFI_STATE_ENABLING;
        if (mWifiManager.getWifiState() == wifiStateNeededToWait) {
            if (!Utils.waitUntil(
                    () -> mWifiManager.getWifiState() == WifiManager.WIFI_STATE_DISABLED,
                    TOGGLE_STATE_TIMEOUT_IN_SEC)) {
                Log.e(TAG, "Wi-Fi failed to stabilize after "
                        + TOGGLE_STATE_TIMEOUT_IN_SEC + "s.");
            }
        }

        if (!executeWithShellPermission(() -> mWifiManager.setWifiEnabled(enabled))) {
            throw new WifiManagerSnippetException(
            "Failed to initiate toggling Wi-Fi to " + enabled, null);
        }
        if (!Utils.waitUntil(
                () -> mWifiManager.getWifiState()
                == expectedWifiState, TOGGLE_STATE_TIMEOUT_IN_SEC)) {
            throw new WifiManagerSnippetException(
            "Failed to toggle Wi-Fi to "
                + enabled
                + "after "
                + TOGGLE_STATE_TIMEOUT_IN_SEC
                + "s timeout",
            null);
        }
    }

    /**
     * Constants for device mobility states.
     */
    @IntDef({
        WifiManager.DEVICE_MOBILITY_STATE_UNKNOWN,
        WifiManager.DEVICE_MOBILITY_STATE_HIGH_MVMT,
        WifiManager.DEVICE_MOBILITY_STATE_LOW_MVMT,
        WifiManager.DEVICE_MOBILITY_STATE_STATIONARY
    })
    private @interface DeviceMobilityState {}

    /**
    * Sets the device mobility state for testing.
    * @param state The mobility state to set.
    */
    @Rpc(description = "Sets the device mobility state.")
    public void wifiSetDeviceMobilityState(@DeviceMobilityState int state) throws Throwable {
        Log.d(TAG, "Setting device mobility state to: " + state);
        // This runs the command with elevated shell permissions.
        executeWithShellPermission(() -> mWifiManager.setDeviceMobilityState(state));
    }
    /** Turns on Wi-Fi. */
    @Rpc(description = "Turn on Wi-Fi.")
    public void wifiToggleEnable() throws InterruptedException, WifiManagerSnippetException {
        wifiToggleState(true);
    }

    /** Turns off Wi-Fi. */
    @Rpc(description = "Turn off Wi-Fi.")
    public void wifiToggleDisable() throws InterruptedException, WifiManagerSnippetException {
        wifiToggleState(false);
    }

    /** Start scan, wait for scan to complete, and return results. */
    @Rpc(
            description =
                "Start scan, wait for scan to complete, and return results, which is a list of "
                + "serialized WifiScanResult objects.")
    public JSONArray wifiScanAndGetResultsWithShellPermission()
            throws InterruptedException, JSONException, WifiManagerSnippetException {
        WifiScanReceiver receiver = new WifiScanReceiver();
        mContext.registerReceiver(
                receiver, new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));

        try {
            mIsScanResultAvailable = false;
            if (!executeWithShellPermission(() -> mWifiManager.startScan())) {
                throw new WifiManagerSnippetException("Failed to initiate Wi-Fi scan.", null);
            }
            if (!Utils.waitUntil(() -> mIsScanResultAvailable, 2 * 60)) {
                throw new WifiManagerSnippetException(
                    "Failed to get scan results after 2min, timeout!", null);
            }

            JSONArray results = new JSONArray();
            for (ScanResult result : mWifiManager.getScanResults()) {
                results.put(WifiAwareSnippetConverter.serializeScanResult(result));
            }
            return results;
        } finally {
            mContext.unregisterReceiver(receiver);
        }
    }

    private class WifiScanReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context c, Intent intent) {
            String action = intent.getAction();
            if (!action.equals(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)) return;
            if (!intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false)) return;
            mIsScanResultAvailable = true;
        }
    }

    /**
     * Gets the list of usable Wi-Fi channels for a given band and operating mode.
     *
     * @param band The Wi-Fi band to query, e.g., {@link SoftApConfiguration#BAND_2GHZ}.
     * @return A list of usable channel frequencies in MHz, or an empty list on failure or if
     *         unsupported.
     */
    @Rpc(description = "Gets usable Wi-Fi channels for a given band and mode.")
    public List<Integer> wifiGetUsableChannels(int band, int mode) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            Log.w(TAG, "getUsableChannels requires Android S (API 31) or higher.");
            return new ArrayList<>();
        }
        if (mWifiManager == null) {
            Log.e(TAG, "WifiManager service not available.");
            return new ArrayList<>();
        }

        try {
            Log.i(TAG, "WifiManager getUsableChannels available.");
            List<WifiAvailableChannel> channelObjects = mWifiManager.getUsableChannels(
                            band, mode);
            if (channelObjects == null) {
                return  new ArrayList<>();
            }
            List<Integer> channelFrequencies = new ArrayList<>();
            for (WifiAvailableChannel channel : channelObjects) {
                channelFrequencies.add(channel.getFrequencyMhz());
            }
            return channelFrequencies;
        } catch (SecurityException e) {
            Log.e(TAG, "Permission denial for getUsableChannels.", e);
            return new ArrayList<>();
        } catch (Exception e) {
            Log.e(TAG, "Error calling getUsableChannels.", e);
            return new ArrayList<>();
        }
    }

    /**
     * Sets the country code for the device.
     *
     * @param countryCode The country code to set.
     */
    @Rpc(description = "Sets the country code for the device.")
    public void setOverrideWifiCountryCode(String countryCode) {
        Log.d(TAG, "setOverridetWifiCountryCode: " + countryCode);
        executeWithShellPermission(() -> mWifiManager.setOverrideCountryCode(countryCode));
    }

    /**
     * Gets the country code for the device.
     *
     * @return The country code for the device.
     */
    @Rpc(description = "Gets the country code for the device.")
    public String getWifiCountryCode() {
        return executeWithShellPermission(() -> mWifiManager.getCountryCode());
    }

    /**
     * Clears the override country code for the device.
     */
    @Rpc(description = "Clears the country code for the device.")
    public void clearOverrideWifiCountryCode() {
        executeWithShellPermission(() -> mWifiManager.clearOverrideCountryCode());
    }
}
