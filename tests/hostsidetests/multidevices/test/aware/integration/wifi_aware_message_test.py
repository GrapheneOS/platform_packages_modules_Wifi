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

# Lint as: python3
"""Wi-Fi Aware Message test reimplemented in Mobly."""
import logging
import string
import sys

from android.platform.test.annotations import ApiTest
from aware import aware_lib_utils as autils
from aware import constants
from mobly import asserts
from mobly import base_test
from mobly import records
from mobly import test_runner
from mobly import utils
from mobly.controllers import android_device
from mobly.controllers.android_device_lib import callback_handler_v2

RUNTIME_PERMISSIONS = (
    'android.permission.ACCESS_FINE_LOCATION',
    'android.permission.ACCESS_COARSE_LOCATION',
    'android.permission.NEARBY_WIFI_DEVICES',
)
PACKAGE_NAME = constants.WIFI_AWARE_SNIPPET_PACKAGE_NAME
snippets_to_load = [
    ('wifi_aware_snippet', PACKAGE_NAME),
    ('wifi', constants.WIFI_SNIPPET_PACKAGE_NAME),
]
_DEFAULT_TIMEOUT = constants.WAIT_WIFI_STATE_TIME_OUT.total_seconds()
_CALLBACK_NAME = constants.DiscoverySessionCallbackParamsType.CALLBACK_NAME
_IS_SESSION_INIT = constants.DiscoverySessionCallbackParamsType.IS_SESSION_INIT
_MESSAGE_SEND_SUCCEEDED = (
    constants.DiscoverySessionCallbackMethodType.MESSAGE_SEND_SUCCEEDED
    )
_MESSAGE_RECEIVED = (
    constants.DiscoverySessionCallbackMethodType.MESSAGE_RECEIVED
    )
_MESSAGE_SEND_RESULT = (
    constants.DiscoverySessionCallbackMethodType.MESSAGE_SEND_RESULT
    )

# Publish & Subscribe Config keys.
_PAYLOAD_SIZE_MIN = 0
_PAYLOAD_SIZE_TYPICAL = 1
_PAYLOAD_SIZE_MAX = 2
_PUBLISH_TYPE_UNSOLICITED = 0
_PUBLISH_TYPE_SOLICITED = 1
_SUBSCRIBE_TYPE_PASSIVE = 0
_SUBSCRIBE_TYPE_ACTIVE = 1

_NUM_MSGS_NO_QUEUE = 10
# number of messages = mult * queue depth
_NUM_MSGS_QUEUE_DEPTH_MULT = 2

_CAP_MAX_QUEUED_TRANSMIT_MESSAGES = "maxQueuedTransmitMessages"
_CAP_MAX_SERVICE_SPECIFIC_INFO_LEN = "maxServiceSpecificInfoLen"


