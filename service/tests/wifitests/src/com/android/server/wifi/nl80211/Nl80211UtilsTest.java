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

import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_ATTR_BSS;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_ATTR_EXT_FEATURES;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_ATTR_FEATURE_FLAGS;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_ATTR_IFINDEX;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_ATTR_IFNAME;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_ATTR_MAC;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_ATTR_MAX_MATCH_SETS;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_ATTR_MAX_NUM_AKM_SUITES;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_ATTR_MAX_NUM_SCAN_SSIDS;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_ATTR_MAX_NUM_SCHED_SCAN_PLANS;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_ATTR_MAX_NUM_SCHED_SCAN_SSIDS;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_ATTR_MAX_SCAN_PLAN_INTERVAL;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_ATTR_MAX_SCAN_PLAN_ITERATIONS;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_ATTR_PROTOCOL_FEATURES;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_ATTR_WIPHY;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_ATTR_WIPHY_BANDS;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_BAND_2GHZ;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_BAND_5GHZ;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_BAND_ATTR_FREQS;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_BAND_ATTR_HT_CAPA;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_BAND_ATTR_VHT_CAPA;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_BSS_BSSID;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_BSS_CAPABILITY;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_BSS_CHAIN_SIGNAL;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_BSS_FREQUENCY;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_BSS_INFORMATION_ELEMENTS;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_BSS_SIGNAL_MBM;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_BSS_STATUS;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_BSS_STATUS_ASSOCIATED;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_BSS_TSF;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_CMD_GET_INTERFACE;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_CMD_GET_PROTOCOL_FEATURES;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_CMD_GET_SCAN;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_CMD_GET_WIPHY;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_CMD_NEW_INTERFACE;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_CMD_NEW_SCAN_RESULTS;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_CMD_NEW_WIPHY;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_FREQUENCY_ATTR_FREQ;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_PROTOCOL_FEATURE_SPLIT_WIPHY_DUMP;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyShort;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.net.module.util.netlink.StructNlAttr;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;

import java.net.NetworkInterface;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

public class Nl80211UtilsTest {

    private static final int TEST_WIPHY_INDEX = 4;
    private static final int TEST_IF_INDEX = 5;
    private static final String TEST_IF_NAME = "wlan0";
    private static final byte[] TEST_MAC_ADDR = new byte[]{0x11, 0x22, 0x33, 0x44, 0x55, 0x66};
    private static final byte[] TEST_MAC_ADDR_2 = new byte[]{0x22, 0x22, 0x33, 0x44, 0x55, 0x66};
    private static final byte[] TEST_SSID = new byte[]{'S', 'S', 'I', 'D'};
    private static final byte[] TEST_SSID_IE = {0x00, 0x04, 'S', 'S', 'I', 'D'};
    private static final byte[] TEST_SSID_2 = new byte[]{'S', 'S', 'I', 'D', '2'};
    private static final byte[] TEST_SSID_IE_2 = {0x00, 0x05, 'S', 'S', 'I', 'D', '2'};


    // Test request messages
    private static final GenericNetlinkMsg TEST_NL80211_REQUEST_GET_PROTOCOL_FEATURES =
            new GenericNetlinkMsg(NL80211_CMD_GET_PROTOCOL_FEATURES, (short) 0, (short) 0, 0);
    private static final GenericNetlinkMsg TEST_NL80211_REQUEST_GET_WIPHY =
            new GenericNetlinkMsg(NL80211_CMD_GET_WIPHY, (short) 0, (short) 0, 0);

    private static final GenericNetlinkMsg TEST_NL80211_REQUEST_GET_INTERFACE =
            new GenericNetlinkMsg(NL80211_CMD_GET_INTERFACE, (short) 0, (short) 0, 0);
    private static final GenericNetlinkMsg TEST_NL80211_REQUEST_GET_SCAN =
            new GenericNetlinkMsg(NL80211_CMD_GET_SCAN, (short) 0, (short) 0, 0);

    @Mock private Nl80211Proxy mNl80211Proxy;
    @Mock private NetworkInterface mNetworkInterface;
    private Nl80211Utils mNl80211Utils;
    private MockitoSession mSession;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        mSession = ExtendedMockito.mockitoSession()
                .strictness(Strictness.LENIENT)
                .mockStatic(NetworkInterface.class, withSettings().lenient())
                .startMocking();
        mNl80211Utils = new Nl80211Utils(mNl80211Proxy);
        when(NetworkInterface.getByName(anyString())).thenReturn(mNetworkInterface);
        when(mNetworkInterface.getIndex()).thenReturn(TEST_IF_INDEX);

