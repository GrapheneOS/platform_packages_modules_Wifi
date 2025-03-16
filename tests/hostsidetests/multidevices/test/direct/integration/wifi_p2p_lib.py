"""WiFi P2P library for multi-devices tests."""
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

from collections.abc import Sequence
import datetime
import time

from direct import constants
from direct import p2p_utils
from mobly import asserts
from mobly.controllers import android_device
from mobly.controllers.android_device_lib import adb
from mobly.controllers.android_device_lib import callback_handler_v2
from mobly.snippet import errors

_DEFAULT_TIMEOUT = datetime.timedelta(seconds=30)
_DEFAULT_SLEEPTIME = 5
_DEFAULT_FUNCTION_SWITCH_TIME = 10
_DEFAULT_SERVICE_WAITING_TIME = 20
_NORMAL_TIMEOUT = datetime.timedelta(seconds=20)

P2P_CONNECT_NEGOTIATION = 0
P2P_CONNECT_JOIN = 1
P2P_CONNECT_INVITATION = 2

######################################################
# Wifi P2p local service type
####################################################
P2P_LOCAL_SERVICE_UPNP = 0
P2P_LOCAL_SERVICE_IPP = 1
P2P_LOCAL_SERVICE_AFP = 2

######################################################
# Wifi P2p local service event
####################################################

DNSSD_EVENT = 'WifiP2pOnDnsSdServiceAvailable'
DNSSD_TXRECORD_EVENT = 'WifiP2pOnDnsSdTxtRecordAvailable'
UPNP_EVENT = 'WifiP2pOnUpnpServiceAvailable'

DNSSD_EVENT_INSTANCENAME_KEY = 'InstanceName'
DNSSD_EVENT_REGISTRATIONTYPE_KEY = 'RegistrationType'
DNSSD_TXRECORD_EVENT_FULLDOMAINNAME_KEY = 'FullDomainName'
DNSSD_TXRECORD_EVENT_TXRECORDMAP_KEY = 'TxtRecordMap'
UPNP_EVENT_SERVICELIST_KEY = 'ServiceList'


######################################################
# Wifi P2p UPnP MediaRenderer local service
######################################################
class UpnpTestData():
    av_transport = 'urn:schemas-upnp-org:service:AVTransport:1'
    connection_manager = 'urn:schemas-upnp-org:service:ConnectionManager:1'
    service_type = 'urn:schemas-upnp-org:device:MediaRenderer:1'
    uuid = '6859dede-8574-59ab-9332-123456789011'
    rootdevice = 'upnp:rootdevice'


######################################################
# Wifi P2p Bonjour IPP & AFP local service
######################################################
class IppTestData():
    ipp_instance_name = 'MyPrinter'
    ipp_registration_type = '_ipp._tcp'
    ipp_domain_name = 'myprinter._ipp._tcp.local.'
    ipp_txt_record = {'txtvers': '1', 'pdl': 'application/postscript'}


class AfpTestData():
    afp_instance_name = 'Example'
    afp_registration_type = '_afpovertcp._tcp'
    afp_domain_name = 'example._afpovertcp._tcp.local.'
    afp_txt_record = {}


