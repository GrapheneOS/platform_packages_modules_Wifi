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

import android.net.wifi.SoftApConfiguration;
import android.net.wifi.WifiConfiguration;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * The converter class that allows users to use custom type as snippet RPC arguments and return
 * values.
 */
public final class WifiJsonConverter {
    private static final Gson GSON = new GsonBuilder().serializeNulls().create();

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

        // By default, depends on Gson to serialize correctly.
        return new JSONObject(GSON.toJson(object));
    }

    private static JSONObject serializeSoftApConfiguration(SoftApConfiguration data)
            throws JSONException {
        JSONObject result = new JSONObject(GSON.toJson(data));
        result.put("SSID", trimQuotationMarks(data.getWifiSsid().toString()));
        return result;
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

    private WifiJsonConverter() { }
}
