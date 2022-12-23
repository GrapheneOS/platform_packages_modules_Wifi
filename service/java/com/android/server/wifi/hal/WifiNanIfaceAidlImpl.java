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

package com.android.server.wifi.hal;

import static android.net.wifi.aware.Characteristics.WIFI_AWARE_CIPHER_SUITE_NCS_PK_128;
import static android.net.wifi.aware.Characteristics.WIFI_AWARE_CIPHER_SUITE_NCS_PK_256;
import static android.net.wifi.aware.Characteristics.WIFI_AWARE_CIPHER_SUITE_NCS_SK_128;
import static android.net.wifi.aware.Characteristics.WIFI_AWARE_CIPHER_SUITE_NCS_SK_256;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.hardware.wifi.NanBandIndex;
import android.hardware.wifi.NanBandSpecificConfig;
import android.hardware.wifi.NanCipherSuiteType;
import android.hardware.wifi.NanConfigRequest;
import android.hardware.wifi.NanConfigRequestSupplemental;
import android.hardware.wifi.NanDataPathSecurityConfig;
import android.hardware.wifi.NanDataPathSecurityType;
import android.hardware.wifi.NanDebugConfig;
import android.hardware.wifi.NanDiscoveryCommonConfig;
import android.hardware.wifi.NanEnableRequest;
import android.hardware.wifi.NanInitiateDataPathRequest;
import android.hardware.wifi.NanMatchAlg;
import android.hardware.wifi.NanPublishRequest;
import android.hardware.wifi.NanRangingIndication;
import android.hardware.wifi.NanRespondToDataPathIndicationRequest;
import android.hardware.wifi.NanSubscribeRequest;
import android.hardware.wifi.NanTransmitFollowupRequest;
import android.hardware.wifi.NanTxType;
import android.net.MacAddress;
import android.net.wifi.aware.ConfigRequest;
import android.net.wifi.aware.PublishConfig;
import android.net.wifi.aware.SubscribeConfig;
import android.net.wifi.aware.WifiAwareDataPathSecurityConfig;
import android.os.RemoteException;
import android.os.ServiceSpecificException;
import android.util.Log;

import com.android.server.wifi.aware.Capabilities;

import java.nio.charset.StandardCharsets;

/**
 * AIDL implementation of the IWifiNanIface interface.
 */
public class WifiNanIfaceAidlImpl implements IWifiNanIface {
    private static final String TAG = "WifiNanIfaceAidlImpl";
    private android.hardware.wifi.IWifiNanIface mWifiNanIface;
    private String mIfaceName;
    private final Object mLock = new Object();
    private WifiNanIfaceCallbackAidlImpl mHalCallback;
    private WifiNanIface.Callback mFrameworkCallback;

    public WifiNanIfaceAidlImpl(@NonNull android.hardware.wifi.IWifiNanIface nanIface) {
        mWifiNanIface = nanIface;
        mHalCallback = new WifiNanIfaceCallbackAidlImpl(this);
    }

    /**
     * Enable verbose logging.
     */
    @Override
    public void enableVerboseLogging(boolean verbose) {
        synchronized (mLock) {
            if (mHalCallback != null) {
                mHalCallback.enableVerboseLogging(verbose);
            }
        }
    }

    protected WifiNanIface.Callback getFrameworkCallback() {
        return mFrameworkCallback;
    }

    /**
     * See comments for {@link IWifiNanIface#getNanIface()}
     *
     * TODO: Remove this API. Will only be used temporarily until HalDeviceManager is refactored.
     */
    @Override
    public android.hardware.wifi.V1_0.IWifiNanIface getNanIface() {
        return null;
    }

