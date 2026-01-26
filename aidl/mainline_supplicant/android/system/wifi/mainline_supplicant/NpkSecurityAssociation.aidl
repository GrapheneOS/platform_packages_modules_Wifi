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

package android.system.wifi.mainline_supplicant;

import android.system.wifi.mainline_supplicant.NanCipherSuiteType;
import android.system.wifi.mainline_supplicant.NanPairingAkm;

/**
 * The security association info after Aware Pairing setup.
 */
parcelable NpkSecurityAssociation {
    /**
     * Index of the identity in the cache database.
     */
    int identityId;

    /**
     * The Aware pairing identity from the peer.
     */
    byte[16] peerNanIdentityKey;

    /**
     * The Aware pairing identity lifetime for the peer, in seconds.
     */
    int peerNanIdentityKeyLifetimeSec;

    /**
     * The Aware pairing identity for local device.
     */
    byte[16] localNanIdentityKey;

    /**
     * The Aware pairing identity lifetime for local device, in seconds.
     */
    int localNanIdentityKeyLifetimeSec;

    /**
     * PMK used in this security association.
     */
    byte[32] npk;

    /**
     * AKM used for key exchange in this security association.
     */
    NanPairingAkm akm;

    /**
     * Cipher type for pairing. Must be one of |NanCipherSuiteType.PUBLIC_KEY_PASN_128_MASK| or
     * |NanCipherSuiteType.PUBLIC_KEY_PASN_256_MASK|.
     */
    NanCipherSuiteType cipherType;
}
