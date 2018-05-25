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

package org.softcake.yubari.netty.authorization;


import static org.softcake.yubari.netty.TransportAttributeKeys.CHANNEL_ATTACHMENT_ATTRIBUTE_KEY;

import org.softcake.yubari.netty.ProtocolVersionNegotiationCompletionEvent;
import org.softcake.yubari.netty.channel.ChannelAttachment;

import com.dukascopy.dds4.transport.msg.system.ProtocolMessage;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractClientAuthorizationProvider extends SimpleChannelInboundHandler<ProtocolMessage>
    implements ClientAuthorizationProvider {
    protected static final Logger LOGGER = LoggerFactory.getLogger(DefaultClientAuthorizationProvider.class);


    public AbstractClientAuthorizationProvider() {

    }

    @Override
    protected void channelRead0(final ChannelHandlerContext ctx, final ProtocolMessage msg) throws Exception {

        processAuthorizationMessage(ctx, msg);
        ctx.fireChannelRead(msg);
    }

    @Override
    public void userEventTriggered(final ChannelHandlerContext ctx, final Object evt) throws Exception {

        super.userEventTriggered(ctx, evt);


        if (evt instanceof ProtocolVersionNegotiationCompletionEvent
            && ((ProtocolVersionNegotiationCompletionEvent) evt).isSuccess()) {

            final ChannelAttachment channelAttachment = ctx.channel()
                                                           .attr(CHANNEL_ATTACHMENT_ATTRIBUTE_KEY)
                                                           .get();
            if (channelAttachment == null) {
                return;
            }
            if (channelAttachment.isPrimaryConnection()) {

                handleAuthorization(ctx);
            }
        }
    }

    abstract void handleAuthorization(final ChannelHandlerContext ctx);

    abstract void processAuthorizationMessage(final ChannelHandlerContext ctx,
                                              final ProtocolMessage protocolMessage);


}


