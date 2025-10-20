"""Utility functions for Wi-Fi tests."""

import contextlib
import datetime
import logging
import time

from mobly import asserts
from mobly.controllers import android_device
from mobly.controllers.android_device_lib import callback_handler_v2
from mobly.controllers.wifi import openwrt_device
from mobly.controllers.wifi.lib import wifi_configs
from mobly.controllers.wifi.lib.encryption import wpa
from mobly.snippet import errors

from connection import constants
from connection import test_utils
from connection import ui_action_utils


_NETWORK_CALLBACK = 'NetworkCallback'
_WIFI_CHANNEL = 11


def start_wpa2_wifi(
    openwrt: openwrt_device.OpenWrtDevice, channel: int = _WIFI_CHANNEL
) -> wifi_configs.WifiInfo:
  """Starts a WPA2-PSK-CCMP Wi-Fi AP."""
  config = wifi_configs.WiFiConfig(
      channel=channel, encryption_config=wpa.gen_config_for_wpa2_ccmp()
  )
  wifi_info = openwrt.start_wifi(config=config)
  openwrt.log.info(
      f'started a Wi-Fi AP with SSID {wifi_info.ssid}, bssid'
      f' {wifi_info.bssid} and password {wifi_info.password}.'
  )
  return wifi_info


def wait_for_expected_wifi_discovered(
    ad: android_device.AndroidDevice,
    wifi_ssid: str,
    wifi_bssid: str,
    timeout: datetime.timedelta = constants.WIFI_SCAN_TIMEOUT,
) -> None:
  """Waits for the Wi-Fi to be discovered."""
  test_utils.wait_until_or_assert(
      condition=lambda: is_wifi_discovered(ad, wifi_ssid, wifi_bssid),
      error_msg=f'Failed to discover AP wifi {wifi_ssid}.',
      timeout=timeout,
  )


def is_wifi_discovered(
    ad: android_device.AndroidDevice, wifi_ssid: str, wifi_bssid: str
) -> bool:
  """Checks if Wi-Fi is discovered."""
  for wifi in ad.wifi.wifiScanAndGetResultsWithShellPermission():
    if (
        wifi['SSID'] == wifi_ssid
        and wifi['BSSID'] == wifi_bssid
    ):
      return True
  return False


def wait_until_network_expected_callback(
    callback_handler: callback_handler_v2.CallbackHandlerV2,
    expected_callback: str,
    timeout: datetime.timedelta = constants.REQUEST_NETWORK_TIMEOUT,
) -> None:
  """Waits for the network to be expected callback.

  Args:
    callback_handler: A network callback handler.
    expected_callback: An expected callback in constants.NetworkCallback.
    timeout: The timeout for the callback event verification.
  """
  callback_handler.waitForEvent(
      event_name=_NETWORK_CALLBACK,
      predicate=lambda e: e.data['callbackName'] == expected_callback,
      timeout=timeout.total_seconds(),
  )
  logging.info('Received event with callback: %s', expected_callback)


# slipt lost out since snippet use a different callbackName only in lost event.
def wait_until_network_lost_callback(
    callback_handler: callback_handler_v2.CallbackHandlerV2,
) -> None:
  """Waits for the network lost callback."""
  callback_handler.waitForEvent(
      event_name=constants.NetworkCallback.CALLBACK_LOST,
      predicate=lambda e: e.data['callbackName']
      == constants.NetworkCallback.LOST,
      timeout=constants.REQUEST_NETWORK_TIMEOUT.total_seconds(),
  )
  logging.info(
      'Received event with callback: %s', constants.NetworkCallback.LOST
  )


