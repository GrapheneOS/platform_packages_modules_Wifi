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

import android.system.wifi.mainline_supplicant.NanStatus;

/**
 * See Wi-Fi Aware Specification 4.0 section 9.5.21.7.
 */
parcelable NanBootstrappingConfirmInd {
    /**
     * Response code from peer NAN Bootstrapping request
     */
    @Backing(type="int")
    enum NanBootstrappingResponseCode {
        REQUEST_ACCEPT = 0,
        REQUEST_REJECT,
        REQUEST_COMEBACK,
    }

    /**
     * Id of the bootstrapping session. Obtained as part of earlier
     * |ISupplicantNanIface.initiateBootstrappingRequest| success notification.
     */
    int bootstrappingInstanceId;

    /**
     * Indicates whether the bootstrapping method negotiation was accepted.
     */
    NanBootstrappingResponseCode responseCode;

    /**
     * Failure reason if |acceptRequest| is false.
     */
    NanStatus failureReasonCode;

    /**
     * The delay of bootstrapping in seconds for the follow up request.
     */
    int comeBackDelaySec;

    /**
     * Cookie received from peer with |comeBackDelaySec| for follow up |NanBootstrappingRequest|.
     * Max length: 255 bytes.
     */
    byte[] cookie;
}
