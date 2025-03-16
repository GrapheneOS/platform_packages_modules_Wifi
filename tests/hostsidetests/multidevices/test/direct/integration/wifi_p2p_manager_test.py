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
"""ACTS Wifi P2pManager Test reimplemented in Mobly."""

from collections.abc import Sequence
import datetime
import logging
import time

from android.platform.test.annotations import ApiTest
from direct import constants
from direct import p2p_utils
from mobly import base_test
from mobly import records
from mobly import test_runner
from mobly import utils
from mobly.controllers import android_device
import wifi_p2p_lib as wp2putils


_DEFAULT_TIMEOUT = datetime.timedelta(seconds=30)
DEFAULT_SLEEPTIME = 5
_DEFAULT_FUNCTION_SWITCH_TIME = 10
_DEFAULT_GROUP_CLIENT_LOST_TIME = 60

_WIFI_DIRECT_SNIPPET_KEY = 'wifi_direct_mobly_snippet'

P2P_CONNECT_NEGOTIATION = 0
P2P_CONNECT_JOIN = 1
P2P_CONNECT_INVITATION = 2

WPS_PBC = wp2putils.WifiP2PEnums.WpsInfo.WIFI_WPS_INFO_PBC
WPS_DISPLAY = wp2putils.WifiP2PEnums.WpsInfo.WIFI_WPS_INFO_DISPLAY
WPS_KEYPAD = wp2putils.WifiP2PEnums.WpsInfo.WIFI_WPS_INFO_KEYPAD


