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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.NetworkInfo.DetailedState;
import android.net.wifi.ScanResult;
import android.net.wifi.SoftApConfiguration;
import android.net.wifi.SoftApInfo;
import android.net.wifi.SupplicantState;
import android.net.wifi.WifiClient;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiScanner;
import android.net.wifi.WifiScanner.ScanData;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.util.Log;

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
import com.google.snippet.wifi.softap.WifiSapJsonDeserializer;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;


/**
 * Snippet class for WifiManager.
 */
public class WifiManagerSnippet extends WifiShellPermissionSnippet implements Snippet {

    private static final String TAG = "WifiManagerSnippet";
    private static final long POLLING_TIMEOUT_MS = TimeUnit.SECONDS.toMillis(10);
    private static final int CONNECT_TIMEOUT_IN_SEC = 90;

    private final Context mContext;
    private final WifiManager mWifiManager;
    private final Handler mHandler;
    private final Object mLock = new Object();
    private WifiManagerSnippet.SnippetSoftApCallback mSoftApCallback;
    private WifiManager.LocalOnlyHotspotReservation mLocalOnlyHotspotReservation;
    private BroadcastReceiver mWifiStateReceiver;

    /**
     * Callback to listen in and verify events to SoftAp.
     */
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

    public WifiManagerSnippet() {
        mContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        mWifiManager = mContext.getSystemService(WifiManager.class);
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
                        WifiInfo currentConnectionInfo = getConnectionInfo();
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

    private WifiInfo getConnectionInfo() {
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
        WifiInfo connectionInfo = getConnectionInfo();
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
                    WifiInfo currentConnectionInfo = getConnectionInfo();
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
                    + getConnectionInfo().getSSID()
                    + "'",
                null);
        }
    }
}
