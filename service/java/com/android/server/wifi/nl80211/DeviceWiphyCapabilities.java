/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.server.wifi.nl80211;

import android.annotation.RequiresApi;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiAnnotations.ChannelWidth;
import android.net.wifi.WifiAnnotations.WifiStandard;
import android.net.wifi.flags.Flags;
import android.os.Build;
import android.util.Log;

import com.android.modules.utils.build.SdkLevel;

import java.util.Objects;

/**
 * DeviceWiphyCapabilities for nl80211
 *
 * Contains the WiFi physical layer attributes and capabilities of the device.
 * It is used to collect these attributes from the device driver via nl80211.
 *
 * Note: This class was copied from {@link android.net.wifi.nl80211.DeviceWiphyCapabilities} but
 * removed the now-unnecessary Parcelable implementation
 */
public final class DeviceWiphyCapabilities {
    private static final String TAG = "DeviceWiphyCapabilities";

    private final boolean m80211nSupported;
    private final boolean m80211acSupported;
    private final boolean m80211axSupported;
    private final boolean m80211beSupported;
    private final boolean mChannelWidth160MhzSupported;
    private final boolean mChannelWidth80p80MhzSupported;
    private final boolean mChannelWidth320MhzSupported;
    private final int mMaxNumberTxSpatialStreams;
    private final int mMaxNumberRxSpatialStreams;
    private final int mMaxNumberAkms;

    private DeviceWiphyCapabilities(Builder builder) {
        m80211nSupported = builder.m80211nSupported;
        m80211acSupported = builder.m80211acSupported;
        m80211axSupported = builder.m80211axSupported;
        m80211beSupported = builder.m80211beSupported;
        mChannelWidth160MhzSupported = builder.mChannelWidth160MhzSupported;
        mChannelWidth80p80MhzSupported = builder.mChannelWidth80p80MhzSupported;
        mChannelWidth320MhzSupported = builder.mChannelWidth320MhzSupported;
        mMaxNumberTxSpatialStreams = builder.mMaxNumberTxSpatialStreams;
        mMaxNumberRxSpatialStreams = builder.mMaxNumberRxSpatialStreams;
        mMaxNumberAkms = builder.mMaxNumberAkms;
    }

    /**
     * Get the IEEE 802.11 standard support
     *
     * @param standard the IEEE 802.11 standard to check on its support.
     *        valid values from {@link ScanResult}'s {@code WIFI_STANDARD_}
     * @return {@code true} if supported, {@code false} otherwise.
     */
    public boolean isWifiStandardSupported(@WifiStandard int standard) {
        switch (standard) {
            case ScanResult.WIFI_STANDARD_LEGACY:
                return true;
            case ScanResult.WIFI_STANDARD_11N:
                return m80211nSupported;
            case ScanResult.WIFI_STANDARD_11AC:
                return m80211acSupported;
            case ScanResult.WIFI_STANDARD_11AX:
                return m80211axSupported;
            case ScanResult.WIFI_STANDARD_11BE:
                return m80211beSupported;
            default:
                Log.e(TAG, "isWifiStandardSupported called with invalid standard: " + standard);
                return false;
        }
    }

    /**
     * Get the support for channel bandwidth
     *
     * @param chWidth valid values from {@link ScanResult}'s {@code CHANNEL_WIDTH_}
     *
     * @return {@code true} if supported, {@code false} otherwise.
     */
    public boolean isChannelWidthSupported(@ChannelWidth int chWidth) {
        switch (chWidth) {
            case ScanResult.CHANNEL_WIDTH_20MHZ:
                return true;
            case ScanResult.CHANNEL_WIDTH_40MHZ:
                return (m80211nSupported || m80211acSupported || m80211axSupported
                        || m80211beSupported);
            case ScanResult.CHANNEL_WIDTH_80MHZ:
                return (m80211acSupported || m80211axSupported || m80211beSupported);
            case ScanResult.CHANNEL_WIDTH_160MHZ:
                return mChannelWidth160MhzSupported;
            case ScanResult.CHANNEL_WIDTH_80MHZ_PLUS_MHZ:
                return mChannelWidth80p80MhzSupported;
            case ScanResult.CHANNEL_WIDTH_320MHZ:
                return mChannelWidth320MhzSupported;
            default:
                Log.e(TAG, "isChannelWidthSupported called with invalid channel width: " + chWidth);
        }
        return false;
    }

    /**
     * Get maximum number of transmit spatial streams
     *
     * @return number of spatial streams
     */
    public int getMaxNumberTxSpatialStreams() {
        return mMaxNumberTxSpatialStreams;
    }

    /**
     * Get maximum number of receive spatial streams
     *
     * @return number of streams
     */
    public int getMaxNumberRxSpatialStreams() {
        return mMaxNumberRxSpatialStreams;
    }

