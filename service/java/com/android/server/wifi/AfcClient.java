/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.server.wifi;

import android.net.wifi.ScanResult;
import android.util.Log;

import com.android.libraries.entitlement.ServiceEntitlementException;
import com.android.server.wifi.entitlement.http.HttpClient;
import com.android.server.wifi.entitlement.http.HttpConstants.RequestMethod;
import com.android.server.wifi.entitlement.http.HttpRequest;
import com.android.server.wifi.entitlement.http.HttpResponse;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

/**
 * Class that queries the AFC server with HTTP post requests and returns the HTTP response result.
 */
public class AfcClient {
    private static final String TAG = "WifiAfcClient";
    static final int CONNECT_TIMEOUT_SECS = 30;
    static final int LOW_FREQUENCY = ScanResult.BAND_6_GHZ_START_FREQ_MHZ;

    // TODO(b/291774201): Update max frequency since server is currently returning errors with
    // higher values than 6425.
    static final int HIGH_FREQUENCY = 6425;
    static final String SERIAL_NUMBER = "ABCDEFG";
    static final String NRA = "FCC";
    static final String ID = "EFGHIJK";
    static final String RULE_SET_ID = "US_47_CFR_PART_15_SUBPART_E";
    static final String VERSION = "1.2";
    static int sRequestId;
    private static String sServerUrl;
    private static HashMap<String, String> sRequestProperties;

    AfcClient() {
        sRequestId = 0;
        sRequestProperties = new HashMap<>();
    }

    /**
     * Query the AFC server through building an HTTP request object and return the HTTP response.
     */
    public HttpResponse queryAfcServer(AfcLocation afcLocation) {

        // If no URL is provided, then fail to proceed with sending request to AFC server
        if (sServerUrl == null) {
            Log.e(TAG,
                    "No Server URL was provided through command line argument. Must provide "
                            + "URL through configure-afc-server wifi shell command.");
            return null;
        }

        HttpRequest.Builder httpRequestBuilder = HttpRequest.builder()
                .setUrl(sServerUrl)
                .setRequestMethod(RequestMethod.POST)
                .setTimeoutInSec(CONNECT_TIMEOUT_SECS);

        for (Map.Entry<String, String> requestProperty : sRequestProperties.entrySet()) {
            httpRequestBuilder.addRequestProperty(requestProperty.getKey(),
                    requestProperty.getValue());
        }

        JSONObject jsonRequestObject = getAfcRequestJSONObject(afcLocation);
        httpRequestBuilder.setPostData(jsonRequestObject);
        HttpRequest httpRequest = httpRequestBuilder.build();
        HttpResponse httpResponse = null;

        try {
            httpResponse = HttpClient.request(httpRequest);
        } catch (ServiceEntitlementException e) {
            Log.e(TAG, "Service Entitlement Exception: " + e);
        }

        // Increment the request ID by 1 for the next request
        sRequestId++;

        return httpResponse;
    }

    /**
     * Get the AFC request JSON object used to query the AFC server.
     */
    private JSONObject getAfcRequestJSONObject(AfcLocation afcLocation) {
        try {
            JSONObject requestObject = new JSONObject();
            JSONArray inquiryRequests = new JSONArray();
            JSONObject inquiryRequest = new JSONObject();
            inquiryRequest.put("requestId", String.valueOf(sRequestId));

            JSONObject deviceDescriptor = new JSONObject();
            deviceDescriptor.put("serialNumber", AfcClient.SERIAL_NUMBER);
            JSONObject certificationId = new JSONObject();
            certificationId.put("nra", AfcClient.NRA);
            certificationId.put("id", AfcClient.ID);
            deviceDescriptor.put("certificationId", certificationId);
            deviceDescriptor.put("rulesetIds", AfcClient.RULE_SET_ID);
            inquiryRequest.put("deviceDescriptor", deviceDescriptor);

            JSONObject location = afcLocation.toJson();
            inquiryRequest.put("location", location);

            JSONArray inquiredFrequencyRange = new JSONArray();
            JSONObject range = new JSONObject();
            range.put("lowFrequency", AfcClient.LOW_FREQUENCY);
            range.put("highFrequency", AfcClient.HIGH_FREQUENCY);
            inquiredFrequencyRange.put(range);
            inquiryRequest.put("inquiredFrequencyRange", inquiredFrequencyRange);

            inquiryRequests.put(0, inquiryRequest);

            requestObject.put("version", AfcClient.VERSION);
            requestObject.put("availableSpectrumInquiryRequests", inquiryRequests);

            return requestObject;
        } catch (JSONException e) {
            Log.e(TAG, "Encountered error when building JSON object: " + e);
            return null;
        }
    }

    /**
     * Set the server URL used to query the AFC server.
     */
    public void setServerURL(String url) {
        sServerUrl = url;
    }

    /**
     * Sets the request properties Map through copying the Map created from the configure-afc-server
     * wifi-shell command.
     * @param requestProperties A map with key and value Strings for the HTTP header's request
     *                          property fields. Each key has a corresponding value.
     */
    public void setRequestPropertyPairs(Map<String, String> requestProperties) {
        sRequestProperties = new HashMap<>();
        sRequestProperties.putAll(requestProperties);
    }

    /**
     * Get the server URL for this AfcClient.
     */
    public String getServerURL() {
        return sServerUrl;
    }

    /**
     * Get the Map with <key, value> header fields for the HTTP request properties.
     */
    public Map<String, String> getRequestProperties() {
        return sRequestProperties;
    }
}
