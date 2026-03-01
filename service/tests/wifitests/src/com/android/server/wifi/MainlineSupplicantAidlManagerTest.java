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

package com.android.server.wifi;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.mockitoSession;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import android.content.pm.PackageManager;
import android.hardware.wifi.supplicant.ISupplicant;
import android.net.wifi.WifiContext;
import android.net.wifi.util.BuildProperties;
import android.net.wifi.util.Environment;
import android.net.wifi.util.WifiResourceCache;
import android.os.Handler;
import android.os.IBinder;
import android.os.test.TestLooper;
import android.system.wifi.mainline_supplicant.IMainlineSupplicant;
import android.system.wifi.mainline_supplicant.ISupplicantNanIface;

import androidx.test.filters.SmallTest;

import com.android.server.wifi.WifiNative.SupplicantDeathEventHandler;
import com.android.server.wifi.aware.AwareIfaceAidlSupplicantImpl;
import com.android.wifi.flags.Flags;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.MockitoSession;

/**
 * Unit tests for {@link com.android.server.wifi.MainlineSupplicantAidlManagerTest}.
 */
@SmallTest
public class MainlineSupplicantAidlManagerTest extends WifiBaseTest {
    @Mock
    IMainlineSupplicant mIMainlineSupplicant;
    @Mock
    ISupplicant mISupplicant;
    @Mock
    ISupplicantNanIface mISupplicantNanIface;
    @Mock
    IBinder mMainlineSupplicantBinder;
    @Mock
    WifiInjector mWifiInjector;
    @Mock
    WifiContext mWifiContext;
    @Mock
    WifiResourceCache mResources;
    @Mock
    PackageManager mPackageManager;
    @Mock
    BuildProperties mBuildProperties;
    private WifiThreadRunner mWifiThreadRunner;
    private TestLooper mTestLooper = new TestLooper();

    private MainlineSupplicantAidlManager mDut;
    private MockitoSession mStaticMockSession = null;
    private IMainlineSupplicant mIMainlineSupplicantSpy;

    private class MainlineSupplicantAidlManagerSpy extends MainlineSupplicantAidlManager {
        MainlineSupplicantAidlManagerSpy(WifiInjector wifiInjector) {
            super(wifiInjector);
        }

        @Override
        protected IMainlineSupplicant getNewServiceBinderMockable() {
            return mIMainlineSupplicantSpy;
        }

        @Override
        public boolean isServiceAvailableMockable(WifiContext context) {
            return true;
        }
    }

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        mStaticMockSession = mockitoSession()
                .mockStatic(BuildProperties.class, withSettings().lenient())
                .mockStatic(Environment.class, withSettings().lenient())
                .mockStatic(Flags.class, withSettings().lenient())
                .startMocking();
        mIMainlineSupplicantSpy = mIMainlineSupplicant;
        mWifiThreadRunner = new WifiThreadRunner(new Handler(mTestLooper.getLooper()));
        when(mWifiInjector.getContext()).thenReturn(mWifiContext);
        when(mWifiContext.getResourceCache()).thenReturn(mResources);
        when(mWifiContext.getPackageManager()).thenReturn(mPackageManager);
        when(mWifiInjector.getWifiThreadRunner()).thenReturn(mWifiThreadRunner);
        when(BuildProperties.getInstance()).thenReturn(mBuildProperties);
        when(mIMainlineSupplicant.asBinder()).thenReturn(mMainlineSupplicantBinder);
        when(mIMainlineSupplicant.getVendorSupplicant()).thenReturn(mISupplicant);
        when(mIMainlineSupplicant.addNanInterface("aware0")).thenReturn(mISupplicantNanIface);

