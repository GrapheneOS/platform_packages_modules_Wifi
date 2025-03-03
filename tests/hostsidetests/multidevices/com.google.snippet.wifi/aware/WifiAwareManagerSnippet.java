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

package com.google.snippet.wifi.aware;

import android.app.UiAutomation;
import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.NetworkSpecifier;
import android.net.MacAddress;
import android.net.wifi.aware.AttachCallback;
import android.net.wifi.aware.Characteristics;
import android.net.wifi.aware.DiscoverySession;
import android.net.wifi.aware.DiscoverySessionCallback;
import android.net.wifi.aware.IdentityChangedListener;
import android.net.wifi.aware.PeerHandle;
import android.net.wifi.aware.PublishConfig;
import android.net.wifi.aware.PublishDiscoverySession;
import android.net.wifi.aware.ServiceDiscoveryInfo;
import android.net.wifi.aware.SubscribeConfig;
import android.net.wifi.aware.SubscribeDiscoverySession;
import android.net.wifi.aware.WifiAwareManager;
import android.net.wifi.aware.WifiAwareNetworkSpecifier;
import android.net.wifi.aware.WifiAwareSession;
import android.net.wifi.rtt.RangingRequest;
import android.net.wifi.rtt.RangingResult;
import android.net.wifi.rtt.RangingResultCallback;
import android.net.wifi.rtt.WifiRttManager;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.text.TextUtils;
import android.util.Base64;

import android.os.RemoteException;

import androidx.annotation.NonNull;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.platform.app.InstrumentationRegistry;

import com.google.android.mobly.snippet.Snippet;
import com.google.android.mobly.snippet.event.EventCache;
import com.google.android.mobly.snippet.event.SnippetEvent;
import com.google.android.mobly.snippet.rpc.AsyncRpc;
import com.google.android.mobly.snippet.rpc.Rpc;
import com.google.android.mobly.snippet.rpc.RpcOptional;
import com.google.android.mobly.snippet.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.ListIterator;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Snippet class for exposing {@link WifiAwareManager} APIs.
 */
public class WifiAwareManagerSnippet implements Snippet {
    private final Context mContext;
    private final WifiAwareManager mWifiAwareManager;
    private final WifiRttManager mWifiRttManager;
    private final WifiManager mWifiManager;
    private final Handler mHandler;
    // WifiAwareSession will be initialized after attach.
    private final ConcurrentHashMap<String, WifiAwareSession> mAttachSessions =
            new ConcurrentHashMap<>();
    // DiscoverySession will be initialized after publish or subscribe
    private final ConcurrentHashMap<String, DiscoverySession> mDiscoverySessions =
            new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, PeerHandle> mPeerHandles = new ConcurrentHashMap<>();
    private final EventCache eventCache = EventCache.getInstance();
    private WifiAwareStateChangedReceiver stateChangedReceiver;

    /**
     * Custom exception class for handling specific errors related to the WifiAwareManagerSnippet
     * operations.
     */
    private static class WifiAwareManagerSnippetException extends Exception {
        WifiAwareManagerSnippetException(String msg) {
            super(msg);
        }
    }

    public WifiAwareManagerSnippet() throws WifiAwareManagerSnippetException {
        mContext = ApplicationProvider.getApplicationContext();
        PermissionUtils.checkPermissions(mContext, Manifest.permission.ACCESS_WIFI_STATE,
                Manifest.permission.CHANGE_WIFI_STATE, Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.NEARBY_WIFI_DEVICES
        );
        mWifiAwareManager = mContext.getSystemService(WifiAwareManager.class);
        checkWifiAwareManager();
        mWifiRttManager = mContext.getSystemService(WifiRttManager.class);
        mWifiManager = mContext.getSystemService(WifiManager.class);
        HandlerThread handlerThread = new HandlerThread("Snippet-Aware");
        handlerThread.start();
        mHandler = new Handler(handlerThread.getLooper());
    }
    private void adoptShellPermission() throws RemoteException {
        UiAutomation uia = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        uia.adoptShellPermissionIdentity();
    }

    private void dropShellPermission() throws RemoteException {
        UiAutomation uia = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        uia.dropShellPermissionIdentity();
    }

    /**
     * Returns the MAC address of the currently active access point.
     */
    @Rpc(description = "Returns information about the currently active access point.")
    public String wifiGetActiveNetworkMacAddress() throws Exception {
        WifiInfo info = null;
        try {
            adoptShellPermission();
            info = mWifiManager.getConnectionInfo();
        } catch (RemoteException e) {
            Log.e("RemoteException message: " + e);
        } finally {
            // cleanup
            dropShellPermission();
        }
        return info.getMacAddress();
    }

