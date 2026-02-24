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

import static com.android.server.wifi.aware.WifiAwareStateManager.NAN_BOOTSTRAPPING_ACCEPT;
import static com.android.server.wifi.aware.WifiAwareStateManager.NAN_BOOTSTRAPPING_COMEBACK;
import static com.android.server.wifi.aware.WifiAwareStateManager.NAN_BOOTSTRAPPING_REJECT;
import static com.android.server.wifi.aware.WifiAwareStateManager.NAN_PAIRING_AKM_PASN;
import static com.android.server.wifi.aware.WifiAwareStateManager.NAN_PAIRING_AKM_SAE;
import static com.android.server.wifi.aware.WifiAwareStateManager.NAN_PAIRING_REQUEST_TYPE_SETUP;
import static com.android.server.wifi.aware.WifiAwareStateManager.NAN_PAIRING_REQUEST_TYPE_VERIFICATION;

import android.annotation.NonNull;
import android.annotation.RequiresNoPermission;
import android.net.wifi.aware.AwarePairingConfig;
import android.net.wifi.aware.Characteristics;
import android.net.wifi.aware.WifiAwareChannelInfo;
import android.net.wifi.util.HexEncoding;
import android.os.RemoteException;
import android.system.wifi.mainline_supplicant.ISupplicantNanIfaceEventCallback;
import android.system.wifi.mainline_supplicant.NanBootstrappingConfirmInd;
import android.system.wifi.mainline_supplicant.NanBootstrappingConfirmInd.NanBootstrappingResponseCode;
import android.system.wifi.mainline_supplicant.NanBootstrappingMethod;
import android.system.wifi.mainline_supplicant.NanBootstrappingRequestInd;
import android.system.wifi.mainline_supplicant.NanCapabilities;
import android.system.wifi.mainline_supplicant.NanCipherSuiteType;
import android.system.wifi.mainline_supplicant.NanClusterEventInd;
import android.system.wifi.mainline_supplicant.NanDataPathChannelInfo;
import android.system.wifi.mainline_supplicant.NanDataPathConfirmInd;
import android.system.wifi.mainline_supplicant.NanDataPathRequestInd;
import android.system.wifi.mainline_supplicant.NanDataPathScheduleUpdateInd;
import android.system.wifi.mainline_supplicant.NanFollowupReceivedInd;
import android.system.wifi.mainline_supplicant.NanMatchInd;
import android.system.wifi.mainline_supplicant.NanPairingAkm;
import android.system.wifi.mainline_supplicant.NanPairingConfig;
import android.system.wifi.mainline_supplicant.NanPairingConfirmInd;
import android.system.wifi.mainline_supplicant.NanPairingRequestInd;
import android.system.wifi.mainline_supplicant.NanPairingRequestType;
import android.system.wifi.mainline_supplicant.NanStatus;
import android.system.wifi.mainline_supplicant.NanStatus.NanStatusCode;
import android.system.wifi.mainline_supplicant.NpkSecurityAssociation;
import android.util.Log;

import com.android.server.wifi.hal.WifiNanIface;
import com.android.server.wifi.hal.WifiRttControllerAidlImpl;
import com.android.server.wifi.util.HalAidlUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Implementation using the callback interface for mainline supplicant.
 */
public class AwareIfaceCallbackSupplicantImpl extends ISupplicantNanIfaceEventCallback.Stub {
    private static final String TAG = "AwareCallbackSupplicant";

    private static final int SUPPORTED_RX_CHAINS_1 = 1;
    private static final int SUPPORTED_RX_CHAINS_2 = 2;
    private static final int SUPPORTED_RX_CHAINS_3 = 3;
    private static final int SUPPORTED_RX_CHAINS_4 = 4;

    private final WifiNanIface.Callback mFrameworkCallback;
    private boolean mVerboseLoggingEnabled;

    /**
     * Constructor.
     *
     * @param frameworkCallback The framework callback to use for the Aware interface.
     */
    public AwareIfaceCallbackSupplicantImpl(@NonNull WifiNanIface.Callback frameworkCallback) {
        mFrameworkCallback = frameworkCallback;
    }

    /**
     * Enable verbose logging.
     */
    public void enableVerboseLogging(boolean verbose) {
        mVerboseLoggingEnabled = verbose;
    }


