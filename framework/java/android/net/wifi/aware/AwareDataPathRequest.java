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

package android.net.wifi.aware;

import static com.android.wifi.flags.Flags.FLAG_MULTI_PEER_AWARE_DATAPATH;

import android.annotation.FlaggedApi;
import android.annotation.IntRange;
import android.annotation.Nullable;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;

import java.util.Objects;

/**
 * A request to set up a data path for a Wi-Fi Aware network. This is used to set up a data path
 * with a peer device.
 * This is used for
 * {@link PublishDiscoverySession#acceptDataPathRequest(PeerHandle, AwareDataPathRequest)}
 * and {@link SubscribeDiscoverySession#initiateDataPathRequest(PeerHandle, AwareDataPathRequest)}
 */
@FlaggedApi(FLAG_MULTI_PEER_AWARE_DATAPATH)
public final class AwareDataPathRequest implements Parcelable {

    /**
     * The port number which will be used to create a connection over this link. This
     * configuration should only be done on the server device, e.g. the device creating the
     * {@link java.net.ServerSocket}.
     */
    private final int mPort;

    /**
     * The transport protocol which will be used to create a connection over this link. This
     * configuration should only be done on the server device, e.g. the device creating the
     * {@link java.net.ServerSocket} for TCP.
     */
    private final int mTransportProtocol;

    /**
     * Channel frequency in MHz for setup data-path on.
     */
    private final int mChannelInMhz;

    /**
     * Force to use the specified channel or not. If true, Channel request is specified and must be
     * respected. If the firmware cannot honor the request then the data-path request is rejected.
     * Otherwise, requested channel can be overridden by firmware.
     */
    private final boolean mForcedChannel;

    private final WifiAwareDataPathSecurityConfig mSecurityConfig;

    /**
     * @hide
     */
    public AwareDataPathRequest(int port, int transportProtocol,
            int channel, boolean forcedChannel, WifiAwareDataPathSecurityConfig securityConfig) {
        mPort = port;
        mTransportProtocol = transportProtocol;
        mChannelInMhz = channel;
        mForcedChannel = forcedChannel;
        mSecurityConfig = securityConfig;
    }

    private AwareDataPathRequest(Parcel in) {
        mPort = in.readInt();
        mTransportProtocol = in.readInt();
        mChannelInMhz = in.readInt();
        mForcedChannel = in.readByte() != 0;
        mSecurityConfig = in.readParcelable(WifiAwareDataPathSecurityConfig.class.getClassLoader());
    }

    public @NonNull static final Creator<AwareDataPathRequest> CREATOR =
            new Creator<AwareDataPathRequest>() {
                @Override
                public AwareDataPathRequest createFromParcel(Parcel in) {
                    return new AwareDataPathRequest(in);
                }

                @Override
                public AwareDataPathRequest[] newArray(int size) {
                    return new AwareDataPathRequest[size];
                }
            };

    /**
     * Get the specified channel in MHZ for this Wi-Fi Aware network specifier.
     * @see AwareDataPathRequest.Builder#setChannelFrequencyMhz(int, boolean)
     * @return Channel frequency in Mhz. A value of 0 indicates that no channel was specified.
     */
    @IntRange(from = 0)
    public int getChannelFrequencyMhz() {
        return mChannelInMhz;
    }

    /**
     * Check if the specified channel is required to honor or not.
     * @see AwareDataPathRequest.Builder#setChannelFrequencyMhz(int, boolean)
     * @return true if the channel is required to honor, false if it is a recommendation.
     */
    public boolean isChannelRequired() {
        return mForcedChannel;
    }

    /**
     * Get the security config specified in this Network Specifier to encrypt Wi-Fi Aware data-path
     * @return {@link WifiAwareDataPathSecurityConfig} used to encrypt the data-path
     */
    public @Nullable WifiAwareDataPathSecurityConfig getDataPathSecurityConfig() {
        return mSecurityConfig;
    }

    /**
     * Get the port number which will be used to create a connection over this link.
     * @see AwareDataPathRequest.Builder#setPort(int)
     * @return The port number. A value of 0 indicates that no port was specified.
     */
    @IntRange(from = 0, to = 65535)
    public int getPort() {
        return mPort;
    }

    /**
     * Get the transport protocol which will be used to create a connection over this link.
     * @see AwareDataPathRequest.Builder#setTransportProtocol(int)
     * @return The transport protocol. A value of -1 indicates that no transport protocol was
     *         specified.
     */
    @IntRange(from = -1, to = 255)
    public int getTransportProtocol() {
        return mTransportProtocol;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeInt(mPort);
        dest.writeInt(mTransportProtocol);
        dest.writeInt(mChannelInMhz);
        dest.writeByte((byte) (mForcedChannel ? 1 : 0));
        dest.writeParcelable(mSecurityConfig, flags);
    }

    @Override
    public String toString() {
        return "AwareDataPathRequest{"
                + "mPort=" + mPort
                + ", mTransportProtocol=" + mTransportProtocol
                + ", mChannelInMhz=" + mChannelInMhz
                + ", mForcedChannel=" + mForcedChannel
                + ", mSecurityConfig=" + mSecurityConfig + "}";
    }

