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

"""Wi-Fi Aware Throughput test reimplemented in Mobly."""

import logging
import sys
import time
import json
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
_TRANSPORT_TYPE_WIFI_AWARE = (
    constants.NetworkCapabilities.Transport.TRANSPORT_WIFI_AWARE
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
_SUBSCRIBE_TYPE_PASSIVE = 0

_REQUEST_NETWORK_TIMEOUT_MS = 15 * 1000

_NETWORK_CB_KEY_WIFI_AWARE_CHANNEL = "wifi_aware_channel"


class WifiAwareThroughputTest(base_test.BaseTestClass):
    """Set of tests for Wi-Fi Aware data-path."""

    # message ID counter to make sure all uses are unique
    msg_id = 0

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
          autils.validate_forbidden_callbacks(ad)
          autils.reset_device_statistics(ad)

    def on_fail(self, record: records.TestResult) -> None:
        android_device.take_bug_reports(self.ads,
                                        destination =
                                        self.current_test_info.output_path)

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

    def network_callback_event(self,
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
        for event in events:
            if event.data[_CALLBACK_NAME] == name:
                return event
        # If the loop completes without finding a match, raise an error.
        raise ValueError(f"Callback with name '{name}' not found.")

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
        network_request_dict = constants.NetworkRequest(
            transport_type=constants.NetworkCapabilities.Transport.TRANSPORT_WIFI_AWARE,
            network_specifier_parcel=network_specifier_parcel["result"],
        ).to_dict()
        return ad.wifi_aware_snippet.connectivityRequestNetwork(
            net_work_request_id, network_request_dict, _REQUEST_NETWORK_TIMEOUT_MS
        )

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
        init_dut_accept_handler = (
            init_dut.wifi_aware_snippet.connectivityServerSocketAccept())
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
        init_callback_event = self.network_callback_event(
            init_req_key,
            constants.NetworkCbEventName.NETWORK_CALLBACK,
            timeout=_DEFAULT_TIMEOUT
        )
        init_name_data =(
            self.find_callback_name(init_callback_event,
                               constants.NetworkCbName.ON_CAPABILITIES_CHANGED)
        )
        init_name = init_name_data.data[_CALLBACK_NAME]
        resp_callback_event = self.network_callback_event(
            resp_req_key,
            constants.NetworkCbEventName.NETWORK_CALLBACK,
            timeout=_DEFAULT_TIMEOUT
        )
        resp_name_data =(
            self.find_callback_name(
                resp_callback_event,
                constants.NetworkCbName.ON_CAPABILITIES_CHANGED)
        )
        resp_name = resp_name_data.data[_CALLBACK_NAME]
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
        init_net_event_nc = init_name_data.data
        resp_net_event_nc = resp_name_data.data
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
        channel = resp_net_event_nc[constants.NetworkCbEventKey.CHANNEL_IN_MHZ]
        # note that Pub <-> Sub since IPv6 are of peer's!
        init_callback_LINK =(
            self.find_callback_name(init_callback_event,
                                    _NETWORK_CB_LINK_PROPERTIES_CHANGED))
        asserts.assert_equal(
            init_callback_LINK.data[_CALLBACK_NAME],
            _NETWORK_CB_LINK_PROPERTIES_CHANGED,
            f'{init_dut} succeeded to request the'+
            ' LinkPropertiesChanged, got callback'
            f' {init_callback_LINK.data[_CALLBACK_NAME]}.'
                )
        resp_callback_LINK =(
            self.find_callback_name(resp_callback_event,
                                    _NETWORK_CB_LINK_PROPERTIES_CHANGED))
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
            init_ipv6, resp_ipv6, channel)

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
        resp_callback_event = self.network_callback_event(
            sub_network_cb_handler,
            constants.NetworkCbEventName.NETWORK_CALLBACK,
            timeout=_DEFAULT_TIMEOUT
        )
        init_callback_event = self.network_callback_event(
            pub_network_cb_handler,
            constants.NetworkCbEventName.NETWORK_CALLBACK,
            timeout=_DEFAULT_TIMEOUT
        )
        init_net_event_nc = self.find_callback_name(
            init_callback_event,constants.NetworkCbName.ON_CAPABILITIES_CHANGED
        )
        resp_net_event_nc = self.find_callback_name(
            resp_callback_event,constants.NetworkCbName.ON_CAPABILITIES_CHANGED
        )
        s_ipv6 = resp_net_event_nc.data[constants.NetworkCbName.NET_CAP_IPV6]
        p_ipv6 = init_net_event_nc.data[constants.NetworkCbName.NET_CAP_IPV6]
        channel = init_net_event_nc.data[constants.NetworkCbEventKey.CHANNEL_IN_MHZ]
        p_network_callback_LINK = self.find_callback_name(
            init_callback_event,_NETWORK_CB_LINK_PROPERTIES_CHANGED)
        s_network_callback_LINK = self.find_callback_name(
            resp_callback_event,_NETWORK_CB_LINK_PROPERTIES_CHANGED)
        s_aware_if = s_network_callback_LINK.data[
            _NETWORK_CB_KEY_INTERFACE_NAME]
        p_aware_if = p_network_callback_LINK.data[
            _NETWORK_CB_KEY_INTERFACE_NAME]
        p_dut.log.info('interfaceName = %s, ipv6=%s', p_aware_if, p_ipv6)
        s_dut.log.info('interfaceName = %s, ipv6=%s', s_aware_if, s_ipv6)
        return (
            init_net_event_nc,
            resp_net_event_nc,
            p_aware_if,
            s_aware_if,
            p_ipv6,
            s_ipv6,
            channel,
        )

    def run_iperf_single_ndp_aware_only(self, use_ib, results):
        """Establishes a Wi-Fi Aware NDP data-path and runs an iperf3 test.
        This method sets up a Wi-Fi Aware connection between two devices using
        the Neighbor Discovery Protocol (NDP). It supports both in-band (IB) and
        out-of-band (OOB) discovery mechanisms to establish the link.
        After the data-path is established, it runs an iperf3 test to measure
        throughput, with one device acting as the server and the other as the
        client. The final transmit and receive rates are parsed from the iperf3
        JSON output and stored in the provided results dictionary.
        Args:
            use_ib: If True, the data-path is established using in-band
                discovery. If False, out-of-band discovery is used.
            results: A dictionary to which the iperf3 test results (tx_rate,
                rx_rate) will be added.
        """
        init_dut = self.ads[0]
        resp_dut = self.ads[1]
        asserts.skip_if(
            not init_dut.is_adb_root or not resp_dut.is_adb_root,
            'Country code toggle needs Android device(s) with root permission',
        )
        if use_ib:
            (resp_req_key, init_req_key, resp_aware_if, init_aware_if,
             resp_ipv6, init_ipv6, aware_channel) = self.create_data_ib_ndp(
                init_dut, resp_dut,
                 autils.create_discovery_config(
                     self.SERVICE_NAME,
                     _PUBLISH_TYPE_UNSOLICITED),
                 autils.create_discovery_config(
                     self.SERVICE_NAME,
                     _SUBSCRIBE_TYPE_PASSIVE),
                 )
        else:
            init_id, init_mac = self.attach_with_identity(init_dut)
            resp_id, resp_mac = self.attach_with_identity(resp_dut)
            time.sleep(self.WAIT_FOR_CLUSTER)
            (init_req_key, resp_req_key, init_aware_if, resp_aware_if, init_ipv6,
             resp_ipv6, aware_channel) = self.create_oob_ndp_on_sessions(
                init_dut, resp_dut, init_id,
                init_mac, resp_id, resp_mac)
        logging.info("Interface names: I=%s, R=%s", init_aware_if,
                      resp_aware_if)
        logging.info("Interface addresses (IPv6): I=%s, R=%s", init_ipv6,
                      resp_ipv6)
        #  Run iperf3
        autils.iperf_server(resp_dut,  "-D")
        result, data = init_dut.run_iperf_client(resp_ipv6, "-6 -J")
        pub_accept_handler = (
            init_dut.wifi_aware_snippet.connectivityServerSocketAccept()
        )
        network_id = pub_accept_handler.callback_id
        # clean-up
        resp_dut.wifi_aware_snippet.connectivityUnregisterNetwork(network_id)
        init_dut.wifi_aware_snippet.connectivityUnregisterNetwork(network_id)
        # Collect results
        data_json = json.loads("".join(data))
        if "error" in data_json:
            asserts.fail(
                "iperf run failed: %s" % data_json["error"], extras=data_json)
        results["tx_rate_mbps"] = data_json["end"]["sum_sent"]["bits_per_second"] / constants.BITS_TO_MBPS
        results["rx_rate_mbps"] = data_json["end"]["sum_received"][
            "bits_per_second"] / constants.BITS_TO_MBPS
        results["wifi_aware_channel"] = aware_channel
        logging.info(
            "iPerf3: Sent = %d Mbps Received = %d Mbps, wifi_aware_channel = %s",
            results["tx_rate_mbps"],
            results["rx_rate_mbps"],
            results["wifi_aware_channel"])

    def run_test_traffic_latency_single_ndp_ib_aware_only_open(self):
        """Measure IPv6 traffic latency performance(ping) on NDP between 2 devices.
        Security config is open.
        """
        init_dut = self.ads[0]
        init_dut.pretty_name = "Initiator"
        resp_dut = self.ads[1]
        resp_dut.pretty_name = "Responder"
        (resp_req_key, init_req_key, resp_aware_if, init_aware_if,
         resp_ipv6, init_ipv6, channel) = self.create_data_ib_ndp(
            resp_dut, init_dut,
            autils.create_discovery_config(
                self.SERVICE_NAME, _PUBLISH_TYPE_UNSOLICITED),
            autils.create_discovery_config(
                self.SERVICE_NAME, _SUBSCRIBE_TYPE_PASSIVE),
        )
        p_req_key = init_req_key
        s_req_key = resp_req_key
        p_aware_if = init_aware_if
        s_aware_if = resp_aware_if
        p_ipv6 = init_ipv6
        s_ipv6 = resp_ipv6
        logging.info("Interface names: P=%s, S=%s", p_aware_if, s_aware_if)
        logging.info("Interface addresses (IPv6): P=%s, S=%s", p_ipv6, s_ipv6)
        logging.info("Start ping %s from %s", s_ipv6, p_ipv6)
        latency_result = autils.run_ping6(init_dut, s_ipv6)
        decoded_output = latency_result.decode('utf-8')
        data_list = []
        data_list.append(decoded_output)
        ping_output = data_list[0]
        pattern = r'rtt min/avg/max/mdev = ([\d./]+)'
        time_pattern = r'time (\d+ms)\s+'
        total_ping_time = re.findall(time_pattern, ping_output)
        avg_ping_time = re.findall(pattern, ping_output)
        logging.info(
            "The traffic min/avg/max/mdev ping results: %s, the time: %s",
            avg_ping_time, total_ping_time)
        results = {
            "avg_ping_result: avg_min/avg/max/mdev": avg_ping_time,
            "total_ping_time_ms": total_ping_time
        }
        ping_data=json.dumps(results)
        asserts.explicit_pass(
            "traffic_latency_single_ndp_ib_aware_only result", ping_data)


    ####################################################
    #  Wi-Fi Aware NDP data-path and runs an iperf3 test:
    #
    # names is: test_iperf_single_ndp_aware_only_ib/oob
    # where:
    # use_ib: If True, the data-path is established using in-band
    #         discovery. If False, out-of-band discovery is used..
    # results:  A dictionary to which the iperf3 test results (tx_rate,
    #             rx_rate) will be added.
    ####################################################

    def test_iperf_single_ndp_aware_only_ib(self):
        """Measure throughput using iperf on a single NDP, with Aware enabled and
    ````no infrastructure connection. Use in-band discovery."""

        results = {}
        self.run_iperf_single_ndp_aware_only(use_ib=True, results=results)
        asserts.explicit_pass(
            "test_iperf_single_ndp_aware_only_ib passes", extras=results)

    ####################################################
    #  Measure IPv6 traffic latency performance(ping) on NDP between 2 devices
    ####################################################

    def test_traffic_latency_single_ndp_ib_aware_only_open(self):
        """Test IPv6 traffic latency performance on NDP
         with security config is open.
        """
        self.run_test_traffic_latency_single_ndp_ib_aware_only_open()


if __name__ == '__main__':
    # Take test args
    if '--' in sys.argv:
        index = sys.argv.index('--')
        sys.argv = sys.argv[:1] + sys.argv[index + 1:]

    test_runner.main()