        // Mock createNl80211Request calls
        when(mNl80211Proxy.createNl80211Request(NL80211_CMD_GET_PROTOCOL_FEATURES))
                .thenReturn(TEST_NL80211_REQUEST_GET_PROTOCOL_FEATURES);
        when(mNl80211Proxy.createNl80211Request(eq(NL80211_CMD_GET_WIPHY), anyShort(), any()))
                .thenReturn(TEST_NL80211_REQUEST_GET_WIPHY);
        when(mNl80211Proxy.createNl80211Request(eq(NL80211_CMD_GET_WIPHY), any()))
                .thenReturn(TEST_NL80211_REQUEST_GET_WIPHY);
        when(mNl80211Proxy.createNl80211Request(eq(NL80211_CMD_GET_INTERFACE), anyShort(), any()))
                .thenReturn(TEST_NL80211_REQUEST_GET_INTERFACE);
        when(mNl80211Proxy.createNl80211Request(eq(NL80211_CMD_GET_SCAN), anyShort(), any()))
                .thenReturn(TEST_NL80211_REQUEST_GET_SCAN);
    }

    @After
    public void cleanup() {
        if (mSession != null) {
            mSession.finishMocking();
        }
    }

    private void setupProtocolFeaturesResponse(int features) {
        GenericNetlinkMsg msg = new GenericNetlinkMsg(
                NL80211_CMD_GET_PROTOCOL_FEATURES, (short) 0, (short) 0, 0);
        msg.addAttribute(
                new StructNlAttr(NL80211_ATTR_PROTOCOL_FEATURES, features));
        when(mNl80211Proxy.sendMessageAndReceiveResponse(
                TEST_NL80211_REQUEST_GET_PROTOCOL_FEATURES)).thenReturn(new Nl80211Response(msg));
    }

    private GenericNetlinkMsg createTestScanResultNetlinkMessage(byte[] bssid, byte[] ie,
            int freq, int signal, long tsf, short capability, boolean associated,
            List<RadioChainInfo> radioChainInfos, short command) {
        GenericNetlinkMsg msg = new GenericNetlinkMsg(command, (short) 0, (short) 0, 0);

        ByteBuffer capaBuf = ByteBuffer.allocate(Short.BYTES).order(ByteOrder.LITTLE_ENDIAN);
        capaBuf.putShort(capability);

        List<StructNlAttr> bssAttrs = new ArrayList<>();
        bssAttrs.add(new StructNlAttr(NL80211_BSS_BSSID, bssid));
        bssAttrs.add(new StructNlAttr(NL80211_BSS_FREQUENCY, freq));
        bssAttrs.add(new StructNlAttr(NL80211_BSS_INFORMATION_ELEMENTS, ie));
        bssAttrs.add(new StructNlAttr(NL80211_BSS_SIGNAL_MBM, signal));
        bssAttrs.add(new StructNlAttr(NL80211_BSS_TSF, tsf));
        bssAttrs.add(new StructNlAttr(NL80211_BSS_CAPABILITY, capaBuf.array()));

        if (associated) {
            bssAttrs.add(new StructNlAttr(NL80211_BSS_STATUS,
                    NL80211_BSS_STATUS_ASSOCIATED));
        }

        if (radioChainInfos != null && !radioChainInfos.isEmpty()) {
            int chainSignalPayloadLength = 0;
            List<StructNlAttr> chainSignalAttrs = new ArrayList<>();
            for (RadioChainInfo info : radioChainInfos) {
                StructNlAttr chainAttr = new StructNlAttr(
                        (short) info.chainId, new byte[]{(byte) info.level});
                chainSignalAttrs.add(chainAttr);
                chainSignalPayloadLength += chainAttr.getAlignedLength();
            }

            ByteBuffer chainSignalPayload = ByteBuffer.allocate(chainSignalPayloadLength);
            for (StructNlAttr chainAttr : chainSignalAttrs) {
                chainAttr.pack(chainSignalPayload);
            }
            bssAttrs.add(new StructNlAttr(NL80211_BSS_CHAIN_SIGNAL,
                    chainSignalPayload.array()));
        }

        int bssPayloadLength = 0;
        for (StructNlAttr attr : bssAttrs) {
            bssPayloadLength += attr.getAlignedLength();
        }

        ByteBuffer bssPayload = ByteBuffer.allocate(bssPayloadLength);
        for (StructNlAttr attr : bssAttrs) {
            attr.pack(bssPayload);
        }

        msg.addAttribute(new StructNlAttr(NL80211_ATTR_BSS, bssPayload.array()));
        return msg;
    }

    @Test
    public void testGetScanResults_success_singleScanResult() {
        int freq = 2412;
        int signal = -50;
        long tsf = 123456789L;
        short capability = 0x1000; // ESS
        boolean associated = true;
        List<RadioChainInfo> radioChainInfos = new ArrayList<>();
        radioChainInfos.add(new RadioChainInfo(0, -52));
        radioChainInfos.add(new RadioChainInfo(1, -55));

        GenericNetlinkMsg scanResultMsg = createTestScanResultNetlinkMessage(
                TEST_MAC_ADDR, TEST_SSID_IE, freq, signal, tsf, capability, associated,
                radioChainInfos, NL80211_CMD_NEW_SCAN_RESULTS);

        when(mNl80211Proxy.sendMessageAndReceiveResponse(TEST_NL80211_REQUEST_GET_SCAN))
                .thenReturn(new Nl80211Response(scanResultMsg));

        List<NativeScanResult> results = mNl80211Utils.getScanResults(TEST_IF_NAME);

        assertNotNull(results);
        assertEquals(1, results.size());
        NativeScanResult result = results.get(0);
        assertArrayEquals(TEST_SSID, result.ssid);
        assertArrayEquals(TEST_MAC_ADDR, result.bssid);
        assertArrayEquals(TEST_SSID_IE, result.infoElement);
        assertEquals(freq, result.frequency);
        assertEquals(signal, result.signalMbm);
        assertEquals(tsf, result.tsf);
        assertEquals((char) capability, result.capability);
        assertEquals(associated, result.associated);
        assertEquals(radioChainInfos.size(), result.radioChainInfos.size());
        for (int i = 0; i < radioChainInfos.size(); i++) {
            assertEquals(radioChainInfos.get(i).chainId, result.radioChainInfos.get(i).chainId);
            assertEquals(radioChainInfos.get(i).level, result.radioChainInfos.get(i).level);
        }
    }

    @Test
    public void testGetScanResults_success_multipleScanResults() {
        int freq1 = 2412;
        int signal1 = -50;
        long tsf1 = 100000000L;
        short capability1 = 0x1000;

        int freq2 = 5180;
        int signal2 = -60;
        long tsf2 = 200000000L;
        short capability2 = 0x1000;

        GenericNetlinkMsg scanResultMsg1 = createTestScanResultNetlinkMessage(
                TEST_MAC_ADDR, TEST_SSID_IE, freq1, signal1, tsf1, capability1, true, null,
                NL80211_CMD_NEW_SCAN_RESULTS);
        GenericNetlinkMsg scanResultMsg2 = createTestScanResultNetlinkMessage(
                TEST_MAC_ADDR_2, TEST_SSID_IE_2, freq2, signal2, tsf2, capability2, false, null,
                NL80211_CMD_NEW_SCAN_RESULTS);

        when(mNl80211Proxy.sendMessageAndReceiveResponse(TEST_NL80211_REQUEST_GET_SCAN))
                .thenReturn(new Nl80211Response(scanResultMsg1, scanResultMsg2));

        List<NativeScanResult> results = mNl80211Utils.getScanResults(TEST_IF_NAME);

        assertNotNull(results);
        assertEquals(2, results.size());

        NativeScanResult result1 = results.get(0);
        assertArrayEquals(TEST_SSID, result1.ssid);
        assertArrayEquals(TEST_MAC_ADDR, result1.bssid);
        assertEquals(freq1, result1.frequency);
        assertTrue(result1.associated);

        NativeScanResult result2 = results.get(1);
        assertArrayEquals(TEST_SSID_2, result2.ssid);
        assertArrayEquals(TEST_MAC_ADDR_2, result2.bssid);
        assertEquals(freq2, result2.frequency);
        assertFalse(result2.associated);
    }

    @Test
    public void testGetScanResults_unexpectedCommandInResponse() {
        int freq = 2412;
        int signal = -50;
        long tsf = 123456789L;
        short capability = 0x1000; // ESS

        // Create a message with an unexpected command type
        GenericNetlinkMsg unexpectedMsg = createTestScanResultNetlinkMessage(
                TEST_MAC_ADDR, TEST_SSID_IE, freq, signal, tsf, capability, false, null,
                NL80211_CMD_GET_WIPHY);

        when(mNl80211Proxy.sendMessageAndReceiveResponse(TEST_NL80211_REQUEST_GET_SCAN))
                .thenReturn(new Nl80211Response(unexpectedMsg));

        List<NativeScanResult> results = mNl80211Utils.getScanResults(TEST_IF_NAME);

        assertNotNull(results);
        assertTrue(results.isEmpty()); // Unexpected command should be ignored
    }

    @Test
    public void testGetScanResults_malformedResponse() {
        // Create a malformed message missing the BSSID
        int freq = 2412;
        int signal = -50;
        long tsf = 123456789L;
        short capability = 0x1000;

        GenericNetlinkMsg malformedMsg = createTestScanResultNetlinkMessage(
                TEST_MAC_ADDR, TEST_SSID_IE, freq, signal, tsf, capability, false, null,
                NL80211_CMD_NEW_SCAN_RESULTS);
        // Remove the BSS attribute which is required for parsing.
        malformedMsg.attributes.remove(NL80211_ATTR_BSS);

        when(mNl80211Proxy.sendMessageAndReceiveResponse(TEST_NL80211_REQUEST_GET_SCAN))
                .thenReturn(new Nl80211Response(malformedMsg));

        List<NativeScanResult> results = mNl80211Utils.getScanResults(TEST_IF_NAME);

        assertNotNull(results);
        assertTrue(results.isEmpty());
    }

    @Test
    public void testInitialize_splitWiphyDumpSupported() {
        setupProtocolFeaturesResponse(NL80211_PROTOCOL_FEATURE_SPLIT_WIPHY_DUMP);
        mNl80211Utils.initialize();
        assertTrue(mNl80211Utils.isSplitWiphyDumpSupported());
    }

    @Test
    public void testInitialize_splitWiphyDumpNotSupported() {
        setupProtocolFeaturesResponse(0);
        mNl80211Utils.initialize();
        assertFalse(mNl80211Utils.isSplitWiphyDumpSupported());
    }

    @Test
    public void testInitialize_getProtocolFeaturesFails() {
        when(mNl80211Proxy.sendMessageAndReceiveResponse(
                TEST_NL80211_REQUEST_GET_PROTOCOL_FEATURES)).thenReturn(null);
        mNl80211Utils.initialize();
        assertFalse(mNl80211Utils.isSplitWiphyDumpSupported());
    }

    @Test
    public void testGetWiphyIndex_success() {
        GenericNetlinkMsg msg = new GenericNetlinkMsg(NL80211_CMD_NEW_WIPHY, (short) 0,
                (short) 0, 0);
        msg.addAttribute(new StructNlAttr(NL80211_ATTR_WIPHY, TEST_WIPHY_INDEX));
        when(mNl80211Proxy.sendMessageAndReceiveResponse(TEST_NL80211_REQUEST_GET_WIPHY))
                .thenReturn(new Nl80211Response(msg));

        assertEquals(TEST_WIPHY_INDEX, mNl80211Utils.getWiphyIndex(TEST_IF_NAME));
    }

    @Test
    public void testGetWiphyIndex_socketException() throws Exception {
        when(NetworkInterface.getByName(TEST_IF_NAME)).thenThrow(new SocketException());
        assertEquals(-1, mNl80211Utils.getWiphyIndex(TEST_IF_NAME));
    }

    @Test
    public void testGetWiphyIndex_noNetworkInterface() throws Exception {
        when(NetworkInterface.getByName(TEST_IF_NAME)).thenReturn(null);
        assertEquals(-1, mNl80211Utils.getWiphyIndex(TEST_IF_NAME));
    }

    @Test
    public void testGetWiphyIndex_noResponse() {
        when(mNl80211Proxy.sendMessageAndReceiveResponse(TEST_NL80211_REQUEST_GET_WIPHY))
                .thenReturn(null);
        assertEquals(-1, mNl80211Utils.getWiphyIndex(TEST_IF_NAME));
    }

    @Test
    public void testGetWiphyIndex_emptyResponse() {
        when(mNl80211Proxy.sendMessageAndReceiveResponse(TEST_NL80211_REQUEST_GET_WIPHY))
                .thenReturn(new Nl80211Response());
        assertEquals(-1, mNl80211Utils.getWiphyIndex(TEST_IF_NAME));
    }

    private GenericNetlinkMsg createBasicWiphyInfoMsg() {
        GenericNetlinkMsg msg = new GenericNetlinkMsg(NL80211_CMD_NEW_WIPHY, (short) 0,
                (short) 0, 0);
        msg.addAttribute(new StructNlAttr(NL80211_ATTR_FEATURE_FLAGS, 0));
        msg.addAttribute(new StructNlAttr(NL80211_ATTR_MAX_NUM_SCAN_SSIDS, (byte) 16));
        msg.addAttribute(
                new StructNlAttr(NL80211_ATTR_MAX_NUM_SCHED_SCAN_SSIDS, (byte) 16));
        msg.addAttribute(new StructNlAttr(NL80211_ATTR_MAX_MATCH_SETS, (byte) 8));
        msg.addAttribute(new StructNlAttr(NL80211_ATTR_MAX_NUM_AKM_SUITES, (short) 1));
        msg.addAttribute(new StructNlAttr(NL80211_ATTR_MAX_NUM_SCHED_SCAN_PLANS, 2));
        msg.addAttribute(new StructNlAttr(NL80211_ATTR_MAX_SCAN_PLAN_INTERVAL, 10));
        msg.addAttribute(new StructNlAttr(NL80211_ATTR_MAX_SCAN_PLAN_ITERATIONS, 3));
        msg.addAttribute(new StructNlAttr(NL80211_ATTR_EXT_FEATURES, new byte[1]));
        return msg;
    }

    private StructNlAttr createWiphyBandsAttribute() {
        // --- Level 3 (Innermost): The actual frequency values ---
        StructNlAttr innerFreq2412 = new StructNlAttr(NL80211_FREQUENCY_ATTR_FREQ, 2412);
        StructNlAttr innerFreq5180 = new StructNlAttr(NL80211_FREQUENCY_ATTR_FREQ, 5180);

        // --- Level 2 (Frequency List Items): Pack L3 into L2 ---
        // Pack innerFreq2412 into a byte[] payload for the (unnamed) attribute '1'
        ByteBuffer freq2412Payload = ByteBuffer.allocate(innerFreq2412.getAlignedLength());
        innerFreq2412.pack(freq2412Payload);
        StructNlAttr freq2412 = new StructNlAttr((short) 1, freq2412Payload.array());

        // Pack innerFreq5180 into a byte[] payload for the (unnamed) attribute '1'
        ByteBuffer freq5180Payload = ByteBuffer.allocate(innerFreq5180.getAlignedLength());
        innerFreq5180.pack(freq5180Payload);
        StructNlAttr freq5180 = new StructNlAttr((short) 1, freq5180Payload.array());

        // --- Level 1 (Band Containers): Pack L2 into L1 ---
        // Create the HT/VHT attributes (these are already non-nested)
        StructNlAttr htCapa = new StructNlAttr(NL80211_BAND_ATTR_HT_CAPA, new byte[0]);
        StructNlAttr vhtCapa = new StructNlAttr(NL80211_BAND_ATTR_VHT_CAPA, new byte[0]);

        // Pack freq2412 (as byte[]) and htCapa (as byte[]) into the freqs2g payload
        ByteBuffer freqs2gPayload = ByteBuffer.allocate(freq2412.getAlignedLength());
        freq2412.pack(freqs2gPayload);
        StructNlAttr freqs2g = new StructNlAttr(
                NL80211_BAND_ATTR_FREQS, freqs2gPayload.array());

        // Pack freq5180 (as byte[]) and vhtCapa (as byte[]) into the freqs5g payload
        ByteBuffer freqs5gPayload = ByteBuffer.allocate(freq5180.getAlignedLength());
        freq5180.pack(freqs5gPayload);
        StructNlAttr freqs5g = new StructNlAttr(
                NL80211_BAND_ATTR_FREQS, freqs5gPayload.array());

        // Pack freqs2g and htCapa into the band2g payload
        ByteBuffer band2gPayload = ByteBuffer.allocate(
                freqs2g.getAlignedLength() + htCapa.getAlignedLength());
        freqs2g.pack(band2gPayload);
        htCapa.pack(band2gPayload);
        StructNlAttr band2g = new StructNlAttr(NL80211_BAND_2GHZ, band2gPayload.array());

        // Pack freqs5g and vhtCapa into the band5g payload
        ByteBuffer band5gPayload = ByteBuffer.allocate(
                freqs5g.getAlignedLength() + vhtCapa.getAlignedLength());
        freqs5g.pack(band5gPayload);
        vhtCapa.pack(band5gPayload);
        StructNlAttr band5g = new StructNlAttr(NL80211_BAND_5GHZ, band5gPayload.array());

        // --- Level 0 (Outermost): Pack L1 into L0 ---
        ByteBuffer payload = ByteBuffer.allocate(
                band2g.getAlignedLength() + band5g.getAlignedLength());
        band2g.pack(payload);
        band5g.pack(payload);

        // Note: NL80211_ATTR_WIPHY_BANDS does not have NLA_F_NESTED in the attribute flag
        // in production. Thus, we must add the payload as a raw byte[] instead of using
        // the nested constructor (which automatically adds NLA_F_NESTED).
        return new StructNlAttr(NL80211_ATTR_WIPHY_BANDS, payload.array());
    }

    @Test
    public void testParseWiphyInfo_success() {
        GenericNetlinkMsg msg = createBasicWiphyInfoMsg();
        msg.addAttribute(createWiphyBandsAttribute());

        Nl80211Utils.WiphyInfo info = mNl80211Utils.parseWiphyInfo(List.of(msg));

        assertNotNull(info);
        assertFalse(info.bandInfo.band2g.isEmpty());
        assertFalse(info.bandInfo.band5g.isEmpty());
        assertTrue(info.bandInfo.is80211nSupported);
        assertTrue(info.bandInfo.is80211acSupported);
        assertEquals(16, info.scanCapabilities.maxNumScanSsids);
    }

    @Test
    public void testParseWiphyInfo_splitDump_success() {
        // Split the response payload into two different msgs.
        GenericNetlinkMsg msg1 = new GenericNetlinkMsg(NL80211_CMD_NEW_WIPHY, (short) 0,
                (short) 0, 0);
        msg1.addAttribute(createWiphyBandsAttribute());
        msg1.addAttribute(new StructNlAttr(NL80211_ATTR_FEATURE_FLAGS, 0));

        GenericNetlinkMsg msg2 = new GenericNetlinkMsg(NL80211_CMD_NEW_WIPHY, (short) 0,
                (short) 0, 0);
        msg2.addAttribute(new StructNlAttr(NL80211_ATTR_MAX_NUM_SCAN_SSIDS, (byte) 16));
        msg2.addAttribute(new StructNlAttr(NL80211_ATTR_MAX_NUM_SCHED_SCAN_SSIDS, (byte) 16));
        msg2.addAttribute(new StructNlAttr(NL80211_ATTR_MAX_MATCH_SETS, (byte) 8));
        msg2.addAttribute(new StructNlAttr(NL80211_ATTR_MAX_NUM_AKM_SUITES, (short) 1));
        msg2.addAttribute(new StructNlAttr(NL80211_ATTR_MAX_NUM_SCHED_SCAN_PLANS, 2));
        msg2.addAttribute(new StructNlAttr(NL80211_ATTR_MAX_SCAN_PLAN_INTERVAL, 10));
        msg2.addAttribute(new StructNlAttr(NL80211_ATTR_MAX_SCAN_PLAN_ITERATIONS, 3));
        msg2.addAttribute(new StructNlAttr(NL80211_ATTR_EXT_FEATURES, new byte[1]));

        Nl80211Utils.WiphyInfo info = mNl80211Utils.parseWiphyInfo(List.of(msg1, msg2));

        assertNotNull(info);
        // Verify information from msg1
        assertFalse(info.bandInfo.band2g.isEmpty());
        assertFalse(info.bandInfo.band5g.isEmpty());
        // Verify information from msg2
        assertTrue(info.bandInfo.is80211nSupported);
        assertTrue(info.bandInfo.is80211acSupported);
        assertEquals(16, info.scanCapabilities.maxNumScanSsids);
    }

    @Test
    public void testParseWiphyInfo_missingBands() {
        GenericNetlinkMsg msg = createBasicWiphyInfoMsg();
        assertNull(mNl80211Utils.parseWiphyInfo(List.of(msg)));
    }

    @Test
    public void testParseWiphyInfo_missingScanCaps() {
        GenericNetlinkMsg msg = new GenericNetlinkMsg(NL80211_CMD_NEW_WIPHY, (short) 0,
                (short) 0, 0);
        msg.addAttribute(createWiphyBandsAttribute());
        msg.addAttribute(new StructNlAttr(NL80211_ATTR_FEATURE_FLAGS, 0));
        assertNull(mNl80211Utils.parseWiphyInfo(List.of(msg)));
    }

    @Test
    public void testParseWiphyInfo_missingFeatureFlags() {
        GenericNetlinkMsg msg = new GenericNetlinkMsg(NL80211_CMD_NEW_WIPHY, (short) 0,
                (short) 0, 0);
        msg.addAttribute(createWiphyBandsAttribute());
        msg.addAttribute(new StructNlAttr(NL80211_ATTR_MAX_NUM_SCAN_SSIDS, (byte) 16));
        msg.addAttribute(new StructNlAttr(NL80211_ATTR_MAX_NUM_SCHED_SCAN_SSIDS, (byte) 16));
        msg.addAttribute(new StructNlAttr(NL80211_ATTR_MAX_MATCH_SETS, (byte) 8));
        assertNull(mNl80211Utils.parseWiphyInfo(List.of(msg)));
    }

    @Test
    public void testGetWiphyInfo_noSplitDump_success() {
        mNl80211Utils.initialize(); // This will set split dump to false by default
        GenericNetlinkMsg msg = createBasicWiphyInfoMsg();
        msg.addAttribute(new StructNlAttr(NL80211_ATTR_WIPHY, TEST_WIPHY_INDEX));
        msg.addAttribute(createWiphyBandsAttribute());
        when(mNl80211Proxy.sendMessageAndReceiveResponse(TEST_NL80211_REQUEST_GET_WIPHY))
                .thenReturn(new Nl80211Response(msg));

        Nl80211Utils.WiphyInfo info = mNl80211Utils.getWiphyInfo(TEST_WIPHY_INDEX);
        assertNotNull(info);
    }

    @Test
    public void testGetWiphyInfo_noSplitDump_failure() {
        mNl80211Utils.initialize();
        when(mNl80211Proxy.sendMessageAndReceiveResponse(TEST_NL80211_REQUEST_GET_WIPHY))
                .thenReturn(null);
        assertNull(mNl80211Utils.getWiphyInfo(TEST_WIPHY_INDEX));
    }

    @Test
    public void testGetWiphyInfo_splitDump_success() {
        setupProtocolFeaturesResponse(NL80211_PROTOCOL_FEATURE_SPLIT_WIPHY_DUMP);
        mNl80211Utils.initialize();

        GenericNetlinkMsg msg1 = new GenericNetlinkMsg(NL80211_CMD_NEW_WIPHY, (short) 0,
                (short) 0, 0);
        msg1.addAttribute(createWiphyBandsAttribute());
        msg1.addAttribute(new StructNlAttr(NL80211_ATTR_WIPHY, TEST_WIPHY_INDEX));
        msg1.addAttribute(new StructNlAttr(NL80211_ATTR_FEATURE_FLAGS, 0));

        GenericNetlinkMsg msg2 = new GenericNetlinkMsg(NL80211_CMD_NEW_WIPHY, (short) 0,
                (short) 0, 0);
        msg2.addAttribute(new StructNlAttr(NL80211_ATTR_WIPHY, TEST_WIPHY_INDEX));
        msg2.addAttribute(new StructNlAttr(NL80211_ATTR_MAX_NUM_SCAN_SSIDS, (byte) 16));
        msg2.addAttribute(new StructNlAttr(NL80211_ATTR_MAX_NUM_SCHED_SCAN_SSIDS, (byte) 16));
        msg2.addAttribute(new StructNlAttr(NL80211_ATTR_MAX_MATCH_SETS, (byte) 8));
        msg2.addAttribute(new StructNlAttr(NL80211_ATTR_MAX_NUM_AKM_SUITES, (short) 1));
        msg2.addAttribute(new StructNlAttr(NL80211_ATTR_MAX_NUM_SCHED_SCAN_PLANS, 2));
        msg2.addAttribute(new StructNlAttr(NL80211_ATTR_MAX_SCAN_PLAN_INTERVAL, 10));
        msg2.addAttribute(new StructNlAttr(NL80211_ATTR_MAX_SCAN_PLAN_ITERATIONS, 3));
        msg2.addAttribute(new StructNlAttr(NL80211_ATTR_EXT_FEATURES, new byte[1]));

        when(mNl80211Proxy.sendMessageAndReceiveResponse(TEST_NL80211_REQUEST_GET_WIPHY))
                .thenReturn(new Nl80211Response(msg1, msg2));

        Nl80211Utils.WiphyInfo info = mNl80211Utils.getWiphyInfo(TEST_WIPHY_INDEX);
        assertNotNull(info);
        assertFalse(info.bandInfo.band2g.isEmpty());
        assertEquals(16, info.scanCapabilities.maxNumScanSsids);
    }

    @Test
    public void testGetWiphyInfoCachesResult() {
        mNl80211Utils.initialize();
        GenericNetlinkMsg msg = createBasicWiphyInfoMsg();
        msg.addAttribute(new StructNlAttr(NL80211_ATTR_WIPHY, TEST_WIPHY_INDEX));
        msg.addAttribute(createWiphyBandsAttribute());
        when(mNl80211Proxy.sendMessageAndReceiveResponse(TEST_NL80211_REQUEST_GET_WIPHY))
                .thenReturn(new Nl80211Response(msg));

        Nl80211Utils.WiphyInfo info1 = mNl80211Utils.getWiphyInfo(TEST_WIPHY_INDEX);
        Nl80211Utils.WiphyInfo info2 = mNl80211Utils.getWiphyInfo(TEST_WIPHY_INDEX);

        verify(mNl80211Proxy, times(1))
                .sendMessageAndReceiveResponse(TEST_NL80211_REQUEST_GET_WIPHY);
        assertEquals(info1, info2);
    }

    @Test
    public void testClearWiphyInfoCaches() {
        mNl80211Utils.initialize();
        GenericNetlinkMsg msg = createBasicWiphyInfoMsg();
        msg.addAttribute(new StructNlAttr(NL80211_ATTR_WIPHY, TEST_WIPHY_INDEX));
        msg.addAttribute(createWiphyBandsAttribute());
        when(mNl80211Proxy.sendMessageAndReceiveResponse(TEST_NL80211_REQUEST_GET_WIPHY))
                .thenReturn(new Nl80211Response(msg));

        mNl80211Utils.getWiphyInfo(TEST_WIPHY_INDEX);
        verify(mNl80211Proxy, times(1))
                .sendMessageAndReceiveResponse(TEST_NL80211_REQUEST_GET_WIPHY);

        mNl80211Utils.clearWiphyInfoCaches();

        mNl80211Utils.getWiphyInfo(TEST_WIPHY_INDEX);
        verify(mNl80211Proxy, times(2))
                .sendMessageAndReceiveResponse(TEST_NL80211_REQUEST_GET_WIPHY);
    }

    @Test
    public void testGetInterfaces_success() {
        GenericNetlinkMsg msg = new GenericNetlinkMsg(NL80211_CMD_NEW_INTERFACE, (short) 0,
                (short) 0, 0);
        msg.addAttribute(new StructNlAttr(NL80211_ATTR_WIPHY, TEST_WIPHY_INDEX));
        msg.addAttribute(new StructNlAttr(NL80211_ATTR_IFINDEX, TEST_IF_INDEX));
        msg.addAttribute(new StructNlAttr(NL80211_ATTR_IFNAME, TEST_IF_NAME));
        msg.addAttribute(new StructNlAttr(NL80211_ATTR_MAC, TEST_MAC_ADDR));
        when(mNl80211Proxy.sendMessageAndReceiveResponse(TEST_NL80211_REQUEST_GET_INTERFACE))
                .thenReturn(new Nl80211Response(msg));

        List<Nl80211Utils.InterfaceInfo> interfaces = mNl80211Utils.getInterfaces(TEST_WIPHY_INDEX);

        assertNotNull(interfaces);
        assertEquals(1, interfaces.size());
        assertEquals(TEST_IF_INDEX, interfaces.get(0).ifIndex);
        assertEquals(TEST_WIPHY_INDEX, interfaces.get(0).wiphyIndex);
        assertEquals(TEST_IF_NAME, interfaces.get(0).name);
        assertEquals(TEST_MAC_ADDR, interfaces.get(0).macAddress);
    }

    @Test
    public void testGetInterfaces_wildcardWiphyIndex_success() {
        GenericNetlinkMsg requestAllInterfaces = new GenericNetlinkMsg(
                NL80211_CMD_GET_INTERFACE, (short) 0, (short) 0, 0);
        when(mNl80211Proxy.createNl80211Request(eq(NL80211_CMD_GET_INTERFACE),
                anyShort())).thenReturn(requestAllInterfaces);

        GenericNetlinkMsg msg1 = new GenericNetlinkMsg(NL80211_CMD_NEW_INTERFACE, (short) 0,
                (short) 0, 0);
        msg1.addAttribute(new StructNlAttr(NL80211_ATTR_WIPHY, 0));
        msg1.addAttribute(new StructNlAttr(NL80211_ATTR_IFINDEX, 1));
        msg1.addAttribute(new StructNlAttr(NL80211_ATTR_IFNAME, "wlan0"));
        msg1.addAttribute(new StructNlAttr(NL80211_ATTR_MAC, new byte[6]));

        GenericNetlinkMsg msg2 = new GenericNetlinkMsg(NL80211_CMD_NEW_INTERFACE,
                (short) 0, (short) 0, 0);
        msg2.addAttribute(new StructNlAttr(NL80211_ATTR_WIPHY, 1));
        msg2.addAttribute(new StructNlAttr(NL80211_ATTR_IFINDEX, 2));
        msg2.addAttribute(new StructNlAttr(NL80211_ATTR_IFNAME, "wlan1"));
        msg2.addAttribute(new StructNlAttr(NL80211_ATTR_MAC, new byte[6]));

        when(mNl80211Proxy.sendMessageAndReceiveResponse(requestAllInterfaces))
                .thenReturn(new Nl80211Response(msg1, msg2));

        List<Nl80211Utils.InterfaceInfo> interfaces = mNl80211Utils.getInterfaces(-1);

        assertNotNull(interfaces);
        assertEquals(2, interfaces.size());
        assertEquals(1, interfaces.get(0).ifIndex);
        assertEquals(0, interfaces.get(0).wiphyIndex);
        assertEquals("wlan0", interfaces.get(0).name);
        assertEquals(2, interfaces.get(1).ifIndex);
        assertEquals(1, interfaces.get(1).wiphyIndex);
        assertEquals("wlan1", interfaces.get(1).name);
    }

    @Test
    public void testGetInterfaces_failure() {
        when(mNl80211Proxy.sendMessageAndReceiveResponse(TEST_NL80211_REQUEST_GET_INTERFACE))
                .thenReturn(null);
        assertNull(mNl80211Utils.getInterfaces(TEST_WIPHY_INDEX));
    }

    @Test
    public void testGetInterfaces_malformedResponse() {
        GenericNetlinkMsg msg = new GenericNetlinkMsg(NL80211_CMD_NEW_INTERFACE,
                (short) 0, (short) 0, 0);
        // Missing IFNAME
        msg.addAttribute(new StructNlAttr(NL80211_ATTR_IFINDEX, TEST_IF_INDEX));
        msg.addAttribute(new StructNlAttr(NL80211_ATTR_MAC, new byte[6]));
        when(mNl80211Proxy.sendMessageAndReceiveResponse(TEST_NL80211_REQUEST_GET_INTERFACE))
                .thenReturn(new Nl80211Response(msg));

        List<Nl80211Utils.InterfaceInfo> interfaces = mNl80211Utils.getInterfaces(TEST_WIPHY_INDEX);

        assertNotNull(interfaces);
        assertTrue(interfaces.isEmpty());
    }
}
