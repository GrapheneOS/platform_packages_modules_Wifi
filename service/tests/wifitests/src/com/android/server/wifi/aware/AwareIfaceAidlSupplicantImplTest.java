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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.net.MacAddress;
import android.net.wifi.aware.AwarePairingConfig;
import android.net.wifi.aware.ConfigRequest;
import android.net.wifi.aware.PublishConfig;
import android.net.wifi.aware.SubscribeConfig;
import android.os.RemoteException;
import android.os.ServiceSpecificException;
import android.system.wifi.mainline_supplicant.ISupplicantNanIface;
import android.system.wifi.mainline_supplicant.NanBootstrappingMethod;
import android.system.wifi.mainline_supplicant.NanCipherSuiteType;
import android.system.wifi.mainline_supplicant.NanConfigRequest;
import android.system.wifi.mainline_supplicant.NanDataPathSecurityConfig;
import android.system.wifi.mainline_supplicant.NanEnableRequest;
import android.system.wifi.mainline_supplicant.NanPublishRequest;
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

        assertTrue(mDut.enableAndConfigure(transactionId, configRequest, true, true,
                powerParameters));

        verify(mMockSupplicantNanIface).enableRequest(eq((char) transactionId),
                any(NanEnableRequest.class), any(NanConfigRequest.class));
    }

    @Test
    public void testEnableAndConfigure_notInitial() throws Exception {
        short transactionId = 16;
        ConfigRequest configRequest = new ConfigRequest.Builder().build();
        PowerParameters powerParameters = new PowerParameters();

        assertTrue(mDut.enableAndConfigure(transactionId, configRequest, false, false,
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

        assertFalse(mDut.enableAndConfigure(transactionId, configRequest, false, false,
                powerParameters));
    }

    @Test
    public void testEnableAndConfigure_serviceSpecificException() throws Exception {
        short transactionId = 18;
        ConfigRequest configRequest = new ConfigRequest.Builder().build();
        PowerParameters powerParameters = new PowerParameters();

        doThrow(new ServiceSpecificException(0, "error")).when(mMockSupplicantNanIface)
                .configRequest(anyChar(), any());

        assertFalse(mDut.enableAndConfigure(transactionId, configRequest, false, false,
                powerParameters));
    }

    @Test
    public void testCreateAwareNetworkInterface() throws Exception {
        short transactionId = 20;
        String interfaceName = "aware_data0";
        assertTrue(mDut.createAwareNetworkInterface(transactionId, interfaceName));
        verify(mMockSupplicantNanIface).createDataInterfaceRequest((char) transactionId,
                interfaceName);
    }

    @Test
    public void testCreateAwareNetworkInterface_remoteException() throws Exception {
        short transactionId = 21;
        String interfaceName = "aware_data0";
        doThrow(new RemoteException()).when(mMockSupplicantNanIface)
                .createDataInterfaceRequest(anyChar(), anyString());
        assertFalse(mDut.createAwareNetworkInterface(transactionId, interfaceName));
    }

    @Test
    public void testCreateAwareNetworkInterface_serviceSpecificException() throws Exception {
        short transactionId = 22;
        String interfaceName = "aware_data0";
        doThrow(new ServiceSpecificException(0, "error")).when(mMockSupplicantNanIface)
                .createDataInterfaceRequest(anyChar(), anyString());
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
}
