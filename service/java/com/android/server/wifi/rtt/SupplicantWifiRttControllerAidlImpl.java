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

package com.android.server.wifi.rtt;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.RequiresNoPermission;
import android.annotation.SuppressLint;
import android.hardware.wifi.common.OuiKeyedData;
import android.hardware.wifi.supplicant.ISupplicantWifiRttControllerEventCallback;
import android.hardware.wifi.supplicant.KeyMgmtMask;
import android.hardware.wifi.supplicant.PairwiseCipherMask;
import android.hardware.wifi.supplicant.ProximityRangingConfig;
import android.hardware.wifi.supplicant.RttBw;
import android.hardware.wifi.supplicant.RttCapabilities;
import android.hardware.wifi.supplicant.RttConfig;
import android.hardware.wifi.supplicant.RttPreamble;
import android.hardware.wifi.supplicant.RttResult;
import android.hardware.wifi.supplicant.RttResult.RttStatus;
import android.hardware.wifi.supplicant.RttSecureConfig;
import android.hardware.wifi.supplicant.RttType;
import android.net.MacAddress;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiAnnotations;
import android.net.wifi.rtt.ContinuousRangingResultCallback;
import android.net.wifi.rtt.PasnConfig;
import android.net.wifi.rtt.ProximityDetectionConfig;
import android.net.wifi.rtt.RangingRequest;
import android.net.wifi.rtt.RangingResult;
import android.net.wifi.rtt.ResponderConfig;
import android.net.wifi.rtt.ResponderLocation;
import android.net.wifi.rtt.SecureRangingConfig;
import android.os.RemoteException;
import android.os.ServiceSpecificException;
import android.util.Log;

import com.android.server.wifi.hal.WifiRttController;
import com.android.server.wifi.util.HalAidlUtil;

import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * AIDL implementation of the ISupplicantWifiRttController interface.
 */
@SuppressLint("NewApi")
public class SupplicantWifiRttControllerAidlImpl implements ISupplicantWifiRttController {
    private static final String TAG = "SupplicantWifiRttControllerAidlImpl";
    private boolean mVerboseLoggingEnabled = false;
    /**
     * Number of microseconds in a 10-millisecond unit.
     */
    private static final int MICROS_IN_10_MILLIS_UNIT = 10000;
    /**
     * Number of microseconds in a 100-microsecond unit.
     */
    private static final int MICROS_IN_100_MICROS_UNIT = 100;

    private android.hardware.wifi.supplicant.ISupplicantWifiRttController mWifiRttController;
    private SupplicantWifiRttController.ProximityRangingCapabilities mPrCapabilities;
    private SupplicantWifiRttControllerEventCallback mHalCallback;
    private Set<SupplicantWifiRttController.SupplicantWifiRttControllerEventCallback>
            mRttEventCallbacks = new HashSet<>();
    private final Object mLock = new Object();

    public SupplicantWifiRttControllerAidlImpl(
            @NonNull android.hardware.wifi.supplicant.ISupplicantWifiRttController rttController) {
        mWifiRttController = rttController;
        mHalCallback = new SupplicantWifiRttControllerEventCallback();
    }

    /**
     * See comments for {@link ISupplicantWifiRttController#setup()}
     */
    @Override
    public boolean setup() {
        final String methodStr = "setup";
        synchronized (mLock) {
            if (!checkIfaceAndLogFailure(methodStr)) return false;
            try {
                mWifiRttController.registerEventCallback(mHalCallback);
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
                return false;
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
                return false;
            }
            if (!updateRttCapabilities()) {
                return false;
            }
            if (mVerboseLoggingEnabled) {
                Log.v(TAG, "setup: mPrCapabilities=" + mPrCapabilities);
            }
            return true;
        }
    }

    /**
     * See comments for {@link ISupplicantWifiRttController#enableVerboseLogging(boolean)}
     */
    @Override
    public void enableVerboseLogging(boolean verbose) {
        synchronized (mLock) {
            mVerboseLoggingEnabled = verbose;
        }
    }

    /**
     * See comments for {@link ISupplicantWifiRttController#registerRttEventCallback(
     * SupplicantWifiRttController.SupplicantWifiRttControllerEventCallback)}
     */
    @Override
    public void registerRttEventCallback(
            SupplicantWifiRttController.SupplicantWifiRttControllerEventCallback callback) {
        synchronized (mLock) {
            if (!mRttEventCallbacks.add(callback)) {
                Log.e(TAG, "Ranging results callback was already registered");
            }
        }
    }

