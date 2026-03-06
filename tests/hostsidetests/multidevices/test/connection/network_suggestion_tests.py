"""CTS-V-Host WiFi network suggestion tests."""

import json
import logging

from mobly import base_test
from mobly import records
from mobly import test_runner
from mobly.controllers import android_device
from mobly.snippet import errors

from connection import ap_helper
from connection import constants
from connection import test_utils
from connection import ui_action_utils
from connection import wifi_utils
import wifi_test_utils

_ERROR_MSG_NETWORK_CONNECT_FAILED = (
    'DUT failed to connect to Wi-Fi via network suggestion. Please check:\n'
    '1. Verify that SSID "{wifi_ssid}" and password "{wifi_pwd}" are correct'
    ' in "WifiConnectionTestbed.yaml".\n'
    '2. Ensure there is no other Wi-Fi network sharing the same SSID but a'
    ' different password.\n'
    '3. Review device logs to determine why the DUT failed to connect to the'
    ' network.'
)


class NetworkSuggestionFailedError(Exception):
  """Raised when the DUT failed to connect to Wi-Fi via network suggestion."""


class NetworkSuggestionTests(base_test.BaseTestClass):
  """CTS-V-Host WiFi network suggestion tests.

  Test Preconditions:
    1. One Android device and one AP device.
    2. For all test cases, we run a snippet app in foreground calling wifi Apis.
  """

  ad: android_device.AndroidDevice
  ap_helper: ap_helper.ApHelper

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
    self._allow_button_of_adding_suggestion_dialog = self.user_params.get(
        constants.KEY_ALLOW_BUTTON_OF_ADDING_SUGGESTION_DIALOG, None
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
    # Pass an empty list to remove added all network suggestions.
    self.ad.wifi.wifiRemoveNetworkSuggestions([])
    self.ad.wifi.wifiClearConfiguredNetworks()
    self.ad.wifi.connectivityUnregisterNetwork(self.request_networkid)
    self.ad.wifi.wifiRemoveSuggestionConnectionStatusListener()
    self.ad.wifi.wifiRemoveSuggestionUserApprovalStatusListener()
    self.ad.wifi.wifiRemoveNetworkSuggestionPostConnectionReceiver()
    self.ad.services.create_output_excerpts_all(self.current_test_info)

  def on_fail(self, record: records.TestResultRecord) -> None:
    self.ad.take_bug_report(destination=self.current_test_info.output_path)

  def test_with_ssid_specified(self) -> None:
    """Tests WiFi connection with SSID suggestions.

    Test Steps:
      1. Start a Wi-Fi AP with a randomly generated SSID and BSSID.
      2. Trigger Wi-Fi scan on the Android device to discover the started Wi-Fi
      AP.
      3. Add the network suggestion with SSID specified.
      4. Simulate click operation on Android device to approve the network
      suggestion.
      5. Remove the network suggestion and verify the it is removed
      successfully.

    Expected Results:
      1. Verify the Android device should connect to the Wi-Fi AP via Network
      suggestion.
      2. Verify the Android device should disconnect to the Wi-Fi AP after
      removing the network suggestion.
    """
    wifi_info = self.ap_helper.get_or_start_wifi()

    # DUT scans for the WiFi and verify the WiFi is discovered.
    wifi_utils.wait_for_expected_wifi_discovered(
        self.ad, wifi_info.ssid, wifi_info.bssid
    )

    is_bssid_set = False
    network_suggestion = constants.NetworkSuggestion(
        ssid=wifi_info.ssid,
        psk=wifi_info.password,
        is_hidden_ssid=False,
        is_metered=False,
    )
    network_suggestion_array = [network_suggestion.to_dict()]
    network_request = constants.NetworkRequest(
        transport_type=constants.TransportType.TRANSPORT_WIFI,
    )

    # TODO: b/433456977 - Set up a unique resource-id to improve robustness.
    # Add the network suggestion
    network_callback = wifi_utils.add_network_suggestions(
        self.ad,
        network_suggestion_array,
        network_request,
        hsv_output_path_when_failed=self.current_test_info.output_path,
        allow_button_text=self._allow_button_of_adding_suggestion_dialog,
    )
    logging.info('wifi network suggestion added.')

    # Adopt shell permission to ensure the scan is triggered.
    try:
      self.ad.wifi.utilityAdoptShellPermission()
      self.ad.wifi.wifiStartScan()
      logging.info('wifi start scan finished with shell permission.')
    finally:
      self.ad.wifi.utilityDropShellPermission()

    # Verify the network is connected.
    try:
      wifi_utils.wait_until_network_expected_callback(
          network_callback, constants.NetworkCallback.ON_AVAILABLE
      )
    except errors.CallbackHandlerTimeoutError as e:
      raise NetworkSuggestionFailedError(
          _ERROR_MSG_NETWORK_CONNECT_FAILED.format(
              wifi_ssid=wifi_info.ssid,
              wifi_pwd=wifi_info.password,
          )
      ) from e
    logging.info('wifi network connected.')

    # Verify the connected network is expected Wifi network.
    wifi_utils.assert_connecting_with_expected_connection(
        self.ad, wifi_info, check_bssid=is_bssid_set
    )
    logging.info('connected network is expected %s', wifi_info.ssid)

    # Remove the network suggestion and verify the process finished.
    wifi_utils.remove_network_suggestion_and_assert_disconnection(
        self.ad, network_suggestion_array, network_callback
    )
    logging.info('wifi network suggestion and connection removed.')

  def test_with_ssid_and_bssid_specified(self) -> None:
    """Tests WiFi connection with SSID and BSSID suggestions.

    Test Steps:
      1. Start a Wi-Fi AP with a randomly generated SSID and BSSID.
      2. Trigger Wi-Fi scan on the Android device to discover the started Wi-Fi
      AP.
      3. Add the network suggestion with SSID and BSSID specified.
      4. Simulate click operation on Android device to approve the network
      suggestion.
      5. Remove the network suggestion and verify the it is removed
      successfully.

    Expected Results:
      1. Verify the Android device should connect to the Wi-Fi AP via Network
      suggestion.
      2. Verify the Android device should disconnect to the Wi-Fi AP after
      removing the network suggestion.
    """
    wifi_info = self.ap_helper.get_or_start_wifi()

    # DUT scans for the WiFi and verify the WiFi is discovered.
    wifi_utils.wait_for_expected_wifi_discovered(
        self.ad, wifi_info.ssid, wifi_info.bssid
    )

    # Set up the network suggestion parameters.
    is_bssid_set = True
    network_suggestion = constants.NetworkSuggestion(
        ssid=wifi_info.ssid,
        bssid=wifi_info.bssid,
        psk=wifi_info.password,
        is_hidden_ssid=False,
        is_metered=False,
    )
    network_suggestion_array = [network_suggestion.to_dict()]
    network_request = constants.NetworkRequest(
        transport_type=constants.TransportType.TRANSPORT_WIFI,
    )

    # TODO: b/433456977 - Set up a unique resource-id to improve robustness.
    # Add the network suggestion
    network_callback = wifi_utils.add_network_suggestions(
        self.ad,
        network_suggestion_array,
        network_request,
        hsv_output_path_when_failed=self.current_test_info.output_path,
        allow_button_text=self._allow_button_of_adding_suggestion_dialog,
    )
    logging.info('wifi network suggestion added.')

    # Adopt shell permission to ensure the scan is triggered.
    try:
      self.ad.wifi.utilityAdoptShellPermission()
      self.ad.wifi.wifiStartScan()
      logging.info('wifi start scan finished with shell permission.')
    finally:
      self.ad.wifi.utilityDropShellPermission()

    # Verify the network is connected.
    try:
      wifi_utils.wait_until_network_expected_callback(
          network_callback, constants.NetworkCallback.ON_AVAILABLE
      )
    except errors.CallbackHandlerTimeoutError as e:
      raise NetworkSuggestionFailedError(
          _ERROR_MSG_NETWORK_CONNECT_FAILED.format(
              wifi_ssid=wifi_info.ssid,
              wifi_pwd=wifi_info.password,
          )
      ) from e
    logging.info('wifi network connected.')

    # Verify the connected network is expected Wifi network.
    wifi_utils.assert_connecting_with_expected_connection(
        self.ad, wifi_info, check_bssid=is_bssid_set
    )
    logging.info('connected network is expected %s', wifi_info.ssid)

    # Remove the network suggestion and verify the process finished.
    wifi_utils.remove_network_suggestion_and_assert_disconnection(
        self.ad, network_suggestion_array, network_callback
    )
    logging.info('wifi network suggestion and connection removed.')

  def test_with_ssid_and_post_connect_broadcast(self) -> None:
    """Tests WiFi connection with SSID and post connect broadcast.

    Test Steps:
      1. Start a Wi-Fi AP with a randomly generated SSID and BSSID.
      2. Trigger Wi-Fi scan on the Android device to discover the started Wi-Fi
      AP.
      3. Add the network suggestion with SSID and BSSID and
      is_app_interaction_required=true.
      4. Simulate click operation on Android device to approve the network
      suggestion.
      5. Verify the post connect broadcast is received.
      6. Check the connected network with expected SSID.
      7. Remove the network suggestion and verify the it is removed
      successfully.

    Expected Results:
      1. Verify the Android device should connect to the Wi-Fi AP via Network
      suggestion and post connect broadcast is received.
      2. Verify the Android device should disconnect to the Wi-Fi AP after
      removing the network suggestion.
    """
    wifi_info = self.ap_helper.get_or_start_wifi()

    # DUT scans for the WiFi and verify the WiFi is discovered.
    wifi_utils.wait_for_expected_wifi_discovered(
        self.ad, wifi_info.ssid, wifi_info.bssid
    )

    # Set up the network suggestion parameters.
    is_bssid_set = False
    network_suggestion = constants.NetworkSuggestion(
        ssid=wifi_info.ssid,
        psk=wifi_info.password,
        is_hidden_ssid=False,
        is_metered=False,
        is_app_interaction_required=True,
    )
    network_suggestion_array = [network_suggestion.to_dict()]
    network_request = constants.NetworkRequest(
        transport_type=constants.TransportType.TRANSPORT_WIFI,
    )

    # Add the network suggestion post connection broadcast receiver.
    broadcast_receiver_callback = (
        self.ad.wifi.wifiAddNetworkSuggestionPostConnectionReceiver()
    )
    logging.info('Launch a broadcast receiver.')

    # TODO: b/433456977 - Set up a unique resource-id to improve robustness.
    # Add the network suggestion and verify the it is added successfully.
    network_callback = wifi_utils.add_network_suggestions(
        self.ad,
        network_suggestion_array,
        network_request,
        hsv_output_path_when_failed=self.current_test_info.output_path,
        allow_button_text=self._allow_button_of_adding_suggestion_dialog,
    )
    logging.info('wifi network suggestion added.')

    # Adopt shell permission to ensure the scan is triggered.
    try:
      self.ad.wifi.utilityAdoptShellPermission()
      self.ad.wifi.wifiStartScan()
      logging.info('wifi start scan finished with shell permission.')
    finally:
      self.ad.wifi.utilityDropShellPermission()

    # Verify the network is connected.
    try:
      wifi_utils.wait_until_network_expected_callback(
          network_callback, constants.NetworkCallback.ON_AVAILABLE
      )
    except errors.CallbackHandlerTimeoutError as e:
      raise NetworkSuggestionFailedError(
          _ERROR_MSG_NETWORK_CONNECT_FAILED.format(
              wifi_ssid=wifi_info.ssid,
              wifi_pwd=wifi_info.password,
          )
      ) from e
    logging.info('wifi network connected.')

    # Verify the post connect broadcast is received.
    broadcast_receiver_callback.waitAndGet(
        event_name=constants.WifiManagerConstants.ACTION_WIFI_NETWORK_SUGGESTION_POST_CONNECTION,
        timeout=constants.POST_CONNECT_BROADCAST_TIMEOUT.total_seconds(),
    )
    logging.info('post connect broadcast is received.')

    # Verify the connected network with expected Wifi network.
    wifi_utils.assert_connecting_with_expected_connection(
        self.ad, wifi_info, check_bssid=is_bssid_set
    )
    logging.info('connected network is expected %s', wifi_info.ssid)

    # Remove the network suggestion and verify the process finished.
    wifi_utils.remove_network_suggestion_and_assert_disconnection(
        self.ad, network_suggestion_array, network_callback
    )
    logging.info('wifi network suggestion and connection removed.')

  def test_with_connection_failure(self) -> None:
    """Tests WiFi connection with connection failure.

    Test Steps:
      1. Start a Wi-Fi AP with a randomly generated parameters and invalid PSK.
      2. Trigger Wi-Fi scan on the Android device to discover the started Wi-Fi
      AP.
      3. Add the network suggestion with invalid PSK.
      4. Simulate click operation on Android device to approve the network
      suggestion.
      5. Verify the network is not connected.

    Expected Results:
      1. Verify the Android device failed to connect to the Wi-Fi AP due to
      failed authentication.
    """
    wifi_info = self.ap_helper.get_or_start_wifi()
    invalid_psk = 'invalid_psk'
    if wifi_info.password == invalid_psk:
      invalid_psk = 'invalid_psk2'

    # DUT scans for the WiFi and verify the WiFi is discovered.
    wifi_utils.wait_for_expected_wifi_discovered(
        self.ad, wifi_info.ssid, wifi_info.bssid
    )

    network_suggestion = constants.NetworkSuggestion(
        ssid=wifi_info.ssid,
        psk=invalid_psk,
        is_hidden_ssid=False,
        is_metered=False,
    )
    network_suggestion_array = [network_suggestion.to_dict()]

    network_request = constants.NetworkRequest(
        transport_type=constants.TransportType.TRANSPORT_WIFI,
    )

    # Add the network suggestion connection status listener.
    connection_status_listener = (
        self.ad.wifi.wifiAddSuggestionConnectionStatusListener()
    )

    # TODO: b/433456977 - Set up a unique resource-id to improve robustness.
    # Add the network suggestion and verify the process is successful.
    network_callback = wifi_utils.add_network_suggestions(
        self.ad,
        network_suggestion_array,
        network_request,
        hsv_output_path_when_failed=self.current_test_info.output_path,
        allow_button_text=self._allow_button_of_adding_suggestion_dialog,
    )
    logging.info('wifi network suggestion added.')

    # Adopt shell permission to ensure the scan is triggered.
    try:
      self.ad.wifi.utilityAdoptShellPermission()
      self.ad.wifi.wifiStartScan()
      logging.info('wifi start scan finished with shell permission.')
    finally:
      self.ad.wifi.utilityDropShellPermission()

    # Verify the network is not connected.
    wifi_utils.assert_no_network_callback_received_within_timeout(
        network_callback,
        constants.NetworkCallback.ON_AVAILABLE,
    )
    logging.info('wifi network not connected.')

    def _is_connection_failure_authentication(event):
      suggestion_data = json.loads(event.data['Suggestion'])
      return (
          event.data['ConnectionStatus']
          == constants.NetworkSuggestionConnectionStatus.STATUS_SUGGESTION_CONNECTION_FAILURE_AUTHENTICATION
          and suggestion_data['ssid'] == wifi_info.ssid
      )

    # Verify the network connection status is failed authentication.
    connection_status_listener.waitForEvent(
        event_name=constants.NetworkCallback.ON_CONNECTION_STATUS,
        predicate=_is_connection_failure_authentication,
        timeout=constants.WIFI_EXPECTED_UNCONNECTION_TIMEOUT.total_seconds(),
    )
    logging.info('network connection status is failed authentication.')

    # Remove the network suggestion, but do not check onLost callback.
    wifi_utils.remove_network_suggestion_and_assert_disconnection(
        self.ad,
        network_suggestion_array,
        network_callback,
        check_on_lost_callback=False,
    )

  def test_that_suggestion_modification_in_place(self) -> None:
    """Tests WiFi connection suggestion modification in place.

    Test Steps:
      1. Start a Wi-Fi AP with a randomly generated parameters.
      2. Trigger Wi-Fi scan on the Android device to discover the started Wi-Fi
      AP.
      3. Add the network suggestion and set is_metered to false.
      4. Simulate click operation on Android device to approve the network
      suggestion.
      5. Verify the network is connected.
      6. Verify the Network Capability NET_CAPABILITY_NOT_METERED exists
      7. Modify the network suggestion and set is_metered to true.
      8. Verify the Network Capability NET_CAPABILITY_NOT_METERED not exists.
      9. Remove the network suggestion and verify the process finished.
      10. Verify the network is lost.

    Expected Results:
      1. Verify the Android device should connect to the Wi-Fi AP via network
      suggestion.
      2. Verify the Network Capability NET_CAPABILITY_NOT_METERED exists.
      3. Verify the Network Capability NET_CAPABILITY_NOT_METERED not exists
      after modifying the network suggestion.
      4. Verify the Android device should disconnect to the Wi-Fi AP after
      removing the network suggestion.
    """
    wifi_info = self.ap_helper.get_or_start_wifi()

    # DUT scans for the WiFi and verify the WiFi is discovered.
    wifi_utils.wait_for_expected_wifi_discovered(
        self.ad, wifi_info.ssid, wifi_info.bssid
    )

    # Set up the network suggestion parameters.
    is_bssid_set = False
    network_suggestion = constants.NetworkSuggestion(
        ssid=wifi_info.ssid,
        psk=wifi_info.password,
        is_hidden_ssid=False,
        is_metered=False,
    )
    network_suggestion_array = [network_suggestion.to_dict()]
    network_request = constants.NetworkRequest(
        transport_type=constants.TransportType.TRANSPORT_WIFI,
    )

    # TODO: b/433456977 - Set up a unique resource-id to improve robustness.
    # Add the network suggestion and verify the it is added successfully.
    network_callback = wifi_utils.add_network_suggestions(
        self.ad,
        network_suggestion_array,
        network_request,
        hsv_output_path_when_failed=self.current_test_info.output_path,
        allow_button_text=self._allow_button_of_adding_suggestion_dialog,
    )
    logging.info('wifi network suggestion added.')

    # Adopt shell permission to ensure the scan is triggered.
    try:
      self.ad.wifi.utilityAdoptShellPermission()
      self.ad.wifi.wifiStartScan()
      logging.info('wifi start scan finished with shell permission.')
    finally:
      self.ad.wifi.utilityDropShellPermission()

    network_callback_id = network_callback.callback_id
    # Verify the network is connected.
    try:
      wifi_utils.wait_until_network_expected_callback(
          network_callback, constants.NetworkCallback.ON_AVAILABLE
      )
    except errors.CallbackHandlerTimeoutError as e:
      raise NetworkSuggestionFailedError(
          _ERROR_MSG_NETWORK_CONNECT_FAILED.format(
              wifi_ssid=wifi_info.ssid,
              wifi_pwd=wifi_info.password,
          )
      ) from e
    logging.info('wifi network connected.')

    # Verify the connected network is expected Wifi network.
    wifi_utils.assert_connecting_with_expected_connection(
        self.ad, wifi_info, check_bssid=is_bssid_set
    )
    logging.info('connected network is expected %s', wifi_info.ssid)

    # Verify the specific network capability exists.
    wifi_utils.wait_until_network_capability_is_as_expected(
        self.ad,
        network_callback,
        network_callback_id,
        constants.NetworkCapabilities.NET_CAPABILITY_NOT_METERED,
        should_exist=True,
    )

    # Modify the network suggestion
    network_suggestion.is_metered = True
    network_suggestion_array = [network_suggestion.to_dict()]
    wifi_utils.add_network_suggestions_and_assert_success(
        self.ad, network_suggestion_array
    )
    logging.info(
        'Added a network suggestion with is_metered capability changed.'
    )

    # Verify the specific network capability is not existed.
    wifi_utils.wait_until_network_capability_is_as_expected(
        self.ad,
        network_callback,
        network_callback_id,
        constants.NetworkCapabilities.NET_CAPABILITY_NOT_METERED,
        should_exist=False,
    )

    # Verify the network capabilities are changed.
    wifi_utils.wait_until_network_expected_callback(
        network_callback, constants.NetworkCallback.ON_CAPABILITIES_CHANGED
    )

    # Remove the network suggestion and verify the process finished.
    wifi_utils.remove_network_suggestion_and_assert_disconnection(
        self.ad, network_suggestion_array, network_callback
    )


if __name__ == '__main__':
  test_runner.main()