    @Override
    @RequiresNoPermission
    public void eventClusterEvent(@NonNull NanClusterEventInd event) throws RemoteException {
        if (mVerboseLoggingEnabled) {
            Log.v(TAG, "eventClusterEvent: eventType=" + event.eventType + ", addr="
                    + String.valueOf(HexEncoding.encode(event.addr)));
        }
        mFrameworkCallback.eventClusterEvent(
                WifiNanIface.NanClusterEventType.fromAidl(event.eventType), event.addr);
    }

    @Override
    @RequiresNoPermission
    public void eventMatch(@NonNull NanMatchInd event) throws RemoteException {
        byte[] serviceSpecificInfo = event.serviceSpecificInfo;
        boolean isExtendedServiceSpecificInfo = false;
        if (serviceSpecificInfo == null || serviceSpecificInfo.length == 0) {
            serviceSpecificInfo = event.extendedServiceSpecificInfo;
            isExtendedServiceSpecificInfo = true;
        }
        if (mVerboseLoggingEnabled) {
            Log.v(
                    TAG,
                    "eventMatch: discoverySessionId="
                            + event.discoverySessionId
                            + ", peerId="
                            + event.peerId
                            + ", addr="
                            + String.valueOf(HexEncoding.encode(event.addr))
                            + ", isExtendedServiceSpecificInfo="
                            + isExtendedServiceSpecificInfo
                            + ", serviceSpecificInfo="
                            + Arrays.toString(serviceSpecificInfo)
                            + ", ssi.size()="
                            + (serviceSpecificInfo == null ? 0 : serviceSpecificInfo.length)
                            + ", matchFilter="
                            + Arrays.toString(event.matchFilter)
                            + ", mf.size()="
                            + (event.matchFilter == null ? 0 : event.matchFilter.length)
                            + ", rangingIndicationType="
                            + event.rangingIndicationType
                            + ", rangingMeasurementInMm="
                            + event.rangingMeasurementInMm
                            + ", "
                            + "scid="
                            + Arrays.toString(event.scid));
        }
        mFrameworkCallback.eventMatch(
                event.discoverySessionId,
                event.peerId,
                event.addr,
                serviceSpecificInfo,
                event.matchFilter,
                WifiNanIface.NanRangingIndication.fromAidl(event.rangingIndicationType),
                event.rangingMeasurementInMm,
                event.scid,
                toPublicDataPathCipherSuites(event.peerCipherType)
                        | toPublicPairingCipherSuites(event.peerCipherType),
                event.peerNira.nonce,
                event.peerNira.tag,
                createPublicPairingConfig(event.peerPairingConfig, event.peerCipherType),
                null);
    }

    private AwarePairingConfig createPublicPairingConfig(NanPairingConfig nativePairingConfig,
            int cipherSuites) {
        return new AwarePairingConfig(nativePairingConfig.enablePairingSetup,
                nativePairingConfig.enablePairingCache,
                nativePairingConfig.enablePairingVerification,
                toBootStrappingMethods(nativePairingConfig.supportedBootstrappingMethods),
                toPublicPairingCipherSuites(cipherSuites));
    }

    @Override
    @RequiresNoPermission
    public void eventMatchExpired(byte discoverySessionId, int peerId) throws RemoteException {
        if (mVerboseLoggingEnabled) {
            Log.v(TAG, "eventMatchExpired: discoverySessionId=" + discoverySessionId
                    + ", peerId=" + peerId);
        }
        mFrameworkCallback.eventMatchExpired(discoverySessionId, peerId);
    }

    @Override
    @RequiresNoPermission
    public void eventPublishTerminated(byte sessionId, @NonNull NanStatus status)
            throws RemoteException {
        if (mVerboseLoggingEnabled) {
            Log.v(TAG, "eventPublishTerminated: sessionId=" + sessionId + ", status="
                    + statusString(status));
        }
        mFrameworkCallback.eventPublishTerminated(
                sessionId, WifiNanIface.NanStatusCode.fromAidl(status.status));
    }

    @Override
    @RequiresNoPermission
    public void eventSubscribeTerminated(byte sessionId, @NonNull NanStatus status)
            throws RemoteException {
        if (mVerboseLoggingEnabled) {
            Log.v(TAG, "eventSubscribeTerminated: sessionId=" + sessionId + ", status="
                    + statusString(status));
        }
        mFrameworkCallback.eventSubscribeTerminated(
                sessionId, WifiNanIface.NanStatusCode.fromAidl(status.status));
    }

