/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.server.wifi.rtt;

import static android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND_SERVICE;
import static android.net.wifi.WifiInfo.DEFAULT_MAC_ADDRESS;
import static android.net.wifi.rtt.ResponderConfig.RESPONDER_STA;
import static android.net.wifi.rtt.WifiRttManager.CHARACTERISTICS_KEY_BOOLEAN_LCI;
import static android.net.wifi.rtt.WifiRttManager.CHARACTERISTICS_KEY_BOOLEAN_LCR;
import static android.net.wifi.rtt.WifiRttManager.CHARACTERISTICS_KEY_BOOLEAN_NTB_INITIATOR;
import static android.net.wifi.rtt.WifiRttManager.CHARACTERISTICS_KEY_BOOLEAN_ONE_SIDED_RTT;
import static android.net.wifi.rtt.WifiRttManager.CHARACTERISTICS_KEY_BOOLEAN_RANGING_FRAME_PROTECTION_SUPPORTED;
import static android.net.wifi.rtt.WifiRttManager.CHARACTERISTICS_KEY_BOOLEAN_SECURE_HE_LTF_SUPPORTED;
import static android.net.wifi.rtt.WifiRttManager.CHARACTERISTICS_KEY_BOOLEAN_STA_RESPONDER;
import static android.net.wifi.rtt.WifiRttManager.CHARACTERISTICS_KEY_INT_MAX_SUPPORTED_SECURE_HE_LTF_PROTO_VERSION;

import static com.android.server.wifi.WifiSettingsConfigStore.WIFI_VERBOSE_LOGGING_ENABLED;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.content.AttributionSource;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.net.MacAddress;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiSsid;
import android.net.wifi.aware.IWifiAwareMacAddressProvider;
import android.net.wifi.aware.MacAddrMapping;
import android.net.wifi.aware.WifiAwareManager;
import android.net.wifi.rtt.ContinuousRangingResultCallback;
import android.net.wifi.rtt.IContinuousRangingResultCallback;
import android.net.wifi.rtt.IProximityDetectionMacAddressCallback;
import android.net.wifi.rtt.IRttCallback;
import android.net.wifi.rtt.IWifiRttManager;
import android.net.wifi.rtt.PasnConfig;
import android.net.wifi.rtt.ProximityDetectionCharacteristics;
import android.net.wifi.rtt.RangingRequest;
import android.net.wifi.rtt.RangingResult;
import android.net.wifi.rtt.RangingResultCallback;
import android.net.wifi.rtt.ResponderConfig;
import android.net.wifi.rtt.ResponderLocation;
import android.net.wifi.rtt.SecureRangingConfig;
import android.net.wifi.rtt.WifiRttManager;
import android.net.wifi.util.Environment;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.os.PowerManager;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.os.UserHandle;
import android.os.WorkSource;
import android.os.WorkSource.WorkChain;
import android.text.TextUtils;
import android.util.Log;
import android.util.SparseIntArray;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.util.WakeupMessage;
import com.android.modules.utils.BasicShellCommandHandler;
import com.android.modules.utils.build.SdkLevel;
import com.android.server.wifi.ActiveModeWarden;
import com.android.server.wifi.BuildProperties;
import com.android.server.wifi.ClientModeImplListener;
import com.android.server.wifi.ClientModeImplMonitor;
import com.android.server.wifi.Clock;
import com.android.server.wifi.ConcreteClientModeManager;
import com.android.server.wifi.FrameworkFacade;
import com.android.server.wifi.HalDeviceManager;
import com.android.server.wifi.SsidTranslator;
import com.android.server.wifi.SystemBuildProperties;
import com.android.server.wifi.WifiConfigManager;
import com.android.server.wifi.WifiNative;
import com.android.server.wifi.WifiSettingsConfigStore;
import com.android.server.wifi.hal.WifiRttController;
import com.android.server.wifi.proto.nano.WifiMetricsProto;
import com.android.server.wifi.util.StringUtil;
import com.android.server.wifi.util.WifiPermissionsUtil;
import com.android.wifi.flags.Flags;
import com.android.wifi.resources.R;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.concurrent.Executor;

/**
 * Implementation of the IWifiRttManager AIDL interface and of the RttService state manager.
 */
public class RttServiceImpl extends IWifiRttManager.Stub {
    private static final String TAG = "RttServiceImpl";
    // TODO set to false after testing
    private static final boolean VDBG = false; // STOPSHIP if true
    private boolean mVerboseLoggingEnabled = false;
    private boolean mVerboseHalLoggingEnabled = false;

    private final Context mContext;
    private final RttShellCommand mShellCommand;
    private Clock mClock;
    private WifiAwareManager mAwareManager;
    private WifiRttController mWifiRttController;
    private HalDeviceManager mHalDeviceManager;
    private RttMetrics mRttMetrics;
    private WifiPermissionsUtil mWifiPermissionsUtil;
    private ActivityManager mActivityManager;
    private PowerManager mPowerManager;
    private long mLastRequestTimestamp;
    private final BuildProperties mBuildProperties;
    private FrameworkFacade mFrameworkFacade;
    private WifiRttController.Capabilities mCapabilities;
    private RttServiceSynchronized mRttServiceSynchronized;
    private SsidTranslator mWifiSsidTranslator;
    private WifiNative mWifiNative;
    private ActiveModeWarden mActiveModeWarden;
    private int mCurrentWifiState = WifiManager.WIFI_STATE_UNKNOWN;
    private ClientModeImplMonitor mClientModeImplMonitor;
    @VisibleForTesting
    String mProximityRangingIfaceName = null;
    @VisibleForTesting
    SupplicantWifiRttController mSupplicantWifiRttController;
    private SupplicantWifiRttController.ProximityRangingCapabilities mProximityRangingCapabilities;

    /* package */ static final String HAL_RANGING_TIMEOUT_TAG = TAG + " HAL Ranging Timeout";

    @VisibleForTesting
    public static final long HAL_RANGING_TIMEOUT_MS = 5_000; // 5 sec
    @VisibleForTesting
    public static final long HAL_AWARE_RANGING_TIMEOUT_MS = 10_000; // 10 sec

    @VisibleForTesting
    public static final long HAL_PROXIMITY_RANGING_TIMEOUT_MS = 120_000; // 120 sec

    // arbitrary, larger than anything reasonable
    /* package */ static final int MAX_QUEUED_PER_UID = 20;
    private WifiConfigManager mWifiConfigManager;
    static final int MAX_ALLOWED_PEERS_PER_CONTINUOUS_RANGING_REQUEST = 1;
    static final String DEFAULT_PR_DEVICE_NAME_PREFIX = "Android_PR_";
    @VisibleForTesting
    String mProximityRangingDeviceName = null;
    @VisibleForTesting
    MacAddress mProximityRangingRandomizedMacAddress = null;
    @VisibleForTesting
    MacAddress mConnectedIfaceMacAddress = null;
    private final RemoteCallbackList<IProximityDetectionMacAddressCallback>
            mProximityDetectionMacAddressCallbacks = new RemoteCallbackList<>();

    @VisibleForTesting
    class ClientModeImplListenerInternal implements ClientModeImplListener {
        @Override
        public void onL2Connected(@NonNull ConcreteClientModeManager clientModeManager) {
            if (mVerboseLoggingEnabled) {
                Log.d(TAG, "onL2Connected: clientModeManager=" + clientModeManager);
            }
            if (mProximityRangingCapabilities == null
                    || mProximityRangingCapabilities.isConnectedMacRandomizationSupported) {
                return;
            }
            if (clientModeManager.getInterfaceName() != null
                    && clientModeManager.getInterfaceName().equals(mProximityRangingIfaceName)) {
                WifiInfo wifiInfo = clientModeManager.getConnectionInfo();
                final String wifiInfoMac = wifiInfo.getMacAddress();
                if (!TextUtils.isEmpty(wifiInfoMac)
                        && !TextUtils.equals(wifiInfoMac, DEFAULT_MAC_ADDRESS)) {
                    mConnectedIfaceMacAddress =  MacAddress.fromString(wifiInfoMac);
                    // Don't set the MAC Address when there is an ongoing continuous ranging
                    // session. Let the HAL handle it(Abort or continue using the same MAC).
                    if (mRttServiceSynchronized.mContinuousRangingSessions.isEmpty()) {
                        updateProximityRangingMacAddressAndNotify();
                    }
                }
            }
        }

        @Override
        public void onConnectionEnd(@NonNull ConcreteClientModeManager clientModeManager) {
            if (mVerboseLoggingEnabled) {
                Log.d(TAG, "onConnectionEnd: clientModeManager=" + clientModeManager);
            }
            if (mProximityRangingCapabilities == null
                    || mProximityRangingCapabilities.isConnectedMacRandomizationSupported) {
                return;
            }
            // TODO In the case where the interface is teared down, it's possible that the
            //  interface name is already be null by the time this onConnectionEnd happens.
            //  Add additional check like primary role to handle this case.
            if (clientModeManager.getInterfaceName() != null
                    && clientModeManager.getInterfaceName().equals(mProximityRangingIfaceName)) {
                mConnectedIfaceMacAddress = null;
            }
        }
    }

    private final ActiveModeWarden.PrimaryClientModeManagerChangedCallback
        mPrimaryCmmChangedCallback =
            new ActiveModeWarden.PrimaryClientModeManagerChangedCallback() {
                @Override
                public void onChange(
                        @Nullable ConcreteClientModeManager prevPrimaryClientModeManager,
                        @Nullable ConcreteClientModeManager newPrimaryClientModeManager) {
                    updateSupplicantRttController(newPrimaryClientModeManager);
                }
            };

    private void updateSupplicantRttController(
            @Nullable ConcreteClientModeManager newPrimaryCmm) {
        // If no new primary, do nothing. Destruction on Wi-Fi OFF is handled in setWifiState.
        if (newPrimaryCmm == null) {
            return;
        }

        // If the interface name is the same, do nothing.
        if (newPrimaryCmm.getInterfaceName() != null
                && newPrimaryCmm.getInterfaceName().equals(mProximityRangingIfaceName)) {
            Log.i(TAG, "updateSupplicantRttController: new primary CMM has the same"
                    + "interface name as the current one, no update needed. ifaceName="
                    + newPrimaryCmm.getInterfaceName());
            return;
        }
        Log.i(TAG, "updateSupplicantRttController: newPrimaryCmm=" + newPrimaryCmm);
        // The old primary supplicant controller destruction will be handled
        // by ISupplicant#removeInterface when the old primary CMM is removed.
        mProximityRangingIfaceName = newPrimaryCmm.getInterfaceName();
        Log.i(TAG, "Creating new SupplicantWifiRttController for interface: "
                + mProximityRangingIfaceName);
        mSupplicantWifiRttController = mWifiNative
                .createSupplicantWifiRttController(mProximityRangingIfaceName);
        if (mSupplicantWifiRttController != null) {
            Log.i(TAG, "Successfully created SupplicantWifiRttController");
            mSupplicantWifiRttController.registerRttEventCallback(
                    mSupplicantRttEventCallback);
            if (!initializeSupplicantWifiRttController()) {
                Log.e(TAG, "Failed to initialize SupplicantWifiRttController");
                mSupplicantWifiRttController = null; // Nullify on failure
                mProximityRangingCapabilities = null;
                mProximityRangingIfaceName = null;
            }
        } else {
            Log.e(TAG, "Failed to create SupplicantWifiRttController");
            mProximityRangingIfaceName = null;
        }
    }

    /**
     * Callback for handling ranging results and status updates from the supplicant HAL.
     * This object is registered with the {@link SupplicantWifiRttController} to receive
     * asynchronous events related to ranging operations.
     */
    @VisibleForTesting
    final SupplicantWifiRttController.SupplicantWifiRttControllerEventCallback
            mSupplicantRttEventCallback =
            new SupplicantWifiRttController.SupplicantWifiRttControllerEventCallback() {
                @Override
                public void onRangingResults(int cmdId, List<RangingResult> rangingResults) {
                    if (mVerboseLoggingEnabled) {
                        Log.d(TAG, "onRangingResults: cmdId=" + cmdId);
                    }
                    mRttServiceSynchronized.mHandler.post(
                            () -> mRttServiceSynchronized.onContinuousRangingResults(cmdId,
                                    rangingResults));
                }
                @Override
                public void onContinuousRangingStatusChanged(int cmdId, int code) {
                    if (mVerboseLoggingEnabled) {
                        Log.d(TAG, "onContinuousRangingStatusChanged: cmdId=" + cmdId
                                + ", code=" + code);
                    }
                    // Not used for now.
                }
                @Override
                public void onContinuousRangingTerminated(int cmdId,
                        @ContinuousRangingResultCallback.RangingTerminateReason int reason) {
                    if (mVerboseLoggingEnabled) {
                        Log.d(TAG, "onContinuousRangingTerminated: cmdId=" + cmdId
                                + ", reason=" + reason);
                    }
                    mRttServiceSynchronized.mHandler.post(
                            () -> mRttServiceSynchronized.removeContinuousRangingSession(cmdId,
                                    reason));
                }
            };

    private final WifiRttController.RttControllerRangingResultsCallback mRangingResultsCallback =
            new WifiRttController.RttControllerRangingResultsCallback() {
                @Override
                public void onRangingResults(int cmdId, List<RangingResult> rangingResults) {
                    if (mVerboseLoggingEnabled) Log.d(TAG, "onRangingResults: cmdId=" + cmdId);
                    mRttServiceSynchronized.mHandler.post(() -> {
                        mRttServiceSynchronized.onRangingResults(cmdId, rangingResults);
                    });
                }
            };

