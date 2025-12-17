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

package com.android.server.wifi.nl80211;

import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_ATTR_CHANNEL_WIDTH;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_ATTR_IFINDEX;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_ATTR_MAC;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_ATTR_REG_ALPHA2;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_ATTR_REG_TYPE;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_ATTR_WIPHY_FREQ;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_CHAN_WIDTH_160;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_CHAN_WIDTH_20;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_CHAN_WIDTH_20_NOHT;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_CHAN_WIDTH_320;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_CHAN_WIDTH_40;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_CHAN_WIDTH_80;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_CHAN_WIDTH_80P80;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_CMD_ASSOCIATE;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_CMD_CH_SWITCH_NOTIFY;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_CMD_DEL_STATION;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_CMD_DISASSOCIATE;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_CMD_NEW_SCAN_RESULTS;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_CMD_NEW_STATION;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_CMD_REG_CHANGE;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_CMD_SCAN_ABORTED;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_CMD_SCHED_SCAN_RESULTS;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_CMD_SCHED_SCAN_STOPPED;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_CMD_WIPHY_REG_CHANGE;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_REGDOM_TYPE_COUNTRY;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_REGDOM_TYPE_CUSTOM_WORLD;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_REGDOM_TYPE_INTERSECTION;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_REGDOM_TYPE_WORLD;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_SCAN_FLAG_COLOCATED_6GHZ;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_SCAN_FLAG_HIGH_ACCURACY;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_SCAN_FLAG_LOW_POWER;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_SCAN_FLAG_LOW_SPAN;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_SCAN_FLAG_RANDOM_ADDR;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.net.MacAddress;
import android.net.wifi.ScanResult;
import android.net.wifi.SoftApInfo;
import android.net.wifi.WifiAnnotations;
import android.net.wifi.WifiScanner;
import android.net.wifi.WifiSsid;
import android.net.wifi.nl80211.NativeWifiClient;
import android.net.wifi.nl80211.WifiNl80211Manager;
import android.os.Bundle;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.Log;
import android.util.SparseIntArray;

import com.android.internal.annotations.VisibleForTesting;
import com.android.server.wifi.SelfRecovery;
import com.android.server.wifi.WifiInjector;
import com.android.server.wifi.util.NetdWrapper;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;

/**
 * Provides functionalities that are implemented natively using Nl80211.
 */
public class Nl80211Native {
    private static final String TAG = "Nl80211Native";
    private boolean mVerboseLoggingEnabled;

    private static final int MAX_SSID_LENGTH = 32;
    @VisibleForTesting
    static final int ENODEV_RESTART_THRESHOLD = 3;
    private static final int PERCENT_NETWORKS_WITH_FREQ_FOR_PNO_SCAN = 30;
    private static final int[] PNO_SCAN_DEFAULT_FREQS_2G =
            {2412, 2417, 2422, 2427, 2432, 2437, 2447, 2452, 2457, 2462};
    private static final int[] PNO_SCAN_DEFAULT_FREQS_5G =
            {5180, 5200, 5220, 5240, 5745, 5765, 5785, 5805};

    /**
     * Wrapper class to store all the information for a client mode interface.
     */
    @VisibleForTesting
    static class ClientInterfaceInfo {
        public final @NonNull String ifName;
        public final int ifIndex;
        public boolean associated;
        public boolean scanning;
        public boolean pnoScanStarted;
        public int enodevCounter;
        public Nl80211Utils.WiphyInfo wiphyInfo;
        public final @NonNull Executor scanCallbackExecutor;
        public final @NonNull ScanEventCallback scanEventCallback;
        public final @NonNull ScanEventCallback pnoScanEventCallback;

        ClientInterfaceInfo(@NonNull String ifName, int ifIndex,
                @NonNull Nl80211Utils.WiphyInfo wiphyInfo,
                @NonNull Executor executor,
                @NonNull ScanEventCallback scanCallback,
                @NonNull ScanEventCallback pnoScanCallback) {
            this.ifName = ifName;
            this.ifIndex = ifIndex;
            this.wiphyInfo = wiphyInfo;
            this.scanCallbackExecutor = executor;
            this.scanEventCallback = scanCallback;
            this.pnoScanEventCallback = pnoScanCallback;
        }
    }

    /**
     * Wrapper class to store all the information for an AP mode interface.
     */
    @VisibleForTesting
    static class ApInterfaceInfo {
        public final @NonNull String ifName;
        public final int ifIndex;
        public @Nullable WifiNl80211Manager.SoftApCallback callback;
        public @Nullable Executor executor;

        ApInterfaceInfo(@NonNull String ifName, int ifIndex) {
            this.ifName = ifName;
            this.ifIndex = ifIndex;
        }
    }

    private final @NonNull Nl80211Proxy mNl80211Proxy;
    private final @NonNull Nl80211Utils mNl80211Utils;
    private final @NonNull NetdWrapper mNetdWrapper;
    private final @NonNull WifiNl80211Manager mWificondManager;
    private final @NonNull WifiInjector mWifiInjector;
    private final boolean mUseWificond;
    private boolean mUseNl80211Override;
    private boolean mIsInitialized;
    private final @NonNull  Map<String, Integer> mActiveIfaceToWiphyIndex = new ArrayMap<>();
    private final @NonNull SparseIntArray mBandToWiphyIndex = new SparseIntArray();
    private final @NonNull Map<String, ClientInterfaceInfo> mClientInterfaceInfos =
            new ArrayMap<>();
    private final @NonNull Map<String, ApInterfaceInfo> mApInterfaceInfos = new ArrayMap<>();
    private @NonNull String mCountryCode = "";
    private final @NonNull Map<CountryCodeChangedListener, Executor> mCountryCodeChangedListeners =
            new ArrayMap<>();

    private Nl80211BroadcastMonitor.Nl80211BroadcastCallback mNewScanResultsCallback =
            (command, message) -> {
                if (mVerboseLoggingEnabled) {
                    Log.d(TAG, "Received NL80211 broadcast: " + message);
                }

                ClientInterfaceInfo clientIfaceInfo = getClientInterfaceInfoForBroadcast(message);
                if (clientIfaceInfo == null) return;

                if (!clientIfaceInfo.scanning) {
                    Log.i(TAG, "Received external scan result notification from kernel.");
                }
                clientIfaceInfo.scanning = false;

                Executor executor = clientIfaceInfo.scanCallbackExecutor;
                ScanEventCallback scanCallback = clientIfaceInfo.scanEventCallback;

                executor.execute(() -> scanCallback.onScanResultReady());
            };

    private Nl80211BroadcastMonitor.Nl80211BroadcastCallback mScanAbortedCallback =
            (command, message) -> {
                if (mVerboseLoggingEnabled) {
                    Log.d(TAG, "Received NL80211 broadcast: " + message);
                }

                ClientInterfaceInfo clientIfaceInfo = getClientInterfaceInfoForBroadcast(message);
                if (clientIfaceInfo == null) return;

                if (!clientIfaceInfo.scanning) {
                    Log.i(TAG, "Received external scan result notification from kernel.");
                }
                clientIfaceInfo.scanning = false;

                Executor executor = clientIfaceInfo.scanCallbackExecutor;
                ScanEventCallback scanCallback = clientIfaceInfo.scanEventCallback;

                // onScanFailed() is missing to match wificond implementation.
                executor.execute(() -> scanCallback.onScanFailed(
                        WifiScanner.REASON_ABORT));
            };

    private Nl80211BroadcastMonitor.Nl80211BroadcastCallback mSchedScanResultsCallback =
            (command, message) -> {
                if (mVerboseLoggingEnabled) {
                    Log.d(TAG, "Received NL80211 broadcast: " + message);
                }

                ClientInterfaceInfo clientIfaceInfo = getClientInterfaceInfoForBroadcast(message);
                if (clientIfaceInfo == null) return;

                Executor executor = clientIfaceInfo.scanCallbackExecutor;
                ScanEventCallback pnoScanCallback = clientIfaceInfo.pnoScanEventCallback;

                executor.execute(() -> pnoScanCallback.onScanResultReady());
            };

    private Nl80211BroadcastMonitor.Nl80211BroadcastCallback mSchedScanStoppedCallback =
            (command, message) -> {
                if (mVerboseLoggingEnabled) {
                    Log.d(TAG, "Received NL80211 broadcast: " + message);
                }

                ClientInterfaceInfo clientIfaceInfo = getClientInterfaceInfoForBroadcast(message);
                if (clientIfaceInfo == null) return;
                clientIfaceInfo.pnoScanStarted = false;

                Executor executor = clientIfaceInfo.scanCallbackExecutor;
                ScanEventCallback pnoScanCallback = clientIfaceInfo.pnoScanEventCallback;

                executor.execute(() -> pnoScanCallback.onScanFailed());
                // onScanFailed(int) is missing to match wificond implementation.
            };

    private Nl80211BroadcastMonitor.Nl80211BroadcastCallback mAssociateCallback =
            (command, message) -> {
                if (mVerboseLoggingEnabled) {
                    Log.d(TAG, "Received NL80211 broadcast: " + message);
                }

                ClientInterfaceInfo clientIfaceInfo = getClientInterfaceInfoForBroadcast(message);
                if (clientIfaceInfo == null) return;

                clientIfaceInfo.associated = true;
            };

    private Nl80211BroadcastMonitor.Nl80211BroadcastCallback mDisassociateCallback =
            (command, message) -> {
                if (mVerboseLoggingEnabled) {
                    Log.d(TAG, "Received NL80211 broadcast: " + message);
                }

                ClientInterfaceInfo clientIfaceInfo = getClientInterfaceInfoForBroadcast(message);
                if (clientIfaceInfo == null) return;

                clientIfaceInfo.associated = false;
            };

