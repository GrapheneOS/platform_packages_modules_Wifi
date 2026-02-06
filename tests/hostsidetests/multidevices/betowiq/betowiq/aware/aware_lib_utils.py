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

"""Util for aware test."""

import base64
import datetime
import json
import logging
import os
import statistics
import time
from typing import Any, Callable, Dict, List, Optional
from queue import Empty


from betowiq.aware import constants

from mobly import asserts
from mobly.controllers import android_device
from mobly.controllers.android_device_lib import adb
from mobly.controllers.android_device_lib import callback_handler_v2
from mobly.snippet import callback_event
from mobly.snippet import errors


_WAIT_DOZE_MODE_IN_SEC = 5
_TIMEOUT_INTERVAL_IN_SEC = 1
_WAIT_WIFI_STATE_TIME_OUT = datetime.timedelta(seconds=10)
_WAIT_TIME_SEC = 3
_CONTROL_WIFI_TIMEOUT_SEC = 10
_REQUEST_NETWORK_TIMEOUT_MS = 15 * 1000
# arbitrary timeout for events
_EVENT_TIMEOUT = 10

# Alias variable.
_CALLBACK_NAME = constants.DiscoverySessionCallbackParamsType.CALLBACK_NAME
_DEFAULT_TIMEOUT = constants.WAIT_WIFI_STATE_TIME_OUT.total_seconds()
_TRANSPORT_TYPE_WIFI_AWARE = (
    constants.NetworkCapabilities.Transport.TRANSPORT_WIFI_AWARE
)
# Definition for timeout and retries.
_DEFAULT_TIMEOUT = constants.WAIT_WIFI_STATE_TIME_OUT.total_seconds()


def callback_no_response(
    callback: callback_handler_v2.CallbackHandlerV2,
    event_name: str,
    timeout: int = _WAIT_WIFI_STATE_TIME_OUT.total_seconds(),
    use_callbackid: bool = False,
    ):
  """Makes a callback call and expects no response within a given timeout.

  Args:
    callback: Snippet callback object.
    event_name: event name to wait.
    timeout: Timeout in second.
    use_callbackid: Using callbackid in eventname, default False.

  Raises:
    CallBackError: if receive response.
  """
  if use_callbackid:
    event_name += callback.callback_id
  try:
    data = callback.waitAndGet(event_name=event_name, timeout=timeout)
    raise CallBackError(f' Unexpected response {data}')
  except errors.CallbackHandlerTimeoutError:
    return


class CallBackError(Exception):
  """Error raised when there is a problem to get callback response."""

def control_wifi(
        ad: android_device.AndroidDevice,
        wifi_state: bool,
):
    """Control Android Wi-Fi status.

    Args:
      ad: Android test device.
      wifi_state: True if or Wi-Fi on False if Wi-Fi off.
      timeout_seconds: Maximum wait time (seconds), default is 10 seconds.

    Raises:
      TimeoutError: If the Wi-Fi state cannot be set within the timeout (in seconds).
    """
    if _check_wifi_status(ad) == wifi_state:
        return
    if wifi_state:
        ad.adb.shell("svc wifi enable")
    else:
        ad.adb.shell("svc wifi disable")
    start_time = time.time()
    while True:
        if _check_wifi_status(ad) == wifi_state:
            return
        # Check for timeout
        if time.time() - start_time > _CONTROL_WIFI_TIMEOUT_SEC:
            raise TimeoutError(
                f"Failed to set Wi-Fi state to {wifi_state} within {_CONTROL_WIFI_TIMEOUT_SEC} seconds."
            )

        time.sleep(1)  # Wait for a second before checking again

def _check_wifi_status(ad: android_device.AndroidDevice):
  """Check Android Wi-Fi status.

  Args:
      ad: android device object.

  Returns:
    True if wifi on, False if wifi off.
  """
  cmd = ad.adb.shell("cmd wifi status").decode("utf-8").strip()
  first_line = cmd.split("\n")[0]
  logging.info("device wifi status: %s", first_line)
  if "enabled" in first_line:
    return True
  else:
    return False


def set_doze_mode(ad: android_device.AndroidDevice, state: bool) -> bool:
  """Enables/Disables Android doze mode.

  Args:
      ad: android device object.
      state: bool, True if intent to enable Android doze mode, False otherwise.

  Returns:
    True if doze mode is enabled, False otherwise.

  Raises:
    TimeoutError: If timeout is hit.
  """
  if state:
    ad.log.info("Enables Android doze mode")
    _dumpsys(ad, "battery unplug")
    _dumpsys(ad, "deviceidle enable")
    _dumpsys(ad, "deviceidle force-idle")
    time.sleep(_WAIT_DOZE_MODE_IN_SEC)
  else:
    ad.log.info("Disables Android doze mode")
    _dumpsys(ad, "deviceidle disable")
    _dumpsys(ad, "battery reset")
  for _ in range(10 + 1):
    adb_shell_result = _dumpsys(ad, "deviceidle get deep")
    logging.info("dumpsys deviceidle get deep: %s", adb_shell_result)
    if adb_shell_result.startswith(constants.DeviceidleState.IDLE.value):
      return True
    if adb_shell_result.startswith(constants.DeviceidleState.ACTIVE.value):
      return False
    time.sleep(_TIMEOUT_INTERVAL_IN_SEC)
  # At this point, timeout must have occurred.
  raise errors.CallbackHandlerTimeoutError(
      ad, "Timed out after waiting for doze_mode set to {state}"
  )


