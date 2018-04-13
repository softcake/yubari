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

import static org.softcake.yubari.netty.TransportAttributeKeys.PROTOCOL_VERSION_ATTRIBUTE_KEY;
import static org.softcake.yubari.netty.TransportAttributeKeys.PROTOCOL_VERSION_SERVER_RESPONSE_BUFFER_ATTRIBUTE_KEY;

import com.dukascopy.dds4.transport.common.protocol.binary.SessionProtocolEncoder;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import io.netty.util.Attribute;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.UnsupportedEncodingException;

/**
 * {@link ChannelDuplexHandler} implementation which acts as negotiator for protocol version handling.
 *
 * @author The softcake Authors.
 */
@Sharable
public class ProtocolVersionClientNegotiatorHandler extends ChannelDuplexHandler {
    private static final int BODY_LENGTH = 4;
    private static final String TRANSPORT_HELLO_MESSAGE = "DDS4TRANSPORT";
    private static final int SEVER_RESPONSE_MESSAGE_SIZE = TRANSPORT_HELLO_MESSAGE.length() + 2 + 4;
    private final String transportName;
    private static final Logger LOGGER = LoggerFactory.getLogger(ProtocolVersionClientNegotiatorHandler.class);

    public ProtocolVersionClientNegotiatorHandler(final String transportName) {

        this.transportName = transportName;
    }

    private static void cleanUp(final ChannelHandlerContext ctx) {

        final Attribute<ByteBuf> byteBufAttribute = ctx.channel().attr(
            PROTOCOL_VERSION_SERVER_RESPONSE_BUFFER_ATTRIBUTE_KEY);
        final ByteBuf byteBuf = byteBufAttribute.get();

        if (byteBuf != null) {
            byteBuf.release();

            assert byteBuf.refCnt() == 0;

            byteBufAttribute.set(null);
        }

    }

    @SuppressWarnings({"squid:S109", "MagicNumber", "BooleanExpressionComplexity"})
    private static int getAcceptedVersionFromResponse(final ByteBuf serverResponseBuf, final int bodyLength) {

        final byte[] data = new byte[bodyLength];
        serverResponseBuf.readBytes(data);
        return ((data[0] & 255) << 24) | ((data[1] & 255) << 16) | ((data[2] & 255) << 8) | (data[3] & 255);
    }

    @SuppressWarnings({"squid:S109", "MagicNumber"})
    private static int getBodyLengthFromResponse(final ByteBuf serverResponseBuf) {

        return ((serverResponseBuf.readByte() & 255) << 8) | (serverResponseBuf.readByte() & 255);
    }

    private static String getHelloMessageFromResponse(final ByteBuf serverResponseBuf)
        throws UnsupportedEncodingException {

        final byte[] helloMessageArray = new byte[TRANSPORT_HELLO_MESSAGE.length()];
        serverResponseBuf.readBytes(helloMessageArray);
        return new String(helloMessageArray, "US-ASCII");
    }

    private static Integer getProtocolVersionAttribute(final ChannelHandlerContext ctx) {

        final Attribute<Integer> attribute = ctx.channel().attr(PROTOCOL_VERSION_ATTRIBUTE_KEY);
        return attribute.get();
    }

    private static void setProtocolVersionAttribute(final ChannelHandlerContext ctx, final int acceptedVersion) {

        final Attribute<Integer> attribute = ctx.channel().attr(PROTOCOL_VERSION_ATTRIBUTE_KEY);
        attribute.set(acceptedVersion);
    }

    /**
     * Gets called if the {@link Channel} of the {@link ChannelHandlerContext} is active.
     *
     * @param ctx the {@link ChannelHandlerContext} hoes {@link Channel} is now active
     * @throws Exception thrown if an error occurs
     */
    @Override
    public void channelActive(final ChannelHandlerContext ctx) throws Exception {

        final Attribute<ByteBuf> byteBufAttribute
            = ctx.channel().attr(PROTOCOL_VERSION_SERVER_RESPONSE_BUFFER_ATTRIBUTE_KEY);
        byteBufAttribute.set(ctx.alloc().buffer(SEVER_RESPONSE_MESSAGE_SIZE));

        final int size = SessionProtocolEncoder.SUPPORTED_VERSIONS.size();

        final ByteBuf response = ctx.alloc().buffer(TRANSPORT_HELLO_MESSAGE.length() + 2 + size * 4);
        response.writeBytes(TRANSPORT_HELLO_MESSAGE.getBytes("US-ASCII"));
        response.writeShort(size * 4);
        SessionProtocolEncoder.SUPPORTED_VERSIONS.forEach(response::writeInt);

        LOGGER.trace("[{}] Sending supported versions list: {}", this.transportName, SessionProtocolEncoder.SUPPORTED_VERSIONS);
        ctx.writeAndFlush(response);
        ctx.fireChannelActive();
    }

