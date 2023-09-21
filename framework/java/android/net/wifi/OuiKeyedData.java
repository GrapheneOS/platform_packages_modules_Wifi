/*
 * Copyright (C) 2023 The Android Open Source Project
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

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.SystemApi;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;

import com.android.modules.utils.build.SdkLevel;

import java.util.Objects;

/**
 * Vendor-provided data for HAL configuration.
 *
 * @hide
 */
@SystemApi
public final class OuiKeyedData implements Parcelable {
    private static final String TAG = "OuiKeyedData";
    private static final String DATA_TAG = "Data";

    /** 24-bit OUI identifier to identify the vendor/OEM. */
    private final int mOui;
    /** Bundle containing the vendor-defined Parcelable. */
    private final Bundle mExtras;

    private OuiKeyedData(int oui, @NonNull Parcelable data) {
        mOui = oui;
        mExtras = new Bundle();
        mExtras.putParcelable(DATA_TAG, data);
    }

    /**
     * Get the OUI for this object.
     *
     * <p>See {@link Builder#Builder(int, Parcelable)}}
     */
    public int getOui() {
        return mOui;
    }

    /**
     * Get the data for this object.
     *
     * <p>See {@link Builder#Builder(int, Parcelable)}}
     */
    public @NonNull Parcelable getData() {
        if (SdkLevel.isAtLeastT()) {
            return mExtras.getParcelable(DATA_TAG, Parcelable.class);
        } else {
            return mExtras.getParcelable(DATA_TAG);
        }
    }

    private static boolean validateOui(int oui) {
        // OUI must be a non-zero 24-bit value.
        return oui != 0 && (oui & 0xFF000000) == 0;
    }

    /**
     * Validate the parameters in this instance.
     *
     * @return true if all parameters are valid, false otherwise
     * @hide
     */
    public boolean validate() {
        return validateOui(mOui) && (getData() != null);
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        OuiKeyedData that = (OuiKeyedData) o;
        return mOui == that.mOui && Objects.equals(getData(), that.getData());
    }

    @Override
    public int hashCode() {
        return Objects.hash(mOui, getData());
    }

    @Override
    public String toString() {
        return "{oui=" + Integer.toHexString(mOui) + ", data=" + getData() + "}";
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeInt(mOui);
        dest.writeBundle(mExtras);
    }

    /** @hide */
    OuiKeyedData(@NonNull Parcel in) {
        this.mOui = in.readInt();
        this.mExtras = in.readBundle(getClass().getClassLoader());
    }

    public static final @NonNull Parcelable.Creator<OuiKeyedData> CREATOR =
            new Parcelable.Creator<OuiKeyedData>() {
                @Override
                public OuiKeyedData createFromParcel(Parcel in) {
                    return new OuiKeyedData(in);
                }

                @Override
                public OuiKeyedData[] newArray(int size) {
                    return new OuiKeyedData[size];
                }
            };

    /** Builder for {@link OuiKeyedData}. */
    public static final class Builder {
        private final int mOui;
        private final @NonNull Parcelable mData;

        /**
         * Constructor for {@link Builder}.
         *
         * @param oui 24-bit OUI identifier to identify the vendor/OEM. See
         *     https://standards-oui.ieee.org/ for more information.
         * @param data Parcelable containing additional configuration data. The Parcelable
         *     definition should be provided by the vendor, and should be known to both the caller
         *     and to the vendor's implementation of the Wi-Fi HALs.
         */
        public Builder(int oui, @NonNull Parcelable data) {
            mOui = oui;
            mData = data;
        }

        /** Construct an OuiKeyedData object with the specified parameters. */
        public @NonNull OuiKeyedData build() {
            OuiKeyedData ouiKeyedData = new OuiKeyedData(mOui, mData);
            if (!ouiKeyedData.validate()) {
                throw new IllegalArgumentException("Provided parameters are invalid");
            }
            return ouiKeyedData;
        }
    }
}
