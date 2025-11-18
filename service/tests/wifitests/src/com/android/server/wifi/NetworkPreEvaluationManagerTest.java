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

package com.android.server.wifi;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.MockitoAnnotations.initMocks;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;


import android.os.Handler;
import android.os.test.TestLooper;

import androidx.test.filters.SmallTest;

import org.mockito.Mock;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.time.Duration;

/**
 * Unit tests for {@link NetworkPreEvaluationManager}.
 */
@SmallTest
public class NetworkPreEvaluationManagerTest extends WifiBaseTest {
    private static final String TEST_PROFILE_KEY = "123";
    private static final long ENABLED_TIMESTAMP_MS = 1000L;

    @Mock Clock mMockClock;
    @Mock WifiDataStall mMockWifiDataStall;
    @Mock NetworkPreEvaluationManager.PreEvaluationResultCallback mMockCallback;
    @Mock WifiGlobals mMockWifiGlobals;

    private TestLooper mTestLooper;
    private Handler mHandler;
    private NetworkPreEvaluationManager mNetworkPreEvaluationManager;

    @Before
    public void setUp() {
        initMocks(this);

        mTestLooper = new TestLooper();
        mHandler = new Handler(mTestLooper.getLooper());
        mNetworkPreEvaluationManager = new NetworkPreEvaluationManager(mMockClock,
                mMockWifiDataStall, mMockWifiGlobals, mHandler);

        when(mMockClock.getElapsedSinceBootMillis()).thenReturn(ENABLED_TIMESTAMP_MS);
    }

    @Test
    public void isPreEvaluationNeeded_returnsFalseWhenGloballyDisabled() {
        when(mMockWifiGlobals.isPreEvaluationEnabled()).thenReturn(false);
        when(mMockWifiDataStall.isCellularDataAvailable()).thenReturn(true);
        mNetworkPreEvaluationManager.setPreEvaluationEnabled(TEST_PROFILE_KEY, true);

        assertFalse(mNetworkPreEvaluationManager.isPreEvaluationNeeded(TEST_PROFILE_KEY,
                false));
    }

    @Test
    public void isPreEvaluationNeeded_returnsFalseWhenNotEnabled() {
        when(mMockWifiDataStall.isCellularDataAvailable()).thenReturn(true);

        assertFalse(mNetworkPreEvaluationManager.isPreEvaluationNeeded(TEST_PROFILE_KEY,
                false));
    }

    @Test
    public void isPreEvaluationNeeded_returnsFalseWhenDisabled() {
        when(mMockWifiDataStall.isCellularDataAvailable()).thenReturn(true);
        mNetworkPreEvaluationManager.setPreEvaluationEnabled(TEST_PROFILE_KEY, false);

        assertFalse(mNetworkPreEvaluationManager.isPreEvaluationNeeded(TEST_PROFILE_KEY,
                false));
    }

    @Test
    public void isPreEvaluationNeeded_returnsFalseWhenCellularDataNotAvailable() {
        when(mMockWifiDataStall.isCellularDataAvailable()).thenReturn(false);
        mNetworkPreEvaluationManager.setPreEvaluationEnabled(TEST_PROFILE_KEY, true);

        assertFalse(mNetworkPreEvaluationManager.isPreEvaluationNeeded(TEST_PROFILE_KEY,
                false));
    }

    @Test
    public void isPreEvaluationNeeded_returnsFalseWhenUserSelected() {
        when(mMockWifiDataStall.isCellularDataAvailable()).thenReturn(true);
        mNetworkPreEvaluationManager.setPreEvaluationEnabled(TEST_PROFILE_KEY, true);

        assertFalse(mNetworkPreEvaluationManager.isPreEvaluationNeeded(TEST_PROFILE_KEY,
                true));
    }

   @Test
    public void isPreEvaluationNeeded_returnsFalseWhenRequestIsTooOld() {
        when(mMockClock.getElapsedSinceBootMillis()).thenReturn(ENABLED_TIMESTAMP_MS);
        when(mMockWifiDataStall.isCellularDataAvailable()).thenReturn(true);
        mNetworkPreEvaluationManager.setPreEvaluationEnabled(TEST_PROFILE_KEY, true);
        when(mMockClock.getElapsedSinceBootMillis()).thenReturn(ENABLED_TIMESTAMP_MS
                + Duration.ofHours(1).toMillis() + 1);

        assertFalse(mNetworkPreEvaluationManager.isPreEvaluationNeeded(TEST_PROFILE_KEY,
                false));
    }

    @Test
    public void isPreEvaluationNeeded_returnsTrueWhenAllConditionsMet() {
        when(mMockWifiGlobals.isPreEvaluationEnabled()).thenReturn(true);
        when(mMockClock.getElapsedSinceBootMillis()).thenReturn(ENABLED_TIMESTAMP_MS);
        when(mMockWifiDataStall.isCellularDataAvailable()).thenReturn(true);
        mNetworkPreEvaluationManager.setPreEvaluationEnabled(TEST_PROFILE_KEY, true);
        when(mMockClock.getElapsedSinceBootMillis()).thenReturn(ENABLED_TIMESTAMP_MS
                + Duration.ofHours(1).toMillis());

        assertTrue(mNetworkPreEvaluationManager.isPreEvaluationNeeded(TEST_PROFILE_KEY,
                false));
    }

