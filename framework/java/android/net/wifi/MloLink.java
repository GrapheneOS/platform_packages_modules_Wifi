/*
 * Copyright (C) 2022 The Android Open Source Project
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

package android.net.wifi;

import static android.net.wifi.WifiScanner.WIFI_BAND_24_GHZ;
import static android.net.wifi.WifiScanner.WIFI_BAND_5_GHZ;
import static android.net.wifi.WifiScanner.WIFI_BAND_6_GHZ;

import android.annotation.IntDef;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.net.MacAddress;
import android.os.Parcel;
import android.os.Parcelable;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Objects;

/**
 * Data structure class representing a Wi-Fi Multi-Link Operation (MLO) link
 * This is only used by 802.11be capable devices
 */
public final class MloLink implements Parcelable {

    /**
     * Invalid link id. Used in {link #getLinkId()}
     */
    public static final int INVALID_MLO_LINK_ID = -1;

    /**
     * Lower limit for MLO link id
     * As described in IEEE 802.11be Specification, section 9.4.2.295b.2.
     *
     * @hide
     */
    public static final int MIN_MLO_LINK_ID = 0;

    /**
     * Upper limit for MLO link id
     * As described in IEEE 802.11be Specification, section 9.4.2.295b.2.
     *
     * @hide
     */
    public static final int MAX_MLO_LINK_ID = 15;

    /**
     * MLO link state: Invalid link state. Used in {link #getState()}
     */
    public static final int MLO_LINK_STATE_INVALID = 0;
    /**
     * MLO link state: Link is not associated with the access point. Used in {link #getState()}
     */
    public static final int MLO_LINK_STATE_UNASSOCIATED = 1;
    /**
     * MLO link state: Link is associated to the access point but not mapped to any traffic stream.
     * Used in {link #getState()}
     */
    public static final int MLO_LINK_STATE_IDLE = 2;
    /**
     * MLO link state: Link is associated to the access point and mapped to at least one traffic
     * stream. {link #getState()}
     * Note that an MLO link could be in that state but in power save mode.
     */
    public static final int MLO_LINK_STATE_ACTIVE = 3;

    /**
     * @hide
     */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef(prefix = {"MLO_LINK_STATE_"}, value = {
            MLO_LINK_STATE_INVALID,
            MLO_LINK_STATE_UNASSOCIATED,
            MLO_LINK_STATE_IDLE,
            MLO_LINK_STATE_ACTIVE})
    public @interface MloLinkState {};

    private int mLinkId;
    private MacAddress mStaMacAddress;
    private @MloLinkState int mState;
    private @WifiAnnotations.WifiBandBasic int mBand;
    private int mChannel;

    /**
     * Constructor for a MloLInk.
     */
    public MloLink() {
        mBand = WifiScanner.WIFI_BAND_UNSPECIFIED;
        mChannel = 0;
        mState = MLO_LINK_STATE_UNASSOCIATED;
        mStaMacAddress = null;
        mLinkId = INVALID_MLO_LINK_ID;
    }

    /** Returns the Wi-Fi band of this link as one of:
     *      {@link WifiScanner#WIFI_BAND_UNSPECIFIED},
     *      {@link WifiScanner#WIFI_BAND_24_GHZ},
     *      {@link WifiScanner#WIFI_BAND_5_GHZ},
     *      {@link WifiScanner#WIFI_BAND_6_GHZ}
     */
    public @WifiAnnotations.WifiBandBasic int getBand() {
        return mBand;
    }

    /** Returns the channel number of this link. */
    public int getChannel() {
        return mChannel;
    }

    /**
     * Returns the link id of this link.
     * Valid values are 0-15, as described in IEEE 802.11be Specification, section 9.4.2.295b.2.
     *
     * @return {@link #INVALID_MLO_LINK_ID} or a valid value (0-15).
     */
    public int getLinkId() {
        return mLinkId;
    }

    /** Returns the state of this link as one of:
     *     {@link #MLO_LINK_STATE_INVALID}
     *     {@link #MLO_LINK_STATE_UNASSOCIATED}
     *     {@link #MLO_LINK_STATE_IDLE}
     *     {@link #MLO_LINK_STATE_ACTIVE}
     */
    public @MloLinkState int getState() {
        return mState;
    }