    @Override
    @RequiresNoPermission
    public void eventTransmitFollowup(char id, @NonNull NanStatus status) throws RemoteException {
        if (mVerboseLoggingEnabled) {
            Log.v(TAG, "eventTransmitFollowup: id=" + id + ", status=" + statusString(status));
        }
        mFrameworkCallback.eventTransmitFollowup(
                (short) id, WifiNanIface.NanStatusCode.fromAidl(status.status));
    }

    @Override
    @RequiresNoPermission
    public void eventFollowupReceived(@NonNull NanFollowupReceivedInd event)
            throws RemoteException {
        byte[] serviceSpecificInfo = event.serviceSpecificInfo;
        boolean isExtendedServiceSpecificInfo = false;
        if (serviceSpecificInfo == null || serviceSpecificInfo.length == 0) {
            serviceSpecificInfo = event.extendedServiceSpecificInfo;
            isExtendedServiceSpecificInfo = true;
        }

        if (mVerboseLoggingEnabled) {
            Log.v(
                    TAG,
                    "eventFollowupReceived: discoverySessionId="
                            + event.discoverySessionId
                            + ", peerId="
                            + event.peerId
                            + ", addr="
                            + String.valueOf(HexEncoding.encode(event.addr))
                            + ", isExtendedServiceSpecificInfo="
                            + isExtendedServiceSpecificInfo
                            + ", serviceSpecificInfo="
                            + Arrays.toString(serviceSpecificInfo)
                            + ", ssi.size()="
                            + (serviceSpecificInfo == null ? 0 : serviceSpecificInfo.length));
        }
        mFrameworkCallback
                .eventFollowupReceived(
                        event.discoverySessionId, event.peerId, event.addr, serviceSpecificInfo);
    }

    @Override
    @RequiresNoPermission
    public void eventDataPathConfirm(@NonNull NanDataPathConfirmInd event) throws RemoteException {
        if (mVerboseLoggingEnabled) {
            Log.v(TAG, "eventDataPathConfirm: ndpInstanceId=" + event.ndpInstanceId
                    + ", reason=" + event.status.description);
        }
        List<WifiAwareChannelInfo> wifiAwareChannelInfos =
                convertAidlChannelInfo(event.channelInfo);
        mFrameworkCallback.eventDataPathConfirm(
                WifiNanIface.NanStatusCode.fromAidl(event.status.status),
                event.ndpInstanceId, event.dataPathSetupSuccess, event.peerNdiMacAddr,
                event.appInfo, wifiAwareChannelInfos);
    }

    @Override
    @RequiresNoPermission
    public void eventDataPathRequest(@NonNull NanDataPathRequestInd event) throws RemoteException {
        if (mVerboseLoggingEnabled) {
            Log.v(TAG, "eventDataPathRequest: discoverySessionId=" + event.discoverySessionId
                    + ", ndpInstanceId=" + event.ndpInstanceId);
        }
        mFrameworkCallback.eventDataPathRequest(event.discoverySessionId,
                event.peerDiscMacAddr, event.ndpInstanceId, event.appInfo, event.ndiInitMac);
    }

    @Override
    @RequiresNoPermission
    public void eventDataPathScheduleUpdate(@NonNull NanDataPathScheduleUpdateInd event)
            throws RemoteException {
        // Not implemented.
    }

    @Override
    @RequiresNoPermission
    public void eventDataPathTerminated(int ndpInstanceId) throws RemoteException {
        if (mVerboseLoggingEnabled) {
            Log.v(TAG, "eventDataPathTerminated: ndpInstanceId=" + ndpInstanceId);
        }
        mFrameworkCallback.eventDataPathTerminated(ndpInstanceId);
    }

    @Override
    @RequiresNoPermission
    public void eventPairingConfirm(@NonNull NanPairingConfirmInd event) {
        if (mVerboseLoggingEnabled) {
            Log.v(TAG, "eventPairingConfirm: pairingInstanceId=" + event.pairingInstanceId
                    + ", status=" + event.status + ", requestType" + event.requestType);
        }
        mFrameworkCallback.eventPairingConfirm(event.pairingInstanceId,
                event.pairingSuccess, WifiNanIface.NanStatusCode.fromAidl(event.status.status),
                pairingRequestTypeFromAidl(event.requestType), event.enablePairingCache,
                createPairingSecurityAssociationInfo(event.npksa));
    }

