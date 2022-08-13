/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.server.wifi;

import static android.net.wifi.aware.Characteristics.WIFI_AWARE_CIPHER_SUITE_NCS_PK_128;
import static android.net.wifi.aware.Characteristics.WIFI_AWARE_CIPHER_SUITE_NCS_PK_256;
import static android.net.wifi.aware.Characteristics.WIFI_AWARE_CIPHER_SUITE_NCS_SK_128;
import static android.net.wifi.aware.Characteristics.WIFI_AWARE_CIPHER_SUITE_NCS_SK_256;

import android.annotation.NonNull;
import android.hardware.wifi.V1_0.IWifiNanIface;
import android.hardware.wifi.V1_0.NanBandIndex;
import android.hardware.wifi.V1_0.NanBandSpecificConfig;
import android.hardware.wifi.V1_0.NanConfigRequest;
import android.hardware.wifi.V1_0.NanDataPathSecurityType;
import android.hardware.wifi.V1_0.NanEnableRequest;
import android.hardware.wifi.V1_0.NanInitiateDataPathRequest;
import android.hardware.wifi.V1_0.NanMatchAlg;
import android.hardware.wifi.V1_0.NanPublishRequest;
import android.hardware.wifi.V1_0.NanRespondToDataPathIndicationRequest;
import android.hardware.wifi.V1_0.NanSubscribeRequest;
import android.hardware.wifi.V1_0.NanTransmitFollowupRequest;
import android.hardware.wifi.V1_0.NanTxType;
import android.hardware.wifi.V1_0.WifiStatus;
import android.hardware.wifi.V1_0.WifiStatusCode;
import android.hardware.wifi.V1_6.NanCipherSuiteType;
import android.net.wifi.aware.ConfigRequest;
import android.net.wifi.aware.PublishConfig;
import android.net.wifi.aware.SubscribeConfig;
import android.net.wifi.aware.WifiAwareChannelInfo;
import android.net.wifi.aware.WifiAwareDataPathSecurityConfig;
import android.os.RemoteException;
import android.util.Log;

import com.android.internal.annotations.VisibleForTesting;
import com.android.server.wifi.aware.Capabilities;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Wrapper class for IWifiNanIface HAL calls.
 */
public class WifiNanIface {
    private static final String TAG = "WifiNanIface";

    @VisibleForTesting
    static final String SERVICE_NAME_FOR_OOB_DATA_PATH = "Wi-Fi Aware Data Path";

    private IWifiNanIface mIWifiNanIface;
    private WifiNanIfaceCallback mHalCallback;
    private Callback mFrameworkCallback;


    // Enums. See HAL for more information about each type.

    /**
     * Event types for a cluster event indication.
     */
    public static class NanClusterEventType {
        public static final int DISCOVERY_MAC_ADDRESS_CHANGED = 0;
        public static final int STARTED_CLUSTER = 1;
        public static final int JOINED_CLUSTER = 2;

        /**
         * Convert NanClusterEventType from HIDL to framework.
         */
        public static int fromHidl(int code) {
            switch (code) {
                case android.hardware.wifi.V1_0.NanClusterEventType.DISCOVERY_MAC_ADDRESS_CHANGED:
                    return DISCOVERY_MAC_ADDRESS_CHANGED;
                case android.hardware.wifi.V1_0.NanClusterEventType.STARTED_CLUSTER:
                    return STARTED_CLUSTER;
                case android.hardware.wifi.V1_0.NanClusterEventType.JOINED_CLUSTER:
                    return JOINED_CLUSTER;
                default:
                    Log.e(TAG, "Unknown NanClusterEventType received from HIDL: " + code);
                    return -1;
            }
        }
    }

    /**
     * NAN DP (data-path) channel config options.
     */
    public static class NanDataPathChannelCfg {
        public static final int CHANNEL_NOT_REQUESTED = 0;
        public static final int REQUEST_CHANNEL_SETUP = 1;
        public static final int FORCE_CHANNEL_SETUP = 2;

        /**
         * Convert NanDataPathChannelCfg from framework to HIDL.
         */
        public static int toHidl(int code) {
            switch (code) {
                case CHANNEL_NOT_REQUESTED:
                    return android.hardware.wifi.V1_0.NanDataPathChannelCfg.CHANNEL_NOT_REQUESTED;
                case REQUEST_CHANNEL_SETUP:
                    return android.hardware.wifi.V1_0.NanDataPathChannelCfg.REQUEST_CHANNEL_SETUP;
                case FORCE_CHANNEL_SETUP:
                    return android.hardware.wifi.V1_0.NanDataPathChannelCfg.FORCE_CHANNEL_SETUP;
                default:
                    Log.e(TAG, "Unknown NanDataPathChannelCfg received from framework: " + code);
                    return -1;
            }
        }
    }

    /**
     * Ranging in the context of discovery session indication controls.
     */
    public static class NanRangingIndication {
        public static final int CONTINUOUS_INDICATION_MASK = 1 << 0;
        public static final int INGRESS_MET_MASK = 1 << 1;
        public static final int EGRESS_MET_MASK = 1 << 2;

        /**
         * Convert NanRangingIndication from HIDL to framework.
         */
        public static int fromHidl(int rangingInd) {
            int frameworkRangingInd = 0;
            if ((android.hardware.wifi.V1_0.NanRangingIndication.CONTINUOUS_INDICATION_MASK
                    & rangingInd) != 0) {
                frameworkRangingInd |= CONTINUOUS_INDICATION_MASK;
            }
            if ((android.hardware.wifi.V1_0.NanRangingIndication.INGRESS_MET_MASK
                    & rangingInd) != 0) {
                frameworkRangingInd |= INGRESS_MET_MASK;
            }
            if ((android.hardware.wifi.V1_0.NanRangingIndication.EGRESS_MET_MASK
                    & rangingInd) != 0) {
                frameworkRangingInd |= EGRESS_MET_MASK;
            }
            return frameworkRangingInd;
        }
    }

    /**
     * NAN API response codes used in request notifications and events.
     */
    public static class NanStatusCode {
        public static final int SUCCESS = 0;
        public static final int INTERNAL_FAILURE = 1;
        public static final int PROTOCOL_FAILURE = 2;
        public static final int INVALID_SESSION_ID = 3;
        public static final int NO_RESOURCES_AVAILABLE = 4;
        public static final int INVALID_ARGS = 5;
        public static final int INVALID_PEER_ID = 6;
        public static final int INVALID_NDP_ID = 7;
        public static final int NAN_NOT_ALLOWED = 8;
        public static final int NO_OTA_ACK = 9;
        public static final int ALREADY_ENABLED = 10;
        public static final int FOLLOWUP_TX_QUEUE_FULL = 11;
        public static final int UNSUPPORTED_CONCURRENCY_NAN_DISABLED = 12;

        /**
         * Convert NanStatusCode from HIDL to framework.
         */
        public static int fromHidl(int code) {
            switch (code) {
                case android.hardware.wifi.V1_0.NanStatusType.SUCCESS:
                    return SUCCESS;
                case android.hardware.wifi.V1_0.NanStatusType.INTERNAL_FAILURE:
                    return INTERNAL_FAILURE;
                case android.hardware.wifi.V1_0.NanStatusType.PROTOCOL_FAILURE:
                    return PROTOCOL_FAILURE;
                case android.hardware.wifi.V1_0.NanStatusType.INVALID_SESSION_ID:
                    return INVALID_SESSION_ID;
                case android.hardware.wifi.V1_0.NanStatusType.NO_RESOURCES_AVAILABLE:
                    return NO_RESOURCES_AVAILABLE;
                case android.hardware.wifi.V1_0.NanStatusType.INVALID_ARGS:
                    return INVALID_ARGS;
                case android.hardware.wifi.V1_0.NanStatusType.INVALID_PEER_ID:
                    return INVALID_PEER_ID;
                case android.hardware.wifi.V1_0.NanStatusType.INVALID_NDP_ID:
                    return INVALID_NDP_ID;
                case android.hardware.wifi.V1_0.NanStatusType.NAN_NOT_ALLOWED:
                    return NAN_NOT_ALLOWED;
                case android.hardware.wifi.V1_0.NanStatusType.NO_OTA_ACK:
                    return NO_OTA_ACK;
                case android.hardware.wifi.V1_0.NanStatusType.ALREADY_ENABLED:
                    return ALREADY_ENABLED;
                case android.hardware.wifi.V1_0.NanStatusType.FOLLOWUP_TX_QUEUE_FULL:
                    return FOLLOWUP_TX_QUEUE_FULL;
                case android.hardware.wifi.V1_0.NanStatusType.UNSUPPORTED_CONCURRENCY_NAN_DISABLED:
                    return UNSUPPORTED_CONCURRENCY_NAN_DISABLED;
                default:
                    Log.e(TAG, "Unknown NanStatusType received from HIDL: " + code);
                    return -1;
            }
        }
    }

    /**
     * Configuration parameters used in the call to enableAndConfigure.
     */
    public static class PowerParameters {
        public int discoveryWindow24Ghz;
        public int discoveryWindow5Ghz;
        public int discoveryWindow6Ghz;
        public int discoveryBeaconIntervalMs;
        public int numberOfSpatialStreamsInDiscovery;
        public boolean enableDiscoveryWindowEarlyTermination;
    }

