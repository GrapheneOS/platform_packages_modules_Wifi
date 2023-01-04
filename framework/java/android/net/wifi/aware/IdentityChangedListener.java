/*
 * Copyright (C) 2016 The Android Open Source Project
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

package android.net.wifi.aware;

import android.annotation.IntDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
/**
 * Base class for a listener which is called with the MAC address of the Aware interface whenever
 * it is changed. Change may be due to device joining a cluster, starting a cluster, or discovery
 * interface change (addresses are randomized at regular intervals). The implication is that
 * peers you've been communicating with may no longer recognize you and you need to re-establish
 * your identity - e.g. by starting a discovery session. This actual MAC address of the
 * interface may also be useful if the application uses alternative (non-Aware) discovery but needs
 * to set up a Aware connection. The provided Aware discovery interface MAC address can then be used
 * in {@link WifiAwareSession#createNetworkSpecifierOpen(int, byte[])} or
 * {@link WifiAwareSession#createNetworkSpecifierPassphrase(int, byte[], String)}.
 */
public class IdentityChangedListener {
    /** @hide */
    @IntDef(prefix = {"CLUSTER_CHANGE_EVENT_"}, value = {
        CLUSTER_CHANGE_EVENT_STARTED,
        CLUSTER_CHANGE_EVENT_JOINED
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface ClusterChangeEvent {}

    /**
     * Wi-Fi Aware cluster change event type when starting a cluster.
     */
    /** @hide */
    public static final int CLUSTER_CHANGE_EVENT_STARTED = 0;
    /**
     * Wi-Fi Aware cluster change event type when joining a cluster.
     */
    /** @hide */
    public static final int CLUSTER_CHANGE_EVENT_JOINED = 1;

    /**
     * @param mac The MAC address of the Aware discovery interface. The application must have the
     * {@link android.Manifest.permission#ACCESS_FINE_LOCATION} to get the actual MAC address,
     *            otherwise all 0's will be provided.
     */
    public void onIdentityChanged(byte[] mac) {
        /* empty */
    }
}
