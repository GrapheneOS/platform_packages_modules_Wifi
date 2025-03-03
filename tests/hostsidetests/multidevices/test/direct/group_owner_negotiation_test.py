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

from android.platform.test.annotations import ApiTest
from mobly import asserts
from mobly import base_test
from mobly import records
from mobly import test_runner
from mobly import utils
from mobly.controllers import android_device
import wifi_test_utils

from direct import constants
from direct import p2p_utils


class GroupOwnerNegotiationTest(base_test.BaseTestClass):
    """Group owner negotiation tests."""

    ads: Sequence[android_device.AndroidDevice]
    requester_ad: android_device.AndroidDevice
    responder_ad: android_device.AndroidDevice

    def setup_class(self) -> None:
        super().setup_class()
        self.ads = self.register_controller(android_device, min_number=2)
        self.responder_ad, self.requester_ad, *_ = self.ads
        self.responder_ad.debug_tag = f'{self.responder_ad.serial}(Responder)'
        self.requester_ad.debug_tag = f'{self.requester_ad.serial}(Requester)'
        utils.concurrent_exec(
            self._setup_device,
            param_list=[[ad] for ad in self.ads],
            raise_on_exception=True,
        )

    def _setup_device(self, ad: android_device.AndroidDevice) -> None:
        ad.load_snippet('wifi', constants.WIFI_SNIPPET_PACKAGE_NAME)
        wifi_test_utils.set_screen_on_and_unlock(ad)
        wifi_test_utils.enable_wifi_verbose_logging(ad)
        # Clear all saved Wi-Fi networks.
        ad.wifi.wifiDisable()
        ad.wifi.wifiClearConfiguredNetworks()
        ad.wifi.wifiEnable()

    @ApiTest([
        'android.net.wifi.p2p.WifiP2pManager#connect(android.net.wifi.p2p.WifiP2pManager.Channel, android.net.wifi.p2p.WifiP2pConfig, android.net.wifi.p2p.WifiP2pManager.ActionListener)',
        'android.net.wifi.p2p.WifiP2pManager#discoverPeers(android.net.wifi.p2p.WifiP2pManager.Channel, android.net.wifi.p2p.WifiP2pManager.ActionListener)',
    ])
    def test_group_owner_negotiation_with_push_button(self) -> None:
        """Test against group owner negotiation and WPS PBC (push button).

        Steps:
            1. Initialize Wi-Fi p2p on both responder and requester device.
            2. Initiate p2p discovery. Verify that the requester finds the
               responder.
            3. Establish a p2p connection with WPS PBC (push button
               configuration). Verify both devices show connection established
               status.
            4. Stop the connection. Verify both devices show connection stopped
               status.
        """
        logging.info('Initializing Wi-Fi p2p.')
        responder = p2p_utils.setup_wifi_p2p(self.responder_ad)
        requester = p2p_utils.setup_wifi_p2p(self.requester_ad)

        requester.ad.log.info('Searching for target device.')
        requester_peer_p2p_device = p2p_utils.discover_p2p_peer(
            requester, responder
        )
        # Make sure that peer is not a group owner (GO) as this is testing
        # against GO negotiation.
        asserts.assert_false(
            requester_peer_p2p_device.is_group_owner,
            f'{requester.ad} found target responder device with invalid role.'
            ' It should not be group owner.',
        )

        requester.ad.log.info('Trying to connect the peer device with WPS PBC.')
        p2p_config = constants.WifiP2pConfig(
            device_address=responder.p2p_device.device_address,
            wps_setup=constants.WpsInfo.PBC,
        )
        p2p_utils.p2p_connect(requester, responder, p2p_config)

        requester.ad.log.info('Disconnecting the peer device.')
        p2p_utils.remove_group_and_verify_disconnected(
            requester, responder, is_group_negotiation=True
        )

    @ApiTest([
        'android.net.wifi.p2p.WifiP2pManager#connect(android.net.wifi.p2p.WifiP2pManager.Channel, android.net.wifi.p2p.WifiP2pConfig, android.net.wifi.p2p.WifiP2pManager.ActionListener)',
        'android.net.wifi.p2p.WifiP2pManager#discoverPeers(android.net.wifi.p2p.WifiP2pManager.Channel, android.net.wifi.p2p.WifiP2pManager.ActionListener)',
    ])
    def test_group_owner_negotiation_with_pin_button(self) -> None:
        """Test against group owner negotiation and WPS PIN.

        Steps:
            1. Initialize Wi-Fi p2p on both responder and requester devices.
            2. Initiate p2p discovery. Verify that the requester finds the
               responder.
            3. Establish a p2p connection using WPS PIN configuration. Verify
               both devices show connection established status.
            4. Stop the connection. Verify both devices show connection stopped
               status.
        """
        logging.info('Initializing Wi-Fi p2p.')
        responder = p2p_utils.setup_wifi_p2p(self.responder_ad)
        requester = p2p_utils.setup_wifi_p2p(self.requester_ad)

        requester.ad.log.info('Searching for target device.')
        requester_peer_p2p_device = p2p_utils.discover_p2p_peer(
            requester, responder
        )
        # Make sure that peer is not a group owner (GO) as this is testing
        # against GO negotiation.
        asserts.assert_false(
            requester_peer_p2p_device.is_group_owner,
            f'{requester.ad} found target responder device with invalid role.'
            ' It should not be group owner.',
        )

        requester.ad.log.info('Trying to connect the peer device with WPS PIN.')
        p2p_config = constants.WifiP2pConfig(
            device_address=responder.p2p_device.device_address,
            wps_setup=constants.WpsInfo.DISPLAY,
        )
        p2p_utils.p2p_connect(requester, responder, p2p_config)

        requester.ad.log.info('Disconnecting the peer device.')
        p2p_utils.remove_group_and_verify_disconnected(
            requester, responder, is_group_negotiation=True
        )

    def _teardown_device(self, ad: android_device.AndroidDevice):
        try:
            p2p_utils.teardown_wifi_p2p(ad)
        finally:
            ad.services.create_output_excerpts_all(self.current_test_info)

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


if __name__ == '__main__':
    test_runner.main()
