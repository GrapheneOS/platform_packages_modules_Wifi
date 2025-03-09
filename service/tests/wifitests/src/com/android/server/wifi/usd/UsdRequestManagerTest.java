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

package com.android.server.wifi.usd;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import android.app.AlarmManager;
import android.net.wifi.IBooleanListener;
import android.net.wifi.usd.Characteristics;
import android.net.wifi.usd.Config;
import android.net.wifi.usd.IPublishSessionCallback;
import android.net.wifi.usd.ISubscribeSessionCallback;
import android.net.wifi.usd.PublishConfig;
import android.net.wifi.usd.SessionCallback;
import android.net.wifi.usd.SubscribeConfig;
import android.os.IBinder;
import android.os.RemoteException;

import androidx.test.filters.SmallTest;

import com.android.server.wifi.ActiveModeWarden;
import com.android.server.wifi.ClientModeManager;
import com.android.server.wifi.Clock;
import com.android.server.wifi.SupplicantStaIfaceHal;
import com.android.server.wifi.SupplicantStaIfaceHal.UsdCapabilitiesInternal;
import com.android.server.wifi.WifiBaseTest;
import com.android.server.wifi.WifiNative;
import com.android.server.wifi.WifiThreadRunner;

import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;
import org.mockito.Mock;

import java.util.ArrayList;
import java.util.List;

/**
 * Unit test for {@link UsdRequestManager}.
 */
@SmallTest
public class UsdRequestManagerTest extends WifiBaseTest {
    private static final int TEST_PUBLISH_ID = 1;
    private static final int TEST_SUBSCRIBE_ID = 2;
    private UsdRequestManager mUsdRequestManager;
    @Mock
    private UsdNativeManager mUsdNativeManager;
    @Mock
    private WifiThreadRunner mWifiThreadRunner;
    private static final String USD_INTERFACE_NAME = "wlan0";
    private static final int USD_REQUEST_COMMAND_ID = 100;
    private static final String USD_TEST_SERVICE_NAME = "UsdTest";
    private static final int USD_TEST_PERIOD_MILLIS = 200;
    private static final int USD_TTL_SEC = 3000;
    @Mock
    private Clock mClock;
    @Mock
    ISubscribeSessionCallback mSubscribeSessionCallback;
    @Mock
    IPublishSessionCallback mPublishSessionCallback;

    private SupplicantStaIfaceHal.UsdCapabilitiesInternal mUsdCapabilities;
    @Mock
    private WifiNative mWifiNative;
    @Mock
    private IBinder mAppBinder;
    private InOrder mInOrderAppBinder;
    @Mock
    private AlarmManager mAlarmManager;
    private byte[] mSsi = new byte[]{1, 2, 3};
    private int[] mFreqs = new int[]{2437};
    private List<byte[]> mFilter;
    @Mock
    ActiveModeWarden mActiveModeWarden;
    @Mock
    ClientModeManager mClientModeManager;
    @Mock
    IBooleanListener mPublisherListener;
    @Mock
    IBooleanListener mSubscriberListener;
    UsdRequestManager.UsdNativeEventsCallback mUsdNativeEventsCallback;

    @Before
    public void setUp() throws Exception {
        initMocks(this);
        mUsdCapabilities = getMockUsdCapabilities();
        //mUsdNativeManager = new UsdNativeManager(mWifiNative);
        when(mWifiNative.getUsdCapabilities()).thenReturn(mUsdCapabilities);
        when(mActiveModeWarden.getPrimaryClientModeManager()).thenReturn(mClientModeManager);
        when(mClientModeManager.getInterfaceName()).thenReturn(USD_INTERFACE_NAME);
        // Capture argument for mUsdNativeManager.registerUsdEventsCallback
        doAnswer(invocation -> {
            mUsdNativeEventsCallback = invocation.getArgument(0);
            return null;
        }).when(mUsdNativeManager).registerUsdEventsCallback(any());
        mUsdRequestManager = new UsdRequestManager(mUsdNativeManager, mWifiThreadRunner,
                mActiveModeWarden, mClock, mAlarmManager);
        UsdCapabilitiesInternal mockUsdCapabilities = getMockUsdCapabilities();
        mFilter = new ArrayList<>();
        mFilter.add(new byte[]{10, 11});
        mFilter.add(new byte[]{12, 13, 14});
        mInOrderAppBinder = inOrder(mAppBinder);
        when(mUsdNativeManager.getUsdCapabilities()).thenReturn(mockUsdCapabilities);
        // Get USD capabilities to update the cache
        mUsdRequestManager.getCharacteristics();
    }

