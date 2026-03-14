/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.server.wifi.aware;

import static org.hamcrest.core.IsEqual.equalTo;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyShort;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.net.MacAddress;
import android.net.wifi.WifiContext;
import android.net.wifi.aware.ConfigRequest;
import android.net.wifi.aware.PublishConfig;
import android.net.wifi.aware.SubscribeConfig;
import android.net.wifi.util.WifiResourceCache;

import androidx.test.filters.SmallTest;

import com.android.server.wifi.MockResources;
import com.android.server.wifi.WifiBaseTest;
import com.android.server.wifi.hal.WifiNanIface;
import com.android.wifi.resources.R;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ErrorCollector;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.PrintWriter;


/**
 * Unit test harness for WifiAwareNativeApi
 */
@SmallTest
public class WifiAwareNativeApiTest extends WifiBaseTest {
    @Mock WifiAwareNativeManager mWifiAwareNativeManagerMock;
    @Mock WifiNanIface mWifiNanIfaceMock;
    @Mock WifiContext mWifiContextMock;
    @Mock AwareIfaceAidlSupplicantImpl mAwareIfaceAidlSupplicantImplMock;

    @Rule public ErrorCollector collector = new ErrorCollector();
    private final MockResources mMockResources = new MockResources();
    private WifiResourceCache mWifiResourceCache;

    private WifiAwareNativeApi mDut;

    /**
     * Initializes mocks.
     */
    @Before
    public void setup() throws Exception {
        MockitoAnnotations.initMocks(this);
        when(mWifiAwareNativeManagerMock.getWifiNanIface()).thenReturn(mWifiNanIfaceMock);
        when(mWifiAwareNativeManagerMock.getSupplicantNanIface())
                .thenReturn(mAwareIfaceAidlSupplicantImplMock);
        when(mWifiContextMock.getResources()).thenReturn(mMockResources);
        mWifiResourceCache = new WifiResourceCache(mWifiContextMock);
        when(mWifiContextMock.getResourceCache()).thenReturn(mWifiResourceCache);
        mDut = new WifiAwareNativeApi(mWifiAwareNativeManagerMock, mWifiContextMock);
        mDut.enableVerboseLogging(true, true);
    }

    /**
     * Test that the set parameter shell command executor works when parameters are valid.
     */
    @Test
    public void testSetParameterShellCommandSuccess() {
        setSettableParam(WifiAwareNativeApi.PARAM_MAC_RANDOM_INTERVAL_SEC, Integer.toString(1),
                true);
    }

    /**
     * Test that the set parameter shell command executor fails on incorrect name.
     */
    @Test
    public void testSetParameterShellCommandInvalidParameterName() {
        setSettableParam("XXX", Integer.toString(1), false);
    }

    /**
     * Test that the set parameter shell command executor fails on invalid value (not convertible
     * to an int).
     */
    @Test
    public void testSetParameterShellCommandInvalidValue() {
        setSettableParam(WifiAwareNativeApi.PARAM_MAC_RANDOM_INTERVAL_SEC, "garbage", false);
    }

    /**
     * Validate disable Aware will pass to the NAN interface, and trigger releaseAware.
     * @throws Exception
     */
    @Test
    public void testDisableConfigRequest() throws Exception {
        when(mWifiAwareNativeManagerMock.getSupplicantNanIface()).thenReturn(null);
        when(mWifiNanIfaceMock.disable(anyShort())).thenReturn(true);
        assertTrue(mDut.disable((short) 10));
        verify(mWifiNanIfaceMock).disable((short) 10);
    }

    @Test
    public void testDisableConfigRequestWithSupplicant() throws Exception {
        when(mAwareIfaceAidlSupplicantImplMock.disableRequest(anyShort())).thenReturn(true);
        assertTrue(mDut.disable((short) 10));
        verify(mAwareIfaceAidlSupplicantImplMock).disableRequest((short) 10);
    }

