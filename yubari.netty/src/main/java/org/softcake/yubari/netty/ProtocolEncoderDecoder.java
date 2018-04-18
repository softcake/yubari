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

import static  org.softcake.yubari.netty.TransportAttributeKeys.DECODER_ATTACHMENT_ATTRIBUTE_KEY;
import static  org.softcake.yubari.netty.TransportAttributeKeys.ENCODER_ATTACHMENT_ATTRIBUTE_KEY;
import static org.softcake.yubari.netty.TransportAttributeKeys.PROTOCOL_VERSION_ATTRIBUTE_KEY;
import static org.softcake.yubari.netty.TransportAttributeKeys.PROTOCOL_VERSION_MESSAGE_QUEUE_ATTRIBUTE_KEY;

import com.dukascopy.dds4.transport.common.protocol.binary.AbstractStaticSessionDictionary;
import com.dukascopy.dds4.transport.common.protocol.binary.BinaryProtocolMessage;
import com.dukascopy.dds4.transport.common.protocol.binary.ClassInfo;
import com.dukascopy.dds4.transport.common.protocol.binary.ClassMappingMessage;
import com.dukascopy.dds4.transport.common.protocol.binary.EncodingContext;
import com.dukascopy.dds4.transport.common.protocol.binary.RawObject;
import com.dukascopy.dds4.transport.common.protocol.binary.RawObject.RawData;
import com.dukascopy.dds4.transport.common.protocol.binary.SessionProtocolDecoder;
import com.dukascopy.dds4.transport.common.protocol.binary.SessionProtocolEncoder;
import com.dukascopy.dds4.transport.common.protocol.binary.codec.CodecFactoryAndCache;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.util.Attribute;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.internal.PendingWrite;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.channels.ClosedChannelException;
import java.util.ArrayList;
import java.util.Map;


@Sharable
public class ProtocolEncoderDecoder extends ChannelDuplexHandler {

    private static final int DEFAULT_PROTOCOL_VERSION = 4;
    private static final Logger LOGGER = LoggerFactory.getLogger(ProtocolEncoderDecoder.class);
    private static final ClosedChannelException CLOSED_CHANNEL_EXCEPTION = new ClosedChannelException();

    static {
        CLOSED_CHANNEL_EXCEPTION.setStackTrace(new StackTraceElement[0]);
    }

    private final String transportName;
    private final CodecFactoryAndCache codecFactoryAndCache;
    private final AbstractStaticSessionDictionary staticSessionDictionary;

    public ProtocolEncoderDecoder(final String transportName,
                                  final int maxMessageSizeBytes,
                                  final AbstractStaticSessionDictionary staticSessionDictionary) {

        this.transportName = transportName;
        this.codecFactoryAndCache = new CodecFactoryAndCache(transportName, maxMessageSizeBytes);
        this.staticSessionDictionary = staticSessionDictionary;
    }

    private static int getProtocolVersion(final ChannelHandlerContext ctx) {

        final Attribute<Integer> protocolVersionAttribute = ctx.channel().attr(PROTOCOL_VERSION_ATTRIBUTE_KEY);
        final Integer protocolVersionObj = protocolVersionAttribute.get();
        return protocolVersionObj == null ? DEFAULT_PROTOCOL_VERSION : protocolVersionObj;
    }

    private void failScheduledWrites(final ChannelHandlerContext ctx) {

        final Attribute<ArrayList<PendingWrite>> messageQueueAttribute = ctx.channel().attr(
            PROTOCOL_VERSION_MESSAGE_QUEUE_ATTRIBUTE_KEY);
        final ArrayList<PendingWrite> messageQueue = messageQueueAttribute.get();
        if (messageQueue != null) {

            synchronized (messageQueue) {
                messageQueue.forEach(pendingWrite -> {
                   // ReferenceCountUtil.release(pendingWrite.msg());

                    try {
                        pendingWrite.failAndRecycle(CLOSED_CHANNEL_EXCEPTION);
                    } catch (final IllegalStateException e) {
                        LOGGER.error("Error occurred...", e);
                    }
                });

                messageQueue.clear();
            }
        }

    }

    @Override
    public void channelActive(final ChannelHandlerContext ctx) throws Exception {

        final Attribute<ArrayList<PendingWrite>> messageQueueAttribute = ctx.channel().attr(
            PROTOCOL_VERSION_MESSAGE_QUEUE_ATTRIBUTE_KEY);
        messageQueueAttribute.set(new ArrayList<>());
        ctx.fireChannelActive();
    }

    @Override
    public void channelInactive(final ChannelHandlerContext ctx) throws Exception {

        failScheduledWrites(ctx);
        ctx.fireChannelInactive();
    }

