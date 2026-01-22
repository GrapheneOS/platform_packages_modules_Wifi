/*
 * Copyright (C) 2024 The Android Open Source Project
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
import android.util.Log;

import com.android.internal.annotations.VisibleForTesting;
import com.android.net.module.util.netlink.StructNlAttr;
import com.android.net.module.util.netlink.StructNlMsgHdr;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Representation of a generic Netlink message. Consists of a Netlink header,
 * generic Netlink header, and an optional list of attributes.
 */
public class GenericNetlinkMsg {
    private static final String TAG = "GenericNetlinkMsg";
    public static final int MIN_STRUCT_SIZE =
            StructNlMsgHdr.STRUCT_SIZE + StructGenNlMsgHdr.STRUCT_SIZE;

    public final StructNlMsgHdr nlHeader;
    public final StructGenNlMsgHdr genNlHeader;
    public final Map<Short, StructNlAttr> attributes;

    public GenericNetlinkMsg(short command, short type, short flags, int sequence) {
        attributes = new HashMap<>();
        genNlHeader = new StructGenNlMsgHdr(command);
        nlHeader = new StructNlMsgHdr();
        nlHeader.nlmsg_len = MIN_STRUCT_SIZE;
        nlHeader.nlmsg_pid = 0; // set to 0 when communicating with the kernel
        nlHeader.nlmsg_flags = flags;
        nlHeader.nlmsg_type = type;
        nlHeader.nlmsg_seq = sequence;
    }

    private GenericNetlinkMsg(StructNlMsgHdr nlHeader, StructGenNlMsgHdr genNlHeader,
            Map<Short, StructNlAttr> attributes) {
        this.nlHeader = nlHeader;
        this.genNlHeader = genNlHeader;
        this.attributes = attributes;
    }

    /**
     * Add a new attribute to this message.
     */
    public void addAttribute(@NonNull StructNlAttr attribute) {
        if (attribute == null) {
            return;
        }
        short attributeId = attribute.nla_type;
        if (attributes.containsKey(attributeId)) {
            // Recalculate the total size if this attribute is being replaced
            StructNlAttr oldAttribute = attributes.get(attributeId);
            nlHeader.nlmsg_len -= oldAttribute.getAlignedLength();
        }
        nlHeader.nlmsg_len += attribute.getAlignedLength();
        attributes.put(attributeId, attribute);
    }

    /**
     * Retrieve an existing attribute from this message.
     *
     * @return Attribute if it exists, null otherwise.
     */
    @Nullable
    public StructNlAttr getAttribute(short attributeId) {
        return attributes.get(attributeId);
    }

    /**
     * Retrieve the value of a short attribute, if it exists.
     *
     * @param attributeId of the attribute to retrieve
     * @return value if it exists, or null if an error was encountered
     */
    @Nullable
    public Short getAttributeValueAsShort(short attributeId) {
        StructNlAttr attribute = getAttribute(attributeId);
        if (attribute == null) return null;

        final ByteBuffer byteBuffer = attribute.getValueAsByteBuffer();
        if (byteBuffer == null || byteBuffer.remaining() != Short.BYTES) {
            return null;
        }
        return byteBuffer.getShort();
    }

    /**
     * Retrieve the value of an integer attribute, if it exists.
     *
     * @param attributeId of the attribute to retrieve
     * @return value if it exists, or null if an error was encountered
     */
    @Nullable
    public Integer getAttributeValueAsInteger(short attributeId) {
        StructNlAttr attribute = getAttribute(attributeId);
        if (attribute == null) return null;

        return attribute.getValueAsInteger();
    }

    /**
     * Retrieve the value of a long attribute, if it exists.
     *
     * @param attributeId of the attribute to retrieve
     * @return value if it exists, or null if an error was encountered
     */
    @Nullable
    public Long getAttributeValueAsLong(short attributeId) {
        StructNlAttr attribute = getAttribute(attributeId);
        if (attribute == null) return null;

        return attribute.getValueAsLong();
    }

    /**
     * Retrieve the value of a byte attribute, if it exists.
     *
     * @param attributeId of the attribute to retrieve
     * @return value if it exists, or null if an error was encountered
     */
    @Nullable
    public Byte getAttributeValueAsByte(short attributeId) {
        StructNlAttr attribute = getAttribute(attributeId);
        if (attribute == null) return null;

        final ByteBuffer byteBuffer = attribute.getValueAsByteBuffer();
        if (byteBuffer == null || byteBuffer.remaining() != Byte.BYTES) {
            return null;
        }
        return byteBuffer.get();
    }

