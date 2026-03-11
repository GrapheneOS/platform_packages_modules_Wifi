#  Copyright (C) 2025 The Android Open Source Project
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
"""Wi-Fi Aware pairing test module."""

import logging
import sys

from android.platform.test.annotations import ApiTest
from mobly import asserts
from mobly import base_test
from mobly import records
from mobly import signals
from mobly import test_runner
from mobly import utils
from mobly.controllers import android_device
import wifi_test_utils

from aware import aware_snippet_utils
from aware import constants

_SNIPPET_PACKAGE_NAME = constants.WIFI_SNIPPET_PACKAGE_NAME
_PUB_SSI = 'Extra bytes in the publisher discovery'.encode('utf-8')
_SUB_SSI = 'Arbitrary bytes for the subscribe discovery'.encode('utf-8')
_MATCH_FILTER = (constants.WifiAwareTestConstants.MATCH_FILTER_BYTES,)
_PASSWORD = 'Some super secret password'
_CIPHER_SUITE = constants.Characteristics.WIFI_AWARE_CIPHER_SUITE_NCS_PK_PASN_128
_MSG_CLIENT_TO_SERVER = 'GET SOME BYTES [Random Identifier: {random_id}]'
_MSG_SERVER_TO_CLIENT = 'PUT SOME OTHER BYTES [Random Identifier: {random_id}]'
_TRANSPORT_PROTOCOL_TCP = (
    constants.WifiAwareTestConstants.TRANSPORT_PROTOCOL_TCP
)


