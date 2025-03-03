#  Copyright (C) 2023 The Android Open Source Project
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

from __future__ import annotations

from collections.abc import Sequence
import dataclasses
import enum
import logging
from typing import Any, Optional, Union

WIFI_SNIPPET_PACKAGE_NAME = "com.google.snippet.wifi"
WIFI_DIRECT_SNIPPET_PACKAGE_NAME = 'com.google.snippet.wifi.direct'

ACTION_LISTENER_CALLBACK_EVENT = "WifiP2pManagerActionListenerCallback"
ACTION_LISTENER_ON_SUCCESS = 'onSuccess'
ACTION_LISTENER_ON_FAILURE = 'onFailure'
ACTION_LISTENER_FAILURE_REASON = 'reason'

EVENT_KEY_CALLBACK_NAME = 'callbackName'
EVENT_KEY_REASON = 'reason'
EVENT_KEY_P2P_DEVICE = 'p2pDevice'
EVENT_KEY_P2P_INFO = 'p2pInfo'
EVENT_KEY_P2P_GROUP = 'p2pGroup'
EVENT_KEY_PEER_LIST = 'peerList'

EXTRA_WIFI_P2P_GROUP = 'p2pGroupInfo'
EXTRA_WIFI_STATE = 'wifi_p2p_state'

ON_CONNECTION_INFO_AVAILABLE = 'WifiP2pOnConnectionInfoAvailable'
ON_DEVICE_INFO_AVAILABLE = 'WifiP2pOnDeviceInfoAvailable'
ON_PERSISTENT_GROUP_INFO_AVAILABLE = 'onPersistentGroupInfoAvailable'
ON_UPNP_SERVICE_AVAILABLE = 'onUpnpServiceAvailable'
ON_DNS_SD_SERVICE_AVAILABLE = 'onDnsSdServiceAvailable'
ON_DNS_SD_TXT_RECORD_AVAILABLE = 'onDnsSdTxtRecordAvailable'
WIFI_P2P_CREATING_GROUP = 'CREATING_GROUP'
WIFI_P2P_CONNECTION_CHANGED_ACTION = (
    'android.net.wifi.p2p.CONNECTION_STATE_CHANGE'
)
WIFI_P2P_DISCOVERY_CHANGED_ACTION = (
    'android.net.wifi.p2p.DISCOVERY_STATE_CHANGE'
)
WIFI_P2P_PEERS_CHANGED_ACTION = 'android.net.wifi.p2p.PEERS_CHANGED'
WIFI_P2P_STATE_CHANGED_ACTION = 'android.net.wifi.p2p.STATE_CHANGED'
WIFI_P2P_THIS_DEVICE_CHANGED_ACTION = 'android.net.wifi.p2p.THIS_DEVICE_CHANGED'

ANONYMIZED_MAC_ADDRESS = '02:00:00:00:00:00'


@enum.unique
class ActionListenerOnFailure(enum.IntEnum):
  """Indicates the failure reason of the initiation of the action.

  https://developer.android.com/reference/android/net/wifi/p2p/WifiP2pManager.ActionListener#onFailure(int)
  """

  ERROR = 0
  P2P_UNSUPPORTED = 1
  BUSY = 2
  NO_SERVICE_REQUESTS = 3


@enum.unique
class Band(enum.IntEnum):
  """Indicates the band of the operating frequency.

  https://developer.android.com/reference/android/net/wifi/p2p/WifiP2pConfig#getGroupOwnerBand()
  """

  GROUP_OWNER_BAND_AUTO = 0
  GROUP_OWNER_BAND_2GHZ = 1
  GROUP_OWNER_BAND_5GHZ = 2


@enum.unique
class IpProvisioningMode(enum.IntEnum):
  """Indicates the IP provisioning mode.

  https://developer.android.com/reference/android/net/wifi/p2p/WifiP2pConfig#getGroupClientIpProvisioningMode()
  """

  GROUP_CLIENT_IP_PROVISIONING_MODE_IPV4_DHCP = 0
  GROUP_CLIENT_IP_PROVISIONING_MODE_IPV6_LINK_LOCAL = 1


