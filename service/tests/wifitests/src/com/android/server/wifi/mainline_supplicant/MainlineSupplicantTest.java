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

package com.android.server.wifi.mainline_supplicant;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.net.wifi.util.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.test.TestLooper;
import android.system.wifi.mainline_supplicant.IMainlineSupplicant;
import android.system.wifi.mainline_supplicant.IStaInterface;

import com.android.server.wifi.WifiNative;
import com.android.server.wifi.WifiThreadRunner;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Unit tests for {@link MainlineSupplicant}.
 */
public class MainlineSupplicantTest {
    private static final String IFACE_NAME = "wlan0";

    private @Mock IMainlineSupplicant mIMainlineSupplicantMock;
    private @Mock IBinder mIBinderMock;
    private @Mock WifiNative.SupplicantDeathEventHandler mFrameworkDeathHandler;
    private @Mock IStaInterface mIStaInterface;
    private MainlineSupplicantSpy mDut;
    private TestLooper mLooper = new TestLooper();

    private ArgumentCaptor<IBinder.DeathRecipient> mDeathRecipientCaptor =
            ArgumentCaptor.forClass(IBinder.DeathRecipient.class);

    // Spy version of this class allows us to override methods for testing.
    private class MainlineSupplicantSpy extends MainlineSupplicant {
        MainlineSupplicantSpy() {
            super(new WifiThreadRunner(new Handler(mLooper.getLooper())));
        }

        @Override
        protected IMainlineSupplicant getNewServiceBinderMockable() {
            return mIMainlineSupplicantMock;
        }
    }

    @Before
    public void setUp() throws Exception {
        assumeTrue(Environment.isSdkAtLeastB());
        MockitoAnnotations.initMocks(this);
        when(mIMainlineSupplicantMock.asBinder()).thenReturn(mIBinderMock);
        when(mIMainlineSupplicantMock.addStaInterface(anyString())).thenReturn(mIStaInterface);
        mDut = new MainlineSupplicantSpy();
    }

    private void validateServiceStart() throws Exception {
        assertTrue(mDut.startService());
        verify(mIBinderMock).linkToDeath(mDeathRecipientCaptor.capture(), anyInt());
        assertTrue(mDut.isActive());
    }

    private void validateServiceStop() {
        mDut.stopService();
        mDeathRecipientCaptor.getValue().binderDied(mIBinderMock);
        assertFalse(mDut.isActive());
    }

    /**
     * Verify that the class can be started and stopped successfully.
     */
    @Test
    public void testStartAndStopSuccess() throws Exception {
        validateServiceStart();
        validateServiceStop();
    }

    /**
     * Verify that unsolicited death notifications (ex. caused by a service crash)
     * are handled correctly.
     */
    @Test
    public void testUnsolicitedDeathNotification() throws Exception {
        validateServiceStart();

        // Notification with an unknown binder should be ignored
        IBinder otherBinder = mock(IBinder.class);
        mDeathRecipientCaptor.getValue().binderDied(otherBinder);
        assertTrue(mDut.isActive());

        // Notification with the correct binder should be handled
        mDeathRecipientCaptor.getValue().binderDied(mIBinderMock);
        assertFalse(mDut.isActive());
    }

    /**
     * Verify that the framework death handler is called on death, if registered.
     */
    @Test
    public void testRegisterFrameworkDeathHandler() throws Exception {
        validateServiceStart();
        mDut.registerFrameworkDeathHandler(mFrameworkDeathHandler);

        mDeathRecipientCaptor.getValue().binderDied(mIBinderMock);
        verify(mFrameworkDeathHandler, times(1)).onDeath();
    }

    /**
     * Verify that the framework death handler is not called on death,
     * if it has been unregistered.
     */
    @Test
    public void testUnregisterFrameworkDeathHandler() throws Exception {
        validateServiceStart();
        // Register and immediately unregister the framework death handler
        mDut.registerFrameworkDeathHandler(mFrameworkDeathHandler);
        mDut.unregisterFrameworkDeathHandler();

        mDeathRecipientCaptor.getValue().binderDied(mIBinderMock);
        verify(mFrameworkDeathHandler, never()).onDeath();
    }

    /**
     * Verify the behavior of {@link MainlineSupplicant#addStaInterface(String)}
     */
    @Test
    public void testAddStaInterface() throws Exception {
        validateServiceStart();
        assertTrue(mDut.addStaInterface(IFACE_NAME));

        // Re-adding an existing interface should return a result from the cache
        assertTrue(mDut.addStaInterface(IFACE_NAME));
        verify(mIMainlineSupplicantMock, times(1)).addStaInterface(anyString());
    }

    /**
     * Verify the behavior of {@link MainlineSupplicant#removeStaInterface(String)}
     */
    @Test
    public void testRemoveStaInterface() throws Exception {
        // Normal add and remove should succeed
        validateServiceStart();
        assertTrue(mDut.addStaInterface(IFACE_NAME));
        assertTrue(mDut.removeStaInterface(IFACE_NAME));

        // Removal a non-existent interface should fail
        assertFalse(mDut.removeStaInterface(IFACE_NAME)); // already removed
        assertFalse(mDut.removeStaInterface(IFACE_NAME + "new")); // never existed

        // Only the valid remove request should reach have reached the service
        verify(mIMainlineSupplicantMock, times(1)).removeStaInterface(anyString());
    }
}
