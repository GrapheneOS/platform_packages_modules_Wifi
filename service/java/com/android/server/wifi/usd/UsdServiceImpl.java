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
import android.content.Context;
import android.net.wifi.IBooleanListener;
import android.net.wifi.usd.Characteristics;
import android.net.wifi.usd.IPublishSessionCallback;
import android.net.wifi.usd.ISubscribeSessionCallback;
import android.net.wifi.usd.IUsdManager;
import android.net.wifi.usd.PublishConfig;
import android.net.wifi.usd.PublishSession;
import android.net.wifi.usd.PublishSessionCallback;
import android.net.wifi.usd.SubscribeConfig;
import android.net.wifi.usd.SubscribeSession;
import android.net.wifi.usd.SubscribeSessionCallback;
import android.net.wifi.usd.UsdManager;
import android.os.Binder;
import android.util.Log;

import com.android.server.wifi.WifiInjector;
import com.android.server.wifi.WifiThreadRunner;
import com.android.server.wifi.util.WifiPermissionsUtil;

import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/**
 * Implementation of the IUsdManager.
 */
public class UsdServiceImpl extends IUsdManager.Stub {
    private static final String TAG = UsdServiceImpl.class.getName();
    private final Context mContext;
    private WifiThreadRunner mWifiThreadRunner;
    private WifiInjector mWifiInjector;
    private WifiPermissionsUtil mWifiPermissionsUtil;
    private UsdRequestManager mUsdRequestManager;
    private UsdNativeManager mUsdNativeManager;

    /**
     * Constructor
     */
    public UsdServiceImpl(Context context) {
        mContext = context;
    }

    /**
     * Start the service
     */
    public void start(@NonNull WifiInjector wifiInjector) {
        mWifiInjector = wifiInjector;
        mWifiPermissionsUtil = mWifiInjector.getWifiPermissionsUtil();
        mUsdNativeManager = new UsdNativeManager(mWifiInjector.getWifiNative());
        mUsdRequestManager = new UsdRequestManager(mUsdNativeManager,
                mWifiInjector.getWifiThreadRunner(),
                mWifiInjector.getActiveModeWarden(),
                mWifiInjector.getClock(), mWifiInjector.getAlarmManager());
        mWifiThreadRunner = mWifiInjector.getWifiThreadRunner();
        Log.i(TAG, "start");
    }

    /**
     * Start/initialize portions of the service which require the boot stage to be complete.
     */
    public void startLate() {
        Log.i(TAG, "startLate");
        // Get USD capabilities to update the cache in UsdRequestManager
        mUsdRequestManager.getCharacteristics();
    }

    /**
     * Proxy for the final native call of the parent class. Enables mocking of
     * the function.
     */
    public int getMockableCallingUid() {
        return Binder.getCallingUid();
    }

    /**
     * See {@link UsdManager#getCharacteristics()}
     */
    @Override
    public Characteristics getCharacteristics() {
        int uid = getMockableCallingUid();
        if (!mWifiPermissionsUtil.checkManageWifiNetworkSelectionPermission(uid)) {
            throw new SecurityException("App not allowed to use USD (uid = " + uid + ")");
        }
        return mUsdRequestManager.getCharacteristics();
    }

    /**
     * See {@link SubscribeSession#sendMessage(int, byte[], Executor, Consumer)}
     */
    public void sendMessage(int sessionId, int peerId, @NonNull byte[] message,
            @NonNull IBooleanListener listener) {
        Objects.requireNonNull(message, "message must not be null");
        Objects.requireNonNull(listener, "listener must not be null");
        int uid = getMockableCallingUid();
        if (!mWifiPermissionsUtil.checkManageWifiNetworkSelectionPermission(uid)) {
            throw new SecurityException("App not allowed to use USD (uid = " + uid + ")");
        }
        Log.i(TAG, "sendMessage ( peerId = " + peerId + " , message length = " + message.length
                + " )");
        mWifiThreadRunner.post(() -> mUsdRequestManager.sendMessage(sessionId, peerId, message,
                listener));
    }

    /**
     * See {@link SubscribeSession#cancel()}
     */
    public void cancelSubscribe(int sessionId) {
        int uid = getMockableCallingUid();
        if (!mWifiPermissionsUtil.checkManageWifiNetworkSelectionPermission(uid)) {
            throw new SecurityException("App not allowed to use USD (uid = " + uid + ")");
        }
        Log.i(TAG, "cancelSubscribe: ( sessionId = " + sessionId + " )");
        mWifiThreadRunner.post(() -> mUsdRequestManager.cancelSubscribe(sessionId));
    }

