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


package com.google.snippet.wifi.aware;

import android.net.MacAddress;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.wifi.aware.AwarePairingConfig;
import android.net.wifi.aware.PeerHandle;
import android.net.wifi.aware.PublishConfig;
import android.net.wifi.aware.SubscribeConfig;
import android.net.wifi.aware.WifiAwareDataPathSecurityConfig;
import android.net.wifi.aware.WifiAwareNetworkSpecifier;
import android.net.wifi.rtt.RangingRequest;
import android.util.Base64;

import androidx.annotation.NonNull;

import com.android.modules.utils.build.SdkLevel;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Deserializes JSONObject into data objects defined in Wi-Fi Aware API.
 */
public class WifiAwareJsonDeserializer {

    private static final String SERVICE_NAME = "service_name";
    private static final String SERVICE_SPECIFIC_INFO = "service_specific_info";
    private static final String MATCH_FILTER = "match_filter";
    private static final String MATCH_FILTER_LIST = "MatchFilterList";
    private static final String SUBSCRIBE_TYPE = "subscribe_type";
    private static final String TERMINATE_NOTIFICATION_ENABLED = "terminate_notification_enabled";
    private static final String MAX_DISTANCE_MM = "max_distance_mm";
    private static final String MIN_DISTANCE_MM = "min_distance_mm";
    private static final String PAIRING_CONFIG = "pairing_config";
    private static final String TTL_SEC = "TtlSec";
    private static final String INSTANTMODE_ENABLE = "InstantModeEnabled";
    private static final String BAND_5 = "5G";
    // PublishConfig special
    private static final String PUBLISH_TYPE = "publish_type";
    private static final String RANGING_ENABLED = "ranging_enabled";
    // AwarePairingConfig specific
    private static final String PAIRING_CACHE_ENABLED = "pairing_cache_enabled";
    private static final String PAIRING_SETUP_ENABLED = "pairing_setup_enabled";
    private static final String PAIRING_VERIFICATION_ENABLED = "pairing_verification_enabled";
    private static final String BOOTSTRAPPING_METHODS = "bootstrapping_methods";
    // WifiAwareNetworkSpecifier specific
    private static final String IS_ACCEPT_ANY = "is_accept_any";
    private static final String PMK = "pmk";
    private static final String CHANNEL_IN_MHZ = "channel_in_mhz";
    private static final String CHANNEL_REQUIRE = "channel_require";
    private static final String PSK_PASSPHRASE = "psk_passphrase";
    private static final String PORT = "port";
    private static final String TRANSPORT_PROTOCOL = "transport_protocol";
    private static final String DATA_PATH_SECURITY_CONFIG = "data_path_security_config";
    private static final String CHANNEL_FREQUENCY_M_HZ = "channel_frequency_m_hz";
    //NetworkRequest specific
    private static final String TRANSPORT_TYPE = "transport_type";
    private static final String CAPABILITY = "capability";
    private static final String NETWORK_SPECIFIER_PARCEL = "network_specifier_parcel";
    //WifiAwareDataPathSecurityConfig specific
    private static final String CIPHER_SUITE = "cipher_suite";
    private static final String SECURITY_CONFIG_PMK = "pmk";
    /** 2.4 GHz band */
    public static final int WIFI_BAND_24_GHZ = 1;
    /** 5 GHz band excluding DFS channels */
    public static final int WIFI_BAND_5_GHZ = 1;
    /** DFS channels from 5 GHz band only */
    public static final int WIFI_BAND_5_GHZ_DFS_ONLY  = 1;

    // Fields for rangingRequest
    private static final String RANGING_REQUEST_PEER_IDS = "peer_ids";
    private static final String RANGING_REQUEST_PEER_MACS = "peer_mac_addresses";

    private WifiAwareJsonDeserializer() {
    }

