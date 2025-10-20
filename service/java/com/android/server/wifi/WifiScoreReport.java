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

import static android.net.wifi.WifiUsabilityStatsEntry.SCORER_TYPE_INVALID;
import static android.net.wifi.WifiUsabilityStatsEntry.SCORER_TYPE_ML;
import static android.net.wifi.WifiUsabilityStatsEntry.SCORER_TYPE_VELOCITY;

import static com.android.server.wifi.Clock.INVALID_TIMESTAMP_MS;
import static com.android.server.wifi.ml_connected_scorer.Constants.POLLING_INTERVAL_MS;

import android.annotation.Nullable;
import android.content.Context;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkScore;
import android.net.ip.IpClientManager;
import android.net.wifi.IWifiConnectedNetworkScorer;
import android.net.wifi.MloLink;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiConnectedSessionInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiScanner;
import android.net.wifi.WifiUsabilityStatsEntry;
import android.os.Build;
import android.os.IBinder;
import android.os.Process;
import android.os.RemoteException;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.RequiresApi;

import com.android.internal.annotations.VisibleForTesting;
import com.android.modules.utils.build.SdkLevel;
import com.android.server.wifi.ActiveModeManager.ClientRole;
import com.android.server.wifi.ml_connected_scorer.MlConnectedScorer;
import com.android.server.wifi.proto.WifiStatsLog;
import com.android.server.wifi.util.RssiUtil;
import com.android.server.wifi.util.StringUtil;
import com.android.wifi.flags.Flags;
import com.android.wifi.resources.R;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.Calendar;
import java.util.LinkedList;
import java.util.StringJoiner;

/**
 * Class used to calculate scores for connected wifi networks and report it to the associated
 * network agent.
 */
public class WifiScoreReport {
    private static final String TAG = "WifiScoreReport";

    private static final int DUMPSYS_ENTRY_COUNT_LIMIT = 3600; // 3 hours on 3 second poll

    private boolean mVerboseLoggingEnabled = false;
    private static final long FIRST_REASONABLE_WALL_CLOCK = 1490000000000L; // mid-December 2016

    private static final int WIFI_CONNECTED_NETWORK_SCORER_IDENTIFIER = 0;
    private static final int INVALID_SESSION_ID = -1;
    private static final long MIN_TIME_TO_WAIT_BEFORE_BLOCKLIST_BSSID_MILLIS = 29000;
    private static final int WIFI_SCORE_TO_TERMINATE_CONNECTION_BLOCKLIST_BSSID = -2;

    /**
     * Set lingering score to be artificially lower than all other scores so that it will force
     * ConnectivityService to prefer any other network over this one.
     */
    @VisibleForTesting
    static final int LINGERING_SCORE = 1;

    // Cache of the last score
    @VisibleForTesting
    int mLegacyIntScore = ConnectedScorer.WIFI_INITIAL_SCORE;
    // Cache of the last usability status
    private boolean mIsUsable = true;
    private int mExternalScorerPredictionStatusForEvaluation =
            WifiStatsLog.SCORER_PREDICTION_RESULT_REPORTED__WIFI_PREDICTED_USABILITY_STATE__WIFI_USABILITY_PREDICTED_NONE;
    private int mAospScorerPredictionStatusForEvaluation =
            WifiStatsLog.SCORER_PREDICTION_RESULT_REPORTED__WIFI_PREDICTED_USABILITY_STATE__WIFI_USABILITY_PREDICTED_NONE;
    private int mLastInternalScorerType = SCORER_TYPE_INVALID;

    /**
     * If true, indicates that the associated {@link ClientModeImpl} instance is lingering
     * as a part of make before break STA + STA use-case, and will always send
     * {@link #LINGERING_SCORE} to NetworkAgent.
     */
    private boolean mShouldReduceNetworkScore = false;

    /** The current role of the ClientModeManager which owns this instance of WifiScoreReport. */
    @Nullable
    private ClientRole mCurrentRole = null;

    /** The IpClientManager from ClientMode */
    @Nullable
    private IpClientManager mIpClientManager = null;


    private final ScoringParams mScoringParams;
    private final Clock mClock;
    private int mSessionNumber = 0; // not to be confused with sessionid, this just counts resets
    private final String mInterfaceName;
    private final WifiBlocklistMonitor mWifiBlocklistMonitor;
    private final WifiScoreCard mWifiScoreCard;
    private final Context mContext;
    private long mLastScoreBreachLowTimeMillis = INVALID_TIMESTAMP_MS;

    @VisibleForTesting
    VelocityBasedConnectedScorer mVelocityBasedConnectedScorer;
    private MlConnectedScorer mMlConnectedScorer;
    private final WifiSettingsStore mWifiSettingsStore;
    private int mSessionIdNoReset = INVALID_SESSION_ID;
    // Indicate whether current network is selected by the user
    private boolean mIsUserSelected = false;

    @Nullable
    private WifiNetworkAgent mNetworkAgent;
    private final WifiMetrics mWifiMetrics;
    private final ExtendedWifiInfo mWifiInfo;
    private final WifiNative mWifiNative;
    private final WifiThreadRunner mWifiThreadRunner;
    private final DeviceConfigFacade mDeviceConfigFacade;
    private final ExternalScoreUpdateObserverProxy mExternalScoreUpdateObserverProxy;
    private final WifiInfo mWifiInfoNoReset;
    private final WifiGlobals mWifiGlobals;
    private final ActiveModeWarden mActiveModeWarden;
    private final WifiConnectivityManager mWifiConnectivityManager;
    private final WifiConfigManager mWifiConfigManager;
    @VisibleForTesting
    long mLastLowScoreScanTimestampMs = INVALID_TIMESTAMP_MS;
    @VisibleForTesting
    long mLastNudCheckTimeMs = INVALID_TIMESTAMP_MS;
    private int mNudYes = 0;    // Counts when we voted for a NUD
    private int mNudCount = 0;  // Counts when we were told a NUD was sent
    private WifiConfiguration mCurrentWifiConfiguration;
    private final ConnectedScorerHelper mConnectedScorerHelper;