    /**
     * Get the maximum number of AKM suites supported in the connection request to the driver.
     */
    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    public int getMaxNumberAkms() {
        return mMaxNumberAkms;
    }

    /** override comparator */
    @Override
    public boolean equals(Object rhs) {
        if (this == rhs) return true;
        if (!(rhs instanceof DeviceWiphyCapabilities)) {
            return false;
        }
        DeviceWiphyCapabilities capa = (DeviceWiphyCapabilities) rhs;

        return m80211nSupported == capa.m80211nSupported
                && m80211acSupported == capa.m80211acSupported
                && m80211axSupported == capa.m80211axSupported
                && m80211beSupported == capa.m80211beSupported
                && mChannelWidth160MhzSupported == capa.mChannelWidth160MhzSupported
                && mChannelWidth80p80MhzSupported == capa.mChannelWidth80p80MhzSupported
                && mChannelWidth320MhzSupported == capa.mChannelWidth320MhzSupported
                && mMaxNumberTxSpatialStreams == capa.mMaxNumberTxSpatialStreams
                && mMaxNumberRxSpatialStreams == capa.mMaxNumberRxSpatialStreams
                && mMaxNumberAkms == capa.mMaxNumberAkms;
    }

    /** override hash code */
    @Override
    public int hashCode() {
        return Objects.hash(m80211nSupported, m80211acSupported, m80211axSupported,
                m80211beSupported, mChannelWidth160MhzSupported, mChannelWidth80p80MhzSupported,
                mChannelWidth320MhzSupported, mMaxNumberTxSpatialStreams,
                mMaxNumberRxSpatialStreams, mMaxNumberAkms);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("DeviceWiphyCapabilities{");
        sb.append("80211nSupported=").append(m80211nSupported);
        sb.append(", 80211acSupported=").append(m80211acSupported);
        sb.append(", 80211axSupported=").append(m80211axSupported);
        sb.append(", 80211beSupported=").append(m80211beSupported);
        sb.append(", channelWidth160MhzSupported=").append(mChannelWidth160MhzSupported);
        sb.append(", channelWidth80p80MhzSupported=").append(mChannelWidth80p80MhzSupported);
        sb.append(", channelWidth320MhzSupported=").append(mChannelWidth320MhzSupported);
        sb.append(", maxNumberTxSpatialStreams=").append(mMaxNumberTxSpatialStreams);
        sb.append(", maxNumberRxSpatialStreams=").append(mMaxNumberRxSpatialStreams);
        sb.append(", maxNumberAkms=").append(mMaxNumberAkms);
        sb.append('}');
        return sb.toString();
    }

    /** Builder for {@link DeviceWiphyCapabilities} */
    public static final class Builder {
        private boolean m80211nSupported = false;
        private boolean m80211acSupported = false;
        private boolean m80211axSupported = false;
        private boolean m80211beSupported = false;
        private boolean mChannelWidth160MhzSupported = false;
        private boolean mChannelWidth80p80MhzSupported = false;
        private boolean mChannelWidth320MhzSupported = false;
        private int mMaxNumberTxSpatialStreams = 1;
        private int mMaxNumberRxSpatialStreams = 1;
        private int mMaxNumberAkms = 1;

        /** Default constructor */
        public Builder() {}

        /**
         * Creates a Builder initialized with values from an existing DeviceWiphyCapabilities.
         *
         * @param capabilities The object to copy values from.
         * @return A new Builder instance with copied values.
         */
        public static Builder createFromDeviceWiphyCapabilities(
                DeviceWiphyCapabilities capabilities) {
            Builder builder = new Builder();
            builder.m80211nSupported = capabilities.m80211nSupported;
            builder.m80211acSupported = capabilities.m80211acSupported;
            builder.m80211axSupported = capabilities.m80211axSupported;
            builder.m80211beSupported = capabilities.m80211beSupported;
            builder.mChannelWidth160MhzSupported = capabilities.mChannelWidth160MhzSupported;
            builder.mChannelWidth80p80MhzSupported = capabilities.mChannelWidth80p80MhzSupported;
            builder.mChannelWidth320MhzSupported = capabilities.mChannelWidth320MhzSupported;
            builder.mMaxNumberTxSpatialStreams = capabilities.mMaxNumberTxSpatialStreams;
            builder.mMaxNumberRxSpatialStreams = capabilities.mMaxNumberRxSpatialStreams;
            builder.mMaxNumberAkms = capabilities.mMaxNumberAkms;
            return builder;
        }