def _dumpsys(ad: android_device.AndroidDevice, command: str) -> str:
  """Dumpsys device info.

  Args:
      ad: android device object.
      command: adb command.

  Returns:
    Android dumsys info
  """
  return ad.adb.shell(f"dumpsys {command}").decode().strip()


def check_android_os_version(
    ad: android_device.AndroidDevice,
    operator_func: Callable[[Any, Any], bool],
    android_version: constants.AndroidVersion,
    ) -> bool:
  """Compares device's Android OS version with the given one.

  Args:
    ad: Android devices.
    operator_func: Operator used in the comparison.
    android_version: The given Android OS version.

  Returns:
    bool: The comparison result.
  """
  device_os_version = int(ad.adb.shell("getprop ro.build.version.release"))
  result = False
  if isinstance(operator_func, constants.Operator):
    return operator_func.value(device_os_version, android_version)
  return result


def _get_airplane_mode(ad: android_device.AndroidDevice) -> bool:
  """Gets the airplane mode.

  Args:
    ad: android device object.

  Returns:
    True if airplane mode On, False for Off.
  """
  state = ad.adb.shell("settings get global airplane_mode_on")
  return bool(int(state))


def set_airplane_mode(ad: android_device.AndroidDevice, state: bool):
  """Sets the airplane mode to the given state.

  Args:
    ad: android device object.
    state: bool, True for Airplane mode on, False for off.
  """
  ad.adb.shell(
      ["settings", "put", "global", "airplane_mode_on", str(int(state))]
  )
  ad.adb.shell([
      "am",
      "broadcast",
      "-a",
      "android.intent.action.AIRPLANE_MODE",
      "--ez",
      "state",
      str(state),
  ])
  start_time = time.time()
  while _get_airplane_mode(ad) != state:
    time.sleep(_TIMEOUT_INTERVAL_IN_SEC)
    asserts.assert_greater(
        time.time() - start_time > _WAIT_TIME_SEC,
        f"Failed to set airplane mode to: {state}",
    )


def decode_list(list_of_b64_strings: List[str]) -> List[bytes]:
  """Converts the list of b64 encoded strings to a list of bytearray.

  Args:
    list_of_b64_strings: A list of strings, each of which is b64 encoded array.

  Returns:
    A list of bytearrays.
  """
  decoded_list = []
  for string_item in list_of_b64_strings:
    decoded_list.append(base64.b64decode(string_item))
  return decoded_list


def encode_list(
    list_of_objects: List[Any]) -> List[str]:
  """Converts a list of strings/bytearrays to a list of b64 encoded bytearrays.

  A None object is treated as a zero-length bytearray.

  Args:
    list_of_objects: A list of strings or bytearray objects.
  Returns:
    A list of the same objects, converted to bytes and b64 encoded.
  """
  encoded_list = []
  for obj in list_of_objects:
    if obj is None:
      obj = bytes()
    if isinstance(obj, str):
      encoded_list.append(base64.b64encode(bytes(obj, "utf-8")).decode("utf-8"))
    else:
      encoded_list.append(base64.b64encode(bytes(obj)).decode("utf-8"))
  return encoded_list


def construct_max_match_filter(max_size: int)-> List[bytes]:
  """Constructs a maximum size match filter that fits into the 'max_size' bytes.

  Match filters are a set of LVs (Length, Value pairs) where L is 1 byte. The
  maximum size match filter will contain max_size/2 LVs with all Vs (except
  possibly the last one) of 1 byte, the last V may be 2 bytes for odd max_size.

  Args:
    max_size: Maximum size of the match filter.
  Returns:
    A list of bytearrays.
  """
  mf_list = []
  num_lvs = max_size // 2
  for i in range(num_lvs - 1):
    mf_list.append(bytes([i]))
  if max_size % 2 == 0:
    mf_list.append(bytes([255]))
  else:
    mf_list.append(bytes([254, 255]))
  return mf_list


def validate_forbidden_callbacks(ad: android_device.AndroidDevice,
                                 limited_cb: Optional[Dict[str, int]] = None
                                ) -> None:
  """Validate the specified callbacks have not been called more than permitted.

  In addition to the input configuration also validates that forbidden callbacks
  have never been called.

  Args:
    ad: Device on which to run.
    limited_cb: Dictionary of CB_EV_* ids and maximum permitted calls (0
                meaning never).
  Raises:
    CallBackError: If forbidden callbacks are triggered.
  """
  cb_data = json.loads(ad.adb.shell("cmd wifiaware native_cb get_cb_count"))
  if limited_cb is None:
    limited_cb = {}
  # Add callbacks which should never be called.
  limited_cb["5"] = 0
  fail = False
  for cb_event in limited_cb.keys():
    if cb_event in cb_data:
      if cb_data[cb_event] > limited_cb[cb_event]:
        fail = True
        ad.log.info(
            "Callback %s observed %d times: more than permitted %d times",
            cb_event, cb_data[cb_event], limited_cb[cb_event])
        break
  if fail:
    raise CallBackError("Forbidden callbacks observed.")


