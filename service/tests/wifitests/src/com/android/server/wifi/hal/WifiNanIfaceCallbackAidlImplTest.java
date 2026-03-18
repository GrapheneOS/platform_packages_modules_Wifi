/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.server.wifi.hal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.hardware.wifi.NanCapabilities;
import android.hardware.wifi.NanClusterEventInd;
import android.hardware.wifi.NanClusterEventType;
import android.hardware.wifi.NanDataPathConfirmInd;
import android.hardware.wifi.NanDataPathRequestInd;
import android.hardware.wifi.NanFollowupReceivedInd;
import android.hardware.wifi.NanIdentityResolutionAttribute;
import android.hardware.wifi.NanMatchInd;
import android.hardware.wifi.NanPairingConfig;
import android.hardware.wifi.NanRangingIndication;
import android.hardware.wifi.NanStatus;
import android.hardware.wifi.NanStatusCode;

import com.android.server.wifi.WifiBaseTest;
import com.android.server.wifi.aware.Capabilities;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class WifiNanIfaceCallbackAidlImplTest extends WifiBaseTest {
    private WifiNanIfaceCallbackAidlImpl mDut;

    @Mock
    private WifiNanIfaceAidlImpl mWifiNanIfaceAidlImplMock;
    @Mock
    private WifiNanIface.Callback mFrameworkCallbackMock;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        mDut = new WifiNanIfaceCallbackAidlImpl(mWifiNanIfaceAidlImplMock);
        when(mWifiNanIfaceAidlImplMock.getFrameworkCallback()).thenReturn(mFrameworkCallbackMock);
    }

    @Test
    public void testNotifyCapabilitiesResponse() {
        short id = 1;
        NanStatus status = new NanStatus();
        status.status = NanStatusCode.SUCCESS;
        NanCapabilities capabilities = new NanCapabilities();

        mDut.notifyCapabilitiesResponse((char) id, status, capabilities);

        verify(mFrameworkCallbackMock).notifyCapabilitiesResponse(eq(id), any(Capabilities.class));
    }

    @Test
    public void testNotifyConfigResponse() {
        short id = 3;
        NanStatus status = new NanStatus();
        status.status = NanStatusCode.SUCCESS;

        mDut.notifyConfigResponse((char) id, status);

        verify(mFrameworkCallbackMock).notifyConfigResponse(id,
                WifiNanIface.NanStatusCode.SUCCESS);
    }

    @Test
    public void testNotifyDisableResponse() {
        short id = 4;
        NanStatus status = new NanStatus();
        status.status = NanStatusCode.SUCCESS;

        mDut.notifyDisableResponse((char) id, status);

        verify(mFrameworkCallbackMock).notifyDisableResponse(id,
                WifiNanIface.NanStatusCode.SUCCESS);
    }

    @Test
    public void testNotifyStartPublishResponse() {
        short id = 5;
        NanStatus status = new NanStatus();
        status.status = NanStatusCode.SUCCESS;
        byte publishId = 10;

        mDut.notifyStartPublishResponse((char) id, status, publishId);

        verify(mFrameworkCallbackMock).notifyStartPublishResponse(id,
                WifiNanIface.NanStatusCode.SUCCESS, publishId);
    }

    @Test
    public void testNotifyStopPublishResponse() {
        short id = 6;
        NanStatus status = new NanStatus();
        status.status = NanStatusCode.SUCCESS;

        mDut.notifyStopPublishResponse((char) id, status);

        // In HIDL, this is empty. In AIDL, this should just be a success log, no callback.
        // For now, let's assume no framework callback. If that changes, this test will fail
        // and need updating.
    }

    @Test
    public void testNotifyStartSubscribeResponse() {
        short id = 7;
        NanStatus status = new NanStatus();
        status.status = NanStatusCode.SUCCESS;
        byte subscribeId = 11;

        mDut.notifyStartSubscribeResponse((char) id, status, subscribeId);

        verify(mFrameworkCallbackMock).notifyStartSubscribeResponse(id,
                WifiNanIface.NanStatusCode.SUCCESS, subscribeId);
    }

    @Test
    public void testNotifyStopSubscribeResponse() {
        short id = 8;
        NanStatus status = new NanStatus();
        status.status = NanStatusCode.SUCCESS;

        mDut.notifyStopSubscribeResponse((char) id, status);
    }

    @Test
    public void testNotifyTransmitFollowupResponse() {
        short id = 9;
        NanStatus status = new NanStatus();
        status.status = NanStatusCode.SUCCESS;

        mDut.notifyTransmitFollowupResponse((char) id, status);

        verify(mFrameworkCallbackMock).notifyTransmitFollowupResponse(id,
                WifiNanIface.NanStatusCode.SUCCESS);
    }

    @Test
    public void testNotifyCreateDataInterfaceResponse() {
        short id = 10;
        NanStatus status = new NanStatus();
        status.status = NanStatusCode.SUCCESS;

        mDut.notifyCreateDataInterfaceResponse((char) id, status);

        verify(mFrameworkCallbackMock).notifyCreateDataInterfaceResponse(id,
                WifiNanIface.NanStatusCode.SUCCESS);
    }

    @Test
    public void testNotifyDeleteDataInterfaceResponse() {
        short id = 11;
        NanStatus status = new NanStatus();
        status.status = NanStatusCode.SUCCESS;

        mDut.notifyDeleteDataInterfaceResponse((char) id, status);

        verify(mFrameworkCallbackMock).notifyDeleteDataInterfaceResponse(id,
                WifiNanIface.NanStatusCode.SUCCESS);
    }

    @Test
    public void testNotifyInitiateDataPathResponse() {
        short id = 12;
        NanStatus status = new NanStatus();
        status.status = NanStatusCode.SUCCESS;
        int ndpInstanceId = 100;

        mDut.notifyInitiateDataPathResponse((char) id, status, ndpInstanceId);

        verify(mFrameworkCallbackMock).notifyInitiateDataPathResponse(id,
                WifiNanIface.NanStatusCode.SUCCESS, ndpInstanceId);
    }

    @Test
    public void testNotifyRespondToDataPathIndicationResponse() {
        short id = 13;
        NanStatus status = new NanStatus();
        status.status = NanStatusCode.SUCCESS;

        mDut.notifyRespondToDataPathIndicationResponse((char) id, status);

        verify(mFrameworkCallbackMock).notifyRespondToDataPathIndicationResponse(id,
                WifiNanIface.NanStatusCode.SUCCESS);
    }

    @Test
    public void testNotifyTerminateDataPathResponse() {
        short id = 14;
        NanStatus status = new NanStatus();
        status.status = NanStatusCode.SUCCESS;

        mDut.notifyTerminateDataPathResponse((char) id, status);

        verify(mFrameworkCallbackMock).notifyTerminateDataPathResponse(id,
                WifiNanIface.NanStatusCode.SUCCESS);
    }

    @Test
    public void testEventPublishTerminated() {
        byte sessionId = 20;
        NanStatus status = new NanStatus();
        status.status = NanStatusCode.SUCCESS;

        mDut.eventPublishTerminated(sessionId, status);

        verify(mFrameworkCallbackMock).eventPublishTerminated(sessionId,
                WifiNanIface.NanStatusCode.SUCCESS);
    }

    @Test
    public void testEventSubscribeTerminated() {
        byte sessionId = 21;
        NanStatus status = new NanStatus();
        status.status = NanStatusCode.SUCCESS;

        mDut.eventSubscribeTerminated(sessionId, status);

        verify(mFrameworkCallbackMock).eventSubscribeTerminated(sessionId,
                WifiNanIface.NanStatusCode.SUCCESS);
    }

    @Test
    public void testEventClusterEvent() {
        NanClusterEventInd event = new NanClusterEventInd();
        event.eventType = NanClusterEventType.JOINED_CLUSTER;
        event.addr = new byte[]{0x01, 0x02, 0x03, 0x04, 0x05, 0x06};

        mDut.eventClusterEvent(event);

        verify(mFrameworkCallbackMock).eventClusterEvent(NanClusterEventType.JOINED_CLUSTER,
                event.addr);
    }

    @Test
    public void testEventMatch() {
        NanMatchInd event = new NanMatchInd();
        event.discoverySessionId = 1;
        event.peerId = 2;
        event.addr = new byte[]{0x01, 0x02, 0x03, 0x04, 0x05, 0x06};
        event.serviceSpecificInfo = new byte[]{0x10, 0x11};
        event.matchFilter = new byte[]{0x20, 0x21};
        event.rangingIndicationType = NanRangingIndication.EGRESS_MET_MASK;
        event.rangingMeasurementInMm = 1000;
        event.scid = new byte[]{0x30, 0x31};
        event.peerCipherType = 0;
        event.peerNira = new NanIdentityResolutionAttribute();
        event.peerPairingConfig = new NanPairingConfig();

        mDut.eventMatch(event);

        verify(mFrameworkCallbackMock).eventMatch(eq(event.discoverySessionId), eq(event.peerId),
                eq(event.addr), eq(event.serviceSpecificInfo), eq(event.matchFilter),
                eq(NanRangingIndication.EGRESS_MET_MASK), eq(event.rangingMeasurementInMm),
                eq(event.scid), eq(0), isNull(), isNull(), any(), isNull());
    }

    @Test
    public void testEventFollowupReceived() {
        NanFollowupReceivedInd event = new NanFollowupReceivedInd();
        event.discoverySessionId = 1;
        event.peerId = 2;
        event.addr = new byte[]{0x01, 0x02, 0x03, 0x04, 0x05, 0x06};
        event.serviceSpecificInfo = new byte[]{0x10, 0x11};

        mDut.eventFollowupReceived(event);

        verify(mFrameworkCallbackMock).eventFollowupReceived(event.discoverySessionId,
                event.peerId, event.addr, event.serviceSpecificInfo);
    }

    @Test
    public void testEventDataPathRequest() {
        NanDataPathRequestInd event = new NanDataPathRequestInd();
        event.discoverySessionId = 1;
        event.peerDiscMacAddr = new byte[]{0x01, 0x02, 0x03, 0x04, 0x05, 0x06};
        event.ndpInstanceId = 10;
        event.appInfo = new byte[]{0x10, 0x11};

        mDut.eventDataPathRequest(event);

        verify(mFrameworkCallbackMock).eventDataPathRequest(event.discoverySessionId,
                event.peerDiscMacAddr, event.ndpInstanceId, event.appInfo, null);
    }

    @Test
    public void testEventDataPathConfirm() {
        NanDataPathConfirmInd event = new NanDataPathConfirmInd();
        event.ndpInstanceId = 10;
        event.peerNdiMacAddr = new byte[]{0x01, 0x02, 0x03, 0x04, 0x05, 0x06};
        event.dataPathSetupSuccess = true;
        event.status = new NanStatus();
        event.status.status = NanStatusCode.SUCCESS;
        event.appInfo = new byte[]{0x10, 0x11};
        event.channelInfo = new android.hardware.wifi.NanDataPathChannelInfo[0];

        mDut.eventDataPathConfirm(event);

        verify(mFrameworkCallbackMock).eventDataPathConfirm(
                eq(WifiNanIface.NanStatusCode.SUCCESS), eq(event.ndpInstanceId),
                eq(event.dataPathSetupSuccess), eq(event.peerNdiMacAddr), eq(event.appInfo), any()
        );
    }

    @Test
    public void testNotifyEnableResponse() {
        short id = 2;
        NanStatus status = new NanStatus();
        status.status = NanStatusCode.SUCCESS;

        mDut.notifyEnableResponse((char) id, status);

        verify(mFrameworkCallbackMock).notifyEnableResponse(id, WifiNanIface.NanStatusCode.SUCCESS);
    }

    @Test
    public void testEventDisabled() {
        NanStatus status = new NanStatus();
        status.status = NanStatusCode.UNSUPPORTED_CONCURRENCY_NAN_DISABLED;

        mDut.eventDisabled(status);

        verify(mFrameworkCallbackMock).eventDisabled(
                WifiNanIface.NanStatusCode.UNSUPPORTED_CONCURRENCY_NAN_DISABLED);
    }
}