    /**
     * Converts Python dict to {@link SubscribeConfig}.
     *
     * @param jsonObject corresponding to SubscribeConfig in
     *                   tests/hostsidetests/multidevices/test/aware/constants.py
     */
    public static SubscribeConfig jsonToSubscribeConfig(JSONObject jsonObject)
            throws JSONException {
        SubscribeConfig.Builder builder = new SubscribeConfig.Builder();
        if (jsonObject == null) {
            return builder.build();
        }
        if (jsonObject.has(SERVICE_NAME)) {
            String serviceName = jsonObject.getString(SERVICE_NAME);
            builder.setServiceName(serviceName);
        }
        if (jsonObject.has(SERVICE_SPECIFIC_INFO)) {
            byte[] serviceSpecificInfo =
                    jsonObject.getString(SERVICE_SPECIFIC_INFO).getBytes(StandardCharsets.UTF_8);
            builder.setServiceSpecificInfo(serviceSpecificInfo);
        }
        if (jsonObject.has(MATCH_FILTER)) {
            List<byte[]> matchFilter = new ArrayList<>();
            for (int i = 0; i < jsonObject.getJSONArray(MATCH_FILTER).length(); i++) {
                matchFilter.add(jsonObject.getJSONArray(MATCH_FILTER).getString(i)
                        .getBytes(StandardCharsets.UTF_8));
            }
            builder.setMatchFilter(matchFilter);
        }
        if (jsonObject.has(MATCH_FILTER_LIST)) {
            byte[] bytes = Base64.decode(
                    jsonObject.getString(MATCH_FILTER_LIST).getBytes(StandardCharsets.UTF_8),
                     Base64.DEFAULT);

            List<byte[]> mf = new TlvBufferUtils.TlvIterable(0, 1, bytes).toList();
            builder.setMatchFilter(mf);
        }
        if (jsonObject.has(SUBSCRIBE_TYPE)) {
            int subscribeType = jsonObject.getInt(SUBSCRIBE_TYPE);
            builder.setSubscribeType(subscribeType);
        }
        if (jsonObject.has(TERMINATE_NOTIFICATION_ENABLED)) {
            boolean terminateNotificationEnabled =
                    jsonObject.getBoolean(TERMINATE_NOTIFICATION_ENABLED);
            builder.setTerminateNotificationEnabled(terminateNotificationEnabled);
        }
        if (jsonObject.has(MAX_DISTANCE_MM)) {
            int maxDistanceMm = jsonObject.getInt(MAX_DISTANCE_MM);
            if (maxDistanceMm > 0) {
                builder.setMaxDistanceMm(maxDistanceMm);
            }
        }
        if (jsonObject.has(MIN_DISTANCE_MM)) {
            int minDistanceMm = jsonObject.getInt(MIN_DISTANCE_MM);
            if (minDistanceMm >= 0) {
                builder.setMinDistanceMm(minDistanceMm);
            }
        }
        if (jsonObject.has(PAIRING_CONFIG)) {
            JSONObject pairingConfigObject = jsonObject.getJSONObject(PAIRING_CONFIG);
            AwarePairingConfig pairingConfig = jsonToAwarePairingConfig(pairingConfigObject);
            builder.setPairingConfig(pairingConfig);
        }
        if (jsonObject.has(TTL_SEC)) {
            builder.setTtlSec(jsonObject.getInt(TTL_SEC));
        }
        if (SdkLevel.isAtLeastT() && jsonObject.has(INSTANTMODE_ENABLE)) {
            builder.setInstantCommunicationModeEnabled(true,
                    Objects.equals(jsonObject.getString(INSTANTMODE_ENABLE), BAND_5)
                            ? WIFI_BAND_5_GHZ :WIFI_BAND_24_GHZ);
        }
        return builder.build();
    }

    /**
     * Converts JSONObject to {@link AwarePairingConfig}.
     *
     * @param jsonObject corresponding to SubscribeConfig in
     *                   tests/hostsidetests/multidevices/test/aware/constants.py
     */
    private static AwarePairingConfig jsonToAwarePairingConfig(JSONObject jsonObject)
            throws JSONException {
        AwarePairingConfig.Builder builder = new AwarePairingConfig.Builder();
        if (jsonObject == null) {
            return builder.build();
        }
        if (jsonObject.has(PAIRING_CACHE_ENABLED)) {
            boolean pairingCacheEnabled = jsonObject.getBoolean(PAIRING_CACHE_ENABLED);
            builder.setPairingCacheEnabled(pairingCacheEnabled);
        }
        if (jsonObject.has(PAIRING_SETUP_ENABLED)) {
            boolean pairingSetupEnabled = jsonObject.getBoolean(PAIRING_SETUP_ENABLED);
            builder.setPairingSetupEnabled(pairingSetupEnabled);
        }
        if (jsonObject.has(PAIRING_VERIFICATION_ENABLED)) {
            boolean pairingVerificationEnabled =
                    jsonObject.getBoolean(PAIRING_VERIFICATION_ENABLED);
            builder.setPairingVerificationEnabled(pairingVerificationEnabled);
        }
        if (jsonObject.has(BOOTSTRAPPING_METHODS)) {
            int bootstrappingMethods = jsonObject.getInt(BOOTSTRAPPING_METHODS);
            builder.setBootstrappingMethods(bootstrappingMethods);
        }
        return builder.build();
    }