def reset_device_parameters(ad: android_device.AndroidDevice):
  """Reset device configurations which may have been set by tests.
  Should be done before tests start (in case previous one was killed
  without tearing down) and after they end (to leave device in usable
  state).

  Args:
    ad: device to be reset
  """
  ad.adb.shell("cmd wifiaware reset")

def aware_cap_str_to_dict(cap_string:str) -> dict:
    idx = cap_string.find('[maxConcurrentAwareClusters')
    # Remove the braces from the string.
    new_string = cap_string[idx:-1].strip('[]')
    # split the string into key-value pairs
    pairs = new_string.split(', ')
    # Converting the values to integer or bool into dictionary
    capabilities = {}
    for pair in pairs:
      key, value = pair.split('=')
      try:
          capabilities[key] = int(value)
      except ValueError:
          capabilities[key] = bool(value)
    return capabilities


def reset_device_statistics(ad: android_device.AndroidDevice,):
  """Reset device statistics.

  Args:
    ad: device to be reset
  """
  ad.adb.shell("cmd wifiaware native_cb get_cb_count --reset")

def get_aware_capabilities(ad: android_device.AndroidDevice):
    """Get the Wi-Fi Aware capabilities from the specified device. The
  capabilities are a dictionary keyed by aware_const.CAP_* keys.

  Args:
    ad: the Android device
  Returns: the capability dictionary.
  """
    try:
      result = ad.adb.shell('cmd wifiaware state_mgr get_capabilities')
      return json.loads(result)
    except adb.AdbError:
      ad.log.info('Another way to get capabilities- dumpsys and parse string.')
      result = ad.adb.shell('dumpsys wifiaware |grep mCapabilities').decode()
      pairs = aware_cap_str_to_dict(result)
      ad.log.info(pairs)
    return pairs


def create_discovery_config(service_name,
                            p_type=None,
                            s_type=None,
                            ssi=None,
                            match_filter=None,
                            match_filter_list=None,
                            ttl=0,
                            term_cb_enable=True,
                            instant_mode=None):
    """Create a publish discovery configuration based on input parameters.

    Args:
        service_name: Service name - required
        d_type: Discovery type (publish or subscribe constants)
        ssi: Supplemental information - defaults to None
        match_filter, match_filter_list: The match_filter, only one mechanism can
                                     be used to specify. Defaults to None.
        ttl: Time-to-live - defaults to 0 (i.e. non-self terminating)
        term_cb_enable: True (default) to enable callback on termination, False
                      means that no callback is called when session terminates.
        instant_mode: set the band to use instant communication mode, 2G or 5G
    Returns:
        publish discovery configuration object.
    """
    config = {}
    config[constants.SERVICE_NAME] = service_name
    if p_type is not None:
      config[constants.PUBLISH_TYPE] = p_type
    if s_type is not None:
      config[constants.SUBSCRIBE_TYPE] = s_type
    if ssi is not None:
        config[constants.SERVICE_SPECIFIC_INFO] = ssi
    if match_filter is not None:
        config[constants.MATCH_FILTER] = match_filter
    if match_filter_list is not None:
        config[constants.MATCH_FILTER_LIST] = match_filter_list
    if instant_mode is not None:
        config[constants.INSTANTMODE_ENABLE] = instant_mode
    config[constants.TTL_SEC] = ttl
    config[constants.TERMINATE_NOTIFICATION_ENABLED] = term_cb_enable
    return config

def start_attach(
    ad: android_device.AndroidDevice,
    is_ranging_enabled: bool = True,
) -> str:
  """Starts the attach process on the provided device."""
  attach_handler = ad.wifi_aware_snippet.wifiAwareAttached(
      is_ranging_enabled
  )
  attach_event = attach_handler.waitAndGet(
      event_name=constants.AttachCallBackMethodType.ATTACHED,
      timeout=_DEFAULT_TIMEOUT,
  )
  asserts.assert_true(
      ad.wifi_aware_snippet.wifiAwareIsSessionAttached(
          attach_event.callback_id
      ),
      f'{ad} attach succeeded, but Wi-Fi Aware session is still null.',
  )
  mac_address = None
  if is_ranging_enabled:
    identity_changed_event = attach_handler.waitAndGet(
        event_name=constants.AttachCallBackMethodType.ID_CHANGED,
        timeout=_DEFAULT_TIMEOUT,
    )
    mac_address = identity_changed_event.data.get('mac', None)
    asserts.assert_true(bool(mac_address), 'Mac address should not be empty')
  ad.log.info('Attach Wi-Fi Aware session succeeded.')
  return attach_event.callback_id, mac_address


