/*
 * Copyright (C) 2020 The Android Open Source Project
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
import android.app.ActivityManager;
import android.content.Context;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiMigration;
import android.net.wifi.util.Environment;
import android.os.Handler;
import android.os.UserHandle;
import android.os.UserManager;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;
import com.android.server.wifi.util.SettingsMigrationDataHolder;
import com.android.server.wifi.util.WifiConfigStoreEncryptionUtil;
import com.android.server.wifi.util.XmlUtil;
import com.android.wifi.flags.FeatureFlags;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlSerializer;

import java.io.FileDescriptor;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Store data for storing wifi settings. These are key (string) / value pairs that are stored in
 * WifiConfigStore.xml file in a separate section.
 */
public class WifiSettingsConfigStore {
    private static final String TAG = "WifiSettingsConfigStore";
    public static final String XML_TAG_SECTION_HEADER = "Settings";
    public static final String XML_TAG_VALUES = "Values";

    // List of all allowed keys.
    private static final ArrayList<Key> sKeys = new ArrayList<>();

    /******** Wifi shared pref keys ***************/
    /**
     * Indicate whether factory reset request is pending.
     */
    public static final Key<Boolean> WIFI_P2P_PENDING_FACTORY_RESET =
            new Key<>("wifi_p2p_pending_factory_reset", false);

    /**
     * Allow scans to be enabled even wifi is turned off.
     */
    public static final Key<Boolean> WIFI_SCAN_ALWAYS_AVAILABLE =
            new Key<>("wifi_scan_always_enabled", false);

    /**
     * Whether wifi scan throttle is enabled or not.
     */
    public static final Key<Boolean> WIFI_SCAN_THROTTLE_ENABLED =
            new Key<>("wifi_scan_throttle_enabled", true);

    /**
     * Setting to enable verbose logging in Wi-Fi.
     */
    public static final Key<Boolean> WIFI_VERBOSE_LOGGING_ENABLED =
            new Key<>("wifi_verbose_logging_enabled", false);

    /**
     * Setting to enable verbose logging in Wi-Fi Aware.
     */
    public static final Key<Boolean> WIFI_AWARE_VERBOSE_LOGGING_ENABLED =
            new Key<>("wifi_aware_verbose_logging_enabled", false);

    /**
     * The Wi-Fi peer-to-peer device name
     */
    public static final Key<String> WIFI_P2P_DEVICE_NAME =
            new Key<>("wifi_p2p_device_name", null);

    /**
     * The Wi-Fi peer-to-peer device mac address
     */
    public static final Key<String> WIFI_P2P_DEVICE_ADDRESS =
            new Key<>("wifi_p2p_device_address", null);

    /**
     * Whether Wifi scoring is enabled or not.
     */
    public static final Key<Boolean> WIFI_SCORING_ENABLED =
            new Key<>("wifi_scoring_enabled", true);

    /**
     * Whether Wifi Passpoint is enabled or not.
     */
    public static final Key<Boolean> WIFI_PASSPOINT_ENABLED =
            new Key<>("wifi_passpoint_enabled", true);

    /**
     * Whether Wifi Multi Internet is enabled for multi ap, dbs or disabled.
     */
    public static final Key<Integer> WIFI_MULTI_INTERNET_MODE =
            new Key<Integer>("wifi_multi_internet_mode",
                    WifiManager.WIFI_MULTI_INTERNET_MODE_DISABLED);

    /**
     * Store the STA factory MAC address retrieved from the driver on the first bootup.
     */
    public static final Key<String> WIFI_STA_FACTORY_MAC_ADDRESS =
            new Key<>("wifi_sta_factory_mac_address", null);

    /**
     * Store the Secondary STA factory MAC address retrieved from the driver on the first bootup.
     */
    public static final Key<String> SECONDARY_WIFI_STA_FACTORY_MAC_ADDRESS =
            new Key<>("secondary_wifi_sta_factory_mac_address", null);


    /**
     * Store the default country code updated via {@link WifiManager#setDefaultCountryCode(String)}
     */
    public static final Key<String> WIFI_DEFAULT_COUNTRY_CODE =
            new Key<>("wifi_default_country_code", WifiCountryCode.getOemDefaultCountryCode());

    /**
     * Store the supported P2P features.
     */
    public static final Key<Long> WIFI_P2P_SUPPORTED_FEATURES =
            new Key<>("wifi_p2p_supported_features", 0L);

    /**
     * Store the supported features retrieved from WiFi HAL and Supplicant HAL. Note that this
     * value is deprecated and is replaced by {@link #WIFI_NATIVE_EXTENDED_SUPPORTED_FEATURES}
     */
    public static final Key<Long> WIFI_NATIVE_SUPPORTED_FEATURES =
            new Key<>("wifi_native_supported_features", 0L);