    private Nl80211BroadcastMonitor.Nl80211BroadcastCallback mStationAddedCallback =
            (command, message) -> {
                if (mVerboseLoggingEnabled) {
                    Log.d(TAG, "Received NL80211 broadcast: " + message);
                }

                ApInterfaceInfo apIfaceInfo = getApInterfaceInfoForBroadcast(message);
                if (apIfaceInfo == null) {
                    Log.e(TAG, "Station added broadcast received but no AP iface was created!");
                    return;
                }
                if (apIfaceInfo.callback == null || apIfaceInfo.executor == null) {
                    if (mVerboseLoggingEnabled) {
                        Log.d(TAG, "No AP callback registered to receive station added broadcast");
                    }
                    return;
                }

                byte[] macAddress = message.getAttributeValueAsByteArray(NL80211_ATTR_MAC);
                if (macAddress == null) {
                    Log.e(TAG, "Failed to get mac address from station event");
                    return;
                }
                apIfaceInfo.executor.execute(() ->
                        apIfaceInfo.callback.onConnectedClientsChanged(
                                new NativeWifiClient(MacAddress.fromBytes(macAddress)),
                                true));
            };

    private Nl80211BroadcastMonitor.Nl80211BroadcastCallback mStationRemovedCallback =
            (command, message) -> {
                if (mVerboseLoggingEnabled) {
                    Log.d(TAG, "Received NL80211 broadcast: " + message);
                }

                ApInterfaceInfo apIfaceInfo = getApInterfaceInfoForBroadcast(message);
                if (apIfaceInfo == null) {
                    Log.e(TAG, "Station removed broadcast received but no AP iface was created!");
                    return;
                }
                if (apIfaceInfo.callback == null || apIfaceInfo.executor == null) {
                    if (mVerboseLoggingEnabled) {
                        Log.d(TAG, "No AP callback registered to receive station deleted"
                                + " broadcast");
                    }
                    return;
                }

                byte[] macAddress = message.getAttributeValueAsByteArray(NL80211_ATTR_MAC);
                if (macAddress == null) {
                    Log.e(TAG, "Failed to get mac address from station event");
                    return;
                }
                apIfaceInfo.executor.execute(() ->
                        apIfaceInfo.callback.onConnectedClientsChanged(
                                new NativeWifiClient(MacAddress.fromBytes(macAddress)),
                                false));
            };

    private Nl80211BroadcastMonitor.Nl80211BroadcastCallback mChannelSwitchCallback =
            (command, message) -> {
                if (mVerboseLoggingEnabled) {
                    Log.d(TAG, "Received NL80211 broadcast: " + message);
                }

                ApInterfaceInfo apIfaceInfo = getApInterfaceInfoForBroadcast(message);
                if (apIfaceInfo == null) {
                    Log.e(TAG, "Channel switch broadcast received but no AP iface was created!");
                    return;
                }
                if (apIfaceInfo.callback == null || apIfaceInfo.executor == null) {
                    if (mVerboseLoggingEnabled) {
                        Log.d(TAG, "No AP callback registered to receive channel switch"
                                + " broadcast");
                    }
                    return;
                }

                Integer frequencyMhz = message.getAttributeValueAsInteger(NL80211_ATTR_WIPHY_FREQ);
                if (frequencyMhz == null) {
                    Log.e(TAG, "Failed to get frequency from channel switch event");
                    return;
                }
                Integer channelWidth =
                        message.getAttributeValueAsInteger(NL80211_ATTR_CHANNEL_WIDTH);
                if (channelWidth == null) {
                    Log.e(TAG, "Failed to get channel width from channel switch event");
                    return;
                }
                apIfaceInfo.executor.execute(() ->
                        apIfaceInfo.callback.onSoftApChannelSwitched(
                                frequencyMhz,
                                convertNl80211ChannelWidthToSoftApInfoChannelWidth(channelWidth)));
            };

    private Nl80211BroadcastMonitor.Nl80211BroadcastCallback mRegChangedCallback;

    private int convertNl80211ChannelWidthToSoftApInfoChannelWidth(int nl80211ChannelWidth) {
        // Convert enum nl80211_chan_width to enum ChannelBandwidth
        switch (nl80211ChannelWidth) {
            case NL80211_CHAN_WIDTH_20_NOHT:
                return SoftApInfo.CHANNEL_WIDTH_20MHZ_NOHT;
            case NL80211_CHAN_WIDTH_20:
                return SoftApInfo.CHANNEL_WIDTH_20MHZ;
            case NL80211_CHAN_WIDTH_40:
                return SoftApInfo.CHANNEL_WIDTH_40MHZ;
            case NL80211_CHAN_WIDTH_80:
                return SoftApInfo.CHANNEL_WIDTH_80MHZ;
            case NL80211_CHAN_WIDTH_80P80:
                return SoftApInfo.CHANNEL_WIDTH_80MHZ_PLUS_MHZ;
            case NL80211_CHAN_WIDTH_160:
                return SoftApInfo.CHANNEL_WIDTH_160MHZ;
            case NL80211_CHAN_WIDTH_320:
                return SoftApInfo.CHANNEL_WIDTH_320MHZ;
            default:
                Log.e(TAG, "Unknown channel width: " + nl80211ChannelWidth);
                return SoftApInfo.CHANNEL_WIDTH_INVALID;
        }
    }

    private ClientInterfaceInfo getClientInterfaceInfoForBroadcast(GenericNetlinkMsg broadcast) {
        Integer ifIndex = broadcast.getAttributeValueAsInteger(NL80211_ATTR_IFINDEX);
        if (ifIndex == null) {
            Log.e(TAG, "Client iface broadcast message does not have ifIndex");
            return null;
        }

        // Find the interface by ifIndex
        for (ClientInterfaceInfo info : mClientInterfaceInfos.values()) {
            if (info.ifIndex == ifIndex) {
                return info;
            }
        }
        Log.e(TAG, "Could not find iface for client iface broadcast message with ifIndex "
                + ifIndex);
        return null;
    }

    private ApInterfaceInfo getApInterfaceInfoForBroadcast(GenericNetlinkMsg broadcast) {
        Integer ifIndex = broadcast.getAttributeValueAsInteger(NL80211_ATTR_IFINDEX);
        if (ifIndex == null) {
            Log.e(TAG, "AP broadcast message does not have ifIndex");
            return null;
        }

        // Find the interface by ifIndex
        for (ApInterfaceInfo info : mApInterfaceInfos.values()) {
            if (info.ifIndex == ifIndex) {
                return info;
            }
        }
        Log.e(TAG, "Could not find iface for AP broadcast message with ifIndex " + ifIndex);
        return null;
    }

    /**
     * Specifies a scan type: single scan initiated by the framework. Can be used in
     * {@link #getScanResults(String, int)} to specify the type of scan result to fetch.
     */
    public static final int SCAN_TYPE_SINGLE_SCAN = 0;

    /**
     * Specifies a scan type: PNO scan. Can be used in {@link #getScanResults(String, int)} to
     * specify the type of scan result to fetch.
     */
    public static final int SCAN_TYPE_PNO_SCAN = 1;

    // Extra scanning parameter used to enable 6Ghz RNR (Reduced Neighbour Support).
    public static final String SCANNING_PARAM_ENABLE_6GHZ_RNR =
            WifiNl80211Manager.SCANNING_PARAM_ENABLE_6GHZ_RNR;

    // Extra scanning parameter used to add vendor IEs (byte[]).
    public static final String EXTRA_SCANNING_PARAM_VENDOR_IES =
            WifiNl80211Manager.EXTRA_SCANNING_PARAM_VENDOR_IES;

    /**
     * Interface used to listen country code event
     */
    public interface CountryCodeChangedListener
            extends WifiNl80211Manager.CountryCodeChangedListener {
        /**
         * Called when country code changed.
         *
         * @param countryCode An ISO-3166-alpha2 country code which is 2-Character alphanumeric.
         */
        void onCountryCodeChanged(@NonNull String countryCode);
    }

    /**
     * Interface used when waiting for scans to be completed (with results).
     */
    public interface ScanEventCallback extends WifiNl80211Manager.ScanEventCallback {
        // Inherit from WifiNl80211Manager.ScanEventCallback
    }

    /**
     * Interface for a callback to provide information about PNO scan request requested with
     * {@link #startPnoScan(String, PnoSettings, Executor, PnoScanRequestCallback)}. Note that the
     * callback are for the status of the request - not the scan itself. The results of the scan
     * are returned with {@link ScanEventCallback}.
     */
    public interface PnoScanRequestCallback extends WifiNl80211Manager.PnoScanRequestCallback {
        // Inherit from WifiNl80211Manager.PnoScanRequestCallback
    }

