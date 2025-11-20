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
import android.system.wifi.mainline_supplicant.NanBandSpecificConfig;

/**
 * Configuration parameters of NAN. Used when enabling and re-configuring a NAN cluster.
 */
parcelable NanConfigRequest {
    /**
     * Master preference of this device.
     * NAN Spec: Master Indication Attribute / Master Preference
     */
    byte masterPref;

    /**
     * Controls whether the |ISupplicantNanIfaceEventCallback.eventClusterEvent| will be
     * delivered for |NanClusterEventType.CLUSTER_STARTED|.
     */
    boolean disableStartedClusterIndication;

    /**
     * Controls whether the |ISupplicantNanIfaceEventCallback.eventClusterEvent| will be
     * delivered for |NanClusterEventType.CLUSTER_JOINED|.
     */
    boolean disableJoinedClusterIndication;

    /**
     * Controls whether the publish service IDs are included in the Sync/Discovery beacons.
     * NAN Spec: Service ID List Attribute
     */
    boolean includePublishServiceIdsInBeacon;

    /**
     * If |includePublishServiceIdsInBeacon| is true, then specifies the number of publish service
     * IDs to include in the Sync/Discovery beacons. Value = 0: include as many service IDs as will
     * fit into the maximum allowed beacon frame size. Value must fit within 7 bits - i.e. <= 127.
     */
    byte numberOfPublishServiceIdsInBeacon;

    /**
     * Controls whether the subscribe service IDs are included in the Sync/Discovery beacons.
     * Spec: Subscribe Service ID List Attribute
     */
    boolean includeSubscribeServiceIdsInBeacon;

    /**
     * If |includeSubscribeServiceIdsInBeacon| is true, then specifies the number of subscribe
     * service IDs to include in the Sync/Discovery beacons. Value = 0: include as many service IDs
     * as will fit into the maximum allowed beacon frame size.
     * Value must fit within 7 bits - i.e. <= 127.
     */
    byte numberOfSubscribeServiceIdsInBeacon;

    /**
     * Number of samples used to calculate RSSI.
     */
    char rssiWindowSize;

    /**
     * Additional configuration provided per band. Indexed by |NanBandIndex|.
     */
    NanBandSpecificConfig[3] bandSpecificConfig;

    /**
     * Optional vendor-specific parameters. Null value indicates that no vendor data is provided.
     */
    @nullable OuiKeyedData[] vendorData;

    /**
     * Specifies the Discovery Beacon interval in ms. Specification only applicable if the device
     * transmits Discovery Beacons (based on the Wi-Fi Aware protocol selection criteria). The
     * value can be increased to reduce power consumption (on devices which would transmit
     * Discovery Beacons). However, cluster synchronization time will likely increase.
     * Values are:
     *  - A value of 0 indicates that the HAL sets the interval to a default (implementation
     *    specific).
     *  - A positive value.
     */
    int discoveryBeaconIntervalMs;

    /**
     * Controls whether NAN RTT (ranging) is permitted. Global flag on any NAN RTT operations are
     * allowed. Controls ranging in the context of discovery as well as direct RTT.
     */
    boolean enableRanging;

    /**
     * Controls whether NAN instant communication mode is enabled.
     */
    boolean enableInstantCommunicationMode;

    /**
     * Controls which channel NAN instant communication mode operates on.
     */
    int instantModeChannel;

    /**
     * Controls which cluster to join.
     */
    byte[6] clusterId;
}
