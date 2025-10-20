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

package com.android.server.wifi.ml_connected_scorer;

import static com.android.server.wifi.Clock.INVALID_TIMESTAMP_MS;
import static com.android.server.wifi.ml_connected_scorer.Flags.HYSTERESIS_NETWORK_STATUS_CHANGE_MILLIS;
import static com.android.server.wifi.ml_connected_scorer.Flags.MIN_TIME_TO_WAIT_BEFORE_BLOCK_BSSID_MILLIS;
import static com.android.server.wifi.ml_connected_scorer.Flags.RSSI_THRESHOLD_NO_HYSTERESIS_NETWORK_STATUS_CHANGE_DBM;
import static com.android.server.wifi.ml_connected_scorer.Flags.SCAN_TRIGGERING_THRESHOLD;
import static com.android.server.wifi.ml_connected_scorer.Flags.SCORE_BREACHING_RSSI_THRESHOLD;
import static com.android.server.wifi.ml_connected_scorer.Flags.THRESHOLD;
import static com.android.server.wifi.ml_connected_scorer.Flags.THRESHOLD_HYSTERESIS;

import static java.lang.Math.min;

import android.net.wifi.WifiInfo;
import android.net.wifi.WifiUsabilityStatsEntry;
import android.util.Log;

import com.android.server.wifi.ConnectedScoreResult;
import com.android.server.wifi.ConnectedScorer;

import com.google.common.annotations.VisibleForTesting;
import com.google.errorprone.annotations.CanIgnoreReturnValue;

import java.util.ArrayDeque;
import java.util.Deque;

/** Class to store a buffer of wifi stats and call classifier. */
public class MlConnectedScorer extends ConnectedScorer {
    private static final String TAG = "MlConnectedScorer";
    private boolean mVerboseLoggingEnabled = false;
    @VisibleForTesting
    final Deque<WifiUsabilityStatsEntryWrapper> mBuffer = new ArrayDeque<>();
    private double mPrevScore = Constants.MAX_SCORE;
    private double mPrevRawScore = Constants.MAX_SCORE;
    private boolean mIsScoreScanThresholdBreach = false;
    @VisibleForTesting
    boolean mRecommendDefaultNetwork = true;
    private double mLastScoreBreachTimeMillis = INVALID_TIMESTAMP_MS;
    @VisibleForTesting
    boolean mBlockCurrentBssid = false;
    private boolean mIsScoreTrendingDownwards = false;
    private WifiUsabilityClassifierFactory mFactory; // the factory used to get the classifier
    private MlConnectedScorerHelper mHelper;
    private String mLastBssid = null;
    private int mLastFrequency = -1;
    public MlConnectedScorer(WifiUsabilityClassifierFactory factory,
            MlConnectedScorerHelper helper) {
        mFactory = factory;
        mHelper = helper;
    }

    /**
     * Generate a {@link ConnectedScoreResult} based on history input data.
     */
    @Override
    public ConnectedScoreResult generateScoreResult(WifiInfo wifiInfo,
            WifiUsabilityStatsEntry stats, long millis, boolean isPrimary) {
        boolean isSameBssidAndFreq =
                mHelper.isSameBssidAndFreq(mLastBssid, mLastFrequency, wifiInfo);
        mLastBssid = wifiInfo.getBSSID();
        mLastFrequency = wifiInfo.getFrequency();

        // ML scorer doesn't support non-primary mode
        double score = isPrimary
                ? getUpdatedScore(isSameBssidAndFreq, stats) : Constants.UNCLASSIFIED_SCORE;
        double adjustedScore = Constants.UNCLASSIFIED_SCORE;
        boolean shouldCheckNud = false;
        if (score != Constants.UNCLASSIFIED_SCORE) {
            adjustedScore = adjustScore(score, isSameBssidAndFreq, stats.getRssi(), THRESHOLD,
                    THRESHOLD_HYSTERESIS, millis);
            shouldCheckNud = (score < WIFI_TRANSITION_SCORE)
                || mHelper.isRssiVeryLowAndLinkSpeedLow(stats)
                || mHelper.isRssiLowAndLinkSpeedVeryLow(stats);
        }

        return ConnectedScoreResult.builder()
                .setScore((int) score)
                .setAdjustedScore((int) adjustedScore)
                .setIsWifiUsable(mRecommendDefaultNetwork)
                .setShouldTriggerScan(mIsScoreScanThresholdBreach)
                .setShouldCheckNud(shouldCheckNud)
                .setShouldBlockBssid(mBlockCurrentBssid)
                .build();
    }

    /**
     * Enable/Disable verbose logging.
     */
    public void enableVerboseLogging(boolean enable) {
        mVerboseLoggingEnabled = enable;
        mHelper.enableVerboseLogging(enable);
    }
    /**
     * Return the score after it has been calculated and adjusted.
     *
     * @param isSameBssidAndFreq the flag to indicate whether the BSSID and the frequency of network
     *     stays the same or not relative to the last update of Wi-Fi usability stats.
     * @param stats the updated Wi-Fi usability statistics.
     */
    @VisibleForTesting
    double getUpdatedScore(
            boolean isSameBssidAndFreq,
            WifiUsabilityStatsEntry stats) {
        WifiUsabilityStatsEntryWrapper wrapper = new WifiUsabilityStatsEntryWrapper(stats);
        // Filter based on bssid or timestamp difference.
        boolean isTimeGapTooLarge = !mBuffer.isEmpty()
                && mHelper.isTimeStampGapTooLarge(mBuffer.peekLast().getStats(), stats);
        if (!isSameBssidAndFreq || isTimeGapTooLarge) {
            mBuffer.clear();
        }
        if (!mBuffer.isEmpty()) {
            wrapper.setDiffs(mBuffer.peekLast().getStats());
        } else {
            wrapper.setDefaultValues();
        }
        mBuffer.add(wrapper);
        if (mBuffer.size() > Constants.MAX_BUFFER_SIZE) {
            mBuffer.remove();
        }
        if (mBuffer.size() >= Constants.MIN_BUFFER_SIZE) {
            WifiUsabilityClassifier classifier =
                    mFactory.getClassifier(Constants.RANDOM_FOREST_MODEL_ID);
            if (classifier == null) {
                Log.e(TAG, "Can't load the ML model!");
                return Constants.UNCLASSIFIED_SCORE;
            }
            double rawScore = classifier.calculateScore(mBuffer);
            rawScore = overrideScoreIfLinkAlreadyBad(
                    rawScore,
                    wrapper.totalTxBad,
                    wrapper.totalTxSuccessDiff,
                    stats.getRssi(),
                    isTimeGapTooLarge);
            return rawScore;
        }
        return Constants.UNCLASSIFIED_SCORE;
    }