    public Nl80211Native(
            @NonNull Nl80211Proxy nl80211Proxy,
            @NonNull Nl80211Utils nl80211Utils,
            @NonNull NetdWrapper netdWrapper,
            @NonNull WifiNl80211Manager wificondManager,
            @NonNull WifiInjector wifiInjector,
            boolean useWificond) {
        mNl80211Proxy = nl80211Proxy;
        mNl80211Utils = nl80211Utils;
        mNetdWrapper = netdWrapper;
        mWificondManager = wificondManager;
        mWifiInjector = wifiInjector;
        mUseWificond = useWificond;
        Log.i(TAG, "useWificond: " + useWificond);

        // Initialize mRegChangedCallback here so we can safely use mNl80211Utils.
        mRegChangedCallback =
                (command, message) -> {
                    if (mVerboseLoggingEnabled) {
                        Log.d(TAG, "Received NL80211 broadcast: " + message);
                    }

                    Byte regType = message.getAttributeValueAsByte(NL80211_ATTR_REG_TYPE);
                    if (regType == null) {
                        Log.e(TAG, "Failed to get NL80211_ATTR_REG_TYPE");
                        return;
                    }

                    String countryCode;
                    switch (regType) {
                        case NL80211_REGDOM_TYPE_COUNTRY -> {
                            countryCode =
                                    message.getAttributeValueAsString(NL80211_ATTR_REG_ALPHA2);
                            if (countryCode == null) {
                                Log.e(TAG, "Failed to get NL80211_ATTR_REG_ALPHA2");
                                return;
                            }

                            if (!countryCode.equals(mCountryCode)) {
                                mCountryCode = countryCode;
                                notifyCountryCodeChangedListeners(countryCode);
                            }
                            updateIfaceInfoAfterRegChanged();
                        }
                        case NL80211_REGDOM_TYPE_WORLD,
                             NL80211_REGDOM_TYPE_CUSTOM_WORLD,
                             NL80211_REGDOM_TYPE_INTERSECTION -> {
                            if (mActiveIfaceToWiphyIndex.isEmpty()) {
                                Log.e(TAG, "Received REG changed callback even though no ifaces are"
                                        + " created!");
                                return;
                            }

                            // TODO: Different wiphys may return different country codes depending
                            // on regulatory hints. For now, we will simply replicate the wificond
                            // logic of iterating through each individual wiphy's CC and comparing
                            // it to our singular mCountryCode.
                            List<Integer> wiphyIndexes =
                                    new ArrayList<>(mActiveIfaceToWiphyIndex.values());
                            Collections.sort(wiphyIndexes);
                            for (int wiphyIndex : wiphyIndexes) {
                                countryCode = mNl80211Utils.getCountryCode(wiphyIndex);
                                if (countryCode != null && !countryCode.equals(mCountryCode)) {
                                    mCountryCode = countryCode;
                                    notifyCountryCodeChangedListeners(countryCode);
                                }
                                updateIfaceInfoAfterRegChanged();
                            }
                        }
                        default -> {
                            Log.e(TAG, "Unknown type of regulatory domain change: " + regType);
                        }
                    }
                };
    }

    /**
     * Initialize this instance of the class.
     *
     * @return true if successful, false otherwise.
     */
    public boolean initialize() {
        if (mIsInitialized) return true;
        mIsInitialized = mNl80211Proxy.initialize();
        mNl80211Utils.initialize();

        Log.i(TAG, "Initialization status: " + mIsInitialized);
        return mIsInitialized;
    }

    /**
     * Check whether this instance has been initialized.
     */
    public boolean isInitialized() {
        return mIsInitialized;
    }

    private boolean useWificond() {
        return mUseWificond && !mUseNl80211Override;
    }

    /**
     * Force usage of Nl80211 implementation even if mUseWificond flag is enabled.
     * This is intended for testing purposes.
     */
    public void setUseNl80211Override(boolean enabled) {
        mUseNl80211Override = enabled;
    }

    /**
     * Dump information about the internal state
     *
     * @param pw PrintWriter to write dump to
     */
    public void dump(PrintWriter pw) {
        pw.println("Dump of " + TAG);
        pw.println("mIsInitialized: " + mIsInitialized);
        pw.println("mUseWificond: " + mUseWificond);
    }

    /**
     * Register a death notification for the WifiNl80211Manager which acts as a proxy for the
     * wificond daemon (i.e. the death listener will be called when and if the wificond daemon
     * dies). This is only active when mUseWificond is true.
     *
     * @param deathEventHandler A {@link Runnable} to be called whenever the wificond daemon dies.
     * Set a death handler for the wificond service.
     */
    public void setWificondOnServiceDeadCallback(@NonNull Runnable deathEventHandler) {
        if (useWificond()) {
            mWificondManager.setOnServiceDeadCallback(deathEventHandler);
            return;
        }

        Log.w(TAG, "setWificondOnServiceDeadCallback ignored when mUseWificond is true.");
    }

    /**
     * Enable verbose logging.
     */
    public void enableVerboseLogging(boolean enable) {
        if (useWificond()) {
            mWificondManager.enableVerboseLogging(enable);
        }

        mVerboseLoggingEnabled = enable;
    }

    /**
     * Get the names of all interfaces available on this device.
     *
     * Note: This method always uses the Nl80211Proxy directly, regardless of the
     * {@link #mUseWificond} flag. This is because wificond does not provide an
     * equivalent high-level API for this specific function.
     * TODO(b/394409845): Remove above note once wificond is removed.
     *
     * @return List of interface names, or null if an error occurred.
     */
    public @Nullable List<String> getInterfaceNames() {
        if (!mIsInitialized) return null;
        List<Nl80211Utils.InterfaceInfo> ifaceInfo = mNl80211Utils.getInterfaces(-1);
        if (ifaceInfo == null) return null;

        List<String> interfaceNames = new ArrayList<>();
        for (Nl80211Utils.InterfaceInfo info : ifaceInfo) {
            interfaceNames.add(info.name);
        }
        return interfaceNames;
    }

    private void handleIfaceTeardown(@NonNull String ifaceName) {
        if (!mActiveIfaceToWiphyIndex.containsKey(ifaceName)) return;

        int wiphyIndex = mActiveIfaceToWiphyIndex.get(ifaceName);
        mActiveIfaceToWiphyIndex.remove(ifaceName);

        // Erase the band to wiphy mapping if there are no more interfaces set up on the wiphy.
        if (!mActiveIfaceToWiphyIndex.values().contains(wiphyIndex)) {
            eraseBandToWiphyIndexMapping(wiphyIndex);
        }

        if (mActiveIfaceToWiphyIndex.isEmpty()) {
            unregisterCountryCodeCallbacks();
        }
    }

    /**
     * Set up an interface for client (STA) mode.
     *
     * @param ifaceName Name of the interface to configure.
     * @param executor The Executor on which to execute the callbacks.
     * @param scanCallback A callback for framework initiated scans.
     * @param pnoScanCallback A callback for PNO (offloaded) scans.
     * @return true on success.
     */
    public boolean setupInterfaceForClientMode(
            @NonNull String ifaceName,
            @NonNull Executor executor,
            @NonNull ScanEventCallback scanCallback,
            @NonNull ScanEventCallback pnoScanCallback) {
        if (useWificond()) {
            return mWificondManager.setupInterfaceForClientMode(
                    ifaceName,
                    executor,
                    scanCallback,
                    pnoScanCallback);
        }

        if (ifaceName == null) {
            Log.e(TAG, "ifaceName cannot be null");
            return false;
        }
        if (executor == null) {
            Log.e(TAG, "executor cannot be null");
            return false;
        }
        if (scanCallback == null) {
            Log.e(TAG, "scanCallback cannot be null");
            return false;
        }
        if (pnoScanCallback == null) {
            Log.e(TAG, "pnoScanCallback cannot be null");
            return false;
        }
        if (!mIsInitialized) return false;

        Nl80211Utils.InterfaceInfo ifaceInfo = mNl80211Utils.getInterfaceInfo(ifaceName);
        if (ifaceInfo == null) {
            Log.e(TAG, "Failed to get interface info for " + ifaceName);
            return false;
        }

        String countryCode = mNl80211Utils.getCountryCode(ifaceInfo.wiphyIndex);
        if (countryCode != null && !countryCode.equals(mCountryCode)) {
            mCountryCode = countryCode;
            notifyCountryCodeChangedListeners(countryCode);
        }

        Nl80211Utils.WiphyInfo wiphyInfo = mNl80211Utils.getWiphyInfo(ifaceInfo.wiphyIndex);
        if (wiphyInfo == null) {
            Log.e(TAG, "Failed to get wiphy info for " + ifaceInfo.wiphyIndex);
            return false;
        }

        if (mClientInterfaceInfos.isEmpty()) {
            registerCallbacksForClientIface();
        }
        mClientInterfaceInfos.put(ifaceName,
                new ClientInterfaceInfo(ifaceName, ifaceInfo.ifIndex, wiphyInfo, executor,
                        scanCallback, pnoScanCallback));

        if (mActiveIfaceToWiphyIndex.isEmpty()) {
            registerCountryCodeCallbacks();
        }
        if (!mActiveIfaceToWiphyIndex.containsKey(ifaceName)) {
            updateBandToWiphyIndexMapping(ifaceInfo.wiphyIndex, wiphyInfo);
            mActiveIfaceToWiphyIndex.put(ifaceName, ifaceInfo.wiphyIndex);
        }

        try {
            mNetdWrapper.setInterfaceUp(ifaceName);
        } catch (Exception e) {
            Log.e(TAG, "Failed to set interface " + ifaceName + " up", e);
            // Ignore the failure and continue, which matches the wificond implementation.
        }
        return true;
    }

    /**
     * Tear down a specific client (STA) interface configured using
     * {@link #setupInterfaceForClientMode(String, Executor, ScanEventCallback, ScanEventCallback)}.
     *
     * @param ifaceName Name of the interface to tear down.
     * @return Returns true on success, false on failure (e.g. when called before an interface was
     * set up).
     */
    public boolean tearDownClientInterface(@NonNull String ifaceName) {
        if (useWificond()) {
            return mWificondManager.tearDownClientInterface(ifaceName);
        }

        if (ifaceName == null) {
            Log.e(TAG, "ifaceName cannot be null");
            return false;
        }
        if (!mIsInitialized) return false;

        if (!mClientInterfaceInfos.containsKey(ifaceName)) {
            if (mVerboseLoggingEnabled) {
                Log.v(TAG, "tearDownClientInterface called for untracked iface " + ifaceName);
            }
            return false;
        }
        mClientInterfaceInfos.remove(ifaceName);
        if (mClientInterfaceInfos.isEmpty()) {
            unregisterCallbacksForClientIface();
        }

        handleIfaceTeardown(ifaceName);

        try {
            mNetdWrapper.setInterfaceDown(ifaceName);
        } catch (Exception e) {
            Log.e(TAG, "Failed to set interface " + ifaceName + " down", e);
            // Ignore the failure and continue, which matches the wificond implementation.
        }
        return true;
    }