# Trigger p2p connect to device_go from device_gc.
def p2p_connect(
    device_gc: p2p_utils.DeviceState,
    device_go: p2p_utils.DeviceState,
    is_reconnect,
    wps_setup,
    p2p_connect_type=P2P_CONNECT_NEGOTIATION,
    go_ad=None,
):
  """Trigger p2p connect to ad2 from ad1.

  Args:
      device_gc: The android device (Client)
      device_go: The android device (GO)
      is_reconnect: boolean, if persist group is exist, is_reconnect is true,
        otherswise is false.
      wps_setup: which wps connection would like to use
      p2p_connect_type: enumeration, which type this p2p connection is
      go_ad: The group owner android device which is used for the invitation
        connection
  """
  device_gc.ad.log.info(
      'Create p2p connection from %s to %s via wps: %s type %d',
      device_gc.ad.serial,
      device_go.ad.serial,
      wps_setup,
      p2p_connect_type,
  )

  if p2p_connect_type == P2P_CONNECT_INVITATION:
    if go_ad is None:
      go_ad = device_gc
    p2p_utils.discover_p2p_peer(device_gc, device_go)
    # GO might be another peer, so ad2 needs to find it first.
    p2p_utils.discover_group_owner(
        client=device_go, group_owner_address=go_ad.p2p_device.device_address
    )
  elif p2p_connect_type == P2P_CONNECT_JOIN:
    peer_p2p_device = p2p_utils.discover_group_owner(
        client=device_gc,
        group_owner_address=device_go.p2p_device.device_address,
    )
    asserts.assert_true(
        peer_p2p_device.is_group_owner,
        f'P2p device {peer_p2p_device} should be group owner.',
    )
  else:
    p2p_utils.discover_p2p_peer(device_gc, device_go)
  time.sleep(_DEFAULT_SLEEPTIME)
  device_gc.ad.log.info(
      'from device1: %s -> device2: %s',
      device_gc.p2p_device.device_address,
      device_go.p2p_device.device_address,
  )
  p2p_config = constants.WifiP2pConfig(
      device_address=device_go.p2p_device.device_address,
      wps_setup=wps_setup,
  )
  if not is_reconnect:
    p2p_utils.p2p_connect(device_gc, device_go, p2p_config)
  else:
    p2p_utils.p2p_reconnect(device_gc, device_go, p2p_config)


def is_go(ad):
  """Check an Android p2p role is Go or not.

  Args:
      ad: The android device

  Returns:
      True: An Android device is p2p go
      False: An Android device is p2p gc
  """
  callback_handler = ad.wifi.wifiP2pRequestConnectionInfo()
  event = callback_handler.waitAndGet(
      event_name=constants.ON_CONNECTION_INFO_AVAILABLE,
      timeout=_DEFAULT_TIMEOUT.total_seconds(),
  )
  if event.data['isGroupOwner']:
    return True
  return False


def p2p_go_ip(ad):
  """Get Group Owner IP address.

  Args:
      ad: The android device

  Returns:
      GO IP address
  """
  event_handler = ad.wifi.wifiP2pRequestConnectionInfo()
  result = event_handler.waitAndGet(
      event_name=constants.ON_CONNECTION_INFO_AVAILABLE,
      timeout=_DEFAULT_TIMEOUT.total_seconds(),
  )
  go_flag = result.data['isGroupOwner']
  ip = result.data['groupOwnerHostAddress'].replace('/', '')
  ad.log.info('is_go:%s, p2p ip: %s', go_flag, ip)
  return ip


def p2p_disconnect(ad):
  """Invoke an Android device removeGroup to trigger p2p disconnect.

  Args:
      ad: The android device
  """
  ad.log.debug('P2p Disconnect')
  try:
    ad.wifi.wifiP2pStopPeerDiscovery()
    ad.wifi.wifiP2pCancelConnect()
    ad.wifi.wifiP2pRemoveGroup()
  finally:
    # Make sure to call `p2pClose`, otherwise `_setup_wifi_p2p` won't be
    # able to run again.
    ad.wifi.p2pClose()


def p2p_connection_ping_test(dut: android_device.AndroidDevice, peer_ip: str):
  """Run a ping over the specified device/link.

  Args:
      dut: Device on which to execute ping6.
      peer_ip: Scoped IPv4 address of the peer to ping.
  """
  cmd = 'ping -c 3 -W 1 %s' % peer_ip
  try:
    dut.log.info(cmd)
    results = dut.adb.shell(cmd)
  except adb.AdbError:
    time.sleep(1)
    dut.log.info('CMD RETRY: %s', cmd)
    results = dut.adb.shell(cmd)

  dut.log.info(results)


