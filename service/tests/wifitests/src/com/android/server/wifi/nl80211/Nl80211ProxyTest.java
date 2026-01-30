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

import static com.android.server.wifi.nl80211.NetlinkConstants.CTRL_ATTR_FAMILY_ID;
import static com.android.server.wifi.nl80211.NetlinkConstants.CTRL_CMD_NEWFAMILY;
import static com.android.server.wifi.nl80211.NetlinkConstants.GENL_ID_CTRL;
import static com.android.server.wifi.nl80211.NetlinkConstants.NETLINK_GENERIC;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_MULTICAST_GROUP_MLME;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_MULTICAST_GROUP_REG;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_MULTICAST_GROUP_SCAN;
import static com.android.server.wifi.nl80211.NetlinkConstants.NLMSG_DONE;
import static com.android.server.wifi.nl80211.NetlinkConstants.NLMSG_ERROR;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.validateMockitoUsage;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import android.content.Context;
import android.net.util.SocketUtils;
import android.net.wifi.SynchronousExecutor;
import android.os.Handler;
import android.os.Looper;
import android.os.MessageQueue;
import android.os.test.TestLooper;
import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;

import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.modules.utils.BackgroundThread;
import com.android.net.module.util.netlink.NetlinkUtils;
import com.android.net.module.util.netlink.StructNlAttr;
import com.android.net.module.util.netlink.StructNlMsgErr;
import com.android.net.module.util.netlink.StructNlMsgHdr;
import com.android.server.wifi.Clock;
import com.android.server.wifi.FrameworkFacade;
import com.android.server.wifi.WifiDeviceStateChangeManager;
import com.android.server.wifi.WifiMetrics;
import com.android.server.wifi.proto.WifiStatsLog;
import com.android.wifi.flags.Flags;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;

import java.io.FileDescriptor;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

/**
 * Unit tests for {@link Nl80211Proxy}.
 */
public class Nl80211ProxyTest {
    private static final short TEST_FAMILY_ID = 25;

    private Nl80211Proxy mDut;
    private MockitoSession mSession;
    private TestLooper mWifiLooper;
    private Handler mWifiHandler;
    private WifiMetrics mWifiMetrics;

    @Mock FileDescriptor mFileDescriptor;
    @Mock Nl80211Proxy.NetlinkResponseListener mResponseListener;
    @Mock Handler mBackgroundHandler;
    @Mock Looper mBackgroundLooper;
    @Mock MessageQueue mBackgroundMessageQueue;
    @Mock Nl80211BroadcastMonitor.Nl80211BroadcastCallback mBroadcastCallback;
    @Mock Context mContext;
    @Mock FrameworkFacade mFacade;
    @Mock Clock mClock;
    @Mock WifiDeviceStateChangeManager mWifiDeviceStateChangeManager;

    @Captor ArgumentCaptor<Nl80211Response> mNl80211ResponseCaptor;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        mSession = ExtendedMockito.mockitoSession()
                .strictness(Strictness.LENIENT)
                .mockStatic(BackgroundThread.class, withSettings().lenient())
                .mockStatic(Flags.class, withSettings().lenient())
                .mockStatic(NetlinkUtils.class, withSettings().lenient())
                .mockStatic(Os.class)
                .mockStatic(SocketUtils.class)
                .mockStatic(WifiStatsLog.class)
                .startMocking();
        when(Os.socket(anyInt(), anyInt(), anyInt())).thenReturn(mFileDescriptor);
        when(Flags.nl80211ProxyEnabled()).thenReturn(true);

        // Use a test looper to dispatch events in the tests.
        mWifiLooper = new TestLooper();
        mWifiHandler = new Handler(mWifiLooper.getLooper());
        mWifiMetrics =
                new WifiMetrics(
                        mContext,
                        mFacade,
                        mClock,
                        mWifiLooper.getLooper(),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        mWifiDeviceStateChangeManager,
                        null);

        // Mock the background thread to avoid running the broadcast monitor.
        when(BackgroundThread.getHandler()).thenReturn(mBackgroundHandler);
        when(mBackgroundHandler.getLooper()).thenReturn(mBackgroundLooper);
        when(mBackgroundLooper.getQueue()).thenReturn(mBackgroundMessageQueue);