    private void registerCallbacksForClientIface() {
        mNl80211Proxy.registerBroadcastCallback(NL80211_CMD_NEW_SCAN_RESULTS,
                mNewScanResultsCallback);
        mNl80211Proxy.registerBroadcastCallback(NL80211_CMD_SCAN_ABORTED,
                mScanAbortedCallback);
        mNl80211Proxy.registerBroadcastCallback(NL80211_CMD_SCHED_SCAN_RESULTS,
                mSchedScanResultsCallback);
        mNl80211Proxy.registerBroadcastCallback(NL80211_CMD_SCHED_SCAN_STOPPED,
                mSchedScanStoppedCallback);

        mNl80211Proxy.registerBroadcastCallback(NL80211_CMD_ASSOCIATE,
                mAssociateCallback);
        mNl80211Proxy.registerBroadcastCallback(NL80211_CMD_DISASSOCIATE,
                mDisassociateCallback);
    }

    private void unregisterCallbacksForClientIface() {
        mNl80211Proxy.unregisterBroadcastCallback(NL80211_CMD_NEW_SCAN_RESULTS,
                mNewScanResultsCallback);
        mNl80211Proxy.unregisterBroadcastCallback(NL80211_CMD_SCAN_ABORTED,
                mScanAbortedCallback);
        mNl80211Proxy.unregisterBroadcastCallback(NL80211_CMD_SCHED_SCAN_RESULTS,
                mSchedScanResultsCallback);
        mNl80211Proxy.unregisterBroadcastCallback(NL80211_CMD_SCHED_SCAN_STOPPED,
                mSchedScanStoppedCallback);

        mNl80211Proxy.unregisterBroadcastCallback(NL80211_CMD_ASSOCIATE,
                mAssociateCallback);
        mNl80211Proxy.unregisterBroadcastCallback(NL80211_CMD_DISASSOCIATE,
                mDisassociateCallback);
    }

    private void registerCountryCodeCallbacks() {
        mNl80211Proxy.registerBroadcastCallback(NL80211_CMD_REG_CHANGE, mRegChangedCallback);
        mNl80211Proxy.registerBroadcastCallback(NL80211_CMD_WIPHY_REG_CHANGE,
                mRegChangedCallback);
    }

    private void unregisterCountryCodeCallbacks() {
        mNl80211Proxy.unregisterBroadcastCallback(NL80211_CMD_REG_CHANGE, mRegChangedCallback);
        mNl80211Proxy.unregisterBroadcastCallback(NL80211_CMD_WIPHY_REG_CHANGE,
                mRegChangedCallback);
    }

    /**
     * Set up interface as a Soft AP.
     *
     * @param ifaceName Name of the interface to configure.
     * @return true on success.
     */
    public boolean setupInterfaceForSoftApMode(@NonNull String ifaceName) {
        if (useWificond()) {
            return mWificondManager.setupInterfaceForSoftApMode(ifaceName);
        }

        if (ifaceName == null) {
            Log.e(TAG, "ifaceName cannot be null");
            return false;
        }
        if (!mIsInitialized) return false;

        Nl80211Utils.InterfaceInfo ifaceInfo = mNl80211Utils.getInterfaceInfo(ifaceName);
        if (ifaceInfo == null) {
            Log.e(TAG, "Failed to get interface info for " + ifaceName);
            return false;
        }

        String countryCode = mNl80211Utils.getCountryCode(ifaceInfo.wiphyIndex);
        if (countryCode != null && !countryCode.equals(mCountryCode)) {
            mCountryCode = countryCode;
            notifyCountryCodeChangedListeners(countryCode);
        }

        Nl80211Utils.WiphyInfo wiphyInfo = mNl80211Utils.getWiphyInfo(ifaceInfo.wiphyIndex);
        if (wiphyInfo == null) {
            Log.e(TAG, "Failed to get wiphy info for " + ifaceInfo.wiphyIndex);
            return false;
        }

        if (mApInterfaceInfos.isEmpty()) {
            registerCallbacksForApIface();
        }
        mApInterfaceInfos.put(ifaceName,
                new ApInterfaceInfo(ifaceName, ifaceInfo.ifIndex));

        if (mActiveIfaceToWiphyIndex.isEmpty()) {
            registerCountryCodeCallbacks();
        }
        if (!mActiveIfaceToWiphyIndex.containsKey(ifaceName)) {
            updateBandToWiphyIndexMapping(ifaceInfo.wiphyIndex, wiphyInfo);
            mActiveIfaceToWiphyIndex.put(ifaceName, ifaceInfo.wiphyIndex);
        }

        return true;
    }

    private void registerCallbacksForApIface() {
        mNl80211Proxy.registerBroadcastCallback(NL80211_CMD_NEW_STATION,
                mStationAddedCallback);
        mNl80211Proxy.registerBroadcastCallback(NL80211_CMD_DEL_STATION,
                mStationRemovedCallback);
        mNl80211Proxy.registerBroadcastCallback(NL80211_CMD_CH_SWITCH_NOTIFY,
                mChannelSwitchCallback);
    }

    private void unregisterCallbacksForApIface() {
        mNl80211Proxy.unregisterBroadcastCallback(NL80211_CMD_NEW_STATION,
                mStationAddedCallback);
        mNl80211Proxy.unregisterBroadcastCallback(NL80211_CMD_DEL_STATION,
                mStationRemovedCallback);
        mNl80211Proxy.unregisterBroadcastCallback(NL80211_CMD_CH_SWITCH_NOTIFY,
                mChannelSwitchCallback);
    }

    /**
     * Tear down a Soft AP interface configured using
     * {@link #setupInterfaceForSoftApMode(String)}.
     *
     * @param ifaceName Name of the interface to tear down.
     * @return Returns true on success, false on failure (e.g. when called before an interface was
     * set up).
     */
    public boolean tearDownSoftApInterface(@NonNull String ifaceName) {
        if (useWificond()) {
            return mWificondManager.tearDownSoftApInterface(ifaceName);
        }

        if (ifaceName == null) {
            Log.e(TAG, "ifaceName cannot be null");
            return false;
        }
        if (!mIsInitialized) return false;

        if (!mApInterfaceInfos.containsKey(ifaceName)) {
            if (mVerboseLoggingEnabled) {
                Log.v(TAG, "tearDownSoftApInterface called for untracked iface " + ifaceName);
            }
            return false;
        }
        mApInterfaceInfos.remove(ifaceName);
        if (mApInterfaceInfos.isEmpty()) {
            unregisterCallbacksForApIface();
        }

        handleIfaceTeardown(ifaceName);

        try {
            mNetdWrapper.setInterfaceDown(ifaceName);
        } catch (Exception e) {
            Log.e(TAG, "Failed to set interface " + ifaceName + " down", e);
            // Ignore the failure and continue, which matches the wificond implementation.
        }
        return true;
    }

    /**
     * Tear down all interfaces, whether clients (STA) or Soft AP.
     *
     * @return Returns true on success.
     */
    public boolean tearDownInterfaces() {
        if (useWificond()) {
            return mWificondManager.tearDownInterfaces();
        }

        if (!mIsInitialized) return false;

        for (String clientIface : new ArrayList<>(mClientInterfaceInfos.keySet())) {
            tearDownClientInterface(clientIface);
        }
        for (String apIface : new ArrayList<>(mApInterfaceInfos.keySet())) {
            tearDownSoftApInterface(apIface);
        }
        return true;
    }

