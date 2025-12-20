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
import android.net.MacAddress;
import android.net.wifi.WifiAnnotations;
import android.net.wifi.rtt.ContinuousRangingResultCallback;
import android.net.wifi.rtt.RangingRequest;
import android.net.wifi.rtt.RangingResult;
import android.util.Log;

import java.io.PrintWriter;
import java.util.List;
import java.util.function.Supplier;

/**
 * Wrapper around a SupplicantWifiRttController.
 * Initialized using a Supplicant AIDL WifiRttController.
 */
public class SupplicantWifiRttController {
    private static final String TAG = "SupplicantWifiRttController";
    protected static final int CONVERSION_US_TO_MS = 1_000;

    private ISupplicantWifiRttController mWifiRttController;

    /**
     * Framework representation of Proximity Ranging capabilities.
     */
    public static class ProximityRangingCapabilities {
        /**
         * Maximum number of simultaneous continuous ranging sessions the
         * device can handle (Act as a seeker/initiator).
         */
        public int maxNumContinuousRangingSeekerSessions;

        /**
         * Maximum number of simultaneous continuous ranging sessions the
         * device can handle (act as an advertiser/responder).
         */
        public int maxNumContinuousRangingAdvertiserSessions;

        /**
         * Whether the device can support ranging initiator (seeker) and responder (advertiser)
         * role operation concurrently.
         */
        public boolean isConcurrentIStaRStaOperationSupported;

        /**
         * Minimum allowed ranging interval supported by firmware in EDCA-based ranging, in ms.
         */
        public int minAllowedRangingInterval80211mc;

        /**
         * Minimum allowed ranging interval supported by firmware in Non-Trigger-Based (NTB)
         * ranging, in ms.
         */
        public int minAllowedRangingIntervalNtbMs;

        /**
         * The device name is a friendly name of the Proximity Ranging device.
         */
        public String deviceName;

        /**
         * Whether EDCA-based ranging is supported.
         */
        public boolean is80211mcBasedRangingSupported;

        /**
         * Whether NTB (Non-Trigger-Based) ranging with a non-secure
         * Long Training Field (LTF) is supported.
         */
        public boolean isNtbNonSecureLtfRangingSupported;

        /**
         * Whether NTB (Non-Trigger-Based) ranging with a secure
         * Long Training Field (LTF) is supported.
         */
        public boolean isNtbSecureLtfRangingSupported;

        /**
         * Whether unauthenticated PASN mode is supported,
         * i.e., when there are no authentication credentials
         * (no Password and no PMK).
         */
        public boolean isUnauthenticatedPasnModeSupported;

        /**
         * Whether authenticated PASN mode is supported,
         * i.e., when both devices share authentication credentials
         * (e.g., a Password or PMK along with the device identity key).
         */
        public boolean isAuthenticatedPasnModeSupported;

        /**
         * Whether the Initiating Station (ISTA) role for
         * EDCA-based ranging is supported.
         */
        public boolean is80211mcBasedIstaRoleSupported;

        /**
         * Whether the Responding Station (RSTA) role for
         * EDCA-based ranging is supported.
         */
        public boolean is80211mcBasedRstaRoleSupported;

        /**
         * Whether the Initiating Station (ISTA) role for
         * NTB (Non-Trigger-Based) ranging is supported.
         */
        public boolean isNtbIstaRoleSupported;

        /**
         * Whether the Responding Station (RSTA) role for
         * NTB (Non-Trigger-Based) ranging is supported.
         */
        public boolean isNtbRstaRoleSupported;

        /**
         * The maximum supported packet bandwidth for
         * EDCA based ranging.
         */
        public @WifiAnnotations.ChannelWidth int maxSupportedPacketBandwidth80211mcBased;

        /**
         * The maximum supported preamble or format for
         * EDCA based ranging.
         */
        public @WifiAnnotations.PreambleType int maxSupportedPreamble80211mcBased;

        /**
         * The maximum supported packet bandwidth for
         * NTB ranging.
         */
        public @WifiAnnotations.ChannelWidth int maxSupportedPacketBandwidthNtb;

        /**
         * The maximum supported preamble or format for
         * NTB ranging.
         */
        public @WifiAnnotations.PreambleType int maxSupportedPreambleNtb;

        /**
         * Whether proximity ranging is supported on the 6GHz band.
         */
        public boolean is6GHzSupported;

        /**
         * Maximum number of transmit antennas supported for ranging.
         */
        public int maxNumTxAntennas;

        /**
         * Maximum number of receive antennas supported for ranging.
         */
        public int maxNumRxAntennas;

