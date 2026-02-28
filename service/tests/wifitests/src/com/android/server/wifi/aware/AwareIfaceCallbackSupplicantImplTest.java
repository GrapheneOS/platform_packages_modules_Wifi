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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyShort;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import android.net.wifi.aware.AwarePairingConfig;
import android.net.wifi.aware.Characteristics;
import android.net.wifi.aware.WifiAwareChannelInfo;
import android.system.wifi.mainline_supplicant.NanBootstrappingConfirmInd;
import android.system.wifi.mainline_supplicant.NanBootstrappingRequestInd;
import android.system.wifi.mainline_supplicant.NanCapabilities;
import android.system.wifi.mainline_supplicant.NanCipherSuiteType;
import android.system.wifi.mainline_supplicant.NanClusterEventInd;
import android.system.wifi.mainline_supplicant.NanClusterEventType;
import android.system.wifi.mainline_supplicant.NanDataPathChannelInfo;
import android.system.wifi.mainline_supplicant.NanDataPathConfirmInd;
import android.system.wifi.mainline_supplicant.NanDataPathRequestInd;
import android.system.wifi.mainline_supplicant.NanFollowupReceivedInd;
import android.system.wifi.mainline_supplicant.NanIdentityResolutionAttribute;
import android.system.wifi.mainline_supplicant.NanMatchInd;
import android.system.wifi.mainline_supplicant.NanPairingAkm;
import android.system.wifi.mainline_supplicant.NanPairingConfig;
import android.system.wifi.mainline_supplicant.NanPairingConfirmInd;
import android.system.wifi.mainline_supplicant.NanPairingRequestInd;
import android.system.wifi.mainline_supplicant.NanPairingRequestType;
import android.system.wifi.mainline_supplicant.NanRangingIndication;
import android.system.wifi.mainline_supplicant.NanStatus;
import android.system.wifi.mainline_supplicant.NanStatus.NanStatusCode;
import android.system.wifi.mainline_supplicant.NpkSecurityAssociation;

import androidx.test.filters.SmallTest;

import com.android.server.wifi.WifiBaseTest;
import com.android.server.wifi.hal.WifiNanIface;
import com.android.server.wifi.util.HalAidlUtil;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.List;

/**
 * Unit test harness for AwareIfaceCallbackSupplicantImpl.
 */
@SmallTest
public class AwareIfaceCallbackSupplicantImplTest extends WifiBaseTest {
    @Mock
    private WifiNanIface.Callback mMockFrameworkCallback;

    private AwareIfaceCallbackSupplicantImpl mDut;
    private static final byte[] MAC_ADDRESS = {0x01, 0x02, 0x03, 0x04, 0x05, 0x06};
    private static final byte[] NDI_MAC_ADDR = {0x07, 0x08, 0x09, 0x0a, 0x0b, 0x0c};
    private static final byte[] APP_INFO = {0x10, 0x11};
    private static final byte[] SERVICE_SPECIFIC_INFO = {0x10, 0x11};
    private static final byte[] MATCH_FILTER = {0x20, 0x21};
    private static final byte[] SCID = {0x30, 0x31};

    @Before
    public void setup() throws Exception {
        MockitoAnnotations.initMocks(this);
        mDut = new AwareIfaceCallbackSupplicantImpl(mMockFrameworkCallback);
        mDut.enableVerboseLogging(true);
    }

    @Test
    public void testEventClusterEvent() throws Exception {
        NanClusterEventInd event = new NanClusterEventInd();
        event.eventType = NanClusterEventType.JOINED_CLUSTER;
        event.addr = MAC_ADDRESS;

        mDut.eventClusterEvent(event);

        verify(mMockFrameworkCallback).eventClusterEvent(
                WifiNanIface.NanClusterEventType.JOINED_CLUSTER, MAC_ADDRESS);
    }

