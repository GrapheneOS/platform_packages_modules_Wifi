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

package com.android.server.wifi.hal;

import android.hardware.wifi.IWifiNanIfaceEventCallback;
import android.hardware.wifi.NanCapabilities;
import android.hardware.wifi.NanCipherSuiteType;
import android.hardware.wifi.NanClusterEventInd;
import android.hardware.wifi.NanDataPathChannelInfo;
import android.hardware.wifi.NanDataPathConfirmInd;
import android.hardware.wifi.NanDataPathRequestInd;
import android.hardware.wifi.NanDataPathScheduleUpdateInd;
import android.hardware.wifi.NanFollowupReceivedInd;
import android.hardware.wifi.NanMatchInd;
import android.hardware.wifi.NanStatus;
import android.hardware.wifi.NanStatusCode;
import android.hardware.wifi.WifiChannelWidthInMhz;
import android.net.MacAddress;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiAnnotations;
import android.net.wifi.aware.Characteristics;
import android.net.wifi.aware.WifiAwareChannelInfo;
import android.net.wifi.util.HexEncoding;
import android.util.Log;

import com.android.server.wifi.aware.Capabilities;
import com.android.server.wifi.hal.WifiNanIface.NanClusterEventType;
import com.android.server.wifi.hal.WifiNanIface.NanRangingIndication;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Callback registered with the Vendor HAL service. On events, converts arguments
 * to their framework equivalents and calls the registered framework callback.
 */
public class WifiNanIfaceCallbackAidlImpl extends IWifiNanIfaceEventCallback.Stub {
    private static final String TAG = "WifiNanIfaceCallbackAidlImpl";

    private boolean mVerboseLoggingEnabled;
    private WifiNanIfaceAidlImpl mWifiNanIface;

    public WifiNanIfaceCallbackAidlImpl(WifiNanIfaceAidlImpl wifiNanIface) {
        mWifiNanIface = wifiNanIface;
    }

    /**
     * Enable verbose logging.
     */
    public void enableVerboseLogging(boolean verbose) {
        mVerboseLoggingEnabled = verbose;
    }

    @Override
    public void notifyCapabilitiesResponse(char id, NanStatus status,
            NanCapabilities capabilities) {
        if (!checkFrameworkCallback()) return;
        if (mVerboseLoggingEnabled) {
            Log.v(TAG, "notifyCapabilitiesResponse: id=" + id + ", status="
                    + statusString(status) + ", capabilities=" + capabilities);
        }

        if (status.status == NanStatusCode.SUCCESS) {
            Capabilities frameworkCapabilities = toFrameworkCapability(capabilities);
            mWifiNanIface.getFrameworkCallback().notifyCapabilitiesResponse(
                    (short) id, frameworkCapabilities);
        } else {
            Log.e(TAG, "notifyCapabilitiesResponse: error code=" + status.status + " ("
                    + status.description + ")");
        }
    }

    @Override
    public void notifyEnableResponse(char id, NanStatus status) {
        if (!checkFrameworkCallback()) return;
        if (mVerboseLoggingEnabled) {
            Log.v(TAG, "notifyEnableResponse: id=" + id + ", status=" + statusString(status));
        }

        if (status.status == NanStatusCode.ALREADY_ENABLED) {
            Log.wtf(TAG, "notifyEnableResponse: id=" + id + ", already enabled!?");
        }
        mWifiNanIface.getFrameworkCallback().notifyEnableResponse(
                (short) id, WifiNanIface.NanStatusCode.fromAidl(status.status));
    }

    @Override
    public void notifyConfigResponse(char id, NanStatus status) {
        if (!checkFrameworkCallback()) return;
        if (mVerboseLoggingEnabled) {
            Log.v(TAG, "notifyConfigResponse: id=" + id + ", status=" + statusString(status));
        }
        mWifiNanIface.getFrameworkCallback().notifyConfigResponse(
                (short) id, WifiNanIface.NanStatusCode.fromAidl(status.status));
    }