    /**
     * Store the extended supported features retrieved from WiFi HAL and Supplicant HAL
     */
    public static final Key<long[]> WIFI_NATIVE_EXTENDED_SUPPORTED_FEATURES =
            new Key<>("wifi_native_extended_supported_features", new long[0]);

    /**
     * Store the supported features retrieved from WiFi HAL and Supplicant HAL
     */
    public static final Key<Integer> WIFI_NATIVE_SUPPORTED_STA_BANDS =
            new Key<>("wifi_native_supported_sta_bands", 0);

    /**
     * Store the static chip info retrieved from WiFi HAL
     */
    public static final Key<String> WIFI_STATIC_CHIP_INFO = new Key<>("wifi_static_chip_info", "");

    /**
     * Store the last country code used by Soft AP.
     */
    public static final Key<String> WIFI_SOFT_AP_COUNTRY_CODE =
            new Key<>("wifi_last_country_code", "");

    /**
     * Store the available channel frequencies in a JSON array for Soft AP for the last country
     * code used.
     */
    public static final Key<String> WIFI_AVAILABLE_SOFT_AP_FREQS_MHZ =
            new Key<>("wifi_available_soft_ap_freqs_mhz", "[]");

    /**
     * Whether to show a dialog when third party apps attempt to enable wifi.
     */
    public static final Key<Boolean> SHOW_DIALOG_WHEN_THIRD_PARTY_APPS_ENABLE_WIFI =
            new Key<>("show_dialog_when_third_party_apps_enable_wifi", false);

    /**
     * Whether the
     * {@link WifiManager#setThirdPartyAppEnablingWifiConfirmationDialogEnabled(boolean)} API was
     * called to set the value of {@link #SHOW_DIALOG_WHEN_THIRD_PARTY_APPS_ENABLE_WIFI}.
     */
    public static final Key<Boolean> SHOW_DIALOG_WHEN_THIRD_PARTY_APPS_ENABLE_WIFI_SET_BY_API =
            new Key<>("show_dialog_when_third_party_apps_enable_wifi_set_by_api", false);

    /**
     * AIDL version implemented by the Supplicant service.
     */
    public static final Key<Integer> SUPPLICANT_HAL_AIDL_SERVICE_VERSION =
            new Key<>("supplicant_hal_aidl_service_version", -1);

    /**
     * Whether the WEP network is allowed or not.
     */
    public static final Key<Boolean> WIFI_WEP_ALLOWED = new Key<>("wep_allowed", true);

    /**
     * Store wiphy capability for 11be support.
     */
    public static final Key<Boolean> WIFI_WIPHY_11BE_SUPPORTED =
            new Key<>("wifi_wiphy_11be_supported", true);

    /**
     * Whether the D2D is allowed or not when infra sta is disabled.
     */
    public static final Key<Boolean> D2D_ALLOWED_WHEN_INFRA_STA_DISABLED =
            new Key<>("d2d_allowed_when_infra_sta_disabled", false);

    /**
     * Whether the user allows or not to notify when a high-quality public network is available.
     */
    public static final Key<Boolean> WIFI_NETWORKS_AVAILABLE_NOTIFICATION_ON =
            new Key<>("wifi_networks_available_notification_on", true);

    /**
     * Whether the user enables or not the feature to auto wakeup near a high-quality saved network.
     */
    public static final Key<Boolean> WIFI_WAKEUP_ENABLED = new Key<>("wifi_wakeup_enabled", false);

    /**
     * Store the default value for {@link #WIFI_WAKEUP_ENABLED} from SettingsProvider.
     */
    public static final Key<Boolean> DEFAULT_WIFI_WAKEUP_ENABLED =
            new Key<>("default_wifi_wakeup_enabled", true);

    /**
     * Store the default value for {@link #WIFI_SCAN_ALWAYS_AVAILABLE} from SettingsProvider.
     */
    public static final Key<Boolean> DEFAULT_WIFI_SCAN_ALWAYS_AVAILABLE =
            new Key<>("default_wifi_scan_always_enabled", false);

    /**
     * Store the default value for {@link #WIFI_NETWORKS_AVAILABLE_NOTIFICATION_ON} from
     * SettingsProvider.
     */
    public static final Key<Boolean> DEFAULT_WIFI_NETWORKS_AVAILABLE_NOTIFICATION_ON =
            new Key<>("default_wifi_networks_available_notification_on", true);

    /******** Wifi shared pref keys ***************/

