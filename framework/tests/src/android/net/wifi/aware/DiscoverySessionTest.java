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

package android.net.wifi.aware;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;

import android.net.wifi.aware.AwarePairingConfig;
import android.net.wifi.aware.Characteristics;

import androidx.test.filters.SmallTest;

import com.android.modules.utils.build.SdkLevel;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Unit test harness for DiscoverySession class.
 */
@SmallTest
public class DiscoverySessionTest {
    @Mock
    private WifiAwareManager mWifiAwareManager;
    private DiscoverySession mDut;
    private static final int CLIENT_ID = 100;
    private static final int SESSION_ID = 200;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mDut = new DiscoverySession(mWifiAwareManager, CLIENT_ID, SESSION_ID);
    }

    @Test
    public void testClose() {
        mDut.close();
        verify(mWifiAwareManager).terminateSession(CLIENT_ID, SESSION_ID);
    }

    @Test
    public void testSendMessage() {
        PeerHandle peerHandle = new PeerHandle(1234);
        int messageId = 55;
        byte[] message = "hello".getBytes();
        int retryCount = 2;

        mDut.sendMessage(peerHandle, messageId, message, retryCount);
        verify(mWifiAwareManager).sendMessage(CLIENT_ID, SESSION_ID, peerHandle, message, messageId,
                retryCount);
    }

    @Test
    public void testSendMessageDefaultRetry() {
        PeerHandle peerHandle = new PeerHandle(1234);
        int messageId = 55;
        byte[] message = "hello".getBytes();

        mDut.sendMessage(peerHandle, messageId, message);
        verify(mWifiAwareManager).sendMessage(CLIENT_ID, SESSION_ID, peerHandle, message, messageId,
                0);
    }

    @Test
    public void testInitiatePairingRequest() {
        assumeTrue(SdkLevel.isAtLeastU());
        PeerHandle peerHandle = new PeerHandle(1234);
        String alias = "alias";
        int cipherSuite = Characteristics.WIFI_AWARE_CIPHER_SUITE_NCS_SK_128;
        String password = "password";

        mDut.initiatePairingRequest(peerHandle, alias, cipherSuite, password);
        verify(mWifiAwareManager).initiateNanPairingSetupRequest(CLIENT_ID, SESSION_ID, peerHandle,
                password, alias, cipherSuite);
    }

    @Test
    public void testAcceptPairingRequest() {
        assumeTrue(SdkLevel.isAtLeastU());
        int requestId = 10;
        PeerHandle peerHandle = new PeerHandle(1234);
        String alias = "alias";
        int cipherSuite = Characteristics.WIFI_AWARE_CIPHER_SUITE_NCS_SK_128;
        String password = "password";

        mDut.acceptPairingRequest(requestId, peerHandle, alias, cipherSuite, password);
        verify(mWifiAwareManager).responseNanPairingSetupRequest(CLIENT_ID, SESSION_ID, peerHandle,
                requestId, password, alias, true, cipherSuite);
    }

    @Test
    public void testRejectPairingRequest() {
        assumeTrue(SdkLevel.isAtLeastU());
        int requestId = 10;
        PeerHandle peerHandle = new PeerHandle(1234);

        mDut.rejectPairingRequest(requestId, peerHandle);
        // Checking the specific parameters passed for reject
        verify(mWifiAwareManager).responseNanPairingSetupRequest(eq(CLIENT_ID), eq(SESSION_ID),
                eq(peerHandle), eq(requestId), isNull(), isNull(), eq(false), anyInt());
    }

    @Test
    public void testInitiateBootstrappingRequest() {
        assumeTrue(SdkLevel.isAtLeastU());
        PeerHandle peerHandle = new PeerHandle(1234);
        int method = AwarePairingConfig.PAIRING_BOOTSTRAPPING_OPPORTUNISTIC;

        mDut.initiateBootstrappingRequest(peerHandle, method);
        verify(mWifiAwareManager).initiateBootStrappingSetupRequest(CLIENT_ID, SESSION_ID,
                peerHandle, method, null);
    }

    @Test
    public void testInitiateBootstrappingRequestWithMessage() {
        PeerHandle peerHandle = new PeerHandle(1234);
        int method = AwarePairingConfig.PAIRING_BOOTSTRAPPING_PIN_CODE_DISPLAY;
        byte[] message = "bootstrapping".getBytes();

        mDut.initiateBootstrappingRequest(peerHandle, method, message);
        verify(mWifiAwareManager).initiateBootStrappingSetupRequest(CLIENT_ID, SESSION_ID,
                peerHandle, method, message);
    }

    @Test
    public void testSuspend() {
        assumeTrue(SdkLevel.isAtLeastU());
        mDut.suspend();
        verify(mWifiAwareManager).suspend(CLIENT_ID, SESSION_ID);
    }

    @Test
    public void testResume() {
        assumeTrue(SdkLevel.isAtLeastU());
        mDut.resume();
        verify(mWifiAwareManager).resume(CLIENT_ID, SESSION_ID);
    }

    @Test
    public void testReleaseDataPath() {
        PeerHandle peerHandle = new PeerHandle(1234);
        boolean result = mDut.releaseDataPath(peerHandle);
        assertTrue(result);
        verify(mWifiAwareManager).releaseDataPath(CLIENT_ID, SESSION_ID, peerHandle);
    }

    @Test
    public void testReleaseDataPathTerminated() {
        mDut.close(); // Terminate session
        PeerHandle peerHandle = new PeerHandle(1234);
        boolean result = mDut.releaseDataPath(peerHandle);
        assertFalse(result);
        verify(mWifiAwareManager, times(0)).releaseDataPath(eq(CLIENT_ID), eq(SESSION_ID), any());
    }

    @Test
    public void testCreateNetworkSpecifierOpen() {
        PeerHandle peerHandle = new PeerHandle(1234);
        mDut.createNetworkSpecifierOpen(peerHandle);
        // Default role is RESPONDER for base class (or rather it depends on subclass type).
        // Wait, the code says:
        // int role = this instanceof SubscribeDiscoverySession
        //        ? WifiAwareManager.WIFI_AWARE_DATA_PATH_ROLE_INITIATOR
        //        : WifiAwareManager.WIFI_AWARE_DATA_PATH_ROLE_RESPONDER;
        // Since mDut is DiscoverySession (base), it is NOT SubscribeDiscoverySession.
        // So role should be RESPONDER.

        verify(mWifiAwareManager).createNetworkSpecifier(CLIENT_ID,
                WifiAwareManager.WIFI_AWARE_DATA_PATH_ROLE_RESPONDER, SESSION_ID, peerHandle, null,
                null);
    }
}
