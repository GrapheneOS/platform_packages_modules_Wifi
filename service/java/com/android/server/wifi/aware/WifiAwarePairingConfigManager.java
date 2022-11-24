/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.server.wifi.aware;

/**
 * The manager to store and maintain the NAN identity key and NPK/NIK caching
 */
public class WifiAwarePairingConfigManager {
    public WifiAwarePairingConfigManager() {

    }

    /**
     * Get the NAN identity key of the app
     * @param packageName the package name of the calling App
     * @return the NAN identity key for this app
     */
    public byte[] getNikForCallingPackage(String packageName) {
        return null;
    }

    /**
     * Get the matched pairing device's alias set by the app
     */
    public String getPairedDeviceAlias(String packageName, byte[] nonce, byte[] tag) {
        return null;
    }
}