def create_discovery_pair(
    p_dut: android_device.AndroidDevice,
    s_dut: android_device.AndroidDevice,
    p_config: dict[str, any],
    s_config: dict[str, any],
    device_startup_delay: int=1,
    msg_id=None,
):
  """Creates a discovery session (publish and subscribe), and pair each other.

  wait for service discovery - at that point the sessions are connected and
  ready for further messaging of data-path setup.

  Args:
      p_dut: Device to use as publisher.
      s_dut: Device to use as subscriber.
      p_config: Publish configuration.
      s_config: Subscribe configuration.
      device_startup_delay: Number of seconds to offset the enabling of NAN
        on the two devices.
      msg_id: Controls whether a message is sent from Subscriber to Publisher
        (so that publisher has the sub's peer ID). If None then not sent,
        otherwise should be an int for the message id.

  Returns:
      variable size list of:
      p_id: Publisher attach session id
      s_id: Subscriber attach session id
      p_disc_id: Publisher discovery session id
      s_disc_id: Subscriber discovery session id
      peer_id_on_sub: Peer ID of the Publisher as seen on the Subscriber
      peer_id_on_pub: Peer ID of the Subscriber as seen on the Publisher. Only
                      included if |msg_id| is not None.
  """
  # attach and wait for confirmation
  p_id, _ = start_attach(p_dut)
  time.sleep(device_startup_delay)
  s_id, _ = start_attach(s_dut)
  p_disc_id = p_dut.wifi_aware_snippet.wifiAwarePublish(
      p_id, p_config
      )
  p_dut.log.info('Created the publish session.')
  p_discovery = p_disc_id.waitAndGet(
      constants.DiscoverySessionCallbackMethodType.DISCOVER_RESULT)
  callback_name = p_discovery.data[_CALLBACK_NAME]
  asserts.assert_equal(
      constants.DiscoverySessionCallbackMethodType.PUBLISH_STARTED,
      callback_name,
      f'{p_dut} publish failed, got callback: {callback_name}.',
      )
  time.sleep(device_startup_delay)
  # Subscriber: start subscribe and wait for confirmation
  s_disc_id = s_dut.wifi_aware_snippet.wifiAwareSubscribe(
      s_id, s_config
      )
  s_dut.log.info('Created the subscribe session.')
  s_discovery = s_disc_id.waitAndGet(
      constants.DiscoverySessionCallbackMethodType.DISCOVER_RESULT)
  callback_name = s_discovery.data[_CALLBACK_NAME]
  asserts.assert_equal(
      constants.DiscoverySessionCallbackMethodType.SUBSCRIBE_STARTED,
      callback_name,
      f'{s_dut} subscribe failed, got callback: {callback_name}.',
      )
  # Subscriber: wait for service discovery
  discovery_event = s_disc_id.waitAndGet(
      constants.DiscoverySessionCallbackMethodType.SERVICE_DISCOVERED)
  peer_id_on_sub = discovery_event.data[
      constants.WifiAwareSnippetParams.PEER_ID
  ]
  # Optionally send a message from Subscriber to Publisher
  if msg_id is not None:
    ping_msg = 'PING'
    # Subscriber: send message to peer (Publisher)
    s_dut.wifi_aware_snippet.wifiAwareSendMessage(
        s_disc_id.callback_id, peer_id_on_sub, msg_id, ping_msg
        )
    message_send_result = s_disc_id.waitAndGet(
        event_name=
        constants.DiscoverySessionCallbackMethodType.MESSAGE_SEND_RESULT,
        timeout=_DEFAULT_TIMEOUT,
        )
    actual_send_message_id = message_send_result.data[
        constants.DiscoverySessionCallbackParamsType.MESSAGE_ID
        ]
    asserts.assert_equal(
        actual_send_message_id,
        msg_id,
        f'{s_dut} send message succeeded but message ID mismatched.'
        )
    pub_rx_msg_event = p_disc_id.waitAndGet(
        event_name=
        constants.DiscoverySessionCallbackMethodType.MESSAGE_RECEIVED,
        timeout=_DEFAULT_TIMEOUT,
        )
    peer_id_on_pub = pub_rx_msg_event.data[
        constants.WifiAwareSnippetParams.PEER_ID
        ]
    received_message_raw = pub_rx_msg_event.data[
        constants.WifiAwareSnippetParams.RECEIVED_MESSAGE
        ]
    received_message = bytes(received_message_raw).decode('utf-8')
    asserts.assert_equal(
        received_message,
        ping_msg,
        f'{p_dut} Subscriber -> Publisher message corrupted.'
        )
    return p_id, s_id, p_disc_id, s_disc_id, peer_id_on_sub, peer_id_on_pub
  return p_id, s_id, p_disc_id, s_disc_id, peer_id_on_sub