    @Test
    public void testEventMatch() throws Exception {
        byte discoverySessionId = 1;
        int peerId = 2;
        int rangingIndicationType = NanRangingIndication.EGRESS_MET_MASK;
        int rangingMeasurementInMm = 1000;
        int peerCipherType = NanCipherSuiteType.SHARED_KEY_128_MASK;
        NanIdentityResolutionAttribute peerNira = new NanIdentityResolutionAttribute();
        peerNira.nonce = new byte[16];
        peerNira.tag = new byte[8];
        NanPairingConfig peerPairingConfig = new NanPairingConfig();
        peerPairingConfig.enablePairingSetup = true;

        NanMatchInd event = new NanMatchInd();
        event.discoverySessionId = discoverySessionId;
        event.peerId = peerId;
        event.addr = MAC_ADDRESS;
        event.serviceSpecificInfo = SERVICE_SPECIFIC_INFO;
        event.extendedServiceSpecificInfo = new byte[0];
        event.matchFilter = MATCH_FILTER;
        event.rangingIndicationType = rangingIndicationType;
        event.rangingMeasurementInMm = rangingMeasurementInMm;
        event.scid = SCID;
        event.peerCipherType = peerCipherType;
        event.peerNira = peerNira;
        event.peerPairingConfig = peerPairingConfig;

        mDut.eventMatch(event);

        verify(mMockFrameworkCallback).eventMatch(
                discoverySessionId,
                peerId,
                MAC_ADDRESS,
                SERVICE_SPECIFIC_INFO,
                MATCH_FILTER,
                WifiNanIface.NanRangingIndication.fromAidl(rangingIndicationType),
                rangingMeasurementInMm,
                SCID,
                Characteristics.WIFI_AWARE_CIPHER_SUITE_NCS_SK_128,
                peerNira.nonce,
                peerNira.tag,
                new AwarePairingConfig(true, false, false, 0, 0),
                null);
    }

    @Test
    public void testEventMatchExtendedSsi() throws Exception {
        byte discoverySessionId = 1;
        int peerId = 2;
        int rangingIndicationType = NanRangingIndication.EGRESS_MET_MASK;
        int rangingMeasurementInMm = 1000;
        int peerCipherType = NanCipherSuiteType.SHARED_KEY_128_MASK;
        NanIdentityResolutionAttribute peerNira = new NanIdentityResolutionAttribute();
        peerNira.nonce = new byte[16];
        peerNira.tag = new byte[8];
        NanPairingConfig peerPairingConfig = new NanPairingConfig();
        peerPairingConfig.enablePairingSetup = true;

        NanMatchInd event = new NanMatchInd();
        event.discoverySessionId = discoverySessionId;
        event.peerId = peerId;
        event.addr = MAC_ADDRESS;
        event.serviceSpecificInfo = new byte[0];
        event.extendedServiceSpecificInfo = SERVICE_SPECIFIC_INFO;
        event.matchFilter = MATCH_FILTER;
        event.rangingIndicationType = rangingIndicationType;
        event.rangingMeasurementInMm = rangingMeasurementInMm;
        event.scid = SCID;
        event.peerCipherType = peerCipherType;
        event.peerNira = peerNira;
        event.peerPairingConfig = peerPairingConfig;

        mDut.eventMatch(event);

        verify(mMockFrameworkCallback).eventMatch(
                discoverySessionId,
                peerId,
                MAC_ADDRESS,
                SERVICE_SPECIFIC_INFO,
                MATCH_FILTER,
                WifiNanIface.NanRangingIndication.fromAidl(rangingIndicationType),
                rangingMeasurementInMm,
                SCID,
                Characteristics.WIFI_AWARE_CIPHER_SUITE_NCS_SK_128,
                peerNira.nonce,
                peerNira.tag,
                new AwarePairingConfig(true, false, false, 0, 0),
                null);
    }

    @Test
    public void testEventMatchExpired() throws Exception {
        byte discoverySessionId = 1;
        int peerId = 2;

        mDut.eventMatchExpired(discoverySessionId, peerId);

        verify(mMockFrameworkCallback).eventMatchExpired(discoverySessionId, peerId);
    }

    @Test
    public void testEventPublishTerminated() throws Exception {
        byte sessionId = 1;
        NanStatus status = new NanStatus();
        status.status = NanStatusCode.SUCCESS;

        mDut.eventPublishTerminated(sessionId, status);

        verify(mMockFrameworkCallback).eventPublishTerminated(sessionId,
                WifiNanIface.NanStatusCode.SUCCESS);
    }

    @Test
    public void testEventSubscribeTerminated() throws Exception {
        byte sessionId = 2;
        NanStatus status = new NanStatus();
        status.status = NanStatusCode.SUCCESS;

        mDut.eventSubscribeTerminated(sessionId, status);

        verify(mMockFrameworkCallback).eventSubscribeTerminated(sessionId,
                WifiNanIface.NanStatusCode.SUCCESS);
    }

    @Test
    public void testEventTransmitFollowup() throws Exception {
        char id = 123;
        NanStatus status = new NanStatus();
        status.status = NanStatusCode.SUCCESS;

        mDut.eventTransmitFollowup(id, status);

        verify(mMockFrameworkCallback).eventTransmitFollowup((short) id,
                WifiNanIface.NanStatusCode.SUCCESS);
    }