    @Override
    public void notifyDisableResponse(char id, NanStatus status) {
        if (!checkFrameworkCallback()) return;
        if (mVerboseLoggingEnabled) {
            Log.v(TAG, "notifyDisableResponse: id=" + id + ", status=" + statusString(status));
        }

        if (status.status != NanStatusCode.SUCCESS) {
            Log.e(TAG, "notifyDisableResponse: failure - code=" + status.status + " ("
                    + status.description + ")");
        }
        mWifiNanIface.getFrameworkCallback().notifyDisableResponse(
                (short) id, WifiNanIface.NanStatusCode.fromAidl(status.status));
    }

    @Override
    public void notifyStartPublishResponse(char id, NanStatus status, byte publishId) {
        if (!checkFrameworkCallback()) return;
        if (mVerboseLoggingEnabled) {
            Log.v(TAG, "notifyStartPublishResponse: id=" + id + ", status=" + statusString(status)
                    + ", publishId=" + publishId);
        }
        mWifiNanIface.getFrameworkCallback().notifyStartPublishResponse(
                (short) id, WifiNanIface.NanStatusCode.fromAidl(status.status), publishId);
    }

    @Override
    public void notifyStopPublishResponse(char id, NanStatus status) {
        if (!checkFrameworkCallback()) return;
        if (mVerboseLoggingEnabled) {
            Log.v(TAG, "notifyStopPublishResponse: id=" + id + ", status=" + statusString(status));
        }

        if (status.status == NanStatusCode.SUCCESS) {
            // NOP
        } else {
            Log.e(TAG, "notifyStopPublishResponse: failure - code=" + status.status + " ("
                    + status.description + ")");
        }
    }

    @Override
    public void notifyStartSubscribeResponse(char id, NanStatus status, byte subscribeId) {
        if (!checkFrameworkCallback()) return;
        if (mVerboseLoggingEnabled) {
            Log.v(TAG, "notifyStartSubscribeResponse: id=" + id + ", status=" + statusString(status)
                    + ", subscribeId=" + subscribeId);
        }
        mWifiNanIface.getFrameworkCallback().notifyStartSubscribeResponse(
                (short) id, WifiNanIface.NanStatusCode.fromAidl(status.status), subscribeId);
    }

    @Override
    public void notifyStopSubscribeResponse(char id, NanStatus status) {
        if (!checkFrameworkCallback()) return;
        if (mVerboseLoggingEnabled) {
            Log.v(TAG, "notifyStopSubscribeResponse: id=" + id + ", status="
                    + statusString(status));
        }

        if (status.status == NanStatusCode.SUCCESS) {
            // NOP
        } else {
            Log.e(TAG, "notifyStopSubscribeResponse: failure - code=" + status.status + " ("
                    + status.description + ")");
        }
    }

    @Override
    public void notifyTransmitFollowupResponse(char id, NanStatus status) {
        if (!checkFrameworkCallback()) return;
        if (mVerboseLoggingEnabled) {
            Log.v(TAG, "notifyTransmitFollowupResponse: id=" + id + ", status="
                    + statusString(status));
        }
        mWifiNanIface.getFrameworkCallback().notifyTransmitFollowupResponse(
                (short) id, WifiNanIface.NanStatusCode.fromAidl(status.status));
    }

    @Override
    public void notifyCreateDataInterfaceResponse(char id, NanStatus status) {
        if (!checkFrameworkCallback()) return;
        if (mVerboseLoggingEnabled) {
            Log.v(TAG, "notifyCreateDataInterfaceResponse: id=" + id + ", status="
                    + statusString(status));
        }
        mWifiNanIface.getFrameworkCallback().notifyCreateDataInterfaceResponse(
                (short) id, WifiNanIface.NanStatusCode.fromAidl(status.status));
    }

