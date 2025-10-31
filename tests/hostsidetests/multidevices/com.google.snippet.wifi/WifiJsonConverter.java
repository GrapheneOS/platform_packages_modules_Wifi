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

package com.google.snippet.wifi;

import android.net.MacAddress;
import android.net.wifi.SoftApConfiguration;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiNetworkSuggestion;

import androidx.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

/**
 * The converter class that allows users to use custom type as snippet RPC arguments and return
 * values.
 */
public final class WifiJsonConverter {

    /**
     * Remove the extra quotation marks from the beginning and the end of a string.
     *
     * <p>This is useful for strings like the SSID field of Android's Wi-Fi configuration.
     */
    public static String trimQuotationMarks(String originalString) {
        String result = originalString;
        if (originalString.length() > 2
                && originalString.charAt(0) == '"'
                && originalString.charAt(originalString.length() - 1) == '"') {
            result = originalString.substring(1, originalString.length() - 1);
        }
        return result;
    }

    /**
     * Serializes a complex type object to {@link JSONObject}.
     **
     * @param object The object to convert to "serialize".
     * @return A JSONObject representation of the input object.
     * @throws JSONException if there is an error serializing the object.
     */
    public static JSONObject serialize(Object object) throws JSONException {
        // If the RPC method requires a custom return type with special serialization
        // considerations we need to define it here.
        if (object instanceof SoftApConfiguration) {
            return serializeSoftApConfiguration((SoftApConfiguration) object);
        }
        if (object instanceof WifiConfiguration) {
            return serializeWifiConfiguration((WifiConfiguration) object);
        }
        if (object instanceof WifiNetworkSuggestion) {
            return serializeWifiNetworkSuggestion((WifiNetworkSuggestion) object);
        }
        if (object instanceof WifiInfo) {
            return serializeWifiInfo((WifiInfo) object);
        }
        throw new JSONException(
            "Unsupported object type: " + object.getClass().getName());
    }

    private static JSONObject serializeSoftApConfiguration(SoftApConfiguration data)
            throws JSONException {
        JSONObject result = new JSONObject();
        result.put("SSID", trimQuotationMarks(data.getWifiSsid().toString()));
        result.put("Passphrase", data.getPassphrase());
        result.put("hiddenSSID", data.isHiddenSsid());
        result.put("mSecurityType", data.getSecurityType());
        result.put("apBand", data.getBand());
        int channel = data.getChannel();
        result.put("apChannel", channel);
        return result;
    }

    /**
     * Helper to convert a List of MacAddress objects to a JSONArray of strings.
     */
    private static JSONArray macAddressListToJsonArray(List<MacAddress> macAddresses) {
        JSONArray jsonArray = new JSONArray();
        if (macAddresses != null) {
            for (MacAddress mac : macAddresses) {
                if (mac != null) {
                    jsonArray.put(mac.toString());
                }
            }
        }
        return jsonArray;
    }

    /**
     * {@link WifiNetworkSuggestion} covert to JSONObject.
     *
     * @param suggestion WifiNetworkSuggestion object to serialize.
     * @return JSONObject of the input object.
     * @throws JSONException if there is an error serializing the object.
     */
    public static JSONObject serializeWifiNetworkSuggestion(
            @NonNull WifiNetworkSuggestion suggestion) throws JSONException {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("ssid", suggestion.getSsid());
        MacAddress bssidMacAddress = suggestion.getBssid();
        if (bssidMacAddress != null) {
            jsonObject.put("bssid", bssidMacAddress.toString());
        } else {
            jsonObject.put("bssid", null);
        }

        jsonObject.put("is_hidden_ssid", suggestion.isHiddenSsid());
        jsonObject.put("is_metered", suggestion.isMetered());
        jsonObject.put("is_app_interaction_required", suggestion.isAppInteractionRequired());

        return jsonObject;
    }

    private static JSONObject serializeWifiConfiguration(WifiConfiguration data)
            throws JSONException {
        JSONObject result = new JSONObject();
        result.put("networkId", data.networkId);
        result.put("Status", WifiConfiguration.Status.strings[data.status]);
        result.put("BSSID", trimQuotationMarks(data.BSSID));
        result.put("SSID", trimQuotationMarks(data.SSID));
        result.put("HOME-PROVIDER-NETWORK", data.isHomeProviderNetwork);
        result.put("PRIO", data.priority);
        result.put("HIDDEN", data.hiddenSSID);
        result.put("PMF", data.requirePmf);
        result.put("CarrierId", data.carrierId);
        result.put("SubscriptionId", data.subscriptionId);
        return result;
    }

    private static JSONObject serializeWifiInfo(WifiInfo data)
            throws JSONException {
        JSONObject result = new JSONObject();
        result.put("bssid", data.getBSSID());
        result.put("ssid", trimQuotationMarks(data.getSSID()));
        result.put("hiddenSSID", data.getHiddenSSID());
        result.put("macAddress", data.getMacAddress());
        result.put("networkId", data.getNetworkId());
        result.put("rssi", data.getRssi());
        result.put("linkSpeed", data.getLinkSpeed());
        result.put("frequency", data.getFrequency());
        return result;
    }

    private WifiJsonConverter() { }
}
