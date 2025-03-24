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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.net.wifi.WifiSsid;
import android.os.Parcel;

import androidx.test.filters.SmallTest;

import org.junit.Test;

import java.util.Arrays;

/**
 * Tests PasnConfig class.
 */
@SmallTest
public class PasnConfigTest {
    private static final int TEST_AKM = PasnConfig.AKM_SAE;
    private static final int TEST_CIPHER = PasnConfig.CIPHER_CCMP_128;
    private static final String TEST_SSID = "\"Test_SSID\"";
    private static final String TEST_PASSWORD = "password";
    private static final String TEST_PASSWORD_MASKED = "*";
    private static final byte[] TEST_COOKIE = new byte[]{1, 2, 3};

    /**
     * Verifies builder and getter methods work as expected.
     */
    @Test
    public void testBuilderAndGetters() {
        WifiSsid ssid = WifiSsid.fromString(TEST_SSID);
        PasnConfig config = new PasnConfig.Builder(TEST_AKM, TEST_CIPHER)
                .setPassword(TEST_PASSWORD)
                .setWifiSsid(ssid)
                .setPasnComebackCookie(TEST_COOKIE)
                .build();

        assertEquals(TEST_AKM, config.getBaseAkms());
        assertEquals(TEST_CIPHER, config.getCiphers());
        assertEquals(TEST_PASSWORD, config.getPassword());
        assertEquals(ssid, config.getWifiSsid());
        assertArrayEquals(TEST_COOKIE, config.getPasnComebackCookie());
    }

    /**
     * Verifies parceling round trip returns an identical object.
     */
    @Test
    public void testParcelableRoundTrip() {
        WifiSsid ssid = WifiSsid.fromString(TEST_SSID);
        PasnConfig config = new PasnConfig.Builder(TEST_AKM, TEST_CIPHER)
                .setPassword(TEST_PASSWORD)
                .setWifiSsid(ssid)
                .setPasnComebackCookie(TEST_COOKIE)
                .build();

        Parcel parcel = Parcel.obtain();
        config.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);

