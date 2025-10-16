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
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_MULTICAST_GROUP_MLME;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_MULTICAST_GROUP_REG;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_MULTICAST_GROUP_SCAN;
import static com.android.server.wifi.nl80211.NetlinkConstants.NLMSG_DONE;
import static com.android.server.wifi.nl80211.NetlinkConstants.NLMSG_ERROR;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.validateMockitoUsage;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import android.net.util.SocketUtils;
import android.net.wifi.SynchronousExecutor;
import android.os.Handler;
import android.os.Looper;
import android.os.MessageQueue;
import android.os.test.TestLooper;
import android.system.Os;

import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.modules.utils.BackgroundThread;
import com.android.net.module.util.netlink.NetlinkUtils;
import com.android.net.module.util.netlink.StructNlAttr;
import com.android.net.module.util.netlink.StructNlMsgHdr;
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

    @Mock FileDescriptor mFileDescriptor;
    @Mock Nl80211Proxy.NetlinkResponseListener mResponseListener;
    @Mock Handler mBackgroundHandler;
    @Mock Looper mBackgroundLooper;
    @Mock MessageQueue mBackgroundMessageQueue;
    @Mock Nl80211BroadcastMonitor.Nl80211BroadcastCallback mBroadcastCallback;

    @Captor ArgumentCaptor<List<GenericNetlinkMsg>> mMessageListCaptor;

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
                .startMocking();
        when(NetlinkUtils.netlinkSocketForProto(anyInt())).thenReturn(mFileDescriptor);
        when(Flags.nl80211ProxyEnabled()).thenReturn(true);

        // Use a test looper to dispatch events in the tests.
        mWifiLooper = new TestLooper();
        mWifiHandler = new Handler(mWifiLooper.getLooper());

        // Mock the background thread to avoid running the broadcast monitor.
        when(BackgroundThread.getHandler()).thenReturn(mBackgroundHandler);
        when(mBackgroundHandler.getLooper()).thenReturn(mBackgroundLooper);
        when(mBackgroundLooper.getQueue()).thenReturn(mBackgroundMessageQueue);

        mDut = new Nl80211Proxy(mWifiHandler);
        initializeDut();
    }

    @After
    public void cleanup() {
        validateMockitoUsage();
        if (mSession != null) {
            mSession.finishMocking();
        }
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
        GenericNetlinkMsg expectedResponse = new GenericNetlinkMsg(
                (short) (Nl80211TestUtils.TEST_COMMAND + 15),
                Nl80211TestUtils.TEST_TYPE,
                Nl80211TestUtils.TEST_FLAGS,
                Nl80211TestUtils.TEST_SEQUENCE);
        setResponseMessage(expectedResponse);
        GenericNetlinkMsg requestMsg = Nl80211TestUtils.createTestMessage();
        GenericNetlinkMsg receivedResponse = mDut.sendMessageAndReceiveResponse(requestMsg);
        assertTrue(expectedResponse.equals(receivedResponse));
    }

    /**
     * Test that the messages after the error response are ignored.
     */
    @Test
    public void testSendAndReceiveMessage_errorResponse() throws Exception {
        // Use a non-default command id to identify this as the response message
        GenericNetlinkMsg errorResponse = new GenericNetlinkMsg(
                (short) (Nl80211TestUtils.TEST_COMMAND + 15),
                NLMSG_ERROR,
                Nl80211TestUtils.TEST_FLAGS,
                Nl80211TestUtils.TEST_SEQUENCE);
        // Message after the error response should be ignored.
        GenericNetlinkMsg extraResponse = new GenericNetlinkMsg(
                (short) (Nl80211TestUtils.TEST_COMMAND + 15),
                Nl80211TestUtils.TEST_TYPE,
                StructNlMsgHdr.NLM_F_MULTI,
                Nl80211TestUtils.TEST_SEQUENCE + 1);
        when(NetlinkUtils.recvMessage(any(), anyInt(), anyLong()))
                .thenReturn(genericNetlinkMsgToByteBuffer(errorResponse))
                .thenReturn(genericNetlinkMsgToByteBuffer(extraResponse));
        GenericNetlinkMsg requestMsg = Nl80211TestUtils.createTestMessage();
        List<GenericNetlinkMsg> receivedResponses = mDut.sendMessageAndReceiveResponses(requestMsg);
        assertEquals(1, receivedResponses.size());
        assertTrue(errorResponse.equals(receivedResponses.get(0)));
    }

    /**
     * Test that we can successfully send an Nl80211 message and receive multi part response using
     * the synchronous send/receive method.
     */
    @Test
    public void testSendAndReceiveMessage_multiPartResponse() throws Exception {
        // First batch will contain two messages
        GenericNetlinkMsg response1 = new GenericNetlinkMsg(
                (short) (Nl80211TestUtils.TEST_COMMAND + 15),
                Nl80211TestUtils.TEST_TYPE,
                StructNlMsgHdr.NLM_F_MULTI,
                Nl80211TestUtils.TEST_SEQUENCE);
        GenericNetlinkMsg response2 = new GenericNetlinkMsg(
                (short) (Nl80211TestUtils.TEST_COMMAND + 16),
                Nl80211TestUtils.TEST_TYPE,
                StructNlMsgHdr.NLM_F_MULTI,
                Nl80211TestUtils.TEST_SEQUENCE);
        // Second batch will contain a message with NLMSG_DONE,
        // marking the end of the multipart response
        GenericNetlinkMsg doneResponse = new GenericNetlinkMsg(
                (short) (Nl80211TestUtils.TEST_COMMAND + 17),
                NLMSG_DONE,
                StructNlMsgHdr.NLM_F_MULTI,
                Nl80211TestUtils.TEST_SEQUENCE);
        when(NetlinkUtils.recvMessage(any(), anyInt(), anyLong()))
                .thenReturn(genericNetlinkMessagesToByteBuffer(response1, response2))
                .thenReturn(genericNetlinkMsgToByteBuffer(doneResponse));
        GenericNetlinkMsg requestMsg = Nl80211TestUtils.createTestMessage();
        List<GenericNetlinkMsg> receivedResponses = mDut.sendMessageAndReceiveResponses(requestMsg);
        assertEquals(3, receivedResponses.size());
        assertTrue(response1.equals(receivedResponses.get(0)));
        assertTrue(response2.equals(receivedResponses.get(1)));
        assertTrue(doneResponse.equals(receivedResponses.get(2)));
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
        GenericNetlinkMsg response = Nl80211TestUtils.createTestMessage();
        setResponseMessage(response);
        mWifiLooper.dispatchAll();

        verify(mResponseListener).onResponse(mMessageListCaptor.capture());
        assertTrue(response.equals(mMessageListCaptor.getValue().get(0)));
    }

    /**
     * Test that an Nl80211 request can be created once the Nl80211Proxy has been initialized.
     */
    @Test
    public void testCreateNl80211Request() throws Exception {
        // Expect failure if the Nl80211Proxy has not been initialized
        mDut = new Nl80211Proxy(mWifiHandler);
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
        mDut = new Nl80211Proxy(mWifiHandler);

        // Expect failure before initialization
        assertFalse(mDut.registerBroadcastCallback(eventType, mBroadcastCallback));
        assertFalse(mDut.unregisterBroadcastCallback(eventType, mBroadcastCallback));

        // Registration should succeed after initialization
        initializeDut();
        assertTrue(mDut.registerBroadcastCallback(eventType, mBroadcastCallback));
        assertTrue(mDut.unregisterBroadcastCallback(eventType, mBroadcastCallback));
    }
}
