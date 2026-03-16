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

package com.android.server.wifi.aware;

import static android.net.wifi.aware.AwarePairingConfig.PAIRING_BOOTSTRAPPING_OPPORTUNISTIC;
import static android.net.wifi.aware.Characteristics.WIFI_AWARE_CIPHER_SUITE_NCS_PK_PASN_128;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyByte;
import static org.mockito.ArgumentMatchers.anyChar;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.net.MacAddress;
import android.net.wifi.aware.AwarePairingConfig;
import android.net.wifi.aware.Characteristics;
import android.net.wifi.aware.ConfigRequest;
import android.net.wifi.aware.PublishConfig;
import android.net.wifi.aware.SubscribeConfig;
import android.net.wifi.aware.TlvBufferUtils;
import android.net.wifi.aware.WifiAwareDataPathSecurityConfig;
import android.os.RemoteException;
import android.os.ServiceSpecificException;
import android.system.wifi.mainline_supplicant.ISupplicantNanIface;
import android.system.wifi.mainline_supplicant.NanBootstrappingMethod;
import android.system.wifi.mainline_supplicant.NanBootstrappingRequest;
import android.system.wifi.mainline_supplicant.NanBootstrappingResponse;
import android.system.wifi.mainline_supplicant.NanCipherSuiteType;
import android.system.wifi.mainline_supplicant.NanConfigRequest;
import android.system.wifi.mainline_supplicant.NanDataPathSecurityConfig;
import android.system.wifi.mainline_supplicant.NanEnableRequest;
import android.system.wifi.mainline_supplicant.NanInitiateDataPathRequest;
import android.system.wifi.mainline_supplicant.NanPairingRequest;
import android.system.wifi.mainline_supplicant.NanPublishRequest;
import android.system.wifi.mainline_supplicant.NanRangingIndication;
import android.system.wifi.mainline_supplicant.NanRespondToDataPathIndicationRequest;
import android.system.wifi.mainline_supplicant.NanRespondToPairingIndicationRequest;
import android.system.wifi.mainline_supplicant.NanSubscribeRequest;
import android.system.wifi.mainline_supplicant.NanTransmitFollowupRequest;

import androidx.test.filters.SmallTest;

import com.android.modules.utils.build.SdkLevel;
import com.android.server.wifi.WifiBaseTest;
import com.android.server.wifi.hal.WifiNanIface;
import com.android.server.wifi.hal.WifiNanIface.PowerParameters;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Unit test harness for AwareIfaceAidlSupplicantImpl.
 */
@SmallTest
public class AwareIfaceAidlSupplicantImplTest extends WifiBaseTest {
    private static final String IFACE_NAME = "aware0";
    @Mock
    private ISupplicantNanIface mMockSupplicantNanIface;
    @Mock
    private WifiNanIface.Callback mMockFrameworkCallback;

    private AwareIfaceAidlSupplicantImpl mDut;
    private byte[] mPeerNik = "6789012345678901".getBytes();

    @Before
    public void setup() throws Exception {
        MockitoAnnotations.initMocks(this);
        mDut = new AwareIfaceAidlSupplicantImpl(mMockSupplicantNanIface);
        mDut.enableVerboseLogging(true);
        when(mMockSupplicantNanIface.getName()).thenReturn(IFACE_NAME);
    }

    @Test
    public void testRegisterFrameworkCallback() throws Exception {
        assertTrue(mDut.registerFrameworkCallback(mMockFrameworkCallback));
        verify(mMockSupplicantNanIface).registerEventCallback(any());
    }

    @Test
    public void testRegisterFrameworkCallback_remoteException() throws Exception {
        doThrow(new RemoteException()).when(mMockSupplicantNanIface).registerEventCallback(any());
        assertFalse(mDut.registerFrameworkCallback(mMockFrameworkCallback));
    }

    @Test
    public void testRegisterFrameworkCallback_serviceSpecificException() throws Exception {
        doThrow(new ServiceSpecificException(0, "error"))
                .when(mMockSupplicantNanIface).registerEventCallback(any());
        assertFalse(mDut.registerFrameworkCallback(mMockFrameworkCallback));
    }

    @Test
    public void testGetName() throws Exception {
        assertEquals(IFACE_NAME, mDut.getName());
        verify(mMockSupplicantNanIface).getName();
    }

    @Test
    public void testGetName_remoteException() throws Exception {
        doThrow(new RemoteException()).when(mMockSupplicantNanIface).getName();
        assertNull(mDut.getName());
    }

    @Test
    public void testGetName_serviceSpecificException() throws Exception {
        doThrow(new ServiceSpecificException(0, "error")).when(mMockSupplicantNanIface).getName();
        assertNull(mDut.getName());
    }

    @Test
    public void testGetCapabilities() throws Exception {
        short transactionId = 12;
        assertTrue(mDut.getCapabilities(transactionId));
        verify(mMockSupplicantNanIface).getCapabilitiesRequest((char) transactionId);
    }

    @Test
    public void testGetCapabilities_remoteException() throws Exception {
        short transactionId = 13;
        doThrow(new RemoteException()).when(mMockSupplicantNanIface).getCapabilitiesRequest(
                (char) transactionId);
        assertFalse(mDut.getCapabilities(transactionId));
    }

    @Test
    public void testGetCapabilities_serviceSpecificException() throws Exception {
        short transactionId = 14;
        doThrow(new ServiceSpecificException(0, "error")).when(
                mMockSupplicantNanIface).getCapabilitiesRequest((char) transactionId);
        assertFalse(mDut.getCapabilities(transactionId));
    }

    @Test
    public void testEnableAndConfigure_initial() throws Exception {
        short transactionId = 15;
        ConfigRequest configRequest = new ConfigRequest.Builder().build();
        PowerParameters powerParameters = new PowerParameters();

        assertTrue(mDut.enableAndConfigure(transactionId, configRequest, true,
                powerParameters));

        verify(mMockSupplicantNanIface).enableRequest(eq((char) transactionId),
                any(NanEnableRequest.class), any(NanConfigRequest.class));
    }