    @Override
    @RequiresNoPermission
    public void eventPairingRequest(@NonNull NanPairingRequestInd event) throws RemoteException {
        if (mVerboseLoggingEnabled) {
            Log.v(TAG, "eventPairingRequest: pairingInstanceId=" + event.pairingInstanceId
                    + ", requestType=" + event.requestType);
        }
        mFrameworkCallback.eventPairingRequest(event.discoverySessionId,
                event.peerId, event.peerDiscMacAddr,
                event.pairingInstanceId, pairingRequestTypeFromAidl(event.requestType),
                event.enablePairingCache, event.peerNira.nonce, event.peerNira.tag);
    }

    @Override
    @RequiresNoPermission
    public void eventPairingSecurityAssociationReceived(@NonNull NpkSecurityAssociation npksa)
        throws RemoteException {
        if (mVerboseLoggingEnabled) {
            Log.v(TAG, "eventPairingSecurityAssociationReceived: ");
        }
        // TODO: pass the event information to upper layer
    }

    @Override
    @RequiresNoPermission
    public void notifyCapabilitiesResponse(char id, @NonNull NanStatus status,
            @NonNull NanCapabilities capabilities) throws RemoteException {
        if (mVerboseLoggingEnabled) {
            Log.v(TAG, "notifyCapabilitiesResponse: id=" + id + ", status="
                    + statusString(status) + ", capabilities=" + capabilities);
        }

        if (status.status == NanStatusCode.SUCCESS) {
            Capabilities frameworkCapabilities = toFrameworkCapability(capabilities);
            mFrameworkCallback.notifyCapabilitiesResponse(
                    (short) id, frameworkCapabilities);
        } else {
            Log.e(TAG, "notifyCapabilitiesResponse: error code=" + status.status + " ("
                    + status.description + ")");
        }
    }

    @Override
    @RequiresNoPermission
    public void notifyConfigResponse(char id, @NonNull NanStatus status) throws RemoteException {
        if (mVerboseLoggingEnabled) {
            Log.v(TAG, "notifyConfigResponse: id=" + id + ", status=" + statusString(status));
        }
        mFrameworkCallback.notifyConfigResponse(
                (short) id, WifiNanIface.NanStatusCode.fromAidl(status.status));
    }

    @Override
    @RequiresNoPermission
    public void notifyCreateDataInterfaceResponse(char id, @NonNull NanStatus status)
            throws RemoteException {
        if (mVerboseLoggingEnabled) {
            Log.v(TAG, "notifyCreateDataInterfaceResponse: id=" + id + ", status="
                    + statusString(status));
        }
        mFrameworkCallback.notifyCreateDataInterfaceResponse(
                (short) id, WifiNanIface.NanStatusCode.fromAidl(status.status));
    }

    @Override
    @RequiresNoPermission
    public void notifyDeleteDataInterfaceResponse(char id, @NonNull NanStatus status)
            throws RemoteException {
        if (mVerboseLoggingEnabled) {
            Log.v(TAG, "notifyDeleteDataInterfaceResponse: id=" + id + ", status="
                    + statusString(status));
        }
        mFrameworkCallback.notifyDeleteDataInterfaceResponse(
                (short) id, WifiNanIface.NanStatusCode.fromAidl(status.status));
    }

    @Override
    @RequiresNoPermission
    public void notifyEnableResponse(char id, @NonNull NanStatus status) throws RemoteException {
        if (mVerboseLoggingEnabled) {
            Log.v(TAG, "notifyEnableResponse: id=" + id + ", status=" + statusString(status));
        }

        if (status.status == NanStatusCode.ALREADY_ENABLED) {
            Log.wtf(TAG, "notifyEnableResponse: id=" + id + ", already enabled!?");
        }
        mFrameworkCallback.notifyEnableResponse(
                (short) id, WifiNanIface.NanStatusCode.fromAidl(status.status));
    }

    @Override
    @RequiresNoPermission
    public void notifyDisableResponse(char id, @NonNull NanStatus status) throws RemoteException {
        if (mVerboseLoggingEnabled) {
            Log.v(TAG, "notifyDisableResponse: id=" + id + ", status=" + statusString(status));
        }

        if (status.status != NanStatusCode.SUCCESS) {
            Log.e(TAG, "notifyDisableResponse: failure - code=" + status.status + " ("
                    + status.description + ")");
        }
        mFrameworkCallback.notifyDisableResponse(
                (short) id, WifiNanIface.NanStatusCode.fromAidl(status.status));
    }