    @Test
    public void testEventFollowupReceived() throws Exception {
        byte discoverySessionId = 1;
        int peerId = 2;

        NanFollowupReceivedInd event = new NanFollowupReceivedInd();
        event.discoverySessionId = discoverySessionId;
        event.peerId = peerId;
        event.addr = MAC_ADDRESS;
        event.serviceSpecificInfo = SERVICE_SPECIFIC_INFO;
        event.extendedServiceSpecificInfo = new byte[0];

        mDut.eventFollowupReceived(event);

        verify(mMockFrameworkCallback).eventFollowupReceived(discoverySessionId, peerId,
                MAC_ADDRESS,
                SERVICE_SPECIFIC_INFO);
    }

    @Test
    public void testEventFollowupReceivedExtendedSsi() throws Exception {
        byte discoverySessionId = 1;
        int peerId = 2;

        NanFollowupReceivedInd event = new NanFollowupReceivedInd();
        event.discoverySessionId = discoverySessionId;
        event.peerId = peerId;
        event.addr = MAC_ADDRESS;
        event.serviceSpecificInfo = new byte[0];
        event.extendedServiceSpecificInfo = SERVICE_SPECIFIC_INFO;

        mDut.eventFollowupReceived(event);

        verify(mMockFrameworkCallback).eventFollowupReceived(discoverySessionId, peerId,
                MAC_ADDRESS,
                SERVICE_SPECIFIC_INFO);
    }

    @Test
    public void testEventDataPathConfirm() throws Exception {
        int ndpInstanceId = 1;
        boolean dataPathSetupSuccess = true;
        NanStatus status = new NanStatus();
        status.status = NanStatusCode.SUCCESS;

        NanDataPathConfirmInd event = new NanDataPathConfirmInd();
        event.ndpInstanceId = ndpInstanceId;
        event.dataPathSetupSuccess = dataPathSetupSuccess;
        event.peerNdiMacAddr = NDI_MAC_ADDR;
        event.appInfo = APP_INFO;
        event.status = status;
        event.channelInfo = new NanDataPathChannelInfo[0];

        mDut.eventDataPathConfirm(event);

        verify(mMockFrameworkCallback).eventDataPathConfirm(
                WifiNanIface.NanStatusCode.SUCCESS,
                ndpInstanceId,
                dataPathSetupSuccess,
                NDI_MAC_ADDR,
                APP_INFO,
                new ArrayList<>());
    }

    @Test
    public void testEventDataPathConfirmWithChannelInfo() throws Exception {
        int ndpInstanceId = 1;
        boolean dataPathSetupSuccess = true;
        NanStatus status = new NanStatus();
        status.status = NanStatusCode.SUCCESS;

        NanDataPathChannelInfo channelInfo = new NanDataPathChannelInfo();
        channelInfo.channelFreqMhz = 2412;
        channelInfo.channelBandwidth = 20;
        channelInfo.numSpatialStreams = 1;
        NanDataPathChannelInfo[] channelInfos = {channelInfo};
        List<WifiAwareChannelInfo> expectedChannelInfo = new ArrayList<>();
        expectedChannelInfo.add(new WifiAwareChannelInfo(channelInfo.channelFreqMhz,
                HalAidlUtil.getChannelBandwidthFromHal(channelInfo.channelBandwidth),
                channelInfo.numSpatialStreams));


        NanDataPathConfirmInd event = new NanDataPathConfirmInd();
        event.ndpInstanceId = ndpInstanceId;
        event.dataPathSetupSuccess = dataPathSetupSuccess;
        event.peerNdiMacAddr = NDI_MAC_ADDR;
        event.appInfo = APP_INFO;
        event.status = status;
        event.channelInfo = channelInfos;

        mDut.eventDataPathConfirm(event);

        verify(mMockFrameworkCallback).eventDataPathConfirm(
                WifiNanIface.NanStatusCode.SUCCESS,
                ndpInstanceId,
                dataPathSetupSuccess,
                NDI_MAC_ADDR,
                APP_INFO,
                expectedChannelInfo);
    }

    @Test
    public void testEventDataPathRequest() throws Exception {
        byte discoverySessionId = 1;
        int ndpInstanceId = 2;

        NanDataPathRequestInd event = new NanDataPathRequestInd();
        event.discoverySessionId = discoverySessionId;
        event.peerDiscMacAddr = NDI_MAC_ADDR;
        event.ndpInstanceId = ndpInstanceId;
        event.appInfo = APP_INFO;
        event.ndiInitMac = NDI_MAC_ADDR;

        mDut.eventDataPathRequest(event);

        verify(mMockFrameworkCallback).eventDataPathRequest(
                discoverySessionId,
                NDI_MAC_ADDR,
                ndpInstanceId,
                APP_INFO,
                NDI_MAC_ADDR);
    }