def request_network(
    ad: android_device.AndroidDevice,
    discovery_session: str,
    peer: int,
    net_work_request_id: str,
    network_specifier_params: (
        constants.WifiAwareNetworkSpecifier | None
    ) = None,
    is_accept_any_peer: bool = False,
) -> callback_handler_v2.CallbackHandlerV2:
  """Requests and configures a Wi-Fi Aware network connection."""
  network_specifier_parcel = (
      ad.wifi_aware_snippet.wifiAwareCreateNetworkSpecifier(
          discovery_session,
          peer,
          is_accept_any_peer,
          network_specifier_params.to_dict()
          if network_specifier_params
          else None,
      )
  )
  network_request_dict = constants.NetworkRequest(
      transport_type=_TRANSPORT_TYPE_WIFI_AWARE,
      network_specifier_parcel=network_specifier_parcel,
  ).to_dict()
  ad.log.debug('Requesting Wi-Fi Aware network: %r', network_request_dict)
  return ad.wifi_aware_snippet.connectivityRequestNetwork(
      net_work_request_id, network_request_dict, _REQUEST_NETWORK_TIMEOUT_MS
  )

def wait_for_networks(
    ad: android_device.AndroidDevice,
    request_network_cb_handler: callback_handler_v2.CallbackHandlerV2,
    expected_channel: str | None = None,
) -> callback_event.CallbackEvent:
  """Waits for and verifies the establishment of a Wi-Fi Aware network."""
  network_callback_event = request_network_cb_handler.waitAndGet(
      event_name=constants.NetworkCbEventName.NETWORK_CALLBACK,
      timeout=_DEFAULT_TIMEOUT,
  )
  callback_name = network_callback_event.data[_CALLBACK_NAME]
  if callback_name == constants.NetworkCbName.ON_UNAVAILABLE:
    asserts.fail(
        f'{ad} failed to request the network, got callback {callback_name}.'
    )
  elif callback_name == constants.NetworkCbName.ON_CAPABILITIES_CHANGED:
    # `network` is the network whose capabilities have changed.
    network = network_callback_event.data[constants.NetworkCbEventKey.NETWORK]
    network_capabilities = network_callback_event.data[
        constants.NetworkCbEventKey.NETWORK_CAPABILITIES
    ]
    asserts.assert_true(
        network and network_capabilities,
        f'{ad} received a null Network or NetworkCapabilities!?.',
    )
    transport_info_class_name = network_callback_event.data[
        constants.NetworkCbEventKey.TRANSPORT_INFO_CLASS_NAME
    ]
    ad.log.info(f'got class_name {transport_info_class_name}')
    asserts.assert_equal(
        transport_info_class_name,
        constants.AWARE_NETWORK_INFO_CLASS_NAME,
        f'{ad} network capabilities changes but it is not a WiFi Aware'
        ' network.',
    )
    if expected_channel:
      mhz_list = network_callback_event.data[
          constants.NetworkCbEventKey.CHANNEL_IN_MHZ
      ]
      asserts.assert_equal(
          mhz_list,
          [expected_channel],
          f'{ad} Channel freq is not match the request.',
      )
  elif callback_name == constants.NetworkCbName.ON_PROPERTIES_CHANGED:
    iface_name = network_callback_event.data[
        constants.NetworkCbEventKey.NETWORK_INTERFACE_NAME
    ]
    ad.log.info('interface name = %s', iface_name)
  else:
    asserts.fail(
        f'{ad} got unknown request network callback {callback_name}.'
    )
  return network_callback_event


