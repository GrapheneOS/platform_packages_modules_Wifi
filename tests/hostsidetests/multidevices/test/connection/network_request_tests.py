"""CTS-V-Host WiFi connection tests."""

import logging
from typing import override

from mobly import base_test
from mobly import test_runner
from mobly import records
from mobly.controllers import android_device

from mobly.controllers.wifi import openwrt_device
from mobly.controllers.wifi.lib import wifi_configs

from connection import constants
from connection import test_utils
from connection import ui_action_utils
from connection import wifi_utils


class NetworkRequestTests(base_test.BaseTestClass):
  """CTS-V-Host WiFi connection tests.

  Class requirements:
    1. One Android device and one AP device.
  """

  openwrt: openwrt_device.OpenWrtDevice
  ad: android_device.AndroidDevice
  wifi_info: wifi_configs.WiFiConfig | None
  request_networkid: str

  _original_wifi_scan_throttle_state: bool | None = None

  def _setup_android_device(self, ad: android_device.AndroidDevice) -> None:
    """Sets up the Android device."""
    test_utils.install_and_load_wifi_mobly_snippet_and_uiautomator(
        ad, self.user_params
    )
    # set the wifi snippet to foreground
    self.ad.wifi.utilityBringToForeground()
    test_utils.drop_shell_permission(ad, ensure_mbs_initialized=True)
    test_utils.enable_wifi_verbose_logging(ad)
    test_utils.set_screen_on_and_unlock(ad)

    # Disable wifi scan throttle.
    self._original_wifi_scan_throttle_state = None
    current_wifi_scan_throttle_state = (
        ad.wifi.wifiIsScanThrottleEnabled()
    )
    if current_wifi_scan_throttle_state:
      ad.wifi.wifiSetScanThrottleState(False)
      # Set this attribute to revert this change in teardown_class phase.
      self._original_wifi_scan_throttle_state = current_wifi_scan_throttle_state

  @override
  def setup_class(self):
    self.openwrt = self.register_controller(openwrt_device)[0]
    # AP setup steps.
    if self.user_params.get('reboot_ap', 'false') == 'true':
      self.openwrt.reboot()
    self.ad = self.register_controller(android_device)[0]
    self._setup_android_device(self.ad)
    # request_networkid support managing multiple network sessions.
    # But we only need one wifi connection in each test
    self.request_networkid = '0'
    test_utils.logging_device_model(self.ad)

  @override
  def teardown_class(self):
    if self._original_wifi_scan_throttle_state is not None:
      self.ad.wifi.wifiSetScanThrottleState(
          self._original_wifi_scan_throttle_state
      )
      self._original_wifi_scan_throttle_state = None

  @override
  def setup_test(self) -> None:
    self.ad.wifi.wifiFactoryReset()
    self.ad.wifi.wifiToggleEnable()
    self.record_data({
        'Test Name': self.current_test_info.name,
        'sponge_properties': {
            'beto_team': 'Wi-Fi',
            'beto_feature': 'Wi-Fi',
        },
    })
    # Close failed to connect wifi dialog to avoid blocking the test.
    ui_action_utils.close_failed_to_connect_wifi_dialog(
        self.ad, self.current_test_info.output_path,
    )
    ui_action_utils.return_home_page(self.ad)
    # set the wifi snippet to foreground
    self.ad.wifi.utilityBringToForeground()

  @override
  def teardown_test(self) -> None:
    self.openwrt.stop_all_wifi()
    self.ad.wifi.wifiClearConfiguredNetworks()
    self.ad.wifi.connectivityUnregisterNetwork(self.request_networkid)
    self.ad.services.create_output_excerpts_all(self.current_test_info)

  @override
  def on_fail(self, record: records.TestResultRecord) -> None:
    self.ad.take_bug_report(destination=self.current_test_info.output_path)

  def test_with_a_specific_ssid_and_bssid(self) -> None:
    """Tests WiFi connection with a specific SSID and BSSID.

    Test Preconditions:
      1. One Android device and one AP device.

    Test Steps:
      1. Start a Wi-Fi AP with a randomly generated SSID and BSSID.
      2. Trigger Wi-Fi scan on the Android device to discover the started Wi-Fi
      AP.
      3. Connects to the Wi-Fi AP.
      4. Verify Network become onAvailable and not lost in 40 seconds.

    Expected Results:
      1. The Android device should discover the Wi-Fi AP.
      2. The Android device should connect to the Wi-Fi AP.
      3. Network should be connected and not lost in 40 seconds.
    """
    wifi_info = wifi_utils.start_wpa2_wifi(self.openwrt)

    # DUT scans for the WiFi and verify the WiFi is discovered.
    wifi_utils.wait_for_expected_wifi_discovered(
        self.ad, wifi_info.ssid, wifi_info.bssid
    )

    # Set up the network request parameters.
    network_specifier = constants.NetworkSpecifier(
        ssid=wifi_info.ssid, bssid=wifi_info.bssid, psk=wifi_info.password
    )
    network_request = constants.NetworkRequest(
        network_specifier=network_specifier,
        remove_capability=constants.NetworkCapabilities.NET_CAPABILITY_INTERNET,
        transport_type=constants.TransportType.TRANSPORT_WIFI,
    )

    # Request the network and verify the network is available and not lost.
    network_callback = self.ad.wifi.connectivityRequestNetwork(
        self.request_networkid,
        network_request.to_dict(),
        constants.REQUEST_NETWORK_TIMEOUT_MS,
    )
    logging.info('Request a network with network specifier.')

    # TODO: b/433456977 - Set up a unique resource-id to improve robustness.
    ui_action_utils.click_connect_in_connection_dialog(
        self.ad, wifi_info.ssid, self.current_test_info.output_path,
    )
    wifi_utils.wait_until_network_expected_callback(
        network_callback, constants.NetworkCallback.ON_AVAILABLE
    )
    logging.info('wifi network connected.')

    wifi_utils.assert_no_network_callback_received_within_timeout(
        network_callback,
        constants.NetworkCallback.LOST,
    )
    logging.info(
        'wifi network not lost within %s seconds.',
        constants.WIFI_CONTINUOUSLY_CHECK_TIMEOUT,
    )

  def test_with_pattern_network_specifier(self) -> None:
    """Tests WiFi connection with a pattern network specifier.

    Test Steps:
      1. Start a Wi-Fi AP with a randomly generated SSID and BSSID.
      2. Trigger Wi-Fi scan on the Android device to discover the started Wi-Fi
      AP.
      3. Connects to the Wi-Fi AP with pattern network specifier.

    Expected Results:
      1. The Android device should discover the Wi-Fi AP.
      2. The Android device should connect to the Wi-Fi AP.
    """
    wifi_info = wifi_utils.start_wpa2_wifi(self.openwrt)

    # DUT scans for the WiFi and verify the Wifi is discovered.
    wifi_utils.wait_for_expected_wifi_discovered(
        self.ad, wifi_info.ssid, wifi_info.bssid
    )

    ssid_pattern = constants.PatternMatcher(
        pattern=wifi_info.ssid[:-1],
        pattern_type=constants.PatternType.PATTERN_PREFIX,
    )
    bssid_pattern = constants.BssidPattern(
        bssid=wifi_info.bssid,
        bssid_mask=constants.BSSID_MASK,
    )
    network_specifier_pattern = constants.NetworkSpecifier(
        ssid_pattern=ssid_pattern,
        bssid_pattern=bssid_pattern,
        psk=wifi_info.password,
    )
    network_request = constants.NetworkRequest(
        network_specifier=network_specifier_pattern,
        remove_capability=constants.NetworkCapabilities.NET_CAPABILITY_INTERNET,
        transport_type=constants.TransportType.TRANSPORT_WIFI,
    )
    # Request a network with network specifier pattern.
    network_callback = self.ad.wifi.connectivityRequestNetwork(
        self.request_networkid,
        network_request.to_dict(),
        constants.REQUEST_NETWORK_TIMEOUT_MS,
    )
    logging.info('Request a network with network specifier pattern.')

    # TODO: b/433456977 - Set up a unique resource-id to improve robustness.
    ui_action_utils.click_pattern_matched_wifi_in_connection_dialog(
        self.ad, wifi_info.ssid, self.current_test_info.output_path,
    )

    wifi_utils.wait_until_network_expected_callback(
        network_callback, constants.NetworkCallback.ON_AVAILABLE
    )
    logging.info('wifi network connected.')

    wifi_utils.assert_no_network_callback_received_within_timeout(
        network_callback,
        constants.NetworkCallback.LOST,
    )
    logging.info(
        'wifi network not lost within %s seconds.',
        constants.WIFI_CONTINUOUSLY_CHECK_TIMEOUT,
    )

  def test_with_unavailable_network_specifier(self) -> None:
    """Tests WiFi connection with SSID that is not in scan results.

    Test Steps:
      1. Connects to the Wi-Fi AP with invalid SSID and BSSID.

    Expected Results:
      1. The Android device failed to connect to the Wi-Fi AP.
    """
    invalid_ssid = 'invalid_ssid'
    invalid_bssid = '02:00:00:00:00:00'
    network_specifier = constants.NetworkSpecifier(
        ssid=invalid_ssid, bssid=invalid_bssid
    )
    network_request = constants.NetworkRequest(
        network_specifier=network_specifier,
        remove_capability=constants.NetworkCapabilities.NET_CAPABILITY_INTERNET,
        transport_type=constants.TransportType.TRANSPORT_WIFI,
    )
    # Request a network with invalid network_specifier.
    network_callback = self.ad.wifi.connectivityRequestNetwork(
        self.request_networkid,
        network_request.to_dict(),
        constants.WIFI_EXPECTED_UNCONNECTION_TIMEOUT_MS,
    )
    logging.info('Request a network with invalid network specifier.')

    # Verify the network is unavailable.
    wifi_utils.wait_until_network_expected_callback(
        network_callback, constants.NetworkCallback.ON_UNAVAILABLE
    )
    logging.info('wifi network is unavailable.')

    # Close failed to find wifi dialog.
    ui_action_utils.close_failed_to_connect_wifi_dialog(
        self.ad,
        self.current_test_info.output_path,
    )

  def test_with_invalid_credential_in_network_specifier(self) -> None:
    """Tests WiFi connection with a wrong credential in network specifier.

    Test Preconditions:
      1. One Android device and one AP device.

    Test Steps:
      1. Start a Wi-Fi AP with a randomly generated SSID and BSSID.
      2. Trigger Wi-Fi scan on the Android device to discover the started Wi-Fi
      AP.
      3. Connects to the Wi-Fi AP with a wrong password.

    Expected Results:
      1. Android device should discover the Wi-Fi AP.
      2. Android device failed to connect to the Wi-Fi AP.
    """
    wifi_info = wifi_utils.start_wpa2_wifi(self.openwrt)

    # DUT scans for the WiFi and verify the WiFi is discovered.
    wifi_utils.wait_for_expected_wifi_discovered(
        self.ad, wifi_info.ssid, wifi_info.bssid
    )

    wrong_password = 'wrong_password'
    # Set up the network request parameters.
    network_specifier = constants.NetworkSpecifier(
        ssid=wifi_info.ssid, bssid=wifi_info.bssid, psk=wrong_password
    )
    network_request = constants.NetworkRequest(
        network_specifier=network_specifier,
        remove_capability=constants.NetworkCapabilities.NET_CAPABILITY_INTERNET,
        transport_type=constants.TransportType.TRANSPORT_WIFI,
    )
    network_callback = self.ad.wifi.connectivityRequestNetwork(
        self.request_networkid,
        network_request.to_dict(),
        constants.WIFI_EXPECTED_UNCONNECTION_TIMEOUT_MS,
    )
    logging.info('Request a network with invalid credential.')

    # TODO: b/433456977 - Set up a unique resource-id to improve robustness.
    ui_action_utils.click_connect_in_connection_dialog(
        self.ad, wifi_info.ssid, self.current_test_info.output_path,
    )

    wifi_utils.wait_until_network_expected_callback(
        network_callback, constants.NetworkCallback.ON_UNAVAILABLE
    )
    logging.info('wifi network is unavailable.')


if __name__ == '__main__':
  test_runner.main()