    /**
     * Start a scan using the specified parameters. A scan is an asynchronous operation. The
     * result of the operation is returned in the {@link ScanEventCallback} registered when
     * setting up an interface using
     * {@link #setupInterfaceForClientMode(String, Executor, ScanEventCallback, ScanEventCallback)}.
     * The latest scans can be obtained using {@link #getScanResults(String, int)} and using a
     * {@link #SCAN_TYPE_SINGLE_SCAN} for the {@code scanType}.
     *
     * Note: The interface must have been already set up using
     * {@link #setupInterfaceForClientMode(String, Executor, ScanEventCallback, ScanEventCallback)}
     * or {@link #setupInterfaceForSoftApMode(String)}.
     *
     * @param ifaceName Name of the interface on which to initiate the scan.
     * @param scanType Type of scan to perform, can be any of
     * {@link WifiScanner#SCAN_TYPE_HIGH_ACCURACY}, {@link WifiScanner#SCAN_TYPE_LOW_POWER}, or
     * {@link WifiScanner#SCAN_TYPE_LOW_LATENCY}.
     * @param freqs list of frequencies to scan for, if null scan all supported channels.
     * @param hiddenNetworkSSIDs List of hidden networks to be scanned for. An empty list indicates
     *                           to scan using the wildcard SSID.
     * @param extraScanningParams bundle of extra scanning parameters.
     * @return Returns one of the scan status codes defined in {@code WifiScanner#REASON_*}
     */
    public int startScan(
            @NonNull String ifaceName,
            @WifiAnnotations.ScanType int scanType,
            @Nullable Set<Integer> freqs,
            @NonNull List<byte[]> hiddenNetworkSSIDs,
            @Nullable Bundle extraScanningParams) {
        if (useWificond()) {
            return mWificondManager.startScan2(
                    ifaceName, scanType, freqs, hiddenNetworkSSIDs, extraScanningParams);
        }

        if (ifaceName == null) {
            Log.e(TAG, "ifaceName cannot be null");
            return WifiScanner.REASON_UNSPECIFIED;
        }
        if (!mIsInitialized) return WifiScanner.REASON_UNSPECIFIED;

        ClientInterfaceInfo ifaceInfo = mClientInterfaceInfos.get(ifaceName);
        if (ifaceInfo == null) {
            Log.e(TAG, "startScan: no active interface found for " + ifaceName);
            return WifiScanner.REASON_UNSPECIFIED;
        }

        if (ifaceInfo.scanning) {
            Log.w(TAG, "startScan: scan already in progress for " + ifaceName);
        }

        boolean requestRandomMac = ifaceInfo.wiphyInfo.wiphyFeatures.supportsRandomMacOneShotScan
                && !ifaceInfo.associated;

        boolean enable6GhzRnr = false;
        byte[] vendorIes = null;
        if (extraScanningParams != null) {
            enable6GhzRnr = extraScanningParams.getBoolean(SCANNING_PARAM_ENABLE_6GHZ_RNR);
            vendorIes = extraScanningParams.getByteArray(EXTRA_SCANNING_PARAM_VENDOR_IES);
        }

        // Prepare scan flags
        if (scanType < 0 || scanType > WifiScanner.SCAN_TYPE_MAX) {
            return WifiScanner.REASON_INVALID_ARGS;
        }
        int scanFlags = getScanFlagForScanType(scanType, ifaceInfo.wiphyInfo.wiphyFeatures);
        if (requestRandomMac) scanFlags |= NL80211_SCAN_FLAG_RANDOM_ADDR;
        if (enable6GhzRnr) scanFlags |= NL80211_SCAN_FLAG_COLOCATED_6GHZ;

        List<byte[]> trimmedHiddenSsids;
        if (hiddenNetworkSSIDs.isEmpty()) {
            // If no hidden SSIDs are supplied, set an empty SSID to indicate a wildcard scan.
            trimmedHiddenSsids = List.of(new byte[0]);
        } else {
            trimmedHiddenSsids =
                    trimScanSsids(ifaceInfo.wiphyInfo.scanCapabilities, hiddenNetworkSSIDs);
        }

        int result = mNl80211Utils.triggerScan(ifaceInfo.ifIndex, scanFlags, freqs,
                trimmedHiddenSsids, vendorIes);

        if (result == WifiScanner.REASON_NO_DEVICE) {
            ifaceInfo.enodevCounter++;
            Log.e(TAG, "Scan failed with error ENODEV. Counter: " + ifaceInfo.enodevCounter);
            if (ifaceInfo.enodevCounter > ENODEV_RESTART_THRESHOLD) {
                Log.e(TAG, "ENODEV threshold reached, restarting subsystem");
                mWifiInjector.getSelfRecovery().trigger(SelfRecovery.REASON_SUBSYSTEM_RESTART);
            }
            return result;
        }

        ifaceInfo.scanning = (result == WifiScanner.REASON_SUCCEEDED);
        ifaceInfo.enodevCounter = 0;
        return result;
    }

    @VisibleForTesting
    protected static int getScanFlagForScanType(
            @WifiAnnotations.ScanType int scanType,
            @NonNull Nl80211Utils.WiphyFeatures features) {
        switch (scanType) {
            case WifiScanner.SCAN_TYPE_LOW_LATENCY -> {
                if (features.supportsLowSpanOneShotScan) {
                    return NL80211_SCAN_FLAG_LOW_SPAN;
                }
            }
            case WifiScanner.SCAN_TYPE_LOW_POWER -> {
                if (features.supportsLowPowerOneShotScan) {
                    return NL80211_SCAN_FLAG_LOW_POWER;
                }
            }
            case WifiScanner.SCAN_TYPE_HIGH_ACCURACY -> {
                if (features.supportsHighAccuracyOneShotScan) {
                    return NL80211_SCAN_FLAG_HIGH_ACCURACY;
                }
            }
            default -> Log.wtf(TAG, "Received invalid scan type " + scanType);
        }

        Log.d(TAG, "Ignoring unsupported scan type " + scanType);
        return 0;
    }

    private List<byte[]> trimScanSsids(
            @NonNull Nl80211Utils.ScanCapabilities scanCapabilities,
            @NonNull List<byte[]> scanSsids) {
        List<byte[]> trimmedSsids = new ArrayList<>();
        List<byte[]> tooLongSsids = new ArrayList<>();
        List<byte[]> surplusSsids = new ArrayList<>();
        for (byte[] ssid : scanSsids) {
            if (trimmedSsids.size() >= scanCapabilities.maxNumScanSsids) {
                surplusSsids.add(ssid);
            } else if (ssid.length > MAX_SSID_LENGTH) {
                tooLongSsids.add(ssid);
            } else {
                trimmedSsids.add(ssid);
            }
        }

        for (byte[] tooLongSsid : tooLongSsids) {
            Log.i(TAG, "Skipped too-long ssid: " + WifiSsid.fromBytes(tooLongSsid));
        }
        for (byte[] surplusSsid : surplusSsids) {
            Log.i(TAG, "Max scan ssids exceeded, skipping ssid: "
                    + WifiSsid.fromBytes(surplusSsid));
        }
        return trimmedSsids;
    }

    /**
     * Start a scan using the specified parameters.
     *
     * @deprecated See {@link #startScan(String, int, Set, List, Bundle)}, which provides an int
     *             result code instead of a boolean.
     */
    @Deprecated
    public boolean startScanPreU(
            @NonNull String ifaceName,
            @WifiAnnotations.ScanType int scanType,
            @Nullable Set<Integer> freqs,
            @Nullable List<byte[]> hiddenNetworkSSIDs,
            @Nullable Bundle extraScanningParams) {
        if (useWificond()) {
            return mWificondManager.startScan(
                    ifaceName, scanType, freqs, hiddenNetworkSSIDs, extraScanningParams);
        }

        Log.wtf(TAG, "startScanPreU should not be called when using the Nl80211Proxy"
                + " implementation.");
        throw new UnsupportedOperationException();
    }

    /**
     * Converts a list of android.net.wifi.nl80211.NativeScanResult to
     * com.android.server.wifi.nl80211.NativeScanResult
     */
    public static List<NativeScanResult> wificondScansToNl80211NativeScans(
            @NonNull List<android.net.wifi.nl80211.NativeScanResult> wificondScans) {
        List<NativeScanResult> nl80211Scans = new ArrayList<>();
        for (android.net.wifi.nl80211.NativeScanResult wificondScan : wificondScans) {
            nl80211Scans.add(new NativeScanResult(wificondScan));
        }
        return nl80211Scans;
    }

    /**
     * Fetch the latest scan results of the indicated type for the specified interface. Note that
     * this method fetches the latest results - it does not initiate a scan. Initiating a scan can
     * be done using {@link #startScan(String, int, Set, List, Bundle)} or
     * {@link #startPnoScan(String, PnoSettings, Executor, PnoScanRequestCallback)}.
     *
     * Note: The interface must have been already set up using
     * {@link #setupInterfaceForClientMode(String, Executor, ScanEventCallback, ScanEventCallback)}
     * or {@link #setupInterfaceForSoftApMode(String)}.
     *
     * <p>
     * When an Access Point’s beacon or probe response includes a Multi-BSSID Element, the
     * returned scan results should include separate scan result for each BSSID within the
     * Multi-BSSID Information Element. This includes both transmitted and non-transmitted BSSIDs.
     * Original Multi-BSSID Element will be included in the Information Elements attached to
     * each of the scan results.
     * Note: This is the expected behavior for devices supporting 11ax (WiFi-6) and above, and an
     * optional requirement for devices running with older WiFi generations.
     * </p>
     *
     * @param ifaceName Name of the interface.
     * @param scanType The type of scan result to be returned, can be
     * {@link #SCAN_TYPE_SINGLE_SCAN} or {@link #SCAN_TYPE_PNO_SCAN}.
     * @return Returns an array of {@link NativeScanResult} or an empty array on failure (e.g. when
     * called before the interface has been set up).
     */
    @NonNull
    public List<NativeScanResult> getScanResults(
            @NonNull String ifaceName,
            int scanType) {
        if (useWificond()) {
            return wificondScansToNl80211NativeScans(
                    mWificondManager.getScanResults(ifaceName, scanType));
        }

        if (ifaceName == null) {
            Log.e(TAG, "ifaceName cannot be null");
            return new ArrayList<>();
        }
        if (!mIsInitialized) return new ArrayList<>();

        // Note: Wificond ignores scanType, so we also don't need to take it into account.
        return mNl80211Utils.getScanResults(ifaceName);
    }

