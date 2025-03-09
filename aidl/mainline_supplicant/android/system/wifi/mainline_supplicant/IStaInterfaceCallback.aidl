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

import android.system.wifi.mainline_supplicant.UsdMessageInfo;
import android.system.wifi.mainline_supplicant.UsdServiceProtoType;

/**
 * Callback interface exposed by the mainline supplicant service
 * for each station mode interface (IStaInterface).
 *
 * Clients need to host an instance of this AIDL interface object and
 * pass a reference of the object to the mainline supplicant via the
 * corresponding |IStaInterface.registerCallback| method.
 */
interface IStaInterfaceCallback {
    /**
     * Information about a USD discovery session with a specific peer.
     */
    @VintfStability
    parcelable UsdServiceDiscoveryInfo {
        /**
         * Identifier for this device.
         */
        int ownId;

        /**
         * Identifier for the discovered peer device.
         */
        int peerId;

        /**
         * MAC address of the discovered peer device.
         */
        byte[6] peerMacAddress;

        /**
         * Match filter from the discovery packet (publish or subscribe) which caused service
         * discovery.
         */
        byte[] matchFilter;

        /**
         * Service protocol that is being used (ex. Generic, CSA Matter).
         */
        UsdServiceProtoType serviceProtoType;

        /**
         * Arbitrary service specific information communicated in discovery packets.
         * There is no semantic meaning to these bytes. They are passed-through from publisher to
         * subscriber as-is with no parsing.
         */
        byte[] serviceSpecificInfo;

        /**
         * Whether Further Service Discovery (FSD) is enabled.
         */
        boolean isFsd;
    }

    /**
     * Codes indicating the status of USD operations.
     */
    @Backing(type="int")
    enum UsdReasonCode {
        /**
         * Unknown failure occurred.
         */
        FAILURE_UNKNOWN = 0,

        /**
         * The operation timed out.
         */
        TIMEOUT = 1,

        /**
         * The operation was requested by the user.
         */
        USER_REQUESTED = 2,

        /**
         * Invalid arguments were provided.
         */
        INVALID_ARGS = 3
    }

    /**
     * Called in response to |IUsdInterface.startPublish| to indicate that the
     * publish session was started successfully.
     *
     * @param cmdId Identifier for the original request.
     * @param publishId Identifier for the publish session.
     */
    void onUsdPublishStarted(in int cmdId, in int publishId);

    /**
     * Called in response to |IUsdInterface.startSubscribe| to indicate that the
     * subscribe session was started successfully.
     *
     * @param cmdId Identifier for the original request.
     * @param subscribeId Identifier for the subscribe session.
     */
    void onUsdSubscribeStarted(in int cmdId, in int subscribeId);

    /**
     * Called in response to |IUsdInterface.startPublish| to indicate that the
     * publish session could not be configured.
     *
     * @param cmdId Identifier for the original request.
     */
    void onUsdPublishConfigFailed(in int cmdId);

    /**
     * Called in response to |IUsdInterface.startSubscribe| to indicate that the
     * subscribe session could not be configured.
     *
     * @param cmdId Identifier for the original request.
     */
    void onUsdSubscribeConfigFailed(in int cmdId);

    /**
     * Called in response to |IUsdInterface.cancelPublish| to indicate that the session
     * was cancelled successfully. May also be called unsolicited if the session terminated
     * by supplicant.
     *
     * @param publishId Identifier for the publish session.
     * @param reasonCode Code indicating the reason for the session cancellation.
     */
    void onUsdPublishTerminated(in int publishId, in UsdReasonCode reasonCode);

    /**
     * Called in response to |IUsdInterface.cancelSubscribe| to indicate that the session
     * was cancelled successfully. May also be called unsolicited if the session terminated
     * by supplicant.
     *
     * @param subscribeId Identifier for the subscribe session.
     * @param reasonCode Code indicating the reason for the session cancellation.
     */
    void onUsdSubscribeTerminated(in int subscribeId, in UsdReasonCode reasonCode);

    /**
     * Indicates that the publisher sent solicited publish message to the subscriber.
     *
     * @param info Instance of |UsdServiceDiscoveryInfo| containing information about the reply.
     */
    void onUsdPublishReplied(in UsdServiceDiscoveryInfo info);

    /**
     * Indicates that a publisher was discovered. Only called if this device is acting as a
     * subscriber.
     *
     * @param info Instance of |UsdServiceDiscoveryInfo| containing information about the service.
     */
    void onUsdServiceDiscovered(in UsdServiceDiscoveryInfo info);

    /**
     * Indicates that a message was received on an active USD link.
     *
     * @param messageInfo Information about the message that was received.
     */
    void onUsdMessageReceived(in UsdMessageInfo messageInfo);
}
