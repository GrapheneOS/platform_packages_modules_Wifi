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

/**
 * Pairing bootstrapping method flag.
 * See Wi-Fi Aware Specification 4.0 section 9.5.21.7 table 128.
 */
@Backing(type="int")
enum NanBootstrappingMethod {
    OPPORTUNISTIC_MASK = 1 << 0,
    PIN_CODE_DISPLAY_MASK = 1 << 1,
    PASSPHRASE_DISPLAY_MASK = 1 << 2,
    QR_DISPLAY_MASK = 1 << 3,
    NFC_TAG_MASK = 1 << 4,
    PIN_CODE_KEYPAD_MASK = 1 << 5,
    PASSPHRASE_KEYPAD_MASK = 1 << 6,
    QR_SCAN_MASK = 1 << 7,
    NFC_READER_MASK = 1 << 8,
    SERVICE_MANAGED_MASK = 1 << 14,
    HANDSHAKE_SKIPPED_MASK = 1 << 15
}
