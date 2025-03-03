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

package com.google.snippet.wifi.direct;

import android.Manifest;
import android.app.Instrumentation;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.NetworkInfo;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pDeviceList;
import android.net.wifi.p2p.WifiP2pGroup;
import android.net.wifi.p2p.WifiP2pGroupList;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceInfo;
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceRequest;
import android.net.wifi.p2p.nsd.WifiP2pServiceInfo;
import android.net.wifi.p2p.nsd.WifiP2pServiceRequest;
import android.net.wifi.p2p.nsd.WifiP2pUpnpServiceInfo;
import android.net.wifi.p2p.nsd.WifiP2pUpnpServiceRequest;
import android.os.Bundle;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;

import com.google.android.mobly.snippet.Snippet;
import com.google.android.mobly.snippet.event.EventCache;
import com.google.android.mobly.snippet.event.SnippetEvent;
import com.google.android.mobly.snippet.rpc.AsyncRpc;
import com.google.android.mobly.snippet.rpc.Rpc;
import com.google.android.mobly.snippet.rpc.RpcDefault;
import com.google.android.mobly.snippet.rpc.RpcOptional;
import com.google.android.mobly.snippet.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;


/** Snippet class for WifiP2pManager. */
public class WifiP2pManagerSnippet implements Snippet {
    private static final String TAG = "WifiP2pManagerSnippet";
    private static final int TIMEOUT_SHORT_MS = 10000;
    private static final int UI_ACTION_SHORT_TIMEOUT_MS = 5000;
    private static final int UI_ACTION_LONG_TIMEOUT_MS = 30000;
    private static final String EVENT_KEY_CALLBACK_NAME = "callbackName";
    private static final String EVENT_KEY_REASON = "reason";
    private static final String EVENT_KEY_P2P_DEVICE = "p2pDevice";
    private static final String EVENT_KEY_P2P_INFO = "p2pInfo";
    private static final String EVENT_KEY_P2P_GROUP = "p2pGroup";
    private static final String EVENT_KEY_PEER_LIST = "peerList";
    private static final String EVENT_KEY_SERVICE_LIST = "serviceList";
    private static final String EVENT_KEY_INSTANCE_NAME = "instanceName";
    private static final String EVENT_KEY_REGISTRATION_TYPE = "registrationType";
    private static final String EVENT_KEY_SOURCE_DEVICE = "sourceDevice";
    private static final String EVENT_KEY_FULL_DOMAIN_NAME = "fullDomainName";
    private static final String EVENT_KEY_TXT_RECORD_MAP = "txtRecordMap";
    private static final String EVENT_KEY_TIMESTAMP_MS = "timestampMs";
    private static final String ACTION_LISTENER_ON_SUCCESS = "onSuccess";
    public static final String ACTION_LISTENER_ON_FAILURE = "onFailure";

    private final Context mContext;
    private final IntentFilter mIntentFilter;
    private final WifiP2pManager mP2pManager;

    private Instrumentation mInstrumentation = InstrumentationRegistry.getInstrumentation();
    private UiDevice mUiDevice = UiDevice.getInstance(mInstrumentation);

    private final HashMap<Integer, WifiP2pManager.Channel> mChannels = new HashMap<>();
    private WifiP2pStateChangedReceiver mStateChangedReceiver = null;

    private int mServiceRequestCnt = 0;
    private int mChannelCnt = -1;

    private final Map<Integer, WifiP2pServiceRequest> mServiceRequests;


    private static class WifiP2pManagerException extends Exception {
        WifiP2pManagerException(String message) {
            super(message);
        }
    }

    public WifiP2pManagerSnippet() {
        Log.d("Elevating permission require to enable support for privileged operation in "
                + "Android Q+");
        mInstrumentation.getUiAutomation().adoptShellPermissionIdentity();

        mContext = ApplicationProvider.getApplicationContext();

        checkPermissions(mContext, Manifest.permission.ACCESS_WIFI_STATE,
                Manifest.permission.CHANGE_WIFI_STATE, Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.NEARBY_WIFI_DEVICES);

        mP2pManager = mContext.getSystemService(WifiP2pManager.class);

        mIntentFilter = new IntentFilter();
        mIntentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
        mIntentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
        mIntentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
        mIntentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);

