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
"""Test cases that create p2p group with group configurations."""

from collections.abc import Sequence
import dataclasses
import datetime
import logging

from mobly import asserts
from mobly import base_test
from mobly import records
from mobly import signals
from mobly import test_runner
from mobly import utils
from mobly.controllers import android_device

from direct import constants
from direct import p2p_utils
import wifi_test_utils
from android.platform.test.annotations import ApiTest

_FREQ_2G = 2447
_GROUP_OWNER_DISCOVER_RETRY = 3


class GroupOwnerWithConfigTest(base_test.BaseTestClass):
    """Test cases that create p2p group with group configurations.

    All tests in this class share the same test steps and expected results.
    The difference is that they are testing against different p2p group
    configurations.

    Test Preconditions:
        Two Android phones that support Wi-Fi Direct.

    Test Steps:
        1. Initialize Wi-Fi p2p on both devices.
        2. Create a p2p group on one device (group owner device) with specific
           group configuration. Verify its p2p role is group owner.
        3. Initiate p2p device discovery on another device (client device).
           Verify that it discovers the group owner.
        4. The client connects the group owner with the same group
           configuration. Verify both devices show connection established
           status.
        5. Remove the p2p group on the client. Verify both devices show
           connection stopped status.
    """

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

    def _setup_device(self, ad: android_device.AndroidDevice) -> None:
        ad.load_snippet('wifi', constants.WIFI_SNIPPET_PACKAGE_NAME)
        wifi_test_utils.set_screen_on_and_unlock(ad)
        wifi_test_utils.enable_wifi_verbose_logging(ad)
        # Clear all saved Wi-Fi networks.
        ad.wifi.wifiDisable()
        ad.wifi.wifiClearConfiguredNetworks()
        ad.wifi.wifiEnable()

    @ApiTest([
        'android.net.wifi.p2p.WifiP2pManager#createGroup(android.net.wifi.p2p.WifiP2pManager.Channel, android.net.wifi.p2p.WifiP2pConfig, android.net.wifi.p2p.WifiP2pManager.ActionListener)',
        'android.net.wifi.p2p.WifiP2pManager#removeGroup(android.net.wifi.p2p.WifiP2pManager.Channel, android.net.wifi.p2p.WifiP2pManager.ActionListener)',
    ])
    def test_p2p_group_with_band_auto(self) -> None:
        """Tests p2p group without specifying band or frequency.

        See class docstring for the test steps and expected results.
        """
        self._test_create_and_join_p2p_group(
            constants.WifiP2pConfig(
                network_name='DIRECT-XY-HELLO-%s' % utils.rand_ascii_str(5),
                passphrase='PWD-%s' % utils.rand_ascii_str(5),
                group_operating_band=None,
                group_operating_frequency=None,
            )
        )

    @ApiTest([
        'android.net.wifi.p2p.WifiP2pManager#createGroup(android.net.wifi.p2p.WifiP2pManager.Channel, android.net.wifi.p2p.WifiP2pConfig, android.net.wifi.p2p.WifiP2pManager.ActionListener)',
        'android.net.wifi.p2p.WifiP2pManager#removeGroup(android.net.wifi.p2p.WifiP2pManager.Channel, android.net.wifi.p2p.WifiP2pManager.ActionListener)',
    ])
    def test_p2p_group_with_band_2g(self) -> None:
        """Tests p2p group by forcing the system to pick the frequency from
        the 2.4GHz band.

        See class docstring for the test steps and expected results.
        """
        network_name = 'DIRECT-XY-HELLO-2.4G-%s' % utils.rand_ascii_str(5)
        self._test_create_and_join_p2p_group(
            constants.WifiP2pConfig(
                network_name=network_name,
                passphrase='PWD-%s' % utils.rand_ascii_str(5),
                group_operating_band=constants.Band.GROUP_OWNER_BAND_2GHZ,
                group_operating_frequency=None,
            )
        )

    @ApiTest([
        'android.net.wifi.p2p.WifiP2pManager#createGroup(android.net.wifi.p2p.WifiP2pManager.Channel, android.net.wifi.p2p.WifiP2pConfig, android.net.wifi.p2p.WifiP2pManager.ActionListener)',
        'android.net.wifi.p2p.WifiP2pManager#removeGroup(android.net.wifi.p2p.WifiP2pManager.Channel, android.net.wifi.p2p.WifiP2pManager.ActionListener)',
    ])
    def test_p2p_group_with_fixed_frequency(self) -> None:
        """Tests p2p group by forcing the system to a fixed operating frequency.

        See class docstring for the test steps and expected results.
        """
        network_name = (
            'DIRECT-XY-HELLO-%dMHz-%s' % ( _FREQ_2G, utils.rand_ascii_str(5))
        )
        self._test_create_and_join_p2p_group(
            constants.WifiP2pConfig(
                network_name=network_name,
                passphrase='PWD-%s' % utils.rand_ascii_str(5),
                group_operating_band=None,
                group_operating_frequency=_FREQ_2G,
            )
        )

    def _test_create_and_join_p2p_group(
        self, p2p_config: constants.WifiP2pConfig
    ):
        # Step 1. Initialize Wi-Fi p2p on both devices.
        group_owner = p2p_utils.setup_wifi_p2p(self.group_owner_ad)
        client = p2p_utils.setup_wifi_p2p(self.client_ad)

        # Step 2. Create a p2p group on one device (group owner device) with
        # specific group configuration.
        p2p_utils.create_group(group_owner, config=p2p_config)

        # Step 3. Initiate p2p device discovery on the client device.
        # Adding a retry because GC might fail to discover GO in one scan
        # attempt. GO is operating on channel 149, P2P protocol scans channel
        # 149 only once as a part of full scan in P2P_FIND.
        for i in range(_GROUP_OWNER_DISCOVER_RETRY):
            try:
                peer_p2p_device = p2p_utils.discover_group_owner(
                    client=client,
                    group_owner_address=group_owner.p2p_device.device_address
                )
                break
            except signals.TestFailure:
                if i == _GROUP_OWNER_DISCOVER_RETRY - 1:
                    raise
                client.ad.log.exception(
                    'Failed to discover group owner %s. Trying again...',
                    group_owner.p2p_device.device_address
                )
        asserts.assert_true(
            peer_p2p_device.is_group_owner,
            f'P2p device {peer_p2p_device} should be group owner.',
        )

        # Step 4. The client connects the group owner with the same p2p group
        # configuration.
        p2p_utils.p2p_connect(client, group_owner, p2p_config)

        # Step 5. Remove the p2p group on the client.
        p2p_utils.remove_group_and_verify_disconnected(
            client, group_owner, is_group_negotiation=False
        )

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


if __name__ == '__main__':
    test_runner.main()
