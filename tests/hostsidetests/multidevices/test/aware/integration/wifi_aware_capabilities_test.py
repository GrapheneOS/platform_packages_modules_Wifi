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
"""Wi-Fi Aware Capabilities test reimplemented in Mobly."""
import string
import sys
from typing import Any, Dict, Union

from aware import aware_lib_utils as autils
from aware import constants
from mobly import asserts
from mobly import base_test
from mobly import records
from mobly import test_runner
from mobly import utils
from mobly.controllers import android_device
from mobly.controllers.android_device_lib import callback_handler_v2
from mobly.snippet import callback_event

RUNTIME_PERMISSIONS = (
    'android.permission.ACCESS_FINE_LOCATION',
    'android.permission.ACCESS_COARSE_LOCATION',
    'android.permission.NEARBY_WIFI_DEVICES',
)
PACKAGE_NAME = constants.WIFI_SNIPPET_PACKAGE_NAME

_CALLBACK_NAME = constants.DiscoverySessionCallbackParamsType.CALLBACK_NAME

# Publish & Subscribe Config keys.
_PAYLOAD_SIZE_MIN = 0
_PAYLOAD_SIZE_TYPICAL = 1
_PAYLOAD_SIZE_MAX = 2

# Definition for timeout and retries.
_DEFAULT_TIMEOUT = constants.WAIT_WIFI_STATE_TIME_OUT.total_seconds()
_REQUEST_NETWORK_TIMEOUT_MS = 15 * 1000
_MAX_TX_RETRIES = 5

_TRANSPORT_TYPE_WIFI_AWARE = (
    constants.NetworkCapabilities.Transport.TRANSPORT_WIFI_AWARE
)


