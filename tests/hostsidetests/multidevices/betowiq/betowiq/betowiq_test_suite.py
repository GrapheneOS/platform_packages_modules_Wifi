#  Copyright (C) 2026 The Android Open Source Project
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

"""Better Together Wi-Fi Quality test suite."""

import sys

from betowiq.aware import wifi_aware_datapath_test
from betowiq.aware import wifi_aware_discovery_test
from betowiq.aware import wifi_aware_latency_test
from betowiq.aware import wifi_aware_matchfilter_test
from betowiq.aware import wifi_aware_protocols_test
from betowiq.aware import wifi_aware_throughput_test
from betowiq.direct import wifi_p2p_group_test
from betowiq.direct import wifi_p2p_latency_test
from betowiq.direct import wifi_p2p_local_service_test
from betowiq.direct import wifi_p2p_manager_test
from betowiq.direct import wifi_p2p_throughput_test
from mobly import base_suite
from mobly import suite_runner


class BeToWiQTestSuite(base_suite.BaseSuite):
    """Better Together Wi-Fi Quality test suite."""

    def setup_suite(self, config):
        del config  # unused

        # Wi-Fi Aware tests
        self.add_test_class(wifi_aware_datapath_test.WifiAwareDatapathTest)
        self.add_test_class(
            wifi_aware_discovery_test.WifiAwareDiscoveryTest)
        self.add_test_class(wifi_aware_latency_test.WifiAwareLatencyTest)
        self.add_test_class(
            wifi_aware_matchfilter_test.WifiAwareMatchFilterTest)
        self.add_test_class(
            wifi_aware_protocols_test.WifiAwareProtocolsTest)
        self.add_test_class(
            wifi_aware_throughput_test.WifiAwareThroughputTest)

        # Wi-Fi P2P tests
        self.add_test_class(wifi_p2p_group_test.WifiP2pGroupTest)
        self.add_test_class(wifi_p2p_latency_test.WifiP2pLatencyTest)
        self.add_test_class(
            wifi_p2p_local_service_test.WifiP2pLocalServiceTest)
        self.add_test_class(wifi_p2p_manager_test.WifiP2pManagerTest)
        self.add_test_class(
            wifi_p2p_throughput_test.WifiP2pThroughputTest)


def main() -> None:
    """Entry point for execution as pip installed script."""
    suite_runner.run_suite_class()

if __name__ == '__main__':
    main()