    @Override
    public void notifyDeleteDataInterfaceResponse(char id, NanStatus status) {
        if (!checkFrameworkCallback()) return;
        if (mVerboseLoggingEnabled) {
            Log.v(TAG, "notifyDeleteDataInterfaceResponse: id=" + id + ", status="
                    + statusString(status));
        }
        mWifiNanIface.getFrameworkCallback().notifyDeleteDataInterfaceResponse(
                (short) id, WifiNanIface.NanStatusCode.fromAidl(status.status));
    }

    @Override
    public void notifyInitiateDataPathResponse(char id, NanStatus status,
            int ndpInstanceId) {
        if (!checkFrameworkCallback()) return;
        if (mVerboseLoggingEnabled) {
            Log.v(TAG, "notifyInitiateDataPathResponse: id=" + id + ", status="
                    + statusString(status) + ", ndpInstanceId=" + ndpInstanceId);
        }
        mWifiNanIface.getFrameworkCallback().notifyInitiateDataPathResponse(
                (short) id, WifiNanIface.NanStatusCode.fromAidl(status.status), ndpInstanceId);
    }

    @Override
    public void notifyRespondToDataPathIndicationResponse(char id, NanStatus status) {
        if (!checkFrameworkCallback()) return;
        if (mVerboseLoggingEnabled) {
            Log.v(TAG, "notifyRespondToDataPathIndicationResponse: id=" + id
                    + ", status=" + statusString(status));
        }
        mWifiNanIface.getFrameworkCallback().notifyRespondToDataPathIndicationResponse(
                (short) id, WifiNanIface.NanStatusCode.fromAidl(status.status));
    }

    @Override
    public void notifyTerminateDataPathResponse(char id, NanStatus status) {
        if (!checkFrameworkCallback()) return;
        if (mVerboseLoggingEnabled) {
            Log.v(TAG, "notifyTerminateDataPathResponse: id=" + id + ", status="
                    + statusString(status));
        }
        mWifiNanIface.getFrameworkCallback().notifyTerminateDataPathResponse(
                (short) id, WifiNanIface.NanStatusCode.fromAidl(status.status));
    }

    @Override
    public void eventClusterEvent(NanClusterEventInd event) {
        if (!checkFrameworkCallback()) return;
        if (mVerboseLoggingEnabled) {
            Log.v(TAG, "eventClusterEvent: eventType=" + event.eventType + ", addr="
                    + String.valueOf(HexEncoding.encode(event.addr)));
        }
        mWifiNanIface.getFrameworkCallback().eventClusterEvent(
                NanClusterEventType.fromAidl(event.eventType), event.addr);
    }

    @Override
    public void eventDisabled(NanStatus status) {
        if (!checkFrameworkCallback()) return;
        if (mVerboseLoggingEnabled) Log.v(TAG, "eventDisabled: status=" + statusString(status));
        mWifiNanIface.getFrameworkCallback().eventDisabled(
                WifiNanIface.NanStatusCode.fromAidl(status.status));
    }

    @Override
    public void eventPublishTerminated(byte sessionId, NanStatus status) {
        if (!checkFrameworkCallback()) return;
        if (mVerboseLoggingEnabled) {
            Log.v(TAG, "eventPublishTerminated: sessionId=" + sessionId + ", status="
                    + statusString(status));
        }
        mWifiNanIface.getFrameworkCallback().eventPublishTerminated(
                sessionId, WifiNanIface.NanStatusCode.fromAidl(status.status));
    }

    @Override
    public void eventSubscribeTerminated(byte sessionId, NanStatus status) {
        if (!checkFrameworkCallback()) return;
        if (mVerboseLoggingEnabled) {
            Log.v(TAG, "eventSubscribeTerminated: sessionId=" + sessionId + ", status="
                    + statusString(status));
        }
        mWifiNanIface.getFrameworkCallback().eventSubscribeTerminated(
                sessionId, WifiNanIface.NanStatusCode.fromAidl(status.status));
    }