    @Override
    public void handlerRemoved(final ChannelHandlerContext ctx) throws Exception {

        failScheduledWrites(ctx);
    }

    @Override
    public void userEventTriggered(final ChannelHandlerContext ctx, final Object evt) throws Exception {

        if (evt instanceof ProtocolVersionNegotiationEvent) {
            final Attribute<ArrayList<PendingWrite>> messageQueueAttribute = ctx.channel().attr(
                PROTOCOL_VERSION_MESSAGE_QUEUE_ATTRIBUTE_KEY);
            final ArrayList<PendingWrite> messageQueue = messageQueueAttribute.get();
            if (messageQueue != null) {
                this.sendScheduledMessages(ctx, messageQueueAttribute);
            }
        }

        super.userEventTriggered(ctx, evt);
    }

    @Override
    public void channelRead(final ChannelHandlerContext ctx, final Object msg) throws Exception {

        if (!(msg instanceof ByteBuf)) {
            ctx.fireChannelRead(msg);
            return;
        }

        final int protocolVersion = getProtocolVersion(ctx);

        if (protocolVersion < DEFAULT_PROTOCOL_VERSION) {
            throw new UnsupportedEncodingException("Protocolversion < 4 not supported");
        }

        final ByteBuf byteBuf = (ByteBuf) msg;
        try (DataInputStream msgData = new DataInputStream(new ByteBufInputStream(byteBuf))) {

            final SessionProtocolDecoder sessionProtocolDecoder = getSessionProtocolDecoder(ctx);
            final BinaryProtocolMessage message = sessionProtocolDecoder.decodeMessage(protocolVersion, msgData);

            LOGGER.trace("[{}] Decoded message [{}]", this.transportName, message);

            if (message != null) {
                if (message instanceof ClassMappingMessage) {
                    final ClassMappingMessage cmm = (ClassMappingMessage) message;
                    sessionProtocolDecoder.classMappingReceived(protocolVersion, cmm);
                } else {
                    ctx.fireChannelRead(message);
                }
            }
        } finally {
            ReferenceCountUtil.release(msg);
        }
    }

    private SessionProtocolDecoder getSessionProtocolDecoder(final ChannelHandlerContext ctx) {

        final Attribute<SessionProtocolDecoder> decoderAttribute = ctx.channel().attr(DECODER_ATTACHMENT_ATTRIBUTE_KEY);
        SessionProtocolDecoder sessionProtocolDecoder = decoderAttribute.get();
        if (sessionProtocolDecoder == null) {

            sessionProtocolDecoder = new SessionProtocolDecoder(this.transportName,
                                                                this.staticSessionDictionary,
                                                                this.codecFactoryAndCache);

            final SessionProtocolDecoder old = decoderAttribute.setIfAbsent(sessionProtocolDecoder);
            if (old != null) {
                sessionProtocolDecoder = old;
            }
        }
        return sessionProtocolDecoder;
    }

    @Override
    public void write(final ChannelHandlerContext ctx, final Object msg, final ChannelPromise promise)
        throws Exception {

        final Attribute<Integer> firstMessageAttribute = ctx.channel().attr(PROTOCOL_VERSION_ATTRIBUTE_KEY);
        final Object firstMessage = firstMessageAttribute.get();
        final Attribute<ArrayList<PendingWrite>> messageQueueAttribute = ctx.channel().attr(
            PROTOCOL_VERSION_MESSAGE_QUEUE_ATTRIBUTE_KEY);
        final ArrayList<PendingWrite> messageQueue = messageQueueAttribute.get();

        if (firstMessage == null) {

            synchronized (messageQueue) {
                messageQueue.add(PendingWrite.newInstance(msg, promise));
            }

        } else {

            this.sendScheduledMessages(ctx, messageQueueAttribute);
            this.doEncode(ctx, msg, promise);
        }
    }