def gen_test_data(service_category):
  """Based on service category to generator Test Data.

  Args:
      service_category: P2p local service type, Upnp or Bonjour

  Returns:
      TestData
  """
  test_data = []
  if service_category == P2P_LOCAL_SERVICE_UPNP:
    test_data.append(UpnpTestData.uuid)
    test_data.append(UpnpTestData.service_type)
    test_data.append(
        [UpnpTestData.av_transport, UpnpTestData.connection_manager]
    )
  elif service_category == P2P_LOCAL_SERVICE_IPP:
    test_data.append(IppTestData.ipp_instance_name)
    test_data.append(IppTestData.ipp_registration_type)
    test_data.append(IppTestData.ipp_txt_record)
  elif service_category == P2P_LOCAL_SERVICE_AFP:
    test_data.append(AfpTestData.afp_instance_name)
    test_data.append(AfpTestData.afp_registration_type)
    test_data.append(AfpTestData.afp_txt_record)

  return test_data


def create_p2p_local_service(ad, service_category):
  """Based on service_category to create p2p local service on an Android device ad.

  Args:
      ad: The android device
      service_category: p2p local service type, UPNP / IPP / AFP,
  """
  test_data = gen_test_data(service_category)
  ad.log.info(
      'LocalService = %s, %s, %s', test_data[0], test_data[1], test_data[2]
  )
  if service_category == P2P_LOCAL_SERVICE_UPNP:
    ad.wifi.wifiP2pAddUpnpLocalService(test_data[0], test_data[1], test_data[2])
  elif (
      service_category == P2P_LOCAL_SERVICE_IPP
      or service_category == P2P_LOCAL_SERVICE_AFP
  ):
    ad.wifi.wifiP2pAddBonjourLocalService(
        test_data[0], test_data[1], test_data[2]
    )


