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
import static org.junit.Assert.assertNotEquals;

import android.os.Parcel;

import androidx.test.filters.SmallTest;

import org.junit.Test;

/**
 * Unit test for {@link AwareDataPathRequest}
 */
@SmallTest
public class AwareDataPathRequestTest {
    private static final int TEST_PORT = 1234;
    private static final int TEST_TRANSPORT_PROTOCOL = 6; // TCP
    private static final String TEST_PASSPHRASE = "somePassword";

    @Test
    public void testBuilder() {
        WifiAwareDataPathSecurityConfig securityConfig =
                new WifiAwareDataPathSecurityConfig.Builder(
                        Characteristics.WIFI_AWARE_CIPHER_SUITE_NCS_SK_128)
                .setPskPassphrase(TEST_PASSPHRASE)
                .build();

        AwareDataPathRequest request = new AwareDataPathRequest.Builder()
                .setPort(TEST_PORT)
                .setTransportProtocol(TEST_TRANSPORT_PROTOCOL)
                .setDataPathSecurityConfig(securityConfig)
                .build();

        assertEquals(TEST_PORT, request.getPort());
        assertEquals(TEST_TRANSPORT_PROTOCOL, request.getTransportProtocol());
        assertEquals(securityConfig, request.getDataPathSecurityConfig());
    }

    @Test
    public void testBuilderDefaults() {
        AwareDataPathRequest request = new AwareDataPathRequest.Builder().build();

        assertEquals(0, request.getPort());
        assertEquals(-1, request.getTransportProtocol());
        assertEquals(null, request.getDataPathSecurityConfig());
    }

    @Test
    public void testParcel() {
        WifiAwareDataPathSecurityConfig securityConfig =
                new WifiAwareDataPathSecurityConfig.Builder(
                        Characteristics.WIFI_AWARE_CIPHER_SUITE_NCS_SK_128)
                .setPskPassphrase(TEST_PASSPHRASE)
                .build();

        AwareDataPathRequest request = new AwareDataPathRequest.Builder()
                .setPort(TEST_PORT)
                .setTransportProtocol(TEST_TRANSPORT_PROTOCOL)
                .setDataPathSecurityConfig(securityConfig)
                .build();

        Parcel parcel = Parcel.obtain();
        request.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        AwareDataPathRequest requestFromParcel =
                AwareDataPathRequest.CREATOR.createFromParcel(parcel);

        assertEquals(request, requestFromParcel);
        assertEquals(request.hashCode(), requestFromParcel.hashCode());
    }

    @Test
    public void testEqualsAndHashCode() {
        WifiAwareDataPathSecurityConfig securityConfig =
                new WifiAwareDataPathSecurityConfig.Builder(
                        Characteristics.WIFI_AWARE_CIPHER_SUITE_NCS_SK_128)
                .setPskPassphrase(TEST_PASSPHRASE)
                .build();

        AwareDataPathRequest request1 = new AwareDataPathRequest.Builder()
                .setPort(TEST_PORT)
                .setTransportProtocol(TEST_TRANSPORT_PROTOCOL)
                .setDataPathSecurityConfig(securityConfig)
                .build();

        AwareDataPathRequest request2 = new AwareDataPathRequest.Builder()
                .setPort(TEST_PORT)
                .setTransportProtocol(TEST_TRANSPORT_PROTOCOL)
                .setDataPathSecurityConfig(securityConfig)
                .build();

        assertEquals(request1, request2);
        assertEquals(request1.hashCode(), request2.hashCode());

        AwareDataPathRequest request3 = new AwareDataPathRequest.Builder()
                .setPort(TEST_PORT + 1)
                .setTransportProtocol(TEST_TRANSPORT_PROTOCOL)
                .setDataPathSecurityConfig(securityConfig)
                .build();

        assertNotEquals(request1, request3);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidPortLow() {
        new AwareDataPathRequest.Builder().setPort(0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidPortHigh() {
        new AwareDataPathRequest.Builder().setPort(65536);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidTransportProtocolLow() {
        new AwareDataPathRequest.Builder().setTransportProtocol(-1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidTransportProtocolHigh() {
        new AwareDataPathRequest.Builder().setTransportProtocol(256);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidSecurityConfigNull() {
        new AwareDataPathRequest.Builder().setDataPathSecurityConfig(null);
    }
}
