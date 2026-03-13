"""CTS-V-Host WiFi connection tests."""

import logging

from mobly import asserts
from mobly import base_test
from mobly import test_runner
from mobly import records
from mobly.controllers import android_device
from mobly.snippet import errors

from connection import ap_helper
from connection import constants
from connection import test_utils
from connection import ui_action_utils
from connection import wifi_utils
import wifi_test_utils

_ERROR_MSG_NETWORK_CONNECT_FAILED = (
    'DUT failed to connect to Wi-Fi via network request. Please check:\n'
    '1. Verify that SSID "{wifi_ssid}" and password "{wifi_pwd}" are correct'
    ' in "WifiConnectionTestbed.yaml".\n'
    '2. Ensure there is no other Wi-Fi network sharing the same SSID but a'
    ' different password.\n'
    '3. Review device logs to determine why the DUT failed to connect to the'
    ' network.'
)

_ERROR_MSG_NETWORK_LOST = (
    'Disconnected from the network even though the request is active.'
)


class NetworkRequestFailedError(Exception):
  """Raised when the DUT failed to connect to Wi-Fi via network request."""


class NetworkRequestTests(base_test.BaseTestClass):
  """CTS-V-Host WiFi connection tests.

  Class requirements:
    1. One Android device and one AP device.
  """

  ad: android_device.AndroidDevice
  ap_helper: ap_helper.ApHelper
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
    # Make sure location mode is on before triggering any Wi-Fi scan.
    test_utils.set_location_mode_on(ad)

    # Disable wifi scan throttle.
    self._original_wifi_scan_throttle_state = None
    current_wifi_scan_throttle_state = (
        ad.wifi.wifiIsScanThrottleEnabled()
    )
    if current_wifi_scan_throttle_state:
      ad.wifi.wifiSetScanThrottleState(False)
      # Set this attribute to revert this change in teardown_class phase.
      self._original_wifi_scan_throttle_state = current_wifi_scan_throttle_state

    test_utils.logging_device_model(ad)

  def setup_class(self):
    self.ad = self.register_controller(android_device)[0]
    self._setup_android_device(self.ad)

    self.ap_helper = ap_helper.ApHelper()
    use_programmable_ap = wifi_test_utils.convert_str_to_bool(
        self.user_params.get(
            'use_programmable_ap', constants.USE_PROGRAMMABLE_AP_DEFAULT
        )
    )
    self.ap_helper.initialize(
        test_class_obj=self,
        use_programmable_ap=use_programmable_ap,
        ad=self.ad,
    )

    # request_networkid support managing multiple network sessions.
    # But we only need one wifi connection in each test
    self.request_networkid = '0'

    self._close_button_of_no_device_found_dialog = self.user_params.get(
        constants.KEY_CLOSE_BUTTON_OF_NO_DEVICE_FOUND_DIALOG, None
    )
    self._close_button_of_something_came_up_dialog = self.user_params.get(
        constants.KEY_CLOSE_BUTTON_OF_SOMETHING_CAME_UP_DIALOG, None
    )
    self._connect_button_of_network_request_dialog = self.user_params.get(
        constants.KEY_CONNECT_BUTTON_OF_NETWORK_REQUEST_DIALOG, None,
    )
    self._select_wifi_button_of_network_request_dialog = self.user_params.get(
        constants.KEY_SELECT_WIFI_BUTTON_OF_NETWORK_REQUEST_DIALOG, None
    )

  def teardown_class(self):
    if self._original_wifi_scan_throttle_state is not None:
      self.ad.wifi.wifiSetScanThrottleState(
          self._original_wifi_scan_throttle_state
      )
      self._original_wifi_scan_throttle_state = None

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
        self.ad,
        self.current_test_info.output_path,
        button_something_came_up=self._close_button_of_something_came_up_dialog,
        button_no_device_found=self._close_button_of_no_device_found_dialog,
    )
    ui_action_utils.return_home_page(self.ad)
    # set the wifi snippet to foreground
    self.ad.wifi.utilityBringToForeground()

  def teardown_test(self) -> None:
    self.ap_helper.stop_programmable_ap()
    self.ad.wifi.wifiClearConfiguredNetworks()
    self.ad.wifi.connectivityUnregisterNetwork(self.request_networkid)
    self.ad.services.create_output_excerpts_all(self.current_test_info)

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
    wifi_info = self.ap_helper.get_or_start_wifi()

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

    try:
      ui_action_utils.click_connect_in_connection_dialog(
          self.ad,
          wifi_info.ssid,
          self.current_test_info.output_path,
          self._connect_button_of_network_request_dialog,
      )
      wifi_utils.wait_until_network_expected_callback(
          network_callback, constants.NetworkCallback.ON_AVAILABLE
      )
    except (
        errors.CallbackHandlerTimeoutError, asserts.signals.TestFailure
    ) as e:
      raise NetworkRequestFailedError(
          _ERROR_MSG_NETWORK_CONNECT_FAILED.format(
              wifi_ssid=wifi_info.ssid,
              wifi_pwd=wifi_info.password,
          )
      ) from e
    logging.info('wifi network connected.')

    wifi_utils.assert_no_network_callback_received_within_timeout(
        network_callback,
        constants.NetworkCallback.LOST,
        error_msg=_ERROR_MSG_NETWORK_LOST,
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
    wifi_info = self.ap_helper.get_or_start_wifi()

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

    try:
      ui_action_utils.click_pattern_matched_wifi_in_connection_dialog(
          self.ad,
          wifi_info.ssid,
          select_button_text=self._select_wifi_button_of_network_request_dialog,
      )
      wifi_utils.wait_until_network_expected_callback(
          network_callback, constants.NetworkCallback.ON_AVAILABLE
      )
    except (
        errors.CallbackHandlerTimeoutError, asserts.signals.TestFailure
    ) as e:
      ui_action_utils.capture_hsv_snapshot(
          self.ad,
          prefix='connect_with_pattern_network_request',
          output_path=self.current_test_info.output_path,
      )
      raise NetworkRequestFailedError(
          _ERROR_MSG_NETWORK_CONNECT_FAILED.format(
              wifi_ssid=wifi_info.ssid,
              wifi_pwd=wifi_info.password,
          )
      ) from e
    logging.info('wifi network connected.')

    wifi_utils.assert_no_network_callback_received_within_timeout(
        network_callback,
        constants.NetworkCallback.LOST,
        error_msg=_ERROR_MSG_NETWORK_LOST,
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
        button_something_came_up=self._close_button_of_something_came_up_dialog,
        button_no_device_found=self._close_button_of_no_device_found_dialog,
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
    wifi_info = self.ap_helper.get_or_start_wifi()
    invalid_psk = 'invalid_psk'
    if wifi_info.password == invalid_psk:
      invalid_psk = 'invalid_psk2'

    # DUT scans for the WiFi and verify the WiFi is discovered.
    wifi_utils.wait_for_expected_wifi_discovered(
        self.ad, wifi_info.ssid, wifi_info.bssid
    )

    # Set up the network request parameters.
    network_specifier = constants.NetworkSpecifier(
        ssid=wifi_info.ssid, bssid=wifi_info.bssid, psk=invalid_psk
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
        self.ad,
        wifi_info.ssid,
        self.current_test_info.output_path,
        self._connect_button_of_network_request_dialog
    )

    wifi_utils.wait_until_network_expected_callback(
        network_callback, constants.NetworkCallback.ON_UNAVAILABLE
    )
    logging.info('wifi network is unavailable.')


if __name__ == '__main__':
  test_runner.main()

