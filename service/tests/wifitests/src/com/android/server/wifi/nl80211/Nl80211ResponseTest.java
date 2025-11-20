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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

/**
 * Unit tests for {@link Nl80211Response}.
 */
public class Nl80211ResponseTest {

    @Test
    public void testConstructorWithSingleMessage() {
        GenericNetlinkMsg msg = Nl80211TestUtils.createTestMessage();
        Nl80211Response response = new Nl80211Response(msg);

        assertEquals(0, response.getErrorCode());
        assertFalse(response.isError());
        assertEquals(msg, response.getMessage());
        assertNotNull(response.getMessages());
        assertEquals(1, response.getMessages().size());
        assertEquals(msg, response.getMessages().get(0));
    }

    @Test
    public void testConstructorWithMultipleMessages() {
        GenericNetlinkMsg msg1 = Nl80211TestUtils.createTestMessage();
        GenericNetlinkMsg msg2 = Nl80211TestUtils.createTestMessageWithAttributes();
        Nl80211Response response = new Nl80211Response(msg1, msg2);

        assertEquals(0, response.getErrorCode());
        assertFalse(response.isError());
        assertEquals(msg1, response.getMessage());
        assertNotNull(response.getMessages());
        assertEquals(2, response.getMessages().size());
        assertEquals(List.of(msg1, msg2), response.getMessages());
    }

    @Test
    public void testConstructorWithErrorCode() {
        int errorCode = -1;
        Nl80211Response response = new Nl80211Response(errorCode);

        assertEquals(errorCode, response.getErrorCode());
        assertTrue(response.isError());
        assertNull(response.getMessage());
        assertNotNull(response.getMessages());
        assertTrue(response.getMessages().isEmpty());
    }

    @Test
    public void testEquals() {
        GenericNetlinkMsg msg = Nl80211TestUtils.createTestMessage();
        Nl80211Response response1 = new Nl80211Response(msg);
        Nl80211Response response2 = new Nl80211Response(msg);
        Nl80211Response response3 = new Nl80211Response(-1);
        Nl80211Response response4 = new Nl80211Response(-1);
        Nl80211Response response5 =
                new Nl80211Response(Nl80211TestUtils.createTestMessageWithAttributes());

        assertEquals(response1, response2);
        assertEquals(response3, response4);
        assertNotEquals(response1, response3);
        assertNotEquals(response1, response5);
        assertNotEquals(response1, null);
        assertNotEquals(response1, new Object());
    }

    @Test
    public void testHashCode() {
        GenericNetlinkMsg msg = Nl80211TestUtils.createTestMessage();
        Nl80211Response response1 = new Nl80211Response(msg);
        Nl80211Response response2 = new Nl80211Response(msg);
        Nl80211Response response3 = new Nl80211Response(-1);
        Nl80211Response response4 = new Nl80211Response(-1);

        // Equal objects must have equal hash codes
        assertEquals(response1.hashCode(), response2.hashCode());
        assertEquals(response3.hashCode(), response4.hashCode());
        // Non-equal objects are not required to have different hash codes, but it's good practice
        // to check they are different in typical cases.
        assertNotEquals(response1.hashCode(), response3.hashCode());
    }
}
