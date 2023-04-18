/*
 * Copyright (C) 2021 The Android Open Source Project
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

import static com.android.server.wifi.InsecureEapNetworkHandler.TOFU_ANONYMOUS_IDENTITY;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.validateMockitoUsage;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.Notification;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiContext;
import android.net.wifi.WifiEnterpriseConfig;
import android.net.wifi.util.HexEncoding;
import android.os.Handler;
import android.text.TextUtils;

import androidx.test.filters.SmallTest;

import com.android.modules.utils.build.SdkLevel;
import com.android.server.wifi.util.CertificateSubjectInfo;
import com.android.wifi.resources.R;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.stubbing.Answer;

import java.nio.charset.StandardCharsets;
import java.security.cert.X509Certificate;

import javax.security.auth.x500.X500Principal;

/**
 * Unit tests for {@link com.android.server.wifi.InsecureEapNetworkHandlerTest}.
 */
@SmallTest
public class InsecureEapNetworkHandlerTest extends WifiBaseTest {

    private static final int ACTION_ACCEPT = 0;
    private static final int ACTION_REJECT = 1;
    private static final int ACTION_TAP = 2;
    private static final String WIFI_IFACE_NAME = "wlan-test-9";
    private static final int FRAMEWORK_NETWORK_ID = 2;
    private static final String TEST_SSID = "\"test_ssid\"";
    private static final String TEST_IDENTITY = "userid";
    private static final String TEST_PASSWORD = "myPassWord!";
    private static final String TEST_EXPECTED_SHA_256_SIGNATURE = "78:A6:27:31:03:D1:7C:39:A0:B6:12"
            + ":6E:22:6C:EC:70:E3:33:37:F4:BC:6A:38:06:74:01:B5:4A:33:E7:8E:AD";

    @Mock WifiContext mContext;
    @Mock WifiConfigManager mWifiConfigManager;
    @Mock WifiNative mWifiNative;
    @Mock FrameworkFacade mFrameworkFacade;
    @Mock WifiNotificationManager mWifiNotificationManager;
    @Mock WifiDialogManager mWifiDialogManager;
    @Mock Handler mHandler;
    @Mock InsecureEapNetworkHandler.InsecureEapNetworkHandlerCallbacks mCallbacks;

    @Mock(answer = Answers.RETURNS_DEEP_STUBS) private Notification.Builder mNotificationBuilder;
    @Mock private WifiDialogManager.DialogHandle mTofuAlertDialog;

    @Captor ArgumentCaptor<BroadcastReceiver> mBroadcastReceiverCaptor;

    MockResources mResources;
    InsecureEapNetworkHandler mInsecureEapNetworkHandler;

