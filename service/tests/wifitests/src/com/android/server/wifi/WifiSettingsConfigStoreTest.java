/*
 * Copyright (C) 2019 The Android Open Source Project
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

import static com.android.server.wifi.WifiConfigurationTestUtil.TEST_UID;
import static com.android.server.wifi.WifiConfigurationTestUtil.TEST_USER_HANDLE;
import static com.android.server.wifi.WifiSettingsConfigStore.WIFI_VERBOSE_LOGGING_ENABLED;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.validateMockitoUsage;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import android.app.ActivityManager;
import android.content.Context;
import android.net.wifi.WifiMigration;
import android.net.wifi.util.Environment;
import android.os.Handler;
import android.os.UserHandle;
import android.os.UserManager;
import android.os.test.TestLooper;
import android.provider.Settings;
import android.util.Xml;

import androidx.test.filters.SmallTest;

import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.internal.util.FastXmlSerializer;
import com.android.server.wifi.WifiSettingsConfigStore.Key;
import com.android.server.wifi.util.SettingsMigrationDataHolder;
import com.android.server.wifi.util.XmlUtil;
import com.android.wifi.flags.FeatureFlags;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.MockitoSession;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlSerializer;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;


/**
 * Unit tests for {@link com.android.server.wifi.WifiSettingsConfigStore}.
 */
@SmallTest
public class WifiSettingsConfigStoreTest extends WifiBaseTest {

    private static final Key<Boolean> TEST_SHARED_SETTING =
            WifiSettingsConfigStore.WIFI_VERBOSE_LOGGING_ENABLED;
    private static final Key<Boolean> TEST_PRIVATE_SETTING =
            WifiSettingsConfigStore.WIFI_WEP_ALLOWED;

    private static final boolean TEST_SHARED_SETTING_NON_DEFAULT_VALUE =
            !TEST_SHARED_SETTING.defaultValue;
    private static final boolean TEST_PRIVATE_SETTING_NON_DEFAULT_VALUE =
            !TEST_PRIVATE_SETTING.defaultValue;

    // A collection of all user-specific private setting keys that need migration.
    // See WifiSettingsConfigStore#prepareSharedToPrivateMigrationDataHolder.
    private static final List<Key> PRIVATE_SETTING_KEYS_NEED_MIGRATION = List.of(
            WifiSettingsConfigStore.D2D_ALLOWED_WHEN_INFRA_STA_DISABLED,
            WifiSettingsConfigStore.WIFI_NETWORKS_AVAILABLE_NOTIFICATION_ON,
            WifiSettingsConfigStore.WIFI_SCAN_ALWAYS_AVAILABLE,
            WifiSettingsConfigStore.WIFI_WAKEUP_ENABLED,
            WifiSettingsConfigStore.WIFI_WEP_ALLOWED
    );

    // A map of user-specific private setting keys that have their Settings.Global counterpart, to
    // their Settings.Global key name.
    private static final Map<Key, String> PRIVATE_SETTING_KEYS_TO_SETTINGS_GLOBAL_KEYS = Map.of(
            WifiSettingsConfigStore.WIFI_NETWORKS_AVAILABLE_NOTIFICATION_ON,
            Settings.Global.WIFI_NETWORKS_AVAILABLE_NOTIFICATION_ON,
            WifiSettingsConfigStore.WIFI_WAKEUP_ENABLED,
            Settings.Global.WIFI_WAKEUP_ENABLED
    );

    @Mock
    private Context mContext;
    @Mock
    private SettingsMigrationDataHolder mSettingsMigrationDataHolder;
    @Mock
    private WifiConfigStore mWifiConfigStore;
    @Mock
    private WifiConfigManager mWifiConfigManager;
    @Mock
    private FeatureFlags mFeatureFlags;
    @Mock
    private FrameworkFacade mFrameworkFacade;
    @Mock
    private UserManager mUserManager;

    private MockitoSession mSession;
    private TestLooper mLooper;
    private WifiSettingsConfigStore mWifiSettingsConfigStore;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        mSession = ExtendedMockito.mockitoSession()
                .mockStatic(ActivityManager.class, withSettings().lenient())
                .startMocking();

