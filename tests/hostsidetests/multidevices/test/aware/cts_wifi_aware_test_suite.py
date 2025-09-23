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
"""CTS Wi-Fi Aware test suite."""

import sys

from aware import wifi_aware_discovery_ranging_test
from aware import wifi_aware_network_test
from aware import wifi_aware_pairing_test
from mobly import base_suite
from mobly import suite_runner


class CtsWifiAwareTestSuite(base_suite.BaseSuite):
    """CTS Wi-Fi Aware test suite."""

    def setup_suite(self, config):
        del config  # unused
        self.add_test_class(wifi_aware_network_test.WifiAwareNetworkTest)
        self.add_test_class(
            wifi_aware_discovery_ranging_test.WifiAwareDiscoveryRangingTest
        )
        self.add_test_class(wifi_aware_pairing_test.WifiAwarePairingTest)


if __name__ == '__main__':
    # Take test args
    if '--' in sys.argv:
        index = sys.argv.index('--')
        sys.argv = sys.argv[:1] + sys.argv[index + 1 :]

    suite_runner.run_suite_class()