    @Test
    public void testEnableAndConfigure_notInitial() throws Exception {
        short transactionId = 16;
        ConfigRequest configRequest = new ConfigRequest.Builder().build();
        PowerParameters powerParameters = new PowerParameters();

        assertTrue(mDut.enableAndConfigure(transactionId, configRequest, false,
                powerParameters));

        verify(mMockSupplicantNanIface).configRequest(eq((char) transactionId),
                any(NanConfigRequest.class));
    }

    @Test
    public void testEnableAndConfigure_remoteException() throws Exception {
        short transactionId = 17;
        ConfigRequest configRequest = new ConfigRequest.Builder().build();
        PowerParameters powerParameters = new PowerParameters();

        doThrow(new RemoteException()).when(mMockSupplicantNanIface).configRequest(anyChar(),
                any());

        assertFalse(mDut.enableAndConfigure(transactionId, configRequest, false,
                powerParameters));
    }

    @Test
    public void testEnableAndConfigure_serviceSpecificException() throws Exception {
        short transactionId = 18;
        ConfigRequest configRequest = new ConfigRequest.Builder().build();
        PowerParameters powerParameters = new PowerParameters();

        doThrow(new ServiceSpecificException(0, "error")).when(mMockSupplicantNanIface)
                .configRequest(anyChar(), any());

        assertFalse(mDut.enableAndConfigure(transactionId, configRequest, false,
                powerParameters));
    }

    @Test
    public void testCreateAwareNetworkInterface() throws Exception {
        short transactionId = 20;
        String interfaceName = "aware_data0";
        assertTrue(mDut.createAwareNetworkInterface(transactionId, interfaceName));
        ArgumentCaptor<byte[]> captor = ArgumentCaptor.forClass(byte[].class);
        verify(mMockSupplicantNanIface).createDataInterfaceRequest(eq((char) transactionId),
                eq(interfaceName), captor.capture());
        assertArrayEquals(captor.getValue(), mDut.getNdiMacAddress(interfaceName));
    }

    @Test
    public void testCreateAwareNetworkInterface_remoteException() throws Exception {
        short transactionId = 21;
        String interfaceName = "aware_data0";
        doThrow(new RemoteException()).when(mMockSupplicantNanIface)
                .createDataInterfaceRequest(anyChar(), anyString(), any());
        assertFalse(mDut.createAwareNetworkInterface(transactionId, interfaceName));
    }

    @Test
    public void testCreateAwareNetworkInterface_serviceSpecificException() throws Exception {
        short transactionId = 22;
        String interfaceName = "aware_data0";
        doThrow(new ServiceSpecificException(0, "error")).when(mMockSupplicantNanIface)
                .createDataInterfaceRequest(anyChar(), anyString(), any());
        assertFalse(mDut.createAwareNetworkInterface(transactionId, interfaceName));
    }

    @Test
    public void testDeleteAwareNetworkInterface() throws Exception {
        short transactionId = 30;
        String interfaceName = "aware_data0";
        assertTrue(mDut.deleteAwareNetworkInterface(transactionId, interfaceName));
        verify(mMockSupplicantNanIface).deleteDataInterfaceRequest((char) transactionId,
                interfaceName);
    }

    @Test
    public void testDeleteAwareNetworkInterface_remoteException() throws Exception {
        short transactionId = 31;
        String interfaceName = "aware_data0";
        doThrow(new RemoteException()).when(mMockSupplicantNanIface)
                .deleteDataInterfaceRequest(anyChar(), anyString());
        assertFalse(mDut.deleteAwareNetworkInterface(transactionId, interfaceName));
    }

    @Test
    public void testDeleteAwareNetworkInterface_serviceSpecificException() throws Exception {
        short transactionId = 32;
        String interfaceName = "aware_data0";
        doThrow(new ServiceSpecificException(0, "error")).when(mMockSupplicantNanIface)
                .deleteDataInterfaceRequest(anyChar(), anyString());
        assertFalse(mDut.deleteAwareNetworkInterface(transactionId, interfaceName));
    }

    @Test
    public void testDisableRequest() throws Exception {
        short transactionId = 65;
        assertTrue(mDut.disableRequest(transactionId));
        verify(mMockSupplicantNanIface).disableRequest((char) transactionId);
    }

    @Test
    public void testDisableRequest_remoteException() throws Exception {
        short transactionId = 66;
        doThrow(new RemoteException()).when(mMockSupplicantNanIface).disableRequest(anyChar());
        assertFalse(mDut.disableRequest(transactionId));
    }

    @Test
    public void testDisableRequest_serviceSpecificException() throws Exception {
        short transactionId = 67;
        doThrow(new ServiceSpecificException(0, "error"))
                .when(mMockSupplicantNanIface).disableRequest(anyChar());
        assertFalse(mDut.disableRequest(transactionId));
    }

    @Test
    public void testPublish() throws Exception {
        short transactionId = 40;
        byte publishId = 1;
        PublishConfig publishConfig = new PublishConfig.Builder()
                .setServiceName("test-service")
                .build();
        assertTrue(mDut.publish(transactionId, publishId, publishConfig, null));
        verify(mMockSupplicantNanIface).startPublishRequest(eq((char) transactionId),
                any(NanPublishRequest.class));
    }

    @Test
    public void testPublish_remoteException() throws Exception {
        short transactionId = 41;
        byte publishId = 1;
        PublishConfig publishConfig = new PublishConfig.Builder()
                .setServiceName("test-service")
                .build();
        doThrow(new RemoteException()).when(mMockSupplicantNanIface)
                .startPublishRequest(anyChar(), any());
        assertFalse(mDut.publish(transactionId, publishId, publishConfig, null));
    }

