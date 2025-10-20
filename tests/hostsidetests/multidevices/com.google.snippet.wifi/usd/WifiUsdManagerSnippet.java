/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.google.snippet.wifi.usd;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.net.wifi.usd.DiscoveryResult;
import android.net.wifi.usd.PublishConfig;
import android.net.wifi.usd.PublishSession;
import android.net.wifi.usd.PublishSessionCallback;
import android.net.wifi.usd.SubscribeConfig;
import android.net.wifi.usd.SubscribeSession;
import android.net.wifi.usd.SubscribeSessionCallback;
import android.net.wifi.usd.UsdManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.ShellIdentityUtils;

import com.google.android.mobly.snippet.Snippet;
import com.google.android.mobly.snippet.rpc.Rpc;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** Snippet class for Wi-Fi USD functionality. */
public class WifiUsdManagerSnippet implements Snippet {
    private static final String TAG = "WifiUsdSnippet";
    private static final int TIMEOUT_SECS = 60;

    private final Context mContext;
    private final UsdManager mUsdManager;
    private final WifiManager mWifiManager;
    private final ScheduledExecutorService mExecutor;

    private PublishSession mActivePublishSession;
    private SubscribeSession mActiveSubscribeSession;

    private final BlockingQueue<String> mReceivedMessages = new LinkedBlockingQueue<>();
    private final AtomicInteger mLastDiscoveredPeerId = new AtomicInteger(-1);
    private final AtomicInteger mLastMessageSenderPeerId = new AtomicInteger(-1);

    /** Snippet constructor. */
    public WifiUsdManagerSnippet() {
        mContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        mUsdManager = mContext.getSystemService(UsdManager.class);
        mWifiManager = mContext.getSystemService(WifiManager.class);
        mExecutor = Executors.newSingleThreadScheduledExecutor();
    }

    private class PublisherCallback extends PublishSessionCallback {
        final CountDownLatch mStartedLatch = new CountDownLatch(1);
        final AtomicReference<String> mFailureReason = new AtomicReference<>();

        @Override
        public void onPublishStarted(@NonNull PublishSession session) {
            mActivePublishSession = session;
            mStartedLatch.countDown();
        }

        @Override
        public void onPublishFailed(int reason) {
            mFailureReason.set("Publish failed: " + reason);
            mStartedLatch.countDown();
        }

        @Override
        public void onMessageReceived(int peerId, @Nullable byte[] message) {
            mLastMessageSenderPeerId.set(peerId);
            if (message != null) {
                mReceivedMessages.offer(new String(message, StandardCharsets.UTF_8));
            }
        }

        void waitForStart() throws Exception {
            if (!mStartedLatch.await(TIMEOUT_SECS, TimeUnit.SECONDS)) {
                throw new Exception("Timeout waiting for publish to start.");
            }
            if (mFailureReason.get() != null) {
                throw new Exception(mFailureReason.get());
            }
        }
    }

    private class SubscriberCallback extends SubscribeSessionCallback {
        final CountDownLatch mStartedLatch = new CountDownLatch(1);
        final CountDownLatch mDiscoveryLatch = new CountDownLatch(1);
        final AtomicReference<String> mFailureReason = new AtomicReference<>();

        @Override
        public void onSubscribeStarted(@NonNull SubscribeSession session) {
            mActiveSubscribeSession = session;
            mStartedLatch.countDown();
        }

        @Override
        public void onSubscribeFailed(int reason) {
            mFailureReason.set("Subscribe failed: " + reason);
            mStartedLatch.countDown();
        }

        @Override
        public void onServiceDiscovered(@NonNull DiscoveryResult discoveryResult) {
            mLastDiscoveredPeerId.set(discoveryResult.getPeerId());
            mDiscoveryLatch.countDown();
        }

        @Override
        public void onMessageReceived(int peerId, @Nullable byte[] message) {
            if (message != null) {
                mReceivedMessages.offer(new String(message, StandardCharsets.UTF_8));
            }
        }

        void waitForStart() throws Exception {
            if (!mStartedLatch.await(TIMEOUT_SECS, TimeUnit.SECONDS)) {
                throw new Exception("Timeout waiting for subscribe to start.");
            }
            if (mFailureReason.get() != null) {
                throw new Exception(mFailureReason.get());
            }
        }

        void waitForDiscovery() throws Exception {
            if (!mDiscoveryLatch.await(TIMEOUT_SECS, TimeUnit.SECONDS)) {
                throw new Exception("Timeout waiting for service discovery.");
            }
        }
    }

    /** Checks if the USD feature is supported on this device. */
    @Rpc(description = "Checks if the USD feature is supported on this device.")
    public boolean isUsdSupported() {
        return mUsdManager != null;
    }

    /** Checks if the USD Publisher role is supported on this device. */
    @Rpc(description = "Checks if the USD Publisher role is supported.")
    public boolean isUsdPublisherSupported() {
        if (mUsdManager == null || mWifiManager == null) return false;
        return ShellIdentityUtils.invokeWithShellPermissions(
                () -> mWifiManager.isUsdPublisherSupported());
    }
    /** Checks if the USD Subscriber role is supported on this device. */
    @Rpc(description = "Checks if the USD Subscriber role is supported.")
    public boolean isUsdSubscriberSupported() {
        if (mUsdManager == null || mWifiManager == null) return false;
        return ShellIdentityUtils.invokeWithShellPermissions(
                () -> mWifiManager.isUsdSubscriberSupported());
    }


