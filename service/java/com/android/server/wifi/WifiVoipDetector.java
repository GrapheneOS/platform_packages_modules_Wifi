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

package com.android.server.wifi;

import static android.media.AudioManager.MODE_COMMUNICATION_REDIRECT;
import static android.media.AudioManager.MODE_IN_COMMUNICATION;

import android.annotation.NonNull;
import android.content.Context;
import android.media.AudioManager;
import android.os.Build;
import android.os.Handler;
import android.telephony.CallAttributes;
import android.telephony.TelephonyCallback;
import android.telephony.TelephonyManager;
import android.util.LocalLog;
import android.util.Log;

import androidx.annotation.RequiresApi;

import com.android.internal.annotations.VisibleForTesting;
import com.android.modules.utils.HandlerExecutor;
import com.android.server.wifi.hal.WifiChip;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Map;

/**
 * Class used to detect Wi-Fi VoIP call status
 */
@RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
public class WifiVoipDetector {
    private static final String TAG = "WifiVoipDetector";

    private final Context mContext;
    private final Handler mHandler;
    private final HandlerExecutor mHandlerExecutor;
    private final WifiInjector mWifiInjector;
    private final LocalLog mLocalLog;

    private final WifiCarrierInfoManager mWifiCarrierInfoManager;

    private AudioManager mAudioManager;
    private TelephonyManager mTelephonyManager;

    private WifiCallingStateListener mWifiCallingStateListener;
    private AudioModeListener mAudioModeListener;

    private int mCurrentMode = WifiChip.WIFI_VOIP_MODE_OFF;
    private boolean mIsMonitoring = false;
    private boolean mIsWifiConnected = false;
    private boolean mIsOTTCallOn = false;
    private boolean mIsVoWifiOn = false;
    private boolean mVerboseLoggingEnabled = false;

    private Map<String, Boolean> mConnectedWifiIfaceMap = new HashMap<>();

    public WifiVoipDetector(@NonNull Context context, @NonNull Handler handler,
            @NonNull WifiInjector wifiInjector,
            @NonNull WifiCarrierInfoManager wifiCarrierInfoManager) {
        mContext = context;
        mHandler = handler;
        mHandlerExecutor = new HandlerExecutor(mHandler);
        mWifiInjector = wifiInjector;
        mWifiCarrierInfoManager = wifiCarrierInfoManager;
        mLocalLog = new LocalLog(32);
    }

    /**
     * Enable verbose logging for WifiConnectivityManager.
     */
    public void enableVerboseLogging(boolean verbose) {
        mVerboseLoggingEnabled = verbose;
    }

    @VisibleForTesting
    public class WifiCallingStateListener extends TelephonyCallback
            implements TelephonyCallback.CallAttributesListener {

        @Override
        public void onCallAttributesChanged(@NonNull CallAttributes callAttributes) {
            boolean isVoWifion = callAttributes.getNetworkType()
                    == TelephonyManager.NETWORK_TYPE_IWLAN;
            if (isVoWifion == mIsVoWifiOn) {
                return;
            }
            mIsVoWifiOn = isVoWifion;
            String log = (mIsVoWifiOn ? "Enter" : "Leave") + " IWLAN Call";
            mLocalLog.log(log);
            if (mVerboseLoggingEnabled) {
                Log.d(TAG, log);
            }
            executeWifiVoIPOptimization();
        }
    }

    @VisibleForTesting
    public class AudioModeListener implements AudioManager.OnModeChangedListener {
        @Override
        public void onModeChanged(int audioMode) {
            boolean isOTTCallOn = audioMode == MODE_IN_COMMUNICATION
                    || audioMode == MODE_COMMUNICATION_REDIRECT;
            if (isOTTCallOn == mIsOTTCallOn) {
                return;
            }
            mIsOTTCallOn = isOTTCallOn;
            String log = "Audio mode (" + (mIsOTTCallOn ? "Enter" : "Leave")
                    + " OTT) onModeChanged to " + audioMode;
            mLocalLog.log(log);
            if (mVerboseLoggingEnabled) {
                Log.d(TAG, log);
            }
            executeWifiVoIPOptimization();
        }
    }

