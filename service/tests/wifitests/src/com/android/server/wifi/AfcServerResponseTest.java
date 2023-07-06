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

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.when;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Unit tests for {@link com.android.server.wifi.AfcServerResponse}.
 */
public class AfcServerResponseTest {
    static final int SUCCESS_RESPONSE_CODE = 0;
    static final int GENERAL_FAILURE_RESPONSE_CODE = -1;
    static final int PROTOCOL_ERROR_RESPONSE_CODE = 100;
    static final int MESSAGE_EXCHANGE_ERROR_RESPONSE_CODE = 300;
    @Mock JSONObject mAvailableSpectrumInquiryResponse;
    @Mock JSONArray mAvailableFrequencyInfo;
    @Mock JSONObject mFrequencyJSON1;
    @Mock JSONObject mFrequencyJSON2;
    @Mock JSONObject mFrequencyJSON3;
    @Mock JSONObject mFrequencyRangeJSON1;
    @Mock JSONObject mFrequencyRangeJSON2;
    @Mock JSONObject mFrequencyRangeJSON3;
    // The response object within a spectrum inquiry response with a requestID matching that of the
    // request.
    @Mock JSONObject mSpectrumResponseJSON;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        when(mAvailableSpectrumInquiryResponse.getJSONArray("availableFrequencyInfo"))
                .thenReturn(mAvailableFrequencyInfo);
        when(mAvailableSpectrumInquiryResponse.optJSONArray("availableFrequencyInfo"))
                .thenReturn(mAvailableFrequencyInfo);
    }

    JSONObject buildSuccessfulSpectrumInquiryResponse() {
        try {
            when(mAvailableFrequencyInfo.length()).thenReturn(3);
            when(mAvailableFrequencyInfo.getJSONObject(0)).thenReturn(mFrequencyJSON1);
            when(mAvailableFrequencyInfo.getJSONObject(1)).thenReturn(mFrequencyJSON2);
            when(mAvailableFrequencyInfo.getJSONObject(2)).thenReturn(mFrequencyJSON3);

            when(mFrequencyJSON1.getJSONObject("frequencyRange")).thenReturn(
                    mFrequencyRangeJSON1);
            when(mFrequencyJSON2.getJSONObject("frequencyRange")).thenReturn(
                    mFrequencyRangeJSON2);
            when(mFrequencyJSON3.getJSONObject("frequencyRange")).thenReturn(
                    mFrequencyRangeJSON3);

            when(mFrequencyRangeJSON1.getInt("lowFrequency")).thenReturn(6109);
            when(mFrequencyRangeJSON1.getInt("highFrequency")).thenReturn(6111);
            when(mFrequencyJSON1.getInt("maxPsd")).thenReturn(17);

            when(mFrequencyRangeJSON2.getInt("lowFrequency")).thenReturn(5925);
            when(mFrequencyRangeJSON2.getInt("highFrequency")).thenReturn(5930);
            when(mFrequencyJSON2.getInt("maxPsd")).thenReturn(23);

            when(mFrequencyRangeJSON3.getInt("lowFrequency")).thenReturn(6177);
            when(mFrequencyRangeJSON3.getInt("highFrequency")).thenReturn(6182);
            when(mFrequencyJSON3.getInt("maxPsd")).thenReturn(23);

            when(mAvailableSpectrumInquiryResponse.getString("availabilityExpireTime"))
                    .thenReturn("2022-06-18T19:17:52.593206170Z");

            when(mAvailableSpectrumInquiryResponse.getJSONObject("response"))
                    .thenReturn(mSpectrumResponseJSON);
            when(mSpectrumResponseJSON.getInt("responseCode")).thenReturn(
                    SUCCESS_RESPONSE_CODE);
            when(mSpectrumResponseJSON.getString("shortDescription")).thenReturn(
                    "Example description");
        } catch (JSONException ignore) {
            // should never occur as all JSON objects are mocks
        }

        return mAvailableSpectrumInquiryResponse;
    }

    /**
     * Builds a successful inquiry response JSON object, but the available frequency info is an
     * empty array
     */
    JSONObject buildSuccessfulSpectrumInquiryResponseWithEmptyFrequencyInfo() {
        try {
            when(mAvailableFrequencyInfo.length()).thenReturn(0);
            when(mAvailableSpectrumInquiryResponse.getString("availabilityExpireTime"))
                    .thenReturn("2023-06-22T19:47:20.311472781Z");

            when(mAvailableSpectrumInquiryResponse.getJSONObject("response"))
                    .thenReturn(mSpectrumResponseJSON);
            when(mSpectrumResponseJSON.getInt("responseCode")).thenReturn(
                    SUCCESS_RESPONSE_CODE);
            when(mSpectrumResponseJSON.getString("shortDescription")).thenReturn(
                    "Example description");
        } catch (JSONException ignore) {
            // should never occur as all JSON objects are mocks
        }

        return mAvailableSpectrumInquiryResponse;
    }

    /**
     * Create a response JSON object with a specified AFC response code. This allows the response
     * to indicate if it was successful, or for what reason it failed.
     *
     * @param afcResponseCode the code indicating the status of the response from the AFC server
     * @return a spectrum inquiry response with the desired response code
     */
    JSONObject buildSpectrumInquiryResponseWithSpecifiedCode(int afcResponseCode) {
        try {
            when(mAvailableFrequencyInfo.length()).thenReturn(0);
            when(mAvailableSpectrumInquiryResponse.getString("availabilityExpireTime"))
                    .thenReturn("2022-06-18T19:17:52.593206170Z");

            when(mAvailableSpectrumInquiryResponse.getJSONObject("response"))
                    .thenReturn(mSpectrumResponseJSON);
            when(mSpectrumResponseJSON.getInt("responseCode")).thenReturn(
                    afcResponseCode);
            when(mSpectrumResponseJSON.getString("shortDescription")).thenReturn(
                    "Example description");
        } catch (JSONException ignore) {
            // should never occur as all JSON objects are mocks
        }

        return mAvailableSpectrumInquiryResponse;
    }

    /**
     * Verify that the correct values are set when the response is successful.
     */
    @Test
    public void testSuccessfulResponse() {
        int httpResponseCode = 200;
        mAvailableSpectrumInquiryResponse = buildSuccessfulSpectrumInquiryResponse();
        AfcServerResponse serverResponse = AfcServerResponse.fromSpectrumInquiryResponse(
                httpResponseCode, mAvailableSpectrumInquiryResponse);

        assertEquals(httpResponseCode, serverResponse.getHttpResponseCode());
        assertEquals(SUCCESS_RESPONSE_CODE, serverResponse.getAfcResponseCode());
        assertEquals(3, serverResponse.getAfcChannelAllowance().availableAfcFrequencyInfos.size());
    }

    /**
     * Verify that the availability expiration time is parsed and converted to a timestamp
     * correctly.
     */
    @Test
    public void testExpireTimeSetCorrectly() {
        int httpResponseCode = 200;
        String expireTimeString = "2023-06-22T19:47:20Z";
        long expireTimestampMs = 1687463240000L;

        try {
            mAvailableSpectrumInquiryResponse = buildSuccessfulSpectrumInquiryResponse();
            when(mAvailableSpectrumInquiryResponse.getString("availabilityExpireTime"))
                    .thenReturn(expireTimeString);
            AfcServerResponse serverResponse = AfcServerResponse.fromSpectrumInquiryResponse(
                    httpResponseCode, mAvailableSpectrumInquiryResponse);

            assertEquals(expireTimestampMs,
                    serverResponse.getAfcChannelAllowance().availabilityExpireTimeMs);
        } catch (JSONException ignore) {
            // should never occur
        }
    }

    /**
     * Verify that when the response is successful and contains an empty frequency info array,
     * the AfcChannelAllowance allowed frequency list is empty.
     */
    @Test
    public void testResponseWithEmptyFrequencyArray() {
        int httpResponseCode = 200;
        mAvailableSpectrumInquiryResponse =
                buildSuccessfulSpectrumInquiryResponseWithEmptyFrequencyInfo();
        AfcServerResponse serverResponse = AfcServerResponse.fromSpectrumInquiryResponse(
                httpResponseCode, mAvailableSpectrumInquiryResponse);

        assertEquals(httpResponseCode, serverResponse.getHttpResponseCode());
        assertEquals(SUCCESS_RESPONSE_CODE, serverResponse.getAfcResponseCode());

        // the allowed frequency array should be empty
        assertEquals(0, serverResponse.getAfcChannelAllowance().availableAfcFrequencyInfos.size());
    }

    /**
     * Verify that the case of a response of type GENERAL_FAILURE is handled correctly.
     */
    @Test
    public void testGeneralFailureResponse() {
        int httpResponseCode = 200;
        mAvailableSpectrumInquiryResponse = buildSpectrumInquiryResponseWithSpecifiedCode(
                GENERAL_FAILURE_RESPONSE_CODE);
        AfcServerResponse serverResponse = AfcServerResponse.fromSpectrumInquiryResponse(
                httpResponseCode, mAvailableSpectrumInquiryResponse);

        assertEquals(httpResponseCode, serverResponse.getHttpResponseCode());
        assertEquals(GENERAL_FAILURE_RESPONSE_CODE, serverResponse.getAfcResponseCode());
    }

    /**
     * Verify that the case of a response of type PROTOCOL_ERROR is handled correctly.
     */
    @Test
    public void testProtocolErrorResponse() {
        int httpResponseCode = 200;
        mAvailableSpectrumInquiryResponse = buildSpectrumInquiryResponseWithSpecifiedCode(
                PROTOCOL_ERROR_RESPONSE_CODE);
        AfcServerResponse serverResponse = AfcServerResponse.fromSpectrumInquiryResponse(
                httpResponseCode, mAvailableSpectrumInquiryResponse);

        assertEquals(httpResponseCode, serverResponse.getHttpResponseCode());
        assertEquals(PROTOCOL_ERROR_RESPONSE_CODE, serverResponse.getAfcResponseCode());
    }

    /**
     * Verify that the case of a response of type MESSAGE_EXCHANGE_ERROR is handled correctly.
     */
    @Test
    public void testMessageExchangeErrorResponse() {
        int httpResponseCode = 200;
        mAvailableSpectrumInquiryResponse = buildSpectrumInquiryResponseWithSpecifiedCode(
                MESSAGE_EXCHANGE_ERROR_RESPONSE_CODE);
        AfcServerResponse serverResponse = AfcServerResponse.fromSpectrumInquiryResponse(
                httpResponseCode, mAvailableSpectrumInquiryResponse);

        assertEquals(httpResponseCode, serverResponse.getHttpResponseCode());
        assertEquals(MESSAGE_EXCHANGE_ERROR_RESPONSE_CODE, serverResponse.getAfcResponseCode());
    }

    /**
     * Verify that no crash occurs if a null spectrum inquiry response object is passed to the AFC
     * server response.
     */
    @Test
    public void testNoCrashOnNullInquiryResponse() {
        int httpResponseCode = 500;
        AfcServerResponse serverResponse = AfcServerResponse.fromSpectrumInquiryResponse(
                httpResponseCode, mAvailableSpectrumInquiryResponse);
        assertEquals(null, serverResponse);
    }
}