    private double overrideScoreIfLinkAlreadyBad(
            double rawScore,
            double totalTxBadDiff,
            double totalTxSuccessDiff,
            int rssi,
            boolean isTimeGapTooLarge) {
        if (isTimeGapTooLarge) {
            return rawScore;
        }
        if (mHelper.isLinkQualityBad(totalTxBadDiff, totalTxSuccessDiff, rssi)) {
            return THRESHOLD - 1;
        }
        return rawScore;
    }

    private double scaleScoreForThreshold(double score, double threshold) {
        return min((WIFI_TRANSITION_SCORE / threshold) * score, Constants.MAX_SCORE);
    }

    @CanIgnoreReturnValue
    @VisibleForTesting
    double adjustScore(double rawScore, boolean isSameBssidAndFreq, int rssi, float threshold,
            float thresholdHysteresis, long currentTime) {
        if (!isSameBssidAndFreq) {
            // Roaming or reconnection just happens
            reset();
            // Force to a high score so that the new network has the chance to evaluate
            rawScore = Constants.MAX_SCORE;
        }
        double adjustedThreshold = threshold;
        if (mPrevScore != Constants.UNCLASSIFIED_SCORE
                && mPrevScore < WIFI_TRANSITION_SCORE) {
            adjustedThreshold += thresholdHysteresis;
        }
        double score = scaleScoreForThreshold(rawScore, adjustedThreshold);
        mIsScoreScanThresholdBreach = (0 <= rawScore && rawScore < SCAN_TRIGGERING_THRESHOLD)
                && mPrevRawScore >= SCAN_TRIGGERING_THRESHOLD;
        // Stay a notch above or below the transition score to reduce ambiguity.
        if ((int) score == (int) WIFI_TRANSITION_SCORE) {
            score = mPrevScore > WIFI_TRANSITION_SCORE ? score + 1 : score - 1;
        }

        boolean isScoreBreachLow = rssi < SCORE_BREACHING_RSSI_THRESHOLD
                  && 0 <= score
                  && score < WIFI_TRANSITION_SCORE
                  && mPrevScore >= WIFI_TRANSITION_SCORE;
        if (isScoreBreachLow) {
            // Record whether score is trending downwards before breaching by checking whether the
            // previous score is in the range of
            // (WIFI_TRANSITION_SCORE, WIFI_MAX_SCORE).  If so remove
            // hysteresis for default network status change.
            mIsScoreTrendingDownwards = mPrevScore < WIFI_MAX_SCORE;
        }
        boolean isScoreBreachHigh = 0 <= mPrevScore
                && mPrevScore < WIFI_TRANSITION_SCORE
                && score >= WIFI_TRANSITION_SCORE;

        if (isScoreBreachLow || isScoreBreachHigh) {
            mLastScoreBreachTimeMillis = currentTime;
        }

        if (mLastScoreBreachTimeMillis != INVALID_TIMESTAMP_MS) {
            if (((currentTime - mLastScoreBreachTimeMillis)
                    > HYSTERESIS_NETWORK_STATUS_CHANGE_MILLIS
                    || mIsScoreTrendingDownwards
                    || rssi <= RSSI_THRESHOLD_NO_HYSTERESIS_NETWORK_STATUS_CHANGE_DBM)
                    && rssi < SCORE_BREACHING_RSSI_THRESHOLD
                    && score < WIFI_TRANSITION_SCORE) {
                mRecommendDefaultNetwork = false;
            }
            if ((currentTime - mLastScoreBreachTimeMillis) > HYSTERESIS_NETWORK_STATUS_CHANGE_MILLIS
                    && score > WIFI_TRANSITION_SCORE) {
                mRecommendDefaultNetwork = true;
            }
        }

        mBlockCurrentBssid = score < WIFI_TRANSITION_SCORE
                && rssi < SCORE_BREACHING_RSSI_THRESHOLD
                && mLastScoreBreachTimeMillis != INVALID_TIMESTAMP_MS
                && (currentTime - mLastScoreBreachTimeMillis)
                        > MIN_TIME_TO_WAIT_BEFORE_BLOCK_BSSID_MILLIS;

        mPrevScore = score;
        mPrevRawScore = rawScore;
        return score;
    }

    /** Reset score to remove hysteresis in adjustScore() for a new connection. */
    @Override
    public void reset() {
        mPrevScore = Constants.MAX_SCORE;
        mPrevRawScore = Constants.MAX_SCORE;
        mRecommendDefaultNetwork = true;
        mBlockCurrentBssid = false;
        mLastScoreBreachTimeMillis = INVALID_TIMESTAMP_MS;
        mIsScoreTrendingDownwards = false;
    }
}
