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

package com.android.server.wifi.aware;

import static android.net.wifi.aware.AwarePairingConfig.PAIRING_BOOTSTRAPPING_NFC_READER;
import static android.net.wifi.aware.AwarePairingConfig.PAIRING_BOOTSTRAPPING_NFC_TAG;
import static android.net.wifi.aware.AwarePairingConfig.PAIRING_BOOTSTRAPPING_OPPORTUNISTIC;
import static android.net.wifi.aware.AwarePairingConfig.PAIRING_BOOTSTRAPPING_PASSPHRASE_DISPLAY;
import static android.net.wifi.aware.AwarePairingConfig.PAIRING_BOOTSTRAPPING_PASSPHRASE_KEYPAD;
import static android.net.wifi.aware.AwarePairingConfig.PAIRING_BOOTSTRAPPING_PIN_CODE_DISPLAY;
import static android.net.wifi.aware.AwarePairingConfig.PAIRING_BOOTSTRAPPING_PIN_CODE_KEYPAD;
import static android.net.wifi.aware.AwarePairingConfig.PAIRING_BOOTSTRAPPING_QR_DISPLAY;
import static android.net.wifi.aware.AwarePairingConfig.PAIRING_BOOTSTRAPPING_QR_SCAN;
import static android.net.wifi.aware.Characteristics.WIFI_AWARE_CIPHER_SUITE_NCS_PK_128;
import static android.net.wifi.aware.Characteristics.WIFI_AWARE_CIPHER_SUITE_NCS_PK_256;
import static android.net.wifi.aware.Characteristics.WIFI_AWARE_CIPHER_SUITE_NCS_PK_PASN_128;
import static android.net.wifi.aware.Characteristics.WIFI_AWARE_CIPHER_SUITE_NCS_PK_PASN_256;
import static android.net.wifi.aware.Characteristics.WIFI_AWARE_CIPHER_SUITE_NCS_SK_128;
import static android.net.wifi.aware.Characteristics.WIFI_AWARE_CIPHER_SUITE_NCS_SK_256;

import android.annotation.Nullable;
import android.net.MacAddress;
import android.net.wifi.aware.AwarePairingConfig;
import android.net.wifi.aware.ConfigRequest;
import android.net.wifi.aware.PublishConfig;
import android.net.wifi.aware.SubscribeConfig;
import android.net.wifi.aware.WifiAwareDataPathSecurityConfig;
import android.os.RemoteException;
import android.os.ServiceSpecificException;
import android.system.wifi.mainline_supplicant.ISupplicantNanIface;
import android.system.wifi.mainline_supplicant.NanBandIndex;
import android.system.wifi.mainline_supplicant.NanBandSpecificConfig;
import android.system.wifi.mainline_supplicant.NanBootstrappingMethod;
import android.system.wifi.mainline_supplicant.NanCipherSuiteType;
import android.system.wifi.mainline_supplicant.NanConfigRequest;
import android.system.wifi.mainline_supplicant.NanDataPathSecurityConfig;
import android.system.wifi.mainline_supplicant.NanDiscoveryCommonConfig;
import android.system.wifi.mainline_supplicant.NanEnableRequest;
import android.system.wifi.mainline_supplicant.NanPairingConfig;
import android.system.wifi.mainline_supplicant.NanPublishRequest;
import android.system.wifi.mainline_supplicant.NanRangingIndication;
import android.system.wifi.mainline_supplicant.NanSubscribeRequest;
import android.system.wifi.mainline_supplicant.NanTransmitFollowupRequest;
import android.system.wifi.mainline_supplicant.WifiChannelInfo;
import android.util.Log;

import com.android.modules.utils.build.SdkLevel;
import com.android.server.wifi.hal.WifiNanIface;
import com.android.server.wifi.hal.WifiRttControllerAidlImpl;
import com.android.server.wifi.util.HalAidlUtil;