    /**
     * See {@link PublishSession#cancel()}
     */
    public void cancelPublish(int sessionId) {
        int uid = getMockableCallingUid();
        if (!mWifiPermissionsUtil.checkManageWifiNetworkSelectionPermission(uid)) {
            throw new SecurityException("App not allowed to use USD (uid = " + uid + ")");
        }
        Log.i(TAG, "cancelPublish: ( sessionId = " + sessionId + " )");
        mWifiThreadRunner.post(() -> mUsdRequestManager.cancelPublish(sessionId));
    }

    /**
     * See {@link PublishSession#updatePublish(byte[])}
     */
    public void updatePublish(int sessionId, @NonNull byte[] ssi) {
        Objects.requireNonNull(ssi, "Service specific info must not be null");
        int uid = getMockableCallingUid();
        if (!mWifiPermissionsUtil.checkManageWifiNetworkSelectionPermission(uid)) {
            throw new SecurityException("App not allowed to use USD (uid = " + uid + ")");
        }
        Log.i(TAG, "updatePublish: ( sessionId = " + sessionId + " )");
        mWifiThreadRunner.post(() -> mUsdRequestManager.updatePublish(sessionId, ssi));
    }

    /**
     * See {@link UsdManager#publish(PublishConfig, Executor, PublishSessionCallback)}
     */
    @Override
    public void publish(PublishConfig publishConfig, IPublishSessionCallback callback) {
        Objects.requireNonNull(publishConfig, "publishConfig must not be null");
        Objects.requireNonNull(callback, "callback must not be null");
        int uid = getMockableCallingUid();
        if (!mWifiPermissionsUtil.checkManageWifiNetworkSelectionPermission(uid)) {
            throw new SecurityException("App not allowed to use USD (uid = " + uid + ")");
        }
        // TODO: validate config
        Log.i(TAG, "publish " + publishConfig);
        mWifiThreadRunner.post(() -> mUsdRequestManager.publish(publishConfig, callback));
    }

    /**
     * See {@link UsdManager#subscribe(SubscribeConfig, Executor, SubscribeSessionCallback)}
     */
    @Override
    public void subscribe(SubscribeConfig subscribeConfig, ISubscribeSessionCallback callback) {
        Objects.requireNonNull(subscribeConfig, "subscribeConfig must not be null");
        Objects.requireNonNull(callback, "callback must not be null");
        int uid = getMockableCallingUid();
        if (!mWifiPermissionsUtil.checkManageWifiNetworkSelectionPermission(uid)) {
            throw new SecurityException("App not allowed to use USD (uid = " + uid + ")");
        }
        // TODO: validate config
        Log.i(TAG, "subscribe " + subscribeConfig);
        mWifiThreadRunner.post(() -> mUsdRequestManager.subscribe(subscribeConfig, callback));
    }

    /**
     * See {@link UsdManager#registerPublisherStatusListener(Executor, Consumer)}
     */
    public void registerPublisherStatusListener(@NonNull IBooleanListener listener) {
        int uid = getMockableCallingUid();
        if (!mWifiPermissionsUtil.checkManageWifiNetworkSelectionPermission(uid)) {
            throw new SecurityException("App not allowed to use USD (uid = " + uid + ")");
        }
        mWifiThreadRunner.post(() -> mUsdRequestManager.registerPublisherStatusListener(listener));
    }

    /**
     * See {@link UsdManager#unregisterPublisherStatusListener(Consumer)}
     */
    public void unregisterPublisherStatusListener(@NonNull IBooleanListener listener) {
        int uid = getMockableCallingUid();
        if (!mWifiPermissionsUtil.checkManageWifiNetworkSelectionPermission(uid)) {
            throw new SecurityException("App not allowed to use USD (uid = " + uid + ")");
        }
        mWifiThreadRunner.post(
                () -> mUsdRequestManager.unregisterPublisherStatusListener(listener));
    }

    /**
     * See {@link UsdManager#registerSubscriberStatusListener(Executor, Consumer)}
     */
    public void registerSubscriberStatusListener(@NonNull IBooleanListener listener) {
        int uid = getMockableCallingUid();
        if (!mWifiPermissionsUtil.checkManageWifiNetworkSelectionPermission(uid)) {
            throw new SecurityException("App not allowed to use USD (uid = " + uid + ")");
        }
        mWifiThreadRunner.post(() -> mUsdRequestManager.registerSubscriberStatusListener(listener));
    }

    /**
     * See {@link UsdManager#unregisterSubscriberStatusListener(Consumer)}
     */
    public void unregisterSubscriberStatusListener(@NonNull IBooleanListener listener) {
        int uid = getMockableCallingUid();
        if (!mWifiPermissionsUtil.checkManageWifiNetworkSelectionPermission(uid)) {
            throw new SecurityException("App not allowed to use USD (uid = " + uid + ")");
        }
        mWifiThreadRunner.post(
                () -> mUsdRequestManager.unregisterSubscriberStatusListener(listener));
    }
}
