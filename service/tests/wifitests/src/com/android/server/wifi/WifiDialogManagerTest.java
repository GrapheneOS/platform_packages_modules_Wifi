/*
 * Copyright 2022 The Android Open Source Project
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

package com.android.server.wifi;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.ComponentName;
import android.content.Intent;
import android.content.res.Resources;
import android.net.wifi.WifiContext;
import android.net.wifi.WifiManager;
import android.os.UserHandle;
import android.view.Display;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.test.filters.SmallTest;

import com.android.wifi.resources.R;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Unit tests for {@link WifiDialogManager}.
 */
@SmallTest
public class WifiDialogManagerTest extends WifiBaseTest {
    private static final int TIMEOUT_MILLIS = 30_000;
    private static final String WIFI_DIALOG_APK_PKG_NAME = "WifiDialogApkPkgName";

    @Mock WifiContext mWifiContext;
    @Mock Resources mResources;
    @Mock WifiThreadRunner mWifiDialogManagerThreadRunner;
    WifiDialogManager mWifiDialogManager;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        when(mWifiContext.getWifiDialogApkPkgName()).thenReturn(WIFI_DIALOG_APK_PKG_NAME);
        when(mWifiContext.getResources()).thenReturn(mResources);
        mWifiDialogManager = new WifiDialogManager(mWifiContext, mWifiDialogManagerThreadRunner);
    }

    /**
     * Helper method to call launchP2pInvitationReceivedDialog synchronously.
     * @return the launched dialog ID.
     */
    private int launchP2pInvitationReceivedDialogSynchronous(
            String deviceName,
            boolean isPinRequested,
            @Nullable String displayPin,
            int displayId,
            @NonNull WifiDialogManager.P2pInvitationReceivedDialogCallback callback,
            @NonNull WifiThreadRunner callbackThreadRunner,
            int timeoutMs) {
        when(mResources.getInteger(R.integer.config_p2pInvitationReceivedDialogTimeoutMs))
                .thenReturn(timeoutMs);
        mWifiDialogManager.launchP2pInvitationReceivedDialog(
                deviceName, isPinRequested, displayPin, displayId, callback, callbackThreadRunner);

        // Synchronously run the posted launchP2pInvitationReceivedDialogInternal runnable.
        ArgumentCaptor<Runnable> runnableArgumentCaptor = ArgumentCaptor.forClass(Runnable.class);
        verify(mWifiDialogManagerThreadRunner, atLeastOnce())
                .post(runnableArgumentCaptor.capture());
        runnableArgumentCaptor.getValue().run();

        // Verify the launch Intent
        ArgumentCaptor<Intent> intentArgumentCaptor = ArgumentCaptor.forClass(Intent.class);
        verify(mWifiContext, atLeastOnce())
                .startActivityAsUser(intentArgumentCaptor.capture(), eq(UserHandle.CURRENT));
        Intent launchIntent = intentArgumentCaptor.getValue();
        assertThat(launchIntent.getAction()).isEqualTo(WifiManager.ACTION_LAUNCH_DIALOG);
        ComponentName component = launchIntent.getComponent();
        assertThat(component.getPackageName()).isEqualTo(WIFI_DIALOG_APK_PKG_NAME);
        assertThat(component.getClassName())
                .isEqualTo(WifiDialogManager.WIFI_DIALOG_ACTIVITY_CLASSNAME);
        assertThat(launchIntent.hasExtra(WifiManager.EXTRA_DIALOG_ID)).isTrue();
        int dialogId = launchIntent.getIntExtra(WifiManager.EXTRA_DIALOG_ID, -1);
        assertThat(dialogId).isNotEqualTo(-1);
        assertThat(launchIntent.hasExtra(WifiManager.EXTRA_DIALOG_TYPE)).isTrue();
        assertThat(launchIntent.getIntExtra(WifiManager.EXTRA_DIALOG_TYPE,
                WifiManager.DIALOG_TYPE_UNKNOWN))
                .isEqualTo(WifiManager.DIALOG_TYPE_P2P_INVITATION_RECEIVED);
        assertThat(launchIntent.hasExtra(WifiManager.EXTRA_P2P_DEVICE_NAME)).isTrue();
        assertThat(launchIntent.getStringExtra(WifiManager.EXTRA_P2P_DEVICE_NAME))
                .isEqualTo(deviceName);
        assertThat(launchIntent.hasExtra(WifiManager.EXTRA_P2P_PIN_REQUESTED)).isTrue();
        assertThat(launchIntent.getBooleanExtra(WifiManager.EXTRA_P2P_PIN_REQUESTED, false))
                .isEqualTo(isPinRequested);
        assertThat(launchIntent.hasExtra(WifiManager.EXTRA_P2P_DISPLAY_PIN)).isTrue();
        assertThat(launchIntent.getStringExtra(WifiManager.EXTRA_P2P_DISPLAY_PIN))
                .isEqualTo(displayPin);
        assertThat(launchIntent.hasExtra(WifiManager.EXTRA_P2P_DISPLAY_ID)).isTrue();
        // the -1000 should always be an incorrect value - i.e. don't default (not really
        // necessary since validation that extra exists - but a backup).
        assertThat(launchIntent.getIntExtra(WifiManager.EXTRA_P2P_DISPLAY_ID, -1000))
                .isEqualTo(displayId);
        return dialogId;
    }

    /**
     * Helper method to call replyToP2pInvitationReceivedDialog synchronously.
     */
    public void replyToP2pInvitationReceivedDialogSynchronous(
            int dialogId,
            boolean accepted,
            @Nullable String optionalPin) {
        mWifiDialogManager.replyToP2pInvitationReceivedDialog(dialogId, accepted, optionalPin);

        // Synchronously run the posted replyToP2pInvitationReceivedDialogInternal runnable.
        ArgumentCaptor<Runnable> runnableArgumentCaptor = ArgumentCaptor.forClass(Runnable.class);
        verify(mWifiDialogManagerThreadRunner, atLeastOnce())
                .post(runnableArgumentCaptor.capture());
        runnableArgumentCaptor.getValue().run();
    }

    private void dispatchMockWifiThreadRunner(WifiThreadRunner wifiThreadRunner) {
        ArgumentCaptor<Runnable> runnableArgumentCaptor = ArgumentCaptor.forClass(Runnable.class);
        verify(wifiThreadRunner, atLeastOnce()).post(runnableArgumentCaptor.capture());
        runnableArgumentCaptor.getValue().run();
    }

    /**
     * Verifies that launching a P2P Invitation Received dialog with a callback will result in the
     * correct callback methods invoked when a response is received.
     */
    @Test
    public void testP2pInvitationReceivedDialog_launchAndResponse_notifiesCallback() {
        WifiDialogManager.P2pInvitationReceivedDialogCallback callback =
                mock(WifiDialogManager.P2pInvitationReceivedDialogCallback.class);
        WifiThreadRunner callbackThreadRunner = mock(WifiThreadRunner.class);
        int dialogId;

        // Accept without PIN
        dialogId = launchP2pInvitationReceivedDialogSynchronous(
                "deviceName", false, null, Display.DEFAULT_DISPLAY, callback,
                callbackThreadRunner, 0);
        replyToP2pInvitationReceivedDialogSynchronous(dialogId, true, null);
        dispatchMockWifiThreadRunner(callbackThreadRunner);
        verify(callback, times(1)).onAccepted(null);

        // Callback should be removed from callback list, so a second notification should be ignored
        replyToP2pInvitationReceivedDialogSynchronous(dialogId, true, "012345");
        verify(callback, times(0)).onAccepted("012345");

        // Accept with PIN
        dialogId = launchP2pInvitationReceivedDialogSynchronous(
                "deviceName", true, null, Display.DEFAULT_DISPLAY, callback, callbackThreadRunner,
                0);
        replyToP2pInvitationReceivedDialogSynchronous(dialogId, true, "012345");
        dispatchMockWifiThreadRunner(callbackThreadRunner);
        verify(callback, times(1)).onAccepted("012345");

        // Accept with PIN but PIN was not requested
        dialogId = launchP2pInvitationReceivedDialogSynchronous(
                "deviceName", false, null, 123, callback, callbackThreadRunner,
                0);
        replyToP2pInvitationReceivedDialogSynchronous(dialogId, true, "012345");
        dispatchMockWifiThreadRunner(callbackThreadRunner);
        verify(callback, times(2)).onAccepted("012345");

        // Accept without PIN but PIN was requested
        dialogId = launchP2pInvitationReceivedDialogSynchronous(
                "deviceName", true, null, Display.DEFAULT_DISPLAY, callback, callbackThreadRunner,
                0);
        replyToP2pInvitationReceivedDialogSynchronous(dialogId, true, null);
        dispatchMockWifiThreadRunner(callbackThreadRunner);
        verify(callback, times(2)).onAccepted(null);

        // Decline without PIN
        dialogId = launchP2pInvitationReceivedDialogSynchronous(
                "deviceName", false, null, Display.DEFAULT_DISPLAY, callback, callbackThreadRunner,
                0);
        replyToP2pInvitationReceivedDialogSynchronous(dialogId, false, null);
        dispatchMockWifiThreadRunner(callbackThreadRunner);
        verify(callback, times(1)).onDeclined();

        // Decline with PIN
        dialogId = launchP2pInvitationReceivedDialogSynchronous(
                "deviceName", true, null, Display.DEFAULT_DISPLAY, callback, callbackThreadRunner,
                0);
        replyToP2pInvitationReceivedDialogSynchronous(dialogId, false, "012345");
        dispatchMockWifiThreadRunner(callbackThreadRunner);
        verify(callback, times(2)).onDeclined();

        // Decline with PIN but PIN was not requested
        dialogId = launchP2pInvitationReceivedDialogSynchronous(
                "deviceName", false, null, Display.DEFAULT_DISPLAY, callback, callbackThreadRunner,
                0);
        replyToP2pInvitationReceivedDialogSynchronous(dialogId, false, "012345");
        dispatchMockWifiThreadRunner(callbackThreadRunner);
        verify(callback, times(3)).onDeclined();

        // Decline without PIN but PIN was requested
        dialogId = launchP2pInvitationReceivedDialogSynchronous(
                "deviceName", true, null, Display.DEFAULT_DISPLAY, callback, callbackThreadRunner,
                0);
        replyToP2pInvitationReceivedDialogSynchronous(dialogId, false, null);
        dispatchMockWifiThreadRunner(callbackThreadRunner);
        verify(callback, times(4)).onDeclined();
    }

    /**
     * Verifies the right callback is notified for a response to a P2P Invitation Received dialog.
     */
    @Test
    public void testP2pInvitationReceivedDialog_multipleDialogs_responseMatchedToCorrectCallback() {
        // Launch Dialog1
        WifiDialogManager.P2pInvitationReceivedDialogCallback callback1 = mock(
                WifiDialogManager.P2pInvitationReceivedDialogCallback.class);
        WifiThreadRunner callbackThreadRunner = mock(WifiThreadRunner.class);
        int dialogId1 = launchP2pInvitationReceivedDialogSynchronous(
                "deviceName", false, null, Display.DEFAULT_DISPLAY, callback1, callbackThreadRunner,
                0);

        // Launch Dialog2
        WifiDialogManager.P2pInvitationReceivedDialogCallback callback2 = mock(
                WifiDialogManager.P2pInvitationReceivedDialogCallback.class);
        int dialogId2 = launchP2pInvitationReceivedDialogSynchronous(
                "deviceName", false, null, Display.DEFAULT_DISPLAY, callback2, callbackThreadRunner,
                0);

        // callback1 notified
        replyToP2pInvitationReceivedDialogSynchronous(dialogId1, true, null);
        dispatchMockWifiThreadRunner(callbackThreadRunner);
        verify(callback1, times(1)).onAccepted(null);
        verify(callback2, times(0)).onAccepted(null);

        // callback2 notified
        replyToP2pInvitationReceivedDialogSynchronous(dialogId2, true, null);
        dispatchMockWifiThreadRunner(callbackThreadRunner);
        verify(callback1, times(1)).onAccepted(null);
        verify(callback2, times(1)).onAccepted(null);
    }

    /**
     * Verifies that a P2P Invitation Received dialog is cancelled after the specified timeout
     */
    @Test
    public void testP2pInvitationReceivedDialog_timeout_cancelsDialog() {
        // Launch Dialog without timeout.
        WifiDialogManager.P2pInvitationReceivedDialogCallback callback = mock(
                WifiDialogManager.P2pInvitationReceivedDialogCallback.class);
        WifiThreadRunner callbackThreadRunner = mock(WifiThreadRunner.class);
        launchP2pInvitationReceivedDialogSynchronous(
                "deviceName", false, null, Display.DEFAULT_DISPLAY, callback, callbackThreadRunner,
                0);

        // Verify cancel runnable wasn't posted.
        verify(mWifiDialogManagerThreadRunner, never()).postDelayed(any(Runnable.class), anyInt());

        // Launch Dialog with timeout
        callback = mock(WifiDialogManager.P2pInvitationReceivedDialogCallback.class);
        int dialogId = launchP2pInvitationReceivedDialogSynchronous(
                "deviceName", false, null, Display.DEFAULT_DISPLAY, callback, callbackThreadRunner,
                TIMEOUT_MILLIS);

        // Verify the timeout runnable was posted and run it.
        ArgumentCaptor<Runnable> runnableArgumentCaptor = ArgumentCaptor.forClass(Runnable.class);
        verify(mWifiDialogManagerThreadRunner, times(1))
                .postDelayed(runnableArgumentCaptor.capture(), eq((long) TIMEOUT_MILLIS));
        runnableArgumentCaptor.getValue().run();

        // Verify that a cancel Intent was sent.
        ArgumentCaptor<Intent> intentArgumentCaptor = ArgumentCaptor.forClass(Intent.class);
        verify(mWifiContext, atLeastOnce())
                .startActivityAsUser(intentArgumentCaptor.capture(), eq(UserHandle.CURRENT));
        Intent cancelIntent = intentArgumentCaptor.getValue();
        assertThat(cancelIntent.getAction()).isEqualTo(WifiManager.ACTION_CANCEL_DIALOG);
        ComponentName component = cancelIntent.getComponent();
        assertThat(component.getPackageName()).isEqualTo(WIFI_DIALOG_APK_PKG_NAME);
        assertThat(component.getClassName())
                .isEqualTo(WifiDialogManager.WIFI_DIALOG_ACTIVITY_CLASSNAME);
        assertThat(cancelIntent.hasExtra(WifiManager.EXTRA_DIALOG_ID)).isTrue();
        assertThat(cancelIntent.getIntExtra(WifiManager.EXTRA_DIALOG_ID, -1)).isEqualTo(dialogId);

        // Launch Dialog without timeout
        callback = mock(WifiDialogManager.P2pInvitationReceivedDialogCallback.class);
        dialogId = launchP2pInvitationReceivedDialogSynchronous(
                "deviceName", false, null, Display.DEFAULT_DISPLAY, callback, callbackThreadRunner,
                TIMEOUT_MILLIS);

        // Reply before the timeout is over
        replyToP2pInvitationReceivedDialogSynchronous(dialogId, true, null);
        dispatchMockWifiThreadRunner(callbackThreadRunner);

        // Verify callback was replied to, and the cancel runnable was posted but then removed.
        verify(callback).onAccepted(null);
        verify(callback, never()).onDeclined();
        verify(mWifiDialogManagerThreadRunner, times(2))
                .postDelayed(runnableArgumentCaptor.capture(), eq((long) TIMEOUT_MILLIS));
        verify(mWifiDialogManagerThreadRunner).removeCallbacks(runnableArgumentCaptor.getValue());
    }
}
