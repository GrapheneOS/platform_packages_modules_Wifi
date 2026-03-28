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
"""Utility functions for interacting with the Wi-Fi Aware snippet RPCs."""

import datetime
import random

from aware import constants
from mobly import asserts
from mobly.controllers import android_device
from mobly.controllers.android_device_lib import callback_handler_v2
from mobly.snippet import callback_event


_DEFAULT_TIMEOUT = constants.WAIT_WIFI_STATE_TIME_OUT.total_seconds()
_REQUEST_NETWORK_TIMEOUT = datetime.timedelta(seconds=15)
_CALLBACK_NAME = constants.DiscoverySessionCallbackParamsType.CALLBACK_NAME
_IS_SESSION_INIT = constants.DiscoverySessionCallbackParamsType.IS_SESSION_INIT
_TRANSPORT_TYPE_WIFI_AWARE = (
    constants.NetworkCapabilities.Transport.TRANSPORT_WIFI_AWARE
)


def start_attach(
    ad: android_device.AndroidDevice,
    is_ranging_enabled: bool,
) -> (str, str):
    """Starts the attach process on the Android device.

    Args:
      ad: The Android device controller.
      is_ranging_enabled: Whether to enable ranging.

    Returns:
      A tuple of the attach session ID and the mac address of the aware
      interface. The mac address will be None if ranging is disabled.
    """
    attach_handler = ad.wifi.wifiAwareAttached(is_ranging_enabled)
    attach_event = attach_handler.waitAndGet(
        event_name=constants.AttachCallBackMethodType.ATTACHED,
        timeout=_DEFAULT_TIMEOUT,
    )
    asserts.assert_true(
        ad.wifi.wifiAwareIsSessionAttached(attach_event.callback_id),
        f'{ad} attach succeeded, but Wi-Fi Aware session is still null.',
    )
    mac_address = None
    if is_ranging_enabled:
        identity_changed_event = attach_handler.waitAndGet(
            event_name=constants.AttachCallBackMethodType.ID_CHANGED,
            timeout=_DEFAULT_TIMEOUT,
        )
        mac_address = identity_changed_event.data.get('mac', None)
        asserts.assert_true(
            bool(mac_address), 'Mac address should not be empty'
        )
    ad.log.info(
        'Attached Wi-Fi Aware session with ID %s and mac address %s.',
        attach_event.callback_id,
        mac_address,
    )
    return attach_event.callback_id, mac_address


def publish_and_subscribe(
    publisher: android_device.AndroidDevice,
    pub_config: constants.PublishConfig,
    pub_attach_session: str,
    subscriber: android_device.AndroidDevice,
    sub_config: constants.SubscribeConfig,
    sub_attach_session: str,
) -> tuple[
    str,
    callback_handler_v2.CallbackHandlerV2,
    str,
    callback_handler_v2.CallbackHandlerV2,
    int,
]:
    """Creates discovery sessions and waits for service discovery.

    This publishes a discovery session on the publisher, and subscribes to it
    on the subscriber. After this method returns, the sessions are connected
    and ready for further messaging.

    Args:
        publisher: The publisher.
        pub_config: The publish disocvery session configuration.
        pub_attach_session: The attach session ID of the publisher.
        subscriber: The subscriber.
        sub_config: The subscribe disocvery session configuration.
        sub_attach_session: The attach session ID of the subscriber.

    Returns:
        A tuple of the publish session ID, the publish session handler, the
        subscribe session ID, the subscribe session handler, and the peer ID
        of the subscriber.
    """
    # Initialize discovery sessions (publish and subscribe).
    pub_session_handler, pub_session = _start_publish(
        publisher=publisher,
        attach_session_id=pub_attach_session,
        pub_config=pub_config,
    )
    sub_session_handler, sub_session = _start_subscribe(
        subscriber=subscriber,
        attach_session_id=sub_attach_session,
        sub_config=sub_config,
    )
    # Wait for discovery.
    subscriber_peer = _wait_for_discovery(
        subscriber,
        sub_session_handler,
        pub_config,
        is_ranging_enabled=pub_config.ranging_enabled,
    )
    subscriber.log.info('The subscriber discovered the published service.')
    return (
        pub_session,
        pub_session_handler,
        sub_session,
        sub_session_handler,
        subscriber_peer,
    )