        mLooper = new TestLooper();
        when(mFeatureFlags.multiUserWifiEnhancement()).thenReturn(false);
    }

    /**
     * Called after each test
     */
    @After
    public void cleanup() {
        validateMockitoUsage();
        if (mSession != null) {
            mSession.finishMocking();
        }
    }

    private void initializeWifiSettingsConfigStore() {
        mWifiSettingsConfigStore =
                new WifiSettingsConfigStore(mContext, new Handler(mLooper.getLooper()),
                        mSettingsMigrationDataHolder, mWifiConfigManager, mWifiConfigStore,
                        mFeatureFlags, mFrameworkFacade, mUserManager);
    }

    @Test
    public void testSetterGetter() {
        initializeWifiSettingsConfigStore();
        assertFalse(mWifiSettingsConfigStore.get(WIFI_VERBOSE_LOGGING_ENABLED));
        mWifiSettingsConfigStore.put(WIFI_VERBOSE_LOGGING_ENABLED, true);
        mLooper.dispatchAll();
        assertTrue(mWifiSettingsConfigStore.get(WIFI_VERBOSE_LOGGING_ENABLED));
        verify(mWifiConfigManager).saveToStore();
    }

    @Test
    public void testSetterTriggerSaveToStore() {
        assumeTrue(Environment.isSdkNewerThanB());
        when(mFeatureFlags.multiUserWifiEnhancement()).thenReturn(true);
        initializeWifiSettingsConfigStore();

        // Capture both shared and user-specific StoreData.
        ArgumentCaptor<WifiConfigStore.StoreData> storeDataCaptor = ArgumentCaptor.forClass(
                WifiConfigStore.StoreData.class);
        verify(mWifiConfigStore, times(2)).registerStoreData(storeDataCaptor.capture());
        WifiConfigStore.StoreData sharedStoreData = storeDataCaptor.getAllValues().get(0);
        WifiConfigStore.StoreData userStoreData = storeDataCaptor.getAllValues().get(1);
        InOrder inOrder = inOrder(mWifiConfigManager);

        assertFalse(sharedStoreData.hasNewDataToSerialize());
        mWifiSettingsConfigStore.put(TEST_SHARED_SETTING, TEST_SHARED_SETTING_NON_DEFAULT_VALUE);
        mLooper.dispatchAll();
        assertTrue(sharedStoreData.hasNewDataToSerialize());
        inOrder.verify(mWifiConfigManager).saveToStore();

        assertFalse(userStoreData.hasNewDataToSerialize());
        mWifiSettingsConfigStore.put(TEST_PRIVATE_SETTING, TEST_PRIVATE_SETTING_NON_DEFAULT_VALUE);
        mLooper.dispatchAll();
        assertTrue(userStoreData.hasNewDataToSerialize());
        inOrder.verify(mWifiConfigManager).saveToStore();
    }

    @Test
    public void testChangeListener() {
        initializeWifiSettingsConfigStore();
        WifiSettingsConfigStore.OnSettingsChangedListener listener = mock(
                WifiSettingsConfigStore.OnSettingsChangedListener.class);
        mWifiSettingsConfigStore.registerChangeListener(WIFI_VERBOSE_LOGGING_ENABLED, listener,
                new Handler(mLooper.getLooper()));
        mWifiSettingsConfigStore.put(WIFI_VERBOSE_LOGGING_ENABLED, true);
        mLooper.dispatchAll();
        verify(listener).onSettingsChanged(WIFI_VERBOSE_LOGGING_ENABLED, true);

        mWifiSettingsConfigStore.unregisterChangeListener(WIFI_VERBOSE_LOGGING_ENABLED, listener);
        mWifiSettingsConfigStore.put(WIFI_VERBOSE_LOGGING_ENABLED, false);
        mLooper.dispatchAll();
        verifyNoMoreInteractions(listener);
    }

    /**
     * Legacy test, can be replaced by {@link #testSharedStoreData} and {@link #testUserStoreData}
     * when the flag is enabled and cleaned.
     */
    @Test
    public void testSaveAndLoadFromStore() throws Exception {
        initializeWifiSettingsConfigStore();
        ArgumentCaptor<WifiConfigStore.StoreData> storeDataCaptor = ArgumentCaptor.forClass(
                WifiConfigStore.StoreData.class);
        verify(mWifiConfigStore).registerStoreData(storeDataCaptor.capture());
        assertNotNull(storeDataCaptor.getValue());

        XmlPullParser in = createSettingsTestXmlForParsing(
                Map.of(WIFI_VERBOSE_LOGGING_ENABLED.key, true));

        storeDataCaptor.getValue().resetData();
        storeDataCaptor.getValue().deserializeData(in, in.getDepth(), -1, null);

        assertTrue(mWifiSettingsConfigStore.get(WIFI_VERBOSE_LOGGING_ENABLED));
        // verify that we did not trigger migration.
        verifyNoMoreInteractions(mSettingsMigrationDataHolder);
    }

    @Test
    public void testSharedStoreData() throws Exception {
        assumeTrue(Environment.isSdkNewerThanB());
        when(mFeatureFlags.multiUserWifiEnhancement()).thenReturn(true);
        initializeWifiSettingsConfigStore();

        // Capture the shared StoreData and verify the file id.
        ArgumentCaptor<WifiConfigStore.StoreData> storeDataCaptor = ArgumentCaptor.forClass(
                WifiConfigStore.StoreData.class);
        verify(mWifiConfigStore, times(2)).registerStoreData(storeDataCaptor.capture());
        WifiConfigStore.StoreData sharedStoreData = storeDataCaptor.getAllValues().get(0);
        assertEquals(WifiConfigStore.STORE_FILE_SHARED_GENERAL, sharedStoreData.getStoreFileId());

        // Load from shared store.
        when(mUserManager.getUserHandles(anyBoolean())).thenReturn(List.of(TEST_USER_HANDLE));
        final XmlPullParser in = createSettingsTestXmlForParsing(
                Map.of(TEST_SHARED_SETTING.key, TEST_SHARED_SETTING_NON_DEFAULT_VALUE));
        sharedStoreData.deserializeData(in, in.getDepth(), -1, null);
        verifyNoMoreInteractions(mSettingsMigrationDataHolder);
        assertEquals(TEST_SHARED_SETTING_NON_DEFAULT_VALUE,
                mWifiSettingsConfigStore.get(TEST_SHARED_SETTING));
        assertEquals(Set.of(TEST_USER_HANDLE), mWifiSettingsConfigStore.mUsersNeedMigration);
        assertFalse(mWifiSettingsConfigStore.mSharedToPrivateMigrationDataHolder.isEmpty());

        // Save data to shared store. Verify that private setting is not saved.
        mWifiSettingsConfigStore.put(TEST_PRIVATE_SETTING, TEST_PRIVATE_SETTING_NON_DEFAULT_VALUE);
        final XmlSerializer out = new FastXmlSerializer();
        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        out.setOutput(outputStream, StandardCharsets.UTF_8.name());
        XmlUtil.writeDocumentStart(out, "Test" /* header */);
        sharedStoreData.serializeData(out, /* encryptionUtil= */ null);
        XmlUtil.writeDocumentEnd(out, "Test" /* header */);
        // Parse the output from serialization and verify the serialized content.
        final XmlPullParser parserForVerification = Xml.newPullParser();
        parserForVerification.setInput(new ByteArrayInputStream(outputStream.toByteArray()),
                StandardCharsets.UTF_8.name());
        XmlUtil.gotoDocumentStart(parserForVerification, "Test" /* header */);
        Map<String, Object> sharedSettings =
                WifiSettingsConfigStore.deserializeSettingsData(
                        parserForVerification, parserForVerification.getDepth());
        assertEquals(TEST_SHARED_SETTING_NON_DEFAULT_VALUE,
                sharedSettings.get(TEST_SHARED_SETTING.key));
        assertFalse(sharedSettings.containsKey(TEST_PRIVATE_SETTING.key));

        // Verify reset data properly.
        sharedStoreData.resetData();
        assertTrue(mWifiSettingsConfigStore.mUsersNeedMigration.isEmpty());
        assertTrue(mWifiSettingsConfigStore.mSharedToPrivateMigrationDataHolder.isEmpty());
    }

    @Test
    public void testUserStoreData() throws Exception {
        assumeTrue(Environment.isSdkNewerThanB());
        when(mFeatureFlags.multiUserWifiEnhancement()).thenReturn(true);
        initializeWifiSettingsConfigStore();

        // Capture the private UserStoreData and verify the file id.
        ArgumentCaptor<WifiConfigStore.StoreData> storeDataCaptor = ArgumentCaptor.forClass(
                WifiConfigStore.StoreData.class);
        verify(mWifiConfigStore, times(2)).registerStoreData(storeDataCaptor.capture());
        WifiConfigStore.StoreData userStoreData = storeDataCaptor.getAllValues().get(1);
        assertEquals(WifiConfigStore.STORE_FILE_USER_GENERAL, userStoreData.getStoreFileId());

        // Load from user store.
        final XmlPullParser in = createSettingsTestXmlForParsing(
                Map.of(TEST_PRIVATE_SETTING.key, TEST_PRIVATE_SETTING_NON_DEFAULT_VALUE));
        userStoreData.deserializeData(in, in.getDepth(), -1, null);
        assertEquals(TEST_PRIVATE_SETTING_NON_DEFAULT_VALUE,
                mWifiSettingsConfigStore.get(TEST_PRIVATE_SETTING));

        // Save data to user store. Verify that shared setting is not saved.
        mWifiSettingsConfigStore.put(TEST_SHARED_SETTING, TEST_SHARED_SETTING_NON_DEFAULT_VALUE);
        final XmlSerializer out = new FastXmlSerializer();
        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        out.setOutput(outputStream, StandardCharsets.UTF_8.name());
        XmlUtil.writeDocumentStart(out, "Test" /* header */);
        userStoreData.serializeData(out, /* encryptionUtil= */ null);
        XmlUtil.writeDocumentEnd(out, "Test" /* header */);
        // Parse the output from serialization and verify the serialized content.
        final XmlPullParser parserForVerification = Xml.newPullParser();
        parserForVerification.setInput(new ByteArrayInputStream(outputStream.toByteArray()),
                StandardCharsets.UTF_8.name());
        XmlUtil.gotoDocumentStart(parserForVerification, "Test" /* header */);
        Map<String, Object> userSettings =
                WifiSettingsConfigStore.deserializeSettingsData(parserForVerification,
                        parserForVerification.getDepth());
        assertEquals(TEST_PRIVATE_SETTING_NON_DEFAULT_VALUE,
                userSettings.get(TEST_PRIVATE_SETTING.key));
        assertFalse(userSettings.containsKey(TEST_SHARED_SETTING.key));

        // Verify reset data properly: 1. Shared setting still exists; 2. User private setting is
        // cleared (fallback to default value).
        userStoreData.resetData();
        assertEquals(TEST_SHARED_SETTING_NON_DEFAULT_VALUE,
                mWifiSettingsConfigStore.get(TEST_SHARED_SETTING));
        assertNotEquals(TEST_PRIVATE_SETTING_NON_DEFAULT_VALUE,
                mWifiSettingsConfigStore.get(TEST_PRIVATE_SETTING));
    }

    @Test
    public void testLoadFromMigration() throws Exception {
        initializeWifiSettingsConfigStore();
        ArgumentCaptor<WifiConfigStore.StoreData> storeDataCaptor = ArgumentCaptor.forClass(
                WifiConfigStore.StoreData.class);
        verify(mWifiConfigStore).registerStoreData(storeDataCaptor.capture());
        WifiConfigStore.StoreData storeData = storeDataCaptor.getValue();

        WifiMigration.SettingsMigrationData migrationData = mock(
                WifiMigration.SettingsMigrationData.class);
        when(mSettingsMigrationDataHolder.retrieveData()).thenReturn(migrationData);
        when(migrationData.isVerboseLoggingEnabled()).thenReturn(true);

        // indicate that there is not data in the store file to trigger migration.
        storeData.resetData();
        storeData.deserializeData(null, -1, -1, null);
        mLooper.dispatchAll();

        assertTrue(mWifiSettingsConfigStore.get(WIFI_VERBOSE_LOGGING_ENABLED));
        // Trigger store file write after migration.
        verify(mWifiConfigManager).saveToStore();
    }

    @Test
    public void testSharedToPrivateMigration_defaultValueForNewUser() throws Exception {
        assumeTrue(Environment.isSdkNewerThanB());
        when(mFeatureFlags.multiUserWifiEnhancement()).thenReturn(true);
        initializeWifiSettingsConfigStore();

        // Capture both shared and user-specific StoreData.
        ArgumentCaptor<WifiConfigStore.StoreData> storeDataCaptor = ArgumentCaptor.forClass(
                WifiConfigStore.StoreData.class);
        verify(mWifiConfigStore, times(2)).registerStoreData(storeDataCaptor.capture());
        WifiConfigStore.StoreData sharedStoreData = storeDataCaptor.getAllValues().get(0);
        WifiConfigStore.StoreData userStoreData = storeDataCaptor.getAllValues().get(1);

        // Load from DE that contains both shared and private settings. Verify that the migration
        // cache is populated. Make an empty list for existing users so any user would be new.
        when(mUserManager.getUserHandles(anyBoolean())).thenReturn(List.of());
        XmlPullParser in = createSettingsTestXmlForParsing(Map.of(
                TEST_SHARED_SETTING.key, TEST_SHARED_SETTING_NON_DEFAULT_VALUE,
                TEST_PRIVATE_SETTING.key, TEST_PRIVATE_SETTING_NON_DEFAULT_VALUE
        ));
        sharedStoreData.deserializeData(in, in.getDepth(), -1, null);
        assertEquals(TEST_PRIVATE_SETTING_NON_DEFAULT_VALUE,
                mWifiSettingsConfigStore.mSharedToPrivateMigrationDataHolder.get(
                        TEST_PRIVATE_SETTING.key));

        // CE doesn't contain settings and try to migrate. Verify that for new users the private
        // setting will fallback to default value.
        assertEquals(TEST_PRIVATE_SETTING_NON_DEFAULT_VALUE,
                mWifiSettingsConfigStore.get(TEST_PRIVATE_SETTING));
        when(ActivityManager.getCurrentUser()).thenReturn(UserHandle.getUserId(TEST_UID));
        userStoreData.deserializeData(/* input */ null, in.getDepth(), -1, null);
        assertEquals(TEST_PRIVATE_SETTING.defaultValue,
                mWifiSettingsConfigStore.get(TEST_PRIVATE_SETTING));

        // Trigger store file write after migration. Since there's no WifiSettingsConfigStore#put
        // operation so far, changes to UserStoreData#hasNewDataToSerialize must be the result of
        // migration.
        mLooper.dispatchAll();
        assertFalse(sharedStoreData.hasNewDataToSerialize());
        assertTrue(userStoreData.hasNewDataToSerialize());
        verify(mWifiConfigManager).saveToStore();
    }

    @Test
    public void testSharedToPrivateMigration_migrateForExistingUser() throws Exception {
        assumeTrue(Environment.isSdkNewerThanB());
        when(mFeatureFlags.multiUserWifiEnhancement()).thenReturn(true);
        initializeWifiSettingsConfigStore();

        // Capture both shared and user-specific StoreData.
        ArgumentCaptor<WifiConfigStore.StoreData> storeDataCaptor = ArgumentCaptor.forClass(
                WifiConfigStore.StoreData.class);
        verify(mWifiConfigStore, times(2)).registerStoreData(storeDataCaptor.capture());
        WifiConfigStore.StoreData sharedStoreData = storeDataCaptor.getAllValues().get(0);
        WifiConfigStore.StoreData userStoreData = storeDataCaptor.getAllValues().get(1);

        // Load from DE that contains both shared and private settings. Verify that the migration
        // cache is populated.
        when(mUserManager.getUserHandles(anyBoolean())).thenReturn(List.of(TEST_USER_HANDLE));
        XmlPullParser in = createSettingsTestXmlForParsing(Map.of(
                TEST_SHARED_SETTING.key, TEST_SHARED_SETTING_NON_DEFAULT_VALUE,
                TEST_PRIVATE_SETTING.key, TEST_PRIVATE_SETTING_NON_DEFAULT_VALUE
        ));
        sharedStoreData.deserializeData(in, in.getDepth(), -1, null);
        assertEquals(TEST_PRIVATE_SETTING_NON_DEFAULT_VALUE,
                mWifiSettingsConfigStore.mSharedToPrivateMigrationDataHolder.get(
                        TEST_PRIVATE_SETTING.key));

        // CE doesn't contain settings and try to migrate. Reset the private key to default to track
        // change as the migrated value would be non-default.
        mWifiSettingsConfigStore.put(TEST_PRIVATE_SETTING, TEST_PRIVATE_SETTING.defaultValue);
        when(ActivityManager.getCurrentUser()).thenReturn(UserHandle.getUserId(TEST_UID));
        userStoreData.deserializeData(/* input */ null, in.getDepth(), -1, null);
        assertEquals(TEST_PRIVATE_SETTING_NON_DEFAULT_VALUE,
                mWifiSettingsConfigStore.get(TEST_PRIVATE_SETTING));
    }

    /**
     * When migration data is not available from shared store, either fallback to default
     * (e.g. {@link WifiSettingsConfigStore#WIFI_WEP_ALLOWED}) or Settings.Global (e.g.
     * {@link WifiSettingsConfigStore#WIFI_NETWORKS_AVAILABLE_NOTIFICATION_ON}).
     */
    @Test
    public void testSharedToPrivateMigration_dataNotFoundFromSharedStore() throws Exception {
        assumeTrue(Environment.isSdkNewerThanB());
        when(mFeatureFlags.multiUserWifiEnhancement()).thenReturn(true);
        initializeWifiSettingsConfigStore();

        // Capture both shared and user-specific StoreData.
        ArgumentCaptor<WifiConfigStore.StoreData> storeDataCaptor = ArgumentCaptor.forClass(
                WifiConfigStore.StoreData.class);
        verify(mWifiConfigStore, times(2)).registerStoreData(storeDataCaptor.capture());
        WifiConfigStore.StoreData sharedStoreData = storeDataCaptor.getAllValues().get(0);
        WifiConfigStore.StoreData userStoreData = storeDataCaptor.getAllValues().get(1);

        // Load from DE that does not contain private settings to be migrated.
        PRIVATE_SETTING_KEYS_TO_SETTINGS_GLOBAL_KEYS.forEach(
                (configStoreKey, settingsGlobalKey) -> {
                    if (configStoreKey.defaultValue instanceof Boolean) {
                        boolean value = (Boolean) configStoreKey.defaultValue;
                        when(mFrameworkFacade.getIntegerSetting(eq(mContext), eq(settingsGlobalKey),
                                anyInt())).thenReturn(value ? 1 : 0);
                    } else {
                        throw new IllegalArgumentException(
                                "Unhandled type for config key: " + configStoreKey.key);
                    }
                });
        when(mUserManager.getUserHandles(anyBoolean())).thenReturn(List.of(TEST_USER_HANDLE));
        XmlPullParser in = createSettingsTestXmlForParsing(Map.of(
                TEST_SHARED_SETTING.key, TEST_SHARED_SETTING_NON_DEFAULT_VALUE
        ));
        sharedStoreData.deserializeData(in, in.getDepth(), -1, null);

        // Verify that in the migration data cache, private keys to be migrated from both
        // Settings.Global and defaultValue are populated with their default values.
        for (Key key : PRIVATE_SETTING_KEYS_NEED_MIGRATION) {
            assertEquals(key.defaultValue,
                    mWifiSettingsConfigStore.mSharedToPrivateMigrationDataHolder.get(key.key));
        }

        // CE doesn't contain settings and try to migrate. Before migration, explicitly set all
        // values as null to track change.
        for (Key key : PRIVATE_SETTING_KEYS_NEED_MIGRATION) {
            mWifiSettingsConfigStore.put(key, null);
        }
        when(ActivityManager.getCurrentUser()).thenReturn(UserHandle.getUserId(TEST_UID));
        userStoreData.deserializeData(/* input */ null, in.getDepth(), -1, null);
        for (Key key : PRIVATE_SETTING_KEYS_NEED_MIGRATION) {
            assertEquals(key.defaultValue, mWifiSettingsConfigStore.get(key));
        }
    }

    private XmlPullParser createSettingsTestXmlForParsing(final Map<String, Object> settings)
            throws Exception {
        // Serialize
        final XmlSerializer out = new FastXmlSerializer();
        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        out.setOutput(outputStream, StandardCharsets.UTF_8.name());
        XmlUtil.writeDocumentStart(out, "Test");
        XmlUtil.writeNextValue(out, "Values", settings);
        XmlUtil.writeDocumentEnd(out, "Test");

        // Start Deserializing
        final XmlPullParser in = Xml.newPullParser();
        ByteArrayInputStream inputStream = new ByteArrayInputStream(outputStream.toByteArray());
        in.setInput(inputStream, StandardCharsets.UTF_8.name());
        XmlUtil.gotoDocumentStart(in, "Test");
        return in;
    }
}