        /**
         * Creates a Builder initialized with values from a wificond capabilities.
         *
         * @param wificondCapabilities The wificond object to copy values from.
         * @return A new Builder instance with copied values.
         */
        public static Builder createFromWificondCapabilities(
                android.net.wifi.nl80211.DeviceWiphyCapabilities wificondCapabilities) {
            Builder builder = new Builder();
            builder.m80211nSupported = wificondCapabilities.isWifiStandardSupported(
                    ScanResult.WIFI_STANDARD_11N);
            builder.m80211acSupported = wificondCapabilities.isWifiStandardSupported(
                    ScanResult.WIFI_STANDARD_11AC);
            builder.m80211axSupported = wificondCapabilities.isWifiStandardSupported(
                    ScanResult.WIFI_STANDARD_11AX);
            builder.m80211beSupported = wificondCapabilities.isWifiStandardSupported(
                    ScanResult.WIFI_STANDARD_11BE);
            builder.mChannelWidth160MhzSupported = wificondCapabilities.isChannelWidthSupported(
                    ScanResult.CHANNEL_WIDTH_160MHZ);
            builder.mChannelWidth80p80MhzSupported = wificondCapabilities.isChannelWidthSupported(
                    ScanResult.CHANNEL_WIDTH_80MHZ_PLUS_MHZ);
            builder.mChannelWidth320MhzSupported = wificondCapabilities.isChannelWidthSupported(
                    ScanResult.CHANNEL_WIDTH_320MHZ);
            builder.mMaxNumberTxSpatialStreams =
                    wificondCapabilities.getMaxNumberTxSpatialStreams();
            builder.mMaxNumberRxSpatialStreams =
                    wificondCapabilities.getMaxNumberRxSpatialStreams();
            if (Flags.getDeviceCrossAkmRoamingSupport() && SdkLevel.isAtLeastV()) {
                builder.mMaxNumberAkms = wificondCapabilities.getMaxNumberAkms();
            }
            return builder;
        }

        /**
         * Set the IEEE 802.11 standard support
         *
         * @param standard the IEEE 802.11 standard to set its support.
         *        valid values from {@link ScanResult}'s {@code WIFI_STANDARD_}
         * @param support {@code true} if supported, {@code false} otherwise.
         * @return this builder
         */
        public Builder setWifiStandardSupport(@WifiStandard int standard, boolean support) {
            switch (standard) {
                case ScanResult.WIFI_STANDARD_11N:
                    m80211nSupported = support;
                    break;
                case ScanResult.WIFI_STANDARD_11AC:
                    m80211acSupported = support;
                    break;
                case ScanResult.WIFI_STANDARD_11AX:
                    m80211axSupported = support;
                    break;
                case ScanResult.WIFI_STANDARD_11BE:
                    m80211beSupported = support;
                    break;
                default:
                    Log.e(TAG, "setWifiStandardSupport called with invalid standard: " + standard);
            }
            return this;
        }

        /**
         * Set support for channel bandwidth
         *
         * @param chWidth valid values are {@link ScanResult#CHANNEL_WIDTH_160MHZ},
         *        {@link ScanResult#CHANNEL_WIDTH_80MHZ_PLUS_MHZ} and
         *        {@link ScanResult#CHANNEL_WIDTH_320MHZ}
         * @param support {@code true} if supported, {@code false} otherwise.
         * @return this builder
         */
        public Builder setChannelWidthSupported(@ChannelWidth int chWidth, boolean support) {
            switch (chWidth) {
                case ScanResult.CHANNEL_WIDTH_160MHZ:
                    mChannelWidth160MhzSupported = support;
                    break;
                case ScanResult.CHANNEL_WIDTH_80MHZ_PLUS_MHZ:
                    mChannelWidth80p80MhzSupported = support;
                    break;
                case ScanResult.CHANNEL_WIDTH_320MHZ:
                    mChannelWidth320MhzSupported = support;
                    break;
                default:
                    Log.e(TAG, "setChannelWidthSupported called with Invalid channel width: "
                            + chWidth);
            }
            return this;
        }

        /**
         * Set maximum number of transmit spatial streams
         *
         * @param streams number of spatial streams
         * @return this builder
         */
        public Builder setMaxNumberTxSpatialStreams(int streams) {
            mMaxNumberTxSpatialStreams = streams;
            return this;
        }

        /**
         * Set maximum number of receive spatial streams
         *
         * @param streams number of streams
         * @return this builder
         */
        public Builder setMaxNumberRxSpatialStreams(int streams) {
            mMaxNumberRxSpatialStreams = streams;
            return this;
        }

        /**
         * Set the maximum number of AKM suites supported in the connection request to the driver.
         * @param akms number of akms
         * @return this builder
         */
        public Builder setMaxNumberAkms(int akms) {
            mMaxNumberAkms = akms;
            return this;
        }

        /**
         * Build the {@link DeviceWiphyCapabilities} object
         * @return the {@link DeviceWiphyCapabilities} object
         */
        public DeviceWiphyCapabilities build() {
            return new DeviceWiphyCapabilities(this);
        }
    }
}
