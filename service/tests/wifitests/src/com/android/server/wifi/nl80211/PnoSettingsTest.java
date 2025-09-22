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

package com.android.server.wifi.nl80211;

import static com.google.common.truth.Truth.assertThat;

import androidx.test.filters.SmallTest;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

/** Unit tests for {@link PnoSettings}. */
@SmallTest
public class PnoSettingsTest {
    private static List<PnoNetwork> createTestNetworks() {
        PnoNetwork network1 = new PnoNetwork();
        network1.setSsid(new byte[] { 's', 's', 'i', 'd' });
        network1.setFrequenciesMhz(new int[] { 2412, 2417, 5035 });
        network1.setHidden(true);

        PnoNetwork network2 = new PnoNetwork();
        network2.setSsid(new byte[] { 'a', 's', 'd', 'f' });
        network2.setFrequenciesMhz(new int[] { 2422, 2427, 5040 });
        network2.setHidden(false);

        return Arrays.asList(network1, network2);
    }

    @Test
    public void testGetters() {
        PnoSettings settings = new PnoSettings();
        settings.setIntervalMillis(1000);
        settings.setMin2gRssiDbm(-70);
        settings.setMin5gRssiDbm(-60);
        settings.setMin6gRssiDbm(-50);
        settings.setPnoNetworks(createTestNetworks());

        assertThat(settings.getIntervalMillis()).isEqualTo(1000);
        assertThat(settings.getMin2gRssiDbm()).isEqualTo(-70);
        assertThat(settings.getMin5gRssiDbm()).isEqualTo(-60);
        assertThat(settings.getMin6gRssiDbm()).isEqualTo(-50);
        assertThat(settings.getPnoNetworks()).isEqualTo(createTestNetworks());
    }

    @Test
    public void testEquals() {
        PnoSettings settings = new PnoSettings();
        settings.setIntervalMillis(1000);
        settings.setMin2gRssiDbm(-70);
        settings.setMin5gRssiDbm(-60);
        settings.setMin6gRssiDbm(-50);
        settings.setPnoNetworks(createTestNetworks());

        PnoSettings equalSettings = new PnoSettings();
        equalSettings.setIntervalMillis(1000);
        equalSettings.setMin2gRssiDbm(-70);
        equalSettings.setMin5gRssiDbm(-60);
        equalSettings.setMin6gRssiDbm(-50);
        equalSettings.setPnoNetworks(createTestNetworks());

        assertThat(settings).isEqualTo(equalSettings);

        PnoSettings unequalSettings = new PnoSettings();
        unequalSettings.setIntervalMillis(2000);
        unequalSettings.setMin2gRssiDbm(-70);
        unequalSettings.setMin5gRssiDbm(-60);
        unequalSettings.setMin6gRssiDbm(-50);
        unequalSettings.setPnoNetworks(createTestNetworks());

        assertThat(settings).isNotEqualTo(unequalSettings);
    }
}
