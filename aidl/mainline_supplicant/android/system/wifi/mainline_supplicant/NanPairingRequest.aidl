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

import android.hardware.wifi.common.OuiKeyedData;
import android.system.wifi.mainline_supplicant.NanPairingRequestType;
import android.system.wifi.mainline_supplicant.NanPairingSecurityConfig;

/**
 * NAN pairing initiate request.
 * Can be used for setup (the initial pairing request) or verification (re-pairing for paired
 * devices).
 * See Wi-Fi Aware R4.0 section 7.6.1.1
 */
parcelable NanPairingRequest {
    /**
     * ID of the peer. Obtained as part of an earlier |ISupplicantNanIfaceEventCallback.eventMatch|
     * or |ISupplicantNanIfaceEventCallback.eventFollowupReceived|.
     */
    int peerId;

    /**
     * ID of an active publish or subscribe discovery session. Follow-up message is transmitted in
     * the context of the discovery session. NAN Spec: Service Descriptor Attribute (SDA) /
     * Instance ID.
     */
    byte discoverySessionId;

    /**
     * NAN management interface MAC address of the peer. Obtained as part of an earlier
     * |ISupplicantNanIfaceEventCallback.eventMatch| or
     * |ISupplicantNanIfaceEventCallback.eventFollowupReceived|.
     */
    byte[6] peerDiscMacAddr;

    /**
     * Indicate the pairing session is for setup or verification.
     */
    NanPairingRequestType requestType;

    /**
     * Whether to cache the negotiated NIK/NPK for future verification.
     */
    boolean enablePairingCache;

    /**
     * The Identity key for pairing, can be used for pairing verification.
     */
    byte[16] pairingIdentityKey;

    /**
     * Security config used for the pairing.
     */
    NanPairingSecurityConfig securityConfig;

    /**
     * The Identity key for pairing from peer, used for pairing verification.
     */
    @nullable byte[16] peerIdentityKey;

    /**
     * Optional vendor-specific parameters. Null value indicates that no vendor data is provided.
     */
    @nullable OuiKeyedData[] vendorData;
}
