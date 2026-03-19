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

/**
 * Interface used to represent Wifi Aware NDL schedule
 * See Wi-Fi Aware Specification 4.0 section 5.1
 */
parcelable ScheduleBitmap {
    /**
     * A hexadecimal string representing the schedule bitmap.
     *
     * The schedule uses a 512/16 schema: the total period is 512 TUs,
     * divided into 32 slots of 16 TUs each. Each character represents 4 bits,
     * so 8 hexadecimal characters represent the full 32-slot bitmap.
     * Example: "0000FFFF" indicates slots 16-31 are active.
     */
    String slotsBitmap;

    /**
     * Indicates if the channel frequency is set.
     */
    boolean isFrequencySet;

    /**
     * The channel frequency of the schedule. Used for both committed and potential schedule.
     */
    int channelFrequency;

    /**
     * The band of the potential schedule.
     * @see NanBandIndex
     */
    int band;

    /**
     * The preference of the potential schedule. 3 is the highest, and 0 is the lowest
     */
    int preference;
}