    public WifiNanIface(@NonNull IWifiNanIface iface) {
        mIWifiNanIface = iface;
        mHalCallback = new WifiNanIfaceCallback(this);
    }

    /**
     * Enable verbose logging.
     */
    public void enableVerboseLogging(boolean verbose) {
        if (mHalCallback != null) {
            mHalCallback.enableVerboseLogging(verbose);
        }
    }

    /**
     * Get the underlying HIDL IWifiNanIface object.
     */
    protected IWifiNanIface getNanIface() {
        return mIWifiNanIface;
    }

    /**
     * Register a framework callback to receive notifications from the HAL.
     * @param cb Instance of {@link Callback}.
     * @return true is successful, false otherwise
     */
    public boolean registerFrameworkCallback(Callback cb) {
        final String methodStr = "registerFrameworkCallback";
        if (!checkNanIfaceAndLogFailure(methodStr)) {
            return false;
        }
        if (mFrameworkCallback != null) {
            Log.e(TAG, "Framework callback is already registered");
            return false;
        } else if (cb == null) {
            Log.e(TAG, "Cannot register a null framework callback");
            return false;
        }

        WifiStatus status;
        try {
            android.hardware.wifi.V1_2.IWifiNanIface iface12 = mockableCastTo_1_2(mIWifiNanIface);
            android.hardware.wifi.V1_5.IWifiNanIface iface15 = mockableCastTo_1_5(mIWifiNanIface);
            android.hardware.wifi.V1_6.IWifiNanIface iface16 = mockableCastTo_1_6(mIWifiNanIface);
            if (iface16 != null) {
                status = iface16.registerEventCallback_1_6(mHalCallback);
            } else if (iface15 != null) {
                status = iface15.registerEventCallback_1_5(mHalCallback);
            } else if (iface12 != null) {
                status = iface12.registerEventCallback_1_2(mHalCallback);
            } else {
                status = mIWifiNanIface.registerEventCallback(mHalCallback);
            }
            if (status.code != WifiStatusCode.SUCCESS) {
                Log.e(TAG, "Unable to register HAL callback, error: " + statusString(status));
                return false;
            }
        } catch (RemoteException e) {
            Log.e(TAG, "registerEventCallback: exception: " + e);
            return false;
        }
        mFrameworkCallback = cb;
        return true;
    }

    public Callback getFrameworkCallback() {
        return mFrameworkCallback;
    }

    /**
     * (HIDL) Cast the input to a 1.2 NAN interface (possibly resulting in a null).
     *
     * Separate function so can be mocked in unit tests.
     */
    public android.hardware.wifi.V1_2.IWifiNanIface mockableCastTo_1_2(IWifiNanIface iface) {
        return android.hardware.wifi.V1_2.IWifiNanIface.castFrom(iface);
    }

    /**
     * (HIDL) Cast the input to a 1.4 NAN interface (possibly resulting in a null).
     *
     * Separate function so can be mocked in unit tests.
     */
    public android.hardware.wifi.V1_4.IWifiNanIface mockableCastTo_1_4(IWifiNanIface iface) {
        return android.hardware.wifi.V1_4.IWifiNanIface.castFrom(iface);
    }

    /**
     * (HIDL) Cast the input to a 1.5 NAN interface (possibly resulting in a null).
     *
     * Separate function so can be mocked in unit tests.
     */
    public android.hardware.wifi.V1_5.IWifiNanIface mockableCastTo_1_5(IWifiNanIface iface) {
        return android.hardware.wifi.V1_5.IWifiNanIface.castFrom(iface);
    }

    /**
     * (HIDL) Cast the input to a 1.6 NAN interface (possibly resulting in a null).
     *
     * Separate function so can be mocked in unit tests.
     */
    public android.hardware.wifi.V1_6.IWifiNanIface mockableCastTo_1_6(IWifiNanIface iface) {
        return android.hardware.wifi.V1_6.IWifiNanIface.castFrom(iface);
    }

    /**
     * Query the firmware's capabilities.
     *
     * @param transactionId Transaction ID for the transaction - used in the async callback to
     *                      match with the original request.
     */
    public boolean getCapabilities(short transactionId) {
        final String methodStr = "getCapabilities";
        if (!checkNanIfaceAndLogFailure(methodStr)) {
            return false;
        }

        android.hardware.wifi.V1_5.IWifiNanIface iface15 = mockableCastTo_1_5(mIWifiNanIface);
        try {
            WifiStatus status;
            if (iface15 == null) {
                status = mIWifiNanIface.getCapabilitiesRequest(transactionId);
            }  else {
                status = iface15.getCapabilitiesRequest_1_5(transactionId);
            }
            if (status.code == WifiStatusCode.SUCCESS) {
                return true;
            } else {
                Log.e(TAG, "getCapabilities: error: " + statusString(status));
                return false;
            }
        } catch (RemoteException e) {
            Log.e(TAG, "getCapabilities: exception: " + e);
            return false;
        }
    }

