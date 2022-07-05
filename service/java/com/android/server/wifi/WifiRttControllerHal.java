/*
 * Copyright (C) 2017 The Android Open Source Project
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

import android.annotation.IntDef;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.hardware.wifi.V1_0.IWifiRttController;
import android.hardware.wifi.V1_0.IWifiRttControllerEventCallback;
import android.hardware.wifi.V1_0.RttCapabilities;
import android.hardware.wifi.V1_0.RttConfig;
import android.hardware.wifi.V1_0.RttPeerType;
import android.hardware.wifi.V1_0.RttResult;
import android.hardware.wifi.V1_0.RttStatus;
import android.hardware.wifi.V1_0.RttType;
import android.hardware.wifi.V1_0.WifiStatus;
import android.hardware.wifi.V1_0.WifiStatusCode;
import android.hardware.wifi.V1_6.RttBw;
import android.hardware.wifi.V1_6.RttPreamble;
import android.hardware.wifi.V1_6.WifiChannelWidthInMhz;
import android.net.MacAddress;
import android.net.wifi.rtt.RangingRequest;
import android.net.wifi.rtt.RangingResult;
import android.net.wifi.rtt.ResponderConfig;
import android.net.wifi.rtt.ResponderLocation;
import android.os.RemoteException;
import android.util.Log;

import com.android.server.wifi.util.GeneralUtil;
import com.android.server.wifi.util.NativeUtil;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Wrapper class for IWifiRttController HAL calls.
 */
public class WifiRttControllerHal {
    private static final String TAG = "WifiRttControllerHal";
    private static final int CONVERSION_US_TO_MS = 1_000;
    private boolean mVerboseLoggingEnabled = false;

    private IWifiRttController mIWifiRttController;
    private android.hardware.wifi.V1_4.IWifiRttController mIWifiRttController14;
    private android.hardware.wifi.V1_6.IWifiRttController mIWifiRttController16;
    private final WifiRttControllerEventCallback mIWifiRttControllerEventCallback;
    private final WifiRttControllerEventCallback14 mIWifiRttControllerEventCallback14;
    private final WifiRttControllerEventCallback16 mIWifiRttControllerEventCallback16;
    private Capabilities mRttCapabilities;
    private Set<RttControllerRangingResultsCallback> mRangingResultsCallbacks = new HashSet<>();

    /** Unknown status */
    public static final int FRAMEWORK_RTT_STATUS_UNKNOWN = -1;
    /** Success */
    public static final int FRAMEWORK_RTT_STATUS_SUCCESS = 0;
    /** General failure status */
    public static final int FRAMEWORK_RTT_STATUS_FAILURE = 1;
    /** Target STA does not respond to request */
    public static final int FRAMEWORK_RTT_STATUS_FAIL_NO_RSP = 2;
    /** Request rejected. Applies to 2-sided RTT only */
    public static final int FRAMEWORK_RTT_STATUS_FAIL_REJECTED = 3;
    public static final int FRAMEWORK_RTT_STATUS_FAIL_NOT_SCHEDULED_YET = 4;
    /** Timing measurement times out */
    public static final int FRAMEWORK_RTT_STATUS_FAIL_TM_TIMEOUT = 5;
    /** Target on different channel, cannot range */
    public static final int FRAMEWORK_RTT_STATUS_FAIL_AP_ON_DIFF_CHANNEL = 6;
    /** Ranging not supported */
    public static final int FRAMEWORK_RTT_STATUS_FAIL_NO_CAPABILITY = 7;
    /** Request aborted for unknown reason */
    public static final int FRAMEWORK_RTT_STATUS_ABORTED = 8;
    /** Invalid T1-T4 timestamp */
    public static final int FRAMEWORK_RTT_STATUS_FAIL_INVALID_TS = 9;
    /** 11mc protocol failed */
    public static final int FRAMEWORK_RTT_STATUS_FAIL_PROTOCOL = 10;
    /** Request could not be scheduled */
    public static final int FRAMEWORK_RTT_STATUS_FAIL_SCHEDULE = 11;
    /** Responder cannot collaborate at time of request */
    public static final int FRAMEWORK_RTT_STATUS_FAIL_BUSY_TRY_LATER = 12;
    /** Bad request args */
    public static final int FRAMEWORK_RTT_STATUS_INVALID_REQ = 13;
    /** WiFi not enabled. */
    public static final int FRAMEWORK_RTT_STATUS_NO_WIFI = 14;
    /** Responder overrides param info, cannot range with new params */
    public static final int FRAMEWORK_RTT_STATUS_FAIL_FTM_PARAM_OVERRIDE = 15;

    /** @hide */
    @IntDef(prefix = "FRAMEWORK_RTT_STATUS_", value = {FRAMEWORK_RTT_STATUS_UNKNOWN,
            FRAMEWORK_RTT_STATUS_SUCCESS, FRAMEWORK_RTT_STATUS_FAILURE,
            FRAMEWORK_RTT_STATUS_FAIL_NO_RSP, FRAMEWORK_RTT_STATUS_FAIL_REJECTED,
            FRAMEWORK_RTT_STATUS_FAIL_NOT_SCHEDULED_YET, FRAMEWORK_RTT_STATUS_FAIL_TM_TIMEOUT,
            FRAMEWORK_RTT_STATUS_FAIL_AP_ON_DIFF_CHANNEL, FRAMEWORK_RTT_STATUS_FAIL_NO_CAPABILITY,
            FRAMEWORK_RTT_STATUS_ABORTED, FRAMEWORK_RTT_STATUS_FAIL_INVALID_TS,
            FRAMEWORK_RTT_STATUS_FAIL_PROTOCOL, FRAMEWORK_RTT_STATUS_FAIL_SCHEDULE,
            FRAMEWORK_RTT_STATUS_FAIL_BUSY_TRY_LATER, FRAMEWORK_RTT_STATUS_INVALID_REQ,
            FRAMEWORK_RTT_STATUS_NO_WIFI, FRAMEWORK_RTT_STATUS_FAIL_FTM_PARAM_OVERRIDE})
    @Retention(RetentionPolicy.SOURCE)
    public @interface FrameworkRttStatus {}

