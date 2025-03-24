/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.server.wifi.mainline_supplicant;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.net.MacAddress;
import android.net.wifi.usd.Config;
import android.net.wifi.usd.PublishConfig;
import android.net.wifi.usd.SubscribeConfig;
import android.net.wifi.util.Environment;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.ServiceSpecificException;
import android.system.wifi.mainline_supplicant.IMainlineSupplicant;
import android.system.wifi.mainline_supplicant.IStaInterface;
import android.system.wifi.mainline_supplicant.IStaInterfaceCallback;
import android.system.wifi.mainline_supplicant.UsdMessageInfo;
import android.system.wifi.mainline_supplicant.UsdServiceProtoType;
import android.util.Log;

import com.android.internal.annotations.VisibleForTesting;
import com.android.server.wifi.SupplicantStaIfaceHal;
import com.android.server.wifi.WifiNative;
import com.android.server.wifi.WifiThreadRunner;
import com.android.server.wifi.usd.UsdNativeManager;
import com.android.wifi.flags.Flags;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Allows us to bring up, tear down, and make calls into the mainline supplicant process.
 * <p>
 * The mainline supplicant is a separate wpa_supplicant binary stored in the Wifi mainline module,
 * which provides specific functionalities such as USD.
 */
public class MainlineSupplicant {
    private static final String TAG = "MainlineSupplicant";
    private static final String MAINLINE_SUPPLICANT_SERVICE_NAME = "wifi_mainline_supplicant";
    private static final long WAIT_FOR_DEATH_TIMEOUT_MS = 50L;
    protected static final int DEFAULT_USD_FREQ_MHZ = 2437;

    private IMainlineSupplicant mIMainlineSupplicant;
    private final Object mLock = new Object();
    private final WifiThreadRunner mWifiThreadRunner;
    private SupplicantDeathRecipient mServiceDeathRecipient;
    private WifiNative.SupplicantDeathEventHandler mFrameworkDeathHandler;
    private CountDownLatch mWaitForDeathLatch;
    private final boolean mIsServiceAvailable;
    private Map<String, IStaInterface> mActiveStaIfaces = new HashMap<>();
    private Map<String, IStaInterfaceCallback> mStaIfaceCallbacks = new HashMap<>();
    private UsdNativeManager.UsdEventsCallback mUsdEventsCallback = null;

    public MainlineSupplicant(@NonNull WifiThreadRunner wifiThreadRunner) {
        mWifiThreadRunner = wifiThreadRunner;
        mServiceDeathRecipient = new SupplicantDeathRecipient();
        mIsServiceAvailable = canServiceBeAccessed();
    }

    @VisibleForTesting
    protected IMainlineSupplicant getNewServiceBinderMockable() {
        return IMainlineSupplicant.Stub.asInterface(
                ServiceManagerWrapper.waitForService(MAINLINE_SUPPLICANT_SERVICE_NAME));
    }

    private @Nullable IBinder getCurrentServiceBinder() {
        synchronized (mLock) {
            if (mIMainlineSupplicant == null) {
                return null;
            }
            return mIMainlineSupplicant.asBinder();
        }
    }

    private class SupplicantDeathRecipient implements IBinder.DeathRecipient {
        @Override
        public void binderDied() {
        }

        @Override
        public void binderDied(@NonNull IBinder who) {
            synchronized (mLock) {
                IBinder currentBinder = getCurrentServiceBinder();
                Log.i(TAG, "Death notification received. who=" + who
                        + ", currentBinder=" + currentBinder);
                if (currentBinder == null || currentBinder != who) {
                    Log.i(TAG, "Ignoring stale death notification");
                    return;
                }
                if (mWaitForDeathLatch != null) {
                    // Latch indicates that this event was triggered by stopService
                    mWaitForDeathLatch.countDown();
                }
                clearState();
                if (mFrameworkDeathHandler != null) {
                    mFrameworkDeathHandler.onDeath();
                }
                Log.i(TAG, "Service death was handled successfully");
            }
        }
    }