    private UsdCapabilitiesInternal getMockUsdCapabilities() {
        return new UsdCapabilitiesInternal(true, true, 1024, 255,
                255, 1, 1);
    }

    /**
     * Test {@link UsdRequestManager#getCharacteristics()}.
     */
    @Test
    public void testUsdGetCharacteristics() {
        Characteristics characteristics = mUsdRequestManager.getCharacteristics();
        assertEquals(mUsdCapabilities.maxNumSubscribeSessions,
                characteristics.getMaxNumberOfSubscribeSessions());
        assertEquals(mUsdCapabilities.maxNumPublishSessions,
                characteristics.getMaxNumberOfPublishSessions());
        assertEquals(mUsdCapabilities.maxServiceNameLengthBytes,
                characteristics.getMaxServiceNameLength());
        assertEquals(mUsdCapabilities.maxMatchFilterLengthBytes,
                characteristics.getMaxMatchFilterLength());
        assertEquals(mUsdCapabilities.maxLocalSsiLengthBytes,
                characteristics.getMaxServiceSpecificInfoLength());
    }

    /**
     * Test USD subscribe.
     */
    @Test
    public void testUsdSubscribe() throws RemoteException {
        SubscribeConfig subscribeConfig = new SubscribeConfig.Builder(USD_TEST_SERVICE_NAME)
                .setQueryPeriodMillis(USD_TEST_PERIOD_MILLIS)
                .setOperatingFrequenciesMhz(mFreqs)
                .setRxMatchFilter(mFilter)
                .setTxMatchFilter(mFilter)
                .setSubscribeType(SubscribeConfig.SUBSCRIBE_TYPE_ACTIVE)
                .setServiceSpecificInfo(mSsi)
                .setServiceProtoType(Config.SERVICE_PROTO_TYPE_CSA_MATTER)
                .setQueryPeriodMillis(USD_TEST_PERIOD_MILLIS)
                .setTtlSeconds(USD_TTL_SEC).build();
        when(mSubscribeSessionCallback.asBinder()).thenReturn(mAppBinder);
        when(mUsdNativeManager.subscribe(USD_INTERFACE_NAME, USD_REQUEST_COMMAND_ID,
                subscribeConfig)).thenReturn(true);
        mUsdRequestManager.subscribe(subscribeConfig, mSubscribeSessionCallback);
        verify(mSubscribeSessionCallback, times(0)).onSubscribeFailed(anyInt());
        mInOrderAppBinder.verify(mAppBinder).linkToDeath(any(IBinder.DeathRecipient.class),
                anyInt());
        mUsdNativeEventsCallback.onUsdSubscribeStarted(USD_REQUEST_COMMAND_ID, TEST_SUBSCRIBE_ID);
        verify(mSubscribeSessionCallback, times(1)).onSubscribeStarted(TEST_SUBSCRIBE_ID);
    }

    /**
     * Test USD publish.
     */
    @Test
    public void testUsdPublish() throws RemoteException {
        PublishConfig publishConfig = new PublishConfig.Builder(USD_TEST_SERVICE_NAME)
                .setAnnouncementPeriodMillis(USD_TEST_PERIOD_MILLIS)
                .setEventsEnabled(true)
                .setOperatingFrequenciesMhz(mFreqs)
                .setRxMatchFilter(mFilter)
                .setTxMatchFilter(mFilter)
                .setServiceProtoType(Config.SERVICE_PROTO_TYPE_CSA_MATTER)
                .setServiceSpecificInfo(mSsi)
                .setSolicitedTransmissionType(Config.TRANSMISSION_TYPE_UNICAST)
                .setTtlSeconds(USD_TTL_SEC)
                .build();
        when(mPublishSessionCallback.asBinder()).thenReturn(mAppBinder);
        when(mUsdNativeManager.publish(eq(USD_INTERFACE_NAME), eq(USD_REQUEST_COMMAND_ID),
                eq(publishConfig))).thenReturn(true);
        mUsdRequestManager.publish(publishConfig, mPublishSessionCallback);
        verify(mPublishSessionCallback, times(0)).onPublishFailed(anyInt());
        mUsdNativeEventsCallback.onUsdPublishStarted(USD_REQUEST_COMMAND_ID, TEST_PUBLISH_ID);
        verify(mPublishSessionCallback).onPublishStarted(TEST_PUBLISH_ID);
    }