    /**
     * Callback to receive ranging results.
     */
    public interface RttControllerRangingResultsCallback {
        /**
         * Called when ranging results are received from the HAL.
         *
         * @param cmdId Command ID specified in the original request.
         * @param rangingResults A list of range results.
         */
        void onRangingResults(int cmdId, List<RangingResult> rangingResults);
    }

    public WifiRttControllerHal(@NonNull IWifiRttController rttController) {
        mIWifiRttController = rttController;
        mIWifiRttController14 = getRttControllerV1_4();
        mIWifiRttController16 = getRttControllerV1_6();
        mIWifiRttControllerEventCallback = new WifiRttControllerEventCallback();
        mIWifiRttControllerEventCallback14 = new WifiRttControllerEventCallback14();
        mIWifiRttControllerEventCallback16 = new WifiRttControllerEventCallback16();
    }

    /**
     * Set up the IWifiRttController.
     */
    public boolean setup() {
        final String methodStr = "setup";
        if (!checkRttControllerAndLogFailure(methodStr)) {
            return false;
        }

        try {
            if (mIWifiRttController16 != null) {
                mIWifiRttController16.registerEventCallback_1_6(
                        mIWifiRttControllerEventCallback16);
            } else if (mIWifiRttController14 != null) {
                mIWifiRttController14.registerEventCallback_1_4(
                        mIWifiRttControllerEventCallback14);
            } else {
                mIWifiRttController.registerEventCallback(
                        mIWifiRttControllerEventCallback);
            }
        } catch (RemoteException e) {
            Log.e(TAG, "Exception registering callback: " + e);
            return false;
        }
        updateRttCapabilities();
        return true;
    }

    /**
     * Enable/Disable verbose logging.
     *
     */
    public void enableVerboseLogging(boolean verboseEnabled) {
        mVerboseLoggingEnabled = verboseEnabled;
    }

    /**
     * Register a callback to receive ranging results.
     * @param callback Callback to register.
     */
    public void registerRangingResultsCallback(RttControllerRangingResultsCallback callback) {
        if (!mRangingResultsCallbacks.add(callback)) {
            Log.e(TAG, "Ranging results callback was already registered");
        }
    }

    /**
     * Check whether the RTT controller is valid.
     */
    public boolean validate() {
        final String methodStr = "validate";
        if (!checkRttControllerAndLogFailure(methodStr)) {
            return false;
        }

        GeneralUtil.Mutable<Boolean> isRttControllerValid = new GeneralUtil.Mutable<>(false);
        try {
            mIWifiRttController.getBoundIface(
                    (status, iface) -> {
                        if (status.code == WifiStatusCode.SUCCESS) {
                            isRttControllerValid.value = true;
                        }
                    });
        } catch (RemoteException e) {
            Log.e(TAG, "RttController: exception getBoundIface " + e);
        }
        return isRttControllerValid.value;
    }

    /**
     * Returns the RTT capabilities.
     */
    public @Nullable Capabilities getRttCapabilities() {
        return mRttCapabilities;
    }

    /**
     * Updates the RTT capabilities.
     */
    private void updateRttCapabilities() {
        final String methodStr = "updateRttCapabilities";
        if (!checkRttControllerAndLogFailure(methodStr)) {
            return;
        }
        if (mRttCapabilities != null) {
            return;
        }
        if (mVerboseLoggingEnabled) Log.v(TAG, "updateRttCapabilities");

        try {
            if (mIWifiRttController16 != null) {
                mIWifiRttController16.getCapabilities_1_6(
                        (status, capabilities16) -> {
                            if (status.code != WifiStatusCode.SUCCESS) {
                                Log.e(TAG, "updateRttCapabilities:"
                                        + " error requesting capabilities "
                                        + "-- code=" + status.code);
                                return;
                            }
                            if (mVerboseLoggingEnabled) {
                                Log.v(TAG, "updateRttCapabilities: RTT capabilities="
                                        + capabilities16);
                            }
                            mRttCapabilities = new Capabilities(capabilities16);
                        });
            } else if (mIWifiRttController14 != null) {
                mIWifiRttController14.getCapabilities_1_4(
                        (status, capabilities14) -> {
                            if (status.code != WifiStatusCode.SUCCESS) {
                                Log.e(TAG, "updateRttCapabilities:"
                                        + " error requesting capabilities "
                                        + "-- code=" + status.code);
                                return;
                            }
                            if (mVerboseLoggingEnabled) {
                                Log.v(TAG, "updateRttCapabilities: RTT capabilities="
                                        + capabilities14);
                            }
                            mRttCapabilities = new Capabilities(capabilities14);
                        });
            } else {
                mIWifiRttController.getCapabilities(
                        (status, capabilities) -> {
                            if (status.code != WifiStatusCode.SUCCESS) {
                                Log.e(TAG, "updateRttCapabilities:"
                                        + " error requesting capabilities "
                                        + "-- code=" + status.code);
                                return;
                            }
                            if (mVerboseLoggingEnabled) {
                                Log.v(TAG, "updateRttCapabilities: RTT capabilities="
                                        + capabilities);
                            }
                            mRttCapabilities = new Capabilities(capabilities);
                        });

            }
        } catch (RemoteException e) {
            Log.e(TAG, "updateRttCapabilities: exception requesting capabilities: " + e);
        }

        if (mRttCapabilities != null && !mRttCapabilities.rttFtmSupported) {
            Log.wtf(TAG, "Firmware indicates RTT is not supported - but device supports RTT - "
                    + "ignored!?");
        }
    }