def _start_publish(
    publisher: android_device.AndroidDevice,
    attach_session_id: str,
    pub_config: constants.PublishConfig,
) -> tuple[callback_handler_v2.CallbackHandlerV2, str]:
    """Starts a publish session on the publisher device.

    Args:
        publisher: The Android device controller of the publisher.
        attach_session_id: The attach session ID of the publisher.
        pub_config: The publish configuration.

    Returns:
        A tuple of the callback handler for the publish session and the
        publish session ID.
    """
    # Start the publish session.
    publish_handler = publisher.wifi.wifiAwarePublish(
        attach_session_id, pub_config.to_dict()
    )

    # Wait for session start result.
    discovery_event = publish_handler.waitAndGet(
        event_name=constants.DiscoverySessionCallbackMethodType.DISCOVER_RESULT,
        timeout=_DEFAULT_TIMEOUT,
    )
    callback_name = discovery_event.data[_CALLBACK_NAME]
    asserts.assert_equal(
        constants.DiscoverySessionCallbackMethodType.PUBLISH_STARTED,
        callback_name,
        f'{publisher} publish failed, got callback: {callback_name}.',
    )

    is_session_init = discovery_event.data[_IS_SESSION_INIT]
    asserts.assert_true(
        is_session_init,
        f'{publisher} publish succeeded, but null discovery session returned.',
    )
    publisher.log.info('Created the publish session.')
    return publish_handler, publish_handler.callback_id


def _start_subscribe(
    subscriber: android_device.AndroidDevice,
    attach_session_id: str,
    sub_config: constants.SubscribeConfig,
) -> tuple[callback_handler_v2.CallbackHandlerV2, str]:
    """Starts a subscribe session on the subscriber device.

    Args:
        subscriber: The Android device controller of the subscriber.
        attach_session_id: The attach session ID of the subscriber.
        sub_config: The subscribe configuration.

    Returns:
        A tuple of the callback handler for the subscribe session and the
        subscribe session ID.
    """
    # Start the subscribe session.
    subscribe_handler = subscriber.wifi.wifiAwareSubscribe(
        attach_session_id, sub_config.to_dict()
    )

    # Wait for session start result.
    discovery_event = subscribe_handler.waitAndGet(
        event_name=constants.DiscoverySessionCallbackMethodType.DISCOVER_RESULT,
        timeout=_DEFAULT_TIMEOUT,
    )
    callback_name = discovery_event.data[_CALLBACK_NAME]
    asserts.assert_equal(
        constants.DiscoverySessionCallbackMethodType.SUBSCRIBE_STARTED,
        callback_name,
        f'{subscriber} subscribe failed, got callback: {callback_name}.',
    )
    is_session_init = discovery_event.data[_IS_SESSION_INIT]
    asserts.assert_true(
        is_session_init,
        f'{subscriber} subscribe succeeded, but null session returned.',
    )
    subscriber.log.info('Created subscribe session.')
    return subscribe_handler, subscribe_handler.callback_id


