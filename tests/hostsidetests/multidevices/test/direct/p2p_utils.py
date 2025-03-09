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

from collections.abc import Sequence
import dataclasses
import datetime
import logging

from mobly import asserts
from mobly.snippet import errors
from mobly.controllers.android_device_lib import callback_handler_v2
from mobly.controllers import android_device

from direct import constants

_DEFAULT_TIMEOUT = datetime.timedelta(seconds=45)
_GROUP_OWNER_DISCOVERY_TIMEOUT = datetime.timedelta(seconds=60)


@dataclasses.dataclass
class DeviceState:
    """All objects related to operating p2p snippet RPCs.

    Attributes:
        ad: The Android device controller object.
        p2p_device: The object that represents a Wi-Fi p2p device.
        broadcast_receiver: The object for getting events that represent
            Wi-Fi p2p broadcast intents on device.
        channel_ids: The IDs of all p2p channels initialized by Mobly snippets.
        upnp_response_listeners: Maps channel IDs to UPnP listeners. These
            listeners correspond to UpnpServiceResponseListener on device.
        dns_sd_response_listeners: Maps channel IDs to DNS SD listeners. These
            listeners listen for callback invocation of
            DnsSdServiceResponseListener and DnsSdTxtRecordListener.
    """

    ad: android_device.AndroidDevice
    p2p_device: constants.WifiP2pDevice
    broadcast_receiver: callback_handler_v2.CallbackHandlerV2
    channel_ids: Sequence[int] = dataclasses.field(default_factory=list)
    upnp_response_listeners: dict[int, callback_handler_v2.CallbackHandlerV2] = dataclasses.field(default_factory=dict)
    dns_sd_response_listeners: dict[int, callback_handler_v2.CallbackHandlerV2] = dataclasses.field(default_factory=dict)


def setup_wifi_p2p(ad: android_device.AndroidDevice) -> DeviceState:
    """Sets up Wi-Fi p2p for automation tests on an Android device."""
    broadcast_receiver = _init_wifi_p2p(ad)
    _delete_all_persistent_groups(ad)
    p2p_device = _get_p2p_device(ad)
    asserts.assert_not_equal(
        p2p_device.device_address,
        constants.ANONYMIZED_MAC_ADDRESS,
        f'{ad} failed to get p2p device MAC address, please check permissions '
        'required by API WifiP2pManager#requestConnectionInfo',
    )
    return DeviceState(
        ad=ad,
        p2p_device=p2p_device,
        broadcast_receiver=broadcast_receiver,
        channel_ids=[broadcast_receiver.ret_value],
    )


def _init_wifi_p2p(
    ad: android_device.AndroidDevice,
) -> callback_handler_v2.CallbackHandlerV2:
    """Registers the snippet app with the Wi-Fi p2p framework.

    This must be the first to be called before any p2p operations are performed.

    Args:
        ad: The Android device controller object.

    Returns:
        The broadcast receiver from which you can get snippet events
        corresponding to Wi-Fi p2p intents received on device.
    """
    broadcast_receiver = ad.wifi.wifiP2pInitialize()

    def _is_p2p_enabled(event):
        return (
            event.data[constants.EXTRA_WIFI_STATE]
            == constants.ExtraWifiState.WIFI_P2P_STATE_ENABLED
        )

    # Wait until receiving the "p2p enabled" event. We might receive a
    # "p2p disabled" event before that.
    broadcast_receiver.waitForEvent(
        event_name=constants.WIFI_P2P_STATE_CHANGED_ACTION,
        predicate=_is_p2p_enabled,
        timeout=_DEFAULT_TIMEOUT.total_seconds(),
    )
    return broadcast_receiver


def _capture_p2p_intents(
    ad: android_device.AndroidDevice,
) -> callback_handler_v2.CallbackHandlerV2:
    """Starts capturing Wi-Fi p2p intents and returns the intent receiver."""
    broadcast_receiver = ad.wifi.wifiP2pCaptureP2pIntents()
    return broadcast_receiver