    @Override
    public int hashCode() {
        return Objects.hash(mPort, mTransportProtocol, mChannelInMhz, mForcedChannel,
                mSecurityConfig);
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AwareDataPathRequest that = (AwareDataPathRequest) o;
        return mPort == that.mPort
                && mTransportProtocol == that.mTransportProtocol
                && mChannelInMhz == that.mChannelInMhz
                && mForcedChannel == that.mForcedChannel
                && Objects.equals(mSecurityConfig, that.mSecurityConfig);
    }

    /**
     * A builder class for a Wi-Fi Aware data path request to set up a data path with a peer
     * device.
     */
    @FlaggedApi(FLAG_MULTI_PEER_AWARE_DATAPATH)
    public static final class Builder {
        private static final int INVALID_PORT = 0;
        private static final int INVALID_TRANSPORT_PROTOCOL = -1;
        private static final int INVALID_CHANNEL = 0;

        private int mPort = INVALID_PORT;
        private int mTransportProtocol = INVALID_TRANSPORT_PROTOCOL;
        private int mChannel = INVALID_CHANNEL;
        private boolean mIsRequired = false;
        private WifiAwareDataPathSecurityConfig mSecurityConfig;

        /**
         * Configure the port number which will be used to create a connection over this link. This
         * configuration should only be done on the server device, e.g. the device creating the
         * {@link java.net.ServerSocket}.
         * <p>Notes:
         * <ul>
         *     <li>The server device must be the Publisher device!
         *     <li>The port information can only be specified on secure links, specified using
         *     {@link #setDataPathSecurityConfig(WifiAwareDataPathSecurityConfig)}
         * </ul>
         *
         * @param port A positive integer indicating the port to be used for communication.
         * @return the current {@link Builder} builder, enabling chaining of builder
         *         methods.
         */
        public @NonNull Builder setPort(@IntRange(from = 1, to = 65535) int port) {
            if (port < 1 || port > 65535) {
                throw new IllegalArgumentException("The port must be a positive integer"
                        + " in range [1, 65535]");
            }
            mPort = port;
            return this;
        }

        /**
         * Configure the transport protocol which will be used to create a connection over this
         * link. This configuration should only be done on the server device, e.g. the device
         * creating the {@link java.net.ServerSocket} for TCP.
         * <p>Notes:
         * <ul>
         *     <li>The server device must be the Publisher device!
         *     <li>The transport protocol information can only be specified on secure links,
         *     specified using
         *     {@link #setDataPathSecurityConfig(WifiAwareDataPathSecurityConfig)}.
         * </ul>
         * The transport protocol number is assigned by the Internet Assigned Numbers Authority
         * (IANA) https://www.iana.org/assignments/protocol-numbers/protocol-numbers.xhtml.
         *
         * @param transportProtocol The transport protocol to be used for communication.
         * @return the current {@link Builder} builder, enabling chaining of builder
         *         methods.
         */
        public @NonNull Builder setTransportProtocol(@IntRange(from = 0, to = 255)
                int transportProtocol) {
            if (transportProtocol < 0 || transportProtocol > 255) {
                throw new IllegalArgumentException(
                        "The transport protocol must be in range [0, 255]");
            }
            mTransportProtocol = transportProtocol;
            return this;
        }

        /**
         * Configure the Channel frequency for the Wi-Fi Aware connection being requested. This
         * method is optional - if not called, then channelInMhz to use will be decided by firmware.
         * Only use this when {@link WifiAwareManager#isSetChannelOnDataPathSupported()} is true,
         * otherwise the set channelInMhz will be ignored.
         * @param channelInMhz Channel frequency in Mhz.
         * @param required If set to true, Channel request is specified and must be respected.
         *               If the firmware cannot honor the request then the data-path request
         *               is rejected. Otherwise, requested channelInMhz is a recommendation and
         *               may be overridden by the firmware.
         * @return the current {@link Builder} builder, enabling chaining of builder methods.
         */
        public @NonNull Builder setChannelFrequencyMhz(@IntRange(from = 0) int channelInMhz,
                boolean required) {
            mChannel = channelInMhz;
            mIsRequired = required;
            return this;
        }

        /**
         * Configure security config for the Wi-Fi Aware connection being requested. This method
         * is optional - if not called, then an Open (unencrypted) connection will be created.
         *
         * @param securityConfig The (optional) security config to be used to encrypt the link.
         * @return the current {@link Builder} builder, enabling chaining of builder
         *         methods.
         */
        public @NonNull Builder setDataPathSecurityConfig(
                @NonNull WifiAwareDataPathSecurityConfig securityConfig) {
            if (securityConfig == null) {
                throw new IllegalArgumentException("The WifiAwareDataPathSecurityConfig "
                        + "should be non-null");
            }

            if (!securityConfig.isValid()) {
                throw new IllegalArgumentException("The WifiAwareDataPathSecurityConfig "
                        + "is invalid");
            }
            mSecurityConfig = securityConfig;
            return this;
        }

        /**
         * Build the {@link AwareDataPathRequest} object.
         * @return the {@link AwareDatapathRequest} object.
         */
        public @android.annotation.NonNull AwareDataPathRequest build() {
            return new AwareDataPathRequest(mPort, mTransportProtocol, mChannel,
                    mIsRequired, mSecurityConfig);
        }
    }
}