    /**
     * Issue a range request to the HAL.
     *
     * @param cmdId Command ID for the request. Will be used in the corresponding response from
     * {@link HalDeviceManager.InterfaceRttControllerLifecycleCallback} onRangingResults()
     * @param request Range request.
     * @return true for success, false for failure.
     */
    public boolean rangeRequest(int cmdId, RangingRequest request) {
        if (mVerboseLoggingEnabled) {
            Log.v(TAG, "rangeRequest: cmdId=" + cmdId + ", # of requests="
                    + request.mRttPeers.size() + ", request=" + request);
        }
        final String methodStr = "rangeRequest";
        if (!checkRttControllerAndLogFailure(methodStr)) {
            return false;
        }

        updateRttCapabilities();
        if (mIWifiRttController16 != null) {
            return sendRangeRequest16(cmdId, request);
        } else if (mIWifiRttController14 != null) {
            return sendRangeRequest14(cmdId, request);
        } else {
            return sendRangeRequest(cmdId, request);
        }
    }

    private void dispatchOnRangingResults(int cmdId, List<RangingResult> rangingResults) {
        for (RttControllerRangingResultsCallback callback : mRangingResultsCallbacks) {
            callback.onRangingResults(cmdId, rangingResults);
        }
    }

    private boolean sendRangeRequest(int cmdId, RangingRequest request) {
        ArrayList<RttConfig> rttConfig =
                convertRangingRequestToRttConfigs(request, mRttCapabilities);
        if (rttConfig == null) {
            Log.e(TAG, "sendRangeRequest: invalid request parameters");
            return false;
        }
        if (rttConfig.size() == 0) {
            Log.e(TAG, "sendRangeRequest: all requests invalidated");
            dispatchOnRangingResults(cmdId, new ArrayList<>());
            return true;
        }

        try {
            WifiStatus status = mIWifiRttController.rangeRequest(cmdId, rttConfig);
            if (status.code != WifiStatusCode.SUCCESS) {
                Log.e(TAG, "sendRangeRequest: cannot issue range request -- code=" + status.code);
                return false;
            }
        } catch (RemoteException e) {
            Log.e(TAG, "sendRangeRequest: exception issuing range request: " + e);
            return false;
        }

        return true;
    }

    private boolean sendRangeRequest14(int cmdId, RangingRequest request) {
        ArrayList<android.hardware.wifi.V1_4.RttConfig> rttConfig =
                convertRangingRequestToRttConfigs14(request, mRttCapabilities);
        if (rttConfig == null) {
            Log.e(TAG, "sendRangeRequest14: invalid request parameters");
            return false;
        }
        if (rttConfig.size() == 0) {
            Log.e(TAG, "sendRangeRequest14: all requests invalidated");
            dispatchOnRangingResults(cmdId, new ArrayList<>());
            return true;
        }

        try {
            WifiStatus status = mIWifiRttController14.rangeRequest_1_4(cmdId, rttConfig);
            if (status.code != WifiStatusCode.SUCCESS) {
                Log.e(TAG, "sendRangeRequest14: cannot issue range request -- code="
                        + status.code);
                return false;
            }
        } catch (RemoteException e) {
            Log.e(TAG, "sendRangeRequest14: exception issuing range request: " + e);
            return false;
        }

        return true;
    }

    private boolean sendRangeRequest16(int cmdId, RangingRequest request) {
        ArrayList<android.hardware.wifi.V1_6.RttConfig> rttConfig =
                convertRangingRequestToRttConfigs16(request, mRttCapabilities);
        if (rttConfig == null) {
            Log.e(TAG, "sendRangeRequest16: invalid request parameters");
            return false;
        }
        if (rttConfig.size() == 0) {
            Log.e(TAG, "sendRangeRequest16: all requests invalidated");
            dispatchOnRangingResults(cmdId, new ArrayList<>());
            return true;
        }

        try {
            WifiStatus status = mIWifiRttController16.rangeRequest_1_6(cmdId, rttConfig);
            if (status.code != WifiStatusCode.SUCCESS) {
                Log.e(TAG, "sendRangeRequest16: cannot issue range request -- code="
                        + status.code);
                return false;
            }
        } catch (RemoteException e) {
            Log.e(TAG, "sendRangeRequest16: exception issuing range request: " + e);
            return false;
        }

        return true;
    }

    /**
     * Cancel an outstanding ranging request. No guarantees of execution - we will ignore any
     * results which are returned for the canceled request.
     *
     * @param cmdId The cmdId issued with the original rangeRequest command.
     * @param macAddresses A list of MAC addresses for which to cancel the operation.
     * @return true for success, false for failure.
     */
    public boolean rangeCancel(int cmdId, ArrayList<byte[]> macAddresses) {
        final String methodStr = "rangeCancel";
        if (mVerboseLoggingEnabled) Log.v(TAG, "rangeCancel: cmdId=" + cmdId);
        if (!checkRttControllerAndLogFailure(methodStr)) {
            return false;
        }

        try {
            WifiStatus status = mIWifiRttController.rangeCancel(cmdId, macAddresses);
            if (status.code != WifiStatusCode.SUCCESS) {
                Log.e(TAG, "rangeCancel: cannot issue range cancel -- code=" + status.code);
                return false;
            }
        } catch (RemoteException e) {
            Log.e(TAG, "rangeCancel: exception issuing range cancel: " + e);
            return false;
        }

        return true;
    }