    /**
     * Enable and configure Aware.
     * @param transactionId Transaction ID for the transaction - used in the
     *            async callback to match with the original request.
     * @param configRequest Requested Aware configuration.
     * @param notifyIdentityChange Indicates whether to get address change callbacks.
     * @param initialConfiguration Specifies whether initial configuration
     *            (true) or an update (false) to the configuration.
     * @param rangingEnabled Indicates whether to enable ranging.
     * @param isInstantCommunicationEnabled Indicates whether to enable instant communication
     * @param instantModeChannel
     * @param macAddressRandomizationIntervalSec
     * @param powerParameters Instance of {@link PowerParameters} containing the parameters to
     *                        use in our config request.
     */
    public boolean enableAndConfigure(short transactionId, ConfigRequest configRequest,
            boolean notifyIdentityChange, boolean initialConfiguration, boolean rangingEnabled,
            boolean isInstantCommunicationEnabled, int instantModeChannel,
            int macAddressRandomizationIntervalSec, PowerParameters powerParameters) {
        final String methodStr = "enableAndConfigure";
        if (!checkNanIfaceAndLogFailure(methodStr)) {
            return false;
        }

        android.hardware.wifi.V1_2.IWifiNanIface iface12 = mockableCastTo_1_2(mIWifiNanIface);
        android.hardware.wifi.V1_4.IWifiNanIface iface14 = mockableCastTo_1_4(mIWifiNanIface);
        android.hardware.wifi.V1_5.IWifiNanIface iface15 = mockableCastTo_1_5(mIWifiNanIface);
        android.hardware.wifi.V1_6.IWifiNanIface iface16 = mockableCastTo_1_6(mIWifiNanIface);
        android.hardware.wifi.V1_2.NanConfigRequestSupplemental configSupplemental12 =
                new android.hardware.wifi.V1_2.NanConfigRequestSupplemental();
        android.hardware.wifi.V1_5.NanConfigRequestSupplemental configSupplemental15 =
                new android.hardware.wifi.V1_5.NanConfigRequestSupplemental();
        android.hardware.wifi.V1_6.NanConfigRequestSupplemental configSupplemental16 =
                new android.hardware.wifi.V1_6.NanConfigRequestSupplemental();
        if (iface12 != null || iface14 != null) {
            configSupplemental12.discoveryBeaconIntervalMs = 0;
            configSupplemental12.numberOfSpatialStreamsInDiscovery = 0;
            configSupplemental12.enableDiscoveryWindowEarlyTermination = false;
            configSupplemental12.enableRanging = rangingEnabled;
        }

        if (iface15 != null) {
            configSupplemental15.V1_2 = configSupplemental12;
            configSupplemental15.enableInstantCommunicationMode = isInstantCommunicationEnabled;
        }
        if (iface16 != null) {
            configSupplemental16.V1_5 = configSupplemental15;
            configSupplemental16.instantModeChannel = instantModeChannel;
        }

        NanBandSpecificConfig config24 = new NanBandSpecificConfig();
        config24.rssiClose = 60;
        config24.rssiMiddle = 70;
        config24.rssiCloseProximity = 60;
        config24.dwellTimeMs = (byte) 200;
        config24.scanPeriodSec = 20;
        if (configRequest.mDiscoveryWindowInterval[ConfigRequest.NAN_BAND_24GHZ]
                == ConfigRequest.DW_INTERVAL_NOT_INIT) {
            config24.validDiscoveryWindowIntervalVal = false;
        } else {
            config24.validDiscoveryWindowIntervalVal = true;
            config24.discoveryWindowIntervalVal =
                    (byte) configRequest.mDiscoveryWindowInterval[ConfigRequest
                            .NAN_BAND_24GHZ];
        }

        NanBandSpecificConfig config5 = new NanBandSpecificConfig();
        config5.rssiClose = 60;
        config5.rssiMiddle = 75;
        config5.rssiCloseProximity = 60;
        config5.dwellTimeMs = (byte) 200;
        config5.scanPeriodSec = 20;
        if (configRequest.mDiscoveryWindowInterval[ConfigRequest.NAN_BAND_5GHZ]
                == ConfigRequest.DW_INTERVAL_NOT_INIT) {
            config5.validDiscoveryWindowIntervalVal = false;
        } else {
            config5.validDiscoveryWindowIntervalVal = true;
            config5.discoveryWindowIntervalVal =
                    (byte) configRequest.mDiscoveryWindowInterval[ConfigRequest
                            .NAN_BAND_5GHZ];
        }

        NanBandSpecificConfig config6 = new NanBandSpecificConfig();
        config6.rssiClose = 60;
        config6.rssiMiddle = 75;
        config6.rssiCloseProximity = 60;
        config6.dwellTimeMs = (byte) 200;
        config6.scanPeriodSec = 20;
        if (configRequest.mDiscoveryWindowInterval[ConfigRequest.NAN_BAND_6GHZ]
                == ConfigRequest.DW_INTERVAL_NOT_INIT) {
            config6.validDiscoveryWindowIntervalVal = false;
        } else {
            config6.validDiscoveryWindowIntervalVal = true;
            config6.discoveryWindowIntervalVal =
                    (byte) configRequest.mDiscoveryWindowInterval[ConfigRequest
                            .NAN_BAND_6GHZ];
        }

        try {
            WifiStatus status;
            if (initialConfiguration) {
                if (iface14 != null || iface15 != null || iface16 != null) {
                    // translate framework to HIDL configuration (V_1.4)
                    android.hardware.wifi.V1_4.NanEnableRequest req =
                            new android.hardware.wifi.V1_4.NanEnableRequest();

                    req.operateInBand[NanBandIndex.NAN_BAND_24GHZ] = true;
                    req.operateInBand[NanBandIndex.NAN_BAND_5GHZ] = configRequest.mSupport5gBand;
                    req.operateInBand[android.hardware.wifi.V1_4.NanBandIndex.NAN_BAND_6GHZ] =
                            configRequest.mSupport6gBand;
                    req.hopCountMax = 2;
                    req.configParams.masterPref = (byte) configRequest.mMasterPreference;
                    req.configParams.disableDiscoveryAddressChangeIndication =
                            !notifyIdentityChange;
                    req.configParams.disableStartedClusterIndication = !notifyIdentityChange;
                    req.configParams.disableJoinedClusterIndication = !notifyIdentityChange;
                    req.configParams.includePublishServiceIdsInBeacon = true;
                    req.configParams.numberOfPublishServiceIdsInBeacon = 0;
                    req.configParams.includeSubscribeServiceIdsInBeacon = true;
                    req.configParams.numberOfSubscribeServiceIdsInBeacon = 0;
                    req.configParams.rssiWindowSize = 8;
                    req.configParams.macAddressRandomizationIntervalSec =
                            macAddressRandomizationIntervalSec;

                    req.configParams.bandSpecificConfig[NanBandIndex.NAN_BAND_24GHZ] = config24;
                    req.configParams.bandSpecificConfig[NanBandIndex.NAN_BAND_5GHZ] = config5;
                    req.configParams.bandSpecificConfig[
                            android.hardware.wifi.V1_4.NanBandIndex.NAN_BAND_6GHZ] = config6;

                    req.debugConfigs.validClusterIdVals = true;
                    req.debugConfigs.clusterIdTopRangeVal = (short) configRequest.mClusterHigh;
                    req.debugConfigs.clusterIdBottomRangeVal = (short) configRequest.mClusterLow;
                    req.debugConfigs.validIntfAddrVal = false;
                    req.debugConfigs.validOuiVal = false;
                    req.debugConfigs.ouiVal = 0;
                    req.debugConfigs.validRandomFactorForceVal = false;
                    req.debugConfigs.randomFactorForceVal = 0;
                    req.debugConfigs.validHopCountForceVal = false;
                    req.debugConfigs.hopCountForceVal = 0;
                    req.debugConfigs.validDiscoveryChannelVal = false;
                    req.debugConfigs.discoveryChannelMhzVal[NanBandIndex.NAN_BAND_24GHZ] = 0;
                    req.debugConfigs.discoveryChannelMhzVal[NanBandIndex.NAN_BAND_5GHZ] = 0;
                    req.debugConfigs.discoveryChannelMhzVal[
                            android.hardware.wifi.V1_4.NanBandIndex.NAN_BAND_6GHZ] = 0;
                    req.debugConfigs.validUseBeaconsInBandVal = false;
                    req.debugConfigs.useBeaconsInBandVal[NanBandIndex.NAN_BAND_24GHZ] = true;
                    req.debugConfigs.useBeaconsInBandVal[NanBandIndex.NAN_BAND_5GHZ] = true;
                    req.debugConfigs.useBeaconsInBandVal[
                            android.hardware.wifi.V1_4.NanBandIndex.NAN_BAND_6GHZ] = true;
                    req.debugConfigs.validUseSdfInBandVal = false;
                    req.debugConfigs.useSdfInBandVal[NanBandIndex.NAN_BAND_24GHZ] = true;
                    req.debugConfigs.useSdfInBandVal[NanBandIndex.NAN_BAND_5GHZ] = true;
                    req.debugConfigs.useSdfInBandVal[
                            android.hardware.wifi.V1_4.NanBandIndex.NAN_BAND_6GHZ] = true;
                    updateConfigForPowerSettings14(req.configParams, configSupplemental12,
                            powerParameters);

                    if (iface16 != null) {
                        status = iface16.enableRequest_1_6(transactionId, req,
                                configSupplemental16);
                    } else if (iface15 != null) {
                        status = iface15.enableRequest_1_5(transactionId, req,
                                configSupplemental15);
                    } else {
                        status = iface14.enableRequest_1_4(transactionId, req,
                                configSupplemental12);
                    }
                } else {
                    // translate framework to HIDL configuration (before V_1.4)
                    NanEnableRequest req = new NanEnableRequest();

                    req.operateInBand[NanBandIndex.NAN_BAND_24GHZ] = true;
                    req.operateInBand[NanBandIndex.NAN_BAND_5GHZ] = configRequest.mSupport5gBand;
                    req.hopCountMax = 2;
                    req.configParams.masterPref = (byte) configRequest.mMasterPreference;
                    req.configParams.disableDiscoveryAddressChangeIndication =
                            !notifyIdentityChange;
                    req.configParams.disableStartedClusterIndication = !notifyIdentityChange;
                    req.configParams.disableJoinedClusterIndication = !notifyIdentityChange;
                    req.configParams.includePublishServiceIdsInBeacon = true;
                    req.configParams.numberOfPublishServiceIdsInBeacon = 0;
                    req.configParams.includeSubscribeServiceIdsInBeacon = true;
                    req.configParams.numberOfSubscribeServiceIdsInBeacon = 0;
                    req.configParams.rssiWindowSize = 8;
                    req.configParams.macAddressRandomizationIntervalSec =
                            macAddressRandomizationIntervalSec;

                    req.configParams.bandSpecificConfig[NanBandIndex.NAN_BAND_24GHZ] = config24;
                    req.configParams.bandSpecificConfig[NanBandIndex.NAN_BAND_5GHZ] = config5;

                    req.debugConfigs.validClusterIdVals = true;
                    req.debugConfigs.clusterIdTopRangeVal = (short) configRequest.mClusterHigh;
                    req.debugConfigs.clusterIdBottomRangeVal = (short) configRequest.mClusterLow;
                    req.debugConfigs.validIntfAddrVal = false;
                    req.debugConfigs.validOuiVal = false;
                    req.debugConfigs.ouiVal = 0;
                    req.debugConfigs.validRandomFactorForceVal = false;
                    req.debugConfigs.randomFactorForceVal = 0;
                    req.debugConfigs.validHopCountForceVal = false;
                    req.debugConfigs.hopCountForceVal = 0;
                    req.debugConfigs.validDiscoveryChannelVal = false;
                    req.debugConfigs.discoveryChannelMhzVal[NanBandIndex.NAN_BAND_24GHZ] = 0;
                    req.debugConfigs.discoveryChannelMhzVal[NanBandIndex.NAN_BAND_5GHZ] = 0;
                    req.debugConfigs.validUseBeaconsInBandVal = false;
                    req.debugConfigs.useBeaconsInBandVal[NanBandIndex.NAN_BAND_24GHZ] = true;
                    req.debugConfigs.useBeaconsInBandVal[NanBandIndex.NAN_BAND_5GHZ] = true;
                    req.debugConfigs.validUseSdfInBandVal = false;
                    req.debugConfigs.useSdfInBandVal[NanBandIndex.NAN_BAND_24GHZ] = true;
                    req.debugConfigs.useSdfInBandVal[NanBandIndex.NAN_BAND_5GHZ] = true;

                    updateConfigForPowerSettings(req.configParams, configSupplemental12,
                            powerParameters);

                    if (iface12 != null) {
                        status = iface12.enableRequest_1_2(transactionId, req,
                                configSupplemental12);
                    } else {
                        status = mIWifiNanIface.enableRequest(transactionId, req);
                    }
                }
            } else {
                if (iface14 != null || iface15 != null || iface16 != null) {
                    android.hardware.wifi.V1_4.NanConfigRequest req =
                            new android.hardware.wifi.V1_4.NanConfigRequest();
                    req.masterPref = (byte) configRequest.mMasterPreference;
                    req.disableDiscoveryAddressChangeIndication = !notifyIdentityChange;
                    req.disableStartedClusterIndication = !notifyIdentityChange;
                    req.disableJoinedClusterIndication = !notifyIdentityChange;
                    req.includePublishServiceIdsInBeacon = true;
                    req.numberOfPublishServiceIdsInBeacon = 0;
                    req.includeSubscribeServiceIdsInBeacon = true;
                    req.numberOfSubscribeServiceIdsInBeacon = 0;
                    req.rssiWindowSize = 8;
                    req.macAddressRandomizationIntervalSec =
                            macAddressRandomizationIntervalSec;

                    req.bandSpecificConfig[NanBandIndex.NAN_BAND_24GHZ] = config24;
                    req.bandSpecificConfig[NanBandIndex.NAN_BAND_5GHZ] = config5;
                    req.bandSpecificConfig[android.hardware.wifi.V1_4.NanBandIndex.NAN_BAND_6GHZ] =
                            config6;

                    updateConfigForPowerSettings14(req, configSupplemental12,
                            powerParameters);
                    if (iface16 != null) {
                        status = iface16.configRequest_1_6(transactionId, req,
                                configSupplemental16);
                    } else if (iface15 != null) {
                        status = iface15.configRequest_1_5(transactionId, req,
                                configSupplemental15);
                    } else {
                        status = iface14.configRequest_1_4(transactionId, req,
                                configSupplemental12);
                    }
                } else {
                    NanConfigRequest req = new NanConfigRequest();
                    req.masterPref = (byte) configRequest.mMasterPreference;
                    req.disableDiscoveryAddressChangeIndication = !notifyIdentityChange;
                    req.disableStartedClusterIndication = !notifyIdentityChange;
                    req.disableJoinedClusterIndication = !notifyIdentityChange;
                    req.includePublishServiceIdsInBeacon = true;
                    req.numberOfPublishServiceIdsInBeacon = 0;
                    req.includeSubscribeServiceIdsInBeacon = true;
                    req.numberOfSubscribeServiceIdsInBeacon = 0;
                    req.rssiWindowSize = 8;
                    req.macAddressRandomizationIntervalSec =
                            macAddressRandomizationIntervalSec;

                    req.bandSpecificConfig[NanBandIndex.NAN_BAND_24GHZ] = config24;
                    req.bandSpecificConfig[NanBandIndex.NAN_BAND_5GHZ] = config5;

                    updateConfigForPowerSettings(req, configSupplemental12, powerParameters);

                    if (iface12 != null) {
                        status = iface12.configRequest_1_2(transactionId, req,
                                configSupplemental12);
                    } else {
                        status = mIWifiNanIface.configRequest(transactionId, req);
                    }
                }
            }
            if (status.code == WifiStatusCode.SUCCESS) {
                return true;
            } else {
                Log.e(TAG, "enableAndConfigure: error: " + statusString(status));
                return false;
            }
        } catch (RemoteException e) {
            Log.e(TAG, "enableAndConfigure: exception: " + e);
            return false;
        }
    }

