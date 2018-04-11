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

import com.dukascopy.dds4.transport.common.protocol.binary.SessionProtocolEncoder;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.util.Attribute;
import io.netty.util.AttributeKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Sharable
public class ProtocolVersionClientNegotiatorHandler extends ChannelDuplexHandler {
    private static final String TRANSPORT_HELLO_MESSAGE = "DDS4TRANSPORT";
    private static final int SEVER_RESPONSE_MESSAGE_SIZE = TRANSPORT_HELLO_MESSAGE.length() + 2 + 4;
    private static final AttributeKey<ByteBuf>
        PROTOCOL_VERSION_SERVER_RESPONSE_BUFFER_ATTRIBUTE_KEY
        = AttributeKey.valueOf("protocol_version_response_buffer");
    private static final Logger LOGGER = LoggerFactory.getLogger(ProtocolVersionClientNegotiatorHandler.class);

    @Override
    public void channelActive(final ChannelHandlerContext ctx) throws Exception {

        final Attribute<ByteBuf> byteBufAttribute = ctx.channel().attr(
            PROTOCOL_VERSION_SERVER_RESPONSE_BUFFER_ATTRIBUTE_KEY);
        byteBufAttribute.set(ctx.alloc().buffer(SEVER_RESPONSE_MESSAGE_SIZE));
        final ByteBuf response = ctx.alloc().buffer(TRANSPORT_HELLO_MESSAGE.length()
                                                    + 2
                                                    + SessionProtocolEncoder.SUPPORTED_VERSIONS.size() * 4);
        response.writeBytes("DDS4TRANSPORT".getBytes("US-ASCII"));
        response.writeShort(SessionProtocolEncoder.SUPPORTED_VERSIONS.size() * 4);

        SessionProtocolEncoder.SUPPORTED_VERSIONS.forEach(response::writeInt);

        LOGGER.trace("Sending supported versions list: {}", SessionProtocolEncoder.SUPPORTED_VERSIONS);
        ctx.writeAndFlush(response);
        ctx.fireChannelActive();
    }

    public void handlerRemoved(final ChannelHandlerContext ctx) throws Exception {

        this.cleanUp(ctx);
        super.handlerRemoved(ctx);
    }

    public void channelInactive(final ChannelHandlerContext ctx) throws Exception {

        this.cleanUp(ctx);
        super.channelInactive(ctx);
    }

    private void cleanUp(final ChannelHandlerContext ctx) throws Exception {

        final Attribute<ByteBuf> byteBufAttribute = ctx.channel().attr(
            PROTOCOL_VERSION_SERVER_RESPONSE_BUFFER_ATTRIBUTE_KEY);
        final ByteBuf byteBuf = byteBufAttribute.get();
        if (byteBuf != null) {
            byteBuf.release();

            assert byteBuf.refCnt() == 0;

            byteBufAttribute.set(null);
        }

    }

    public void channelRead(final ChannelHandlerContext ctx, final Object message) throws Exception {

        if (!(message instanceof ByteBuf)) {
            ctx.fireChannelRead(message);
            return;
        }


        final ByteBuf byteBuffer = (ByteBuf) message;

        try {
            final Attribute<Integer> firstMessageAttribute = ctx.channel()
                                                                .attr(ProtocolEncoderDecoder.PROTOCOL_VERSION_ATTRIBUTE_KEY);

            final Object firstMessage = firstMessageAttribute.get();

            if (firstMessage == null) {
                final Attribute<ByteBuf> byteBufAttribute = ctx.channel().attr(
                    PROTOCOL_VERSION_SERVER_RESPONSE_BUFFER_ATTRIBUTE_KEY);
                final ByteBuf serverResponseBuf = byteBufAttribute.get();
                serverResponseBuf.writeBytes(byteBuffer,
                                             Math.min(SEVER_RESPONSE_MESSAGE_SIZE - serverResponseBuf.readableBytes(),
                                                      byteBuffer.readableBytes()));
                if (serverResponseBuf.readableBytes() >= SEVER_RESPONSE_MESSAGE_SIZE) {
                    try {
                        final byte[] helloMessageArray = new byte["DDS4TRANSPORT".length()];
                        serverResponseBuf.readBytes(helloMessageArray);
                        final String transportHelloMessage = new String(helloMessageArray, "US-ASCII");
                        if (transportHelloMessage.equals("DDS4TRANSPORT")) {
                            final int bodyLength = (serverResponseBuf.readByte() & 255) << 8
                                                   | serverResponseBuf.readByte() & 255;
                            if (bodyLength != 4) {
                                LOGGER.error("Server sent the incorrect version negotiation response, data.length != "
                                             + "4");
                                ctx.close();
                            } else {
                                assert serverResponseBuf.readableBytes() == bodyLength;

                                final byte[] data = new byte[bodyLength];
                                serverResponseBuf.readBytes(data);
                                final int acceptedVersion = (data[0] & 255) << 24
                                                            | (data[1] & 255) << 16
                                                            | (data[2] & 255) << 8
                                                            | data[3] & 255;
                                if (SessionProtocolEncoder.SUPPORTED_VERSIONS.contains(acceptedVersion)) {
                                    final Attribute<Integer> protocolVersionAttribute = ctx.channel().attr(
                                        ProtocolEncoderDecoder.PROTOCOL_VERSION_ATTRIBUTE_KEY);
                                    protocolVersionAttribute.set(acceptedVersion);

                                    LOGGER.trace("Server responded with version: {}", acceptedVersion);


                                    ctx.fireUserEventTriggered(ProtocolVersionNegotiationSuccessEvent.SUCCESS);
                                } else {
                                    if (acceptedVersion < 0) {
                                        LOGGER.error("Server has sent error message while negotiating protocol "
                                                     + "version");
                                    } else {
                                        LOGGER.error("Server has sent version that client does not support");
                                    }

                                    ctx.close();
                                }
                            }
                        } else {
                            LOGGER.warn("Server did not send transport version negotiation message");
                            ctx.close();
                        }
                    } finally {
                        serverResponseBuf.release();
                        byteBufAttribute.set(null);
                    }
                }

                if (byteBuffer.readableBytes() > 0) {
                    byteBuffer.retain();
                    ctx.fireChannelRead(byteBuffer);
                }
            } else {
                byteBuffer.retain();
                ctx.fireChannelRead(byteBuffer);
            }
        } finally {
            byteBuffer.release();
        }


    }

    public void write(final ChannelHandlerContext ctx, final Object msg, final ChannelPromise promise)
        throws Exception {

        final Attribute<Integer> firstMessageAttribute = ctx.channel()
                                                            .attr(ProtocolEncoderDecoder
                                                                      .PROTOCOL_VERSION_ATTRIBUTE_KEY);
        final Object firstMessage = firstMessageAttribute.get();
        if (firstMessage == null) {
            LOGGER.warn("Received write request while version is not negotiated yet");
            throw new Exception("Received write request while version is not negotiated yet");
        } else {
            ctx.write(msg, promise);
        }
    }
}
