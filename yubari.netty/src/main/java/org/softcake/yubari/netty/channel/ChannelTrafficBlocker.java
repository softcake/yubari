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

package org.softcake.yubari.netty.channel;

import static org.softcake.yubari.netty.TransportAttributeKeys.MESSAGES_BUFFER;
import static org.softcake.yubari.netty.TransportAttributeKeys.READ_SUSPENDED;

import com.dukascopy.dds4.transport.msg.system.CurrencyMarket;
import com.dukascopy.dds4.transport.msg.system.HeartbeatOkResponseMessage;
import com.dukascopy.dds4.transport.msg.system.HeartbeatRequestMessage;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.Attribute;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;

@Sharable
public class ChannelTrafficBlocker extends ChannelDuplexHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(ChannelTrafficBlocker.class);

    private final String transportName;

    public ChannelTrafficBlocker(final String transportName) {

        this.transportName = transportName;
    }

    public static boolean isSuspended(final Channel channel) {

        Boolean suspended = channel.attr(READ_SUSPENDED).get();
        if (suspended == null) {
            suspended = Boolean.FALSE;
            channel.attr(READ_SUSPENDED).set(suspended);
        }
        return suspended;
    }

    public static boolean isSuspended(final ChannelHandlerContext ctx) {

        return isSuspended(ctx.channel());
    }

    @Override
    public void channelRead(final ChannelHandlerContext ctx, final Object msg) {
        if (msg instanceof CurrencyMarket) {
            final CurrencyMarket cmm = (CurrencyMarket) msg;
            cmm.setCreationTimestamp(System.nanoTime());
        }

        if (isSuspended(ctx)
            && !(msg instanceof HeartbeatRequestMessage)
            && !(msg instanceof HeartbeatOkResponseMessage)) {
            final Attribute<ArrayDeque<Object>> attr = ctx.channel().attr(MESSAGES_BUFFER);
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

    @Override
    public void read(final ChannelHandlerContext ctx) {

        if (!isSuspended(ctx)) {
            final Attribute<ArrayDeque<Object>> attr = ctx.channel().attr(MESSAGES_BUFFER);
            final ArrayDeque<Object> buffer = attr.get();

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

    public void suspend(final ChannelHandlerContext ctx) {

        this.suspend(ctx.channel());
    }

    public void suspend(final Channel channel) {

        channel.attr(READ_SUSPENDED).set(Boolean.TRUE);
        // channel.config().setAutoRead(false);
        LOGGER.debug("[{}] Reading suspended", this.transportName);
    }

    public void resume(final ChannelHandlerContext ctx) {

        this.resume(ctx.channel());
    }

    public void resume(final Channel channel) {

        channel.attr(READ_SUSPENDED).set(false);
        LOGGER.debug("[{}] Reading resumed", this.transportName);
        channel.read();
    }
}