    /**
     * Check whether the mainline supplicant service can be accessed.
     */
    private boolean canServiceBeAccessed() {
        // Requires an Android B+ Selinux policy and a copy of the binary.
        return Environment.isSdkAtLeastB() && Flags.mainlineSupplicant()
                && Environment.isMainlineSupplicantBinaryInWifiApex();
    }

    /**
     * Returns true if the mainline supplicant service is available on this device.
     */
    public boolean isAvailable() {
        return mIsServiceAvailable;
    }

    /**
     * Reset the internal state for this instance.
     */
    private void clearState() {
        synchronized (mLock) {
            mIMainlineSupplicant = null;
            mActiveStaIfaces.clear();
            mStaIfaceCallbacks.clear();
        }
    }

    /**
     * Start the mainline supplicant process.
     *
     * @return true if the process was started, false otherwise.
     */
    public boolean startService() {
        synchronized (mLock) {
            if (!Environment.isSdkAtLeastB()) {
                Log.e(TAG, "Service is not available before Android B");
                return false;
            }
            if (mIMainlineSupplicant != null) {
                Log.i(TAG, "Service has already been started");
                return true;
            }

            mIMainlineSupplicant = getNewServiceBinderMockable();
            if (mIMainlineSupplicant == null) {
                Log.e(TAG, "Unable to retrieve binder from the ServiceManager");
                return false;
            }

            try {
                mWaitForDeathLatch = null;
                mIMainlineSupplicant.asBinder()
                        .linkToDeath(mServiceDeathRecipient, /* flags= */  0);
            } catch (RemoteException e) {
                handleRemoteException(e, "startService");
                return false;
            }

            Log.i(TAG, "Service was started successfully");
            return true;
        }
    }

    /**
     * Check whether this instance is active.
     */
    public boolean isActive() {
        synchronized (mLock) {
            return mIMainlineSupplicant != null;
        }
    }

