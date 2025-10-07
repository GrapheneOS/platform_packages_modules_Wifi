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

package android.net.wifi.p2p.nsd;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import androidx.test.filters.SmallTest;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for {@link android.net.wifi.p2p.nsd.WifiP2pUsdBasedServiceConfig}.
 */
@SmallTest
@RunWith(JUnit4.class)
public class WifiP2pUsdBasedServiceConfigTest {

    private static final String TAG = "WifiP2pUsdBasedServiceConfigTest";

    @Test
    public void testEqualsAndHashCode() {
        // Test with identical objects
        WifiP2pUsdBasedServiceConfig config1 = new WifiP2pUsdBasedServiceConfig
                .Builder("serviceName")
                .setServiceProtocolType(WifiP2pUsdBasedServiceConfig.SERVICE_PROTOCOL_TYPE_BONJOUR)
                .setServiceSpecificInfo(new byte[]{1, 2, 3})
                .build();
        WifiP2pUsdBasedServiceConfig config2 = new WifiP2pUsdBasedServiceConfig
                .Builder("serviceName")
                .setServiceProtocolType(WifiP2pUsdBasedServiceConfig.SERVICE_PROTOCOL_TYPE_BONJOUR)
                .setServiceSpecificInfo(new byte[]{1, 2, 3})
                .build();

        assertTrue("config1 should be equal to config2", config1.equals(config2));
        assertEquals(config1.hashCode(), config2.hashCode());

        // Test with different service protocol types
        WifiP2pUsdBasedServiceConfig config3 = new WifiP2pUsdBasedServiceConfig
                .Builder("serviceName")
                .setServiceProtocolType(WifiP2pUsdBasedServiceConfig.SERVICE_PROTOCOL_TYPE_GENERIC)
                .setServiceSpecificInfo(new byte[]{1, 2, 3})
                .build();
        assertNotEquals(config1, config3);
        assertNotEquals(config1.hashCode(), config3.hashCode());

        // Test with different service names
        WifiP2pUsdBasedServiceConfig config4 = new WifiP2pUsdBasedServiceConfig
                .Builder("differentServiceName")
                .setServiceProtocolType(WifiP2pUsdBasedServiceConfig.SERVICE_PROTOCOL_TYPE_BONJOUR)
                .setServiceSpecificInfo(new byte[]{1, 2, 3})
                .build();
        assertNotEquals(config1, config4);
        assertNotEquals(config1.hashCode(), config4.hashCode());

        // Test with different service specific info
        WifiP2pUsdBasedServiceConfig config5 = new WifiP2pUsdBasedServiceConfig
                .Builder("serviceName")
                .setServiceProtocolType(WifiP2pUsdBasedServiceConfig.SERVICE_PROTOCOL_TYPE_BONJOUR)
                .setServiceSpecificInfo(new byte[]{4, 5, 6})
                .build();
        assertNotEquals(config1, config5);
        assertNotEquals(config1.hashCode(), config5.hashCode());

        // Test with null service specific info
        WifiP2pUsdBasedServiceConfig config6 = new WifiP2pUsdBasedServiceConfig
                .Builder("serviceName")
                .setServiceProtocolType(WifiP2pUsdBasedServiceConfig.SERVICE_PROTOCOL_TYPE_BONJOUR)
                .setServiceSpecificInfo(null)
                .build();
        assertNotEquals(config1, config6);
        assertNotEquals(config1.hashCode(), config6.hashCode());
    }
}
