"""Utils for UI actions."""

import datetime
import logging
import os
import time

from mobly import asserts
from mobly.controllers import android_device
from snippet_uiautomator import errors

from connection import constants

_UI_OPERATION_TIMEOUT = datetime.timedelta(seconds=10)
_UI_RESPONGE_TIMEOUT = datetime.timedelta(seconds=3)


def click_connect_in_connection_dialog(
    device: android_device.AndroidDevice,
    ssid: str,
    hsv_output_path_when_failed: str | None = None,
    connect_button_text: str | None = None,
) -> None:
  """Clicks Connect in wifi connection dialog.

  go/hsv/6077908610187264 is an example showing the dialog this method handles.
  """
  # TODO: b/433456977 - Set up a unique resource-id to improve robustness.
  connection_dialog = device.ui(text=ssid)
  try:
    asserts.assert_true(
        connection_dialog.wait.exists(_UI_OPERATION_TIMEOUT),
        msg='Failed to find wifi connection dialog',
    )
    connect_button_text = connect_button_text or 'Connect'
    asserts.assert_true(
        device.ui(text=connect_button_text).click(),
        msg='Failed to click the connect button in wifi connection dialog',
    )
  except (errors.BaseError, asserts.signals.TestFailure):
    capture_hsv_snapshot(
        device,
        prefix='wifi_connection_dialog',
        output_path=hsv_output_path_when_failed,
    )
    raise


def click_pattern_matched_wifi_in_connection_dialog(
    device: android_device.AndroidDevice,
    ssid: str,
    select_button_text: str | None = None,
) -> None:
  """Clicks pattern matched wifi in connection dialog.

  go/hsv/4632544036257792 and go/hsv/6467167670239232 are examples showing the
  dialog this method handles.
  """
  pattern_matched_wifi = device.ui(text=ssid)
  asserts.assert_true(
      pattern_matched_wifi.wait.exists(_UI_OPERATION_TIMEOUT),
      msg='Failed to find pattern matched wifi connection dialog',
  )
  click_success = False
  if select_button_text:
    click_success = device.ui(text=select_button_text).click()
  else:
    click_success = pattern_matched_wifi.click()
    # Try clicking `Connect` to handle OEM UI customization. We don't have a
    # better way besides having a try since:
    # 1. `click_success` is True even when clicking it does not take effect.
    # 2. We cannot wait too long to check whether `text=ssid` element disappears
    #    since there's a short timeout for user to respond to this dialog.
    click_success |= device.ui(text='Connect').click()
  asserts.assert_true(
      click_success,
      msg='Failed to select matched Wi-Fi when using a pattern network request.'
  )


def open_notification_bar(device: android_device.AndroidDevice) -> None:
  """Opens notification bar."""
  device.adb.shell('service call statusbar 1')


def allow_network_suggestion_in_dialog(
    device: android_device.AndroidDevice,
    hsv_output_path_when_failed: str | None = None,
    allow_button_text: str | None = None,
) -> None:
  """Allows network suggestion in dialog.

  go/hsv/4858200577802240 is an example showing the dialog this method handles.
  """
  allow_button_text = allow_button_text or 'Allow'
  try:
    asserts.assert_true(
        device.ui(textContains=allow_button_text).wait.exists(
            constants.CALLBACK_TIMEOUT
        ),
        msg='Failed to find network suggestion in dialog',
    )
    device.ui(text=allow_button_text).click()
  except (errors.BaseError, asserts.signals.TestFailure):
    capture_hsv_snapshot(
        device,
        prefix='allow_adding_network_suggestion',
        output_path=hsv_output_path_when_failed,
    )
    raise


def return_home_page(device: android_device.AndroidDevice) -> None:
  """Exit Setting windows and return to home screen."""
  device.adb.shell('input keyevent 3')
  # Add a delay to wait return to home animation finished.
  time.sleep(_UI_RESPONGE_TIMEOUT.total_seconds())


def close_failed_to_connect_wifi_dialog(
    device: android_device.AndroidDevice,
    hsv_output_path_when_failed: str | None = None,
    button_no_device_found: str | None = None,
    button_something_came_up: str | None = None,
) -> None:
  """Closes failed to connect wifi dialog."""
  if device.ui(textContains='No devices found.').wait.exists(
      _UI_OPERATION_TIMEOUT
  ):
    capture_hsv_snapshot(
        device,
        prefix='No devices found.',
        output_path=hsv_output_path_when_failed,
    )
    text = button_no_device_found or 'Cancel'
    device.ui(text=text).click()
  if device.ui(textContains='Something came up.').wait.exists(
      _UI_OPERATION_TIMEOUT
  ):
    capture_hsv_snapshot(
        device,
        prefix='Something came up',
        output_path=hsv_output_path_when_failed,
    )
    if button_something_came_up:
      device.ui(textMatches=button_something_came_up).click()
    else:
      device.ui(textMatches='Cancel|OK').click()


def capture_hsv_snapshot(
    device: android_device.AndroidDevice,
    prefix: str,
    output_path: str | None = None,
) -> None:
  """Captures go/hsv device snapshots.

  Note: Assumes the uiautomator is loaded.

  Saving HSV file and screenshot in the sponge artifacts.
  More information at go/hsv-readme

  Args:
    device: Android device to have the HSV snapshot captured.
    prefix: Name of the HSV snapshot file.
    output_path: Path to the log directory.
  """
  if output_path is None:
    output_path = device.log_path

  hierarchy = device.ui.dump()

  # Take a screenshot equiped with hierarchy.
  device.take_screenshot(output_path, prefix=prefix)

  hsv_file_name = device.generate_filename(
      file_type='hsv', extension_name='xml'
  )

  # Write hierarchy to xml file.
  with open(
      os.path.join(output_path, hsv_file_name), 'w', encoding='utf8'
  ) as f:
    print(hierarchy, file=f)
  logging.info('UI hierarchy saved to: %s', hsv_file_name)
