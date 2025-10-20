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

import android.annotation.Nullable;
import android.net.wifi.WifiContext;
import android.util.Log;

import com.android.server.wifi.ml_connected_scorer.MlModelParams.RandomForestModel;

/** Factory for the different ml models for predicting wifi usability scores. */
public class WifiUsabilityClassifierFactory {
    WifiContext mWifiContext;
    RandomForestModule mRandomForestModule;
    RandomForestClassifier mRandomForestClassifier;

    public WifiUsabilityClassifierFactory(WifiContext wifiContext,
            RandomForestModule randomForestModule) {
        mWifiContext = wifiContext;
        mRandomForestModule = randomForestModule;
    }

    /**
     * Get a {@link WifiUsabilityClassifier} for the modelId.
     * @param modelId ID of the model
     * @return a WifiUsabilityClassifier for the modelId
     */
    public @Nullable WifiUsabilityClassifier getClassifier(int modelId) {
        switch (modelId) {
            case Constants.RANDOM_FOREST_MODEL_ID:
                if (mRandomForestClassifier == null) {
                    RandomForestModel randomForestModel =
                            mRandomForestModule.getRandomForestParams(mWifiContext);
                    if (randomForestModel != null) {
                        mRandomForestClassifier = new RandomForestClassifier(randomForestModel,
                            Constants.RANDOM_FOREST_MODEL_ID);
                    }
                }
                return mRandomForestClassifier;
            // fall through
            default:
                Log.e(Constants.TAG, "Failed to find model id: " + modelId);
                return null;
        }
    }
}