    @Test
    public void startPreEvaluation_Timeout() {
        // Start pre-evaluation
        mNetworkPreEvaluationManager.startPreEvaluation(TEST_PROFILE_KEY, mMockCallback);

        // Move the clock forward to trigger the timeout
        mTestLooper.moveTimeForward(NetworkPreEvaluationManager.PRE_EVALUATION_TIMEOUT_MS);
        mTestLooper.dispatchAll();

        // Verify that onFail was called on the callback
        verify(mMockCallback).onFail(TEST_PROFILE_KEY);
        verify(mMockCallback, never()).onPass(anyString());
    }

    @Test
    public void startPreEvaluation_Fail() {
        // Start pre-evaluation
        mNetworkPreEvaluationManager.startPreEvaluation(TEST_PROFILE_KEY, mMockCallback);

        // Stop pre-evaluation with a fail result
        mNetworkPreEvaluationManager.stopPreEvaluation(TEST_PROFILE_KEY, false);

        // Verify that onPass was called on the callback
        verify(mMockCallback).onFail(TEST_PROFILE_KEY);
        verify(mMockCallback, never()).onPass(anyString());

        // Move the clock forward past the timeout to ensure the callback was not called again.
        mTestLooper.moveTimeForward(NetworkPreEvaluationManager.PRE_EVALUATION_TIMEOUT_MS);
        mTestLooper.dispatchAll();
        verifyNoMoreInteractions(mMockCallback);
    }

    @Test
    public void startPreEvaluation_Success() {
        // Start pre-evaluation
        mNetworkPreEvaluationManager.startPreEvaluation(TEST_PROFILE_KEY, mMockCallback);

        // Stop pre-evaluation with a success result
        mNetworkPreEvaluationManager.stopPreEvaluation(TEST_PROFILE_KEY, true);

        // Verify that onPass was called on the callback
        verify(mMockCallback).onPass(TEST_PROFILE_KEY);
        verify(mMockCallback, never()).onFail(anyString());

        // Move the clock forward past the timeout to ensure the callback was not called.
        mTestLooper.moveTimeForward(NetworkPreEvaluationManager.PRE_EVALUATION_TIMEOUT_MS);
        mTestLooper.dispatchAll();
        verifyNoMoreInteractions(mMockCallback);
    }

    @Test
    public void startPreEvaluation_Success_ThenFail() {
        // Start pre-evaluation
        mNetworkPreEvaluationManager.startPreEvaluation(TEST_PROFILE_KEY, mMockCallback);

        // Stop pre-evaluation with a success result
        mNetworkPreEvaluationManager.stopPreEvaluation(TEST_PROFILE_KEY, true);

        // Verify that onPass was called on the callback
        verify(mMockCallback).onPass(TEST_PROFILE_KEY);
        verify(mMockCallback, never()).onFail(anyString());

        // Stop pre-evaluation with a fail result
        mNetworkPreEvaluationManager.stopPreEvaluation(TEST_PROFILE_KEY, false);

        // Verify that nothing was called on the callback
        verifyNoMoreInteractions(mMockCallback);
    }

    @Test
    public void startPreEvaluation_Fail_ThenSuccess() {
        // Start pre-evaluation
        mNetworkPreEvaluationManager.startPreEvaluation(TEST_PROFILE_KEY, mMockCallback);

        // Stop pre-evaluation with a fail result
        mNetworkPreEvaluationManager.stopPreEvaluation(TEST_PROFILE_KEY, false);

        // Verify that onPass was called on the callback
        verify(mMockCallback, never()).onPass(anyString());
        verify(mMockCallback).onFail(TEST_PROFILE_KEY);

        // Stop pre-evaluation with a success result
        mNetworkPreEvaluationManager.stopPreEvaluation(TEST_PROFILE_KEY, true);

        // Verify that nothing was called on the callback
        verifyNoMoreInteractions(mMockCallback);
    }

    @Test
    public void startPreEvaluation_withNullCallback_doesNotCrash() {
        // Start pre-evaluation
        mNetworkPreEvaluationManager.startPreEvaluation(TEST_PROFILE_KEY, null);

        // Stop pre-evaluation with a success result
        mNetworkPreEvaluationManager.stopPreEvaluation(TEST_PROFILE_KEY, true);
    }

    @Test
    public void startPreEvaluation_withNullProfileKey_doesNotCrash() {
        // Start pre-evaluation
        mNetworkPreEvaluationManager.startPreEvaluation(null, mMockCallback);
        mNetworkPreEvaluationManager.stopPreEvaluation(TEST_PROFILE_KEY, true);

        // Verify that nothing was called on the callback
        verifyNoMoreInteractions(mMockCallback);
    }

    @Test
    public void stopPreEvaluation_withNullCallback_doesNotCrash() {
        // Start pre-evaluation
        mNetworkPreEvaluationManager.startPreEvaluation(TEST_PROFILE_KEY, null);

        // Stop pre-evaluation with a success result
        mNetworkPreEvaluationManager.stopPreEvaluation(TEST_PROFILE_KEY, true);
    }

    @Test
    public void stopPreEvaluation_withNullProfileKey_doesNotCrash() {
        // Start pre-evaluation
        mNetworkPreEvaluationManager.startPreEvaluation(TEST_PROFILE_KEY, mMockCallback);
        mNetworkPreEvaluationManager.stopPreEvaluation(null, true);

        // Verify that nothing was called on the callback
        verifyNoMoreInteractions(mMockCallback);
    }
}