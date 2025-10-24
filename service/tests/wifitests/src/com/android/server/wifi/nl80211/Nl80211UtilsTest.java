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

import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_PROTOCOL_FEATURE_SPLIT_WIPHY_DUMP;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_ATTR_EXT_FEATURES;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_ATTR_FEATURE_FLAGS;
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
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_CMD_GET_PROTOCOL_FEATURES;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_CMD_GET_WIPHY;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_CMD_NEW_WIPHY;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_FREQUENCY_ATTR_FREQ;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyShort;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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
import java.util.ArrayList;
import java.util.List;

public class Nl80211UtilsTest {

    private static final int TEST_WIPHY_INDEX = 4;
    private static final int TEST_IF_INDEX = 5;
    private static final String TEST_IF_NAME = "wlan0";

    // Test request messages
    private static final GenericNetlinkMsg TEST_NL80211_REQUEST_GET_PROTOCOL_FEATURES =
            new GenericNetlinkMsg(NL80211_CMD_GET_PROTOCOL_FEATURES, (short) 0, (short) 0, 0);
    private static final GenericNetlinkMsg TEST_NL80211_REQUEST_GET_WIPHY =
            new GenericNetlinkMsg(NL80211_CMD_GET_WIPHY, (short) 0, (short) 0, 0);

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
    }

    @After
    public void cleanup() {
        if (mSession != null) {
            mSession.finishMocking();
        }
    }

    private void setupProtocolFeaturesResponse(int features) {
        GenericNetlinkMsg response = new GenericNetlinkMsg(
                NL80211_CMD_GET_PROTOCOL_FEATURES, (short) 0, (short) 0, 0);
        response.addAttribute(
                new StructNlAttr(NL80211_ATTR_PROTOCOL_FEATURES, features));
        when(mNl80211Proxy.sendMessageAndReceiveResponse(
                TEST_NL80211_REQUEST_GET_PROTOCOL_FEATURES)).thenReturn(response);
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
        GenericNetlinkMsg response = new GenericNetlinkMsg(NL80211_CMD_NEW_WIPHY, (short) 0,
                (short) 0, 0);
        response.addAttribute(new StructNlAttr(NL80211_ATTR_WIPHY, TEST_WIPHY_INDEX));
        when(mNl80211Proxy.sendMessageAndReceiveResponses(TEST_NL80211_REQUEST_GET_WIPHY))
                .thenReturn(List.of(response));

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
        when(mNl80211Proxy.sendMessageAndReceiveResponses(TEST_NL80211_REQUEST_GET_WIPHY))
                .thenReturn(null);
        assertEquals(-1, mNl80211Utils.getWiphyIndex(TEST_IF_NAME));
    }

    @Test
    public void testGetWiphyIndex_emptyResponse() {
        when(mNl80211Proxy.sendMessageAndReceiveResponses(TEST_NL80211_REQUEST_GET_WIPHY))
                .thenReturn(new ArrayList<>());
        assertEquals(-1, mNl80211Utils.getWiphyIndex(TEST_IF_NAME));
    }

    private GenericNetlinkMsg createBasicWiphyInfoPacket() {
        GenericNetlinkMsg packet = new GenericNetlinkMsg(NL80211_CMD_NEW_WIPHY, (short) 0,
                (short) 0, 0);
        packet.addAttribute(new StructNlAttr(NL80211_ATTR_FEATURE_FLAGS, 0));
        packet.addAttribute(new StructNlAttr(NL80211_ATTR_MAX_NUM_SCAN_SSIDS, (byte) 16));
        packet.addAttribute(
                new StructNlAttr(NL80211_ATTR_MAX_NUM_SCHED_SCAN_SSIDS, (byte) 16));
        packet.addAttribute(new StructNlAttr(NL80211_ATTR_MAX_MATCH_SETS, (byte) 8));
        packet.addAttribute(new StructNlAttr(NL80211_ATTR_MAX_NUM_AKM_SUITES, (short) 1));
        packet.addAttribute(new StructNlAttr(NL80211_ATTR_MAX_NUM_SCHED_SCAN_PLANS, 2));
        packet.addAttribute(new StructNlAttr(NL80211_ATTR_MAX_SCAN_PLAN_INTERVAL, 10));
        packet.addAttribute(new StructNlAttr(NL80211_ATTR_MAX_SCAN_PLAN_ITERATIONS, 3));
        packet.addAttribute(new StructNlAttr(NL80211_ATTR_EXT_FEATURES, new byte[1]));
        return packet;
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
        GenericNetlinkMsg packet = createBasicWiphyInfoPacket();
        packet.addAttribute(createWiphyBandsAttribute());

        Nl80211Utils.WiphyInfo info = mNl80211Utils.parseWiphyInfo(List.of(packet));

        assertNotNull(info);
        assertFalse(info.bandInfo.band2g.isEmpty());
        assertFalse(info.bandInfo.band5g.isEmpty());
        assertTrue(info.bandInfo.is80211nSupported);
        assertTrue(info.bandInfo.is80211acSupported);
        assertEquals(16, info.scanCapabilities.maxNumScanSsids);
    }

    @Test
    public void testParseWiphyInfo_splitDump_success() {
        // Split the response payload into two different packets.
        GenericNetlinkMsg packet1 = new GenericNetlinkMsg(NL80211_CMD_NEW_WIPHY, (short) 0,
                (short) 0, 0);
        packet1.addAttribute(createWiphyBandsAttribute());
        packet1.addAttribute(new StructNlAttr(NL80211_ATTR_FEATURE_FLAGS, 0));

        GenericNetlinkMsg packet2 = new GenericNetlinkMsg(NL80211_CMD_NEW_WIPHY, (short) 0,
                (short) 0, 0);
        packet2.addAttribute(new StructNlAttr(NL80211_ATTR_MAX_NUM_SCAN_SSIDS, (byte) 16));
        packet2.addAttribute(new StructNlAttr(NL80211_ATTR_MAX_NUM_SCHED_SCAN_SSIDS, (byte) 16));
        packet2.addAttribute(new StructNlAttr(NL80211_ATTR_MAX_MATCH_SETS, (byte) 8));
        packet2.addAttribute(new StructNlAttr(NL80211_ATTR_MAX_NUM_AKM_SUITES, (short) 1));
        packet2.addAttribute(new StructNlAttr(NL80211_ATTR_MAX_NUM_SCHED_SCAN_PLANS, 2));
        packet2.addAttribute(new StructNlAttr(NL80211_ATTR_MAX_SCAN_PLAN_INTERVAL, 10));
        packet2.addAttribute(new StructNlAttr(NL80211_ATTR_MAX_SCAN_PLAN_ITERATIONS, 3));
        packet2.addAttribute(new StructNlAttr(NL80211_ATTR_EXT_FEATURES, new byte[1]));

        Nl80211Utils.WiphyInfo info = mNl80211Utils.parseWiphyInfo(List.of(packet1, packet2));

        assertNotNull(info);
        // Verify information from packet1
        assertFalse(info.bandInfo.band2g.isEmpty());
        assertFalse(info.bandInfo.band5g.isEmpty());
        // Verify information from packet2
        assertTrue(info.bandInfo.is80211nSupported);
        assertTrue(info.bandInfo.is80211acSupported);
        assertEquals(16, info.scanCapabilities.maxNumScanSsids);
    }

    @Test
    public void testParseWiphyInfo_missingBands() {
        GenericNetlinkMsg packet = createBasicWiphyInfoPacket();
        assertNull(mNl80211Utils.parseWiphyInfo(List.of(packet)));
    }

    @Test
    public void testParseWiphyInfo_missingScanCaps() {
        GenericNetlinkMsg packet = new GenericNetlinkMsg(NL80211_CMD_NEW_WIPHY, (short) 0,
                (short) 0, 0);
        packet.addAttribute(createWiphyBandsAttribute());
        packet.addAttribute(new StructNlAttr(NL80211_ATTR_FEATURE_FLAGS, 0));
        assertNull(mNl80211Utils.parseWiphyInfo(List.of(packet)));
    }

    @Test
    public void testParseWiphyInfo_missingFeatureFlags() {
        GenericNetlinkMsg packet = new GenericNetlinkMsg(NL80211_CMD_NEW_WIPHY, (short) 0,
                (short) 0, 0);
        packet.addAttribute(createWiphyBandsAttribute());
        packet.addAttribute(new StructNlAttr(NL80211_ATTR_MAX_NUM_SCAN_SSIDS, (byte) 16));
        packet.addAttribute(new StructNlAttr(NL80211_ATTR_MAX_NUM_SCHED_SCAN_SSIDS, (byte) 16));
        packet.addAttribute(new StructNlAttr(NL80211_ATTR_MAX_MATCH_SETS, (byte) 8));
        assertNull(mNl80211Utils.parseWiphyInfo(List.of(packet)));
    }

    @Test
    public void testGetWiphyInfo_noSplitDump_success() {
        mNl80211Utils.initialize(); // This will set split dump to false by default
        GenericNetlinkMsg response = createBasicWiphyInfoPacket();
        response.addAttribute(new StructNlAttr(NL80211_ATTR_WIPHY, TEST_WIPHY_INDEX));
        response.addAttribute(createWiphyBandsAttribute());
        when(mNl80211Proxy.sendMessageAndReceiveResponse(TEST_NL80211_REQUEST_GET_WIPHY))
                .thenReturn(response);

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

        GenericNetlinkMsg response1 = new GenericNetlinkMsg(NL80211_CMD_NEW_WIPHY, (short) 0,
                (short) 0, 0);
        response1.addAttribute(createWiphyBandsAttribute());
        response1.addAttribute(new StructNlAttr(NL80211_ATTR_WIPHY, TEST_WIPHY_INDEX));
        response1.addAttribute(new StructNlAttr(NL80211_ATTR_FEATURE_FLAGS, 0));

        GenericNetlinkMsg response2 = new GenericNetlinkMsg(NL80211_CMD_NEW_WIPHY, (short) 0,
                (short) 0, 0);
        response2.addAttribute(new StructNlAttr(NL80211_ATTR_WIPHY, TEST_WIPHY_INDEX));
        response2.addAttribute(new StructNlAttr(NL80211_ATTR_MAX_NUM_SCAN_SSIDS, (byte) 16));
        response2.addAttribute(new StructNlAttr(NL80211_ATTR_MAX_NUM_SCHED_SCAN_SSIDS, (byte) 16));
        response2.addAttribute(new StructNlAttr(NL80211_ATTR_MAX_MATCH_SETS, (byte) 8));
        response2.addAttribute(new StructNlAttr(NL80211_ATTR_MAX_NUM_AKM_SUITES, (short) 1));
        response2.addAttribute(new StructNlAttr(NL80211_ATTR_MAX_NUM_SCHED_SCAN_PLANS, 2));
        response2.addAttribute(new StructNlAttr(NL80211_ATTR_MAX_SCAN_PLAN_INTERVAL, 10));
        response2.addAttribute(new StructNlAttr(NL80211_ATTR_MAX_SCAN_PLAN_ITERATIONS, 3));
        response2.addAttribute(new StructNlAttr(NL80211_ATTR_EXT_FEATURES, new byte[1]));

        when(mNl80211Proxy.sendMessageAndReceiveResponses(TEST_NL80211_REQUEST_GET_WIPHY))
                .thenReturn(List.of(response1, response2));

        Nl80211Utils.WiphyInfo info = mNl80211Utils.getWiphyInfo(TEST_WIPHY_INDEX);
        assertNotNull(info);
        assertFalse(info.bandInfo.band2g.isEmpty());
        assertEquals(16, info.scanCapabilities.maxNumScanSsids);
    }

    @Test
    public void testGetWiphyInfo_splitDump_failure_nullResponse() {
        setupProtocolFeaturesResponse(NL80211_PROTOCOL_FEATURE_SPLIT_WIPHY_DUMP);
        mNl80211Utils.initialize();
        when(mNl80211Proxy.sendMessageAndReceiveResponses(TEST_NL80211_REQUEST_GET_WIPHY))
                .thenReturn(null);
        assertNull(mNl80211Utils.getWiphyInfo(TEST_WIPHY_INDEX));
    }
}
