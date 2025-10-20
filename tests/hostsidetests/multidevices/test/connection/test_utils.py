"""Utility functions for general test related operations."""

from collections.abc import Callable
import datetime
import logging
import time
from typing import Any

from mobly import asserts
from mobly.controllers import android_device
from mobly.controllers.android_device_lib import apk_utils
from snippet_uiautomator import uiautomator

from connection import constants

TEST_OUTPUT_PATH: str = ''

_APK_PACKAGE = 'com.google.snippet.wifi'
_SHORT_DELAY_TIME_BETWEEN_ACTIONS = datetime.timedelta(seconds=1)


def install_and_load_wifi_mobly_snippet_and_uiautomator(
    device: android_device.AndroidDevice, user_params: dict[str, Any]
) -> None:
  """Installs wifi_mobly_snippet_and_uiautomator_apk to the Android device.

  Args:
    device: AndroidDevice instance.
    user_params: User params to get the apk path from the testbed host.
  """
  apk_path_list = user_params.get('mh_files', {}).get('wifi_mobly_snippet_apk')
  if apk_path_list and apk_path_list[0]:
    apk_path = apk_path_list[0]
    apk_utils.install(device, apk_path)
  # TODO: b/432226106 - Explicit load the snippet to avoid the issue
  # - failed to get_latest_logcat_timestamp
  # Since loading the snippet server on we own, skip the uiautomator service and
  # let snippet client handle the lifecycle.
  device.load_snippet('wifi', _APK_PACKAGE)
  device.ui = uiautomator.UiDevice(ui=device.wifi)


def wait_until_or_assert(
    condition: Callable[[], bool],
    error_msg: str,
    timeout: datetime.timedelta,
) -> None:
  """Waits until the condition is met, or asserts if timeout.

  Args:
    condition: Represents the condition to wait for.
    error_msg: The error message to be included in the assertion failure.
    timeout: The maximum time to wait for the condition to be met.

  Raises:
    mobly.signals.TestFailure: When the condition is not met within the timeout.
  """
  end_time = time.monotonic() + timeout.total_seconds()
  while time.monotonic() < end_time:
    if condition():
      return
    time.sleep(_SHORT_DELAY_TIME_BETWEEN_ACTIONS.total_seconds())
  asserts.fail(f'{error_msg} within {timeout.total_seconds()} seconds')


def set_screen_on_and_unlock(device: android_device.AndroidDevice):
  """Sets the screen to stay on and unlocks the device.

  Args:
      device: AndroidDevice instance.
  """
  device.adb.shell('input keyevent KEYCODE_WAKEUP')
  device.adb.shell('wm dismiss-keyguard')
  device.adb.shell('svc power stayon true')


def enable_wifi_verbose_logging(device: android_device.AndroidDevice):
  """Sets the Wi-Fi verbose logging developer option to Enable."""
  device.adb.shell('cmd wifi set-verbose-logging enabled')


def drop_shell_permission(
    device: android_device.AndroidDevice,
    ensure_mbs_initialized: bool = False,
):
  """Drops the shell permission.

  Several snippet classes in MBS enable shell permission in their constructors
  but aren't initialized until their first method call. To drop shell permission
  on the first attempt, set `ensure_mbs_initialized` to True. This will ensure
  the snippet classes are initialized before dropping the shell permission.

  Args:
    device: AndroidDevice instance.
    ensure_mbs_initialized: Whether to ensure MBS snippet classes are
      initialized before dropping the shell permission.
  """
  if ensure_mbs_initialized:
    # Ensure MBS WifiManagerSnippet is initialized.
    device.wifi.isWifiConnected()
  device.wifi.utilityDropShellPermission()


def logging_device_model(device: android_device.AndroidDevice) -> None:
  """Checks the device model."""
  ro_product_model = device.adb.getprop('ro.product.model').lower()
  logging.info('ro.product.model: %s', ro_product_model)


def set_location_mode_on(device: android_device.AndroidDevice):
  """Sets the location mode to on."""
  device.adb.shell(
      'settings put secure location_mode'
      f' {constants.LocationMode.LOCATION_MODE_HIGH_ACCURACY}'
  )
  location_mode = device.adb.shell('settings get secure location_mode')
  location_mode = int(location_mode.decode('utf-8').strip())
  asserts.assert_equal(
      location_mode,
      constants.LocationMode.LOCATION_MODE_HIGH_ACCURACY,
      'Failed to enable location mode.',
  )
