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

package com.android.server.wifi.rtt;

import static android.net.wifi.rtt.ResponderConfig.RESPONDER_STA;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.hardware.wifi.supplicant.ISupplicantWifiRttController;
import android.hardware.wifi.supplicant.ISupplicantWifiRttControllerEventCallback;
import android.hardware.wifi.supplicant.RttBw;
import android.hardware.wifi.supplicant.RttCapabilities;
import android.hardware.wifi.supplicant.RttConfig;
import android.hardware.wifi.supplicant.RttPreamble;
import android.hardware.wifi.supplicant.RttResult;
import android.net.MacAddress;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiSsid;
import android.net.wifi.rtt.PasnConfig;
import android.net.wifi.rtt.ProximityDetectionConfig;
import android.net.wifi.rtt.RangingRequest;
import android.net.wifi.rtt.RangingResult;
import android.net.wifi.rtt.ResponderConfig;
import android.net.wifi.rtt.SecureRangingConfig;
import android.net.wifi.util.Environment;
import android.os.RemoteException;
import android.os.ServiceSpecificException;

import androidx.test.filters.SmallTest;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * Unit tests for {@link SupplicantWifiRttControllerAidlImpl}.
 */
@SmallTest
public class SupplicantWifiRttControllerAidlImplTest {

    private static final String IFACE_NAME = "wlan0";
    private static final MacAddress TEST_MAC_ADDRESS = MacAddress.fromString("01:23:45:67:89:AB");
    private static final byte[] TEST_MAC_ADDRESS_BYTES = TEST_MAC_ADDRESS.toByteArray();
    private static final String TEST_DEVICE_NAME = "TestDevice";
    private static final byte[] TEST_DEV_IK = new byte[16];
    private static final byte[] TEST_PMK = new byte[32];

    static {
        Arrays.fill(TEST_DEV_IK, (byte) 0x0A);
        Arrays.fill(TEST_PMK, (byte) 0x0B);
    }

    private static final int TEST_RANGING_SERVICE_ROLE = ProximityDetectionConfig
            .RANGING_SERVICE_ROLE_SEEKER;
    private static final int TEST_DISCOVERY_CHANNEL_FREQUENCY_MHZ = 2412;
    private static final int TEST_PREFERRED_RANGING_CHANNEL_FREQUENCY_MHZ = 5180;
    private static final boolean TEST_ADVERTISER_REQUIRE_RANGE_RESULT = true;
    private static final int TEST_PREFERRED_RANGING_MEASUREMENT_ROLE = ProximityDetectionConfig
            .RANGING_MEASUREMENT_ROLE_ISTA;
    private static final int TEST_CONTINUOUS_RANGING_INTERVAL_MS = 500;
    private static final int TEST_EGRESS_DISTANCE_MM = 1000;
    private static final int TEST_INGRESS_DISTANCE_MM = 500;

    @Mock
    private ISupplicantWifiRttController mMockHalRttController;

    private SupplicantWifiRttControllerAidlImpl mDut;
    private ISupplicantWifiRttControllerEventCallback mHalCallback;
    private SupplicantWifiRttController.SupplicantWifiRttControllerEventCallback mFrameworkCallback;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        mDut = new SupplicantWifiRttControllerAidlImpl(mMockHalRttController);

        ArgumentCaptor<ISupplicantWifiRttControllerEventCallback> callbackCaptor =
                ArgumentCaptor.forClass(ISupplicantWifiRttControllerEventCallback.class);

        doAnswer(new Answer() {
            public Object answer(InvocationOnMock invocation) {
                mHalCallback = callbackCaptor.getValue();
                return null;
            }
        }).when(mMockHalRttController).registerEventCallback(callbackCaptor.capture());

        RttCapabilities capabilities = new RttCapabilities();
        capabilities.prDeviceInfo =
                new android.hardware.wifi.supplicant.ProximityRangingDeviceInfo();
        capabilities.prDeviceInfo.maxNumContinuousRangingSeekerSessions = 1;
        capabilities.prDeviceInfo.protocolInfo =
                new android.hardware.wifi.supplicant.ProximityRangingProtocolInfo();
        capabilities.prDeviceInfo.protocolInfo.isEdcaBasedRangingSupported = true;
        capabilities.prDeviceInfo.protocolInfo.maxSupportedPacketBandwidthEdcaBased =
                RttBw.BW_80MHZ;
        capabilities.prDeviceInfo.protocolInfo.maxSupportedPreambleEdcaBased = RttPreamble.VHT;
        capabilities.prDeviceInfo.protocolInfo.isNtbNonSecureLtfRangingSupported = true;
        capabilities.prDeviceInfo.protocolInfo.isNtbSecureLtfRangingSupported = true;
        capabilities.prDeviceInfo.protocolInfo.maxSupportedPacketBandwidthNtb =
                RttBw.BW_80MHZ;
        capabilities.prDeviceInfo.protocolInfo.maxSupportedPreambleNtb = RttPreamble.VHT;
        capabilities.prDeviceInfo.protocolInfo.isNtbIstaRoleSupported = true;
        capabilities.prDeviceInfo.protocolInfo.isNtbRstaRoleSupported = true;
        capabilities.prDeviceInfo.protocolInfo.isEdcaBasedIstaRoleSupported = true;
        capabilities.prDeviceInfo.protocolInfo.isEdcaBasedRstaRoleSupported = true;

