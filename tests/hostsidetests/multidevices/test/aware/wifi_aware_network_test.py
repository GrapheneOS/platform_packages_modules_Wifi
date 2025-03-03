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
"""Wi-Fi Aware network test module."""

import logging
import sys

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
_PUB_SSI = 'Extra bytes in the publisher discovery'.encode('utf-8')
_SUB_SSI = 'Arbitrary bytes for the subscribe discovery'.encode('utf-8')
_MATCH_FILTER = ('bytes used for matching'.encode('utf-8'),)
_PASSWORD = 'Some super secret password'
_PMK = '01234567890123456789012345678901'
_MSG_SUB_TO_PUB = "Let's talk [Random Identifier: {random_id}]"
_MSG_PUB_TO_SUB = 'Ready [Random Identifier: %s]'
_MSG_CLIENT_TO_SERVER = 'GET SOME BYTES [Random Identifier: {random_id}]'
_MSG_SERVER_TO_CLIENT = 'PUT SOME OTHER BYTES [Random Identifier: {random_id}]'
_TRANSPORT_PROTOCOL_TCP = (
    constants.WifiAwareTestConstants.TRANSPORT_PROTOCOL_TCP
)
_TEST_FREQUENCY_5745 = 5745


class WifiAwareNetworkTest(base_test.BaseTestClass):
    """Wi-Fi Aware network test class.

    All tests in this class share the same test steps and expected results.
    The difference is that each test tests against a different set of Wi-Fi
    Aware configuration.

    Test Preconditions:
        * All tests require two Android devices that support Wi-Fi Aware.
        * Test `test_data_path_force_channel_setup` requires the test devices to
          support setting a channel requirement in a data-path request.

    Test Steps:
        1. Attach a Wi-Fi Aware session on each device.
        2. Publisher publishes an Wi-Fi Aware service, subscriber subscribes to
           it. Wait for service discovery.
        3. Send messages through discovery session's API.
        4. Request a Wi-Fi Aware network on each device.
        5. Subscriber establishes a socket connection to the publisher.
        6. Send messages through the new socket connection.

    Expected Results:
        Publisher and subscriber can send and receive messages through the
        discovery session's API and the socket connection.
    """

    ads: list[android_device.AndroidDevice]
    publisher: android_device.AndroidDevice
    subscriber: android_device.AndroidDevice

    def setup_class(self):
        # Register and set up Android devices in parallel.
        self.ads = self.register_controller(android_device, min_number=2)
        self.publisher = self.ads[0]
        self.subscriber = self.ads[1]

        def setup_device(device: android_device.AndroidDevice):
            device.load_snippet('wifi', _SNIPPET_PACKAGE_NAME)
            device.wifi.wifiEnable()
            wifi_test_utils.set_screen_on_and_unlock(device)
            wifi_test_utils.enable_wifi_verbose_logging(device)
            # Device capability check
            asserts.abort_class_if(
                not device.wifi.wifiAwareIsSupported(),
                f'{device} does not support Wi-Fi Aware.',
            )
            asserts.abort_class_if(
                not device.wifi.wifiAwareIsAvailable(),
                f'{device} Wi-Fi Aware is not available.',
            )

        utils.concurrent_exec(
            setup_device,
            ((self.publisher,), (self.subscriber,)),
            max_workers=2,
            raise_on_exception=True,
        )

    @ApiTest(
        apis=[
            'android.net.wifi.aware.PublishConfig.Builder#setPublishType(int)',
            'android.net.wifi.aware.SubscribeConfig.Builder#setSubscribeType(int)',
            'android.net.wifi.aware.WifiAwareNetworkSpecifier.Builder#build()',
        ]
    )
    def test_data_path_open_unsolicited_pub_and_passive_sub(self) -> None:
        """Test Wi-Fi Aware OPEN network with unsolicited publish and passive
        subscribe.

        See class docstring for the test steps and expected results.
        """
        self._test_wifi_aware_network(
            pub_config=constants.PublishConfig(
                service_specific_info=_PUB_SSI,
                match_filter=_MATCH_FILTER,
                publish_type=constants.PublishType.UNSOLICITED,
                ranging_enabled=False,
            ),
            sub_config=constants.SubscribeConfig(
                service_specific_info=_SUB_SSI,
                match_filter=_MATCH_FILTER,
                subscribe_type=constants.SubscribeType.PASSIVE,
            ),
        )

    @ApiTest(
        apis=[
            'android.net.wifi.aware.PublishConfig.Builder#setPublishType(int)',
            'android.net.wifi.aware.SubscribeConfig.Builder#setSubscribeType(int)',
            'android.net.wifi.aware.WifiAwareNetworkSpecifier.Builder#build()',
            'android.net.wifi.aware.WifiAwareNetworkSpecifier.Builder#setPskPassphrase(String)',
        ]
    )
    def test_data_path_passphrase_unsolicited_pub_and_passive_sub(self) -> None:
        """Test Wi-Fi Aware network with passphrase, unsolicited publish, and
        passive subscribe.

        See class docstring for the test steps and expected results.
        """
        self._test_wifi_aware_network(
            pub_config=constants.PublishConfig(
                service_specific_info=_PUB_SSI,
                match_filter=_MATCH_FILTER,
                publish_type=constants.PublishType.UNSOLICITED,
                ranging_enabled=False,
            ),
            sub_config=constants.SubscribeConfig(
                service_specific_info=_SUB_SSI,
                match_filter=_MATCH_FILTER,
                subscribe_type=constants.SubscribeType.PASSIVE,
            ),
            network_specifier_on_pub=constants.WifiAwareNetworkSpecifier(
                psk_passphrase=_PASSWORD,
                transport_protocol=_TRANSPORT_PROTOCOL_TCP,
            ),
            network_specifier_on_sub=constants.WifiAwareNetworkSpecifier(
                psk_passphrase=_PASSWORD,
            ),
        )

    @ApiTest(
        apis=[
            'android.net.wifi.aware.PublishConfig.Builder#setPublishType(int)',
            'android.net.wifi.aware.SubscribeConfig.Builder#setSubscribeType(int)',
            'android.net.wifi.aware.WifiAwareNetworkSpecifier.Builder#build()',
            'android.net.wifi.aware.WifiAwareNetworkSpecifier.Builder#setPmk(byte[])',
        ]
    )
    def test_data_path_pmk_unsolicited_pub_and_passive_sub(self) -> None:
        """Test Wi-Fi Aware network using PMK with unsolicited publish and
        passive subscribe.

        See class docstring for the test steps and expected results.
        """
        self._test_wifi_aware_network(
            pub_config=constants.PublishConfig(
                service_specific_info=_PUB_SSI,
                match_filter=_MATCH_FILTER,
                publish_type=constants.PublishType.UNSOLICITED,
                ranging_enabled=False,
            ),
            sub_config=constants.SubscribeConfig(
                service_specific_info=_SUB_SSI,
                match_filter=_MATCH_FILTER,
                subscribe_type=constants.SubscribeType.PASSIVE,
            ),
            network_specifier_on_pub=constants.WifiAwareNetworkSpecifier(
                transport_protocol=_TRANSPORT_PROTOCOL_TCP,
                pmk=_PMK,
            ),
            network_specifier_on_sub=constants.WifiAwareNetworkSpecifier(
                data_path_security_config=constants.WifiAwareDataPathSecurityConfig(
                    pmk=_PMK,
                )
            ),
        )

    @ApiTest(
        apis=[
            'android.net.wifi.aware.PublishConfig.Builder#setPublishType(int)',
            'android.net.wifi.aware.SubscribeConfig.Builder#setSubscribeType(int)',
            'android.net.wifi.aware.WifiAwareNetworkSpecifier.Builder#build()',
        ]
    )
    def test_data_path_open_solicited_pub_and_active_sub(self) -> None:
        """Test OPEN Wi-Fi Aware network with solicited publish and active
        subscribe.

        See class docstring for the test steps and expected results.
        """
        self._test_wifi_aware_network(
            pub_config=constants.PublishConfig(
                service_specific_info=_PUB_SSI,
                match_filter=_MATCH_FILTER,
                publish_type=constants.PublishType.SOLICITED,
                ranging_enabled=False,
            ),
            sub_config=constants.SubscribeConfig(
                service_specific_info=_SUB_SSI,
                match_filter=_MATCH_FILTER,
                subscribe_type=constants.SubscribeType.ACTIVE,
            ),
        )

    @ApiTest(
        apis=[
            'android.net.wifi.aware.PublishConfig.Builder#setPublishType(int)',
            'android.net.wifi.aware.SubscribeConfig.Builder#setSubscribeType(int)',
            'android.net.wifi.aware.WifiAwareNetworkSpecifier.Builder#build()',
            'android.net.wifi.aware.WifiAwareNetworkSpecifier.Builder#setPskPassphrase(String)',
        ]
    )
    def test_data_path_passphrase_solicited_pub_and_active_sub(self) -> None:
        """Test Wi-Fi Aware network with passphrase, solicited publish, and
        active subscribe.

        See class docstring for the test steps and expected results.
        """
        self._test_wifi_aware_network(
            pub_config=constants.PublishConfig(
                service_specific_info=_PUB_SSI,
                match_filter=_MATCH_FILTER,
                publish_type=constants.PublishType.SOLICITED,
                ranging_enabled=False,
            ),
            sub_config=constants.SubscribeConfig(
                service_specific_info=_SUB_SSI,
                match_filter=_MATCH_FILTER,
                subscribe_type=constants.SubscribeType.ACTIVE,
            ),
            network_specifier_on_pub=constants.WifiAwareNetworkSpecifier(
                psk_passphrase=_PASSWORD,
                transport_protocol=_TRANSPORT_PROTOCOL_TCP,
            ),
            network_specifier_on_sub=constants.WifiAwareNetworkSpecifier(
                psk_passphrase=_PASSWORD,
            ),
        )

    @ApiTest(
        apis=[
            'android.net.wifi.aware.PublishConfig.Builder#setPublishType(int)',
            'android.net.wifi.aware.SubscribeConfig.Builder#setSubscribeType(int)',
            'android.net.wifi.aware.WifiAwareNetworkSpecifier.Builder#build()',
            'android.net.wifi.aware.WifiAwareNetworkSpecifier.Builder#setPmk(byte[])',
        ]
    )
    def test_data_path_pmk_solicited_pub_and_active_sub(self) -> None:
        """Test Wi-Fi Aware network with PMK, solicited publish, and active
        subscribe.

        See class docstring for the test steps and expected results.
        """
        self._test_wifi_aware_network(
            pub_config=constants.PublishConfig(
                service_specific_info=_PUB_SSI,
                match_filter=_MATCH_FILTER,
                publish_type=constants.PublishType.SOLICITED,
                ranging_enabled=False,
            ),
            sub_config=constants.SubscribeConfig(
                service_specific_info=_SUB_SSI,
                match_filter=_MATCH_FILTER,
                subscribe_type=constants.SubscribeType.ACTIVE,
            ),
            network_specifier_on_pub=constants.WifiAwareNetworkSpecifier(
                transport_protocol=_TRANSPORT_PROTOCOL_TCP,
                pmk=_PMK,
            ),
            network_specifier_on_sub=constants.WifiAwareNetworkSpecifier(
                data_path_security_config=constants.WifiAwareDataPathSecurityConfig(
                    pmk=_PMK,
                )
            ),
        )

    @ApiTest(
        apis=[
            'android.net.wifi.aware.SubscribeConfig.Builder#setSubscribeType(int)',
            'android.net.wifi.aware.WifiAwareNetworkSpecifier.Builder#build()',
            'android.net.wifi.aware.WifiAwareNetworkSpecifier.Builder#Builder(android.net.wifi.aware.PublishDiscoverySession)',
        ]
    )
    def test_data_path_open_unsolicited_pub_accept_any_and_passive_sub(
        self,
    ) -> None:
        """Test OPEN Wi-Fi Aware with unsolicited publish (accept any peer) and
        passive subscribe.

        See class docstring for the test steps and expected results.
        """
        self._test_wifi_aware_network(
            pub_config=constants.PublishConfig(
                service_specific_info=_PUB_SSI,
                match_filter=_MATCH_FILTER,
                publish_type=constants.PublishType.UNSOLICITED,
                ranging_enabled=False,
            ),
            sub_config=constants.SubscribeConfig(
                service_specific_info=_SUB_SSI,
                match_filter=_MATCH_FILTER,
                subscribe_type=constants.SubscribeType.PASSIVE,
            ),
            is_pub_accept_any_peer=True,
        )

    @ApiTest(
        apis=[
            'android.net.wifi.aware.SubscribeConfig.Builder#setSubscribeType(int)',
            'android.net.wifi.aware.WifiAwareNetworkSpecifier.Builder#build()',
            'android.net.wifi.aware.WifiAwareNetworkSpecifier.Builder#Builder(android.net.wifi.aware.PublishDiscoverySession)',
            'android.net.wifi.aware.WifiAwareNetworkSpecifier.Builder#setPskPassphrase(String)',
        ]
    )
    def test_data_path_passphrase_unsolicited_pub_accept_any_and_passive_sub(
        self,
    ) -> None:
        """Test Wi-Fi Aware with passphrase, unsolicited publish (accept any
        peer), and passive subscribe.

        See class docstring for the test steps and expected results.
        """
        self._test_wifi_aware_network(
            pub_config=constants.PublishConfig(
                service_specific_info=_PUB_SSI,
                match_filter=_MATCH_FILTER,
                publish_type=constants.PublishType.UNSOLICITED,
                ranging_enabled=False,
            ),
            sub_config=constants.SubscribeConfig(
                service_specific_info=_SUB_SSI,
                match_filter=_MATCH_FILTER,
                subscribe_type=constants.SubscribeType.PASSIVE,
            ),
            network_specifier_on_pub=constants.WifiAwareNetworkSpecifier(
                psk_passphrase=_PASSWORD,
                transport_protocol=_TRANSPORT_PROTOCOL_TCP,
            ),
            network_specifier_on_sub=constants.WifiAwareNetworkSpecifier(
                psk_passphrase=_PASSWORD,
            ),
            is_pub_accept_any_peer=True,
        )

    @ApiTest(
        apis=[
            'android.net.wifi.aware.SubscribeConfig.Builder#setSubscribeType(int)',
            'android.net.wifi.aware.WifiAwareNetworkSpecifier.Builder#build()',
            'android.net.wifi.aware.WifiAwareNetworkSpecifier.Builder#Builder(android.net.wifi.aware.PublishDiscoverySession)',
            'android.net.wifi.aware.WifiAwareNetworkSpecifier.Builder#setPmk(byte[])',
            'android.net.NetworkCapabilities#TRANSPORT_WIFI_AWARE',
        ]
    )
    def test_data_path_pmk_unsolicited_pub_accept_any_and_passive_sub(
        self,
    ) -> None:
        """Test Wi-Fi Aware with PMK, unsolicited publish (accept any peer), and
        passive subscribe.

        See class docstring for the test steps and expected results.
        """
        self._test_wifi_aware_network(
            pub_config=constants.PublishConfig(
                service_specific_info=_PUB_SSI,
                match_filter=_MATCH_FILTER,
                publish_type=constants.PublishType.UNSOLICITED,
                ranging_enabled=False,
            ),
            sub_config=constants.SubscribeConfig(
                service_specific_info=_SUB_SSI,
                match_filter=_MATCH_FILTER,
                subscribe_type=constants.SubscribeType.PASSIVE,
            ),
            network_specifier_on_pub=constants.WifiAwareNetworkSpecifier(
                transport_protocol=_TRANSPORT_PROTOCOL_TCP,
                pmk=_PMK,
            ),
            network_specifier_on_sub=constants.WifiAwareNetworkSpecifier(
                data_path_security_config=constants.WifiAwareDataPathSecurityConfig(
                    pmk=_PMK,
                )
            ),
            is_pub_accept_any_peer=True,
        )

    @ApiTest(
        apis=[
            'android.net.wifi.aware.SubscribeConfig.Builder#setSubscribeType(int)',
            'android.net.wifi.aware.WifiAwareNetworkSpecifier.Builder#build()',
            'android.net.wifi.aware.WifiAwareNetworkSpecifier.Builder#Builder(android.net.wifi.aware.PublishDiscoverySession)',
            'android.net.NetworkCapabilities#TRANSPORT_WIFI_AWARE',
        ]
    )
    def test_data_path_open_solicited_pub_accept_any_active_sub(self) -> None:
        """Test Wi-Fi Aware with open network, solicited publish (accept any
        peer), and active subscribe.

        See class docstring for the test steps and expected results.
        """
        self._test_wifi_aware_network(
            pub_config=constants.PublishConfig(
                service_specific_info=_PUB_SSI,
                match_filter=_MATCH_FILTER,
                publish_type=constants.PublishType.SOLICITED,
                ranging_enabled=False,
            ),
            sub_config=constants.SubscribeConfig(
                service_specific_info=_SUB_SSI,
                match_filter=_MATCH_FILTER,
                subscribe_type=constants.SubscribeType.ACTIVE,
            ),
            is_pub_accept_any_peer=True,
        )

    @ApiTest(
        apis=[
            'android.net.wifi.aware.SubscribeConfig.Builder#setSubscribeType(int)',
            'android.net.wifi.aware.WifiAwareNetworkSpecifier.Builder#build()',
            'android.net.wifi.aware.WifiAwareNetworkSpecifier.Builder#Builder(android.net.wifi.aware.PublishDiscoverySession)',
            'android.net.wifi.aware.WifiAwareNetworkSpecifier.Builder#setPskPassphrase(String)',
        ]
    )
    def test_data_path_passphrase_solicited_pub_accept_any_and_active_sub(
        self,
    ) -> None:
        """Test Wi-Fi Aware with passphrase, solicited publish (accept any
        peer), and active subscribe.

        See class docstring for the test steps and expected results.
        """
        self._test_wifi_aware_network(
            pub_config=constants.PublishConfig(
                service_specific_info=_PUB_SSI,
                match_filter=_MATCH_FILTER,
                publish_type=constants.PublishType.SOLICITED,
                ranging_enabled=False,
            ),
            sub_config=constants.SubscribeConfig(
                service_specific_info=_SUB_SSI,
                match_filter=_MATCH_FILTER,
                subscribe_type=constants.SubscribeType.ACTIVE,
            ),
            network_specifier_on_pub=constants.WifiAwareNetworkSpecifier(
                psk_passphrase=_PASSWORD,
                transport_protocol=_TRANSPORT_PROTOCOL_TCP,
            ),
            network_specifier_on_sub=constants.WifiAwareNetworkSpecifier(
                psk_passphrase=_PASSWORD,
            ),
            is_pub_accept_any_peer=True,
        )

    @ApiTest(
        apis=[
            'android.net.wifi.aware.SubscribeConfig.Builder#setSubscribeType(int)',
            'android.net.wifi.aware.WifiAwareNetworkSpecifier.Builder#build()',
            'android.net.wifi.aware.WifiAwareNetworkSpecifier.Builder#Builder(android.net.wifi.aware.PublishDiscoverySession)',
            'android.net.wifi.aware.WifiAwareNetworkSpecifier.Builder#setPmk(byte[])',
        ]
    )
    def test_data_path_pmk_solicited_pub_accept_any_and_active_sub(
        self,
    ) -> None:
        """Test Wi-Fi Aware with PMK, solicited publish (accept any peer), and
        active subscribe.

        See class docstring for the test steps and expected results.
        """
        self._test_wifi_aware_network(
            pub_config=constants.PublishConfig(
                service_specific_info=_PUB_SSI,
                match_filter=_MATCH_FILTER,
                publish_type=constants.PublishType.SOLICITED,
                ranging_enabled=False,
            ),
            sub_config=constants.SubscribeConfig(
                service_specific_info=_SUB_SSI,
                match_filter=_MATCH_FILTER,
                subscribe_type=constants.SubscribeType.ACTIVE,
            ),
            network_specifier_on_pub=constants.WifiAwareNetworkSpecifier(
                transport_protocol=_TRANSPORT_PROTOCOL_TCP,
                pmk=_PMK,
            ),
            network_specifier_on_sub=constants.WifiAwareNetworkSpecifier(
                data_path_security_config=constants.WifiAwareDataPathSecurityConfig(
                    pmk=_PMK,
                )
            ),
            is_pub_accept_any_peer=True,
        )

    @ApiTest(
        apis=[
            'android.net.wifi.aware.WifiAwareNetworkSpecifier.Builder#Builder(android.net.wifi.aware.DiscoverySession, android.net.wifi.aware.PeerHandle)',
            'android.net.wifi.aware.WifiAwareNetworkSpecifier.Builder#setChannelFrequencyMhz(int, boolean)',
        ]
    )
    def test_data_path_force_channel_setup(self):
        """Test Wi-Fi Aware network, forcing it to use a specific channel.

        This test case requires that both devices support setting a channel
        requirement in the Wi-Fi Aware data-path request.

        See class docstring for the test steps and expected results.
        """
        # The support of this function depends on the chip used.
        asserts.skip_if(
            not self.publisher.wifi.wifiAwareIsSetChannelOnDataPathSupported(),
            'Publish device not support this test feature.',
        )
        asserts.skip_if(
            not self.subscriber.wifi.wifiAwareIsSetChannelOnDataPathSupported(),
            'Subscriber device not support this test feature.',
        )

        self._test_wifi_aware_network(
            pub_config=constants.PublishConfig(
                service_specific_info=_PUB_SSI,
                match_filter=_MATCH_FILTER,
                publish_type=constants.PublishType.UNSOLICITED,
                ranging_enabled=False,
            ),
            sub_config=constants.SubscribeConfig(
                service_specific_info=_SUB_SSI,
                match_filter=_MATCH_FILTER,
                subscribe_type=constants.SubscribeType.PASSIVE,
            ),
            network_specifier_on_pub=constants.WifiAwareNetworkSpecifier(
                transport_protocol=_TRANSPORT_PROTOCOL_TCP,
                pmk=_PMK,
                channel_frequency_m_hz=_TEST_FREQUENCY_5745,
            ),
            network_specifier_on_sub=constants.WifiAwareNetworkSpecifier(
                data_path_security_config=constants.WifiAwareDataPathSecurityConfig(
                    pmk=_PMK,
                ),
                channel_frequency_m_hz=_TEST_FREQUENCY_5745,
            ),
        )

    def _test_wifi_aware_network(
        self,
        pub_config: constants.PublishConfig,
        sub_config: constants.SubscribeConfig,
        network_specifier_on_pub: (
            constants.WifiAwareNetworkSpecifier | None
        ) = None,
        network_specifier_on_sub: (
            constants.WifiAwareNetworkSpecifier | None
        ) = None,
        is_pub_accept_any_peer: bool = False,
    ):
        """Tests establishing a Wi-Fi Aware network and sending messages."""
        network_specifier_on_pub = (
            network_specifier_on_pub or constants.WifiAwareNetworkSpecifier()
        )
        network_specifier_on_sub = (
            network_specifier_on_sub or constants.WifiAwareNetworkSpecifier()
        )

        # Step 1: Attach Wi-Fi Aware sessions.
        pub_attach_session, _ = aware_snippet_utils.start_attach(
            self.publisher, pub_config.ranging_enabled
        )
        sub_attach_session, _ = aware_snippet_utils.start_attach(
            self.subscriber, pub_config.ranging_enabled
        )

        # Step 2: Publisher publishes an Wi-Fi Aware service, subscriber
        # subscribes to it. Wait for service discovery.
        (
            pub_session,
            pub_session_handler,
            sub_session,
            sub_session_handler,
            sub_peer,
        ) = aware_snippet_utils.publish_and_subscribe(
            publisher=self.publisher,
            pub_config=pub_config,
            pub_attach_session=pub_attach_session,
            subscriber=self.subscriber,
            sub_config=sub_config,
            sub_attach_session=sub_attach_session,
        )

        # Step 3: Send messages through the discovery sessions.
        msg = _MSG_SUB_TO_PUB.format(random_id=utils.rand_ascii_str(5))
        pub_peer = aware_snippet_utils.send_msg_through_discovery_session(
            sender=self.subscriber,
            sender_discovery_session_handler=sub_session_handler,
            receiver=self.publisher,
            receiver_discovery_session_handler=pub_session_handler,
            discovery_session=sub_session,
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
            discovery_session=pub_session,
            peer_on_sender=pub_peer,
            send_message=msg,
        )
        self.publisher.log.info(
            'Sent a message to peer %d through discovery session.',
            pub_peer,
        )

        # Step 4: Establish a Wi-Fi Aware network.
        # Step 4.1: Initialize a server socket on the publisher.
        pub_accept_handler = (
            self.publisher.wifi.connectivityServerSocketAccept()
        )
        network_id = pub_accept_handler.callback_id
        pub_local_port = pub_accept_handler.ret_value
        # `WifiAwareNetworkSpecifier` does not allow setting port for open
        # networks.
        if (
            network_specifier_on_pub.psk_passphrase is not None
            and network_specifier_on_pub.pmk is not None
        ):
            network_specifier_on_pub.port = pub_local_port
        # Step 4.2: Request a Wi-Fi Aware network on each device.
        pub_network_handler = aware_snippet_utils.request_aware_network(
            ad=self.publisher,
            discovery_session=pub_session,
            peer=pub_peer,
            network_id=network_id,
            network_specifier_params=network_specifier_on_pub,
            is_accept_any_peer=is_pub_accept_any_peer,
        )
        sub_network_handler = aware_snippet_utils.request_aware_network(
            ad=self.subscriber,
            discovery_session=sub_session,
            peer=sub_peer,
            network_id=network_id,
            network_specifier_params=network_specifier_on_sub,
        )
        # Step 4.3: Wait for network establishment.
        aware_snippet_utils.wait_for_aware_network(
            ad=self.publisher,
            request_network_handler=pub_network_handler,
        )
        network_cap_changed_event = aware_snippet_utils.wait_for_aware_network(
            ad=self.subscriber,
            request_network_handler=sub_network_handler,
        )
        # Check frequency if the config forces a channel.
        if network_specifier_on_sub.channel_frequency_m_hz:
            asserts.assert_equal(
                network_cap_changed_event.data[
                    constants.NetworkCbEventKey.CHANNEL_IN_MHZ
                ],
                [network_specifier_on_pub.channel_frequency_m_hz],
                f'{self.subscriber} Channel freq does not match the request.',
            )

        # Step 5: Establish a socket connection and send messages through it.
        aware_snippet_utils.establish_socket_connection(
            self.publisher,
            self.subscriber,
            pub_accept_handler=pub_accept_handler,
            network_id=network_id,
            pub_local_port=pub_local_port,
        )

        # Step 6: Send messages through the socket connection.
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

        # Test finished, clean up.
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