    @Override
    @RequiresNoPermission
    public void notifyStartPublishResponse(char id, @NonNull NanStatus status, byte publishId)
            throws RemoteException {
        if (mVerboseLoggingEnabled) {
            Log.v(TAG, "notifyStartPublishResponse: id=" + id + ", status=" + statusString(status)
                    + ", publishId=" + publishId);
        }
        mFrameworkCallback.notifyStartPublishResponse(
                (short) id, WifiNanIface.NanStatusCode.fromAidl(status.status), publishId);
    }

    @Override
    @RequiresNoPermission
    public void notifyStartSubscribeResponse(char id, @NonNull NanStatus status, byte subscribeId)
            throws RemoteException {
        if (mVerboseLoggingEnabled) {
            Log.v(TAG, "notifyStartSubscribeResponse: id=" + id + ", status=" + statusString(status)
                    + ", subscribeId=" + subscribeId);
        }
        mFrameworkCallback.notifyStartSubscribeResponse(
                (short) id, WifiNanIface.NanStatusCode.fromAidl(status.status), subscribeId);
    }

    @Override
    @RequiresNoPermission
    public void notifyStopPublishResponse(char id, @NonNull NanStatus status)
            throws RemoteException {
        if (mVerboseLoggingEnabled) {
            Log.v(TAG, "notifyStopPublishResponse: id=" + id + ", status=" + statusString(status));
        }

        if (status.status != NanStatusCode.SUCCESS) {
            Log.e(TAG, "notifyStopPublishResponse: failure - code=" + status.status + " ("
                    + status.description + ")");
        }
    }

    @Override
    @RequiresNoPermission
    public void notifyStopSubscribeResponse(char id, @NonNull NanStatus status)
            throws RemoteException {
        if (mVerboseLoggingEnabled) {
            Log.v(TAG, "notifyStopSubscribeResponse: id=" + id + ", status="
                    + statusString(status));
        }

        if (status.status != NanStatusCode.SUCCESS) {
            Log.e(TAG, "notifyStopSubscribeResponse: failure - code=" + status.status + " ("
                    + status.description + ")");
        }
    }

    @Override
    @RequiresNoPermission
    public void notifyTransmitFollowupResponse(char id, @NonNull NanStatus status)
            throws RemoteException {
        if (mVerboseLoggingEnabled) {
            Log.v(TAG, "notifyTransmitFollowupResponse: id=" + id + ", status="
                    + statusString(status));
        }
        mFrameworkCallback.notifyTransmitFollowupResponse(
                (short) id, WifiNanIface.NanStatusCode.fromAidl(status.status));
    }

    @Override
    @RequiresNoPermission
    public void notifyInitiateBootstrappingResponse(char id, @NonNull NanStatus status,
            int bootstrappingInstanceId) throws RemoteException {
        if (mVerboseLoggingEnabled) {
            Log.v(TAG, "notifyInitiateBootstrappingResponse: id=" + id
                    + ", status=" + statusString(status));
        }
        mFrameworkCallback.notifyInitiateBootstrappingResponse(
                (short) id, WifiNanIface.NanStatusCode.fromAidl(status.status),
                bootstrappingInstanceId);
    }

    @Override
    @RequiresNoPermission
    public void notifyRespondToBootstrappingIndicationResponse(char id, @NonNull NanStatus status) {
        if (mVerboseLoggingEnabled) {
            Log.v(TAG, "notifyRespondToBootstrappingIndicationResponse: id=" + id
                    + ", status=" + statusString(status));
        }
        mFrameworkCallback.notifyRespondToBootstrappingIndicationResponse(
                (short) id, WifiNanIface.NanStatusCode.fromAidl(status.status));
    }

    @Override
    @RequiresNoPermission
    public void notifyInitiatePairingResponse(char id, @NonNull NanStatus status,
            int pairingInstanceId) throws RemoteException {
        if (mVerboseLoggingEnabled) {
            Log.v(TAG, "notifyInitiatePairingResponse: id=" + id + ", status="
                    + statusString(status) + ", pairingInstanceId=" + pairingInstanceId);
        }
        mFrameworkCallback.notifyInitiatePairingResponse((short) id,
                WifiNanIface.NanStatusCode.fromAidl(status.status), pairingInstanceId);
    }

    @Override
    @RequiresNoPermission
    public void notifyRespondToPairingIndicationResponse(char id, @NonNull NanStatus status)
            throws RemoteException {
        if (mVerboseLoggingEnabled) {
            Log.v(TAG, "notifyRespondToPairingIndicationResponse: id=" + id + ", status="
                    + statusString(status));
        }
        mFrameworkCallback.notifyRespondToPairingIndicationResponse(
                (short) id, WifiNanIface.NanStatusCode.fromAidl(status.status));
    }

