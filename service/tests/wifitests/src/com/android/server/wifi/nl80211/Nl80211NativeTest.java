/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.server.wifi.nl80211;

import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_ATTR_IFINDEX;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_CMD_GET_INTERFACE;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_CMD_NEW_SCAN_RESULTS;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_CMD_SCAN_ABORTED;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_CMD_SCHED_SCAN_RESULTS;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_CMD_SCHED_SCAN_STOPPED;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.net.wifi.ScanResult;
import android.net.wifi.WifiScanner;
import android.net.wifi.nl80211.WifiNl80211Manager;
import android.os.Bundle;

import androidx.test.filters.SmallTest;

import com.android.modules.utils.build.SdkLevel;
import com.android.net.module.util.netlink.StructNlMsgHdr;
import com.android.server.wifi.util.NetdWrapper;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;

/**
 * Unit tests for {@link Nl80211Native}.
 */
@SmallTest
public class Nl80211NativeTest {
    private Nl80211Native mDut;
    private static final String IFACE_NAME = "wlan0";
    private static final int IFACE_INDEX = 3;
    private static final String COUNTRY_CODE = "US";

    @Mock Nl80211Proxy mNl80211Proxy;
    @Mock Nl80211Utils mNl80211Utils;
    @Mock NetdWrapper mNetdWrapper;
    @Mock WifiNl80211Manager mWificondManager;
    @Mock Executor mExecutor;
    @Mock Nl80211Native.ScanEventCallback mScanCallback;
    @Mock Nl80211Native.ScanEventCallback mPnoScanCallback;
    @Mock WifiNl80211Manager.SoftApCallback mSoftApCallback;
    @Mock PnoSettings mPnoSettings;
    @Mock Nl80211Native.PnoScanRequestCallback mPnoScanRequestCallback;
    @Mock WifiNl80211Manager.SendMgmtFrameCallback mSendMgmtFrameCallback;
    @Mock Nl80211Native.CountryCodeChangedListener mCountryCodeChangedListener;
    @Mock Runnable mDeathEventHandler;

