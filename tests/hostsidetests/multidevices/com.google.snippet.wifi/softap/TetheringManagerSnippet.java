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

package com.google.snippet.wifi.softap;

import static java.util.concurrent.TimeUnit.SECONDS;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.TetheringManager;
import android.net.TetheringManager.TetheringRequest;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.RemoteException;
import android.util.Log;

import androidx.test.platform.app.InstrumentationRegistry;

import com.google.android.mobly.snippet.Snippet;
import com.google.android.mobly.snippet.event.EventCache;
import com.google.android.mobly.snippet.event.SnippetEvent;
import com.google.android.mobly.snippet.rpc.AsyncRpc;
import com.google.android.mobly.snippet.rpc.Rpc;
import com.google.snippet.wifi.WifiShellPermissionSnippet;

import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Snippet class for TetheringManager.
 */
public class TetheringManagerSnippet extends WifiShellPermissionSnippet implements Snippet {
    private static final String TAG = "TetheringManagerSnippet";
    private final Context mContext;
    private final EventCache mEventCache = EventCache.getInstance();
    private final TetheringManager mTetheringManager;
    private final Handler mHandler;
    private TetherStateChangedReceiver mTetherChangedReceiver;


    /**
     * Callback class to get the results of tethering start.
     */
    private static class SnippetStartTetheringCallback
            implements TetheringManager.StartTetheringCallback {
        private final String mCallbackId;

        SnippetStartTetheringCallback(String callbackId) {
            mCallbackId = callbackId;
        }

        @Override
        public void onTetheringStarted() {
            Log.d(TAG, "onTetheringStarted");
            SnippetEvent event = new SnippetEvent(mCallbackId, "onTetheringStarted");
            EventCache.getInstance().postEvent(event);
        }

        @Override
        public void onTetheringFailed(final int error) {
            Log.d(TAG, "onTetheringFailed, error=" + error);
            SnippetEvent event = new SnippetEvent(mCallbackId, "onTetheringFailed");
            event.getData().putInt("error", error);
            EventCache.getInstance().postEvent(event);
        }
    }

    public TetheringManagerSnippet() {
        mContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        mTetheringManager = mContext.getSystemService(TetheringManager.class);
        HandlerThread handlerThread = new HandlerThread(getClass().getSimpleName());
        handlerThread.start();
        mHandler = new Handler(handlerThread.getLooper());
    }

    /**
     * Starts tethering.
     *
     * @param callbackId A unique identifier assigned automatically by Mobly.
     */
    @AsyncRpc(description = "Call to start tethering.")
    public void tetheringStartTethering(String callbackId) {
        TetheringRequest request =
                new TetheringRequest.Builder(TetheringManager.TETHERING_WIFI).build();

        SnippetStartTetheringCallback callback = new SnippetStartTetheringCallback(callbackId);
        mTetheringManager.startTethering(request, mHandler::post, callback);
    }

    /**
     * Stop tethering.
     */
    @Rpc(description = "Call to stop tethering.")
    public void tetheringStopTethering() throws RemoteException {

        adoptShellPermission();
        mTetheringManager.stopTethering(TetheringManager.TETHERING_WIFI);
    }

    /*
     * Start wifi tethering.
     */
    @Rpc(description = "Call to start tethering with a provisioning check if needed")
    public void tetheringStartTetheringWithProvisioning(Integer type, Boolean showProvisioningUi)
            throws RemoteException {
        Log.d(TAG, "startTethering for type: " + type + " showProvUi: " + showProvisioningUi);
        final CountDownLatch latch = new CountDownLatch(1);
        TetherStartCallback tetherCallback = new TetherStartCallback(latch);
        Executor executor = Executors.newSingleThreadScheduledExecutor();

        TetheringRequest request =
                new TetheringRequest.Builder(type).setShouldShowEntitlementUi(
                        showProvisioningUi).build();
        try {
            adoptShellPermission();
            mTetheringManager.startTethering(request, executor, tetherCallback);
            latch.await(10, SECONDS);
        } catch (InterruptedException | RemoteException e) {
            Log.e(TAG, "tetheringStartTetheringWithProvisioning fails: " + e);
        } finally {
            dropShellPermission();
        }
    }

