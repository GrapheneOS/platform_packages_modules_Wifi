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
"""Wi-Fi Aware Discovery test reimplemented in Mobly."""
import enum
import logging
import random
import string
import sys
import time
from typing import Any, Dict, Union

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
_MSG_ID_SUB_TO_PUB = random.randint(1000, 5000)
_MSG_ID_PUB_TO_SUB = random.randint(5001, 9999)
_MSG_SUB_TO_PUB = "Let's talk [Random Identifier: %s]" % utils.rand_ascii_str(5)
_MSG_PUB_TO_SUB = 'Ready [Random Identifier: %s]' % utils.rand_ascii_str(5)
_CALLBACK_NAME = constants.DiscoverySessionCallbackParamsType.CALLBACK_NAME
_IS_SESSION_INIT = constants.DiscoverySessionCallbackParamsType.IS_SESSION_INIT

# Publish & Subscribe Config keys.
_PAYLOAD_SIZE_MIN = 0
_PAYLOAD_SIZE_TYPICAL = 1
_PAYLOAD_SIZE_MAX = 2
_PUBLISH_TYPE_UNSOLICITED = 0
_PUBLISH_TYPE_SOLICITED = 1
_SUBSCRIBE_TYPE_PASSIVE = 0
_SUBSCRIBE_TYPE_ACTIVE = 1