def wait_for_network(
        ad: android_device.AndroidDevice,
        request_network_cb_handler: callback_handler_v2.CallbackHandlerV2,
        expected_channel: str | None = None,
    ) -> callback_event.CallbackEvent:
    """
    Waits for Wi-Fi Aware network events, attempting up to 3 times.

    This function collects all available network callbacks within the attempts
    and then searches for a definitive success or failure event to verify.
    """
    collected_events = []
    # Loop a maximum of 2 times to collect events.
    for attempt in range(2):
        try:
            event = request_network_cb_handler.waitAndGet(
                event_name=constants.NetworkCbEventName.NETWORK_CALLBACK,
                timeout=_DEFAULT_TIMEOUT,
            )
            ad.log.info(f'Attempt {attempt + 1}: Collected event'
                        f' {event.data.get(_CALLBACK_NAME)}')
            collected_events.append(event)
        except Empty:
            ad.log.info(f'Attempt {attempt + 1}: No event received in time.'
                        ' Stopping collection.')
            # If the queue is empty, no need to try again.
            break
        except Exception as e:
            asserts.fail(f'An unexpected error occurred while waiting for'
                         f' event: {e}')

    # After collecting events, process them to find the one we need.
    success_event = None
    for event in collected_events:
        callback_name = event.data.get(_CALLBACK_NAME)

        if callback_name == constants.NetworkCbName.ON_UNAVAILABLE:
            # A definitive failure event was found. Fail immediately.
            asserts.fail(
                f'{ad} failed to request the network. Received ON_UNAVAILABLE.'
            )
        elif callback_name == constants.NetworkCbName.ON_CAPABILITIES_CHANGED:
            # This is the primary success event. Store it and stop searching.
            success_event = event
            break

    # After checking all collected events, verify the success event.
    if success_event:
        # `network` is the network whose capabilities have changed.
        network = success_event.data[constants.NetworkCbEventKey.NETWORK]
        network_capabilities = success_event.data[
            constants.NetworkCbEventKey.NETWORK_CAPABILITIES
        ]
        asserts.assert_true(
            network and network_capabilities,
            f'{ad} received a null Network or NetworkCapabilities!?.',
            )
        transport_info_class_name = success_event.data[
            constants.NetworkCbEventKey.TRANSPORT_INFO_CLASS_NAME
        ]
        ad.log.info(f'Got class_name {transport_info_class_name}')
        asserts.assert_equal(
            transport_info_class_name,
            constants.AWARE_NETWORK_INFO_CLASS_NAME,
            f'{ad} network capabilities changed but it is not a WiFi Aware'
            ' network.',
        )
        if expected_channel:
            mhz_list = success_event.data[
                constants.NetworkCbEventKey.CHANNEL_IN_MHZ
            ]
            asserts.assert_equal(
                mhz_list,
                [expected_channel],
                f'{ad} Channel freq does not match the request.',
            )
        # Return the successfully validated event.
        return success_event
    else:
        # If the loop finishes and no success_event was found, fail.
        event_names = [e.data.get(_CALLBACK_NAME) for e in collected_events]
        asserts.fail(
            f'{ad} did not receive ON_CAPABILITIES_CHANGED after all attempts.'
            f' Got events: {event_names}'
        )

def wait_for_link(
        ad: android_device.AndroidDevice,
        request_network_cb_handler: callback_handler_v2.CallbackHandlerV2,
    ) -> callback_event.CallbackEvent:
    """
    Waits for and verifies the establishment of a Wi-Fi Aware network,
    attempting to fetch events up to 3 times.
    """
    collected_events = []
    # Loop a maximum of 3 times to collect any available events.
    for attempt in range(2):
        try:
            event = request_network_cb_handler.waitAndGet(
                event_name=constants.NetworkCbEventName.NETWORK_CALLBACK,
                timeout=_DEFAULT_TIMEOUT,
            )
            ad.log.info(
                f'Attempt {attempt + 1}: Collected event'
                f' {event.data.get(_CALLBACK_NAME)}'
            )
            collected_events.append(event)
        except Empty:
            ad.log.info(
                f'Attempt {attempt + 1}: No more events in the queue. '
                'Stopping collection.'
            )
            # If the queue is empty, there's no need to try again.
            break
        except Exception as e:
            asserts.fail(
                f'An unexpected error occurred while waiting for event: {e}'
            )

    # After collecting events, process them to find the one we need.
    success_event = None
    for event in collected_events:
        # Use .get() for safe access in case the key is missing.
        callback_name = event.data.get(_CALLBACK_NAME)

        if callback_name == constants.NetworkCbName.ON_UNAVAILABLE:
            # A definitive failure event was found. Fail the test immediately.
            asserts.fail(
                f'{ad} failed to request the network, received ON_UNAVAILABLE.'
            )
        elif callback_name == constants.NetworkCbName.ON_PROPERTIES_CHANGED:
            # This is the success event we are looking for.
            success_event = event
            # We found the event, no need to check the rest of the list.
            break

    # After checking all collected events, verify the outcome.
    if success_event:
        # If we found the success event, log its details and return it.
        iface_name = success_event.data[
            constants.NetworkCbEventKey.NETWORK_INTERFACE_NAME
        ]
        ad.log.info('Successfully found link. Interface name = %s', iface_name)
        ad.log.info('type = %s', type(success_event))
        return success_event
    else:
        # If the loop finishes and no success_event was found, fail the test.
        event_names = [e.data.get(_CALLBACK_NAME) for e in collected_events]
        asserts.fail(
            f'{ad} did not receive ON_PROPERTIES_CHANGED after all attempts. '
            f'Got events: {event_names}'
        )

def _wait_accept_success(
    pub_accept_handler: callback_handler_v2.CallbackHandlerV2
) -> None:
    pub_accept_event = pub_accept_handler.waitAndGet(
        event_name=constants.SnippetEventNames.SERVER_SOCKET_ACCEPT,
        timeout=_DEFAULT_TIMEOUT
    )
    is_accept = pub_accept_event.data.get(constants.SnippetEventParams.IS_ACCEPT, False)
    if not is_accept:
        error = pub_accept_event.data[constants.SnippetEventParams.ERROR]
        asserts.fail(
            f'Publisher failed to accept the connection. Error: {error}'
        )

