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

import time
import logging
import enum

from mobly.controllers import android_device
from mobly.controllers.android_device_lib import callback_handler_v2
from mobly import asserts
from mobly.snippet import errors
from mobly import utils
from softap import constants
from queue import Empty
from mobly.controllers.android_device_lib import adb


_CALLBACK_NAME = "callbackName"
_CALLBACK_TIMEOUT = constants.CALLBACK_TIMEOUT.total_seconds()
_WIFI_SCAN_INTERVAL_SEC = constants.WIFI_SCAN_INTERVAL_SEC.total_seconds()

def match_networks(target_params, networks):
    """Finds the WiFi networks that match a given set of parameters in a list
    of WiFi networks.

    To be considered a match, the network should contain every key-value pair
    of target_params

    Args:
        target_params:
        A dict with 1 or more key-value pairs representing a Wi-Fi network.
        E.g. { 'SSID': 'wh_ap1_5g', 'BSSID': '30:b5:c2:33:e4:47' }
        networks: A list of dict objects representing WiFi networks.

    Returns:
        The networks that match the target parameters.
    """
    results = []
    asserts.assert_true(target_params,
                        "Expected networks object 'target_params' is empty")
    for n in networks:
        add_network = 1
        for k, v in target_params.items():
            if k not in n:
                add_network = 0
                break
            if n[k] != v:
                add_network = 0
                break
        if add_network:
            results.append(n)
    return results

def start_wifi_connection_scan_and_check_for_network(
    ad: android_device.AndroidDevice,
    network_ssid: str,
    max_tries: int=3):
    """
    Start connectivity scans & checks if the |network_ssid| is seen in
    scan results. The method performs a max of |max_tries| connectivity scans
    to find the network.

    Args:
        ad: An AndroidDevice object.
        network_ssid: SSID of the network we are looking for.
        max_tries: Number of scans to try.
    Returns:
        True: if network_ssid status is expected in scan results.
        False: if network_ssid status is expected in scan results.
    """
    for num_tries in range(max_tries):
        scanned_results = ad.wifi.wifiScanAndGetResults()
        ad.wifi.wifiSetScanThrottleDisable()
        scanned_ssids = sorted(
            [scan_result['SSID'] for scan_result in scanned_results]
            )
        if network_ssid in scanned_ssids:
            return True
        else:
            if (num_tries + 1) == max_tries:
                break
            time.sleep(_WIFI_SCAN_INTERVAL_SEC)
            continue
    return False

def start_wifi_connection_scan_and_ensure_network_found(
    ad, network_ssid, max_tries=3):
    """
    Start connectivity scans & ensure the |network_ssid| is seen in
    scan results. The method performs a max of |max_tries| connectivity scans
    to find the network.
    This method asserts on failure!

    Args:
        ad: An AndroidDevice object.
        network_ssid: SSID of the network we are looking for.
        max_tries: Number of scans to try.
    """
    logging.info("Starting scans to ensure %s is present", network_ssid)
    assert_msg = "Failed to find " + network_ssid + " in scan results" \
        " after " + str(max_tries) + " tries"
    asserts.assert_true(
        start_wifi_connection_scan_and_check_for_network(
      ad, network_ssid, max_tries), assert_msg)

def create_softap_config():
    """Create a softap config with random ssid and password."""
    ap_ssid = "softap_" + utils.rand_ascii_str(8)
    ap_password = utils.rand_ascii_str(8)
    logging.info("softap setup: %s %s", ap_ssid, ap_password)
    config = {
        constants.WiFiTethering.SSID_KEY: ap_ssid,
        constants.WiFiTethering.PWD_KEY: ap_password,
    }
    return config