    /**
     * Disable Aware.
     *
     * @param transactionId Transaction ID for the transaction -
     *            used in the async callback to match with the original request.
     */
    public boolean disable(short transactionId) {
        final String methodStr = "disable";
        if (!checkNanIfaceAndLogFailure(methodStr)) {
            return false;
        }

        try {
            WifiStatus status = mIWifiNanIface.disableRequest(transactionId);
            if (status.code == WifiStatusCode.SUCCESS) {
                return true;
            } else {
                Log.e(TAG, "disable: error: " + statusString(status));
                return false;
            }
        } catch (RemoteException e) {
            Log.e(TAG, "disable: exception: " + e);
            return false;
        }
    }

    /**
     * Start or modify a service publish session.
     *
     * @param transactionId Transaction ID for the transaction -
     *            used in the async callback to match with the original request.
     * @param publishId ID of the requested session - 0 to request a new publish
     *            session.
     * @param publishConfig Configuration of the discovery session.
     */
    public boolean publish(short transactionId, byte publishId, PublishConfig publishConfig) {
        final String methodStr = "publish";
        if (!checkNanIfaceAndLogFailure(methodStr)) {
            return false;
        }

        android.hardware.wifi.V1_6.IWifiNanIface iface16 = mockableCastTo_1_6(mIWifiNanIface);
        if (iface16 == null) {
            NanPublishRequest req = new NanPublishRequest();
            req.baseConfigs.sessionId = publishId;
            req.baseConfigs.ttlSec = (short) publishConfig.mTtlSec;
            req.baseConfigs.discoveryWindowPeriod = 1;
            req.baseConfigs.discoveryCount = 0;
            convertNativeByteArrayToArrayList(publishConfig.mServiceName,
                    req.baseConfigs.serviceName);
            req.baseConfigs.discoveryMatchIndicator = NanMatchAlg.MATCH_NEVER;
            convertNativeByteArrayToArrayList(publishConfig.mServiceSpecificInfo,
                    req.baseConfigs.serviceSpecificInfo);
            convertNativeByteArrayToArrayList(publishConfig.mMatchFilter,
                    publishConfig.mPublishType == PublishConfig.PUBLISH_TYPE_UNSOLICITED
                            ? req.baseConfigs.txMatchFilter : req.baseConfigs.rxMatchFilter);
            req.baseConfigs.useRssiThreshold = false;
            req.baseConfigs.disableDiscoveryTerminationIndication =
                    !publishConfig.mEnableTerminateNotification;
            req.baseConfigs.disableMatchExpirationIndication = true;
            req.baseConfigs.disableFollowupReceivedIndication = false;

            req.autoAcceptDataPathRequests = false;

            req.baseConfigs.rangingRequired = publishConfig.mEnableRanging;

            req.baseConfigs.securityConfig.securityType = NanDataPathSecurityType.OPEN;
            WifiAwareDataPathSecurityConfig securityConfig = publishConfig.getSecurityConfig();
            if (securityConfig != null) {
                req.baseConfigs.securityConfig.cipherType = getHalCipherSuiteType(
                        securityConfig.getCipherSuite());
                if (securityConfig.getPmk() != null && securityConfig.getPmk().length != 0) {
                    req.baseConfigs.securityConfig.securityType = NanDataPathSecurityType.PMK;
                    copyArray(securityConfig.getPmk(), req.baseConfigs.securityConfig.pmk);
                }
                if (securityConfig.getPskPassphrase() != null
                        && securityConfig.getPskPassphrase().length() != 0) {
                    req.baseConfigs.securityConfig.securityType =
                            NanDataPathSecurityType.PASSPHRASE;
                    convertNativeByteArrayToArrayList(securityConfig.getPskPassphrase().getBytes(),
                            req.baseConfigs.securityConfig.passphrase);
                }
            }

            req.publishType = publishConfig.mPublishType;
            req.txType = NanTxType.BROADCAST;

            try {
                WifiStatus status = mIWifiNanIface.startPublishRequest(transactionId, req);
                if (status.code == WifiStatusCode.SUCCESS) {
                    return true;
                } else {
                    Log.e(TAG, "publish: error: " + statusString(status));
                    return false;
                }
            } catch (RemoteException e) {
                Log.e(TAG, "publish: exception: " + e);
                return false;
            }
        } else {
            android.hardware.wifi.V1_6.NanPublishRequest req =
                    new android.hardware.wifi.V1_6.NanPublishRequest();
            req.baseConfigs.sessionId = publishId;
            req.baseConfigs.ttlSec = (short) publishConfig.mTtlSec;
            req.baseConfigs.discoveryWindowPeriod = 1;
            req.baseConfigs.discoveryCount = 0;
            convertNativeByteArrayToArrayList(publishConfig.mServiceName,
                    req.baseConfigs.serviceName);
            req.baseConfigs.discoveryMatchIndicator = NanMatchAlg.MATCH_NEVER;
            convertNativeByteArrayToArrayList(publishConfig.mServiceSpecificInfo,
                    req.baseConfigs.serviceSpecificInfo);
            convertNativeByteArrayToArrayList(publishConfig.mMatchFilter,
                    publishConfig.mPublishType == PublishConfig.PUBLISH_TYPE_UNSOLICITED
                            ? req.baseConfigs.txMatchFilter : req.baseConfigs.rxMatchFilter);
            req.baseConfigs.useRssiThreshold = false;
            req.baseConfigs.disableDiscoveryTerminationIndication =
                    !publishConfig.mEnableTerminateNotification;
            req.baseConfigs.disableMatchExpirationIndication = true;
            req.baseConfigs.disableFollowupReceivedIndication = false;

            req.autoAcceptDataPathRequests = false;

            req.baseConfigs.rangingRequired = publishConfig.mEnableRanging;

            req.baseConfigs.securityConfig.securityType = NanDataPathSecurityType.OPEN;
            WifiAwareDataPathSecurityConfig securityConfig = publishConfig.getSecurityConfig();
            if (securityConfig != null) {
                req.baseConfigs.securityConfig.cipherType = getHalCipherSuiteType(
                        securityConfig.getCipherSuite());
                if (securityConfig.getPmk() != null && securityConfig.getPmk().length != 0) {
                    req.baseConfigs.securityConfig.securityType = NanDataPathSecurityType.PMK;
                    copyArray(securityConfig.getPmk(), req.baseConfigs.securityConfig.pmk);
                }
                if (securityConfig.getPskPassphrase() != null
                        && securityConfig.getPskPassphrase().length() != 0) {
                    req.baseConfigs.securityConfig.securityType =
                            NanDataPathSecurityType.PASSPHRASE;
                    convertNativeByteArrayToArrayList(securityConfig.getPskPassphrase().getBytes(),
                            req.baseConfigs.securityConfig.passphrase);
                }
                if (securityConfig.getPmkId() != null && securityConfig.getPmkId().length != 0) {
                    copyArray(securityConfig.getPmkId(), req.baseConfigs.securityConfig.scid);
                }
            }

            req.publishType = publishConfig.mPublishType;
            req.txType = NanTxType.BROADCAST;

            try {
                WifiStatus status = iface16.startPublishRequest_1_6(transactionId, req);
                if (status.code == WifiStatusCode.SUCCESS) {
                    return true;
                } else {
                    Log.e(TAG, "publish: error: " + statusString(status));
                    return false;
                }
            } catch (RemoteException e) {
                Log.e(TAG, "publish: exception: " + e);
                return false;
            }
        }
    }

