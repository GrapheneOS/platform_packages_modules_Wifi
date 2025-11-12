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

import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_ATTR_IFNAME;
import static com.android.server.wifi.nl80211.NetlinkConstants.NL80211_CMD_GET_INTERFACE;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.net.wifi.WifiAnnotations;
import android.net.wifi.WifiScanner;
import android.net.wifi.nl80211.WifiNl80211Manager;
import android.os.Bundle;
import android.util.Log;

import com.android.net.module.util.netlink.StructNlMsgHdr;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;

/**
 * Provides functionalities that are implemented natively using Nl80211.
 */
public class Nl80211Native {
    private static final String TAG = "Nl80211Native";
    private boolean mVerboseLoggingEnabled;

    private final @NonNull Nl80211Proxy mNl80211Proxy;
    private final @NonNull WifiNl80211Manager mWificondManager;
    private final boolean mUseWificond;
    private boolean mIsInitialized;

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

    /**
     * Transmission counters obtained using {@link #getTxPacketCounters(String)}.
     */
    public static class TxPacketCounters {
        /** @hide */
        public TxPacketCounters(int txPacketSucceeded, int txPacketFailed) {
            this.txPacketSucceeded = txPacketSucceeded;
            this.txPacketFailed = txPacketFailed;
        }

        /**
         * Number of successfully transmitted packets.
         */
        public final int txPacketSucceeded;

        /**
         * Number of packet transmission failures.
         */
        public final int txPacketFailed;
    }

    public Nl80211Native(
            @NonNull Nl80211Proxy nl80211Proxy,
            @NonNull WifiNl80211Manager wificondManager,
            boolean useWificond) {
        mNl80211Proxy = nl80211Proxy;
        mWificondManager = wificondManager;
        mUseWificond = useWificond;
        Log.i(TAG, "useWificond: " + useWificond);
    }

    /**
     * Initialize this instance of the class.
     *
     * @return true if successful, false otherwise.
     */
    public boolean initialize() {
        if (mIsInitialized) return true;
        mIsInitialized = mNl80211Proxy.initialize();
        Log.i(TAG, "Initialization status: " + mIsInitialized);
        return mIsInitialized;
    }

    /**
     * Check whether this instance has been initialized.
     */
    public boolean isInitialized() {
        return mIsInitialized;
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
        if (mUseWificond) {
            mWificondManager.setOnServiceDeadCallback(deathEventHandler);
            return;
        }

        Log.w(TAG, "setWificondOnServiceDeadCallback ignored when mUseWificond is true.");
    }

