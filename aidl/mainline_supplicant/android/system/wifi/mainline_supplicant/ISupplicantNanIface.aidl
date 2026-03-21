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

package android.system.wifi.mainline_supplicant;

import android.system.wifi.mainline_supplicant.ISupplicantNanIfaceEventCallback;
import android.system.wifi.mainline_supplicant.NanBootstrappingRequest;
import android.system.wifi.mainline_supplicant.NanBootstrappingResponse;
import android.system.wifi.mainline_supplicant.NanConfigRequest;
import android.system.wifi.mainline_supplicant.NanEnableRequest;
import android.system.wifi.mainline_supplicant.NanInitiateDataPathRequest;
import android.system.wifi.mainline_supplicant.NanPairingRequest;
import android.system.wifi.mainline_supplicant.NanPublishRequest;
import android.system.wifi.mainline_supplicant.NanRespondToDataPathIndicationRequest;
import android.system.wifi.mainline_supplicant.NanRespondToPairingIndicationRequest;
import android.system.wifi.mainline_supplicant.NanSchedule;
import android.system.wifi.mainline_supplicant.NanSubscribeRequest;
import android.system.wifi.mainline_supplicant.NanTransmitFollowupRequest;

/**
 * Interface used to represent a single NAN (Neighbour Aware Network) iface.
 *
 * References to "NAN Spec" are to the Wi-Fi Alliance "Wi-Fi Neighbor Awareness Networking (NAN)
 * Technical Specification".
 */
interface ISupplicantNanIface {
    /**
     * Requests notifications of significant events on this iface. Multiple calls to this must
     * register multiple callbacks, each of which must receive all events.
     *
     * @param callback An instance of the |ISupplicantNanIfaceEventCallback| AIDL interface
     *        object.
     * @throws ServiceSpecificException with one of the following values:
     *         |SupplicantStatusCode.FAILURE_IFACE_INVALID|
     */
    void registerEventCallback(in ISupplicantNanIfaceEventCallback callback);

    /**
     * Get NAN capabilities. Asynchronous response is with
     * |ISupplicantNanIfaceEventCallback.notifyCapabilitiesResponse|.
     *
     * @param cmdId Command Id to use for this invocation.
     * @throws ServiceSpecificException with one of the following values:
     *         |SupplicantStatusCode.FAILURE_IFACE_INVALID|,
     *         |SupplicantStatusCode.FAILURE_UNKNOWN|
     */
    void getCapabilitiesRequest(in char cmdId);

    /**
     * Configures and activates NAN clustering (does not start a discovery session or set up
     * data-interfaces or data-paths). Uses the |ISupplicantNanIface.configureRequest| method to
     * change the configuration of an already enabled NAN interface.
     * Asynchronous response is with
     * |ISupplicantNanIfaceEventCallback.notifyEnableResponse|.
     *
     * @param cmdId Command Id to use for this invocation.
     * @param msg1 Instance of |NanEnableRequest|.
     * @param msg2 Instance of |NanConfigRequest|.
     * @throws ServiceSpecificException with one of the following values:
     *         |SupplicantStatusCode.FAILURE_UNSUPPORTED|,
     *         |SupplicantStatusCode.FAILURE_IFACE_INVALID|,
     *         |SupplicantStatusCode.FAILURE_ARGS_INVALID|,
     *         |SupplicantStatusCode.FAILURE_UNKNOWN|
     */
    void enableRequest(in char cmdId, in NanEnableRequest msg1, in NanConfigRequest msg2);

    /**
     * Configures an existing NAN functionality (i.e. assumes |ISupplicantNanIface.enableRequest|
     * already submitted and succeeded). Asynchronous response is with
     * |ISupplicantNanIfaceEventCallback.notifyConfigResponse|.
     *
     * @param cmdId Command Id to use for this invocation.
     * @param msg Instance of |NanConfigRequest|.
     * @throws ServiceSpecificException with one of the following values:
     *         |SupplicantStatusCode.FAILURE_UNSUPPORTED|,
     *         |SupplicantStatusCode.FAILURE_IFACE_INVALID|,
     *         |SupplicantStatusCode.FAILURE_ARGS_INVALID|,
     *         |SupplicantStatusCode.FAILURE_UNKNOWN|
     */
    void configRequest(in char cmdId, in NanConfigRequest msg);