    /**
     * Retrieve the value of a byte array attribute, if it exists.
     *
     * @param attributeId of the attribute to retrieve
     * @return value if it exists, or null if an error was encountered
     */
    @Nullable
    public byte[] getAttributeValueAsByteArray(short attributeId) {
        StructNlAttr attribute = getAttribute(attributeId);
        if (attribute == null) return null;
        return attribute.nla_value;
    }

    /**
     * Retrieve the value of a string attribute, if it exists.
     *
     * @param attributeId of the attribute to retrieve
     * @return value if it exists, or null if an error was encountered
     */
    @Nullable
    public String getAttributeValueAsString(short attributeId) {
        StructNlAttr attribute = getAttribute(attributeId);
        if (attribute == null) return null;
        return attribute.getValueAsString();
    }

    /**
     * Retrieve the inner attributes from a nested attribute.
     *
     * @param outerAttribute containing nested inner attributes
     * @return Inner attributes if valid, or null otherwise.
     *         Attributes will be represented as an (attributeId -> attribute) map.
     */
    public static @Nullable Map<Short, StructNlAttr> getInnerNestedAttributes(
            @NonNull StructNlAttr outerAttribute) {
        if (outerAttribute == null) return null;
        // Outer attribute's payload is a byte buffer containing additional attributes
        ByteBuffer innerBytes = outerAttribute.getValueAsByteBuffer();
        if (innerBytes == null) return null;
        return parseAttributesToMap(innerBytes, innerBytes.remaining());
    }

    /**
     * Check that this message contains the expected fields.
     */
    public boolean verifyFields(short command, short... attributeIds) {
        if (command != genNlHeader.command) {
            Log.e(TAG, "Found unexpected command. expected=" + command
                    + ", actual=" + genNlHeader.command);
            return false;
        }
        for (short attributeId : attributeIds) {
            if (!attributes.containsKey(attributeId)) {
                Log.e(TAG, "Message does not contain any attribute with id=" + attributeId);
                return false;
            }
        }
        return true;
    }

    /**
     * @return true if the provided Netlink flag is enabled.
     */
    public boolean isFlagEnabled(short flag) {
        return (nlHeader.nlmsg_flags & flag) == flag;
    }

    /*
     * @return The command identifier from the generic Netlink header.
     */
    public short getCommand() {
        return genNlHeader.command;
    }

    /**
     * Write this StructNl80211Msg to a new byte array.
     */
    public byte[] toByteArray() {
        byte[] bytes = new byte[nlHeader.nlmsg_len];
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        this.pack(buffer);
        return bytes;
    }

    /**
     * Write this GenericNetlinkMsg to a ByteBuffer.
     */
    public void pack(@NonNull ByteBuffer byteBuffer) {
        if (byteBuffer == null || byteBuffer.remaining() < nlHeader.nlmsg_len) {
            return;
        }
        // Nl80211 expects the message to be in native byte order
        ByteOrder originalByteOrder = byteBuffer.order();
        byteBuffer.order(ByteOrder.nativeOrder());

        nlHeader.pack(byteBuffer);
        genNlHeader.pack(byteBuffer);

        for (StructNlAttr attribute : attributes.values()) {
            attribute.pack(byteBuffer);
        }
        byteBuffer.order(originalByteOrder);
    }

    /**
     * Parse attributes from a ByteBuffer to an (attributeId -> attribute) map.
     *
     * @param byteBuffer containing the attributes
     * @param expectedSize of all the attributes
     * @return map containing the parsed attributes, or null if an error occurred
     */
    @VisibleForTesting
    protected static @Nullable Map<Short, StructNlAttr> parseAttributesToMap(
            @NonNull ByteBuffer byteBuffer, int expectedSize) {
        if (byteBuffer == null) return null;
        Map<Short, StructNlAttr> attributes = new HashMap<>();
        int remainingSize = expectedSize;

        while (remainingSize >= StructNlAttr.NLA_HEADERLEN) {
            StructNlAttr attribute = StructNlAttr.parse(byteBuffer);
            if (attribute == null) {
                Log.e(TAG, "Unable to parse attribute. bufRemaining=" + byteBuffer.remaining());
                return null;
            }
            remainingSize -= attribute.getAlignedLength();
            if (remainingSize < 0) {
                Log.e(TAG, "Attribute is larger than the remaining size. attributeSize="
                        + attribute.getAlignedLength() + ", remainingSize=" + remainingSize);
                return null;
            }
            attributes.put(attribute.nla_type, attribute);
        }

        if (remainingSize != 0) {
            Log.e(TAG, "Expected size does not match the parsed size. expected=" + expectedSize
                    + ", remaining=" + remainingSize);
            return null;
        }
        return attributes;
    }

