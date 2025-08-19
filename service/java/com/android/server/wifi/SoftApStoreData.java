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

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.Context;
import android.net.wifi.SoftApConfiguration;
import android.net.wifi.util.Environment;
import android.util.Log;

import com.android.server.wifi.util.SettingsMigrationDataHolder;
import com.android.server.wifi.util.WifiConfigStoreEncryptionUtil;
import com.android.server.wifi.util.XmlUtil;
import com.android.wifi.flags.Flags;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlSerializer;

import java.io.IOException;

/**
 * Store data for SoftAp
 */
public abstract class SoftApStoreData implements WifiConfigStore.StoreData {
    protected static final String TAG = "SoftApStoreData";
    private static final String XML_TAG_SECTION_HEADER_SOFTAP = "SoftAp";

    private final Context mContext;
    protected final SettingsMigrationDataHolder mSettingsMigrationDataHolder;
    protected final DataSource mDataSource;

    /**
     * Interface define the data source for the notifier store data.
     */
    public interface DataSource {
        /**
         * Retrieve the SoftAp configuration from the data source to serialize them to disk.
         *
         * @return {@link SoftApConfiguration} Instance of SoftApConfiguration.
         */
        SoftApConfiguration toSerialize();

        /**
         * Set the SoftAp configuration in the data source after serializing them from disk.
         *
         * @param config {@link SoftApConfiguration} Instance of SoftApConfiguration.
         */
        void fromDeserialized(@NonNull SoftApConfiguration config);

        /**
         * Clear internal data structure in preparation for user switch or initial store read.
         */
        void reset();

        /**
         * Indicates whether there is new data to serialize.
         */
        boolean hasNewDataToSerialize();

        /**
         * Retrieves data to be migrated from shared to private store on initial DE load and saves
         * in cache. Also keep record of a list of existing users as we will only migrate data for
         * existing users. Used by migration data supplier, {@link SharedStoreData}.
         *
         * @param config {@link SoftApConfiguration} Instance of SoftApConfiguration.
         */
        void prepareSharedToPrivateMigrationDataHolder(@NonNull SoftApConfiguration config);

        /**
         * Reset the migration cache and existing user list. Used by migration data supplier,
         * {@link SharedStoreData}.
         */
        void resetMigrationDataHolder();

        /**
         * Performs a one-off migration from shared to private store. Used by {@link UserStoreData},
         * once for each existing user if there's no SoftApConfiguration data loaded from CE.
         * Migration data is assumed to be retrieved by {@link SharedStoreData} with
         * {@link #prepareSharedToPrivateMigrationDataHolder(SoftApConfiguration)} on initial DE
         * load.
         */
        void migrateFromSharedToPrivateIfNeeded();
    }

    /**
     * Creates the SSID Set store data.
     *
     * @param dataSource The DataSource that implements the update and retrieval of the SSID set.
     */
    SoftApStoreData(Context context, SettingsMigrationDataHolder settingsMigrationDataHolder,
            DataSource dataSource) {
        mContext = context;
        mSettingsMigrationDataHolder = settingsMigrationDataHolder;
        mDataSource = dataSource;
    }