    @Override
    public void eventMatch(NanMatchInd event) {
        if (!checkFrameworkCallback()) return;
        if (mVerboseLoggingEnabled) {
            Log.v(TAG, "eventMatch: discoverySessionId=" + event.discoverySessionId
                    + ", peerId=" + event.peerId
                    + ", addr=" + String.valueOf(HexEncoding.encode(event.addr))
                    + ", serviceSpecificInfo=" + Arrays.toString(event.serviceSpecificInfo)
                    + ", ssi.size()=" + (event.serviceSpecificInfo == null
                            ? 0 : event.serviceSpecificInfo.length)
                    + ", matchFilter=" + Arrays.toString(event.matchFilter)
                    + ", mf.size()=" + (event.matchFilter == null ? 0 : event.matchFilter.length)
                    + ", rangingIndicationType=" + event.rangingIndicationType
                    + ", rangingMeasurementInMm=" + event.rangingMeasurementInMm + ", "
                    + "scid=" + Arrays.toString(event.scid));
        }
        mWifiNanIface.getFrameworkCallback().eventMatch(event.discoverySessionId, event.peerId,
                event.addr, event.serviceSpecificInfo, event.matchFilter,
                NanRangingIndication.fromAidl(event.rangingIndicationType),
                event.rangingMeasurementInMm, event.scid,
                toPublicCipherSuites(event.peerCipherType));
    }

    @Override
    public void eventMatchExpired(byte discoverySessionId, int peerId) {
        if (!checkFrameworkCallback()) return;
        if (mVerboseLoggingEnabled) {
            Log.v(TAG, "eventMatchExpired: discoverySessionId=" + discoverySessionId
                    + ", peerId=" + peerId);
        }
        mWifiNanIface.getFrameworkCallback().eventMatchExpired(discoverySessionId, peerId);
    }

    @Override
    public void eventFollowupReceived(NanFollowupReceivedInd event) {
        if (!checkFrameworkCallback()) return;
        if (mVerboseLoggingEnabled) {
            Log.v(TAG, "eventFollowupReceived: discoverySessionId=" + event.discoverySessionId
                    + ", peerId=" + event.peerId + ", addr=" + String.valueOf(
                    HexEncoding.encode(event.addr)) + ", serviceSpecificInfo=" + Arrays.toString(
                    event.serviceSpecificInfo) + ", ssi.size()="
                    + (event.serviceSpecificInfo == null ? 0 : event.serviceSpecificInfo.length));
        }
        mWifiNanIface.getFrameworkCallback().eventFollowupReceived(
                event.discoverySessionId, event.peerId, event.addr, event.serviceSpecificInfo);
    }

    @Override
    public void eventTransmitFollowup(char id, NanStatus status) {
        if (!checkFrameworkCallback()) return;
        if (mVerboseLoggingEnabled) {
            Log.v(TAG, "eventTransmitFollowup: id=" + id + ", status=" + statusString(status));
        }
        mWifiNanIface.getFrameworkCallback().eventTransmitFollowup(
                (short) id, WifiNanIface.NanStatusCode.fromAidl(status.status));
    }

    @Override
    public void eventDataPathRequest(NanDataPathRequestInd event) {
        if (!checkFrameworkCallback()) return;
        if (mVerboseLoggingEnabled) {
            Log.v(TAG, "eventDataPathRequest: discoverySessionId=" + event.discoverySessionId
                    + ", peerDiscMacAddr=" + String.valueOf(
                    HexEncoding.encode(event.peerDiscMacAddr)) + ", ndpInstanceId="
                    + event.ndpInstanceId + ", appInfo.size()=" + event.appInfo.length);
        }
        mWifiNanIface.getFrameworkCallback().eventDataPathRequest(event.discoverySessionId,
                event.peerDiscMacAddr, event.ndpInstanceId, event.appInfo);
    }

