/*
 * Copyright (C) 2024 The Android Open Source Project
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

import android.system.wifi.mainline_supplicant.IStaInterfaceCallback;
import android.system.wifi.mainline_supplicant.UsdMessageInfo;
import android.system.wifi.mainline_supplicant.UsdServiceProtoType;

/**
 * Interface exposed by the supplicant for each station mode network
 * interface (ex. wlan0) it controls.
 */
interface IStaInterface {
    /**
     * Capabilities supported by USD. Values are only valid if |isUsdPublisherSupported|
     * and/or |isUsdSubscriberSupported| are true.
     */
    parcelable UsdCapabilities {
        /**
         * Whether USD Publisher is supported on this device.
         */
        boolean isUsdPublisherSupported;

        /**
         * Whether USD Subscriber is supported on this device.
         */
        boolean isUsdSubscriberSupported;

        /**
         * Maximum allowed length (in bytes) for the Service Specific Info (SSI).
         */
        int maxLocalSsiLengthBytes;

        /**
         * Maximum allowed length (in bytes) for the service name.
         */
        int maxServiceNameLengthBytes;

        /**
         * Maximum allowed length (in bytes) for a match filter.
         */
        int maxMatchFilterLengthBytes;

        /**
         * Maximum number of allowed publish sessions.
         */
        int maxNumPublishSessions;

        /**
         * Maximum number of allowed subscribe sessions.
         */
        int maxNumSubscribeSessions;
    }

    /**
     * Data used in both publish and subscribe configurations.
     */
    parcelable BaseConfig {
        /**
         * Service name of the USD session. A UTF-8 encoded string from 1 to 255 bytes in length.
         * The only acceptable single-byte UTF-8 symbols for a Service Name are alphanumeric
         * values (A-Z, a-z, 0-9), hyphen ('-'), period ('.'), and underscore ('_'). All
         * valid multi-byte UTF-8 characters are acceptable in a Service Name.
         */
        @utf8InCpp String serviceName;

        /**
         * Service protocol type for the USD session (ex. Generic, CSA Matter).
         */
        UsdServiceProtoType serviceProtoType;

        /**
         * Details about the service being offered or being looked for. This information is
         * transmitted within Service Discovery frames, and is used to help devices find each other
         * and establish connections. The format and content of the service specific information are
         * flexible and can be determined by the application.
         */
        byte[] serviceSpecificInfo;

        /**
         * Ordered sequence of <length, value> pairs (|length| uses 1 byte and contains the number
         * of bytes in the |value| field) which specify further match criteria (beyond the service
         * name).
         *
         * The match behavior is specified in details in the NAN spec.
         * Publisher: used if provided.
         * Subscriber: used (if provided) only in ACTIVE sessions.
         *
         * Max length: |UsdCapabilities.maxMatchFilterLength|.
         * NAN Spec: matching_filter_tx and Service Descriptor Attribute (SDA) / Matching Filter
         */
        @nullable byte[] txMatchFilter;

        /**
         * Ordered sequence of <length, value> pairs (|length| uses 1 byte and contains the number
         * of bytes in the |value| field) which specify further match criteria (beyond the service
         * name).
         *
         * The match behavior is specified in details in the NAN spec.
         * Publisher: used in SOLICITED or SOLICITED_UNSOLICITED sessions.
         * Subscriber: used in ACTIVE or PASSIVE sessions.
         *
         * Max length: |UsdCapabilities.maxMatchFilterLength|.
         * NAN Spec: matching_filter_rx
         */
        @nullable byte[] rxMatchfilter;

        /**
         * Time interval (in seconds) that a USD session will be alive.
         * The session will be terminated when the time to live (TTL) is reached, triggering either
         * |IUsdCallback.onPublishTerminated| for Publish, or |IUsdCallback.onSubscribeTerminated|
         * for Subscribe.
         */
        int ttlSec;

        /**
         * Frequency where the device should begin to dwell. Default value is channel 6 (2.437 GHz),
         * but other values may be selected per regulation in the geographical location.
         */
        int defaultFreqMhz;

        /**
         * Channels which can be switched to. May contain any of the 20 MHz channels in the
         * 2.4 Ghz and/or 5 Ghz bands, per regulation in the geographical location.
         */
        int[] freqsMhz;
    }

    /**
     * Subscribe modes that this session can be configured in.
     */
    enum SubscribeType {
        /**
         * Subscribe function does not request transmission of any Subscribe messages, but checks
         * for matches in received Publish messages.
         */
        PASSIVE_MODE = 0,
        /**
         * Subscribe function additionally requests transmission of Subscribe messages and processes
         * Publish messages.
         */
        ACTIVE_MODE = 1,
    }

    /**
     * Parameters for configuring a USD subscribe session.
     */
    parcelable SubscribeConfig {
        /**
         * Base USD session parameters.
         */
        BaseConfig baseConfig;

        /**
         * Subscribe mode that this session should be configured in.
         */
        SubscribeType subscribeType;

        /**
         * Recommended periodicity (in milliseconds) of query transmissions for the session.
         */
        int queryPeriodMillis;
    }

