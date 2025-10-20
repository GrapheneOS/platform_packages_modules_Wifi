/*
 * Copyright (C) 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.net.wifi.usd;

import android.net.wifi.IBooleanListener;
import android.net.wifi.usd.Characteristics;
import android.net.wifi.usd.IPublishSessionCallback;
import android.net.wifi.usd.ISubscribeSessionCallback;
import android.net.wifi.usd.PublishConfig;
import android.net.wifi.usd.SubscribeConfig;

/**
 * Interface that UsdService implements
 *
 * @hide
 */
interface IUsdManager {
    Characteristics getCharacteristics();
    void sendMessage(int sessionId, int peerId, in byte[] message, in IBooleanListener listener);
    void cancelSubscribe(int sessionId);
    void cancelPublish(int sessionId);
    void updatePublish(int sessionId, in byte[] ssi);
    void publish(in PublishConfig publishConfig, IPublishSessionCallback callback);
    void subscribe(in SubscribeConfig subscribeConfig, ISubscribeSessionCallback callback);
    void registerSubscriberStatusListener(IBooleanListener listener);
    void unregisterSubscriberStatusListener(IBooleanListener listener);
    void registerPublisherStatusListener(IBooleanListener listener);
    void unregisterPublisherStatusListener(IBooleanListener listener);
}
