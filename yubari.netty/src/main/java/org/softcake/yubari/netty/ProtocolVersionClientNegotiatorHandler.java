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
import com.dukascopy.dds4.transport.netty.ProtocolEncoderDecoder;
import com.dukascopy.dds4.transport.netty.ProtocolVersionNegotiationSuccessEvent;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.util.Attribute;
import io.netty.util.AttributeKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Iterator;

@Sharable
public class ProtocolVersionClientNegotiatorHandler extends ChannelDuplexHandler {
    public static final String TRANSPORT_HELLO_MESSAGE = "DDS4TRANSPORT";
    public static final int SEVER_RESPONSE_MESSAGE_SIZE = "DDS4TRANSPORT".length() + 2 + 4;
    public static final AttributeKey<ByteBuf>
        PROTOCOL_VERSION_SERVER_RESPONSE_BUFFER_ATTRIBUTE_KEY
        = AttributeKey.valueOf("protocol_version_response_buffer");
    private static final Logger LOGGER = LoggerFactory.getLogger(ProtocolVersionClientNegotiatorHandler.class);

    public ProtocolVersionClientNegotiatorHandler() {

    }

    public void channelActive(ChannelHandlerContext ctx) throws Exception {

        Attribute<ByteBuf> byteBufAttribute = ctx.channel().attr(PROTOCOL_VERSION_SERVER_RESPONSE_BUFFER_ATTRIBUTE_KEY);
        byteBufAttribute.set(ctx.alloc().buffer(SEVER_RESPONSE_MESSAGE_SIZE));
        ByteBuf response = ctx.alloc().buffer("DDS4TRANSPORT".length()
                                              + 2
                                              + SessionProtocolEncoder.SUPPORTED_VERSIONS.size() * 4);
        response.writeBytes("DDS4TRANSPORT".getBytes("US-ASCII"));
        response.writeShort(SessionProtocolEncoder.SUPPORTED_VERSIONS.size() * 4);
        Iterator i$ = SessionProtocolEncoder.SUPPORTED_VERSIONS.iterator();

        while (i$.hasNext()) {
            Integer supportedVersion = (Integer) i$.next();
            response.writeInt(supportedVersion);
        }

        LOGGER.trace("Sending supported versions list: {}", SessionProtocolEncoder.SUPPORTED_VERSIONS);
        ctx.writeAndFlush(response);
        ctx.fireChannelActive();
    }

    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {

        this.cleanUp(ctx);
        super.handlerRemoved(ctx);
    }

    public void channelInactive(ChannelHandlerContext ctx) throws Exception {

        this.cleanUp(ctx);
        super.channelInactive(ctx);
    }

    private void cleanUp(ChannelHandlerContext ctx) throws Exception {

        Attribute<ByteBuf> byteBufAttribute = ctx.channel().attr(PROTOCOL_VERSION_SERVER_RESPONSE_BUFFER_ATTRIBUTE_KEY);
        ByteBuf byteBuf = (ByteBuf) byteBufAttribute.get();
        if (byteBuf != null) {
            byteBuf.release();

            assert byteBuf.refCnt() == 0;

            byteBufAttribute.set(null);
        }

    }

    public void channelRead(ChannelHandlerContext ctx, Object message) throws Exception {

        if (!(message instanceof ByteBuf)) {
            ctx.fireChannelRead(message);
        } else {
            ByteBuf byteBuffer = (ByteBuf) message;

            try {
                Attribute<Integer> firstMessageAttribute = ctx.channel()
                                                              .attr(ProtocolEncoderDecoder
                                                                        .PROTOCOL_VERSION_ATTRIBUTE_KEY);
                Object firstMessage = firstMessageAttribute.get();
                if (firstMessage == null) {
                    Attribute<ByteBuf> byteBufAttribute = ctx.channel().attr(
                        PROTOCOL_VERSION_SERVER_RESPONSE_BUFFER_ATTRIBUTE_KEY);
                    ByteBuf serverResponseBuf = (ByteBuf) byteBufAttribute.get();
                    serverResponseBuf.writeBytes(byteBuffer,
                                                 Math.min(SEVER_RESPONSE_MESSAGE_SIZE
                                                          - serverResponseBuf.readableBytes(),
                                                          byteBuffer.readableBytes()));
                    if (serverResponseBuf.readableBytes() >= SEVER_RESPONSE_MESSAGE_SIZE) {
                        try {
                            byte[] helloMessageArray = new byte["DDS4TRANSPORT".length()];
                            serverResponseBuf.readBytes(helloMessageArray);
                            String transportHelloMessage = new String(helloMessageArray, "US-ASCII");
                            if (transportHelloMessage.equals("DDS4TRANSPORT")) {
                                int bodyLength = (serverResponseBuf.readByte() & 255) << 8
                                                 | serverResponseBuf.readByte() & 255;
                                if (bodyLength != 4) {
                                    LOGGER.error(
                                        "Server sent the incorrect version negotiation response, data.length != 4");
                                    ctx.close();
                                } else {
                                    assert serverResponseBuf.readableBytes() == bodyLength;

                                    byte[] data = new byte[bodyLength];
                                    serverResponseBuf.readBytes(data);
                                    int acceptedVersion = (data[0] & 255) << 24
                                                          | (data[1] & 255) << 16
                                                          | (data[2] & 255) << 8
                                                          | data[3] & 255;
                                    if (SessionProtocolEncoder.SUPPORTED_VERSIONS.contains(acceptedVersion)) {
                                        Attribute<Integer> protocolVersionAttribute = ctx.channel().attr(
                                            ProtocolEncoderDecoder.PROTOCOL_VERSION_ATTRIBUTE_KEY);
                                        protocolVersionAttribute.set(acceptedVersion);
                                        if (LOGGER.isTraceEnabled()) {
                                            LOGGER.trace("Server responded with version: {}", acceptedVersion);
                                        }

                                        ctx.fireUserEventTriggered(ProtocolVersionNegotiationSuccessEvent.SUCCESS);
                                    } else {
                                        if (acceptedVersion < 0) {
                                            LOGGER.error(
                                                "Server has sent error message while negotiating protocol version");
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

    }

    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {

        Attribute<Integer> firstMessageAttribute = ctx.channel()
                                                      .attr(ProtocolEncoderDecoder.PROTOCOL_VERSION_ATTRIBUTE_KEY);
        Object firstMessage = firstMessageAttribute.get();
        if (firstMessage == null) {
            LOGGER.warn("Received write request while version is not negotiated yet");
            throw new Exception("Received write request while version is not negotiated yet");
        } else {
            ctx.write(msg, promise);
        }
    }
}
