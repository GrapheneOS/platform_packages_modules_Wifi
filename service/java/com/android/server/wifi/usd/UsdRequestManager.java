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

package com.android.server.wifi.usd;

import android.annotation.NonNull;
import android.annotation.SuppressLint;
import android.app.AlarmManager;
import android.net.MacAddress;
import android.net.wifi.IBooleanListener;
import android.net.wifi.usd.Characteristics;
import android.net.wifi.usd.Config;
import android.net.wifi.usd.IPublishSessionCallback;
import android.net.wifi.usd.ISubscribeSessionCallback;
import android.net.wifi.usd.PublishConfig;
import android.net.wifi.usd.PublishSession;
import android.net.wifi.usd.PublishSessionCallback;
import android.net.wifi.usd.SessionCallback;
import android.net.wifi.usd.SubscribeConfig;
import android.net.wifi.usd.SubscribeSession;
import android.net.wifi.usd.SubscribeSessionCallback;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.util.Log;
import android.util.SparseArray;

import com.android.server.wifi.ActiveModeWarden;
import com.android.server.wifi.Clock;
import com.android.server.wifi.SupplicantStaIfaceHal;
import com.android.server.wifi.WifiThreadRunner;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

import javax.annotation.concurrent.NotThreadSafe;

/**
 * This class UsdRequestManager acts as central point for handling various USD requests from
 * applications such as publish, subscribe, send message, etc. It sends the command to HAL to
 * carry out these actions and expect for callbacks from HAL on various events such as susbcribe/
 * publish started, service discovered, received a message from the peer, etc.
 *
 * <p>Here is how it works,
 * <ul>
 * <li>Role: The UsdRequestManager can act as either a publisher or subscriber
 * <li>Request handling: It manages incoming requests and ensures the new commands are not accepted
 * while a previous subscribe or publish is still awaiting for the response from HAL.
 * <li>Session Management: USD session are organized and tracked using unique session IDs. Each
 * session maintains a collection of USD discovery results which are indexed by the USD peer.
 * <li>USD Peer: A peer is created for discover and also created a unique id (hash) which maps to
 * local session id, remote session id and remote mac address. Applications are given this unique
 * id (hash) on various indications.
 *
 * <p>Essentially, this class streamlines USD communication by managing requests, organizing
 * sessions, and maintaining information about discovered peers. It also enforces a sequential
 * processing of requests to prevent conflicts and ensure reliable communication with HAL.
 * </ul>
 */
@NotThreadSafe
@SuppressLint("NewApi")
public class UsdRequestManager {
    public static final String TAG = "UsdRequestManager";
    private static final int DEFAULT_COMMAND_ID = 100;
    private static final int USD_TEMP_SESSION_ID = 255;
    private static final int INVALID_ID = -1;
    private static final String USD_REQUEST_MANAGER_ALARM_TAG = "UsdRequestManagerAlarmTag";

    /**
     * A unique peer hash (a unique peer id) generator. Application will get the peer hash as the
     * identifier of the peer. Also peer hash is globally mapped to a peer (defined by ownId,
     * peerId and peer mac address).
     */
    private static int sNextPeerHash = 100;
    private final UsdNativeManager mUsdNativeManager;
    private final ActiveModeWarden mActiveModeWarden;
    private SupplicantStaIfaceHal.UsdCapabilitiesInternal mUsdCapabilities;
    private final WifiThreadRunner mWifiThreadRunner;
    private final Clock mClock;
    private enum Role {
        NONE, PUBLISHER, SUBSCRIBER
    }
    private Role mRequesterRole;
    private final AlarmManager mAlarmManager;
    private final AlarmManager.OnAlarmListener mTimeoutListener = () -> {
        startCleaningUpExpiredSessions();
    };
    private static final int TEMP_SESSION_TIMEOUT_MILLIS = 1000;
    private static final int TTL_GAP_MILLIS = 1000;

    private final RemoteCallbackList<IBooleanListener> mPublisherListenerList =
            new RemoteCallbackList<IBooleanListener>();
    private final RemoteCallbackList<IBooleanListener> mSubscriberListenerList =
            new RemoteCallbackList<IBooleanListener>();

