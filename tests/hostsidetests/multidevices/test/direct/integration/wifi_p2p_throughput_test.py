#  Copyright (C) 2025 The Android Open Source Project
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
"""ACTS Wifi P2p Throughput Test reimplemented in Mobly."""

from collections.abc import Sequence
import time
import logging
import json

from android.platform.test.annotations import ApiTest
from aware import aware_lib_utils as autils
from direct import constants
from direct import p2p_utils
from mobly import asserts
from mobly import base_test
from mobly import test_runner
from mobly import utils
from mobly.controllers import android_device
import wifi_p2p_lib as wp2putils
import wifi_test_utils


_DEFAULT_FUNCTION_SWITCH_TIME = 10

P2P_CONNECT_JOIN = 1

_P2P_CONNECT_APIS = ApiTest(
    apis=[
        'android.net.wifi.WpsInfo#PBC',
        'android.net.wifi.p2p.WifiP2pManager#createGroup('
        'android.net.wifi.p2p.WifiP2pManager.Channel,'
        'android.net.wifi.p2p.WifiP2pConfig,'
        'android.net.wifi.p2p.WifiP2pManager.ActionListener)',
        'android.net.wifi.p2p.WifiP2pManager#connect('
        'android.net.wifi.p2p.WifiP2pManager.Channel,'
        'android.net.wifi.p2p.WifiP2pConfig,'
        'android.net.wifi.p2p.WifiP2pManager.ActionListener)',
        'android.net.wifi.p2p.WifiP2pManager#removeGroup('
        'android.net.wifi.p2p.WifiP2pManager.Channel,'
        'android.net.wifi.p2p.WifiP2pManager.ActionListener)',
    ]
)