        /**
         * Whether the device supports MAC address randomization for proximity ranging
         * while the device is in a connected Wi-Fi (STA) state.
         */
        public boolean isConnectedMacRandomizationSupported;

        public ProximityRangingCapabilities() {
        }

        public ProximityRangingCapabilities(
                android.hardware.wifi.supplicant.ProximityRangingDeviceInfo prHalCapabilities) {
            maxNumContinuousRangingSeekerSessions = prHalCapabilities
                    .maxNumContinuousRangingSeekerSessions;
            maxNumContinuousRangingAdvertiserSessions = prHalCapabilities
                    .maxNumContinuousRangingAdvertiserSessions;
            isConcurrentIStaRStaOperationSupported = prHalCapabilities
                    .isConcurrentIStaRStaOperationSupported;
            minAllowedRangingInterval80211mc = prHalCapabilities.minAllowedRangingIntervalEdcaMs;
            minAllowedRangingIntervalNtbMs = prHalCapabilities.minAllowedRangingIntervalNtbMs;
            isConnectedMacRandomizationSupported =
                    prHalCapabilities.isConnectedMacRandomizationSupported;
            deviceName = prHalCapabilities.protocolInfo.deviceName;
            is80211mcBasedRangingSupported =
                    prHalCapabilities.protocolInfo.isEdcaBasedRangingSupported;
            isNtbNonSecureLtfRangingSupported =
                    prHalCapabilities.protocolInfo.isNtbNonSecureLtfRangingSupported;
            isNtbSecureLtfRangingSupported =
                    prHalCapabilities.protocolInfo.isNtbSecureLtfRangingSupported;
            isUnauthenticatedPasnModeSupported =
                    prHalCapabilities.protocolInfo.isUnauthenticatedPasnModeSupported;
            isAuthenticatedPasnModeSupported =
                    prHalCapabilities.protocolInfo.isAuthenticatedPasnModeSupported;
            is80211mcBasedIstaRoleSupported =
                    prHalCapabilities.protocolInfo.isEdcaBasedIstaRoleSupported;
            is80211mcBasedRstaRoleSupported =
                    prHalCapabilities.protocolInfo.isEdcaBasedRstaRoleSupported;
            isNtbIstaRoleSupported = prHalCapabilities.protocolInfo.isNtbIstaRoleSupported;
            isNtbRstaRoleSupported = prHalCapabilities.protocolInfo.isNtbRstaRoleSupported;
            maxSupportedPacketBandwidth80211mcBased =
                    SupplicantWifiRttControllerAidlImpl.halToFrameworkRttPacketBandwidth(
                            prHalCapabilities.protocolInfo.maxSupportedPacketBandwidthEdcaBased);
            maxSupportedPreamble80211mcBased =
                    SupplicantWifiRttControllerAidlImpl.halToFrameworkPreamble(
                            prHalCapabilities.protocolInfo.maxSupportedPreambleEdcaBased);
            maxSupportedPacketBandwidthNtb =
                    SupplicantWifiRttControllerAidlImpl.halToFrameworkRttPacketBandwidth(
                            prHalCapabilities.protocolInfo.maxSupportedPacketBandwidthNtb);
            maxSupportedPreambleNtb = SupplicantWifiRttControllerAidlImpl.halToFrameworkPreamble(
                    prHalCapabilities.protocolInfo.maxSupportedPreambleNtb);
            is6GHzSupported = prHalCapabilities.protocolInfo.is6GHzSupported;
            maxNumTxAntennas = prHalCapabilities.protocolInfo.maxNumTxAntennas;
            maxNumRxAntennas = prHalCapabilities.protocolInfo.maxNumRxAntennas;
        }
    }

    /**
     * Callback to receive ranging events.
     */
    public interface SupplicantWifiRttControllerEventCallback {
        /**
         * Called when ranging results are received from the HAL in response to a
         * {@link #rangeRequest(int, RangingRequest)}.
         *
         * @param cmdId Command ID specified in the original request, used to associate results
         *              with the request.
         * @param rangingResults A list of {@link RangingResult} objects, one for each peer
         *                       that was successfully ranged.
         */
        void onRangingResults(int cmdId, List<RangingResult> rangingResults);

        /**
         * Called to provide status updates for an ongoing continuous ranging session.
         *
         * @param cmdId The command ID of the continuous ranging session.
         * @param code  A status code indicating the current state of the session. The specific
         *              codes are dependent on the HAL implementation.
         */
        void onContinuousRangingStatusChanged(int cmdId, int code);