    private final Context mContext;
    private final Handler mHandler;
    private final SettingsMigrationDataHolder mSettingsMigrationDataHolder;
    private final WifiConfigManager mWifiConfigManager;
    private final FeatureFlags mFeatureFlags;
    private final FrameworkFacade mFrameworkFacade;
    private final UserManager mUserManager;
    private int mCurrentUserId = UserHandle.SYSTEM.getIdentifier();
    private boolean mVerboseLoggingEnabled = false;

    private final Object mLock = new Object();
    @GuardedBy("mLock")
    private final Map<String, Object> mSettings = new HashMap<>();
    @GuardedBy("mLock")
    private final Map<String, Map<OnSettingsChangedListener, Handler>> mListeners =
            new HashMap<>();
    private WifiMigration.SettingsMigrationData mCachedMigrationData = null;

    private boolean mHasNewSharedStoreDataToSerialize = false;
    private boolean mHasNewUserStoreDataToSerialize = false;

    @VisibleForTesting
    final Map<String, Object> mSharedToPrivateMigrationDataHolder = new HashMap<>();
    @VisibleForTesting
    final Set<UserHandle> mUsersNeedMigration = new HashSet<>();

    // Set of all user-specific private setting keys, which require to backup and restore.
    private final Set<Key> mUserPrivateKeys = new HashSet<>();

    /**
     * Interface for a settings change listener.
     * @param <T> Type of the value.
     */
    public interface OnSettingsChangedListener<T> {
        /**
         * Invoked when a particular key settings changes.
         *
         * @param key Key that was changed.
         * @param newValue New value that was assigned to the key.
         */
        void onSettingsChanged(@NonNull Key<T> key, @Nullable T newValue);
    }

    public WifiSettingsConfigStore(@NonNull Context context, @NonNull Handler handler,
            @NonNull SettingsMigrationDataHolder settingsMigrationDataHolder,
            @NonNull WifiConfigManager wifiConfigManager,
            @NonNull WifiConfigStore wifiConfigStore,
            @NonNull FeatureFlags featureFlags,
            @NonNull FrameworkFacade frameworkFacade,
            UserManager userManager) {
        mContext = context;
        mHandler = handler;
        mSettingsMigrationDataHolder = settingsMigrationDataHolder;
        mWifiConfigManager = wifiConfigManager;
        mFeatureFlags = featureFlags;
        mFrameworkFacade = frameworkFacade;
        mUserManager = userManager;

        // Register data store for shared settings.
        wifiConfigStore.registerStoreData(new SharedStoreData());
        // The following keys support B&R regardless of whether per-user setting is enabled or not.
        // When multi-user WiFi is not supported, mUserPrivateKeys only serves as a collection for
        // keys that support B&R.
        mUserPrivateKeys.addAll(Set.of(
                WIFI_WEP_ALLOWED,
                D2D_ALLOWED_WHEN_INFRA_STA_DISABLED
        ));
        if (Environment.isSdkAtLeastC() && mFeatureFlags.multiUserWifiEnhancement()) {
            // Register data store for user-specific settings.
            wifiConfigStore.registerStoreData(new UserStoreData());
            // Register new user-specific keys that don't originally support B&R in old build. When
            // multi-user WiFi is supported, mUserPrivateKeys serves as a collection for both
            // user-specific keys and keys that support B&R.
            mUserPrivateKeys.addAll(Set.of(
                    WIFI_NETWORKS_AVAILABLE_NOTIFICATION_ON,
                    WIFI_WAKEUP_ENABLED,
                    WIFI_SCAN_ALWAYS_AVAILABLE
            ));
        }
    }

    public ArrayList<Key> getAllKeys() {
        return sKeys;
    }

    public Set<Key> getAllBackupRestoreKeys() {
        return mUserPrivateKeys;
    }

    /**
     * Invokes listeners for an iterable collection of keys. For example, it can be used to invoke
     * listeners for all keys ({@link #sKeys}) or user-specific private keys
     * ({@link #mUserPrivateKeys}).
     *
     * @param keys An iterable collection of setting keys, of which their listeners to be invoked.
     */
    private void invokeListenersForKeys(Iterable<Key> keys) {
        synchronized (mLock) {
            for (Key key : keys) {
                invokeListeners(key);
            }
        }
    }

    private <T> void invokeListeners(@NonNull Key<T> key) {
        synchronized (mLock) {
            if (!mSettings.containsKey(key.key)) return;
            Object newValue = mSettings.get(key.key);
            Map<OnSettingsChangedListener, Handler> listeners = mListeners.get(key.key);
            if (listeners == null || listeners.isEmpty()) return;
            for (Map.Entry<OnSettingsChangedListener, Handler> listener
                    : listeners.entrySet()) {
                // Trigger the callback in the appropriate handler.
                listener.getValue().post(() ->
                        listener.getKey().onSettingsChanged(key, newValue));
            }
        }
    }