    /**
     * Start or modify a service subscription session.
     *
     * @param transactionId Transaction ID for the transaction -
     *            used in the async callback to match with the original request.
     * @param subscribeId ID of the requested session - 0 to request a new
     *            subscribe session.
     * @param subscribeConfig Configuration of the discovery session.
     */
    public boolean subscribe(short transactionId, byte subscribeId,
            SubscribeConfig subscribeConfig) {
        final String methodStr = "subscribe";
        if (!checkNanIfaceAndLogFailure(methodStr)) {
            return false;
        }

        NanSubscribeRequest req = new NanSubscribeRequest();
        req.baseConfigs.sessionId = subscribeId;
        req.baseConfigs.ttlSec = (short) subscribeConfig.mTtlSec;
        req.baseConfigs.discoveryWindowPeriod = 1;
        req.baseConfigs.discoveryCount = 0;
        convertNativeByteArrayToArrayList(subscribeConfig.mServiceName,
                req.baseConfigs.serviceName);
        req.baseConfigs.discoveryMatchIndicator = NanMatchAlg.MATCH_ONCE;
        convertNativeByteArrayToArrayList(subscribeConfig.mServiceSpecificInfo,
                req.baseConfigs.serviceSpecificInfo);
        convertNativeByteArrayToArrayList(subscribeConfig.mMatchFilter,
                subscribeConfig.mSubscribeType == SubscribeConfig.SUBSCRIBE_TYPE_ACTIVE
                        ? req.baseConfigs.txMatchFilter : req.baseConfigs.rxMatchFilter);
        req.baseConfigs.useRssiThreshold = false;
        req.baseConfigs.disableDiscoveryTerminationIndication =
                !subscribeConfig.mEnableTerminateNotification;
        req.baseConfigs.disableMatchExpirationIndication = false;
        req.baseConfigs.disableFollowupReceivedIndication = false;

        req.baseConfigs.rangingRequired =
                subscribeConfig.mMinDistanceMmSet || subscribeConfig.mMaxDistanceMmSet;
        req.baseConfigs.configRangingIndications = 0;
        if (subscribeConfig.mMinDistanceMmSet) {
            req.baseConfigs.distanceEgressCm = (short) Math.min(
                    subscribeConfig.mMinDistanceMm / 10, Short.MAX_VALUE);
            req.baseConfigs.configRangingIndications |=
                    android.hardware.wifi.V1_0.NanRangingIndication.EGRESS_MET_MASK;
        }
        if (subscribeConfig.mMaxDistanceMmSet) {
            req.baseConfigs.distanceIngressCm = (short) Math.min(
                    subscribeConfig.mMaxDistanceMm / 10, Short.MAX_VALUE);
            req.baseConfigs.configRangingIndications |=
                    android.hardware.wifi.V1_0.NanRangingIndication.INGRESS_MET_MASK;
        }

        // TODO: configure security
        req.baseConfigs.securityConfig.securityType = NanDataPathSecurityType.OPEN;

        req.subscribeType = subscribeConfig.mSubscribeType;

        try {
            WifiStatus status = mIWifiNanIface.startSubscribeRequest(transactionId, req);
            if (status.code == WifiStatusCode.SUCCESS) {
                return true;
            } else {
                Log.e(TAG, "subscribe: error: " + statusString(status));
                return false;
            }
        } catch (RemoteException e) {
            Log.e(TAG, "subscribe: exception: " + e);
            return false;
        }
    }

    /**
     * Send a message through an existing discovery session.
     *
     * @param transactionId Transaction ID for the transaction -
     *            used in the async callback to match with the original request.
     * @param pubSubId ID of the existing publish/subscribe session.
     * @param requestorInstanceId ID of the peer to communicate with - obtained
     *            through a previous discovery (match) operation with that peer.
     * @param dest MAC address of the peer to communicate with - obtained
     *            together with requestorInstanceId.
     * @param message Message.
     */
    public boolean sendMessage(short transactionId, byte pubSubId, int requestorInstanceId,
            byte[] dest, byte[] message) {
        final String methodStr = "sendMessage";
        if (!checkNanIfaceAndLogFailure(methodStr)) {
            return false;
        }

        NanTransmitFollowupRequest req = new NanTransmitFollowupRequest();
        req.discoverySessionId = pubSubId;
        req.peerId = requestorInstanceId;
        copyArray(dest, req.addr);
        req.isHighPriority = false;
        req.shouldUseDiscoveryWindow = true;
        convertNativeByteArrayToArrayList(message, req.serviceSpecificInfo);
        req.disableFollowupResultIndication = false;

        try {
            WifiStatus status = mIWifiNanIface.transmitFollowupRequest(transactionId, req);
            if (status.code == WifiStatusCode.SUCCESS) {
                return true;
            } else {
                Log.e(TAG, "sendMessage: error: " + statusString(status));
                return false;
            }
        } catch (RemoteException e) {
            Log.e(TAG, "sendMessage: exception: " + e);
            return false;
        }
    }

    /**
     * Terminate a publish discovery session.
     *
     * @param transactionId Transaction ID for the transaction -
     *            used in the async callback to match with the original request.
     * @param pubSubId ID of the publish/subscribe session - obtained when
     *            creating a session.
     */
    public boolean stopPublish(short transactionId, byte pubSubId) {
        final String methodStr = "stopPublish";
        if (!checkNanIfaceAndLogFailure(methodStr)) {
            return false;
        }

        try {
            WifiStatus status = mIWifiNanIface.stopPublishRequest(transactionId, pubSubId);
            if (status.code == WifiStatusCode.SUCCESS) {
                return true;
            } else {
                Log.e(TAG, "stopPublish: error: " + statusString(status));
                return false;
            }
        } catch (RemoteException e) {
            Log.e(TAG, "stopPublish: exception: " + e);
            return false;
        }
    }

    /**
     * Terminate a subscribe discovery session.
     *
     * @param transactionId transactionId Transaction ID for the transaction -
     *            used in the async callback to match with the original request.
     * @param pubSubId ID of the publish/subscribe session - obtained when
     *            creating a session.
     */
    public boolean stopSubscribe(short transactionId, byte pubSubId) {
        final String methodStr = "stopSubscribe";
        if (!checkNanIfaceAndLogFailure(methodStr)) {
            return false;
        }

        try {
            WifiStatus status = mIWifiNanIface.stopSubscribeRequest(transactionId, pubSubId);
            if (status.code == WifiStatusCode.SUCCESS) {
                return true;
            } else {
                Log.e(TAG, "stopSubscribe: error: " + statusString(status));
                return false;
            }
        } catch (RemoteException e) {
            Log.e(TAG, "stopSubscribe: exception: " + e);
            return false;
        }
    }

    /**
     * Create a Aware network interface. This only creates the Linux interface - it doesn't actually
     * create the data connection.
     *
     * @param transactionId Transaction ID for the transaction - used in the async callback to
     *                      match with the original request.
     * @param interfaceName The name of the interface, e.g. "aware0".
     */
    public boolean createAwareNetworkInterface(short transactionId, String interfaceName) {
        final String methodStr = "createAwareNetworkInterface";
        if (!checkNanIfaceAndLogFailure(methodStr)) {
            return false;
        }

        try {
            WifiStatus status = mIWifiNanIface.createDataInterfaceRequest(
                    transactionId, interfaceName);
            if (status.code == WifiStatusCode.SUCCESS) {
                return true;
            } else {
                Log.e(TAG, "createAwareNetworkInterface: error: " + statusString(status));
                return false;
            }
        } catch (RemoteException e) {
            Log.e(TAG, "createAwareNetworkInterface: exception: " + e);
            return false;
        }
    }