    /**
     * Enable verbose logging.
     */
    public void enableVerboseLogging(boolean enable) {
        if (mUseWificond) {
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
        GenericNetlinkMsg request = mNl80211Proxy.createNl80211Request(NL80211_CMD_GET_INTERFACE,
                StructNlMsgHdr.NLM_F_DUMP);
        if (request == null) {
            Log.e(TAG, "Failed to create Nl80211 request");
            return null;
        }
        List<GenericNetlinkMsg> responses = mNl80211Proxy.sendMessageAndReceiveResponses(request);
        if (responses == null) {
            Log.e(TAG, "Failed to get interface names");
            return null;
        }
        List<String> interfaceNames = new ArrayList<>();
        for (GenericNetlinkMsg response : responses) {
            if (response.getAttribute(NL80211_ATTR_IFNAME) != null) {
                interfaceNames.add(response.getAttribute(NL80211_ATTR_IFNAME).getValueAsString());
            }
        }
        return interfaceNames;
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
        if (mUseWificond) {
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

        // TODO (b/394409845): Implement the Nl80211Proxy path for setting up client interface
        throw new UnsupportedOperationException();
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
        if (mUseWificond) {
            return mWificondManager.tearDownClientInterface(ifaceName);
        }

        if (ifaceName == null) {
            Log.e(TAG, "ifaceName cannot be null");
            return false;
        }
        if (!mIsInitialized) return false;

        // TODO (b/394409845): Implement the Nl80211Proxy path for tearing down client interface
        throw new UnsupportedOperationException();
    }

    /**
     * Set up interface as a Soft AP.
     *
     * @param ifaceName Name of the interface to configure.
     * @return true on success.
     */
    public boolean setupInterfaceForSoftApMode(@NonNull String ifaceName) {
        if (mUseWificond) {
            return mWificondManager.setupInterfaceForSoftApMode(ifaceName);
        }

        if (ifaceName == null) {
            Log.e(TAG, "ifaceName cannot be null");
            return false;
        }
        if (!mIsInitialized) return false;

        // TODO (b/394409845): Implement the Nl80211Proxy path
        throw new UnsupportedOperationException();
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
        if (mUseWificond) {
            return mWificondManager.tearDownSoftApInterface(ifaceName);
        }

        if (ifaceName == null) {
            Log.e(TAG, "ifaceName cannot be null");
            return false;
        }
        if (!mIsInitialized) return false;

        // TODO (b/394409845): Implement the Nl80211Proxy path
        throw new UnsupportedOperationException();
    }

    /**
     * Tear down all interfaces, whether clients (STA) or Soft AP.
     *
     * @return Returns true on success.
     */
    public boolean tearDownInterfaces() {
        if (mUseWificond) {
            return mWificondManager.tearDownInterfaces();
        }

        if (!mIsInitialized) return false;

        // TODO (b/394409845): Implement the Nl80211Proxy path
        throw new UnsupportedOperationException();
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
     * @param hiddenNetworkSSIDs List of hidden networks to be scanned for, a null indicates that
     *                           no hidden frequencies will be scanned for.
     * @param extraScanningParams bundle of extra scanning parameters.
     * @return Returns one of the scan status codes defined in {@code WifiScanner#REASON_*}
     */
    public int startScan(
            @NonNull String ifaceName,
            @WifiAnnotations.ScanType int scanType,
            @Nullable Set<Integer> freqs,
            @Nullable List<byte[]> hiddenNetworkSSIDs,
            @Nullable Bundle extraScanningParams) {
        if (mUseWificond) {
            return mWificondManager.startScan2(
                    ifaceName, scanType, freqs, hiddenNetworkSSIDs, extraScanningParams);
        }

        if (ifaceName == null) {
            Log.e(TAG, "ifaceName cannot be null");
            return WifiScanner.REASON_UNSPECIFIED;
        }
        if (!mIsInitialized) return WifiScanner.REASON_UNSPECIFIED;

        // TODO (b/394409845): Implement the Nl80211Proxy path for starting a scan
        throw new UnsupportedOperationException();
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
        if (mUseWificond) {
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
        if (mUseWificond) {
            return wificondScansToNl80211NativeScans(
                    mWificondManager.getScanResults(ifaceName, scanType));
        }

        if (ifaceName == null) {
            Log.e(TAG, "ifaceName cannot be null");
            return new ArrayList<>();
        }
        if (!mIsInitialized) return new ArrayList<>();

        // TODO (b/394409845): Implement the Nl80211Proxy path for getting scan results
        throw new UnsupportedOperationException();
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
        if (mUseWificond) {
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

        // TODO (b/394409845): Implement the Nl80211Proxy path
        throw new UnsupportedOperationException();
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
        if (mUseWificond) {
            return mWificondManager.stopPnoScan(ifaceName);
        }

        if (ifaceName == null) {
            Log.e(TAG, "ifaceName cannot be null");
            return false;
        }
        if (!mIsInitialized) return false;

        // TODO (b/394409845): Implement the Nl80211Proxy path
        throw new UnsupportedOperationException();
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
        if (mUseWificond) {
            mWificondManager.abortScan(ifaceName);
            return;
        }

        if (ifaceName == null) {
            Log.e(TAG, "ifaceName cannot be null");
            return;
        }
        if (!mIsInitialized) return;

        // TODO (b/394409845): Implement the Nl80211Proxy path
        throw new UnsupportedOperationException();
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
        if (mUseWificond) {
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
        if (mUseWificond) {
            android.net.wifi.nl80211.DeviceWiphyCapabilities wificondCaps =
                    mWificondManager.getDeviceWiphyCapabilities(ifaceName);
            if (wificondCaps == null) return null;
            return new DeviceWiphyCapabilities(wificondCaps);
        }

        if (ifaceName == null) {
            Log.e(TAG, "ifaceName cannot be null");
            return null;
        }
        if (!mIsInitialized) return null;

        // TODO (b/394409845): Implement the Nl80211Proxy path
        throw new UnsupportedOperationException();
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
        if (mUseWificond) {
            return mWificondManager.getChannelsMhzForBand(band);
        }

        if (!mIsInitialized) return new int[0];

        // TODO (b/394409845): Implement the Nl80211Proxy path
        throw new UnsupportedOperationException();
    }

    /**
     * Get current transmit (Tx) packet counters of the specified interface. The interface must
     * have been already set up using
     * {@link #setupInterfaceForClientMode(String, Executor, ScanEventCallback, ScanEventCallback)}
     * or {@link #setupInterfaceForSoftApMode(String)}.
     *
     * @param ifaceName Name of the interface.
     * @return {@link TxPacketCounters} of the current interface or null on error (e.g. when
     * called before the interface has been set up).
     */
    @Nullable
    public TxPacketCounters getTxPacketCounters(@NonNull String ifaceName) {
        if (mUseWificond) {
            WifiNl80211Manager.TxPacketCounters result =
                    mWificondManager.getTxPacketCounters(ifaceName);
            if (result == null) return null;
            return new TxPacketCounters(result.txPacketSucceeded, result.txPacketFailed);
        }

        if (ifaceName == null) {
            Log.e(TAG, "ifaceName cannot be null");
            return null;
        }
        if (!mIsInitialized) return null;

        // TODO (b/394409845): Implement the Nl80211Proxy path
        throw new UnsupportedOperationException();
    }

    /**
     * Get the max number of SSIDs that the driver supports per scan.
     *
     * @param ifaceName Name of the interface.
     */
    public int getMaxSsidsPerScan(@NonNull String ifaceName) {
        if (mUseWificond) {
            return mWificondManager.getMaxSsidsPerScan(ifaceName);
        }

        if (ifaceName == null) {
            Log.e(TAG, "ifaceName cannot be null");
            return 0;
        }
        if (!mIsInitialized) return 0;

        // TODO (b/394409845): Implement the Nl80211Proxy path
        throw new UnsupportedOperationException();
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
        if (mUseWificond) {
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

        // TODO (b/394409845): Implement the Nl80211Proxy path
        throw new UnsupportedOperationException();
    }

    /**
     * Unregister CountryCodeChangedListener with pid.
     *
     * @param listener listener which registered country code changed events.
     */
    public void unregisterCountryCodeChangedListener(
            @NonNull CountryCodeChangedListener listener) {
        if (mUseWificond) {
            mWificondManager.unregisterCountryCodeChangedListener(listener);
            return;
        }

        if (listener == null) {
            Log.e(TAG, "listener cannot be null");
            return;
        }
        if (!mIsInitialized) return;

        // TODO (b/394409845): Implement the Nl80211Proxy path
        throw new UnsupportedOperationException();
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
        if (mUseWificond) {
            mWificondManager.notifyCountryCodeChanged(newCountryCode);
            return;
        }

        if (!mIsInitialized) return;

        // TODO (b/394409845): Implement the Nl80211Proxy path
        throw new UnsupportedOperationException();
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
    public boolean registerWificondApCallback(
            @NonNull String ifaceName,
            @NonNull Executor executor,
            @NonNull WifiNl80211Manager.SoftApCallback callback) {
        if (mUseWificond) {
            return mWificondManager.registerApCallback(ifaceName, executor, callback);
        }

        Log.wtf(TAG, "registerApCallback should not be called when Nl80211Proxy implementation is"
                + "used. Please use android.hardware.wifi.hostapd.V1_3.IHostapdCallback instead.");
        throw new UnsupportedOperationException();
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
        if (mUseWificond) {
            mWificondManager.sendMgmtFrame(ifaceName, frame, mcs, executor, callback);
            return;
        }

        // TODO (b/394409845): Remove all instances of sendMgmtFrame since it should be unused now.
        Log.wtf(TAG, "sendMgmtFrame was called even though we don't expect any users!");
        throw new UnsupportedOperationException();
    }
}
