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

package com.android.server.wifi;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import android.content.Context;
import android.hardware.wifi.supplicant.UsdServiceDiscoveryInfo;
import android.hardware.wifi.supplicant.ProximityRangingProtocolInfo;
import android.hardware.wifi.supplicant.DeviceIdentityKey;
import android.hardware.wifi.supplicant.RttBw;
import android.net.MacAddress;
import android.net.wifi.WifiSsid;
import android.os.Handler;
import android.os.test.TestLooper;

import androidx.test.filters.SmallTest;
import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.server.wifi.usd.UsdRequestManager;
import com.android.wifi.flags.Flags;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.MockitoSession;

@SmallTest
public class SupplicantStaIfaceCallbackAidlImplTest extends WifiBaseTest {
    private static final String IFACE_NAME = "wlan0";
    private static final byte[] TEST_SSID_BYTES = "<<google>>".getBytes();
    private static final WifiSsid TEST_WIFI_SSID = WifiSsid.fromBytes(TEST_SSID_BYTES);
    private static final int TEST_OWN_ID = 10;
    private static final int TEST_PEER_ID = 20;
    private static final int TEST_SERVICE_PROTO_TYPE = 2;
    private static final String TEST_DEVICE_NAME = "Target_P2P_Device";
    private static final String TEST_MAC_ADDR_STR_1 = "12:34:56:78:9a:bc";
    private static final String TEST_MAC_ADDR_STR_2 = "00:11:22:33:44:55";
    private static final byte[] TEST_MAC_ADDR_BYTES_1 =
            new byte[]{0x12, 0x34, 0x56, 0x78, (byte) 0x9a, (byte) 0xbc};
    private static final byte[] TEST_MAC_ADDR_BYTES_2 =
            new byte[]{0x00, 0x11, 0x22, 0x33, 0x44, 0x55};
    private static final byte[] TEST_SSI_DATA = new byte[]{4, 5, 6};
    private static final int TEST_PREAMBLE_1 = 1;
    private static final int TEST_PREAMBLE_2 = 2;
    private static final int IDENTITY_KEY_LEN = 16;

    private SupplicantStaIfaceCallbackAidlImpl mCallback;
    private TestLooper mLooper;
    private Object mLock = new Object();
    private MockitoSession mSession;

    @Mock private SupplicantStaIfaceHalAidlBase mStaIfaceHal;
    @Mock private Context mContext;
    @Mock private WifiMonitor mWifiMonitor;
    @Mock private SsidTranslator mSsidTranslator;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        mLooper = new TestLooper();
        mSession = ExtendedMockito.mockitoSession()
                .mockStatic(Flags.class, withSettings().lenient())
                .startMocking();

        // Mock SSID translation to return the same SSID by default
        when(mSsidTranslator.getTranslatedSsidForStaIface(any(), anyString()))
                .thenReturn(TEST_WIFI_SSID);