class WifiAwareCapabilitiesTest(base_test.BaseTestClass):
  """Set of tests for Wi-Fi Aware Capabilities - verifying that the provided
  capabilities are real (i.e. available)."""
  # message ID counter to make sure all uses are unique
  msg_id = 0

  ads: list[android_device.AndroidDevice]
  SERVICE_NAME = 'GoogleTestXYZ'

  def setup_class(self):
    # Register two Android devices.
    self.ads = self.register_controller(android_device, min_number=1)

    def setup_device(device: android_device.AndroidDevice):
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
    autils.set_airplane_mode(ad, False)
    autils.control_wifi(ad, True)

  def on_fail(self, record: records.TestResult) -> None:
    android_device.take_bug_reports(
        self.ads, destination=self.current_test_info.output_path
    )

  def create_base_config(
      self,
      is_publish: bool,
      ptype: Union[int, None],
      stype: Union[int, None],
      payload_size: int,
      ttl: int,
      term_ind_on: bool,
      null_match: bool,
      service_name: str,
  ) -> Dict[str, Any]:
    config = {}
    if is_publish:
      config[constants.PUBLISH_TYPE] = ptype
    else:
      config[constants.SUBSCRIBE_TYPE] = stype
    config[constants.TTL_SEC] = ttl
    config[constants.TERMINATE_NOTIFICATION_ENABLED] = term_ind_on
    if payload_size == _PAYLOAD_SIZE_MIN:
      config[constants.SERVICE_NAME] = (
          'a' if not service_name else service_name
      )
      config[constants.SERVICE_SPECIFIC_INFO] = None
      config[constants.MATCH_FILTER] = []
    elif payload_size == _PAYLOAD_SIZE_TYPICAL:
      config[constants.SERVICE_NAME] = (
          'GoogleTestServiceX' if not service_name else service_name
      )
      if is_publish:
        config[constants.SERVICE_SPECIFIC_INFO] = string.ascii_letters
      else:
        config[constants.SERVICE_SPECIFIC_INFO] = string.ascii_letters[::-1]
      config[constants.MATCH_FILTER] = autils.encode_list([
          (10).to_bytes(1, byteorder='big'),
          'hello there string' if not null_match else None,
          bytes(range(40)),
      ])
    else:  # aware_constant.PAYLOAD_SIZE_MAX
      config[constants.SERVICE_NAME] = (
          'VeryLong' + 'X' * (len('maxServiceNameLen') - 8)
          if not service_name
          else service_name
      )
      config[constants.SERVICE_SPECIFIC_INFO] = (
          'P' if is_publish else 'S'
      ) * len('maxServiceSpecificInfoLen')
      mf = autils.construct_max_match_filter(len('maxMatchFilterLen'))
      if null_match:
        mf[2] = None
      config[constants.MATCH_FILTER] = autils.encode_list(mf)
    return config

  def get_next_msg_id(self) -> int:
    """Increment the message ID and returns the new value.

    Guarantees that each call to the method returns a unique value.

    Returns:
      A new message id value.
    """

    self.msg_id = self.msg_id + 1
    return self.msg_id

  def create_publish_config(
      self,
      ptype: int,
      payload_size: int,
      ttl: int,
      term_ind_on: bool,
      null_match: bool,
      service_name: str = '',
  ) -> Dict[str, Any]:
    return self.create_base_config(
        True,
        ptype,
        None,
        payload_size,
        ttl,
        term_ind_on,
        null_match,
        service_name,
    )

  def create_subscribe_config(
      self,
      stype: int,
      payload_size: int,
      ttl: int,
      term_ind_on: bool,
      null_match: bool,
      service_name: str = '',
  ) -> Dict[str, Any]:
    return self.create_base_config(
        False,
        None,
        stype,
        payload_size,
        ttl,
        term_ind_on,
        null_match,
        service_name,
    )

  def _start_attach(self, ad: android_device.AndroidDevice) -> str:
    """Starts the attach process on the provided device."""
    handler = ad.wifi_aware_snippet.wifiAwareAttach()
    attach_event = handler.waitAndGet(
        event_name=constants.AttachCallBackMethodType.ATTACHED,
        timeout=_DEFAULT_TIMEOUT,
    )
    asserts.assert_true(
        ad.wifi_aware_snippet.wifiAwareIsSessionAttached(handler.callback_id),
        f'{ad} attach succeeded, but Wi-Fi Aware session is still null.',
    )
    ad.log.info('Attach Wi-Fi Aware session succeeded.')
    return attach_event.callback_id

  def _start_discovery_session(
      self, dut, session_id, is_publish, dtype, service_name, expect_success
  ) -> callback_event.CallbackEvent:
    """Start a discovery session.

    Args:
      dut: Device under test
      session_id: ID of the Aware session in which to start discovery
      is_publish: True for a publish session, False for subscribe session
      dtype: Type of the discovery session
      service_name: Service name to use for the discovery session
      expect_success: True if expect session to be created, False otherwise

    Returns:
      Discovery session ID.
    """
    if is_publish:
      p_config = self.create_publish_config(
          dtype,
          _PAYLOAD_SIZE_TYPICAL,
          ttl=0,
          term_ind_on=False,
          null_match=False,
          service_name=service_name,
      )
      dut.log.info(
          'Created the publish session with type is %s, service_name is %s',
          dtype,
          p_config[constants.SERVICE_NAME],
      )
      disc_id = dut.wifi_aware_snippet.wifiAwarePublish(session_id, p_config)
      event_name = constants.DiscoverySessionCallbackMethodType.PUBLISH_STARTED
    else:
      s_config = self.create_subscribe_config(
          dtype,
          _PAYLOAD_SIZE_TYPICAL,
          ttl=0,
          term_ind_on=False,
          null_match=False,
          service_name=service_name,
      )
      dut.log.info(
          'Created the subscribe session with type is %s, service_name is %s',
          dtype,
          s_config[constants.SERVICE_NAME],
      )
      disc_id = dut.wifi_aware_snippet.wifiAwareSubscribe(session_id, s_config)
      event_name = (
          constants.DiscoverySessionCallbackMethodType.SUBSCRIBE_STARTED
      )

    if expect_success:
      discovery_result = disc_id.waitAndGet(
          constants.DiscoverySessionCallbackMethodType.DISCOVER_RESULT,
          timeout=_DEFAULT_TIMEOUT,
      )
      callback_name = discovery_result.data[_CALLBACK_NAME]
      asserts.assert_equal(
          event_name,
          callback_name,
          f'{dut} {service_name} failed, got callback: {callback_name}.',
      )
    else:
      disc_id.waitAndGet(
          constants.DiscoverySessionCallbackMethodType.SESSION_CONFIG_FAILED
      )
      dut.log.info(
          f'Got the expect value- {service_name} session config failed.'
      )

    dut.log.info('_start_discovery_session return: %s', disc_id.callback_id)
    return disc_id

  def test_max_discovery_sessions(self) -> None:
    """Validate device capabilities.

    Validate that the device can create as many discovery sessions as are
    indicated in the device capabilities.
    """
    dut = self.ads[0]
    dut.log.info('test_max_discovery_sessions start ...')
    session_id = self._start_attach(dut)
    pub_disc_id: callback_event.CallbackEvent = None
    sub_disc_id: callback_event.CallbackEvent = None

    service_name_template = 'GoogleTestService-%s-%d'
    # Start the max number of publish sessions.
    for i in range(autils.get_aware_capabilities(dut)['maxPublishes']):
      # Create publish discovery session of both types.
      pub_disc_id = self._start_discovery_session(
          dut,
          session_id,
          True,
          constants.PublishType.UNSOLICITED
          if i % 2 == 0
          else constants.PublishType.SOLICITED,
          service_name_template % ('pub', i),
          True,
      )
    asserts.assert_true(
        pub_disc_id is not None,
        'publish sessions initialize failed',
    )
    # Start the max number of subscribe sessions.
    for i in range(autils.get_aware_capabilities(dut)['maxSubscribes']):
      # Create publish discovery session of both types.
      sub_disc_id = self._start_discovery_session(
          dut,
          session_id,
          False,
          constants.SubscribeType.PASSIVE
          if i % 2 == 0
          else constants.SubscribeType.ACTIVE,
          service_name_template % ('sub', i),
          True,
      )
    asserts.assert_true(
        sub_disc_id is not None,
        'subscribe sessions initialize failed',
    )
    # Start another publish & subscribe and expect failure.
    self._start_discovery_session(
        dut,
        session_id,
        True,
        constants.PublishType.UNSOLICITED,
        service_name_template % ('pub', 900),
        False,
    )
    self._start_discovery_session(
        dut,
        session_id,
        False,
        constants.SubscribeType.ACTIVE,
        service_name_template % ('sub', 901),
        False,
    )

    # Delete one of the publishes and try again (see if can create subscribe
    # instead - should not).
    dut.log.info('Close last one of the publish session.')
    dut.wifi_aware_snippet.wifiAwareCloseDiscoverSession(
        pub_disc_id.callback_id
    )
    self._start_discovery_session(
        dut,
        session_id,
        False,
        constants.SubscribeType.ACTIVE,
        service_name_template % ('sub', 902),
        False,
    )
    self._start_discovery_session(
        dut,
        session_id,
        True,
        constants.PublishType.UNSOLICITED,
        service_name_template % ('pub', 903),
        True,
    )

    # Delete one of the subscribes and try again (see if can create publish
    # instead - should not).
    dut.log.info('Close last one of the subscribe session.')
    dut.wifi_aware_snippet.wifiAwareCloseDiscoverSession(
        sub_disc_id.callback_id
    )
    self._start_discovery_session(
        dut,
        session_id,
        True,
        constants.PublishType.UNSOLICITED,
        service_name_template % ('pub', 904),
        False,
    )
    self._start_discovery_session(
        dut,
        session_id,
        False,
        constants.SubscribeType.ACTIVE,
        service_name_template % ('sub', 905),
        True,
    )


if __name__ == '__main__':
  # Take test args
  if '--' in sys.argv:
    index = sys.argv.index('--')
    sys.argv = sys.argv[:1] + sys.argv[index + 1 :]

  test_runner.main()