        when(mMockHalRttController.getCapabilities()).thenReturn(capabilities);

        mFrameworkCallback =
                mock(SupplicantWifiRttController.SupplicantWifiRttControllerEventCallback.class);
    }

    private void setupDut() {
        mDut.setup();
        mDut.registerRttEventCallback(mFrameworkCallback);
    }

    @Test
    public void testSetup_success() throws RemoteException, ServiceSpecificException {
        assertTrue(mDut.setup());
        verify(mMockHalRttController).registerEventCallback(any());
        verify(mMockHalRttController).getCapabilities();
    }

    @Test
    public void testSetup_failureOnRegisterCallback() throws RemoteException {
        doThrow(new RemoteException()).when(mMockHalRttController).registerEventCallback(any());
        assertFalse(mDut.setup());
    }

    @Test
    public void testSetup_failureOnGetCapabilities() throws RemoteException {
        when(mMockHalRttController.getCapabilities()).thenThrow(new RemoteException());
        assertFalse(mDut.setup());
    }

    @Test
    public void testValidate() throws RemoteException {
        setupDut();
        when(mMockHalRttController.getName()).thenReturn(IFACE_NAME);
        assertTrue(mDut.validate());
        verify(mMockHalRttController).getName();
    }

    @Test
    public void testGetName() throws RemoteException {
        setupDut();
        when(mMockHalRttController.getName()).thenReturn(IFACE_NAME);
        assertEquals(IFACE_NAME, mDut.getName());
        verify(mMockHalRttController).getName();
    }

    @Test
    public void testSetProximityRangingDeviceName() throws RemoteException {
        setupDut();
        mDut.setProximityRangingDeviceName(TEST_DEVICE_NAME);
        verify(mMockHalRttController).setProximityRangingDeviceName(eq(TEST_DEVICE_NAME));
    }

    @Test
    public void testSetProximityRangingMacAddress() throws RemoteException {
        setupDut();
        mDut.setProximityRangingMacAddress(TEST_MAC_ADDRESS_BYTES);
        verify(mMockHalRttController).setProximityRangingMacAddress(eq(TEST_MAC_ADDRESS_BYTES));
    }

    @Test
    public void testGetProximityRangingMacAddress() throws RemoteException {
        setupDut();
        when(mMockHalRttController.getProximityRangingMacAddress())
                .thenReturn(TEST_MAC_ADDRESS_BYTES);
        assertArrayEquals(TEST_MAC_ADDRESS_BYTES, mDut.getProximityRangingMacAddress());
        verify(mMockHalRttController).getProximityRangingMacAddress();
    }

    @Test
    public void testGetProximityRangingCapabilities() {
        setupDut();
        assertNotNull(mDut.getProximityRangingCapabilities());
    }

    @Test
    public void testRangeRequest() throws RemoteException {
        assumeTrue(Environment.isSdkNewerThanB());
        setupDut();
        ProximityDetectionConfig pdConfig = new ProximityDetectionConfig
                .Builder(TEST_RANGING_SERVICE_ROLE)
                .setDiscoveryChannelFrequencyMhz(TEST_DISCOVERY_CHANNEL_FREQUENCY_MHZ)
                .setPreferredRangingChannelFrequencyMhz(
                        TEST_PREFERRED_RANGING_CHANNEL_FREQUENCY_MHZ)
                .setAdvertiserRequireRangeResult(TEST_ADVERTISER_REQUIRE_RANGE_RESULT)
                .setPreferredRangingMeasurementRole(TEST_PREFERRED_RANGING_MEASUREMENT_ROLE)
                .setContinuousRangingIntervalMillis(TEST_CONTINUOUS_RANGING_INTERVAL_MS)
                .setEgressDistanceMm(TEST_EGRESS_DISTANCE_MM)
                .setIngressDistanceMm(TEST_INGRESS_DISTANCE_MM)
                .build();
        PasnConfig pasnConfig = new PasnConfig
                .Builder(PasnConfig.AKM_SAE, PasnConfig.CIPHER_GCMP_256)
                .setWifiSsid(WifiSsid.fromString("\"TEST_SSID\""))
                .setPassword("TEST_PASSWORD")
                .setProximityDetectionSeekerDeviceIdentityKey(TEST_DEV_IK)
                .build();
        SecureRangingConfig secureRangingConfig = new SecureRangingConfig
                .Builder(pasnConfig)
                .setRangingFrameProtectionEnabled(true)
                .setSecureHeLtfEnabled(true)
                .build();
        ResponderConfig responder = new ResponderConfig.Builder()
                .setMacAddress(TEST_MAC_ADDRESS)
                .set80211mcSupported(true)
                .setResponderType(RESPONDER_STA)
                .setChannelWidth(ScanResult.CHANNEL_WIDTH_80MHZ)
                .setPreamble(ScanResult.PREAMBLE_VHT)
                .setSecureRangingConfig(secureRangingConfig)
                .setProximityDetectionConfig(pdConfig)
                .build();
        RangingRequest request = new RangingRequest.Builder()
                .addResponder(responder)
                .build();
        mDut.rangeRequest(1, request);
        verify(mMockHalRttController).rangeRequest(eq(1), any(RttConfig[].class));
    }

    @Test
    public void testRangeCancel() throws RemoteException {
        setupDut();
        ArrayList<MacAddress> macAddresses = new ArrayList<>();
        macAddresses.add(TEST_MAC_ADDRESS);
        mDut.rangeCancel(1, macAddresses);

        ArgumentCaptor<android.hardware.wifi.supplicant.MacAddress[]> macAddressesCaptor =
                ArgumentCaptor.forClass(android.hardware.wifi.supplicant.MacAddress[].class);
        verify(mMockHalRttController).rangeCancel(eq(1), macAddressesCaptor.capture());
        assertArrayEquals(macAddresses.get(0).toByteArray(),
                macAddressesCaptor.getValue()[0].data);
    }

    @Test
    public void testOnResults() throws RemoteException {
        setupDut();
        RttResult rttResult = new RttResult();
        rttResult.addr = TEST_MAC_ADDRESS_BYTES;
        rttResult.lci = new android.hardware.wifi.supplicant.WifiInformationElement();
        rttResult.lcr = new android.hardware.wifi.supplicant.WifiInformationElement();
        RttResult[] halResults = new RttResult[]{rttResult};
        mHalCallback.onResults(1, halResults);

        ArgumentCaptor<ArrayList<RangingResult>> resultsCaptor =
                ArgumentCaptor.forClass(ArrayList.class);
        verify(mFrameworkCallback).onRangingResults(eq(1), resultsCaptor.capture());
        assertEquals(1, resultsCaptor.getValue().size());
        assertEquals(TEST_MAC_ADDRESS, resultsCaptor.getValue().get(0).getMacAddress());
    }

    @Test
    public void testHalToFrameworkChannelBandwidth() {
        assertEquals(ScanResult.CHANNEL_WIDTH_20MHZ,
                SupplicantWifiRttControllerAidlImpl
                        .halToFrameworkRttPacketBandwidth(RttBw.BW_20MHZ));
        assertEquals(ScanResult.CHANNEL_WIDTH_40MHZ,
                SupplicantWifiRttControllerAidlImpl
                        .halToFrameworkRttPacketBandwidth(RttBw.BW_40MHZ));
        assertEquals(ScanResult.CHANNEL_WIDTH_80MHZ,
                SupplicantWifiRttControllerAidlImpl
                        .halToFrameworkRttPacketBandwidth(RttBw.BW_80MHZ));
        assertEquals(ScanResult.CHANNEL_WIDTH_160MHZ,
                SupplicantWifiRttControllerAidlImpl
                        .halToFrameworkRttPacketBandwidth(RttBw.BW_160MHZ));
        assertEquals(ScanResult.CHANNEL_WIDTH_320MHZ,
                SupplicantWifiRttControllerAidlImpl
                        .halToFrameworkRttPacketBandwidth(RttBw.BW_320MHZ));
        assertEquals(RangingResult.UNSPECIFIED,
                SupplicantWifiRttControllerAidlImpl.halToFrameworkRttPacketBandwidth(-1));
    }

    @Test
    public void testHalToFrameworkRttStatus() {
        assertEquals(RangingResult.STATUS_SUCCESS,
                SupplicantWifiRttControllerAidlImpl.halToFrameworkRttStatus(
                        RttResult.RttStatus.SUCCESS));
        assertEquals(RangingResult.STATUS_FAIL,
                SupplicantWifiRttControllerAidlImpl.halToFrameworkRttStatus(
                        RttResult.RttStatus.FAILURE));
        assertThrows(IllegalArgumentException.class, () -> {
            SupplicantWifiRttControllerAidlImpl.halToFrameworkRttStatus(-1);
        });
    }
}