def gen_expect_test_data(service_type, query_string1, query_string2):
  """Based on serviceCategory to generator expect serviceList.

  Args:
      service_type: P2p local service type, Upnp or Bonjour
      query_string1: Query String, NonNull
      query_string2: Query String, used for Bonjour, Nullable

  Returns:
      expect_service_list: expect data generated.
  """
  expect_service_list = {}
  if (
      service_type
      == WifiP2PEnums.WifiP2pServiceInfo.WIFI_P2P_SERVICE_TYPE_BONJOUR
  ):
    ipp_service = WifiP2PEnums.WifiP2pDnsSdServiceResponse()
    afp_service = WifiP2PEnums.WifiP2pDnsSdServiceResponse()
    if query_string1 == IppTestData.ipp_registration_type:
      if query_string2 == IppTestData.ipp_instance_name:
        ipp_service.instance_name = ''
        ipp_service.registration_type = ''
        ipp_service.full_domain_name = IppTestData.ipp_domain_name
        ipp_service.txt_record_map = IppTestData.ipp_txt_record
        expect_service_list[ipp_service.to_string()] = 1
        return expect_service_list
      ipp_service.instance_name = IppTestData.ipp_instance_name
      ipp_service.registration_type = (
          IppTestData.ipp_registration_type + '.local.'
      )
      ipp_service.full_domain_name = ''
      ipp_service.txt_record_map = ''
      expect_service_list[ipp_service.to_string()] = 1
      return expect_service_list
    elif query_string1 == AfpTestData.afp_registration_type:
      if query_string2 == AfpTestData.afp_instance_name:
        afp_service.instance_name = ''
        afp_service.registration_type = ''
        afp_service.full_domain_name = AfpTestData.afp_domain_name
        afp_service.txt_record_map = AfpTestData.afp_txt_record
        expect_service_list[afp_service.to_string()] = 1
        return expect_service_list
    ipp_service.instance_name = IppTestData.ipp_instance_name
    ipp_service.registration_type = (
        IppTestData.ipp_registration_type + '.local.'
    )
    ipp_service.full_domain_name = ''
    ipp_service.txt_record_map = ''
    expect_service_list[ipp_service.to_string()] = 1

    ipp_service.instance_name = ''
    ipp_service.registration_type = ''
    ipp_service.full_domain_name = IppTestData.ipp_domain_name
    ipp_service.txt_record_map = IppTestData.ipp_txt_record
    expect_service_list[ipp_service.to_string()] = 1

    afp_service.instance_name = AfpTestData.afp_instance_name
    afp_service.registration_type = (
        AfpTestData.afp_registration_type + '.local.'
    )
    afp_service.full_domain_name = ''
    afp_service.txt_record_map = ''
    expect_service_list[afp_service.to_string()] = 1

    afp_service.instance_name = ''
    afp_service.registration_type = ''
    afp_service.full_domain_name = AfpTestData.afp_domain_name
    afp_service.txt_record_map = AfpTestData.afp_txt_record
    expect_service_list[afp_service.to_string()] = 1

    return expect_service_list
  elif (
      service_type == WifiP2PEnums.WifiP2pServiceInfo.WIFI_P2P_SERVICE_TYPE_UPNP
  ):
    upnp_service = (
        'uuid:' + UpnpTestData.uuid + '::' + (UpnpTestData.rootdevice)
    )
    expect_service_list[upnp_service] = 1
    if query_string1 != 'upnp:rootdevice':
      upnp_service = (
          'uuid:' + UpnpTestData.uuid + ('::' + UpnpTestData.av_transport)
      )
      expect_service_list[upnp_service] = 1
      upnp_service = (
          'uuid:' + UpnpTestData.uuid + ('::' + UpnpTestData.connection_manager)
      )
      expect_service_list[upnp_service] = 1
      upnp_service = (
          'uuid:' + UpnpTestData.uuid + ('::' + UpnpTestData.service_type)
      )
      expect_service_list[upnp_service] = 1
      upnp_service = 'uuid:' + UpnpTestData.uuid
      expect_service_list[upnp_service] = 1

  return expect_service_list


def check_service_query_result(service_list, expect_service_list):
  """Check serviceList same as expectServiceList or not.

  Args:
      service_list: ServiceList which get from query result
      expect_service_list: ServiceList which hardcode in genExpectTestData

  Returns:
      True: serviceList  same as expectServiceList
      False:Exist discrepancy between serviceList and expectServiceList
  """
  temp_service_list = service_list.copy()
  temp_expect_service_list = expect_service_list.copy()
  for service in service_list.keys():
    if service in expect_service_list:
      del temp_service_list[service]
      del temp_expect_service_list[service]
  return not temp_expect_service_list and not temp_service_list


def _check_all_expect_data(expect_data: dict[str, int]) -> bool:
  for _, v in expect_data.items():
    if v == 1:
      return False
  return True