    /**
     * Request a PNO (Preferred Network Offload). The offload request and the scans are asynchronous
     * operations. The result of the request are returned in the {@code callback} parameter which
     * is an {@link PnoScanRequestCallback}. The scan results are are return in the
     * {@link ScanEventCallback} which is registered when setting up an interface using
     * {@link #setupInterfaceForClientMode(String, Executor, ScanEventCallback, ScanEventCallback)}.
     * The latest PNO scans can be obtained using {@link #getScanResults(String, int)} with the
     * {@code scanType} set to {@link #SCAN_TYPE_PNO_SCAN}.
     *
     * Note: The interface must have been already set up using
     * {@link #setupInterfaceForClientMode(String, Executor, ScanEventCallback, ScanEventCallback)}
     * or {@link #setupInterfaceForSoftApMode(String)}.
     *
     * @param ifaceName Name of the interface on which to request a PNO.
     * @param pnoSettings PNO scan configuration.
     * @param executor The Executor on which to execute the callback.
     * @param callback Callback for the results of the offload request.
     * @return true on success, false on failure (e.g. when called before the interface has been set
     * up).
     */
    public boolean startPnoScan(
            @NonNull String ifaceName,
            @NonNull PnoSettings pnoSettings,
            @NonNull Executor executor,
            @NonNull PnoScanRequestCallback callback) {
        if (useWificond()) {
            return mWificondManager.startPnoScan(
                    ifaceName, pnoSettings.toWificondPnoSettings(), executor, callback);
        }

        if (ifaceName == null) {
            Log.e(TAG, "ifaceName cannot be null");
            return false;
        }
        if (pnoSettings == null) {
            Log.e(TAG, "pnoSettings cannot be null");
            return false;
        }
        if (executor == null) {
            Log.e(TAG, "executor cannot be null");
            return false;
        }
        if (callback == null) {
            Log.e(TAG, "callback cannot be null");
            return false;
        }
        if (!mIsInitialized) return false;

        ClientInterfaceInfo ifaceInfo = mClientInterfaceInfos.get(ifaceName);
        if (ifaceInfo == null) {
            Log.e(TAG, "No active interface found for " + ifaceName);
            return false;
        }

        if (ifaceInfo.pnoScanStarted) {
            Log.w(TAG, "Pno scan already started");
        }

        List<byte[]> scanSsids = new ArrayList<>();
        List<byte[]> matchSsids = new ArrayList<>();
        Set<Integer> uniqueFreqs = new ArraySet<>();
        int networksWithoutFreqs = 0;

        Set<Integer> allSupportedFreqs = new ArraySet<>();
        allSupportedFreqs.addAll(ifaceInfo.wiphyInfo.bandInfo.band2g);
        allSupportedFreqs.addAll(ifaceInfo.wiphyInfo.bandInfo.band5g);
        allSupportedFreqs.addAll(ifaceInfo.wiphyInfo.bandInfo.bandDfs);
        allSupportedFreqs.addAll(ifaceInfo.wiphyInfo.bandInfo.band6g);
        allSupportedFreqs.addAll(ifaceInfo.wiphyInfo.bandInfo.band60g);

        // Extract scan parameters from PnoSettings
        List<PnoNetwork> pnoNetworks = pnoSettings.getPnoNetworks();
        for (PnoNetwork network : pnoNetworks) {
            // Hidden SSIDs
            if (network.isHidden()) {
                if (scanSsids.size() < ifaceInfo.wiphyInfo.scanCapabilities.maxNumSchedScanSsids) {
                    scanSsids.add(network.getSsid());
                } else {
                    Log.w(TAG, "Max scheduled scan SSIDs exceeded, skipping: "
                            + WifiSsid.fromBytes(network.getSsid()));
                }
            }

            // Match SSIDs
            if (matchSsids.size() < ifaceInfo.wiphyInfo.scanCapabilities.maxMatchSets) {
                matchSsids.add(network.getSsid());
            } else {
                Log.w(TAG, "Max PNO match SSIDs exceeded, skipping: "
                        + WifiSsid.fromBytes(network.getSsid()));
            }

            // Filter unsupported frequencies
            int[] freqs = network.getFrequenciesMhz();
            if (freqs.length == 0) {
                networksWithoutFreqs++;
                continue;
            }
            for (int freq : freqs) {
                if (!allSupportedFreqs.contains(freq)) continue;
                uniqueFreqs.add(freq);
            }
        }

        // Scan the default frequencies if we have too many networks without frequency data.
        if (!pnoNetworks.isEmpty()
                && (networksWithoutFreqs * 100
                > pnoNetworks.size() * PERCENT_NETWORKS_WITH_FREQ_FOR_PNO_SCAN)) {
            for (int freq : PNO_SCAN_DEFAULT_FREQS_2G) {
                if (allSupportedFreqs.contains(freq)) uniqueFreqs.add(freq);
            }
            // Note: PNO_SCAN_DEFAULT_FREQS_5G doesn't contain DFS frequencies.
            for (int freq : PNO_SCAN_DEFAULT_FREQS_5G) {
                if (allSupportedFreqs.contains(freq)) uniqueFreqs.add(freq);
            }
        }

        Nl80211Utils.WiphyFeatures wiphyFeatures = ifaceInfo.wiphyInfo.wiphyFeatures;
        boolean requestRandomMac = wiphyFeatures.supportsRandomMacSchedScan
                && !ifaceInfo.associated;
        boolean requestLowPower = wiphyFeatures.supportsLowPowerOneShotScan;
        boolean requestSchedScanRelativeRssi = wiphyFeatures.supportsExtSchedScanRelativeRssi;

        List<Nl80211Utils.PnoScanPlan> scanPlans =
                generatePnoScanPlans(pnoSettings, ifaceInfo.wiphyInfo.scanCapabilities);

        int result = mNl80211Utils.startPnoScan(
                ifaceInfo.ifIndex,
                scanPlans,
                pnoSettings.getIntervalMillis(),
                pnoSettings.getMin2gRssiDbm(),
                pnoSettings.getMin5gRssiDbm(),
                requestRandomMac,
                requestLowPower,
                requestSchedScanRelativeRssi,
                scanSsids,
                matchSsids,
                new ArrayList<>(uniqueFreqs));

        if (result != WifiScanner.REASON_SUCCEEDED) {
            if (result == WifiScanner.REASON_NO_DEVICE) {
                ifaceInfo.enodevCounter++;
                Log.e(TAG, "Pno scan failed with error ENODEV. Counter: "
                        + ifaceInfo.enodevCounter);
                if (ifaceInfo.enodevCounter > ENODEV_RESTART_THRESHOLD) {
                    Log.e(TAG, "ENODEV threshold reached, restarting subsystem");
                    mWifiInjector.getSelfRecovery().trigger(SelfRecovery.REASON_SUBSYSTEM_RESTART);
                }
            } else {
                ifaceInfo.enodevCounter = 0;
            }

            Log.e(TAG, "PNO scan failed with reason: " + result);
            executor.execute(callback::onPnoRequestFailed);
            return false;
        }

        Log.e(TAG, "PNO scan started successfully for frequencies: " + uniqueFreqs);
        executor.execute(callback::onPnoRequestSucceeded);
        ifaceInfo.enodevCounter = 0;
        ifaceInfo.pnoScanStarted = true;
        return true;
    }

    /**
     * Generates list of PNO scan plans for the given PnoSettings and scan capabilities.
     * If the given settings are not supported, returns an empty list.
     */
    private List<Nl80211Utils.PnoScanPlan> generatePnoScanPlans(
            @NonNull PnoSettings pnoSettings,
            @NonNull Nl80211Utils.ScanCapabilities scanCapabilities) {
        int maxRequestedScanIntervalSeconds = (int) ((pnoSettings.getIntervalMillis()
                * pnoSettings.getScanIntervalMultiplier()) / 1000);
        int numRequestedScanPlans = 2;

        if (numRequestedScanPlans > scanCapabilities.maxNumScanPlans
                || maxRequestedScanIntervalSeconds > scanCapabilities.maxScanPlanIntervalSeconds
                || pnoSettings.getScanIterations() > scanCapabilities.maxScanPlanIterations) {
            return new ArrayList<>();
        }

        List<Nl80211Utils.PnoScanPlan> plans = new ArrayList<>();
        plans.add(new Nl80211Utils.PnoScanPlan(
                (int) pnoSettings.getIntervalMillis(), pnoSettings.getScanIterations()));
        plans.add(new Nl80211Utils.PnoScanPlan(maxRequestedScanIntervalSeconds, 0 /* ignored */));
        return plans;
    }

    /**
     * Stop PNO scan configured with
     * {@link #startPnoScan(String, PnoSettings, Executor, PnoScanRequestCallback)}.
     *
     * Note: The interface must have been already set up using
     * {@link #setupInterfaceForClientMode(String, Executor, ScanEventCallback, ScanEventCallback)}
     * or {@link #setupInterfaceForSoftApMode(String)}.
     *
     * @param ifaceName Name of the interface on which the PNO scan was configured.
     * @return true on success, false on failure (e.g. when called before the interface has been
     * set up).
     */
    public boolean stopPnoScan(@NonNull String ifaceName) {
        if (useWificond()) {
            return mWificondManager.stopPnoScan(ifaceName);
        }

        if (ifaceName == null) {
            Log.e(TAG, "ifaceName cannot be null");
            return false;
        }
        if (!mIsInitialized) return false;

        ClientInterfaceInfo ifaceInfo = mClientInterfaceInfos.get(ifaceName);
        if (ifaceInfo == null) {
            Log.e(TAG, "No active interface found for " + ifaceName);
            return false;
        }

        if (!ifaceInfo.pnoScanStarted) {
            Log.w(TAG, "No pno scan started");
        }
        ifaceInfo.pnoScanStarted = false;

        return mNl80211Utils.stopPnoScan(ifaceInfo.ifIndex);
    }

    /**
     * Abort ongoing single scan started with {@link #startScan(String, int, Set, List, Bundle)}.
     * No failure callback, e.g. {@link WifiNl80211Manager.ScanEventCallback#onScanFailed()}, is
     * triggered by this operation.
     *
     * Note: The interface must have been already set up using
     * {@link #setupInterfaceForClientMode(String, Executor, ScanEventCallback, ScanEventCallback)}
     * or {@link #setupInterfaceForSoftApMode(String)}. If the interface has not been set up then
     * this method has no impact.
     *
     * @param ifaceName Name of the interface on which the scan was started.
     */
    public void abortScan(@NonNull String ifaceName) {
        if (useWificond()) {
            mWificondManager.abortScan(ifaceName);
            return;
        }

        if (ifaceName == null) {
            Log.e(TAG, "ifaceName cannot be null");
            return;
        }
        if (!mIsInitialized) return;

        ClientInterfaceInfo ifaceInfo = mClientInterfaceInfos.get(ifaceName);
        if (ifaceInfo == null) {
            Log.e(TAG, "Cannot abort scan for untracked iface: " + ifaceName);
            return;
        }

        if (!ifaceInfo.scanning) {
            Log.e(TAG, "Cannot abort scan when iface isn't scanning: " + ifaceName);
            return;
        }

        mNl80211Utils.abortScan(ifaceInfo.ifIndex);
    }