    /**
     * Set up a STA interface with the specified iface name.
     *
     * @param ifaceName Name of the interface.
     * @return true on success, false otherwise.
     */
    public boolean addStaInterface(@NonNull String ifaceName) {
        synchronized (mLock) {
            final String methodName = "addStaInterface";
            if (!checkIsActiveAndLogError(methodName)) {
                return false;
            }
            if (ifaceName == null) {
                return false;
            }
            if (mActiveStaIfaces.containsKey(ifaceName)) {
                Log.i(TAG, "STA interface " + ifaceName + " already exists");
                return true;
            }

            try {
                IStaInterface staIface = mIMainlineSupplicant.addStaInterface(ifaceName);
                IStaInterfaceCallback callback = new MainlineSupplicantStaIfaceCallback(
                        this, ifaceName, mWifiThreadRunner);
                if (!registerStaIfaceCallback(staIface, callback)) {
                    Log.i(TAG, "Unable to register callback with interface " + ifaceName);
                    return false;
                }
                mActiveStaIfaces.put(ifaceName, staIface);
                // Keep callback in a store to avoid recycling by the garbage collector
                mStaIfaceCallbacks.put(ifaceName, callback);
                Log.i(TAG, "Added STA interface " + ifaceName);
                return true;
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodName);
            } catch (RemoteException e) {
                handleRemoteException(e, methodName);
            }
            return false;
        }
    }

    /**
     * Tear down the STA interface with the specified iface name.
     *
     * @param ifaceName Name of the interface.
     * @return true on success, false otherwise.
     */
    public boolean removeStaInterface(@NonNull String ifaceName) {
        synchronized (mLock) {
            final String methodName = "removeStaInterface";
            if (!checkIsActiveAndLogError(methodName)) {
                return false;
            }
            if (ifaceName == null) {
                return false;
            }
            if (!mActiveStaIfaces.containsKey(ifaceName)) {
                Log.i(TAG, "STA interface " + ifaceName + " does not exist");
                return false;
            }

            try {
                mIMainlineSupplicant.removeStaInterface(ifaceName);
                mActiveStaIfaces.remove(ifaceName);
                mStaIfaceCallbacks.remove(ifaceName);
                Log.i(TAG, "Removed STA interface " + ifaceName);
                return true;
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodName);
            } catch (RemoteException e) {
                handleRemoteException(e, methodName);
            }
            return false;
        }
    }

    /**
     * Register a callback with the provided STA interface.
     *
     * @return true if the registration was successful, false otherwise.
     */
    private boolean registerStaIfaceCallback(@NonNull IStaInterface iface,
            @NonNull IStaInterfaceCallback callback) {
        synchronized (mLock) {
            final String methodName = "registerStaIfaceCallback";
            if (iface == null || callback == null) {
                return false;
            }
            try {
                iface.registerCallback(callback);
                return true;
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodName);
            } catch (RemoteException e) {
                handleRemoteException(e, methodName);
            }
            return false;
        }
    }

    /**
     * Register a framework callback to receive USD events.
     */
    public void registerUsdEventsCallback(
            @NonNull UsdNativeManager.UsdEventsCallback usdEventsCallback) {
        mUsdEventsCallback = usdEventsCallback;
    }

    /**
     * Get the registered USD events callback. Method should only be used
     * by {@link MainlineSupplicantStaIfaceCallback}.
     */
    protected @Nullable UsdNativeManager.UsdEventsCallback getUsdEventsCallback() {
        return mUsdEventsCallback;
    }

    /**
     * Stop the mainline supplicant process.
     */
    public void stopService() {
        synchronized (mLock) {
            if (mIMainlineSupplicant == null) {
                Log.i(TAG, "Service has already been stopped");
                return;
            }
            try {
                Log.i(TAG, "Attempting to stop the service");
                mWaitForDeathLatch = new CountDownLatch(1);
                mIMainlineSupplicant.terminate();
            } catch (RemoteException e) {
                handleRemoteException(e, "stopService");
                return;
            }
        }

        // Wait for latch to confirm the service death
        try {
            if (mWaitForDeathLatch.await(WAIT_FOR_DEATH_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                Log.i(TAG, "Service death confirmation was received");
            } else {
                Log.e(TAG, "Timed out waiting for confirmation of service death");
            }
        } catch (InterruptedException e) {
            Log.e(TAG, "Failed to wait for service death");
        }
    }

    /**
     * Register a WifiNative death handler to receive service death notifications.
     */
    public void registerFrameworkDeathHandler(
            @NonNull WifiNative.SupplicantDeathEventHandler deathHandler) {
        if (deathHandler == null) {
            Log.e(TAG, "Attempted to register a null death handler");
            return;
        }
        synchronized (mLock) {
            if (mFrameworkDeathHandler != null) {
                Log.i(TAG, "Replacing the existing death handler");
            }
            mFrameworkDeathHandler = deathHandler;
        }
    }

    /**
     * Unregister an existing WifiNative death handler, for instance to avoid receiving a
     * death notification during a solicited terminate.
     */
    public void unregisterFrameworkDeathHandler() {
        synchronized (mLock) {
            if (mFrameworkDeathHandler == null) {
                Log.e(TAG, "Framework death handler has already been unregistered");
                return;
            }
            mFrameworkDeathHandler = null;
        }
    }

    private static byte frameworkToHalUsdTransmissionType(
            @Config.TransmissionType int transmissionType) {
        switch (transmissionType) {
            case Config.TRANSMISSION_TYPE_MULTICAST:
                return IStaInterface.UsdPublishTransmissionType.MULTICAST;
            case Config.TRANSMISSION_TYPE_UNICAST:
            default:
                return IStaInterface.UsdPublishTransmissionType.UNICAST;
        }
    }

    private static byte frameworkToHalUsdProtoType(
            @Config.ServiceProtoType int protoType) {
        switch (protoType) {
            case Config.SERVICE_PROTO_TYPE_GENERIC:
                return UsdServiceProtoType.GENERIC;
            case Config.SERVICE_PROTO_TYPE_CSA_MATTER:
                return UsdServiceProtoType.CSA_MATTER;
            default:
                return UsdServiceProtoType.UNKNOWN;
        }
    }

    @VisibleForTesting
    protected static IStaInterface.UsdPublishConfig frameworkToHalUsdPublishConfig(
            PublishConfig frameworkConfig) {
        IStaInterface.UsdPublishConfig aidlConfig = new IStaInterface.UsdPublishConfig();
        // USD publisher is always solicited and unsolicited
        aidlConfig.publishType = IStaInterface.UsdPublishType.SOLICITED_AND_UNSOLICITED;
        // FSD is always enabled for USD
        aidlConfig.isFsd = true;
        aidlConfig.transmissionType = frameworkToHalUsdTransmissionType(
                frameworkConfig.getSolicitedTransmissionType());
        aidlConfig.announcementPeriodMillis = frameworkConfig.getAnnouncementPeriodMillis();
        aidlConfig.baseConfig = new IStaInterface.UsdBaseConfig();
        aidlConfig.baseConfig.ttlSec = frameworkConfig.getTtlSeconds();
        int[] freqs = frameworkConfig.getOperatingFrequenciesMhz();
        aidlConfig.baseConfig.defaultFreqMhz = (freqs == null || freqs.length == 0)
                ? DEFAULT_USD_FREQ_MHZ : freqs[0];
        aidlConfig.baseConfig.freqsMhz = (freqs == null || freqs.length <= 1)
                ? new int[0] : Arrays.copyOfRange(freqs, 1, freqs.length);
        aidlConfig.baseConfig.serviceName = Arrays.toString(frameworkConfig.getServiceName());
        aidlConfig.baseConfig.serviceSpecificInfo =
                frameworkConfig.getServiceSpecificInfo() != null
                        ? frameworkConfig.getServiceSpecificInfo() : new byte[0];
        aidlConfig.baseConfig.rxMatchFilter = frameworkConfig.getRxMatchFilterTlv() != null
                ? frameworkConfig.getRxMatchFilterTlv() : new byte[0];
        aidlConfig.baseConfig.txMatchFilter = frameworkConfig.getTxMatchFilterTlv() != null
                ? frameworkConfig.getTxMatchFilterTlv() : new byte[0];
        aidlConfig.baseConfig.serviceProtoType = frameworkToHalUsdProtoType(
                frameworkConfig.getServiceProtoType());
        return aidlConfig;
    }

    /**
     * Start a USD publish operation.
     *
     * @param ifaceName Name of the interface
     * @param cmdId An id for this command
     * @param publishConfig Publish configuration
     * @return true if successful, false otherwise
     */
    public boolean startUsdPublish(@NonNull String ifaceName, int cmdId,
            @NonNull PublishConfig publishConfig) {
        synchronized (mLock) {
            final String methodName = "startUsdPublish";
            if (ifaceName == null || publishConfig == null) {
                return false;
            }
            IStaInterface iface = getStaIfaceOrLogError(ifaceName, methodName);
            if (iface == null) {
                return false;
            }
            try {
                iface.startUsdPublish(cmdId, frameworkToHalUsdPublishConfig(publishConfig));
                return true;
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodName);
            } catch (RemoteException e) {
                handleRemoteException(e, methodName);
            }
            return false;
        }
    }

    private static byte frameworkToHalUsdSubscribeType(
            @Config.SubscribeType int subscribeType) {
        switch (subscribeType) {
            case Config.SUBSCRIBE_TYPE_ACTIVE:
                return IStaInterface.UsdSubscribeType.ACTIVE_MODE;
            case Config.SUBSCRIBE_TYPE_PASSIVE:
            default:
                return IStaInterface.UsdSubscribeType.PASSIVE_MODE;
        }
    }

    @VisibleForTesting
    protected static IStaInterface.UsdSubscribeConfig frameworkToHalUsdSubscribeConfig(
            SubscribeConfig frameworkConfig) {
        IStaInterface.UsdSubscribeConfig aidlConfig = new IStaInterface.UsdSubscribeConfig();
        aidlConfig.subscribeType =
                frameworkToHalUsdSubscribeType(frameworkConfig.getSubscribeType());
        aidlConfig.queryPeriodMillis = frameworkConfig.getQueryPeriodMillis();
        aidlConfig.baseConfig = new IStaInterface.UsdBaseConfig();
        aidlConfig.baseConfig.ttlSec = frameworkConfig.getTtlSeconds();
        int[] freqs = frameworkConfig.getOperatingFrequenciesMhz();
        aidlConfig.baseConfig.defaultFreqMhz = (freqs == null || freqs.length == 0)
                ? DEFAULT_USD_FREQ_MHZ : freqs[0];
        aidlConfig.baseConfig.freqsMhz = (freqs == null || freqs.length <= 1)
                ? new int[0] : Arrays.copyOfRange(freqs, 1, freqs.length);
        aidlConfig.baseConfig.serviceName = Arrays.toString(frameworkConfig.getServiceName());
        aidlConfig.baseConfig.serviceSpecificInfo =
                frameworkConfig.getServiceSpecificInfo() != null
                        ? frameworkConfig.getServiceSpecificInfo() : new byte[0];
        aidlConfig.baseConfig.rxMatchFilter = frameworkConfig.getRxMatchFilterTlv() != null
                ? frameworkConfig.getRxMatchFilterTlv() : new byte[0];
        aidlConfig.baseConfig.txMatchFilter = frameworkConfig.getTxMatchFilterTlv() != null
                ? frameworkConfig.getTxMatchFilterTlv() : new byte[0];
        aidlConfig.baseConfig.serviceProtoType = frameworkToHalUsdProtoType(
                frameworkConfig.getServiceProtoType());
        return aidlConfig;
    }

    /**
     * Start a USD subscribe operation.
     *
     * @param ifaceName Name of the interface
     * @param cmdId An id for this command
     * @param subscribeConfig Subscribe configuration
     * @return true if successful, false otherwise
     */
    public boolean startUsdSubscribe(@NonNull String ifaceName, int cmdId,
            @NonNull SubscribeConfig subscribeConfig) {
        synchronized (mLock) {
            final String methodName = "startUsdSubscribe";
            if (ifaceName == null || subscribeConfig == null) {
                return false;
            }
            IStaInterface iface = getStaIfaceOrLogError(ifaceName, methodName);
            if (iface == null) {
                return false;
            }
            try {
                iface.startUsdSubscribe(cmdId, frameworkToHalUsdSubscribeConfig(subscribeConfig));
                return true;
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodName);
            } catch (RemoteException e) {
                handleRemoteException(e, methodName);
            }
            return false;
        }
    }

    /**
     * Get the USD capabilities for the interface.
     *
     * @param ifaceName Name of the interface
     * @return UsdCapabilities if available, otherwise null
     */
    public @Nullable SupplicantStaIfaceHal.UsdCapabilitiesInternal getUsdCapabilities(
            @NonNull String ifaceName) {
        synchronized (mLock) {
            final String methodName = "getUsdCapabilities";
            if (ifaceName == null) {
                return null;
            }
            IStaInterface iface = getStaIfaceOrLogError(ifaceName, methodName);
            if (iface == null) {
                return null;
            }
            try {
                IStaInterface.UsdCapabilities aidlCaps = iface.getUsdCapabilities();
                if (aidlCaps == null) {
                    Log.e(TAG, "Received null USD capabilities from the HAL");
                    return null;
                }
                return new SupplicantStaIfaceHal.UsdCapabilitiesInternal(
                        aidlCaps.isUsdPublisherSupported,
                        aidlCaps.isUsdSubscriberSupported,
                        aidlCaps.maxLocalSsiLengthBytes,
                        aidlCaps.maxServiceNameLengthBytes,
                        aidlCaps.maxMatchFilterLengthBytes,
                        aidlCaps.maxNumPublishSessions,
                        aidlCaps.maxNumSubscribeSessions);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodName);
            } catch (RemoteException e) {
                handleRemoteException(e, methodName);
            }
            return null;
        }
    }

    /**
     * Update an ongoing USD publish operation.
     *
     * @param ifaceName Name of the interface
     * @param publishId Publish id for this session
     * @param ssi Service specific info
     */
    public void updateUsdPublish(@NonNull String ifaceName, int publishId,
            @NonNull byte[] ssi) {
        synchronized (mLock) {
            final String methodName = "updateUsdPublish";
            if (ifaceName == null || ssi == null) {
                return;
            }
            IStaInterface iface = getStaIfaceOrLogError(ifaceName, methodName);
            if (iface == null) {
                return;
            }
            try {
                iface.updateUsdPublish(publishId, ssi);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodName);
            } catch (RemoteException e) {
                handleRemoteException(e, methodName);
            }
        }
    }

    /**
     * Cancel an ongoing USD publish session.
     *
     * @param ifaceName Name of the interface
     * @param publishId Publish id for the session
     */
    public void cancelUsdPublish(@NonNull String ifaceName, int publishId) {
        synchronized (mLock) {
            final String methodName = "cancelUsdPublish";
            if (ifaceName == null) {
                return;
            }
            IStaInterface iface = getStaIfaceOrLogError(ifaceName, methodName);
            if (iface == null) {
                return;
            }
            try {
                iface.cancelUsdPublish(publishId);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodName);
            } catch (RemoteException e) {
                handleRemoteException(e, methodName);
            }
        }
    }

    /**
     * Cancel an ongoing USD subscribe session.
     *
     * @param ifaceName Name of the interface
     * @param subscribeId Subscribe id for the session
     */
    public void cancelUsdSubscribe(@NonNull String ifaceName, int subscribeId) {
        synchronized (mLock) {
            final String methodName = "cancelUsdSubscribe";
            if (ifaceName == null) {
                return;
            }
            IStaInterface iface = getStaIfaceOrLogError(ifaceName, methodName);
            if (iface == null) {
                return;
            }
            try {
                iface.cancelUsdSubscribe(subscribeId);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodName);
            } catch (RemoteException e) {
                handleRemoteException(e, methodName);
            }
        }
    }

    private static UsdMessageInfo createUsdMessageInfo(int ownId, int peerId,
            MacAddress peerMacAddress, byte[] message) {
        UsdMessageInfo messageInfo = new UsdMessageInfo();
        messageInfo.ownId = ownId;
        messageInfo.peerId = peerId;
        messageInfo.message = message;
        messageInfo.peerMacAddress = peerMacAddress.toByteArray();
        return messageInfo;
    }

    /**
     * Send a message to an ongoing USD publish or subscribe session.
     *
     * @param ifaceName Name of the interface
     * @param ownId Id for the session
     * @param peerId Id for the peer session
     * @param peerMacAddress Mac address of the peer session
     * @param message Data to send
     * @return true if successful, false otherwise
     */
    public boolean sendUsdMessage(@NonNull String ifaceName, int ownId, int peerId,
            @NonNull MacAddress peerMacAddress, @NonNull byte[] message) {
        synchronized (mLock) {
            final String methodName = "sendUsdMessage";
            if (ifaceName == null || peerMacAddress == null || message == null) {
                return false;
            }
            IStaInterface iface = getStaIfaceOrLogError(ifaceName, methodName);
            if (iface == null) {
                return false;
            }
            try {
                iface.sendUsdMessage(
                        createUsdMessageInfo(ownId, peerId, peerMacAddress, message));
                return true;
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodName);
            } catch (RemoteException e) {
                handleRemoteException(e, methodName);
            }
            return false;
        }
    }

    private void handleServiceSpecificException(ServiceSpecificException e, String methodName) {
        Log.e(TAG, methodName + " encountered ServiceSpecificException " + e);
    }

    private boolean checkIsActiveAndLogError(String methodName) {
        if (!isActive()) {
            Log.e(TAG, "Unable to call " + methodName + " since the instance is not active");
            return false;
        }
        return true;
    }

    private @Nullable IStaInterface getStaIfaceOrLogError(String ifaceName, String methodName) {
        synchronized (mLock) {
            if (!mActiveStaIfaces.containsKey(ifaceName)) {
                Log.e(TAG, "Unable to call " + methodName + " since iface "
                        + ifaceName + " does not exist");
                return null;
            }
            return mActiveStaIfaces.get(ifaceName);
        }
    }

    private void handleRemoteException(RemoteException e, String methodName) {
        synchronized (mLock) {
            Log.e(TAG, methodName + " encountered RemoteException " + e);
            clearState();
        }
    }
}