    private static ArrayList<RttConfig> convertRangingRequestToRttConfigs(
            RangingRequest request, Capabilities cap) {
        ArrayList<RttConfig> rttConfigs = new ArrayList<>(request.mRttPeers.size());

        // Skip any configurations which have an error (just print out a message).
        // The caller will only get results for valid configurations.
        for (ResponderConfig responder: request.mRttPeers) {
            RttConfig config = new RttConfig();

            System.arraycopy(responder.macAddress.toByteArray(), 0, config.addr, 0,
                    config.addr.length);

            try {
                config.type = responder.supports80211mc ? RttType.TWO_SIDED : RttType.ONE_SIDED;
                if (config.type == RttType.ONE_SIDED && cap != null && !cap.oneSidedRttSupported) {
                    Log.w(TAG, "Device does not support one-sided RTT");
                    continue;
                }

                config.peer = halRttPeerTypeFromResponderType(responder.responderType);
                config.channel.width = halChannelWidthFromResponderChannelWidth(
                        responder.channelWidth);
                config.channel.centerFreq = responder.frequency;
                config.channel.centerFreq0 = responder.centerFreq0;
                config.channel.centerFreq1 = responder.centerFreq1;
                config.bw = halRttChannelBandwidthFromResponderChannelWidth(responder.channelWidth);
                config.preamble = halRttPreambleFromResponderPreamble(responder.preamble);
                validateBwAndPreambleCombination(config.bw, config.preamble);

                if (config.peer == RttPeerType.NAN) {
                    config.mustRequestLci = false;
                    config.mustRequestLcr = false;
                    config.burstPeriod = 0;
                    config.numBurst = 0;
                    config.numFramesPerBurst = request.mRttBurstSize;
                    config.numRetriesPerRttFrame = 0; // irrelevant for 2-sided RTT
                    config.numRetriesPerFtmr = 3;
                    config.burstDuration = 9;
                } else { // AP + all non-NAN requests
                    config.mustRequestLci = true;
                    config.mustRequestLcr = true;
                    config.burstPeriod = 0;
                    config.numBurst = 0;
                    config.numFramesPerBurst = request.mRttBurstSize;
                    config.numRetriesPerRttFrame = (config.type == RttType.TWO_SIDED ? 0 : 3);
                    config.numRetriesPerFtmr = 3;
                    config.burstDuration = 9;

                    if (cap != null) { // constrain parameters per device capabilities
                        config.mustRequestLci = config.mustRequestLci && cap.lciSupported;
                        config.mustRequestLcr = config.mustRequestLcr && cap.lcrSupported;
                        config.bw = halRttChannelBandwidthCapabilityLimiter(config.bw, cap);
                        config.preamble = halRttPreambleCapabilityLimiter(config.preamble, cap);
                    }
                }
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "Invalid configuration: " + e.getMessage());
                continue;
            }

            rttConfigs.add(config);
        }

        return rttConfigs;
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

    private static ArrayList<android.hardware.wifi.V1_4.RttConfig>
            convertRangingRequestToRttConfigs14(RangingRequest request, Capabilities cap) {
        ArrayList<android.hardware.wifi.V1_4.RttConfig> rttConfigs =
                new ArrayList<>(request.mRttPeers.size());

        // Skip any configurations which have an error (just print out a message).
        // The caller will only get results for valid configurations.
        for (ResponderConfig responder: request.mRttPeers) {
            android.hardware.wifi.V1_4.RttConfig config =
                    new android.hardware.wifi.V1_4.RttConfig();
            System.arraycopy(responder.macAddress.toByteArray(), 0, config.addr, 0,
                    config.addr.length);

            try {
                config.type = responder.supports80211mc ? RttType.TWO_SIDED : RttType.ONE_SIDED;
                if (config.type == RttType.ONE_SIDED && cap != null && !cap.oneSidedRttSupported) {
                    Log.w(TAG, "Device does not support one-sided RTT");
                    continue;
                }

                config.peer = halRttPeerTypeFromResponderType(responder.responderType);
                config.channel.width = halChannelWidthFromResponderChannelWidth(
                        responder.channelWidth);
                config.channel.centerFreq = responder.frequency;
                config.channel.centerFreq0 = responder.centerFreq0;
                config.channel.centerFreq1 = responder.centerFreq1;
                config.bw = halRttChannelBandwidthFromResponderChannelWidth(responder.channelWidth);
                config.preamble = halRttPreamble14FromResponderPreamble(responder.preamble);

                if (config.peer == RttPeerType.NAN) {
                    config.mustRequestLci = false;
                    config.mustRequestLcr = false;
                    config.burstPeriod = 0;
                    config.numBurst = 0;
                    config.numFramesPerBurst = request.mRttBurstSize;
                    config.numRetriesPerRttFrame = 0; // irrelevant for 2-sided RTT
                    config.numRetriesPerFtmr = 3;
                    config.burstDuration = 9;
                } else { // AP + all non-NAN requests
                    config.mustRequestLci = true;
                    config.mustRequestLcr = true;
                    config.burstPeriod = 0;
                    config.numBurst = 0;
                    config.numFramesPerBurst = request.mRttBurstSize;
                    config.numRetriesPerRttFrame = (config.type == RttType.TWO_SIDED ? 0 : 3);
                    config.numRetriesPerFtmr = 3;
                    config.burstDuration = 9;

                    if (cap != null) { // constrain parameters per device capabilities
                        config.mustRequestLci = config.mustRequestLci && cap.lciSupported;
                        config.mustRequestLcr = config.mustRequestLcr && cap.lcrSupported;
                        config.bw = halRttChannelBandwidthCapabilityLimiter(config.bw, cap);
                        config.preamble = halRttPreambleCapabilityLimiter(config.preamble, cap);
                    }
                }
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "Invalid configuration: " + e.getMessage());
                continue;
            }

            rttConfigs.add(config);
        }

