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

package com.android.server.wifi.mainline_supplicant;

import android.annotation.NonNull;
import android.system.wifi.mainline_supplicant.IStaInterfaceCallback;
import android.system.wifi.mainline_supplicant.UsdMessageInfo;

import com.android.server.wifi.WifiThreadRunner;

/**
 * Implementation of the mainline supplicant {@link IStaInterfaceCallback}.
 */
public class MainlineSupplicantStaIfaceCallback extends IStaInterfaceCallback.Stub {
    private final MainlineSupplicant mMainlineSupplicant;
    private final String mIfaceName;
    private final WifiThreadRunner mWifiThreadRunner;

    MainlineSupplicantStaIfaceCallback(@NonNull MainlineSupplicant mainlineSupplicant,
            @NonNull String ifaceName, @NonNull WifiThreadRunner wifiThreadRunner) {
        mMainlineSupplicant = mainlineSupplicant;
        mIfaceName = ifaceName;
        mWifiThreadRunner = wifiThreadRunner;
    }

    /**
     * Called in response to startUsdPublish to indicate that the publish session
     * was started successfully.
     *
     * @param cmdId Identifier for the original request.
     * @param publishId Identifier for the publish session.
     */
    @Override
    public void onUsdPublishStarted(int cmdId, int publishId) { }

    /**
     * Called in response to startUsdSubscribe to indicate that the subscribe session
     * was started successfully.
     *
     * @param cmdId Identifier for the original request.
     * @param subscribeId Identifier for the subscribe session.
     */
    @Override
    public void onUsdSubscribeStarted(int cmdId, int subscribeId) { }

    /**
     * Called in response to startUsdPublish to indicate that the publish session
     * could not be configured.
     *
     * @param cmdId Identifier for the original request.
     * @param errorCode Code indicating the failure reason.
     */
    @Override public void onUsdPublishConfigFailed(int cmdId, int errorCode)  { }

    /**
     * Called in response to startUsdSubscribe to indicate that the subscribe session
     * could not be configured.
     *
     * @param cmdId Identifier for the original request.
     * @param errorCode Code indicating the failure reason.
     */
    @Override
    public void onUsdSubscribeConfigFailed(int cmdId, int errorCode) { }

    /**
     * Called in response to cancelUsdPublish to indicate that the session was cancelled
     * successfully. May also be called unsolicited if the session terminated by supplicant.
     *
     * @param publishId Identifier for the publish session.
     * @param reasonCode Code indicating the reason for the session cancellation.
     */
    @Override
    public void onUsdPublishTerminated(int publishId, int reasonCode) { }

    /**
     * Called in response to cancelUsdSubscribe to indicate that the session was cancelled
     * successfully. May also be called unsolicited if the session terminated by supplicant.
     *
     * @param subscribeId Identifier for the subscribe session.
     * @param reasonCode Code indicating the reason for the session cancellation.
     */
    @Override
    public void onUsdSubscribeTerminated(int subscribeId, int reasonCode) {  }

    /**
     * Indicates that the publisher sent a solicited publish message to the subscriber.
     *
     * @param info Instance of |UsdServiceDiscoveryInfo| containing information about the reply.
     */
    @Override
    public void onUsdPublishReplied(UsdServiceDiscoveryInfo info) { }

    /**
     * Indicates that a publisher was discovered. Only called if this device is acting as a
     * subscriber.
     *
     * @param info Instance of |UsdServiceDiscoveryInfo| containing information about the service.
     */
    @Override
    public void onUsdServiceDiscovered(UsdServiceDiscoveryInfo info) { }

    /**
     * Indicates that a message was received on an active USD link.
     *
     * @param messageInfo Information about the message that was received.
     */
    @Override
    public void onUsdMessageReceived(UsdMessageInfo messageInfo) { }
}
