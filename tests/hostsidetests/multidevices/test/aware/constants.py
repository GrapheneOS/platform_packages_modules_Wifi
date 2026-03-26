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
"""Constants for Wifi-Aware Mobly test"""

import dataclasses
import datetime
import enum
import operator

from mobly import utils

# Package name for the Wi-Fi Aware snippet application
WIFI_SNIPPET_PACKAGE_NAME = 'com.google.snippet.wifi'
# Timeout duration for Wi-Fi state change operations
WAIT_WIFI_STATE_TIME_OUT = datetime.timedelta(seconds=30)
AWARE_NETWORK_INFO_CLASS_NAME = 'android.net.wifi.aware.WifiAwareNetworkInfo'

SERVICE_NAME = 'service_name'
SERVICE_SPECIFIC_INFO = 'service_specific_info'
MATCH_FILTER = 'match_filter'
MATCH_FILTER_LIST = 'MatchFilterList'
SUBSCRIBE_TYPE = 'subscribe_type'
PUBLISH_TYPE = 'publish_type'
TERMINATE_NOTIFICATION_ENABLED = 'terminate_notification_enabled'
MAX_DISTANCE_MM = 'max_distance_mm'
PAIRING_CONFIG = 'pairing_config'
AWARE_NETWORK_INFO_CLASS_NAME = 'android.net.wifi.aware.WifiAwareNetworkInfo'
TTL_SEC = 'TtlSec'
INSTANT_MODE = 'instant_mode'
FEATURE_WIFI_AWARE = 'feature:android.hardware.wifi.aware'
DISCOVERY_KEY_RANGING_ENABLED = 'ranging_enabled'
DISCOVERY_KEY_MIN_DISTANCE_MM = 'MinDistanceMm'
DISCOVERY_KEY_MAX_DISTANCE_MM = 'MaxDistanceMm'
INSTANT_MODE_BAND_5 = '5G'
INSTANT_MODE_BAND_24 = '2.4G'


# onServiceLost reason code
EASON_PEER_NOT_VISIBLE = 1

# throughput unit change
BITS_TO_MBPS = 1000000

class WifiAwareTestConstants:
    """Constants for Wi-Fi Aware test."""

    SERVICE_NAME = 'CtsVerifierTestService-%s' % utils.rand_ascii_str(5)
    MATCH_FILTER_BYTES = 'bytes used for matching'.encode('utf-8')
    PUB_SSI = 'Extra bytes in the publisher discovery'.encode('utf-8')
    SUB_SSI = 'Arbitrary bytes for the subscribe discovery'.encode('utf-8')
    LARGE_ENOUGH_DISTANCE_MM = 100000
    PASSWORD = 'Some super secret password'
    ALIAS_PUBLISH = 'publisher'
    ALIAS_SUBSCRIBE = 'subscriber'
    TEST_WAIT_DURATION_MS = 10000
    TEST_MESSAGE = 'test message!'
    MESSAGE_ID = 1234
    MSG_CLIENT_TO_SERVER = (
        'GET SOME BYTES [Random Identifier: %s]' % utils.rand_ascii_str(5)
    )
    MSG_SERVER_TO_CLIENT = (
        'PUT SOME OTHER BYTES [Random Identifier: %s]' % utils.rand_ascii_str(5)
    )
    PMK = '01234567890123456789012345678901'
    # 6 == TCP
    TRANSPORT_PROTOCOL_TCP = 6
    CHANNEL_IN_MHZ = 5745


@enum.unique
class WifiAwareSnippetEventName(enum.StrEnum):
    """Represents event names for Wi-Fi Aware snippet operations."""

    GET_PAIRED_DEVICE = 'getPairedDevices'
    ON_AVAILABLE = 'onAvailable'
    ON_LOST = 'onLost'


@enum.unique
class WifiAwareSnippetParams(enum.StrEnum):
    """Represents parameters for Wi-Fi Aware snippet events."""

    ALIAS_LIST = 'getPairedDevices'