    @Test
    public void testEventDataPathTerminated() throws Exception {
        int ndpInstanceId = 1;

        mDut.eventDataPathTerminated(ndpInstanceId);

        verify(mMockFrameworkCallback).eventDataPathTerminated(ndpInstanceId);
    }

    @Test
    public void testNotifyCapabilitiesResponse() throws Exception {
        char id = 123;
        NanStatus status = new NanStatus();
        status.status = NanStatusCode.SUCCESS;
        NanCapabilities capabilities = new NanCapabilities();
        capabilities.maxPublishes = 1;
        capabilities.maxSubscribes = 2;
        capabilities.maxServiceNameLen = 3;
        capabilities.maxMatchFilterLen = 4;
        capabilities.maxServiceSpecificInfoLen = 5;
        capabilities.maxExtendedServiceSpecificInfoLen = 6;
        capabilities.maxNdiInterfaces = 7;
        capabilities.maxNdpSessions = 8;
        capabilities.maxAppInfoLen = 9;
        capabilities.supportedCipherSuites = NanCipherSuiteType.SHARED_KEY_128_MASK
                | NanCipherSuiteType.PUBLIC_KEY_PASN_256_MASK;
        capabilities.instantCommunicationModeSupportFlag = true;
        capabilities.supportsPairing = true;
        capabilities.supportsSuspension = true;
        capabilities.supportsPeriodicRanging = true;

        mDut.notifyCapabilitiesResponse(id, status, capabilities);

        ArgumentCaptor<Capabilities> captor =
                ArgumentCaptor.forClass(Capabilities.class);
        verify(mMockFrameworkCallback).notifyCapabilitiesResponse(anyShort(), captor.capture());
        Capabilities frameworkCapabilities = captor.getValue();
        assertEquals(1, frameworkCapabilities.maxPublishes);
        assertEquals(2, frameworkCapabilities.maxSubscribes);
        assertEquals(3, frameworkCapabilities.maxServiceNameLen);
        assertEquals(4, frameworkCapabilities.maxMatchFilterLen);
        assertEquals(5, frameworkCapabilities.maxServiceSpecificInfoLen);
        assertEquals(6, frameworkCapabilities.maxExtendedServiceSpecificInfoLen);
        assertEquals(7, frameworkCapabilities.maxNdiInterfaces);
        assertEquals(8, frameworkCapabilities.maxNdpSessions);
        assertEquals(9, frameworkCapabilities.maxAppInfoLen);
        assertEquals(Characteristics.WIFI_AWARE_CIPHER_SUITE_NCS_SK_128,
                frameworkCapabilities.supportedDataPathCipherSuites);
        assertEquals(Characteristics.WIFI_AWARE_CIPHER_SUITE_NCS_PK_PASN_256,
                frameworkCapabilities.supportedPairingCipherSuites);
        assertTrue(frameworkCapabilities.isInstantCommunicationModeSupported);
        assertTrue(frameworkCapabilities.isNanPairingSupported);
        assertTrue(frameworkCapabilities.isSuspensionSupported);
        assertTrue(frameworkCapabilities.isPeriodicRangingSupported);
    }

    @Test
    public void testNotifyCapabilitiesResponseFailure() throws Exception {
        char id = 123;
        NanStatus status = new NanStatus();
        status.status = NanStatusCode.INTERNAL_FAILURE;
        NanCapabilities capabilities = new NanCapabilities();

        mDut.notifyCapabilitiesResponse(id, status, capabilities);
        // No framework callback for this. Just log.
    }

    @Test
    public void testNotifyConfigResponse() throws Exception {
        char id = 123;
        NanStatus status = new NanStatus();
        status.status = NanStatusCode.SUCCESS;

        mDut.notifyConfigResponse(id, status);

        verify(mMockFrameworkCallback).notifyConfigResponse((short) id,
                WifiNanIface.NanStatusCode.SUCCESS);
    }

    @Test
    public void testNotifyCreateDataInterfaceResponse() throws Exception {
        char id = 123;
        NanStatus status = new NanStatus();
        status.status = NanStatusCode.SUCCESS;

        mDut.notifyCreateDataInterfaceResponse(id, status);

        verify(mMockFrameworkCallback).notifyCreateDataInterfaceResponse((short) id,
                WifiNanIface.NanStatusCode.SUCCESS);
    }

    @Test
    public void testNotifyDeleteDataInterfaceResponse() throws Exception {
        char id = 123;
        NanStatus status = new NanStatus();
        status.status = NanStatusCode.SUCCESS;

        mDut.notifyDeleteDataInterfaceResponse(id, status);

        verify(mMockFrameworkCallback).notifyDeleteDataInterfaceResponse((short) id,
                WifiNanIface.NanStatusCode.SUCCESS);
    }

