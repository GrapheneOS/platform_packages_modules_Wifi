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

class WifiSoftApThreeDevicesTest(base_test.BaseTestClass):
  """SoftAp with multi-devices test class.

  Attributes:
    host: Android device providing Wi-Fi hotspot
    client: Android device connecting to host provided hotspot
  """

  def setup_class(self) -> None:
    self.ads = self.register_controller(android_device, min_number=3)
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

  def validate_full_tether_startup(self, band=None, hidden=False,
                                     test_ping=False, test_clients=None,
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
            test_clients (bool, optional):
              A boolean indicating whether to perform a ping test between
                two connected client devices. Requires at least
                two client devices to be associated with `self.ads
                (excluding the host). Defaults to None.
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
    if test_clients:
      if len(self.ads) > 2:
        self.validate_ping_between_two_clients(config)
    sutils._stop_tethering(self.host)
    asserts.assert_false(self.host.wifi.wifiIsApEnabled(),
                         "SoftAp is still reported as running")
    if initial_wifi_state:
      sutils.wait_for_wifi_state(self.host, True)
    elif self.host.wifi.wifiCheckState():
      asserts.fail("Wifi was disabled before softap and now it is enabled")

  def validate_ping_between_two_clients(self, config):
    """Test ping between softap's clients.

        Connect two android device to the wifi hotspot.
        Verify the clients can ping each other.

        Args:
            config: wifi network config with SSID, password
    """
    ad1 = self.client
    ad2 = self.ads[2]
    sutils._wifi_connect(ad1, config, check_connectivity=False)
    sutils._wifi_connect(ad2, config, check_connectivity=False)
    ad1_ip = ad1.wifi.connectivityGetIPv4Addresses('wlan0')[0]
    ad2_ip = ad2.wifi.connectivityGetIPv4Addresses('wlan0')[0]


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

  def test_softap_2G_two_clients_ping_each_other(self):

    """Test for 2G hotspot with 2 clients

        1. Turn on 2G hotspot
        2. Two clients connect to the hotspot
        3. Two clients ping each other
    """
    self.validate_full_tether_startup(
      constants.WiFiHotspotBand.WIFI_CONFIG_SOFTAP_BAND_2G, test_clients=True)

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

  def test_softap_5G_two_clients_ping_each_other(self):
    """Test for 5G hotspot with 2 clients

        1. Turn on 5G hotspot
        2. Two clients connect to the hotspot
        3. Two clients ping each other
    """
    self.validate_full_tether_startup(
      constants.WiFiHotspotBand.WIFI_CONFIG_SOFTAP_BAND_5G, test_clients=True)


if __name__ == '__main__':
  test_runner.main()

