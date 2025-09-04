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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.android.net.module.util.netlink.StructNlAttr;
import com.android.net.module.util.netlink.StructNlMsgHdr;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;

/**
 * Unit tests for {@link Nl80211Native}.
 */
public class Nl80211NativeTest {
    private Nl80211Native mDut;

    @Mock Nl80211Proxy mNl80211Proxy;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        mDut = new Nl80211Native(mNl80211Proxy);
        when(mNl80211Proxy.initialize()).thenReturn(true);
        assertTrue(mDut.initialize());
        assertTrue(mDut.isInitialized());
        when(mNl80211Proxy.createNl80211Request(
                        NetlinkConstants.NL80211_CMD_GET_INTERFACE, StructNlMsgHdr.NLM_F_DUMP))
                .thenReturn(Nl80211TestUtils.createTestMessage());
    }

    /** Test that {@link Nl80211Native#getInterfaceNames()} returns the expected value. */
    @Test
    public void testGetInterfaceNames_success_returnsInterfaceNames() {
        GenericNetlinkMsg response = Nl80211TestUtils.createTestMessage();
        response.addAttribute(new StructNlAttr(NetlinkConstants.NL80211_ATTR_IFNAME, "wlan0"));
        when(mNl80211Proxy.sendMessageAndReceiveResponses(any()))
                .thenReturn(List.of(response));
        List<String> interfaceNames = mDut.getInterfaceNames();
        assertEquals(interfaceNames, List.of("wlan0"));
    }

    /** Test that {@link Nl80211Native#getInterfaceNames()} returns null if the response is null. */
    @Test
    public void testGetInterfaceNames_failedToReceiveResponses_returnsNull() {
        when(mNl80211Proxy.sendMessageAndReceiveResponses(any())).thenReturn(null);
        List<String> interfaceNames = mDut.getInterfaceNames();
        assertNull(interfaceNames);
    }

    /**
     * Test that {@link Nl80211Native#getInterfaceNames()} returns null if the request failed to
     * create.
     */
    @Test
    public void testGetInterfaceNames_failedToCreateRequest_returnsNull() {
        when(mNl80211Proxy.createNl80211Request(
                        NetlinkConstants.NL80211_CMD_GET_INTERFACE, StructNlMsgHdr.NLM_F_DUMP))
                .thenReturn(null);
        List<String> interfaceNames = mDut.getInterfaceNames();
        assertNull(interfaceNames);
    }
}
