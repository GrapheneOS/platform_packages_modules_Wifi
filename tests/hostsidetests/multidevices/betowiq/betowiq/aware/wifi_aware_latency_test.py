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

"""Wi-Fi Aware Latency test reimplemented in Mobly."""

import logging
import sys
import time
import json
import queue
import os

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
# from queue import Empty


RUNTIME_PERMISSIONS = (
    'android.permission.ACCESS_FINE_LOCATION',
    'android.permission.ACCESS_COARSE_LOCATION',
    'android.permission.NEARBY_WIFI_DEVICES',
)
PACKAGE_NAME = constants.WIFI_SNIPPET_PACKAGE_NAME
_DEFAULT_TIMEOUT = constants.WAIT_WIFI_STATE_TIME_OUT.total_seconds()
_CALLBACK_NAME = constants.DiscoverySessionCallbackParamsType.CALLBACK_NAME
_TRANSPORT_TYPE_WIFI_AWARE = (
    constants.NetworkCapabilities.Transport.TRANSPORT_WIFI_AWARE
)
_SESSION_CB_KEY_MESSAGE_AS_STRING= (
    constants.DiscoverySessionCallbackParamsType.SESSION_CB_KEY_MESSAGE_AS_STRING
)
_SESSION_CB_KEY_LATENCY_MS = (
    constants.DiscoverySessionCallbackParamsType.SESSION_CB_KEY_LATENCY_MS
)
_MESSAGE_RECEIVED = (
    constants.DiscoverySessionCallbackMethodType.MESSAGE_RECEIVED
)
_MESSAGE_SEND_RESULT = (
    constants.DiscoverySessionCallbackMethodType.MESSAGE_SEND_RESULT
)
_NETWORK_CB_KEY_CURRENT_TS = (
    constants.NetworkCbEventKey.NETWORK_CB_KEY_CURRENT_TS
)
_NETWORK_CB_KEY_CREATE_TS=(
    constants.NetworkCbEventKey.NETWORK_CB_KEY_CREATE_TS
)
_ON_AVAILABLE = (
    constants.WifiAwareSnippetEventName.ON_AVAILABLE
)
_SERVICE_DISCOVERED=(
    constants.DiscoverySessionCallbackMethodType.SERVICE_DISCOVERED
)
_NETWORK_CB_KEY_NETWORK_SPECIFIER = "network_specifier"
_NETWORK_CB_LINK_PROPERTIES_CHANGED = (
    constants.NetworkCbName.ON_PROPERTIES_CHANGED)
_NETWORK_CB_KEY_INTERFACE_NAME = "interfaceName"

# Aware Data-Path Constants
_DATA_PATH_INITIATOR = 0
_DATA_PATH_RESPONDER = 1

# Publish & Subscribe Config keys.
_PUBLISH_TYPE_UNSOLICITED = 0
_PUBLISH_TYPE_SOLICITED = 1
_SUBSCRIBE_TYPE_PASSIVE = 0
_SUBSCRIBE_TYPE_ACTIVE = 1

_REQUEST_NETWORK_TIMEOUT_MS = 15 * 1000