    /**
     * Validate that power configuration params set in the DUT are properly converted to
     * {@link WifiNanIface.PowerParameters}.
     */
    @Test
    public void testConversionToPowerParams() {
        when(mWifiAwareNativeManagerMock.getSupplicantNanIface()).thenReturn(null);
        ArgumentCaptor<WifiNanIface.PowerParameters> powerParamsCaptor = ArgumentCaptor.forClass(
                WifiNanIface.PowerParameters.class);
        when(mWifiNanIfaceMock.enableAndConfigure(anyShort(), any(),
                anyBoolean(), anyBoolean(), anyBoolean(), anyInt(), anyInt(), anyInt(), any()))
                .thenReturn(true);

        // Call enableAndConfigure without changing the config.
        mDut.enableAndConfigure((short) 1, null, true,
                false, true /* isIdle */, true, true, 1, -1);
        verify(mWifiNanIfaceMock).enableAndConfigure(anyShort(), any(),
                anyBoolean(), anyBoolean(), anyBoolean(), anyInt(), anyInt(),
                eq(1800),   // PARAM_MAC_RANDOM_INTERVAL_SEC_DEFAULT
                powerParamsCaptor.capture());

        // Expect the default power parameters.
        WifiNanIface.PowerParameters powerParams = powerParamsCaptor.getValue();
        assertEquals(powerParams.discoveryWindow24Ghz, 4);  // PARAM_DW_24GHZ_IDLE
        assertEquals(powerParams.discoveryWindow5Ghz, 0);   // PARAM_DW_5GHZ_IDLE
        assertEquals(powerParams.discoveryWindow6Ghz, 0);   // PARAM_DW_6GHZ_IDLE
        assertEquals(powerParams.discoveryBeaconIntervalMs,
                0); // PARAM_DISCOVERY_BEACON_INTERVAL_MS_IDLE
        assertEquals(powerParams.numberOfSpatialStreamsInDiscovery,
                0); // PARAM_NUM_SS_IN_DISCOVERY_IDLE
        assertFalse(powerParams.enableDiscoveryWindowEarlyTermination);

        // Set custom power configuration params.
        byte idle5 = 2;
        byte idle24 = -1;
        setSettablePowerParam(WifiAwareNativeApi.POWER_PARAM_IDLE_KEY,
                WifiAwareNativeApi.PARAM_DW_5GHZ, Integer.toString(idle5), true);
        setSettablePowerParam(WifiAwareNativeApi.POWER_PARAM_IDLE_KEY,
                WifiAwareNativeApi.PARAM_DW_24GHZ, Integer.toString(idle24), true);

        mDut.enableAndConfigure((short) 1, null, true,
                false, true /* isIdle */, true, true, 1, -1);
        verify(mWifiNanIfaceMock, times(2)).enableAndConfigure(anyShort(), any(),
                anyBoolean(), anyBoolean(), anyBoolean(), anyInt(), anyInt(),
                eq(1800),   // PARAM_MAC_RANDOM_INTERVAL_SEC_DEFAULT
                powerParamsCaptor.capture());

        // Expect the updated power parameters.
        powerParams = powerParamsCaptor.getValue();
        assertEquals(powerParams.discoveryWindow24Ghz, idle24);
        assertEquals(powerParams.discoveryWindow5Ghz, idle5);
        assertEquals(powerParams.discoveryWindow6Ghz, 0);   // PARAM_DW_6GHZ_IDLE
        assertEquals(powerParams.discoveryBeaconIntervalMs,
                0); // PARAM_DISCOVERY_BEACON_INTERVAL_MS_IDLE
        assertEquals(powerParams.numberOfSpatialStreamsInDiscovery,
                0); // PARAM_NUM_SS_IN_DISCOVERY_IDLE
        assertFalse(powerParams.enableDiscoveryWindowEarlyTermination);
    }

    // utilities