def assert_no_network_callback_received_within_timeout(
    callback_handler: callback_handler_v2.CallbackHandlerV2,
    specific_callback: str,
    timeout: datetime.timedelta = constants.WIFI_CONTINUOUSLY_CHECK_TIMEOUT,
) -> None:
  """Waits for the network to be not specific callback during within timeout."""
  # NetworkCallback class uses different callback names.
  # packages: com.google.snippet.wifi.aware.ConnectivityManagerSnippet
  if specific_callback == constants.NetworkCallback.LOST:
    event_name = constants.NetworkCallback.CALLBACK_LOST
  else:
    event_name = _NETWORK_CALLBACK
  event = None
  with contextlib.suppress(errors.CallbackHandlerTimeoutError):
    event = callback_handler.waitForEvent(
        event_name=event_name,
        predicate=lambda e: e.data['callbackName'] == specific_callback,
        timeout=timeout.total_seconds(),
    )
  asserts.assert_is_none(
      event,
      f'Network lost event was received within {timeout.total_seconds()}'
      f' seconds. Event: {event}'
  )


def add_network_suggestions_and_assert_success(
    ad: android_device.AndroidDevice,
    network_suggestions: list[dict[str, str | int | bool]],
) -> None:
  """Adds network suggestion and asserts the process is successful."""
  asserts.assert_equal(
      ad.wifi.wifiAddNetworkSuggestions(network_suggestions),
      constants.NetworkSuggestionStatus.STATUS_NETWORK_SUGGESTIONS_SUCCESS,
      f'{ad} failed to add Network suggestion to : {network_suggestions}.',
  )


def wait_for_expected_network_suggestion_listener_status(
    callback_handler: callback_handler_v2.CallbackHandlerV2,
    expected_status: int,
) -> None:
  """Waits for the network suggestion listener status to be expected."""
  _ = callback_handler.waitForEvent(
      event_name=constants.NetworkCallback.ON_USER_APPROVAL_STATUS_CHANGE,
      predicate=lambda e: e.data['UserApprovalStatus'] == expected_status,
      timeout=constants.REQUEST_NETWORK_TIMEOUT.total_seconds(),
  )


def assert_network_suggestions_are_expected(
    ad: android_device.AndroidDevice,
    expected_network_suggestions: list[dict[str, str | int | bool]],
) -> None:
  """Checks that the retrieved network suggestions match the expected list."""
  retrieved_network_suggestions = ad.wifi.wifiGetNetworkSuggestions()
  logging.debug(
      'Network suggestions - Retrieved: %s, Expected: %s',
      retrieved_network_suggestions,
      expected_network_suggestions,
  )
  asserts.assert_equal(
      len(retrieved_network_suggestions),
      len(expected_network_suggestions),
      'Retrieved list length is not equal to expected list.'
  )

  for expected, actual in zip(
      expected_network_suggestions, retrieved_network_suggestions
  ):
    asserts.assert_equal(
        expected['ssid'],
        actual['ssid'],
        '"ssid" does not match expected list.',
    )
    if 'bssid' in expected:
      asserts.assert_equal(
          expected['bssid'],
          actual['bssid'],
          '"bssid" does not match expected list.',
      )


def add_network_suggestions(
    ad: android_device.AndroidDevice,
    network_suggestions: list[dict[str, str | int | bool]],
    network_request: constants.NetworkRequest,
    hsv_output_path_when_failed: str | None = None,
) -> callback_handler_v2.CallbackHandlerV2:
  """Adds network suggestions and verify approval, asserts expected suggestions.

  Args:
    ad: An Android device.
    network_suggestions: A list of network suggestions to add.
    network_request: A network request to add.
    hsv_output_path_when_failed: Path of hsv output when failed.

  Returns:
    A network callback handler of an added networksuggestion.
  """
  if hsv_output_path_when_failed is None:
    hsv_output_path_when_failed = ad.log_path

  # Register the network callback to monitor the network status.
  network_callback = ad.wifi.connectivityRegisterNetworkCallback(
      network_request.to_dict()
  )

  # Add the network suggestion
  add_network_suggestions_and_assert_success(ad, network_suggestions)
  # Add the network suggestion user approval status listener.
  network_suggestion_listener = (
      ad.wifi.wifiAddSuggestionUserApprovalStatusListener()
  )

  # TODO: b/433456977 - Set up a unique resource-id to improve robustness.
  ui_action_utils.allow_network_suggestion_in_dialog(
      ad, hsv_output_path_when_failed
  )

  network_suggestion_listener.waitForEvent(
      event_name=constants.NetworkCallback.ON_USER_APPROVAL_STATUS_CHANGE,
      predicate=lambda e: e.data['UserApprovalStatus']
      == constants.NetworkSuggestionUserApprovalStatus.STATUS_SUGGESTION_APPROVAL_APPROVED_BY_USER,
      timeout=constants.REQUEST_NETWORK_TIMEOUT.total_seconds(),
  )

  assert_network_suggestions_are_expected(ad, network_suggestions)

  return network_callback


