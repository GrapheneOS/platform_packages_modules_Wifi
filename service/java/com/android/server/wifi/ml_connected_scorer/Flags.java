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

/** Defines flags used in the scorer. */
public final class Flags {
    public static final boolean ENABLE_MODEL_WITH_RX_LINK_SPEED_BEACON_RX = false;
    public static final int HYSTERESIS_NETWORK_STATUS_CHANGE_MILLIS = 5000;
    public static final int MIN_TIME_TO_WAIT_BEFORE_BLOCK_BSSID_MILLIS = 29000;
    public static final int RSSI_THRESHOLD_NO_HYSTERESIS_NETWORK_STATUS_CHANGE_DBM = -81;
    public static final int SCAN_TRIGGERING_THRESHOLD = 10;
    public static final int SCORE_BREACHING_RSSI_THRESHOLD = -67;
    public static final int SCORE_LOW_RSSI_THR_DBM = -86;
    public static final int SCORE_LOW_TX_BAD_THR = 1000;
    public static final int SCORE_LOW_TX_SUCCESS_TO_BAD_RATIO_THR = 6;
    public static final float THRESHOLD = 10.0f;
    public static final float THRESHOLD_HYSTERESIS = 70.0f;
    public static final boolean ENABLE_DEFAULT_MEAN_CCA_BUSY_RATIO = true;
    public static final boolean ENABLE_SUBTRACT_RX_TIME_FROM_CCA_BUSY_TIME = false;
    public static final int RX_PKT_COUNT_FOR_UPDATE_STATS_THRESHOLD = 3;
    public static final int SUCCESS_PKT_COUNT_VERY_HIGH_THRESHOLD = 45;
    public static final int TX_PKT_COUNT_FOR_UPDATE_STATS_THRESHOLD = 3;
    public static final int LINK_SPEED_LOW_MBPS = 18;
    public static final int LINK_SPEED_VERY_LOW_MBPS = 6;
    public static final int EXIT_DATA_STALL_COUNT = 3;
    public static final int EXIT_DATA_STALL_SPEED_THRESHOLD_KBPS = 20000;
}
