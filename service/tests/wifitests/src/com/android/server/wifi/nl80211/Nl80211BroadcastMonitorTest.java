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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.validateMockitoUsage;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import android.net.util.SocketUtils;
import android.os.Handler;
import android.os.Looper;
import android.os.MessageQueue;
import android.os.test.TestLooper;
import android.system.Os;

import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.net.module.util.netlink.NetlinkUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;

import java.io.FileDescriptor;
import java.util.Arrays;
import java.util.List;

/**
 * Unit tests for {@link Nl80211BroadcastMonitor}.
 */
public class Nl80211BroadcastMonitorTest {
    private static final List<Integer> TEST_GROUP_IDS = Arrays.asList(10, 11, 12);
    private static final GenericNetlinkMsg TEST_MESSAGE = Nl80211TestUtils.createTestMessage();
    private static final byte[] TEST_MESSAGE_BUFFER = TEST_MESSAGE.toByteArray();

    private Nl80211BroadcastMonitor mDut;
    private MockitoSession mSession;
    private TestLooper mWifiLooper;
    private Handler mWifiHandler;

    @Mock FileDescriptor mFileDescriptor;
    @Mock Handler mBackgroundHandler;
    @Mock Looper mBackgroundLooper;
    @Mock MessageQueue mBackgroundMessageQueue;
    @Mock Nl80211BroadcastMonitor.Nl80211BroadcastCallback mEventCallback;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        mSession = ExtendedMockito.mockitoSession()
                .strictness(Strictness.LENIENT)
                .mockStatic(Nl80211Proxy.class, withSettings().lenient())
                .mockStatic(Os.class, withSettings().lenient())
                .mockStatic(SocketUtils.class, withSettings().lenient())
                .startMocking();
        when(mBackgroundHandler.getLooper()).thenReturn(mBackgroundLooper);
        when(mBackgroundLooper.getQueue()).thenReturn(mBackgroundMessageQueue);
        mWifiLooper = new TestLooper();
        mWifiHandler = new Handler(mWifiLooper.getLooper());
        mDut = new Nl80211BroadcastMonitor(mBackgroundHandler, mWifiHandler, TEST_GROUP_IDS);
    }

    @After
    public void cleanup() {
        validateMockitoUsage();
        if (mSession != null) {
            mSession.finishMocking();
        }
    }

    /**
     * Verify that {@link Nl80211BroadcastMonitor#createFd()} returns a valid file descriptor
     * when successful, and null if an error is encountered.
     */
    @Test
    public void testCreateFd() {
        // Successful case
        when(Nl80211Proxy.createNetlinkFileDescriptor(/* nonBlocking */ true))
                .thenReturn(mFileDescriptor);
        assertEquals(mFileDescriptor, mDut.createFd());

        // Error while creating the file descriptor
        when(Nl80211Proxy.createNetlinkFileDescriptor(/* nonBlocking */ true))
                .thenReturn(null);
        assertNull(mDut.createFd());
    }

    /**
     * Verify that received packets are only posted to the Wifi handler if the provided
     * buffer is valid.
     */
    @Test
    public void testHandlePacketValidCheck() {
        // Null buffers should be rejected.
        mDut.handlePacket(null, 0);
        assertFalse(mWifiLooper.isIdle()); // no packets in the message queue

        // Buffers with an invalid declared size should be rejected.
        mDut.handlePacket(TEST_MESSAGE_BUFFER, 0);
        mDut.handlePacket(TEST_MESSAGE_BUFFER, NetlinkUtils.DEFAULT_RECV_BUFSIZE + 1);
        assertFalse(mWifiLooper.isIdle());

        // Buffers smaller than the declared size should be rejected.
        int declaredBufferSize = 10;
        mDut.handlePacket(new byte[declaredBufferSize - 1], declaredBufferSize);
        assertFalse(mWifiLooper.isIdle());

        // Buffers with a valid length should be posted to the Wifi thread for handling.
        mDut.handlePacket(TEST_MESSAGE_BUFFER, TEST_MESSAGE_BUFFER.length);
        assertTrue(mWifiLooper.isIdle()); // packet is in the message queue
    }

    /**
     * Verify that callbacks are only triggered when they are registered for a
     * specific type of event.
     */
    @Test
    public void testRegisterAndUnregisterCallback() {
        for (int i = 0; i < 4; i++) {
            // Receive several messages of the same type.
            mDut.handlePacket(TEST_MESSAGE_BUFFER, TEST_MESSAGE_BUFFER.length);
        }

        // No callbacks should be triggered before registration.
        mWifiLooper.dispatchNext();
        verifyNoInteractions(mEventCallback);

        // Callback should not be triggered if registered for a different event type.
        short eventType = TEST_MESSAGE.genNlHeader.command;
        mDut.registerBroadcastCallback((short) (eventType + 1), mEventCallback);
        mWifiLooper.dispatchNext();
        verifyNoInteractions(mEventCallback);

        // Callback should be triggered if registered for the expected event type.
        mDut.registerBroadcastCallback(eventType, mEventCallback);
        mWifiLooper.dispatchNext();
        verify(mEventCallback).onEvent(eq(eventType), any());

        // Callback should not be triggered after it has been unregistered.
        mDut.unregisterBroadcastCallback(eventType, mEventCallback);
        mWifiLooper.dispatchNext();
        verifyNoMoreInteractions(mEventCallback);
    }
}
