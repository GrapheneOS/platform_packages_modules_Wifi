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
"""Test cases for p2p service discovery and group connection."""

from collections.abc import Sequence
import dataclasses
import datetime
import logging
import time

from android.platform.test.annotations import ApiTest
from mobly import asserts
from mobly import base_test
from mobly import records
from mobly import test_runner
from mobly import utils
from mobly.controllers import android_device

from direct import constants
from direct import p2p_utils
import wifi_test_utils


class GroupOwnerTest(base_test.BaseTestClass):
    """Test cases for p2p service discovery and group connection."""

    ads: Sequence[android_device.AndroidDevice]
    group_owner_ad: android_device.AndroidDevice
    client_ad: android_device.AndroidDevice

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
        wifi_test_utils.enable_wifi_verbose_logging(ad)
        # Clear all saved Wi-Fi networks.
        ad.wifi.wifiDisable()
        ad.wifi.wifiClearConfiguredNetworks()
        ad.wifi.wifiEnable()

    @ApiTest([
        'android.net.wifi.p2p.WifiP2pManager#createGroup(android.net.wifi.p2p.WifiP2pManager.Channel, android.net.wifi.p2p.WifiP2pConfig, android.net.wifi.p2p.WifiP2pManager.ActionListener)',
        'android.net.wifi.p2p.WifiP2pManager#removeGroup(android.net.wifi.p2p.WifiP2pManager.Channel, android.net.wifi.p2p.WifiP2pManager.ActionListener)',
    ])
    def test_connect_with_push_button(self) -> None:
        """Test p2p client connects to the group owner with WPS PBC.

        Steps:
          1. Initialize Wi-Fi p2p on both group owner and client.
          2. Add p2p local services on the group owner.
          3. Create a p2p group on the group owner.
          4. Add UPnP service request and initiate p2p service discovery on
             the client. Verify that the client only discovers UPnP services.
          5. Initiate p2p device discovery on the client. Verify that the client
             discovers the group owner.
          6. The client connects the group owner with WPS PBC (push button
             configuration). Verify both devices show connection established
             status.
          7. Remove the p2p group on the requester. Verify both devices show
             connection stopped status.
        """
        # This is a temporary workaround to avoid p2p service discovery
        # conflicts previous wifi scan or p2p operations. Without sleeping,
        # p2p service discovery process has a high failure rate.
        time.sleep(10)

        # Step 1. Initialize Wi-Fi p2p on both group owner and client.
        logging.info('Initializing Wi-Fi p2p.')
        group_owner = p2p_utils.setup_wifi_p2p(self.group_owner_ad)
        client = p2p_utils.setup_wifi_p2p(self.client_ad)

        # Step 2. Add p2p local services on the group owner.
        self._add_local_services(group_owner)

        # Step 3. Create a p2p group on the group owner.
        group_owner.ad.log.debug('Creating a p2p group.')
        p2p_utils.create_group(group_owner, config=None)

        # Step 4. Add UPnP service request and initiate p2p service discovery on
        # the client.
        client.ad.log.info('Searching for target p2p services.')
        # Only add UPnP service request.
        client.ad.wifi.wifiP2pAddUpnpServiceRequest()
        p2p_utils.set_upnp_response_listener(client)
        p2p_utils.set_dns_sd_response_listeners(client)
        client.ad.wifi.wifiP2pDiscoverServices()

        # Client should only discover Upnp services, but not Bonjour services.
        p2p_utils.check_discovered_services(
            client,
            expected_src_device_address=group_owner.p2p_device.device_address,
            expected_dns_sd_sequence=(),
            expected_dns_txt_sequence=(),
            expected_upnp_sequence=constants.ServiceData.ALL_UPNP_SERVICES,
        )

        # Step 5 - 7. Verify that the client can join the group with WPS PBC.
        self._test_join_group(
            client,
            group_owner,
            constants.WpsInfo.PBC,
        )

    @ApiTest([
        'android.net.wifi.p2p.WifiP2pManager#createGroup(android.net.wifi.p2p.WifiP2pManager.Channel, android.net.wifi.p2p.WifiP2pConfig, android.net.wifi.p2p.WifiP2pManager.ActionListener)',
        'android.net.wifi.p2p.WifiP2pManager#removeGroup(android.net.wifi.p2p.WifiP2pManager.Channel, android.net.wifi.p2p.WifiP2pManager.ActionListener)',
    ])
    def test_connect_with_pin_code(self) -> None:
        """Test p2p client connects to the group owner with WPS DISPLAY.

        Steps:
        1. Initialize Wi-Fi p2p on both group owner and client.
        2. Add p2p local services on the group owner.
        3. Create a p2p group on the group owner.
        4. Perform p2p service discovery on the client. Add Bonjour service
           request to one p2p channel and initiate p2p service discovery on
           another p2p channel. Verify that the client only discovers Bounjour
           p2p services.
        5. Initiate p2p device discovery on the client. Verify that the client
           discovers the group owner.
        6. The client connects the group owner with WPS PIN code. Verify both
           devices show connection established status.
        7. Remove the p2p group on the requester. Verify both devices show
           connection stopped status.
        """
        # This is a temporary workaround to avoid p2p service discovery
        # conflicts previous wifi scan or p2p operations. Without sleeping,
        # p2p service discovery process has a high failure rate.
        time.sleep(10)

        # Step 1. Initialize Wi-Fi p2p on both group owner and client.
        logging.info('Initializing Wi-Fi p2p.')
        group_owner = p2p_utils.setup_wifi_p2p(self.group_owner_ad)
        client = p2p_utils.setup_wifi_p2p(self.client_ad)
        main_channel_id = client.channel_ids[0]

        # Step 2. Add p2p local services on the group owner.
        self._add_local_services(group_owner)

        # Step 3. Create a p2p group on the group owner.
        group_owner.ad.log.debug('Creating a p2p group.')
        p2p_utils.create_group(group_owner, config=None)

        # Step 4. Perform p2p service discovery on the client.
        client.ad.log.info('Searching for target p2p services.')
        # Initialize an extra p2p channel.
        sub_channel_id = p2p_utils.init_extra_channel(client)
        client.ad.wifi.wifiP2pAddBonjourServiceRequest(
            None,  # serviceType
            None,  # instanceName
            sub_channel_id,
        )
        p2p_utils.set_upnp_response_listener(client, sub_channel_id)
        p2p_utils.set_dns_sd_response_listeners(client, sub_channel_id)
        # Discover services on the main channel.
        client.ad.wifi.wifiP2pDiscoverServices(main_channel_id)

        # Client should only discover Bonjour services, but not UPnP services.
        p2p_utils.check_discovered_services(
            client,
            expected_src_device_address=group_owner.p2p_device.device_address,
            expected_dns_sd_sequence=constants.ServiceData.ALL_DNS_SD,
            expected_dns_txt_sequence=constants.ServiceData.ALL_DNS_TXT,
            expected_upnp_sequence=(),
            channel_id=sub_channel_id,
        )

        # Step 5 - 7. Verify that the client can join the group with WPS PIN.
        self._test_join_group(
            client,
            group_owner,
            constants.WpsInfo.DISPLAY,
        )

    def _add_local_services(self, device: p2p_utils.DeviceState) -> None:
        """Adds local services on the given device."""
        device.ad.log.debug('Setting up p2p local services.')
        # Add local services on group owner.
        p2p_utils.add_upnp_local_service(
            device, constants.ServiceData.DEFAULT_UPNP_SERVICE_CONF
        )
        p2p_utils.add_bonjour_local_service(
            device, constants.ServiceData.DEFAULT_IPP_SERVICE_CONF
        )
        p2p_utils.add_bonjour_local_service(
            device, constants.ServiceData.DEFAULT_AFP_SERVICE_CONF
        )

    def _test_join_group(
        self,
        client: p2p_utils.DeviceState,
        group_owner: p2p_utils.DeviceState,
        wps_config: constants.WpsInfo,
    ):
        # Step 5. Initiate p2p device discovery on the client.
        client.ad.log.info('Searching for the target group owner.')
        peer_p2p_device = p2p_utils.discover_group_owner(
            client=client,
            group_owner_address=group_owner.p2p_device.device_address,
        )
        asserts.assert_true(
            peer_p2p_device.is_group_owner,
            f'P2p device {peer_p2p_device} should be group owner.',
        )

        # Step 6. The client connects the group owner.
        p2p_config = constants.WifiP2pConfig(
            device_address=group_owner.p2p_device.device_address,
            wps_setup=wps_config,
        )
        client.ad.log.info('Trying to connect the group owner with p2p config: %s', p2p_config)
        p2p_utils.p2p_connect(client, group_owner, p2p_config)

        # Step 7. Remove the p2p group on the requester.
        client.ad.log.info('Disconnecting with the group owner.')
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
