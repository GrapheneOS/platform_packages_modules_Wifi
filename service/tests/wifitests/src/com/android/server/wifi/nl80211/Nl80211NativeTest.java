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

import static com.android.dx.mockito.inline.extended.ExtendedMockito.doThrow;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_ATTR_CHANNEL_WIDTH;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_ATTR_IFINDEX;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_ATTR_MAC;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_ATTR_REG_ALPHA2;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_ATTR_REG_TYPE;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_ATTR_WIPHY_FREQ;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_CMD_CH_SWITCH_NOTIFY;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_CMD_DEL_STATION;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_CMD_GET_INTERFACE;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_CMD_NEW_SCAN_RESULTS;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_CMD_NEW_STATION;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_CMD_REG_CHANGE;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_CMD_SCAN_ABORTED;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_CMD_SCHED_SCAN_RESULTS;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_CMD_SCHED_SCAN_STOPPED;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_REGDOM_TYPE_COUNTRY;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_REGDOM_TYPE_WORLD;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_SCAN_FLAG_HIGH_ACCURACY;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_SCAN_FLAG_LOW_POWER;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_SCAN_FLAG_LOW_SPAN;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.net.MacAddress;
import android.net.wifi.ScanResult;
import android.net.wifi.SoftApInfo;
import android.net.wifi.WifiAnnotations;
import android.net.wifi.WifiScanner;
import android.net.wifi.nl80211.NativeWifiClient;
import android.net.wifi.nl80211.WifiNl80211Manager;
import android.os.Bundle;

import androidx.test.filters.SmallTest;

