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
 * See Wi-Fi Aware Specification 4.0 section 9.5.21.7.
 */
parcelable NanBootstrappingResponse {
    /**
     * NAN management interface MAC address of the peer. Obtained as part of an earlier
     * |ISupplicantNanIfaceEventCallback.eventMatch| or
     * |ISupplicantNanIfaceEventCallback.eventFollowupReceived|. Used to identify the peer
     * information.
     */
    byte[6] peerDiscMacAddr;

    /**
     * ID of bootstrapping session. Used to identify the bootstrapping further negotiation/APIs.
     */
    int bootstrappingInstanceId;

    /**
     * True if the request was accepted, false otherwise.
     */
    boolean acceptRequest;

    /**
     * ID of an active publish or subscribe discovery session. Follow-up message is transmitted in
     * the context of the discovery session. NAN Spec: Service Descriptor Attribute (SDA) /
     * Instance ID.
     */
    byte discoverySessionId;

    /**
     * One of |NanBootstrappingMethod| indicating the bootstrapping method in the request.
     */
    NanBootstrappingMethod responseBootstrappingMethod;
}
