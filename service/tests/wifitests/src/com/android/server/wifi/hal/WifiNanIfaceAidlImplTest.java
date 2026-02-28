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

package com.android.server.wifi.hal;

import static android.hardware.wifi.V1_0.NanCipherSuiteType.SHARED_KEY_128_MASK;
import static android.hardware.wifi.V1_0.NanCipherSuiteType.SHARED_KEY_256_MASK;
import static android.net.wifi.aware.AwarePairingConfig.PAIRING_BOOTSTRAPPING_OPPORTUNISTIC;
import static android.net.wifi.aware.Characteristics.WIFI_AWARE_CIPHER_SUITE_NCS_PK_PASN_128;
import static android.net.wifi.aware.Characteristics.WIFI_AWARE_CIPHER_SUITE_NCS_PK_PASN_256;
import static android.net.wifi.aware.Characteristics.WIFI_AWARE_CIPHER_SUITE_NCS_SK_128;
import static android.net.wifi.aware.Characteristics.WIFI_AWARE_CIPHER_SUITE_NCS_SK_256;

import static com.android.server.wifi.aware.WifiAwareStateManager.NAN_PAIRING_AKM_PASN;
import static com.android.server.wifi.aware.WifiAwareStateManager.NAN_PAIRING_AKM_SAE;

import static org.hamcrest.core.IsEqual.equalTo;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.hardware.wifi.IWifiNanIface;
import android.hardware.wifi.NanBandIndex;
import android.hardware.wifi.NanBootstrappingMethod;
import android.hardware.wifi.NanBootstrappingRequest;
import android.hardware.wifi.NanBootstrappingResponse;
import android.hardware.wifi.NanCipherSuiteType;
import android.hardware.wifi.NanConfigRequest;
import android.hardware.wifi.NanConfigRequestSupplemental;
import android.hardware.wifi.NanDataPathSecurityType;
import android.hardware.wifi.NanEnableRequest;
import android.hardware.wifi.NanPairingAkm;
import android.hardware.wifi.NanPairingRequest;
import android.hardware.wifi.NanPairingRequestType;
import android.hardware.wifi.NanPairingSecurityType;
import android.hardware.wifi.NanPeriodicRangingInterval;
import android.hardware.wifi.NanPublishRequest;
import android.hardware.wifi.NanRangingIndication;
import android.hardware.wifi.NanRespondToPairingIndicationRequest;
import android.hardware.wifi.NanSubscribeRequest;
import android.net.MacAddress;
import android.net.wifi.OuiKeyedData;
import android.net.wifi.aware.AwarePairingConfig;
import android.net.wifi.aware.ConfigRequest;
import android.net.wifi.aware.PublishConfig;
import android.net.wifi.aware.SubscribeConfig;
import android.net.wifi.aware.WifiAwareChannelInfo;
import android.net.wifi.aware.WifiAwareDataPathSecurityConfig;
import android.net.wifi.util.Environment;
import android.os.PersistableBundle;
import android.os.RemoteException;
import android.util.Pair;

import androidx.annotation.Nullable;