class WifiP2pManagerTest(base_test.BaseTestClass):
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

    def on_fail(self, record: records.TestResult) -> None:
        logging.info('Collecting bugreports...')
        android_device.take_bug_reports(
            self.ads, destination=self.current_test_info.output_path
        )

    @ApiTest(
        apis=[
            'android.net.wifi.p2p.WifiP2pManager#discoverPeers(android.net.wifi.p2p.WifiP2pManager.Channel channel, android.net.wifi.p2p.WifiP2pManager.ActionListener listener)',
        ]
    )
    def test_p2p_discovery(self):
        """Verify the p2p discovery functionality.

        Steps:
        1. Discover the target device
        2. Check the target device in peer list
        """
        self.ads[0].log.info('Device discovery')
        responder = p2p_utils.setup_wifi_p2p(self.ads[0])
        requester = p2p_utils.setup_wifi_p2p(self.ads[1])

        requester.ad.log.info('Searching for target device.')
        responder_p2p_dev = p2p_utils.discover_p2p_peer(responder, requester)
        self.ads[0].log.info('name= %s, address=%s, group_owner=%s',
                             responder_p2p_dev.device_name,
                             responder_p2p_dev.device_address,
                             responder_p2p_dev.is_group_owner)
        requester_p2p_dev = p2p_utils.discover_p2p_peer(requester, responder)
        self.ads[1].log.info('name= %s, address=%s, group_owner=%s',
                             requester_p2p_dev.device_name,
                             requester_p2p_dev.device_address,
                             requester_p2p_dev.is_group_owner)

    @ApiTest(
        apis=[
            'android.net.wifi.p2p.WifiP2pManager#requestConnectionInfo(android.net.wifi.p2p.WifiP2pManager.Channel channel, android.net.wifi.p2p.WifiP2pManager.ConnectionInfoListener listener)',
            'android.net.wifi.p2p.WifiP2pManager#connect(android.net.wifi.p2p.WifiP2pManager.Channel channel, android.net.wifi.p2p.WifiP2pConfig config, android.net.wifi.p2p.WifiP2pManager.ActionListener listener)',
        ]
    )
    def test_p2p_connect_via_pbc_and_ping_and_reconnect(self):
        """Verify the p2p connect via pbc functionality.

        Steps:
        1. Request the connection which include discover the target device
        2. check which dut is GO and which dut is GC
        3. connection check via ping from GC to GO
        4. disconnect
        5. Trigger connect again from GO for reconnect test.
        6. GO trigger disconnect
        7. Trigger connect again from GC for reconnect test.
        8. GC trigger disconnect
        """
        self.ads[0].log.info('Device initialize')
        go_dut = self.ads[0]
        gc_dut = self.ads[1]

        logging.info('GO: %s, GC: %s', go_dut.serial, gc_dut.serial)
        device_go = p2p_utils.setup_wifi_p2p(go_dut)
        device_gc = p2p_utils.setup_wifi_p2p(gc_dut)
        self.run_p2p_connect_and_ping(device_gc, device_go, WPS_PBC, False)
        self.run_p2p_connect_and_ping(device_go, device_gc, WPS_PBC, True)
        self.run_p2p_connect_and_ping(device_gc, device_go, WPS_PBC, True)

    @ApiTest(
        apis=[
            'android.net.wifi.p2p.WifiP2pManager#requestConnectionInfo(android.net.wifi.p2p.WifiP2pManager.Channel channel, android.net.wifi.p2p.WifiP2pManager.ConnectionInfoListener listener)',
            'android.net.wifi.p2p.WifiP2pManager#connect(android.net.wifi.p2p.WifiP2pManager.Channel channel, android.net.wifi.p2p.WifiP2pConfig config, android.net.wifi.p2p.WifiP2pManager.ActionListener listener)',
        ]
    )
    def test_p2p_connect_via_display_and_ping_and_reconnect(self):
        """Verify the p2p connect via display functionality.

        Steps:
        1. Request the connection which include discover the target device
        2. check which dut is GO and which dut is GC
        3. connection check via ping from GC to GO
        4. disconnect
        5. Trigger connect again from GO for reconnect test.
        6. GO trigger disconnect
        7. Trigger connect again from GC for reconnect test.
        8. GC trigger disconnect
        """
        self.ads[0].log.info('Device initialize')
        go_dut = self.ads[0]
        gc_dut = self.ads[1]

        logging.info('GO: %s, GC: %s', go_dut.serial, gc_dut.serial)
        device_go = p2p_utils.setup_wifi_p2p(go_dut)
        device_gc = p2p_utils.setup_wifi_p2p(gc_dut)
        self.run_p2p_connect_and_ping(device_gc, device_go, WPS_DISPLAY, False)
        self.run_p2p_connect_and_ping(device_go, device_gc, WPS_DISPLAY, True)
        self.run_p2p_connect_and_ping(device_gc, device_go, WPS_DISPLAY, True)

    @ApiTest(
        apis=[
            'android.net.wifi.p2p.WifiP2pManager#requestConnectionInfo(android.net.wifi.p2p.WifiP2pManager.Channel channel, android.net.wifi.p2p.WifiP2pManager.ConnectionInfoListener listener)',
            'android.net.wifi.p2p.WifiP2pManager#connect(android.net.wifi.p2p.WifiP2pManager.Channel channel, android.net.wifi.p2p.WifiP2pConfig config, android.net.wifi.p2p.WifiP2pManager.ActionListener listener)',
        ]
    )
    def test_p2p_connect_via_keypad_and_ping_and_reconnect(self):
        """Verify the p2p connect via keypad functionality.

        Steps:
        1. Request the connection which include discover the target device
        2. check which dut is GO and which dut is GC
        3. connection check via ping from GC to GO
        4. disconnect
        5. Trigger connect again from GO for reconnect test.
        6. GO trigger disconnect
        7. Trigger connect again from GC for reconnect test.
        8. GC trigger disconnect
        """
        self.ads[0].log.info('Device initialize')
        go_dut = self.ads[0]
        gc_dut = self.ads[1]

        logging.info('GO: %s, GC: %s', go_dut.serial, gc_dut.serial)
        device_go = p2p_utils.setup_wifi_p2p(go_dut)
        device_gc = p2p_utils.setup_wifi_p2p(gc_dut)

        self.run_p2p_connect_and_ping(device_gc, device_go, WPS_KEYPAD, False)
        self.run_p2p_connect_and_ping(device_go, device_gc, WPS_KEYPAD, True)
        self.run_p2p_connect_and_ping(device_gc, device_go, WPS_KEYPAD, True)

    def run_p2p_connect_and_ping(
        self,
        device1,
        device2,
        pws_method,
        re_connect):
        # Request the connection
        wp2putils.p2p_connect(device1, device2, re_connect, pws_method)

        if wp2putils.is_go(device1.ad):
            client_dut = device2.ad
        else:
            client_dut = device1.ad
        logging.info('Client is : %s', client_dut.serial)
        go_ip = wp2putils.p2p_go_ip(client_dut)
        wp2putils.p2p_connection_ping_test(client_dut, go_ip)

        # trigger disconnect
        p2p_utils.remove_group_and_verify_disconnected(
            device1, device2, is_group_negotiation=False
        )
        time.sleep(_DEFAULT_FUNCTION_SWITCH_TIME)

if __name__ == '__main__':
  test_runner.main()