        PasnConfig fromParcel = PasnConfig.CREATOR.createFromParcel(parcel);
        assertEquals(config, fromParcel);
    }

    /**
     * Tests that null SSID and password result in unauthenticated PASN being used.
     */
    @Test
    public void testNullSsidAndPassword() {
        PasnConfig config = new PasnConfig.Builder(TEST_AKM, TEST_CIPHER).build();
        assertNull(config.getPassword());
        assertNull(config.getWifiSsid());
    }

    /**
     * Tests equality and non-equality of objects.
     */
    @Test
    public void testEqualsAndHashCode() {
        WifiSsid ssid1 = WifiSsid.fromString(TEST_SSID);
        WifiSsid ssid2 = WifiSsid.fromString("\"Another_SSID\"");
        PasnConfig config1 = new PasnConfig.Builder(TEST_AKM, TEST_CIPHER)
                .setWifiSsid(ssid1)
                .setPassword(TEST_PASSWORD)
                .setPasnComebackCookie(TEST_COOKIE)
                .build();
        PasnConfig config2 = new PasnConfig.Builder(TEST_AKM, TEST_CIPHER)
                .setWifiSsid(ssid1)
                .setPassword(TEST_PASSWORD)
                .setPasnComebackCookie(TEST_COOKIE)
                .build();
        PasnConfig config3 = new PasnConfig.Builder(TEST_AKM, TEST_CIPHER)
                .setWifiSsid(ssid2)
                .setPassword(TEST_PASSWORD)
                .setPasnComebackCookie(TEST_COOKIE)
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
        PasnConfig config = new PasnConfig.Builder(TEST_AKM, TEST_CIPHER)
                .setPassword(TEST_PASSWORD)
                .setWifiSsid(ssid)
                .setPasnComebackCookie(TEST_COOKIE)
                .build();

        String expectedString = "PasnConfig{" + "mBaseAkms=" + TEST_AKM + ", mCiphers="
                + TEST_CIPHER + ", mPassword='" + TEST_PASSWORD_MASKED + '\'' + ", mWifiSsid="
                + ssid + ", mPasnComebackCookie=" + Arrays.toString(TEST_COOKIE) + '}';
        assertEquals(expectedString, config.toString());
    }


    /**
     * Verifies that builder methods return a non-null builder instance.
     */
    @Test
    public void testBuilderMethodsReturnNonNull() {
        PasnConfig.Builder builder = new PasnConfig.Builder(TEST_AKM, TEST_CIPHER);
        assertNotNull(builder.setPassword(TEST_PASSWORD));
        assertNotNull(builder.setWifiSsid(WifiSsid.fromString(TEST_SSID)));
        assertNotNull(builder.setPasnComebackCookie(TEST_COOKIE));
    }

    /**
     * Tests the validation when setting an empty comeback cookie.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testSetPasnComebackCookie_emptyCookie() {
        new PasnConfig.Builder(TEST_AKM, TEST_CIPHER).setPasnComebackCookie(new byte[0]);
    }

    /**
     * Tests the validation when setting a long comeback cookie.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testSetPasnComebackCookie_longCookie() {
        new PasnConfig.Builder(TEST_AKM, TEST_CIPHER).setPasnComebackCookie(new byte[256]);
    }

    /**
     * Tests the validation when setting a null comeback cookie.
     */
    @Test(expected = NullPointerException.class)
    public void testSetPasnComebackCookie_nullCookie() {
        new PasnConfig.Builder(TEST_AKM, TEST_CIPHER).setPasnComebackCookie(null);
    }

    /**
     * Tests {@link PasnConfig#getBaseAkmsFromCapabilities(String)} method.
     */
    @Test
    public void testGetBaseAkmsFromCapabilities() {
        assertEquals(PasnConfig.AKM_NONE, PasnConfig.getBaseAkmsFromCapabilities(null));
        assertEquals(PasnConfig.AKM_NONE, PasnConfig.getBaseAkmsFromCapabilities(""));
        assertEquals(PasnConfig.AKM_SAE,
                PasnConfig.getBaseAkmsFromCapabilities("[RSN-SAE+SAE_EXT_KEY-CCMP-128]"));
        assertEquals(PasnConfig.AKM_SAE,
                PasnConfig.getBaseAkmsFromCapabilities("[RSN-PSK+SAE-CCMP-128]"));
        assertEquals(PasnConfig.AKM_FT_PSK_SHA256,
                PasnConfig.getBaseAkmsFromCapabilities("[RSN-FT/PSK-CCMP-128]"));
        assertEquals(PasnConfig.AKM_SAE | PasnConfig.AKM_PASN,
                PasnConfig.getBaseAkmsFromCapabilities("[RSN-PSK+SAE+PASN-CCMP-128]"));
    }

    /**
     * Tests {@link PasnConfig#getCiphersFromCapabilities(String)} method.
     */
    @Test
    public void testGetCiphersFromCapabilities() {
        assertEquals(PasnConfig.CIPHER_NONE, PasnConfig.getCiphersFromCapabilities(null));
        assertEquals(PasnConfig.CIPHER_NONE, PasnConfig.getCiphersFromCapabilities(""));
        assertEquals(PasnConfig.CIPHER_CCMP_128,
                PasnConfig.getCiphersFromCapabilities("[RSN-SAE+SAE_EXT_KEY-CCMP-128]"));
        assertEquals(PasnConfig.CIPHER_CCMP_256,
                PasnConfig.getCiphersFromCapabilities("[RSN-SAE+SAE_EXT_KEY-CCMP-256]"));
        assertEquals(PasnConfig.CIPHER_GCMP_128,
                PasnConfig.getCiphersFromCapabilities("[RSN-SAE+SAE_EXT_KEY-GCMP-128]"));
        assertEquals(PasnConfig.CIPHER_GCMP_256,
                PasnConfig.getCiphersFromCapabilities("[RSN-SAE+SAE_EXT_KEY-GCMP-256]"));
        assertEquals(PasnConfig.CIPHER_GCMP_256 | PasnConfig.CIPHER_CCMP_128,
                PasnConfig.getCiphersFromCapabilities("[RSN-SAE+SAE_EXT_KEY-GCMP-256+CCMP-128]"));
    }

    /**
     * Tests {@link PasnConfig#isAkmRequiresPassword(int)} method.
     */
    @Test
    public void testIsAkmRequiresPassword() {
        assertTrue(PasnConfig.isAkmRequiresPassword(PasnConfig.AKM_SAE));
    }
}