def _send_socket_msg(
    sender_ad: android_device.AndroidDevice,
    receiver_ad: android_device.AndroidDevice,
    msg: str,
    send_callback_id: str,
    receiver_callback_id: str,
):
    """Sends a message from one device to another and verifies receipt."""
    is_write_socket = sender_ad.wifi_aware_snippet.connectivityWriteSocket(
        send_callback_id, msg
    )
    asserts.assert_true(
        is_write_socket,
        f'{sender_ad} Failed to write data to the socket.'
    )
    sender_ad.log.info('Wrote data to the socket.')
    # Verify received message
    received_message = receiver_ad.wifi_aware_snippet.connectivityReadSocket(
        receiver_callback_id, len(msg)
    )
    asserts.assert_equal(
        received_message,
        msg,
        f'{receiver_ad} received message mismatched.Failure:Expected {msg} but got '
        f'{received_message}.'
    )
    receiver_ad.log.info('Read data from the socket.')

def establish_socket_and_send_msg(
    publisher: android_device.AndroidDevice,
    subscriber: android_device.AndroidDevice,
    pub_accept_handler: callback_handler_v2.CallbackHandlerV2,
    network_id: str,
    pub_local_port: int
):
    """Handles socket-based communication between publisher and subscriber."""
    # Init socket
    # Create a ServerSocket and makes it listen for client connections.
    subscriber.wifi_aware_snippet.connectivityCreateSocketOverWiFiAware(
        network_id, pub_local_port
    )
    _wait_accept_success(pub_accept_handler)
    # Subscriber Send socket data
    subscriber.log.info('Subscriber create a socket.')
    _send_socket_msg(
        sender_ad=subscriber,
        receiver_ad=publisher,
        msg=constants.WifiAwareTestConstants.MSG_CLIENT_TO_SERVER,
        send_callback_id=network_id,
        receiver_callback_id=network_id
    )
    _send_socket_msg(
        sender_ad=publisher,
        receiver_ad=subscriber,
        msg=constants.WifiAwareTestConstants.MSG_SERVER_TO_CLIENT,
        send_callback_id=network_id,
        receiver_callback_id=network_id
    )
    publisher.wifi_aware_snippet.connectivityCloseWrite(network_id)
    subscriber.wifi_aware_snippet.connectivityCloseWrite(network_id)
    publisher.wifi_aware_snippet.connectivityCloseRead(network_id)
    subscriber.wifi_aware_snippet.connectivityCloseRead(network_id)
    logging.info('Communicated through socket connection of Wi-Fi Aware network successfully.')


def run_ping6(dut: android_device.AndroidDevice, peer_ipv6: str):
    """Run a ping6 over the specified device/link.

    Args:
       dut: Device on which to execute ping6.
       peer_ipv6: Scoped IPv6 address of the peer to ping.
    """
    cmd = 'ping6 -c 3 -W 5 %s' % peer_ipv6
    try:
        dut.log.info(cmd)
        results = dut.adb.shell(cmd)
    except adb.AdbError:
        time.sleep(1)
        dut.log.info('CMD RETRY: %s', cmd)
        results = dut.adb.shell(cmd)
    dut.log.info("cmd='%s' -> '%s'", cmd, results)
    if not results:
        asserts.fail("ping6 empty results - seems like a failure")
    return results

def iperf_server(ad,  extra_args=""):
    """
    Starts an iperf3 server and checks the immediate output for errors.

    NOTE: This is a blocking call and will hang if the server starts
    successfully. It only proceeds if the command fails instantly.
    """
    out = ad.adb.shell("iperf3 -s {}".format(extra_args))
    clean_out = str(out, 'utf-8').strip().split('\n')
    if "error" in clean_out:
        return False, clean_out
    return True, clean_out

def configure_power_setting(dut, mode, name, value):
    """Use the command-line API to configure the power setting

    Args:
        dut: Device on which to perform configuration
        mode: The power mode being set, should be "default", "inactive", or "idle"
        name: One of the power settings from 'wifiaware set-power'.
        value: An integer.
    """
    dut.adb.shell("cmd wifiaware native_api set-power %s %s %d" % (mode, name, value))


def config_power_settings(dut,
                          dw_24ghz,
                          dw_5ghz,
                          disc_beacon_interval=None,
                          num_ss_in_disc=None,
                          enable_dw_early_term=None):
    """Configure device's discovery window (DW) values to the specified values -
    whether the device is in interactive or non-interactive mode.

    Args:
        dw_24ghz: DW interval in the 2.4GHz band.
        dw_5ghz: DW interval in the 5GHz band.
        disc_beacon_interval: The discovery beacon interval (in ms). If None then
                          not set.
        num_ss_in_disc: Number of spatial streams to use for discovery. If None then
                    not set.
    enable_dw_early_term: If True then enable early termination of the DW. If
                          None then not set.
    """
    configure_power_setting(dut, "default", "dw_24ghz", dw_24ghz)
    configure_power_setting(dut, "default", "dw_5ghz", dw_5ghz)
    configure_power_setting(dut, "inactive", "dw_24ghz", dw_24ghz)
    configure_power_setting(dut, "inactive", "dw_5ghz", dw_5ghz)

    if disc_beacon_interval is not None:
        configure_power_setting(dut, "default", "disc_beacon_interval_ms",
                                disc_beacon_interval)
        configure_power_setting(dut, "inactive", "disc_beacon_interval_ms",
                                disc_beacon_interval)

    if num_ss_in_disc is not None:
        configure_power_setting(dut, "default", "num_ss_in_discovery",
                                num_ss_in_disc)
        configure_power_setting(dut, "inactive", "num_ss_in_discovery",
                                num_ss_in_disc)

    if enable_dw_early_term is not None:
        configure_power_setting(dut, "default", "enable_dw_early_term",
                                enable_dw_early_term)
        configure_power_setting(dut, "inactive", "enable_dw_early_term",
                                enable_dw_early_term)

