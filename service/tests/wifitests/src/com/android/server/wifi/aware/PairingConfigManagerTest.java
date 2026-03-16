/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.server.wifi.aware;

import static com.android.server.wifi.aware.PairingConfigManager.NIR;
import static com.android.server.wifi.aware.PairingConfigManager.TAG_SIZE_IN_BYTE;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.net.MacAddress;
import android.net.wifi.aware.Characteristics;
import android.net.wifi.util.Environment;

import com.android.server.wifi.DeviceConfigFacade;
import com.android.server.wifi.WifiBaseTest;
import com.android.server.wifi.WifiConfigManager;
import com.android.server.wifi.WifiConfigStore;
import com.android.server.wifi.WifiInjector;
import com.android.wifi.flags.FeatureFlags;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;



/**
 * Unit test harness for PairingConfigManager.
 */
public class PairingConfigManagerTest extends WifiBaseTest {
    private PairingConfigManager mPairingConfigManager;
    private final String mPackageName = "some.package";
    private final String mPackageName1 = "another.package";
    private final String mAlias = "alias";
    private final byte[] mNouce = "nounce".getBytes();
    private final String mMac = "fa:45:23:23:12:12";
    @Mock private WifiInjector mWifiInjector;
    @Mock private WifiConfigStore mWifiConfigStore;
    @Mock private WifiConfigManager mWifiConfigManager;
    @Mock private DeviceConfigFacade mDeviceConfigFacade;
    @Mock private FeatureFlags mFeatureFlags;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        when(mWifiInjector.getWifiConfigStore()).thenReturn(mWifiConfigStore);
        when(mWifiInjector.getWifiConfigManager()).thenReturn(mWifiConfigManager);
        when(mWifiInjector.getDeviceConfigFacade()).thenReturn(mDeviceConfigFacade);
        when(mDeviceConfigFacade.getFeatureFlags()).thenReturn(mFeatureFlags);
        when(mFeatureFlags.multiUserWifiEnhancement()).thenReturn(true);
        mPairingConfigManager = new PairingConfigManager(mWifiInjector);
        if (Environment.isSdkAtLeastC()) {
            verify(mWifiConfigStore).registerStoreData(any());
        }
    }

    /**
     * Test get new NIK for the App
     */
    @Test
    public void testCallingPackageNik() {
        byte[] nik = mPairingConfigManager.getNikForCallingPackage(mPackageName);
        assertFalse(Arrays.equals(nik,
                mPairingConfigManager.getNikForCallingPackage(mPackageName1)));
        mPairingConfigManager.removePackage(mPackageName);
        assertFalse(Arrays.equals(nik,
                mPairingConfigManager.getNikForCallingPackage(mPackageName)));
        if (Environment.isSdkAtLeastC()) {
            verify(mWifiConfigManager, times(4)).saveToStore();
        } else {
            verify(mWifiConfigManager, never()).saveToStore();
        }
    }

    /**
     * Test add paired device and match
     */
    @Test
    public void testAddPairedPeerDevice() {
        byte[] localNik = mPairingConfigManager.getNikForCallingPackage(mPackageName);
        byte[] peerNik = mPairingConfigManager.getNikForCallingPackage(mPackageName1);
        PairingConfigManager.PairingSecurityAssociationInfo pairingInfo =
                new PairingConfigManager.PairingSecurityAssociationInfo(peerNik, localNik,
                        new byte[16], WifiAwareStateManager.NAN_PAIRING_AKM_PASN,
                        Characteristics.WIFI_AWARE_CIPHER_SUITE_NCS_PK_PASN_128);
        mPairingConfigManager.addPairedDeviceSecurityAssociation(mPackageName, mAlias, pairingInfo);
        byte[] mac = MacAddress.fromString(mMac).toByteArray();
        byte[] peerTag = generateTag(peerNik, mNouce, mac);
        String peerAlias = mPairingConfigManager.getPairedDeviceAlias(mPackageName, mNouce, peerTag,
                mac);
        assertEquals(mAlias, peerAlias);
        assertEquals(pairingInfo, mPairingConfigManager.getSecurityInfoPairedDevice(peerAlias));
        mPairingConfigManager.removePairedDevice(mPackageName, mAlias);
        assertNull(mPairingConfigManager.getPairedDeviceAlias(mPackageName, mNouce, peerTag,
                mac));
        if (Environment.isSdkAtLeastC()) {
            verify(mWifiConfigManager, times(4)).saveToStore();
        } else {
            verify(mWifiConfigManager, never()).saveToStore();
        }
    }

    /**
     * Test remove App will clear the paired device
     */
    @Test
    public void testRemovePackages() {
        byte[] localNik = mPairingConfigManager.getNikForCallingPackage(mPackageName);
        byte[] peerNik = mPairingConfigManager.getNikForCallingPackage(mPackageName1);
        PairingConfigManager.PairingSecurityAssociationInfo pairingInfo =
                new PairingConfigManager.PairingSecurityAssociationInfo(peerNik, localNik,
                        new byte[16], WifiAwareStateManager.NAN_PAIRING_AKM_PASN,
                        Characteristics.WIFI_AWARE_CIPHER_SUITE_NCS_PK_PASN_128);
        mPairingConfigManager.addPairedDeviceSecurityAssociation(mPackageName, mAlias, pairingInfo);
        List<String> allAlias = mPairingConfigManager.getAllPairedDevices(mPackageName);
        assertEquals(1, allAlias.size());
        assertEquals(mAlias, allAlias.get(0));
        mPairingConfigManager.removePackage(mPackageName1);
        allAlias = mPairingConfigManager.getAllPairedDevices(mPackageName);
        assertEquals(1, allAlias.size());
        assertEquals(mAlias, allAlias.get(0));
        mPairingConfigManager.removePackage(mPackageName);
        allAlias = mPairingConfigManager.getAllPairedDevices(mPackageName);
        assertTrue(allAlias.isEmpty());
    }

    /**
     * Test reset will clear all the caches
     */
    @Test
    public void testReset() {
        byte[] localNik = mPairingConfigManager.getNikForCallingPackage(mPackageName);
        byte[] peerNik = mPairingConfigManager.getNikForCallingPackage(mPackageName1);
        PairingConfigManager.PairingSecurityAssociationInfo pairingInfo =
                new PairingConfigManager.PairingSecurityAssociationInfo(peerNik, localNik,
                        new byte[16], WifiAwareStateManager.NAN_PAIRING_AKM_PASN,
                        Characteristics.WIFI_AWARE_CIPHER_SUITE_NCS_PK_PASN_128);
        mPairingConfigManager.addPairedDeviceSecurityAssociation(mPackageName, mAlias, pairingInfo);
        List<String> allAlias = mPairingConfigManager.getAllPairedDevices(mPackageName);
        assertEquals(1, allAlias.size());
        assertEquals(mAlias, allAlias.get(0));
        mPairingConfigManager.reset();
        allAlias = mPairingConfigManager.getAllPairedDevices(mPackageName);
        assertTrue(allAlias.isEmpty());

    }

    /**
     * Test store data get and set
     */
    @Test
    public void testStoreDataSetAndGet() {
        byte[] nik = "test_nik".getBytes();
        Map<String, byte[]> nikMap = new HashMap<>();
        nikMap.put(mPackageName, nik);

        Set<String> aliasSet = new HashSet<>();
        aliasSet.add(mAlias);
        Map<String, Set<String>> perAppAliasMap = new HashMap<>();
        perAppAliasMap.put(mPackageName, aliasSet);

        Map<String, byte[]> aliasToNikMap = new HashMap<>();
        aliasToNikMap.put(mAlias, nik);

        PairingConfigManager.PairingSecurityAssociationInfo pairingInfo =
                new PairingConfigManager.PairingSecurityAssociationInfo(nik, nik,
                        new byte[16], WifiAwareStateManager.NAN_PAIRING_AKM_PASN,
                        Characteristics.WIFI_AWARE_CIPHER_SUITE_NCS_PK_PASN_128);
        Map<String, PairingConfigManager.PairingSecurityAssociationInfo>
                securityAssociationInfoMap = new HashMap<>();
        securityAssociationInfoMap.put(mAlias, pairingInfo);

        mPairingConfigManager.setPackageNameToNikMap(nikMap);
        mPairingConfigManager.setPerAppPairedAliasMap(perAppAliasMap);
        mPairingConfigManager.setAliasToNikMap(aliasToNikMap);
        mPairingConfigManager.setAliasToSecurityInfoMap(securityAssociationInfoMap);

        assertEquals(nikMap, mPairingConfigManager.getPackageNameToNikMap());
        assertEquals(perAppAliasMap, mPairingConfigManager.getPerAppPairedAliasMap());
        assertEquals(aliasToNikMap, mPairingConfigManager.getAliasToNikMap());
        assertEquals(securityAssociationInfoMap,
                mPairingConfigManager.getAliasToSecurityInfoMap());
    }

    /**
     * Test hasNewDataToSerialize and serializeComplete
     */
    @Test
    public void testSerializationFlag() {
        assertFalse(mPairingConfigManager.hasNewDataToSerialize());
        mPairingConfigManager.getNikForCallingPackage(mPackageName);
        assertTrue(mPairingConfigManager.hasNewDataToSerialize());
        mPairingConfigManager.serializeComplete();
        assertFalse(mPairingConfigManager.hasNewDataToSerialize());
    }

    private byte[] generateTag(byte[] nik, byte[] nonce, byte[] mac) {
        SecretKeySpec spec = new SecretKeySpec(nik, "HmacSHA256");
        try {
            Mac hash = Mac.getInstance("HmacSHA256");
            hash.init(spec);
            hash.update(NIR);
            hash.update(mac);
            hash.update(nonce);
            return Arrays.copyOf(hash.doFinal(), TAG_SIZE_IN_BYTE);
        } catch (NoSuchAlgorithmException | InvalidKeyException | IllegalStateException e) {
            return null;
        }
    }
}