    /**
     * Trigger config store writes and invoke listeners in the main wifi service looper's handler.
     * If {@code isUserPrivateOnly} is {@code true}, only user-specific private settings will be
     * saved and listeners for those keys will be invoked. Otherwise, all settings (both private and
     * shared settings) will be saved and their listeners will be invoked.
     *
     * @param isUserPrivateOnly Defines the scope of settings to be saved and their listeners to be
     *                          invoked. If {@code true}, only operate on all user-specific private
     *                          settings; otherwise all settings including both private and shared.
     */
    private void triggerSaveToStoreAndInvokeUserPrivateOrAllListeners(boolean isUserPrivateOnly) {
        mHandler.post(() -> {
            Iterable<Key> keys = sKeys;
            if (Environment.isSdkAtLeastC() && mFeatureFlags.multiUserWifiEnhancement()) {
                mHasNewUserStoreDataToSerialize = true;
                if (!isUserPrivateOnly) {
                    mHasNewSharedStoreDataToSerialize = true;
                }
                keys = isUserPrivateOnly ? mUserPrivateKeys : sKeys;
            } else {
                mHasNewSharedStoreDataToSerialize = true;
            }
            mWifiConfigManager.saveToStore();
            invokeListenersForKeys(keys);
        });
    }

    /**
     * Trigger config store writes and invoke listeners in the main wifi service looper's handler.
     */
    private <T> void triggerSaveToStoreAndInvokeListeners(@NonNull Key<T> key) {
        mHandler.post(() -> {
            if (Environment.isSdkAtLeastC() && mFeatureFlags.multiUserWifiEnhancement()
                    && mUserPrivateKeys.contains(key)) {
                mHasNewUserStoreDataToSerialize = true;
            } else {
                mHasNewSharedStoreDataToSerialize = true;
            }
            mWifiConfigManager.saveToStore();

            invokeListeners(key);
        });
    }

    /**
     * Performs a one time migration from Settings.Global values to settings store. Only
     * performed one time if the settings store is empty.
     */
    private void migrateFromSettingsIfNeeded() {
        if (!mSettings.isEmpty()) return; // already migrated.

        mCachedMigrationData = mSettingsMigrationDataHolder.retrieveData();
        if (mCachedMigrationData == null) {
            Log.e(TAG, "No settings data to migrate");
            return;
        }
        Log.i(TAG, "Migrating data out of settings to shared preferences");

        mSettings.put(WIFI_P2P_DEVICE_NAME.key,
                mCachedMigrationData.getP2pDeviceName());
        mSettings.put(WIFI_P2P_PENDING_FACTORY_RESET.key,
                mCachedMigrationData.isP2pFactoryResetPending());
        mSettings.put(WIFI_SCAN_ALWAYS_AVAILABLE.key,
                mCachedMigrationData.isScanAlwaysAvailable());
        mSettings.put(WIFI_SCAN_THROTTLE_ENABLED.key,
                mCachedMigrationData.isScanThrottleEnabled());
        mSettings.put(WIFI_VERBOSE_LOGGING_ENABLED.key,
                mCachedMigrationData.isVerboseLoggingEnabled());
        if (Environment.isSdkAtLeastC() && mFeatureFlags.multiUserWifiEnhancement()) {
            // Ensure the global default values (DEFAULT_WIFI_*) are loaded into mSettings
            // during the initial settings migration.
            loadDefaultValuesFromGlobalSettingsIfNeeded();
        }
        triggerSaveToStoreAndInvokeUserPrivateOrAllListeners(false /* isUserPrivateOnly */);
    }

    /**
     * Loads the default values for specific Wi-Fi settings from {@link Settings.Global}
     * and populates the local settings map if they are not already present.
     */
    private void loadDefaultValuesFromGlobalSettingsIfNeeded() {
        final Map<Key<Boolean>, String> globalSettingsKeyMap = Map.of(
                DEFAULT_WIFI_NETWORKS_AVAILABLE_NOTIFICATION_ON,
                Settings.Global.WIFI_NETWORKS_AVAILABLE_NOTIFICATION_ON,
                DEFAULT_WIFI_WAKEUP_ENABLED,
                Settings.Global.WIFI_WAKEUP_ENABLED,
                DEFAULT_WIFI_SCAN_ALWAYS_AVAILABLE,
                WifiScanAlwaysAvailableSettingsCompatibility
                        .SETTINGS_GLOBAL_WIFI_SCAN_ALWAYS_AVAILABLE
        );
        for (Map.Entry<Key<Boolean>, String> entry : globalSettingsKeyMap.entrySet()) {
            Key<Boolean> configStoreKey = entry.getKey();
            String settingsGlobalKey = entry.getValue();
            if (!mSettings.containsKey(configStoreKey.key)) {
                boolean value = mFrameworkFacade.getIntegerSetting(mContext,
                        settingsGlobalKey, configStoreKey.defaultValue ? 1 : 0) == 1;
                Log.i(TAG, "prepare default value " + value + " for key " + configStoreKey.key);
                mSettings.put(configStoreKey.key, value);
            }
        }
    }