    /**
     * Callback from {@link ExternalScoreUpdateObserverProxy}
     *
     * Wifi Scorer calls these callbacks when it needs to send information to us.
     */
    private class ScoreUpdateObserverProxy implements WifiManager.ScoreUpdateObserver {
        @Override
        public void notifyScoreUpdate(int sessionId, int score) {
            if (mWifiConnectedNetworkScorerHolder == null
                    || sessionId == INVALID_SESSION_ID
                    || sessionId != getCurrentSessionId()) {
                Log.w(TAG, "Ignoring stale/invalid external score"
                        + " sessionId=" + sessionId
                        + " currentSessionId=" + getCurrentSessionId()
                        + " score=" + score);
                return;
            }
            if (mIsExternalScorerDryRun) {
                return;
            }
            long millis = mClock.getWallClockMillis();
            if (SdkLevel.isAtLeastS()) {
                mLegacyIntScore = score;
                // Only primary network can have external scorer.
                updateWifiMetrics(millis, SCORER_TYPE_INVALID, -1, -1, score);
                return;
            }
        }

        @Override
        public void triggerUpdateOfWifiUsabilityStats(int sessionId) {
            if (mWifiConnectedNetworkScorerHolder == null
                    || sessionId == INVALID_SESSION_ID
                    || sessionId != getCurrentSessionId()
                    || mInterfaceName == null) {
                Log.w(TAG, "Ignoring triggerUpdateOfWifiUsabilityStats"
                        + " sessionId=" + sessionId
                        + " currentSessionId=" + getCurrentSessionId()
                        + " interfaceName=" + mInterfaceName);
                return;
            }
            if (mIsExternalScorerDryRun) {
                return;
            }
            WifiLinkLayerStats stats = mWifiNative.getWifiLinkLayerStats(mInterfaceName);

            // update mWifiInfo
            // TODO(b/153075963): Better coordinate this class and ClientModeImpl to remove
            // redundant codes below and in ClientModeImpl#fetchRssiLinkSpeedAndFrequencyNative.
            WifiSignalPollResults pollResults = mWifiNative.signalPoll(mInterfaceName);
            if (pollResults != null) {
                int newRssi = RssiUtil.calculateAdjustedRssi(pollResults.getRssi());
                int newTxLinkSpeed = pollResults.getTxLinkSpeed();
                int newFrequency = pollResults.getFrequency();
                int newRxLinkSpeed = pollResults.getRxLinkSpeed();

                /* Set link specific signal poll results */
                for (MloLink link : mWifiInfo.getAffiliatedMloLinks()) {
                    int linkId = link.getLinkId();
                    link.setRssi(pollResults.getRssi(linkId));
                    link.setTxLinkSpeedMbps(pollResults.getTxLinkSpeed(linkId));
                    link.setRxLinkSpeedMbps(pollResults.getRxLinkSpeed(linkId));
                    link.setChannel(ScanResult.convertFrequencyMhzToChannelIfSupported(
                            pollResults.getFrequency(linkId)));
                    link.setBand(ScanResult.toBand(pollResults.getFrequency(linkId)));
                }

                if (newRssi > WifiInfo.INVALID_RSSI) {
                    mWifiInfo.setRssi(newRssi);
                }
                /*
                 * set Tx link speed only if it is valid
                 */
                if (newTxLinkSpeed > 0) {
                    mWifiInfo.setLinkSpeed(newTxLinkSpeed);
                    mWifiInfo.setTxLinkSpeedMbps(newTxLinkSpeed);
                }
                /*
                 * set Rx link speed only if it is valid
                 */
                if (newRxLinkSpeed > 0) {
                    mWifiInfo.setRxLinkSpeedMbps(newRxLinkSpeed);
                }
                if (newFrequency > 0) {
                    mWifiInfo.setFrequency(newFrequency);
                }
            }

            // TODO(b/153075963): This should not be plumbed through WifiMetrics
            com.android.server.wifi.proto.nano.WifiMetricsProto.WifiUsabilityStatsEntry statsEntry =
                    mWifiMetrics.buildStatsEntry(mInterfaceName, mWifiInfo, stats, false, 0);
            WifiUsabilityStatsEntry parcelableStatsEntry =
                    mWifiMetrics.createNewWifiUsabilityStatsEntryParcelable(statsEntry,
                            stats, mWifiInfo);
            mWifiMetrics.sendWifiUsabilityStats(statsEntry, parcelableStatsEntry);
            mWifiMetrics.addWifiUsabilityStatsEntries(statsEntry);
        }

        @Override
        public void notifyStatusUpdate(int sessionId, boolean isUsable) {
            if (mWifiConnectedNetworkScorerHolder == null
                    || sessionId == INVALID_SESSION_ID
                    || sessionId != getCurrentSessionId()) {
                Log.w(TAG, "Ignoring stale/invalid external status"
                        + " sessionId=" + sessionId
                        + " currentSessionId=" + getCurrentSessionId()
                        + " isUsable=" + isUsable);
                return;
            }
            mExternalScorerPredictionStatusForEvaluation =
                    convertToPredictionStatusForEvaluation(isUsable);
            if (mIsExternalScorerDryRun) {
                return;
            }
            if (mNetworkAgent == null) {
                return;
            }
            if (mShouldReduceNetworkScore) {
                return;
            }
            if (!mIsUsable && isUsable) {
                // Disable the network switch dialog temporarily if the status changed to usable.
                int durationMs = mContext.getResources().getInteger(
                        R.integer.config_wifiNetworkSwitchDialogDisabledMsWhenMarkedUsable);
                mWifiConnectivityManager.disableNetworkSwitchDialog(durationMs);
            }
            mIsUsable = isUsable;
            // Wifi is set to be usable if adaptive connectivity is disabled.
            if (!mAdaptiveConnectivityEnabledSettingObserver.get()
                    || !mWifiSettingsStore.isWifiScoringEnabled()) {
                mIsUsable = true;
                if (mVerboseLoggingEnabled) {
                    Log.d(TAG, "Wifi scoring disabled - Notify that Wifi is usable");
                }
            }
            // Send `exiting` to NetworkScore, but don't update and send mLegacyIntScore
            // and don't change any other fields. All we want to do is relay to ConnectivityService
            // whether the current network is usable.
            if (SdkLevel.isAtLeastS()) {
                mNetworkAgent.sendNetworkScore(getNetworkScore(mLegacyIntScore, mIsUsable));
                if (mVerboseLoggingEnabled && !mIsUsable) {
                    Log.d(TAG, "Wifi is set to exiting by the external scorer");
                }
            }
            mWifiInfo.setUsable(mIsUsable);
            mWifiMetrics.setScorerPredictedWifiUsabilityState(mInterfaceName,
                    mIsUsable ? WifiMetrics.WifiUsabilityState.USABLE
                            : WifiMetrics.WifiUsabilityState.UNUSABLE);
        }

