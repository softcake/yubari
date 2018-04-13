/*
 * Copyright 2018 softcake.org.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.softcake.yubari.netty;

import static io.netty.util.AttributeKey.valueOf;

import org.softcake.yubari.netty.channel.ChannelAttachment;
import org.softcake.yubari.netty.channel.ChannelTrafficBlocker;

import com.dukascopy.dds4.transport.common.protocol.binary.SessionProtocolDecoder;
import com.dukascopy.dds4.transport.common.protocol.binary.SessionProtocolEncoder;
import io.netty.buffer.ByteBuf;
import io.netty.util.AttributeKey;
import io.netty.util.internal.PendingWrite;

import java.util.ArrayDeque;
import java.util.ArrayList;

public final class TransportAttributeKeys {
    private TransportAttributeKeys() {

        throw new IllegalAccessError("Utility class");
    }

    static final AttributeKey<Integer> PROTOCOL_VERSION_ATTRIBUTE_KEY = valueOf("protocol_version");
    static final AttributeKey<SessionProtocolEncoder> ENCODER_ATTACHMENT_ATTRIBUTE_KEY
        = valueOf("encoder_attachment");
    static final AttributeKey<SessionProtocolDecoder> DECODER_ATTACHMENT_ATTRIBUTE_KEY
        = valueOf("decoder_attachment");
    static final AttributeKey<ArrayList<PendingWrite>> PROTOCOL_VERSION_MESSAGE_QUEUE_ATTRIBUTE_KEY
        = valueOf("protocol_version_message_queue");
    static final AttributeKey<ByteBuf> PROTOCOL_VERSION_SERVER_RESPONSE_BUFFER_ATTRIBUTE_KEY
        = valueOf("protocol_version_response_buffer");
    public static final AttributeKey<ChannelAttachment> CHANNEL_ATTACHMENT_ATTRIBUTE_KEY
        = valueOf("channel_attachment");
    public static final AttributeKey<Boolean> READ_SUSPENDED
        = valueOf(String.format("%s.READ_SUSPENDED", ChannelTrafficBlocker.class.getName()));
    public static final AttributeKey<ArrayDeque<Object>> MESSAGES_BUFFER
        = valueOf(String.format("%s.MESSAGES_BUFFER", ChannelTrafficBlocker.class.getName()));
}
