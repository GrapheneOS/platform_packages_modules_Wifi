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

import static com.android.dx.mockito.inline.extended.ExtendedMockito.inOrder;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.staticMockMarker;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.validateMockitoUsage;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import android.location.Location;

import androidx.test.filters.SmallTest;

import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.dx.mockito.inline.extended.StaticInOrder;
import com.android.libraries.entitlement.ServiceEntitlementException;
import com.android.server.wifi.entitlement.http.HttpClient;
import com.android.server.wifi.entitlement.http.HttpRequest;
import com.android.server.wifi.entitlement.http.HttpResponse;

import org.json.JSONException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.MockitoSession;

import java.net.MalformedURLException;
import java.util.Random;

/**
 * Unit tests for {@link AfcClient}.
 */
@SmallTest
public class AfcClientTest {
    @Captor ArgumentCaptor<HttpRequest> mHttpRequestCaptor;
    @Mock private Location mLocation;
    @Mock private Random mRandom;
    private AfcClient mAfcClient;
    private MockitoSession mSession;
    private HttpResponse mHttpResponse;
    private AfcLocation mAfcLocation;
    private static final double LONGITUDE = -122;
    private static final double LATITUDE = 37;

    @Before
    public void setUp() throws MalformedURLException, ServiceEntitlementException {
        MockitoAnnotations.initMocks(this);

        when(mRandom.nextDouble()).thenReturn(0.5);
        when(mLocation.getLongitude()).thenReturn(LONGITUDE);
        when(mLocation.getLatitude()).thenReturn(LATITUDE);
        mAfcLocation = new AfcEllipseLocation(
                AfcEllipseLocation.DEFAULT_SEMI_MINOR_AXIS_METERS,
                AfcEllipseLocation.DEFAULT_SEMI_MAJOR_AXIS_METERS,
                AfcEllipseLocation.DEFAULT_ORIENTATION,
                AfcEllipseLocation.DEFAULT_CENTER_LEEWAY_METERS, mRandom, mLocation
        );

        mHttpRequestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
        // Create new AfcClient with a testing server URL
        mAfcClient = new AfcClient();
        mAfcClient.setServerURL("https://testingURL");

        mHttpResponse = HttpResponse.builder().setResponseCode(0).setBody(
                "Test body").build();

        // Start a mockitoSession to mock HttpClient's static request method
        mSession = ExtendedMockito.mockitoSession()
                .mockStatic(HttpClient.class, withSettings().lenient())
                .initMocks(this)
                .startMocking();
        when(HttpClient.request(any(HttpRequest.class))).thenReturn(mHttpResponse);
    }

    /**
     * Verify that the Http request has expected fields set and that the request ID in the HTTP
     * request is incremented with every query to the AFC server.
     */
    @Test
    public void testHTTPRequestInitialization() throws JSONException {
        StaticInOrder inOrder = inOrder(staticMockMarker(HttpClient.class));

        mAfcClient.queryAfcServer(mAfcLocation);
        inOrder.verify(() -> HttpClient.request(mHttpRequestCaptor.capture()));
        HttpRequest httpRequest1 = mHttpRequestCaptor.getValue();
        assertThat(httpRequest1.postData().getJSONArray("availableSpectrumInquiryRequests")
                .getJSONObject(0).getJSONObject("location").getJSONObject(
                        "ellipse").getJSONObject("center").get("longitude"))
                .isEqualTo(LONGITUDE);
        assertThat(httpRequest1.postData().getJSONArray("availableSpectrumInquiryRequests")
                .getJSONObject(0).getJSONObject("location").getJSONObject(
                        "ellipse").getJSONObject("center").get("latitude"))
                .isEqualTo(LATITUDE);

        assertThat(httpRequest1.postData().getJSONArray("availableSpectrumInquiryRequests")
                .getJSONObject(0).get("requestId")).isEqualTo("0");

        mAfcClient.queryAfcServer(mAfcLocation);
        inOrder.verify(() -> HttpClient.request(mHttpRequestCaptor.capture()));
        HttpRequest httpRequest2 = mHttpRequestCaptor.getValue();

        // Ensure that the request ID has been incremented
        assertThat(httpRequest2.postData().getJSONArray("availableSpectrumInquiryRequests")
                .getJSONObject(0).get("requestId")).isEqualTo("1");

        mAfcClient.queryAfcServer(mAfcLocation);
        inOrder.verify(() -> HttpClient.request(mHttpRequestCaptor.capture()));
        HttpRequest httpRequest3 = mHttpRequestCaptor.getValue();

        // Ensure that the request ID has been incremented
        assertThat(httpRequest3.postData().getJSONArray("availableSpectrumInquiryRequests")
                .getJSONObject(0).get("requestId")).isEqualTo("2");
    }

    /**
     * Verify that the response received from the HttpClient request method matches the return
     * value of AfcClient#queryAfcServer.
     */
    @Test
    public void testReceiveHttpResponse() {
        HttpResponse afcServerHttpResponse = mAfcClient.queryAfcServer(mAfcLocation);

        // Verify that the response from the AfcClient method matches the response from HttpClient
        assertThat(afcServerHttpResponse).isEqualTo(mHttpResponse);
        assertThat(afcServerHttpResponse.body()).isEqualTo("Test body");
        assertThat(afcServerHttpResponse.responseCode()).isEqualTo(0);
    }
    /**
     * Called after each test.
     */
    @After
    public void cleanup() {
        validateMockitoUsage();
        if (mSession != null) {
            mSession.finishMocking();
        }
    }
}