def start_wifi_tethering(ad: android_device.AndroidDevice,
                         ssid: str,
                         password: str,
                         band: str=None,
                         hidden:  bool = False,
                         security: str =None):
    """Starts wifi tethering on an android_device.

    Args:
        ad: android_device to start wifi tethering on.
        ssid: The SSID the soft AP should broadcast.
        password: The password the soft AP should use.
        band: The band the soft AP should be set on. It should be either
            WifiEnums.WIFI_CONFIG_APBAND_2G or WifiEnums.WIFI_CONFIG_APBAND_5G.
        hidden: boolean to indicate if the AP needs to be hidden or not.
        security: security type of softap.

    Returns:
        No return value.
        Error checks in this function will raise test failure signals
    """
    config = {constants.WiFiTethering.SSID_KEY: ssid}
    if password:
        config[constants.WiFiTethering.PWD_KEY] = password
    if band:
        config[constants.WiFiTethering.AP_BAND_KEY] = band
    if hidden:
        config[constants.WiFiTethering.HIDDEN_KEY] = hidden
    if security:
        config[constants.WiFiTethering.SECURITY] = security

    asserts.assert_true(ad.wifi.wifiSetWifiApConfiguration(config),
                        "Failed to update WifiAp Configuration")
    handler_state = ad.wifi.tetheringStartTrackingTetherStateChange()
    handler_tethering = ad.wifi.tetheringStartTetheringWithProvisioning(0, False)
    time.sleep(_WIFI_SCAN_INTERVAL_SEC)
    try:
        result_receiver = handler_state.waitAndGet('TetherStateChangedReceiver')
        callback_name = result_receiver.data["callbackName"]
        ad.log.info('StateChanged callback_name: %s', callback_name)
        logging.info("result_receiver %s", result_receiver)
        ad.log.debug("Tethering started successfully.")
    except Exception:
        msg = "Failed to receive confirmation of wifi tethering starting"
        asserts.fail(msg)
    finally:
      ad.wifi.tetheringStopTrackingTetherStateChange()


def start_wifi_connection_scan_and_ensure_network_not_found(
    ad, network_ssid, max_tries=2):
    """
    Start connectivity scans & ensure the |network_ssid| is not seen in
    scan results. The method performs a max of |max_tries| connectivity scans
    to find the network.
    This method asserts on failure!

    Args:
        ad: An AndroidDevice object.
        network_ssid: SSID of the network we are looking for.
        max_tries: Number of scans to try.
    """
    ad.log.info("Starting scans to ensure %s is not present", network_ssid)
    assert_msg = "Found " + network_ssid + " in scan results" \
        " after " + str(max_tries) + " tries"

    asserts.assert_false(
        start_wifi_connection_scan_and_check_for_network(
            ad, network_ssid, max_tries), assert_msg)

def wait_for_wifi_state(ad, state):
    """Toggles the state of wifi.

    TestFailure signals are raised when something goes wrong.

    Args:
        ad: An AndroidDevice object.
        state: Wifi state to wait for.
    """
    if state == ad.wifi.wifiCheckState():
        # Check if the state is already achieved, so we don't wait for the
        # state change event by mistake.
        return
    state_handler = ad.wifi.wifiStartTrackForStateChange()
    fail_msg = "Device did not transition to Wi-Fi state to %s on %s." % (
        state, ad.serial)
    try:
        state_handler.waitAndGet(event_name="WifiNetworkConnected",
                                 timeout=10)
    except Empty:
        asserts.assert_equal(state, ad.wifi.wifiCheckState(), fail_msg)
    finally:
        ad.wifi.wifiStopTrackForStateChange()

def adb_shell_ping(ad,
                   count=120,
                   dest_ip="www.google.com"):
    try:
      results = ad.adb.shell("ping -c %d %s" % (count, dest_ip))
      ad.log.info("After to ping results %s" % results)
      return True
    except adb.AdbError:
      time.sleep(1)
      results=ad.adb.shell("ping -c %d %s" % (count, dest_ip))
      return True
    if not results:
        asserts.fail("ping empty results - seems like a failure")
        return False