    /**
     * Read a GenericNetlinkMsg from a native-order ByteBuffer.
     *
     * @return Parsed GenericNetlinkMsg object, or null if an error occurred.
     */
    @Nullable
    public static GenericNetlinkMsg parse(@NonNull ByteBuffer byteBuffer) {
        if (byteBuffer == null || byteBuffer.remaining() < MIN_STRUCT_SIZE) {
            Log.e(TAG, "Invalid byte buffer received");
            return null;
        }

        StructNlMsgHdr nlHeader = StructNlMsgHdr.parse(byteBuffer);
        StructGenNlMsgHdr genNlHeader = StructGenNlMsgHdr.parse(byteBuffer);
        if (nlHeader == null || genNlHeader == null) {
            Log.e(TAG, "Unable to parse message headers");
            return null;
        }

        int remainingSize = nlHeader.nlmsg_len - MIN_STRUCT_SIZE;
        if (byteBuffer.remaining() < remainingSize) {
            Log.e(TAG, "Byte buffer is smaller than the expected message size");
            return null;
        }
        Map<Short, StructNlAttr> attributes = parseAttributesToMap(byteBuffer, remainingSize);
        if (attributes == null) return null;
        return new GenericNetlinkMsg(nlHeader, genNlHeader, attributes);
    }

    private static boolean attributesAreEqual(Map<Short, StructNlAttr> attributes,
            Map<Short, StructNlAttr> otherAttributes) {
        if (attributes.size() != otherAttributes.size()) return false;
        Set<Short> attributeIds = attributes.keySet();
        Set<Short> otherAttributeIds = otherAttributes.keySet();
        if (!attributeIds.containsAll(otherAttributeIds)) return false;

        for (short attributeId : attributeIds) {
            StructNlAttr attribute = attributes.get(attributeId);
            StructNlAttr otherAttribute = otherAttributes.get(attributeId);
            if (!Arrays.equals(attribute.nla_value, otherAttribute.nla_value)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if (o == null || !(o instanceof GenericNetlinkMsg)) return false;
        GenericNetlinkMsg other = (GenericNetlinkMsg) o;
        return this.nlHeader.nlmsg_len == other.nlHeader.nlmsg_len
                && this.nlHeader.nlmsg_flags == other.nlHeader.nlmsg_flags
                && this.nlHeader.nlmsg_pid == other.nlHeader.nlmsg_pid
                && this.nlHeader.nlmsg_seq == other.nlHeader.nlmsg_seq
                && this.nlHeader.nlmsg_type == other.nlHeader.nlmsg_type
                && this.genNlHeader.equals(other.genNlHeader)
                && attributesAreEqual(this.attributes, other.attributes);
    }

    @Override
    public int hashCode() {
        int hash = Objects.hash(nlHeader.nlmsg_len, nlHeader.nlmsg_flags, nlHeader.nlmsg_pid,
                nlHeader.nlmsg_seq, nlHeader.nlmsg_type, genNlHeader.hashCode());
        // Sort attributes to guarantee hashing order
        List<Short> sortedAttributeIds = new ArrayList<>(attributes.keySet());
        Collections.sort(sortedAttributeIds);
        for (short attributeId : sortedAttributeIds) {
            StructNlAttr attribute = attributes.get(attributeId);
            hash = Objects.hash(hash, attribute.nla_type, Arrays.hashCode(attribute.nla_value));
        }
        return hash;
    }

    @Override
    public String toString() {
        return "GenericNetlinkMsg{ "
                + "nlHeader{" + nlHeader + "}, "
                + "genNlHeader{" + genNlHeader + "}, "
                + "attributes{" + attributes.values() + "} "
                + "}";
    }
}