class WifiP2pThroughputTest(base_test.BaseTestClass):
    """Tests Wi-Fi Direct Throughput between 2 Android devices."""

    ads: Sequence[android_device.AndroidDevice]
    group_owner_ad: android_device.AndroidDevice
    client_ad: android_device.AndroidDevice
    network_name = 'DIRECT-xy-Hello'
    passphrase = 'P2pWorld1234'


    def setup_class(self) -> None:
        super().setup_class()
        self.ads = self.register_controller(android_device, min_number=2)
        utils.concurrent_exec(
            self._setup_device,
            param_list=[[ad] for ad in self.ads],
            raise_on_exception=True,
        )
        self.group_owner_ad, self.client_ad, *_ = self.ads
        self.group_owner_ad.debug_tag = (
            f'{self.group_owner_ad.serial}(Group Owner)'
        )
        self.client_ad.debug_tag = f'{self.client_ad.serial}(Client)'

    def _setup_device(self, ad: android_device.AndroidDevice) -> None:
        ad.load_snippet('wifi', constants.WIFI_SNIPPET_PACKAGE_NAME)
        wifi_test_utils.set_screen_on_and_unlock(ad)
        # Clear all saved Wi-Fi networks.
        ad.wifi.wifiDisable()
        ad.wifi.wifiClearConfiguredNetworks()
        ad.wifi.wifiEnable()

    def teardown_test(self) -> None:
        for ad in self.ads:
            ad.wifi.p2pClose()
            ad.wifi.wifiDisable()
            ad.wifi.wifiClearConfiguredNetworks()
            ad.wifi.wifiEnable()

        utils.concurrent_exec(
            lambda d: d.services.create_output_excerpts_all(
                self.current_test_info
            ),
            param_list=[[ad] for ad in self.ads],
            raise_on_exception=True,
        )


    def p2p_group_join(self, band, wps_config: constants.WpsInfo):
        """General flow for p2p group join.

        Steps:
        1. GO creates a group.
        2. GC joins the group.
        3. connection check via ping from GC to GO

        Args:
            band: P2p band for the group.
            wps_config: WPS configuration for the group.
        """
        go_dut = self.ads[0]
        gc_dut = self.ads[1]
        go_dut.log.info('Initializing Wi-Fi p2p.')
        group_owner = p2p_utils.setup_wifi_p2p(go_dut)
        client = p2p_utils.setup_wifi_p2p(gc_dut)
        # Create a group.
        p2p_config = constants.WifiP2pConfig(
            network_name='DIRECT-XY-HELLO-%s' % utils.rand_ascii_str(5),
            passphrase=self.passphrase,
            group_operating_band=band,
        )
        p2p_utils.create_group(group_owner, config=p2p_config)
        time.sleep(_DEFAULT_FUNCTION_SWITCH_TIME)

        # Request the connection.
        wp2putils.p2p_connect(client, group_owner, False, wps_config,
                              p2p_connect_type=P2P_CONNECT_JOIN)
        go_ip = wp2putils.p2p_go_ip(gc_dut)
        wp2putils.p2p_connection_ping_test(gc_dut, go_ip)
        autils.iperf_server(go_dut,  "-D")
        result, data = gc_dut.run_iperf_client(go_ip, "-p 5201 -J")
        try:
            iperf_output_string = "".join(data)
            # Change: Use json.loads() to parse the JSON string into a Python dictionary.
            data_json = json.loads(iperf_output_string)
        except json.JSONDecodeError as e:
            # Added error handling for malformed JSON
            logging.error("Failed to parse iperf3 JSON output: %s", e)
            logging.error("Raw data was: %s", data)
            return
        # Change: Check for "error" key in the parsed dictionary.
        if "error" in data_json:
            logging.error("iperf run failed: %s", data_json["error"])
            return

        results = {}
        # Change: Access the dictionary keys to get the correct values.
        # Ensure the 'end' and 'sum_sent' keys exist before accessing.
        try:
            results["tx_rate_mbps"] = (
                    data_json["end"]["sum_sent"]["bits_per_second"
                    ] / constants.BITS_TO_MBPS
            )
            results["rx_rate_mbps"] = (
                    data_json["end"]["sum_received"][
                        "bits_per_second"] / constants.BITS_TO_MBPS
            )
        except KeyError as e:
            logging.error("Missing key in iperf3 JSON output: %s", e)
            return

        logging.info("iPerf3: Sent = %d Mbps Received = %d Mbps",
                     results["tx_rate_mbps"],
                     results["rx_rate_mbps"])

        # Trigger p2p disconnect.
        p2p_utils.remove_group_and_verify_disconnected(
            client, group_owner, is_group_negotiation=False
        )
        asserts.explicit_pass(
            "test p2p_group_throughput passes", extras=results)

        time.sleep(_DEFAULT_FUNCTION_SWITCH_TIME)

    def check_channel_list_exist(
            self, ad: android_device.AndroidDevice, band):
        """Check the channel list is None then skip test.

			Args:
				ad: android_device.
				band: band for the setting.
			"""
        usable_channels  = ad.wifi.wifiGetUsableChannels(
            band, constants.WifiAvailableChannelMode.OP_MODE_WIFI_DIRECT_GO
        )
        logging.info("This band usable_channels list is: %s", usable_channels)
        asserts.skip_if(
            not usable_channels ,
            "No usable channels found on setup, skipping test case")

    @_P2P_CONNECT_APIS
    def test_iperf_p2p_group_join_via_pbc_with_2g(self):
        """Measure throughput using iperf on WPS PBC and 2G mode."""
        self.check_channel_list_exist(
        self.group_owner_ad, constants.Band.GROUP_OWNER_BAND_2GHZ
        )
        self.p2p_group_join(
            constants.Band.GROUP_OWNER_BAND_2GHZ, constants.WpsInfo.PBC)

    @_P2P_CONNECT_APIS
    def test_iperf_p2p_group_join_via_pbc_with_5g(self):
        """Measure throughput using iperf on WPS PBC and 5G mode."""
        self.check_channel_list_exist(
            self.group_owner_ad, constants.Band.GROUP_OWNER_BAND_5GHZ_WITH_DFS
        )
        self.p2p_group_join(
            constants.Band.GROUP_OWNER_BAND_5GHZ, constants.WpsInfo.PBC)

    @_P2P_CONNECT_APIS
    def test_iperf_p2p_group_join_via_display_with_2g(self):
        """ Measure throughput using iperf on WPS DISPLAY and 2G mode."""
        self.check_channel_list_exist(
            self.group_owner_ad, constants.Band.GROUP_OWNER_BAND_2GHZ
        )
        self.p2p_group_join(
            constants.Band.GROUP_OWNER_BAND_2GHZ,
            constants.WpsInfo.DISPLAY)

    @_P2P_CONNECT_APIS
    def test_iperf_p2p_group_join_via_display_with_5g(self):
        """ Measure throughput using iperf on WPS DISPLAY and 5G mode."""
        self.check_channel_list_exist(
            self.group_owner_ad, constants.Band.GROUP_OWNER_BAND_5GHZ_WITH_DFS
        )
        self.p2p_group_join(
            constants.Band.GROUP_OWNER_BAND_5GHZ,
            constants.WpsInfo.DISPLAY)


if __name__ == '__main__':
  test_runner.main()