    @Override
    @RequiresNoPermission
    public void notifyTerminatePairingResponse(char id, @NonNull NanStatus status)
            throws RemoteException {
        if (mVerboseLoggingEnabled) {
            Log.v(TAG, "notifyTerminatePairingResponse: id=" + id + ", status="
                    + statusString(status));
        }
        mFrameworkCallback.notifyTerminatePairingResponse((short) id,
                WifiNanIface.NanStatusCode.fromAidl(status.status));
    }

    @Override
    @RequiresNoPermission
    public void notifyInitiateDataPathResponse(char id, @NonNull NanStatus status,
            int ndpInstanceId) throws RemoteException {
        if (mVerboseLoggingEnabled) {
            Log.v(TAG, "notifyInitiateDataPathResponse: id=" + id + ", status="
                    + statusString(status) + ", ndpInstanceId=" + ndpInstanceId);
        }
        mFrameworkCallback.notifyInitiateDataPathResponse(
                (short) id, WifiNanIface.NanStatusCode.fromAidl(status.status), ndpInstanceId);
    }

    @Override
    @RequiresNoPermission
    public void notifyRespondToDataPathIndicationResponse(char id, @NonNull NanStatus status)
            throws RemoteException {
        if (mVerboseLoggingEnabled) {
            Log.v(TAG, "notifyRespondToDataPathIndicationResponse: id=" + id
                    + ", status=" + statusString(status));
        }
        mFrameworkCallback.notifyRespondToDataPathIndicationResponse(
                (short) id, WifiNanIface.NanStatusCode.fromAidl(status.status));
    }

    @Override
    @RequiresNoPermission
    public void notifyTerminateDataPathResponse(char id, @NonNull NanStatus status)
            throws RemoteException {
        if (mVerboseLoggingEnabled) {
            Log.v(TAG, "notifyTerminateDataPathResponse: id=" + id + ", status="
                    + statusString(status));
        }
        mFrameworkCallback.notifyTerminateDataPathResponse(
                (short) id, WifiNanIface.NanStatusCode.fromAidl(status.status));
    }

    @Override
    @RequiresNoPermission
    public void eventBootstrappingRequest(@NonNull NanBootstrappingRequestInd event) {
        if (mVerboseLoggingEnabled) {
            Log.v(TAG, "eventBootstrappingRequest: discoverySessionId="
                    + event.discoverySessionId + ", peerId=" + event.peerId);
        }
        mFrameworkCallback.eventBootstrappingRequest(event.discoverySessionId,
                event.peerId, event.peerDiscMacAddr, event.bootstrappingInstanceId,
                event.requestBootstrappingMethod, null);
    }

    @Override
    @RequiresNoPermission
    public void eventBootstrappingConfirm(@NonNull NanBootstrappingConfirmInd event) {
        if (mVerboseLoggingEnabled) {
            Log.v(TAG, "eventBootstrappingConfirm: bootstrappingInstanceId="
                    + event.bootstrappingInstanceId);
        }
        mFrameworkCallback.eventBootstrappingConfirm(
                event.discoverySessionId,
                event.bootstrappingInstanceId,
                convertAidlBootstrappingResponseCodeToFramework(event.responseCode),
                WifiNanIface.NanStatusCode.fromAidl(event.failureReasonCode.status),
                event.comeBackDelaySec, event.bootstrappingMethod, event.cookie,
		event.peerDiscMacAddr);
    }

    private static int toPublicDataPathCipherSuites(int nativeCipherSuites) {
        int publicCipherSuites = 0;

        if ((nativeCipherSuites & NanCipherSuiteType.SHARED_KEY_128_MASK) != 0) {
            publicCipherSuites |= Characteristics.WIFI_AWARE_CIPHER_SUITE_NCS_SK_128;
        }
        if ((nativeCipherSuites & NanCipherSuiteType.SHARED_KEY_256_MASK) != 0) {
            publicCipherSuites |= Characteristics.WIFI_AWARE_CIPHER_SUITE_NCS_SK_256;
        }
        if ((nativeCipherSuites & NanCipherSuiteType.PUBLIC_KEY_2WDH_128_MASK) != 0) {
            publicCipherSuites |= Characteristics.WIFI_AWARE_CIPHER_SUITE_NCS_PK_128;
        }
        if ((nativeCipherSuites & NanCipherSuiteType.PUBLIC_KEY_2WDH_256_MASK) != 0) {
            publicCipherSuites |= Characteristics.WIFI_AWARE_CIPHER_SUITE_NCS_PK_256;
        }

        return publicCipherSuites;
    }

