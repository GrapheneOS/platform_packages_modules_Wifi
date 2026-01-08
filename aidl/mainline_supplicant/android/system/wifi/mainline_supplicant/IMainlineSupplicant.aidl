/*
 * Copyright (C) 2024 The Android Open Source Project
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

package android.system.wifi.mainline_supplicant;

import android.hardware.wifi.supplicant.ISupplicant;
import android.system.wifi.mainline_supplicant.ISupplicantNanIface;

/**
 * Root of the mainline supplicant interface. This is an unstable AIDL interface used
 * to interact with the supplicant binary stored in the mainline module.
 */
interface IMainlineSupplicant {
    /**
     * Retrieve the root interface for the vendor supplicant.
     *
     * @return AIDL interface object representing the root of the
     *         vendor supplicant service
     * @throws ServiceSpecificException with one of the following values:
     *         |SupplicantStatusCode.FAILURE_UNKNOWN|
     */
    @PropagateAllowBlocking ISupplicant getVendorSupplicant();

    /**
     * Registers a wireless NANinterface in supplicant.
     *
     * @param ifaceName Name of the interface (e.g aware0).
     * @return AIDL interface object representing the interface if
     *         successful, null otherwise.
     * @throws ServiceSpecificException with one of the following values:
     *         |SupplicantStatusCode.FAILURE_ARGS_INVALID|,
     *         |SupplicantStatusCode.FAILURE_UNKNOWN|,
     *         |SupplicantStatusCode.FAILURE_IFACE_EXISTS|
     */
    @PropagateAllowBlocking ISupplicantNanIface addNanInterface(in String ifaceName);

    /**
     * Removes a wireless NAN interface from supplicant.
     *
     * @param ifaceName Name of the interface (e.g aware0).
     * @throws ServiceSpecificException with one of the following values:
     *         |SupplicantStatusCode.FAILURE_ARGS_INVALID|,
     *         |SupplicantStatusCode.FAILURE_UNKNOWN|,
     *         |SupplicantStatusCode.FAILURE_IFACE_DOES_NOT_EXIST|
     */
    void removeNanInterface(in String ifaceName);

    /**
     * Set the current user's identity for loading per supplicant configuration file
     * from user's storage.
     *
     * @param userId the identity of the current foreground user which the user credential
     *               encrypted (CE) storage has unlocked in the device. It can be observed from
     *。             ActivityManager#getCurrentUser.
     * @throws ServiceSpecificException with one of the following values:
     *         |SupplicantStatusCode.FAILURE_UNKNOWN|
     */
    void setCurrentUserIdentity(in int userId);
}
