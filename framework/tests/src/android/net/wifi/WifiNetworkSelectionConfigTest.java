/*
 * Copyright (C) 2022 The Android Open Source Project
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

package android.net.wifi;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.os.Parcel;

import androidx.test.filters.SmallTest;

import org.junit.Test;

/**
 * Unit tests for {@link android.net.wifi.WifiNetworkSelectionConfig}.
 */
@SmallTest
public class WifiNetworkSelectionConfigTest {

    /**
     * Check that parcel marshalling/unmarshalling works
     */
    @Test
    public void testWifiNetworkSelectionConfigParcel() {
        WifiNetworkSelectionConfig config = new WifiNetworkSelectionConfig.Builder()
                .setAssociatedNetworkSelectionOverride(
                        WifiNetworkSelectionConfig.ASSOCIATED_NETWORK_SELECTION_OVERRIDE_ENABLED)
                .setSufficiencyCheckEnabledWhenScreenOff(false)
                .build();

        Parcel parcelW = Parcel.obtain();
        config.writeToParcel(parcelW, 0);
        byte[] bytes = parcelW.marshall();
        parcelW.recycle();

        Parcel parcelR = Parcel.obtain();
        parcelR.unmarshall(bytes, 0, bytes.length);
        parcelR.setDataPosition(0);
        WifiNetworkSelectionConfig parcelConfig =
                WifiNetworkSelectionConfig.CREATOR.createFromParcel(parcelR);

        assertEquals(WifiNetworkSelectionConfig.ASSOCIATED_NETWORK_SELECTION_OVERRIDE_ENABLED,
                parcelConfig.getAssociatedNetworkSelectionOverride());
        assertFalse(parcelConfig.isSufficiencyCheckEnabledWhenScreenOff());
        assertTrue(parcelConfig.isSufficiencyCheckEnabledWhenScreenOn());
        assertEquals(config, parcelConfig);
        assertEquals(config.hashCode(), parcelConfig.hashCode());
    }

    @Test
    public void testInvalidBuilderThrowsException() {
        assertThrows(IllegalArgumentException.class,
                () -> new WifiNetworkSelectionConfig.Builder()
                        .setAssociatedNetworkSelectionOverride(-1));
    }

    /**
     * Verify the default builder creates a config with the expected default values.
     */
    @Test
    public void testDefaultBuilder() {
        WifiNetworkSelectionConfig config = new WifiNetworkSelectionConfig.Builder().build();

        assertEquals(WifiNetworkSelectionConfig.ASSOCIATED_NETWORK_SELECTION_OVERRIDE_NONE,
                config.getAssociatedNetworkSelectionOverride());
        assertTrue(config.isSufficiencyCheckEnabledWhenScreenOff());
        assertTrue(config.isSufficiencyCheckEnabledWhenScreenOn());
    }
}
