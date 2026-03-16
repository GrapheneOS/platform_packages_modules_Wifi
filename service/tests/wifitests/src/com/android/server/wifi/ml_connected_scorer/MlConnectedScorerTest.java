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

import static com.android.server.wifi.ConnectedScorer.WIFI_MAX_SCORE;
import static com.android.server.wifi.ml_connected_scorer.Flags.HYSTERESIS_NETWORK_STATUS_CHANGE_MILLIS;
import static com.android.server.wifi.ml_connected_scorer.Flags.MIN_TIME_TO_WAIT_BEFORE_BLOCK_BSSID_MILLIS;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

import android.net.wifi.WifiInfo;
import android.net.wifi.WifiUsabilityStatsEntry;
import android.net.wifi.WifiUsabilityStatsEntry.ContentionTimeStats;
import android.net.wifi.WifiUsabilityStatsEntry.RadioStats;
import android.net.wifi.WifiUsabilityStatsEntry.RateStats;
import android.util.SparseArray;

import androidx.test.filters.SmallTest;

import com.android.server.wifi.ConnectedScoreResult;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Unit tests for {@link com.android.server.wifi.ml_connected_scorer.MlConnectedScorer}.
 */
@SmallTest
public final class MlConnectedScorerTest {
    private static final long TIME_STAMP_MS = 1234567L;
    private static final int TEST_RSSI = 23;
    private static final double TEST_MODEL_SCORE = 60.0;
    private static final float TEST_THRESHOLD = 50.0f;
    private static final float TEST_THRESHOLD_HYSTERESIS = 0.0f;
    static final double TOL = 1e-6; // for assertEquals(double, double, tolerance)

    private MlConnectedScorer mScorer;