    /**
     * Deletes a Aware network interface. The data connection can (should?) be torn down previously.
     *
     * @param transactionId Transaction ID for the transaction - used in the async callback to
     *                      match with the original request.
     * @param interfaceName The name of the interface, e.g. "aware0".
     */
    public boolean deleteAwareNetworkInterface(short transactionId, String interfaceName) {
        final String methodStr = "deleteAwareNetworkInterface";
        if (!checkNanIfaceAndLogFailure(methodStr)) {
            return false;
        }

        try {
            WifiStatus status = mIWifiNanIface.deleteDataInterfaceRequest(
                    transactionId, interfaceName);
            if (status.code == WifiStatusCode.SUCCESS) {
                return true;
            } else {
                Log.e(TAG, "deleteAwareNetworkInterface: error: " + statusString(status));
                return false;
            }
        } catch (RemoteException e) {
            Log.e(TAG, "deleteAwareNetworkInterface: exception: " + e);
            return false;
        }
    }

    /**
     * Initiates setting up a data-path between device and peer. Security is provided by either
     * PMK or Passphrase (not both) - if both are null then an open (unencrypted) link is set up.
     * @param transactionId      Transaction ID for the transaction - used in the async callback to
     *                           match with the original request.
     * @param peerId             ID of the peer ID to associate the data path with. A value of 0
     *                           indicates that not associated with an existing session.
     * @param channelRequestType Indicates whether the specified channel is available, if available
     *                           requested or forced (resulting in failure if cannot be
     *                           accommodated).
     * @param channel            The channel on which to set up the data-path.
     * @param peer               The MAC address of the peer to create a connection with.
     * @param interfaceName      The interface on which to create the data connection.
     * @param isOutOfBand Is the data-path out-of-band (i.e. without a corresponding Aware discovery
     *                    session).
     * @param appInfo Arbitrary binary blob transmitted to the peer.
     * @param capabilities The capabilities of the firmware.
     * @param securityConfig Security config to encrypt the data-path
     */
    public boolean initiateDataPath(short transactionId, int peerId, int channelRequestType,
            int channel, byte[] peer, String interfaceName,
            boolean isOutOfBand, byte[] appInfo, Capabilities capabilities,
            WifiAwareDataPathSecurityConfig securityConfig) {
        final String methodStr = "initiateDataPath";
        if (!checkNanIfaceAndLogFailure(methodStr)) {
            return false;
        }

        if (capabilities == null) {
            Log.e(TAG, "initiateDataPath: null capabilities");
            return false;
        }

        android.hardware.wifi.V1_6.IWifiNanIface iface16 = mockableCastTo_1_6(mIWifiNanIface);

        if (iface16 == null) {
            NanInitiateDataPathRequest req = new NanInitiateDataPathRequest();
            req.peerId = peerId;
            copyArray(peer, req.peerDiscMacAddr);
            req.channelRequestType = NanDataPathChannelCfg.toHidl(channelRequestType);
            req.channel = channel;
            req.ifaceName = interfaceName;
            req.securityConfig.securityType = NanDataPathSecurityType.OPEN;
            if (securityConfig != null) {
                req.securityConfig.cipherType = getHalCipherSuiteType(
                        securityConfig.getCipherSuite());
                if (securityConfig.getPmk() != null && securityConfig.getPmk().length != 0) {

                    req.securityConfig.securityType = NanDataPathSecurityType.PMK;
                    copyArray(securityConfig.getPmk(), req.securityConfig.pmk);
                }
                if (securityConfig.getPskPassphrase() != null
                        && securityConfig.getPskPassphrase().length() != 0) {
                    req.securityConfig.securityType = NanDataPathSecurityType.PASSPHRASE;
                    convertNativeByteArrayToArrayList(securityConfig.getPskPassphrase().getBytes(),
                            req.securityConfig.passphrase);
                }
            }

            if (req.securityConfig.securityType != NanDataPathSecurityType.OPEN && isOutOfBand) {
                convertNativeByteArrayToArrayList(
                        SERVICE_NAME_FOR_OOB_DATA_PATH.getBytes(StandardCharsets.UTF_8),
                        req.serviceNameOutOfBand);
            }
            convertNativeByteArrayToArrayList(appInfo, req.appInfo);

            try {
                WifiStatus status = mIWifiNanIface.initiateDataPathRequest(transactionId, req);
                if (status.code == WifiStatusCode.SUCCESS) {
                    return true;
                } else {
                    Log.e(TAG, "initiateDataPath: error: " + statusString(status));
                    return false;
                }
            } catch (RemoteException e) {
                Log.e(TAG, "initiateDataPath: exception: " + e);
                return false;
            }
        } else {
            android.hardware.wifi.V1_6.NanInitiateDataPathRequest req =
                    new android.hardware.wifi.V1_6.NanInitiateDataPathRequest();
            req.peerId = peerId;
            copyArray(peer, req.peerDiscMacAddr);
            req.channelRequestType = NanDataPathChannelCfg.toHidl(channelRequestType);
            req.channel = channel;
            req.ifaceName = interfaceName;
            req.securityConfig.securityType = NanDataPathSecurityType.OPEN;
            if (securityConfig != null) {
                req.securityConfig.cipherType = getHalCipherSuiteType(
                        securityConfig.getCipherSuite());
                if (securityConfig.getPmk() != null && securityConfig.getPmk().length != 0) {
                    req.securityConfig.securityType = NanDataPathSecurityType.PMK;
                    copyArray(securityConfig.getPmk(), req.securityConfig.pmk);
                }
                if (securityConfig.getPskPassphrase() != null
                        && securityConfig.getPskPassphrase().length() != 0) {
                    req.securityConfig.securityType = NanDataPathSecurityType.PASSPHRASE;
                    convertNativeByteArrayToArrayList(securityConfig.getPskPassphrase().getBytes(),
                            req.securityConfig.passphrase);
                }
                if (securityConfig.getPmkId() != null && securityConfig.getPmkId().length != 0) {
                    copyArray(securityConfig.getPmkId(), req.securityConfig.scid);
                }
            }

            if (req.securityConfig.securityType != NanDataPathSecurityType.OPEN && isOutOfBand) {
                convertNativeByteArrayToArrayList(
                        SERVICE_NAME_FOR_OOB_DATA_PATH.getBytes(StandardCharsets.UTF_8),
                        req.serviceNameOutOfBand);
            }
            convertNativeByteArrayToArrayList(appInfo, req.appInfo);

            try {
                WifiStatus status = iface16.initiateDataPathRequest_1_6(transactionId, req);
                if (status.code == WifiStatusCode.SUCCESS) {
                    return true;
                } else {
                    Log.e(TAG, "initiateDataPath_1_6: error: " + statusString(status));
                    return false;
                }
            } catch (RemoteException e) {
                Log.e(TAG, "initiateDataPath_1_6: exception: " + e);
                return false;
            }
        }
    }

