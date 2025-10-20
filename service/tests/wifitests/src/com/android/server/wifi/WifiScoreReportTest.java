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

import static com.android.server.wifi.ClientModeImpl.WIFI_WORK_SOURCE;
import static com.android.server.wifi.Clock.INVALID_TIMESTAMP_MS;
import static com.android.server.wifi.ml_connected_scorer.Constants.POLLING_INTERVAL_MS;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;
import static org.mockito.AdditionalAnswers.answerVoid;
import static org.mockito.AdditionalMatchers.not;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.atMost;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.net.MacAddress;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkScore;
import android.net.ip.IpClientManager;
import android.net.wifi.IScoreUpdateObserver;
import android.net.wifi.IWifiConnectedNetworkScorer;
import android.net.wifi.MloLink;
import android.net.wifi.WifiConnectedSessionInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiScanner;
import android.net.wifi.WifiUsabilityStatsEntry;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.test.TestLooper;

import androidx.test.filters.SmallTest;

import com.android.modules.utils.build.SdkLevel;
import com.android.server.wifi.ml_connected_scorer.MlConnectedScorer;
import com.android.server.wifi.proto.WifiStatsLog;
import com.android.wifi.flags.Flags;
import com.android.wifi.resources.R;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatcher;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.verification.VerificationMode;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Unit tests for {@link com.android.server.wifi.WifiScoreReport}.
 */
@RunWith(Parameterized.class)
@SmallTest
public class WifiScoreReportTest extends WifiBaseTest {
    class FakeClock extends Clock {
        long mWallClockMillis = 1500000000000L;
        long mElapsedSinceBootMillis = 0L;
        int mStepMillis = 1001;

        @Override
        public long getWallClockMillis() {
            mWallClockMillis += mStepMillis;
            return mWallClockMillis;
        }

        @Override
        public long getElapsedSinceBootMillis() {
            return mElapsedSinceBootMillis;
        }
    }

    private static final int TEST_LOW_CONNECTED_SCORE_SCAN_PERIOD_SECONDS = 60;
    private static final int TEST_NETWORK_ID = 860370;
    private static final int TEST_SESSION_ID = 8603703; // last digit is a check digit
    private static final String TEST_IFACE_NAME = "wlan0";
    public static final String TEST_BSSID = "00:00:00:00:00:00";
    public static final boolean TEST_USER_SELECTED = true;
    public static final int TEST_NETWORK_SWITCH_DIALOG_DISABLED_MS = 300_000;
    private static final int TEST_UID = 435546654;
    private static final String EXTERNAL_SCORER_PKG_NAME = "com.google.android.carrier.carrierwifi";
    private static final String DRY_RUN_SCORER_PKG_NAME = "com.example.xxx";
    private static final int TEST_RSSI = -67;
    private static final int TEST_SCORE = 55;
    private static final int ADJUSTED_SCORE = 50;
    private static final long TEST_WALL_CLOCK_MILLIS_1 = 12345678L;
    private static final long TEST_WALL_CLOCK_MILLIS_2 = 22345678L;

    FakeClock mClock;
    @Mock Clock mMockClock;
    WifiScoreReport mWifiScoreReport;
    WifiScoreReport mWifiScoreReportWithMockHelper;
    ExtendedWifiInfo mWifiInfo;
    ScoringParams mScoringParams;
    @Mock WifiNetworkAgent mNetworkAgent;
    @Mock WifiNetworkAgent mMockNetworkAgent;
    WifiThreadRunner mWifiThreadRunner;
    @Mock Context mContext;
    @Mock PackageManager mMockPackageManager;
    @Mock Resources mResources;
    @Mock WifiMetrics mWifiMetrics;
    @Mock PrintWriter mPrintWriter;
    @Mock IBinder mAppBinder;
    @Mock IWifiConnectedNetworkScorer mWifiConnectedNetworkScorer;
    @Mock WifiNative mWifiNative;
    @Mock WifiBlocklistMonitor mWifiBlocklistMonitor;
    @Mock Network mNetwork;
    @Mock Network mMockNetwork;
    @Mock WifiScoreCard mWifiScoreCard;
    @Mock WifiScoreCard.PerNetwork mPerNetwork;
    @Mock DeviceConfigFacade mDeviceConfigFacade;
    @Mock AdaptiveConnectivityEnabledSettingObserver mAdaptiveConnectivityEnabledSettingObserver;
    @Mock ExternalScoreUpdateObserverProxy mExternalScoreUpdateObserverProxy;
    @Mock WifiSettingsStore mWifiSettingsStore;
    @Mock WifiGlobals mWifiGlobals;
    @Mock ActiveModeWarden mActiveModeWarden;
    @Mock WifiConnectivityManager mWifiConnectivityManager;
    @Mock WifiConfigManager mWifiConfigManager;
    @Mock VelocityBasedConnectedScorer mMockVelocityScorer;
    @Mock ConnectedScorerHelper mMockConnectedScorerHelper;
    @Mock IpClientManager mMockIpClientManager;
    @Mock WifiUsabilityStatsEntry mMockWifiUsabilityStatsEntry;
    @Mock MlConnectedScorer mMockMlConnectedScorer;
    @Captor ArgumentCaptor<WifiManager.ScoreUpdateObserver> mExternalScoreUpdateObserverCbCaptor;
    private TestLooper mLooper;

    public class WifiConnectedNetworkScorerImpl extends IWifiConnectedNetworkScorer.Stub {
        public int mSessionId = -1;

        @Override
        public void onStart(WifiConnectedSessionInfo sessionInfo) {
            mSessionId = sessionInfo.getSessionId();
        }
        @Override
        public void onStop(int sessionId) {
        }
        @Override
        public void onSetScoreUpdateObserver(IScoreUpdateObserver observerImpl) {
        }
        @Override
        public void onNetworkSwitchAccepted(
                int sessionId, int targetNetworkId, String targetBssid) {
        }
        @Override
        public void onNetworkSwitchRejected(
                int sessionId, int targetNetworkId, String targetBssid) {
        }
    }

    @Parameterized.Parameter(0)
    public boolean mIsPrimary;

    @Parameters(name = "{0}")
    public static List<Boolean> isPrimaryValueList() {
        if (SdkLevel.isAtLeastT()) {
            return Arrays.asList(true, false);
        }
        return Arrays.asList(true);
    }

    /**
     * Sets up resource values for testing
     *
     * See frameworks/base/core/res/res/values/config.xml
     */
    private void setUpResources(Resources resources) {
        when(resources.getInteger(
                R.integer.config_wifi_framework_wifi_score_bad_rssi_threshold_5GHz))
            .thenReturn(-82);
        when(resources.getInteger(
                R.integer.config_wifi_framework_wifi_score_entry_rssi_threshold_5GHz))
            .thenReturn(-77);
        when(resources.getInteger(
                R.integer.config_wifi_framework_wifi_score_low_rssi_threshold_5GHz))
            .thenReturn(-70);
        when(resources.getInteger(
                R.integer.config_wifi_framework_wifi_score_good_rssi_threshold_5GHz))
            .thenReturn(-57);
        when(resources.getInteger(
                R.integer.config_wifi_framework_wifi_score_bad_rssi_threshold_24GHz))
            .thenReturn(-85);
        when(resources.getInteger(
                R.integer.config_wifi_framework_wifi_score_entry_rssi_threshold_24GHz))
            .thenReturn(-80);
        when(resources.getInteger(
                R.integer.config_wifi_framework_wifi_score_low_rssi_threshold_24GHz))
            .thenReturn(-73);
        when(resources.getInteger(
                R.integer.config_wifi_framework_wifi_score_good_rssi_threshold_24GHz))
            .thenReturn(-60);
        when(resources.getInteger(
                R.integer.config_wifiFrameworkScoreBadRssiThreshold6ghz))
            .thenReturn(-82);
        when(resources.getInteger(
                R.integer.config_wifiFrameworkScoreEntryRssiThreshold6ghz))
            .thenReturn(-77);
        when(resources.getInteger(
                R.integer.config_wifiFrameworkScoreLowRssiThreshold6ghz))
            .thenReturn(-70);
        when(resources.getInteger(
                R.integer.config_wifiFrameworkScoreGoodRssiThreshold6ghz))
            .thenReturn(-57);
        when(resources.getInteger(
                R.integer.config_wifiFrameworkMinPacketPerSecondHighTraffic))
            .thenReturn(100);
        when(resources.getBoolean(
                R.bool.config_wifiMinConfirmationDurationSendNetworkScoreEnabled))
            .thenReturn(false);
    }

    /**
     * Sets up for unit test
     */
    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        setUpResources(mResources);
        mWifiInfo = new ExtendedWifiInfo(mWifiGlobals, TEST_IFACE_NAME);
        mWifiInfo.setFrequency(2412);
        mWifiInfo.setBSSID(TEST_BSSID);
        mLooper = new TestLooper();
        when(mActiveModeWarden.canRequestSecondaryTransientClientModeManager()).thenReturn(true);
        when(mWifiGlobals.getWifiLowConnectedScoreThresholdToTriggerScanForMbb()).thenReturn(
                getTransitionScore());
        when(mWifiGlobals.getWifiLowConnectedScoreScanPeriodSeconds()).thenReturn(
                TEST_LOW_CONNECTED_SCORE_SCAN_PERIOD_SECONDS);
        when(mWifiGlobals.getPollRssiIntervalMillis()).thenReturn((int) POLLING_INTERVAL_MS);
        when(mContext.getResources()).thenReturn(mResources);
        when(mResources.getInteger(
                R.integer.config_wifiNetworkSwitchDialogDisabledMsWhenMarkedUsable))
                .thenReturn(TEST_NETWORK_SWITCH_DIALOG_DISABLED_MS);
        when(mContext.getPackageManager()).thenReturn(mMockPackageManager);
        when(mMockPackageManager.getPackagesForUid(anyInt()))
                .thenReturn(new String[]{EXTERNAL_SCORER_PKG_NAME});
        when(mDeviceConfigFacade.getDryRunScorerPkgName()).thenReturn(DRY_RUN_SCORER_PKG_NAME);
        when(mNetwork.getNetId()).thenReturn(0);
        when(mNetworkAgent.getNetwork()).thenReturn(mNetwork);
        when(mNetworkAgent.getCurrentNetworkCapabilities()).thenReturn(
                new NetworkCapabilities.Builder()
                        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                        .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                        .build());

        when(mMockNetworkAgent.getNetwork()).thenReturn(mMockNetwork);
        when(mMockNetworkAgent.getCurrentNetworkCapabilities()).thenReturn(
            new NetworkCapabilities.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .build());

        mClock = new FakeClock();
        mScoringParams = new ScoringParams();
        mWifiThreadRunner = new WifiThreadRunner(new Handler(mLooper.getLooper()));
        when(mAdaptiveConnectivityEnabledSettingObserver.get()).thenReturn(true);

        /*
         * This mWifiScoreReport is used for the legacy tests which test the code logic in both
         * WifiScoreReport and ConnectedScorerHelper.
         */
        mWifiScoreReport = new WifiScoreReport(mScoringParams, mClock, mWifiMetrics, mWifiInfo,
                mWifiNative, mWifiBlocklistMonitor, mWifiThreadRunner, mWifiScoreCard,
                mDeviceConfigFacade, mContext,
                mAdaptiveConnectivityEnabledSettingObserver, TEST_IFACE_NAME,
                mExternalScoreUpdateObserverProxy, mWifiSettingsStore,
                mWifiGlobals, mActiveModeWarden, mWifiConnectivityManager, mWifiConfigManager,
                new ConnectedScorerHelper(mScoringParams, mWifiGlobals, mWifiConnectivityManager),
                mMockMlConnectedScorer);
        mWifiScoreReport.onRoleChanged(mIsPrimary ? ActiveModeManager.ROLE_CLIENT_PRIMARY
                : ActiveModeManager.ROLE_CLIENT_SECONDARY_LONG_LIVED);
        mWifiScoreReport.setNetworkAgent(mNetworkAgent);

