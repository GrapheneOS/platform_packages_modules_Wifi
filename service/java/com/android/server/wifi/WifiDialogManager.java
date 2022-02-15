/*
 * Copyright (C) 2022 The Android Open Source Project
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

import android.content.Intent;
import android.net.wifi.WifiContext;
import android.net.wifi.WifiManager;
import android.os.UserHandle;
import android.util.ArraySet;
import android.util.Log;
import android.util.SparseArray;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.android.wifi.resources.R;

import java.util.Set;

/**
 * Class to manage launching dialogs via WifiDialog and returning the user reply.
 * This class is thread-safe via the use of a single WifiThreadRunner.
 */
public class WifiDialogManager {
    private static final String TAG = "WifiDialogManager";
    @VisibleForTesting static final String WIFI_DIALOG_ACTIVITY_CLASSNAME =
            "com.android.wifi.dialog.WifiDialogActivity";

    private boolean mVerboseLoggingEnabled;

    private int mNextDialogId = 0;
    private final Set<Integer> mCurrentDialogIds = new ArraySet<>();
    private final @NonNull SparseArray<P2pInvitationReceivedDialogHandle>
            mP2pInvitationReceivedDialogsHandles = new SparseArray<>();

    private final @NonNull WifiContext mContext;
    private final @NonNull WifiThreadRunner mWifiThreadRunner;

    public WifiDialogManager(
            @NonNull WifiContext context,
            @NonNull WifiThreadRunner wifiThreadRunner) {
        mContext = context;
        mWifiThreadRunner = wifiThreadRunner;
    }

    /**
     * Enables verbose logging.
     */
    public void enableVerboseLogging(boolean enabled) {
        mVerboseLoggingEnabled = enabled;
    }

    private int getNextDialogId() {
        if (mCurrentDialogIds.isEmpty() || mNextDialogId < 0) {
            mNextDialogId = 0;
        }
        return mNextDialogId++;
    }

    /**
     * Sends an Intent to cancel a launched dialog with the given ID.
     */
    private void sendCancelIntentForDialogId(int dialogId) {
        if (!mCurrentDialogIds.contains(dialogId)) {
            Log.e(TAG, "Tried to cancel dialog but could not find id " + dialogId);
            return;
        }
        String wifiDialogApkPkgName = mContext.getWifiDialogApkPkgName();
        if (wifiDialogApkPkgName == null) {
            Log.e(TAG, "Tried to cancel dialog but could not find a WifiDialog apk package name!");
            return;
        }
        Intent intent = new Intent(WifiManager.ACTION_CANCEL_DIALOG);
        intent.putExtra(WifiManager.EXTRA_DIALOG_ID, dialogId);
        intent.setClassName(mContext.getWifiDialogApkPkgName(), WIFI_DIALOG_ACTIVITY_CLASSNAME);
        mContext.startActivityAsUser(intent, UserHandle.CURRENT);
    }

    /**
     * Callback for receiving P2P Invitation Received dialog responses.
     */
    public interface P2pInvitationReceivedDialogCallback {
        /**
         * Invitation was accepted.
         * @param optionalPin Optional PIN if a PIN was requested, or {@code null} otherwise.
         */
        void onAccepted(@Nullable String optionalPin);

        /**
         * Invitation was declined or cancelled.
         */
        void onDeclined();
    }

    /**
     * Handles launching and callback response for a single P2P Invitation Received dialog
     */
    private class P2pInvitationReceivedDialogHandle {
        private int mDialogId;
        private Intent mIntent;
        private @NonNull P2pInvitationReceivedDialogCallback mCallback;
        private @NonNull WifiThreadRunner mCallbackThreadRunner;
        private @Nullable Runnable mTimeoutRunnable;

        P2pInvitationReceivedDialogHandle(
                final int dialogId,
                final @NonNull String deviceName,
                final boolean isPinRequested,
                @Nullable String displayPin,
                int displayId,
                @NonNull P2pInvitationReceivedDialogCallback callback,
                @NonNull WifiThreadRunner callbackThreadRunner) {
            mDialogId = dialogId;
            Intent intent = new Intent(WifiManager.ACTION_LAUNCH_DIALOG);
            intent.putExtra(WifiManager.EXTRA_DIALOG_TYPE,
                    WifiManager.DIALOG_TYPE_P2P_INVITATION_RECEIVED);
            intent.putExtra(WifiManager.EXTRA_DIALOG_ID, dialogId);
            intent.putExtra(WifiManager.EXTRA_P2P_DEVICE_NAME, deviceName);
            intent.putExtra(WifiManager.EXTRA_P2P_PIN_REQUESTED, isPinRequested);
            intent.putExtra(WifiManager.EXTRA_P2P_DISPLAY_PIN, displayPin);
            intent.putExtra(WifiManager.EXTRA_P2P_DISPLAY_ID, displayId);
            intent.setClassName(mContext.getWifiDialogApkPkgName(), WIFI_DIALOG_ACTIVITY_CLASSNAME);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            mIntent = intent;
            mCallback = callback;
            mCallbackThreadRunner = callbackThreadRunner;
        }

