"""Wifi connection test suite."""

from mobly import base_suite
from mobly import suite_runner

from connection import network_request_tests
from connection import network_suggestion_tests


class WifiConnectionTestSuite(base_suite.BaseSuite):
  """CTS-V-Host WiFi connection test suite."""

  def setup_suite(self, config):
    config_with_reboot = config.copy()
    config_with_reboot.user_params['reboot_ap'] = 'true'

    self.add_test_class(
        config=config_with_reboot,
        clazz=network_request_tests.NetworkRequestTests,
    )
    self.add_test_class(
        clazz=network_suggestion_tests.NetworkSuggestionTests,
    )

if __name__ == '__main__':
  suite_runner.run_suite_class()

