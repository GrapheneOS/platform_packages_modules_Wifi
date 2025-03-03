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
"""Wi-Fi Aware Rtt Disable test reimplemented in Mobly."""
import functools
import logging
import signal
import sys
import time

from aware import aware_lib_utils as autils
from aware import constants
from mobly import asserts
from mobly import base_test
from mobly import records
from mobly import test_runner
from mobly import utils
from mobly.controllers import android_device
from mobly.snippet import errors

RUNTIME_PERMISSIONS = (
    'android.permission.ACCESS_FINE_LOCATION',
    'android.permission.ACCESS_COARSE_LOCATION',
    'android.permission.NEARBY_WIFI_DEVICES',
)
PACKAGE_NAME = constants.WIFI_AWARE_SNIPPET_PACKAGE_NAME

# Alias variable.
_DEFAULT_TIMEOUT = constants.WAIT_WIFI_STATE_TIME_OUT.total_seconds()
_CALLBACK_NAME = constants.DiscoverySessionCallbackParamsType.CALLBACK_NAME

######################################################
# status codes
######################################################
_RANGING_FAIL_CODE_GENERIC = 1
_RANGING_FAIL_CODE_RTT_NOT_AVAILABLE = 2


# Timeout decorator block
class TimeoutError(Exception):
  """Exception for timeout decorator related errors."""


def _timeout_handler():
  """Handler function used by signal to terminate a timed out function."""
  raise TimeoutError()


def timeout(sec):
  """A decorator used to add time out check to a function.

  This only works in main thread due to its dependency on signal module.
  Do NOT use it if the decorated function does not run in the Main thread.

  Args:
      sec: Number of seconds to wait before the function times out. No timeout
        if set to 0

  Returns:
      What the decorated function returns.

  Raises:
      TimeoutError is raised when time out happens.
  """

  def decorator(func):

    @functools.wraps(func)
    def wrapper(*args, **kwargs):
      if sec:
        signal.signal(signal.SIGALRM, _timeout_handler)
        signal.alarm(sec)
      try:
        return func(*args, **kwargs)
      except TimeoutError as exc:
        raise TimeoutError(
            ('Function {} timed out after {} seconds.').format(
                func.__name__, sec
            )
        ) from exc
      finally:
        signal.alarm(0)

    return wrapper

  return decorator