@enum.unique
class DiscoverySessionCallbackMethodType(enum.StrEnum):
    """Represents the types of callback methods for Wi-Fi Aware discovery sessions.

    These callbacks are correspond to DiscoverySessionCallback in the Android documentation:
    https://developer.android.com/reference/android/net/wifi/aware/DiscoverySessionCallback
    """

    PUBLISH_STARTED = 'onPublishStarted'
    SUBSCRIBE_STARTED = 'onSubscribeStarted'
    SESSION_CONFIG_UPDATED = 'onSessionConfigUpdated'
    SESSION_CONFIG_FAILED = 'onSessionConfigFailed'
    SESSION_TERMINATED = 'onSessionTerminated'
    SERVICE_DISCOVERED = 'onServiceDiscovered'
    SERVICE_DISCOVERED_WITHIN_RANGE = 'onServiceDiscoveredWithinRange'
    MESSAGE_SEND_SUCCEEDED = 'onMessageSendSucceeded'
    MESSAGE_SEND_FAILED = 'onMessageSendFailed'
    MESSAGE_RECEIVED = 'onMessageReceived'
    PAIRING_REQUEST_RECEIVED = 'onPairingSetupRequestReceived'
    PAIRING_SETUP_SUCCEEDED = 'onPairingSetupSucceeded'
    PAIRING_SETUP_FAILED = 'onPairingSetupFailed'
    PAIRING_VERIFICATION_SUCCEEDED = 'onPairingVerificationSucceed'
    PAIRING_VERIFICATION_FAILED = 'onPairingVerificationFailed'
    BOOTSTRAPPING_SUCCEEDED = 'onBootstrappingSucceeded'
    BOOTSTRAPPING_FAILED = 'onBootstrappingFailed'
    DATA_PATH_REQUEST_RECEIVED = 'onDataPathRequestReceived'
    DATA_PATH_CONNECTED = 'onDataPathConnected'
    DATA_PATH_REQUEST_FAILURE = 'onDataPathRequestFailed'
    DATA_PATH_DISCONNECTED = 'onDataPathDisconnected'

    # Event for the publish or subscribe step: triggered by onPublishStarted or SUBSCRIBE_STARTED or
    # onSessionConfigFailed
    DISCOVER_RESULT = 'discoveryResult'
    # Event for the message send result.
    MESSAGE_SEND_RESULT = 'messageSendResult'
    SESSION_CB_ON_SERVICE_LOST = 'WifiAwareSessionOnServiceLost'
    SESSION_CB_KEY_LOST_REASON = 'lostReason'


@enum.unique
class DiscoverySessionCallbackParamsType(enum.StrEnum):
    CALLBACK_NAME = 'callbackName'
    IS_SESSION_INIT = 'isSessionInitialized'
    MESSAGE_ID = 'messageId'
    RECEIVE_MESSAGE = 'receivedMessage'
    SESSION_CB_KEY_MESSAGE_AS_STRING = "messageAsString"
    SESSION_CB_KEY_LATENCY_MS = "latencyMs"


@enum.unique
class NetworkCbEventName(enum.StrEnum):
    """Represents the event name for ConnectivityManager network callbacks."""

    NETWORK_CALLBACK = 'NetworkCallback'
    NETWORK_CB_LOST = 'CallbackLost'


@enum.unique
class NetworkCbEventKey(enum.StrEnum):
    """Represents event data keys for ConnectivityManager network callbacks."""

    NETWORK = 'network'
    CALLBACK_NAME = 'callbackName'
    NETWORK_CAPABILITIES = 'networkCapabilities'
    TRANSPORT_INFO_CLASS_NAME = 'transportInfoClassName'
    CHANNEL_IN_MHZ = 'channelInMhz'
    NETWORK_INTERFACE_NAME = 'interfaceName'
    NETWORK_CB_KEY_CURRENT_TS = "current_timestamp"
    NETWORK_CB_KEY_CREATE_TS = "creation_timestamp"


@enum.unique
class NetworkCbName(enum.StrEnum):
    """Represents the name of network callback for ConnectivityManager.

    These callbacks are correspond to DiscoverySessionCallback in the Android documentation:
    https://developer.android.com/reference/android/net/ConnectivityManager.NetworkCallback
    """

    ON_UNAVAILABLE = 'onUnavailable'
    ON_CAPABILITIES_CHANGED = 'onCapabilitiesChanged'
    ON_PROPERTIES_CHANGED = 'onLinkPropertiesChanged'
    NET_CAP_IPV6 = 'aware_ipv6'
    NET_CAP_PORT = 'port'
    NET_CAP_TRANSPORT_PROTOCOL = 'aware_transport_protocol'


