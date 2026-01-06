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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.validateMockitoUsage;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import android.annotation.NonNull;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.hardware.wifi.supplicant.ISupplicant;
import android.hardware.wifi.supplicant.ISupplicantStaIface;
import android.hardware.wifi.supplicant.ISupplicantStaIfaceCallback;
import android.net.wifi.util.BuildProperties;
import android.net.wifi.util.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.test.TestLooper;
import android.system.wifi.mainline_supplicant.IMainlineSupplicant;

import androidx.test.filters.SmallTest;

import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.wifi.flags.Flags;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.MockitoSession;

import java.io.PrintWriter;

/**
 * Unit tests for {@link SupplicantStaIfaceHalAidlMainlineImpl}.
 */
@SmallTest
public class SupplicantStaIfaceHalAidlMainlineImplTest extends WifiBaseTest {
    @Mock private ISupplicant mISupplicantMock;
    @Mock private IMainlineSupplicant mIMainlineSupplicantMock;
    @Mock private IBinder mServiceBinderMock;
    @Mock private Context mContext;
    @Mock private WifiMonitor mWifiMonitor;
    @Mock private WifiNative.SupplicantDeathEventHandler mSupplicantHalDeathHandler;
    @Mock private Clock mClock;
    @Mock private WifiMetrics mWifiMetrics;
    @Mock private WifiGlobals mWifiGlobals;
    @Mock private SsidTranslator mSsidTranslator;
    @Mock private WifiInjector mWifiInjector;
    @Mock private Resources mResources;
    @Mock private BuildProperties mBuildProperties;
    @Mock private PackageManager mPackageManager;
    @Mock private ISupplicantStaIface mISupplicantStaIfaceMock;
    @Mock private WifiConfigManager mWifiConfigManager;
    @Mock private WifiSettingsConfigStore mWifiSettingsConfigStore;

    private MockitoSession mSession;
    private TestLooper mLooper = new TestLooper();
    private Handler mHandler = null;
    private SupplicantStaIfaceHalSpy mDut;
    private ArgumentCaptor<IBinder.DeathRecipient> mSupplicantDeathCaptor =
            ArgumentCaptor.forClass(IBinder.DeathRecipient.class);

    private class SupplicantStaIfaceHalSpy extends SupplicantStaIfaceHalAidlMainlineImpl {
        SupplicantStaIfaceHalSpy(Context context, WifiMonitor monitor,
                Handler handler, Clock clock, WifiMetrics wifiMetrics, WifiGlobals wifiGlobals,
                @NonNull SsidTranslator ssidTranslator, WifiInjector wifiInjector) {
            super(context, monitor, handler, clock, wifiMetrics, wifiGlobals, ssidTranslator,
                    wifiInjector);
        }

        @Override
        protected IMainlineSupplicant getNewServiceBinderMockable() {
            return mIMainlineSupplicantMock;
        }

        @Override
        protected boolean isServiceAvailableMockable(Context context) {
            return true;
        }
    }

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        mSession = ExtendedMockito.mockitoSession()
                .mockStatic(Flags.class, withSettings().lenient())
                .mockStatic(Environment.class, withSettings().lenient())
                .mockStatic(BuildProperties.class, withSettings().lenient())
                .startMocking();

        mHandler = spy(new Handler(mLooper.getLooper()));

        when(mContext.getResources()).thenReturn(mResources);
        when(mContext.getPackageManager()).thenReturn(mPackageManager);
        when(BuildProperties.getInstance()).thenReturn(mBuildProperties);
        when(mResources.getBoolean(anyInt())).thenReturn(true);
        when(mIMainlineSupplicantMock.asBinder()).thenReturn(mServiceBinderMock);
        when(mIMainlineSupplicantMock.getVendorSupplicant()).thenReturn(mISupplicantMock);
        when(mWifiInjector.getWifiConfigManager()).thenReturn(mWifiConfigManager);
        when(mWifiInjector.getSettingsConfigStore()).thenReturn(mWifiSettingsConfigStore);
        when(mWifiConfigManager.getCurrentUserId()).thenReturn(0);
        when(mISupplicantMock.getInterfaceVersion()).thenReturn(5);