def _delete_all_persistent_groups(
    ad: android_device.AndroidDevice,
) -> None:
    """Deletes all persistent Wi-Fi p2p groups."""
    groups = _request_persistent_group_info(ad)
    ad.log.debug('Wi-Fi p2p persistent groups before delete: %s', groups)
    for group in groups:
        result_data = ad.wifi.wifiP2pDeletePersistentGroup(group.network_id)
        result = result_data[constants.EVENT_KEY_CALLBACK_NAME]
        if result != constants.ACTION_LISTENER_ON_SUCCESS:
            reason = constants.ActionListenerOnFailure(
                result_data[constants.EVENT_KEY_REASON]
            )
            raise RuntimeError(
                'Failed to delete persistent group with network id '
                f'{group.network_id}. Reason: {reason.name}'
            )
    groups = _request_persistent_group_info(ad)
    ad.log.debug('Wi-Fi p2p persistent groups after delete: %s', groups)


def _request_persistent_group_info(
    ad: android_device.AndroidDevice,
) -> Sequence[constants.WifiP2pGroup]:
    """Requests persistent group information."""
    callback_handler = ad.wifi.wifiP2pRequestPersistentGroupInfo()
    event = callback_handler.waitAndGet(
        event_name=constants.ON_PERSISTENT_GROUP_INFO_AVAILABLE,
        timeout=_DEFAULT_TIMEOUT.total_seconds(),
    )
    groups = constants.WifiP2pGroup.from_dict_list(event.data['groupList'])
    return groups


def _get_p2p_device(
    ad: android_device.AndroidDevice,
) -> constants.WifiP2pDevice:
    """Gets the Wi-Fi p2p device information."""
    callback_handler = ad.wifi.wifiP2pRequestDeviceInfo()
    event = callback_handler.waitAndGet(
        event_name=constants.ON_DEVICE_INFO_AVAILABLE,
        timeout=_DEFAULT_TIMEOUT.total_seconds(),
    )
    return constants.WifiP2pDevice.from_dict(
        event.data[constants.EVENT_KEY_P2P_DEVICE]
    )


def init_extra_channel(device: DeviceState) -> int:
  """Initializes an extra p2p channel and returns the channel ID."""
  channel_id = device.ad.wifi.wifiP2pInitExtraChannel()
  device.channel_ids.append(channel_id)
  return channel_id


def discover_p2p_peer(
    requester: DeviceState,
    responder: DeviceState,
) -> constants.WifiP2pDevice:
    """Initiates p2p peer discovery for the requester to find the responder.

    This initiates p2p discovery on both devices and checks that the
    requester can discover responder.

    Returns:
        The peer p2p device discovered on the client.
    """
    requester.ad.log.debug('Discovering Wi-Fi p2p peer %s.', responder.ad)
    # Clear events in broadcast receiver before initiating peer discovery.
    _clear_events(requester, constants.WIFI_P2P_PEERS_CHANGED_ACTION)

    # Initiate peer discovery.
    responder.ad.wifi.wifiP2pDiscoverPeers()
    requester.ad.wifi.wifiP2pDiscoverPeers()

    # Wait until found the p2p peer device with expected MAC address.
    expected_address = responder.p2p_device.device_address

    def _filter_target_p2p_device(event) -> Sequence[constants.WifiP2pDevice]:
        peers = constants.WifiP2pDevice.from_dict_list(
            event.data[constants.EVENT_KEY_PEER_LIST]
        )
        filtered_peers = [
            peer for peer in peers if peer.device_address == expected_address
        ]
        return filtered_peers

    try:
        event = requester.broadcast_receiver.waitForEvent(
            event_name=constants.WIFI_P2P_PEERS_CHANGED_ACTION,
            predicate=lambda event: len(_filter_target_p2p_device(event)) > 0,
            timeout=_DEFAULT_TIMEOUT.total_seconds(),
        )
    except errors.CallbackHandlerTimeoutError as e:
        asserts.fail(
            f'{requester.ad} did not find the responder device. Expected '
            f'responder MAC: {expected_address}.'
        )

    # There should be only one expected p2p peer.
    peers = _filter_target_p2p_device(event)
    if len(peers) == 0:
        asserts.fail(
            f'{requester.ad} did not find the responder device. Expected '
            f'responder MAC: {expected_address}, found event: {event}.'
        )
    if len(peers) > 1:
        asserts.fail(
            f'{requester.ad} found more than one responder device. Expected '
            f'responder MAC: {expected_address}, found event: {event}.'
        )
    return peers[0]