    @Test
    public void testNotifyEnableResponse() throws Exception {
        char id = 123;
        NanStatus status = new NanStatus();
        status.status = NanStatusCode.SUCCESS;

        mDut.notifyEnableResponse(id, status);

        verify(mMockFrameworkCallback).notifyEnableResponse((short) id,
                WifiNanIface.NanStatusCode.SUCCESS);
    }

    @Test
    public void testNotifyEnableResponseAlreadyEnabled() throws Exception {
        char id = 123;
        NanStatus status = new NanStatus();
        status.status = NanStatusCode.ALREADY_ENABLED;

        mDut.notifyEnableResponse(id, status);

        verify(mMockFrameworkCallback).notifyEnableResponse((short) id,
                WifiNanIface.NanStatusCode.ALREADY_ENABLED);
    }

    @Test
    public void testNotifyDisableResponse() throws Exception {
        char id = 123;
        NanStatus status = new NanStatus();
        status.status = NanStatusCode.SUCCESS;

        mDut.notifyDisableResponse(id, status);

        verify(mMockFrameworkCallback).notifyDisableResponse((short) id,
                WifiNanIface.NanStatusCode.SUCCESS);
    }

    @Test
    public void testNotifyDisableResponseFailure() throws Exception {
        char id = 123;
        NanStatus status = new NanStatus();
        status.status = NanStatusCode.INTERNAL_FAILURE;

        mDut.notifyDisableResponse(id, status);
        verify(mMockFrameworkCallback).notifyDisableResponse((short) id,
                WifiNanIface.NanStatusCode.INTERNAL_FAILURE);
    }

    @Test
    public void testNotifyStartPublishResponse() throws Exception {
        char id = 123;
        NanStatus status = new NanStatus();
        status.status = NanStatusCode.SUCCESS;
        byte publishId = 1;

        mDut.notifyStartPublishResponse(id, status, publishId);

        verify(mMockFrameworkCallback).notifyStartPublishResponse((short) id,
                WifiNanIface.NanStatusCode.SUCCESS, publishId);
    }

    @Test
    public void testNotifyStartSubscribeResponse() throws Exception {
        char id = 123;
        NanStatus status = new NanStatus();
        status.status = NanStatusCode.SUCCESS;
        byte subscribeId = 2;

        mDut.notifyStartSubscribeResponse(id, status, subscribeId);

        verify(mMockFrameworkCallback).notifyStartSubscribeResponse((short) id,
                WifiNanIface.NanStatusCode.SUCCESS, subscribeId);
    }

    @Test
    public void testNotifyStopPublishResponse() throws Exception {
        char id = 123;
        NanStatus status = new NanStatus();
        status.status = NanStatusCode.SUCCESS;

        mDut.notifyStopPublishResponse(id, status);
        // No framework callback for this. Just log.
    }

    @Test
    public void testNotifyStopPublishResponseFailure() throws Exception {
        char id = 123;
        NanStatus status = new NanStatus();
        status.status = NanStatusCode.INTERNAL_FAILURE;

        mDut.notifyStopPublishResponse(id, status);
        // No framework callback for this. Just log.
    }

    @Test
    public void testNotifyStopSubscribeResponse() throws Exception {
        char id = 123;
        NanStatus status = new NanStatus();
        status.status = NanStatusCode.SUCCESS;

        mDut.notifyStopSubscribeResponse(id, status);
        // No framework callback for this. Just log.
    }

    @Test
    public void testNotifyStopSubscribeResponseFailure() throws Exception {
        char id = 123;
        NanStatus status = new NanStatus();
        status.status = NanStatusCode.INTERNAL_FAILURE;

        mDut.notifyStopSubscribeResponse(id, status);
        // No framework callback for this. Just log.
    }

    @Test
    public void testNotifyTransmitFollowupResponse() throws Exception {
        char id = 123;
        NanStatus status = new NanStatus();
        status.status = NanStatusCode.SUCCESS;

        mDut.notifyTransmitFollowupResponse(id, status);

        verify(mMockFrameworkCallback).notifyTransmitFollowupResponse((short) id,
                WifiNanIface.NanStatusCode.SUCCESS);
    }

    @Test
    public void testNotifyInitiateBootstrappingResponse() throws Exception {
        char id = 123;
        NanStatus status = new NanStatus();
        status.status = NanStatusCode.SUCCESS;
        int bootstrappingInstanceId = 1;

        mDut.notifyInitiateBootstrappingResponse(id, status, bootstrappingInstanceId);

        verify(mMockFrameworkCallback).notifyInitiateBootstrappingResponse((short) id,
                WifiNanIface.NanStatusCode.SUCCESS, bootstrappingInstanceId);
    }