    /**
     * Disables NAN functionality.
     * Asynchronous response is with |ISupplicantNanIfaceEventCallback.notifyDisableResponse|.
     *
     * @param cmdId Command Id to use for this invocation.
     * @throws ServiceSpecificException with one of the following values:
     *         |SupplicantStatusCode.FAILURE_IFACE_INVALID|,
     *         |SupplicantStatusCode.FAILURE_UNKNOWN|
     */
    void disableRequest(in char cmdId);

    /**
     * Creates a NAN Data Interface.
     * Asynchronous response is with
     * |ISupplicantNanIfaceEventCallback.notifyCreateDataInterfaceResponse|.
     *
     * @param cmdId Command Id to use for this invocation.
     * @param ifaceName The name of the interface, e.g. "aware0".
     * @param MacAddr The MAC address of the interface
     * @throws ServiceSpecificException with one of the following values:
     *         |SupplicantStatusCode.FAILURE_IFACE_INVALID|,
     *         |SupplicantStatusCode.FAILURE_UNKNOWN|
     */
    void createDataInterfaceRequest(in char cmdId, in String ifaceName, in byte[6] MacAddr);

    /**
     * Deletes a NAN Data Interface.
     * Asynchronous response is with
     * |ISupplicantNanIfaceEventCallback.notifyDeleteDataInterfaceResponse|.
     *
     * @param cmdId Command Id to use for this invocation.
     * @param ifaceName The name of the interface, e.g. "aware0".
     * @throws ServiceSpecificException with one of the following values:
     *         |SupplicantStatusCode.FAILURE_IFACE_INVALID|,
     *         |SupplicantStatusCode.FAILURE_UNKNOWN|
     */
    void deleteDataInterfaceRequest(in char cmdId, in String ifaceName);

    /**
     * Gets the name of this iface.
     *
     * @return Name of this iface.
     * @throws ServiceSpecificException with one of the following values:
     *         |SupplicantStatusCode.FAILURE_IFACE_INVALID|
     */
    String getName();

    /**
     * Publish request to start advertising a discovery service.
     * Asynchronous response is with
     * |ISupplicantNanIfaceEventCallback.notifyStartPublishResponse|.
     *
     * @param cmdId Command Id to use for this invocation.
     * @param msg Instance of |NanPublishRequest|.
     * @throws ServiceSpecificException with one of the following values:
     *         |SupplicantStatusCode.FAILURE_IFACE_INVALID|,
     *         |SupplicantStatusCode.FAILURE_ARGS_INVALID|,
     *         |SupplicantStatusCode.FAILURE_UNKNOWN|
     */
    void startPublishRequest(in char cmdId, in NanPublishRequest msg);

    /**
     * Subscribe request to start searching for a discovery service.
     * Asynchronous response is with
     * |ISupplicantNanIfaceEventCallback.notifyStartSubscribeResponse|.
     *
     * @param cmdId Command Id to use for this invocation.
     * @param msg Instance of |NanSubscribeRequest|.
     * @throws ServiceSpecificException with one of the following values:
     *         |SupplicantStatusCode.FAILURE_IFACE_INVALID|,
     *         |SupplicantStatusCode.FAILURE_ARGS_INVALID|,
     *         |SupplicantStatusCode.FAILURE_UNKNOWN|
     */
    void startSubscribeRequest(in char cmdId, in NanSubscribeRequest msg);

    /**
     * Stop publishing a discovery service.
     * Asynchronous response is with
     * |ISupplicantNanIfaceEventCallback.notifyStopPublishResponse|.
     *
     * @param cmdId Command Id to use for this invocation.
     * @param sessionId ID of the publish discovery session to be stopped.
     * @throws ServiceSpecificException with one of the following values:
     *         |SupplicantStatusCode.FAILURE_IFACE_INVALID|,
     *         |SupplicantStatusCode.FAILURE_UNKNOWN|
     */
    void stopPublishRequest(in char cmdId, in byte sessionId);

