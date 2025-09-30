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
     * Callback invoked in response to a capability request
     * |ISupplicantNanIface.getCapabilitiesRequest|.
     *
     * @param id Command ID corresponding to the original request.
     * @param status NanStatus of the operation. Possible status codes are:
     *     |NanStatusCode.SUCCESS|
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
}
