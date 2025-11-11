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
/**
 * Event types for a cluster event indication.
 */
@Backing(type="int")
enum NanClusterEventType {
    /**
     * Management/discovery interface MAC address has changed
     * (e.g. due to randomization or at startup).
     */
    DISCOVERY_MAC_ADDRESS_CHANGED = 0,
    /**
     * A new cluster has been formed by this device.
     */
    STARTED_CLUSTER,
    /**
     * This device has joined an existing cluster.
     */
    JOINED_CLUSTER,
}