    /**
     * Retrieves data to be migrated from shared to private store on initial DE load and saves in
     * cache. If data is not found from DE, fall back to Settings.Global (if possible) and (if still
     * not found) finally {@link Key#defaultValue}.
     *
     * @param values Settings loaded from DE.
     */
    private void prepareSharedToPrivateMigrationDataHolder(@Nullable Map<String, Object> values) {
        // Retrieve data from deserialize SharedStoreData first.
        if (values != null) {
            for (Key key : mUserPrivateKeys) {
                if (!values.containsKey(key.key)) continue;
                mSharedToPrivateMigrationDataHolder.put(key.key, values.get(key.key));
            }
        }

        // If data is not retrieved from SharedStoreData, fall back to Settings.Global (if possible)
        // then Key.defaultValue for each key under migration.
        final Map<Key, String> keysForMigrationFromSettingsGlobal = Map.of(
                WIFI_NETWORKS_AVAILABLE_NOTIFICATION_ON,
                Settings.Global.WIFI_NETWORKS_AVAILABLE_NOTIFICATION_ON,
                WIFI_WAKEUP_ENABLED,
                Settings.Global.WIFI_WAKEUP_ENABLED,
                WIFI_SCAN_ALWAYS_AVAILABLE,
                WifiScanAlwaysAvailableSettingsCompatibility
                        .SETTINGS_GLOBAL_WIFI_SCAN_ALWAYS_AVAILABLE
        );
        keysForMigrationFromSettingsGlobal.forEach((configStoreKey, settingsGlobalKey) -> {
            if (mSharedToPrivateMigrationDataHolder.containsKey(configStoreKey.key)) {
                // Key already found in DE and there's no need for migration from Settings.Global.
                return;
            }
            // All Key types should be handled in the switch case below with proper methods to fetch
            // from Settings.Global.
            Object value = switch (configStoreKey.defaultValue) {
                case Boolean defaultValue -> mFrameworkFacade.getIntegerSetting(mContext,
                        settingsGlobalKey, defaultValue ? 1 : 0) == 1;
                default -> null;
            };
            mSharedToPrivateMigrationDataHolder.put(configStoreKey.key, value);
        });

        // Keys with no Settings.Global counterpart or already migrated from there will fall back to
        // Key.defaultValue when not retrieved from SharedStoreData.
        final List<Key> keysForMigrationFromDefaultValue = List.of(
                WIFI_WEP_ALLOWED /* no Settings.Global */,
                D2D_ALLOWED_WHEN_INFRA_STA_DISABLED /* no Settings.Global */
        );
        for (Key key : keysForMigrationFromDefaultValue) {
            if (!mSharedToPrivateMigrationDataHolder.containsKey(key.key)) {
                mSharedToPrivateMigrationDataHolder.put(key.key, key.defaultValue);
            }
        }

        // Ensure the global default values (DEFAULT_WIFI_*) are loaded into mSettings.
        // This is necessary because migrateFromSharedToPrivateIfNeeded() relies on these
        // DEFAULT_WIFI_* keys to assign initial values to new users.
        loadDefaultValuesFromGlobalSettingsIfNeeded();
    }

