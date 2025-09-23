# Copyright (C) 2025 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#         http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

import time
import logging
import re

from mobly import asserts
from mobly import base_test
from mobly import utils
from mobly import test_runner
from mobly.controllers import android_device
from mobly.snippet import errors

# --- Constants ---
WIFI_SNIPPET_PACKAGE = 'com.google.snippet.wifi'
# Constants mirroring the DEVICE_MOBILITY_STATE_* values from WifiManager.java
MOBILITY_STATE_HIGH = 1
MOBILITY_STATE_LOW = 2
MOBILITY_STATE_STATIONARY = 3
GWP_PACKAGE = "com.google.android.apps.carrier.carrierwifi"
SCONE_PACKAGE = "com.google.android.apps.scone"
GWP_DISABLE_COMMAND = f"pm disable {GWP_PACKAGE}"
GWP_ENABLE_COMMAND = f"pm enable {GWP_PACKAGE}"
SCONE_DISABLE_COMMAND = f"pm disable {SCONE_PACKAGE}"
SCONE_ENABLE_COMMAND = f"pm enable {SCONE_PACKAGE}"
STATIONARY_CONNECT_TIMEOUT_S = 30
NO_CONNECT_WAIT_S = 30
CALLBACK_TIMEOUT = 60


class WifiMobilityTest(base_test.BaseTestClass):
    """
    Tests Wi-Fi auto-connection behavior based on device mobility state
    for carrier-identified networks.
    """

    def setup_class(self):
        """Initializes devices and loads necessary snippets."""
        self.ads = self.register_controller(android_device)
        self.dut = self.ads[0]
        self.ap = self.ads[1]

        def _setup_device(device):
            device.load_snippet('wifi', WIFI_SNIPPET_PACKAGE)

        utils.concurrent_exec(_setup_device, [(self.dut,), (self.ap,)])

        self.ap_started = False
        self.dut.wifi.wifiToggleEnable()
        # Get the carrier ID from the DUT once at the start of the test class.
        self.carrier_id = self._get_carrier_id_from_dut()
        time.sleep(2)

    def setup_test(self):
        """Prepares for each test by starting the AP and adding the suggestion."""
        logging.info("--- SETUP TEST ---")
        self.ap_started = False
        logging.info("Disabling GWP and SCONE on DUT.")
        try:
            self.dut.adb.shell(GWP_DISABLE_COMMAND)
            self.dut.adb.shell(SCONE_DISABLE_COMMAND)
        except Exception as e:
            logging.warning(f"Could not disable mobility services: {e}")

        # Set an initial "moving" state to prevent any premature connections during setup.
        # This is done *after* disabling services to prevent the state from being overridden.
        logging.info("Setting initial mobility state to HIGH.")
        self.dut.wifi.wifiSetDeviceMobilityState(MOBILITY_STATE_HIGH)
        time.sleep(1)

        logging.info("Cleaning up DUT Wi-Fi state.")
        self.dut.wifi.wifiClearConfiguredNetworks()
        # Common setup for all test cases
        self.dut.wifi.wifiAllowAutojoinGlobal(True)
        # Start a standard Local-Only Hotspot on the AP device.
        # The DUT will treat this as a carrier network because of the suggestion
        # added below with the carrier ID.
        logging.info("Enabling carrier AP using Local-Only Hotspot.")
        self.ssid, self.password = self._start_local_only_hotspot()

        # Add a network suggestion on the DUT. The crucial part is the '-c {self.carrier_id}'
        # flag, which causes the framework to identify this network as a carrier network.
        logging.info(f"Adding carrier network suggestion for '{self.ssid}' with carrier ID {self.carrier_id}.")
        suggestion_command = (
            f'cmd wifi add-suggestion "{self.ssid}" wpa2 "{self.password}" '
            f'-c {self.carrier_id}'
        )
        approval_command = 'cmd wifi network-suggestions-set-user-approved com.android.shell yes'
        self.dut.adb.shell(suggestion_command)
        self.dut.adb.shell(approval_command)
        time.sleep(5) # Allow time for suggestion to be processed

    def teardown_test(self):
        """Cleans up after each test."""
        logging.info("--- TEARDOWN TEST ---")
        if self.ap_started:
            logging.info("Stopping Local-Only Hotspot on AP device.")
            try:
                self.ap.wifi.wifiStopLocalOnlyHotspot()
            except Exception as e:
                logging.error(f"Failed to stop Local-Only Hotspot: {e}")

        logging.info("Re-enabling GWP and SCONE services on DUT.")
        try:
            self.dut.adb.shell(GWP_ENABLE_COMMAND)
            self.dut.adb.shell(SCONE_ENABLE_COMMAND)
        except Exception as e:
            logging.warning(f"Could not re-enable mobility services: {e}")

        logging.info("Resetting DUT mobility state to 'STATIONARY'.")
        self.dut.wifi.wifiSetDeviceMobilityState(MOBILITY_STATE_STATIONARY)

        logging.info("Performing final Wi-Fi cleanup on DUT.")
        self.dut.wifi.wifiClearConfiguredNetworks()

    def _get_carrier_id_from_dut(self):
        """Runs a robust adb command to get the carrier ID from the active SIM."""
        try:
            # This command finds the line for the active SIM (in slot 0 or 1)
            # from the subscription info dump.
            command = 'dumpsys isub | grep "simSlotIndex=[0-9]"'
            output = self.dut.adb.shell(command).decode('utf-8').strip()

            # Search for the pattern "carrierId=" followed by one or more digits.
            match = re.search(r'carrierId=(\d+)', output)
            if match:
                # The first group in the match is the number.
                carrier_id = int(match.group(1))
                if carrier_id == -1:
                    asserts.abort_class("Active SIM found, but carrier ID is invalid (-1).")
                logging.info(f"Successfully retrieved active carrier ID: {carrier_id}")
                return carrier_id
            else:
                asserts.abort_class(
                    "Could not parse carrier ID from active SIM info: " + output)
        except Exception as e:
            asserts.abort_class(f"Failed to get carrier ID from device. Error: {e}")

    def _get_current_ssid(self):
        """Helper to get the SSID of the currently connected network on the DUT."""
        try:
            connection_info = self.dut.wifi.wifiGetCurrentConnectionInfo()
            if connection_info and connection_info.get('ssid') and connection_info.get('ssid') != '<unknown ssid>':
                return connection_info['ssid'].strip('"')
        except Exception as e:
            logging.warning(f"Could not get connection info: {e}")
        return None

    def _start_local_only_hotspot(self):
        """Starts the Local-Only Hotspot and returns its credentials."""
        logging.info("Attempting to start Local-Only Hotspot.")
        try:
            callback = self.ap.wifi.wifiStartLocalOnlyHotspot()
            event = callback.waitAndGet(event_name="onStarted", timeout=CALLBACK_TIMEOUT)
            self.ap_started = True
            ssid = event.data.get('ssid')
            password = event.data.get('passphrase')
            logging.info(f"Local-Only Hotspot enabled successfully with SSID: {ssid}")
            asserts.assert_true(ssid and password, "Failed to get hotspot credentials.")
            return ssid, password
        except Exception as e:
            asserts.abort_class(f"Failed to start Local-Only Hotspot. Reason: {e}")

    # --- Test Cases ---

    def test_no_connection_in_high_mobility(self):
        """Verifies the device does not auto-connect in a high mobility state."""
        logging.info("Setting DUT mobility state to 'HIGH'.")
        self.dut.wifi.wifiSetDeviceMobilityState(MOBILITY_STATE_HIGH)
        logging.info("Verifying no connection while in high mobility.")
        time.sleep(NO_CONNECT_WAIT_S)
        asserts.assert_is_none(self._get_current_ssid(),
                               "FAIL: Device connected while in a HIGH mobility state.")
        logging.info("PASS: Did not connect while in high mobility.")

    def test_no_connection_in_low_mobility(self):
        """Verifies the device does not auto-connect in a low mobility state."""
        logging.info("Setting DUT mobility state to 'LOW'.")
        self.dut.wifi.wifiSetDeviceMobilityState(MOBILITY_STATE_LOW)
        logging.info("Verifying no connection while in low mobility.")
        time.sleep(NO_CONNECT_WAIT_S)
        asserts.assert_is_none(self._get_current_ssid(),
                               "FAIL: Device connected while in a LOW mobility state.")
        logging.info("PASS: Did not connect while in low mobility.")

    def test_connection_in_stationary_mobility(self):
        """Verifies the device auto-connects in a stationary state."""
        logging.info("Setting mobility state to 'STATIONARY'.")
        self.dut.wifi.wifiSetDeviceMobilityState(MOBILITY_STATE_STATIONARY)

        # Passively wait for the framework to connect on its own.
        logging.info(f"Waiting for device to AUTO-CONNECT to '{self.ssid}'...")
        end_time = time.time() + STATIONARY_CONNECT_TIMEOUT_S
        while time.time() < end_time:
            if self._get_current_ssid() == self.ssid:
                logging.info(f"PASS: Successfully auto-connected to '{self.ssid}'.")
                return
            time.sleep(1)
        asserts.fail(f"FAIL: Device did not auto-connect to '{self.ssid}' within {STATIONARY_CONNECT_TIMEOUT_S}s.")


if __name__ == '__main__':
    test_runner.main()