    @Test
    public void testPublish_serviceSpecificException() throws Exception {
        short transactionId = 42;
        byte publishId = 1;
        PublishConfig publishConfig = new PublishConfig.Builder()
                .setServiceName("test-service")
                .build();
        doThrow(new ServiceSpecificException(0, "error")).when(mMockSupplicantNanIface)
                .startPublishRequest(anyChar(), any());
        assertFalse(mDut.publish(transactionId, publishId, publishConfig, null));
    }

    @Test
    public void testSubscribe() throws Exception {
        short transactionId = 50;
        byte subscribeId = 2;
        SubscribeConfig subscribeConfig = new SubscribeConfig.Builder()
                .setServiceName("test-service")
                .build();
        assertTrue(mDut.subscribe(transactionId, subscribeId, subscribeConfig, null));
        verify(mMockSupplicantNanIface).startSubscribeRequest(eq((char) transactionId),
                any(NanSubscribeRequest.class));
    }

    @Test
    public void testSubscribe_remoteException() throws Exception {
        short transactionId = 51;
        byte subscribeId = 2;
        SubscribeConfig subscribeConfig = new SubscribeConfig.Builder()
                .setServiceName("test-service")
                .build();
        doThrow(new RemoteException()).when(mMockSupplicantNanIface)
                .startSubscribeRequest(anyChar(), any());
        assertFalse(mDut.subscribe(transactionId, subscribeId, subscribeConfig, null));
    }

    @Test
    public void testSubscribe_serviceSpecificException() throws Exception {
        short transactionId = 52;
        byte subscribeId = 2;
        SubscribeConfig subscribeConfig = new SubscribeConfig.Builder()
                .setServiceName("test-service")
                .build();
        doThrow(new ServiceSpecificException(0, "error")).when(mMockSupplicantNanIface)
                .startSubscribeRequest(anyChar(), any());
        assertFalse(mDut.subscribe(transactionId, subscribeId, subscribeConfig, null));
    }

    @Test
    public void testStopPublish() throws Exception {
        short transactionId = 60;
        byte publishId = 1;
        assertTrue(mDut.stopPublish(transactionId, publishId));
        verify(mMockSupplicantNanIface).stopPublishRequest((char) transactionId, publishId);
    }

    @Test
    public void testStopPublish_remoteException() throws Exception {
        short transactionId = 61;
        byte publishId = 1;
        doThrow(new RemoteException()).when(mMockSupplicantNanIface)
                .stopPublishRequest(anyChar(), anyByte());
        assertFalse(mDut.stopPublish(transactionId, publishId));
    }

    @Test
    public void testStopPublish_serviceSpecificException() throws Exception {
        short transactionId = 62;
        byte publishId = 1;
        doThrow(new ServiceSpecificException(0, "error")).when(mMockSupplicantNanIface)
                .stopPublishRequest(anyChar(), anyByte());
        assertFalse(mDut.stopPublish(transactionId, publishId));
    }

    @Test
    public void testStopSubscribe() throws Exception {
        short transactionId = 70;
        byte subscribeId = 2;
        assertTrue(mDut.stopSubscribe(transactionId, subscribeId));
        verify(mMockSupplicantNanIface).stopSubscribeRequest((char) transactionId, subscribeId);
    }

    @Test
    public void testStopSubscribe_remoteException() throws Exception {
        short transactionId = 71;
        byte subscribeId = 2;
        doThrow(new RemoteException()).when(mMockSupplicantNanIface)
                .stopSubscribeRequest(anyChar(), anyByte());
        assertFalse(mDut.stopSubscribe(transactionId, subscribeId));
    }

    @Test
    public void testStopSubscribe_serviceSpecificException() throws Exception {
        short transactionId = 72;
        byte subscribeId = 2;
        doThrow(new ServiceSpecificException(0, "error")).when(mMockSupplicantNanIface)
                .stopSubscribeRequest(anyChar(), anyByte());
        assertFalse(mDut.stopSubscribe(transactionId, subscribeId));
    }

    @Test
    public void testSendMessage() throws Exception {
        short transactionId = 80;
        byte pubSubId = 1;
        int requesterInstanceId = 123;
        MacAddress dest = MacAddress.fromString("00:11:22:33:44:55");
        byte[] message = "hello".getBytes();
        assertTrue(mDut.sendMessage(transactionId, pubSubId, requesterInstanceId, dest, message));
        verify(mMockSupplicantNanIface).transmitFollowupRequest(eq((char) transactionId),
                any(NanTransmitFollowupRequest.class));
    }

    @Test
    public void testSendMessage_remoteException() throws Exception {
        short transactionId = 81;
        byte pubSubId = 1;
        int requesterInstanceId = 123;
        MacAddress dest = MacAddress.fromString("00:11:22:33:44:55");
        byte[] message = "hello".getBytes();
        doThrow(new RemoteException()).when(mMockSupplicantNanIface)
                .transmitFollowupRequest(anyChar(), any());
        assertFalse(
                mDut.sendMessage(transactionId, pubSubId, requesterInstanceId, dest, message));
    }

    @Test
    public void testSendMessage_serviceSpecificException() throws Exception {
        short transactionId = 82;
        byte pubSubId = 1;
        int requesterInstanceId = 123;
        MacAddress dest = MacAddress.fromString("00:11:22:33:44:55");
        byte[] message = "hello".getBytes();
        doThrow(new ServiceSpecificException(0, "error")).when(mMockSupplicantNanIface)
                .transmitFollowupRequest(anyChar(), any());
        assertFalse(
                mDut.sendMessage(transactionId, pubSubId, requesterInstanceId, dest, message));
    }