    @Test
    public void testNotifyRespondToBootstrappingIndicationResponse() {
        char id = 123;
        NanStatus status = new NanStatus();
        status.status = NanStatusCode.SUCCESS;

        mDut.notifyRespondToBootstrappingIndicationResponse(id, status);

        verify(mMockFrameworkCallback).notifyRespondToBootstrappingIndicationResponse((short) id,
                WifiNanIface.NanStatusCode.SUCCESS);
    }

    @Test
    public void testNotifyInitiatePairingResponse() throws Exception {
        char id = 123;
        NanStatus status = new NanStatus();
        status.status = NanStatusCode.SUCCESS;
        int pairingInstanceId = 1;

        mDut.notifyInitiatePairingResponse(id, status, pairingInstanceId);

        verify(mMockFrameworkCallback).notifyInitiatePairingResponse((short) id,
                WifiNanIface.NanStatusCode.SUCCESS, pairingInstanceId);
    }

    @Test
    public void testNotifyRespondToPairingIndicationResponse() throws Exception {
        char id = 123;
        NanStatus status = new NanStatus();
        status.status = NanStatusCode.SUCCESS;

        mDut.notifyRespondToPairingIndicationResponse(id, status);

        verify(mMockFrameworkCallback).notifyRespondToPairingIndicationResponse((short) id,
                WifiNanIface.NanStatusCode.SUCCESS);
    }

    @Test
    public void testNotifyTerminatePairingResponse() throws Exception {
        char id = 123;
        NanStatus status = new NanStatus();
        status.status = NanStatusCode.SUCCESS;

        mDut.notifyTerminatePairingResponse(id, status);

        verify(mMockFrameworkCallback).notifyTerminatePairingResponse((short) id,
                WifiNanIface.NanStatusCode.SUCCESS);
    }

    @Test
    public void testNotifyInitiateDataPathResponse() throws Exception {
        char id = 123;
        NanStatus status = new NanStatus();
        status.status = NanStatusCode.SUCCESS;
        int ndpInstanceId = 1;

        mDut.notifyInitiateDataPathResponse(id, status, ndpInstanceId);

        verify(mMockFrameworkCallback).notifyInitiateDataPathResponse((short) id,
                WifiNanIface.NanStatusCode.SUCCESS, ndpInstanceId);
    }

    @Test
    public void testNotifyRespondToDataPathIndicationResponse() throws Exception {
        char id = 123;
        NanStatus status = new NanStatus();
        status.status = NanStatusCode.SUCCESS;

        mDut.notifyRespondToDataPathIndicationResponse(id, status);

        verify(mMockFrameworkCallback).notifyRespondToDataPathIndicationResponse((short) id,
                WifiNanIface.NanStatusCode.SUCCESS);
    }

    @Test
    public void testNotifyTerminateDataPathResponse() throws Exception {
        char id = 123;
        NanStatus status = new NanStatus();
        status.status = NanStatusCode.SUCCESS;

        mDut.notifyTerminateDataPathResponse(id, status);

        verify(mMockFrameworkCallback).notifyTerminateDataPathResponse((short) id,
                WifiNanIface.NanStatusCode.SUCCESS);
    }

    @Test
    public void testEventBootstrappingRequest() {
        byte discoverySessionId = 1;
        int peerId = 2;
        int bootstrappingInstanceId = 3;
        int requestBootstrappingMethod = 4;

        NanBootstrappingRequestInd event = new NanBootstrappingRequestInd();
        event.discoverySessionId = discoverySessionId;
        event.peerId = peerId;
        event.peerDiscMacAddr = MAC_ADDRESS;
        event.bootstrappingInstanceId = bootstrappingInstanceId;
        event.requestBootstrappingMethod = requestBootstrappingMethod;

        mDut.eventBootstrappingRequest(event);

        verify(mMockFrameworkCallback).eventBootstrappingRequest(discoverySessionId,
                peerId, MAC_ADDRESS, bootstrappingInstanceId,
                requestBootstrappingMethod, null);
    }

