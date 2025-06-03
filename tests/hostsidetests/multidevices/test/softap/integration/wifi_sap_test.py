#  Copyright (C) 2024 The Android Open Source Project
#
#  Licensed under the Apache License, Version 2.0 (the "License");
#  you may not use this file except in compliance with the License.
#  You may obtain a copy of the License at
#
#       http://www.apache.org/licenses/LICENSE-2.0
#
#  Unless required by applicable law or agreed to in writing, software
#  distributed under the License is distributed on an "AS IS" BASIS,
#  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#  See the License for the specific language governing permissions and
#  limitations under the License.

# Lint as: python3
import logging
import time


from android.platform.test.annotations import ApiTest
from mobly import asserts
from mobly import base_test
from mobly import test_runner
from mobly import utils
from mobly.controllers import android_device
from mobly.controllers.android_device_lib import callback_handler_v2
from aware import aware_lib_utils as autils
from softap import constants
import wifi_test_utils
from mobly.controllers.android_device_lib import adb
from softap.integration import wifi_sap_lib_utils as sutils


DBS_SUPPORTED_MODELS =  ["komodo"]
STA_CONCURRENCY_SUPPORTED_MODELS  =["komodo", "caiman"]
WIFI6_MODELS =  ["komodo"]

WAIT_REBOOT_SEC = 20