    /**
     * Notify wifi is connected or not to start monitoring the VoIP status.
     *
     * @param isConnected whether or not wif is connected.
     * @param isPrimary the connected client mode is primary or not
     * @param ifaceName the interface name of connected client momde
     */
    public void notifyWifiConnected(boolean isConnected, boolean isPrimary, String ifaceName) {
        if (isConnected) {
            mConnectedWifiIfaceMap.put(ifaceName, isPrimary);
            if (isPrimary) {
                mIsWifiConnected = true;
            }
        } else {
            Boolean isPrimaryBefore = mConnectedWifiIfaceMap.remove(ifaceName);
            if (mConnectedWifiIfaceMap.size() > 0) {
                if (isPrimaryBefore != null && isPrimaryBefore.booleanValue()) {
                    if (isPrimary) {
                        // Primary client mode is disconnected.
                        mIsWifiConnected = false;
                    } else {
                        // Previous primary was changed to secondary && there is another client mode
                        // which will be primary mode. (MBB use case).
                        return;
                    }
                }
            } else {
                // No any client mode is connected.
                mIsWifiConnected = false;
            }
        }
        if (mIsWifiConnected) {
            startMonitoring();
        } else {
            stopMonitoring();
        }
    }

    public boolean isWifiVoipOn() {
        return (mIsWifiConnected && mIsOTTCallOn) || mIsVoWifiOn;
    }

    private void executeWifiVoIPOptimization() {
        final boolean wifiVipOn = isWifiVoipOn();
        int newMode = wifiVipOn ? WifiChip.WIFI_VOIP_MODE_VOICE : WifiChip.WIFI_VOIP_MODE_OFF;
        if (mCurrentMode != newMode) {
            String log = "Update voip over wifi to new mode: " + newMode;
            if (!mWifiInjector.getWifiNative().setVoipMode(newMode)) {
                log = "Failed to set Voip Mode to " + newMode + " (maybe not supported?)";
            } else {
                mCurrentMode = newMode;
                mWifiInjector.getWifiMetrics().setVoipMode(mCurrentMode);
            }
            mLocalLog.log(log);
            if (mVerboseLoggingEnabled) {
                Log.d(TAG, log);
            }
        }
    }

    private void startMonitoring() {
        if (mIsMonitoring) {
            return;
        }
        mIsMonitoring = true;
        if (mAudioManager == null) {
            mAudioManager = mContext.getSystemService(AudioManager.class);
        }
        if (mTelephonyManager == null) {
            mTelephonyManager = mWifiInjector.makeTelephonyManager();
        }
        if (mWifiCallingStateListener == null) {
            mWifiCallingStateListener = new WifiCallingStateListener();
        }
        if (mAudioModeListener == null) {
            mAudioModeListener = new AudioModeListener();
        }
        if (mTelephonyManager != null) {
            mIsVoWifiOn = mWifiCarrierInfoManager.isWifiCallingAvailable();
            mTelephonyManager.registerTelephonyCallback(
                     mHandlerExecutor, mWifiCallingStateListener);
        }
        if (mAudioManager != null) {
            int audioMode = mAudioManager.getMode();
            mIsOTTCallOn = audioMode == MODE_IN_COMMUNICATION
                    || audioMode == MODE_COMMUNICATION_REDIRECT;
            mAudioManager.addOnModeChangedListener(mHandlerExecutor, mAudioModeListener);
        }
        executeWifiVoIPOptimization();
    }

    private void stopMonitoring() {
        if (!mIsMonitoring) {
            return;
        }
        mIsMonitoring = false;
        if (mAudioModeListener != null) {
            mAudioManager.removeOnModeChangedListener(mAudioModeListener);
        }
        if (mWifiCallingStateListener != null) {
            mTelephonyManager.unregisterTelephonyCallback(mWifiCallingStateListener);
            mWifiCallingStateListener = null;
        }
        mIsOTTCallOn = false;
        mIsVoWifiOn = false;
        mIsWifiConnected = false;
        executeWifiVoIPOptimization();
    }

    /**
     * Dump output for debugging.
     */
    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println("Dump of WifiVoipDetector:");
        mLocalLog.dump(fd, pw, args);
        pw.println("mIsMonitoring = " + mIsMonitoring);
        pw.println("mIsOTTCallOn = " + mIsOTTCallOn);
        pw.println("mIsVoWifiOn = " + mIsVoWifiOn);
        pw.println("mIsWifiConnected = " + mIsWifiConnected);
        pw.println("mCurrentMode = " + mCurrentMode);
    }
}