    private static int toPublicPairingCipherSuites(int nativeCipherSuites) {
        int publicCipherSuites = 0;

        if ((nativeCipherSuites & NanCipherSuiteType.PUBLIC_KEY_PASN_128_MASK) != 0) {
            publicCipherSuites |= Characteristics.WIFI_AWARE_CIPHER_SUITE_NCS_PK_PASN_128;
        }
        if ((nativeCipherSuites & NanCipherSuiteType.PUBLIC_KEY_PASN_256_MASK) != 0) {
            publicCipherSuites |= Characteristics.WIFI_AWARE_CIPHER_SUITE_NCS_PK_PASN_256;
        }

        return publicCipherSuites;
    }

    private int toBootStrappingMethods(int nativeMethods) {
        int publicMethods = 0;

        if ((nativeMethods & NanBootstrappingMethod.OPPORTUNISTIC_MASK) != 0) {
            publicMethods |= AwarePairingConfig.PAIRING_BOOTSTRAPPING_OPPORTUNISTIC;
        }
        if ((nativeMethods & NanBootstrappingMethod.PIN_CODE_DISPLAY_MASK) != 0) {
            publicMethods |= AwarePairingConfig.PAIRING_BOOTSTRAPPING_PIN_CODE_DISPLAY;
        }
        if ((nativeMethods & NanBootstrappingMethod.PASSPHRASE_DISPLAY_MASK) != 0) {
            publicMethods |= AwarePairingConfig.PAIRING_BOOTSTRAPPING_PASSPHRASE_DISPLAY;
        }
        if ((nativeMethods & NanBootstrappingMethod.QR_DISPLAY_MASK) != 0) {
            publicMethods |= AwarePairingConfig.PAIRING_BOOTSTRAPPING_QR_DISPLAY;
        }
        if ((nativeMethods & NanBootstrappingMethod.NFC_TAG_MASK) != 0) {
            publicMethods |= AwarePairingConfig.PAIRING_BOOTSTRAPPING_NFC_TAG;
        }
        if ((nativeMethods & NanBootstrappingMethod.PIN_CODE_KEYPAD_MASK) != 0) {
            publicMethods |= AwarePairingConfig.PAIRING_BOOTSTRAPPING_PIN_CODE_KEYPAD;
        }
        if ((nativeMethods & NanBootstrappingMethod.PASSPHRASE_KEYPAD_MASK) != 0) {
            publicMethods |= AwarePairingConfig.PAIRING_BOOTSTRAPPING_PASSPHRASE_KEYPAD;
        }
        if ((nativeMethods & NanBootstrappingMethod.QR_SCAN_MASK) != 0) {
            publicMethods |= AwarePairingConfig.PAIRING_BOOTSTRAPPING_QR_SCAN;
        }
        if ((nativeMethods & NanBootstrappingMethod.NFC_READER_MASK) != 0) {
            publicMethods |= AwarePairingConfig.PAIRING_BOOTSTRAPPING_NFC_READER;
        }

        return publicMethods;
    }

    private static String statusString(NanStatus status) {
        if (status == null) {
            return "status=null";
        }
        return status.status + " (" + status.description + ")";
    }

