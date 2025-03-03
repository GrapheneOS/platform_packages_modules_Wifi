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
import time

from mobly import asserts
from mobly import base_test
from mobly import test_runner
from mobly import utils
from mobly.controllers import android_device
from mobly.controllers.android_device_lib import callback_handler_v2
from mobly.snippet import errors
from softap import constants
import wifi_test_utils

_CALLBACK_TIMEOUT = constants.CALLBACK_TIMEOUT.total_seconds()
_WIFI_SCAN_INTERVAL_SEC = constants.WIFI_SCAN_INTERVAL_SEC.total_seconds()


class WifiSoftApTest(base_test.BaseTestClass):
  """SoftAp test class.

  Attributes:
    host: Android device providing Wi-Fi hotspot
    client: Android device connecting to host provided hotspot
  """

  def setup_class(self) -> None:
    self.ads = self.register_controller(android_device, min_number=2)
    self.host = self.ads[0]
    self.client = self.ads[1]
    utils.concurrent_exec(
        self._setup_device,
        param_list=[[ad] for ad in self.ads],
        raise_on_exception=True,
    )

    asserts.abort_class_if(
        not self.host.wifi.wifiIsPortableHotspotSupported(),
        'Hotspot is not supported on host, abort remaining tests.',
    )

  def _setup_device(self, ad: android_device.AndroidDevice) -> None:
    ad.load_snippet('wifi', 'com.google.snippet.wifi')
    wifi_test_utils.enable_wifi_verbose_logging(ad)

  def setup_test(self):
    for ad in self.ads:
      self._stop_tethering(self.host)
      ad.wifi.wifiDisableAllSavedNetworks()
      ad.wifi.wifiEnable()

  def teardown_class(self):
    for ad in self.ads:
      ad.wifi.wifiEnableAllSavedNetworks()
      ad.wifi.wifiEnable()

  def _test_softap_disconnect(self, use_local_only: bool):
    """Tests SoftApCallback#onClientsDisconnected callback.

    Steps:
    1. Start host's hotspot and verify it is enabled.
    2. Register SoftApCallback
    3. Add client to the hotspot and verify connection.
    4. Disconnect the client and verify disconnection.

    Args:
      use_local_only: True to test local-only hotspot, False to test tethering.
    """
    asserts.skip_if(
        not self.host.wifi.wifiCheckSoftApDisconnectReasonFlag(),
        'Skipping because softap_disconnect_reason flag is not enabled.',
    )

    # Start host's hotspot and verify that it is enabled
    # and register SoftApCallback.
    if use_local_only:
      callback = self.host.wifi.wifiStartLocalOnlyHotspot()
      ssid, password = self._wait_for_local_only_on_started(callback)
      softap_callback = (
          self.host.wifi.wifiRegisterLocalOnlyHotspotSoftApCallback()
      )
    else:
      callback = self.host.wifi.tetheringStartTethering()
      ssid, password = self._wait_for_on_tethering_started(callback)
      softap_callback = self.host.wifi.wifiRegisterSoftApCallback()

    # Add client to the hotspot and verify connection.
    asserts.assert_true(
        self._check_wifi_scan_result_for_ssid(self.client, ssid),
        'Network could not be found in Wi-Fi scan results',
    )
    self.client.wifi.wifiConnectSimple(ssid, password)
    client_mac_address = self._wait_for_on_connected_clients_changed(
        softap_callback
    )

    # Disconnect the client and verify disconnection.
    self.client.wifi.wifiDisable()
    self._wait_for_on_clients_disconnected(client_mac_address, softap_callback)

    if use_local_only:
      self.host.wifi.wifiStopLocalOnlyHotspot()
      self.host.wifi.wifiUnregisterLocalOnlyHotspotSoftApCallback()
    else:
      self.host.wifi.tetheringStopTethering()
      self.host.wifi.wifiUnregisterSoftApCallback()

  def test_local_only_softap_disconnect(self):
    """Tests local-only SoftAp disconnection."""
    self._test_softap_disconnect(use_local_only=True)

  def test_tethering_softap_disconnect(self):
    """Tests tethering SoftAp disconnection."""
    self._test_softap_disconnect(use_local_only=False)

  def _check_wifi_scan_result_for_ssid(
      self, ad: android_device.AndroidDevice, ssid: str
  ) -> bool:
    """Scan Wi-Fi networks for the network with the given ssid.

    Args:
      ad: The Android device object.
      ssid: SSID of the AP.

    Returns:
      True if SSID is found in Wi-Fi scan results, False otherwise.
    """
    # Due to restriction of "scan four times in a 2-minute period" in
    # https://developer.android.com/guide/topics/connectivity/wifi-scan#wifi-scan-restrictions
    # this function only scans 4 times looking for the ssid, 5 second
    # interval between each scan.
    for _ in range(1, 5):
      scanned_results = ad.wifi.wifiScanAndGetResults()
      scanned_ssids = sorted(
          [scan_result['SSID'] for scan_result in scanned_results]
      )
      if ssid in scanned_ssids:
        return True
      time.sleep(_WIFI_SCAN_INTERVAL_SEC)

    return False

  def _wait_for_local_only_on_started(
      self,
      local_only_cb: callback_handler_v2.CallbackHandlerV2,
  ) -> tuple[str, str]:
    """Waits for onStarted callback to be received.

    If local-only hotspot failed to start, the current test is skipped.

    Args:
      local_only_cb: The LocalOnlyHotspot callback identifier.

    Returns:
      A tuple containing the credentials of the hotspot session. The
      tuple is in the form of (ssid of network, passphrase of network).
    """
    try:
      on_started_event = local_only_cb.waitAndGet(
          event_name=constants.LocalOnlyHotspotCallbackEventName.ON_STARTED,
          timeout=_CALLBACK_TIMEOUT,
      )
    except errors.CallbackHandlerTimeoutError:
      on_failed_event = local_only_cb.waitAndGet(
          event_name=constants.LocalOnlyHotspotCallbackEventName.ON_FAILED,
          timeout=_CALLBACK_TIMEOUT,
      )
      failure_reason = on_failed_event.data[
          constants.LocalOnlyOnFailedDataKey.REASON
      ]
      asserts.skip(
          'Skipping this test because the local-only hotspot '
          f'could not be started. Reason: {failure_reason}'
      )

    ssid = on_started_event.data[constants.LocalOnlyOnStartedDataKey.SSID]
    passphrase = on_started_event.data[
        constants.LocalOnlyOnStartedDataKey.PASSPHRASE
    ]
    return ssid, passphrase

  def _wait_for_on_tethering_started(
      self,
      tethering_cb: callback_handler_v2.CallbackHandlerV2,
  ) -> tuple[str, str]:
    """Waits for onTetheringStarted callback to be received.

    If tethering failed to start, the current test is skipped.

    Args:
      tethering_cb: The StartTethering callback identifier.

    Returns:
      A tuple containing the credentials of the tethering session. The tuple
      is in the form of (ssid of network, passphrase of network).
    """
    try:
      tethering_cb.waitAndGet(
          event_name=constants.StartTetheringCallbackEventName.ON_TETHERING_STARTED,
          timeout=_CALLBACK_TIMEOUT,
      )
    except errors.CallbackHandlerTimeoutError:
      on_tethering_failure_event = tethering_cb.waitAndGet(
          event_name=constants.StartTetheringCallbackEventName.ON_TETHERING_FAILED,
          timeout=_CALLBACK_TIMEOUT,
      )
      failure_error = on_tethering_failure_event.data[
          constants.TetheringOnTetheringFailedDataKey.ERROR
      ]
      asserts.skip(
          'Skipping this test because tethering could not be started. Reason:'
          f' {failure_error}'
      )

    current_configuration = self.host.wifi.wifiGetSoftApConfiguration()
    ssid = current_configuration['SSID']
    passphrase = current_configuration['mPassphrase']
    return ssid, passphrase

  def _stop_tethering(self, ad: android_device.AndroidDevice) -> bool:
    """Stops any ongoing tethering sessions on the android device.

    Args:
      ad: The Android device object.

    Returns:
      True if tethering is disabled successfully, False otherwise.
    """
    if not ad.wifi.wifiIsApEnabled():
      return True

    ad.wifi.tetheringStopTethering()
    return ad.wifi.wifiWaitForTetheringDisabled()

  def _wait_for_on_connected_clients_changed(
      self, softap_callback: callback_handler_v2.CallbackHandlerV2
  ) -> str:
    """Wait for SoftApCallback#onConnectedClientsChanged to be received.

    The test fails if the callback is never received. If successful, the
    mac address of the connected client is returned.

    Args:
      softap_callback: The SoftApCallback callback identifier.

    Returns:
      The string mac address of the connected client.
    """
    try:
      on_connected_clients_changed_event = softap_callback.waitAndGet(
          event_name=(
              constants.SoftApCallbackEventName.ON_CONNECTED_CLIENTS_CHANGED
          ),
          timeout=_CALLBACK_TIMEOUT,
      )
    except errors.CallbackHandlerTimeoutError:
      asserts.fail('Connection could not be established.')

    # In our test cases, there is only one other device involved
    # so we can confirm that the client has connected.
    asserts.assert_equal(
        on_connected_clients_changed_event.data[
            constants.SoftApOnConnectedClientsChangedDataKey.CONNECTED_CLIENTS_COUNT
        ],
        1,
    )

    return on_connected_clients_changed_event.data[
        constants.SoftApOnConnectedClientsChangedDataKey.CLIENT_MAC_ADDRESS
    ]

  def _wait_for_on_clients_disconnected(
      self,
      expected_mac_address: str,
      softap_callback: callback_handler_v2.CallbackHandlerV2,
  ) -> None:
    """Wait for SoftApCallback#onClientsDisconnected to be received.

    The test fails if the callback is never received.

    Args:
      expected_mac_address: Expected mac address of disconnected client
      softap_callback: The SoftApCallback callback identifier.
    """
    try:
      on_clients_disconnected_event = softap_callback.waitAndGet(
          event_name='onClientsDisconnected', timeout=_CALLBACK_TIMEOUT
      )
    except errors.CallbackHandlerTimeoutError:
      asserts.fail('No client disconnected.')

    # In our test cases, there is only one other device involved
    # so we can confirm that the client has disconnected.
    asserts.assert_equal(
        on_clients_disconnected_event.data[
            constants.SoftApOnClientsDisconnectedDataKey.DISCONNECTED_CLIENTS_COUNT
        ],
        1,
    )
    asserts.assert_equal(
        on_clients_disconnected_event.data[
            constants.SoftApOnClientsDisconnectedDataKey.CLIENT_MAC_ADDRESS
        ],
        expected_mac_address,
    )


if __name__ == '__main__':
  test_runner.main()
