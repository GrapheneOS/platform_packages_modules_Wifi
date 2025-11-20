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

package com.android.server.wifi.nl80211;

import static com.android.net.module.util.netlink.StructNlMsgHdr.NLM_F_ACK;
import static com.android.server.wifi.nl80211.NetlinkConstants.CTRL_ATTR_FAMILY_ID;
import static com.android.server.wifi.nl80211.NetlinkConstants.CTRL_ATTR_FAMILY_NAME;
import static com.android.server.wifi.nl80211.NetlinkConstants.CTRL_ATTR_MCAST_GROUPS;
import static com.android.server.wifi.nl80211.NetlinkConstants.CTRL_ATTR_MCAST_GRP_ID;
import static com.android.server.wifi.nl80211.NetlinkConstants.CTRL_ATTR_MCAST_GRP_NAME;
import static com.android.server.wifi.nl80211.NetlinkConstants.CTRL_CMD_GETFAMILY;
import static com.android.server.wifi.nl80211.NetlinkConstants.CTRL_CMD_NEWFAMILY;
import static com.android.server.wifi.nl80211.NetlinkConstants.GENL_ID_CTRL;
import static com.android.server.wifi.nl80211.NetlinkConstants.NETLINK_GENERIC;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_GENL_NAME;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_MULTICAST_GROUP_MLME;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_MULTICAST_GROUP_REG;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_MULTICAST_GROUP_SCAN;
import static com.android.server.wifi.nl80211.NetlinkConstants.NLMSG_DONE;
import static com.android.server.wifi.nl80211.NetlinkConstants.NLMSG_ERROR;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.os.Handler;
import android.system.ErrnoException;
import android.util.Log;

import com.android.internal.annotations.VisibleForTesting;
import com.android.modules.utils.BackgroundThread;
import com.android.net.module.util.netlink.NetlinkUtils;
import com.android.net.module.util.netlink.StructNlAttr;
import com.android.net.module.util.netlink.StructNlMsgErr;
import com.android.net.module.util.netlink.StructNlMsgHdr;
import com.android.server.wifi.WifiMetrics;
import com.android.server.wifi.proto.WifiStatsLog;
import com.android.wifi.flags.Flags;

import java.io.FileDescriptor;
import java.io.InterruptedIOException;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

/**
 * Wrapper around Nl80211 functionality. Allows sending and receiving Nl80211 messages.
 */
public class Nl80211Proxy {
    private static final String TAG = "Nl80211Proxy";

    private static final String[] sRequiredMulticastGroups = new String[]{
            NL80211_MULTICAST_GROUP_SCAN,
            NL80211_MULTICAST_GROUP_REG,
            NL80211_MULTICAST_GROUP_MLME};

    private WifiMetrics mWifiMetrics;
    private boolean mIsInitialized;
    private FileDescriptor mNetlinkFd;
    private short mNl80211FamilyId;
    private int mSequenceNumber;
    private Handler mWifiHandler;
    private Nl80211BroadcastMonitor mBroadcastMonitor;
    private Map<String, Integer> mMulticastGroups = new HashMap<>();

    /**
     * Listener to receive Netlink responses asynchronously.
     */
    public interface NetlinkResponseListener {
        /**
         * Called when a response has been received.
         *
         * @param response Response, or null if an error occurred
         */
        void onResponse(@Nullable Nl80211Response response);
    }

    public Nl80211Proxy(Handler wifiHandler, WifiMetrics wifiMetrics) {
        mWifiHandler = wifiHandler;
        mWifiMetrics = wifiMetrics;
    }

    private int getSequenceNumber() {
        return mSequenceNumber++;
    }

    protected static FileDescriptor createNetlinkFileDescriptor() {
        try {
            FileDescriptor fd = NetlinkUtils.netlinkSocketForProto(NETLINK_GENERIC);
            NetlinkUtils.connectToKernel(fd);
            return fd;
        } catch (ErrnoException | SocketException e) {
            Log.e(TAG, "Unable to create Netlink file descriptor. " + e);
            return null;
        }
    }

    private boolean sendNl80211Message(@NonNull GenericNetlinkMsg message) {
        if (message == null) return false;
        Log.i(TAG, "Sending Nl80211 message: " + message);
        try {
            byte[] msgBytes = message.toByteArray();
            NetlinkUtils.sendMessage(
                    mNetlinkFd, msgBytes, 0, msgBytes.length, NetlinkUtils.IO_TIMEOUT_MS);
            return true;
        } catch (ErrnoException | IllegalArgumentException | InterruptedIOException e) {
            Log.i(TAG, "Unable to send Nl80211 message. " + e);
            return false;
        }
    }