class WifiSoftApTest(base_test.BaseTestClass):
  """SoftAp test class.

  Attributes:
    host: Android device providing Wi-Fi hotspot
    client: Android device connecting to host provided hotspot
  """

  def setup_class(self) -> None:
    self.ads = self.register_controller(android_device, min_number=2)
    self.host = self.ads[0]
    self.client = self.ads[1]


    utils.concurrent_exec(
        self._setup_device,
        param_list=[[ad] for ad in self.ads],
        raise_on_exception=True,
    )

    asserts.abort_class_if(
        not self.host.wifi.wifiIsPortableHotspotSupported(),
        'Hotspot is not supported on host, abort remaining tests.',
    )

  def _setup_device(self, ad: android_device.AndroidDevice) -> None:
    ad.load_snippet('wifi', 'com.google.snippet.wifi')
    sutils._stop_tethering(ad)
    wifi_test_utils.enable_wifi_verbose_logging(ad)
    wifi_test_utils.set_screen_on_and_unlock(ad)

    self.AP_IFACE = 'wlan0'
    if ad.model in DBS_SUPPORTED_MODELS:
      self.AP_IFACE = 'wlan1'
    if ad.model in STA_CONCURRENCY_SUPPORTED_MODELS:
      self.AP_IFACE = 'wlan2'

  def setup_test(self):
    for ad in self.ads:
      if not ad.wifi.wifiIsEnabled():
        ad.wifi.wifiEnable()
      sutils._stop_tethering(self.host)

  def on_fail(self, record):
    logging.info('Collecting bugreports...')
    android_device.take_bug_reports(
      ads=[self.host, self.client],
      test_name=record.test_name,
      begin_time=record.begin_time,
      destination=self.current_test_info.output_path
        )

  def teardown_test(self):
    for ad in self.ads:
      sutils._stop_tethering(self.host)
      ad.wifi.wifiClearConfiguredNetworks()
      ad.wifi.wifiDisableAllSavedNetworks()
      ad.wifi.wifiEnable()
      if ad.is_adb_root:
        autils.set_airplane_mode(self.host, False)

  def teardown_class(self):
    for ad in self.ads:
      ad.wifi.wifiDisableAllSavedNetworks()
      ad.wifi.wifiClearConfiguredNetworks()
      ad.wifi.wifiEnable()
      ad.wifi.wifiFactoryReset()


  def confirm_softap_in_scan_results(self, ap_ssid):
    """Confirm the ap started by wifi tethering is seen in scan results.

        Args:
            ap_ssid: SSID of the ap we are looking for.
    """
    sutils.start_wifi_connection_scan_and_check_for_network(
      self.client, ap_ssid)

  def validate_ping_between_softap_and_client(self, config):
    """Test ping between softap and its client.

        Connect one android device to the wifi hotspot.
        Verify they can ping each other.

        Args:
            config: wifi network config with SSID, password
    """
    config = {
      "SSID": config[constants.WiFiTethering.SSID_KEY],
      "password": config[constants.WiFiTethering.PWD_KEY],
      }
    sutils._wifi_connect(self.client, config, check_connectivity=False)
    host_ip = self.host.wifi.connectivityGetIPv4Addresses(self.AP_IFACE)[0]
    client_ip = self.client.wifi.connectivityGetIPv4Addresses('wlan0')[0]
    sutils.verify_11ax_softap(self.host, self.client, WIFI6_MODELS)
    self.client.log.info("Try to ping %s" % host_ip)
    asserts.assert_true(
      sutils.adb_shell_ping(self.client, count=10, dest_ip=host_ip),
      "%s ping %s failed" % (self.client.serial, host_ip))
    self.host.log.info("Try to ping %s" % client_ip)
    asserts.assert_true(
      sutils.adb_shell_ping(self.host, count=10, dest_ip=client_ip),
      "%s ping %s failed" % (self.host.serial, client_ip))

  def validate_full_tether_startup(self,
                                  band=None,
                                  hidden=False,
                                  test_ping=False,
                                  security=None):

    """Test full startup of wifi tethering.

        This test case performs the following steps:
        1. Reports the current WiFi state of the host device.
        2. Attempts to switch the host device to WiFi Access Point (AP) mode,
          configuring the SoftAP with provided or default settings.
        3. Verifies that the SoftAP is successfully activated on the host device.
        4. Shuts down the WiFi tethering (disables SoftAP) on the host device.
        5. Verifies that the WiFi state on the host device has returned to its
          previous state before tethering was enabled.

        Args:
            band (str, optional):
              The Wi-Fi band to use for tethering (e.g., "2.4GHz",
              "5GHz"). Defaults to None,which might use a default
              or let the system decide.
            hidden (bool, optional):
              A boolean indicating whether the SoftAP network
              should be hidden (not broadcast its SSID). Defaults to False.
            test_ping (bool, optional):
              A boolean indicating whether to perform a ping test between
                the SoftAP host and a connected client. Requires a client
                device to be associated with `self.client`. Defaults to False.
            security (str, optional):
              The security protocol to use for the SoftAP (e.g., "WPA2_PSK").
                Defaults to None, which might use a default or open network.
    """

    initial_wifi_state = self.host.wifi.wifiCheckState()
    #Skip for sim state check
    self.host.log.info("current state: %s", initial_wifi_state)
    config = sutils.create_softap_config()
    sutils.start_wifi_tethering(self.host,
                                config[constants.WiFiTethering.SSID_KEY],
                                config[constants.WiFiTethering.PWD_KEY],
                                band,
                                hidden,
                                security)
    if hidden:
      # First ensure it's not seen in scan results.
      # If the network is hidden, it should be saved on the client to be
      # seen in scan results.
      sutils.start_wifi_connection_scan_and_ensure_network_not_found(
        self.client, config[constants.WiFiTethering.SSID_KEY])
      config[constants.WiFiTethering.HIDDEN_KEY] = True
      ret = self.client.wifi.wifiAddNetwork(config)
      asserts.assert_true(ret != -1, "Add network %r failed" % config)
      self.client.wifi.wifiEnableNetwork(ret, 0)
    self.confirm_softap_in_scan_results(config[constants.WiFiTethering.SSID_KEY])
    if test_ping:
      self.validate_ping_between_softap_and_client(config)
    sutils._stop_tethering(self.host)
    asserts.assert_false(self.host.wifi.wifiIsApEnabled(),
                        "SoftAp is still reported as running")
    if initial_wifi_state:
      sutils.wait_for_wifi_state(self.host, True)
    elif self.host.wifi.wifiCheckState():
      asserts.fail("Wifi was disabled before softap and now it is enabled")

  def validate_softap_after_reboot(self, band, security, hidden=False):
    config = sutils.create_softap_config()
    softap_config = config.copy()
    softap_config[constants.WiFiTethering.AP_BAND_KEY] = band
    softap_config[constants.WiFiTethering.SECURITY] = security
    if hidden:
      softap_config[constants.WiFiTethering.HIDDEN_KEY] = hidden
    asserts.assert_true(
      self.host.wifi.wifiSetWifiApConfiguration(softap_config),
      "Failed to update WifiAp Configuration")
    logging.info("StartTracking...")
    self.host.wifi.tetheringStartTrackingTetherStateChange()
    time.sleep(WAIT_REBOOT_SEC)
    logging.info("start reboot...")
    with self.host.handle_reboot():
        self.host.reboot()
        self.host.log.info("DUT rebooted successfully")
        time.sleep(WAIT_REBOOT_SEC)
    sutils.start_wifi_tethering_saved_config(self.host)
    sutils.connect_to_wifi_network(self.client,softap_config, hidden=hidden)

  @ApiTest(
    apis=[
      'android.net.wifi.WifiManager#isPortableHotspotSupported()',
      'android.net.ConnectivityManage#isTetheringSupported()',
      ]
  )

  def test_check_wifi_tethering_supported(self):
    """Test check for wifi tethering support.
      1. Call method to check if wifi hotspot is supported
        """
    hotspot_supported = self.host.wifi.wifiIsPortableHotspotSupported()
    tethering_supported = self.host.wifi.connectivityIsTetheringSupported()
    logging.info(
      "IsPortableHotspotSupported: %s, IsTetheringSupported %s." % (
        hotspot_supported, tethering_supported))
    asserts.assert_true(
      hotspot_supported,
      "DUT should support wifi tethering but is reporting false.")
    asserts.assert_true(
      tethering_supported,
      "DUT should also support wifi tethering when called from"
      +" ConnectivityManager")

  @ApiTest(
    apis=[
      'android.net.wifi.WifiManager#isPortableHotspotSupported()',
      'android.net.ConnectivityManage#isTetheringSupported()',
      'android.net.wifi.WifiManager.getWifiState()',
      'android.net.wifi.WifiManager.setSoftApConfiguration(SoftApConfiguration.Builder())',
      'android.net.TetheringManage.startTethering(int type,'+
      ' @NonNull final Executor executor,final StartTetheringCallback callback)',
      'android.net.wifi.WifiManager.SCAN_RESULTS_AVAILABLE_ACTION',
      'android.net.wifi.WifiManager.startScan()',
      'android.net.wifi.WifiManager.getScanResults()',
      'android.net.wifi.WifiManager.addNetwork(android.net.wifi.WifiConfiguration(json))',
      'android.net.wifi.WifiManager.enableNetwork(netId, disableOthers)',
      'android.net.wifi.WifiManager.connect(android.net.wifi.WifiConfiguration(json))',
      'android.net.wifi.WifiManager.getConnectionInfo().getWifiStandard()',
      ]
  )

  def test_full_tether_startup(self):

    """Test full startup of wifi tethering in default band.

        1. Report current state.
        2. Switch to AP mode.
        3. verify SoftAP active.
        4. Shutdown wifi tethering.
        5. verify back to previous mode.
    """
    self.validate_full_tether_startup()

  @ApiTest(
    apis=[
      'android.net.wifi.WifiManager#isPortableHotspotSupported()',
      'android.net.ConnectivityManage#isTetheringSupported()',
      'android.net.wifi.WifiManager.getWifiState()',
      'android.net.wifi.WifiManager.setSoftApConfiguration(SoftApConfiguration.Builder())',
      'android.net.TetheringManage.startTethering(int type,'+
      ' @NonNull final Executor executor,final StartTetheringCallback callback)',
      'android.net.wifi.WifiManager.SCAN_RESULTS_AVAILABLE_ACTION',
      'android.net.wifi.WifiManager.startScan()',
      'android.net.wifi.WifiManager.getScanResults()',
      'android.net.wifi.WifiManager.addNetwork(android.net.wifi.WifiConfiguration(json))',
      'android.net.wifi.WifiManager.enableNetwork(netId, disableOthers)',
      'android.net.wifi.WifiManager.connect(android.net.wifi.WifiConfiguration(json))',
      'android.net.wifi.WifiManager.getConnectionInfo().getWifiStandard()',
      ]
  )

  def test_full_tether_startup_2G(self):
    """Test full startup of wifi tethering in 2G band.

        1. Report current state.
        2. Switch to AP mode.
        3. verify SoftAP active.
        4. Shutdown wifi tethering.
      5. verify back to previous mode.
    """
    self.validate_full_tether_startup(
      constants.WiFiHotspotBand.WIFI_CONFIG_SOFTAP_BAND_2G)

  @ApiTest(
    apis=[
      'android.net.wifi.WifiManager#isPortableHotspotSupported()',
      'android.net.ConnectivityManage#isTetheringSupported()',
      'android.net.wifi.WifiManager.getWifiState()',
      'android.net.wifi.WifiManager.setSoftApConfiguration(SoftApConfiguration.Builder())',
      'android.net.TetheringManage.startTethering(int type,'+
      ' @NonNull final Executor executor,final StartTetheringCallback callback)',
      'android.net.wifi.WifiManager.SCAN_RESULTS_AVAILABLE_ACTION',
      'android.net.wifi.WifiManager.startScan()',
      'android.net.wifi.WifiManager.getScanResults()',
      'android.net.wifi.WifiManager.addNetwork(android.net.wifi.WifiConfiguration(json))',
      'android.net.wifi.WifiManager.enableNetwork(netId, disableOthers)',
      'android.net.wifi.WifiManager.connect(android.net.wifi.WifiConfiguration(json))',
      'android.net.wifi.WifiManager.getConnectionInfo().getWifiStandard()',
      ]
  )

  def test_full_tether_startup_5G(self):
    """Test full startup of wifi tethering in 5G band.

        1. Report current state.
        2. Switch to AP mode.
        3. verify SoftAP active.
        4. Shutdown wifi tethering.
        5. verify back to previous mode.
    """
    self.validate_full_tether_startup(
      constants.WiFiHotspotBand.WIFI_CONFIG_SOFTAP_BAND_5G)

  @ApiTest(
    apis=[
      'android.net.wifi.WifiManager#isPortableHotspotSupported()',
      'android.net.ConnectivityManage#isTetheringSupported()',
      'android.net.wifi.WifiManager.getWifiState()',
      'android.net.wifi.WifiManager.setSoftApConfiguration(SoftApConfiguration.Builder())',
      'android.net.TetheringManage.startTethering(int type,'+
      ' @NonNull final Executor executor,final StartTetheringCallback callback)',
      'android.net.wifi.WifiManager.SCAN_RESULTS_AVAILABLE_ACTION',
      'android.net.wifi.WifiManager.startScan()',
      'android.net.wifi.WifiManager.getScanResults()',
      'android.net.wifi.WifiManager.addNetwork(android.net.wifi.WifiConfiguration(json))',
      'android.net.wifi.WifiManager.enableNetwork(netId, disableOthers)',
      'android.net.wifi.WifiManager.connect(android.net.wifi.WifiConfiguration(json))',
      'android.net.wifi.WifiManager.getConnectionInfo().getWifiStandard()',
      ]
  )

  def test_full_tether_startup_auto(self):
    """Test full startup of wifi tethering in 5G band.

        1. Report current state.
        2. Switch to AP mode.
        3. verify SoftAP active.
        4. Shutdown wifi tethering.
        5. verify back to previous mode.
    """
    self.validate_full_tether_startup(
      constants.WiFiHotspotBand.WIFI_CONFIG_SOFTAP_BAND_2G_5G)

  @ApiTest(
    apis=[
      'android.net.wifi.WifiManager#isPortableHotspotSupported()',
      'android.net.ConnectivityManage#isTetheringSupported()',
      'android.net.wifi.WifiManager.getWifiState()',
      'android.net.wifi.WifiManager.setSoftApConfiguration(SoftApConfiguration.Builder())',
      'android.net.TetheringManage.startTethering(int type,'+
      ' @NonNull final Executor executor,final StartTetheringCallback callback)',
      'android.net.wifi.WifiManager.SCAN_RESULTS_AVAILABLE_ACTION',
      'android.net.wifi.WifiManager.startScan()',
      'android.net.wifi.WifiManager.getScanResults()',
      'android.net.wifi.WifiManager.addNetwork(android.net.wifi.WifiConfiguration(json))',
      'android.net.wifi.WifiManager.enableNetwork(netId, disableOthers)',
      'android.net.wifi.WifiManager.connect(android.net.wifi.WifiConfiguration(json))',
      'android.net.wifi.WifiManager.getConnectionInfo().getWifiStandard()',
      ]
  )

  def test_full_tether_startup_2G_hidden(self):
    """Test full startup of wifi tethering in 2G band using hidden AP.

        1. Report current state.
        2. Switch to AP mode.
        3. verify SoftAP active.
        4. Shutdown wifi tethering.
        5. verify back to previous mode.
    """
    self.validate_full_tether_startup(
      constants.WiFiHotspotBand.WIFI_CONFIG_SOFTAP_BAND_2G,
      hidden=True)

  @ApiTest(
    apis=[
      'android.net.wifi.WifiManager#isPortableHotspotSupported()',
      'android.net.ConnectivityManage#isTetheringSupported()',
      'android.net.wifi.WifiManager.getWifiState()',
      'android.net.wifi.WifiManager.setSoftApConfiguration(SoftApConfiguration.Builder())',
      'android.net.TetheringManage.startTethering(int type,'+
      ' @NonNull final Executor executor,final StartTetheringCallback callback)',
      'android.net.wifi.WifiManager.SCAN_RESULTS_AVAILABLE_ACTION',
      'android.net.wifi.WifiManager.startScan()',
      'android.net.wifi.WifiManager.getScanResults()',
      'android.net.wifi.WifiManager.addNetwork(android.net.wifi.WifiConfiguration(json))',
      'android.net.wifi.WifiManager.enableNetwork(netId, disableOthers)',
      'android.net.wifi.WifiManager.connect(android.net.wifi.WifiConfiguration(json))',
      'android.net.wifi.WifiManager.getConnectionInfo().getWifiStandard()',
      ]
  )

  def test_full_tether_startup_5G_hidden(self):
    """Test full startup of wifi tethering in 5G band using hidden AP.

        1. Report current state.
        2. Switch to AP mode.
        3. verify SoftAP active.
        4. Shutdown wifi tethering.
        5. verify back to previous mode.
    """
    self.validate_full_tether_startup(
      constants.WiFiHotspotBand.WIFI_CONFIG_SOFTAP_BAND_5G,
      hidden=True)

  @ApiTest(
    apis=[
      'android.net.wifi.WifiManager#isPortableHotspotSupported()',
      'android.net.ConnectivityManage#isTetheringSupported()',
      'android.net.wifi.WifiManager.getWifiState()',
      'android.net.wifi.WifiManager.setSoftApConfiguration(SoftApConfiguration.Builder())',
      'android.net.TetheringManage.startTethering(int type,'+
      ' @NonNull final Executor executor,final StartTetheringCallback callback)',
      'android.net.wifi.WifiManager.SCAN_RESULTS_AVAILABLE_ACTION',
      'android.net.wifi.WifiManager.startScan()',
      'android.net.wifi.WifiManager.getScanResults()',
      'android.net.wifi.WifiManager.addNetwork(android.net.wifi.WifiConfiguration(json))',
      'android.net.wifi.WifiManager.enableNetwork(netId, disableOthers)',
      'android.net.wifi.WifiManager.connect(android.net.wifi.WifiConfiguration(json))',
      'android.net.wifi.WifiManager.getConnectionInfo().getWifiStandard()',
      ]
  )

  def test_full_tether_startup_auto_hidden(self):
    """Test full startup of wifi tethering in auto-band using hidden AP.

        1. Report current state.
        2. Switch to AP mode.
        3. verify SoftAP active.
        4. Shutdown wifi tethering.
        5. verify back to previous mode.
    """
    self.validate_full_tether_startup(
      constants.WiFiHotspotBand.WIFI_CONFIG_SOFTAP_BAND_2G_5G,
      hidden=True)

  @ApiTest(
    apis=[
      'android.net.wifi.WifiManager#isPortableHotspotSupported()',
      'android.net.ConnectivityManage#isTetheringSupported()',
      'android.net.wifi.WifiManager.getWifiState()',
      'android.net.wifi.WifiManager.setSoftApConfiguration(SoftApConfiguration.Builder())',
      'android.net.TetheringManage.startTethering(int type,'+
      ' @NonNull final Executor executor,final StartTetheringCallback callback)',
      'android.net.wifi.WifiManager.SCAN_RESULTS_AVAILABLE_ACTION',
      'android.net.wifi.WifiManager.startScan()',
      'android.net.wifi.WifiManager.getScanResults()',
      'android.net.wifi.WifiManager.addNetwork(android.net.wifi.WifiConfiguration(json))',
      'android.net.wifi.WifiManager.enableNetwork(netId, disableOthers)',
      'android.net.wifi.WifiManager.connect(android.net.wifi.WifiConfiguration(json))',
      'android.net.wifi.WifiManager.getConnectionInfo().getWifiStandard()',
      ]
  )

  def test_full_tether_startup_wpa3(self):
    """Test full startup of softap in default band and wpa3 security.

        Steps:
        1. Configure softap in default band and wpa3 security.
        2. Verify dut client connects to the softap.
    """
    for self.ad in self.ads:
      asserts.skip_if(self.ad.model not in STA_CONCURRENCY_SUPPORTED_MODELS,
                        "DUT does not support WPA3 softAp")

    self.validate_full_tether_startup(
      security=constants.SoftApSecurityType.WPA3_SAE)

  @ApiTest(
    apis=[
      'android.net.wifi.WifiManager#isPortableHotspotSupported()',
      'android.net.ConnectivityManage#isTetheringSupported()',
      'android.net.wifi.WifiManager.getWifiState()',
      'android.net.wifi.WifiManager.setSoftApConfiguration(SoftApConfiguration.Builder())',
      'android.net.TetheringManage.startTethering(int type,'+
      ' @NonNull final Executor executor,final StartTetheringCallback callback)',
      'android.net.wifi.WifiManager.SCAN_RESULTS_AVAILABLE_ACTION',
      'android.net.wifi.WifiManager.startScan()',
      'android.net.wifi.WifiManager.getScanResults()',
      'android.net.wifi.WifiManager.addNetwork(android.net.wifi.WifiConfiguration(json))',
      'android.net.wifi.WifiManager.enableNetwork(netId, disableOthers)',
      'android.net.wifi.WifiManager.connect(android.net.wifi.WifiConfiguration(json))',
      'android.net.wifi.WifiManager.getConnectionInfo().getWifiStandard()',
      ]
  )

  def test_full_tether_startup_2G_wpa3(self):
    """Test full startup of softap in 2G band and wpa3 security.

        Steps:
        1. Configure softap in 2G band and wpa3 security.
        2. Verify dut client connects to the softap.
    """
    for self.ad in self.ads:
      asserts.skip_if(self.ad.model not in STA_CONCURRENCY_SUPPORTED_MODELS,
                    "DUT does not support WPA3 softAp")
    self.validate_full_tether_startup(
      constants.WiFiHotspotBand.WIFI_CONFIG_SOFTAP_BAND_2G,
      security=constants.SoftApSecurityType.WPA3_SAE)

  @ApiTest(
    apis=[
      'android.net.wifi.WifiManager#isPortableHotspotSupported()',
      'android.net.ConnectivityManage#isTetheringSupported()',
      'android.net.wifi.WifiManager.getWifiState()',
      'android.net.wifi.WifiManager.setSoftApConfiguration(SoftApConfiguration.Builder())',
      'android.net.TetheringManage.startTethering(int type,'+
      ' @NonNull final Executor executor,final StartTetheringCallback callback)',
      'android.net.wifi.WifiManager.SCAN_RESULTS_AVAILABLE_ACTION',
      'android.net.wifi.WifiManager.startScan()',
      'android.net.wifi.WifiManager.getScanResults()',
      'android.net.wifi.WifiManager.addNetwork(android.net.wifi.WifiConfiguration(json))',
      'android.net.wifi.WifiManager.enableNetwork(netId, disableOthers)',
      'android.net.wifi.WifiManager.connect(android.net.wifi.WifiConfiguration(json))',
      'android.net.wifi.WifiManager.getConnectionInfo().getWifiStandard()',
      ]
  )

  def test_full_tether_startup_5G_wpa3(self):
    """Test full startup of softap in 5G band and wpa3 security.

        Steps:
        1. Configure softap in 5G band and wpa3 security.
        2. Verify dut client connects to the softap.
    """
    for self.ad in self.ads:
      asserts.skip_if(self.ad.model not in STA_CONCURRENCY_SUPPORTED_MODELS,
                    "DUT does not support WPA3 softAp")
    self.validate_full_tether_startup(
      constants.WiFiHotspotBand.WIFI_CONFIG_SOFTAP_BAND_5G,
      security=constants.SoftApSecurityType.WPA3_SAE)

  @ApiTest(
    apis=[
      'android.net.wifi.WifiManager#isPortableHotspotSupported()',
      'android.net.ConnectivityManage#isTetheringSupported()',
      'android.net.wifi.WifiManager.getWifiState()',
      'android.net.wifi.WifiManager.setSoftApConfiguration(SoftApConfiguration.Builder())',
      'android.net.TetheringManage.startTethering(int type,'+
      ' @NonNull final Executor executor,final StartTetheringCallback callback)',
      'android.net.wifi.WifiManager.SCAN_RESULTS_AVAILABLE_ACTION',
      'android.net.wifi.WifiManager.startScan()',
      'android.net.wifi.WifiManager.getScanResults()',
      'android.net.wifi.WifiManager.addNetwork(android.net.wifi.WifiConfiguration(json))',
      'android.net.wifi.WifiManager.enableNetwork(netId, disableOthers)',
      'android.net.wifi.WifiManager.connect(android.net.wifi.WifiConfiguration(json))',
      'android.net.wifi.WifiManager.getConnectionInfo().getWifiStandard()',
      ]
  )

  def test_full_tether_startup_5G_wpa3(self):
    """Test full startup of softap in 5G band and wpa3 security.

        Steps:
        1. Configure softap in 5G band and wpa3 security.
        2. Verify dut client connects to the softap.
    """
    for self.ad in self.ads:
      asserts.skip_if(self.ad.model not in STA_CONCURRENCY_SUPPORTED_MODELS,
                    "DUT does not support WPA3 softAp")
    self.validate_full_tether_startup(
      constants.WiFiHotspotBand.WIFI_CONFIG_SOFTAP_BAND_2G_5G,
      security=constants.SoftApSecurityType.WPA3_SAE)

  @ApiTest(
    apis=[
      'android.net.wifi.WifiManager#isPortableHotspotSupported()',
      'android.net.ConnectivityManage#isTetheringSupported()',
      'android.net.wifi.WifiManager.getWifiState()',
      'android.net.wifi.WifiManager.setSoftApConfiguration(SoftApConfiguration.Builder())',
      'android.net.TetheringManage.startTethering(int type,'+
      ' @NonNull final Executor executor,final StartTetheringCallback callback)',
      'android.net.wifi.WifiManager.SCAN_RESULTS_AVAILABLE_ACTION',
      'android.net.wifi.WifiManager.startScan()',
      'android.net.wifi.WifiManager.getScanResults()',
      'android.net.wifi.WifiManager.addNetwork(android.net.wifi.WifiConfiguration(json))',
      'android.net.wifi.WifiManager.enableNetwork(netId, disableOthers)',
      'android.net.wifi.WifiManager.connect(android.net.wifi.WifiConfiguration(json))',
      'android.net.wifi.WifiManager.getConnectionInfo().getWifiStandard()',
      ]
  )

  def test_full_tether_startup_hidden_wpa3(self):
    """Test full startup of hidden softap in default band and wpa3 security.

        Steps:
        1. Configure hidden softap in default band and wpa3 security.
      2. Verify dut client connects to the softap.
    """
    for self.ad in self.ads:
      asserts.skip_if(self.ad.model not in STA_CONCURRENCY_SUPPORTED_MODELS,
                      "DUT does not support WPA3 softAp")
    self.validate_full_tether_startup(
      hidden=True,
      security=constants.SoftApSecurityType.WPA3_SAE
      )

  @ApiTest(
    apis=[
      'android.net.wifi.WifiManager#isPortableHotspotSupported()',
      'android.net.ConnectivityManage#isTetheringSupported()',
      'android.net.wifi.WifiManager.getWifiState()',
      'android.net.wifi.WifiManager.setSoftApConfiguration(SoftApConfiguration.Builder())',
      'android.net.TetheringManage.startTethering(int type,'+
      ' @NonNull final Executor executor,final StartTetheringCallback callback)',
      'android.net.wifi.WifiManager.SCAN_RESULTS_AVAILABLE_ACTION',
      'android.net.wifi.WifiManager.startScan()',
      'android.net.wifi.WifiManager.getScanResults()',
      'android.net.wifi.WifiManager.addNetwork(android.net.wifi.WifiConfiguration(json))',
      'android.net.wifi.WifiManager.enableNetwork(netId, disableOthers)',
      'android.net.wifi.WifiManager.connect(android.net.wifi.WifiConfiguration(json))',
      'android.net.wifi.WifiManager.getConnectionInfo().getWifiStandard()',
      ]
  )

  def test_full_tether_startup_2G_hidden_wpa3(self):
    """Test full startup of hidden softap in 2G band and wpa3 security.

        Steps:
        1. Configure hidden softap in 2G band and wpa3 security.
        2. Verify dut client connects to the softap.
    """
    for self.ad in self.ads:
      asserts.skip_if(self.ad.model not in STA_CONCURRENCY_SUPPORTED_MODELS,
                      "DUT does not support WPA3 softAp")
    self.validate_full_tether_startup(
      constants.WiFiHotspotBand.WIFI_CONFIG_SOFTAP_BAND_2G,
      hidden=True,
      security=constants.SoftApSecurityType.WPA3_SAE)

  @ApiTest(
    apis=[
      'android.net.wifi.WifiManager#isPortableHotspotSupported()',
      'android.net.ConnectivityManage#isTetheringSupported()',
      'android.net.wifi.WifiManager.getWifiState()',
      'android.net.wifi.WifiManager.setSoftApConfiguration(SoftApConfiguration.Builder())',
      'android.net.TetheringManage.startTethering(int type,'+
      ' @NonNull final Executor executor,final StartTetheringCallback callback)',
      'android.net.wifi.WifiManager.SCAN_RESULTS_AVAILABLE_ACTION',
      'android.net.wifi.WifiManager.startScan()',
      'android.net.wifi.WifiManager.getScanResults()',
      'android.net.wifi.WifiManager.addNetwork(android.net.wifi.WifiConfiguration(json))',
      'android.net.wifi.WifiManager.enableNetwork(netId, disableOthers)',
      'android.net.wifi.WifiManager.connect(android.net.wifi.WifiConfiguration(json))',
      'android.net.wifi.WifiManager.getConnectionInfo().getWifiStandard()',
      ]
  )

  def test_full_tether_startup_5G_hidden_wpa3(self):
    """Test full startup of hidden softap in 5G band and wpa3 security.

        Steps:
        1. Configure hidden softap in 5G band and wpa3 security.
        2. Verify dut client connects to the softap.
    """
    for self.ad in self.ads:
      asserts.skip_if(self.ad.model not in STA_CONCURRENCY_SUPPORTED_MODELS,
                      "DUT does not support WPA3 softAp")
    self.validate_full_tether_startup(
      constants.WiFiHotspotBand.WIFI_CONFIG_SOFTAP_BAND_5G,
      hidden=True,
      security=constants.SoftApSecurityType.WPA3_SAE)

  @ApiTest(
    apis=[
      'android.net.wifi.WifiManager#isPortableHotspotSupported()',
      'android.net.ConnectivityManage#isTetheringSupported()',
      'android.net.wifi.WifiManager.getWifiState()',
      'android.net.wifi.WifiManager.setSoftApConfiguration(SoftApConfiguration.Builder())',
      'android.net.TetheringManage.startTethering(int type,'+
      ' @NonNull final Executor executor,final StartTetheringCallback callback)',
      'android.net.wifi.WifiManager.SCAN_RESULTS_AVAILABLE_ACTION',
      'android.net.wifi.WifiManager.startScan()',
      'android.net.wifi.WifiManager.getScanResults()',
      'android.net.wifi.WifiManager.addNetwork(android.net.wifi.WifiConfiguration(json))',
      'android.net.wifi.WifiManager.enableNetwork(netId, disableOthers)',
      'android.net.wifi.WifiManager.connect(android.net.wifi.WifiConfiguration(json))',
      'android.net.wifi.WifiManager.getConnectionInfo().getWifiStandard()',
      ]
  )

  def test_full_tether_startup_auto_hidden_wpa3(self):
    """Test full startup of hidden softap in auto band and wpa3 security.

        Steps:
        1. Configure hidden softap in auto band and wpa3 security.
        2. Verify dut client connects to the softap.
    """
    for self.ad in self.ads:
      asserts.skip_if(self.ad.model not in STA_CONCURRENCY_SUPPORTED_MODELS,
                      "DUT does not support WPA3 softAp")
    self.validate_full_tether_startup(
      constants.WiFiHotspotBand.WIFI_CONFIG_SOFTAP_BAND_2G_5G,
      hidden=True,
      security=constants.SoftApSecurityType.WPA3_SAE)

  @ApiTest(
    apis=[
      'android.net.wifi.WifiManager#isPortableHotspotSupported()',
      'android.net.ConnectivityManage#isTetheringSupported()',
      'android.net.wifi.WifiManager.getWifiState()',
      'android.net.wifi.WifiManager.setSoftApConfiguration(SoftApConfiguration.Builder())',
      'android.net.TetheringManage.startTethering(int type,'+
      ' @NonNull final Executor executor,final StartTetheringCallback callback)',
      'android.net.wifi.WifiManager.SCAN_RESULTS_AVAILABLE_ACTION',
      'android.net.wifi.WifiManager.startScan()',
      'android.net.wifi.WifiManager.getScanResults()',
      'android.net.wifi.WifiManager.addNetwork(android.net.wifi.WifiConfiguration(json))',
      'android.net.wifi.WifiManager.enableNetwork(netId, disableOthers)',
      'android.net.wifi.WifiManager.connect(android.net.wifi.WifiConfiguration(json))',
      'android.net.wifi.WifiManager.getConnectionInfo().getWifiStandard()',
      ]
  )

  def test_full_tether_startup_wpa2_wpa3(self):
    """Test full startup of softap in default band and wpa2/wpa3 security.

        Steps:
        1. Configure softap in default band and wpa2/wpa3 security.
        2. Verify dut client connects to the softap.
    """
    for self.ad in self.ads:
      asserts.skip_if(self.ad.model not in STA_CONCURRENCY_SUPPORTED_MODELS,
                      "DUT does not support WPA3 softAp")

    self.validate_full_tether_startup(
      security=constants.SoftApSecurityType.WPA3_SAE_TRANSITION)

  @ApiTest(
    apis=[
      'android.net.wifi.WifiManager#isPortableHotspotSupported()',
      'android.net.ConnectivityManage#isTetheringSupported()',
      'android.net.wifi.WifiManager.getWifiState()',
      'android.net.wifi.WifiManager.setSoftApConfiguration(SoftApConfiguration.Builder())',
      'android.net.TetheringManage.startTethering(int type,'+
      ' @NonNull final Executor executor,final StartTetheringCallback callback)',
      'android.net.wifi.WifiManager.SCAN_RESULTS_AVAILABLE_ACTION',
      'android.net.wifi.WifiManager.startScan()',
      'android.net.wifi.WifiManager.getScanResults()',
      'android.net.wifi.WifiManager.addNetwork(android.net.wifi.WifiConfiguration(json))',
      'android.net.wifi.WifiManager.enableNetwork(netId, disableOthers)',
      'android.net.wifi.WifiManager.connect(android.net.wifi.WifiConfiguration(json))',
      'android.net.wifi.WifiManager.getConnectionInfo().getWifiStandard()',
      ]
  )

  def test_full_tether_startup_2G_wpa2_wpa3(self):
    """Test full startup of softap in 2G band and wpa2/wpa3 security.

        Steps:
        1. Configure softap in default band and wpa2/wpa3 security.
        2. Verify dut client connects to the softap.
    """
    for self.ad in self.ads:
      asserts.skip_if(self.ad.model not in STA_CONCURRENCY_SUPPORTED_MODELS,
                      "DUT does not support WPA3 softAp")
    self.validate_full_tether_startup(
      constants.WiFiHotspotBand.WIFI_CONFIG_SOFTAP_BAND_2G,
      security=constants.SoftApSecurityType.WPA3_SAE_TRANSITION)

  @ApiTest(
    apis=[
      'android.net.wifi.WifiManager#isPortableHotspotSupported()',
      'android.net.ConnectivityManage#isTetheringSupported()',
      'android.net.wifi.WifiManager.getWifiState()',
      'android.net.wifi.WifiManager.setSoftApConfiguration(SoftApConfiguration.Builder())',
      'android.net.TetheringManage.startTethering(int type,'+
      ' @NonNull final Executor executor,final StartTetheringCallback callback)',
      'android.net.wifi.WifiManager.SCAN_RESULTS_AVAILABLE_ACTION',
      'android.net.wifi.WifiManager.startScan()',
      'android.net.wifi.WifiManager.getScanResults()',
      'android.net.wifi.WifiManager.addNetwork(android.net.wifi.WifiConfiguration(json))',
      'android.net.wifi.WifiManager.enableNetwork(netId, disableOthers)',
      'android.net.wifi.WifiManager.connect(android.net.wifi.WifiConfiguration(json))',
      'android.net.wifi.WifiManager.getConnectionInfo().getWifiStandard()',
      ]
  )

  def test_full_tether_startup_5G_wpa2_wpa3(self):
    """Test full startup of softap in 5G band and wpa2/wpa3 security.

        Steps:
        1. Configure softap in default band and wpa2/wpa3 security.
        2. Verify dut client connects to the softap.
    """
    for self.ad in self.ads:
      asserts.skip_if(self.ad.model not in STA_CONCURRENCY_SUPPORTED_MODELS,
                      "DUT does not support WPA3 softAp")
    self.validate_full_tether_startup(
      constants.WiFiHotspotBand.WIFI_CONFIG_SOFTAP_BAND_5G,
      security=constants.SoftApSecurityType.WPA3_SAE_TRANSITION)

  @ApiTest(
    apis=[
      'android.net.wifi.WifiManager#isPortableHotspotSupported()',
      'android.net.ConnectivityManage#isTetheringSupported()',
      'android.net.wifi.WifiManager.getWifiState()',
      'android.net.wifi.WifiManager.setSoftApConfiguration(SoftApConfiguration.Builder())',
      'android.net.TetheringManage.startTethering(int type,'+
      ' @NonNull final Executor executor,final StartTetheringCallback callback)',
      'android.net.wifi.WifiManager.SCAN_RESULTS_AVAILABLE_ACTION',
      'android.net.wifi.WifiManager.startScan()',
      'android.net.wifi.WifiManager.getScanResults()',
      'android.net.wifi.WifiManager.addNetwork(android.net.wifi.WifiConfiguration(json))',
      'android.net.wifi.WifiManager.enableNetwork(netId, disableOthers)',
      'android.net.wifi.WifiManager.connect(android.net.wifi.WifiConfiguration(json))',
      'android.net.wifi.WifiManager.getConnectionInfo().getWifiStandard()',
      ]
  )

  def test_full_tether_startup_auto_wpa2_wpa3(self):
    """Test full startup of softap in auto band and wpa2/wpa3 security.

        Steps:
        1. Configure softap in default band and wpa2/wpa3 security.
        2. Verify dut client connects to the softap.
    """
    for self.ad in self.ads:
      asserts.skip_if(self.ad.model not in STA_CONCURRENCY_SUPPORTED_MODELS,
                      "DUT does not support WPA3 softAp")
    self.validate_full_tether_startup(
      constants.WiFiHotspotBand.WIFI_CONFIG_SOFTAP_BAND_2G_5G,
      security=constants.SoftApSecurityType.WPA3_SAE_TRANSITION)

  @ApiTest(
    apis=[
      'android.net.wifi.WifiManager#isPortableHotspotSupported()',
      'android.net.ConnectivityManage#isTetheringSupported()',
      'android.net.wifi.WifiManager.getWifiState()',
      'android.net.wifi.WifiManager.setSoftApConfiguration(SoftApConfiguration.Builder())',
      'android.net.TetheringManage.startTethering(int type,'+
      ' @NonNull final Executor executor,final StartTetheringCallback callback)',
      'android.net.wifi.WifiManager.SCAN_RESULTS_AVAILABLE_ACTION',
      'android.net.wifi.WifiManager.startScan()',
      'android.net.wifi.WifiManager.getScanResults()',
      'android.net.wifi.WifiManager.addNetwork(android.net.wifi.WifiConfiguration(json))',
      'android.net.wifi.WifiManager.enableNetwork(netId, disableOthers)',
      'android.net.wifi.WifiManager.connect(android.net.wifi.WifiConfiguration(json))',
      'android.net.wifi.WifiManager.getConnectionInfo().getWifiStandard()',
      ]
  )

  def test_full_tether_startup_2G_hidden_wpa2_wpa3(self):
    """Test full startup of hidden softap in 2G band and wpa2/wpa3.

        Steps:
        1. Configure hidden softap in 2G band and wpa2/wpa3 security.
        2. Verify dut client connects to the softap.
    """
    for self.ad in self.ads:
      asserts.skip_if(self.ad.model not in STA_CONCURRENCY_SUPPORTED_MODELS,
                      "DUT does not support WPA3 softAp")
    self.validate_full_tether_startup(
       constants.WiFiHotspotBand.WIFI_CONFIG_SOFTAP_BAND_2G,
       hidden=True,
       security=constants.SoftApSecurityType.WPA3_SAE_TRANSITION)

  @ApiTest(
    apis=[
      'android.net.wifi.WifiManager#isPortableHotspotSupported()',
      'android.net.ConnectivityManage#isTetheringSupported()',
      'android.net.wifi.WifiManager.getWifiState()',
      'android.net.wifi.WifiManager.setSoftApConfiguration(SoftApConfiguration.Builder())',
      'android.net.TetheringManage.startTethering(int type,'+
      ' @NonNull final Executor executor,final StartTetheringCallback callback)',
      'android.net.wifi.WifiManager.SCAN_RESULTS_AVAILABLE_ACTION',
      'android.net.wifi.WifiManager.startScan()',
      'android.net.wifi.WifiManager.getScanResults()',
      'android.net.wifi.WifiManager.addNetwork(android.net.wifi.WifiConfiguration(json))',
      'android.net.wifi.WifiManager.enableNetwork(netId, disableOthers)',
      'android.net.wifi.WifiManager.connect(android.net.wifi.WifiConfiguration(json))',
      'android.net.wifi.WifiManager.getConnectionInfo().getWifiStandard()',
      ]
  )

  def test_full_tether_startup_5G_hidden_wpa2_wpa3(self):
    """Test full startup of hidden softap in 5G band and wpa2/wpa3.

        Steps:
        1. Configure hidden softap in 5G band and wpa2/wpa3 security.
        2. Verify dut client connects to the softap.
    """
    for self.ad in self.ads:
      asserts.skip_if(self.ad.model not in STA_CONCURRENCY_SUPPORTED_MODELS,
                      "DUT does not support WPA3 softAp")
    self.validate_full_tether_startup(
      constants.WiFiHotspotBand.WIFI_CONFIG_SOFTAP_BAND_5G,
      hidden=True,
      security=constants.SoftApSecurityType.WPA3_SAE_TRANSITION)

  @ApiTest(
    apis=[
      'android.net.wifi.WifiManager#isPortableHotspotSupported()',
      'android.net.ConnectivityManage#isTetheringSupported()',
      'android.net.wifi.WifiManager.getWifiState()',
      'android.net.wifi.WifiManager.setSoftApConfiguration(SoftApConfiguration.Builder())',
      'android.net.TetheringManage.startTethering(int type,'+
      ' @NonNull final Executor executor,final StartTetheringCallback callback)',
      'android.net.wifi.WifiManager.SCAN_RESULTS_AVAILABLE_ACTION',
      'android.net.wifi.WifiManager.startScan()',
      'android.net.wifi.WifiManager.getScanResults()',
      'android.net.wifi.WifiManager.addNetwork(android.net.wifi.WifiConfiguration(json))',
      'android.net.wifi.WifiManager.enableNetwork(netId, disableOthers)',
      'android.net.wifi.WifiManager.connect(android.net.wifi.WifiConfiguration(json))',
      'android.net.wifi.WifiManager.getConnectionInfo().getWifiStandard()',
      ]
  )

  def test_full_tether_startup_auto_hidden_wpa2_wpa3(self):
    """Test full startup of hidden softap in auto band and wpa2/wpa3.

        Steps:
        1. Configure hidden softap in auto band and wpa2/wpa3 security.
        2. Verify dut client connects to the softap.
    """
    for self.ad in self.ads:
      asserts.skip_if(self.ad.model not in STA_CONCURRENCY_SUPPORTED_MODELS,
                      "DUT does not support WPA3 softAp")
    self.validate_full_tether_startup(
       constants.WiFiHotspotBand.WIFI_CONFIG_SOFTAP_BAND_2G_5G,
       hidden=True,
       security=constants.SoftApSecurityType.WPA3_SAE_TRANSITION)

  @ApiTest(
    apis=[
      'android.net.wifi.WifiManager#isPortableHotspotSupported()',
      'android.net.ConnectivityManage#isTetheringSupported()',
      'android.net.wifi.WifiManager.getWifiState()',
      'android.net.wifi.WifiManager.setSoftApConfiguration(SoftApConfiguration.Builder())',
      'android.net.TetheringManage.startTethering(int type,'+
      ' @NonNull final Executor executor,final StartTetheringCallback callback)',
      'android.net.wifi.WifiManager.SCAN_RESULTS_AVAILABLE_ACTION',
      'android.net.wifi.WifiManager.startScan()',
      'android.net.wifi.WifiManager.getScanResults()',
      'android.net.wifi.WifiManager.addNetwork(android.net.wifi.WifiConfiguration(json))',
      'android.net.wifi.WifiManager.enableNetwork(netId, disableOthers)',
      'android.net.wifi.WifiManager.connect(android.net.wifi.WifiConfiguration(json))',
      'android.net.wifi.WifiManager.getConnectionInfo().getWifiStandard()',
      ]
  )

  def test_full_tether_startup_2G_with_airplane_mode_on(self):
    """Test full startup of wifi tethering in 2G band with
        airplane mode on.

        1. Turn on airplane mode.
        2. Report current state.
        3. Switch to AP mode.
        4. verify SoftAP active.
        5. Shutdown wifi tethering.
        6. verify back to previous mode.
        7. Turn off airplane mode.
    """
    asserts.skip_if(
      not self.host.is_adb_root,
      "APM toggle needs Android device(s) with root permission")
    self.host.log.debug("Toggling Airplane mode ON.")
    autils.set_airplane_mode(self.host, True)
    asserts.assert_true(autils._get_airplane_mode(self.host),
                        "Can not turn on airplane mode: %s" % self.host.serial)
    if not self.host.wifi.wifiIsEnabled():
      self.host.wifi.wifiEnable()
    self.validate_full_tether_startup(
      band=constants.WiFiHotspotBand.WIFI_CONFIG_SOFTAP_BAND_2G)

  @ApiTest(
    apis=[
      'android.net.wifi.WifiManager#isPortableHotspotSupported()',
      'android.net.ConnectivityManage#isTetheringSupported()',
      'android.net.wifi.WifiManager.getWifiState()',
      'android.net.wifi.WifiManager.setSoftApConfiguration(SoftApConfiguration.Builder())',
      'android.net.TetheringManage.startTethering(int type,'+
      ' @NonNull final Executor executor,final StartTetheringCallback callback)',
      'android.net.wifi.WifiManager.SCAN_RESULTS_AVAILABLE_ACTION',
      'android.net.wifi.WifiManager.startScan()',
      'android.net.wifi.WifiManager.getScanResults()',
      'android.net.wifi.WifiManager.addNetwork(android.net.wifi.WifiConfiguration(json))',
      'android.net.wifi.WifiManager.enableNetwork(netId, disableOthers)',
      'android.net.wifi.WifiManager.connect(android.net.wifi.WifiConfiguration(json))',
      'android.net.wifi.WifiManager.getConnectionInfo().getWifiStandard()',
      ]
  )

  def test_full_tether_startup_2G_one_client_ping_softap(self):
    """Device can connect to 2G hotspot and ping test.

        Steps:
        1. Turn on DUT's 2G softap
        2. Client connects to the softap
        3. Client and DUT ping each other
    """
    self.validate_full_tether_startup(
      band=constants.WiFiHotspotBand.WIFI_CONFIG_SOFTAP_BAND_2G,
      test_ping=True)

  @ApiTest(
    apis=[
      'android.net.wifi.WifiManager#isPortableHotspotSupported()',
      'android.net.ConnectivityManage#isTetheringSupported()',
      'android.net.wifi.WifiManager.getWifiState()',
      'android.net.wifi.WifiManager.setSoftApConfiguration(SoftApConfiguration.Builder())',
      'android.net.TetheringManage.startTethering(int type,'+
      ' @NonNull final Executor executor,final StartTetheringCallback callback)',
      'android.net.wifi.WifiManager.SCAN_RESULTS_AVAILABLE_ACTION',
      'android.net.wifi.WifiManager.startScan()',
      'android.net.wifi.WifiManager.getScanResults()',
      'android.net.wifi.WifiManager.addNetwork(android.net.wifi.WifiConfiguration(json))',
      'android.net.wifi.WifiManager.enableNetwork(netId, disableOthers)',
      'android.net.wifi.WifiManager.connect(android.net.wifi.WifiConfiguration(json))',
      'android.net.wifi.WifiManager.getConnectionInfo().getWifiStandard()',
      ]
  )

  def test_full_tether_startup_5G_one_client_ping_softap(self):
    """Device can connect to 5G hotspot and ping test.

        Steps:
        1. Turn on DUT's 5G softap
        2. Client connects to the softap
        3. Client and DUT ping each other
    """
    self.validate_full_tether_startup(
      band=constants.WiFiHotspotBand.WIFI_CONFIG_SOFTAP_BAND_5G,
      test_ping=True)

  def test_softap_wpa3_2g_after_reboot(self):

    """Test full startup of softap in 2G band, wpa3 security after reboot.

        Steps:
        1. Save softap in 2G band and wpa3 security.
        2. Reboot device and start softap.
        3. Verify dut client connects to the softap.
    """
    for self.ad in self.ads:
      asserts.skip_if(self.ad.model not in STA_CONCURRENCY_SUPPORTED_MODELS,
                      "DUT does not support WPA3 softAp")
    self.validate_softap_after_reboot(
      constants.WiFiHotspotBand.WIFI_CONFIG_SOFTAP_BAND_2G,
      constants.SoftApSecurityType.WPA3_SAE,
      False)

  @ApiTest(
    apis=[
      'android.net.wifi.WifiManager#isPortableHotspotSupported()',
      'android.net.ConnectivityManage#isTetheringSupported()',
      'android.net.wifi.WifiManager.getWifiState()',
      'android.net.wifi.WifiManager.setSoftApConfiguration(SoftApConfiguration.Builder())',
      'android.net.TetheringManage.startTethering(int type,'+
      ' @NonNull final Executor executor,final StartTetheringCallback callback)',
      'android.net.wifi.WifiManager.SCAN_RESULTS_AVAILABLE_ACTION',
      'android.net.wifi.WifiManager.startScan()',
      'android.net.wifi.WifiManager.getScanResults()',
      'android.net.wifi.WifiManager.addNetwork(android.net.wifi.WifiConfiguration(json))',
      'android.net.wifi.WifiManager.enableNetwork(netId, disableOthers)',
      'android.net.wifi.WifiManager.connect(android.net.wifi.WifiConfiguration(json))',
      'android.net.wifi.WifiManager.getConnectionInfo().getWifiStandard()',
      ]
  )

  def test_softap_wpa3_5g_after_reboot(self):
    """Test full startup of softap in 5G band, wpa3 security after reboot.

        Steps:
        1. Save softap in 5G band and wpa3 security.
        2. Reboot device and start softap.
        3. Verify dut client connects to the softap.
    """
    for self.ad in self.ads:
      asserts.skip_if(self.ad.model not in STA_CONCURRENCY_SUPPORTED_MODELS,
                      "DUT does not support WPA3 softAp")
    self.validate_softap_after_reboot(
      constants.WiFiHotspotBand.WIFI_CONFIG_SOFTAP_BAND_5G,
      constants.SoftApSecurityType.WPA3_SAE,
      False)

  @ApiTest(
    apis=[
      'android.net.wifi.WifiManager#isPortableHotspotSupported()',
      'android.net.ConnectivityManage#isTetheringSupported()',
      'android.net.wifi.WifiManager.getWifiState()',
      'android.net.wifi.WifiManager.setSoftApConfiguration(SoftApConfiguration.Builder())',
      'android.net.TetheringManage.startTethering(int type,'+
      ' @NonNull final Executor executor,final StartTetheringCallback callback)',
      'android.net.wifi.WifiManager.SCAN_RESULTS_AVAILABLE_ACTION',
      'android.net.wifi.WifiManager.startScan()',
      'android.net.wifi.WifiManager.getScanResults()',
      'android.net.wifi.WifiManager.addNetwork(android.net.wifi.WifiConfiguration(json))',
      'android.net.wifi.WifiManager.enableNetwork(netId, disableOthers)',
      'android.net.wifi.WifiManager.connect(android.net.wifi.WifiConfiguration(json))',
      'android.net.wifi.WifiManager.getConnectionInfo().getWifiStandard()',
      ]
  )

  def test_softap_wpa2_wpa3_2g_after_reboot(self):

    """Test full startup of softap in 2G band, wpa2/wpa3 security after reboot.

        Steps:
        1. Save softap in 2G band and wpa2/wpa3 security.
        2. Reboot device and start softap.
        3. Verify dut client connects to the softap.
    """
    for self.ad in self.ads:
      asserts.skip_if(self.ad.model not in STA_CONCURRENCY_SUPPORTED_MODELS,
                      "DUT does not support WPA3 softAp")
    self.validate_softap_after_reboot(
      constants.WiFiHotspotBand.WIFI_CONFIG_SOFTAP_BAND_2G,
      constants.SoftApSecurityType.WPA3_SAE_TRANSITION,
      False)

  @ApiTest(
    apis=[
      'android.net.wifi.WifiManager#isPortableHotspotSupported()',
      'android.net.ConnectivityManage#isTetheringSupported()',
      'android.net.wifi.WifiManager.getWifiState()',
      'android.net.wifi.WifiManager.setSoftApConfiguration(SoftApConfiguration.Builder())',
      'android.net.TetheringManage.startTethering(int type,'+
      ' @NonNull final Executor executor,final StartTetheringCallback callback)',
      'android.net.wifi.WifiManager.SCAN_RESULTS_AVAILABLE_ACTION',
      'android.net.wifi.WifiManager.startScan()',
      'android.net.wifi.WifiManager.getScanResults()',
      'android.net.wifi.WifiManager.addNetwork(android.net.wifi.WifiConfiguration(json))',
      'android.net.wifi.WifiManager.enableNetwork(netId, disableOthers)',
      'android.net.wifi.WifiManager.connect(android.net.wifi.WifiConfiguration(json))',
      'android.net.wifi.WifiManager.getConnectionInfo().getWifiStandard()',
      ]
  )

  def test_softap_wpa2_wpa3_5g_after_reboot(self):

    """Test full startup of softap in 2G band, wpa2/wpa3 security after reboot.

        Steps:
        1. Save softap in 5G band and wpa2/wpa3 security.
        2. Reboot device and start softap.
        3. Verify dut client connects to the softap.
    """
    for self.ad in self.ads:
      asserts.skip_if(self.ad.model not in STA_CONCURRENCY_SUPPORTED_MODELS,
                      "DUT does not support WPA3 softAp")
    self.validate_softap_after_reboot(
      constants.WiFiHotspotBand.WIFI_CONFIG_SOFTAP_BAND_5G,
      constants.SoftApSecurityType.WPA3_SAE_TRANSITION,
      False)

  @ApiTest(
    apis=[
      'android.net.wifi.WifiManager#isPortableHotspotSupported()',
      'android.net.ConnectivityManage#isTetheringSupported()',
      'android.net.wifi.WifiManager.getWifiState()',
      'android.net.wifi.WifiManager.setSoftApConfiguration(SoftApConfiguration.Builder())',
      'android.net.TetheringManage.startTethering(int type,'+
      ' @NonNull final Executor executor,final StartTetheringCallback callback)',
      'android.net.wifi.WifiManager.SCAN_RESULTS_AVAILABLE_ACTION',
      'android.net.wifi.WifiManager.startScan()',
      'android.net.wifi.WifiManager.getScanResults()',
      'android.net.wifi.WifiManager.addNetwork(android.net.wifi.WifiConfiguration(json))',
      'android.net.wifi.WifiManager.enableNetwork(netId, disableOthers)',
      'android.net.wifi.WifiManager.connect(android.net.wifi.WifiConfiguration(json))',
      'android.net.wifi.WifiManager.getConnectionInfo().getWifiStandard()',
      ]
  )

  def test_softap_wpa3_2g_hidden_after_reboot(self):

    """Test full startup of softap in 2G band, wpa2/wpa3 security after reboot.

        Steps:
        1. Save softap in 2G band and wpa3 security.
        2. Reboot device and start softap.
        3. Verify dut client connects to the softap.
    """
    for self.ad in self.ads:
      asserts.skip_if(self.ad.model not in STA_CONCURRENCY_SUPPORTED_MODELS,
                      "DUT does not support WPA3 softAp")
    self.validate_softap_after_reboot(
      constants.WiFiHotspotBand.WIFI_CONFIG_SOFTAP_BAND_2G,
      constants.SoftApSecurityType.WPA3_SAE,
      hidden=True)

  @ApiTest(
    apis=[
      'android.net.wifi.WifiManager#isPortableHotspotSupported()',
      'android.net.ConnectivityManage#isTetheringSupported()',
      'android.net.wifi.WifiManager.getWifiState()',
      'android.net.wifi.WifiManager.setSoftApConfiguration(SoftApConfiguration.Builder())',
      'android.net.TetheringManage.startTethering(int type,'+
      ' @NonNull final Executor executor,final StartTetheringCallback callback)',
      'android.net.wifi.WifiManager.SCAN_RESULTS_AVAILABLE_ACTION',
      'android.net.wifi.WifiManager.startScan()',
      'android.net.wifi.WifiManager.getScanResults()',
      'android.net.wifi.WifiManager.addNetwork(android.net.wifi.WifiConfiguration(json))',
      'android.net.wifi.WifiManager.enableNetwork(netId, disableOthers)',
      'android.net.wifi.WifiManager.connect(android.net.wifi.WifiConfiguration(json))',
      'android.net.wifi.WifiManager.getConnectionInfo().getWifiStandard()',
      ]
  )

  def test_softap_wpa3_5g_hidden_after_reboot(self):

    """Test full startup of softap in 5G band, wpa2/wpa3 security after reboot.

        Steps:
        1. Save softap in 5G band and wpa3 security.
        2. Reboot device and start softap.
        3. Verify dut client connects to the softap.
    """
    for self.ad in self.ads:
      asserts.skip_if(self.ad.model not in STA_CONCURRENCY_SUPPORTED_MODELS,
                      "DUT does not support WPA3 softAp")
    self.validate_softap_after_reboot(
      constants.WiFiHotspotBand.WIFI_CONFIG_SOFTAP_BAND_5G,
      constants.SoftApSecurityType.WPA3_SAE,
      hidden=True)

  @ApiTest(
    apis=[
      'android.net.wifi.WifiManager#isPortableHotspotSupported()',
      'android.net.ConnectivityManage#isTetheringSupported()',
      'android.net.wifi.WifiManager.getWifiState()',
      'android.net.wifi.WifiManager.setSoftApConfiguration(SoftApConfiguration.Builder())',
      'android.net.TetheringManage.startTethering(int type,'+
      ' @NonNull final Executor executor,final StartTetheringCallback callback)',
      'android.net.wifi.WifiManager.SCAN_RESULTS_AVAILABLE_ACTION',
      'android.net.wifi.WifiManager.startScan()',
      'android.net.wifi.WifiManager.getScanResults()',
      'android.net.wifi.WifiManager.addNetwork(android.net.wifi.WifiConfiguration(json))',
      'android.net.wifi.WifiManager.enableNetwork(netId, disableOthers)',
      'android.net.wifi.WifiManager.connect(android.net.wifi.WifiConfiguration(json))',
      'android.net.wifi.WifiManager.getConnectionInfo().getWifiStandard()',
      ]
  )

  def test_softap_wpa2_wpa3_2g_hidden_after_reboot(self):

    """Test full startup of softap in 2G band, wpa2/wpa3 security after reboot.

        Steps:
        1. Save softap in 2G band and wpa2/wpa3 security.
        2. Reboot device and start softap.
        3. Verify dut client connects to the softap.
    """
    for self.ad in self.ads:
      asserts.skip_if(self.ad.model not in STA_CONCURRENCY_SUPPORTED_MODELS,
                      "DUT does not support WPA3 softAp")
    self.validate_softap_after_reboot(
      constants.WiFiHotspotBand.WIFI_CONFIG_SOFTAP_BAND_2G,
      constants.SoftApSecurityType.WPA3_SAE_TRANSITION,
      hidden=True)

  @ApiTest(
    apis=[
      'android.net.wifi.WifiManager#isPortableHotspotSupported()',
      'android.net.ConnectivityManage#isTetheringSupported()',
      'android.net.wifi.WifiManager.getWifiState()',
      'android.net.wifi.WifiManager.setSoftApConfiguration(SoftApConfiguration.Builder())',
      'android.net.TetheringManage.startTethering(int type,'+
      ' @NonNull final Executor executor,final StartTetheringCallback callback)',
      'android.net.wifi.WifiManager.SCAN_RESULTS_AVAILABLE_ACTION',
      'android.net.wifi.WifiManager.startScan()',
      'android.net.wifi.WifiManager.getScanResults()',
      'android.net.wifi.WifiManager.addNetwork(android.net.wifi.WifiConfiguration(json))',
      'android.net.wifi.WifiManager.enableNetwork(netId, disableOthers)',
      'android.net.wifi.WifiManager.connect(android.net.wifi.WifiConfiguration(json))',
      'android.net.wifi.WifiManager.getConnectionInfo().getWifiStandard()',
      ]
  )

  def test_softap_wpa2_wpa3_5g_hidden_after_reboot(self):

    """Test full startup of softap in 5G band, wpa2/wpa3 security after reboot.

        Steps:
        1. Save softap in 5G band and wpa2/wpa3 security.
        2. Reboot device and start softap.
        3. Verify dut client connects to the softap.
    """
    for self.ad in self.ads:
      asserts.skip_if(self.ad.model not in STA_CONCURRENCY_SUPPORTED_MODELS,
                      "DUT does not support WPA3 softAp")
    self.validate_softap_after_reboot(
      constants.WiFiHotspotBand.WIFI_CONFIG_SOFTAP_BAND_5G,
      constants.SoftApSecurityType.WPA3_SAE_TRANSITION,
      hidden=True)

if __name__ == '__main__':

  test_runner.main()
