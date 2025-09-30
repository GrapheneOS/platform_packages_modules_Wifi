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

import android.system.wifi.mainline_supplicant.NanConfigRequest;

/**
 * Enables requests for NAN. Start-up configuration for |ISupplicantNanIface.enableRequest|.
 */
parcelable NanEnableRequest {
    /**
     * Enables operation in specific bands. Indexed by |NanBandIndex|. Multiple bands can be
     * enabled simultaneously. Operation on the 2.4 GHz band is mandatory, while the 5 GHz and 6
     * GHz bands are optional and can be enabled concurrently.
     */
    boolean[3] operateInBand;

    /**
     * Configurations of NAN cluster operation. Can also be modified at run-time using
     * |ISupplicantNanIface.configRequest|.
     */
    NanConfigRequest configParams;
}
