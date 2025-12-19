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

package com.android.server.wifi.rtt;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.net.MacAddress;
import android.net.wifi.rtt.RangingRequest;
import android.os.RemoteException;
import android.os.ServiceSpecificException;

import androidx.test.filters.SmallTest;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;

@SmallTest
public class SupplicantWifiRttControllerTest {

    private static final String IFACE_NAME = "wlan0";
    private static final MacAddress TEST_MAC_ADDRESS = MacAddress.fromString("01:23:45:67:89:AB");
    private static final byte[] TEST_MAC_ADDRESS_BYTES = TEST_MAC_ADDRESS.toByteArray();
    private static final String TEST_DEVICE_NAME = "TestDevice";

    @Mock
    private android.hardware.wifi.supplicant.ISupplicantWifiRttController mMockHalRttController;
    @Mock
    private SupplicantWifiRttControllerAidlImpl mMockRttControllerAidlImpl;

    private TestSupplicantWifiRttController mDut;

    private class TestSupplicantWifiRttController extends SupplicantWifiRttController {
        TestSupplicantWifiRttController(
                android.hardware.wifi.supplicant.ISupplicantWifiRttController rttController) {
            super(rttController);
        }

        @Override
        protected SupplicantWifiRttControllerAidlImpl createWifiRttControllerAidlImplMockable(
                android.hardware.wifi.supplicant.ISupplicantWifiRttController rttController) {
            return mMockRttControllerAidlImpl;
        }
    }

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mDut = new TestSupplicantWifiRttController(mMockHalRttController);
    }

    @Test
    public void testSetup_success() throws RemoteException, ServiceSpecificException {
        when(mMockRttControllerAidlImpl.setup()).thenReturn(true);
        assertTrue(mDut.setup());
        verify(mMockRttControllerAidlImpl).setup();
    }

    @Test
    public void testSetup_failure() throws RemoteException, ServiceSpecificException {
        when(mMockRttControllerAidlImpl.setup()).thenReturn(false);
        assertFalse(mDut.setup());
        verify(mMockRttControllerAidlImpl).setup();
    }

    @Test
    public void testEnableVerboseLogging() {
        mDut.enableVerboseLogging(true);
        verify(mMockRttControllerAidlImpl).enableVerboseLogging(true);

        mDut.enableVerboseLogging(false);
        verify(mMockRttControllerAidlImpl).enableVerboseLogging(false);
    }

    @Test
    public void testRegisterRangingResultsCallback() {
        SupplicantWifiRttController.SupplicantWifiRttControllerEventCallback callback =
                mock(SupplicantWifiRttController.SupplicantWifiRttControllerEventCallback.class);
        mDut.registerRttEventCallback(callback);
        verify(mMockRttControllerAidlImpl).registerRttEventCallback(callback);
    }

    @Test
    public void testValidate_success() throws RemoteException, ServiceSpecificException {
        when(mMockRttControllerAidlImpl.validate()).thenReturn(true);
        assertTrue(mDut.validate());
        verify(mMockRttControllerAidlImpl).validate();
    }

    @Test
    public void testValidate_failure() throws RemoteException, ServiceSpecificException {
        when(mMockRttControllerAidlImpl.validate()).thenReturn(false);
        assertFalse(mDut.validate());
        verify(mMockRttControllerAidlImpl).validate();
    }

    @Test
    public void testGetName_success() throws RemoteException, ServiceSpecificException {
        when(mMockRttControllerAidlImpl.getName()).thenReturn(IFACE_NAME);
        assertEquals(IFACE_NAME, mDut.getName());
        verify(mMockRttControllerAidlImpl).getName();
    }

    @Test
    public void testGetName_null() throws RemoteException, ServiceSpecificException {
        when(mMockRttControllerAidlImpl.getName()).thenReturn(null);
        assertNull(mDut.getName());
        verify(mMockRttControllerAidlImpl).getName();
    }

    @Test
    public void testSetProximityRangingDeviceName() {
        doNothing().when(mMockRttControllerAidlImpl).setProximityRangingDeviceName(anyString());
        mDut.setProximityRangingDeviceName(TEST_DEVICE_NAME);
        verify(mMockRttControllerAidlImpl).setProximityRangingDeviceName(eq(TEST_DEVICE_NAME));
    }

    @Test
    public void testSetProximityRangingMacAddress() {
        doNothing().when(mMockRttControllerAidlImpl)
                .setProximityRangingMacAddress(any(byte[].class));
        mDut.setProximityRangingMacAddress(TEST_MAC_ADDRESS_BYTES);
        verify(mMockRttControllerAidlImpl)
                .setProximityRangingMacAddress(eq(TEST_MAC_ADDRESS_BYTES));
    }

    @Test
    public void testGetProximityRangingMacAddress_success() throws RemoteException,
            ServiceSpecificException {
        when(mMockRttControllerAidlImpl.getProximityRangingMacAddress())
                .thenReturn(TEST_MAC_ADDRESS_BYTES);
        assertArrayEquals(TEST_MAC_ADDRESS_BYTES, mDut.getProximityRangingMacAddress());
        verify(mMockRttControllerAidlImpl).getProximityRangingMacAddress();
    }

    @Test
    public void testGetProximityRangingMacAddress_null() throws RemoteException,
            ServiceSpecificException {
        when(mMockRttControllerAidlImpl.getProximityRangingMacAddress()).thenReturn(null);
        assertNull(mDut.getProximityRangingMacAddress());
        verify(mMockRttControllerAidlImpl).getProximityRangingMacAddress();
    }

    @Test
    public void testGetProximityRangingCapabilities_success() throws RemoteException,
            ServiceSpecificException {
        SupplicantWifiRttController.ProximityRangingCapabilities capabilities =
                new SupplicantWifiRttController.ProximityRangingCapabilities();
        when(mMockRttControllerAidlImpl.getProximityRangingCapabilities())
                .thenReturn(capabilities);
        assertEquals(capabilities, mDut.getProximityRangingCapabilities());
        verify(mMockRttControllerAidlImpl).getProximityRangingCapabilities();
    }

    @Test
    public void testGetProximityRangingCapabilities_null() throws RemoteException,
            ServiceSpecificException {
        when(mMockRttControllerAidlImpl.getProximityRangingCapabilities()).thenReturn(null);
        assertNull(mDut.getProximityRangingCapabilities());
        verify(mMockRttControllerAidlImpl).getProximityRangingCapabilities();
    }

    @Test
    public void testRangeRequest_success() throws RemoteException, ServiceSpecificException {
        RangingRequest request = mock(RangingRequest.class);
        when(mMockRttControllerAidlImpl.rangeRequest(anyInt(), any(RangingRequest.class)))
                .thenReturn(true);
        assertTrue(mDut.rangeRequest(1, request));
        verify(mMockRttControllerAidlImpl).rangeRequest(eq(1), eq(request));
    }

    @Test
    public void testRangeRequest_failure() throws RemoteException, ServiceSpecificException {
        RangingRequest request = mock(RangingRequest.class);
        when(mMockRttControllerAidlImpl.rangeRequest(anyInt(), any(RangingRequest.class)))
                .thenReturn(false);
        assertFalse(mDut.rangeRequest(1, request));
        verify(mMockRttControllerAidlImpl).rangeRequest(eq(1), eq(request));
    }

    @Test
    public void testRangeCancel_success() throws RemoteException, ServiceSpecificException {
        ArrayList<MacAddress> macAddresses = new ArrayList<>();
        macAddresses.add(TEST_MAC_ADDRESS);
        when(mMockRttControllerAidlImpl.rangeCancel(anyInt(), any(ArrayList.class)))
                .thenReturn(true);
        assertTrue(mDut.rangeCancel(1, macAddresses));
        verify(mMockRttControllerAidlImpl).rangeCancel(eq(1), eq(macAddresses));
    }

    @Test
    public void testRangeCancel_failure() throws RemoteException, ServiceSpecificException {
        ArrayList<MacAddress> macAddresses = new ArrayList<>();
        macAddresses.add(TEST_MAC_ADDRESS);
        when(mMockRttControllerAidlImpl.rangeCancel(anyInt(), any(ArrayList.class)))
                .thenReturn(false);
        assertFalse(mDut.rangeCancel(1, macAddresses));
        verify(mMockRttControllerAidlImpl).rangeCancel(eq(1), eq(macAddresses));
    }

    @Test
    public void testDump() {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        mDut.dump(pw);
        verify(mMockRttControllerAidlImpl).dump(pw);
    }
}