        @Override
        public void requestNudOperation(int sessionId) {
            if (mWifiConnectedNetworkScorerHolder == null
                    || sessionId == INVALID_SESSION_ID
                    || sessionId != getCurrentSessionId()) {
                Log.w(TAG, "Ignoring stale/invalid external input for NUD triggering"
                        + " sessionId=" + sessionId
                        + " currentSessionId=" + getCurrentSessionId());
                return;
            }
            if (mIsExternalScorerDryRun) {
                return;
            }
            if (!mAdaptiveConnectivityEnabledSettingObserver.get()
                    || !mWifiSettingsStore.isWifiScoringEnabled()) {
                if (mVerboseLoggingEnabled) {
                    Log.d(TAG, "Wifi scoring disabled - Cannot request a NUD operation");
                }
                return;
            }
            mNudYes++;
            if (mConnectedScorerHelper.checkNudIfNeeded(mIpClientManager, mLastNudCheckTimeMs,
                    mClock.getWallClockMillis())) {
                noteNudCheck();
            }
        }

        @Override
        public void blocklistCurrentBssid(int sessionId) {
            if (mWifiConnectedNetworkScorerHolder == null
                    || sessionId == INVALID_SESSION_ID
                    || sessionId != mSessionIdNoReset) {
                Log.w(TAG, "Ignoring stale/invalid external input for blocklisting"
                        + " sessionId=" + sessionId
                        + " mSessionIdNoReset=" + mSessionIdNoReset);
                return;
            }
            if (mIsExternalScorerDryRun) {
                return;
            }
            if (!mAdaptiveConnectivityEnabledSettingObserver.get()
                    || !mWifiSettingsStore.isWifiScoringEnabled()) {
                if (mVerboseLoggingEnabled) {
                    Log.d(TAG, "Wifi scoring disabled - Cannot blocklist current BSSID");
                }
                return;
            }
            if (mWifiInfoNoReset.getBSSID() != null) {
                mWifiBlocklistMonitor.handleBssidConnectionFailure(mWifiInfoNoReset.getBSSID(),
                        mCurrentWifiConfiguration,
                        WifiBlocklistMonitor.REASON_FRAMEWORK_DISCONNECT_CONNECTED_SCORE,
                        mWifiInfoNoReset.getRssi());
            }
        }
    }

    /**
     * If true, put the WifiScoreReport in lingering mode. A very low score is reported to
     * NetworkAgent, and the real score is never reported.
     */
    public void setShouldReduceNetworkScore(boolean shouldReduceNetworkScore) {
        Log.d(TAG, "setShouldReduceNetworkScore=" + shouldReduceNetworkScore
                + " mNetworkAgent is null? " + (mNetworkAgent == null));
        mShouldReduceNetworkScore = shouldReduceNetworkScore;
        // inform the external scorer that ongoing session has ended (since the score is no longer
        // under their control)
        if (mShouldReduceNetworkScore && mWifiConnectedNetworkScorerHolder != null) {
            mWifiConnectedNetworkScorerHolder.stopSession();
        }
        // if set to true, send score below disconnect threshold to start lingering
        sendNetworkScore(mLegacyIntScore, mIsUsable);
    }

    /**
     * Report network score to connectivity service.
     */
    private void reportNetworkScoreToConnectivityServiceIfNecessary(int adjustedScore,
            boolean isUsable) {
        mWifiInfo.setUsable(isUsable);
        if (adjustedScore == mWifiInfo.getScore()) {
            return;
        } else {
            if (mVerboseLoggingEnabled) {
                Log.d(TAG, "report new wifi score " + adjustedScore);
            }
            mWifiInfo.setScore(adjustedScore);
        }
        // only send network score if not lingering. If lingering, would have already sent score at
        // start of lingering.
        if (mShouldReduceNetworkScore) {
            return;
        }
        sendNetworkScore(adjustedScore, isUsable);
    }

    /**
     * Container for storing info about external scorer and tracking its death.
     */
    private final class WifiConnectedNetworkScorerHolder implements IBinder.DeathRecipient {
        private final IBinder mBinder;
        private final IWifiConnectedNetworkScorer mScorer;
        private int mSessionId = INVALID_SESSION_ID;
        private boolean mShouldCheckIpLayerOnce = false;

        WifiConnectedNetworkScorerHolder(IBinder binder, IWifiConnectedNetworkScorer scorer) {
            mBinder = binder;
            mScorer = scorer;
        }

        /**
         * Link WiFi connected scorer to death listener.
         */
        public boolean linkScorerToDeath() {
            try {
                mBinder.linkToDeath(this, 0);
            } catch (RemoteException e) {
                Log.e(TAG, "Unable to linkToDeath Wifi connected network scorer " + mScorer, e);
                return false;
            }
            return true;
        }

        /**
         * App hosting the binder has died.
         */
        @Override
        public void binderDied() {
            mWifiThreadRunner.post(() -> revertToDefaultConnectedScorer(),
                    "WifiConnectedNetworkScorerHolder#binderDied");
        }

        /**
         * Unlink this object from binder death.
         */
        public void reset() {
            mBinder.unlinkToDeath(this, 0);
        }

        /**
         * Starts a new scoring session.
         */
        public void startSession(int sessionId, boolean isUserSelected) {
            if (sessionId == INVALID_SESSION_ID) {
                throw new IllegalArgumentException();
            }
            if (mSessionId != INVALID_SESSION_ID) {
                // This is not expected to happen, log if it does
                Log.e(TAG, "Stopping session " + mSessionId + " before starting " + sessionId);
                stopSession();
            }
            // Bail now if the scorer has gone away
            if (this != mWifiConnectedNetworkScorerHolder) {
                return;
            }
            mSessionId = sessionId;
            mSessionIdNoReset = sessionId;
            try {
                WifiConnectedSessionInfo sessionInfo =
                        new WifiConnectedSessionInfo.Builder(sessionId)
                                .setUserSelected(isUserSelected)
                                .build();
                mScorer.onStart(sessionInfo);
            } catch (RemoteException e) {
                Log.e(TAG, "Unable to start Wifi connected network scorer " + this, e);
                revertToDefaultConnectedScorer();
            }
        }
        public void stopSession() {
            final int sessionId = mSessionId;
            if (sessionId == INVALID_SESSION_ID) return;
            mSessionId = INVALID_SESSION_ID;
            mShouldCheckIpLayerOnce = false;
            try {
                mScorer.onStop(sessionId);
            } catch (RemoteException e) {
                Log.e(TAG, "Unable to stop Wifi connected network scorer " + this, e);
                revertToDefaultConnectedScorer();
            }
        }