    /**
     * Performs a one-off migration from shared to private store. Only performed once for each user
     * if the CE settings store is empty. Migration data is assumed to be retrieved with
     * {@link #prepareSharedToPrivateMigrationDataHolder(Map)} on initial DE load.
     */
    private void migrateFromSharedToPrivateIfNeeded() {
        UserHandle foregroundUser = UserHandle.of(ActivityManager.getCurrentUser());
        synchronized (mLock) {
            if (mUsersNeedMigration.contains(foregroundUser)) {
                Log.i(TAG, "Migrating data from shared to private store for user id: "
                        + foregroundUser.getIdentifier());
                for (Key key : mUserPrivateKeys) {
                    mSettings.put(key.key, mSharedToPrivateMigrationDataHolder.getOrDefault(key.key,
                            key.defaultValue));
                }
            } else {
                // Don't migrate for new users, but still explicitly reset values to defaultValue so
                // that the corresponding controllers can maintain their state properly on user
                // switch.
                final Map<Key, Key> defaultKeyMap = Map.of(
                        WIFI_NETWORKS_AVAILABLE_NOTIFICATION_ON,
                                DEFAULT_WIFI_NETWORKS_AVAILABLE_NOTIFICATION_ON,
                        WIFI_WAKEUP_ENABLED, DEFAULT_WIFI_WAKEUP_ENABLED,
                        WIFI_SCAN_ALWAYS_AVAILABLE, DEFAULT_WIFI_SCAN_ALWAYS_AVAILABLE
                );
                for (Key key : mUserPrivateKeys) {
                    Key defaultKey = defaultKeyMap.get(key);
                    if (defaultKey != null) {
                        Object defaultValue = get(defaultKey);
                        Log.i(TAG, "Use default value " + defaultValue + " for key - " + key);
                        mSettings.put(key.key, defaultValue);
                    } else {
                        Log.i(TAG, "Use key's default value for key - " + key);
                        mSettings.put(key.key, key.defaultValue);
                    }
                }
            }
            triggerSaveToStoreAndInvokeUserPrivateOrAllListeners(true /* isUserPrivateOnly */);
        }
    }

    /**
     * Store a value to the stored settings.
     *
     * @param key One of the settings keys.
     * @param value Value to be stored.
     */
    public <T> void put(@NonNull Key<T> key, @Nullable T value) {
        synchronized (mLock) {
            mSettings.put(key.key, value);
        }
        triggerSaveToStoreAndInvokeListeners(key);
    }

    /**
     * Retrieve a value from the stored settings.
     *
     * @param key One of the settings keys.
     * @return value stored in settings, defValue if the key does not exist.
     */
    public @Nullable <T> T get(@NonNull Key<T> key) {
        synchronized (mLock) {
            return (T) mSettings.getOrDefault(key.key, key.defaultValue);
        }
    }

    /**
     * Register for settings change listener.
     *
     * @param key One of the settings keys.
     * @param listener Listener to be registered.
     * @param handler Handler to post the listener
     */
    public <T> void registerChangeListener(@NonNull Key<T> key,
            @NonNull OnSettingsChangedListener<T> listener, @NonNull Handler handler) {
        synchronized (mLock) {
            mListeners.computeIfAbsent(
                    key.key, ignore -> new HashMap<>()).put(listener, handler);
        }
    }

    /**
     * Unregister for settings change listener.
     *
     * @param key One of the settings keys.
     * @param listener Listener to be unregistered.
     */
    public <T> void unregisterChangeListener(@NonNull Key<T> key,
            @NonNull OnSettingsChangedListener<T> listener) {
        synchronized (mLock) {
            Map<OnSettingsChangedListener, Handler> listeners = mListeners.get(key.key);
            if (listeners == null || listeners.isEmpty()) {
                Log.e(TAG, "No listeners for " + key);
                return;
            }
            if (listeners.remove(listener) == null) {
                Log.e(TAG, "Unknown listener for " + key);
            }
        }
    }

    /**
     * Parse out the wifi settings from the input xml stream.
     */
    public static Map<String, Object> deserializeSettingsData(
            XmlPullParser in, int outerTagDepth)
            throws XmlPullParserException, IOException {
        Map<String, Object> values = null;
        while (!XmlUtil.isNextSectionEnd(in, outerTagDepth)) {
            String[] valueName = new String[1];
            Object value = XmlUtil.readCurrentValue(in, valueName);
            if (TextUtils.isEmpty(valueName[0])) {
                throw new XmlPullParserException("Missing value name");
            }
            switch (valueName[0]) {
                case XML_TAG_VALUES:
                    values = (Map) value;
                    break;
                default:
                    Log.w(TAG, "Ignoring unknown tag under " + XML_TAG_SECTION_HEADER + ": "
                            + valueName[0]);
                    break;
            }
        }
        return values;
    }

    /**
     * Resets user-session related data, typically invoked on user stop or switch (through
     * {@link UserStoreData#resetData}).
     */
    private void resetUserSessionData() {
        synchronized (mLock) {
            // It is worth noting that removing keys won't update the setting values in their
            // corresponding controllers. Explicitly put a new value and invoke the listener are
            // required, which are conducted in UserStoreData#deserializeData or
            // migrateFromSharedToPrivateIfNeeded.
            for (Key key : mUserPrivateKeys) {
                mSettings.remove(key.key);
            }
            mHasNewUserStoreDataToSerialize = false;
        }
    }