class WifiAwareDiscoveryTest(base_test.BaseTestClass):
    """Wi-Fi Aware test class."""

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

    def on_fail(self, record: records.TestResult) -> None:
        android_device.take_bug_reports(self.ads,
                                        destination =
                                        self.current_test_info.output_path)

    def _start_attach(self, ad: android_device.AndroidDevice) -> str:
        """Starts the attach process on the provided device."""
        handler = ad.wifi_aware_snippet.wifiAwareAttach()
        attach_event = handler.waitAndGet(
            event_name=constants.AttachCallBackMethodType.ATTACHED,
            timeout=_DEFAULT_TIMEOUT,
        )
        asserts.assert_true(
            ad.wifi_aware_snippet.wifiAwareIsSessionAttached(handler.callback_id),
            f'{ad} attach succeeded, but Wi-Fi Aware session is still null.'
        )
        ad.log.info('Attach Wi-Fi Aware session succeeded.')
        return attach_event.callback_id

    def _send_msg_and_check_received(
        self,
        *,
        sender: android_device.AndroidDevice,
        sender_aware_session_cb_handler: callback_handler_v2.CallbackHandlerV2,
        receiver: android_device.AndroidDevice,
        receiver_aware_session_cb_handler: callback_handler_v2.CallbackHandlerV2,
        discovery_session: str,
        peer: int,
        send_message: str,
        send_message_id: int,
    ) -> int:
        sender.wifi_aware_snippet.wifiAwareSendMessage(
            discovery_session, peer, send_message_id, send_message
        )
        message_send_result = sender_aware_session_cb_handler.waitAndGet(
            event_name =
            constants.DiscoverySessionCallbackMethodType.MESSAGE_SEND_RESULT,
            timeout =_DEFAULT_TIMEOUT,
        )
        callback_name = message_send_result.data[
            constants.DiscoverySessionCallbackParamsType.CALLBACK_NAME
        ]
        asserts.assert_equal(
            callback_name,
            constants.DiscoverySessionCallbackMethodType.MESSAGE_SEND_SUCCEEDED,
            f'{sender} failed to send message with an unexpected callback.',
        )
        actual_send_message_id = message_send_result.data[
            constants.DiscoverySessionCallbackParamsType.MESSAGE_ID
        ]
        asserts.assert_equal(
            actual_send_message_id,
            send_message_id,
            f'{sender} send message succeeded but message ID mismatched.'
        )
        receive_message_event = receiver_aware_session_cb_handler.waitAndGet(
            event_name = constants.DiscoverySessionCallbackMethodType.MESSAGE_RECEIVED,
            timeout = _DEFAULT_TIMEOUT,
        )
        received_message_raw = receive_message_event.data[
            constants.WifiAwareSnippetParams.RECEIVED_MESSAGE
        ]
        received_message = bytes(received_message_raw).decode('utf-8')
        asserts.assert_equal(
            received_message,
            send_message,
            f'{receiver} received the message but message content mismatched.'
        )
        return receive_message_event.data[
            constants.WifiAwareSnippetParams.PEER_ID]

    def create_base_config(self,
                           caps: Dict[str, Union[bool, int, str]],
                           is_publish: bool,
                           ptype: Union[int, None],
                           stype: Union[int, None],
                           payload_size: int,
                           ttl: int,
                           term_ind_on: bool,
                           null_match: bool) -> Dict[str, Any]:
        config = {}
        if is_publish:
            config[constants.PUBLISH_TYPE] = ptype
        else:
            config[constants.SUBSCRIBE_TYPE] = stype
        config[constants.TTL_SEC] = ttl
        config[constants.TERMINATE_NOTIFICATION_ENABLED] = term_ind_on
        if payload_size == _PAYLOAD_SIZE_MIN:
            config[constants.SERVICE_NAME] = "a"
            config[constants.SERVICE_SPECIFIC_INFO] = None
            config[constants.MATCH_FILTER] = []
        elif payload_size == _PAYLOAD_SIZE_TYPICAL:
            config[constants.SERVICE_NAME] = "GoogleTestServiceX"
            if is_publish:
                config[constants.SERVICE_SPECIFIC_INFO] = string.ascii_letters
            else:
                config[constants.SERVICE_SPECIFIC_INFO] = string.ascii_letters[
                    ::-1]
            config[constants.MATCH_FILTER] = autils.encode_list(
                [(10).to_bytes(1, byteorder="big"),"hello there string"
                 if not null_match else None,bytes(range(40))])
        else:  # aware_constant.PAYLOAD_SIZE_MAX
            config[constants.SERVICE_NAME] = "VeryLong" + "X" * (
                len("maxServiceNameLen") - 8)
            config[constants.SERVICE_SPECIFIC_INFO] = (
                "P" if is_publish else "S") * len("maxServiceSpecificInfoLen")
            mf = autils.construct_max_match_filter(len("maxMatchFilterLen"))
            if null_match:
                mf[2] = None
            config[constants.MATCH_FILTER] = autils.encode_list(mf)
        return config

    def create_publish_config(self, caps: Dict[str, Union[bool, int, str]],
                              ptype: int, payload_size: int, ttl: int,
                              term_ind_on: bool,
                              null_match: bool) -> Dict[str, Any]:
        return self.create_base_config(caps, True, ptype, None, payload_size,
                                       ttl, term_ind_on, null_match)

    def create_subscribe_config(self, caps: Dict[str, Union[bool, int, str]],
                                stype: int, payload_size: int, ttl: int,
                                term_ind_on: bool,
                                null_match: bool) -> Dict[str, Any]:
        return self.create_base_config(caps, False,  None, stype, payload_size,
                                       ttl, term_ind_on, null_match)

    def _positive_discovery_logic(self, ptype: int, stype: int,
                                payload_size: int) -> None:
        """Utility function for positive discovery test.

        1. Attach both publisher + subscriber to WiFi Aware service.
        2. Publisher publishes a service.
        3. Subscriber discoveries service(s) from publisher.
        4. Exchange messages both publisher + subscriber.
        5. Update publish/subscribe.
        6. Terminate publish/subscribe.

        Args:
            ptype: Publish discovery type.
            stype: Subscribe discovery type.
            payload_size: One of PAYLOAD_SIZE_* constants - MIN, TYPICAL, MAX.

        """
        pid = self._start_attach(self.publisher)
        sid = self._start_attach(self.subscriber)
        p_config = self.create_publish_config(
            self.publisher.wifi_aware_snippet.getCharacteristics(),
            ptype,
            payload_size,
            ttl=0,
            term_ind_on=False,
            null_match=False,
            )
        p_disc_id = self.publisher.wifi_aware_snippet.wifiAwarePublish(
            pid, p_config
            )
        self.publisher.log.info('Created the publish session.')
        p_discovery = p_disc_id.waitAndGet(
            constants.DiscoverySessionCallbackMethodType.DISCOVER_RESULT)
        callback_name = p_discovery.data[_CALLBACK_NAME]
        asserts.assert_equal(
            constants.DiscoverySessionCallbackMethodType.PUBLISH_STARTED,
            callback_name,
            f'{self.publisher} publish failed, got callback: {callback_name}.',
            )
        s_config = self.create_subscribe_config(
            self.subscriber.wifi_aware_snippet.getCharacteristics(),
            stype,
            payload_size,
            ttl=0,
            term_ind_on=False,
            null_match=True,
            )
        s_disc_id = self.subscriber.wifi_aware_snippet.wifiAwareSubscribe(
            sid, s_config
            )
        s_discovery = s_disc_id.waitAndGet(
            constants.DiscoverySessionCallbackMethodType.DISCOVER_RESULT)
        callback_name = s_discovery.data[_CALLBACK_NAME]
        asserts.assert_equal(
            constants.DiscoverySessionCallbackMethodType.SUBSCRIBE_STARTED,
            callback_name,
            f'{self.subscriber} subscribe failed, got callback: {callback_name}.'
        )
        is_session_init = s_discovery.data[_IS_SESSION_INIT]
        asserts.assert_true(
            is_session_init,
            f'{self.subscriber} subscribe succeeded, but null session returned.'
        )
        discovered_event = s_disc_id.waitAndGet(
            constants.DiscoverySessionCallbackMethodType.SERVICE_DISCOVERED)
        subscriber_peer = discovered_event.data[constants.WifiAwareSnippetParams.PEER_ID]
        if p_config[constants.SERVICE_SPECIFIC_INFO] == None:
            p_ssi_1st_disc = "null"
        else:
            p_ssi_1st_disc = p_config[constants.SERVICE_SPECIFIC_INFO]
        s_ssi_1st_disc =  bytes(
            discovered_event.data[
                constants.WifiAwareSnippetParams.SERVICE_SPECIFIC_INFO]
            ).decode("utf-8")
        asserts.assert_equal(s_ssi_1st_disc, p_ssi_1st_disc,
                             "Discovery mismatch: service specific info (SSI)")
        p_filter_list_1 = autils.decode_list(p_config[constants.MATCH_FILTER])
        s_filter_list_1 = discovered_event.data[
            constants.WifiAwareSnippetParams.MATCH_FILTER]
        s_filter_list_1 = [bytes(filter[
            constants.WifiAwareSnippetParams.MATCH_FILTER_VALUE
            ]).decode("utf-8")
                           for filter in s_filter_list_1]
        s_filter_list_1 = autils.decode_list(s_filter_list_1)
        asserts.assert_equal(s_filter_list_1,
                             p_filter_list_1 if ptype == _PUBLISH_TYPE_UNSOLICITED
                             else  autils.decode_list(s_config[constants.MATCH_FILTER]),
                             "Discovery mismatch: match filter")
        # Subscriber sends a message to publisher.
        publisher_peer = self._send_msg_and_check_received(
            sender=self.subscriber,
            sender_aware_session_cb_handler=s_disc_id,
            receiver=self.publisher,
            receiver_aware_session_cb_handler=p_disc_id,
            discovery_session=s_disc_id.callback_id,
            peer=subscriber_peer,
            send_message=_MSG_SUB_TO_PUB,
            send_message_id=_MSG_ID_SUB_TO_PUB,
            )
        logging.info(
            'The subscriber sent a message and the publisher received it.'
            )
        # Publisher sends a message to subscriber.
        self._send_msg_and_check_received(
            sender=self.publisher,
            sender_aware_session_cb_handler=p_disc_id,
            receiver=self.subscriber,
            receiver_aware_session_cb_handler=s_disc_id,
            discovery_session=p_disc_id.callback_id,
            peer=publisher_peer,
            send_message=_MSG_PUB_TO_SUB,
            send_message_id=_MSG_ID_PUB_TO_SUB,
        )
        logging.info(
            'The publisher sent a message and the subscriber received it.'
        )
        p_config[constants.SERVICE_SPECIFIC_INFO] = "something else"
        self.publisher.wifi_aware_snippet.wifiAwareUpdatePublish(
            p_disc_id.callback_id, p_config
            )
        p_disc_id.waitAndGet(
            constants.DiscoverySessionCallbackMethodType.SESSION_CONFIG_UPDATED)
        discovered_event_1 = s_disc_id.waitAndGet(
            constants.DiscoverySessionCallbackMethodType.SERVICE_DISCOVERED)
        p_ssi_2st_disc = p_config[constants.SERVICE_SPECIFIC_INFO]
        s_ssi_2st_disc = bytes(
            discovered_event_1.data[
                constants.WifiAwareSnippetParams.SERVICE_SPECIFIC_INFO]
        ).decode("utf-8")
        asserts.assert_equal(s_ssi_2st_disc, p_ssi_2st_disc,
                             "Discovery mismatch (after pub update): service specific info (SSI")
        p_filter_list_2 = autils.decode_list(p_config[constants.MATCH_FILTER])
        s_filter_list_2 = discovered_event_1.data[
            constants.WifiAwareSnippetParams.MATCH_FILTER]
        s_filter_list_2 = [bytes(filter[
            constants.WifiAwareSnippetParams.MATCH_FILTER_VALUE]).decode("utf-8")
                           for filter in s_filter_list_2]
        s_filter_list_2 = autils.decode_list(s_filter_list_2)
        asserts.assert_equal(s_filter_list_2,
                             p_filter_list_2  if ptype == _PUBLISH_TYPE_UNSOLICITED
                             else  autils.decode_list(s_config[constants.MATCH_FILTER]),
                             "Discovery mismatch: match filter")
        disc_peer_id = discovered_event_1.data[
            constants.WifiAwareSnippetParams.PEER_ID]
        asserts.assert_equal(subscriber_peer, disc_peer_id,
                             "Peer ID changed when publish was updated!?")
        s_config = self.create_subscribe_config(
            self.subscriber.wifi_aware_snippet.getCharacteristics(),
            stype,
            payload_size,
            ttl=0,
            term_ind_on=False,
            null_match=False,
            )
        s_disc_id_1 = self.subscriber.wifi_aware_snippet.wifiAwareUpdateSubscribe(
            discovered_event_1.callback_id, s_config
            )
        s_disc_id.waitAndGet(
            constants.DiscoverySessionCallbackMethodType.SESSION_CONFIG_UPDATED)
        self.publisher.wifi_aware_snippet.wifiAwareCloseDiscoverSession(
            p_disc_id.callback_id)
        self.subscriber.wifi_aware_snippet.wifiAwareCloseDiscoverSession(
            s_disc_id.callback_id)
        p_disc_id.waitAndGet(
            constants.DiscoverySessionCallbackMethodType.SESSION_TERMINATED)
        s_disc_id.waitAndGet(
            constants.DiscoverySessionCallbackMethodType.SESSION_TERMINATED)
        time.sleep(10)
        if self.publisher.is_adb_root:
            # Verify that forbidden callbacks aren't called.
            autils.validate_forbidden_callbacks(self.publisher, {"4": 0})
        self.publisher.wifi_aware_snippet.wifiAwareDetach(pid)
        self.subscriber.wifi_aware_snippet.wifiAwareDetach(sid)

    def verify_discovery_session_term(self, dut, disc_id, config, is_publish,
                                      term_ind_on):
        """Utility to verify that the specified discovery session has terminated.
        (by waiting for the TTL and then attempting to reconfigure).

        Args:
            dut: device under test
            disc_id: discovery id for the existing session
            config: configuration of the existing session
            is_publish: True if the configuration was publish, False if subscribe
            term_ind_on: True if a termination indication is expected, False otherwise
        """
        # Wait for session termination
        if term_ind_on:
            disc_id.waitAndGet(
                constants.DiscoverySessionCallbackMethodType.SESSION_TERMINATED,
                timeout = _DEFAULT_TIMEOUT)
        else:
            autils.callback_no_response(
                disc_id,
                constants.DiscoverySessionCallbackMethodType.SESSION_TERMINATED,
                timeout = _DEFAULT_TIMEOUT)
        config[constants.SERVICE_SPECIFIC_INFO] = "something else"
        if is_publish:
            dut.wifi_aware_snippet.wifiAwareUpdatePublish(
            disc_id.callback_id, config
            )
        else:
            dut.wifi_aware_snippet.wifiAwareUpdateSubscribe(
            disc_id.callback_id, config
            )
        if not term_ind_on:
            disc_id.waitAndGet(
                constants.DiscoverySessionCallbackMethodType.SESSION_CONFIG_FAILED,
                timeout =_DEFAULT_TIMEOUT
                )

    def positive_ttl_test_utility(self, is_publish, ptype, stype, term_ind_on):
        """Utility which runs a positive discovery session TTL configuration test.

        Iteration 1: Verify session started with TTL
        Iteration 2: Verify session started without TTL and reconfigured with TTL
        Iteration 3: Verify session started with (long) TTL and reconfigured with
                 (short) TTL

        Args:
            is_publish: True if testing publish, False if testing subscribe
            ptype: Publish discovery type (used if is_publish is True)
            stype: Subscribe discovery type (used if is_publish is False)
            term_ind_on: Configuration of termination indication
        """
        SHORT_TTL = 5  # 5 seconds
        LONG_TTL = 100  # 100 seconds
        dut = self.ads[0]
        id = self._start_attach(dut)
        # Iteration 1: Start discovery session with TTL
        config = self.create_base_config(
            dut.wifi_aware_snippet.getCharacteristics(),
            is_publish,
            ptype, stype,
            _PAYLOAD_SIZE_TYPICAL,
            SHORT_TTL,
            term_ind_on,
            False)
        if is_publish:
            disc_id = dut.wifi_aware_snippet.wifiAwarePublish(
                id, config
                )
            p_discovery = disc_id.waitAndGet(
                constants.DiscoverySessionCallbackMethodType.DISCOVER_RESULT,
                timeout=_DEFAULT_TIMEOUT)
            callback_name = p_discovery.data[_CALLBACK_NAME]
            asserts.assert_equal(
                constants.DiscoverySessionCallbackMethodType.PUBLISH_STARTED,
                callback_name,
                f'{dut} publish failed, got callback: {callback_name}.',
                )
        else:
            disc_id = dut.wifi_aware_snippet.wifiAwareSubscribe(
                id, config
                )
            s_discovery = disc_id.waitAndGet(
                constants.DiscoverySessionCallbackMethodType.DISCOVER_RESULT,
                timeout=_DEFAULT_TIMEOUT)
            callback_name = s_discovery.data[_CALLBACK_NAME]
            asserts.assert_equal(
                constants.DiscoverySessionCallbackMethodType.SUBSCRIBE_STARTED,
                callback_name,
                f'{dut} subscribe failed, got callback: {callback_name}.',
                )
        # Wait for session termination & verify
        self.verify_discovery_session_term(dut, disc_id, config, is_publish,
                                           term_ind_on)
        # Iteration 2: Start a discovery session without TTL
        config = self.create_base_config(
            dut.wifi_aware_snippet.getCharacteristics(),
            is_publish,
            ptype, stype,
            _PAYLOAD_SIZE_TYPICAL,
            0,
            term_ind_on,
            False)
        if is_publish:
            disc_id = dut.wifi_aware_snippet.wifiAwarePublish(
                id, config
                )
            p_discovery = disc_id.waitAndGet(
                constants.DiscoverySessionCallbackMethodType.DISCOVER_RESULT,
                timeout=_DEFAULT_TIMEOUT)
            callback_name = p_discovery.data[_CALLBACK_NAME]
            asserts.assert_equal(
                constants.DiscoverySessionCallbackMethodType.PUBLISH_STARTED,
                callback_name,
                f'{dut} publish failed, got callback: {callback_name}.',
                )
        else:
            disc_id = dut.wifi_aware_snippet.wifiAwareSubscribe(
                id, config
                )
            s_discovery = disc_id.waitAndGet(
                constants.DiscoverySessionCallbackMethodType.DISCOVER_RESULT,
                timeout=_DEFAULT_TIMEOUT)
            callback_name = s_discovery.data[_CALLBACK_NAME]
            asserts.assert_equal(
                constants.DiscoverySessionCallbackMethodType.SUBSCRIBE_STARTED,
                callback_name,
                f'{dut} subscribe failed, got callback: {callback_name}.',
                )
        # Update with a TTL
        config = self.create_base_config(
            dut.wifi_aware_snippet.getCharacteristics(),
            is_publish,
            ptype, stype,
            _PAYLOAD_SIZE_TYPICAL,
            SHORT_TTL,
            term_ind_on,
            False)
        if is_publish:
            disc_id = dut.wifi_aware_snippet.wifiAwarePublish(
                id, config
                )
            p_discovery = disc_id.waitAndGet(
                constants.DiscoverySessionCallbackMethodType.DISCOVER_RESULT,
                timeout=_DEFAULT_TIMEOUT)
            callback_name = p_discovery.data[_CALLBACK_NAME]
            asserts.assert_equal(
                constants.DiscoverySessionCallbackMethodType.PUBLISH_STARTED,
                callback_name,
                f'{dut} publish failed, got callback: {callback_name}.',
                )
        else:
            disc_id = dut.wifi_aware_snippet.wifiAwareSubscribe(
                id, config
                )
            s_discovery = disc_id.waitAndGet(
                constants.DiscoverySessionCallbackMethodType.DISCOVER_RESULT,
                timeout=_DEFAULT_TIMEOUT)
            callback_name = s_discovery.data[_CALLBACK_NAME]
            asserts.assert_equal(
                constants.DiscoverySessionCallbackMethodType.SUBSCRIBE_STARTED,
                callback_name,
                f'{dut} subscribe failed, got callback: {callback_name}.',
                )
        # Wait for session termination & verify
        self.verify_discovery_session_term(dut, disc_id, config, is_publish,
                                           term_ind_on)
        # Iteration 3: Start a discovery session with (long) TTL
        config = self.create_base_config(
            dut.wifi_aware_snippet.getCharacteristics(),
            is_publish,
            ptype, stype,
            _PAYLOAD_SIZE_TYPICAL,
            LONG_TTL,
            term_ind_on,
            False)
        if is_publish:
            disc_id = dut.wifi_aware_snippet.wifiAwarePublish(
                id, config
                )
            p_discovery = disc_id.waitAndGet(
                constants.DiscoverySessionCallbackMethodType.DISCOVER_RESULT,
                timeout=_DEFAULT_TIMEOUT)
            callback_name = p_discovery.data[_CALLBACK_NAME]
            asserts.assert_equal(
                constants.DiscoverySessionCallbackMethodType.PUBLISH_STARTED,
                callback_name,
                f'{dut} publish failed, got callback: {callback_name}.',
                )
        else:
            disc_id = dut.wifi_aware_snippet.wifiAwareSubscribe(
                id, config
                )
            s_discovery = disc_id.waitAndGet(
                constants.DiscoverySessionCallbackMethodType.DISCOVER_RESULT,
                timeout=_DEFAULT_TIMEOUT)
            callback_name = s_discovery.data[_CALLBACK_NAME]
            asserts.assert_equal(
                constants.DiscoverySessionCallbackMethodType.SUBSCRIBE_STARTED,
                callback_name,
                f'{dut} subscribe failed, got callback: {callback_name}.',
                )
        # Update with a TTL
        config = self.create_base_config(
            dut.wifi_aware_snippet.getCharacteristics(),
            is_publish,
            ptype, stype,
            _PAYLOAD_SIZE_TYPICAL,
            SHORT_TTL,
            term_ind_on,
            False)
        if is_publish:
            disc_id = dut.wifi_aware_snippet.wifiAwarePublish(
                id, config
                )
            p_discovery = disc_id.waitAndGet(
                constants.DiscoverySessionCallbackMethodType.DISCOVER_RESULT,
                timeout=_DEFAULT_TIMEOUT)
            callback_name = p_discovery.data[_CALLBACK_NAME]
            asserts.assert_equal(
                constants.DiscoverySessionCallbackMethodType.PUBLISH_STARTED,
                callback_name,
                f'{dut} publish failed, got callback: {callback_name}.',
                )
        else:
            disc_id = dut.wifi_aware_snippet.wifiAwareSubscribe(
                id, config
                )
            s_discovery = disc_id.waitAndGet(
                constants.DiscoverySessionCallbackMethodType.DISCOVER_RESULT,
                timeout=_DEFAULT_TIMEOUT)
            callback_name = s_discovery.data[_CALLBACK_NAME]
            asserts.assert_equal(
                constants.DiscoverySessionCallbackMethodType.SUBSCRIBE_STARTED,
                callback_name,
                f'{dut} subscribe failed, got callback: {callback_name}.',
                )
        # Wait for session termination & verify
        self.verify_discovery_session_term(dut, disc_id, config, is_publish,
                                           term_ind_on)
        # verify that forbidden callbacks aren't called
        if not term_ind_on:
             autils.validate_forbidden_callbacks(
                 dut,{
                    "2": 0,
                    "3": 0})

    def discovery_mismatch_test_utility(self,
                                        is_expected_to_pass,
                                        p_type,
                                        s_type,
                                        p_service_name=None,
                                        s_service_name=None,
                                        p_mf_1=None,
                                        s_mf_1=None):
        """Utility which runs the negative discovery test for mismatched service
        configs.

        Args:
            is_expected_to_pass: True if positive test, False if negative
            p_type: Publish discovery type
            s_type: Subscribe discovery type
            p_service_name: Publish service name (or None to leave unchanged)
            s_service_name: Subscribe service name (or None to leave unchanged)
            p_mf_1: Publish match filter element [1] (or None to leave unchanged)
            s_mf_1: Subscribe match filter element [1] (or None to leave unchanged)
        """
        p_dut = self.ads[0]
        s_dut = self.ads[1]
        # create configurations
        p_config = self.create_publish_config(
            p_dut.wifi_aware_snippet.getCharacteristics(),
            p_type,
            _PAYLOAD_SIZE_TYPICAL,
            ttl=0,
            term_ind_on=False,
            null_match=False)
        if p_service_name is not None:
            p_config[constants.SERVICE_NAME] = p_service_name
        if p_mf_1 is not None:
            p_config[constants.MATCH_FILTER] = autils.encode_list(
              [(10).to_bytes(1, byteorder="big"), p_mf_1 , bytes(range(40))])
        s_config = self.create_subscribe_config(
            s_dut.wifi_aware_snippet.getCharacteristics(),
            s_type,
            _PAYLOAD_SIZE_TYPICAL,
            ttl=0,
            term_ind_on=False,
            null_match=False)
        if s_service_name is not None:
            s_config[constants.SERVICE_NAME] = s_service_name
        if s_mf_1 is not None:
            s_config[constants.MATCH_FILTER] = autils.encode_list(
              [(10).to_bytes(1, byteorder="big"), s_mf_1 , bytes(range(40))])
        p_id = self._start_attach(p_dut)
        s_id = self._start_attach(s_dut)
        # Publisher: start publish and wait for confirmation
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
        s_disc_id = s_dut.wifi_aware_snippet.wifiAwareSubscribe(
                s_id, s_config
                )
        s_discovery = s_disc_id.waitAndGet(
                constants.DiscoverySessionCallbackMethodType.DISCOVER_RESULT,
                timeout=_DEFAULT_TIMEOUT)
        callback_name = s_discovery.data[_CALLBACK_NAME]
        asserts.assert_equal(
            constants.DiscoverySessionCallbackMethodType.SUBSCRIBE_STARTED,
            callback_name,
            f'{s_dut} subscribe failed, got callback: {callback_name}.',
            )
        # Subscriber: fail on service discovery
        if is_expected_to_pass:
            s_disc_id.waitAndGet(
                constants.DiscoverySessionCallbackMethodType.SERVICE_DISCOVERED)
        else:
            autils.callback_no_response(
                s_disc_id,
                constants.DiscoverySessionCallbackMethodType.SERVICE_DISCOVERED,
                timeout = _DEFAULT_TIMEOUT)
        # Publisher+Subscriber: Terminate sessions
        p_dut.wifi_aware_snippet.wifiAwareCloseDiscoverSession(
            p_disc_id.callback_id)
        s_dut.wifi_aware_snippet.wifiAwareCloseDiscoverSession(
            s_disc_id.callback_id)
        p_disc_id.waitAndGet(
            constants.DiscoverySessionCallbackMethodType.SESSION_TERMINATED)
        s_disc_id.waitAndGet(
            constants.DiscoverySessionCallbackMethodType.SESSION_TERMINATED)

    def create_discovery_pair(
        self, p_dut, s_dut, p_config, s_config, msg_id=None):
        """Creates a discovery session (publish and subscribe), and waits for
        service discovery - at that point the sessions are connected and ready for
        further messaging of data-path setup.

        Args:
            p_dut: Device to use as publisher.
            s_dut: Device to use as subscriber.
            p_config: Publish configuration.
            s_config: Subscribe configuration.
            device_startup_offset: Number of seconds to offset the enabling of NAN on
                                   the two devices.
            msg_id: Controls whether a message is sent from Subscriber to Publisher
            (so that publisher has the sub's peer ID). If None then not sent,
            otherwise should be an int for the message id.
        Returns: variable size list of:
            p_id: Publisher attach session id
            s_id: Subscriber attach session id
            p_disc_id: Publisher discovery session id
            s_disc_id: Subscriber discovery session id
            peer_id_on_sub: Peer ID of the Publisher as seen on the Subscriber
            peer_id_on_pub: Peer ID of the Subscriber as seen on the Publisher. Only
                            included if |msg_id| is not None.
        """

        p_dut = self.ads[0]
        s_dut = self.ads[1]
        # attach and wait for confirmation
        p_id = self._start_attach(p_dut)
        s_id = self._start_attach(s_dut)
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
        peer_id_on_sub = discovery_event.data[constants.WifiAwareSnippetParams.PEER_ID]
        # Optionally send a message from Subscriber to Publisher
        if msg_id is not None:
            ping_msg = 'PING'
            # Subscriber: send message to peer (Publisher)
            s_dut.sender.wifi_aware_snippet.wifiAwareSendMessage(
                s_disc_id, peer_id_on_sub, _MSG_ID_SUB_TO_PUB, ping_msg
                )
            message_send_result = s_disc_id.waitAndGet(
                event_name =
                constants.DiscoverySessionCallbackMethodType.MESSAGE_SEND_RESULT,
                timeout =_DEFAULT_TIMEOUT,
                )
            actual_send_message_id = message_send_result.data[
            constants.DiscoverySessionCallbackParamsType.MESSAGE_ID
            ]
            asserts.assert_equal(
                actual_send_message_id,
                _MSG_ID_SUB_TO_PUB,
                f'{s_dut} send message succeeded but message ID mismatched.'
                )
            pub_rx_msg_event = p_disc_id.waitAndGet(
                event_name = constants.DiscoverySessionCallbackMethodType.MESSAGE_RECEIVED,
                timeout = _DEFAULT_TIMEOUT,
                )
            peer_id_on_pub = pub_rx_msg_event.data[constants.WifiAwareSnippetParams.PEER_ID]
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

    def exchange_messages(self, p_dut, p_disc_id, s_dut, s_disc_id, peer_id_on_sub, session_name):
        """
        Exchange message between Publisher and Subscriber on target discovery session

        Args:
            p_dut: Publisher device
            p_disc_id: Publish discovery session id
            s_dut: Subscriber device
            s_disc_id: Subscribe discovery session id
            peer_id_on_sub: Peer ID of the Publisher as seen on the Subscriber
            session_name: dictionary of discovery session name base on role("pub" or "sub")
                          {role: {disc_id: name}}
        """

        msg_template = "Hello {} from {} !"
        # Message send from Subscriber to Publisher
        s_to_p_msg = msg_template.format(session_name["pub"][p_disc_id],
                                         session_name["sub"][s_disc_id])
        publisher_peer = self._send_msg_and_check_received(
            sender = s_dut,
            sender_aware_session_cb_handler= s_disc_id,
            receiver = p_dut,
            receiver_aware_session_cb_handler= p_disc_id,
            discovery_session = s_disc_id.callback_id,
            peer=peer_id_on_sub,
            send_message =s_to_p_msg,
            send_message_id = _MSG_ID_SUB_TO_PUB,
            )
        logging.info(
            'The subscriber sent a message and the publisher received it.'
            )

        # Publisher sends a message to subscriber.
        p_to_s_msg = msg_template.format(session_name["sub"][s_disc_id],
                                         session_name["pub"][p_disc_id])
        self._send_msg_and_check_received(
            sender=p_dut,
            sender_aware_session_cb_handler=p_disc_id,
            receiver=s_dut,
            receiver_aware_session_cb_handler=s_disc_id,
            discovery_session=p_disc_id.callback_id,
            peer=publisher_peer,
            send_message=p_to_s_msg,
            send_message_id=_MSG_ID_PUB_TO_SUB,
        )
        logging.info(
            'The publisher sent a message and the subscriber received it.'
        )

    def run_multiple_concurrent_services(self, type_x, type_y):
        """Validate same service name with multiple service specific info on publisher
        and subscriber can see all service

        - p_dut running Publish X and Y
        - s_dut running subscribe A and B
        - subscribe A find X and Y
        - subscribe B find X and Y

        Message exchanges:
            - A to X and X to A
            - B to X and X to B
            - A to Y and Y to A
            - B to Y and Y to B

        Note: test requires that publisher device support 2 publish sessions concurrently,
        and subscriber device support 2 subscribe sessions concurrently.
        The test will be skipped if the devices are not capable.

        Args:
            type_x, type_y: A list of [ptype, stype] of the publish and subscribe
                      types for services X and Y respectively.
        """

        p_dut = self.ads[0]
        s_dut = self.ads[1]
        X_SERVICE_NAME = "ServiceXXX"
        Y_SERVICE_NAME = "ServiceYYY"
        asserts.skip_if(
            autils.get_aware_capabilities(p_dut)["maxPublishes"] < 2
            or autils.get_aware_capabilities(s_dut)["maxPublishes"] < 2
            ,"Devices do not support 2 publish sessions"
            )
        # attach and wait for confirmation
        p_id = self._start_attach(p_dut)
        s_id = self._start_attach(s_dut)
        # DUT1 & DUT2: start publishing both X & Y services and wait for
        # confirmations
        dut1_x_pid = p_dut.wifi_aware_snippet.wifiAwarePublish(
            p_id, autils.create_discovery_config(X_SERVICE_NAME, type_x[0], None)
                )
        p_dut.log.info('Created the DUT1 X publish session.')
        p_discovery = dut1_x_pid.waitAndGet(
            constants.DiscoverySessionCallbackMethodType.DISCOVER_RESULT)
        callback_name = p_discovery.data[_CALLBACK_NAME]
        asserts.assert_equal(
            constants.DiscoverySessionCallbackMethodType.PUBLISH_STARTED,
            callback_name,
            f'{p_dut} DUT1 X publish failed, got callback: {callback_name}.',
            )
        dut1_y_pid = p_dut.wifi_aware_snippet.wifiAwarePublish(
                p_id, autils.create_discovery_config(Y_SERVICE_NAME, type_y[0], None)
                )
        p_dut.log.info('Created the DUT1 Y publish session.')
        p_discovery = dut1_y_pid.waitAndGet(
            constants.DiscoverySessionCallbackMethodType.DISCOVER_RESULT)
        callback_name = p_discovery.data[_CALLBACK_NAME]
        asserts.assert_equal(
            constants.DiscoverySessionCallbackMethodType.PUBLISH_STARTED,
            callback_name,
            f'{p_dut} DUT1 Y publish failed, got callback: {callback_name}.',
            )
        dut2_x_pid = s_dut.wifi_aware_snippet.wifiAwarePublish(
                s_id, autils.create_discovery_config(X_SERVICE_NAME, type_x[0], None)
                )
        s_dut.log.info('Created the DUT2 X publish session.')
        p_discovery = dut2_x_pid.waitAndGet(
            constants.DiscoverySessionCallbackMethodType.DISCOVER_RESULT)
        callback_name = p_discovery.data[_CALLBACK_NAME]
        asserts.assert_equal(
            constants.DiscoverySessionCallbackMethodType.PUBLISH_STARTED,
            callback_name,
            f'{s_dut} DUT2 X publish failed, got callback: {callback_name}.',
            )
        dut2_y_pid = s_dut.wifi_aware_snippet.wifiAwarePublish(
                s_id, autils.create_discovery_config(Y_SERVICE_NAME, type_y[0], None)
                )
        s_dut.log.info('Created the DUT2 Y publish session.')
        p_discovery = dut2_y_pid.waitAndGet(
            constants.DiscoverySessionCallbackMethodType.DISCOVER_RESULT)
        callback_name = p_discovery.data[_CALLBACK_NAME]
        asserts.assert_equal(
            constants.DiscoverySessionCallbackMethodType.PUBLISH_STARTED,
            callback_name,
            f'{s_dut} DUT1 Y publish failed, got callback: {callback_name}.',
            )
        # DUT1: start subscribing for X
        dut1_x_sid = p_dut.wifi_aware_snippet.wifiAwareSubscribe(
            p_id, autils.create_discovery_config(X_SERVICE_NAME, None, type_x[1])
            )
        p_dut.log.info('Created the DUT1 X subscribe session.')
        s_discovery = dut1_x_sid.waitAndGet(
                constants.DiscoverySessionCallbackMethodType.DISCOVER_RESULT,
                timeout=_DEFAULT_TIMEOUT)
        callback_name = s_discovery.data[_CALLBACK_NAME]
        asserts.assert_equal(
            constants.DiscoverySessionCallbackMethodType.SUBSCRIBE_STARTED,
            callback_name,
            f'{p_dut} DUT1 X subscribe failed, got callback: {callback_name}.',
            )
        # DUT1: start subscribing for Y
        dut1_y_sid = p_dut.wifi_aware_snippet.wifiAwareSubscribe(
                p_id, autils.create_discovery_config(Y_SERVICE_NAME, None, type_y[1])
                )
        p_dut.log.info('Created the DUT1 Y subscribe session.')
        s_discovery = dut1_y_sid.waitAndGet(
            constants.DiscoverySessionCallbackMethodType.DISCOVER_RESULT)
        callback_name = s_discovery.data[_CALLBACK_NAME]
        asserts.assert_equal(
            constants.DiscoverySessionCallbackMethodType.SUBSCRIBE_STARTED,
            callback_name,
            f'{p_dut} DUT1 Y subscribe failed, got callback: {callback_name}.',
            )
        # DUT2: start subscribing for X
        dut2_x_sid = s_dut.wifi_aware_snippet.wifiAwareSubscribe(
            s_id, autils.create_discovery_config(X_SERVICE_NAME, None, type_x[1])
            )
        s_dut.log.info('Created the DUT2 X subscribe session.')
        s_discovery = dut2_x_sid.waitAndGet(
                constants.DiscoverySessionCallbackMethodType.DISCOVER_RESULT,
                timeout=_DEFAULT_TIMEOUT)
        callback_name = s_discovery.data[_CALLBACK_NAME]
        asserts.assert_equal(
            constants.DiscoverySessionCallbackMethodType.SUBSCRIBE_STARTED,
            callback_name,
            f'{s_dut} DUT2 X subscribe failed, got callback: {callback_name}.',
            )
        # DUT2: start subscribing for Y
        dut2_y_sid = s_dut.wifi_aware_snippet.wifiAwareSubscribe(
                s_id, autils.create_discovery_config(Y_SERVICE_NAME, None, type_y[1])
                )
        s_dut.log.info('Created the DUT2 Y subscribe session.')
        s_discovery = dut2_y_sid.waitAndGet(
            constants.DiscoverySessionCallbackMethodType.DISCOVER_RESULT)
        callback_name = s_discovery.data[_CALLBACK_NAME]
        asserts.assert_equal(
            constants.DiscoverySessionCallbackMethodType.SUBSCRIBE_STARTED,
            callback_name,
            f'{s_dut} DUT2 Y subscribe failed, got callback: {callback_name}.',
            )
        dut1_x_sid_event = dut1_x_sid.waitAndGet(
            constants.DiscoverySessionCallbackMethodType.SERVICE_DISCOVERED)
        dut1_peer_id_for_dut2_x = dut1_x_sid_event.data[constants.WifiAwareSnippetParams.PEER_ID]

        dut2_y_sid_event = dut2_y_sid.waitAndGet(
            constants.DiscoverySessionCallbackMethodType.SERVICE_DISCOVERED)
        dut2_peer_id_for_dut1_y = dut2_y_sid_event.data[constants.WifiAwareSnippetParams.PEER_ID]

        # DUT1.X send message to DUT2
        x_msg = "Hello X on DUT2!"
        publisher_peer = self._send_msg_and_check_received(
            sender = p_dut,
            sender_aware_session_cb_handler= dut1_x_sid,
            receiver = s_dut,
            receiver_aware_session_cb_handler= dut2_x_pid,
            discovery_session = dut1_x_sid.callback_id,
            peer=dut1_peer_id_for_dut2_x,
            send_message =x_msg,
            send_message_id = _MSG_ID_PUB_TO_SUB,
            )
        logging.info(
            'The DUT1.X sent a message and the DUT2 received it.'
            )

        # DUT2.Y send message to DUT1
        y_msg = "Hello Y on DUT1!"
        self._send_msg_and_check_received(
            sender = s_dut,
            sender_aware_session_cb_handler= dut2_y_sid,
            receiver = p_dut,
            receiver_aware_session_cb_handler= dut1_y_pid,
            discovery_session = dut2_y_sid.callback_id,
            peer=dut2_peer_id_for_dut1_y,
            send_message =y_msg,
            send_message_id = _MSG_ID_SUB_TO_PUB,
            )
        logging.info(
            'The DUT2.Y sent a message and the DUT1 received it.'
            )

    def run_multiple_concurrent_services_same_name_diff_ssi(self, type_x, type_y):
        """Validate same service name with multiple service specific info on publisher
        and subscriber can see all service

        - p_dut running Publish X and Y
        - s_dut running subscribe A and B
        - subscribe A find X and Y
        - subscribe B find X and Y

        Message exchanges:
            - A to X and X to A
            - B to X and X to B
            - A to Y and Y to A
         - B to Y and Y to B

        Note: test requires that publisher device support 2 publish sessions concurrently,
        and subscriber device support 2 subscribe sessions concurrently.
        The test will be skipped if the devices are not capable.

        Args:
            type_x, type_y: A list of [ptype, stype] of the publish and subscribe
                      types for services X and Y respectively.
         """
        p_dut = self.ads[0]
        s_dut = self.ads[1]
        asserts.skip_if(
            autils.get_aware_capabilities(p_dut)["maxPublishes"] < 2
            or autils.get_aware_capabilities(s_dut)["maxPublishes"] < 2
            ,"Devices do not support 2 publish sessions"
            )
        SERVICE_NAME = "ServiceName"
        X_SERVICE_SSI = "ServiceSpecificInfoXXX"
        Y_SERVICE_SSI = "ServiceSpecificInfoYYY"
        # attach and wait for confirmation
        p_id = self._start_attach(p_dut)
        s_id = self._start_attach(s_dut)
        p_disc_id_x = p_dut.wifi_aware_snippet.wifiAwarePublish(
            p_id, autils.create_discovery_config(SERVICE_NAME, type_x[0], None, X_SERVICE_SSI)
                )
        p_dut.log.info('Created the DUT1 X publish session.')
        p_discovery = p_disc_id_x.waitAndGet(
            constants.DiscoverySessionCallbackMethodType.DISCOVER_RESULT)
        callback_name = p_discovery.data[_CALLBACK_NAME]
        asserts.assert_equal(
            constants.DiscoverySessionCallbackMethodType.PUBLISH_STARTED,
            callback_name,
            f'{p_dut} DUT1 X publish failed, got callback: {callback_name}.',
            )
        p_disc_id_y = p_dut.wifi_aware_snippet.wifiAwarePublish(
                p_id, autils.create_discovery_config(SERVICE_NAME, type_x[0], None, Y_SERVICE_SSI)
                )
        p_dut.log.info('Created the DUT1 Y publish session.')
        p_discovery = p_disc_id_y.waitAndGet(
            constants.DiscoverySessionCallbackMethodType.DISCOVER_RESULT)
        callback_name = p_discovery.data[_CALLBACK_NAME]
        asserts.assert_equal(
            constants.DiscoverySessionCallbackMethodType.PUBLISH_STARTED,
            callback_name,
            f'{p_dut} DUT1 Y publish failed, got callback: {callback_name}.',
            )
        # Subscriber: start subscribe session A
        s_disc_id_a = s_dut.wifi_aware_snippet.wifiAwareSubscribe(
            s_id, autils.create_discovery_config(SERVICE_NAME, None, type_x[1] )
            )
        s_dut.log.info('Created the DUT2 X subscribe session.')
        s_discovery = s_disc_id_a.waitAndGet(
                constants.DiscoverySessionCallbackMethodType.DISCOVER_RESULT,
                timeout=_DEFAULT_TIMEOUT)
        callback_name = s_discovery.data[_CALLBACK_NAME]
        asserts.assert_equal(
            constants.DiscoverySessionCallbackMethodType.SUBSCRIBE_STARTED,
            callback_name,
            f'{s_dut} DUT2 X subscribe failed, got callback: {callback_name}.',
            )
        # Subscriber: start subscribe session B
        s_disc_id_b = s_dut.wifi_aware_snippet.wifiAwareSubscribe(
                s_id, autils.create_discovery_config(SERVICE_NAME, None, type_y[1])
                )
        s_dut.log.info('Created the DUT2 Y subscribe session.')
        s_discovery = s_disc_id_b.waitAndGet(
            constants.DiscoverySessionCallbackMethodType.DISCOVER_RESULT)
        callback_name = s_discovery.data[_CALLBACK_NAME]
        asserts.assert_equal(
            constants.DiscoverySessionCallbackMethodType.SUBSCRIBE_STARTED,
            callback_name,
            f'{s_dut} DUT2 Y subscribe failed, got callback: {callback_name}.',
            )
        session_name = {"pub": {p_disc_id_x: "X", p_disc_id_y: "Y"},
                        "sub": {s_disc_id_a: "A", s_disc_id_b: "B"}}
        # Subscriber: subscribe session A & B wait for service discovery
        # Number of results on each session should be exactly 2
        results_a = {}
        for i in range(2):
            event = s_disc_id_a.waitAndGet(
                constants.DiscoverySessionCallbackMethodType.SERVICE_DISCOVERED)
            results_a[
                bytes(event.data[constants.WifiAwareSnippetParams.SERVICE_SPECIFIC_INFO]).decode("utf-8")] = event
        autils.callback_no_response(
            s_disc_id_a, constants.DiscoverySessionCallbackMethodType.SERVICE_DISCOVERED, 10, True
        )
        results_b = {}
        for i in range(2):
            event = s_disc_id_b.waitAndGet(
                constants.DiscoverySessionCallbackMethodType.SERVICE_DISCOVERED)
            results_b[
                bytes(event.data[constants.WifiAwareSnippetParams.SERVICE_SPECIFIC_INFO]).decode("utf-8")] = event
        autils.callback_no_response(
            s_disc_id_b, constants.DiscoverySessionCallbackMethodType.SERVICE_DISCOVERED, 10, True
        )
        s_a_peer_id_for_p_x = results_a[X_SERVICE_SSI].data[constants.WifiAwareSnippetParams.PEER_ID]
        s_a_peer_id_for_p_y = results_a[Y_SERVICE_SSI].data[constants.WifiAwareSnippetParams.PEER_ID]
        s_b_peer_id_for_p_x = results_b[X_SERVICE_SSI].data[constants.WifiAwareSnippetParams.PEER_ID]
        s_b_peer_id_for_p_y = results_b[Y_SERVICE_SSI].data[constants.WifiAwareSnippetParams.PEER_ID]

        # Message exchange between Publisher and Subscribe
        self.exchange_messages(p_dut, p_disc_id_x,
                               s_dut, s_disc_id_a, s_a_peer_id_for_p_x, session_name)

        self.exchange_messages(p_dut, p_disc_id_x,
                               s_dut, s_disc_id_b, s_b_peer_id_for_p_x, session_name)

        self.exchange_messages(p_dut, p_disc_id_y,
                               s_dut, s_disc_id_a, s_a_peer_id_for_p_y, session_name)

        self.exchange_messages(p_dut, p_disc_id_y,
                               s_dut, s_disc_id_b, s_b_peer_id_for_p_y, session_name)

    def run_service_discovery_on_service_lost(self, p_type, s_type):
        """
        Validate service lost callback will be receive on subscriber, when publisher stopped publish
        - p_dut running Publish
        - s_dut running subscribe
        - s_dut discover p_dut
        - p_dut stop publish
        - s_dut receive service lost callback

        Args:
            p_type: Publish discovery type
            s_type: Subscribe discovery type
        """
        p_dut = self.ads[0]
        s_dut = self.ads[1]
        # attach and wait for confirmation
        p_id = self._start_attach(p_dut)
        s_id = self._start_attach(s_dut)
        p_config = self.create_publish_config(
            p_dut.wifi_aware_snippet.getCharacteristics(),
            p_type,
            _PAYLOAD_SIZE_TYPICAL,
            ttl=0,
            term_ind_on=False,
            null_match=False,
            )
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
        s_config = self.create_subscribe_config(
            s_dut.wifi_aware_snippet.getCharacteristics(),
            s_type,
            _PAYLOAD_SIZE_TYPICAL,
            ttl=0,
            term_ind_on=False,
            null_match=True,
            )
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
            f'{s_dut} subscribe failed, got callback: {callback_name}.'
        )
        discovered_event = s_disc_id.waitAndGet(
            constants.DiscoverySessionCallbackMethodType.SERVICE_DISCOVERED)
        peer_id_on_sub = discovered_event.data[
            constants.WifiAwareSnippetParams.PEER_ID]
        # Publisher+Subscriber: Terminate sessions
        p_dut.wifi_aware_snippet.wifiAwareCloseDiscoverSession(
            p_disc_id.callback_id)
        time.sleep(10)
        service_lost_event = s_disc_id.waitAndGet(
            constants.DiscoverySessionCallbackMethodType.SESSION_CB_ON_SERVICE_LOST)
        asserts.assert_equal(peer_id_on_sub,
                             service_lost_event.data[constants.WifiAwareSnippetParams.PEER_ID])
        asserts.assert_equal(
            constants.EASON_PEER_NOT_VISIBLE,
            service_lost_event.data[constants.DiscoverySessionCallbackMethodType.SESSION_CB_KEY_LOST_REASON]
            )

    @ApiTest(
        apis=[
            'android.net.wifi.aware.WifiAwareManager#attach(android.net.wifi.aware.AttachCallback, android.net.wifi.aware.IdentityChangedListener, android.os.Handler)',
            'android.net.wifi.aware.WifiAwareSession#publish(android.net.wifi.aware.PublishConfig, android.net.wifi.aware.DiscoverySessionCallback, android.os.Handler)',
            'android.net.wifi.aware.WifiAwareSession#subscrible(android.net.wifi.aware.SubscribeConfig, android.net.wifi.aware.DiscoverySessionCallback, android.os.Handler)',
            'android.net.wifi.aware.PublishConfig.Builder#setPublishType(PublishConfig.PUBLISH_TYPE_UNSOLICITED)',
            'android.net.wifi.aware.SubscribeConfig.Builder#setSubscribeType(SubscribeConfig.SUBSCRIBE_TYPE_PASSIVE)',
        ]
    )

    def test_positive_unsolicited_passive_typical(self)-> None:
        """Functional test case / Discovery test cases / positive test case:
        - Unsolicited publish + passive subscribe
        - Typical payload fields size

        Verifies that discovery and message exchange succeeds.
        """
        self._positive_discovery_logic(
             _PUBLISH_TYPE_UNSOLICITED,
             _SUBSCRIBE_TYPE_PASSIVE,
             _PAYLOAD_SIZE_TYPICAL
            )

    @ApiTest(
        apis=[
            'android.net.wifi.aware.WifiAwareManager#attach(android.net.wifi.aware.AttachCallback, android.net.wifi.aware.IdentityChangedListener, android.os.Handler)',
            'android.net.wifi.aware.WifiAwareSession#publish(android.net.wifi.aware.PublishConfig, android.net.wifi.aware.DiscoverySessionCallback, android.os.Handler)',
            'android.net.wifi.aware.WifiAwareSession#subscrible(android.net.wifi.aware.SubscribeConfig, android.net.wifi.aware.DiscoverySessionCallback, android.os.Handler)',
            'android.net.wifi.aware.PublishConfig.Builder#setPublishType(PublishConfig.PUBLISH_TYPE_UNSOLICITED)',
            'android.net.wifi.aware.SubscribeConfig.Builder#setSubscribeType(SubscribeConfig.SUBSCRIBE_TYPE_PASSIVE)',
        ]
    )

    def test_positive_unsolicited_passive_min(self)-> None:
        """Functional test case / Discovery test cases / positive test case:
        - Unsolicited publish + passive subscribe
        - Minimal payload fields size

        Verifies that discovery and message exchange succeeds.
        """
        self._positive_discovery_logic(
             _PUBLISH_TYPE_UNSOLICITED,
             _SUBSCRIBE_TYPE_PASSIVE,
             _PAYLOAD_SIZE_MIN
            )

    @ApiTest(
        apis=[
            'android.net.wifi.aware.WifiAwareManager#attach(android.net.wifi.aware.AttachCallback, android.net.wifi.aware.IdentityChangedListener, android.os.Handler)',
            'android.net.wifi.aware.WifiAwareSession#publish(android.net.wifi.aware.PublishConfig, android.net.wifi.aware.DiscoverySessionCallback, android.os.Handler)',
            'android.net.wifi.aware.WifiAwareSession#subscrible(android.net.wifi.aware.SubscribeConfig, android.net.wifi.aware.DiscoverySessionCallback, android.os.Handler)',
            'android.net.wifi.aware.PublishConfig.Builder#setPublishType(PublishConfig.PUBLISH_TYPE_UNSOLICITED)',
            'android.net.wifi.aware.SubscribeConfig.Builder#setSubscribeType(SubscribeConfig.SUBSCRIBE_TYPE_PASSIVE)',
        ]
    )

    def test_positive_unsolicited_passive_max(self)-> None:
        """Functional test case / Discovery test cases / positive test case:
        - Unsolicited publish + passive subscribe
        - Maximal payload fields size

        Verifies that discovery and message exchange succeeds.
        """
        self._positive_discovery_logic(
             _PUBLISH_TYPE_UNSOLICITED,
             _SUBSCRIBE_TYPE_PASSIVE,
             _PAYLOAD_SIZE_MAX
            )

    @ApiTest(
        apis=[
            'android.net.wifi.aware.WifiAwareManager#attach(android.net.wifi.aware.AttachCallback, android.net.wifi.aware.IdentityChangedListener, android.os.Handler)',
            'android.net.wifi.aware.WifiAwareSession#publish(android.net.wifi.aware.PublishConfig, android.net.wifi.aware.DiscoverySessionCallback, android.os.Handler)',
            'android.net.wifi.aware.WifiAwareSession#subscrible(android.net.wifi.aware.SubscribeConfig, android.net.wifi.aware.DiscoverySessionCallback, android.os.Handler)',
            'android.net.wifi.aware.PublishConfig.Builder#setPublishType(PublishConfig.PUBLISH_TYPE_SOLICITED)',
            'android.net.wifi.aware.SubscribeConfig.Builder#setSubscribeType(SubscribeConfig.SUBSCRIBE_TYPE_ACTIVE)',
        ]
    )

    def test_positive_solicited_active_typical(self)-> None:
        """Functional test case / Discovery test cases / positive test case:
        - Solicited publish + active subscribe
        - Typical payload fields size

        Verifies that discovery and message exchange succeeds.
        """
        self._positive_discovery_logic(
             _PUBLISH_TYPE_SOLICITED,
             _SUBSCRIBE_TYPE_ACTIVE,
             _PAYLOAD_SIZE_TYPICAL
            )

    @ApiTest(
        apis=[
            'android.net.wifi.aware.WifiAwareManager#attach(android.net.wifi.aware.AttachCallback, android.net.wifi.aware.IdentityChangedListener, android.os.Handler)',
            'android.net.wifi.aware.WifiAwareSession#publish(android.net.wifi.aware.PublishConfig, android.net.wifi.aware.DiscoverySessionCallback, android.os.Handler)',
            'android.net.wifi.aware.WifiAwareSession#subscrible(android.net.wifi.aware.SubscribeConfig, android.net.wifi.aware.DiscoverySessionCallback, android.os.Handler)',
            'android.net.wifi.aware.PublishConfig.Builder#setPublishType(PublishConfig.PUBLISH_TYPE_SOLICITED)',
            'android.net.wifi.aware.SubscribeConfig.Builder#setSubscribeType(SubscribeConfig.SUBSCRIBE_TYPE_ACTIVE)',
        ]
    )

    def test_positive_solicited_active_min(self)-> None:
        """Functional test case / Discovery test cases / positive test case:
        - Solicited publish + active subscribe
        - Minimal payload fields size

        Verifies that discovery and message exchange succeeds.
        """
        self._positive_discovery_logic(
             _PUBLISH_TYPE_SOLICITED,
             _SUBSCRIBE_TYPE_ACTIVE,
             _PAYLOAD_SIZE_MIN
            )

    @ApiTest(
        apis=[
            'android.net.wifi.aware.WifiAwareManager#attach(android.net.wifi.aware.AttachCallback, android.net.wifi.aware.IdentityChangedListener, android.os.Handler)',
            'android.net.wifi.aware.WifiAwareSession#publish(android.net.wifi.aware.PublishConfig, android.net.wifi.aware.DiscoverySessionCallback, android.os.Handler)',
            'android.net.wifi.aware.WifiAwareSession#subscrible(android.net.wifi.aware.SubscribeConfig, android.net.wifi.aware.DiscoverySessionCallback, android.os.Handler)',
            'android.net.wifi.aware.PublishConfig.Builder#setPublishType(PublishConfig.PUBLISH_TYPE_SOLICITED)',
            'android.net.wifi.aware.SubscribeConfig.Builder#setSubscribeType(SubscribeConfig.SUBSCRIBE_TYPE_ACTIVE)',
        ]
    )

    def test_positive_solicited_active_max(self)-> None:
        """Functional test case / Discovery test cases / positive test case:
        - Solicited publish + active subscribe
        - Maximal payload fields size

        Verifies that discovery and message exchange succeeds.
        """
        self._positive_discovery_logic(
             _PUBLISH_TYPE_SOLICITED,
             _SUBSCRIBE_TYPE_ACTIVE,
             _PAYLOAD_SIZE_MAX
            )

    #######################################
    # TTL tests key:
    #
    # names is: test_ttl_<pub_type|sub_type>_<term_ind>
    # where:
    #
    # pub_type: Type of publish discovery session: unsolicited or solicited.
    # sub_type: Type of subscribe discovery session: passive or active.
    # term_ind: ind_on or ind_off
    #######################################

    @ApiTest(
        apis=[
            'android.net.wifi.aware.WifiAwareManager#attach(android.net.wifi.aware.AttachCallback, android.net.wifi.aware.IdentityChangedListener, android.os.Handler)',
            'android.net.wifi.aware.WifiAwareSession#publish(android.net.wifi.aware.PublishConfig, android.net.wifi.aware.DiscoverySessionCallback, android.os.Handler)',
            'android.net.wifi.aware.WifiAwareSession#subscrible(android.net.wifi.aware.SubscribeConfig, android.net.wifi.aware.DiscoverySessionCallback, android.os.Handler)',
            'android.net.wifi.aware.PublishConfig.Builder#setPublishType(PublishConfig.PUBLISH_TYPE_UNSOLICITED)',
            'android.net.wifi.aware.PublishConfig.Builder#setTerminateNotificationEnabled(PublishConfig.mEnableTerminateNotification)',
        ]
    )

    def test_ttl_unsolicited_ind_on(self)-> None:
        """Functional test case / Discovery test cases / TTL test case:
        - Unsolicited publish
        - Termination indication enabled
        """
        self.positive_ttl_test_utility(
            is_publish=True,
            ptype=_PUBLISH_TYPE_UNSOLICITED,
            stype=None,
            term_ind_on=True)

    @ApiTest(
        apis=[
            'android.net.wifi.aware.WifiAwareManager#attach(android.net.wifi.aware.AttachCallback, android.net.wifi.aware.IdentityChangedListener, android.os.Handler)',
            'android.net.wifi.aware.WifiAwareSession#publish(android.net.wifi.aware.PublishConfig, android.net.wifi.aware.DiscoverySessionCallback, android.os.Handler)',
            'android.net.wifi.aware.WifiAwareSession#subscrible(android.net.wifi.aware.SubscribeConfig, android.net.wifi.aware.DiscoverySessionCallback, android.os.Handler)',
            'android.net.wifi.aware.PublishConfig.Builder#setPublishType(PublishConfig.PUBLISH_TYPE_UNSOLICITED)',
            'android.net.wifi.aware.PublishConfig.Builder#setTerminateNotificationEnabled(PublishConfig.mEnableTerminateNotification)',
        ]
    )

    def test_ttl_unsolicited_ind_off(self)-> None:
        """Functional test case / Discovery test cases / TTL test case:
        - Unsolicited publish
        - Termination indication disabled
        """
        self.positive_ttl_test_utility(
            is_publish=True,
            ptype=_PUBLISH_TYPE_UNSOLICITED,
            stype=None,
            term_ind_on=False)

    @ApiTest(
        apis=[
            'android.net.wifi.aware.WifiAwareManager#attach(android.net.wifi.aware.AttachCallback, android.net.wifi.aware.IdentityChangedListener, android.os.Handler)',
            'android.net.wifi.aware.WifiAwareSession#publish(android.net.wifi.aware.PublishConfig, android.net.wifi.aware.DiscoverySessionCallback, android.os.Handler)',
            'android.net.wifi.aware.WifiAwareSession#subscrible(android.net.wifi.aware.SubscribeConfig, android.net.wifi.aware.DiscoverySessionCallback, android.os.Handler)',
            'android.net.wifi.aware.PublishConfig.Builder#setPublishType(PublishConfig.PUBLISH_TYPE_SOLICITED)',
            'android.net.wifi.aware.PublishConfig.Builder#setTerminateNotificationEnabled(PublishConfig.mEnableTerminateNotification)',
        ]
    )

    def test_ttl_solicited_ind_on(self)-> None:
        """Functional test case / Discovery test cases / TTL test case:
        - Solicited publish
        - Termination indication enabled
        """
        self.positive_ttl_test_utility(
            is_publish=True,
            ptype=_PUBLISH_TYPE_SOLICITED,
            stype=None,
            term_ind_on=True)

    @ApiTest(
        apis=[
            'android.net.wifi.aware.WifiAwareManager#attach(android.net.wifi.aware.AttachCallback, android.net.wifi.aware.IdentityChangedListener, android.os.Handler)',
            'android.net.wifi.aware.WifiAwareSession#publish(android.net.wifi.aware.PublishConfig, android.net.wifi.aware.DiscoverySessionCallback, android.os.Handler)',
            'android.net.wifi.aware.WifiAwareSession#subscrible(android.net.wifi.aware.SubscribeConfig, android.net.wifi.aware.DiscoverySessionCallback, android.os.Handler)',
            'android.net.wifi.aware.PublishConfig.Builder#setPublishType(PublishConfig.PUBLISH_TYPE_SOLICITED)',
            'android.net.wifi.aware.PublishConfig.Builder#setTerminateNotificationEnabled(PublishConfig.mEnableTerminateNotification)',
        ]
    )

    def test_ttl_solicited_ind_off(self)-> None:
        """Functional test case / Discovery test cases / TTL test case:
        - Solicited publish
        - Termination indication disabled
        """
        self.positive_ttl_test_utility(
            is_publish=True,
            ptype=_PUBLISH_TYPE_SOLICITED,
            stype=None,
            term_ind_on=False)

    @ApiTest(
        apis=[
            'android.net.wifi.aware.WifiAwareManager#attach(android.net.wifi.aware.AttachCallback, android.net.wifi.aware.IdentityChangedListener, android.os.Handler)',
            'android.net.wifi.aware.WifiAwareSession#publish(android.net.wifi.aware.PublishConfig, android.net.wifi.aware.DiscoverySessionCallback, android.os.Handler)',
            'android.net.wifi.aware.WifiAwareSession#subscrible(android.net.wifi.aware.SubscribeConfig, android.net.wifi.aware.DiscoverySessionCallback, android.os.Handler)',
            'android.net.wifi.aware.SubscribeConfig.Builder#setSubscribeType(SubscribeConfig.SUBSCRIBE_TYPE_PASSIVE)',
            'android.net.wifi.aware.SubscribeConfig.Builder#setTerminateNotificationEnabled(SubscribeConfig.mEnableTerminateNotification)',
        ]
    )

    def test_ttl_passive_ind_on(self)-> None:
        """Functional test case / Discovery test cases / TTL test case:
        - Passive subscribe
        - Termination indication enabled
        """
        self.positive_ttl_test_utility(
            is_publish=False,
            ptype=None,
            stype=_SUBSCRIBE_TYPE_PASSIVE,
            term_ind_on=True)

    @ApiTest(
        apis=[
            'android.net.wifi.aware.WifiAwareManager#attach(android.net.wifi.aware.AttachCallback, android.net.wifi.aware.IdentityChangedListener, android.os.Handler)',
            'android.net.wifi.aware.WifiAwareSession#publish(android.net.wifi.aware.PublishConfig, android.net.wifi.aware.DiscoverySessionCallback, android.os.Handler)',
            'android.net.wifi.aware.WifiAwareSession#subscrible(android.net.wifi.aware.SubscribeConfig, android.net.wifi.aware.DiscoverySessionCallback, android.os.Handler)',
            'android.net.wifi.aware.SubscribeConfig.Builder#setSubscribeType(SubscribeConfig.SUBSCRIBE_TYPE_PASSIVE)',
            'android.net.wifi.aware.SubscribeConfig.Builder#setTerminateNotificationEnabled(SubscribeConfig.mEnableTerminateNotification)',
        ]
    )

    def test_ttl_passive_ind_off(self)-> None:
        """Functional test case / Discovery test cases / TTL test case:
        - Passive subscribe
        - Termination indication disabled
        """
        self.positive_ttl_test_utility(
            is_publish=False,
            ptype=None,
            stype=_SUBSCRIBE_TYPE_PASSIVE,
            term_ind_on=False)

    @ApiTest(
        apis=[
            'android.net.wifi.aware.WifiAwareManager#attach(android.net.wifi.aware.AttachCallback, android.net.wifi.aware.IdentityChangedListener, android.os.Handler)',
            'android.net.wifi.aware.WifiAwareSession#publish(android.net.wifi.aware.PublishConfig, android.net.wifi.aware.DiscoverySessionCallback, android.os.Handler)',
            'android.net.wifi.aware.WifiAwareSession#subscrible(android.net.wifi.aware.SubscribeConfig, android.net.wifi.aware.DiscoverySessionCallback, android.os.Handler)',
            'android.net.wifi.aware.SubscribeConfig.Builder#setSubscribeType(SubscribeConfig.SUBSCRIBE_TYPE_PASSIVE)',
            'android.net.wifi.aware.SubscribeConfig.Builder#setTerminateNotificationEnabled(SubscribeConfig.mEnableTerminateNotification)',
        ]
    )

    def test_ttl_active_ind_on(self)-> None:
        """Functional test case / Discovery test cases / TTL test case:
        - Active subscribe
        - Termination indication enabled
        """
        self.positive_ttl_test_utility(
            is_publish=False,
            ptype=None,
            stype=_SUBSCRIBE_TYPE_ACTIVE,
            term_ind_on=True)

    @ApiTest(
        apis=[
            'android.net.wifi.aware.WifiAwareManager#attach(android.net.wifi.aware.AttachCallback, android.net.wifi.aware.IdentityChangedListener, android.os.Handler)',
            'android.net.wifi.aware.WifiAwareSession#publish(android.net.wifi.aware.PublishConfig, android.net.wifi.aware.DiscoverySessionCallback, android.os.Handler)',
            'android.net.wifi.aware.WifiAwareSession#subscrible(android.net.wifi.aware.SubscribeConfig, android.net.wifi.aware.DiscoverySessionCallback, android.os.Handler)',
            'android.net.wifi.aware.SubscribeConfig.Builder#setSubscribeType(SubscribeConfig.SUBSCRIBE_TYPE_PASSIVE)',
            'android.net.wifi.aware.SubscribeConfig.Builder#setTerminateNotificationEnabled(SubscribeConfig.mEnableTerminateNotification)',
        ]
    )

    def test_ttl_active_ind_off(self)-> None:
        """Functional test case / Discovery test cases / TTL test case:
        - Active subscribe
        - Termination indication disabled
        """
        self.positive_ttl_test_utility(
            is_publish=False,
            ptype=None,
            stype=_SUBSCRIBE_TYPE_ACTIVE,
            term_ind_on=False)

    #######################################
    # Mismatched discovery session type tests key:
    #
    # names is: test_mismatch_service_type_<pub_type>_<sub_type>
    # where:
    #
    # pub_type: Type of publish discovery session: unsolicited or solicited.
    # sub_type: Type of subscribe discovery session: passive or active.
    #######################################

    @ApiTest(
        apis=[
            'android.net.wifi.aware.WifiAwareManager#attach(android.net.wifi.aware.AttachCallback, android.net.wifi.aware.IdentityChangedListener, android.os.Handler)',
            'android.net.wifi.aware.WifiAwareSession#publish(android.net.wifi.aware.PublishConfig, android.net.wifi.aware.DiscoverySessionCallback, android.os.Handler)',
            'android.net.wifi.aware.WifiAwareSession#subscrible(android.net.wifi.aware.SubscribeConfig, android.net.wifi.aware.DiscoverySessionCallback, android.os.Handler)',
            'android.net.wifi.aware.PublishConfig.Builder#setPublishType(PublishConfig.PUBLISH_TYPE_UNSOLICITED)',
            'android.net.wifi.aware.SubscribeConfig.Builder#setSubscribeType(SubscribeConfig.SUBSCRIBE_TYPE_ACTIVE)',
            'android.net.wifi.aware.SubscribeConfig.Builder#setTerminateNotificationEnabled(SubscribeConfig.mEnableTerminateNotification)',
            'android.net.wifi.aware.PublishConfig.Builder#setTerminateNotificationEnabled(PublishConfig.mEnableTerminateNotification)',
        ]
    )

    def test_mismatch_service_type_unsolicited_active(self):
        """Functional test case / Discovery test cases / Mismatch service name
    - Unsolicited publish
    - Active subscribe
    """
        self.discovery_mismatch_test_utility(
            is_expected_to_pass=True,
            p_type=_PUBLISH_TYPE_UNSOLICITED,
            s_type=_SUBSCRIBE_TYPE_ACTIVE)

    @ApiTest(
        apis=[
            'android.net.wifi.aware.WifiAwareManager#attach(android.net.wifi.aware.AttachCallback, android.net.wifi.aware.IdentityChangedListener, android.os.Handler)',
            'android.net.wifi.aware.WifiAwareSession#publish(android.net.wifi.aware.PublishConfig, android.net.wifi.aware.DiscoverySessionCallback, android.os.Handler)',
            'android.net.wifi.aware.WifiAwareSession#subscrible(android.net.wifi.aware.SubscribeConfig, android.net.wifi.aware.DiscoverySessionCallback, android.os.Handler)',
            'android.net.wifi.aware.PublishConfig.Builder#setPublishType(PublishConfig.PUBLISH_TYPE_SOLICITED)',
            'android.net.wifi.aware.SubscribeConfig.Builder#setSubscribeType(SubscribeConfig.SUBSCRIBE_TYPE_PASSIVE)',
            'android.net.wifi.aware.SubscribeConfig.Builder#setTerminateNotificationEnabled(SubscribeConfig.mEnableTerminateNotification)',
            'android.net.wifi.aware.PublishConfig.Builder#setTerminateNotificationEnabled(PublishConfig.mEnableTerminateNotification)',
        ]
    )

    def test_mismatch_service_type_solicited_passive(self):
        """Functional test case / Discovery test cases / Mismatch service name
    - Unsolicited publish
    - Active subscribe
    """
        self.discovery_mismatch_test_utility(
            is_expected_to_pass=False,
            p_type = _PUBLISH_TYPE_SOLICITED,
            s_type = _SUBSCRIBE_TYPE_PASSIVE)

    ######################################
    # Mismatched service name tests key:
    #
    # names is: test_mismatch_service_name_<pub_type>_<sub_type>
    # where:
    #
    # pub_type: Type of publish discovery session: unsolicited or solicited.
    # sub_type: Type of subscribe discovery session: passive or active.
    #######################################

    @ApiTest(
        apis=[
            'android.net.wifi.aware.WifiAwareManager#attach(android.net.wifi.aware.AttachCallback, android.net.wifi.aware.IdentityChangedListener, android.os.Handler)',
            'android.net.wifi.aware.WifiAwareSession#publish(android.net.wifi.aware.PublishConfig, android.net.wifi.aware.DiscoverySessionCallback, android.os.Handler)',
            'android.net.wifi.aware.WifiAwareSession#subscrible(android.net.wifi.aware.SubscribeConfig, android.net.wifi.aware.DiscoverySessionCallback, android.os.Handler)',
            'android.net.wifi.aware.PublishConfig.Builder#setPublishType(PublishConfig.PUBLISH_TYPE_UNSOLICITED,)',
            'android.net.wifi.aware.SubscribeConfig.Builder#setSubscribeType(SubscribeConfig.SUBSCRIBE_TYPE_PASSIVE)',
            'android.net.wifi.aware.SubscribeConfig.Builder#setTerminateNotificationEnabled(SubscribeConfig.mEnableTerminateNotification)',
            'android.net.wifi.aware.PublishConfig.Builder#setTerminateNotificationEnabled(PublishConfig.mEnableTerminateNotification)',
        ]
    )

    def test_mismatch_service_name_unsolicited_passive(self):
        """Functional test case / Discovery test cases / Mismatch service name
    - Unsolicited publish
    - Passive subscribe
    """
        self.discovery_mismatch_test_utility(
            is_expected_to_pass=False,
            p_type=_PUBLISH_TYPE_UNSOLICITED,
            s_type=_SUBSCRIBE_TYPE_PASSIVE,
            p_service_name="GoogleTestServiceXXX",
            s_service_name="GoogleTestServiceYYY")

    @ApiTest(
        apis=[
            'android.net.wifi.aware.WifiAwareManager#attach(android.net.wifi.aware.AttachCallback, android.net.wifi.aware.IdentityChangedListener, android.os.Handler)',
            'android.net.wifi.aware.WifiAwareSession#publish(android.net.wifi.aware.PublishConfig, android.net.wifi.aware.DiscoverySessionCallback, android.os.Handler)',
            'android.net.wifi.aware.WifiAwareSession#subscrible(android.net.wifi.aware.SubscribeConfig, android.net.wifi.aware.DiscoverySessionCallback, android.os.Handler)',
            'android.net.wifi.aware.PublishConfig.Builder#setPublishType(PublishConfig.PUBLISH_TYPE_SOLICITED,)',
            'android.net.wifi.aware.SubscribeConfig.Builder#setSubscribeType(SubscribeConfig.SUBSCRIBE_TYPE_ACTIVE)',
            'android.net.wifi.aware.SubscribeConfig.Builder#setTerminateNotificationEnabled(SubscribeConfig.mEnableTerminateNotification)',
            'android.net.wifi.aware.PublishConfig.Builder#setTerminateNotificationEnabled(PublishConfig.mEnableTerminateNotification)',
        ]
    )

    def test_mismatch_service_name_solicited_active(self):
        """Functional test case / Discovery test cases / Mismatch service name
    - Solicited publish
    - Active subscribe
    """
        self.discovery_mismatch_test_utility(
            is_expected_to_pass=False,
            p_type=_PUBLISH_TYPE_SOLICITED,
            s_type=_SUBSCRIBE_TYPE_ACTIVE,
            p_service_name="GoogleTestServiceXXX",
            s_service_name="GoogleTestServiceYYY")

    #######################################
    # Mismatched discovery match filter tests key:
    #
    # names is: test_mismatch_match_filter_<pub_type>_<sub_type>
    # where:
    #
    # pub_type: Type of publish discovery session: unsolicited or solicited.
    # sub_type: Type of subscribe discovery session: passive or active.
    #######################################

    @ApiTest(
        apis=[
            'android.net.wifi.aware.WifiAwareManager#attach(android.net.wifi.aware.AttachCallback, android.net.wifi.aware.IdentityChangedListener, android.os.Handler)',
            'android.net.wifi.aware.WifiAwareSession#publish(android.net.wifi.aware.PublishConfig, android.net.wifi.aware.DiscoverySessionCallback, android.os.Handler)',
            'android.net.wifi.aware.WifiAwareSession#subscrible(android.net.wifi.aware.SubscribeConfig, android.net.wifi.aware.DiscoverySessionCallback, android.os.Handler)',
            'android.net.wifi.aware.PublishConfig.Builder#setPublishType(PublishConfig.PUBLISH_TYPE_UNSOLICITED,)',
            'android.net.wifi.aware.SubscribeConfig.Builder#setSubscribeType(SubscribeConfig.SUBSCRIBE_TYPE_PASSIVE)',
            'android.net.wifi.aware.SubscribeConfig.Builder#setTerminateNotificationEnabled(SubscribeConfig.mEnableTerminateNotification)',
            'android.net.wifi.aware.PublishConfig.Builder#setTerminateNotificationEnabled(PublishConfig.mEnableTerminateNotification)',
        ]
    )

    def test_mismatch_match_filter_unsolicited_passive(self):
        """Functional test case / Discovery test cases / Mismatch match filter
    - Unsolicited publish
    - Passive subscribe
    """
        self.discovery_mismatch_test_utility(
            is_expected_to_pass=False,
            p_type=_PUBLISH_TYPE_UNSOLICITED,
            s_type=_SUBSCRIBE_TYPE_PASSIVE,
            p_mf_1="hello there string",
            s_mf_1="goodbye there string")

    @ApiTest(
        apis=[
            'android.net.wifi.aware.WifiAwareManager#attach(android.net.wifi.aware.AttachCallback, android.net.wifi.aware.IdentityChangedListener, android.os.Handler)',
            'android.net.wifi.aware.WifiAwareSession#publish(android.net.wifi.aware.PublishConfig, android.net.wifi.aware.DiscoverySessionCallback, android.os.Handler)',
            'android.net.wifi.aware.WifiAwareSession#subscrible(android.net.wifi.aware.SubscribeConfig, android.net.wifi.aware.DiscoverySessionCallback, android.os.Handler)',
            'android.net.wifi.aware.PublishConfig.Builder#setPublishType(PublishConfig.PUBLISH_TYPE_SOLICITED,)',
            'android.net.wifi.aware.SubscribeConfig.Builder#setSubscribeType(SubscribeConfig.SUBSCRIBE_TYPE_ACTIVE,)',
            'android.net.wifi.aware.SubscribeConfig.Builder#setTerminateNotificationEnabled(SubscribeConfig.mEnableTerminateNotification)',
            'android.net.wifi.aware.PublishConfig.Builder#setTerminateNotificationEnabled(PublishConfig.mEnableTerminateNotification)',
        ]
    )

    def test_mismatch_match_filter_solicited_active(self):
        """Functional test case / Discovery test cases / Mismatch match filter
    - Solicited publish
    - Active subscribe
    """
        self.discovery_mismatch_test_utility(
            is_expected_to_pass=False,
            p_type=_PUBLISH_TYPE_SOLICITED,
            s_type=_SUBSCRIBE_TYPE_ACTIVE,
            p_mf_1="hello there string",
            s_mf_1="goodbye there string")

    #########################################################
    # Multiple concurrent services
    #######################################

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

    def test_multiple_concurrent_services_both_unsolicited_passive(self):
        """Validate multiple concurrent discovery sessions running on both devices.
    - DUT1 & DUT2 running Publish for X
    - DUT1 & DUT2 running Publish for Y
    - DUT1 Subscribes for X
    - DUT2 Subscribes for Y
    Message exchanges.

    Both sessions are Unsolicited/Passive.

    Note: test requires that devices support 2 publish sessions concurrently.
    The test will be skipped if the devices are not capable.
    """
        self.run_multiple_concurrent_services(
            type_x=[
                _PUBLISH_TYPE_UNSOLICITED,
                _SUBSCRIBE_TYPE_PASSIVE
            ],
            type_y=[
                _PUBLISH_TYPE_UNSOLICITED,
                _SUBSCRIBE_TYPE_PASSIVE
            ])

    @ApiTest(
        apis=[
            'android.net.wifi.aware.WifiAwareManager#attach(android.net.wifi.aware.AttachCallback, android.net.wifi.aware.IdentityChangedListener, android.os.Handler)',
            'android.net.wifi.aware.WifiAwareSession#publish(android.net.wifi.aware.PublishConfig, android.net.wifi.aware.DiscoverySessionCallback, android.os.Handler)',
            'android.net.wifi.aware.WifiAwareSession#subscrible(android.net.wifi.aware.SubscribeConfig, android.net.wifi.aware.DiscoverySessionCallback, android.os.Handler)',
            'android.net.wifi.aware.PublishConfig.Builder#setPublishType(PublishConfig.PUBLISH_TYPE_SOLICITED,)',
            'android.net.wifi.aware.SubscribeConfig.Builder#setSubscribeType(SubscribeConfig.SUBSCRIBE_TYPE_ACTIVE)',
            'android.net.wifi.aware.DiscoverySession#sendMessage(int, byte[])',
        ]
    )

    def test_multiple_concurrent_services_both_solicited_active(self):
        """Validate multiple concurrent discovery sessions running on both devices.
    - DUT1 & DUT2 running Publish for X
    - DUT1 & DUT2 running Publish for Y
    - DUT1 Subscribes for X
    - DUT2 Subscribes for Y
    Message exchanges.

    Both sessions are Solicited/Active.

    Note: test requires that devices support 2 publish sessions concurrently.
    The test will be skipped if the devices are not capable.
    """
        self.run_multiple_concurrent_services(
            type_x=[
                _PUBLISH_TYPE_SOLICITED,
                _SUBSCRIBE_TYPE_ACTIVE
            ],
            type_y=[
                _PUBLISH_TYPE_SOLICITED, _SUBSCRIBE_TYPE_ACTIVE
            ])

    @ApiTest(
        apis=[
            'android.net.wifi.aware.WifiAwareManager#attach(android.net.wifi.aware.AttachCallback, android.net.wifi.aware.IdentityChangedListener, android.os.Handler)',
            'android.net.wifi.aware.WifiAwareSession#publish(android.net.wifi.aware.PublishConfig, android.net.wifi.aware.DiscoverySessionCallback, android.os.Handler)',
            'android.net.wifi.aware.WifiAwareSession#subscrible(android.net.wifi.aware.SubscribeConfig, android.net.wifi.aware.DiscoverySessionCallback, android.os.Handler)',
            'android.net.wifi.aware.PublishConfig.Builder#setPublishType(PublishConfig.PUBLISH_TYPE_SOLICITED,)',
            'android.net.wifi.aware.SubscribeConfig.Builder#setSubscribeType(SubscribeConfig.SUBSCRIBE_TYPE_ACTIVE)',
            'android.net.wifi.aware.PublishConfig.Builder#setPublishType(PublishConfig.PUBLISH_TYPE_UNSOLICITED,)',
            'android.net.wifi.aware.SubscribeConfig.Builder#setSubscribeType(SubscribeConfig.SUBSCRIBE_TYPE_PASSIVE)',
            'android.net.wifi.aware.DiscoverySession#sendMessage(int, byte[])',
        ]
    )

    def test_multiple_concurrent_services_mix_unsolicited_solicited(self):
        """Validate multiple concurrent discovery sessions running on both devices.
    - DUT1 & DUT2 running Publish for X
    - DUT1 & DUT2 running Publish for Y
    - DUT1 Subscribes for X
    - DUT2 Subscribes for Y
    Message exchanges.

    Session A is Unsolicited/Passive.
    Session B is Solicited/Active.

    Note: test requires that devices support 2 publish sessions concurrently.
    The test will be skipped if the devices are not capable.
    """
        self.run_multiple_concurrent_services(
            type_x=[
                _PUBLISH_TYPE_UNSOLICITED,
                _SUBSCRIBE_TYPE_PASSIVE
            ],
            type_y=[
                _PUBLISH_TYPE_SOLICITED, _SUBSCRIBE_TYPE_ACTIVE
            ])

    #########################################################
    # Multiple concurrent services with diff ssi
    #########################################################

    @ApiTest(
        apis=[
            'android.net.wifi.aware.WifiAwareManager#attach(android.net.wifi.aware.AttachCallback, android.net.wifi.aware.IdentityChangedListener, android.os.Handler)',
            'android.net.wifi.aware.WifiAwareSession#publish(android.net.wifi.aware.PublishConfig, android.net.wifi.aware.DiscoverySessionCallback, android.os.Handler)',
            'android.net.wifi.aware.WifiAwareSession#subscrible(android.net.wifi.aware.SubscribeConfig, android.net.wifi.aware.DiscoverySessionCallback, android.os.Handler)',
            'android.net.wifi.aware.PublishConfig.Builder#setPublishType(PublishConfig.PUBLISH_TYPE_UNSOLICITED,)',
            'android.net.wifi.aware.SubscribeConfig.Builder#setSubscribeType(SubscribeConfig.SUBSCRIBE_TYPE_PASSIVE)',
            'android.net.wifi.aware.DiscoverySession#sendMessage(int, byte[])',
        ]
    )

    def test_multiple_concurrent_services_diff_ssi_unsolicited_passive(self):
        """Multi service test on same service name but different Service Specific Info
        - Unsolicited publish
        - Passive subscribe
        """
        self.run_multiple_concurrent_services_same_name_diff_ssi(
            type_x=[_PUBLISH_TYPE_UNSOLICITED, _SUBSCRIBE_TYPE_PASSIVE],
            type_y=[_PUBLISH_TYPE_UNSOLICITED, _SUBSCRIBE_TYPE_PASSIVE])

    @ApiTest(
        apis=[
            'android.net.wifi.aware.WifiAwareManager#attach(android.net.wifi.aware.AttachCallback, android.net.wifi.aware.IdentityChangedListener, android.os.Handler)',
            'android.net.wifi.aware.WifiAwareSession#publish(android.net.wifi.aware.PublishConfig, android.net.wifi.aware.DiscoverySessionCallback, android.os.Handler)',
            'android.net.wifi.aware.WifiAwareSession#subscrible(android.net.wifi.aware.SubscribeConfig, android.net.wifi.aware.DiscoverySessionCallback, android.os.Handler)',
            'android.net.wifi.aware.PublishConfig.Builder#setPublishType(PublishConfig.PUBLISH_TYPE_SOLICITED,)',
            'android.net.wifi.aware.SubscribeConfig.Builder#setSubscribeType(SubscribeConfig.SUBSCRIBE_TYPE_ACTIVE)',
            'android.net.wifi.aware.DiscoverySession#sendMessage(int, byte[])',
        ]
    )

    def test_multiple_concurrent_services_diff_ssi_solicited_active(self):
        """Multi service test on same service name but different Service Specific Info
        - Solicited publish
        - Active subscribe
        """
        self.run_multiple_concurrent_services_same_name_diff_ssi(
            type_x=[_PUBLISH_TYPE_SOLICITED, _SUBSCRIBE_TYPE_ACTIVE],
            type_y=[_PUBLISH_TYPE_SOLICITED, _SUBSCRIBE_TYPE_ACTIVE])

    #########################################################

    @ApiTest(
        apis=[
            'android.net.wifi.aware.WifiAwareManager#attach(android.net.wifi.aware.AttachCallback, android.net.wifi.aware.IdentityChangedListener, android.os.Handler)',
            'android.net.wifi.aware.WifiAwareSession#publish(android.net.wifi.aware.PublishConfig, android.net.wifi.aware.DiscoverySessionCallback, android.os.Handler)',
            'android.net.wifi.aware.WifiAwareSession#subscrible(android.net.wifi.aware.SubscribeConfig, android.net.wifi.aware.DiscoverySessionCallback, android.os.Handler)',
            'android.net.wifi.aware.PublishConfig.Builder#setPublishType(PublishConfig.PUBLISH_TYPE_UNSOLICITED,)',
            'android.net.wifi.aware.SubscribeConfig.Builder#setSubscribeType(SubscribeConfig.SUBSCRIBE_TYPE_PASSIVE)',
            'android.net.wifi.aware.DiscoverySession#sendMessage(int, byte[])',
        ]
    )

    def test_upper_lower_service_name_equivalence(self):
        """Validate that Service Name is case-insensitive. Publish a service name
        with mixed case, subscribe to the same service name with alternative case
        and verify that discovery happens."""
        p_dut = self.ads[0]
        s_dut = self.ads[1]

        pub_service_name = "GoogleAbCdEf"
        sub_service_name = "GoogleaBcDeF"
        p_config = autils.create_discovery_config(pub_service_name)
        p_config[constants.PUBLISH_TYPE] = _PUBLISH_TYPE_UNSOLICITED
        s_config = autils.create_discovery_config(sub_service_name)
        s_config[constants.SUBSCRIBE_TYPE] = _SUBSCRIBE_TYPE_PASSIVE
        self.create_discovery_pair(
            p_dut,
            s_dut,
            p_config,
            s_config)

    #########################################################
    # service discovery on service lost
    #########################################################

    @ApiTest(
        apis=[
            'android.net.wifi.aware.WifiAwareManager#attach(android.net.wifi.aware.AttachCallback, android.net.wifi.aware.IdentityChangedListener, android.os.Handler)',
            'android.net.wifi.aware.WifiAwareSession#publish(android.net.wifi.aware.PublishConfig, android.net.wifi.aware.DiscoverySessionCallback, android.os.Handler)',
            'android.net.wifi.aware.WifiAwareSession#subscrible(android.net.wifi.aware.SubscribeConfig, android.net.wifi.aware.DiscoverySessionCallback, android.os.Handler)',
            'android.net.wifi.aware.PublishConfig.Builder#setPublishType(PublishConfig.PUBLISH_TYPE_UNSOLICITED,)',
            'android.net.wifi.aware.SubscribeConfig.Builder#setSubscribeType(SubscribeConfig.SUBSCRIBE_TYPE_PASSIVE)',
        ]
    )

    def test_service_discovery_on_service_lost_unsolicited_passive(self):
        """
        Test service discovery lost with unsolicited publish and passive subscribe
        """
        self.run_service_discovery_on_service_lost(_PUBLISH_TYPE_UNSOLICITED,
                                                   _SUBSCRIBE_TYPE_PASSIVE)

    @ApiTest(
        apis=[
            'android.net.wifi.aware.WifiAwareManager#attach(android.net.wifi.aware.AttachCallback, android.net.wifi.aware.IdentityChangedListener, android.os.Handler)',
            'android.net.wifi.aware.WifiAwareSession#publish(android.net.wifi.aware.PublishConfig, android.net.wifi.aware.DiscoverySessionCallback, android.os.Handler)',
            'android.net.wifi.aware.WifiAwareSession#subscrible(android.net.wifi.aware.SubscribeConfig, android.net.wifi.aware.DiscoverySessionCallback, android.os.Handler)',
            'android.net.wifi.aware.PublishConfig.Builder#setPublishType(PublishConfig.PUBLISH_TYPE_SOLICITED,)',
            'android.net.wifi.aware.SubscribeConfig.Builder#setSubscribeType(SubscribeConfig.SUBSCRIBE_TYPE_ACTIVE)',
        ]
    )

    def test_service_discovery_on_service_lost_solicited_active(self):
        """
        Test service discovery lost with solicited publish and active subscribe
        """
        self.run_service_discovery_on_service_lost(_PUBLISH_TYPE_SOLICITED,
                                                   _SUBSCRIBE_TYPE_ACTIVE)


if __name__ == '__main__':
    # Take test args
    if '--' in sys.argv:
        index = sys.argv.index('--')
        sys.argv = sys.argv[:1] + sys.argv[index + 1:]

    test_runner.main()
