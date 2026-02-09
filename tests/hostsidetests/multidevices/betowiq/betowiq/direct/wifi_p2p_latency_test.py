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

"""Tests Wi-Fi Direct (P2P) connection latency."""

from collections.abc import Sequence
import logging
from queue import Empty

from betowiq import wifi_p2p_lib as wp2putils
from betowiq import wifi_test_utils
from betowiq.direct import constants
from betowiq.direct import p2p_utils
from mobly import asserts
from mobly import base_test
from mobly import records
from mobly import signals
from mobly import test_runner
from mobly import utils
from mobly.controllers import android_device
from mobly.controllers.android_device_lib import adb
from mobly.snippet import errors
from snippet_uiautomator import uiautomator


_IPV6_LINK_LOCAL = (
    constants.IpProvisioningMode.GROUP_CLIENT_IP_PROVISIONING_MODE_IPV6_LINK_LOCAL
)

_IPV4_DHCP = (
    constants.IpProvisioningMode.GROUP_CLIENT_IP_PROVISIONING_MODE_IPV4_DHCP
)

class WifiP2pLatencyTest(base_test.BaseTestClass):
    """Tests Wi-Fi Direct (P2P) group  connection latency ."""

    ads: Sequence[android_device.AndroidDevice]
    group_owner_ad: android_device.AndroidDevice
    client_ad: android_device.AndroidDevice

    def setup_class(self) -> None:
        self.ads = self.register_controller(android_device, min_number=2)
        utils.concurrent_exec(
            self._setup_device,
            param_list=[[ad] for ad in self.ads],
            raise_on_exception=True,
        )
        self.group_owner_ad, self.client_ad, *_ = self.ads
        self.group_owner_ad.debug_tag = (
            f'{self.group_owner_ad.serial}-GroupOwner'
        )
        self.client_ad.debug_tag = f'{self.client_ad.serial}-Client'

    def _setup_device(self, ad: android_device.AndroidDevice) -> None:
        ad.load_snippet('wifi', constants.WIFI_SNIPPET_PACKAGE_NAME)
        # set the wifi snippet to foreground
        ad.wifi.utilityBringToForeground()
        # Load snippet UiAutomator
        ad.ui = uiautomator.UiDevice(ui=ad.wifi)
        wifi_test_utils.enable_wifi_verbose_logging(ad)
        wifi_test_utils.set_screen_on_and_unlock(ad)
        wifi_test_utils.restart_wifi_and_disable_connection_scan(ad)

    def _teardown_wifi_p2p(self, ad: android_device.AndroidDevice):
        try:
            p2p_utils.teardown_wifi_p2p(ad)
        finally:
            ad.services.create_output_excerpts_all(self.current_test_info)

    def teardown_test(self) -> None:
        utils.concurrent_exec(
            self._teardown_wifi_p2p,
            param_list=[[ad] for ad in self.ads],
            raise_on_exception=True,
        )

    def teardown_class(self):
        utils.concurrent_exec(
            wifi_test_utils.restore_wifi_auto_join,
            param_list=[[ad] for ad in self.ads],
            raise_on_exception=True,
        )

    def on_fail(self, record: records.TestResult) -> None:
        wifi_test_utils.take_bug_reports(
            self.ads, destination=self.current_test_info.output_path
        )

    def _is_group_formed(self, event):
        try:
            p2p_info = constants.WifiP2pInfo.from_dict(
                event.data[constants.EVENT_KEY_P2P_INFO]
            )
            return p2p_info.group_formed
        except KeyError:
            return False

    def establish_p2p_and_measure_latency(
        self,
        band
    )-> None:
        """Establishes a P2P connection and measures the client connection latency.

        This function orchestrates a complete P2P connection flow:
        1.  Initializes two devices, one as a Group Owner (GO) and one as a Client.
        2.  The GO creates a persistent P2P group on the specified band.
        3.  The Client initiates a connection to the GO's group. The time at which
            the connection attempt starts on the client is recorded.
        4.  The test waits for the connection to be fully established by monitoring
            P2P connection change events on the client.
        5.  The end-to-end connection latency is calculated as the difference
            between the connection established event time and the start time
            recorded in step 3.
        6.  The latency is recorded, and a ping test is performed.
        7.  The P2P group is torn down.

        Args:
            band: The operating band (2.4GHz or 5GHz) for the P2P group.
        """
        # Initialize the Android devices for Wi-Fi P2P operations, designating
        # their roles for clarity.
        group_owner = p2p_utils.setup_wifi_p2p(self.group_owner_ad)
        client = p2p_utils.setup_wifi_p2p(self.client_ad)

        # Define the P2P group configuration with
        # a unique network name and passphrase.
        p2p_config_create = constants.WifiP2pConfig(
            network_name='DIRECT-XY-HELLO-%s' % utils.rand_ascii_str(5),
            passphrase='PWD-%s' % utils.rand_ascii_str(5),
            group_operating_band=band,
        )
        # Instruct the Group Owner device to create the P2P group.
        p2p_utils.create_group(group_owner, config=p2p_config_create)

        latency_ms = None
        try:
            logging.info(
                "Initiating P2P connection from client and measuring latency...")
            # Call the snippet method on the client to start the connection attempt.
            # This method returns the timestamp (in ms) just before the asynchronous
            # connect call is made.
            start_time = client.ad.wifi.startWifiP2pConnectTime(
                p2p_config_create.to_dict()
            )
            logging.info(f"Client connection process initiated at:"
                         f" {start_time} ms.")
            # Wait for the client to receive a WIFI_P2P_CONNECTION_CHANGED_ACTION
            # event indicating the group is fully formed and connected.
            connection_event = client.broadcast_receiver.waitForEvent(
                event_name=constants.WIFI_P2P_CONNECTION_CHANGED_ACTION,
                predicate=self._is_group_formed,
                timeout=p2p_utils._DEFAULT_TIMEOUT.total_seconds(),
            )
            logging.info("Client connection established event: %s",
                         connection_event)
            end_time = connection_event.data["timestampMs"]
            logging.info(f"Client connection established at: {end_time} ms.")
            # Calculate the end-to-end connection latency.
            latency_ms = end_time - start_time
            logging.info(f"P2P connection latency: {latency_ms} ms.")

        except Exception as e:
            asserts.fail(f"Failed during P2P connection or latency measurement: {e}")

        # Verify connectivity with a ping test.
        go_ip = wp2putils.p2p_go_ip(self.group_owner_ad)
        wp2putils.p2p_connection_ping_test(self.client_ad, go_ip)

        if latency_ms is not None:
            results = {
                "connection_latency_ms" : latency_ms,
            }
            asserts.explicit_pass(
                "P2P connection latency measured successfully.",
                extras=results
            )
        # Clean up by removing the P2P group.
        p2p_utils.remove_group_and_verify_disconnected(
                client, group_owner, is_group_negotiation=False
            )

    def run_ping6(
            self, dut: android_device.AndroidDevice,
            peer_ipv6: str, interface: str) -> bool:
        """Run a ping6 over the specified device/link.
        Args:
           dut: Device on which to execute ping6.
           peer_ipv6: Scoped IPv6 address of the peer to ping.
           interface: The network interface on dut to use for ping6.
        Returns:
            True if ping acks are received, False otherwise.
        """
        if not interface:
            dut.log.error("Interface is required for link-local ping6")
            return False
        cmd = f'ping6 -c 3 -W 5 {peer_ipv6}%{interface}'
        try:
            dut.log.info(f"Executing ping command: {cmd}")
            results = dut.adb.shell(cmd)
            dut.log.info("ping6 stdout: %s", results)
            # Basic check for packet loss
            if "0% packet loss" in results.decode('utf-8'):
                return True
            else:
                dut.log.warning("ping6 failed or had packet loss.")
                return False
        except adb.AdbError as e:
            dut.log.warning(
                f"ping6 command execution error: {cmd}, "
                f"stdout: {e.stdout}, stderr: {e.stderr}, ret: {e.ret_code}")
            return False

    def _measure_p2p_connection_latency(
            self, client_device, p2p_config
        ) -> tuple[int, dict]:
        """
        Instructs a client to connect to a P2P group and measures the latency.
        Args:
            client_device: The Mobly controller for the client Android device.
            p2p_config: The WifiP2pConfig object for the connection.
        Returns:
            A tuple containing:
                - The connection latency in milliseconds.
                - The connection event data dictionary.
        Raises:
            mobly.asserts.fail: If the connection fails or times out.
        """
        try:
            logging.info("Initiating P2P connection and measuring latency...")
            # Start the timer on the host side right before the RPC call
            # Wait for the client to receive a WIFI_P2P_CONNECTION_CHANGED_ACTION
            # event indicating the group is fully formed and connected.
            start_time_host=(
                client_device.ad.wifi.startWifiP2pConnectTime(
                    p2p_config.to_dict())
            )
            connection_event = client_device.broadcast_receiver.waitForEvent(
                event_name=constants.WIFI_P2P_CONNECTION_CHANGED_ACTION,
                predicate=self._is_group_formed,
                timeout=p2p_utils._DEFAULT_TIMEOUT.total_seconds(),
            )
            # Use the host-side timer for a more reliable end-to-end measurement
            end_time_host = connection_event.data["timestampMs"]
            # Calculate the end-to-end connection latency.
            latency_ms = end_time_host - start_time_host
            logging.info(f"P2P connection latency: {latency_ms} ms.")
            return latency_ms, connection_event.data
        except Empty:
            asserts.fail(
                "Connection timed out: Did not receive a 'group formed' event."
            )
        except Exception as e:
            asserts.fail(f"Failed during P2P connection: {e}")

    def _verify_p2p_connectivity(
            self,
            client_ad,
            group_owner_ad,
            connection_event_data,
            use_ipv6: bool
        ) -> None:
        """
        Performs a ping test between the client and group owner.
        Args:
            client_ad: The client AndroidDevice object.
            group_owner_ad: The group owner AndroidDevice object.
            connection_event_data: The data from the connection event.
            use_ipv6: If True, perform an IPv6 ping; otherwise, use IPv4.
        """
        if use_ipv6:
            logging.info("Starting IPv6 connectivity test...")
            # Safely extract the IPv6 address and interface name
            group_owner_ipv6 = connection_event_data.get(
                'p2pInfo', {}).get('groupOwnerAddress')
            group_owner_interface = connection_event_data.get(
                'p2pGroup', {}).get('interface')
            if not group_owner_ipv6:
                asserts.fail(
                    "Could not retrieve Group Owner IPv6 address"
                    " from connection event.")
            logging.info(
                "group_owner_ipv6: %s group_owner_interface:%s" %
                (group_owner_ipv6 , group_owner_interface)
            )
            # Perform the ping from the client to the group owner
            self.run_ping6(client_ad, group_owner_ipv6, group_owner_interface)
        else:
            logging.info("Starting IPv4 connectivity test...")
            go_ip = wp2putils.p2p_go_ip(group_owner_ad)
            if not go_ip:
                asserts.fail("Could not retrieve Group Owner IPv4 address.")
            wp2putils.p2p_connection_ping_test(client_ad, go_ip)
        logging.info("Connectivity test passed.")

    def p2p_connection_with_mode_frequency_latency(
            self, mode, frequency, use_ipv6: bool
        ) -> None:
        """
        Tests Wi-Fi P2P connection setup latency and
        connectivity at a specific frequency.

        Args:
            mode: IP provisioning mode for the group client.
            frequency: The frequency (in MHz) for the P2P group.
            use_ipv6: If True, test IPv6 connectivity; otherwise, test IPv4.
        """
        # 1. ARRANGE: Set up devices and configuration
        group_owner = p2p_utils.setup_wifi_p2p(self.group_owner_ad)
        client = p2p_utils.setup_wifi_p2p(self.client_ad)
        p2p_config = constants.WifiP2pConfig(
            network_name=f'DIRECT-XY-HELLO-{utils.rand_ascii_str(5)}',
            group_client_ip_provisioning_mode=mode,
            passphrase=f'PWD-{utils.rand_ascii_str(5)}',
            group_operating_frequency=frequency,
        )
        logging.info(f"P2P Config: {p2p_config.to_dict()}")
        try:
            p2p_utils.create_group(group_owner, config=p2p_config)
            # 2. ACT: Measure latency and verify connectivity
            latency_ms, connection_data = self._measure_p2p_connection_latency(
                client, p2p_config
            )
            self._verify_p2p_connectivity(
                self.client_ad, self.group_owner_ad, connection_data, use_ipv6
            )
            # 3. ASSERT: Report success and results
            results = {"connection_latency_ms": latency_ms}
            asserts.explicit_pass(
                "P2P connection and connectivity test passed.", extras=results
            )
        finally:
            # 4. CLEANUP: Always tear down the group
            logging.info("Tearing down P2P group.")
            p2p_utils.remove_group_and_verify_disconnected(
                client, group_owner, is_group_negotiation=False
            )

    def test_p2p_connection_with_band_5g_latency(self) -> None:
        """"Measures connection latency to a P2P group on the 5GHz band."""
        self.establish_p2p_and_measure_latency(
            band=constants.Band.GROUP_OWNER_BAND_5GHZ,
        )

    def test_p2p_connection_with_band_2g_latency(self) -> None:
        """Measures connection latency to a P2P group on the 2GHz band."""
        self.establish_p2p_and_measure_latency(
            band=constants.Band.GROUP_OWNER_BAND_2GHZ
        )

    def test_p2p_connection_with_ch6_ipv4_latency(self) -> None:
        """Measures P2P connection latency on a 2.4GHz channel with IPv4."""
        self.p2p_connection_with_mode_frequency_latency(
            mode=_IPV4_DHCP,
            frequency= 2437,
            use_ipv6=False
        )

    def test_p2p_connection_with_ch6_ipv6_latency(self) -> None:
        """Measures P2P connection latency on a 2.4GHz channel with IPv6"""
        self.p2p_connection_with_mode_frequency_latency(
            mode=_IPV6_LINK_LOCAL,
            frequency= 2437,
            use_ipv6=True
        )

    def test_p2p_connection_with_ch149_ipv4_latency(self) -> None:
        """Measures P2P connection latency on a 5GHz channel with IPv4."""
        self.p2p_connection_with_mode_frequency_latency(
            mode=_IPV4_DHCP,
            frequency= 5745,
            use_ipv6=False
        )

    def test_p2p_connection_with_ch149_ipv6_latency(self) -> None:
        """Measures P2P connection latency on a 5GHz channel with IPv6."""
        self.p2p_connection_with_mode_frequency_latency(
            mode=_IPV6_LINK_LOCAL,
            frequency= 5745,
            use_ipv6=True
        )


if __name__ == '__main__':
    test_runner.main()