    private void doEncode(final ChannelHandlerContext ctx, final Object messageObject, final ChannelPromise promise) {

        ByteBuf buf = ctx.alloc().ioBuffer();

        try (DataOutputStream dataOutputStream = new DataOutputStream(new ByteBufOutputStream(buf))) {

            LOGGER.trace("[{}] Encoding [{}] to bytes", this.transportName, messageObject);

            final int protocolVersion = getProtocolVersion(ctx);

            if (protocolVersion < DEFAULT_PROTOCOL_VERSION) {
                throw new UnsupportedEncodingException("Protocolversion < 4 not supported");
            }

            final EncodingContext encodingContext = new EncodingContext();
            final SessionProtocolEncoder sessionProtocolEncoder = getSessionProtocolEncoder(ctx);

            if (messageObject instanceof RawObject) {
                final RawObject rawObject = (RawObject) messageObject;
                final RawData rawData = rawObject.getData(protocolVersion);
                if (rawData == null) {
                    sessionProtocolEncoder.encodeMessage(protocolVersion,
                                                         dataOutputStream,
                                                         rawObject.getMessage(),
                                                         encodingContext);
                } else {
                    processRawData(ctx, dataOutputStream, rawData, encodingContext);
                }
            } else {
                sessionProtocolEncoder.encodeMessage(protocolVersion,
                                                     dataOutputStream,
                                                     (BinaryProtocolMessage) messageObject,
                                                     encodingContext);
            }

            dataOutputStream.flush();

            LOGGER.trace("[{}] Message [{}] encoded to {} bytes",
                         this.transportName,
                         messageObject,
                         buf.readableBytes());

            if (buf.isReadable()) {
                ctx.write(buf, promise);
            } else {
                buf.release();
                ctx.write(Unpooled.EMPTY_BUFFER, promise);
            }

            buf = null;

        } catch (final IOException e) {
            LOGGER.error("[{}] Encoding [{}] to failed", this.transportName, messageObject, e);
        } finally {
            ReferenceCountUtil.release(messageObject);
            if (buf != null) {
                buf.release();
            }
        }

    }

    private void processRawData(final ChannelHandlerContext ctx,
                                final DataOutputStream dataOutputStream,
                                final RawData rawData,
                                final EncodingContext encodingContext) throws IOException {

        final int protocolVersion = getProtocolVersion(ctx);
        final SessionProtocolEncoder sessionProtocolEncoder = getSessionProtocolEncoder(ctx);
        final Map<Integer, Class> deferredClassIdPositions = rawData.getDeferredClassIdPositions();
        final byte[] data = (byte[]) rawData.getData();
        int index = 0;

        if (deferredClassIdPositions != null && !deferredClassIdPositions.isEmpty()) {

            for (final Map.Entry<Integer, Class> entry : deferredClassIdPositions.entrySet()) {
                final ClassInfo<Short> classInfo = sessionProtocolEncoder.getDictionary().getClassInfo(entry.getValue(),
                                                                                                       encodingContext);
                if (classInfo == null) {
                    throw new IllegalArgumentException(String.format("Unable to find %s in dictionary",
                                                                     entry.getValue()));
                }

                final int idIndex = entry.getKey();
                if (index < idIndex) {
                    dataOutputStream.write(data, index, idIndex - index);
                    index = idIndex;
                }
                sessionProtocolEncoder.getDictionary().writeClassId(protocolVersion,
                                                                    dataOutputStream,
                                                                    entry.getValue(),
                                                                    encodingContext);
                index += DEFAULT_PROTOCOL_VERSION;

            }

        }

        if (index < data.length) {
            dataOutputStream.write(data, index, data.length - index);
        }
    }

    private SessionProtocolEncoder getSessionProtocolEncoder(final ChannelHandlerContext ctx) {

        final Attribute<SessionProtocolEncoder> encoderAttribute = ctx.channel().attr(ENCODER_ATTACHMENT_ATTRIBUTE_KEY);
        SessionProtocolEncoder sessionProtocolEncoder = encoderAttribute.get();

        if (sessionProtocolEncoder == null) {

            sessionProtocolEncoder = new SessionProtocolEncoder(this.transportName,
                                                                this.staticSessionDictionary,
                                                                this.codecFactoryAndCache);

            final SessionProtocolEncoder old = encoderAttribute.setIfAbsent(sessionProtocolEncoder);
            if (old != null) {
                sessionProtocolEncoder = old;
            }
        }
        return sessionProtocolEncoder;
    }

    private void sendScheduledMessages(final ChannelHandlerContext ctx,
                                       final Attribute<ArrayList<PendingWrite>> messageQueueAttribute) {

        final ArrayList<PendingWrite> messageQueue = messageQueueAttribute.get();

        if (messageQueue == null || messageQueue.isEmpty()) {
            return;
        }

        synchronized (messageQueue) {
            messageQueue.forEach(pendingWrite -> doEncode(ctx, pendingWrite.msg(), (ChannelPromise) pendingWrite.promise()));
            ctx.flush();
            messageQueue.clear();
        }
    }
/*
    private class PendingWrite {
        private Object msg;
        private ChannelPromise promise;

        PendingWrite(final Object message, final ChannelPromise promise) {

            this.msg = message;
            this.promise = promise;
        }

        Object getMessage() {

            return msg;
        }


        ChannelPromise getPromise() {

            return promise;
        }


    }*/
}
