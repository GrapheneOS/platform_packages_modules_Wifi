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
"""Tests Wi-Fi Direct (P2P) connection latency."""

from collections.abc import Sequence
import logging

from mobly import asserts
from mobly import base_test
from mobly import records
from mobly import signals
from mobly import test_runner
from mobly import utils
from mobly.controllers import android_device
from mobly.snippet import errors
from snippet_uiautomator import uiautomator

from direct import constants
from direct import p2p_utils
import wifi_p2p_lib as wp2putils
import wifi_test_utils
from android.platform.test.annotations import ApiTest



_P2P_CONNECT_APIS = ApiTest(
    apis=[
        'android.net.wifi.p2p.WifiP2pManager#createGroup('
        'android.net.wifi.p2p.WifiP2pManager.Channel,'
        'android.net.wifi.p2p.WifiP2pConfig,'
        'android.net.wifi.p2p.WifiP2pManager.ActionListener)',
        'android.net.wifi.p2p.WifiP2pManager#removeGroup('
        'android.net.wifi.p2p.WifiP2pManager.Channel, '
        'android.net.wifi.p2p.WifiP2pManager.ActionListener)',
    ]
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

    @_P2P_CONNECT_APIS
    def test_p2p_connection_with_band_5g_latency(self) -> None:
        """"Measures connection latency to a P2P group on the 5GHz band."""
        self.establish_p2p_and_measure_latency(
            band=constants.Band.GROUP_OWNER_BAND_5GHZ,
        )

    @_P2P_CONNECT_APIS
    def test_p2p_connection_with_band_2g_latency(self) -> None:
        """Measures connection latency to a P2P group on the 2GHz band."""
        self.establish_p2p_and_measure_latency(
            band=constants.Band.GROUP_OWNER_BAND_2GHZ
        )


if __name__ == '__main__':
    test_runner.main()