        public void onNetworkSwitchAccepted(
                int sessionId, int targetNetworkId, String targetBssid) {
            try {
                mScorer.onNetworkSwitchAccepted(sessionId, targetNetworkId, targetBssid);
            } catch (RemoteException e) {
                Log.e(TAG, "Unable to notify network switch accepted for Wifi connected network"
                        + " scorer " + this, e);
                revertToDefaultConnectedScorer();
            }
        }

        public void onNetworkSwitchRejected(
                int sessionId, int targetNetworkId, String targetBssid) {
            try {
                mScorer.onNetworkSwitchRejected(sessionId, targetNetworkId, targetBssid);
            } catch (RemoteException e) {
                Log.e(TAG, "Unable to notify network switch rejected for Wifi connected network"
                        + " scorer " + this, e);
                revertToDefaultConnectedScorer();
            }
        }

        public boolean isShellCommandScorer() {
            return mScorer instanceof WifiShellCommand.WifiScorer;
        }

        private void setShouldCheckIpLayerOnce(boolean shouldCheckIpLayerOnce) {
            mShouldCheckIpLayerOnce = shouldCheckIpLayerOnce;
        }

        private boolean getShouldCheckIpLayerOnce() {
            return mShouldCheckIpLayerOnce;
        }
    }

    private final ScoreUpdateObserverProxy mScoreUpdateObserverCallback =
            new ScoreUpdateObserverProxy();

    @Nullable
    private WifiConnectedNetworkScorerHolder mWifiConnectedNetworkScorerHolder;
    private boolean mIsExternalScorerDryRun;

    private final AdaptiveConnectivityEnabledSettingObserver
            mAdaptiveConnectivityEnabledSettingObserver;

    WifiScoreReport(ScoringParams scoringParams, Clock clock, WifiMetrics wifiMetrics,
            ExtendedWifiInfo wifiInfo, WifiNative wifiNative,
            WifiBlocklistMonitor wifiBlocklistMonitor,
            WifiThreadRunner wifiThreadRunner, WifiScoreCard wifiScoreCard,
            DeviceConfigFacade deviceConfigFacade, Context context,
            AdaptiveConnectivityEnabledSettingObserver adaptiveConnectivityEnabledSettingObserver,
            String interfaceName,
            ExternalScoreUpdateObserverProxy externalScoreUpdateObserverProxy,
            WifiSettingsStore wifiSettingsStore,
            WifiGlobals wifiGlobals,
            ActiveModeWarden activeModeWarden,
            WifiConnectivityManager wifiConnectivityManager,
            WifiConfigManager wifiConfigManager,
            ConnectedScorerHelper connectedScorerHelper,
            MlConnectedScorer mlConnectedScorer) {
        mScoringParams = scoringParams;
        mClock = clock;
        mAdaptiveConnectivityEnabledSettingObserver = adaptiveConnectivityEnabledSettingObserver;
        mVelocityBasedConnectedScorer = new VelocityBasedConnectedScorer(scoringParams, wifiGlobals,
                connectedScorerHelper);
        mMlConnectedScorer = mlConnectedScorer;
        mWifiMetrics = wifiMetrics;
        mWifiInfo = wifiInfo;
        mWifiNative = wifiNative;
        mWifiBlocklistMonitor = wifiBlocklistMonitor;
        mWifiThreadRunner = wifiThreadRunner;
        mWifiScoreCard = wifiScoreCard;
        mDeviceConfigFacade = deviceConfigFacade;
        mContext = context;
        mInterfaceName = interfaceName;
        mExternalScoreUpdateObserverProxy = externalScoreUpdateObserverProxy;
        mWifiSettingsStore = wifiSettingsStore;
        mWifiInfoNoReset = new WifiInfo(mWifiInfo);
        mWifiGlobals = wifiGlobals;
        mActiveModeWarden = activeModeWarden;
        mWifiConnectivityManager = wifiConnectivityManager;
        mWifiConfigManager = wifiConfigManager;
        mConnectedScorerHelper = connectedScorerHelper;
        mWifiMetrics.setIsExternalWifiScorerOn(false, Process.WIFI_UID);
        mWifiMetrics.setScorerPredictedWifiUsabilityState(mInterfaceName,
                WifiMetrics.WifiUsabilityState.UNKNOWN);
    }

    /** Returns whether this scores primary network based on the role */
    private boolean isPrimary() {
        return mCurrentRole != null && mCurrentRole == ActiveModeManager.ROLE_CLIENT_PRIMARY;
    }

    /**
     * Reset the last calculated score.
     */
    public void reset() {
        mSessionNumber++;
        clearScorerPredictionStatusForEvaluation();
        mLegacyIntScore = isPrimary() ? ConnectedScorer.WIFI_INITIAL_SCORE
                : ConnectedScorer.WIFI_SECONDARY_INITIAL_SCORE;
        mIsUsable = true;
        mWifiMetrics.setScorerPredictedWifiUsabilityState(mInterfaceName,
                WifiMetrics.WifiUsabilityState.UNKNOWN);
        if (mVelocityBasedConnectedScorer != null) {
            mVelocityBasedConnectedScorer.reset();
        }
        if (mMlConnectedScorer != null) {
            mMlConnectedScorer.reset();
        }
        mLastScoreBreachLowTimeMillis = INVALID_TIMESTAMP_MS;
        mLastLowScoreScanTimestampMs = INVALID_TIMESTAMP_MS;
        mLastNudCheckTimeMs = INVALID_TIMESTAMP_MS;
        if (mVerboseLoggingEnabled) Log.d(TAG, "reset");
    }

    /**
     * Enable/Disable verbose logging in score report generation.
     */
    public void enableVerboseLogging(boolean enable) {
        mVerboseLoggingEnabled = enable;
    }