        mServiceRequests = new HashMap<Integer, WifiP2pServiceRequest>();
    }

    /**
     * Initialize the application with the Wi-Fi P2P framework and registers necessary receivers.
     *
     * @param callbackId The callback ID assigned by Mobly
     * @return The ID of the initialized channel. Use this ID to specify which channel to
     *         operate on in future operations.
     * @throws WifiP2pManagerException If the Wi-Fi P2P has already been initialized.
     */
    @AsyncRpc(description = "Register the application with the Wi-Fi framework.")
    public int wifiP2pInitialize(String callbackId) throws WifiP2pManagerException {
        if (mStateChangedReceiver != null) {
            throw new WifiP2pManagerException("WifiP2pManager has already been initialized. "
                    + "Please call `p2pClose()` close the current connection.");
        }
        if (mChannelCnt != -1) {
            throw new WifiP2pManagerException("Please call `p2pClose()` to close the current "
                    + "connection before initializing a new one.");
        }
        checkP2pManager();
        // Initialize the first channel. This channel will be used by default if an Wi-Fi P2P RPC
        // method is called without a channel ID.
        mStateChangedReceiver = new WifiP2pStateChangedReceiver(callbackId);
        mContext.registerReceiver(mStateChangedReceiver, mIntentFilter,
                Context.RECEIVER_NOT_EXPORTED);
        WifiP2pManager.Channel channel =
                mP2pManager.initialize(mContext, mContext.getMainLooper(), null);
        mChannelCnt += 1;
        mChannels.put(mChannelCnt, channel);
        return mChannelCnt;
    }

    /**
     * Initialize an extra Wi-Fi P2P channel. This is for multi-channel tests.
     *
     * @return The id of the new channel.
     */
    @Rpc(description = "Initialize an extra Wi-Fi P2P channel. This is needed when you need to "
            + "test with multiple channels.")
    public int wifiP2pInitExtraChannel() {
        if (mChannelCnt == -1) {
            throw new IllegalStateException("Main channel has not been initialized. Please call "
                    + "`wifiP2pInitialize` first.");
        }
        WifiP2pManager.Channel channel =
                mP2pManager.initialize(mContext, mContext.getMainLooper(), null);
        mChannelCnt += 1;
        mChannels.put(mChannelCnt, channel);
        return mChannelCnt;
    }

    /**
     * Request the device information in the form of WifiP2pDevice.
     *
     * @param callbackId The callback ID assigned by Mobly.
     * @param channelId The ID of the channel for Wi-Fi P2P to operate on.
     * @throws WifiP2pManagerException If got invalid channel ID.
     */
    @AsyncRpc(description = "Request the device information in the form of WifiP2pDevice.")
    public void wifiP2pRequestDeviceInfo(String callbackId,
            @RpcDefault(value = "0") Integer channelId)
            throws WifiP2pManagerException {
        WifiP2pManager.Channel channel = getChannel(channelId);
        mP2pManager.requestDeviceInfo(channel, new DeviceInfoListener(callbackId));
    }

    /**
     * Request the connection information in the form of WifiP2pDevice.
     *
     * @param callbackId The callback ID assigned by Mobly.
     * @param channelId The ID of the channel for Wi-Fi P2P to operate on.
     */
    @AsyncRpc(description = "Request the connection information in the form of WifiP2pDevice.")
    public void wifiP2pRequestConnectionInfo(String callbackId,
            @RpcDefault(value = "0") Integer channelId)
            throws WifiP2pManagerException {
        WifiP2pManager.Channel channel = getChannel(channelId);
        mP2pManager.requestConnectionInfo(channel,
            new WifiP2pConnectionInfoListener(callbackId));
    }

    /**
     * Initiate peer discovery. A discovery process involves scanning for available Wi-Fi peers for
     * the purpose of establishing a connection.
     *
     * @param channelId The ID of the channel for Wi-Fi P2P to operate on.
     * @throws Throwable If the P2P operation failed or timed out, or got invalid channel ID.
     */
    @Rpc(description = "Initiate peer discovery. A discovery process involves scanning for "
            + "available Wi-Fi peers for the purpose of establishing a connection.")
    public void wifiP2pDiscoverPeers(@RpcDefault(value = "0") Integer channelId) throws Throwable {
        WifiP2pManager.Channel channel = getChannel(channelId);
        String callbackId = UUID.randomUUID().toString();
        mP2pManager.discoverPeers(channel, new ActionListener(callbackId));
        verifyActionListenerSucceed(callbackId);
    }

    /**
     * Request peers that are discovered for wifi p2p.
     *
     * @param callbackId The callback ID assigned by Mobly.
     * @param channelId The ID of the channel for Wi-Fi P2P to operate on.
     * @throws Throwable If the P2P operation failed or timed out, or got invalid channel ID.
     */
    @AsyncRpc(description = "Request peers that are discovered for wifi p2p.")
    public void wifiP2pRequestPeers(String callbackId, @RpcDefault(value = "0") Integer channelId)
            throws Throwable {
        WifiP2pManager.Channel channel = getChannel(channelId);
        mP2pManager.requestPeers(channel, new PeerListListener(callbackId));
    }

    /**
     * Cancel any ongoing p2p group negotiation.
     *
     * @param channelId The ID of the channel for Wi-Fi P2P to operate on.
     * @return The event posted by the callback methods of {@link ActionListener}.
     * @throws Throwable If the P2P operation failed or timed out, or got invalid channel ID.
     */
    @Rpc(description = "Cancel any ongoing p2p group negotiation.")
    public Bundle wifiP2pCancelConnect(@RpcDefault(value = "0") Integer channelId)
            throws Throwable {
        WifiP2pManager.Channel channel = getChannel(channelId);
        String callbackId = UUID.randomUUID().toString();
        mP2pManager.cancelConnect(channel, new ActionListener((callbackId)));
        return waitActionListenerResult(callbackId);
    }

    /**
     * Stop current ongoing peer discovery.
     *
     * @param channelId The ID of the channel for Wi-Fi P2P to operate on.
     * @return The event posted by the callback methods of {@link ActionListener}.
     * @throws Throwable If the P2P operation failed or timed out, or got invalid channel ID.
     */
    @Rpc(description = "Stop current ongoing peer discovery.")
    public Bundle wifiP2pStopPeerDiscovery(@RpcDefault(value = "0") Integer channelId)
            throws Throwable {
        WifiP2pManager.Channel channel = getChannel(channelId);
        String callbackId = UUID.randomUUID().toString();
        mP2pManager.stopPeerDiscovery(channel, new ActionListener(callbackId));
        return waitActionListenerResult(callbackId);
    }

    /**
     * Create a p2p group with the current device as the group owner.
     *
     * @param  wifiP2pConfig The configuration for the p2p group.
     * @param  channelId The ID of the channel for Wi-Fi P2P to operate on.
     * @throws Throwable If the P2P operation failed or timed out, or got invalid channel ID.
     */
    @Rpc(description = "Create a p2p group with the current device as the group owner.")
    public void wifiP2pCreateGroup(
            @RpcOptional JSONObject wifiP2pConfig,
            @RpcDefault(value = "0") Integer channelId
    ) throws Throwable {
        WifiP2pManager.Channel channel = getChannel(channelId);
        String callbackId = UUID.randomUUID().toString();
        ActionListener actionListener = new ActionListener(callbackId);
        WifiP2pConfig config = null;
        if (wifiP2pConfig != null) {
            config = JsonDeserializer.jsonToWifiP2pConfig(wifiP2pConfig);
        }
        Log.d("Creating wifi p2p group with config: " + String.valueOf(config));
        mP2pManager.createGroup(channel, config, actionListener);
        verifyActionListenerSucceed(callbackId);
    }

    /**
     * Start a p2p connection to a device with the specified configuration.
     *
     * @param wifiP2pConfig The configuration for the p2p connection.
     * @param channelId The ID of the channel for Wi-Fi P2P to operate on.
     * @throws Throwable If the P2P operation failed or timed out, or got invalid channel ID.
     */
    @Rpc(description = "Start a p2p connection to a device with the specified configuration.")
    public void wifiP2pConnect(
            JSONObject wifiP2pConfig,
            @RpcDefault(value = "0") Integer channelId) throws Throwable {
        WifiP2pManager.Channel channel = getChannel(channelId);
        String callbackId = UUID.randomUUID().toString();
        WifiP2pConfig config = JsonDeserializer.jsonToWifiP2pConfig(wifiP2pConfig);
        Log.d("Connecting p2p group with config: " + String.valueOf(config));
        mP2pManager.connect(channel, config, new ActionListener(callbackId));
        verifyActionListenerSucceed(callbackId);
    }

    /**
     * Accept p2p connection invitation through clicking on UI.
     *
     * @param deviceName The name of the device to connect.
     * @throws WifiP2pManagerException If failed to accept invitation through UI.
     */
    @Rpc(description = "Accept p2p connection invitation through clicking on UI.")
    public void wifiP2pAcceptInvitation(String deviceName) throws WifiP2pManagerException {
        if (!mUiDevice.wait(Until.hasObject(By.text("Invitation to connect")),
                UI_ACTION_LONG_TIMEOUT_MS)) {
            throw new WifiP2pManagerException(
                    "Expected connect invitation did not occur within timeout.");
        }
        if (!mUiDevice.wait(Until.hasObject(By.text(deviceName)), UI_ACTION_SHORT_TIMEOUT_MS)) {
            throw new WifiP2pManagerException(
                    "The connect invitation is not triggered by expected peer device.");
        }
        Pattern pattern = Pattern.compile("(ACCEPT|OK|Accept)");
        if (!mUiDevice.wait(Until.hasObject(By.text(pattern).clazz(Button.class)),
                UI_ACTION_SHORT_TIMEOUT_MS)) {
            throw new WifiP2pManagerException("Accept button did not occur within timeout.");
        }
        UiObject2 acceptButton = mUiDevice.findObject(By.text(pattern).clazz(Button.class));
        if (acceptButton == null) {
            throw new WifiP2pManagerException(
                    "There's no accept button for the connect invitation.");
        }
        acceptButton.click();
    }

    /**
     * Get p2p connect PIN code after calling {@link #wifiP2pConnect(JSONObject,Integer)} with
     * WPS PIN.
     *
     * @param deviceName The name of the device to connect.
     * @return The generated PIN as a String.
     * @throws Throwable If failed to get PIN code.
     */
    @Rpc(description = "Get p2p connect PIN code after calling wifiP2pConnect with WPS PIN.")
    public String wifiP2pGetPinCode(String deviceName) throws Throwable {
        // Wait for the 'Invitation sent' dialog to appear
        if (!mUiDevice.wait(Until.hasObject(By.text("Invitation sent")),
                UI_ACTION_LONG_TIMEOUT_MS)) {
            throw new WifiP2pManagerException(
                    "Invitation sent dialog did not appear within timeout.");
        }
        if (!mUiDevice.wait(Until.hasObject(By.text(deviceName)), UI_ACTION_SHORT_TIMEOUT_MS)) {
            throw new WifiP2pManagerException(
                    "The connect invitation is not triggered by expected peer device.");
        }
        // Find the UI lement with text='PIN:'
        UiObject2 pinLabel = mUiDevice.findObject(By.text("PIN:"));
        if (pinLabel == null) {
            throw new WifiP2pManagerException("PIN label not found.");
        }
        // Get the sibling UI element that contains the PIN code. Use regex pattern "\d+" as PIN
        // code must be composed entirely of numbers.
        Pattern pattern = Pattern.compile("\\d+");
        UiObject2 pinValue = pinLabel.getParent().findObject(By.text(pattern));
        if (pinValue == null) {
            throw new WifiP2pManagerException("Failed to find Pin code UI element.");
        }
        String pinCode = pinValue.getText();
        Log.d("Retrieved PIN code: " + pinCode);
        // Click 'OK' to close the PIN code alert
        UiObject2 okButton = mUiDevice.findObject(By.text("OK").clazz(Button.class));
        if (okButton == null) {
            throw new WifiP2pManagerException(
                    "OK button not found in the p2p connection invitation pop-up window.");
        }
        okButton.click();
        Log.d("Closed the p2p connect invitation pop-up window.");
        return pinCode;
    }

    /**
     * Enters the given PIN code to accept a P2P connection invitation.
     *
     * @param pinCode    The PIN to enter.
     * @param deviceName The name of the device that initiated the connection.
     * @throws WifiP2pManagerException If the PIN entry field is not found within timeout.
     */
    @Rpc(description = "Enter the PIN code to accept a P2P connection invitation.")
    public void wifiP2pEnterPin(String pinCode, String deviceName) throws WifiP2pManagerException {
        // Wait for the 'Invitation to connect' dialog to appear
        if (!mUiDevice.wait(Until.hasObject(By.textContains("Invitation to connect")),
                UI_ACTION_LONG_TIMEOUT_MS)) {
            throw new WifiP2pManagerException(
                    "Invitation to connect dialog did not appear within timeout.");
        }
        if (!mUiDevice.wait(Until.hasObject(By.text(deviceName)), UI_ACTION_SHORT_TIMEOUT_MS)) {
            throw new WifiP2pManagerException(
                    "The connect invitation is not triggered by expected peer device.");
        }
        // Find the PIN entry field
        UiObject2 pinEntryField = mUiDevice.findObject(By.focused(true));
        if (pinEntryField == null) {
            throw new WifiP2pManagerException("PIN entry field not found.");
        }
        // Enter the PIN code
        pinEntryField.setText(pinCode);
        Log.d("Entered PIN code: " + pinCode);
        // Accept the invitation
        Pattern acceptPattern = Pattern.compile("(ACCEPT|OK|Accept)", Pattern.CASE_INSENSITIVE);
        UiObject2 acceptButton = mUiDevice.findObject(By.clazz(Button.class).text(acceptPattern));
        if (acceptButton == null) {
            throw new WifiP2pManagerException(
                    "Failed to find accept button for p2p connect invitation.");
        }
        acceptButton.click();
        Log.d("Accepted the connection.");
    }

    /**
     * Remove the current p2p group.
     *
     * @param channelId The ID of the channel for Wi-Fi P2P to operate on.
     * @return The event posted by the callback methods of {@link ActionListener}.
     * @throws Throwable If the P2P operation failed or timed out, or got invalid channel ID.
     */
    @Rpc(description = "Remove the current p2p group.")
    public Bundle wifiP2pRemoveGroup(@RpcDefault(value = "0") Integer channelId) throws Throwable {
        WifiP2pManager.Channel channel = getChannel(channelId);
        String callbackId = UUID.randomUUID().toString();
        mP2pManager.removeGroup(channel, new ActionListener(callbackId));
        return waitActionListenerResult(callbackId);
    }

    /**
     * Request the number of persistent p2p group.
     *
     * @param callbackId The callback ID assigned by Mobly.
     * @param channelId The ID of the channel for Wi-Fi P2P to operate on.
     * @throws Throwable If this failed to request persistent group info, or got invalid channel ID.
     */
    @AsyncRpc(description = "Request the number of persistent p2p group")
    public void wifiP2pRequestPersistentGroupInfo(
            String callbackId,
            @RpcDefault(value = "0") Integer channelId) throws Throwable {
        WifiP2pManager.Channel channel = getChannel(channelId);
        mP2pManager.requestPersistentGroupInfo(channel,
                new PersistentGroupInfoListener(callbackId));
    }

    /**
     * Delete the persistent p2p group with the given network ID.
     *
     * @param networkId The network ID of the persistent p2p group to delete.
     * @param channelId The ID of the channel for Wi-Fi P2P to operate on.
     * @return The event posted by the callback methods of {@link ActionListener}.
     * @throws Throwable If this failed to delete persistent group, or got invalid channel ID.
     */
    @Rpc(description = "Delete the persistent p2p group with the given network ID.")
    public Bundle wifiP2pDeletePersistentGroup(int networkId,
            @RpcDefault(value = "0") Integer channelId) throws Throwable {
        WifiP2pManager.Channel channel = getChannel(channelId);
        String callbackId = UUID.randomUUID().toString();
        mP2pManager.deletePersistentGroup(channel, networkId, new ActionListener(callbackId));
        return waitActionListenerResult(callbackId);
    }

    /**
     * Register Upnp service as a local Wi-Fi p2p service for service discovery.
     * @param uuid The UUID to be passed to
     *     {@link WifiP2pUpnpServiceInfo#newInstance(String, String, List)}.
     * @param device The device to be passed to
     *     {@link WifiP2pUpnpServiceInfo#newInstance(String, String, List)}.
     * @param services The services to be passed to
     *     {@link WifiP2pUpnpServiceInfo#newInstance(String, String, List)}.
     * @param channelId The ID of the channel for Wi-Fi P2P to operate on.
     * @throws Throwable If failed to add local service or got invalid channel ID.
     */
    @Rpc(description = "Register Upnp service as a local Wi-Fi p2p service for service discovery.")
    public void wifiP2pAddUpnpLocalService(
            String uuid,
            String device,
            JSONArray services,
            @RpcDefault(value = "0"
            ) Integer channelId) throws Throwable {
        WifiP2pManager.Channel channel = getChannel(channelId);
        List<String> serviceList = new ArrayList<String>();
        for (int i = 0; i < services.length(); i++) {
            serviceList.add(services.getString(i));
            Log.d("wifiP2pAddUpnpLocalService, services: " + services.getString(i));
        }
        WifiP2pServiceInfo serviceInfo =
                WifiP2pUpnpServiceInfo.newInstance(uuid, device, serviceList);

        String callbackId = UUID.randomUUID().toString();
        mP2pManager.addLocalService(channel, serviceInfo, new ActionListener(callbackId));
        verifyActionListenerSucceed(callbackId);
    }

    /**
     * Register Bonjour service as a local Wi-Fi p2p service for service discovery.
     *
     * @param instanceName The instance name to be passed to
     *     {@link WifiP2pDnsSdServiceInfo#newInstance(String, String, Map)}.
     * @param serviceType The serviceType to be passed to
     *     {@link WifiP2pDnsSdServiceInfo#newInstance(String, String, Map)}.
     * @param txtMap The TXT record to be passed to
     *     {@link WifiP2pDnsSdServiceInfo#newInstance(String, String, Map)}.
     * @param channelId The ID of the channel for Wi-Fi P2P to operate on.
     * @throws Throwable If failed to add local service or got invalid channel ID.
     */
    @Rpc(description = "Register Bonjour service as a local Wi-Fi p2p service for service"
            + " discovery.")
    public void wifiP2pAddBonjourLocalService(String instanceName,
            String serviceType,
            @RpcOptional JSONObject txtMap,
            @RpcDefault(value = "0") Integer channelId
    ) throws Throwable {
        WifiP2pManager.Channel channel = getChannel(channelId);
        Map<String, String> map = null;
        if (txtMap != null) {
            map = new HashMap<String, String>();
            Iterator<String> keyIterator = txtMap.keys();
            while (keyIterator.hasNext()) {
                String key = keyIterator.next();
                map.put(key, txtMap.getString(key));
            }
        }
        WifiP2pDnsSdServiceInfo serviceInfo =
                WifiP2pDnsSdServiceInfo.newInstance(instanceName, serviceType, map);

        String callbackId = UUID.randomUUID().toString();
        mP2pManager.addLocalService(channel, serviceInfo, new ActionListener(callbackId));
        verifyActionListenerSucceed(callbackId);
    }

    /**
     * Clear all registered local services of service discovery.
     *
     * @param channelId The ID of the channel for Wi-Fi P2P to operate on.
     * @throws Throwable If failed to clear local services or got invalid channel ID.
     */
    @Rpc(description = "Clear all registered local services of service discovery.")
    public void wifiP2pClearLocalServices(@RpcDefault(value = "0") Integer channelId)
            throws Throwable {
        WifiP2pManager.Channel channel = getChannel(channelId);
        String callbackId = UUID.randomUUID().toString();
        mP2pManager.clearLocalServices(channel, new ActionListener(callbackId));
        waitActionListenerResult(callbackId);
    }

    /**
     * Add a service discovery request.
     *
     * @param protocolType The protocol type of the service discovery request.
     * @param channelId The ID of the channel for Wi-Fi P2P to operate on.
     * @return The ID of the service request, which is used when calling
     * @throws Throwable If add service request action timed out or got invalid channel ID.
     */
    @Rpc(description = "Add a service discovery request.")
    public Integer wifiP2pAddServiceRequest(
            int protocolType, @RpcDefault(value = "0") Integer channelId
    ) throws Throwable {
        WifiP2pManager.Channel channel = getChannel(channelId);

        WifiP2pServiceRequest request = WifiP2pServiceRequest.newInstance(protocolType);
        mServiceRequestCnt += 1;
        mServiceRequests.put(mServiceRequestCnt, request);

        String callbackId = UUID.randomUUID().toString();
        mP2pManager.addServiceRequest(channel, request, new ActionListener(callbackId));
        verifyActionListenerSucceed(callbackId);
        return mServiceRequestCnt;
    }

    /**
     * Add a service Upnp discovery request.
     *
     * @param serviceType The service type to be passed to {@link WifiP2pUpnpServiceRequest}.
     * @param channelId The ID of the channel for Wi-Fi P2P to operate on.
     * @return The ID of the service request, which is used when calling
     *     {@link #wifiP2pRemoveServiceRequest(int, Integer)}.
     * @throws Throwable If add service request action timed out or got invalid channel ID.
     */
    @Rpc(description = "Add a service Upnp discovery request.")
    public Integer wifiP2pAddUpnpServiceRequest(
            @RpcOptional String serviceType,
            @RpcDefault(value = "0") Integer channelId
    ) throws Throwable {
        WifiP2pManager.Channel channel = getChannel(channelId);
        WifiP2pUpnpServiceRequest request;
        if (serviceType == null) {
            request = WifiP2pUpnpServiceRequest.newInstance();
        } else {
            request = WifiP2pUpnpServiceRequest.newInstance(serviceType);
        }
        mServiceRequestCnt += 1;
        mServiceRequests.put(mServiceRequestCnt, request);

        String callbackId = UUID.randomUUID().toString();
        mP2pManager.addServiceRequest(channel, request, new ActionListener(callbackId));
        verifyActionListenerSucceed(callbackId);
        return mServiceRequestCnt;
    }

    /**
     * Add a service Bonjour discovery request.
     *
     * @param instanceName The instance name to be passed to
     *     {@link WifiP2pDnsSdServiceRequest#newInstance(String, String)}.
     * @param serviceType The service type to be passed to
     *     {@link WifiP2pDnsSdServiceRequest#newInstance(String, String)}.
     * @param channelId The ID of the channel for Wi-Fi P2P to operate on.
     * @return The ID of the service request, which is used when calling
     *     {@link #wifiP2pRemoveServiceRequest(int, Integer)}.
     *  @throws Throwable If add service request action timed out or got invalid channel ID.
     */
    @Rpc(description = "Add a service Bonjour discovery request.")
    public Integer wifiP2pAddBonjourServiceRequest(
            @RpcOptional String instanceName,
            @RpcOptional String serviceType,
            @RpcDefault(value = "0") Integer channelId
    ) throws Throwable {
        WifiP2pManager.Channel channel = getChannel(channelId);
        WifiP2pDnsSdServiceRequest request;
        if (instanceName != null) {
            request = WifiP2pDnsSdServiceRequest.newInstance(instanceName, serviceType);
        } else if (serviceType == null) {
            request = WifiP2pDnsSdServiceRequest.newInstance();
        } else {
            request = WifiP2pDnsSdServiceRequest.newInstance(serviceType);
        }
        mServiceRequestCnt += 1;
        mServiceRequests.put(mServiceRequestCnt, request);

        String callbackId = UUID.randomUUID().toString();
        mP2pManager.addServiceRequest(channel, request, new ActionListener(callbackId));
        verifyActionListenerSucceed(callbackId);
        return mServiceRequestCnt;
    }

    /**
     * Remove a service discovery request.
     *
     * @param index The index of the service request to remove.
     * @param channelId The ID of the channel for Wi-Fi P2P to operate on.
     * @throws Throwable If remove service request action timed out or got invalid channel ID.
     */
    @Rpc(description = "Remove a service discovery request.")
    public void wifiP2pRemoveServiceRequest(int index, @RpcDefault(value = "0") Integer channelId)
            throws Throwable {
        WifiP2pManager.Channel channel = getChannel(channelId);
        String callbackId = UUID.randomUUID().toString();
        WifiP2pServiceRequest serviceRequest = mServiceRequests.remove(index);
        if (serviceRequest == null) {
            throw new WifiP2pManagerException("Service request not found. Please use the request ID"
                    + " returned by `wifiP2pAddServiceRequest`.");
        }
        mP2pManager.removeServiceRequest(channel, serviceRequest,
                new ActionListener(callbackId));
        verifyActionListenerSucceed(callbackId);
    }

    /**
     * Clear all registered service discovery requests.
     *
     * @param channelId The ID of the channel for Wi-Fi P2P to operate on.
     * @throws Throwable If clear service requests action timed out or got invalid channel ID.
     */
    @Rpc(description = "Clear all registered service discovery requests.")
    public void wifiP2pClearServiceRequests(@RpcDefault(value = "0") Integer channelId)
            throws Throwable {
        WifiP2pManager.Channel channel = getChannel(channelId);
        String callbackId = UUID.randomUUID().toString();
        mP2pManager.clearServiceRequests(channel, new ActionListener(callbackId));
        waitActionListenerResult(callbackId);
    }

    /**
     * Set a callback to be invoked on receiving Upnp service discovery response.
     *
     * @param callbackId The callback ID assigned by Mobly.
     * @param channelId The ID of the channel for Wi-Fi P2P to operate on.
     * @throws WifiP2pManagerException If the channel is not created.
     */
    @AsyncRpc(description = "Set a callback to be invoked on receiving Upnp service discovery "
            + " response.")
    public void wifiP2pSetUpnpResponseListener(String callbackId,
            @RpcDefault(value = "0") Integer channelId)
            throws WifiP2pManagerException {
        WifiP2pManager.Channel channel = getChannel(channelId);
        mP2pManager.setUpnpServiceResponseListener(channel,
                new UpnpServiceResponseListener(callbackId));
    }

    /**
     * Unset the Upnp service response callback set by `wifiP2pSetUpnpResponseListener`.
     *
     * @param channelId The ID of the channel for Wi-Fi P2P to operate on.
     * @throws WifiP2pManagerException If the channel is not created.
     */
    @Rpc(description = "Unset the Upnp service response callback set by "
            + "`wifiP2pSetUpnpResponseListener`.")
    public void wifiP2pUnsetUpnpResponseListener(@RpcDefault(value = "0") Integer channelId)
            throws WifiP2pManagerException {
        WifiP2pManager.Channel channel = getChannel(channelId);
        mP2pManager.setUpnpServiceResponseListener(channel, null);
    }

    /**
     * Set a callback to be invoked on receiving Bonjour service discovery response.
     *
     * @param callbackId The callback ID assigned by Mobly.
     * @param channelId The ID of the channel for Wi-Fi P2P to operate on.
     * @throws WifiP2pManagerException If the channel is not created.
     */
    @AsyncRpc(description = "Set a callback to be invoked on receiving Bonjour service discovery"
            + " response.")
    public void wifiP2pSetDnsSdResponseListeners(String callbackId,
            @RpcDefault(value = "0") Integer channelId)
            throws WifiP2pManagerException {
        WifiP2pManager.Channel channel = getChannel(channelId);
        mP2pManager.setDnsSdResponseListeners(channel, new DnsSdServiceResponseListener(callbackId),
                new DnsSdTxtRecordListener(callbackId));
    }

    /**
     * Unset the Bonjour service response callback set by `wifiP2pSetDnsSdResponseListeners`.
     *
     * @param channelId The ID of the channel for Wi-Fi P2P to operate on.
     * @throws WifiP2pManagerException If the channel is not created.
     */
    @Rpc(description = "Unset the Bonjour service response callback set by "
            + "`wifiP2pSetDnsSdResponseListeners`.")
    public void wifiP2pUnsetDnsSdResponseListeners(@RpcDefault(value = "0") Integer channelId)
            throws WifiP2pManagerException {
        WifiP2pManager.Channel channel = getChannel(channelId);
        mP2pManager.setDnsSdResponseListeners(channel, null, null);
    }

    /**
     * Initiate service discovery.
     *
     * @param channelId The ID of the channel for Wi-Fi P2P to operate on.
     * @throws Throwable If the P2P operation failed or timed out, or got invalid channel ID.
     */
    @Rpc(description = "Initiate service discovery.")
    public void wifiP2pDiscoverServices(
            @RpcDefault(value = "0") Integer channelId
    ) throws Throwable {
        WifiP2pManager.Channel channel = getChannel(channelId);
        String callbackId = UUID.randomUUID().toString();
        mP2pManager.discoverServices(channel, new ActionListener(callbackId));
        verifyActionListenerSucceed(callbackId);
    }

    /**
     * Close the current P2P connection and indicate to the P2P service that connections created by
     * the app can be removed.
     */
    @Rpc(description = "Close all P2P connections and indicate to the P2P service that"
            + " connections created by the app can be removed.")
    public void p2pClose() {
        for (Map.Entry<Integer, WifiP2pManager.Channel> entry : mChannels.entrySet()) {
            Integer channelId = entry.getKey();
            WifiP2pManager.Channel channel = entry.getValue();
            Log.d("Cleaning p2p resources associated with channelId=" + channelId);
            if (channel != null) {
                try {
                    wifiP2pClearServiceRequests(channelId);
                } catch (Throwable e) {
                    Log.e("Failed to clear service requests on channelId=" + channelId
                            + ", error message: " + e.getMessage());
                }
                mP2pManager.setDnsSdResponseListeners(channel, null, null);
                mP2pManager.setUpnpServiceResponseListener(channel, null);
                try {
                    wifiP2pClearLocalServices(channelId);
                } catch (Throwable e) {
                    Log.e("Failed to clear local services on channelId=" + channelId
                            + ", error message: " + e.getMessage());
                }
                channel.close();
            }
        }
        mChannels.clear();
        mChannelCnt = -1;
        if (mStateChangedReceiver != null) {
            mContext.unregisterReceiver(mStateChangedReceiver);
            mStateChangedReceiver = null;
        }
    }

    @Override
    public void shutdown() {
        p2pClose();
    }

    private class WifiP2pStateChangedReceiver extends BroadcastReceiver {
        private String mCallbackId;

        private WifiP2pStateChangedReceiver(@NonNull String callbackId) {
            this.mCallbackId = callbackId;
        }

        @Override
        public void onReceive(Context mContext, Intent intent) {
            String action = intent.getAction();
            SnippetEvent event = new SnippetEvent(mCallbackId, action);
            String logPrefix = TAG + ": WifiP2pStateChangedReceiver: onReceive: Got intent: action="
                    + action + ", ";
            switch (action) {
                case WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION:
                    int wifiP2pState = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, 0);
                    Log.d(logPrefix + "wifiP2pState=" + wifiP2pState);
                    event.getData().putInt(WifiP2pManager.EXTRA_WIFI_STATE, wifiP2pState);
                    break;
                case WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION:
                    WifiP2pDeviceList peerList = (WifiP2pDeviceList) intent.getParcelableExtra(
                            WifiP2pManager.EXTRA_P2P_DEVICE_LIST);
                    Log.d(logPrefix + "p2pPeerList=" + BundleUtils.fromWifiP2pDeviceList(peerList));
                    event.getData().putParcelableArrayList(
                            EVENT_KEY_PEER_LIST, BundleUtils.fromWifiP2pDeviceList(peerList));
                    break;
                case WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION:
                    NetworkInfo networkInfo = intent.getParcelableExtra(
                            WifiP2pManager.EXTRA_NETWORK_INFO);
                    WifiP2pInfo p2pInfo = (WifiP2pInfo) intent.getParcelableExtra(
                            WifiP2pManager.EXTRA_WIFI_P2P_INFO);
                    WifiP2pGroup p2pGroup = (WifiP2pGroup) intent.getParcelableExtra(
                            WifiP2pManager.EXTRA_WIFI_P2P_GROUP);
                    Log.d(logPrefix + "networkInfo=" + String.valueOf(networkInfo) + ", p2pInfo="
                            + String.valueOf(p2pInfo) + ", p2pGroup=" + String.valueOf(p2pGroup));
                    if (networkInfo != null) {
                        event.getData().putBoolean("isConnected", networkInfo.isConnected());
                    } else {
                        event.getData().putBoolean("isConnected", false);
                    }
                    event.getData().putBundle(
                            EVENT_KEY_P2P_INFO, BundleUtils.fromWifiP2pInfo(p2pInfo));
                    event.getData().putBundle(
                            EVENT_KEY_P2P_GROUP, BundleUtils.fromWifiP2pGroup(p2pGroup));
                    break;
            }
            EventCache.getInstance().postEvent(event);
        }
    }

    private static class ActionListener implements WifiP2pManager.ActionListener {
        public static final String CALLBACK_EVENT_NAME = "WifiP2pManagerActionListenerCallback";

        private final String mCallbackId;

        ActionListener(String callbackId) {
            this.mCallbackId = callbackId;
        }

        @Override
        public void onSuccess() {
            SnippetEvent event = new SnippetEvent(mCallbackId, CALLBACK_EVENT_NAME);
            event.getData().putString(EVENT_KEY_CALLBACK_NAME, ACTION_LISTENER_ON_SUCCESS);
            EventCache.getInstance().postEvent(event);
        }

        @Override
        public void onFailure(int reason) {
            SnippetEvent event = new SnippetEvent(mCallbackId, CALLBACK_EVENT_NAME);
            event.getData().putString(EVENT_KEY_CALLBACK_NAME, ACTION_LISTENER_ON_FAILURE);
            event.getData().putInt(EVENT_KEY_REASON, reason);
            EventCache.getInstance().postEvent(event);
        }
    }

    private static class  WifiP2pConnectionInfoListener
        implements WifiP2pManager.ConnectionInfoListener {
        public static final String EVENT_NAME_ON_CONNECTION_INFO =
            "WifiP2pOnConnectionInfoAvailable";
        private final String mCallbackId;

        public WifiP2pConnectionInfoListener(String callbackId) {
            this.mCallbackId = callbackId;
        }

        @Override
        public void onConnectionInfoAvailable(WifiP2pInfo info) {
            Log.d(TAG + ": onConnectionInfoAvailable: " + info.toString());
            SnippetEvent event = new SnippetEvent(mCallbackId, EVENT_NAME_ON_CONNECTION_INFO);
            event.getData().putBoolean("groupFormed", info.groupFormed);
            event.getData().putBoolean("isGroupOwner", info.isGroupOwner);
            event.getData().putString("groupOwnerHostAddress", info.groupOwnerAddress.toString());
            EventCache.getInstance().postEvent(event);
        }
    }

    private static class DeviceInfoListener implements WifiP2pManager.DeviceInfoListener {
        public static final String EVENT_NAME_ON_DEVICE_INFO = "WifiP2pOnDeviceInfoAvailable";

        private final String mCallbackId;

        DeviceInfoListener(String callbackId) {
            this.mCallbackId = callbackId;
        }

        @Override
        public void onDeviceInfoAvailable(WifiP2pDevice device) {
            if (device == null) {
                return;
            }
            Log.d(TAG + ": onDeviceInfoAvailable: " + device.toString());
            SnippetEvent event = new SnippetEvent(mCallbackId, EVENT_NAME_ON_DEVICE_INFO);
            event.getData().putBundle(EVENT_KEY_P2P_DEVICE, BundleUtils.fromWifiP2pDevice(device));
            EventCache.getInstance().postEvent(event);
        }
    }

    private static class PeerListListener implements WifiP2pManager.PeerListListener {
        private final String mCallbackId;

        PeerListListener(String callbackId) {
            this.mCallbackId = callbackId;
        }

        @Override
        public void onPeersAvailable(WifiP2pDeviceList newPeers) {
            Log.d(TAG + ": onPeersAvailable: " + newPeers.getDeviceList());
            ArrayList<Bundle> devices = BundleUtils.fromWifiP2pDeviceList(newPeers);
            SnippetEvent event = new SnippetEvent(mCallbackId, "WifiP2pOnPeersAvailable");
            event.getData().putParcelableArrayList(EVENT_KEY_PEER_LIST, devices);
            event.getData().putLong(EVENT_KEY_TIMESTAMP_MS, System.currentTimeMillis());
            EventCache.getInstance().postEvent(event);
        }
    }

    private static class PersistentGroupInfoListener
            implements WifiP2pManager.PersistentGroupInfoListener {
        private final String mCallbackId;

        PersistentGroupInfoListener(String callbackId) {
            this.mCallbackId = callbackId;
        }

        @Override
        public void onPersistentGroupInfoAvailable(@NonNull WifiP2pGroupList groups) {
            Log.d(TAG + ": onPersistentGroupInfoAvailable: " + groups.toString());
            SnippetEvent event = new SnippetEvent(mCallbackId, "onPersistentGroupInfoAvailable");
            event.getData()
                    .putParcelableArrayList("groupList", BundleUtils.fromWifiP2pGroupList(groups));
            EventCache.getInstance().postEvent(event);
        }
    }

    private static class UpnpServiceResponseListener
            implements WifiP2pManager.UpnpServiceResponseListener {
        private final String mCallbackId;

        UpnpServiceResponseListener(String callbackId) {
            this.mCallbackId = callbackId;
        }

        @Override
        public void onUpnpServiceAvailable(List<String> uniqueServiceNames,
                WifiP2pDevice srcDevice) {
            Log.d(TAG + ": onUpnpServiceAvailable: service names: " + uniqueServiceNames);
            SnippetEvent event = new SnippetEvent(mCallbackId, "onUpnpServiceAvailable");
            event.getData()
                    .putBundle(EVENT_KEY_SOURCE_DEVICE, BundleUtils.fromWifiP2pDevice(srcDevice));
            event.getData()
                    .putStringArrayList(EVENT_KEY_SERVICE_LIST, new ArrayList(uniqueServiceNames));
            EventCache.getInstance().postEvent(event);
        }
    }

    private static class DnsSdServiceResponseListener
            implements WifiP2pManager.DnsSdServiceResponseListener {
        private final String mCallbackId;

        DnsSdServiceResponseListener(String callbackId) {
            this.mCallbackId = callbackId;
        }

        @Override
        public void onDnsSdServiceAvailable(String instanceName, String registrationType,
                WifiP2pDevice srcDevice) {
            SnippetEvent event = new SnippetEvent(mCallbackId, "onDnsSdServiceAvailable");
            event.getData().putString(EVENT_KEY_INSTANCE_NAME, instanceName);
            event.getData().putString(EVENT_KEY_REGISTRATION_TYPE, registrationType);
            event.getData()
                    .putBundle(EVENT_KEY_SOURCE_DEVICE, BundleUtils.fromWifiP2pDevice(srcDevice));
            EventCache.getInstance().postEvent(event);
        }
    }

    private static class DnsSdTxtRecordListener implements WifiP2pManager.DnsSdTxtRecordListener {
        private final String mCallbackId;

        DnsSdTxtRecordListener(String callbackId) {
            this.mCallbackId = callbackId;
        }

        @Override
        public void onDnsSdTxtRecordAvailable(String fullDomainName,
                Map<String, String> txtRecordMap, WifiP2pDevice srcDevice) {
            SnippetEvent event = new SnippetEvent(mCallbackId, "onDnsSdTxtRecordAvailable");
            event.getData().putString(EVENT_KEY_FULL_DOMAIN_NAME, fullDomainName);
            Bundle txtMap = new Bundle();
            for (String key : txtRecordMap.keySet()) {
                txtMap.putString(key, txtRecordMap.get(key));
            }
            event.getData().putBundle(EVENT_KEY_TXT_RECORD_MAP, txtMap);
            event.getData()
                    .putBundle(EVENT_KEY_SOURCE_DEVICE, BundleUtils.fromWifiP2pDevice(srcDevice));
            EventCache.getInstance().postEvent(event);
        }
    }

    /**
     * Get the channel by channel ID.
     *
     * @param channelId The ID of the channel for Wi-Fi P2P to operate on.
     * @return The channel.
     * @throws WifiP2pManagerException If the channel is not created.
     */
    private WifiP2pManager.Channel getChannel(int channelId)
            throws WifiP2pManagerException {
        WifiP2pManager.Channel channel = mChannels.get(channelId);
        if (channel == null) {
            Log.e(TAG + ": getChannel : channel keys" + mChannels.keySet());
            throw new WifiP2pManagerException(
                    "The channelId " + channelId + " is wrong. Please use the channelId returned "
                            + "by calling `wifiP2pInitialize` or `wifiP2pInitExtraChannel`.");
        }
        return channel;
    }

    /**
     * Check if the device supports Wi-Fi Direct.
     *
     * @throws WifiP2pManagerException If the device does not support Wi-Fi Direct.
     */
    private void checkP2pManager() throws WifiP2pManagerException {
        if (mP2pManager == null) {
            throw new WifiP2pManagerException("Device does not support Wi-Fi Direct.");
        }
    }

    /**
     * Check permissions for the given permissions.
     *
     * @param context The context to check permissions.
     * @param permissions The permissions to check.
     */
    private static void checkPermissions(Context context, String... permissions) {
        for (String permission : permissions) {
            if (context.checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
                throw new SecurityException(
                        "Permission denied (missing " + permission + " permission)");
            }
        }
    }

    /**
     * Wait until any callback of {@link ActionListener} is triggered.
     *
     * @param callbackId The callback ID associated with the action listener.
     * @return The event posted by the callback methods of {@link ActionListener}.
     * @throws Throwable If the action timed out.
     */
    private Bundle waitActionListenerResult(String callbackId) throws Throwable {
        SnippetEvent event = waitForSnippetEvent(callbackId, ActionListener.CALLBACK_EVENT_NAME,
                TIMEOUT_SHORT_MS);
        Log.d("Got action listener result event: " + event.getData().toString());
        return event.getData();
    }

    /**
     * Wait until any callback of {@link ActionListener} is triggered and verify it succeeded.
     *
     * @param callbackId The callback ID associated with the action listener.
     * @throws Throwable If the action timed out or failed.
     */
    private void verifyActionListenerSucceed(String callbackId) throws Throwable {
        Bundle eventData = waitActionListenerResult(callbackId);
        String result = eventData.getString(EVENT_KEY_CALLBACK_NAME);
        if (Objects.equals(ACTION_LISTENER_ON_SUCCESS, result)) {
            return;
        }
        if (Objects.equals(ACTION_LISTENER_ON_FAILURE, result)) {
            // Please keep reason code in error message for client side to check the reason.
            throw new WifiP2pManagerException(
                    "Action failed with reason_code=" + eventData.getInt(EVENT_KEY_REASON)
            );
        }
        throw new WifiP2pManagerException("Action got unknown event: " + eventData.toString());
    }

    /**
     * Wait for a SnippetEvent with the given callbackId and eventName.
     *
     * @param callbackId The callback ID associated with the action listener.
     * @param eventName The event name to wait for.
     * @param timeout The timeout in milliseconds.
     * @return The SnippetEvent.
     * @throws Throwable If the action timed out.
     */
    private static SnippetEvent waitForSnippetEvent(String callbackId, String eventName,
            Integer timeout) throws Throwable {
        String qId = EventCache.getQueueId(callbackId, eventName);
        LinkedBlockingDeque<SnippetEvent> q = EventCache.getInstance().getEventDeque(qId);
        SnippetEvent result;
        try {
            result = q.pollFirst(timeout, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            throw e.getCause();
        }

        if (result == null) {
            throw new TimeoutException(
                    "Timed out waiting(" + timeout + " millis) for SnippetEvent: " + callbackId);
        }
        return result;
    }
}
