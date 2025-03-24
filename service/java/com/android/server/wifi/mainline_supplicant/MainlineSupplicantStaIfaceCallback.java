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
import android.annotation.Nullable;
import android.annotation.SuppressLint;
import android.net.MacAddress;
import android.net.wifi.usd.Config;
import android.net.wifi.usd.SessionCallback;
import android.system.wifi.mainline_supplicant.IStaInterfaceCallback;
import android.system.wifi.mainline_supplicant.UsdMessageInfo;
import android.system.wifi.mainline_supplicant.UsdServiceProtoType;
import android.util.Log;

import com.android.server.wifi.WifiThreadRunner;
import com.android.server.wifi.usd.UsdNativeManager;
import com.android.server.wifi.usd.UsdRequestManager;

/**
 * Implementation of the mainline supplicant {@link IStaInterfaceCallback}.
 */
public class MainlineSupplicantStaIfaceCallback extends IStaInterfaceCallback.Stub {
    private static final String TAG = "MainlineSupplicantStaIfaceCallback";

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
    public void onUsdPublishStarted(int cmdId, int publishId) {
        mWifiThreadRunner.post(() -> {
            UsdNativeManager.UsdEventsCallback usdCallback =
                    mMainlineSupplicant.getUsdEventsCallback();
            if (usdCallback == null) {
                Log.e(TAG, "UsdEventsCallback callback is null");
                return;
            }
            usdCallback.onUsdPublishStarted(cmdId, publishId);
        });
    }

    /**
     * Called in response to startUsdSubscribe to indicate that the subscribe session
     * was started successfully.
     *
     * @param cmdId Identifier for the original request.
     * @param subscribeId Identifier for the subscribe session.
     */
    @Override
    public void onUsdSubscribeStarted(int cmdId, int subscribeId) {
        mWifiThreadRunner.post(() -> {
            UsdNativeManager.UsdEventsCallback usdCallback =
                    mMainlineSupplicant.getUsdEventsCallback();
            if (usdCallback == null) {
                Log.e(TAG, "UsdEventsCallback callback is null");
                return;
            }
            usdCallback.onUsdSubscribeStarted(cmdId, subscribeId);
        });
    }

    @SuppressLint("NewApi")
    private static @SessionCallback.FailureCode int
            convertHalToFrameworkUsdConfigErrorCode(int errorCode) {
        switch (errorCode) {
            case UsdConfigErrorCode.FAILURE_TIMEOUT:
                return SessionCallback.FAILURE_TIMEOUT;
            case UsdConfigErrorCode.FAILURE_NOT_AVAILABLE:
                return SessionCallback.FAILURE_NOT_AVAILABLE;
            default:
                return SessionCallback.FAILURE_UNKNOWN;
        }
    }

    /**
     * Called in response to startUsdPublish to indicate that the publish session
     * could not be configured.
     *
     * @param cmdId Identifier for the original request.
     * @param errorCode Code indicating the failure reason.
     */
    @Override public void onUsdPublishConfigFailed(int cmdId, int errorCode)  {
        mWifiThreadRunner.post(() -> {
            UsdNativeManager.UsdEventsCallback usdCallback =
                    mMainlineSupplicant.getUsdEventsCallback();
            if (usdCallback == null) {
                Log.e(TAG, "UsdEventsCallback callback is null");
                return;
            }
            usdCallback.onUsdPublishConfigFailed(cmdId,
                    convertHalToFrameworkUsdConfigErrorCode(errorCode));
        });
    }

    /**
     * Called in response to startUsdSubscribe to indicate that the subscribe session
     * could not be configured.
     *
     * @param cmdId Identifier for the original request.
     * @param errorCode Code indicating the failure reason.
     */
    @Override
    public void onUsdSubscribeConfigFailed(int cmdId, int errorCode) {
        mWifiThreadRunner.post(() -> {
            UsdNativeManager.UsdEventsCallback usdCallback =
                    mMainlineSupplicant.getUsdEventsCallback();
            if (usdCallback == null) {
                Log.e(TAG, "UsdEventsCallback callback is null");
                return;
            }
            usdCallback.onUsdSubscribeConfigFailed(cmdId,
                    convertHalToFrameworkUsdConfigErrorCode(errorCode));
        });
    }

    @SuppressLint("NewApi")
    private static @SessionCallback.TerminationReasonCode int
            convertHalToFrameworkUsdTerminateReasonCode(int reasonCode) {
        switch (reasonCode) {
            case UsdTerminateReasonCode.USER_REQUESTED:
                return SessionCallback.TERMINATION_REASON_USER_INITIATED;
            default:
                return SessionCallback.TERMINATION_REASON_UNKNOWN;
        }
    }

    /**
     * Called in response to cancelUsdPublish to indicate that the session was cancelled
     * successfully. May also be called unsolicited if the session terminated by supplicant.
     *
     * @param publishId Identifier for the publish session.
     * @param reasonCode Code indicating the reason for the session cancellation.
     */
    @Override
    public void onUsdPublishTerminated(int publishId, int reasonCode) {
        mWifiThreadRunner.post(() -> {
            UsdNativeManager.UsdEventsCallback usdCallback =
                    mMainlineSupplicant.getUsdEventsCallback();
            if (usdCallback == null) {
                Log.e(TAG, "UsdEventsCallback callback is null");
                return;
            }
            usdCallback.onUsdPublishTerminated(publishId,
                    convertHalToFrameworkUsdTerminateReasonCode(reasonCode));
        });
    }

