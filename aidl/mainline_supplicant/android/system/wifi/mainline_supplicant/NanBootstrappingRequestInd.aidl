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

import android.system.wifi.mainline_supplicant.NanBootstrappingMethod;

/**
 * NAN Data path request indication message structure.
 * Event indication received by an intended Responder when a NAN data request is initiated by an
 * Initiator. See Wi-Fi Aware Specification 4.0 section 9.5.21.7.
 */
parcelable NanBootstrappingRequestInd {
    /**
     * Discovery session (publish or subscribe) ID of a previously created discovery session. The
     * bootstrapping request is received in the context of this discovery session.
     * NAN Spec: Service Descriptor Attribute (SDA) / Instance ID
     */
    byte discoverySessionId;

    /**
     * A unique ID of the peer. Can be subsequently used in
     * |ISupplicantNanIface.transmitFollowupRequest| or to set up a data-path.
     */
    int peerId;

    /**
     * MAC address of the Initiator peer. This is the MAC address of the peer's management/
     * discovery NAN interface.
     */
    byte[6] peerDiscMacAddr;

    /**
     * ID of bootstrapping session. Used to identify the bootstrapping further negotiation/APIs.
     */
    int bootstrappingInstanceId;

    /**
     * One of |NanBootstrappingMethod| indicating the bootstrapping method in the incoming request.
     */
    NanBootstrappingMethod requestBootstrappingMethod;
}