    private void setSettablePowerParam(String mode, String name, String value,
            boolean expectSuccess) {
        PrintWriter pwMock = mock(PrintWriter.class);
        WifiAwareShellCommand parentShellMock = mock(WifiAwareShellCommand.class);
        when(parentShellMock.getNextArgRequired()).thenReturn("set-power").thenReturn(
                mode).thenReturn(name).thenReturn(value);
        when(parentShellMock.getErrPrintWriter()).thenReturn(pwMock);

        collector.checkThat(mDut.onCommand(parentShellMock), equalTo(expectSuccess ? 0 : -1));
    }

    private void setSettableParam(String name, String value, boolean expectSuccess) {
        PrintWriter pwMock = mock(PrintWriter.class);
        WifiAwareShellCommand parentShellMock = mock(WifiAwareShellCommand.class);
        when(parentShellMock.getNextArgRequired()).thenReturn("set").thenReturn(name).thenReturn(
                value);
        when(parentShellMock.getErrPrintWriter()).thenReturn(pwMock);

        collector.checkThat(mDut.onCommand(parentShellMock), equalTo(expectSuccess ? 0 : -1));
    }

    @Test
    public void testPublish() {
        when(mWifiAwareNativeManagerMock.getSupplicantNanIface()).thenReturn(null);
        mMockResources.setBoolean(R.bool.config_wifiAwareSdeaHeaderFromFramework, true);
        mDut.publish((short) 1, (byte) 0x01, null, null);
        verify(mWifiNanIfaceMock).publish(eq((short) 1), eq((byte) 0x01), eq(null), eq(null),
                eq(WifiAwareNativeApi.SDEA_HEADER));
    }

    @Test
    public void testSubscribe() {
        when(mWifiAwareNativeManagerMock.getSupplicantNanIface()).thenReturn(null);
        mMockResources.setBoolean(R.bool.config_wifiAwareSdeaHeaderFromFramework, true);
        mDut.subscribe((short) 1, (byte) 0x01, null, null);
        verify(mWifiNanIfaceMock).subscribe(eq((short) 1), eq((byte) 0x01), eq(null), eq(null),
                eq(WifiAwareNativeApi.SDEA_HEADER));
    }

    @Test
    public void testBootstrappingRequest() {
        when(mWifiAwareNativeManagerMock.getSupplicantNanIface()).thenReturn(null);
        mMockResources.setBoolean(R.bool.config_wifiAwareSdeaHeaderFromFramework, true);
        mDut.initiateBootstrapping((short) 1, 1, new byte[6], 1, new byte[16], (byte) 1, false,
                new byte[16]);
        verify(mWifiNanIfaceMock).initiateBootstrapping(eq((short) 1), eq(1), any(), eq(1), any(),
                eq((byte) 1), eq(false), any(), eq(WifiAwareNativeApi.SDEA_HEADER));
    }

    @Test
    public void testMessageSend() {
        when(mWifiAwareNativeManagerMock.getSupplicantNanIface()).thenReturn(null);
        mMockResources.setBoolean(R.bool.config_wifiAwareSdeaHeaderFromFramework, true);
        mDut.sendMessage((short) 1, (byte) 1, 1, new byte[6], new byte[16], 1);
        verify(mWifiNanIfaceMock).sendMessage(eq((short) 1), eq((byte) 1), eq(1), any(), any(),
                eq(WifiAwareNativeApi.SDEA_HEADER));
    }

    @Test
    public void testGetCapabilities() {
        when(mWifiAwareNativeManagerMock.getSupplicantNanIface()).thenReturn(null);
        mDut.getCapabilities((short) 1);
        verify(mWifiNanIfaceMock).getCapabilities(eq((short) 1));
    }

    @Test
    public void testGetCapabilitiesWithSupplicant() {
        mDut.getCapabilities((short) 1);
        verify(mAwareIfaceAidlSupplicantImplMock).getCapabilities(eq((short) 1));
    }

