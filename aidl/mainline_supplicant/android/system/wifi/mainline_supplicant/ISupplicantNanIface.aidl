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

package android.system.wifi.mainline_supplicant;

import android.system.wifi.mainline_supplicant.ISupplicantNanIfaceEventCallback;
import android.system.wifi.mainline_supplicant.NanConfigRequest;
import android.system.wifi.mainline_supplicant.NanEnableRequest;

/**
 * Interface used to represent a single NAN (Neighbour Aware Network) iface.
 *
 * References to "NAN Spec" are to the Wi-Fi Alliance "Wi-Fi Neighbor Awareness Networking (NAN)
 * Technical Specification".
 */
interface ISupplicantNanIface {
    /**
     * Requests notifications of significant events on this iface. Multiple calls to this must
     * register multiple callbacks, each of which must receive all events.
     *
     * @param callback An instance of the |ISupplicantNanIfaceEventCallback| AIDL interface
     *        object.
     * @throws ServiceSpecificException with one of the following values:
     *         |SupplicantStatusCode.FAILURE_IFACE_INVALID|
     */
    void registerEventCallback(in ISupplicantNanIfaceEventCallback callback);

    /**
     * Get NAN capabilities. Asynchronous response is with
     * |ISupplicantNanIfaceEventCallback.notifyCapabilitiesResponse|.
     *
     * @param cmdId Command Id to use for this invocation.
     * @throws ServiceSpecificException with one of the following values:
     *         |SupplicantStatusCode.FAILURE_IFACE_INVALID|,
     *         |SupplicantStatusCode.FAILURE_UNKNOWN|
     */
    void getCapabilitiesRequest(in char cmdId);

    /**
     * Configures and activates NAN clustering (does not start a discovery session or set up
     * data-interfaces or data-paths). Uses the |ISupplicantNanIface.configureRequest| method to
     * change the configuration of an already enabled NAN interface.
     * Asynchronous response is with
     * |ISupplicantNanIfaceEventCallback.notifyEnableResponse|.
     *
     * @param cmdId Command Id to use for this invocation.
     * @param msg1 Instance of |NanEnableRequest|.
     * @param msg2 Instance of |NanConfigRequest|.
     * @throws ServiceSpecificException with one of the following values:
     *         |SupplicantStatusCode.FAILURE_UNSUPPORTED|,
     *         |SupplicantStatusCode.FAILURE_IFACE_INVALID|,
     *         |SupplicantStatusCode.FAILURE_ARGS_INVALID|,
     *         |SupplicantStatusCode.FAILURE_UNKNOWN|
     */
    void enableRequest(in char cmdId, in NanEnableRequest msg1,
                       in NanConfigRequest msg2);

    /**
     * Configures an existing NAN functionality (i.e. assumes |ISupplicantNanIface.enableRequest|
     * already submitted and succeeded). Asynchronous response is with
     * |ISupplicantNanIfaceEventCallback.notifyConfigResponse|.
     *
     * @param cmdId Command Id to use for this invocation.
     * @param msg Instance of |NanConfigRequest|.
     * @throws ServiceSpecificException with one of the following values:
     *         |SupplicantStatusCode.FAILURE_UNSUPPORTED|,
     *         |SupplicantStatusCode.FAILURE_IFACE_INVALID|,
     *         |SupplicantStatusCode.FAILURE_ARGS_INVALID|,
     *         |SupplicantStatusCode.FAILURE_UNKNOWN|
     */
    void configRequest(in char cmdId, in NanConfigRequest msg);

    /**
     * Disables NAN functionality.
     * Asynchronous response is with |ISupplicantNanIfaceEventCallback.notifyDisableResponse|.
     *
     * @param cmdId Command Id to use for this invocation.
     * @throws ServiceSpecificException with one of the following values:
     *         |SupplicantStatusCode.FAILURE_IFACE_INVALID|,
     *         |SupplicantStatusCode.FAILURE_UNKNOWN|
     */
    void disableRequest(in char cmdId);

    /**
     * Creates a NAN Data Interface.
     * Asynchronous response is with
     * |ISupplicantNanIfaceEventCallback.notifyCreateDataInterfaceResponse|.
     *
     * @param cmdId Command Id to use for this invocation.
     * @param ifaceName The name of the interface, e.g. "aware0".
     * @throws ServiceSpecificException with one of the following values:
     *         |SupplicantStatusCode.FAILURE_IFACE_INVALID|,
     *         |SupplicantStatusCode.FAILURE_UNKNOWN|
     */
    void createDataInterfaceRequest(in char cmdId, in String ifaceName);

    /**
     * Deletes a NAN Data Interface.
     * Asynchronous response is with
     * |ISupplicantNanIfaceEventCallback.notifyDeleteDataInterfaceResponse|.
     *
     * @param cmdId Command Id to use for this invocation.
     * @param ifaceName The name of the interface, e.g. "aware0".
     * @throws ServiceSpecificException with one of the following values:
     *         |SupplicantStatusCode.FAILURE_IFACE_INVALID|,
     *         |SupplicantStatusCode.FAILURE_UNKNOWN|
     */
    void deleteDataInterfaceRequest(in char cmdId, in String ifaceName);

    /**
     * Gets the name of this iface.
     *
     * @return Name of this iface.
     * @throws ServiceSpecificException with one of the following values:
     *         |SupplicantStatusCode.FAILURE_IFACE_INVALID|
     */
    String getName();
}