def discover_group_owner(
    client: DeviceState,
    group_owner_address: str,
) -> constants.WifiP2pDevice:
    """Initiates p2p peer discovery for the client to find expected group owner.

    This requires that p2p group has already been established on the group
    owner.

    Args:
        client: The device acts as p2p client.
        group_owner_address: The expected MAC address of the group owner.

    Returns:
        The peer p2p device discovered on the client.
    """
    client.ad.log.debug(
        'Discovering Wi-Fi p2p group owner %s.', group_owner_address
    )
    client.ad.wifi.wifiP2pDiscoverPeers()

    # Wait until found the p2p peer device with expected MAC address. It must
    # be a group owner.
    def _filter_target_group_owner(event) -> Sequence[constants.WifiP2pDevice]:
        peers = constants.WifiP2pDevice.from_dict_list(
            event.data[constants.EVENT_KEY_PEER_LIST]
        )
        filtered_peers = [
            peer
            for peer in peers
            if peer.device_address == group_owner_address
            and peer.is_group_owner
        ]
        return filtered_peers

    try:
        event = client.broadcast_receiver.waitForEvent(
            event_name=constants.WIFI_P2P_PEERS_CHANGED_ACTION,
            predicate=lambda event: len(_filter_target_group_owner(event)) > 0,
            timeout=_GROUP_OWNER_DISCOVERY_TIMEOUT.total_seconds(),
        )
    except errors.CallbackHandlerTimeoutError as e:
        asserts.fail(
            f'{client.ad} did not find the group owner device. Expected group '
            f'owner MAC: {group_owner_address}.'
        )

    # There should be only one expected p2p peer.
    peers = _filter_target_group_owner(event)
    if len(peers) == 0:
        asserts.fail(
            f'{client.ad} did not find the group owner device. Expected group '
            f'owner MAC: {group_owner_address}, got event: {event}.'
        )
    if len(peers) > 1:
        asserts.fail(
            f'{client.ad} found more than one group owner devices. Expected '
            f'group owner MAC: {group_owner_address}, got event: {event}.'
        )
    return peers[0]


def create_group(
    device: DeviceState, config: constants.WifiP2pConfig | None = None
):
    """Creates a Wi-Fi p2p group on the given device."""
    _clear_events(device, constants.WIFI_P2P_CONNECTION_CHANGED_ACTION)

    config = config.to_dict() if config else None
    device.ad.wifi.wifiP2pCreateGroup(config)

    # Wait until groupFormed=True
    _wait_connection_notice(device.broadcast_receiver)


def p2p_connect(
    requester: DeviceState,
    responder: DeviceState,
    config: constants.WifiP2pConfig,
) -> None:
    """Establishes Wi-Fi p2p connection with WPS configuration.

    This method instructs the requester to initiate a connection request and the
    responder to accept the connection. It then verifies the connection status
    on both devices.

    Args:
        requester: The requester device.
        responder: The responder device.
        config: The Wi-Fi p2p configuration.
    """
    logging.info(
        'Establishing a p2p connection through p2p configuration %s.', config
    )

    # Clear events in broadcast receiver.
    _clear_events(requester, constants.WIFI_P2P_PEERS_CHANGED_ACTION)
    _clear_events(requester, constants.WIFI_P2P_CONNECTION_CHANGED_ACTION)
    _clear_events(responder, constants.WIFI_P2P_PEERS_CHANGED_ACTION)
    _clear_events(responder, constants.WIFI_P2P_CONNECTION_CHANGED_ACTION)

    requester.ad.wifi.wifiP2pConnect(config.to_dict())
    requester.ad.log.info('Sent P2P connect invitation to responder.')
    # Connect with WPS config requires user inetraction through UI.
    if config.wps_setup == constants.WpsInfo.PBC:
        responder.ad.wifi.wifiP2pAcceptInvitation(
            requester.p2p_device.device_name
        )
        responder.ad.log.info('Accepted connect invitation.')
    elif config.wps_setup == constants.WpsInfo.DISPLAY:
        pin = requester.ad.wifi.wifiP2pGetPinCode(
            responder.p2p_device.device_name
        )
        requester.ad.log.info('p2p connection PIN code: %s', pin)
        responder.ad.wifi.wifiP2pEnterPin(pin, requester.p2p_device.device_name)
        responder.ad.log.info('Enetered PIN code.')
    elif config.wps_setup is not None:
        asserts.fail(f'Unsupported WPS configuration: {config.wps_setup}')

    # Check p2p status on requester.
    _wait_connection_notice(requester.broadcast_receiver)
    _wait_peer_connected(
        requester.broadcast_receiver,
        responder.p2p_device.device_address,
    )
    requester.ad.log.debug(
        'Connected with device %s through wifi p2p.',
        responder.p2p_device.device_address,
    )

    # Check p2p status on responder.
    _wait_connection_notice(responder.broadcast_receiver)
    _wait_peer_connected(
        responder.broadcast_receiver,
        requester.p2p_device.device_address,
    )
    responder.ad.log.debug(
        'Connected with device %s through wifi p2p.',
        requester.p2p_device.device_address,
    )
    logging.info('Established wifi p2p connection.')