        when(mMockClock.getWallClockMillis()).thenReturn(TEST_WALL_CLOCK_MILLIS_1);
        /* This mWifiScoreReportWithMockHelper is used for new tests which only test the code logic
         * in WifiScoreReport.
         */
        mWifiScoreReportWithMockHelper = new WifiScoreReport(mScoringParams, mMockClock,
            mWifiMetrics,
            mWifiInfo,
            mWifiNative, mWifiBlocklistMonitor, mWifiThreadRunner, mWifiScoreCard,
            mDeviceConfigFacade, mContext,
            mAdaptiveConnectivityEnabledSettingObserver, TEST_IFACE_NAME,
            mExternalScoreUpdateObserverProxy, mWifiSettingsStore,
            mWifiGlobals, mActiveModeWarden, mWifiConnectivityManager, mWifiConfigManager,
            mMockConnectedScorerHelper, mMockMlConnectedScorer);
        mWifiScoreReportWithMockHelper.mVelocityBasedConnectedScorer = mMockVelocityScorer;
        mWifiScoreReportWithMockHelper.setNetworkAgent(mMockNetworkAgent);
        mWifiScoreReportWithMockHelper.setIpClientManager(mMockIpClientManager);
        mWifiScoreReportWithMockHelper.onRoleChanged(mIsPrimary
                ? ActiveModeManager.ROLE_CLIENT_PRIMARY
                : ActiveModeManager.ROLE_CLIENT_SECONDARY_LONG_LIVED);