def request_service_and_check_result(
    ad_service_provider: p2p_utils.DeviceState,
    ad_service_receiver: p2p_utils.DeviceState,
    service_type: int,
    query_string1,
    query_string2,
):
  """Based on service type and query info, check service request result.

  Check same as expect or not on an Android device ad_service_receiver.
  And remove p2p service request after result check.

  Args:
      ad_service_provider: The android device which provide p2p local service
      ad_service_receiver: The android device which query p2p local service
      service_type: P2p local service type, Upnp or Bonjour
      query_string1: Query String, NonNull
      query_string2: Query String, used for Bonjour, Nullable

  Returns:
      0: if service request result is as expected.
  """
  expect_data = gen_expect_test_data(service_type, query_string1, query_string2)
  p2p_utils.discover_p2p_peer(ad_service_receiver, ad_service_provider)
  ad_service_receiver.ad.wifi.wifiP2pStopPeerDiscovery()
  ad_service_receiver.ad.wifi.wifiP2pClearServiceRequests()
  time.sleep(_DEFAULT_FUNCTION_SWITCH_TIME)

  service_id = 0
  if (
      service_type
      == WifiP2PEnums.WifiP2pServiceInfo.WIFI_P2P_SERVICE_TYPE_BONJOUR
  ):
    ad_service_receiver.ad.log.info(
        'Request bonjour service in %s with Query String %s and %s '
        % (ad_service_receiver.ad.serial, query_string1, query_string2)
    )
    ad_service_receiver.ad.log.info('expectData 1st %s' % expect_data)
    if query_string1:
      service_id = ad_service_receiver.ad.wifi.wifiP2pAddBonjourServiceRequest(
          query_string2,  # instanceName
          query_string1,  # serviceType
      )
    else:
      service_id = ad_service_receiver.ad.wifi.wifiP2pAddServiceRequest(
          service_type
      )
    time.sleep(_DEFAULT_FUNCTION_SWITCH_TIME)
    ad_service_receiver.ad.log.info('service request id %s' % service_id)
    p2p_utils.set_dns_sd_response_listeners(ad_service_receiver)
    ad_service_receiver.ad.wifi.wifiP2pDiscoverServices()
    ad_service_receiver.ad.log.info('Check Service Listener')
    time.sleep(_DEFAULT_SERVICE_WAITING_TIME)
    check_discovered_dns_sd_response(
        ad_service_receiver,
        expected_responses=expect_data,
        expected_src_device_address=(
            ad_service_provider.p2p_device.device_address
        ),
        channel_id=ad_service_receiver.channel_ids[0],
        timeout=_NORMAL_TIMEOUT,
    )
    ad_service_receiver.ad.log.info('expectData 2nd %s' % expect_data)
    check_discovered_dns_sd_txt_record(
        ad_service_receiver,
        expected_records=expect_data,
        expected_src_device_address=(
            ad_service_provider.p2p_device.device_address
        ),
        channel_id=ad_service_receiver.channel_ids[0],
        timeout=_NORMAL_TIMEOUT,
    )
    got_all_expects = _check_all_expect_data(expect_data)
    ad_service_receiver.ad.log.info(
        'Got all the expect data : %s', got_all_expects
    )
    asserts.assert_true(
        got_all_expects,
        "Don't got all the expect data.",
    )
  elif (
      service_type == WifiP2PEnums.WifiP2pServiceInfo.WIFI_P2P_SERVICE_TYPE_UPNP
  ):
    ad_service_receiver.ad.log.info(
        'Request upnp service in %s with Query String %s '
        % (ad_service_receiver.ad.serial, query_string1)
    )
    ad_service_receiver.ad.log.info('expectData %s' % expect_data)
    if query_string1:
      service_id = ad_service_receiver.ad.wifi.wifiP2pAddUpnpServiceRequest(
          query_string1
      )
    else:
      service_id = ad_service_receiver.ad.wifi.wifiP2pAddServiceRequest(
          service_type
      )
    p2p_utils.set_upnp_response_listener(ad_service_receiver)
    ad_service_receiver.ad.wifi.wifiP2pDiscoverServices()
    ad_service_receiver.ad.log.info('Check Service Listener')
    time.sleep(_DEFAULT_FUNCTION_SWITCH_TIME)
    p2p_utils.check_discovered_services(
        ad_service_receiver,
        ad_service_provider.p2p_device.device_address,
        expected_dns_sd_sequence=None,
        expected_dns_txt_sequence=None,
        expected_upnp_sequence=expect_data,
    )
  ad_service_receiver.ad.wifi.wifiP2pRemoveServiceRequest(service_id)
  return 0


