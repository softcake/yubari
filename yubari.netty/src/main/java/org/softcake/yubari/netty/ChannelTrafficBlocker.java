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

import com.dukascopy.dds4.transport.msg.system.HeartbeatOkResponseMessage;
import com.dukascopy.dds4.transport.msg.system.HeartbeatRequestMessage;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.Attribute;
import io.netty.util.AttributeKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;

@Sharable
public class ChannelTrafficBlocker extends ChannelDuplexHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(ChannelTrafficBlocker.class);
    private static final AttributeKey<Boolean>
        READ_SUSPENDED
        = AttributeKey.valueOf(ChannelTrafficBlocker.class.getName() + ".READ_SUSPENDED");
    private static final AttributeKey<ArrayDeque<Object>>
        MESSAGES_BUFFER
        = AttributeKey.valueOf(ChannelTrafficBlocker.class.getName() + ".MESSAGES_BUFFER");
    private final String transportName;

    public ChannelTrafficBlocker(String transportName) {

        this.transportName = transportName;
    }

    public void channelRead(ChannelHandlerContext ctx, Object msg) {

        if (isSuspended(ctx)
            && !(msg instanceof HeartbeatRequestMessage)
            && !(msg instanceof HeartbeatOkResponseMessage)) {
            Attribute<ArrayDeque<Object>> attr = ctx.channel().attr(MESSAGES_BUFFER);
            ArrayDeque<Object> buffer = attr.get();
            if (buffer == null) {
                buffer = new ArrayDeque<>();
                attr.set(buffer);
            }

            buffer.addLast(msg);
        } else {
            ctx.fireChannelRead(msg);
        }

    }

    public void read(ChannelHandlerContext ctx) {

        if (!isSuspended(ctx)) {
            Attribute<ArrayDeque<Object>> attr = ctx.channel().attr(MESSAGES_BUFFER);
            ArrayDeque<Object> buffer = attr.get();

            if (buffer != null && !buffer.isEmpty()) {
                Object msg;
                do {
                    msg = buffer.pollFirst();
                    if (msg != null) {
                        ctx.fireChannelRead(msg);
                    }
                } while (msg != null && !isSuspended(ctx));

                if (!isSuspended(ctx)) {

                    LOGGER.trace("[{}] Passing read request further", this.transportName);


                    ctx.channel().config().setAutoRead(true);
                    ctx.read();
                }
            } else {
                if (!ctx.channel().config().isAutoRead()) {
                    LOGGER.trace("[{}] Passing read request further", this.transportName);
                }

                ctx.channel().config().setAutoRead(true);
                ctx.read();
            }
        }

    }

    public static boolean isSuspended(Channel channel) {

        Boolean suspended = channel.attr(READ_SUSPENDED).get();
        return suspended != null && !Boolean.FALSE.equals(suspended);
    }

    public static boolean isSuspended(ChannelHandlerContext ctx) {

        return isSuspended(ctx.channel());
    }

    public void suspend(ChannelHandlerContext ctx) {

        this.suspend(ctx.channel());
    }

    public void suspend(Channel channel) {

        channel.attr(READ_SUSPENDED).set(true);
        channel.config().setAutoRead(false);
        LOGGER.trace("[{}] Reading suspended", this.transportName);
    }

    public void resume(ChannelHandlerContext ctx) {

        this.resume(ctx.channel());
    }

    public void resume(Channel channel) {

        channel.attr(READ_SUSPENDED).set(false);
        LOGGER.trace("[{}] Reading resumed", this.transportName);
        channel.read();
    }
}