def _wait_for_discovery(
    subscriber: android_device.AndroidDevice,
    sub_session_handler: callback_handler_v2.CallbackHandlerV2,
    pub_config: constants.PublishConfig,
    is_ranging_enabled: bool,
) -> int:
    """Waits for discovery of the publisher's service by the subscriber.

    Args:
        subscriber: The Android device controller of the subscriber.
        sub_session_handler: The callback handler for the subscribe session.
        pub_config: The publish configuration.
        is_ranging_enabled: Whether the publisher has ranging enabled.

    Returns:
        The peer ID of the publisher as seen on the subscriber.
    """
    event_name = constants.DiscoverySessionCallbackMethodType.SERVICE_DISCOVERED
    if is_ranging_enabled:
        event_name = (
            constants.DiscoverySessionCallbackMethodType.SERVICE_DISCOVERED_WITHIN_RANGE
        )
    discover_data = sub_session_handler.waitAndGet(
        event_name=event_name, timeout=_DEFAULT_TIMEOUT
    )

    service_info = bytes(
        discover_data.data[
            constants.WifiAwareSnippetParams.SERVICE_SPECIFIC_INFO
        ]
    )
    if pub_config.pairing_config is not None:
        asserts.assert_equal(
            discover_data.data[
                constants.WifiAwareSnippetParams.PAIRED_SETUP_ENABLED
            ],
            pub_config.pairing_config.pairing_setup_enabled,
            f'{subscriber} got unexpected pairing setup enabled in discovery'
            f' callback event "{event_name}".',
        )
        asserts.assert_equal(
            discover_data.data[
                constants.WifiAwareSnippetParams.PAIRED_CACHE_ENABLED
            ],
            pub_config.pairing_config.pairing_cache_enabled,
            f'{subscriber} got unexpected pairing cache enabled in discovery'
            f' callback event "{event_name}".',
        )
        asserts.assert_equal(
            discover_data.data[
                constants.WifiAwareSnippetParams.PAIRED_VERIFICATION_ENABLED
            ],
            pub_config.pairing_config.pairing_verification_enabled,
            f'{subscriber} got unexpected pairing verification enabled in'
            f' discovery callback event "{event_name}".',
        )
        asserts.assert_equal(
            discover_data.data[
                constants.WifiAwareSnippetParams.BOOTSTRAPPING_METHOD
            ],
            pub_config.pairing_config.bootstrapping_methods,
            f'{subscriber} got unexpected bootstrapping method in discovery'
            f' callback event "{event_name}".',
        )

    asserts.assert_equal(
        service_info,
        pub_config.service_specific_info,
        f'{subscriber} got unexpected service info in discovery'
        f' callback event "{event_name}".',
    )
    match_filters = discover_data.data[
        constants.WifiAwareSnippetParams.MATCH_FILTER
    ]
    match_filters = [
        bytes(filter[constants.WifiAwareSnippetParams.MATCH_FILTER_VALUE])
        for filter in match_filters
    ]
    asserts.assert_equal(
        match_filters,
        [constants.WifiAwareTestConstants.MATCH_FILTER_BYTES],
        f'{subscriber} got unexpected match filter data in discovery'
        f' callback event "{event_name}".',
    )
    return discover_data.data[constants.WifiAwareSnippetParams.PEER_ID]

def wait_data_path_request(
    publisher: android_device.AndroidDevice,
    pub_session_handler: callback_handler_v2.CallbackHandlerV2,
) -> int:
    event_name = constants.DiscoverySessionCallbackMethodType.DATA_PATH_REQUEST_RECEIVED
    event_data = pub_session_handler.waitAndGet(
        event_name=event_name, timeout=_DEFAULT_TIMEOUT
    )
    return event_data.data[constants.WifiAwareSnippetParams.PEER_ID]

def wait_data_path_connect(
        device: android_device.AndroidDevice,
        session_handler: callback_handler_v2.CallbackHandlerV2,
) -> int:
    event_name = constants.DiscoverySessionCallbackMethodType.DATA_PATH_CONNECTED
    event_data = session_handler.waitAndGet(
        event_name=event_name, timeout=_DEFAULT_TIMEOUT
    )
    return event_data.data[constants.WifiAwareSnippetParams.PEER_ID]

def wait_data_path_connection_failure(
        device: android_device.AndroidDevice,
        session_handler: callback_handler_v2.CallbackHandlerV2,
) -> int:
    event_name = constants.DiscoverySessionCallbackMethodType.DATA_PATH_REQUEST_FAILURE
    event_data = session_handler.waitAndGet(
        event_name=event_name, timeout=_DEFAULT_TIMEOUT
    )
    return event_data.data[constants.WifiAwareSnippetParams.PEER_ID]

def wait_data_path_disconnect(
        device: android_device.AndroidDevice,
        session_handler: callback_handler_v2.CallbackHandlerV2,
) -> int:
    event_name = constants.DiscoverySessionCallbackMethodType.DATA_PATH_DISCONNECTED
    event_data = session_handler.waitAndGet(
        event_name=event_name, timeout=_DEFAULT_TIMEOUT
    )
    return event_data.data[constants.WifiAwareSnippetParams.PEER_ID]

