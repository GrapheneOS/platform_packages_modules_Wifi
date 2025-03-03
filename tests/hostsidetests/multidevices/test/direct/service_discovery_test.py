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
"""Test cases for Wi-Fi p2p service discovery."""
from collections.abc import Sequence
import logging

from android.platform.test.annotations import ApiTest
from direct import constants
from direct import p2p_utils
from mobly import asserts
from mobly import base_test
from mobly import records
from mobly import test_runner
from mobly import utils
from mobly.controllers import android_device

import wifi_test_utils


@ApiTest(
    [
        "android.net.wifi.p2p.WifiP2pManager#discoverServices(android.net.wifi.p2p.WifiP2pManager.Channel, android.net.wifi.p2p.WifiP2pManager.ActionListener)",
        "android.net.wifi.p2p.WifiP2pManager#addServiceRequest(android.net.wifi.p2p.WifiP2pManager.Channel, android.net.wifi.p2p.WifiP2pManager.WifiP2pServiceRequest, android.net.wifi.p2p.WifiP2pManager.ActionListener)",
        "android.net.wifi.p2p.WifiP2pManager#setUpnpServiceResponseListener(android.net.wifi.p2p.WifiP2pManager.Channel, android.net.wifi.p2p.WifiP2pManager.UpnpServiceResponseListener)",
        "android.net.wifi.p2p.WifiP2pManager#setDnsSdResponseListeners(android.net.wifi.p2p.WifiP2pManager.Channel, android.net.wifi.p2p.WifiP2pManager.DnsSdServiceResponseListener, android.net.wifi.p2p.WifiP2pManager.DnsSdTxtRecordListener)",
        "android.net.wifi.p2p.WifiP2pManager#removeServiceRequest(android.net.wifi.p2p.WifiP2pManager.Channel, android.net.wifi.p2p.WifiP2pManager.WifiP2pServiceRequest, android.net.wifi.p2p.WifiP2pManager.ActionListener)",
        "android.net.wifi.p2p.WifiP2pManager#clearServiceRequests(android.net.wifi.p2p.WifiP2pManager.Channel, android.net.wifi.p2p.WifiP2pManager.ActionListener)"
    ]
)
class ServiceDiscoveryTest(base_test.BaseTestClass):
    """Test cases for Wi-Fi p2p service discovery.

    Test Preconditions:
        Two Android phones that support Wi-Fi Direct.

    Test steps are described in the docstring of each test case.
    """

    ads: Sequence[android_device.AndroidDevice]
    responder_ad: android_device.AndroidDevice
    requester_ad: android_device.AndroidDevice

    def setup_class(self) -> None:
        super().setup_class()
        self.ads = self.register_controller(android_device, min_number=2)
        utils.concurrent_exec(
            self._setup_device,
            param_list=[[ad] for ad in self.ads],
            raise_on_exception=True,
        )
        self.responder_ad, self.requester_ad, *_ = self.ads
        self.responder_ad.debug_tag = (
            f'{self.responder_ad.serial}(Responder)'
        )
        self.requester_ad.debug_tag = f'{self.requester_ad.serial}(Requester)'

    def _setup_device(self, ad: android_device.AndroidDevice) -> None:
        ad.load_snippet('wifi', constants.WIFI_SNIPPET_PACKAGE_NAME)
        wifi_test_utils.set_screen_on_and_unlock(ad)
        wifi_test_utils.enable_wifi_verbose_logging(ad)
        # Clear all saved Wi-Fi networks.
        ad.wifi.wifiDisable()
        ad.wifi.wifiClearConfiguredNetworks()
        ad.wifi.wifiEnable()

    def test_search_all_services_01(self) -> None:
        """Searches all p2p services with API
        `WifiP2pServiceRequest.newInstance(WifiP2pServiceInfo.SERVICE_TYPE_ALL)`.

        Test Steps:
            1. Initialize Wi-Fi p2p on both devices.
            2. Add local services UPnP and Bonjour and initiate peer discovery on one device
               (responder).
            3. Add service request on another device (requester) with API
               `WifiP2pServiceRequest.newInstance(WifiP2pServiceInfo.SERVICE_TYPE_ALL)`.
            4. Initiate p2p service discovery on the requester. Verify that the requester
               discovers all services.
        """
        requester, responder = self._setup_wifi_p2p()
        self._add_p2p_services(responder)
        requester.ad.wifi.wifiP2pAddServiceRequest(
            constants.ServiceType.ALL
        )
        self._search_p2p_services(
            responder,
            requester,
            expected_dns_sd_sequence=constants.ServiceData.ALL_DNS_SD,
            expected_dns_txt_sequence=constants.ServiceData.ALL_DNS_TXT,
            expected_upnp_sequence=constants.ServiceData.ALL_UPNP_SERVICES,
        )

    def test_search_all_services_02(self) -> None:
        """Searches all p2p services with API
        `WifiP2pServiceRequest.newInstance(SERVICE_TYPE_BONJOUR/SERVICE_TYPE_UPNP)`

        Test Steps:
            1. Initialize Wi-Fi p2p on both devices.
            2. Add local services UPnP and Bonjour and initiate peer discovery on one device
               (responder).
            3. Add service request on another device (requester) with API
               `WifiP2pServiceRequest.newInstance(SERVICE_TYPE_BONJOUR)` and
               `WifiP2pServiceRequest.newInstance(SERVICE_TYPE_UPNP)`.
            4. Initiate p2p service discovery on the requester. Verify that the requester discovers
               all services.
        """
        requester, responder = self._setup_wifi_p2p()
        self._add_p2p_services(responder)
        requester.ad.wifi.wifiP2pAddServiceRequest(
            constants.ServiceType.BONJOUR,
        )
        requester.ad.wifi.wifiP2pAddServiceRequest(
            constants.ServiceType.UPNP,
        )
        self._search_p2p_services(
            responder,
            requester,
            expected_dns_sd_sequence=constants.ServiceData.ALL_DNS_SD,
            expected_dns_txt_sequence=constants.ServiceData.ALL_DNS_TXT,
            expected_upnp_sequence=constants.ServiceData.ALL_UPNP_SERVICES,
        )

    def test_search_all_services_03(self) -> None:
        """Searches all p2p services with API `WifiP2pDnsSdServiceRequest.newInstance()`
        and `WifiP2pUpnpServiceRequest.newInstance()`.

        Test Steps:
            1. Initialize Wi-Fi p2p on both devices.
            2. Add local services UPnP and Bonjour and initiate peer discovery on one device
               (responder).
            3. Add service request on another device (requester) with API
               `WifiP2pUpnpServiceRequest.newInstance()` and
               `WifiP2pDnsSdServiceRequest.newInstance()`.
            4. Initiate p2p service discovery on the requester. Verify that the requester discovers
               all services.
        """
        requester, responder = self._setup_wifi_p2p()
        self._add_p2p_services(responder)
        requester.ad.wifi.wifiP2pAddBonjourServiceRequest()
        requester.ad.wifi.wifiP2pAddUpnpServiceRequest()
        self._search_p2p_services(
            responder,
            requester,
            expected_dns_sd_sequence=constants.ServiceData.ALL_DNS_SD,
            expected_dns_txt_sequence=constants.ServiceData.ALL_DNS_TXT,
            expected_upnp_sequence=constants.ServiceData.ALL_UPNP_SERVICES,
        )

    def test_serv_req_dns_ptr(self) -> None:
        """Searches Bonjour services with Bonjour domain.

        Test Steps:
            1. Initialize Wi-Fi p2p on both devices.
            2. Add local services UPnP and Bonjour and initiate peer discovery on one device
               (responder).
            3. Add Bonjour service request with service type `_ipp._tcp`.
            4. Initiate p2p service discovery on the requester. Verify that the requester discovers
               expected services.
        """
        requester, responder = self._setup_wifi_p2p()
        self._add_p2p_services(responder)
        requester.ad.wifi.wifiP2pAddBonjourServiceRequest(
            None,  # instanceName
            '_ipp._tcp',
        )
        self._search_p2p_services(
            responder,
            requester,
            expected_dns_sd_sequence=constants.ServiceData.IPP_DNS_SD,
            expected_dns_txt_sequence=(),
            expected_upnp_sequence=(),
        )

    def test_serv_req_dns_txt(self) -> None:
        """Searches Bonjour services with TXT record.

        Test Steps:
            1. Initialize Wi-Fi p2p on both devices.
            2. Add local services UPnP and Bonjour and initiate peer discovery on one device
               (responder).
            3. Add Bonjour service request with instance name `MyPrinter` and
               service type `_ipp._tcp`.
            4. Initiate p2p service discovery on the requester. Verify that the requester discovers
               expected services.
        """
        requester, responder = self._setup_wifi_p2p()
        self._add_p2p_services(responder)
        requester.ad.wifi.wifiP2pAddBonjourServiceRequest(
            'MyPrinter',
            '_ipp._tcp',
        )
        self._search_p2p_services(
            responder,
            requester,
            expected_dns_sd_sequence=(),
            expected_dns_txt_sequence=constants.ServiceData.IPP_DNS_TXT,
            expected_upnp_sequence=(),
        )

    def test_serv_req_upnp_all(self) -> None:
        """Searches all UPnP services with service type `ssdp:all`.

        Test Steps:
            1. Initialize Wi-Fi p2p on both devices.
            2. Add local services UPnP and Bonjour and initiate peer discovery on one device
               (responder).
            3. Add UPnP service request with service type `ssdp:all`.
            4. Initiate p2p service discovery on the requester. Verify that the requester discovers
               expected services.
        """
        requester, responder = self._setup_wifi_p2p()
        self._add_p2p_services(responder)
        requester.ad.wifi.wifiP2pAddUpnpServiceRequest('ssdp:all')
        self._search_p2p_services(
            responder,
            requester,
            expected_dns_sd_sequence=(),
            expected_dns_txt_sequence=(),
            expected_upnp_sequence=constants.ServiceData.ALL_UPNP_SERVICES,
        )

    def test_serv_req_upnp_root_device(self) -> None:
        """Searches UPnP root devices.

        Test Steps:
            1. Initialize Wi-Fi p2p on both devices.
            2. Add local services UPnP and Bonjour and initiate peer discovery on one device
               (responder).
            3. Add UPnP service request with service type `upnp:rootdevice`.
            4. Initiate p2p service discovery on the requester. Verify that the requester discovers
               expected services.
        """
        requester, responder = self._setup_wifi_p2p()
        self._add_p2p_services(responder)
        requester.ad.wifi.wifiP2pAddUpnpServiceRequest('upnp:rootdevice')
        self._search_p2p_services(
            responder,
            requester,
            expected_dns_sd_sequence=(),
            expected_dns_txt_sequence=(),
            expected_upnp_sequence=constants.ServiceData.UPNP_ROOT_DEVICE,
        )

    def test_serv_req_remove_request(self) -> None:
        """Checks that API `WifiP2pManager#removeServiceRequest` works well.

        Test Steps:
            1. Initialize Wi-Fi p2p on both devices.
            2. Add local services UPnP and Bonjour and initiate peer discovery on one device
               (responder).
            3. Add 2 UPnP service requests and 2 Bonjour service requests on the
               requester.
            4. Removes 3 of the 4 added requests.
            5. Initiate p2p service discovery on the requester. Verify that the requester
               only discovers services corresponds to the remaining request.
        """
        requester, responder = self._setup_wifi_p2p()
        self._add_p2p_services(responder)

        # Add requests
        requester.ad.log.info('Adding service requests.')
        upnp_req_1_id = requester.ad.wifi.wifiP2pAddUpnpServiceRequest()
        requester.ad.wifi.wifiP2pAddUpnpServiceRequest('ssdp:all')
        bonjour_req_1_id = requester.ad.wifi.wifiP2pAddBonjourServiceRequest()
        bonjour_req_2_id = requester.ad.wifi.wifiP2pAddBonjourServiceRequest(
            None,  # instanceName
            '_ipp._tcp',
        )

        # Remove 3 of the 4 added requests except for ssdp:all
        requester.ad.log.info('Removing service requests.')
        requester.ad.wifi.wifiP2pRemoveServiceRequest(upnp_req_1_id)
        requester.ad.wifi.wifiP2pRemoveServiceRequest(bonjour_req_1_id)
        requester.ad.wifi.wifiP2pRemoveServiceRequest(bonjour_req_2_id)

        # Initialize test listener.
        p2p_utils.set_upnp_response_listener(requester)
        p2p_utils.set_dns_sd_response_listeners(requester)
        # Search service
        requester.ad.wifi.wifiP2pDiscoverServices()

        # Initiates service discovery and check expected services.
        p2p_utils.check_discovered_services(
            requester,
            responder.p2p_device.device_address,
            expected_dns_sd_sequence=(),
            expected_dns_txt_sequence=(),
            expected_upnp_sequence=constants.ServiceData.ALL_UPNP_SERVICES
        )

    def test_serv_req_clear_request(self) -> None:
        """Checks that API `WifiP2pManager#clearServiceRequests` works well.

        Test Steps:
            1. Initialize Wi-Fi p2p on both devices.
            2. Add local services UPnP and Bonjour and initiate peer discovery on one device
               (responder).
            3. Add 2 UPnP service requests and 2 Bonjour service requests on the
               requester.
            4. Clears all added requests.
            5. Initiate p2p service discovery on the requester. Verify that the service
               discovery should fail due to no service request.
        """
        requester, responder = self._setup_wifi_p2p()
        self._add_p2p_services(responder)

        # Add requests
        requester.ad.log.info('Adding service requests.')
        requester.ad.wifi.wifiP2pAddUpnpServiceRequest()
        requester.ad.wifi.wifiP2pAddUpnpServiceRequest('ssdp:all')
        requester.ad.wifi.wifiP2pAddBonjourServiceRequest()
        requester.ad.wifi.wifiP2pAddBonjourServiceRequest(
            None,  # instanceName
            '_ipp._tcp',
        )

        # Clear requests
        requester.ad.log.info('Clearing all service requests.')
        requester.ad.wifi.wifiP2pClearServiceRequests()

        # Search services, but NO_SERVICE_REQUESTS is returned.
        requester.ad.log.info('Initiating service discovery.')
        expect_error_code = constants.WifiP2pManagerConstants.NO_SERVICE_REQUESTS
        with asserts.assert_raises_regex(
            Exception,
            f'reason_code={str(expect_error_code)}',
            extras='Service discovery should fail due to no service request.',
        ):
            requester.ad.wifi.wifiP2pDiscoverServices()

    def test_serv_req_multi_channel_01(self) -> None:
        """Searches all UPnP services on channel1 and all Bonjour services on
        channel2.

        Test Steps:
            1. Initialize Wi-Fi p2p on both devices. This initializes p2p channel
               channel1.
            2. Add local services UPnP and Bonjour and initiate peer discovery on one device
               (responder).
            3. Initialize an extra p2p channel channel2 on another device (requester).
            4. Add a UPnP service request to channel1.
            5. Add a Bonjour request to channel2.
            6. Initiate p2p service discovery on channel1. Verify that the requester
               discovers UPnP services on channel1 and Bonjour services on channel2.
        """
        requester, responder = self._setup_wifi_p2p()
        self._add_p2p_services(responder)
        channel1 = requester.channel_ids[0]
        channel2 = p2p_utils.init_extra_channel(requester)

        requester.ad.log.info('Adding service requests.')
        # Add UPnP request to the channel 1.
        requester.ad.wifi.wifiP2pAddUpnpServiceRequest(
            None,  # serviceType
            channel1,
        )
        # Add UPnP request to channel 2.
        requester.ad.wifi.wifiP2pAddBonjourServiceRequest(
            None,  # instanceName
            None,  # serviceType
            channel2,
        )

        # Set service listener.
        requester.ad.log.info('Setting service listeners.')
        p2p_utils.set_upnp_response_listener(requester, channel1)
        p2p_utils.set_dns_sd_response_listeners(requester, channel1)
        p2p_utils.set_upnp_response_listener(requester, channel2)
        p2p_utils.set_dns_sd_response_listeners(requester, channel2)

        # Discover services
        requester.ad.log.info('Initiating service discovery.')
        requester.ad.wifi.wifiP2pDiscoverServices(channel1)
        responder_address = responder.p2p_device.device_address

        # Check discovered services
        # Channel1 receive only UPnP service.
        requester.ad.log.info('Checking services on channel %d.', channel1)
        p2p_utils.check_discovered_services(
            requester,
            responder.p2p_device.device_address,
            expected_dns_sd_sequence=(),
            expected_dns_txt_sequence=(),
            expected_upnp_sequence=constants.ServiceData.ALL_UPNP_SERVICES,
            channel_id=channel1,
        )
        # Channel2 receive only Bonjour service.
        requester.ad.log.info('Checking services on channel %d.', channel2)
        p2p_utils.check_discovered_services(
            requester,
            responder.p2p_device.device_address,
            expected_dns_sd_sequence=constants.ServiceData.ALL_DNS_SD,
            expected_dns_txt_sequence=constants.ServiceData.ALL_DNS_TXT,
            expected_upnp_sequence=(),
            channel_id=channel2,
        )

        # Clean up.
        p2p_utils.reset_p2p_service_state(requester.ad, channel1)
        p2p_utils.reset_p2p_service_state(requester.ad, channel2)

    def test_serv_req_multi_channel_02(self) -> None:
        """Searches Bonjour IPP PTR service on channel1 and AFP TXT service on channel2.

        Test Steps:
            1. Initialize Wi-Fi p2p on both devices. This initializes p2p channel
               channel1.
            2. Add local services UPnP and Bonjour and initiate peer discovery on one device
               (responder).
            3. Initialize an extra p2p channel channel2 on another device (requester).
            4. Add a Bonjour IPP PTR request to channel1.
            5. Add a Bonjour AFP TXT request to channel2.
            6. Initiate p2p service discovery on channel1. Verify that the requester
               discovers IPP PTR services on channel1 and AFP TXT services on channel2.
        """
        requester, responder = self._setup_wifi_p2p()
        self._add_p2p_services(responder)
        channel1 = requester.channel_ids[0]
        channel2 = p2p_utils.init_extra_channel(requester)

        # Add Bonjour IPP PRT request to channel1.
        requester.ad.log.info('Adding service requests.')
        requester.ad.wifi.wifiP2pAddBonjourServiceRequest(
            None,  # instanceName
            '_ipp._tcp',
            channel1,
        )

        # Add Bonjour AFP TXT request to channel2.
        requester.ad.wifi.wifiP2pAddBonjourServiceRequest(
            'Example',
            '_afpovertcp._tcp',
            channel2,
        )

        # Initialize listener test objects.
        requester.ad.log.info('Setting service listeners.')
        p2p_utils.set_upnp_response_listener(requester, channel1)
        p2p_utils.set_dns_sd_response_listeners(requester, channel1)
        p2p_utils.set_upnp_response_listener(requester, channel2)
        p2p_utils.set_dns_sd_response_listeners(requester, channel2)

        # Discover services
        requester.ad.log.info('Initiating service discovery.')
        requester.ad.wifi.wifiP2pDiscoverServices(channel1)
        responder_address = responder.p2p_device.device_address

        # Check discovered services
        # Channel1 receive only Bonjour IPP PTR.
        requester.ad.log.info('Checking services on channel %d.', channel1)
        p2p_utils.check_discovered_services(
            requester,
            responder.p2p_device.device_address,
            expected_dns_sd_sequence=constants.ServiceData.IPP_DNS_SD,
            expected_dns_txt_sequence=(),
            expected_upnp_sequence=(),
            channel_id=channel1,
        )
        # Channel2 receive only Bonjour AFP TXT.
        requester.ad.log.info('Checking services on channel %d.', channel2)
        p2p_utils.check_discovered_services(
            requester,
            responder.p2p_device.device_address,
            expected_dns_sd_sequence=(),
            expected_dns_txt_sequence=constants.ServiceData.AFP_DNS_TXT,
            expected_upnp_sequence=(),
            channel_id=channel2,
        )

        # Clean up.
        p2p_utils.reset_p2p_service_state(requester.ad, channel1)
        p2p_utils.reset_p2p_service_state(requester.ad, channel2)

    def test_serv_req_multi_channel_03(self) -> None:
        """Checks that `removeServiceRequest` and `clearServiceRequests` have no
        effect against another channel.

        Test Steps:
            1. Initialize Wi-Fi p2p on both devices. This initializes p2p channel
               channel1.
            2. Add local services UPnP and Bonjour and initiate peer discovery on one device
               (responder).
            3. Initialize an extra p2p channel channel2 on another device (requester).
            4. Add a Bonjour request to channel1.
            5. Try to remove the request of channel1 on channel2. This should
               not have effect.
            6. Try to clear service requests on channel2. This should not have
               effect.
            4. Add a Bonjour request to channel2.
            5. Initiate p2p service discovery on channel1. Verify that the requester
               discovers Bonjour services but not UPnP services on channel1.
        """
        requester, responder = self._setup_wifi_p2p()
        self._add_p2p_services(responder)
        channel1 = requester.channel_ids[0]
        channel2 = p2p_utils.init_extra_channel(requester)

        # Add Bonjour request to channel1.
        requester.ad.log.info('Adding service request to channel %d', channel1)
        bonjour_req_id = requester.ad.wifi.wifiP2pAddBonjourServiceRequest(
            None,  # instanceName
            None,  # serviceType
            channel1,
        )
        requester.ad.log.info('Added request %d', bonjour_req_id)

        # Try to remove the Bonjour request of channel1 on channel2.
        # However, it should silently succeed but have no effect
        requester.ad.log.info('Removing the request %d from channel %d', bonjour_req_id, channel1)
        requester.ad.wifi.wifiP2pRemoveServiceRequest(bonjour_req_id, channel2)

        # Clear the all requests on channel2.
        # However, it should silently succeed but have no effect
        requester.ad.log.info('Clearing service requests on channel %d', channel2)
        requester.ad.wifi.wifiP2pClearServiceRequests(channel2)

        # Initialize service listeners.
        requester.ad.log.info('Setting service listeners on both channels.')
        p2p_utils.set_upnp_response_listener(requester, channel1)
        p2p_utils.set_dns_sd_response_listeners(requester, channel1)
        p2p_utils.set_upnp_response_listener(requester, channel2)
        p2p_utils.set_dns_sd_response_listeners(requester, channel2)

        # Discover services
        requester.ad.log.info('Initiating service discovery.')
        requester.ad.wifi.wifiP2pDiscoverServices(channel1)
        responder_address = responder.p2p_device.device_address

        # Check that Bonjour response can be received on channel1
        requester.ad.log.info('Checking Bonjour services on channel %d.', channel1)
        p2p_utils.check_discovered_services(
            requester,
            responder.p2p_device.device_address,
            expected_dns_sd_sequence=constants.ServiceData.ALL_DNS_SD,
            expected_dns_txt_sequence=constants.ServiceData.AFP_DNS_TXT,
            expected_upnp_sequence=(),
            channel_id=channel1,
        )

        # Clean up.
        p2p_utils.reset_p2p_service_state(requester.ad, channel1)
        p2p_utils.reset_p2p_service_state(requester.ad, channel2)

    def _search_p2p_services(
        self,
        responder: p2p_utils.DeviceState,
        requester: p2p_utils.DeviceState,
        expected_dns_sd_sequence: Sequence[Sequence[str, dict[str, str]]],
        expected_dns_txt_sequence: Sequence[Sequence[str, str]],
        expected_upnp_sequence: Sequence[str],
    ) -> None:
        """Initiate service discovery and assert expected p2p services are discovered.

        Args:
            responder: The responder device state.
            requester: The requester device state.
            expected_dns_sd_sequence: Expected DNS-SD responses.
            expected_dns_txt_sequence: Expected DNS TXT records.
            expected_upnp_sequence: Expected UPNP services.
        """
        requester.ad.log.info('Initiating service discovery.')
        p2p_utils.set_upnp_response_listener(requester)
        p2p_utils.set_dns_sd_response_listeners(requester)
        requester.ad.wifi.wifiP2pDiscoverServices()

        requester.ad.log.info('Checking discovered services.')
        p2p_utils.check_discovered_services(
            requester,
            responder.p2p_device.device_address,
            expected_dns_sd_sequence=expected_dns_sd_sequence,
            expected_dns_txt_sequence=expected_dns_txt_sequence,
            expected_upnp_sequence=expected_upnp_sequence,
        )

    def _add_p2p_services(self, responder: p2p_utils.DeviceState):
        """Sets up P2P services on the responder device.

        This method adds local UPNP and Bonjour services to the responder device and
        initiates peer discovery.
        """
        responder.ad.log.info('Setting up p2p local services UpnP and Bonjour.')
        p2p_utils.add_upnp_local_service(
            responder, constants.ServiceData.DEFAULT_UPNP_SERVICE_CONF
        )
        p2p_utils.add_bonjour_local_service(
            responder, constants.ServiceData.DEFAULT_IPP_SERVICE_CONF
        )
        p2p_utils.add_bonjour_local_service(
            responder, constants.ServiceData.DEFAULT_AFP_SERVICE_CONF
        )
        responder.ad.wifi.wifiP2pDiscoverPeers()

    def _setup_wifi_p2p(self):
        logging.info('Initializing Wi-Fi p2p.')
        responder = p2p_utils.setup_wifi_p2p(self.responder_ad)
        requester = p2p_utils.setup_wifi_p2p(self.requester_ad)
        return requester, responder

    def _teardown_wifi_p2p(self, ad: android_device.AndroidDevice):
        try:
            p2p_utils.teardown_wifi_p2p(ad)
        finally:
            ad.services.create_output_excerpts_all(self.current_test_info)

    def teardown_test(self) -> None:
        utils.concurrent_exec(
            self._teardown_wifi_p2p,
            param_list=[[ad] for ad in self.ads],
            raise_on_exception=False,
        )

    def on_fail(self, record: records.TestResult) -> None:
        logging.info('Collecting bugreports...')
        android_device.take_bug_reports(
            self.ads, destination=self.current_test_info.output_path
        )


if __name__ == '__main__':
    test_runner.main()