    @Override
    public void eventDataPathConfirm(NanDataPathConfirmInd event) {
        if (!checkFrameworkCallback()) return;
        if (mVerboseLoggingEnabled) {
            Log.v(TAG, "eventDataPathConfirm: ndpInstanceId=" + event.ndpInstanceId
                    + ", peerNdiMacAddr=" + String.valueOf(
                    HexEncoding.encode(event.peerNdiMacAddr)) + ", dataPathSetupSuccess="
                    + event.dataPathSetupSuccess + ", reason=" + event.status.status
                    + ", appInfo.size()=" + event.appInfo.length
                    + ", channelInfo" + Arrays.toString(event.channelInfo));
        }
        List<WifiAwareChannelInfo> wifiAwareChannelInfos = convertHalChannelInfo(event.channelInfo);
        mWifiNanIface.getFrameworkCallback().eventDataPathConfirm(
                WifiNanIface.NanStatusCode.fromAidl(event.status.status), event.ndpInstanceId,
                event.dataPathSetupSuccess, event.peerNdiMacAddr, event.appInfo,
                wifiAwareChannelInfos);
    }

    @Override
    public void eventDataPathScheduleUpdate(NanDataPathScheduleUpdateInd event) {
        if (!checkFrameworkCallback()) return;
        if (mVerboseLoggingEnabled) {
            Log.v(TAG, "eventDataPathScheduleUpdate: peerMac="
                    + MacAddress.fromBytes(event.peerDiscoveryAddress)
                    + ", ndpIds=" + Arrays.toString(event.ndpInstanceIds)
                    + ", channelInfo=" + Arrays.toString(event.channelInfo));
        }
        List<WifiAwareChannelInfo> wifiAwareChannelInfos = convertHalChannelInfo(event.channelInfo);
        ArrayList<Integer> ndpInstanceIds = new ArrayList<>();
        for (int i : event.ndpInstanceIds) {
            ndpInstanceIds.add(i);
        }
        mWifiNanIface.getFrameworkCallback().eventDataPathScheduleUpdate(
                event.peerDiscoveryAddress, ndpInstanceIds, wifiAwareChannelInfos);
    }

    @Override
    public void eventDataPathTerminated(int ndpInstanceId) {
        if (!checkFrameworkCallback()) return;
        if (mVerboseLoggingEnabled) {
            Log.v(TAG, "eventDataPathTerminated: ndpInstanceId=" + ndpInstanceId);
        }
        mWifiNanIface.getFrameworkCallback().eventDataPathTerminated(ndpInstanceId);
    }

    @Override
    public String getInterfaceHash() {
        return IWifiNanIfaceEventCallback.HASH;
    }

    @Override
    public int getInterfaceVersion() {
        return IWifiNanIfaceEventCallback.VERSION;
    }

    private Capabilities toFrameworkCapability(NanCapabilities capabilities) {
        Capabilities frameworkCapabilities = new Capabilities();
        frameworkCapabilities.maxConcurrentAwareClusters = capabilities.maxConcurrentClusters;
        frameworkCapabilities.maxPublishes = capabilities.maxPublishes;
        frameworkCapabilities.maxSubscribes = capabilities.maxSubscribes;
        frameworkCapabilities.maxServiceNameLen = capabilities.maxServiceNameLen;
        frameworkCapabilities.maxMatchFilterLen = capabilities.maxMatchFilterLen;
        frameworkCapabilities.maxTotalMatchFilterLen = capabilities.maxTotalMatchFilterLen;
        frameworkCapabilities.maxServiceSpecificInfoLen =
                capabilities.maxServiceSpecificInfoLen;
        frameworkCapabilities.maxExtendedServiceSpecificInfoLen =
                capabilities.maxExtendedServiceSpecificInfoLen;
        frameworkCapabilities.maxNdiInterfaces = capabilities.maxNdiInterfaces;
        frameworkCapabilities.maxNdpSessions = capabilities.maxNdpSessions;
        frameworkCapabilities.maxAppInfoLen = capabilities.maxAppInfoLen;
        frameworkCapabilities.maxQueuedTransmitMessages =
                capabilities.maxQueuedTransmitFollowupMsgs;
        frameworkCapabilities.maxSubscribeInterfaceAddresses =
                capabilities.maxSubscribeInterfaceAddresses;
        frameworkCapabilities.supportedCipherSuites = toPublicCipherSuites(
                capabilities.supportedCipherSuites);
        frameworkCapabilities.isInstantCommunicationModeSupported =
                capabilities.instantCommunicationModeSupportFlag;
        return frameworkCapabilities;
    }

