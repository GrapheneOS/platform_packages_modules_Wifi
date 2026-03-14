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

import android.annotation.Nullable;
import android.content.pm.PackageManager;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiContext;
import android.net.wifi.WifiManager;
import android.net.wifi.util.WifiResourceCache;
import android.util.ArraySet;
import android.util.Log;
import android.util.SparseArray;

import androidx.annotation.Keep;

import com.android.internal.annotations.VisibleForTesting;
import com.android.modules.utils.build.SdkLevel;
import com.android.server.wifi.WifiBlocklistMonitor.CarrierSpecificEapFailureConfig;
import com.android.wifi.flags.Flags;
import com.android.wifi.resources.R;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import javax.annotation.concurrent.ThreadSafe;


/** Global wifi service in-memory state that is not persisted. */
@ThreadSafe
public class WifiGlobals {

    private static final String TAG = "WifiGlobals";
    private final WifiContext mContext;
    private final WifiResourceCache mWifiResourceCache;

    private final AtomicInteger mPollRssiIntervalMillis = new AtomicInteger(-1);
    private final AtomicInteger mPollRssiShortIntervalMillis = new AtomicInteger();
    private final AtomicInteger mPollRssiLongIntervalMillis = new AtomicInteger();
    private boolean mIsPollRssiIntervalOverridden = false;
    private final boolean mIsXrPeripheral;
    private final AtomicBoolean mIpReachabilityDisconnectEnabled = new AtomicBoolean(true);
    private final AtomicBoolean mIsBluetoothConnected = new AtomicBoolean(false);
    // Set default to false to check if the value will be overridden by WifiSettingConfigStore.
    private final AtomicBoolean mIsWepAllowed = new AtomicBoolean(false);
    private final AtomicBoolean mIsD2dStaConcurrencySupported = new AtomicBoolean(false);
    private final AtomicInteger mSendDhcpHostnameRestriction = new AtomicInteger();
    private int mPreviouslyConnectedNetworkWrongPasswordThreshold = 3;
    private boolean mIsWpa3SaeUpgradeOffloadEnabled;
    private boolean mIsWpa3SaeH2eSupported;
    private boolean mIsMultiInternetSameBandConnectionAllowed;
    private boolean mIsMultiInternetSameBssidConnectionAllowed;
    private final Map<String, List<String>> mCountryCodeToAfcServers;
    // This is set by WifiManager#setVerboseLoggingEnabled(int).
    private int mVerboseLoggingLevel = WifiManager.VERBOSE_LOGGING_LEVEL_DISABLED;
    private boolean mIsUsingExternalScorer = false;
    private Set<String> mMacRandomizationUnsupportedSsidPrefixes = new ArraySet<>();

    private SparseArray<SparseArray<CarrierSpecificEapFailureConfig>>
            mCarrierSpecificEapFailureConfigMapPerCarrierId = new SparseArray<>();