    /**
     * Responds to a data request from a peer. Security is provided by either PMK or Passphrase (not
     * both) - if both are null then an open (unencrypted) link is set up.
     * @param transactionId Transaction ID for the transaction - used in the async callback to
     *                      match with the original request.
     * @param accept Accept (true) or reject (false) the original call.
     * @param ndpId The NDP (Aware data path) ID. Obtained from the request callback.
     * @param interfaceName The interface on which the data path will be setup. Obtained from the
     *                      request callback.
     * @param appInfo Arbitrary binary blob transmitted to the peer.
     * @param isOutOfBand Is the data-path out-of-band (i.e. without a corresponding Aware discovery
     *                    session).
     * @param capabilities The capabilities of the firmware.
     * @param securityConfig Security config to encrypt the data-path
     */
    public boolean respondToDataPathRequest(short transactionId, boolean accept, int ndpId,
            String interfaceName, byte[] appInfo,
            boolean isOutOfBand, Capabilities capabilities,
            WifiAwareDataPathSecurityConfig securityConfig) {
        final String methodStr = "respondToDataPathRequest";
        if (!checkNanIfaceAndLogFailure(methodStr)) {
            return false;
        }

        if (capabilities == null) {
            Log.e(TAG, "respondToDataPathRequest: null capabilities");
            return false;
        }

        android.hardware.wifi.V1_6.IWifiNanIface iface16 = mockableCastTo_1_6(mIWifiNanIface);

        if (iface16 == null) {
            NanRespondToDataPathIndicationRequest req = new NanRespondToDataPathIndicationRequest();
            req.acceptRequest = accept;
            req.ndpInstanceId = ndpId;
            req.ifaceName = interfaceName;
            req.securityConfig.securityType = NanDataPathSecurityType.OPEN;
            if (securityConfig != null) {
                req.securityConfig.cipherType = getHalCipherSuiteType(
                        securityConfig.getCipherSuite());
                if (securityConfig.getPmk() != null && securityConfig.getPmk().length != 0) {

                    req.securityConfig.securityType = NanDataPathSecurityType.PMK;
                    copyArray(securityConfig.getPmk(), req.securityConfig.pmk);
                }
                if (securityConfig.getPskPassphrase() != null
                        && securityConfig.getPskPassphrase().length() != 0) {
                    req.securityConfig.securityType = NanDataPathSecurityType.PASSPHRASE;
                    convertNativeByteArrayToArrayList(securityConfig.getPskPassphrase().getBytes(),
                            req.securityConfig.passphrase);
                }
            }

            if (req.securityConfig.securityType != NanDataPathSecurityType.OPEN && isOutOfBand) {
                convertNativeByteArrayToArrayList(
                        SERVICE_NAME_FOR_OOB_DATA_PATH.getBytes(StandardCharsets.UTF_8),
                        req.serviceNameOutOfBand);
            }
            convertNativeByteArrayToArrayList(appInfo, req.appInfo);

            try {
                WifiStatus status = mIWifiNanIface.respondToDataPathIndicationRequest(
                        transactionId, req);
                if (status.code == WifiStatusCode.SUCCESS) {
                    return true;
                } else {
                    Log.e(TAG, "respondToDataPathRequest: error: " + statusString(status));
                    return false;
                }
            } catch (RemoteException e) {
                Log.e(TAG, "respondToDataPathRequest: exception: " + e);
                return false;
            }
        } else {
            android.hardware.wifi.V1_6.NanRespondToDataPathIndicationRequest req =
                    new android.hardware.wifi.V1_6.NanRespondToDataPathIndicationRequest();
            req.acceptRequest = accept;
            req.ndpInstanceId = ndpId;
            req.ifaceName = interfaceName;
            req.securityConfig.securityType = NanDataPathSecurityType.OPEN;
            if (securityConfig != null) {
                req.securityConfig.cipherType = getHalCipherSuiteType(
                        securityConfig.getCipherSuite());
                if (securityConfig.getPmk() != null && securityConfig.getPmk().length != 0) {

                    req.securityConfig.securityType = NanDataPathSecurityType.PMK;
                    copyArray(securityConfig.getPmk(), req.securityConfig.pmk);
                }
                if (securityConfig.getPskPassphrase() != null
                        && securityConfig.getPskPassphrase().length() != 0) {
                    req.securityConfig.securityType = NanDataPathSecurityType.PASSPHRASE;
                    convertNativeByteArrayToArrayList(securityConfig.getPskPassphrase().getBytes(),
                            req.securityConfig.passphrase);
                }
                if (securityConfig.getPmkId() != null && securityConfig.getPmkId().length != 0) {
                    copyArray(securityConfig.getPmkId(), req.securityConfig.scid);
                }
            }

            if (req.securityConfig.securityType != NanDataPathSecurityType.OPEN && isOutOfBand) {
                convertNativeByteArrayToArrayList(
                        SERVICE_NAME_FOR_OOB_DATA_PATH.getBytes(StandardCharsets.UTF_8),
                        req.serviceNameOutOfBand);
            }
            convertNativeByteArrayToArrayList(appInfo, req.appInfo);

            try {
                WifiStatus status = iface16
                        .respondToDataPathIndicationRequest_1_6(transactionId, req);
                if (status.code == WifiStatusCode.SUCCESS) {
                    return true;
                } else {
                    Log.e(TAG, "respondToDataPathRequest_1_6: error: " + statusString(status));
                    return false;
                }
            } catch (RemoteException e) {
                Log.e(TAG, "respondToDataPathRequest_1_6: exception: " + e);
                return false;
            }
        }
    }

    /**
     * Terminate an existing data-path (does not delete the interface).
     *
     * @param transactionId Transaction ID for the transaction - used in the async callback to
     *                      match with the original request.
     * @param ndpId The NDP (Aware data path) ID to be terminated.
     */
    public boolean endDataPath(short transactionId, int ndpId) {
        final String methodStr = "endDataPath";
        if (!checkNanIfaceAndLogFailure(methodStr)) {
            return false;
        }

        try {
            WifiStatus status = mIWifiNanIface.terminateDataPathRequest(transactionId, ndpId);
            if (status.code == WifiStatusCode.SUCCESS) {
                return true;
            } else {
                Log.e(TAG, "endDataPath: error: " + statusString(status));
                return false;
            }
        } catch (RemoteException e) {
            Log.e(TAG, "endDataPath: exception: " + e);
            return false;
        }
    }

    /**
     * Framework callback object. Will get called when the equivalent events are received
     * from the HAL.
     */
    public interface Callback {
        /**
         * Invoked in response to a capability request.
         * @param id ID corresponding to the original request.
         * @param capabilities Capability data.
         */
        void notifyCapabilitiesResponse(short id, Capabilities capabilities);

        /**
         * Invoked in response to an enable request.
         * @param id ID corresponding to the original request.
         * @param status Status the operation (see {@link NanStatusCode}).
         */
        void notifyEnableResponse(short id, int status);

        /**
         * Invoked in response to a config request.
         * @param id ID corresponding to the original request.
         * @param status Status the operation (see {@link NanStatusCode}).
         */
        void notifyConfigResponse(short id, int status);

        /**
         * Invoked in response to a disable request.
         * @param id ID corresponding to the original request.
         * @param status Status the operation (see {@link NanStatusCode}).
         */
        void notifyDisableResponse(short id, int status);

        /**
         * Invoked to notify the status of the start publish request.
         * @param id ID corresponding to the original request.
         * @param status Status the operation (see {@link NanStatusCode}).
         * @param publishId
         */
        void notifyStartPublishResponse(short id, int status, byte publishId);

        /**
         * Invoked to notify the status of the start subscribe request.
         * @param id ID corresponding to the original request.
         * @param status Status the operation (see {@link NanStatusCode}).
         * @param subscribeId ID of the new subscribe session (if successfully created).
         */
        void notifyStartSubscribeResponse(short id, int status, byte subscribeId);

        /**
         * Invoked in response to a transmit followup request.
         * @param id ID corresponding to the original request.
         * @param status Status the operation (see {@link NanStatusCode}).
         */
        void notifyTransmitFollowupResponse(short id, int status);

        /**
         * Invoked in response to a create data interface request.
         * @param id ID corresponding to the original request.
         * @param status Status the operation (see {@link NanStatusCode}).
         */
        void notifyCreateDataInterfaceResponse(short id, int status);

        /**
         * Invoked in response to a delete data interface request.
         * @param id ID corresponding to the original request.
         * @param status Status the operation (see {@link NanStatusCode}).
         */
        void notifyDeleteDataInterfaceResponse(short id, int status);

        /**
         * Invoked in response to a delete data interface request.
         * @param id ID corresponding to the original request.
         * @param status Status the operation (see {@link NanStatusCode}).
         * @param ndpInstanceId
         */
        void notifyInitiateDataPathResponse(short id, int status, int ndpInstanceId);

        /**
         * Invoked in response to a respond to data path indication request.
         * @param id ID corresponding to the original request.
         * @param status Status the operation (see {@link NanStatusCode}).
         */
        void notifyRespondToDataPathIndicationResponse(short id, int status);

        /**
         * Invoked in response to a terminate data path request.
         * @param id ID corresponding to the original request.
         * @param status Status the operation (see {@link NanStatusCode}).
         */
        void notifyTerminateDataPathResponse(short id, int status);

        /**
         * Indicates that a cluster event has been received.
         * @param eventType Type of the cluster event (see {@link NanClusterEventType}).
         * @param addr MAC Address associated with the corresponding event.
         */
        void eventClusterEvent(int eventType, byte[] addr);

        /**
         * Indicates that a NAN has been disabled.
         * @param status Status the operation (see {@link NanStatusCode}).
         */
        void eventDisabled(int status);

        /**
         * Indicates that an active publish session has terminated.
         * @param sessionId Discovery session ID of the terminated session.
         * @param status Status the operation (see {@link NanStatusCode}).
         */
        void eventPublishTerminated(byte sessionId, int status);

        /**
         * Indicates that an active subscribe session has terminated.
         * @param sessionId Discovery session ID of the terminated session.
         * @param status Status the operation (see {@link NanStatusCode}).
         */
        void eventSubscribeTerminated(byte sessionId, int status);

        /**
         * Indicates that a match has occurred - i.e. a service has been discovered.
         * @param discoverySessionId Publish or subscribe discovery session ID of an existing
         *                           discovery session.
         * @param peerId Unique ID of the peer. Can be used subsequently in sendMessage()
         *               or to set up a data-path.
         * @param addr NAN Discovery (management) MAC address of the peer.
         * @param serviceSpecificInfo The arbitrary information contained in the
         *                            |NanDiscoveryCommonConfig.serviceSpecificInfo| of
         *                            the peer's discovery session configuration.
         * @param matchFilter The match filter from the discovery packet (publish or subscribe)
         *                    which caused service discovery. Matches the
         *                    |NanDiscoveryCommonConfig.txMatchFilter| of the peer's unsolicited
         *                    publish message or of the local device's Active subscribe message.
         * @param rangingIndicationType The ranging event(s) which triggered the ranging. Ex. can
         *                              indicate that continuous ranging was requested, or else that
         *                              an ingress event occurred. See {@link NanRangingIndication}.
         * @param rangingMeasurementInMm If ranging was required and executed, contains the
         *                               distance to the peer in mm.
         * @param scid Security Context Identifier identifies the Security Context.
         *             For NAN Shared Key Cipher Suite, this field contains the 16 octet PMKID
         *             identifying the PMK used for setting up the Secure Data Path.
         * @param peerCipherType Cipher type for data-paths constructed in the context of this
         *                       discovery session.
         */
        void eventMatch(byte discoverySessionId, int peerId, byte[] addr,
                byte[] serviceSpecificInfo, byte[] matchFilter, int rangingIndicationType,
                int rangingMeasurementInMm, byte[] scid, int peerCipherType);

