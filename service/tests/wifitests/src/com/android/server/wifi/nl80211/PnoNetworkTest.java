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

/** Unit tests for {@link PnoNetwork}. */
@SmallTest
public class PnoNetworkTest {

    private static final byte[] TEST_SSID = { 's', 's', 'i', 'd' };
    private static final int[] TEST_FREQUENCIES = { 2412, 2417, 5035 };

    @Test
    public void testGetters() {
        PnoNetwork network = new PnoNetwork();
        network.setSsid(TEST_SSID);
        network.setFrequenciesMhz(TEST_FREQUENCIES);
        network.setHidden(true);

        assertThat(network.getSsid()).isEqualTo(TEST_SSID);
        assertThat(network.getFrequenciesMhz()).isEqualTo(TEST_FREQUENCIES);
        assertThat(network.isHidden()).isTrue();
    }

    @Test
    public void testEquals() {
        PnoNetwork network = new PnoNetwork();
        network.setSsid(new byte[] { 'a', 's', 'd', 'f' });
        network.setFrequenciesMhz(new int[] { 1, 2, 3 });
        network.setHidden(true);

        PnoNetwork equalNetwork = new PnoNetwork();
        equalNetwork.setSsid(new byte[] { 'a', 's', 'd', 'f' });
        equalNetwork.setFrequenciesMhz(new int[] { 1, 2, 3 });
        equalNetwork.setHidden(true);

        assertThat(network).isEqualTo(equalNetwork);

        PnoNetwork unequalNetwork = new PnoNetwork();
        unequalNetwork.setSsid(new byte[] { 'j', 'k', 'l', ';' });
        unequalNetwork.setFrequenciesMhz(new int[] { 4, 5, 6 });
        unequalNetwork.setHidden(false);

        assertThat(network).isNotEqualTo(unequalNetwork);
    }
}
