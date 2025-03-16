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
"""Wi-Fi Aware discovery ranging test module."""

import logging
import sys
import time

from android.platform.test.annotations import ApiTest
from mobly import asserts
from mobly import base_test
from mobly import records
from mobly import test_runner
from mobly import utils
from mobly.controllers import android_device
import wifi_test_utils

from aware import aware_snippet_utils
from aware import constants


_SNIPPET_PACKAGE_NAME = constants.WIFI_SNIPPET_PACKAGE_NAME
_DEFAULT_TIMEOUT = constants.WAIT_WIFI_STATE_TIME_OUT.total_seconds()
_MSG_SUB_TO_PUB = "Let's talk [Random Identifier: {random_id}]"
_MSG_PUB_TO_SUB = 'Ready [Random Identifier: %s]'
_LARGE_ENOUGH_DISTANCE_MM = 100000  # 100 meters
_MIN_RSSI = -100
_WAIT_SEC_FOR_RTT_INITIATOR_RESPONDER_SWITCH = 5


@ApiTest(
    apis=[
        'android.net.wifi.rtt.RangingRequest.Builder#addWifiAwarePeer(android.net.wifi.aware.PeerHandle)',
        'android.net.wifi.aware.PublishConfig.Builder#setRangingEnabled(boolean)',
        'android.net.wifi.aware.SubscribeConfig.Builder#setMaxDistanceMm(int)',
        'android.net.wifi.rtt.WifiRttManager#startRanging(android.net.wifi.rtt.RangingRequest, java.util.concurrent.Executor, android.net.wifi.rtt.RangingResultCallback)',
        'android.net.wifi.aware.WifiAwareManager#attach(android.net.wifi.aware.AttachCallback, android.net.wifi.aware.IdentityChangedListener, android.os.Handler)',
        'android.net.wifi.aware.WifiAwareSession#publish(android.net.wifi.aware.PublishConfig, android.net.wifi.aware.DiscoverySessionCallback, android.os.Handler)',
        'android.net.wifi.aware.WifiAwareSession#subscrible(android.net.wifi.aware.SubscribeConfig, android.net.wifi.aware.DiscoverySessionCallback, android.os.Handler)',
    ]
)
class WifiAwareDiscoveryRangingTest(base_test.BaseTestClass):
    """Wi-Fi Aware discovery ranging test class.

    All tests in this class share the same test steps and expected results.
    The difference is that different tests perform ranging with different
    configurations.

    Test Preconditions:
        * Two Android devices that support Wi-Fi Aware and Wi-Fi RTT.
        * The devices should be placed ~20cm apart.

    Test Steps:
        1. Attach a Wi-Fi Aware session on each device.
        2. Publisher publishes an Wi-Fi Aware service, subscriber subscribes
           to it. Wait for service discovery.
        3. Send messages through discovery session's API.
        4. Perform ranging on one device to another device through Wi-Fi Aware.
           Perform this step on the publisher and subscriber, respectively.

    Expected Results:
        Discovery ranging succeeds. In ranging result, the device that
        performs ranging discovers the expected peer device, with a valid
        distance and RSSI value.
    """

    ads: list[android_device.AndroidDevice]
    publisher: android_device.AndroidDevice
    subscriber: android_device.AndroidDevice

    # Wi-Fi Aware attach session ID
    pub_attach_session: str | None = None
    sub_attach_session: str | None = None
    # Wi-Fi Aware discovery session ID
    pub_session: str | None = None
    sub_session: str | None = None

    def setup_class(self):
        # Register and set up Android devices in parallel.
        self.ads = self.register_controller(android_device, min_number=2)
        self.publisher = self.ads[0]
        self.subscriber = self.ads[1]

        # Device setup
        utils.concurrent_exec(
            self._setup_device,
            ((self.publisher,), (self.subscriber,)),
            max_workers=2,
            raise_on_exception=True,
        )

        # Device capability check
        for device in [self.publisher, self.subscriber]:
            asserts.abort_class_if(
                not device.wifi.wifiAwareIsSupported(),
                f'{device} does not support Wi-Fi Aware.',
            )
            asserts.abort_class_if(
                not device.wifi.wifiAwareIsAvailable(),
                f'Wi-Fi Aware is not available on {device}.',
            )
            asserts.abort_class_if(
                not device.wifi.wifiAwareIsRttSupported(),
                f'{device} does not support Wi-Fi RTT.',
            )

    def _setup_device(self, device: android_device.AndroidDevice):
        device.load_snippet('wifi', _SNIPPET_PACKAGE_NAME)
        device.wifi.wifiEnable()
        wifi_test_utils.set_screen_on_and_unlock(device)
        wifi_test_utils.enable_wifi_verbose_logging(device)

    def test_discovery_ranging_to_peer_handle(self) -> None:
        """Test ranging to a Wi-Fi Aware peer handle.

        See class docstring for the test steps and expected results.
        """
        pub_config = constants.PublishConfig(
            publish_type=constants.PublishType.UNSOLICITED,
            ranging_enabled=True,
        )
        sub_config = constants.SubscribeConfig(
            subscribe_type=constants.SubscribeType.PASSIVE,
            max_distance_mm=_LARGE_ENOUGH_DISTANCE_MM,
        )

        # Step 1 - 3: Set up Wi-Fi Aware discovery sessions, so it is ready
        # for ranging.
        _, _, pub_peer, sub_peer = self._setup_discovery_sessions(
            pub_config=pub_config,
            sub_config=sub_config,
        )

        # Step 4: Perform ranging on the publisher and subscriber, respectively.
        self.publisher.log.info('Performing ranging to peer ID %d.', pub_peer)
        self._perform_ranging(
            self.publisher,
            constants.RangingRequest(peer_ids=[pub_peer]),
        )

        # RTT initiator/responder role switch takes time. We don't have an
        # API to enforce it. So wait a few seconds for a semi-arbitrary
        # teardown.
        time.sleep(_WAIT_SEC_FOR_RTT_INITIATOR_RESPONDER_SWITCH)
        self.subscriber.log.info('Performing ranging to peer ID %d.', sub_peer)
        self._perform_ranging(
            self.subscriber,
            constants.RangingRequest(peer_ids=[sub_peer]),
        )

        # Test finished, clean up.
        self.publisher.wifi.wifiAwareCloseDiscoverSession(self.pub_session)
        self.subscriber.wifi.wifiAwareCloseDiscoverSession(self.sub_session)
        self.publisher.wifi.wifiAwareDetach(self.pub_attach_session)
        self.subscriber.wifi.wifiAwareDetach(self.sub_attach_session)

    def test_discovery_ranging_to_peer_mac_address(self) -> None:
        """Test ranging to a Wi-Fi Aware peer MAC address.

        See class docstring for the test steps and expected results.
        """
        pub_config = constants.PublishConfig(
            publish_type=constants.PublishType.UNSOLICITED,
            ranging_enabled=True,
        )
        sub_config = constants.SubscribeConfig(
            subscribe_type=constants.SubscribeType.PASSIVE,
            max_distance_mm=_LARGE_ENOUGH_DISTANCE_MM,
        )

        # Step 1 - 3: Set up Wi-Fi Aware discovery sessions, so it is ready
        # for ranging.
        pub_mac_address, sub_mac_address, _, _ = self._setup_discovery_sessions(
            pub_config=pub_config,
            sub_config=sub_config,
        )

        # Step 4: Perform ranging on the publisher and subscriber, respectively.
        self.publisher.log.info(
            'Performing ranging to peer MAC address %s.', sub_mac_address
        )
        self._perform_ranging(
            self.publisher,
            constants.RangingRequest(peer_mac_addresses=[sub_mac_address]),
        )

        # RTT initiator/responder role switch takes time. We don't have an
        # API to enforce it. So wait a few seconds for a semi-arbitrary
        # teardown.
        time.sleep(_WAIT_SEC_FOR_RTT_INITIATOR_RESPONDER_SWITCH)
        self.subscriber.log.info(
            'Performing ranging to peer MAC address %s.', pub_mac_address
        )
        self._perform_ranging(
            self.subscriber,
            constants.RangingRequest(peer_mac_addresses=[pub_mac_address]),
        )

        # Test finished, clean up.
        self.publisher.wifi.wifiAwareCloseDiscoverSession(self.pub_session)
        self.subscriber.wifi.wifiAwareCloseDiscoverSession(self.sub_session)
        self.publisher.wifi.wifiAwareDetach(self.pub_attach_session)
        self.subscriber.wifi.wifiAwareDetach(self.sub_attach_session)

    def _setup_discovery_sessions(
        self,
        pub_config: constants.PublishConfig,
        sub_config: constants.SubscribeConfig,
    ) -> tuple[str, str, int, int]:
        """Sets up Wi-Fi Aware discovery sessions.

        Args:
            pub_config: The publish configuration.
            sub_config: The subscribe configuration.

        Returns:
            A tuple of (publisher MAC address, subscriber MAC address,
            publisher peer ID, subscriber peer ID).
        """
        # Step 1: Attach Wi-Fi Aware sessions.
        self.pub_attach_session, pub_mac_address = (
            aware_snippet_utils.start_attach(
                self.publisher, pub_config.ranging_enabled
            )
        )
        self.sub_attach_session, sub_mac_address = (
            aware_snippet_utils.start_attach(
                self.subscriber, pub_config.ranging_enabled
            )
        )

        # Step 2: Publisher publishes an Wi-Fi Aware service, subscriber
        # subscribes to it. Wait for service discovery.
        (
            self.pub_session,
            pub_session_handler,
            self.sub_session,
            sub_session_handler,
            sub_peer,
        ) = aware_snippet_utils.publish_and_subscribe(
            publisher=self.publisher,
            pub_config=pub_config,
            pub_attach_session=self.pub_attach_session,
            subscriber=self.subscriber,
            sub_config=sub_config,
            sub_attach_session=self.sub_attach_session,
        )

        # Step 3: Send messages through the discovery sessions.
        msg = _MSG_SUB_TO_PUB.format(random_id=utils.rand_ascii_str(5))
        pub_peer = aware_snippet_utils.send_msg_through_discovery_session(
            sender=self.subscriber,
            sender_discovery_session_handler=sub_session_handler,
            receiver=self.publisher,
            receiver_discovery_session_handler=pub_session_handler,
            discovery_session=self.sub_session,
            peer_on_sender=sub_peer,
            send_message=msg,
        )
        self.subscriber.log.info(
            'Sent a message to peer %d through discovery session.',
            sub_peer,
        )
        msg = _MSG_PUB_TO_SUB.format(random_id=utils.rand_ascii_str(5))
        aware_snippet_utils.send_msg_through_discovery_session(
            sender=self.publisher,
            sender_discovery_session_handler=pub_session_handler,
            receiver=self.subscriber,
            receiver_discovery_session_handler=sub_session_handler,
            discovery_session=self.pub_session,
            peer_on_sender=pub_peer,
            send_message=msg,
        )
        self.publisher.log.info(
            'Sent a message to peer %d through discovery session.',
            pub_peer,
        )
        return (pub_mac_address, sub_mac_address, pub_peer, sub_peer)

    def _perform_ranging(
        self,
        ad: android_device.AndroidDevice,
        request: constants.RangingRequest,
    ):
        """Performs ranging and checks the ranging result.

        Args:
            ad: The Android device controller.
            request: The ranging request.
        """
        ad.log.debug('Starting ranging with request: %s', request)
        ranging_cb_handler = ad.wifi.wifiAwareStartRanging(request.to_dict())
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
        asserts.assert_true(
            results is not None and len(results) == 1,
            'Ranging got invalid results: null, empty, or wrong length.',
        )

        status_code = results[0].get(
            constants.RangingResultCb.DATA_KEY_RESULT_STATUS, None
        )
        asserts.assert_equal(
            status_code,
            constants.RangingResultStatusCode.SUCCESS,
            'Ranging peer failed: invalid result status code.',
        )

        distance_mm = results[0].get(
            constants.RangingResultCb.DATA_KEY_RESULT_DISTANCE_MM, None
        )
        asserts.assert_true(
            (
                distance_mm is not None
                and distance_mm <= _LARGE_ENOUGH_DISTANCE_MM
            ),
            'Ranging peer failed: invalid distance in ranging result.',
        )
        rssi = results[0].get(
            constants.RangingResultCb.DATA_KEY_RESULT_RSSI, None
        )
        asserts.assert_true(
            rssi is not None and rssi >= _MIN_RSSI,
            'Ranging peer failed: invalid rssi in ranging result.',
        )

        peer_id = results[0].get(
            constants.RangingResultCb.DATA_KEY_PEER_ID, None
        )
        if peer_id is not None:
            msg = 'Ranging peer failed: invalid peer ID in ranging result.'
            asserts.assert_in(peer_id, request.peer_ids, msg)

        peer_mac = results[0].get(constants.RangingResultCb.DATA_KEY_MAC, None)
        if peer_mac is not None:
            msg = (
                'Ranging peer failed: invalid peer MAC address in ranging '
                'result.'
            )
            asserts.assert_in(peer_mac, request.peer_mac_addresses, msg)

    def teardown_test(self):
        utils.concurrent_exec(
            self._teardown_on_device,
            ((self.publisher,), (self.subscriber,)),
            max_workers=2,
            raise_on_exception=True,
        )
        self.pub_session = None
        self.sub_session = None
        self.pub_attach_session = None
        self.sub_attach_session = None

    def _teardown_on_device(self, ad: android_device.AndroidDevice) -> None:
        """Releases resources and sessions after each test."""
        try:
            ad.wifi.connectivityReleaseAllSockets()
            ad.wifi.wifiAwareCloseAllWifiAwareSession()
        finally:
            ad.services.create_output_excerpts_all(self.current_test_info)

    def on_fail(self, record: records.TestResult) -> None:
        logging.info('Collecting bugreports...')
        android_device.take_bug_reports(
            self.ads, destination=self.current_test_info.output_path
        )


if __name__ == '__main__':
    # Take test args
    if '--' in sys.argv:
        index = sys.argv.index('--')
        sys.argv = sys.argv[:1] + sys.argv[index + 1 :]

    test_runner.main()