    @Test
    public void testInitiateDataPath() throws Exception {
        short transactionId = 90;
        int peerId = 100;
        int channelRequestType = 0;
        int channel = 2437;
        MacAddress peer = MacAddress.fromString("00:11:22:33:44:55");
        String interfaceName = "aware_data0";
        boolean isOutOfBand = true;
        byte[] appInfo = "appInfo".getBytes();
        WifiAwareDataPathSecurityConfig securityConfig = null;
        byte pubSubId = 1;
        boolean frameProtectionEnabled = false;

        assertTrue(mDut.initiateDataPath(transactionId, peerId, channelRequestType, channel, peer,
                interfaceName, isOutOfBand, appInfo, securityConfig, pubSubId,
                frameProtectionEnabled));
        verify(mMockSupplicantNanIface).initiateDataPathRequest(eq((char) transactionId),
                any(NanInitiateDataPathRequest.class));
    }

    @Test
    public void testInitiateDataPath_remoteException() throws Exception {
        short transactionId = 91;
        int peerId = 100;
        int channelRequestType = 0;
        int channel = 2437;
        MacAddress peer = MacAddress.fromString("00:11:22:33:44:55");
        String interfaceName = "aware_data0";
        boolean isOutOfBand = true;
        byte[] appInfo = "appInfo".getBytes();
        WifiAwareDataPathSecurityConfig securityConfig = null;
        byte pubSubId = 1;
        boolean frameProtectionEnabled = false;

        doThrow(new RemoteException()).when(mMockSupplicantNanIface)
                .initiateDataPathRequest(anyChar(), any());
        assertFalse(mDut.initiateDataPath(transactionId, peerId, channelRequestType, channel, peer,
                interfaceName, isOutOfBand, appInfo, securityConfig, pubSubId,
                frameProtectionEnabled));
    }

    @Test
    public void testInitiateDataPath_serviceSpecificException() throws Exception {
        short transactionId = 92;
        int peerId = 100;
        int channelRequestType = 0;
        int channel = 2437;
        MacAddress peer = MacAddress.fromString("00:11:22:33:44:55");
        String interfaceName = "aware_data0";
        boolean isOutOfBand = true;
        byte[] appInfo = "appInfo".getBytes();
        WifiAwareDataPathSecurityConfig securityConfig = null;
        byte pubSubId = 1;
        boolean frameProtectionEnabled = false;

        doThrow(new ServiceSpecificException(0, "error")).when(mMockSupplicantNanIface)
                .initiateDataPathRequest(anyChar(), any());
        assertFalse(mDut.initiateDataPath(transactionId, peerId, channelRequestType, channel, peer,
                interfaceName, isOutOfBand, appInfo, securityConfig, pubSubId,
                frameProtectionEnabled));
    }

    @Test
    public void testRespondToDataPathRequest() throws Exception {
        short transactionId = 100;
        boolean accept = true;
        int ndpId = 200;
        String interfaceName = "aware_data0";
        byte[] appInfo = "appInfo".getBytes();
        boolean isOutOfBand = true;
        WifiAwareDataPathSecurityConfig securityConfig = null;
        byte pubSubId = 1;
        boolean frameProtectionEnabled = false;

        assertTrue(mDut.respondToDataPathRequest(transactionId, accept, ndpId, interfaceName,
                appInfo, isOutOfBand, securityConfig, pubSubId, frameProtectionEnabled,
		null, null));
        verify(mMockSupplicantNanIface).respondToDataPathIndicationRequest(
                eq((char) transactionId), any(NanRespondToDataPathIndicationRequest.class));
    }

    @Test
    public void testRespondToDataPathRequest_remoteException() throws Exception {
        short transactionId = 101;
        boolean accept = true;
        int ndpId = 200;
        String interfaceName = "aware_data0";
        byte[] appInfo = "appInfo".getBytes();
        boolean isOutOfBand = true;
        WifiAwareDataPathSecurityConfig securityConfig = null;
        byte pubSubId = 1;
        boolean frameProtectionEnabled = false;

        doThrow(new RemoteException()).when(mMockSupplicantNanIface)
                .respondToDataPathIndicationRequest(anyChar(), any());
        assertFalse(mDut.respondToDataPathRequest(transactionId, accept, ndpId, interfaceName,
                appInfo, isOutOfBand, securityConfig, pubSubId, frameProtectionEnabled,
		null, null));
    }

    @Test
    public void testRespondToDataPathRequest_serviceSpecificException() throws Exception {
        short transactionId = 102;
        boolean accept = true;
        int ndpId = 200;
        String interfaceName = "aware_data0";
        byte[] appInfo = "appInfo".getBytes();
        boolean isOutOfBand = true;
        WifiAwareDataPathSecurityConfig securityConfig = null;
        byte pubSubId = 1;
        boolean frameProtectionEnabled = false;

        doThrow(new ServiceSpecificException(0, "error")).when(mMockSupplicantNanIface)
                .respondToDataPathIndicationRequest(anyChar(), any());
        assertFalse(mDut.respondToDataPathRequest(transactionId, accept, ndpId, interfaceName,
                appInfo, isOutOfBand, securityConfig, pubSubId, frameProtectionEnabled,
		null, null));
    }

    @Test
    public void testEndDataPath() throws Exception {
        short transactionId = 110;
        int ndpId = 200;
        byte[] addr = new byte[6];
        byte[] initMac = new byte[6];


        assertTrue(mDut.endDataPath(transactionId, ndpId, addr, initMac));
        verify(mMockSupplicantNanIface)
                .terminateDataPathRequest((char) transactionId, ndpId, addr, initMac);
    }

    @Test
    public void testEndDataPath_remoteException() throws Exception {
        short transactionId = 111;
        int ndpId = 200;
        byte[] addr = new byte[6];
        byte[] initMac = new byte[6];


        doThrow(new RemoteException()).when(mMockSupplicantNanIface)
                .terminateDataPathRequest(anyChar(), anyInt(), any(), any());
        assertFalse(mDut.endDataPath(transactionId, ndpId, addr, initMac));
    }