    /**
     * Stop subscribing to a discovery service.
     * Asynchronous response is with
     * |ISupplicantNanIfaceEventCallback.notifyStopSubscribeResponse|.
     *
     * @param cmdId Command Id to use for this invocation.
     * @param sessionId ID of the subscribe discovery session to be stopped.
     * @throws ServiceSpecificException with one of the following values:
     *         |SupplicantStatusCode.FAILURE_IFACE_INVALID|,
     *         |SupplicantStatusCode.FAILURE_UNKNOWN|
     */
    void stopSubscribeRequest(in char cmdId, in byte sessionId);

    /**
     * NAN transmit follow up message request.
     * Asynchronous response is with
     * |ISupplicantNanIfaceEventCallback.notifyTransmitFollowupResponse|.
     *
     * @param cmdId Command Id to use for this invocation.
     * @param msg Instance of |NanTransmitFollowupRequest|.
     * @throws ServiceSpecificException with one of the following values:
     *         |SupplicantStatusCode.FAILURE_IFACE_INVALID|,
     *         |SupplicantStatusCode.FAILURE_ARGS_INVALID|,
     *         |SupplicantStatusCode.FAILURE_UNKNOWN|
     */
    void transmitFollowupRequest(in char cmdId, in NanTransmitFollowupRequest msg);

    /**
     * Initiate a NAN pairing bootstrapping operation: Initiator.
     * Asynchronous response is with
     * |ISupplicantNanIfaceEventCallback.notifyInitiateBootstrappingResponse|.
     *
     * @param cmdId Command Id to use for this invocation.
     * @param msg Instance of |NanBootstrappingRequest|.
     * @throws ServiceSpecificException with one of the following values:
     *         |SupplicantStatusCode.FAILURE_IFACE_INVALID|,
     *         |SupplicantStatusCode.FAILURE_ARGS_INVALID|,
     *         |SupplicantStatusCode.FAILURE_UNKNOWN|
     */
    void initiateBootstrappingRequest(in char cmdId, in NanBootstrappingRequest msg);

    /**
     * Respond to a received request indication of NAN pairing bootstrapping operation.
     * An indication is received by the Responder from the Initiator.
     * Asynchronous response is with
     * |ISupplicantNanIfaceEventCallback.notifyRespondToPairingIndicationResponse|.
     *
     * @param cmdId Command Id to use for this invocation.
     * @param msg Instance of |NanBootstrappingResponse|.
     * @throws ServiceSpecificException with one of the following values:
     *         |SupplicantStatusCode.FAILURE_IFACE_INVALID|,
     *         |SupplicantStatusCode.FAILURE_ARGS_INVALID|,
     *         |SupplicantStatusCode.FAILURE_UNKNOWN|
     */
    void respondToBootstrappingIndicationRequest(in char cmdId, in NanBootstrappingResponse msg);

    /**
     * Initiate a NAN pairing operation: Initiator.
     * Asynchronous response is with
     * |ISupplicantNanIfaceEventCallback.notifyInitiatePairingResponse|.
     *
     * @param cmdId Command Id to use for this invocation.
     * @param msg Instance of |NanPairingRequest|.
     * @throws ServiceSpecificException with one of the following values:
     *         |SupplicantStatusCode.FAILURE_IFACE_INVALID|,
     *         |SupplicantStatusCode.FAILURE_ARGS_INVALID|,
     *         |SupplicantStatusCode.FAILURE_UNKNOWN|
     */
    void initiatePairingRequest(in char cmdId, in NanPairingRequest msg);

    /**
     * Respond to a received request indication of NAN pairing setup operation.
     * An indication is received by the Responder from the Initiator.
     * Asynchronous response is with
     * |ISupplicantNanIfaceEventCallback.notifyRespondToPairingIndicationResponse|.
     *
     * @param cmdId Command Id to use for this invocation.
     * @param msg Instance of |NanRespondToPairingIndicationRequest|.
     * @throws ServiceSpecificException with one of the following values:
     *         |SupplicantStatusCode.FAILURE_IFACE_INVALID|,
     *         |SupplicantStatusCode.FAILURE_ARGS_INVALID|,
     *         |SupplicantStatusCode.FAILURE_UNKNOWN|
     */
    void respondToPairingIndicationRequest(
            in char cmdId, in NanRespondToPairingIndicationRequest msg);

