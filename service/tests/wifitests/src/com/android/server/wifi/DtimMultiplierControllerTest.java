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

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.net.LinkAddress;
import android.net.LinkProperties;

import androidx.test.filters.SmallTest;

import com.android.wifi.resources.R;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.util.ArrayList;
import java.util.List;

/**
 * Unit tests for {@link com.android.server.wifi.DtimMultiplierController}.
 */
@SmallTest
public class DtimMultiplierControllerTest extends WifiBaseTest{
    private static final String INTERFACE_BAE = "wlan0";
    static final int MULTIPLIER_IPV6_ONLY = 2;
    static final int MULTIPLIER_IPV4_ONLY = 9;
    static final int MULTIPLIER_IPV4_IPV6 = 2;
    static final int MULTIPLIER_MULTICAST_LOCK_ENABLED = 1;
    @Mock
    WifiNative mWifiNative;
    @Mock
    Context mContext;
    MockResources mResources;
    private DtimMultiplierController mController;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        mResources = new MockResources();
        mResources.setBoolean(R.bool.config_wifiDtimMultiplierConfigEnabled, true);
        mResources.setInteger(R.integer.config_wifiDtimMultiplierIpv6Only, MULTIPLIER_IPV6_ONLY);
        mResources.setInteger(R.integer.config_wifiDtimMultiplierIpv4Only, MULTIPLIER_IPV4_ONLY);
        mResources.setInteger(R.integer.config_wifiDtimMultiplierIpv6Ipv4, MULTIPLIER_IPV4_IPV6);
        mResources.setInteger(R.integer.config_wifiDtimMultiplierMulticastLockEnabled,
                MULTIPLIER_MULTICAST_LOCK_ENABLED);
        when(mContext.getResources()).thenReturn(mResources);
        mController = new DtimMultiplierController(mContext, INTERFACE_BAE, mWifiNative);
    }

    @Test
    public void testIPv4AddrOnly() throws Exception {
        mController.updateLinkProperties(generateLinkProperties(generateLinkAddressIPv4()));

        verify(mWifiNative, times(1)).setDtimMultiplier(
                INTERFACE_BAE, MULTIPLIER_IPV4_ONLY);
    }

    private LinkAddress generateLinkAddressIPv4() throws Exception {
        Inet4Address ipv4Addr = (Inet4Address) Inet4Address.getByName("192.168.1.1");
        return new LinkAddress(ipv4Addr, 4);
    }

    private LinkProperties generateLinkProperties(LinkAddress linkAddr) {
        List<LinkAddress> addrList = new ArrayList<>();
        addrList.add(linkAddr);
        LinkProperties linkProperties = new LinkProperties();
        linkProperties.setLinkAddresses(addrList);
        return linkProperties;
    }

    private LinkAddress generateLinkAddressIPv6() throws Exception {
        byte[] addr = {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1};
        Inet6Address ipv6Addr = Inet6Address.getByAddress("localhost", addr, 6);
        return new LinkAddress(ipv6Addr, 4);
    }

    @Test
    public void testMulticastLockEnabledThenDisabled() throws Exception {
        mController.updateLinkProperties(generateLinkProperties(generateLinkAddressIPv6()));
        verify(mWifiNative, times(1)).setDtimMultiplier(
                INTERFACE_BAE, MULTIPLIER_IPV6_ONLY);

        mController.setMulticastLock(true);
        verify(mWifiNative, times(1)).setDtimMultiplier(
                INTERFACE_BAE, MULTIPLIER_MULTICAST_LOCK_ENABLED);

        mController.setMulticastLock(false);
        verify(mWifiNative, times(2)).setDtimMultiplier(
                INTERFACE_BAE, MULTIPLIER_IPV6_ONLY);
    }

    @Test
    public void testIPv4OnlyThenIPv4IPv6Network() throws Exception {
        mController.updateLinkProperties(generateLinkProperties(generateLinkAddressIPv4()));
        verify(mWifiNative, times(1)).setDtimMultiplier(
                INTERFACE_BAE, MULTIPLIER_IPV4_ONLY);

        mController.reset();
        mController.updateLinkProperties(generateLinkProperties(
                generateLinkAddressIPv4(), generateLinkAddressIPv6()));
        verify(mWifiNative, times(1)).setDtimMultiplier(
                INTERFACE_BAE, MULTIPLIER_IPV4_IPV6);
    }

    private LinkProperties generateLinkProperties(LinkAddress linkAddr1, LinkAddress linkAddr2) {
        List<LinkAddress> addrList = new ArrayList<>();
        addrList.add(linkAddr1);
        addrList.add(linkAddr2);
        LinkProperties linkProperties = new LinkProperties();
        linkProperties.setLinkAddresses(addrList);
        return linkProperties;
    }
}
