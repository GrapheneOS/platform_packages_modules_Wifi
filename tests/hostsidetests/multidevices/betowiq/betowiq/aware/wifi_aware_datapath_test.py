#  Copyright (C) 2026 The Android Open Source Project
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

"""Wi-Fi Aware Datapath test reimplemented in Mobly."""

import base64
import logging
import sys
import time
import re

from betowiq.aware import aware_lib_utils as autils
from betowiq.aware import constants
from mobly import asserts
from mobly import base_test
from mobly import records
from mobly import test_runner
from mobly import utils
from mobly.controllers import android_device
from mobly.controllers.android_device_lib import callback_handler_v2
from mobly.snippet import callback_event
from queue import Empty


RUNTIME_PERMISSIONS = (
    'android.permission.ACCESS_FINE_LOCATION',
    'android.permission.ACCESS_COARSE_LOCATION',
    'android.permission.NEARBY_WIFI_DEVICES',
)
PACKAGE_NAME = constants.WIFI_AWARE_SNIPPET_PACKAGE_NAME
_DEFAULT_TIMEOUT = constants.WAIT_WIFI_STATE_TIME_OUT.total_seconds()
_CALLBACK_NAME = constants.DiscoverySessionCallbackParamsType.CALLBACK_NAME
_IS_SESSION_INIT = constants.DiscoverySessionCallbackParamsType.IS_SESSION_INIT
_MESSAGE_SEND_SUCCEEDED = (
    constants.DiscoverySessionCallbackMethodType.MESSAGE_SEND_SUCCEEDED
    )
_MESSAGE_RECEIVED = (
    constants.DiscoverySessionCallbackMethodType.MESSAGE_RECEIVED
    )
_MESSAGE_SEND_RESULT = (
    constants.DiscoverySessionCallbackMethodType.MESSAGE_SEND_RESULT
    )
_TRANSPORT_TYPE_WIFI_AWARE = (
    constants.NetworkCapabilities.Transport.TRANSPORT_WIFI_AWARE
)

_NETWORK_CB_KEY_NETWORK_SPECIFIER = "network_specifier"

_NETWORK_CB_LINK_PROPERTIES_CHANGED = (
    constants.NetworkCbName.ON_PROPERTIES_CHANGED)
_NETWORK_CB_KEY_INTERFACE_NAME = "interfaceName"
_CAP_MAX_NDI_INTERFACES = "maxNdiInterfaces"


# Aware Data-Path Constants
_DATA_PATH_INITIATOR = 0
_DATA_PATH_RESPONDER = 1

# Publish & Subscribe Config keys.
_PAYLOAD_SIZE_MIN = 0
_PAYLOAD_SIZE_TYPICAL = 1
_PAYLOAD_SIZE_MAX = 2
_PUBLISH_TYPE_UNSOLICITED = 0
_PUBLISH_TYPE_SOLICITED = 1
_SUBSCRIBE_TYPE_PASSIVE = 0
_SUBSCRIBE_TYPE_ACTIVE = 1

_REQUEST_NETWORK_TIMEOUT_MS = 15 * 1000