import com.android.modules.utils.build.SdkLevel;
import com.android.server.wifi.WifiBaseTest;
import com.android.server.wifi.aware.Capabilities;
import com.android.server.wifi.util.HalAidlUtil;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ErrorCollector;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class WifiNanIfaceAidlImplTest extends WifiBaseTest {
    private static final Capabilities TEST_CAPABILITIES = new Capabilities();
    private static final byte[] TEST_SDEA_HEADER = new byte[] {0x01, 0x02, 0x03};
    private WifiNanIfaceAidlImpl mDut;
    @Mock private IWifiNanIface mIWifiNanIfaceMock;
    private FrameworkCallback mFrameworkCallback;

    private static class FrameworkCallback implements WifiNanIface.Callback {
        private List<android.net.wifi.rtt.RangingResult> mRangingResults;

        @Override
        public void notifyCapabilitiesResponse(short id, Capabilities capabilities) {
        }
        @Override
        public void notifyEnableResponse(short id, int status) {
        }
        @Override
        public void notifyConfigResponse(short id, int status) {
        }
        @Override
        public void notifyDisableResponse(short id, int status) {
        }
        @Override
        public void notifyStartPublishResponse(short id, int status, byte publishId) {
        }
        @Override
        public void notifyStartSubscribeResponse(short id, int status, byte subscribeId) {
        }
        @Override
        public void notifyTransmitFollowupResponse(short id, int status) {
        }
        @Override
        public void notifyCreateDataInterfaceResponse(short id, int status) {
        }
        @Override
        public void notifyDeleteDataInterfaceResponse(short id, int status) {
        }
        @Override
        public void notifyInitiateDataPathResponse(short id, int status, int ndpInstanceId) {
        }
        @Override
        public void notifyRespondToDataPathIndicationResponse(short id, int status) {
        }
        @Override
        public void notifyTerminateDataPathResponse(short id, int status) {
        }
        @Override
        public void notifyInitiatePairingResponse(short id, int status, int pairingInstanceId) {
        }
        @Override
        public void notifyRespondToPairingIndicationResponse(short id, int status) {
        }
        @Override
        public void notifyInitiateBootstrappingResponse(short id, int status,
                int bootstrappingInstanceId) {
        }
        @Override
        public void notifyRespondToBootstrappingIndicationResponse(short id, int status) {
        }
        @Override
        public void notifySuspendResponse(short id, int status) {
        }
        @Override
        public void notifyResumeResponse(short id, int status) {
        }
        @Override
        public void notifyTerminatePairingResponse(short id, int status) {
        }
        @Override
        public void eventClusterEvent(int eventType, byte[] addr) {
        }
        @Override
        public void eventDisabled(int status) {
        }
        @Override
        public void eventPublishTerminated(byte sessionId, int status) {
        }
        @Override
        public void eventSubscribeTerminated(byte sessionId, int status) {
        }
        @Override
        public void eventMatch(byte discoverySessionId, int peerId, byte[] addr,
                byte[] serviceSpecificInfo, byte[] matchFilter, int rangingIndicationType,
                int rangingMeasurementInMm, byte[] scid, int peerCipherType, byte[] nonce,
                byte[] tag, AwarePairingConfig pairingConfig,
                @Nullable List<OuiKeyedData> vendorData) {
        }
        @Override
        public void eventMatchExpired(byte discoverySessionId, int peerId) {
        }
        @Override
        public void eventFollowupReceived(byte discoverySessionId, int peerId, byte[] addr,
                byte[] serviceSpecificInfo) {
        }
        @Override
        public void eventTransmitFollowup(short id, int status) {
        }
        @Override
        public void eventDataPathRequest(byte discoverySessionId, byte[] peerDiscMacAddr,
                int ndpInstanceId, byte[] appInfo, byte[] ndiInitMac) {
        }
        @Override
        public void eventDataPathConfirm(int status, int ndpInstanceId,
                boolean dataPathSetupSuccess, byte[] peerNdiMacAddr, byte[] appInfo,
                List<WifiAwareChannelInfo> channelInfos) {
        }
        @Override
        public void eventDataPathScheduleUpdate(byte[] peerDiscoveryAddress,
                ArrayList<Integer> ndpInstanceIds, List<WifiAwareChannelInfo> channelInfo) {
        }
        @Override
        public void eventDataPathTerminated(int ndpInstanceId) {
        }
        @Override
        public void eventPairingRequest(int discoverySessionId, int peerId,
                byte[] peerDiscMacAddr, int ndpInstanceId, int requestType, boolean enableCache,
                byte[] nonce, byte[] tag) {
        }
        @Override
        public void eventPairingConfirm(int pairingId, boolean accept, int reason, int requestType,
                boolean enableCache,
                com.android.server.wifi.aware.PairingConfigManager.PairingSecurityAssociationInfo
                npksa) {
        }
        @Override
        public void eventBootstrappingRequest(int discoverySessionId, int peerId,
                byte[] peerDiscMacAddr, int bootstrappingInstanceId, int method,
                byte[] serviceSpecificInfo) {
        }
        @Override
        public void eventBootstrappingConfirm(int sessionId, int pairingId, int responseCode,
		int reason, int comebackDelay, int bootstrappingMethod,
		byte[] cookie, byte[] peerMacAddr) {
        }
        @Override
        public void eventSuspensionModeChanged(boolean isSuspended) {
        }
        @Override
        public void notifyRangingResults(ArrayList<android.net.wifi.rtt.RangingResult>
                rangingResults, byte sessionId) {
            mRangingResults = rangingResults;
        }
    }

    @Rule public ErrorCollector collector = new ErrorCollector();

    @Before
    public void setup() throws Exception {
        MockitoAnnotations.initMocks(this);
        mFrameworkCallback = new FrameworkCallback();
        mDut = new WifiNanIfaceAidlImpl(mIWifiNanIfaceMock);
        TEST_CAPABILITIES.supportedDataPathCipherSuites = WIFI_AWARE_CIPHER_SUITE_NCS_SK_128
                | WIFI_AWARE_CIPHER_SUITE_NCS_SK_256;
    }

    private static OuiKeyedData generateFrameworkOuiKeyedData(int oui) {
        PersistableBundle bundle = new PersistableBundle();
        bundle.putString("stringKey", "stringValue");
        bundle.putInt("intKey", 789);
        return new OuiKeyedData.Builder(oui, bundle).build();
    }

    private static List<OuiKeyedData> generateFrameworkOuiKeyedDataList(int size) {
        List<OuiKeyedData> dataList = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            dataList.add(generateFrameworkOuiKeyedData(i + 1));
        }
        return dataList;
    }

    private static boolean compareHalOuiKeyedData(android.hardware.wifi.common.OuiKeyedData left,
            android.hardware.wifi.common.OuiKeyedData right) {
        return left.oui == right.oui && Objects.equals(left.vendorData, right.vendorData);
    }

    private static boolean compareHalOuiKeyedDataList(
            android.hardware.wifi.common.OuiKeyedData[] left,
            android.hardware.wifi.common.OuiKeyedData[] right) {
        // Assume both values are non-null
        if (left.length != right.length) return false;
        for (int i = 0; i < left.length; i++) {
            if (!compareHalOuiKeyedData(left[i], right[i])) {
                return false;
            }
        }
        return true;
    }

    @Test
    public void testDiscoveryRangingSettings() throws RemoteException {
        short tid = 250;
        byte pid = 34;
        int minDistanceMm = 100;
        int maxDistanceMm = 555;
        int periodicRangingInterval = SubscribeConfig.PERIODIC_RANGING_INTERVAL_512TU;
        short minDistanceCm = (short) (minDistanceMm / 10);
        short maxDistanceCm = (short) (maxDistanceMm / 10);
        List<OuiKeyedData> frameworkVendorData = generateFrameworkOuiKeyedDataList(5);
        android.hardware.wifi.common.OuiKeyedData[] halVendorData =
                HalAidlUtil.frameworkToHalOuiKeyedDataList(frameworkVendorData);

        ArgumentCaptor<NanPublishRequest> pubCaptor = ArgumentCaptor.forClass(
                NanPublishRequest.class);
        ArgumentCaptor<NanSubscribeRequest> subCaptor = ArgumentCaptor.forClass(
                NanSubscribeRequest.class);

        PublishConfig pubDefault = new PublishConfig.Builder().setServiceName("XXX").build();
        PublishConfig pubWithRanging = new PublishConfig.Builder().setServiceName(
                "XXX").setRangingEnabled(true).build();
        SubscribeConfig subDefault = new SubscribeConfig.Builder().setServiceName("XXX").build();
        SubscribeConfig subWithMin = new SubscribeConfig.Builder().setServiceName(
                "XXX").setEgressDistanceMm(minDistanceMm).build();
        SubscribeConfig subWithMax = new SubscribeConfig.Builder().setServiceName(
                "XXX").setIngressDistanceMm(maxDistanceMm).build();
        SubscribeConfig subWithMinMax = new SubscribeConfig.Builder().setServiceName(
                "XXX").setEgressDistanceMm(minDistanceMm).setIngressDistanceMm(
                maxDistanceMm).build();

        PublishConfig pubWithVendorData = null;
        SubscribeConfig subWithVendorData = null;
        if (SdkLevel.isAtLeastV()) {
            pubWithVendorData = new PublishConfig.Builder()
                    .setServiceName("XXX")
                    .setVendorData(frameworkVendorData)
                    .build();
            subWithVendorData = new SubscribeConfig.Builder()
                    .setServiceName("XXX")
                    .setVendorData(frameworkVendorData)
                    .build();
        }
        SubscribeConfig subWithPeriodicRanging = null;
        if (Environment.isSdkAtLeastB()) {
            subWithPeriodicRanging = new SubscribeConfig.Builder()
                   .setServiceName("XXX")
                   .setPeriodicRangingEnabled(true)
                   .setPeriodicRangingInterval(periodicRangingInterval)
                   .build();
        }

        int numPublishExpected = 2;
        int numSubscribeExpected = 4;

        assertTrue(mDut.publish(tid, pid, pubDefault, null, null));
        assertTrue(mDut.publish(tid, pid, pubWithRanging, null, null));
        assertTrue(mDut.subscribe(tid, pid, subDefault, null, null));
        assertTrue(mDut.subscribe(tid, pid, subWithMin, null, null));
        assertTrue(mDut.subscribe(tid, pid, subWithMax, null, null));
        assertTrue(mDut.subscribe(tid, pid, subWithMinMax, null, null));

        if (SdkLevel.isAtLeastV()) {
            assertTrue(mDut.publish(tid, pid, pubWithVendorData, null, null));
            assertTrue(mDut.subscribe(tid, pid, subWithVendorData, null, null));
            numPublishExpected += 1;
            numSubscribeExpected += 1;

        }

        if (Environment.isSdkAtLeastB()) {
            assertTrue(mDut.subscribe(tid, pid, subWithPeriodicRanging, null, null));
            numSubscribeExpected += 1;
        }

        verify(mIWifiNanIfaceMock, times(numPublishExpected))
                .startPublishRequest(eq((char) tid), pubCaptor.capture());
        verify(mIWifiNanIfaceMock, times(numSubscribeExpected))
                .startSubscribeRequest(eq((char) tid), subCaptor.capture());

        NanPublishRequest halPubReq;
        NanSubscribeRequest halSubReq;

        // pubDefault
        halPubReq = pubCaptor.getAllValues().get(0);
        collector.checkThat("pubDefault.baseConfigs.sessionId", pid,
                equalTo(halPubReq.baseConfigs.sessionId));
        collector.checkThat("pubDefault.baseConfigs.rangingRequired", false,
                equalTo(halPubReq.baseConfigs.rangingRequired));

        // pubWithRanging
        halPubReq = pubCaptor.getAllValues().get(1);
        collector.checkThat("pubWithRanging.baseConfigs.sessionId", pid,
                equalTo(halPubReq.baseConfigs.sessionId));
        collector.checkThat("pubWithRanging.baseConfigs.rangingRequired", true,
                equalTo(halPubReq.baseConfigs.rangingRequired));

        // subDefault
        halSubReq = subCaptor.getAllValues().get(0);
        collector.checkThat("subDefault.baseConfigs.sessionId", pid,
                equalTo(halSubReq.baseConfigs.sessionId));
        collector.checkThat("subDefault.baseConfigs.rangingRequired", false,
                equalTo(halSubReq.baseConfigs.rangingRequired));

        // subWithMin
        halSubReq = subCaptor.getAllValues().get(1);
        collector.checkThat("subWithMin.baseConfigs.sessionId", pid,
                equalTo(halSubReq.baseConfigs.sessionId));
        collector.checkThat("subWithMin.baseConfigs.rangingRequired", true,
                equalTo(halSubReq.baseConfigs.rangingRequired));
        collector.checkThat("subWithMin.baseConfigs.configRangingIndications",
                NanRangingIndication.EGRESS_MET_MASK,
                equalTo(halSubReq.baseConfigs.configRangingIndications));
        collector.checkThat("subWithMin.baseConfigs.distanceEgressCm", minDistanceCm,
                equalTo((short) halSubReq.baseConfigs.distanceEgressCm));

        // subWithMax
        halSubReq = subCaptor.getAllValues().get(2);
        collector.checkThat("subWithMax.baseConfigs.sessionId", pid,
                equalTo(halSubReq.baseConfigs.sessionId));
        collector.checkThat("subWithMax.baseConfigs.rangingRequired", true,
                equalTo(halSubReq.baseConfigs.rangingRequired));
        collector.checkThat("subWithMax.baseConfigs.configRangingIndications",
                NanRangingIndication.INGRESS_MET_MASK,
                equalTo(halSubReq.baseConfigs.configRangingIndications));
        collector.checkThat("subWithMin.baseConfigs.distanceIngressCm", maxDistanceCm,
                equalTo((short) halSubReq.baseConfigs.distanceIngressCm));
        // subWithMinMax
        halSubReq = subCaptor.getAllValues().get(3);
        collector.checkThat("subWithMinMax.baseConfigs.sessionId", pid,
                equalTo(halSubReq.baseConfigs.sessionId));
        collector.checkThat("subWithMinMax.baseConfigs.rangingRequired", true,
                equalTo(halSubReq.baseConfigs.rangingRequired));
        collector.checkThat("subWithMinMax.baseConfigs.configRangingIndications",
                NanRangingIndication.INGRESS_MET_MASK | NanRangingIndication.EGRESS_MET_MASK,
                equalTo(halSubReq.baseConfigs.configRangingIndications));
        collector.checkThat("subWithMin.baseConfigs.distanceEgressCm", minDistanceCm,
                equalTo((short) halSubReq.baseConfigs.distanceEgressCm));
        collector.checkThat("subWithMin.baseConfigs.distanceIngressCm", maxDistanceCm,
                equalTo((short) halSubReq.baseConfigs.distanceIngressCm));

        if (SdkLevel.isAtLeastV()) {
            halPubReq = pubCaptor.getAllValues().get(2);
            halSubReq = subCaptor.getAllValues().get(4);
            assertTrue(compareHalOuiKeyedDataList(halVendorData, halPubReq.vendorData));
            assertTrue(compareHalOuiKeyedDataList(halVendorData, halSubReq.vendorData));
        }

        // subPeriodicRanging
        if (Environment.isSdkAtLeastB()) {
            halSubReq = subCaptor.getAllValues().get(5);
            collector.checkThat("subDefault.baseConfigs.sessionId", pid,
                    equalTo(halSubReq.baseConfigs.sessionId));
            collector.checkThat("subDefault.baseConfigs.rangingIntervalMs", periodicRangingInterval,
                    equalTo(halSubReq.baseConfigs.rangingIntervalMs));
        }
    }

    @Test
    public void testPublishWithPairingSettings() throws RemoteException {
        assumeTrue(SdkLevel.isAtLeastU());
        short tid = 250;
        byte pid = 34;
        byte[] ssi = "some service specific info".getBytes();
        AwarePairingConfig awarePairingConfig = new AwarePairingConfig.Builder()
                .setPairingCacheEnabled(true)
                .setPairingSetupEnabled(true)
                .setPairingVerificationEnabled(true)
                .setBootstrappingMethods(PAIRING_BOOTSTRAPPING_OPPORTUNISTIC)
                .setSupportedCipherSuites(WIFI_AWARE_CIPHER_SUITE_NCS_PK_PASN_128)
                .build();
        PublishConfig config = new PublishConfig.Builder()
                .setServiceName("XXX")
                .setPairingConfig(awarePairingConfig)
                .setServiceSpecificInfo(ssi)
                .build();
        ArgumentCaptor<NanPublishRequest> pubCaptor = ArgumentCaptor.forClass(
                NanPublishRequest.class);
        assertTrue(mDut.publish(tid, pid, config, null, TEST_SDEA_HEADER));
        verify(mIWifiNanIfaceMock)
                .startPublishRequest(eq((char) tid), pubCaptor.capture());
        NanPublishRequest halPubReq = pubCaptor.getValue();
        assertEquals(NanDataPathSecurityType.PASSPHRASE,
                halPubReq.baseConfigs.securityConfig.securityType);
        assertEquals(NanCipherSuiteType.PUBLIC_KEY_PASN_128_MASK,
                halPubReq.baseConfigs.securityConfig.cipherType);
        assertTrue(halPubReq.pairingConfig.enablePairingSetup);
        assertTrue(halPubReq.pairingConfig.enablePairingCache);
        assertTrue(halPubReq.pairingConfig.enablePairingVerification);
        assertEquals(NanBootstrappingMethod.BOOTSTRAPPING_OPPORTUNISTIC_MASK,
                halPubReq.pairingConfig.supportedBootstrappingMethods);
        assertEquals(NanCipherSuiteType.PUBLIC_KEY_PASN_128_MASK,
                halPubReq.baseConfigs.securityConfig.cipherType);
        assertTrue(halPubReq.baseConfigs.securityConfig.requiresEnhancedFrameProtection);
        assertTrue(halPubReq.baseConfigs.securityConfig.supportBigtksa);
        assertTrue(halPubReq.baseConfigs.securityConfig.supportGtkAndIgtk);
        assertArrayEquals(ssi, Arrays.copyOfRange(halPubReq.baseConfigs.extendedServiceSpecificInfo,
                3, halPubReq.baseConfigs.extendedServiceSpecificInfo.length));
        assertArrayEquals(TEST_SDEA_HEADER, Arrays.copyOfRange(
                halPubReq.baseConfigs.extendedServiceSpecificInfo, 0, 3));
    }

    @Test
    public void testSubScribeWithPairingSettings() throws RemoteException {
        assumeTrue(SdkLevel.isAtLeastU());
        short tid = 250;
        byte pid = 34;
        byte[] ssi = "some service specific info".getBytes();
        AwarePairingConfig awarePairingConfig = new AwarePairingConfig.Builder()
                .setPairingCacheEnabled(true)
                .setPairingSetupEnabled(true)
                .setPairingVerificationEnabled(true)
                .setBootstrappingMethods(PAIRING_BOOTSTRAPPING_OPPORTUNISTIC)
                .setSupportedCipherSuites(WIFI_AWARE_CIPHER_SUITE_NCS_PK_PASN_128)
                .build();
        SubscribeConfig config = new SubscribeConfig.Builder()
                .setServiceName("XXX")
                .setPairingConfig(awarePairingConfig)
                .setServiceSpecificInfo(ssi)
                .build();
        ArgumentCaptor<NanSubscribeRequest> subCaptor = ArgumentCaptor.forClass(
                NanSubscribeRequest.class);
        assertTrue(mDut.subscribe(tid, pid, config, null, TEST_SDEA_HEADER));
        verify(mIWifiNanIfaceMock)
                .startSubscribeRequest(eq((char) tid), subCaptor.capture());
        NanSubscribeRequest halSubReq = subCaptor.getValue();
        assertEquals(NanCipherSuiteType.PUBLIC_KEY_PASN_128_MASK,
                halSubReq.baseConfigs.securityConfig.cipherType);
        assertTrue(halSubReq.pairingConfig.enablePairingSetup);
        assertTrue(halSubReq.pairingConfig.enablePairingCache);
        assertTrue(halSubReq.pairingConfig.enablePairingVerification);
        assertEquals(NanBootstrappingMethod.BOOTSTRAPPING_OPPORTUNISTIC_MASK,
                halSubReq.pairingConfig.supportedBootstrappingMethods);
        assertEquals(NanCipherSuiteType.PUBLIC_KEY_PASN_128_MASK,
                halSubReq.baseConfigs.securityConfig.cipherType);
        assertTrue(halSubReq.baseConfigs.securityConfig.requiresEnhancedFrameProtection);
        assertTrue(halSubReq.baseConfigs.securityConfig.supportBigtksa);
        assertTrue(halSubReq.baseConfigs.securityConfig.supportGtkAndIgtk);
        assertArrayEquals(ssi, Arrays.copyOfRange(halSubReq.baseConfigs.extendedServiceSpecificInfo,
                3, halSubReq.baseConfigs.extendedServiceSpecificInfo.length));
        assertArrayEquals(TEST_SDEA_HEADER, Arrays.copyOfRange(
                halSubReq.baseConfigs.extendedServiceSpecificInfo, 0, 3));
    }


    /**
     * Validate that the configuration parameters used to manage power state behavior are
     * using default values at the default power state.
     */
    @Test
    public void testEnableAndConfigPowerSettingsDefaults() throws RemoteException {
        byte default24 = 1;
        byte default5 = 1;

        Pair<NanConfigRequest, NanConfigRequestSupplemental> configs =
                validateEnableAndConfigure((short) 10, new ConfigRequest.Builder().build(), true,
                        true, true, false, default24, default5);

        collector.checkThat("validDiscoveryWindowIntervalVal-5", true,
                equalTo(configs.first.bandSpecificConfig[NanBandIndex.NAN_BAND_5GHZ]
                        .validDiscoveryWindowIntervalVal));
        collector.checkThat("validDiscoveryWindowIntervalVal-24", true,
                equalTo(configs.first.bandSpecificConfig[NanBandIndex.NAN_BAND_24GHZ]
                        .validDiscoveryWindowIntervalVal));
        collector.checkThat("discoveryWindowIntervalVal-5", default5,
                equalTo(configs.first.bandSpecificConfig[NanBandIndex.NAN_BAND_5GHZ]
                        .discoveryWindowIntervalVal));
        collector.checkThat("discoveryWindowIntervalVal-24", default24,
                equalTo(configs.first.bandSpecificConfig[NanBandIndex.NAN_BAND_24GHZ]
                        .discoveryWindowIntervalVal));

        collector.checkThat("discoveryBeaconIntervalMs", 0,
                equalTo(configs.second.discoveryBeaconIntervalMs));
        collector.checkThat("numberOfSpatialStreamsInDiscovery", 0,
                equalTo(configs.second.numberOfSpatialStreamsInDiscovery));
        collector.checkThat("enableDiscoveryWindowEarlyTermination", false,
                equalTo(configs.second.enableDiscoveryWindowEarlyTermination));
    }

    /**
     * Validate that the configuration parameters used to manage power state behavior are
     * using the specified non-interactive values when in that power state.
     */
    @Test
    public void testEnableAndConfigPowerSettingsNoneInteractive() throws RemoteException {
        byte interactive24 = 3;
        byte interactive5 = 2;

        Pair<NanConfigRequest, NanConfigRequestSupplemental> configs =
                validateEnableAndConfigure((short) 10, new ConfigRequest.Builder().build(), false,
                        false, false, false, interactive24, interactive5);

        collector.checkThat("validDiscoveryWindowIntervalVal-5", true,
                equalTo(configs.first.bandSpecificConfig[NanBandIndex.NAN_BAND_5GHZ]
                        .validDiscoveryWindowIntervalVal));
        collector.checkThat("discoveryWindowIntervalVal-5", interactive5,
                equalTo(configs.first.bandSpecificConfig[NanBandIndex.NAN_BAND_5GHZ]
                        .discoveryWindowIntervalVal));
        collector.checkThat("validDiscoveryWindowIntervalVal-24", true,
                equalTo(configs.first.bandSpecificConfig[NanBandIndex.NAN_BAND_24GHZ]
                        .validDiscoveryWindowIntervalVal));
        collector.checkThat("discoveryWindowIntervalVal-24", interactive24,
                equalTo(configs.first.bandSpecificConfig[NanBandIndex.NAN_BAND_24GHZ]
                        .discoveryWindowIntervalVal));

        // Note: still defaults (i.e. disabled) - will be tweaked for low power
        collector.checkThat("discoveryBeaconIntervalMs", 0,
                equalTo(configs.second.discoveryBeaconIntervalMs));
        collector.checkThat("numberOfSpatialStreamsInDiscovery", 0,
                equalTo(configs.second.numberOfSpatialStreamsInDiscovery));
        collector.checkThat("enableDiscoveryWindowEarlyTermination", false,
                equalTo(configs.second.enableDiscoveryWindowEarlyTermination));
    }

    /**
     * Validate that the configuration parameters used to manage power state behavior are
     * using the specified idle (doze) values when in that power state.
     */
    @Test
    public void testEnableAndConfigPowerSettingsIdle() throws RemoteException {
        byte idle24 = -1;
        byte idle5 = 2;

        Pair<NanConfigRequest, NanConfigRequestSupplemental> configs =
                validateEnableAndConfigure((short) 10, new ConfigRequest.Builder().build(), false,
                        true, false, true, idle24, idle5);

        collector.checkThat("validDiscoveryWindowIntervalVal-5", true,
                equalTo(configs.first.bandSpecificConfig[NanBandIndex.NAN_BAND_5GHZ]
                        .validDiscoveryWindowIntervalVal));
        collector.checkThat("discoveryWindowIntervalVal-5", idle5,
                equalTo(configs.first.bandSpecificConfig[NanBandIndex.NAN_BAND_5GHZ]
                        .discoveryWindowIntervalVal));
        collector.checkThat("validDiscoveryWindowIntervalVal-24", false,
                equalTo(configs.first.bandSpecificConfig[NanBandIndex.NAN_BAND_24GHZ]
                        .validDiscoveryWindowIntervalVal));

        // Note: still defaults (i.e. disabled) - will be tweaked for low power
        collector.checkThat("discoveryBeaconIntervalMs", 0,
                equalTo(configs.second.discoveryBeaconIntervalMs));
        collector.checkThat("numberOfSpatialStreamsInDiscovery", 0,
                equalTo(configs.second.numberOfSpatialStreamsInDiscovery));
        collector.checkThat("enableDiscoveryWindowEarlyTermination", false,
                equalTo(configs.second.enableDiscoveryWindowEarlyTermination));
    }

    /**
     * Validate the initiation of NDP for an open link.
     */
    @Test
    public void testInitiateDataPathOpen() throws Exception {
        validateInitiateDataPath(
                /* usePmk */ false,
                /* usePassphrase */ false,
                /* isOutOfBand */ false,
                /* publicCipherSuites */ 0,
                /* halCipherSuite */ 0,
                /* frameProtectionEnabled */ false);
    }

    /**
     * Validate the initiation of NDP for an open link with frame protection enabled.
     */
    @Test
    public void testInitiateDataPathOpenFrameProtectionEnabled() throws Exception {
        validateInitiateDataPath(
                /* usePmk */ false,
                /* usePassphrase */ false,
                /* isOutOfBand */ false,
                /* publicCipherSuites */ 0,
                /* halCipherSuite */ 0,
                /* frameProtectionEnabled */ true);
    }

    /**
     * Validate the initiation of NDP for a PMK protected link with in-band discovery.
     */
    @Test
    public void testInitiateDataPathPmkInBand() throws Exception {
        validateInitiateDataPath(
                /* usePmk */ true,
                /* usePassphrase */ false,
                /* isOutOfBand */ false,
                /* publicCipherSuites */ WIFI_AWARE_CIPHER_SUITE_NCS_SK_256,
                /* halCipherSuite */ SHARED_KEY_256_MASK,
                /* frameProtectionEnabled */ false);

    }

    /**
     * Validate the initiation of NDP for a Passphrase protected link with in-band discovery.
     */
    @Test
    public void testInitiateDataPathPassphraseInBand() throws Exception {
        validateInitiateDataPath(
                /* usePmk */ false,
                /* usePassphrase */ true,
                /* isOutOfBand */ false,
                /* publicCipherSuites */ WIFI_AWARE_CIPHER_SUITE_NCS_SK_128,
                /* halCipherSuite */ SHARED_KEY_128_MASK,
                /* frameProtectionEnabled */ false);
    }

    /**
     * Validate the initiation of NDP for a PMK protected link with out-of-band discovery.
     */
    @Test
    public void testInitiateDataPathPmkOutOfBand() throws Exception {
        validateInitiateDataPath(
                /* usePmk */ true,
                /* usePassphrase */ false,
                /* isOutOfBand */ true,
                /* supportedCipherSuites */ WIFI_AWARE_CIPHER_SUITE_NCS_SK_128,
                /* expectedCipherSuite */ SHARED_KEY_128_MASK,
                /* frameProtectionEnabled */ false);
    }

    /**
     * Validate the response to an NDP request for an open link.
     */
    @Test
    public void testRespondToDataPathRequestOpenInBand() throws Exception {
        validateRespondToDataPathRequest(
                /* usePmk */ false,
                /* usePassphrase */ false,
                /* accept */ true,
                /* isOutOfBand */ false,
                /* publicCipherSuites */  WIFI_AWARE_CIPHER_SUITE_NCS_SK_256,
                /* halCipherSuite */ SHARED_KEY_256_MASK,
                /* frameProtectionEnabled */ false);
    }

    /**
     * Validate the response to an NDP request for a PMK-protected link with in-band discovery.
     */
    @Test
    public void testRespondToDataPathRequestPmkInBand() throws Exception {
        validateRespondToDataPathRequest(
                /* usePmk */ true,
                /* usePassphrase */ false,
                /* accept */ true,
                /* isOutOfBand */ false,
                /* publicCipherSuites */ WIFI_AWARE_CIPHER_SUITE_NCS_SK_128,
                /* halCipherSuite */ SHARED_KEY_128_MASK,
                /* frameProtectionEnabled */ false);
    }

    /**
     * Validate the response to an NDP request for a Passphrase-protected link with in-band
     * discovery.
     */
    @Test
    public void testRespondToDataPathRequestPassphraseInBand() throws Exception {
        validateRespondToDataPathRequest(
                /* usePmk */ false,
                /* usePassphrase */ true,
                /* accept */ true,
                /* isOutOfBand */ false,
                /* publicCipherSuites */ WIFI_AWARE_CIPHER_SUITE_NCS_SK_256,
                /* halCipherSuite */ SHARED_KEY_256_MASK,
                /* frameProtectionEnabled */ false);
    }

    /**
     * Validate the response to an NDP request for a PMK-protected link with out-of-band discovery.
     */
    @Test
    public void testRespondToDataPathRequestPmkOutOfBand() throws Exception {
        validateRespondToDataPathRequest(
                /* usePmk */ true,
                /* usePassphrase */ false,
                /* accept */ true,
                /* isOutOfBand */ true,
                /* publicCipherSuites */ WIFI_AWARE_CIPHER_SUITE_NCS_SK_128,
                /* halCipherSuite */ SHARED_KEY_128_MASK,
                /* frameProtectionEnabled */ false);
    }

    /**
     * Validate the response to an NDP request - when request is rejected.
     */
    @Test
    public void testRespondToDataPathRequestReject() throws Exception {
        validateRespondToDataPathRequest(
                /* usePmk */ true,
                /* usePassphrase */ false,
                /* accept */ false,
                /* isOutOfBand */ true,
                /* publicCipherSuites */ WIFI_AWARE_CIPHER_SUITE_NCS_SK_128,
                /* halCipherSuite */ 0,
                /* frameProtectionEnabled */ false);
    }

    @Test
    public void testSuspendRequest() throws Exception {
        short tid = 250;
        byte pid = 34;
        assertTrue(mDut.suspend(tid, pid));
        verify(mIWifiNanIfaceMock).suspendRequest(eq((char) tid), eq(pid));
    }

    @Test
    public void testResumeRequest() throws Exception {
        short tid = 251;
        byte pid = 35;
        assertTrue(mDut.resume(tid, pid));
        verify(mIWifiNanIfaceMock).resumeRequest(eq((char) tid), eq(pid));
    }

    @Test
    public void testEndDataPath() throws Exception {
        short tid = 251;
        int ndpId = 35;
        assertTrue(mDut.endDataPath(tid, ndpId));
        verify(mIWifiNanIfaceMock).terminateDataPathRequest(eq((char) tid), eq(ndpId));
    }
    @Test
    public void testRespondToPairingRequest() throws Exception {
        short tid = 251;
        ArgumentCaptor<NanRespondToPairingIndicationRequest> reqCaptor = ArgumentCaptor.forClass(
                NanRespondToPairingIndicationRequest.class);
        assertTrue(mDut.respondToPairingRequest(tid, 1, true, null, true,
                NanPairingRequestType.NAN_PAIRING_SETUP, null, null , NAN_PAIRING_AKM_PASN,
                WIFI_AWARE_CIPHER_SUITE_NCS_PK_PASN_256));
        verify(mIWifiNanIfaceMock).respondToPairingIndicationRequest(eq((char) tid),
                reqCaptor.capture());
        NanRespondToPairingIndicationRequest request = reqCaptor.getValue();
        assertEquals(NanPairingRequestType.NAN_PAIRING_SETUP, request.requestType);
        assertTrue(request.acceptRequest);
        assertEquals(1, request.pairingInstanceId);
        assertEquals(NanPairingSecurityType.OPPORTUNISTIC, request.securityConfig.securityType);
        assertArrayEquals(new byte[32], request.securityConfig.pmk);
        assertArrayEquals(new byte[0], request.securityConfig.passphrase);
        assertTrue(request.enablePairingCache);
        assertArrayEquals(new byte[16], request.pairingIdentityKey);
        assertEquals(NanCipherSuiteType.PUBLIC_KEY_PASN_256_MASK,
                request.securityConfig.cipherType);
        assertEquals(NanPairingAkm.PASN, request.securityConfig.akm);
    }

    @Test
    public void testInitiateNanPairingRequest() throws Exception {
        short tid = 251;
        MacAddress peer = MacAddress.fromString("00:01:02:03:04:05");
        ArgumentCaptor<NanPairingRequest> reqCaptor = ArgumentCaptor.forClass(
                NanPairingRequest.class);
        assertTrue(mDut.initiateNanPairingRequest(tid, 1, peer, null, true,
                NanPairingRequestType.NAN_PAIRING_SETUP, null, "PASSWORD", NAN_PAIRING_AKM_SAE,
                WIFI_AWARE_CIPHER_SUITE_NCS_PK_PASN_128));
        verify(mIWifiNanIfaceMock).initiatePairingRequest(eq((char) tid),
                reqCaptor.capture());
        NanPairingRequest request = reqCaptor.getValue();
        assertEquals(NanPairingRequestType.NAN_PAIRING_SETUP, request.requestType);
        assertEquals(1, request.peerId);
        assertEquals(NanPairingSecurityType.PASSPHRASE, request.securityConfig.securityType);
        assertArrayEquals(new byte[32], request.securityConfig.pmk);
        assertArrayEquals("PASSWORD".getBytes(), request.securityConfig.passphrase);
        assertTrue(request.enablePairingCache);
        assertArrayEquals(new byte[16], request.pairingIdentityKey);
        assertEquals(NanCipherSuiteType.PUBLIC_KEY_PASN_128_MASK,
                request.securityConfig.cipherType);
        assertEquals(NanPairingAkm.SAE, request.securityConfig.akm);
    }

    @Test
    public void testEndPairing() throws Exception {
        short tid = 251;
        assertTrue(mDut.endPairing(tid, 1));
        verify(mIWifiNanIfaceMock).terminatePairingRequest(eq((char) tid), eq(1));
    }

    @Test
    public void testInitiateNanBootstrappingRequest() throws Exception {
        short tid = 251;
        byte pid = 34;
        byte[] ssi = "some service specific info".getBytes();
        MacAddress peer = MacAddress.fromString("00:01:02:03:04:05");
        ArgumentCaptor<NanBootstrappingRequest> reqCaptor = ArgumentCaptor.forClass(
                NanBootstrappingRequest.class);
        assertTrue(mDut.initiateNanBootstrappingRequest(tid, 1, peer, 2, null, pid, false, ssi,
                TEST_SDEA_HEADER));
        verify(mIWifiNanIfaceMock).initiateBootstrappingRequest(eq((char) tid),
                reqCaptor.capture());
        NanBootstrappingRequest request = reqCaptor.getValue();
        assertEquals(1, request.peerId);
        assertEquals(2, request.requestBootstrappingMethod);
        assertArrayEquals(peer.toByteArray(), request.peerDiscMacAddr);
        assertArrayEquals(new byte[0], request.cookie);
        assertEquals(pid, request.discoverySessionId);
        assertArrayEquals(ssi, Arrays.copyOfRange(request.serviceSpecificInfo, 3,
            request.serviceSpecificInfo.length));
        assertArrayEquals(TEST_SDEA_HEADER, Arrays.copyOfRange(request.serviceSpecificInfo, 0, 3));
    }

    @Test
    public void testRespondToNanBootstrappingRequest() throws Exception {
        short tid = 251;
        byte pid = 34;
        ArgumentCaptor<NanBootstrappingResponse> reqCaptor = ArgumentCaptor.forClass(
                NanBootstrappingResponse.class);
        assertTrue(mDut.respondToNanBootstrappingRequest(tid, 1, true, pid, 2));
        verify(mIWifiNanIfaceMock).respondToBootstrappingIndicationRequest(eq((char) tid),
                reqCaptor.capture());
        NanBootstrappingResponse request = reqCaptor.getValue();
        assertEquals(1, request.bootstrappingInstanceId);
        assertTrue(request.acceptRequest);
        assertEquals(pid, request.discoverySessionId);
    }

    @Test
    public void testNotifyCapabilitiesResponse() throws RemoteException {

        // 1. mock the callback
        WifiNanIface.Callback callbackMock = mock(WifiNanIface.Callback.class);
        mDut.registerFrameworkCallback(callbackMock);
        ArgumentCaptor<android.hardware.wifi.IWifiNanIfaceEventCallback> halCallbackCaptor =
                ArgumentCaptor.forClass(
                        android.hardware.wifi.IWifiNanIfaceEventCallback.class);
        verify(mIWifiNanIfaceMock).registerEventCallback(halCallbackCaptor.capture());
        android.hardware.wifi.IWifiNanIfaceEventCallback halCallback =
                halCallbackCaptor.getValue();
        // 2. create NanCapabilities
        android.hardware.wifi.NanCapabilities capabilitiesIn =
                new android.hardware.wifi.NanCapabilities();
        capabilitiesIn.supportedPeriodicRangingIntervals =
                NanPeriodicRangingInterval.INTERVAL_128TU
                        | NanPeriodicRangingInterval.INTERVAL_512TU;
        android.hardware.wifi.NanStatus status =
                new android.hardware.wifi.NanStatus();
        status.status = android.hardware.wifi.NanStatusCode.SUCCESS;
        status.description = "Success";
        // 3. call notifyCapabilitiesResponse
        halCallback.notifyCapabilitiesResponse((char) 0, status, capabilitiesIn);
        // 4. verify the onCapabilitiesUpdate is called with the correct capabilities
        ArgumentCaptor<Capabilities> capabilitiesOutCaptor =
                ArgumentCaptor.forClass(Capabilities.class);
        verify(callbackMock).notifyCapabilitiesResponse(eq((short) 0),
                capabilitiesOutCaptor.capture());
        assertEquals(
                android.net.wifi.aware.Characteristics.SUPPORTED_PERIODIC_RANGING_INTERVAL_128TU
                        | android.net.wifi.aware.Characteristics
                        .SUPPORTED_PERIODIC_RANGING_INTERVAL_512TU,
                capabilitiesOutCaptor.getValue().supportedPeriodicRangingIntervals);
    }

    // utilities

    private Pair<NanConfigRequest, NanConfigRequestSupplemental> validateEnableAndConfigure(
            short transactionId, ConfigRequest configRequest, boolean notifyIdentityChange,
            boolean initialConfiguration, boolean isInteractive, boolean isIdle,
            int discoveryWindow24Ghz, int discoveryWindow5Ghz) throws RemoteException {
        assertTrue(mDut.enableAndConfigure(transactionId, configRequest, notifyIdentityChange,
                initialConfiguration, false, false, 2437, -1 /* clusterId */,
                1800 /* PARAM_MAC_RANDOM_INTERVAL_SEC_DEFAULT */,
                getPowerParams(isInteractive, isIdle, discoveryWindow24Ghz, discoveryWindow5Ghz)));

        ArgumentCaptor<NanEnableRequest> enableReqCaptor = ArgumentCaptor.forClass(
                NanEnableRequest.class);
        ArgumentCaptor<NanConfigRequest> configReqCaptor = ArgumentCaptor.forClass(
                NanConfigRequest.class);
        ArgumentCaptor<NanConfigRequestSupplemental> configSuppCaptor = ArgumentCaptor.forClass(
                NanConfigRequestSupplemental.class);
        NanConfigRequest config;
        NanConfigRequestSupplemental configSupp = null;

        if (initialConfiguration) {
            verify(mIWifiNanIfaceMock).enableRequest(eq((char) transactionId),
                    enableReqCaptor.capture(), configSuppCaptor.capture());
            configSupp = configSuppCaptor.getValue();
            config = enableReqCaptor.getValue().configParams;
        } else {
            verify(mIWifiNanIfaceMock).configRequest(eq((char) transactionId),
                    configReqCaptor.capture(), configSuppCaptor.capture());
            configSupp = configSuppCaptor.getValue();
            config = configReqCaptor.getValue();
        }

        collector.checkThat("disableDiscoveryAddressChangeIndication", !notifyIdentityChange,
                equalTo(config.disableDiscoveryAddressChangeIndication));
        collector.checkThat("disableStartedClusterIndication", !notifyIdentityChange,
                equalTo(config.disableStartedClusterIndication));
        collector.checkThat("disableJoinedClusterIndication", !notifyIdentityChange,
                equalTo(config.disableJoinedClusterIndication));

        return new Pair<>(config, configSupp);
    }

    private WifiNanIface.PowerParameters getPowerParams(boolean isInteractive, boolean isIdle,
            int discoveryWindow24Ghz, int discoveryWindow5Ghz) {
        WifiNanIface.PowerParameters params = new WifiNanIface.PowerParameters();
        params.discoveryBeaconIntervalMs = 0;   // PARAM_DISCOVERY_BEACON_INTERVAL_MS_DEFAULT
        params.enableDiscoveryWindowEarlyTermination = false;  // PARAM_ENABLE_DW_EARLY_TERM_DEFAULT
        params.numberOfSpatialStreamsInDiscovery = 0;   // PARAM_NUM_SS_IN_DISCOVERY_DEFAULT
        if (isInteractive && !isIdle) {
            params.discoveryWindow24Ghz = 1;    // PARAM_DW_24GHZ_DEFAULT
            params.discoveryWindow5Ghz = 1;     // PARAM_DW_5GHZ_DEFAULT
            params.discoveryWindow6Ghz = 1;     // PARAM_DW_6GHZ_DEFAULT
        } else {
            params.discoveryWindow24Ghz = discoveryWindow24Ghz;
            params.discoveryWindow5Ghz = discoveryWindow5Ghz;
            params.discoveryWindow6Ghz = 0;
        }
        return params;
    }

    private void validateInitiateDataPath(boolean usePmk, boolean usePassphrase,
            boolean isOutOfBand, int publicCipherSuites, int halCipherSuite,
        boolean frameProtectionEnabled)
            throws Exception {
        short tid = 44;
        int peerId = 555;
        byte pubSubId = 1;
        int channelRequestType =
                android.hardware.wifi.NanDataPathChannelCfg.CHANNEL_NOT_REQUESTED;
        int channel = 2146;
        MacAddress peer = MacAddress.fromString("00:01:02:03:04:05");
        String interfaceName = "aware_data5";
        final byte[] pmk = "01234567890123456789012345678901".getBytes();
        String passphrase = "blahblah";
        final byte[] appInfo = "Out-of-band info".getBytes();

        ArgumentCaptor<android.hardware.wifi.NanInitiateDataPathRequest> captor =
                ArgumentCaptor.forClass(
                        android.hardware.wifi.NanInitiateDataPathRequest.class);
        WifiAwareDataPathSecurityConfig securityConfig = null;
        if (usePassphrase) {
            securityConfig = new WifiAwareDataPathSecurityConfig
                    .Builder(publicCipherSuites)
                    .setPskPassphrase(passphrase)
                    .build();
        } else if (usePmk) {
            securityConfig = new WifiAwareDataPathSecurityConfig
                    .Builder(publicCipherSuites)
                    .setPmk(pmk)
                    .build();
        }

        assertTrue(mDut.initiateDataPath(tid, peerId, channelRequestType, channel, peer,
                interfaceName, isOutOfBand, appInfo, TEST_CAPABILITIES, securityConfig, pubSubId,
                       frameProtectionEnabled));

        verify(mIWifiNanIfaceMock).initiateDataPathRequest(eq((char) tid), captor.capture());

        android.hardware.wifi.NanInitiateDataPathRequest nidpr = captor.getValue();
        collector.checkThat("peerId", peerId, equalTo(nidpr.peerId));
        collector.checkThat("peerDiscMacAddr", peer.toByteArray(),
                equalTo(nidpr.peerDiscMacAddr));
        collector.checkThat("channelRequestType", channelRequestType,
                equalTo(nidpr.channelRequestType));
        collector.checkThat("channel", channel, equalTo(nidpr.channel));
        collector.checkThat("ifaceName", interfaceName, equalTo(nidpr.ifaceName));
        collector.checkThat("pubSubId", pubSubId, equalTo(nidpr.discoverySessionId));

        if (usePmk) {
            collector.checkThat("securityConfig.securityType",
                    NanDataPathSecurityType.PMK,
                    equalTo(nidpr.securityConfig.securityType));
            collector.checkThat("securityConfig.cipherType", halCipherSuite,
                    equalTo(nidpr.securityConfig.cipherType));
            collector.checkThat("securityConfig.pmk", pmk, equalTo(nidpr.securityConfig.pmk));
            collector.checkThat("securityConfig.passphrase", new byte[0],
                    equalTo(nidpr.securityConfig.passphrase));
        }

        if (usePassphrase) {
            collector.checkThat("securityConfig.securityType",
                    NanDataPathSecurityType.PASSPHRASE,
                    equalTo(nidpr.securityConfig.securityType));
            collector.checkThat("securityConfig.cipherType", halCipherSuite,
                    equalTo(nidpr.securityConfig.cipherType));
            collector.checkThat("securityConfig.passphrase", passphrase.getBytes(),
                    equalTo(nidpr.securityConfig.passphrase));
        }

        collector.checkThat("appInfo", appInfo, equalTo(nidpr.appInfo));

        if ((usePmk || usePassphrase) && isOutOfBand) {
            collector.checkThat("serviceNameOutOfBand",
                    WifiNanIface.SERVICE_NAME_FOR_OOB_DATA_PATH.getBytes(),
                    equalTo(nidpr.serviceNameOutOfBand));
        } else {
            collector.checkThat("serviceNameOutOfBand", new byte[0],
                    equalTo(nidpr.serviceNameOutOfBand));
        }
        collector.checkThat("frameProtectionEnabled", frameProtectionEnabled,
                equalTo(nidpr.securityConfig.requiresEnhancedFrameProtection));
        collector.checkThat("frameProtectionEnabled", frameProtectionEnabled,
                equalTo(nidpr.securityConfig.supportBigtksa));
        collector.checkThat("frameProtectionEnabled", frameProtectionEnabled,
                equalTo(nidpr.securityConfig.supportGtkAndIgtk));

    }

    private void validateRespondToDataPathRequest(boolean usePmk, boolean usePassphrase,
            boolean accept, boolean isOutOfBand, int publicCipherSuites, int halCipherSuite,
        boolean frameProtectionEnabled)
            throws Exception {
        short tid = 33;
        int ndpId = 44;
        byte pubSubId = 1;
        String interfaceName = "aware_whatever22";
        final byte[] pmk = "01234567890123456789012345678901".getBytes();
        String passphrase = "blahblah";
        final byte[] appInfo = "Out-of-band info".getBytes();

        ArgumentCaptor<android.hardware.wifi.NanRespondToDataPathIndicationRequest> captor =
                ArgumentCaptor.forClass(
                        android.hardware.wifi.NanRespondToDataPathIndicationRequest.class);
        WifiAwareDataPathSecurityConfig securityConfig = null;
        if (usePassphrase) {
            securityConfig = new WifiAwareDataPathSecurityConfig
                    .Builder(publicCipherSuites)
                    .setPskPassphrase(passphrase)
                    .build();
        } else if (usePmk) {
            securityConfig = new WifiAwareDataPathSecurityConfig
                    .Builder(publicCipherSuites)
                    .setPmk(pmk)
                    .build();
        }

        assertTrue(mDut.respondToDataPathRequest(tid, accept, ndpId, interfaceName,
                appInfo, isOutOfBand, TEST_CAPABILITIES, securityConfig, pubSubId,
                frameProtectionEnabled, null, null));

        verify(mIWifiNanIfaceMock)
                .respondToDataPathIndicationRequest(eq((char) tid), captor.capture());

        android.hardware.wifi.NanRespondToDataPathIndicationRequest nrtdpir =
                captor.getValue();
        collector.checkThat("acceptRequest", accept, equalTo(nrtdpir.acceptRequest));
        collector.checkThat("ndpInstanceId", ndpId, equalTo(nrtdpir.ndpInstanceId));
        collector.checkThat("ifaceName", interfaceName, equalTo(nrtdpir.ifaceName));
        collector.checkThat("pubSubId", pubSubId, equalTo(nrtdpir.discoverySessionId));
        collector.checkThat("frameProtectionEnabled", frameProtectionEnabled,
                equalTo(nrtdpir.securityConfig.requiresEnhancedFrameProtection));
        collector.checkThat("frameProtectionEnabled", frameProtectionEnabled,
                equalTo(nrtdpir.securityConfig.supportBigtksa));
        collector.checkThat("frameProtectionEnabled", frameProtectionEnabled,
                equalTo(nrtdpir.securityConfig.supportGtkAndIgtk));

        if (accept) {
            if (usePmk) {
                collector.checkThat("securityConfig.securityType",
                        NanDataPathSecurityType.PMK,
                        equalTo(nrtdpir.securityConfig.securityType));
                collector.checkThat("securityConfig.cipherType", halCipherSuite,
                        equalTo(nrtdpir.securityConfig.cipherType));
                collector.checkThat("securityConfig.pmk", pmk, equalTo(nrtdpir.securityConfig.pmk));
                collector.checkThat("securityConfig.passphrase", new byte[0],
                        equalTo(nrtdpir.securityConfig.passphrase));
            }

            if (usePassphrase) {
                collector.checkThat("securityConfig.securityType",
                        NanDataPathSecurityType.PASSPHRASE,
                        equalTo(nrtdpir.securityConfig.securityType));
                collector.checkThat("securityConfig.cipherType", halCipherSuite,
                        equalTo(nrtdpir.securityConfig.cipherType));
                collector.checkThat("securityConfig.passphrase", passphrase.getBytes(),
                        equalTo(nrtdpir.securityConfig.passphrase));
            }

            collector.checkThat("appInfo", appInfo, equalTo((nrtdpir.appInfo)));

            if ((usePmk || usePassphrase) && isOutOfBand) {
                collector.checkThat("serviceNameOutOfBand",
                        WifiNanIface.SERVICE_NAME_FOR_OOB_DATA_PATH.getBytes(),
                        equalTo((nrtdpir.serviceNameOutOfBand)));
            } else {
                collector.checkThat("serviceNameOutOfBand", new byte[0],
                        equalTo(nrtdpir.serviceNameOutOfBand));
            }
        }
    }

    @Test
    public void testRangingResultsBusyTryLater() throws Exception {
        mDut.registerFrameworkCallback(mFrameworkCallback);
        WifiNanIfaceCallbackAidlImpl halCallback = new WifiNanIfaceCallbackAidlImpl(mDut);
        android.hardware.wifi.RttResult[] results =
                new android.hardware.wifi.RttResult[1];
        android.hardware.wifi.RttResult res = new android.hardware.wifi.RttResult();
        res.lci = new android.hardware.wifi.WifiInformationElement();
        res.lcr = new android.hardware.wifi.WifiInformationElement();
        res.addr = MacAddress.byteAddrFromStringAddr("05:06:07:08:09:0A");
        res.status = android.hardware.wifi.RttStatus.FAIL_BUSY_TRY_LATER;
        res.retryAfterDuration = 40; // 5120 ms (40 * 128)
        results[0] = res;

        halCallback.notifyRangingResults(results, (byte) 0);

        // verify contents of the framework results
        List<android.net.wifi.rtt.RangingResult> rttR = mFrameworkCallback.mRangingResults;

        collector.checkThat("number of entries", rttR.size(), equalTo(1));

        android.net.wifi.rtt.RangingResult rttResult = rttR.get(0);
        collector.checkThat("status", rttResult.getStatus(),
                equalTo(android.net.wifi.rtt.RangingResult.STATUS_BUSY_TRY_LATER));
        collector.checkThat("mac", rttResult.getMacAddress().toByteArray(),
                equalTo(MacAddress.fromString("05:06:07:08:09:0A").toByteArray()));
        collector.checkThat("retryAfterDuration", rttResult.getRetryAfterDurationMillis(),
                equalTo(40 * 128));
    }
}
