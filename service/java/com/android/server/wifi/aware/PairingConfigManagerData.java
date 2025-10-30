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

package com.android.server.wifi.aware;

import android.net.wifi.util.HexEncoding;
import android.util.Log;

import com.android.server.wifi.WifiConfigStore;
import com.android.server.wifi.util.WifiConfigStoreEncryptionUtil;
import com.android.server.wifi.util.XmlUtil;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlSerializer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * This class performs serialization and parsing of XML data block that contain the NAN pairing
 * configuration data.
 */
public class PairingConfigManagerData implements WifiConfigStore.StoreData {
    private static final String TAG = "PairingConfigManagerData";

    public static final String XML_TAG_SECTION_HEADER = "PairingConfigManagerData";
    private static final String XML_TAG_PACKAGE_NAME_TO_NIK_MAP = "PackageNameToNikMap";
    private static final String XML_TAG_PER_APP_PAIRED_ALIAS_MAP = "PerAppPairedAliasMap";
    private static final String XML_TAG_ALIAS_TO_NIK_MAP = "AliasToNikMap";
    private static final String XML_TAG_ALIAS_TO_SECURITY_INFO_MAP = "AliasToSecurityInfoMap";

    private static final String XML_TAG_PEER_NIK = "PeerNik";
    private static final String XML_TAG_LOCAL_NIK = "LocalNik";
    private static final String XML_TAG_NPK = "Npk";
    private static final String XML_TAG_AKM = "Akm";
    private static final String XML_TAG_CIPHER_SUITE = "CipherSuite";

    /**
     * Interface defining the data source for the NAN pairing configuration store data.
     */
    public interface DataSource {
        Map<String, byte[]> getPackageNameToNikMap();
        void setPackageNameToNikMap(Map<String, byte[]> packageNameTOnikMap);
        Map<String, Set<String>> getPerAppPairedAliasMap();
        void setPerAppPairedAliasMap(Map<String, Set<String>> perAppPairedAliasMap);
        Map<String, byte[]> getAliasToNikMap();
        void setAliasToNikMap(Map<String, byte[]> aliasToNikMap);
        Map<String, PairingConfigManager.PairingSecurityAssociationInfo>
                getAliasToSecurityInfoMap();
        void setAliasToSecurityInfoMap(
                Map<String, PairingConfigManager.PairingSecurityAssociationInfo>
                        aliasToSecurityInfoMap);

        void reset();
        boolean hasNewDataToSerialize();
        void serializeComplete();
    }

    private final DataSource mDataSource;

    public PairingConfigManagerData(DataSource dataSource) {
        mDataSource = dataSource;
    }

    @Override
    public void serializeData(XmlSerializer out, WifiConfigStoreEncryptionUtil encryptionUtil)
            throws XmlPullParserException, IOException {
        XmlUtil.writeNextValue(out, XML_TAG_PACKAGE_NAME_TO_NIK_MAP,
                mDataSource.getPackageNameToNikMap());
        XmlUtil.writeNextValue(out, XML_TAG_PER_APP_PAIRED_ALIAS_MAP,
                mDataSource.getPerAppPairedAliasMap());
        XmlUtil.writeNextValue(out, XML_TAG_ALIAS_TO_NIK_MAP,
                mDataSource.getAliasToNikMap());
        XmlUtil.writeNextValue(out, XML_TAG_ALIAS_TO_SECURITY_INFO_MAP,
                securityInfoMapToMap(mDataSource.getAliasToSecurityInfoMap()));
        mDataSource.serializeComplete();
    }