    private void startCleaningUpExpiredSessions() {
        long current = mClock.getElapsedSinceBootMillis();
        long nextSchedule = Long.MAX_VALUE;
        long age;
        List<Integer> sessionsToDelete = new ArrayList<>();

        // Cleanup sessions which crossed the TTL.
        for (int i = 0; i < mUsdSessions.size(); i++) {
            UsdSession usdSession = mUsdSessions.valueAt(i);
            int sessionId = mUsdSessions.keyAt(i);
            int ttlMillis = TEMP_SESSION_TIMEOUT_MILLIS;
            if (sessionId != USD_TEMP_SESSION_ID) {
                ttlMillis = ((usdSession.getRole() == Role.PUBLISHER)
                        ? usdSession.mPublishConfig.getTtlSeconds()
                        : usdSession.mSubscribeConfig.getTtlSeconds()) * 1000 + TTL_GAP_MILLIS;
            }
            age = current - usdSession.mCreationTimeMillis;
            if (age >= ttlMillis) {
                sessionsToDelete.add(sessionId);
            } else {
                nextSchedule = Math.min(ttlMillis - age, nextSchedule);
            }
        }

        for (int sessionId : sessionsToDelete) {
            mUsdSessions.get(sessionId).sessionCleanup();
            mUsdSessions.remove(sessionId);
        }

        // Reschedule if necessary.
        if (mUsdSessions.size() > 0 && nextSchedule < Long.MAX_VALUE) {
            mAlarmManager.set(AlarmManager.ELAPSED_REALTIME,
                    mClock.getElapsedSinceBootMillis() + nextSchedule,
                    USD_REQUEST_MANAGER_ALARM_TAG, mTimeoutListener,
                    mWifiThreadRunner.getHandler());
        }
    }

    private void stopCleaningUpExpiredSessions() {
        mAlarmManager.cancel(mTimeoutListener);
    }

    /**
     * A class to represent USD peer. A combination of ownId, peerId and peerMacAddress define a
     * unique peer.
     */
    public static final class UsdPeer {
        public final int ownId;
        public final int peerId;
        public final MacAddress peerMacAddress;