def _wait_peer_connected(
    broadcast_receiver: callback_handler_v2.CallbackHandlerV2, peer_address: str
):
    """Waits for event that indicates expected Wi-Fi p2p peer is connected."""

    def _is_peer_connected(event):
        devices = constants.WifiP2pDevice.from_dict_list(event.data['peerList'])
        for device in devices:
            if (
                device.device_address == peer_address
                and device.status == constants.WifiP2pDeviceStatus.CONNECTED
            ):
                return True
        return False

    broadcast_receiver.waitForEvent(
        event_name=constants.WIFI_P2P_PEERS_CHANGED_ACTION,
        predicate=_is_peer_connected,
        timeout=_DEFAULT_TIMEOUT.total_seconds(),
    )


def _wait_connection_notice(
    broadcast_receiver: callback_handler_v2.CallbackHandlerV2,
):
    """Waits for event that indicates a p2p connection is established."""

    def _is_group_formed(event):
        try:
            p2p_info = constants.WifiP2pInfo.from_dict(
                event.data[constants.EVENT_KEY_P2P_INFO]
            )
            return p2p_info.group_formed
        except KeyError:
            return False

    event = broadcast_receiver.waitForEvent(
        event_name=constants.WIFI_P2P_CONNECTION_CHANGED_ACTION,
        predicate=_is_group_formed,
        timeout=_DEFAULT_TIMEOUT.total_seconds(),
    )


def remove_group_and_verify_disconnected(
    requester: DeviceState,
    responder: DeviceState,
    is_group_negotiation: bool,
):
    """Stops p2p connection and verifies disconnection status on devices."""
    logging.info('Stopping wifi p2p connection.')

    # Clear events in broadcast receiver.
    _clear_events(requester, constants.WIFI_P2P_CONNECTION_CHANGED_ACTION)
    _clear_events(requester, constants.ON_DEVICE_INFO_AVAILABLE)
    _clear_events(responder, constants.WIFI_P2P_CONNECTION_CHANGED_ACTION)
    _clear_events(responder, constants.ON_DEVICE_INFO_AVAILABLE)

    # Requester initiates p2p group removal.
    requester.ad.wifi.wifiP2pRemoveGroup()

    # Check p2p status on requester.
    _wait_disconnection_notice(requester.broadcast_receiver)
    _wait_peer_disconnected(
        requester.broadcast_receiver, responder.p2p_device.device_address
    )
    requester.ad.log.debug(
        'Disconnected with device %s through wifi p2p.',
        responder.p2p_device.device_address,
    )

    # Check p2p status on responder.
    if is_group_negotiation:
        _wait_disconnection_notice(responder.broadcast_receiver)
    _wait_peer_disconnected(
        responder.broadcast_receiver, requester.p2p_device.device_address
    )
    responder.ad.log.debug(
        'Disconnected with device %s through wifi p2p.',
        requester.p2p_device.device_address,
    )

    logging.info('Stopped wifi p2p connection.')


def _wait_disconnection_notice(broadcast_receiver):
    """Waits for event that indicates the p2p connection is disconnected."""

    def _is_disconnect_event(event):
        info = constants.WifiP2pInfo.from_dict(
            event.data[constants.EVENT_KEY_P2P_INFO]
        )
        return not info.group_formed

    broadcast_receiver.waitForEvent(
        event_name=constants.WIFI_P2P_CONNECTION_CHANGED_ACTION,
        predicate=_is_disconnect_event,
        timeout=_DEFAULT_TIMEOUT.total_seconds(),
    )


