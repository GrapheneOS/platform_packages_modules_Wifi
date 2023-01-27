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

import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.net.wifi.IListListener;
import android.net.wifi.QosPolicyParams;
import android.net.wifi.WifiManager;
import android.os.HandlerThread;
import android.os.test.TestLooper;

import com.android.modules.utils.build.SdkLevel;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ApplicationQosPolicyRequestHandlerTest {
    private static final String TEST_IFACE_NAME_0 = "wlan0";
    private static final String TEST_IFACE_NAME_1 = "wlan1";
    private static final int TEST_POLICY_ID_START = 10;

    private ApplicationQosPolicyRequestHandler mDut;
    private TestLooper mLooper;

    @Mock ActiveModeWarden mActiveModeWarden;
    @Mock WifiNative mWifiNative;
    @Mock HandlerThread mHandlerThread;
    @Mock IListListener mIListListener;
    @Mock ClientModeManager mClientModeManager0;
    @Mock ClientModeManager mClientModeManager1;

    @Captor ArgumentCaptor<SupplicantStaIfaceHal.QosScsResponseCallback> mApCallbackCaptor;
    @Captor ArgumentCaptor<List<Integer>> mApplicationCallbackResultCaptor;
    private List<String> mApCallbackReceivedIfaces;

    private class ApplicationQosPolicyRequestHandlerSpy extends ApplicationQosPolicyRequestHandler {
        ApplicationQosPolicyRequestHandlerSpy() {
            super(mActiveModeWarden, mWifiNative, mHandlerThread);
        }

        @Override
        protected void logApCallbackMockable(String ifaceName,
                List<SupplicantStaIfaceHal.QosPolicyStatus> halStatusList) {
            mApCallbackReceivedIfaces.add(ifaceName);
        }
    }

    @Before
    public void setUp() throws Exception {
        assumeTrue(SdkLevel.isAtLeastU());
        MockitoAnnotations.initMocks(this);
        mApCallbackReceivedIfaces = new ArrayList<>();

        mLooper = new TestLooper();
        when(mHandlerThread.getLooper()).thenReturn(mLooper.getLooper());
        mDut = new ApplicationQosPolicyRequestHandlerSpy();
        verify(mWifiNative).registerQosScsResponseCallback(mApCallbackCaptor.capture());

        when(mClientModeManager0.getInterfaceName()).thenReturn(TEST_IFACE_NAME_0);
        when(mClientModeManager1.getInterfaceName()).thenReturn(TEST_IFACE_NAME_1);
        setupSynchronousResponse(SupplicantStaIfaceHal.QOS_POLICY_SCS_REQUEST_STATUS_SENT);
    }

    private QosPolicyParams createDownlinkPolicy(int policyId) {
        return new QosPolicyParams.Builder(policyId, QosPolicyParams.DIRECTION_DOWNLINK)
                .setUserPriority(QosPolicyParams.USER_PRIORITY_BACKGROUND_LOW)
                .setIpVersion(QosPolicyParams.IP_VERSION_4)
                .build();
    }

    private List<QosPolicyParams> createDownlinkPolicyList(int size, int basePolicyId) {
        List<QosPolicyParams> policies = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            policies.add(createDownlinkPolicy(basePolicyId + i));
        }
        return policies;
    }

    private List<Integer> generateIntegerList(int size, int val) {
        List<Integer> integerList = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            integerList.add(val);
        }
        return integerList;
    }

    private void setupSynchronousResponse(
            @SupplicantStaIfaceHal.QosPolicyScsRequestStatusCode int expectedStatus) {
        doAnswer(new Answer() {
            @Override
            public Object answer(InvocationOnMock invocation) {
                Object[] args = invocation.getArguments();
                List<QosPolicyParams> policies = (List<QosPolicyParams>) args[1];
                List<SupplicantStaIfaceHal.QosPolicyStatus> statusList = new ArrayList<>();
                for (QosPolicyParams policy : policies) {
                    statusList.add(new SupplicantStaIfaceHal.QosPolicyStatus(
                            policy.getTranslatedPolicyId(), expectedStatus));
                }
                return statusList;
            }
        }).when(mWifiNative).addQosPolicyRequestForScs(anyString(), anyList());
    }

    private List<Integer> getVirtualPolicyIds(List<QosPolicyParams> policyList) {
        List<Integer> virtualPolicyIds = new ArrayList<>();
        for (QosPolicyParams policy : policyList) {
            virtualPolicyIds.add(policy.getTranslatedPolicyId());
        }
        return virtualPolicyIds;
    }

    private void triggerApCallback(String ifaceName, List<QosPolicyParams> policyList,
            @SupplicantStaIfaceHal.QosPolicyScsResponseStatusCode int expectedStatus) {
        List<Integer> virtualPolicyIds = getVirtualPolicyIds(policyList);
        List<Integer> expectedStatusList =
                generateIntegerList(policyList.size(), expectedStatus);

        // Generate the HAL status list.
        List<SupplicantStaIfaceHal.QosPolicyStatus> cbStatusList = new ArrayList<>();
        for (int i = 0; i < virtualPolicyIds.size(); i++) {
            cbStatusList.add(new SupplicantStaIfaceHal.QosPolicyStatus(
                    virtualPolicyIds.get(i), expectedStatusList.get(i)));
        }

        // Trigger callback from the AP.
        mApCallbackCaptor.getValue().onApResponse(ifaceName, cbStatusList);
        mLooper.dispatchAll();
    }

    private void triggerAndVerifyApCallback(String ifaceName, List<QosPolicyParams> policyList,
            @SupplicantStaIfaceHal.QosPolicyScsResponseStatusCode int expectedStatus) {
        triggerApCallback(ifaceName, policyList, expectedStatus);
        assertTrue(mApCallbackReceivedIfaces.contains(ifaceName));
    }

    private void verifyApplicationCallback(@WifiManager.QosRequestStatus int expectedStatus)
            throws Exception {
        verify(mIListListener, times(1)).onResult(mApplicationCallbackResultCaptor.capture());
        List<Integer> expectedStatusList = generateIntegerList(
                mApplicationCallbackResultCaptor.getValue().size(), expectedStatus);
        List<Integer> applicationStatusList = mApplicationCallbackResultCaptor.getValue();
        assertTrue(expectedStatusList.equals(applicationStatusList));
    }

    /**
     * Tests that the policy add is rejected immediately if there are no active
     * ClientModeManagers providing internet.
     */
    @Test
    public void testDownlinkPolicyAddNoClientModeManagers() throws Exception {
        when(mActiveModeWarden.getInternetConnectivityClientModeManagers())
                .thenReturn(new ArrayList<>());
        List<QosPolicyParams> policyList = createDownlinkPolicyList(5, TEST_POLICY_ID_START);
        mDut.queueAddRequest(policyList, mIListListener);
        verifyApplicationCallback(WifiManager.QOS_REQUEST_STATUS_INSUFFICIENT_RESOURCES);
    }

    /**
     * Tests the policy add flow when there is a single ClientModeManager.
     */
    @Test
    public void testDownlinkPolicyAddOneClientModeManager() throws Exception {
        when(mActiveModeWarden.getInternetConnectivityClientModeManagers())
                .thenReturn(Arrays.asList(mClientModeManager0));
        List<QosPolicyParams> policyList = createDownlinkPolicyList(5, TEST_POLICY_ID_START);
        mDut.queueAddRequest(policyList, mIListListener);

        verify(mWifiNative).addQosPolicyRequestForScs(eq(TEST_IFACE_NAME_0), anyList());
        verifyApplicationCallback(WifiManager.QOS_REQUEST_STATUS_TRACKING);
        triggerAndVerifyApCallback(TEST_IFACE_NAME_0, policyList,
                SupplicantStaIfaceHal.QOS_POLICY_SCS_RESPONSE_STATUS_SUCCESS);
    }

    /**
     * Tests the policy add flow when there are multiple ClientModeManagers.
     */
    @Test
    public void testDownlinkPolicyAddTwoClientModeManagers() throws Exception {
        when(mActiveModeWarden.getInternetConnectivityClientModeManagers())
                .thenReturn(Arrays.asList(mClientModeManager0, mClientModeManager1));
        List<QosPolicyParams> policyList = createDownlinkPolicyList(5, TEST_POLICY_ID_START);
        mDut.queueAddRequest(policyList, mIListListener);

        verify(mWifiNative).addQosPolicyRequestForScs(eq(TEST_IFACE_NAME_0), anyList());
        verify(mWifiNative).addQosPolicyRequestForScs(eq(TEST_IFACE_NAME_1), anyList());
        verifyApplicationCallback(WifiManager.QOS_REQUEST_STATUS_TRACKING);

        triggerAndVerifyApCallback(TEST_IFACE_NAME_0, policyList,
                SupplicantStaIfaceHal.QOS_POLICY_SCS_RESPONSE_STATUS_SUCCESS);
        triggerAndVerifyApCallback(TEST_IFACE_NAME_1, policyList,
                SupplicantStaIfaceHal.QOS_POLICY_SCS_RESPONSE_STATUS_SUCCESS);
    }

    /**
     * Tests the policy add flow when multiple policy requests are queued.
     */
    @Test
    public void testMultipleDownlinkPolicyRequests() throws Exception {
        when(mActiveModeWarden.getInternetConnectivityClientModeManagers())
                .thenReturn(Arrays.asList(mClientModeManager0));
        List<QosPolicyParams> policyList1 = createDownlinkPolicyList(5, TEST_POLICY_ID_START);
        List<QosPolicyParams> policyList2 = createDownlinkPolicyList(5, TEST_POLICY_ID_START + 15);
        IListListener mockPolicy1Listener = mock(IListListener.class);
        IListListener mockPolicy2Listener = mock(IListListener.class);

        mDut.queueAddRequest(policyList1, mockPolicy1Listener);
        mDut.queueAddRequest(policyList2, mockPolicy2Listener);
        verify(mWifiNative, times(1)).addQosPolicyRequestForScs(eq(TEST_IFACE_NAME_0), anyList());
        verify(mockPolicy1Listener).onResult(anyList());
        verify(mockPolicy2Listener, never()).onResult(anyList());

        // Trigger callback from the AP for policyList1.
        // This should initiate processing for the next request.
        triggerAndVerifyApCallback(TEST_IFACE_NAME_0, policyList1,
                SupplicantStaIfaceHal.QOS_POLICY_SCS_RESPONSE_STATUS_SUCCESS);
        verify(mWifiNative, times(2)).addQosPolicyRequestForScs(eq(TEST_IFACE_NAME_0), anyList());
        verify(mockPolicy2Listener).onResult(anyList());

        // Trigger callback from the AP for policyList2.
        triggerAndVerifyApCallback(TEST_IFACE_NAME_0, policyList2,
                SupplicantStaIfaceHal.QOS_POLICY_SCS_RESPONSE_STATUS_SUCCESS);
    }

    /**
     * Tests that an error code is returned immediately if the HAL encounters an error.
     */
    @Test
    public void testDownlinkPolicyAddHalError() throws Exception {
        when(mActiveModeWarden.getInternetConnectivityClientModeManagers())
                .thenReturn(Arrays.asList(mClientModeManager0));
        when(mWifiNative.addQosPolicyRequestForScs(anyString(), anyList())).thenReturn(null);
        List<QosPolicyParams> policyList = createDownlinkPolicyList(5, TEST_POLICY_ID_START);
        mDut.queueAddRequest(policyList, mIListListener);
        verify(mWifiNative).addQosPolicyRequestForScs(eq(TEST_IFACE_NAME_0), anyList());
        verifyApplicationCallback(WifiManager.QOS_REQUEST_STATUS_FAILURE_UNKNOWN);
    }

    /**
     * Tests that an error code is immediately returned if the requested policy is rejected by
     * the supplicant due to invalid arguments.
     */
    @Test
    public void testDownlinkPolicyAddInvalidArguments() throws Exception {
        when(mActiveModeWarden.getInternetConnectivityClientModeManagers())
                .thenReturn(Arrays.asList(mClientModeManager0));
        setupSynchronousResponse(SupplicantStaIfaceHal.QOS_POLICY_SCS_REQUEST_STATUS_INVALID);
        List<QosPolicyParams> policyList = createDownlinkPolicyList(5, TEST_POLICY_ID_START);
        mDut.queueAddRequest(policyList, mIListListener);
        verify(mWifiNative).addQosPolicyRequestForScs(eq(TEST_IFACE_NAME_0), anyList());
        verifyApplicationCallback(WifiManager.QOS_REQUEST_STATUS_INVALID_PARAMETERS);
    }

    /**
     * Tests that unsolicited callbacks from the AP are ignored.
     */
    @Test
    public void testDownlinkPolicyUnsolicitedCallback() throws Exception {
        // Queue two add requests.
        when(mActiveModeWarden.getInternetConnectivityClientModeManagers())
                .thenReturn(Arrays.asList(mClientModeManager0));
        List<QosPolicyParams> policyList1 = createDownlinkPolicyList(5, TEST_POLICY_ID_START);
        List<QosPolicyParams> policyList2 = createDownlinkPolicyList(5, TEST_POLICY_ID_START + 15);
        IListListener mockPolicy1Listener = mock(IListListener.class);
        IListListener mockPolicy2Listener = mock(IListListener.class);
        mDut.queueAddRequest(policyList1, mockPolicy1Listener);
        mDut.queueAddRequest(policyList2, mockPolicy2Listener);

        // Verify that the first request is processed and the second is not.
        verify(mockPolicy1Listener).onResult(anyList());
        verify(mockPolicy2Listener, never()).onResult(anyList());

        // Trigger an unsolicited callback containing the incorrect policy IDs.
        // Verify that the second request is not processed.
        triggerApCallback(TEST_IFACE_NAME_0, policyList2,
                SupplicantStaIfaceHal.QOS_POLICY_SCS_RESPONSE_STATUS_SUCCESS);
        verify(mockPolicy2Listener, never()).onResult(anyList());

        // Trigger an unsolicited callback on the wrong interface.
        triggerApCallback(TEST_IFACE_NAME_1, policyList1,
                SupplicantStaIfaceHal.QOS_POLICY_SCS_RESPONSE_STATUS_SUCCESS);
        verify(mockPolicy2Listener, never()).onResult(anyList());

        // Trigger the expected callback. Expect that the second request is processed.
        triggerAndVerifyApCallback(TEST_IFACE_NAME_0, policyList1,
                SupplicantStaIfaceHal.QOS_POLICY_SCS_RESPONSE_STATUS_SUCCESS);
        verify(mockPolicy2Listener).onResult(anyList());
    }
}