@enum.unique
class RangingResultCb(enum.StrEnum):
    """Constant for handling callback of snippet RPC wifiAwareStartRanging."""

    # Callback methods related to RangingResultCallback:
    # https://developer.android.com/reference/android/net/wifi/rtt/RangingResultCallback
    CB_METHOD_ON_RANGING_RESULT = 'onRangingResults'
    CB_METHOD_ON_RANGING_FAILURE = 'onRangingFailure'

    # Other constants related to snippet implementation.
    EVENT_NAME_ON_RANGING_RESULT = 'WifiRttRangingOnRangingResult'
    DATA_KEY_CALLBACK_NAME = 'callbackName'
    DATA_KEY_RESULTS = 'results'
    DATA_KEY_RESULT_STATUS = 'status'
    DATA_KEY_RESULT_DISTANCE_MM = 'distanceMm'
    DATA_KEY_RESULT_RSSI = 'rssi'
    DATA_KEY_PEER_ID = 'peerId'
    DATA_KEY_MAC = 'mac'
    DATA_KEY_DISTANCE_STD_DEV_MM = 'distanceStdDevMm'
    DATA_KEY_NUM_ATTEMPTED_MEASUREMENTS = 'numAttemptedMeasurements'
    DATA_KEY_NUM_SUCCESSFUL_MEASUREMENTS = 'numSuccessfulMeasurements'
    DATA_KEY_LCI = 'lci'
    DATA_KEY_LCR = 'lcr'
    DATA_KEY_TIMESTAMP = 'timestamp'
    DATA_KEY_MAC_AS_STRING = 'macAsString'


@enum.unique
class RangingStatusCb(enum.IntEnum):
    """Constant for handling RTT status."""

    EVENT_CB_RANGING_STATUS_SUCCESS = 0
    EVENT_CB_RANGING_STATUS_FAIL = 1
    EVENT_CB_RANGING_STATUS_RESPONDER_DOES_NOT_SUPPORT_IEEE80211MC = 2


@enum.unique
class RangingResultStatusCode(enum.IntEnum):
    """Ranging result status code.

    This corresponds to status constants in RangingRequest:
    https://developer.android.com/reference/android/net/wifi/rtt/RangingResult
    """

    SUCCESS = 0
    FAIL = 1
    RESPONDER_DOES_NOT_SUPPORT_IEEE80211MC = 2


@enum.unique
class WifiAwareSnippetParams(enum.StrEnum):
    """Represents parameters for Wi-Fi Aware snippet events."""

    SERVICE_SPECIFIC_INFO = 'serviceSpecificInfo'
    RECEIVED_MESSAGE = 'receivedMessage'
    PEER_HANDLE = 'peerHandle'
    MATCH_FILTER = 'matchFilter'
    MATCH_FILTER_VALUE = 'value'
    PAIRING_CONFIG = 'pairingConfig'
    DISTANCE_MM = 'distanceMm'
    LAST_MESSAGE_ID = 'lastMessageId'
    PAIRING_REQUEST_ID = 'pairingRequestId'
    BOOTSTRAPPING_METHOD = 'bootstrappingMethod'
    PEER_ID = 'peerId'
    PAIRED_SETUP_ENABLED = 'pairingSetupEnabled'
    PAIRED_CACHE_ENABLED = 'pairingCacheEnabled'
    PAIRED_VERIFICATION_ENABLED = 'pairingVerificationEnabled'


@enum.unique
class SubscribeType(enum.IntEnum):
    """Represents the types of subscriptions in Wi-Fi Aware.

    These callbacks are correspond to SubscribeConfig in the Android documentation:
    https://developer.android.com/reference/android/net/wifi/aware/SubscribeConfig#constants_1
    """

    PASSIVE = 0
    ACTIVE = 1


@enum.unique
class PublishType(enum.IntEnum):
    """Represents the types of publications in Wi-Fi Aware.

    These publication types are correspond to PublishConfig in the Android documentation:
    https://developer.android.com/reference/android/net/wifi/aware/PublishConfig#constants_1
    """

    UNSOLICITED = 0
    SOLICITED = 1


