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

import datetime
import time

from direct import constants
from direct import p2p_utils
from mobly import asserts
from mobly.controllers import android_device
from mobly.controllers.android_device_lib import adb

_DEFAULT_TIMEOUT = datetime.timedelta(seconds=30)
_DEFAULT_SLEEPTIME = 5

P2P_CONNECT_NEGOTIATION = 0
P2P_CONNECT_JOIN = 1
P2P_CONNECT_INVITATION = 2


# Trigger p2p connect to device_go from device_gc.
def p2p_connect(
    device_gc: p2p_utils.DeviceState,
    device_go: p2p_utils.DeviceState,
    wps_setup,
    p2p_connect_type=P2P_CONNECT_NEGOTIATION,
    go_ad=None,
):
  """Trigger p2p connect to ad2 from ad1.

  Args:
      device_gc: The android device (Client)
      device_go: The android device (GO)
      wps_setup: which wps connection would like to use
      p2p_connect_type: enumeration, which type this p2p connection is
      go_ad: The group owner android device which is used for the invitation
        connection
  """
  device_gc.ad.log.info(
      'Create p2p connection from %s to %s via wps: %s type %d'
      % (device_gc.ad.serial, device_go.ad.serial, wps_setup, p2p_connect_type)
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
        group_owner_address=device_go.p2p_device.device_address
    )
    asserts.assert_true(
        peer_p2p_device.is_group_owner,
        f"P2p device {peer_p2p_device} should be group owner.",
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
  p2p_utils.p2p_connect(device_gc, device_go, p2p_config)


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
  ad.log.info('p2p go ip')
  event_handler = ad.wifi.wifiP2pRequestConnectionInfo()
  ad.log.info(type(event_handler))
  result = event_handler.waitAndGet(
      event_name=constants.ON_CONNECTION_INFO_AVAILABLE,
      timeout=_DEFAULT_TIMEOUT.total_seconds(),
  )
  ip = result.data['groupOwnerHostAddress'].replace('/', '')
  ad.log.info('p2p go ip: %s' % ip)
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
    InstanceName = ''
    RegistrationType = ''
    FullDomainName = ''
    TxtRecordMap = {}

    def __init__(self):
      pass

    def toString(self):
      return (
          self.InstanceName
          + self.RegistrationType
          + (self.FullDomainName + str(self.TxtRecordMap))
      )