        void launchDialog() {
            mContext.startActivityAsUser(mIntent, UserHandle.CURRENT);
            int timeoutMs = mContext.getResources()
                    .getInteger(R.integer.config_p2pInvitationReceivedDialogTimeoutMs);
            if (timeoutMs > 0) {
                mTimeoutRunnable = () -> sendCancelIntentForDialogId(mDialogId);
                mWifiThreadRunner.postDelayed(mTimeoutRunnable, timeoutMs);
            }
        }

        void onAccepted(@Nullable String optionalPin) {
            mCallbackThreadRunner.post(() -> mCallback.onAccepted(optionalPin));
            if (mTimeoutRunnable != null) {
                mWifiThreadRunner.removeCallbacks(mTimeoutRunnable);
                mTimeoutRunnable = null;
            }
        }

        void onDeclined() {
            mCallbackThreadRunner.post(() -> mCallback.onDeclined());
            if (mTimeoutRunnable != null) {
                mWifiThreadRunner.removeCallbacks(mTimeoutRunnable);
                mTimeoutRunnable = null;
            }
        }
    }

    /**
     * Launches a P2P Invitation Received dialog.
     * @param deviceName Name of the device sending the invitation.
     * @param isPinRequested True if a PIN was requested and a PIN input UI should be shown.
     * @param displayPin Display PIN, or {@code null} if no PIN should be displayed
     * @param displayId The ID of the Display on which to place the dialog (Display.DEFAULT_DISPLAY
     *                  refers to the default display)
     * @param callback Callback to receive the dialog response.
     * @param threadRunner WifiThreadRunner to run the callback on.
     */
    @AnyThread
    public void launchP2pInvitationReceivedDialog(
            String deviceName,
            boolean isPinRequested,
            @Nullable String displayPin,
            int displayId,
            @NonNull P2pInvitationReceivedDialogCallback callback,
            @NonNull WifiThreadRunner threadRunner) {
        if (callback == null) {
            Log.e(TAG, "Cannot launch a P2P Invitation Received dialog with null callback!");
            return;
        }
        if (threadRunner == null) {
            Log.e(TAG, "Cannot launch a P2P Invitation Received dialog with null thread runner!");
            return;
        }
        mWifiThreadRunner.post(() ->
                launchP2pInvitationReceivedDialogInternal(
                        deviceName,
                        isPinRequested,
                        displayPin,
                        displayId,
                        callback,
                        threadRunner));
    }

    private void launchP2pInvitationReceivedDialogInternal(
            String deviceName,
            boolean isPinRequested,
            @Nullable String displayPin,
            int displayId,
            @NonNull P2pInvitationReceivedDialogCallback callback,
            @NonNull WifiThreadRunner threadRunner) {
        int dialogId = getNextDialogId();
        mCurrentDialogIds.add(dialogId);
        P2pInvitationReceivedDialogHandle dialogHandle = new P2pInvitationReceivedDialogHandle(
                dialogId,
                deviceName,
                isPinRequested,
                displayPin,
                displayId,
                callback,
                threadRunner);
        mP2pInvitationReceivedDialogsHandles.put(dialogId, dialogHandle);
        dialogHandle.launchDialog();
        if (mVerboseLoggingEnabled) {
            Log.v(TAG, "Launching P2P Invitation Received dialog."
                    + " id=" + dialogId
                    + " deviceName=" + deviceName
                    + " isPinRequested=" + isPinRequested
                    + " displayPin=" + displayPin
                    + " displayId=" + displayId
                    + " callback=" + callback);
        }
    }

    /**
     * Returns the reply to a P2P Invitation Received dialog to the callback of matching dialogId.
     * @param dialogId id of the replying dialog.
     * @param accepted Whether the invitation was accepted.
     * @param optionalPin PIN of the reply, or {@code null} if none was supplied.
     * @hide
     */
    @AnyThread
    public void replyToP2pInvitationReceivedDialog(
            int dialogId,
            boolean accepted,
            @Nullable String optionalPin) {
        mWifiThreadRunner.post(() -> replyToP2pInvitationReceivedDialogInternal(
                dialogId,
                accepted,
                optionalPin));
    }

    private void replyToP2pInvitationReceivedDialogInternal(
            int dialogId,
            boolean accepted,
            @Nullable String optionalPin) {
        if (mVerboseLoggingEnabled) {
            Log.i(TAG, "Response received for P2P Invitation Received dialog."
                    + " id=" + dialogId
                    + " accepted=" + accepted
                    + " pin=" + optionalPin);
        }
        mCurrentDialogIds.remove(dialogId);
        P2pInvitationReceivedDialogHandle dialogHandle =
                mP2pInvitationReceivedDialogsHandles.get(dialogId);
        mP2pInvitationReceivedDialogsHandles.remove(dialogId);
        if (dialogHandle == null) {
            if (mVerboseLoggingEnabled) {
                Log.w(TAG, "No matching dialog handle for P2P Invitation Received dialog"
                        + " id=" + dialogId);
            }
            return;
        }
        if (accepted) {
            dialogHandle.onAccepted(optionalPin);
        } else {
            dialogHandle.onDeclined();
        }
    }
}