    private @Nullable Nl80211Response receiveNl80211Response(@NonNull GenericNetlinkMsg sent) {
        List<GenericNetlinkMsg> messages = new ArrayList<>();
        boolean isAck = sent.isFlagEnabled(NLM_F_ACK);
        try {
            // The response may arrive in several batches, where each batch
            // can contain several individual messages.
            boolean isDone = false;
            while (!isDone) {
                // Receive a batch of messages into the receive buffer.
                ByteBuffer recvBuffer =
                        NetlinkUtils.recvMessage(
                                mNetlinkFd,
                                NetlinkUtils.DEFAULT_RECV_BUFSIZE,
                                NetlinkUtils.IO_TIMEOUT_MS);
                // Netlink requires native order
                recvBuffer.order(ByteOrder.nativeOrder());

                // Parse the individual messages from the batch.
                while (recvBuffer.remaining() > 0) {
                    recvBuffer.mark();

                    // Parse the Netlink header to determine the type of message
                    StructNlMsgHdr nlMsgHdr = StructNlMsgHdr.parse(recvBuffer);
                    if (nlMsgHdr == null) {
                        Log.e(TAG, "Unable to parse a received message");
                        mWifiMetrics.reportNl80211CommandResult(sent,
                                WifiStatsLog.WIFI_NL80211_COMMAND_RESULT_REPORTED__REASON_CODE__RESPONSE_NLMSG_NULL);
                        return null;
                    }

                    // Error should terminate the response and return the error code.
                    if (nlMsgHdr.nlmsg_type == NLMSG_ERROR) {
                        mWifiMetrics.reportNl80211CommandResult(sent,
                                WifiStatsLog.WIFI_NL80211_COMMAND_RESULT_REPORTED__REASON_CODE__RESPONSE_NLMSG_ERROR);
                        StructNlMsgErr errMsg = StructNlMsgErr.parse(recvBuffer);
                        if (errMsg == null) {
                            Log.e(TAG, "Failed to parse error message");
                            return null;
                        }
                        // Netlink returns errors as negative numbers, so we must reverse the sign
                        // to match with positive POSIX error codes.
                        int posixError = -errMsg.error;
                        Log.i(TAG, "Received NLMSG_ERROR with error " + posixError
                                + " for message " + sent);
                        if (!isAck && posixError == 0) {
                            Log.wtf(TAG, "Received unexpected NLMSG_ERROR ACK");
                            return null;
                        }
                        return new Nl80211Response(posixError);
                    }

                    // Done should terminate the response.
                    if (nlMsgHdr.nlmsg_type == NLMSG_DONE) {
                        Log.i(TAG, "Received NLMSG_DONE for message " + sent);
                        mWifiMetrics.reportNl80211CommandResult(sent,
                                WifiStatsLog.WIFI_NL80211_COMMAND_RESULT_REPORTED__REASON_CODE__RESPONSE_NLMSG_DONE);
                        isDone = true;
                        break;
                    }

                    // Not a control message -- try to parse as Generic Netlink.
                    recvBuffer.reset(); // Reset so GenericNetlinkMsg can parse the Netlink header.
                    GenericNetlinkMsg message = GenericNetlinkMsg.parse(recvBuffer);
                    if (message == null) {
                        Log.e(TAG, "Unable to parse a received message for " + sent);
                        mWifiMetrics.reportNl80211CommandResult(sent,
                                WifiStatsLog.WIFI_NL80211_COMMAND_RESULT_REPORTED__REASON_CODE__RESPONSE_NLMSG_NULL);
                        return null;
                    }
                    messages.add(message);

                    // Exit early if we aren't expecting multiple responses.
                    if (!message.isFlagEnabled(StructNlMsgHdr.NLM_F_MULTI)) {
                        Log.i(TAG, "Multi flag is not set");
                        mWifiMetrics.reportNl80211CommandResult(sent,
                                WifiStatsLog.WIFI_NL80211_COMMAND_RESULT_REPORTED__REASON_CODE__RESPONSE_DONE_NO_MULTI);
                        isDone = true;
                        break;
                    }
                }
            }
        } catch (ErrnoException | IllegalArgumentException | InterruptedIOException e) {
            mWifiMetrics.reportNl80211CommandResult(sent,
                    WifiStatsLog.WIFI_NL80211_COMMAND_RESULT_REPORTED__REASON_CODE__RESPONSE_NLMSG_EXCEPTION);
            Log.i(TAG, "Unable to receive Nl80211 messages. " + e);
            return null;
        }
        return new Nl80211Response(messages.toArray(new GenericNetlinkMsg[0]));
    }