    private Capabilities toFrameworkCapability(
            NanCapabilities capabilities) {
        Capabilities frameworkCapabilities = new Capabilities();
        frameworkCapabilities.maxPublishes = capabilities.maxPublishes;
        frameworkCapabilities.maxSubscribes = capabilities.maxSubscribes;
        frameworkCapabilities.maxServiceNameLen = capabilities.maxServiceNameLen;
        frameworkCapabilities.maxMatchFilterLen = capabilities.maxMatchFilterLen;
        frameworkCapabilities.maxServiceSpecificInfoLen =
                capabilities.maxServiceSpecificInfoLen;
        frameworkCapabilities.maxExtendedServiceSpecificInfoLen =
                capabilities.maxExtendedServiceSpecificInfoLen;
        frameworkCapabilities.maxNdiInterfaces = capabilities.maxNdiInterfaces;
        frameworkCapabilities.maxNdpSessions = capabilities.maxNdpSessions;
        frameworkCapabilities.maxAppInfoLen = capabilities.maxAppInfoLen;
        frameworkCapabilities.supportedDataPathCipherSuites = toPublicDataPathCipherSuites(
                capabilities.supportedCipherSuites);
        frameworkCapabilities.supportedPairingCipherSuites = toPublicPairingCipherSuites(
                capabilities.supportedCipherSuites);
        frameworkCapabilities.isInstantCommunicationModeSupported =
                capabilities.instantCommunicationModeSupportFlag;
        frameworkCapabilities.isNanPairingSupported = capabilities.supportsPairing;
        frameworkCapabilities.isSetClusterIdSupported = true;
        frameworkCapabilities.isSuspensionSupported = capabilities.supportsSuspension;
        frameworkCapabilities.isPeriodicRangingSupported = capabilities.supportsPeriodicRanging;
        frameworkCapabilities.maxSupportedRangingPktBandWidth = WifiRttControllerAidlImpl
                .halToFrameworkChannelBandwidth(capabilities.maxSupportedBandwidth);
        frameworkCapabilities.maxSupportedRxChains =
                toFrameworkChainsSupported(capabilities.maxNumRxChainsSupported);
        return frameworkCapabilities;
    }

    private static int toFrameworkChainsSupported(int supportedRxChains) {
        return switch (supportedRxChains) {
            case SUPPORTED_RX_CHAINS_1 -> Characteristics.SUPPORTED_RX_CHAINS_1;
            case SUPPORTED_RX_CHAINS_2 -> Characteristics.SUPPORTED_RX_CHAINS_2;
            case SUPPORTED_RX_CHAINS_3 -> Characteristics.SUPPORTED_RX_CHAINS_3;
            case SUPPORTED_RX_CHAINS_4 -> Characteristics.SUPPORTED_RX_CHAINS_4;
            default -> Characteristics.SUPPORTED_RX_CHAINS_UNSPECIFIED;
        };
    }

    private List<WifiAwareChannelInfo> convertAidlChannelInfo(
            NanDataPathChannelInfo[] channelInfos) {
        List<WifiAwareChannelInfo> wifiAwareChannelInfos = new ArrayList<>();
        if (channelInfos == null) {
            return null;
        }
        for (NanDataPathChannelInfo channelInfo : channelInfos) {
            wifiAwareChannelInfos.add(new WifiAwareChannelInfo(channelInfo.channelFreqMhz,
                    HalAidlUtil.getChannelBandwidthFromHal(channelInfo.channelBandwidth),
                    channelInfo.numSpatialStreams));
        }
        return wifiAwareChannelInfos;
    }

    private static int pairingRequestTypeFromAidl(@NanPairingRequestType int requestType) {
        if (requestType == NanPairingRequestType.NAN_PAIRING_SETUP) {
            return NAN_PAIRING_REQUEST_TYPE_SETUP;
        }
        return NAN_PAIRING_REQUEST_TYPE_VERIFICATION;
    }

    private static PairingConfigManager.PairingSecurityAssociationInfo
            createPairingSecurityAssociationInfo(NpkSecurityAssociation npksa) {
        return new PairingConfigManager.PairingSecurityAssociationInfo(npksa.peerNanIdentityKey,
                npksa.localNanIdentityKey, npksa.npk,
                createPublicPairingAkm(npksa.akm), toPublicPairingCipherSuites(npksa.cipherType));
    }

    private static int createPublicPairingAkm(int aidlAkm) {
        switch (aidlAkm) {
            case NanPairingAkm.SAE:
                return NAN_PAIRING_AKM_SAE;
            case NanPairingAkm.PASN:
                return NAN_PAIRING_AKM_PASN;
        }
        Log.e(TAG, "unknown pairing AKM");
        return aidlAkm;
    }

    private int convertAidlBootstrappingResponseCodeToFramework(int aidlCode) {
        switch (aidlCode) {
            case NanBootstrappingResponseCode.REQUEST_ACCEPT:
                return NAN_BOOTSTRAPPING_ACCEPT;
            case NanBootstrappingResponseCode.REQUEST_REJECT:
                return NAN_BOOTSTRAPPING_REJECT;
            case NanBootstrappingResponseCode.REQUEST_COMEBACK:
                return NAN_BOOTSTRAPPING_COMEBACK;
        }
        Log.e(TAG, "unknown bootstrapping response code");
        return aidlCode;
    }
}