class WifiAwareMessageTest(base_test.BaseTestClass):
    """Wi-Fi Aware test class."""

    # message ID counter to make sure all uses are unique
    msg_id = 0

    ads: list[android_device.AndroidDevice]
    publisher: android_device.AndroidDevice
    subscriber: android_device.AndroidDevice

    def setup_class(self):
        # Register two Android devices.
        self.ads = self.register_controller(android_device, min_number=2)
        self.publisher = self.ads[0]
        self.subscriber = self.ads[1]

        def setup_device(device: android_device.AndroidDevice):
            for snippet_name, package_name in snippets_to_load:
                device.load_snippet(snippet_name, package_name)
            for permission in RUNTIME_PERMISSIONS:
                device.adb.shell(['pm', 'grant', package_name, permission])
            asserts.abort_all_if(
                not device.wifi_aware_snippet.wifiAwareIsAvailable(),
                f'{device} Wi-Fi Aware is not available.',
            )

        # Set up devices in parallel.
        utils.concurrent_exec(
            setup_device,
            ((self.publisher,), (self.subscriber,)),
            max_workers=2,
            raise_on_exception=True,
        )

    def setup_test(self):
        for ad in self.ads:
            ad.wifi.wifiEnable()
            aware_avail = ad.wifi_aware_snippet.wifiAwareIsAvailable()
            if not aware_avail:
                ad.log.info('Aware not available. Waiting ...')
                state_handler = ad.wifi_aware_snippet.wifiAwareMonitorStateChange()
                state_handler.waitAndGet(
                    constants.WifiAwareBroadcast.WIFI_AWARE_AVAILABLE)

    def teardown_test(self):
        utils.concurrent_exec(
            self._teardown_test_on_device,
            ((self.publisher,), (self.subscriber,)),
            max_workers=2,
            raise_on_exception=True,
        )
        utils.concurrent_exec(
            lambda d: d.services.create_output_excerpts_all(
                self.current_test_info),
            param_list=[[ad] for ad in self.ads],
            raise_on_exception=True,
        )

    def _teardown_test_on_device(self, ad: android_device.AndroidDevice) -> None:
        ad.wifi_aware_snippet.wifiAwareCloseAllWifiAwareSession()
        ad.wifi.wifiClearConfiguredNetworks()
        ad.wifi.wifiEnable()
        if ad.is_adb_root:
          autils.reset_device_parameters(ad)
          autils.reset_device_statistics(ad)
          autils.validate_forbidden_callbacks(ad)

    def on_fail(self, record: records.TestResult) -> None:
        android_device.take_bug_reports(self.ads,
                                        destination =
                                        self.current_test_info.output_path)

    def _start_attach(self, ad: android_device.AndroidDevice) -> str:
        """Starts the attach process on the provided device."""
        handler = ad.wifi_aware_snippet.wifiAwareAttach()
        attach_event = handler.waitAndGet(
            event_name = constants.AttachCallBackMethodType.ATTACHED,
            timeout = _DEFAULT_TIMEOUT,
        )
        asserts.assert_true(
            ad.wifi_aware_snippet.wifiAwareIsSessionAttached(handler.callback_id),
            f'{ad} attach succeeded, but Wi-Fi Aware session is still null.'
        )
        ad.log.info('Attach Wi-Fi Aware session succeeded.')
        return attach_event.callback_id

    def get_next_msg_id(self):
        """Increment the message ID and returns the new value.
        Guarantees that each call to the method returns a unique value.

        Returns: a new message id value.
        """

        self.msg_id = self.msg_id + 1
        return self.msg_id

    def assert_equal_strings(self, first, second, msg=None, extras=None):
        """Assert equality of the string operands.
            where None is treated as equal to an empty string (''),
            otherwise fail the test.
        Error message is "first != second" by default. Additional explanation
        can be supplied in the message.

        Args:
            first, seconds: The strings that are evaluated for equality.
            msg: A string that adds additional info about the failure.
            extras: An optional field for extra information to be included in
                    test result.
        """
        if first == None:
            first = ''
        if second == None:
            second = ''
        asserts.assert_equal(first, second, msg, extras)

    def create_msg(self, payload_size, id):
        """Creates a message string of the specified size containing the id.

        Args:
            payload_size: The size of the message to create - min (null or
            empty message), typical, max (based on device capabilities).
            Use the PAYLOAD_SIZE_xx constants.
            id: Information to include in the generated message (or None).

        Returns: A string of the requested size, optionally containing the id.
        """
        if payload_size == _PAYLOAD_SIZE_MIN:
            return ""
        elif payload_size == _PAYLOAD_SIZE_TYPICAL:
            return "*** ID=%d ***" % id + string.ascii_uppercase
        else:  # PAYLOAD_SIZE_MAX
            return "*** ID=%4d ***" % id + "M" * (
                len(_CAP_MAX_SERVICE_SPECIFIC_INFO_LEN) - 15)

    def create_config(self, is_publish, extra_diff=None):
        """Create a base configuration based on input parameters.

        Args:
            is_publish: True for publish, False for subscribe sessions.
            extra_diff: String to add to service name: allows differentiating
                        discovery sessions.

        Returns:
            publish discovery configuration object.
        """
        config = {}
        if is_publish:
            config[
                constants.PUBLISH_TYPE] = _PUBLISH_TYPE_UNSOLICITED
        else:
            config[
                constants.SUBSCRIBE_TYPE ] = _SUBSCRIBE_TYPE_PASSIVE
        config[constants.SERVICE_NAME] = "GoogleTestServiceX" + (
            extra_diff if extra_diff is not None else "")
        return config

    def prep_message_exchange(self, extra_diff=None):
        """Creates a discovery session (publish and subscribe), and waits for
        service discovery - at that point the sessions are ready for message
        exchange.

        Args:
            extra_diff: String to add to service name: allows differentiating
                        discovery sessions.
        """

        p_dut = self.ads[0]
        p_dut.pretty_name = "Publisher"
        s_dut = self.ads[1]
        s_dut.pretty_name = "Subscriber"
        use_id = extra_diff is not None
        p_id = self._start_attach(p_dut)
        s_id = self._start_attach(s_dut)
        p_disc_id = p_dut.wifi_aware_snippet.wifiAwarePublish(
                p_id, self.create_config(True, extra_diff=extra_diff)
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
        # Subscriber: start subscribe and wait for confirmation
        s_disc_id = s_dut.wifi_aware_snippet.wifiAwareSubscribe(
                s_id, self.create_config(False, extra_diff=extra_diff)
                )
        s_dut.log.info('Created the subscribe session.')
        s_discovery = s_disc_id.waitAndGet(
                constants.DiscoverySessionCallbackMethodType.DISCOVER_RESULT,
                timeout=_DEFAULT_TIMEOUT)
        callback_name = s_discovery.data[_CALLBACK_NAME]
        asserts.assert_equal(
            constants.DiscoverySessionCallbackMethodType.SUBSCRIBE_STARTED,
            callback_name,
            f'{s_dut} subscribe failed, got callback: {callback_name}.',
            )
        discovered_event = s_disc_id.waitAndGet(
            constants.DiscoverySessionCallbackMethodType.SERVICE_DISCOVERED)
        peer_id_on_sub = discovered_event.data[
            constants.WifiAwareSnippetParams.PEER_ID]
        return {
            "p_dut": p_dut,
            "s_dut": s_dut,
            "p_id": p_id,
            "s_id": s_id,
            "p_disc_id": p_disc_id,
            "s_disc_id": s_disc_id,
            "peer_id_on_sub": peer_id_on_sub
        }
    def run_message_no_queue(self, payload_size):
        """Validate L2 message exchange between publisher & subscriber.
        with no queueing - i.e. wait for an ACK on each message before
        sending the next message.

        Args:
            payload_size: min, typical, or max (PAYLOAD_SIZE_xx).
        """
        discovery_info = self.prep_message_exchange()
        p_dut = discovery_info["p_dut"]
        s_dut = discovery_info["s_dut"]
        p_disc_id = discovery_info["p_disc_id"]
        s_disc_id = discovery_info["s_disc_id"]
        peer_id_on_sub = discovery_info["peer_id_on_sub"]
        for i in range(_NUM_MSGS_NO_QUEUE):
            msg = self.create_msg(payload_size, i)
            msg_id = self.get_next_msg_id()
            logging.info("msg: %s", msg)
            s_dut.wifi_aware_snippet.wifiAwareSendMessage(
                s_disc_id.callback_id, peer_id_on_sub, msg_id, msg
                )
            tx_event = s_disc_id.waitAndGet(
            event_name = _MESSAGE_SEND_RESULT,
            timeout = _DEFAULT_TIMEOUT,
            )
            callback_name = tx_event.data[
                constants.DiscoverySessionCallbackParamsType.CALLBACK_NAME
            ]
            asserts.assert_equal(
                callback_name,
                _MESSAGE_SEND_SUCCEEDED,
                f'{s_dut} failed to send message with an unexpected callback.',
            )
            actual_send_message_id = tx_event.data[
                constants.DiscoverySessionCallbackParamsType.MESSAGE_ID
            ]
            asserts.assert_equal(
                actual_send_message_id,
                msg_id,
                f'{s_dut} send message succeeded but message ID mismatched.'
            )
            rx_event = p_disc_id.waitAndGet(
                event_name = _MESSAGE_RECEIVED,
                timeout = _DEFAULT_TIMEOUT,
            )
            received_message_raw = rx_event.data[
                constants.WifiAwareSnippetParams.RECEIVED_MESSAGE
            ]
            received_message = bytes(received_message_raw).decode('utf-8')
            self.assert_equal_strings(
                msg,
                received_message,
                "Subscriber -> Publisher message %d corrupted" % i)
        peer_id_on_pub = rx_event.data[
            constants.WifiAwareSnippetParams.PEER_ID]
        for i in range(_NUM_MSGS_NO_QUEUE):
            msg = self.create_msg(payload_size, 1000 + i)
            msg_id = self.get_next_msg_id()

            p_dut.wifi_aware_snippet.wifiAwareSendMessage(
                p_disc_id.callback_id, peer_id_on_pub, msg_id, msg)
            tx_event = p_disc_id.waitAndGet(
                event_name=_MESSAGE_SEND_RESULT,
                timeout=_DEFAULT_TIMEOUT,
                )
            callback_name = tx_event.data[
                constants.DiscoverySessionCallbackParamsType.CALLBACK_NAME
            ]
            asserts.assert_equal(
                callback_name,
                _MESSAGE_SEND_SUCCEEDED,
                f'{p_dut} failed to send message with an unexpected callback.',
            )
            actual_send_message_id = tx_event.data[
                constants.DiscoverySessionCallbackParamsType.MESSAGE_ID
            ]
            asserts.assert_equal(
                actual_send_message_id,
                msg_id,
                f'{p_dut} send message succeeded but message ID mismatched.'
            )
            rx_event = s_disc_id.waitAndGet(
                event_name = _MESSAGE_RECEIVED,
                timeout = _DEFAULT_TIMEOUT,
            )
            received_message_raw = rx_event.data[
                constants.WifiAwareSnippetParams.RECEIVED_MESSAGE
            ]
            received_message = bytes(received_message_raw).decode('utf-8')
            self.assert_equal_strings(
                msg,
                received_message,
                "Subscriber -> Publisher message %d corrupted" % i)

    def wait_for_messages(self,
                          tx_msgs,
                          tx_msg_ids,
                          tx_disc_id,
                          rx_disc_id,
                          tx_dut,
                          rx_dut,
                          are_msgs_empty=False):
        """Validate that all expected messages are transmitted correctly.
        and received as expected. Method is called after the messages are
        sent into the transmission queue.

        Note: that message can be transmitted and received out-of-order (
        which is acceptable and the method handles that correctly).

        Args:
            tx_msgs: dictionary of transmitted messages
            tx_msg_ids: dictionary of transmitted message ids
            tx_disc_id: transmitter discovery session id (None for no
                        decoration)
            rx_disc_id: receiver discovery session id (None for no decoration)
            tx_dut: transmitter device
            rx_dut: receiver device
            are_msgs_empty: True if the messages are None or empty (changes dup
                            detection)

        Returns: the peer ID from any of the received messages
        """
        peer_id_on_rx = None
        still_to_be_tx = len(tx_msg_ids)
        while still_to_be_tx != 0:
            tx_event = tx_disc_id.waitAndGet(
                event_name=_MESSAGE_SEND_RESULT,
                timeout=_DEFAULT_TIMEOUT,
                )
            tx_msg_id = tx_event.data[
                constants.DiscoverySessionCallbackParamsType.MESSAGE_ID
                ]
            tx_msg_ids[tx_msg_id] = tx_msg_ids[tx_msg_id] + 1
            if tx_msg_ids[tx_msg_id] == 1:
                still_to_be_tx = still_to_be_tx - 1
            # check for any duplicate transmit notifications
        asserts.assert_equal(
            len(tx_msg_ids), sum(tx_msg_ids.values()),
            "Duplicate transmit message IDs: %s" % tx_msg_ids)

        # wait for all messages to be received
        still_to_be_rx = len(tx_msg_ids)
        while still_to_be_rx != 0:
            rx_event = rx_disc_id.waitAndGet(
                event_name=_MESSAGE_RECEIVED,
                timeout=_DEFAULT_TIMEOUT,
                )
            peer_id_on_rx = rx_event.data[
                constants.WifiAwareSnippetParams.PEER_ID
                ]
            if are_msgs_empty:
                still_to_be_rx = still_to_be_rx - 1
            else:
                received_message_raw = rx_event.data[
                    constants.WifiAwareSnippetParams.RECEIVED_MESSAGE
                    ]
                rx_msg = bytes(received_message_raw).decode('utf-8')
                asserts.assert_true(
                    rx_msg in tx_msgs,
                    "Received a message we did not send!? -- '%s'" % rx_msg)
                tx_msgs[rx_msg] = tx_msgs[rx_msg] + 1
                if tx_msgs[rx_msg] == 1:
                    still_to_be_rx = still_to_be_rx - 1
        # check for any duplicate received messages
        if not are_msgs_empty:
            asserts.assert_equal(
                len(tx_msgs), sum(tx_msgs.values()),
                "Duplicate transmit messages: %s" % tx_msgs)
        return peer_id_on_rx

    def run_message_with_queue(self, payload_size):
        """Validate L2 message exchange between publisher & subscriber.
        with queueing - i.e. transmit all messages and then wait for ACKs.

        Args:
            payload_size: min, typical, or max (PAYLOAD_SIZE_xx).
        """
        discovery_info = self.prep_message_exchange()
        p_dut = discovery_info["p_dut"]
        s_dut = discovery_info["s_dut"]
        p_disc_id = discovery_info["p_disc_id"]
        s_disc_id = discovery_info["s_disc_id"]
        peer_id_on_sub = discovery_info["peer_id_on_sub"]

        msgs = {}
        msg_ids = {}
        for i in range(
            _NUM_MSGS_QUEUE_DEPTH_MULT * autils.get_aware_capabilities(s_dut)[_CAP_MAX_QUEUED_TRANSMIT_MESSAGES]):
            msg = self.create_msg(payload_size, i)
            msg_id = self.get_next_msg_id()
            msgs[msg] = 0
            msg_ids[msg_id] = 0
            s_dut.wifi_aware_snippet.wifiAwareSendMessage(s_disc_id.callback_id,
                                                          peer_id_on_sub,
                                                          msg_id,
                                                          msg)
        peer_id_on_pub = self.wait_for_messages(msgs,
                                                msg_ids,
                                                s_disc_id,
                                                p_disc_id,
                                                s_dut,
                                                p_dut,
                                                payload_size
                                                    ==_PAYLOAD_SIZE_MIN)
        msgs = {}
        msg_ids = {}
        for i in range(
            _NUM_MSGS_QUEUE_DEPTH_MULT *  autils.get_aware_capabilities(p_dut)[_CAP_MAX_QUEUED_TRANSMIT_MESSAGES]):
            msg = self.create_msg(payload_size, 1000 + i)
            msg_id = self.get_next_msg_id()
            msgs[msg] = 0
            msg_ids[msg_id] = 0
            p_dut.wifi_aware_snippet.wifiAwareSendMessage(p_disc_id.callback_id,
                                                          peer_id_on_pub,
                                                          msg_id,
                                                          msg)
        self.wait_for_messages(msgs, msg_ids,p_disc_id, s_disc_id, p_dut, s_dut,
                               payload_size == _PAYLOAD_SIZE_MIN)
    def run_message_multi_session_with_queue(self, payload_size):
        """Validate L2 message exchange between publishers & subscribers with.
        queueing - i.e. transmit all messages and then wait for ACKs. Uses 2
        discovery sessions running concurrently and validates that messages
        arrive at the correct destination.

        Args:
            payload_size: min, typical, or max (PAYLOAD_SIZE_xx)
        """
        discovery_info1 = self.prep_message_exchange(extra_diff="-111")
        p_dut = discovery_info1["p_dut"]  # same for both sessions
        s_dut = discovery_info1["s_dut"]  # same for both sessions
        p_disc_id1 = discovery_info1["p_disc_id"]
        s_disc_id1 = discovery_info1["s_disc_id"]
        peer_id_on_sub1 = discovery_info1["peer_id_on_sub"]

        discovery_info2 = self.prep_message_exchange(extra_diff="-222")
        p_disc_id2 = discovery_info2["p_disc_id"]
        s_disc_id2 = discovery_info2["s_disc_id"]
        peer_id_on_sub2 = discovery_info2["peer_id_on_sub"]
        msgs1 = {}
        msg_ids1 = {}
        msgs2 = {}
        msg_ids2 = {}
        for i in range(
                _NUM_MSGS_QUEUE_DEPTH_MULT * autils.get_aware_capabilities(s_dut)[_CAP_MAX_QUEUED_TRANSMIT_MESSAGES]):
            msg1 = self.create_msg(payload_size, i)
            msg_id1 = self.get_next_msg_id()
            msgs1[msg1] = 0
            msg_ids1[msg_id1] = 0
            s_dut.wifi_aware_snippet.wifiAwareSendMessage(s_disc_id1.callback_id,
                                                          peer_id_on_sub1,
                                                          msg_id1,
                                                          msg1)
            msg2 = self.create_msg(payload_size, 100 + i)
            msg_id2 = self.get_next_msg_id()
            msgs2[msg2] = 0
            msg_ids2[msg_id2] = 0
            s_dut.wifi_aware_snippet.wifiAwareSendMessage(s_disc_id2.callback_id,
                                                          peer_id_on_sub2,
                                                          msg_id2,
                                                          msg2)
        peer_id_on_pub1 = self.wait_for_messages(
            msgs1, msg_ids1, s_disc_id1, p_disc_id1, s_dut, p_dut,
            payload_size == _PAYLOAD_SIZE_MIN)
        peer_id_on_pub2 = self.wait_for_messages(
            msgs2, msg_ids2, s_disc_id2, p_disc_id2, s_dut, p_dut,
            payload_size == _PAYLOAD_SIZE_MIN)
        msgs1 = {}
        msg_ids1 = {}
        msgs2 = {}
        msg_ids2 = {}
        for i in range(
                _NUM_MSGS_QUEUE_DEPTH_MULT * autils.get_aware_capabilities(p_dut)[_CAP_MAX_QUEUED_TRANSMIT_MESSAGES]):
            msg1 = self.create_msg(payload_size, 1000 + i)
            msg_id1 = self.get_next_msg_id()
            msgs1[msg1] = 0
            msg_ids1[msg_id1] = 0
            p_dut.wifi_aware_snippet.wifiAwareSendMessage(p_disc_id1.callback_id,
                                                          peer_id_on_pub1,
                                                          msg_id1,
                                                          msg1)
            msg2 = self.create_msg(payload_size, 1100 + i)
            msg_id2 = self.get_next_msg_id()
            msgs2[msg2] = 0
            msg_ids2[msg_id2] = 0

            p_dut.wifi_aware_snippet.wifiAwareSendMessage(
                p_disc_id2.callback_id, peer_id_on_pub2, msg_id2,msg2)
        self.wait_for_messages(msgs1, msg_ids1, p_disc_id1, s_disc_id1, p_dut,
                               s_dut, payload_size == _PAYLOAD_SIZE_MIN)
        self.wait_for_messages(msgs2, msg_ids2, p_disc_id2, s_disc_id2, p_dut,
                               s_dut, payload_size == _PAYLOAD_SIZE_MIN)

    @ApiTest(
        apis=[
            'android.net.wifi.aware.WifiAwareManager#attach(android.net.wifi.aware.AttachCallback, android.net.wifi.aware.IdentityChangedListener, android.os.Handler)',
            'android.net.wifi.aware.WifiAwareSession#publish(android.net.wifi.aware.PublishConfig, android.net.wifi.aware.DiscoverySessionCallback, android.os.Handler)',
            'android.net.wifi.aware.WifiAwareSession#subscrible(android.net.wifi.aware.SubscribeConfig, android.net.wifi.aware.DiscoverySessionCallback, android.os.Handler)',
            'android.net.wifi.aware.PublishConfig.Builder#setPublishType(PublishConfig.PUBLISH_TYPE_UNSOLICITED)',
            'android.net.wifi.aware.SubscribeConfig.Builder#setSubscribeType(SubscribeConfig.SUBSCRIBE_TYPE_PASSIVE)',
            'android.net.wifi.aware.DiscoverySession#sendMessage(int, byte[])',
        ]
    )

    def test_message_no_queue_min(self):
        """Functional / Message / No queue
        - Minimal payload size (None or "")
        """
        self.run_message_no_queue(_PAYLOAD_SIZE_MIN)

    @ApiTest(
        apis=[
            'android.net.wifi.aware.WifiAwareManager#attach(android.net.wifi.aware.AttachCallback, android.net.wifi.aware.IdentityChangedListener, android.os.Handler)',
            'android.net.wifi.aware.WifiAwareSession#publish(android.net.wifi.aware.PublishConfig, android.net.wifi.aware.DiscoverySessionCallback, android.os.Handler)',
            'android.net.wifi.aware.WifiAwareSession#subscrible(android.net.wifi.aware.SubscribeConfig, android.net.wifi.aware.DiscoverySessionCallback, android.os.Handler)',
            'android.net.wifi.aware.PublishConfig.Builder#setPublishType(PublishConfig.PUBLISH_TYPE_UNSOLICITED)',
            'android.net.wifi.aware.SubscribeConfig.Builder#setSubscribeType(SubscribeConfig.SUBSCRIBE_TYPE_PASSIVE)',
            'android.net.wifi.aware.DiscoverySession#sendMessage(int, byte[])',
        ]
    )

    def test_message_no_queue_typical(self):
        """Functional / Message / No queue
        - Typical payload size
        """
        self.run_message_no_queue(_PAYLOAD_SIZE_TYPICAL)

    @ApiTest(
        apis=[
            'android.net.wifi.aware.WifiAwareManager#attach(android.net.wifi.aware.AttachCallback, android.net.wifi.aware.IdentityChangedListener, android.os.Handler)',
            'android.net.wifi.aware.WifiAwareSession#publish(android.net.wifi.aware.PublishConfig, android.net.wifi.aware.DiscoverySessionCallback, android.os.Handler)',
            'android.net.wifi.aware.WifiAwareSession#subscrible(android.net.wifi.aware.SubscribeConfig, android.net.wifi.aware.DiscoverySessionCallback, android.os.Handler)',
            'android.net.wifi.aware.PublishConfig.Builder#setPublishType(PublishConfig.PUBLISH_TYPE_UNSOLICITED)',
            'android.net.wifi.aware.SubscribeConfig.Builder#setSubscribeType(SubscribeConfig.SUBSCRIBE_TYPE_PASSIVE)',
            'android.net.wifi.aware.DiscoverySession#sendMessage(int, byte[])',
        ]
    )

    def test_message_no_queue_max(self):
        """Functional / Message / No queue
        - Max payload size (based on device capabilities)
        """
        self.run_message_no_queue(_PAYLOAD_SIZE_MAX)

    @ApiTest(
        apis=[
            'android.net.wifi.aware.WifiAwareManager#attach(android.net.wifi.aware.AttachCallback, android.net.wifi.aware.IdentityChangedListener, android.os.Handler)',
            'android.net.wifi.aware.WifiAwareSession#publish(android.net.wifi.aware.PublishConfig, android.net.wifi.aware.DiscoverySessionCallback, android.os.Handler)',
            'android.net.wifi.aware.WifiAwareSession#subscrible(android.net.wifi.aware.SubscribeConfig, android.net.wifi.aware.DiscoverySessionCallback, android.os.Handler)',
            'android.net.wifi.aware.PublishConfig.Builder#setPublishType(PublishConfig.PUBLISH_TYPE_UNSOLICITED)',
            'android.net.wifi.aware.SubscribeConfig.Builder#setSubscribeType(SubscribeConfig.SUBSCRIBE_TYPE_PASSIVE)',
            'android.net.wifi.aware.DiscoverySession#sendMessage(int, byte[])',
        ]
    )

    def test_message_with_queue_min(self):
        """Functional / Message / With queue
    - Minimal payload size (none or "")
    """
        self.run_message_with_queue(_PAYLOAD_SIZE_MIN)

    @ApiTest(
        apis=[
            'android.net.wifi.aware.WifiAwareManager#attach(android.net.wifi.aware.AttachCallback, android.net.wifi.aware.IdentityChangedListener, android.os.Handler)',
            'android.net.wifi.aware.WifiAwareSession#publish(android.net.wifi.aware.PublishConfig, android.net.wifi.aware.DiscoverySessionCallback, android.os.Handler)',
            'android.net.wifi.aware.WifiAwareSession#subscrible(android.net.wifi.aware.SubscribeConfig, android.net.wifi.aware.DiscoverySessionCallback, android.os.Handler)',
            'android.net.wifi.aware.PublishConfig.Builder#setPublishType(PublishConfig.PUBLISH_TYPE_UNSOLICITED)',
            'android.net.wifi.aware.SubscribeConfig.Builder#setSubscribeType(SubscribeConfig.SUBSCRIBE_TYPE_PASSIVE)',
            'android.net.wifi.aware.DiscoverySession#sendMessage(int, byte[])',
        ]
    )

    def test_message_with_queue_typical(self):
        """Functional / Message / With queue
    - Typical payload size
    """
        self.run_message_with_queue(_PAYLOAD_SIZE_TYPICAL)

    @ApiTest(
        apis=[
            'android.net.wifi.aware.WifiAwareManager#attach(android.net.wifi.aware.AttachCallback, android.net.wifi.aware.IdentityChangedListener, android.os.Handler)',
            'android.net.wifi.aware.WifiAwareSession#publish(android.net.wifi.aware.PublishConfig, android.net.wifi.aware.DiscoverySessionCallback, android.os.Handler)',
            'android.net.wifi.aware.WifiAwareSession#subscrible(android.net.wifi.aware.SubscribeConfig, android.net.wifi.aware.DiscoverySessionCallback, android.os.Handler)',
            'android.net.wifi.aware.PublishConfig.Builder#setPublishType(PublishConfig.PUBLISH_TYPE_UNSOLICITED)',
            'android.net.wifi.aware.SubscribeConfig.Builder#setSubscribeType(SubscribeConfig.SUBSCRIBE_TYPE_PASSIVE)',
            'android.net.wifi.aware.DiscoverySession#sendMessage(int, byte[])',
        ]
    )

    def test_message_with_queue_max(self):
        """Functional / Message / With queue
    - Max payload size (based on device capabilities)
    """
        self.run_message_with_queue(_PAYLOAD_SIZE_MAX)

    @ApiTest(
        apis=[
            'android.net.wifi.aware.WifiAwareManager#attach(android.net.wifi.aware.AttachCallback, android.net.wifi.aware.IdentityChangedListener, android.os.Handler)',
            'android.net.wifi.aware.WifiAwareSession#publish(android.net.wifi.aware.PublishConfig, android.net.wifi.aware.DiscoverySessionCallback, android.os.Handler)',
            'android.net.wifi.aware.WifiAwareSession#subscrible(android.net.wifi.aware.SubscribeConfig, android.net.wifi.aware.DiscoverySessionCallback, android.os.Handler)',
            'android.net.wifi.aware.PublishConfig.Builder#setPublishType(PublishConfig.PUBLISH_TYPE_UNSOLICITED)',
            'android.net.wifi.aware.SubscribeConfig.Builder#setSubscribeType(SubscribeConfig.SUBSCRIBE_TYPE_PASSIVE)',
            'android.net.wifi.aware.DiscoverySession#sendMessage(int, byte[])',
        ]
    )

    def test_message_with_multiple_discovery_sessions_typical(self):
        """Functional / Message / Multiple sessions

    Sets up 2 discovery sessions on 2 devices. Sends a message in each
    direction on each discovery session and verifies that reaches expected
    destination.
    """
        self.run_message_multi_session_with_queue(_PAYLOAD_SIZE_TYPICAL)


if __name__ == '__main__':
    # Take test args
    if '--' in sys.argv:
        index = sys.argv.index('--')
        sys.argv = sys.argv[:1] + sys.argv[index + 1:]

    test_runner.main()