    /**
     * Calculate the new wifi network score based on updated link layer stats.
     *
     * Note: This function will only notify connectivity services of the updated route if we are NOT
     * using a connected external WiFi scorer.
     * @param statsEntry the parcelable {@link WifiUsabilityStatsEntry}. The internal scorer type
     *                   and score will be set to the statsEntry
     */
    public void calculateAndReportScore(WifiUsabilityStatsEntry statsEntry) {
        if (mWifiInfo.getRssi() == mWifiInfo.INVALID_RSSI) {
            Log.d(TAG, "Not reporting score because RSSI is invalid");
            return;
        }

        long millis = mClock.getWallClockMillis();
        ConnectedScoreResult scoreResult;
        int internalScorerType = Flags.mlScorerInWifiFw() ? mWifiGlobals.getInternalScorerType()
                : SCORER_TYPE_VELOCITY;
        final boolean isIntervalMatch =
                POLLING_INTERVAL_MS == mWifiGlobals.getPollRssiIntervalMillis();
        if (internalScorerType == SCORER_TYPE_VELOCITY
                || (internalScorerType == SCORER_TYPE_ML && !isIntervalMatch)) {
            scoreResult = mVelocityBasedConnectedScorer.generateScoreResult(mWifiInfo, statsEntry,
                    millis, isPrimary());
            internalScorerType = SCORER_TYPE_VELOCITY; // May fall back to VELOCITY from ML
        } else if (internalScorerType == SCORER_TYPE_ML && isPrimary()) {
            scoreResult = mMlConnectedScorer.generateScoreResult(mWifiInfo, statsEntry, millis,
                    isPrimary());
        } else {
            return;
        }
        int adjustedScore = scoreResult.adjustedScore();
        statsEntry.setInternalScore(adjustedScore);
        statsEntry.setInternalScorerType(internalScorerType);

        mLastInternalScorerType = internalScorerType;
        mAospScorerPredictionStatusForEvaluation =
                convertToPredictionStatusForEvaluation(scoreResult.isWifiUsable());
        mWifiMetrics.setScorerPredictedWifiUsabilityState(mInterfaceName, scoreResult.isWifiUsable()
                ? WifiMetrics.WifiUsabilityState.UNUSABLE : WifiMetrics.WifiUsabilityState.USABLE);

        // Bypass AOSP scorer if Wifi connected network scorer is set
        if (mWifiConnectedNetworkScorerHolder != null && !mIsExternalScorerDryRun) {
            return;
        }

        if (mAdaptiveConnectivityEnabledSettingObserver.get()
                && mWifiSettingsStore.isWifiScoringEnabled()) {
            // Trigger Wi-Fi scan
            if (mActiveModeWarden.canRequestSecondaryTransientClientModeManager()) {
                if (mConnectedScorerHelper.triggerScanIfNeeded(mLastLowScoreScanTimestampMs,
                        mClock.getElapsedSinceBootMillis(), scoreResult.shouldTriggerScan())) {
                    mLastLowScoreScanTimestampMs = mClock.getElapsedSinceBootMillis();
                }
            }
            // Check NUD
            if (scoreResult.shouldCheckNud()) {
                mNudYes++;
                if (mConnectedScorerHelper.checkNudIfNeeded(mIpClientManager,
                        mLastNudCheckTimeMs, mClock.getWallClockMillis())) {
                    noteNudCheck();
                }
            }
            // Report to ConnectivityService
            reportNetworkScoreToConnectivityServiceIfNecessary(adjustedScore,
                    scoreResult.isWifiUsable());
            // Set the global variables which are used when MBB starts, a new Network Agent comes or
            // role changes.
            mLegacyIntScore = adjustedScore;
            mIsUsable = scoreResult.isWifiUsable();
        } else {
            // Stay a notch above the transition score if adaptive connectivity is disabled.
            reportNetworkScoreToConnectivityServiceIfNecessary(
                    ConnectedScorer.WIFI_TRANSITION_SCORE + 1, true);
            mLegacyIntScore = ConnectedScorer.WIFI_TRANSITION_SCORE + 1;
            mIsUsable = true;
        }

        updateWifiMetrics(millis, internalScorerType, scoreResult.score(), adjustedScore, -1);
    }

    private boolean enoughTimePassedSinceLastLowConnectedScoreScan() {
        return mLastLowScoreScanTimestampMs == INVALID_TIMESTAMP_MS
                || mClock.getElapsedSinceBootMillis() - mLastLowScoreScanTimestampMs
                > (mWifiGlobals.getWifiLowConnectedScoreScanPeriodSeconds() * 1000);
    }

    private int getCurrentNetId() {
        int netId = 0;
        if (mNetworkAgent != null) {
            final Network network = mNetworkAgent.getNetwork();
            if (network != null) {
                netId = network.getNetId();
            }
        }
        return netId;
    }

    @Nullable
    private NetworkCapabilities getCurrentNetCapabilities() {
        return mNetworkAgent == null ? null : mNetworkAgent.getCurrentNetworkCapabilities();
    }

    private int getCurrentSessionId() {
        return sessionIdFromNetId(getCurrentNetId());
    }

    /**
     * Encodes a network id into a scoring session id.
     *
     * We use a different numeric value for session id and the network id
     * to make it clear that these are not the same thing. However, for
     * easier debugging, the network id can be recovered by dropping the
     * last decimal digit (at least until they get very, very, large).
     */
    public static int sessionIdFromNetId(final int netId) {
        if (netId <= 0) return INVALID_SESSION_ID;
        return (int) (((long) netId * 10 + (8 - (netId % 9))) % Integer.MAX_VALUE + 1);
    }

    private void updateWifiMetrics(long now, int internalScorerType, int internalScore,
             int internalAdjustedScore, int externalScore) {
        int netId = getCurrentNetId();

        logLinkMetrics(now, netId, internalScorerType, internalScore, internalAdjustedScore,
                externalScore);
        mWifiMetrics.incrementWifiScoreCount(mInterfaceName,
                internalScorerType == SCORER_TYPE_INVALID ? externalScore : internalAdjustedScore);
    }

    /** Called when the IpClientManager is changed. */
    public void setIpClientManager(@Nullable IpClientManager ipClientManager) {
        mIpClientManager = ipClientManager;
    }

    /**
     * Should be called when a reachability check has been issued
     *
     * When the caller has requested an IP reachability check, calling this will
     * help to rate-limit requests via shouldCheckIpLayer()
     */
    public void noteNudCheck() {
        mLastNudCheckTimeMs = mClock.getWallClockMillis();
        mNudCount++;
    }