    /**
     * Initialize this instance of Nl80211Proxy.
     *
     * @return true if initialization was successful, false otherwise
     */
    public boolean initialize() {
        if (!Flags.nl80211ProxyEnabled()) {
            Log.i(TAG, "Feature is not enabled");
            return false;
        }
        if (mIsInitialized) {
            Log.i(TAG, "Instance is already initialized");
            return true;
        }
        mNetlinkFd = createNetlinkFileDescriptor();
        if (mNetlinkFd == null) return false;
        if (!retrieveNl80211FamilyInfo()) return false;

        // If the family info was successfully retrieved above, then
        // all the required group IDs will be in the map.
        List<Integer> requiredGroupIds = new ArrayList<>();
        for (String groupName : sRequiredMulticastGroups) {
            requiredGroupIds.add(mMulticastGroups.get(groupName));
        }

        // Start the broadcast monitor on the background thread.
        // Received events will be posted to the Wifi thread.
        Handler backgroundHandler = BackgroundThread.getHandler();
        mBroadcastMonitor = new Nl80211BroadcastMonitor(
                backgroundHandler, mWifiHandler, requiredGroupIds);
        backgroundHandler.post(mBroadcastMonitor::start);

        Log.i(TAG, "Initialization was successful");
        mIsInitialized = true;
        return true;
    }

    /**
     * Send a GenericNetlinkMsg and receive the response.
     *
     * @param message Netlink message to be sent.
     * @return Nl80211Response, or null if there was an error sending or receiving the response.
     */
    public @Nullable Nl80211Response sendMessageAndReceiveResponse(
            @NonNull GenericNetlinkMsg message) {
        if (mNetlinkFd == null) {
            mWifiMetrics.reportNl80211CommandResult(message,
                    WifiStatsLog.WIFI_NL80211_COMMAND_RESULT_REPORTED__REASON_CODE__SEND_FD_UNAVAILABLE);
            Log.e(TAG, "Netlink file descriptor is not available");
            return null;
        }
        if (message == null) {
            mWifiMetrics.reportNl80211CommandResult(message,
                    WifiStatsLog.WIFI_NL80211_COMMAND_RESULT_REPORTED__REASON_CODE__SEND_NLMSG_NULL);
            Log.e(TAG, "Unable to send a null message");
            return null;
        }
        if (!sendNl80211Message(message)) {
            mWifiMetrics.reportNl80211CommandResult(message,
                    WifiStatsLog.WIFI_NL80211_COMMAND_RESULT_REPORTED__REASON_CODE__SEND_NLMSG_FAILED);
            return null;
        }
        return receiveNl80211Response(message);
    }

    /**
     * Asynchronously send a GenericNetlinkMsg and receive several responses.
     *
     * All interactions with Netlink will be handled on a separate handler, and the responses
     * will be sent to the provided listener once received.
     *
     * @param request Netlink message to be sent.
     * @param executor Executor on which to invoke the response listener.
     * @param listener Listener to invoke once the responses have been received.
     * @return true if the request was posted successfully, false otherwise
     */
    public boolean sendMessageAndReceiveResponsesAsync(@NonNull GenericNetlinkMsg request,
            @NonNull Executor executor, @NonNull NetlinkResponseListener listener) {
        if (!mIsInitialized) {
            Log.e(TAG, "Instance has not been initialized");
            return false;
        }
        if (request == null || executor == null || listener == null) {
            Log.e(TAG, "Null argument was provided");
            return false;
        }
        mWifiHandler.post(() -> {
            Nl80211Response response = sendMessageAndReceiveResponse(request);
            executor.execute(() -> listener.onResponse(response));
        });
        return true;
    }

    /**
     * Retrieve and store the family information for Nl80211.
     *
     * @return true if the information was retrieved successfully, false otherwise
     */
    private boolean retrieveNl80211FamilyInfo() {
        GenericNetlinkMsg request = new GenericNetlinkMsg(
                CTRL_CMD_GETFAMILY, GENL_ID_CTRL, StructNlMsgHdr.NLM_F_REQUEST,
                getSequenceNumber());
        request.addAttribute(new StructNlAttr(CTRL_ATTR_FAMILY_NAME, NL80211_GENL_NAME));

        Nl80211Response response = sendMessageAndReceiveResponse(request);
        if (response == null || response.isError() || response.getMessage() == null) {
            Log.e(TAG, "Failed to send CTRL_CMD_GETFAMILY");
            return false;
        }

        GenericNetlinkMsg msg = response.getMessage();
        if (!msg.verifyFields(CTRL_CMD_NEWFAMILY, CTRL_ATTR_FAMILY_ID, CTRL_ATTR_MCAST_GROUPS)) {
            Log.e(TAG, "Unable to request family information");
            return false;
        }

        Short familyId = msg.getAttributeValueAsShort(CTRL_ATTR_FAMILY_ID);
        if (familyId == null) {
            Log.e(TAG, "Unable to retrieve the Nl80211 family id");
            return false;
        }
        mNl80211FamilyId = familyId;

        Map<String, Integer> multicastGroups =
                parseMulticastGroupsAttribute(msg.getAttribute(CTRL_ATTR_MCAST_GROUPS));
        for (String groupName : sRequiredMulticastGroups) {
            if (!multicastGroups.containsKey(groupName)) {
                Log.e(TAG, "Missing required multicast group. Retrieved=" + multicastGroups);
                return false;
            }
        }
        mMulticastGroups = multicastGroups;

        Log.i(TAG, "Successfully retrieved Nl80211 family information");
        return true;
    }

