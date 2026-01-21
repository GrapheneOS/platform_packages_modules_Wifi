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

"""CTS Wi-Fi Location Info Foreground tests."""

from android.platform.test.annotations import ApiTest
from mobly import asserts
from mobly import base_test
from mobly.controllers import android_device


@ApiTest(
    apis=[
        "android.Manifest.permission#ACCESS_FINE_LOCATION",
    ]
)
class WifiLocationInfoForegroundTest(base_test.BaseTestClass):
    _WIFI_SNIPPET_PACKAGE = "com.google.snippet.wifi"
    _FINE_LOCATION_PERMISSION = "android.permission.ACCESS_FINE_LOCATION"

    def setup_class(self) -> None:
        super().setup_class()
        self.android_devices = self.register_controller(android_device)
        if len(self.android_devices) < 2:
            asserts.skip("This test requires at least two Android devices.")
        self.dut = self.android_devices[0]
        self.hotspot_device = self.android_devices[1]
        self.dut.load_snippet("wifi", self._WIFI_SNIPPET_PACKAGE)
        self.hotspot_device.load_snippet("wifi", self._WIFI_SNIPPET_PACKAGE)
        # Ensure system-wide location services are enabled for consistent test results.
        self.dut.adb.shell("settings put secure location_mode 3")
        self.hotspot_device.adb.shell("settings put secure location_mode 3")

    def setup_test(self):
        self.dut.wifi.wifiToggleState(True)
        self.dut.adb.shell(f"pm revoke --user current {self._WIFI_SNIPPET_PACKAGE} {self._FINE_LOCATION_PERMISSION}")
        self.dut.unload_snippet("wifi")
        self.dut.load_snippet("wifi", self._WIFI_SNIPPET_PACKAGE)

    def teardown_test(self):
        self.dut.adb.shell(f"pm revoke --user current {self._WIFI_SNIPPET_PACKAGE} {self._FINE_LOCATION_PERMISSION}")
        self.dut.unload_snippet("wifi")
        self.dut.load_snippet("wifi", self._WIFI_SNIPPET_PACKAGE)
        self.hotspot_device.unload_snippet("wifi")
        self.hotspot_device.load_snippet("wifi", self._WIFI_SNIPPET_PACKAGE)

    def _start_local_only_hotspot_and_get_config(self):
        """Starts a local-only hotspot and returns the Wi-Fi configuration."""
        callback = self.hotspot_device.wifi.wifiStartLocalOnlyHotspot()
        on_started_event = callback.waitAndGet(event_name="onStarted", timeout=10)
        ssid = on_started_event.data['ssid']
        password = on_started_event.data['passphrase']
        return {"SSID": ssid, "password": password}

    def test_scan_trigger_not_allowed_for_foreground_activity_with_no_location_permission(self):
        """Verifies Wi-Fi scan fails without fine location permission.

        Steps:
            1. Ensure Wi-Fi is enabled.
            2. Revoke ACCESS_FINE_LOCATION permission for the snippet.
            3. Restart the snippet to apply permission changes.
            4. Attempt to trigger a Wi-Fi scan.

        Expected Result:
            The Wi-Fi scan attempt should fail (return False).
        """
        asserts.assert_false(
            self.dut.wifi.wifiStartScanAndGetStatus(),
            "Scan trigger should fail without location permission.")

    def test_scan_trigger_allowed_for_foreground_activity_with_fine_location_permission(self):
        """Verifies Wi-Fi scan succeeds with fine location permission.

        Steps:
            1. Grant ACCESS_FINE_LOCATION permission to the snippet.
            2. Attempt to trigger a Wi-Fi scan.

        Expected Result:
            The Wi-Fi scan attempt should succeed (return True).
        """
        self.dut.adb.shell(f"pm grant --user current {self._WIFI_SNIPPET_PACKAGE} {self._FINE_LOCATION_PERMISSION}")
        asserts.assert_true(
            self.dut.wifi.wifiStartScanAndGetStatus(),
            "Scan trigger should succeed with location permission.")

    def test_scan_results_retrieval_not_allowed_for_foreground_activity_with_no_location_permission(self):
        """Verifies scan results retrieval fails without fine location permission.

        Steps:
            1. Ensure ACCESS_FINE_LOCATION permission is revoked for the snippet.
            2. Attempt to retrieve Wi-Fi scan results.

        Expected Result:
            The scan results retrieval should fail (return False).
        """
        asserts.assert_false(
            self.dut.wifi.wifiGetScanResults(),
            "Scan results retrieval should fail without location permission.")

    def test_scan_results_retrieval_allowed_for_foreground_activity_with_fine_location_permission(self):
        """Verifies scan results retrieval succeeds with fine location permission.

        Steps:
            1. Grant ACCESS_FINE_LOCATION permission to the snippet.
            2. Attempt to retrieve Wi-Fi scan results.

        Expected Result:
            The scan results retrieval should succeed (return True).
        """
        self.dut.adb.shell(f"pm grant --user current {self._WIFI_SNIPPET_PACKAGE} {self._FINE_LOCATION_PERMISSION}")
        asserts.assert_true(
            self.dut.wifi.wifiGetScanResults(),
            "Scan results retrieval should succeed with location permission.")

    def test_connection_info_retrieval_not_allowed_for_foreground_activity_with_no_location_permission(self):
        """Verifies connection info retrieval fails without fine location permission.

        Steps:
            1. Start a Wi-Fi hotspot on the second device.
            2. Connect to the hotspot from the DUT.
            3. Ensure ACCESS_FINE_LOCATION permission is revoked for the snippet.
            4. Attempt to retrieve Wi-Fi connection info.

        Expected Result:
            The connection info retrieval should fail (return None).
        """
        try:
            wifi_config = self._start_local_only_hotspot_and_get_config()
            self.dut.wifi.wifiConnecting(wifi_config)
            asserts.assert_is_none(
                self.dut.wifi.wifiGetConnectionInfoWithoutShellPermission(),
                "Connection info retrieval should fail without location permission.")
        finally:
            self.hotspot_device.wifi.wifiStopLocalOnlyHotspot()

    def test_connection_info_retrieval_allowed_for_foreground_activity_with_fine_location_permission(self):
        """Verifies connection info retrieval succeeds with fine location permission.

        Steps:
            1. Start a Wi-Fi hotspot on the second device.
            2. Grant ACCESS_FINE_LOCATION permission to the snippet.
            3. Connect to the hotspot from the DUT.
            4. Attempt to retrieve Wi-Fi connection info.

        Expected Result:
            The connection info retrieval should succeed (return not None).
        """
        try:
            self.dut.adb.shell(f"pm grant --user current {self._WIFI_SNIPPET_PACKAGE} {self._FINE_LOCATION_PERMISSION}")
            wifi_config = self._start_local_only_hotspot_and_get_config()
            self.dut.wifi.wifiConnecting(wifi_config)
            asserts.assert_is_not_none(
                self.dut.wifi.wifiGetConnectionInfoWithoutShellPermission(),
                "Connection info retrieval should succeed with location permission.")
        finally:
            self.hotspot_device.wifi.wifiStopLocalOnlyHotspot()

    def test_transport_info_retrieval_not_allowed_for_foreground_activity_with_no_location_permission(self):
        """Verifies transport info retrieval fails without fine location permission.

        Steps:
            1. Ensure ACCESS_FINE_LOCATION permission is revoked for the snippet.
            2. Attempt to retrieve Wi-Fi transport info.

        Expected Result:
            The transport info retrieval should fail (return None).
        """
        try:
            wifi_config = self._start_local_only_hotspot_and_get_config()
            self.dut.wifi.wifiConnecting(wifi_config)
            asserts.assert_is_none(
                self.dut.wifi.wifiGetTransportInfo(),
                "Transport info retrieval should fail without location permission.")
        finally:
            self.hotspot_device.wifi.wifiStopLocalOnlyHotspot()


    def test_transport_info_retrieval_allowed_for_foreground_activity_with_fine_location_permission(self):
        """Verifies transport info retrieval succeeds with fine location permission.

        Steps:
            1. Grant ACCESS_FINE_LOCATION permission to the snippet.
            2. Start a Wi-Fi hotspot on the second device.
            3. Connect to the hotspot from the DUT.
            4. Attempt to retrieve Wi-Fi transport info.

        Expected Result:
            The transport info retrieval should succeed (return not None).
        """
        try:
            self.dut.adb.shell(f"pm grant --user current {self._WIFI_SNIPPET_PACKAGE} {self._FINE_LOCATION_PERMISSION}")
            wifi_config = self._start_local_only_hotspot_and_get_config()
            self.dut.wifi.wifiConnecting(wifi_config)
            asserts.assert_is_not_none(
                self.dut.wifi.wifiGetTransportInfo(),
                "Transport info retrieval should succeed with location permission.")
        finally:
            self.hotspot_device.wifi.wifiStopLocalOnlyHotspot()
