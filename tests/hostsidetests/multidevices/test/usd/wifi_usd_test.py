# Copyright (C) 2025 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#         http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
# Lint as: python3
"""Mobly test for bidirectional Wi-Fi USD functionality."""
import logging
import sys
import time

from android.platform.test.annotations import ApiTest
from mobly import asserts
from mobly import base_test
from mobly import test_runner
from mobly import utils
from mobly.controllers import android_device

WIFI_USD_SNIPPET_PATH = 'wifi_usd_snippet'
WIFI_SNIPPET_PACKAGE_NAME = 'com.google.snippet.wifi'
USD_SERVICE_NAME = '_test'
USD_SSI = "6677"
TEST_MESSAGE = 'hello from subscriber'
PUBLISHER_REPLY_MESSAGE = 'reply from publisher'
LARGE_TEST_MESSAGE = 'B' * 1000
LARGE_REPLY_MESSAGE = 'C' * 1000

class WifiUsdTest(base_test.BaseTestClass):

    def setup_class(self):
        """Sets up the devices and snippets for the test."""
        self.ads = self.register_controller(android_device, min_number=2)
        self.publisher = self.ads[0]
        self.subscriber = self.ads[1]

        def setup_device(device):
            device.load_snippet(WIFI_USD_SNIPPET_PATH, WIFI_SNIPPET_PACKAGE_NAME)
            device.adb.shell(['pm', 'grant', WIFI_SNIPPET_PACKAGE_NAME,
                              'android.permission.ACCESS_FINE_LOCATION'])
            device.adb.shell(['pm', 'grant', WIFI_SNIPPET_PACKAGE_NAME,
                              'android.permission.NEARBY_WIFI_DEVICES'])

        utils.concurrent_exec(
            setup_device,
            ((self.publisher,), (self.subscriber,)),
            max_workers=2,
            raise_on_exception=True,
        )

        # Sequentially check for USD support on each device.
        logging.info("Checking for Wi-Fi USD support...")
        for device in self.ads:
            asserts.abort_class_if(
                not device.wifi_usd_snippet.isUsdSubscriberSupported() or
                not device.wifi_usd_snippet.isUsdPublisherSupported(),
                f'{device.serial} does not support Wi-Fi USD.'
            )

        logging.info("Wi-Fi USD is supported on both devices. Proceeding with tests.")

    def on_fail(self, record):
        """Takes bug reports on test failure."""
        android_device.take_bug_reports(
            ads=[self.publisher, self.subscriber],
            test_name=record.test_name,
            begin_time=record.begin_time,
            destination=self.current_test_info.output_path
        )

    def setup_test(self):
        """Ensures a clean state before each test."""
        logging.info("--- SETUP: Stopping any lingering sessions ---")
        try:
            self.publisher.wifi_usd_snippet.stopUsdSessions()
            self.subscriber.wifi_usd_snippet.stopUsdSessions()
        except Exception as e:
            logging.warning(f"Could not stop sessions during setup: {e}")

    def teardown_test(self):
        """Ensures a clean state after each test."""
        logging.info("--- TEARDOWN: Stopping all sessions ---")
        self.publisher.wifi_usd_snippet.stopUsdSessions()
        self.subscriber.wifi_usd_snippet.stopUsdSessions()

    @ApiTest(
        apis=[
            'android.net.wifi.usd.UsdManager#publish(PublishConfig, java.util.concurrent.Executor, android.net.wifi.usd.PublishSessionCallback)',
            'android.net.wifi.usd.UsdManager#subscribe(SubscribeConfig, java.util.concurrent.Executor, android.net.wifi.usd.SubscribeSessionCallback)',
            'android.net.wifi.usd.PublishSession#sendMessage(int, byte[], java.util.concurrent.Executor, java.util.function.Consumer)',
        ]
    )
    def test_bidirectional_message_exchange(self):
        """Tests the full subscriber -> publisher -> subscriber message flow."""
        try:
            logging.info("Publisher starting session...")
            self.publisher.wifi_usd_snippet.startUsdPublishSession(
                USD_SERVICE_NAME, USD_SSI)
            logging.info("Publisher session started.")
            time.sleep(3)

            logging.info("Subscriber discovering and sending first message...")
            self.subscriber.wifi_usd_snippet.subscribeActiveAndSendMessage(
                USD_SERVICE_NAME, USD_SSI, TEST_MESSAGE)
            logging.info("Subscriber successfully sent the first message.")

            logging.info("Publisher waiting to receive message...")
            received_message = self.publisher.wifi_usd_snippet.receiveMessage()
            asserts.assert_is_not_none(received_message, "FAIL: Publisher did not receive a message.")
            asserts.assert_equal(received_message, TEST_MESSAGE, "FAIL: Message received by publisher is incorrect.")
            logging.info("SUCCESS: Publisher received correct message.")

            subscriber_peer_id = self.publisher.wifi_usd_snippet.getLastMessageSenderPeerId()
            asserts.assert_true(subscriber_peer_id != -1, "FAIL: Publisher did not learn subscriber's peer ID.")
            logging.info("Publisher sending reply to peer %d...", subscriber_peer_id)
            self.publisher.wifi_usd_snippet.sendMessageFromPublisher(
                subscriber_peer_id, PUBLISHER_REPLY_MESSAGE)
            logging.info("SUCCESS: Publisher sent reply without errors.")

            logging.info("Subscriber waiting to receive reply...")
            received_reply = self.subscriber.wifi_usd_snippet.receiveMessage()
            asserts.assert_is_not_none(received_reply, "FAIL: Subscriber did not receive a reply.")
            asserts.assert_equal(received_reply, PUBLISHER_REPLY_MESSAGE, "FAIL: Reply received by subscriber is incorrect.")
            logging.info("SUCCESS: Subscriber received correct reply: '%s'", received_reply)

        except Exception as e:
            asserts.fail(f"Test failed with an exception: {e}")
        logging.info("Bidirectional message exchange passed!")

    @ApiTest(
        apis=[
            'android.net.wifi.usd.UsdManager#publish(PublishConfig, java.util.concurrent.Executor,  android.net.wifi.usd.PublishSessionCallback)',
            'android.net.wifi.usd.UsdManager#subscribe(SubscribeConfig, java.util.concurrent.Executor, android.net.wifi.usd.SubscribeSessionCallback)',
            'android.net.wifi.usd.PublishSession#sendMessage(int, byte[], java.util.concurrent.Executor, java.util.function.Consumer)',
            'android.net.wifi.usd.SubscribeSession#sendMessage(int, byte[], java.util.concurrent.Executor, java.util.function.Consumer)',
        ]
    )
    def test_bidirectional_large_message_exchange(self):
        """Verifies a large message can be sent in both directions."""
        logging.info("Testing bidirectional large message exchange...")
        try:
            self.publisher.wifi_usd_snippet.startUsdPublishSession(
                USD_SERVICE_NAME, USD_SSI)
            logging.info("Publisher session started for large message test.")
            time.sleep(2)

            logging.info(f"Subscriber sending large message of size {len(LARGE_TEST_MESSAGE)} bytes...")
            self.subscriber.wifi_usd_snippet.subscribeActiveAndSendMessage(
                USD_SERVICE_NAME, USD_SSI, LARGE_TEST_MESSAGE)
            logging.info("Subscriber successfully sent large message.")

            logging.info("Publisher waiting to receive large message...")
            received_message = self.publisher.wifi_usd_snippet.receiveMessage()
            asserts.assert_is_not_none(received_message, "FAIL: Publisher did not receive the large message.")
            asserts.assert_equal(len(received_message), len(LARGE_TEST_MESSAGE), "FAIL: Received message size does not match.")
            logging.info("SUCCESS: Publisher received large message of correct size.")

            subscriber_peer_id = self.publisher.wifi_usd_snippet.getLastMessageSenderPeerId()
            asserts.assert_true(subscriber_peer_id != -1, "FAIL: Publisher did not learn subscriber's peer ID for reply.")
            logging.info("Publisher sending large reply to peer %d...", subscriber_peer_id)
            self.publisher.wifi_usd_snippet.sendMessageFromPublisher(
                subscriber_peer_id, LARGE_REPLY_MESSAGE)
            logging.info("SUCCESS: Publisher sent large reply.")

            logging.info("Subscriber waiting to receive large reply...")
            received_reply = self.subscriber.wifi_usd_snippet.receiveMessage()
            asserts.assert_is_not_none(received_reply, "FAIL: Subscriber did not receive the large reply.")
            asserts.assert_equal(len(received_reply), len(LARGE_REPLY_MESSAGE), "FAIL: Received reply size does not match.")
            logging.info("SUCCESS: Subscriber received large reply of correct size.")

        except Exception as e:
            asserts.fail(f"Failed during large message exchange: {e}")
        logging.info("Bidirectional large message exchange passed!")

    @ApiTest(
        apis=[
            'android.net.wifi.usd.PublishConfig.Builder#Builder(String)',
            'android.net.wifi.usd.SubscribeConfig.Builder#setSubscribeType(int)',
            'android.net.wifi.usd.SubscribeSessionCallback#onServiceDiscovered(android.net.wifi.usd.DiscoveryResult)',
            'android.net.wifi.usd.Config#SUBSCRIBE_TYPE_PASSIVE',
        ]
    )
    def test_passive_subscriber_exchange(self):
        """Tests message exchange with a PASSIVE subscriber."""
        logging.info("Testing with a PASSIVE subscriber...")
        try:
            self.publisher.wifi_usd_snippet.startUsdPublishSession(USD_SERVICE_NAME, USD_SSI)
            time.sleep(3)
            logging.info("Passive subscriber discovering and sending first message...")
            self.subscriber.wifi_usd_snippet.subscribePassiveAndSendMessage(
                USD_SERVICE_NAME, USD_SSI, TEST_MESSAGE)
            logging.info("Passive subscriber successfully sent the first message.")

            logging.info("Publisher waiting to receive message...")
            received_message = self.publisher.wifi_usd_snippet.receiveMessage()
            asserts.assert_is_not_none(received_message, "FAIL: Publisher did not receive a message in passive test.")
            asserts.assert_equal(received_message, TEST_MESSAGE, "FAIL: Message received by publisher is incorrect in passive test.")
            logging.info("SUCCESS: Publisher received correct message in passive test.")

            subscriber_peer_id = self.publisher.wifi_usd_snippet.getLastMessageSenderPeerId()
            asserts.assert_true(subscriber_peer_id != -1, "FAIL: Publisher did not learn subscriber's peer ID in passive test.")
            logging.info("Publisher sending reply to peer %d...", subscriber_peer_id)
            self.publisher.wifi_usd_snippet.sendMessageFromPublisher(
                subscriber_peer_id, PUBLISHER_REPLY_MESSAGE)
            logging.info("SUCCESS: Publisher sent reply without errors in passive test.")

            logging.info("Subscriber waiting to receive reply...")
            received_reply = self.subscriber.wifi_usd_snippet.receiveMessage()
            asserts.assert_is_not_none(received_reply, "FAIL: Subscriber did not receive a reply in passive test.")
            asserts.assert_equal(received_reply, PUBLISHER_REPLY_MESSAGE, "FAIL: Reply received by subscriber is incorrect in passive test.")
            logging.info("SUCCESS: Subscriber received correct reply: '%s'", received_reply)

        except Exception as e:
            asserts.fail(f"Passive subscriber test failed with an exception: {e}")
        logging.info("Passive subscriber bidirectional exchange passed!")

    @ApiTest(
        apis=[
            'android.net.wifi.usd.PublishConfig.Builder#Builder(String)',
            'android.net.wifi.usd.SubscribeConfig.Builder#setSubscribeType(int)',
            'android.net.wifi.usd.SubscribeSessionCallback#onServiceDiscovered(android.net.wifi.usd.DiscoveryResult)',
            'android.net.wifi.usd.Config#SUBSCRIBE_TYPE_ACTIVE',
        ]
    )
    def test_active_subscriber_exchange(self):
        """Tests message exchange with an ACTIVE subscriber."""
        logging.info("Testing with an ACTIVE subscriber...")
        try:
            self.publisher.wifi_usd_snippet.startUsdPublishSession(USD_SERVICE_NAME, USD_SSI)
            time.sleep(3)
            logging.info("Active subscriber discovering and sending first message...")
            self.subscriber.wifi_usd_snippet.subscribeActiveAndSendMessage(
                USD_SERVICE_NAME, USD_SSI, TEST_MESSAGE)
            logging.info("Active subscriber successfully sent the first message.")

            logging.info("Publisher waiting to receive message...")
            received_message = self.publisher.wifi_usd_snippet.receiveMessage()
            asserts.assert_is_not_none(received_message, "FAIL: Publisher did not receive a message in active test.")
            asserts.assert_equal(received_message, TEST_MESSAGE, "FAIL: Message received by publisher is incorrect in active test.")
            logging.info("SUCCESS: Publisher received correct message in active test.")

            subscriber_peer_id = self.publisher.wifi_usd_snippet.getLastMessageSenderPeerId()
            asserts.assert_true(subscriber_peer_id != -1, "FAIL: Publisher did not learn subscriber's peer ID in active test.")
            logging.info("Publisher sending reply to peer %d...", subscriber_peer_id)
            self.publisher.wifi_usd_snippet.sendMessageFromPublisher(
                subscriber_peer_id, PUBLISHER_REPLY_MESSAGE)
            logging.info("SUCCESS: Publisher sent reply without errors in active test.")

            logging.info("Subscriber waiting to receive reply...")
            received_reply = self.subscriber.wifi_usd_snippet.receiveMessage()
            asserts.assert_is_not_none(received_reply, "FAIL: Subscriber did not receive a reply in active test.")
            asserts.assert_equal(received_reply, PUBLISHER_REPLY_MESSAGE, "FAIL: Reply received by subscriber is incorrect in active test.")
            logging.info("SUCCESS: Subscriber received correct reply: '%s'", received_reply)

        except Exception as e:
            asserts.fail(f"Active subscriber test failed with an exception: {e}")
        logging.info("Active subscriber bidirectional exchange passed!")

if __name__ == '__main__':
    if '--' in sys.argv:
        index = sys.argv.index('--')
        sys.argv = sys.argv[:1] + sys.argv[index + 1:]
    test_runner.main()
