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

package android.net.wifi.p2p;

import android.annotation.FlaggedApi;
import android.annotation.NonNull;
import android.net.wifi.WifiAnnotations;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.RequiresApi;

import com.android.wifi.flags.Flags;

@RequiresApi(37)
@FlaggedApi(Flags.FLAG_WIFI_P2P_CONNECTION_INFO)
public final class WifiP2pConnectionInfo implements Parcelable {
    private int mWifiStandard;
    private int mChannelWidth;
    private int mTxNss;
    private int mRxNss;

    /**
     * @param wifiStandard See {@link ScanResult#WIFI_STANDARD_XXX}
     * @param channelWidth See {@link ScanResult#CHANNEL_WIDTH_XXX}
     * @param txNss Maximum number of transmit spatial streams
     * @param rxNss Maximum number of receive spatial streams
     */
    public WifiP2pConnectionInfo(int wifiStandard, int channelWidth, int txNss, int rxNss) {
        this.mWifiStandard = wifiStandard;
        this.mChannelWidth = channelWidth;
        this.mTxNss = txNss;
        this.mRxNss = rxNss;
    }

    /** copy constructor */
    /** @hide */
    public WifiP2pConnectionInfo(@NonNull WifiP2pConnectionInfo source) {
        this.mWifiStandard = source.mWifiStandard;
        this.mChannelWidth = source.mChannelWidth;
        this.mTxNss = source.mTxNss;
        this.mRxNss = source.mRxNss;
    }

    private WifiP2pConnectionInfo(@NonNull Parcel in) {
        readFromParcel(in);
    }

    @NonNull
    public static final Creator<WifiP2pConnectionInfo> CREATOR =
            new Creator<WifiP2pConnectionInfo>() {
                @Override
                public WifiP2pConnectionInfo createFromParcel(@NonNull Parcel in) {
                    return new WifiP2pConnectionInfo(in);
                }

                @Override
                public WifiP2pConnectionInfo[] newArray(int size) {
                    return new WifiP2pConnectionInfo[size];
                }
            };

    private void readFromParcel(@NonNull Parcel source) {
        mWifiStandard = source.readInt();
        mChannelWidth = source.readInt();
        mTxNss = source.readInt();
        mRxNss = source.readInt();
    }

    @Override
    public void writeToParcel(@NonNull Parcel parcel, int flags) {
        parcel.writeInt(mWifiStandard);
        parcel.writeInt(mChannelWidth);
        parcel.writeInt(mTxNss);
        parcel.writeInt(mRxNss);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    /**
     * Gets the Wi-Fi standard of the current P2P connection.
     *
     * @return The Wi-Fi standard, as defined by the {@code WIFI_STANDARD_*} constants in
     *         {@link android.net.wifi.ScanResult}.
     */
    public @WifiAnnotations.WifiStandard int getWifiStandard() {
        return mWifiStandard;
    }

    /**
     * Gets the channel width of the current P2P connection.
     *
     * @return The channel width, as defined by the {@code CHANNEL_WIDTH_*} constants in
     *         {@link android.net.wifi.ScanResult}.
     */
    public @WifiAnnotations.ChannelWidth int getChannelWidth() {
        return mChannelWidth;
    }

    /**
     * Gets the maximum number of transmit spatial streams (NSS) for the current P2P connection.
     *
     * @return The maximum number of spatial streams used for transmitting data.
     */
    public int getTxNss() {
        return mTxNss;
    }

    /**
     * Gets the maximum number of receive spatial streams (NSS) for the current P2P connection.
     *
     * @return The maximum number of spatial streams used for receiving data.
     */
    public int getRxNss() {
        return mRxNss;
    }

    @Override
    public String toString() {
        StringBuilder sbuf = new StringBuilder("WifiP2pConnectionInfo:");
        sbuf.append("\n wifiStandard: ").append(mWifiStandard);
        sbuf.append("\n channelWidth: ").append(mChannelWidth);
        sbuf.append("\n txNss: ").append(mTxNss);
        sbuf.append("\n rxNss: ").append(mRxNss);
        return sbuf.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof WifiP2pConnectionInfo)) return false;
        WifiP2pConnectionInfo that = (WifiP2pConnectionInfo) o;
        return mWifiStandard == that.mWifiStandard
                && mChannelWidth == that.mChannelWidth
                && mTxNss == that.mTxNss
                && mRxNss == that.mRxNss;
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(mWifiStandard, mChannelWidth, mTxNss, mRxNss);
    }
}