        mDut = new SupplicantStaIfaceHalSpy(mContext, mWifiMonitor, mHandler, mClock,
                mWifiMetrics, mWifiGlobals, mSsidTranslator, mWifiInjector);
    }

    @After
    public void cleanup() {
        validateMockitoUsage();
        if (mSession != null) {
            mSession.finishMocking();
        }
    }

    /**
     * Helper function to execute and validate the initialization sequence.
     */
    private void executeAndValidateInitializationSequence() throws Exception {
        assertTrue(mDut.initialize());
        assertTrue(mDut.startDaemon());
        assertTrue(mDut.isInitializationComplete());

        InOrder inOrder = inOrder(mIMainlineSupplicantMock, mISupplicantMock, mServiceBinderMock);
        inOrder.verify(mIMainlineSupplicantMock).getVendorSupplicant();
        inOrder.verify(mServiceBinderMock).linkToDeath(mSupplicantDeathCaptor.capture(), eq(0));
    }

    /**
     * Sunny day scenario for SupplicantStaIface HAL Mainline initialization.
     * Asserts successful initialization.
     */
    @Test
    public void testInitialize_success() throws Exception {
        executeAndValidateInitializationSequence();
    }

    /**
     * Tests the initialization failure flow when the mainline supplicant service cannot be
     * retrieved from the ServiceManager.
     */
    @Test
    public void testInitialize_serviceNotRetrievedFailure() throws Exception {
        mDut = new SupplicantStaIfaceHalSpy(mContext, mWifiMonitor, mHandler, mClock,
                mWifiMetrics, mWifiGlobals, mSsidTranslator, mWifiInjector) {
            @Override
            protected IMainlineSupplicant getNewServiceBinderMockable() {
                return null;
            }
        };

        assertTrue(mDut.initialize());
        assertFalse(mDut.startDaemon());
        assertFalse(mDut.isInitializationComplete());

        verify(mIMainlineSupplicantMock, never()).getVendorSupplicant();
    }

    /**
     * Tests the initialization failure flow when the mainline service fails to return the vendor
     * ISupplicant instance.
     */
    @Test
    public void testInitialize_vendorSupplicantNotAvailableFailure() throws Exception {
        when(mIMainlineSupplicantMock.getVendorSupplicant()).thenReturn(null);

        assertTrue(mDut.initialize());
        assertFalse(mDut.startDaemon());
        assertFalse(mDut.isInitializationComplete());

        verify(mIMainlineSupplicantMock).getVendorSupplicant();
    }

    /**
     * Tests that the death of the mainline service binder is correctly handled.
     */
    @Test
    public void testDeathRecipient() throws Exception {
        executeAndValidateInitializationSequence();
        mDut.registerDeathHandler(mSupplicantHalDeathHandler);

        IBinder.DeathRecipient deathRecipient = mSupplicantDeathCaptor.getValue();
        deathRecipient.binderDied(mServiceBinderMock);
        mLooper.dispatchAll();

        assertFalse(mDut.isInitializationComplete());
        verify(mSupplicantHalDeathHandler).onDeath();
    }

    private void setupIsServiceAvailableHappyPath() {
        when(mResources.getBoolean(
                com.android.wifi.resources.R.bool.config_wifiMainlineSupplicantEnabled))
                .thenReturn(true);
        ExtendedMockito.doReturn(true).when(() -> Environment.isSdkAtLeastB());
        ExtendedMockito.doReturn(true).when(() -> Flags.mainlineSupplicant());
        ExtendedMockito.doReturn(true).when(() -> Environment
                                                  .isMainlineSupplicantBinaryInWifiApex());
        when(mPackageManager.hasSystemFeature(PackageManager.FEATURE_WATCH)).thenReturn(false);
        when(mPackageManager.hasSystemFeature(PackageManager.FEATURE_EMBEDDED)).thenReturn(false);
        when(mPackageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)).thenReturn(false);
        when(mPackageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)).thenReturn(false);
        when(mBuildProperties.isUserBuild()).thenReturn(false);
    }

    @Test
    public void testIsServiceAvailable_happyPath() {
        setupIsServiceAvailableHappyPath();
        assertTrue(SupplicantStaIfaceHalAidlMainlineImpl.isServiceAvailable(mContext));
    }

    @Test
    public void testIsServiceAvailable_returnsFalseWhenOverlayIsFalse() {
        setupIsServiceAvailableHappyPath();
        when(mResources.getBoolean(
                com.android.wifi.resources.R.bool.config_wifiMainlineSupplicantEnabled))
                .thenReturn(false);
        assertFalse(SupplicantStaIfaceHalAidlMainlineImpl.isServiceAvailable(mContext));
    }

    @Test
    public void testIsServiceAvailable_returnsFalseWhenDeviceIsWatch() {
        setupIsServiceAvailableHappyPath();
        when(mPackageManager.hasSystemFeature(PackageManager.FEATURE_WATCH)).thenReturn(true);
        assertFalse(SupplicantStaIfaceHalAidlMainlineImpl.isServiceAvailable(mContext));
    }

    @Test
    public void testIsServiceAvailable_returnsFalseWhenBuildIsUser() {
        setupIsServiceAvailableHappyPath();
        when(mBuildProperties.isUserBuild()).thenReturn(true);
        assertFalse(SupplicantStaIfaceHalAidlMainlineImpl.isServiceAvailable(mContext));
    }

    @Test
    public void testIsServiceAvailable_returnsFalseWhenFlagIsDisabled() {
        setupIsServiceAvailableHappyPath();
        ExtendedMockito.doReturn(false).when(() -> Flags.mainlineSupplicant());
        assertFalse(SupplicantStaIfaceHalAidlMainlineImpl.isServiceAvailable(mContext));
    }

    @Test
    public void testIsServiceAvailable_returnsFalseWhenBinaryNotInApex() {
        setupIsServiceAvailableHappyPath();
        ExtendedMockito.doReturn(false).when(() -> Environment
                                                  .isMainlineSupplicantBinaryInWifiApex());
        assertFalse(SupplicantStaIfaceHalAidlMainlineImpl.isServiceAvailable(mContext));
    }

    /**
     * Test that we can call {@link SupplicantStaIfaceHalAidlMainlineImpl#dump(PrintWriter)}
     */
    @Test
    public void testDump() {
        PrintWriter pw = mock(PrintWriter.class);
        mDut.dump(pw);
        verify(pw, atLeastOnce()).println(anyString());
    }

    @Test
    public void testSetupIface() throws Exception {
        executeAndValidateInitializationSequence();
        when(mISupplicantMock.addStaInterface(anyString())).thenReturn(mISupplicantStaIfaceMock);

        assertTrue(mDut.setupIface("wlan0"));

        InOrder inOrder = inOrder(mISupplicantMock, mISupplicantStaIfaceMock);
        inOrder.verify(mISupplicantMock).setCurrentUserIdentity(anyInt());
        inOrder.verify(mISupplicantMock).addStaInterface(eq("wlan0"));
        inOrder.verify(mISupplicantStaIfaceMock).registerCallback(
                any(ISupplicantStaIfaceCallback.class));
    }

    @Test
    public void testSetupIface_setCurrentUserIdentityFailure() throws Exception {
        executeAndValidateInitializationSequence();
        doThrow(new RemoteException()).when(mISupplicantMock).setCurrentUserIdentity(anyInt());
        assertFalse(mDut.setupIface("wlan0"));
    }

    @Test
    public void testSetupIface_addIfaceFailure() throws Exception {
        executeAndValidateInitializationSequence();
        when(mISupplicantMock.addStaInterface(anyString())).thenReturn(null);
        assertFalse(mDut.setupIface("wlan0"));
        verify(mISupplicantMock).setCurrentUserIdentity(anyInt());
    }

    @Test
    public void testSetupIface_registerCallbackFailure() throws Exception {
        executeAndValidateInitializationSequence();
        when(mISupplicantMock.addStaInterface(anyString())).thenReturn(mISupplicantStaIfaceMock);
        doThrow(new RemoteException()).when(mISupplicantStaIfaceMock).registerCallback(
                any(ISupplicantStaIfaceCallback.class));
        assertFalse(mDut.setupIface("wlan0"));
        verify(mISupplicantMock).setCurrentUserIdentity(anyInt());
        verify(mISupplicantMock).addStaInterface(eq("wlan0"));
    }
}
