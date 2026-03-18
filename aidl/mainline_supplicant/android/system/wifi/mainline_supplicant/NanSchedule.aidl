/*
 * Copyright (C) 2026 The Android Open Source Project
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

package android.system.wifi.mainline_supplicant;

import android.system.wifi.mainline_supplicant.ScheduleBitmap;

/**
 * Interface used to represent Wifi Aware NDL schedule
 * See Wi-Fi Aware Specification 4.0 section 5.1
 */

parcelable NanSchedule {
    /**
     * Identify the associated NAN Availability attribute
     */
    int mapId;

    /**
     * Committed Availability entry
     */
    ScheduleBitmap[] committedSchedule;

    /**
     * Potential Availability entry
     */
    ScheduleBitmap[] potentialSchedule;
}