    public WifiGlobals(WifiContext context) {
        mContext = context;
        mWifiResourceCache = context.getResourceCache();
        mIsWpa3SaeUpgradeOffloadEnabled = mWifiResourceCache
                .getBoolean(R.bool.config_wifiSaeUpgradeOffloadEnabled);
        mPollRssiIntervalMillis.set(mWifiResourceCache.getInteger(
                R.integer.config_wifiPollRssiIntervalMilliseconds));
        mPollRssiShortIntervalMillis.set(mWifiResourceCache.getInteger(
                R.integer.config_wifiPollRssiIntervalMilliseconds));
        mPollRssiLongIntervalMillis.set(mWifiResourceCache.getInteger(
                R.integer.config_wifiPollRssiLongIntervalMilliseconds));
        mPreviouslyConnectedNetworkWrongPasswordThreshold = mWifiResourceCache.getInteger(
                R.integer.config_wifiPreviouslyConnectedNetworkWrongPasswordThreshold);
        mIsWpa3SaeH2eSupported = mWifiResourceCache
                .getBoolean(R.bool.config_wifiSaeH2eSupported);
        mIsMultiInternetSameBandConnectionAllowed = mWifiResourceCache.getBoolean(
                R.bool.config_wifiMultiInternetSameBandConnectionAllowed);
        mIsMultiInternetSameBssidConnectionAllowed = mWifiResourceCache.getBoolean(
                R.bool.config_wifiMultiInternetSameBssidConnectionAllowed);
        mIsXrPeripheral = mContext.getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_XR_PERIPHERAL);
        Set<String> unsupportedSsidPrefixes = new ArraySet<>(mWifiResourceCache.getStringArray(
                R.array.config_wifiForceDisableMacRandomizationSsidPrefixList));
        mCountryCodeToAfcServers = getCountryCodeToAfcServersMap();
        if (!unsupportedSsidPrefixes.isEmpty()) {
            for (String ssid : unsupportedSsidPrefixes) {
                String cleanedSsid = ssid.length() > 1 && (ssid.charAt(0) == '"')
                        && (ssid.charAt(ssid.length() - 1) == '"')
                        ? ssid.substring(0, ssid.length() - 1) : ssid;
                mMacRandomizationUnsupportedSsidPrefixes.add(cleanedSsid);
            }
        }
        loadCarrierSpecificEapFailureConfigMap();
    }

    /**
     * Gets the CarrierSpecificEapFailureConfig applicable for the carrierId and eapFailureReason.
     * @param carrierId the carrier ID
     * @param eapFailureReason EAP failure reason
     * @return The applicable CarrierSpecificEapFailureConfig, or null if there's no data for this
     * particular combination of carrierId and eapFailureReason.
     */
    public @Nullable CarrierSpecificEapFailureConfig getCarrierSpecificEapFailureConfig(
            int carrierId, int eapFailureReason) {
        if (!mCarrierSpecificEapFailureConfigMapPerCarrierId.contains(carrierId)) {
            return null;
        }
        return mCarrierSpecificEapFailureConfigMapPerCarrierId.get(carrierId).get(eapFailureReason);
    }

    /**
     * Utility method for unit testing.
     */
    public @VisibleForTesting int getCarrierSpecificEapFailureConfigMapSize() {
        return mCarrierSpecificEapFailureConfigMapPerCarrierId.size();
    }

    private void loadCarrierSpecificEapFailureConfigMap() {
        String[] eapFailureOverrides = mWifiResourceCache.getStringArray(
                R.array.config_wifiEapFailureConfig);
        if (eapFailureOverrides == null) {
            return;
        }
        for (String line : eapFailureOverrides) {
            if (line == null) {
                continue;
            }
            String[] items = line.split(",");
            if (items.length != 5) {
                // error case. Should have exactly 5 items.
                Log.e(TAG, "Failed to parse eapFailureOverrides line=" + line);
                continue;
            }
            try {
                int carrierId = Integer.parseInt(items[0].trim());
                int eapFailureCode = Integer.parseInt(items[1].trim());
                int displayDialogue = Integer.parseInt(items[2].trim());
                int disableThreshold = Integer.parseInt(items[3].trim());
                int disableDurationMinutes = Integer.parseInt(items[4].trim());
                if (!mCarrierSpecificEapFailureConfigMapPerCarrierId.contains(carrierId)) {
                    mCarrierSpecificEapFailureConfigMapPerCarrierId.put(carrierId,
                            new SparseArray<>());
                }
                SparseArray<CarrierSpecificEapFailureConfig> perEapFailureMap =
                        mCarrierSpecificEapFailureConfigMapPerCarrierId.get(carrierId);
                perEapFailureMap.put(eapFailureCode, new CarrierSpecificEapFailureConfig(
                        disableThreshold, disableDurationMinutes * 60 * 1000, displayDialogue > 0));
            } catch (Exception e) {
                // failure to parse. Something is wrong with the config.
                Log.e(TAG, "Parsing eapFailureOverrides line=" + line
                        + ". Exception occurred:" + e);
            }
        }
    }

    private Map<String, List<String>> getCountryCodeToAfcServersMap() {
        Map<String, List<String>> countryCodeToAfcServers = new HashMap<>();
        String[] countryCodeToAfcServersFromConfig = mWifiResourceCache.getStringArray(
                R.array.config_wifiAfcServerUrlsForCountry);

        if (countryCodeToAfcServersFromConfig == null) {
            return countryCodeToAfcServers;
        }

        // each entry should be of the form: countryCode,url1,url2...
        for (String entry : countryCodeToAfcServersFromConfig) {
            String[] countryAndUrls = entry.split(",");

            // if no servers are specified for a country, then continue to the next entry
            if (countryAndUrls.length < 2) {
                continue;
            }
            countryCodeToAfcServers.put(countryAndUrls[0], Arrays.asList(Arrays.copyOfRange(
                    countryAndUrls, 1, countryAndUrls.length)));
        }
        return countryCodeToAfcServers;
    }

    public Set<String> getMacRandomizationUnsupportedSsidPrefixes() {
        return mMacRandomizationUnsupportedSsidPrefixes;
    }

    /** Get the interval between RSSI polls, in milliseconds. */
    @Keep
    public int getPollRssiIntervalMillis() {
        return mPollRssiIntervalMillis.get();
    }

    /** Set the interval between RSSI polls, in milliseconds. */
    public void setPollRssiIntervalMillis(int newPollIntervalMillis) {
        mPollRssiIntervalMillis.set(newPollIntervalMillis);
    }

    /** Returns whether CMD_IP_REACHABILITY_LOST events should trigger disconnects. */
    public boolean getIpReachabilityDisconnectEnabled() {
        return mIpReachabilityDisconnectEnabled.get();
    }

    /**
     * Returns a list of AFC server URLs for a country, or null if AFC is not available in that
     * country.
     */
    public @Nullable List<String> getAfcServerUrlsForCountry(String countryCode) {
        return mCountryCodeToAfcServers.get(countryCode);
    }

    /**
     * Returns whether this device supports AFC.
     */
    public boolean isAfcSupportedOnDevice() {
        return mWifiResourceCache.getBoolean(R.bool.config_wifiAfcSupported)
                && mWifiResourceCache.getBoolean(R.bool.config_wifiSoftap6ghzSupported)
                && mWifiResourceCache.getBoolean(R.bool.config_wifi6ghzSupport);
    }

    /** Sets whether CMD_IP_REACHABILITY_LOST events should trigger disconnects. */
    @Keep
    public void setIpReachabilityDisconnectEnabled(boolean enabled) {
        mIpReachabilityDisconnectEnabled.set(enabled);
    }

    /** Set whether bluetooth is enabled. */
    public void setBluetoothEnabled(boolean isEnabled) {
        // If BT was connected and then turned off, there is no CONNECTION_STATE_CHANGE message.
        // So set mIsBluetoothConnected to false if we get a bluetooth disable while connected.
        // But otherwise, Bluetooth being turned on doesn't mean that we're connected.
        if (!isEnabled) {
            mIsBluetoothConnected.set(false);
        }
    }

    /** Set whether bluetooth is connected. */
    public void setBluetoothConnected(boolean isConnected) {
        mIsBluetoothConnected.set(isConnected);
    }

    /** Get whether bluetooth is connected */
    public boolean isBluetoothConnected() {
        return mIsBluetoothConnected.get();
    }

    /**
     * Helper method to check if Connected MAC Randomization is supported - onDown events are
     * skipped if this feature is enabled (b/72459123).
     *
     * @return boolean true if Connected MAC randomization is supported, false otherwise
     */
    public boolean isConnectedMacRandomizationEnabled() {
        return mWifiResourceCache.getBoolean(
                R.bool.config_wifi_connected_mac_randomization_supported);
    }

    /**
     * Helper method to check if WEP networks are deprecated.
     *
     * @return boolean true if WEP networks are deprecated, false otherwise.
     */
    public boolean isWepDeprecated() {
        return mWifiResourceCache.getBoolean(R.bool.config_wifiWepDeprecated)
                || (mWifiResourceCache.getBoolean(R.bool.config_wifiWepAllowedControlSupported)
                && !mIsWepAllowed.get());
    }

    /**
     * Helper method to check if WEP networks are supported.
     *
     * @return boolean true if WEP networks are supported, false otherwise.
     */
    public boolean isWepSupported() {
        return !mWifiResourceCache.getBoolean(R.bool.config_wifiWepDeprecated
        );
    }

    /**
     * Helper method to check if WPA-Personal networks are deprecated.
     *
     * @return boolean true if WPA-Personal networks are deprecated, false otherwise.
     */
    public boolean isWpaPersonalDeprecated() {
        return mWifiResourceCache
                .getBoolean(R.bool.config_wifiWpaPersonalDeprecated);
    }

    /**
     * Get the configuration for whether Multi-internet are allowed to
     * connect simultaneously to both 5GHz high and 5GHz low.
     */
    public boolean isSupportMultiInternetDual5G() {
        return mWifiResourceCache.getBoolean(
                R.bool.config_wifiAllowMultiInternetConnectDual5GFrequency);
    }

    /**
     * Get number of repeated NUD failures needed to disable a network.
     */
    public int getRepeatedNudFailuresThreshold() {
        return mWifiResourceCache
                .getInteger(R.integer.config_wifiDisableReasonRepeatedNudFailuresThreshold);
    }

    /**
     * Get the time window in millis to count for repeated NUD failures.
     */
    public int getRepeatedNudFailuresWindowMs() {
        return mWifiResourceCache
                .getInteger(R.integer.config_wifiDisableReasonRepeatedNudFailuresWindowMs);
    }

    /**
     * Helper method to check if the device may not connect to the configuration
     * due to deprecated security type
     */
    public boolean isDeprecatedSecurityTypeNetwork(@Nullable WifiConfiguration config) {
        if (config == null) {
            return false;
        }
        if (isWepDeprecated() && config.isSecurityType(WifiConfiguration.SECURITY_TYPE_WEP)) {
            return true;
        }
        if (isWpaPersonalDeprecated() && config.isWpaPersonalOnlyConfiguration()) {
            return true;
        }
        return false;
    }

    /**
     * Help method to check if WPA3 SAE auto-upgrade is enabled.
     *
     * @return boolean true if auto-upgrade is enabled, false otherwise.
     */
    public boolean isWpa3SaeUpgradeEnabled() {
        return mWifiResourceCache
                .getBoolean(R.bool.config_wifiSaeUpgradeEnabled);
    }

    /**
     * Help method to check if WPA3 SAE auto-upgrade offload is enabled.
     *
     * @return boolean true if auto-upgrade offload is enabled, false otherwise.
     */
    public boolean isWpa3SaeUpgradeOffloadEnabled() {
        return mIsWpa3SaeUpgradeOffloadEnabled;
    }

    /**
     * Helper method to enable WPA3 SAE auto-upgrade offload based on the device capability for
     * CROSS-AKM support.
     */
    public void setWpa3SaeUpgradeOffloadEnabled() {
        Log.d(TAG, "Device supports CROSS-AKM feature - Enable WPA3 SAE auto-upgrade offload");
        mIsWpa3SaeUpgradeOffloadEnabled = true;
    }


    /**
     * Help method to check if OWE auto-upgrade is enabled.
     *
     * @return boolean true if auto-upgrade is enabled, false otherwise.
     */
    public boolean isOweUpgradeEnabled() {
        // OWE auto-upgrade is supported on S or newer releases.
        return SdkLevel.isAtLeastS() && mWifiResourceCache
                .getBoolean(R.bool.config_wifiOweUpgradeEnabled);
    }

    /**
     * Help method to check if the setting to flush ANQP cache when Wi-Fi is toggled off.
     *
     * @return boolean true to flush ANQP cache on Wi-Fi toggle off event, false otherwise.
     */
    public boolean flushAnqpCacheOnWifiToggleOffEvent() {
        return mWifiResourceCache
                .getBoolean(R.bool.config_wifiFlushAnqpCacheOnWifiToggleOffEvent);
    }

    /*
     * Help method to check if WPA3 SAE Hash-to-Element is supported on this device.
     *
     * @return boolean true if supported;otherwise false.
     */
    public boolean isWpa3SaeH2eSupported() {
        return mIsWpa3SaeH2eSupported;
    }

    public boolean isMultiInternetSameBandConnectionAllowed() {
        return mIsMultiInternetSameBandConnectionAllowed;
    }

    public boolean isMultiInternetSameBssidConnectionAllowed() {
        return mIsMultiInternetSameBssidConnectionAllowed;
    }

    /**
     * Helper method to enable WPA3 SAE Hash-to-Element support based on the supplicant aidl
     * version.
     */
    public void enableWpa3SaeH2eSupport() {
        mIsWpa3SaeH2eSupported = true;
    }

    /**
     * Record the verbose logging level
     */
    public void setVerboseLoggingLevel(int level) {
        mVerboseLoggingLevel = level;
    }

    /** Return the currently set verbose logging level. */
    public int getVerboseLoggingLevel() {
        return mVerboseLoggingLevel;
    }

    /** Check if show key verbose logging mode is enabled. */
    public boolean getShowKeyVerboseLoggingModeEnabled() {
        return mVerboseLoggingLevel == WifiManager.VERBOSE_LOGGING_LEVEL_ENABLED_SHOW_KEY;
    }

    /** Set whether the external scorer is being used **/
    public void setUsingExternalScorer(boolean isUsingExternalScorer) {
        mIsUsingExternalScorer = isUsingExternalScorer;
    }

    /** Get whether the external scorer is being used **/
    public boolean isUsingExternalScorer() {
        return mIsUsingExternalScorer;
    }

    /** Get the prefix of the default wifi p2p device name. */
    public String getWifiP2pDeviceNamePrefix() {
        return mWifiResourceCache
                .getString(R.string.config_wifiP2pDeviceNamePrefix);
    }

    /** Get the number of the default wifi p2p device name postfix digit. */
    public int getWifiP2pDeviceNamePostfixNumDigits() {
        return mWifiResourceCache
                .getInteger(R.integer.config_wifiP2pDeviceNamePostfixNumDigits);
    }

    /** Get the number of log records to maintain. */
    public int getClientModeImplNumLogRecs() {
        return  mWifiResourceCache.getInteger(R.integer.config_wifiClientModeImplNumLogRecs);
    }

    /** Get whether to use the saved factory MAC address when available **/
    public boolean isSaveFactoryMacToConfigStoreEnabled() {
        return mWifiResourceCache
                .getBoolean(R.bool.config_wifiSaveFactoryMacToWifiConfigStore);
    }

    /** Get the low score threshold to do scan for MBB when external scorer is not used. **/
    public int getWifiLowConnectedScoreThresholdToTriggerScanForMbb() {
        return mWifiResourceCache.getInteger(
                R.integer.config_wifiLowConnectedScoreThresholdToTriggerScanForMbb);
    }

    /** Get the minimum period between the extra scans triggered for MBB when score is low **/
    public int getWifiLowConnectedScoreScanPeriodSeconds() {
        return mWifiResourceCache.getInteger(
                R.integer.config_wifiLowConnectedScoreScanPeriodSeconds);
    }

    /** Get whether or not insecure enterprise configuration is allowed. */
    public boolean isInsecureEnterpriseConfigurationAllowed() {
        return mWifiResourceCache.getBoolean(
                R.bool.config_wifiAllowInsecureEnterpriseConfigurationsForSettingsAndSUW);
    }

    /** Get whether or not P2P MAC randomization is supported */
    public boolean isP2pMacRandomizationSupported() {
        return mWifiResourceCache.getBoolean(
                R.bool.config_wifi_p2p_mac_randomization_supported);
    }

    public int getPreviouslyConnectedNetworkWrongPasswordThreshold() {
        return mPreviouslyConnectedNetworkWrongPasswordThreshold;
    }

    /** Get the regular (short) interval between RSSI polls, in milliseconds. */
    public int getPollRssiShortIntervalMillis() {
        return mPollRssiShortIntervalMillis.get();
    }

    /** Set the regular (short) interval between RSSI polls, in milliseconds. */
    public void setPollRssiShortIntervalMillis(int newPollIntervalMillis) {
        mPollRssiShortIntervalMillis.set(newPollIntervalMillis);
    }

    /**
     * Get the long interval between RSSI polls, in milliseconds. The long interval is to
     * reduce power consumption of the polls. This value should be greater than the regular
     * interval.
     */
    public int getPollRssiLongIntervalMillis() {
        return mPollRssiLongIntervalMillis.get();
    }

    /**
     * Set the long interval between RSSI polls, in milliseconds. The long interval is to
     * reduce power consumption of the polls. This value should be greater than the regular
     * interval.
     */
    public void setPollRssiLongIntervalMillis(int newPollIntervalMillis) {
        mPollRssiLongIntervalMillis.set(newPollIntervalMillis);
    }

    /**
     * Get the RSSI threshold for client mode RSSI monitor, in dBm. If the device is stationary
     * and current RSSI >= Threshold + Hysteresis value, set long interval and enable RSSI
     * monitoring using the RSSI threshold. If device is non-stationary or current RSSI <=
     * Threshold, set regular interval and disable RSSI monitoring.
     */
    public int getClientRssiMonitorThresholdDbm() {
        return mWifiResourceCache.getInteger(
                R.integer.config_wifiClientRssiMonitorThresholdDbm);
    }

    /**
     * Get the hysteresis value in dB for the client mode RSSI monitor threshold. It can avoid
     * frequent switch between regular and long polling intervals.
     */
    public int getClientRssiMonitorHysteresisDb() {
        return mWifiResourceCache.getInteger(
                R.integer.config_wifiClientRssiMonitorHysteresisDb);
    }

    /**
     * Get whether adjusting the RSSI polling interval between regular and long intervals
     * is enabled.
     */
    public boolean isAdjustPollRssiIntervalEnabled() {
        return mWifiResourceCache.getBoolean(
                R.bool.config_wifiAdjustPollRssiIntervalEnabled);
    }

    /** Set whether the RSSI polling interval is overridden to a fixed value **/
    public void setPollRssiIntervalOverridden(boolean isPollRssiIntervalOverridden) {
        mIsPollRssiIntervalOverridden = isPollRssiIntervalOverridden;
    }

    /** Get whether the RSSI polling interval is overridden to a fixed value **/
    public boolean isPollRssiIntervalOverridden() {
        return mIsPollRssiIntervalOverridden;
    }

    /**
     * Get whether hot-plugging an interface will trigger a restart of the wifi stack.
     */
    public boolean isWifiInterfaceAddedSelfRecoveryEnabled() {
        return  mWifiResourceCache.getBoolean(
                R.bool.config_wifiInterfaceAddedSelfRecoveryEnabled);
    }

    /**
     * Get whether background scan is supported.
     */
    public boolean isBackgroundScanSupported() {
        return mWifiResourceCache
                .getBoolean(R.bool.config_wifi_background_scan_support
                );
    };

    /**
     * Get whether software pno is enabled.
     */
    public boolean isSwPnoEnabled() {
        return mWifiResourceCache
                .getBoolean(R.bool.config_wifiSwPnoEnabled);
    };

    /**
     * Get whether to temporarily disable a unwanted network that has low RSSI.
     */
    public boolean disableUnwantedNetworkOnLowRssi() {
        return mWifiResourceCache.getBoolean(
                R.bool.config_wifiDisableUnwantedNetworkOnLowRssi);
    }

    /**
     * Get whether to disable NUD disconnects for WAPI configurations in a specific CC.
     */
    public boolean disableNudDisconnectsForWapiInSpecificCc() {
        return mWifiResourceCache.getBoolean(
                R.bool.config_wifiDisableNudDisconnectsForWapiInSpecificCc);
    }

    /**
     * Get the threshold to use for blocking a network due to NETWORK_NOT_FOUND_EVENT failure.
     */
    public int getNetworkNotFoundEventThreshold() {
        return mWifiResourceCache.getInteger(
                R.integer.config_wifiNetworkNotFoundEventThreshold);
    }

    /**
     * Set whether wep network is allowed by user.
     */
    public void setWepAllowed(boolean isAllowed) {
        mIsWepAllowed.set(isAllowed);
    }

    /**
     * Get whether or not wep network is allowed by user.
     */
    public boolean isWepAllowed() {
        return mIsWepAllowed.get();
    }

    /**
     * Set whether the device supports device-to-device + STA concurrency.
     */
    public void setD2dStaConcurrencySupported(boolean isSupported) {
        mIsD2dStaConcurrencySupported.set(isSupported);
    }

    /**
     * Returns whether the device supports device-to-device when infra STA is disabled.
     */
    public boolean isD2dSupportedWhenInfraStaDisabled() {
        if (Flags.allowD2dWithoutStaOnXr()) {
            if (!mWifiResourceCache
                    .getBoolean(R.bool.config_wifiD2dAllowedControlSupportedWhenInfraStaDisabled)) {
                return false;
            }
            if (mIsXrPeripheral) {
                // Allowed for XR device
                return true;
            }
            // For non-XR device, check if concurrency supported.
            return !mIsD2dStaConcurrencySupported.get();
        }
        return mWifiResourceCache
                .getBoolean(R.bool.config_wifiD2dAllowedControlSupportedWhenInfraStaDisabled)
                && !mIsD2dStaConcurrencySupported.get();
    }

    public boolean isNetworkSelectionSetTargetBssid() {
        return mWifiResourceCache.getBoolean(R.bool.config_wifiNetworkSelectionSetTargetBssid);
    }

    /**
     * Set the global dhcp hostname restriction.
     */
    public void setSendDhcpHostnameRestriction(
            @WifiManager.SendDhcpHostnameRestriction int restriction) {
        mSendDhcpHostnameRestriction.set(restriction);
    }

    /**
     * Get the global dhcp hostname restriction.
     */
    @WifiManager.SendDhcpHostnameRestriction
    public int getSendDhcpHostnameRestriction() {
        return mSendDhcpHostnameRestriction.get();
    }

    /**
     * Get the maximum Wifi temporary disable duration.
     */
    public long getWifiConfigMaxDisableDurationMs() {
        return mWifiResourceCache
                .getInteger(R.integer.config_wifiDisableTemporaryMaximumDurationMs);
    }

    /**
     * Returns whether device support Wi-Fi 7 multi-link device Soft Ap.
     */
    public boolean isMLDApSupported() {
        int numberOfMLDSupported = mWifiResourceCache
                .getInteger(R.integer.config_wifiSoftApMaxNumberMLDSupported);
        return numberOfMLDSupported != 0;
    }

    /**
     * Returns the internal scorer type. 0 is the Velocity scorer a.k.a. traditional AOSP scorer.
     * 1 is the ML(Machine Learning) scorer.
     */
    public int getInternalScorerType() {
        return mWifiResourceCache.getInteger(R.integer.config_internalScorerType);
    }

    /**
     * Returns true if the external scorer should take priority over the internal scorer.
     */
    public boolean prioritizeExternalScorer() {
        return mWifiResourceCache.getBoolean(R.bool.config_prioritizeExternalScorer);
    }

    /**
     * Returns true if the pre-evaluation is enabled.
     *
     * <p>Pre-evaluation is a feature that allows the scorer to evaluate networks before the
     * connection. This helps to avoid unnecessary connection attempts to networks that are known to
     * be bad.
     */
    public boolean isPreEvaluationEnabled() {
        return mWifiResourceCache.getBoolean(R.bool.config_preEvaluationEnabled);
    }

    /** Dump method for debugging */
    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println("Dump of WifiGlobals");
        pw.println("mPollRssiIntervalMillis=" + mPollRssiIntervalMillis.get());
        pw.println("mIsPollRssiIntervalOverridden=" + mIsPollRssiIntervalOverridden);
        pw.println("mPollRssiShortIntervalMillis=" + mPollRssiShortIntervalMillis.get());
        pw.println("mPollRssiLongIntervalMillis=" + mPollRssiLongIntervalMillis.get());
        pw.println("mIpReachabilityDisconnectEnabled=" + mIpReachabilityDisconnectEnabled.get());
        pw.println("mIsBluetoothConnected=" + mIsBluetoothConnected.get());
        pw.println("mIsWpa3SaeUpgradeOffloadEnabled=" + mIsWpa3SaeUpgradeOffloadEnabled);
        pw.println("mIsUsingExternalScorer="
                + mIsUsingExternalScorer);
        pw.println("mIsWepAllowed=" + mIsWepAllowed.get());
        pw.println("IsD2dSupportedWhenInfraStaDisabled="
                + isD2dSupportedWhenInfraStaDisabled());
        if (Flags.allowD2dWithoutStaOnXr()) {
            pw.println("mIsXrPeripheral=" + mIsXrPeripheral);
        }
        pw.println("mIsWpa3SaeH2eSupported=" + mIsWpa3SaeH2eSupported);
        pw.println("mIsMultiInternetSameBandConnectionAllowed="
                + mIsMultiInternetSameBandConnectionAllowed);
        pw.println("mIsMultiInternetSameBssidConnectionAllowed="
                + mIsMultiInternetSameBssidConnectionAllowed);
        for (int i = 0; i < mCarrierSpecificEapFailureConfigMapPerCarrierId.size(); i++) {
            int carrierId = mCarrierSpecificEapFailureConfigMapPerCarrierId.keyAt(i);
            SparseArray<CarrierSpecificEapFailureConfig> perFailureMap =
                    mCarrierSpecificEapFailureConfigMapPerCarrierId.valueAt(i);
            for (int j = 0; j < perFailureMap.size(); j++) {
                int eapFailureCode = perFailureMap.keyAt(j);
                pw.println("carrierId=" + carrierId
                        + ", eapFailureCode=" + eapFailureCode
                        + ", displayNotification=" + perFailureMap.valueAt(j).displayNotification
                        + ", threshold=" + perFailureMap.valueAt(j).threshold
                        + ", durationMs=" + perFailureMap.valueAt(j).durationMs);
            }
        }
        pw.println("mSendDhcpHostnameRestriction=" + mSendDhcpHostnameRestriction.get());
    }
}
