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

import android.net.MacAddress;
import android.net.wifi.aware.Characteristics;

import com.android.server.wifi.WifiBaseTest;

import org.junit.Before;
import org.junit.Test;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.List;

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

    @Before
    public void setup() {
        mPairingConfigManager = new PairingConfigManager();
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
