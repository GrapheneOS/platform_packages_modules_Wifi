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
import android.net.wifi.rtt.RangingRequest;

import java.io.PrintWriter;
import java.util.List;

interface ISupplicantWifiRttController {
    /**
     * Set up the ISupplicantWifiRttController.
     *
     * @return true if successful, false otherwise.
     */
    boolean setup();

    /**
     * Enable/disable verbose logging.
     */
    void enableVerboseLogging(boolean verbose);

    /**
     * Check whether the RTT controller is valid.
     *
     * @return true if the RTT controller is valid, false otherwise or if an error occurred.
     */
    boolean validate();

    /**
     * Retrieves the name of the network interface attached to the RTT controller.
     *
     * @return Name of the network interface, e.g., wlan0
     */
    String getName();

    /**
     * Get the Proximity Ranging capabilities.
     *
     * @return Capabilities, or null if they could not be retrieved.
     */
    @Nullable
    SupplicantWifiRttController.ProximityRangingCapabilities getProximityRangingCapabilities();

    /**
     * Set the device name for Proximity Ranging.
     * User-friendly name of the Proximity Ranging device
     * (up to 32 bytes encoded in UTF-8).
     *
     * @param name Name to be set.
     */
    void setProximityRangingDeviceName(String name);

    /**
     * Changes the MAC address used for proximity ranging.
     * Note: The MAC address will be used for USD discovery with
     * ranging enabled, Proximity Ranging security/range/channel
     * negotiation and range measurements.
     *
     * @param macAddress MAC address to change to.
     */
    void setProximityRangingMacAddress(@NonNull byte[] macAddress);

    /**
     * Get the MAC address which will be used in security/range
     * role/channel negotiation & range measurement.
     *
     * @return The MAC address of the interface used for USD discovery with
     * ranging enabled, Proximity Ranging security/range/channel
     * negotiation and range measurements.
     */
    @Nullable
    byte[] getProximityRangingMacAddress();

    /**
     * Issue a range request to the HAL.
     *
     * @param cmdId Command ID for the request.
     * @param request Range request.
     * @return true if successful, false otherwise.
     */
    boolean rangeRequest(int cmdId, RangingRequest request);

    /**
     * Cancel an outstanding ranging request.
     *
     * Note: No guarantees of execution. Can ignore any results which are returned for the
     * canceled request.
     *
     * @param cmdId The cmdId issued with the original rangeRequest command.
     * @param macAddresses A list of MAC addresses for which to cancel the operation.
     * @return true for success, false for failure.
     */
    boolean rangeCancel(int cmdId, List<MacAddress> macAddresses);

    /**
     * Register a callback for RTT events.
     *
     * @param callback The callback for handling RTT events.
     */
    void registerRttEventCallback(
            SupplicantWifiRttController.SupplicantWifiRttControllerEventCallback callback);

    /**
     * Dump the internal state of the class.
     */
    void dump(PrintWriter pw);
}