    /**
     * Returns whether Wi-Fi Aware is supported.
     */
    @Rpc(description = "Is Wi-Fi Aware supported.")
    public boolean wifiAwareIsSupported() {
        return mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_WIFI_AWARE);
    }

    /**
     * Returns whether Wi-Fi RTT is supported.
     */
    @Rpc(description = "Is Wi-Fi RTT supported.")
    public boolean wifiAwareIsRttSupported() {
        return mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_WIFI_RTT);
    }

    /**
     * Use {@link WifiAwareManager#attach(AttachCallback, Handler)} to attach to the Wi-Fi Aware.
     *
     * @param callbackId Assigned automatically by mobly. Also will be used as Attach session id for
     *                   further operations
     */
    @AsyncRpc(
            description = "Attach to the Wi-Fi Aware service - enabling the application to "
                    + "create discovery sessions or publish or subscribe to services."
    )
    public void wifiAwareAttach(String callbackId) {
        attach(callbackId, false);
    }

    /**
     * Use {@link WifiAwareManager#attach(AttachCallback, Handler)} to attach to the Wi-Fi Aware.
     *
     * @param callbackId Assigned automatically by mobly. Also will be used as Attach session id for
     *                   further operations
     * @param identityCb If true, the application will be notified of changes to the device's
     */
    @AsyncRpc(
            description = "Attach to the Wi-Fi Aware service - enabling the application to "
                    + "create discovery sessions or publish or subscribe to services."
    )
    public void wifiAwareAttached(String callbackId, boolean identityCb)
            throws WifiAwareManagerSnippetException {
        attach(callbackId, identityCb);
    }

    private void attach(String callbackId, boolean identityCb) {
        AttachCallback attachCallback = new AttachCallback() {
            @Override
            public void onAttachFailed() {
                super.onAttachFailed();
                sendEvent(callbackId, "onAttachFailed");
            }

            @Override
            public void onAttached(WifiAwareSession session) {
                super.onAttached(session);
                mAttachSessions.put(callbackId, session);
                sendEvent(callbackId, "onAttached");

            }

            @Override
            public void onAwareSessionTerminated() {
                super.onAwareSessionTerminated();
                mAttachSessions.remove(callbackId);
                sendEvent(callbackId, "onAwareSessionTerminated");
            }
        };
        if (identityCb) {
            mWifiAwareManager.attach(attachCallback,
                    new AwareIdentityChangeListenerPostsEvents(eventCache, callbackId), mHandler
            );
        } else {
            mWifiAwareManager.attach(attachCallback, mHandler);
        }

    }

    private static class AwareIdentityChangeListenerPostsEvents extends IdentityChangedListener {
        private final EventCache eventCache;
        private final String callbackId;

        public AwareIdentityChangeListenerPostsEvents(EventCache eventCache, String callbackId) {
            this.eventCache = eventCache;
            this.callbackId = callbackId;
        }

        @Override
        public void onIdentityChanged(byte[] mac) {
            SnippetEvent event = new SnippetEvent(callbackId, "WifiAwareAttachOnIdentityChanged");
            event.getData().putLong("timestampMs", System.currentTimeMillis());
            event.getData().putString("mac", MacAddress.fromBytes(mac).toString());
            eventCache.postEvent(event);
            Log.d("WifiAwareattach identity changed called for WifiAwareAttachOnIdentityChanged");
        }
    }

    /**
     * Starts listening for wifiAware state change related broadcasts.
     *
     * @param callbackId the callback id
     */
    @AsyncRpc(description = "Start listening for wifiAware state change related broadcasts.")
    public void wifiAwareMonitorStateChange(String callbackId) {
        stateChangedReceiver = new WifiAwareStateChangedReceiver(eventCache, callbackId);
        IntentFilter filter = new IntentFilter(WifiAwareManager.ACTION_WIFI_AWARE_STATE_CHANGED);
        mContext.registerReceiver(stateChangedReceiver, filter);
    }

    /**
     * Stops listening for wifiAware state change related broadcasts.
     */
    @Rpc(description = "Stop listening for wifiAware state change related broadcasts.")
    public void wifiAwareMonitorStopStateChange() {
        if (stateChangedReceiver != null) {
            mContext.unregisterReceiver(stateChangedReceiver);
            stateChangedReceiver = null;
        }
    }

    class WifiAwareStateChangedReceiver extends BroadcastReceiver {
        private final EventCache eventCache;
        private final String callbackId;

        public WifiAwareStateChangedReceiver(EventCache eventCache, String callbackId) {
            this.eventCache = eventCache;
            this.callbackId = callbackId;
        }

        @Override
        public void onReceive(Context c, Intent intent) {
            boolean isAvailable = mWifiAwareManager.isAvailable();
            SnippetEvent event = new SnippetEvent(callbackId,
                    "WifiAwareState" + (isAvailable ? "Available" : "NotAvailable")
            );
            eventCache.postEvent(event);
        }
    }

    /**
     * Use {@link WifiAwareSession#close()} to detach from the Wi-Fi Aware.
     *
     * @param sessionId The Id of the Aware attach session
     */
    @Rpc(description = "Detach from the Wi-Fi Aware service.")
    public void wifiAwareDetach(String sessionId) {
        WifiAwareSession session = mAttachSessions.remove(sessionId);
        if (session != null) {
            session.close();
        }

    }

    /**
     * Check if Wi-Fi Aware is attached.
     *
     * @param sessionId The Id of the Aware attached event callback id
     */
    @Rpc(description = "Check if Wi-Fi aware is attached")
    public boolean wifiAwareIsSessionAttached(String sessionId) {
        return !mAttachSessions.isEmpty() && mAttachSessions.containsKey(sessionId);
    }

    /**
     * Check if Wi-Fi Aware is  pairing supported.
     */
    @Rpc(description = "Check if Wi-Fi aware pairing is available")
    public Boolean wifiAwareIsAwarePairingSupported() throws WifiAwareManagerSnippetException {
        checkWifiAwareManager();
        Characteristics characteristics = mWifiAwareManager.getCharacteristics();
        if (characteristics == null) {
            throw new WifiAwareManagerSnippetException(
                    "Can not get Wi-Fi Aware characteristics. Possible reasons include: 1. The "
                            + "Wi-Fi Aware service is not initialized. Please call "
                            + "attachWifiAware first. 2. The device does not support Wi-Fi Aware."
                            + " Check the device's hardware and driver Wi-Fi Aware support.");

        }
        return characteristics.isAwarePairingSupported();
    }


    /**
     * Check if Wi-Fi Aware services is available.
     */
    private void checkWifiAwareManager() throws WifiAwareManagerSnippetException {
        if (mWifiAwareManager == null) {
            throw new WifiAwareManagerSnippetException("Device does not support Wi-Fi Aware.");
        }
    }

    /**
     * Checks if Wi-Fi RTT Manager has been set.
     */
    private void checkWifiRttManager() throws WifiAwareManagerSnippetException {
        if (mWifiRttManager == null) {
            throw new WifiAwareManagerSnippetException("Device does not support Wi-Fi Rtt.");
        }
    }

    /**
     * Checks if Wi-Fi RTT is available.
     */
    @Rpc(description = "Check if Wi-Fi Aware is available")
    public Boolean wifiRttIsAvailable() {
        return mWifiRttManager.isAvailable();
    }
    private void checkWifiRttAvailable() throws WifiAwareManagerSnippetException {
        if (!mWifiRttManager.isAvailable()) {
            throw new WifiAwareManagerSnippetException("WiFi RTT is not available now.");
        }
    }

    /**
     * Check if Wi-Fi Aware is available.
     */
    @Rpc(description = "Check if Wi-Fi Aware is available")
    public Boolean wifiAwareIsAvailable() {
        return mWifiAwareManager.isAvailable();
    }

    /**
     * Send callback event of current method
     */
    private void sendEvent(String callbackId, String methodName) {
        SnippetEvent event = new SnippetEvent(callbackId, methodName);
        EventCache.getInstance().postEvent(event);
    }

    class WifiAwareDiscoverySessionCallback extends DiscoverySessionCallback {

        String mCallBackId = "";

        WifiAwareDiscoverySessionCallback(String callBackId) {
            this.mCallBackId = callBackId;
        }

        private void putMatchFilterData(List<byte[]> matchFilter, SnippetEvent event) {
            Bundle[] matchFilterBundle = new Bundle[matchFilter.size()];
            int index = 0;
            for (byte[] filter : matchFilter) {
                Bundle bundle = new Bundle();
                bundle.putByteArray("value", filter);
                matchFilterBundle[index] = bundle;
                index++;
            }
            event.getData().putParcelableArray("matchFilter", matchFilterBundle);
        }

        @Override
        public void onPublishStarted(PublishDiscoverySession session) {
            mDiscoverySessions.put(mCallBackId, session);
            SnippetEvent snippetEvent = new SnippetEvent(mCallBackId, "discoveryResult");
            snippetEvent.getData().putString("callbackName", "onPublishStarted");
            snippetEvent.getData().putBoolean("isSessionInitialized", session != null);
            EventCache.getInstance().postEvent(snippetEvent);
        }

        @Override
        public void onSubscribeStarted(SubscribeDiscoverySession session) {
            mDiscoverySessions.put(mCallBackId, session);
            SnippetEvent snippetEvent = new SnippetEvent(mCallBackId, "discoveryResult");
            snippetEvent.getData().putString("callbackName", "onSubscribeStarted");
            snippetEvent.getData().putBoolean("isSessionInitialized", session != null);
            EventCache.getInstance().postEvent(snippetEvent);
        }

        @Override
        public void onSessionConfigUpdated() {
            sendEvent(mCallBackId, "onSessionConfigUpdated");
        }

        @Override
        public void onSessionConfigFailed() {
            sendEvent(mCallBackId, "onSessionConfigFailed");
        }

        @Override
        public void onSessionTerminated() {
            sendEvent(mCallBackId, "onSessionTerminated");
        }

        @Override
        public void onServiceDiscovered(ServiceDiscoveryInfo info) {
            mPeerHandles.put(info.getPeerHandle().hashCode(), info.getPeerHandle());
            SnippetEvent event = new SnippetEvent(mCallBackId, "onServiceDiscovered");
            event.getData().putByteArray("serviceSpecificInfo", info.getServiceSpecificInfo());
            event.getData().putString("pairedAlias", info.getPairedAlias());
            event.getData().putInt("peerId", info.getPeerHandle().hashCode());
            List<byte[]> matchFilter = info.getMatchFilters();
            putMatchFilterData(matchFilter, event);
            EventCache.getInstance().postEvent(event);
        }

        @Override
        public void onServiceDiscoveredWithinRange(
                PeerHandle peerHandle, byte[] serviceSpecificInfo, List<byte[]> matchFilter,
                int distanceMm
        ) {
            mPeerHandles.put(peerHandle.hashCode(), peerHandle);
            SnippetEvent event = new SnippetEvent(mCallBackId, "onServiceDiscoveredWithinRange");
            event.getData().putByteArray("serviceSpecificInfo", serviceSpecificInfo);
            event.getData().putInt("distanceMm", distanceMm);
            event.getData().putInt("peerId", peerHandle.hashCode());
            putMatchFilterData(matchFilter, event);
            EventCache.getInstance().postEvent(event);
        }

        @Override
        public void onMessageSendSucceeded(int messageId) {
            SnippetEvent event = new SnippetEvent(mCallBackId, "messageSendResult");
            event.getData().putString("callbackName", "onMessageSendSucceeded");
            event.getData().putInt("messageId", messageId);
            EventCache.getInstance().postEvent(event);
        }

        @Override
        public void onMessageSendFailed(int messageId) {
            SnippetEvent event = new SnippetEvent(mCallBackId, "messageSendResult");
            event.getData().putString("callbackName", "onMessageSendFailed");
            event.getData().putInt("messageId", messageId);
            EventCache.getInstance().postEvent(event);
        }

        @Override
        public void onMessageReceived(PeerHandle peerHandle, byte[] message) {
            mPeerHandles.put(peerHandle.hashCode(), peerHandle);
            SnippetEvent event = new SnippetEvent(mCallBackId, "onMessageReceived");
            event.getData().putByteArray("receivedMessage", message);
            event.getData().putInt("peerId", peerHandle.hashCode());
            EventCache.getInstance().postEvent(event);
        }

        @Override
        public void onPairingSetupRequestReceived(PeerHandle peerHandle, int requestId) {
            SnippetEvent event = new SnippetEvent(mCallBackId, "onPairingSetupRequestReceived");
            event.getData().putInt("pairingRequestId", requestId);
            event.getData().putInt("peerId", peerHandle.hashCode());
            EventCache.getInstance().postEvent(event);
        }

        @Override
        public void onPairingSetupSucceeded(PeerHandle peerHandle, String alias) {
            SnippetEvent event = new SnippetEvent(mCallBackId, "onPairingSetupSucceeded");
            event.getData().putString("pairedAlias", alias);
            event.getData().putInt("peerId", peerHandle.hashCode());
            EventCache.getInstance().postEvent(event);
        }

        @Override
        public void onPairingSetupFailed(PeerHandle peerHandle) {
            SnippetEvent event = new SnippetEvent(mCallBackId, "onPairingSetupFailed");
            event.getData().putInt("peerId", peerHandle.hashCode());
            EventCache.getInstance().postEvent(event);
        }

        @Override
        public void onPairingVerificationSucceed(
                @NonNull PeerHandle peerHandle, @NonNull String alias
        ) {
            super.onPairingVerificationSucceed(peerHandle, alias);
            SnippetEvent event = new SnippetEvent(mCallBackId, "onPairingVerificationSucceed");
            event.getData().putString("pairedAlias", alias);
            event.getData().putInt("peerId", peerHandle.hashCode());
            EventCache.getInstance().postEvent(event);
        }

        @Override
        public void onPairingVerificationFailed(PeerHandle peerHandle) {
            SnippetEvent event = new SnippetEvent(mCallBackId, "onPairingVerificationFailed");
            event.getData().putInt("peerId", peerHandle.hashCode());
            EventCache.getInstance().postEvent(event);
        }

        @Override
        public void onBootstrappingSucceeded(PeerHandle peerHandle, int method) {
            SnippetEvent event = new SnippetEvent(mCallBackId, "onBootstrappingSucceeded");
            event.getData().putInt("bootstrappingMethod", method);
            event.getData().putInt("peerId", peerHandle.hashCode());
            EventCache.getInstance().postEvent(event);
        }

        @Override
        public void onBootstrappingFailed(PeerHandle peerHandle) {
            SnippetEvent event = new SnippetEvent(mCallBackId, "onBootstrappingFailed");
            event.getData().putInt("peerId", peerHandle.hashCode());
            EventCache.getInstance().postEvent(event);
        }

        @Override
        public void onServiceLost(PeerHandle peerHandle, int reason) {
            SnippetEvent event = new SnippetEvent(mCallBackId, "WifiAwareSessionOnServiceLost");
            event.getData().putString("discoverySessionId", mCallBackId);
            event.getData().putInt("peerId", peerHandle.hashCode());
            event.getData().putInt("lostReason", reason);
            EventCache.getInstance().postEvent(event);
        }
    }

    private WifiAwareSession getWifiAwareSession(String sessionId)
            throws WifiAwareManagerSnippetException {
        WifiAwareSession session = mAttachSessions.get(sessionId);
        if (session == null) {
            throw new WifiAwareManagerSnippetException(
                    "Wi-Fi Aware session is not attached. Please call wifiAwareAttach first.");
        }
        return session;
    }


    /**
     * Creates a new Aware subscribe discovery session. For Android T and later, this method
     * requires NEARBY_WIFI_DEVICES permission and user permission flag "neverForLocation". For
     * earlier versions, this method requires NEARBY_WIFI_DEVICES and ACCESS_FINE_LOCATION
     * permissions.
     *
     * @param sessionId       The Id of the Aware attach session, should be the callbackId from
     *                        {@link #wifiAwareAttach(String)}
     * @param callbackId      Assigned automatically by mobly. Also will be used as discovery
     *                        session id for further operations
     * @param subscribeConfig Defines the subscription configuration via WifiAwareJsonDeserializer.
     */
    @AsyncRpc(
            description = "Create a Wi-Fi Aware subscribe discovery session and handle callbacks."
    )
    public void wifiAwareSubscribe(
            String callbackId, String sessionId, SubscribeConfig subscribeConfig
    ) throws JSONException, WifiAwareManagerSnippetException {
        WifiAwareSession session = getWifiAwareSession(sessionId);
        Log.v("Creating a new Aware subscribe session with config: " + subscribeConfig.toString());
        WifiAwareDiscoverySessionCallback myDiscoverySessionCallback =
                new WifiAwareDiscoverySessionCallback(callbackId);
        session.subscribe(subscribeConfig, myDiscoverySessionCallback, mHandler);
    }

    /**
     * Creates a new Aware publish discovery session. Requires NEARBY_WIFI_DEVICES (with
     * neverForLocation) or ACCESS_FINE_LOCATION for Android TIRAMISU+. ACCESS_FINE_LOCATION is
     * required for earlier versions.
     *
     * @param sessionId     The Id of the Aware attach session, should be the callbackId from
     *                      {@link #wifiAwareAttach(String)}
     * @param callbackId    Assigned automatically by mobly. Also will be used as discovery session
     *                      id for further operations
     * @param publishConfig Defines the publish configuration via WifiAwareJsonDeserializer.
     */
    @AsyncRpc(description = "Create a Wi-Fi Aware publish discovery session and handle callbacks.")
    public void wifiAwarePublish(String callbackId, String sessionId, PublishConfig publishConfig)
            throws JSONException, WifiAwareManagerSnippetException {
        WifiAwareSession session = getWifiAwareSession(sessionId);
        Log.v("Creating a new Aware publish session with config: " + publishConfig.toString());
        WifiAwareDiscoverySessionCallback myDiscoverySessionCallback =
                new WifiAwareDiscoverySessionCallback(callbackId);
        session.publish(publishConfig, myDiscoverySessionCallback, mHandler);
    }

    private PeerHandle getPeerHandler(int peerId) throws WifiAwareManagerSnippetException {
        PeerHandle handle = mPeerHandles.get(peerId);
        if (handle == null) {
            throw new WifiAwareManagerSnippetException(
                    "GetPeerHandler failed. Please call publish or subscribe method, error "
                            + "peerId: " + peerId + ", mPeerHandles: " + mPeerHandles);
        }
        return handle;
    }

    private DiscoverySession getDiscoverySession(String discoverySessionId)
            throws WifiAwareManagerSnippetException {
        DiscoverySession session = mDiscoverySessions.get(discoverySessionId);
        if (session == null) {
            throw new WifiAwareManagerSnippetException(
                    "GetDiscoverySession failed. Please call publish or subscribe method, "
                            + "error discoverySessionId: " + discoverySessionId
                            + ", mDiscoverySessions: " + mDiscoverySessions);
        }
        return session;

    }

    /**
     * Sends a message to a peer using Wi-Fi Aware.
     *
     * <p>This method sends a specified message to a peer device identified by a peer handle
     * in an ongoing Wi-Fi Aware discovery session. The message is sent asynchronously, and the
     * method waits for the send status to confirm whether the message was successfully sent or if
     * any errors occurred.</p>
     *
     * <p>Before sending the message, this method checks if there is an active discovery
     * session. If there is no active session, it throws a
     * {@link WifiAwareManagerSnippetException}.</p>
     *
     * @param discoverySessionId The Id of the discovery session, should be the callbackId from
     *                           publish/subscribe action
     * @param peerId             identifier for the peer handle
     * @param messageId          an integer representing the message ID, which is used to track the
     *                           message.
     * @param message            a {@link String} containing the message to be sent.
     * @throws WifiAwareManagerSnippetException if there is no active discovery session or if
     *                                          sending the message fails.
     * @see android.net.wifi.aware.DiscoverySession#sendMessage
     * @see android.net.wifi.aware.PeerHandle
     * @see java.nio.charset.StandardCharsets#UTF_8
     */
    @Rpc(description = "Send a message to a peer using Wi-Fi Aware.")
    public void wifiAwareSendMessage(
            String discoverySessionId, int peerId, int messageId, String message
    ) throws WifiAwareManagerSnippetException {
        // 4. send message & wait for send status
        DiscoverySession session = getDiscoverySession(discoverySessionId);
        PeerHandle handle = getPeerHandler(peerId);
        session.sendMessage(handle, messageId, message.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Closes the current Wi-Fi Aware discovery session if it is active.
     *
     * <p>This method checks if there is an active discovery session. If so,
     * it closes the session and sets the session object to null. This ensures that resources are
     * properly released and the session is cleanly terminated.</p>
     *
     * @param discoverySessionId The Id of the discovery session
     */
    @Rpc(description = "Close the current Wi-Fi Aware discovery session.")
    public void wifiAwareCloseDiscoverSession(String discoverySessionId) {
        DiscoverySession session = mDiscoverySessions.remove(discoverySessionId);
        if (session != null) {
            session.close();
        }
    }

    /**
     * Closes all Wi-Fi Aware session if it is active. And clear all cache sessions
     */
    @Rpc(description = "Close the current Wi-Fi Aware session.")
    public void wifiAwareCloseAllWifiAwareSession() {
        for (WifiAwareSession session : mAttachSessions.values()) {
            session.close();
        }
        mAttachSessions.clear();
        mDiscoverySessions.clear();
        mPeerHandles.clear();
    }

    /**
     * Creates a Wi-Fi Aware network specifier for requesting network through connectivityManager.
     *
     * @param discoverySessionId The Id of the discovery session,
     * @param peerId             The Id of the peer handle
     * @param isAcceptAnyPeer    A boolean value indicating whether the network specifier should
     * @return a {@link String} containing the network specifier encoded as a Base64 string.
     * @throws JSONException                    if there is an error parsing the JSON object.
     * @throws WifiAwareManagerSnippetException if there is an error creating the network
     *                                          specifier.
     */
    @Rpc(
            description = "Create a network specifier to be used when specifying a Aware network "
                    + "request"
    )
    public String wifiAwareCreateNetworkSpecifier(
            String discoverySessionId, Integer peerId, boolean isAcceptAnyPeer,
            @RpcOptional JSONObject jsonObject
    ) throws JSONException, WifiAwareManagerSnippetException {
        DiscoverySession session = getDiscoverySession(discoverySessionId);
        PeerHandle handle = null;
        if (peerId != null && peerId > 0){
            handle = getPeerHandler(peerId);
        }
        WifiAwareNetworkSpecifier.Builder builder;
        if (isAcceptAnyPeer) {
            builder = new WifiAwareNetworkSpecifier.Builder((PublishDiscoverySession) session);
        } else {
            builder = new WifiAwareNetworkSpecifier.Builder(session, handle);
        }
        WifiAwareNetworkSpecifier specifier =
                WifiAwareJsonDeserializer.jsonToNetworkSpecifier(jsonObject, builder);
        return SerializationUtil.parcelableToString(specifier);
    }

    /**
     * Creates a oob NetworkSpecifier for requesting a Wi-Fi Aware network via ConnectivityManager.
     *
     * @param sessionId The Id of the AwareSession session,
     * @param role             The role of this device: AwareDatapath Role.
     * @param macAddress    The MAC address of the peer's Aware discovery interface.
     * @return A {@link NetworkSpecifier}  to be used to construct
     * @throws WifiAwareManagerSnippetException if there is an error creating the network
     *                                          specifier.
     */
    @Rpc(
            description = "Create a oob network specifier to be used when specifying a Aware "
                    + "network request"
    )
    public NetworkSpecifier createNetworkSpecifierOob(String sessionId, int role, String macAddress,
        String passphrase, String pmk)
            throws WifiAwareManagerSnippetException {
            WifiAwareSession session = getWifiAwareSession(sessionId);
             NetworkSpecifier specifier = null;
            byte[] peermac = null;
            byte[] pmkDecoded = null;
            if (!TextUtils.isEmpty(pmk)){
                pmkDecoded = Base64.decode(pmk, Base64.DEFAULT);
            }
            if (macAddress != null) {
                peermac = MacAddress.fromString(macAddress).toByteArray();
            }
            if (passphrase != null && !passphrase.isEmpty()) {
                specifier = session.createNetworkSpecifierPassphrase(role, peermac, passphrase);
            }
            else if (pmk != null) {
                specifier = session.createNetworkSpecifierPmk(role, peermac, pmkDecoded);
            }
            else if (peermac != null){
                specifier = session.createNetworkSpecifierOpen(role, peermac);
            } else {
            throw new WifiAwareManagerSnippetException(
                "At least one of passphrase, or macAddress must be provided.");
            }
            return specifier;
    }

    @Override
    public void shutdown() throws Exception {
        wifiAwareCloseAllWifiAwareSession();
    }

    /**
     * Returns the characteristics of the WiFi Aware interface.
     *
     * @return WiFi Aware characteristics
     */
    @Rpc(description = "Get the characteristics of the WiFi Aware interface.")
    public Characteristics getCharacteristics() {
        return mWifiAwareManager.getCharacteristics();
    }

    /**
     * Creates a wifiAwareUpdatePublish discovery session. Requires NEARBY_WIFI_DEVICES (with
     * neverForLocation) or ACCESS_FINE_LOCATION for Android TIRAMISU+. ACCESS_FINE_LOCATION is
     * required for earlier versions.
     *
     * @param sessionId     The Id of the Aware attach session, should be the callbackId from
     *                      {@link #wifiAwareAttach(String)}
     * @param publishConfig Defines the publish configuration via WifiAwareJsonDeserializer.
     */
    @Rpc(description = "Create a wifiAwareUpdatePublish discovery session and handle callbacks.")
    public void wifiAwareUpdatePublish(String sessionId, PublishConfig publishConfig)
            throws JSONException, WifiAwareManagerSnippetException, IllegalArgumentException {
        DiscoverySession session = getDiscoverySession(sessionId);
        if (session == null) {
            throw new IllegalStateException(
                    "Calling wifiAwareUpdatePublish before session (session ID " + sessionId
                            + ") is ready");
        }
        if (!(session instanceof PublishDiscoverySession)) {
            throw new IllegalArgumentException(
                    "Calling wifiAwareUpdatePublish with a subscribe session ID");
        }
        Log.v("Updating a  Aware publish session with config: " + publishConfig.toString());

        ((PublishDiscoverySession) session).updatePublish(publishConfig);
    }

    /**
     * Creates a wifiAwareUpdateSubscribe discovery session. For Android T and later, this method
     * requires NEARBY_WIFI_DEVICES permission and user permission flag "neverForLocation". For
     * earlier versions, this method requires NEARBY_WIFI_DEVICES and ACCESS_FINE_LOCATION
     * permissions.
     *
     * @param sessionId       The Id of the Aware attach session, should be the callbackId from
     *                        {@link #wifiAwareAttach(String)}
     * @param subscribeConfig Defines the subscription configuration via WifiAwareJsonDeserializer.
     */
    @Rpc(description = "Create a wifiAwareUpdateSubscribe discovery session and handle callbacks.")
    public void wifiAwareUpdateSubscribe(
            String sessionId, SubscribeConfig subscribeConfig
    ) throws JSONException, WifiAwareManagerSnippetException {
        DiscoverySession session = getDiscoverySession(sessionId);
        if (session == null) {
            throw new IllegalStateException(
                    "Calling wifiAwareUpdateSubscribe before session (session ID " + sessionId
                            + ") is ready");
        }
        if (!(session instanceof SubscribeDiscoverySession)) {
            throw new IllegalArgumentException(
                    "Calling wifiAwareUpdateSubscribe with a publish session ID");
        }
        Log.v("Creating a wifiAwareUpdateSubscribe session with config: "
                + subscribeConfig.toString());
        ((SubscribeDiscoverySession) session).updateSubscribe(subscribeConfig);

    }

    /**
     * Converts a JSON representation of a ScanResult to an actual ScanResult object. Mirror of
     * the code in
     * {@link com.googlecode.android_scripting.jsonrpc.JsonBuilder#buildJsonScanResult(ScanResult)}.
     *
     * @param j JSON object representing a ScanResult.
     * @return a ScanResult object
     * @throws JSONException on any JSON errors
     */
    public static ScanResult getScanResult(JSONObject j) throws JSONException {
        if (j == null) {
            return null;
        }

        ScanResult scanResult = new ScanResult();

        if (j.has("BSSID")) {
            scanResult.BSSID = j.getString("BSSID");
        }
        if (j.has("SSID")) {
            scanResult.SSID = j.getString("SSID");
        }
        if (j.has("frequency")) {
            scanResult.frequency = j.getInt("frequency");
        }
        if (j.has("level")) {
            scanResult.level = j.getInt("level");
        }
        if (j.has("capabilities")) {
            scanResult.capabilities = j.getString("capabilities");
        }
        if (j.has("timestamp")) {
            scanResult.timestamp = j.getLong("timestamp");
        }
        if (j.has("centerFreq0")) {
            scanResult.centerFreq0 = j.getInt("centerFreq0");
        }
        if (j.has("centerFreq1")) {
            scanResult.centerFreq1 = j.getInt("centerFreq1");
        }
        if (j.has("channelWidth")) {
            scanResult.channelWidth = j.getInt("channelWidth");
        }
        if (j.has("operatorFriendlyName")) {
            scanResult.operatorFriendlyName = j.getString("operatorFriendlyName");
        }
        if (j.has("venueName")) {
            scanResult.venueName = j.getString("venueName");
        }

        return scanResult;
    }

    /**
     * Converts a JSONArray toa a list of ScanResult.
     *
     * @param j JSONArray representing a collection of ScanResult objects
     * @return a list of ScanResult objects
     * @throws JSONException on any JSON error
     */
    public static List<ScanResult> getScanResults(JSONArray j) throws JSONException {
        if (j == null || j.length() == 0) {
            return null;
        }

        ArrayList<ScanResult> scanResults = new ArrayList<>(j.length());
        for (int i = 0; i < j.length(); ++i) {
            scanResults.add(getScanResult(j.getJSONObject(i)));
        }

        return scanResults;
    }

    /**
     * Starts Wi-Fi RTT ranging with Wi-Fi Aware access points.
     *
     * @param callbackId        Assigned automatically by mobly for all async RPCs.
     * @param requestJsonObject The ranging request in JSONArray type for calling {@link
     *                          android.net.wifi.ScanResult}.
     */
    @AsyncRpc(description = "Start ranging to an Access Points.")
    public void wifiRttStartRangingToAccessPoints(
            String callbackId, JSONArray requestJsonObject
    ) throws JSONException, WifiAwareManagerSnippetException {
        Log.v("wifiRttStartRangingToAccessPoints: " + requestJsonObject);
        RangingRequest request = new RangingRequest.Builder().addAccessPoints(
                            getScanResults(requestJsonObject)).build();
        Log.v("Starting Wi-Fi RTT ranging with access point: " + request.toString());
        RangingCallback rangingCb = new RangingCallback(eventCache, callbackId);
        mWifiRttManager.startRanging(request, command -> mHandler.post(command), rangingCb);
    }

    /**
     * Starts Wi-Fi RTT ranging with Wi-Fi Aware peers.
     *
     * @param callbackId        Assigned automatically by mobly for all async RPCs.
     * @param requestJsonObject The ranging request in JSONObject type for calling {@link
     *                          android.net.wifi.rtt.WifiRttManager#startRanging startRanging}.
     */
    @AsyncRpc(description = "Start Wi-Fi RTT ranging with Wi-Fi Aware peers.")
    public void wifiAwareStartRanging(
            String callbackId, JSONObject requestJsonObject
    ) throws JSONException, WifiAwareManagerSnippetException {
        checkWifiRttManager();
        checkWifiRttAvailable();
        RangingRequest request = WifiAwareJsonDeserializer.jsonToRangingRequest(
                requestJsonObject, mPeerHandles);
        Log.v("Starting Wi-Fi RTT ranging with config: " + request.toString());
        RangingCallback rangingCb = new RangingCallback(eventCache, callbackId);
        mWifiRttManager.startRanging(request, command -> mHandler.post(command), rangingCb);
    }

    /**
     * Ranging result callback class.
     */
    private static class RangingCallback extends RangingResultCallback {
        private static final String EVENT_NAME_RANGING_RESULT = "WifiRttRangingOnRangingResult";
        private final EventCache mEventCache;
        private final String mCallbackId;

        RangingCallback(EventCache eventCache, String callbackId) {
            this.mEventCache = eventCache;
            this.mCallbackId = callbackId;
        }

        @Override
        public void onRangingFailure(int code) {
            SnippetEvent event = new SnippetEvent(mCallbackId, EVENT_NAME_RANGING_RESULT);
            event.getData().putString("callbackName", "onRangingFailure");
            event.getData().putInt("status", code);
            mEventCache.postEvent(event);
        }

        @Override
        public void onRangingResults(List<RangingResult> results) {
            SnippetEvent event = new SnippetEvent(mCallbackId, EVENT_NAME_RANGING_RESULT);
            event.getData().putString("callbackName", "onRangingResults");

            Bundle[] resultBundles = new Bundle[results.size()];
            for (int i = 0; i < results.size(); i++) {
                RangingResult result = results.get(i);
                resultBundles[i] = new Bundle();
                resultBundles[i].putInt("status", result.getStatus());
                if (result.getStatus() == RangingResult.STATUS_SUCCESS) {
                    resultBundles[i].putInt("distanceMm", result.getDistanceMm());
                    resultBundles[i].putInt("rssi", result.getRssi());
                    resultBundles[i].putInt("distanceStdDevMm", result.getDistanceStdDevMm());
                    resultBundles[i].putInt(
                        "numAttemptedMeasurements",
                        result.getNumAttemptedMeasurements());
                    resultBundles[i].putInt(
                        "numSuccessfulMeasurements",
                        result.getNumSuccessfulMeasurements());
                    resultBundles[i].putByteArray("lci", result.getLci());
                    resultBundles[i].putByteArray("lcr", result.getLcr());
                    resultBundles[i].putLong("timestamp", result.getRangingTimestampMillis());
                }
                PeerHandle peer = result.getPeerHandle();
                if (peer != null) {
                    resultBundles[i].putInt("peerId", peer.hashCode());
                } else {
                    resultBundles[i].putBundle("peerId", null);
                }
                MacAddress mac = result.getMacAddress();
                resultBundles[i].putString("mac", mac != null ? mac.toString() : null);
                resultBundles[i].putString("macAsString",
                    mac != null ? result.getMacAddress().toString() : null);
            }
            event.getData().putParcelableArray("results", resultBundles);
            mEventCache.postEvent(event);
        }
    }

    /**
     * Return whether this device supports setting a channel requirement in a data-path request.
     */
    @Rpc(
            description = "Return whether this device supports setting a channel requirement in a "
                + "data-path request."
    )
    public boolean wifiAwareIsSetChannelOnDataPathSupported() {
        return mWifiAwareManager.isSetChannelOnDataPathSupported();
    }

}

