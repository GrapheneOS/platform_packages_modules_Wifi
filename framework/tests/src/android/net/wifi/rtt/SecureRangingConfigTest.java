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
package android.net.wifi.rtt;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.net.wifi.WifiSsid;
import android.os.Parcel;

import androidx.test.filters.SmallTest;

import org.junit.Test;


/**
 * Unit tests for {@link SecureRangingConfig}.
 */
@SmallTest
public class SecureRangingConfigTest {

    private static final boolean TEST_SECURE_HE_LTF = true;
    private static final boolean TEST_RANGING_FRAME_PROTECTION = false;
    private static final int TEST_AKM = PasnConfig.AKM_SAE;
    private static final int TEST_CIPHER = PasnConfig.CIPHER_CCMP_128;
    private static final byte[] TEST_COOKIE = new byte[]{1, 2, 3};
    private static final String TEST_SSID = "\"Test SSID\"";
    private static final String TEST_PASSWORD = "password";

    /**
     * Verify builder and getter methods work as expected.
     */
    @Test
    public void testBuilderAndGetters() {
        WifiSsid ssid = WifiSsid.fromString(TEST_SSID);
        PasnConfig pasnConfig = new PasnConfig.Builder(TEST_AKM, TEST_CIPHER)
                .setPassword(TEST_PASSWORD)
                .setWifiSsid(ssid)
                .setPasnComebackCookie(TEST_COOKIE).build();
        SecureRangingConfig config = new SecureRangingConfig.Builder(pasnConfig)
                .setSecureHeLtfEnabled(TEST_SECURE_HE_LTF)
                .setRangingFrameProtectionEnabled(TEST_RANGING_FRAME_PROTECTION)
                .build();

        assertEquals(TEST_SECURE_HE_LTF, config.isSecureHeLtfEnabled());
        assertEquals(TEST_RANGING_FRAME_PROTECTION, config.isRangingFrameProtectionEnabled());
        assertEquals(pasnConfig, config.getPasnConfig());
    }

    /**
     * Verify parceling round trip returns an identical object.
     */
    @Test
    public void testParcelableRoundTrip() {
        WifiSsid ssid = WifiSsid.fromString(TEST_SSID);
        PasnConfig pasnConfig = new PasnConfig.Builder(TEST_AKM, TEST_CIPHER)
                .setPassword(TEST_PASSWORD)
                .setWifiSsid(ssid)
                .setPasnComebackCookie(TEST_COOKIE).build();

        SecureRangingConfig config = new SecureRangingConfig.Builder(pasnConfig)
                .setSecureHeLtfEnabled(TEST_SECURE_HE_LTF)
                .setRangingFrameProtectionEnabled(TEST_RANGING_FRAME_PROTECTION)
                .build();

        Parcel parcel = Parcel.obtain();
        config.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);

        SecureRangingConfig fromParcel =
                SecureRangingConfig.CREATOR.createFromParcel(parcel);
        assertEquals(config, fromParcel);
    }

    /**
     * Verify default values when not set through the builder.
     */
    @Test
    public void testDefaultValues() {
        PasnConfig pasnConfig = new PasnConfig.Builder(TEST_AKM, TEST_CIPHER).build();
        SecureRangingConfig config = new SecureRangingConfig.Builder(pasnConfig).build();

        assertTrue(config.isSecureHeLtfEnabled());
        assertTrue(config.isRangingFrameProtectionEnabled());
    }

    /**
     * Tests equality and non-equality of objects.
     */
    @Test
    public void testEqualsAndHashCode() {
        PasnConfig pasnConfig1 = new PasnConfig.Builder(TEST_AKM, TEST_CIPHER)
                .setPasnComebackCookie(TEST_COOKIE).build();
        PasnConfig pasnConfig2 = new PasnConfig.Builder(TEST_AKM, TEST_CIPHER).build();
        SecureRangingConfig config1 = new SecureRangingConfig.Builder(pasnConfig1)
                .setSecureHeLtfEnabled(TEST_SECURE_HE_LTF)
                .setRangingFrameProtectionEnabled(TEST_RANGING_FRAME_PROTECTION)
                .build();
        SecureRangingConfig config2 = new SecureRangingConfig.Builder(pasnConfig1)
                .setSecureHeLtfEnabled(TEST_SECURE_HE_LTF)
                .setRangingFrameProtectionEnabled(TEST_RANGING_FRAME_PROTECTION)
                .build();
        SecureRangingConfig config3 = new SecureRangingConfig.Builder(pasnConfig2)
                .setSecureHeLtfEnabled(TEST_SECURE_HE_LTF)
                .setRangingFrameProtectionEnabled(TEST_RANGING_FRAME_PROTECTION)
                .build();

        assertEquals(config1, config2);
        assertNotEquals(config1, config3);
        assertEquals(config1.hashCode(), config2.hashCode());
    }

    /**
     * Tests toString() method.
     */
    @Test
    public void testToString() {
        WifiSsid ssid = WifiSsid.fromString(TEST_SSID);
        PasnConfig pasnConfig = new PasnConfig.Builder(TEST_AKM, TEST_CIPHER)
                .setPassword(TEST_PASSWORD)
                .setWifiSsid(ssid)
                .setPasnComebackCookie(TEST_COOKIE).build();
        SecureRangingConfig config = new SecureRangingConfig.Builder(pasnConfig)
                .setSecureHeLtfEnabled(TEST_SECURE_HE_LTF)
                .setRangingFrameProtectionEnabled(TEST_RANGING_FRAME_PROTECTION)
                .build();

        String configString = config.toString();

        assertNotNull(configString);
        assertNotEquals("", configString);
    }

    @Test
    public void testBuilderMethodsReturnNonNull() {
        PasnConfig pasnConfig = new PasnConfig.Builder(TEST_AKM, TEST_CIPHER).build();
        SecureRangingConfig.Builder builder = new SecureRangingConfig.Builder(pasnConfig);
        assertNotNull(builder.setSecureHeLtfEnabled(TEST_SECURE_HE_LTF));
        assertNotNull(builder.setRangingFrameProtectionEnabled(TEST_RANGING_FRAME_PROTECTION));
    }


    @Test(expected = NullPointerException.class)
    public void testBuilder_nullPasnConfigThrowsException() {
        new SecureRangingConfig.Builder(null);
    }

}