    /**
     * Test USD publish when already a subscriber running.
     */
    @Test
    public void testUsdPublishFailureWhenSubscriberRunning() throws RemoteException {
        SubscribeConfig subscribeConfig = new SubscribeConfig.Builder(USD_TEST_SERVICE_NAME)
                .setQueryPeriodMillis(USD_TEST_PERIOD_MILLIS)
                .setOperatingFrequenciesMhz(mFreqs)
                .setRxMatchFilter(mFilter)
                .setTxMatchFilter(mFilter)
                .setSubscribeType(SubscribeConfig.SUBSCRIBE_TYPE_ACTIVE)
                .setServiceSpecificInfo(mSsi)
                .setServiceProtoType(Config.SERVICE_PROTO_TYPE_CSA_MATTER)
                .setQueryPeriodMillis(USD_TEST_PERIOD_MILLIS)
                .setTtlSeconds(USD_TTL_SEC).build();
        PublishConfig publishConfig = new PublishConfig.Builder(USD_TEST_SERVICE_NAME)
                .setAnnouncementPeriodMillis(USD_TEST_PERIOD_MILLIS)
                .setEventsEnabled(true)
                .setOperatingFrequenciesMhz(mFreqs)
                .setRxMatchFilter(mFilter)
                .setTxMatchFilter(mFilter)
                .setServiceProtoType(Config.SERVICE_PROTO_TYPE_CSA_MATTER)
                .setServiceSpecificInfo(mSsi)
                .setSolicitedTransmissionType(Config.TRANSMISSION_TYPE_UNICAST)
                .setTtlSeconds(USD_TTL_SEC)
                .build();

        when(mSubscribeSessionCallback.asBinder()).thenReturn(mAppBinder);
        when(mUsdNativeManager.subscribe(USD_INTERFACE_NAME, USD_REQUEST_COMMAND_ID,
                subscribeConfig)).thenReturn(true);
        mUsdRequestManager.subscribe(subscribeConfig, mSubscribeSessionCallback);
        mUsdRequestManager.publish(publishConfig, mPublishSessionCallback);
        verify(mPublishSessionCallback).onPublishFailed(SessionCallback.FAILURE_NOT_AVAILABLE);
    }

    /**
     * Test USD subscribe when already a publisher running.
     */
    @Test
    public void testUsdSubscribeFailureWhenPublisherRunning() throws RemoteException {
        SubscribeConfig subscribeConfig = new SubscribeConfig.Builder(USD_TEST_SERVICE_NAME)
                .setQueryPeriodMillis(USD_TEST_PERIOD_MILLIS)
                .setOperatingFrequenciesMhz(mFreqs)
                .setRxMatchFilter(mFilter)
                .setTxMatchFilter(mFilter)
                .setSubscribeType(SubscribeConfig.SUBSCRIBE_TYPE_ACTIVE)
                .setServiceSpecificInfo(mSsi)
                .setServiceProtoType(Config.SERVICE_PROTO_TYPE_CSA_MATTER)
                .setQueryPeriodMillis(USD_TEST_PERIOD_MILLIS)
                .setTtlSeconds(USD_TTL_SEC).build();
        PublishConfig publishConfig = new PublishConfig.Builder(USD_TEST_SERVICE_NAME)
                .setAnnouncementPeriodMillis(USD_TEST_PERIOD_MILLIS)
                .setEventsEnabled(true)
                .setOperatingFrequenciesMhz(mFreqs)
                .setRxMatchFilter(mFilter)
                .setTxMatchFilter(mFilter)
                .setServiceProtoType(Config.SERVICE_PROTO_TYPE_CSA_MATTER)
                .setServiceSpecificInfo(mSsi)
                .setSolicitedTransmissionType(Config.TRANSMISSION_TYPE_UNICAST)
                .setTtlSeconds(USD_TTL_SEC)
                .build();
        when(mPublishSessionCallback.asBinder()).thenReturn(mAppBinder);
        when(mUsdNativeManager.publish(eq(USD_INTERFACE_NAME), eq(USD_REQUEST_COMMAND_ID),
                eq(publishConfig))).thenReturn(true);
        mUsdRequestManager.publish(publishConfig, mPublishSessionCallback);
        mUsdRequestManager.subscribe(subscribeConfig, mSubscribeSessionCallback);
        verify(mSubscribeSessionCallback).onSubscribeFailed(SessionCallback.FAILURE_NOT_AVAILABLE);
    }