    private final HalDeviceManager.InterfaceRttControllerLifecycleCallback mRttLifecycleCb =
            new HalDeviceManager.InterfaceRttControllerLifecycleCallback() {
                @Override
                public void onNewRttController(WifiRttController controller) {
                    if (mVerboseLoggingEnabled) {
                        Log.d(TAG, "onNewRttController: controller=" + controller);
                    }
                    boolean changed = mWifiRttController == null;
                    mWifiRttController = controller;
                    mWifiRttController.registerRangingResultsCallback(mRangingResultsCallback);
                    if (changed) {
                        enableIfPossible();
                    }
                }

                @Override
                public void onRttControllerDestroyed() {
                    if (mVerboseLoggingEnabled) Log.d(TAG, "onRttControllerDestroyed");
                    mWifiRttController = null;
                    disable();
                }
            };

    public RttServiceImpl(Context context) {
        mContext = context;
        mBuildProperties = new SystemBuildProperties();
        mFrameworkFacade = new FrameworkFacade();
        mShellCommand = new RttShellCommand();
        mShellCommand.reset();
    }

    @VisibleForTesting
    void setRttCapabilitiesForTest(WifiRttController.Capabilities capabilities) {
        mCapabilities = capabilities;
    }


    private void updateVerboseLoggingEnabled() {
        final int verboseAlwaysOnLevel = mContext.getResources().getInteger(
                R.integer.config_wifiVerboseLoggingAlwaysOnLevel);
        mVerboseLoggingEnabled = mFrameworkFacade.isVerboseLoggingAlwaysOn(verboseAlwaysOnLevel,
                mBuildProperties) || mVerboseHalLoggingEnabled;
    }

    /*
     * Shell command: adb shell cmd wifirtt ...
     */

    // If set to 0: normal behavior, if set to 1: do not allow any caller (including system
    // callers) privileged API access
    private static final String CONTROL_PARAM_OVERRIDE_ASSUME_NO_PRIVILEGE_NAME =
            "override_assume_no_privilege";
    private static final int CONTROL_PARAM_OVERRIDE_ASSUME_NO_PRIVILEGE_DEFAULT = 0;

    private class RttShellCommand extends BasicShellCommandHandler {
        private Map<String, Integer> mControlParams = new HashMap<>();

        @Override
        public int onCommand(String cmd) {
            final int uid = Binder.getCallingUid();
            if (uid != 0) {
                throw new SecurityException(
                        "Uid " + uid + " does not have access to wifirtt commands");
            }

            final PrintWriter pw = getErrPrintWriter();
            try {
                if ("reset".equals(cmd)) {
                    reset();
                    return 0;
                } else if ("get".equals(cmd)) {
                    String name = getNextArgRequired();
                    if (!mControlParams.containsKey(name)) {
                        pw.println("Unknown parameter name -- '" + name + "'");
                        return -1;
                    }
                    getOutPrintWriter().println(mControlParams.get(name));
                    return 0;
                } else if ("set".equals(cmd)) {
                    String name = getNextArgRequired();
                    String valueStr = getNextArgRequired();

                    if (!mControlParams.containsKey(name)) {
                        pw.println("Unknown parameter name -- '" + name + "'");
                        return -1;
                    }

                    try {
                        mControlParams.put(name, Integer.valueOf(valueStr));
                        return 0;
                    } catch (NumberFormatException e) {
                        pw.println("Can't convert value to integer -- '" + valueStr + "'");
                        return -1;
                    }
                } else if ("get_capabilities".equals(cmd)) {
                    if (mCapabilities == null && mWifiRttController != null) {
                        mCapabilities = mWifiRttController.getRttCapabilities();
                    }
                    JSONObject j = new JSONObject();
                    if (mCapabilities != null) {
                        try {
                            j.put("rttOneSidedSupported", mCapabilities.oneSidedRttSupported);
                            j.put("rttFtmSupported", mCapabilities.rttFtmSupported);
                            j.put("lciSupported", mCapabilities.lciSupported);
                            j.put("lcrSupported", mCapabilities.lcrSupported);
                            j.put("responderSupported", mCapabilities.responderSupported);
                            j.put("mcVersion", mCapabilities.mcVersion);
                            j.put("ntbInitiatorSupported", mCapabilities.ntbInitiatorSupported);
                            j.put("ntbResponderSupported", mCapabilities.ntbResponderSupported);
                            j.put("secureHeLtfSupported", mCapabilities.secureHeLtfSupported);
                            j.put("rangingFrameProtectionSupported",
                                    mCapabilities.rangingFrameProtectionSupported);
                        } catch (JSONException e) {
                            Log.e(TAG, "onCommand: get_capabilities e=" + e);
                        }
                    }
                    getOutPrintWriter().println(j.toString());
                    return 0;
                } else {
                    handleDefaultCommands(cmd);
                }
            } catch (Exception e) {
                pw.println("Exception: " + e);
            }
            return -1;
        }

        @Override
        public void onHelp() {
            final PrintWriter pw = getOutPrintWriter();

            pw.println("Wi-Fi RTT (wifirt) commands:");
            pw.println("  help");
            pw.println("    Print this help text.");
            pw.println("  reset");
            pw.println("    Reset parameters to default values.");
            pw.println("  get_capabilities: prints out the RTT capabilities as a JSON string");
            pw.println("  get <name>");
            pw.println("    Get the value of the control parameter.");
            pw.println("  set <name> <value>");
            pw.println("    Set the value of the control parameter.");
            pw.println("  Control parameters:");
            for (String name : mControlParams.keySet()) {
                pw.println("    " + name);
            }
            pw.println();
        }

        public int getControlParam(String name) {
            if (mControlParams.containsKey(name)) {
                return mControlParams.get(name);
            }

            Log.wtf(TAG, "getControlParam for unknown variable: " + name);
            return 0;
        }

        public void reset() {
            mControlParams.put(CONTROL_PARAM_OVERRIDE_ASSUME_NO_PRIVILEGE_NAME,
                    CONTROL_PARAM_OVERRIDE_ASSUME_NO_PRIVILEGE_DEFAULT);
        }
    }

    /*
     * INITIALIZATION
     */

