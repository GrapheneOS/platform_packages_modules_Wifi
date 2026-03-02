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

"""CTS tests for Wi-Fi Location-sensitive APIs from a background app.

This test class verifies the permission model for apps accessing Wi-Fi APIs
that can reveal location information (e.g., scan results, connection info)
while the app is running in the background.
"""

import time
import logging

from android.platform.test.annotations import ApiTest
from mobly import asserts
from mobly import base_test
from mobly import utils
from mobly.controllers import android_device
from mobly import test_runner
import wifi_test_utils


@ApiTest(
    apis=[
        "android.Manifest.permission#ACCESS_BACKGROUND_LOCATION",
    ]
)
class WifiLocationInfoBackgroundTest(base_test.BaseTestClass):
    _WIFI_SNIPPET_PACKAGE = "com.google.snippet.wifi"
    _FINE_LOCATION_PERMISSION = "android.permission.ACCESS_FINE_LOCATION"
    _BACKGROUND_LOCATION_PERMISSION = "android.permission.ACCESS_BACKGROUND_LOCATION"

    def setup_class(self) -> None:
        super().setup_class()
        self.ads = self.register_controller(android_device, min_number=2)
        self.dut = self.ads[0]
        self.dut2 = self.ads[1]
        utils.concurrent_exec(
            self._setup_device,
            param_list=[[ad] for ad in self.ads],
            raise_on_exception=True,
        )

    def _setup_device(self, ad: android_device.AndroidDevice) -> None:
        ad.load_snippet('wifi',  self._WIFI_SNIPPET_PACKAGE)
        wifi_test_utils.enable_wifi_verbose_logging(ad)
        wifi_test_utils.set_screen_on_and_unlock(ad)
        ad.adb.shell("settings put secure location_mode 3")
        ad.wifi.utilityBringToBackground()

    def setup_test(self):
        for ad in self.ads:
            ad.wifi.wifiToggleState(True)
            ad.adb.shell(f"pm revoke --user current {self._WIFI_SNIPPET_PACKAGE} {self._FINE_LOCATION_PERMISSION}")
            ad.adb.shell(f"pm revoke --user current {self._WIFI_SNIPPET_PACKAGE} {self._BACKGROUND_LOCATION_PERMISSION}")
            ad.unload_snippet("wifi")
            ad.load_snippet("wifi", self._WIFI_SNIPPET_PACKAGE)
            ad.wifi.utilityBringToBackground()

    def teardown_test(self):
        for ad in self.ads:
            ad.adb.shell(f"pm revoke --user current {self._WIFI_SNIPPET_PACKAGE} {self._FINE_LOCATION_PERMISSION}")
            ad.adb.shell(f"pm revoke --user current {self._WIFI_SNIPPET_PACKAGE} {self._BACKGROUND_LOCATION_PERMISSION}")
            ad.unload_snippet("wifi")
            ad.load_snippet("wifi", self._WIFI_SNIPPET_PACKAGE)

    def _connect_dut_to_hotspot(self):
        """Starts a hotspot on one device and connects the other to it."""
        callback = self.dut2.wifi.wifiStartLocalOnlyHotspot()
        on_started_event = callback.waitAndGet(event_name="onStarted", timeout=30)
        ssid = on_started_event.data['ssid']
        password = on_started_event.data['passphrase']
        wifi_config = {"SSID": ssid}
        if password:
            wifi_config["password"] = password
        self.dut.log.info(f"DUT2 started hotspot: {ssid}")
        self.dut.wifi.wifiConnecting(wifi_config)
        self.dut.log.info(f"DUT attempting to connect to {ssid}")
        time.sleep(12) # Increased wait time
        return ssid

    def test_scan_trigger_allowed_with_background_location_permission(self):
        """Verifies a Wi-Fi scan succeeds with fine and background location permissions.
        On modern Android versions, apps scanning for Wi-Fi in the background require both
        ACCESS_FINE_LOCATION and ACCESS_BACKGROUND_LOCATION permissions. This test
        validates that scenario.
        Steps:
            1. Grant ACCESS_FINE_LOCATION to the snippet application via ADB.
            2. Grant ACCESS_BACKGROUND_LOCATION to the snippet application via ADB.
            3. Trigger a Wi-Fi scan.
        Expected Result:
            The Wi-Fi scan is successfully initiated, and the RPC call returns True.
        """
        self.dut.adb.shell(f"pm grant --user current {self._WIFI_SNIPPET_PACKAGE} {self._FINE_LOCATION_PERMISSION}")
        self.dut.adb.shell(f"pm grant --user current {self._WIFI_SNIPPET_PACKAGE} {self._BACKGROUND_LOCATION_PERMISSION}")
        logging.info("Granted FINE and BACKGROUND location permissions.")
        time.sleep(2) # Allow permissions to settle
        # The wifiStartScanAndGetStatus RPC should return True, indicating success.
        asserts.assert_true(
            self.dut.wifi.wifiStartScanAndGetStatus(),
            "Scan trigger should succeed with both fine and background location permissions."
        )

    def test_scan_trigger_not_allowed_with_fine_location_permission(self):
        """Verifies a background Wi-Fi scan fails with only fine location permission.
        On modern Android versions, an app targeting the background requires both
        ACCESS_FINE_LOCATION and ACCESS_BACKGROUND_LOCATION to perform Wi-Fi scans.
        This test validates that providing only fine location is insufficient.
        Steps:
            1. Ensure the snippet has ACCESS_FINE_LOCATION permission.
            2. Explicitly revoke ACCESS_BACKGROUND_LOCATION permission to ensure a
               clean test state.
            3. Attempt to trigger a Wi-Fi scan.
        Expected Result:
            The Wi-Fi scan attempt should be blocked by the system, and the RPC
            call should return False.
        """
        self.dut.adb.shell(
            f"pm grant --user current {self._WIFI_SNIPPET_PACKAGE} {self._FINE_LOCATION_PERMISSION}"
        )
        logging.info("Granted FINE location permissions.")
        time.sleep(2) # Allow permissions to settle
        # The wifiStartScanAndGetStatus RPC should return False.
        asserts.assert_false(
            self.dut.wifi.wifiStartScanAndGetStatus(),
            "Scan trigger should fail when only fine location permission is granted."
        )

    def test_scan_results_retrieval_allowed_with_background_location_permission(self):
        """Verifies scan results retrieval succeeds with background and fine location.
        Steps:
            1. Grant ACCESS_FINE_LOCATION to the snippet.
            2. Grant ACCESS_BACKGROUND_LOCATION to the snippet.
            3. Attempt to retrieve Wi-Fi scan results.
        Expected Result:
            The scan results retrieval should succeed and return a list of results.
        """
        self.dut.adb.shell(f"pm grant --user current {self._WIFI_SNIPPET_PACKAGE} {self._FINE_LOCATION_PERMISSION}")
        self.dut.adb.shell(f"pm grant --user current {self._WIFI_SNIPPET_PACKAGE} {self._BACKGROUND_LOCATION_PERMISSION}")
        logging.info("Granted FINE and BACKGROUND location permissions.")
        time.sleep(2) # Allow permissions to settle
        asserts.assert_true(
            self.dut.wifi.wifiGetScanResults(),
            "Scan results retrieval should succeed with location permission.")

    def test_scan_results_retrieval_not_allowed_with_fine_location_permission(self):
        """Verifies scan results retrieval fails with only fine location permission.
        Steps:
            1. Grant ACCESS_FINE_LOCATION permission to the snippet.
            2. Revoke ACCESS_BACKGROUND_LOCATION permission.
            3. Attempt to retrieve Wi-Fi scan results.
        Expected Result:
            The scan results retrieval should fail and return an empty list.
        """
        self.dut.adb.shell(f"pm grant --user current {self._WIFI_SNIPPET_PACKAGE} {self._FINE_LOCATION_PERMISSION}")
        logging.info("Granted FINE location permissions.")
        time.sleep(2) # Allow permissions to settle
        asserts.assert_false(
            self.dut.wifi.wifiGetScanResults(),
            "Scan results retrieval should fail without location permission.")

    def test_connection_info_retrieval_allowed_with_background_location_permission(self):
        """Verifies connection info retrieval succeeds with background and fine location.
        Steps:
            1. Grant ACCESS_FINE_LOCATION and ACCESS_BACKGROUND_LOCATION permissions.
            2. Connect to a Wi-Fi network.
            3. Attempt to retrieve Wi-Fi connection info.
        Expected Result:
            The connection info retrieval should succeed and return a valid WifiInfo object.
        """
        wifi_test_utils.check_hotspot_device_supports_hotspot(self.dut2)
        self.dut.adb.shell(f"pm grant --user current {self._WIFI_SNIPPET_PACKAGE} {self._FINE_LOCATION_PERMISSION}")
        self.dut.adb.shell(f"pm grant --user current {self._WIFI_SNIPPET_PACKAGE} {self._BACKGROUND_LOCATION_PERMISSION}")
        logging.info("Granted FINE and BACKGROUND location permissions.")
        time.sleep(2) # Allow permissions to settle
        try:
            self._connect_dut_to_hotspot()
            wifi_info = self.dut.wifi.wifiGetConnectionInfoWithoutShellPermission()
            self.dut.log.info(f"wifiGetConnectionInfoWithoutShellPermission returned: {wifi_info["ssid"]}")
            asserts.assert_is_not_none(
                wifi_info,
                "wifiGetConnectionInfoWithoutShellPermission returned None, check logcat for SecurityException.")
        except Exception as e:
            asserts.fail(f"Test failed due to exception: {e}")
        finally:
            self.dut2.wifi.wifiStopLocalOnlyHotspot()

    def test_connection_info_retrieval_not_allow_with_fine_location_permission(self):
        """Verifies connection info retrieval returns redacted info with only fine location.
        For a background app, retrieving connection info without background location
        permission should return a redacted WifiInfo object (e.g., SSID=<unknown ssid>)
        rather than failing completely.
        Steps:
            1. Grant ACCESS_FINE_LOCATION permission.
            2. Revoke ACCESS_BACKGROUND_LOCATION permission.
            3. Connect to a Wi-Fi network.
            4. Attempt to retrieve Wi-Fi connection info.
        Expected Result:
            The call should succeed, but the returned WifiInfo object should contain
            redacted, location-identifying information.
        """
        wifi_test_utils.check_hotspot_device_supports_hotspot(self.dut2)
        self.dut.adb.shell(f"pm grant --user current {self._WIFI_SNIPPET_PACKAGE} {self._FINE_LOCATION_PERMISSION}")
        logging.info("Granted FINE location permissions.")
        time.sleep(2) # Allow permissions to settle
        try:
            self._connect_dut_to_hotspot()
            wifi_info = self.dut.wifi.wifiGetConnectionInfoWithoutShellPermission()
            self.dut.log.info(f"wifiGetConnectionInfoWithoutShellPermission returned: {wifi_info}")
            asserts.assert_is_none(
                wifi_info,
                "wifiGetConnectionInfoWithoutShellPermission returned None, check logcat for SecurityException.")
        except Exception as e:
            asserts.fail(f"Test failed due to exception: {e}")
        finally:
            self.dut2.wifi.wifiStopLocalOnlyHotspot()

    def test_transport_info_retrieval_allowed_with_background_location_permission(self):
        """Verifies transport info retrieval succeeds with background and fine location.
        Steps:
            1. Grant ACCESS_FINE_LOCATION and ACCESS_BACKGROUND_LOCATION permissions.
            2. Connect to a Wi-Fi network.
            3. Attempt to retrieve Wi-Fi transport info.
        Expected Result:
            The transport info retrieval should succeed and return a valid object.
        """
        wifi_test_utils.check_hotspot_device_supports_hotspot(self.dut2)
        self.dut.adb.shell(f"pm grant --user current {self._WIFI_SNIPPET_PACKAGE} {self._FINE_LOCATION_PERMISSION}")
        self.dut.adb.shell(f"pm grant --user current {self._WIFI_SNIPPET_PACKAGE} {self._BACKGROUND_LOCATION_PERMISSION}")
        logging.info("Granted FINE and BACKGROUND location permissions.")
        time.sleep(2) # Allow permissions to settle
        try:
            self._connect_dut_to_hotspot()
            tran_info = self.dut.wifi.wifiGetTransportInfo()
            self.dut.log.info(f"wifiGetTransportInfo returned: {tran_info}")
            asserts.assert_is_not_none(
                tran_info,
                "wifiGetTransportInfo returned None, check logcat for SecurityException.")
        except Exception as e:
            asserts.fail(f"Test failed due to exception: {e}")
        finally:
            self.dut2.wifi.wifiStopLocalOnlyHotspot()

    def test_transport_info_retrieval_not_allowed_with_fine_location_permission(self):
        """Verifies transport info retrieval fails with only fine location permission.
        Steps:
            1. Grant ACCESS_FINE_LOCATION permission.
            2. Revoke ACCESS_BACKGROUND_LOCATION permission.
            3. Connect to a Wi-Fi network.
            4. Attempt to retrieve Wi-Fi transport info.
        Expected Result:
            The transport info retrieval should fail and return None.
        """
        wifi_test_utils.check_hotspot_device_supports_hotspot(self.dut2)
        self.dut.adb.shell(f"pm grant --user current {self._WIFI_SNIPPET_PACKAGE} {self._FINE_LOCATION_PERMISSION}")
        logging.info("Granted FINE location permissions.")
        time.sleep(2) # Allow permissions to settle
        try:
            self._connect_dut_to_hotspot()
            tran_info = self.dut.wifi.wifiGetTransportInfo()
            self.dut.log.info(f"wifiGetTransportInfo returned: {tran_info}")
            asserts.assert_is_none(
                tran_info,
                "wifiGetTransportInfo returned None, check logcat for SecurityException.")
        except Exception as e:
            asserts.fail(f"Test failed due to exception: {e}")
        finally:
            self.dut2.wifi.wifiStopLocalOnlyHotspot()

if __name__ == '__main__':
    test_runner.main()
    # mobly_g3.main()