    @Mock WifiUsabilityClassifier mMockClassifier;
    @Mock WifiUsabilityClassifierFactory mMockFactory;
    @Mock MlConnectedScorerHelper mMockHelper;
    @Mock WifiInfo mMockWifiInfo;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mScorer = new MlConnectedScorer(mMockFactory, mMockHelper);
        when(mMockFactory.getClassifier(anyInt())).thenReturn(mMockClassifier);
        when(mMockClassifier.calculateScore(any())).thenReturn(TEST_MODEL_SCORE);
        when(mMockHelper.isTimeStampGapTooLarge(any(WifiUsabilityStatsEntry.class),
                any(WifiUsabilityStatsEntry.class))).thenReturn(false);
    }

    @Test
    public void generateScoreResult_notPrimary_returnUnclassfiedScore() {
        ConnectedScoreResult result = mScorer.generateScoreResult(mMockWifiInfo,
                getUsabilityStats(TIME_STAMP_MS), TIME_STAMP_MS, false);
        assertEquals(Constants.UNCLASSIFIED_SCORE, result.score(), TOL);
        assertEquals(Constants.UNCLASSIFIED_SCORE, result.adjustedScore(), TOL);
        assertTrue(result.isWifiUsable());
        assertFalse(result.shouldCheckNud());
        assertFalse(result.shouldTriggerScan());
        assertFalse(result.shouldBlockBssid());
    }


    @Test
    public void getUpdatedScore_timeGapTooLarge_returnMaxScore() {
        when(mMockHelper.isTimeStampGapTooLarge(
                any(WifiUsabilityStatsEntry.class), any(WifiUsabilityStatsEntry.class)))
                .thenReturn(true);

        assertEquals(WIFI_MAX_SCORE, mScorer.getUpdatedScore(true,
                getUsabilityStats(TIME_STAMP_MS)), TOL);
    }

    @Test
    public void getUpdatedScore_notSameBssidAndFreq_returnMaxScore() {
        when(mMockHelper.isTimeStampGapTooLarge(
                any(WifiUsabilityStatsEntry.class), any(WifiUsabilityStatsEntry.class)))
                .thenReturn(true);

        assertEquals(WIFI_MAX_SCORE,
                mScorer.getUpdatedScore(false, getUsabilityStats(TIME_STAMP_MS)), TOL);
    }

    @Test
    public void getUpdatedScore_sameBssidAndFreq_mBufferNotCleared() {
        assertTrue(mScorer.mBuffer.isEmpty());

        mScorer.getUpdatedScore(true, getUsabilityStats(TIME_STAMP_MS));
        mScorer.getUpdatedScore(true, getUsabilityStats(TIME_STAMP_MS));

        assertEquals(2, mScorer.mBuffer.size());
    }

    @Test
    public void getUpdatedScore_notSameBssidAndFreq_mBufferCleared() {
        assertTrue(mScorer.mBuffer.isEmpty());

        mScorer.getUpdatedScore(false, getUsabilityStats(TIME_STAMP_MS));
        mScorer.getUpdatedScore(false, getUsabilityStats(TIME_STAMP_MS));

        assertEquals(1, mScorer.mBuffer.size());
    }

    @Test
    public void getUpdatedScore_nullClassifier_returnUnclassifiedScore() {
        when(mMockFactory.getClassifier(anyInt())).thenReturn(null);

        assertEquals(Constants.UNCLASSIFIED_SCORE,
                mScorer.getUpdatedScore(true, getUsabilityStats(TIME_STAMP_MS)), TOL);
    }

    @Test
    public void adjustScore_avoidScoreThresholdValue() throws Exception {
        double score1 = mScorer.adjustScore(60, true, -70, TEST_THRESHOLD,
                TEST_THRESHOLD_HYSTERESIS, TIME_STAMP_MS + 1);
        double score2 = mScorer.adjustScore(49, true, -70, TEST_THRESHOLD,
                TEST_THRESHOLD_HYSTERESIS, TIME_STAMP_MS + 2);
        double score3 = mScorer.adjustScore(49, true, -70, TEST_THRESHOLD,
                TEST_THRESHOLD_HYSTERESIS, TIME_STAMP_MS + 3);
        double score4 = mScorer.adjustScore(50, true, -70, TEST_THRESHOLD,
                TEST_THRESHOLD_HYSTERESIS, TIME_STAMP_MS + 4);
        double score5 = mScorer.adjustScore(60, true, -70, TEST_THRESHOLD,
                TEST_THRESHOLD_HYSTERESIS, TIME_STAMP_MS + 5);
        double score6 = mScorer.adjustScore(50, true, -70, TEST_THRESHOLD,
                TEST_THRESHOLD_HYSTERESIS, TIME_STAMP_MS + 6);
        double score7 = mScorer.adjustScore(49, true, -70, TEST_THRESHOLD,
                TEST_THRESHOLD_HYSTERESIS, TIME_STAMP_MS + 7);
        double score8 = mScorer.adjustScore(60, true, -70, TEST_THRESHOLD,
                TEST_THRESHOLD_HYSTERESIS, TIME_STAMP_MS + 8);
        double score9 = mScorer.adjustScore(50, true, -70, TEST_THRESHOLD,
                TEST_THRESHOLD_HYSTERESIS, TIME_STAMP_MS + 9);
        double score10 = mScorer.adjustScore(50, true, -70, TEST_THRESHOLD,
                TEST_THRESHOLD_HYSTERESIS, TIME_STAMP_MS + 10);
        double score11 = mScorer.adjustScore(49, true, -70, TEST_THRESHOLD,
                TEST_THRESHOLD_HYSTERESIS, TIME_STAMP_MS + 11);
        double score12 = mScorer.adjustScore(60, true, -70, TEST_THRESHOLD,
                TEST_THRESHOLD_HYSTERESIS, TIME_STAMP_MS + 12);
        double score13 = mScorer.adjustScore(50, true, -70, TEST_THRESHOLD,
                TEST_THRESHOLD_HYSTERESIS, TIME_STAMP_MS + 13);
        double score14 = mScorer.adjustScore(52, true, -70, TEST_THRESHOLD,
                TEST_THRESHOLD_HYSTERESIS, TIME_STAMP_MS + 14);

        assertThat(score1).isEqualTo(60);
        assertThat(score2).isEqualTo(49);
        assertThat(score3).isEqualTo(49);
        assertThat(score4).isEqualTo(49);
        assertThat(score5).isEqualTo(60);
        assertThat(score6).isEqualTo(51);
        assertThat(score7).isEqualTo(49);
        assertThat(score8).isEqualTo(60);
        assertThat(score9).isEqualTo(51);
        assertThat(score10).isEqualTo(51);
        assertThat(score11).isEqualTo(49);
        assertThat(score12).isEqualTo(60);
        assertThat(score13).isEqualTo(51);
        assertThat(score14).isEqualTo(52);
    }

    @Test
    public void adjustScore_noStatusChangeIfRssiIsHigh() throws Exception {
        mScorer.adjustScore(60, true, -60, TEST_THRESHOLD, TEST_THRESHOLD_HYSTERESIS,
                TIME_STAMP_MS + 1);
        mScorer.adjustScore(49, true, -60, TEST_THRESHOLD, TEST_THRESHOLD_HYSTERESIS,
                TIME_STAMP_MS + 2);
        mScorer.adjustScore(49, true, -60, TEST_THRESHOLD, TEST_THRESHOLD_HYSTERESIS,
                TIME_STAMP_MS + 3);
        mScorer.adjustScore(49, true, -60, TEST_THRESHOLD, TEST_THRESHOLD_HYSTERESIS,
                TIME_STAMP_MS + 4 + HYSTERESIS_NETWORK_STATUS_CHANGE_MILLIS);

        assertThat(mScorer.mRecommendDefaultNetwork).isTrue();
    }

    @Test
    public void getRecommendDefaultNetwork_trueByDefault() throws Exception {
        assertThat(mScorer.mRecommendDefaultNetwork).isTrue();
    }

    @Test
    public void adjustScore_noStatusChangeIfLessThanHysteresis() throws Exception {
        mScorer.adjustScore(60, true, -70, TEST_THRESHOLD, TEST_THRESHOLD_HYSTERESIS,
                TIME_STAMP_MS + 1);
        mScorer.adjustScore(49, true, -70, TEST_THRESHOLD, TEST_THRESHOLD_HYSTERESIS,
                TIME_STAMP_MS + 2);
        mScorer.adjustScore(49, true, -70, TEST_THRESHOLD, TEST_THRESHOLD_HYSTERESIS,
                TIME_STAMP_MS + 3);
        mScorer.adjustScore(49, true, -70, TEST_THRESHOLD, TEST_THRESHOLD_HYSTERESIS,
                TIME_STAMP_MS + 4 - 100);

        assertThat(mScorer.mRecommendDefaultNetwork).isTrue();
    }

    @Test
    public void adjustScore_statusChangeIfRssiIsLowAndLargeHysteresis() throws Exception {
        mScorer.adjustScore(60, true, -70, TEST_THRESHOLD, TEST_THRESHOLD_HYSTERESIS,
                TIME_STAMP_MS + 1);
        mScorer.adjustScore(49, true, -70, TEST_THRESHOLD, TEST_THRESHOLD_HYSTERESIS,
                TIME_STAMP_MS + 2);
        assertThat(mScorer.mRecommendDefaultNetwork).isTrue();
        mScorer.adjustScore(49, true, -70, TEST_THRESHOLD, TEST_THRESHOLD_HYSTERESIS,
                TIME_STAMP_MS + 3);
        mScorer.adjustScore(49, true, -70, TEST_THRESHOLD, TEST_THRESHOLD_HYSTERESIS,
                TIME_STAMP_MS + 4 + HYSTERESIS_NETWORK_STATUS_CHANGE_MILLIS);

        assertThat(mScorer.mRecommendDefaultNetwork).isFalse();
    }

    @Test
    public void adjustScore_statusChangeIfScoreTrendingDownwards() throws Exception {
        mScorer.adjustScore(60, true, -70, TEST_THRESHOLD, TEST_THRESHOLD_HYSTERESIS,
                TIME_STAMP_MS + 1);
        mScorer.adjustScore(53, true, -70, TEST_THRESHOLD, TEST_THRESHOLD_HYSTERESIS,
                TIME_STAMP_MS + 2);
        mScorer.adjustScore(49, true, -70, TEST_THRESHOLD, TEST_THRESHOLD_HYSTERESIS,
                TIME_STAMP_MS + 3);

        assertThat(mScorer.mRecommendDefaultNetwork).isFalse();
    }

    @Test
    public void adjustScore_statusNotChangeIfRoaming() throws Exception {
        mScorer.adjustScore(60, true, -70, TEST_THRESHOLD, TEST_THRESHOLD_HYSTERESIS,
                TIME_STAMP_MS + 1);
        mScorer.adjustScore(53, true, -70, TEST_THRESHOLD, TEST_THRESHOLD_HYSTERESIS,
                TIME_STAMP_MS + 2);
        mScorer.adjustScore(49, false, -70, TEST_THRESHOLD, TEST_THRESHOLD_HYSTERESIS,
                TIME_STAMP_MS + 3);

        assertThat(mScorer.mRecommendDefaultNetwork).isTrue();
    }

    @Test
    public void adjustScore_noStatusChangeIfRssiIsHighAgain() throws Exception {
        mScorer.adjustScore(60, true, -60, TEST_THRESHOLD, TEST_THRESHOLD_HYSTERESIS,
                TIME_STAMP_MS + 1);
        mScorer.adjustScore(49, true, -70, TEST_THRESHOLD, TEST_THRESHOLD_HYSTERESIS,
                TIME_STAMP_MS + 2);
        mScorer.adjustScore(49, true, -70, TEST_THRESHOLD, TEST_THRESHOLD_HYSTERESIS,
                TIME_STAMP_MS + 3);
        mScorer.adjustScore(49, true, -70, TEST_THRESHOLD, TEST_THRESHOLD_HYSTERESIS,
                TIME_STAMP_MS + 4 + HYSTERESIS_NETWORK_STATUS_CHANGE_MILLIS);
        assertThat(mScorer.mRecommendDefaultNetwork).isFalse();
        mScorer.adjustScore(49, true, -60, TEST_THRESHOLD, TEST_THRESHOLD_HYSTERESIS,
                TIME_STAMP_MS + 5 + HYSTERESIS_NETWORK_STATUS_CHANGE_MILLIS);
        mScorer.adjustScore(49, true, -60, TEST_THRESHOLD, TEST_THRESHOLD_HYSTERESIS,
                TIME_STAMP_MS + 6 + HYSTERESIS_NETWORK_STATUS_CHANGE_MILLIS * 3);

        assertThat(mScorer.mRecommendDefaultNetwork).isFalse();
    }

    @Test
    public void adjustScore_statusChangeIfScoreIsHighAgain() throws Exception {
        mScorer.adjustScore(60, true, -60, TEST_THRESHOLD, TEST_THRESHOLD_HYSTERESIS,
                TIME_STAMP_MS + 1);
        mScorer.adjustScore(49, true, -70, TEST_THRESHOLD, TEST_THRESHOLD_HYSTERESIS,
                TIME_STAMP_MS + 2);
        mScorer.adjustScore(49, true, -70, TEST_THRESHOLD, TEST_THRESHOLD_HYSTERESIS,
                TIME_STAMP_MS + 3);
        mScorer.adjustScore(49, true, -70, TEST_THRESHOLD, TEST_THRESHOLD_HYSTERESIS,
                TIME_STAMP_MS + 4 + HYSTERESIS_NETWORK_STATUS_CHANGE_MILLIS);
        assertThat(mScorer.mRecommendDefaultNetwork).isFalse();
        mScorer.adjustScore(60, true, -70, TEST_THRESHOLD, TEST_THRESHOLD_HYSTERESIS,
                TIME_STAMP_MS + 5 + HYSTERESIS_NETWORK_STATUS_CHANGE_MILLIS);
        mScorer.adjustScore(60, true, -70, TEST_THRESHOLD, TEST_THRESHOLD_HYSTERESIS,
                TIME_STAMP_MS + 6 + HYSTERESIS_NETWORK_STATUS_CHANGE_MILLIS * 3);

        assertThat(mScorer.mRecommendDefaultNetwork).isTrue();
    }

    @Test
    public void adjustScore_noStatusChangeIfLargeHyesteresisButRssiIsHigh() throws Exception {
        mScorer.adjustScore(60, true, -70, TEST_THRESHOLD, TEST_THRESHOLD_HYSTERESIS,
                TIME_STAMP_MS + 1);
        mScorer.adjustScore(49, true, -70, TEST_THRESHOLD, TEST_THRESHOLD_HYSTERESIS,
                TIME_STAMP_MS + 2);
        mScorer.adjustScore(49, true, -70, TEST_THRESHOLD, TEST_THRESHOLD_HYSTERESIS,
                TIME_STAMP_MS + 3);
        mScorer.adjustScore(49, true, -60, TEST_THRESHOLD, TEST_THRESHOLD_HYSTERESIS,
                TIME_STAMP_MS + 4 + HYSTERESIS_NETWORK_STATUS_CHANGE_MILLIS);

        assertThat(mScorer.mRecommendDefaultNetwork).isTrue();
    }

    @Test
    public void adjustScore_statusChangeIfRssiIsLow() throws Exception {
        mScorer.adjustScore(60, true, -70, TEST_THRESHOLD, TEST_THRESHOLD_HYSTERESIS,
                TIME_STAMP_MS + 1);
        mScorer.adjustScore(49, true, -85, TEST_THRESHOLD, TEST_THRESHOLD_HYSTERESIS,
                TIME_STAMP_MS + 2);

        assertThat(mScorer.mRecommendDefaultNetwork).isFalse();
    }

    @Test
    public void getBlockCurrentBssid_falseByDefault() throws Exception {
        assertThat(mScorer.mBlockCurrentBssid).isFalse();
    }

    @Test
    public void adjustScore_blockIfScoreBreachingLongEnoughAndRssiLow() throws Exception {
        mScorer.adjustScore(60, true, -70, TEST_THRESHOLD, TEST_THRESHOLD_HYSTERESIS,
                TIME_STAMP_MS + 1);
        mScorer.adjustScore(49, true, -70, TEST_THRESHOLD, TEST_THRESHOLD_HYSTERESIS,
                TIME_STAMP_MS + 2);
        mScorer.adjustScore(49, true, -70, TEST_THRESHOLD, TEST_THRESHOLD_HYSTERESIS,
                TIME_STAMP_MS + 3);
        mScorer.adjustScore(49, true, -70, TEST_THRESHOLD, TEST_THRESHOLD_HYSTERESIS,
                TIME_STAMP_MS + 4 + MIN_TIME_TO_WAIT_BEFORE_BLOCK_BSSID_MILLIS);

        assertThat(mScorer.mBlockCurrentBssid).isTrue();
    }

    @Test
    public void adjustScore_notBlockIfScoreBreachingShortAndRssiLow() throws Exception {
        mScorer.adjustScore(60, true, -70, TEST_THRESHOLD, TEST_THRESHOLD_HYSTERESIS,
                TIME_STAMP_MS + 1);
        mScorer.adjustScore(49, true, -70, TEST_THRESHOLD, TEST_THRESHOLD_HYSTERESIS,
                TIME_STAMP_MS + 2);
        mScorer.adjustScore(49, true, -70, TEST_THRESHOLD, TEST_THRESHOLD_HYSTERESIS,
                TIME_STAMP_MS + 3);
        mScorer.adjustScore(49, true, -70, TEST_THRESHOLD, TEST_THRESHOLD_HYSTERESIS,
                TIME_STAMP_MS + 4 + MIN_TIME_TO_WAIT_BEFORE_BLOCK_BSSID_MILLIS - 100);

        assertThat(mScorer.mBlockCurrentBssid).isFalse();
    }

    @Test
    public void adjustScore_notBlockIfScoreGoesBack() throws Exception {
        mScorer.adjustScore(60, true, -70, TEST_THRESHOLD, TEST_THRESHOLD_HYSTERESIS,
                TIME_STAMP_MS + 1);
        mScorer.adjustScore(49, true, -70, TEST_THRESHOLD, TEST_THRESHOLD_HYSTERESIS,
                TIME_STAMP_MS + 2);
        mScorer.adjustScore(49, true, -70, TEST_THRESHOLD, TEST_THRESHOLD_HYSTERESIS,
                TIME_STAMP_MS + 3);
        mScorer.adjustScore(60, true, -70, TEST_THRESHOLD, TEST_THRESHOLD_HYSTERESIS,
                TIME_STAMP_MS + 4 + MIN_TIME_TO_WAIT_BEFORE_BLOCK_BSSID_MILLIS);

        assertThat(mScorer.mBlockCurrentBssid).isFalse();
    }

    @Test
    public void adjustScore_notBlockIfRssiHigh() throws Exception {
        mScorer.adjustScore(60, true, -70, TEST_THRESHOLD, TEST_THRESHOLD_HYSTERESIS,
                TIME_STAMP_MS + 1);
        mScorer.adjustScore(49, true, -70, TEST_THRESHOLD, TEST_THRESHOLD_HYSTERESIS,
                TIME_STAMP_MS + 2);
        mScorer.adjustScore(49, true, -70, TEST_THRESHOLD, TEST_THRESHOLD_HYSTERESIS,
                TIME_STAMP_MS + 3);
        mScorer.adjustScore(49, true, -60, TEST_THRESHOLD, TEST_THRESHOLD_HYSTERESIS,
                TIME_STAMP_MS + 4 + MIN_TIME_TO_WAIT_BEFORE_BLOCK_BSSID_MILLIS);

        assertThat(mScorer.mBlockCurrentBssid).isFalse();
    }

    private WifiUsabilityStatsEntry getUsabilityStats(long timeStampMs) {
        return new WifiUsabilityStatsEntry(
                timeStampMs, // long timeStampMillis
                TEST_RSSI, // int rssi
                998, // int linkSpeedMbps
                50, // long totalTxSuccess
                0, // long totalTxRetries
                0, // long totalTxBad
                50, // long totalRxSuccess
                1234567L, // long totalRadioOnTimeMillis
                1000L, // long totalRadioTxTimeMillis
                1000L, // long totalRadioRxTimeMillis
                1000L, // long totalScanTimeMillis
                0, // long totalNanScanTimeMillis
                0, // long totalBackgroundScanTimeMillis
                0, // long totalRoamScanTimeMillis
                0, // long totalPnoScanTimeMillis
                0, // long totalHotspot2ScanTimeMillis
                0, // long totalCcaBusyFreqTimeMillis
                0, // long totalRadioOnFreqTimeMillis
                0, // long totalBeaconRx
                0, // @ProbeStatus int probeStatusSinceLastUpdate
                0, // int probeElapsedTimeSinceLastUpdateMillis
                0, // int probeMcsRateSinceLastUpdate
                100, // int rxLinkSpeedMbps
                0, //int timeSliceDutyCycleInPercent,
                new ContentionTimeStats[0], //ContentionTimeStats[] contentionTimeStats,
                new RateStats[0], //RateStats[] rateStats,
                new RadioStats[0], //RadioStats[] radiostats,
                0, //int channelUtilizationRatio,
                true, // boolean isThroughputSufficient
                true, // boolean isWifiScoringEnabled
                true, // boolean isCellularDataAvailable
                0, // @NetworkType int cellularDataNetworkType
                0, // int cellularSignalStrengthDbm
                0, // int cellularSignalStrengthDb,
                false, // boolean isSameRegisteredCell
                new SparseArray<>(), // SparseArray<LinkStats> linkStats
                0, // int wifiLinkCount
                0, // @WifiManager.MloMode int mloMode
                0, //long txTransmittedBytes,
                0, // long rxTransmittedBytes,
                0, // int labelBadEventCount,
                0, //int wifiFrameworkState,
                0, // int isNetworkCapabilitiesDownstreamSufficient,
                0, //int isNetworkCapabilitiesUpstreamSufficient,
                0, //int isThroughputPredictorDownstreamSufficient,
                0, //int isThroughputPredictorUpstreamSufficient,
                false, // boolean isBluetoothConnected,
                0, // int uwbAdapterState,
                false, // boolean isLowLatencyActivated,
                0, // int maxSupportedTxLinkSpeed,
                0, //int maxSupportedRxLinkSpeed,
                0, // int voipMode,
                0, // int threadDeviceRole,
                0, // int statusDataStall,
                0, //int internalScore,
                0); // int internalScorerType
    }
}