    /**
     * Converts Python dict to {@link PublishConfig}.
     *
     * @param jsonObject corresponding to PublishConfig in
     *                   tests/hostsidetests/multidevices/test/aware/constants.py
     */
    public static PublishConfig jsonToPublishConfig(JSONObject jsonObject) throws JSONException {
        PublishConfig.Builder builder = new PublishConfig.Builder();
        if (jsonObject == null) {
            return builder.build();
        }
        if (jsonObject.has(SERVICE_NAME)) {
            String serviceName = jsonObject.getString(SERVICE_NAME);
            builder.setServiceName(serviceName);
        }
        if (jsonObject.has(SERVICE_SPECIFIC_INFO)) {
            byte[] serviceSpecificInfo =
                    jsonObject.getString(SERVICE_SPECIFIC_INFO).getBytes(StandardCharsets.UTF_8);
            builder.setServiceSpecificInfo(serviceSpecificInfo);
        }
        if (jsonObject.has(MATCH_FILTER)) {
            List<byte[]> matchFilter = new ArrayList<>();
            for (int i = 0; i < jsonObject.getJSONArray(MATCH_FILTER).length(); i++) {
                matchFilter.add(jsonObject.getJSONArray(MATCH_FILTER).getString(i)
                        .getBytes(StandardCharsets.UTF_8));
            }
            builder.setMatchFilter(matchFilter);
        }
        if (jsonObject.has(MATCH_FILTER_LIST)) {
            byte[] bytes = Base64.decode(
                    jsonObject.getString(MATCH_FILTER_LIST).getBytes(StandardCharsets.UTF_8),
                     Base64.DEFAULT);
            List<byte[]> mf = new TlvBufferUtils.TlvIterable(0, 1, bytes).toList();
            builder.setMatchFilter(mf);
        }
        if (jsonObject.has(PUBLISH_TYPE)) {
            int publishType = jsonObject.getInt(PUBLISH_TYPE);
            builder.setPublishType(publishType);
        }
        if (jsonObject.has(TERMINATE_NOTIFICATION_ENABLED)) {
            boolean terminateNotificationEnabled =
                    jsonObject.getBoolean(TERMINATE_NOTIFICATION_ENABLED);
            builder.setTerminateNotificationEnabled(terminateNotificationEnabled);
        }
        if (jsonObject.has(RANGING_ENABLED)) {
            boolean rangingEnabled = jsonObject.getBoolean(RANGING_ENABLED);
            builder.setRangingEnabled(rangingEnabled);
        }
        if (jsonObject.has(PAIRING_CONFIG)) {
            JSONObject pairingConfigObject = jsonObject.getJSONObject(PAIRING_CONFIG);
            AwarePairingConfig pairingConfig = jsonToAwarePairingConfig(pairingConfigObject);
            builder.setPairingConfig(pairingConfig);
        }
        if (jsonObject.has(TTL_SEC)) {
            builder.setTtlSec(jsonObject.getInt(TTL_SEC));
        }
        if (SdkLevel.isAtLeastT() && jsonObject.has(INSTANTMODE_ENABLE)) {
            builder.setInstantCommunicationModeEnabled(true,
                    Objects.equals(jsonObject.getString(INSTANTMODE_ENABLE), BAND_5)
                            ? WIFI_BAND_5_GHZ :WIFI_BAND_24_GHZ);
        }
        return builder.build();
    }

    /**
     * Converts request from JSON object to {@link NetworkRequest}.
     *
     * @param jsonObject corresponding to WifiAwareNetworkSpecifier in
     *                   tests/hostsidetests/multidevices/test/aware/constants.py
     */
    public static NetworkRequest jsonToNetworkRequest(JSONObject jsonObject) throws JSONException {
        NetworkRequest.Builder requestBuilder = new NetworkRequest.Builder();
        if (jsonObject == null) {
            return requestBuilder.build();
        }
        int transportType;
        if (jsonObject.has(TRANSPORT_TYPE)) {
            transportType = jsonObject.getInt(TRANSPORT_TYPE);
        } else {
            // Returns null for request of unknown type.
            return null;
        }
        if (transportType == NetworkCapabilities.TRANSPORT_WIFI_AWARE) {
            requestBuilder.addTransportType(transportType);
            if (jsonObject.has(NETWORK_SPECIFIER_PARCEL)) {
                String specifierParcelableStr = jsonObject.getString(NETWORK_SPECIFIER_PARCEL);
                WifiAwareNetworkSpecifier wifiAwareNetworkSpecifier =
                        SerializationUtil.stringToParcelable(
                                specifierParcelableStr,
                                WifiAwareNetworkSpecifier.CREATOR
                        );
                // Set the network specifier in the request builder
                requestBuilder.setNetworkSpecifier(wifiAwareNetworkSpecifier);
            }
            if (jsonObject.has(CAPABILITY)) {
                int capability = jsonObject.getInt(CAPABILITY);
                requestBuilder.addCapability(capability);
            }
            return requestBuilder.build();
        }
        return null;
    }