def _wait_peer_disconnected(broadcast_receiver, target_address):
    """Waits for event that indicates current Wi-Fi p2p peer is disconnected."""

    def _is_peer_disconnect_event(event):
        devices = constants.WifiP2pDevice.from_dict_list(
            event.data[constants.EVENT_KEY_PEER_LIST]
        )
        for device in devices:
            if device.device_address == target_address:
                return device.status != constants.WifiP2pDeviceStatus.CONNECTED
        # Target device not found also means it is disconnected.
        return True

    broadcast_receiver.waitForEvent(
        event_name=constants.WIFI_P2P_PEERS_CHANGED_ACTION,
        predicate=_is_peer_disconnect_event,
        timeout=_DEFAULT_TIMEOUT.total_seconds(),
    )


def _clear_events(device: DeviceState, event_name):
    """Clears the events with the given name in the broadcast receiver."""
    all_events = device.broadcast_receiver.getAll(event_name)
    device.ad.log.debug(
        'Cleared %d events of event name %s', len(all_events), event_name
    )


def teardown_wifi_p2p(ad: android_device.AndroidDevice):
    """Destroys all resources initialized in `_setup_wifi_p2p`."""
    try:
        ad.wifi.wifiP2pStopPeerDiscovery()
        ad.wifi.wifiP2pCancelConnect()
        ad.wifi.wifiP2pRemoveGroup()
    finally:
        # Make sure to call `p2pClose`, otherwise `_setup_wifi_p2p` won't be
        # able to run again.
        ad.wifi.p2pClose()


def add_upnp_local_service(device: DeviceState, config: dict):
    """Adds p2p local Upnp service."""
    device.ad.wifi.wifiP2pAddUpnpLocalService(
        config['uuid'], config['device'], config['services']
    )


def add_bonjour_local_service(device: DeviceState, config: dict):
    """Adds p2p local Bonjour service."""
    device.ad.wifi.wifiP2pAddBonjourLocalService(
        config['instance_name'], config['service_type'], config['txt_map']
    )


def set_upnp_response_listener(
    device: DeviceState, channel_id: int | None = None
):
    """Set response listener for Upnp service."""
    channel_id = channel_id or device.channel_ids[0]
    upnp_response_listener = device.ad.wifi.wifiP2pSetUpnpResponseListener(
        channel_id
    )
    device.upnp_response_listeners[channel_id] = upnp_response_listener


def set_dns_sd_response_listeners(
    device: DeviceState, channel_id: int | None = None
):
    """Set response listener for Bonjour service."""
    channel_id = channel_id or device.channel_ids[0]
    listener = device.ad.wifi.wifiP2pSetDnsSdResponseListeners(channel_id)
    device.dns_sd_response_listeners[channel_id] = listener


def reset_p2p_service_state(
    ad: android_device.AndroidDevice, channel_id
):
    """Clears all p2p service related states on device."""
    ad.wifi.wifiP2pClearServiceRequests(channel_id)
    ad.wifi.wifiP2pUnsetDnsSdResponseListeners(channel_id)
    ad.wifi.wifiP2pUnsetUpnpResponseListener(channel_id)
    ad.wifi.wifiP2pClearLocalServices(channel_id)


def check_discovered_services(
    requester: DeviceState,
    expected_src_device_address: str,
    expected_dns_sd_sequence: Sequence[Sequence[str, dict[str, str]]],
    expected_dns_txt_sequence: Sequence[Sequence[str, str]],
    expected_upnp_sequence: Sequence[str],
    channel_id: int | None = None,
):
    """Checks the discovered service responses are as expected.

    This checks all services discovered within the timeout `_DEFAULT_TIMEOUT`.
    If any expected service sequence is empty, this checks that no such service
    are received within timeout.

    Args:
        device: The device that is discovering services.
        expected_src_device_address: Only check services advertised from this
            device address.
        expected_dns_sd_sequence: Expected DNS SD responses.
        expected_dns_txt_sequence: Expected DNS SD TXT records.
        expected_upnp_sequence: Expected UPnP services.
        channel_id: The channel to check for expected services.
    """
    channel_id = channel_id or requester.channel_ids[0]
    start_time = datetime.datetime.now()
    timeout = _DEFAULT_TIMEOUT
    check_discovered_dns_sd_response(
        requester,
        expected_responses=expected_dns_sd_sequence,
        expected_src_device_address=expected_src_device_address,
        channel_id=channel_id,
        timeout=timeout,
    )
    remaining_timeout = timeout - (datetime.datetime.now() - start_time)
    check_discovered_dns_sd_txt_record(
        requester,
        expected_records=expected_dns_txt_sequence,
        expected_src_device_address=expected_src_device_address,
        channel_id=channel_id,
        timeout=remaining_timeout,
    )
    remaining_timeout = timeout - (datetime.datetime.now() - start_time)
    check_discovered_upnp_services(
        requester,
        expected_services=expected_upnp_sequence,
        expected_src_device_address=expected_src_device_address,
        channel_id=channel_id,
        timeout=remaining_timeout,
    )