    /**
     * Request signal polling.
     *
     * @param ifaceName Name of the interface on which to poll. The interface must have been
     *                  already set up using
     *{@link #setupInterfaceForClientMode(String, Executor, ScanEventCallback, ScanEventCallback)}
     *                  or {@link #setupInterfaceForSoftApMode(String)}.
     *
     * @return A {@link android.net.wifi.nl80211.WifiNl80211Manager.SignalPollResult} object
     * containing interface statistics, or a null on error (e.g. the interface hasn't been set up
     * yet).
     *
     * @deprecated replaced by
     * {@link com.android.server.wifi.SupplicantStaIfaceHal#getSignalPollResults}
     */
    @Deprecated
    @Nullable
    public WifiNl80211Manager.SignalPollResult wificondSignalPoll(@NonNull String ifaceName) {
        if (useWificond()) {
            return mWificondManager.signalPoll(ifaceName);
        }

        Log.wtf(TAG, "signalPoll should not be called when using the Nl80211Proxy"
                + " implementation. This should be handled by"
                + " com.android.server.wifi.SupplicantStaIfaceHal#getSignalPollResults");
        throw new UnsupportedOperationException();
    }

    /**
     * Get the device phy capabilities for a given interface.
     *
     * Note: The interface must have been already set up using
     * {@link #setupInterfaceForClientMode(String, Executor, ScanEventCallback, ScanEventCallback)}
     * or {@link #setupInterfaceForSoftApMode(String)}.
     *
     * @return DeviceWiphyCapabilities or null on error (e.g. when called on an interface which has
     * not been set up).
     */
    @Nullable
    public DeviceWiphyCapabilities getDeviceWiphyCapabilities(@NonNull String ifaceName) {
        if (useWificond()) {
            android.net.wifi.nl80211.DeviceWiphyCapabilities wificondCaps =
                    mWificondManager.getDeviceWiphyCapabilities(ifaceName);
            if (wificondCaps == null) return null;
            return new DeviceWiphyCapabilities(wificondCaps);
        }

        if (ifaceName == null) {
            Log.e(TAG, "ifaceName cannot be null");
            return null;
        }

        if (!mIsInitialized) {
            Log.e(TAG, "Service is not initialized");
            return null;
        }

        int wiphyIndex = mNl80211Utils.getWiphyIndex(ifaceName);
        if (wiphyIndex == -1) {
            Log.e(TAG, "Failed to get wiphy index for " + ifaceName);
            return null;
        }

        Log.d(TAG, "Using wiphy index " + wiphyIndex + " for " + ifaceName);
        Nl80211Utils.WiphyInfo wiphyInfo = mNl80211Utils.getWiphyInfo(wiphyIndex);
        if (wiphyInfo == null) {
            Log.e(TAG, "getDeviceWiphyCapabilities: Failed to get wiphy info for index "
                    + wiphyIndex);
            return null;
        }

        DeviceWiphyCapabilities capabilities = new DeviceWiphyCapabilities();
        capabilities.setWifiStandardSupport(ScanResult.WIFI_STANDARD_11N,
                wiphyInfo.bandInfo.is80211nSupported);
        capabilities.setWifiStandardSupport(ScanResult.WIFI_STANDARD_11AC,
                wiphyInfo.bandInfo.is80211acSupported);
        capabilities.setWifiStandardSupport(ScanResult.WIFI_STANDARD_11AX,
                wiphyInfo.bandInfo.is80211axSupported);
        capabilities.setWifiStandardSupport(ScanResult.WIFI_STANDARD_11BE,
                wiphyInfo.bandInfo.is80211beSupported);
        capabilities.setChannelWidthSupported(ScanResult.CHANNEL_WIDTH_160MHZ,
                wiphyInfo.bandInfo.is160MhzSupported);
        capabilities.setChannelWidthSupported(ScanResult.CHANNEL_WIDTH_80MHZ_PLUS_MHZ,
                wiphyInfo.bandInfo.is80p80MhzSupported);
        capabilities.setChannelWidthSupported(ScanResult.CHANNEL_WIDTH_320MHZ,
                wiphyInfo.bandInfo.is320MhzSupported);
        capabilities.setMaxNumberTxSpatialStreams(wiphyInfo.bandInfo.maxTxStreams);
        capabilities.setMaxNumberRxSpatialStreams(wiphyInfo.bandInfo.maxRxStreams);
        capabilities.setMaxNumberAkms(wiphyInfo.driverCapabilities.maxNumAkmSuites);
        return capabilities;
    }

    private void updateBandToWiphyIndexMapping(
            int wiphyIndex, @NonNull Nl80211Utils.WiphyInfo wiphyInfo) {
        // 2.4 GHz Band
        boolean has2gChannels = !wiphyInfo.bandInfo.band2g.isEmpty();
        boolean is2gAlreadyMapped =
                mBandToWiphyIndex.indexOfKey(WifiScanner.WIFI_BAND_24_GHZ) >= 0;
        if (has2gChannels && !is2gAlreadyMapped) {
            mBandToWiphyIndex.put(WifiScanner.WIFI_BAND_24_GHZ, wiphyIndex);
            Log.i(TAG, "Added 2.4 GHz support at wiphy index: " + wiphyIndex);
        }

        // 5 GHz Band
        boolean has5gChannels =
                !wiphyInfo.bandInfo.band5g.isEmpty() || !wiphyInfo.bandInfo.bandDfs.isEmpty();
        boolean is5gAlreadyMapped = mBandToWiphyIndex.indexOfKey(WifiScanner.WIFI_BAND_5_GHZ) >= 0;
        if (has5gChannels && !is5gAlreadyMapped) {
            mBandToWiphyIndex.put(WifiScanner.WIFI_BAND_5_GHZ, wiphyIndex);
            mBandToWiphyIndex.put(WifiScanner.WIFI_BAND_5_GHZ_DFS_ONLY, wiphyIndex);
            Log.i(TAG, "Added 5 GHz support at wiphy index: " + wiphyIndex);
        }

        // 6 GHz Band
        boolean has6gChannels = !wiphyInfo.bandInfo.band6g.isEmpty();
        boolean is6gAlreadyMapped =
                mBandToWiphyIndex.indexOfKey(WifiScanner.WIFI_BAND_6_GHZ) >= 0;
        if (has6gChannels && !is6gAlreadyMapped) {
            mBandToWiphyIndex.put(WifiScanner.WIFI_BAND_6_GHZ, wiphyIndex);
            Log.i(TAG, "Added 6 GHz support at wiphy index: " + wiphyIndex);
        }

        // 60 GHz
        boolean has60gChannels = !wiphyInfo.bandInfo.band60g.isEmpty();
        boolean is60gAlreadyMapped =
                mBandToWiphyIndex.indexOfKey(WifiScanner.WIFI_BAND_60_GHZ) >= 0;
        if (has60gChannels && !is60gAlreadyMapped) {
            mBandToWiphyIndex.put(WifiScanner.WIFI_BAND_60_GHZ, wiphyIndex);
            Log.i(TAG, "Added 60 GHz support at wiphy index: " + wiphyIndex);
        }
    }

    private void eraseBandToWiphyIndexMapping(int wiphyIndex) {
        int nextIndex = mBandToWiphyIndex.indexOfValue(wiphyIndex);
        while (nextIndex >= 0) {
            mBandToWiphyIndex.removeAt(nextIndex);
            nextIndex = mBandToWiphyIndex.indexOfValue(wiphyIndex);
        }
    }

    /**
     * Query the list of valid frequencies (in MHz) for the provided band.
     * The result depends on the on the country code that has been set.
     *
     * @param band as specified by one of the WifiScanner.WIFI_BAND_* constants.
     * The following bands are supported:
     * {@link WifiScanner#WIFI_BAND_24_GHZ},
     * {@link WifiScanner#WIFI_BAND_5_GHZ},
     * {@link WifiScanner#WIFI_BAND_5_GHZ_DFS_ONLY},
     * {@link WifiScanner#WIFI_BAND_6_GHZ}
     * {@link WifiScanner#WIFI_BAND_60_GHZ}
     * @return frequencies vector of valid frequencies (MHz), or an empty array for error.
     * @throws IllegalArgumentException if band is not recognized.
     */
    @NonNull
    public int[] getChannelsMhzForBand(@WifiAnnotations.WifiBandBasic int band) {
        if (useWificond()) {
            return mWificondManager.getChannelsMhzForBand(band);
        }

        if (!mIsInitialized) return new int[0];

        if (mBandToWiphyIndex.indexOfKey(band) < 0) {
            Log.e(TAG, "getChannelsMhzForBand: Wiphy index not recorded for band " + band);
            return new int[0];
        }
        int wiphyIndex = mBandToWiphyIndex.get(band);

        Nl80211Utils.WiphyInfo wiphyInfo = mNl80211Utils.getWiphyInfo(wiphyIndex);
        if (wiphyInfo == null) {
            Log.e(TAG, "getChannelsMhzForBand: Could not get wiphy info for index " + wiphyIndex);
            return new int[0];
        }

        List<Integer> channelsMhz;
        switch (band) {
            case WifiScanner.WIFI_BAND_24_GHZ -> {
                channelsMhz = wiphyInfo.bandInfo.band2g;
            }
            case WifiScanner.WIFI_BAND_5_GHZ -> {
                channelsMhz = wiphyInfo.bandInfo.band5g;
            }
            case WifiScanner.WIFI_BAND_5_GHZ_DFS_ONLY -> {
                channelsMhz = wiphyInfo.bandInfo.bandDfs;
            }
            case WifiScanner.WIFI_BAND_6_GHZ -> {
                channelsMhz = wiphyInfo.bandInfo.band6g;
            }
            case WifiScanner.WIFI_BAND_60_GHZ -> {
                channelsMhz = wiphyInfo.bandInfo.band60g;
            }
            default -> {
                Log.e(TAG, "getChannelsMhzForBand: Unsupported band: " + band);
                return new int[0];
            }
        }

        int[] bandArray = new int[channelsMhz.size()];
        for (int i = 0; i < channelsMhz.size(); i++) {
            bandArray[i] = channelsMhz.get(i);
        }
        return bandArray;
    }

