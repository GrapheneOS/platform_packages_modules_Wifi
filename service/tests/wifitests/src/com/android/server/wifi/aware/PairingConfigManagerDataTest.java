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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


import android.util.Xml;
import com.android.internal.util.FastXmlSerializer;
import com.android.server.wifi.WifiBaseTest;
import com.android.server.wifi.WifiConfigStore;
import com.android.server.wifi.util.WifiConfigStoreEncryptionUtil;
import com.android.server.wifi.util.XmlUtil;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlSerializer;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Unit test harness for {@link PairingConfigManagerData}.
 */
public class PairingConfigManagerDataTest extends WifiBaseTest {
    @Mock
    private PairingConfigManagerData.DataSource mDataSource;
    @Mock
    private WifiConfigStoreEncryptionUtil mEncryptionUtil;
    private PairingConfigManagerData mPairingConfigManagerData;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        mPairingConfigManagerData = new PairingConfigManagerData(mDataSource);
    }

    /**
     * Test serialization and deserialization of data.
     */
    @Test
    public void testSerializeAndDeserialize() throws Exception {
        // Setup sample data
        Map<String, byte[]> nikMap = new HashMap<>();
        nikMap.put("pkg", "nik".getBytes());
        Set<String> aliasSet = new HashSet<>();
        aliasSet.add("alias");
        Map<String, Set<String>> perAppAliasMap = new HashMap<>();
        perAppAliasMap.put("pkg", aliasSet);
        Map<String, byte[]> aliasToNikMap = new HashMap<>();
        aliasToNikMap.put("alias", "peer_nik".getBytes());
        PairingConfigManager.PairingSecurityAssociationInfo securityInfo =
                new PairingConfigManager.PairingSecurityAssociationInfo("peer_nik".getBytes(),
                        "local_nik".getBytes(), "npk".getBytes(), 1, 2);
        Map<String, PairingConfigManager.PairingSecurityAssociationInfo> securityInfoMap =
                new HashMap<>();
        securityInfoMap.put("alias", securityInfo);

        when(mDataSource.getPackageNameToNikMap()).thenReturn(nikMap);
        when(mDataSource.getPerAppPairedAliasMap()).thenReturn(perAppAliasMap);
        when(mDataSource.getAliasToNikMap()).thenReturn(aliasToNikMap);
        when(mDataSource.getAliasToSecurityInfoMap()).thenReturn(securityInfoMap);

        // Serialize the data
        final XmlSerializer out = new FastXmlSerializer();
        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        out.setOutput(outputStream, StandardCharsets.UTF_8.name());
        mPairingConfigManagerData.serializeData(out, mEncryptionUtil);
        out.flush();

        final XmlPullParser in = Xml.newPullParser();
        final ByteArrayInputStream inputStream = new ByteArrayInputStream(
                outputStream.toByteArray());
        in.setInput(inputStream, StandardCharsets.UTF_8.name());
        mPairingConfigManagerData.deserializeData(in, in.getDepth(), 0, mEncryptionUtil);

        // Verify that the data was set correctly
        ArgumentCaptor<Map<String, byte[]>> nikMapCaptor =
                ArgumentCaptor.forClass(Map.class);
        ArgumentCaptor<Map<String, Set<String>>> perAppAliasMapCaptor =
                ArgumentCaptor.forClass(Map.class);
        ArgumentCaptor<Map<String, byte[]>> aliasToNikMapCaptor =
                ArgumentCaptor.forClass(Map.class);
        ArgumentCaptor<Map<String, PairingConfigManager.PairingSecurityAssociationInfo>>
                securityInfoMapCaptor = ArgumentCaptor.forClass(Map.class);
        verify(mDataSource).setPackageNameToNikMap(nikMapCaptor.capture());
        verify(mDataSource).setPerAppPairedAliasMap(perAppAliasMapCaptor.capture());
        verify(mDataSource).setAliasToNikMap(aliasToNikMapCaptor.capture());
        verify(mDataSource).setAliasToSecurityInfoMap(securityInfoMapCaptor.capture());
        assertEquals(nikMap.size(), nikMapCaptor.getValue().size());
        assertArrayEquals(nikMap.get("pkg"), nikMapCaptor.getValue().get("pkg"));
        assertEquals(perAppAliasMap.size(), perAppAliasMapCaptor.getValue().size());
        assertEquals(aliasSet, perAppAliasMapCaptor.getValue().get("pkg"));
        assertEquals(aliasToNikMap.size(), aliasToNikMapCaptor.getValue().size());
        assertArrayEquals(aliasToNikMap.get("alias"), aliasToNikMapCaptor.getValue().get("alias"));
        assertEquals(securityInfoMap.size(), securityInfoMapCaptor.getValue().size());
        assertEquals(securityInfo, securityInfoMapCaptor.getValue().get("alias"));
    }

    /**
     * Test the resetData method.
     */
    @Test
    public void testResetData() {
        mPairingConfigManagerData.resetData();
        verify(mDataSource).reset();
    }

    /**
     * Test the hasNewDataToSerialize method.
     */
    @Test
    public void testHasNewDataToSerialize() {
        when(mDataSource.hasNewDataToSerialize()).thenReturn(true);
        assertEquals(true, mPairingConfigManagerData.hasNewDataToSerialize());
    }

    @Test
    public void testGetName() {
        assertEquals(PairingConfigManagerData.XML_TAG_SECTION_HEADER,
                mPairingConfigManagerData.getName());
    }

    @Test
    public void testGetStoreFileId() {
        assertEquals(WifiConfigStore.STORE_FILE_USER_AWARE,
                mPairingConfigManagerData.getStoreFileId());
    }

}