    /**
     * Gets called after the {@link ChannelHandler} was removed from the actual context and it doesn't handle events
     * anymore.
     *
     * @param ctx the {@link ChannelHandlerContext}
     * @throws Exception thrown if an error occurs
     */
    @Override
    public void handlerRemoved(final ChannelHandlerContext ctx) throws Exception {

        cleanUp(ctx);
        super.handlerRemoved(ctx);
    }

    /**
     * The {@link Channel} of the {@link ChannelHandlerContext} was registered is now inactive and reached its
     * end of lifetime.
     *
     * @param ctx the {@link ChannelHandlerContext} for hoes {@link Channel} is now inactive
     * @throws Exception thrown if an error occurs
     */
    @Override
    public void channelInactive(final ChannelHandlerContext ctx) throws Exception {

        cleanUp(ctx);
        super.channelInactive(ctx);
    }

    /**
     * Invoked when the current {@link Channel} has read a message from the peer.
     *
     * @param ctx     the {@link ChannelHandlerContext} for which the read operation is made
     * @param message the message to read
     * @throws Exception thrown if an error occurs
     */
    @SuppressWarnings({"squid:S1142", "ReturnCount"})
    @Override
    public void channelRead(final ChannelHandlerContext ctx, final Object message) throws Exception {

        if (!(message instanceof ByteBuf)) {
            ctx.fireChannelRead(message);

        } else if (getProtocolVersionAttribute(ctx) != null) {
            final ByteBuf byteBuffer = (ByteBuf) message;
            byteBuffer.retain();
            ctx.fireChannelRead(byteBuffer);

        } else {
            final ByteBuf byteBuffer = (ByteBuf) message;
            final Attribute<ByteBuf> byteBufAttribute = ctx.channel().attr(
                PROTOCOL_VERSION_SERVER_RESPONSE_BUFFER_ATTRIBUTE_KEY);
            final ByteBuf serverResponseBuf = byteBufAttribute.get();

            try {

                serverResponseBuf.writeBytes(byteBuffer,
                                             Math.min(SEVER_RESPONSE_MESSAGE_SIZE - serverResponseBuf.readableBytes(),
                                                      byteBuffer.readableBytes()));

                if (serverResponseBuf.readableBytes() >= SEVER_RESPONSE_MESSAGE_SIZE) {

                    final String transportHelloMessage = getHelloMessageFromResponse(serverResponseBuf);

                    if (!transportHelloMessage.equals(TRANSPORT_HELLO_MESSAGE)) {

                        LOGGER.warn("[{}] Server did not send transport version negotiation message.",
                                    this.transportName);

                        ctx.close();
                        return;
                    }

                    final int bodyLength = getBodyLengthFromResponse(serverResponseBuf);

                    if (bodyLength != BODY_LENGTH) {

                        LOGGER.error("[{}] Server sent the incorrect version negotiation response, data.length != {}",
                                     this.transportName,
                                     BODY_LENGTH);
                        ctx.close();
                        return;
                    }

                    assert serverResponseBuf.readableBytes() == bodyLength;

                    final int acceptedVersion = getAcceptedVersionFromResponse(serverResponseBuf, bodyLength);

                    if (SessionProtocolEncoder.SUPPORTED_VERSIONS.contains(acceptedVersion)) {

                        setProtocolVersionAttribute(ctx, acceptedVersion);
                        LOGGER.trace("[{}] Server responded with version: {}", this.transportName, acceptedVersion);
                        ctx.fireUserEventTriggered(ProtocolVersionNegotiationEvent.SUCCESS);

                    } else {
                        logErrorMessage(acceptedVersion);
                        ctx.close();
                    }
                }

                if (byteBuffer.readableBytes() > 0) {
                    byteBuffer.retain();
                    ctx.fireChannelRead(byteBuffer);
                }

            } finally {
                serverResponseBuf.release();
                byteBufAttribute.set(null);
                byteBuffer.release();
            }
        }
    }

    private void logErrorMessage(final int acceptedVersion) {

        if (acceptedVersion < 0) {
            LOGGER.error("[{}] Server has sent error message while negotiating protocol version: [{}]",
                         this.transportName,
                         acceptedVersion);
        } else {
            LOGGER.error("[{}] Server has sent version that client does not support! version: [{}]",
                         this.transportName,
                         acceptedVersion);
        }
    }

    /**
     * Called once a write operation is made. The write operation will write the messages through the
     * {@link ChannelPipeline}. Those are then ready to be flushed to the actual {@link Channel} once
     * {@link Channel#flush()} is called
     *
     * @param ctx     the {@link ChannelHandlerContext} for which the write operation is made
     * @param msg     the message to write
     * @param promise the {@link ChannelPromise} to notify once the operation completes
     * @throws Exception thrown if an error occurs
     */
    @Override
    public void write(final ChannelHandlerContext ctx, final Object msg, final ChannelPromise promise)
        throws Exception {

        final Integer versionAttribute = getProtocolVersionAttribute(ctx);
        if (versionAttribute == null) {
            throw new UnsupportedOperationException("Received write request while version is not negotiated yet");
        } else {
            ctx.write(msg, promise);
        }
    }
}
