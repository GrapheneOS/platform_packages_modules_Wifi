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

import android.system.wifi.mainline_supplicant.NanDataPathSecurityConfig;
import android.system.wifi.mainline_supplicant.NanSchedule;

/**
 * Response to a data-path request from a peer.
 */
parcelable NanRespondToDataPathIndicationRequest {
    /**
     * Accept (true) or reject (false) the request.
     * NAN Spec: Data Path Attributes / NDP Attribute / Type and Status
     */
    boolean acceptRequest;

    /**
     * ID of the data-path (NDP) for which we're responding. Obtained as part of the request in
     * |ISupplicantNanIfaceEventCallback.eventDataPathRequest|.
     */
    int ndpInstanceId;

    /**
     * MAC address of NAN Data Interface (NDI) address of the Initiator.
     * It is a randomized MAC address used for the data interface to prevent device tracking.
     */
    byte[6] ndiInitMac;

    /**
     * MAC address of the Initiator peer. This is the MAC address of the peer's
     * management/discovery NAN interface.
     */
    byte[6] peerDiscMacAddr;

    /**
     * NAN data interface name on which this data-path session is to be started.
     * This must be an interface created using |ISupplicantNanIface.createDataInterfaceRequest|.
     */
    String ifaceName;

    /**
     * Security configuration of the requested data-path.
     */
    NanDataPathSecurityConfig securityConfig;

    /**
     * Arbitrary information communicated to the peer as part of the data-path setup process. There
     * is no semantic meaning to these bytes. They are passed-through from sender to receiver as-is
     * with no parsing.
     * Max length: |NanCapabilities.maxAppInfoLen|.
     * NAN Spec: Data Path Attributes / NDP Attribute / NDP Specific Info
     */
    byte[] appInfo;

    /**
     * A service name to be used with |passphrase| to construct a Pairwise Master Key (PMK) for the
     * data-path. Only relevant when a data-path is requested which is not associated with a NAN
     * discovery session - e.g. using out-of-band discovery.
     * Constraints: same as |NanDiscoveryCommonConfig.serviceName|
     * NAN Spec: Appendix: Mapping pass-phrase to PMK for NCS-SK Cipher Suites
     */
    byte[] serviceNameOutOfBand;

    /**
     * ID of an active publish or subscribe discovery session.
     * NAN Spec: Service Descriptor Attribute (SDA) / Instance ID
     */
    byte discoverySessionId;

    /**
     * NDL schedule.
     */
    NanSchedule[] schedule;
}