class WifiAwareLatencyTest(base_test.BaseTestClass):
    """Set of tests for Wi-Fi Aware Latency."""

    # message ID counter to make sure all uses are unique
    msg_id = 0
    device_startup_offset=2

    # number of second to 'reasonably' wait to make sure that devices synchronize
    # with each other - useful for OOB test cases, where the OOB discovery would
    # take some time
    WAIT_FOR_CLUSTER = 5

    SERVICE_NAME = "GoogleTestServiceXYZ"

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
            ad.wifi_aware_snippet.wifiAwareCloseAllWifiAwareSession()
            if not aware_avail:
                ad.log.info('Aware not available. Waiting ...')
                state_handler = (
                    ad.wifi_aware_snippet.wifiAwareMonitorStateChange())
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

    def _teardown_test_on_device(
            self, ad: android_device.AndroidDevice) -> None:
        ad.wifi_aware_snippet.wifiAwareCloseAllWifiAwareSession()
        ad.wifi_aware_snippet.connectivityReleaseAllSockets()
        if ad.is_adb_root:
          autils.reset_device_parameters(ad)
          autils.reset_device_statistics(ad)

    def on_fail(self, record: records.TestResult) -> None:
        android_device.take_bug_reports(self.ads,
                                        destination =
                                        self.current_test_info.output_path)

    def start_discovery_session(self, dut, session_id, is_publish, dtype, instant_mode = None):
        """Start a discovery session

        Args:
            dut: Device under test
            session_id: ID of the Aware session in which to start discovery
            is_publish: True for a publish session, False for subscribe session
            dtype: Type of the discovery session
            instant_mode: set the channel to use instant communication mode.

        Returns:
        Discovery session started event.
        """
        config = {}
        config[constants.SERVICE_NAME] ="GoogleTestServiceXY"
        if instant_mode is not None:
            config[constants.INSTANT_MODE] = instant_mode
        if is_publish:
            config[constants.PUBLISH_TYPE] = dtype
            disc_id = dut.wifi_aware_snippet.wifiAwarePublish(
                session_id, config
            )
            discovery = disc_id.waitAndGet(
                constants.DiscoverySessionCallbackMethodType.DISCOVER_RESULT)
            callback_name = discovery.data[_CALLBACK_NAME]
            asserts.assert_equal(
                constants.DiscoverySessionCallbackMethodType.PUBLISH_STARTED,
                callback_name,
                f'{dut} publish failed, got callback: {callback_name}.',
            )
        else:
            config[constants.SUBSCRIBE_TYPE] = dtype
            disc_id = dut.wifi_aware_snippet.wifiAwareSubscribe(
                session_id, config
            )
            discovery = disc_id.waitAndGet(
                constants.DiscoverySessionCallbackMethodType.DISCOVER_RESULT)
            callback_name = discovery.data[_CALLBACK_NAME]
            asserts.assert_equal(
                constants.DiscoverySessionCallbackMethodType.SUBSCRIBE_STARTED,
                callback_name,
                f'{dut} subscribe failed, got callback: {callback_name}.',
            )
        return disc_id, discovery

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
        dut_id = handler.waitAndGet(constants.AttachCallBackMethodType.ATTACHED)
        even = handler.waitAndGet(constants.AttachCallBackMethodType.ID_CHANGED)
        mac = even.data["mac"]
        return dut_id.callback_id, mac

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

    def network_callback_events(self,
                               key,
                               name,
                               timeout = 5,
                                times = 3):
        """
        Attempts to collect up to 3 events from a queue.

        This function will loop a maximum of three times, trying to fetch an
        event in each iteration. The loop will exit early if the queue is
        empty or if an unexpected error occurs.

        Args:
            key: The object with the waitAndGet method.
            name: The name of the event to wait for.
            timeout: The timeout in seconds for each wait attempt.
            times:Loop a maximum of times
        Returns:
            A list of the events that were successfully collected.
        """
        all_available_events = []
        # Loop a maximum of 3 times.
        # The underscore '_' is used as the variable name because we don't
        # need to use the loop counter itself.
        for _ in range(times):
            try:
                event = key.waitAndGet(
                    event_name = name,
                    timeout = timeout
                )
                all_available_events.append(event)
                logging.info(f"Collected event: {event}")
            except queue.Empty:
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
                network_specifier_params.to_dict()
                if network_specifier_params
                else None,
            )
        )
        network_request_dict = constants.NetworkRequest(
            transport_type=_TRANSPORT_TYPE_WIFI_AWARE,
            network_specifier_parcel=network_specifier_parcel,
        ).to_dict()
        ad.log.debug(
            'Requesting Wi-Fi Aware network: %s', network_request_dict)
        return ad.wifi_aware_snippet.connectivityRequestNetwork(
            net_work_request_id,
            network_request_dict, _REQUEST_NETWORK_TIMEOUT_MS
        )

    def run_end_to_end_latency(
            self, results, dw_24ghz, dw_5ghz,
            num_iterations, include_setup,
            instant_mode = None, csv_name="latency_test"):
        """Measure the latency for end-to-end communication link setup:
        - Start Aware
        - Discovery
        - NDP setup

        Args:
            results: Result array to be populated - will add results (not erase it)
            dw_24ghz: DW interval in the 2.4GHz band.
            dw_5ghz: DW interval in the 5GHz band.
            num_iterations: number of the iterations.
            startup_offset: The start-up gap (in seconds) between the two devices
            include_setup: True to include the cluster setup in the latency
                        measurements.
            instant_mode: set the band to use instant communication mode, 2G or 5G
            csv_name: csv file test result name.
        """

        key = "dw24_%d_dw5_%d" % (dw_24ghz, dw_5ghz)
        results[key] = {}
        results[key]["num_iterations"] = num_iterations
        p_dut = self.ads[0]
        p_dut.pretty_name = "Publisher"
        s_dut = self.ads[1]
        s_dut.pretty_name ="Subscriber"
        # override the default DW configuration
        autils.config_power_settings(p_dut, dw_24ghz, dw_5ghz)
        autils.config_power_settings(s_dut, dw_24ghz, dw_5ghz)
        latencies = []

        # allow for failures here since running lots of samples and would like to
        # get the partial data even in the presence of errors
        failures = 0

        if not include_setup:
            # Publisher+Subscriber: attach and wait for confirmation
            p_id, p_mac = self.attach_with_identity(p_dut)
            s_id, s_mac = self.attach_with_identity(s_dut)
        p_dut_accept_handler = (
            p_dut.wifi_aware_snippet.connectivityServerSocketAccept()
        )
        network_id = p_dut_accept_handler.callback_id
        for i in range(num_iterations):
            while (True):
                # for pseudo-goto/finalize
                timestamp_start = time.perf_counter()
                if include_setup:
                    p_id, p_mac = self.attach_with_identity(p_dut)
                    s_id, s_mac = self.attach_with_identity(s_dut)
                # start publish
                p_disc_id, p_disc_event = self.start_discovery_session(
                    p_dut, p_id, True, _PUBLISH_TYPE_UNSOLICITED, instant_mode)
                # start subscribe
                s_disc_id, s_session_event = self.start_discovery_session(
                    s_dut, s_id, False, _SUBSCRIBE_TYPE_PASSIVE, instant_mode)

                p_req_key = self._request_network(
                    ad=p_dut,
                    discovery_session=p_disc_id.callback_id,
                    peer=None,
                    net_work_request_id=network_id,
                    is_accept_any_peer=True
                    )
                try:
                    discovered_event = s_disc_id.waitAndGet(
                        _SERVICE_DISCOVERED)
                    s_dut.log.info(
                        "[Subscriber] SESSION_CB_ON_SERVICE_DISCOVERED: %s",
                            discovered_event.data)
                    peer_id_on_sub = discovered_event.data[
                        constants.WifiAwareSnippetParams.PEER_ID]
                except queue.Empty:
                    s_dut.log.info("[Subscriber] Timed out while waiting for "
                                    "SESSION_CB_ON_SERVICE_DISCOVERED")
                    failures = failures + 1
                    break
                s_req_key = self._request_network(
                    s_dut,
                    s_disc_id.callback_id,
                    peer_id_on_sub,
                    network_id,
                    is_accept_any_peer=False
                    )
                    # Publisher & Subscriber: wait for network formation
                p_callback_event = self.network_callback_events(
                    p_req_key,
                    constants.NetworkCbEventName.NETWORK_CALLBACK,
                    timeout=_DEFAULT_TIMEOUT
                )
                p_callback_name=(
                    self.find_callback_name(
                        p_callback_event,
                        constants.NetworkCbName.ON_PROPERTIES_CHANGED))
                s_callback_event = self.network_callback_events(
                    s_req_key,
                    constants.NetworkCbEventName.NETWORK_CALLBACK,
                    timeout=_DEFAULT_TIMEOUT
                )
                s_callback_name=(
                    self.find_callback_name(
                        s_callback_event,
                        constants.NetworkCbName.ON_PROPERTIES_CHANGED))
                p_aware_if= p_callback_name.data[
                        constants.NetworkCbEventKey.NETWORK_INTERFACE_NAME
                        ]
                s_aware_if = s_callback_name.data[
                    constants.NetworkCbEventKey.NETWORK_INTERFACE_NAME
                    ]
                p_ipv6 = (
                    p_dut.wifi_aware_snippet.connectivityGetLinkLocalIpv6Address(
                        p_aware_if
                    )
                )
                p_dut.log.info(
                    'interfaceName = %s, ipv6=%s', p_aware_if, p_ipv6)
                s_ipv6 = (
                    s_dut.wifi_aware_snippet.connectivityGetLinkLocalIpv6Address(
                        s_aware_if
                    )
                )
                s_dut.log.info(
                    'interfaceName = %s, ipv6=%s', s_aware_if, s_ipv6)
                latencies.append(time.perf_counter() - timestamp_start)
                break
            p_dut.wifi_aware_snippet.wifiAwareCloseDiscoverSession(
                p_disc_id.callback_id)
            s_dut.wifi_aware_snippet.wifiAwareCloseDiscoverSession(
                s_disc_id.callback_id)
            if include_setup:
                p_dut.wifi_aware_snippet.wifiAwareDetach(p_id)
                s_dut.wifi_aware_snippet.wifiAwareDetach(s_id)

        filename = f"{csv_name}.csv"
        output_file = os.path.join(self.log_path, filename)
        autils.extract_stats(
            s_dut,
            data=latencies,
            results=results[key],
            key_prefix="",
            log_prefix=f"E2E Latency (dw24={dw_24ghz}, dw5={dw_5ghz})",
            csv_filepath=output_file)
        asserts.explicit_pass(
            "test_end_to_end_latency_default_dws finished", extras=results)

    ####################################################
    #  Measure the latency for end-to-end communication link setup:
    # Start Aware
    #- Discovery
    #- NDP setup
    #
    # names is:test_end_to_end_setup_latency_default_dws/
    #           latency_non_interactive_dws/instant_mode_2g/instant_mode_5g
    #
    # where:
    # results: Result array to be populated -
    #          will add results (not erase it)
    # dw_24ghz: DW interval in the 2.4GHz band.
    # dw_5ghz: DW interval in the 5GHz band.
    # num_iterations: number of the iterations.
    # startup_offset: The start-up gap (in seconds) between the two devices
    # include_setup: True to include the cluster setup in the latency
    #     measurements.
    # instant_mode: set the band to use instant communication mode, 2G or 5G
    # csv_name: csv file test result name.
    #
    ####################################################

    def test_end_to_end_latency_default_dws(self):
        """Measure the latency for end-to-end communication link setup:
        - Start Aware
        - Discovery
        - NDP setup
        """
        results = {}
        self.run_end_to_end_latency(
            results,
            dw_24ghz=constants.AwarePowerSettings.POWER_DW_24_INTERACTIVE,
            dw_5ghz=constants.AwarePowerSettings.POWER_DW_5_INTERACTIVE,
            num_iterations=10,
            include_setup=True,
            csv_name="test_end_to_end_latency_default_dws"
        )
        asserts.explicit_pass(
            "test_ndp_setup_latency_non_interactive_dws finished",
            extras=results)

    def test_end_to_end_latency_default_dws_instant_mode_2g(self):
        """Measure the latency for end-to-end communication link setup:
      - Start Aware
      - Discovery
      - NDP setup
        """
        results = {}
        self.run_end_to_end_latency(
            results,
            dw_24ghz=constants.AwarePowerSettings.POWER_DW_24_INTERACTIVE,
            dw_5ghz=constants.AwarePowerSettings.POWER_DW_5_INTERACTIVE,
            num_iterations=10,
            include_setup=True,
            instant_mode="2G",
            csv_name="test_end_to_end_latency_default_dws_instant_mode_2g")
        asserts.explicit_pass(
            "test_end_to_end_latency_default_dws finished", extras=results)

    def test_end_to_end_latency_default_dws_instant_mode_5g(self):
        """Measure the latency for end-to-end communication link setup:
        - Start Aware
        - Discovery
        - NDP setup
        """
        results = {}
        self.run_end_to_end_latency(
            results,
            dw_24ghz=constants.AwarePowerSettings.POWER_DW_24_INTERACTIVE,
            dw_5ghz=constants.AwarePowerSettings.POWER_DW_5_INTERACTIVE,
            num_iterations=10,
            include_setup=True,
            instant_mode="5G",
            csv_name="test_end_to_end_latency_default_dws_instant_mode_5g")
        asserts.explicit_pass(
            "test_end_to_end_latency_default_dws finished", extras=results)

    def test_end_to_end_latency_post_attach_default_dws(self):
        """Measure the latency for end-to-end communication link setup without
        the initial synchronization:
        - Start Aware & synchronize initially
        - Loop:
        - Discovery
        - NDP setup
        """
        results = {}
        self.run_end_to_end_latency(
            results,
            dw_24ghz=constants.AwarePowerSettings.POWER_DW_24_INTERACTIVE,
            dw_5ghz=constants.AwarePowerSettings.POWER_DW_5_INTERACTIVE,
            num_iterations=10,
            include_setup=False,
            csv_name="test_end_to_end_latency_post_attach_default_dws")
        asserts.explicit_pass(
            "test_end_to_end_latency_post_attach_default_dws finished",
            extras=results)

    def test_end_to_end_latency_post_attach_default_dws_instant_mode_2g(self):
        """Measure the latency for end-to-end communication link setup without
        the initial synchronization:
        - Start Aware & synchronize initially
        - Loop:
        - Discovery
        - NDP setup
        """
        asserts.skip_if(not(
            autils.get_aware_capabilities(self.publisher)["isInstantCommunicationModeSupported"]
            and autils.get_aware_capabilities(self.subscriber)["isInstantCommunicationModeSupported"])
            ,"Device doesn't support instant communication mode"
        )

        results = {}
        self.run_end_to_end_latency(
            results,
            dw_24ghz=constants.AwarePowerSettings.POWER_DW_24_INTERACTIVE,
            dw_5ghz=constants.AwarePowerSettings.POWER_DW_5_INTERACTIVE,
            num_iterations=10,
            include_setup=False,
            instant_mode="2G",
            csv_name="test_end_to_end_latency_post_attach_default_dws_instant_mode_2g")
        asserts.explicit_pass(
            "test_end_to_end_latency_post_attach_default_dws_instant_mode finished",
            extras=results)

    def test_end_to_end_latency_post_attach_default_dws_instant_mode_5g(self):
        """Measure the latency for end-to-end communication link setup without
        the initial synchronization:
        - Start Aware & synchronize initially
        - Loop:
        - Discovery
        - NDP setup
        """

        asserts.skip_if(not(
                autils.get_aware_capabilities(self.publisher)["isInstantCommunicationModeSupported"]
                and autils.get_aware_capabilities(self.subscriber)["isInstantCommunicationModeSupported"])
                        ,"Device doesn't support instant communication mode"
        )
        results = {}
        self.run_end_to_end_latency(
            results,
            dw_24ghz=constants.AwarePowerSettings.POWER_DW_24_INTERACTIVE,
            dw_5ghz=constants.AwarePowerSettings.POWER_DW_5_INTERACTIVE,
            num_iterations=10,
            include_setup=False,
            instant_mode="5G",
            csv_name="test_end_to_end_latency_post_attach_default_dws_instant_mode_5g")
        asserts.explicit_pass(
            "test_end_to_end_latency_post_attach_default_dws_instant_mode finished",
            extras=results)


if __name__ == '__main__':
    # Take test args
    if '--' in sys.argv:
        index = sys.argv.index('--')
        sys.argv = sys.argv[:1] + sys.argv[index + 1:]

    test_runner.main()
