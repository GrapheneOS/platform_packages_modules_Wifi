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
"""Wi-Fi Aware Discovery with ranging test reimplemented in Mobly."""
import logging
import statistics
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
from mobly.controllers.android_device_lib import callback_handler_v2
from mobly.snippet import callback_event

RUNTIME_PERMISSIONS = (
    'android.permission.ACCESS_FINE_LOCATION',
    'android.permission.ACCESS_COARSE_LOCATION',
    'android.permission.NEARBY_WIFI_DEVICES',
)
PACKAGE_NAME = constants.WIFI_AWARE_SNIPPET_PACKAGE_NAME

_CALLBACK_NAME = constants.DiscoverySessionCallbackParamsType.CALLBACK_NAME
_DEFAULT_TIMEOUT = constants.WAIT_WIFI_STATE_TIME_OUT.total_seconds()

_LARGE_ENOUGH_DISTANCE_MM = 1000000
_IS_SESSION_INIT = constants.DiscoverySessionCallbackParamsType.IS_SESSION_INIT


class WiFiAwareDiscoveryWithRangingTest(base_test.BaseTestClass):
  """Set of tests for Wi-Fi Aware discovery configured with ranging (RTT)."""
  SERVICE_NAME = 'GoogleTestServiceRRRRR'

  ads: list[android_device.AndroidDevice]
  device_startup_offset = 2

  def setup_class(self):
    # Register two Android devices.
    logging.basicConfig(level=logging.INFO, force=True)
    self.ads = self.register_controller(android_device, min_number=2)

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
    logging.info('setup_test')
    for ad in self.ads:
      ad.log.info('setup_test: open wifi')
      autils.control_wifi(ad, True)
      aware_avail = ad.wifi_aware_snippet.wifiAwareIsAvailable()
      if not aware_avail:
        ad.log.info('Aware not available. Waiting ...')
        state_handler = ad.wifi_aware_snippet.wifiAwareMonitorStateChange()
        state_handler.waitAndGet(
            constants.WifiAwareBroadcast.WIFI_AWARE_AVAILABLE
        )

  def teardown_test(self):
    logging.info('teardown_test')
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
    logging.info('on_fail')
    android_device.take_bug_reports(
        self.ads, destination=self.current_test_info.output_path
    )

  def getname(self, level=1):
    """Python magic to return the name of the *calling* function.

    Args:
      level: How many levels up to go for the method name. Default = calling
        method.

    Returns:
      The name of the *calling* function.
    """
    logging.info('debug> %s', sys._getframe(level).f_code.co_name)
    return sys._getframe(level).f_code.co_name

  def _start_publish(
      self,
      p_dut: android_device.AndroidDevice,
      attach_session_id: str,
      pub_config: constants.PublishConfig,
      update_pub: bool = False,
  ) -> callback_event.CallbackEvent:
    """Starts a publish session on the publisher device."""

    # Start the publishing session and return the handler.
    if update_pub:
      publish_handler = p_dut.wifi_aware_snippet.wifiAwareUpdatePublish(
          attach_session_id,
          pub_config.to_dict(),
      )
    else:
      publish_handler = p_dut.wifi_aware_snippet.wifiAwarePublish(
          attach_session_id,
          pub_config.to_dict(),
      )

    # Wait for publish session to start.
    discovery_event = publish_handler.waitAndGet(
        event_name=constants.DiscoverySessionCallbackMethodType.DISCOVER_RESULT,
        timeout=_DEFAULT_TIMEOUT,
    )
    callback_name = discovery_event.data[_CALLBACK_NAME]
    asserts.assert_equal(
        constants.DiscoverySessionCallbackMethodType.PUBLISH_STARTED,
        callback_name,
        f'{p_dut} {pub_config.service_name} publish failed: {callback_name}.',
    )
    return publish_handler

  def _start_subscribe(
      self,
      s_dut: android_device.AndroidDevice,
      attach_session_id: str,
      sub_config: constants.SubscribeConfig,
      update_sub: bool = False,
  ) -> callback_event.CallbackEvent:
    """Starts a subscribe session on the subscriber device."""

    # Start the subscription session and return the handler.
    if update_sub:
      subscribe_handler = s_dut.wifi_aware_snippet.wifiAwareUpdateSubscribe(
          attach_session_id, sub_config.to_dict()
      )
    else:
      subscribe_handler = s_dut.wifi_aware_snippet.wifiAwareSubscribe(
          attach_session_id, sub_config.to_dict()
      )

    # Wait for subscribe session to start.
    discovery_event = subscribe_handler.waitAndGet(
        event_name=constants.DiscoverySessionCallbackMethodType.DISCOVER_RESULT,
        timeout=_DEFAULT_TIMEOUT,
    )
    callback_name = discovery_event.data[_CALLBACK_NAME]
    asserts.assert_equal(
        constants.DiscoverySessionCallbackMethodType.SUBSCRIBE_STARTED,
        callback_name,
        f'{s_dut} {sub_config.service_name} subscribe failed: {callback_name}.',
    )
    return subscribe_handler

  def run_discovery(
      self,
      p_config: dict[str, any],
      s_config: dict[str, any],
      expect_discovery: bool,
      expect_range: bool = False,
  ) -> tuple[android_device.AndroidDevice, android_device.AndroidDevice,
             callback_handler_v2.CallbackHandlerV2,
             callback_handler_v2.CallbackHandlerV2]:
    """Run discovery on the 2 input devices with the specified configurations.

    Args:
      p_config: Publisher discovery configuration.
      s_config: Subscriber discovery configuration.
      expect_discovery: True or False indicating whether discovery is expected
      with the specified configurations.
      expect_range: True if we expect distance results (i.e. ranging to happen).
      Only relevant if expect_discovery is True.

    Returns:
      p_dut, s_dut: Publisher/Subscribe DUT
      p_disc_id, s_disc_id: Publisher/Subscribe discovery session ID
    """
    p_dut = self.ads[0]
    p_dut.pretty_name = 'Publisher'
    s_dut = self.ads[1]
    s_dut.pretty_name = 'Subscriber'
    p_dut.log.info(p_config)
    p_dut.log.info(s_config)

    # Publisher+Subscriber: attach and wait for confirmation.
    p_id = p_dut.wifi_aware_snippet.wifiAwareAttached(False)
    p_id.waitAndGet(constants.AttachCallBackMethodType.ATTACHED)
    time.sleep(self.device_startup_offset)
    s_id = s_dut.wifi_aware_snippet.wifiAwareAttached(False)
    s_id.waitAndGet(constants.AttachCallBackMethodType.ATTACHED)

    # Publisher: start publish and wait for confirmation.
    p_dut.log.info('start publish')
    p_disc_id = p_dut.wifi_aware_snippet.wifiAwarePublish(p_id.callback_id,
                                                          p_config.to_dict())
    p_discovery = p_disc_id.waitAndGet(
        constants.DiscoverySessionCallbackMethodType.DISCOVER_RESULT)
    callback_name = p_discovery.data[_CALLBACK_NAME]
    asserts.assert_equal(
        constants.DiscoverySessionCallbackMethodType.PUBLISH_STARTED,
        callback_name,
        f'{p_dut} publish failed, got callback: {callback_name}.',
        )
    time.sleep(1)
    s_dut.log.info('start subscribe')
    # Subscriber: start subscribe and wait for confirmation.
    s_disc_id = s_dut.wifi_aware_snippet.wifiAwareSubscribe(s_id.callback_id,
                                                            s_config.to_dict())
    s_discovery = s_disc_id.waitAndGet(
        constants.DiscoverySessionCallbackMethodType.DISCOVER_RESULT)
    callback_name = s_discovery.data[_CALLBACK_NAME]
    asserts.assert_equal(
        constants.DiscoverySessionCallbackMethodType.SUBSCRIBE_STARTED,
        callback_name,
        f'{s_dut} subscribe failed, got callback: {callback_name}.',
        )

    # Subscriber: wait or fail on service discovery.
    if expect_discovery:
        event_name = (
            constants.DiscoverySessionCallbackMethodType.SERVICE_DISCOVERED
        )
        if expect_range:
            event_name = (
                constants.DiscoverySessionCallbackMethodType
                .SERVICE_DISCOVERED_WITHIN_RANGE
            )
        discover_data = s_disc_id.waitAndGet(
            event_name=event_name, timeout=_DEFAULT_TIMEOUT
        )

        if expect_range:
            asserts.assert_true(
                constants.WifiAwareSnippetParams.DISTANCE_MM
                in discover_data.data,
                'Discovery with ranging expected!',
            )
            s_dut.log.info('distance=%s', discover_data.data[
                constants.WifiAwareSnippetParams.DISTANCE_MM])
        else:
            asserts.assert_false(
                constants.WifiAwareSnippetParams.DISTANCE_MM
                in discover_data.data,
                'Discovery with ranging NOT expected!',
            )
    else:
        s_dut.log.info('onServiceDiscovered NOT expected! wait for timeout.')
        autils.callback_no_response(
            s_disc_id,
            constants.DiscoverySessionCallbackMethodType.SERVICE_DISCOVERED,
            timeout=_DEFAULT_TIMEOUT)

    return p_dut, s_dut, p_disc_id, s_disc_id

  @ApiTest(
    apis=[
        'android.net.wifi.aware.PublishConfig.Builder#setPublishType(PublishConfig.PUBLISH_TYPE_UNSOLICITED)',
        'android.net.wifi.aware.SubscribeConfig.Builder#setSubscribeType(SubscribeConfig.SUBSCRIBE_TYPE_PASSIVE)',
        'android.net.wifi.aware.PublishConfig.Builder#setRangingEnabled(boolean enable)',
        'android.net.wifi.aware.DiscoverySessionCallback#onServiceDiscovered(android.net.wifi.aware.ServiceDiscoveryInfo info)',
    ]
  )
  def test_ranged_discovery_unsolicited_passive_prange_snorange(self):
    """Verify discovery(unsolicited/passive) with ranging.

    - Unsolicited Publish/Passive Subscribe
    - Publisher enables ranging
    - Subscriber disables ranging

    Expect: normal discovery (as if no ranging performed) - no distance
    """
    (p_dut, s_dut, p_disc_id, s_disc_id) = self.run_discovery(
        p_config=constants.PublishConfig(
            service_name=self.SERVICE_NAME,
            publish_type=constants.PublishType.UNSOLICITED,
            ranging_enabled=True,
        ),
        s_config=constants.SubscribeConfig(
            service_name=self.SERVICE_NAME,
            subscribe_type=constants.SubscribeType.PASSIVE,
            service_specific_info=self.getname().encode(),
        ),
        expect_discovery=True,
        expect_range=False,
    )
    p_dut.wifi_aware_snippet.wifiAwareCloseDiscoverSession(
        p_disc_id.callback_id)
    s_dut.wifi_aware_snippet.wifiAwareCloseDiscoverSession(
        s_disc_id.callback_id)

  @ApiTest(
    apis=[
        'android.net.wifi.aware.PublishConfig.Builder#setPublishType(PublishConfig.PUBLISH_TYPE_SOLICITED)',
        'android.net.wifi.aware.SubscribeConfig.Builder#setSubscribeType(SubscribeConfig.SUBSCRIBE_TYPE_ACTIVE)',
        'android.net.wifi.aware.PublishConfig.Builder#setRangingEnabled(boolean enable)',
        'android.net.wifi.aware.DiscoverySessionCallback#onServiceDiscovered(android.net.wifi.aware.ServiceDiscoveryInfo info)',
    ]
  )
  def test_ranged_discovery_solicited_active_prange_snorange(self):
    """Verify discovery(solicited/active) with ranging.

    - Solicited Publish/Active Subscribe
    - Publisher enables ranging
    - Subscriber disables ranging

    Expect: normal discovery (as if no ranging performed) - no distance
    """
    (p_dut, s_dut, p_disc_id, s_disc_id) = self.run_discovery(
        p_config=constants.PublishConfig(
            service_name=self.SERVICE_NAME,
            publish_type=constants.PublishType.SOLICITED,
            service_specific_info=self.getname().encode(),
            ranging_enabled=True,
        ),
        s_config=constants.SubscribeConfig(
            service_name=self.SERVICE_NAME,
            subscribe_type=constants.SubscribeType.ACTIVE,
            service_specific_info=self.getname().encode(),
        ),
        expect_discovery=True,
        expect_range=False,
    )
    p_dut.wifi_aware_snippet.wifiAwareCloseDiscoverSession(
        p_disc_id.callback_id)
    s_dut.wifi_aware_snippet.wifiAwareCloseDiscoverSession(
        s_disc_id.callback_id)

  @ApiTest(
    apis=[
        'android.net.wifi.aware.PublishConfig.Builder#setPublishType(PublishConfig.PUBLISH_TYPE_UNSOLICITED)',
        'android.net.wifi.aware.SubscribeConfig.Builder#setSubscribeType(SubscribeConfig.SUBSCRIBE_TYPE_PASSIVE)',
        'android.net.wifi.aware.SubscribeConfig.Builder#setMaxDistanceMm(int)',
        'android.net.wifi.aware.DiscoverySessionCallback#onServiceDiscovered(android.net.wifi.aware.ServiceDiscoveryInfo info)',
    ]
  )
  def test_ranged_discovery_unsolicited_passive_pnorange_smax_inrange(self):
    """Verify discovery(unsolicited/passive) with ranging.

    - Unsolicited Publish/Passive Subscribe
    - Publisher disables ranging
    - Subscriber enables ranging with max such that always within range (large
      max)

    Expect: normal discovery (as if no ranging performed) - no distance
    """
    (p_dut, s_dut, p_disc_id, s_disc_id) = self.run_discovery(
        p_config=constants.PublishConfig(
            service_name=self.SERVICE_NAME,
            publish_type=constants.PublishType.UNSOLICITED,
            service_specific_info=self.getname().encode(),
            ranging_enabled=False,
        ),
        s_config=constants.SubscribeConfig(
            service_name=self.SERVICE_NAME,
            subscribe_type=constants.SubscribeType.PASSIVE,
            service_specific_info=self.getname().encode(),
            min_distance_mm=None,
            max_distance_mm=_LARGE_ENOUGH_DISTANCE_MM,
        ),
        expect_discovery=True,
        expect_range=False,
    )
    p_dut.wifi_aware_snippet.wifiAwareCloseDiscoverSession(
        p_disc_id.callback_id)
    s_dut.wifi_aware_snippet.wifiAwareCloseDiscoverSession(
        s_disc_id.callback_id)

  @ApiTest(
    apis = [
        'android.net.wifi.aware.PublishConfig.Builder#setPublishType(PublishConfig.PUBLISH_TYPE_SOLICITED)',
        'android.net.wifi.aware.SubscribeConfig.Builder#setSubscribeType(SubscribeConfig.SUBSCRIBE_TYPE_ACTIVE)',
        'android.net.wifi.aware.SubscribeConfig.Builder#setMaxDistanceMm(int)',
        'android.net.wifi.aware.DiscoverySessionCallback#onServiceDiscovered(android.net.wifi.aware.ServiceDiscoveryInfo info)',
    ]
  )
  def test_ranged_discovery_solicited_active_pnorange_smax_inrange(self):
    """Verify discovery(solicited/active) with ranging.

    - Solicited Publish/Active Subscribe
    - Publisher disables ranging
    - Subscriber enables ranging with max such that always within range (large
      max)

    Expect: normal discovery (as if no ranging performed) - no distance
    """
    (p_dut, s_dut, p_disc_id, s_disc_id) = self.run_discovery(
        p_config=constants.PublishConfig(
            service_name=self.SERVICE_NAME,
            publish_type=constants.PublishType.SOLICITED,
            service_specific_info=self.getname().encode(),
            ranging_enabled=False,
        ),
        s_config=constants.SubscribeConfig(
            service_name=self.SERVICE_NAME,
            subscribe_type=constants.SubscribeType.ACTIVE,
            service_specific_info=self.getname().encode(),
            min_distance_mm=None,
            max_distance_mm=_LARGE_ENOUGH_DISTANCE_MM,
        ),
        expect_discovery=True,
        expect_range=False,
    )
    p_dut.wifi_aware_snippet.wifiAwareCloseDiscoverSession(
        p_disc_id.callback_id)
    s_dut.wifi_aware_snippet.wifiAwareCloseDiscoverSession(
        s_disc_id.callback_id)

  @ApiTest(
    apis = [
        'android.net.wifi.aware.PublishConfig.Builder#setPublishType(PublishConfig.PUBLISH_TYPE_UNSOLICITED)',
        'android.net.wifi.aware.SubscribeConfig.Builder#setSubscribeType(SubscribeConfig.SUBSCRIBE_TYPE_PASSIVE)',
        'android.net.wifi.aware.SubscribeConfig.Builder#setMinDistanceMm(int)',
        'android.net.wifi.aware.DiscoverySessionCallback#onServiceDiscovered(android.net.wifi.aware.ServiceDiscoveryInfo info)',
    ]
  )
  def test_ranged_discovery_unsolicited_passive_pnorange_smin_outofrange(self):
    """Verify discovery(unsolicited/passive) with ranging.

    - Unsolicited Publish/Passive Subscribe
    - Publisher disables ranging
    - Subscriber enables ranging with min such that always out of range (large
      min)

    Expect: normal discovery (as if no ranging performed) - no distance
    """
    (p_dut, s_dut, p_disc_id, s_disc_id) = self.run_discovery(
        p_config=constants.PublishConfig(
            service_name=self.SERVICE_NAME,
            publish_type=constants.PublishType.UNSOLICITED,
            service_specific_info=self.getname().encode(),
            ranging_enabled=False,
        ),
        s_config=constants.SubscribeConfig(
            service_name=self.SERVICE_NAME,
            subscribe_type=constants.SubscribeType.PASSIVE,
            service_specific_info=self.getname().encode(),
            min_distance_mm=_LARGE_ENOUGH_DISTANCE_MM,
            max_distance_mm=None,
        ),
        expect_discovery=True,
        expect_range=False,
    )
    p_dut.wifi_aware_snippet.wifiAwareCloseDiscoverSession(
        p_disc_id.callback_id)
    s_dut.wifi_aware_snippet.wifiAwareCloseDiscoverSession(
        s_disc_id.callback_id)

  @ApiTest(
    apis = [
        'android.net.wifi.aware.PublishConfig.Builder#setPublishType(PublishConfig.PUBLISH_TYPE_SOLICITED)',
        'android.net.wifi.aware.SubscribeConfig.Builder#setSubscribeType(SubscribeConfig.SUBSCRIBE_TYPE_ACTIVE)',
        'android.net.wifi.aware.SubscribeConfig.Builder#setMinDistanceMm(int)',
        'android.net.wifi.aware.DiscoverySessionCallback#onServiceDiscovered(android.net.wifi.aware.ServiceDiscoveryInfo info)',
    ]
  )
  def test_ranged_discovery_solicited_active_pnorange_smin_outofrange(self):
    """Verify discovery(solicited/active) with ranging.

    - Solicited Publish/Active Subscribe
    - Publisher disables ranging
    - Subscriber enables ranging with min such that always out of range (large
      min)

    Expect: normal discovery (as if no ranging performed) - no distance
    """
    (p_dut, s_dut, p_disc_id, s_disc_id) = self.run_discovery(
        p_config=constants.PublishConfig(
            service_name=self.SERVICE_NAME,
            publish_type=constants.PublishType.SOLICITED,
            service_specific_info=self.getname().encode(),
            ranging_enabled=False,
        ),
        s_config=constants.SubscribeConfig(
            service_name=self.SERVICE_NAME,
            subscribe_type=constants.SubscribeType.ACTIVE,
            service_specific_info=self.getname().encode(),
            min_distance_mm=_LARGE_ENOUGH_DISTANCE_MM,
            max_distance_mm=None,
        ),
        expect_discovery=True,
        expect_range=False,
    )
    p_dut.wifi_aware_snippet.wifiAwareCloseDiscoverSession(
        p_disc_id.callback_id)
    s_dut.wifi_aware_snippet.wifiAwareCloseDiscoverSession(
        s_disc_id.callback_id)

  @ApiTest(
    apis = [
        'android.net.wifi.aware.PublishConfig.Builder#setPublishType(PublishConfig.PUBLISH_TYPE_UNSOLICITED)',
        'android.net.wifi.aware.SubscribeConfig.Builder#setSubscribeType(SubscribeConfig.SUBSCRIBE_TYPE_PASSIVE)',
        'android.net.wifi.aware.PublishConfig.Builder#setRangingEnabled(boolean enable)',
        'android.net.wifi.aware.SubscribeConfig.Builder#setMinDistanceMm(int)',
        'android.net.wifi.aware.DiscoverySessionCallback#onServiceDiscovered(android.net.wifi.aware.ServiceDiscoveryInfo info)',
        'android.net.wifi.aware.DiscoverySessionCallback#onServiceDiscoveredWithinRange(android.net.wifi.aware.PeerHandle peerHandle, byte[] serviceSpecificInfo, List<byte[]> matchFilter, int distanceMm)',
    ]
  )
  def test_ranged_discovery_unsolicited_passive_prange_smin_inrange(self):
    """Verify discovery(unsolicited/passive) with ranging.

    - Unsolicited Publish/Passive Subscribe
    - Publisher enables ranging
    - Subscriber enables ranging with min such that in range (min=0)

    Expect: discovery with distance
    """
    (p_dut, s_dut, p_disc_id, s_disc_id) = self.run_discovery(
        p_config=constants.PublishConfig(
            service_name=self.SERVICE_NAME,
            publish_type=constants.PublishType.UNSOLICITED,
            service_specific_info=self.getname().encode(),
            ranging_enabled=True,
        ),
        s_config=constants.SubscribeConfig(
            service_name=self.SERVICE_NAME,
            subscribe_type=constants.SubscribeType.PASSIVE,
            service_specific_info=self.getname().encode(),
            min_distance_mm=0,
            max_distance_mm=None,
        ),
        expect_discovery=True,
        expect_range=True,
    )
    p_dut.wifi_aware_snippet.wifiAwareCloseDiscoverSession(
        p_disc_id.callback_id)
    s_dut.wifi_aware_snippet.wifiAwareCloseDiscoverSession(
        s_disc_id.callback_id)

  @ApiTest(
    apis = [
        'android.net.wifi.aware.PublishConfig.Builder#setPublishType(PublishConfig.PUBLISH_TYPE_UNSOLICITED)',
        'android.net.wifi.aware.SubscribeConfig.Builder#setSubscribeType(SubscribeConfig.SUBSCRIBE_TYPE_PASSIVE)',
        'android.net.wifi.aware.PublishConfig.Builder#setRangingEnabled(boolean enable)',
        'android.net.wifi.aware.SubscribeConfig.Builder#setMaxDistanceMm(int)',
        'android.net.wifi.aware.DiscoverySessionCallback#onServiceDiscovered(android.net.wifi.aware.ServiceDiscoveryInfo info)',
        'android.net.wifi.aware.DiscoverySessionCallback#onServiceDiscoveredWithinRange(android.net.wifi.aware.PeerHandle peerHandle, byte[] serviceSpecificInfo, java.util.List<byte[]> matchFilter, int distanceMm)',
    ]
  )
  def test_ranged_discovery_unsolicited_passive_prange_smax_inrange(self):
    """Verify discovery(unsolicited/passive) with ranging.

    - Unsolicited Publish/Passive Subscribe
    - Publisher enables ranging
    - Subscriber enables ranging with max such that in range (max=large)

    Expect: discovery with distance
    """
    (p_dut, s_dut, p_disc_id, s_disc_id) = self.run_discovery(
        p_config=constants.PublishConfig(
            service_name=self.SERVICE_NAME,
            publish_type=constants.PublishType.UNSOLICITED,
            service_specific_info=self.getname().encode(),
            ranging_enabled=True,
        ),
        s_config=constants.SubscribeConfig(
            service_name=self.SERVICE_NAME,
            subscribe_type=constants.SubscribeType.PASSIVE,
            service_specific_info=self.getname().encode(),
            min_distance_mm=None,
            max_distance_mm=_LARGE_ENOUGH_DISTANCE_MM,
        ),
        expect_discovery=True,
        expect_range=True,
    )
    p_dut.wifi_aware_snippet.wifiAwareCloseDiscoverSession(
        p_disc_id.callback_id)
    s_dut.wifi_aware_snippet.wifiAwareCloseDiscoverSession(
        s_disc_id.callback_id)

  @ApiTest(
    apis = [
        'android.net.wifi.aware.PublishConfig.Builder#setPublishType(PublishConfig.PUBLISH_TYPE_UNSOLICITED)',
        'android.net.wifi.aware.SubscribeConfig.Builder#setSubscribeType(SubscribeConfig.SUBSCRIBE_TYPE_PASSIVE)',
        'android.net.wifi.aware.PublishConfig.Builder#setRangingEnabled(boolean enable)',
        'android.net.wifi.aware.SubscribeConfig.Builder#setMinDistanceMm(int)',
        'android.net.wifi.aware.SubscribeConfig.Builder#setMaxDistanceMm(int)',
        'android.net.wifi.aware.DiscoverySessionCallback#onServiceDiscovered(android.net.wifi.aware.ServiceDiscoveryInfo info)',
        'android.net.wifi.aware.DiscoverySessionCallback#onServiceDiscoveredWithinRange(android.net.wifi.aware.PeerHandle peerHandle, byte[] serviceSpecificInfo, List<byte[]> matchFilter, int distanceMm)',
    ]
  )
  def test_ranged_discovery_unsolicited_passive_prange_sminmax_inrange(self):
    """Verify discovery(unsolicited/passive) with ranging.

    - Unsolicited Publish/Passive Subscribe
    - Publisher enables ranging
    - Subscriber enables ranging with min/max such that in range (min=0,
      max=large)

    Expect: discovery with distance
    """
    (p_dut, s_dut, p_disc_id, s_disc_id) = self.run_discovery(
        p_config=constants.PublishConfig(
            service_name=self.SERVICE_NAME,
            publish_type=constants.PublishType.UNSOLICITED,
            service_specific_info=self.getname().encode(),
            ranging_enabled=True,
        ),
        s_config=constants.SubscribeConfig(
            service_name=self.SERVICE_NAME,
            subscribe_type=constants.SubscribeType.PASSIVE,
            service_specific_info=self.getname().encode(),
            min_distance_mm=0,
            max_distance_mm=_LARGE_ENOUGH_DISTANCE_MM,
        ),
        expect_discovery=True,
        expect_range=True,
    )
    p_dut.wifi_aware_snippet.wifiAwareCloseDiscoverSession(
        p_disc_id.callback_id)
    s_dut.wifi_aware_snippet.wifiAwareCloseDiscoverSession(
        s_disc_id.callback_id)

  @ApiTest(
    apis = [
        'android.net.wifi.aware.PublishConfig.Builder#setPublishType(PublishConfig.PUBLISH_TYPE_SOLICITED)',
        'android.net.wifi.aware.SubscribeConfig.Builder#setSubscribeType(SubscribeConfig.SUBSCRIBE_TYPE_ACTIVE)',
        'android.net.wifi.aware.PublishConfig.Builder#setRangingEnabled(boolean enable)',
        'android.net.wifi.aware.SubscribeConfig.Builder#setMinDistanceMm(int)',
        'android.net.wifi.aware.DiscoverySessionCallback#onServiceDiscovered(android.net.wifi.aware.ServiceDiscoveryInfo info)',
        'android.net.wifi.aware.DiscoverySessionCallback#onServiceDiscoveredWithinRange(android.net.wifi.aware.PeerHandle peerHandle, byte[] serviceSpecificInfo, List<byte[]> matchFilter, int distanceMm)',
    ]
  )
  def test_ranged_discovery_solicited_active_prange_smin_inrange(self):
    """Verify discovery(solicited/active) with ranging.

    - Solicited Publish/Active Subscribe
    - Publisher enables ranging
    - Subscriber enables ranging with min such that in range (min=0)

    Expect: discovery with distance
    """
    (p_dut, s_dut, p_disc_id, s_disc_id) = self.run_discovery(
        p_config=constants.PublishConfig(
            service_name=self.SERVICE_NAME,
            publish_type=constants.PublishType.SOLICITED,
            service_specific_info=self.getname().encode(),
            ranging_enabled=True,
        ),
        s_config=constants.SubscribeConfig(
            service_name=self.SERVICE_NAME,
            subscribe_type=constants.SubscribeType.ACTIVE,
            service_specific_info=self.getname().encode(),
            min_distance_mm=0,
            max_distance_mm=None,
        ),
        expect_discovery=True,
        expect_range=True,
    )
    p_dut.wifi_aware_snippet.wifiAwareCloseDiscoverSession(
        p_disc_id.callback_id)
    s_dut.wifi_aware_snippet.wifiAwareCloseDiscoverSession(
        s_disc_id.callback_id)

  @ApiTest(
    apis = [
        'android.net.wifi.aware.PublishConfig.Builder#setPublishType(PublishConfig.PUBLISH_TYPE_SOLICITED)',
        'android.net.wifi.aware.SubscribeConfig.Builder#setSubscribeType(SubscribeConfig.SUBSCRIBE_TYPE_ACTIVE)',
        'android.net.wifi.aware.PublishConfig.Builder#setRangingEnabled(boolean enable)',
        'android.net.wifi.aware.SubscribeConfig.Builder#setMaxDistanceMm(int)',
        'android.net.wifi.aware.DiscoverySessionCallback#onServiceDiscovered(android.net.wifi.aware.ServiceDiscoveryInfo info)',
        'android.net.wifi.aware.DiscoverySessionCallback#onServiceDiscoveredWithinRange(android.net.wifi.aware.PeerHandle peerHandle, byte[] serviceSpecificInfo, List<byte[]> matchFilter, int distanceMm)',
    ]
  )
  def test_ranged_discovery_solicited_active_prange_smax_inrange(self):
    """Verify discovery(solicited/active) with ranging.

    - Solicited Publish/Active Subscribe
    - Publisher enables ranging
    - Subscriber enables ranging with max such that in range (max=large)

    Expect: discovery with distance
    """
    (p_dut, s_dut, p_disc_id, s_disc_id) = self.run_discovery(
        p_config=constants.PublishConfig(
            service_name=self.SERVICE_NAME,
            publish_type=constants.PublishType.SOLICITED,
            service_specific_info=self.getname().encode(),
            ranging_enabled=True,
        ),
        s_config=constants.SubscribeConfig(
            service_name=self.SERVICE_NAME,
            subscribe_type=constants.SubscribeType.ACTIVE,
            service_specific_info=self.getname().encode(),
            min_distance_mm=None,
            max_distance_mm=_LARGE_ENOUGH_DISTANCE_MM,
        ),
        expect_discovery=True,
        expect_range=True,
    )
    p_dut.wifi_aware_snippet.wifiAwareCloseDiscoverSession(
        p_disc_id.callback_id)
    s_dut.wifi_aware_snippet.wifiAwareCloseDiscoverSession(
        s_disc_id.callback_id)

  @ApiTest(
    apis = [
        'android.net.wifi.aware.PublishConfig.Builder#setPublishType(PublishConfig.PUBLISH_TYPE_SOLICITED)',
        'android.net.wifi.aware.SubscribeConfig.Builder#setSubscribeType(SubscribeConfig.SUBSCRIBE_TYPE_ACTIVE)',
        'android.net.wifi.aware.PublishConfig.Builder#setRangingEnabled(boolean enable)',
        'android.net.wifi.aware.SubscribeConfig.Builder#setMinDistanceMm(int)',
        'android.net.wifi.aware.SubscribeConfig.Builder#setMaxDistanceMm(int)',
        'android.net.wifi.aware.DiscoverySessionCallback#onServiceDiscovered(android.net.wifi.aware.ServiceDiscoveryInfo info)',
        'android.net.wifi.aware.DiscoverySessionCallback#onServiceDiscoveredWithinRange(android.net.wifi.aware.PeerHandle peerHandle, byte[] serviceSpecificInfo, List<byte[]> matchFilter, int distanceMm)',
    ]
  )
  def test_ranged_discovery_solicited_active_prange_sminmax_inrange(self):
    """Verify discovery(solicited/active) with ranging.

    - Solicited Publish/Active Subscribe
    - Publisher enables ranging
    - Subscriber enables ranging with min/max such that in range (min=0,
      max=large)

    Expect: discovery with distance
    """
    (p_dut, s_dut, p_disc_id, s_disc_id) = self.run_discovery(
        p_config=constants.PublishConfig(
            service_name=self.SERVICE_NAME,
            publish_type=constants.PublishType.SOLICITED,
            service_specific_info=self.getname().encode(),
            ranging_enabled=True,
        ),
        s_config=constants.SubscribeConfig(
            service_name=self.SERVICE_NAME,
            subscribe_type=constants.SubscribeType.ACTIVE,
            service_specific_info=self.getname().encode(),
            min_distance_mm=0,
            max_distance_mm=_LARGE_ENOUGH_DISTANCE_MM,
        ),
        expect_discovery=True,
        expect_range=True,
    )
    p_dut.wifi_aware_snippet.wifiAwareCloseDiscoverSession(
        p_disc_id.callback_id)
    s_dut.wifi_aware_snippet.wifiAwareCloseDiscoverSession(
        s_disc_id.callback_id)

  @ApiTest(
    apis = [
        'android.net.wifi.aware.PublishConfig.Builder#setPublishType(PublishConfig.PUBLISH_TYPE_UNSOLICITED)',
        'android.net.wifi.aware.SubscribeConfig.Builder#setSubscribeType(SubscribeConfig.SUBSCRIBE_TYPE_PASSIVE)',
        'android.net.wifi.aware.PublishConfig.Builder#setRangingEnabled(boolean enable)',
        'android.net.wifi.aware.SubscribeConfig.Builder#setMinDistanceMm(int)',
    ]
  )
  def test_ranged_discovery_unsolicited_passive_prange_smin_outofrange(self):
    """Verify discovery(unsolicited/passive) with ranging.

    - Unsolicited Publish/Passive Subscribe
    - Publisher enables ranging
    - Subscriber enables ranging with min such that out of range (min=large)

    Expect: no discovery
    """
    (p_dut, s_dut, p_disc_id, s_disc_id) = self.run_discovery(
        p_config=constants.PublishConfig(
            service_name=self.SERVICE_NAME,
            publish_type=constants.PublishType.UNSOLICITED,
            service_specific_info=self.getname().encode(),
            ranging_enabled=True,
        ),
        s_config=constants.SubscribeConfig(
            service_name=self.SERVICE_NAME,
            subscribe_type=constants.SubscribeType.PASSIVE,
            service_specific_info=self.getname().encode(),
            min_distance_mm=_LARGE_ENOUGH_DISTANCE_MM,
            max_distance_mm=None,
        ),
        expect_discovery=False,
    )
    p_dut.wifi_aware_snippet.wifiAwareCloseDiscoverSession(
        p_disc_id.callback_id)
    s_dut.wifi_aware_snippet.wifiAwareCloseDiscoverSession(
        s_disc_id.callback_id)

  @ApiTest(
    apis = [
        'android.net.wifi.aware.PublishConfig.Builder#setPublishType(PublishConfig.PUBLISH_TYPE_UNSOLICITED)',
        'android.net.wifi.aware.SubscribeConfig.Builder#setSubscribeType(SubscribeConfig.SUBSCRIBE_TYPE_PASSIVE)',
        'android.net.wifi.aware.PublishConfig.Builder#setRangingEnabled(boolean enable)',
        'android.net.wifi.aware.SubscribeConfig.Builder#setMaxDistanceMm(int)',
        'android.net.wifi.aware.DiscoverySessionCallback#onServiceDiscovered(android.net.wifi.aware.ServiceDiscoveryInfo info)',
    ]
  )
  def test_ranged_discovery_unsolicited_passive_prange_smax_outofrange(self):
    """Verify discovery(unsolicited/passive) with ranging.

    - Unsolicited Publish/Passive Subscribe
    - Publisher enables ranging
    - Subscriber enables ranging with max such that in range (max=0)

    Expect: no discovery
    """
    (p_dut, s_dut, p_disc_id, s_disc_id) = self.run_discovery(
        p_config=constants.PublishConfig(
            service_name=self.SERVICE_NAME,
            publish_type=constants.PublishType.UNSOLICITED,
            service_specific_info=self.getname().encode(),
            ranging_enabled=True,
        ),
        s_config=constants.SubscribeConfig(
            service_name=self.SERVICE_NAME,
            subscribe_type=constants.SubscribeType.PASSIVE,
            service_specific_info=self.getname().encode(),
            min_distance_mm=None,
            max_distance_mm=0,
        ),
        expect_discovery=True,
        expect_range=False,
    )
    p_dut.wifi_aware_snippet.wifiAwareCloseDiscoverSession(
        p_disc_id.callback_id)
    s_dut.wifi_aware_snippet.wifiAwareCloseDiscoverSession(
        s_disc_id.callback_id)

  @ApiTest(
    apis = [
        'android.net.wifi.aware.PublishConfig.Builder#setPublishType(PublishConfig.PUBLISH_TYPE_UNSOLICITED)',
        'android.net.wifi.aware.SubscribeConfig.Builder#setSubscribeType(SubscribeConfig.SUBSCRIBE_TYPE_PASSIVE)',
        'android.net.wifi.aware.PublishConfig.Builder#setRangingEnabled(boolean enable)',
        'android.net.wifi.aware.SubscribeConfig.Builder#setMinDistanceMm(int)',
        'android.net.wifi.aware.SubscribeConfig.Builder#setMaxDistanceMm(int)',
    ]
  )
  def test_ranged_discovery_unsolicited_passive_prange_sminmax_outofrange(self):
    """Verify discovery(unsolicited/passive) with ranging.

    - Unsolicited Publish/Passive Subscribe
    - Publisher enables ranging
    - Subscriber enables ranging with min/max such that out of range (min=large,
      max=large+1)

    Expect: no discovery
    """
    (p_dut, s_dut, p_disc_id, s_disc_id) = self.run_discovery(
        p_config=constants.PublishConfig(
            service_name=self.SERVICE_NAME,
            publish_type=constants.PublishType.UNSOLICITED,
            service_specific_info=self.getname().encode(),
            ranging_enabled=True,
        ),
        s_config=constants.SubscribeConfig(
            service_name=self.SERVICE_NAME,
            subscribe_type=constants.SubscribeType.PASSIVE,
            service_specific_info=self.getname().encode(),
            min_distance_mm=_LARGE_ENOUGH_DISTANCE_MM,
            max_distance_mm=_LARGE_ENOUGH_DISTANCE_MM + 1,
        ),
        expect_discovery=False,
    )
    p_dut.wifi_aware_snippet.wifiAwareCloseDiscoverSession(
        p_disc_id.callback_id)
    s_dut.wifi_aware_snippet.wifiAwareCloseDiscoverSession(
        s_disc_id.callback_id)

  @ApiTest(
    apis = [
        'android.net.wifi.aware.PublishConfig.Builder#setPublishType(PublishConfig.PUBLISH_TYPE_SOLICITED)',
        'android.net.wifi.aware.SubscribeConfig.Builder#setSubscribeType(SubscribeConfig.SUBSCRIBE_TYPE_ACTIVE)',
        'android.net.wifi.aware.PublishConfig.Builder#setRangingEnabled(boolean enable)',
        'android.net.wifi.aware.SubscribeConfig.Builder#setMinDistanceMm(int)',
    ]
  )
  def test_ranged_discovery_solicited_active_prange_smin_outofrange(self):
    """Verify discovery(solicited/active) with ranging.

    - Solicited Publish/Active Subscribe
    - Publisher enables ranging
    - Subscriber enables ranging with min such that out of range (min=large)

    Expect: no discovery
    """
    (p_dut, s_dut, p_disc_id, s_disc_id) = self.run_discovery(
        p_config=constants.PublishConfig(
            service_name=self.SERVICE_NAME,
            publish_type=constants.PublishType.SOLICITED,
            service_specific_info=self.getname().encode(),
            ranging_enabled=True,
        ),
        s_config=constants.SubscribeConfig(
            service_name=self.SERVICE_NAME,
            subscribe_type=constants.SubscribeType.ACTIVE,
            service_specific_info=self.getname().encode(),
            min_distance_mm=_LARGE_ENOUGH_DISTANCE_MM,
            max_distance_mm=None,
        ),
        expect_discovery=False,
    )
    p_dut.wifi_aware_snippet.wifiAwareCloseDiscoverSession(
        p_disc_id.callback_id)
    s_dut.wifi_aware_snippet.wifiAwareCloseDiscoverSession(
        s_disc_id.callback_id)

  @ApiTest(
    apis = [
        'android.net.wifi.aware.PublishConfig.Builder#setPublishType(PublishConfig.PUBLISH_TYPE_SOLICITED)',
        'android.net.wifi.aware.SubscribeConfig.Builder#setSubscribeType(SubscribeConfig.SUBSCRIBE_TYPE_ACTIVE)',
        'android.net.wifi.aware.PublishConfig.Builder#setRangingEnabled(boolean enable)',
        'android.net.wifi.aware.SubscribeConfig.Builder#setMaxDistanceMm(int)',
    ]
  )
  def test_ranged_discovery_solicited_active_prange_smax_outofrange(self):
    """Verify discovery(solicited/active) with ranging.

    - Solicited Publish/Active Subscribe
    - Publisher enables ranging
    - Subscriber enables ranging with max such that out of range (max=0)

    Expect: no discovery
    """
    (p_dut, s_dut, p_disc_id, s_disc_id) = self.run_discovery(
        p_config=constants.PublishConfig(
            service_name=self.SERVICE_NAME,
            publish_type=constants.PublishType.SOLICITED,
            service_specific_info=self.getname().encode(),
            ranging_enabled=True,
        ),
        s_config=constants.SubscribeConfig(
            service_name=self.SERVICE_NAME,
            subscribe_type=constants.SubscribeType.ACTIVE,
            service_specific_info=self.getname().encode(),
            min_distance_mm=None,
            max_distance_mm=0,
        ),
        expect_discovery=True,
        expect_range=False,
    )
    p_dut.wifi_aware_snippet.wifiAwareCloseDiscoverSession(
        p_disc_id.callback_id)
    s_dut.wifi_aware_snippet.wifiAwareCloseDiscoverSession(
        s_disc_id.callback_id)

  @ApiTest(
    apis = [
        'android.net.wifi.aware.PublishConfig.Builder#setPublishType(PublishConfig.PUBLISH_TYPE_SOLICITED)',
        'android.net.wifi.aware.SubscribeConfig.Builder#setSubscribeType(SubscribeConfig.SUBSCRIBE_TYPE_ACTIVE)',
        'android.net.wifi.aware.PublishConfig.Builder#setRangingEnabled(boolean enable)',
        'android.net.wifi.aware.SubscribeConfig.Builder#setMinDistanceMm(int)',
        'android.net.wifi.aware.SubscribeConfig.Builder#setMaxDistanceMm(int)',
    ]
  )
  def test_ranged_discovery_solicited_active_prange_sminmax_outofrange(self):
    """Verify discovery(solicited/active) with ranging.

    - Solicited Publish/Active Subscribe
    - Publisher enables ranging
    - Subscriber enables ranging with min/max such that out of range (min=large,
      max=large+1)

    Expect: no discovery
    """
    (p_dut, s_dut, p_disc_id, s_disc_id) = self.run_discovery(
        p_config=constants.PublishConfig(
            service_name=self.SERVICE_NAME,
            publish_type=constants.PublishType.SOLICITED,
            service_specific_info=self.getname().encode(),
            ranging_enabled=True,
        ),
        s_config=constants.SubscribeConfig(
            service_name=self.SERVICE_NAME,
            subscribe_type=constants.SubscribeType.ACTIVE,
            service_specific_info=self.getname().encode(),
            min_distance_mm=_LARGE_ENOUGH_DISTANCE_MM,
            max_distance_mm=_LARGE_ENOUGH_DISTANCE_MM + 1,
        ),
        expect_discovery=False,
    )
    p_dut.wifi_aware_snippet.wifiAwareCloseDiscoverSession(
        p_disc_id.callback_id)
    s_dut.wifi_aware_snippet.wifiAwareCloseDiscoverSession(
        s_disc_id.callback_id)

  def run_discovery_prange_sminmax_outofrange(
      self,
      is_unsolicited_passive
  ) -> tuple[android_device.AndroidDevice, android_device.AndroidDevice,
             callback_handler_v2.CallbackHandlerV2,
             callback_handler_v2.CallbackHandlerV2]:
    """Run discovery with ranging with configuration type.

    - Publisher enables ranging
    - Subscriber enables ranging with min/max such that out of range (min=large,
      max=large+1)

    Expected: no discovery

    This is a baseline test for the update-configuration tests.

    Args:
      is_unsolicited_passive: True for Unsolicited/Passive, False for
                              Solicited/Active.
    Returns:
      the return arguments of the run_discovery.
    """
    pub_type = (constants.PublishType.UNSOLICITED if is_unsolicited_passive
                else constants.PublishType.SOLICITED)
    sub_type = (constants.SubscribeType.PASSIVE if is_unsolicited_passive
                else constants.SubscribeType.ACTIVE)

    return self.run_discovery(
        p_config=constants.PublishConfig(
            service_name=self.SERVICE_NAME,
            publish_type=pub_type,
            service_specific_info=self.getname(2).encode(),
            ranging_enabled=True,
        ),
        s_config=constants.SubscribeConfig(
            service_name=self.SERVICE_NAME,
            subscribe_type=sub_type,
            service_specific_info=self.getname(2).encode(),
            min_distance_mm=_LARGE_ENOUGH_DISTANCE_MM,
            max_distance_mm=_LARGE_ENOUGH_DISTANCE_MM + 1,
        ),
        expect_discovery=False,
    )

  def run_discovery_update(self,
                           p_dut,
                           s_dut,
                           p_disc_id,
                           s_disc_id,
                           p_config,
                           s_config,
                           expect_discovery,
                           expect_range=False):
    """Run discovery on the 2 input devices with the specified update configurations.

    I.e., update the existing discovery sessions with the configurations.

    Args:
      p_dut: Publisher DUTs.
      s_dut: Subscriber DUT.
      p_disc_id: Publisher discovery session IDs.
      s_disc_id: Subscriber discovery session IDs.
      p_config: Publisher discovery configuration.
      s_config: Subscriber discovery configuration.
      expect_discovery: True or False indicating whether discovery is expected
      with the specified configurations.
      expect_range: True if we expect distance results (i.e. ranging to happen).
      Only relevant if expect_discovery is True.
    """
    # try to perform reconfiguration at same time (and wait once for all
    # confirmations)
    if p_config is not None:
        p_dut.wifi_aware_snippet.wifiAwareUpdatePublish(
            p_disc_id.callback_id,
            p_config.to_dict())
    if s_config is not None:
        s_dut.wifi_aware_snippet.wifiAwareUpdateSubscribe(
            s_disc_id.callback_id,
            s_config.to_dict())

    if p_config is not None and p_disc_id is not None:
        p_dut.log.info('Check for publish config updated.')
        p_disc_id.waitAndGet(
            event_name=
            constants.DiscoverySessionCallbackMethodType.SESSION_CONFIG_UPDATED,
            timeout=_DEFAULT_TIMEOUT
        )
    if s_config is not None and s_disc_id is not None:
        s_dut.log.info('Check for subscribe config updated.')
        s_disc_id.waitAndGet(
            event_name=
            constants.DiscoverySessionCallbackMethodType.SESSION_CONFIG_UPDATED,
            timeout=_DEFAULT_TIMEOUT
        )

    # Subscriber: wait or fail on service discovery.
    if expect_discovery:
        event_name = (
            constants.DiscoverySessionCallbackMethodType.SERVICE_DISCOVERED
        )
        if expect_range:
            event_name = (
                constants.DiscoverySessionCallbackMethodType
                .SERVICE_DISCOVERED_WITHIN_RANGE
            )
        discover_data = s_disc_id.waitAndGet(
            event_name=event_name, timeout=_DEFAULT_TIMEOUT
        )

        if expect_range:
            asserts.assert_true(
                constants.WifiAwareSnippetParams.DISTANCE_MM
                in discover_data.data,
                'Discovery with ranging expected!',
            )
            s_dut.log.info('distance=%s', discover_data.data[
                constants.WifiAwareSnippetParams.DISTANCE_MM])
        else:
            asserts.assert_false(
                constants.WifiAwareSnippetParams.DISTANCE_MM
                in discover_data.data,
                'Discovery with ranging NOT expected!',
            )
    else:
        s_dut.log.info('onServiceDiscovered NOT expected! wait for timeout.')
        autils.callback_no_response(
            s_disc_id,
            constants.DiscoverySessionCallbackMethodType.SERVICE_DISCOVERED,
            timeout=_DEFAULT_TIMEOUT)

    time.sleep(autils._EVENT_TIMEOUT)

  @ApiTest(
    apis = [
        'android.net.wifi.aware.PublishConfig.Builder#setPublishType(PublishConfig.PUBLISH_TYPE_UNSOLICITED)',
        'android.net.wifi.aware.SubscribeConfig.Builder#setSubscribeType(SubscribeConfig.SUBSCRIBE_TYPE_PASSIVE)',
        'android.net.wifi.aware.PublishConfig.Builder#setRangingEnabled(boolean enable)',
        'android.net.wifi.aware.SubscribeConfig.Builder#setMinDistanceMm(int)',
        'android.net.wifi.aware.SubscribeConfig.Builder#setMaxDistanceMm(int)',
        'android.net.wifi.aware.DiscoverySessionCallback#onServiceDiscovered(android.net.wifi.aware.ServiceDiscoveryInfo info)',
        'android.net.wifi.aware.DiscoverySessionCallback#onServiceDiscoveredWithinRange(android.net.wifi.aware.PeerHandle peerHandle, byte[] serviceSpecificInfo, List<byte[]> matchFilter, int distanceMm)',
        'android.net.wifi.aware.SubscribeDiscoverySession#updateSubscribe(android.net.wifi.aware.SubscribeConfig subscribeConfig)',
    ]
  )
  def test_ranged_updated_discovery_unsolicited_passive_oor_to_ir(self):
    """Verify discovery with ranging operation with updated configuration.

    - Unsolicited Publish/Passive Subscribe
    - Publisher enables ranging
    - Subscriber:
      - Starts: Ranging enabled, min/max such that out of range (min=large,
                max=large+1)
      - Reconfigured to: Ranging enabled, min/max such that in range (min=0,
                        max=large)

    Expect: discovery + ranging after update
    """
    (p_dut, s_dut, p_disc_id, s_disc_id) = (
        self.run_discovery_prange_sminmax_outofrange(True)
    )
    self.run_discovery_update(
        p_dut,
        s_dut,
        p_disc_id,
        s_disc_id,
        p_config=None,  # no updates
        s_config=constants.SubscribeConfig(
            service_name=self.SERVICE_NAME,
            subscribe_type=constants.SubscribeType.PASSIVE,
            service_specific_info=self.getname().encode(),
            min_distance_mm=0,
            max_distance_mm=_LARGE_ENOUGH_DISTANCE_MM,
        ),
        expect_discovery=True,
        expect_range=True)
    p_dut.wifi_aware_snippet.wifiAwareCloseDiscoverSession(p_disc_id.callback_id)
    s_dut.wifi_aware_snippet.wifiAwareCloseDiscoverSession(s_disc_id.callback_id)

  @ApiTest(
    apis = [
        'android.net.wifi.aware.PublishConfig.Builder#setPublishType(PublishConfig.PUBLISH_TYPE_UNSOLICITED)',
        'android.net.wifi.aware.SubscribeConfig.Builder#setSubscribeType(SubscribeConfig.SUBSCRIBE_TYPE_PASSIVE)',
        'android.net.wifi.aware.PublishConfig.Builder#setRangingEnabled(boolean enable)',
        'android.net.wifi.aware.SubscribeConfig.Builder#setMinDistanceMm(int)',
        'android.net.wifi.aware.SubscribeConfig.Builder#setMaxDistanceMm(int)',
        'android.net.wifi.aware.DiscoverySessionCallback#onServiceDiscovered(android.net.wifi.aware.ServiceDiscoveryInfo info)',
        'android.net.wifi.aware.DiscoverySessionCallback#onServiceDiscoveredWithinRange(android.net.wifi.aware.PeerHandle peerHandle, byte[] serviceSpecificInfo, List<byte[]> matchFilter, int distanceMm)',
        'android.net.wifi.aware.PublishDiscoverySession#updatePublish(android.net.wifi.aware.PublishConfig publishConfig)',
    ]
  )
  def test_ranged_updated_discovery_unsolicited_passive_pub_unrange(self):
    """Verify discovery with ranging operation with updated configuration.

    - Unsolicited Publish/Passive Subscribe
    - Publisher enables ranging
    - Subscriber: Ranging enabled, min/max such that out of range (min=large,
                  max=large+1)
    - Reconfigured to: Publisher disables ranging

    Expect: discovery w/o ranging after update
    """
    (p_dut, s_dut, p_disc_id, s_disc_id) = (
        self.run_discovery_prange_sminmax_outofrange(True)
        )
    self.run_discovery_update(
        p_dut,
        s_dut,
        p_disc_id,
        s_disc_id,
        p_config=constants.PublishConfig(
            service_name=self.SERVICE_NAME,
            publish_type=constants.PublishType.UNSOLICITED,
            service_specific_info=self.getname().encode(),
        ),
        s_config=None,  # no updates
        expect_discovery=True,
        expect_range=False)
    p_dut.wifi_aware_snippet.wifiAwareCloseDiscoverSession(p_disc_id.callback_id)
    s_dut.wifi_aware_snippet.wifiAwareCloseDiscoverSession(s_disc_id.callback_id)

  @ApiTest(
    apis = [
        'android.net.wifi.aware.PublishConfig.Builder#setPublishType(PublishConfig.PUBLISH_TYPE_UNSOLICITED)',
        'android.net.wifi.aware.SubscribeConfig.Builder#setSubscribeType(SubscribeConfig.SUBSCRIBE_TYPE_PASSIVE)',
        'android.net.wifi.aware.PublishConfig.Builder#setRangingEnabled(boolean enable)',
        'android.net.wifi.aware.SubscribeConfig.Builder#setMinDistanceMm(int)',
        'android.net.wifi.aware.SubscribeConfig.Builder#setMaxDistanceMm(int)',
        'android.net.wifi.aware.DiscoverySessionCallback#onServiceDiscovered(android.net.wifi.aware.ServiceDiscoveryInfo info)',
        'android.net.wifi.aware.SubscribeDiscoverySession#updateSubscribe(android.net.wifi.aware.SubscribeConfig subscribeConfig)',
    ]
  )
  def test_ranged_updated_discovery_unsolicited_passive_sub_unrange(self):
    """Verify discovery with ranging operation with updated configuration.

    - Unsolicited Publish/Passive Subscribe
    - Publisher enables ranging
    - Subscriber:
      - Starts: Ranging enabled, min/max such that out of range (min=large,
                max=large+1)
      - Reconfigured to: Ranging disabled

    Expect: discovery w/o ranging after update
    """
    (p_dut, s_dut, p_disc_id, s_disc_id) = (
        self.run_discovery_prange_sminmax_outofrange(True)
    )
    self.run_discovery_update(
        p_dut,
        s_dut,
        p_disc_id,
        s_disc_id,
        p_config=None,  # no updates
        s_config=constants.SubscribeConfig(
            service_name=self.SERVICE_NAME,
            subscribe_type=constants.SubscribeType.PASSIVE,
            service_specific_info=self.getname().encode(),
        ),
        expect_discovery=True,
        expect_range=False)
    p_dut.wifi_aware_snippet.wifiAwareCloseDiscoverSession(p_disc_id.callback_id)
    s_dut.wifi_aware_snippet.wifiAwareCloseDiscoverSession(s_disc_id.callback_id)

  @ApiTest(
    apis = [
        'android.net.wifi.aware.PublishConfig.Builder#setPublishType(PublishConfig.PUBLISH_TYPE_UNSOLICITED)',
        'android.net.wifi.aware.SubscribeConfig.Builder#setSubscribeType(SubscribeConfig.SUBSCRIBE_TYPE_PASSIVE)',
        'android.net.wifi.aware.PublishConfig.Builder#setRangingEnabled(boolean enable)',
        'android.net.wifi.aware.SubscribeConfig.Builder#setMinDistanceMm(int)',
        'android.net.wifi.aware.SubscribeConfig.Builder#setMaxDistanceMm(int)',
        'android.net.wifi.aware.SubscribeDiscoverySession#updateSubscribe(android.net.wifi.aware.SubscribeConfig subscribeConfig)',
    ]
  )
  def test_ranged_updated_discovery_unsolicited_passive_sub_oor(self):
    """Verify discovery with ranging operation with updated configuration.

    - Unsolicited Publish/Passive Subscribe
    - Publisher enables ranging
    - Subscriber:
      - Starts: Ranging enabled, min/max such that out of range (min=large,
                max=large+1)
      - Reconfigured to: different out-of-range setting

    Expect: no discovery after update
    """
    (p_dut, s_dut, p_disc_id, s_disc_id) = (
        self.run_discovery_prange_sminmax_outofrange(True)
    )
    self.run_discovery_update(
        p_dut,
        s_dut,
        p_disc_id,
        s_disc_id,
        p_config=None,  # no updates
        s_config=constants.SubscribeConfig(
            service_name=self.SERVICE_NAME,
            subscribe_type=constants.SubscribeType.PASSIVE,
            service_specific_info=self.getname().encode(),
            min_distance_mm=_LARGE_ENOUGH_DISTANCE_MM,
            max_distance_mm=_LARGE_ENOUGH_DISTANCE_MM+1,
        ),
        expect_discovery=False)
    p_dut.wifi_aware_snippet.wifiAwareCloseDiscoverSession(p_disc_id.callback_id)
    s_dut.wifi_aware_snippet.wifiAwareCloseDiscoverSession(s_disc_id.callback_id)

  @ApiTest(
    apis = [
        'android.net.wifi.aware.PublishConfig.Builder#setPublishType(PublishConfig.PUBLISH_TYPE_UNSOLICITED)',
        'android.net.wifi.aware.SubscribeConfig.Builder#setSubscribeType(SubscribeConfig.SUBSCRIBE_TYPE_PASSIVE)',
        'android.net.wifi.aware.PublishConfig.Builder#setRangingEnabled(boolean enable)',
        'android.net.wifi.aware.SubscribeConfig.Builder#setMinDistanceMm(int)',
        'android.net.wifi.aware.SubscribeConfig.Builder#setMaxDistanceMm(int)',
        'android.net.wifi.aware.PublishDiscoverySession#updatePublish(android.net.wifi.aware.PublishConfig publishConfig)',
    ]
  )
  def test_ranged_updated_discovery_unsolicited_passive_pub_same(self):
    """Verify discovery with ranging operation with updated configuration.

    - Unsolicited Publish/Passive Subscribe
    - Publisher enables ranging
    - Subscriber: Ranging enabled, min/max such that out of range (min=large,
                  max=large+1)
    - Reconfigured to: Publisher with same settings (ranging enabled)

    Expect: no discovery after update
    """
    (p_dut, s_dut, p_disc_id, s_disc_id) = (
        self.run_discovery_prange_sminmax_outofrange(True)
    )
    self.run_discovery_update(
        p_dut,
        s_dut,
        p_disc_id,
        s_disc_id,
        p_config=constants.PublishConfig(
            service_name=self.SERVICE_NAME,
            publish_type=constants.PublishType.UNSOLICITED,
            service_specific_info=self.getname().encode(),
            ranging_enabled=True,
        ),
        s_config=None,  # no updates
        expect_discovery=False)
    p_dut.wifi_aware_snippet.wifiAwareCloseDiscoverSession(p_disc_id.callback_id)
    s_dut.wifi_aware_snippet.wifiAwareCloseDiscoverSession(s_disc_id.callback_id)

  @ApiTest(
    apis = [
        'android.net.wifi.aware.PublishConfig.Builder#setPublishType(PublishConfig.PUBLISH_TYPE_UNSOLICITED)',
        'android.net.wifi.aware.SubscribeConfig.Builder#setSubscribeType(SubscribeConfig.SUBSCRIBE_TYPE_PASSIVE)',
        'android.net.wifi.aware.PublishConfig.Builder#setRangingEnabled(boolean enable)',
        'android.net.wifi.aware.SubscribeConfig.Builder#setMinDistanceMm(int)',
        'android.net.wifi.aware.SubscribeConfig.Builder#setMaxDistanceMm(int)',
        'android.net.wifi.aware.SubscribeDiscoverySession#updateSubscribe(android.net.wifi.aware.SubscribeConfig subscribeConfig)',
    ]
  )
  def test_ranged_updated_discovery_unsolicited_passive_multi_step(self):
    """Verify discovery with ranging operation with updated configuration.

    - Unsolicited Publish/Passive Subscribe
    - Publisher enables ranging
    - Subscriber: Ranging enabled, min/max such that out of range (min=large,
                  max=large+1)
      - Expect: no discovery
    - Reconfigured to: Ranging enabled, min/max such that in-range (min=0)
      - Expect: discovery with ranging
    - Reconfigured to: Ranging enabled, min/max such that out-of-range
                       (min=large)
      - Expect: no discovery
    - Reconfigured to: Ranging disabled
      - Expect: discovery without ranging
    """
    (p_dut, s_dut, p_disc_id, s_disc_id) = (
        self.run_discovery_prange_sminmax_outofrange(True)
    )
    self.run_discovery_update(
        p_dut,
        s_dut,
        p_disc_id,
        s_disc_id,
        p_config=None,  # no updates
        s_config=constants.SubscribeConfig(
            service_name=self.SERVICE_NAME,
            subscribe_type=constants.SubscribeType.PASSIVE,
            service_specific_info=self.getname().encode(),
            min_distance_mm=0,
            max_distance_mm=None,
        ),
        expect_discovery=True,
        expect_range=True)
    self.run_discovery_update(
        p_dut,
        s_dut,
        p_disc_id,
        s_disc_id,
        p_config=None,  # no updates
        s_config=constants.SubscribeConfig(
            service_name=self.SERVICE_NAME,
            subscribe_type=constants.SubscribeType.PASSIVE,
            service_specific_info=self.getname().encode(),
            min_distance_mm=_LARGE_ENOUGH_DISTANCE_MM,
            max_distance_mm=None),
        expect_discovery=False)

    self.run_discovery_update(
        p_dut,
        s_dut,
        p_disc_id,
        s_disc_id,
        p_config=None,  # no updates
        s_config=constants.SubscribeConfig(
            service_name=self.SERVICE_NAME,
            subscribe_type=constants.SubscribeType.PASSIVE,
            service_specific_info=self.getname().encode(),
        ),
        expect_discovery=True,
        expect_range=False)
    p_dut.wifi_aware_snippet.wifiAwareCloseDiscoverSession(p_disc_id.callback_id)
    s_dut.wifi_aware_snippet.wifiAwareCloseDiscoverSession(s_disc_id.callback_id)

  @ApiTest(
    apis = [
        'android.net.wifi.aware.PublishConfig.Builder#setPublishType(PublishConfig.PUBLISH_TYPE_SOLICITED)',
        'android.net.wifi.aware.SubscribeConfig.Builder#setSubscribeType(SubscribeConfig.SUBSCRIBE_TYPE_ACTIVE)',
        'android.net.wifi.aware.PublishConfig.Builder#setRangingEnabled(boolean enable)',
        'android.net.wifi.aware.SubscribeConfig.Builder#setMinDistanceMm(int)',
        'android.net.wifi.aware.SubscribeConfig.Builder#setMaxDistanceMm(int)',
        'android.net.wifi.aware.DiscoverySessionCallback#onServiceDiscovered(android.net.wifi.aware.ServiceDiscoveryInfo info)',
        'android.net.wifi.aware.DiscoverySessionCallback#onServiceDiscoveredWithinRange(android.net.wifi.aware.PeerHandle peerHandle, byte[] serviceSpecificInfo, List<byte[]> matchFilter, int distanceMm)',
        'android.net.wifi.aware.SubscribeDiscoverySession#updateSubscribe(android.net.wifi.aware.SubscribeConfig subscribeConfig)',
    ]
  )
  def test_ranged_updated_discovery_solicited_active_oor_to_ir(self):
    """Verify discovery with ranging operation with updated configuration.

    - Solicited Publish/Active Subscribe
    - Publisher enables ranging
    - Subscriber:
      - Starts: Ranging enabled, min/max such that out of range (min=large,
                max=large+1)
      - Reconfigured to: Ranging enabled, min/max such that in range (min=0,
                        max=large)

    Expect: discovery + ranging after update
    """
    (p_dut, s_dut, p_disc_id, s_disc_id) = (
        self.run_discovery_prange_sminmax_outofrange(False)
    )
    self.run_discovery_update(
        p_dut,
        s_dut,
        p_disc_id,
        s_disc_id,
        p_config=None,  # no updates
        s_config=constants.SubscribeConfig(
            service_name=self.SERVICE_NAME,
            subscribe_type=constants.SubscribeType.ACTIVE,
            service_specific_info=self.getname().encode(),
            min_distance_mm=0,
            max_distance_mm=_LARGE_ENOUGH_DISTANCE_MM,
        ),
        expect_discovery=True,
        expect_range=True)
    p_dut.wifi_aware_snippet.wifiAwareCloseDiscoverSession(p_disc_id.callback_id)
    s_dut.wifi_aware_snippet.wifiAwareCloseDiscoverSession(s_disc_id.callback_id)

  @ApiTest(
    apis = [
        'android.net.wifi.aware.PublishConfig.Builder#setPublishType(PublishConfig.PUBLISH_TYPE_SOLICITED)',
        'android.net.wifi.aware.SubscribeConfig.Builder#setSubscribeType(SubscribeConfig.SUBSCRIBE_TYPE_ACTIVE)',
        'android.net.wifi.aware.PublishConfig.Builder#setRangingEnabled(boolean enable)',
        'android.net.wifi.aware.SubscribeConfig.Builder#setMinDistanceMm(int)',
        'android.net.wifi.aware.SubscribeConfig.Builder#setMaxDistanceMm(int)',
        'android.net.wifi.aware.DiscoverySessionCallback#onServiceDiscovered(android.net.wifi.aware.ServiceDiscoveryInfo info)',
        'android.net.wifi.aware.PublishDiscoverySession#updatePublish(android.net.wifi.aware.PublishConfig publishConfig)',
    ]
  )
  def test_ranged_updated_discovery_solicited_active_pub_unrange(self):
    """Verify discovery with ranging operation with updated configuration.

    - Solicited Publish/Active Subscribe
    - Publisher enables ranging
    - Subscriber: Ranging enabled, min/max such that out of range (min=large,
                  max=large+1)
    - Reconfigured to: Publisher disables ranging

    Expect: discovery w/o ranging after update
    """
    (p_dut, s_dut, p_disc_id, s_disc_id) = (
        self.run_discovery_prange_sminmax_outofrange(False)
    )
    self.run_discovery_update(
        p_dut,
        s_dut,
        p_disc_id,
        s_disc_id,
        p_config=constants.PublishConfig(
            service_name=self.SERVICE_NAME,
            publish_type=constants.PublishType.SOLICITED,
            service_specific_info=self.getname().encode(),
        ),
        s_config=None,  # no updates
        expect_discovery=True,
        expect_range=False)
    p_dut.wifi_aware_snippet.wifiAwareCloseDiscoverSession(p_disc_id.callback_id)
    s_dut.wifi_aware_snippet.wifiAwareCloseDiscoverSession(s_disc_id.callback_id)

  @ApiTest(
    apis = [
        'android.net.wifi.aware.PublishConfig.Builder#setPublishType(PublishConfig.PUBLISH_TYPE_SOLICITED)',
        'android.net.wifi.aware.SubscribeConfig.Builder#setSubscribeType(SubscribeConfig.SUBSCRIBE_TYPE_ACTIVE)',
        'android.net.wifi.aware.PublishConfig.Builder#setRangingEnabled(boolean enable)',
        'android.net.wifi.aware.SubscribeConfig.Builder#setMinDistanceMm(int)',
        'android.net.wifi.aware.SubscribeConfig.Builder#setMaxDistanceMm(int)',
        'android.net.wifi.aware.DiscoverySessionCallback#onServiceDiscovered(android.net.wifi.aware.ServiceDiscoveryInfo info)',
        'android.net.wifi.aware.SubscribeDiscoverySession#updateSubscribe(android.net.wifi.aware.SubscribeConfig subscribeConfig)',
    ]
  )
  def test_ranged_updated_discovery_solicited_active_sub_unrange(self):
    """Verify discovery with ranging operation with updated configuration.

    - Solicited Publish/Active Subscribe
    - Publisher enables ranging
    - Subscriber:
      - Starts: Ranging enabled, min/max such that out of range (min=large,

    Expect: discovery w/o ranging after update
    """
    (p_dut, s_dut, p_disc_id, s_disc_id) = (
        self.run_discovery_prange_sminmax_outofrange(False)
    )
    self.run_discovery_update(
        p_dut,
        s_dut,
        p_disc_id,
        s_disc_id,
        p_config=None,  # no updates
        s_config=constants.SubscribeConfig(
            service_name=self.SERVICE_NAME,
            subscribe_type=constants.SubscribeType.ACTIVE,
            service_specific_info=self.getname().encode(),
        ),
        expect_discovery=True,
        expect_range=False)
    p_dut.wifi_aware_snippet.wifiAwareCloseDiscoverSession(p_disc_id.callback_id)
    s_dut.wifi_aware_snippet.wifiAwareCloseDiscoverSession(s_disc_id.callback_id)

  @ApiTest(
    apis = [
        'android.net.wifi.aware.PublishConfig.Builder#setPublishType(PublishConfig.PUBLISH_TYPE_SOLICITED)',
        'android.net.wifi.aware.SubscribeConfig.Builder#setSubscribeType(SubscribeConfig.SUBSCRIBE_TYPE_ACTIVE)',
        'android.net.wifi.aware.PublishConfig.Builder#setRangingEnabled(boolean enable)',
        'android.net.wifi.aware.SubscribeConfig.Builder#setMinDistanceMm(int)',
        'android.net.wifi.aware.SubscribeConfig.Builder#setMaxDistanceMm(int)',
        'android.net.wifi.aware.SubscribeDiscoverySession#updateSubscribe(android.net.wifi.aware.SubscribeConfig subscribeConfig)',
    ]
  )
  def test_ranged_updated_discovery_solicited_active_sub_oor(self):
    """Verify discovery with ranging operation with updated configuration.

    - Solicited Publish/Active Subscribe
    - Publisher enables ranging
    - Subscriber:
      - Starts: Ranging enabled, min/max such that out of range (min=large,
                max=large+1)
      - Reconfigured to: different out-of-range setting


    Expect: no discovery after update
    """
    (p_dut, s_dut, p_disc_id, s_disc_id) = (
        self.run_discovery_prange_sminmax_outofrange(False)
    )
    self.run_discovery_update(
        p_dut,
        s_dut,
        p_disc_id,
        s_disc_id,
        p_config=None,  # no updates
        s_config=constants.SubscribeConfig(
            service_name=self.SERVICE_NAME,
            subscribe_type=constants.SubscribeType.ACTIVE,
            service_specific_info=self.getname().encode(),
            min_distance_mm=_LARGE_ENOUGH_DISTANCE_MM,
            max_distance_mm=_LARGE_ENOUGH_DISTANCE_MM + 1,
        ),
        expect_discovery=False)
    p_dut.wifi_aware_snippet.wifiAwareCloseDiscoverSession(p_disc_id.callback_id)
    s_dut.wifi_aware_snippet.wifiAwareCloseDiscoverSession(s_disc_id.callback_id)

  @ApiTest(
    apis = [
        'android.net.wifi.aware.PublishConfig.Builder#setPublishType(PublishConfig.PUBLISH_TYPE_SOLICITED)',
        'android.net.wifi.aware.SubscribeConfig.Builder#setSubscribeType(SubscribeConfig.SUBSCRIBE_TYPE_ACTIVE)',
        'android.net.wifi.aware.PublishConfig.Builder#setRangingEnabled(boolean enable)',
        'android.net.wifi.aware.SubscribeConfig.Builder#setMinDistanceMm(int)',
        'android.net.wifi.aware.SubscribeConfig.Builder#setMaxDistanceMm(int)',
        'android.net.wifi.aware.DiscoverySessionCallback#onServiceDiscovered(android.net.wifi.aware.ServiceDiscoveryInfo info)',
        'android.net.wifi.aware.PublishDiscoverySession#updatePublish(android.net.wifi.aware.PublishConfig publishConfig)',
    ]
  )
  def test_ranged_updated_discovery_solicited_active_pub_same(self):
    """Verify discovery with ranging operation with updated configuration.

    - Solicited Publish/Active Subscribe
    - Publisher enables ranging
    - Subscriber: Ranging enabled, min/max such that out of range (min=large,
                  max=large+1)

    Expect: no discovery after update
    """
    (p_dut, s_dut, p_disc_id, s_disc_id) = (
        self.run_discovery_prange_sminmax_outofrange(False)
    )
    self.run_discovery_update(
        p_dut,
        s_dut,
        p_disc_id,
        s_disc_id,
        p_config=constants.PublishConfig(
            service_name=self.SERVICE_NAME,
            publish_type=constants.PublishType.SOLICITED,
            service_specific_info=self.getname().encode(),
            ranging_enabled=True,
        ),
        s_config=None,  # no updates
        expect_discovery=False)
    p_dut.wifi_aware_snippet.wifiAwareCloseDiscoverSession(p_disc_id.callback_id)
    s_dut.wifi_aware_snippet.wifiAwareCloseDiscoverSession(s_disc_id.callback_id)

  def _read_discovery_result(
      self,
      s_disc_id,
      expect_discovery: bool,
      expect_range: bool) -> callback_event.CallbackEvent:
    # Subscriber: wait or fail on service discovery.
    if expect_discovery:
        event_name = (
            constants.DiscoverySessionCallbackMethodType.SERVICE_DISCOVERED
        )
        if expect_range:
            event_name = (
                constants.DiscoverySessionCallbackMethodType
                .SERVICE_DISCOVERED_WITHIN_RANGE
            )
        discover_data = s_disc_id.waitAndGet(
            event_name=event_name, timeout=_DEFAULT_TIMEOUT
        )

        if expect_range:
            asserts.assert_true(
                constants.WifiAwareSnippetParams.DISTANCE_MM
                in discover_data.data,
                'Discovery with ranging expected!',
            )
            logging.info('distance=%s', discover_data.data[
                constants.WifiAwareSnippetParams.DISTANCE_MM])
        else:
            asserts.assert_false(
                constants.WifiAwareSnippetParams.DISTANCE_MM
                in discover_data.data,
                'Discovery with ranging NOT expected!',
            )
        return discover_data
    else:
        logging.info('onServiceDiscovered NOT expected! wait for timeout.')
        autils.callback_no_response(
            s_disc_id,
            constants.DiscoverySessionCallbackMethodType.SERVICE_DISCOVERED,
            timeout=_DEFAULT_TIMEOUT)
        return None

  @ApiTest(
    apis = [
        'android.net.wifi.aware.PublishConfig.Builder#setPublishType(PublishConfig.PUBLISH_TYPE_UNSOLICITED)',
        'android.net.wifi.aware.SubscribeConfig.Builder#setSubscribeType(SubscribeConfig.SUBSCRIBE_TYPE_PASSIVE)',
        'android.net.wifi.aware.PublishConfig.Builder#setRangingEnabled(boolean enable)',
        'android.net.wifi.aware.SubscribeConfig.Builder#setMinDistanceMm(int)',
        'android.net.wifi.aware.SubscribeConfig.Builder#setMaxDistanceMm(int)',
        'android.net.wifi.aware.DiscoverySessionCallback#onServiceDiscovered(android.net.wifi.aware.ServiceDiscoveryInfo info)',
        'android.net.wifi.aware.DiscoverySessionCallback#onServiceDiscoveredWithinRange(android.net.wifi.aware.PeerHandle peerHandle, byte[] serviceSpecificInfo, List<byte[]> matchFilter, int distanceMm)',
        'android.net.wifi.aware.SubscribeDiscoverySession#updateSubscribe(android.net.wifi.aware.SubscribeConfig subscribeConfig)',
    ]
  )
  def test_ranged_updated_discovery_solicited_active_multi_step(self):
    """Verify discovery with ranging operation with updated configuration.

    - Unsolicited Publish/Passive Subscribe
    - Publisher enables ranging
    - Subscriber: Ranging enabled, min/max such that out of range (min=large,
                  max=large+1)
      - Expect: no discovery
    - Reconfigured to: Ranging enabled, min/max such that in-range (min=0)
      - Expect: discovery with ranging
    - Reconfigured to: Ranging enabled, min/max such that out-of-range
                       (min=large)
    - Reconfigured to: Ranging disabled
      - Expect: discovery without ranging
    """
    (p_dut, s_dut, p_disc_id, s_disc_id) = (
        self.run_discovery_prange_sminmax_outofrange(True)
    )
    self.run_discovery_update(
        p_dut,
        s_dut,
        p_disc_id,
        s_disc_id,
        p_config=None,  # no updates
        s_config=constants.SubscribeConfig(
            service_name=self.SERVICE_NAME,
            subscribe_type=constants.SubscribeType.ACTIVE,
            service_specific_info=self.getname().encode(),
            min_distance_mm=0,
            max_distance_mm=None,
        ),
        expect_discovery=True,
        expect_range=True)
    self.run_discovery_update(
        p_dut,
        s_dut,
        p_disc_id,
        s_disc_id,
        p_config=None,  # no updates
        s_config=constants.SubscribeConfig(
            service_name=self.SERVICE_NAME,
            subscribe_type=constants.SubscribeType.ACTIVE,
            service_specific_info=self.getname().encode(),
            min_distance_mm=_LARGE_ENOUGH_DISTANCE_MM,
            max_distance_mm=None,
        ),
        expect_discovery=False)
    self.run_discovery_update(
        p_dut,
        s_dut,
        p_disc_id,
        s_disc_id,
        p_config=None,  # no updates
        s_config=constants.SubscribeConfig(
            service_name=self.SERVICE_NAME,
            subscribe_type=constants.SubscribeType.ACTIVE,
            service_specific_info=self.getname().encode(),
        ),
        expect_discovery=True,
        expect_range=False)
    p_dut.wifi_aware_snippet.wifiAwareCloseDiscoverSession(p_disc_id.callback_id)
    s_dut.wifi_aware_snippet.wifiAwareCloseDiscoverSession(s_disc_id.callback_id)

  @ApiTest(
    apis = [
        'android.net.wifi.aware.PublishConfig.Builder#setPublishType(PublishConfig.PUBLISH_TYPE_UNSOLICITED)',
        'android.net.wifi.aware.SubscribeConfig.Builder#setSubscribeType(SubscribeConfig.SUBSCRIBE_TYPE_PASSIVE)',
        'android.net.wifi.aware.PublishConfig.Builder#setRangingEnabled(boolean enable)',
        'android.net.wifi.aware.SubscribeConfig.Builder#setMinDistanceMm(int)',
        'android.net.wifi.aware.SubscribeConfig.Builder#setMaxDistanceMm(int)',
        'android.net.wifi.aware.DiscoverySessionCallback#onServiceDiscovered(android.net.wifi.aware.ServiceDiscoveryInfo info)',
        'android.net.wifi.aware.DiscoverySessionCallback#onServiceDiscoveredWithinRange(android.net.wifi.aware.PeerHandle peerHandle, byte[] serviceSpecificInfo, List<byte[]> matchFilter, int distanceMm)',
    ]
  )
  def test_ranged_discovery_multi_session(self):
    """Verify behavior with multiple concurrent discovery session with different configurations.

    Device A (Publisher):
      Publisher AA: ranging enabled
      Publisher BB: ranging enabled
      Publisher CC: ranging enabled
      Publisher DD: ranging disabled
    Device B (Subscriber):
      Subscriber AA: ranging out-of-range -> no match
      Subscriber BB: ranging in-range -> match w/range
      Subscriber CC: ranging disabled -> match w/o range
      Subscriber DD: ranging out-of-range -> match w/o range
    """
    p_dut = self.ads[0]
    p_dut.pretty_name = 'Publisher'
    s_dut = self.ads[1]
    s_dut.pretty_name = 'Subscriber'

    # Publisher+Subscriber: attach and wait for confirmation
    p_id = p_dut.wifi_aware_snippet.wifiAwareAttached(False)
    p_id.waitAndGet(constants.AttachCallBackMethodType.ATTACHED)
    time.sleep(self.device_startup_offset)
    s_id = s_dut.wifi_aware_snippet.wifiAwareAttached(False)
    s_id.waitAndGet(constants.AttachCallBackMethodType.ATTACHED)

    # Subscriber: start sessions
    aa_s_disc_id = s_dut.wifi_aware_snippet.wifiAwareSubscribe(
        s_id.callback_id,
        constants.SubscribeConfig(
            service_name='AA',
            subscribe_type=constants.SubscribeType.PASSIVE,
            service_specific_info=self.getname().encode(),
            min_distance_mm=_LARGE_ENOUGH_DISTANCE_MM,
            max_distance_mm=_LARGE_ENOUGH_DISTANCE_MM + 1,
        ).to_dict())

    bb_s_disc_id = s_dut.wifi_aware_snippet.wifiAwareSubscribe(
        s_id.callback_id,
        constants.SubscribeConfig(
            service_name='BB',
            subscribe_type=constants.SubscribeType.PASSIVE,
            service_specific_info=self.getname().encode(),
            min_distance_mm=0,
            max_distance_mm=_LARGE_ENOUGH_DISTANCE_MM,
        ).to_dict())

    cc_s_disc_id = s_dut.wifi_aware_snippet.wifiAwareSubscribe(
        s_id.callback_id,
        constants.SubscribeConfig(
            service_name='CC',
            subscribe_type=constants.SubscribeType.PASSIVE,
            service_specific_info=self.getname().encode()).to_dict())

    dd_s_disc_id = s_dut.wifi_aware_snippet.wifiAwareSubscribe(
        s_id.callback_id,
        constants.SubscribeConfig(
            service_name='DD',
            subscribe_type=constants.SubscribeType.PASSIVE,
            service_specific_info=self.getname().encode(),
            min_distance_mm=_LARGE_ENOUGH_DISTANCE_MM,
            max_distance_mm=_LARGE_ENOUGH_DISTANCE_MM + 1,
        ).to_dict())

    aa_discovery = aa_s_disc_id.waitAndGet(
        constants.DiscoverySessionCallbackMethodType.DISCOVER_RESULT,
        timeout=_DEFAULT_TIMEOUT)
    callback_name = aa_discovery.data[_CALLBACK_NAME]
    asserts.assert_equal(
        constants.DiscoverySessionCallbackMethodType.SUBSCRIBE_STARTED,
        callback_name,
        f'{s_dut} aa subscribe failed, got callback: {callback_name}.',
        )

    bb_discovery = bb_s_disc_id.waitAndGet(
        constants.DiscoverySessionCallbackMethodType.DISCOVER_RESULT,
        timeout=_DEFAULT_TIMEOUT)
    callback_name = bb_discovery.data[_CALLBACK_NAME]
    asserts.assert_equal(
        constants.DiscoverySessionCallbackMethodType.SUBSCRIBE_STARTED,
        callback_name,
        f'{s_dut} bb subscribe failed, got callback: {callback_name}.',
        )

    cc_discovery = cc_s_disc_id.waitAndGet(
        constants.DiscoverySessionCallbackMethodType.DISCOVER_RESULT,
        timeout=_DEFAULT_TIMEOUT)
    callback_name = cc_discovery.data[_CALLBACK_NAME]
    asserts.assert_equal(
        constants.DiscoverySessionCallbackMethodType.SUBSCRIBE_STARTED,
        callback_name,
        f'{s_dut} cc subscribe failed, got callback: {callback_name}.',
        )
    dd_discovery = dd_s_disc_id.waitAndGet(
        constants.DiscoverySessionCallbackMethodType.DISCOVER_RESULT,
        timeout=_DEFAULT_TIMEOUT)
    callback_name = dd_discovery.data[_CALLBACK_NAME]
    asserts.assert_equal(
        constants.DiscoverySessionCallbackMethodType.SUBSCRIBE_STARTED,
        callback_name,
        f'{s_dut} dd subscribe failed, got callback: {callback_name}.',
        )
    # Publisher: start sessions
    aa_p_disc_id = p_dut.wifi_aware_snippet.wifiAwarePublish(
        p_id.callback_id,
        constants.PublishConfig(
            service_name='AA',
            publish_type=constants.PublishType.UNSOLICITED,
            service_specific_info=self.getname().encode(),
            ranging_enabled=True,
        ).to_dict())
    bb_p_disc_id = p_dut.wifi_aware_snippet.wifiAwarePublish(
        p_id.callback_id,
        constants.PublishConfig(
            service_name='BB',
            publish_type=constants.PublishType.UNSOLICITED,
            service_specific_info=self.getname().encode(),
            ranging_enabled=True,
        ).to_dict())
    cc_p_disc_id = p_dut.wifi_aware_snippet.wifiAwarePublish(
        p_id.callback_id,
        constants.PublishConfig(
            service_name='CC',
            publish_type=constants.PublishType.UNSOLICITED,
            service_specific_info=self.getname().encode(),
            ranging_enabled=True,
        ).to_dict())
    dd_p_disc_id = p_dut.wifi_aware_snippet.wifiAwarePublish(
        p_id.callback_id,
        constants.PublishConfig(
            service_name='DD',
            publish_type=constants.PublishType.UNSOLICITED,
            service_specific_info=self.getname().encode(),
        ).to_dict())
    aa_p_discovery = aa_p_disc_id.waitAndGet(
        constants.DiscoverySessionCallbackMethodType.DISCOVER_RESULT,
        timeout=_DEFAULT_TIMEOUT)
    callback_name = aa_p_discovery.data[_CALLBACK_NAME]
    asserts.assert_equal(
        constants.DiscoverySessionCallbackMethodType.PUBLISH_STARTED,
        callback_name,
        f'{p_dut} aa publish failed, got callback: {callback_name}.',
        )

    bb_p_discovery = bb_p_disc_id.waitAndGet(
        constants.DiscoverySessionCallbackMethodType.DISCOVER_RESULT,
        timeout=_DEFAULT_TIMEOUT)
    callback_name = bb_p_discovery.data[_CALLBACK_NAME]
    asserts.assert_equal(
        constants.DiscoverySessionCallbackMethodType.PUBLISH_STARTED,
        callback_name,
        f'{p_dut} bb publish failed, got callback: {callback_name}.',
        )

    cc_p_discovery = cc_p_disc_id.waitAndGet(
        constants.DiscoverySessionCallbackMethodType.DISCOVER_RESULT,
        timeout=_DEFAULT_TIMEOUT)
    callback_name = cc_p_discovery.data[_CALLBACK_NAME]
    asserts.assert_equal(
        constants.DiscoverySessionCallbackMethodType.PUBLISH_STARTED,
        callback_name,
        f'{p_dut} cc publish failed, got callback: {callback_name}.',
        )

    dd_p_discovery = dd_p_disc_id.waitAndGet(
        constants.DiscoverySessionCallbackMethodType.DISCOVER_RESULT,
        timeout=_DEFAULT_TIMEOUT)
    callback_name = dd_p_discovery.data[_CALLBACK_NAME]
    asserts.assert_equal(
        constants.DiscoverySessionCallbackMethodType.PUBLISH_STARTED,
        callback_name,
        f'{p_dut} dd publish failed, got callback: {callback_name}.',
        )
    # Expected and unexpected service discovery
    utils.concurrent_exec(
        self._read_discovery_result,
        param_list=[(cc_s_disc_id, True, False),
                    (dd_s_disc_id, True, False),
                    (bb_s_disc_id, True, True),
                    (aa_s_disc_id, False, False)],
        max_workers=4,
    )
    time.sleep(autils._EVENT_TIMEOUT)

  def _extract_stats(
      self,
      results,
      range_reference_mm,
      range_margin_mm,
      min_rssi,
      reference_lci=None,
      reference_lcr=None,
      summary_only: bool = False,
  ):
    """Extract statistics from a list of RTT results.

     - num_results (success or fails)
     - num_success_results
     - num_no_results (e.g. timeout)
     - num_failures
     - num_range_out_of_margin (only for successes)
     - num_invalid_rssi (only for successes)
     - distances: extracted list of distances
     - distance_std_devs: extracted list of distance standard-deviations
     - rssis: extracted list of RSSI
     - distance_mean
     - distance_std_dev (based on distance - ignoring the individual std-devs)
     - rssi_mean
     - rssi_std_dev
     - status_codes
     - lcis: extracted list of all of the individual LCI
     - lcrs: extracted list of all of the individual LCR
     - any_lci_mismatch: True/False - checks if all LCI results are identical to
                         the reference LCI.
     - any_lcr_mismatch: True/False - checks if all LCR results are identical to
                         the reference LCR.
     - num_attempted_measurements: extracted list of all of the individual
                                   number of attempted measurements.
     - num_successful_measurements: extracted list of all of the individual
                                    number of successful measurements.
     - invalid_num_attempted: True/False - checks if number of attempted
                              measurements is non-zero for successful results.
     - invalid_num_successful: True/False - checks if number of successful
                               measurements is non-zero for successful results.

    Args:
      results: List of RTT results.
      range_reference_mm: Reference value for the distance (in mm)
      range_margin_mm: Acceptable absolute margin for distance (in mm)
      min_rssi: Acceptable minimum RSSI value.
      reference_lci: Reference values for LCI.
      reference_lcr: Reference value for the LCR.
      summary_only: Only include summary keys (reduce size).

    Returns:
      A dictionary of stats.
    """
    if reference_lci is None:
        reference_lci = []
    if reference_lcr is None:
        reference_lcr = []

    stats = {}
    stats['num_results'] = 0
    stats['num_success_results'] = 0
    stats['num_no_results'] = 0
    stats['num_failures'] = 0
    stats['num_range_out_of_margin'] = 0
    stats['num_invalid_rssi'] = 0
    stats['any_lci_mismatch'] = False
    stats['any_lcr_mismatch'] = False
    stats['invalid_num_attempted'] = False
    stats['invalid_num_successful'] = False

    range_max_mm = range_reference_mm + range_margin_mm
    range_min_mm = range_reference_mm - range_margin_mm

    distances = []
    distance_std_devs = []
    rssis = []
    num_attempted_measurements = []
    num_successful_measurements = []
    status_codes = []
    lcis = []
    lcrs = []

    for i in range(len(results)):
      result = results[i]

      if result is None:  # None -> timeout waiting for RTT result
        stats['num_no_results'] = stats['num_no_results'] + 1
        continue
      stats['num_results'] = stats['num_results'] + 1

      status_codes.append(
          result[constants.RangingResultCb.DATA_KEY_RESULT_STATUS]
      )
      if (
          status_codes[-1]
          != constants.RangingStatusCb.EVENT_CB_RANGING_STATUS_SUCCESS
      ):
        stats['num_failures'] = stats['num_failures'] + 1
        continue
      stats['num_success_results'] = stats['num_success_results'] + 1

      distance_mm = result[
          constants.RangingResultCb.DATA_KEY_RESULT_DISTANCE_MM
      ]
      distances.append(distance_mm)
      if not range_min_mm <= distance_mm <= range_max_mm:
        stats['num_range_out_of_margin'] = stats['num_range_out_of_margin'] + 1
      distance_std_devs.append(
          result[constants.RangingResultCb.DATA_KEY_DISTANCE_STD_DEV_MM]
      )

      rssi = result[constants.RangingResultCb.DATA_KEY_RESULT_RSSI]
      rssis.append(rssi)
      if not min_rssi <= rssi <= 0:
        stats['num_invalid_rssi'] = stats['num_invalid_rssi'] + 1

      num_attempted = result[
          constants.RangingResultCb.DATA_KEY_NUM_ATTEMPTED_MEASUREMENTS
      ]

      num_attempted_measurements.append(num_attempted)
      if num_attempted == 0:
        stats['invalid_num_attempted'] = True

      num_successful = result[
          constants.RangingResultCb.DATA_KEY_NUM_SUCCESSFUL_MEASUREMENTS
      ]
      num_successful_measurements.append(num_successful)
      if num_successful == 0:
        stats['invalid_num_successful'] = True

      lcis.append(result[constants.RangingResultCb.DATA_KEY_LCI])

      if result[constants.RangingResultCb.DATA_KEY_LCI] != reference_lci:
        stats['any_lci_mismatch'] = True
      lcrs.append(result[constants.RangingResultCb.DATA_KEY_LCR])
      if result[constants.RangingResultCb.DATA_KEY_LCR] != reference_lcr:
        stats['any_lcr_mismatch'] = True

    if distances:
      stats['distance_mean'] = statistics.mean(distances)
    if len(distances) > 1:
      stats['distance_std_dev'] = statistics.stdev(distances)
    if rssis:
      stats['rssi_mean'] = statistics.mean(rssis)
    if len(rssis) > 1:
      stats['rssi_std_dev'] = statistics.stdev(rssis)
    if not summary_only:
      stats['distances'] = distances
      stats['distance_std_devs'] = distance_std_devs
      stats['rssis'] = rssis
      stats['num_attempted_measurements'] = num_attempted_measurements
      stats['num_successful_measurements'] = num_successful_measurements
      stats['status_codes'] = status_codes
      stats['lcis'] = lcis
      stats['lcrs'] = lcrs

    return stats

  def _perform_ranging(
      self,
      ad: android_device.AndroidDevice,
      request: constants.RangingRequest,
  ):
    """Performs ranging and checks the ranging result."""
    ad.log.debug('Starting ranging with request: %s', request)
    ranging_cb_handler = ad.wifi_aware_snippet.wifiAwareStartRanging(
        request.to_dict()
    )
    event = ranging_cb_handler.waitAndGet(
        event_name=constants.RangingResultCb.EVENT_NAME_ON_RANGING_RESULT,
        timeout=_DEFAULT_TIMEOUT,
    )

    callback_name = event.data.get(
        constants.RangingResultCb.DATA_KEY_CALLBACK_NAME, None
    )
    asserts.assert_equal(
        callback_name,
        constants.RangingResultCb.CB_METHOD_ON_RANGING_RESULT,
        'Ranging failed: got unexpected callback.',
    )

    results = event.data.get(
        constants.RangingResultCb.DATA_KEY_RESULTS, None
    )
    return results

  @ApiTest(
    apis = [
        'android.net.wifi.aware.PublishConfig.Builder#setPublishType(PublishConfig.PUBLISH_TYPE_UNSOLICITED)',
        'android.net.wifi.aware.SubscribeConfig.Builder#setSubscribeType(SubscribeConfig.SUBSCRIBE_TYPE_PASSIVE)',
        'android.net.wifi.aware.PublishConfig.Builder#setRangingEnabled(boolean enable)',
        'android.net.wifi.aware.SubscribeConfig.Builder#setMinDistanceMm(int)',
        'android.net.wifi.aware.SubscribeConfig.Builder#setMaxDistanceMm(int)',
        'android.net.wifi.aware.DiscoverySessionCallback#onServiceDiscovered(android.net.wifi.aware.ServiceDiscoveryInfo info)',
        'android.net.wifi.aware.DiscoverySessionCallback#onServiceDiscoveredWithinRange(android.net.wifi.aware.PeerHandle peerHandle, byte[] serviceSpecificInfo, List<byte[]> matchFilter, int distanceMm)',
        'android.net.wifi.aware.SubscribeDiscoverySession#updateSubscribe(android.net.wifi.aware.SubscribeConfig subscribeConfig)',
    ]
  )
  def test_discovery_direct_concurrency(self):
    """Verify the behavior of Wi-Fi Aware Ranging used as part of discovery and as direct ranging to a peer device.

    Process:
    - Start YYY service with ranging in-range
    - Start XXX service with ranging out-of-range
    - Start performing direct Ranging
    - While above going on update XXX to be in-range
    - Keep performing direct Ranging in context of YYY
    - Stop direct Ranging and look for XXX to discover
    """
    dut1 = self.ads[0]
    dut1.pretty_name = 'DUT1'
    dut2 = self.ads[1]
    dut2.pretty_name = 'DUT2'

    # DUTs: attach and wait for confirmation
    dut1_id = dut1.wifi_aware_snippet.wifiAwareAttached(False)
    dut1_id.waitAndGet(constants.AttachCallBackMethodType.ATTACHED)
    time.sleep(self.device_startup_offset)
    dut2_id = dut2.wifi_aware_snippet.wifiAwareAttached(True)
    dut2_id.waitAndGet(constants.AttachCallBackMethodType.ATTACHED)
    identity_changed_event = dut2_id.waitAndGet(
        event_name=constants.AttachCallBackMethodType.ID_CHANGED,
        timeout=_DEFAULT_TIMEOUT,
    )
    mac_address = identity_changed_event.data.get('mac', None)

    # DUT1: publishers bring-up
    _ = self._start_publish(
        dut1,
        dut1_id.callback_id,
        pub_config=constants.PublishConfig(
            service_name='XXX',
            publish_type=constants.PublishType.UNSOLICITED,
            service_specific_info=self.getname().encode(),
            ranging_enabled=True,
        ),
    )

    _ = self._start_publish(
        dut1,
        dut1_id.callback_id,
        pub_config=constants.PublishConfig(
            service_name='YYY',
            publish_type=constants.PublishType.UNSOLICITED,
            service_specific_info=self.getname().encode(),
            ranging_enabled=True,
        ),
    )
    # DUT2: subscribers bring-up
    xxx_s_id = self._start_subscribe(
        dut2,
        dut2_id.callback_id,
        sub_config=constants.SubscribeConfig(
            service_name='XXX',
            subscribe_type=constants.SubscribeType.PASSIVE,
            service_specific_info=self.getname().encode(),
            min_distance_mm=_LARGE_ENOUGH_DISTANCE_MM,
            max_distance_mm=_LARGE_ENOUGH_DISTANCE_MM + 1,
        ),
    )
    yyy_s_id = self._start_subscribe(
        dut2,
        dut2_id.callback_id,
        sub_config=constants.SubscribeConfig(
            service_name='YYY',
            subscribe_type=constants.SubscribeType.PASSIVE,
            service_specific_info=self.getname().encode(),
            min_distance_mm=None,
            max_distance_mm=_LARGE_ENOUGH_DISTANCE_MM,
        ),
    )
    # Service discovery: YYY (with range info), but no XXX
    result_disc = self._read_discovery_result(yyy_s_id, True, True)
    yyy_peer_id_on_sub = result_disc.data[
        constants.WifiAwareSnippetParams.PEER_ID
    ]
    self._read_discovery_result(xxx_s_id, False, False)

    # Direct ranging
    results21 = []
    for _ in range(10):
      key_results = self._perform_ranging(
          dut2, constants.RangingRequest(peer_ids=[yyy_peer_id_on_sub])
      )
      dut2.log.info(type(key_results))
      dut2.log.info(key_results)
      results21.append(key_results[0])

    time.sleep(5)  # while switching roles
    results12 = []
    for _ in range(10):
      key_results = self._perform_ranging(
          dut1, constants.RangingRequest(peer_mac_addresses=[mac_address])
      )
      dut1.log.info(type(key_results))
      dut1.log.info(key_results[0])
      results12.append(key_results[0])

    stats = [
        self._extract_stats(results12, 0, 0, 0),
        self._extract_stats(results21, 0, 0, 0),
    ]
    # Update XXX to be within range
    dut2.wifi_aware_snippet.wifiAwareUpdateSubscribe(
        xxx_s_id.callback_id,
        constants.SubscribeConfig(
            service_name='XXX',
            subscribe_type=constants.SubscribeType.PASSIVE,
            service_specific_info=self.getname().encode(),
            min_distance_mm=None,
            max_distance_mm=_LARGE_ENOUGH_DISTANCE_MM,
        ).to_dict())
    xxx_s_id.waitAndGet(
        event_name=
        constants.DiscoverySessionCallbackMethodType.SESSION_CONFIG_UPDATED,
        timeout=_DEFAULT_TIMEOUT
    )
    # Expect discovery on XXX - wait until discovery with ranging:
    # - 0 or more: without ranging info (due to concurrency limitations)
    # - 1 or more: with ranging (once concurrency limitation relieved)
    num_events = 0
    while True:
        try:
            discover_data = xxx_s_id.waitAndGet(
                event_name=(
                    constants.DiscoverySessionCallbackMethodType
                    .SERVICE_DISCOVERED_WITHIN_RANGE
                ), timeout=3
            )
            if constants.WifiAwareSnippetParams.DISTANCE_MM in discover_data.data:
                break
        except callback_handler_v2.TimeoutError:
            logging.info('discovered with in_range timeout, skip it')

        num_events = num_events + 1
        asserts.assert_true(
            num_events < 10,  # arbitrary safety valve
            'Way too many discovery events without ranging!')
    logging.info('num_events : %s', num_events)
    asserts.explicit_pass(
        'Discovery/Direct RTT Concurrency Pass', extras={'data': stats})


if __name__ == '__main__':
  # Take test args
  if '--' in sys.argv:
    index = sys.argv.index('--')
    sys.argv = sys.argv[:1] + sys.argv[index + 1 :]

  test_runner.main()
