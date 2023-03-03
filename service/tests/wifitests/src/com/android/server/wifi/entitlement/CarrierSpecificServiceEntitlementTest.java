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

package com.android.server.wifi.entitlement;

import static android.text.format.DateUtils.HOUR_IN_MILLIS;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.when;
import static com.android.server.wifi.entitlement.RequestFactory.MESSAGE_ID_3GPP_AUTHENTICATION;
import static com.android.server.wifi.entitlement.RequestFactory.MESSAGE_ID_GET_IMSI_PSEUDONYM;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.any;

import androidx.test.filters.SmallTest;

import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.libraries.entitlement.EapAkaHelper;
import com.android.libraries.entitlement.ServiceEntitlementException;
import com.android.server.wifi.entitlement.http.HttpClient;
import com.android.server.wifi.entitlement.http.HttpRequest;
import com.android.server.wifi.entitlement.http.HttpResponse;
import com.android.server.wifi.entitlement.response.GetImsiPseudonymResponse;
import com.android.server.wifi.entitlement.response.Response;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;

/**
 * Unit tests for {@link CarrierSpecificServiceEntitlement}.
 */
@SmallTest
public class CarrierSpecificServiceEntitlementTest {
    private static final String JSON_KEY_MESSAGE_ID = "message-id";
    private static final String JSON_KEY_RESPONSE_CODE = "response-code";
    private static final String JSON_KEY_EAP_AKA_CHALLENGE = "aka-challenge";
    private static final String JSON_KEY_AKA_TOKEN = "aka-token";
    private static final String JSON_KEY_IMSI_PSEUDONYM = "imsi-pseudonym";

    private static final String IMSI = "imsi";
    private static final String CHALLENGE = "challenge";
    private static final String IMSI_PSEUDONYM = "imsi_pseudonym";
    private static final int REFRESH_INTERVAL = 48;
    private static final String AKA_TOKEN = "aka_token";
    private static final String SERVER_URL = "https://server_url";
    @Mock
    private EapAkaHelper mEapAkaHelper;
    @Mock
    EapAkaHelper.EapAkaResponse  mEapAkaResponse;
    @Mock
    private RequestFactory mRequestFactory;
    MockitoSession mMockingSession = null;
    private CarrierSpecificServiceEntitlement mEntitlement;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        mMockingSession = ExtendedMockito.mockitoSession().strictness(Strictness.LENIENT)
                .mockStatic(HttpClient.class).startMocking();
    }

    @After
    public void cleanUp() throws Exception {
        if (mMockingSession != null) {
            mMockingSession.finishMocking();
        }
    }

    @Test
    public void getImsiPseudonymSuccess()
            throws TransientException, JSONException, NonTransientException,
            ServiceEntitlementException {
        when(mEapAkaHelper.getEapAkaResponse(any())).thenReturn(mEapAkaResponse);
        when(mEapAkaResponse.response()).thenReturn("response");
        when(mRequestFactory.createAuthRequest()).thenReturn(new JSONArray());
        when(mRequestFactory.createGetImsiPseudonymRequest(any(), any()))
                .thenReturn(new JSONArray());
        when(HttpClient.request(any(HttpRequest.class)))
                .thenReturn(getAuthHttpResponse(), getGetImsiPseudonymHttpResponse());

        mEntitlement = new CarrierSpecificServiceEntitlement(IMSI, mRequestFactory, mEapAkaHelper,
                SERVER_URL);
        PseudonymInfo pseudonymInfo = mEntitlement.getImsiPseudonym().get();
        assertEquals(IMSI_PSEUDONYM, pseudonymInfo.getPseudonym());
        assertEquals(REFRESH_INTERVAL * HOUR_IN_MILLIS, pseudonymInfo.getTtlInMillis());
    }

    @Test
    public void getImsiPseudonymFail()
            throws TransientException, JSONException, NonTransientException,
            ServiceEntitlementException {
        when(mEapAkaHelper.getEapAkaResponse(any())).thenReturn(mEapAkaResponse);
        when(mEapAkaResponse.response()).thenReturn("response");
        when(mRequestFactory.createAuthRequest()).thenReturn(new JSONArray());
        when(HttpClient.request(any())).thenReturn(getAuthFailedHttpResponse());
        mEntitlement = new CarrierSpecificServiceEntitlement(IMSI, mRequestFactory, mEapAkaHelper,
                SERVER_URL);
        assertThrows(NonTransientException.class, () -> mEntitlement.getImsiPseudonym());
    }

    private HttpResponse getAuthHttpResponse() throws JSONException {
        JSONObject body = (new JSONObject()).put(JSON_KEY_MESSAGE_ID,
                        RequestFactory.MESSAGE_ID_3GPP_AUTHENTICATION)
                .put(JSON_KEY_RESPONSE_CODE, Response.RESPONSE_CODE_AKA_CHALLENGE)
                .put(JSON_KEY_EAP_AKA_CHALLENGE, CHALLENGE);
        JSONArray bodyArray = (new JSONArray()).put(body);
        return HttpResponse.builder().setBody((bodyArray.toString())).build();
    }

    private HttpResponse getAuthFailedHttpResponse() throws JSONException {
        JSONObject body = (new JSONObject()).put(JSON_KEY_MESSAGE_ID,
                        RequestFactory.MESSAGE_ID_3GPP_AUTHENTICATION)
                .put(JSON_KEY_RESPONSE_CODE, Response.RESPONSE_CODE_AKA_AUTH_FAILED);
        JSONArray bodyArray = (new JSONArray()).put(body);
        return HttpResponse.builder().setBody((bodyArray.toString())).build();
    }

    private HttpResponse getGetImsiPseudonymHttpResponse() throws JSONException {
        JSONObject body = (new JSONObject()).put(JSON_KEY_MESSAGE_ID,
                        MESSAGE_ID_3GPP_AUTHENTICATION)
                .put(JSON_KEY_RESPONSE_CODE, Response.RESPONSE_CODE_REQUEST_SUCCESSFUL)
                .put(JSON_KEY_AKA_TOKEN, AKA_TOKEN);

        JSONArray bodyArray = (new JSONArray()).put(body);

        body = (new JSONObject()).put(JSON_KEY_MESSAGE_ID,
                        MESSAGE_ID_GET_IMSI_PSEUDONYM)
                .put(JSON_KEY_RESPONSE_CODE, Response.RESPONSE_CODE_REQUEST_SUCCESSFUL)
                .put(JSON_KEY_IMSI_PSEUDONYM, IMSI_PSEUDONYM)
                .put(GetImsiPseudonymResponse.JSON_KEY_REFRESH_INTERVAL, REFRESH_INTERVAL);

        bodyArray.put(body);
        return HttpResponse.builder().setBody((bodyArray.toString())).build();
    }
}
