/*
 * Copyright (C) 2026 The Android Open Source Project
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.server.wifi.nl80211.Nl80211Native;
import com.android.wifi.flags.Flags;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.MockitoSession;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Unit tests for {@link com.android.server.wifi.WifiPowerStatsManager}.
 */
public class WifiPowerStatsManagerTest {
    private static final String TEST_INTERFACE_NAME = "wlan0";
    private static final int TLV_HEADER_SIZE = 4;

    @Mock
    private Nl80211Native mNl80211Native;
    @Mock
    private ActiveModeWarden mActiveModeWarden;
    @Mock
    private ConcreteClientModeManager mClientModeManager;

    private WifiPowerStatsManager mWifiPowerStatsManager;
    private MockitoSession mSession;

    @After
    public void tearDown() {
        if (mSession != null) {
            mSession.finishMocking();
        }
    }

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mSession = ExtendedMockito.mockitoSession()
                .mockStatic(Flags.class, withSettings().lenient())
                .startMocking();
        when(Flags.powerStatsApi()).thenReturn(true);
        when(mActiveModeWarden.getPrimaryClientModeManager()).thenReturn(mClientModeManager);
        when(mClientModeManager.getInterfaceName()).thenReturn(TEST_INTERFACE_NAME);
        mWifiPowerStatsManager = new WifiPowerStatsManager(mNl80211Native, mActiveModeWarden);
    }

    private ByteBuffer createTestPowerStatsBuffer() {
        // Create a buffer with realistic test data
        ByteBuffer buffer = ByteBuffer.allocate(512).order(ByteOrder.LITTLE_ENDIAN);

        // NUM_WIFI_CORE
        buffer.putShort((short) (TLV_HEADER_SIZE + Integer.BYTES));
        buffer.putShort((short) 1);
        buffer.putInt(1);

        // RADIO_INFO
        buffer.putShort((short) (TLV_HEADER_SIZE + 24));
        buffer.putShort((short) 2);
        buffer.putInt(0); // coreIndex
        buffer.putInt(1000); // radioOnTimeMs
        buffer.putInt(200); // txTimeMs
        buffer.putInt(300); // rxTimeMs
        buffer.putInt(1); // rxListeningLevelsNum
        buffer.putInt(100); // rxListeningTimeMsPerLevels

        // TX_RATE_INFO
        buffer.putShort((short) (TLV_HEADER_SIZE + 28));
        buffer.putShort((short) 3);
        buffer.putInt(0); // coreIndex
        buffer.putInt(1); // numRates
        buffer.putInt(5); // rateIndex
        buffer.putInt(1); // band
        buffer.putInt(2); // bw
        buffer.putInt(3); // nss
        buffer.putInt(50); // count

        // RX_RATE_INFO
        buffer.putShort((short) (TLV_HEADER_SIZE + 28));
        buffer.putShort((short) 4);
        buffer.putInt(0); // coreIndex
        buffer.putInt(1); // numRates
        buffer.putInt(5); // rateIndex
        buffer.putInt(1); // band
        buffer.putInt(2); // bw
        buffer.putInt(3); // nss
        buffer.putInt(50); // count

        // CHIP_POWER_STATS
        buffer.putShort((short) (TLV_HEADER_SIZE + 12));
        buffer.putShort((short) 5);
        buffer.putInt(5000); // wlanPwrOnTimeMs
        buffer.putInt(1); // sleepLevelsNum
        buffer.putInt(1000); // sleepTimeMsPerLevels

        buffer.flip();
        return buffer;
    }

    @Test
    public void testGetWlanPwrStats_NativeFailure() {
        when(mNl80211Native.getWifiChipStats(TEST_INTERFACE_NAME)).thenReturn(null);
        assertNull(mWifiPowerStatsManager.getWlanPwrStats());
    }

    @Test
    public void testGetWlanPwrStats_NullInterfaceName() {
        when(mClientModeManager.getInterfaceName()).thenReturn(null);
        assertNull(mWifiPowerStatsManager.getWlanPwrStats());
    }

    @Test
    public void testGetWlanPwrStats_EmptyBuffer() {
        when(mNl80211Native.getWifiChipStats(TEST_INTERFACE_NAME))
                .thenReturn(ByteBuffer.allocate(0));
        assertNull(mWifiPowerStatsManager.getWlanPwrStats());
    }

    @Test
    public void testGetWlanPwrStats_MalformedBuffer() {
        // This buffer is too small to contain valid data, which should cause a parsing error.
        ByteBuffer malformedBuffer = ByteBuffer.allocate(4);
        malformedBuffer.putInt(1234);
        malformedBuffer.flip();
        when(mNl80211Native.getWifiChipStats(TEST_INTERFACE_NAME)).thenReturn(malformedBuffer);
        assertNull(mWifiPowerStatsManager.getWlanPwrStats());
    }

    @Test
    public void testDump() {
        // 1. Get stats to populate the log
        ByteBuffer testBuffer = createTestPowerStatsBuffer();
        when(mNl80211Native.getWifiChipStats(TEST_INTERFACE_NAME)).thenReturn(testBuffer);
        WifiChipStats stats = mWifiPowerStatsManager.getWlanPwrStats();

        // 2. Capture dump output
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        mWifiPowerStatsManager.dump(null, pw, null);
        String dumpOutput = sw.toString();

        // 3. Verify the output
        assertTrue(dumpOutput.contains("Dump of WifiPowerStatsManager"));
    }

    @Test
    public void testDump_ApiDisabled() {
        when(Flags.powerStatsApi()).thenReturn(false);
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        mWifiPowerStatsManager.dump(null, pw, null);
        String dumpOutput = sw.toString();
        assertTrue(dumpOutput.contains("WifiPowerStats: Power stats API is disabled."));
    }

    @Test
    public void testGetWlanPwrStats_DeepValidation() {
        ByteBuffer testBuffer = createTestPowerStatsBuffer();
        when(mNl80211Native.getWifiChipStats(TEST_INTERFACE_NAME)).thenReturn(testBuffer);

        WifiChipStats stats = mWifiPowerStatsManager.getWlanPwrStats();

        assertNotNull("WifiChipStats object should be created", stats);
        assertEquals("numWifiCore mismatch", 1, stats.numWifiCore);

        WifiChipStats.CoreRadioStats core = stats.coreStats[0];
        assertEquals("Core index mismatch", 0, core.coreIndex);
        assertEquals("Radio on time mismatch", 1000, core.radioOnTimeMs);
        assertEquals("TX time mismatch", 200, core.txTimeMs);
        assertEquals("RX time mismatch", 300, core.rxTimeMs);
        assertEquals("RX listening levels mismatch", 1, core.rxListeningLevelsNum);
        assertEquals("RX listening time value mismatch", 100, core.rxListeningTimeMsPerLevels[0]);

        WifiChipStats.TxRateStats txRate = stats.txRateStats[0];
        assertEquals("TX Rate list size mismatch", 1, txRate.rates.size());
        WifiChipStats.RateInfo txInfo = txRate.rates.get(0);
        assertEquals("TX Rate index mismatch", 5, txInfo.rateIndex);
        assertEquals("TX Band mismatch", 1, txInfo.band);
        assertEquals("TX NSS mismatch", 3, txInfo.nss);
        assertEquals("TX Count mismatch", 50, txInfo.count);

        WifiChipStats.RxRateStats rxRate = stats.rxRateStats[0];
        assertEquals("RX Rate list size mismatch", 1, rxRate.rates.size());
        assertEquals("RX Rate count should match mock data", 50, rxRate.rates.get(0).count);

        WifiChipStats.ChipPowerState chip = stats.chipPowerState;
        assertEquals("Chip power-on time mismatch", 5000, chip.wlanPwrOnTimeMs);
        assertEquals("Sleep levels count mismatch", 1, chip.sleepLevelsNum);
        assertEquals("Sleep time value mismatch", 1000, chip.sleepTimeMsPerLevels[0]);

        StringWriter sw = new StringWriter();
        mWifiPowerStatsManager.dump(null, new PrintWriter(sw), null);
        assertTrue("Dump should contain parsed stats data",
                sw.toString().contains("onTimeMs=5000"));
    }

    @Test
    public void testGetWlanPwrStats_ApiDisabled() {
        when(Flags.powerStatsApi()).thenReturn(false);

        WifiChipStats stats = mWifiPowerStatsManager.getWlanPwrStats();

        assertNotNull(stats);
        assertEquals(0, stats.numWifiCore);
        assertEquals(0, stats.coreStats.length);
        assertEquals(0, stats.txRateStats.length);
        assertEquals(0, stats.rxRateStats.length);
        assertNotNull(stats.chipPowerState);
        assertEquals(0, stats.chipPowerState.wlanPwrOnTimeMs);
    }

    @Test
    public void testGetWlanPwrStats_VerifiesInterfaceNameRetrieval() {
        // Setup a different interface name to verify dynamic retrieval
        String dynamicInterfaceName = "wlan1";
        when(mClientModeManager.getInterfaceName()).thenReturn(dynamicInterfaceName);
        when(mNl80211Native.getWifiChipStats(dynamicInterfaceName))
                .thenReturn(createTestPowerStatsBuffer());

        mWifiPowerStatsManager.getWlanPwrStats();

        verify(mActiveModeWarden).getPrimaryClientModeManager();
        verify(mClientModeManager).getInterfaceName();
        verify(mNl80211Native).getWifiChipStats(dynamicInterfaceName);
    }

    @Test
    public void testGetWlanPwrStats_FallbackToSoftApInterfaceName() {
        when(mClientModeManager.getInterfaceName()).thenReturn(null);

        SoftApManager softApManager = mock(SoftApManager.class);
        String softApInterfaceName = "wlan_ap0";
        when(softApManager.getInterfaceName()).thenReturn(softApInterfaceName);
        when(mActiveModeWarden.getTetheredSoftApManager()).thenReturn(softApManager);

        when(mNl80211Native.getWifiChipStats(softApInterfaceName))
                .thenReturn(createTestPowerStatsBuffer());

        WifiChipStats stats = mWifiPowerStatsManager.getWlanPwrStats();

        assertNotNull(stats);
        verify(mActiveModeWarden).getPrimaryClientModeManager();
        verify(mActiveModeWarden).getTetheredSoftApManager();
        verify(softApManager).getInterfaceName();
        verify(mNl80211Native).getWifiChipStats(softApInterfaceName);
    }
}
