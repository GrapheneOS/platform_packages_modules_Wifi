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
 * Response to a pairing request from a peer.
 * See Wi-Fi Aware R4.0 section 7.6.1.2
 */
parcelable NanRespondToPairingIndicationRequest {
    /**
     * Accept (true) or reject (false) the request.
     * NAN Spec: Data Path Attributes / NDP Attribute / Type and Status
     */
    boolean acceptRequest;

    /**
     * ID of the NAN pairing for which we're responding. Obtained as part of the request in
     * |ISupplicantNanIfaceEventCallback.eventPairingRequest|.
     */
    int pairingInstanceId;

    /**
     * Indicate the pairing session is of setup or verification
     */
    NanPairingRequestType requestType;

    /**
     * Whether should cache the negotiated NIK/NPK for future verification
     */
    boolean enablePairingCache;

    /**
     * The Identity key for pairing, can be used for pairing verification
     */
    byte[16] pairingIdentityKey;

    /**
     * Security config used for the pairing
     */
    NanPairingSecurityConfig securityConfig;

    /**
     * Optional vendor-specific parameters. Null value indicates that no vendor data is provided.
     */
    @nullable OuiKeyedData[] vendorData;
}