def request_service_and_check_result_with_retry(
    ad_service_provider,
    ad_service_receiver,
    service_type,
    query_string1,
    query_string2,
    retry_count=3,
):
  """allow failures for requestServiceAndCheckResult.

  Service

      discovery might fail unexpectedly because the request packet might not be
      received by the service responder due to p2p state switch.

  Args:
      ad_service_provider: The android device which provide p2p local service
      ad_service_receiver: The android device which query p2p local service
      service_type: P2p local service type, Upnp or Bonjour
      query_string1: Query String, NonNull
      query_string2: Query String, used for Bonjour, Nullable
      retry_count: maximum retry count, default is 3
  """
  ret = 0
  while retry_count > 0:
    ret = request_service_and_check_result(
        ad_service_provider,
        ad_service_receiver,
        service_type,
        query_string1,
        query_string2,
    )
    if ret == 0:
      break
    retry_count -= 1

  asserts.assert_equal(0, ret, 'cannot find any services with retries.')


def _check_no_discovered_service(
    ad: android_device.AndroidDevice,
    callback_handler: callback_handler_v2.CallbackHandlerV2,
    event_name: str,
    expected_src_device_address: str,
    timeout: datetime.timedelta = _DEFAULT_TIMEOUT,
):
    """Checks that no service is received from the specified source device."""
    def _is_expected_event(event):
        src_device = constants.WifiP2pDevice.from_dict(
            event.data['sourceDevice']
        )
        return src_device.device_address == expected_src_device_address

    # Set to a small timeout to allow pulling all received events
    if timeout.total_seconds() <= 1:
        timeout = datetime.timedelta(seconds=1)
    try:
        event = callback_handler.waitForEvent(
            event_name=event_name,
            predicate=_is_expected_event,
            timeout=timeout.total_seconds(),
        )
    except errors.CallbackHandlerTimeoutError:
        # Timeout error is expected as there should not be any qualified service
        return
    asserts.assert_is_none(
        event,
        f'{ad} should not discover p2p service. Discovered: {event}',
    )


def check_discovered_dns_sd_response(
    device: p2p_utils.DeviceState,
    expected_responses: Sequence[Sequence[str, str]],
    expected_src_device_address: str,
    channel_id: int | None = None,
    timeout: datetime.timedelta = _DEFAULT_TIMEOUT,
):
    """Check discovered DNS SD responses.

    If no responses are expected, check that no DNS SD response appear within
    timeout. Otherwise, wait for all expected responses within timeout.

    This assumes that Bonjour service listener is set by
    `set_dns_sd_response_listeners`.

    Args:
        device: The device that is discovering DNS SD responses.
        expected_responses: The expected DNS SD responses.
        expected_src_device_address: This only checks services that are from the
            expected source device.
        channel_id: The channel to check for expected responses.
        timeout: The wait timeout.
    """
    channel_id = channel_id or device.channel_ids[0]
    callback_handler = device.dns_sd_response_listeners[channel_id]

    def _all_service_received(event):
        nonlocal expected_responses
        src_device = constants.WifiP2pDevice.from_dict(
            event.data['sourceDevice']
        )
        if src_device.device_address != expected_src_device_address:
            return False
        registration_type = event.data['registrationType']
        instance_name = event.data['instanceName']
        service_item = instance_name + registration_type
        device.ad.log.info('Received DNS SD response: %s', service_item)
        if service_item in expected_responses:
            expected_responses[service_item] = 0
        _check_all_expect_data(expected_responses)

    device.ad.log.info('Waiting for DNS SD services: %s', expected_responses)
    # Set to a small timeout to allow pulling all received events
    if timeout.total_seconds() <= 1:
        timeout = datetime.timedelta(seconds=1)
    try:
        callback_handler.waitForEvent(
            event_name=constants.ON_DNS_SD_SERVICE_AVAILABLE,
            predicate=_all_service_received,
            timeout=timeout.total_seconds(),
        )
    except errors.CallbackHandlerTimeoutError:
        device.ad.log.info(f'need to wait for services: {expected_responses}')