    /**
     * Data for dumpsys
     *
     * These are stored as csv formatted lines
     */
    private LinkedList<String> mLinkMetricsHistory = new LinkedList<String>();

    /**
     * Data logging for dumpsys
     */
    private void logLinkMetrics(long now, int netId, int internalScorerType, int internalScore,
            int internalAdjustedScore, int externalScore) {
        if (now < FIRST_REASONABLE_WALL_CLOCK) return;
        double filteredRssi = -1;
        double rssiThreshold = -1;
        if (mWifiConnectedNetworkScorerHolder == null) {
            filteredRssi = mVelocityBasedConnectedScorer.getFilteredRssi();
            rssiThreshold = mVelocityBasedConnectedScorer.getAdjustedRssiThreshold();
        }
        WifiScoreCard.PerNetwork network = mWifiScoreCard.lookupNetwork(mWifiInfo.getSSID());
        StringJoiner stats = new StringJoiner(",");

        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(now);
        // timestamp format: "%tm-%td %tH:%tM:%tS.%tL"
        stats.add(StringUtil.calendarToString(c));
        stats.add(Integer.toString(mSessionNumber));
        stats.add(Integer.toString(netId));
        stats.add(Integer.toString(mWifiInfo.getRssi()));
        stats.add(StringUtil.doubleToString(filteredRssi, 1));
        stats.add(Double.toString(rssiThreshold));
        stats.add(Integer.toString(mWifiInfo.getFrequency()));
        stats.add(Integer.toString(mWifiInfo.getLinkSpeed()));
        stats.add(Integer.toString(mWifiInfo.getRxLinkSpeedMbps()));
        stats.add(Integer.toString(network.getTxLinkBandwidthKbps() / 1000));
        stats.add(Long.toString(network.getRxLinkBandwidthKbps() / 1000));
        stats.add(Long.toString(mWifiMetrics.getTotalBeaconRxCount()));
        stats.add(StringUtil.doubleToString(mWifiInfo.getSuccessfulTxPacketsPerSecond(), 2));
        stats.add(StringUtil.doubleToString(mWifiInfo.getRetriedTxPacketsPerSecond(), 2));
        stats.add(StringUtil.doubleToString(mWifiInfo.getLostTxPacketsPerSecond(), 2));
        stats.add(StringUtil.doubleToString(mWifiInfo.getSuccessfulRxPacketsPerSecond(), 2));
        stats.add(Integer.toString(mNudYes));
        stats.add(Integer.toString(mNudCount));
        String internalScorerName = switch (internalScorerType) {
            case SCORER_TYPE_VELOCITY -> "Velocity";
            case SCORER_TYPE_ML -> "ML";
            default -> "Invalid";
        };
        stats.add(internalScorerName);
        stats.add(Integer.toString(internalScore));
        stats.add(Integer.toString(internalAdjustedScore));
        stats.add(Integer.toString(externalScore));
        // MLO stats
        for (MloLink link : mWifiInfo.getAffiliatedMloLinks()) {
            StringJoiner mloStats = new StringJoiner(",", "{", "}");
            mloStats.add(Integer.toString(link.getLinkId()));
            mloStats.add(Integer.toString(link.getRssi()));
            final int band;
            switch (link.getBand()) {
                case WifiScanner.WIFI_BAND_24_GHZ:
                    band = ScanResult.WIFI_BAND_24_GHZ;
                    break;
                case WifiScanner.WIFI_BAND_5_GHZ:
                    band = ScanResult.WIFI_BAND_5_GHZ;
                    break;
                case WifiScanner.WIFI_BAND_6_GHZ:
                    band = ScanResult.WIFI_BAND_6_GHZ;
                    break;
                case WifiScanner.WIFI_BAND_60_GHZ:
                    band = ScanResult.WIFI_BAND_60_GHZ;
                    break;
                default:
                    band = ScanResult.UNSPECIFIED;
                    break;
            }
            int linkFreq =
                    ScanResult.convertChannelToFrequencyMhzIfSupported(link.getChannel(), band);
            mloStats.add(Integer.toString(linkFreq));
            mloStats.add(Integer.toString(link.getTxLinkSpeedMbps()));
            mloStats.add(Integer.toString(link.getRxLinkSpeedMbps()));
            mloStats.add(Long.toString(mWifiMetrics.getTotalBeaconRxCount(link.getLinkId())));
            mloStats.add(StringUtil.doubleToString(link.getSuccessfulTxPacketsPerSecond(), 2));
            mloStats.add(StringUtil.doubleToString(link.getRetriedTxPacketsPerSecond(), 2));
            mloStats.add(StringUtil.doubleToString(link.getLostTxPacketsPerSecond(), 2));
            mloStats.add(StringUtil.doubleToString(link.getSuccessfulRxPacketsPerSecond(), 2));
            mloStats.add(MloLink.getStateString(link.getState()));
            mloStats.add(
                    WifiUsabilityStatsEntry.getLinkStateString(
                            mWifiMetrics.getLinkUsageState(link.getLinkId())));
            stats.add(mloStats.toString());
        }


        synchronized (mLinkMetricsHistory) {
            mLinkMetricsHistory.add(stats.toString());
            while (mLinkMetricsHistory.size() > DUMPSYS_ENTRY_COUNT_LIMIT) {
                mLinkMetricsHistory.removeFirst();
            }
        }
    }

    /**
     * Tag to be used in dumpsys request
     */
    public static final String DUMP_ARG = "WifiScoreReport";

