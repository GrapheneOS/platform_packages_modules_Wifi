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
"""ACTS Wifi P2p Group Test reimplemented in Mobly."""

from collections.abc import Sequence
import datetime
import time

from android.platform.test.annotations import ApiTest
from direct import constants
from direct import p2p_utils
from mobly import base_test
from mobly import test_runner
from mobly import utils
from mobly.controllers import android_device
import wifi_p2p_lib as wp2putils


_DEFAULT_TIMEOUT = datetime.timedelta(seconds=30)
DEFAULT_SLEEPTIME = 5
_DEFAULT_FUNCTION_SWITCH_TIME = 10
_DEFAULT_GROUP_CLIENT_LOST_TIME = 60

P2P_CONNECT_NEGOTIATION = 0
P2P_CONNECT_JOIN = 1
P2P_CONNECT_INVITATION = 2

WPS_PBC = wp2putils.WifiP2PEnums.WpsInfo.WIFI_WPS_INFO_PBC
WPS_DISPLAY = wp2putils.WifiP2PEnums.WpsInfo.WIFI_WPS_INFO_DISPLAY
WPS_KEYPAD = wp2putils.WifiP2PEnums.WpsInfo.WIFI_WPS_INFO_KEYPAD


class WifiP2pGroupTest(base_test.BaseTestClass):
    """Tests Wi-Fi Direct between 2 Android devices."""

    ads: Sequence[android_device.AndroidDevice]
    group_owner_ad: android_device.AndroidDevice
    client_ad: android_device.AndroidDevice
    network_name = 'DIRECT-xy-Hello'
    passphrase = 'P2pWorld1234'
    group_band = '2'

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
        # Clear all saved Wi-Fi networks.
        ad.wifi.wifiDisable()
        ad.wifi.wifiClearConfiguredNetworks()
        ad.wifi.wifiEnable()

    def teardown_test(self) -> None:
        for ad in self.ads:
            ad.wifi.p2pClose()

        utils.concurrent_exec(
            lambda d: d.services.create_output_excerpts_all(
                self.current_test_info
            ),
            param_list=[[ad] for ad in self.ads],
            raise_on_exception=True,
        )

    def p2p_group_join(self, wps_config: constants.WpsInfo):
        """General flow for p2p group join.

        Steps:
        1. GO creates a group.
        2. GC joins the group.
        3. connection check via ping from GC to GO

        Args:
            wps_config: WPS configuration for the group.
        """
        go_dut = self.ads[0]
        gc_dut = self.ads[1]

        go_dut.log.info('Initializing Wi-Fi p2p.')
        group_owner = p2p_utils.setup_wifi_p2p(go_dut)
        client = p2p_utils.setup_wifi_p2p(gc_dut)
        # Create a group.
        p2p_utils.create_group(group_owner, config=None)
        time.sleep(_DEFAULT_FUNCTION_SWITCH_TIME)

        # Request the connection.
        wp2putils.p2p_connect(client, group_owner, False, wps_config,
                              p2p_connect_type=P2P_CONNECT_JOIN)

        go_ip = wp2putils.p2p_go_ip(gc_dut)
        wp2putils.p2p_connection_ping_test(gc_dut, go_ip)
        # Trigger p2p disconnect.
        p2p_utils.remove_group_and_verify_disconnected(
            client, group_owner, is_group_negotiation=False
        )
        time.sleep(_DEFAULT_FUNCTION_SWITCH_TIME)

    @ApiTest([
        'android.net.wifi.WpsInfo#PBC',
        'android.net.wifi.p2p.WifiP2pManager#createGroup(android.net.wifi.p2p.WifiP2pManager.Channel, android.net.wifi.p2p.WifiP2pConfig, android.net.wifi.p2p.WifiP2pManager.ActionListener)',
        'android.net.wifi.p2p.WifiP2pManager#connect(android.net.wifi.p2p.WifiP2pManager.Channel, android.net.wifi.p2p.WifiP2pConfig, android.net.wifi.p2p.WifiP2pManager.ActionListener)',
        'android.net.wifi.p2p.WifiP2pManager#removeGroup(android.net.wifi.p2p.WifiP2pManager.Channel, android.net.wifi.p2p.WifiP2pManager.ActionListener)',
    ])
    def test_p2p_group_join_via_pbc(self):
        """Verify the p2p creates a group and join this group via WPS PBC method."""
        self.p2p_group_join(constants.WpsInfo.PBC)

    @ApiTest([
        'android.net.wifi.WpsInfo#DISPLAY',
        'android.net.wifi.p2p.WifiP2pManager#createGroup(android.net.wifi.p2p.WifiP2pManager.Channel, android.net.wifi.p2p.WifiP2pConfig, android.net.wifi.p2p.WifiP2pManager.ActionListener)',
        'android.net.wifi.p2p.WifiP2pManager#connect(android.net.wifi.p2p.WifiP2pManager.Channel, android.net.wifi.p2p.WifiP2pConfig, android.net.wifi.p2p.WifiP2pManager.ActionListener)',
        'android.net.wifi.p2p.WifiP2pManager#removeGroup(android.net.wifi.p2p.WifiP2pManager.Channel, android.net.wifi.p2p.WifiP2pManager.ActionListener)',
    ])
    def test_p2p_group_join_via_display(self):
        """Verify the p2p creates a group and join this group via WPS DISPLAY method."""
        self.p2p_group_join(WPS_DISPLAY)

    @ApiTest([
        'android.net.wifi.p2p.WifiP2pConfig.Builder#setPassphrase(String passphrase)',
        'android.net.wifi.p2p.WifiP2pConfig.Builder#setGroupOperatingBand(int band)',
        'android.net.wifi.p2p.WifiP2pManager#createGroup(android.net.wifi.p2p.WifiP2pManager.Channel, android.net.wifi.p2p.WifiP2pConfig, android.net.wifi.p2p.WifiP2pManager.ActionListener)',
        'android.net.wifi.p2p.WifiP2pManager#connect(android.net.wifi.p2p.WifiP2pManager.Channel, android.net.wifi.p2p.WifiP2pConfig, android.net.wifi.p2p.WifiP2pManager.ActionListener)',
        'android.net.wifi.p2p.WifiP2pManager#removeGroup(android.net.wifi.p2p.WifiP2pManager.Channel, android.net.wifi.p2p.WifiP2pManager.ActionListener)',
    ])
    def test_p2p_group_with_config(self):
        """Verify the p2p creates a group and join this group with config.

        Steps:
        1. GO creates a group with config.
        2. GC joins the group with config.
        3. connection check via ping from GC to GO
        """
        go_dut = self.ads[0]
        gc_dut = self.ads[1]
        # Initialize Wi-Fi p2p on both devices.
        group_owner = p2p_utils.setup_wifi_p2p(go_dut)
        client = p2p_utils.setup_wifi_p2p(gc_dut)

        # Create a p2p group on one device (group owner device) with
        # specific group configuration.
        p2p_config = constants.WifiP2pConfig(
            network_name='DIRECT-XY-HELLO-%s' % utils.rand_ascii_str(5),
            passphrase=self.passphrase,
            group_operating_band=self.group_band,
        )
        p2p_utils.create_group(group_owner, config=p2p_config)
        time.sleep(_DEFAULT_FUNCTION_SWITCH_TIME)
        # Request the connection. Since config is known, this is reconnection.
        p2p_utils.p2p_connect(client, group_owner, p2p_config)

        go_ip = wp2putils.p2p_go_ip(gc_dut)
        wp2putils.p2p_connection_ping_test(gc_dut, go_ip)
        # Trigger disconnect.
        p2p_utils.remove_group_and_verify_disconnected(
            client, group_owner, is_group_negotiation=False
        )
        time.sleep(_DEFAULT_FUNCTION_SWITCH_TIME)

if __name__ == '__main__':
  test_runner.main()