def assert_connecting_with_expected_connection(
    ad: android_device.AndroidDevice,
    wifi_info: wifi_configs.WifiInfo,
) -> None:
  """Checks that the connected network matches the expected Wi-Fi."""
  asserts.assert_equal(
      ad.wifi.wifiGetCurrentConnectionInfo()['ssid'],
      wifi_info.ssid,
      'The SSID of connected Wi-Fi is not expected.',
  )
  asserts.assert_equal(
      ad.wifi.wifiGetCurrentConnectionInfo()['bssid'],
      wifi_info.bssid,
      'The BSSID of connected Wi-Fi is not expected.',
  )


def wait_until_network_capability_is_as_expected(
    ad: android_device.AndroidDevice,
    network_callback: callback_handler_v2.CallbackHandlerV2,
    network_callback_id: int,
    expected_capability: constants.NetworkCapabilities,
    should_exist: bool,
    timeout: datetime.timedelta = constants.CAPABILITIES_CHANGED_FOR_METERED_TIMEOUT,
) -> None:
  """Waits until the network contains the expected capability.

  Args:
    ad: An Android device.
    network_callback: A network callback handler of an added networksuggestion.
    network_callback_id: An unique network callback id.
    expected_capability: An expected capability wants to be verified.
    should_exist: Whether the capability should exist.
    timeout: The timeout for the capability status verification.
  """
  end_time = time.monotonic() + timeout.total_seconds()

  while time.monotonic() < end_time:
    if (
        ad.wifi.connectivityHasCapability(
            network_callback_id, expected_capability.value
        )
        == should_exist
    ):
      return

    try:
      wait_until_network_expected_callback(
          network_callback,
          constants.NetworkCallback.ON_CAPABILITIES_CHANGED,
          timeout=constants.CAPABILITIES_CHANGED_TIMEOUT,
      )
    except errors.CallbackHandlerTimeoutError:
      logging.debug(
          'Timeout waiting for ON_CAPABILITIES_CHANGED in this iteration.'
      )

  asserts.fail(
      f'Failed to get network capability {expected_capability.name} to'
      f' expected: {"exist" if should_exist else "not exist"} within'
      f' {timeout.total_seconds()} seconds.'
  )


def remove_network_suggestion_and_assert_disconnection(
    ad: android_device.AndroidDevice,
    network_suggestions: list[dict[str, str | int | bool]],
    network_callback: callback_handler_v2.CallbackHandlerV2,
) -> None:
  """Removes network suggestions and verifies the network is disconnected."""
  # clear existing callback lost events.
  network_callback.getAll(event_name=constants.NetworkCallback.CALLBACK_LOST)
  # Remove the network suggestion and verify the process finished.
  asserts.assert_equal(
      ad.wifi.wifiRemoveNetworkSuggestions(network_suggestions),
      constants.NetworkSuggestionStatus.STATUS_NETWORK_SUGGESTIONS_SUCCESS,
      'Failed to remove Network suggestion',
  )
  # Verify the network is lost.
  network_callback.waitForEvent(
      event_name=constants.NetworkCallback.CALLBACK_LOST,
      predicate=lambda e: e.data['callbackName']
      == constants.NetworkCallback.LOST,
      timeout=constants.WIFI_LOST_TIMEOUT.total_seconds(),
  )