    /**
     * Callback class to get the results of tethering start.
     */
    private static class TetherStartCallback implements TetheringManager.StartTetheringCallback {
        private final CountDownLatch mLatch;

        TetherStartCallback(CountDownLatch latch) {
            this.mLatch = latch;
        }

        @Override
        public void onTetheringStarted() {
            Log.d(TAG, "TetherStartCallback onTetheringStarted callback is triggered.");
            mLatch.countDown();
        }

        @Override
        public void onTetheringFailed(final int error) {
            Log.d(
                    TAG,
                    "TetherStartCallback onTetheringFailed callback is triggered: error " + error);
        }
    }

    static class TetherStateChangedReceiver extends BroadcastReceiver {
        private final String mCallbackId;

        TetherStateChangedReceiver(String callbackId) {
            this.mCallbackId = callbackId;
        }
        @Override
        public void onReceive(Context c, Intent intent) {
            Log.v(TAG, "TetherStateChangedReceiver onReceive");
            String action = intent.getAction();
            if (WifiManager.WIFI_AP_STATE_CHANGED_ACTION.equals(action)) {
                SnippetEvent event = new SnippetEvent(mCallbackId, "TetherStateChangedReceiver");
                event.getData().putString("callbackName", "WifiManagerApStateChanged");
                Log.d(TAG, "Wifi AP state changed.");
                int state = intent.getIntExtra(WifiManager.EXTRA_WIFI_AP_STATE,
                        WifiManager.WIFI_AP_STATE_FAILED);
                if (state == WifiManager.WIFI_AP_STATE_ENABLED) {
                    event.getData().putString("status", "WifiManagerApEnabled");
                } else if (state == WifiManager.WIFI_AP_STATE_DISABLED) {
                    event.getData().putString("status", "WifiManagerApDisabled");
                }
                EventCache.getInstance().postEvent(event);
            } else if (TetheringManager.ACTION_TETHER_STATE_CHANGED.equals(action)) {
                Log.d(TAG, "Tether state changed.");
                ArrayList<String> available = intent.getStringArrayListExtra(
                        TetheringManager.EXTRA_AVAILABLE_TETHER);
                ArrayList<String> active = intent.getStringArrayListExtra(
                        TetheringManager.EXTRA_ACTIVE_TETHER);
                ArrayList<String> errored = intent.getStringArrayListExtra(
                        TetheringManager.EXTRA_ERRORED_TETHER);
                SnippetEvent event = new SnippetEvent(mCallbackId, "TetherStateChangedReceiver");
                event.getData().putString("callbackName", "TetherStateChanged");
                event.getData().putStringArrayList("AVAILABLE_TETHER", available);
                event.getData().putStringArrayList("ACTIVE_TETHER", active);
                event.getData().putStringArrayList("ERRORED_TETHER", errored);
                EventCache.getInstance().postEvent(event);
            }
        }
    }

    @AsyncRpc(description = "Start listening for tether state change related broadcasts.")
    public void tetheringStartTrackingTetherStateChange(String callbackId) {
        mTetherChangedReceiver = new TetherStateChangedReceiver(callbackId);
        IntentFilter mTetherFilter = new IntentFilter(WifiManager.WIFI_AP_STATE_CHANGED_ACTION);
        mTetherFilter.addAction(TetheringManager.ACTION_TETHER_STATE_CHANGED);
        mContext.registerReceiver(mTetherChangedReceiver, mTetherFilter);
    }

    @Rpc(description = "Stop listening for wifi state change related broadcasts.")
    public void tetheringStopTrackingTetherStateChange() {
        if (mTetherChangedReceiver != null) {
            mContext.unregisterReceiver(mTetherChangedReceiver);
            mTetherChangedReceiver = null;
        }
    }
}
