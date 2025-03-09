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
"""Wi-Fi Aware MacRandom test reimplemented in Mobly."""
import re
import sys
import time

from android.platform.test.annotations import ApiTest
from aware import aware_lib_utils as autils
from aware import constants
from mobly import asserts
from mobly import base_test
from mobly import records
from mobly import test_runner
from mobly import utils
from mobly.controllers import android_device


RUNTIME_PERMISSIONS = (
    'android.permission.ACCESS_FINE_LOCATION',
    'android.permission.ACCESS_COARSE_LOCATION',
    'android.permission.NEARBY_WIFI_DEVICES',
)
PACKAGE_NAME = constants.WIFI_AWARE_SNIPPET_PACKAGE_NAME

# Alias variable.
_DEFAULT_TIMEOUT = constants.WAIT_WIFI_STATE_TIME_OUT.total_seconds()

# Aware NDI (NAN data-interface) name prefix
AWARE_NDI_PREFIX = 'aware_data'


class MacRandomTest(base_test.BaseTestClass):
  """Test Cases: MacRandomTest.

  Set of tests for Wi-Fi Aware MAC address randomization of NMI (NAN
  management interface) and NDI (NAN data interface).
  """
  NUM_ITERATIONS = 10
  RANDOM_INTERVAL = 120  # minimal value in current implementation

  ads: list[android_device.AndroidDevice]
  SERVICE_NAME = 'GoogleTestXYZ'

  def setup_class(self):
    # Register two Android devices.
    self.ads = self.register_controller(android_device, min_number=1)

    def setup_device(device: android_device.AndroidDevice):
      autils.control_wifi(device, True)
      device.load_snippet('wifi_aware_snippet', PACKAGE_NAME)
      for permission in RUNTIME_PERMISSIONS:
        device.adb.shell(['pm', 'grant', PACKAGE_NAME, permission])
      asserts.abort_all_if(
          not device.wifi_aware_snippet.wifiAwareIsAvailable(),
          f'{device} Wi-Fi Aware is not available.',
      )

    # Set up devices in parallel.
    utils.concurrent_exec(
        setup_device,
        param_list=[[ad] for ad in self.ads],
        max_workers=1,
        raise_on_exception=True,
    )

  def setup_test(self):
    for ad in self.ads:
      autils.control_wifi(ad, True)
      aware_avail = ad.wifi_aware_snippet.wifiAwareIsAvailable()
      if not aware_avail:
        ad.log.info('Aware not available. Waiting ...')
        state_handler = ad.wifi_aware_snippet.wifiAwareMonitorStateChange()
        state_handler.waitAndGet(
            constants.WifiAwareBroadcast.WIFI_AWARE_AVAILABLE
        )

  def teardown_test(self):
    utils.concurrent_exec(
        self._teardown_test_on_device,
        param_list=[[ad] for ad in self.ads],
        max_workers=1,
        raise_on_exception=True,
    )
    utils.concurrent_exec(
        lambda d: d.services.create_output_excerpts_all(self.current_test_info),
        param_list=[[ad] for ad in self.ads],
        raise_on_exception=True,
    )

  def _teardown_test_on_device(self, ad: android_device.AndroidDevice) -> None:
    ad.wifi_aware_snippet.wifiAwareCloseAllWifiAwareSession()
    ad.wifi_aware_snippet.wifiAwareMonitorStopStateChange()
    autils.control_wifi(ad, True)

  def on_fail(self, record: records.TestResult) -> None:
    android_device.take_bug_reports(
        self.ads, destination=self.current_test_info.output_path
    )

  def get_wifi_mac_address(self, ad: android_device.AndroidDevice):
    """Get the Wi-Fi interface MAC address.

    Get the Wi-Fi interface MAC address as a upper-case string of hex digits
    without any separators (e.g. ':').

    Args:
        ad: Device on which to run.

    Returns:
        The Wi-Fi interface MAC address as a upper-case string of hex digits
        without any separators (e.g. ':').
    """
    results = ad.wifi_aware_snippet.wifiGetActiveNetworkMacAddress()
    return results.upper().replace(':', '')

  def get_mac_addr(self, device: android_device.AndroidDevice, interface: str):
    """Get the MAC address.

    Get the MAC address of the specified interface. Uses ifconfig and parses
    its output. Normalizes string to remove ':' and upper case.

    Args:
        device: Device on which to query the interface MAC address.
        interface: Name of the interface for which to obtain the MAC address.

    Returns:
        The MAC address with upper-case string.
    """
    out = device.adb.shell('ifconfig %s' % interface)
    res = re.match(r'.* HWaddr (\S+).*', out.decode(), re.S)
    asserts.assert_true(
        res,
        'Unable to obtain MAC address for interface %s' % interface,
        extras=out,
    )
    return res.group(1).upper().replace(':', '')

  def configure_mac_random_interval(
      self, device: android_device.AndroidDevice, interval_sec: int
  ):
    """Use the command-line API to configure the MAC address randomization interval.

    Args:
        device: Device on which to perform configuration
        interval_sec: The MAC randomization interval in seconds. A value of 0
          disables all randomization.
    """
    device.adb.shell(
        'cmd wifiaware native_api set mac_random_interval_sec %d' % interval_sec
    )

  @ApiTest(
    apis=[
        'android.net.wifi.WifiManager#getConnectionInfo()',
        'android.net.wifi.WifiInfo#getMacAddress()',
    ]
  )
  def test_nmi_ndi_randomization_on_enable(self):
    """Validate randomization of the NMI.

    Validate randomization of the NMI (NAN management interface) and all NDIs
    (NAN data-interface) on each enable/disable cycle.
    """
    dut = self.ads[0]
    asserts.skip_if(
        not dut.is_adb_root,
        'APM toggle needs Android device(s) with root permission',
    )
    # re-enable randomization interval (since if disabled it may also disable
    # the 'randomize on enable' feature).
    self.configure_mac_random_interval(dut, 1800)

    # DUT: attach and wait for confirmation & identity 10 times
    mac_addresses = {}
    for _ in range(self.NUM_ITERATIONS):
      attach_id = dut.wifi_aware_snippet.wifiAwareAttached(True)
      identity_changed_event = attach_id.waitAndGet(
          event_name=constants.AttachCallBackMethodType.ID_CHANGED,
          timeout=_DEFAULT_TIMEOUT,
      )
      # process NMI
      mac = identity_changed_event.data.get('mac', None)
      dut.log.info('NMI=%s', mac)
      if mac in mac_addresses:
        mac_addresses[mac] = mac_addresses[mac] + 1
      else:
        mac_addresses[mac] = 1

      # process NDIs
      time.sleep(5)  # wait for NDI creation to complete
      for j in range(autils.get_aware_capabilities(dut)['maxNdiInterfaces']):
        ndi_interface = '%s%d' % (AWARE_NDI_PREFIX, j)
        ndi_mac = self.get_mac_addr(dut, ndi_interface)
        dut.log.info('NDI %s=%s', ndi_interface, ndi_mac)
        if ndi_mac in mac_addresses:
          mac_addresses[ndi_mac] = mac_addresses[ndi_mac] + 1
        else:
          mac_addresses[ndi_mac] = 1
      dut.wifi_aware_snippet.wifiAwareDetach(attach_id.callback_id)
    # Test for uniqueness
    for mac in mac_addresses:
      if mac_addresses[mac] != 1:
        asserts.fail(
            'MAC address %s repeated %d times (all=%s)'
            % (mac, mac_addresses[mac], mac_addresses)
        )
    # Verify that infra interface (e.g. wlan0) MAC address is not used for NMI
    infra_mac = self.get_wifi_mac_address(dut)
    asserts.assert_false(
        infra_mac in mac_addresses,
        'Infrastructure MAC address (%s) is used for Aware NMI (all=%s)'
        % (infra_mac, mac_addresses),
    )

  @ApiTest(
    apis=[
        'android.net.wifi.aware.IdentityChangedListener#onIdentityChanged(byte[] mac)',
    ]
  )
  def test_nmi_randomization_on_interval(self):
    """Validate randomization of the NMI on different intervals.

    Validate randomization of the NMI (NAN management interface) on a set
    interval. Default value is 30 minutes - change to a small value to allow
    testing in real-time.
    """
    dut = self.ads[0]
    asserts.skip_if(
        not dut.is_adb_root,
        'APM toggle needs Android device(s) with root permission',
    )
    # set randomization interval to 120 seconds
    self.configure_mac_random_interval(dut, self.RANDOM_INTERVAL)

    attach_id = dut.wifi_aware_snippet.wifiAwareAttached(True)
    identity_changed_event = attach_id.waitAndGet(
        event_name=constants.AttachCallBackMethodType.ID_CHANGED,
        timeout=_DEFAULT_TIMEOUT,
    )
    mac_address1 = identity_changed_event.data.get('mac', None)
    dut.log.info('mac1=%s', mac_address1)

    identity_changed_event = attach_id.waitAndGet(
        event_name=constants.AttachCallBackMethodType.ID_CHANGED,
        timeout=self.RANDOM_INTERVAL + 5,
    )
    mac_address2 = identity_changed_event.data.get('mac', None)
    dut.log.info('mac2=%s', mac_address2)

    # validate MAC address is randomized
    asserts.assert_false(
        mac_address1 == mac_address2,
        'Randomized MAC addresses (%s, %s) should be different'
        % (mac_address1, mac_address2),
    )


if __name__ == '__main__':
  # Take test args
  if '--' in sys.argv:
    index = sys.argv.index('--')
    sys.argv = sys.argv[:1] + sys.argv[index + 1 :]

  test_runner.main()
