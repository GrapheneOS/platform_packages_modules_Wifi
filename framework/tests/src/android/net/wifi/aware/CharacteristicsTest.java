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

package android.net.wifi.aware;

import static org.junit.Assert.assertEquals;

import android.os.Bundle;

import androidx.test.filters.SmallTest;

import org.junit.Test;

/**
 * Unit test harness for {@link android.net.wifi.aware.Characteristics}.
 */
@SmallTest
public class CharacteristicsTest {
    @Test
    public void testGetSupportedPeriodicRangingIntervals() {
        Bundle bundle = new Bundle();
        int expectedIntervals = Characteristics.SUPPORTED_PERIODIC_RANGING_INTERVAL_128TU
                | Characteristics.SUPPORTED_PERIODIC_RANGING_INTERVAL_512TU;
        bundle.putInt(Characteristics.KEY_SUPPORTED_PERIODIC_RANGING_INTERVALS, expectedIntervals);
        Characteristics characteristics = new Characteristics(bundle);

        int actualIntervals = characteristics.getSupportedPeriodicRangingIntervals();

        assertEquals(expectedIntervals, actualIntervals);
    }
}