    @Test
    public void testEndDataPath_serviceSpecificException() throws Exception {
        short transactionId = 112;
        int ndpId = 200;
        byte[] addr = new byte[6];
        byte[] initMac = new byte[6];

        doThrow(new ServiceSpecificException(0, "error")).when(mMockSupplicantNanIface)
                .terminateDataPathRequest(anyChar(), anyInt(), any(), any());
        assertFalse(mDut.endDataPath(transactionId, ndpId, addr, initMac));
    }

    @Test
    public void testRespondToPairingRequest() throws Exception {
        short transactionId = 120;
        int pairingId = 300;
        boolean accept = true;
        byte[] pairingIdentityKey = new byte[16];
        boolean enablePairingCache = true;
        int requestType = 0;
        byte[] pmk = new byte[32];
        String password = "password";
        int akm = 0;
        int cipherSuite = 0;
        byte[] mac = new byte[6];
        byte pubSubId = 1;

        assertTrue(mDut.respondToPairingRequest(transactionId, pairingId, accept,
                pairingIdentityKey, enablePairingCache, requestType, pmk, password, akm,
                cipherSuite, pubSubId, mac, mPeerNik));
        verify(mMockSupplicantNanIface).respondToPairingIndicationRequest(
                eq((char) transactionId), any(NanRespondToPairingIndicationRequest.class));
    }

    @Test
    public void testRespondToPairingRequest_remoteException() throws Exception {
        short transactionId = 121;
        int pairingId = 300;
        boolean accept = true;
        byte[] pairingIdentityKey = new byte[16];
        boolean enablePairingCache = true;
        int requestType = 0;
        byte[] pmk = new byte[32];
        String password = "password";
        int akm = 0;
        int cipherSuite = 0;
        byte[] mac = new byte[6];
        byte pubSubId = 1;

        doThrow(new RemoteException()).when(mMockSupplicantNanIface)
                .respondToPairingIndicationRequest(anyChar(), any());
        assertFalse(mDut.respondToPairingRequest(transactionId, pairingId, accept,
                pairingIdentityKey, enablePairingCache, requestType, pmk, password, akm,
                cipherSuite, pubSubId, mac, mPeerNik));
    }

    @Test
    public void testRespondToPairingRequest_serviceSpecificException() throws Exception {
        short transactionId = 122;
        int pairingId = 300;
        boolean accept = true;
        byte[] pairingIdentityKey = new byte[16];
        boolean enablePairingCache = true;
        int requestType = 0;
        byte[] pmk = new byte[32];
        String password = "password";
        int akm = 0;
        int cipherSuite = 0;
        byte[] mac = new byte[6];
        byte pubSubId = 1;

        doThrow(new ServiceSpecificException(0, "error")).when(mMockSupplicantNanIface)
                .respondToPairingIndicationRequest(anyChar(), any());
        assertFalse(mDut.respondToPairingRequest(transactionId, pairingId, accept,
                pairingIdentityKey, enablePairingCache, requestType, pmk, password, akm,
                cipherSuite, pubSubId, mac, mPeerNik));
    }

    @Test
    public void testInitiateNanPairingRequest() throws Exception {
        short transactionId = 130;
        int peerId = 100;
        MacAddress peer = MacAddress.fromString("00:11:22:33:44:55");
        byte[] pairingIdentityKey = new byte[16];
        boolean enablePairingCache = true;
        int requestType = 0;
        byte[] pmk = new byte[32];
        String password = "password";
        int akm = 0;
        int cipherSuite = 0;
        byte pubSubId = 1;

        assertTrue(mDut.initiateNanPairingRequest(transactionId, peerId, peer, pairingIdentityKey,
                enablePairingCache, requestType, pmk, password, akm, cipherSuite, pubSubId,
                mPeerNik));
        verify(mMockSupplicantNanIface).initiatePairingRequest(eq((char) transactionId),
                any(NanPairingRequest.class));
    }

    @Test
    public void testInitiateNanPairingRequest_remoteException() throws Exception {
        short transactionId = 131;
        int peerId = 100;
        MacAddress peer = MacAddress.fromString("00:11:22:33:44:55");
        byte[] pairingIdentityKey = new byte[16];
        boolean enablePairingCache = true;
        int requestType = 0;
        byte[] pmk = new byte[32];
        String password = "password";
        int akm = 0;
        int cipherSuite = 0;
        byte pubSubId = 1;

        doThrow(new RemoteException()).when(mMockSupplicantNanIface)
                .initiatePairingRequest(anyChar(), any());
        assertFalse(mDut.initiateNanPairingRequest(transactionId, peerId, peer, pairingIdentityKey,
                enablePairingCache, requestType, pmk, password, akm, cipherSuite, pubSubId,
                mPeerNik));
    }

    @Test
    public void testInitiateNanPairingRequest_serviceSpecificException() throws Exception {
        short transactionId = 132;
        int peerId = 100;
        MacAddress peer = MacAddress.fromString("00:11:22:33:44:55");
        byte[] pairingIdentityKey = new byte[16];
        boolean enablePairingCache = true;
        int requestType = 0;
        byte[] pmk = new byte[32];
        String password = "password";
        int akm = 0;
        int cipherSuite = 0;
        byte pubSubId = 1;

        doThrow(new ServiceSpecificException(0, "error")).when(mMockSupplicantNanIface)
                .initiatePairingRequest(anyChar(), any());
        assertFalse(mDut.initiateNanPairingRequest(transactionId, peerId, peer, pairingIdentityKey,
                enablePairingCache, requestType, pmk, password, akm, cipherSuite, pubSubId,
                mPeerNik));
    }

    @Test
    public void testEndPairing() throws Exception {
        short transactionId = 140;
        int pairingId = 300;
        byte[] addr = new byte[6];

        assertTrue(mDut.endPairing(transactionId, pairingId));
        verify(mMockSupplicantNanIface).terminatePairingRequest(
                (char) transactionId, pairingId, addr);
    }

