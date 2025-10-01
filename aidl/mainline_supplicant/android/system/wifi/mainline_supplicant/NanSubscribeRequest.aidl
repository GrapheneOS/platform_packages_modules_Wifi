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
import android.system.wifi.mainline_supplicant.NanDiscoveryCommonConfig;
import android.system.wifi.mainline_supplicant.NanPairingConfig;

/**
 * Subscribe request. Specifies a subscribe discovery operation.
 */
parcelable NanSubscribeRequest {
    /**
     * NAN subscribe discovery session types.
     */
    @Backing(type="int")
    enum NanSubscribeType {
        PASSIVE = 0,
        ACTIVE,
    }

    /**
     * Common configuration of discovery sessions.
     */
    NanDiscoveryCommonConfig baseConfig;

    /**
     * The type of the subscribe discovery session.
     */
    NanSubscribeType subscribeType;

    /**
     * Control whether the presence of {@link NanDiscoveryCommonConfig#serviceSpecificInfo} data is
     * needed in the publisher in order to trigger service discovery, i.e. a
     * {@link ISupplicantNanIfaceEventCallback#eventMatch}. The test is for presence of data - not
     * for the specific contents of the data.
     */
    boolean isSsiRequiredForMatch;

    /**
     * Security config used for the pairing
     */
    NanPairingConfig pairingConfig;

    /**
     * The Identity key for pairing, will generate NIRA for verification by the peer.
     */
    byte[16] identityKey;

    /**
     * Optional vendor-specific parameters. Null value indicates that no vendor data is provided.
     */
    @nullable OuiKeyedData[] vendorData;
}
