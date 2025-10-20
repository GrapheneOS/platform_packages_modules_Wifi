"""Constants for Wi-Fi tests."""

import dataclasses
import datetime
import enum
from typing import Any


BSSID_MASK = 'ff:ff:ff:ff:ff:00'
WIFI_SCAN_TIMEOUT = datetime.timedelta(seconds=30)
REQUEST_NETWORK_TIMEOUT = datetime.timedelta(minutes=3)
REQUEST_NETWORK_TIMEOUT_MS = int(
    REQUEST_NETWORK_TIMEOUT.total_seconds() * 1000
)
WIFI_LOST_TIMEOUT = datetime.timedelta(seconds=30)
WIFI_CONTINUOUSLY_CHECK_TIMEOUT = datetime.timedelta(seconds=40)
POST_CONNECT_BROADCAST_TIMEOUT = datetime.timedelta(seconds=30)
WIFI_EXPECTED_UNCONNECTION_TIMEOUT = datetime.timedelta(seconds=30)
WIFI_EXPECTED_UNCONNECTION_TIMEOUT_MS = int(
    WIFI_EXPECTED_UNCONNECTION_TIMEOUT.total_seconds() * 1000
)
CAPABILITIES_CHANGED_FOR_METERED_TIMEOUT = datetime.timedelta(seconds=80)
CAPABILITIES_CHANGED_TIMEOUT = datetime.timedelta(seconds=15)
CALLBACK_TIMEOUT = datetime.timedelta(seconds=40)


@enum.unique
class PatternType(enum.IntEnum):
  """Pattern type.

  https://developer.android.com/reference/android/os/PatternMatcher#constants_1
  """

  PATTERN_LITERAL = 0
  PATTERN_PREFIX = 1
  PATTERN_SIMPLE_GLOB = 2
  PATTERN_ADVANCED_GLOB = 3
  PATTERN_SUFFIX = 4


@dataclasses.dataclass(frozen=True)
class PatternMatcher:
  """Pattern matcher."""

  pattern: str
  pattern_type: PatternType

  def to_dict(self) -> dict[str, str | PatternType]:
    """Returns a dict representation of PatternMatcher."""
    return {'pattern': self.pattern, 'pattern_type': self.pattern_type}


@dataclasses.dataclass(frozen=True)
class BssidPattern:
  """BSSID pattern."""

  bssid: str
  bssid_mask: str

  def to_dict(self) -> dict[str, str]:
    """Returns a dict representation of BssidPattern."""
    result = {}
    if self.bssid:
      result['bssid'] = self.bssid
    if self.bssid_mask:
      result['bssid_mask'] = self.bssid_mask
    return result


@dataclasses.dataclass(frozen=True)
class NetworkSpecifier:
  """Network specification.

  https://developer.android.com/reference/android/net/wifi/WifiNetworkSuggestion.Builder
  """

  ssid: str | None = None
  bssid: str | None = None
  ssid_pattern: PatternMatcher | None = None
  bssid_pattern: BssidPattern | None = None
  psk: str | None = None

  def to_dict(self) -> dict[str, Any]:
    """Returns a dict representation of NetworkSpecifier."""
    result = {}
    if self.ssid is not None:
      result['ssid'] = self.ssid
    if self.bssid is not None:
      result['bssid'] = self.bssid
    if self.ssid_pattern is not None:
      result['ssid_pattern'] = self.ssid_pattern.to_dict()
    if self.bssid_pattern is not None:
      result['bssid_pattern'] = self.bssid_pattern.to_dict()
    if self.psk:
      result['psk'] = self.psk
    return result


@dataclasses.dataclass(frozen=False)
class NetworkSuggestion:
  """Network Suggestion.

  https://developer.android.com/reference/android/net/wifi/WifiNetworkSuggestion.Builder
  """

  ssid: str
  bssid: str | None = None
  psk: str | None = None
  is_hidden_ssid: bool | None = None
  is_metered: bool | None = None
  is_app_interaction_required: bool | None = None

  def to_dict(self) -> dict[str, str | int | bool]:
    """Returns a dict representation of NetworkSuggestion."""
    return {k: v for k, v in dataclasses.asdict(self).items() if v is not None}