    ArgumentCaptor<Nl80211BroadcastMonitor.Nl80211BroadcastCallback>
            mNl80211BroadcastCallbackCaptor =
            ArgumentCaptor.forClass(Nl80211BroadcastMonitor.Nl80211BroadcastCallback.class);

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        when(mNl80211Proxy.initialize()).thenReturn(true);
        when(mNl80211Proxy.createNl80211Request(NL80211_CMD_GET_INTERFACE,
                StructNlMsgHdr.NLM_F_DUMP))
                .thenReturn(Nl80211TestUtils.createTestMessage());
    }

    private Nl80211Native initNl80211Native(boolean useWificond) {
        Nl80211Native nl80211Native = new Nl80211Native(mNl80211Proxy, mNl80211Utils, mNetdWrapper,
                mWificondManager, useWificond);
        nl80211Native.initialize();
        return nl80211Native;
    }

    /** Test that a scan result event invokes the correct callback. */
    @Test
    public void testBroadcastEvent_onNewScanResults_invokesScanCallback() {
        mDut = initNl80211Native(false);
        // Setup interface to register some scan callbacks that are driven by the broadcast.
        when(mNl80211Utils.getWiphyIndex(IFACE_NAME)).thenReturn(0);
        List<Nl80211Utils.InterfaceInfo> interfaces = new ArrayList<>();
        Nl80211Utils.InterfaceInfo expectedInfo = new Nl80211Utils.InterfaceInfo(
                IFACE_INDEX, 0, IFACE_NAME, new byte[6]);
        interfaces.add(expectedInfo);
        when(mNl80211Utils.getInterfaces(0)).thenReturn(interfaces);
        mDut.setupInterfaceForClientMode(IFACE_NAME, mExecutor, mScanCallback, mPnoScanCallback);
        verify(mNl80211Proxy).registerBroadcastCallback(eq(NL80211_CMD_NEW_SCAN_RESULTS),
                mNl80211BroadcastCallbackCaptor.capture());
        GenericNetlinkMsg genericNetlinkMessage = mock(GenericNetlinkMsg.class);
        when(genericNetlinkMessage.getAttributeValueAsInteger(NL80211_ATTR_IFINDEX))
                .thenReturn(IFACE_INDEX);

        mNl80211BroadcastCallbackCaptor.getValue().onEvent(NL80211_CMD_NEW_SCAN_RESULTS,
                genericNetlinkMessage);

        // Verify the registered scan callbacks are called.
        ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);
        verify(mExecutor).execute(runnableCaptor.capture());
        runnableCaptor.getValue().run();
        verify(mScanCallback).onScanResultReady();
        verify(mPnoScanCallback, never()).onScanResultReady();
    }

    /** Test that a scan aborted event invokes the correct callback. */
    @Test
    public void testBroadcastEvent_onScanAborted_invokesScanCallback() {
        // Required for onScanFailed(int);
        assumeTrue(SdkLevel.isAtLeastU());

        mDut = initNl80211Native(false);
        // Setup interface to register some scan callbacks that are driven by the broadcast.
        when(mNl80211Utils.getWiphyIndex(IFACE_NAME)).thenReturn(0);
        List<Nl80211Utils.InterfaceInfo> interfaces = new ArrayList<>();
        Nl80211Utils.InterfaceInfo expectedInfo = new Nl80211Utils.InterfaceInfo(
                IFACE_INDEX, 0, IFACE_NAME, new byte[6]);
        interfaces.add(expectedInfo);
        when(mNl80211Utils.getInterfaces(0)).thenReturn(interfaces);
        mDut.setupInterfaceForClientMode(IFACE_NAME, mExecutor, mScanCallback, mPnoScanCallback);
        verify(mNl80211Proxy).registerBroadcastCallback(eq(NL80211_CMD_SCAN_ABORTED),
                mNl80211BroadcastCallbackCaptor.capture());
        GenericNetlinkMsg genericNetlinkMessage = mock(GenericNetlinkMsg.class);
        when(genericNetlinkMessage.getAttributeValueAsInteger(NL80211_ATTR_IFINDEX))
                .thenReturn(IFACE_INDEX);

        mNl80211BroadcastCallbackCaptor.getValue().onEvent(NL80211_CMD_SCAN_ABORTED,
                genericNetlinkMessage);

        // Verify the registered scan callbacks are called.
        ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);
        verify(mExecutor).execute(runnableCaptor.capture());
        runnableCaptor.getValue().run();
        verify(mScanCallback).onScanFailed(WifiScanner.REASON_ABORT);
        verify(mPnoScanCallback, never()).onScanFailed();
    }

    /** Test that a PNO scan result event invokes the correct callback. */
    @Test
    public void testBroadcastEvent_onSchedScanResults_invokesPnoScanCallback() {
        mDut = initNl80211Native(false);
        // Setup interface to register some scan callbacks that are driven by the broadcast.
        when(mNl80211Utils.getWiphyIndex(IFACE_NAME)).thenReturn(0);
        List<Nl80211Utils.InterfaceInfo> interfaces = new ArrayList<>();
        Nl80211Utils.InterfaceInfo expectedInfo = new Nl80211Utils.InterfaceInfo(
                IFACE_INDEX, 0, IFACE_NAME, new byte[6]);
        interfaces.add(expectedInfo);
        when(mNl80211Utils.getInterfaces(0)).thenReturn(interfaces);
        mDut.setupInterfaceForClientMode(IFACE_NAME, mExecutor, mScanCallback, mPnoScanCallback);
        verify(mNl80211Proxy).registerBroadcastCallback(eq(NL80211_CMD_SCHED_SCAN_RESULTS),
                mNl80211BroadcastCallbackCaptor.capture());
        GenericNetlinkMsg genericNetlinkMessage = mock(GenericNetlinkMsg.class);
        when(genericNetlinkMessage.getAttributeValueAsInteger(NL80211_ATTR_IFINDEX))
                .thenReturn(IFACE_INDEX);

        mNl80211BroadcastCallbackCaptor.getValue().onEvent(NL80211_CMD_SCHED_SCAN_RESULTS,
                genericNetlinkMessage);

        // Verify the registered scan callbacks are called.
        ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);
        verify(mExecutor).execute(runnableCaptor.capture());
        runnableCaptor.getValue().run();
        verify(mPnoScanCallback).onScanResultReady();
        verify(mScanCallback, never()).onScanResultReady();
    }

    /** Test that a PNO scan stopped event invokes the correct callback. */
    @Test
    public void testBroadcastEvent_onSchedScanStopped_invokesPnoScanCallback() {
        mDut = initNl80211Native(false);
        // Setup interface to register some scan callbacks that are driven by the broadcast.
        when(mNl80211Utils.getWiphyIndex(IFACE_NAME)).thenReturn(0);
        List<Nl80211Utils.InterfaceInfo> interfaces = new ArrayList<>();
        Nl80211Utils.InterfaceInfo expectedInfo = new Nl80211Utils.InterfaceInfo(
                IFACE_INDEX, 0, IFACE_NAME, new byte[6]);
        interfaces.add(expectedInfo);
        when(mNl80211Utils.getInterfaces(0)).thenReturn(interfaces);
        mDut.setupInterfaceForClientMode(IFACE_NAME, mExecutor, mScanCallback, mPnoScanCallback);
        verify(mNl80211Proxy).registerBroadcastCallback(eq(NL80211_CMD_SCHED_SCAN_STOPPED),
                mNl80211BroadcastCallbackCaptor.capture());
        GenericNetlinkMsg genericNetlinkMessage = mock(GenericNetlinkMsg.class);
        when(genericNetlinkMessage.getAttributeValueAsInteger(NL80211_ATTR_IFINDEX))
                .thenReturn(IFACE_INDEX);

        mNl80211BroadcastCallbackCaptor.getValue().onEvent(NL80211_CMD_SCHED_SCAN_STOPPED,
                genericNetlinkMessage);

        // Verify the registered scan callbacks are called.
        ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);
        verify(mExecutor).execute(runnableCaptor.capture());
        runnableCaptor.getValue().run();
        verify(mPnoScanCallback).onScanFailed();
        verify(mScanCallback, never()).onScanFailed();
    }

    /** Test that an event for an unknown interface is ignored. */
    @Test
    public void testBroadcastEvent_forUnknownInterface_doesNothing() {
        mDut = initNl80211Native(false);
        // Setup interface to register some scan callbacks that are driven by the broadcast.
        when(mNl80211Utils.getWiphyIndex(IFACE_NAME)).thenReturn(0);
        List<Nl80211Utils.InterfaceInfo> interfaces = new ArrayList<>();
        Nl80211Utils.InterfaceInfo expectedInfo = new Nl80211Utils.InterfaceInfo(
                IFACE_INDEX, 0, IFACE_NAME, new byte[6]);
        interfaces.add(expectedInfo);
        when(mNl80211Utils.getInterfaces(0)).thenReturn(interfaces);
        mDut.setupInterfaceForClientMode(
                IFACE_NAME, mExecutor, mScanCallback, mPnoScanCallback);
        verify(mNl80211Proxy).registerBroadcastCallback(eq(NL80211_CMD_NEW_SCAN_RESULTS),
                mNl80211BroadcastCallbackCaptor.capture());
        GenericNetlinkMsg genericNetlinkMessage = mock(GenericNetlinkMsg.class);
        when(genericNetlinkMessage.getAttributeValueAsInteger(NL80211_ATTR_IFINDEX))
                .thenReturn(IFACE_INDEX + 1); // Different ifIndex

        mNl80211BroadcastCallbackCaptor.getValue().onEvent(NL80211_CMD_NEW_SCAN_RESULTS,
                genericNetlinkMessage);

        verify(mExecutor, never()).execute(any());
    }

    /** Test that {@link Nl80211Native#getInterfaceNames()} returns the expected value. */
    @Test
    public void testGetInterfaceNames_success_returnsInterfaceNames() {
        mDut = initNl80211Native(false);
        List<Nl80211Utils.InterfaceInfo> interfaces = new ArrayList<>();
        interfaces.add(new Nl80211Utils.InterfaceInfo(1, 0, IFACE_NAME, new byte[6]));
        when(mNl80211Utils.getInterfaces(anyInt())).thenReturn(interfaces);
        List<String> interfaceNames = mDut.getInterfaceNames();
        assertEquals(List.of(IFACE_NAME), interfaceNames);
    }

    /** Test that {@link Nl80211Native#getInterfaceNames()} returns null on failure. */
    @Test
    public void testGetInterfaceNames_failure_returnsNull() {
        mDut = initNl80211Native(false);
        when(mNl80211Utils.getInterfaces(anyInt())).thenReturn(null);
        List<String> interfaceNames = mDut.getInterfaceNames();
        assertNull(interfaceNames);
    }

    @Test
    public void testSetupInterfaceForClientMode_useWificondEnabled_callsWificond() {
        mDut = initNl80211Native(true);
        when(mWificondManager.setupInterfaceForClientMode(
                IFACE_NAME, mExecutor, mScanCallback, mPnoScanCallback)).thenReturn(true);
        assertTrue(mDut.setupInterfaceForClientMode(
                IFACE_NAME, mExecutor, mScanCallback, mPnoScanCallback));
        verify(mWificondManager).setupInterfaceForClientMode(
                IFACE_NAME, mExecutor, mScanCallback, mPnoScanCallback);
    }

    @Test
    public void testSetupInterfaceForClientMode_success() {
        mDut = initNl80211Native(false);
        when(mNl80211Utils.getWiphyIndex(IFACE_NAME)).thenReturn(0);
        List<Nl80211Utils.InterfaceInfo> interfaces = new ArrayList<>();
        Nl80211Utils.InterfaceInfo expectedInfo = new Nl80211Utils.InterfaceInfo(
                0, 0, IFACE_NAME, new byte[6]);
        interfaces.add(expectedInfo);
        when(mNl80211Utils.getInterfaces(0)).thenReturn(interfaces);

        assertTrue(mDut.setupInterfaceForClientMode(
                IFACE_NAME, mExecutor, mScanCallback, mPnoScanCallback));
        assertEquals(1, mDut.getClientInterfaceInfos().size());
        Nl80211Native.ClientInterfaceInfo clientInterfaceInfo =
                mDut.getClientInterfaceInfos().get(IFACE_NAME);
        assertNotNull(clientInterfaceInfo);
        assertEquals(IFACE_NAME, clientInterfaceInfo.ifName);
        assertEquals(mExecutor, clientInterfaceInfo.scanCallbackExecutor);
        assertEquals(mScanCallback, clientInterfaceInfo.scanEventCallback);
        assertEquals(mPnoScanCallback, clientInterfaceInfo.pnoScanEventCallback);
    }

    @Test
    public void testTearDownClientInterface() {
        mDut = initNl80211Native(false);
        when(mNl80211Utils.getWiphyIndex(IFACE_NAME)).thenReturn(0);
        List<Nl80211Utils.InterfaceInfo> interfaces = new ArrayList<>();
        Nl80211Utils.InterfaceInfo expectedInfo = new Nl80211Utils.InterfaceInfo(
                0, 0, IFACE_NAME, new byte[6]);
        interfaces.add(expectedInfo);
        when(mNl80211Utils.getInterfaces(0)).thenReturn(interfaces);

        assertTrue(mDut.setupInterfaceForClientMode(
                IFACE_NAME, mExecutor, mScanCallback, mPnoScanCallback));
        assertEquals(1, mDut.getClientInterfaceInfos().size());

        assertTrue(mDut.tearDownClientInterface(IFACE_NAME));
        assertEquals(0, mDut.getClientInterfaceInfos().size());
        assertNull(mDut.getClientInterfaceInfos().get(IFACE_NAME));
    }

    @Test
    public void testSetupInterfaceForClientMode_getWiphyIndexFails() {
        mDut = initNl80211Native(false);
        when(mNl80211Utils.getWiphyIndex(IFACE_NAME)).thenReturn(-1);

        assertEquals(false, mDut.setupInterfaceForClientMode(
                IFACE_NAME, mExecutor, mScanCallback, mPnoScanCallback));
    }

    @Test
    public void testSetupInterfaceForClientMode_getInterfacesFails() {
        mDut = initNl80211Native(false);
        when(mNl80211Utils.getWiphyIndex(IFACE_NAME)).thenReturn(0);
        when(mNl80211Utils.getInterfaces(0)).thenReturn(null);

        assertEquals(false, mDut.setupInterfaceForClientMode(
                IFACE_NAME, mExecutor, mScanCallback, mPnoScanCallback));
    }

    @Test
    public void testSetupInterfaceForClientMode_interfaceNotFound() {
        mDut = initNl80211Native(false);
        when(mNl80211Utils.getWiphyIndex(IFACE_NAME)).thenReturn(0);
        List<Nl80211Utils.InterfaceInfo> interfaces = new ArrayList<>();
        interfaces.add(new Nl80211Utils.InterfaceInfo(0, 0, "anotheriface", new byte[6]));
        when(mNl80211Utils.getInterfaces(0)).thenReturn(interfaces);

        assertEquals(false, mDut.setupInterfaceForClientMode(
                IFACE_NAME, mExecutor, mScanCallback, mPnoScanCallback));
    }

    @Test
    public void testTearDownClientInterface_useWificondEnabled_callsWificond() {
        mDut = initNl80211Native(true);
        when(mWificondManager.tearDownClientInterface(IFACE_NAME)).thenReturn(true);
        assertTrue(mDut.tearDownClientInterface(IFACE_NAME));
        verify(mWificondManager).tearDownClientInterface(IFACE_NAME);
    }

    @Test
    public void testSetupInterfaceForSoftApMode_useWificondEnabled_callsWificond() {
        mDut = initNl80211Native(true);
        when(mWificondManager.setupInterfaceForSoftApMode(IFACE_NAME)).thenReturn(true);
        assertTrue(mDut.setupInterfaceForSoftApMode(IFACE_NAME));
        verify(mWificondManager).setupInterfaceForSoftApMode(IFACE_NAME);
    }

    @Test
    public void testSetupInterfaceForSoftApMode_throwsException() {
        mDut = initNl80211Native(false);
        assertThrows(UnsupportedOperationException.class,
                () -> mDut.setupInterfaceForSoftApMode(IFACE_NAME));
    }

    @Test
    public void testTearDownSoftApInterface_useWificondEnabled_callsWificond() {
        mDut = initNl80211Native(true);
        when(mWificondManager.tearDownSoftApInterface(IFACE_NAME)).thenReturn(true);
        assertTrue(mDut.tearDownSoftApInterface(IFACE_NAME));
        verify(mWificondManager).tearDownSoftApInterface(IFACE_NAME);
    }

    @Test
    public void testTearDownSoftApInterface_throwsException() {
        mDut = initNl80211Native(false);
        assertThrows(UnsupportedOperationException.class,
                () -> mDut.tearDownSoftApInterface(IFACE_NAME));
    }

    @Test
    public void testTearDownInterfaces_useWificondEnabled_callsWificond() {
        mDut = initNl80211Native(true);
        when(mWificondManager.tearDownInterfaces()).thenReturn(true);
        assertTrue(mDut.tearDownInterfaces());
        verify(mWificondManager).tearDownInterfaces();
    }

    @Test
    public void testTearDownInterfaces_throwsException() {
        mDut = initNl80211Native(false);
        assertThrows(UnsupportedOperationException.class,
                () -> mDut.tearDownInterfaces());
    }

    @Test
    public void testRegisterWificondApCallback_useWificondEnabled_callsWificond() {
        mDut = initNl80211Native(true);
        when(mWificondManager.registerApCallback(IFACE_NAME, mExecutor, mSoftApCallback))
                .thenReturn(true);
        assertTrue(mDut.registerWificondApCallback(IFACE_NAME, mExecutor, mSoftApCallback));
        verify(mWificondManager).registerApCallback(IFACE_NAME, mExecutor, mSoftApCallback);
    }

    @Test
    public void testRegisterWificondApCallback_throwsException() {
        mDut = initNl80211Native(false);
        assertThrows(UnsupportedOperationException.class,
                () -> mDut.registerWificondApCallback(IFACE_NAME, mExecutor, mSoftApCallback));
    }

    @Test
    public void testStartScan_useWificondEnabled_callsWificond() {
        assumeTrue(SdkLevel.isAtLeastU());

        mDut = initNl80211Native(true);
        Set<Integer> freqs = new HashSet<>(List.of(2412));
        List<byte[]> ssids = new ArrayList<>();
        Bundle extras = new Bundle();
        when(mWificondManager.startScan2(
                IFACE_NAME, WifiScanner.SCAN_TYPE_HIGH_ACCURACY, freqs, ssids, extras))
                .thenReturn(WifiScanner.REASON_SUCCEEDED);
        assertEquals(WifiScanner.REASON_SUCCEEDED, mDut.startScan(
                IFACE_NAME, WifiScanner.SCAN_TYPE_HIGH_ACCURACY, freqs, ssids, extras));
        verify(mWificondManager).startScan2(
                IFACE_NAME, WifiScanner.SCAN_TYPE_HIGH_ACCURACY, freqs, ssids, extras);
    }

    @Test
    public void testStartScan_throwsException() {
        mDut = initNl80211Native(false);
        assertThrows(UnsupportedOperationException.class, () -> mDut.startScan(
                IFACE_NAME, WifiScanner.SCAN_TYPE_HIGH_ACCURACY, null, null, null));
    }

    @Test
    public void testStartScanPreU_useWificondEnabled_callsWificond() {
        mDut = initNl80211Native(true);
        when(mWificondManager.startScan(anyString(), anyInt(), any(), any(), any()))
                .thenReturn(true);
        assertTrue(mDut.startScanPreU(IFACE_NAME, 0, null, null, null));
        verify(mWificondManager).startScan(IFACE_NAME, 0, null, null, null);
    }

    @Test
    public void testStartScanPreU_returnsFalse() {
        mDut = initNl80211Native(false);
        assertThrows(UnsupportedOperationException.class,
                () -> mDut.startScanPreU(IFACE_NAME, 0, null, null, null));
    }

    @Test
    public void testGetScanResults_useWificondEnabled_callsWificond() {
        mDut = initNl80211Native(true);
        android.net.wifi.nl80211.NativeScanResult expectedScanResult =
                new android.net.wifi.nl80211.NativeScanResult();
        expectedScanResult.ssid = new byte[] {'a', 's', 'd', 'f'};
        expectedScanResult.bssid = new byte[] {0x01, 0x02, 0x03, 0x04, 0x05, 0x06};
        when(mWificondManager.getScanResults(IFACE_NAME, 0))
                .thenReturn(Collections.singletonList(expectedScanResult));

        List<NativeScanResult> results = mDut.getScanResults(IFACE_NAME, 0);

        assertNotNull(results);
        verify(mWificondManager).getScanResults(IFACE_NAME, 0);
        assertEquals(1, results.size());
        assertArrayEquals(expectedScanResult.getSsid(), results.get(0).getSsid());
    }

    @Test
    public void testGetScanResults_nullIfaceName() {
        mDut = initNl80211Native(false);
        List<NativeScanResult> results = mDut.getScanResults(null, 0);
        assertTrue(results.isEmpty());
    }

    @Test
    public void testGetScanResults_notInitialized() {
        Nl80211Native nl80211Native = new Nl80211Native(mNl80211Proxy, mNl80211Utils, mNetdWrapper,
                mWificondManager, false);
        List<NativeScanResult> results = nl80211Native.getScanResults(IFACE_NAME, 0);
        assertTrue(results.isEmpty());
    }

    @Test
    public void testGetScanResults_success() {
        mDut = initNl80211Native(false);
        List<NativeScanResult> expectedScanResults = new ArrayList<>();
        NativeScanResult scanResult = new NativeScanResult();
        scanResult.ssid = new byte[] {'T', 'E', 'S', 'T'};
        scanResult.bssid = new byte[] {0x11, 0x22, 0x33, 0x44, 0x55, 0x66};
        expectedScanResults.add(scanResult);
        when(mNl80211Utils.getScanResults(eq(IFACE_NAME)))
                .thenReturn(expectedScanResults);

        List<NativeScanResult> results = mDut.getScanResults(IFACE_NAME,
                Nl80211Native.SCAN_TYPE_SINGLE_SCAN);

        assertNotNull(results);
        assertEquals(1, results.size());
        assertEquals(scanResult, results.get(0));
        verify(mNl80211Utils).getScanResults(eq(IFACE_NAME));
    }

    @Test
    public void testStartPnoScan_useWificondEnabled_callsWificond() {
        mDut = initNl80211Native(true);

        // 1. Setup the input PnoSettings (the NEW server-side class)
        // Setup test PnoNetwork
        com.android.server.wifi.nl80211.PnoNetwork pnoNetwork =
                new com.android.server.wifi.nl80211.PnoNetwork();
        byte[] ssidBytes = "test-ssid".getBytes();
        int[] freqs = new int[]{2412, 5180, 5745};
        pnoNetwork.setHidden(true);
        pnoNetwork.setSsid(ssidBytes);
        pnoNetwork.setFrequenciesMhz(freqs);
        List<com.android.server.wifi.nl80211.PnoNetwork> pnoNetworkList = List.of(pnoNetwork);

        // Setup test PnoSettings
        com.android.server.wifi.nl80211.PnoSettings serverPnoSettings =
                new com.android.server.wifi.nl80211.PnoSettings();
        serverPnoSettings.setIntervalMillis(15000L);
        serverPnoSettings.setMin2gRssiDbm(-70);
        serverPnoSettings.setMin5gRssiDbm(-75);
        serverPnoSettings.setMin6gRssiDbm(-80);
        serverPnoSettings.setScanIterations(5);
        serverPnoSettings.setScanIntervalMultiplier(3);
        serverPnoSettings.setPnoNetworks(pnoNetworkList);

        // 2. Set the mock behavior for wificond
        when(mWificondManager.startPnoScan(
                eq(IFACE_NAME), any(), eq(mExecutor), eq(mPnoScanRequestCallback)))
                .thenReturn(true);

        // 3. Call the method under test
        assertTrue(mDut.startPnoScan(
                IFACE_NAME, serverPnoSettings, mExecutor, mPnoScanRequestCallback));

        // 4. Capture the argument passed to wificond
        ArgumentCaptor<android.net.wifi.nl80211.PnoSettings> wificondPnoSettingsCaptor =
                ArgumentCaptor.forClass(android.net.wifi.nl80211.PnoSettings.class);
        verify(mWificondManager).startPnoScan(eq(IFACE_NAME), wificondPnoSettingsCaptor.capture(),
                eq(mExecutor), eq(mPnoScanRequestCallback));

        // 5. Verify the captured (OLD/wificond) PnoSettings
        android.net.wifi.nl80211.PnoSettings capturedWificondSettings =
                wificondPnoSettingsCaptor.getValue();
        assertNotNull(capturedWificondSettings);

        // Verify all primitive fields were copied correctly
        assertEquals(serverPnoSettings.getIntervalMillis(),
                capturedWificondSettings.getIntervalMillis());
        assertEquals(serverPnoSettings.getMin2gRssiDbm(),
                capturedWificondSettings.getMin2gRssiDbm());
        assertEquals(serverPnoSettings.getMin5gRssiDbm(),
                capturedWificondSettings.getMin5gRssiDbm());
        assertEquals(serverPnoSettings.getMin6gRssiDbm(),
                capturedWificondSettings.getMin6gRssiDbm());
        if (SdkLevel.isAtLeastU()) {
            assertEquals(serverPnoSettings.getScanIterations(),
                    capturedWificondSettings.getScanIterations());
            assertEquals(serverPnoSettings.getScanIntervalMultiplier(),
                    capturedWificondSettings.getScanIntervalMultiplier());
        }

        // Verify the list of networks was converted
        assertNotNull(capturedWificondSettings.getPnoNetworks());
        assertEquals(1, capturedWificondSettings.getPnoNetworks().size());

        // Verify the contents of the (old/wificond) PnoNetwork
        android.net.wifi.nl80211.PnoNetwork capturedWificondNetwork =
                capturedWificondSettings.getPnoNetworks().get(0);
        assertNotNull(capturedWificondNetwork);
        assertEquals(pnoNetwork.isHidden(), capturedWificondNetwork.isHidden());
        assertArrayEquals(pnoNetwork.getSsid(), capturedWificondNetwork.getSsid());
        assertArrayEquals(pnoNetwork.getFrequenciesMhz(),
                capturedWificondNetwork.getFrequenciesMhz());
    }

    @Test
    public void testStartPnoScan_throwsException() {
        mDut = initNl80211Native(false);
        assertThrows(UnsupportedOperationException.class,
                () -> mDut.startPnoScan(
                        IFACE_NAME, mPnoSettings, mExecutor, mPnoScanRequestCallback));
    }

    @Test
    public void testStopPnoScan_useWificondEnabled_callsWificond() {
        mDut = initNl80211Native(true);
        when(mWificondManager.stopPnoScan(IFACE_NAME)).thenReturn(true);
        assertTrue(mDut.stopPnoScan(IFACE_NAME));
        verify(mWificondManager).stopPnoScan(IFACE_NAME);
    }

    @Test
    public void testStopPnoScan_throwsException() {
        mDut = initNl80211Native(false);
        assertThrows(UnsupportedOperationException.class,
                () -> mDut.stopPnoScan(IFACE_NAME));
    }

    @Test
    public void testAbortScan_useWificondEnabled_callsWificond() {
        mDut = initNl80211Native(true);
        mDut.abortScan(IFACE_NAME);
        verify(mWificondManager).abortScan(IFACE_NAME);
    }

    @Test
    public void testAbortScan_throwsException() {
        mDut = initNl80211Native(false);
        assertThrows(UnsupportedOperationException.class, () -> mDut.abortScan(IFACE_NAME));
    }

    @Test
    public void testWificondSignalPoll_useWificondEnabled_callsWificond() {
        mDut = initNl80211Native(true);
        WifiNl80211Manager.SignalPollResult expectedResult =
                mock(WifiNl80211Manager.SignalPollResult.class);
        when(mWificondManager.signalPoll(IFACE_NAME)).thenReturn(expectedResult);
        assertEquals(expectedResult, mDut.wificondSignalPoll(IFACE_NAME));
        verify(mWificondManager).signalPoll(IFACE_NAME);
    }

    @Test
    public void testWificondSignalPoll_throwsException() {
        mDut = initNl80211Native(false);
        assertThrows(UnsupportedOperationException.class,
                () -> mDut.wificondSignalPoll(IFACE_NAME));
    }

    @Test
    public void testGetDeviceWiphyCapabilities_useWificondEnabled_callsWificond() {
        mDut = initNl80211Native(true);
        android.net.wifi.nl80211.DeviceWiphyCapabilities wificondCaps =
                new android.net.wifi.nl80211.DeviceWiphyCapabilities();
        when(mWificondManager.getDeviceWiphyCapabilities(IFACE_NAME)).thenReturn(wificondCaps);
        assertEquals(new DeviceWiphyCapabilities(wificondCaps),
                mDut.getDeviceWiphyCapabilities(IFACE_NAME));
        verify(mWificondManager).getDeviceWiphyCapabilities(IFACE_NAME);
    }

    @Test
    public void testGetDeviceWiphyCapabilities_success() {
        mDut = initNl80211Native(false);
        Nl80211Utils.BandInfo bandInfo = new Nl80211Utils.BandInfo();
        bandInfo.is80211nSupported = true;
        bandInfo.is80211acSupported = true;
        bandInfo.is80211axSupported = true;
        bandInfo.is80211beSupported = true;
        bandInfo.is160MhzSupported = true;
        bandInfo.is80p80MhzSupported = true;
        bandInfo.is320MhzSupported = true;
        bandInfo.maxTxStreams = 8;
        bandInfo.maxRxStreams = 4;
        Nl80211Utils.ScanCapabilities scanCapabilities =
                new Nl80211Utils.ScanCapabilities(0, 0, 0, 0, 0, 0);
        Nl80211Utils.WiphyFeatures wiphyFeatures =
                new Nl80211Utils.WiphyFeatures(false, false, false, false, false, false, false);
        Nl80211Utils.DriverCapabilities driverCapabilities =
                new Nl80211Utils.DriverCapabilities(5);
        Nl80211Utils.WiphyInfo wiphyInfo = new Nl80211Utils.WiphyInfo(
                bandInfo, scanCapabilities, wiphyFeatures, driverCapabilities);

        when(mNl80211Utils.getWiphyIndex(IFACE_NAME)).thenReturn(0);
        when(mNl80211Utils.getWiphyInfo(0)).thenReturn(wiphyInfo);

        DeviceWiphyCapabilities capabilities = mDut.getDeviceWiphyCapabilities(IFACE_NAME);

        assertNotNull(capabilities);
        assertTrue(capabilities.isWifiStandardSupported(ScanResult.WIFI_STANDARD_11N));
        assertTrue(capabilities.isWifiStandardSupported(ScanResult.WIFI_STANDARD_11AC));
        assertTrue(capabilities.isWifiStandardSupported(ScanResult.WIFI_STANDARD_11AX));
        assertTrue(capabilities.isWifiStandardSupported(ScanResult.WIFI_STANDARD_11BE));
        assertTrue(capabilities.isChannelWidthSupported(ScanResult.CHANNEL_WIDTH_160MHZ));
        assertTrue(capabilities.isChannelWidthSupported(
                ScanResult.CHANNEL_WIDTH_80MHZ_PLUS_MHZ));
        assertTrue(capabilities.isChannelWidthSupported(ScanResult.CHANNEL_WIDTH_320MHZ));
        assertEquals(8, capabilities.getMaxNumberTxSpatialStreams());
        assertEquals(4, capabilities.getMaxNumberRxSpatialStreams());
        assertEquals(5, capabilities.getMaxNumberAkms());
    }

    @Test
    public void testGetDeviceWiphyCapabilities_getWiphyIndexFails() {
        mDut = initNl80211Native(false);
        when(mNl80211Utils.getWiphyIndex(IFACE_NAME)).thenReturn(-1);
        assertNull(mDut.getDeviceWiphyCapabilities(IFACE_NAME));
    }

    @Test
    public void testGetDeviceWiphyCapabilities_getWiphyInfoFails() {
        mDut = initNl80211Native(false);
        when(mNl80211Utils.getWiphyIndex(IFACE_NAME)).thenReturn(0);
        when(mNl80211Utils.getWiphyInfo(0)).thenReturn(null);
        assertNull(mDut.getDeviceWiphyCapabilities(IFACE_NAME));
    }

    @Test
    public void testGetDeviceWiphyCapabilities_notInitialized() {
        Nl80211Native nl80211Native = new Nl80211Native(mNl80211Proxy, mNl80211Utils, mNetdWrapper,
                mWificondManager, false);
        assertNull(nl80211Native.getDeviceWiphyCapabilities(IFACE_NAME));
    }

    @Test
    public void testGetChannelsMhzForBand_useWificondEnabled_callsWificond() {
        mDut = initNl80211Native(true);
        int[] channels = {2412, 2417};
        when(mWificondManager.getChannelsMhzForBand(anyInt())).thenReturn(channels);
        assertArrayEquals(channels, mDut.getChannelsMhzForBand(0));
        verify(mWificondManager).getChannelsMhzForBand(0);
    }

    @Test
    public void testGetChannelsMhzForBand_throwsException() {
        mDut = initNl80211Native(false);
        assertThrows(UnsupportedOperationException.class,
                () -> mDut.getChannelsMhzForBand(0));
    }

    @Test
    public void testGetMaxSsidsPerScan_useWificondEnabled_callsWificond() {
        assumeTrue(SdkLevel.isAtLeastT());
        mDut = initNl80211Native(true);
        when(mWificondManager.getMaxSsidsPerScan(IFACE_NAME)).thenReturn(16);
        assertEquals(16, mDut.getMaxSsidsPerScan(IFACE_NAME));
        verify(mWificondManager).getMaxSsidsPerScan(IFACE_NAME);
    }

    @Test
    public void testGetMaxSsidsPerScan_success() {
        mDut = initNl80211Native(false);
        final int maxSsids = 16;
        Nl80211Utils.ScanCapabilities scanCaps = new Nl80211Utils.ScanCapabilities(
                maxSsids, 0, 0, 0, 0, 0);
        Nl80211Utils.WiphyInfo wiphyInfo = new Nl80211Utils.WiphyInfo(
                new Nl80211Utils.BandInfo(),
                scanCaps,
                mock(Nl80211Utils.WiphyFeatures.class),
                mock(Nl80211Utils.DriverCapabilities.class));

        when(mNl80211Utils.getWiphyIndex(IFACE_NAME)).thenReturn(0);
        when(mNl80211Utils.getWiphyInfo(0)).thenReturn(wiphyInfo);

        assertEquals(maxSsids, mDut.getMaxSsidsPerScan(IFACE_NAME));
    }

    @Test
    public void testGetMaxSsidsPerScan_getWiphyIndexFails() {
        mDut = initNl80211Native(false);
        when(mNl80211Utils.getWiphyIndex(IFACE_NAME)).thenReturn(-1);
        assertEquals(0, mDut.getMaxSsidsPerScan(IFACE_NAME));
    }

    @Test
    public void testGetMaxSsidsPerScan_getWiphyInfoFails() {
        mDut = initNl80211Native(false);
        when(mNl80211Utils.getWiphyIndex(IFACE_NAME)).thenReturn(0);
        when(mNl80211Utils.getWiphyInfo(0)).thenReturn(null);
        assertEquals(0, mDut.getMaxSsidsPerScan(IFACE_NAME));
    }

    @Test
    public void testSendMgmtFrame_useWificondEnabled_callsWificond() {
        mDut = initNl80211Native(true);
        byte[] frame = new byte[1];
        mDut.sendMgmtFrame(IFACE_NAME, frame, 0, mExecutor, mSendMgmtFrameCallback);
        verify(mWificondManager).sendMgmtFrame(
                eq(IFACE_NAME), eq(frame), eq(0), eq(mExecutor),
                eq(mSendMgmtFrameCallback));
    }

    @Test
    public void testSendMgmtFrame_throwsException() {
        mDut = initNl80211Native(false);
        assertThrows(UnsupportedOperationException.class,
                () -> mDut.sendMgmtFrame(
                        IFACE_NAME, new byte[1], 0, mExecutor, mSendMgmtFrameCallback));
    }

    @Test
    public void testRegisterCountryCodeChangedListener_useWificondEnabled_callsWificond() {
        mDut = initNl80211Native(true);
        when(mWificondManager.registerCountryCodeChangedListener(
                mExecutor, mCountryCodeChangedListener)).thenReturn(true);
        assertTrue(mDut.registerCountryCodeChangedListener(
                mExecutor, mCountryCodeChangedListener));
        verify(mWificondManager).registerCountryCodeChangedListener(
                mExecutor, mCountryCodeChangedListener);
    }

    @Test
    public void testRegisterCountryCodeChangedListener_throwsException() {
        mDut = initNl80211Native(false);
        assertThrows(UnsupportedOperationException.class,
                () -> mDut.registerCountryCodeChangedListener(
                        mExecutor, mCountryCodeChangedListener));
    }

    @Test
    public void testUnregisterCountryCodeChangedListener_useWificondEnabled_callsWificond() {
        mDut = initNl80211Native(true);
        mDut.unregisterCountryCodeChangedListener(mCountryCodeChangedListener);
        verify(mWificondManager).unregisterCountryCodeChangedListener(
                mCountryCodeChangedListener);
    }

    @Test
    public void testUnregisterCountryCodeChangedListener_throwsException() {
        mDut = initNl80211Native(false);
        assertThrows(UnsupportedOperationException.class,
                () -> mDut.unregisterCountryCodeChangedListener(mCountryCodeChangedListener));
    }

    @Test
    public void testNotifyCountryCodeChanged_useWificondEnabled_callsWificond() {
        assumeTrue(SdkLevel.isAtLeastT());
        mDut = initNl80211Native(true);
        mDut.notifyCountryCodeChanged(COUNTRY_CODE);
        verify(mWificondManager).notifyCountryCodeChanged(COUNTRY_CODE);
    }

    @Test
    public void testNotifyCountryCodeChanged_throwsException() {
        mDut = initNl80211Native(false);
        assertThrows(UnsupportedOperationException.class,
                () -> mDut.notifyCountryCodeChanged(COUNTRY_CODE));
    }

    @Test
    public void testSetWificondOnServiceDeadCallback_useWificondEnabled_callsWificond() {
        mDut = initNl80211Native(true);
        mDut.setWificondOnServiceDeadCallback(mDeathEventHandler);
        verify(mWificondManager).setOnServiceDeadCallback(mDeathEventHandler);
    }

    @Test
    public void testSetWificondOnServiceDeadCallback_doesNotCallWificond() {
        mDut = initNl80211Native(false);
        mDut.setWificondOnServiceDeadCallback(mDeathEventHandler);
        verify(mWificondManager, never()).setOnServiceDeadCallback(any());
    }

    @Test
    public void testEnableVerboseLogging_useWificondEnabled_callsWificond() {
        mDut = initNl80211Native(true);
        mDut.enableVerboseLogging(true);
        verify(mWificondManager).enableVerboseLogging(true);
    }

    @Test
    public void testEnableVerboseLogging_doesNotCallWificond() {
        mDut = initNl80211Native(false);
        mDut.enableVerboseLogging(true);
        verify(mWificondManager, never()).enableVerboseLogging(anyBoolean());
    }
}