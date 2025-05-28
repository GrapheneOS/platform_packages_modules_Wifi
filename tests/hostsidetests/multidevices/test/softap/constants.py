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
"""Constants for SoftAp Mobly test."""

import datetime
import enum


# Timeout duration for receiving callbacks
CALLBACK_TIMEOUT = datetime.timedelta(seconds=10)
# Wi-Fi scan result interval
WIFI_SCAN_INTERVAL_SEC = datetime.timedelta(seconds=5)

# WiFi standards
WIFI_STANDARD_11AX = 6

@enum.unique
class LocalOnlyHotspotCallbackEventName(enum.StrEnum):
  """Event names for WifiManager#LocalOnlyHotspotCallback."""

  ON_STARTED = 'onStarted'
  ON_FAILED = 'onFailed'


@enum.unique
class LocalOnlyOnStartedDataKey(enum.StrEnum):
  """Data keys received from LocalOnlyHotspotCallback#onStarted."""

  SSID = 'ssid'
  PASSPHRASE = 'passphrase'


@enum.unique
class LocalOnlyOnFailedDataKey(enum.StrEnum):
  """Data keys received from LocalOnlyHotspotCallback#onFailed."""

  REASON = 'reason'


@enum.unique
class StartTetheringCallbackEventName(enum.StrEnum):
  """Event names for TetheringManager#StartTetheringCallback."""

  ON_TETHERING_STARTED = 'onTetheringStarted'
  ON_TETHERING_FAILED = 'onTetheringFailed'


@enum.unique
class TetheringOnTetheringFailedDataKey(enum.StrEnum):
  """Data keys received from the StartTetheringCallback#onTetheringFailed."""

  ERROR = 'error'


@enum.unique
class SoftApCallbackEventName(enum.StrEnum):
  """Event names for WifiManager#SoftApCallback."""

  ON_CONNECTED_CLIENTS_CHANGED = 'onConnectedClientsChanged'
  ON_CLIENTS_DISCONNECTED = 'onClientsDisconnected'


@enum.unique
class SoftApOnConnectedClientsChangedDataKey(enum.StrEnum):
  """Data keys received from SoftApCallback#onConnectedClientsChanged."""

  CONNECTED_CLIENTS_COUNT = 'connectedClientsCount'
  CLIENT_MAC_ADDRESS = 'clientMacAddress'


@enum.unique
class SoftApOnClientsDisconnectedDataKey(enum.StrEnum):
  """Data keys received from SoftApCallback#onClientsDisconnected."""

  DISCONNECTED_CLIENTS_COUNT = 'disconnectedClientsCount'
  CLIENT_MAC_ADDRESS = 'clientMacAddress'


class WifiEnums():
    # All Wifi channels to frequencies lookup.
    freq_to_channel = {
        2412: 1,
        2417: 2,
        2422: 3,
        2427: 4,
        2432: 5,
        2437: 6,
        2442: 7,
        2447: 8,
        2452: 9,
        2457: 10,
        2462: 11,
        2467: 12,
        2472: 13,
        2484: 14,
        4915: 183,
        4920: 184,
        4925: 185,
        4935: 187,
        4940: 188,
        4945: 189,
        4960: 192,
        4980: 196,
        5035: 7,
        5040: 8,
        5045: 9,
        5055: 11,
        5060: 12,
        5080: 16,
        5170: 34,
        5180: 36,
        5190: 38,
        5200: 40,
        5210: 42,
        5220: 44,
        5230: 46,
        5240: 48,
        5260: 52,
        5280: 56,
        5300: 60,
        5320: 64,
        5500: 100,
        5520: 104,
        5540: 108,
        5560: 112,
        5580: 116,
        5600: 120,
        5620: 124,
        5640: 128,
        5660: 132,
        5680: 136,
        5700: 140,
        5745: 149,
        5765: 153,
        5785: 157,
        5795: 159,
        5805: 161,
        5825: 165,
        }

@enum.unique
class WiFiTethering(enum.StrEnum):
    SSID_KEY = "SSID"  # Used for Wifi & SoftAp
    SSID_PATTERN_KEY = "ssidPattern"
    NETID_KEY = "network_id"
    BSSID_KEY = "BSSID"  # Used for Wifi & SoftAp
    BSSID_PATTERN_KEY = "bssidPattern"
    PWD_KEY = "password"  # Used for Wifi & SoftAp
    frequency_key = "frequency"
    HIDDEN_KEY = "hiddenSSID"  # Used for Wifi & SoftAp
    IS_APP_INTERACTION_REQUIRED = "isAppInteractionRequired"
    IS_USER_INTERACTION_REQUIRED = "isUserInteractionRequired"
    IS_SUGGESTION_METERED = "isMetered"
    PRIORITY = "priority"
    SECURITY = "security"  # Used for Wifi & SoftAp

    # Used for SoftAp
    AP_BAND_KEY = "apBand"
    AP_CHANNEL_KEY = "apChannel"
    AP_BANDS_KEY = "apBands"

@enum.unique
class WiFiHotspotBand(enum.IntEnum):
    """WiFi hotspot band code."""
    WIFI_CONFIG_SOFTAP_BAND_2G = 1
    WIFI_CONFIG_SOFTAP_BAND_5G = 2
    WIFI_CONFIG_SOFTAP_BAND_2G_5G = 3
    WIFI_CONFIG_SOFTAP_BAND_6G = 4
    WIFI_CONFIG_SOFTAP_BAND_2G_6G = 5
    WIFI_CONFIG_SOFTAP_BAND_5G_6G = 6
    WIFI_CONFIG_SOFTAP_BAND_ANY = 7

class SoftApSecurityType():
  OPEN = "NONE"
  WPA2 = "WPA2_PSK"
  WPA3_SAE_TRANSITION = "WPA3_SAE_TRANSITION"
  WPA3_SAE = "WPA3_SAE"