        public UsdPeer(int ownId, int peerId, MacAddress peerMacAddress) {
            this.ownId = ownId;
            this.peerId = peerId;
            this.peerMacAddress = peerMacAddress;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof UsdPeer peer)) return false;
            return ownId == peer.ownId && peerId == peer.peerId && peerMacAddress.equals(
                    peer.peerMacAddress);
        }

        @Override
        public int hashCode() {
            return Objects.hash(ownId, peerId, peerMacAddress);
        }
    }

    /**
     * A class representing USD session.
     */
    private final class UsdSession implements IBinder.DeathRecipient {
        private int mId = INVALID_ID;
        private Role mSessionRole = Role.NONE;
        private PublishConfig mPublishConfig;
        private IPublishSessionCallback mIPublishSessionCallback;
        private SubscribeConfig mSubscribeConfig;
        private ISubscribeSessionCallback mISubscribeSessionCallback;
        private final long mCreationTimeMillis;
        /**
         * Maps peer to peer hash (a unique identifier to the peer).
         */
        private final HashMap<UsdPeer, Integer> mSessionPeers = new HashMap<>();

        /**
         * Get Role of the session. See {@link Role} for different roles.
         */
        public Role getRole() {
            return mSessionRole;
        }

        /**
         * Set session id for this session.
         */
        public void setSessionId(int sessionId) {
            mId = sessionId;
        }

        /**
         * Adds a peer to the session if not already there. It creates a unique id (key) and add the
         * peer to a map.
         */
        public void addPeerOnce(UsdPeer peer) {
            if (mSessionPeers.containsKey(peer)) return;
            int peerHash = sNextPeerHash++;
            mSessionPeers.put(peer, peerHash);
            addPeerToGlobalMap(peerHash, peer);
        }

        /**
         * Get unique hash (a unique id) for a peer.
         */
        public int getPeerHash(UsdPeer peer) {
            return mSessionPeers.getOrDefault(peer, INVALID_ID);
        }

        /**
         * Clear all peers for this session.
         */
        public void releasePeers() {
            // Release all peers associated to this session from global map.
            for (int peerHash : mSessionPeers.values()) {
                removePeerFromGlobalMap(peerHash);
            }
            mSessionPeers.clear();
        }

        /**
         * A constructor for publisher session.
         */
        UsdSession(PublishConfig publishConfig, IPublishSessionCallback callback) {
            mSessionRole = Role.PUBLISHER;
            mPublishConfig = publishConfig;
            mIPublishSessionCallback = callback;
            // Register the recipient for a notification if this binder goes away.
            try {
                callback.asBinder().linkToDeath(this, 0);
            } catch (RemoteException e) {
                Log.e(TAG, "UsdSession linkToDeath " + e);
            }
            mCreationTimeMillis = mClock.getElapsedSinceBootMillis();
        }

        /**
         * A constructor for subscriber session.
         */
        UsdSession(SubscribeConfig subscribeConfig, ISubscribeSessionCallback callback) {
            mSessionRole = Role.SUBSCRIBER;
            mSubscribeConfig = subscribeConfig;
            mISubscribeSessionCallback = callback;
            // Register the recipient for a notification if this binder goes away.
            try {
                callback.asBinder().linkToDeath(this, 0);
            } catch (RemoteException e) {
                Log.e(TAG, "UsdSession linkToDeath " + e);
            }
            mCreationTimeMillis = mClock.getElapsedSinceBootMillis();
        }

        @Override
        public void binderDied() {
            mWifiThreadRunner.post(() -> sessionCleanup());
        }

        /**
         * A sessionCleanup function for the USD session.
         */
        public void sessionCleanup() {
            releasePeers();
            if (mSessionRole == Role.PUBLISHER) {
                mIPublishSessionCallback.asBinder().unlinkToDeath(this, 0);
            } else {
                mISubscribeSessionCallback.asBinder().unlinkToDeath(this, 0);
            }
            if (isSingleSession()) {
                mRequesterRole = Role.NONE;
                stopCleaningUpExpiredSessions();
                // Once last session is cleaned up, broadcast subscriber/publisher status.
                if (mSessionRole == Role.PUBLISHER) {
                    broadcastSubscriberStatus();
                } else {
                    broadcastPublisherStatus();
                }
            }
            mUsdSessions.remove(mId);
            mSessionRole = Role.NONE;
        }
    }

    /**
     * A class for USD discovery info from HAL.
     */
    public static final class UsdHalDiscoveryInfo {
        public final int ownId;
        public final int peerId;
        public MacAddress peerMacAddress;
        public final byte[] serviceSpecificInfo;
        @Config.ServiceProtoType
        public final int serviceProtoType;
        public final boolean isFsdEnabled;
        public final byte[] matchFilter;

        public UsdHalDiscoveryInfo(int ownId, int peerId, MacAddress peerMacAddress,
                byte[] serviceSpecificInfo, int serviceProtoType, boolean isFsdEnabled,
                byte[] matchFilter) {
            this.ownId = ownId;
            this.peerId = peerId;
            this.peerMacAddress = peerMacAddress;
            this.serviceSpecificInfo = serviceSpecificInfo;
            this.serviceProtoType = serviceProtoType;
            this.isFsdEnabled = isFsdEnabled;
            this.matchFilter = matchFilter;
        }
    }

    private final SparseArray<UsdSession> mUsdSessions = new SparseArray<>();
    private final SparseArray<UsdPeer> mGlobalPeerMap = new SparseArray<>();

    private boolean isSingleSession() {
        return mUsdSessions.size() == 1;
    }

    /**
     * Add peer to the global peer map.
     */
    private void addPeerToGlobalMap(int peerHash, UsdPeer peer) {
        mGlobalPeerMap.put(peerHash, peer);
    }

    /**
     * Checks whether peer existing in the global peer map.
     */
    private boolean doesPeerExistInGlobalMap(int peerHash) {
        return mGlobalPeerMap.contains(peerHash);
    }

    /**
     * Gets peer from the global peer map. Returns null if peer does not exist.
     */
    private UsdPeer getPeerFromGlobalMap(int peerHash) {
        return mGlobalPeerMap.get(peerHash);
    }

    /**
     * Removes peer from global peer map.
     */
    private void removePeerFromGlobalMap(int peerHash) {
        mGlobalPeerMap.remove(peerHash);
    }

    /**
     * Constructor.
     */
    public UsdRequestManager(UsdNativeManager usdNativeManager, WifiThreadRunner wifiThreadRunner,
            ActiveModeWarden activeModeWarden, Clock clock, AlarmManager alarmManager) {
        mUsdNativeManager = usdNativeManager;
        mActiveModeWarden = activeModeWarden;
        mWifiThreadRunner = wifiThreadRunner;
        registerUsdEventsCallback(new UsdNativeEventsCallback());
        mClock = clock;
        mAlarmManager = alarmManager;
        mRequesterRole = Role.NONE;
    }

    private boolean isUsdPublisherSupported() {
        return mUsdCapabilities != null && mUsdCapabilities.isUsdPublisherSupported;
    }

    private boolean isUsdSubscriberSupported() {
        return mUsdCapabilities != null && mUsdCapabilities.isUsdSubscriberSupported;
    }

    /**
     * Get USD characteristics.
     */
    public Characteristics getCharacteristics() {
        Bundle bundle = new Bundle();
        if (mUsdCapabilities == null) {
            mUsdCapabilities = mUsdNativeManager.getUsdCapabilities();
        }
        if (mUsdCapabilities != null) {
            bundle.putInt(Characteristics.KEY_MAX_NUM_SUBSCRIBE_SESSIONS,
                    mUsdCapabilities.maxNumSubscribeSessions);
            bundle.putInt(Characteristics.KEY_MAX_NUM_PUBLISH_SESSIONS,
                    mUsdCapabilities.maxNumPublishSessions);
            bundle.putInt(Characteristics.KEY_MAX_SERVICE_SPECIFIC_INFO_LENGTH,
                    mUsdCapabilities.maxLocalSsiLengthBytes);
            bundle.putInt(Characteristics.KEY_MAX_MATCH_FILTER_LENGTH,
                    mUsdCapabilities.maxMatchFilterLengthBytes);
            bundle.putInt(Characteristics.KEY_MAX_SERVICE_NAME_LENGTH,
                    mUsdCapabilities.maxServiceNameLengthBytes);
        }
        return new Characteristics(bundle);
    }

    /**
     * Whether subscriber is available.
     */
    public boolean isSubscriberAvailable() {
        return mUsdSessions.size() == 0 || mUsdSessions.valueAt(0).mSessionRole == Role.SUBSCRIBER;
    }

    /**
     * Whether publisher is available.
     */
    public boolean isPublisherAvailable() {
        return mUsdSessions.size() == 0 || mUsdSessions.valueAt(0).mSessionRole == Role.PUBLISHER;
    }

    private void notifyStatus(IBooleanListener listener, String errMsg, boolean isSuccess) {
        if (!isSuccess) {
            Log.e(TAG, "notifyStatus: " + errMsg);
        }
        try {
            listener.onResult(isSuccess);
        } catch (RemoteException e) {
            Log.e(TAG, e.toString());
        }
    }

    private String getUsdInterfaceName() {
        return mActiveModeWarden.getPrimaryClientModeManager().getInterfaceName();
    }

    /**
     * See {@link SubscribeSession#sendMessage(int, byte[], Executor, Consumer)} and
     * {@link PublishSession#sendMessage(int, byte[], Executor, Consumer)}
     */
    public void sendMessage(int sessionId, int peerHash, @NonNull byte[] message,
            @NonNull IBooleanListener listener) {
        if (!isUsdAvailable()) {
            notifyStatus(listener, "USD is not available", false);
            return;
        }
        if (getUsdInterfaceName() == null) {
            notifyStatus(listener, "USD interface name is null", false);
            return;
        }
        if (!mUsdSessions.contains(sessionId)) {
            notifyStatus(listener, "Session does not exist. Session id = " + sessionId, false);
            return;
        }
        if (message.length > mUsdCapabilities.maxLocalSsiLengthBytes) {
            notifyStatus(listener, "longer message than supported. Max len supported = "
                    + mUsdCapabilities.maxLocalSsiLengthBytes + " len = " + message.length, false);
            return;
        }
        if (!doesPeerExistInGlobalMap(peerHash)) {
            notifyStatus(listener, "Invalid peer hash = " + peerHash, false);
            return;
        }
        UsdPeer peer = getPeerFromGlobalMap(peerHash);
        if (mUsdNativeManager.sendMessage(getUsdInterfaceName(), sessionId, peer.peerId,
                peer.peerMacAddress, message)) {
            notifyStatus(listener, "", true);
        } else {
            notifyStatus(listener, "sendMessage failed", false);
        }
    }

    private boolean isUsdAvailable() {
        if (mRequesterRole == Role.PUBLISHER) {
            return isPublisherAvailable();
        } else if (mRequesterRole == Role.SUBSCRIBER) {
            return isSubscriberAvailable();
        }
        return false;
    }

    /**
     * See {@link SubscribeSession#cancel()}
     */
    public void cancelSubscribe(int sessionId) {
        if (getUsdInterfaceName() == null) {
            Log.e(TAG, "cancelSubscribe: USD interface name is null");
            return;
        }
        if (mRequesterRole == Role.SUBSCRIBER && mUsdSessions.contains(sessionId)) {
            mUsdNativeManager.cancelSubscribe(getUsdInterfaceName(), sessionId);
        }
    }

    /**
     * See {@link PublishSession#cancel()}
     */
    public void cancelPublish(int sessionId) {
        if (getUsdInterfaceName() == null) {
            Log.e(TAG, "cancelPublish: USD interface name is null");
            return;
        }
        if (mRequesterRole == Role.PUBLISHER && mUsdSessions.contains(sessionId)) {
            mUsdNativeManager.cancelPublish(getUsdInterfaceName(), sessionId);
        }
    }

    /**
     * See {@link PublishSession#updatePublish(byte[])}
     */
    public void updatePublish(int sessionId, byte[] ssi) {
        if (getUsdInterfaceName() == null) {
            Log.e(TAG, "updatePublish: USD interface name is null");
            return;
        }
        if (mRequesterRole == Role.PUBLISHER && mUsdSessions.contains(sessionId)
                && isPublisherAvailable()) {
            mUsdNativeManager.updatePublish(getUsdInterfaceName(), sessionId, ssi);
        }
    }

    private void notifyPublishFailure(IPublishSessionCallback callback, int reasonCode,
            String reason) {
        try {
            Log.w(TAG, reason);
            callback.onPublishFailed(reasonCode);
        } catch (RemoteException e) {
            Log.e(TAG, "publish: " + e);
        }
    }

    /**
     * See {@link android.net.wifi.usd.UsdManager#publish(PublishConfig, Executor,
     * PublishSessionCallback)}
     */
    public void publish(PublishConfig publishConfig, IPublishSessionCallback callback) {
        if (!isUsdPublisherSupported() || !isPublisherAvailable()) {
            notifyPublishFailure(callback, SessionCallback.FAILURE_NOT_AVAILABLE, "Not available");
            return;
        }
        if (getUsdInterfaceName() == null) {
            notifyPublishFailure(callback, SessionCallback.FAILURE_NOT_AVAILABLE,
                    "USD interface name is null");
            return;
        }
        // Check if the Role is already taken.
        if (mRequesterRole == Role.SUBSCRIBER) {
            notifyPublishFailure(callback, SessionCallback.FAILURE_NOT_AVAILABLE,
                    "Subscriber is running");
            return;
        }
        if (sessionCreationInProgress()) {
            notifyPublishFailure(callback, SessionCallback.FAILURE_NOT_AVAILABLE,
                    "Publish session creation in progress");
            return;
        }
        // Check if maximum sessions reached
        if (mUsdSessions.size() >= mUsdCapabilities.maxNumPublishSessions) {
            notifyPublishFailure(callback, SessionCallback.FAILURE_MAX_SESSIONS_REACHED,
                    "Maximum number of publish sessions reached, num of sessions = "
                            + mUsdSessions.size());
            return;
        }
        // publish
        if (mUsdNativeManager.publish(getUsdInterfaceName(), DEFAULT_COMMAND_ID, publishConfig)) {
            createPublishSession(publishConfig, callback);
            // Next: onUsdPublishStarted or  onUsdPublishConfigFailed
        } else {
            notifyPublishFailure(callback, SessionCallback.FAILURE_NOT_AVAILABLE, "Failed");
        }
    }

    private boolean sessionCreationInProgress() {
        return mUsdSessions.contains(USD_TEMP_SESSION_ID);
    }

    private void notifySubscribeFailure(ISubscribeSessionCallback callback, int reasonCode,
            String reason) {
        try {
            Log.w(TAG, reason);
            callback.onSubscribeFailed(reasonCode);
        } catch (RemoteException e) {
            Log.e(TAG, "subscribe: " + e);
        }
    }

    private void createPublishSession(PublishConfig config, IPublishSessionCallback callback) {
        UsdSession usdSession = new UsdSession(config, callback);
        // Use a temp session id. Will get updated in onPublisherStarted.
        usdSession.setSessionId(USD_TEMP_SESSION_ID);
        mUsdSessions.put(USD_TEMP_SESSION_ID, usdSession);
        if (isSingleSession()) {
            mRequesterRole = Role.PUBLISHER;
            startCleaningUpExpiredSessions();
            // After first publisher session is created, notify subscriber status as not available.
            broadcastSubscriberStatus();
        }
    }

    private void createSubscribeSession(SubscribeConfig config,
            ISubscribeSessionCallback callback) {
        UsdSession usdSession = new UsdSession(config, callback);
        // Use a temp session id. Will get updated in onSubscriberStarted.
        usdSession.setSessionId(USD_TEMP_SESSION_ID);
        mUsdSessions.put(USD_TEMP_SESSION_ID, usdSession);
        if (isSingleSession()) {
            mRequesterRole = Role.SUBSCRIBER;
            startCleaningUpExpiredSessions();
            // After first subscriber session is created, notify publisher status as not available.
            broadcastPublisherStatus();
        }
    }

    /**
     * See {@link android.net.wifi.usd.UsdManager#subscribe(SubscribeConfig, Executor,
     * SubscribeSessionCallback)}
     */
    public void subscribe(SubscribeConfig subscribeConfig, ISubscribeSessionCallback callback) {
        if (!isUsdSubscriberSupported() || !isSubscriberAvailable()) {
            notifySubscribeFailure(callback, SessionCallback.FAILURE_NOT_AVAILABLE,
                    "Not available");
            return;
        }
        if (getUsdInterfaceName() == null) {
            notifySubscribeFailure(callback, SessionCallback.FAILURE_NOT_AVAILABLE,
                    "USD interface name is null");
            return;
        }
        // Check if the Role is already taken.
        if (mRequesterRole == Role.PUBLISHER) {
            notifySubscribeFailure(callback, SessionCallback.FAILURE_NOT_AVAILABLE,
                    "Publisher is running");
            return;
        }
        if (sessionCreationInProgress()) {
            notifySubscribeFailure(callback, SessionCallback.FAILURE_NOT_AVAILABLE,
                    "Subscribe session creation in progress");
            return;
        }
        // Check if maximum sessions reached
        if (mUsdSessions.size() >= mUsdCapabilities.maxNumSubscribeSessions) {
            notifySubscribeFailure(callback, SessionCallback.FAILURE_MAX_SESSIONS_REACHED,
                    "Maximum number of subscribe sessions reached");
            return;
        }
        // subscribe
        if (mUsdNativeManager.subscribe(getUsdInterfaceName(), DEFAULT_COMMAND_ID,
                subscribeConfig)) {
            createSubscribeSession(subscribeConfig, callback);
            // Next: onUsdSubscribeStarted or onUsdSubscribeConfigFailed
        } else {
            notifySubscribeFailure(callback, SessionCallback.FAILURE_NOT_AVAILABLE, "Failed");
        }
    }

    /**
     * Register USD events from HAL.
     */
    public void  registerUsdEventsCallback(UsdNativeEventsCallback usdNativeEventsCallback) {
        mUsdNativeManager.registerUsdEventsCallback(usdNativeEventsCallback);
    }

    /**
     * Validate the session.
     */
    private static boolean isValidSession(UsdSession session, int sessionId, Role role) {
        if (session == null) {
            Log.e(TAG, "isValidSession: session does not exist (id = " + sessionId + ")");
            return false;
        }
        if (session.mSessionRole != role) {
            Log.e(TAG, "isValidSession: Invalid session role (id = " + sessionId + ")");
            return false;
        }
        if (session.mId != sessionId) {
            Log.e(TAG, "isValidSession: Invalid session id (id = " + sessionId + ")");
            return false;
        }
        return true;
    }

    /**
     * Implementation of USD callbacks. All callbacks are posted to Wi-Fi thread from
     * SupplicantStaIfaceCallbackAidlImpl.
     */
    public class UsdNativeEventsCallback implements UsdNativeManager.UsdEventsCallback {
        @Override
        public void onUsdPublishStarted(int cmdId, int publishId) {
            if (cmdId != DEFAULT_COMMAND_ID) {
                Log.e(TAG, "onUsdPublishStarted: Invalid command id = " + cmdId);
                return;
            }
            UsdSession usdSession = mUsdSessions.get(USD_TEMP_SESSION_ID);
            if (!isValidSession(usdSession, USD_TEMP_SESSION_ID, Role.PUBLISHER)) return;
            mUsdSessions.put(publishId, usdSession);
            usdSession.setSessionId(publishId);
            mUsdSessions.remove(USD_TEMP_SESSION_ID);
            try {
                usdSession.mIPublishSessionCallback.onPublishStarted(publishId);
            } catch (RemoteException e) {
                Log.e(TAG, "onUsdPublishStarted " + e);
            }
            // Next: onUsdPublishReplied or onUsdPublishTerminated
        }

        @Override
        public void onUsdSubscribeStarted(int cmdId, int subscribeId) {
            if (cmdId != DEFAULT_COMMAND_ID) {
                Log.e(TAG, "onUsdSubscribeStarted: Invalid command id = " + cmdId);
                return;
            }
            UsdSession usdSession = mUsdSessions.get(USD_TEMP_SESSION_ID);
            if (!isValidSession(usdSession, USD_TEMP_SESSION_ID, Role.SUBSCRIBER)) return;
            mUsdSessions.put(subscribeId, usdSession);
            usdSession.setSessionId(subscribeId);
            mUsdSessions.remove(USD_TEMP_SESSION_ID);
            try {
                usdSession.mISubscribeSessionCallback.onSubscribeStarted(subscribeId);
            } catch (RemoteException e) {
                Log.e(TAG, "onUsdSubscribeStarted " + e);
            }
            // Next: onUsdServiceDiscovered or onUsdSubscribeTerminated
        }

        @Override
        public void onUsdPublishConfigFailed(int cmdId,
                @SessionCallback.FailureCode int errorCode) {
            if (cmdId != DEFAULT_COMMAND_ID) {
                Log.e(TAG, "onUsdPublishConfigFailed: Invalid command id = " + cmdId);
                return;
            }
            UsdSession usdSession = mUsdSessions.get(USD_TEMP_SESSION_ID);
            if (isValidSession(usdSession, USD_TEMP_SESSION_ID, Role.PUBLISHER)) return;
            usdSession.sessionCleanup();
            try {
                usdSession.mIPublishSessionCallback.onPublishFailed(errorCode);
            } catch (RemoteException e) {
                Log.e(TAG, "onUsdPublishConfigFailed " + e);
            }
        }

        @Override
        public void onUsdSubscribeConfigFailed(int cmdId,
                @SessionCallback.FailureCode int errorCode) {
            if (cmdId != DEFAULT_COMMAND_ID) {
                Log.e(TAG, "onUsdSubscribeConfigFailed: Invalid command id = " + cmdId);
                return;
            }
            UsdSession usdSession = mUsdSessions.get(USD_TEMP_SESSION_ID);
            if (!isValidSession(usdSession, USD_TEMP_SESSION_ID, Role.SUBSCRIBER)) return;
            usdSession.sessionCleanup();
            try {
                usdSession.mISubscribeSessionCallback.onSubscribeFailed(errorCode);
            } catch (RemoteException e) {
                Log.e(TAG, "onUsdSubscribeConfigFailed " + e);
            }
        }

        @Override
        public void onUsdPublishTerminated(int publishId, int reasonCode) {
            if (!mUsdSessions.contains(publishId)) {
                return;
            }
            UsdSession usdSession = mUsdSessions.get(publishId);
            if (!isValidSession(usdSession, publishId, Role.PUBLISHER)) return;
            try {
                usdSession.mIPublishSessionCallback.onPublishSessionTerminated(reasonCode);
            } catch (RemoteException e) {
                Log.e(TAG, "onUsdPublishTerminated " + e);
            }
            usdSession.sessionCleanup();
        }

        @Override
        public void onUsdSubscribeTerminated(int subscribeId, int reasonCode) {
            if (!mUsdSessions.contains(subscribeId)) {
                return;
            }
            UsdSession usdSession = mUsdSessions.get(subscribeId);
            if (!isValidSession(usdSession, subscribeId, Role.SUBSCRIBER)) return;
            try {
                usdSession.mISubscribeSessionCallback.onSubscribeSessionTerminated(reasonCode);
            } catch (RemoteException e) {
                Log.e(TAG, "onUsdSubscribeTerminated " + e);
            }
            usdSession.sessionCleanup();
        }

        @Override
        public void onUsdPublishReplied(UsdHalDiscoveryInfo info) {
            // Check whether session matches.
            if (!mUsdSessions.contains(info.ownId)) {
                return;
            }
            // Check whether events are enabled for the publisher.
            UsdSession usdSession = mUsdSessions.get(info.ownId);
            if (isValidSession(usdSession, info.ownId, Role.PUBLISHER)) return;
            if (!usdSession.mPublishConfig.isEventsEnabled()) return;
            // Add the peer to the session if not already present.
            UsdPeer peer = new UsdPeer(info.ownId, info.peerId, info.peerMacAddress);
            usdSession.addPeerOnce(peer);
            try {
                // Pass unique peer hash to the application. When the application gives back the
                // peer hash, it'll be used to retrieve the peer.
                usdSession.mIPublishSessionCallback.onPublishReplied(usdSession.getPeerHash(peer),
                        info.serviceSpecificInfo, info.serviceProtoType, info.isFsdEnabled);
            } catch (RemoteException e) {
                Log.e(TAG, "onUsdPublishReplied " + e);
            }
        }

        @Override
        public void onUsdServiceDiscovered(UsdHalDiscoveryInfo info) {
            // Check whether session matches.
            if (!mUsdSessions.contains(info.ownId)) {
                return;
            }
            // Add the peer to the session if not already present.
            UsdPeer peer = new UsdPeer(info.ownId, info.peerId, info.peerMacAddress);
            UsdSession usdSession = mUsdSessions.get(info.ownId);
            if (isValidSession(usdSession, info.ownId, Role.SUBSCRIBER)) return;
            usdSession.addPeerOnce(peer);
            try {
                // Pass unique peer hash to the application. When the application gives back the
                // peer hash, it'll be used to retrieve the peer.
                usdSession.mISubscribeSessionCallback.onSubscribeDiscovered(
                        usdSession.getPeerHash(peer), info.serviceSpecificInfo,
                        info.serviceProtoType, info.isFsdEnabled);
            } catch (RemoteException e) {
                Log.e(TAG, "onUsdServiceDiscovered " + e);
            }
        }

        @Override
        public void onUsdMessageReceived(int ownId, int peerId, MacAddress peerMacAddress,
                byte[] message) {
            // Check whether session matches.
            if (!mUsdSessions.contains(ownId)) {
                return;
            }
            // Add the peer to the session if not already present.
            UsdPeer peer = new UsdPeer(ownId, peerId, peerMacAddress);
            UsdSession usdSession = mUsdSessions.get(ownId);
            if (isValidSession(usdSession, ownId, mRequesterRole)) return;
            usdSession.addPeerOnce(peer);
            try {
                // Pass unique peer hash to the application. When the application gives back the
                // peer hash, it'll be used to retrieve the peer.
                if (mRequesterRole == Role.SUBSCRIBER) {
                    usdSession.mISubscribeSessionCallback.onMessageReceived(
                            usdSession.getPeerHash(peer), message);
                } else {
                    usdSession.mIPublishSessionCallback.onMessageReceived(
                            usdSession.getPeerHash(peer), message);
                }
            } catch (RemoteException e) {
                Log.e(TAG, "onUsdMessageReceived " + e);
            }
        }
    }

    private void broadcastPublisherStatus() {
        int numListeners = mPublisherListenerList.beginBroadcast();
        for (int i = 0; i < numListeners; i++) {
            IBooleanListener listener = mPublisherListenerList.getBroadcastItem(i);
            try {
                listener.onResult(isPublisherAvailable());
            } catch (RemoteException e) {
                Log.e(TAG, "broadcastPublisherStatus: " + e);
            }
        }
        mPublisherListenerList.finishBroadcast();
    }

    private void broadcastSubscriberStatus() {
        int numListeners = mSubscriberListenerList.beginBroadcast();
        for (int i = 0; i < numListeners; i++) {
            IBooleanListener listener = mSubscriberListenerList.getBroadcastItem(i);
            try {
                listener.onResult(isSubscriberAvailable());
            } catch (RemoteException e) {
                Log.e(TAG, "broadcastSubscriberStatus: " + e);
            }
        }
        mSubscriberListenerList.finishBroadcast();
    }

    /**
     * Register for publisher status listener and notify the application on current status.
     */
    public void registerPublisherStatusListener(IBooleanListener listener) {
        mPublisherListenerList.register(listener);
        try {
            listener.onResult(isPublisherAvailable());
        } catch (RemoteException e) {
            Log.e(TAG, "registerPublisherStatusListener: " + e);
        }
    }

    /**
     * Unregister previously registered publisher status listener.
     */
    public void unregisterPublisherStatusListener(IBooleanListener listener) {
        mPublisherListenerList.unregister(listener);
    }

    /**
     * Register for subscriber status listener and notify the application on current status.
     */
    public void registerSubscriberStatusListener(IBooleanListener listener) {
        mSubscriberListenerList.register(listener);
        try {
            listener.onResult(isSubscriberAvailable());
        } catch (RemoteException e) {
            Log.e(TAG, "registerSubscriberStatusListener: " + e);
        }
    }

    /**
     * Unregister previously registered subscriber status listener.
     */
    public void unregisterSubscriberStatusListener(IBooleanListener listener) {
        mSubscriberListenerList.unregister(listener);
    }
}