        /**
         * Called when a continuous ranging session has been terminated. No more results will be
         * received for this session.
         *
         * @param cmdId The command ID of the terminated continuous ranging session.
         * @param reason The reason for the termination, as defined in
         *               {@link ContinuousRangingResultCallback.RangingTerminateReason}.
         */
        void onContinuousRangingTerminated(int cmdId,
                @ContinuousRangingResultCallback.RangingTerminateReason int reason);

    }

    public SupplicantWifiRttController(@NonNull android.hardware.wifi.supplicant
            .ISupplicantWifiRttController rttController) {
        mWifiRttController = createWifiRttControllerAidlImplMockable(rttController);
    }

    protected SupplicantWifiRttControllerAidlImpl createWifiRttControllerAidlImplMockable(
            @NonNull android.hardware.wifi.supplicant.ISupplicantWifiRttController rttController) {
        return new SupplicantWifiRttControllerAidlImpl(rttController);
    }
    private <T> T validateAndCall(String methodStr, T defaultVal, @NonNull Supplier<T> supplier) {
        if (mWifiRttController == null) {
            Log.wtf(TAG, "Cannot call " + methodStr
                    + " because Supplicant mWifiRttController is null");
            return defaultVal;
        }
        return supplier.get();
    }

    /**
     * See comments for {@link ISupplicantWifiRttController#setup()}
     */
    public boolean setup() {
        return validateAndCall("setup", false,
                () -> mWifiRttController.setup());
    }

    /**
     * See comments for {@link ISupplicantWifiRttController#enableVerboseLogging(boolean)}
     */
    public void enableVerboseLogging(boolean verbose) {
        if (mWifiRttController != null) {
            mWifiRttController.enableVerboseLogging(verbose);
        }
    }

    /**
     * See comments for {@link ISupplicantWifiRttController#registerRttEventCallback(
     *                         SupplicantWifiRttControllerEventCallback)}
     */
    public void registerRttEventCallback(SupplicantWifiRttControllerEventCallback callback) {
        if (mWifiRttController != null) {
            mWifiRttController.registerRttEventCallback(callback);
        }
    }

    /**
     * See comments for {@link ISupplicantWifiRttController#validate()}
     */
    public boolean validate() {
        return validateAndCall("validate", false,
                () -> mWifiRttController.validate());
    }

    /**
     * See comments for {@link ISupplicantWifiRttController#getName()}
     */
    @Nullable
    public String getName() {
        return validateAndCall("getName", null,
                () -> mWifiRttController.getName());
    }

    /**
     * See comments for {@link ISupplicantWifiRttController#setProximityRangingDeviceName(String)}
     */
    public void setProximityRangingDeviceName(String name) {
        validateAndCall("setProximityRangingDeviceName", null,
                () -> {
                    mWifiRttController.setProximityRangingDeviceName(name);
                    return null;
                });
    }

    /**
     * See comments for {@link ISupplicantWifiRttController#setProximityRangingMacAddress(byte[])}
     */
    public void setProximityRangingMacAddress(@NonNull byte[] macAddress) {
        validateAndCall("setProximityRangingMacAddress", null,
                () -> {
                    mWifiRttController.setProximityRangingMacAddress(macAddress);
                    return null;
                });
    }

    /**
     * See comments for {@link ISupplicantWifiRttController#getProximityRangingMacAddress()}
     */
    @Nullable
    public byte[] getProximityRangingMacAddress() {
        return validateAndCall("getProximityRangingMacAddress", null,
                () -> mWifiRttController.getProximityRangingMacAddress());
    }

    /**
     * See comments for {@link ISupplicantWifiRttController#getProximityRangingCapabilities()}
     */
    @Nullable
    public ProximityRangingCapabilities getProximityRangingCapabilities() {
        return validateAndCall("getProximityRangingCapabilities", null,
                () -> mWifiRttController.getProximityRangingCapabilities());
    }

    /**
     * See comments for {@link ISupplicantWifiRttController#rangeRequest(int, RangingRequest)}
     */
    public boolean rangeRequest(int cmdId, RangingRequest request) {
        return validateAndCall("rangeRequest", false,
                () -> mWifiRttController.rangeRequest(cmdId, request));
    }

    /**
     * See comments for {@link ISupplicantWifiRttController#rangeCancel(int, List)}
     */
    public boolean rangeCancel(int cmdId, List<MacAddress> macAddresses) {
        return validateAndCall("rangeCancel", false,
                () -> mWifiRttController.rangeCancel(cmdId, macAddresses));
    }

    /**
     * See comments for {@link ISupplicantWifiRttController#dump(PrintWriter)}
     */
    public void dump(PrintWriter pw) {
        if (mWifiRttController != null) {
            mWifiRttController.dump(pw);
        }
    }
}
