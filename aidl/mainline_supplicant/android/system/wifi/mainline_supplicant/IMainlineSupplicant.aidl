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

import android.system.wifi.mainline_supplicant.IStaInterface;

/**
 * Root of the mainline supplicant interface. This is an unstable AIDL interface used
 * to interact with the supplicant binary stored in the mainline module.
 */
interface IMainlineSupplicant {
    /**
     * Register a STA interface with the supplicant.
     *
     * @param ifaceName Name of the interface (ex. wlan0)
     * @throws ServiceSpecificException with one of the following values:
     *         |SupplicantStatusCode.FAILURE_UNKNOWN|
     *         |SupplicantStatusCode.FAILURE_ARGS_INVALID|
     */
    @PropagateAllowBlocking IStaInterface addStaInterface(String ifaceName);

    /**
     * Remove a STA interface from the supplicant.
     *
     * @param ifaceName Name of the interface (ex. wlan0)
     * @throws ServiceSpecificException with one of the following values:
     *         |SupplicantStatusCode.FAILURE_UNKNOWN|
     *         |SupplicantStatusCode.FAILURE_ARGS_INVALID|
     *         |SupplicantStatusCode.FAILURE_IFACE_UNKNOWN|
     */
    void removeStaInterface(String ifaceName);

    /**
     * Terminate the service.
     */
    oneway void terminate();
}