    /**
     * Called in response to cancelUsdSubscribe to indicate that the session was cancelled
     * successfully. May also be called unsolicited if the session terminated by supplicant.
     *
     * @param subscribeId Identifier for the subscribe session.
     * @param reasonCode Code indicating the reason for the session cancellation.
     */
    @Override
    public void onUsdSubscribeTerminated(int subscribeId, int reasonCode) {
        mWifiThreadRunner.post(() -> {
            UsdNativeManager.UsdEventsCallback usdCallback =
                    mMainlineSupplicant.getUsdEventsCallback();
            if (usdCallback == null) {
                Log.e(TAG, "UsdEventsCallback callback is null");
                return;
            }
            usdCallback.onUsdSubscribeTerminated(subscribeId,
                    convertHalToFrameworkUsdTerminateReasonCode(reasonCode));
        });
    }

    private static @Config.ServiceProtoType int
            convertHalToFrameworkUsdProtocolType(int protocolType) {
        switch (protocolType) {
            case UsdServiceProtoType.CSA_MATTER:
                return Config.SERVICE_PROTO_TYPE_CSA_MATTER;
            default:
                return Config.SERVICE_PROTO_TYPE_GENERIC;
        }
    }

    private static @Nullable MacAddress safeConvertMacAddress(byte[] macAddrBytes) {
        if (macAddrBytes == null) return null;
        try {
            return MacAddress.fromBytes(macAddrBytes);
        } catch (Exception e) {
            return null;
        }
    }

    private static UsdRequestManager.UsdHalDiscoveryInfo
            convertHalToFrameworkUsdDiscoveryInfo(UsdServiceDiscoveryInfo discoveryInfo) {
        if (discoveryInfo.peerMacAddress == null
                || discoveryInfo.serviceSpecificInfo == null
                || discoveryInfo.matchFilter == null) {
            Log.e(TAG, "USD discovery info contains a null parameter");
            return null;
        }
        MacAddress peerAddress = safeConvertMacAddress(discoveryInfo.peerMacAddress);
        if (peerAddress == null) {
            Log.e(TAG, "USD discovery info contains an invalid peer address");
            return null;
        }
        return new UsdRequestManager.UsdHalDiscoveryInfo(discoveryInfo.ownId,
                discoveryInfo.peerId,
                peerAddress,
                discoveryInfo.serviceSpecificInfo,
                convertHalToFrameworkUsdProtocolType(discoveryInfo.serviceProtoType),
                discoveryInfo.isFsd,
                discoveryInfo.matchFilter);
    }

    /**
     * Indicates that the publisher sent a solicited publish message to the subscriber.
     *
     * @param info Instance of |UsdServiceDiscoveryInfo| containing information about the reply.
     */
    @Override
    public void onUsdPublishReplied(UsdServiceDiscoveryInfo info) {
        mWifiThreadRunner.post(() -> {
            UsdNativeManager.UsdEventsCallback usdCallback =
                    mMainlineSupplicant.getUsdEventsCallback();
            if (usdCallback == null) {
                Log.e(TAG, "UsdEventsCallback callback is null");
                return;
            }
            UsdRequestManager.UsdHalDiscoveryInfo convertedInfo =
                    convertHalToFrameworkUsdDiscoveryInfo(info);
            if (convertedInfo == null) {
                return;
            }
            usdCallback.onUsdPublishReplied(convertedInfo);
        });
    }

    /**
     * Indicates that a publisher was discovered. Only called if this device is acting as a
     * subscriber.
     *
     * @param info Instance of |UsdServiceDiscoveryInfo| containing information about the service.
     */
    @Override
    public void onUsdServiceDiscovered(UsdServiceDiscoveryInfo info) {
        mWifiThreadRunner.post(() -> {
            UsdNativeManager.UsdEventsCallback usdCallback =
                    mMainlineSupplicant.getUsdEventsCallback();
            if (usdCallback == null) {
                Log.e(TAG, "UsdEventsCallback callback is null");
                return;
            }
            UsdRequestManager.UsdHalDiscoveryInfo convertedInfo =
                    convertHalToFrameworkUsdDiscoveryInfo(info);
            if (convertedInfo == null) {
                return;
            }
            usdCallback.onUsdServiceDiscovered(convertedInfo);
        });
    }

    /**
     * Indicates that a message was received on an active USD link.
     *
     * @param messageInfo Information about the message that was received.
     */
    @Override
    public void onUsdMessageReceived(UsdMessageInfo messageInfo) {
        mWifiThreadRunner.post(() -> {
            UsdNativeManager.UsdEventsCallback usdCallback =
                    mMainlineSupplicant.getUsdEventsCallback();
            if (usdCallback == null) {
                Log.e(TAG, "UsdEventsCallback callback is null");
                return;
            }
            MacAddress peerAddress = safeConvertMacAddress(messageInfo.peerMacAddress);
            if (peerAddress == null) {
                Log.e(TAG, "USD message info contains an invalid peer address");
                return;
            }
            if (messageInfo.message == null) {
                Log.e(TAG, "USD message info contains a null message");
                return;
            }
            usdCallback.onUsdMessageReceived(messageInfo.ownId,
                    messageInfo.peerId,
                    peerAddress,
                    messageInfo.message);
        });
    }
}