@enum.unique
class ExtraWifiState(enum.IntEnum):
  """Indicates whether Wi-Fi p2p is enabled or disabled.

  https://developer.android.com/reference/android/net/wifi/p2p/WifiP2pManager#EXTRA_WIFI_STATE
  """

  WIFI_P2P_STATE_UNKNOWN = 0
  WIFI_P2P_STATE_DISABLED = 1
  WIFI_P2P_STATE_ENABLED = 2


@enum.unique
class WifiP2pDeviceStatus(enum.IntEnum):
  """Represents status code for WifiP2pDevice.and

  https://developer.android.com/reference/android/net/wifi/p2p/WifiP2pDevice#constants_1
  """

  CONNECTED = 0
  INVITED = 1
  FAILED = 2
  AVAILABLE = 3
  UNAVAILABLE = 4


@enum.unique
class WpsInfo(enum.IntEnum):
  """Represents Wi-Fi Protected Setup.

  https://developer.android.com/reference/android/net/wifi/WpsInfo
  """

  PBC = 0
  DISPLAY = 1
  KEYPAD = 2
  LABEL = 3
  INVALID = 4


@dataclasses.dataclass(frozen=True)
class WifiP2pConfig:
  """Represents a Wi-Fi P2p configuration for setting up a connection.

  https://developer.android.com/reference/android/net/wifi/p2p/WifiP2pConfig
  """

  persistent_mode: Optional[bool] = None
  device_address: Optional[str] = None
  group_client_ip_provisioning_mode: Optional[IpProvisioningMode] = None
  group_operating_band: Optional[Band] = None
  group_operating_frequency: Optional[int] = None
  network_name: Optional[str] = None
  passphrase: Optional[str] = None
  wps_setup: WpsInfo | None = None

  def to_dict(self) -> dict[str, Union[bool, int, str]]:
    """Converts this WifiP2pConfig to a dictionary."""
    return {
        k: v.value if isinstance(v, enum.Enum) else v
        for k, v in self.__dict__.items()
        if v is not None
    }


@dataclasses.dataclass
class WifiP2pDevice:
  """Represents a Wi-Fi p2p device.

  https://developer.android.com/reference/android/net/wifi/p2p/WifiP2pDevice
  """

  device_name: str
  device_address: str
  is_group_owner: bool
  status: int
  primary_device_type: str
  secondary_device_type: str

  @classmethod
  def from_dict(cls, device: dict[str, Any]) -> WifiP2pDevice:
    """Generates a WifiP2pDevice object from a dictionary."""
    logging.debug(
        "Converting following snippet event data to WifiP2pDevice: %s",
        device,
    )
    return WifiP2pDevice(
        device_name=device["deviceName"],
        device_address=device["deviceAddress"],
        is_group_owner=device["isGroupOwner"],
        status=device["status"],
        primary_device_type=device["primaryDeviceType"],
        secondary_device_type=device["secondaryDeviceType"],
    )

  @classmethod
  def from_dict_list(
      cls, devices: list[dict[str, Any]]
  ) -> Sequence[WifiP2pDevice]:
    """Generates WifiP2pDevice objects from a list of dictionary."""
    return [cls.from_dict(device) for device in devices]


@dataclasses.dataclass(frozen=True)
class WifiP2pInfo:
  """Represents a connection information about a Wi-Fi p2p group.

  https://developer.android.com/reference/android/net/wifi/p2p/WifiP2pInfo
  """

  group_formed: bool
  group_owner_address: str
  is_group_owner: bool

  @classmethod
  def from_dict(cls, info: dict[str, Any]) -> WifiP2pInfo:
    """Generates a WifiP2pInfo object from a dictionary."""
    return WifiP2pInfo(
        group_formed=info["groupFormed"],
        group_owner_address=info["groupOwnerAddress"],
        is_group_owner=info["isGroupOwner"],
    )


