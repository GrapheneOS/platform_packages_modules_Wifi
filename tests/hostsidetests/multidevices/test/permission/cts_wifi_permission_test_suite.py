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

"""Wi-Fi Permission test suite."""

from mobly import base_suite
from mobly import suite_runner

import wifi_location_info_background_test
import wifi_location_info_foreground_test

class CtsWifiPermissionTests(base_suite.BaseSuite):
    """CTS Wi-Fi Permission test suite."""

    def setup_suite(self, config):
        del config  # unused
        self.add_test_class(
            wifi_location_info_foreground_test.WifiLocationInfoForegroundTest
        )
        self.add_test_class(
            wifi_location_info_background_test.WifiLocationInfoBackgroundTest
        )


if __name__ == '__main__':
    suite_runner.run_suite_class()
