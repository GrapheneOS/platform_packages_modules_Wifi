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

import android.system.wifi.mainline_supplicant.NanSchedule;

/**
 * NAN Data path request indication message structure.
 * Event indication is received by an intended Responder when a NAN data request is initiated by an
 * Initiator.
 */
parcelable NanDataPathRequestInd {
    /**
     * ID of an active publish or subscribe discovery session. The data-path request is in the
     * context of this discovery session.
     * NAN Spec: Data Path Attributes / NDP Attribute / Publish ID
     */
    byte discoverySessionId;

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
     * ID of the data-path. Used to identify the data-path in further negotiation/APIs.
     */
    int ndpInstanceId;

    /**
     * Specifies whether security is required by the peer for the data-path being created.
     * NAN Spec: Data Path Attributes / NDP Attribute / NDP Control / Security
     * Present
     */
    boolean securityRequired;

    /**
     * Arbitrary information communicated from the peer as part of the data-path setup process.
     * There is no semantic meaning to these bytes. They are passed-through from sender to receiver
     * as-is with no parsing.
     * Max length: |NanCapabilities.maxAppInfoLen|.
     * NAN Spec: Data Path Attributes / NDP Attribute / NDP Specific Info
     */
    byte[] appInfo;

    /**
     * Peer NDL schedule.
     */
    NanSchedule[] peerSchedule;
}