    private int toPublicCipherSuites(int nativeCipherSuites) {
        int publicCipherSuites = 0;

        if ((nativeCipherSuites & NanCipherSuiteType.SHARED_KEY_128_MASK) != 0) {
            publicCipherSuites |= Characteristics.WIFI_AWARE_CIPHER_SUITE_NCS_SK_128;
        }
        if ((nativeCipherSuites & NanCipherSuiteType.SHARED_KEY_256_MASK) != 0) {
            publicCipherSuites |= Characteristics.WIFI_AWARE_CIPHER_SUITE_NCS_SK_256;
        }
        if ((nativeCipherSuites & NanCipherSuiteType.PUBLIC_KEY_128_MASK) != 0) {
            publicCipherSuites |= Characteristics.WIFI_AWARE_CIPHER_SUITE_NCS_PK_128;
        }
        if ((nativeCipherSuites & NanCipherSuiteType.PUBLIC_KEY_256_MASK) != 0) {
            publicCipherSuites |= Characteristics.WIFI_AWARE_CIPHER_SUITE_NCS_PK_256;
        }

        return publicCipherSuites;
    }

    private static String statusString(NanStatus status) {
        if (status == null) {
            return "status=null";
        }
        StringBuilder sb = new StringBuilder();
        sb.append(status.status).append(" (").append(status.description).append(")");
        return sb.toString();
    }

    /**
     * Convert HAL channelBandwidth to framework enum
     */
    private @WifiAnnotations.ChannelWidth int getChannelBandwidthFromHal(int channelBandwidth) {
        switch(channelBandwidth) {
            case WifiChannelWidthInMhz.WIDTH_40:
                return ScanResult.CHANNEL_WIDTH_40MHZ;
            case WifiChannelWidthInMhz.WIDTH_80:
                return ScanResult.CHANNEL_WIDTH_80MHZ;
            case WifiChannelWidthInMhz.WIDTH_160:
                return ScanResult.CHANNEL_WIDTH_160MHZ;
            case WifiChannelWidthInMhz.WIDTH_80P80:
                return ScanResult.CHANNEL_WIDTH_80MHZ_PLUS_MHZ;
            case WifiChannelWidthInMhz.WIDTH_320:
                return ScanResult.CHANNEL_WIDTH_320MHZ;
            default:
                return ScanResult.CHANNEL_WIDTH_20MHZ;
        }
    }

    /**
     * Convert HAL NanDataPathChannelInfo to WifiAwareChannelInfo
     */
    private List<WifiAwareChannelInfo> convertHalChannelInfo(
            NanDataPathChannelInfo[] channelInfos) {
        List<WifiAwareChannelInfo> wifiAwareChannelInfos = new ArrayList<>();
        if (channelInfos == null) {
            return null;
        }
        for (android.hardware.wifi.NanDataPathChannelInfo channelInfo : channelInfos) {
            wifiAwareChannelInfos.add(new WifiAwareChannelInfo(channelInfo.channelFreq,
                    getChannelBandwidthFromHal(channelInfo.channelBandwidth),
                    channelInfo.numSpatialStreams));
        }
        return wifiAwareChannelInfos;
    }

    private boolean checkFrameworkCallback() {
        if (mWifiNanIface == null) {
            Log.e(TAG, "mWifiNanIface is null");
            return false;
        } else if (mWifiNanIface.getFrameworkCallback() == null) {
            Log.e(TAG, "Framework callback is null");
            return false;
        }
        return true;
    }
}
