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
import os
import logging
import re
import time

from mobly import asserts
from mobly.controllers import android_device

_MAINLINE_MODULE_VERSION_REGEX = re.compile(
    r'package:(?P<package>[\S]+) versionCode:(?P<version>\d+)'
)


def set_screen_on_and_unlock(ad: android_device.AndroidDevice):
    """Sets the screen to stay on and unlocks the device.

    Args:
        ad: AndroidDevice instance.
    """
    ad.adb.shell("input keyevent KEYCODE_WAKEUP")
    ad.adb.shell("wm dismiss-keyguard")
    ad.adb.shell("svc power stayon true")

def enable_wifi_verbose_logging(ad: android_device.AndroidDevice):
    """Sets the Wi-Fi verbose logging developer option to Enable."""
    ad.adb.shell('cmd wifi set-verbose-logging enabled')

def take_bug_reports(ads, test_name=None, begin_time=None, destination=None):
    logging.info('Collecting bugreports...')
    android_device.take_bug_reports(ads, destination=destination)

def restart_wifi_and_disable_connection_scan(ad: android_device.AndroidDevice):
    ad.wifi.wifiDisableAllSavedNetworks()
    ad.wifi.wifiDisable()
    ad.wifi.wifiEnable()
    ad.wifi.wifiAllowAutojoinGlobal(False)

def restore_wifi_auto_join(ad: android_device.AndroidDevice):
    ad.wifi.wifiEnableAllSavedNetworks()
    ad.wifi.wifiAllowAutojoinGlobal(True)

def skip_if_not_meet_min_sdk_level(
    ad: android_device.AndroidDevice, min_sdk_level: int
):
    """Skips current test if the Android SDK level does not meet requirement."""
    sdk_level = ad.build_info.get(
        android_device.BuildInfoConstants.BUILD_VERSION_SDK.build_info_key, None)
    asserts.skip_if(
        sdk_level is None, f'{ad}. Cannot get Android SDK level for device.')
    sdk_level = int(sdk_level)
    asserts.skip_if(
        sdk_level < min_sdk_level,
        (
            f'{ad}. The sdk level {sdk_level} is less than required minimum '
            f'sdk level {min_sdk_level} for this test case.'
        ),
    )

def skip_if_tv_device(
    ad: android_device.AndroidDevice,
):
    """Skips current test if the device is a TV."""
    characteristics = (ad.adb.shell('getprop ro.build.characteristics').decode().strip())
    asserts.skip_if('tv' in characteristics, f'{ad}. This test is not for TV devices.')


def capture_hsv_snapshot(
    device: android_device.AndroidDevice,
    prefix: str,
    output_path: str | None = None,
) -> None:
  """Captures go/hsv device snapshots.

  Note: Assumes the uiautomator is loaded, otherwise only capture screenshot.

  Args:
    device: Android device to have the HSV snapshot captured.
    prefix: Name of the HSV snapshot file.
    output_path: Path to the log directory.
  """
  if output_path is None:
      output_path = device.log_path

  # Take a screenshot equiped with hierarchy.
  device.take_screenshot(output_path, prefix=f'screenshot,{prefix}')

  if not hasattr(device, 'ui'):
      return

  hierarchy = device.ui.dump()
  hsv_file_name = device.generate_filename(
      file_type=f'hsv,{prefix}', extension_name='xml'
  )

  # Write hierarchy to xml file.
  with open(
      os.path.join(output_path, hsv_file_name), 'w', encoding='utf8'
  ) as f:
      print(hierarchy, file=f)
  logging.info('UI hierarchy saved to: %s', hsv_file_name)


def get_device_brand(device: android_device.AndroidDevice):
  """Gets the device brand information from `adb shell getprop`."""
  return device.adb.getprop('ro.product.brand')


def record_wifi_mainline_version(device: android_device.AndroidDevice):
  """Adds Wi-Fi mainline version to the device info section of test summary."""
  output = device.adb.shell(
      [
          'pm', 'list', 'packages', '--apex-only', '--show-versioncode',
          'com.google.android.wifi',
      ]
  ).decode()
  match = _MAINLINE_MODULE_VERSION_REGEX.match(output)
  if match is not None and match.group('version') is not None:
    version = match.group('version')
  else:
    version = ''
  device.add_device_info('wifi_mainline_version', version)


def convert_str_to_bool(value: str | bool) -> bool:
  """Converts the given arg to bool if it's a string. Otherwise returns as is.
  """
  if isinstance(value, str):
    return value.lower() == 'true'
  return value

def check_hotspot_device_supports_hotspot(ad: android_device.AndroidDevice):
    """Checks if the hotspot device supports hotspot, and skips the test if not."""
    if not ad.wifi.wifiIsPortableHotspotSupported():
        asserts.skip("Hotspot device does not support hotspot.")