    /**
     * Aware pairing termination request. Executed by either the Initiator
     * or Responder.
     * Asynchronous response is with
     * |ISupplicantNanIfaceEventCallback.notifyTerminatePairingResponse|.
     *
     * @param cmdId Command Id to use for this invocation.
     * @param pairingInstanceId Pairing instance ID to be terminated.
     * @param peerDiscMacAddr MAC address of the peer. This is the MAC address of the peer's
     *        management/discovery NAN interface.
     * @throws ServiceSpecificException with one of the following values:
     *         |SupplicantStatusCode.FAILURE_IFACE_INVALID|,
     *         |SupplicantStatusCode.FAILURE_UNKNOWN|
     */
    void terminatePairingRequest(
            in char cmdId, in int pairingInstanceId, in byte[6] peerDiscMacAddr);

    /**
     * Initiate a data-path (NDP) setup operation: Initiator.
     * Asynchronous response is with
     * |ISupplicantNanIfaceEventCallback.notifyInitiateDataPathResponse|.
     *
     * @param cmdId Command Id to use for this invocation.
     * @param msg Instance of |NanInitiateDataPathRequest|.
     * @throws ServiceSpecificException with one of the following values:
     *         |SupplicantStatusCode.FAILURE_IFACE_INVALID|,
     *         |SupplicantStatusCode.FAILURE_ARGS_INVALID|,
     *         |SupplicantStatusCode.FAILURE_UNKNOWN|
     */
    void initiateDataPathRequest(in char cmdId, in NanInitiateDataPathRequest msg);

    /**
     * Respond to a received data indication as part of a data-path (NDP) setup operation.
     * An indication is received by the Responder from the Initiator.
     * Asynchronous response is with
     * |ISupplicantNanIfaceEventCallback.notifyRespondToDataPathIndicationResponse|.
     *
     * @param cmdId Command Id to use for this invocation.
     * @param msg Instance of |NanRespondToDataPathIndicationRequest|.
     * @throws ServiceSpecificException with one of the following values:
     *         |SupplicantStatusCode.FAILURE_IFACE_INVALID|,
     *         |SupplicantStatusCode.FAILURE_ARGS_INVALID|,
     *         |SupplicantStatusCode.FAILURE_UNKNOWN|
     */
    void respondToDataPathIndicationRequest(
            in char cmdId, in NanRespondToDataPathIndicationRequest msg);

    /**
     * Data-path (NDP) termination request. Executed by either Initiator or Responder.
     * Asynchronous response is with
     * |ISupplicantNanIfaceEventCallback.notifyTerminateDataPathResponse|.
     *
     * @param cmdId Command Id to use for this invocation.
     * @param ndpInstanceId Data-path instance ID to be terminated.
     * @param peerDiscMacAddr MAC address of the peer. This is the MAC address of the peer's
     *        management/discovery NAN interface.
     * @param ndiInitMac MAC address of the data interface that initiated the data-path.
     * @throws ServiceSpecificException with one of the following values:
     *         |SupplicantStatusCode.FAILURE_IFACE_INVALID|,
     *         |SupplicantStatusCode.FAILURE_UNKNOWN|
     */
    void terminateDataPathRequest(
            in char cmdId, in int ndpInstanceId, in byte[6] peerDiscMacAddr, in byte[6] ndiInitMac);

    /**
     * Set local NDL schedule.
     * Asynchronous response is with
     * |ISupplicantNanIfaceEventCallback.notifyScheduleUpdated|.

     * @param cmdId Command Id to use for this invocation.
     * @param schedule Local NDL schedule
     * @throws ServiceSpecificException with one of the following values:
     *         |SupplicantStatusCode.FAILURE_ARGS_INVALID|,
     *         |SupplicantStatusCode.FAILURE_UNKNOWN|

     */
    void setSchedule(in char cmdId, in NanSchedule[] schedule);
}