def send_msg_through_discovery_session(
    sender: android_device.AndroidDevice,
    sender_discovery_session_handler: callback_handler_v2.CallbackHandlerV2,
    receiver: android_device.AndroidDevice,
    receiver_discovery_session_handler: callback_handler_v2.CallbackHandlerV2,
    discovery_session: str,
    peer_on_sender: int,
    send_message: str,
    send_message_id: int | None = None,
) -> int:
    """Sends a message through a discovery session and verifies receipt.

    Args:
        sender: The Android device controller of the sender.
        sender_discovery_session_handler: The callback handler for the sender's
            discovery session.
        receiver: The Android device controller of the receiver.
        receiver_discovery_session_handler: The callback handler for the
            receiver's discovery session.
        discovery_session: The discovery session ID.
        peer_on_sender: The peer ID of the receiver as seen on the sender.
        send_message: The message to send.
        send_message_id: The message ID. If not provided, a random ID will be
            generated.

    Returns:
        The peer ID of the sender as seen on the receiver.
    """
    send_message_id = send_message_id or random.randint(1000, 5000)
    sender.wifi.wifiAwareSendMessage(
        discovery_session, peer_on_sender, send_message_id, send_message
    )
    message_send_result = sender_discovery_session_handler.waitAndGet(
        event_name=constants.DiscoverySessionCallbackMethodType.MESSAGE_SEND_RESULT,
        timeout=_DEFAULT_TIMEOUT,
    )
    callback_name = message_send_result.data[
        constants.DiscoverySessionCallbackParamsType.CALLBACK_NAME
    ]
    asserts.assert_equal(
        callback_name,
        constants.DiscoverySessionCallbackMethodType.MESSAGE_SEND_SUCCEEDED,
        f'{sender} failed to send message with an unexpected callback.',
    )
    actual_send_message_id = message_send_result.data[
        constants.DiscoverySessionCallbackParamsType.MESSAGE_ID
    ]
    asserts.assert_equal(
        actual_send_message_id,
        send_message_id,
        f'{sender} send message succeeded but message ID mismatched.',
    )
    receive_message_event = receiver_discovery_session_handler.waitAndGet(
        event_name=constants.DiscoverySessionCallbackMethodType.MESSAGE_RECEIVED,
        timeout=_DEFAULT_TIMEOUT,
    )
    received_message_raw = receive_message_event.data[
        constants.WifiAwareSnippetParams.RECEIVED_MESSAGE
    ]
    received_message = bytes(received_message_raw).decode('utf-8')
    asserts.assert_equal(
        received_message,
        send_message,
        f'{receiver} received the message but message content mismatched.',
    )
    return receive_message_event.data[constants.WifiAwareSnippetParams.PEER_ID]


def create_server_socket(
    publisher: android_device.AndroidDevice,
):
    """Creates a server socket listening on a local port."""
    server_accept_handler = publisher.wifi.connectivityServerSocketAccept()
    network_id = server_accept_handler.callback_id
    server_local_port = server_accept_handler.ret_value
    return server_accept_handler, network_id, server_local_port


def request_aware_network(
    ad: android_device.AndroidDevice,
    discovery_session: str,
    peer: int,
    network_id: str,
    network_specifier_params: constants.WifiAwareNetworkSpecifier,
    is_accept_any_peer: bool = False,
) -> callback_handler_v2.CallbackHandlerV2:
    """Sends the command to request a Wi-Fi Aware network.

    This does not wait for the network to be established.

    Args:
        ad: The Android device controller.
        discovery_session: The discovery session ID.
        peer: The ID of the peer to establish a Wi-Fi Aware connection with.
        network_id: The network ID.
        network_specifier_params: The network specifier parameters.
        is_accept_any_peer: Whether to accept any peer. If True, the argument
            peer will be ignored.

    Returns:
        The callback handler for querying the network status.
    """
    network_specifier_parcel = ad.wifi.wifiAwareCreateNetworkSpecifier(
        discovery_session,
        peer,
        is_accept_any_peer,
        network_specifier_params.to_dict(),
    )
    network_request = constants.NetworkRequest(
        transport_type=_TRANSPORT_TYPE_WIFI_AWARE,
        network_specifier_parcel=network_specifier_parcel,
    )
    ad.log.debug('Requesting Wi-Fi Aware network: %r', network_request)
    return ad.wifi.connectivityRequestNetwork(
        network_id,
        network_request.to_dict(),
        _REQUEST_NETWORK_TIMEOUT.total_seconds() * 1000,
    )


