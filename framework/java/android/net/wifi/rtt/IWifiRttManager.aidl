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

package android.net.wifi.rtt;

import android.net.MacAddress;
import android.net.wifi.rtt.IContinuousRangingResultCallback;
import android.net.wifi.rtt.IProximityDetectionMacAddressCallback;
import android.net.wifi.rtt.IRttCallback;
import android.net.wifi.rtt.ProximityDetectionCharacteristics;
import android.net.wifi.rtt.RangingRequest;
import android.os.Bundle;
import android.os.WorkSource;

/**
 * @hide
 */
interface IWifiRttManager {
    boolean isAvailable();
    void startRanging(in IBinder binder, in String callingPackage, in String callingFeatureId,
            in WorkSource workSource, in RangingRequest request, in IRttCallback callback,
            in Bundle extras);
    void cancelRanging(in WorkSource workSource);
    Bundle getRttCharacteristics();
    ProximityDetectionCharacteristics getProximityDetectionCharacteristics();
    void setProximityDetectionDeviceName(in String deviceName);
    MacAddress getProximityDetectionRandomizedMacAddress();
    void registerProximityDetectionMacAddressCallback(
            in IProximityDetectionMacAddressCallback callback);
    void unregisterProximityDetectionMacAddressCallback(
            in IProximityDetectionMacAddressCallback callback);
    void startContinuousRanging(in IBinder binder, in String callingPackage,
            in String callingFeatureId, in WorkSource workSource, in RangingRequest request,
            in IContinuousRangingResultCallback callback);
    void stopContinuousRanging(in WorkSource workSource);
}