class BootstrappingMethod(enum.IntEnum):
    """Represents bootstrapping methods for Wi-Fi Aware pairing.

    These types are correspond to AwarePairingConfig bootstrapping methods in the Android
    documentation:
    https://developer.android.com/reference/android/net/wifi/aware/AwarePairingConfig#summary
    """

    OPPORTUNISTIC = 1
    PIN_CODE_DISPLAY = 2
    PASSPHRASE_DISPLAY = 4
    QR_DISPLAY = 8
    NFC_TAG = 16
    PIN_CODE_KEYPAD = 32
    PASSPHRASE_KEYPAD = 64
    QR_SCAN = 128
    NFC_READER = 256


@dataclasses.dataclass(frozen=True)
class AwarePairingConfig:
    """Config for Wi-Fi Aware Pairing.

    These configurations correspond to AwarePairingConfig in the Android documentation:
    https://developer.android.com/reference/android/net/wifi/aware/AwarePairingConfig?hl=en
    """

    pairing_cache_enabled: bool = False
    pairing_setup_enabled: bool = False
    pairing_verification_enabled: bool = False
    bootstrapping_methods: BootstrappingMethod = (
        BootstrappingMethod.OPPORTUNISTIC
    )

    def to_dict(self) -> dict[str, int | bool]:
        result = dataclasses.asdict(self)
        result['bootstrapping_methods'] = self.bootstrapping_methods.value
        return result


@dataclasses.dataclass(frozen=True)
class SubscribeConfig:
    """Config for Wi-Fi Aware Subscribe.

    These configurations correspond to SubscribeConfig in the Android documentation:
    https://developer.android.com/reference/android/net/wifi/aware/SubscribeConfig
    """

    subscribe_type: SubscribeType
    service_specific_info: bytes = WifiAwareTestConstants.SUB_SSI
    match_filter: list[bytes] | None = (
        WifiAwareTestConstants.MATCH_FILTER_BYTES,
    )
    min_distance_mm: int | None = None
    max_distance_mm: int | None = None
    pairing_config: AwarePairingConfig | None = None
    terminate_notification_enabled: bool = True
    service_name: str = WifiAwareTestConstants.SERVICE_NAME
    instant_mode: str | None = None

    def to_dict(
        self,
    ) -> dict[str, str | bool | list[str] | int | dict[str, int | bool | None]]:
        result = dataclasses.asdict(self)
        result['subscribe_type'] = self.subscribe_type.value
        result['service_specific_info'] = self.service_specific_info.decode(
            'utf-8'
        )
        if self.instant_mode is None:
            del result[INSTANT_MODE]
        else:
            result[INSTANT_MODE] = self.instant_mode

        if self.match_filter is None:
            del result['match_filter']
        else:
            result['match_filter'] = [
                mf.decode('utf-8') for mf in self.match_filter
            ]

        if self.pairing_config is None:
            del result['pairing_config']
        else:
            result['pairing_config'] = self.pairing_config.to_dict()

        if self.max_distance_mm is None:
            del result['max_distance_mm']

        if self.min_distance_mm is None:
            del result["min_distance_mm"]

        return result


@dataclasses.dataclass(frozen=True)
class PublishConfig:
    """Wi-Fi Aware Publish Config.

    These configurations correspond to PublishConfig in the Android documentation:
    https://developer.android.com/reference/android/net/wifi/aware/PublishConfig
    """

    publish_type: PublishType
    service_specific_info: bytes = WifiAwareTestConstants.PUB_SSI
    match_filter: list[bytes] | None = (
        WifiAwareTestConstants.MATCH_FILTER_BYTES,
    )
    ranging_enabled: bool = False
    terminate_notification_enabled: bool = True
    pairing_config: AwarePairingConfig | None = None
    service_name: str = WifiAwareTestConstants.SERVICE_NAME
    instant_mode: str | None = None

    def to_dict(
        self,
    ) -> dict:
        """Convert PublishConfig to dict."""
        result = dataclasses.asdict(self)
        result['publish_type'] = self.publish_type.value
        result['service_specific_info'] = self.service_specific_info.decode(
            'utf-8'
        )
        if self.match_filter is None:
            del result['match_filter']
        else:
            result['match_filter'] = [
                mf.decode('utf-8') for mf in self.match_filter
            ]

        if self.instant_mode is None:
            del result[INSTANT_MODE]
        else:
            result[INSTANT_MODE] = self.instant_mode

        if self.pairing_config is None:
            del result['pairing_config']
        else:
            result['pairing_config'] = self.pairing_config.to_dict()
        return result


