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
"""Wi-Fi Aware Datapath test reimplemented in Mobly."""
import base64
import logging
import sys
import time
import re

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

_NETWORK_CB_LINK_PROPERTIES_CHANGED = constants.NetworkCbName.ON_PROPERTIES_CHANGED
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
    PASSPHRASE_MAX = "012345678901234567890123456789012345678901234567890123456789012"
    PMK = "ODU0YjE3YzdmNDJiNWI4NTQ2NDJjNDI3M2VkZTQyZGU="
    PASSPHRASE2 = "This is some random passphrase - very very secure - but diff!!"
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
        network_specifier_params: constants.WifiAwareNetworkSpecifier | None = None,
        is_accept_any_peer: bool = False,
    ) -> callback_handler_v2.CallbackHandlerV2:
        """Requests and configures a Wi-Fi Aware network connection."""
        network_specifier_parcel = (
            ad.wifi_aware_snippet.wifiAwareCreateNetworkSpecifier(
                discovery_session,
                peer,
                is_accept_any_peer,
                network_specifier_params.to_dict() if network_specifier_params else None,
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
            # Subscriber: send message to peer (Publisher - so it knows our address)
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
        logging.info("msg: %s,received_message: %s",msg, received_message)
        asserts.assert_equal(
            received_message,
            msg,
            f'{receiver_ad} received message mismatched.Failure:Expected {msg} but got '
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
             ptype, stype, use_peer_id, pub_on_both=pub_on_both, pub_on_both_same=pub_on_both_same)
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
        pub_accept_handler = p_dut.wifi_aware_snippet.connectivityServerSocketAccept()
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
        p_network_callback_event = p_req_key.waitAndGet(
                event_name=constants.NetworkCbEventName.NETWORK_CALLBACK,
                timeout=_DEFAULT_TIMEOUT,
            )
        p_callback_name = p_network_callback_event.data[_CALLBACK_NAME]
        s_network_callback_event = s_req_key.waitAndGet(
                event_name=constants.NetworkCbEventName.NETWORK_CALLBACK,
                timeout=_DEFAULT_TIMEOUT,
            )
        s_callback_name = s_network_callback_event.data[_CALLBACK_NAME]


        if expect_failure:
            asserts.assert_equal(
                p_callback_name, constants.NetworkCbName.ON_UNAVAILABLE,
                f'{p_dut} failed to request the network, got callback'
                f' {p_callback_name}.'
                )
            asserts.assert_equal(
                s_callback_name, constants.NetworkCbName.ON_UNAVAILABLE,
                f'{s_dut} failed to request the network, got callback'
                f' {s_callback_name}.'
                )
        else:
            # # Publisher & Subscriber: wait for network formation
            asserts.assert_equal(
                p_callback_name, constants.NetworkCbName.ON_CAPABILITIES_CHANGED,
                f'{p_dut} succeeded to request the network, got callback'
                f' {p_callback_name}.'
                )
            network = p_network_callback_event.data[
                constants.NetworkCbEventKey.NETWORK]
            network_capabilities = p_network_callback_event.data[
                constants.NetworkCbEventKey.NETWORK_CAPABILITIES]
            asserts.assert_true(
                network and network_capabilities,
                f'{p_dut} received a null Network or NetworkCapabilities!?.'
            )
            asserts.assert_equal(
                s_callback_name, constants.NetworkCbName.ON_CAPABILITIES_CHANGED,
                f'{s_dut} succeeded to request the network, got callback'
                f' {s_callback_name}.'
                )
            network = s_network_callback_event.data[
                constants.NetworkCbEventKey.NETWORK]
            network_capabilities = s_network_callback_event.data[
                constants.NetworkCbEventKey.NETWORK_CAPABILITIES]
            asserts.assert_true(
                network and network_capabilities,
                f'{s_dut} received a null Network or NetworkCapabilities!?.'
            )
            p_net_event_nc = p_network_callback_event.data
            s_net_event_nc = s_network_callback_event.data

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
            p_network_callback_event.data,
            s_network_callback_event.data,
            encr_type == self.ENCR_TYPE_OPEN,
            port = pub_local_port,
            transport_protocol = transport_protocol)

        p_network_callback_LINK = p_req_key.waitAndGet(
                event_name=constants.NetworkCbEventName.NETWORK_CALLBACK,
                timeout=_DEFAULT_TIMEOUT,
            )
        asserts.assert_equal(
                p_network_callback_LINK.data[_CALLBACK_NAME],
                _NETWORK_CB_LINK_PROPERTIES_CHANGED,
                f'{p_dut} succeeded to request the LinkPropertiesChanged,'+
                ' got callback'
                f' {p_network_callback_LINK.data[_CALLBACK_NAME]}.'
                )

        s_network_callback_LINK = s_req_key.waitAndGet(
                event_name=constants.NetworkCbEventName.NETWORK_CALLBACK,
                timeout=_DEFAULT_TIMEOUT,
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


    def attach_with_identity(self, dut):
        """Start an Aware session (attach) and wait for confirmation and
        identity information (mac address).

        Args:
            dut: Device under test
        Returns:
            id: Aware session ID.
        mac: Discovery MAC address of this device.
        """
        handler = dut.wifi_aware_snippet.wifiAwareAttached(True)
        id = handler.waitAndGet(constants.AttachCallBackMethodType.ATTACHED)
        even = handler.waitAndGet(constants.AttachCallBackMethodType.ID_CHANGED)
        mac = even.data["mac"]
        return id.callback_id, mac

    def request_oob_network(
        self,
        ad: android_device.AndroidDevice,
        aware_session : str,
        role: int,
        mac: str,
        passphrase:str,
        pmk:str,
        net_work_request_id: str,
    ) -> callback_handler_v2.CallbackHandlerV2:
        """Requests a Wi-Fi Aware network."""
        network_specifier_parcel = (
            ad.wifi_aware_snippet.createNetworkSpecifierOob(
              aware_session, role, mac, passphrase, pmk)
        )
        logging.info("network_specifier_parcel: %s", network_specifier_parcel)
        network_request_dict = constants.NetworkRequest(
            transport_type=constants.NetworkCapabilities.Transport.TRANSPORT_WIFI_AWARE,
            network_specifier_parcel=network_specifier_parcel["result"],
        ).to_dict()
        logging.info("network_request_dict: %s", network_request_dict)
        return ad.wifi_aware_snippet.connectivityRequestNetwork(
            net_work_request_id, network_request_dict, _REQUEST_NETWORK_TIMEOUT_MS
        )


    def run_oob_data_path_test(self,
                               encr_type,
                               use_peer_id,
                               setup_discovery_sessions=False,
                               expect_failure=False):
        """Runs the out-of-band data-path tests.

        Args:
        encr_type: Encryption type, one of ENCR_TYPE_*
        setup_discovery_sessions: If True also set up a (spurious) discovery
            session (pub on both sides, sub on Responder side). Validates a corner
            case.
        expect_failure: If True then don't expect NDP formation, otherwise expect
                        NDP setup to succeed.
        """
        init_dut = self.ads[0]
        init_dut.pretty_name = "Initiator"
        resp_dut = self.ads[1]
        resp_dut.pretty_name = "Responder"
        init_id, init_mac = self.attach_with_identity(init_dut)
        resp_id, resp_mac = self.attach_with_identity(resp_dut)
        time.sleep(self.WAIT_FOR_CLUSTER)
        if setup_discovery_sessions:
            pconfig = autils.create_discovery_config(
                self.SERVICE_NAME, p_type =_PUBLISH_TYPE_UNSOLICITED,
                s_type = None)
            init_disc_id = init_dut.wifi_aware_snippet.wifiAwarePublish(
                init_id, pconfig
                    )
            logging.info('Created the DUT publish session %s', init_disc_id)
            init_discovery = init_disc_id.waitAndGet(
                constants.DiscoverySessionCallbackMethodType.DISCOVER_RESULT)
            init_name = init_discovery.data[_CALLBACK_NAME]
            asserts.assert_equal(
                constants.DiscoverySessionCallbackMethodType.PUBLISH_STARTED,
                init_name,
                f'{init_dut} DUT publish failed, got callback: {init_name}.',
                )

            resp_disc_id = resp_dut.wifi_aware_snippet.wifiAwarePublish(
                resp_id, pconfig
                    )
            logging.info('Created the DUT publish session %s', resp_disc_id)
            resp_discovery = resp_disc_id.waitAndGet(
                constants.DiscoverySessionCallbackMethodType.DISCOVER_RESULT)
            resp_name = resp_discovery.data[_CALLBACK_NAME]
            asserts.assert_equal(
                constants.DiscoverySessionCallbackMethodType.PUBLISH_STARTED,
                resp_name,
                f'{resp_dut} DUT publish failed, got callback: {resp_name}.',
                )
            sconfig = autils.create_discovery_config(
                self.SERVICE_NAME, p_type =None, s_type =_SUBSCRIBE_TYPE_PASSIVE)
            resp_disc_id = resp_dut.wifi_aware_snippet.wifiAwareSubscribe(
                resp_id, sconfig
                    )
            resp_dut.log.info('Created the DUT subscribe session.: %s',
            resp_disc_id)
            resp_discovery = resp_disc_id.waitAndGet(
                    constants.DiscoverySessionCallbackMethodType.DISCOVER_RESULT,
                    timeout=_DEFAULT_TIMEOUT)
            resp_name = resp_discovery.data[_CALLBACK_NAME]
            asserts.assert_equal(
                constants.DiscoverySessionCallbackMethodType.SUBSCRIBE_STARTED,
                resp_name,
                f'{resp_dut} DUT subscribe failed, got callback: {resp_name}.',
                )
            discovered_event = resp_disc_id.waitAndGet(
            constants.DiscoverySessionCallbackMethodType.SERVICE_DISCOVERED)
        passphrase = None
        pmk = None
        if encr_type == self.ENCR_TYPE_PASSPHRASE:
            passphrase = self.PASSPHRASE
        elif encr_type == self.ENCR_TYPE_PMK:
            pmk = self.PMK

        # Responder: request network
        init_dut_accept_handler =(
            init_dut.wifi_aware_snippet.connectivityServerSocketAccept())
        network_id = init_dut_accept_handler.callback_id
        init_local_port = init_dut_accept_handler.ret_value
        resp_req_key = self.request_oob_network(
            resp_dut,
            resp_id,
            _DATA_PATH_RESPONDER,
            init_mac if use_peer_id else None,
            passphrase,
            pmk,
            network_id
            )

        # Initiator: request network
        init_req_key = self.request_oob_network(
            init_dut,
            init_id,
            _DATA_PATH_INITIATOR,
            resp_mac,
            passphrase,
            pmk,
            network_id
            )
        init_callback_event = init_req_key.waitAndGet(
                event_name=constants.NetworkCbEventName.NETWORK_CALLBACK,
                timeout=_DEFAULT_TIMEOUT,
            )
        init_name = init_callback_event.data[_CALLBACK_NAME]
        resp_callback_event = resp_req_key.waitAndGet(
                event_name=constants.NetworkCbEventName.NETWORK_CALLBACK,
                timeout=_DEFAULT_TIMEOUT,
            )
        resp_name = resp_callback_event.data[_CALLBACK_NAME]

        if expect_failure:
            asserts.assert_equal(
                init_name, constants.NetworkCbName.ON_UNAVAILABLE,
                f'{init_dut} failed to request the network, got callback'
                f' {init_name}.'
                )
            asserts.assert_equal(
                resp_name, constants.NetworkCbName.ON_UNAVAILABLE,
                f'{resp_dut} failed to request the network, got callback'
                f' {resp_name}.'
                )
        else:
            # # Publisher & Subscriber: wait for network formation
            asserts.assert_equal(
                init_name, constants.NetworkCbName.ON_CAPABILITIES_CHANGED,
                f'{init_dut} succeeded to request the network, got callback'
                f' {init_name}.'
                )
            network = init_callback_event.data[
                constants.NetworkCbEventKey.NETWORK]
            network_capabilities = init_callback_event.data[
                constants.NetworkCbEventKey.NETWORK_CAPABILITIES]
            asserts.assert_true(
                network and network_capabilities,
                f'{init_dut} received a null Network or NetworkCapabilities!?.'
            )
            asserts.assert_equal(
                resp_name, constants.NetworkCbName.ON_CAPABILITIES_CHANGED,
                f'{resp_dut} succeeded to request the network, got callback'
                f' {resp_name}.'
                )
            network = resp_callback_event.data[
                constants.NetworkCbEventKey.NETWORK]
            network_capabilities = resp_callback_event.data[
                constants.NetworkCbEventKey.NETWORK_CAPABILITIES]
            asserts.assert_true(
                network and network_capabilities,
                f'{resp_dut} received a null Network or NetworkCapabilities!?.'
            )
            init_net_event_nc = init_callback_event.data
            resp_net_event_nc = resp_callback_event.data
            # validate no leak of information
            asserts.assert_false(
                _NETWORK_CB_KEY_NETWORK_SPECIFIER in init_net_event_nc,
                "Network specifier leak!")
            asserts.assert_false(
                _NETWORK_CB_KEY_NETWORK_SPECIFIER in resp_net_event_nc,
                "Network specifier leak!")

            #To get ipv6 ip address
            resp_ipv6= init_net_event_nc[constants.NetworkCbName.NET_CAP_IPV6]
            init_ipv6 = resp_net_event_nc[constants.NetworkCbName.NET_CAP_IPV6]
            # note that Pub <-> Sub since IPv6 are of peer's!
            init_callback_LINK = init_req_key.waitAndGet(
                    event_name=constants.NetworkCbEventName.NETWORK_CALLBACK,
                    timeout=_DEFAULT_TIMEOUT,
                )
            asserts.assert_equal(
                    init_callback_LINK.data[_CALLBACK_NAME],
                    _NETWORK_CB_LINK_PROPERTIES_CHANGED,
                    f'{init_dut} succeeded to request the'+
                    ' LinkPropertiesChanged, got callback'
                    f' {init_callback_LINK.data[_CALLBACK_NAME]}.'
                    )

            resp_callback_LINK = resp_req_key.waitAndGet(
                    event_name=constants.NetworkCbEventName.NETWORK_CALLBACK,
                    timeout=_DEFAULT_TIMEOUT,
                )
            asserts.assert_equal(
                    resp_callback_LINK.data[_CALLBACK_NAME],
                    _NETWORK_CB_LINK_PROPERTIES_CHANGED,
                    f'{resp_dut} succeeded to request the'+
                    'LinkPropertiesChanged, got callback'
                    f' {resp_callback_LINK.data[_CALLBACK_NAME]}.'
                    )
            init_aware_if = init_callback_LINK.data[
                _NETWORK_CB_KEY_INTERFACE_NAME]
            resp_aware_if = resp_callback_LINK.data[
                _NETWORK_CB_KEY_INTERFACE_NAME]

            logging.info("Interface names: p=%s, s=%s", init_aware_if,
                        resp_aware_if)
            logging.info("Interface addresses (IPv6): p=%s, s=%s", init_ipv6,
                        resp_ipv6)
            self._establish_socket_and_send_msg(
                pub_accept_handler=init_dut_accept_handler,
                network_id=network_id,
                pub_local_port=init_local_port
                )

            # terminate sessions and wait for ON_LOST callbacks
            init_dut.wifi_aware_snippet.wifiAwareDetach(init_id)
            resp_dut.wifi_aware_snippet.wifiAwareDetach(resp_id)
            time.sleep(self.WAIT_FOR_CLUSTER)
            init_callback_lost = init_req_key.waitAndGet(
                    event_name=constants.NetworkCbEventName.NETWORK_CB_LOST,
                    timeout=_DEFAULT_TIMEOUT,
                )
            resp_callback_lost = resp_req_key.waitAndGet(
                    event_name=constants.NetworkCbEventName.NETWORK_CB_LOST,
                    timeout=_DEFAULT_TIMEOUT,
                )
        init_dut.wifi_aware_snippet.connectivityUnregisterNetwork(init_callback_event.callback_id)
        resp_dut.wifi_aware_snippet.connectivityUnregisterNetwork(resp_callback_event.callback_id)

    def wait_for_request_responses(self, dut, req_keys, aware_ifs, aware_ipv6):
        """Wait for network request confirmation for all request keys.

        Args:
            dut: Device under test
            req_keys: (in) A list of the network requests
            aware_ifs: (out) A list into which to append the network interface
            aware_ipv6: (out) A list into which to append the network ipv6
            address
        """
        network_callback_event = req_keys.waitAndGet(
            event_name=constants.NetworkCbEventName.NETWORK_CALLBACK,
            timeout=_DEFAULT_TIMEOUT,
            )
            # network_callback_event=req_keys[0]
        if network_callback_event.data[_CALLBACK_NAME] == _NETWORK_CB_LINK_PROPERTIES_CHANGED:
            if network_callback_event.callback_id:
                aware_ifs.append(network_callback_event.data["interfaceName"])
            else:
                logging.info(
                    "Received an unexpected connectivity, the revoked "+
                    "network request probably went through -- %s", network_callback_event)
        elif network_callback_event.data[_CALLBACK_NAME] == (
            constants.NetworkCbName.ON_CAPABILITIES_CHANGED) :
            if network_callback_event.callback_id:
                aware_ipv6.append(network_callback_event.data[
                    constants.NetworkCbName.NET_CAP_IPV6])
            else:
                logging.info(
                    "Received an unexpected connectivity, the revoked "+
                    "network request probably went through -- %s",
                    network_callback_event)
                asserts.assert_false(
                     _NETWORK_CB_KEY_NETWORK_SPECIFIER in
                    network_callback_event.data,
                     "Network specifier leak!")

    def get_ipv6_addr(self, device, interface):
        """Get the IPv6 address of the specified interface. Uses ifconfig and parses
        its output. Returns a None if the interface does not have an IPv6 address
        (indicating it is not UP).

        Args:
            device: Device on which to query the interface IPv6 address.
            interface: Name of the interface for which to obtain the IPv6 address.
        """
        out = device.adb.shell("ifconfig %s" % interface)
        res = re.search(r"inet6 addr: (.*?)/64", str(out))
        if not res:
            return None
        return res.group(1)

    def run_mismatched_oob_data_path_test(self,
                                          init_mismatch_mac=False,
                                          resp_mismatch_mac=False,
                                          init_encr_type=ENCR_TYPE_OPEN,
                                          resp_encr_type=ENCR_TYPE_OPEN):
        """Runs the negative out-of-band data-path tests: mismatched information
        between Responder and Initiator.

        Args:
            init_mismatch_mac: True to mismatch the Initiator MAC address
            resp_mismatch_mac: True to mismatch the Responder MAC address
            init_encr_type: Encryption type of Initiator - ENCR_TYPE_*
            resp_encr_type: Encryption type of Responder - ENCR_TYPE_*
        """

        init_dut = self.ads[0]
        init_dut.pretty_name = "Initiator"
        resp_dut = self.ads[1]
        resp_dut.pretty_name = "Responder"
        init_handler = init_dut.wifi_aware_snippet.wifiAwareAttached(True)
        resp_handler = resp_dut.wifi_aware_snippet.wifiAwareAttached(True)
        init_id, init_mac = self.attach_with_identity(init_dut)
        resp_id, resp_mac = self.attach_with_identity(resp_dut)
        if init_mismatch_mac:  # assumes legit ones don't start with "00"
            init_mac = "00" + init_mac[2:]
        if resp_mismatch_mac:
            resp_mac = "00" + resp_mac[2:]

        # wait for devices to synchronize with each other - there are no other
        # mechanisms to make sure this happens for OOB discovery (except retrying
        # to execute the data-path request)
        time.sleep(self.WAIT_FOR_CLUSTER)

        # set up separate keys: even if types are the same we want a mismatch
        init_passphrase = None
        init_pmk = None
        if init_encr_type == self.ENCR_TYPE_PASSPHRASE:
            init_passphrase = self.PASSPHRASE
        elif init_encr_type == self.ENCR_TYPE_PMK:
            init_pmk = self.PMK
        resp_passphrase = None
        resp_pmk = None
        if resp_encr_type == self.ENCR_TYPE_PASSPHRASE:
            resp_passphrase = self.PASSPHRASE2
        elif resp_encr_type == self.ENCR_TYPE_PMK:
            resp_pmk = self.PMK2

        # Responder: request network
        init_dut_accept_handler = init_dut.wifi_aware_snippet.connectivityServerSocketAccept()
        network_id = init_dut_accept_handler.callback_id
        init_local_port = init_dut_accept_handler.ret_value
        resp_req_key = self.request_oob_network(
            resp_dut,
            resp_id,
            _DATA_PATH_RESPONDER,
            init_mac,
            resp_passphrase,
            resp_pmk,
            network_id
            )

        # Initiator: request network
        init_req_key = self.request_oob_network(
            init_dut,
            init_id,
            _DATA_PATH_INITIATOR,
            resp_mac,
            init_passphrase,
            resp_pmk,
            network_id
            )
        # Initiator & Responder:
        # - expect unavailable on the Initiator party if the
        #   Initiator and Responder with mac or encryption mismatch
        # - For responder:
        #   - If mac mismatch, responder will keep waiting ...
        #   - If encryption mismatch, responder expect unavailable
        p_network_callback_event = init_req_key.waitAndGet(
                event_name=constants.NetworkCbEventName.NETWORK_CALLBACK,
                timeout=_DEFAULT_TIMEOUT,
            )
        p_callback_name = p_network_callback_event.data[_CALLBACK_NAME]
        asserts.assert_equal(
            p_callback_name, constants.NetworkCbName.ON_UNAVAILABLE,
            f'{init_dut} failed to request the network, got callback'
            f' {p_callback_name}.'
            )
        time.sleep(self.EVENT_NDP_TIMEOUT)
        if init_mismatch_mac or resp_mismatch_mac:
            autils.callback_no_response(
                resp_handler, constants.NetworkCbEventName.NETWORK_CALLBACK,
                10, True
            )
        else:
            s_network_callback_event = resp_req_key.waitAndGet(
                event_name=constants.NetworkCbEventName.NETWORK_CALLBACK,
                timeout=_DEFAULT_TIMEOUT,
            )
            s_callback_name = s_network_callback_event.data[_CALLBACK_NAME]
            asserts.assert_equal(
                s_callback_name, constants.NetworkCbName.ON_UNAVAILABLE,
                f'{resp_dut} failed to request the network, got callback'
                f' {s_callback_name}.'
                )
        init_dut.wifi_aware_snippet.connectivityUnregisterNetwork(network_id)
        resp_dut.wifi_aware_snippet.connectivityUnregisterNetwork(network_id)

    def get_network_specifier(self, dut, id, dev_type, peer_mac, sec, net_work_request_id):
        """Create a network specifier for the device based on the security
        configuration.

        Args:
        dut: device
        id: session ID
        dev_type: device type - Initiator or Responder
        peer_mac: the discovery MAC address of the peer
        sec: security configuration
        """
        if sec is None:
            network_specifier_parcel = (
                dut.wifi_aware_snippet.createNetworkSpecifierOob(
                id, dev_type, peer_mac, None, None)
                )
        if isinstance(sec, str):
            network_specifier_parcel = (
                dut.wifi_aware_snippet.createNetworkSpecifierOob(
                id, dev_type, peer_mac, sec, None)
                )
        else:
            network_specifier_parcel = (
                dut.wifi_aware_snippet.createNetworkSpecifierOob(
                id, dev_type, peer_mac, None, sec)
                )
        network_request_dict = constants.NetworkRequest(
            transport_type=constants.NetworkCapabilities.Transport.TRANSPORT_WIFI_AWARE,
            network_specifier_parcel=network_specifier_parcel["result"],
        ).to_dict()
        return dut.wifi_aware_snippet.connectivityRequestNetwork(
            net_work_request_id, network_request_dict, _REQUEST_NETWORK_TIMEOUT_MS
        )

    def run_mix_ib_oob(self, same_request, ib_first, inits_on_same_dut):
        """Validate that multiple network requests issued using both in-band
        and out-of-band discovery behave as expected.

        The same_request parameter controls whether identical single NDP is
        expected, if True, or whether multiple NDPs on different NDIs are
        expected, if False.

        Args:
            same_request: Issue canonically identical requests (same NMI peer,
            same passphrase) if True, if False use different passphrases.
            ib_first: If True then the in-band network is requested first,
            otherwise (if False) then the out-of-band network is requested first.
            inits_on_same_dut: If True then the Initiators are run on the same
            device, otherwise (if False) then the Initiators are run on
            different devices. Note that Subscribe == Initiator.
        """
        p_dut = self.ads[0]
        s_dut = self.ads[1]
        if not same_request:
            asserts.skip_if(
                autils.get_aware_capabilities(p_dut)[
                    _CAP_MAX_NDI_INTERFACES] < 2 or
                autils.get_aware_capabilities(s_dut)[
                    _CAP_MAX_NDI_INTERFACES] < 2,
                "DUTs do not support enough NDIs")

        (p_dut, s_dut, p_id, s_id, p_disc_id, s_disc_id, peer_id_on_sub,
         peer_id_on_pub) = self.set_up_discovery(_PUBLISH_TYPE_UNSOLICITED,
                                                 _SUBSCRIBE_TYPE_PASSIVE, True)
        p_id2, p_mac = self.attach_with_identity(p_dut)
        s_id2, s_mac = self.attach_with_identity(s_dut)
        time.sleep(self.WAIT_FOR_CLUSTER)

        if inits_on_same_dut:
            resp_dut = p_dut
            resp_id = p_id2
            resp_mac = p_mac
            init_dut = s_dut
            init_id = s_id2
            init_mac = s_mac

        else:
            resp_dut = s_dut
            resp_id = s_id2
            resp_mac = s_mac
            init_dut = p_dut
            init_id = p_id2
            init_mac = p_mac

        passphrase = None if same_request else self.PASSPHRASE
        pub_accept_handler = (
            p_dut.wifi_aware_snippet.connectivityServerSocketAccept())
        network_id = pub_accept_handler.callback_id
        self.publish_session = p_disc_id.callback_id
        self.subscribe_session = s_disc_id.callback_id

        if ib_first:
            # request in-band network (to completion)
            p_req_key = self._request_network(
                ad=p_dut,
                discovery_session=self.publish_session,
                peer=peer_id_on_pub,
                net_work_request_id=network_id
                )
            s_req_key = self._request_network(
                ad=s_dut,
                discovery_session=self.subscribe_session,
                peer=peer_id_on_sub,
                net_work_request_id=network_id
                )
            p_net_event_nc = autils.wait_for_network(
                ad=p_dut,
                request_network_cb_handler=p_req_key,
                expected_channel=None,
                )
            s_net_event_nc = autils.wait_for_network(
                ad=s_dut,
                request_network_cb_handler=s_req_key,
                expected_channel=None,
                )
            p_net_event_lp = autils.wait_for_link(
                ad=p_dut,
                request_network_cb_handler=p_req_key,
                )
            s_net_event_lp = autils.wait_for_link(
                ad=s_dut,
                request_network_cb_handler=s_req_key,
                )
            # validate no leak of information
            asserts.assert_false(
                _NETWORK_CB_KEY_NETWORK_SPECIFIER in p_net_event_nc.data,
                "Network specifier leak!")
            asserts.assert_false(
                _NETWORK_CB_KEY_NETWORK_SPECIFIER in s_net_event_nc.data,
                "Network specifier leak!")

        # request out-of-band network
        init_dut_accept_handler = (
            init_dut.wifi_aware_snippet.connectivityServerSocketAccept())
        network_id = init_dut_accept_handler.callback_id
        resp_req_key = self.request_oob_network(
            resp_dut,
            resp_id,
            _DATA_PATH_RESPONDER,
            init_mac,
            passphrase,
            None,
            network_id
            )
        init_req_key = self.request_oob_network(
            init_dut,
            init_id,
            _DATA_PATH_INITIATOR,
            resp_mac,
            passphrase,
            None,
            network_id
            )
        time.sleep(5)
        # Publisher & Subscriber: wait for network formation
        resp_net_event_nc = autils.wait_for_network(
            ad=resp_dut,
            request_network_cb_handler=resp_req_key,
            expected_channel=None,
            )
        init_net_event_nc = autils.wait_for_network(
            ad=init_dut,
            request_network_cb_handler=init_req_key,
            expected_channel=None,
            )
        resp_net_event_lp = autils.wait_for_link(
            ad=resp_dut,
            request_network_cb_handler=resp_req_key,
            )
        init_net_event_lp = autils.wait_for_link(
            ad=init_dut,
            request_network_cb_handler=init_req_key,
            )
        # validate no leak of information
        asserts.assert_false(
            _NETWORK_CB_KEY_NETWORK_SPECIFIER in resp_net_event_nc.data,
            "Network specifier leak!")
        asserts.assert_false(
            _NETWORK_CB_KEY_NETWORK_SPECIFIER in init_net_event_nc.data,
            "Network specifier leak!")

        if not ib_first:
            # request in-band network (to completion)
            p_req_key = self._request_network(
                ad=p_dut,
                discovery_session=self.publish_session,
                peer=peer_id_on_pub,
                net_work_request_id=network_id
                )
            s_req_key = self._request_network(
                ad=s_dut,
                discovery_session=self.subscribe_session,
                peer=peer_id_on_sub,
                net_work_request_id=network_id
                )
            # Publisher & Subscriber: wait for network formation
            p_net_event_nc = autils.wait_for_network(
                ad=p_dut,
                request_network_cb_handler=p_req_key,
                expected_channel=None,
                )
            s_net_event_nc = autils.wait_for_network(
                ad=s_dut,
                request_network_cb_handler=s_req_key,
                expected_channel=None,
                )
            p_net_event_lp = autils.wait_for_link(
                ad=p_dut,
                request_network_cb_handler=p_req_key,
                )
            s_net_event_lp = autils.wait_for_link(
                ad=s_dut,
                request_network_cb_handler=s_req_key,
                )
            # validate no leak of information
            asserts.assert_false(
                _NETWORK_CB_KEY_NETWORK_SPECIFIER in p_net_event_nc.data,
                "Network specifier leak!")
            asserts.assert_false(
                _NETWORK_CB_KEY_NETWORK_SPECIFIER in s_net_event_nc.data,
                "Network specifier leak!")

        # note that Init <-> Resp & Pub <--> Sub since IPv6 are of peer's!
        init_ipv6 = resp_net_event_nc.data[constants.NetworkCbName.NET_CAP_IPV6]
        resp_ipv6 = init_net_event_nc.data[constants.NetworkCbName.NET_CAP_IPV6]
        pub_ipv6 = s_net_event_nc.data[constants.NetworkCbName.NET_CAP_IPV6]
        sub_ipv6 = p_net_event_nc.data[constants.NetworkCbName.NET_CAP_IPV6]

        # extract net info
        pub_interface = p_net_event_lp.data[
            _NETWORK_CB_KEY_INTERFACE_NAME]
        sub_interface = s_net_event_lp.data[
            _NETWORK_CB_KEY_INTERFACE_NAME]
        resp_interface = resp_net_event_lp.data[
            _NETWORK_CB_KEY_INTERFACE_NAME]
        init_interface = init_net_event_lp.data[
            _NETWORK_CB_KEY_INTERFACE_NAME]
        logging.info(
            "Interface names: Pub=%s, Sub=%s, Resp=%s, Init=%s",
            pub_interface, sub_interface, resp_interface,
            init_interface
            )
        logging.info(
            "Interface addresses (IPv6): Pub=%s, Sub=%s, Resp=%s, Init=%s",
            pub_ipv6, sub_ipv6, resp_ipv6, init_ipv6)

        # validate NDP/NDI conditions (using interface names & ipv6)
        if same_request:
            asserts.assert_equal(
                pub_interface, resp_interface if inits_on_same_dut else
                init_interface, "NDP interfaces don't match on Pub/other")
            asserts.assert_equal(
                sub_interface, init_interface if inits_on_same_dut else
                resp_interface, "NDP interfaces don't match on Sub/other")

            asserts.assert_equal(
                pub_ipv6, resp_ipv6 if inits_on_same_dut else init_ipv6,
                "NDP IPv6 don't match on Pub/other")
            asserts.assert_equal(
                sub_ipv6, init_ipv6 if inits_on_same_dut else resp_ipv6,
                "NDP IPv6 don't match on Sub/other")
        else:
            asserts.assert_false(
                pub_interface == (
                    resp_interface if inits_on_same_dut else init_interface),
                "NDP interfaces match on Pub/other")
            asserts.assert_false(
                sub_interface == (
                    init_interface if inits_on_same_dut else resp_interface),
                "NDP interfaces match on Sub/other")

            asserts.assert_false(
                pub_ipv6 == (resp_ipv6 if inits_on_same_dut else init_ipv6),
                "NDP IPv6 match on Pub/other")
            asserts.assert_false(
                sub_ipv6 == (init_ipv6 if inits_on_same_dut else resp_ipv6),
                "NDP IPv6 match on Sub/other")

        # release requests
        init_dut.wifi_aware_snippet.connectivityUnregisterNetwork(
            init_net_event_nc.callback_id)
        resp_dut.wifi_aware_snippet.connectivityUnregisterNetwork(
            resp_net_event_nc.callback_id)
        p_dut.wifi_aware_snippet.connectivityUnregisterNetwork(
            p_net_event_nc.callback_id)
        s_dut.wifi_aware_snippet.connectivityUnregisterNetwork(
            s_net_event_nc.callback_id)

    def create_oob_ndp_on_sessions(self,
                                   init_dut,
                                   resp_dut,
                                   init_id,
                                   init_mac,
                                   resp_id,
                                   resp_mac):
        """Create an NDP on top of existing Aware sessions (using OOB discovery)

        Args:
            init_dut: Initiator device
            resp_dut: Responder device
            init_id: Initiator attach session id
            init_mac: Initiator discovery MAC address
            resp_id: Responder attach session id
            resp_mac: Responder discovery MAC address
        Returns:
            init_req_key: Initiator network request
            resp_req_key: Responder network request
            init_aware_if: Initiator Aware data interface
            resp_aware_if: Responder Aware data interface
            init_ipv6: Initiator IPv6 address
            resp_ipv6: Responder IPv6 address
        """
        # Responder: request network
        init_dut_accept_handler = init_dut.wifi_aware_snippet.connectivityServerSocketAccept()
        network_id = init_dut_accept_handler.callback_id
        init_local_port = init_dut_accept_handler.ret_value
        resp_req_key = self.request_oob_network(
            resp_dut,
            resp_id,
            _DATA_PATH_RESPONDER,
            init_mac,
            None,
            None,
            network_id
            )
        # Initiator: request network
        init_req_key = self.request_oob_network(
            init_dut,
            init_id,
            _DATA_PATH_INITIATOR,
            resp_mac,
            None,
            None,
            network_id
            )
        time.sleep(5)
        init_callback_event = init_req_key.waitAndGet(
                event_name=constants.NetworkCbEventName.NETWORK_CALLBACK,
                timeout=_DEFAULT_TIMEOUT,
            )
        init_name = init_callback_event.data[_CALLBACK_NAME]
        resp_callback_event = resp_req_key.waitAndGet(
                event_name=constants.NetworkCbEventName.NETWORK_CALLBACK,
                timeout=_DEFAULT_TIMEOUT,
            )
        resp_name = resp_callback_event.data[_CALLBACK_NAME]
        asserts.assert_equal(
            init_name, constants.NetworkCbName.ON_CAPABILITIES_CHANGED,
            f'{init_dut} succeeded to request the network, got callback'
            f' {init_name}.'
            )
        asserts.assert_equal(
            resp_name, constants.NetworkCbName.ON_CAPABILITIES_CHANGED,
            f'{resp_dut} succeeded to request the network, got callback'
            f' {resp_name}.'
            )
        init_net_event_nc = init_callback_event.data
        resp_net_event_nc = resp_callback_event.data
            # validate no leak of information
        asserts.assert_false(
            _NETWORK_CB_KEY_NETWORK_SPECIFIER in init_net_event_nc,
            "Network specifier leak!")
        asserts.assert_false(
            _NETWORK_CB_KEY_NETWORK_SPECIFIER in resp_net_event_nc,
            "Network specifier leak!")

        #To get ipv6 ip address
        resp_ipv6= init_net_event_nc[constants.NetworkCbName.NET_CAP_IPV6]
        init_ipv6 = resp_net_event_nc[constants.NetworkCbName.NET_CAP_IPV6]
        # note that Pub <-> Sub since IPv6 are of peer's!
        init_callback_LINK = init_req_key.waitAndGet(
            event_name=constants.NetworkCbEventName.NETWORK_CALLBACK,
            timeout=_DEFAULT_TIMEOUT,
            )
        asserts.assert_equal(
            init_callback_LINK.data[_CALLBACK_NAME],
            _NETWORK_CB_LINK_PROPERTIES_CHANGED,
            f'{init_dut} succeeded to request the'+
            ' LinkPropertiesChanged, got callback'
            f' {init_callback_LINK.data[_CALLBACK_NAME]}.'
                )
        resp_callback_LINK = resp_req_key.waitAndGet(
            event_name=constants.NetworkCbEventName.NETWORK_CALLBACK,
            timeout=_DEFAULT_TIMEOUT,
            )
        asserts.assert_equal(
            resp_callback_LINK.data[_CALLBACK_NAME],
            _NETWORK_CB_LINK_PROPERTIES_CHANGED,
            f'{resp_dut} succeeded to request the'+
            'LinkPropertiesChanged, got callback'
            f' {resp_callback_LINK.data[_CALLBACK_NAME]}.'
            )
        init_aware_if = init_callback_LINK.data[
            _NETWORK_CB_KEY_INTERFACE_NAME]
        resp_aware_if = resp_callback_LINK.data[
            _NETWORK_CB_KEY_INTERFACE_NAME]
        return (init_req_key, resp_req_key, init_aware_if, resp_aware_if,
            init_ipv6, resp_ipv6)

    def set_wifi_country_code(self,
                              ad: android_device.AndroidDevice,
                              country_code):
        """Sets the wifi country code on the device.

        Args:
            ad: An AndroidDevice object.
            country_code: 2 letter ISO country code

        Raises:
            An RpcException if unable to set the country code.
        """
        try:
            ad.adb.shell("cmd wifi force-country-code enabled %s" % country_code)
        except Exception as e:
            ad.log.info(f"ADB command execution failed: {e}")

    def create_data_ib_ndp(
        self,
        p_dut: android_device.AndroidDevice,
        s_dut: android_device.AndroidDevice,
        p_config: dict[str, any],
        s_config: dict[str, any]
        ):
        """Create an NDP (using in-band discovery).
        Args:
        p_dut: Device to use as publisher.
        s_dut: Device to use as subscriber.
        p_config: Publish configuration.
        s_config: Subscribe configuration.

        Returns:
        A tuple containing the following:
            - Publisher network capabilities.
            - Subscriber network capabilities.
            - Publisher network interface name.
            - Subscriber network interface name.
            - Publisher IPv6 address.
            - Subscriber IPv6 address.
        """

        (p_id, s_id, p_disc_id, s_disc_id, peer_id_on_sub, peer_id_on_pub) = (
            autils.create_discovery_pair(
                p_dut, s_dut, p_config, s_config, msg_id=9999
                )
        )
        pub_accept_handler = (
            p_dut.wifi_aware_snippet.connectivityServerSocketAccept()
        )
        network_id = pub_accept_handler.callback_id

        # Request network Publisher (responder).
        pub_network_cb_handler = self._request_network(
            ad=p_dut,
            discovery_session=p_disc_id.callback_id,
            peer=peer_id_on_pub,
            net_work_request_id=network_id,
        )

        # Request network for Subscriber (initiator).
        sub_network_cb_handler = self._request_network(
            ad=s_dut,
            discovery_session=s_disc_id.callback_id,
            peer=peer_id_on_sub,
            net_work_request_id=network_id,
        )
        resp_net_event_nc = sub_network_cb_handler.waitAndGet(
                event_name=constants.NetworkCbEventName.NETWORK_CALLBACK,
                timeout=_DEFAULT_TIMEOUT,
            )
        # time.sleep(5)
        init_net_event_nc = pub_network_cb_handler.waitAndGet(
                event_name=constants.NetworkCbEventName.NETWORK_CALLBACK,
                timeout=_DEFAULT_TIMEOUT,
            )
        s_ipv6 = resp_net_event_nc.data[constants.NetworkCbName.NET_CAP_IPV6]
        p_ipv6 = init_net_event_nc.data[constants.NetworkCbName.NET_CAP_IPV6]
        p_network_callback_LINK = pub_network_cb_handler.waitAndGet(
                event_name=constants.NetworkCbEventName.NETWORK_CALLBACK,
                timeout=_DEFAULT_TIMEOUT,
            )
        s_network_callback_LINK = sub_network_cb_handler.waitAndGet(
                event_name=constants.NetworkCbEventName.NETWORK_CALLBACK,
                timeout=_DEFAULT_TIMEOUT,
            )
        s_aware_if = s_network_callback_LINK.data[
            _NETWORK_CB_KEY_INTERFACE_NAME]
        p_aware_if = p_network_callback_LINK.data[
            _NETWORK_CB_KEY_INTERFACE_NAME]
        pub_network_cap = autils.wait_for_network(
            ad=p_dut,
            request_network_cb_handler=pub_network_cb_handler,
            expected_channel=None,
        )
        sub_network_cap = autils.wait_for_network(
            ad=s_dut,
            request_network_cb_handler=sub_network_cb_handler,
            expected_channel=None,
        )

        p_dut.log.info('interfaceName = %s, ipv6=%s', p_aware_if, p_ipv6)

        s_dut.log.info('interfaceName = %s, ipv6=%s', s_aware_if, s_ipv6)
        return (
            pub_network_cap,
            sub_network_cap,
            p_aware_if,
            s_aware_if,
            p_ipv6,
            s_ipv6,
        )

    def run_multiple_regulatory_domains(self, use_ib, init_domain,
                                        resp_domain):
        """Verify that a data-path setup with two conflicting regulatory domains
            works (the result should be run in Channel 6 - but that is not tested).

        Args:
            use_ib: True to use in-band discovery, False to use out-of-band discovery.
            init_domain: The regulatory domain of the Initiator/Subscriber.
            resp_domain: The regulator domain of the Responder/Publisher.
        """
        init_dut = self.ads[0]
        resp_dut = self.ads[1]
        asserts.skip_if(
            not init_dut.is_adb_root or not resp_dut.is_adb_root,
            'Country code toggle needs Android device(s) with root permission',
        )
        self.set_wifi_country_code(init_dut, init_domain)
        self.set_wifi_country_code(resp_dut, resp_domain)
        if use_ib:
            (resp_req_key, init_req_key, resp_aware_if, init_aware_if,
             resp_ipv6, init_ipv6) = self.create_data_ib_ndp(
                 resp_dut, init_dut,
                 autils.create_discovery_config(
                     "GoogleTestXyz", _PUBLISH_TYPE_UNSOLICITED),
                 autils.create_discovery_config(
                     "GoogleTestXyz", _SUBSCRIBE_TYPE_PASSIVE),
                 )
        else:
            init_id, init_mac = self.attach_with_identity(init_dut)
            resp_id, resp_mac = self.attach_with_identity(resp_dut)
            time.sleep(self.WAIT_FOR_CLUSTER)
            (init_req_key, resp_req_key, init_aware_if, resp_aware_if, init_ipv6,
             resp_ipv6) = self.create_oob_ndp_on_sessions(init_dut, resp_dut, init_id,
                                                          init_mac, resp_id, resp_mac)
        logging.info("Interface names: I=%s, R=%s", init_aware_if,
                      resp_aware_if)
        logging.info("Interface addresses (IPv6): I=%s, R=%s", init_ipv6,
                      resp_ipv6)
        pub_accept_handler = (
            init_dut.wifi_aware_snippet.connectivityServerSocketAccept()
        )
        network_id = pub_accept_handler.callback_id
        # clean-up
        resp_dut.wifi_aware_snippet.connectivityUnregisterNetwork(network_id)
        init_dut.wifi_aware_snippet.connectivityUnregisterNetwork(network_id)

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

    ##############################################################

    def test_passphrase_min(self):
        """Data-path: minimum passphrase length

        Use in-band, unsolicited/passive, any peer combination
        """
        self.run_ib_data_path_test(
            ptype=_PUBLISH_TYPE_UNSOLICITED,
            stype=_SUBSCRIBE_TYPE_PASSIVE,
            encr_type=self.ENCR_TYPE_PASSPHRASE,
            use_peer_id=False,
            passphrase_to_use=self.PASSPHRASE_MIN)

    def test_passphrase_max(self):
        """Data-path: maximum passphrase length

        Use in-band, unsolicited/passive, any peer combination
         """
        self.run_ib_data_path_test(
            ptype=_PUBLISH_TYPE_UNSOLICITED,
            stype=_SUBSCRIBE_TYPE_PASSIVE,
            encr_type=self.ENCR_TYPE_PASSPHRASE,
            use_peer_id=False,
            passphrase_to_use=self.PASSPHRASE_MAX)

    def test_negative_mismatch_init_mac(self):
        """Data-path: failure when Initiator MAC address mismatch"""
        self.run_mismatched_oob_data_path_test(
            init_mismatch_mac=True, resp_mismatch_mac=False)

    def test_negative_mismatch_resp_mac(self):
        """Data-path: failure when Responder MAC address mismatch"""
        self.run_mismatched_oob_data_path_test(
            init_mismatch_mac=False, resp_mismatch_mac=True)

    def test_negative_mismatch_passphrase(self):
        """Data-path: failure when passphrases mismatch"""
        self.run_mismatched_oob_data_path_test(
            init_encr_type=self.ENCR_TYPE_PASSPHRASE,
            resp_encr_type=self.ENCR_TYPE_PASSPHRASE)

    def test_negative_mismatch_open_passphrase(self):
        """Data-path:
            failure when initiator is open, and responder passphrase
        """
        self.run_mismatched_oob_data_path_test(
            init_encr_type=self.ENCR_TYPE_OPEN,
            resp_encr_type=self.ENCR_TYPE_PASSPHRASE)

    def test_negative_mismatch_passphrase_open(self):
        """Data-path:
            failure when initiator is passphrase, and responder open
        """
        self.run_mismatched_oob_data_path_test(
            init_encr_type=self.ENCR_TYPE_PASSPHRASE,
            resp_encr_type=self.ENCR_TYPE_OPEN)

    def test_negative_mismatch_pmk(self):
        """Data-path: failure when PMK mismatch"""
        self.run_mismatched_oob_data_path_test(
            init_encr_type=self.ENCR_TYPE_PMK,
            resp_encr_type=self.ENCR_TYPE_PMK)

    def test_negative_mismatch_open_pmk(self):
        """Data-path: failure when initiator is open, and responder PMK"""
        self.run_mismatched_oob_data_path_test(
            init_encr_type=self.ENCR_TYPE_OPEN,
            resp_encr_type=self.ENCR_TYPE_PMK)

    def test_negative_mismatch_pmk_passphrase(self):
        """Data-path: failure when initiator is pmk, and responder passphrase"""
        self.run_mismatched_oob_data_path_test(
            init_encr_type=self.ENCR_TYPE_PMK,
            resp_encr_type=self.ENCR_TYPE_PASSPHRASE)

    def test_negative_mismatch_pmk_open(self):
        """Data-path: failure when initiator is PMK, and responder open"""
        self.run_mismatched_oob_data_path_test(
            init_encr_type=self.ENCR_TYPE_PMK,
            resp_encr_type=self.ENCR_TYPE_OPEN)

    def test_negative_mismatch_passphrase_pmk(self):
        """Data-path: failure when initiator is passphrase, and responder pmk"""
        self.run_mismatched_oob_data_path_test(
            init_encr_type=self.ENCR_TYPE_PASSPHRASE,
            resp_encr_type=self.ENCR_TYPE_PMK)

    #######################################
    # Positive Out-of-Band (OOB) tests key:
    #
    # names is: test_oob_<encr_type>_<peer_spec>
    # where:
    #
    # encr_type: Encryption type: open, passphrase
    # peer_spec: Peer specification method: any or specific
    #
    # Optionally set up an extra discovery session to test coexistence. If so
    # add "ib_coex" to test name.
    #
    # Note: Out-of-Band means using a non-Wi-Fi Aware mechanism for discovery
    # and exchange of MAC addresses and then Wi-Fi Aware for data-path.
    #######################################

    def test_oob_open_specific(self):
        """Data-path: out-of-band, open encryption, specific peer

    Verifies end-to-end discovery + data-path creation.
    """
        self.run_oob_data_path_test(
            encr_type=self.ENCR_TYPE_OPEN, use_peer_id=True)

    def test_oob_passphrase_specific(self):
        """Data-path: out-of-band, passphrase, specific peer

    Verifies end-to-end discovery + data-path creation.
    """
        self.run_oob_data_path_test(
            encr_type=self.ENCR_TYPE_PASSPHRASE, use_peer_id=True)

    def test_oob_pmk_specific(self):
        """Data-path: out-of-band, PMK, specific peer

    Verifies end-to-end discovery + data-path creation.
    """
        self.run_oob_data_path_test(
            encr_type=self.ENCR_TYPE_PMK, use_peer_id=True)

    def test_oob_ib_coex_open_specific(self):
        """Data-path: out-of-band, open encryption, specific peer - in-band coex:
    set up a concurrent discovery session to verify no impact. The session
    consists of Publisher on both ends, and a Subscriber on the Responder.

    Verifies end-to-end discovery + data-path creation.
    """
        self.run_oob_data_path_test(
            encr_type=self.ENCR_TYPE_OPEN,
            setup_discovery_sessions=True , use_peer_id=True)

    def test_multiple_identical_networks(self):
        """Validate that creating multiple networks between 2 devices, each network
        with identical configuration is supported over a single NDP.

        Verify that the interface and IPv6 address is the same for all networks.
        """
        init_dut = self.ads[0]
        init_dut.pretty_name = "Initiator"
        resp_dut = self.ads[1]
        resp_dut.pretty_name = "Responder"
        N = 2  # first iteration (must be 2 to give us a chance to cancel the first)
        M = 5  # second iteration

        init_ids = []
        resp_ids = []

        # Initiator+Responder: attach and wait for confirmation & identity
        # create N+M sessions to be used in the different (but identical) NDPs
        for i in range(N + M):
            id, init_mac = self.attach_with_identity(init_dut)
            init_ids.append(id)
            id, resp_mac = self.attach_with_identity(resp_dut)
            resp_ids.append(id)

        # wait for devices to synchronize with each other - there are no other
        # mechanisms to make sure this happens for OOB discovery (except retrying
        # to execute the data-path request)
        time.sleep(self.WAIT_FOR_CLUSTER)

        resp_req_keys = []
        init_req_keys = []
        resp_aware_ifs = []
        init_aware_ifs = []
        resp_aware_ipv6 = []
        init_aware_ipv6 = []
        init_dut_accept_handler = (
            init_dut.wifi_aware_snippet.connectivityServerSocketAccept())
        network_id = init_dut_accept_handler.callback_id
        for i in range(N):
            init_req_key = self.request_oob_network(
                init_dut,
                init_ids[i],
                _DATA_PATH_INITIATOR,
                resp_mac,
                None,
                None,
                network_id
                )
            init_req_keys.append(init_req_key)
            resp_ini_key = self.request_oob_network(
                resp_dut,
                resp_ids[i],
                _DATA_PATH_RESPONDER,
                init_mac,
                None,
                None,
                network_id
                )
            resp_req_keys.append(resp_ini_key)
            self.wait_for_request_responses(init_dut, init_req_keys[i],
             init_aware_ifs, resp_aware_ipv6)
            self.wait_for_request_responses(resp_dut,
             resp_req_keys[i], resp_aware_ifs, init_aware_ipv6)
        for i in range(M):
            init_req_key = self.request_oob_network(
                init_dut,
                init_ids[N + i],
                _DATA_PATH_INITIATOR,
                resp_mac,
                None,
                None,
                network_id
                )
            init_req_keys.append(init_req_key)
            resp_ini_key = self.request_oob_network(
                resp_dut,
                resp_ids[N + i],
                _DATA_PATH_RESPONDER,
                init_mac,
                None,
                None,
                network_id
                )
            resp_req_keys.append(resp_ini_key)
            self.wait_for_request_responses(init_dut, init_req_keys[i],
             init_aware_ifs, resp_aware_ipv6)
            self.wait_for_request_responses(resp_dut, resp_req_keys[i],
             resp_aware_ifs, init_aware_ipv6)
        # determine whether all interfaces and ipv6 addresses are identical
        # (single NDP)
        init_aware_ifs = list(set(init_aware_ifs))
        resp_aware_ifs = list(set(resp_aware_ifs))
        init_aware_ipv6 = list(set(init_aware_ipv6))
        resp_aware_ipv6 = list(set(resp_aware_ipv6))
        logging.info("Interface names: I=%s, R=%s", init_aware_ifs, resp_aware_ifs)
        logging.info("Interface IPv6: I=%s, R=%s", init_aware_ipv6, resp_aware_ipv6)
        logging.info("Initiator requests: %s", init_req_keys)
        logging.info("Responder requests: %s", resp_req_keys)
        asserts.assert_equal(
            len(init_aware_ifs), 1, "Multiple initiator interfaces")
        asserts.assert_equal(
            len(resp_aware_ifs), 1, "Multiple responder interfaces")
        asserts.assert_equal(
            len(init_aware_ipv6), 1, "Multiple initiator IPv6 addresses")
        asserts.assert_equal(
            len(resp_aware_ipv6), 1, "Multiple responder IPv6 addresses")

        if init_dut.is_adb_root:
            for i in range(
                autils.get_aware_capabilities(init_dut)[_CAP_MAX_NDI_INTERFACES]):
                if_name = "%s%d" %("aware_data",i)
                init_ipv6 = self.get_ipv6_addr(init_dut, if_name)
                resp_ipv6 = self.get_ipv6_addr(resp_dut, if_name)
                asserts.assert_equal(
                    init_ipv6 is None, if_name not in init_aware_ifs,
                    "Initiator interface %s in unexpected state" % if_name)
                asserts.assert_equal(
                    resp_ipv6 is None, if_name not in resp_aware_ifs,
                    "Responder interface %s in unexpected state" % if_name)
        for resp_req_key in resp_req_keys:
            resp_req_callback_event = resp_req_key.waitAndGet(
                event_name=constants.NetworkCbEventName.NETWORK_CALLBACK,
                timeout=_DEFAULT_TIMEOUT,
            )
            resp_dut.wifi_aware_snippet.connectivityUnregisterNetwork(
                resp_req_callback_event.callback_id)
        for init_req_key in init_req_keys:
            init_req_callback_event = init_req_key.waitAndGet(
                event_name=constants.NetworkCbEventName.NETWORK_CALLBACK,
                timeout=_DEFAULT_TIMEOUT,
            )
            init_dut.wifi_aware_snippet.connectivityUnregisterNetwork(
                init_req_callback_event.callback_id)

    def test_identical_network_from_both_sides(self):
        """Validate that requesting two identical NDPs (Open) each being initiated
        from a different side, results in the same/single NDP.

        Verify that the interface and IPv6 address is the same for all networks.
        """
        init_dut = self.ads[0]
        resp_dut = self.ads[1]
        init_id, init_mac = self.attach_with_identity(init_dut)
        resp_id, resp_mac = self.attach_with_identity(resp_dut)
        time.sleep(self.WAIT_FOR_CLUSTER)
        # first NDP: DUT1 (Init) -> DUT2 (Resp)
        init_dut_accept_handler = (
            init_dut.wifi_aware_snippet.connectivityServerSocketAccept())
        network_id = init_dut_accept_handler.callback_id
        resp_req_key_a = self.request_oob_network(
            resp_dut,
            resp_id,
            _DATA_PATH_RESPONDER, # DATA_PATH_RESPONDER = 1
            init_mac,
            None,
            None,
            network_id
            )

        # Initiator: request network
        init_req_key_a = self.request_oob_network(
            init_dut,
            init_id,
            _DATA_PATH_INITIATOR, #DATA_PATH_INITIATOR = 0
            resp_mac,
            None,
            None,
            network_id
            )
        i_network_callback_event_a = init_req_key_a.waitAndGet(
                event_name=constants.NetworkCbEventName.NETWORK_CALLBACK,
                timeout=_DEFAULT_TIMEOUT,
            )
        i_callback_name_a = i_network_callback_event_a.data[_CALLBACK_NAME]
        r_network_callback_event_a = resp_req_key_a.waitAndGet(
                event_name=constants.NetworkCbEventName.NETWORK_CALLBACK,
                timeout=_DEFAULT_TIMEOUT,
            )
        r_callback_name_a = r_network_callback_event_a.data[_CALLBACK_NAME]

        asserts.assert_equal(
                i_callback_name_a,
                constants.NetworkCbName.ON_CAPABILITIES_CHANGED,
                f'{init_dut} succeeded to request the network, got callback'
                f' {i_callback_name_a}.'
                )
        network = i_network_callback_event_a.data[
            constants.NetworkCbEventKey.NETWORK]
        network_capabilities = i_network_callback_event_a.data[
            constants.NetworkCbEventKey.NETWORK_CAPABILITIES]
        asserts.assert_true(
            network and network_capabilities,
            f'{init_dut} received a null Network or NetworkCapabilities!?.'
        )
        asserts.assert_equal(
            r_callback_name_a, constants.NetworkCbName.ON_CAPABILITIES_CHANGED,
            f'{resp_dut} succeeded to request the network, got callback'
            f' {r_callback_name_a}.'
            )
        network = r_network_callback_event_a.data[
            constants.NetworkCbEventKey.NETWORK]
        network_capabilities = r_network_callback_event_a.data[
            constants.NetworkCbEventKey.NETWORK_CAPABILITIES]
        asserts.assert_true(
            network and network_capabilities,
            f'{resp_dut} received a null Network or NetworkCapabilities!?.'
        )
        i_net_event_nc_a = i_network_callback_event_a.data
        r_net_event_nc_a = r_network_callback_event_a.data
        # validate no leak of information
        asserts.assert_false(
            _NETWORK_CB_KEY_NETWORK_SPECIFIER in i_net_event_nc_a,
            "Network specifier leak!")
        asserts.assert_false(
            _NETWORK_CB_KEY_NETWORK_SPECIFIER in r_net_event_nc_a,
            "Network specifier leak!")
        #To get ipv6 ip address
        i_ipv6_1= i_net_event_nc_a[constants.NetworkCbName.NET_CAP_IPV6]
        r_ipv6_1 = r_net_event_nc_a[constants.NetworkCbName.NET_CAP_IPV6]
        # note that Pub <-> Sub since IPv6 are of peer's!
        i_network_callback_LINK_a = init_req_key_a.waitAndGet(
            event_name=constants.NetworkCbEventName.NETWORK_CALLBACK,
            timeout=_DEFAULT_TIMEOUT,
            )
        asserts.assert_equal(
            i_network_callback_LINK_a.data[_CALLBACK_NAME],
            _NETWORK_CB_LINK_PROPERTIES_CHANGED,
            f'{init_dut} succeeded to request the LinkPropertiesChanged,'+
            ' got callback'
            f' {i_network_callback_LINK_a.data[_CALLBACK_NAME]}.'
            )

        r_network_callback_LINK_a = resp_req_key_a.waitAndGet(
            event_name=constants.NetworkCbEventName.NETWORK_CALLBACK,
            timeout=_DEFAULT_TIMEOUT,
            )
        asserts.assert_equal(
            r_network_callback_LINK_a.data[_CALLBACK_NAME],
            _NETWORK_CB_LINK_PROPERTIES_CHANGED,
            f'{resp_dut} succeeded to request the LinkPropertiesChanged,'+
            ' got callback'
            f' {r_network_callback_LINK_a.data[_CALLBACK_NAME]}.'
            )
        i_aware_if_1 = i_network_callback_LINK_a.data[
            _NETWORK_CB_KEY_INTERFACE_NAME]
        r_aware_if_1 = r_network_callback_LINK_a.data[
            _NETWORK_CB_KEY_INTERFACE_NAME]

        logging.info("Interface names: p=%s, s=%s", i_aware_if_1,
                     r_aware_if_1)
        logging.info("Interface addresses (IPv6): p=%s, s=%s", i_ipv6_1,
                     r_ipv6_1)
        # second NDP: DUT2 (Init) -> DUT1 (Resp)
        init_req_key = self.request_oob_network(
            init_dut,
            init_id,
            _DATA_PATH_RESPONDER,
            resp_mac,
            None,
            None,
            network_id
            )
        resp_ini_key = self.request_oob_network(
            resp_dut,
            resp_id,
            _DATA_PATH_INITIATOR,
            init_mac,
            None,
            None,
            network_id
            )
        i_network_callback_event = init_req_key.waitAndGet(
                event_name=constants.NetworkCbEventName.NETWORK_CALLBACK,
                timeout=_DEFAULT_TIMEOUT,
            )
        i_callback_name = i_network_callback_event.data[_CALLBACK_NAME]
        r_network_callback_event = resp_ini_key.waitAndGet(
                event_name=constants.NetworkCbEventName.NETWORK_CALLBACK,
                timeout=_DEFAULT_TIMEOUT,
            )
        r_callback_name = r_network_callback_event.data[_CALLBACK_NAME]

        asserts.assert_equal(
                i_callback_name,
                constants.NetworkCbName.ON_CAPABILITIES_CHANGED,
                f'{init_dut} succeeded to request the network, got callback'
                f' {i_callback_name}.'
                )
        network = i_network_callback_event.data[
            constants.NetworkCbEventKey.NETWORK]
        network_capabilities = i_network_callback_event.data[
            constants.NetworkCbEventKey.NETWORK_CAPABILITIES]
        asserts.assert_true(
            network and network_capabilities,
            f'{init_dut} received a null Network or NetworkCapabilities!?.'
        )
        asserts.assert_equal(
            r_callback_name, constants.NetworkCbName.ON_CAPABILITIES_CHANGED,
            f'{resp_dut} succeeded to request the network, got callback'
            f' {r_callback_name}.'
            )
        network = r_network_callback_event.data[
            constants.NetworkCbEventKey.NETWORK]
        network_capabilities = r_network_callback_event.data[
            constants.NetworkCbEventKey.NETWORK_CAPABILITIES]
        asserts.assert_true(
            network and network_capabilities,
            f'{resp_dut} received a null Network or NetworkCapabilities!?.'
        )
        i_net_event_nc = i_network_callback_event.data
        r_net_event_nc = r_network_callback_event.data
        # validate no leak of information
        asserts.assert_false(
            _NETWORK_CB_KEY_NETWORK_SPECIFIER in i_net_event_nc,
            "Network specifier leak!")
        asserts.assert_false(
            _NETWORK_CB_KEY_NETWORK_SPECIFIER in r_net_event_nc,
            "Network specifier leak!")
        #To get ipv6 ip address
        i_ipv6_2 = i_net_event_nc[constants.NetworkCbName.NET_CAP_IPV6]
        r_ipv6_2 = r_net_event_nc[constants.NetworkCbName.NET_CAP_IPV6]
        # note that Pub <-> Sub since IPv6 are of peer's!
        i_network_callback_LINK = init_req_key.waitAndGet(
            event_name=constants.NetworkCbEventName.NETWORK_CALLBACK,
            timeout=_DEFAULT_TIMEOUT,
            )
        asserts.assert_equal(
            i_network_callback_LINK.data[_CALLBACK_NAME],
            _NETWORK_CB_LINK_PROPERTIES_CHANGED,
            f'{init_dut} succeeded to request the LinkPropertiesChanged,'+
            ' got callback'
            f' {i_network_callback_LINK.data[_CALLBACK_NAME]}.'
            )

        r_network_callback_LINK = resp_ini_key.waitAndGet(
            event_name=constants.NetworkCbEventName.NETWORK_CALLBACK,
            timeout=_DEFAULT_TIMEOUT,
            )
        asserts.assert_equal(
            r_network_callback_LINK.data[_CALLBACK_NAME],
            _NETWORK_CB_LINK_PROPERTIES_CHANGED,
            f'{resp_dut} succeeded to request the LinkPropertiesChanged,'+
            ' got callback'
            f' {r_network_callback_LINK.data[_CALLBACK_NAME]}.'
            )
        i_aware_if_2 = i_network_callback_LINK.data[
            _NETWORK_CB_KEY_INTERFACE_NAME]
        r_aware_if_2 = r_network_callback_LINK.data[
            _NETWORK_CB_KEY_INTERFACE_NAME]

        logging.info("Interface names: p=%s, s=%s", i_aware_if_2,
                     r_aware_if_2)
        logging.info("Interface addresses (IPv6): p=%s, s=%s", i_ipv6_2,
                     r_ipv6_2)
        # validate equality of NDPs (using interface names & ipv6)
        asserts.assert_equal(i_aware_if_1, i_aware_if_2,
                             "DUT1 NDPs are on different interfaces")
        asserts.assert_equal(r_aware_if_1, r_aware_if_2,
                             "DUT2 NDPs are on different interfaces")
        asserts.assert_equal(i_ipv6_1, i_ipv6_2,
                             "DUT1 NDPs are using different IPv6 addresses")
        asserts.assert_equal(r_ipv6_1, r_ipv6_2,
                             "DUT2 NDPs are using different IPv6 addresses")
        # release requests
        init_dut.wifi_aware_snippet.connectivityUnregisterNetwork(
            i_network_callback_event_a.callback_id)
        resp_dut.wifi_aware_snippet.connectivityUnregisterNetwork(
            r_network_callback_event_a.callback_id)
        init_dut.wifi_aware_snippet.connectivityUnregisterNetwork(
            i_network_callback_event.callback_id)
        resp_dut.wifi_aware_snippet.connectivityUnregisterNetwork(
            r_network_callback_event.callback_id)

    def run_multiple_ndi(self, sec_configs, flip_init_resp=False):
        """Validate that the device can create and use multiple NDIs.

        The security configuration can be:
        - None: open
        - String: passphrase
        - otherwise: PMK (byte array)

        Args:
        sec_configs: list of security configurations
        flip_init_resp: if True the roles of Initiator and Responder are flipped
                        between the 2 devices, otherwise same devices are always
                        configured in the same role.
        """
        dut1 = self.ads[0]
        dut2 = self.ads[1]
        asserts.skip_if(
            autils.get_aware_capabilities(dut1)[_CAP_MAX_NDI_INTERFACES] <
            len(sec_configs)
            or autils.get_aware_capabilities(dut2)[_CAP_MAX_NDI_INTERFACES] <
            len(sec_configs), "DUTs do not support enough NDIs")

        id1, mac1 = self.attach_with_identity(dut1)
        id2, mac2 = self.attach_with_identity(dut2)
        time.sleep(self.WAIT_FOR_CLUSTER)
        dut2_req_keys = []
        dut2_key_evens = []
        dut1_req_keys = []
        dut1_key_evens = []
        dut2_aware_ifs = []
        dut1_aware_ifs = []
        dut2_aware_ipv6s = []
        dut1_aware_ipv6s = []
        dut2_type = _DATA_PATH_RESPONDER
        dut1_type = _DATA_PATH_INITIATOR
        dut2_is_responder = True
        if flip_init_resp:
            if dut2_is_responder:
                dut2_type = _DATA_PATH_INITIATOR
                dut1_type = _DATA_PATH_RESPONDER
            else:
                dut2_type = _DATA_PATH_RESPONDER
                dut1_type = _DATA_PATH_INITIATOR
            dut2_is_responder = not dut2_is_responder
        # first NDP: DUT1 (Init) -> DUT2 (Resp)
        dut1_accept_handler = (
            dut1.wifi_aware_snippet.connectivityServerSocketAccept())
        network_id = dut1_accept_handler.callback_id
        for sec in sec_configs:
            if dut2_is_responder:
                dut2_req_key = self.get_network_specifier(
                    dut2, id2, dut2_type, mac1, sec, network_id
                    )
                dut1_req_key = self.get_network_specifier(
                    dut1, id1, dut1_type, mac2, sec, network_id
                    )
            else:
                dut1_req_key = self.get_network_specifier(
                    dut1, id1, dut1_type, mac2, sec, network_id
                    )
                dut2_req_key = self.get_network_specifier(
                    dut2, id2, dut2_type, mac1,  sec, network_id
                    )
            dut2_req_keys.append(dut2_req_key)
            dut1_req_keys.append(dut1_req_key)

            dut1_key_even = dut1_req_key.waitAndGet(
                event_name=constants.NetworkCbEventName.NETWORK_CALLBACK,
                timeout=_DEFAULT_TIMEOUT,
                )
            i_callback_name_a = dut1_key_even.data[_CALLBACK_NAME]
            dut2_key_even = dut2_req_key.waitAndGet(
                event_name=constants.NetworkCbEventName.NETWORK_CALLBACK,
                timeout=_DEFAULT_TIMEOUT,
                )
            r_callback_name_a = dut2_key_even.data[_CALLBACK_NAME]
            dut2_aware_ipv6 = dut2_key_even.data[
                constants.NetworkCbName.NET_CAP_IPV6]
            dut1_aware_ipv6 = dut1_key_even.data[
                constants.NetworkCbName.NET_CAP_IPV6]
            dut2_key_evens.append(dut2_key_even)
            dut1_key_evens.append(dut1_key_even)
            asserts.assert_true(
                constants.NetworkCbName.ON_CAPABILITIES_CHANGED in
                dut2_key_even.data[_CALLBACK_NAME],
                f'{dut2} succeeded to request the network, got callback'
                f' {dut2_key_evens}.'
                )
            asserts.assert_true(
                constants.NetworkCbName.ON_CAPABILITIES_CHANGED in
                dut1_key_even.data[_CALLBACK_NAME],
                f'{dut1} succeeded to request the network, got callback'
                f' {dut1_key_evens}.'
                )

            dut1_key_even = dut1_req_key.waitAndGet(
                event_name=constants.NetworkCbEventName.NETWORK_CALLBACK,
                timeout=_DEFAULT_TIMEOUT,
                )
            i_callback_name_a = dut1_key_even.data[_CALLBACK_NAME]
            dut2_key_even = dut2_req_key.waitAndGet(
                event_name=constants.NetworkCbEventName.NETWORK_CALLBACK,
                timeout=_DEFAULT_TIMEOUT,
                )
            r_callback_name_a = dut2_key_even.data[_CALLBACK_NAME]

            dut2_key_evens.append(dut2_key_even)
            dut1_key_evens.append(dut1_key_even)
            asserts.assert_true(
                _NETWORK_CB_LINK_PROPERTIES_CHANGED in
                dut2_key_even.data[_CALLBACK_NAME],
                f'{dut2} succeeded to request the network, got callback'
                f' {dut2_key_evens}.'
                )
            asserts.assert_true(
                _NETWORK_CB_LINK_PROPERTIES_CHANGED in
                dut1_key_even.data[_CALLBACK_NAME],
                f'{dut1} succeeded to request the network, got callback'
                f' {dut1_key_evens}.'
                )
            dut2_aware_if = dut2_key_even.data[_NETWORK_CB_KEY_INTERFACE_NAME]
            dut1_aware_if = dut1_key_even.data[_NETWORK_CB_KEY_INTERFACE_NAME]
            dut2_aware_ifs.append(dut2_aware_if)
            dut1_aware_ifs.append(dut1_aware_if)
            dut2_aware_ipv6s.append(dut2_aware_ipv6)
            dut1_aware_ipv6s.append(dut1_aware_ipv6)
        dut1_aware_ifs = list(set(dut1_aware_ifs))
        dut2_aware_ifs = list(set(dut2_aware_ifs))
        dut1_aware_ipv6s = list(set(dut1_aware_ipv6s))
        dut2_aware_ipv6s = list(set(dut2_aware_ipv6s))
        logging.info("Interface names: DUT1=%s, DUT2=%s", dut1_aware_ifs,
                      dut2_aware_ifs)
        logging.info("IPv6 addresses: DUT1=%s, DUT2=%s", dut1_aware_ipv6s,
                      dut2_aware_ipv6s)
        asserts.assert_equal(
            len(dut1_aware_ifs), len(sec_configs), "Multiple DUT1 interfaces")
        asserts.assert_equal(
            len(dut2_aware_ifs), len(sec_configs), "Multiple DUT2 interfaces")
        asserts.assert_equal(
            len(dut1_aware_ipv6s), len(sec_configs),
            "Multiple DUT1 IPv6 addresses")
        asserts.assert_equal(
            len(dut2_aware_ipv6s), len(sec_configs),
            "Multiple DUT2 IPv6 addresses")
        for i in range(len(sec_configs)):
            if_name = "%s%d" %("aware_data",i)
            dut1_ipv6 = self.get_ipv6_addr(dut1, if_name)
            dut2_ipv6 = self.get_ipv6_addr(dut2, if_name)
            asserts.assert_equal(
                dut1_ipv6 is None, if_name not in dut1_aware_ifs,
                "Initiator interface %s in unexpected state" % if_name)
            asserts.assert_equal(
                dut2_ipv6 is None, if_name not in dut2_aware_ifs,
                "Responder interface %s in unexpected state" % if_name)
        for dut1_req_key in dut1_req_keys:
            dut1.wifi_aware_snippet.connectivityUnregisterNetwork(
                dut1_key_even.callback_id)
        for dut2_key_even in dut2_key_evens:
            dut2.wifi_aware_snippet.connectivityUnregisterNetwork(
                dut2_key_even.callback_id)

    def test_multiple_ndi_open_passphrase(self):
        """Verify that between 2 DUTs can create 2 NDPs with different security
        configuration (one open, one using passphrase). The result should use
        twodifferent NDIs
        """
        # self.run_multiple_ndi(self.PASSPHRASE)
        self.run_multiple_ndi([None, self.PASSPHRASE])

    def test_multiple_ndi_passphrases(self):
        """Verify that between 2 DUTs can create 2 NDPs with different security
        configuration (using different passphrases). The result should use two
        different NDIs
        """
        self.run_multiple_ndi([self.PASSPHRASE, self.PASSPHRASE2])

    def test_multiple_ndi_open_passphrase_flip(self):
        """Verify that between 2 DUTs can create 2 NDPs with different security
        configuration (one open, one using passphrase). The result should use
        two different NDIs.

        Flip Initiator and Responder roles.
        """
        self.run_multiple_ndi([None, self.PASSPHRASE], flip_init_resp=True)

    def test_multiple_ndi_passphrases_flip(self):
        """Verify that between 2 DUTs can create 2 NDPs with different security
        configuration (using different passphrases). The result should use two
        different NDIs

        Flip Initiator and Responder roles.
        """
        self.run_multiple_ndi(
            [self.PASSPHRASE, self.PASSPHRASE2], flip_init_resp=True)

    def test_multiple_ndi_open_pmk(self):
        """Verify that between 2 DUTs can create 2 NDPs with different security
        configuration (one open, one using pmk). The result should use two
        different NDIs
        """
        self.run_multiple_ndi([None, self.PMK])

    def test_multiple_ndi_passphrase_pmk(self):
        """Verify that between 2 DUTs can create 2 NDPs with different security
        configuration (one using passphrase, one using pmk). The result should
        use two different NDIs
        """
        self.run_multiple_ndi([self.PASSPHRASE, self.PMK])

    def test_multiple_ndi_pmks(self):
        """Verify that between 2 DUTs can create 2 NDPs with different security
        configuration (using different PMKS). The result should use two
        different NDIs
        """
        self.run_multiple_ndi([self.PMK, self.PMK2])

    def test_multiple_ndi_open_pmk_flip(self):
        """Verify that between 2 DUTs can create 2 NDPs with different security
        configuration (one open, one using pmk). The result should use two
        different NDIs

        Flip Initiator and Responder roles.
        """
        self.run_multiple_ndi([None, self.PMK], flip_init_resp=True)

    def test_multiple_ndi_passphrase_pmk_flip(self):
        """Verify that between 2 DUTs can create 2 NDPs with different security
        configuration (one using passphrase, one using pmk). The result should
        use two different NDIs

        Flip Initiator and Responder roles.
        """
        self.run_multiple_ndi([self.PASSPHRASE, self.PMK], flip_init_resp=True)

    def test_multiple_ndi_pmks_flip(self):
        """Verify that between 2 DUTs can create 2 NDPs with different security
        configuration (using different PMKS). The result should use two
        different NDIs

        Flip Initiator and Responder roles.
        """
        self.run_multiple_ndi([self.PMK, self.PMK2], flip_init_resp=True)

    #######################################
    # The device can create and use multiple NDIs tests key:
    #
    # names is:test_<same_request>_ndps_mix_ib_oob_
    #          <ib_first>_<inits_on_same_dut>_polarity
    # same_request:
    #   Issue canonically identical requests (same NMI peer, same passphrase)
    #   if True, if False use different passphrases.
    # ib_first:
    #   If True then the in-band network is requested first, otherwise (if False)
    #   then the out-of-band network is requested first.
    # inits_on_same_dut:
    #   If True then the Initiators are run on the same device, otherwise
    #   (if False) then the Initiators are run on different devices.
    #
    #######################################

    def test_identical_ndps_mix_ib_oob_ib_first_same_polarity(self):
        """Validate that a single NDP is created for multiple identical
        requests which are issued through either in-band (ib) or out-of-band
        (oob) APIs.

        The in-band request is issued first. Both Initiators (Sub == Initiator)
        are run on the same device.
        """
        self.run_mix_ib_oob(
            same_request=True, ib_first=True, inits_on_same_dut=True)

    def test_identical_ndps_mix_ib_oob_oob_first_same_polarity(self):
        """Validate that a single NDP is created for multiple identical
        requests which are issued through either in-band (ib) or out-of-band
        (oob) APIs.

        The out-of-band request is issued first. Both Initiators (Sub ==
        Initiator)
        are run on the same device.
        """
        self.run_mix_ib_oob(
            same_request=True, ib_first=False, inits_on_same_dut=True)

    def test_identical_ndps_mix_ib_oob_ib_first_diff_polarity(self):
        """Validate that a single NDP is created for multiple identical
        requests which are issued through either in-band (ib) or out-of-band
        (oob) APIs.

        The in-band request is issued first. Initiators (Sub == Initiator) are
        run on different devices.
        """
        self.run_mix_ib_oob(
            same_request=True, ib_first=True, inits_on_same_dut=False)

    def test_identical_ndps_mix_ib_oob_oob_first_diff_polarity(self):
        """Validate that a single NDP is created for multiple identical
        requests which are issued through either in-band (ib) or out-of-band
        (oob) APIs.

        The out-of-band request is issued first. Initiators (Sub == Initiator)
        are run on different devices.
        """
        self.run_mix_ib_oob(
            same_request=True, ib_first=False, inits_on_same_dut=False)

    def test_multiple_ndis_mix_ib_oob_ib_first_same_polarity(self):

        """Validate that multiple NDIs are created for NDPs which are requested
        with different security configurations. Use a mix of in-band and
        out-of-band APIs to request the different NDPs.

        The in-band request is issued first. Initiators (Sub == Initiator) are
        run on the same device.
        """
        self.run_mix_ib_oob(
            same_request=False, ib_first=True, inits_on_same_dut=True)

    def test_multiple_ndis_mix_ib_oob_oob_first_same_polarity(self):
        """Validate that multiple NDIs are created for NDPs which are requested
        with different security configurations. Use a mix of in-band and
        out-of-band APIs to request the different NDPs.

        The out-of-band request is issued first. Initiators (Sub == Initiator)
        are run on the same device.
        """
        self.run_mix_ib_oob(
            same_request=False, ib_first=False, inits_on_same_dut=True)

    def test_multiple_ndis_mix_ib_oob_ib_first_diff_polarity(self):
        """Validate that multiple NDIs are created for NDPs which are requested
        with different security configurations. Use a mix of in-band and
        out-of-band APIs to request the different NDPs.

        The in-band request is issued first. Initiators (Sub == Initiator) are
        run on different devices.
        """
        self.run_mix_ib_oob(
            same_request=False, ib_first=True, inits_on_same_dut=False)


    def test_multiple_ndis_mix_ib_oob_oob_first_diff_polarity(self):
        """Validate that multiple NDIs are created for NDPs which are requested
        with different security configurations. Use a mix of in-band and
        out-of-band APIs to request the different NDPs.

        The out-of-band request is issued first. Initiators (Sub == Initiator)
        are run on different devices.
        """
        self.run_mix_ib_oob(
            same_request=False, ib_first=False, inits_on_same_dut=False)

    #######################################
    # The device can setup two conflicting regulatory domains with a data-path test:
    # names is:test_multiple_regulator_domains_ib_(regulatorA)_(regulatorB)
    # use_ib: True to use in-band discovery, False to use out-of-band discovery.
    # init_domain: The regulatory domain of the Initiator/Subscriber.
    # resp_domain: The regulator domain of the Responder/Publisher.
    #
    #######################################

    def test_multiple_regulator_domains_ib_us_jp(self):
        """Verify data-path setup across multiple regulator domains.

        - Uses in-band discovery
        - Subscriber=US, Publisher=JP
        """
        self.run_multiple_regulatory_domains(
            use_ib=True,
            init_domain="US",
            resp_domain="JP")

    @ApiTest(
    apis=[
        'android.net.wifi.aware.WifiAwareManager#attach(android.net.wifi.aware.AttachCallback, android.net.wifi.aware.IdentityChangedListener, android.os.Handler)',
        'android.net.wifi.aware.WifiAwareSession#publish(android.net.wifi.aware.PublishConfig, android.net.wifi.aware.DiscoverySessionCallback, android.os.Handler)',
        'android.net.wifi.aware.WifiAwareSession#subscrible(android.net.wifi.aware.SubscribeConfig, android.net.wifi.aware.DiscoverySessionCallback, android.os.Handler)',
        'android.net.wifi.aware.WifiAwareNetworkSpecifier.Builder#build',
        'android.net.wifi.aware.WifiAwareSession#createNetworkSpecifierOpen(byte[])',
        ]
    )

    def test_multiple_regulator_domains_ib_jp_us(self):
        """Verify data-path setup across multiple regulator domains.

    - Uses in-band discovery
    - Subscriber=JP, Publisher=US
    """
        self.run_multiple_regulatory_domains(
            use_ib=True,
            init_domain="JP",
            resp_domain="US")

    @ApiTest(
    apis=[
        'android.net.wifi.aware.WifiAwareManager#attach(android.net.wifi.aware.AttachCallback, android.net.wifi.aware.IdentityChangedListener, android.os.Handler)',
        'android.net.wifi.aware.WifiAwareSession#publish(android.net.wifi.aware.PublishConfig, android.net.wifi.aware.DiscoverySessionCallback, android.os.Handler)',
        'android.net.wifi.aware.WifiAwareSession#subscrible(android.net.wifi.aware.SubscribeConfig, android.net.wifi.aware.DiscoverySessionCallback, android.os.Handler)',
        'android.net.wifi.aware.WifiAwareNetworkSpecifier.Builder#build',
        'android.net.wifi.aware.WifiAwareSession#createNetworkSpecifierOpen(byte[])',
        ]
    )

    def test_multiple_regulator_domains_oob_us_jp(self):
        """Verify data-path setup across multiple regulator domains.

    - Uses out-f-band discovery
    - Initiator=US, Responder=JP
    """
        self.run_multiple_regulatory_domains(
            use_ib=False,
            init_domain="US",
            resp_domain="JP")

    @ApiTest(
    apis=[
        'android.net.wifi.aware.WifiAwareManager#attach(android.net.wifi.aware.AttachCallback, android.net.wifi.aware.IdentityChangedListener, android.os.Handler)',
        'android.net.wifi.aware.WifiAwareSession#publish(android.net.wifi.aware.PublishConfig, android.net.wifi.aware.DiscoverySessionCallback, android.os.Handler)',
        'android.net.wifi.aware.WifiAwareSession#subscrible(android.net.wifi.aware.SubscribeConfig, android.net.wifi.aware.DiscoverySessionCallback, android.os.Handler)',
        'android.net.wifi.aware.WifiAwareNetworkSpecifier.Builder#build',
        'android.net.wifi.aware.WifiAwareSession#createNetworkSpecifierOpen(byte[])',
        ]
    )

    def test_multiple_regulator_domains_oob_jp_us(self):
        """Verify data-path setup across multiple regulator domains.

    - Uses out-of-band discovery
    - Initiator=JP, Responder=US
    """
        self.run_multiple_regulatory_domains(
            use_ib=False,
            init_domain="JP",
            resp_domain="US")


if __name__ == '__main__':
    # Take test args
    if '--' in sys.argv:
        index = sys.argv.index('--')
        sys.argv = sys.argv[:1] + sys.argv[index + 1:]

    test_runner.main()