    /**
     * Sets up for unit test
     */
    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        mResources = new MockResources();
        when(mContext.getString(anyInt())).thenReturn("TestString");
        when(mContext.getString(anyInt(), any())).thenReturn("TestStringWithArgument");
        when(mContext.getText(anyInt())).thenReturn("TestStr");
        when(mContext.getString(eq(R.string.wifi_ca_cert_dialog_message_issuer_name_text),
                anyString()))
                .thenAnswer((Answer<String>) invocation ->
                        "Issuer Name:\n" + invocation.getArguments()[1] + "\n\n");
        when(mContext.getString(eq(R.string.wifi_ca_cert_dialog_message_server_name_text),
                anyString()))
                .thenAnswer((Answer<String>) invocation ->
                        "Server Name:\n" + invocation.getArguments()[1] + "\n\n");
        when(mContext.getString(eq(R.string.wifi_ca_cert_dialog_message_organization_text),
                anyString()))
                .thenAnswer((Answer<String>) invocation ->
                        "Organization:\n" + invocation.getArguments()[1] + "\n\n");
        when(mContext.getString(eq(R.string.wifi_ca_cert_dialog_message_contact_text),
                anyString()))
                .thenAnswer((Answer<String>) invocation ->
                        "Contact:\n" + invocation.getArguments()[1] + "\n\n");
        when(mContext.getString(eq(R.string.wifi_ca_cert_dialog_message_signature_name_text),
                anyString()))
                .thenAnswer((Answer<String>) invocation ->
                        "SHA-256 Fingerprint:\n" + invocation.getArguments()[1] + "\n\n");
        when(mContext.getWifiOverlayApkPkgName()).thenReturn("test.com.android.wifi.resources");
        when(mContext.getResources()).thenReturn(mResources);
        when(mWifiDialogManager.createSimpleDialogWithUrl(
                any(), any(), any(), anyInt(), anyInt(), any(), any(), any(), any(), any()))
                .thenReturn(mTofuAlertDialog);
        when(mWifiDialogManager.createSimpleDialog(
                any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(mTofuAlertDialog);

        when(mFrameworkFacade.makeNotificationBuilder(any(), any()))
                .thenReturn(mNotificationBuilder);
    }

    @After
    public void cleanUp() throws Exception {
        validateMockitoUsage();
    }

    /**
     * Verify Trust On First Use flow.
     * - This network is selected by a user.
     * - Accept the connection.
     */
    @Test
    public void verifyTrustOnFirstUseAcceptWhenConnectByUser() throws Exception {
        assumeTrue(SdkLevel.isAtLeastT());
        boolean isAtLeastT = true, isTrustOnFirstUseSupported = true, isUserSelected = true;
        boolean needUserApproval = true;

        WifiConfiguration config = prepareWifiConfiguration(isAtLeastT);
        setupTest(config, isAtLeastT, isTrustOnFirstUseSupported);
        verifyTrustOnFirstUseFlowWithDefaultCerts(config, ACTION_ACCEPT,
                isTrustOnFirstUseSupported, isUserSelected, needUserApproval);
    }

    /**
     * Verify Trust On First Use flow.
     * - This network is selected by a user.
     * - Reject the connection.
     */
    @Test
    public void verifyTrustOnFirstUseRejectWhenConnectByUser() throws Exception {
        assumeTrue(SdkLevel.isAtLeastT());
        boolean isAtLeastT = true, isTrustOnFirstUseSupported = true, isUserSelected = true;
        boolean needUserApproval = true;

        WifiConfiguration config = prepareWifiConfiguration(isAtLeastT);
        setupTest(config, isAtLeastT, isTrustOnFirstUseSupported);
        verifyTrustOnFirstUseFlowWithDefaultCerts(config, ACTION_REJECT,
                isTrustOnFirstUseSupported, isUserSelected, needUserApproval);
    }

    /**
     * Verify Trust On First Use flow.
     * - This network is auto-connected.
     * - Accept the connection.
     */
    @Test
    public void verifyTrustOnFirstUseAcceptWhenConnectByAutoConnect() throws Exception {
        assumeTrue(SdkLevel.isAtLeastT());
        boolean isAtLeastT = true, isTrustOnFirstUseSupported = true, isUserSelected = false;
        boolean needUserApproval = true;

        WifiConfiguration config = prepareWifiConfiguration(isAtLeastT);
        setupTest(config, isAtLeastT, isTrustOnFirstUseSupported);
        verifyTrustOnFirstUseFlowWithDefaultCerts(config, ACTION_ACCEPT,
                isTrustOnFirstUseSupported, isUserSelected, needUserApproval);
    }

    /**
     * Verify Trust On First Use flow.
     * - This network is auto-connected.
     * - Reject the connection.
     */
    @Test
    public void verifyTrustOnFirstUseRejectWhenConnectByAutoConnect() throws Exception {
        assumeTrue(SdkLevel.isAtLeastT());
        boolean isAtLeastT = true, isTrustOnFirstUseSupported = true, isUserSelected = false;
        boolean needUserApproval = true;

        WifiConfiguration config = prepareWifiConfiguration(isAtLeastT);
        setupTest(config, isAtLeastT, isTrustOnFirstUseSupported);
        verifyTrustOnFirstUseFlowWithDefaultCerts(config, ACTION_REJECT,
                isTrustOnFirstUseSupported, isUserSelected, needUserApproval);
    }

    /**
     * Verify Trust On First Use flow.
     * - This network is auto-connected.
     * - Tap the notification to show the dialog.
     */
    @Test
    public void verifyTrustOnFirstUseTapWhenConnectByAutoConnect() throws Exception {
        assumeTrue(SdkLevel.isAtLeastT());
        boolean isAtLeastT = true, isTrustOnFirstUseSupported = true, isUserSelected = false;
        boolean needUserApproval = true;

        WifiConfiguration config = prepareWifiConfiguration(isAtLeastT);
        setupTest(config, isAtLeastT, isTrustOnFirstUseSupported);
        verifyTrustOnFirstUseFlowWithDefaultCerts(config, ACTION_TAP,
                isTrustOnFirstUseSupported, isUserSelected, needUserApproval);
    }

    /**
     * Verify that it reports errors if there is no pending Root CA certifiate
     * with Trust On First Use support.
     */
    @Test
    public void verifyTrustOnFirstUseWhenTrustOnFirstUseNoPendingCert() throws Exception {
        assumeTrue(SdkLevel.isAtLeastT());
        boolean isAtLeastT = true, isTrustOnFirstUseSupported = true, isUserSelected = true;

        WifiConfiguration config = prepareWifiConfiguration(isAtLeastT);
        setupTest(config, isAtLeastT, isTrustOnFirstUseSupported);
        mInsecureEapNetworkHandler.startUserApprovalIfNecessary(isUserSelected);
        verify(mCallbacks).onError(eq(config.SSID));
        verify(mWifiConfigManager, atLeastOnce()).updateNetworkSelectionStatus(eq(config.networkId),
                eq(WifiConfiguration.NetworkSelectionStatus
                        .DISABLED_BY_WIFI_MANAGER));
    }

    /**
     * Verify that Trust On First Use is not supported on T.
     * It follows the same behavior on preT release.
     */
    @Test
    public void verifyTrustOnFirstUseWhenTrustOnFirstUseNotSupported() throws Exception {
        assumeTrue(SdkLevel.isAtLeastT());
        boolean isAtLeastT = true, isTrustOnFirstUseSupported = false, isUserSelected = true;

        WifiConfiguration config = prepareWifiConfiguration(isAtLeastT);
        setupTest(config, isAtLeastT, isTrustOnFirstUseSupported);
        mInsecureEapNetworkHandler.startUserApprovalIfNecessary(isUserSelected);
        verify(mCallbacks, never()).onError(any());
    }

    /**
     * Verify legacy insecure EAP network flow.
     * - This network is selected by a user.
     * - Accept the connection.
     */
    @Test
    public void verifyLegacyEapNetworkAcceptWhenConnectByUser() throws Exception {
        assumeFalse(SdkLevel.isAtLeastT());
        boolean isAtLeastT = false, isTrustOnFirstUseSupported = false, isUserSelected = true;
        boolean needUserApproval = true;

        WifiConfiguration config = prepareWifiConfiguration(isAtLeastT);
        setupTest(config, isAtLeastT, isTrustOnFirstUseSupported);
        verifyTrustOnFirstUseFlowWithDefaultCerts(config, ACTION_ACCEPT,
                isTrustOnFirstUseSupported, isUserSelected, needUserApproval);
    }

    /**
     * Verify legacy insecure EAP network flow.
     * - Trust On First Use is not supported.
     * - This network is selected by a user.
     * - Reject the connection.
     */
    @Test
    public void verifyLegacyEapNetworkRejectWhenConnectByUser() throws Exception {
        assumeFalse(SdkLevel.isAtLeastT());
        boolean isAtLeastT = false, isTrustOnFirstUseSupported = false, isUserSelected = true;
        boolean needUserApproval = true;

        WifiConfiguration config = prepareWifiConfiguration(isAtLeastT);
        setupTest(config, isAtLeastT, isTrustOnFirstUseSupported);
        verifyTrustOnFirstUseFlowWithDefaultCerts(config, ACTION_REJECT,
                isTrustOnFirstUseSupported, isUserSelected, needUserApproval);
    }

    /**
     * Verify legacy insecure EAP network flow.
     * - This network is auto-connected.
     * - Accept the connection.
     */
    @Test
    public void verifyLegacyEapNetworkAcceptWhenAutoConnect() throws Exception {
        assumeFalse(SdkLevel.isAtLeastT());
        boolean isAtLeastT = false, isTrustOnFirstUseSupported = false, isUserSelected = false;
        boolean needUserApproval = true;

        WifiConfiguration config = prepareWifiConfiguration(isAtLeastT);
        setupTest(config, isAtLeastT, isTrustOnFirstUseSupported);
        verifyTrustOnFirstUseFlowWithDefaultCerts(config, ACTION_ACCEPT,
                isTrustOnFirstUseSupported, isUserSelected, needUserApproval);
    }

    /**
     * Verify legacy insecure EAP network flow.
     * - Trust On First Use is not supported.
     * - This network is auto-connected.
     * - Reject the connection.
     */
    @Test
    public void verifyLegacyEapNetworkRejectWhenAutoConnect() throws Exception {
        assumeFalse(SdkLevel.isAtLeastT());
        boolean isAtLeastT = false, isTrustOnFirstUseSupported = false, isUserSelected = false;
        boolean needUserApproval = true;

        WifiConfiguration config = prepareWifiConfiguration(isAtLeastT);
        setupTest(config, isAtLeastT, isTrustOnFirstUseSupported);
        verifyTrustOnFirstUseFlowWithDefaultCerts(config, ACTION_REJECT,
                isTrustOnFirstUseSupported, isUserSelected, needUserApproval);
    }

    /**
     * Verify legacy insecure EAP network flow.
     * - This network is selected by a user.
     * - Tap the notification
     */
    @Test
    public void verifyLegacyEapNetworkOpenLinkWhenConnectByUser() throws Exception {
        assumeFalse(SdkLevel.isAtLeastT());
        boolean isAtLeastT = false, isTrustOnFirstUseSupported = false, isUserSelected = true;
        boolean needUserApproval = true;

        WifiConfiguration config = prepareWifiConfiguration(isAtLeastT);
        setupTest(config, isAtLeastT, isTrustOnFirstUseSupported);
        verifyTrustOnFirstUseFlowWithDefaultCerts(config, ACTION_TAP,
                isTrustOnFirstUseSupported, isUserSelected, needUserApproval);
    }

    private X509Certificate generateMockCert(String subject, String issuer, boolean isCa) {
        X509Certificate mockCert = mock(X509Certificate.class);
        X500Principal mockSubjectPrincipal = mock(X500Principal.class);
        when(mockCert.getSubjectX500Principal()).thenReturn(mockSubjectPrincipal);
        when(mockSubjectPrincipal.getName()).thenReturn("C=TW,ST=Taiwan,L=Taipei"
                + ",O=" + subject + " Organization"
                + ",CN=" + subject
                + ",1.2.840.113549.1.9.1=#1614" + String.valueOf(HexEncoding.encode(
                        (subject + "@email.com").getBytes(StandardCharsets.UTF_8))));
        try {
            when(mockCert.getEncoded()).thenReturn(new byte[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10});
        } catch (Exception e) {
            // nothing
        }
        X500Principal mockIssuerX500Principal = mock(X500Principal.class);
        when(mockCert.getIssuerX500Principal()).thenReturn(mockIssuerX500Principal);
        when(mockIssuerX500Principal.getName()).thenReturn("C=TW,ST=Taiwan,L=Taipei"
                + ",O=" + issuer + " Organization"
                + ",CN=" + issuer
                + ",1.2.840.113549.1.9.1=#1614" + String.valueOf(HexEncoding.encode(
                (issuer + "@email.com").getBytes(StandardCharsets.UTF_8))));

        when(mockCert.getSignature()).thenReturn(new byte[]{
                (byte) 0xde, (byte) 0xad, (byte) 0xbe, (byte) 0xef,
                (byte) 0x12, (byte) 0x34, (byte) 0x56, (byte) 0x78,
                (byte) 0x90, (byte) 0xab, (byte) 0xcd, (byte) 0xef});

        when(mockCert.getBasicConstraints()).thenReturn(isCa ? 99 : -1);
        return mockCert;
    }

    private WifiConfiguration prepareWifiConfiguration(boolean isAtLeastT) {
        WifiConfiguration config = spy(WifiConfigurationTestUtil.createEapNetwork(
                WifiEnterpriseConfig.Eap.TTLS, WifiEnterpriseConfig.Phase2.MSCHAPV2));
        config.networkId = FRAMEWORK_NETWORK_ID;
        config.SSID = TEST_SSID;
        if (isAtLeastT) {
            config.enterpriseConfig.enableTrustOnFirstUse(true);
        }
        config.enterpriseConfig.setCaPath("");
        config.enterpriseConfig.setDomainSuffixMatch("");
        config.enterpriseConfig.setIdentity(TEST_IDENTITY);
        config.enterpriseConfig.setPassword(TEST_PASSWORD);
        return config;
    }

    private void setupTest(WifiConfiguration config,
            boolean isAtLeastT, boolean isTrustOnFirstUseSupported) {
        setupTest(config, isAtLeastT, isTrustOnFirstUseSupported, false);
    }

    private void setupTest(WifiConfiguration config,
            boolean isAtLeastT, boolean isTrustOnFirstUseSupported,
            boolean isInsecureEnterpriseConfigurationAllowed) {
        mInsecureEapNetworkHandler = new InsecureEapNetworkHandler(
                mContext,
                mWifiConfigManager,
                mWifiNative,
                mFrameworkFacade,
                mWifiNotificationManager,
                mWifiDialogManager,
                isTrustOnFirstUseSupported,
                isInsecureEnterpriseConfigurationAllowed,
                mCallbacks,
                WIFI_IFACE_NAME,
                mHandler);

        if (isTrustOnFirstUseSupported
                && (config.enterpriseConfig.getEapMethod() == WifiEnterpriseConfig.Eap.TTLS
                || config.enterpriseConfig.getEapMethod() == WifiEnterpriseConfig.Eap.PEAP)
                && config.enterpriseConfig.getPhase2Method() != WifiEnterpriseConfig.Phase2.NONE) {
            // Verify that the configuration contains an identity
            assertEquals(TEST_IDENTITY, config.enterpriseConfig.getIdentity());
            assertTrue(TextUtils.isEmpty(config.enterpriseConfig.getAnonymousIdentity()));
            assertEquals(TEST_PASSWORD, config.enterpriseConfig.getPassword());
        }
        mInsecureEapNetworkHandler.prepareConnection(config);

        if (isTrustOnFirstUseSupported && config.enterpriseConfig.isTrustOnFirstUseEnabled()
                && (config.enterpriseConfig.getEapMethod() == WifiEnterpriseConfig.Eap.TTLS
                || config.enterpriseConfig.getEapMethod() == WifiEnterpriseConfig.Eap.PEAP)
                && config.enterpriseConfig.getPhase2Method() != WifiEnterpriseConfig.Phase2.NONE) {
            // Verify identities are cleared
            assertTrue(TextUtils.isEmpty(config.enterpriseConfig.getIdentity()));
            assertEquals(TOFU_ANONYMOUS_IDENTITY, config.enterpriseConfig.getAnonymousIdentity());
            assertTrue(TextUtils.isEmpty(config.enterpriseConfig.getPassword()));
        }

        if (isTrustOnFirstUseSupported && config.enterpriseConfig.isTrustOnFirstUseEnabled()) {
            verify(mContext, atLeastOnce()).registerReceiver(
                    mBroadcastReceiverCaptor.capture(),
                    argThat(f -> f.hasAction(InsecureEapNetworkHandler.ACTION_CERT_NOTIF_TAP)),
                    eq(null),
                    eq(mHandler));
        } else if ((isTrustOnFirstUseSupported
                && !config.enterpriseConfig.isTrustOnFirstUseEnabled()
                && isInsecureEnterpriseConfigurationAllowed)
                || !isTrustOnFirstUseSupported) {
            verify(mContext, atLeastOnce()).registerReceiver(
                    mBroadcastReceiverCaptor.capture(),
                    argThat(f -> f.hasAction(InsecureEapNetworkHandler.ACTION_CERT_NOTIF_ACCEPT)
                            && f.hasAction(InsecureEapNetworkHandler.ACTION_CERT_NOTIF_REJECT)),
                    eq(null),
                    eq(mHandler));
        }
    }

    /**
     * Verify Trust On First Use flow with a minimal cert chain
     * - This network is selected by a user.
     * - Accept the connection.
     */
    @Test
    public void verifyTrustOnFirstUseAcceptWhenConnectByUserWithMinimalChain() throws Exception {
        assumeTrue(SdkLevel.isAtLeastT());
        boolean isAtLeastT = true, isTrustOnFirstUseSupported = true, isUserSelected = true;
        boolean needUserApproval = true;

        WifiConfiguration config = prepareWifiConfiguration(isAtLeastT);
        setupTest(config, isAtLeastT, isTrustOnFirstUseSupported);

        X509Certificate mockCaCert = generateMockCert("ca", "ca", true);
        X509Certificate mockServerCert = generateMockCert("server", "ca", false);
        mInsecureEapNetworkHandler.addPendingCertificate(config.SSID, 1, mockCaCert);
        mInsecureEapNetworkHandler.addPendingCertificate(config.SSID, 0, mockServerCert);

        verifyTrustOnFirstUseFlow(config, ACTION_ACCEPT, isTrustOnFirstUseSupported,
                isUserSelected, needUserApproval, mockCaCert, mockServerCert);
    }

    /**
     * Verify Trust On First Use flow with a self-signed CA cert.
     * - This network is selected by a user.
     * - Accept the connection.
     */
    @Test
    public void verifyTrustOnFirstUseAcceptWhenConnectByUserWithSelfSignedCaCert()
            throws Exception {
        assumeTrue(SdkLevel.isAtLeastT());
        boolean isAtLeastT = true, isTrustOnFirstUseSupported = true, isUserSelected = true;
        boolean needUserApproval = true;

        WifiConfiguration config = prepareWifiConfiguration(isAtLeastT);
        setupTest(config, isAtLeastT, isTrustOnFirstUseSupported);

        X509Certificate mockSelfSignedCert = generateMockCert("self", "self", false);
        mInsecureEapNetworkHandler.addPendingCertificate(config.SSID, 0, mockSelfSignedCert);

        verifyTrustOnFirstUseFlow(config, ACTION_ACCEPT, isTrustOnFirstUseSupported,
                isUserSelected, needUserApproval, mockSelfSignedCert, mockSelfSignedCert);
    }

    /**
     * Verify that the connection should be terminated.
     * - TOFU is supported.
     * - Insecure EAP network is not allowed.
     * - No cert is received.
     */
    @Test
    public void verifyOnErrorWithoutCert() throws Exception {
        assumeTrue(SdkLevel.isAtLeastT());
        boolean isAtLeastT = true, isTrustOnFirstUseSupported = true, isUserSelected = true;

        WifiConfiguration config = prepareWifiConfiguration(isAtLeastT);
        setupTest(config, isAtLeastT, isTrustOnFirstUseSupported);

        mInsecureEapNetworkHandler.startUserApprovalIfNecessary(isUserSelected);
        verify(mCallbacks).onError(eq(config.SSID));
        verify(mWifiConfigManager, atLeastOnce()).updateNetworkSelectionStatus(eq(config.networkId),
                eq(WifiConfiguration.NetworkSelectionStatus
                        .DISABLED_BY_WIFI_MANAGER));
    }

    /**
     * Verify that the connection should be upgraded to TOFU.
     * - TOFU is supported.
     * - Insecure EAP network is not allowed.
     * - TOFU is not enabled
     */
    @Test
    public void verifyOnErrorWithTofuDisabledWhenInsecureEapNetworkIsNotAllowed()
            throws Exception {
        assumeTrue(SdkLevel.isAtLeastT());
        boolean isAtLeastT = true, isTrustOnFirstUseSupported = true, isUserSelected = true;

        WifiConfiguration config = prepareWifiConfiguration(isAtLeastT);
        config.enterpriseConfig.enableTrustOnFirstUse(false);
        setupTest(config, isAtLeastT, isTrustOnFirstUseSupported);

        mInsecureEapNetworkHandler.addPendingCertificate(config.SSID, 1,
                generateMockCert("ca", "ca", true));
        mInsecureEapNetworkHandler.addPendingCertificate(config.SSID, 0,
                generateMockCert("server", "ca", false));

        mInsecureEapNetworkHandler.startUserApprovalIfNecessary(isUserSelected);
        assertTrue(config.enterpriseConfig.isTrustOnFirstUseEnabled());
    }

    /**
     * Verify that no error occurs in insecure network handling flow.
     * - TOFU is supported.
     * - Insecure EAP network is allowed.
     * - TOFU is not enabled
     * - No user approval is needed.
     */
    @Test
    public void verifyNoErrorWithTofuDisabledWhenInsecureEapNetworkIsAllowed()
            throws Exception {
        assumeTrue(SdkLevel.isAtLeastT());
        boolean isAtLeastT = true, isTrustOnFirstUseSupported = true, isUserSelected = true;
        boolean isInsecureEnterpriseConfigurationAllowed = true;

        WifiConfiguration config = prepareWifiConfiguration(isAtLeastT);
        config.enterpriseConfig.enableTrustOnFirstUse(false);
        setupTest(config, isAtLeastT, isTrustOnFirstUseSupported,
                isInsecureEnterpriseConfigurationAllowed);

        mInsecureEapNetworkHandler.addPendingCertificate(config.SSID, 1,
                generateMockCert("ca", "ca", true));
        mInsecureEapNetworkHandler.addPendingCertificate(config.SSID, 0,
                generateMockCert("server", "ca", false));

        mInsecureEapNetworkHandler.startUserApprovalIfNecessary(isUserSelected);
        verify(mCallbacks, never()).onError(any());
    }

    /**
     * Verify that it reports errors if the cert chain is headless.
     */
    @Test
    public void verifyOnErrorWithHeadlessCertChain() throws Exception {
        assumeTrue(SdkLevel.isAtLeastT());
        boolean isAtLeastT = true, isTrustOnFirstUseSupported = true, isUserSelected = true;

        WifiConfiguration config = prepareWifiConfiguration(isAtLeastT);
        setupTest(config, isAtLeastT, isTrustOnFirstUseSupported);

        // Missing root CA cert.
        mInsecureEapNetworkHandler.addPendingCertificate(config.SSID, 0,
                generateMockCert("server", "ca", false));

        mInsecureEapNetworkHandler.startUserApprovalIfNecessary(isUserSelected);
        verify(mCallbacks).onError(eq(config.SSID));
        verify(mWifiConfigManager, atLeastOnce()).updateNetworkSelectionStatus(eq(config.networkId),
                eq(WifiConfiguration.NetworkSelectionStatus
                        .DISABLED_BY_WIFI_MANAGER));
    }

    /**
     * Verify that is reports errors if the server cert issuer does not match the parent subject.
     */
    @Test
    public void verifyOnErrorWithIncompleteChain() throws Exception {
        assumeTrue(SdkLevel.isAtLeastT());
        boolean isAtLeastT = true, isTrustOnFirstUseSupported = true, isUserSelected = true;

        WifiConfiguration config = prepareWifiConfiguration(isAtLeastT);
        setupTest(config, isAtLeastT, isTrustOnFirstUseSupported);

        X509Certificate mockCaCert = generateMockCert("ca", "ca", true);
        // Missing intermediate cert.
        X509Certificate mockServerCert = generateMockCert("server", "intermediate", false);
        mInsecureEapNetworkHandler.addPendingCertificate(config.SSID, 1, mockCaCert);
        mInsecureEapNetworkHandler.addPendingCertificate(config.SSID, 0, mockServerCert);

        mInsecureEapNetworkHandler.startUserApprovalIfNecessary(isUserSelected);
        verify(mCallbacks).onError(eq(config.SSID));
        verify(mWifiConfigManager, atLeastOnce()).updateNetworkSelectionStatus(eq(config.networkId),
                eq(WifiConfiguration.NetworkSelectionStatus
                        .DISABLED_BY_WIFI_MANAGER));
    }

    /**
     * Verify that setting pending certificate won't crash with no current configuration.
     */
    @Test
    public void verifySetPendingCertificateNoCrashWithNoConfig()
            throws Exception {
        assumeTrue(SdkLevel.isAtLeastT());
        mInsecureEapNetworkHandler = new InsecureEapNetworkHandler(
                mContext,
                mWifiConfigManager,
                mWifiNative,
                mFrameworkFacade,
                mWifiNotificationManager,
                mWifiDialogManager,
                true /* isTrustOnFirstUseSupported */,
                false /* isInsecureEnterpriseConfigurationAllowed */,
                mCallbacks,
                WIFI_IFACE_NAME,
                mHandler);
        X509Certificate mockSelfSignedCert = generateMockCert("self", "self", false);
        mInsecureEapNetworkHandler.addPendingCertificate("NotExist", 0, mockSelfSignedCert);
    }

    @Test
    public void testExistingCertChainIsClearedOnPreparingNewConnection() throws Exception {
        assumeTrue(SdkLevel.isAtLeastT());
        boolean isAtLeastT = true, isTrustOnFirstUseSupported = true, isUserSelected = true;

        WifiConfiguration config = prepareWifiConfiguration(isAtLeastT);
        setupTest(config, isAtLeastT, isTrustOnFirstUseSupported);

        // Missing root CA cert.
        mInsecureEapNetworkHandler.addPendingCertificate(config.SSID, 0,
                generateMockCert("server", "ca", false));

        // The wrong cert chain should be cleared after this call.
        mInsecureEapNetworkHandler.prepareConnection(config);

        X509Certificate mockSelfSignedCert = generateMockCert("self", "self", false);
        mInsecureEapNetworkHandler.addPendingCertificate(config.SSID, 0, mockSelfSignedCert);

        mInsecureEapNetworkHandler.startUserApprovalIfNecessary(isUserSelected);
        verify(mCallbacks, never()).onError(any());
    }

    @Test
    public void verifyUserApprovalIsNotNeededWithDifferentTargetConfig() throws Exception {
        assumeTrue(SdkLevel.isAtLeastT());
        boolean isAtLeastT = true, isTrustOnFirstUseSupported = true, isUserSelected = true;

        WifiConfiguration config = prepareWifiConfiguration(isAtLeastT);
        setupTest(config, isAtLeastT, isTrustOnFirstUseSupported);

        X509Certificate mockSelfSignedCert = generateMockCert("self", "self", false);
        mInsecureEapNetworkHandler.addPendingCertificate(config.SSID, 0, mockSelfSignedCert);

        // Pass another PSK config which is not the same as the current one.
        WifiConfiguration pskConfig = WifiConfigurationTestUtil.createPskNetwork();
        pskConfig.networkId = FRAMEWORK_NETWORK_ID + 2;
        mInsecureEapNetworkHandler.prepareConnection(pskConfig);
        mInsecureEapNetworkHandler.startUserApprovalIfNecessary(isUserSelected);
        verify(mCallbacks, never()).onError(any());

        // Pass another non-TOFU EAP config which is not the same as the current one.
        WifiConfiguration anotherEapConfig = spy(WifiConfigurationTestUtil.createEapNetwork(
                WifiEnterpriseConfig.Eap.SIM, WifiEnterpriseConfig.Phase2.NONE));
        anotherEapConfig.networkId = FRAMEWORK_NETWORK_ID + 1;
        mInsecureEapNetworkHandler.prepareConnection(anotherEapConfig);
        mInsecureEapNetworkHandler.startUserApprovalIfNecessary(isUserSelected);
        verify(mCallbacks, never()).onError(any());
    }

    private void verifyTrustOnFirstUseFlowWithDefaultCerts(WifiConfiguration config,
            int action, boolean isTrustOnFirstUseSupported, boolean isUserSelected,
            boolean needUserApproval) throws Exception {
        X509Certificate mockCaCert = generateMockCert("ca", "ca", true);
        X509Certificate mockServerCert = generateMockCert("server", "middle", false);
        if (isTrustOnFirstUseSupported) {
            mInsecureEapNetworkHandler.addPendingCertificate(config.SSID, 2, mockCaCert);
            mInsecureEapNetworkHandler.addPendingCertificate(config.SSID, 1,
                    generateMockCert("middle", "ca", false));
            mInsecureEapNetworkHandler.addPendingCertificate(config.SSID, 0, mockServerCert);
        }
        verifyTrustOnFirstUseFlow(config, action, isTrustOnFirstUseSupported,
                isUserSelected, needUserApproval, mockCaCert, mockServerCert);
    }

    private void verifyTrustOnFirstUseFlow(WifiConfiguration config,
            int action, boolean isTrustOnFirstUseSupported, boolean isUserSelected,
            boolean needUserApproval, X509Certificate expectedCaCert,
            X509Certificate expectedServerCert) throws Exception {
        mInsecureEapNetworkHandler.startUserApprovalIfNecessary(isUserSelected);

        ArgumentCaptor<String> dialogMessageCaptor = ArgumentCaptor.forClass(String.class);
        if (isUserSelected) {
            ArgumentCaptor<WifiDialogManager.SimpleDialogCallback> dialogCallbackCaptor =
                    ArgumentCaptor.forClass(WifiDialogManager.SimpleDialogCallback.class);
            verify(mWifiDialogManager).createSimpleDialogWithUrl(
                    any(), dialogMessageCaptor.capture(), any(), anyInt(), anyInt(), any(), any(),
                    any(), dialogCallbackCaptor.capture(), any());
            if (isTrustOnFirstUseSupported) {
                assertTofuDialogMessage(expectedCaCert, expectedServerCert,
                        dialogMessageCaptor.getValue());
            }
            if (action == ACTION_ACCEPT) {
                dialogCallbackCaptor.getValue().onPositiveButtonClicked();
            } else if (action == ACTION_REJECT) {
                dialogCallbackCaptor.getValue().onNegativeButtonClicked();
            }
        } else {
            verify(mFrameworkFacade, never()).makeAlertDialogBuilder(any());
            verify(mFrameworkFacade).makeNotificationBuilder(
                    eq(mContext), eq(WifiService.NOTIFICATION_NETWORK_ALERTS));

            // Trust On First Use notification has no accept and reject action buttons.
            // It only supports TAP and launch the dialog.
            if (isTrustOnFirstUseSupported) {
                Intent intent = new Intent(InsecureEapNetworkHandler.ACTION_CERT_NOTIF_TAP);
                intent.putExtra(InsecureEapNetworkHandler.EXTRA_PENDING_CERT_SSID, TEST_SSID);
                BroadcastReceiver br = mBroadcastReceiverCaptor.getValue();
                br.onReceive(mContext, intent);
                ArgumentCaptor<WifiDialogManager.SimpleDialogCallback> dialogCallbackCaptor =
                        ArgumentCaptor.forClass(WifiDialogManager.SimpleDialogCallback.class);
                verify(mWifiDialogManager).createSimpleDialogWithUrl(
                        any(), dialogMessageCaptor.capture(), any(), anyInt(), anyInt(), any(),
                        any(), any(), dialogCallbackCaptor.capture(), any());
                assertTofuDialogMessage(expectedCaCert, expectedServerCert,
                        dialogMessageCaptor.getValue());
                if (action == ACTION_ACCEPT) {
                    dialogCallbackCaptor.getValue().onPositiveButtonClicked();
                } else if (action == ACTION_REJECT) {
                    dialogCallbackCaptor.getValue().onNegativeButtonClicked();
                }
            } else {
                Intent intent = new Intent();
                if (action == ACTION_ACCEPT) {
                    intent = new Intent(InsecureEapNetworkHandler.ACTION_CERT_NOTIF_ACCEPT);
                } else if (action == ACTION_REJECT) {
                    intent = new Intent(InsecureEapNetworkHandler.ACTION_CERT_NOTIF_REJECT);
                } else if (action == ACTION_TAP) {
                    intent = new Intent(InsecureEapNetworkHandler.ACTION_CERT_NOTIF_TAP);
                }
                intent.putExtra(InsecureEapNetworkHandler.EXTRA_PENDING_CERT_SSID, TEST_SSID);
                BroadcastReceiver br = mBroadcastReceiverCaptor.getValue();
                br.onReceive(mContext, intent);
            }
        }

        if (action == ACTION_ACCEPT) {
            verify(mWifiConfigManager).updateNetworkSelectionStatus(eq(config.networkId),
                    eq(WifiConfiguration.NetworkSelectionStatus.DISABLED_NONE));
            if (isTrustOnFirstUseSupported) {
                verify(mWifiConfigManager).updateCaCertificate(
                        eq(config.networkId), eq(expectedCaCert), eq(expectedServerCert));
            } else {
                verify(mWifiConfigManager, never()).updateCaCertificate(
                        anyInt(), any(), any());
            }
            verify(mCallbacks).onAccept(eq(config.SSID));
        } else if (action == ACTION_REJECT) {
            verify(mWifiConfigManager, atLeastOnce())
                    .updateNetworkSelectionStatus(eq(config.networkId),
                            eq(WifiConfiguration.NetworkSelectionStatus
                            .DISABLED_BY_WIFI_MANAGER));
            verify(mCallbacks).onReject(eq(config.SSID), eq(!isTrustOnFirstUseSupported));
        } else if (action == ACTION_TAP) {
            verify(mWifiDialogManager).createSimpleDialogWithUrl(
                    any(), any(), any(), anyInt(), anyInt(), any(), any(), any(), any(), any());
            verify(mTofuAlertDialog).launchDialog();
        }
        verify(mCallbacks, never()).onError(any());
    }

    private void assertTofuDialogMessage(
            X509Certificate rootCaCert,
            X509Certificate serverCert,
            String message) {
        CertificateSubjectInfo serverCertSubjectInfo =
                CertificateSubjectInfo.parse(serverCert.getSubjectX500Principal().getName());
        CertificateSubjectInfo serverCertIssuerInfo =
                CertificateSubjectInfo.parse(serverCert.getIssuerX500Principal().getName());
        assertNotNull("Server cert subject info is null", serverCertSubjectInfo);
        assertNotNull("Server cert issuer info is null", serverCertIssuerInfo);

        assertTrue("TOFU dialog message does not contain server cert subject name ",
                message.contains(serverCertSubjectInfo.commonName));
        assertTrue("TOFU dialog message does not contain server cert issuer name",
                message.contains(serverCertIssuerInfo.commonName));
        if (!TextUtils.isEmpty(serverCertSubjectInfo.organization)) {
            assertTrue("TOFU dialog message does not contain server cert organization",
                    message.contains(serverCertSubjectInfo.organization));
        }
    }

    @Test
    public void testCleanUp() throws Exception {
        assumeTrue(SdkLevel.isAtLeastT());

        boolean isAtLeastT = true, isTrustOnFirstUseSupported = true;
        WifiConfiguration config = prepareWifiConfiguration(isAtLeastT);
        setupTest(config, isAtLeastT, isTrustOnFirstUseSupported);

        BroadcastReceiver br = mBroadcastReceiverCaptor.getValue();
        mInsecureEapNetworkHandler.cleanup();
        verify(mContext).unregisterReceiver(br);
    }

    /**
     * Verify the getDigest and fingerprint methods
     */
    @Test
    public void verifyGetDigest() throws Exception {
        X509Certificate mockServerCert = generateMockCert("server", "ca", false);
        assertEquals(mInsecureEapNetworkHandler.getDigest(mockServerCert, "SHA256"),
                TEST_EXPECTED_SHA_256_SIGNATURE);
    }
}
