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

package com.google.snippet.wifi.aware;

import android.net.NetworkRequest;
import android.net.wifi.aware.PublishConfig;
import android.net.wifi.aware.SubscribeConfig;
import android.net.wifi.ScanResult;
import android.net.wifi.aware.WifiAwareNetworkSpecifier;

import com.google.android.mobly.snippet.SnippetObjectConverter;
import com.google.android.mobly.snippet.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.lang.reflect.Type;
import java.util.List;

/**
 * The converter class that allows users to use custom type as snippet RPC arguments and return
 * values.
 */
public class WifiAwareSnippetConverter implements SnippetObjectConverter {


    public static String trimQuotationMarks(String originalString) {
        String result = originalString;
        if (originalString == null)
            return result;
        if (originalString.length() > 2
                && originalString.charAt(0) == '"'
                && originalString.charAt(originalString.length() - 1) == '"') {
            result = originalString.substring(1, originalString.length() - 1);
        }
        return result;
    }

    @Override
    public JSONObject serialize(Object object) throws JSONException {
        // If the RPC method requires a custom return type, e.g. SubscribeConfig, PublishConfig, we
        // need to define it here.
        // If the object type is not recognized, you can throw an exception or return null
        // depending on your application's needs.
        JSONObject result = new JSONObject();
        if (object instanceof WifiAwareNetworkSpecifier) {
            WifiAwareNetworkSpecifier frame = (WifiAwareNetworkSpecifier) object;
            result.put("result", SerializationUtil.parcelableToString(frame));
            return result;
        }
        return null;
    }

    public static JSONObject serializeScanResult(ScanResult data) throws JSONException {
        JSONObject result = new JSONObject();
        result.put("BSSID", data.BSSID);
        result.put("SSID", trimQuotationMarks(data.getWifiSsid().toString()));
        result.put("capabilities", data.capabilities);
        result.put("centerFreq0", data.centerFreq0);
        result.put("centerFreq1", data.centerFreq1);
        result.put("channelWidth", data.channelWidth);
        result.put("frequency", data.frequency);
        result.put("level", data.level);
        result.put("operatorFriendlyName",
            (data.operatorFriendlyName != null) ? data.operatorFriendlyName.toString() : "");
        result.put("timestamp", data.timestamp);
        result.put("venueName", (data.venueName != null) ? data.venueName.toString() : "");
        result.put("scan_result_parcel", SerializationUtil.parcelableToString(data));
        return result;
    }

    @Override
    public Object deserialize(JSONObject jsonObject, Type type) throws JSONException {
        // The parameters of Mobly RPC directly reference the Object type.
        // Here, we need to convert JSONObjects back into specific types.
        if (type == SubscribeConfig.class) {
            return WifiAwareJsonDeserializer.jsonToSubscribeConfig(jsonObject);
        } else if (type == PublishConfig.class) {
            return WifiAwareJsonDeserializer.jsonToPublishConfig(jsonObject);
        } else if (type == NetworkRequest.class) {
            return WifiAwareJsonDeserializer.jsonToNetworkRequest(jsonObject);
        }
        // If the type is not recognized, you can throw an exception or return null
        // depending on your application's needs.
        return null;
    }
}