import com.android.modules.utils.build.SdkLevel;
import com.android.net.module.util.netlink.StructNlAttr;
import com.android.net.module.util.netlink.StructNlMsgHdr;
import com.android.server.wifi.SelfRecovery;
import com.android.server.wifi.WifiInjector;
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
    private static final int WIPHY_INDEX_0 = 0;
    private static final int WIPHY_INDEX_1 = 1;
    private static final String CLIENT_IFACE_NAME = "wlan0";
    private static final int CLIENT_IFACE_INDEX = 3;
    private static final byte[] TEST_BSSID = new byte[]{0x0A, 0x0B, 0x0C, 0x0D, 0x0E, 0x0F};
    private static final byte[] TEST_BSSID_2 = new byte[]{0x0B, 0x0B, 0x0C, 0x0D, 0x0E, 0x0F};
    private static final int TEST_FREQ = 2412;
    private static final int TEST_FREQ_2 = 2417;
    private static final String AP_IFACE_NAME = "wlan1";
    private static final int AP_IFACE_INDEX = 4;
    private static final String COUNTRY_CODE = "US";

    @Mock
    Nl80211Proxy mNl80211Proxy;
    @Mock
    Nl80211Utils mNl80211Utils;
    @Mock
    NetdWrapper mNetdWrapper;
    @Mock
    WifiNl80211Manager mWificondManager;
    @Mock
    WifiInjector mWifiInjector;
    @Mock
    SelfRecovery mSelfRecovery;
    @Mock
    Executor mExecutor;
    @Mock
    Nl80211Native.ScanEventCallback mScanCallback;
    @Mock
    Nl80211Native.ScanEventCallback mPnoScanCallback;
    @Mock
    WifiNl80211Manager.SoftApCallback mSoftApCallback;
    @Mock
    PnoSettings mPnoSettings;
    @Mock
    Nl80211Native.PnoScanRequestCallback mPnoScanRequestCallback;
    @Mock
    WifiNl80211Manager.SendMgmtFrameCallback mSendMgmtFrameCallback;
    @Mock
    Nl80211Native.CountryCodeChangedListener mCountryCodeChangedListener;
    @Mock
    Runnable mDeathEventHandler;

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
        when(mWifiInjector.getSelfRecovery()).thenReturn(mSelfRecovery);
    }

    private Nl80211Native initNl80211Native(boolean useWificond) {
        Nl80211Native nl80211Native = new Nl80211Native(mNl80211Proxy, mNl80211Utils, mNetdWrapper,
                mWificondManager, mWifiInjector, useWificond);
        nl80211Native.initialize();
        return nl80211Native;
    }

    /**
     * Sets up a client mode interface which is a prerequisite for certain methods.
     */
    private void setupClientModeInterfaceForTest(int wiphyIndex,
            @Nullable Nl80211Utils.BandInfo bandInfo,
            @Nullable Nl80211Utils.ScanCapabilities scanCapabilities,
            @Nullable Nl80211Utils.WiphyFeatures wiphyFeatures) {
        Nl80211Utils.InterfaceInfo ifaceInfo = new Nl80211Utils.InterfaceInfo(
                CLIENT_IFACE_INDEX, wiphyIndex, CLIENT_IFACE_NAME, new byte[6]);
        when(mNl80211Utils.getInterfaceInfo(CLIENT_IFACE_NAME)).thenReturn(ifaceInfo);
        // Mock a basic WiphyInfo response for setupInterfaceForClientMode to succeed.
        if (bandInfo == null) {
            bandInfo = new Nl80211Utils.BandInfo();
        }
        if (scanCapabilities == null) {
            scanCapabilities = mock(Nl80211Utils.ScanCapabilities.class);
        }
        if (wiphyFeatures == null) {
            wiphyFeatures = mock(Nl80211Utils.WiphyFeatures.class);
        }
        Nl80211Utils.WiphyInfo wiphyInfo = new Nl80211Utils.WiphyInfo(
                bandInfo,
                scanCapabilities,
                wiphyFeatures,
                mock(Nl80211Utils.DriverCapabilities.class));
        when(mNl80211Utils.getWiphyInfo(wiphyIndex)).thenReturn(wiphyInfo);
        mDut.setupInterfaceForClientMode(
                CLIENT_IFACE_NAME, mExecutor, mScanCallback, mPnoScanCallback);
    }

    /** Test that a scan result event invokes the correct callback. */
    @Test
    public void testBroadcastEvent_onNewScanResults_invokesScanCallback() {
        mDut = initNl80211Native(false);
        setupClientModeInterfaceForTest(WIPHY_INDEX_0, null, null, null);
        verify(mNl80211Proxy).registerBroadcastCallback(eq(NL80211_CMD_NEW_SCAN_RESULTS),
                mNl80211BroadcastCallbackCaptor.capture());
        GenericNetlinkMsg genericNetlinkMessage = mock(GenericNetlinkMsg.class);
        when(genericNetlinkMessage.getAttributeValueAsInteger(NL80211_ATTR_IFINDEX))
                .thenReturn(CLIENT_IFACE_INDEX);

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
        setupClientModeInterfaceForTest(WIPHY_INDEX_0, null, null, null);
        verify(mNl80211Proxy).registerBroadcastCallback(eq(NL80211_CMD_SCAN_ABORTED),
                mNl80211BroadcastCallbackCaptor.capture());
        GenericNetlinkMsg genericNetlinkMessage = mock(GenericNetlinkMsg.class);
        when(genericNetlinkMessage.getAttributeValueAsInteger(NL80211_ATTR_IFINDEX))
                .thenReturn(CLIENT_IFACE_INDEX);

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
        setupClientModeInterfaceForTest(WIPHY_INDEX_0, null, null, null);
        verify(mNl80211Proxy).registerBroadcastCallback(eq(NL80211_CMD_SCHED_SCAN_RESULTS),
                mNl80211BroadcastCallbackCaptor.capture());
        GenericNetlinkMsg genericNetlinkMessage = mock(GenericNetlinkMsg.class);
        when(genericNetlinkMessage.getAttributeValueAsInteger(NL80211_ATTR_IFINDEX))
                .thenReturn(CLIENT_IFACE_INDEX);

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
        setupClientModeInterfaceForTest(WIPHY_INDEX_0, null, null, null);
        verify(mNl80211Proxy).registerBroadcastCallback(eq(NL80211_CMD_SCHED_SCAN_STOPPED),
                mNl80211BroadcastCallbackCaptor.capture());
        GenericNetlinkMsg genericNetlinkMessage = mock(GenericNetlinkMsg.class);
        when(genericNetlinkMessage.getAttributeValueAsInteger(NL80211_ATTR_IFINDEX))
                .thenReturn(CLIENT_IFACE_INDEX);

        mNl80211BroadcastCallbackCaptor.getValue().onEvent(NL80211_CMD_SCHED_SCAN_STOPPED,
                genericNetlinkMessage);

        // Verify the registered scan callbacks are called.
        ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);
        verify(mExecutor).execute(runnableCaptor.capture());
        runnableCaptor.getValue().run();
        verify(mPnoScanCallback).onScanFailed();
        verify(mScanCallback, never()).onScanFailed();
    }

    @Test
    public void testBroadcastEvent_onNewStation_invokesApCallback() {
        mDut = initNl80211Native(false);
        setupSoftApInterfaceForTest(WIPHY_INDEX_0, null);
        mDut.registerApCallback(AP_IFACE_NAME, mExecutor, mSoftApCallback);
        verify(mNl80211Proxy).registerBroadcastCallback(eq(NL80211_CMD_NEW_STATION),
                mNl80211BroadcastCallbackCaptor.capture());
        GenericNetlinkMsg newStationMsg = mock(GenericNetlinkMsg.class);
        when(newStationMsg.getAttributeValueAsInteger(NL80211_ATTR_IFINDEX))
                .thenReturn(AP_IFACE_INDEX);
        byte[] macAddress = new byte[]{0x00, 0x11, 0x22, 0x33, 0x44, 0x55};
        when(newStationMsg.getAttributeValueAsByteArray(NL80211_ATTR_MAC)).thenReturn(macAddress);

        mNl80211BroadcastCallbackCaptor.getValue().onEvent(NL80211_CMD_NEW_STATION, newStationMsg);

        ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);
        verify(mExecutor).execute(runnableCaptor.capture());
        runnableCaptor.getValue().run();
        ArgumentCaptor<NativeWifiClient> clientCaptor =
                ArgumentCaptor.forClass(NativeWifiClient.class);
        verify(mSoftApCallback).onConnectedClientsChanged(clientCaptor.capture(), eq(true));
        assertEquals(MacAddress.fromBytes(macAddress), clientCaptor.getValue().getMacAddress());
    }

    @Test
    public void testBroadcastEvent_onDelStation_invokesApCallback() {
        mDut = initNl80211Native(false);
        setupSoftApInterfaceForTest(WIPHY_INDEX_0, null);
        mDut.registerApCallback(AP_IFACE_NAME, mExecutor, mSoftApCallback);
        verify(mNl80211Proxy).registerBroadcastCallback(eq(NL80211_CMD_DEL_STATION),
                mNl80211BroadcastCallbackCaptor.capture());
        GenericNetlinkMsg delStationMsg = mock(GenericNetlinkMsg.class);
        when(delStationMsg.getAttributeValueAsInteger(NL80211_ATTR_IFINDEX))
                .thenReturn(AP_IFACE_INDEX);
        byte[] macAddress = new byte[]{0x00, 0x11, 0x22, 0x33, 0x44, 0x55};
        when(delStationMsg.getAttributeValueAsByteArray(NL80211_ATTR_MAC)).thenReturn(macAddress);

        mNl80211BroadcastCallbackCaptor.getValue().onEvent(NL80211_CMD_DEL_STATION, delStationMsg);

        ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);
        verify(mExecutor).execute(runnableCaptor.capture());
        runnableCaptor.getValue().run();
        ArgumentCaptor<NativeWifiClient> clientCaptor =
                ArgumentCaptor.forClass(NativeWifiClient.class);
        verify(mSoftApCallback).onConnectedClientsChanged(clientCaptor.capture(), eq(false));
        assertEquals(MacAddress.fromBytes(macAddress), clientCaptor.getValue().getMacAddress());
    }

    @Test
    public void testBroadcastEvent_onChannelSwitch_invokesApCallback() {
        mDut = initNl80211Native(false);
        setupSoftApInterfaceForTest(WIPHY_INDEX_0, null);
        mDut.registerApCallback(AP_IFACE_NAME, mExecutor, mSoftApCallback);
        verify(mNl80211Proxy).registerBroadcastCallback(eq(NL80211_CMD_CH_SWITCH_NOTIFY),
                mNl80211BroadcastCallbackCaptor.capture());
        GenericNetlinkMsg chSwitchNotifyMsg = mock(GenericNetlinkMsg.class);
        when(chSwitchNotifyMsg.getAttributeValueAsInteger(NL80211_ATTR_IFINDEX))
                .thenReturn(AP_IFACE_INDEX);
        int frequency = 5220;
        int channelWidth = NetlinkConstants.NL80211_CHAN_WIDTH_80;
        when(chSwitchNotifyMsg.getAttributeValueAsInteger(NL80211_ATTR_WIPHY_FREQ))
                .thenReturn(frequency);
        when(chSwitchNotifyMsg.getAttributeValueAsInteger(NL80211_ATTR_CHANNEL_WIDTH))
                .thenReturn(channelWidth);

        mNl80211BroadcastCallbackCaptor.getValue().onEvent(NL80211_CMD_CH_SWITCH_NOTIFY,
                chSwitchNotifyMsg);

        ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);
        verify(mExecutor).execute(runnableCaptor.capture());
        runnableCaptor.getValue().run();
        verify(mSoftApCallback).onSoftApChannelSwitched(frequency,
                SoftApInfo.CHANNEL_WIDTH_80MHZ);
    }

    /** Test that an event for an unknown interface is ignored. */
    @Test
    public void testBroadcastEvent_forUnknownInterface_doesNothing() {
        mDut = initNl80211Native(false);
        // Setup interface to register some scan callbacks that are driven by the broadcast.
        setupClientModeInterfaceForTest(WIPHY_INDEX_0, null, null, null);
        verify(mNl80211Proxy).registerBroadcastCallback(eq(NL80211_CMD_NEW_SCAN_RESULTS),
                mNl80211BroadcastCallbackCaptor.capture());
        GenericNetlinkMsg genericNetlinkMessage = mock(GenericNetlinkMsg.class);
        when(genericNetlinkMessage.getAttributeValueAsInteger(NL80211_ATTR_IFINDEX))
                .thenReturn(AP_IFACE_INDEX); // Different ifIndex

        mNl80211BroadcastCallbackCaptor.getValue().onEvent(NL80211_CMD_NEW_SCAN_RESULTS,
                genericNetlinkMessage);

        verify(mExecutor, never()).execute(any());
    }

    /** Test that an associate event updates the client interface info. */
    @Test
    public void testBroadcastEvent_onAssociate_updatesClientInfo() {
        NativeScanResult scanResult = new NativeScanResult();
        scanResult.bssid = TEST_BSSID;
        scanResult.frequency = TEST_FREQ;
        scanResult.associated = true;
        when(mNl80211Utils.getScanResults(CLIENT_IFACE_NAME)).thenReturn(List.of(scanResult));

        GenericNetlinkMsg associateMsg = mock(GenericNetlinkMsg.class);
        when(associateMsg.getAttributeValueAsInteger(NL80211_ATTR_IFINDEX))
                .thenReturn(CLIENT_IFACE_INDEX);
        when(associateMsg.getAttributeValueAsByteArray(NL80211_ATTR_MAC)).thenReturn(TEST_BSSID);

        testAssociationEvent(NetlinkConstants.NL80211_CMD_ASSOCIATE, associateMsg,
                /* expectSuccess */ true, scanResult.bssid, scanResult.frequency);
    }

    private void testAssociationEvent(short command, @NonNull GenericNetlinkMsg eventMsg,
            boolean expectSuccess, @Nullable byte[] expectedBssid, int expectedFreq) {
        // Setup
        mDut = initNl80211Native(false);
        setupClientModeInterfaceForTest(WIPHY_INDEX_0, null, null, null);
        ArgumentCaptor<Nl80211BroadcastMonitor.Nl80211BroadcastCallback> callbackCaptor =
                ArgumentCaptor.forClass(Nl80211BroadcastMonitor.Nl80211BroadcastCallback.class);
        verify(mNl80211Proxy).registerBroadcastCallback(eq(command), callbackCaptor.capture());

        // Trigger
        callbackCaptor.getValue().onEvent(command, eventMsg);

        // Verify
        Nl80211Native.ClientInterfaceInfo clientIfaceInfo =
                mDut.getClientInterfaceInfos().get(CLIENT_IFACE_NAME);
        assertEquals(expectSuccess, clientIfaceInfo.associated);
        assertArrayEquals(expectedBssid, clientIfaceInfo.associatedBssid);
        assertEquals(expectedFreq, clientIfaceInfo.associatedFreqMhz);
    }

    @Test
    public void testBroadcastEvent_onConnect_success() {
        NativeScanResult scanResult = new NativeScanResult();
        scanResult.bssid = TEST_BSSID;
        scanResult.frequency = TEST_FREQ;
        scanResult.associated = true;
        when(mNl80211Utils.getScanResults(CLIENT_IFACE_NAME)).thenReturn(List.of(scanResult));

        GenericNetlinkMsg connectMsg = mock(GenericNetlinkMsg.class);
        when(connectMsg.getAttributeValueAsInteger(NL80211_ATTR_IFINDEX))
                .thenReturn(CLIENT_IFACE_INDEX);
        when(connectMsg.getAttributeValueAsByteArray(NL80211_ATTR_MAC))
                .thenReturn(scanResult.bssid);
        when(connectMsg.getAttributeValueAsShort(eq(NetlinkConstants.NL80211_ATTR_STATUS_CODE)))
                .thenReturn((short) 0);

        testAssociationEvent(NetlinkConstants.NL80211_CMD_CONNECT, connectMsg,
                /* expectSuccess */ true, scanResult.bssid, scanResult.frequency);
    }

    @Test
    public void testBroadcastEvent_onRoam_success() {
        NativeScanResult scanResult = new NativeScanResult();
        scanResult.bssid = TEST_BSSID;
        scanResult.frequency = TEST_FREQ;
        scanResult.associated = true;
        when(mNl80211Utils.getScanResults(CLIENT_IFACE_NAME)).thenReturn(List.of(scanResult));

        GenericNetlinkMsg roamMsg = mock(GenericNetlinkMsg.class);
        when(roamMsg.getAttributeValueAsInteger(NL80211_ATTR_IFINDEX))
                .thenReturn(CLIENT_IFACE_INDEX);
        when(roamMsg.getAttributeValueAsByteArray(NL80211_ATTR_MAC)).thenReturn(scanResult.bssid);

        testAssociationEvent(NetlinkConstants.NL80211_CMD_ROAM, roamMsg, /* expectSuccess */ true,
                scanResult.bssid, scanResult.frequency);
    }

    @Test
    public void testBroadcastEvent_onAssociate_successEvenWithStatusCode() {
        // This test verifies parity with wificond, which ignores status code for ASSOCIATE events.
        NativeScanResult scanResult = new NativeScanResult();
        scanResult.bssid = TEST_BSSID;
        scanResult.frequency = TEST_FREQ;
        scanResult.associated = true;
        when(mNl80211Utils.getScanResults(CLIENT_IFACE_NAME)).thenReturn(List.of(scanResult));

        GenericNetlinkMsg associateMsg = mock(GenericNetlinkMsg.class);
        when(associateMsg.getAttributeValueAsInteger(NL80211_ATTR_IFINDEX))
                .thenReturn(CLIENT_IFACE_INDEX);
        when(associateMsg.getAttributeValueAsByteArray(NL80211_ATTR_MAC))
                .thenReturn(scanResult.bssid);
        when(associateMsg.getAttributeValueAsShort(eq(NetlinkConstants.NL80211_ATTR_STATUS_CODE)))
                .thenReturn((short) 15); // Non-zero, but should be ignored

        testAssociationEvent(NetlinkConstants.NL80211_CMD_ASSOCIATE, associateMsg,
                /* expectSuccess */ true, scanResult.bssid, scanResult.frequency);
    }

    @Test
    public void testBroadcastEvent_onAssociate_failureTimeout() {
        GenericNetlinkMsg associateMsg = mock(GenericNetlinkMsg.class);
        when(associateMsg.getAttributeValueAsInteger(NL80211_ATTR_IFINDEX))
                .thenReturn(CLIENT_IFACE_INDEX);
        when(associateMsg.getAttribute(eq(NetlinkConstants.NL80211_ATTR_TIMED_OUT)))
                .thenReturn(mock(StructNlAttr.class));

        testAssociationEvent(NetlinkConstants.NL80211_CMD_ASSOCIATE, associateMsg,
                /* expectSuccess */ false, /* expectedBssid */ null, /* expectedFreqMhz */ 0);
    }

    @Test
    public void testBroadcastEvent_onAssociate_successButFreqNotInScanResults() {
        // Mock scan results that do NOT contain the associated BSSID
        when(mNl80211Utils.getScanResults(CLIENT_IFACE_NAME)).thenReturn(new ArrayList<>());

        GenericNetlinkMsg associateMsg = mock(GenericNetlinkMsg.class);
        when(associateMsg.getAttributeValueAsInteger(NL80211_ATTR_IFINDEX))
                .thenReturn(CLIENT_IFACE_INDEX);
        when(associateMsg.getAttributeValueAsByteArray(NL80211_ATTR_MAC)).thenReturn(TEST_BSSID);

        // Expect success, but frequency should remain 0
        testAssociationEvent(NetlinkConstants.NL80211_CMD_ASSOCIATE, associateMsg,
                /* expectSuccess */ true, TEST_BSSID, /* expectedFreqMhz */ 0);
    }

    @Test
    public void testBroadcastEvent_onAssociate_ignoresScanResultWithMismatchedBssid() {
        NativeScanResult matchingScanResult = new NativeScanResult();
        matchingScanResult.bssid = TEST_BSSID;
        matchingScanResult.frequency = TEST_FREQ;
        matchingScanResult.associated = true;

        NativeScanResult differentScanResult = new NativeScanResult();
        differentScanResult.bssid = TEST_BSSID_2;
        differentScanResult.frequency = TEST_FREQ_2;
        differentScanResult.associated = true;

        when(mNl80211Utils.getScanResults(CLIENT_IFACE_NAME))
                .thenReturn(List.of(differentScanResult, matchingScanResult));

        GenericNetlinkMsg associateMsg = mock(GenericNetlinkMsg.class);
        when(associateMsg.getAttributeValueAsInteger(NL80211_ATTR_IFINDEX))
                .thenReturn(CLIENT_IFACE_INDEX);
        when(associateMsg.getAttributeValueAsByteArray(NL80211_ATTR_MAC))
                .thenReturn(matchingScanResult.bssid);
        when(associateMsg.getAttributeValueAsShort(eq(NetlinkConstants.NL80211_ATTR_STATUS_CODE)))
                .thenReturn((short) 0);

        testAssociationEvent(NetlinkConstants.NL80211_CMD_ASSOCIATE, associateMsg,
                /* expectSuccess */ true, matchingScanResult.bssid, matchingScanResult.frequency);
    }

    @Test
    public void testBroadcastEvent_onConnect_failureStatusCode() {
        GenericNetlinkMsg connectMsg = mock(GenericNetlinkMsg.class);
        when(connectMsg.getAttributeValueAsInteger(NL80211_ATTR_IFINDEX))
                .thenReturn(CLIENT_IFACE_INDEX);
        when(connectMsg.getAttributeValueAsShort(eq(NetlinkConstants.NL80211_ATTR_STATUS_CODE)))
                .thenReturn((short) 1); // Non-zero status code

        testAssociationEvent(NetlinkConstants.NL80211_CMD_CONNECT, connectMsg,
                /* expectSuccess */ false, /* expectedBssid */ null, /* expectedFreqMhz */ 0);
    }

    @Test
    public void testBroadcastEvent_onConnect_failureMissingBssid() {
        GenericNetlinkMsg connectMsg = mock(GenericNetlinkMsg.class);
        when(connectMsg.getAttributeValueAsInteger(NL80211_ATTR_IFINDEX))
                .thenReturn(CLIENT_IFACE_INDEX);
        when(connectMsg.getAttributeValueAsByteArray(NL80211_ATTR_MAC)).thenReturn(null);
        when(connectMsg.getAttributeValueAsShort(eq(NetlinkConstants.NL80211_ATTR_STATUS_CODE)))
                .thenReturn((short) 0);

        testAssociationEvent(NetlinkConstants.NL80211_CMD_CONNECT, connectMsg,
                /* expectSuccess */ false, /* expectedBssid */ null,  /* expectedFreqMhz */ 0);
    }

    private void testDisassociationEvent(short command) {
        mDut = initNl80211Native(false);
        // Set up a connected client interface.
        setupClientModeInterfaceForTest(WIPHY_INDEX_0, null, null, null);
        Nl80211Native.ClientInterfaceInfo clientIfaceInfo =
                mDut.getClientInterfaceInfos().get(CLIENT_IFACE_NAME);
        clientIfaceInfo.associated = true;
        clientIfaceInfo.associatedBssid = TEST_BSSID;
        clientIfaceInfo.associatedFreqMhz = TEST_FREQ;

        ArgumentCaptor<Nl80211BroadcastMonitor.Nl80211BroadcastCallback> callbackCaptor =
                ArgumentCaptor.forClass(Nl80211BroadcastMonitor.Nl80211BroadcastCallback.class);
        verify(mNl80211Proxy).registerBroadcastCallback(eq(command), callbackCaptor.capture());

        GenericNetlinkMsg disassociateMsg = mock(GenericNetlinkMsg.class);
        when(disassociateMsg.getAttributeValueAsInteger(NL80211_ATTR_IFINDEX))
                .thenReturn(CLIENT_IFACE_INDEX);

        // Trigger a disassociation event (either CMD_DISASSOCIATE or CMD_DISCONNECT).
        callbackCaptor.getValue().onEvent(command, disassociateMsg);

        // Client interface should not be associated anymore.
        assertFalse(clientIfaceInfo.associated);
        assertNull(clientIfaceInfo.associatedBssid);
        assertEquals(0, clientIfaceInfo.associatedFreqMhz);
    }

    @Test
    public void testBroadcastEvent_onDisconnect_clearsClientInfo() {
        testDisassociationEvent(NetlinkConstants.NL80211_CMD_DISCONNECT);
    }

    @Test
    public void testBroadcastEvent_onDisassociate_clearsClientInfo() {
        testDisassociationEvent(NetlinkConstants.NL80211_CMD_DISASSOCIATE);
    }

    @Test
    public void testSetupInterfaceForClientMode_netdFailureReturnsTrue() {
        mDut = initNl80211Native(false);
        Nl80211Utils.InterfaceInfo ifaceInfo = new Nl80211Utils.InterfaceInfo(
                CLIENT_IFACE_INDEX, WIPHY_INDEX_0, CLIENT_IFACE_NAME, new byte[6]);
        when(mNl80211Utils.getInterfaceInfo(CLIENT_IFACE_NAME)).thenReturn(ifaceInfo);
        Nl80211Utils.WiphyInfo wiphyInfo = new Nl80211Utils.WiphyInfo(
                new Nl80211Utils.BandInfo(),
                mock(Nl80211Utils.ScanCapabilities.class),
                new Nl80211Utils.WiphyFeatures.Builder().build(),
                mock(Nl80211Utils.DriverCapabilities.class));
        when(mNl80211Utils.getWiphyInfo(WIPHY_INDEX_0)).thenReturn(wiphyInfo);
        // Simulate a failure from NetdWrapper.
        doThrow(new IllegalStateException("netd has died"))
                .when(mNetdWrapper).setInterfaceUp(CLIENT_IFACE_NAME);

        assertTrue(mDut.setupInterfaceForClientMode(
                CLIENT_IFACE_NAME, mExecutor, mScanCallback, mPnoScanCallback));
    }

    @Test
    public void testTearDownClientInterface_netdFailureReturnsTrue() {
        mDut = initNl80211Native(false);
        setupClientModeInterfaceForTest(WIPHY_INDEX_0, null, null, null);
        // Simulate a failure from NetdWrapper.
        doThrow(new IllegalStateException("netd has died"))
                .when(mNetdWrapper).setInterfaceDown(CLIENT_IFACE_NAME);

        assertTrue(mDut.tearDownClientInterface(CLIENT_IFACE_NAME));
        // The client info should still be removed even if netd fails to bring down the interface.
        assertFalse(mDut.getClientInterfaceInfos().containsKey(CLIENT_IFACE_NAME));
    }

    @Test
    public void testTearDownSoftApInterface_netdFailureReturnsTrue() {
        mDut = initNl80211Native(false);
        setupSoftApInterfaceForTest(WIPHY_INDEX_0, null);
        // Simulate a failure from NetdWrapper.
        doThrow(new IllegalStateException("netd has died"))
                .when(mNetdWrapper).setInterfaceDown(AP_IFACE_NAME);

        assertTrue(mDut.tearDownSoftApInterface(AP_IFACE_NAME));
        // The AP info should still be removed even if netd fails to bring down the interface.
        assertFalse(mDut.getApInterfaceInfos().containsKey(AP_IFACE_NAME));
    }

    /** Test that {@link Nl80211Native#getInterfaceNames()} returns the expected value. */
    @Test
    public void testGetInterfaceNames_success_returnsInterfaceNames() {
        mDut = initNl80211Native(false);
        List<Nl80211Utils.InterfaceInfo> interfaces = new ArrayList<>();
        interfaces.add(new Nl80211Utils.InterfaceInfo(1, 0, CLIENT_IFACE_NAME, new byte[6]));
        when(mNl80211Utils.getInterfaces(anyInt())).thenReturn(interfaces);
        List<String> interfaceNames = mDut.getInterfaceNames();
        assertEquals(List.of(CLIENT_IFACE_NAME), interfaceNames);
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
                CLIENT_IFACE_NAME, mExecutor, mScanCallback, mPnoScanCallback)).thenReturn(true);
        assertTrue(mDut.setupInterfaceForClientMode(
                CLIENT_IFACE_NAME, mExecutor, mScanCallback, mPnoScanCallback));
        verify(mWificondManager).setupInterfaceForClientMode(
                CLIENT_IFACE_NAME, mExecutor, mScanCallback, mPnoScanCallback);
    }

    @Test
    public void testSetupInterfaceForClientMode_success() {
        mDut = initNl80211Native(false);
        setupClientModeInterfaceForTest(WIPHY_INDEX_0, null, null, null);

        assertTrue(mDut.getClientInterfaceInfos().containsKey(CLIENT_IFACE_NAME));
        assertEquals(1, mDut.getClientInterfaceInfos().size());
        Nl80211Native.ClientInterfaceInfo clientInterfaceInfo =
                mDut.getClientInterfaceInfos().get(CLIENT_IFACE_NAME);
        assertNotNull(clientInterfaceInfo);
        assertEquals(CLIENT_IFACE_NAME, clientInterfaceInfo.ifName);
        assertEquals(mExecutor, clientInterfaceInfo.scanCallbackExecutor);
        assertEquals(mScanCallback, clientInterfaceInfo.scanEventCallback);
        assertEquals(mPnoScanCallback, clientInterfaceInfo.pnoScanEventCallback);
    }

    @Test
    public void testSetupInterfaceForClientMode_getWiphyIndexFails() {
        mDut = initNl80211Native(false);
        when(mNl80211Utils.getWiphyIndex(CLIENT_IFACE_NAME)).thenReturn(-1);

        assertEquals(false, mDut.setupInterfaceForClientMode(
                CLIENT_IFACE_NAME, mExecutor, mScanCallback, mPnoScanCallback));
    }

    @Test
    public void testSetupInterfaceForClientMode_getInterfacesFails() {
        mDut = initNl80211Native(false);
        when(mNl80211Utils.getWiphyIndex(CLIENT_IFACE_NAME)).thenReturn(0);
        when(mNl80211Utils.getInterfaces(0)).thenReturn(null);

        assertEquals(false, mDut.setupInterfaceForClientMode(
                CLIENT_IFACE_NAME, mExecutor, mScanCallback, mPnoScanCallback));
    }

    @Test
    public void testSetupInterfaceForClientMode_interfaceNotFound() {
        mDut = initNl80211Native(false);
        when(mNl80211Utils.getWiphyIndex(CLIENT_IFACE_NAME)).thenReturn(0);
        List<Nl80211Utils.InterfaceInfo> interfaces = new ArrayList<>();
        interfaces.add(new Nl80211Utils.InterfaceInfo(0, 0, "anotheriface", new byte[6]));
        when(mNl80211Utils.getInterfaces(0)).thenReturn(interfaces);

        assertEquals(false, mDut.setupInterfaceForClientMode(
                CLIENT_IFACE_NAME, mExecutor, mScanCallback, mPnoScanCallback));
    }

    @Test
    public void testTearDownClientInterface_useWificondEnabled_callsWificond() {
        mDut = initNl80211Native(true);
        when(mWificondManager.tearDownClientInterface(CLIENT_IFACE_NAME)).thenReturn(true);
        assertTrue(mDut.tearDownClientInterface(CLIENT_IFACE_NAME));
        verify(mWificondManager).tearDownClientInterface(CLIENT_IFACE_NAME);
    }

    @Test
    public void testTearDownClientInterface_noIfaceFoundReturnsFalse() {
        mDut = initNl80211Native(false);
        assertFalse(mDut.tearDownSoftApInterface(AP_IFACE_NAME));
    }

    @Test
    public void testTearDownClientInterface_success() {
        mDut = initNl80211Native(false);
        setupClientModeInterfaceForTest(WIPHY_INDEX_0, null, null, null);

        assertTrue(mDut.getClientInterfaceInfos().containsKey(CLIENT_IFACE_NAME));
        assertEquals(1, mDut.getClientInterfaceInfos().size());

        assertTrue(mDut.tearDownClientInterface(CLIENT_IFACE_NAME));
        assertEquals(0, mDut.getClientInterfaceInfos().size());
        assertNull(mDut.getClientInterfaceInfos().get(CLIENT_IFACE_NAME));
    }

    @Test
    public void testSetupInterfaceForSoftApMode_useWificondEnabled_callsWificond() {
        mDut = initNl80211Native(true);
        when(mWificondManager.setupInterfaceForSoftApMode(AP_IFACE_NAME)).thenReturn(true);
        assertTrue(mDut.setupInterfaceForSoftApMode(AP_IFACE_NAME));
        verify(mWificondManager).setupInterfaceForSoftApMode(AP_IFACE_NAME);
    }

    @Test
    public void testSetupInterfaceForSoftApMode_success() {
        mDut = initNl80211Native(false);
        setupSoftApInterfaceForTest(WIPHY_INDEX_0, null);

        assertTrue(mDut.getApInterfaceInfos().containsKey(AP_IFACE_NAME));
        assertEquals(1, mDut.getApInterfaceInfos().size());
        Nl80211Native.ApInterfaceInfo apInterfaceInfo =
                mDut.getApInterfaceInfos().get(AP_IFACE_NAME);
        assertNotNull(apInterfaceInfo);
        assertEquals(AP_IFACE_NAME, apInterfaceInfo.ifName);

        verify(mNl80211Proxy).registerBroadcastCallback(
                eq(NL80211_CMD_NEW_STATION), any());
        verify(mNl80211Proxy).registerBroadcastCallback(
                eq(NL80211_CMD_DEL_STATION), any());
        verify(mNl80211Proxy).registerBroadcastCallback(
                eq(NL80211_CMD_CH_SWITCH_NOTIFY), any());
    }

    @Test
    public void testTearDownSoftApInterface_useWificondEnabled_callsWificond() {
        mDut = initNl80211Native(true);
        when(mWificondManager.tearDownSoftApInterface(AP_IFACE_NAME)).thenReturn(true);
        assertTrue(mDut.tearDownSoftApInterface(AP_IFACE_NAME));
        verify(mWificondManager).tearDownSoftApInterface(AP_IFACE_NAME);
    }

    @Test
    public void testTearDownSoftApInterface_noIfaceFoundReturnsFalse() {
        mDut = initNl80211Native(false);
        assertFalse(mDut.tearDownSoftApInterface(AP_IFACE_NAME));
    }

    @Test
    public void testTearDownSoftApInterface_success() {
        mDut = initNl80211Native(false);
        setupSoftApInterfaceForTest(WIPHY_INDEX_0, null);
        assertTrue(mDut.getApInterfaceInfos().containsKey(AP_IFACE_NAME));

        assertTrue(mDut.tearDownSoftApInterface(AP_IFACE_NAME));

        verify(mNetdWrapper).setInterfaceDown(AP_IFACE_NAME);
        assertFalse(mDut.getApInterfaceInfos().containsKey(AP_IFACE_NAME));
        verify(mNl80211Proxy).unregisterBroadcastCallback(
                eq(NL80211_CMD_NEW_STATION), any());
        verify(mNl80211Proxy).unregisterBroadcastCallback(
                eq(NL80211_CMD_DEL_STATION), any());
        verify(mNl80211Proxy).unregisterBroadcastCallback(
                eq(NL80211_CMD_CH_SWITCH_NOTIFY), any());
    }

    @Test
    public void testSetupInterfaceForClientMode_getWiphyInfoFails() {
        mDut = initNl80211Native(false);
        Nl80211Utils.InterfaceInfo ifaceInfo = new Nl80211Utils.InterfaceInfo(
                CLIENT_IFACE_INDEX, WIPHY_INDEX_0, CLIENT_IFACE_NAME, new byte[6]);
        when(mNl80211Utils.getInterfaceInfo(CLIENT_IFACE_NAME)).thenReturn(ifaceInfo);
        when(mNl80211Utils.getWiphyInfo(WIPHY_INDEX_0)).thenReturn(null);

        assertFalse(mDut.setupInterfaceForClientMode(
                CLIENT_IFACE_NAME, mExecutor, mScanCallback, mPnoScanCallback));
    }

    @Test
    public void testSetupInterfaceForSoftApMode_getWiphyInfoFails() {
        mDut = initNl80211Native(false);
        Nl80211Utils.InterfaceInfo ifaceInfo = new Nl80211Utils.InterfaceInfo(
                AP_IFACE_INDEX, WIPHY_INDEX_0, AP_IFACE_NAME, new byte[6]);
        when(mNl80211Utils.getInterfaceInfo(AP_IFACE_NAME)).thenReturn(ifaceInfo);
        when(mNl80211Utils.getWiphyInfo(WIPHY_INDEX_0)).thenReturn(null);

        assertFalse(mDut.setupInterfaceForSoftApMode(AP_IFACE_NAME));
    }

    @Test
    public void testTearDownInterfaces_useWificondEnabled_callsWificond() {
        mDut = initNl80211Native(true);
        when(mWificondManager.tearDownInterfaces()).thenReturn(true);
        assertTrue(mDut.tearDownInterfaces());
        verify(mWificondManager).tearDownInterfaces();
    }

    @Test
    public void testTeardownInterfaces_success() {
        mDut = initNl80211Native(false);
        setupClientModeInterfaceForTest(WIPHY_INDEX_0, null, null, null);
        setupSoftApInterfaceForTest(WIPHY_INDEX_1, null);

        assertFalse(mDut.getClientInterfaceInfos().isEmpty());
        assertFalse(mDut.getApInterfaceInfos().isEmpty());

        assertTrue(mDut.tearDownInterfaces());

        assertTrue(mDut.getClientInterfaceInfos().isEmpty());
        assertTrue(mDut.getApInterfaceInfos().isEmpty());
        verify(mNetdWrapper).setInterfaceDown(CLIENT_IFACE_NAME);
        verify(mNetdWrapper).setInterfaceDown(AP_IFACE_NAME);
    }

    @Test
    public void testRegisterApCallback_useWificondEnabled_callsWificond() {
        mDut = initNl80211Native(true);
        when(mWificondManager.registerApCallback(AP_IFACE_NAME, mExecutor, mSoftApCallback))
                .thenReturn(true);
        assertTrue(mDut.registerApCallback(AP_IFACE_NAME, mExecutor, mSoftApCallback));
        verify(mWificondManager).registerApCallback(AP_IFACE_NAME, mExecutor, mSoftApCallback);
    }

    @Test
    public void testRegisterApCallback_success() {
        mDut = initNl80211Native(false);
        setupSoftApInterfaceForTest(WIPHY_INDEX_0, null);

        assertTrue(mDut.registerApCallback(AP_IFACE_NAME, mExecutor, mSoftApCallback));

        Nl80211Native.ApInterfaceInfo apInterfaceInfo =
                mDut.getApInterfaceInfos().get(AP_IFACE_NAME);
        assertEquals(mExecutor, apInterfaceInfo.executor);
        assertEquals(mSoftApCallback, apInterfaceInfo.callback);
    }

    @Test
    public void testStartScan_useWificondEnabled_callsWificond() {
        assumeTrue(SdkLevel.isAtLeastU());

        mDut = initNl80211Native(true);
        Set<Integer> freqs = new HashSet<>(List.of(2412));
        List<byte[]> ssids = new ArrayList<>();
        Bundle extras = new Bundle();
        when(mWificondManager.startScan2(
                CLIENT_IFACE_NAME, WifiScanner.SCAN_TYPE_HIGH_ACCURACY, freqs, ssids, extras))
                .thenReturn(WifiScanner.REASON_SUCCEEDED);
        assertEquals(WifiScanner.REASON_SUCCEEDED, mDut.startScan(
                CLIENT_IFACE_NAME, WifiScanner.SCAN_TYPE_HIGH_ACCURACY, freqs, ssids, extras));
        verify(mWificondManager).startScan2(
                CLIENT_IFACE_NAME, WifiScanner.SCAN_TYPE_HIGH_ACCURACY, freqs, ssids, extras);
    }

    private void setupSoftApInterfaceForTest(int wiphyIndex,
            @Nullable Nl80211Utils.WiphyInfo wiphyInfo) {
        when(mNl80211Utils.getInterfaceInfo(AP_IFACE_NAME))
                .thenReturn(new Nl80211Utils.InterfaceInfo(
                        AP_IFACE_INDEX, wiphyIndex, AP_IFACE_NAME, new byte[6]));
        if (wiphyInfo == null) {
            wiphyInfo = new Nl80211Utils.WiphyInfo(
                    new Nl80211Utils.BandInfo(),
                    mock(Nl80211Utils.ScanCapabilities.class),
                    mock(Nl80211Utils.WiphyFeatures.class),
                    mock(Nl80211Utils.DriverCapabilities.class));
        }
        when(mNl80211Utils.getWiphyInfo(wiphyIndex)).thenReturn(wiphyInfo);
        mDut.setupInterfaceForSoftApMode(AP_IFACE_NAME);
    }

    /** Test that a successful scan results in the expected calls to Nl80211Utils. */
    @Test
    public void testStartScan_success() {
        mDut = initNl80211Native(false);
        Nl80211Utils.ScanCapabilities scanCapabilities = new Nl80211Utils.ScanCapabilities(
                2 /* maxNumScanSsids */, 0, 0, 0, 0, 0);
        setupClientModeInterfaceForTest(WIPHY_INDEX_0, null, scanCapabilities, null);
        Set<Integer> freqs = new HashSet<>(List.of(2412, 5180));
        List<byte[]> hiddenSsids = List.of("hidden1".getBytes(), "hidden2".getBytes());
        Bundle extraParams = new Bundle();
        extraParams.putBoolean(Nl80211Native.SCANNING_PARAM_ENABLE_6GHZ_RNR, true);
        extraParams.putByteArray(Nl80211Native.EXTRA_SCANNING_PARAM_VENDOR_IES,
                new byte[]{0x01, 0x02});

        when(mNl80211Utils.triggerScan(
                eq(CLIENT_IFACE_INDEX), anyInt(),
                eq(freqs), eq(hiddenSsids), eq(new byte[]{0x01, 0x02})))
                .thenReturn(WifiScanner.REASON_SUCCEEDED);

        int result = mDut.startScan(CLIENT_IFACE_NAME, WifiScanner.SCAN_TYPE_HIGH_ACCURACY,
                freqs, hiddenSsids, extraParams);
        assertEquals(WifiScanner.REASON_SUCCEEDED, result);
        verify(mNl80211Utils).triggerScan(
                eq(CLIENT_IFACE_INDEX), anyInt(), eq(freqs), eq(hiddenSsids),
                eq(new byte[]{0x01, 0x02}));
    }

    @Test
    public void testStartScan_invalidScanType() {
        mDut = initNl80211Native(false);
        setupClientModeInterfaceForTest(WIPHY_INDEX_0, null, null, null);

        int result = mDut.startScan(CLIENT_IFACE_NAME, WifiScanner.SCAN_TYPE_MAX + 1,
                null, Collections.emptyList(), null);

        assertEquals(WifiScanner.REASON_INVALID_ARGS, result);
        verify(mNl80211Utils, never()).triggerScan(anyInt(), anyInt(), any(), any(), any());
    }

    /** Test that startScan returns UNSPECIFIED if no client interface info is found. */
    @Test
    public void testStartScan_noInterfaceInfo() {
        mDut = initNl80211Native(false);
        // Do not call setupClientModeInterfaceForTest, so mClientInterfaceInfos is empty
        int result = mDut.startScan(CLIENT_IFACE_NAME, WifiScanner.SCAN_TYPE_HIGH_ACCURACY,
                null, Collections.emptyList(), null);
        assertEquals(WifiScanner.REASON_UNSPECIFIED, result);
        verify(mNl80211Utils, never()).triggerScan(anyInt(), anyInt(), any(), any(), any());
    }

    /** Test that an empty hidden network SSIDs arg will trigger a wildcard scan. */
    @Test
    public void testStartScan_emptyHiddenSsids_triggersWildcardScan() {
        mDut = initNl80211Native(false);
        setupClientModeInterfaceForTest(WIPHY_INDEX_0, null, null, null);

        List<byte[]> emptySsids = new ArrayList<>();
        List<byte[]> expectedSsids = List.of(new byte[0]);

        when(mNl80211Utils.triggerScan(
                eq(CLIENT_IFACE_INDEX), anyInt(),
                eq(null), eq(expectedSsids), eq(null)))
                .thenReturn(WifiScanner.REASON_SUCCEEDED);

        int result = mDut.startScan(CLIENT_IFACE_NAME, WifiScanner.SCAN_TYPE_LOW_LATENCY,
                null, emptySsids, null);
        assertEquals(WifiScanner.REASON_SUCCEEDED, result);
        ArgumentCaptor<List<byte[]>> ssidsCaptor = ArgumentCaptor.forClass(List.class);
        verify(mNl80211Utils).triggerScan(
                eq(CLIENT_IFACE_INDEX), anyInt(),
                eq(null), ssidsCaptor.capture(), eq(null));
        List<byte[]> ssids = ssidsCaptor.getValue();
        assertEquals(1, ssids.size());
        assertEquals(0, ssids.get(0).length);
    }

    /** Test that startScan correctly trims and passes hidden SSIDs. */
    @Test
    public void testStartScan_withHiddenSsids_trimsCorrectly() {
        mDut = initNl80211Native(false);
        Nl80211Utils.ScanCapabilities scanCapabilities = new Nl80211Utils.ScanCapabilities(
                1 /* maxNumScanSsids */, 0, 0, 0, 0, 0);
        setupClientModeInterfaceForTest(WIPHY_INDEX_0, null, scanCapabilities, null);

        byte[] ssid1 = "ssid1".getBytes();
        byte[] ssid2 = "ssid2".getBytes();
        List<byte[]> hiddenSsids = List.of(ssid1, ssid2);
        List<byte[]> expectedTrimmedSsids = List.of(ssid1); // Only first one should be taken

        when(mNl80211Utils.triggerScan(
                eq(CLIENT_IFACE_INDEX), anyInt(), eq(null), eq(expectedTrimmedSsids), eq(null)))
                .thenReturn(WifiScanner.REASON_SUCCEEDED);

        int result = mDut.startScan(CLIENT_IFACE_NAME, WifiScanner.SCAN_TYPE_HIGH_ACCURACY,
                null, hiddenSsids, null);
        assertEquals(WifiScanner.REASON_SUCCEEDED, result);
        verify(mNl80211Utils).triggerScan(
                eq(CLIENT_IFACE_INDEX), anyInt(),
                eq(null), eq(expectedTrimmedSsids), eq(null));
    }

    /** Test that startScan correctly handles extra scanning parameters. */
    @Test
    public void testStartScan_withExtraScanningParams() {
        mDut = initNl80211Native(false);
        setupClientModeInterfaceForTest(WIPHY_INDEX_0, null, null, null);
        Bundle extraParams = new Bundle();
        extraParams.putBoolean(Nl80211Native.SCANNING_PARAM_ENABLE_6GHZ_RNR, true);
        byte[] vendorIes = new byte[]{0x0A, 0x0B, 0x0C};
        extraParams.putByteArray(Nl80211Native.EXTRA_SCANNING_PARAM_VENDOR_IES, vendorIes);

        when(mNl80211Utils.triggerScan(
                eq(CLIENT_IFACE_INDEX), anyInt(),
                eq(null), eq(null), eq(vendorIes)))
                .thenReturn(WifiScanner.REASON_SUCCEEDED);

        int result = mDut.startScan(CLIENT_IFACE_NAME, WifiScanner.SCAN_TYPE_HIGH_ACCURACY,
                null, Collections.emptyList(), extraParams);
        assertEquals(WifiScanner.REASON_SUCCEEDED, result);
        verify(mNl80211Utils).triggerScan(
                eq(CLIENT_IFACE_INDEX), anyInt(), eq(null), any(), eq(vendorIes));
    }

    @Test
    public void testStartScan_triggersSelfRecoveryOnEnodevThreshold() {
        mDut = initNl80211Native(false);
        setupClientModeInterfaceForTest(WIPHY_INDEX_0, null, null, null);

        when(mNl80211Utils.triggerScan(
                anyInt(), anyInt(), any(), any(), any()))
                .thenReturn(WifiScanner.REASON_NO_DEVICE);

        // Call startScan ENODEV_RESTART_THRESHOLD times. It should not trigger SelfRecovery yet.
        for (int i = 0; i < Nl80211Native.ENODEV_RESTART_THRESHOLD; i++) {
            int result = mDut.startScan(CLIENT_IFACE_NAME, WifiScanner.SCAN_TYPE_HIGH_ACCURACY,
                    null, Collections.emptyList(), null);
            assertEquals(WifiScanner.REASON_NO_DEVICE, result);
            verify(mSelfRecovery, never()).trigger(anyInt());
        }

        // Call startScan one more time, which should trigger SelfRecovery.
        int result = mDut.startScan(CLIENT_IFACE_NAME, WifiScanner.SCAN_TYPE_HIGH_ACCURACY,
                null, Collections.emptyList(), null);
        assertEquals(WifiScanner.REASON_NO_DEVICE, result);
        verify(mSelfRecovery).trigger(SelfRecovery.REASON_SUBSYSTEM_RESTART);
    }

    @Test
    public void testStartScan_successResetsEnodevCounter() {
        mDut = initNl80211Native(false);
        setupClientModeInterfaceForTest(WIPHY_INDEX_0, null, null, null);

        // Fail a few times, but not enough to trigger recovery
        when(mNl80211Utils.triggerScan(anyInt(), anyInt(), any(), any(), any()))
                .thenReturn(WifiScanner.REASON_NO_DEVICE);
        for (int i = 0; i < Nl80211Native.ENODEV_RESTART_THRESHOLD - 1; i++) {
            mDut.startScan(CLIENT_IFACE_NAME, WifiScanner.SCAN_TYPE_HIGH_ACCURACY, null,
                    Collections.emptyList(), null);
            verify(mSelfRecovery, never()).trigger(anyInt());
        }

        // One successful scan should reset the counter
        when(mNl80211Utils.triggerScan(anyInt(), anyInt(), any(), any(), any()))
                .thenReturn(WifiScanner.REASON_SUCCEEDED);
        mDut.startScan(CLIENT_IFACE_NAME, WifiScanner.SCAN_TYPE_HIGH_ACCURACY, null,
                Collections.emptyList(), null);
        verify(mSelfRecovery, never()).trigger(anyInt());

        // Now, trigger failures again, and verify recovery is only triggered after the threshold
        when(mNl80211Utils.triggerScan(anyInt(), anyInt(), any(), any(), any()))
                .thenReturn(WifiScanner.REASON_NO_DEVICE);
        for (int i = 0; i < Nl80211Native.ENODEV_RESTART_THRESHOLD; i++) {
            mDut.startScan(CLIENT_IFACE_NAME, WifiScanner.SCAN_TYPE_HIGH_ACCURACY, null,
                    Collections.emptyList(), null);
            verify(mSelfRecovery, never()).trigger(anyInt());
        }
        mDut.startScan(CLIENT_IFACE_NAME, WifiScanner.SCAN_TYPE_HIGH_ACCURACY, null,
                Collections.emptyList(), null);
        verify(mSelfRecovery).trigger(SelfRecovery.REASON_SUBSYSTEM_RESTART);
    }

    private void testStartScanWithScanFlags(@WifiAnnotations.ScanType int scanType,
            @NonNull Nl80211Utils.WiphyFeatures features,
            int expectedScanFlags) {
        mDut = initNl80211Native(false);
        setupClientModeInterfaceForTest(WIPHY_INDEX_0, null, null, features);
        when(mNl80211Utils.triggerScan(anyInt(), eq(expectedScanFlags), any(), any(), any()))
                .thenReturn(WifiScanner.REASON_SUCCEEDED);

        int result = mDut.startScan(CLIENT_IFACE_NAME, scanType, null, Collections.emptyList(),
                null);

        assertEquals(result, WifiScanner.REASON_SUCCEEDED);
        verify(mNl80211Utils).triggerScan(
                anyInt(), eq(expectedScanFlags), any(), any(), any());
    }

    @Test
    public void testStartScan_supportedScanTypeLowLatency_setsScanFlag() {
        Nl80211Utils.WiphyFeatures features = new Nl80211Utils.WiphyFeatures.Builder()
                .setSupportsLowSpanOneShotScan(true)
                .build();
        testStartScanWithScanFlags(WifiScanner.SCAN_TYPE_LOW_LATENCY, features,
                NL80211_SCAN_FLAG_LOW_SPAN);
    }

    @Test
        public void testStartScan_supportedScanTypeLowPower_setsScanFlag() {
        Nl80211Utils.WiphyFeatures features = new Nl80211Utils.WiphyFeatures.Builder()
                .setSupportsLowPowerOneShotScan(true)
                .build();
        testStartScanWithScanFlags(WifiScanner.SCAN_TYPE_LOW_POWER, features,
                NL80211_SCAN_FLAG_LOW_POWER);
    }

    @Test
    public void testStartScan_supportedScanTypeHighAccuracy_setsScanFlag() {
        Nl80211Utils.WiphyFeatures features = new Nl80211Utils.WiphyFeatures.Builder()
                .setSupportsHighAccuracyOneShotScan(true)
                .build();
        testStartScanWithScanFlags(WifiScanner.SCAN_TYPE_HIGH_ACCURACY, features,
                NL80211_SCAN_FLAG_HIGH_ACCURACY);
    }

    @Test
    public void testStartScan_unsupportedScanTypeLowLatency_doesNotSetScanFlag() {
        Nl80211Utils.WiphyFeatures features = new Nl80211Utils.WiphyFeatures.Builder()
                .setSupportsLowSpanOneShotScan(false)
                .build();
        testStartScanWithScanFlags(WifiScanner.SCAN_TYPE_LOW_LATENCY, features, 0);
    }

    @Test
    public void testStartScan_unsupportedScanTypeLowPower_doesNotSetScanFlag() {
        Nl80211Utils.WiphyFeatures features = new Nl80211Utils.WiphyFeatures.Builder()
                .setSupportsLowPowerOneShotScan(false)
                .build();
        testStartScanWithScanFlags(WifiScanner.SCAN_TYPE_LOW_POWER, features, 0);
    }

    @Test
    public void testStartScan_unsupportedScanTypeHighAccuracy_doesNotSetScanFlag() {
        Nl80211Utils.WiphyFeatures features = new Nl80211Utils.WiphyFeatures.Builder()
                .setSupportsHighAccuracyOneShotScan(false)
                .build();
        testStartScanWithScanFlags(WifiScanner.SCAN_TYPE_HIGH_ACCURACY, features, 0);
    }

    @Test
    public void testGetScanFlag_lowLatencySupported() {
        Nl80211Utils.WiphyFeatures features = new Nl80211Utils.WiphyFeatures.Builder()
                .setSupportsLowSpanOneShotScan(true)
                .build();

        int flags = Nl80211Native.getScanFlagForScanType(
                WifiScanner.SCAN_TYPE_LOW_LATENCY, features);

        assertEquals(NL80211_SCAN_FLAG_LOW_SPAN, flags);
    }

    @Test
    public void testGetScanFlag_lowLatencyUnsupported_returnsZero() {
        Nl80211Utils.WiphyFeatures features = new Nl80211Utils.WiphyFeatures.Builder()
                .setSupportsLowSpanOneShotScan(false)
                .build();

        int flags = mDut.getScanFlagForScanType(
                WifiScanner.SCAN_TYPE_LOW_LATENCY, features);

        assertEquals(0, flags);
    }

    @Test
    public void testGetScanFlag_lowPowerSupported() {
        Nl80211Utils.WiphyFeatures features = new Nl80211Utils.WiphyFeatures.Builder()
                .setSupportsLowPowerOneShotScan(true)
                .build();

        int flags = Nl80211Native.getScanFlagForScanType(
                WifiScanner.SCAN_TYPE_LOW_POWER, features);

        assertEquals(NL80211_SCAN_FLAG_LOW_POWER, flags);
    }

    @Test
    public void testGetScanFlag_lowPowerUnsupported_returnsZero() {
        Nl80211Utils.WiphyFeatures features = new Nl80211Utils.WiphyFeatures.Builder()
                .setSupportsLowPowerOneShotScan(false)
                .build();

        int flags = mDut.getScanFlagForScanType(
                WifiScanner.SCAN_TYPE_LOW_POWER, features);

        assertEquals(0, flags);
    }

    @Test
    public void testGetScanFlag_highAccuracySupported() {
        Nl80211Utils.WiphyFeatures features = new Nl80211Utils.WiphyFeatures.Builder()
                .setSupportsHighAccuracyOneShotScan(true)
                .build();

        int flags = Nl80211Native.getScanFlagForScanType(
                WifiScanner.SCAN_TYPE_HIGH_ACCURACY, features);

        assertEquals(NL80211_SCAN_FLAG_HIGH_ACCURACY, flags);
    }

    @Test
    public void testGetScanFlag_highAccuracyNotSupported_returnsZero() {
        Nl80211Utils.WiphyFeatures features = new Nl80211Utils.WiphyFeatures.Builder()
                .setSupportsHighAccuracyOneShotScan(false)
                .build();

        int flags = Nl80211Native.getScanFlagForScanType(
                WifiScanner.SCAN_TYPE_HIGH_ACCURACY, features);

        assertEquals(0, flags);
    }

    @Test
    public void testGetScanFlag_invalidTypeReturnsZero() {
        Nl80211Utils.WiphyFeatures allFeatures = new Nl80211Utils.WiphyFeatures.Builder()
                .setSupportsLowPowerOneShotScan(true)
                .setSupportsLowSpanOneShotScan(true)
                .setSupportsHighAccuracyOneShotScan(true)
                .build();

        // Pass a scan type larger than the max defined
        int flags = Nl80211Native.getScanFlagForScanType(WifiScanner.SCAN_TYPE_MAX + 1,
                allFeatures);

        assertEquals(0, flags);
    }

    @Test
    public void testStartScan_withRandomMac_setsRandomMacFlag() {
        mDut = initNl80211Native(false);
        Nl80211Utils.WiphyFeatures wiphyFeatures = new Nl80211Utils.WiphyFeatures.Builder()
                .setSupportsRandomMacOneShotScan(true)
                .build();
        setupClientModeInterfaceForTest(WIPHY_INDEX_0, null, null, wiphyFeatures);
        // Ensure the interface is not associated, so that random MAC is requested
        mDut.getClientInterfaceInfos().get(CLIENT_IFACE_NAME).associated = false;

        when(mNl80211Utils.triggerScan(
                eq(CLIENT_IFACE_INDEX),
                eq(NetlinkConstants.NL80211_SCAN_FLAG_RANDOM_ADDR),
                any(), any(), any()))
                .thenReturn(WifiScanner.REASON_SUCCEEDED);

        mDut.startScan(CLIENT_IFACE_NAME, WifiScanner.SCAN_TYPE_LOW_LATENCY, null,
                Collections.emptyList(), null);

        verify(mNl80211Utils).triggerScan(
                eq(CLIENT_IFACE_INDEX),
                eq(NetlinkConstants.NL80211_SCAN_FLAG_RANDOM_ADDR),
                any(), any(), any());
    }

    @Test
    public void testStartScan_with6GhzRnr_sets6GhzRnrFlag() {
        mDut = initNl80211Native(false);
        Nl80211Utils.WiphyFeatures wiphyFeatures = mock(Nl80211Utils.WiphyFeatures.class);
        // We don't need any specific scan type support for this test.
        setupClientModeInterfaceForTest(WIPHY_INDEX_0, null, null, wiphyFeatures);

        Bundle extraParams = new Bundle();
        extraParams.putBoolean(Nl80211Native.SCANNING_PARAM_ENABLE_6GHZ_RNR, true);

        when(mNl80211Utils.triggerScan(
                eq(CLIENT_IFACE_INDEX),
                eq(NetlinkConstants.NL80211_SCAN_FLAG_COLOCATED_6GHZ),
                any(), any(), any()))
                .thenReturn(WifiScanner.REASON_SUCCEEDED);

        mDut.startScan(CLIENT_IFACE_NAME, WifiScanner.SCAN_TYPE_LOW_LATENCY, null,
                Collections.emptyList(), extraParams);

        verify(mNl80211Utils).triggerScan(
                eq(CLIENT_IFACE_INDEX),
                eq(NetlinkConstants.NL80211_SCAN_FLAG_COLOCATED_6GHZ),
                any(), any(), any());
    }

    /** Test that a successful abortScan results in the expected call to Nl80211Utils. */
    @Test
    public void testAbortScan_success() {
        mDut = initNl80211Native(false);
        setupClientModeInterfaceForTest(WIPHY_INDEX_0, null, null, null);

        // Start a scan first to set the 'scanning' flag to true
        when(mNl80211Utils.triggerScan(anyInt(), anyInt(), any(), any(), any()))
                .thenReturn(WifiScanner.REASON_SUCCEEDED);
        mDut.startScan(CLIENT_IFACE_NAME, WifiScanner.SCAN_TYPE_HIGH_ACCURACY, null,
                Collections.emptyList(), null);
        assertTrue(mDut.getClientInterfaceInfos().get(CLIENT_IFACE_NAME).scanning);

        mDut.abortScan(CLIENT_IFACE_NAME);
        verify(mNl80211Utils).abortScan(eq(CLIENT_IFACE_INDEX));
    }

    /** Test that abortScan does nothing if no client interface info is found. */
    @Test
    public void testAbortScan_noInterfaceInfo() {
        mDut = initNl80211Native(false);
        // Do not call setupClientModeInterfaceForTest, so mClientInterfaceInfos is empty

        mDut.abortScan(CLIENT_IFACE_NAME);
        verify(mNl80211Utils, never()).abortScan(anyInt());
    }

    /** Test that abortScan does nothing if no scan is active for the interface. */
    @Test
    public void testAbortScan_noActiveScan_doesNothing() {
        mDut = initNl80211Native(false);
        setupClientModeInterfaceForTest(WIPHY_INDEX_0, null, null, null);
        // 'scanning' flag should be false by default
        assertFalse(mDut.getClientInterfaceInfos().get(CLIENT_IFACE_NAME).scanning);

        mDut.abortScan(CLIENT_IFACE_NAME);

        verify(mNl80211Utils, never()).abortScan(anyInt());
    }

    @Test
    public void testStartScanPreU_useWificondEnabled_callsWificond() {
        mDut = initNl80211Native(true);
        when(mWificondManager.startScan(anyString(), anyInt(), any(), any(), any()))
                .thenReturn(true);
        assertTrue(mDut.startScanPreU(CLIENT_IFACE_NAME, 0, null, null, null));
        verify(mWificondManager).startScan(CLIENT_IFACE_NAME, 0, null, null, null);
    }

    @Test
    public void testStartScanPreU_returnsFalse() {
        mDut = initNl80211Native(false);
        assertThrows(UnsupportedOperationException.class,
                () -> mDut.startScanPreU(CLIENT_IFACE_NAME, 0, null, null, null));
    }

    @Test
    public void testGetScanResults_useWificondEnabled_callsWificond() {
        mDut = initNl80211Native(true);
        android.net.wifi.nl80211.NativeScanResult expectedScanResult =
                new android.net.wifi.nl80211.NativeScanResult();
        expectedScanResult.ssid = new byte[]{'a', 's', 'd', 'f'};
        expectedScanResult.bssid = new byte[]{0x01, 0x02, 0x03, 0x04, 0x05, 0x06};
        when(mWificondManager.getScanResults(CLIENT_IFACE_NAME, 0))
                .thenReturn(Collections.singletonList(expectedScanResult));

        List<NativeScanResult> results = mDut.getScanResults(CLIENT_IFACE_NAME, 0);

        assertNotNull(results);
        verify(mWificondManager).getScanResults(CLIENT_IFACE_NAME, 0);
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
                mWificondManager, mWifiInjector, false);
        List<NativeScanResult> results = nl80211Native.getScanResults(CLIENT_IFACE_NAME, 0);
        assertTrue(results.isEmpty());
    }

    @Test
    public void testGetScanResults_success() {
        mDut = initNl80211Native(false);
        List<NativeScanResult> expectedScanResults = new ArrayList<>();
        NativeScanResult scanResult = new NativeScanResult();
        scanResult.ssid = new byte[]{'T', 'E', 'S', 'T'};
        scanResult.bssid = new byte[]{0x11, 0x22, 0x33, 0x44, 0x55, 0x66};
        expectedScanResults.add(scanResult);
        when(mNl80211Utils.getScanResults(eq(CLIENT_IFACE_NAME)))
                .thenReturn(expectedScanResults);

        List<NativeScanResult> results = mDut.getScanResults(CLIENT_IFACE_NAME,
                Nl80211Native.SCAN_TYPE_SINGLE_SCAN);

        assertNotNull(results);
        assertEquals(1, results.size());
        assertEquals(scanResult, results.get(0));
        verify(mNl80211Utils).getScanResults(eq(CLIENT_IFACE_NAME));
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
                eq(CLIENT_IFACE_NAME), any(), eq(mExecutor), eq(mPnoScanRequestCallback)))
                .thenReturn(true);

        // 3. Call the method under test
        assertTrue(mDut.startPnoScan(
                CLIENT_IFACE_NAME, serverPnoSettings, mExecutor, mPnoScanRequestCallback));

        // 4. Capture the argument passed to wificond
        ArgumentCaptor<android.net.wifi.nl80211.PnoSettings> wificondPnoSettingsCaptor =
                ArgumentCaptor.forClass(android.net.wifi.nl80211.PnoSettings.class);
        verify(mWificondManager).startPnoScan(eq(CLIENT_IFACE_NAME),
                wificondPnoSettingsCaptor.capture(), eq(mExecutor), eq(mPnoScanRequestCallback));

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
    public void testStartPnoScan_success() {
        mDut = initNl80211Native(false);
        Nl80211Utils.ScanCapabilities scanCapabilities = new Nl80211Utils.ScanCapabilities(
                0, 1 /* maxNumSchedScanSsids */, 1 /* maxMatchSets */, 2, 10, 3);
        Nl80211Utils.WiphyFeatures wiphyFeatures = new Nl80211Utils.WiphyFeatures.Builder()
                .setSupportsRandomMacSchedScan(true)
                .setSupportsLowPowerOneShotScan(true)
                .setSupportsExtSchedScanRelativeRssi(true)
                .build();
        Nl80211Utils.WiphyInfo wiphyInfo = new Nl80211Utils.WiphyInfo(
                new Nl80211Utils.BandInfo(), scanCapabilities, wiphyFeatures,
                mock(Nl80211Utils.DriverCapabilities.class));

        setupClientModeInterfaceForTest(WIPHY_INDEX_0, wiphyInfo.bandInfo, scanCapabilities,
                wiphyFeatures);

        // Mock PnoSettings
        when(mPnoSettings.getIntervalMillis()).thenReturn(15000L);
        when(mPnoSettings.getMin2gRssiDbm()).thenReturn(-70);
        when(mPnoSettings.getMin5gRssiDbm()).thenReturn(-80);
        when(mPnoSettings.getScanIterations()).thenReturn(5);
        when(mPnoSettings.getScanIntervalMultiplier()).thenReturn(3);
        when(mPnoSettings.getPnoNetworks()).thenReturn(new ArrayList<>());

        when(mNl80211Utils.startPnoScan(
                anyInt(), any(), anyLong(), anyInt(), anyInt(), anyBoolean(), anyBoolean(),
                anyBoolean(), any(), any(), any()))
                .thenReturn(WifiScanner.REASON_SUCCEEDED);

        boolean result = mDut.startPnoScan(
                CLIENT_IFACE_NAME, mPnoSettings, mExecutor, mPnoScanRequestCallback);

        assertTrue(result);
        verify(mNl80211Utils).startPnoScan(
                eq(CLIENT_IFACE_INDEX), any(), eq(15000L), eq(-70), eq(-80), eq(true), eq(true),
                eq(true), any(), any(), any());
        ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);
        verify(mExecutor).execute(runnableCaptor.capture());
        runnableCaptor.getValue().run();
        verify(mPnoScanRequestCallback).onPnoRequestSucceeded();
        assertTrue(mDut.getClientInterfaceInfos().get(CLIENT_IFACE_NAME).pnoScanStarted);
    }

    @Test
    public void testStartPnoScan_nullIfaceName() {
        mDut = initNl80211Native(false);
        assertFalse(mDut.startPnoScan(null, mPnoSettings, mExecutor, mPnoScanRequestCallback));
        verify(mNl80211Utils, never()).startPnoScan(anyInt(), any(), anyLong(), anyInt(),
                anyInt(), anyBoolean(), anyBoolean(), anyBoolean(), any(), any(), any());
    }

    @Test
    public void testStartPnoScan_nullPnoSettings() {
        mDut = initNl80211Native(false);
        assertFalse(mDut.startPnoScan(CLIENT_IFACE_NAME, null, mExecutor, mPnoScanRequestCallback));
        verify(mNl80211Utils, never()).startPnoScan(anyInt(), any(), anyLong(), anyInt(),
                anyInt(), anyBoolean(), anyBoolean(), anyBoolean(), any(), any(), any());
    }

    @Test
    public void testStartPnoScan_nullExecutor() {
        mDut = initNl80211Native(false);
        assertFalse(mDut.startPnoScan(CLIENT_IFACE_NAME, mPnoSettings, null,
                mPnoScanRequestCallback));
        verify(mNl80211Utils, never()).startPnoScan(anyInt(), any(), anyLong(), anyInt(),
                anyInt(), anyBoolean(), anyBoolean(), anyBoolean(), any(), any(), any());
    }

    @Test
    public void testStartPnoScan_nullCallback() {
        mDut = initNl80211Native(false);
        assertFalse(mDut.startPnoScan(CLIENT_IFACE_NAME, mPnoSettings, mExecutor, null));
        verify(mNl80211Utils, never()).startPnoScan(anyInt(), any(), anyLong(), anyInt(),
                anyInt(), anyBoolean(), anyBoolean(), anyBoolean(), any(), any(), any());
    }

    @Test
    public void testStartPnoScan_notInitialized() {
        Nl80211Native nl80211Native = new Nl80211Native(mNl80211Proxy, mNl80211Utils, mNetdWrapper,
                mWificondManager, mWifiInjector, false);
        assertFalse(nl80211Native.startPnoScan(CLIENT_IFACE_NAME, mPnoSettings, mExecutor,
                mPnoScanRequestCallback));
        verify(mNl80211Utils, never()).startPnoScan(anyInt(), any(), anyLong(), anyInt(),
                anyInt(), anyBoolean(), anyBoolean(), anyBoolean(), any(), any(), any());
    }

    @Test
    public void testStartPnoScan_noInterfaceInfo() {
        mDut = initNl80211Native(false);
        // Do not call setupClientModeInterfaceForTest, so mClientInterfaceInfos is empty
        assertFalse(mDut.startPnoScan(CLIENT_IFACE_NAME, mPnoSettings, mExecutor,
                mPnoScanRequestCallback));
        verify(mNl80211Utils, never()).startPnoScan(anyInt(), any(), anyLong(), anyInt(),
                anyInt(), anyBoolean(), anyBoolean(), anyBoolean(), any(), any(), any());
    }

    @Test
    public void testStartPnoScan_nl80211UtilsReturnsFailure() {
        mDut = initNl80211Native(false);
        setupClientModeInterfaceForTest(WIPHY_INDEX_0, null, null, null);

        when(mNl80211Utils.startPnoScan(
                anyInt(), any(), anyLong(), anyInt(), anyInt(), anyBoolean(), anyBoolean(),
                anyBoolean(), any(), any(), any()))
                .thenReturn(WifiScanner.REASON_UNSPECIFIED);

        boolean result = mDut.startPnoScan(
                CLIENT_IFACE_NAME, mPnoSettings, mExecutor, mPnoScanRequestCallback);

        assertFalse(result);
        ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);
        verify(mExecutor).execute(runnableCaptor.capture());
        runnableCaptor.getValue().run();
        verify(mPnoScanRequestCallback).onPnoRequestFailed();
        assertFalse(mDut.getClientInterfaceInfos().get(CLIENT_IFACE_NAME).pnoScanStarted);
    }

    @Test
    public void testStartPnoScan_triggersSelfRecoveryOnEnodevThreshold() {
        mDut = initNl80211Native(false);
        setupClientModeInterfaceForTest(WIPHY_INDEX_0, null, null, null);

        when(mNl80211Utils.startPnoScan(
                anyInt(), any(), anyLong(), anyInt(), anyInt(), anyBoolean(), anyBoolean(),
                anyBoolean(), any(), any(), any()))
                .thenReturn(WifiScanner.REASON_NO_DEVICE);

        // Call startPnoScan ENODEV_RESTART_THRESHOLD times. It should not trigger SelfRecovery yet.
        for (int i = 0; i < Nl80211Native.ENODEV_RESTART_THRESHOLD; i++) {
            mDut.startPnoScan(CLIENT_IFACE_NAME, mPnoSettings, mExecutor, mPnoScanRequestCallback);
            verify(mSelfRecovery, never()).trigger(anyInt());
        }

        // Call startPnoScan one more time, which should trigger SelfRecovery.
        mDut.startPnoScan(CLIENT_IFACE_NAME, mPnoSettings, mExecutor, mPnoScanRequestCallback);
        verify(mSelfRecovery).trigger(SelfRecovery.REASON_SUBSYSTEM_RESTART);
    }

    @Test
    public void testStartPnoScan_alreadyStarted() {
        mDut = initNl80211Native(false);
        setupClientModeInterfaceForTest(WIPHY_INDEX_0, null, null, null);

        // Manually set pnoScanStarted to true
        mDut.getClientInterfaceInfos().get(CLIENT_IFACE_NAME).pnoScanStarted = true;

        when(mNl80211Utils.startPnoScan(
                anyInt(), any(), anyLong(), anyInt(), anyInt(), anyBoolean(), anyBoolean(),
                anyBoolean(), any(), any(), any()))
                .thenReturn(WifiScanner.REASON_SUCCEEDED);

        boolean result = mDut.startPnoScan(
                CLIENT_IFACE_NAME, mPnoSettings, mExecutor, mPnoScanRequestCallback);

        assertTrue(result);
        // Verify that Nl80211Utils.startPnoScan was still called
        verify(mNl80211Utils).startPnoScan(
                eq(CLIENT_IFACE_INDEX), any(), anyLong(), anyInt(), anyInt(), anyBoolean(),
                anyBoolean(), anyBoolean(), any(), any(), any());
        // Verify callback is still invoked
        ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);
        verify(mExecutor).execute(runnableCaptor.capture());
        runnableCaptor.getValue().run();
        verify(mPnoScanRequestCallback).onPnoRequestSucceeded();
    }

    @Test
    public void testStartPnoScan_withHiddenSsids_trimsCorrectly() {
        mDut = initNl80211Native(false);
        Nl80211Utils.ScanCapabilities scanCapabilities = new Nl80211Utils.ScanCapabilities(
                0, 1 /* maxNumSchedScanSsids */, 0, 0, 0, 0);
        Nl80211Utils.WiphyInfo wiphyInfo = new Nl80211Utils.WiphyInfo(
                new Nl80211Utils.BandInfo(), scanCapabilities,
                mock(Nl80211Utils.WiphyFeatures.class),
                mock(Nl80211Utils.DriverCapabilities.class));
        setupClientModeInterfaceForTest(WIPHY_INDEX_0, wiphyInfo.bandInfo, scanCapabilities, null);

        byte[] ssid1 = "ssid1".getBytes();
        byte[] ssid2 = "ssid2".getBytes();
        List<PnoNetwork> pnoNetworks = new ArrayList<>();
        PnoNetwork pnoNetwork1 = new PnoNetwork();
        pnoNetwork1.setSsid(ssid1);
        pnoNetwork1.setHidden(true);
        PnoNetwork pnoNetwork2 = new PnoNetwork();
        pnoNetwork2.setSsid(ssid2);
        pnoNetwork2.setHidden(true);
        pnoNetworks.add(pnoNetwork1);
        pnoNetworks.add(pnoNetwork2);
        when(mPnoSettings.getPnoNetworks()).thenReturn(pnoNetworks);
        when(mPnoSettings.getIntervalMillis()).thenReturn(15000L);
        when(mPnoSettings.getMin2gRssiDbm()).thenReturn(-70);
        when(mPnoSettings.getMin5gRssiDbm()).thenReturn(-80);
        when(mPnoSettings.getScanIterations()).thenReturn(5);
        when(mPnoSettings.getScanIntervalMultiplier()).thenReturn(3);

        when(mNl80211Utils.startPnoScan(
                eq(CLIENT_IFACE_INDEX), any(), anyLong(), anyInt(), anyInt(), anyBoolean(),
                anyBoolean(), anyBoolean(), eq(List.of(ssid1)), any(), any()))
                .thenReturn(WifiScanner.REASON_SUCCEEDED);

        boolean result = mDut.startPnoScan(
                CLIENT_IFACE_NAME, mPnoSettings, mExecutor, mPnoScanRequestCallback);

        assertTrue(result);
        verify(mNl80211Utils).startPnoScan(
                eq(CLIENT_IFACE_INDEX), any(), anyLong(), anyInt(), anyInt(), anyBoolean(),
                anyBoolean(), anyBoolean(), eq(List.of(ssid1)), any(), any());
    }

    @Test
    public void testStartPnoScan_withMatchSsids_trimsCorrectly() {
        mDut = initNl80211Native(false);
        Nl80211Utils.ScanCapabilities scanCapabilities = new Nl80211Utils.ScanCapabilities(
                0, 0, 1 /* maxMatchSets */, 0, 0, 0);
        Nl80211Utils.WiphyInfo wiphyInfo = new Nl80211Utils.WiphyInfo(
                new Nl80211Utils.BandInfo(), scanCapabilities,
                mock(Nl80211Utils.WiphyFeatures.class),
                mock(Nl80211Utils.DriverCapabilities.class));
        setupClientModeInterfaceForTest(WIPHY_INDEX_0, wiphyInfo.bandInfo, scanCapabilities, null);

        byte[] ssid1 = "ssid1".getBytes();
        byte[] ssid2 = "ssid2".getBytes();
        List<PnoNetwork> pnoNetworks = new ArrayList<>();
        PnoNetwork pnoNetwork1 = new PnoNetwork();
        pnoNetwork1.setSsid(ssid1);
        PnoNetwork pnoNetwork2 = new PnoNetwork();
        pnoNetwork2.setSsid(ssid2);
        pnoNetworks.add(pnoNetwork1);
        pnoNetworks.add(pnoNetwork2);
        when(mPnoSettings.getPnoNetworks()).thenReturn(pnoNetworks);
        when(mPnoSettings.getIntervalMillis()).thenReturn(15000L);
        when(mPnoSettings.getMin2gRssiDbm()).thenReturn(-70);
        when(mPnoSettings.getMin5gRssiDbm()).thenReturn(-80);
        when(mPnoSettings.getScanIterations()).thenReturn(5);
        when(mPnoSettings.getScanIntervalMultiplier()).thenReturn(3);

        when(mNl80211Utils.startPnoScan(
                eq(CLIENT_IFACE_INDEX), any(), anyLong(), anyInt(), anyInt(), anyBoolean(),
                anyBoolean(), anyBoolean(), any(), eq(List.of(ssid1)), any()))
                .thenReturn(WifiScanner.REASON_SUCCEEDED);

        boolean result = mDut.startPnoScan(
                CLIENT_IFACE_NAME, mPnoSettings, mExecutor, mPnoScanRequestCallback);

        assertTrue(result);
        verify(mNl80211Utils).startPnoScan(
                eq(CLIENT_IFACE_INDEX), any(), anyLong(), anyInt(), anyInt(), anyBoolean(),
                anyBoolean(), anyBoolean(), any(), eq(List.of(ssid1)), any());
    }

    @Test
    public void testStartPnoScan_addsDefaultFreqsIfManyNetworksWithoutFreqs() {
        mDut = initNl80211Native(false);
        Nl80211Utils.BandInfo bandInfo = new Nl80211Utils.BandInfo();
        bandInfo.band2g.add(2412);
        bandInfo.band2g.add(2417);
        bandInfo.band5g.add(5180);
        bandInfo.band5g.add(5200);
        bandInfo.band5g.add(5220);
        setupClientModeInterfaceForTest(WIPHY_INDEX_0, bandInfo, null, null);

        List<PnoNetwork> pnoNetworks = new ArrayList<>();
        // Add 4 networks without frequencies and 1 with frequencies.
        for (int i = 0; i < 4; i++) {
            PnoNetwork network = new PnoNetwork();
            network.setSsid(("ssid" + i).getBytes());
            network.setFrequenciesMhz(new int[0]); // No frequencies
            pnoNetworks.add(network);
        }
        PnoNetwork networkWithFreq = new PnoNetwork();
        networkWithFreq.setSsid("ssid_with_freq".getBytes());
        networkWithFreq.setFrequenciesMhz(new int[]{5220});
        pnoNetworks.add(networkWithFreq);
        when(mPnoSettings.getPnoNetworks()).thenReturn(pnoNetworks);
        when(mPnoSettings.getIntervalMillis()).thenReturn(15000L);
        when(mPnoSettings.getMin2gRssiDbm()).thenReturn(-70);
        when(mPnoSettings.getMin5gRssiDbm()).thenReturn(-80);
        when(mPnoSettings.getScanIterations()).thenReturn(5);
        when(mPnoSettings.getScanIntervalMultiplier()).thenReturn(3);

        List<Integer> expectedFrequencies = new ArrayList<>();
        expectedFrequencies.add(2412); // From PNO_SCAN_DEFAULT_FREQS_2G
        expectedFrequencies.add(2417); // From PNO_SCAN_DEFAULT_FREQS_2G
        expectedFrequencies.add(5180); // From PNO_SCAN_DEFAULT_FREQS_5G
        expectedFrequencies.add(5200); // From PNO_SCAN_DEFAULT_FREQS_5G
        expectedFrequencies.add(5220); // From networkWithFreq

        when(mNl80211Utils.startPnoScan(
                eq(CLIENT_IFACE_INDEX), any(), anyLong(), anyInt(), anyInt(), anyBoolean(),
                anyBoolean(), anyBoolean(), any(), any(), eq(expectedFrequencies)))
                .thenReturn(WifiScanner.REASON_SUCCEEDED);

        boolean result = mDut.startPnoScan(
                CLIENT_IFACE_NAME, mPnoSettings, mExecutor, mPnoScanRequestCallback);

        assertTrue(result);
        verify(mNl80211Utils).startPnoScan(
                eq(CLIENT_IFACE_INDEX), any(), anyLong(), anyInt(), anyInt(), anyBoolean(),
                anyBoolean(), anyBoolean(), any(), any(), eq(new ArrayList<>(expectedFrequencies)));
    }

    @Test
    public void testStartPnoScan_numScanPlansNotSupported() {
        mDut = initNl80211Native(false);
        // Set scan capabilities to not support multiple plans
        Nl80211Utils.ScanCapabilities scanCapabilities = new Nl80211Utils.ScanCapabilities(
                0, 0, 0, /* maxNumScanPlans */ 1, /* maxScanPlanInterval */ 20,
                /* maxScanPlanIterations */ 10);
        setupClientModeInterfaceForTest(WIPHY_INDEX_0, null, scanCapabilities, null);

        when(mPnoSettings.getIntervalMillis()).thenReturn(15000L);
        when(mPnoSettings.getMin2gRssiDbm()).thenReturn(-70);
        when(mPnoSettings.getMin5gRssiDbm()).thenReturn(-80);
        when(mPnoSettings.getScanIterations()).thenReturn(5);
        when(mPnoSettings.getScanIntervalMultiplier()).thenReturn(3);
        when(mPnoSettings.getPnoNetworks()).thenReturn(new ArrayList<>());

        when(mNl80211Utils.startPnoScan(anyInt(), any(), anyLong(), anyInt(), anyInt(),
                anyBoolean(), anyBoolean(), anyBoolean(), any(), any(), any()))
                .thenReturn(WifiScanner.REASON_SUCCEEDED);

        boolean result = mDut.startPnoScan(
                CLIENT_IFACE_NAME, mPnoSettings, mExecutor, mPnoScanRequestCallback);

        assertTrue(result);
        verify(mNl80211Utils).startPnoScan(
                anyInt(), eq(new ArrayList<>()), anyLong(), anyInt(), anyInt(), anyBoolean(),
                anyBoolean(), anyBoolean(), any(), any(), any());
    }

    @Test
    public void testStartPnoScan_maxRequestedScanIntervalNotSupported() {
        mDut = initNl80211Native(false);
        // Set a supported scan interval of 11 seconds but a max requested interval of 12 seconds.
        Nl80211Utils.ScanCapabilities scanCapabilities = new Nl80211Utils.ScanCapabilities(
                0, 0, 0,  /* maxNumScanPlans */ 2, /* maxScanPlanIntervalSeconds */ 11,
                /* maxScanPlanIterations */ 10);
        setupClientModeInterfaceForTest(WIPHY_INDEX_0, null, scanCapabilities, null);

        when(mPnoSettings.getIntervalMillis()).thenReturn(4000L);
        when(mPnoSettings.getMin2gRssiDbm()).thenReturn(-70);
        when(mPnoSettings.getMin5gRssiDbm()).thenReturn(-80);
        when(mPnoSettings.getScanIterations()).thenReturn(5);
        when(mPnoSettings.getScanIntervalMultiplier()).thenReturn(3);
        when(mPnoSettings.getPnoNetworks()).thenReturn(new ArrayList<>());

        when(mNl80211Utils.startPnoScan(anyInt(), any(), anyLong(), anyInt(), anyInt(),
                anyBoolean(), anyBoolean(), anyBoolean(), any(), any(), any()))
                .thenReturn(WifiScanner.REASON_SUCCEEDED);

        boolean result = mDut.startPnoScan(
                CLIENT_IFACE_NAME, mPnoSettings, mExecutor, mPnoScanRequestCallback);

        assertTrue(result);
        verify(mNl80211Utils).startPnoScan(
                anyInt(), eq(new ArrayList<>()), anyLong(), anyInt(), anyInt(), anyBoolean(),
                anyBoolean(), anyBoolean(), any(), any(), any());
    }

    @Test
    public void testStartPnoScan_numScanIterationsNotSupported() {
        mDut = initNl80211Native(false);
        // Set scan capabilities to not support the requested number of scan iterations.
        Nl80211Utils.ScanCapabilities scanCapabilities = new Nl80211Utils.ScanCapabilities(
                0, 0, 0, /* maxNumScanPlans */ 2 , /* maxScanPlanInterval */ 20,
                /* maxScanPlanIterations */ 4);
        setupClientModeInterfaceForTest(WIPHY_INDEX_0, null, scanCapabilities, null);

        when(mPnoSettings.getIntervalMillis()).thenReturn(15000L);
        when(mPnoSettings.getMin2gRssiDbm()).thenReturn(-70);
        when(mPnoSettings.getMin5gRssiDbm()).thenReturn(-80);
        when(mPnoSettings.getScanIterations()).thenReturn(5);
        when(mPnoSettings.getScanIntervalMultiplier()).thenReturn(3);
        when(mPnoSettings.getPnoNetworks()).thenReturn(new ArrayList<>());

        when(mNl80211Utils.startPnoScan(anyInt(), any(), anyLong(), anyInt(), anyInt(),
                anyBoolean(), anyBoolean(), anyBoolean(), any(), any(), any()))
                .thenReturn(WifiScanner.REASON_SUCCEEDED);

        boolean result = mDut.startPnoScan(
                CLIENT_IFACE_NAME, mPnoSettings, mExecutor, mPnoScanRequestCallback);

        assertTrue(result);
        verify(mNl80211Utils).startPnoScan(
                anyInt(), eq(new ArrayList<>()), anyLong(), anyInt(), anyInt(), anyBoolean(),
                anyBoolean(), anyBoolean(), any(), any(), any());
    }

    @Test
    public void testStartPnoScan_successResetsEnodevCounter() {
        mDut = initNl80211Native(false);
        setupClientModeInterfaceForTest(WIPHY_INDEX_0, null, null, null);

        // Fail a few times, but not enough to trigger recovery
        when(mNl80211Utils.startPnoScan(anyInt(), any(), anyLong(), anyInt(), anyInt(),
                anyBoolean(), anyBoolean(), anyBoolean(), any(), any(), any()))
                .thenReturn(WifiScanner.REASON_NO_DEVICE);
        for (int i = 0; i < Nl80211Native.ENODEV_RESTART_THRESHOLD - 1; i++) {
            mDut.startPnoScan(CLIENT_IFACE_NAME, mPnoSettings, mExecutor, mPnoScanRequestCallback);
            verify(mSelfRecovery, never()).trigger(anyInt());
        }

        // One successful PNO scan should reset the counter
        when(mNl80211Utils.startPnoScan(anyInt(), any(), anyLong(), anyInt(), anyInt(),
                anyBoolean(), anyBoolean(), anyBoolean(), any(), any(), any()))
                .thenReturn(WifiScanner.REASON_SUCCEEDED);
        mDut.startPnoScan(CLIENT_IFACE_NAME, mPnoSettings, mExecutor, mPnoScanRequestCallback);

        // Now, trigger failures again, and verify recovery is only triggered after the threshold
        when(mNl80211Utils.startPnoScan(anyInt(), any(), anyLong(), anyInt(), anyInt(),
                anyBoolean(), anyBoolean(), anyBoolean(), any(), any(), any()))
                .thenReturn(WifiScanner.REASON_NO_DEVICE);
        for (int i = 0; i < Nl80211Native.ENODEV_RESTART_THRESHOLD; i++) {
            mDut.startPnoScan(CLIENT_IFACE_NAME, mPnoSettings, mExecutor, mPnoScanRequestCallback);
            verify(mSelfRecovery, never()).trigger(anyInt());
        }
        mDut.startPnoScan(CLIENT_IFACE_NAME, mPnoSettings, mExecutor, mPnoScanRequestCallback);
        verify(mSelfRecovery).trigger(SelfRecovery.REASON_SUBSYSTEM_RESTART);
    }

    @Test
    public void testStopPnoScan_useWificondEnabled_callsWificond() {
        mDut = initNl80211Native(true);
        when(mWificondManager.stopPnoScan(CLIENT_IFACE_NAME)).thenReturn(true);
        assertTrue(mDut.stopPnoScan(CLIENT_IFACE_NAME));
        verify(mWificondManager).stopPnoScan(CLIENT_IFACE_NAME);
    }

    @Test
    public void testStopPnoScan_success() {
        mDut = initNl80211Native(false);
        setupClientModeInterfaceForTest(WIPHY_INDEX_0, null, null, null);
        mDut.getClientInterfaceInfos().get(CLIENT_IFACE_NAME).pnoScanStarted = true;

        when(mNl80211Utils.stopPnoScan(CLIENT_IFACE_INDEX)).thenReturn(true);

        assertTrue(mDut.stopPnoScan(CLIENT_IFACE_NAME));

        verify(mNl80211Utils).stopPnoScan(CLIENT_IFACE_INDEX);
        assertFalse(mDut.getClientInterfaceInfos().get(CLIENT_IFACE_NAME).pnoScanStarted);
    }

    @Test
    public void testStopPnoScan_failure() {
        mDut = initNl80211Native(false);
        setupClientModeInterfaceForTest(WIPHY_INDEX_0, null, null, null);
        mDut.getClientInterfaceInfos().get(CLIENT_IFACE_NAME).pnoScanStarted = true;

        when(mNl80211Utils.stopPnoScan(CLIENT_IFACE_INDEX)).thenReturn(false);

        assertFalse(mDut.stopPnoScan(CLIENT_IFACE_NAME));

        verify(mNl80211Utils).stopPnoScan(CLIENT_IFACE_INDEX);
        assertFalse(mDut.getClientInterfaceInfos().get(CLIENT_IFACE_NAME).pnoScanStarted);
    }

    @Test
    public void testStopPnoScan_nullIfaceName() {
        mDut = initNl80211Native(false);
        assertFalse(mDut.stopPnoScan(null));
        verify(mNl80211Utils, never()).stopPnoScan(anyInt());
    }

    @Test
    public void testStopPnoScan_notInitialized() {
        Nl80211Native nl80211Native = new Nl80211Native(mNl80211Proxy, mNl80211Utils, mNetdWrapper,
                mWificondManager, mWifiInjector, false);
        assertFalse(nl80211Native.stopPnoScan(CLIENT_IFACE_NAME));
        verify(mNl80211Utils, never()).stopPnoScan(anyInt());
    }

    @Test
    public void testStopPnoScan_noInterfaceInfo() {
        mDut = initNl80211Native(false);
        assertFalse(mDut.stopPnoScan(CLIENT_IFACE_NAME));
        verify(mNl80211Utils, never()).stopPnoScan(anyInt());
    }

    @Test
    public void testStopPnoScan_alreadyStopped() {
        mDut = initNl80211Native(false);
        setupClientModeInterfaceForTest(WIPHY_INDEX_0, null, null, null);
        assertFalse(mDut.getClientInterfaceInfos().get(CLIENT_IFACE_NAME).pnoScanStarted);

        when(mNl80211Utils.stopPnoScan(CLIENT_IFACE_INDEX)).thenReturn(true);

        assertTrue(mDut.stopPnoScan(CLIENT_IFACE_NAME));

        verify(mNl80211Utils).stopPnoScan(CLIENT_IFACE_INDEX);
        assertFalse(mDut.getClientInterfaceInfos().get(CLIENT_IFACE_NAME).pnoScanStarted);
    }

    @Test
    public void testAbortScan_useWificondEnabled_callsWificond() {
        mDut = initNl80211Native(true);
        mDut.abortScan(CLIENT_IFACE_NAME);
        verify(mWificondManager).abortScan(CLIENT_IFACE_NAME);
    }

    @Test
    public void testWificondSignalPoll_useWificondEnabled_callsWificond() {
        mDut = initNl80211Native(true);
        WifiNl80211Manager.SignalPollResult expectedResult =
                mock(WifiNl80211Manager.SignalPollResult.class);
        when(mWificondManager.signalPoll(CLIENT_IFACE_NAME)).thenReturn(expectedResult);
        assertEquals(expectedResult, mDut.wificondSignalPoll(CLIENT_IFACE_NAME));
        verify(mWificondManager).signalPoll(CLIENT_IFACE_NAME);
    }

    @Test
    public void testWificondSignalPoll_returnsNull() {
        mDut = initNl80211Native(false);
        assertNull(mDut.wificondSignalPoll(CLIENT_IFACE_NAME));
    }

    @Test
    public void testGetDeviceWiphyCapabilities_useWificondEnabled_callsWificond() {
        mDut = initNl80211Native(true);
        android.net.wifi.nl80211.DeviceWiphyCapabilities wificondCaps =
                new android.net.wifi.nl80211.DeviceWiphyCapabilities();
        when(mWificondManager.getDeviceWiphyCapabilities(CLIENT_IFACE_NAME))
                .thenReturn(wificondCaps);
        assertEquals(new DeviceWiphyCapabilities(wificondCaps),
                mDut.getDeviceWiphyCapabilities(CLIENT_IFACE_NAME));
        verify(mWificondManager).getDeviceWiphyCapabilities(CLIENT_IFACE_NAME);
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
                new Nl80211Utils.WiphyFeatures.Builder().build();
        Nl80211Utils.DriverCapabilities driverCapabilities =
                new Nl80211Utils.DriverCapabilities(5);
        Nl80211Utils.WiphyInfo wiphyInfo = new Nl80211Utils.WiphyInfo(
                bandInfo, scanCapabilities, wiphyFeatures, driverCapabilities);

        when(mNl80211Utils.getWiphyIndex(CLIENT_IFACE_NAME)).thenReturn(0);
        when(mNl80211Utils.getWiphyInfo(0)).thenReturn(wiphyInfo);

        DeviceWiphyCapabilities capabilities = mDut.getDeviceWiphyCapabilities(CLIENT_IFACE_NAME);

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
        when(mNl80211Utils.getWiphyIndex(CLIENT_IFACE_NAME)).thenReturn(-1);
        assertNull(mDut.getDeviceWiphyCapabilities(CLIENT_IFACE_NAME));
    }

    @Test
    public void testGetDeviceWiphyCapabilities_getWiphyInfoFails() {
        mDut = initNl80211Native(false);
        when(mNl80211Utils.getWiphyIndex(CLIENT_IFACE_NAME)).thenReturn(0);
        when(mNl80211Utils.getWiphyInfo(0)).thenReturn(null);
        assertNull(mDut.getDeviceWiphyCapabilities(CLIENT_IFACE_NAME));
    }

    @Test
    public void testGetDeviceWiphyCapabilities_notInitialized() {
        Nl80211Native nl80211Native = new Nl80211Native(mNl80211Proxy, mNl80211Utils, mNetdWrapper,
                mWificondManager, mWifiInjector, false);
        assertNull(nl80211Native.getDeviceWiphyCapabilities(CLIENT_IFACE_NAME));
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
    public void testGetChannelsMhzForBand_invalidBand() {
        mDut = initNl80211Native(false);
        setupClientModeInterfaceForTest(WIPHY_INDEX_0, null, null, null);
        int[] channels = mDut.getChannelsMhzForBand(-1);
        assertEquals(0, channels.length);
    }

    @Test
    public void testGetChannelsMhzForBand_wiphyInfoReturnsNull() {
        mDut = initNl80211Native(false);
        setupClientModeInterfaceForTest(WIPHY_INDEX_0, null, null, null);
        when(mNl80211Utils.getWiphyInfo(0)).thenReturn(null);
        int[] channels = mDut.getChannelsMhzForBand(WifiScanner.WIFI_BAND_24_GHZ);
        assertEquals(0, channels.length);
    }

    @Test
    public void testGetChannelsMhzForBand_success() {
        mDut = initNl80211Native(false);
        Nl80211Utils.BandInfo bandInfo = new Nl80211Utils.BandInfo();
        bandInfo.band2g.add(2412);
        bandInfo.band5g.add(5180);
        bandInfo.bandDfs.add(5260);
        bandInfo.band6g.add(5955);
        bandInfo.band60g.add(60480);
        setupClientModeInterfaceForTest(WIPHY_INDEX_0, bandInfo, null, null);

        // 2.4 GHz
        int[] channels2g = mDut.getChannelsMhzForBand(WifiScanner.WIFI_BAND_24_GHZ);
        assertArrayEquals(new int[]{2412}, channels2g);

        // 5 GHz
        int[] channels5g = mDut.getChannelsMhzForBand(WifiScanner.WIFI_BAND_5_GHZ);
        assertArrayEquals(new int[]{5180}, channels5g);

        // 5 GHz DFS
        int[] channelsDfs = mDut.getChannelsMhzForBand(WifiScanner.WIFI_BAND_5_GHZ_DFS_ONLY);
        assertArrayEquals(new int[]{5260}, channelsDfs);

        // 6 GHz
        int[] channels6g = mDut.getChannelsMhzForBand(WifiScanner.WIFI_BAND_6_GHZ);
        assertArrayEquals(new int[]{5955}, channels6g);

        // 60 GHz
        int[] channels60g = mDut.getChannelsMhzForBand(WifiScanner.WIFI_BAND_60_GHZ);
        assertArrayEquals(new int[]{60480}, channels60g);
    }

    @Test
    public void testGetMaxSsidsPerScan_useWificondEnabled_callsWificond() {
        assumeTrue(SdkLevel.isAtLeastT());
        mDut = initNl80211Native(true);
        when(mWificondManager.getMaxSsidsPerScan(CLIENT_IFACE_NAME)).thenReturn(16);
        assertEquals(16, mDut.getMaxSsidsPerScan(CLIENT_IFACE_NAME));
        verify(mWificondManager).getMaxSsidsPerScan(CLIENT_IFACE_NAME);
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

        when(mNl80211Utils.getWiphyIndex(CLIENT_IFACE_NAME)).thenReturn(WIPHY_INDEX_0);
        when(mNl80211Utils.getWiphyInfo(WIPHY_INDEX_0)).thenReturn(wiphyInfo);

        assertEquals(maxSsids, mDut.getMaxSsidsPerScan(CLIENT_IFACE_NAME));
    }

    @Test
    public void testGetMaxSsidsPerScan_getWiphyIndexFails() {
        mDut = initNl80211Native(false);
        when(mNl80211Utils.getWiphyIndex(CLIENT_IFACE_NAME)).thenReturn(-1);
        assertEquals(0, mDut.getMaxSsidsPerScan(CLIENT_IFACE_NAME));
    }

    @Test
    public void testGetMaxSsidsPerScan_getWiphyInfoFails() {
        mDut = initNl80211Native(false);
        when(mNl80211Utils.getWiphyIndex(CLIENT_IFACE_NAME)).thenReturn(WIPHY_INDEX_0);
        when(mNl80211Utils.getWiphyInfo(WIPHY_INDEX_0)).thenReturn(null);
        assertEquals(0, mDut.getMaxSsidsPerScan(CLIENT_IFACE_NAME));
    }

    @Test
    public void testSendMgmtFrame_useWificondEnabled_callsWificond() {
        mDut = initNl80211Native(true);
        byte[] frame = new byte[1];
        mDut.sendMgmtFrame(CLIENT_IFACE_NAME, frame, 0, mExecutor, mSendMgmtFrameCallback);
        verify(mWificondManager).sendMgmtFrame(
                eq(CLIENT_IFACE_NAME), eq(frame), eq(0), eq(mExecutor),
                eq(mSendMgmtFrameCallback));
    }

    @Test
    public void testSendMgmtFrame_throwsException() {
        mDut = initNl80211Native(false);
        assertThrows(UnsupportedOperationException.class,
                () -> mDut.sendMgmtFrame(
                        CLIENT_IFACE_NAME, new byte[1], 0, mExecutor, mSendMgmtFrameCallback));
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
    public void testSetupClientInterfaceRegistersCountryCodeCallback() {
        mDut = initNl80211Native(false);
        setupClientModeInterfaceForTest(WIPHY_INDEX_0, null, null, null);

        verify(mNl80211Proxy).registerBroadcastCallback(
                eq(NL80211_CMD_REG_CHANGE), any());
        verify(mNl80211Proxy).registerBroadcastCallback(
                eq(NetlinkConstants.NL80211_CMD_WIPHY_REG_CHANGE), any());

        // Tear down the interface and verify that the callbacks are unregistered.
        mDut.tearDownClientInterface(CLIENT_IFACE_NAME);
        verify(mNl80211Proxy).unregisterBroadcastCallback(
                eq(NL80211_CMD_REG_CHANGE), any());
        verify(mNl80211Proxy).unregisterBroadcastCallback(
                eq(NetlinkConstants.NL80211_CMD_WIPHY_REG_CHANGE), any());
    }

    @Test
    public void testSetupSoftApInterfaceRegistersCountryCodeCallback() {
        mDut = initNl80211Native(false);
        setupSoftApInterfaceForTest(WIPHY_INDEX_0, null);

        verify(mNl80211Proxy).registerBroadcastCallback(
                eq(NL80211_CMD_REG_CHANGE), any());
        verify(mNl80211Proxy).registerBroadcastCallback(
                eq(NetlinkConstants.NL80211_CMD_WIPHY_REG_CHANGE), any());

        // Tear down the interface and verify that the callbacks are unregistered.
        mDut.tearDownSoftApInterface(AP_IFACE_NAME);
        verify(mNl80211Proxy).unregisterBroadcastCallback(
                eq(NL80211_CMD_REG_CHANGE), any());
        verify(mNl80211Proxy).unregisterBroadcastCallback(
                eq(NetlinkConstants.NL80211_CMD_WIPHY_REG_CHANGE), any());
    }

    @Test
    public void testCountryCodeCallbackUnregisteredAfterLastInterfaceTornDown() {
        mDut = initNl80211Native(false);

        // Setup two ifaces
        setupSoftApInterfaceForTest(WIPHY_INDEX_0, null);
        setupClientModeInterfaceForTest(WIPHY_INDEX_0, null, null, null);
        verify(mNl80211Proxy).registerBroadcastCallback(
                eq(NL80211_CMD_REG_CHANGE), any());
        verify(mNl80211Proxy).registerBroadcastCallback(
                eq(NetlinkConstants.NL80211_CMD_WIPHY_REG_CHANGE), any());

        // Tear down AP iface should not unregister the callbacks
        mDut.tearDownSoftApInterface(AP_IFACE_NAME);
        verify(mNl80211Proxy, never()).unregisterBroadcastCallback(
                eq(NL80211_CMD_REG_CHANGE), any());
        verify(mNl80211Proxy, never()).unregisterBroadcastCallback(
                eq(NetlinkConstants.NL80211_CMD_WIPHY_REG_CHANGE), any());

        // Tear down client iface (the last iface) should unregister the callbacks
        mDut.tearDownClientInterface(CLIENT_IFACE_NAME);
        verify(mNl80211Proxy).unregisterBroadcastCallback(eq(NL80211_CMD_REG_CHANGE), any());
        verify(mNl80211Proxy).unregisterBroadcastCallback(
                eq(NetlinkConstants.NL80211_CMD_WIPHY_REG_CHANGE), any());
    }

    @Test
    public void testCountryCodeChanged_countryType_notifiesListeners() {
        mDut = initNl80211Native(false);
        setupClientModeInterfaceForTest(WIPHY_INDEX_0, null, null, null);
        mDut.registerCountryCodeChangedListener(mExecutor, mCountryCodeChangedListener);

        ArgumentCaptor<Nl80211BroadcastMonitor.Nl80211BroadcastCallback> callbackCaptor =
                ArgumentCaptor.forClass(Nl80211BroadcastMonitor.Nl80211BroadcastCallback.class);
        verify(mNl80211Proxy).registerBroadcastCallback(
                eq(NL80211_CMD_REG_CHANGE), callbackCaptor.capture());

        GenericNetlinkMsg regChangeMsg = mock(GenericNetlinkMsg.class);
        when(regChangeMsg.getAttributeValueAsByte(eq(NL80211_ATTR_REG_TYPE)))
                .thenReturn((byte) NL80211_REGDOM_TYPE_COUNTRY);
        when(regChangeMsg.getAttributeValueAsString(eq(NL80211_ATTR_REG_ALPHA2))).thenReturn("CA");

        callbackCaptor.getValue().onEvent(NL80211_CMD_REG_CHANGE, regChangeMsg);

        ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);
        verify(mExecutor).execute(runnableCaptor.capture());
        runnableCaptor.getValue().run();

        verify(mCountryCodeChangedListener).onCountryCodeChanged("CA");
    }

    @Test
    public void testCountryCodeChanged_worldType_notifiesListeners() {
        mDut = initNl80211Native(false);
        setupClientModeInterfaceForTest(WIPHY_INDEX_0, null, null, null);
        mDut.registerCountryCodeChangedListener(mExecutor, mCountryCodeChangedListener);
        when(mNl80211Utils.getCountryCode(WIPHY_INDEX_0)).thenReturn("DE");

        ArgumentCaptor<Nl80211BroadcastMonitor.Nl80211BroadcastCallback> callbackCaptor =
                ArgumentCaptor.forClass(Nl80211BroadcastMonitor.Nl80211BroadcastCallback.class);
        verify(mNl80211Proxy).registerBroadcastCallback(
                eq(NL80211_CMD_REG_CHANGE), callbackCaptor.capture());

        GenericNetlinkMsg regChangeMsg = mock(GenericNetlinkMsg.class);
        when(regChangeMsg.getAttributeValueAsByte(eq(NL80211_ATTR_REG_TYPE)))
                .thenReturn((byte) NL80211_REGDOM_TYPE_WORLD);

        callbackCaptor.getValue().onEvent(NL80211_CMD_REG_CHANGE, regChangeMsg);

        ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);
        verify(mExecutor).execute(runnableCaptor.capture());
        runnableCaptor.getValue().run();

        verify(mCountryCodeChangedListener).onCountryCodeChanged("DE");
    }

    @Test
    public void testCountryCodeChanged_sameCode_doesNotNotifyListeners() {
        mDut = initNl80211Native(false);
        when(mNl80211Utils.getCountryCode(WIPHY_INDEX_0)).thenReturn(COUNTRY_CODE);
        setupClientModeInterfaceForTest(WIPHY_INDEX_0, null, null, null);
        mDut.registerCountryCodeChangedListener(mExecutor, mCountryCodeChangedListener);
        // Reset to clear the notification from initial setup.
        reset(mCountryCodeChangedListener);

        ArgumentCaptor<Nl80211BroadcastMonitor.Nl80211BroadcastCallback> callbackCaptor =
                ArgumentCaptor.forClass(Nl80211BroadcastMonitor.Nl80211BroadcastCallback.class);
        verify(mNl80211Proxy).registerBroadcastCallback(
                eq(NL80211_CMD_REG_CHANGE), callbackCaptor.capture());

        GenericNetlinkMsg regChangeMsg = mock(GenericNetlinkMsg.class);
        when(regChangeMsg.getAttributeValueAsByte(eq(NL80211_ATTR_REG_TYPE)))
                .thenReturn((byte) NL80211_REGDOM_TYPE_COUNTRY);
        when(regChangeMsg.getAttributeValueAsString(eq(NL80211_ATTR_REG_ALPHA2)))
                .thenReturn(COUNTRY_CODE);

        callbackCaptor.getValue().onEvent(NL80211_CMD_REG_CHANGE, regChangeMsg);

        verify(mExecutor, never()).execute(any());
        verify(mCountryCodeChangedListener, never()).onCountryCodeChanged(anyString());
    }

    /**
     * This test verifies that different wiphy country codes will be sent to the listeners
     * sequentially. While this is non-optimal behavior, it is the current behavior of wificond,
     * which was ported as-is to Nl80211Native's direct Nl80211 implementation.
     */
    @Test
    public void testCountryCodeChanged_differentWiphyCountryCodes_notifiesListenersSequentially() {
        mDut = initNl80211Native(false);
        setupClientModeInterfaceForTest(WIPHY_INDEX_0, null, null, null);
        setupSoftApInterfaceForTest(WIPHY_INDEX_1, null);

        mDut.registerCountryCodeChangedListener(mExecutor, mCountryCodeChangedListener);

        ArgumentCaptor<Nl80211BroadcastMonitor.Nl80211BroadcastCallback> callbackCaptor =
                ArgumentCaptor.forClass(Nl80211BroadcastMonitor.Nl80211BroadcastCallback.class);
        verify(mNl80211Proxy, times(1)).registerBroadcastCallback(
                eq(NL80211_CMD_REG_CHANGE), callbackCaptor.capture());

        when(mNl80211Utils.getCountryCode(WIPHY_INDEX_0)).thenReturn("JP");
        when(mNl80211Utils.getCountryCode(WIPHY_INDEX_1)).thenReturn("US");

        GenericNetlinkMsg regChangeMsg = mock(GenericNetlinkMsg.class);
        when(regChangeMsg.getAttributeValueAsByte(eq(NL80211_ATTR_REG_TYPE)))
                .thenReturn((byte) NL80211_REGDOM_TYPE_WORLD);
        when(regChangeMsg.getAttributeValueAsString(eq(NL80211_ATTR_REG_ALPHA2))).thenReturn(null);

        callbackCaptor.getValue().onEvent(NL80211_CMD_REG_CHANGE, regChangeMsg);

        ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);
        verify(mExecutor, times(2)).execute(runnableCaptor.capture());
        for (Runnable runnable : runnableCaptor.getAllValues()) {
            runnable.run();
        }

        // The listener should be called twice, once for each wiphy in sorted order
        ArgumentCaptor<String> countryCodeCaptor = ArgumentCaptor.forClass(String.class);
        verify(mCountryCodeChangedListener, times(2))
                .onCountryCodeChanged(countryCodeCaptor.capture());
        assertEquals("JP", countryCodeCaptor.getAllValues().get(0));
        assertEquals("US", countryCodeCaptor.getAllValues().get(1));
        verify(mNl80211Utils, times(2)).clearWiphyInfoCaches();
    }

    @Test
    public void testCountryCodeChanged_getWiphyInfoFails_preservesOriginalWiphyInfo() {
        mDut = initNl80211Native(false);
        setupClientModeInterfaceForTest(WIPHY_INDEX_0, null, null, null);
        Nl80211Native.ClientInterfaceInfo info =
                mDut.getClientInterfaceInfos().get(CLIENT_IFACE_NAME);
        Nl80211Utils.WiphyInfo originalWiphyInfo = info.wiphyInfo;
        assertNotNull(originalWiphyInfo);

        ArgumentCaptor<Nl80211BroadcastMonitor.Nl80211BroadcastCallback> callbackCaptor =
                ArgumentCaptor.forClass(Nl80211BroadcastMonitor.Nl80211BroadcastCallback.class);
        verify(mNl80211Proxy).registerBroadcastCallback(
                eq(NL80211_CMD_REG_CHANGE), callbackCaptor.capture());

        // Simulate getWiphyInfo failing
        when(mNl80211Utils.getWiphyInfo(WIPHY_INDEX_0)).thenReturn(null);

        GenericNetlinkMsg regChangeMsg = mock(GenericNetlinkMsg.class);
        when(regChangeMsg.getAttributeValueAsByte(eq(NL80211_ATTR_REG_TYPE)))
                .thenReturn((byte) NL80211_REGDOM_TYPE_WORLD);

        // Trigger the regulatory change
        callbackCaptor.getValue().onEvent(NL80211_CMD_REG_CHANGE, regChangeMsg);

        // Verify that the original wiphyInfo was preserved (not replaced with null)
        assertNotNull(info.wiphyInfo);
        assertEquals(originalWiphyInfo, info.wiphyInfo);
    }

    @Test
    public void testSetupInterfaceForClientModeSetsInitialCountryCode() {
        mDut = initNl80211Native(false);
        when(mNl80211Utils.getCountryCode(WIPHY_INDEX_0)).thenReturn("US");

        // Register listener before setting up the interface
        mDut.registerCountryCodeChangedListener(mExecutor, mCountryCodeChangedListener);
        setupClientModeInterfaceForTest(WIPHY_INDEX_0, null, null, null);

        ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);
        verify(mExecutor).execute(runnableCaptor.capture());
        runnableCaptor.getValue().run();

        verify(mCountryCodeChangedListener).onCountryCodeChanged("US");
    }

    @Test
    public void testSetupSoftApInterfaceSetsInitialCountryCode() {
        mDut = initNl80211Native(false);
        when(mNl80211Utils.getCountryCode(WIPHY_INDEX_0)).thenReturn("JP");

        // Register listener before setting up the interface
        mDut.registerCountryCodeChangedListener(mExecutor, mCountryCodeChangedListener);
        setupSoftApInterfaceForTest(WIPHY_INDEX_0, null);

        ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);
        verify(mExecutor).execute(runnableCaptor.capture());
        runnableCaptor.getValue().run();

        verify(mCountryCodeChangedListener).onCountryCodeChanged("JP");
    }

    @Test
    public void testCountryCodeChange_updatesWiphyInfo() {
        mDut = initNl80211Native(false);
        // Initial setup with no 6GHz support
        Nl80211Utils.BandInfo bandInfoNo6g = new Nl80211Utils.BandInfo();
        bandInfoNo6g.band6g.clear();
        setupClientModeInterfaceForTest(WIPHY_INDEX_0, bandInfoNo6g, null, null);
        mDut.registerCountryCodeChangedListener(mExecutor, mCountryCodeChangedListener);
        assertEquals(bandInfoNo6g,
                mDut.getClientInterfaceInfos().get(CLIENT_IFACE_NAME).wiphyInfo.bandInfo);

        // Mock a new WiphyInfo with 6GHz support after the country code change
        Nl80211Utils.BandInfo bandInfoWith6g = new Nl80211Utils.BandInfo();
        bandInfoWith6g.band6g.add(6150);
        Nl80211Utils.WiphyInfo wiphyInfoWith6g = new Nl80211Utils.WiphyInfo(
                bandInfoWith6g,
                mock(Nl80211Utils.ScanCapabilities.class),
                mock(Nl80211Utils.WiphyFeatures.class),
                mock(Nl80211Utils.DriverCapabilities.class));
        when(mNl80211Utils.getWiphyInfo(WIPHY_INDEX_0)).thenReturn(wiphyInfoWith6g);

        ArgumentCaptor<Nl80211BroadcastMonitor.Nl80211BroadcastCallback> callbackCaptor =
                ArgumentCaptor.forClass(Nl80211BroadcastMonitor.Nl80211BroadcastCallback.class);
        verify(mNl80211Proxy).registerBroadcastCallback(
                eq(NL80211_CMD_REG_CHANGE), callbackCaptor.capture());

        GenericNetlinkMsg regChangeMsg = mock(GenericNetlinkMsg.class);
        when(regChangeMsg.getAttributeValueAsByte(eq(NL80211_ATTR_REG_TYPE)))
                .thenReturn((byte) NL80211_REGDOM_TYPE_COUNTRY);
        when(regChangeMsg.getAttributeValueAsString(eq(NL80211_ATTR_REG_ALPHA2))).thenReturn("CA");
        callbackCaptor.getValue().onEvent(NL80211_CMD_REG_CHANGE, regChangeMsg);

        verify(mNl80211Utils).clearWiphyInfoCaches();
        // The wiphy info in ClientInterfaceInfo should be updated to support 6Ghz.
        assertEquals(bandInfoWith6g,
                mDut.getClientInterfaceInfos().get(CLIENT_IFACE_NAME).wiphyInfo.bandInfo);
    }

    @Test
    public void testRegisterAndUnregisterCountryCodeChangedListener() {
        mDut = initNl80211Native(false);
        Nl80211Native.CountryCodeChangedListener secondListener =
                mock(Nl80211Native.CountryCodeChangedListener.class);
        Nl80211Native.CountryCodeChangedListener thirdListener =
                mock(Nl80211Native.CountryCodeChangedListener.class);
        when(mNl80211Utils.getCountryCode(WIPHY_INDEX_0)).thenReturn("US");


        // Registering two listeners without an active interface should not register broadcast cb.
        mDut.registerCountryCodeChangedListener(mExecutor, mCountryCodeChangedListener);
        mDut.registerCountryCodeChangedListener(mExecutor, secondListener);
        verify(mNl80211Proxy, never()).registerBroadcastCallback(
                eq(NL80211_CMD_REG_CHANGE), any());

        // Now setup an interface, which should register the broadcast callback and notify
        // the currently registered listeners.
        setupClientModeInterfaceForTest(WIPHY_INDEX_0, null, null, null);
        verify(mNl80211Proxy).registerBroadcastCallback(
                eq(NL80211_CMD_REG_CHANGE), any());
        ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);
        verify(mExecutor, times(2)).execute(runnableCaptor.capture());
        for (Runnable r : runnableCaptor.getAllValues()) {
            r.run();
        }
        verify(mCountryCodeChangedListener).onCountryCodeChanged("US");
        verify(secondListener).onCountryCodeChanged("US");

        // Registering a third listener should not register the broadcast callback again.
        mDut.registerCountryCodeChangedListener(mExecutor, thirdListener);
        verify(mNl80211Proxy, times(1)).registerBroadcastCallback(
                eq(NL80211_CMD_REG_CHANGE), any());

        // Unregistering one listener should not unregister the broadcast callback.
        mDut.unregisterCountryCodeChangedListener(mCountryCodeChangedListener);
        verify(mNl80211Proxy, never()).unregisterBroadcastCallback(
                eq(NL80211_CMD_REG_CHANGE), any());
    }

    @Test
    public void testUnregisterCountryCodeChangedListener_useWificondEnabled_callsWificond() {
        mDut = initNl80211Native(true);
        mDut.unregisterCountryCodeChangedListener(mCountryCodeChangedListener);
        verify(mWificondManager).unregisterCountryCodeChangedListener(
                mCountryCodeChangedListener);
    }

    @Test
    public void testNotifyCountryCodeChanged_useWificondEnabled_callsWificond() {
        assumeTrue(SdkLevel.isAtLeastT());
        mDut = initNl80211Native(true);
        mDut.notifyCountryCodeChanged(COUNTRY_CODE);
        verify(mWificondManager).notifyCountryCodeChanged(COUNTRY_CODE);
    }

    @Test
    public void testNotifyCountryCodeChanged_success() {
        mDut = initNl80211Native(false);
        // Initial setup with no 6GHz support
        Nl80211Utils.BandInfo bandInfoNo6g = new Nl80211Utils.BandInfo();
        bandInfoNo6g.band6g.clear();
        setupClientModeInterfaceForTest(WIPHY_INDEX_0, bandInfoNo6g, null, null);
        assertEquals(bandInfoNo6g,
                mDut.getClientInterfaceInfos().get(CLIENT_IFACE_NAME).wiphyInfo.bandInfo);

        // Verify that 6GHz is not supported initially
        assertEquals(0, mDut.getChannelsMhzForBand(WifiScanner.WIFI_BAND_6_GHZ).length);

        // Mock the new WiphyInfo that will be returned after the country code change
        Nl80211Utils.BandInfo bandInfoWith6g = new Nl80211Utils.BandInfo();
        bandInfoWith6g.band6g.add(6150); // Add a 6GHz channel
        Nl80211Utils.WiphyInfo wiphyInfoWith6g = new Nl80211Utils.WiphyInfo(
                bandInfoWith6g,
                mock(Nl80211Utils.ScanCapabilities.class),
                mock(Nl80211Utils.WiphyFeatures.class),
                mock(Nl80211Utils.DriverCapabilities.class));
        when(mNl80211Utils.getWiphyInfo(WIPHY_INDEX_0)).thenReturn(wiphyInfoWith6g);

        mDut.notifyCountryCodeChanged(COUNTRY_CODE);

        verify(mNl80211Utils).clearWiphyInfoCaches();
        // The wiphy info in ClientInterfaceInfo should be updated to support 6Ghz.
        assertEquals(bandInfoWith6g,
                mDut.getClientInterfaceInfos().get(CLIENT_IFACE_NAME).wiphyInfo.bandInfo);
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

    @Test
    public void testBroadcastEvent_onNewScanResults_resetsScanningFlag() {
        mDut = initNl80211Native(false);
        setupClientModeInterfaceForTest(WIPHY_INDEX_0, null, null, null);
        // Manually set the scanning flag to true to simulate an ongoing scan
        mDut.getClientInterfaceInfos().get(CLIENT_IFACE_NAME).scanning = true;

        verify(mNl80211Proxy).registerBroadcastCallback(eq(NL80211_CMD_NEW_SCAN_RESULTS),
                mNl80211BroadcastCallbackCaptor.capture());
        GenericNetlinkMsg genericNetlinkMessage = mock(GenericNetlinkMsg.class);
        when(genericNetlinkMessage.getAttributeValueAsInteger(NL80211_ATTR_IFINDEX))
                .thenReturn(CLIENT_IFACE_INDEX);

        mNl80211BroadcastCallbackCaptor.getValue().onEvent(NL80211_CMD_NEW_SCAN_RESULTS,
                genericNetlinkMessage);

        assertFalse(mDut.getClientInterfaceInfos().get(CLIENT_IFACE_NAME).scanning);
    }

    @Test
    public void testBroadcastEvent_onScanAborted_resetsScanningFlag() {
        mDut = initNl80211Native(false);
        setupClientModeInterfaceForTest(WIPHY_INDEX_0, null, null, null);
        // Manually set the scanning flag to true to simulate an ongoing scan
        mDut.getClientInterfaceInfos().get(CLIENT_IFACE_NAME).scanning = true;

        verify(mNl80211Proxy).registerBroadcastCallback(eq(NL80211_CMD_SCAN_ABORTED),
                mNl80211BroadcastCallbackCaptor.capture());
        GenericNetlinkMsg genericNetlinkMessage = mock(GenericNetlinkMsg.class);
        when(genericNetlinkMessage.getAttributeValueAsInteger(NL80211_ATTR_IFINDEX))
                .thenReturn(CLIENT_IFACE_INDEX);

        mNl80211BroadcastCallbackCaptor.getValue().onEvent(NL80211_CMD_SCAN_ABORTED,
                genericNetlinkMessage);

        assertFalse(mDut.getClientInterfaceInfos().get(CLIENT_IFACE_NAME).scanning);
    }
}
