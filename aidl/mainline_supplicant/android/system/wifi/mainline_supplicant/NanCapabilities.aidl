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
 * NAN Capabilities response.
 */
parcelable NanCapabilities {
    /**
     * RTT Measurement Bandwidth.
     */
    @Backing(type="int")
    enum RttBw {
        BW_UNSPECIFIED = 0x0,
        BW_5MHZ = 0x01,
        BW_10MHZ = 0x02,
        BW_20MHZ = 0x04,
        BW_40MHZ = 0x08,
        BW_80MHZ = 0x10,
        BW_160MHZ = 0x20,
        BW_320MHZ = 0x40,
    }

    /**
     * Maximum number of concurrent publish discovery sessions.
     */
    int maxPublishes;

    /**
     * Maximum number of concurrent subscribe discovery sessions.
     */
    int maxSubscribes;

    /**
     * Maximum length (in bytes) of service name.
     */
    int maxServiceNameLen;

    /**
     * Maximum length (in bytes) of individual match filters.
     */
    int maxMatchFilterLen;

    /**
     * Maximum length (in bytes) of the service specific info field.
     */
    int maxServiceSpecificInfoLen;

    /**
     * Maximum length (in bytes) of the extended service specific info field.
     */
    int maxExtendedServiceSpecificInfoLen;

    /**
     * Maximum number of data interfaces (NDI) which can be created concurrently on the
     * device.
     */
    int maxNdiInterfaces;

    /**
     * Maximum number of data paths (NDP) which can be created concurrently on the device,
     * across all data interfaces (NDI).
     */
    int maxNdpSessions;

    /**
     * Maximum length (in bytes) of application info field (used in data-path negotiations).
     */
    int maxAppInfoLen;

    /**
     * Bitmap of |NanCipherSuiteType| values indicating the set of supported cipher suites.
     */
    int supportedCipherSuites;

    /**
     * Flag to indicate if instant communication mode is supported.
     */
    boolean instantCommunicationModeSupportFlag;

    /**
     * Flag to indicate if NAN pairing and all associated Aware R4 security features are
     * supported.
     *
     * This flag is set to true only if all of the following are supported:
     * - NAN Pairing (as in Wi-Fi Aware Specification Version 4.0 section 7.6)
     * - NDP unicast data frame encryption (as in Wi-Fi Aware Specification Version 4.0
     *   section 7.3.1)
     * - Group addressed data frame encryption (as in Wi-Fi Aware Specification Version 4.0
     *   section 7.3.3)
     * - Management frame protection (as in Wi-Fi Aware Specification Version 4.0
     *   section 7.3.2 for both unicast and multicast frames)
     * - Beacon integrity protection (as in Wi-Fi Aware Specification Version 4.0
     *   section 7.3.4)
     */
    boolean supportsPairing;

    /**
     * Flag to indicate if NAN suspension is supported.
     */
    boolean supportsSuspension;

    /**
     * Flag to indicate if NAN periodic ranging is supported.
     */
    boolean supportsPeriodicRanging;

    /**
     * Maximum supported bandwidth.
     */
    RttBw maxSupportedBandwidth;

    /**
     * Maximum number of supported receive chains.
     */
    int maxNumRxChainsSupported;
    }