def wait_for_aware_network(
    ad: android_device.AndroidDevice,
    request_network_handler: callback_handler_v2.CallbackHandlerV2,
) -> callback_event.CallbackEvent:
    """Waits for and verifies the establishment of a Wi-Fi Aware network.

    Args:
        ad: The Android device controller.
        request_network_handler: The callback handler for requesting network.

    Returns:
        The callback event for network capabilities changed event, providing
        information of the new network connection.
    """
    def is_unavailable_or_capabilities_changed_event(event):
        return event.data[_CALLBACK_NAME] in (
            constants.NetworkCbName.ON_UNAVAILABLE,
            constants.NetworkCbName.ON_CAPABILITIES_CHANGED,
        )
    network_callback_event = request_network_handler.waitForEvent(
        event_name=constants.NetworkCbEventName.NETWORK_CALLBACK,
        predicate=is_unavailable_or_capabilities_changed_event,
        timeout=_DEFAULT_TIMEOUT,
    )
    callback_name = network_callback_event.data[_CALLBACK_NAME]
    if callback_name == constants.NetworkCbName.ON_UNAVAILABLE:
        asserts.fail(
            f'{ad} failed to request the network, got callback'
            f' {callback_name}.'
        )
    elif callback_name == constants.NetworkCbName.ON_CAPABILITIES_CHANGED:
        # `network` is the network whose capabilities have changed.
        network = network_callback_event.data[
            constants.NetworkCbEventKey.NETWORK
        ]
        network_capabilities = network_callback_event.data[
            constants.NetworkCbEventKey.NETWORK_CAPABILITIES
        ]
        asserts.assert_true(
            network and network_capabilities,
            f'{ad} received a null Network or NetworkCapabilities!?.',
        )
        transport_info_class_name = network_callback_event.data[
            constants.NetworkCbEventKey.TRANSPORT_INFO_CLASS_NAME
        ]
        asserts.assert_equal(
            transport_info_class_name,
            constants.AWARE_NETWORK_INFO_CLASS_NAME,
            f'{ad} network capabilities changes but it is not a WiFi Aware'
            ' network.',
        )
        return network_callback_event
    else:
        asserts.fail(
            f'{ad} got unknown request network callback {callback_name}.'
        )


def establish_socket_connection(
    publisher: android_device.AndroidDevice,
    subscriber: android_device.AndroidDevice,
    pub_accept_handler: callback_handler_v2.CallbackHandlerV2,
    network_id: str,
    pub_local_port: int,
):
    """Establishes a socket connection between the publisher and subscriber.

    Args:
        publisher: The publisher.
        subscriber: The subscriber.
        pub_accept_handler: The callback handler returned when the publisher
            called snippet RPC `connectivityServerSocketAccept`.
        network_id: The network ID.
        pub_local_port: The local port of the publisher's server socket.
    """
    subscriber.wifi.connectivityCreateSocketOverWiFiAware(
        network_id, pub_local_port
    )
    pub_accept_event = pub_accept_handler.waitAndGet(
        event_name=constants.SnippetEventNames.SERVER_SOCKET_ACCEPT,
        timeout=_DEFAULT_TIMEOUT,
    )
    is_accept = pub_accept_event.data.get(
        constants.SnippetEventParams.IS_ACCEPT, False
    )
    if not is_accept:
        error = pub_accept_event.data[constants.SnippetEventParams.ERROR]
        asserts.fail(
            f'{publisher} Failed to accept the connection. Error: {error}'
        )
    subscriber.log.info('Subscriber created a socket to the publisher.')


def send_socket_msg(
    sender_ad: android_device.AndroidDevice,
    receiver_ad: android_device.AndroidDevice,
    msg: str,
    network_id: str,
):
    """Sends a message from one device to another and verifies receipt."""
    is_write_socket = sender_ad.wifi.connectivityWriteSocket(network_id, msg)
    asserts.assert_true(
        is_write_socket, f'{sender_ad} Failed to write data to the socket.'
    )
    sender_ad.log.info('Wrote data to the socket.')
    received_message = receiver_ad.wifi.connectivityReadSocket(
        network_id, len(msg)
    )
    asserts.assert_equal(
        received_message,
        msg,
        f'{receiver_ad} received message mismatched.Failure:Expected {msg} but '
        f'got {received_message}.',
    )
    receiver_ad.log.info('Read data from the socket.')
