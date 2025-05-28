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

package com.google.snippet.wifi.softap;

import android.net.MacAddress;
import android.net.wifi.SoftApConfiguration;
import android.text.TextUtils;

import com.android.modules.utils.build.SdkLevel;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;


/**
 * Deserializes JSONObject into data objects defined in Wi-Fi Aware API.
 */
public class WifiSapJsonDeserializer {

    private WifiSapJsonDeserializer() {}

    private static int[] convertJSONArrayToIntArray(JSONArray jArray) throws JSONException {
        if (jArray == null) {
            return null;
        }
        int[] iArray = new int[jArray.length()];
        for (int i = 0; i < jArray.length(); i++) {
            iArray[i] = jArray.getInt(i);
        }
        return iArray;
    }
    /**
     * Converts JSON object to {@link SoftApConfiguration}.
     *
     * @param configJson corresponding to SoftApConfiguration in
     * @param configBuilder    builder to build the SoftApConfiguration
     * @return SoftApConfiguration object
     */
    public static SoftApConfiguration jsonToSoftApConfiguration(
            JSONObject configJson, SoftApConfiguration.Builder configBuilder) throws JSONException {
        if (configJson == null) {
            return configBuilder.build();
        }
        if (configJson.has("SSID")) {
            configBuilder.setSsid(configJson.getString("SSID"));
        }
        if (configJson.has("password")) {
            String pwd = configJson.getString("password");
            // Check if new security type SAE (WPA3) is present. Default to PSK
            if (configJson.has("security")) {
                String securityType = configJson.getString("security");
                if (TextUtils.equals(securityType, "WPA2_PSK")) {
                    configBuilder.setPassphrase(pwd, SoftApConfiguration.SECURITY_TYPE_WPA2_PSK);
                } else if (TextUtils.equals(securityType, "WPA3_SAE_TRANSITION")) {
                    configBuilder.setPassphrase(pwd,
                            SoftApConfiguration.SECURITY_TYPE_WPA3_SAE_TRANSITION);
                } else if (TextUtils.equals(securityType, "WPA3_SAE")) {
                    configBuilder.setPassphrase(pwd, SoftApConfiguration.SECURITY_TYPE_WPA3_SAE);
                }
            } else {
                configBuilder.setPassphrase(pwd, SoftApConfiguration.SECURITY_TYPE_WPA2_PSK);
            }
        }
        if (configJson.has("BSSID")) {
            configBuilder.setBssid(MacAddress.fromString(configJson.getString("BSSID")));
        }
        if (configJson.has("hiddenSSID")) {
            configBuilder.setHiddenSsid(configJson.getBoolean("hiddenSSID"));
        }
        if (configJson.has("apBand")) {
            configBuilder.setBand(configJson.getInt("apBand"));
        }
        if (configJson.has("apChannel") && configJson.has("apBand")) {
            configBuilder.setChannel(configJson.getInt("apChannel"), configJson.getInt("apBand"));
        }

        if (configJson.has("MaxNumberOfClients")) {
            configBuilder.setMaxNumberOfClients(configJson.getInt("MaxNumberOfClients"));
        }

        if (configJson.has("ShutdownTimeoutMillis")) {
            configBuilder.setShutdownTimeoutMillis(configJson.getLong("ShutdownTimeoutMillis"));
        }

        if (configJson.has("AutoShutdownEnabled")) {
            configBuilder.setAutoShutdownEnabled(configJson.getBoolean("AutoShutdownEnabled"));
        }

        if (configJson.has("ClientControlByUserEnabled")) {
            configBuilder.setClientControlByUserEnabled(
                    configJson.getBoolean("ClientControlByUserEnabled"));
        }

        List allowedClientList = new ArrayList<>();
        if (configJson.has("AllowedClientList")) {
            JSONArray allowedList = configJson.getJSONArray("AllowedClientList");
            for (int i = 0; i < allowedList.length(); i++) {
                allowedClientList.add(MacAddress.fromString(allowedList.getString(i)));
            }
        }

        List blockedClientList = new ArrayList<>();
        if (configJson.has("BlockedClientList")) {
            JSONArray blockedList = configJson.getJSONArray("BlockedClientList");
            for (int j = 0; j < blockedList.length(); j++) {
                blockedClientList.add(MacAddress.fromString(blockedList.getString(j)));
            }
        }

        configBuilder.setAllowedClientList(allowedClientList);
        configBuilder.setBlockedClientList(blockedClientList);

        if (SdkLevel.isAtLeastS()) {
            if (configJson.has("apBands")) {
                JSONArray jBands = configJson.getJSONArray("apBands");
                int[] bands = convertJSONArrayToIntArray(jBands);
                configBuilder.setBands(bands);
            }
            if (configJson.has("MacRandomizationSetting")) {
                configBuilder.setMacRandomizationSetting(
                        configJson.getInt("MacRandomizationSetting"));
            }

            if (configJson.has("BridgedModeOpportunisticShutdownEnabled")) {
                configBuilder.setBridgedModeOpportunisticShutdownEnabled(
                        configJson.getBoolean("BridgedModeOpportunisticShutdownEnabled"));
            }

            if (configJson.has("Ieee80211axEnabled")) {
                configBuilder.setIeee80211axEnabled(configJson.getBoolean("Ieee80211axEnabled"));
            }
        }
        return configBuilder.build();
    }
}