def check_discovered_upnp_services(
    device: DeviceState,
    expected_services: Sequence[str],
    expected_src_device_address: str,
    channel_id: int | None = None,
    timeout: datetime.timedelta = _DEFAULT_TIMEOUT,
):
    """Check discovered Upnp services.

    If no services are expected, check that no UPnP service appear within
    timeout. Otherwise, wait for all expected services within timeout.

    This assumes that Upnp service listener is set by
    `set_upnp_response_listener`.

    Args:
        device: The device that is discovering Upnp services.
        expected_services: The expected Upnp services.
        expected_src_device_address: This only checks services that are from the
            expected source device.
        channel_id: The channel to check for expected services.
        timeout: The wait timeout.
    """
    channel_id = channel_id or device.channel_ids[0]
    callback_handler = device.upnp_response_listeners[channel_id]
    if len(expected_services) == 0:
        _check_no_discovered_service(
            ad=device.ad,
            callback_handler=callback_handler,
            event_name=constants.ON_UPNP_SERVICE_AVAILABLE,
            expected_src_device_address=expected_src_device_address,
            timeout=timeout,
        )
        return

    expected_services = set(expected_services)
    def _all_service_received(event):
        nonlocal expected_services
        src_device = constants.WifiP2pDevice.from_dict(
            event.data['sourceDevice']
        )
        if src_device.device_address != expected_src_device_address:
            return False
        for service in event.data['serviceList']:
            if service in expected_services:
                device.ad.log.debug('Received upnp services: %s', service)
                expected_services.remove(service)
        return len(expected_services) == 0

    device.ad.log.debug('Waiting for UpnP services: %s', expected_services)
    # Set to a small timeout to allow pulling all received events
    if timeout.total_seconds() <= 1:
        timeout = datetime.timedelta(seconds=1)
    try:
        callback_handler.waitForEvent(
            event_name=constants.ON_UPNP_SERVICE_AVAILABLE,
            predicate=_all_service_received,
            timeout=timeout.total_seconds(),
        )
    except errors.CallbackHandlerTimeoutError as e:
        asserts.fail(
            f'{device.ad} Timed out waiting for services: {expected_services}'
        )


def check_discovered_dns_sd_response(
    device: DeviceState,
    expected_responses: Sequence[Sequence[str, str]],
    expected_src_device_address: str,
    channel_id: int | None = None,
    timeout: datetime.timedelta = _DEFAULT_TIMEOUT,
):
    """Check discovered DNS SD responses.

    If no responses are expected, check that no DNS SD response appear within
    timeout. Otherwise, wait for all expected responses within timeout.

    This assumes that Bonjour service listener is set by
    `set_dns_sd_response_listeners`.

    Args:
        device: The device that is discovering DNS SD responses.
        expected_responses: The expected DNS SD responses.
        expected_src_device_address: This only checks services that are from the
            expected source device.
        channel_id: The channel to check for expected responses.
        timeout: The wait timeout.
    """
    channel_id = channel_id or device.channel_ids[0]
    callback_handler = device.dns_sd_response_listeners[channel_id]
    if not expected_responses:
        _check_no_discovered_service(
            device.ad,
            callback_handler=callback_handler,
            event_name=constants.ON_DNS_SD_SERVICE_AVAILABLE,
            expected_src_device_address=expected_src_device_address,
            timeout=timeout,
        )
        return

    expected_responses = list(expected_responses)

    def _all_service_received(event):
        nonlocal expected_responses
        src_device = constants.WifiP2pDevice.from_dict(
            event.data['sourceDevice']
        )
        if src_device.device_address != expected_src_device_address:
            return False
        registration_type = event.data['registrationType']
        instance_name = event.data['instanceName']
        service_tuple = (instance_name, registration_type)
        device.ad.log.debug('Received DNS SD response: %s', service_tuple)
        if service_tuple in expected_responses:
            expected_responses.remove(service_tuple)
        return len(expected_responses) == 0

    device.ad.log.debug('Waiting for DNS SD services: %s', expected_responses)
    # Set to a small timeout to allow pulling all received events
    if timeout.total_seconds() <= 1:
        timeout = datetime.timedelta(seconds=1)
    try:
        callback_handler.waitForEvent(
            event_name=constants.ON_DNS_SD_SERVICE_AVAILABLE,
            predicate=_all_service_received,
            timeout=timeout.total_seconds(),
        )
    except errors.CallbackHandlerTimeoutError as e:
        asserts.fail(
            f'{device.ad} Timed out waiting for services: {expected_responses}'
        )


