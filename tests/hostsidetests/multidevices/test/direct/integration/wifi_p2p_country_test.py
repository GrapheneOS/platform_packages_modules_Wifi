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

from collections.abc import Sequence
import logging
import time

from android.platform.test.annotations import ApiTest
from mobly import asserts
from mobly import base_test
from mobly import records
from mobly import test_runner
from mobly import utils
from mobly.controllers import android_device
import wifi_test_utils
import wifi_p2p_lib as wp2putils


from direct import constants
from direct import p2p_utils


_DEFAULT_FUNCTION_SWITCH_TIME = 10
_P2P_CONNECT_APIS = ApiTest(
    apis=[
        'android.net.wifi.p2p.WifiP2pManager#requestConnectionInfo('
        'android.net.wifi.p2p.WifiP2pManager.Channel channel, '
        'android.net.wifi.p2p.WifiP2pManager.ConnectionInfoListener listener)',
        'android.net.wifi.p2p.WifiP2pManager#connect('
        'android.net.wifi.p2p.WifiP2pManager.Channel channel, '
        'android.net.wifi.p2p.WifiP2pConfig config, '
        'android.net.wifi.p2p.WifiP2pManager.ActionListener listener)',
    ]
)


class WifiP2pCountryTestCases(base_test.BaseTestClass):
    """P2p negotiation with country test."""

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
            f'{self.group_owner_ad.serial}(Group Owner)'
        )
        self.client_ad.debug_tag = f'{self.client_ad.serial}(Client)'

    def teardown_test(self) -> None:
        utils.concurrent_exec(
            self._teardown_device,
            param_list=[[ad] for ad in self.ads],
            raise_on_exception=True,
        )

    def on_fail(self, record: records.TestResult) -> None:
        logging.info('Collecting bugreports...')
        android_device.take_bug_reports(
            self.ads, destination=self.current_test_info.output_path
        )

    def _setup_device(self, ad: android_device.AndroidDevice) -> None:
        ad.load_snippet('wifi', constants.WIFI_SNIPPET_PACKAGE_NAME)
        wifi_test_utils.set_screen_on_and_unlock(ad)
        # Clear all saved Wi-Fi networks.
        ad.wifi.wifiDisable()
        ad.wifi.wifiClearConfiguredNetworks()
        ad.wifi.wifiEnable()

    def _teardown_device(self, ad: android_device.AndroidDevice):
        p2p_utils.teardown_wifi_p2p(ad)

    def set_wifi_country_code(
        self,
        ad: android_device.AndroidDevice,
        country_code: str):
        """Sets the wifi country code on the device.

        Args:
            ad: An AndroidDevice object.
            country_code: 2 letter ISO country code

        Raises:
            An RpcException if unable to set the country code.
        """
        try:
            ad.adb.shell('cmd wifi force-country-code enabled %s' % country_code)
        except android_device.adb.AdbError as e:
            ad.log.error(f"Failed to set country code: {e}")
            ad.droid.wifiSetCountryCode(constants.CountryCode.US)

    def p2p_connect_ping_and_reconnect_country(self, psk, country):
        """Verify the p2p connect via pbc/display functionality.

        Steps:
        1. Request the connection which include discover the target device
        2. check which dut is GO and which dut is GC
        3. connection check via ping from GC to GO
        4. disconnect
        5. Trigger connect again from GO for reconnect test.
        6. GO trigger disconnect
        7. Trigger connect again from GC for reconnect test.
        8. GC trigger disconnect

        Args:
            psk: Connect via pbc/display
            country: CountryCode String.

        """
        go_dut = self.ads[0]
        gc_dut = self.ads[1]

        self.set_wifi_country_code(go_dut , country)
        self.set_wifi_country_code(gc_dut , country)
        group_owner = p2p_utils.setup_wifi_p2p(go_dut)
        client = p2p_utils.setup_wifi_p2p(gc_dut)
        requester_peer_p2p_device = p2p_utils.discover_p2p_peer(
            group_owner, client
            )
        wp2putils.p2p_connect(group_owner, client, False, psk)
        if wp2putils.is_go(self.ads[0]) :
            client_dut = self.ads[1]
        else:
            client_dut = self.ads[0]
        logging.info('Client is : %s', client_dut.serial)
        go_ip = wp2putils.p2p_go_ip(client_dut)
        wp2putils.p2p_connection_ping_test(client_dut, go_ip)
        p2p_utils.remove_group_and_verify_disconnected(
            group_owner, client, is_group_negotiation=False
            )
        time.sleep(_DEFAULT_FUNCTION_SWITCH_TIME)
        wp2putils.p2p_connect(client, group_owner, True,  psk)
        p2p_utils.remove_group_and_verify_disconnected(
            group_owner, client, is_group_negotiation=False
            )
        time.sleep(_DEFAULT_FUNCTION_SWITCH_TIME)
        wp2putils.p2p_connect(group_owner, client, True, psk)

    @_P2P_CONNECT_APIS
    def test_p2p_connect_via_pbc_and_ping_and_reconnect_US(self) -> None:
        self.p2p_connect_ping_and_reconnect_country(constants.WpsInfo.PBC, 'US')

    @_P2P_CONNECT_APIS
    def test_p2p_connect_via_pbc_and_ping_and_reconnect_JP(self) -> None:
        self.p2p_connect_ping_and_reconnect_country(constants.WpsInfo.PBC, 'JP')

    @_P2P_CONNECT_APIS
    def test_p2p_connect_via_pbc_and_ping_and_reconnect_DE(self) -> None:
        self.p2p_connect_ping_and_reconnect_country(constants.WpsInfo.PBC, 'DE')

    @_P2P_CONNECT_APIS
    def test_p2p_connect_via_pbc_and_ping_and_reconnect_AU(self) -> None:
        self.p2p_connect_ping_and_reconnect_country(constants.WpsInfo.PBC, 'AU')

    @_P2P_CONNECT_APIS
    def test_p2p_connect_via_pbc_and_ping_and_reconnect_TW(self) -> None:
        self.p2p_connect_ping_and_reconnect_country(constants.WpsInfo.PBC, 'TW')

    @_P2P_CONNECT_APIS
    def test_p2p_connect_via_pbc_and_ping_and_reconnect_GB(self) -> None:
        self.p2p_connect_ping_and_reconnect_country(constants.WpsInfo.PBC, 'GB')

    @_P2P_CONNECT_APIS
    def test_p2p_connect_via_pbc_and_ping_and_reconnect_DZ(self) -> None:
        self.p2p_connect_ping_and_reconnect_country(constants.WpsInfo.PBC, 'DZ')

    @_P2P_CONNECT_APIS
    def test_p2p_connect_via_pbc_and_ping_and_reconnect_ID(self) -> None:
        self.p2p_connect_ping_and_reconnect_country(constants.WpsInfo.PBC, 'ID')

    @_P2P_CONNECT_APIS
    def test_p2p_connect_via_display_and_ping_and_reconnect_US(self) -> None:
        self.p2p_connect_ping_and_reconnect_country(constants.WpsInfo.DISPLAY, 'US')

    @_P2P_CONNECT_APIS
    def test_p2p_connect_via_display_and_ping_and_reconnect_JP(self) -> None:
        self.p2p_connect_ping_and_reconnect_country(constants.WpsInfo.DISPLAY, 'JP')

    @_P2P_CONNECT_APIS
    def test_p2p_connect_via_display_and_ping_and_reconnect_DE(self) -> None:
        self.p2p_connect_ping_and_reconnect_country(constants.WpsInfo.DISPLAY, 'DE')

    @_P2P_CONNECT_APIS
    def test_p2p_connect_via_display_and_ping_and_reconnect_AU(self) -> None:
        self.p2p_connect_ping_and_reconnect_country(constants.WpsInfo.DISPLAY, 'AU')

    @_P2P_CONNECT_APIS
    def test_p2p_connect_via_display_and_ping_and_reconnect_TW(self) -> None:
        self.p2p_connect_ping_and_reconnect_country(constants.WpsInfo.DISPLAY, 'TW')

    @_P2P_CONNECT_APIS
    def test_p2p_connect_via_display_and_ping_and_reconnect_GB(self) -> None:
        self.p2p_connect_ping_and_reconnect_country(constants.WpsInfo.DISPLAY, 'GB')

    @_P2P_CONNECT_APIS
    def test_p2p_connect_via_display_and_ping_and_reconnect_DZ(self) -> None:
        self.p2p_connect_ping_and_reconnect_country(constants.WpsInfo.DISPLAY, 'DZ')

    @_P2P_CONNECT_APIS
    def test_p2p_connect_via_display_and_ping_and_reconnect_ID(self) -> None:
        self.p2p_connect_ping_and_reconnect_country(constants.WpsInfo.DISPLAY, 'ID')


if __name__ == '__main__':
    test_runner.main()