    /**
     * Test USD subscribe failure when unsupported.
     */
    @Test
    public void testUsdSubscribeFailureWhenUnsupported() throws RemoteException {
        when(mWifiNative.getUsdCapabilities()).thenReturn(new UsdCapabilitiesInternal(false,
                true, 1024, 255, 255, 1, 1));
        mUsdRequestManager.getCharacteristics();
        SubscribeConfig subscribeConfig = new SubscribeConfig.Builder(USD_TEST_SERVICE_NAME)
                .setQueryPeriodMillis(USD_TEST_PERIOD_MILLIS)
                .setOperatingFrequenciesMhz(mFreqs)
                .setRxMatchFilter(mFilter)
                .setTxMatchFilter(mFilter)
                .setSubscribeType(SubscribeConfig.SUBSCRIBE_TYPE_ACTIVE)
                .setServiceSpecificInfo(mSsi)
                .setServiceProtoType(Config.SERVICE_PROTO_TYPE_CSA_MATTER)
                .setQueryPeriodMillis(USD_TEST_PERIOD_MILLIS)
                .setTtlSeconds(USD_TTL_SEC).build();
        mUsdRequestManager.subscribe(subscribeConfig, mSubscribeSessionCallback);
        verify(mSubscribeSessionCallback).onSubscribeFailed(SessionCallback.FAILURE_NOT_AVAILABLE);
    }

    /**
     * Test USD publish failure when unsupported.
     */
    @Test
    public void testUsdPublishFailureWhenUnsupported() throws RemoteException {
        when(mWifiNative.getUsdCapabilities()).thenReturn(new UsdCapabilitiesInternal(true,
                false, 1024, 255, 255, 1, 1));
        mUsdRequestManager.getCharacteristics();
        PublishConfig publishConfig = new PublishConfig.Builder(USD_TEST_SERVICE_NAME)
                .setAnnouncementPeriodMillis(USD_TEST_PERIOD_MILLIS)
                .setEventsEnabled(true)
                .setOperatingFrequenciesMhz(mFreqs)
                .setRxMatchFilter(mFilter)
                .setTxMatchFilter(mFilter)
                .setServiceProtoType(Config.SERVICE_PROTO_TYPE_CSA_MATTER)
                .setServiceSpecificInfo(mSsi)
                .setSolicitedTransmissionType(Config.TRANSMISSION_TYPE_UNICAST)
                .setTtlSeconds(USD_TTL_SEC)
                .build();
        mUsdRequestManager.publish(publishConfig, mPublishSessionCallback);
        verify(mPublishSessionCallback).onPublishFailed(SessionCallback.FAILURE_NOT_AVAILABLE);
    }

