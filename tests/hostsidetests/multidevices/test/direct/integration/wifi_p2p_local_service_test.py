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
"""ACTS Wifi P2p Local Service Test reimplemented in Mobly."""

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
import wifi_test_utils


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


class WifiP2pLocalServiceTest(base_test.BaseTestClass):
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
        wifi_test_utils.set_screen_on_and_unlock(ad)
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
            param_list=[[ad] for ad in (self.group_owner_ad, self.client_ad)],
            raise_on_exception=True,
        )

    def on_fail(self, record: records.TestResult) -> None:
        logging.info('Collecting bugreports...')
        android_device.take_bug_reports(
            self.ads, destination=self.current_test_info.output_path
        )

    @ApiTest(
        apis=[
            'android.net.wifi.p2p.WifiP2pManager#connect(android.net.wifi.p2p.WifiP2pManager.Channel, android.net.wifi.p2p.WifiP2pConfig, android.net.wifi.p2p.WifiP2pManager.ActionListener)',
            'android.net.wifi.p2p.WifiP2pManager#discoverPeers(android.net.wifi.p2p.WifiP2pManager.Channel, android.net.wifi.p2p.WifiP2pManager.ActionListener)',
            'android.net.wifi.p2p.nsd.WifiP2pUpnpServiceRequestnew#Instance()',
            'android.net.wifi.p2p.nsd.WifiP2pUpnpServiceRequest#newInstance(String search_target)',
            'android.net.wifi.p2p.WifiP2pManager#addServiceRequest(android.net.wifi.p2p.WifiP2pManager.Channel channel, android.net.wifi.p2p.nsd.WifiP2pServiceRequest req, android.net.wifi.p2p.WifiP2pManager.ActionListener listener)',
        ]
    )
    def test_p2p_upnp_service(self):
        """Verify the p2p discovery functionality.

        Steps:
        1. dut1 add local Upnp service
        2. dut2 register Upnp Service listener
        3. Check dut2 peer list if it only included dut1
        4. Setup p2p upnp local service request with different query string
        5. Check p2p upnp local servier query result is expect or not
        6. Test different query string and check query result
        Note: Step 2 - Step 5 should reference function
              request_service_and_check_result
        """
        logging.info('Add local Upnp Service')
        # Initialize Wi-Fi p2p on both group owner and client.
        group_owner = p2p_utils.setup_wifi_p2p(self.group_owner_ad)
        client = p2p_utils.setup_wifi_p2p(self.client_ad)
        wp2putils.create_p2p_local_service(self.group_owner_ad,
                                           wp2putils.P2P_LOCAL_SERVICE_UPNP)
        wp2putils.request_service_and_check_result(
            group_owner, client, wp2putils.WifiP2PEnums.WifiP2pServiceInfo.
            WIFI_P2P_SERVICE_TYPE_UPNP, None, None)
        time.sleep(_DEFAULT_FUNCTION_SWITCH_TIME)
        wp2putils.request_service_and_check_result(
            group_owner, client, wp2putils.WifiP2PEnums.WifiP2pServiceInfo.
            WIFI_P2P_SERVICE_TYPE_UPNP, 'ssdp:all', None)
        time.sleep(_DEFAULT_FUNCTION_SWITCH_TIME)
        wp2putils.request_service_and_check_result(
            group_owner, client, wp2putils.WifiP2PEnums.WifiP2pServiceInfo.
            WIFI_P2P_SERVICE_TYPE_UPNP, 'upnp:rootdevice', None)

    @ApiTest(
        apis=[
            'android.net.wifi.p2p.WifiP2pManager#connect(android.net.wifi.p2p.WifiP2pManager.Channel, android.net.wifi.p2p.WifiP2pConfig, android.net.wifi.p2p.WifiP2pManager.ActionListener)',
            'android.net.wifi.p2p.WifiP2pManager#discoverPeers(android.net.wifi.p2p.WifiP2pManager.Channel, android.net.wifi.p2p.WifiP2pManager.ActionListener)',
            'android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceRequest#newInstance()',
            'android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceRequest#newInstance(String serviceType)',
            'android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceRequest#newInstance(String instanceName, String serviceType)',
            'android.net.wifi.p2p.WifiP2pManager#addServiceRequest(android.net.wifi.p2p.WifiP2pManager.Channel channel, android.net.wifi.p2p.nsd.WifiP2pServiceRequest req, android.net.wifi.p2p.WifiP2pManager.ActionListener listener)',
        ]
    )
    def test_p2p_bonjour_service(self):
        """Verify the p2p discovery functionality.

        Steps:
        1. dut1 add local bonjour service - IPP and AFP
        2. dut2 register bonjour Service listener - dnssd and dnssd_txrecord
        3. Check dut2 peer list if it only included dut1
        4. Setup p2p bonjour local service request with different query string
        5. Check p2p bonjour local servier query result is expect or not
        6. Test different query string and check query result
        Note: Step 2 - Step 5 should reference function
              request_service_and_check_result_with_retry
        """
        logging.info(
            'Add local bonjour service to %s', self.group_owner_ad.serial
        )
        group_owner = p2p_utils.setup_wifi_p2p(self.group_owner_ad)
        client = p2p_utils.setup_wifi_p2p(self.client_ad)
        wp2putils.create_p2p_local_service(self.group_owner_ad,
                                           wp2putils.P2P_LOCAL_SERVICE_IPP)
        wp2putils.create_p2p_local_service(self.group_owner_ad,
                                           wp2putils.P2P_LOCAL_SERVICE_AFP)

        wp2putils.request_service_and_check_result_with_retry(
            group_owner, client, wp2putils.WifiP2PEnums.WifiP2pServiceInfo.
            WIFI_P2P_SERVICE_TYPE_BONJOUR, None, None)
        time.sleep(_DEFAULT_FUNCTION_SWITCH_TIME)
        wp2putils.request_service_and_check_result_with_retry(
            group_owner, client, wp2putils.WifiP2PEnums.WifiP2pServiceInfo.
            WIFI_P2P_SERVICE_TYPE_BONJOUR, '_ipp._tcp', None)
        time.sleep(_DEFAULT_FUNCTION_SWITCH_TIME)
        wp2putils.request_service_and_check_result_with_retry(
            group_owner, client, wp2putils.WifiP2PEnums.WifiP2pServiceInfo.
            WIFI_P2P_SERVICE_TYPE_BONJOUR, '_ipp._tcp', 'MyPrinter')
        time.sleep(_DEFAULT_FUNCTION_SWITCH_TIME)
        wp2putils.request_service_and_check_result_with_retry(
            group_owner, client, wp2putils.WifiP2PEnums.WifiP2pServiceInfo.
            WIFI_P2P_SERVICE_TYPE_BONJOUR, '_afpovertcp._tcp', 'Example')

if __name__ == '__main__':
  test_runner.main()