@dataclasses.dataclass(frozen=True)
class WifiP2pGroup:
  """Represents a Wi-Fi p2p group.

  https://developer.android.com/reference/android/net/wifi/p2p/WifiP2pGroup
  """

  frequency: int
  interface: str
  network_id: int
  network_name: str
  owner: WifiP2pDevice
  passphrase: str
  is_group_owner: bool

  @classmethod
  def from_dict(cls, group: dict[str, Any]) -> WifiP2pGroup:
    """Generates a WifiP2pGroup object from a dictionary."""
    return WifiP2pGroup(
        frequency=group['frequency'],
        interface=group['interface'],
        network_id=group['networkId'],
        network_name=group['networkName'],
        owner=WifiP2pDevice.from_dict(group['owner']),
        passphrase=group['passphrase'],
        is_group_owner=group['isGroupOwner'],
    )

  @classmethod
  def from_dict_list(
      cls, groups: list[dict[str, Any]]
  ) -> Sequence[WifiP2pGroup]:
    """Generates WifiP2pGroup objects from a list of dictionary."""
    return [cls.from_dict(group) for group in groups]


@enum.unique
class ServiceType(enum.IntEnum):
    """Indicates the type of Wi-Fi p2p services.

    https://developer.android.com/reference/android/net/wifi/p2p/nsd/WifiP2pServiceInfo#summary
    """

    ALL = 0
    BONJOUR = 1
    UPNP = 2
    WS_DISCOVERY = 3


class ServiceData:
    """Constants for Wi-Fi p2p services."""

    # Service configurations.
    # Configuration for Bonjour IPP local service.
    IPP_DNS_SD = (('MyPrinter', '_ipp._tcp.local.'),)
    AFP_DNS_SD = (('Example', '_afpovertcp._tcp.local.'),)
    ALL_DNS_SD = (
        ('MyPrinter', '_ipp._tcp.local.'),
        ('Example', '_afpovertcp._tcp.local.'),
    )

    IPP_DNS_TXT = (
        ('myprinter._ipp._tcp.local.', {
            'txtvers': '1',
            'pdl': 'application/postscript'
        }),
    )
    AFP_DNS_TXT = (('example._afpovertcp._tcp.local.', {}),)
    ALL_DNS_TXT = (('myprinter._ipp._tcp.local.',
                    {
                        'txtvers': '1',
                        'pdl': 'application/postscript'
                    }
                    ), ('example._afpovertcp._tcp.local.', {}),)

    # Configuration for IPP local service.
    DEFAULT_IPP_SERVICE_CONF = {
        'instance_name': 'MyPrinter',
        'service_type': '_ipp._tcp',
        'txt_map': {
            'txtvers': '1',
            'pdl': 'application/postscript'
        },
    }
    # Configuration for AFP local service.
    DEFAULT_AFP_SERVICE_CONF = {
        'instance_name': 'Example',
        'service_type': '_afpovertcp._tcp',
        'txt_map': {},
    }
    # Configuration for UPnP MediaRenderer local service.
    DEFAULT_UPNP_SERVICE_CONF = {
        'uuid': '6859dede-8574-59ab-9332-123456789011',
        'device': 'urn:schemas-upnp-org:device:MediaRenderer:1',
        'services': [
            'urn:schemas-upnp-org:service:AVTransport:1',
            'urn:schemas-upnp-org:service:ConnectionManager:1',
        ],
    }

    # Expected services to be discovered.
    ALL_UPNP_SERVICES = (
        'uuid:6859dede-8574-59ab-9332-123456789011',
        'uuid:6859dede-8574-59ab-9332-123456789011::upnp:rootdevice',
        (
            'uuid:6859dede-8574-59ab-9332-123456789011::urn:schemas-upnp-org:'
            'device:MediaRenderer:1'
        ),
        (
            'uuid:6859dede-8574-59ab-9332-123456789011::urn:schemas-upnp-org:'
            'service:AVTransport:1'
        ),
        (
            'uuid:6859dede-8574-59ab-9332-123456789011::urn:schemas-upnp-org:'
            'service:ConnectionManager:1'
        ),
    )

    UPNP_ROOT_DEVICE = ('uuid:6859dede-8574-59ab-9332-123456789011::upnp:rootdevice',)


class WifiP2pManagerConstants:
    """Constants for Wi-Fi p2p manager.

    https://developer.android.com/reference/android/net/wifi/p2p/WifiP2pManager#NO_SERVICE_REQUESTS
    """
    NO_SERVICE_REQUESTS = 3