    /**
     * Converts JSON object to {@link WifiAwareNetworkSpecifier}.
     *
     * @param jsonObject corresponding to WifiAwareNetworkSpecifier in
     * @param builder    builder to build the WifiAwareNetworkSpecifier
     * @return WifiAwareNetworkSpecifier object
     */
    public static WifiAwareNetworkSpecifier jsonToNetworkSpecifier(
            JSONObject jsonObject, WifiAwareNetworkSpecifier.Builder builder
    ) throws JSONException {
        if (jsonObject == null) {
            return builder.build();
        }
        if (jsonObject.has(PSK_PASSPHRASE)) {
            String pskPassphrase = jsonObject.getString(PSK_PASSPHRASE);
            builder.setPskPassphrase(pskPassphrase);
        }
        if (jsonObject.has(PORT)) {
            builder.setPort(jsonObject.getInt(PORT));
        }
        if (jsonObject.has(TRANSPORT_PROTOCOL)) {
            builder.setTransportProtocol(jsonObject.getInt(TRANSPORT_PROTOCOL));
        }
        if (jsonObject.has(PMK)) {
            builder.setPmk(jsonObject.getString(PMK).getBytes(StandardCharsets.UTF_8));
        }
        if (jsonObject.has(DATA_PATH_SECURITY_CONFIG)) {
            builder.setDataPathSecurityConfig(jsonToDataPathSSecurityConfig(
                    jsonObject.getJSONObject(DATA_PATH_SECURITY_CONFIG)));
        }
        if (jsonObject.has(CHANNEL_FREQUENCY_M_HZ)) {
            builder.setChannelFrequencyMhz(jsonObject.getInt(CHANNEL_FREQUENCY_M_HZ), true);
        }

        return builder.build();

    }

    /**
     * Converts request from JSON object to {@link WifiAwareDataPathSecurityConfig}.
     *
     * @param jsonObject corresponding to WifiAwareNetworkSpecifier in
     *                   tests/hostsidetests/multidevices/test/aware/constants.py
     */
    private static WifiAwareDataPathSecurityConfig jsonToDataPathSSecurityConfig(
            @NonNull JSONObject jsonObject
    ) throws JSONException {
        WifiAwareDataPathSecurityConfig.Builder builder = null;

        if (jsonObject.has(CIPHER_SUITE)) {
            int cipherSuite = jsonObject.getInt(CIPHER_SUITE);
            builder = new WifiAwareDataPathSecurityConfig.Builder(cipherSuite);
        } else {
            throw new RuntimeException("Missing 'cipher_suite' in data path security jsonObject "
                    + "config");
        }
        if (jsonObject.has(SECURITY_CONFIG_PMK)) {
            byte[] pmk = jsonObject.getString(SECURITY_CONFIG_PMK).getBytes(StandardCharsets.UTF_8);
            builder.setPmk(pmk);
        }
        return builder.build();

    }

    /**
     * Converts the ranging request from JSONObject to {@link android.net.wifi.rtt.RangingRequest}.
     * This converts peer IDs in the request to Wi-Fi Aware peer handles in
     * {@link #mPeerHandles mPeerHandles}.
     *
     * @param jsonObject        The ranging request in JSONObject type.
     * @param peerHandles       All Wi-Fi Aware peers.
     * @return The converted ranging request.
     */
    public static RangingRequest jsonToRangingRequest(
            @NonNull JSONObject jsonObject, ConcurrentHashMap<Integer, PeerHandle> peerHandles
    ) throws JSONException, IllegalArgumentException {
        RangingRequest.Builder builder = new RangingRequest.Builder();
        if (jsonObject.has(RANGING_REQUEST_PEER_IDS)) {
            JSONArray values = jsonObject.getJSONArray(RANGING_REQUEST_PEER_IDS);
            for (int i = 0; i < values.length(); i++) {
                int peerId = values.getInt(i);
                PeerHandle handle = peerHandles.get(peerId);
                if (handle == null) {
                    throw new IllegalArgumentException(
                        "Got an invalid peerId. peerId: " + peerId + ", all peer Handles: "
                            + peerHandles
                    );
                }
                builder.addWifiAwarePeer(handle);
            }
        }
        if (jsonObject.has(RANGING_REQUEST_PEER_MACS)) {
            JSONArray values = jsonObject.getJSONArray(RANGING_REQUEST_PEER_MACS);
            for (int i = 0; i < values.length(); i++) {
                String macAddressStr = values.getString(i);
                MacAddress macAddress = MacAddress.fromString(macAddressStr);
                builder.addWifiAwarePeer(macAddress);
            }
        }
        return builder.build();
    }
}