        mDut = new MainlineSupplicantAidlManagerSpy(mWifiInjector);
    }

    @After
    public void tearDown() throws Exception {
        if (mStaticMockSession != null) {
            mStaticMockSession.finishMocking();
            mStaticMockSession = null;
        }
    }

    @Test
    public void testIsServiceAvailable() {
        when(mResources.getBoolean(
                com.android.wifi.resources.R.bool.config_wifiMainlineSupplicantEnabled))
                .thenReturn(true);
        when(mBuildProperties.isUserBuild()).thenReturn(false);
        when(Environment.isSdkAtLeastB()).thenReturn(true);
        when(Environment.isMainlineSupplicantBinaryInWifiApex()).thenReturn(true);
        when(Flags.mainlineSupplicant()).thenReturn(true);
        when(mPackageManager.hasSystemFeature(PackageManager.FEATURE_WATCH)).thenReturn(false);
        when(mPackageManager.hasSystemFeature(PackageManager.FEATURE_EMBEDDED)).thenReturn(false);
        when(mPackageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)).thenReturn(false);
        when(mPackageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)).thenReturn(false);
        when(mPackageManager.hasSystemFeature(PackageManager.FEATURE_PC)).thenReturn(true);
        assertTrue(MainlineSupplicantAidlManager.isServiceAvailable(mWifiContext));
    }

    @Test
    public void testIsServiceAvailableDisabledByOverlay() {
        when(mResources.getBoolean(
                com.android.wifi.resources.R.bool.config_wifiMainlineSupplicantEnabled))
                .thenReturn(false);
        when(mBuildProperties.isUserBuild()).thenReturn(false);
        //when(SdkLevel.isAtLeastU()).thenReturn(true);
        when(Flags.mainlineSupplicant()).thenReturn(true);
        assertFalse(MainlineSupplicantAidlManager.isServiceAvailable(mWifiContext));
    }

    @Test
    public void testIsServiceAvailableUserBuild() {
        when(mResources.getBoolean(
                com.android.wifi.resources.R.bool.config_wifiMainlineSupplicantEnabled))
                .thenReturn(true);
        when(mBuildProperties.isUserBuild()).thenReturn(true);
        //when(SdkLevel.isAtLeastU()).thenReturn(true);
        when(Flags.mainlineSupplicant()).thenReturn(true);
        assertFalse(MainlineSupplicantAidlManager.isServiceAvailable(mWifiContext));
    }

    @Test
    public void testIsServiceAvailableMissingFlag() {
        when(mResources.getBoolean(
                com.android.wifi.resources.R.bool.config_wifiMainlineSupplicantEnabled))
                .thenReturn(true);
        when(mBuildProperties.isUserBuild()).thenReturn(false);
        //when(SdkLevel.isAtLeastU()).thenReturn(true);
        when(Flags.mainlineSupplicant()).thenReturn(false);
        assertFalse(MainlineSupplicantAidlManager.isServiceAvailable(mWifiContext));
    }

    @Test
    public void testIsServiceAvailableWatch() {
        when(mResources.getBoolean(
                com.android.wifi.resources.R.bool.config_wifiMainlineSupplicantEnabled))
                .thenReturn(true);
        when(mBuildProperties.isUserBuild()).thenReturn(false);
        //when(SdkLevel.isAtLeastU()).thenReturn(true);
        when(Flags.mainlineSupplicant()).thenReturn(true);
        when(mPackageManager.hasSystemFeature(PackageManager.FEATURE_WATCH)).thenReturn(true);
        assertFalse(MainlineSupplicantAidlManager.isServiceAvailable(mWifiContext));
    }

    @Test
    public void testStartDaemon() throws Exception {
        when(mResources.getBoolean(
                com.android.wifi.resources.R.bool.config_wifiMainlineSupplicantEnabled))
                .thenReturn(true);
        when(mBuildProperties.isUserBuild()).thenReturn(false);
        when(Flags.mainlineSupplicant()).thenReturn(true);
        assertTrue(mDut.startDaemon());
        assertTrue(mDut.isInitializationComplete());
        ArgumentCaptor<IBinder.DeathRecipient> deathRecipientCaptor =
                ArgumentCaptor.forClass(IBinder.DeathRecipient.class);
        verify(mMainlineSupplicantBinder).linkToDeath(deathRecipientCaptor.capture(), anyInt());
    }

    @Test
    public void testStartDaemonFailure() {
        when(mResources.getBoolean(
                com.android.wifi.resources.R.bool.config_wifiMainlineSupplicantEnabled))
                .thenReturn(true);
        when(mBuildProperties.isUserBuild()).thenReturn(false);
        when(Flags.mainlineSupplicant()).thenReturn(true);
        mIMainlineSupplicantSpy = null;
        assertFalse(mDut.startDaemon());
        assertFalse(mDut.isInitializationComplete());
    }

    @Test
    public void testTerminate() throws Exception {
        testStartDaemon();
        mDut.terminate();
        verify(mISupplicant).terminate();
    }

    @Test
    public void testDeathRecipient() throws Exception {
        testStartDaemon();
        ArgumentCaptor<IBinder.DeathRecipient> deathRecipientCaptor =
                ArgumentCaptor.forClass(IBinder.DeathRecipient.class);
        verify(mMainlineSupplicantBinder).linkToDeath(deathRecipientCaptor.capture(), anyInt());
        deathRecipientCaptor.getValue().binderDied(mMainlineSupplicantBinder);
        mTestLooper.dispatchAll();
        assertFalse(mDut.isInitializationComplete());
    }

    @Test
    public void testGetWifiNanIface() throws Exception {
        testStartDaemon();
        AwareIfaceAidlSupplicantImpl iface = mDut.getWifiNanIface("aware0");
        verify(mIMainlineSupplicant).addNanInterface("aware0");
        iface.getName();
        verify(mISupplicantNanIface).getName();
    }

    @Test
    public void testRemoveWifiNanIface() throws Exception {
        testGetWifiNanIface();
        mDut.removeWifiNanIface("aware0");
        verify(mIMainlineSupplicant).removeNanInterface("aware0");
    }

    @Test
    public void testDeathHandlerRegistration() throws Exception {
        testStartDaemon();
        SupplicantDeathEventHandler deathEventHandler =
                mock(SupplicantDeathEventHandler.class);
        ArgumentCaptor<IBinder.DeathRecipient> deathRecipientCaptor =
                ArgumentCaptor.forClass(IBinder.DeathRecipient.class);
        verify(mMainlineSupplicantBinder).linkToDeath(deathRecipientCaptor.capture(), anyInt());

        // Register callback and test for death notification.
        mDut.registerDeathHandler(deathEventHandler);
        deathRecipientCaptor.getValue().binderDied(mMainlineSupplicantBinder);
        mTestLooper.dispatchAll();
        verify(deathEventHandler).onDeath();

        // Unregister callback and test for no death notification.
        mDut.unregisterDeathHandler(deathEventHandler);
        deathRecipientCaptor.getValue().binderDied(mMainlineSupplicantBinder);
        mTestLooper.dispatchAll();
        // Should not have any more interactions.
        verifyNoMoreInteractions(deathEventHandler);
    }

    @Test
    public void testIsAwareSupported() {
        when(mResources.getBoolean(
                com.android.wifi.resources.R.bool.config_supplicantAwareEnabled)).thenReturn(false);
        assertFalse(mDut.isAwareSupported());
        when(mResources.getBoolean(
                com.android.wifi.resources.R.bool.config_supplicantAwareEnabled)).thenReturn(true);
        assertTrue(mDut.isAwareSupported());
    }
}