    /**
     * Test USD status listener for publisher and subscriber.
     * @throws RemoteException
     */
    @Test
    public void testUsdStatusListener() throws RemoteException {
        when(mPublisherListener.asBinder()).thenReturn(mAppBinder);
        when(mSubscriberListener.asBinder()).thenReturn(mAppBinder);
        mUsdRequestManager.registerPublisherStatusListener(mPublisherListener);
        mUsdRequestManager.registerSubscriberStatusListener(mSubscriberListener);
        // Initially, publisher and  should be available
        verify(mPublisherListener).onResult(true);
        verify(mSubscriberListener).onResult(true);

        // Start a publish session
        PublishConfig publishConfig = new PublishConfig.Builder(USD_TEST_SERVICE_NAME)
                .setAnnouncementPeriodMillis(USD_TEST_PERIOD_MILLIS)
                .setEventsEnabled(true)
                .setOperatingFrequenciesMhz(mFreqs)
                .setRxMatchFilter(mFilter)
                .setTxMatchFilter(mFilter)
                .setServiceProtoType(Config.SERVICE_PROTO_TYPE_CSA_MATTER)
                .setServiceSpecificInfo(mSsi)
                .setSolicitedTransmissionType(Config.TRANSMISSION_TYPE_UNICAST)
                .setTtlSeconds(USD_TTL_SEC)
                .build();
        when(mPublishSessionCallback.asBinder()).thenReturn(mAppBinder);
        when(mUsdNativeManager.publish(eq(USD_INTERFACE_NAME), eq(USD_REQUEST_COMMAND_ID),
                eq(publishConfig))).thenReturn(true);
        mUsdRequestManager.publish(publishConfig, mPublishSessionCallback);
        verify(mPublishSessionCallback, times(0)).onPublishFailed(anyInt());
        mUsdNativeEventsCallback.onUsdPublishStarted(USD_REQUEST_COMMAND_ID, TEST_PUBLISH_ID);
        verify(mPublishSessionCallback).onPublishStarted(TEST_PUBLISH_ID);

        // After starting, subscriber should be unavailable
        verify(mSubscriberListener, times(1)).onResult(false);

        // Stop publish session.
        mUsdRequestManager.cancelPublish(TEST_PUBLISH_ID);
        verify(mUsdNativeManager).cancelPublish(USD_INTERFACE_NAME, TEST_PUBLISH_ID);
        // Subscriber should be available again
        verify(mPublisherListener, times(1)).onResult(true);
    }

    /**
     * Test USD publish when interface name is null.
     */
    @Test
    public void testPublishWithNullInterfaceName() throws RemoteException {
        when(mClientModeManager.getInterfaceName()).thenReturn(null);
        PublishConfig publishConfig = new PublishConfig.Builder(USD_TEST_SERVICE_NAME)
                .setAnnouncementPeriodMillis(USD_TEST_PERIOD_MILLIS)
                .setEventsEnabled(true)
                .setOperatingFrequenciesMhz(mFreqs)
                .setRxMatchFilter(mFilter)
                .setTxMatchFilter(mFilter)
                .setServiceProtoType(Config.SERVICE_PROTO_TYPE_CSA_MATTER)
                .setServiceSpecificInfo(mSsi)
                .setSolicitedTransmissionType(Config.TRANSMISSION_TYPE_UNICAST)
                .setTtlSeconds(USD_TTL_SEC)
                .build();
        mUsdRequestManager.publish(publishConfig, mPublishSessionCallback);
        verify(mPublishSessionCallback).onPublishFailed(SessionCallback.FAILURE_NOT_AVAILABLE);
    }

    /**
     * Test USD subscribe when interface name is null.
     */
    @Test
    public void testSubscribeWithNullInterfaceName() throws RemoteException {
        when(mClientModeManager.getInterfaceName()).thenReturn(null);
        SubscribeConfig subscribeConfig = new SubscribeConfig.Builder(USD_TEST_SERVICE_NAME)
                .setQueryPeriodMillis(USD_TEST_PERIOD_MILLIS)
                .setOperatingFrequenciesMhz(mFreqs)
                .setRxMatchFilter(mFilter)
                .setTxMatchFilter(mFilter)
                .setSubscribeType(SubscribeConfig.SUBSCRIBE_TYPE_ACTIVE)
                .setServiceSpecificInfo(mSsi)
                .setServiceProtoType(Config.SERVICE_PROTO_TYPE_CSA_MATTER)
                .setQueryPeriodMillis(USD_TEST_PERIOD_MILLIS)
                .setTtlSeconds(USD_TTL_SEC).build();
        mUsdRequestManager.subscribe(subscribeConfig, mSubscribeSessionCallback);
        verify(mSubscribeSessionCallback).onSubscribeFailed(SessionCallback.FAILURE_NOT_AVAILABLE);
    }
}