def check_discovered_dns_sd_txt_record(
    device: DeviceState,
    expected_records: Sequence[Sequence[str, dict[str, str]]],
    expected_src_device_address: str,
    channel_id: int | None = None,
    timeout: datetime.timedelta = _DEFAULT_TIMEOUT,
):
    """Check discovered DNS SD TXT records.

    If no records are expected, check that no DNS SD TXT record appear within
    timeout. Otherwise, wait for all expected records within timeout.

    This assumes that Bonjour service listener is set by
    `set_dns_sd_response_listeners`.

    Args:
        device: The device that is discovering DNS SD TXT records.
        expected_records: The expected DNS SD TXT records.
        expected_src_device_address: This only checks services that are from the
            expected source device.
        channel_id: The channel to check for expected records.
        timeout: The wait timeout.
    """
    channel_id = channel_id or device.channel_ids[0]
    idx = device.channel_ids.index(channel_id)
    callback_handler = device.dns_sd_response_listeners[idx]
    if not expected_records:
        _check_no_discovered_service(
            device.ad,
            callback_handler=callback_handler,
            event_name=constants.ON_DNS_SD_TXT_RECORD_AVAILABLE,
            expected_src_device_address=expected_src_device_address,
            timeout=timeout,
        )
        return

    expected_records = list(expected_records)
    device.ad.log.debug('Expected DNS SD TXT records: %s', expected_records)
    def _all_service_received(event):
        nonlocal expected_records
        src_device = constants.WifiP2pDevice.from_dict(
            event.data['sourceDevice']
        )
        if src_device.device_address != expected_src_device_address:
            return False
        full_domain_name = event.data['fullDomainName']
        txt_record_map = event.data['txtRecordMap']
        record_to_remove = (full_domain_name, txt_record_map)
        device.ad.log.debug('Received DNS SD TXT record: %s', record_to_remove)
        if record_to_remove in expected_records:
            expected_records.remove(record_to_remove)
        return len(expected_records) == 0

    device.ad.log.debug('Waiting for DNS SD TXT records: %s', expected_records)
    # Set to a small timeout to allow pulling all received events
    if timeout.total_seconds() <= 1:
        timeout = datetime.timedelta(seconds=1)
    try:
        callback_handler.waitForEvent(
            event_name=constants.ON_DNS_SD_TXT_RECORD_AVAILABLE,
            predicate=_all_service_received,
            timeout=timeout.total_seconds(),
        )
    except errors.CallbackHandlerTimeoutError as e:
        asserts.fail(
            f'{device.ad} Timed out waiting for services: {expected_records}'
        )


def _check_no_discovered_service(
    ad: android_device.AndroidDevice,
    callback_handler: callback_handler_v2.CallbackHandlerV2,
    event_name: str,
    expected_src_device_address: str,
    timeout: datetime.timedelta = _DEFAULT_TIMEOUT,
):
    """Checks that no service is received from the specified source device."""
    def _is_expected_event(event):
        src_device = constants.WifiP2pDevice.from_dict(
            event.data['sourceDevice']
        )
        return src_device.device_address == expected_src_device_address

    # Set to a small timeout to allow pulling all received events
    if timeout.total_seconds() <= 1:
        timeout = datetime.timedelta(seconds=1)
    try:
        event = callback_handler.waitForEvent(
            event_name=event_name,
            predicate=_is_expected_event,
            timeout=timeout.total_seconds(),
        )
    except errors.CallbackHandlerTimeoutError as e:
        # Timeout error is expected as there should not be any qualified service
        return
    asserts.assert_is_none(
        event,
        f'{ad} should not discover p2p service. Discovered: {event}',
    )