/**
 * Implementation using the AIDL interface for mainline supplicant.
 */
public class AwareIfaceAidlSupplicantImpl {
    private static final String TAG = "AwareSupplicant";
    private ISupplicantNanIface mWifiNanIface;
    private String mIfaceName;
    private AwareIfaceCallbackSupplicantImpl mHalCallback;
    private boolean mVerboseLoggingEnabled;

    /**
     * Constructor
     *
     * @param iface The ISupplicantNanIface interface.
     */
    public AwareIfaceAidlSupplicantImpl(ISupplicantNanIface iface) {
        mWifiNanIface = iface;
    }

    /**
     * Enable verbose logging.
     */
    public void enableVerboseLogging(boolean verbose) {
        mVerboseLoggingEnabled = verbose;
        if (mHalCallback != null) {
            mHalCallback.enableVerboseLogging(verbose);
        }
    }

    private void handleRemoteException(RemoteException e, String methodStr) {
        mWifiNanIface = null;
        mIfaceName = null;
        Log.e(TAG, methodStr + " failed with remote exception: " + e);
    }

    private void handleServiceSpecificException(ServiceSpecificException e, String methodStr) {
        Log.e(TAG, methodStr + " failed with service-specific exception: " + e);
    }

    /**
     * Register a framework callback.
     * Should be only be called once when create the interface.
     *
     * @param frameworkCallback The framework callback to register.
     * @return True if the registration was successful, false otherwise.
     */
    public boolean registerFrameworkCallback(WifiNanIface.Callback frameworkCallback) {
        final String methodStr = "registerFrameworkCallback";
        if (!checkIfaceAndLogFailure(methodStr)) return false;
        mHalCallback = new AwareIfaceCallbackSupplicantImpl(frameworkCallback);
        try {
            mWifiNanIface.registerEventCallback(mHalCallback);
            mHalCallback.enableVerboseLogging(mVerboseLoggingEnabled);
            return true;
        } catch (RemoteException e) {
            handleRemoteException(e, methodStr);
        } catch (ServiceSpecificException e) {
            handleServiceSpecificException(e, methodStr);
        }
        mHalCallback = null;
        return false;
    }

    /**
     * Get the name of the interface.
     *
     * @return The name of the interface, or null if an error occurred.
     */
    @Nullable
    public String getName() {
        final String methodStr = "getName";
        if (!checkIfaceAndLogFailure(methodStr)) return null;
        if (mIfaceName != null) return mIfaceName;
        try {
            mIfaceName = mWifiNanIface.getName();
            return mIfaceName;
        } catch (RemoteException e) {
            handleRemoteException(e, methodStr);
        } catch (ServiceSpecificException e) {
            handleServiceSpecificException(e, methodStr);
        }
        return null;
    }

    /**
     * Get the capabilities of the Aware interface.
     *
     * @see ISupplicantNanIface#getCapabilitiesRequest(char)
     * @return True if the request was successful, false otherwise.
     */
    public boolean getCapabilities(short transactionId) {
        final String methodStr = "getCapabilities";
        if (!checkIfaceAndLogFailure(methodStr)) return false;
        try {
            mWifiNanIface.getCapabilitiesRequest((char) transactionId);
            return true;
        } catch (RemoteException e) {
            handleRemoteException(e, methodStr);
        } catch (ServiceSpecificException e) {
            handleServiceSpecificException(e, methodStr);
        }
        return false;
    }

