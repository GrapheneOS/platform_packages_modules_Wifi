import logging
from typing import Any, Optional, Sequence

from mobly import runtime_test_info
try:
    from mobly.controllers.wifi import openwrt_device
    from mobly.controllers.wifi.lib import wifi_configs as openwrt_lib_wifi_configs
except ImportError:
    openwrt_device = None
    openwrt_lib_wifi_configs = None

# Use alias for type annotation since we cannot import this module for some
# test invocations.
OpenWrtDevice = Any

_FREQ_2437_MHZ = 2437  # channel 6
_FREQ_5745_MHZ = 5745  # channel 149
_FREQ_2G_MIN_MHZ = 2412
_FREQ_2G_MAX_MHZ = 2484
_FREQ_5G_MIN_MHZ = 5160
_FREQ_5G_MAX_MHZ = 5885


class SnifferHelper:
  """A helper class for capturing packets in Wi-Fi tests.

  This class ensures that sniffer steps are automatically skipped when the tests
  are running in external environments, as the sniffer tool is only available
  to internal debugging today.
  """

  def __init__(self):
      self._sniffer = None

  @property
  def enabled(self) -> bool:
    """Whether this sniffer is enabled."""
    return self._sniffer is not None

  def register_controller_for_sniffer(self, test_class_obj) -> Optional['OpenWrtDevice']:
      """Registers the controller for sniffer if controller lib is available.

      Now we can only do sniffer when running the test internally. This will
      return the controller for the sniffer if the test can do sniffer. Otherwise
      this returns None.

      Args:
        test_class_obj: The test class object.

      Returns:
        The controller for sniffer if available. Otherwise None.
      """
      if openwrt_device is None:
          logging.warning(
              'Ignoring the packet capture logic since the OpenWrt lib cannot'
              ' be imported. Note that the sniffer feature is only supported'
              ' for internal debugging.'
          )
          return

      self._sniffer = test_class_obj.register_controller(openwrt_device)[0]

  def stop_packet_capture(
      self,
      current_test_info: runtime_test_info.RuntimeTestInfo | None = None,
  ) -> None:
      """Stops packet capture.

      Args:
          current_test_info: Use `self.current_test_info` for saving the
              captured packets. If set to None, the pcap file will be deleted.
      """
      if not self.enabled:
          return
      self._sniffer.stop_packet_capture(current_test_info=current_test_info)

  def start_packet_capture_for_aware_discovery(self) -> None:
      """Starts packet capture on the channels used by Aware discovery phase."""
      if not self.enabled:
          return
      # TODO: Instead of hard coding, we should get the social channels based on
      # the country code.
      self.start_packet_capture_on_frequencies(
          frequencies_mhz=[_FREQ_2437_MHZ, _FREQ_5745_MHZ]
      )

  def start_packet_capture_on_frequencies(
      self,
      frequencies_mhz: Sequence[int],
  ) -> None:
      """Starts packet capture on the specified frequencies.

      The sniffer supports simultaneous capture on one 2.4GHz and one 5GHz
      channel. If multiple frequencies are provided for the same band, only the
      first specified frequency for that band will be used; others will be
      ignored.

      Args:
          sniffer: The sniffer controller instance.
          frequencies_mhz: A list of frequencies (in MHz) to monitor.
      """
      if not self.enabled:
          return
      frequencies_2g = []
      frequencies_5g = []
      for freq in frequencies_mhz:
          if _FREQ_2G_MIN_MHZ <= freq <= _FREQ_2G_MAX_MHZ:
              frequencies_2g.append(freq)
          elif _FREQ_5G_MIN_MHZ <= freq <= _FREQ_5G_MAX_MHZ:
              frequencies_5g.append(freq)
          else:
              logging.warning('Got frequency by sniffer, ignoring: %d', freq)

      def _sniffer_on_first_freq(frequency_list, log_tag):
          if not frequency_list:
              return
          if len(frequency_list) > 1:
              logging.warning(
                  'Got a list of %s frequencies, will only sniffer on the first'
                  'frequency: %s', log_tag, frequencies_2g
              )
          freq_config = openwrt_lib_wifi_configs.FreqConfig.from_frequency(
              frequency_list[0]
          )
          self._sniffer.start_packet_capture(freq_config=freq_config)

      _sniffer_on_first_freq(frequencies_2g, log_tag='2G')
      _sniffer_on_first_freq(frequencies_5g, log_tag='5G')