    @Override
    public void serializeData(XmlSerializer out,
            @Nullable WifiConfigStoreEncryptionUtil encryptionUtil)
            throws XmlPullParserException, IOException {
        SoftApConfiguration softApConfig = mDataSource.toSerialize();
        if (softApConfig != null) {
            XmlUtil.SoftApConfigurationXmlUtil.writeSoftApConfigurationToXml(out, softApConfig,
                    encryptionUtil);
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
        return XML_TAG_SECTION_HEADER_SOFTAP;
    }

    /**
     * Read-only StoreData for shared SoftApConfiguration. Used for loading migration data from
     * device shared storage and preparing migration cache as the supplier.
     *
     * <p> When {@link Flags#multiUserWifiEnhancement()} is not supported or sdk version is not
     * newer than B, SharedStoreData fallbacks to the only SoftApStoreData in old builds that I/O
     * with WifiConfigStoreSoftAp.xml in DE.
     */
    public static class SharedStoreData extends SoftApStoreData {

        /**
         * Create the read-only StoreData of SoftApConfiguration loaded from shared device store.
         *
         * @param context                     WiFi context, an instance of {@link Context}.
         * @param settingsMigrationDataHolder Holder for storing the migration settings data
         *                                    retrieved from Settings.Global. Note that this is not
         *                                    related to shared-to-private data migration.
         * @param dataSource                  The {@link DataSource} that implements the update,
         *                                    retrieval, and migration of
         *                                    {@link SoftApConfiguration} with
         *                                    {@link WifiApConfigStore}.
         */
        SharedStoreData(Context context,
                SettingsMigrationDataHolder settingsMigrationDataHolder,
                DataSource dataSource) {
            super(context, settingsMigrationDataHolder, dataSource);
        }

        @Override
        public void serializeData(XmlSerializer out,
                @android.annotation.Nullable WifiConfigStoreEncryptionUtil encryptionUtil)
                throws XmlPullParserException, IOException {
            if (Flags.multiUserWifiEnhancement() && Environment.isSdkNewerThanB()) {
                Log.e(TAG, "Cannot serialize read-only shared SoftApStoreData, which is only"
                        + "used for data migration.");
            } else {
                super.serializeData(out, encryptionUtil);
            }

        }

        @Override
        public void deserializeData(XmlPullParser in, int outerTagDepth,
                @WifiConfigStore.Version int version,
                @android.annotation.Nullable WifiConfigStoreEncryptionUtil encryptionUtil)
                throws XmlPullParserException, IOException {
            // Ignore empty reads.
            if (in == null) {
                return;
            }
            SoftApConfiguration softApConfig = XmlUtil.SoftApConfigurationXmlUtil.parseFromXml(
                    in, outerTagDepth, mSettingsMigrationDataHolder,
                    version >= WifiConfigStore.ENCRYPT_CREDENTIALS_CONFIG_STORE_DATA_VERSION,
                    encryptionUtil);
            if (softApConfig == null) {
                return;
            }
            if (Flags.multiUserWifiEnhancement() && Environment.isSdkNewerThanB()) {
                mDataSource.prepareSharedToPrivateMigrationDataHolder(softApConfig);
            } else {
                mDataSource.fromDeserialized(softApConfig);
            }
        }

        @Override
        public void resetData() {
            if (Flags.multiUserWifiEnhancement() && Environment.isSdkNewerThanB()) {
                // Since SharedStoreData will only load DE data to migration holder and won't write
                // into cache for user data, reset migration data holder is sufficient.
                mDataSource.resetMigrationDataHolder();
            } else {
                super.resetData();
            }
        }

        @Override
        public boolean hasNewDataToSerialize() {
            if (Flags.multiUserWifiEnhancement() && Environment.isSdkNewerThanB()) {
                // SharedDataStore is read-only so there's always no data for serialization.
                return false;
            } else {
                return super.hasNewDataToSerialize();
            }
        }

        @Override
        public @WifiConfigStore.StoreFileId int getStoreFileId() {
            return WifiConfigStore.STORE_FILE_SHARED_SOFTAP; // Shared softap store.
        }
    }

    /**
     * StoreData for user-specific SoftApConfiguration.
     */
    public static class UserStoreData extends SoftApStoreData {

        /**
         * Create the user-specific StoreData of SoftApConfiguration.
         *
         * @param context                     WiFi context, an instance of {@link Context}.
         * @param settingsMigrationDataHolder Holder for storing the migration settings data
         *                                    retrieved from Settings.Global. Note that this is not
         *                                    related to shared-to-private data migration.
         * @param dataSource                  The {@link DataSource} that implements the update,
         *                                    retrieval, and migration of
         *                                    {@link SoftApConfiguration} with
         *                                    {@link WifiApConfigStore}.
         */
        UserStoreData(Context context,
                SettingsMigrationDataHolder settingsMigrationDataHolder,
                DataSource dataSource) {
            super(context, settingsMigrationDataHolder, dataSource);
        }

        @Override
        public void deserializeData(XmlPullParser in, int outerTagDepth,
                @WifiConfigStore.Version int version,
                @Nullable WifiConfigStoreEncryptionUtil encryptionUtil)
                throws XmlPullParserException, IOException {
            if (in == null) {
                mDataSource.migrateFromSharedToPrivateIfNeeded();
                return;
            }
            SoftApConfiguration softApConfig = XmlUtil.SoftApConfigurationXmlUtil.parseFromXml(
                        in, outerTagDepth, mSettingsMigrationDataHolder,
                        version >= WifiConfigStore.ENCRYPT_CREDENTIALS_CONFIG_STORE_DATA_VERSION,
                        encryptionUtil);
            if (softApConfig != null) {
                mDataSource.fromDeserialized(softApConfig);
            }
        }

        @Override
        public @WifiConfigStore.StoreFileId int getStoreFileId() {
            return WifiConfigStore.STORE_FILE_USER_SOFTAP; // User-specific softap store.
        }
    }
}