    @Test
    public void testEventBootstrappingConfirm() {
        int bootstrappingInstanceId = 1;
        int responseCode =
                NanBootstrappingConfirmInd.NanBootstrappingResponseCode.REQUEST_ACCEPT;
        NanStatus failureReasonCode = new NanStatus();
        failureReasonCode.status = NanStatusCode.SUCCESS;
        int comeBackDelaySec = 10;
        byte[] cookie = {0x01, 0x02};
        int bootstrappingMethod = 0;
        byte sessionId = 1;

        NanBootstrappingConfirmInd event = new NanBootstrappingConfirmInd();
        event.bootstrappingInstanceId = bootstrappingInstanceId;
        event.responseCode = responseCode;
        event.failureReasonCode = failureReasonCode;
        event.comeBackDelaySec = comeBackDelaySec;
        event.cookie = cookie;
        event.peerDiscMacAddr = MAC_ADDRESS;
        event.bootstrappingMethod = bootstrappingMethod;
        event.discoverySessionId = sessionId;

        mDut.eventBootstrappingConfirm(event);

        verify(mMockFrameworkCallback).eventBootstrappingConfirm(
                sessionId,
                bootstrappingInstanceId,
                WifiAwareStateManager.NAN_BOOTSTRAPPING_ACCEPT,
                WifiNanIface.NanStatusCode.SUCCESS,
                comeBackDelaySec,
                bootstrappingMethod,
                cookie,
                MAC_ADDRESS);
    }

    @Test
    public void testEventBootstrappingConfirmReject() {
        int bootstrappingInstanceId = 1;
        int responseCode =
                NanBootstrappingConfirmInd.NanBootstrappingResponseCode.REQUEST_REJECT;
        NanStatus failureReasonCode = new NanStatus();
        failureReasonCode.status = NanStatusCode.SUCCESS;
        int comeBackDelaySec = 10;
        byte[] cookie = {0x01, 0x02};
        int bootstrappingMethod = 0;
        byte sessionId = 1;

        NanBootstrappingConfirmInd event = new NanBootstrappingConfirmInd();
        event.bootstrappingInstanceId = bootstrappingInstanceId;
        event.responseCode = responseCode;
        event.failureReasonCode = failureReasonCode;
        event.comeBackDelaySec = comeBackDelaySec;
        event.cookie = cookie;
        event.peerDiscMacAddr = MAC_ADDRESS;
        event.bootstrappingMethod = bootstrappingMethod;
        event.discoverySessionId = sessionId;

        mDut.eventBootstrappingConfirm(event);

        verify(mMockFrameworkCallback).eventBootstrappingConfirm(
                sessionId,
                bootstrappingInstanceId,
                WifiAwareStateManager.NAN_BOOTSTRAPPING_REJECT,
                WifiNanIface.NanStatusCode.SUCCESS,
                comeBackDelaySec,
                bootstrappingMethod,
                cookie,
                MAC_ADDRESS);
    }

    @Test
    public void testEventBootstrappingConfirmComeback() {
        int bootstrappingInstanceId = 1;
        int responseCode =
                NanBootstrappingConfirmInd.NanBootstrappingResponseCode.REQUEST_COMEBACK;
        NanStatus failureReasonCode = new NanStatus();
        failureReasonCode.status = NanStatusCode.SUCCESS;
        int comeBackDelaySec = 10;
        byte[] cookie = {0x01, 0x02};
        int bootstrappingMethod = 0;
        byte sessionId = 1;

        NanBootstrappingConfirmInd event = new NanBootstrappingConfirmInd();
        event.bootstrappingInstanceId = bootstrappingInstanceId;
        event.responseCode = responseCode;
        event.failureReasonCode = failureReasonCode;
        event.comeBackDelaySec = comeBackDelaySec;
        event.cookie = cookie;
        event.peerDiscMacAddr = MAC_ADDRESS;
        event.bootstrappingMethod = bootstrappingMethod;
        event.discoverySessionId = sessionId;

        mDut.eventBootstrappingConfirm(event);

        verify(mMockFrameworkCallback).eventBootstrappingConfirm(
                sessionId,
                bootstrappingInstanceId,
                WifiAwareStateManager.NAN_BOOTSTRAPPING_COMEBACK,
                WifiNanIface.NanStatusCode.SUCCESS,
                comeBackDelaySec,
                bootstrappingMethod,
                cookie,
                MAC_ADDRESS);
    }

    @Test
    public void testEventPairingRequest() throws Exception {
        byte discoverySessionId = 1;
        int peerId = 2;
        int pairingInstanceId = 3;
        int requestType = NanPairingRequestType.NAN_PAIRING_SETUP;
        boolean enablePairingCache = true;
        NanIdentityResolutionAttribute peerNira = new NanIdentityResolutionAttribute();
        peerNira.nonce = new byte[16];
        peerNira.tag = new byte[8];

        NanPairingRequestInd event = new NanPairingRequestInd();
        event.discoverySessionId = discoverySessionId;
        event.peerId = peerId;
        event.peerDiscMacAddr = MAC_ADDRESS;
        event.pairingInstanceId = pairingInstanceId;
        event.requestType = requestType;
        event.enablePairingCache = enablePairingCache;
        event.peerNira = peerNira;

        mDut.eventPairingRequest(event);

        verify(mMockFrameworkCallback).eventPairingRequest(discoverySessionId,
                peerId, MAC_ADDRESS, pairingInstanceId,
                WifiAwareStateManager.NAN_PAIRING_REQUEST_TYPE_SETUP,
                enablePairingCache, peerNira.nonce, peerNira.tag);
    }