    /**
     * Dump logged signal strength and traffic measurements.
     * @param fd unused
     * @param pw PrintWriter for writing dump to
     * @param args unused
     */
    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        LinkedList<String> history;
        synchronized (mLinkMetricsHistory) {
            history = new LinkedList<>(mLinkMetricsHistory);
        }
        // Note: MLO stats are printed only for multi link connection. It is appended to the
        // existing print as {link1Id,link1Rssi ... ,link1UsageState}, {link2Id,link2Rssi ... ,
        // link2UsageState}, ..etc.
        pw.println(
                "time,session,netid,rssi,filtered_rssi,rssi_threshold,freq,txLinkSpeed,"
                    + "rxLinkSpeed,txTput,rxTput,bcnCnt,tx_good,tx_retry,tx_bad,rx_pps,nudrq,nuds,"
                    + "internalScorerType, internalScore, internalAdjustedScore, externalScore,"
                    + "{linkId,linkRssi,linkFreq,txLinkSpeed,rxLinkSpeed,linkBcnCnt,"
                    + "linkTxGood,linkTxRetry,linkTxBad,linkRxGood,linkMloState,linkUsageState}");
        for (String line : history) {
            pw.println(line);
        }
        history.clear();
        pw.println("externalScorerActive=" + (mWifiConnectedNetworkScorerHolder != null));
        pw.println("mShouldReduceNetworkScore=" + mShouldReduceNetworkScore);
    }

    /**
     * Set a scorer for Wi-Fi connected network score handling.
     */
    public boolean setWifiConnectedNetworkScorer(IBinder binder,
            IWifiConnectedNetworkScorer scorer, int callerUid) {
        if (binder == null || scorer == null) return false;
        // Enforce that only a single scorer can be set successfully.
        if (mWifiConnectedNetworkScorerHolder != null) {
            Log.e(TAG, "Failed to set current scorer because one scorer is already set");
            return false;
        }
        WifiConnectedNetworkScorerHolder scorerHolder =
                new WifiConnectedNetworkScorerHolder(binder, scorer);
        if (!scorerHolder.linkScorerToDeath()) {
            return false;
        }
        mWifiConnectedNetworkScorerHolder = scorerHolder;
        mDeviceConfigFacade.setDryRunScorerPkgNameChangedListener(dryRunPkgName -> {
            mIsExternalScorerDryRun =
                isExternalScorerDryRun(dryRunPkgName, callerUid);
            mWifiGlobals.setUsingExternalScorer(!mIsExternalScorerDryRun);
        });
        mIsExternalScorerDryRun =
                isExternalScorerDryRun(mDeviceConfigFacade.getDryRunScorerPkgName(), callerUid);
        mWifiGlobals.setUsingExternalScorer(!mIsExternalScorerDryRun);

        // Register to receive updates from external scorer.
        mExternalScoreUpdateObserverProxy.registerCallback(mScoreUpdateObserverCallback);

        mWifiMetrics.setIsExternalWifiScorerOn(true, callerUid);
        // If there is already a connection, start a new session
        final int netId = getCurrentNetId();
        if (netId > 0 && !mShouldReduceNetworkScore) {
            startConnectedNetworkScorer(netId, mIsUserSelected);
        }
        return true;
    }

    private boolean isExternalScorerDryRun(String dryRunPkgName, int uid) {
        Log.d(TAG, "isExternalScorerDryRun(" + dryRunPkgName + ", " + uid + ")");
        String[] packageNames = mContext.getPackageManager().getPackagesForUid(uid);
        for (String packageName : packageNames) {
            if (!TextUtils.isEmpty(packageName)
                    && packageName.equalsIgnoreCase(dryRunPkgName)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Clear an existing scorer for Wi-Fi connected network score handling.
     */
    public void clearWifiConnectedNetworkScorer() {
        if (mWifiConnectedNetworkScorerHolder == null) {
            return;
        }
        mWifiConnectedNetworkScorerHolder.reset();
        revertToDefaultConnectedScorer();
    }

    /**
     * Notify the connected network scorer of the user accepting a network switch.
     */
    public void onNetworkSwitchAccepted(int targetNetworkId, String targetBssid) {
        if (mWifiConnectedNetworkScorerHolder == null || mIsExternalScorerDryRun) {
            return;
        }
        mWifiConnectedNetworkScorerHolder.onNetworkSwitchAccepted(
                getCurrentSessionId(), targetNetworkId, targetBssid);
    }

    /**
     * Notify the connected network scorer of the user rejecting a network switch.
     */
    public void onNetworkSwitchRejected(int targetNetworkId, String targetBssid) {
        if (mWifiConnectedNetworkScorerHolder == null || mIsExternalScorerDryRun) {
            return;
        }
        mWifiConnectedNetworkScorerHolder.onNetworkSwitchRejected(
                getCurrentSessionId(), targetNetworkId, targetBssid);
    }

    /**
     * If this connection is not going to be the default route on the device when cellular is
     * present, don't send this connection to external scorer for scoring (since scoring only makes
     * sense if we need to score wifi vs cellular to determine the default network).
     *
     * Hence, we ignore local only or restricted wifi connections.
     * @return true if the connection is local only or restricted, false otherwise.
     */
    private boolean isLocalOnlyOrRestrictedConnection() {
        final NetworkCapabilities nc = getCurrentNetCapabilities();
        if (nc == null) return false;
        if (SdkLevel.isAtLeastS()) {
            // restricted connection support only added in S.
            if (nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_OEM_PAID)
                    || nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_OEM_PRIVATE)) {
                // restricted connection.
                Log.v(TAG, "Restricted connection, ignore.");
                return true;
            }
        }
        if (!nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
            // local only connection.
            Log.v(TAG, "Local only connection, ignore.");
            return true;
        }
        return false;
    }

    /**
     * Start the registered Wi-Fi connected network scorer.
     * @param netId identifies the current android.net.Network
     */
    public void startConnectedNetworkScorer(int netId, boolean isUserSelected) {
        Log.d(TAG, "startConnectedNetworkScorer(" + netId + ", " + isUserSelected + ")");
        mIsUserSelected = isUserSelected;
        final int sessionId = getCurrentSessionId();
        if (mWifiConnectedNetworkScorerHolder == null
                || netId != getCurrentNetId()
                || isLocalOnlyOrRestrictedConnection()
                || sessionId == INVALID_SESSION_ID) {
            StringBuilder sb = new StringBuilder();
            sb.append("Cannot start external scoring netId=").append(netId)
                    .append(" currentNetId=").append(getCurrentNetId());
            if (mVerboseLoggingEnabled) {
                sb.append(" currentNetCapabilities=").append(getCurrentNetCapabilities());
            }
            sb.append(" sessionId=").append(sessionId);
            Log.w(TAG, sb.toString());
            return;
        }
        mCurrentWifiConfiguration = mWifiConfigManager.getConfiguredNetwork(
                mWifiInfo.getNetworkId());
        mWifiInfo.setScore(isPrimary() ? ConnectedScorer.WIFI_MAX_SCORE
                : ConnectedScorer.WIFI_SECONDARY_MAX_SCORE);
        mWifiConnectedNetworkScorerHolder.startSession(sessionId, mIsUserSelected);
        mWifiInfoNoReset.setBSSID(mWifiInfo.getBSSID());
        mWifiInfoNoReset.setSSID(mWifiInfo.getWifiSsid());
        mWifiInfoNoReset.setRssi(mWifiInfo.getRssi());
        mLastScoreBreachLowTimeMillis = INVALID_TIMESTAMP_MS;
    }

    /**
     * Stop the registered Wi-Fi connected network scorer.
     */
    public void stopConnectedNetworkScorer() {
        mNetworkAgent = null;
        if (mWifiConnectedNetworkScorerHolder == null) {
            return;
        }
        mWifiConnectedNetworkScorerHolder.stopSession();

        long millis = mClock.getWallClockMillis();
        // Blocklist the current BSS
        if ((mLastScoreBreachLowTimeMillis != INVALID_TIMESTAMP_MS)
                && ((millis - mLastScoreBreachLowTimeMillis)
                        >= MIN_TIME_TO_WAIT_BEFORE_BLOCKLIST_BSSID_MILLIS)) {
            mWifiBlocklistMonitor.handleBssidConnectionFailure(mWifiInfo.getBSSID(),
                    mCurrentWifiConfiguration,
                    WifiBlocklistMonitor.REASON_FRAMEWORK_DISCONNECT_CONNECTED_SCORE,
                    mWifiInfo.getRssi());
            mLastScoreBreachLowTimeMillis = INVALID_TIMESTAMP_MS;
        }
    }

    /**
     * Set NetworkAgent
     */
    public void setNetworkAgent(WifiNetworkAgent agent) {
        WifiNetworkAgent oldAgent = mNetworkAgent;
        mNetworkAgent = agent;
        // if mNetworkAgent was null previously, then the score wasn't sent to ConnectivityService.
        // Send it now that the NetworkAgent has been set.
        if (oldAgent == null && mNetworkAgent != null) {
            sendNetworkScore(mLegacyIntScore, mIsUsable);
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private NetworkScore getNetworkScore(int adjustedScore, boolean isUsable) {
        // We should force keep connected for a MBB CMM which is not lingering.
        boolean shouldForceKeepConnected =
                mCurrentRole == ActiveModeManager.ROLE_CLIENT_SECONDARY_TRANSIENT
                        && !mShouldReduceNetworkScore;
        int keepConnectedReason =
                shouldForceKeepConnected
                        ? NetworkScore.KEEP_CONNECTED_FOR_HANDOVER
                        : NetworkScore.KEEP_CONNECTED_NONE;
        return new NetworkScore.Builder()
                .setLegacyInt(mShouldReduceNetworkScore ? LINGERING_SCORE : adjustedScore)
                .setTransportPrimary(mCurrentRole == ActiveModeManager.ROLE_CLIENT_PRIMARY)
                .setExiting(!isUsable || mShouldReduceNetworkScore)
                .setKeepConnectedReason(keepConnectedReason)
                .build();
    }

    /** Get counts when we voted for a NUD. */
    @VisibleForTesting
    public int getNudYes() {
        return mNudYes;
    }

    private void revertToDefaultConnectedScorer() {
        Log.d(TAG, "Using internal scorer");
        mWifiConnectedNetworkScorerHolder = null;
        mDeviceConfigFacade.setDryRunScorerPkgNameChangedListener(null);
        mWifiGlobals.setUsingExternalScorer(false);
        mExternalScoreUpdateObserverProxy.unregisterCallback(mScoreUpdateObserverCallback);
        mWifiMetrics.setIsExternalWifiScorerOn(false, Process.WIFI_UID);
    }

    /**
     * This is a function of {@link #mCurrentRole} {@link #mShouldReduceNetworkScore}, and
     * {@link #mLegacyIntScore}, and should be called when any of them changes.
     */
    private void sendNetworkScore(int adjustedScore, boolean isUsable) {
        if (mNetworkAgent == null) {
            return;
        }
        if (SdkLevel.isAtLeastS()) {
            // NetworkScore was introduced in S
            mNetworkAgent.sendNetworkScore(getNetworkScore(adjustedScore, isUsable));
        }
    }

    private int convertToPredictionStatusForEvaluation(boolean isUsable) {
        return isUsable
                ? WifiStatsLog.SCORER_PREDICTION_RESULT_REPORTED__WIFI_PREDICTED_USABILITY_STATE__WIFI_USABILITY_PREDICTED_USABLE
                : WifiStatsLog.SCORER_PREDICTION_RESULT_REPORTED__WIFI_PREDICTED_USABILITY_STATE__WIFI_USABILITY_PREDICTED_UNUSABLE;
    }

    /**
     * Get whether an external scorer is active for scoring.
     */
    public boolean isExternalScorerActive() {
        return mWifiConnectedNetworkScorerHolder != null && !mIsExternalScorerDryRun;
    }

    /**
     * Get wifi predicted state for metrics
     */
    public int getExternalScorerPredictionStatusForEvaluation() {
        return mExternalScorerPredictionStatusForEvaluation;
    }

    /**
     * Get wifi predicted state for metrics
     */
    public int getAospScorerPredictionStatusForEvaluation() {
        return mAospScorerPredictionStatusForEvaluation;
    }

    /**
     * Get the internal scorer type that did the latest evaluation
     */
    public int getLastInternalScorerType() {
        return mLastInternalScorerType;
    }

    /**
     * Clear the predicted states for metrics
     */
    public void clearScorerPredictionStatusForEvaluation() {
        mExternalScorerPredictionStatusForEvaluation =
                WifiStatsLog.SCORER_PREDICTION_RESULT_REPORTED__WIFI_PREDICTED_USABILITY_STATE__WIFI_USABILITY_PREDICTED_NONE;
        mAospScorerPredictionStatusForEvaluation =
                WifiStatsLog.SCORER_PREDICTION_RESULT_REPORTED__WIFI_PREDICTED_USABILITY_STATE__WIFI_USABILITY_PREDICTED_NONE;
    }

    /** Called when the owner {@link ConcreteClientModeManager}'s role changes. */
    public void onRoleChanged(@Nullable ClientRole role) {
        mCurrentRole = role;
        sendNetworkScore(mLegacyIntScore, mIsUsable);
    }

    /**
     * Get whether we are in the lingering state or not.
     */
    public boolean getLingering() {
        return (SdkLevel.isAtLeastS() && mWifiConnectedNetworkScorerHolder != null
                && !mIsExternalScorerDryRun)
                ? !mIsUsable : mLegacyIntScore < ConnectedScorer.WIFI_TRANSITION_SCORE;
    }
}
