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

/**
 * Transmit follow up message request.
 */
parcelable NanTransmitFollowupRequest {
    /**
     * ID of an active publish or subscribe discovery session. Follow-up message is transmitted in
     * the context of the discovery session.
     * NAN Spec: Service Descriptor Attribute (SDA) / Instance ID
     */
    byte discoverySessionId;

    /**
     * ID of the peer. Obtained as part of an earlier |ISupplicantNanIfaceEventCallback.eventMatch|
     * or |ISupplicantNanIfaceEventCallback.eventFollowupReceived|.
     */
    int peerId;

    /**
     * MAC address of the peer. Obtained as part of an earlier
     * |ISupplicantNanIfaceEventCallback.eventMatch| or
     * |ISupplicantNanIfaceEventCallback.eventFollowupReceived|.
     */
    byte[6] addr;

    /**
     * Arbitrary information communicated to the peer. There is no semantic meaning to these bytes.
     * They are passed-through from sender to receiver as-is with no parsing.
     * Max length: |NanCapabilities.maxServiceSpecificInfoLen|.
     * NAN Spec: Service Descriptor Attribute (SDA) / Service Info
     */
    byte[] serviceSpecificInfo;

    /**
     * Arbitrary information communicated in discovery packets. There is no semantic meaning to
     * these bytes. They are passed-through from publisher to subscriber as-is with no parsing.
     * Max length: |NanCapabilities.maxExtendedServiceSpecificInfoLen|.
     * NAN Spec: Service Descriptor
     * Extension Attribute (SDEA) / Service Info
     */
    byte[] extendedServiceSpecificInfo;

    /**
     * Disable |ISupplicantNanIfaceEventCallback.eventTransmitFollowup| - i.e. do not get
     * indication on whether the follow-up was transmitted and received successfully.
     */
    boolean disableFollowupResultIndication;
}