    /**
     * Enable and configure the Aware interface.
     *
     * @see ISupplicantNanIface#enableRequest(char, NanEnableRequest, NanConfigRequest)
     * @see ISupplicantNanIface#configRequest(char, NanConfigRequest)
     * @return True if the request was successful, false otherwise.
     */
    public boolean enableAndConfigure(short transactionId, ConfigRequest configRequest,
            boolean notifyIdentityChange, boolean initialConfiguration,
            WifiNanIface.PowerParameters powerParameters) {
        final String methodStr = "enableAndConfigure";
        try {
            if (!checkIfaceAndLogFailure(methodStr)) return false;
            NanConfigRequest configReq = createNanConfigRequest(
                        configRequest, notifyIdentityChange, powerParameters);
            if (initialConfiguration) {
                NanEnableRequest req = createNanEnableRequest(configRequest, configReq);
                mWifiNanIface.enableRequest((char) transactionId, req, configReq);
            } else {
                mWifiNanIface.configRequest((char) transactionId, configReq);
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
     * Disable Aware interface
     *
     * @see ISupplicantNanIface#disableRequest(char)
     * @return True if the request was successful, false otherwise.
     */
    public boolean disableRequest(short transactionId) {
        final String methodStr = "disableRequest";
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

    /**
     * Create an Aware network interface.
     *
     * @see ISupplicantNanIface#createDataInterfaceRequest(char, String)
     * @return True if the request was successful, false otherwise.
     */
    public boolean createAwareNetworkInterface(short transactionId, String interfaceName) {
        final String methodStr = "createAwareNetworkInterface";
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

    /**
     * Delete an Aware network interface.
     *
     * @see ISupplicantNanIface#deleteDataInterfaceRequest(char, String)
     * @return True if the request was successful, false otherwise.
     */
    public boolean deleteAwareNetworkInterface(short transactionId, String interfaceName) {
        final String methodStr = "deleteAwareNetworkInterface";
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

    /**
     * Publish a NAN service.
     *
     * @see ISupplicantNanIface#startPublishRequest(char, NanPublishRequest)
     * @return True if the request was successful, false otherwise.
     */
    public boolean publish(short transactionId, byte publishId, PublishConfig publishConfig,
            byte[] nanIdentityKey) {
        final String methodStr = "publish";
        try {
            if (!checkIfaceAndLogFailure(methodStr)) return false;
            NanPublishRequest req = createNanPublishRequest(publishId, publishConfig,
                        nanIdentityKey);
            mWifiNanIface.startPublishRequest((char) transactionId, req);
            return true;
        } catch (RemoteException e) {
            handleRemoteException(e, methodStr);
        } catch (ServiceSpecificException e) {
            handleServiceSpecificException(e, methodStr);
        }
        return false;
    }

    /**
     * Subscribe to a NAN service.
     *
     * @see ISupplicantNanIface#startSubscribeRequest(char, NanSubscribeRequest)
     * @return True if the request was successful, false otherwise.
     */
    public boolean subscribe(short transactionId, byte subscribeId,
            SubscribeConfig subscribeConfig, byte[] nanIdentityKey) {
        final String methodStr = "subscribe";
        try {
            if (!checkIfaceAndLogFailure(methodStr)) return false;
            NanSubscribeRequest req = createNanSubscribeRequest(subscribeId, subscribeConfig,
                    nanIdentityKey);
            mWifiNanIface.startSubscribeRequest((char) transactionId, req);
            return true;
        } catch (RemoteException e) {
            handleRemoteException(e, methodStr);
        } catch (ServiceSpecificException e) {
            handleServiceSpecificException(e, methodStr);
        }
        return false;
    }

    /**
     * Stop publishing a NAN service.
     *
     * @see ISupplicantNanIface#stopPublishRequest(char, byte)
     * @return True if the request was successful, false otherwise.
     */
    public boolean stopPublish(short transactionId, byte pubSubId) {
        final String methodStr = "stopPublish";
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

    /**
     * Stop subscribing to a NAN service.
     *
     * @see ISupplicantNanIface#stopSubscribeRequest(char, byte)
     * @return True if the request was successful, false otherwise.
     */
    public boolean stopSubscribe(short transactionId, byte pubSubId) {
        final String methodStr = "stopSubscribe";
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

    /**
     * Send a message to a discovered peer.
     * @see ISupplicantNanIface#transmitFollowupRequest(char, NanTransmitFollowupRequest)
     * @return True if the request was successful, false otherwise.
     */
    public boolean sendMessage(short transactionId, byte pubSubId, int requesterInstanceId,
            MacAddress dest, byte[] message) {
        final String methodStr = "sendMessage";
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

    private NanEnableRequest createNanEnableRequest(
            ConfigRequest configRequest, NanConfigRequest configReq) {
        NanEnableRequest req = new NanEnableRequest();
        req.configParams = configReq;
        req.operateInBand = new boolean[3];
        req.operateInBand[NanBandIndex.NAN_BAND_24GHZ] = true;
        req.operateInBand[NanBandIndex.NAN_BAND_5GHZ] = configRequest.mSupport5gBand;
        req.operateInBand[NanBandIndex.NAN_BAND_6GHZ] = configRequest.mSupport6gBand;
        return req;
    }

    private static NanConfigRequest createNanConfigRequest(
            ConfigRequest configRequest, boolean notifyIdentityChange,
            WifiNanIface.PowerParameters powerParameters) {
        NanConfigRequest req = new NanConfigRequest();
        NanBandSpecificConfig[] nanBandSpecificConfigs =
                createNanBandSpecificConfigs(configRequest);

        req.masterPref = (byte) configRequest.mMasterPreference;
        req.disableStartedClusterIndication = !notifyIdentityChange;
        req.disableJoinedClusterIndication = !notifyIdentityChange;
        req.includePublishServiceIdsInBeacon = true;
        req.numberOfPublishServiceIdsInBeacon = 0;
        req.includeSubscribeServiceIdsInBeacon = true;
        req.numberOfSubscribeServiceIdsInBeacon = 0;
        req.rssiWindowSize = 8;

        req.bandSpecificConfig = new NanBandSpecificConfig[3];
        req.bandSpecificConfig[NanBandIndex.NAN_BAND_24GHZ] = nanBandSpecificConfigs[0];
        req.bandSpecificConfig[NanBandIndex.NAN_BAND_5GHZ] = nanBandSpecificConfigs[1];
        req.bandSpecificConfig[NanBandIndex.NAN_BAND_6GHZ] = nanBandSpecificConfigs[2];
        req.discoveryBeaconIntervalMs = powerParameters.discoveryBeaconIntervalMs;
        updateConfigForPowerSettings(req, powerParameters);
        return req;

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

    /**
     * Update the NAN configuration to reflect the current power settings
     */
    private static void updateConfigForPowerSettings(NanConfigRequest req,
            WifiNanIface.PowerParameters powerParameters) {
        if (powerParameters == null) {
            return;
        }
        updateSingleConfigForPowerSettings(req.bandSpecificConfig[NanBandIndex.NAN_BAND_5GHZ],
                powerParameters.discoveryWindow5Ghz);
        updateSingleConfigForPowerSettings(req.bandSpecificConfig[NanBandIndex.NAN_BAND_24GHZ],
                powerParameters.discoveryWindow24Ghz);
        updateSingleConfigForPowerSettings(req.bandSpecificConfig[NanBandIndex.NAN_BAND_6GHZ],
                powerParameters.discoveryWindow6Ghz);
    }

    private static void updateSingleConfigForPowerSettings(
            NanBandSpecificConfig cfg, int override) {
        if (override != -1) {
            cfg.validDiscoveryWindowIntervalVal = true;
            cfg.discoveryWindowIntervalVal = (byte) override;
        }
    }

    private static void enableFrameProtection(NanDataPathSecurityConfig securityConfig) {
        securityConfig.requiresEnhancedFrameProtection = true;
        securityConfig.supportBigtksa = true;
        securityConfig.supportGtkAndIgtk = true;
    }

    private static int getSupplicantBootstrappingMethods(int frameworkBootstrappingMethods) {
        int bootstrappingMethods = 0;
        if ((frameworkBootstrappingMethods & PAIRING_BOOTSTRAPPING_OPPORTUNISTIC) != 0) {
            bootstrappingMethods |= NanBootstrappingMethod.OPPORTUNISTIC_MASK;
        }
        if ((frameworkBootstrappingMethods & PAIRING_BOOTSTRAPPING_PIN_CODE_DISPLAY) != 0) {
            bootstrappingMethods |= NanBootstrappingMethod.PIN_CODE_DISPLAY_MASK;
        }
        if ((frameworkBootstrappingMethods & PAIRING_BOOTSTRAPPING_PIN_CODE_KEYPAD) != 0) {
            bootstrappingMethods |= NanBootstrappingMethod.PIN_CODE_KEYPAD_MASK;
        }
        if ((frameworkBootstrappingMethods & PAIRING_BOOTSTRAPPING_NFC_READER) != 0) {
            bootstrappingMethods |= NanBootstrappingMethod.NFC_READER_MASK;
        }
        if ((frameworkBootstrappingMethods & PAIRING_BOOTSTRAPPING_NFC_TAG) != 0) {
            bootstrappingMethods |= NanBootstrappingMethod.NFC_TAG_MASK;
        }
        if ((frameworkBootstrappingMethods & PAIRING_BOOTSTRAPPING_QR_DISPLAY) != 0) {
            bootstrappingMethods |= NanBootstrappingMethod.QR_DISPLAY_MASK;
        }
        if ((frameworkBootstrappingMethods & PAIRING_BOOTSTRAPPING_QR_SCAN) != 0) {
            bootstrappingMethods |= NanBootstrappingMethod.QR_SCAN_MASK;
        }
        if ((frameworkBootstrappingMethods & PAIRING_BOOTSTRAPPING_PASSPHRASE_DISPLAY) != 0) {
            bootstrappingMethods |= NanBootstrappingMethod.PASSPHRASE_DISPLAY_MASK;
        }
        if ((frameworkBootstrappingMethods & PAIRING_BOOTSTRAPPING_PASSPHRASE_KEYPAD) != 0) {
            bootstrappingMethods |= NanBootstrappingMethod.PASSPHRASE_KEYPAD_MASK;
        }

        return bootstrappingMethods;
    }

    private static int getSupplicantCipherSuites(int frameworkCipherSuites) {
        int cipherSuites = NanCipherSuiteType.NONE;
        if ((frameworkCipherSuites & WIFI_AWARE_CIPHER_SUITE_NCS_SK_128) != 0) {
            cipherSuites |= NanCipherSuiteType.SHARED_KEY_128_MASK;
        }
        if ((frameworkCipherSuites & WIFI_AWARE_CIPHER_SUITE_NCS_SK_256) != 0) {
            cipherSuites |= NanCipherSuiteType.SHARED_KEY_256_MASK;
        }
        if ((frameworkCipherSuites & WIFI_AWARE_CIPHER_SUITE_NCS_PK_128) != 0) {
            cipherSuites |= NanCipherSuiteType.PUBLIC_KEY_2WDH_128_MASK;
        }
        if ((frameworkCipherSuites & WIFI_AWARE_CIPHER_SUITE_NCS_PK_256) != 0) {
            cipherSuites |= NanCipherSuiteType.PUBLIC_KEY_2WDH_256_MASK;
        }
        if ((frameworkCipherSuites & WIFI_AWARE_CIPHER_SUITE_NCS_PK_PASN_128) != 0) {
            cipherSuites |= NanCipherSuiteType.PUBLIC_KEY_PASN_128_MASK;
        }
        if ((frameworkCipherSuites & WIFI_AWARE_CIPHER_SUITE_NCS_PK_PASN_256) != 0) {
            cipherSuites |= NanCipherSuiteType.PUBLIC_KEY_PASN_256_MASK;
        }
        return cipherSuites;
    }

    private static NanPublishRequest createNanPublishRequest(
            byte publishId, PublishConfig publishConfig, byte[] nik) {
        NanPublishRequest req = new NanPublishRequest();
        req.baseConfig = new NanDiscoveryCommonConfig();
        req.baseConfig.sessionId = publishId;
        req.baseConfig.ttlSec = (char) publishConfig.mTtlSec;
        req.baseConfig.discoveryWindowPeriod = 1;
        req.baseConfig.discoveryCount = 0;
        req.baseConfig.serviceName = copyArray(publishConfig.mServiceName);
        if (publishConfig.mServiceSpecificInfo != null
                && publishConfig.mServiceSpecificInfo.length > 255) {
            req.baseConfig.serviceSpecificInfo = new byte[0];
        } else {
            req.baseConfig.serviceSpecificInfo = copyArray(publishConfig.mServiceSpecificInfo);
        }
        req.baseConfig.extendedServiceSpecificInfo = copyArray(publishConfig.mServiceSpecificInfo);
        if (publishConfig.mPublishType == PublishConfig.PUBLISH_TYPE_UNSOLICITED) {
            req.baseConfig.txMatchFilter = copyArray(publishConfig.mMatchFilter);
            req.baseConfig.rxMatchFilter = new byte[0];
        } else {
            req.baseConfig.rxMatchFilter = copyArray(publishConfig.mMatchFilter);
            req.baseConfig.txMatchFilter = new byte[0];
        }
        req.baseConfig.useRssiThreshold = false;
        req.baseConfig.disableDiscoveryTerminationIndication =
                !publishConfig.mEnableTerminateNotification;
        req.baseConfig.disableMatchExpirationIndication = true;
        req.baseConfig.disableFollowupReceivedIndication = false;

        req.autoAcceptDataPathRequests = false;

        req.baseConfig.rangingRequired = publishConfig.mEnableRanging;

        req.baseConfig.securityConfig = new NanDataPathSecurityConfig();
        req.baseConfig.securityConfig.pmk = new byte[32];
        req.baseConfig.securityConfig.passphrase = new byte[32];
        req.baseConfig.securityConfig.scid = new byte[16];
        req.baseConfig.securityConfig.securityType =
                NanDataPathSecurityConfig.NanDataPathSecurityType.OPEN;
        WifiAwareDataPathSecurityConfig securityConfig = publishConfig.getSecurityConfig();
        if (securityConfig != null) {
            req.baseConfig.securityConfig.cipherType = getSupplicantCipherSuites(
                    securityConfig.getCipherSuite());
            if (securityConfig.getPmk() != null && securityConfig.getPmk().length != 0) {
                req.baseConfig.securityConfig.securityType =
                        NanDataPathSecurityConfig.NanDataPathSecurityType.PMK;
                req.baseConfig.securityConfig.pmk = copyArray(securityConfig.getPmk());
            }
            if (securityConfig.getPskPassphrase() != null
                    && !securityConfig.getPskPassphrase().isEmpty()) {
                req.baseConfig.securityConfig.securityType =
                        NanDataPathSecurityConfig.NanDataPathSecurityType.PASSPHRASE;
                req.baseConfig.securityConfig.passphrase =
                        securityConfig.getPskPassphrase().getBytes();
            }
            if (securityConfig.getPmkId() != null && securityConfig.getPmkId().length != 0) {
                req.baseConfig.securityConfig.scid = copyArray(securityConfig.getPmkId());
            }
        }

        req.baseConfig.enableSessionSuspendability = SdkLevel.isAtLeastU()
                && publishConfig.isSuspendable();

        req.rangingResultsRequired = publishConfig.mEnablePeriodicRangingResults;
        req.publishType = publishConfig.mPublishType;
        req.pairingConfig = createAidlPairingConfig(publishConfig.getPairingConfig());
        if (publishConfig.getPairingConfig() != null) {
            req.baseConfig.securityConfig.securityType =
                    NanDataPathSecurityConfig.NanDataPathSecurityType.PASSPHRASE;
            req.baseConfig.securityConfig.cipherType = getSupplicantCipherSuites(
                    publishConfig.getPairingConfig().getSupportedCipherSuites());
            enableFrameProtection(req.baseConfig.securityConfig);
        }
        req.identityKey = copyArray(nik, 16);

        if (SdkLevel.isAtLeastV() && !publishConfig.getVendorData().isEmpty()) {
            req.vendorData =
                    HalAidlUtil.frameworkToHalOuiKeyedDataList(publishConfig.getVendorData());
        }

        return req;
    }

    private static NanSubscribeRequest createNanSubscribeRequest(
            byte subscribeId, SubscribeConfig subscribeConfig, byte[] nik) {
        NanSubscribeRequest req = new NanSubscribeRequest();
        req.baseConfig = new NanDiscoveryCommonConfig();
        req.baseConfig.sessionId = subscribeId;
        req.baseConfig.ttlSec = (char) subscribeConfig.mTtlSec;
        req.baseConfig.discoveryWindowPeriod = 1;
        req.baseConfig.discoveryCount = 0;
        req.baseConfig.serviceName = copyArray(subscribeConfig.mServiceName);
        if (subscribeConfig.mServiceSpecificInfo != null
                && subscribeConfig.mServiceSpecificInfo.length > 255) {
            req.baseConfig.serviceSpecificInfo = new byte[0];
        } else {
            req.baseConfig.serviceSpecificInfo = copyArray(subscribeConfig.mServiceSpecificInfo);
        }
        req.baseConfig.extendedServiceSpecificInfo =
            copyArray(subscribeConfig.mServiceSpecificInfo);
        if (subscribeConfig.mSubscribeType == SubscribeConfig.SUBSCRIBE_TYPE_ACTIVE) {
            req.baseConfig.txMatchFilter = copyArray(subscribeConfig.mMatchFilter);
            req.baseConfig.rxMatchFilter = new byte[0];
        } else {
            req.baseConfig.rxMatchFilter = copyArray(subscribeConfig.mMatchFilter);
            req.baseConfig.txMatchFilter = new byte[0];
        }
        req.baseConfig.useRssiThreshold = false;
        req.baseConfig.disableDiscoveryTerminationIndication =
                !subscribeConfig.mEnableTerminateNotification;
        req.baseConfig.disableMatchExpirationIndication = false;
        req.baseConfig.disableFollowupReceivedIndication = false;

        req.baseConfig.rangingRequired =
                subscribeConfig.mEgressDistanceMmSet || subscribeConfig.mIngressDistanceMmSet
                        || subscribeConfig.mPeriodicRangingEnabled;
        req.baseConfig.configRangingIndications = 0;
        if (subscribeConfig.mEgressDistanceMmSet) {
            req.baseConfig.distanceEgressCm = (char) Math.min(
                    subscribeConfig.mEgressDistanceMm / 10, Short.MAX_VALUE);
            req.baseConfig.configRangingIndications |= NanRangingIndication.EGRESS_MET_MASK;
        }
        if (subscribeConfig.mIngressDistanceMmSet) {
            req.baseConfig.distanceIngressCm = (char) Math.min(
                    subscribeConfig.mIngressDistanceMm / 10, Short.MAX_VALUE);
            req.baseConfig.configRangingIndications |= NanRangingIndication.INGRESS_MET_MASK;
        }

        // TODO: configure security
        req.baseConfig.securityConfig = new NanDataPathSecurityConfig();
        req.baseConfig.securityConfig.securityType =
            NanDataPathSecurityConfig.NanDataPathSecurityType.OPEN;
        req.baseConfig.securityConfig.pmk = new byte[32];
        req.baseConfig.securityConfig.passphrase = new byte[0];
        req.baseConfig.securityConfig.scid = new byte[16];

        req.baseConfig.enableSessionSuspendability = SdkLevel.isAtLeastU()
                && subscribeConfig.isSuspendable();

        req.subscribeType = subscribeConfig.mSubscribeType;
        req.pairingConfig = createAidlPairingConfig(subscribeConfig.getPairingConfig());
        if (subscribeConfig.getPairingConfig() != null) {
            req.baseConfig.securityConfig.cipherType = getSupplicantCipherSuites(
                    subscribeConfig.getPairingConfig().getSupportedCipherSuites());
            enableFrameProtection(req.baseConfig.securityConfig);
        }
        req.identityKey = copyArray(nik, 16);

        if (SdkLevel.isAtLeastV() && !subscribeConfig.getVendorData().isEmpty()) {
            req.vendorData =
                    HalAidlUtil.frameworkToHalOuiKeyedDataList(subscribeConfig.getVendorData());
        }

        if (subscribeConfig.mPeriodicRangingEnabled) {
            req.baseConfig.configRangingIndications |=
                    NanRangingIndication.CONTINUOUS_INDICATION_MASK;
            req.baseConfig.rangingIntervalMs = subscribeConfig.mPeriodicRangingInterval;
            req.baseConfig.rttBurstSize = subscribeConfig.mRttBurstSize;
            req.baseConfig.preamble = WifiRttControllerAidlImpl
                    .frameworkToHalResponderPreamble(subscribeConfig.mPreamble);
            req.baseConfig.channelInfo = new WifiChannelInfo();
            req.baseConfig.channelInfo.width = WifiRttControllerAidlImpl
                    .frameworkToHalChannelWidth(subscribeConfig.mChannelWidth);
            req.baseConfig.channelInfo.centerFreq = subscribeConfig.mFrequencyMhz;
            req.baseConfig.channelInfo.centerFreq0 = subscribeConfig.mCenterFrequency0Mhz;
            req.baseConfig.channelInfo.centerFreq1 = subscribeConfig.mCenterFrequency1Mhz;
        }

        return req;
    }

    private static NanPairingConfig createAidlPairingConfig(
            @Nullable AwarePairingConfig pairingConfig) {
        NanPairingConfig config = new NanPairingConfig();
        if (pairingConfig == null) {
            return config;
        }
        config.enablePairingCache = pairingConfig.isPairingCacheEnabled();
        config.enablePairingSetup = pairingConfig.isPairingSetupEnabled();
        config.enablePairingVerification = pairingConfig.isPairingVerificationEnabled();
        config.supportedBootstrappingMethods =
                getSupplicantBootstrappingMethods(pairingConfig.getBootstrappingMethods());
        return config;
    }

    private static NanTransmitFollowupRequest createNanTransmitFollowupRequest(
            byte pubSubId, int requesterInstanceId, MacAddress dest, byte[] message) {
        NanTransmitFollowupRequest req = new NanTransmitFollowupRequest();
        req.discoverySessionId = pubSubId;
        req.peerId = requesterInstanceId;
        req.addr = dest.toByteArray();
        if (message != null && message.length > 255) {
            req.extendedServiceSpecificInfo = copyArray(message);
            req.serviceSpecificInfo = new byte[0];
        } else {
            req.serviceSpecificInfo = copyArray(message);
            req.extendedServiceSpecificInfo = new byte[0];
        }
        req.disableFollowupResultIndication = false;
        return req;
    }

    private static byte[] copyArray(byte[] source) {
        return copyArray(source, 0);
    }

    private static byte[] copyArray(byte[] source, int length) {
        return source != null && source.length != 0 ? source.clone() : new byte[length];
    }

    private boolean checkIfaceAndLogFailure(String methodStr) {
        if (mWifiNanIface == null) {
            Log.e(TAG, "ISupplicantNanIface." + methodStr + " failed with null iface");
            return false;
        }
        return true;
    }
}