    /** Returns the STA MAC address of this link. */
    public @Nullable MacAddress getStaMacAddress() {
        return mStaMacAddress;
    }

    /**
     * Sets the channel number of this link.
     *
     * @hide
     */
    public void setChannel(int channel) {
        mChannel = channel;
    }

    /**
     * Sets the band for this link
     *
     * @hide
     */
    public void setBand(@WifiAnnotations.WifiBandBasic int band) {
        mBand = band;
    }

    /**
     * Sets the linkId of this link
     *
     * @hide
     */
    public void setLinkId(int linkId) {
        mLinkId = linkId;
    }

    /**
     * Sets the state of this link
     *
     * @hide
     */
    public void setState(@MloLinkState int state) {
        mState = state;
    }

    /**
     * set the STA MAC Address for this link
     *
     * @hide
     */
    public void setStaMacAddress(MacAddress address) {
        mStaMacAddress = address;
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MloLink that = (MloLink) o;
        return mBand == that.mBand
                && mChannel == that.mChannel
                && mLinkId == that.mLinkId
                && Objects.equals(mStaMacAddress, that.mStaMacAddress)
                && mState == that.mState;
    }

    @Override
    public int hashCode() {
        return Objects.hash(mBand, mChannel, mLinkId, mStaMacAddress, mState);
    }

    private String getStateString(@MloLinkState int state) {
        switch(state) {
            case MLO_LINK_STATE_INVALID:
                return "MLO_LINK_STATE_INVALID";
            case MLO_LINK_STATE_UNASSOCIATED:
                return "MLO_LINK_STATE_UNASSOCIATED";
            case MLO_LINK_STATE_IDLE:
                return "MLO_LINK_STATE_IDLE";
            case MLO_LINK_STATE_ACTIVE:
                return "MLO_LINK_STATE_ACTIVE";
            default:
                return "Unknown MLO link state";
        }
    }

    /**
     * @hide
     */
    public static boolean isValidState(@MloLinkState int state) {
        switch(state) {
            case MLO_LINK_STATE_INVALID:
            case MLO_LINK_STATE_UNASSOCIATED:
            case MLO_LINK_STATE_IDLE:
            case MLO_LINK_STATE_ACTIVE:
                return true;
        }
        return false;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("MloLink{");
        if (mBand == WIFI_BAND_24_GHZ) {
            sb.append("2.4GHz");
        } else if (mBand == WIFI_BAND_5_GHZ) {
            sb.append("5GHz");
        } else if (mBand == WIFI_BAND_6_GHZ) {
            sb.append("6GHz");
        } else {
            sb.append("UNKNOWN BAND");
        }
        sb.append(", channel: ").append(mChannel);
        sb.append(", id: ").append(mLinkId);
        sb.append(", state: ").append(getStateString(mState));
        if (mStaMacAddress != null) {
            sb.append(", MAC Address: ").append(mStaMacAddress.toString());
        }
        sb.append('}');
        return sb.toString();
    }

    /** Implement the Parcelable interface */
    @Override
    public int describeContents() {
        return 0;
    }

    /** Implement the Parcelable interface */
    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeInt(mBand);
        dest.writeInt(mChannel);
        dest.writeInt(mLinkId);
        dest.writeInt(mState);
        dest.writeParcelable(mStaMacAddress, flags);
    }

    /** Implement the Parcelable interface */
    public static final @NonNull Creator<MloLink> CREATOR =
            new Creator<MloLink>() {
                public MloLink createFromParcel(Parcel in) {
                    MloLink link = new MloLink();
                    link.mBand = in.readInt();
                    link.mChannel = in.readInt();
                    link.mLinkId = in.readInt();
                    link.mState = in.readInt();
                    link.mStaMacAddress = in.readParcelable(MacAddress.class.getClassLoader());
                    return link;
                }

                public MloLink[] newArray(int size) {
                    return new MloLink[size];
                }
            };
}
