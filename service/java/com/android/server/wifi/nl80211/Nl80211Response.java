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

import android.annotation.NonNull;
import android.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Representation of an Nl80211 response containing a (possibly empty) list of Generic Netlink
 * messages. If the response was NLMSG_ERROR, the error code will be available in
 * {@link #getErrorCode()}.
 */
public class Nl80211Response {
    @NonNull
    private final List<GenericNetlinkMsg> mMessages;
    private final int mErrorCode;

    public Nl80211Response(@NonNull GenericNetlinkMsg... messages) {
        mMessages = List.of(messages);
        mErrorCode = 0;
    }

    public Nl80211Response(int errorCode) {
        mMessages = new ArrayList<>();
        mErrorCode = errorCode;
    }

    /**
     * Gets the response message, or null if there was none.
     */
    @Nullable
    public GenericNetlinkMsg getMessage() {
        if (mMessages.isEmpty()) return null;
        return mMessages.get(0);
    }

    /**
     * Gets the response messages, or an empty list if there was none.
     */
    @NonNull
    public List<GenericNetlinkMsg> getMessages() {
        return mMessages;
    }

    /**
     * Gets the POSIX error code if the response was an NLMSG_ERROR.
     */
    public int getErrorCode() {
        return mErrorCode;
    }

    /**
     * Returns true if there is a non-zero error code in the response.
     */
    public boolean isError() {
        return mErrorCode != 0;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Nl80211Response)) return false;
        Nl80211Response that = (Nl80211Response) o;
        return mErrorCode == that.mErrorCode && Objects.equals(mMessages, that.mMessages);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mMessages, mErrorCode);
    }

    @Override
    public String toString() {
        return "Nl80211Response{" + "mMessages=" + mMessages + ", mErrorCode=" + mErrorCode + '}';
    }
}