    @Test
    public void testEndPairing_remoteException() throws Exception {
        short transactionId = 141;
        int pairingId = 300;

        doThrow(new RemoteException()).when(mMockSupplicantNanIface)
                .terminatePairingRequest(anyChar(), anyInt(), any());
        assertFalse(mDut.endPairing(transactionId, pairingId));
    }

    @Test
    public void testEndPairing_serviceSpecificException() throws Exception {
        short transactionId = 142;
        int pairingId = 300;

        doThrow(new ServiceSpecificException(0, "error")).when(mMockSupplicantNanIface)
                .terminatePairingRequest(anyChar(), anyInt(), any());
        assertFalse(mDut.endPairing(transactionId, pairingId));
    }

    @Test
    public void testInitiateNanBootstrappingRequest() throws Exception {
        short transactionId = 150;
        int peerId = 100;
        MacAddress peer = MacAddress.fromString("00:11:22:33:44:55");
        int method = 0;
        byte[] cookie = new byte[0];
        byte pubSubId = 1;
        boolean isComeBack = false;
        byte[] ssi = new byte[0];

        assertTrue(mDut.initiateNanBootstrappingRequest(transactionId, peerId, peer, method,
                cookie, pubSubId, isComeBack, ssi));
        verify(mMockSupplicantNanIface).initiateBootstrappingRequest(eq((char) transactionId),
                any(NanBootstrappingRequest.class));
    }

    @Test
    public void testInitiateNanBootstrappingRequest_remoteException() throws Exception {
        short transactionId = 151;
        int peerId = 100;
        MacAddress peer = MacAddress.fromString("00:11:22:33:44:55");
        int method = 0;
        byte[] cookie = new byte[0];
        byte pubSubId = 1;
        boolean isComeBack = false;
        byte[] ssi = new byte[0];

        doThrow(new RemoteException()).when(mMockSupplicantNanIface)
                .initiateBootstrappingRequest(anyChar(), any());
        assertFalse(mDut.initiateNanBootstrappingRequest(transactionId, peerId, peer, method,
                cookie, pubSubId, isComeBack, ssi));
    }

    @Test
    public void testInitiateNanBootstrappingRequest_serviceSpecificException() throws Exception {
        short transactionId = 152;
        int peerId = 100;
        MacAddress peer = MacAddress.fromString("00:11:22:33:44:55");
        int method = 0;
        byte[] cookie = new byte[0];
        byte pubSubId = 1;
        boolean isComeBack = false;
        byte[] ssi = new byte[0];

        doThrow(new ServiceSpecificException(0, "error")).when(mMockSupplicantNanIface)
                .initiateBootstrappingRequest(anyChar(), any());
        assertFalse(mDut.initiateNanBootstrappingRequest(transactionId, peerId, peer, method,
                cookie, pubSubId, isComeBack, ssi));
    }

    @Test
    public void testRespondToNanBootstrappingRequest() throws Exception {
        short transactionId = 160;
        int bootstrappingId = 400;
        boolean accept = true;
        byte pubSubId = 1;
        int method = 0;
        MacAddress peer = MacAddress.fromString("00:11:22:33:44:55");

        assertTrue(mDut.respondToNanBootstrappingRequest(transactionId, bootstrappingId, accept,
                pubSubId, method, peer.toByteArray()));
        verify(mMockSupplicantNanIface).respondToBootstrappingIndicationRequest(
                eq((char) transactionId), any(NanBootstrappingResponse.class));
    }

    @Test
    public void testRespondToNanBootstrappingRequest_remoteException() throws Exception {
        short transactionId = 161;
        int bootstrappingId = 400;
        boolean accept = true;
        byte pubSubId = 1;
        int method = 0;
        MacAddress peer = MacAddress.fromString("00:11:22:33:44:55");

        doThrow(new RemoteException()).when(mMockSupplicantNanIface)
                .respondToBootstrappingIndicationRequest(anyChar(), any());
        assertFalse(mDut.respondToNanBootstrappingRequest(transactionId, bootstrappingId, accept,
                pubSubId, method, peer.toByteArray()));
    }

    @Test
    public void testRespondToNanBootstrappingRequest_serviceSpecificException() throws Exception {
        short transactionId = 162;
        int bootstrappingId = 400;
        boolean accept = true;
        byte pubSubId = 1;
        int method = 0;
        MacAddress peer = MacAddress.fromString("00:11:22:33:44:55");

        doThrow(new ServiceSpecificException(0, "error")).when(mMockSupplicantNanIface)
                .respondToBootstrappingIndicationRequest(anyChar(), any());
        assertFalse(mDut.respondToNanBootstrappingRequest(transactionId, bootstrappingId, accept,
                pubSubId, method, peer.toByteArray()));
    }

    @Test
    public void testPublishWithPairingSettings() throws RemoteException {
        assumeTrue(SdkLevel.isAtLeastU());
        short tid = 250;
        byte pid = 34;
        byte[] ssi = "some service specific info".getBytes();
        AwarePairingConfig awarePairingConfig = new AwarePairingConfig.Builder()
                .setPairingCacheEnabled(true)
                .setPairingSetupEnabled(true)
                .setPairingVerificationEnabled(true)
                .setBootstrappingMethods(PAIRING_BOOTSTRAPPING_OPPORTUNISTIC)
                .setSupportedCipherSuites(WIFI_AWARE_CIPHER_SUITE_NCS_PK_PASN_128)
                .build();
        PublishConfig config = new PublishConfig.Builder()
                .setServiceName("XXX")
                .setPairingConfig(awarePairingConfig)
                .setServiceSpecificInfo(ssi)
                .build();
        ArgumentCaptor<NanPublishRequest> pubCaptor = ArgumentCaptor.forClass(
                NanPublishRequest.class);
        assertTrue(mDut.publish(tid, pid, config, null));
        verify(mMockSupplicantNanIface)
                .startPublishRequest(eq((char) tid), pubCaptor.capture());
        NanPublishRequest halPubReq = pubCaptor.getValue();
        assertEquals(NanDataPathSecurityConfig.NanDataPathSecurityType.PASSPHRASE,
                halPubReq.baseConfig.securityConfig.securityType);
        assertEquals(NanCipherSuiteType.PUBLIC_KEY_PASN_128_MASK,
                halPubReq.baseConfig.securityConfig.cipherType);
        assertTrue(halPubReq.pairingConfig.enablePairingSetup);
        assertTrue(halPubReq.pairingConfig.enablePairingCache);
        assertTrue(halPubReq.pairingConfig.enablePairingVerification);
        assertEquals(NanBootstrappingMethod.OPPORTUNISTIC_MASK,
                halPubReq.pairingConfig.supportedBootstrappingMethods);
        assertEquals(NanCipherSuiteType.PUBLIC_KEY_PASN_128_MASK,
                halPubReq.baseConfig.securityConfig.cipherType);
        assertTrue(halPubReq.baseConfig.securityConfig.requiresEnhancedFrameProtection);
        assertTrue(halPubReq.baseConfig.securityConfig.supportBigtksa);
        assertTrue(halPubReq.baseConfig.securityConfig.supportGtkAndIgtk);
        assertArrayEquals(ssi, halPubReq.baseConfig.extendedServiceSpecificInfo);
    }

