/*
 * Copyright (C) 2016 The Android Open Source Project
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

import android.annotation.NonNull;
import android.os.Handler;
import android.util.Log;
import android.util.SparseLongArray;

import androidx.annotation.VisibleForTesting;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;


/**
 * Manage the pre-evaluation request for networks.
 *
 * <p> When a client requests to pre-evaluate a network, we will pre-evaluate the network if the
 * request is received within 1 hour of the network being requested. This is to avoid pre-evaluating
 * networks that are no longer needed. This class is not thread-safe.
 */
public class NetworkPreEvaluationManager {
    private static final String TAG = "NetworkPreEvaluationManager";
    @VisibleForTesting
    static final long PRE_EVALUATION_TIMEOUT_MS = Duration.ofSeconds(15).toMillis();
    private boolean mVerboseLogEnabled = false;
    private Map<String, Long> mPreEvaluationEnabledMap = new HashMap<>();
    private final Clock mClock;
    private final WifiDataStall mWifiDataStall;
    private final Handler mHandler;
    private PreEvaluationResultCallback mCallBack;

    public static interface PreEvaluationResultCallback {
        void onPass(@NonNull String profileKey);
        void onFail(@NonNull String profileKey);
    }

    public NetworkPreEvaluationManager(Clock clock, WifiDataStall wifiDataStall, Handler handler) {
        mWifiDataStall = wifiDataStall;
        mClock = clock;
        mHandler = handler;
    }

    /**
     * Enable or disable the pre-evaluation for the given network.
     *
     * @param networkId The network ID.
     * @param enabled Whether the network should be pre-evaluated.
     */
    public void setPreEvaluationEnabled(String profileKey, boolean enabled) {
        vlogd("setPreEvaluation: profileKey=" + profileKey + ", enabled=" + enabled);
        if (enabled) {
            mPreEvaluationEnabledMap.put(profileKey, mClock.getElapsedSinceBootMillis());
        } else {
            mPreEvaluationEnabledMap.remove(profileKey);
        }
    }

    private boolean getPreEvaluationEnabled(String profileKey) {
        long requestTimeMs = mPreEvaluationEnabledMap.getOrDefault(profileKey, 0L);
        if (requestTimeMs <= 0) {
            return false;
        }
        if (mClock.getElapsedSinceBootMillis() - requestTimeMs > Duration.ofHours(1).toMillis()) {
            // If the request is older than 1 hour, there is no need to pre-evaluate it anymore.
            mPreEvaluationEnabledMap.remove(profileKey);
            vlogd("Deleting pre-evaluation request for profileKey=" + profileKey
                    + " because it is older than 1 hour");
            return false;
        }
        return true;
    }

    /**
     * Returns true if the network with the given profileKey should be pre-evaluated.
     *
     * @param profileKey The profile key of the network.
     * @param isUserSelected Whether the network is user selected.
     * @return true if the network with the given profileKey should be pre-evaluated.
     */
    public boolean isPreEvaluationNeeded(String profileKey, boolean isUserSelected) {
        return !isUserSelected && mWifiDataStall.isCellularDataAvailable()
                && getPreEvaluationEnabled(profileKey);
    }

    /**
     * Starts the pre-evaluation for the given network.
     *
     * @param profileKey The profile key of the network.
     * @param callback The callback to be called when the pre-evaluation is complete.
     */
    public void startPreEvaluation(String profileKey, PreEvaluationResultCallback callback) {
        if (profileKey == null || callback == null) {
            return;
        }
        mCallBack = callback;
        // Start a timer to call onFail if pre-evaluation is not complete within the specified time.
        mHandler.postDelayed(() -> stopPreEvaluation(profileKey, false),
                PRE_EVALUATION_TIMEOUT_MS);
    }

    /**
     * Stops the pre-evaluation for the given network and calls the callback.
     *
     * @param profileKey The profile key of the network.
     * @param isUsable Whether the network is usable.
     */
    public void stopPreEvaluation(String profileKey, boolean isUsable) {
        if (mCallBack == null || profileKey == null) {
            return;
        }
        if (isUsable) {
            mCallBack.onPass(profileKey);
        } else {
            mCallBack.onFail(profileKey);
        }
        mCallBack = null;
    }

    /**
     * Enables verbose logging.
     */
    public void enableVerboseLogging(boolean enabled) {
        mVerboseLogEnabled = enabled;
    }

    private void vlogd(String msg) {
        if (!mVerboseLogEnabled) {
            return;
        }
        Log.d(TAG, msg, null);
    }
}
