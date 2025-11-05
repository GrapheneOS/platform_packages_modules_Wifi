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

package com.android.server.wifi.p2p;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.validateMockitoUsage;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.hardware.wifi.supplicant.ISupplicant;
import android.net.wifi.WifiContext;
import android.net.wifi.util.BuildProperties;
import android.net.wifi.util.Environment;
import android.os.IBinder;
import android.system.wifi.mainline_supplicant.IMainlineSupplicant;

import androidx.test.filters.SmallTest;

import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.server.wifi.WifiBaseTest;
import com.android.server.wifi.WifiInjector;
import com.android.server.wifi.WifiNative;
import com.android.wifi.flags.Flags;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.MockitoSession;

/**
 * Unit tests for {@link SupplicantP2pIfaceHalAidlMainlineImpl}.
 */
@SmallTest
public class SupplicantP2pIfaceHalAidlMainlineImplTest extends WifiBaseTest {
    @Mock private ISupplicant mISupplicantMock;
    @Mock private IMainlineSupplicant mIMainlineSupplicantMock;
    @Mock private IBinder mServiceBinderMock;
    @Mock private WifiContext mWifiContext;
    @Mock private WifiP2pMonitor mWifiMonitor;
    @Mock private WifiNative.SupplicantDeathEventHandler mSupplicantHalDeathHandler;
    @Mock private WifiInjector mWifiInjector;
    @Mock private Resources mResources;
    @Mock private BuildProperties mBuildProperties;
    @Mock private PackageManager mPackageManager;

    private MockitoSession mSession;
    private SupplicantP2pIfaceHalSpy mDut;
    private ArgumentCaptor<IBinder.DeathRecipient> mSupplicantDeathCaptor =
            ArgumentCaptor.forClass(IBinder.DeathRecipient.class);

    private class SupplicantP2pIfaceHalSpy extends SupplicantP2pIfaceHalAidlMainlineImpl {
        SupplicantP2pIfaceHalSpy(WifiP2pMonitor monitor, WifiInjector wifiInjector) {
            super(monitor, wifiInjector);
        }

        @Override
        protected IMainlineSupplicant getNewServiceBinderMockable() {
            return mIMainlineSupplicantMock;
        }

        @Override
        protected boolean isServiceAvailableMockable(WifiContext context) {
            return true;
        }
    }

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        mSession = ExtendedMockito.mockitoSession()
                .mockStatic(BuildProperties.class, withSettings().lenient())
                .mockStatic(Environment.class, withSettings().lenient())
                .mockStatic(Flags.class, withSettings().lenient())
                .startMocking();

        when(mWifiInjector.getContext()).thenReturn(mWifiContext);
        when(mWifiContext.getResources()).thenReturn(mResources);
        when(mWifiContext.getPackageManager()).thenReturn(mPackageManager);
        when(BuildProperties.getInstance()).thenReturn(mBuildProperties);
        when(mResources.getBoolean(anyInt())).thenReturn(true);
        when(mIMainlineSupplicantMock.asBinder()).thenReturn(mServiceBinderMock);
        when(mIMainlineSupplicantMock.getVendorSupplicant()).thenReturn(mISupplicantMock);

        mDut = new SupplicantP2pIfaceHalSpy(mWifiMonitor, mWifiInjector);
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
        assertTrue(mDut.isInitializationComplete());

        InOrder inOrder = inOrder(mIMainlineSupplicantMock, mServiceBinderMock);
        inOrder.verify(mIMainlineSupplicantMock).getVendorSupplicant();
        inOrder.verify(mServiceBinderMock).linkToDeath(mSupplicantDeathCaptor.capture(), eq(0));
    }

    /**
     * Sunny day scenario for SupplicantP2pIface HAL Mainline initialization.
     * Asserts successful initialization.
     */
    @Test
    public void testInitialize_success() throws Exception {
        executeAndValidateInitializationSequence();
    }

    /**
     * Tests the initialization failure flow when the mainline supplicant service is not available.
     */
    @Test
    public void testInitialize_serviceNotAvailableFailure() throws Exception {
        mDut = new SupplicantP2pIfaceHalSpy(mWifiMonitor, mWifiInjector) {
            @Override
            protected boolean isServiceAvailableMockable(WifiContext context) {
                return false;
            }
        };

        assertFalse(mDut.initialize());
        assertFalse(mDut.isInitializationComplete());
        verify(mIMainlineSupplicantMock, never()).getVendorSupplicant();
    }

    /**
     * Tests the initialization failure flow when the mainline supplicant service cannot be
     * retrieved from the ServiceManager.
     */
    @Test
    public void testInitialize_serviceBinderNotRetrievedFailure() throws Exception {
        mDut = new SupplicantP2pIfaceHalSpy(mWifiMonitor, mWifiInjector) {
            @Override
            protected IMainlineSupplicant getNewServiceBinderMockable() {
                return null;
            }
        };

        assertFalse(mDut.initialize());
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

        assertFalse(mDut.initialize());
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

        assertFalse(mDut.isInitializationComplete());
        verify(mSupplicantHalDeathHandler).onDeath();
    }
}