    /**
     * Get the max number of SSIDs that the driver supports per scan.
     *
     * @param ifaceName Name of the interface.
     * @return max number of scan SSIDs, or 0 upon error.
     */
    public int getMaxSsidsPerScan(@NonNull String ifaceName) {
        if (useWificond()) {
            return mWificondManager.getMaxSsidsPerScan(ifaceName);
        }

        if (ifaceName == null) {
            Log.e(TAG, "ifaceName cannot be null");
            return 0;
        }
        if (!mIsInitialized) return 0;

        int wiphyIndex = mNl80211Utils.getWiphyIndex(ifaceName);
        if (wiphyIndex == -1) {
            Log.e(TAG, "Failed to get wiphy index for " + ifaceName);
            return 0;
        }

        Nl80211Utils.WiphyInfo wiphyInfo = mNl80211Utils.getWiphyInfo(wiphyIndex);
        if (wiphyInfo == null) {
            Log.e(TAG, "Failed to get wiphy info for index " + wiphyIndex);
            return 0;
        }

        return wiphyInfo.scanCapabilities.maxNumScanSsids;
    }

    /**
     * Register the provided listener for country code event.
     *
     * @param executor The Executor on which to execute the callbacks.
     * @param listener listener for country code changed events.
     * @return true on success, false on failure.
     */
    public boolean registerCountryCodeChangedListener(
            @NonNull Executor executor,
            @NonNull CountryCodeChangedListener listener) {
        if (useWificond()) {
            return mWificondManager.registerCountryCodeChangedListener(executor, listener);
        }

        if (executor == null) {
            Log.e(TAG, "executor cannot be null");
            return false;
        }
        if (listener == null) {
            Log.e(TAG, "listener cannot be null");
            return false;
        }
        if (!mIsInitialized) return false;

        mCountryCodeChangedListeners.put(listener, executor);
        return true;
    }

    private void notifyCountryCodeChangedListeners(String countryCode) {
        for (Map.Entry<CountryCodeChangedListener, Executor> listenerEntry
                : mCountryCodeChangedListeners.entrySet()) {
            CountryCodeChangedListener listener = listenerEntry.getKey();
            Executor executor = listenerEntry.getValue();
            executor.execute(() -> listener.onCountryCodeChanged(countryCode));
        }
    }

    /**
     * Unregister CountryCodeChangedListener with pid.
     *
     * @param listener listener which registered country code changed events.
     */
    public void unregisterCountryCodeChangedListener(
            @NonNull CountryCodeChangedListener listener) {
        if (useWificond()) {
            mWificondManager.unregisterCountryCodeChangedListener(listener);
            return;
        }

        if (listener == null) {
            Log.e(TAG, "listener cannot be null");
            return;
        }
        if (!mIsInitialized) return;

        mCountryCodeChangedListeners.remove(listener);
    }

    /**
     * Notifies the wificond daemon that the WiFi framework has successfully updated the Country
     * Code of the driver. The wificond daemon needs this notification if the device does not
     * support the NL80211_CMD_REG_CHANGED (otherwise it will find out on its own). The wificond
     * updates in internal state in response to this Country Code update.
     *
     * @param newCountryCode new country code. An ISO-3166-alpha2 country code which is 2-Character
     *                       alphanumeric.
     */
    public void notifyCountryCodeChanged(@Nullable String newCountryCode) {
        if (useWificond()) {
            mWificondManager.notifyCountryCodeChanged(newCountryCode);
            return;
        }

        if (!mIsInitialized) return;

        Log.i(TAG, "notifyCountryCodeChanged called with " + newCountryCode);
        updateIfaceInfoAfterRegChanged();
    }

    /**
     * Updates all of the cached info that depends on the current country code, namely the supported
     * bands and the band to wiphy index mapping.
     */
    private void updateIfaceInfoAfterRegChanged() {
        mNl80211Utils.clearWiphyInfoCaches();

        // Gather all of the update WiphyInfo for the active wiphys.
        Map<Integer, Nl80211Utils.WiphyInfo> updatedWiphyInfos = new ArrayMap<>();
        for (Integer wiphyIndex : new ArraySet<>(mActiveIfaceToWiphyIndex.values())) {
            Nl80211Utils.WiphyInfo wiphyInfo = mNl80211Utils.getWiphyInfo(wiphyIndex);
            if (wiphyInfo == null) {
                Log.e(TAG, "Could not get WiphyInfo for index " + wiphyIndex);
                continue;
            }

            updatedWiphyInfos.put(wiphyIndex, wiphyInfo);
            updateBandToWiphyIndexMapping(wiphyIndex, wiphyInfo);
        }

        // Update the ClientInterfaceInfo's cached WiphyInfo
        for (ClientInterfaceInfo clientIfaceInfo : mClientInterfaceInfos.values()) {
            Integer wiphyIndex = mActiveIfaceToWiphyIndex.get(clientIfaceInfo.ifName);
            if (wiphyIndex == null) {
                Log.wtf(TAG, "Iface " + clientIfaceInfo.ifName + " in mClientInterfaceInfos is not"
                        + " in mActiveIfaceToWiphyIndex!");
                continue;
            }

            Nl80211Utils.WiphyInfo newWiphyInfo = updatedWiphyInfos.get(wiphyIndex);
            if (newWiphyInfo == null) {
                Log.e(TAG, "Did not get new wiphy info for iface " + clientIfaceInfo.ifName);
                continue;
            }
            clientIfaceInfo.wiphyInfo = newWiphyInfo;
        }
    }

    /**
     * Register the provided callback handler for SoftAp events. The interface must first be created
     * using {@link #setupInterfaceForSoftApMode(String)}. The callback registration is valid until
     * the interface is deleted using {@link #tearDownSoftApInterface(String)} (no deregistration
     * method is provided).
     * <p>
     * Note that only one callback can be registered at a time - any registration overrides previous
     * registrations.
     *
     * @param ifaceName Name of the interface on which to register the callback.
     * @param executor The Executor on which to execute the callbacks.
     * @param callback Callback for AP events.
     * @return true on success, false on failure (e.g. when called on an interface which has not
     * been set up).
     *
     * @deprecated The usage is replaced by vendor HAL
     * {@code android.hardware.wifi.hostapd.V1_3.IHostapdCallback}.
     */
    @Deprecated
    public boolean registerApCallback(
            @NonNull String ifaceName,
            @NonNull Executor executor,
            @NonNull WifiNl80211Manager.SoftApCallback callback) {
        if (useWificond()) {
            return mWificondManager.registerApCallback(ifaceName, executor, callback);
        }

        if (ifaceName == null) {
            Log.e(TAG, "ifaceName cannot be null");
            return false;
        }
        if (executor == null) {
            Log.e(TAG, "executor cannot be null");
            return false;
        }
        if (callback == null) {
            Log.e(TAG, "callback cannot be null");
            return false;
        }

        ApInterfaceInfo ifaceInfo = mApInterfaceInfos.get(ifaceName);
        if (ifaceInfo == null) {
            Log.e(TAG, "No active interface found for " + ifaceName);
            return false;
        }

        ifaceInfo.callback = callback;
        ifaceInfo.executor = executor;
        return true;
    }

    /**
     * Send a management frame on the specified interface at the specified rate. Useful for probing
     * the link with arbitrary frames.
     *
     * Note: The interface must have been already set up using
     * {@link #setupInterfaceForClientMode(String, Executor, ScanEventCallback, ScanEventCallback)}
     * or {@link #setupInterfaceForSoftApMode(String)}.
     * @deprecated Not used anymore.
     *
     * @param ifaceName The interface on which to send the frame.
     * @param frame The raw byte array of the management frame to tramit.
     * @param mcs The MCS (modulation and coding scheme), i.e. rate, at which to transmit the
     *            frame. Specified per IEEE 802.11.
     * @param executor The Executor on which to execute the callbacks.
     * @param callback A {@link WifiNl80211Manager.SendMgmtFrameCallback} callback for results of
     *                 the operation.
     */
    @Deprecated
    public void sendMgmtFrame(
            @NonNull String ifaceName,
            @NonNull byte[] frame,
            int mcs,
            @NonNull Executor executor,
            @NonNull WifiNl80211Manager.SendMgmtFrameCallback callback) {
        if (useWificond()) {
            mWificondManager.sendMgmtFrame(ifaceName, frame, mcs, executor, callback);
            return;
        }

        // TODO (b/394409845): Remove all instances of sendMgmtFrame since it should be unused now.
        Log.wtf(TAG, "sendMgmtFrame was called even though we don't expect any users!");
        throw new UnsupportedOperationException();
    }

    /**
     * Gets information about all interfaces associated with a given wiphy.
     * @param wiphyIndex The index of the wiphy device.
     * @return A list of {@link Nl80211Utils.InterfaceInfo} objects, or null on failure.
     */
    @VisibleForTesting
    @Nullable
    public List<Nl80211Utils.InterfaceInfo> getInterfaces(int wiphyIndex) {
        return mNl80211Utils.getInterfaces(wiphyIndex);
    }

    /**
     * Returns client interfaces set up by {@link #setupInterfaceForClientMode(String, Executor,
     * ScanEventCallback, ScanEventCallback)}.
     */
    @VisibleForTesting
    public Map<String, ClientInterfaceInfo> getClientInterfaceInfos() {
        return mClientInterfaceInfos;
    }

    /**
     * Returns client interfaces set up by {@link #setupInterfaceForSoftApMode(String)}.
     */
    @VisibleForTesting
    public Map<String, ApInterfaceInfo> getApInterfaceInfos() {
        return mApInterfaceInfos;
    }
}
