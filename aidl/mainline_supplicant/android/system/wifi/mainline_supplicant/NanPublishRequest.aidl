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
 * Publish request. Specifies a publish discovery operation.
 */
parcelable NanPublishRequest {
    /**
     * NAN publish discovery session types.
     */
    @Backing(type="int")
    enum NanPublishType {
        UNSOLICITED = 0,
        SOLICITED,
        UNSOLICITED_SOLICITED,
    }

    /**
     * NAN transmit type used in |NanPublishType.SOLICITED| or
     * |NanPublishType.UNSOLICITED_SOLICITED| publish discovery sessions. Describes the addressing
     * of the packet responding to an ACTIVE subscribe query.
     */
    @Backing(type="int")
    enum NanTxType {
        BROADCAST = 0,
        UNICAST,
    }

    /**
     * Common configuration of discovery sessions.
     */
    NanDiscoveryCommonConfig baseConfig;

    /**
     * Type of the publish discovery session.
     */
    NanPublishType publishType;

    /**
     * For publishType of |NanPublishType.SOLICITED| or |NanPublishType.UNSOLICITED_SOLICITED|,
     * this specifies the type of transmission used for responding to the probing subscribe
     * discovery peer.
     */
    NanTxType txType;

    /**
     * Specifies whether data-path requests |ISupplicantNanIfaceEventCallback.eventDataPathRequest|
     * (in the context of this discovery session) are automatically accepted (if true) - in which
     * case the Responder must not call the
     * |ISupplicantNanIface.respondToDataPathIndicationRequest| method and the device must
     * automatically accept the data-path request and complete the negotiation.
     */
    boolean autoAcceptDataPathRequests;

    /**
     * The config for NAN pairing.
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

    /**
     * If |NanCapabilities.supportsPeriodicRanging| is true, then this field specifies whether the
     * ranging results need to be notified to the Publisher when they are available.
     */
    boolean rangingResultsRequired;
}
