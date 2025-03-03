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
"""Wi-Fi Aware Matchfilter test reimplemented in Mobly."""
import base64
import enum
import logging
import random
import sys

from android.platform.test.annotations import ApiTest
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
snippets_to_load = [
    ('wifi_aware_snippet', PACKAGE_NAME),
    ('wifi', constants.WIFI_SNIPPET_PACKAGE_NAME),
]
_DEFAULT_TIMEOUT = constants.WAIT_WIFI_STATE_TIME_OUT.total_seconds()
_MSG_ID_SUB_TO_PUB = random.randint(1000, 5000)
_MSG_ID_PUB_TO_SUB = random.randint(5001, 9999)
_MSG_SUB_TO_PUB = "Let's talk [Random Identifier: %s]" % utils.rand_ascii_str(5)
_MSG_PUB_TO_SUB = 'Ready [Random Identifier: %s]' % utils.rand_ascii_str(5)
_CALLBACK_NAME = constants.DiscoverySessionCallbackParamsType.CALLBACK_NAME
_IS_SESSION_INIT = constants.DiscoverySessionCallbackParamsType.IS_SESSION_INIT

# Publish & Subscribe Config keys.
_PAYLOAD_SIZE_MIN = 0
_PAYLOAD_SIZE_TYPICAL = 1
_PAYLOAD_SIZE_MAX = 2
_PUBLISH_TYPE_UNSOLICITED = 0
_PUBLISH_TYPE_SOLICITED = 1
_SUBSCRIBE_TYPE_PASSIVE = 0
_SUBSCRIBE_TYPE_ACTIVE = 1


@enum.unique
class AttachCallBackMethodType(enum.StrEnum):
    """Represents Attach Callback Method Type in Wi-Fi Aware.

    https://developer.android.com/reference/android/net/wifi/aware/AttachCallback
    """
    ATTACHED = 'onAttached'
    ATTACH_FAILED = 'onAttachFailed'
    AWARE_SESSION_TERMINATED = 'onAwareSessionTerminated'