    /**
     * Initializes the RTT service (usually with objects from an injector).
     *
     * @param looper The looper on which to synchronize operations.
     * @param clock A mockable clock.
     * @param awareManager The Wi-Fi Aware service (binder) if supported on the system.
     * @param rttMetrics The Wi-Fi RTT metrics object.
     * @param wifiPermissionsUtil Utility for permission checks.
     * @param settingsConfigStore Used for retrieving verbose logging level.
     * @param halDeviceManager The HAL device manager object.
     * @param wifiConfigManager The Wi-Fi configuration manager used to retrieve credentials for
     *                          secure ranging
     * @param ssidTranslator SSID translator
     */
    public void start(Looper looper, Clock clock, WifiAwareManager awareManager,
            RttMetrics rttMetrics, WifiPermissionsUtil wifiPermissionsUtil,
            WifiSettingsConfigStore settingsConfigStore, HalDeviceManager halDeviceManager,
            WifiConfigManager wifiConfigManager, SsidTranslator ssidTranslator,
            WifiNative wifiNative, ActiveModeWarden activeModeWarden,
            ClientModeImplMonitor clientModeImplMonitor) {
        mClock = clock;
        mAwareManager = awareManager;
        mHalDeviceManager = halDeviceManager;
        mRttMetrics = rttMetrics;
        mWifiPermissionsUtil = wifiPermissionsUtil;
        mRttServiceSynchronized = new RttServiceSynchronized(looper);
        mActivityManager = (ActivityManager) mContext.getSystemService(Context.ACTIVITY_SERVICE);
        mPowerManager = mContext.getSystemService(PowerManager.class);
        mWifiConfigManager = wifiConfigManager;
        mWifiSsidTranslator = ssidTranslator;
        mWifiNative = wifiNative;
        mActiveModeWarden = activeModeWarden;
        mClientModeImplMonitor = clientModeImplMonitor;
        if (isProximityRangingFeatureSupported()) {
            setProximityRangingDeviceName(generateProximityRangingRandomizedDeviceName());
            mProximityRangingRandomizedMacAddress = generateProximityRangingRandomizedMacAddress();
            if (mClientModeImplMonitor != null) {
                mClientModeImplMonitor.registerListener(new ClientModeImplListenerInternal());
            }
            mActiveModeWarden
                    .registerPrimaryClientModeManagerChangedCallback(mPrimaryCmmChangedCallback);
        }

        mRttServiceSynchronized.mHandler.post(() -> {
            IntentFilter intentFilter = new IntentFilter();
            intentFilter.addAction(PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED);
            BroadcastReceiver idleModeChangeReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    String action = intent.getAction();
                    if (mVerboseLoggingEnabled) {
                        Log.v(TAG, "BroadcastReceiver: action=" + action);
                    }
                    if (PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED.equals(action)) {
                        if (mPowerManager.isDeviceIdleMode()) {
                            disable();
                        } else {
                            enableIfPossible();
                        }
                    }
                }};
            if (Flags.monitorIntentForAllUsers() && Environment.isSdkAtLeastC()) {
                mContext.registerReceiverForAllUsers(idleModeChangeReceiver,
                        intentFilter, null, mRttServiceSynchronized.mHandler);
            } else {
                mContext.registerReceiver(idleModeChangeReceiver, intentFilter, null,
                        mRttServiceSynchronized.mHandler);
            }

            settingsConfigStore.registerChangeListener(
                    WIFI_VERBOSE_LOGGING_ENABLED,
                    (key, newValue) -> enableVerboseLogging(newValue),
                    mRttServiceSynchronized.mHandler);
            enableVerboseLogging(settingsConfigStore.get(WIFI_VERBOSE_LOGGING_ENABLED));

            intentFilter = new IntentFilter();
            intentFilter.addAction(LocationManager.MODE_CHANGED_ACTION);
            mContext.registerReceiverForAllUsers(new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    if (mVerboseLoggingEnabled) {
                        Log.v(TAG, "onReceive: MODE_CHANGED_ACTION: intent=" + intent);
                    }
                    if (mWifiPermissionsUtil.isLocationModeEnabled()) {
                        enableIfPossible();
                    } else {
                        disable();
                    }
                }
            }, intentFilter, null, mRttServiceSynchronized.mHandler);

            mHalDeviceManager.initialize();
            mHalDeviceManager.registerStatusListener(() -> {
                if (VDBG) Log.d(TAG, "hdm.onStatusChanged");
                if (mHalDeviceManager.isStarted()) {
                    mHalDeviceManager.registerRttControllerLifecycleCallback(mRttLifecycleCb,
                            mRttServiceSynchronized.mHandler);
                }
            }, mRttServiceSynchronized.mHandler);
            if (mHalDeviceManager.isStarted()) {
                mHalDeviceManager.registerRttControllerLifecycleCallback(
                        mRttLifecycleCb, mRttServiceSynchronized.mHandler);
            }
        });
    }

    private void enableVerboseLogging(boolean verboseEnabled) {
        mVerboseHalLoggingEnabled = verboseEnabled || VDBG;
        updateVerboseLoggingEnabled();
        mRttMetrics.enableVerboseLogging(mVerboseLoggingEnabled);
        if (mWifiRttController != null) {
            mWifiRttController.enableVerboseLogging(mVerboseLoggingEnabled);
        }
    }



    /**
     * Handles the transition to boot completed phase
     */
    public void handleBootCompleted() {
        updateVerboseLoggingEnabled();
    }

    private boolean isProximityRangingFeatureSupported() {
        return Environment.isSdkNewerThanB() && Flags.proximityRanging();
    }

    private boolean isSupplicantHalProximityRangingFeatureSupported() {
        return mWifiNative.isSupplicantAidlServiceVersionAtLeast(5)
                || mWifiNative.isUsingAidlMainlineSupplicantService();
    }

    /**
     * Set the current Wi-Fi state.
     * @param newState The new Wi-Fi state.
     */
    public void setWifiState(int newState) {
        if (VDBG) {
            Log.d(TAG, "setWifiState: newState=" + newState + ", mCurrentWifiState="
                    + mCurrentWifiState);
        }
        if (mCurrentWifiState == newState) {
            return;
        }

        if (!(isProximityRangingFeatureSupported()
                && isSupplicantHalProximityRangingFeatureSupported())) {
            return;
        }
        if (newState != WifiManager.WIFI_STATE_ENABLED
                && newState != WifiManager.WIFI_STATE_DISABLED) {
            return;
        }
        boolean isEnabled = newState == WifiManager.WIFI_STATE_ENABLED;
        if (!isEnabled) { // isEnabled is false, so Wi-Fi is disabled
            Log.i(TAG, "Wi-Fi Turned OFF - Remove SupplicantWifiRttController");
            if (mSupplicantWifiRttController != null) {
                mRttServiceSynchronized.cleanUpContinuousRangingSessions(0, null,
                        ContinuousRangingResultCallback.TERMINATE_REASON_UNKNOWN);
                mSupplicantWifiRttController = null;
                mProximityRangingIfaceName = null;
            }
        }
        mCurrentWifiState = newState;
    }

    /*
     * ASYNCHRONOUS DOMAIN - can be called from different threads!
     */

    /**
     * Proxy for the final native call of the parent class. Enables mocking of
     * the function.
     */
    public int getMockableCallingUid() {
        return getCallingUid();
    }

    /**
     * Enable the API if possible: broadcast notification & start launching any queued requests
     *
     * If possible:
     * - RTT HAL is available
     * - Not in Idle mode
     * - Location Mode allows Wi-Fi based locationing
     */
    public void enableIfPossible() {
        boolean isAvailable = isAvailable();
        if (VDBG) Log.v(TAG, "enableIfPossible: isAvailable=" + isAvailable);
        if (!isAvailable) {
            return;
        }
        sendRttStateChangedBroadcast(true);
        mRttServiceSynchronized.mHandler.post(() -> {
            // queue should be empty at this point (but this call allows validation)
            mRttServiceSynchronized.executeNextRangingRequestIfPossible(false);
        });
    }

    /**
     * Disable the API:
     * - Clean-up (fail) pending requests
     * - Broadcast notification
     */
    public void disable() {
        if (VDBG) Log.v(TAG, "disable");
        sendRttStateChangedBroadcast(false);
        mRttServiceSynchronized.mHandler.post(() -> {
            mRttServiceSynchronized.cleanUpOnDisable();
        });
    }

    @Override
    public int handleShellCommand(@NonNull ParcelFileDescriptor in,
            @NonNull ParcelFileDescriptor out, @NonNull ParcelFileDescriptor err,
            @NonNull String[] args) {
        return mShellCommand.exec(
                this, in.getFileDescriptor(), out.getFileDescriptor(), err.getFileDescriptor(),
                args);
    }

    /**
     * Binder interface API to indicate whether the API is currently available. This requires an
     * immediate asynchronous response.
     */
    @Override
    public boolean isAvailable() {
        long ident = Binder.clearCallingIdentity();
        try {
            return mWifiRttController != null && !mPowerManager.isDeviceIdleMode()
                    && mWifiPermissionsUtil.isLocationModeEnabled();
        } finally {
            Binder.restoreCallingIdentity(ident);
        }
    }

    @Override
    public Bundle getRttCharacteristics() {
        enforceAccessPermission();
        if (mCapabilities == null && mWifiRttController != null) {
            mCapabilities = mWifiRttController.getRttCapabilities();
        }
        return convertCapabilitiesToBundle(mCapabilities);
    }

    private Bundle convertCapabilitiesToBundle(WifiRttController.Capabilities capabilities) {
        Bundle characteristics = new Bundle();
        if (capabilities == null) {
            return characteristics;
        }
        characteristics.putBoolean(CHARACTERISTICS_KEY_BOOLEAN_ONE_SIDED_RTT,
                capabilities.oneSidedRttSupported);
        characteristics.putBoolean(CHARACTERISTICS_KEY_BOOLEAN_LCI, capabilities.lciSupported);
        characteristics.putBoolean(CHARACTERISTICS_KEY_BOOLEAN_LCR, capabilities.lcrSupported);
        characteristics.putBoolean(CHARACTERISTICS_KEY_BOOLEAN_STA_RESPONDER,
                capabilities.responderSupported);
        characteristics.putBoolean(CHARACTERISTICS_KEY_BOOLEAN_NTB_INITIATOR,
                capabilities.ntbInitiatorSupported);
        characteristics.putBoolean(CHARACTERISTICS_KEY_BOOLEAN_SECURE_HE_LTF_SUPPORTED,
                capabilities.secureHeLtfSupported);
        characteristics.putBoolean(CHARACTERISTICS_KEY_BOOLEAN_RANGING_FRAME_PROTECTION_SUPPORTED,
                capabilities.rangingFrameProtectionSupported);
        characteristics.putInt(CHARACTERISTICS_KEY_INT_MAX_SUPPORTED_SECURE_HE_LTF_PROTO_VERSION,
                capabilities.maxSupportedSecureHeLtfProtocolVersion);
        return characteristics;
    }

    /**
     * Override IEEE 802.11az parameters with overlay values.
     */
    private void override11azOverlays(RangingRequest rangingRequest) {
        int minNtbTime = mContext.getResources().getInteger(
                R.integer.config_wifi80211azMinTimeBetweenNtbMeasurementsMicros);
        int maxNtbTime = mContext.getResources().getInteger(
                R.integer.config_wifi80211azMaxTimeBetweenNtbMeasurementsMicros);
        if (minNtbTime > 0 || maxNtbTime > 0) {
            for (ResponderConfig peer : rangingRequest.mRttPeers) {
                if (peer.is80211azNtbSupported()) {
                    if (maxNtbTime > 0) peer.setNtbMaxTimeBetweenMeasurementsMicros(maxNtbTime);
                    if (minNtbTime > 0) peer.setNtbMinTimeBetweenMeasurementsMicros(minNtbTime);
                }
            }
        }
    }

    private @WifiConfiguration.SecurityType int getSecurityTypeFromPasnAkms(
            @PasnConfig.AkmType int akms) {
        @WifiConfiguration.SecurityType int securityType;
        // IEEE 802.11az supports PASN authentication with FT, PASN authentication with SAE and
        // PASN authentication with FILS shared key. On FT PSK and SAE needs pre-shared key or
        // password.
        if (akms == PasnConfig.AKM_PASN) {
            securityType = WifiConfiguration.SECURITY_TYPE_OPEN;
        } else if ((akms & PasnConfig.AKM_SAE) != 0) {
            securityType = WifiConfiguration.SECURITY_TYPE_SAE;
        } else if ((akms & PasnConfig.AKM_FT_PSK_SHA256) != 0
                || (akms & PasnConfig.AKM_FT_PSK_SHA384) != 0) {
            // Note: WifiConfiguration is created as PSK. The fast transition (FT) flag is added
            // before saving to wpa_supplicant. So check for SECURITY_TYPE_PSK.
            securityType = WifiConfiguration.SECURITY_TYPE_PSK;
        } else {
            securityType = WifiConfiguration.SECURITY_TYPE_EAP;
        }
        return securityType;
    }

    /**
     * Update the PASN password from WifiConfiguration.
     */
    private void updatePasswordIfRequired(@NonNull PasnConfig pasnConfig) {
        if (pasnConfig.getPassword() != null || pasnConfig.getWifiSsid() == null) {
            return;
        }
        int securityType = getSecurityTypeFromPasnAkms(pasnConfig.getBaseAkms());
        if (securityType == WifiConfiguration.SECURITY_TYPE_SAE
                || securityType == WifiConfiguration.SECURITY_TYPE_PSK) {
            // The SSID within PasnConfig is supplied by an 11az secure ranging application,
            // which doesn't guarantee UTF-8 encoding. Therefore, a UTF-8 conversion step is
            // necessary before the SSID can be reliably used by service code.
            WifiSsid translatedSsid = mWifiSsidTranslator.getTranslatedSsid(
                    pasnConfig.getWifiSsid());
            WifiConfiguration wifiConfiguration =
                    mWifiConfigManager.getConfiguredNetworkWithPassword(translatedSsid,
                            securityType);
            if (wifiConfiguration != null) {
                pasnConfig.setPassword(wifiConfiguration.preSharedKey);
            }
        }
    }

    /**
     * Update the secure ranging parameters if required.
     */
    private void updateSecureRangingParams(RangingRequest request) {
        for (ResponderConfig rttPeer : request.mRttPeers) {
            SecureRangingConfig secureRangingConfig = rttPeer.getSecureRangingConfig();
            if (secureRangingConfig != null) {
                updatePasswordIfRequired(secureRangingConfig.getPasnConfig());
            }
        }
    }

    /**
     * Binder interface API to start a ranging operation. Called on binder thread, operations needs
     * to be posted to handler thread.
     */
    @Override
    public void startRanging(IBinder binder, String callingPackage, String callingFeatureId,
            WorkSource workSource, RangingRequest request, IRttCallback callback, Bundle extras)
            throws RemoteException {
        if (VDBG) {
            Log.v(TAG, "startRanging: binder=" + binder + ", callingPackage=" + callingPackage
                    + ", workSource=" + workSource + ", request=" + request + ", callback="
                    + callback);
        }
        // verify arguments
        if (binder == null) {
            throw new IllegalArgumentException("Binder must not be null");
        }
        if (request == null || request.mRttPeers == null || request.mRttPeers.size() == 0) {
            throw new IllegalArgumentException("Request must not be null or empty");
        }
        for (ResponderConfig responder : request.mRttPeers) {
            if (responder == null) {
                throw new IllegalArgumentException("Request must not contain null Responders");
            }
        }
        if (callback == null) {
            throw new IllegalArgumentException("Callback must not be null");
        }
        request.enforceValidity(mAwareManager != null);

        if (!isAvailable()) {
            try {
                mRttMetrics.recordOverallStatus(
                        WifiMetricsProto.WifiRttLog.OVERALL_RTT_NOT_AVAILABLE);
                callback.onRangingFailure(RangingResultCallback.STATUS_CODE_FAIL_RTT_NOT_AVAILABLE);
            } catch (RemoteException e) {
                Log.e(TAG, "startRanging: disabled, callback failed -- " + e);
            }
            return;
        }

        final int uid = getMockableCallingUid();

        // permission checks
        enforceAccessPermission();
        enforceChangePermission();
        mWifiPermissionsUtil.checkPackage(uid, callingPackage);

        // check if only Aware APs are ranged.
        boolean onlyAwareApRanged = request.mRttPeers.stream().allMatch(
                config -> config.responderType == ResponderConfig.RESPONDER_AWARE);
        final Object attributionSource;
        if (onlyAwareApRanged && SdkLevel.isAtLeastT()) {
            // Special case: if only aware APs are ranged, then allow this request if the caller
            // has nearby permission.
            attributionSource = extras.getParcelable(
                    WifiManager.EXTRA_PARAM_KEY_ATTRIBUTION_SOURCE);
            if (!mWifiPermissionsUtil.checkNearbyDevicesPermission(
                    (AttributionSource) attributionSource, true,
                    "wifi aware ranging")) {
                // No nearby permission. Still check for location permission.
                mWifiPermissionsUtil.enforceFineLocationPermission(
                        callingPackage, callingFeatureId, uid);
            }
        } else {
            attributionSource = null;
            mWifiPermissionsUtil.enforceFineLocationPermission(
                    callingPackage, callingFeatureId, uid);
        }

        final WorkSource ws;
        if (workSource != null) {
            enforceLocationHardware();
            // We only care about UIDs in the incoming worksources and not their associated
            // tags. Clear names so that other operations involving wakesources become simpler.
            ws = workSource.withoutNames();
        } else {
            ws = null;
        }

        boolean isCalledFromPrivilegedContext =
                checkLocationHardware() && mShellCommand.getControlParam(
                        CONTROL_PARAM_OVERRIDE_ASSUME_NO_PRIVILEGE_NAME) == 0;

        // register for binder death
        IBinder.DeathRecipient dr = new IBinder.DeathRecipient() {
            @Override
            public void binderDied() {
                if (mVerboseLoggingEnabled) Log.v(TAG, "binderDied: uid=" + uid);
                binder.unlinkToDeath(this, 0);

                mRttServiceSynchronized.mHandler.post(() -> {
                    mRttServiceSynchronized.cleanUpClientRequests(uid, null);
                });
            }
        };

        try {
            binder.linkToDeath(dr, 0);
        } catch (RemoteException e) {
            Log.e(TAG, "Error on linkToDeath - " + e);
            return;
        }

        override11azOverlays(request);
        updateSecureRangingParams(request);

        mRttServiceSynchronized.mHandler.post(() -> {
            WorkSource sourceToUse = ws;
            if (ws == null || ws.isEmpty()) {
                sourceToUse = new WorkSource(uid);
            }
            mRttServiceSynchronized.queueRangingRequest(uid, sourceToUse, binder, dr,
                    callingPackage, callingFeatureId, request, callback,
                    isCalledFromPrivilegedContext, attributionSource);
        });
    }

    @Override
    public void cancelRanging(WorkSource workSource) throws RemoteException {
        if (VDBG) Log.v(TAG, "cancelRanging: workSource=" + workSource);
        enforceLocationHardware();
        // We only care about UIDs in the incoming worksources and not their associated
        // tags. Clear names so that other operations involving wakesources become simpler.
        final WorkSource ws = (workSource != null) ? workSource.withoutNames() : null;

        if (ws == null || ws.isEmpty()) {
            Log.e(TAG, "cancelRanging: invalid work-source -- " + ws);
            return;
        }

        mRttServiceSynchronized.mHandler.post(() -> {
            mRttServiceSynchronized.cleanUpClientRequests(0, ws);
        });
    }

    boolean initializeSupplicantWifiRttController() {
        String mSupplicantWifiRttControllerInterfaceName;
        if (VDBG) Log.v(TAG, "initializeSupplicantWifiRttController");
        if (mSupplicantWifiRttController == null) {
            return false;
        }
        mSupplicantWifiRttControllerInterfaceName = mSupplicantWifiRttController.getName();
        if (mSupplicantWifiRttControllerInterfaceName != null) {
            Log.d(TAG, "initializeSupplicantWifiRttController on interface: "
                    + mSupplicantWifiRttControllerInterfaceName);
        } else {
            Log.e(TAG, "Failed to get supplicant RTT controller interface name");
            return false;
        }
        // Cache the capabilities
        mProximityRangingCapabilities = mSupplicantWifiRttController
                .getProximityRangingCapabilities();
        if (mProximityRangingCapabilities == null) {
            Log.e(TAG, "Failed to get proximity ranging capabilities");
            return false;
        }
        // Set the device name
        mSupplicantWifiRttController.setProximityRangingDeviceName(mProximityRangingDeviceName);
        // Set the device randomized MAC address
        if (!updateProximityRangingMacAddressAndNotify()) {
            Log.e(TAG, "Failed to set randomized MAC address");
            return false;
        }
        Log.d(TAG, "initializeSupplicantWifiRttController success");
        return true;
    }

    /**
     * See {@link WifiRttManager#getProximityDetectionCharacteristics()}
     */
    @Override
    @Nullable
    public ProximityDetectionCharacteristics getProximityDetectionCharacteristics() {
        enforceNetworkStackPermission();
        if (VDBG) {
            Log.v(TAG, "getProximityDetectionCharacteristics:");
        }
        if (!isProximityRangingFeatureSupported()) {
            Log.i(TAG, "Proximity Ranging is not supported on this build");
            return null;
        }
        if (mSupplicantWifiRttController == null) {
            Log.e(TAG, "getProximityDetectionCharacteristics: Failed to get controller");
            return null;
        }
        if (mProximityRangingCapabilities == null) {
            mProximityRangingCapabilities =
                    mSupplicantWifiRttController.getProximityRangingCapabilities();
        }
        if (mProximityRangingCapabilities == null) {
            Log.e(TAG, "getProximityDetectionCharacteristics: Failed to get capabilities");
            return null;
        }
        Bundle bundle = convertProximityRangingCapabilitiesToBundle(mProximityRangingCapabilities);
        return new ProximityDetectionCharacteristics(bundle);
    }

    @VisibleForTesting
    Bundle convertProximityRangingCapabilitiesToBundle(
            SupplicantWifiRttController.ProximityRangingCapabilities capabilities) {
        Bundle characteristics = new Bundle();
        if (capabilities == null) {
            return characteristics;
        }
        characteristics.putInt(ProximityDetectionCharacteristics
                        .KEY_INT_MAX_NUM_CONTINUOUS_RANGING_SEEKER_SESSIONS,
                capabilities.maxNumContinuousRangingSeekerSessions);
        characteristics.putInt(ProximityDetectionCharacteristics
                        .KEY_INT_MAX_NUM_CONTINUOUS_RANGING_ADVERTISER_SESSIONS,
                capabilities.maxNumContinuousRangingAdvertiserSessions);
        characteristics.putBoolean(ProximityDetectionCharacteristics
                        .KEY_BOOLEAN_CONCURRENT_ISTA_RSTA_OPERATION_SUPPORTED,
                capabilities.isConcurrentIStaRStaOperationSupported);
        characteristics.putInt(ProximityDetectionCharacteristics
                        .KEY_INT_MIN_ALLOWED_RANGING_INTERVAL_80211MC_MS,
                capabilities.minAllowedRangingInterval80211mc);
        characteristics.putInt(ProximityDetectionCharacteristics
                        .KEY_INT_MIN_ALLOWED_RANGING_INTERVAL_NTB_MS,
                capabilities.minAllowedRangingIntervalNtbMs);
        characteristics.putString(ProximityDetectionCharacteristics
                .KEY_STRING_PROXIMITY_DETECTION_DEVICE_NAME, mProximityRangingDeviceName);
        characteristics.putBoolean(ProximityDetectionCharacteristics
                        .KEY_BOOLEAN_80211MC_BASED_RANGING_SUPPORTED,
                capabilities.is80211mcBasedRangingSupported);
        characteristics.putBoolean(ProximityDetectionCharacteristics
                        .KEY_BOOLEAN_NTB_SECURE_HE_LTF_RANGING_SUPPORTED,
                capabilities.isNtbSecureLtfRangingSupported);
        characteristics.putBoolean(ProximityDetectionCharacteristics
                        .KEY_BOOLEAN_NTB_NON_SECURE_HE_LTF_RANGING_SUPPORTED,
                capabilities.isNtbNonSecureLtfRangingSupported);
        characteristics.putBoolean(
                ProximityDetectionCharacteristics.KEY_BOOLEAN_80211MC_BASED_ISTA_ROLE,
                capabilities.is80211mcBasedIstaRoleSupported);
        characteristics.putBoolean(
                ProximityDetectionCharacteristics.KEY_BOOLEAN_80211MC_BASED_RSTA_ROLE,
                capabilities.is80211mcBasedRstaRoleSupported);
        characteristics.putBoolean(ProximityDetectionCharacteristics.KEY_BOOLEAN_NTB_ISTA_ROLE,
                capabilities.isNtbIstaRoleSupported);
        characteristics.putBoolean(ProximityDetectionCharacteristics.KEY_BOOLEAN_NTB_RSTA_ROLE,
                capabilities.isNtbRstaRoleSupported);
        characteristics.putInt(ProximityDetectionCharacteristics
                        .KEY_INT_MAX_SUPPORTED_PACKET_WIDTH_80211MC_BASED,
                capabilities.maxSupportedPacketBandwidth80211mcBased);
        characteristics.putInt(ProximityDetectionCharacteristics
                        .KEY_INT_MAX_SUPPORTED_PREAMBLE_80211MC_BASED,
                capabilities.maxSupportedPreamble80211mcBased);
        characteristics.putInt(ProximityDetectionCharacteristics
                        .KEY_INT_MAX_SUPPORTED_PACKET_WIDTH_NTB,
                capabilities.maxSupportedPacketBandwidthNtb);
        characteristics.putInt(ProximityDetectionCharacteristics
                        .KEY_INT_MAX_SUPPORTED_PREAMBLE_NTB,
                capabilities.maxSupportedPreambleNtb);
        characteristics.putBoolean(
                ProximityDetectionCharacteristics.KEY_BOOLEAN_UNAUTHENTICATED_PASN,
                capabilities.isUnauthenticatedPasnModeSupported);
        characteristics.putBoolean(
                ProximityDetectionCharacteristics.KEY_BOOLEAN_AUTHENTICATED_PASN,
                capabilities.isAuthenticatedPasnModeSupported);
        return characteristics;
    }

    /**
     * See {@link WifiRttManager#setProximityDetectionDeviceName(String)}
     */
    @Override
    public void setProximityDetectionDeviceName(@NonNull String deviceName) {
        enforceNetworkStackPermission();
        if (!isProximityRangingFeatureSupported()) {
            throw new UnsupportedOperationException(
                    "Proximity Ranging is not supported on this build");
        }
        if (VDBG) {
            Log.v(TAG, "setProximityDetectionDeviceName:" + deviceName);
        }
        if (TextUtils.isEmpty(deviceName)) {
            throw new IllegalArgumentException("deviceName must not be null or empty");
        }
        if (deviceName.length() > 32) {
            throw new IllegalArgumentException("deviceName must not be longer than 32 bytes");
        }
        mRttServiceSynchronized.mHandler.post(() -> {
            setProximityRangingDeviceName(deviceName);
            if (mSupplicantWifiRttController != null) {
                mSupplicantWifiRttController.setProximityRangingDeviceName(deviceName);
            }
        });
    }

    /**
     * See {@link WifiRttManager#getProximityDetectionRandomizedMacAddress()}
     */
    @Override
    public MacAddress getProximityDetectionRandomizedMacAddress() {
        enforceNetworkStackPermission();
        if (!isProximityRangingFeatureSupported()) {
            throw new UnsupportedOperationException(
                    "Proximity Ranging is not supported on this build");
        }
        if (VDBG) {
            Log.v(TAG, "getProximityDetectionRandomizedMacAddress:"
                    + mProximityRangingRandomizedMacAddress);
        }
        if (mSupplicantWifiRttController == null) {
            Log.e(TAG, "getProximityDetectionRandomizedMacAddress: Failed to get controller");
            return null;
        }
        return mProximityRangingRandomizedMacAddress;
    }

    /**
     * See {@link WifiRttManager#registerProximityDetectionMacAddressCallback(Executor,
     * WifiRttManager.ProximityDetectionMacAddressCallback)}
     */
    @Override
    public void registerProximityDetectionMacAddressCallback(
            @NonNull IProximityDetectionMacAddressCallback callback) {
        Objects.requireNonNull(callback, "Listener must not be null");
        enforceNetworkStackPermission();
        if (VDBG) {
            Log.v(TAG, "registerProximityDetectionMacAddressCallback: from pid="
                    + Binder.getCallingPid());
        }
        if (!isProximityRangingFeatureSupported()) {
            throw new UnsupportedOperationException(
                    "Proximity Ranging is not supported on this build");
        }
        final int uid = getMockableCallingUid();
        mRttServiceSynchronized.mHandler.post(() -> {
            if (!mProximityDetectionMacAddressCallbacks.register(callback, uid)) {
                Log.e(TAG, "registerProximityDetectionMacAddressCallback: register failed");
            } else if (mProximityRangingRandomizedMacAddress != null) {
                try {
                    callback.onResult(mProximityRangingRandomizedMacAddress);
                } catch (RemoteException e) {
                    Log.e(TAG, "onResult: remote exception -- " + e);
                }
            }
        });
    }

    /**
     * See {@link WifiRttManager#unregisterProximityDetectionMacAddressCallback(
     * WifiRttManager.ProximityDetectionMacAddressCallback)}
     */
    @Override
    public void unregisterProximityDetectionMacAddressCallback(
            @NonNull IProximityDetectionMacAddressCallback callback) {
        Objects.requireNonNull(callback, "Listener must not be null");
        enforceNetworkStackPermission();
        if (!isProximityRangingFeatureSupported()) {
            throw new UnsupportedOperationException(
                    "Proximity Ranging is not supported on this build");
        }
        if (VDBG) {
            Log.v(TAG, "unregisterProximityDetectionMacAddressCallback: from pid="
                    + Binder.getCallingPid());
        }
        mRttServiceSynchronized.mHandler.post(() -> {
            if (!mProximityDetectionMacAddressCallbacks.unregister(callback)) {
                Log.e(TAG, "unregisterProximityDetectionMacAddressCallback: unregister failed");
            }
        });
    }

    /**
     * See {@link WifiRttManager#startContinuousRanging(WorkSource,
     * RangingRequest, java.util.concurrent.Executor, ContinuousRangingResultCallback)}
     */
    @SuppressLint("NewApi")
    @Override
    public void startContinuousRanging(IBinder binder, String callingPackage,
            String callingFeatureId, WorkSource workSource, RangingRequest request,
            IContinuousRangingResultCallback callback) {
        Objects.requireNonNull(binder, "binder must not be null");
        Objects.requireNonNull(callback, "Callback must not be null");
        Objects.requireNonNull(callingPackage, "packageName must not be null");
        enforceNetworkStackPermission();
        if (VDBG) {
            Log.v(TAG, "startContinuousRanging: binder=" + binder + ", callingPackage="
                    + callingPackage + ", workSource=" + workSource + ", request=" + request
                    + ", callback=" + callback);
        }
        if (!Environment.isSdkNewerThanB()) {
            throw new UnsupportedOperationException("ContinuousRanging API is not supported");
        }
        if (!isProximityRangingFeatureSupported()) {
            throw new UnsupportedOperationException(
                    "Proximity Ranging is not supported on this build");
        }
        if (request == null || request.mRttPeers == null || request.mRttPeers.size() == 0) {
            throw new IllegalArgumentException("Request must not be null or empty");
        }
        for (ResponderConfig responder : request.mRttPeers) {
            if (responder == null) {
                throw new IllegalArgumentException("Request must not contain null Responders");
            }
        }

        if (!isAvailable()) {
            try {
                callback.onRangingFailure(
                        ContinuousRangingResultCallback.FAILURE_REASON_RTT_NOT_AVAILABLE);
            } catch (RemoteException e) {
                Log.e(TAG, "startContinuousRanging: disabled, callback failed -- " + e);
            }
            return;
        }

        final int uid = getMockableCallingUid();
        if (mSupplicantWifiRttController == null) {
            Log.e(TAG, "startContinuousRanging: SupplicantWifiRttController is null");
            try {
                callback.onRangingFailure(
                        ContinuousRangingResultCallback.FAILURE_REASON_RTT_NOT_AVAILABLE);
            } catch (RemoteException e) {
                Log.e(TAG, "startContinuousRanging: supplicant null, callback failed -- " + e);
            }
            return;
        }

        if (request.mRttPeers.size() > MAX_ALLOWED_PEERS_PER_CONTINUOUS_RANGING_REQUEST) {
            throw new IllegalArgumentException("Request must contain at most "
                    + MAX_ALLOWED_PEERS_PER_CONTINUOUS_RANGING_REQUEST + " Responder");
        }
        // Further validation done in RttServiceSynchronized
        final WorkSource ws = workSource != null ? workSource.withoutNames() : null;

        IBinder.DeathRecipient dr = new IBinder.DeathRecipient() {
            @Override
            public void binderDied() {
                if (mVerboseLoggingEnabled) Log.v(TAG, "binderDied: uid=" + uid);
                try {
                    binder.unlinkToDeath(this, 0);
                } catch (NoSuchElementException e) {
                    Log.e(TAG, "ContinuousRanging binderDied(): unlinkToDeath failed -- " + e);
                }

                mRttServiceSynchronized.mHandler.post(() -> {
                    mRttServiceSynchronized.cleanUpContinuousRangingSessions(uid, null,
                            ContinuousRangingResultCallback.TERMINATE_REASON_UNKNOWN);
                });
            }
        };


        try {
            binder.linkToDeath(dr, 0);
        } catch (RemoteException e) {
            Log.e(TAG, "Error on linkToDeath - " + e);
            try {
                callback.onRangingFailure(
                        ContinuousRangingResultCallback.FAILURE_REASON_GENERIC);
            } catch (RemoteException re) {
                Log.e(TAG, "startContinuousRanging: linkToDeath failed, callback failed -- " + re);
            }
            return;
        }

        mRttServiceSynchronized.mHandler.post(() -> {
            WorkSource sourceToUse = ws;
            if (ws == null || ws.isEmpty()) {
                sourceToUse = new WorkSource(uid);
            }
            mRttServiceSynchronized.queueContinuousRangingRequest(uid, sourceToUse, binder, dr,
                    callingPackage, callingFeatureId, request, callback);
        });
    }

    /**
     * See {@link WifiRttManager#stopContinuousRanging(WorkSource)}
     */
    @Override
    public void stopContinuousRanging(WorkSource workSource) {
        enforceNetworkStackPermission();
        if (VDBG) {
            Log.i(TAG, "stopContinuousRanging");
        }
        if (!isProximityRangingFeatureSupported()) {
            throw new UnsupportedOperationException(
                    "Proximity Ranging is not supported on this build");
        }
        final int uid = getMockableCallingUid();
        final WorkSource ws = workSource != null ? workSource.withoutNames() : null;

        mRttServiceSynchronized.mHandler.post(() -> {
            WorkSource sourceToUse = ws;
            if (ws == null || ws.isEmpty()) {
                sourceToUse = new WorkSource(uid);
            }
            mRttServiceSynchronized.cleanUpContinuousRangingSessions(uid, sourceToUse,
                    ContinuousRangingResultCallback.TERMINATE_REASON_USER_REQUEST);
        });
    }

    /**
     * Generates a randomized device name for Proximity Ranging
     *
     * @return A string representing the device name.
     */
    private String generateProximityRangingRandomizedDeviceName() {
        return DEFAULT_PR_DEVICE_NAME_PREFIX + StringUtil.generateRandomString(4);
    }

    /**
     * Sets the Proximity Ranging device name
     */
    private void setProximityRangingDeviceName(String deviceName) {
        if (VDBG) Log.v(TAG, "setProximityRangingDeviceName: deviceName=" + deviceName);
        mProximityRangingDeviceName = deviceName;
    }

    /**
     * Generates a randomized MAC address for Proximity Ranging.
     *
     * @return A randomized MAC Address.
     */
    private MacAddress generateProximityRangingRandomizedMacAddress() {
        if (VDBG) Log.v(TAG, "generateProximityRangingRandomizedMacAddress");
        java.util.Random random = new java.util.Random();
        byte[] randomMac = new byte[6];
        random.nextBytes(randomMac);
        /*
         * Set the locally administered bit (the second-least significant bit in the
         * first byte) to prevent collisions with globally unique addresses.
         * Also, ensure the multicast bit (the least significant bit) is not set.
         */
        randomMac[0] |= (byte) 0x02; // Set the locally administered bit
        randomMac[0] &= (byte) 0xFE; // Clear the multicast bit
        return MacAddress.fromBytes(randomMac);
    }

    /**
     * Sets the Proximity Ranging MAC Address.
     */
    private boolean updateProximityRangingMacAddressAndNotify() {
        if (VDBG) {
            Log.v(TAG, "updateProximityRangingMacAddressAndNotify");
        }
        if (mSupplicantWifiRttController == null) {
            Log.e(TAG, "updateProximityRangingMacAddressAndNotify failed:"
                    + " SupplicantWifiRttController is null");
            return false;
        }
        if (mProximityRangingCapabilities == null) {
            Log.e(TAG, "Failed to get proximity ranging capabilities");
            return false;
        }
        boolean useConnectedMac =
                !mProximityRangingCapabilities.isConnectedMacRandomizationSupported
                        && mConnectedIfaceMacAddress != null;
        if (useConnectedMac
                && mConnectedIfaceMacAddress.equals(mProximityRangingRandomizedMacAddress)) {
            return true;
        }
        MacAddress macAddress;
        if (useConnectedMac) {
            macAddress = mConnectedIfaceMacAddress;
        } else {
            macAddress = generateProximityRangingRandomizedMacAddress();
        }
        mSupplicantWifiRttController.setProximityRangingMacAddress(macAddress.toByteArray());
        byte[] retrievedMacAddressBytes = mSupplicantWifiRttController
                .getProximityRangingMacAddress();
        if (retrievedMacAddressBytes == null || !Arrays.equals(macAddress.toByteArray(),
                retrievedMacAddressBytes)) {
            Log.e(TAG, "Failed to set proximity ranging MAC address. Set: " + macAddress
                    + ", Retrieved: " + (retrievedMacAddressBytes == null ? "null"
                    : MacAddress.fromBytes(retrievedMacAddressBytes).toString()));
            return false;
        }
        mProximityRangingRandomizedMacAddress = macAddress;
        if (mProximityDetectionMacAddressCallbacks.getRegisteredCallbackCount() > 0) {
            // Inform the application of the change in the MAC address
            final int itemCount = mProximityDetectionMacAddressCallbacks.beginBroadcast();
            for (int i = 0; i < itemCount; i++) {
                try {
                    mProximityDetectionMacAddressCallbacks.getBroadcastItem(i).onResult(
                            macAddress);
                } catch (RemoteException e) {
                    Log.e(TAG, "onResult: remote exception -- " + e);
                }
            }
            mProximityDetectionMacAddressCallbacks.finishBroadcast();
        }
        return true;
    }

    private void enforceNetworkStackPermission() {
        mContext.enforceCallingOrSelfPermission(android.Manifest.permission.NETWORK_STACK, TAG);
    }

    private void enforceAccessPermission() {
        mContext.enforceCallingOrSelfPermission(android.Manifest.permission.ACCESS_WIFI_STATE, TAG);
    }

    private void enforceChangePermission() {
        mContext.enforceCallingOrSelfPermission(android.Manifest.permission.CHANGE_WIFI_STATE, TAG);
    }

    private void enforceLocationHardware() {
        mContext.enforceCallingOrSelfPermission(android.Manifest.permission.LOCATION_HARDWARE,
                TAG);
    }

    private boolean checkLocationHardware() {
        return mContext.checkCallingOrSelfPermission(android.Manifest.permission.LOCATION_HARDWARE)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void sendRttStateChangedBroadcast(boolean enabled) {
        if (VDBG) Log.v(TAG, "sendRttStateChangedBroadcast: enabled=" + enabled);
        final Intent intent = new Intent(WifiRttManager.ACTION_WIFI_RTT_STATE_CHANGED);
        intent.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY);
        mContext.sendBroadcastAsUser(intent, UserHandle.ALL);
    }

    @Override
    protected void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        if (mContext.checkCallingOrSelfPermission(
                android.Manifest.permission.DUMP) != PackageManager.PERMISSION_GRANTED) {
            pw.println("Permission Denial: can't dump RttService from pid="
                    + Binder.getCallingPid() + ", uid=" + Binder.getCallingUid());
            return;
        }
        pw.println("Wi-Fi RTT Service");
        mRttServiceSynchronized.dump(fd, pw, args);
        pw.println("  mWifiRttController: " + mWifiRttController);
        if (mWifiRttController != null) {
            mWifiRttController.dump(pw);
        }
    }

    /**
     * Calculates the timeout duration for a Wi-Fi RTT ranging request based on the types of
     * responders in the request.
     *
     * <p>The base timeout is {@link #HAL_RANGING_TIMEOUT_MS}. However, if the request includes
     * specific types of responders, the timeout may be increased to accommodate their
     * potentially longer response times or specific protocol requirements:
     *
     * <ul>
     *   <li>If any responder is of type {@link ResponderConfig#RESPONDER_AWARE}, the timeout
     *       is extended to at least {@link #HAL_AWARE_RANGING_TIMEOUT_MS}.
     *   <li>If the device supports IEEE 802.11az Non-Trigger-Based (NTB) initiation
     *       (as indicated by {@code mCapabilities.ntbInitiatorSupported}) and any responder
     *       also supports 802.11az NTB, the timeout is further adjusted to be at least
     *       the maximum of the {@code NtbMaxTimeBetweenMeasurementsMicros} configured for
     *       such responders (converted to milliseconds). This longer timeout is intended
     *       to accommodate ranging sessions to multiple NTB peers without requiring
     *       FTMR/FTM renegotiation for each one.
     * </ul>
     *
     * <p>The method returns the maximum timeout determined by these conditions, ensuring the
     * ranging operation waits sufficiently long for all requested peers.
     */
    @VisibleForTesting
    static long calculateRangeRequestTimeoutMs(List<ResponderConfig> responderConfigs,
            WifiRttController.Capabilities capabilities) {
        long timeout = HAL_RANGING_TIMEOUT_MS;
        if (responderConfigs == null) {
            return timeout;
        }
        for (ResponderConfig responderConfig : responderConfigs) {
            if (responderConfig.getResponderType() == ResponderConfig.RESPONDER_AWARE) {
                timeout = Math.max(timeout, HAL_AWARE_RANGING_TIMEOUT_MS);
            }
            // For IEEE 802.11az NTB ranging, the maximum time between measurements will be
            // configured in such a way to handle ranging to multiple peers without
            // triggering  FTMR/FTM renegotiation for each AP. This should be set to the
            // timeout for a ranging session, typically 15000ms (15 secs), which behaves
            // well for positioning.
            if (capabilities != null && capabilities.ntbInitiatorSupported
                    && responderConfig.is80211azNtbSupported()) {
                timeout = Math.max(timeout,
                        responderConfig.getNtbMaxTimeBetweenMeasurementsMicros() / 1000);
            }
        }
        return timeout;
    }

    /*
     * SYNCHRONIZED DOMAIN
     */

    /**
     * RTT service implementation - synchronized on a single thread. All commands should be posted
     * to the exposed handler.
     */
    private class RttServiceSynchronized {
        public Handler mHandler;

        private int mNextCommandId = 1000;
        private Map<Integer, RttRequesterInfo> mRttRequesterInfo = new HashMap<>();
        private List<RttRequestInfo> mRttRequestQueue = new LinkedList<>();
        private Map<Integer, ContinuousRangingRequestInfo> mContinuousRangingSessions =
                new HashMap<>();
        private WakeupMessage mRangingTimeoutMessage = null;
        private WakeupMessage mContinuousRangingTimeoutMessage = null;

        RttServiceSynchronized(Looper looper) {
            mHandler = new Handler(looper);
            mRangingTimeoutMessage = new WakeupMessage(mContext, mHandler,
                    HAL_RANGING_TIMEOUT_TAG, () -> {
                timeoutRangingRequest();
            });
            mContinuousRangingTimeoutMessage = new WakeupMessage(mContext, mHandler,
                    HAL_RANGING_TIMEOUT_TAG + " Continuous",
                    this::timeoutContinuousRangingRequest);
        }

        private void cancelRanging(RttRequestInfo rri) {
            ArrayList<MacAddress> macAddresses = new ArrayList<>();
            for (ResponderConfig peer : rri.request.mRttPeers) {
                macAddresses.add(peer.macAddress);
            }

            if (mWifiRttController != null) {
                mWifiRttController.rangeCancel(rri.cmdId, macAddresses);
            } else {
                Log.e(TAG, "Could not call cancelRanging, rttControllerHal is null");
            }
        }

        private void cleanUpOnDisable() {
            if (VDBG) Log.v(TAG, "RttServiceSynchronized.cleanUpOnDisable");
            for (RttRequestInfo rri : mRttRequestQueue) {
                try {
                    if (rri.dispatchedToNative) {
                        // may not be necessary in some cases (e.g. Wi-Fi disable may already clear
                        // up active RTT), but in other cases will be needed (doze disabling RTT
                        // but Wi-Fi still up). Doesn't hurt - worst case will fail.
                        cancelRanging(rri);
                    }
                    mRttMetrics.recordOverallStatus(
                            WifiMetricsProto.WifiRttLog.OVERALL_RTT_NOT_AVAILABLE);
                    rri.callback.onRangingFailure(
                            RangingResultCallback.STATUS_CODE_FAIL_RTT_NOT_AVAILABLE);
                } catch (RemoteException e) {
                    Log.e(TAG, "RttServiceSynchronized.startRanging: disabled, callback failed -- "
                            + e);
                }
                rri.binder.unlinkToDeath(rri.dr, 0);
            }
            mRttRequestQueue.clear();
            mRangingTimeoutMessage.cancel();
            // Clean up continuous ranging sessions
            if (isProximityRangingFeatureSupported()) {
                cleanUpContinuousRangingSessions(0, null,
                        ContinuousRangingResultCallback.TERMINATE_REASON_UNKNOWN);
                mContinuousRangingTimeoutMessage.cancel();
            }
        }

        /**
         * Remove entries related to the specified client and cancel any dispatched to HAL
         * requests. Expected to provide either the UID or the WorkSource (the other will be 0 or
         * null respectively).
         *
         * A workSource specification will be cleared from the requested workSource and the request
         * cancelled only if there are no remaining uids in the work-source.
         */
        private void cleanUpClientRequests(int uid, WorkSource workSource) {
            if (VDBG) {
                Log.v(TAG, "RttServiceSynchronized.cleanUpOnClientDeath: uid=" + uid
                        + ", workSource=" + workSource + ", mRttRequestQueue=" + mRttRequestQueue);
            }
            boolean dispatchedRequestAborted = false;
            ListIterator<RttRequestInfo> it = mRttRequestQueue.listIterator();
            while (it.hasNext()) {
                RttRequestInfo rri = it.next();

                boolean match = rri.uid == uid; // original UID will never be 0
                if (rri.workSource != null && workSource != null) {
                    rri.workSource.remove(workSource);
                    if (rri.workSource.isEmpty()) {
                        match = true;
                    }
                }

                if (match) {
                    if (!rri.dispatchedToNative) {
                        it.remove();
                        rri.binder.unlinkToDeath(rri.dr, 0);
                    } else {
                        dispatchedRequestAborted = true;
                        Log.d(TAG, "Client death - cancelling RTT operation in progress: cmdId="
                                + rri.cmdId);
                        mRangingTimeoutMessage.cancel();
                        cancelRanging(rri);
                    }
                }
            }

            if (VDBG) {
                Log.v(TAG, "RttServiceSynchronized.cleanUpOnClientDeath: uid=" + uid
                        + ", dispatchedRequestAborted=" + dispatchedRequestAborted
                        + ", after cleanup - mRttRequestQueue=" + mRttRequestQueue);
            }

            if (dispatchedRequestAborted) {
                executeNextRangingRequestIfPossible(true);
            }
        }

        private void cleanUpContinuousRangingSessions(int uid, WorkSource workSource,
                @ContinuousRangingResultCallback.RangingTerminateReason int reason) {
            if (VDBG) {
                Log.v(TAG, "cleanUpContinuousRangingSessions: uid=" + uid
                        + ", workSource=" + workSource);
            }
            if (mSupplicantWifiRttController == null) return;

            List<Integer> cmdIdsToCancel = new ArrayList<>();
            if (uid == 0 && workSource == null) { // A null worksource means cancel all
                cmdIdsToCancel.addAll(mContinuousRangingSessions.keySet());
            } else {
                for (ContinuousRangingRequestInfo session : mContinuousRangingSessions.values()) {
                    boolean match = (uid != 0 && session.mUid == uid);
                    if (!match && workSource != null) {
                        if (session.mWorkSource != null) {
                            session.mWorkSource.remove(workSource);
                            if (session.mWorkSource.isEmpty()) {
                                match = true;
                            }
                        }
                    }
                    if (match) {
                        cmdIdsToCancel.add(session.mCmdId);
                    }
                }
            }

            for (int cmdId : cmdIdsToCancel) {
                mSupplicantWifiRttController.rangeCancel(cmdId, new ArrayList<>());
                removeContinuousRangingSession(cmdId, reason);
            }
        }

        private void timeoutRangingRequest() {
            if (VDBG) {
                Log.v(TAG, "RttServiceSynchronized.timeoutRangingRequest mRttRequestQueue="
                        + mRttRequestQueue);
            }
            if (mRttRequestQueue.size() == 0) {
                Log.w(TAG, "RttServiceSynchronized.timeoutRangingRequest: but nothing in queue!?");
                return;
            }
            RttRequestInfo rri = mRttRequestQueue.get(0);
            if (!rri.dispatchedToNative) {
                Log.w(TAG, "RttServiceSynchronized.timeoutRangingRequest: command not dispatched "
                        + "to native!?");
                return;
            }
            cancelRanging(rri);
            try {
                mRttMetrics.recordOverallStatus(WifiMetricsProto.WifiRttLog.OVERALL_TIMEOUT);
                rri.callback.onRangingFailure(RangingResultCallback.STATUS_CODE_FAIL);
            } catch (RemoteException e) {
                Log.e(TAG, "RttServiceSynchronized.timeoutRangingRequest: callback failed: " + e);
            }
            executeNextRangingRequestIfPossible(true);
        }

        private void queueRangingRequest(int uid, WorkSource workSource, IBinder binder,
                IBinder.DeathRecipient dr, String callingPackage, String callingFeatureId,
                RangingRequest request, IRttCallback callback,
                boolean isCalledFromPrivilegedContext, Object attributionSource) {
            mRttMetrics.recordRequest(workSource, request);

            if (isProximityRangingFeatureSupported() && !mContinuousRangingSessions.isEmpty()) {
                Log.e(TAG, "queueRangingRequest:"
                        + " another continuous rtt session is active");
                binder.unlinkToDeath(dr, 0);
                try {
                    // TODO Define a new metrics for this
                    mRttMetrics.recordOverallStatus(WifiMetricsProto.WifiRttLog.OVERALL_THROTTLE);
                    callback.onRangingFailure(RangingResultCallback.STATUS_CODE_FAIL);
                } catch (RemoteException e) {
                    Log.e(TAG, "RttServiceSynchronized.queueRangingRequest: spamming, callback "
                            + "failed -- " + e);
                }
                return;
            }

            if (isRequestorSpamming(workSource)) {
                Log.w(TAG,
                        "Work source " + workSource + " is spamming, dropping request: " + request);
                binder.unlinkToDeath(dr, 0);
                try {
                    mRttMetrics.recordOverallStatus(WifiMetricsProto.WifiRttLog.OVERALL_THROTTLE);
                    callback.onRangingFailure(RangingResultCallback.STATUS_CODE_FAIL);
                } catch (RemoteException e) {
                    Log.e(TAG, "RttServiceSynchronized.queueRangingRequest: spamming, callback "
                            + "failed -- " + e);
                }
                return;
            }

            RttRequestInfo newRequest = new RttRequestInfo();
            newRequest.uid = uid;
            newRequest.workSource = workSource;
            newRequest.binder = binder;
            newRequest.dr = dr;
            newRequest.callingPackage = callingPackage;
            newRequest.callingFeatureId = callingFeatureId;
            newRequest.request = request;
            newRequest.callback = callback;
            newRequest.isCalledFromPrivilegedContext = isCalledFromPrivilegedContext;
            newRequest.attributionSource = attributionSource;
            mRttRequestQueue.add(newRequest);

            if (VDBG) {
                Log.v(TAG, "RttServiceSynchronized.queueRangingRequest: newRequest=" + newRequest);
            }

            executeNextRangingRequestIfPossible(false);
        }

        private boolean isRequestorSpamming(WorkSource ws) {
            if (VDBG) Log.v(TAG, "isRequestorSpamming: ws" + ws);

            SparseIntArray counts = new SparseIntArray();

            for (RttRequestInfo rri : mRttRequestQueue) {
                for (int i = 0; i < rri.workSource.size(); ++i) {
                    int uid = rri.workSource.getUid(i);
                    counts.put(uid, counts.get(uid) + 1);
                }

                final List<WorkChain> workChains = rri.workSource.getWorkChains();
                if (workChains != null) {
                    for (int i = 0; i < workChains.size(); ++i) {
                        final int uid = workChains.get(i).getAttributionUid();
                        counts.put(uid, counts.get(uid) + 1);
                    }
                }
            }

            for (int i = 0; i < ws.size(); ++i) {
                if (counts.get(ws.getUid(i)) < MAX_QUEUED_PER_UID) {
                    return false;
                }
            }

            final List<WorkChain> workChains = ws.getWorkChains();
            if (workChains != null) {
                for (int i = 0; i < workChains.size(); ++i) {
                    final int uid = workChains.get(i).getAttributionUid();
                    if (counts.get(uid) < MAX_QUEUED_PER_UID) {
                        return false;
                    }
                }
            }

            if (mVerboseLoggingEnabled) {
                Log.v(TAG, "isRequestorSpamming: ws=" + ws + ", someone is spamming: " + counts);
            }
            return true;
        }

        private void executeNextRangingRequestIfPossible(boolean popFirst) {
            if (VDBG) Log.v(TAG, "executeNextRangingRequestIfPossible: popFirst=" + popFirst);

            if (popFirst) {
                if (mRttRequestQueue.size() == 0) {
                    Log.w(TAG, "executeNextRangingRequestIfPossible: pop requested - but empty "
                            + "queue!? Ignoring pop.");
                } else {
                    RttRequestInfo topOfQueueRequest = mRttRequestQueue.remove(0);
                    topOfQueueRequest.binder.unlinkToDeath(topOfQueueRequest.dr, 0);
                }
            }

            if (mRttRequestQueue.size() == 0) {
                if (VDBG) Log.v(TAG, "executeNextRangingRequestIfPossible: no requests pending");
                return;
            }

            // if top of list is in progress then do nothing
            RttRequestInfo nextRequest = mRttRequestQueue.get(0);
            if (nextRequest.peerHandlesTranslated || nextRequest.dispatchedToNative) {
                if (VDBG) {
                    Log.v(TAG, "executeNextRangingRequestIfPossible: called but a command is "
                            + "executing. topOfQueue=" + nextRequest);
                }
                return;
            }

            startRanging(nextRequest);
        }

        private void startRanging(RttRequestInfo nextRequest) {
            if (VDBG) {
                Log.v(TAG, "RttServiceSynchronized.startRanging: nextRequest=" + nextRequest);
            }

            if (!isAvailable()) {
                Log.d(TAG, "RttServiceSynchronized.startRanging: disabled");
                try {
                    mRttMetrics.recordOverallStatus(
                            WifiMetricsProto.WifiRttLog.OVERALL_RTT_NOT_AVAILABLE);
                    nextRequest.callback.onRangingFailure(
                            RangingResultCallback.STATUS_CODE_FAIL_RTT_NOT_AVAILABLE);
                } catch (RemoteException e) {
                    Log.e(TAG, "RttServiceSynchronized.startRanging: disabled, callback failed -- "
                            + e);
                    executeNextRangingRequestIfPossible(true);
                    return;
                }
            }

            if (processAwarePeerHandles(nextRequest)) {
                if (VDBG) {
                    Log.v(TAG, "RttServiceSynchronized.startRanging: deferring due to PeerHandle "
                            + "Aware requests");
                }
                return;
            }

            if (!preExecThrottleCheck(nextRequest.workSource, nextRequest.callingPackage)) {
                Log.w(TAG, "RttServiceSynchronized.startRanging: execution throttled - nextRequest="
                        + nextRequest + ", mRttRequesterInfo=" + mRttRequesterInfo);
                try {
                    mRttMetrics.recordOverallStatus(WifiMetricsProto.WifiRttLog.OVERALL_THROTTLE);
                    nextRequest.callback.onRangingFailure(RangingResultCallback.STATUS_CODE_FAIL);
                } catch (RemoteException e) {
                    Log.e(TAG, "RttServiceSynchronized.startRanging: throttled, callback failed -- "
                            + e);
                }
                executeNextRangingRequestIfPossible(true);
                return;
            }

            nextRequest.cmdId = mNextCommandId++;
            mLastRequestTimestamp = mClock.getWallClockMillis();
            if (mWifiRttController != null
                    && mWifiRttController.rangeRequest(nextRequest.cmdId, nextRequest.request)) {
                mRangingTimeoutMessage.schedule(
                        mClock.getElapsedSinceBootMillis() + calculateRangeRequestTimeoutMs(
                                nextRequest.request.mRttPeers, mCapabilities));
            } else {
                Log.w(TAG, "RttServiceSynchronized.startRanging: native rangeRequest call failed");
                if (mWifiRttController == null) {
                    Log.e(TAG, "mWifiRttController is null");
                }
                try {
                    mRttMetrics.recordOverallStatus(
                            WifiMetricsProto.WifiRttLog.OVERALL_HAL_FAILURE);
                    nextRequest.callback.onRangingFailure(RangingResultCallback.STATUS_CODE_FAIL);
                } catch (RemoteException e) {
                    Log.e(TAG, "RttServiceSynchronized.startRanging: HAL request failed, callback "
                            + "failed -- " + e);
                }
                executeNextRangingRequestIfPossible(true);
            }
            nextRequest.dispatchedToNative = true;
        }

        /**
         * Perform pre-execution throttling checks:
         * - If all uids in ws are in background then check last execution and block if request is
         * more frequent than permitted
         * - If executing (i.e. permitted) then update execution time
         *
         * Returns true to permit execution, false to abort it.
         */
        private boolean preExecThrottleCheck(WorkSource ws, String callingPackage) {
            if (VDBG) Log.v(TAG, "preExecThrottleCheck: ws=" + ws);

            // are all UIDs running in the background or is at least 1 in the foreground?
            boolean allUidsInBackground = true;
            for (int i = 0; i < ws.size(); ++i) {
                int uidImportance = mActivityManager.getUidImportance(ws.getUid(i));
                if (VDBG) {
                    Log.v(TAG, "preExecThrottleCheck: uid=" + ws.getUid(i) + " -> importance="
                            + uidImportance);
                }
                if (uidImportance <= IMPORTANCE_FOREGROUND_SERVICE) {
                    allUidsInBackground = false;
                    break;
                }
            }

            final List<WorkChain> workChains = ws.getWorkChains();
            if (allUidsInBackground && workChains != null) {
                for (int i = 0; i < workChains.size(); ++i) {
                    final WorkChain wc = workChains.get(i);
                    int uidImportance = mActivityManager.getUidImportance(wc.getAttributionUid());
                    if (VDBG) {
                        Log.v(TAG, "preExecThrottleCheck: workChain=" + wc + " -> importance="
                                + uidImportance);
                    }

                    if (uidImportance <= IMPORTANCE_FOREGROUND_SERVICE) {
                        allUidsInBackground = false;
                        break;
                    }
                }
            }
            if (allUidsInBackground) {
                String[] exceptionList = mContext.getResources().getStringArray(
                        R.array.config_wifiBackgroundRttThrottleExceptionList);
                for (String packageName : exceptionList) {
                    if (TextUtils.equals(packageName, callingPackage)) {
                        allUidsInBackground = false;
                        break;
                    }
                }
            }

            // if all UIDs are in background then check timestamp since last execution and see if
            // any is permitted (infrequent enough)
            boolean allowExecution = false;
            int backgroundProcessExecGapMs = mContext.getResources().getInteger(
                    R.integer.config_wifiRttBackgroundExecGapMs);
            long mostRecentExecutionPermitted =
                    mClock.getElapsedSinceBootMillis() - backgroundProcessExecGapMs;
            if (allUidsInBackground) {
                for (int i = 0; i < ws.size(); ++i) {
                    RttRequesterInfo info = mRttRequesterInfo.get(ws.getUid(i));
                    if (info == null || info.lastRangingExecuted < mostRecentExecutionPermitted) {
                        allowExecution = true;
                        break;
                    }
                }

                if (workChains != null & !allowExecution) {
                    for (int i = 0; i < workChains.size(); ++i) {
                        final WorkChain wc = workChains.get(i);
                        RttRequesterInfo info = mRttRequesterInfo.get(wc.getAttributionUid());
                        if (info == null
                                || info.lastRangingExecuted < mostRecentExecutionPermitted) {
                            allowExecution = true;
                            break;
                        }
                    }
                }
            } else {
                allowExecution = true;
            }

            // update exec time
            if (allowExecution) {
                for (int i = 0; i < ws.size(); ++i) {
                    RttRequesterInfo info = mRttRequesterInfo.get(ws.getUid(i));
                    if (info == null) {
                        info = new RttRequesterInfo();
                        mRttRequesterInfo.put(ws.getUid(i), info);
                    }
                    info.lastRangingExecuted = mClock.getElapsedSinceBootMillis();
                }

                if (workChains != null) {
                    for (int i = 0; i < workChains.size(); ++i) {
                        final WorkChain wc = workChains.get(i);
                        RttRequesterInfo info = mRttRequesterInfo.get(wc.getAttributionUid());
                        if (info == null) {
                            info = new RttRequesterInfo();
                            mRttRequesterInfo.put(wc.getAttributionUid(), info);
                        }
                        info.lastRangingExecuted = mClock.getElapsedSinceBootMillis();
                    }
                }
            }

            return allowExecution;
        }

        /**
         * Check request for any PeerHandle Aware requests. If there are any: issue requests to
         * translate the peer ID to a MAC address and abort current execution of the range request.
         * The request will be re-attempted when response is received.
         *
         * In cases of failure: pop the current request and execute the next one. Failures:
         * - Not able to connect to remote service (unlikely)
         * - Request already processed: but we're missing information
         *
         * @return true if need to abort execution, false otherwise.
         */
        private boolean processAwarePeerHandles(RttRequestInfo request) {
            List<Integer> peerIdsNeedingTranslation = new ArrayList<>();
            for (ResponderConfig rttPeer : request.request.mRttPeers) {
                if (rttPeer.peerHandle != null && rttPeer.macAddress == null) {
                    peerIdsNeedingTranslation.add(rttPeer.peerHandle.peerId);
                }
            }

            if (peerIdsNeedingTranslation.size() == 0) {
                return false;
            }

            if (request.peerHandlesTranslated) {
                Log.w(TAG, "processAwarePeerHandles: request=" + request
                        + ": PeerHandles translated - but information still missing!?");
                try {
                    mRttMetrics.recordOverallStatus(
                            WifiMetricsProto.WifiRttLog.OVERALL_AWARE_TRANSLATION_FAILURE);
                    request.callback.onRangingFailure(RangingResultCallback.STATUS_CODE_FAIL);
                } catch (RemoteException e) {
                    Log.e(TAG, "processAwarePeerHandles: onRangingResults failure -- " + e);
                }
                executeNextRangingRequestIfPossible(true);
                return true; // an abort because we removed request and are executing next one
            }

            request.peerHandlesTranslated = true;
            int[] peerIdsArray = peerIdsNeedingTranslation.stream().mapToInt(i -> i).toArray();
            mAwareManager.requestMacAddresses(request.uid, peerIdsArray,
                    new IWifiAwareMacAddressProvider.Stub() {
                        @Override
                        public void macAddress(MacAddrMapping[] peerIdToMacList) {
                            // ASYNC DOMAIN
                            mHandler.post(() -> {
                                // BACK TO SYNC DOMAIN
                                processReceivedAwarePeerMacAddresses(request, peerIdToMacList);
                            });
                        }
                    });
            return true; // a deferral
        }

        private void processReceivedAwarePeerMacAddresses(RttRequestInfo request,
                MacAddrMapping[] peerIdToMacList) {
            if (VDBG) {
                Log.v(TAG, "processReceivedAwarePeerMacAddresses: request=" + request);
                Log.v(TAG, "processReceivedAwarePeerMacAddresses: peerIdToMacList begin");
                for (MacAddrMapping mapping : peerIdToMacList) {
                    Log.v(TAG, "    " + mapping.peerId + ": "
                            + MacAddress.fromBytes(mapping.macAddress));
                }
                Log.v(TAG, "processReceivedAwarePeerMacAddresses: peerIdToMacList end");
            }

            RangingRequest.Builder newRequestBuilder = new RangingRequest.Builder();
            for (ResponderConfig rttPeer : request.request.mRttPeers) {
                if (rttPeer.peerHandle != null && rttPeer.macAddress == null) {
                    byte[] mac = null;
                    for (MacAddrMapping mapping : peerIdToMacList) {
                        if (mapping.peerId == rttPeer.peerHandle.peerId) {
                            mac = mapping.macAddress;
                            break;
                        }
                    }
                    if (mac == null || mac.length != 6) {
                        Log.e(TAG, "processReceivedAwarePeerMacAddresses: received an invalid MAC "
                                + "address for peerId=" + rttPeer.peerHandle.peerId);
                        continue;
                    }
                    // To create a ResponderConfig object with both a MAC address and peer
                    // handler, we're bypassing the standard Builder pattern and directly using
                    // the constructor. This is because the SDK's Builder.build() method has a
                    // built-in restriction that prevents setting both properties simultaneously.
                    // To avoid triggering this exception, we're directly invoking the
                    // constructor to accommodate both values.
                    ResponderConfig.Builder responderConfigBuilder = new ResponderConfig.Builder()
                            .setMacAddress(MacAddress.fromBytes(mac))
                            .setPeerHandle(rttPeer.peerHandle)
                            .setResponderType(rttPeer.getResponderType())
                            .set80211mcSupported(rttPeer.is80211mcSupported())
                            .set80211azNtbSupported(rttPeer.is80211azNtbSupported())
                            .setChannelWidth(rttPeer.getChannelWidth())
                            .setFrequencyMhz(rttPeer.getFrequencyMhz())
                            .setCenterFreq1Mhz(rttPeer.getCenterFreq1Mhz())
                            .setCenterFreq0Mhz(rttPeer.getCenterFreq0Mhz())
                            .setPreamble(rttPeer.getPreamble());
                    newRequestBuilder.addResponder(new ResponderConfig(responderConfigBuilder));
                } else {
                    newRequestBuilder.addResponder(rttPeer);
                }
            }
            newRequestBuilder.setRttBurstSize(request.request.getRttBurstSize());
            request.request = newRequestBuilder.build();

            // run request again
            startRanging(request);
        }

        private void onRangingResults(int cmdId, List<RangingResult> results) {
            if (mRttRequestQueue.size() == 0) {
                Log.e(TAG, "RttServiceSynchronized.onRangingResults: no current RTT request "
                        + "pending!?");
                return;
            }
            mRangingTimeoutMessage.cancel();
            RttRequestInfo topOfQueueRequest = mRttRequestQueue.get(0);

            if (VDBG) {
                Log.v(TAG, "RttServiceSynchronized.onRangingResults: cmdId=" + cmdId
                        + ", topOfQueueRequest=" + topOfQueueRequest + ", results="
                        + Arrays.toString(results.toArray()));
            }

            if (topOfQueueRequest.cmdId != cmdId) {
                Log.e(TAG, "RttServiceSynchronized.onRangingResults: cmdId=" + cmdId
                        + ", does not match pending RTT request cmdId=" + topOfQueueRequest.cmdId);
                return;
            }

            boolean onlyAwareApRanged = topOfQueueRequest.request.mRttPeers.stream().allMatch(
                    config -> config.responderType == ResponderConfig.RESPONDER_AWARE);
            boolean permissionGranted = false;
            if (onlyAwareApRanged && SdkLevel.isAtLeastT()) {
                // Special case: if only aware APs are ranged, then allow this request if the caller
                // has nearby permission.
                permissionGranted = mWifiPermissionsUtil.checkNearbyDevicesPermission(
                        (AttributionSource) topOfQueueRequest.attributionSource, true,
                        "wifi aware on ranging result");
            }
            if (!permissionGranted) {
                permissionGranted =
                        mWifiPermissionsUtil.checkCallersLocationPermission(
                                topOfQueueRequest.callingPackage,
                                topOfQueueRequest.callingFeatureId,
                                topOfQueueRequest.uid, /* coarseForTargetSdkLessThanQ */ false,
                                null) && mWifiPermissionsUtil.isLocationModeEnabled();
            }
            try {
                if (permissionGranted) {
                    List<RangingResult> finalResults = postProcessResults(topOfQueueRequest.request,
                            results, topOfQueueRequest.isCalledFromPrivilegedContext);
                    mRttMetrics.recordOverallStatus(WifiMetricsProto.WifiRttLog.OVERALL_SUCCESS);
                    mRttMetrics.recordResult(topOfQueueRequest.request, results,
                            (int) (mClock.getWallClockMillis() - mLastRequestTimestamp));
                    if (VDBG) {
                        Log.v(TAG, "RttServiceSynchronized.onRangingResults: finalResults="
                                + finalResults);
                    }
                    topOfQueueRequest.callback.onRangingResults(finalResults);
                } else {
                    Log.w(TAG, "RttServiceSynchronized.onRangingResults: location permission "
                            + "revoked - not forwarding results");
                    mRttMetrics.recordOverallStatus(
                            WifiMetricsProto.WifiRttLog.OVERALL_LOCATION_PERMISSION_MISSING);
                    topOfQueueRequest.callback.onRangingFailure(
                            RangingResultCallback.STATUS_CODE_FAIL);
                }
            } catch (RemoteException e) {
                Log.e(TAG,
                        "RttServiceSynchronized.onRangingResults: callback exception -- " + e);
            }

            executeNextRangingRequestIfPossible(true);
        }

        @SuppressLint("NewApi")
        private int validateContinuousRangingRequest(
                RangingRequest request) {
            if (mSupplicantWifiRttController == null) {
                Log.e(TAG, "validateContinuousRangingRequest:"
                        + " SupplicantWifiRttController is null");
                return ContinuousRangingResultCallback.FAILURE_REASON_RTT_NOT_AVAILABLE;
            }
            if (!mContinuousRangingSessions.isEmpty()) {
                Log.e(TAG, "validateContinuousRangingRequest:"
                        + " another continuous rtt session is active");
                return ContinuousRangingResultCallback.FAILURE_REASON_RTT_BUSY;
            }
            if (!mRttRequestQueue.isEmpty()) {
                Log.e(TAG, "validateContinuousRangingRequest:"
                        + " another single shot rtt session is active");
                return ContinuousRangingResultCallback.FAILURE_REASON_RTT_BUSY;
            }
            // More validation, specific to continuous ranging
            ResponderConfig responder = request.mRttPeers.get(0);
            if (responder.responderType != RESPONDER_STA) {
                Log.e(TAG, "Responder type must be RESPONDER_STA for continuous ranging");
                return ContinuousRangingResultCallback.FAILURE_REASON_GENERIC;
            }
            if (responder.getMacAddress() == null) {
                // TODO: USD peer ID might be supported in future
                Log.e(TAG, "Responder must have a MAC address for continuous ranging");
                return ContinuousRangingResultCallback.FAILURE_REASON_GENERIC;
            }
            // For now, require both secure ranging and PD config
            if (responder.getSecureRangingConfig() == null
                    || responder.getProximityDetectionConfig() == null) {
                Log.e(TAG, "Responder must have secure ranging and proximity detection config");
                return ContinuousRangingResultCallback.FAILURE_REASON_GENERIC;
            }
            return 0;
        }

        private void queueContinuousRangingRequest(int uid, WorkSource workSource, IBinder binder,
                IBinder.DeathRecipient dr, String callingPackage, String callingFeatureId,
                RangingRequest request, IContinuousRangingResultCallback callback) {

            int failureReason = validateContinuousRangingRequest(request);
            mRttMetrics.recordContinuousRangingRequest(request, failureReason);
            if (failureReason != 0) {
                try {
                    callback.onRangingFailure(failureReason);
                } catch (RemoteException e) {
                    // ignored
                }
                try {
                    binder.unlinkToDeath(dr, 0);
                } catch (NoSuchElementException e) {
                    Log.e(TAG, "queueContinuousRangingRequest: unlinkToDeath failed -- " + e);
                }
                return;
            }

            ContinuousRangingRequestInfo newRequest = new ContinuousRangingRequestInfo();
            newRequest.mUid = uid;
            newRequest.mWorkSource = workSource;
            newRequest.mBinder = binder;
            newRequest.mDr = dr;
            newRequest.mCallingPackage = callingPackage;
            newRequest.mCallingFeatureId = callingFeatureId;
            newRequest.mRequest = request;
            newRequest.mCallback = callback;
            newRequest.mCmdId = mNextCommandId++;
            newRequest.mStartTimeMillis = mClock.getElapsedSinceBootMillis();

            if (mSupplicantWifiRttController.rangeRequest(newRequest.mCmdId, newRequest.mRequest)) {
                mRttMetrics.recordContinuousRangingStartStatus(0, newRequest.mRequest);
                mContinuousRangingSessions.put(newRequest.mCmdId, newRequest);
                // TODO: Send the same session timeout to HAL
                mContinuousRangingTimeoutMessage.schedule(
                        mClock.getElapsedSinceBootMillis() + HAL_PROXIMITY_RANGING_TIMEOUT_MS);
            } else {
                Log.e(TAG, "queueContinuousRangingRequest: rangeRequest call failed");
                try {
                    mRttMetrics.recordContinuousRangingStartStatus(
                            ContinuousRangingResultCallback.FAILURE_REASON_GENERIC,
                            newRequest.mRequest);
                    callback.onRangingFailure(
                            ContinuousRangingResultCallback.FAILURE_REASON_GENERIC);
                } catch (RemoteException e) {
                    // ignored
                }
                try {
                    binder.unlinkToDeath(dr, 0);
                } catch (NoSuchElementException e) {
                    Log.e(TAG, "queueContinuousRangingRequest: unlinkToDeath failed -- " + e);
                }
            }
        }

        private void timeoutContinuousRangingRequest() {
            if (mSupplicantWifiRttController == null) {
                Log.e(TAG, "timeoutContinuousRangingRequest: SupplicantWifiRttController is null.");
                return;
            }
            // For simplicity, timeout the oldest session. A better implementation might track
            // timeouts per session.
            if (mContinuousRangingSessions.isEmpty()) {
                Log.w(TAG, "timeoutContinuousRangingRequest: but no sessions active!?");
                return;
            }
            int cmdIdToTimeout = mContinuousRangingSessions.keySet().iterator().next();
            Log.i(TAG, "timeoutContinuousRangingRequest: cmdId=" + cmdIdToTimeout);
            mSupplicantWifiRttController.rangeCancel(cmdIdToTimeout, new ArrayList<>());
            removeContinuousRangingSession(cmdIdToTimeout,
                    ContinuousRangingResultCallback.TERMINATE_REASON_TIMEOUT);
        }

        private void onContinuousRangingResults(int cmdId, List<RangingResult> results) {
            ContinuousRangingRequestInfo session = mContinuousRangingSessions.get(cmdId);
            if (session == null) {
                Log.e(TAG, "onContinuousRangingResults for unknown cmdId: " + cmdId);
                return;
            }

            try {
                // TODO: post-process results if needed, similar to one-shot ranging
                mRttMetrics.recordContinuousRangingResults(cmdId, results);
                session.mCallback.onRangingResults(results);
            } catch (RemoteException e) {
                Log.e(TAG, "onContinuousRangingResults: callback exception -- " + e);
            }
        }

        @SuppressLint("NewApi")
        private void removeContinuousRangingSession(int cmdId, int reason) {
            mContinuousRangingTimeoutMessage.cancel();
            ContinuousRangingRequestInfo session = mContinuousRangingSessions.remove(cmdId);
            if (session == null) {
                if (mVerboseLoggingEnabled) {
                    Log.v(TAG, "removeContinuousRangingSession for unknown cmdId: " + cmdId);
                }
                return;
            }

            long durationMs = mClock.getElapsedSinceBootMillis() - session.mStartTimeMillis;
            int intervalMs = session.mRequest.mRttPeers.get(0).getProximityDetectionConfig()
                    .getContinuousRangingIntervalMillis();
            mRttMetrics.recordContinuousRangingSession(durationMs, intervalMs);
            mRttMetrics.recordContinuousRangingTerminationReason(reason);

            try {
                session.mCallback.onRangingStopped(reason);
            } catch (RemoteException e) {
                Log.e(TAG, "removeContinuousRangingSession: callback exception -- " + e);
            }
            try {
                session.mBinder.unlinkToDeath(session.mDr, 0);
            } catch (NoSuchElementException e) {
                Log.e(TAG, "removeContinuousRangingSession: unlinkToDeath failed -- " + e);
            }
            if (mContinuousRangingSessions.isEmpty()) {
                updateProximityRangingMacAddressAndNotify();
                setProximityRangingDeviceName(generateProximityRangingRandomizedDeviceName());
                if (mSupplicantWifiRttController != null) {
                    mSupplicantWifiRttController.setProximityRangingDeviceName(
                            mProximityRangingDeviceName);
                }
            }
        }

        /*
         * Post process the results:
         * - For requests without results: add FAILED results
         * - For Aware requests using PeerHandle: replace MAC address with PeerHandle
         * - Effectively: throws away results which don't match requests
         */
        private List<RangingResult> postProcessResults(RangingRequest request,
                List<RangingResult> results, boolean isCalledFromPrivilegedContext) {
            Map<MacAddress, RangingResult> resultEntries = new HashMap<>();
            for (RangingResult result : results) {
                resultEntries.put(result.getMacAddress(), result);
            }

            List<RangingResult> finalResults = new ArrayList<>(request.mRttPeers.size());

            for (ResponderConfig peer : request.mRttPeers) {
                RangingResult resultForRequest = resultEntries.get(peer.macAddress);
                if (resultForRequest == null || resultForRequest.getStatus()
                        != RangingResult.STATUS_SUCCESS) {
                    if (mVerboseLoggingEnabled) {
                        Log.v(TAG, "postProcessResults: missing=" + peer.macAddress);
                    }
                    RangingResult.Builder builder = new RangingResult.Builder();
                    if (resultForRequest != null && resultForRequest.getStatus()
                            == RangingResult.STATUS_BUSY_TRY_LATER) {
                        builder.setStatus(RangingResult.STATUS_BUSY_TRY_LATER);
                        builder.setRetryAfterDurationMillis(
                                resultForRequest.getRetryAfterDurationMillis());
                    } else {
                        builder.setStatus(RangingResult.STATUS_FAIL);
                    }
                    if (peer.peerHandle == null) {
                        builder.setMacAddress(peer.getMacAddress());
                    } else {
                        builder.setPeerHandle(peer.peerHandle);
                    }
                    finalResults.add(builder.build());
                } else {

                    // Clear LCI and LCR data if the location data should not be retransmitted,
                    // has a retention expiration time, contains no useful data, or did not parse,
                    // or the caller is not in a privileged context.
                    byte[] lci = resultForRequest.getLci();
                    byte[] lcr = resultForRequest.getLcr();
                    ResponderLocation responderLocation =
                            resultForRequest.getUnverifiedResponderLocation();
                    if (responderLocation == null || !isCalledFromPrivilegedContext) {
                        lci = null;
                        lcr = null;
                    }
                    RangingResult.Builder builder = new RangingResult.Builder();
                    builder.setStatus(RangingResult.STATUS_SUCCESS)
                            .setDistanceMm(resultForRequest.getDistanceMm())
                            .setDistanceStdDevMm(resultForRequest.getDistanceStdDevMm())
                            .setRssi(resultForRequest.getRssi())
                            .setNumAttemptedMeasurements(
                                    resultForRequest.getNumAttemptedMeasurements())
                            .setNumSuccessfulMeasurements(
                                    resultForRequest.getNumSuccessfulMeasurements())
                            .setLci(lci)
                            .setLcr(lcr)
                            .setUnverifiedResponderLocation(responderLocation)
                            .setRangingTimestampMillis(resultForRequest.getRangingTimestampMillis())
                            .set80211mcMeasurement(resultForRequest.is80211mcMeasurement())
                            .setMeasurementChannelFrequencyMHz(
                                    resultForRequest.getMeasurementChannelFrequencyMHz())
                            .setMeasurementBandwidth(resultForRequest.getMeasurementBandwidth())
                            .set80211azNtbMeasurement(resultForRequest.is80211azNtbMeasurement())
                            .setMinTimeBetweenNtbMeasurementsMicros(
                                    resultForRequest.getMinTimeBetweenNtbMeasurementsMicros())
                            .setMaxTimeBetweenNtbMeasurementsMicros(
                                    resultForRequest.getMaxTimeBetweenNtbMeasurementsMicros())
                            .set80211azInitiatorTxLtfRepetitionsCount(
                                    resultForRequest.get80211azInitiatorTxLtfRepetitionsCount())
                            .set80211azResponderTxLtfRepetitionsCount(
                                    resultForRequest.get80211azResponderTxLtfRepetitionsCount())
                            .set80211azNumberOfTxSpatialStreams(
                                    resultForRequest.get80211azNumberOfTxSpatialStreams())
                            .set80211azNumberOfRxSpatialStreams(
                                    resultForRequest.get80211azNumberOfRxSpatialStreams());
                    if (peer.peerHandle == null) {
                        builder.setMacAddress(peer.getMacAddress());
                    } else {
                        builder.setPeerHandle(peer.peerHandle);
                    }
                    if (SdkLevel.isAtLeastV() && resultForRequest.getVendorData() != null
                            && !resultForRequest.getVendorData().isEmpty()) {
                        builder.setVendorData(resultForRequest.getVendorData());
                    }
                    // set secure ranging fields
                    builder.setRangingFrameProtected(resultForRequest.isRangingFrameProtected())
                            .setRangingAuthenticated(resultForRequest.isRangingAuthenticated())
                            .setSecureHeLtfEnabled(resultForRequest.isSecureHeLtfEnabled())
                            .setSecureHeLtfProtocolVersion(
                                    resultForRequest.getSecureHeLtfProtocolVersion());
                    if (resultForRequest.getPasnComebackCookie() != null) {
                        builder.setPasnComebackCookie(resultForRequest.getPasnComebackCookie());
                        builder.setPasnComebackAfterMillis(
                                resultForRequest.getPasnComebackAfterMillis());
                    }
                    finalResults.add(builder.build());
                }
            }
            return finalResults;
        }

        // dump call (asynchronous most likely)
        protected void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
            pw.println("  mNextCommandId: " + mNextCommandId);
            pw.println("  mRttRequesterInfo: " + mRttRequesterInfo);
            pw.println("  mRttRequestQueue: " + mRttRequestQueue);
            pw.println("  mRangingTimeoutMessage: " + mRangingTimeoutMessage);
            pw.println("  mContinuousRangingSessions: " + mContinuousRangingSessions);
            pw.println("  mContinuousRangingTimeoutMessage: " + mContinuousRangingTimeoutMessage);
            pw.println("  mWifiRttController: " + mWifiRttController);
            pw.println("  mHalDeviceManager: " + mHalDeviceManager);
            mRttMetrics.dump(fd, pw, args);
        }
    }

    private static class RttRequestInfo {
        public int uid;
        public WorkSource workSource;
        public IBinder binder;
        public IBinder.DeathRecipient dr;
        public String callingPackage;
        public String callingFeatureId;
        public RangingRequest request;
        public IRttCallback callback;
        public boolean isCalledFromPrivilegedContext;
        // This should be of Class AttributionSource, not is declared as Object for mainline
        // backward compatibility.
        public Object attributionSource;

        public int cmdId = 0; // uninitialized cmdId value
        public boolean dispatchedToNative = false;
        public boolean peerHandlesTranslated = false;

        @Override
        public String toString() {
            return new StringBuilder("RttRequestInfo: uid=").append(uid).append(
                    ", workSource=").append(workSource).append(", binder=").append(binder).append(
                    ", dr=").append(dr).append(", callingPackage=").append(callingPackage).append(
                    ", callingFeatureId=").append(callingFeatureId).append(", request=").append(
                    request.toString()).append(", callback=").append(callback).append(
                    ", cmdId=").append(cmdId).append(", peerHandlesTranslated=").append(
                    peerHandlesTranslated).append(", isCalledFromPrivilegedContext=").append(
                    isCalledFromPrivilegedContext).toString();
        }
    }

    private static class RttRequesterInfo {
        public long lastRangingExecuted;

        @Override
        public String toString() {
            return new StringBuilder("RttRequesterInfo: lastRangingExecuted=").append(
                    lastRangingExecuted).toString();
        }
    }

    private static class ContinuousRangingRequestInfo {
        int mUid;
        WorkSource mWorkSource;
        IBinder mBinder;
        IBinder.DeathRecipient mDr;
        String mCallingPackage;
        String mCallingFeatureId;
        RangingRequest mRequest;
        IContinuousRangingResultCallback mCallback;
        long mStartTimeMillis;

        int mCmdId = 0; // uninitialized cmdId value

        @Override
        public String toString() {
            return "ContinuousRangingRequestInfo: uid=" + mUid + ", workSource=" + mWorkSource
                    + ", binder=" + mBinder + ", dr=" + mDr + ", callingPackage=" + mCallingPackage
                    + ", callingFeatureId=" + mCallingFeatureId + ", request="
                    + mRequest.toString() + ", callback=" + mCallback + ", startTimeMillis="
                    + mStartTimeMillis + ", cmdId=" + mCmdId;
        }
    }
}