    /**
     * Handles the switch to a different foreground user:
     * - Currently, only updates {@link #mCurrentUserId} to be used by other handlers. The I/O of
     *   all per-user settings data is maintained by {@link WifiConfigManager#handleUserSwitch} and
     *   data reset is called by {@link UserStoreData#resetData} when data for new user is loaded.
     *
     * Need to be called when {@link com.android.server.SystemService#onUserSwitching} is invoked.
     *
     * @param userId The identifier of the new foreground user, after the switch.
     */
    public void handleUserSwitch(int userId) {
        if (mVerboseLoggingEnabled) {
            Log.v(TAG, "Handling user switch for " + userId);
        }
        if (userId == mCurrentUserId) {
            Log.w(TAG, "User already in foreground " + userId);
            return;
        }
        mCurrentUserId = userId;
    }

    /**
     * Handles the stop of foreground user. This is needed to clear any user data. Note that we must
     * call this method after user data is saved by {@link WifiConfigManager#handleUserStop}, which
     * handles the serialization of all StoreData including {@link UserStoreData}.
     *
     * Need to be called when {@link com.android.server.SystemService#onUserStopping} is invoked.
     *
     * @param userId The identifier of the user that stopped.
     */
    public void handleUserStop(int userId) {
        if (mVerboseLoggingEnabled) {
            Log.v(TAG, "Handling user stop for " + userId);
        }
        if (userId == mCurrentUserId) {
            resetUserSessionData();
        }
    }

    /**
     * Enable/disable verbose logging.
     */
    public void enableVerboseLogging(boolean enabled) {
        mVerboseLoggingEnabled = enabled;
    }

