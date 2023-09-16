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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.os.Parcel;

import com.android.modules.utils.build.SdkLevel;

import org.junit.Before;
import org.junit.Test;

public class OuiKeyedDataTest {
    // Use WifiSsid as a sample Parcelable.
    private final WifiSsid mTestParcelable = WifiSsid.fromString("\"testData\"");
    private final int mTestOui = 0x00112233;

    @Before
    public void setUp() {
        assumeTrue(SdkLevel.isAtLeastV());
    }

    /** Tests that the builder throws an exception if given an invalid OUI. */
    @Test
    public void testBuilderInvalidOui() {
        int invalidOui = 0;
        final OuiKeyedData.Builder zeroOuiBuilder =
                new OuiKeyedData.Builder(invalidOui, mTestParcelable);
        assertThrows(IllegalArgumentException.class, () -> zeroOuiBuilder.build());

        invalidOui = 0x11000000; // larger than 24 bits
        final OuiKeyedData.Builder invalidOuiBuilder =
                new OuiKeyedData.Builder(invalidOui, mTestParcelable);
        assertThrows(IllegalArgumentException.class, () -> invalidOuiBuilder.build());
    }

    /** Tests that the builder throws an exception if given a null data value. */
    @Test
    public void testBuilderNullData() {
        final OuiKeyedData.Builder builder = new OuiKeyedData.Builder(mTestOui, null);
        assertThrows(IllegalArgumentException.class, () -> builder.build());
    }

    /** Tests that this class can be properly parceled and unparceled. */
    @Test
    public void testParcelReadWrite() {
        OuiKeyedData data = new OuiKeyedData.Builder(mTestOui, mTestParcelable).build();
        Parcel parcel = Parcel.obtain();
        data.writeToParcel(parcel, 0);
        parcel.setDataPosition(0); // Rewind data position back to the beginning for read.
        OuiKeyedData unparceledData = OuiKeyedData.CREATOR.createFromParcel(parcel);

        assertTrue(data.equals(unparceledData));
        assertEquals(mTestOui, unparceledData.getOui());
        assertTrue(mTestParcelable.equals(unparceledData.getData()));
    }

    /** Tests the equality and hashcode operations on equivalent instances. */
    @Test
    public void testSameObjectComparison() {
        OuiKeyedData data1 = new OuiKeyedData.Builder(mTestOui, mTestParcelable).build();
        OuiKeyedData data2 = new OuiKeyedData.Builder(mTestOui, mTestParcelable).build();
        assertTrue(data1.equals(data2));
        assertEquals(data1.hashCode(), data2.hashCode());
    }

    /** Tests the equality and hashcode operations on different instances. */
    @Test
    public void testDifferentObjectComparison() {
        WifiSsid otherParcelable = WifiSsid.fromString("\"otherData\"");
        OuiKeyedData data1 = new OuiKeyedData.Builder(mTestOui, mTestParcelable).build();
        OuiKeyedData data2 = new OuiKeyedData.Builder(mTestOui, otherParcelable).build();
        assertFalse(data1.equals(data2));
        assertNotEquals(data1.hashCode(), data2.hashCode());
    }
}