        mCallback = spy(new SupplicantStaIfaceCallbackAidlImpl(
                mStaIfaceHal, IFACE_NAME, mLock, mContext,
                mWifiMonitor, mSsidTranslator, new Handler(mLooper.getLooper())));
        when(Flags.proximityRangingImpl()).thenReturn(true);
    }

    @After
    public void cleanup() {
        validateMockitoUsage();
        if (mSession != null) {
            mSession.finishMocking();
        }
    }

    /**
     * Test the mapping of AIDL UsdServiceDiscoveryInfo to framework UsdHalDiscoveryInfo
     * specifically for the "Publish Replied" event.
     */
    @Test
    public void testOnUsdPublishRepliedCompleteFieldMapping() {
        doReturn(5).when(mCallback).getInterfaceVersion();
        UsdServiceDiscoveryInfo aidlConfig = new UsdServiceDiscoveryInfo();
        aidlConfig.ownId = TEST_OWN_ID;
        aidlConfig.peerId = TEST_PEER_ID;
        aidlConfig.peerMacAddress = TEST_MAC_ADDR_BYTES_1;
        aidlConfig.peerDevIk = new DeviceIdentityKey();
        aidlConfig.peerDevIk.data = new byte[IDENTITY_KEY_LEN];
        aidlConfig.prInfo = new ProximityRangingProtocolInfo();
        aidlConfig.prInfo.deviceName = TEST_DEVICE_NAME;
        aidlConfig.prInfo.isEdcaBasedRangingSupported = true;
        aidlConfig.prInfo.isNtbNonSecureLtfRangingSupported = false;
        aidlConfig.prInfo.isNtbSecureLtfRangingSupported = true;
        aidlConfig.prInfo.isUnauthenticatedPasnModeSupported = true;
        aidlConfig.prInfo.isAuthenticatedPasnModeSupported = false;
        aidlConfig.prInfo.isEdcaBasedIstaRoleSupported = true;
        aidlConfig.prInfo.isEdcaBasedRstaRoleSupported = true;
        aidlConfig.prInfo.isNtbIstaRoleSupported = false;
        aidlConfig.prInfo.isNtbRstaRoleSupported = true;
        aidlConfig.prInfo.maxSupportedPacketBandwidthEdcaBased = RttBw.BW_80MHZ;
        aidlConfig.prInfo.maxSupportedPreambleEdcaBased = TEST_PREAMBLE_1;
        aidlConfig.prInfo.maxSupportedPacketBandwidthNtb = RttBw.BW_160MHZ;
        aidlConfig.prInfo.maxSupportedPreambleNtb = TEST_PREAMBLE_2;
        aidlConfig.prInfo.is6GHzSupported = true;
        UsdRequestManager.UsdNativeEventsCallback mockFrameworkCallback =
                mock(UsdRequestManager.UsdNativeEventsCallback.class);
        when(mStaIfaceHal.getUsdEventsCallback()).thenReturn(mockFrameworkCallback);

        mCallback.onUsdPublishReplied(aidlConfig);
        mLooper.dispatchAll();

        ArgumentCaptor<UsdRequestManager.UsdHalDiscoveryInfo> captor =
                ArgumentCaptor.forClass(UsdRequestManager.UsdHalDiscoveryInfo.class);
        verify(mockFrameworkCallback).onUsdPublishReplied(captor.capture());
        UsdRequestManager.UsdHalDiscoveryInfo result = captor.getValue();
        assertNotNull(result.proximityRangingProtocolInfo);
        assertEquals(MacAddress.fromString(TEST_MAC_ADDR_STR_1), result.peerMacAddress);
        assertEquals(TEST_DEVICE_NAME, result.proximityRangingProtocolInfo.deviceName);
        assertTrue(result.proximityRangingProtocolInfo.isEdcaBasedRangingSupported);
        assertFalse(result.proximityRangingProtocolInfo.isNtbNonSecureLtfRangingSupported);
        assertTrue(result.proximityRangingProtocolInfo.isNtbSecureLtfRangingSupported);
        assertEquals(RttBw.BW_80MHZ,
                result.proximityRangingProtocolInfo.maxSupportedPacketBandwidthEdcaBased);
        assertEquals(RttBw.BW_160MHZ,
                result.proximityRangingProtocolInfo.maxSupportedPacketBandwidthNtb);
        assertTrue(result.proximityRangingProtocolInfo.is6GHzSupported);
    }

    /**
     * Test the mapping of AIDL UsdServiceDiscoveryInfo to framework UsdHalDiscoveryInfo
     * specifically for the "Service Discovered" (Subscribe) event.
     */
    @Test
    public void testOnUsdServiceDiscoveredCompleteFieldMapping() {
        doReturn(5).when(mCallback).getInterfaceVersion();
        UsdServiceDiscoveryInfo aidlConfig = new UsdServiceDiscoveryInfo();
        aidlConfig.ownId = TEST_OWN_ID;
        aidlConfig.peerId = TEST_PEER_ID;
        aidlConfig.peerMacAddress = TEST_MAC_ADDR_BYTES_2;
        aidlConfig.serviceSpecificInfo = TEST_SSI_DATA;
        aidlConfig.protoType = TEST_SERVICE_PROTO_TYPE;
        aidlConfig.isFsd = false;
        aidlConfig.prInfo = new ProximityRangingProtocolInfo();
        aidlConfig.prInfo.deviceName = TEST_DEVICE_NAME;
        aidlConfig.prInfo.isEdcaBasedRangingSupported = true;
        aidlConfig.prInfo.isNtbNonSecureLtfRangingSupported = false;
        aidlConfig.peerDevIk = new DeviceIdentityKey();
        aidlConfig.peerDevIk.data = new byte[IDENTITY_KEY_LEN];
        UsdRequestManager.UsdNativeEventsCallback mockFrameworkCallback =
                mock(UsdRequestManager.UsdNativeEventsCallback.class);
        when(mStaIfaceHal.getUsdEventsCallback()).thenReturn(mockFrameworkCallback);

        mCallback.onUsdServiceDiscovered(aidlConfig);
        mLooper.dispatchAll();

        ArgumentCaptor<UsdRequestManager.UsdHalDiscoveryInfo> captor =
                ArgumentCaptor.forClass(UsdRequestManager.UsdHalDiscoveryInfo.class);
        verify(mockFrameworkCallback).onUsdServiceDiscovered(captor.capture());
        UsdRequestManager.UsdHalDiscoveryInfo result = captor.getValue();
        assertEquals(TEST_OWN_ID, result.ownId);
        assertEquals(TEST_PEER_ID, result.peerId);
        assertEquals(MacAddress.fromString(TEST_MAC_ADDR_STR_2), result.peerMacAddress);
        assertArrayEquals(TEST_SSI_DATA, result.serviceSpecificInfo);
        assertEquals(TEST_SERVICE_PROTO_TYPE, result.serviceProtoType);
        assertFalse(result.isFsdEnabled);
        assertNotNull(result.proximityRangingProtocolInfo);
        assertEquals(TEST_DEVICE_NAME, result.proximityRangingProtocolInfo.deviceName);
        assertTrue(result.proximityRangingProtocolInfo.isEdcaBasedRangingSupported);
        assertFalse(result.proximityRangingProtocolInfo.isNtbNonSecureLtfRangingSupported);
        assertNotNull(result.deviceIdentityKey);
    }

    @Test
    public void testOnUsdServiceDiscovered_InvalidProximityRaningInfoAndDevIk() {
        doReturn(5).when(mCallback).getInterfaceVersion();
        UsdServiceDiscoveryInfo aidlConfig = new UsdServiceDiscoveryInfo();
        aidlConfig.ownId = TEST_OWN_ID;
        aidlConfig.peerId = TEST_PEER_ID;
        aidlConfig.peerMacAddress = TEST_MAC_ADDR_BYTES_2;
        aidlConfig.serviceSpecificInfo = TEST_SSI_DATA;
        aidlConfig.protoType = TEST_SERVICE_PROTO_TYPE;
        aidlConfig.isFsd = false;
        aidlConfig.prInfo = null; // Simulate null proximity ranging info
        // Simulate invalid DevIk
        aidlConfig.peerDevIk = new DeviceIdentityKey();
        aidlConfig.peerDevIk.data =
                new byte[] {0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0A};
        UsdRequestManager.UsdNativeEventsCallback mockFrameworkCallback =
                mock(UsdRequestManager.UsdNativeEventsCallback.class);
        when(mStaIfaceHal.getUsdEventsCallback()).thenReturn(mockFrameworkCallback);

        mCallback.onUsdServiceDiscovered(aidlConfig);
        mLooper.dispatchAll();

        ArgumentCaptor<UsdRequestManager.UsdHalDiscoveryInfo> captor =
                ArgumentCaptor.forClass(UsdRequestManager.UsdHalDiscoveryInfo.class);
        verify(mockFrameworkCallback).onUsdServiceDiscovered(captor.capture());
        UsdRequestManager.UsdHalDiscoveryInfo result = captor.getValue();
        assertEquals(TEST_OWN_ID, result.ownId);
        assertEquals(TEST_PEER_ID, result.peerId);
        assertEquals(MacAddress.fromString(TEST_MAC_ADDR_STR_2), result.peerMacAddress);
        assertArrayEquals(TEST_SSI_DATA, result.serviceSpecificInfo);
        assertEquals(TEST_SERVICE_PROTO_TYPE, result.serviceProtoType);
        assertFalse(result.isFsdEnabled);
        assertNull(result.proximityRangingProtocolInfo);
        assertNull(result.deviceIdentityKey);
    }
}