    @Test
    public void testEnableAndConfigureWithSupplicant() {
        ArgumentCaptor<WifiNanIface.PowerParameters> powerParamsCaptor = ArgumentCaptor.forClass(
                WifiNanIface.PowerParameters.class);
        ConfigRequest configRequest = new ConfigRequest.Builder().build();
        mDut.enableAndConfigure((short) 1, configRequest, true, false, true, true, true, 1,
                -1);
        verify(mAwareIfaceAidlSupplicantImplMock).enableAndConfigure(eq((short) 1),
                eq(configRequest), eq(true), powerParamsCaptor.capture());
    }

    @Test
    public void testPublishWithSupplicant() {
        PublishConfig publishConfig = new PublishConfig.Builder().build();
        byte[] nik = new byte[]{1, 2, 3};
        mDut.publish((short) 1, (byte) 2, publishConfig, nik);
        verify(mAwareIfaceAidlSupplicantImplMock).publish(eq((short) 1), eq((byte) 2),
                eq(publishConfig), eq(nik));
    }

    @Test
    public void testSubscribeWithSupplicant() {
        SubscribeConfig subscribeConfig = new SubscribeConfig.Builder().build();
        byte[] nik = new byte[]{1, 2, 3};
        mDut.subscribe((short) 1, (byte) 2, subscribeConfig, nik);
        verify(mAwareIfaceAidlSupplicantImplMock).subscribe(eq((short) 1), eq((byte) 2),
                eq(subscribeConfig), eq(nik));
    }

    @Test
    public void testSendMessageWithSupplicant() {
        byte[] dest = new byte[]{1, 2, 3, 4, 5, 6};
        byte[] message = new byte[]{10, 11, 12};
        mDut.sendMessage((short) 1, (byte) 2, 3, dest, message, 4);
        verify(mAwareIfaceAidlSupplicantImplMock).sendMessage(eq((short) 1), eq((byte) 2), eq(3),
                eq(MacAddress.fromBytes(dest)), eq(message));
    }

    @Test
    public void testStopPublish() {
        when(mWifiAwareNativeManagerMock.getSupplicantNanIface()).thenReturn(null);
        mDut.stopPublish((short) 1, (byte) 2);
        verify(mWifiNanIfaceMock).stopPublish(eq((short) 1), eq((byte) 2));
    }

    @Test
    public void testStopPublishWithSupplicant() {
        mDut.stopPublish((short) 1, (byte) 2);
        verify(mAwareIfaceAidlSupplicantImplMock).stopPublish(eq((short) 1), eq((byte) 2));
    }

    @Test
    public void testStopSubscribe() {
        when(mWifiAwareNativeManagerMock.getSupplicantNanIface()).thenReturn(null);
        mDut.stopSubscribe((short) 1, (byte) 2);
        verify(mWifiNanIfaceMock).stopSubscribe(eq((short) 1), eq((byte) 2));
    }

    @Test
    public void testStopSubscribeWithSupplicant() {
        mDut.stopSubscribe((short) 1, (byte) 2);
        verify(mAwareIfaceAidlSupplicantImplMock).stopSubscribe(eq((short) 1), eq((byte) 2));
    }

    @Test
    public void testCreateAwareNetworkInterface() {
        when(mWifiAwareNativeManagerMock.getSupplicantNanIface()).thenReturn(null);
        String interfaceName = "aware0";
        mDut.createAwareNetworkInterface((short) 1, interfaceName);
        verify(mWifiNanIfaceMock).createAwareNetworkInterface(eq((short) 1), eq(interfaceName));
    }

    @Test
    public void testCreateAwareNetworkInterfaceWithSupplicant() {
        String interfaceName = "aware0";
        mDut.createAwareNetworkInterface((short) 1, interfaceName);
        verify(mAwareIfaceAidlSupplicantImplMock).createAwareNetworkInterface(eq((short) 1),
                eq(interfaceName));
    }

    @Test
    public void testDeleteAwareNetworkInterface() {
        when(mWifiAwareNativeManagerMock.getSupplicantNanIface()).thenReturn(null);
        String interfaceName = "aware0";
        mDut.deleteAwareNetworkInterface((short) 1, interfaceName);
        verify(mWifiNanIfaceMock).deleteAwareNetworkInterface(eq((short) 1), eq(interfaceName));
    }