@enum.unique
class NetworkCapabilities(enum.IntEnum):
  """Network capabilities.

  https://developer.android.com/reference/android/net/NetworkCapabilities
  """

  NET_CAPABILITY_MMS = 0
  NET_CAPABILITY_SUPL = 1
  NET_CAPABILITY_DUN = 2
  NET_CAPABILITY_FOTA = 3
  NET_CAPABILITY_IMS = 4
  NET_CAPABILITY_CBS = 5
  NET_CAPABILITY_WIFI_P2P = 6
  NET_CAPABILITY_IA = 7
  NET_CAPABILITY_RCS = 8
  NET_CAPABILITY_XCAP = 9
  NET_CAPABILITY_EIMS = 10
  NET_CAPABILITY_NOT_METERED = 11
  NET_CAPABILITY_INTERNET = 12
  NET_CAPABILITY_NOT_RESTRICTED = 13
  NET_CAPABILITY_TRUSTED = 14
  NET_CAPABILITY_NOT_VPN = 15
  NET_CAPABILITY_VALIDATED = 16
  NET_CAPABILITY_CAPTIVE_PORTAL = 17
  NET_CAPABILITY_NOT_ROAMING = 18
  NET_CAPABILITY_FOREGROUND = 19
  NET_CAPABILITY_NOT_CONGESTED = 20
  NET_CAPABILITY_NOT_SUSPENDED = 21
  NET_CAPABILITY_OEM_PAID = 22
  NET_CAPABILITY_MCX = 23
  NET_CAPABILITY_PARTIAL_CONNECTIVITY = 24
  NET_CAPABILITY_TEMPORARILY_NOT_METERED = 25
  NET_CAPABILITY_OEM_PRIVATE = 26
  NET_CAPABILITY_VEHICLE_INTERNAL = 27
  NET_CAPABILITY_NOT_VCN_MANAGED = 28
  NET_CAPABILITY_ENTERPRISE = 29
  NET_CAPABILITY_VSIM = 30
  NET_CAPABILITY_BIP = 31
  NET_CAPABILITY_HEAD_UNIT = 32
  NET_CAPABILITY_MMTEL = 33
  NET_CAPABILITY_PRIORITIZE_LATENCY = 34
  NET_CAPABILITY_PRIORITIZE_BANDWIDTH = 35


@enum.unique
class TransportType(enum.IntEnum):
  """Transport type.

  https://developer.android.com/reference/android/net/NetworkCapabilities#TRANSPORT_WIFI
  """

  TRANSPORT_CELLULAR = 0
  TRANSPORT_WIFI = 1
  TRANSPORT_BLUETOOTH = 2
  TRANSPORT_ETHERNET = 3
  TRANSPORT_VPN = 4
  TRANSPORT_WIFI_AWARE = 5
  TRANSPORT_LOWPAN = 6


@dataclasses.dataclass(frozen=True)
class NetworkRequest:
  """Network request parameters."""

  network_specifier: NetworkSpecifier | None = None
  remove_capability: NetworkCapabilities | None = None
  transport_type: TransportType = TransportType.TRANSPORT_WIFI

  def to_dict(self) -> dict[str, Any]:
    """Returns a dict representation of NetworkRequest."""
    result = {}
    if self.network_specifier is not None:
      result['network_specifier'] = self.network_specifier.to_dict()
    if self.remove_capability:
      result['remove_capability'] = self.remove_capability.value
    result['transport_type'] = self.transport_type.value
    return result


@enum.unique
class NetworkCallback(enum.StrEnum):
  """Network Callback event."""

  ON_AVAILABLE = 'onAvailable'
  ON_UNAVAILABLE = 'onUnavailable'
  CALLBACK_LOST = 'CallbackLost'
  LOST = 'Lost'
  ON_CAPABILITIES_CHANGED = 'onCapabilitiesChanged'
  ON_USER_APPROVAL_STATUS_CHANGE = 'onUserApprovalStatusChange'
  ON_CONNECTION_STATUS = 'onConnectionStatus'


