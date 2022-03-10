/*
 * Copyright (C) 2022 The Android Open Source Project
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

import static com.android.server.wifi.WifiSettingsStore.APM_DISABLED;
import static com.android.server.wifi.WifiSettingsStore.APM_ENABLED;
import static com.android.server.wifi.WifiSettingsStore.APM_WIFI_ENABLED_NOTIFICATION;
import static com.android.server.wifi.WifiSettingsStore.APM_WIFI_NOTIFICATION;
import static com.android.server.wifi.WifiSettingsStore.BLUETOOTH_APM_STATE;
import static com.android.server.wifi.WifiSettingsStore.BT_REMAINS_ON_IN_APM;
import static com.android.server.wifi.WifiSettingsStore.BT_TURNS_OFF_IN_APM;
import static com.android.server.wifi.WifiSettingsStore.NOTIFICATION_NOT_SHOWN;
import static com.android.server.wifi.WifiSettingsStore.NOTIFICATION_SHOWN;
import static com.android.server.wifi.WifiSettingsStore.WIFI_APM_STATE;
import static com.android.server.wifi.WifiSettingsStore.WIFI_DISABLED;
import static com.android.server.wifi.WifiSettingsStore.WIFI_DISABLED_APM_ON;
import static com.android.server.wifi.WifiSettingsStore.WIFI_ENABLED;
import static com.android.server.wifi.WifiSettingsStore.WIFI_ENABLED_APM_OVERRIDE;
import static com.android.server.wifi.WifiSettingsStore.WIFI_REMAINS_ON_IN_APM;
import static com.android.server.wifi.WifiSettingsStore.WIFI_TURNS_OFF_IN_APM;

import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.Notification;
import android.content.ContentResolver;
import android.content.res.Resources;
import android.net.wifi.WifiContext;
import android.os.Handler;
import android.os.test.TestLooper;
import android.provider.Settings;

import androidx.test.filters.SmallTest;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Unit tests for {@link WifiSettingsStore}.
 */
@SmallTest
public class WifiSettingsStoreTest extends WifiBaseTest {
    private WifiSettingsStore mWifiSettingsStore;
    private TestLooper mLooper;
    private WifiThreadRunner mWifiThreadRunner;

    @Mock private WifiContext mContext;
    @Mock private ContentResolver mContentResolver;
    @Mock private DeviceConfigFacade mDeviceConfigFacade;
    @Mock private FrameworkFacade mFrameworkFacade;
    @Mock private Notification mNotification;
    @Mock private Notification.Builder mNotificationBuilder;
    @Mock private Resources mResources;
    @Mock private WifiNotificationManager mNotificationManager;
    @Mock private WifiSettingsConfigStore mWifiSettingsConfigStore;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        mLooper = new TestLooper();
        mWifiThreadRunner = new WifiThreadRunner(new Handler(mLooper.getLooper()));
        mWifiThreadRunner.setTimeoutsAreErrors(true);
        when(mContext.getContentResolver()).thenReturn(mContentResolver);
        when(mContext.getResources()).thenReturn(mResources);
        when(mContext.getWifiOverlayApkPkgName()).thenReturn("test.com.android.wifi.resources");
        when(mResources.getString(anyInt())).thenReturn("resource");
        when(mFrameworkFacade.getIntegerSetting(mContentResolver, Settings.Global.AIRPLANE_MODE_ON,
                APM_DISABLED)).thenReturn(APM_DISABLED);
        when(mFrameworkFacade.getIntegerSetting(mContentResolver,
                Settings.Global.WIFI_ON)).thenReturn(WIFI_ENABLED);
        when(mFrameworkFacade.getStringSetting(mContext,
                Settings.Global.AIRPLANE_MODE_TOGGLEABLE_RADIOS)).thenReturn("wifi");
        when(mFrameworkFacade.getStringSetting(mContext,
                Settings.Global.AIRPLANE_MODE_RADIOS)).thenReturn("wifi");
        when(mFrameworkFacade.getUserContext(mContext)).thenReturn(mContext);
        when(mFrameworkFacade.getSettingsPackageName(mContext)).thenReturn("package");
        when(mFrameworkFacade.makeNotificationBuilder(any(), anyString()))
                .thenReturn(mNotificationBuilder);
        when(mNotificationBuilder.setSmallIcon(any())).thenReturn(mNotificationBuilder);
        when(mNotificationBuilder.setContentTitle(any())).thenReturn(mNotificationBuilder);
        when(mNotificationBuilder.setContentText(any())).thenReturn(mNotificationBuilder);
        when(mNotificationBuilder.setStyle(any())).thenReturn(mNotificationBuilder);
        when(mNotificationBuilder.setLocalOnly(anyBoolean())).thenReturn(mNotificationBuilder);
        when(mNotificationBuilder.setAutoCancel(anyBoolean())).thenReturn(mNotificationBuilder);
        when(mNotificationBuilder.build()).thenReturn(mNotification);