    @Test
    public void testSubScribeWithPairingSettings() throws RemoteException {
        assumeTrue(SdkLevel.isAtLeastU());
        short tid = 250;
        byte pid = 34;
        byte[] ssi = "some service specific info".getBytes();
        AwarePairingConfig awarePairingConfig = new AwarePairingConfig.Builder()
                .setPairingCacheEnabled(true)
                .setPairingSetupEnabled(true)
                .setPairingVerificationEnabled(true)
                .setBootstrappingMethods(PAIRING_BOOTSTRAPPING_OPPORTUNISTIC)
                .setSupportedCipherSuites(WIFI_AWARE_CIPHER_SUITE_NCS_PK_PASN_128)
                .build();
        SubscribeConfig config = new SubscribeConfig.Builder()
                .setServiceName("XXX")
                .setPairingConfig(awarePairingConfig)
                .setServiceSpecificInfo(ssi)
                .build();
        ArgumentCaptor<NanSubscribeRequest> subCaptor = ArgumentCaptor.forClass(
                NanSubscribeRequest.class);
        assertTrue(mDut.subscribe(tid, pid, config, null));
        verify(mMockSupplicantNanIface)
                .startSubscribeRequest(eq((char) tid), subCaptor.capture());
        NanSubscribeRequest halSubReq = subCaptor.getValue();
        assertEquals(NanCipherSuiteType.PUBLIC_KEY_PASN_128_MASK,
                halSubReq.baseConfig.securityConfig.cipherType);
        assertTrue(halSubReq.pairingConfig.enablePairingSetup);
        assertTrue(halSubReq.pairingConfig.enablePairingCache);
        assertTrue(halSubReq.pairingConfig.enablePairingVerification);
        assertEquals(NanBootstrappingMethod.OPPORTUNISTIC_MASK,
                halSubReq.pairingConfig.supportedBootstrappingMethods);
        assertEquals(NanCipherSuiteType.PUBLIC_KEY_PASN_128_MASK,
                halSubReq.baseConfig.securityConfig.cipherType);
        assertTrue(halSubReq.baseConfig.securityConfig.requiresEnhancedFrameProtection);
        assertTrue(halSubReq.baseConfig.securityConfig.supportBigtksa);
        assertTrue(halSubReq.baseConfig.securityConfig.supportGtkAndIgtk);
        assertArrayEquals(ssi, halSubReq.baseConfig.extendedServiceSpecificInfo);
    }

    @Test
    public void testPublish_Unsolicited() throws Exception {
        short transactionId = 43;
        byte publishId = 1;
        final byte[] matchFilter = { 1, 16, 1, 22 };
        PublishConfig publishConfig = new PublishConfig.Builder()
                .setServiceName("test-service")
                .setPublishType(PublishConfig.PUBLISH_TYPE_UNSOLICITED)
                .setMatchFilter(new TlvBufferUtils.TlvIterable(0, 1, matchFilter).toList())
                .build();
        assertTrue(mDut.publish(transactionId, publishId, publishConfig, null));
        ArgumentCaptor<NanPublishRequest> captor = ArgumentCaptor.forClass(NanPublishRequest.class);
        verify(mMockSupplicantNanIface).startPublishRequest(eq((char) transactionId),
                captor.capture());
        NanPublishRequest req = captor.getValue();
        assertEquals(PublishConfig.PUBLISH_TYPE_UNSOLICITED, req.publishType);
        // Verify match filters set correctly for unsolicited
        assertTrue(req.baseConfig.txMatchFilter.length > 0);
        assertEquals(0, req.baseConfig.rxMatchFilter.length);
    }

    @Test
    public void testPublish_WithSecurity_Passphrase() throws Exception {
        short transactionId = 44;
        byte publishId = 1;
        WifiAwareDataPathSecurityConfig securityConfig = new WifiAwareDataPathSecurityConfig
                .Builder(Characteristics.WIFI_AWARE_CIPHER_SUITE_NCS_SK_128)
                .setPskPassphrase("12345678")
                .build();
        PublishConfig publishConfig = new PublishConfig.Builder()
                .setServiceName("test-service")
                .setDataPathSecurityConfig(securityConfig)
                .build();
        assertTrue(mDut.publish(transactionId, publishId, publishConfig, null));
        ArgumentCaptor<NanPublishRequest> captor = ArgumentCaptor.forClass(NanPublishRequest.class);
        verify(mMockSupplicantNanIface).startPublishRequest(eq((char) transactionId),
                captor.capture());
        NanPublishRequest req = captor.getValue();
        assertEquals(NanDataPathSecurityConfig.NanDataPathSecurityType.PASSPHRASE,
                req.baseConfig.securityConfig.securityType);
        assertArrayEquals("12345678".getBytes(), req.baseConfig.securityConfig.passphrase);
        assertEquals(NanCipherSuiteType.SHARED_KEY_128_MASK,
                req.baseConfig.securityConfig.cipherType);
    }

