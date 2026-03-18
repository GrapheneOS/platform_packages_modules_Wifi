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
 *  Data Path Initiator requesting a data-path.
 */
parcelable NanInitiateDataPathRequest {
    /**
     * NAN DP (data-path) channel config options.
     */
    @Backing(type="int")
    enum NanDataPathChannelCfg {
        CHANNEL_NOT_REQUESTED = 0,
        REQUEST_CHANNEL_SETUP,
        FORCE_CHANNEL_SETUP,
    }

    /**
     * ID of the peer. Obtained as part of an earlier |ISupplicantNanIfaceEventCallback.eventMatch|
     * or |ISupplicantNanIfaceEventCallback.eventFollowupReceived|.
     */
    int peerId;

    /**
     * NAN management interface MAC address of the peer. Obtained as part of an earlier
     * |ISupplicantNanIfaceEventCallback.eventMatch| or
     * |ISupplicantNanIfaceEventCallback.eventFollowupReceived|.
     */
    byte[6] peerDiscMacAddr;

    /**
     * Config flag for channel request.
     */
    NanDataPathChannelCfg channelRequestType;

    /**
     * Channel frequency in MHz to start data-path. Not relevant if |channelRequestType| is
     * |NanDataPathChannelCfg.CHANNEL_NOT_REQUESTED|.
     */
    int channelMhz;

    /**
     * NAN data interface name on which this data-path session is to be initiated.
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