    @Override
    public void deserializeData(XmlPullParser in, int outerTagDepth, int version,
            WifiConfigStoreEncryptionUtil encryptionUtil)
            throws XmlPullParserException, IOException {
        mDataSource.reset();
        if (in != null) {
            while (!XmlUtil.isNextSectionEnd(in, outerTagDepth)) {
                String[] valueName = new String[1];
                Object value = XmlUtil.readCurrentValue(in, valueName);
                if (valueName[0] == null) {
                    throw new XmlPullParserException("Missing value name");
                }
                switch (valueName[0]) {
                    case XML_TAG_PACKAGE_NAME_TO_NIK_MAP ->
                            mDataSource.setPackageNameToNikMap((Map<String, byte[]>) value);
                    case XML_TAG_PER_APP_PAIRED_ALIAS_MAP ->
                            mDataSource.setPerAppPairedAliasMap((Map<String, Set<String>>) value);
                    case XML_TAG_ALIAS_TO_NIK_MAP ->
                            mDataSource.setAliasToNikMap((Map<String, byte[]>) value);
                    case XML_TAG_ALIAS_TO_SECURITY_INFO_MAP ->
                            mDataSource.setAliasToSecurityInfoMap(
                                    mapToSecurityInfoMap((Map<String, Map<String, String>>) value));
                    default -> Log.w(TAG, "Unknown tag under " + XML_TAG_SECTION_HEADER + ": "
                            + valueName[0]);
                }
            }
        }
    }

    @Override
    public void resetData() {
        mDataSource.reset();
    }

    @Override
    public boolean hasNewDataToSerialize() {
        return mDataSource.hasNewDataToSerialize();
    }

    @Override
    public String getName() {
        return XML_TAG_SECTION_HEADER;
    }

    @Override
    public int getStoreFileId() {
        return WifiConfigStore.STORE_FILE_USER_AWARE;
    }

    private Map<String, Map<String, String>> securityInfoMapToMap(
            Map<String, PairingConfigManager.PairingSecurityAssociationInfo> input) {
        Map<String, Map<String, String>> output = new HashMap<>();
        if (input == null) return output;
        for (Map.Entry<String, PairingConfigManager.PairingSecurityAssociationInfo> entry :
                input.entrySet()) {
            PairingConfigManager.PairingSecurityAssociationInfo info = entry.getValue();
            Map<String, String> infoMap = new HashMap<>();
            infoMap.put(XML_TAG_PEER_NIK, HexEncoding.encodeToString(info.mPeerNik));
            infoMap.put(XML_TAG_LOCAL_NIK, HexEncoding.encodeToString(info.mLocalNik));
            infoMap.put(XML_TAG_NPK, HexEncoding.encodeToString(info.mNpk));
            infoMap.put(XML_TAG_AKM, String.valueOf(info.mAkm));
            infoMap.put(XML_TAG_CIPHER_SUITE, String.valueOf(info.mCipherSuite));
            output.put(entry.getKey(), infoMap);
        }
        return output;
    }

    private Map<String, PairingConfigManager.PairingSecurityAssociationInfo> mapToSecurityInfoMap(
            Map<String, Map<String, String>> input) {
        Map<String, PairingConfigManager.PairingSecurityAssociationInfo> output = new HashMap<>();
        if (input == null) return output;
        try {
            for (Map.Entry<String, Map<String, String>> entry : input.entrySet()) {
                Map<String, String> infoMap = entry.getValue();
                byte[] peerNik = HexEncoding.decode(infoMap.get(XML_TAG_PEER_NIK));
                byte[] localNik = HexEncoding.decode((String) infoMap.get(XML_TAG_LOCAL_NIK));
                byte[] npk = HexEncoding.decode((String) infoMap.get(XML_TAG_NPK));
                int akm = Integer.parseInt(infoMap.get(XML_TAG_AKM));
                int cipherSuite = Integer.parseInt(infoMap.get(XML_TAG_CIPHER_SUITE));
                output.put(entry.getKey(),
                        new PairingConfigManager.PairingSecurityAssociationInfo(peerNik, localNik,
                                npk, akm, cipherSuite));
            }
        } catch (IllegalArgumentException | ClassCastException e) {
            Log.e(TAG, "Failed to decode security info map", e);
            // return empty map on failure
            return new HashMap<>();
        }
        return output;
    }
}