@enum.unique
class NetworkSuggestionStatus(enum.IntEnum):
  """Network Suggestion Status.

  https://developer.android.com/reference/android/net/wifi/WifiManager#STATUS_NETWORK_SUGGESTIONS_ERROR_ADD_DUPLICATE
  """

  STATUS_NETWORK_SUGGESTIONS_SUCCESS = 0
  STATUS_NETWORK_SUGGESTIONS_ERROR_INTERNAL = 1
  STATUS_NETWORK_SUGGESTIONS_ERROR_APP_DISALLOWED = 2
  STATUS_NETWORK_SUGGESTIONS_ERROR_ADD_DUPLICATE = 3
  STATUS_NETWORK_SUGGESTIONS_ERROR_ADD_EXCEEDS_MAX_PER_APP = 4
  STATUS_NETWORK_SUGGESTIONS_ERROR_REMOVE_INVALID = 5
  STATUS_NETWORK_SUGGESTIONS_ERROR_ADD_NOT_ALLOWED = 6
  STATUS_NETWORK_SUGGESTIONS_ERROR_ADD_INVALID = 7
  STATUS_NETWORK_SUGGESTIONS_ERROR_RESTRICTED_BY_ADMIN = 8


@enum.unique
class NetworkSuggestionUserApprovalStatus(enum.IntEnum):
  """Network Suggestion User Approval Status.

  https://developer.android.com/reference/android/net/wifi/WifiManager#STATUS_SUGGESTION_APPROVAL_APPROVED_BY_CARRIER_PRIVILEGE
  """

  STATUS_SUGGESTION_APPROVAL_UNKNOWN = 0
  STATUS_SUGGESTION_APPROVAL_PENDING = 1
  STATUS_SUGGESTION_APPROVAL_APPROVED_BY_USER = 2
  STATUS_SUGGESTION_APPROVAL_REJECTED_BY_USER = 3
  STATUS_SUGGESTION_APPROVAL_APPROVED_BY_CARRIER_PRIVILEGE = 4


@enum.unique
class NetworkSuggestionConnectionStatus(enum.IntEnum):
  """Network Suggestion Connection Status.

  https://developer.android.com/reference/android/net/wifi/WifiManager#STATUS_SUGGESTION_CONNECTION_FAILURE_AUTHENTICATION
  """

  STATUS_SUGGESTION_CONNECTION_FAILURE_ASSOCIATION = 1
  STATUS_SUGGESTION_CONNECTION_FAILURE_AUTHENTICATION = 2
  STATUS_SUGGESTION_CONNECTION_FAILURE_IP_PROVISIONING = 3
  STATUS_SUGGESTION_CONNECTION_FAILURE_UNKNOWN = 4


@enum.unique
class WifiManagerConstants(enum.StrEnum):
  """Wifi Manager Constants.

  https://developer.android.com/reference/android/net/wifi/WifiManager#ACTION_WIFI_NETWORK_SUGGESTION_POST_CONNECTION
  """

  ACTION_PICK_WIFI_NETWORK = 'android.net.wifi.action.PICK_WIFI_NETWORK'
  ACTION_REMOVE_SUGGESTION_DISCONNECT = 'android.net.wifi.action.REMOVE_SUGGESTION_DISCONNECT'
  ACTION_REMOVE_SUGGESTION_LINGER = 'android.net.wifi.action.REMOVE_SUGGESTION_LINGER'
  ACTION_REQUEST_SCAN_ALWAYS_AVAILABLE = 'android.net.wifi.action.REQUEST_SCAN_ALWAYS_AVAILABLE'
  ACTION_WIFI_NETWORK_SUGGESTION_POST_CONNECTION = 'android.net.wifi.action.WIFI_NETWORK_SUGGESTION_POST_CONNECTION'
  ACTION_WIFI_SCAN_AVAILABILITY_CHANGED = 'android.net.wifi.action.WIFI_SCAN_AVAILABILITY_CHANGED'


@enum.unique
class LocationMode(enum.IntEnum):
  """Location Mode.

  https://developer.android.com/reference/android/provider/Settings.Secure#LOCATION_MODE
  """
  LOCATION_MODE_OFF = 0
  LOCATION_MODE_SENSORS_ONLY = 1
  LOCATION_MODE_BATTERY_SAVING = 2
  LOCATION_MODE_HIGH_ACCURACY = 3