class WifiAwareDatapathTest(base_test.BaseTestClass):
    """Set of tests for Wi-Fi Aware data-path."""

    # message ID counter to make sure all uses are unique
    msg_id = 0

    # number of second to 'reasonably' wait to make sure that devices synchronize
    # with each other - useful for OOB test cases, where the OOB discovery would
    # take some time
    WAIT_FOR_CLUSTER = 5

    EVENT_NDP_TIMEOUT = 20

    # configuration parameters used by tests
    ENCR_TYPE_OPEN = 0
    ENCR_TYPE_PASSPHRASE = 1
    ENCR_TYPE_PMK = 2

    PASSPHRASE = "This is some random passphrase - very very secure!!"
    PASSPHRASE_MIN = "01234567"
    PASSPHRASE_MAX = (
        "012345678901234567890123456789012345678901234567890123456789012")
    PMK = "ODU0YjE3YzdmNDJiNWI4NTQ2NDJjNDI3M2VkZTQyZGU="
    PASSPHRASE2 = (
        "This is some random passphrase - very very secure - but diff!!")
    PMK2 = "MTIzNDU2Nzg5MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTI="

    PING_MSG = "ping"


    SERVICE_NAME = "GoogleTestServiceDataPath"

    ads: list[android_device.AndroidDevice]
    publisher: android_device.AndroidDevice
    subscriber: android_device.AndroidDevice

    def setup_class(self):
        # Register two Android devices.
        self.ads = self.register_controller(android_device, min_number=2)
        self.publisher = self.ads[0]
        self.subscriber = self.ads[1]

        def setup_device(device: android_device.AndroidDevice):
            device.load_snippet(
                'wifi_aware_snippet', PACKAGE_NAME
            )
            for permission in RUNTIME_PERMISSIONS:
                device.adb.shell(['pm', 'grant', PACKAGE_NAME, permission])
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
            autils.control_wifi(ad, True)
            aware_avail = ad.wifi_aware_snippet.wifiAwareIsAvailable()
            if not aware_avail:
                ad.log.info('Aware not available. Waiting ...')
                state_handler = ad.wifi_aware_snippet.wifiAwareMonitorStateChange()
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

    def _teardown_test_on_device(self, ad: android_device.AndroidDevice) -> None:
        ad.wifi_aware_snippet.wifiAwareCloseAllWifiAwareSession()
        ad.wifi_aware_snippet.connectivityReleaseAllSockets()
        if ad.is_adb_root:
          autils.reset_device_parameters(ad)
          autils.validate_forbidden_callbacks(ad)
          autils.reset_device_statistics(ad)

    def on_fail(self, record: records.TestResult) -> None:
        android_device.take_bug_reports(self.ads,
                                        destination =
                                        self.current_test_info.output_path)

    def _start_attach(self, ad: android_device.AndroidDevice) -> str:
        """Starts the attach process on the provided device."""
        handler = ad.wifi_aware_snippet.wifiAwareAttach()
        attach_event = handler.waitAndGet(
            event_name = constants.AttachCallBackMethodType.ATTACHED,
            timeout = _DEFAULT_TIMEOUT,
        )
        asserts.assert_true(
            ad.wifi_aware_snippet.wifiAwareIsSessionAttached(handler.callback_id),
            f'{ad} attach succeeded, but Wi-Fi Aware session is still null.'
        )
        ad.log.info('Attach Wi-Fi Aware session succeeded.')
        return attach_event.callback_id

    def get_next_msg_id(self):
        """Increment the message ID and returns the new value.
        Guarantees that each call to the method returns a unique value.

        Returns: a new message id value.
        """

        self.msg_id = self.msg_id + 1
        return self.msg_id

    def _request_network(
        self,
        ad: android_device.AndroidDevice,
        discovery_session: str,
        peer: int,
        net_work_request_id: str,
        network_specifier_params:
        constants.WifiAwareNetworkSpecifier | None = None,
        is_accept_any_peer: bool = False,
    ) -> callback_handler_v2.CallbackHandlerV2:
        """Requests and configures a Wi-Fi Aware network connection."""
        network_specifier_parcel = (
            ad.wifi_aware_snippet.wifiAwareCreateNetworkSpecifier(
                discovery_session,
                peer,
                is_accept_any_peer,
                network_specifier_params.to_dict()
                if network_specifier_params else None,
            )
        )
        network_request_dict = constants.NetworkRequest(
            transport_type=_TRANSPORT_TYPE_WIFI_AWARE,
            network_specifier_parcel=network_specifier_parcel,
        ).to_dict()
        ad.log.debug('Requesting Wi-Fi Aware network: %s', network_request_dict)
        return ad.wifi_aware_snippet.connectivityRequestNetwork(
            net_work_request_id, network_request_dict, _REQUEST_NETWORK_TIMEOUT_MS
        )

    def set_up_discovery(self,
                         ptype,
                         stype,
                         get_peer_id,
                         pub_on_both=False,
                         pub_on_both_same=True):
        """Set up discovery sessions and wait for service discovery.

        Args:
            ptype: Publish discovery type
            stype: Subscribe discovery type
            get_peer_id: Send a message across to get the peer's id
            pub_on_both: If True then set up a publisher on both devices.
                         The second publisher isn't used (existing to test
                          use-case).
            pub_on_both_same: If True then the second publish uses an identical
                        service name, otherwise a different service name.
        """
        p_dut = self.ads[0]
        s_dut = self.ads[1]
        p_dut.pretty_name = "Publisher"
        s_dut.pretty_name = "Subscriber"

        # Publisher+Subscriber: attach and wait for confirmation
        p_id = self._start_attach(p_dut)
        s_id = self._start_attach(s_dut)

        # Publisher: start publish and wait for confirmation
        p_config = autils.create_discovery_config(self.SERVICE_NAME,
                                                  p_type =ptype,
                                                  s_type = None)
        p_disc_id = p_dut.wifi_aware_snippet.wifiAwarePublish(
            p_id, p_config
                )
        logging.info('Created the DUT publish session %s', p_disc_id)
        p_discovery = p_disc_id.waitAndGet(
            constants.DiscoverySessionCallbackMethodType.DISCOVER_RESULT)
        callback_name = p_discovery.data[_CALLBACK_NAME]
        asserts.assert_equal(
            constants.DiscoverySessionCallbackMethodType.PUBLISH_STARTED,
            callback_name,
            f'{p_dut} DUT publish failed, got callback: {callback_name}.',
            )
        # Optionally set up a publish session on the Subscriber device
        if pub_on_both:
            p2_config = autils.create_discovery_config(self.SERVICE_NAME,
                                                  p_type = ptype,
                                                  s_type = None)
            if not pub_on_both_same:
                p2_config[constants.SERVICE_NAME] = (
                        p2_config[constants.SERVICE_NAME] + "-XYZXYZ")
            s_disc_id= s_dut.wifi_aware_snippet.wifiAwarePublish(
                s_id, p2_config)
            s_dut.log.info('Created the DUT publish session %s', s_disc_id)
            s_discovery = s_disc_id.waitAndGet(
                constants.DiscoverySessionCallbackMethodType.DISCOVER_RESULT)
            callback_name = s_discovery.data[_CALLBACK_NAME]
            asserts.assert_equal(
                constants.DiscoverySessionCallbackMethodType.PUBLISH_STARTED,
                callback_name,
                f'{s_dut} DUT publish failed, got callback: {callback_name}.',
            )

        # Subscriber: start subscribe and wait for confirmation
        s_config = autils.create_discovery_config(self.SERVICE_NAME,
                                                  p_type = None,
                                                  s_type = stype
                                                  )
        s_disc_id = s_dut.wifi_aware_snippet.wifiAwareSubscribe(
            s_id, s_config
                )
        s_dut.log.info('Created the DUT subscribe session.: %s', s_disc_id)
        s_discovery = s_disc_id.waitAndGet(
                constants.DiscoverySessionCallbackMethodType.DISCOVER_RESULT,
                timeout=_DEFAULT_TIMEOUT)
        callback_name = s_discovery.data[_CALLBACK_NAME]
        asserts.assert_equal(
            constants.DiscoverySessionCallbackMethodType.SUBSCRIBE_STARTED,
            callback_name,
            f'{s_dut} DUT subscribe failed, got callback: {callback_name}.',
            )
        discovered_event = s_disc_id.waitAndGet(
        constants.DiscoverySessionCallbackMethodType.SERVICE_DISCOVERED)
        peer_id_on_sub =(
            discovered_event.data[constants.WifiAwareSnippetParams.PEER_ID])
        peer_id_on_pub = None
        if get_peer_id:  # only need message to receive peer ID
            # Subscriber: send message to peer (
            # Publisher - so it knows our address)
            s_dut.wifi_aware_snippet.wifiAwareSendMessage(
                s_disc_id.callback_id,
                peer_id_on_sub,
                self.get_next_msg_id(),
                self.PING_MSG,
                )
            tx_event = s_disc_id.waitAndGet(
            event_name = _MESSAGE_SEND_RESULT,
            timeout = _DEFAULT_TIMEOUT,
            )
            # Publisher: wait for received message
            rx_event = p_disc_id.waitAndGet(
                event_name = _MESSAGE_RECEIVED,
                timeout = _DEFAULT_TIMEOUT,
            )
            pub_rx_msg_event = rx_event.data[
                constants.WifiAwareSnippetParams.RECEIVED_MESSAGE
            ]
            peer_id_on_pub = rx_event.data[
                constants.WifiAwareSnippetParams.PEER_ID]
        return (p_dut, s_dut, p_id, s_id, p_disc_id, s_disc_id, peer_id_on_sub,
                peer_id_on_pub)

    def verify_network_info(self, p_data, s_data, open, port,
                            transport_protocol):
        """Verify that the port and transport protocol information is correct.
            - should only exist on subscriber (received from publisher)
              and match transmitted values
            - should only exist on an encrypted NDP

        Args:
            p_data, s_data: Pub and Sub (respectively) net cap event data.
            open: True if NDP unencrypted, False if encrypted.
            port: Expected port value.
            transport_protocol: Expected transport protocol value.
        """
        asserts.assert_true(constants.NetworkCbName.NET_CAP_PORT not in p_data,
                            "port info not expected on Pub")
        asserts.assert_true(
            constants.NetworkCbName.NET_CAP_TRANSPORT_PROTOCOL not in p_data,
            "transport protocol info not expected on Pub")
        if open:
            asserts.assert_true(
                constants.NetworkCbName.NET_CAP_PORT not in s_data,
                "port info not expected on Sub (open NDP)")
            asserts.assert_true(
                constants.NetworkCbName.NET_CAP_TRANSPORT_PROTOCOL not in s_data,
                "transport protocol info not expected on Sub (open NDP)")
        else:
            asserts.assert_equal(
                s_data[constants.NetworkCbName.NET_CAP_PORT], port,
                "Port info does not match on Sub (from Pub)")
            asserts.assert_equal(
                s_data[constants.NetworkCbName.NET_CAP_TRANSPORT_PROTOCOL],
                transport_protocol,
                "Transport protocol info does not match on Sub (from Pub)")

    def _wait_accept_success(
        self,
        pub_accept_handler: callback_handler_v2.CallbackHandlerV2
    ) -> None:
        pub_accept_event = pub_accept_handler.waitAndGet(
            event_name=constants.SnippetEventNames.SERVER_SOCKET_ACCEPT,
            timeout=_DEFAULT_TIMEOUT
        )
        is_accept = pub_accept_event.data.get(
            constants.SnippetEventParams.IS_ACCEPT, False)
        if not is_accept:
            error = pub_accept_event.data[constants.SnippetEventParams.ERROR]
            asserts.fail(
                f'{self.publisher} Failed to accept the connection.'+
                ' Error: {error}'
            )

    def _send_socket_msg(
        self,
        sender_ad: android_device.AndroidDevice,
        receiver_ad: android_device.AndroidDevice,
        msg: str,
        send_callback_id: str,
        receiver_callback_id: str,
    ):
        """Sends a message from one device to another and verifies receipt."""
        is_write_socket = sender_ad.wifi_aware_snippet.connectivityWriteSocket(
            send_callback_id, msg
        )
        asserts.assert_true(
            is_write_socket,
            f'{sender_ad} Failed to write data to the socket.'
        )
        sender_ad.log.info('Wrote data to the socket.')
        self.publisher.log.info('Server socket accepted the connection.')
        # Verify received message
        received_message = receiver_ad.wifi_aware_snippet.connectivityReadSocket(
            receiver_callback_id, len(msg)
        )
        logging.info(
            "msg: %s,received_message: %s",msg, received_message)
        asserts.assert_equal(
            received_message,
            msg,
            f'{receiver_ad} received message mismatched.'+
            'Failure:Expected {msg} but got '
            f'{received_message}.'
        )
        receiver_ad.log.info('Read data from the socket.')


    def _establish_socket_and_send_msg(
        self,
        pub_accept_handler: callback_handler_v2.CallbackHandlerV2,
        network_id: str,
        pub_local_port: int
    ):
        """Handles socket-based communication between publisher and subscriber."""
        # Init socket
        # Create a ServerSocket and makes it listen for client connections.
        self.subscriber.wifi_aware_snippet.connectivityCreateSocketOverWiFiAware(
            network_id, pub_local_port
        )
        self._wait_accept_success(pub_accept_handler)
        # Subscriber Send socket data
        self.subscriber.log.info('Subscriber create a socket.')
        self._send_socket_msg(
            sender_ad=self.subscriber,
            receiver_ad=self.publisher,
            msg=constants.WifiAwareTestConstants.MSG_CLIENT_TO_SERVER,
            send_callback_id=network_id,
            receiver_callback_id=network_id
        )
        self._send_socket_msg(
            sender_ad=self.publisher,
            receiver_ad=self.subscriber,
            msg=constants.WifiAwareTestConstants.MSG_SERVER_TO_CLIENT,
            send_callback_id=network_id,
            receiver_callback_id=network_id
        )
        self.publisher.wifi_aware_snippet.connectivityCloseWrite(network_id)
        self.subscriber.wifi_aware_snippet.connectivityCloseWrite(network_id)
        self.publisher.wifi_aware_snippet.connectivityCloseRead(network_id)
        self.subscriber.wifi_aware_snippet.connectivityCloseRead(network_id)
        logging.info('Communicated through socket connection of'+
            'Wi-Fi Aware network successfully.')

    def natwark_callback_even(self,
                             key,
                             name,
                             timeout = 5):
        """
        Attempts to collect up to 3 events from a queue.

        This function will loop a maximum of three times, trying to fetch an
        event in each iteration. The loop will exit early if the queue is
        empty or if an unexpected error occurs.

        Args:
            key: The object with the waitAndGet method.
            name: The name of the event to wait for.
            timeout: The timeout in seconds for each wait attempt.

        Returns:
            A list of the events that were successfully collected.
        """
        all_available_events = []
        # Loop a maximum of 3 times.
        # The underscore '_' is used as the variable name because we don't
        # need to use the loop counter itself.
        for _ in range(3):
            try:
                event = key.waitAndGet(
                event_name = name,
                timeout = timeout
                )
                all_available_events.append(event)
                logging.info(f"Collected event: {event}")
            except Empty:
                # The queue is empty, so we can't get any more events.
                # Stop trying.
                logging.info(
                    "No more events in the queue. Exiting collection loop.")
                break
            except Exception as e:
                # An unexpected error occurred. Log it and stop trying.
                logging.error(f"An unexpected error occurred: {e}")
                break
        return all_available_events

    def find_callback_name(self, events, name):
        """Finds the first event in a list by its callback name.

        Args:
            events: A list of event objects to search.
            name: The callback name to find.

        Returns:
            The first matching event object.

        Raises:
            ValueError: If no event with the specified name is found.
        """
        for event in events:
            if event.data[_CALLBACK_NAME] == name:
                return event
        # If the loop completes without finding a match, raise an error.
        raise ValueError(f"Callback with name '{name}' not found.")


    def run_ib_data_path_test(self,
                              ptype,
                              stype,
                              encr_type,
                              use_peer_id,
                              passphrase_to_use=None,
                              pub_on_both=False,
                              pub_on_both_same=True,
                              expect_failure=False):
        """Runs the in-band data-path tests.

        Args:
            ptype: Publish discovery type
            stype: Subscribe discovery type
            encr_type: Encryption type, one of ENCR_TYPE_*
            use_peer_id: On Responder (publisher): True to use peer ID, False
                        toaccept any request
            passphrase_to_use: The passphrase to use if encr_type=
                ENCR_TYPE_PASSPHRASE If None then use self.PASSPHRASE
            pub_on_both: If True then set up a publisher on both devices.
             The second publisher isn't used (existing to test use-case).
            pub_on_both_same: If True then the second publish uses an identical
                        service name, otherwise a different service name.
        expect_failure: If True then don't expect NDP formation, otherwise expect
                      NDP setup to succeed.
        """
        (p_dut, s_dut, p_id, s_id, p_disc_id, s_disc_id, peer_id_on_sub,
                peer_id_on_pub) = self.set_up_discovery(
             ptype, stype, use_peer_id,
            pub_on_both=pub_on_both, pub_on_both_same=pub_on_both_same)
        passphrase = None
        pmk = None

        if encr_type == self.ENCR_TYPE_PASSPHRASE:
            passphrase = (self.PASSPHRASE
                          if passphrase_to_use == None else passphrase_to_use)
        elif encr_type == self.ENCR_TYPE_PMK:
            pmk = base64.b64decode(self.PMK).decode("utf-8")

        port = 1234
        transport_protocol = 6  # TCP/IP

        # Publisher: request network
        pub_accept_handler = (
            p_dut.wifi_aware_snippet.connectivityServerSocketAccept())
        network_id = pub_accept_handler.callback_id
        pub_local_port = pub_accept_handler.ret_value
        self.publish_session = p_disc_id.callback_id
        self.subscribe_session = s_disc_id.callback_id
        if encr_type == self.ENCR_TYPE_OPEN:
            p_req_key = self._request_network(
                ad=p_dut,
                discovery_session=self.publish_session,
                peer=peer_id_on_pub if use_peer_id else None,
                net_work_request_id=network_id,
                network_specifier_params=constants.WifiAwareNetworkSpecifier(
                    psk_passphrase=passphrase,
                    pmk=pmk,
                    ),
                 is_accept_any_peer = False if use_peer_id else True,
                 )

        else:
            p_req_key = self._request_network(
                ad=p_dut,
                discovery_session=self.publish_session,
                peer=peer_id_on_pub if use_peer_id else None,
                net_work_request_id=network_id,
                network_specifier_params = constants.WifiAwareNetworkSpecifier(
                    psk_passphrase=passphrase,
                    pmk=pmk,
                    port=pub_local_port,
                    transport_protocol=transport_protocol
                    ),
                is_accept_any_peer = False if use_peer_id else True,
                )
        # Subscriber: request network
        s_req_key = self._request_network(
            ad=s_dut,
            discovery_session=self.subscribe_session,
            peer=peer_id_on_sub,
            net_work_request_id=network_id,
            network_specifier_params=constants.WifiAwareNetworkSpecifier(
                psk_passphrase=passphrase,
                pmk=pmk,
                ),
            )
        collected_events = self.natwark_callback_even(
            p_req_key, constants.NetworkCbEventName.NETWORK_CALLBACK)
        # After the loop, the list will contain all successfully collected events.
        logging.info(
            f"Finished collection. Total events saved: {len(collected_events)}"
        )
        p_callback_name = (
            self.find_callback_name(
                collected_events,
                constants.NetworkCbName.ON_CAPABILITIES_CHANGED))
        all_available_events =self.natwark_callback_even(
            s_req_key, constants.NetworkCbEventName.NETWORK_CALLBACK)
        s_callback_name=(
            self.find_callback_name(
                all_available_events,
                constants.NetworkCbName.ON_CAPABILITIES_CHANGED))
        if s_callback_name:
            logging.info(
                f"Successfully found the ON_CAPABILITIES_CHANGED event.")
            # Now you can access its data.
            # For example, if the event data is in a 'data' key:
            s_callback_data = s_callback_name.data
            logging.info(f"s_callback_data data: {s_callback_data}")
        else:
            logging.warning(
                "Could not find the ON_CAPABILITIES_CHANGED event in the list."
            )

        if expect_failure:
            asserts.assert_equal(
                p_callback_name.data[_CALLBACK_NAME],
                constants.NetworkCbName.ON_UNAVAILABLE,
                f'{p_dut} failed .to request the network, got callback'
                f' {p_callback_name}.'
                )
            asserts.assert_equal(
                s_callback_name.data[_CALLBACK_NAME],
                constants.NetworkCbName.ON_UNAVAILABLE,
                f'{s_dut} failed to request the network, got callback'
                f' {s_callback_name}.'
                )
        else:
            # Publisher & Subscriber: wait for network formation
            asserts.assert_equal(
                p_callback_name.data[_CALLBACK_NAME],
                constants.NetworkCbName.ON_CAPABILITIES_CHANGED,
                f'{p_dut} succeeded to request the network, got callback'
                f' {p_callback_name}.'
                )
            network = p_callback_name.data[
                constants.NetworkCbEventKey.NETWORK]
            network_capabilities = p_callback_name.data[
                constants.NetworkCbEventKey.NETWORK_CAPABILITIES]
            asserts.assert_true(
                network and network_capabilities,
                f'{p_dut} received a null Network or NetworkCapabilities!?.'
            )
            asserts.assert_equal(
                s_callback_name.data[_CALLBACK_NAME],
                constants.NetworkCbName.ON_CAPABILITIES_CHANGED,
                f'{s_dut} succeeded to request the network, got callback'
                f' {s_callback_name}.'
                )
            network = s_callback_name.data[
                constants.NetworkCbEventKey.NETWORK]
            network_capabilities = s_callback_name.data[
                constants.NetworkCbEventKey.NETWORK_CAPABILITIES]
            asserts.assert_true(
                network and network_capabilities,
                f'{s_dut} received a null Network or NetworkCapabilities!?.'
            )
            p_net_event_nc = p_callback_name.data
            s_net_event_nc = s_callback_name.data
            # validate no leak of information
            asserts.assert_false(
                _NETWORK_CB_KEY_NETWORK_SPECIFIER in p_net_event_nc,
                "Network specifier leak!")
            asserts.assert_false(
                _NETWORK_CB_KEY_NETWORK_SPECIFIER in s_net_event_nc,
                "Network specifier leak!")

            #To get ipv6 ip address
            s_ipv6= p_net_event_nc[constants.NetworkCbName.NET_CAP_IPV6]
            p_ipv6 = s_net_event_nc[constants.NetworkCbName.NET_CAP_IPV6]
            # note that Pub <-> Sub since IPv6 are of peer's!
            self.verify_network_info(
                p_callback_name.data,
                s_callback_name.data,
                encr_type == self.ENCR_TYPE_OPEN,
                port = pub_local_port,
                transport_protocol = transport_protocol)
            p_network_callback_LINK = (
                self.find_callback_name(
                    collected_events, _NETWORK_CB_LINK_PROPERTIES_CHANGED)
                )
            asserts.assert_equal(
                p_network_callback_LINK.data[_CALLBACK_NAME],
                _NETWORK_CB_LINK_PROPERTIES_CHANGED,
                f'{p_dut} succeeded to request the LinkPropertiesChanged,'+
                ' got callback'
                f' {p_network_callback_LINK.data[_CALLBACK_NAME]}.'
            )
            s_network_callback_LINK = (
                self.find_callback_name(
                    all_available_events, _NETWORK_CB_LINK_PROPERTIES_CHANGED)
            )
            asserts.assert_equal(
                s_network_callback_LINK.data[_CALLBACK_NAME],
                _NETWORK_CB_LINK_PROPERTIES_CHANGED,
                f'{s_dut} succeeded to request the LinkPropertiesChanged,'+
                ' got callback'
                f' {s_network_callback_LINK.data[_CALLBACK_NAME]}.'
                )
            p_aware_if = p_network_callback_LINK.data[
                                _NETWORK_CB_KEY_INTERFACE_NAME]
            s_aware_if = s_network_callback_LINK.data[
                                _NETWORK_CB_KEY_INTERFACE_NAME]

            logging.info("Interface names: p=%s, s=%s", p_aware_if,
                        s_aware_if)
            logging.info("Interface addresses (IPv6): p=%s, s=%s", p_ipv6,
                        s_ipv6)
            self._establish_socket_and_send_msg(
                pub_accept_handler=pub_accept_handler,
                network_id=network_id,
                pub_local_port=pub_local_port
                )

            # terminate sessions and wait for ON_LOST callbacks
            p_dut.wifi_aware_snippet.wifiAwareDetach(p_id)
            s_dut.wifi_aware_snippet.wifiAwareDetach(s_id)
            time.sleep(10)
            p_network_callback_lost = p_req_key.waitAndGet(
                event_name=constants.NetworkCbEventName.NETWORK_CB_LOST,
                timeout=_DEFAULT_TIMEOUT,
            )
            s_network_callback_lost = s_req_key.waitAndGet(
                event_name=constants.NetworkCbEventName.NETWORK_CB_LOST,
                timeout=_DEFAULT_TIMEOUT,
            )
        p_dut.wifi_aware_snippet.connectivityUnregisterNetwork(network_id)
        s_dut.wifi_aware_snippet.connectivityUnregisterNetwork(network_id)

    #######################################
    # Positive In-Band (IB) tests key:
    #
    # names is: test_ib_<pub_type>_<sub_type>_<encr_type>_<peer_spec>
    # where:
    #
    # pub_type: Type of publish discovery session: unsolicited or solicited.
    # sub_type: Type of subscribe discovery session: passive or active.
    # encr_type: Encryption type: open, passphrase
    # peer_spec: Peer specification method: any or specific
    #
    # Note: In-Band means using Wi-Fi Aware for discovery and referring to the
    # peer using the Aware-provided peer handle (as opposed to a MAC address).
    #######################################

    def test_ib_unsolicited_passive_open_specific(self):
        """Data-path: in-band, unsolicited/passive, open encryption, specific peer

    Verifies end-to-end discovery + data-path creation.
    """
        self.run_ib_data_path_test(
            ptype=_PUBLISH_TYPE_UNSOLICITED,
            stype=_SUBSCRIBE_TYPE_PASSIVE,
            encr_type=self.ENCR_TYPE_OPEN,
            use_peer_id=True)

    def test_ib_unsolicited_passive_open_any(self):
        """Data-path: in-band, unsolicited/passive, open encryption, any peer

    Verifies end-to-end discovery + data-path creation.
    """
        self.run_ib_data_path_test(
            ptype=_PUBLISH_TYPE_UNSOLICITED,
            stype=_SUBSCRIBE_TYPE_PASSIVE,
            encr_type=self.ENCR_TYPE_OPEN,
            use_peer_id=False)

    def test_ib_unsolicited_passive_passphrase_specific(self):
        """Data-path: in-band, unsolicited/passive, passphrase, specific peer

    Verifies end-to-end discovery + data-path creation.
    """
        self.run_ib_data_path_test(
            ptype=_PUBLISH_TYPE_UNSOLICITED,
            stype=_SUBSCRIBE_TYPE_PASSIVE,
            encr_type=self.ENCR_TYPE_PASSPHRASE,
            use_peer_id=True)

    def test_ib_unsolicited_passive_passphrase_any(self):
        """Data-path: in-band, unsolicited/passive, passphrase, any peer

    Verifies end-to-end discovery + data-path creation.
    """
        self.run_ib_data_path_test(
            ptype=_PUBLISH_TYPE_UNSOLICITED,
            stype=_SUBSCRIBE_TYPE_PASSIVE,
            encr_type=self.ENCR_TYPE_PASSPHRASE,
            use_peer_id=False)

    def test_ib_unsolicited_passive_pmk_specific(self):
        """Data-path: in-band, unsolicited/passive, PMK, specific peer

    Verifies end-to-end discovery + data-path creation.
    """
        self.run_ib_data_path_test(
            ptype=_PUBLISH_TYPE_UNSOLICITED,
            stype=_SUBSCRIBE_TYPE_PASSIVE,
            encr_type=self.ENCR_TYPE_PMK,
            use_peer_id=True)

    def test_ib_unsolicited_passive_pmk_any(self):
        """Data-path: in-band, unsolicited/passive, PMK, any peer

    Verifies end-to-end discovery + data-path creation.
    """
        self.run_ib_data_path_test(
            ptype=_PUBLISH_TYPE_UNSOLICITED,
            stype=_SUBSCRIBE_TYPE_PASSIVE,
            encr_type=self.ENCR_TYPE_PMK,
            use_peer_id=False)

    def test_ib_solicited_active_open_specific(self):
        """Data-path: in-band, solicited/active, open encryption, specific peer

    Verifies end-to-end discovery + data-path creation.
    """
        self.run_ib_data_path_test(
            ptype=_PUBLISH_TYPE_SOLICITED,
            stype=_SUBSCRIBE_TYPE_ACTIVE,
            encr_type=self.ENCR_TYPE_OPEN,
            use_peer_id=True)

    def test_ib_solicited_active_open_any(self):
        """Data-path: in-band, solicited/active, open encryption, any peer

    Verifies end-to-end discovery + data-path creation.
    """
        self.run_ib_data_path_test(
            ptype=_PUBLISH_TYPE_SOLICITED,
            stype=_SUBSCRIBE_TYPE_ACTIVE,
            encr_type=self.ENCR_TYPE_OPEN,
            use_peer_id=False)

    def test_ib_solicited_active_passphrase_specific(self):
        """Data-path: in-band, solicited/active, passphrase, specific peer

    Verifies end-to-end discovery + data-path creation.
    """
        self.run_ib_data_path_test(
            ptype=_PUBLISH_TYPE_SOLICITED,
            stype=_SUBSCRIBE_TYPE_ACTIVE,
            encr_type=self.ENCR_TYPE_PASSPHRASE,
            use_peer_id=True)

    def test_ib_solicited_active_passphrase_any(self):
        """Data-path: in-band, solicited/active, passphrase, any peer

    Verifies end-to-end discovery + data-path creation.
    """
        self.run_ib_data_path_test(
            ptype=_PUBLISH_TYPE_SOLICITED,
            stype=_SUBSCRIBE_TYPE_ACTIVE,
            encr_type=self.ENCR_TYPE_PASSPHRASE,
            use_peer_id=False)

    def test_ib_solicited_active_pmk_specific(self):
        """Data-path: in-band, solicited/active, PMK, specific peer

    Verifies end-to-end discovery + data-path creation.
    """
        self.run_ib_data_path_test(
            ptype=_PUBLISH_TYPE_SOLICITED,
            stype=_SUBSCRIBE_TYPE_ACTIVE,
            encr_type=self.ENCR_TYPE_PMK,
            use_peer_id=True)

    def test_ib_solicited_active_pmk_any(self):
        """Data-path: in-band, solicited/active, PMK, any peer

    Verifies end-to-end discovery + data-path creation.
    """
        self.run_ib_data_path_test(
            ptype=_PUBLISH_TYPE_SOLICITED,
            stype=_SUBSCRIBE_TYPE_ACTIVE,
            encr_type=self.ENCR_TYPE_PMK,
            use_peer_id=False)


    #######################################
    # Positive In-Band (IB) with a publish session running on the subscriber
    # tests key:
    #
    # names is: test_ib_extra_pub_<same|diff>_<pub_type>_<sub_type>
    #                                          _<encr_type>_<peer_spec>
    # where:
    #
    # same|diff: Whether the extra publish session (on the subscriber) is the same
    #            or different from the primary session.
    # pub_type: Type of publish discovery session: unsolicited or solicited.
    # sub_type: Type of subscribe discovery session: passive or active.
    # encr_type: Encryption type: open, passphrase
    # peer_spec: Peer specification method: any or specific
    #
    # Note: In-Band means using Wi-Fi Aware for discovery and referring to the
    # peer using the Aware-provided peer handle (as opposed to a MAC address).
    #######################################

    def test_ib_extra_pub_same_unsolicited_passive_open_specific(self):
        """Data-path: in-band, unsolicited/passive, open encryption.
                    specific peer.

        Configuration contains a publisher (for the same service)
        running on *both* devices.

        Verifies end-to-end discovery + data-path creation.
        """
        self.run_ib_data_path_test(
            ptype=_PUBLISH_TYPE_UNSOLICITED,
            stype=_SUBSCRIBE_TYPE_PASSIVE,
            encr_type=self.ENCR_TYPE_OPEN,
            use_peer_id=True,
            pub_on_both=True,
            pub_on_both_same=True)

    def test_ib_extra_pub_same_unsolicited_passive_open_any(self):
        """Data-path: in-band, unsolicited/passive, open encryption.
                    any peer.

        Configuration contains a publisher (for the same service) running on
        *both* devices.

        Verifies end-to-end discovery + data-path creation.
        """
        self.run_ib_data_path_test(
            ptype=_PUBLISH_TYPE_UNSOLICITED,
            stype=_SUBSCRIBE_TYPE_PASSIVE,
            encr_type=self.ENCR_TYPE_OPEN,
            use_peer_id=False,
            pub_on_both=True,
            pub_on_both_same=True)

    def test_ib_extra_pub_diff_unsolicited_passive_open_specific(self):
        """Data-path: in-band, unsolicited/passive, open encryption.
                    specific peer.

        Configuration contains a publisher (for a different service) running on
        *both* devices.

        Verifies end-to-end discovery + data-path creation.
        """
        self.run_ib_data_path_test(
            ptype=_PUBLISH_TYPE_UNSOLICITED,
            stype=_SUBSCRIBE_TYPE_PASSIVE,
            encr_type=self.ENCR_TYPE_OPEN,
            use_peer_id=True,
            pub_on_both=True,
            pub_on_both_same=False)

    def test_ib_extra_pub_diff_unsolicited_passive_open_any(self):
        """Data-path: in-band, unsolicited/passive, open encryption, any peer.

        Configuration contains a publisher (for a different service) running on
        *both* devices.

        Verifies end-to-end discovery + data-path creation.
        """
        self.run_ib_data_path_test(
            ptype=_PUBLISH_TYPE_UNSOLICITED,
            stype=_SUBSCRIBE_TYPE_PASSIVE,
            encr_type=self.ENCR_TYPE_OPEN,
            use_peer_id=False,
            pub_on_both=True,
            pub_on_both_same=False)


if __name__ == '__main__':
    # Take test args
    if '--' in sys.argv:
        index = sys.argv.index('--')
        sys.argv = sys.argv[:1] + sys.argv[index + 1:]

    test_runner.main()