    @Test
    public void testSubscribe_Active() throws Exception {
        short transactionId = 53;
        byte subscribeId = 2;
        final byte[] matchFilter = { 1, 16, 1, 22 };
        SubscribeConfig subscribeConfig = new SubscribeConfig.Builder()
                .setServiceName("test-service")
                .setSubscribeType(SubscribeConfig.SUBSCRIBE_TYPE_ACTIVE)
                .setMatchFilter(new TlvBufferUtils.TlvIterable(0, 1, matchFilter).toList())
                .build();
        assertTrue(mDut.subscribe(transactionId, subscribeId, subscribeConfig, null));
        ArgumentCaptor<NanSubscribeRequest> captor =
                ArgumentCaptor.forClass(NanSubscribeRequest.class);
        verify(mMockSupplicantNanIface).startSubscribeRequest(eq((char) transactionId),
                captor.capture());
        NanSubscribeRequest req = captor.getValue();
        assertEquals(SubscribeConfig.SUBSCRIBE_TYPE_ACTIVE, req.subscribeType);
        assertTrue(req.baseConfig.txMatchFilter.length > 0);
        assertEquals(0, req.baseConfig.rxMatchFilter.length);
    }

    @Test
    public void testSubscribe_WithRanging() throws Exception {
        short transactionId = 54;
        byte subscribeId = 2;
        SubscribeConfig subscribeConfig = new SubscribeConfig.Builder()
                .setServiceName("test-service")
                .setMaxDistanceMm(1000)
                .setMinDistanceMm(100)
                .build();
        assertTrue(mDut.subscribe(transactionId, subscribeId, subscribeConfig, null));
        ArgumentCaptor<NanSubscribeRequest> captor =
                ArgumentCaptor.forClass(NanSubscribeRequest.class);
        verify(mMockSupplicantNanIface).startSubscribeRequest(eq((char) transactionId),
                captor.capture());
        NanSubscribeRequest req = captor.getValue();
        assertTrue(req.baseConfig.rangingRequired);
        assertEquals(100, req.baseConfig.distanceIngressCm); // 1000mm = 100cm
        assertEquals(10, req.baseConfig.distanceEgressCm); // 100mm = 10cm
        assertEquals(NanRangingIndication.INGRESS_MET_MASK | NanRangingIndication.EGRESS_MET_MASK,
                req.baseConfig.configRangingIndications);
    }

    @Test
    public void testInitiateDataPath_WithPassphrase() throws Exception {
        short transactionId = 93;
        int peerId = 100;
        int channelRequestType = 0;
        int channel = 2437;
        MacAddress peer = MacAddress.fromString("00:11:22:33:44:55");
        String interfaceName = "aware_data0";
        boolean isOutOfBand = true;
        byte[] appInfo = "appInfo".getBytes();
        WifiAwareDataPathSecurityConfig securityConfig = new WifiAwareDataPathSecurityConfig
                .Builder(Characteristics.WIFI_AWARE_CIPHER_SUITE_NCS_SK_128)
                .setPskPassphrase("12345678")
                .build();
        byte pubSubId = 1;
        boolean frameProtectionEnabled = false;

        assertTrue(mDut.initiateDataPath(transactionId, peerId, channelRequestType, channel, peer,
                interfaceName, isOutOfBand, appInfo, securityConfig, pubSubId,
                frameProtectionEnabled));
        ArgumentCaptor<NanInitiateDataPathRequest> captor =
                ArgumentCaptor.forClass(NanInitiateDataPathRequest.class);
        verify(mMockSupplicantNanIface).initiateDataPathRequest(eq((char) transactionId),
                captor.capture());
        NanInitiateDataPathRequest req = captor.getValue();
        assertEquals(NanDataPathSecurityConfig.NanDataPathSecurityType.PASSPHRASE,
                req.securityConfig.securityType);
        assertArrayEquals("12345678".getBytes(), req.securityConfig.passphrase);
        assertEquals(NanCipherSuiteType.SHARED_KEY_128_MASK, req.securityConfig.cipherType);
    }

    @Test
    public void testRespondToDataPathRequest_WithSecurity() throws Exception {
        short transactionId = 103;
        boolean accept = true;
        int ndpId = 200;
        String interfaceName = "aware_data0";
        byte[] appInfo = "appInfo".getBytes();
        boolean isOutOfBand = true;
        WifiAwareDataPathSecurityConfig securityConfig = new WifiAwareDataPathSecurityConfig
                .Builder(Characteristics.WIFI_AWARE_CIPHER_SUITE_NCS_SK_128)
                .setPskPassphrase("12345678")
                .build();
        byte pubSubId = 1;
        boolean frameProtectionEnabled = false;

        assertTrue(mDut.respondToDataPathRequest(transactionId, accept, ndpId, interfaceName,
                appInfo, isOutOfBand, securityConfig, pubSubId, frameProtectionEnabled,
		null, null));
        ArgumentCaptor<NanRespondToDataPathIndicationRequest> captor =
                ArgumentCaptor.forClass(NanRespondToDataPathIndicationRequest.class);
        verify(mMockSupplicantNanIface).respondToDataPathIndicationRequest(
                eq((char) transactionId), captor.capture());
        NanRespondToDataPathIndicationRequest req = captor.getValue();
        assertEquals(NanDataPathSecurityConfig.NanDataPathSecurityType.PASSPHRASE,
                req.securityConfig.securityType);
        assertArrayEquals("12345678".getBytes(), req.securityConfig.passphrase);
        assertEquals(NanCipherSuiteType.SHARED_KEY_128_MASK, req.securityConfig.cipherType);
    }
}