        /**
         * Indicates that a previously discovered match (service) has expired.
         * @param discoverySessionId Discovery session ID of the expired match.
         * @param peerId Peer ID of the expired match.
         */
        void eventMatchExpired(byte discoverySessionId, int peerId);

        /**
         * Indicates that a followup message has been received from a peer.
         * @param discoverySessionId Discovery session (publish or subscribe) ID of a previously
         *                           created discovery session.
         * @param peerId Unique ID of the peer.
         * @param addr NAN Discovery (management) MAC address of the peer.
         * @param serviceSpecificInfo Received message from the peer. There is no semantic
         *                            meaning to these bytes. They are passed-through from sender
         *                            to receiver as-is with no parsing.
         */
        void eventFollowupReceived(byte discoverySessionId, int peerId, byte[] addr,
                byte[] serviceSpecificInfo);

        /**
         * Provides status of a completed followup message transmit operation.
         * @param id ID corresponding to the original request.
         * @param status Status the operation (see {@link NanStatusCode}).
         */
        void eventTransmitFollowup(short id, int status);

        /**
         * Indicates that a data-path (NDP) setup has been requested by an initiator peer
         * (received by the intended responder).
         * @param discoverySessionId ID of an active publish or subscribe discovery session.
         * @param peerDiscMacAddr MAC address of the Initiator peer. This is the MAC address of
         *                        the peer's management/discovery NAN interface.
         * @param ndpInstanceId ID of the data-path. Used to identify the data-path in further
         *                      negotiation/APIs.
         * @param appInfo Arbitrary information communicated from the peer as part of the
         *                data-path setup process. There is no semantic meaning to these bytes.
         *                They are passed from sender to receiver as-is with no parsing.
         */
        void eventDataPathRequest(byte discoverySessionId, byte[] peerDiscMacAddr,
                int ndpInstanceId, byte[] appInfo);

        /**
         * Indicates that a data-path (NDP) setup has been completed. Received by both the
         * Initiator and Responder.
         * @param status Status the operation (see {@link NanStatusCode}).
         * @param ndpInstanceId ID of the data-path.
         * @param dataPathSetupSuccess Indicates whether the data-path setup succeeded (true)
         *                             or failed (false).
         * @param peerNdiMacAddr MAC address of the peer's data-interface (not its
         *                       management/discovery interface).
         * @param appInfo Arbitrary information communicated from the peer as part of the
         *                data-path setup process. There is no semantic meaning to  these bytes.
         *                They are passed from sender to receiver as-is with no parsing.
         * @param channelInfos
         */
        void eventDataPathConfirm(int status, int ndpInstanceId, boolean dataPathSetupSuccess,
                byte[] peerNdiMacAddr, byte[] appInfo, List<WifiAwareChannelInfo> channelInfos);

        /**
         * Indicates that a data-path (NDP) schedule has been updated (ex. channels
         * have been changed).
         * @param peerDiscoveryAddress Discovery address (NMI) of the peer to which the NDP
         *                             is connected.
         * @param ndpInstanceIds List of NDPs to which this update applies.
         * @param channelInfo Updated channel(s) information.
         */
        void eventDataPathScheduleUpdate(byte[] peerDiscoveryAddress,
                ArrayList<Integer> ndpInstanceIds, List<WifiAwareChannelInfo> channelInfo);

        /**
         * Indicates that a list of data-paths (NDP) have been terminated. Received by both the
         * Initiator and Responder.
         * @param ndpInstanceId Data-path ID of the terminated data-path.
         */
        void eventDataPathTerminated(int ndpInstanceId);
    }

    /**
     * Update the NAN configuration to reflect the current power settings (before V1.4)
     */
    private void updateConfigForPowerSettings(NanConfigRequest req,
            android.hardware.wifi.V1_2.NanConfigRequestSupplemental configSupplemental12,
            PowerParameters powerParameters) {
        updateSingleConfigForPowerSettings(req.bandSpecificConfig[NanBandIndex.NAN_BAND_5GHZ],
                powerParameters.discoveryWindow5Ghz);
        updateSingleConfigForPowerSettings(req.bandSpecificConfig[NanBandIndex.NAN_BAND_24GHZ],
                powerParameters.discoveryWindow24Ghz);

        configSupplemental12.discoveryBeaconIntervalMs = powerParameters.discoveryBeaconIntervalMs;
        configSupplemental12.numberOfSpatialStreamsInDiscovery =
                powerParameters.numberOfSpatialStreamsInDiscovery;
        configSupplemental12.enableDiscoveryWindowEarlyTermination =
                powerParameters.enableDiscoveryWindowEarlyTermination;
    }

    /**
     * Update the NAN configuration to reflect the current power settings (V1.4)
     */
    private void updateConfigForPowerSettings14(android.hardware.wifi.V1_4.NanConfigRequest req,
            android.hardware.wifi.V1_2.NanConfigRequestSupplemental configSupplemental12,
            PowerParameters powerParameters) {
        updateSingleConfigForPowerSettings(req.bandSpecificConfig[NanBandIndex.NAN_BAND_5GHZ],
                powerParameters.discoveryWindow5Ghz);
        updateSingleConfigForPowerSettings(req.bandSpecificConfig[NanBandIndex.NAN_BAND_24GHZ],
                powerParameters.discoveryWindow24Ghz);
        updateSingleConfigForPowerSettings(req.bandSpecificConfig[
                        android.hardware.wifi.V1_4.NanBandIndex.NAN_BAND_6GHZ],
                powerParameters.discoveryWindow6Ghz);

        configSupplemental12.discoveryBeaconIntervalMs = powerParameters.discoveryBeaconIntervalMs;
        configSupplemental12.numberOfSpatialStreamsInDiscovery =
                powerParameters.numberOfSpatialStreamsInDiscovery;
        configSupplemental12.enableDiscoveryWindowEarlyTermination =
                powerParameters.enableDiscoveryWindowEarlyTermination;
    }

    private void updateSingleConfigForPowerSettings(NanBandSpecificConfig cfg, int override) {
        if (override != -1) {
            cfg.validDiscoveryWindowIntervalVal = true;
            cfg.discoveryWindowIntervalVal = (byte) override;
        }
    }

    /**
     * Returns the HAL cipher suite.
     */
    private int getHalCipherSuiteType(int frameworkCipherSuites) {
        switch (frameworkCipherSuites) {
            case WIFI_AWARE_CIPHER_SUITE_NCS_SK_128:
                return NanCipherSuiteType.SHARED_KEY_128_MASK;
            case WIFI_AWARE_CIPHER_SUITE_NCS_SK_256:
                return NanCipherSuiteType.SHARED_KEY_256_MASK;
            case WIFI_AWARE_CIPHER_SUITE_NCS_PK_128:
                return NanCipherSuiteType.PUBLIC_KEY_128_MASK;
            case WIFI_AWARE_CIPHER_SUITE_NCS_PK_256:
                return NanCipherSuiteType.PUBLIC_KEY_256_MASK;
        }
        return NanCipherSuiteType.NONE;
    }

    /**
     * Converts a byte[] to an ArrayList<Byte>. Fills in the entries of the 'to' array if
     * provided (non-null), otherwise creates and returns a new ArrayList<>.
     *
     * @param from The input byte[] to convert from.
     * @param to An optional ArrayList<> to fill in from 'from'.
     *
     * @return A newly allocated ArrayList<> if 'to' is null, otherwise null.
     */
    private ArrayList<Byte> convertNativeByteArrayToArrayList(byte[] from, ArrayList<Byte> to) {
        if (from == null) {
            from = new byte[0];
        }

        if (to == null) {
            to = new ArrayList<>(from.length);
        } else {
            to.ensureCapacity(from.length);
        }
        for (int i = 0; i < from.length; ++i) {
            to.add(from[i]);
        }
        return to;
    }

    private void copyArray(byte[] from, byte[] to) {
        if (from == null || to == null || from.length != to.length) {
            Log.e(TAG, "copyArray error: from=" + Arrays.toString(from) + ", to="
                    + Arrays.toString(to));
            return;
        }
        for (int i = 0; i < from.length; ++i) {
            to[i] = from[i];
        }
    }

    private static String statusString(WifiStatus status) {
        if (status == null) {
            return "status=null";
        }
        StringBuilder sb = new StringBuilder();
        sb.append(status.code).append(" (").append(status.description).append(")");
        return sb.toString();
    }

    private boolean checkNanIfaceAndLogFailure(String methodStr) {
        if (mIWifiNanIface == null) {
            Log.e(TAG, "Can't call " + methodStr + ", mIWifiNanIface is null");
            return false;
        }
        return true;
    }
}