    /**
     * See comments for {@link ISupplicantWifiRttController#validate()}
     */
    @Override
    public boolean validate() {
        final String methodStr = "validate";
        synchronized (mLock) {
            if (!checkIfaceAndLogFailure(methodStr)) return false;
            try {
                // Just check that we can call this method successfully.
                mWifiRttController.getName();
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
     * See comments for {@link ISupplicantWifiRttController#getName()}
     */
    @Override
    public String getName() {
        final String methodStr = "getName";
        synchronized (mLock) {
            if (!checkIfaceAndLogFailure(methodStr)) return null;
            try {
                return mWifiRttController.getName();
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return null;
        }
    }

    /**
     * See comments for {@link ISupplicantWifiRttController#setProximityRangingDeviceName(String)}
     */
    @Override
    public void setProximityRangingDeviceName(String name) {
        final String methodStr = "setProximityRangingDeviceName";
        synchronized (mLock) {
            if (!checkIfaceAndLogFailure(methodStr)) return;
            try {
                mWifiRttController.setProximityRangingDeviceName(name);
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
        }
    }

    /**
     * See comments for {@link ISupplicantWifiRttController#setProximityRangingMacAddress(byte[])}
     */
    @Override
    public void setProximityRangingMacAddress(@NonNull byte[] macAddress) {
        final String methodStr = "setProximityRangingMacAddress";
        synchronized (mLock) {
            if (!checkIfaceAndLogFailure(methodStr)) return;
            try {
                mWifiRttController.setProximityRangingMacAddress(macAddress);
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
        }
    }

    /**
     * See comments for {@link ISupplicantWifiRttController#getProximityRangingMacAddress()}
     */
    @Nullable
    @Override
    public byte[] getProximityRangingMacAddress() {
        final String methodStr = "getProximityRangingMacAddress";
        synchronized (mLock) {
            if (!checkIfaceAndLogFailure(methodStr)) return null;
            try {
                return mWifiRttController.getProximityRangingMacAddress();
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return null;
        }
    }

    /**
     * See comments for {@link ISupplicantWifiRttController#getProximityRangingCapabilities()}
     */
    @Override
    @Nullable
    public SupplicantWifiRttController.ProximityRangingCapabilities
            getProximityRangingCapabilities() {
        final String methodStr = "getProximityRangingCapabilities";
        synchronized (mLock) {
            if (!checkIfaceAndLogFailure(methodStr)) return null;
            if (mPrCapabilities == null && !updateRttCapabilities()) {
                Log.e(TAG, "Failed to get Proximity Ranging capabilities");
                return null;
            }
            return mPrCapabilities;
        }
    }

    /**
     * See comments for {@link ISupplicantWifiRttController#rangeRequest(int, RangingRequest)}
     */
    @Override
    public boolean rangeRequest(int cmdId, @NonNull RangingRequest request) {
        final String methodStr = "rangeRequest";
        synchronized (mLock) {
            if (!checkIfaceAndLogFailure(methodStr)) return false;
            if (mVerboseLoggingEnabled) {
                Log.v(TAG, "rangeRequest: cmdId=" + cmdId + ", # of requests="
                        + request.mRttPeers.size() + ", request=" + request);
            }
            updateRttCapabilities();
            try {
                RttConfig[] rttConfigs =
                        convertRangingRequestToRttConfigs(request, mPrCapabilities);
                if (rttConfigs == null) {
                    Log.e(TAG, methodStr + " received invalid request parameters");
                    return false;
                } else if (rttConfigs.length == 0) {
                    Log.e(TAG, methodStr + " invalidated all requests");
                    dispatchOnRangingResults(cmdId, new ArrayList<>());
                    return true;
                }
                mWifiRttController.rangeRequest(cmdId, rttConfigs);
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
     * See comments for {@link ISupplicantWifiRttController#rangeCancel(int, List)}
     */
    @Override
    public boolean rangeCancel(int cmdId, @NonNull List<MacAddress> macAddresses) {
        final String methodStr = "rangeCancel";
        synchronized (mLock) {
            if (!checkIfaceAndLogFailure(methodStr)) return false;
            try {
                android.hardware.wifi.supplicant.MacAddress[] halAddresses =
                        new android.hardware.wifi.supplicant.MacAddress[macAddresses.size()];
                for (int i = 0; i < macAddresses.size(); i++) {
                    android.hardware.wifi.supplicant.MacAddress halAddress =
                            new android.hardware.wifi.supplicant.MacAddress();
                    halAddress.data = macAddresses.get(i).toByteArray();
                    halAddresses[i] = halAddress;
                }
                mWifiRttController.rangeCancel(cmdId, halAddresses);
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
     * See comments for {@link ISupplicantWifiRttController#dump(PrintWriter)}
     */
    @Override
    public void dump(PrintWriter pw) {
        pw.println("WifiRttController:");
        pw.println("  mIWifiRttController: " + mWifiRttController);
        pw.println("  mPrCapabilities: " + mPrCapabilities);
    }

    /**
     *  Callback for supplicant AIDL events on the SupplicantWifiRttController
     */
    private class SupplicantWifiRttControllerEventCallback extends
            ISupplicantWifiRttControllerEventCallback.Stub {
        @Override
        @RequiresNoPermission
        public void onResults(int cmdId, RttResult[] halResults) {
            if (mVerboseLoggingEnabled) {
                int numResults = halResults != null ? halResults.length : -1;
                Log.v(TAG, "onResults: cmdId=" + cmdId + ", # of results=" + numResults);
            }
            if (halResults == null) {
                halResults = new RttResult[0];
            }
            List<RangingResult> rangingResults = halToFrameworkRangingResults(halResults);
            dispatchOnRangingResults(cmdId, rangingResults);
        }
        @Override
        @RequiresNoPermission
        public void onContinuousRangingStatusChanged(int cmdId, int code) {
            Log.v(TAG, "onContinuousRangingStatusChanged: cmdId=" + cmdId + ", code=" + code);
            for (SupplicantWifiRttController.SupplicantWifiRttControllerEventCallback
                    callback : mRttEventCallbacks) {
                callback.onContinuousRangingStatusChanged(cmdId, 0);
            }
        }
        @Override
        @RequiresNoPermission
        public void onContinuousRangingTerminated(int cmdId, int reason) {
            Log.v(TAG, "onContinuousRangingTerminated: cmdId=" + cmdId + ", reason=" + reason);
            for (SupplicantWifiRttController.SupplicantWifiRttControllerEventCallback
                    callback : mRttEventCallbacks) {
                callback.onContinuousRangingStatusChanged(cmdId,
                        halToFrameworkRangingTerminateReason(reason));
            }
        }

        @Override
        @RequiresNoPermission
        public String getInterfaceHash() {
            return ISupplicantWifiRttControllerEventCallback.HASH;
        }

        @Override
        @RequiresNoPermission
        public int getInterfaceVersion() {
            return ISupplicantWifiRttControllerEventCallback.VERSION;
        }
    }

    // Utilities

    private List<RangingResult> halToFrameworkRangingResults(@NonNull RttResult[] halResults) {
        List<RangingResult> rangingResults = new ArrayList();
        for (RttResult rttResult : halResults) {
            if (rttResult == null) continue;
            byte[] lci = rttResult.lci.data;
            byte[] lcr = rttResult.lcr.data;
            ResponderLocation responderLocation;
            try {
                responderLocation = new ResponderLocation(lci, lcr);
                if (!responderLocation.isValid()) {
                    responderLocation = null;
                }
            } catch (Exception e) {
                responderLocation = null;
                Log.e(TAG, "ResponderLocation: lci/lcr parser failed exception -- " + e);
            }
            if (rttResult.successNumber <= 1 && rttResult.distanceSdMm != 0) {
                if (mVerboseLoggingEnabled) {
                    Log.w(TAG, "postProcessResults: non-zero distance stdev with 0||1 num "
                            + "samples!? result=" + rttResult);
                }
                rttResult.distanceSdMm = 0;
            }
            RangingResult.Builder resultBuilder = new RangingResult.Builder()
                    .setStatus(halToFrameworkRttStatus(rttResult.status))
                    .setMacAddress(MacAddress.fromBytes(rttResult.addr))
                    .setDistanceMm(rttResult.distanceMm)
                    .setDistanceStdDevMm(rttResult.distanceSdMm)
                    .setRssi(rttResult.rssi / -2)
                    .setNumAttemptedMeasurements(rttResult.numberPerBurstPeer)
                    .setNumSuccessfulMeasurements(rttResult.successNumber)
                    .setLci(lci)
                    .setLcr(lcr)
                    .setUnverifiedResponderLocation(responderLocation)
                    .setRangingTimestampMillis(
                            rttResult.timestampUs / SupplicantWifiRttController.CONVERSION_US_TO_MS)
                    .set80211mcMeasurement(rttResult.type == RttType.TWO_SIDED_11MC)
                    .set80211azNtbMeasurement(rttResult.type == RttType.TWO_SIDED_11AZ_NTB_SECURE)
                    .setMeasurementChannelFrequencyMHz(rttResult.channelFrequencyMHz)
                    .setMeasurementBandwidth(halToFrameworkRttPacketBandwidth(rttResult.packetBw))
                    .setMinTimeBetweenNtbMeasurementsMicros(rttResult.ntbMinMeasurementTimeIn100Us
                            * MICROS_IN_100_MICROS_UNIT)
                    .setMaxTimeBetweenNtbMeasurementsMicros(rttResult.ntbMaxMeasurementTimeIn10Ms
                            * MICROS_IN_10_MILLIS_UNIT)
                    .set80211azInitiatorTxLtfRepetitionsCount(rttResult.i2rTxLtfRepetitionCount)
                    .set80211azResponderTxLtfRepetitionsCount(rttResult.r2iTxLtfRepetitionCount)
                    .set80211azNumberOfTxSpatialStreams(rttResult.numTxSpatialStreams)
                    .set80211azNumberOfRxSpatialStreams(rttResult.numRxSpatialStreams)
                    .setRangingAuthenticated((rttResult.baseAkm & ~KeyMgmtMask.PASN) != 0)
                    .setSecureHeLtfEnabled(rttResult.isSecureLtfEnabled)
                    .setSecureHeLtfProtocolVersion(rttResult.secureHeLtfProtocolVersion)
                    .setNominalTimeMillis(rttResult.nominalTimeMs)
                    .setAvailabilityWindowDurationMillis(rttResult.availabilityWindowTimeMs)
                    .setNumNtbRepetitionsPerMeasurement(rttResult.numNtbRepetitionsPerMeasurement)
                    .setLmrDelayed(rttResult.isDelayedLmrEnabled);

            if (rttResult.vendorData != null) {
                resultBuilder.setVendorData(
                        HalAidlUtil.halToFrameworkOuiKeyedDataList(rttResult.vendorData));
            }
            rangingResults.add(resultBuilder.build());
        }
        return rangingResults;
    }

    /**
     * Converts the supplicant HAL's RTT packet bandwidth to the framework's equivalent.
     */
    public static @WifiAnnotations.ChannelWidth int halToFrameworkRttPacketBandwidth(
            @RttBw int packetBw) {
        return switch (packetBw) {
            case RttBw.BW_20MHZ -> ScanResult.CHANNEL_WIDTH_20MHZ;
            case RttBw.BW_40MHZ -> ScanResult.CHANNEL_WIDTH_40MHZ;
            case RttBw.BW_80MHZ -> ScanResult.CHANNEL_WIDTH_80MHZ;
            case RttBw.BW_160MHZ -> ScanResult.CHANNEL_WIDTH_160MHZ;
            case RttBw.BW_320MHZ -> ScanResult.CHANNEL_WIDTH_320MHZ;
            default -> RangingResult.UNSPECIFIED;
        };
    }

    /**
     * Converts the supplicant HAL's Preamble to the framework's equivalent.
     */
    public static byte halToFrameworkPreamble(@RttPreamble int preamble)
            throws IllegalArgumentException {
        return switch (preamble) {
            case RttPreamble.LEGACY -> ResponderConfig.PREAMBLE_LEGACY;
            case RttPreamble.HT -> ResponderConfig.PREAMBLE_HT;
            case RttPreamble.VHT -> ResponderConfig.PREAMBLE_VHT;
            case RttPreamble.HE -> ResponderConfig.PREAMBLE_HE;
            case RttPreamble.EHT -> ResponderConfig.PREAMBLE_EHT;
            default -> throw new IllegalArgumentException(
                    "halToFrameworkPreamble: bad " + preamble);
        };
    }

    /**
     * Converts the supplicant HAL's RTT status to the framework's equivalent.
     */
    public static int halToFrameworkRttStatus(
            int halStatus) throws IllegalArgumentException {
        return switch (halStatus) {
            case RttStatus.SUCCESS -> RangingResult.STATUS_SUCCESS;
            case RttStatus.FAILURE -> RangingResult.STATUS_FAIL;
            default -> throw new IllegalArgumentException(
                    "halToFrameworkRttStatus: bad " + halStatus);
        };
    }

    private boolean updateRttCapabilities() {
        final String methodStr = "updateRttCapabilities";
        if (mVerboseLoggingEnabled) {
            Log.v(TAG, "updateRttCapabilities");
        }
        if (mPrCapabilities != null) {
            return true;
        }
        try {
            RttCapabilities halCapabilities = mWifiRttController.getCapabilities();
            if (halCapabilities == null) {
                Log.e(TAG, "Failed to get Supplicant RTT Capabilities");
                return false;
            }
            if (halCapabilities.prDeviceInfo != null
                    && halCapabilities.prDeviceInfo.protocolInfo != null) {
                mPrCapabilities = new SupplicantWifiRttController
                        .ProximityRangingCapabilities(halCapabilities.prDeviceInfo);
            } else {
                Log.e(TAG, "Failed to get RTT Proximity Ranging Capabilities");
                return false;
            }
        } catch (RemoteException e) {
            handleRemoteException(e, methodStr);
            return false;
        } catch (ServiceSpecificException e) {
            handleServiceSpecificException(e, methodStr);
            return false;
        }

        // TODO add more conditions
        if (mPrCapabilities != null
                && mPrCapabilities.maxNumContinuousRangingSeekerSessions == 0) {
            Log.wtf(TAG, "Device reports no continuous ranging seeker sessions supported."
                    + "Capabilities: " + mPrCapabilities);
            return false;
        }
        return true;
    }

    private static RttConfig[] convertRangingRequestToRttConfigs(RangingRequest request,
            SupplicantWifiRttController.ProximityRangingCapabilities cap) {
        List<RttConfig> rttConfigs = new ArrayList<>();

        // Skip any configurations which have an error (just print out a message).
        // The caller will only get results for valid configurations.
        for (ResponderConfig responder: request.mRttPeers) {
            RttConfig config = new RttConfig();
            config.addr = responder.macAddress.toByteArray();

            OuiKeyedData[] vendorData = null;
            if (request.getVendorData() != null
                    && !request.getVendorData().isEmpty()) {
                vendorData = HalAidlUtil.frameworkToHalOuiKeyedDataList(request.getVendorData());
            }

            try {
                if (cap != null) {
                    if (responder.supports80211azNtb && (cap.isNtbNonSecureLtfRangingSupported
                            || cap.isNtbSecureLtfRangingSupported)) {
                        config.type = RttType.TWO_SIDED_11AZ_NTB_SECURE;
                    } else if (responder.supports80211mc && cap.is80211mcBasedRangingSupported) {
                        // IEEE 802.11mc is supported by the device
                        config.type = RttType.TWO_SIDED_11MC;
                    } else {
                        Log.w(TAG, "Device doesn't support RTT");
                        continue;
                    }
                } else {
                    if (responder.supports80211azNtb) {
                        // IEEE 802.11mc is supported by the device
                        config.type = RttType.TWO_SIDED_11AZ_NTB_SECURE;
                    } else {
                        config.type = RttType.TWO_SIDED_11MC;
                    }
                }

                config.peer = frameworkToHalRttPeerType(responder.responderType);
                config.bw = frameworkToHalRttPacketBandwidth(responder.channelWidth);
                config.preamble = frameworkToHalResponderPreamble(responder.preamble);
                config.vendorData = vendorData;
                validateBwAndPreambleCombination(config.bw, config.preamble);
                // ResponderConfig#ntbMaxMeasurementTime is in units of 10 milliseconds
                config.ntbMaxMeasurementTimeIn10Millis = responder
                        .getNtbMaxTimeBetweenMeasurementsMicros() / MICROS_IN_10_MILLIS_UNIT;
                // ResponderConfig#ntbMinMeasurementTime is in units of 100 microseconds
                config.ntbMinMeasurementTimeIn100Us = responder
                        .getNtbMinTimeBetweenMeasurementsMicros() / MICROS_IN_100_MICROS_UNIT;
                config.mustRequestLci = true;
                config.mustRequestLcr = true;
                config.numFramesPerBurst = (byte) request.mRttBurstSize;
                config.numNtbRepetitionsPerMeasurement = (byte) request.mRttBurstSize;
                config.numRetriesPerFtmr = 3;
                config.burstDuration = (byte) WifiRttController.getOptimumBurstDuration(
                        request.mRttBurstSize);

                // constrain parameters per device capabilities
                if (cap != null) {
                    config.bw = halRttChannelBandwidthCapabilityLimiter(config.bw, cap,
                            config.type);
                    config.preamble = halRttPreambleCapabilityLimiter(config.preamble, cap,
                                config.type, responder.frequency);
                }
                boolean success = addSecureRangingConfig(config, responder.getSecureRangingConfig(),
                            cap, request.getSecurityMode());
                if (!success) {
                    Log.e(TAG, "Failed to add secure config");
                    continue;
                }
                success = addProximityRangingConfig(config,
                        responder.getProximityDetectionConfig());
                if (!success) {
                    Log.e(TAG, "Failed to add Proximity Ranging config");
                    continue;
                }
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "Invalid configuration: " + e.getMessage());
                continue;
            }

            rttConfigs.add(config);
        }

        RttConfig[] configArray = new RttConfig[rttConfigs.size()];
        for (int i = 0; i < rttConfigs.size(); i++) {
            configArray[i] = rttConfigs.get(i);
        }
        return configArray;
    }

    private static boolean addProximityRangingConfig(RttConfig halRttConfig,
            ProximityDetectionConfig pdConfig) {
        if (halRttConfig == null || pdConfig == null) {
            return false;
        }
        halRttConfig.pdConfig = new ProximityRangingConfig();
        halRttConfig.pdConfig.rangingServiceRole =
                frameworkToHalRangingServiceRole(pdConfig.getRangingServiceRole());
        halRttConfig.pdConfig.discoveryChannelFrequencyMhz =
                pdConfig.getDiscoveryChannelFrequencyMhz();
        halRttConfig.pdConfig.preferredRangingChannelFrequencyMhz =
                pdConfig.getPreferredRangingChannelFrequencyMhz();
        halRttConfig.pdConfig.continuousRangingIntervalMillis =
                pdConfig.getContinuousRangingIntervalMillis();
        halRttConfig.pdConfig.continuousRangingSessionTimeMillis = 120000;
        halRttConfig.pdConfig.advertiserRequiresRangeReport =
                pdConfig.isAdvertiserRequireRangeResult();

        try {
            // Convert from millimeters to centimeters for the HAL
            halRttConfig.pdConfig.distanceIngressCm = pdConfig.getIngressDistanceMm() / 10;
            halRttConfig.pdConfig.configRangingIndications |= ProximityRangingConfig
                    .ProximityRangingIndication.INGRESS_MET_MASK;
        } catch (IllegalStateException e) {
            halRttConfig.pdConfig.distanceIngressCm = 0; // Default if not set
        }

        try {
            // Convert from millimeters to centimeters for the HAL
            halRttConfig.pdConfig.distanceEgressCm = pdConfig.getEgressDistanceMm() / 10;
            halRttConfig.pdConfig.configRangingIndications |= ProximityRangingConfig
                    .ProximityRangingIndication.EGRESS_MET_MASK;
        } catch (IllegalStateException e) {
            halRttConfig.pdConfig.distanceEgressCm = 0; // Default if not set
        }
        return true;
    }

    /**
     * Converts the framework's ranging service role to the supplicant HAL's equivalent.
     */
    private static int frameworkToHalRangingServiceRole(
            @ProximityDetectionConfig.RangingServiceRole int serviceRole) {
        return switch (serviceRole) {
            case ProximityDetectionConfig.RANGING_SERVICE_ROLE_SEEKER ->
                    ProximityRangingConfig.RangingServiceRole.SEEKER;
            case ProximityDetectionConfig.RANGING_SERVICE_ROLE_ADVERTISER ->
                    ProximityRangingConfig.RangingServiceRole.ADVERTISER;
            default -> throw new IllegalArgumentException("Unknown ranging service role: "
                    + serviceRole);
        };
    }

    /**
     * Add secure ranging, if supported.
     */
    private static boolean addSecureRangingConfig(RttConfig halRttConfig,
            SecureRangingConfig secureConfig,
            SupplicantWifiRttController.ProximityRangingCapabilities cap,
            @RangingRequest.SecurityMode int securityMode) {
        if (halRttConfig == null || secureConfig == null || cap == null) {
            return false;
        }
        // An open security mode does not require security
        if (securityMode == RangingRequest.SECURITY_MODE_OPEN) {
            return false;
        }
        // Check PASN configuration.
        PasnConfig pasnConfig = secureConfig.getPasnConfig();
        @PasnConfig.AkmType int baseAkm = pasnConfig.getBaseAkms();
        @PasnConfig.Cipher int cipherSuite = pasnConfig.getCiphers();
        // Responder and device need to support a valid base AKM and cipher suite.
        if (baseAkm == PasnConfig.AKM_NONE || cipherSuite == PasnConfig.CIPHER_NONE) {
            Log.e(TAG, "AKM/CIPHERS not compatible, skip secure ranging");
            return false;
        }
        halRttConfig.secureConfig = new RttSecureConfig();
        halRttConfig.secureConfig.enableSecureHeLtf =
                secureConfig.isSecureHeLtfEnabled() && cap.isNtbSecureLtfRangingSupported;
        halRttConfig.secureConfig.pasnConfig = new android.hardware.wifi.supplicant.PasnConfig();
        halRttConfig.secureConfig.pasnConfig.cipherSuite =
                convertFrameworkCipherSuiteToHal(cipherSuite);
        halRttConfig.secureConfig.pasnConfig.baseAkm = convertFrameworkAkmToHal(baseAkm);
        var passphrase = pasnConfig.getPassword();
        if (passphrase != null) {
            halRttConfig.secureConfig.pasnConfig.passphrase = passphrase.getBytes(
                    StandardCharsets.UTF_8);
        }
        halRttConfig.secureConfig.pasnConfig.pmk = pasnConfig.getPmk() != null
                ? pasnConfig.getPmk() : new byte[0];
        halRttConfig.secureConfig.pasnConfig.devIk =
                new android.hardware.wifi.supplicant.DeviceIdentityKey();
        halRttConfig.secureConfig.pasnConfig.devIk.data =
                pasnConfig.getProximityDetectionSeekerDeviceIdentityKey() != null
                        ? pasnConfig.getProximityDetectionSeekerDeviceIdentityKey() : new byte[0];
        return true;
    }

    /**
     * Converts the framework's most secure cipher to the supplicant HAL's equivalent.
     */
    private static int convertFrameworkCipherSuiteToHal(@PasnConfig.Cipher int requiredCiphers) {
        if ((requiredCiphers & PasnConfig.CIPHER_GCMP_256) != 0) {
            return PairwiseCipherMask.GCMP_256;
        }
        if ((requiredCiphers & PasnConfig.CIPHER_GCMP_128) != 0) {
            return PairwiseCipherMask.GCMP_128;
        }
        if ((requiredCiphers & PasnConfig.CIPHER_CCMP_256) != 0) {
            return PairwiseCipherMask.CCMP_256;
        }
        if ((requiredCiphers & PasnConfig.CIPHER_CCMP_128) != 0) {
            return PairwiseCipherMask.CCMP;
        }
        return PairwiseCipherMask.NONE;
    }

    /**
     * Converts the framework's most secure AKM to the supplicant HAL's equivalent.
     */
    private static int convertFrameworkAkmToHal(@PasnConfig.AkmType int requiredAkms) {
        if ((requiredAkms & PasnConfig.AKM_SAE) != 0) {
            return KeyMgmtMask.SAE;
        }
        if ((requiredAkms & PasnConfig.AKM_PASN) != 0) {
            return KeyMgmtMask.PASN;
        }
        return PasnConfig.AKM_NONE;
    }

    private static void validateBwAndPreambleCombination(int bw, int preamble) {
        if (bw <= RttBw.BW_20MHZ) {
            return;
        }
        if (bw == RttBw.BW_40MHZ && preamble >= RttPreamble.HT) {
            return;
        }
        if (bw == RttBw.BW_320MHZ && preamble == RttPreamble.EHT) {
            return;
        }
        if (bw >= RttBw.BW_80MHZ && bw < RttBw.BW_320MHZ && preamble >= RttPreamble.VHT) {
            return;
        }
        throw new IllegalArgumentException(
                "bw and preamble combination is invalid, bw: " + bw + " preamble: " + preamble);
    }

    /**
     * Converts the framework's peer type to the supplicant HAL's equivalent.
     */
    private static int frameworkToHalRttPeerType(int responderType)
            throws IllegalArgumentException {
        switch (responderType) {
            case ResponderConfig.RESPONDER_STA:
                return RttConfig.RttPeerType.STA;
            case ResponderConfig.RESPONDER_P2P_GO:
            case ResponderConfig.RESPONDER_P2P_CLIENT:
            case ResponderConfig.RESPONDER_AWARE:
            case ResponderConfig.RESPONDER_AP:
            default:
                throw new IllegalArgumentException(
                        "frameworkToSupplicantHalRttPeerType: bad " + responderType);
        }
    }

    /**
     * Converts the framework's packet bandwidth to the supplicant HAL's equivalent.
     */
    private static byte frameworkToHalRttPacketBandwidth(int responderChannelWidth)
            throws IllegalArgumentException {
        return switch (responderChannelWidth) {
            case ResponderConfig.CHANNEL_WIDTH_20MHZ -> RttBw.BW_20MHZ;
            case ResponderConfig.CHANNEL_WIDTH_40MHZ -> RttBw.BW_40MHZ;
            case ResponderConfig.CHANNEL_WIDTH_80MHZ -> RttBw.BW_80MHZ;
            case ResponderConfig.CHANNEL_WIDTH_160MHZ,
                    ResponderConfig.CHANNEL_WIDTH_80MHZ_PLUS_MHZ -> RttBw.BW_160MHZ;
            case ResponderConfig.CHANNEL_WIDTH_320MHZ -> RttBw.BW_320MHZ;
            default -> throw new IllegalArgumentException(
                    "halRttChannelBandwidthFromSupplicantHalBandwidth: bad "
                            + responderChannelWidth);
        };
    }

    /**
     * Converts the framework's Preamble to the supplicant HAL's equivalent.
     */
    public static byte frameworkToHalResponderPreamble(int responderPreamble)
            throws IllegalArgumentException {
        return switch (responderPreamble) {
            case ResponderConfig.PREAMBLE_LEGACY -> RttPreamble.LEGACY;
            case ResponderConfig.PREAMBLE_HT -> RttPreamble.HT;
            case ResponderConfig.PREAMBLE_VHT -> RttPreamble.VHT;
            case ResponderConfig.PREAMBLE_HE -> RttPreamble.HE;
            case ResponderConfig.PREAMBLE_EHT -> RttPreamble.EHT;
            default -> throw new IllegalArgumentException(
                    "frameworkToHalResponderPreamble: bad " + responderPreamble);
        };
    }

    /**
     * Converts the supplicant HAL's termination reason code to the framework's equivalent.
     */
    private static @ContinuousRangingResultCallback.RangingTerminateReason int
            halToFrameworkRangingTerminateReason(int halReason) {
        switch (halReason) {
            case ISupplicantWifiRttControllerEventCallback
                         .ContinuousRangingTerminateReasonCode.TIMEOUT:
                return ContinuousRangingResultCallback.TERMINATE_REASON_TIMEOUT;
            case ISupplicantWifiRttControllerEventCallback
                         .ContinuousRangingTerminateReasonCode.USER_REQUEST:
                return ContinuousRangingResultCallback.TERMINATE_REASON_USER_REQUEST;
            case ISupplicantWifiRttControllerEventCallback
                         .ContinuousRangingTerminateReasonCode.ABORT_CONCURRENCY:
                return ContinuousRangingResultCallback.TERMINATE_REASON_ABORT_CONCURRENCY;
            case ISupplicantWifiRttControllerEventCallback
                         .ContinuousRangingTerminateReasonCode.RECEIVED_RTT_TERMINATE:
                return ContinuousRangingResultCallback.TERMINATE_REASON_RECEIVED_RTT_TERMINATE;
            case ISupplicantWifiRttControllerEventCallback
                         .ContinuousRangingTerminateReasonCode.PR_RANGE_NEG_FAILED:
                // Note: No direct mapping in framework, falling back to UNKNOWN.
                // TODO call onRangingFailure(FAILURE_REASON_RTT_PD_NEGOTIATION_FAILED)
            default:
                return ContinuousRangingResultCallback.TERMINATE_REASON_UNKNOWN;
        }
    }

    /**
     * Check whether the selected RTT channel bandwidth is supported by the device.
     * If supported, return the requested bandwidth.
     * If not supported, return the next lower bandwidth which is supported.
     * If none, throw an IllegalArgumentException.
     *
     * Note: the halRttChannelBandwidth is a single bit flag from the HAL RttBw type.
     */
    private static byte halRttChannelBandwidthCapabilityLimiter(byte halRttChannelBandwidth,
            SupplicantWifiRttController.ProximityRangingCapabilities cap, @RttType int rttType)
            throws IllegalArgumentException {
        byte requestedBandwidth = halRttChannelBandwidth;
        int bwSupported =
                (rttType == RttType.TWO_SIDED_11AZ_NTB_SECURE)
                        ? cap.maxSupportedPacketBandwidthNtb
                        : cap.maxSupportedPacketBandwidth80211mcBased;
        while ((halRttChannelBandwidth != 0) && ((halRttChannelBandwidth & bwSupported) == 0)) {
            halRttChannelBandwidth >>= 1;
        }

        if (halRttChannelBandwidth != 0) {
            return halRttChannelBandwidth;
        }

        throw new IllegalArgumentException(
                "RTT BW=" + requestedBandwidth + ", not supported by device capabilities=" + cap
                        + " - and no supported alternative");
    }

    /**
     * Check whether the selected RTT preamble is supported by the device and the RTT type.
     * <ul>
     * <li>If supported, return the requested preamble.
     * <li>If not supported, return the next "lower" preamble which is supported.
     * <li>If none, throw an IllegalArgumentException.
     * </ul>
     *
     * <p>Note: the halRttPreamble is a single bit flag from the HAL RttPreamble type.
     *
     * <p>Note: The IEEE 802.11mc is only compatible with HE and EHT when using the 6 GHz band.
     * However, the IEEE 802.11az supports HE and EHT across all Wi-Fi bands (2.4GHz, 5 GHz, and
     * 6 GHz).
     */
    private static byte halRttPreambleCapabilityLimiter(byte halRttPreamble,
            SupplicantWifiRttController.ProximityRangingCapabilities cap, @RttType int rttType,
            int frequency)
            throws IllegalArgumentException {
        // Note: requestedPreamble is only used for the error logging
        byte requestedPreamble = halRttPreamble;
        // Since RTT type is limited based on device capability, check preamble for any adjustment.
        // The IEEE 802.11mc is only compatible with HE and EHT when using the 6 GHz band. So
        // adjust the preamble accordingly.
        if (rttType <= RttType.TWO_SIDED_11MC && !ScanResult.is6GHz(frequency)) {
            if (halRttPreamble >= RttPreamble.HE) {
                halRttPreamble = RttPreamble.VHT;
            }
        }
        // Check device capability whether preamble is supported by the device, otherwise adjust it.
        int preambleSupported =
                (rttType == RttType.TWO_SIDED_11AZ_NTB_SECURE)
                        ? cap.maxSupportedPreambleNtb
                        : cap.maxSupportedPreamble80211mcBased;
        while ((halRttPreamble != 0) && ((halRttPreamble & preambleSupported) == 0)) {
            halRttPreamble >>= 1;
        }

        if (halRttPreamble != 0) {
            return halRttPreamble;
        }

        throw new IllegalArgumentException(
                "RTT Preamble=" + requestedPreamble + ", not supported by device capabilities="
                        + cap + " - and no supported alternative");
    }

    private void dispatchOnRangingResults(int cmdId, List<RangingResult> rangingResults) {
        for (SupplicantWifiRttController.SupplicantWifiRttControllerEventCallback
                callback : mRttEventCallbacks) {
            callback.onRangingResults(cmdId, rangingResults);
        }
    }

    private boolean checkIfaceAndLogFailure(String methodStr) {
        if (mWifiRttController == null) {
            Log.e(TAG, "Unable to call " + methodStr + " because iface is null.");
            return false;
        }
        return true;
    }

    private void handleRemoteException(RemoteException e, String methodStr) {
        mWifiRttController = null;
        Log.e(TAG, methodStr + " failed with remote exception: " + e);
    }

    private void handleServiceSpecificException(ServiceSpecificException e, String methodStr) {
        Log.e(TAG, methodStr + " failed with service-specific exception: " + e);
    }
}