class RttDisableTest(base_test.BaseTestClass):
  """Test class for RTT ranging enable/disable flows."""

  MODE_DISABLE_WIFI = 0
  MODE_DISABLE_LOCATIONING = 1

  ads: list[android_device.AndroidDevice]

  def setup_class(self):
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
      self.set_location_service(ad, True)
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
    # autils.control_wifi(ad, True)

  def on_fail(self, record: records.TestResult) -> None:
    android_device.take_bug_reports(
        self.ads, destination=self.current_test_info.output_path
    )

  def scan_networks(
      self, dut: android_device.AndroidDevice, max_tries: int = 3
  ) -> list[dict[str, str]]:
    """Perform a scan and return scan results.

    Args:
        dut: Device under test.
        max_tries: Retry scan to ensure network is found

    Returns:
        an array of scan results.
    """
    scan_results = []
    for _ in range(max_tries):
        scan_results = dut.wifi_aware_snippet.wifiScanAndGetResults()
        if scan_results:
            break

    return scan_results

  def select_best_scan_results(
      self,
      scans: list[dict[str, str]],
      select_count: int,
      lowest_rssi: int = -80,
  ):
    """Select best result based on RSSI.

    Select the strongest 'select_count' scans in the input list based on
    highest RSSI. Exclude all very weak signals, even if results in a shorter
    list.

    Args:
        scans: List of scan results.
        select_count: An integer specifying how many scans to return at most.
        lowest_rssi: The lowest RSSI to accept into the output.

    Returns:
           a list of the strongest 'select_count' scan results from the scans
           list.
    """

    def _take_rssi(element):
      return element['level']

    result = []
    scans.sort(key=_take_rssi, reverse=True)
    for scan in scans:
      logging.info(
          'scan type: %s, %s, %s', scan['SSID'], scan['level'], scan['BSSID']
      )
      if len(result) == select_count:
        break
      if scan['level'] < lowest_rssi:
        break  # rest are lower since we're sorted
      result.append(scan)

    return result

  def set_location_service(self, ad, new_state):
    """Set Location service on/off in Settings->Location.

    Args:
        ad: android device object.
        new_state: new state for "Location service".
            If new_state is False, turn off location service.
            If new_state if True, set location service to "High accuracy".
    """
    ad.adb.shell('content insert --uri '
                 ' content://com.google.settings/partner --bind '
                 'name:s:network_location_opt_in --bind value:s:1')
    ad.adb.shell('content insert --uri '
                 ' content://com.google.settings/partner --bind '
                 'name:s:use_location_for_services --bind value:s:1')
    if new_state:
        ad.adb.shell('settings put secure location_mode 3')
    else:
        ad.adb.shell('settings put secure location_mode 0')

  def force_airplane_mode(self, ad, new_state, timeout_value=60):
    """Force the device to set airplane mode on or off by adb shell command.

    Args:
        ad: android device object.
        new_state: Turn on airplane mode if True.
            Turn off airplane mode if False.
        timeout_value: max wait time for 'adb wait-for-device'

    Returns:
        True if success.
        False if timeout.
    """

    # Using timeout decorator.
    # Wait for device with timeout. If after <timeout_value> seconds, adb
    # is still waiting for device, throw TimeoutError exception.
    @timeout(timeout_value)
    def wait_for_device_with_timeout(ad):
        ad.adb.wait_for_device()

    try:
        wait_for_device_with_timeout(ad)
        ad.adb.shell('settings put global airplane_mode_on {}'.format(
            1 if new_state else 0))
        ad.adb.shell('am broadcast -a android.intent.action.AIRPLANE_MODE')
    except TimeoutError:
        # adb wait for device timeout
        return False
    return True

  def run_disable_rtt(self, disable_mode):
    """Validate the RTT ranging feature if RTT disabled.

    Validate the RTT disabled flows: whether by disabling Wi-Fi or entering
    doze mode.

    Args:
      disable_mode: The particular mechanism in which RTT is disabled. One of
        the MODE_* constants.
    """
    dut = self.ads[0]

    # validate start-up conditions
    asserts.assert_true(
        dut.wifi_aware_snippet.wifiRttIsAvailable(), 'RTT is not available'
    )

    # scan to get some APs to be used later
    all_aps = self.select_best_scan_results(
        self.scan_networks(dut), select_count=1
    )
    asserts.assert_true(all_aps, 'Need at least one visible AP!')

    # disable RTT and validate broadcast & API
    if disable_mode == self.MODE_DISABLE_WIFI:
      # disabling Wi-Fi is not sufficient: since scan mode (and hence RTT) will
      # remain enabled - we need to disable the Wi-Fi chip aka Airplane Mode
      asserts.assert_true(
          self.force_airplane_mode(dut, True),
          'Can not turn on airplane mode on: %s' % dut.serial,
      )
      autils.control_wifi(dut, False)
    elif disable_mode == self.MODE_DISABLE_LOCATIONING:
      self.set_location_service(dut, False)
    time.sleep(10)
    dut.log.info(
        'WiFi RTT status: %s', dut.wifi_aware_snippet.wifiRttIsAvailable()
    )
    asserts.assert_false(
        dut.wifi_aware_snippet.wifiRttIsAvailable(), 'RTT is available'
    )

    # request a range and validate error
    dut.log.info('access points input: %s', all_aps[0:1])
    ranging_cb_handler = (
        dut.wifi_aware_snippet.wifiRttStartRangingToAccessPoints(all_aps[0:1])
    )
    event = ranging_cb_handler.waitAndGet(
        event_name=constants.RangingResultCb.EVENT_NAME_ON_RANGING_RESULT,
        timeout=_DEFAULT_TIMEOUT,
    )

    callback_name = event.data.get(
        constants.RangingResultCb.DATA_KEY_CALLBACK_NAME, None
    )
    dut.log.info('StartRangingToAccessPoints callback = %s', callback_name)
    asserts.assert_equal(
        callback_name,
        constants.RangingResultCb.CB_METHOD_ON_RANGING_FAILURE,
        'Should be ranging failed.',
    )
    status_code = event.data.get(
        constants.RangingResultCb.DATA_KEY_RESULT_STATUS, None
    )
    dut.log.info('StartRangingToAccessPoints status code = %s', status_code)
    asserts.assert_equal(
        status_code, _RANGING_FAIL_CODE_RTT_NOT_AVAILABLE, 'Invalid error code'
    )

    # enable RTT and validate broadcast & API
    if disable_mode == self.MODE_DISABLE_WIFI:
      asserts.assert_true(
          self.force_airplane_mode(dut, False),
          'Can not turn off airplane mode on: %s' % dut.serial,
      )
      autils.control_wifi(dut, True)
    elif disable_mode == self.MODE_DISABLE_LOCATIONING:
      self.set_location_service(dut, True)

    asserts.assert_true(
        dut.wifi_aware_snippet.wifiRttIsAvailable(), 'RTT is not available'
    )

  def test_disable_wifi(self):
    """Validate that getting expected broadcast when Wi-Fi is disabled and that any range requests are rejected.
    """
    self.run_disable_rtt(self.MODE_DISABLE_WIFI)

  def test_disable_location(self):
    """Validate that getting expected broadcast when locationing is disabled and that any range requests are rejected.
    """
    self.run_disable_rtt(self.MODE_DISABLE_LOCATIONING)


if __name__ == '__main__':
  # Take test args
  if '--' in sys.argv:
    index = sys.argv.index('--')
    sys.argv = sys.argv[:1] + sys.argv[index + 1 :]

  test_runner.main()