@dataclasses.dataclass(frozen=True)
class RangingRequest:
    """Wi-Fi RTT Ranging request.

    This class correspond to android.net.wifi.rtt.RangingRequest:
    https://developer.android.com/reference/android/net/wifi/rtt/RangingRequest

    Attributes:
        peer_ids: A list of peer IDs that will be converted to peer Handles and
            passed to RangingRequest on device.
        peer_mac_addresses: A list of peer MAC addresses that will be passed to
            RangingRequest on device.
    """

    peer_ids: list[int] = dataclasses.field(default_factory=list)
    peer_mac_addresses: list[str] = dataclasses.field(default_factory=list)

    def to_dict(self) -> dict:
        result = {}
        if self.peer_ids:
            result['peer_ids'] = self.peer_ids
        if self.peer_mac_addresses:
            result['peer_mac_addresses'] = self.peer_mac_addresses
        return result


class NetworkCapabilities:
    """Network Capabilities.

    https://developer.android.com/reference/android/net/NetworkCapabilities?hl=en#summary
    """

    class Transport(enum.IntEnum):
        """Transport type.

        https://developer.android.com/reference/android/net/NetworkCapabilities#TRANSPORT_CELLULAR
        """

        TRANSPORT_CELLULAR = 0
        TRANSPORT_WIFI = 1
        TRANSPORT_BLUETOOTH = 2
        TRANSPORT_ETHERNET = 3
        TRANSPORT_VPN = 4
        TRANSPORT_WIFI_AWARE = 5
        TRANSPORT_LOWPAN = 6

    class NetCapability(enum.IntEnum):
        """Network Capability.

        https://developer.android.com/reference/android/net/NetworkCapabilities#NET_CAPABILITY_MMS
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


@dataclasses.dataclass(frozen=True)
class NetworkRequest:
    """Wi-Fi Aware Network Request.

    https://developer.android.com/reference/android/net/NetworkRequest
    """

    transport_type: NetworkCapabilities.Transport
    network_specifier_parcel: str

    def to_dict(self) -> dict:
        result = dataclasses.asdict(self)
        if not self.network_specifier_parcel:
            del result['network_specifier_parcel']
        if self.transport_type:
            result['transport_type'] = self.transport_type.value
        return result


class Characteristics(enum.IntEnum):
    """The characteristics of the Wi-Fi Aware implementation.

    https://developer.android.com/reference/android/net/wifi/aware/Characteristics
    """

    WIFI_AWARE_CIPHER_SUITE_NCS_SK_128 = 1
    WIFI_AWARE_CIPHER_SUITE_NCS_SK_256 = 2
    WIFI_AWARE_CIPHER_SUITE_NCS_PK_128 = 4
    WIFI_AWARE_CIPHER_SUITE_NCS_PK_256 = 8
    WIFI_AWARE_CIPHER_SUITE_NCS_PK_PASN_128 = 16
    WIFI_AWARE_CIPHER_SUITE_NCS_PK_PASN_256 = 32


@dataclasses.dataclass(frozen=False)
class WifiAwareDataPathSecurityConfig:
    """Wi-Fi Aware Network Specifier.

    https://developer.android.com/reference/android/net/wifi/aware/WifiAwareNetworkSpecifier
    """
    psk_passphrase: str | None = None
    pmk: str | None = None
    cipher_suite: Characteristics | None = (
        Characteristics.WIFI_AWARE_CIPHER_SUITE_NCS_SK_128
    )

    def to_dict(self) -> dict:
        result = dataclasses.asdict(self)
        if not self.pmk:
            del result['pmk']
        if not self.cipher_suite:
            del result['cipher_suite']
        if not self.psk_passphrase:
            del result['psk_passphrase']
        else:
            result['cipher_suite'] = self.cipher_suite.value
        return result


@dataclasses.dataclass(frozen=False)
class AwareDataPathRequest:
    port: int | None = None
    transport_protocol: int | None = None
    data_path_security_config: WifiAwareDataPathSecurityConfig | None = None

    def to_dict(self) -> dict:
        result = dataclasses.asdict(self)
        if not self.port:
            del result['port']
        if not self.transport_protocol:
            del result['transport_protocol']
        if not self.data_path_security_config:
            del result['data_path_security_config']
        else:
            result['data_path_security_config'] = (
                self.data_path_security_config.to_dict()
            )
        return result

@dataclasses.dataclass(frozen=False)
class WifiAwareNetworkSpecifier:
    """Wi-Fi Aware Network Specifier.

    https://developer.android.com/reference/android/net/wifi/aware/WifiAwareNetworkSpecifier
    """

    psk_passphrase: str | None = None
    port: int | None = None
    transport_protocol: int | None = None
    pmk: str | None = None
    data_path_security_config: WifiAwareDataPathSecurityConfig | None = None
    channel_frequency_m_hz: int | None = None

    def to_dict(self) -> dict:
        result = dataclasses.asdict(self)
        if not self.psk_passphrase:
            del result['psk_passphrase']
        if not self.port:
            del result['port']
        if not self.transport_protocol:
            del result['transport_protocol']
        if not self.pmk:
            del result['pmk']
        if not self.data_path_security_config:
            del result['data_path_security_config']
        else:
            result['data_path_security_config'] = (
                self.data_path_security_config.to_dict()
            )
        if not self.channel_frequency_m_hz:
            del result['channel_frequency_m_hz']
        return result


class SnippetEventNames:
    """Represents event names for Wi-Fi Aware snippet operations."""

    SERVER_SOCKET_ACCEPT = 'ServerSocketAccept'


class SnippetEventParams:
    """Represents parameters for Wi-Fi Aware snippet events."""

    IS_ACCEPT = 'isAccept'
    ERROR = 'error'
    LOCAL_PORT = 'localPort'


@enum.unique
class AttachCallBackMethodType(enum.StrEnum):
    """Represents Attach Callback Method Type in Wi-Fi Aware.

    https://developer.android.com/reference/android/net/wifi/aware/AttachCallback
    """

    ATTACHED = 'onAttached'
    ATTACH_FAILED = 'onAttachFailed'
    AWARE_SESSION_TERMINATED = 'onAwareSessionTerminated'
    ID_CHANGED = 'WifiAwareAttachOnIdentityChanged'


@enum.unique
class WifiAwareBroadcast(enum.StrEnum):
    WIFI_AWARE_AVAILABLE = 'WifiAwareStateAvailable'
    WIFI_AWARE_NOT_AVAILABLE = 'WifiAwareStateNotAvailable'


@enum.unique
class DeviceidleState(enum.StrEnum):
    ACTIVE = 'ACTIVE'
    IDLE = 'IDLE'
    INACTIVE = 'INACTIVE'
    OVERRIDE = 'OVERRIDE'


@enum.unique
class Operator(enum.Enum):
    """Operator used in the comparison."""

    GREATER = operator.gt
    GREATER_EQUAL = operator.ge
    NOT_EQUAL = operator.ne
    EQUAL = operator.eq
    LESS = operator.lt
    LESS_EQUAL = operator.le


@enum.unique
class AndroidVersion(enum.IntEnum):
    """Android OS version."""

    R = 11
    S = 12
    T = 13
    U = 14


@enum.unique
class CountryCode(enum.StrEnum):
    """Country Code abbreviation."""
    AUSTRALIA = 'AU'
    CHINA = 'CN'
    GERMANY = 'DE'
    JAPAN = 'JP'
    UK = 'GB'
    US = 'US'
    UNKNOWN = 'UNKNOWN'

class AwarePowerSettings:
    """Aware Power Settings."""
    POWER_DW_24_INTERACTIVE = 1
    POWER_DW_5_INTERACTIVE = 1
    POWER_DISC_BEACON_INTERVAL_INTERACTIVE = 0
    POWER_NUM_SS_IN_DISC_INTERACTIVE = 0
    POWER_ENABLE_DW_EARLY_TERM_INTERACTIVE = 0

    POWER_DW_24_NON_INTERACTIVE = 4
    POWER_DW_5_NON_INTERACTIVE = 0
    POWER_DISC_BEACON_INTERVAL_NON_INTERACTIVE = 0
    POWER_NUM_SS_IN_DISC_NON_INTERACTIVE = 0
    POWER_ENABLE_DW_EARLY_TERM_NON_INTERACTIVE = 0