        mDut = new Nl80211Proxy(mWifiHandler, mWifiMetrics);
        initializeDut();
    }

    @After
    public void cleanup() {
        validateMockitoUsage();
        if (mSession != null) {
            mSession.finishMocking();
        }
    }

    @Test
    public void testCreateNetlinkFileDescriptor_blocking_success() {
        FileDescriptor fd = Nl80211Proxy.createNetlinkFileDescriptor(false);
        assertNotNull(fd);
        // Adjust times() to account for setUp() already creating a blocking FD.
        ExtendedMockito.verify(() -> Os.socket(
                OsConstants.AF_NETLINK,
                OsConstants.SOCK_DGRAM | OsConstants.SOCK_CLOEXEC,
                NETLINK_GENERIC), times(2));
        ExtendedMockito.verify(() -> NetlinkUtils.connectToKernel(fd), times(2));
    }

    @Test
    public void testCreateNetlinkFileDescriptor_nonBlocking_success() {
        FileDescriptor fd = Nl80211Proxy.createNetlinkFileDescriptor(true);
        assertNotNull(fd);
        ExtendedMockito.verify(() -> Os.socket(
                OsConstants.AF_NETLINK,
                OsConstants.SOCK_DGRAM | OsConstants.SOCK_CLOEXEC | OsConstants.SOCK_NONBLOCK,
                NETLINK_GENERIC));
        // Adjust times() to account for setUp() already creating a blocking FD.
        ExtendedMockito.verify(() -> NetlinkUtils.connectToKernel(fd), times(2));
    }

    @Test
    public void testCreateNetlinkFileDescriptor_failure() {
        ExtendedMockito.doThrow(new ErrnoException("Failed to create socket", OsConstants.EACCES))
                .when(() -> Os.socket(anyInt(), anyInt(), anyInt()));

        FileDescriptor actualFd = Nl80211Proxy.createNetlinkFileDescriptor(false);
        assertNull(actualFd);
        // Adjust times() to account for setUp() already creating a blocking FD.
        ExtendedMockito.verify(() -> NetlinkUtils.connectToKernel(any()), times(1));
    }

    private void initializeDut() throws Exception {
        GenericNetlinkMsg familyResponse = new GenericNetlinkMsg(
                CTRL_CMD_NEWFAMILY, GENL_ID_CTRL, (short) 0, 0);
        familyResponse.addAttribute(
                new StructNlAttr(CTRL_ATTR_FAMILY_ID, TEST_FAMILY_ID));
        familyResponse.addAttribute(Nl80211TestUtils.createMulticastGroupsAttribute());
        setResponseMessage(familyResponse);
        assertTrue(mDut.initialize());
    }

    /**
     * Convert GenericNetlinkMsg to ByteBuffer.
     */
    private ByteBuffer genericNetlinkMsgToByteBuffer(
            GenericNetlinkMsg responseMessage) throws Exception {
        ByteBuffer responseMsgBuffer =
                Nl80211TestUtils.createByteBuffer(responseMessage.nlHeader.nlmsg_len);
        responseMessage.pack(responseMsgBuffer);
        responseMsgBuffer.position(0); // reset to the beginning of the buffer
        return responseMsgBuffer;
    }

    /**
     * Pack several instances of GenericNetlinkMsg into a ByteBuffer.
     */
    private ByteBuffer genericNetlinkMessagesToByteBuffer(
            GenericNetlinkMsg... messages) throws Exception {
        int requiredBufSize = 0;
        for (GenericNetlinkMsg message : messages) {
            requiredBufSize += message.nlHeader.nlmsg_len;
        }
        ByteBuffer buffer = Nl80211TestUtils.createByteBuffer(requiredBufSize);
        for (GenericNetlinkMsg message : messages) {
            message.pack(buffer);
        }
        buffer.position(0); // reset to the beginning of the buffer
        return buffer;
    }

    /**
     * Set the response returned by {@link NetlinkUtils#recvMessage(FileDescriptor, int, long)}
     */
    private void setResponseMessage(GenericNetlinkMsg responseMessage) throws Exception {
        ByteBuffer responseMsgBuffer = genericNetlinkMsgToByteBuffer(responseMessage);
        when(NetlinkUtils.recvMessage(any(), anyInt(), anyLong())).thenReturn(responseMsgBuffer);
    }

    /**
     * Test that the initialization logic is only run once, even if the initialize
     * method is called several times.
     */
    @Test
    public void testRepeatedInitialization() {
        // Verify that the broadcast monitor was started on the
        // background thread during the first initialization.
        verify(mBackgroundHandler).post(any());
        verify(mBackgroundHandler).getLooper();

        // Rerunning initialize should not result in starting
        // a new broadcast monitor on the background thread.
        assertTrue(mDut.initialize());
        verifyNoMoreInteractions(mBackgroundHandler);
    }

    /**
     * Test that we can successfully send an Nl80211 message and receive a response using
     * the synchronous send/receive method.
     */
    @Test
    public void testSendAndReceiveMessage() throws Exception {
        // Use a non-default command id to identify this as the response message
        GenericNetlinkMsg expectedMsg = new GenericNetlinkMsg(
                (short) (Nl80211TestUtils.TEST_COMMAND + 15),
                Nl80211TestUtils.TEST_TYPE,
                Nl80211TestUtils.TEST_FLAGS,
                Nl80211TestUtils.TEST_SEQUENCE);
        Nl80211Response expectedResponse = new Nl80211Response(expectedMsg);
        setResponseMessage(expectedMsg);
        GenericNetlinkMsg requestMsg = Nl80211TestUtils.createTestMessage();
        Nl80211Response receivedResponse = mDut.sendMessageAndReceiveResponse(requestMsg);
        assertTrue(expectedResponse.equals(receivedResponse));
    }

    /**
     * Test that the messages after the error response are ignored, and we get the error code in
     * the response.
     */
    @Test
    public void testSendAndReceiveMessage_errorResponse() throws Exception {
        GenericNetlinkMsg requestMsg = Nl80211TestUtils.createTestMessage();

        // Create the error message, which should contain the error code followed by the Netlink
        // header of the original request.
        StructNlMsgHdr errorHdr = new StructNlMsgHdr(
                StructNlMsgErr.STRUCT_SIZE,
                NLMSG_ERROR,
                StructNlMsgHdr.NLM_F_MULTI,
                Nl80211TestUtils.TEST_SEQUENCE);
        ByteBuffer errorBuf = Nl80211TestUtils.createByteBuffer(errorHdr.nlmsg_len);
        errorHdr.pack(errorBuf);
        errorBuf.putInt(-OsConstants.ENOENT); // Netlink error codes are negated.
        requestMsg.nlHeader.pack(errorBuf);
        errorBuf.position(0);

        // Message after the error response should be ignored.
        GenericNetlinkMsg extraResponse = new GenericNetlinkMsg(
                (short) (Nl80211TestUtils.TEST_COMMAND + 15),
                Nl80211TestUtils.TEST_TYPE,
                StructNlMsgHdr.NLM_F_MULTI,
                Nl80211TestUtils.TEST_SEQUENCE + 1);

        when(NetlinkUtils.recvMessage(any(), anyInt(), anyLong()))
                .thenReturn(errorBuf)
                .thenReturn(genericNetlinkMsgToByteBuffer(extraResponse));

        Nl80211Response response = mDut.sendMessageAndReceiveResponse(requestMsg);

        assertTrue(response.getMessages().isEmpty());
        assertTrue(response.isError());
        assertEquals(OsConstants.ENOENT, response.getErrorCode());
        ExtendedMockito.verify(() -> WifiStatsLog.write(
                WifiStatsLog.WIFI_NL80211_COMMAND_RESULT_REPORTED,
                requestMsg.getCommand(),
                WifiStatsLog.WIFI_NL80211_COMMAND_RESULT_REPORTED__REASON_CODE__RESPONSE_NLMSG_ERROR));
    }

    /**
     * Test that we return an empty non-error response for an ack (ERROR message with code 0).
     */
    @Test
    public void testSendAndReceiveMessage_ackResponse() throws Exception {
        GenericNetlinkMsg requestMsg = Nl80211TestUtils.createTestMessageWithAckFlag();

        // Create the error message, which should contain the error code followed by the Netlink
        // header of the original request.
        StructNlMsgHdr errorHdr = new StructNlMsgHdr(
                StructNlMsgErr.STRUCT_SIZE,
                NLMSG_ERROR,
                StructNlMsgHdr.NLM_F_MULTI,
                Nl80211TestUtils.TEST_SEQUENCE);
        ByteBuffer errorBuf = Nl80211TestUtils.createByteBuffer(errorHdr.nlmsg_len);
        errorHdr.pack(errorBuf);
        errorBuf.putInt(0);
        requestMsg.nlHeader.pack(errorBuf);
        errorBuf.position(0);

        when(NetlinkUtils.recvMessage(any(), anyInt(), anyLong()))
                .thenReturn(errorBuf);

        Nl80211Response response = mDut.sendMessageAndReceiveResponse(requestMsg);

        assertNotNull(response);
        assertFalse(response.isError());
    }

    /**
     * Test that we return null for an ack response (ERROR message with code 0) if we didn't set the
     * ack flag.
     */
    @Test
    public void testSendAndReceiveMessage_ackResponseWithoutAckFlag_returnsNull() throws Exception {
        GenericNetlinkMsg requestMsg = Nl80211TestUtils.createTestMessage();

        // Create the error message, which should contain the error code followed by the Netlink
        // header of the original request.
        StructNlMsgHdr errorHdr = new StructNlMsgHdr(
                StructNlMsgErr.STRUCT_SIZE,
                NLMSG_ERROR,
                StructNlMsgHdr.NLM_F_MULTI,
                Nl80211TestUtils.TEST_SEQUENCE);
        ByteBuffer errorBuf = Nl80211TestUtils.createByteBuffer(errorHdr.nlmsg_len);
        errorHdr.pack(errorBuf);
        errorBuf.putInt(0);
        requestMsg.nlHeader.pack(errorBuf);
        errorBuf.position(0);

        when(NetlinkUtils.recvMessage(any(), anyInt(), anyLong()))
                .thenReturn(errorBuf);

        Nl80211Response response = mDut.sendMessageAndReceiveResponse(requestMsg);

        assertNull(response);
    }

    /**
     * Test that we can successfully send an Nl80211 message and receive multi part response using
     * the synchronous send/receive method.
     */
    @Test
    public void testSendAndReceiveMessage_multiPartResponse() throws Exception {
        // First batch will contain two messages
        GenericNetlinkMsg msg1 = new GenericNetlinkMsg(
                (short) (Nl80211TestUtils.TEST_COMMAND + 15),
                Nl80211TestUtils.TEST_TYPE,
                StructNlMsgHdr.NLM_F_MULTI,
                Nl80211TestUtils.TEST_SEQUENCE);
        GenericNetlinkMsg msg2 = new GenericNetlinkMsg(
                (short) (Nl80211TestUtils.TEST_COMMAND + 16),
                Nl80211TestUtils.TEST_TYPE,
                StructNlMsgHdr.NLM_F_MULTI,
                Nl80211TestUtils.TEST_SEQUENCE);
        // Second batch will contain a message with NLMSG_DONE,
        // marking the end of the multipart response
        StructNlMsgHdr doneHdr = new StructNlMsgHdr(
                0,
                NLMSG_DONE,
                StructNlMsgHdr.NLM_F_MULTI,
                Nl80211TestUtils.TEST_SEQUENCE);
        ByteBuffer doneBuf = Nl80211TestUtils.createByteBuffer(doneHdr.nlmsg_len);
        doneHdr.pack(doneBuf);
        doneBuf.position(0);

        when(NetlinkUtils.recvMessage(any(), anyInt(), anyLong()))
                .thenReturn(genericNetlinkMessagesToByteBuffer(msg1, msg2))
                .thenReturn(doneBuf);
        GenericNetlinkMsg requestMsg = Nl80211TestUtils.createTestMessage();

        Nl80211Response response = mDut.sendMessageAndReceiveResponse(requestMsg);

        List<GenericNetlinkMsg> messages = response.getMessages();
        assertEquals(2, messages.size());
        assertTrue(msg1.equals(messages.get(0)));
        assertTrue(msg2.equals(messages.get(1)));
        ExtendedMockito.verify(() -> WifiStatsLog.write(
                WifiStatsLog.WIFI_NL80211_COMMAND_RESULT_REPORTED,
                requestMsg.getCommand(),
                WifiStatsLog.WIFI_NL80211_COMMAND_RESULT_REPORTED__REASON_CODE__RESPONSE_NLMSG_DONE));
    }

    @Test
    public void testReceiveNl80211Response_mixedSequenceNumbers_skipsMismatchedMessages()
            throws Exception {
        final int sentSeqNum = 10;
        final int mismatchedSeqNum = sentSeqNum + 1;

        GenericNetlinkMsg requestMsg = new GenericNetlinkMsg(
                Nl80211TestUtils.TEST_COMMAND,
                Nl80211TestUtils.TEST_TYPE,
                Nl80211TestUtils.TEST_FLAGS,
                sentSeqNum);
        GenericNetlinkMsg correctResponse = new GenericNetlinkMsg(
                (short) (Nl80211TestUtils.TEST_COMMAND + 2),
                Nl80211TestUtils.TEST_TYPE,
                StructNlMsgHdr.NLM_F_MULTI,
                sentSeqNum);
        StructNlMsgHdr doneHdr = new StructNlMsgHdr(
                0, NLMSG_DONE, StructNlMsgHdr.NLM_F_MULTI, sentSeqNum);
        ByteBuffer doneBuf = Nl80211TestUtils.createByteBuffer(doneHdr.nlmsg_len);
        doneHdr.pack(doneBuf);
        doneBuf.position(0);

        GenericNetlinkMsg mismatchedResponse = new GenericNetlinkMsg(
                (short) (Nl80211TestUtils.TEST_COMMAND + 1),
                Nl80211TestUtils.TEST_TYPE,
                StructNlMsgHdr.NLM_F_MULTI,
                mismatchedSeqNum);
        StructNlMsgHdr mismatchedDoneHdr = new StructNlMsgHdr(
                0, NLMSG_DONE, StructNlMsgHdr.NLM_F_MULTI, mismatchedSeqNum);
        ByteBuffer mismatchedDoneBuf =
                Nl80211TestUtils.createByteBuffer(mismatchedDoneHdr.nlmsg_len);
        mismatchedDoneHdr.pack(mismatchedDoneBuf);
        mismatchedDoneBuf.position(0);

        when(NetlinkUtils.recvMessage(any(), anyInt(), anyLong()))
                .thenReturn(genericNetlinkMessagesToByteBuffer(mismatchedResponse))
                .thenReturn(mismatchedDoneBuf)
                .thenReturn(genericNetlinkMessagesToByteBuffer(correctResponse))
                .thenReturn(doneBuf);

        Nl80211Response response = mDut.sendMessageAndReceiveResponse(requestMsg);

        // Mismatched message should be skipped, but we should still read the matching message.
        assertNotNull(response);
        assertFalse(response.isError());
        assertEquals(1, response.getMessages().size());
        assertTrue(correctResponse.equals(response.getMessages().get(0)));
    }

    /**
     * Test that we can successfully send an Nl80211 message and receive a response using
     * the asynchronous send/receive method.
     */
    @Test
    public void testSendAndReceiveMessageAsync() throws Exception {
        // Initial request will be posted to the async handler, but should not execute
        GenericNetlinkMsg requestMsg = Nl80211TestUtils.createTestMessage();
        Executor executor = new SynchronousExecutor();
        assertTrue(mDut.sendMessageAndReceiveResponsesAsync(
                requestMsg, executor, mResponseListener));
        verify(mResponseListener, never()).onResponse(any());

        // Send and receive messages on the async handler
        GenericNetlinkMsg msg = Nl80211TestUtils.createTestMessage();
        setResponseMessage(msg);
        mWifiLooper.dispatchAll();

        verify(mResponseListener).onResponse(mNl80211ResponseCaptor.capture());
        assertTrue(msg.equals(mNl80211ResponseCaptor.getValue().getMessages().get(0)));

        ExtendedMockito.verify(() -> WifiStatsLog.write(
                WifiStatsLog.WIFI_NL80211_COMMAND_RESULT_REPORTED,
                requestMsg.getCommand(),
                WifiStatsLog.WIFI_NL80211_COMMAND_RESULT_REPORTED__REASON_CODE__RESPONSE_DONE_NO_MULTI));
    }

    /**
     * Test that an Nl80211 request can be created once the Nl80211Proxy has been initialized.
     */
    @Test
    public void testCreateNl80211Request() throws Exception {
        // Expect failure if the Nl80211Proxy has not been initialized
        mDut = new Nl80211Proxy(mWifiHandler, mWifiMetrics);
        assertNull(mDut.createNl80211Request(Nl80211TestUtils.TEST_COMMAND));

        // Expect that the message can be created after initialization,
        // since the Nl80211 family ID has been retrieved
        initializeDut();
        GenericNetlinkMsg message = mDut.createNl80211Request(Nl80211TestUtils.TEST_COMMAND);
        assertEquals(TEST_FAMILY_ID, message.nlHeader.nlmsg_type);
    }

    /**
     * Test that {@link Nl80211Proxy#parseMulticastGroupsAttribute(StructNlAttr)} can parse
     * a valid multicast groups attribute.
     */
    @Test
    public void testParseMulticastGroupsAttribute() {
        StructNlAttr multicastGroupsAttribute = Nl80211TestUtils.createMulticastGroupsAttribute();
        Map<String, Integer> parsedMulticastGroups =
                Nl80211Proxy.parseMulticastGroupsAttribute(multicastGroupsAttribute);
        // Result is expected to contain all the required groups
        assertTrue(parsedMulticastGroups.containsKey(NL80211_MULTICAST_GROUP_SCAN));
        assertTrue(parsedMulticastGroups.containsKey(NL80211_MULTICAST_GROUP_REG));
        assertTrue(parsedMulticastGroups.containsKey(NL80211_MULTICAST_GROUP_MLME));
    }

    /**
     * Test that a broadcast callback can be successfully registered and unregistered
     * after this instance has been initialized;
     */
    @Test
    public void testRegisterAndUnregisterBroadcastCallback() throws Exception {
        short eventType = 123;
        mDut = new Nl80211Proxy(mWifiHandler, mWifiMetrics);

        // Expect failure before initialization
        assertFalse(mDut.registerBroadcastCallback(eventType, mBroadcastCallback));
        assertFalse(mDut.unregisterBroadcastCallback(eventType, mBroadcastCallback));

        // Registration should succeed after initialization
        initializeDut();
        assertTrue(mDut.registerBroadcastCallback(eventType, mBroadcastCallback));
        assertTrue(mDut.unregisterBroadcastCallback(eventType, mBroadcastCallback));
    }

    /**
     * Test that a vendor request can be successfully created.
     */
    @Test
    public void testCreateVendorRequest() {
        final int ifIndex = 3;
        final int vendorId = 123;
        final int subcmd = 456;
        GenericNetlinkMsg msg = mDut.createVendorRequest(ifIndex, vendorId, subcmd);
        assertNotNull(msg);
        assertEquals(NetlinkConstants.NL80211_CMD_VENDOR, msg.getCommand());
        assertEquals(ifIndex,
                (int) msg.getAttributeValueAsInteger(NetlinkConstants.NL80211_ATTR_IFINDEX));
        assertEquals(vendorId,
                (int) msg.getAttributeValueAsInteger(NetlinkConstants.NL80211_ATTR_VENDOR_ID));
        assertEquals(subcmd,
                (int) msg.getAttributeValueAsInteger(NetlinkConstants.NL80211_ATTR_VENDOR_SUBCMD));
    }
}
