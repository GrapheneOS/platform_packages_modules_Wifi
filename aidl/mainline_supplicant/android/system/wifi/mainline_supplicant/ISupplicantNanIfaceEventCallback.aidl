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

import android.system.wifi.mainline_supplicant.NanCapabilities;
import android.system.wifi.mainline_supplicant.NanClusterEventInd;
import android.system.wifi.mainline_supplicant.NanFollowupReceivedInd;
import android.system.wifi.mainline_supplicant.NanMatchInd;
import android.system.wifi.mainline_supplicant.NanStatus;

/**
 * NAN Response and Asynchronous Event Callbacks.
 *
 * References to "NAN Spec" are to the Wi-Fi Alliance "Wi-Fi Neighbor Awareness Networking (NAN)
 * Technical Specification".
 */
oneway interface ISupplicantNanIfaceEventCallback {
    /**
     * Callback indicating that a cluster event has been received.
     *
     * @param event NanClusterEventInd containing event details.
     */
    void eventClusterEvent(in NanClusterEventInd event);

    /**
     * Callback indicating that a match has occurred: i.e. a service has been discovered.
     *
     * @param event NanMatchInd containing event details.
     */
    void eventMatch(in NanMatchInd event);

    /**
     * Callback indicating that a previously discovered match (service) has expired.
     *
     * @param discoverySessionId Discovery session ID of the expired match.
     * @param peerId Peer ID of the expired match.
     */
    void eventMatchExpired(in byte discoverySessionId, in int peerId);

    /**
     * Callback indicating that an active publish session has terminated.
     *
     * @param sessionId Discovery session ID of the terminated session.
     * @param status NanStatus describing the reason for the session termination.
     *               Possible status codes are:
     *               |NanStatusCode.SUCCESS|
     *               |NanStatusCode.INTERNAL_FAILURE|
     */
    void eventPublishTerminated(in byte sessionId, in NanStatus status);

    /**
     * Callback indicating that an active subscribe session has terminated.
     *
     * @param sessionId Discovery session ID of the terminated session.
     * @param status NanStatus describing the reason for the session termination.
     *               Possible status codes are:
     *               |NanStatusCode.SUCCESS|
     *               |NanStatusCode.INTERNAL_FAILURE|
     */
    void eventSubscribeTerminated(in byte sessionId, in NanStatus status);

    /**
     * Callback providing status of a completed followup message transmit operation. Indicates the
     * response after the supplicant has attempted to send the followup message over-the-air.
     *
     * @param id Command ID corresponding to the original |transmitFollowupRequest| request.
     * @param status NanStatus of the operation. Possible status codes are:
     *        |NanStatusCode.SUCCESS|
     *        |NanStatusCode.NO_OTA_ACK|
     *        |NanStatusCode.PROTOCOL_FAILURE|
     */
    void eventTransmitFollowup(in char id, in NanStatus status);

    /**
     * Callback indicating that a followup message has been received from a peer.
     *
     * @param event NanFollowupReceivedInd containing event details.
     */
    void eventFollowupReceived(in NanFollowupReceivedInd event);

    /**
     * Callback invoked in response to a capability request
     * |ISupplicantNanIface.getCapabilitiesRequest|.
     *
     * @param id Command ID corresponding to the original request.
     * @param status NanStatus of the operation. Possible status codes are:
     *        |NanStatusCode.SUCCESS|
     * @param capabilities Capability data.
     */
    void notifyCapabilitiesResponse(
        in char id, in NanStatus status, in NanCapabilities capabilities);

    /**
     * Callback invoked in response to a config request |ISupplicantNanIface.configRequest|.
     *
     * @param id Command ID corresponding to the original request.
     * @param status NanStatus of the operation. Possible status codes are:
     *        |NanStatusCode.SUCCESS|
     *        |NanStatusCode.INVALID_ARGS|
     *        |NanStatusCode.INTERNAL_FAILURE|
     *        |NanStatusCode.PROTOCOL_FAILURE|
     */
    void notifyConfigResponse(in char id, in NanStatus status);

    /**
     * Callback invoked in response to a create data interface request
     * |ISupplicantNanIface.createDataInterfaceRequest|.
     *
     * @param id Command ID corresponding to the original request.
     * @param status NanStatus of the operation. Possible status codes are:
     *        |NanStatusCode.SUCCESS|
     *        |NanStatusCode.INVALID_ARGS|
     *        |NanStatusCode.INTERNAL_FAILURE|
     */
    void notifyCreateDataInterfaceResponse(in char id, in NanStatus status);

    /**
     * Callback invoked in response to a delete data interface request
     * |ISupplicantNanIface.deleteDataInterfaceRequest|.
     *
     * @param id Command ID corresponding to the original request.
     * @param status NanStatus of the operation. Possible status codes are:
     *        |NanStatusCode.SUCCESS|
     *        |NanStatusCode.INVALID_ARGS|
     *        |NanStatusCode.INTERNAL_FAILURE|
     */
    void notifyDeleteDataInterfaceResponse(in char id, in NanStatus status);

    /**
     * Callback invoked in response to an enable request |ISupplicantNanIface.enableRequest|.
     *
     * @param id Command ID corresponding to the original request.
     * @param status NanStatus of the operation. Possible status codes are:
     *        |NanStatusCode.SUCCESS|
     *        |NanStatusCode.ALREADY_ENABLED|
     *        |NanStatusCode.INVALID_ARGS|
     *        |NanStatusCode.INTERNAL_FAILURE|
     *        |NanStatusCode.PROTOCOL_FAILURE|
     *        |NanStatusCode.NAN_NOT_ALLOWED|
     */
    void notifyEnableResponse(in char id, in NanStatus status);

    /**
     * Callback invoked in response to a disable request |ISupplicantNanIface.disableRequest|.
     *
     * @param id Command ID corresponding to the original request.
     * @param status NanStatus of the operation. Possible status codes are:
     *        |NanStatusCode.SUCCESS|
     *        |NanStatusCode.PROTOCOL_FAILURE|
     */
    void notifyDisableResponse(in char id, in NanStatus status);

    /**
     * Callback invoked to notify the status of the start publish request from
     * |ISupplicantNanIface.startPublishRequest|.
     *
     * @param id Command ID corresponding to the original request.
     * @param status NanStatus of the operation. Possible status codes are:
     *        |NanStatusCode.SUCCESS|
     *        |NanStatusCode.INVALID_ARGS|
     *        |NanStatusCode.PROTOCOL_FAILURE|
     *        |NanStatusCode.NO_RESOURCES_AVAILABLE|
     *        |NanStatusCode.INVALID_SESSION_ID|
     * @param sessionId ID of the new publish session (if successfully created).
     */
    void notifyStartPublishResponse(in char id, in NanStatus status, in byte sessionId);

    /**
     * Callback invoked to notify the status of the start subscribe request from
     * |ISupplicantNanIface.startSubscribeRequest|.
     *
     * @param id Command ID corresponding to the original request.
     * @param status NanStatus of the operation. Possible status codes are:
     *        |NanStatusCode.SUCCESS|
     *        |NanStatusCode.INVALID_ARGS|
     *        |NanStatusCode.PROTOCOL_FAILURE|
     *        |NanStatusCode.NO_RESOURCES_AVAILABLE|
     *        |NanStatusCode.INVALID_SESSION_ID|
     * @param sessionId ID of the new subscribe session (if successfully created).
     */
    void notifyStartSubscribeResponse(in char id, in NanStatus status, in byte sessionId);

    /**
      * Callback invoked to notify the status of the stop publish request from
      * |ISupplicantNanIface.stopPublishRequest|.
      *
      * @param id Command ID corresponding to the original request.
      * @param status NanStatus of the operation. Possible status codes are:
      *         |NanStatusCode.SUCCESS|
      *         |NanStatusCode.INVALID_SESSION_ID|
      *         |NanStatusCode.INTERNAL_FAILURE|
      */
    void notifyStopPublishResponse(in char id, in NanStatus status);

    /**
     * Callback invoked to notify the status of the stop subscribe request from
     * |ISupplicantNanIface.stopSubscribeRequest|.
     *
     * @param id Command ID corresponding to the original request.
     * @param status NanStatus of the operation. Possible status codes are:
     *         |NanStatusCode.SUCCESS|
     *         |NanStatusCode.INVALID_SESSION_ID|
     *         |NanStatusCode.INTERNAL_FAILURE|
     */
    void notifyStopSubscribeResponse(in char id, in NanStatus status);

    /**
     * Callback invoked in response to a transmit followup request
     * |ISupplicantNanIface.transmitFollowupRequest|. Indicates the response from the local
     * firmware/hardware. The result of the over-the-air transmission is reported via
     * |eventTransmitFollowup|.
     *
     * @param id Command ID corresponding to the original request.
     * @param status NanStatus of the operation. Possible status codes are:
     *        |NanStatusCode.SUCCESS|
     *        |NanStatusCode.INVALID_ARGS|
     *        |NanStatusCode.INTERNAL_FAILURE|
     *        |NanStatusCode.INVALID_SESSION_ID|
     *        |NanStatusCode.INVALID_PEER_ID|
     *        |NanStatusCode.FOLLOWUP_TX_QUEUE_FULL|
     */
    void notifyTransmitFollowupResponse(in char id, in NanStatus status);
}