        mWifiSettingsStore = new WifiSettingsStore(mContext, mWifiSettingsConfigStore,
                mWifiThreadRunner, mFrameworkFacade, mNotificationManager, mDeviceConfigFacade);
    }

    @Test
    public void testApmEnhancementWifiToggled() {
        // APM Enhancement feature and APM is enabled
        when(mFrameworkFacade.getIntegerSetting(mContentResolver, Settings.Global.AIRPLANE_MODE_ON,
                APM_DISABLED)).thenReturn(APM_ENABLED);
        assertTrue(mWifiSettingsStore.updateAirplaneModeTracker());
        mWifiSettingsStore.setPersistWifiState(WIFI_ENABLED);

        when(mDeviceConfigFacade.isApmEnhancementEnabled()).thenReturn(true);
        when(mFrameworkFacade.getSecureIntegerSetting(mContext, APM_WIFI_ENABLED_NOTIFICATION,
                NOTIFICATION_NOT_SHOWN)).thenReturn(NOTIFICATION_NOT_SHOWN);

        // Toggling Wi-Fi on triggers notification and enables the Wi-Fi APM state
        assertTrue(mWifiSettingsStore.handleWifiToggled(true));
        mLooper.dispatchAll();

        verify(mFrameworkFacade).setIntegerSetting(mContentResolver,
                Settings.Global.WIFI_ON, WIFI_ENABLED_APM_OVERRIDE);
        verify(mFrameworkFacade).setSecureIntegerSetting(
                mContext, WIFI_APM_STATE, WIFI_REMAINS_ON_IN_APM);
        verify(mNotificationManager).notify(anyInt(), any(Notification.class));
        verify(mFrameworkFacade).setSecureIntegerSetting(
                mContext, APM_WIFI_ENABLED_NOTIFICATION, NOTIFICATION_SHOWN);

        // Toggling Wi-Fi off disables the Wi-Fi APM state
        assertTrue(mWifiSettingsStore.handleWifiToggled(false));

        verify(mFrameworkFacade).setIntegerSetting(mContentResolver,
                Settings.Global.WIFI_ON, WIFI_DISABLED);
        verify(mFrameworkFacade).setSecureIntegerSetting(
                mContext, WIFI_APM_STATE, WIFI_TURNS_OFF_IN_APM);
    }

    @Test
    public void testApmEnhancementAirplaneModeToggled() {
        // APM Enhancement feature and Wi-Fi is enabled
        when(mFrameworkFacade.getIntegerSetting(mContentResolver, Settings.Global.AIRPLANE_MODE_ON,
                APM_DISABLED)).thenReturn(APM_ENABLED);
        when(mFrameworkFacade.getIntegerSetting(mContentResolver, Settings.Global.BLUETOOTH_ON,
                0)).thenReturn(1);
        when(mFrameworkFacade.getSecureIntegerSetting(mContext, APM_WIFI_NOTIFICATION,
                NOTIFICATION_NOT_SHOWN)).thenReturn(NOTIFICATION_NOT_SHOWN);
        when(mDeviceConfigFacade.isApmEnhancementEnabled()).thenReturn(true);
        assertTrue(mWifiSettingsStore.updateAirplaneModeTracker());
        mWifiSettingsStore.setPersistWifiState(WIFI_ENABLED);

        // Wi-Fi remains on when Wi-Fi APM state is enabled
        // Notification is not shown when BT APM state is also enabled
        when(mFrameworkFacade.getSecureIntegerSetting(mContext, WIFI_APM_STATE,
                WIFI_TURNS_OFF_IN_APM)).thenReturn(WIFI_REMAINS_ON_IN_APM);
        when(mFrameworkFacade.getSecureIntegerSetting(mContext, BLUETOOTH_APM_STATE,
                BT_TURNS_OFF_IN_APM)).thenReturn(BT_REMAINS_ON_IN_APM);

        mWifiSettingsStore.handleAirplaneModeToggled();
        mLooper.dispatchAll();
        verify(mFrameworkFacade).setIntegerSetting(mContentResolver,
                Settings.Global.WIFI_ON, WIFI_ENABLED_APM_OVERRIDE);
        verify(mNotificationManager, never()).notify(anyInt(), any(Notification.class));
        verify(mFrameworkFacade, never()).setSecureIntegerSetting(
                mContext, APM_WIFI_NOTIFICATION, NOTIFICATION_SHOWN);

        // Notification is shown when BT APM state is disabled
        mWifiSettingsStore.setPersistWifiState(WIFI_ENABLED);
        when(mFrameworkFacade.getSecureIntegerSetting(mContext, BLUETOOTH_APM_STATE,
                BT_TURNS_OFF_IN_APM)).thenReturn(BT_TURNS_OFF_IN_APM);

        mWifiSettingsStore.handleAirplaneModeToggled();
        mLooper.dispatchAll();
        verify(mNotificationManager).notify(anyInt(), any(Notification.class));
        verify(mFrameworkFacade).setSecureIntegerSetting(
                mContext, APM_WIFI_NOTIFICATION, NOTIFICATION_SHOWN);

        // Wi-Fi turns off when Wi-Fi APM state is disabled
        mWifiSettingsStore.setPersistWifiState(WIFI_ENABLED);
        when(mFrameworkFacade.getSecureIntegerSetting(mContext, WIFI_APM_STATE,
                WIFI_TURNS_OFF_IN_APM)).thenReturn(WIFI_TURNS_OFF_IN_APM);

        mWifiSettingsStore.handleAirplaneModeToggled();
        verify(mFrameworkFacade).setIntegerSetting(mContentResolver,
                Settings.Global.WIFI_ON, WIFI_DISABLED_APM_ON);
    }
}