class WifiAwareMatchFilterTest(base_test.BaseTestClass):
    """Set of tests for Wi-Fi Aware Match Filter behavior. These all
  use examples from Appendix H of the Wi-Fi Aware standard."""

    ads: list[android_device.AndroidDevice]
    publisher: android_device.AndroidDevice
    subscriber: android_device.AndroidDevice

    SERVICE_NAME = "GoogleTestServiceMFMFMF"

    MF_NNNNN = bytes([0x0, 0x0, 0x0, 0x0, 0x0])
    MF_12345 = bytes([0x1, 0x1, 0x1, 0x2, 0x1, 0x3, 0x1, 0x4, 0x1, 0x5])
    MF_12145 = bytes([0x1, 0x1, 0x1, 0x2, 0x1, 0x1, 0x1, 0x4, 0x1, 0x5])
    MF_1N3N5 = bytes([0x1, 0x1, 0x0, 0x1, 0x3, 0x0, 0x1, 0x5])
    MF_N23N5 = bytes([0x0, 0x1, 0x2, 0x1, 0x3, 0x0, 0x1, 0x5])
    MF_N2N4 = bytes([0x0, 0x1, 0x2, 0x0, 0x1, 0x4])
    MF_1N3N = bytes([0x1, 0x1, 0x0, 0x1, 0x3, 0x0])

    match_filters = [
                    [None, None, True, True],
                    [None, MF_NNNNN, True, True],
                    [MF_NNNNN, None, True, True],
                    [None, MF_12345, True, False],
                    [MF_12345, None, False, True],
                    [MF_NNNNN, MF_12345, True, True],
                    [MF_12345, MF_NNNNN, True, True],
                    [MF_12345, MF_12345, True, True],
                    [MF_12345, MF_12145, False, False],
                    [MF_1N3N5, MF_12345, True, True],
                    [MF_12345, MF_N23N5, True, True],
                    [MF_N2N4, MF_12345, True, False],
                    [MF_12345, MF_1N3N, False, True]
                    ]

    def setup_class(self):
        # Register two Android devices.
        self.ads = self.register_controller(android_device, min_number=2)
        self.publisher = self.ads[0]
        self.subscriber = self.ads[1]

        def setup_device(device: android_device.AndroidDevice):
            for snippet_name, package_name in snippets_to_load:
                device.load_snippet(snippet_name, package_name)
            for permission in RUNTIME_PERMISSIONS:
                device.adb.shell(['pm', 'grant', package_name, permission])
            asserts.abort_all_if(
                not device.wifi_aware_snippet.wifiAwareIsAvailable(),
                f'{device} Wi-Fi Aware is not available.',
            )

        # Set up devices in parallel.
        utils.concurrent_exec(
            setup_device,
            ((self.publisher,), (self.subscriber,)),
            max_workers=2,
            raise_on_exception=True,
        )

    def setup_test(self):
        for ad in self.ads:
            ad.wifi.wifiEnable()
            aware_avail = ad.wifi_aware_snippet.wifiAwareIsAvailable()
            if not aware_avail:
                ad.log.info('Aware not available. Waiting ...')
                state_handler = (
                    ad.wifi_aware_snippet.wifiAwareMonitorStateChange())
                state_handler.waitAndGet(
                    constants.WifiAwareBroadcast.WIFI_AWARE_AVAILABLE)

    def teardown_test(self):
        utils.concurrent_exec(
            self._teardown_test_on_device,
            ((self.publisher,), (self.subscriber,)),
            max_workers=2,
            raise_on_exception=True,
        )
        utils.concurrent_exec(
            lambda d: d.services.create_output_excerpts_all(
                self.current_test_info),
            param_list=[[ad] for ad in self.ads],
            raise_on_exception=True,
        )

    def _teardown_test_on_device(self,
                                 ad: android_device.AndroidDevice) -> None:
        ad.wifi_aware_snippet.wifiAwareCloseAllWifiAwareSession()
        ad.wifi.wifiClearConfiguredNetworks()
        ad.wifi.wifiEnable()
        if ad.is_adb_root:
          autils.reset_device_parameters(ad)
          autils.reset_device_statistics(ad)
          autils.validate_forbidden_callbacks(ad)

    def on_fail(self, record: records.TestResult) -> None:
        android_device.take_bug_reports(self.ads,
                                        destination =
                                        self.current_test_info.output_path)

    def _start_attach(self, ad: android_device.AndroidDevice) -> str:
        """Starts the attach process on the provided device."""
        handler = ad.wifi_aware_snippet.wifiAwareAttach()
        attach_event = handler.waitAndGet(
            event_name=AttachCallBackMethodType.ATTACHED,
            timeout=_DEFAULT_TIMEOUT,
        )
        asserts.assert_true(
            ad.wifi_aware_snippet.wifiAwareIsSessionAttached(
                handler.callback_id),
            f'{ad} attach succeeded, but Wi-Fi Aware session is still null.'
        )
        ad.log.info('Attach Wi-Fi Aware session succeeded.')
        return attach_event.callback_id

    def run_discovery(self, p_dut, s_dut,
                      p_mf,
                      s_mf,
                      do_unsolicited_passive,
                      expect_discovery):
        """Creates a discovery session (publish and subscribe) with.
        the specified configuration.

        Args:
            p_dut: Device to use as publisher.
            s_dut: Device to use as subscriber.
            p_mf: Publish's match filter.
            s_mf: Subscriber's match filter.
            do_unsolicited_passive: True to use an Unsolicited/
                                    Passive discovery,
                                    False for a Solicited/
                                    Active discovery session.
            expect_discovery: True if service should be discovered,
                              False otherwise.
        Returns: True on success, False on failure (based on expect_discovery
                arg)
        """

        # Encode the match filter
        p_mf = base64.b64encode(
            p_mf).decode("utf-8") if p_mf is not None else None
        s_mf = base64.b64encode(
            s_mf).decode("utf-8") if s_mf is not None else None

        # Publisher+Subscriber: attach and wait for confirmation
        p_id = self._start_attach(p_dut)
        s_id = self._start_attach(s_dut)

        # Publisher: start publish and wait for confirmation
        p_config = autils.create_discovery_config(self.SERVICE_NAME,
                                                  p_type =
                                                  _PUBLISH_TYPE_UNSOLICITED
                                                  if do_unsolicited_passive
                                                  else  _PUBLISH_TYPE_SOLICITED,
                                                  s_type = None,
                                                  match_filter_list = p_mf)
        dut_p_mf = p_dut.wifi_aware_snippet.wifiAwarePublish(
            p_id, p_config
                )
        p_dut.log.info('Created the DUT publish session %s', dut_p_mf)
        p_discovery = dut_p_mf.waitAndGet(
            constants.DiscoverySessionCallbackMethodType.DISCOVER_RESULT)
        callback_name = p_discovery.data[_CALLBACK_NAME]
        asserts.assert_equal(
            constants.DiscoverySessionCallbackMethodType.PUBLISH_STARTED,
            callback_name,
            f'{p_dut} DUT publish failed, got callback: {callback_name}.',
            )

        # Subscriber: start subscribe and wait for confirmation
        s_config = autils.create_discovery_config(self.SERVICE_NAME,
                                                  p_type = None,
                                                  s_type =
                                                  _SUBSCRIBE_TYPE_PASSIVE
                                                  if do_unsolicited_passive
                                                  else  _SUBSCRIBE_TYPE_ACTIVE,
                                                  match_filter_list=s_mf)
        dut_s_mf = s_dut.wifi_aware_snippet.wifiAwareSubscribe(
            s_id, s_config
                )
        s_dut.log.info('Created the DUT subscribe session.: %s', dut_s_mf)
        s_discovery = dut_s_mf.waitAndGet(
                constants.DiscoverySessionCallbackMethodType.DISCOVER_RESULT,
                timeout=_DEFAULT_TIMEOUT)
        callback_name = s_discovery.data[_CALLBACK_NAME]
        asserts.assert_equal(
            constants.DiscoverySessionCallbackMethodType.SUBSCRIBE_STARTED,
            callback_name,
            f'{s_dut} DUT subscribe failed, got callback: {callback_name}.',
            )
        event = None
        try:
            event = dut_s_mf.waitAndGet(
                constants.DiscoverySessionCallbackMethodType.SERVICE_DISCOVERED,
                timeout=_DEFAULT_TIMEOUT)
            s_dut.log.info(
                "[Subscriber] SESSION_CB_ON_SERVICE_DISCOVERED: %s",event)
        except errors.CallbackHandlerTimeoutError:
            s_dut.log.info(
                "[Subscriber] No SESSION_CB_ON_SERVICE_DISCOVERED: %s",event)
            pass
        p_dut.wifi_aware_snippet.wifiAwareCloseDiscoverSession(
            dut_p_mf.callback_id)
        s_dut.wifi_aware_snippet.wifiAwareCloseDiscoverSession(
            dut_s_mf.callback_id)

        p_dut.wifi_aware_snippet.wifiAwareCloseAllWifiAwareSession()

        s_dut.wifi_aware_snippet.wifiAwareCloseAllWifiAwareSession()

        if expect_discovery:
            return event is not None
        else:
            return event is None

    def run_match_filters_per_spec(self, do_unsolicited_passive):
        """Validate all the match filter combinations in the Wi-Fi Aware spec,
        Appendix H.

        Args:
            do_unsolicited_passive: True to run the Unsolicited/Passive tests,
                                    False to run the Solicited/Active tests.
        """
        p_dut = self.ads[0]
        s_dut = self.ads[1]
        p_dut.pretty_name = "Publisher"
        s_dut.pretty_name = "Subscriber"
        fails = []
        for i in range(len(self.match_filters)):
            test_info = self.match_filters[i]
            if do_unsolicited_passive:
                pub_type = "Unsolicited"
                sub_type = "Passive"
                pub_mf = test_info[0]
                sub_mf = test_info[1]
                expect_discovery = test_info[3]
            else:
                pub_type = "Solicited"
                sub_type = "Active"
                pub_mf = test_info[1]
                sub_mf = test_info[0]
                expect_discovery = test_info[2]

            logging.info("Test #%d: %s Pub MF=%s, %s Sub MF=%s: Discovery %s",
                        i, pub_type, pub_mf, sub_type, sub_mf, "EXPECTED"
                        if test_info[2] else "UNEXPECTED")
            result = self.run_discovery(
                p_dut,
                s_dut,
                p_mf=pub_mf,
                s_mf=sub_mf,
                do_unsolicited_passive = do_unsolicited_passive,
                expect_discovery = expect_discovery)
            logging.info("Test #%d %s Pub/%s Sub %s", i, pub_type, sub_type,
                      "PASS" if result else "FAIL")
            if not result:
                fails.append(i)
            logging.info("fails: %s", fails)

        asserts.assert_true(
            len(fails) == 0,
            "Some match filter tests are failing",
            extras={"data": fails})

    @ApiTest(
        apis=[
            'android.net.wifi.aware.WifiAwareManager#attach(android.net.wifi.aware.AttachCallback, android.net.wifi.aware.IdentityChangedListener, android.os.Handler)',
            'android.net.wifi.aware.WifiAwareSession#publish(android.net.wifi.aware.PublishConfig, android.net.wifi.aware.DiscoverySessionCallback, android.os.Handler)',
            'android.net.wifi.aware.WifiAwareSession#subscrible(android.net.wifi.aware.SubscribeConfig, android.net.wifi.aware.DiscoverySessionCallback, android.os.Handler)',
            'android.net.wifi.aware.PublishConfig.Builder#setPublishType(PublishConfig.PUBLISH_TYPE_UNSOLICITED)',
            'android.net.wifi.aware.SubscribeConfig.Builder#setSubscribeType(SubscribeConfig.SUBSCRIBE_TYPE_PASSIVE)',
            'android.net.wifi.aware.DiscoverySession#sendMessage(int, byte[])',
        ]
    )

    def test_match_filters_per_spec_unsolicited_passive(self):
        """Validate all the match filter combinations in the Wi-Fi Aware spec,
        Appendix H for Unsolicited Publish (tx filter) Passive Subscribe (rx
        filter)"""
        self.run_match_filters_per_spec(do_unsolicited_passive=True)

    @ApiTest(
        apis=[
            'android.net.wifi.aware.WifiAwareManager#attach(android.net.wifi.aware.AttachCallback, android.net.wifi.aware.IdentityChangedListener, android.os.Handler)',
            'android.net.wifi.aware.WifiAwareSession#publish(android.net.wifi.aware.PublishConfig, android.net.wifi.aware.DiscoverySessionCallback, android.os.Handler)',
            'android.net.wifi.aware.WifiAwareSession#subscrible(android.net.wifi.aware.SubscribeConfig, android.net.wifi.aware.DiscoverySessionCallback, android.os.Handler)',
            'android.net.wifi.aware.PublishConfig.Builder#setPublishType(PublishConfig.PUBLISH_TYPE_SOLICITED)',
            'android.net.wifi.aware.SubscribeConfig.Builder#setSubscribeType(SubscribeConfig.SUBSCRIBE_TYPE_ACTIVE)',
            'android.net.wifi.aware.DiscoverySession#sendMessage(int, byte[])',
        ]
    )

    def test_match_filters_per_spec_solicited_active(self):
        """Validate all the match filter combinations in the Wi-Fi Aware spec,
        Appendix H for Solicited Publish (rx filter) Active Subscribe (tx
        filter)"""
        self.run_match_filters_per_spec(do_unsolicited_passive=False)


if __name__ == '__main__':
    # Take test args
    if '--' in sys.argv:
        index = sys.argv.index('--')
        sys.argv = sys.argv[:1] + sys.argv[index + 1:]

    test_runner.main()