class WifiAwarePairingTest(base_test.BaseTestClass):
    """Wi-Fi Aware pairing test class."""

    ads: list[android_device.AndroidDevice]
    publisher: android_device.AndroidDevice
    subscriber: android_device.AndroidDevice

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
                not device.wifi.wifiAwareIsAwarePairingSupported(),
                f'{device} does not support Wi-Fi Aware Pairing.',
            )
            asserts.abort_class_if(
                not device.wifi.wifiAwareIsAvailable(),
                f'Wi-Fi Aware is not available on {device}.',
            )

    def _setup_device(self, device: android_device.AndroidDevice):
        device.load_snippet('wifi', _SNIPPET_PACKAGE_NAME)
        device.wifi.wifiEnable()
        wifi_test_utils.set_screen_on_and_unlock(device)
        wifi_test_utils.enable_wifi_verbose_logging(device)

    def teardown_test(self):
        utils.concurrent_exec(
            self._teardown_on_device,
            ((self.publisher,), (self.subscriber,)),
            max_workers=2,
            raise_on_exception=True,
        )

    def _teardown_on_device(self, ad: android_device.AndroidDevice) -> None:
        """Releases resources and sessions after each test."""
        try:
            ad.wifi.wifiAwareCloseAllWifiAwareSession()
            ad.wifi.wifiAwareresetPairedDevices()
        finally:
            ad.services.create_output_excerpts_all(self.current_test_info)

    def on_fail(self, record: records.TestResult) -> None:
        logging.info('Collecting bugreports...')
        android_device.take_bug_reports(
            self.ads, destination=self.current_test_info.output_path
        )

    def _establish_and_verify_data_path(
        self, pub_session, sub_session, pub_peer_id, sub_peer_id
    ) -> int:
        """Establishes a Wi-Fi Aware data-path and verifies data exchange."""
        # Step 1: Establish a Wi-Fi Aware network.
        # Step 1.1: Initialize a server socket on the publisher.
        pub_accept_handler = (
            self.publisher.wifi.connectivityServerSocketAccept()
        )
        network_id = pub_accept_handler.callback_id
        pub_local_port = pub_accept_handler.ret_value

        # Step  1.2: Request a Wi-Fi Aware network on each device.
        network_specifier_on_pub = constants.WifiAwareNetworkSpecifier(
            psk_passphrase=_PASSWORD,
            port = pub_local_port,
            transport_protocol=_TRANSPORT_PROTOCOL_TCP
        )
        network_specifier_on_sub = constants.WifiAwareNetworkSpecifier(
            psk_passphrase=_PASSWORD,
        )
        pub_network_handler = aware_snippet_utils.request_aware_network(
            ad=self.publisher,
            discovery_session=pub_session,
            peer=pub_peer_id,
            network_id=network_id,
            network_specifier_params=network_specifier_on_pub,
            is_accept_any_peer=False,
        )
        sub_network_handler = aware_snippet_utils.request_aware_network(
            ad=self.subscriber,
            discovery_session=sub_session,
            peer=sub_peer_id,
            network_id=network_id,
            network_specifier_params=network_specifier_on_sub,
        )
        # Step 1.3: Wait for network establishment.
        aware_snippet_utils.wait_for_aware_network(
            ad=self.publisher,
            request_network_handler=pub_network_handler,
        )
        network_cap_changed_event = aware_snippet_utils.wait_for_aware_network(
            ad=self.subscriber,
            request_network_handler=sub_network_handler,
        )

        # Step 2: Establish a socket connection and send messages through it.
        aware_snippet_utils.establish_socket_connection(
            self.publisher,
            self.subscriber,
            pub_accept_handler=pub_accept_handler,
            network_id=network_id,
            pub_local_port=pub_local_port,
        )

        msg = _MSG_CLIENT_TO_SERVER.format(random_id=utils.rand_ascii_str(5))
        aware_snippet_utils.send_socket_msg(
            sender_ad=self.subscriber,
            receiver_ad=self.publisher,
            network_id=network_id,
            msg=msg,
        )
        msg = _MSG_SERVER_TO_CLIENT.format(random_id=utils.rand_ascii_str(5))
        aware_snippet_utils.send_socket_msg(
            sender_ad=self.publisher,
            receiver_ad=self.subscriber,
            network_id=network_id,
            msg=msg,
        )
        logging.info('Communicated through socket connection successfully.')
        return network_id

    @ApiTest(
        apis=[
            'android.net.wifi.aware.AwarePairingConfig',
            'android.net.wifi.aware.DiscoverySession#initiateBootstrapping',
            'android.net.wifi.aware.DiscoverySessionCallback#onBootstrappingSucceeded',
            'android.net.wifi.aware.DiscoverySession#initiatePairingRequest',
            'android.net.wifi.aware.DiscoverySessionCallback#onPairingRequestReceived',
            'android.net.wifi.aware.DiscoverySession#acceptPairingRequest',
            'android.net.wifi.aware.DiscoverySessionCallback#onPairingSetupSucceeded',
        ]
    )
    def test_aware_pairing_with_cache_enabled(self):
        """Verifies Wi-Fi Aware pairing and data-path with caching enabled.

        This test case covers the end-to-end flow of Wi-Fi Aware pairing,
        including service discovery, bootstrapping, pairing setup, and
        establishing a data-path connection over which data is exchanged.
        The pairing configuration has caching enabled.

        Test Steps:
        1.  Publisher and Subscriber set up pairing configs with caching
            enabled and `PIN_CODE_DISPLAY` as the bootstrapping method.
        2.  Publisher starts an unsolicited publish, and Subscriber starts a
            passive subscribe.
        3.  Wait for the service to be discovered.
        4.  Subscriber initiates bootstrapping with the Publisher.
        5.  Verify both devices receive the `onBootstrappingSucceeded` callback.
        6.  Subscriber initiates a pairing request to the Publisher.
        7.  Publisher receives the request and accepts it.
        8.  Verify both devices receive the `onPairingSetupSucceeded` callback.
        9.  Establish a Wi-Fi Aware data-path (socket connection) between the
            devices.
        10. Send messages over the socket in both directions to verify the
            data-path is functional.
        11. Clean up all sessions and network resources.
        12. Re-attach Wi-Fi Aware sessions.
        13. Publisher and Subscriber publish and subscribe again.
        14. Verify both devices receive the `onPairingVerificationSucceeded` callback.
        """

        pairing_config = constants.AwarePairingConfig(
            pairing_setup_enabled=True,
            pairing_cache_enabled=True,
            pairing_verification_enabled=True,
            bootstrapping_methods=constants.BootstrappingMethod.PIN_CODE_DISPLAY,
        )
        pub_config = constants.PublishConfig(
            publish_type=constants.PublishType.UNSOLICITED,
            service_specific_info=_PUB_SSI,
            pairing_config=pairing_config
        )
        sub_config = constants.SubscribeConfig(
            subscribe_type=constants.SubscribeType.PASSIVE,
            service_specific_info=_SUB_SSI,
            pairing_config=pairing_config
        )

        # Step 1: Attach Wi-Fi Aware sessions.
        pub_attach_session, _ = aware_snippet_utils.start_attach(
            self.publisher, is_ranging_enabled=False
        )
        sub_attach_session, _ = aware_snippet_utils.start_attach(
            self.subscriber, is_ranging_enabled=False
        )

        # Step 2: Publisher publishes an Wi-Fi Aware service, subscriber
        # subscribes to it. Wait for service discovery.
        (
            pub_session,
            pub_session_handler,
            sub_session,
            sub_session_handler,
            sub_peer_id,
        ) = aware_snippet_utils.publish_and_subscribe(
            publisher=self.publisher,
            pub_config=pub_config,
            pub_attach_session=pub_attach_session,
            subscriber=self.subscriber,
            sub_config=sub_config,
            sub_attach_session=sub_attach_session,
        )

        # Step 3: setup bootstrapping.
        # Step 3.1: Subscriber initiates bootstrapping.
        self.subscriber.wifi.wifiAwareInitiateBootstrapping(
            sub_session,
            sub_peer_id,
            constants.BootstrappingMethod.PIN_CODE_DISPLAY,
        )

        # Step 3.2: Both devices get bootstrapping success callback.
        pub_bootstrapping_success_event = pub_session_handler.waitAndGet(
            event_name=constants.DiscoverySessionCallbackMethodType.BOOTSTRAPPING_SUCCEEDED,
            timeout=constants.WAIT_WIFI_STATE_TIME_OUT.total_seconds(),
        )
        sub_bootstrapping_success_event = sub_session_handler.waitAndGet(
            event_name=constants.DiscoverySessionCallbackMethodType.BOOTSTRAPPING_SUCCEEDED,
            timeout=constants.WAIT_WIFI_STATE_TIME_OUT.total_seconds(),
        )

        asserts.assert_equal(
            pub_bootstrapping_success_event.data['bootstrappingMethod'],
            constants.BootstrappingMethod.PIN_CODE_DISPLAY,
            'Publisher received wrong bootstrapping method.',
        )
        asserts.assert_equal(
            sub_bootstrapping_success_event.data['bootstrappingMethod'],
            constants.BootstrappingMethod.PIN_CODE_DISPLAY,
            'Subscriber received wrong bootstrapping method.',
        )
        self.publisher.log.info('Publisher bootstrapping succeeded.')
        self.subscriber.log.info('Subscriber bootstrapping succeeded.')

        # Step 4: setup pairing.
        # Step 4.1: Subscriber initiates pairing request.
        self.subscriber.log.info('Subscriber initiates pairing request.')
        self.subscriber.wifi.wifiAwareInitiatePairing(
            sub_session,
            sub_peer_id,
            'publisher_alias',
            _CIPHER_SUITE.value,
            _PASSWORD,
        )

        # Step 4.2: Publisher receives and accepts pairing request.
        pairing_request_event = pub_session_handler.waitAndGet(
            event_name=constants.DiscoverySessionCallbackMethodType.PAIRING_REQUEST_RECEIVED,
            timeout=constants.WAIT_WIFI_STATE_TIME_OUT.total_seconds(),
        )
        request_id = pairing_request_event.data['pairingRequestId']
        pub_peer_id = pairing_request_event.data['peerId']
        self.publisher.log.info('Publisher received pairing request.')
        self.publisher.wifi.wifiAwareAcceptPairing(
            pub_session,
            request_id,
            pub_peer_id,
            'subscriber_alias',
            _CIPHER_SUITE.value,
            _PASSWORD,
        )

        # Step 4.3: Both devices get pairing success callback.
        pub_pairing_success_event = pub_session_handler.waitAndGet(
            event_name=constants.DiscoverySessionCallbackMethodType.PAIRING_SETUP_SUCCEEDED,
            timeout=constants.WAIT_WIFI_STATE_TIME_OUT.total_seconds(),
        )
        sub_pairing_success_event = sub_session_handler.waitAndGet(
            event_name=constants.DiscoverySessionCallbackMethodType.PAIRING_SETUP_SUCCEEDED,
            timeout=constants.WAIT_WIFI_STATE_TIME_OUT.total_seconds(),
        )

        asserts.assert_equal(
            pub_pairing_success_event.data['pairedAlias'],
            'subscriber_alias',
            'Publisher received wrong alias.',
        )
        asserts.assert_equal(
            sub_pairing_success_event.data['pairedAlias'],
            'publisher_alias',
            'Subscriber received wrong alias.',
        )
        self.publisher.log.info('Publisher pairing succeeded.')
        self.subscriber.log.info('Subscriber pairing succeeded.')

        # Step 5: Establish a Wi-Fi Aware data-path and verify data exchange.
        network_id = self._establish_and_verify_data_path(
            pub_session, sub_session, pub_peer_id, sub_peer_id
        )

        # Clean up network resources.
        self.publisher.wifi.connectivityCloseAllSocket(network_id)
        self.subscriber.wifi.connectivityCloseAllSocket(network_id)
        self.publisher.wifi.connectivityUnregisterNetwork(network_id)
        self.subscriber.wifi.connectivityUnregisterNetwork(network_id)
        # Clean up Wi-Fi Aware resources.
        self.publisher.wifi.wifiAwareCloseDiscoverSession(pub_session)
        self.subscriber.wifi.wifiAwareCloseDiscoverSession(sub_session)
        self.publisher.wifi.wifiAwareDetach(pub_attach_session)
        self.subscriber.wifi.wifiAwareDetach(sub_attach_session)

         # Step 6: re-Attach Wi-Fi Aware sessions.
        pub_attach_session, _ = aware_snippet_utils.start_attach(
            self.publisher, is_ranging_enabled=False
        )
        sub_attach_session, _ = aware_snippet_utils.start_attach(
            self.subscriber, is_ranging_enabled=False
        )

        # Step 7: Publisher publishes an Wi-Fi Aware service, subscriber
        # subscribes to it. Wait for service discovery.
        (
            pub_session,
            pub_session_handler,
            sub_session,
            sub_session_handler,
            sub_peer_id,
        ) = aware_snippet_utils.publish_and_subscribe(
            publisher=self.publisher,
            pub_config=pub_config,
            pub_attach_session=pub_attach_session,
            subscriber=self.subscriber,
            sub_config=sub_config,
            sub_attach_session=sub_attach_session,
        )

        # Step 8: Both devices get pairing verification success callback.
        pub_pairing_success_event = pub_session_handler.waitAndGet(
            event_name=constants.DiscoverySessionCallbackMethodType.PAIRING_VERIFICATION_SUCCEEDED,
            timeout=constants.WAIT_WIFI_STATE_TIME_OUT.total_seconds(),
        )
        sub_pairing_success_event = sub_session_handler.waitAndGet(
            event_name=constants.DiscoverySessionCallbackMethodType.PAIRING_VERIFICATION_SUCCEEDED,
            timeout=constants.WAIT_WIFI_STATE_TIME_OUT.total_seconds(),
        )
        asserts.assert_equal(
            pub_pairing_success_event.data['pairedAlias'],
            'subscriber_alias',
            'Publisher received wrong alias.',
        )
        asserts.assert_equal(
            sub_pairing_success_event.data['pairedAlias'],
            'publisher_alias',
            'Subscriber received wrong alias.',
        )

        # Clean up Wi-Fi Aware resources.
        self.publisher.wifi.wifiAwareCloseDiscoverSession(pub_session)
        self.subscriber.wifi.wifiAwareCloseDiscoverSession(sub_session)
        self.publisher.wifi.wifiAwareDetach(pub_attach_session)
        self.subscriber.wifi.wifiAwareDetach(sub_attach_session)

    def test_aware_pairing_with_cache_disabled(self):
        """Verifies Wi-Fi Aware pairing and data-path with caching disabled.

        This test case covers the end-to-end flow of Wi-Fi Aware pairing,
        including service discovery, bootstrapping, pairing setup, and
        establishing a data-path connection over which data is exchanged.
        The pairing configuration has caching disabled.

        Test Steps:
        1.  Publisher and Subscriber set up pairing configs with caching
            disabled and `PIN_CODE_DISPLAY` as the bootstrapping method.
        2.  Publisher starts an unsolicited publish, and Subscriber starts a
            passive subscribe.
        3.  Wait for the service to be discovered.
        4.  Subscriber initiates bootstrapping with the Publisher.
        5.  Verify both devices receive the `onBootstrappingSucceeded` callback.
        6.  Subscriber initiates a pairing request to the Publisher.
        7.  Publisher receives the request and accepts it.
        8.  Verify both devices receive the `onPairingSetupSucceeded` callback.
        9.  Establish a Wi-Fi Aware data-path (socket connection) between the
            devices.
        10. Send messages over the socket in both directions to verify the
            data-path is functional.
        11. Clean up all sessions and network resources.
        12. Re-attach Wi-Fi Aware sessions.
        13. Publisher and Subscriber publish and subscribe again.
        14. Verify neither device receives the `onPairingVerificationSucceeded`
            callback.
        """
        wifi_test_utils.skip_if_not_meet_min_sdk_level(self.publisher, 37)
        wifi_test_utils.skip_if_not_meet_min_sdk_level(self.subscriber, 37)

        pairing_config = constants.AwarePairingConfig(
            pairing_setup_enabled=True,
            pairing_cache_enabled=False,
            pairing_verification_enabled=True,
            bootstrapping_methods=constants.BootstrappingMethod.PIN_CODE_DISPLAY,
        )
        pub_config = constants.PublishConfig(
            publish_type=constants.PublishType.UNSOLICITED,
            service_specific_info=_PUB_SSI,
            pairing_config=pairing_config
        )
        sub_config = constants.SubscribeConfig(
            subscribe_type=constants.SubscribeType.PASSIVE,
            service_specific_info=_SUB_SSI,
            pairing_config=pairing_config
        )

        # Step 1: Attach Wi-Fi Aware sessions.
        pub_attach_session, _ = aware_snippet_utils.start_attach(
            self.publisher, is_ranging_enabled=False
        )
        sub_attach_session, _ = aware_snippet_utils.start_attach(
            self.subscriber, is_ranging_enabled=False
        )

        # Step 2: Publisher publishes an Wi-Fi Aware service, subscriber
        # subscribes to it. Wait for service discovery.
        (
            pub_session,
            pub_session_handler,
            sub_session,
            sub_session_handler,
            sub_peer_id,
        ) = aware_snippet_utils.publish_and_subscribe(
            publisher=self.publisher,
            pub_config=pub_config,
            pub_attach_session=pub_attach_session,
            subscriber=self.subscriber,
            sub_config=sub_config,
            sub_attach_session=sub_attach_session,
        )

        # Step 3: setup bootstrapping.
        # Step 3.1: Subscriber initiates bootstrapping.
        self.subscriber.wifi.wifiAwareInitiateBootstrapping(
            sub_session,
            sub_peer_id,
            constants.BootstrappingMethod.PIN_CODE_DISPLAY,
        )

        # Step 3.2: Both devices get bootstrapping success callback.
        pub_bootstrapping_success_event = pub_session_handler.waitAndGet(
            event_name=constants.DiscoverySessionCallbackMethodType.BOOTSTRAPPING_SUCCEEDED,
            timeout=constants.WAIT_WIFI_STATE_TIME_OUT.total_seconds(),
        )
        sub_bootstrapping_success_event = sub_session_handler.waitAndGet(
            event_name=constants.DiscoverySessionCallbackMethodType.BOOTSTRAPPING_SUCCEEDED,
            timeout=constants.WAIT_WIFI_STATE_TIME_OUT.total_seconds(),
        )

        asserts.assert_equal(
            pub_bootstrapping_success_event.data['bootstrappingMethod'],
            constants.BootstrappingMethod.PIN_CODE_DISPLAY,
            'Publisher received wrong bootstrapping method.',
        )
        asserts.assert_equal(
            sub_bootstrapping_success_event.data['bootstrappingMethod'],
            constants.BootstrappingMethod.PIN_CODE_DISPLAY,
            'Subscriber received wrong bootstrapping method.',
        )
        self.publisher.log.info('Publisher bootstrapping succeeded.')
        self.subscriber.log.info('Subscriber bootstrapping succeeded.')

        # Step 4: setup pairing.
        # Step 4.1: Subscriber initiates pairing request.
        self.subscriber.log.info('Subscriber initiates pairing request.')
        self.subscriber.wifi.wifiAwareInitiatePairing(
            sub_session,
            sub_peer_id,
            'publisher_alias',
            _CIPHER_SUITE.value,
            _PASSWORD,
        )

        # Step 4.2: Publisher receives and accepts pairing request.
        pairing_request_event = pub_session_handler.waitAndGet(
            event_name=constants.DiscoverySessionCallbackMethodType.PAIRING_REQUEST_RECEIVED,
            timeout=constants.WAIT_WIFI_STATE_TIME_OUT.total_seconds(),
        )
        request_id = pairing_request_event.data['pairingRequestId']
        pub_peer_id = pairing_request_event.data['peerId']
        self.publisher.log.info('Publisher received pairing request.')
        self.publisher.wifi.wifiAwareAcceptPairing(
            pub_session,
            request_id,
            pub_peer_id,
            'subscriber_alias',
            _CIPHER_SUITE.value,
            _PASSWORD,
        )

        # Step 4.3: Both devices get pairing success callback.
        pub_pairing_success_event = pub_session_handler.waitAndGet(
            event_name=constants.DiscoverySessionCallbackMethodType.PAIRING_SETUP_SUCCEEDED,
            timeout=constants.WAIT_WIFI_STATE_TIME_OUT.total_seconds(),
        )
        sub_pairing_success_event = sub_session_handler.waitAndGet(
            event_name=constants.DiscoverySessionCallbackMethodType.PAIRING_SETUP_SUCCEEDED,
            timeout=constants.WAIT_WIFI_STATE_TIME_OUT.total_seconds(),
        )

        asserts.assert_equal(
            pub_pairing_success_event.data['pairedAlias'],
            'subscriber_alias',
            'Publisher received wrong alias.',
        )
        asserts.assert_equal(
            sub_pairing_success_event.data['pairedAlias'],
            'publisher_alias',
            'Subscriber received wrong alias.',
        )
        self.publisher.log.info('Publisher pairing succeeded.')
        self.subscriber.log.info('Subscriber pairing succeeded.')

        # Step 5: Establish a Wi-Fi Aware data-path and verify data exchange.
        network_id = self._establish_and_verify_data_path(
            pub_session, sub_session, pub_peer_id, sub_peer_id
        )

        # Clean up network resources.
        self.publisher.wifi.connectivityCloseAllSocket(network_id)
        self.subscriber.wifi.connectivityCloseAllSocket(network_id)
        self.publisher.wifi.connectivityUnregisterNetwork(network_id)
        self.subscriber.wifi.connectivityUnregisterNetwork(network_id)
        # Clean up Wi-Fi Aware resources.
        self.publisher.wifi.wifiAwareCloseDiscoverSession(pub_session)
        self.subscriber.wifi.wifiAwareCloseDiscoverSession(sub_session)
        self.publisher.wifi.wifiAwareDetach(pub_attach_session)
        self.subscriber.wifi.wifiAwareDetach(sub_attach_session)

        # Step 6: re-Attach Wi-Fi Aware sessions.
        pub_attach_session, _ = aware_snippet_utils.start_attach(
            self.publisher, is_ranging_enabled=False
        )
        sub_attach_session, _ = aware_snippet_utils.start_attach(
            self.subscriber, is_ranging_enabled=False
        )

        # Step 7: Publisher publishes an Wi-Fi Aware service, subscriber
        # subscribes to it. Wait for service discovery.
        (
            pub_session,
            pub_session_handler,
            sub_session,
            sub_session_handler,
            sub_peer_id,
        ) = aware_snippet_utils.publish_and_subscribe(
            publisher=self.publisher,
            pub_config=pub_config,
            pub_attach_session=pub_attach_session,
            subscriber=self.subscriber,
            sub_config=sub_config,
            sub_attach_session=sub_attach_session,
        )

        # Step 8: Both devices shouldn't get pairing verification callback.
        try:
          pub_session_handler.waitAndGet(
              event_name=constants.DiscoverySessionCallbackMethodType.PAIRING_VERIFICATION_SUCCEEDED,
              timeout=constants.WAIT_WIFI_STATE_TIME_OUT.total_seconds(),
          )
          raise signals.TestFailure('Publisher received pairing verification callback.')
        except Exception as e:
          self.publisher.log.info(
              'Publisher did not receive pairing verification callback.')
        try:
          sub_session_handler.waitAndGet(
              event_name=constants.DiscoverySessionCallbackMethodType.PAIRING_VERIFICATION_SUCCEEDED,
              timeout=constants.WAIT_WIFI_STATE_TIME_OUT.total_seconds(),
          )
          raise signals.TestFailure('Subscriber received pairing verification callback.')
        except Exception as e:
          self.subscriber.log.info(
              'Subscriber did not receive pairing verification callback.')

        # Clean up Wi-Fi Aware resources.
        self.publisher.wifi.wifiAwareCloseDiscoverSession(pub_session)
        self.subscriber.wifi.wifiAwareCloseDiscoverSession(sub_session)
        self.publisher.wifi.wifiAwareDetach(pub_attach_session)
        self.subscriber.wifi.wifiAwareDetach(sub_attach_session)

    @ApiTest(
        apis=[

            'android.net.wifi.aware.DiscoverySession#rejectPairingRequest',
            'android.net.wifi.aware.DiscoverySessionCallback#onPairingSetupFailed',
        ]
    )
    def test_aware_pairing_rejection(self):
        """Verifies Wi-Fi Aware pairing rejection.

        This test case covers the end-to-end flow of Wi-Fi Aware pairing,
        including service discovery, bootstrapping, pairing setup, and
        establishing a data-path connection over which data is exchanged.
        The pairing configuration has caching disabled.

        Test Steps:
        1.  Publisher and Subscriber set up pairing configs with caching
            disabled and `PIN_CODE_DISPLAY` as the bootstrapping method.
        2.  Publisher starts an unsolicited publish, and Subscriber starts a
            passive subscribe.
        3.  Wait for the service to be discovered.
        4.  Subscriber initiates bootstrapping with the Publisher.
        5.  Verify both devices receive the `onBootstrappingSucceeded` callback.
        6.  Subscriber initiates a pairing request to the Publisher.
        7.  Publisher receives the request and rejects it.
        8.  Verify both devices receive the `onPairingSetupFailed` callback.
        9.  Clean up all sessions and network resources.
        """

        pairing_config = constants.AwarePairingConfig(
            pairing_setup_enabled=True,
            pairing_cache_enabled=True,
            pairing_verification_enabled=True,
            bootstrapping_methods=constants.BootstrappingMethod.PIN_CODE_DISPLAY,
        )
        pub_config = constants.PublishConfig(
            publish_type=constants.PublishType.UNSOLICITED,
            service_specific_info=_PUB_SSI,
            pairing_config=pairing_config
        )
        sub_config = constants.SubscribeConfig(
            subscribe_type=constants.SubscribeType.PASSIVE,
            service_specific_info=_SUB_SSI,
            pairing_config=pairing_config
        )

        # Step 1: Attach Wi-Fi Aware sessions.
        pub_attach_session, _ = aware_snippet_utils.start_attach(
            self.publisher, is_ranging_enabled=False
        )
        sub_attach_session, _ = aware_snippet_utils.start_attach(
            self.subscriber, is_ranging_enabled=False
        )

        # Step 2: Publisher publishes an Wi-Fi Aware service, subscriber
        # subscribes to it. Wait for service discovery.
        (
            pub_session,
            pub_session_handler,
            sub_session,
            sub_session_handler,
            sub_peer_id,
        ) = aware_snippet_utils.publish_and_subscribe(
            publisher=self.publisher,
            pub_config=pub_config,
            pub_attach_session=pub_attach_session,
            subscriber=self.subscriber,
            sub_config=sub_config,
            sub_attach_session=sub_attach_session,
        )

        # Step 3: setup bootstrapping.
        # Step 3.1: Subscriber initiates bootstrapping.
        self.subscriber.wifi.wifiAwareInitiateBootstrapping(
            sub_session,
            sub_peer_id,
            constants.BootstrappingMethod.PIN_CODE_DISPLAY,
        )

        # Step 3.2: Both devices get bootstrapping success callback.
        pub_bootstrapping_success_event = pub_session_handler.waitAndGet(
            event_name=constants.DiscoverySessionCallbackMethodType.BOOTSTRAPPING_SUCCEEDED,
            timeout=constants.WAIT_WIFI_STATE_TIME_OUT.total_seconds(),
        )
        sub_bootstrapping_success_event = sub_session_handler.waitAndGet(
            event_name=constants.DiscoverySessionCallbackMethodType.BOOTSTRAPPING_SUCCEEDED,
            timeout=constants.WAIT_WIFI_STATE_TIME_OUT.total_seconds(),
        )

        asserts.assert_equal(
            pub_bootstrapping_success_event.data['bootstrappingMethod'],
            constants.BootstrappingMethod.PIN_CODE_DISPLAY,
            'Publisher received wrong bootstrapping method.',
        )
        asserts.assert_equal(
            sub_bootstrapping_success_event.data['bootstrappingMethod'],
            constants.BootstrappingMethod.PIN_CODE_DISPLAY,
            'Subscriber received wrong bootstrapping method.',
        )
        self.publisher.log.info('Publisher bootstrapping succeeded.')
        self.subscriber.log.info('Subscriber bootstrapping succeeded.')

        # Step 4: setup pairing.
        # Step 4.1: Subscriber initiates pairing request.
        self.subscriber.log.info('Subscriber initiates pairing request.')
        self.subscriber.wifi.wifiAwareInitiatePairing(
            sub_session,
            sub_peer_id,
            'publisher_alias',
            _CIPHER_SUITE.value,
            _PASSWORD,
        )

        # Step 4.2: Publisher receives and rejects pairing request.
        pairing_request_event = pub_session_handler.waitAndGet(
            event_name=constants.DiscoverySessionCallbackMethodType.PAIRING_REQUEST_RECEIVED,
            timeout=constants.WAIT_WIFI_STATE_TIME_OUT.total_seconds(),
        )
        request_id = pairing_request_event.data['pairingRequestId']
        pub_peer_id = pairing_request_event.data['peerId']
        self.publisher.log.info('Publisher received pairing request.')
        self.publisher.wifi.wifiAwareRejectPairing(
            pub_session,
            request_id,
            pub_peer_id,
        )
        # Step 4.3: Both devices get pairing failure callback.
        pub_session_handler.waitAndGet(
            event_name=constants.DiscoverySessionCallbackMethodType.PAIRING_SETUP_FAILED,
            timeout=constants.WAIT_WIFI_STATE_TIME_OUT.total_seconds(),
        )
        sub_session_handler.waitAndGet(
            event_name=constants.DiscoverySessionCallbackMethodType.PAIRING_SETUP_FAILED,
            timeout=constants.WAIT_WIFI_STATE_TIME_OUT.total_seconds(),
        )

        # Clean up Wi-Fi Aware resources.
        self.publisher.wifi.wifiAwareCloseDiscoverSession(pub_session)
        self.subscriber.wifi.wifiAwareCloseDiscoverSession(sub_session)
        self.publisher.wifi.wifiAwareDetach(pub_attach_session)
        self.subscriber.wifi.wifiAwareDetach(sub_attach_session)

    @ApiTest(
        apis=[
            'android.net.wifi.aware.AwarePairingConfig',
            'android.net.wifi.aware.DiscoverySession#initiateBootstrapping',
            (
                'android.net.wifi.aware.DiscoverySessionCallback'
                '#onBootstrappingSucceeded'
            ),
        ]
    )
    def test_boostraping_method_neogotiation_matched(self):
        """Test negotiation with compatible bootstrapping methods.

        This test verifies that bootstrapping succeeds when the initiator
        (subscriber) and responder (publisher) have compatible bootstrapping
        methods.

        Test Steps:
        1. Publisher supports PIN_CODE_DISPLAY.
        2. Subscriber supports PIN_CODE_KEYPAD.
        3. Subscriber initiates bootstrapping.
        4. Both should receive a bootstrapping success callback.
        """
        pub_config = constants.PublishConfig(
            publish_type=constants.PublishType.UNSOLICITED,
            service_specific_info=_PUB_SSI,
            pairing_config=constants.AwarePairingConfig(
                pairing_setup_enabled=True,
                pairing_cache_enabled=True,
                pairing_verification_enabled=True,
                bootstrapping_methods=constants.BootstrappingMethod.PIN_CODE_DISPLAY,
            ),
        )
        sub_config = constants.SubscribeConfig(
            subscribe_type=constants.SubscribeType.PASSIVE,
            service_specific_info=_SUB_SSI,
            pairing_config=constants.AwarePairingConfig(
                pairing_setup_enabled=True,
                pairing_cache_enabled=True,
                pairing_verification_enabled=True,
                bootstrapping_methods=constants.BootstrappingMethod.PIN_CODE_KEYPAD,
            ),
        )

        # Step 1: Attach Wi-Fi Aware sessions.
        pub_attach_session, _ = aware_snippet_utils.start_attach(
            self.publisher, is_ranging_enabled=False
        )
        sub_attach_session, _ = aware_snippet_utils.start_attach(
            self.subscriber, is_ranging_enabled=False
        )
        # Step 2: Publisher publishes an Wi-Fi Aware service, subscriber
        # subscribes to it. Wait for service discovery.
        (
            pub_session,
            pub_session_handler,
            sub_session,
            sub_session_handler,
            sub_peer_id,
        ) = aware_snippet_utils.publish_and_subscribe(
            publisher=self.publisher,
            pub_config=pub_config,
            pub_attach_session=pub_attach_session,
            subscriber=self.subscriber,
            sub_config=sub_config,
            sub_attach_session=sub_attach_session,
        )

        # Step 3: setup bootstrapping.
        # Step 3.1: Subscriber initiates bootstrapping.
        self.subscriber.wifi.wifiAwareInitiateBootstrapping(
            sub_session,
            sub_peer_id,
            constants.BootstrappingMethod.PIN_CODE_KEYPAD,
        )

        # Step 3.2: Both devices get bootstrapping success callback.
        pub_bootstrapping_success_event = pub_session_handler.waitAndGet(
            event_name=constants.DiscoverySessionCallbackMethodType.BOOTSTRAPPING_SUCCEEDED,
            timeout=constants.WAIT_WIFI_STATE_TIME_OUT.total_seconds(),
        )
        sub_bootstrapping_success_event = sub_session_handler.waitAndGet(
            event_name=constants.DiscoverySessionCallbackMethodType.BOOTSTRAPPING_SUCCEEDED,
            timeout=constants.WAIT_WIFI_STATE_TIME_OUT.total_seconds(),
        )

        asserts.assert_equal(
            pub_bootstrapping_success_event.data['bootstrappingMethod'],
            constants.BootstrappingMethod.PIN_CODE_DISPLAY,
            'Publisher received wrong bootstrapping method.',
        )
        asserts.assert_equal(
            sub_bootstrapping_success_event.data['bootstrappingMethod'],
            constants.BootstrappingMethod.PIN_CODE_KEYPAD,
            'Subscriber received wrong bootstrapping method.',
        )
        self.publisher.log.info('Publisher bootstrapping succeeded.')
        self.subscriber.log.info('Subscriber bootstrapping succeeded.')

    def test_boostraping_method_neogotiation_mismatch(self):
        """Test negotiation with incompatible bootstrapping methods.

        This test verifies that bootstrapping fails when the initiator
        (subscriber) and responder (publisher) have incompatible bootstrapping
        methods.

        1. Publisher supports NFC_READER.
        2. Subscriber supports PIN_CODE_KEYPAD.
        3. Subscriber initiates bootstrapping.
        4. Subscriber should receive a bootstrapping failure callback.
        """
        pub_config = constants.PublishConfig(
            publish_type=constants.PublishType.UNSOLICITED,
            service_specific_info=_PUB_SSI,
            pairing_config=constants.AwarePairingConfig(
                pairing_setup_enabled=True,
                pairing_cache_enabled=True,
                pairing_verification_enabled=True,
                bootstrapping_methods=constants.BootstrappingMethod.NFC_READER,
            ),
        )
        sub_config = constants.SubscribeConfig(
            subscribe_type=constants.SubscribeType.PASSIVE,
            service_specific_info=_SUB_SSI,
            pairing_config=constants.AwarePairingConfig(
                pairing_setup_enabled=True,
                pairing_cache_enabled=True,
                pairing_verification_enabled=True,
                bootstrapping_methods=constants.BootstrappingMethod.PIN_CODE_KEYPAD,
            ),
        )

        # Step 1: Attach Wi-Fi Aware sessions.
        pub_attach_session, _ = aware_snippet_utils.start_attach(
            self.publisher, is_ranging_enabled=False
        )
        sub_attach_session, _ = aware_snippet_utils.start_attach(
            self.subscriber, is_ranging_enabled=False
        )
        # Step 2: Publisher publishes an Wi-Fi Aware service, subscriber
        # subscribes to it. Wait for service discovery.
        (
            pub_session,
            pub_session_handler,
            sub_session,
            sub_session_handler,
            sub_peer_id,
        ) = aware_snippet_utils.publish_and_subscribe(
            publisher=self.publisher,
            pub_config=pub_config,
            pub_attach_session=pub_attach_session,
            subscriber=self.subscriber,
            sub_config=sub_config,
            sub_attach_session=sub_attach_session,
        )

        # Step 3: setup bootstrapping.
        # Step 3.1: Subscriber initiates bootstrapping.
        self.subscriber.wifi.wifiAwareInitiateBootstrapping(
            sub_session,
            sub_peer_id,
            constants.BootstrappingMethod.PIN_CODE_KEYPAD,
        )

        # Step 3.2: only subscriber gets bootstrapping failure callback.
        sub_session_handler.waitAndGet(
            event_name=constants.DiscoverySessionCallbackMethodType.BOOTSTRAPPING_FAILED,
            timeout=constants.WAIT_WIFI_STATE_TIME_OUT.total_seconds(),
        )
        self.subscriber.log.info('Subscriber bootstrapping failed.')

if __name__ == '__main__':
    # Take test args
    if '--' in sys.argv:
        index = sys.argv.index('--')
        sys.argv = sys.argv[:1] + sys.argv[index + 1 :]

    test_runner.main()
