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

package com.android.server.wifi.nl80211;

import static android.system.OsConstants.ENOBUFS;

import static com.android.net.module.util.SocketUtils.closeSocketQuietly;
import static com.android.server.wifi.nl80211.NetlinkConstants.NETLINK_ADD_MEMBERSHIP;
import static com.android.server.wifi.nl80211.NetlinkConstants.SOL_NETLINK;

import android.annotation.NonNull;
import android.os.Handler;
import android.system.ErrnoException;
import android.system.Os;
import android.util.Log;

import com.android.net.module.util.FdEventsReader;
import com.android.net.module.util.PacketReader;
import com.android.net.module.util.netlink.NetlinkUtils;

import java.io.FileDescriptor;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Monitor for Nl80211 broadcasts such as scan results and regulatory changes.
 *
 * Threading Model: The main event loop should run on the Background Thread to avoid
 * blocking the Wifi Thread. When an event is received on the Background Thread, it should
 * be posted to the Wifi Thread for processing.
 */
public class Nl80211BroadcastMonitor extends PacketReader {
    private static final String TAG = "Nl80211BroadcastMonitor";
    private final Handler mWifiHandler;
    private final List<Integer> mMulticastGroupIds;
    private final Object mLock = new Object();

    // Maps an event type to a set of callbacks to trigger when that event occurs.
    private final Map<Short, Set<Nl80211BroadcastCallback>> mEventCallbackMap = new HashMap<>();

    Nl80211BroadcastMonitor(@NonNull Handler backgroundHandler, @NonNull Handler wifiHandler,
            @NonNull List<Integer> multicastGroupIds) {
        super(backgroundHandler, NetlinkUtils.DEFAULT_RECV_BUFSIZE);
        mWifiHandler = wifiHandler;
        mMulticastGroupIds = multicastGroupIds;
    }

    /**
     * Callback interface to receive Nl80211 broadcast events.
     */
    public interface Nl80211BroadcastCallback {
        /**
         * Called when a broadcast event with the specified type is received.
         *
         * @param type Type of broadcast that was received. See the
         *             NL80211_CMD values in {@link NetlinkConstants}.
         * @param message Full message sent by the broadcast.
         */
        void onEvent(short type, @NonNull GenericNetlinkMsg message);
    }

    /**
     * Called by the parent class to create the file descriptor to monitor.
     * See {@link FdEventsReader#createFd()}.
     */
    @Override
    protected FileDescriptor createFd() {
        FileDescriptor fd = Nl80211Proxy.createNetlinkFileDescriptor();
        if (fd == null) {
            Log.i(TAG, "Unable to create file descriptor");
            return null;
        }

        try {
            Log.i(TAG, "Registering for multicast groups: " + mMulticastGroupIds);
            for (int groupId : mMulticastGroupIds) {
                Os.setsockoptInt(fd, SOL_NETLINK, NETLINK_ADD_MEMBERSHIP, groupId);
            }
            Log.i(TAG, "File descriptor was created successfully");
            return fd;
        } catch (ErrnoException e) {
            Log.e(TAG, "Unable to subscribe to multicast groups: " + e);
            closeSocketQuietly(fd);
            return null;
        }
    }

    /**
     * Called by the parent class if an error is encountered in the main loop.
     * See {@link FdEventsReader#logError(String, Exception)}.
     */
    @Override
    protected void logError(String msg, Exception e) {
        Log.e(TAG, "Error in the parent class. msg=" + msg + ", e=" + e);
    }

    /**
     * Called by the parent class if a read error is encountered. We will use the same
     * implementation as {@link com.android.net.module.util.ip.NetlinkMonitor}.
     * Also see {@link FdEventsReader#handleReadError(ErrnoException)}.
     */
    @Override
    protected boolean handleReadError(ErrnoException e) {
        if (e.errno == ENOBUFS) {
            Log.w(TAG, "Ignoring read error of type ENOBUFS");
            return false;
        }
        Log.e(TAG, "Read error in parent class: " + e);
        return true;
    }

    /**
     * Called by the parent class when a packet is received.
     * See {@link FdEventsReader#handlePacket(Object, int)}.
     */
    @Override
    protected void handlePacket(byte[] recvbuf, int length) {
        // The provided length will likely be smaller than the actual buffer size,
        // since the same buffer will be reused across reads.
        if (recvbuf == null || length == 0 || length > NetlinkUtils.DEFAULT_RECV_BUFSIZE
                || length > recvbuf.length) {
            Log.e(TAG, "Received an invalid buffer");
            return;
        }
        // Copy recvbuf since the buffer will be reused by the caller.
        byte[] copiedBuffer = Arrays.copyOf(recvbuf, length);
        // Actual processing will be done on the Wifi thread.
        mWifiHandler.post(() -> handlePacketOnWifiThread(copiedBuffer));
    }

    /**
     * Handle a packet received by the broadcast monitor.
     * This method should only run on the Wifi thread.
     */
    private void handlePacketOnWifiThread(@NonNull byte[] recvbuf) {
        // Assume that the buffer contains a single message.
        ByteBuffer byteBuffer = ByteBuffer.wrap(recvbuf);
        byteBuffer.order(ByteOrder.nativeOrder());
        GenericNetlinkMsg message = GenericNetlinkMsg.parse(byteBuffer);
        if (message == null) return;

        synchronized (mLock) {
            short type = message.genNlHeader.command;
            Set<Nl80211BroadcastCallback> callbacks = mEventCallbackMap.get(type);
            if (callbacks == null || callbacks.isEmpty()) return;
            for (Nl80211BroadcastCallback callback : callbacks) {
                callback.onEvent(type, message);
            }
        }
    }

    /**
     * Register a callback to trigger when the specified broadcast event type is received.
     *
     * @param type Type of broadcast event on which to trigger the callback. See the
     *             NL80211_CMD values in {@link NetlinkConstants}.
     * @param callback Callback object that should be called.
     */
    public void registerBroadcastCallback(short type, @NonNull Nl80211BroadcastCallback callback) {
        synchronized (mLock) {
            if (callback == null) return;
            if (!mEventCallbackMap.containsKey(type)) {
                mEventCallbackMap.put(type, new HashSet<>());
            }
            mEventCallbackMap.get(type).add(callback);
        }
    }

    /**
     * Unregister a broadcast event callback that was previously registered.
     *
     * @param type Type of broadcast event which the callback is associated with. See the
     *             NL80211_CMD values in {@link NetlinkConstants}.
     * @param callback Callback object which was registered.
     */
    public void unregisterBroadcastCallback(
            short type, @NonNull Nl80211BroadcastCallback callback) {
        synchronized (mLock) {
            if (callback == null || !mEventCallbackMap.containsKey(type)) return;
            Set<Nl80211BroadcastCallback> callbacks = mEventCallbackMap.get(type);
            callbacks.remove(callback);
            if (callbacks.isEmpty()) {
                // Remove this event type from the map if it has no registered callbacks.
                mEventCallbackMap.remove(type);
            }
        }
    }
}