    /**
     * Dump output for debugging.
     */
    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println();
        pw.println("Dump of " + TAG);
        pw.println("Settings:");
        for (Map.Entry<String, Object> entry : mSettings.entrySet()) {
            pw.print(entry.getKey());
            pw.print("=");
            pw.println(entry.getValue());
        }
        if (mCachedMigrationData != null) {
            pw.println("Migration data:");
            pw.print(WIFI_P2P_DEVICE_NAME.key);
            pw.print("=");
            pw.println(mCachedMigrationData.getP2pDeviceName());
            pw.print(WIFI_P2P_PENDING_FACTORY_RESET.key);
            pw.print("=");
            pw.println(mCachedMigrationData.isP2pFactoryResetPending());
            pw.print(WIFI_SCAN_ALWAYS_AVAILABLE.key);
            pw.print("=");
            pw.println(mCachedMigrationData.isScanAlwaysAvailable());
            pw.print(WIFI_SCAN_THROTTLE_ENABLED.key);
            pw.print("=");
            pw.println(mCachedMigrationData.isScanThrottleEnabled());
            pw.print(WIFI_VERBOSE_LOGGING_ENABLED.key);
            pw.print("=");
            pw.println(mCachedMigrationData.isVerboseLoggingEnabled());
            pw.println();
        }
        if (Environment.isSdkAtLeastC() && mFeatureFlags.multiUserWifiEnhancement()) {
            pw.println("Migration data for shared to private settings migration:");
            for (Key key : mUserPrivateKeys) {
                pw.print(key.key);
                pw.print("=");
                pw.println(mSharedToPrivateMigrationDataHolder.get(key.key));
            }
        }
    }

    /**
     * Base class to store string key and its default value.
     * @param <T> Type of the value.
     */
    public static class Key<T> {
        public final String key;
        public final T defaultValue;

        private Key(@NonNull String key, T defaultValue) {
            this.key = key;
            this.defaultValue = defaultValue;
            sKeys.add(this);
        }

        @VisibleForTesting
        public String getKey() {
            return key;
        }

        @Override
        public String toString() {
            return "[Key " + key + ", DefaultValue: " + defaultValue + "]";
        }

        @Override
        public boolean equals(Object o) {
            if (o == this) {
                return true;
            }

            // null instanceof [type]" also returns false
            if (!(o instanceof Key)) {
                return false;
            }

            Key anotherKey = (Key) o;
            return Objects.equals(key, anotherKey.key)
                    && Objects.equals(defaultValue, anotherKey.defaultValue);
        }

        @Override
        public int hashCode() {
            return Objects.hash(key, defaultValue);
        }
    }

    /**
     * Store data for persisting the settings data to shared config store.
     */
    public class SharedStoreData implements WifiConfigStore.StoreData {
        @Override
        public void serializeData(XmlSerializer out,
                @Nullable WifiConfigStoreEncryptionUtil encryptionUtil)
                throws XmlPullParserException, IOException {
            synchronized (mLock) {
                if (Environment.isSdkAtLeastC() && mFeatureFlags.multiUserWifiEnhancement()) {
                    // Only serialize shared settings.
                    Map<String, Object> sharedSettings = new HashMap<>(mSettings);
                    for (Key key : mUserPrivateKeys) {
                        sharedSettings.remove(key.key);
                    }
                    XmlUtil.writeNextValue(out, XML_TAG_VALUES, sharedSettings);
                } else {
                    XmlUtil.writeNextValue(out, XML_TAG_VALUES, mSettings);
                }
            }
        }

        @Override
        public void deserializeData(XmlPullParser in, int outerTagDepth,
                @WifiConfigStore.Version int version,
                @Nullable WifiConfigStoreEncryptionUtil encryptionUtil)
                throws XmlPullParserException, IOException {
            if (in == null) {
                // Empty read triggers the migration since it indicates that there is no settings
                // data stored in the settings store.
                migrateFromSettingsIfNeeded();
                return;
            }
            Map<String, Object> values = deserializeSettingsData(in, outerTagDepth);
            if (values != null) {
                synchronized (mLock) {
                    mSettings.putAll(values);
                    if (Environment.isSdkAtLeastC() && mFeatureFlags.multiUserWifiEnhancement()) {
                        // Invoke registered listeners for shared setting keys. Obtain shared keys
                        // by subtracting private keys from all keys.
                        Set<Key> sharedKeys = new HashSet<>(sKeys);
                        sharedKeys.removeAll(mUserPrivateKeys);
                        invokeListenersForKeys(sharedKeys);
                    } else {
                        // Invoke all the registered listeners.
                        invokeListenersForKeys(sKeys);
                    }

                }
            }
            if (Environment.isSdkAtLeastC() && mFeatureFlags.multiUserWifiEnhancement()
                    && mUsersNeedMigration.isEmpty()) {
                mUsersNeedMigration.addAll(mUserManager.getUserHandles(/* excludeDying= */ true));
                prepareSharedToPrivateMigrationDataHolder(values);
            }
        }

        @Override
        public void resetData() {
            synchronized (mLock) {
                mSettings.clear();
                if (Environment.isSdkAtLeastC() && mFeatureFlags.multiUserWifiEnhancement()) {
                    mUsersNeedMigration.clear();
                    mSharedToPrivateMigrationDataHolder.clear();
                    mHasNewSharedStoreDataToSerialize = false;
                }
            }
        }

        @Override
        public boolean hasNewDataToSerialize() {
            return mHasNewSharedStoreDataToSerialize;
        }

        @Override
        public String getName() {
            return XML_TAG_SECTION_HEADER;
        }

        @Override
        public @WifiConfigStore.StoreFileId int getStoreFileId() {
            // Shared general store.
            return WifiConfigStore.STORE_FILE_SHARED_GENERAL;
        }
    }

    /**
     * Store data for persisting the settings data to private user-specific config store.
     */
    public class UserStoreData implements WifiConfigStore.StoreData {
        @Override
        public void serializeData(XmlSerializer out,
                @Nullable WifiConfigStoreEncryptionUtil encryptionUtil)
                throws XmlPullParserException, IOException {
            synchronized (mLock) {
                // Only serialize user-specific settings.
                Map<String, Object> userSettings = new HashMap<>();
                for (Key key : mUserPrivateKeys) {
                    if (mSettings.containsKey(key.key)) {
                        userSettings.put(key.key, mSettings.get(key.key));
                    }
                }
                XmlUtil.writeNextValue(out, XML_TAG_VALUES, userSettings);
            }
        }

        @Override
        public void deserializeData(XmlPullParser in, int outerTagDepth,
                @WifiConfigStore.Version int version,
                @Nullable WifiConfigStoreEncryptionUtil encryptionUtil)
                throws XmlPullParserException, IOException {
            if (in == null) {
                migrateFromSharedToPrivateIfNeeded();
                return;
            }
            Map<String, Object> values = deserializeSettingsData(in, outerTagDepth);
            synchronized (mLock) {
                if (values != null) {
                    mSettings.putAll(values);
                }
                // On user-switch, explicitly reset all private keys that are not loaded from CE to
                // default values and invoke listeners, so that the corresponding values in their
                // controllers can be updated (rather than using the stale value from last user).
                for (Key key : mUserPrivateKeys) {
                    if (values != null && values.containsKey(key.key)) continue;
                    mSettings.put(key.key, key.defaultValue);
                }
                // Invoke the registered listeners for private settings.
                invokeListenersForKeys(mUserPrivateKeys);
            }
        }

        @Override
        public void resetData() {
            resetUserSessionData();
        }

        @Override
        public boolean hasNewDataToSerialize() {
            return mHasNewUserStoreDataToSerialize;
        }

        @Override
        public String getName() {
            return XML_TAG_SECTION_HEADER;
        }

        @Override
        public @WifiConfigStore.StoreFileId int getStoreFileId() {
            // User-specific store.
            return WifiConfigStore.STORE_FILE_USER_GENERAL;
        }
    }
}