        return rttConfigs;
    }

    private static ArrayList<android.hardware.wifi.V1_6.RttConfig>
            convertRangingRequestToRttConfigs16(RangingRequest request, Capabilities cap) {
        ArrayList<android.hardware.wifi.V1_6.RttConfig> rttConfigs =
                new ArrayList<>(request.mRttPeers.size());

        // Skip any configurations which have an error (just print out a message).
        // The caller will only get results for valid configurations.
        for (ResponderConfig responder: request.mRttPeers) {
            android.hardware.wifi.V1_6.RttConfig config =
                    new android.hardware.wifi.V1_6.RttConfig();
            System.arraycopy(responder.macAddress.toByteArray(), 0, config.addr, 0,
                    config.addr.length);

            try {
                config.type = responder.supports80211mc ? RttType.TWO_SIDED : RttType.ONE_SIDED;
                if (config.type == RttType.ONE_SIDED && cap != null && !cap.oneSidedRttSupported) {
                    Log.w(TAG, "Device does not support one-sided RTT");
                    continue;
                }

                config.peer = halRttPeerTypeFromResponderType(responder.responderType);
                config.channel.width = halChannelWidthFromResponderChannelWidth(
                        responder.channelWidth);
                config.channel.centerFreq = responder.frequency;
                config.channel.centerFreq0 = responder.centerFreq0;
                config.channel.centerFreq1 = responder.centerFreq1;
                config.bw = halRttChannelBandwidthFromResponderChannelWidth(responder.channelWidth);
                config.preamble = halRttPreamble14FromResponderPreamble(responder.preamble);

                if (config.peer == RttPeerType.NAN) {
                    config.mustRequestLci = false;
                    config.mustRequestLcr = false;
                    config.burstPeriod = 0;
                    config.numBurst = 0;
                    config.numFramesPerBurst = request.mRttBurstSize;
                    config.numRetriesPerRttFrame = 0; // irrelevant for 2-sided RTT
                    config.numRetriesPerFtmr = 3;
                    config.burstDuration = 9;
                } else { // AP + all non-NAN requests
                    config.mustRequestLci = true;
                    config.mustRequestLcr = true;
                    config.burstPeriod = 0;
                    config.numBurst = 0;
                    config.numFramesPerBurst = request.mRttBurstSize;
                    config.numRetriesPerRttFrame = (config.type == RttType.TWO_SIDED ? 0 : 3);
                    config.numRetriesPerFtmr = 3;
                    config.burstDuration = 9;

                    if (cap != null) { // constrain parameters per device capabilities
                        config.mustRequestLci = config.mustRequestLci && cap.lciSupported;
                        config.mustRequestLcr = config.mustRequestLcr && cap.lcrSupported;
                        config.bw = halRttChannelBandwidthCapabilityLimiter(config.bw, cap);
                        config.preamble = halRttPreambleCapabilityLimiter(config.preamble, cap);
                    }
                }
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "Invalid configuration: " + e.getMessage());
                continue;
            }

            rttConfigs.add(config);
        }

        return rttConfigs;
    }

    private static int halRttPeerTypeFromResponderType(int responderType) {
        switch (responderType) {
            case ResponderConfig.RESPONDER_AP:
                return RttPeerType.AP;
            case ResponderConfig.RESPONDER_STA:
                return RttPeerType.STA;
            case ResponderConfig.RESPONDER_P2P_GO:
                return RttPeerType.P2P_GO;
            case ResponderConfig.RESPONDER_P2P_CLIENT:
                return RttPeerType.P2P_CLIENT;
            case ResponderConfig.RESPONDER_AWARE:
                return RttPeerType.NAN;
            default:
                throw new IllegalArgumentException(
                        "halRttPeerTypeFromResponderType: bad " + responderType);
        }
    }

    private static int halChannelWidthFromResponderChannelWidth(int responderChannelWidth) {
        switch (responderChannelWidth) {
            case ResponderConfig.CHANNEL_WIDTH_20MHZ:
                return WifiChannelWidthInMhz.WIDTH_20;
            case ResponderConfig.CHANNEL_WIDTH_40MHZ:
                return WifiChannelWidthInMhz.WIDTH_40;
            case ResponderConfig.CHANNEL_WIDTH_80MHZ:
                return WifiChannelWidthInMhz.WIDTH_80;
            case ResponderConfig.CHANNEL_WIDTH_160MHZ:
                return WifiChannelWidthInMhz.WIDTH_160;
            case ResponderConfig.CHANNEL_WIDTH_80MHZ_PLUS_MHZ:
                return WifiChannelWidthInMhz.WIDTH_80P80;
            case ResponderConfig.CHANNEL_WIDTH_320MHZ:
                return WifiChannelWidthInMhz.WIDTH_320;
            default:
                throw new IllegalArgumentException(
                        "halChannelWidthFromResponderChannelWidth: bad " + responderChannelWidth);
        }
    }

    private static int halRttChannelBandwidthFromResponderChannelWidth(int responderChannelWidth) {
        switch (responderChannelWidth) {
            case ResponderConfig.CHANNEL_WIDTH_20MHZ:
                return RttBw.BW_20MHZ;
            case ResponderConfig.CHANNEL_WIDTH_40MHZ:
                return RttBw.BW_40MHZ;
            case ResponderConfig.CHANNEL_WIDTH_80MHZ:
                return RttBw.BW_80MHZ;
            case ResponderConfig.CHANNEL_WIDTH_160MHZ:
            case ResponderConfig.CHANNEL_WIDTH_80MHZ_PLUS_MHZ:
                return RttBw.BW_160MHZ;
            case ResponderConfig.CHANNEL_WIDTH_320MHZ:
                return RttBw.BW_320MHZ;
            default:
                throw new IllegalArgumentException(
                        "halRttChannelBandwidthFromHalBandwidth: bad " + responderChannelWidth);
        }
    }

    private static int halRttPreambleFromResponderPreamble(int responderPreamble) {
        switch (responderPreamble) {
            case ResponderConfig.PREAMBLE_LEGACY:
                return RttPreamble.LEGACY;
            case ResponderConfig.PREAMBLE_HT:
                return RttPreamble.HT;
            case ResponderConfig.PREAMBLE_VHT:
                return RttPreamble.VHT;
            case ResponderConfig.PREAMBLE_HE:
                return RttPreamble.HE;
            case ResponderConfig.PREAMBLE_EHT:
                return RttPreamble.EHT;
            default:
                throw new IllegalArgumentException(
                        "halRttPreambleFromResponderPreamble: bad " + responderPreamble);
        }
    }

    private static int halRttPreamble14FromResponderPreamble(int responderPreamble) {
        switch (responderPreamble) {
            case ResponderConfig.PREAMBLE_LEGACY:
                return RttPreamble.LEGACY;
            case ResponderConfig.PREAMBLE_HT:
                return RttPreamble.HT;
            case ResponderConfig.PREAMBLE_VHT:
                return RttPreamble.VHT;
            case ResponderConfig.PREAMBLE_HE:
                return RttPreamble.HE;
            default:
                throw new IllegalArgumentException(
                        "halRttPreamble14FromResponderPreamble: bad " + responderPreamble);
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
    private static int halRttChannelBandwidthCapabilityLimiter(int halRttChannelBandwidth,
            Capabilities cap) {
        int requestedBandwidth = halRttChannelBandwidth;
        while ((halRttChannelBandwidth != 0) && ((halRttChannelBandwidth & cap.bwSupported) == 0)) {
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
     * Check whether the selected RTT preamble is supported by the device.
     * If supported, return the requested preamble.
     * If not supported, return the next "lower" preamble which is supported.
     * If none, throw an IllegalArgumentException.
     *
     * Note: the halRttPreamble is a single bit flag from the HAL RttPreamble type.
     */
    private static int halRttPreambleCapabilityLimiter(int halRttPreamble, Capabilities cap) {
        int requestedPreamble = halRttPreamble;
        while ((halRttPreamble != 0) && ((halRttPreamble & cap.preambleSupported) == 0)) {
            halRttPreamble >>= 1;
        }

        if (halRttPreamble != 0) {
            return halRttPreamble;
        }

        throw new IllegalArgumentException(
                "RTT Preamble=" + requestedPreamble + ", not supported by device capabilities="
                        + cap + " - and no supported alternative");
    }

    private android.hardware.wifi.V1_4.IWifiRttController getRttControllerV1_4() {
        if (mIWifiRttController == null) {
            return null;
        }
        return android.hardware.wifi.V1_4.IWifiRttController.castFrom(mIWifiRttController);
    }

    private android.hardware.wifi.V1_6.IWifiRttController getRttControllerV1_6() {
        if (mIWifiRttController == null) {
            return null;
        }
        return android.hardware.wifi.V1_6.IWifiRttController.castFrom(mIWifiRttController);
    }

    /**
     * Dump the internal state of the class.
     */
    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println("WifiRttControllerHal:");
        pw.println("  mIWifiRttController: " + mIWifiRttController);
        pw.println("  mRttCapabilities: " + mRttCapabilities);
    }

    /**
     *  Callback for HAL events on 1.0 WifiRttController
     */
    private class WifiRttControllerEventCallback extends IWifiRttControllerEventCallback.Stub {
        /**
         * Callback from the HAL with range results.
         *
         * @param cmdId Command ID specified in the original request.
         * @param halResults A list of range results.
         */
        @Override
        public void onResults(int cmdId, ArrayList<RttResult> halResults) {
            if (mVerboseLoggingEnabled) {
                Log.v(TAG, "onResults: cmdId=" + cmdId + ", # of results=" + halResults.size());
            }
            if (halResults == null) {
                halResults = new ArrayList<>();
            }
            halResults.removeIf(Objects::isNull);
            ArrayList<RangingResult> rangingResults = convertHalResultsRangingResults(halResults);
            dispatchOnRangingResults(cmdId, rangingResults);
        }
    }

    /**
     *  Callback for HAL events on 1.4 WifiRttController
     */
    private class WifiRttControllerEventCallback14 extends
            android.hardware.wifi.V1_4.IWifiRttControllerEventCallback.Stub {
        @Override
        public void onResults(int cmdId, ArrayList<RttResult> halResults) {
            // This callback is not supported on this version of the interface.
            return;
        }

        @Override
        public void onResults_1_4(int cmdId,
                ArrayList<android.hardware.wifi.V1_4.RttResult> halResults) {
            if (mVerboseLoggingEnabled) {
                Log.v(TAG,
                        "onResults_1_4: cmdId=" + cmdId + ", # of results=" + halResults.size());
            }
            if (halResults == null) {
                halResults = new ArrayList<>();
            }
            halResults.removeIf(Objects::isNull);
            ArrayList<RangingResult> rangingResults = convertHalResultsRangingResults14(halResults);
            dispatchOnRangingResults(cmdId, rangingResults);
        }
    }

    /**
     *  Callback for HAL events on 1.6 WifiRttController
     */
    private class WifiRttControllerEventCallback16 extends
            android.hardware.wifi.V1_6.IWifiRttControllerEventCallback.Stub {
        @Override
        public void onResults(int cmdId, ArrayList<RttResult> halResults) {
            // This callback is not supported on this version of the interface.
            return;
        }

        @Override
        public void onResults_1_4(int cmdId,
                ArrayList<android.hardware.wifi.V1_4.RttResult> halResults) {
            // This callback is not supported on this version of the interface.
            return;
        }

        @Override
        public void onResults_1_6(int cmdId,
                ArrayList<android.hardware.wifi.V1_6.RttResult> halResults) {
            if (mVerboseLoggingEnabled) {
                Log.v(TAG,
                        "onResults_1_6: cmdId=" + cmdId + ", # of results=" + halResults.size());
            }
            if (halResults == null) {
                halResults = new ArrayList<>();
            }
            halResults.removeIf(Objects::isNull);
            ArrayList<RangingResult> rangingResults = convertHalResultsRangingResults16(halResults);
            dispatchOnRangingResults(cmdId, rangingResults);
        }
    }

    private ArrayList<RangingResult> convertHalResultsRangingResults(
            ArrayList<RttResult> halResults) {
        ArrayList<RangingResult> rangingResults = new ArrayList<>();
        for (RttResult rttResult : halResults) {
            byte[] lci = NativeUtil.byteArrayFromArrayList(rttResult.lci.data);
            byte[] lcr = NativeUtil.byteArrayFromArrayList(rttResult.lcr.data);
            ResponderLocation responderLocation;
            try {
                responderLocation = new ResponderLocation(lci, lcr);
                if (!responderLocation.isValid()) {
                    responderLocation = null;
                }
            } catch (Exception e) {
                responderLocation = null;
                Log.e(TAG,
                        "ResponderLocation: lci/lcr parser failed exception -- " + e);
            }
            if (rttResult.successNumber <= 1 && rttResult.distanceSdInMm != 0) {
                if (mVerboseLoggingEnabled) {
                    Log.w(TAG, "postProcessResults: non-zero distance stdev with 0||1 num "
                            + "samples!? result=" + rttResult);
                }
                rttResult.distanceSdInMm = 0;
            }
            rangingResults.add(new RangingResult(
                    convertHalStatusToFrameworkStatus(rttResult.status),
                    MacAddress.fromBytes(rttResult.addr),
                    rttResult.distanceInMm, rttResult.distanceSdInMm,
                    rttResult.rssi / -2, rttResult.numberPerBurstPeer,
                    rttResult.successNumber, lci, lcr, responderLocation,
                    rttResult.timeStampInUs / CONVERSION_US_TO_MS,
                    rttResult.type == RttType.TWO_SIDED));
        }
        return rangingResults;
    }

    private ArrayList<RangingResult> convertHalResultsRangingResults14(
            ArrayList<android.hardware.wifi.V1_4.RttResult> halResults) {
        ArrayList<RangingResult> rangingResults = new ArrayList<>();
        for (android.hardware.wifi.V1_4.RttResult rttResult : halResults) {
            byte[] lci = NativeUtil.byteArrayFromArrayList(rttResult.lci.data);
            byte[] lcr = NativeUtil.byteArrayFromArrayList(rttResult.lcr.data);
            ResponderLocation responderLocation;
            try {
                responderLocation = new ResponderLocation(lci, lcr);
                if (!responderLocation.isValid()) {
                    responderLocation = null;
                }
            } catch (Exception e) {
                responderLocation = null;
                Log.e(TAG,
                        "ResponderLocation: lci/lcr parser failed exception -- " + e);
            }
            if (rttResult.successNumber <= 1 && rttResult.distanceSdInMm != 0) {
                if (mVerboseLoggingEnabled) {
                    Log.w(TAG, "postProcessResults: non-zero distance stdev with 0||1 num "
                            + "samples!? result=" + rttResult);
                }
                rttResult.distanceSdInMm = 0;
            }
            rangingResults.add(new RangingResult(
                    convertHalStatusToFrameworkStatus(rttResult.status),
                    MacAddress.fromBytes(rttResult.addr),
                    rttResult.distanceInMm, rttResult.distanceSdInMm,
                    rttResult.rssi / -2, rttResult.numberPerBurstPeer,
                    rttResult.successNumber, lci, lcr, responderLocation,
                    rttResult.timeStampInUs / CONVERSION_US_TO_MS,
                    rttResult.type == RttType.TWO_SIDED));
        }
        return rangingResults;
    }

    private ArrayList<RangingResult> convertHalResultsRangingResults16(
            ArrayList<android.hardware.wifi.V1_6.RttResult> halResults) {
        ArrayList<RangingResult> rangingResults = new ArrayList<>();
        for (android.hardware.wifi.V1_6.RttResult rttResult : halResults) {
            byte[] lci = NativeUtil.byteArrayFromArrayList(rttResult.lci.data);
            byte[] lcr = NativeUtil.byteArrayFromArrayList(rttResult.lcr.data);
            ResponderLocation responderLocation;
            try {
                responderLocation = new ResponderLocation(lci, lcr);
                if (!responderLocation.isValid()) {
                    responderLocation = null;
                }
            } catch (Exception e) {
                responderLocation = null;
                Log.e(TAG,
                        "ResponderLocation: lci/lcr parser failed exception -- " + e);
            }
            if (rttResult.successNumber <= 1 && rttResult.distanceSdInMm != 0) {
                if (mVerboseLoggingEnabled) {
                    Log.w(TAG, "postProcessResults: non-zero distance stdev with 0||1 num "
                            + "samples!? result=" + rttResult);
                }
                rttResult.distanceSdInMm = 0;
            }
            rangingResults.add(new RangingResult(
                    convertHalStatusToFrameworkStatus(rttResult.status),
                    MacAddress.fromBytes(rttResult.addr),
                    rttResult.distanceInMm, rttResult.distanceSdInMm,
                    rttResult.rssi / -2, rttResult.numberPerBurstPeer,
                    rttResult.successNumber, lci, lcr, responderLocation,
                    rttResult.timeStampInUs / CONVERSION_US_TO_MS,
                    rttResult.type == RttType.TWO_SIDED));
        }
        return rangingResults;
    }

    private @FrameworkRttStatus int convertHalStatusToFrameworkStatus(int halStatus) {
        switch (halStatus) {
            case RttStatus.SUCCESS:
                return FRAMEWORK_RTT_STATUS_SUCCESS;
            case RttStatus.FAILURE:
                return FRAMEWORK_RTT_STATUS_FAILURE;
            case RttStatus.FAIL_NO_RSP:
                return FRAMEWORK_RTT_STATUS_FAIL_NO_RSP;
            case RttStatus.FAIL_REJECTED:
                return FRAMEWORK_RTT_STATUS_FAIL_REJECTED;
            case RttStatus.FAIL_NOT_SCHEDULED_YET:
                return FRAMEWORK_RTT_STATUS_FAIL_NOT_SCHEDULED_YET;
            case RttStatus.FAIL_TM_TIMEOUT:
                return FRAMEWORK_RTT_STATUS_FAIL_TM_TIMEOUT;
            case RttStatus.FAIL_AP_ON_DIFF_CHANNEL:
                return FRAMEWORK_RTT_STATUS_FAIL_AP_ON_DIFF_CHANNEL;
            case RttStatus.FAIL_NO_CAPABILITY:
                return FRAMEWORK_RTT_STATUS_FAIL_NO_CAPABILITY;
            case RttStatus.ABORTED:
                return FRAMEWORK_RTT_STATUS_ABORTED;
            case RttStatus.FAIL_INVALID_TS:
                return FRAMEWORK_RTT_STATUS_FAIL_INVALID_TS;
            case RttStatus.FAIL_PROTOCOL:
                return FRAMEWORK_RTT_STATUS_FAIL_PROTOCOL;
            case RttStatus.FAIL_SCHEDULE:
                return FRAMEWORK_RTT_STATUS_FAIL_SCHEDULE;
            case RttStatus.FAIL_BUSY_TRY_LATER:
                return FRAMEWORK_RTT_STATUS_FAIL_BUSY_TRY_LATER;
            case RttStatus.INVALID_REQ:
                return FRAMEWORK_RTT_STATUS_INVALID_REQ;
            case RttStatus.NO_WIFI:
                return FRAMEWORK_RTT_STATUS_NO_WIFI;
            case RttStatus.FAIL_FTM_PARAM_OVERRIDE:
                return FRAMEWORK_RTT_STATUS_FAIL_FTM_PARAM_OVERRIDE;
            default:
                Log.e(TAG, "Unrecognized RttStatus: " + halStatus);
                return FRAMEWORK_RTT_STATUS_UNKNOWN;
        }
    }

    /**
     * Framework representation of RTT capabilities.
     */
    public class Capabilities {
        // 1-sided rtt measurement is supported.
        public boolean oneSidedRttSupported;
        // Location configuration information supported.
        public boolean lciSupported;
        // Location civic records supported.
        public boolean lcrSupported;
        // Preamble supported, see bit mask definition above.
        public int preambleSupported;
        // RTT bandwidth supported.
        public int bwSupported;
        // Whether STA responder role is supported.
        public boolean responderSupported;
        // Draft 11mc version supported, including major and minor version. e.g., draft 4.3 is 43.
        public byte mcVersion;
        // Whether ftm rtt data collection is supported.
        public boolean rttFtmSupported;

        public Capabilities(RttCapabilities rttHalCapabilities) {
            oneSidedRttSupported = rttHalCapabilities.rttOneSidedSupported;
            lciSupported = rttHalCapabilities.lciSupported;
            lcrSupported = rttHalCapabilities.lcrSupported;
            responderSupported = rttHalCapabilities.responderSupported;
            preambleSupported = rttHalCapabilities.preambleSupport;
            mcVersion = rttHalCapabilities.mcVersion;
            bwSupported = rttHalCapabilities.bwSupport;
            rttFtmSupported = rttHalCapabilities.rttFtmSupported;
        }

        public Capabilities(android.hardware.wifi.V1_4.RttCapabilities rttHalCapabilities) {
            oneSidedRttSupported = rttHalCapabilities.rttOneSidedSupported;
            lciSupported = rttHalCapabilities.lciSupported;
            lcrSupported = rttHalCapabilities.lcrSupported;
            responderSupported = rttHalCapabilities.responderSupported;
            preambleSupported = rttHalCapabilities.preambleSupport;
            mcVersion = rttHalCapabilities.mcVersion;
            bwSupported = rttHalCapabilities.bwSupport;
            rttFtmSupported = rttHalCapabilities.rttFtmSupported;
        }

        public Capabilities(android.hardware.wifi.V1_6.RttCapabilities rttHalCapabilities) {
            oneSidedRttSupported = rttHalCapabilities.rttOneSidedSupported;
            lciSupported = rttHalCapabilities.lciSupported;
            lcrSupported = rttHalCapabilities.lcrSupported;
            responderSupported = rttHalCapabilities.responderSupported;
            preambleSupported = rttHalCapabilities.preambleSupport;
            mcVersion = rttHalCapabilities.mcVersion;
            bwSupported = rttHalCapabilities.bwSupport;
            rttFtmSupported = rttHalCapabilities.rttFtmSupported;
        }
    }

    /**
     * Returns false if mIWifiRttController is null, and logs failure containing methodStr.
     */
    private boolean checkRttControllerAndLogFailure(String methodStr) {
        if (mIWifiRttController == null) {
            Log.e(TAG, "Can't call " + methodStr + ", mIWifiRttController is null");
            return false;
        }
        return true;
    }
}
