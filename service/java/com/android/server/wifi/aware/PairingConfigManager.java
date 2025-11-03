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

package com.android.server.wifi.aware;

import android.net.wifi.util.HexEncoding;
import android.util.Log;

import com.android.server.wifi.WifiConfigStore;
import com.android.server.wifi.WifiInjector;
import com.android.wifi.flags.FeatureFlags;

import java.nio.ByteBuffer;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * The manager to store and maintain the NAN identity key and NPK/NIK caching
 */
public class PairingConfigManager implements PairingConfigManagerData.DataSource {

    private static final String TAG = "AwarePairingManager";

    // NIK size
    public static final int NIK_SIZE_IN_BYTE = 16;
    // TAG size
    public static final int TAG_SIZE_IN_BYTE = 8;
    // NIR byte array
    public static final byte[] NIR = {'N', 'I', 'R'};

    /**
     * Store the NPKSA from the NAN Pairing confirmation
     */
    public static class PairingSecurityAssociationInfo {
        public final byte[] mPeerNik;
        public final byte[] mNpk;
        public final int mAkm;
        public final byte[] mLocalNik;

        public final int mCipherSuite;

        public PairingSecurityAssociationInfo(byte[] peerNik, byte[] localNik, byte[] npk,
                int akm, int cipherSuite) {
            mPeerNik = peerNik;
            mNpk = npk;
            mAkm = akm;
            mLocalNik = localNik;
            mCipherSuite = cipherSuite;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof PairingSecurityAssociationInfo)) return false;
            PairingSecurityAssociationInfo that = (PairingSecurityAssociationInfo) o;
            return Arrays.equals(mPeerNik, that.mPeerNik)
                    && Arrays.equals(mNpk, that.mNpk)
                    && mAkm == that.mAkm
                    && Arrays.equals(mLocalNik, that.mLocalNik)
                    && mCipherSuite == that.mCipherSuite;
        }

        @Override
        public int hashCode() {
            return Objects.hash(Arrays.hashCode(mPeerNik), Arrays.hashCode(mNpk), mAkm,
                    Arrays.hashCode(mLocalNik), mCipherSuite);
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("PairingSecurityAssociationInfo{");
            sb.append("mPeerNik=");
            sb.append(HexEncoding.encode(mPeerNik));
            sb.append(", mNpk=");
            sb.append(HexEncoding.encode(mNpk));
            sb.append(", mAkm=");
            sb.append(mAkm);
            sb.append(", mLocalNik=");
            sb.append(HexEncoding.encode(mLocalNik));
            sb.append(", mCipherSuite=");
            sb.append(mCipherSuite);
            sb.append("}");
            return sb.toString();
        }
    }

    private final Map<String, byte[]> mPackageNameToNikMap = new HashMap<>();
    private final Map<String, Set<String>> mPerAppPairedAliasMap = new HashMap<>();
    private final Map<String, byte[]> mAliasToNikMap = new HashMap<>();
    private final Map<String, PairingSecurityAssociationInfo> mAliasToSecurityInfoMap =
            new HashMap<>();
    private final WifiInjector mWifiInjector;
    private boolean mHasNewDataToSerialize = false;
    private final FeatureFlags mFeatureFlags;

    public PairingConfigManager(WifiInjector wifiInjector) {
        mWifiInjector = wifiInjector;
        mFeatureFlags = mWifiInjector.getDeviceConfigFacade().getFeatureFlags();
        if (mFeatureFlags.multiUserWifiEnhancement()) {
            wifiInjector.getWifiConfigStore().registerStoreData(
                    new PairingConfigManagerData(this));
        }
    }

    private void saveToStore() {
        if (!mFeatureFlags.multiUserWifiEnhancement()) {
            return;
        }
        if (!mWifiInjector.getWifiConfigManager().saveToStore()) {
            Log.w(TAG, "Failed to save to store");
        }
    }

    private byte[] createRandomNik() {
        long first, second;
        Random mRandom = new SecureRandom();
        first = mRandom.nextLong();
        second = mRandom.nextLong();
        ByteBuffer buffer = ByteBuffer.allocate(NIK_SIZE_IN_BYTE);
        buffer.putLong(first);
        buffer.putLong(second);
        return buffer.array();
    }

    /**
     * Get the NAN identity key of the app
     * @param packageName the package name of the calling App
     * @return the NAN identity key for this app
     */
    public byte[] getNikForCallingPackage(String packageName) {
        if (mPackageNameToNikMap.get(packageName) != null) {
            return mPackageNameToNikMap.get(packageName);
        }

        byte[] nik = createRandomNik();
        mPackageNameToNikMap.put(packageName, nik);
        mHasNewDataToSerialize = true;
        saveToStore();
        return nik;
    }

    /**
     * Get the matched pairing device's alias set by the app
     */
    public String getPairedDeviceAlias(String packageName, byte[] nonce, byte[] tag, byte[] mac) {
        Set<String> aliasSet = mPerAppPairedAliasMap.get(packageName);
        if (aliasSet == null) {
            return null;
        }
        for (String alias : aliasSet) {
            if (checkMatchAlias(alias, nonce, tag, mac)) {
                return alias;
            }
        }
        return null;
    }

    private boolean checkMatchAlias(String alias, byte[] nonce, byte[] tag, byte[] mac) {
        byte[] nik = mAliasToNikMap.get(alias);
        if (nik == null) return false;
        SecretKeySpec spec = new SecretKeySpec(nik, "HmacSHA256");

        try {
            Mac hash = Mac.getInstance("HmacSHA256");
            hash.init(spec);
            hash.update(NIR);
            hash.update(mac);
            hash.update(nonce);
            byte[] message = Arrays.copyOf(hash.doFinal(), TAG_SIZE_IN_BYTE);
            if (Arrays.equals(tag, message)) {
                return true;
            }

        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            Log.e(TAG, "Unexpected exception caught in getPairedDeviceAlias", e);
        }
        return false;
    }

    /**
     * Get the cached security info fo the target alias.
     */
    public PairingSecurityAssociationInfo getSecurityInfoPairedDevice(String alias) {
        return mAliasToSecurityInfoMap.get(alias);
    }

    /**
     * Add the NAN pairing sercurity info to the cache.
     */
    public void addPairedDeviceSecurityAssociation(String packageName, String alias,
            PairingSecurityAssociationInfo info) {
        if (info == null) {
            return;
        }
        Set<String> pairedDevices = mPerAppPairedAliasMap.computeIfAbsent(packageName,
                k -> new HashSet<>());
        pairedDevices.add(alias);
        mAliasToNikMap.put(alias, info.mPeerNik);
        mAliasToSecurityInfoMap.put(alias, info);
        mHasNewDataToSerialize = true;
        saveToStore();
    }

    /**
     * Remove all the caches related to the target calling App
     */
    public void removePackage(String packageName) {
        if (mPackageNameToNikMap.remove(packageName) == null) {
            return;
        }
        Set<String> aliasSet = mPerAppPairedAliasMap.remove(packageName);
        if (aliasSet != null) {
            for (String alias : aliasSet) {
                mAliasToNikMap.remove(alias);
                mAliasToSecurityInfoMap.remove(alias);
            }
        }
        mHasNewDataToSerialize = true;
        saveToStore();
    }

    /**
     * Remove the caches related to the target alias
     */
    public void removePairedDevice(String packageName, String alias) {
        mAliasToNikMap.remove(alias);
        mAliasToSecurityInfoMap.remove(alias);
        if (mPerAppPairedAliasMap.containsKey(packageName)) {
            mPerAppPairedAliasMap.get(packageName).remove(alias);
        }
        mHasNewDataToSerialize = true;
        saveToStore();
    }

    /**
     * Get all paired devices alias for target calling app
     */
    public List<String> getAllPairedDevices(String callingPackage) {
        Set<String> aliasSet = mPerAppPairedAliasMap.get(callingPackage);
        if (aliasSet == null) {
            return Collections.emptyList();
        }
        return new ArrayList<>(aliasSet);
    }

    @Override
    public Map<String, byte[]> getPackageNameToNikMap() {
        return mPackageNameToNikMap;
    }

    @Override
    public void setPackageNameToNikMap(Map<String, byte[]> packageNameTOnikMap) {
        mPackageNameToNikMap.clear();
        mPackageNameToNikMap.putAll(packageNameTOnikMap);
    }

    @Override
    public Map<String, Set<String>> getPerAppPairedAliasMap() {
        return mPerAppPairedAliasMap;
    }

    @Override
    public void setPerAppPairedAliasMap(Map<String, Set<String>> perAppPairedAliasMap) {
        mPerAppPairedAliasMap.clear();
        mPerAppPairedAliasMap.putAll(perAppPairedAliasMap);
    }

    @Override
    public Map<String, byte[]> getAliasToNikMap() {
        return mAliasToNikMap;
    }

    @Override
    public void setAliasToNikMap(Map<String, byte[]> aliasToNikMap) {
        mAliasToNikMap.clear();
        mAliasToNikMap.putAll(aliasToNikMap);
    }

    @Override
    public Map<String, PairingSecurityAssociationInfo> getAliasToSecurityInfoMap() {
        return mAliasToSecurityInfoMap;
    }

    @Override
    public void setAliasToSecurityInfoMap(
            Map<String, PairingSecurityAssociationInfo> aliasToSecurityInfoMap) {
        mAliasToSecurityInfoMap.clear();
        mAliasToSecurityInfoMap.putAll(aliasToSecurityInfoMap);
    }

    @Override
    public boolean hasNewDataToSerialize() {
        return mHasNewDataToSerialize;
    }

    @Override
    public void serializeComplete() {
        mHasNewDataToSerialize = false;
    }

    /**
     * Reset all the caches
     */
    @Override
    public void reset() {
        mPackageNameToNikMap.clear();
        mPerAppPairedAliasMap.clear();
        mAliasToNikMap.clear();
        mAliasToSecurityInfoMap.clear();
    }
}
