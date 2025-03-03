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

# Lint as: python3
"""Wi-Fi Aware integration test suite in Mobly."""

import sys

from mobly import base_suite
from mobly import suite_runner
import wifi_aware_attached_test
import wifi_aware_capabilities_test
import wifi_aware_datapath_test
import wifi_aware_discovery_test
import wifi_aware_discovery_with_ranging_test
import wifi_aware_mac_random_test
import wifi_aware_matchfilter_test
import wifi_aware_message_test
import wifi_aware_protocols_multi_country_test
import wifi_aware_protocols_test


class WifiAwareIntegrationTestSuite(base_suite.BaseSuite):
  """Wi-Fi Aware integration test suite."""

  def setup_suite(self, config):
    del config  # Unused.
    self.add_test_class(wifi_aware_attached_test.WifiAwareAttachTest)
    self.add_test_class(wifi_aware_capabilities_test.WifiAwareCapabilitiesTest)
    self.add_test_class(wifi_aware_datapath_test.WifiAwareDatapathTest)
    self.add_test_class(wifi_aware_discovery_test.WifiAwareDiscoveryTest)
    self.add_test_class(
        wifi_aware_discovery_with_ranging_test.WiFiAwareDiscoveryWithRangingTest
    )
    self.add_test_class(wifi_aware_mac_random_test.MacRandomTest)
    self.add_test_class(wifi_aware_matchfilter_test.WifiAwareMatchFilterTest)
    self.add_test_class(wifi_aware_message_test.WifiAwareMessageTest)
    self.add_test_class(
        wifi_aware_protocols_multi_country_test.ProtocolsMultiCountryTest
    )
    self.add_test_class(wifi_aware_protocols_test.WifiAwareProtocolsTest)


if __name__ == '__main__':
  # Take test args
  if '--' in sys.argv:
    index = sys.argv.index('--')
    sys.argv = sys.argv[:1] + sys.argv[index + 1 :]

  suite_runner.run_suite_class()