def check_discovered_dns_sd_txt_record(
    device: p2p_utils.DeviceState,
    expected_records: Sequence[Sequence[str, dict[str, str]]],
    expected_src_device_address: str,
    channel_id: int | None = None,
    timeout: datetime.timedelta = _DEFAULT_TIMEOUT,
):
    """Check discovered DNS SD TXT records.

    If no records are expected, check that no DNS SD TXT record appear within
    timeout. Otherwise, wait for all expected records within timeout.

    This assumes that Bonjour service listener is set by
    `set_dns_sd_response_listeners`.

    Args:
        device: The device that is discovering DNS SD TXT records.
        expected_records: The expected DNS SD TXT records.
        expected_src_device_address: This only checks services that are from the
            expected source device.
        channel_id: The channel to check for expected records.
        timeout: The wait timeout.
    """
    channel_id = channel_id or device.channel_ids[0]
    idx = device.channel_ids.index(channel_id)
    callback_handler = device.dns_sd_response_listeners[idx]

    device.ad.log.info('Expected DNS SD TXT records: %s', expected_records)
    def _all_service_received(event):
        nonlocal expected_records
        src_device = constants.WifiP2pDevice.from_dict(
            event.data['sourceDevice']
        )
        if src_device.device_address != expected_src_device_address:
            return False
        full_domain_name = event.data['fullDomainName']
        txt_record_map = event.data['txtRecordMap']
        record_to_remove = full_domain_name + str(txt_record_map)
        device.ad.log.info('Received DNS SD TXT record: %s', record_to_remove)
        if record_to_remove in expected_records:
            expected_records[record_to_remove] = 0
        _check_all_expect_data(expected_records)

    device.ad.log.info('Waiting for DNS SD TXT records: %s', expected_records)
    # Set to a small timeout to allow pulling all received events
    if timeout.total_seconds() <= 1:
        timeout = datetime.timedelta(seconds=1)
    try:
        callback_handler.waitForEvent(
            event_name=constants.ON_DNS_SD_TXT_RECORD_AVAILABLE,
            predicate=_all_service_received,
            timeout=timeout.total_seconds(),
        )
    except errors.CallbackHandlerTimeoutError:
        device.ad.log.info(f'need to wait for services: {expected_records}')


class WifiP2PEnums:
  """Enums for WifiP2p."""

  class WifiP2pConfig:
    DEVICEADDRESS_KEY = 'deviceAddress'
    WPSINFO_KEY = 'wpsInfo'
    GO_INTENT_KEY = 'groupOwnerIntent'
    NETID_KEY = 'netId'
    NETWORK_NAME = 'networkName'
    PASSPHRASE = 'passphrase'
    GROUP_BAND = 'groupOwnerBand'

  class WpsInfo:
    WPS_SETUP_KEY = 'setup'
    BSSID_KEY = 'BSSID'
    WPS_PIN_KEY = 'pin'
    WIFI_WPS_INFO_PBC = 0
    WIFI_WPS_INFO_DISPLAY = 1
    WIFI_WPS_INFO_KEYPAD = 2
    WIFI_WPS_INFO_LABEL = 3
    WIFI_WPS_INFO_INVALID = 4

  class WifiP2pServiceInfo:
    # Macros for wifi p2p.
    WIFI_P2P_SERVICE_TYPE_ALL = 0
    WIFI_P2P_SERVICE_TYPE_BONJOUR = 1
    WIFI_P2P_SERVICE_TYPE_UPNP = 2
    WIFI_P2P_SERVICE_TYPE_VENDOR_SPECIFIC = 255

  class WifiP2pDnsSdServiceResponse:
    instance_name = ''
    registration_type = ''
    full_domain_name = ''
    txt_record_map = {}

    def __init__(self):
      pass

    def to_string(self):
      return (
          self.instance_name
          + self.registration_type
          + (self.full_domain_name + str(self.txt_record_map))
      )