    /** Starts a USD publish session. */
    @Rpc(description = "Starts a USD publish session.")
    public void startUsdPublishSession(String serviceName, String ssi) throws Exception {
        PublishConfig config =
                new PublishConfig.Builder(serviceName)
                        .setServiceSpecificInfo(ssi.getBytes(StandardCharsets.UTF_8))
                        .build();
        PublisherCallback callback = new PublisherCallback();
        ShellIdentityUtils.invokeWithShellPermissions(
                () -> mUsdManager.publish(config, mExecutor, callback));
        callback.waitForStart();
    }

    /** Helper method for different subscribe types. */
    private void subscribeAndSendMessage(
            String serviceName, String ssi, String message, int subscribeType) throws Exception {
        SubscribeConfig.Builder configBuilder =
                new SubscribeConfig.Builder(serviceName)
                        .setServiceSpecificInfo(ssi.getBytes(StandardCharsets.UTF_8));

        // Set the subscribe type based on the provided parameter
        configBuilder.setSubscribeType(subscribeType);

        SubscribeConfig config = configBuilder.build();
        SubscriberCallback callback = new SubscriberCallback();
        ShellIdentityUtils.invokeWithShellPermissions(
                () -> mUsdManager.subscribe(config, mExecutor, callback));
        callback.waitForStart();
        callback.waitForDiscovery();

        int peerId = mLastDiscoveredPeerId.get();
        if (peerId == -1) {
            throw new Exception("Discovery succeeded but peer ID is invalid.");
        }

        sendMessage(peerId, message);
    }

    /** Subscribes passively, discovers, and sends a message. */
    @Rpc(description = "Subscribes passively, discovers, and sends a message.")
    public void subscribePassiveAndSendMessage(String serviceName, String ssi, String message)
            throws Exception {
        subscribeAndSendMessage(
                serviceName, ssi, message, SubscribeConfig.SUBSCRIBE_TYPE_PASSIVE);
    }

    /** Subscribes actively, discovers, and sends a message. */
    @Rpc(description = "Subscribes actively, discovers, and sends a message.")
    public void subscribeActiveAndSendMessage(String serviceName, String ssi, String message)
            throws Exception {
        subscribeAndSendMessage(serviceName, ssi, message, SubscribeConfig.SUBSCRIBE_TYPE_ACTIVE);
    }

    /** Sends a message from the current session. */
    private void sendMessage(int peerId, String message) throws Exception {
        if (mActiveSubscribeSession == null) {
            throw new Exception("No active subscribe session.");
        }
        final CountDownLatch sendLatch = new CountDownLatch(1);
        final AtomicReference<String> failureReason = new AtomicReference<>();
        byte[] msgBytes = message.getBytes(StandardCharsets.UTF_8);

        ShellIdentityUtils.invokeWithShellPermissions(
                () -> {
                    mActiveSubscribeSession.sendMessage(
                            peerId,
                            msgBytes,
                            mExecutor,
                            success -> {
                                if (!success) {
                                    failureReason.set("sendMessage callback returned false.");
                                }
                                sendLatch.countDown();
                            });
                });

        if (!sendLatch.await(TIMEOUT_SECS, TimeUnit.SECONDS)) {
            throw new Exception("Timeout waiting for sendMessage callback.");
        }
        if (failureReason.get() != null) {
            throw new Exception(failureReason.get());
        }
    }

    /** Sends a message from the publisher to a specified peer. */
    @Rpc(description = "Sends a message from the publisher to a specified peer.")
    public void sendMessageFromPublisher(int peerId, String message) throws Exception {
        if (mActivePublishSession == null) {
            throw new Exception("No active publish session.");
        }
        final CountDownLatch sendLatch = new CountDownLatch(1);
        final AtomicReference<String> failureReason = new AtomicReference<>();
        byte[] msgBytes = message.getBytes(StandardCharsets.UTF_8);

        ShellIdentityUtils.invokeWithShellPermissions(
                () -> {
                    mActivePublishSession.sendMessage(
                            peerId,
                            msgBytes,
                            mExecutor,
                            success -> {
                                if (!success) {
                                    failureReason.set(
                                            "sendMessage from publisher callback returned false.");
                                }
                                sendLatch.countDown();
                            });
                });
        if (!sendLatch.await(TIMEOUT_SECS, TimeUnit.SECONDS)) {
            throw new Exception("Timeout waiting for sendMessage from publisher callback.");
        }
        if (failureReason.get() != null) {
            throw new Exception(failureReason.get());
        }
    }

    /** Waits for and returns a received message. */
    @Rpc(description = "Waits for and returns a received message.")
    @Nullable
    public String receiveMessage() throws InterruptedException {
        return mReceivedMessages.poll(TIMEOUT_SECS, TimeUnit.SECONDS);
    }

    /** Retrieves the peer ID of the last message sender. */
    @Rpc(description = "Retrieves the peer ID of the last message sender.")
    public int getLastMessageSenderPeerId() {
        return mLastMessageSenderPeerId.get();
    }

    /** Stops any active USD sessions. */
    @Rpc(description = "Stops any active USD sessions.")
    public void stopUsdSessions() {
        if (mActivePublishSession != null) {
            ShellIdentityUtils.invokeWithShellPermissions(() -> mActivePublishSession.cancel());
            mActivePublishSession = null;
        }
        if (mActiveSubscribeSession != null) {
            ShellIdentityUtils.invokeWithShellPermissions(() -> mActiveSubscribeSession.cancel());
            mActiveSubscribeSession = null;
        }
        mReceivedMessages.clear();
    }

    @Override
    public void shutdown() {
        stopUsdSessions();
        if (mExecutor != null) {
            mExecutor.shutdown();
        }
    }
}