        when(mDeviceConfigFacade.getMinConfirmationDurationSendLowScoreMs()).thenReturn(
                DeviceConfigFacade.DEFAULT_MIN_CONFIRMATION_DURATION_SEND_LOW_SCORE_MS);
        when(mDeviceConfigFacade.getRssiThresholdNotSendLowScoreToCsDbm()).thenReturn(
                DeviceConfigFacade.DEFAULT_RSSI_THRESHOLD_NOT_SEND_LOW_SCORE_TO_CS_DBM);
        when(mWifiSettingsStore.isWifiScoringEnabled()).thenReturn(true);
        when(mPerNetwork.getTxLinkBandwidthKbps()).thenReturn(40_000);
        when(mPerNetwork.getRxLinkBandwidthKbps()).thenReturn(50_000);
        when(mWifiScoreCard.lookupNetwork(any())).thenReturn(mPerNetwork);
    }

    /**
     * Cleans up after test
     */
    @After
    public void tearDown() throws Exception {
        mResources = null;
        mWifiScoreReport = null;
        mWifiMetrics = null;
    }

    /**
     * Assert a certain score was sent. Works on all SDK levels.
     * @param score expected score
     * @param mode times(n), never(), atLeastOnce(), etc.
     */
    private void verifySentNetworkScore(int score, VerificationMode mode) {
        verify(mNetworkAgent, mode).sendNetworkScore(argThat(
                // note that a lambda doesn't work here, will cause a crash due to missing
                // class `NetworkScore` on R even though this code path is never reached on R.
                // Maybe lambdas are eagerly loaded by the classloader, while inner classes
                // aren't?
                new ArgumentMatcher<NetworkScore>() {
                    @Override
                    public boolean matches(NetworkScore networkScore) {
                        return networkScore.getLegacyInt() == score;
                    }
                }));
    }

    private void verifySentNetworkScore(int score) {
        verifySentNetworkScore(score, times(1));
    }

    private void verifySentAnyNetworkScore(VerificationMode mode) {
        verify(mNetworkAgent, mode).sendNetworkScore(any(NetworkScore.class));
    }

    private void verifySentAnyNetworkScore() {
        verifySentAnyNetworkScore(times(1));
    }

    private int getTransitionScore() {
        return mIsPrimary ? ConnectedScorer.WIFI_TRANSITION_SCORE
                : ConnectedScorer.WIFI_SECONDARY_TRANSITION_SCORE;
    }

    private int getMaxScore() {
        return mIsPrimary ? ConnectedScorer.WIFI_MAX_SCORE
                : ConnectedScorer.WIFI_SECONDARY_MAX_SCORE;
    }

    /**
     * Test for score reporting
     *
     * The score should be sent to both NetworkAgent and WifiMetrics
     */
    @Test
    public void calculateAndReportScoreSucceeds() throws Exception {
        assumeTrue(SdkLevel.isAtLeastS());
        ConnectedScoreResult scoreResult = ConnectedScoreResult.builder()
                .setScore(TEST_SCORE)
                .setAdjustedScore(ADJUSTED_SCORE)
                .setIsWifiUsable(true)
                .build();
        when(mMockVelocityScorer.generateScoreResult(any(), any(), anyLong(), anyBoolean()))
                .thenReturn(scoreResult);
        mWifiScoreReport.mVelocityBasedConnectedScorer = mMockVelocityScorer;
        // initially called once
        verifySentAnyNetworkScore();

        mWifiInfo.setRssi(-77);
        assertEquals(WifiStatsLog.SCORER_PREDICTION_RESULT_REPORTED__WIFI_PREDICTED_USABILITY_STATE__WIFI_USABILITY_PREDICTED_NONE,
                mWifiScoreReport.getAospScorerPredictionStatusForEvaluation());
        assertEquals(WifiStatsLog.SCORER_PREDICTION_RESULT_REPORTED__WIFI_PREDICTED_USABILITY_STATE__WIFI_USABILITY_PREDICTED_NONE,
                mWifiScoreReport.getExternalScorerPredictionStatusForEvaluation());
        mWifiScoreReport.calculateAndReportScore(mMockWifiUsabilityStatsEntry);

        verify(mMockWifiUsabilityStatsEntry).setInternalScore(not(eq(-1)));
        // called again after calculateAndReportScore()
        verifySentAnyNetworkScore(times(2));
        verify(mWifiMetrics).incrementWifiScoreCount(eq(TEST_IFACE_NAME), anyInt());
        assertEquals(WifiStatsLog.SCORER_PREDICTION_RESULT_REPORTED__WIFI_PREDICTED_USABILITY_STATE__WIFI_USABILITY_PREDICTED_USABLE,
                mWifiScoreReport.getAospScorerPredictionStatusForEvaluation());
        assertEquals(WifiStatsLog.SCORER_PREDICTION_RESULT_REPORTED__WIFI_PREDICTED_USABILITY_STATE__WIFI_USABILITY_PREDICTED_NONE,
                mWifiScoreReport.getExternalScorerPredictionStatusForEvaluation());
        assertEquals(ADJUSTED_SCORE, mWifiScoreReport.mLegacyIntScore);
    }

    @Test
    public void calculateAndReportScore_mlInternalScorerAndPrimary() {
        assumeTrue("Skipping test because feature flag is disabled", Flags.mlScorerInWifiFw());
        assumeTrue(mIsPrimary);
        mWifiInfo.setRssi(-77);
        when(mWifiGlobals.getInternalScorerType()).thenReturn(SCORER_TYPE_ML);
        ConnectedScoreResult scoreResult = ConnectedScoreResult.builder()
                .setScore(TEST_SCORE)
                .setAdjustedScore(ADJUSTED_SCORE)
                .setIsWifiUsable(true)
                .build();
        when(mMockMlConnectedScorer.generateScoreResult(any(), any(), anyLong(), anyBoolean()))
                .thenReturn(scoreResult);

        mWifiScoreReportWithMockHelper.calculateAndReportScore(mMockWifiUsabilityStatsEntry);
        verify(mMockWifiUsabilityStatsEntry).setInternalScore(eq(ADJUSTED_SCORE));
        verify(mMockWifiUsabilityStatsEntry).setInternalScorerType(eq(SCORER_TYPE_ML));
        assertEquals(SCORER_TYPE_ML, mWifiScoreReportWithMockHelper.getLastInternalScorerType());
    }

    @Test
    public void calculateAndReportScore_mlInternalScorerAndPrimaryUnmatchedPollingInterval() {
        assumeTrue("Skipping test because feature flag is disabled", Flags.mlScorerInWifiFw());
        assumeTrue(mIsPrimary);
        mWifiInfo.setRssi(-77);
        when(mWifiGlobals.getInternalScorerType()).thenReturn(SCORER_TYPE_ML);
        when(mWifiGlobals.getPollRssiIntervalMillis()).thenReturn((int) (POLLING_INTERVAL_MS - 1));
        ConnectedScoreResult scoreResult = ConnectedScoreResult.builder()
                .setScore(TEST_SCORE)
                .setAdjustedScore(ADJUSTED_SCORE)
                .setIsWifiUsable(true)
                .build();
        when(mMockVelocityScorer.generateScoreResult(any(), any(), anyLong(), anyBoolean()))
                .thenReturn(scoreResult);

        mWifiScoreReportWithMockHelper.calculateAndReportScore(mMockWifiUsabilityStatsEntry);

        verify(mMockWifiUsabilityStatsEntry).setInternalScore(eq(ADJUSTED_SCORE));
        verify(mMockWifiUsabilityStatsEntry).setInternalScorerType(eq(SCORER_TYPE_VELOCITY));
        assertEquals(SCORER_TYPE_VELOCITY,
                mWifiScoreReportWithMockHelper.getLastInternalScorerType());
    }

    @Test
    public void calculateAndReportScore_mlInternalScorerAndSecondary() {
        assumeTrue("Skipping test because feature flag is disabled", Flags.mlScorerInWifiFw());
        assumeFalse(mIsPrimary);
        mWifiInfo.setRssi(-77);
        when(mWifiGlobals.getInternalScorerType()).thenReturn(SCORER_TYPE_ML);
        ConnectedScoreResult scoreResult = ConnectedScoreResult.builder()
                .setScore(TEST_SCORE)
                .setAdjustedScore(ADJUSTED_SCORE)
                .setIsWifiUsable(true)
                .build();
        when(mMockMlConnectedScorer.generateScoreResult(any(), any(), anyLong(), anyBoolean()))
                .thenReturn(scoreResult);

        mWifiScoreReportWithMockHelper.calculateAndReportScore(mMockWifiUsabilityStatsEntry);

        verify(mMockWifiUsabilityStatsEntry, never()).setInternalScore(anyInt());
        verify(mMockWifiUsabilityStatsEntry, never()).setInternalScorerType(anyInt());
        assertEquals(SCORER_TYPE_INVALID,
                mWifiScoreReportWithMockHelper.getLastInternalScorerType());
    }

    @Test
    public void calculateAndReportScore_mlInternalScorerWithFlagDisabled() {
        assumeFalse("Skipping test because feature flag is enabled", Flags.mlScorerInWifiFw());
        mWifiInfo.setRssi(-77);
        when(mWifiGlobals.getInternalScorerType()).thenReturn(SCORER_TYPE_ML);
        ConnectedScoreResult scoreResult = ConnectedScoreResult.builder()
                .setScore(TEST_SCORE)
                .setAdjustedScore(ADJUSTED_SCORE)
                .setIsWifiUsable(true)
                .build();
        when(mMockVelocityScorer.generateScoreResult(any(), any(), anyLong(), anyBoolean()))
                .thenReturn(scoreResult);

        mWifiScoreReportWithMockHelper.calculateAndReportScore(mMockWifiUsabilityStatsEntry);
        verify(mMockWifiUsabilityStatsEntry).setInternalScore(eq(ADJUSTED_SCORE));
        verify(mMockWifiUsabilityStatsEntry).setInternalScorerType(eq(SCORER_TYPE_VELOCITY));
    }

    @Test
    public void calculateAndReportScore_velocityInternalScorerAndPrimary() {
        assumeTrue(mIsPrimary);
        mWifiInfo.setRssi(-77);
        when(mWifiGlobals.getInternalScorerType()).thenReturn(SCORER_TYPE_VELOCITY);
        ConnectedScoreResult scoreResult = ConnectedScoreResult.builder()
                .setScore(TEST_SCORE)
                .setAdjustedScore(ADJUSTED_SCORE)
                .setIsWifiUsable(true)
                .build();
        when(mMockVelocityScorer.generateScoreResult(any(), any(), anyLong(), anyBoolean()))
                .thenReturn(scoreResult);

        mWifiScoreReportWithMockHelper.calculateAndReportScore(mMockWifiUsabilityStatsEntry);

        verify(mMockWifiUsabilityStatsEntry).setInternalScore(eq(ADJUSTED_SCORE));
        verify(mMockWifiUsabilityStatsEntry).setInternalScorerType(eq(SCORER_TYPE_VELOCITY));
    }

    @Test
    public void calculateAndReportScore_velocityInternalScorerAndSecondary() {
        assumeFalse(mIsPrimary);
        mWifiInfo.setRssi(-77);
        when(mWifiGlobals.getInternalScorerType()).thenReturn(SCORER_TYPE_VELOCITY);
        ConnectedScoreResult scoreResult = ConnectedScoreResult.builder()
                .setScore(TEST_SCORE)
                .setAdjustedScore(ADJUSTED_SCORE)
                .setIsWifiUsable(true)
                .build();
        when(mMockVelocityScorer.generateScoreResult(any(), any(), anyLong(), anyBoolean()))
                .thenReturn(scoreResult);

        mWifiScoreReportWithMockHelper.calculateAndReportScore(mMockWifiUsabilityStatsEntry);

        verify(mMockWifiUsabilityStatsEntry).setInternalScore(eq(ADJUSTED_SCORE));
        verify(mMockWifiUsabilityStatsEntry).setInternalScorerType(eq(SCORER_TYPE_VELOCITY));
    }

    /**
     * Make sure that ConnectedScorerHelper.triggerScanIfNeeded() is called with correct parameters.
     */
    @Test
    public void calculateAndReportScore_triggerScanIfNeeded() throws Exception {
        assumeTrue(SdkLevel.isAtLeastS());
        mWifiInfo.setRssi(TEST_RSSI);
        ConnectedScoreResult scoreResult = ConnectedScoreResult.builder()
                .setScore(TEST_SCORE)
                .setAdjustedScore(ADJUSTED_SCORE)
                .setIsWifiUsable(true)
                .setShouldTriggerScan(true)
                .build();
        when(mMockVelocityScorer.generateScoreResult(any(), any(), anyLong(), anyBoolean()))
                .thenReturn(scoreResult);

        mWifiScoreReportWithMockHelper.calculateAndReportScore(mMockWifiUsabilityStatsEntry);

        verify(mMockWifiUsabilityStatsEntry).setInternalScore(eq(ADJUSTED_SCORE));
        verify(mMockConnectedScorerHelper).triggerScanIfNeeded(eq(INVALID_TIMESTAMP_MS),
                eq(mClock.mElapsedSinceBootMillis), eq(true));
    }

    /**
     * Make sure that ConnectedScorerHelper.triggerScanIfNeeded() is not called if
     * AdaptiveConnectivitiy is disabled.
     */
    @Test
    public void calculateAndReportScore_adaptiveConnectivityDisabled_notTriggerScan()
            throws Exception {
        assumeTrue(SdkLevel.isAtLeastS());
        when(mAdaptiveConnectivityEnabledSettingObserver.get()).thenReturn(false);
        mWifiInfo.setRssi(TEST_RSSI);
        ConnectedScoreResult scoreResult = ConnectedScoreResult.builder()
                .setScore(TEST_SCORE)
                .setAdjustedScore(ADJUSTED_SCORE)
                .setIsWifiUsable(true)
                .setShouldTriggerScan(true)
                .build();
        when(mMockVelocityScorer.generateScoreResult(any(), any(), anyLong(), anyBoolean()))
                .thenReturn(scoreResult);

        mWifiScoreReportWithMockHelper.calculateAndReportScore(mMockWifiUsabilityStatsEntry);

        verify(mMockWifiUsabilityStatsEntry).setInternalScore(eq(ADJUSTED_SCORE));
        verify(mMockConnectedScorerHelper, never())
                .triggerScanIfNeeded(anyLong(), anyLong(), anyBoolean());
    }

    /**
     * Make sure that ConnectedScorerHelper.triggerScanIfNeeded() is not called if scoring is
     * disabled.
     */
    @Test
    public void calculateAndReportScore_scoringDisabled_notTriggerScan()
            throws Exception {
        assumeTrue(SdkLevel.isAtLeastS());
        when(mWifiSettingsStore.isWifiScoringEnabled()).thenReturn(false);
        mWifiInfo.setRssi(TEST_RSSI);
        ConnectedScoreResult scoreResult = ConnectedScoreResult.builder()
                .setScore(TEST_SCORE)
                .setAdjustedScore(ADJUSTED_SCORE)
                .setIsWifiUsable(true)
                .setShouldTriggerScan(true)
                .build();
        when(mMockVelocityScorer.generateScoreResult(any(), any(), anyLong(), anyBoolean()))
                .thenReturn(scoreResult);

        mWifiScoreReportWithMockHelper.calculateAndReportScore(mMockWifiUsabilityStatsEntry);

        verify(mMockWifiUsabilityStatsEntry).setInternalScore(eq(ADJUSTED_SCORE));
        verify(mMockConnectedScorerHelper, never())
                .triggerScanIfNeeded(anyLong(), anyLong(), anyBoolean());
    }

    /**
     * Make sure that ConnectedScorerHelper.triggerScanIfNeeded() is not called if
     * it can't request secondary transient client mode manager.
     */
    @Test
    public void calculateAndReportScore_cannotRequestSecondary_notTriggerScan()
            throws Exception {
        assumeTrue(SdkLevel.isAtLeastS());
        when(mActiveModeWarden.canRequestSecondaryTransientClientModeManager()).thenReturn(false);
        mWifiInfo.setRssi(TEST_RSSI);
        ConnectedScoreResult scoreResult = ConnectedScoreResult.builder()
                .setScore(TEST_SCORE)
                .setAdjustedScore(ADJUSTED_SCORE)
                .setIsWifiUsable(true)
                .setShouldTriggerScan(true)
                .build();
        when(mMockVelocityScorer.generateScoreResult(any(), any(), anyLong(), anyBoolean()))
                .thenReturn(scoreResult);

        mWifiScoreReportWithMockHelper.calculateAndReportScore(mMockWifiUsabilityStatsEntry);

        verify(mMockWifiUsabilityStatsEntry).setInternalScore(eq(ADJUSTED_SCORE));
        verify(mMockConnectedScorerHelper, never())
                .triggerScanIfNeeded(anyLong(), anyLong(), anyBoolean());
    }

    /**
     * Make sure that WifiScoreReport.mLastLowScoreScanTimestampMs is updated after
     * ConnectedScorerHelper.triggerScanIfNeeded() returns true;
     */
    @Test
    public void calculateAndReportScore_triggerScan_timeStampUpdated()
            throws Exception {
        assumeTrue(SdkLevel.isAtLeastS());
        when(mMockConnectedScorerHelper.triggerScanIfNeeded(anyLong(), anyLong(), anyBoolean()))
                .thenReturn(true);
        ConnectedScoreResult scoreResult = ConnectedScoreResult.builder()
                .setScore(TEST_SCORE)
                .setAdjustedScore(ADJUSTED_SCORE)
                .setIsWifiUsable(true)
                .setShouldTriggerScan(true)
                .build();
        when(mMockVelocityScorer.generateScoreResult(any(), any(), anyLong(), anyBoolean()))
                .thenReturn(scoreResult);
        mWifiInfo.setRssi(TEST_RSSI);
        assertNotEquals(mClock.mElapsedSinceBootMillis,
                mWifiScoreReportWithMockHelper.mLastLowScoreScanTimestampMs);

        mWifiScoreReportWithMockHelper.calculateAndReportScore(mMockWifiUsabilityStatsEntry);

        assertEquals(mClock.mElapsedSinceBootMillis,
                mWifiScoreReportWithMockHelper.mLastLowScoreScanTimestampMs);
    }

    /**
     * Make sure that WifiScoreReport.mLastLowScoreScanTimestampMs is not updated after
     * ConnectedScorerHelper.triggerScanIfNeeded() returns false;
     */
    @Test
    public void calculateAndReportScore_notTriggerScan_timeStampNotUpdated()
            throws Exception {
        assumeTrue(SdkLevel.isAtLeastS());
        when(mMockConnectedScorerHelper.triggerScanIfNeeded(anyLong(), anyLong(), anyBoolean()))
                .thenReturn(false);
        ConnectedScoreResult scoreResult = ConnectedScoreResult.builder()
                .setScore(TEST_SCORE)
                .setAdjustedScore(ADJUSTED_SCORE)
                .setIsWifiUsable(true)
                .setShouldTriggerScan(true)
                .build();
        when(mMockVelocityScorer.generateScoreResult(any(), any(), anyLong(), anyBoolean()))
                .thenReturn(scoreResult);
        mWifiInfo.setRssi(TEST_RSSI);
        assertNotEquals(mClock.mElapsedSinceBootMillis,
                mWifiScoreReportWithMockHelper.mLastLowScoreScanTimestampMs);

        mWifiScoreReportWithMockHelper.calculateAndReportScore(mMockWifiUsabilityStatsEntry);

        assertNotEquals(mClock.mElapsedSinceBootMillis,
                mWifiScoreReportWithMockHelper.mLastLowScoreScanTimestampMs);
    }

    @Test
    public void mbbNetworkForceKeepUp() throws Exception {
        assumeTrue(SdkLevel.isAtLeastS());
        reset(mNetworkAgent);

        ArgumentCaptor<NetworkScore> networkScoreCaptor =
                ArgumentCaptor.forClass(NetworkScore.class);

        // start as SECONDARY_TRANSIENT
        mWifiScoreReport.onRoleChanged(ActiveModeManager.ROLE_CLIENT_SECONDARY_TRANSIENT);

        verify(mNetworkAgent).sendNetworkScore(networkScoreCaptor.capture());
        {
            NetworkScore networkScore = networkScoreCaptor.getValue();
            assertNotEquals(WifiScoreReport.LINGERING_SCORE, networkScore.getLegacyInt());
            assertFalse(networkScore.isExiting());
            assertFalse(networkScore.isTransportPrimary());
            // force keep up network
            assertEquals(NetworkScore.KEEP_CONNECTED_FOR_HANDOVER,
                    networkScore.getKeepConnectedReason());
        }

        // network validated, becomes primary
        mWifiScoreReport.onRoleChanged(ActiveModeManager.ROLE_CLIENT_PRIMARY);
        verify(mNetworkAgent, times(2)).sendNetworkScore(networkScoreCaptor.capture());
        {
            NetworkScore networkScore = networkScoreCaptor.getValue();
            assertNotEquals(WifiScoreReport.LINGERING_SCORE, networkScore.getLegacyInt());
            assertFalse(networkScore.isExiting());
            assertTrue(networkScore.isTransportPrimary());
            // no longer need to force keep up network
            assertEquals(NetworkScore.KEEP_CONNECTED_NONE, networkScore.getKeepConnectedReason());
        }
    }

    @Test
    public void calculateAndReportScoreWhileLingering_sendLingeringScore() throws Exception {
        assumeTrue(SdkLevel.isAtLeastS());
        mWifiScoreReport.enableVerboseLogging(true);
        reset(mNetworkAgent);

        ArgumentCaptor<NetworkScore> networkScoreCaptor =
                ArgumentCaptor.forClass(NetworkScore.class);

        // start as primary
        mWifiScoreReport.onRoleChanged(ActiveModeManager.ROLE_CLIENT_PRIMARY);

        verify(mNetworkAgent).sendNetworkScore(networkScoreCaptor.capture());
        {
            NetworkScore networkScore = networkScoreCaptor.getValue();
            assertNotEquals(WifiScoreReport.LINGERING_SCORE, networkScore.getLegacyInt());
            assertFalse(networkScore.isExiting());
            assertTrue(networkScore.isTransportPrimary());
            assertEquals(NetworkScore.KEEP_CONNECTED_NONE, networkScore.getKeepConnectedReason());
        }

        // then, role changed to SECONDARY_TRANSIENT and started lingering
        mWifiScoreReport.onRoleChanged(ActiveModeManager.ROLE_CLIENT_SECONDARY_TRANSIENT);
        mWifiScoreReport.setShouldReduceNetworkScore(true);
        // upon lingering, immediately send LINGERING_SCORE
        // capture most recent invocation
        verify(mNetworkAgent, times(3)).sendNetworkScore(networkScoreCaptor.capture());
        {
            NetworkScore networkScore = networkScoreCaptor.getValue();
            assertEquals(WifiScoreReport.LINGERING_SCORE, networkScore.getLegacyInt());
            assertTrue(networkScore.isExiting());
            assertFalse(networkScore.isTransportPrimary());
            assertEquals(NetworkScore.KEEP_CONNECTED_NONE, networkScore.getKeepConnectedReason());
        }

        mWifiInfo.setRssi(-77);
        mWifiScoreReport.calculateAndReportScore(mMockWifiUsabilityStatsEntry);
        // score not sent again while lingering
        verify(mNetworkAgent, times(3)).sendNetworkScore(any());

        // disable lingering
        mWifiScoreReport.onRoleChanged(ActiveModeManager.ROLE_CLIENT_PRIMARY);
        mWifiScoreReport.setShouldReduceNetworkScore(false);
        // report score again
        mWifiInfo.setRssi(-60);
        mWifiScoreReport.calculateAndReportScore(mMockWifiUsabilityStatsEntry);
        // Some non-lingering score is sent
        // capture most recent invocation
        verify(mNetworkAgent, times(6)).sendNetworkScore(networkScoreCaptor.capture());
        {
            NetworkScore networkScore = networkScoreCaptor.getValue();
            assertNotEquals(WifiScoreReport.LINGERING_SCORE, networkScore.getLegacyInt());
            assertFalse(networkScore.isExiting());
            assertTrue(networkScore.isTransportPrimary());
            assertEquals(NetworkScore.KEEP_CONNECTED_NONE, networkScore.getKeepConnectedReason());
        }
    }

    @Test
    public void testExternalScorerWhileLingering_sendLingeringScore() throws Exception {
        assumeTrue(SdkLevel.isAtLeastS());
        mWifiScoreReport.onRoleChanged(ActiveModeManager.ROLE_CLIENT_SECONDARY_LONG_LIVED);

        // Register Client for verification.
        mWifiScoreReport.setWifiConnectedNetworkScorer(mAppBinder, mWifiConnectedNetworkScorer,
                TEST_UID);
        verify(mExternalScoreUpdateObserverProxy).registerCallback(
                mExternalScoreUpdateObserverCbCaptor.capture());
        when(mNetwork.getNetId()).thenReturn(TEST_NETWORK_ID);
        mWifiScoreReport.startConnectedNetworkScorer(TEST_NETWORK_ID, TEST_USER_SELECTED);
        verify(mWifiConnectedNetworkScorer).onStart(
                argThat(sessionInfo -> sessionInfo.getSessionId() == TEST_SESSION_ID
                        && sessionInfo.isUserSelected() == TEST_USER_SELECTED));

        reset(mNetworkAgent);

        ArgumentCaptor<NetworkScore> networkScoreCaptor =
                ArgumentCaptor.forClass(NetworkScore.class);
        mWifiScoreReport.setShouldReduceNetworkScore(true);
        // upon lingering, immediately send LINGERING_SCORE
        // capture most recent invocation
        verify(mNetworkAgent).sendNetworkScore(networkScoreCaptor.capture());
        {
            NetworkScore networkScore = networkScoreCaptor.getValue();
            assertEquals(WifiScoreReport.LINGERING_SCORE, networkScore.getLegacyInt());
            assertTrue(networkScore.isExiting());
            assertFalse(networkScore.isTransportPrimary());
        }
        // upon lingering, send session end to client.
        verify(mWifiConnectedNetworkScorer).onStop(TEST_SESSION_ID);

        // send score after session has ended
        mExternalScoreUpdateObserverCbCaptor.getValue().notifyStatusUpdate(TEST_SESSION_ID, false);
        mLooper.dispatchAll();
        // score not sent since session ended
        verify(mNetworkAgent, never()).sendNetworkScore(argThat(
                new ArgumentMatcher<NetworkScore>() {
                    @Override
                    public boolean matches(NetworkScore ns) {
                        return ns.getLegacyInt() == 49 && ns.isExiting() && ns.isTransportPrimary();
                    }
                }));
        assertTrue(mWifiInfo.isUsable());
    }

    /**
     * Test for no score report if rssi is invalid
     *
     * The score should be sent to neither the NetworkAgent nor the
     * WifiMetrics
     */
    @Test
    public void calculateAndReportScoreDoesNotReportWhenRssiIsNotValid() throws Exception {
        assumeTrue(SdkLevel.isAtLeastS());
        // initially called once
        verifySentAnyNetworkScore();

        mWifiInfo.setRssi(WifiInfo.INVALID_RSSI);

        mWifiScoreReport.calculateAndReportScore(mMockWifiUsabilityStatsEntry);

        // still only called once
        verifySentAnyNetworkScore();
        verify(mWifiMetrics, never()).incrementWifiScoreCount(any(), anyInt());
    }

    /**
     * Test for operation with null NetworkAgent
     *
     * Expect to not die, and to calculate the score and report to metrics.
     */
    @Test
    public void networkAgentMayBeNull() throws Exception {
        assumeTrue(SdkLevel.isAtLeastS());
        mWifiInfo.setRssi(-33);
        mWifiScoreReport.enableVerboseLogging(true);
        mWifiScoreReport.setNetworkAgent(null);
        mWifiScoreReport.calculateAndReportScore(mMockWifiUsabilityStatsEntry);
        verify(mWifiMetrics).incrementWifiScoreCount(eq(TEST_IFACE_NAME), anyInt());
    }

    /**
     * Exercise the rates with low RSSI
     *
     * The setup has a low (not bad) RSSI, and data movement (txSuccessRate) above
     * the threshold.
     *
     * Expect a score above threshold.
     */
    @Test
    public void allowLowRssiIfDataIsMoving() throws Exception {
        assumeTrue(SdkLevel.isAtLeastS());
        assumeTrue(mIsPrimary);
        mWifiInfo.setRssi(-80);
        mWifiInfo.setLinkSpeed(6); // Mbps
        mWifiInfo.setSuccessfulTxPacketsPerSecond(5.1); // proportional to pps
        mWifiInfo.setSuccessfulRxPacketsPerSecond(5.1);
        for (int i = 0; i < 10; i++) {
            mWifiScoreReport.calculateAndReportScore(mMockWifiUsabilityStatsEntry);
        }
        int score = mWifiInfo.getScore();
        assertTrue(score > getTransitionScore());
        verify(mWifiConnectivityManager, never()).forceConnectivityScan(any());
    }

    /**
     * Bad RSSI without data moving should allow handoff
     *
     * The setup has a bad RSSI, and the txSuccessRate is below threshold; several
     * scoring iterations are performed.
     *
     * Expect the score to drop below the handoff threshold.
     */
    @Test
    public void giveUpOnBadRssiWhenDataIsNotMoving() throws Exception {
        assumeTrue(SdkLevel.isAtLeastS());
        // initially called once
        verifySentAnyNetworkScore();

        mWifiInfo.setRssi(-100);
        mWifiInfo.setLinkSpeed(6); // Mbps
        mWifiInfo.setFrequency(5220);
        mWifiScoreReport.enableVerboseLogging(true);
        mWifiInfo.setSuccessfulTxPacketsPerSecond(0.1);
        mWifiInfo.setSuccessfulRxPacketsPerSecond(0.1);
        for (int i = 0; i < 10; i++) {
            mWifiScoreReport.calculateAndReportScore(mMockWifiUsabilityStatsEntry);
        }
        int score = mWifiInfo.getScore();
        assertTrue(score < getTransitionScore());
        ArgumentCaptor<NetworkScore> scoreCaptor = ArgumentCaptor.forClass(NetworkScore.class);
        verify(mNetworkAgent, times(2)).sendNetworkScore(scoreCaptor.capture());
        NetworkScore ns = scoreCaptor.getValue();
        assertEquals(score, ns.getLegacyInt());
        assertTrue(ns.isExiting());
        if (mIsPrimary) assertTrue(ns.isTransportPrimary());
    }

    @Test
    public void testAospScoreBreachTriggersScan() throws Exception {
        assumeTrue(SdkLevel.isAtLeastS());
        // initially called once
        verifySentAnyNetworkScore();

        // First has good RSSI but low traffic
        mWifiInfo.setRssi(-33);
        mWifiInfo.setLinkSpeed(6); // Mbps
        mWifiInfo.setFrequency(5220);
        mWifiScoreReport.enableVerboseLogging(true);
        mWifiInfo.setSuccessfulTxPacketsPerSecond(0.1);
        mWifiInfo.setSuccessfulRxPacketsPerSecond(0.1);
        for (int i = 0; i < 10; i++) {
            mWifiScoreReport.calculateAndReportScore(mMockWifiUsabilityStatsEntry);
        }
        assertTrue("wifi score should be high",
                mWifiInfo.getScore() >= getTransitionScore());
        verify(mWifiConnectivityManager, never()).forceConnectivityScan(any());

        // Then simulate low RSSI and traffic, and verify score becomes low and a scan is triggered.
        mWifiInfo.setRssi(-100);
        for (int i = 0; i < 10; i++) {
            mWifiScoreReport.calculateAndReportScore(mMockWifiUsabilityStatsEntry);
        }
        assertTrue("wifi score should be low",
                mWifiInfo.getScore() < getTransitionScore());
        verify(mWifiConnectivityManager).forceConnectivityScan(WIFI_WORK_SOURCE);

        // move time forward slightly, not passing enough time to do another scan.
        mClock.mElapsedSinceBootMillis = (long) TEST_LOW_CONNECTED_SCORE_SCAN_PERIOD_SECONDS * 1000;

        // trigger low score again and verify no additional scan yet.
        mWifiScoreReport.calculateAndReportScore(mMockWifiUsabilityStatsEntry);
        verify(mWifiConnectivityManager).forceConnectivityScan(WIFI_WORK_SOURCE);

        // move time past the threshold to trigger scan again and verify the scan happens
        mClock.mElapsedSinceBootMillis =
                (long) TEST_LOW_CONNECTED_SCORE_SCAN_PERIOD_SECONDS * 1000 + 1;
        mWifiScoreReport.calculateAndReportScore(mMockWifiUsabilityStatsEntry);
        verify(mWifiConnectivityManager, times(2)).forceConnectivityScan(WIFI_WORK_SOURCE);
    }

    /**
     * When the AOSP score is low but external scorer is registered, scan will not be triggered.
     */
    @Test
    public void testAospScoreBreachNoScanWhenExternalScorerEnabled() throws Exception {
        assumeTrue(SdkLevel.isAtLeastS());
        // register external scorer
        mWifiScoreReport.setWifiConnectedNetworkScorer(mAppBinder, mWifiConnectedNetworkScorer,
                TEST_UID);
        verify(mWifiGlobals).setUsingExternalScorer(true);
        mWifiInfo.setFrequency(5220);
        for (int rssi = -60; rssi >= -83; rssi -= 1) {
            mWifiInfo.setRssi(rssi);
            mWifiScoreReport.calculateAndReportScore(mMockWifiUsabilityStatsEntry);
        }
        int score = mWifiInfo.getScore();
        assertTrue(score < getTransitionScore());
        verify(mWifiConnectivityManager, never()).forceConnectivityScan(any());

        // when the external score is cleared, scan should be triggered.
        mWifiScoreReport.clearWifiConnectedNetworkScorer();
        mWifiScoreReport.calculateAndReportScore(mMockWifiUsabilityStatsEntry);
        assertTrue(score < getTransitionScore());
        verify(mWifiConnectivityManager).forceConnectivityScan(any());
        verify(mWifiGlobals).setUsingExternalScorer(false);
    }

    /**
     * When the score ramps down to the exit theshold, let go.
     */
    @Test
    public void giveUpOnBadRssiAggressively() throws Exception {
        assumeTrue(SdkLevel.isAtLeastS());
        String oops = "giveUpOnBadRssiAggressively";
        mWifiInfo.setFrequency(5220);
        for (int rssi = -60; rssi >= -83; rssi -= 1) {
            mWifiInfo.setRssi(rssi);
            oops += " " + mClock.mWallClockMillis + "," + rssi;
            mWifiScoreReport.calculateAndReportScore(mMockWifiUsabilityStatsEntry);
            oops += ":" + mWifiInfo.getScore();
        }
        int score = mWifiInfo.getScore();
        verifySentNetworkScore(score);
        assertTrue(oops, score < getTransitionScore());
    }

    /**
     * RSSI that falls rapidly but does not cross entry threshold should not cause handoff
     *
     * Expect the score to not drop below the handoff threshold.
     */
    @Test
    public void stayOnIfRssiDoesNotGetBelowEntryThreshold() throws Exception {
        assumeTrue(SdkLevel.isAtLeastS());
        assumeTrue(mIsPrimary);
        String oops = "didNotStickLanding";
        int minScore = 100;
        mWifiInfo.setLinkSpeed(6); // Mbps
        mWifiInfo.setFrequency(5220);
        mWifiScoreReport.enableVerboseLogging(true);
        mWifiInfo.setSuccessfulTxPacketsPerSecond(0.1);
        mWifiInfo.setSuccessfulRxPacketsPerSecond(0.1);
        assertTrue(mScoringParams.update("rssi5=-83:-80:-66:-55"));
        for (int r = -30; r >= -100; r -= 1) {
            int rssi = Math.max(r, -80);
            mWifiInfo.setRssi(rssi);
            oops += " " + mClock.mWallClockMillis + "," + rssi;
            mWifiScoreReport.calculateAndReportScore(mMockWifiUsabilityStatsEntry);
            oops += ":" + mWifiInfo.getScore();
            if (mWifiInfo.getScore() < minScore) minScore = mWifiInfo.getScore();
        }
        assertTrue(oops, minScore > getTransitionScore());
    }

    /**
     * Don't breach if the success rates are great
     *
     * Ramp the RSSI down, but maintain a high packet throughput
     *
     * Expect score to stay above above threshold.
     */
    @Test
    public void allowTerribleRssiIfDataIsMovingWell() throws Exception {
        assumeTrue(SdkLevel.isAtLeastS());
        assumeTrue(mIsPrimary);
        mWifiInfo.setSuccessfulTxPacketsPerSecond(
                mScoringParams.getYippeeSkippyPacketsPerSecond() + 0.1);
        mWifiInfo.setSuccessfulRxPacketsPerSecond(
                mScoringParams.getYippeeSkippyPacketsPerSecond() + 0.1);
        assertTrue(mWifiInfo.getSuccessfulTxPacketsPerSecond() > 10);
        mWifiInfo.setFrequency(5220);
        for (int r = -30; r >= -120; r -= 2) {
            mWifiInfo.setRssi(r);
            mWifiScoreReport.calculateAndReportScore(mMockWifiUsabilityStatsEntry);
            assertTrue(mWifiInfo.getScore() > getTransitionScore());
        }
        // If the throughput dips, we should let go
        mWifiInfo.setSuccessfulRxPacketsPerSecond(
                mScoringParams.getYippeeSkippyPacketsPerSecond() - 0.1);
        mWifiScoreReport.calculateAndReportScore(mMockWifiUsabilityStatsEntry);
        assertTrue(mWifiInfo.getScore() < getTransitionScore());
        // And even if throughput improves again, once we have decided to let go, disregard
        // the good rates.
        mWifiInfo.setSuccessfulRxPacketsPerSecond(
                mScoringParams.getYippeeSkippyPacketsPerSecond() + 0.1);
        mWifiScoreReport.calculateAndReportScore(mMockWifiUsabilityStatsEntry);
        assertTrue(mWifiInfo.getScore() < getTransitionScore());
    }

    /**
     * This setup causes some reports to be generated when println
     * methods are called, to check for "concurrent" modification
     * errors.
     */
    private void setupToGenerateAReportWhenPrintlnIsCalled() {
        int[] counter = new int[1];
        doAnswer(answerVoid((String line) -> {
            if (counter[0]++ < 3) {
                mWifiScoreReport.calculateAndReportScore(mMockWifiUsabilityStatsEntry);
            }
        })).when(mPrintWriter).println(anyString());
    }

    /**
     * Test data logging
     */
    @Test
    public void testDataLogging() throws Exception {
        assumeTrue(SdkLevel.isAtLeastS());
        for (int i = 0; i < 10; i++) {
            mWifiInfo.setRssi(-65 + i);
            mWifiInfo.setLinkSpeed(300);
            mWifiInfo.setFrequency(5220);
            mWifiInfo.setSuccessfulTxPacketsPerSecond(0.1 + i);
            mWifiInfo.setRetriedTxPacketsRate(0.2 + i);
            mWifiInfo.setLostTxPacketsPerSecond(0.01 * i);
            mWifiInfo.setSuccessfulRxPacketsPerSecond(0.3 + i);
            mWifiScoreReport.calculateAndReportScore(mMockWifiUsabilityStatsEntry);
        }
        setupToGenerateAReportWhenPrintlnIsCalled();
        mWifiScoreReport.dump(null, mPrintWriter, null);
        verify(mPrintWriter, times(13)).println(anyString());
    }

    /** Test data logging with MLO */
    @Test
    public void testDataLoggingMlo() throws Exception {
        assumeTrue(SdkLevel.isAtLeastS());
        List<MloLink> mloLinks = new ArrayList<>();
        MloLink link1 = new MloLink();
        link1.setBand(WifiScanner.WIFI_BAND_24_GHZ);
        link1.setChannel(6);
        link1.setApMacAddress(MacAddress.fromString("01:02:03:04:05:06"));
        link1.setTxLinkSpeedMbps(300);
        link1.setState(MloLink.MLO_LINK_STATE_ACTIVE);
        link1.setLinkId(1);

        MloLink link2 = new MloLink();
        link2.setBand(WifiScanner.WIFI_BAND_5_GHZ);
        link2.setChannel(44);
        link2.setApMacAddress(MacAddress.fromString("01:02:03:04:05:07"));
        link2.setTxLinkSpeedMbps(600);
        link2.setLinkId(2);
        link2.setState(MloLink.MLO_LINK_STATE_ACTIVE);

        mloLinks.add(link1);
        mloLinks.add(link2);

        when(mWifiMetrics.getTotalBeaconRxCount(1)).thenReturn(500L);
        when(mWifiMetrics.getLinkUsageState(1))
                .thenReturn(WifiUsabilityStatsEntry.LINK_STATE_IN_USE);
        when(mWifiMetrics.getTotalBeaconRxCount(2)).thenReturn(0L);
        when(mWifiMetrics.getLinkUsageState(2))
                .thenReturn(WifiUsabilityStatsEntry.LINK_STATE_NOT_IN_USE);

        mWifiInfo.setAffiliatedMloLinks(mloLinks);

        for (int i = 0; i < 10; i++) {
            mWifiInfo.setRssi(-65 + i);
            mWifiInfo.setLinkSpeed(300);
            mWifiInfo.setFrequency(5220);
            mWifiInfo.setSuccessfulTxPacketsPerSecond(0.1 + i);
            mWifiInfo.setRetriedTxPacketsRate(0.2 + i);
            mWifiInfo.setLostTxPacketsPerSecond(0.01 * i);
            mWifiInfo.setSuccessfulRxPacketsPerSecond(0.3 + i);
            link1.setRssi(-65 + i);
            link1.setRetriedTxPacketsRate(0.2 + i);
            link1.setLostTxPacketsPerSecond(0.01 * i);
            link1.setSuccessfulRxPacketsPerSecond(0.3 + i);
            link2.setRssi(-65 + i);
            link2.setRetriedTxPacketsRate(0.2 + i);
            link2.setLostTxPacketsPerSecond(0.01 * i);
            link2.setSuccessfulRxPacketsPerSecond(0.3 + i);
            mWifiScoreReport.calculateAndReportScore(mMockWifiUsabilityStatsEntry);
        }
        setupToGenerateAReportWhenPrintlnIsCalled();
        mWifiScoreReport.dump(null, mPrintWriter, null);
        verify(mPrintWriter, times(13)).println(anyString());
    }

    /**
     *  Test data logging limit
     *  <p>
     *  Check that only a bounded amount of data is collected for dumpsys report
     */
    @Test
    public void testDataLoggingLimit() throws Exception {
        assumeTrue(SdkLevel.isAtLeastS());
        for (int i = 0; i < 3620; i++) {
            mWifiInfo.setRssi(-65 + i % 20);
            mWifiInfo.setLinkSpeed(300);
            mWifiInfo.setFrequency(5220);
            mWifiInfo.setSuccessfulTxPacketsPerSecond(0.1 + i % 100);
            mWifiInfo.setRetriedTxPacketsRate(0.2 + i % 100);
            mWifiInfo.setLostTxPacketsPerSecond(0.0001 * i);
            mWifiInfo.setSuccessfulRxPacketsPerSecond(0.3 + i % 200);
            mWifiScoreReport.calculateAndReportScore(mMockWifiUsabilityStatsEntry);
        }
        mWifiScoreReport.dump(null, mPrintWriter, null);
        verify(mPrintWriter, atMost(3603)).println(anyString());
    }

    /**
     * Test for resetting the internal timer which is used to keep staying at
     * below transition score for a certain period of time.
     */
    @Test
    public void stayAtBelowTransitionScoreWithReset() throws Exception {
        assumeTrue(SdkLevel.isAtLeastS());
        mWifiScoreReport.enableVerboseLogging(true);
        mWifiInfo.setFrequency(5220);

        assertEquals(WifiStatsLog.SCORER_PREDICTION_RESULT_REPORTED__WIFI_PREDICTED_USABILITY_STATE__WIFI_USABILITY_PREDICTED_NONE,
                mWifiScoreReport.getAospScorerPredictionStatusForEvaluation());
        // Reduce RSSI value to fall below the transition score
        for (int rssi = -60; rssi >= -83; rssi -= 1) {
            mWifiInfo.setRssi(rssi);
            mWifiScoreReport.calculateAndReportScore(mMockWifiUsabilityStatsEntry);
        }
        assertTrue(mWifiInfo.getScore() < getTransitionScore());
        assertEquals(WifiStatsLog.SCORER_PREDICTION_RESULT_REPORTED__WIFI_PREDICTED_USABILITY_STATE__WIFI_USABILITY_PREDICTED_UNUSABLE,
                mWifiScoreReport.getAospScorerPredictionStatusForEvaluation());

        // Then, set high RSSI value to exceed the transition score
        mWifiInfo.setRssi(-50);
        // Reset the internal timer so that no need to wait for 9 seconds
        mWifiScoreReport.reset();
        assertEquals(WifiStatsLog.SCORER_PREDICTION_RESULT_REPORTED__WIFI_PREDICTED_USABILITY_STATE__WIFI_USABILITY_PREDICTED_NONE,
                mWifiScoreReport.getAospScorerPredictionStatusForEvaluation());

        mWifiScoreReport.calculateAndReportScore(mMockWifiUsabilityStatsEntry);
        assertTrue(mWifiInfo.getScore() > getTransitionScore());
        assertEquals(WifiStatsLog.SCORER_PREDICTION_RESULT_REPORTED__WIFI_PREDICTED_USABILITY_STATE__WIFI_USABILITY_PREDICTED_USABLE,
                mWifiScoreReport.getAospScorerPredictionStatusForEvaluation());
    }

    /**
     * Verify that client gets ScoreChangeCallback object when client sets its scorer.
     */
    @Test
    public void testClientNotification() throws RemoteException {
        assumeTrue(SdkLevel.isAtLeastS());
        // Register Client for verification.
        mWifiScoreReport.setWifiConnectedNetworkScorer(mAppBinder, mWifiConnectedNetworkScorer,
                TEST_UID);
        // Client should get ScoreChangeCallback.
        verify(mExternalScoreUpdateObserverProxy).registerCallback(any());
    }

    /**
     * Verify that clear client should be handled.
     */
    @Test
    public void testClearClient() throws RemoteException {
        assumeTrue(SdkLevel.isAtLeastS());
        // Register Client for verification.
        mWifiScoreReport.setWifiConnectedNetworkScorer(mAppBinder, mWifiConnectedNetworkScorer,
                TEST_UID);
        verify(mExternalScoreUpdateObserverProxy).registerCallback(any());
        mWifiScoreReport.clearWifiConnectedNetworkScorer();
        verify(mAppBinder).unlinkToDeath(any(), anyInt());
        verify(mExternalScoreUpdateObserverProxy).unregisterCallback(any());

        mWifiScoreReport.startConnectedNetworkScorer(10, true);
        verify(mWifiConnectedNetworkScorer, never()).onStart(any());
    }

    /**
     * Verify that WifiScoreReport adds for death notification on setting client.
     */
    @Test
    public void testAddsForBinderDeathOnSetClient() throws Exception {
        assumeTrue(SdkLevel.isAtLeastS());
        mWifiScoreReport.setWifiConnectedNetworkScorer(mAppBinder, mWifiConnectedNetworkScorer,
                TEST_UID);
        verify(mExternalScoreUpdateObserverProxy).registerCallback(any());
        verify(mAppBinder).linkToDeath(any(IBinder.DeathRecipient.class), anyInt());
    }

    /**
     * Verify that client fails to get message when scorer add failed.
     */
    @Test
    public void testAddsScorerFailureOnLinkToDeath() throws Exception {
        assumeTrue(SdkLevel.isAtLeastS());
        doThrow(new RemoteException())
                .when(mAppBinder).linkToDeath(any(IBinder.DeathRecipient.class), anyInt());
        mWifiScoreReport.setWifiConnectedNetworkScorer(mAppBinder, mWifiConnectedNetworkScorer,
                TEST_UID);
        verify(mExternalScoreUpdateObserverProxy, never()).registerCallback(any());
        verify(mAppBinder).linkToDeath(any(IBinder.DeathRecipient.class), anyInt());

        // Client should not get any message when scorer add failed.
        verify(mWifiConnectedNetworkScorer, never()).onSetScoreUpdateObserver(any());
    }

    /**
     * Verify netId to sessionId conversion.
     */
    @Test
    public void testSessionId() throws Exception {
        assumeTrue(SdkLevel.isAtLeastS());
        assertEquals(-1, WifiScoreReport.sessionIdFromNetId(Integer.MIN_VALUE));
        assertEquals(-1, WifiScoreReport.sessionIdFromNetId(-42));
        assertEquals(-1, WifiScoreReport.sessionIdFromNetId(-1));
        assertEquals(-1, WifiScoreReport.sessionIdFromNetId(0));
        assertEquals(18, WifiScoreReport.sessionIdFromNetId(1));
        assertEquals(3339, WifiScoreReport.sessionIdFromNetId(333));
        assertEquals(TEST_SESSION_ID, WifiScoreReport.sessionIdFromNetId(TEST_NETWORK_ID));
        int dangerOfOverflow = Integer.MAX_VALUE / 10;
        assertEquals(214748364, dangerOfOverflow);
        assertEquals(2147483646, WifiScoreReport.sessionIdFromNetId(dangerOfOverflow));
        assertEquals(8, WifiScoreReport.sessionIdFromNetId(dangerOfOverflow + 1));
        assertEquals(8, WifiScoreReport.sessionIdFromNetId(Integer.MAX_VALUE));
    }

    /**
     * Verify that client gets session ID when onStart() method is called.
     */
    @Test
    public void testClientGetSessionIdOnStart() throws Exception {
        assumeTrue(SdkLevel.isAtLeastS());
        // Register Client for verification.
        mWifiScoreReport.setWifiConnectedNetworkScorer(mAppBinder, mWifiConnectedNetworkScorer,
                TEST_UID);
        verify(mExternalScoreUpdateObserverProxy).registerCallback(any());
        when(mNetwork.getNetId()).thenReturn(TEST_NETWORK_ID);
        mWifiScoreReport.startConnectedNetworkScorer(TEST_NETWORK_ID, TEST_USER_SELECTED);
        verify(mWifiConnectedNetworkScorer).onStart(
                argThat(sessionInfo -> sessionInfo.getSessionId() == TEST_SESSION_ID
                        && sessionInfo.isUserSelected() == TEST_USER_SELECTED));
    }

    /**
     * Verify that onStart is called if there is already an active network when registered.
     */
    @Test
    public void testClientStartOnRegWhileActive() throws Exception {
        assumeTrue(SdkLevel.isAtLeastS());
        when(mNetwork.getNetId()).thenReturn(TEST_NETWORK_ID);
        mWifiScoreReport.startConnectedNetworkScorer(TEST_NETWORK_ID, TEST_USER_SELECTED);
        mWifiScoreReport.setWifiConnectedNetworkScorer(mAppBinder, mWifiConnectedNetworkScorer,
                TEST_UID);
        verify(mExternalScoreUpdateObserverProxy).registerCallback(any());
        verify(mWifiConnectedNetworkScorer).onStart(
                argThat(sessionInfo -> sessionInfo.getSessionId() == TEST_SESSION_ID
                        && sessionInfo.isUserSelected() == TEST_USER_SELECTED));
    }

    /**
     * Verify that client gets session ID when onStop() method is called.
     */
    @Test
    public void testClientGetSessionIdOnStop() throws Exception {
        assumeTrue(SdkLevel.isAtLeastS());
        // Register Client for verification.
        mWifiScoreReport.setWifiConnectedNetworkScorer(mAppBinder, mWifiConnectedNetworkScorer,
                TEST_UID);
        verify(mExternalScoreUpdateObserverProxy).registerCallback(any());
        when(mNetwork.getNetId()).thenReturn(TEST_NETWORK_ID);
        mWifiScoreReport.startConnectedNetworkScorer(TEST_NETWORK_ID, TEST_USER_SELECTED);
        verify(mWifiConnectedNetworkScorer).onStart(
                argThat(sessionInfo -> sessionInfo.getSessionId() == TEST_SESSION_ID
                        && sessionInfo.isUserSelected() == TEST_USER_SELECTED));
        mWifiScoreReport.stopConnectedNetworkScorer();
        verify(mWifiConnectedNetworkScorer).onStop(TEST_SESSION_ID);
        // After the session stops, it should not start again (without a new NetworkAgent)
        mWifiScoreReport.startConnectedNetworkScorer(TEST_NETWORK_ID, TEST_USER_SELECTED);
        verify(mWifiConnectedNetworkScorer).onStart(
                argThat(sessionInfo -> sessionInfo.getSessionId() == TEST_SESSION_ID
                        && sessionInfo.isUserSelected() == TEST_USER_SELECTED));
    }

    /**
     * Verify that only a single Wi-Fi connected network scorer can be registered successfully.
     */
    @Test
    public void verifyOnlyASingleScorerCanBeRegisteredSuccessively() throws Exception {
        assumeTrue(SdkLevel.isAtLeastS());
        WifiConnectedNetworkScorerImpl scorerImpl = new WifiConnectedNetworkScorerImpl();
        assertEquals(true, mWifiScoreReport.setWifiConnectedNetworkScorer(
                mAppBinder, scorerImpl, TEST_UID));
        verify(mExternalScoreUpdateObserverProxy).registerCallback(any());
        assertEquals(false, mWifiScoreReport.setWifiConnectedNetworkScorer(
                mAppBinder, scorerImpl, TEST_UID));
    }

    @Test
    public void frameworkIgnoreTriggerUpdateOfWifiUsabilityStatsForDryRunScorer() throws Exception {
        assumeTrue(SdkLevel.isAtLeastS());
        when(mMockPackageManager.getPackagesForUid(anyInt()))
                .thenReturn(new String[]{DRY_RUN_SCORER_PKG_NAME});
        WifiConnectedNetworkScorerImpl scorerImpl = new WifiConnectedNetworkScorerImpl();
        // Register Client for verification.
        mWifiScoreReport.setWifiConnectedNetworkScorer(mAppBinder, scorerImpl, TEST_UID);
        verify(mExternalScoreUpdateObserverProxy).registerCallback(
                mExternalScoreUpdateObserverCbCaptor.capture());
        when(mNetwork.getNetId()).thenReturn(TEST_NETWORK_ID);
        //mClock.mStepMillis = 0;
        mWifiScoreReport.startConnectedNetworkScorer(TEST_NETWORK_ID, TEST_USER_SELECTED);

        //mClock.mWallClockMillis = 5001;
        mExternalScoreUpdateObserverCbCaptor.getValue()
                .triggerUpdateOfWifiUsabilityStats(scorerImpl.mSessionId);
        mLooper.dispatchAll();
        verify(mWifiNative, never()).getWifiLinkLayerStats(TEST_IFACE_NAME);
        verify(mWifiNative, never()).signalPoll(TEST_IFACE_NAME);
        assertFalse(mWifiScoreReport.isExternalScorerActive());
    }

    /**
     * Verify that WifiScoreReport gets updated score when notifyScoreUpdate() is called by apps.
     */
    @Test
    public void frameworkIgnoreNotifyScoreUpdateFromDryRunScorer() throws Exception {
        assumeTrue(SdkLevel.isAtLeastS());
        assertEquals(ConnectedScorer.WIFI_INITIAL_SCORE, mWifiScoreReport.mLegacyIntScore);
        when(mMockPackageManager.getPackagesForUid(anyInt()))
                .thenReturn(new String[]{DRY_RUN_SCORER_PKG_NAME});
        WifiConnectedNetworkScorerImpl scorerImpl = new WifiConnectedNetworkScorerImpl();
        mWifiScoreReport.setWifiConnectedNetworkScorer(mAppBinder, scorerImpl, TEST_UID);
        verify(mExternalScoreUpdateObserverProxy).registerCallback(
                mExternalScoreUpdateObserverCbCaptor.capture());
        when(mNetwork.getNetId()).thenReturn(TEST_NETWORK_ID);
        mWifiScoreReport.startConnectedNetworkScorer(TEST_NETWORK_ID, TEST_USER_SELECTED);
        assertEquals(TEST_SESSION_ID, scorerImpl.mSessionId);

        mExternalScoreUpdateObserverCbCaptor.getValue().notifyScoreUpdate(
                scorerImpl.mSessionId, ConnectedScorer.WIFI_INITIAL_SCORE - 1);
        mLooper.dispatchAll();

        assertEquals(ConnectedScorer.WIFI_INITIAL_SCORE, mWifiScoreReport.mLegacyIntScore);
        assertFalse(mWifiScoreReport.isExternalScorerActive());
    }

    @Test
    public void frameworkIgnoreNotifyStatusUpdateFromDryRunScorer() throws Exception {
        assumeTrue(SdkLevel.isAtLeastS());
        assertEquals(ConnectedScorer.WIFI_INITIAL_SCORE, mWifiScoreReport.mLegacyIntScore);
        when(mMockPackageManager.getPackagesForUid(anyInt()))
                .thenReturn(new String[]{DRY_RUN_SCORER_PKG_NAME});
        WifiConnectedNetworkScorerImpl scorerImpl = new WifiConnectedNetworkScorerImpl();
        mWifiScoreReport.setWifiConnectedNetworkScorer(mAppBinder, scorerImpl, TEST_UID);
        verify(mExternalScoreUpdateObserverProxy).registerCallback(
                mExternalScoreUpdateObserverCbCaptor.capture());
        when(mNetwork.getNetId()).thenReturn(TEST_NETWORK_ID);
        mWifiScoreReport.startConnectedNetworkScorer(TEST_NETWORK_ID, TEST_USER_SELECTED);
        assertEquals(TEST_SESSION_ID, scorerImpl.mSessionId);

        mExternalScoreUpdateObserverCbCaptor.getValue().notifyStatusUpdate(
                scorerImpl.mSessionId, true);
        mLooper.dispatchAll();
        assertTrue(mWifiInfo.isUsable());
        mExternalScoreUpdateObserverCbCaptor.getValue().notifyStatusUpdate(
                scorerImpl.mSessionId, false);
        mLooper.dispatchAll();
        assertTrue(mWifiInfo.isUsable());
        assertFalse(mWifiScoreReport.isExternalScorerActive());
    }

    /**
     * Verify that WifiScoreReport gets NUD request only once when requestNudOperation() is called
     * by apps.
     */
    @Test
    public void frameworkIgnoreRequestNudOperationForDryRunScorer() throws Exception {
        assumeTrue(SdkLevel.isAtLeastS());
        when(mMockPackageManager.getPackagesForUid(anyInt()))
                .thenReturn(new String[]{DRY_RUN_SCORER_PKG_NAME});
        WifiConnectedNetworkScorerImpl scorerImpl = new WifiConnectedNetworkScorerImpl();
        // Register Client for verification.
        mWifiScoreReportWithMockHelper.setWifiConnectedNetworkScorer(mAppBinder, scorerImpl,
                TEST_UID);
        verify(mExternalScoreUpdateObserverProxy).registerCallback(
                mExternalScoreUpdateObserverCbCaptor.capture());
        when(mNetwork.getNetId()).thenReturn(TEST_NETWORK_ID);
        mClock.mStepMillis = 0;
        mWifiScoreReportWithMockHelper.startConnectedNetworkScorer(TEST_NETWORK_ID,
                TEST_USER_SELECTED);

        mClock.mWallClockMillis = 5001;
        mExternalScoreUpdateObserverCbCaptor.getValue().requestNudOperation(scorerImpl.mSessionId);
        mLooper.dispatchAll();

        verify(mMockConnectedScorerHelper, never())
                .checkNudIfNeeded(any(IpClientManager.class), anyLong(), anyLong());
        assertEquals(0, mWifiScoreReportWithMockHelper.getNudYes());
        assertFalse(mWifiScoreReport.isExternalScorerActive());
    }

    @Test
    public void frameworkIgnoreBlocklistCurrentBssidForDryRunScorer() throws Exception {
        assumeTrue(SdkLevel.isAtLeastS());
        when(mMockPackageManager.getPackagesForUid(anyInt()))
                .thenReturn(new String[]{DRY_RUN_SCORER_PKG_NAME});
        WifiConnectedNetworkScorerImpl scorerImpl = new WifiConnectedNetworkScorerImpl();
        // Register Client for verification.
        mWifiScoreReport.setWifiConnectedNetworkScorer(mAppBinder, scorerImpl, TEST_UID);
        verify(mExternalScoreUpdateObserverProxy).registerCallback(
                mExternalScoreUpdateObserverCbCaptor.capture());
        when(mNetwork.getNetId()).thenReturn(TEST_NETWORK_ID);
        mClock.mStepMillis = 0;
        mWifiScoreReport.startConnectedNetworkScorer(TEST_NETWORK_ID, TEST_USER_SELECTED);

        mClock.mWallClockMillis = 5001;
        mExternalScoreUpdateObserverCbCaptor.getValue().requestNudOperation(scorerImpl.mSessionId);
        mLooper.dispatchAll();
        verify(mWifiBlocklistMonitor, never())
                .handleBssidConnectionFailure(any(), any(), anyInt(), anyInt());
        assertFalse(mWifiScoreReport.isExternalScorerActive());
    }

    /**
     * Verify that WifiScoreReport triggers an update of WifiUsabilityStatsEntry.
     */
    @Test
    public void testFrameworkTriggersUpdateOfWifiUsabilityStats() throws Exception {
        assumeTrue(SdkLevel.isAtLeastS());
        WifiConnectedNetworkScorerImpl scorerImpl = new WifiConnectedNetworkScorerImpl();
        // Register Client for verification.
        assertFalse(mWifiScoreReport.isExternalScorerActive());
        mWifiScoreReport.setWifiConnectedNetworkScorer(mAppBinder, scorerImpl, TEST_UID);
        verify(mExternalScoreUpdateObserverProxy).registerCallback(
                mExternalScoreUpdateObserverCbCaptor.capture());
        assertTrue(mWifiScoreReport.isExternalScorerActive());
        when(mNetwork.getNetId()).thenReturn(TEST_NETWORK_ID);

        WifiSignalPollResults signalPollResults = new WifiSignalPollResults();
        signalPollResults.addEntry(0, -42, 65, 54, 2437);
        when(mWifiNative.signalPoll(TEST_IFACE_NAME)).thenReturn(signalPollResults);

        mWifiScoreReport.startConnectedNetworkScorer(TEST_NETWORK_ID, TEST_USER_SELECTED);

        mExternalScoreUpdateObserverCbCaptor.getValue().triggerUpdateOfWifiUsabilityStats(
                scorerImpl.mSessionId);
        mLooper.dispatchAll();
        verify(mWifiNative).getWifiLinkLayerStats(TEST_IFACE_NAME);
        verify(mWifiNative).signalPoll(TEST_IFACE_NAME);
        assertEquals(-42, mWifiInfo.getRssi());

        // Verify valid RSSI poll is updated to WifiInfo
        signalPollResults.addEntry(0, -55, 65, 54, 2437);
        mExternalScoreUpdateObserverCbCaptor.getValue().triggerUpdateOfWifiUsabilityStats(
                scorerImpl.mSessionId);
        mLooper.dispatchAll();
        assertEquals(-55, mWifiInfo.getRssi());

        // Verify invalid RSSI poll is ignored
        signalPollResults.addEntry(0, -999, 65, 54, 2437);
        mExternalScoreUpdateObserverCbCaptor.getValue().triggerUpdateOfWifiUsabilityStats(
                scorerImpl.mSessionId);
        mLooper.dispatchAll();
        assertEquals(-55, mWifiInfo.getRssi());
    }

    /**
     * Verify BSSID blocklist does not happen when score stays below threshold for less than the
     * minimum duration
     */
    @Test
    public void bssidBlockListDoesnotHappenWhenExitingIsLessThanMinDuration() throws Exception {
        assumeTrue(SdkLevel.isAtLeastS());
        WifiConnectedNetworkScorerImpl scorerImpl = new WifiConnectedNetworkScorerImpl();
        // Register Client for verification.
        mWifiScoreReport.setWifiConnectedNetworkScorer(mAppBinder, scorerImpl, TEST_UID);
        verify(mExternalScoreUpdateObserverProxy).registerCallback(
                mExternalScoreUpdateObserverCbCaptor.capture());
        when(mNetwork.getNetId()).thenReturn(TEST_NETWORK_ID);
        mWifiScoreReport.startConnectedNetworkScorer(TEST_NETWORK_ID, TEST_USER_SELECTED);
        mClock.mStepMillis = 0;

        mClock.mWallClockMillis = 10;
        mExternalScoreUpdateObserverCbCaptor.getValue().notifyScoreUpdate(
                scorerImpl.mSessionId, 49);
        mLooper.dispatchAll();
        mClock.mWallClockMillis = 29009;
        mExternalScoreUpdateObserverCbCaptor.getValue().notifyScoreUpdate(
                scorerImpl.mSessionId, 49);
        mLooper.dispatchAll();
        mWifiScoreReport.stopConnectedNetworkScorer();
        mLooper.dispatchAll();
        verify(mWifiBlocklistMonitor, never()).handleBssidConnectionFailure(any(), any(),
                anyInt(), anyInt());
    }

    /**
     * Verify BSSID blocklist does not happen when there is score flip flop
     */
    @Test
    public void bssidBlockListDoesnotHappenWhenExitingIsReset() throws Exception {
        assumeTrue(SdkLevel.isAtLeastS());
        WifiConnectedNetworkScorerImpl scorerImpl = new WifiConnectedNetworkScorerImpl();
        // Register Client for verification.
        mWifiScoreReport.setWifiConnectedNetworkScorer(mAppBinder, scorerImpl, TEST_UID);
        verify(mExternalScoreUpdateObserverProxy).registerCallback(
                mExternalScoreUpdateObserverCbCaptor.capture());
        when(mNetwork.getNetId()).thenReturn(TEST_NETWORK_ID);
        mWifiScoreReport.startConnectedNetworkScorer(TEST_NETWORK_ID, TEST_USER_SELECTED);
        mClock.mStepMillis = 0;

        mClock.mWallClockMillis = 10;
        mExternalScoreUpdateObserverCbCaptor.getValue().notifyScoreUpdate(
                scorerImpl.mSessionId, 49);
        mLooper.dispatchAll();
        mClock.mWallClockMillis = 15000;
        mExternalScoreUpdateObserverCbCaptor.getValue().notifyScoreUpdate(
                scorerImpl.mSessionId, 51);
        mLooper.dispatchAll();
        mClock.mWallClockMillis = 29011;
        mExternalScoreUpdateObserverCbCaptor.getValue().notifyScoreUpdate(
                scorerImpl.mSessionId, 49);
        mLooper.dispatchAll();
        mWifiScoreReport.stopConnectedNetworkScorer();
        mLooper.dispatchAll();
        verify(mWifiBlocklistMonitor, never()).handleBssidConnectionFailure(any(), any(),
                anyInt(), anyInt());
    }

    /**
     * Verify that the initial score value in WifiInfo is the max when onStart is called.
     */
    @Test
    public void testOnStartInitialScoreInWifiInfoIsMaxScore() throws Exception {
        assumeTrue(SdkLevel.isAtLeastS());
        when(mNetwork.getNetId()).thenReturn(TEST_NETWORK_ID);
        mWifiScoreReport.setWifiConnectedNetworkScorer(mAppBinder, mWifiConnectedNetworkScorer,
                TEST_UID);
        mWifiScoreReport.startConnectedNetworkScorer(TEST_NETWORK_ID, TEST_USER_SELECTED);
        assertEquals(getMaxScore(), mWifiInfo.getScore());
    }

    /**
     * Verify NUD check is not recommended and the score of 51 is sent to connectivity service
     * when adaptive connectivity is disabled for AOSP scorer.
     */
    @Test
    public void verifyScoreIfToggleOffForAospScorer() throws Exception {
        assumeTrue(SdkLevel.isAtLeastS());
        // initially called once
        verifySentAnyNetworkScore();

        mWifiInfo.setFrequency(5220);
        mWifiInfo.setRssi(-85);
        when(mAdaptiveConnectivityEnabledSettingObserver.get()).thenReturn(false);

        mWifiScoreReport.calculateAndReportScore(mMockWifiUsabilityStatsEntry);

        ArgumentCaptor<NetworkScore> scoreCaptor = ArgumentCaptor.forClass(NetworkScore.class);
        verify(mNetworkAgent, times(2)).sendNetworkScore(scoreCaptor.capture());
        NetworkScore ns = scoreCaptor.getValue();
        assertEquals(51, ns.getLegacyInt());
        assertFalse(ns.isExiting());
        if (mIsPrimary) assertTrue(ns.isTransportPrimary());
    }

    @Test
    public void verifyNudCheckIfToggleOffForAospScorer() throws Exception {
        assumeTrue(SdkLevel.isAtLeastS());
        when(mAdaptiveConnectivityEnabledSettingObserver.get()).thenReturn(false);
        ConnectedScoreResult scoreResult = ConnectedScoreResult.builder()
                .setScore(TEST_SCORE)
                .setAdjustedScore(ADJUSTED_SCORE)
                .setIsWifiUsable(true)
                .setShouldCheckNud(true)
                .build();
        when(mMockVelocityScorer.generateScoreResult(any(), any(), anyLong(), anyBoolean()))
                .thenReturn(scoreResult);

        mWifiScoreReportWithMockHelper.calculateAndReportScore(mMockWifiUsabilityStatsEntry);

        verify(mMockConnectedScorerHelper, never())
                .checkNudIfNeeded(any(IpClientManager.class), anyLong(), anyLong());
    }

    /**
     * Verify NUD check is not recommended and the score of 51 is sent to connectivity service
     * when Wifi scoring is disabled.
     */
    @Test
    public void verifyScoreIfScoringDisabledForAospScorer() throws Exception {
        assumeTrue(SdkLevel.isAtLeastS());
        // initially called once
        verifySentAnyNetworkScore();
        mWifiInfo.setFrequency(5220);
        mWifiInfo.setRssi(-85);
        when(mWifiSettingsStore.isWifiScoringEnabled()).thenReturn(false);
        mWifiScoreReport.calculateAndReportScore(mMockWifiUsabilityStatsEntry);

        ArgumentCaptor<NetworkScore> scoreCaptor = ArgumentCaptor.forClass(NetworkScore.class);
        verify(mNetworkAgent, times(2)).sendNetworkScore(scoreCaptor.capture());
        NetworkScore ns = scoreCaptor.getValue();
        assertEquals(51, ns.getLegacyInt());
        assertFalse(ns.isExiting());
        if (mIsPrimary) assertTrue(ns.isTransportPrimary());
    }

    /**
     * Verify NUD check is not recommended and the score of 51 is sent to connectivity service
     * when Wifi scoring is disabled.
     */
    @Test
    public void verifyNudCheckIfScoringDisabledForAospScorer() throws Exception {
        assumeTrue(SdkLevel.isAtLeastS());
        when(mWifiSettingsStore.isWifiScoringEnabled()).thenReturn(false);
        ConnectedScoreResult scoreResult = ConnectedScoreResult.builder()
                .setScore(TEST_SCORE)
                .setAdjustedScore(ADJUSTED_SCORE)
                .setIsWifiUsable(true)
                .setShouldCheckNud(true)
                .build();
        when(mMockVelocityScorer.generateScoreResult(any(), any(), anyLong(), anyBoolean()))
                .thenReturn(scoreResult);

        mWifiScoreReportWithMockHelper.calculateAndReportScore(mMockWifiUsabilityStatsEntry);

        verify(mMockConnectedScorerHelper, never())
                .checkNudIfNeeded(any(IpClientManager.class), anyLong(), anyLong());
    }

    /**
     * Verify that WifiScoreReport gets updated score when notifyStoreUpdate() is called by apps.
     */
    @Test
    public void testFrameworkGetsNotifiedOfUpdatedScore() throws Exception {
        assumeTrue(SdkLevel.isAtLeastS());
        WifiConnectedNetworkScorerImpl scorerImpl = new WifiConnectedNetworkScorerImpl();
        // Register Client for verification.
        mWifiScoreReport.setWifiConnectedNetworkScorer(mAppBinder, scorerImpl, TEST_UID);
        verify(mExternalScoreUpdateObserverProxy).registerCallback(
                mExternalScoreUpdateObserverCbCaptor.capture());
        when(mNetwork.getNetId()).thenReturn(TEST_NETWORK_ID);
        mWifiScoreReport.startConnectedNetworkScorer(TEST_NETWORK_ID, TEST_USER_SELECTED);

        mExternalScoreUpdateObserverCbCaptor.getValue().notifyScoreUpdate(
                scorerImpl.mSessionId, 59);
        mLooper.dispatchAll();
        verify(mWifiMetrics).incrementWifiScoreCount(eq(TEST_IFACE_NAME), anyInt());
    }

    /**
     * Verify that WifiScoreReport gets updated status when notifyStatusUpdate() is called by apps.
     */
    @Test
    public void testFrameworkGetsNotifiedOfUpdatedStatus() throws Exception {
        assumeTrue(SdkLevel.isAtLeastS());
        // initially called once
        verify(mNetworkAgent).sendNetworkScore(any());
        assertTrue(mWifiInfo.isUsable());
        WifiConnectedNetworkScorerImpl scorerImpl = new WifiConnectedNetworkScorerImpl();
        // Register Client for verification.
        mWifiScoreReport.setWifiConnectedNetworkScorer(mAppBinder, scorerImpl, TEST_UID);
        verify(mExternalScoreUpdateObserverProxy).registerCallback(
                mExternalScoreUpdateObserverCbCaptor.capture());
        when(mNetwork.getNetId()).thenReturn(TEST_NETWORK_ID);
        mWifiScoreReport.startConnectedNetworkScorer(TEST_NETWORK_ID, TEST_USER_SELECTED);

        assertEquals(WifiStatsLog.SCORER_PREDICTION_RESULT_REPORTED__WIFI_PREDICTED_USABILITY_STATE__WIFI_USABILITY_PREDICTED_NONE,
                mWifiScoreReport.getExternalScorerPredictionStatusForEvaluation());
        mExternalScoreUpdateObserverCbCaptor.getValue().notifyStatusUpdate(
                scorerImpl.mSessionId, true);
        mLooper.dispatchAll();
        assertEquals(WifiStatsLog.SCORER_PREDICTION_RESULT_REPORTED__WIFI_PREDICTED_USABILITY_STATE__WIFI_USABILITY_PREDICTED_USABLE,
                mWifiScoreReport.getExternalScorerPredictionStatusForEvaluation());

        {
            ArgumentCaptor<NetworkScore> scoreCaptor = ArgumentCaptor.forClass(NetworkScore.class);
            verify(mNetworkAgent, times(2)).sendNetworkScore(scoreCaptor.capture());
            assertTrue(mWifiInfo.isUsable());
            NetworkScore ns = scoreCaptor.getValue();
            assertEquals(60, ns.getLegacyInt());
            assertFalse(ns.isExiting());
            if (mIsPrimary) assertTrue(ns.isTransportPrimary());
        }

        mExternalScoreUpdateObserverCbCaptor.getValue().notifyStatusUpdate(
                scorerImpl.mSessionId, false);
        mLooper.dispatchAll();
        assertEquals(WifiStatsLog.SCORER_PREDICTION_RESULT_REPORTED__WIFI_PREDICTED_USABILITY_STATE__WIFI_USABILITY_PREDICTED_UNUSABLE,
                mWifiScoreReport.getExternalScorerPredictionStatusForEvaluation());

        {
            ArgumentCaptor<NetworkScore> scoreCaptor = ArgumentCaptor.forClass(NetworkScore.class);
            verify(mNetworkAgent, times(3)).sendNetworkScore(scoreCaptor.capture());
            assertFalse(mWifiInfo.isUsable());
            NetworkScore ns = scoreCaptor.getValue();
            assertEquals(60, ns.getLegacyInt());
            assertTrue(ns.isExiting());
            if (mIsPrimary) assertTrue(ns.isTransportPrimary());
        }

        // Not usable -> Usable should disable the network switch dialog for the specified duration.
        mExternalScoreUpdateObserverCbCaptor.getValue().notifyStatusUpdate(
                scorerImpl.mSessionId, true);
        mLooper.dispatchAll();
        assertEquals(WifiStatsLog.SCORER_PREDICTION_RESULT_REPORTED__WIFI_PREDICTED_USABILITY_STATE__WIFI_USABILITY_PREDICTED_USABLE,
                mWifiScoreReport.getExternalScorerPredictionStatusForEvaluation());
        verify(mWifiConnectivityManager).disableNetworkSwitchDialog(
                TEST_NETWORK_SWITCH_DIALOG_DISABLED_MS);
    }

    /**
     * Verify that WifiScoreReport gets NUD request only once when requestNudOperation() is called
     * by apps.
     */
    @Test
    public void testFrameworkGetsNotifiedOfRequestedNudOperation() throws Exception {
        assumeTrue(SdkLevel.isAtLeastS());
        when(mMockConnectedScorerHelper.checkNudIfNeeded(
                any(IpClientManager.class), anyLong(), anyLong())).thenReturn(true);
        WifiConnectedNetworkScorerImpl scorerImpl = new WifiConnectedNetworkScorerImpl();
        // Register Client for verification.
        mWifiScoreReportWithMockHelper.setWifiConnectedNetworkScorer(mAppBinder, scorerImpl,
                TEST_UID);
        verify(mExternalScoreUpdateObserverProxy).registerCallback(
                mExternalScoreUpdateObserverCbCaptor.capture());
        when(mMockNetwork.getNetId()).thenReturn(TEST_NETWORK_ID);
        mWifiScoreReportWithMockHelper.startConnectedNetworkScorer(TEST_NETWORK_ID,
                TEST_USER_SELECTED);

        mExternalScoreUpdateObserverCbCaptor.getValue().requestNudOperation(scorerImpl.mSessionId);
        mLooper.dispatchAll();
        verify(mMockConnectedScorerHelper)
                .checkNudIfNeeded(any(IpClientManager.class), anyLong(), anyLong());
        assertEquals(1, mWifiScoreReportWithMockHelper.getNudYes());
        assertEquals(TEST_WALL_CLOCK_MILLIS_1, mWifiScoreReportWithMockHelper.mLastNudCheckTimeMs);

        when(mMockClock.getWallClockMillis()).thenReturn(TEST_WALL_CLOCK_MILLIS_2);
        when(mMockConnectedScorerHelper.checkNudIfNeeded(
                any(IpClientManager.class), anyLong(), anyLong())).thenReturn(false);
        mExternalScoreUpdateObserverCbCaptor.getValue().requestNudOperation(scorerImpl.mSessionId);
        mLooper.dispatchAll();
        verify(mMockConnectedScorerHelper, times(2))
                .checkNudIfNeeded(any(IpClientManager.class), anyLong(), anyLong());
        assertEquals(2, mWifiScoreReportWithMockHelper.getNudYes());
        assertEquals(TEST_WALL_CLOCK_MILLIS_1, mWifiScoreReportWithMockHelper.mLastNudCheckTimeMs);

    }

    /**
     * Verify that blocklisting happens when blocklistCurrentBssid() is called by apps.
     */
    @Test
    public void testFrameworkGetsBlocklistCurrentBssidOperation() throws Exception {
        assumeTrue(SdkLevel.isAtLeastS());
        WifiConnectedNetworkScorerImpl scorerImpl = new WifiConnectedNetworkScorerImpl();
        // Register Client for verification.
        mWifiScoreReport.setWifiConnectedNetworkScorer(mAppBinder, scorerImpl, TEST_UID);
        verify(mExternalScoreUpdateObserverProxy).registerCallback(
                mExternalScoreUpdateObserverCbCaptor.capture());
        when(mNetwork.getNetId()).thenReturn(TEST_NETWORK_ID);
        mWifiScoreReport.startConnectedNetworkScorer(TEST_NETWORK_ID, TEST_USER_SELECTED);

        mExternalScoreUpdateObserverCbCaptor.getValue().blocklistCurrentBssid(
                scorerImpl.mSessionId);
        mLooper.dispatchAll();
        verify(mWifiBlocklistMonitor).handleBssidConnectionFailure(any(), any(),
                eq(WifiBlocklistMonitor.REASON_FRAMEWORK_DISCONNECT_CONNECTED_SCORE), anyInt());
    }

    @Test
    public void testClientNotNotifiedForLocalOnlyConnection() throws Exception {
        assumeTrue(SdkLevel.isAtLeastS());
        when(mNetworkAgent.getCurrentNetworkCapabilities()).thenReturn(
                new NetworkCapabilities.Builder()
                        // no internet
                        .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                        .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                        .build());
        // Register Client for verification.
        mWifiScoreReport.setWifiConnectedNetworkScorer(mAppBinder, mWifiConnectedNetworkScorer,
                TEST_UID);
        verify(mExternalScoreUpdateObserverProxy).registerCallback(
                mExternalScoreUpdateObserverCbCaptor.capture());
        when(mNetwork.getNetId()).thenReturn(TEST_NETWORK_ID);
        mWifiScoreReport.startConnectedNetworkScorer(TEST_NETWORK_ID, TEST_USER_SELECTED);
        verify(mWifiConnectedNetworkScorer, never()).onStart(any());
    }

    @Test
    public void testClientNotNotifiedForOemPaidConnection() throws Exception {
        assumeTrue(SdkLevel.isAtLeastS());
        when(mNetworkAgent.getCurrentNetworkCapabilities()).thenReturn(
                new NetworkCapabilities.Builder()
                        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                        // oem paid
                        .addCapability(NetworkCapabilities.NET_CAPABILITY_OEM_PAID)
                        .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                        .build());
        // Register Client for verification.
        mWifiScoreReport.setWifiConnectedNetworkScorer(mAppBinder, mWifiConnectedNetworkScorer,
                TEST_UID);
        verify(mExternalScoreUpdateObserverProxy).registerCallback(
                mExternalScoreUpdateObserverCbCaptor.capture());
        when(mNetwork.getNetId()).thenReturn(TEST_NETWORK_ID);
        mWifiScoreReport.startConnectedNetworkScorer(TEST_NETWORK_ID, TEST_USER_SELECTED);
        verify(mWifiConnectedNetworkScorer, never()).onStart(any());
    }

    @Test
    public void testClientNotNotifiedForOemPrivateConnection() throws Exception {
        assumeTrue(SdkLevel.isAtLeastS());
        when(mNetworkAgent.getCurrentNetworkCapabilities()).thenReturn(
                new NetworkCapabilities.Builder()
                        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                        // oem private
                        .addCapability(NetworkCapabilities.NET_CAPABILITY_OEM_PRIVATE)
                        .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                        .build());
        // Register Client for verification.
        mWifiScoreReport.setWifiConnectedNetworkScorer(mAppBinder, mWifiConnectedNetworkScorer,
                TEST_UID);
        verify(mExternalScoreUpdateObserverProxy).registerCallback(
                mExternalScoreUpdateObserverCbCaptor.capture());
        when(mNetwork.getNetId()).thenReturn(TEST_NETWORK_ID);
        mWifiScoreReport.startConnectedNetworkScorer(TEST_NETWORK_ID, TEST_USER_SELECTED);
        verify(mWifiConnectedNetworkScorer, never()).onStart(any());
    }
}
