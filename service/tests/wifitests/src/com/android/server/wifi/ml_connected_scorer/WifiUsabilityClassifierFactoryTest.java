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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.when;

import android.net.wifi.WifiContext;

import androidx.test.filters.SmallTest;

import com.android.server.wifi.ml_connected_scorer.MlModelParams.RandomForestModel;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Unit tests for {@link com.android.server.wifi.ml_connected_scorer.RandomForestModule}.
 */
@SmallTest
public final class WifiUsabilityClassifierFactoryTest {
    private static final RandomForestModel RANDOM_FOREST_MODEL =
            RandomForestModel.newBuilder().build();
    private WifiUsabilityClassifierFactory mWifiUsabilityClassifierFactory;

    @Mock
    RandomForestModule mMockRandomForestModule;
    @Mock
    WifiContext mMockWifiContext;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mWifiUsabilityClassifierFactory =
                new WifiUsabilityClassifierFactory(mMockWifiContext, mMockRandomForestModule);
        when(mMockRandomForestModule.getRandomForestParams(any(WifiContext.class)))
                .thenReturn(RANDOM_FOREST_MODEL);
    }

    @Test
    public void getModel_invalidId_returnNull() {
        assertNull(mWifiUsabilityClassifierFactory
                .getClassifier(Constants.RANDOM_FOREST_MODEL_ID + 100));
    }

    @Test
    public void getModel_validId_returnNotNull() {
        assertEquals(Constants.RANDOM_FOREST_MODEL_ID,
                mWifiUsabilityClassifierFactory.getClassifier(
                        Constants.RANDOM_FOREST_MODEL_ID).getModelId());
    }
}
