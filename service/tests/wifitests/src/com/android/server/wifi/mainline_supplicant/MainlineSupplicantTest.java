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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.net.wifi.util.Environment;
import android.os.IBinder;
import android.system.wifi.mainline_supplicant.IMainlineSupplicant;

import com.android.server.wifi.WifiNative;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Unit tests for {@link MainlineSupplicant}.
 */
public class MainlineSupplicantTest {
    private @Mock IMainlineSupplicant mIMainlineSupplicantMock;
    private @Mock IBinder mIBinderMock;
    private @Mock WifiNative.SupplicantDeathEventHandler mFrameworkDeathHandler;
    private MainlineSupplicantSpy mDut;

    private ArgumentCaptor<IBinder.DeathRecipient> mDeathRecipientCaptor =
            ArgumentCaptor.forClass(IBinder.DeathRecipient.class);

    // Spy version of this class allows us to override methods for testing.
    private class MainlineSupplicantSpy extends MainlineSupplicant {
        MainlineSupplicantSpy() {
            super();
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
}