    /**
     * See comments for {@link IWifiNanIface#registerFrameworkCallback(WifiNanIface.Callback)}
     */
    @Override
    public boolean registerFrameworkCallback(WifiNanIface.Callback callback) {
        final String methodStr = "registerFrameworkCallback";
        synchronized (mLock) {
            if (!checkIfaceAndLogFailure(methodStr)) return false;
            if (mFrameworkCallback != null) {
                Log.e(TAG, "Framework callback is already registered");
                return false;
            } else if (callback == null) {
                Log.e(TAG, "Cannot register a null framework callback");
                return false;
            }

            try {
                mWifiNanIface.registerEventCallback(mHalCallback);
                mFrameworkCallback = callback;
                return true;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return false;
        }
    }

    /**
     * See comments for {@link IWifiNanIface#getName()}
     */
    @Override
    @Nullable
    public String getName() {
        final String methodStr = "getName";
        synchronized (mLock) {
            if (!checkIfaceAndLogFailure(methodStr)) return null;
            if (mIfaceName != null) return mIfaceName;
            try {
                String ifaceName = mWifiNanIface.getName();
                mIfaceName = ifaceName;
                return mIfaceName;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return null;
        }
    }

    /**
     * See comments for {@link IWifiNanIface#getCapabilities(short)}
     */
    @Override
    public boolean getCapabilities(short transactionId) {
        final String methodStr = "getCapabilities";
        synchronized (mLock) {
            try {
                if (!checkIfaceAndLogFailure(methodStr)) return false;
                mWifiNanIface.getCapabilitiesRequest((char) transactionId);
                return true;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return false;
        }
    }

    /**
     * See comments for {@link IWifiNanIface#enableAndConfigure(short, ConfigRequest, boolean,
     *                         boolean, boolean, boolean, int, int, WifiNanIface.PowerParameters)}
     */
    @Override
    public boolean enableAndConfigure(short transactionId, ConfigRequest configRequest,
            boolean notifyIdentityChange, boolean initialConfiguration, boolean rangingEnabled,
            boolean isInstantCommunicationEnabled, int instantModeChannel,
            int macAddressRandomizationIntervalSec, WifiNanIface.PowerParameters powerParameters) {
        final String methodStr = "enableAndConfigure";
        try {
            if (!checkIfaceAndLogFailure(methodStr)) return false;
            NanConfigRequestSupplemental supplemental = createNanConfigRequestSupplemental(
                    rangingEnabled, isInstantCommunicationEnabled, instantModeChannel);
            if (initialConfiguration) {
                NanEnableRequest req = createNanEnableRequest(
                        configRequest, notifyIdentityChange, supplemental,
                        macAddressRandomizationIntervalSec, powerParameters);
                mWifiNanIface.enableRequest((char) transactionId, req, supplemental);
            } else {
                NanConfigRequest req = createNanConfigRequest(
                        configRequest, notifyIdentityChange, supplemental,
                        macAddressRandomizationIntervalSec, powerParameters);
                mWifiNanIface.configRequest((char) transactionId, req, supplemental);
            }
            return true;
        } catch (RemoteException e) {
            handleRemoteException(e, methodStr);
        } catch (ServiceSpecificException e) {
            handleServiceSpecificException(e, methodStr);
        }
        return false;
    }

    /**
     * See comments for {@link IWifiNanIface#disable(short)}
     */
    @Override
    public boolean disable(short transactionId) {
        final String methodStr = "disable";
        synchronized (mLock) {
            try {
                if (!checkIfaceAndLogFailure(methodStr)) return false;
                mWifiNanIface.disableRequest((char) transactionId);
                return true;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return false;
        }
    }

    /**
     * See comments for {@link IWifiNanIface#publish(short, byte, PublishConfig)}
     */
    @Override
    public boolean publish(short transactionId, byte publishId, PublishConfig publishConfig) {
        final String methodStr = "publish";
        synchronized (mLock) {
            try {
                if (!checkIfaceAndLogFailure(methodStr)) return false;
                NanPublishRequest req = createNanPublishRequest(publishId, publishConfig);
                mWifiNanIface.startPublishRequest((char) transactionId, req);
                return true;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return false;
        }
    }

    /**
     * See comments for {@link IWifiNanIface#subscribe(short, byte, SubscribeConfig)}
     */
    @Override
    public boolean subscribe(short transactionId, byte subscribeId,
            SubscribeConfig subscribeConfig) {
        final String methodStr = "subscribe";
        synchronized (mLock) {
            try {
                if (!checkIfaceAndLogFailure(methodStr)) return false;
                NanSubscribeRequest req = createNanSubscribeRequest(subscribeId, subscribeConfig);
                mWifiNanIface.startSubscribeRequest((char) transactionId, req);
                return true;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return false;
        }
    }

    /**
     * See comments for {@link IWifiNanIface#sendMessage(short, byte, int, MacAddress, byte[])}
     */
    @Override
    public boolean sendMessage(short transactionId, byte pubSubId, int requesterInstanceId,
            MacAddress dest, byte[] message) {
        final String methodStr = "sendMessage";
        synchronized (mLock) {
            try {
                if (!checkIfaceAndLogFailure(methodStr)) return false;
                NanTransmitFollowupRequest req = createNanTransmitFollowupRequest(
                        pubSubId, requesterInstanceId, dest, message);
                mWifiNanIface.transmitFollowupRequest((char) transactionId, req);
                return true;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return false;
        }
    }

    /**
     * See comments for {@link IWifiNanIface#stopPublish(short, byte)}
     */
    @Override
    public boolean stopPublish(short transactionId, byte pubSubId) {
        final String methodStr = "stopPublish";
        synchronized (mLock) {
            try {
                if (!checkIfaceAndLogFailure(methodStr)) return false;
                mWifiNanIface.stopPublishRequest((char) transactionId, pubSubId);
                return true;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return false;
        }
    }

    /**
     * See comments for {@link IWifiNanIface#stopSubscribe(short, byte)}
     */
    @Override
    public boolean stopSubscribe(short transactionId, byte pubSubId) {
        final String methodStr = "stopSubscribe";
        synchronized (mLock) {
            try {
                if (!checkIfaceAndLogFailure(methodStr)) return false;
                mWifiNanIface.stopSubscribeRequest((char) transactionId, pubSubId);
                return true;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return false;
        }
    }

    /**
     * See comments for {@link IWifiNanIface#createAwareNetworkInterface(short, String)}
     */
    @Override
    public boolean createAwareNetworkInterface(short transactionId, String interfaceName) {
        final String methodStr = "createAwareNetworkInterface";
        synchronized (mLock) {
            try {
                if (!checkIfaceAndLogFailure(methodStr)) return false;
                mWifiNanIface.createDataInterfaceRequest((char) transactionId, interfaceName);
                return true;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return false;
        }
    }

    /**
     * See comments for {@link IWifiNanIface#deleteAwareNetworkInterface(short, String)}
     */
    @Override
    public boolean deleteAwareNetworkInterface(short transactionId, String interfaceName) {
        final String methodStr = "deleteAwareNetworkInterface";
        synchronized (mLock) {
            try {
                if (!checkIfaceAndLogFailure(methodStr)) return false;
                mWifiNanIface.deleteDataInterfaceRequest((char) transactionId, interfaceName);
                return true;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return false;
        }
    }

    /**
     * See comments for {@link IWifiNanIface#initiateDataPath(short, int, int, int, MacAddress,
     *                         String, boolean, byte[], Capabilities,
     *                         WifiAwareDataPathSecurityConfig)}
     */
    @Override
    public boolean initiateDataPath(short transactionId, int peerId, int channelRequestType,
            int channel, MacAddress peer, String interfaceName,
            boolean isOutOfBand, byte[] appInfo, Capabilities capabilities,
            WifiAwareDataPathSecurityConfig securityConfig) {
        final String methodStr = "initiateDataPath";
        synchronized (mLock) {
            try {
                if (!checkIfaceAndLogFailure(methodStr)) return false;
                NanInitiateDataPathRequest req = createNanInitiateDataPathRequest(
                        peerId, channelRequestType, channel, peer, interfaceName, isOutOfBand,
                        appInfo, securityConfig);
                mWifiNanIface.initiateDataPathRequest((char) transactionId, req);
                return true;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return false;
        }
    }

    /**
     * See comments for {@link IWifiNanIface#respondToDataPathRequest(short, boolean, int, String,
     *                         byte[], boolean, Capabilities, WifiAwareDataPathSecurityConfig)}
     */
    @Override
    public boolean respondToDataPathRequest(short transactionId, boolean accept, int ndpId,
            String interfaceName, byte[] appInfo, boolean isOutOfBand, Capabilities capabilities,
            WifiAwareDataPathSecurityConfig securityConfig) {
        final String methodStr = "respondToDataPathRequest";
        synchronized (mLock) {
            try {
                if (!checkIfaceAndLogFailure(methodStr)) return false;
                NanRespondToDataPathIndicationRequest req =
                        createNanRespondToDataPathIndicationRequest(
                                accept, ndpId, interfaceName, appInfo, isOutOfBand,
                                securityConfig);
                mWifiNanIface.respondToDataPathIndicationRequest((char) transactionId, req);
                return true;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return false;
        }
    }

    /**
     * See comments for {@link IWifiNanIface#endDataPath(short, int)}
     */
    @Override
    public boolean endDataPath(short transactionId, int ndpId) {
        final String methodStr = "endDataPath";
        synchronized (mLock) {
            try {
                if (!checkIfaceAndLogFailure(methodStr)) return false;
                mWifiNanIface.terminateDataPathRequest((char) transactionId, ndpId);
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return false;
        }
    }


    // Utilities

    private static NanConfigRequestSupplemental createNanConfigRequestSupplemental(
            boolean rangingEnabled, boolean isInstantCommunicationEnabled, int instantModeChannel) {
        NanConfigRequestSupplemental out = new NanConfigRequestSupplemental();
        out.discoveryBeaconIntervalMs = 0;
        out.numberOfSpatialStreamsInDiscovery = 0;
        out.enableDiscoveryWindowEarlyTermination = false;
        out.enableRanging = rangingEnabled;
        out.enableInstantCommunicationMode = isInstantCommunicationEnabled;
        out.instantModeChannel = instantModeChannel;
        return out;
    }

    private static NanBandSpecificConfig[] createNanBandSpecificConfigs(
            ConfigRequest configRequest) {
        NanBandSpecificConfig config24 = new NanBandSpecificConfig();
        config24.rssiClose = 60;
        config24.rssiMiddle = 70;
        config24.rssiCloseProximity = 60;
        config24.dwellTimeMs = 200;
        config24.scanPeriodSec = 20;
        if (configRequest.mDiscoveryWindowInterval[ConfigRequest.NAN_BAND_24GHZ]
                == ConfigRequest.DW_INTERVAL_NOT_INIT) {
            config24.validDiscoveryWindowIntervalVal = false;
        } else {
            config24.validDiscoveryWindowIntervalVal = true;
            config24.discoveryWindowIntervalVal =
                    (byte) configRequest.mDiscoveryWindowInterval[ConfigRequest.NAN_BAND_24GHZ];
        }

        NanBandSpecificConfig config5 = new NanBandSpecificConfig();
        config5.rssiClose = 60;
        config5.rssiMiddle = 75;
        config5.rssiCloseProximity = 60;
        config5.dwellTimeMs = 200;
        config5.scanPeriodSec = 20;
        if (configRequest.mDiscoveryWindowInterval[ConfigRequest.NAN_BAND_5GHZ]
                == ConfigRequest.DW_INTERVAL_NOT_INIT) {
            config5.validDiscoveryWindowIntervalVal = false;
        } else {
            config5.validDiscoveryWindowIntervalVal = true;
            config5.discoveryWindowIntervalVal =
                    (byte) configRequest.mDiscoveryWindowInterval[ConfigRequest.NAN_BAND_5GHZ];
        }

        NanBandSpecificConfig config6 = new NanBandSpecificConfig();
        config6.rssiClose = 60;
        config6.rssiMiddle = 75;
        config6.rssiCloseProximity = 60;
        config6.dwellTimeMs = 200;
        config6.scanPeriodSec = 20;
        if (configRequest.mDiscoveryWindowInterval[ConfigRequest.NAN_BAND_6GHZ]
                == ConfigRequest.DW_INTERVAL_NOT_INIT) {
            config6.validDiscoveryWindowIntervalVal = false;
        } else {
            config6.validDiscoveryWindowIntervalVal = true;
            config6.discoveryWindowIntervalVal =
                    (byte) configRequest.mDiscoveryWindowInterval[ConfigRequest.NAN_BAND_6GHZ];
        }

        return new NanBandSpecificConfig[]{config24, config5, config6};
    }

    private static NanEnableRequest createNanEnableRequest(
            ConfigRequest configRequest, boolean notifyIdentityChange,
            NanConfigRequestSupplemental configSupplemental,
            int macAddressRandomizationIntervalSec, WifiNanIface.PowerParameters powerParameters) {
        NanEnableRequest req = new NanEnableRequest();
        NanBandSpecificConfig[] nanBandSpecificConfigs =
                createNanBandSpecificConfigs(configRequest);

        req.operateInBand = new boolean[3];
        req.operateInBand[NanBandIndex.NAN_BAND_24GHZ] = true;
        req.operateInBand[NanBandIndex.NAN_BAND_5GHZ] = configRequest.mSupport5gBand;
        req.operateInBand[NanBandIndex.NAN_BAND_6GHZ] = configRequest.mSupport6gBand;
        req.hopCountMax = 2;
        req.configParams = new NanConfigRequest();
        req.configParams.masterPref = (byte) configRequest.mMasterPreference;
        req.configParams.disableDiscoveryAddressChangeIndication = !notifyIdentityChange;
        req.configParams.disableStartedClusterIndication = !notifyIdentityChange;
        req.configParams.disableJoinedClusterIndication = !notifyIdentityChange;
        req.configParams.includePublishServiceIdsInBeacon = true;
        req.configParams.numberOfPublishServiceIdsInBeacon = 0;
        req.configParams.includeSubscribeServiceIdsInBeacon = true;
        req.configParams.numberOfSubscribeServiceIdsInBeacon = 0;
        req.configParams.rssiWindowSize = 8;
        req.configParams.macAddressRandomizationIntervalSec = macAddressRandomizationIntervalSec;

        req.configParams.bandSpecificConfig = new NanBandSpecificConfig[3];
        req.configParams.bandSpecificConfig[NanBandIndex.NAN_BAND_24GHZ] =
                nanBandSpecificConfigs[0];
        req.configParams.bandSpecificConfig[NanBandIndex.NAN_BAND_5GHZ] = nanBandSpecificConfigs[1];
        req.configParams.bandSpecificConfig[NanBandIndex.NAN_BAND_6GHZ] = nanBandSpecificConfigs[2];

        req.debugConfigs = new NanDebugConfig();
        req.debugConfigs.validClusterIdVals = true;
        req.debugConfigs.clusterIdTopRangeVal = (char) configRequest.mClusterHigh;
        req.debugConfigs.clusterIdBottomRangeVal = (char) configRequest.mClusterLow;
        req.debugConfigs.validIntfAddrVal = false;
        req.debugConfigs.validOuiVal = false;
        req.debugConfigs.ouiVal = 0;
        req.debugConfigs.validRandomFactorForceVal = false;
        req.debugConfigs.randomFactorForceVal = 0;
        req.debugConfigs.validHopCountForceVal = false;
        req.debugConfigs.hopCountForceVal = 0;
        req.debugConfigs.validDiscoveryChannelVal = false;
        req.debugConfigs.discoveryChannelMhzVal = new int[3];
        req.debugConfigs.discoveryChannelMhzVal[NanBandIndex.NAN_BAND_24GHZ] = 0;
        req.debugConfigs.discoveryChannelMhzVal[NanBandIndex.NAN_BAND_5GHZ] = 0;
        req.debugConfigs.discoveryChannelMhzVal[NanBandIndex.NAN_BAND_6GHZ] = 0;
        req.debugConfigs.validUseBeaconsInBandVal = false;
        req.debugConfigs.useBeaconsInBandVal = new boolean[3];
        req.debugConfigs.useBeaconsInBandVal[NanBandIndex.NAN_BAND_24GHZ] = true;
        req.debugConfigs.useBeaconsInBandVal[NanBandIndex.NAN_BAND_5GHZ] = true;
        req.debugConfigs.useBeaconsInBandVal[NanBandIndex.NAN_BAND_6GHZ] = true;
        req.debugConfigs.validUseSdfInBandVal = false;
        req.debugConfigs.useSdfInBandVal = new boolean[3];
        req.debugConfigs.useSdfInBandVal[NanBandIndex.NAN_BAND_24GHZ] = true;
        req.debugConfigs.useSdfInBandVal[NanBandIndex.NAN_BAND_5GHZ] = true;
        req.debugConfigs.useSdfInBandVal[NanBandIndex.NAN_BAND_6GHZ] = true;
        updateConfigForPowerSettings(req.configParams, configSupplemental, powerParameters);
        return req;
    }

    private static NanConfigRequest createNanConfigRequest(
            ConfigRequest configRequest, boolean notifyIdentityChange,
            NanConfigRequestSupplemental configSupplemental,
            int macAddressRandomizationIntervalSec, WifiNanIface.PowerParameters powerParameters) {
        NanConfigRequest req = new NanConfigRequest();
        NanBandSpecificConfig[] nanBandSpecificConfigs =
                createNanBandSpecificConfigs(configRequest);

        req.masterPref = (byte) configRequest.mMasterPreference;
        req.disableDiscoveryAddressChangeIndication = !notifyIdentityChange;
        req.disableStartedClusterIndication = !notifyIdentityChange;
        req.disableJoinedClusterIndication = !notifyIdentityChange;
        req.includePublishServiceIdsInBeacon = true;
        req.numberOfPublishServiceIdsInBeacon = 0;
        req.includeSubscribeServiceIdsInBeacon = true;
        req.numberOfSubscribeServiceIdsInBeacon = 0;
        req.rssiWindowSize = 8;
        req.macAddressRandomizationIntervalSec = macAddressRandomizationIntervalSec;

        req.bandSpecificConfig = new NanBandSpecificConfig[3];
        req.bandSpecificConfig[NanBandIndex.NAN_BAND_24GHZ] = nanBandSpecificConfigs[0];
        req.bandSpecificConfig[NanBandIndex.NAN_BAND_5GHZ] = nanBandSpecificConfigs[1];
        req.bandSpecificConfig[NanBandIndex.NAN_BAND_6GHZ] = nanBandSpecificConfigs[2];
        updateConfigForPowerSettings(req, configSupplemental, powerParameters);
        return req;
    }

    /**
     * Update the NAN configuration to reflect the current power settings
     */
    private static void updateConfigForPowerSettings(NanConfigRequest req,
            NanConfigRequestSupplemental configSupplemental,
            WifiNanIface.PowerParameters powerParameters) {
        updateSingleConfigForPowerSettings(req.bandSpecificConfig[NanBandIndex.NAN_BAND_5GHZ],
                powerParameters.discoveryWindow5Ghz);
        updateSingleConfigForPowerSettings(req.bandSpecificConfig[NanBandIndex.NAN_BAND_24GHZ],
                powerParameters.discoveryWindow24Ghz);
        updateSingleConfigForPowerSettings(req.bandSpecificConfig[NanBandIndex.NAN_BAND_6GHZ],
                powerParameters.discoveryWindow6Ghz);

        configSupplemental.discoveryBeaconIntervalMs = powerParameters.discoveryBeaconIntervalMs;
        configSupplemental.numberOfSpatialStreamsInDiscovery =
                powerParameters.numberOfSpatialStreamsInDiscovery;
        configSupplemental.enableDiscoveryWindowEarlyTermination =
                powerParameters.enableDiscoveryWindowEarlyTermination;
    }

    private static void updateSingleConfigForPowerSettings(
            NanBandSpecificConfig cfg, int override) {
        if (override != -1) {
            cfg.validDiscoveryWindowIntervalVal = true;
            cfg.discoveryWindowIntervalVal = (byte) override;
        }
    }

    private static NanPublishRequest createNanPublishRequest(
            byte publishId, PublishConfig publishConfig) {
        NanPublishRequest req = new NanPublishRequest();
        req.baseConfigs = new NanDiscoveryCommonConfig();
        req.baseConfigs.sessionId = publishId;
        req.baseConfigs.ttlSec = (char) publishConfig.mTtlSec;
        req.baseConfigs.discoveryWindowPeriod = 1;
        req.baseConfigs.discoveryCount = 0;
        req.baseConfigs.serviceName = publishConfig.mServiceName;
        req.baseConfigs.discoveryMatchIndicator = NanMatchAlg.MATCH_NEVER;
        req.baseConfigs.serviceSpecificInfo = publishConfig.mServiceSpecificInfo;
        if (publishConfig.mPublishType == PublishConfig.PUBLISH_TYPE_UNSOLICITED) {
            req.baseConfigs.txMatchFilter = publishConfig.mMatchFilter;
            req.baseConfigs.rxMatchFilter = new byte[0];
        } else {
            req.baseConfigs.rxMatchFilter = publishConfig.mMatchFilter;
            req.baseConfigs.txMatchFilter = new byte[0];
        }
        req.baseConfigs.useRssiThreshold = false;
        req.baseConfigs.disableDiscoveryTerminationIndication =
                !publishConfig.mEnableTerminateNotification;
        req.baseConfigs.disableMatchExpirationIndication = true;
        req.baseConfigs.disableFollowupReceivedIndication = false;

        req.autoAcceptDataPathRequests = false;

        req.baseConfigs.rangingRequired = publishConfig.mEnableRanging;

        req.baseConfigs.securityConfig = new NanDataPathSecurityConfig();
        req.baseConfigs.securityConfig.securityType = NanDataPathSecurityType.OPEN;
        WifiAwareDataPathSecurityConfig securityConfig = publishConfig.getSecurityConfig();
        if (securityConfig != null) {
            req.baseConfigs.securityConfig.cipherType = getHalCipherSuiteType(
                    securityConfig.getCipherSuite());
            if (securityConfig.getPmk() != null && securityConfig.getPmk().length != 0) {
                req.baseConfigs.securityConfig.securityType = NanDataPathSecurityType.PMK;
                req.baseConfigs.securityConfig.pmk = copyArray(securityConfig.getPmk());
            }
            if (securityConfig.getPskPassphrase() != null
                    && securityConfig.getPskPassphrase().length() != 0) {
                req.baseConfigs.securityConfig.securityType = NanDataPathSecurityType.PASSPHRASE;
                req.baseConfigs.securityConfig.passphrase =
                        securityConfig.getPskPassphrase().getBytes();
            }
            if (securityConfig.getPmkId() != null && securityConfig.getPmkId().length != 0) {
                req.baseConfigs.securityConfig.scid = copyArray(securityConfig.getPmkId());
            }
        }

        req.publishType = publishConfig.mPublishType;
        req.txType = NanTxType.BROADCAST;
        return req;
    }

    private static NanSubscribeRequest createNanSubscribeRequest(
            byte subscribeId, SubscribeConfig subscribeConfig) {
        NanSubscribeRequest req = new NanSubscribeRequest();
        req.baseConfigs = new NanDiscoveryCommonConfig();
        req.baseConfigs.sessionId = subscribeId;
        req.baseConfigs.ttlSec = (char) subscribeConfig.mTtlSec;
        req.baseConfigs.discoveryWindowPeriod = 1;
        req.baseConfigs.discoveryCount = 0;
        req.baseConfigs.serviceName = subscribeConfig.mServiceName;
        req.baseConfigs.discoveryMatchIndicator = NanMatchAlg.MATCH_ONCE;
        req.baseConfigs.serviceSpecificInfo = subscribeConfig.mServiceSpecificInfo;
        if (subscribeConfig.mSubscribeType == SubscribeConfig.SUBSCRIBE_TYPE_ACTIVE) {
            req.baseConfigs.txMatchFilter = subscribeConfig.mMatchFilter;
            req.baseConfigs.rxMatchFilter = new byte[0];
        } else {
            req.baseConfigs.rxMatchFilter = subscribeConfig.mMatchFilter;
            req.baseConfigs.txMatchFilter = new byte[0];
        }
        req.baseConfigs.useRssiThreshold = false;
        req.baseConfigs.disableDiscoveryTerminationIndication =
                !subscribeConfig.mEnableTerminateNotification;
        req.baseConfigs.disableMatchExpirationIndication = false;
        req.baseConfigs.disableFollowupReceivedIndication = false;

        req.baseConfigs.rangingRequired =
                subscribeConfig.mMinDistanceMmSet || subscribeConfig.mMaxDistanceMmSet;
        req.baseConfigs.configRangingIndications = 0;
        if (subscribeConfig.mMinDistanceMmSet) {
            req.baseConfigs.distanceEgressCm = (char) Math.min(
                    subscribeConfig.mMinDistanceMm / 10, Short.MAX_VALUE);
            req.baseConfigs.configRangingIndications |= NanRangingIndication.EGRESS_MET_MASK;
        }
        if (subscribeConfig.mMaxDistanceMmSet) {
            req.baseConfigs.distanceIngressCm = (char) Math.min(
                    subscribeConfig.mMaxDistanceMm / 10, Short.MAX_VALUE);
            req.baseConfigs.configRangingIndications |= NanRangingIndication.INGRESS_MET_MASK;
        }

        // TODO: configure security
        req.baseConfigs.securityConfig = new NanDataPathSecurityConfig();
        req.baseConfigs.securityConfig.securityType = NanDataPathSecurityType.OPEN;

        req.subscribeType = subscribeConfig.mSubscribeType;
        return req;
    }

    private static NanTransmitFollowupRequest createNanTransmitFollowupRequest(
            byte pubSubId, int requesterInstanceId, MacAddress dest, byte[] message) {
        NanTransmitFollowupRequest req = new NanTransmitFollowupRequest();
        req.discoverySessionId = pubSubId;
        req.peerId = requesterInstanceId;
        req.addr = dest.toByteArray();
        req.isHighPriority = false;
        req.shouldUseDiscoveryWindow = true;
        req.serviceSpecificInfo = message;
        req.disableFollowupResultIndication = false;
        return req;
    }

    private static NanInitiateDataPathRequest createNanInitiateDataPathRequest(
            int peerId, int channelRequestType, int channel, MacAddress peer, String interfaceName,
            boolean isOutOfBand, byte[] appInfo, WifiAwareDataPathSecurityConfig securityConfig) {
        NanInitiateDataPathRequest req = new NanInitiateDataPathRequest();
        req.peerId = peerId;
        req.peerDiscMacAddr = peer.toByteArray();
        req.channelRequestType = WifiNanIface.NanDataPathChannelCfg.toAidl(channelRequestType);
        req.channel = channel;
        req.ifaceName = interfaceName;
        req.securityConfig = new NanDataPathSecurityConfig();
        req.securityConfig.securityType = NanDataPathSecurityType.OPEN;
        if (securityConfig != null) {
            req.securityConfig.cipherType = getHalCipherSuiteType(securityConfig.getCipherSuite());
            if (securityConfig.getPmk() != null && securityConfig.getPmk().length != 0) {
                req.securityConfig.securityType = NanDataPathSecurityType.PMK;
                req.securityConfig.pmk = copyArray(securityConfig.getPmk());
            }
            if (securityConfig.getPskPassphrase() != null
                    && securityConfig.getPskPassphrase().length() != 0) {
                req.securityConfig.securityType = NanDataPathSecurityType.PASSPHRASE;
                req.securityConfig.passphrase = securityConfig.getPskPassphrase().getBytes();
            }
            if (securityConfig.getPmkId() != null && securityConfig.getPmkId().length != 0) {
                req.securityConfig.scid = copyArray(securityConfig.getPmkId());
            }
        }

        if (req.securityConfig.securityType != NanDataPathSecurityType.OPEN && isOutOfBand) {
            req.serviceNameOutOfBand = WifiNanIface.SERVICE_NAME_FOR_OOB_DATA_PATH
                    .getBytes(StandardCharsets.UTF_8);
        }
        req.appInfo = appInfo;
        return req;
    }

    private static NanRespondToDataPathIndicationRequest
            createNanRespondToDataPathIndicationRequest(boolean accept, int ndpId,
            String interfaceName, byte[] appInfo, boolean isOutOfBand,
            WifiAwareDataPathSecurityConfig securityConfig) {
        NanRespondToDataPathIndicationRequest req = new NanRespondToDataPathIndicationRequest();
        req.acceptRequest = accept;
        req.ndpInstanceId = ndpId;
        req.ifaceName = interfaceName;
        req.securityConfig = new NanDataPathSecurityConfig();
        req.securityConfig.securityType = NanDataPathSecurityType.OPEN;
        if (securityConfig != null) {
            req.securityConfig.cipherType = getHalCipherSuiteType(securityConfig.getCipherSuite());
            if (securityConfig.getPmk() != null && securityConfig.getPmk().length != 0) {
                req.securityConfig.securityType = NanDataPathSecurityType.PMK;
                req.securityConfig.pmk = copyArray(securityConfig.getPmk());
            }
            if (securityConfig.getPskPassphrase() != null
                    && securityConfig.getPskPassphrase().length() != 0) {
                req.securityConfig.securityType = NanDataPathSecurityType.PASSPHRASE;
                req.securityConfig.passphrase = securityConfig.getPskPassphrase().getBytes();
            }
            if (securityConfig.getPmkId() != null && securityConfig.getPmkId().length != 0) {
                req.securityConfig.scid = copyArray(securityConfig.getPmkId());
            }
        }

        if (req.securityConfig.securityType != NanDataPathSecurityType.OPEN && isOutOfBand) {
            req.serviceNameOutOfBand = WifiNanIface.SERVICE_NAME_FOR_OOB_DATA_PATH
                    .getBytes(StandardCharsets.UTF_8);
        }
        req.appInfo = appInfo;
        return req;
    }

    private static int getHalCipherSuiteType(int frameworkCipherSuites) {
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

    private static byte[] copyArray(byte[] source) {
        return source != null ? source.clone() : new byte[0];
    }

    private boolean checkIfaceAndLogFailure(String methodStr) {
        if (mWifiNanIface == null) {
            Log.e(TAG, "Unable to call " + methodStr + " because iface is null.");
            return false;
        }
        return true;
    }

    private void handleRemoteException(RemoteException e, String methodStr) {
        mWifiNanIface = null;
        mIfaceName = null;
        Log.e(TAG, methodStr + " failed with remote exception: " + e);
    }

    private void handleServiceSpecificException(ServiceSpecificException e, String methodStr) {
        Log.e(TAG, methodStr + " failed with service-specific exception: " + e);
    }
}