    /**
     * Parse the nested multicast groups attribute.
     *
     * Expected structure is:
     *  - ID=CTRL_ATTR_MCAST_GROUPS, VAL={nested}                              ---
     *     - ID=1, VAL={nested}                              ---                |
     *        - ID=CTRL_ATTR_MCAST_GRP_NAME, VAL={string}     |   groupAttr     |
     *        - ID=CTRL_ATTR_MCAST_GRP_ID, VAL={int}         ---                |  rootAttr
     *     - ID=2, ATTR={nested}                                                |
     *     - ID=3, ATTR={nested}                                                |
     *     ...                                                                 ---
     */
    @VisibleForTesting
    protected static @NonNull Map<String, Integer> parseMulticastGroupsAttribute(
            StructNlAttr rootAttribute) {
        Map<Short, StructNlAttr> groupAttributes =
                GenericNetlinkMsg.getInnerNestedAttributes(rootAttribute);
        if (groupAttributes == null) return new HashMap<>();

        Map<String, Integer> multicastGroups = new HashMap<>();
        for (StructNlAttr groupAttribute : groupAttributes.values()) {
            Map<Short, StructNlAttr> groupInnerAttributes =
                    GenericNetlinkMsg.getInnerNestedAttributes(groupAttribute);
            if (groupInnerAttributes == null
                    || !groupInnerAttributes.containsKey(CTRL_ATTR_MCAST_GRP_NAME)
                    || !groupInnerAttributes.containsKey(CTRL_ATTR_MCAST_GRP_ID)) {
                continue;
            }

            String groupName =
                    groupInnerAttributes.get(CTRL_ATTR_MCAST_GRP_NAME).getValueAsString();
            Integer groupId = groupInnerAttributes.get(CTRL_ATTR_MCAST_GRP_ID).getValueAsInteger();
            if (groupName == null || groupId == null) {
                continue;
            }
            multicastGroups.put(groupName, groupId);
        }
        return multicastGroups;
    }

    /**
     * Wrapper to construct an Nl80211 request message.
     *
     * @param command Command ID for this request.
     * @param flags Flags for this request.
     * @param attributes Attributes for this request.
     * @return Nl80211 message, or null if the Nl80211Proxy has not been initialized
     */
    public @Nullable GenericNetlinkMsg createNl80211Request(
            short command, short flags, StructNlAttr... attributes) {
        if (!mIsInitialized) {
            Log.e(TAG, "Instance has not been initialized");
            return null;
        }
        GenericNetlinkMsg request =
                new GenericNetlinkMsg(
                        command,
                        mNl80211FamilyId,
                        (short) (flags | StructNlMsgHdr.NLM_F_REQUEST),
                        getSequenceNumber());
        for (StructNlAttr attribute : attributes) {
            request.addAttribute(attribute);
        }
        return request;
    }

    /**
     * Wrapper to construct an Nl80211 request message.
     *
     * @param command Command ID for this request.
     * @param attributes Attributes for this request.
     * @return Nl80211 message, or null if the Nl80211Proxy has not been initialized
     */
    public @Nullable GenericNetlinkMsg createNl80211Request(
            short command, StructNlAttr... attributes) {
        return createNl80211Request(command, (short) 0x0, attributes);
    }


    /**
     * Register a callback to trigger when the specified broadcast event command is received.
     *
     * @param command Type of broadcast command on which to trigger the callback.
     * @param callback Callback object that should be called.
     */
    public boolean registerBroadcastCallback(
            short command, @NonNull Nl80211BroadcastMonitor.Nl80211BroadcastCallback callback) {
        if (!mIsInitialized || mBroadcastMonitor == null) {
            Log.e(TAG, "Unable to register broadcast callback before initialization");
            return false;
        }
        mBroadcastMonitor.registerBroadcastCallback(command, callback);
        return true;
    }

    /**
     * Unregister a broadcast event callback that was previously registered.
     *
     * @param command Type of broadcast command which the callback is associated with.
     * @param callback Callback object which was registered.
     */
    public boolean unregisterBroadcastCallback(
            short command, @NonNull Nl80211BroadcastMonitor.Nl80211BroadcastCallback callback) {
        if (!mIsInitialized || mBroadcastMonitor == null) {
            Log.e(TAG, "Unable to unregister broadcast callback before initialization");
            return false;
        }
        mBroadcastMonitor.unregisterBroadcastCallback(command, callback);
        return true;
    }
}