def verify_11ax_softap(dut, dut_client, wifi6_supported_models):
    """Verify 11ax SoftAp if devices support it.

    Check if both DUT and DUT client supports 11ax, then SoftAp turns on
    with 11ax mode and DUT client can connect to it.

    Args:
      dut: Softap device.
      dut_client: Client connecting to softap.
      wifi6_supported_models: List of device models supporting 11ax.
    """
    if dut.model in wifi6_supported_models and dut_client.model in wifi6_supported_models:
        logging.info(
            "Verifying 11ax softap. DUT model: %s, DUT Client model: %s",
            dut.model, dut_client.model)
        asserts.assert_true(
            dut_client.wifi.wifiGetConnectionStandard() ==
            constants.WIFI_STANDARD_11AX,
            "DUT failed to start SoftAp in 11ax.")


def reset_wifi(ad: android_device.AndroidDevice):
    """Clears all saved Wi-Fi networks on a device.

    This will turn Wi-Fi on.

    Args:
        ad: An AndroidDevice object.
    """
    networks = ad.wifi.wifiGetConfiguredNetworks()
    ad.log.info('Configured Networks = %s', networks)
    if not networks:
        return
    removed = []
    for net in networks:
        if net['networkId'] not in removed:
           ad.wifi.wifiForgetNetwork(net['networkId'])
           removed.append(net['networkId'])
        else:
           continue
    # Check again to see if there's any network left.
    asserts.assert_true(
        not ad.wifi.wifiGetConfiguredNetworks(),
        "Failed to remove these Wi-Fi network Lists: %s" % networks)

def _wifi_connect(ad: android_device.AndroidDevice,
                  network: dict,
                  num_of_tries=1,
                  check_connectivity=True):

    """Connect an Android device to a wifi network.

    Initiate connection to a wifi network, wait for the "connected" event, then
    confirm the connected ssid is the one requested.

    This will directly fail a test if anything goes wrong.

    Args:
        ad: android_device object to initiate connection on.
        network: A dictionary representing the network to connect to. The
                 dictionary must have the key "SSID".
        num_of_tries: An integer that is the number of times to try before
                      delaring failure. Default is 1.
    """
    asserts.assert_true(
      constants.WiFiTethering.SSID_KEY in network,
      "Key '%s' must be present in network definition." % constants.WiFiTethering.SSID_KEY)
    state_handler = ad.wifi.wifiStartTrackForStateChange()
    expected_ssid = network[constants.WiFiTethering.SSID_KEY]
    ad.log.info("Starting connection process to %s", expected_ssid)
    ad.wifi.wifiConnecting(network)
    try:
      results = state_handler.waitAndGet(event_name="WifiNetworkConnected",
                                         timeout=10)
      ad.log.info("Connected to Wi-Fi network %s.",
                  results.data[constants.WiFiTethering.SSID_KEY])
      asserts.assert_equal(
        expected_ssid,
        results.data[constants.WiFiTethering.SSID_KEY],
        f'{ad} Need to connect to expected ssid {expected_ssid}.',
        )
      results = state_handler.waitAndGet(event_name="WifiStateChanged",
                                         timeout=10)
      ad.log.info("Wifi state = %s", results.data['enabled'])
      asserts.assert_true(results.data['enabled'] , "Wifi State is not correct.")
    except Exception as error:
      ad.log.error("Failed to connect to %s with error %s", expected_ssid,
                     error)
    finally:
      ad.wifi.wifiStopTrackForStateChange()

def _stop_tethering(ad: android_device.AndroidDevice) -> bool:
    """Stops any ongoing tethering sessions on the android device.

    Args:
      ad: The Android device object.

    Returns:
      True if tethering is disabled successfully, False otherwise.
    """
    if not ad.wifi.wifiIsApEnabled():
      return True
    ad.wifi.tetheringStopTethering()
    return ad.wifi.wifiWaitForTetheringDisabled()