    @Test
    public void testDeleteAwareNetworkInterfaceWithSupplicant() {
        String interfaceName = "aware0";
        mDut.deleteAwareNetworkInterface((short) 1, interfaceName);
        verify(mAwareIfaceAidlSupplicantImplMock).deleteAwareNetworkInterface(eq((short) 1),
                eq(interfaceName));
    }

    @Test
    public void testInitiateDataPath() {
        when(mWifiAwareNativeManagerMock.getSupplicantNanIface()).thenReturn(null);
        byte[] peer = new byte[]{1, 2, 3, 4, 5, 6};
        mDut.initiateDataPath((short) 1, 12, 0, 2437, peer, "aware0", true, null, null, null,
                (byte) 1, false);
        verify(mWifiNanIfaceMock).initiateDataPath(eq((short) 1), eq(12), eq(0), eq(2437),
                eq(MacAddress.fromBytes(peer)), eq("aware0"), eq(true), eq(null), eq(null),
                eq(null), eq((byte) 1), eq(false));
    }

    @Test
    public void testInitiateDataPathWithSupplicant() {
        byte[] peer = new byte[]{1, 2, 3, 4, 5, 6};
        mDut.initiateDataPath((short) 1, 12, 0, 2437, peer, "aware0", true, null, null, null,
                (byte) 1, false);
        verify(mAwareIfaceAidlSupplicantImplMock).initiateDataPath(eq((short) 1), eq(12), eq(0),
                eq(2437), eq(MacAddress.fromBytes(peer)), eq("aware0"), eq(true), eq(null),
                eq(null), eq((byte) 1), eq(false));
    }

    @Test
    public void testRespondToDataPathRequest() {
        when(mWifiAwareNativeManagerMock.getSupplicantNanIface()).thenReturn(null);
        mDut.respondToDataPathRequest((short) 1, true, 123, "aware0", null, true, null, null,
                (byte) 1, false, null, null);
        verify(mWifiNanIfaceMock).respondToDataPathRequest(eq((short) 1), eq(true), eq(123),
                eq("aware0"), eq(null), eq(true), eq(null), eq(null), eq((byte) 1), eq(false),
                eq(null), eq(null));
    }

    @Test
    public void testRespondToDataPathRequestWithSupplicant() {
        mDut.respondToDataPathRequest((short) 1, true, 123, "aware0", null, true, null, null,
                (byte) 1, false, null, null);
        verify(mAwareIfaceAidlSupplicantImplMock).respondToDataPathRequest(eq((short) 1), eq(true),
                eq(123), eq("aware0"), eq(null), eq(true), eq(null), eq((byte) 1), eq(false),
                eq(null), eq(null));
    }

    @Test
    public void testEndDataPath() {
        when(mWifiAwareNativeManagerMock.getSupplicantNanIface()).thenReturn(null);
        mDut.endDataPath((short) 1, 123);
        verify(mWifiNanIfaceMock).endDataPath(eq((short) 1), eq(123));
    }

    @Test
    public void testEndDataPathWithSupplicant() {
        mDut.endDataPath((short) 1, 123);
        verify(mAwareIfaceAidlSupplicantImplMock).endDataPath(eq((short) 1), eq(123));
    }

    @Test
    public void testEndPairing() {
        when(mWifiAwareNativeManagerMock.getSupplicantNanIface()).thenReturn(null);
        mDut.endPairing((short) 1, 123);
        verify(mWifiNanIfaceMock).endPairing(eq((short) 1), eq(123));
    }

    @Test
    public void testEndPairingWithSupplicant() {
        mDut.endPairing((short) 1, 123);
        verify(mAwareIfaceAidlSupplicantImplMock).endPairing(eq((short) 1), eq(123));
    }