    /**
     * Type of USD publishing.
     */
    enum PublishType {
        /**
         * Only transmissions that are triggered by a specific event.
         */
        SOLICITED_ONLY = 0,

        /**
         * Only transmissions that are not requested.
         */
        UNSOLICITED_ONLY = 1,

        /**
         * Both solicited and unsolicited transmissions.
         */
        SOLICITED_AND_UNSOLICITED = 2,
    }

    /**
     * Types of USD publish transmissions.
     */
    @Backing(type="int")
    enum PublishTransmissionType {
        /**
         * Sends data from one device to a single, specific destination device.
         */
        UNICAST = 0,

        /**
         * Sends data from one device to a group of devices on the network simultaneously.
         */
        MULTICAST = 1,
    }

    /**
     * Parameters for configuring a USD publish session.
     */
    parcelable PublishConfig {
        /**
         * Base USD session parameters.
         */
        BaseConfig baseConfig;

        /**
         * Types of transmissions (solicited vs. unsolicited) which should be generated.
         */
        PublishType publishType;

        /**
         * Whether Further Service Discovery (FSD) is enabled.
         */
        boolean isFsd;

        /**
         * Interval (in milliseconds) for sending unsolicited publish transmissions.
         */
        int announcementPeriodMillis;

        /**
         * Type of the publish transmission (ex. unicast, multicast).
         */
        PublishTransmissionType transmissionType;
    }

    /**
     * Register for callbacks on this interface.
     *
     * @param callback Callback object to invoke.
     */
    void registerCallback(in IStaInterfaceCallback callback);

    /**
     * Retrieve capabilities related to Unsynchronized Service Discovery (USD).
     *
     * @return Instance of |UsdCapabilities| containing the capability info.
     */
    UsdCapabilities getUsdCapabilities();

    /**
     * Start a USD publish session. Triggers a response via |IUsdCallback.onPublishStarted|
     * if successful, or |IUsdCallback.onPublishConfigFailed| if failed.
     *
     * @param cmdId Identifier for this request. Will be returned in the callback to identify
     *              the request.
     * @param usdPublishConfig Parameters for the requested publish session.
     * @throws ServiceSpecificException with one of the following values:
     *         |SupplicantStatusCode.FAILURE_UNKNOWN|
     *         |SupplicantStatusCode.FAILURE_UNSUPPORTED|
     */
    void startUsdPublish(in int cmdId, in PublishConfig usdPublishConfig);

    /**
     * Start a USD subscribe session. Triggers a response via |IUsdCallback.onSubscribeStarted|
     * if successful, or |IUsdCallback.onSubscribeConfigFailed| if failed.
     *
     * @param cmdId Identifier for this request. Will be returned in the callback to identify
     *              the request.
     * @param usdSubscribeConfig Parameters for the requested subscribe session.
     * @throws ServiceSpecificException with one of the following values:
     *         |SupplicantStatusCode.FAILURE_UNKNOWN|
     *         |SupplicantStatusCode.FAILURE_UNSUPPORTED|
     */
    void startUsdSubscribe(in int cmdId, in SubscribeConfig usdSubscribeConfig);

    /**
     * Update the service-specific info for an active publish session.
     *
     * @param publishId Identifier for the active publish session.
     * @param serviceSpecificInfo Byte array containing the service-specific info. Note that the
     *                            maximum info length is |UsdCapabilities.maxLocalSsiLengthBytes|.
     * @throws ServiceSpecificException with one of the following values:
     *         |SupplicantStatusCode.FAILURE_UNKNOWN|
     *         |SupplicantStatusCode.FAILURE_UNSUPPORTED|
     */
    void updateUsdPublish(in int publishId, in byte[] serviceSpecificInfo);

    /**
     * Cancel an existing USD publish session. |IUsdCallback.onPublishTerminated|
     * will be called upon completion.
     *
     * @param publishId Identifier for the publish session to cancel.
     * @throws ServiceSpecificException with one of the following values:
     *         |SupplicantStatusCode.FAILURE_UNKNOWN|
     *         |SupplicantStatusCode.FAILURE_UNSUPPORTED|
     */
    void cancelUsdPublish(in int publishId);

    /**
     * Cancel an existing USD subscribe session.
     * |IUsdCallback.onSubscribeTerminated| will be called upon completion.
     *
     * @param subscribeId Identifier for the subscribe session to cancel.
     * @throws ServiceSpecificException with one of the following values:
     *         |SupplicantStatusCode.FAILURE_UNKNOWN|
     *         |SupplicantStatusCode.FAILURE_UNSUPPORTED|
     */
    void cancelUsdSubscribe(in int subscribeId);

    /**
     * Send a message to a peer device across an active USD link.
     *
     * @param messageInfo Information for the message to be sent.
     * @throws ServiceSpecificException with one of the following values:
     *         |SupplicantStatusCode.FAILURE_UNKNOWN|
     *         |SupplicantStatusCode.FAILURE_UNSUPPORTED|
     */
    void sendUsdMessage(in UsdMessageInfo messageInfo);
}
