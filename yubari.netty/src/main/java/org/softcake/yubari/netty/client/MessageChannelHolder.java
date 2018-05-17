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

package org.softcake.yubari.netty.client;

import com.dukascopy.dds4.transport.msg.system.ProtocolMessage;
import io.netty.channel.Channel;


/**
 * @author René Neubert
 */
public class MessageChannelHolder {
    private ProtocolMessage message;

    public ProtocolMessage getMessage() {

        return message;
    }

    public Channel getChannel() {

        return channel;
    }

    private Channel channel;


    public MessageChannelHolder(final ProtocolMessage message, final Channel channel) {

        this.message = message;
        this.channel = channel;
    }
}