def extract_stats(ad, data, results, key_prefix, log_prefix, csv_filepath=None):
    num_samples = len(data)
    results[f'{key_prefix}num_samples'] = num_samples
    if not data:
        return
    data_min = min(data)
    data_max = max(data)
    data_mean = statistics.mean(data)
    data_cdf = extract_cdf(data)
    data_cdf_decile = extract_cdf_decile(data_cdf)

    # --- Populate the results dictionary ---
    results[f'{key_prefix}min'] = data_min
    results[f'{key_prefix}max'] = data_max
    results[f'{key_prefix}mean'] = data_mean
    results[f'{key_prefix}cdf'] = data_cdf
    results[f'{key_prefix}cdf_decile'] = data_cdf_decile
    results[f'{key_prefix}raw_data'] = data
    # --- Build the log string and handle CSV output ---
    log_message = ""
    csv_header = "log_message" # A simple header for our single-column CSV

    if num_samples > 1:
        data_stdev = statistics.stdev(data)
        results[f'{key_prefix}stdev'] = data_stdev
        # Format the string that will be used for both logging and the CSV
        log_message = (
            f'{log_prefix}: num_samples={num_samples}, min={data_min:.2f}, '
            f'max={data_max:.2f}, mean={data_mean:.2f}, stdev={data_stdev:.2f}, '
            f'cdf_decile={data_cdf_decile}'
        )
    else:
        log_message = (
            f'{log_prefix}: num_samples={num_samples}, min={data_min:.2f}, '
            f'max={data_max:.2f}, mean={data_mean:.2f}, '
            f'cdf_decile={data_cdf_decile}'
        )

    # Log the message to the console/logcat
    ad.log.info(log_message)
    # If a CSV file path was provided, write the same message to the file
    if csv_filepath:
        write_to_csv(csv_filepath, csv_header, log_message)

def extract_cdf(data):
    """Calculates the Cumulative Distribution Function (CDF) of the data.

    Args:
        data: A list containing data (does not have to be sorted).

    Returns: a list of 2 lists: the X and Y axis of the CDF.
    """
    x = []
    cdf = []
    if not data:
        return (x, cdf)
    all_values = sorted(data)
    for val in all_values:
        if not x:
            x.append(val)
            cdf.append(1)
        else:
            if x[-1] == val:
                cdf[-1] += 1
            else:
                x.append(val)
                cdf.append(cdf[-1] + 1)
    scale = 1.0 / len(all_values)
    for i in range(len(cdf)):
        cdf[i] = cdf[i] * scale
    return (x, cdf)

def extract_cdf_decile(cdf):
    """Extracts the 10%, 20%, ..., 90% points from the CDF and returns their
    value (a list of 9 values).

    Since CDF may not (will not) have exact x% value picks the value >= x%.

    Args:
        cdf: a list of 2 lists, the X and Y of the CDF.
    """
    decades = []
    next_decade = 10
    for x, y in zip(cdf[0], cdf[1]):
        while 100 * y >= next_decade:
            decades.append(x)
            next_decade = next_decade + 10
        if next_decade == 100:
            break
    return decades

# The new function to save the results
def save_results_to_json(results: dict, filepath: str):
    """Saves a dictionary to a file in a human-readable JSON format."""
    try:
        with open(filepath, 'w') as f:
            json.dump(results, f, indent=4)
        logging.info(f"Successfully saved results to {filepath}")
    except IOError as e:
        logging.error(f"Failed to write to file {filepath}: {e}")
    except TypeError as e:
        logging.error(
            f"Data contains a type that cannot be serialized to JSON: {e}")

def write_to_csv(filepath: str, header: str, row: str):
    """
    Appends a row to a CSV file.
    Creating it and adding a header if it doesn't exist.

    Args:
        filepath: The path to the CSV file.
        header: The header string to write if the file is new.
        row: The data string to append as a new line.
    """
    try:
        # Check if the file exists to decide whether to write the header
        file_exists = os.path.exists(filepath)

        # Use 'a' (append mode) to add to the file without overwriting it
        with open(filepath, 'a') as f:
            if not file_exists:
                f.write(header + '\n')
            f.write(row + '\n')

    except IOError as e:
        # Use the standard logging module for errors
        logging.error(f"Could not write to CSV file {filepath}: {e}")
