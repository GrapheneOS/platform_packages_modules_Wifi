"""A helper module for handling Access Points for Wi-Fi tests."""

from collections.abc import Mapping
import datetime
import logging
from typing import Any, Optional, Sequence

from mobly import asserts
from mobly.controllers import android_device
try:
    from mobly.controllers.wifi import openwrt_device
    from mobly.controllers.wifi.lib import wifi_configs as openwrt_lib_wifi_configs
    from mobly.controllers.wifi.lib.encryption import wpa
except ImportError:
    openwrt_device = None
    openwrt_lib_wifi_configs = None
    wpa = None

from connection import constants
from connection import wifi_utils

# Use alias for type annotation since we cannot import this module for some
# test invocations.
OpenWrtDevice = Any

_WIFI_CHANNEL_11 = 11


class ApHelper:
  """A helper class for handling Wi-Fi Access Points.

  This supports using one of the 2 kinds of Access Points below:

  1. An existing Wi-Fi network. User should configures SSID and password in
     test params and this class performs necessary validations.
  2. A programmable AP. For this option, this class can start and stop adhoc
     Wi-Fi networks with the given programmable AP device.
  """

  _programmable_ap: Optional['OpenWrtDevice']
  _configured_wifi_info: constants.WifiInfo | None

  def __init__(self):
    self._programmable_ap = None
    self._configured_wifi_info = None

  def initialize(
      self,
      test_class_obj,
      use_programmable_ap: bool,
      ad: android_device.AndroidDevice,
  ):
    """Initializes this object.

    If use_programmable_ap is False, extract the Wi-Fi SSID and password
    from user configured test configs, and verifies that the given Android
    device can discover the given Wi-Fi network.

    Otherwise, register the controller for the programmable AP according to
    the test configs.
    """
    if use_programmable_ap:
      asserts.assert_is_not_none(
          openwrt_device,
          'The test config is set to enabling programmable AP, which is not'
          ' supported in current invocation method. Please run with disabling'
          ' it or contact test owner for help.'
      )
      self._programmable_ap = test_class_obj.register_controller(
          openwrt_device
      )[0]
      if test_class_obj.user_params.get('reboot_ap', 'false') == 'true':
        self._programmable_ap.reboot()
      return

    # If not using programmable AP, must configure SSID when running the test.
    configured_wifi_ssid = test_class_obj.user_params.get('wifi_ssid')
    configured_wifi_pwd = test_class_obj.user_params.get('wifi_password')
    asserts.assert_true(
        configured_wifi_ssid,
        'Config wifi_ssid is not set. Please set wifi_ssid and wifi_password'
        ' in "WifiConnectionTestbed.yaml".'
    )
    asserts.assert_true(
        configured_wifi_pwd,
        'Config wifi_password is not set. Please set wifi_ssid and'
        ' wifi_password in "WifiConnectionTestbed.yaml.'
    )

    self.configured_wifi_info = wifi_utils.assert_configured_wifi_is_available(
        ad, configured_wifi_ssid, configured_wifi_pwd
    )

  def get_or_start_wifi(self) -> constants.WifiInfo:
    """Gets the user configured Wi-Fi or starts a Wi-Fi on programmable AP."""
    if self._programmable_ap is None:
      return self.configured_wifi_info

    config = openwrt_lib_wifi_configs.WiFiConfig(
        channel=_WIFI_CHANNEL_11,
        encryption_config=wpa.gen_config_for_wpa2_ccmp(),
    )
    wifi_info_from_openwrt = self._programmable_ap.start_wifi(config=config)
    self._programmable_ap.log.info(
        'Started a Wi-Fi AP with SSID %s, bssid %s, and password %s',
        wifi_info_from_openwrt.ssid,
        wifi_info_from_openwrt.bssid,
        wifi_info_from_openwrt.password,
    )

    wifi_info = constants.WifiInfo(
        ssid=wifi_info_from_openwrt.ssid,
        password=wifi_info_from_openwrt.password,
        bssid=wifi_info_from_openwrt.bssid,
    )
    return wifi_info

  def stop_programmable_ap(self):
    """Stops the programmable AP; No-op if it's using static Wi-Fi network."""
    if self._programmable_ap is not None:
      self._programmable_ap.stop_all_wifi()