    @Test
    public void testInitiatePairing() {
        when(mWifiAwareNativeManagerMock.getSupplicantNanIface()).thenReturn(null);
        byte[] peer = new byte[]{1, 2, 3, 4, 5, 6};
        mDut.initiatePairing((short) 1, 123, peer, null, true, 1, null, null, 1, 1, (byte)1);
        verify(mWifiNanIfaceMock).initiatePairing(eq((short) 1), eq(123),
                eq(MacAddress.fromBytes(peer)), eq(null), eq(true), eq(1), eq(null), eq(null),
                eq(1), eq(1));
    }

    @Test
    public void testInitiatePairingWithSupplicant() {
        byte[] peer = new byte[]{1, 2, 3, 4, 5, 6};
        mDut.initiatePairing((short) 1, 123, peer, null, true, 1, null, null, 1, 1, (byte)1);
        verify(mAwareIfaceAidlSupplicantImplMock).initiateNanPairingRequest(eq((short) 1), eq(123),
                eq(MacAddress.fromBytes(peer)), eq(null), eq(true), eq(1), eq(null), eq(null),
                eq(1), eq(1), eq((byte)1));
    }

    @Test
    public void testRespondToPairingRequest() {
        when(mWifiAwareNativeManagerMock.getSupplicantNanIface()).thenReturn(null);
        mDut.respondToPairingRequest((short) 1, 123, true, null, true, 1, null, null, 1, 1,
		(byte)1, null);
        verify(mWifiNanIfaceMock).respondToPairingRequest(eq((short) 1), eq(123), eq(true),
                eq(null), eq(true), eq(1), eq(null), eq(null), eq(1), eq(1));
    }

    @Test
    public void testRespondToPairingRequestWithSupplicant() {
        mDut.respondToPairingRequest((short) 1, 123, true, null, true, 1, null, null, 1, 1,
		(byte)1, null);
        verify(mAwareIfaceAidlSupplicantImplMock).respondToPairingRequest(eq((short) 1), eq(123),
                eq(true), eq(null), eq(true), eq(1), eq(null), eq(null), eq(1), eq(1),
		eq((byte)1), eq(null));
    }

    @Test
    public void testInitiateBootstrappingWithSupplicant() {
        byte[] peer = new byte[]{1, 2, 3, 4, 5, 6};
        mDut.initiateBootstrapping((short) 1, 123, peer, 1, null, (byte) 1, true, null);
        verify(mAwareIfaceAidlSupplicantImplMock).initiateNanBootstrappingRequest(eq((short) 1),
                eq(123), eq(MacAddress.fromBytes(peer)), eq(1), eq(null), eq((byte) 1), eq(true),
                eq(null));
    }

    @Test
    public void testRespondToBootstrappingRequest() {
        when(mWifiAwareNativeManagerMock.getSupplicantNanIface()).thenReturn(null);
        mDut.respondToBootstrappingRequest((short) 1, 123, true, (byte) 1, 1, null);
        verify(mWifiNanIfaceMock).respondToBootstrappingRequest(eq((short) 1), eq(123), eq(true),
                eq((byte) 1), eq(1));
    }

    @Test
    public void testRespondToBootstrappingRequestWithSupplicant() {
        mDut.respondToBootstrappingRequest((short) 1, 123, true, (byte) 1, 1, null);
        verify(mAwareIfaceAidlSupplicantImplMock).respondToNanBootstrappingRequest(eq((short) 1),
                eq(123), eq(true), eq((byte) 1), eq(1), eq(null));
    }

    @Test
    public void testSuspendRequest() {
        when(mWifiAwareNativeManagerMock.getSupplicantNanIface()).thenReturn(null);
        mDut.suspendRequest((short) 1, (byte) 1);
        verify(mWifiNanIfaceMock).suspendRequest(eq((short) 1), eq((byte) 1));
    }

    @Test
    public void testResumeRequest() {
        when(mWifiAwareNativeManagerMock.getSupplicantNanIface()).thenReturn(null);
        mDut.resumeRequest((short) 1, (byte) 1);
        verify(mWifiNanIfaceMock).resumeRequest(eq((short) 1), eq((byte) 1));
    }
}