    @Test
    public void testEventPairingConfirm() {
        int pairingInstanceId = 1;
        boolean pairingSuccess = true;
        NanStatus status = new NanStatus();
        status.status = NanStatusCode.SUCCESS;
        int requestType = NanPairingRequestType.NAN_PAIRING_SETUP;
        boolean enablePairingCache = true;
        NpkSecurityAssociation npksa = new NpkSecurityAssociation();
        npksa.peerNanIdentityKey = new byte[32];
        npksa.localNanIdentityKey = new byte[32];
        npksa.npk = new byte[32];
        npksa.akm = NanPairingAkm.SAE;
        npksa.cipherType = NanCipherSuiteType.PUBLIC_KEY_PASN_256_MASK;

        NanPairingConfirmInd event = new NanPairingConfirmInd();
        event.pairingInstanceId = pairingInstanceId;
        event.pairingSuccess = pairingSuccess;
        event.status = status;
        event.requestType = requestType;
        event.enablePairingCache = enablePairingCache;
        event.npksa = npksa;

        mDut.eventPairingConfirm(event);

        ArgumentCaptor<PairingConfigManager.PairingSecurityAssociationInfo> captor =
                ArgumentCaptor.forClass(PairingConfigManager.PairingSecurityAssociationInfo.class);
        verify(mMockFrameworkCallback).eventPairingConfirm(
                eq(pairingInstanceId),
                eq(pairingSuccess),
                eq(WifiNanIface.NanStatusCode.SUCCESS),
                eq(WifiAwareStateManager.NAN_PAIRING_REQUEST_TYPE_SETUP),
                eq(enablePairingCache),
                captor.capture());
        PairingConfigManager.PairingSecurityAssociationInfo info = captor.getValue();
        assertEquals(npksa.peerNanIdentityKey, info.mPeerNik);
        assertEquals(npksa.localNanIdentityKey, info.mLocalNik);
        assertEquals(npksa.npk, info.mNpk);
        assertEquals(WifiAwareStateManager.NAN_PAIRING_AKM_SAE, info.mAkm);
        assertEquals(Characteristics.WIFI_AWARE_CIPHER_SUITE_NCS_PK_PASN_256,
                info.mCipherSuite);
    }

    @Test
    public void testEventPairingConfirmPasn() {
        int pairingInstanceId = 1;
        boolean pairingSuccess = true;
        NanStatus status = new NanStatus();
        status.status = NanStatusCode.SUCCESS;
        int requestType = NanPairingRequestType.NAN_PAIRING_SETUP;
        boolean enablePairingCache = true;
        NpkSecurityAssociation npksa = new NpkSecurityAssociation();
        npksa.peerNanIdentityKey = new byte[32];
        npksa.localNanIdentityKey = new byte[32];
        npksa.npk = new byte[32];
        npksa.akm = NanPairingAkm.PASN;
        npksa.cipherType = NanCipherSuiteType.PUBLIC_KEY_PASN_256_MASK;

        NanPairingConfirmInd event = new NanPairingConfirmInd();
        event.pairingInstanceId = pairingInstanceId;
        event.pairingSuccess = pairingSuccess;
        event.status = status;
        event.requestType = requestType;
        event.enablePairingCache = enablePairingCache;
        event.npksa = npksa;

        mDut.eventPairingConfirm(event);

        ArgumentCaptor<PairingConfigManager.PairingSecurityAssociationInfo> captor =
                ArgumentCaptor.forClass(PairingConfigManager.PairingSecurityAssociationInfo.class);
        verify(mMockFrameworkCallback).eventPairingConfirm(
                eq(pairingInstanceId),
                eq(pairingSuccess),
                eq(WifiNanIface.NanStatusCode.SUCCESS),
                eq(WifiAwareStateManager.NAN_PAIRING_REQUEST_TYPE_SETUP),
                eq(enablePairingCache),
                captor.capture());
        PairingConfigManager.PairingSecurityAssociationInfo info = captor.getValue();
        assertEquals(WifiAwareStateManager.NAN_PAIRING_AKM_PASN, info.mAkm);
    }
}
