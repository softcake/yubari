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

import com.dukascopy.dds4.transport.common.protocol.binary.AbstractStaticSessionDictionary;
import com.dukascopy.dds4.transport.common.protocol.binary.BinaryProtocolMessage;
import com.dukascopy.dds4.transport.common.protocol.binary.ClassInfo;
import com.dukascopy.dds4.transport.common.protocol.binary.ClassMappingMessage;
import com.dukascopy.dds4.transport.common.protocol.binary.DynamicSessionDictionary;
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
import io.netty.handler.codec.EncoderException;
import io.netty.util.Attribute;
import io.netty.util.AttributeKey;
import io.netty.util.ReferenceCountUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.channels.ClosedChannelException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

@Sharable
public class ProtocolEncoderDecoder extends ChannelDuplexHandler {
    public static final AttributeKey<SessionProtocolEncoder> ENCODER_ATTACHMENT_ATTRIBUTE_KEY = AttributeKey.valueOf(
        "encoder_attachment");
    public static final AttributeKey<SessionProtocolDecoder> DECODER_ATTACHMENT_ATTRIBUTE_KEY = AttributeKey.valueOf(
        "decoder_attachment");
    public static final AttributeKey<Integer> PROTOCOL_VERSION_ATTRIBUTE_KEY = AttributeKey.valueOf("protocol_version");
    public static final AttributeKey<ArrayList<ProtocolEncoderDecoder.PendingWrite>>
        PROTOCOL_VERSION_MESSAGE_QUEUE_ATTRIBUTE_KEY
        = AttributeKey.valueOf("protocol_version_message_queue");
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

    public void channelActive(final ChannelHandlerContext ctx) throws Exception {

        final Attribute<ArrayList<ProtocolEncoderDecoder.PendingWrite>> messageQueueAttribute = ctx.channel().attr(
            PROTOCOL_VERSION_MESSAGE_QUEUE_ATTRIBUTE_KEY);
        messageQueueAttribute.set(new ArrayList<>());
        ctx.fireChannelActive();
    }

    public void channelInactive(final ChannelHandlerContext ctx) throws Exception {

        this.failScheduledWrites(ctx);
        ctx.fireChannelInactive();
    }

    public void handlerRemoved(final ChannelHandlerContext ctx) throws Exception {

        this.failScheduledWrites(ctx);
    }

    public void userEventTriggered(final ChannelHandlerContext ctx, final Object evt) throws Exception {

        if (evt instanceof ProtocolVersionNegotiationSuccessEvent) {
            final Attribute<ArrayList<ProtocolEncoderDecoder.PendingWrite>> messageQueueAttribute = ctx.channel().attr(
                PROTOCOL_VERSION_MESSAGE_QUEUE_ATTRIBUTE_KEY);
            final ArrayList<ProtocolEncoderDecoder.PendingWrite> messageQueue = messageQueueAttribute.get();
            if (messageQueue != null) {
                this.sendScheduledMessages(ctx, messageQueueAttribute);
            }
        }

        super.userEventTriggered(ctx, evt);
    }

    public void channelRead(final ChannelHandlerContext ctx, final Object msg) throws Exception {

        if (!(msg instanceof ByteBuf)) {
            ctx.fireChannelRead(msg);
        } else {

            final ByteBuf byteBuf = (ByteBuf) msg;
            final Attribute<Integer> protocolVersionAttribute = ctx.channel().attr(PROTOCOL_VERSION_ATTRIBUTE_KEY);
            final Integer protocolVersionObj = protocolVersionAttribute.get();
            final int protocolVersion = protocolVersionObj == null ? 4 : protocolVersionObj;



            try(final DataInputStream msgData = new DataInputStream(new ByteBufInputStream(byteBuf))) {



                try {
                    final Attribute<SessionProtocolDecoder> decoderAttribute = ctx.channel().attr(
                        DECODER_ATTACHMENT_ATTRIBUTE_KEY);
                    SessionProtocolDecoder sessionProtocolDecoder = decoderAttribute.get();
                    if (sessionProtocolDecoder == null) {
                        if (protocolVersion < 4) {
                            sessionProtocolDecoder = new SessionProtocolDecoder(this.transportName,
                                                                                new DynamicSessionDictionary(),
                                                                                this.codecFactoryAndCache);
                        } else {
                            sessionProtocolDecoder = new SessionProtocolDecoder(this.transportName,
                                                                                this.staticSessionDictionary,
                                                                                this.codecFactoryAndCache);
                        }

                        final SessionProtocolDecoder old = decoderAttribute.setIfAbsent(sessionProtocolDecoder);
                        if (old != null) {
                            sessionProtocolDecoder = old;
                        }
                    }

                    final BinaryProtocolMessage message = sessionProtocolDecoder.decodeMessage(protocolVersion,
                                                                                               msgData);
                    if (LOGGER.isTraceEnabled()) {
                        LOGGER.trace("[{}] Decoded message [{}]", this.transportName, message);
                    }

                    if (message != null) {
                        if (message instanceof ClassMappingMessage) {
                            final ClassMappingMessage cmm = (ClassMappingMessage) message;
                            sessionProtocolDecoder.classMappingReceived(protocolVersion, cmm);
                        } else {
                            ctx.fireChannelRead(message);
                        }
                    }
                } catch (final Exception e) {
                    LOGGER.error(String.format("[%s] (%s) protocol exception - %s message decoding error",
                                               this.transportName,
                                               ctx.channel().remoteAddress(),
                                               e.getMessage()), e);
                    throw e;
                }
            } finally {
                ReferenceCountUtil.release(msg);
            }

        }
    }

    public void write(final ChannelHandlerContext ctx, final Object msg, final ChannelPromise promise)
        throws Exception {

        final Attribute<Integer> firstMessageAttribute = ctx.channel().attr(PROTOCOL_VERSION_ATTRIBUTE_KEY);
        final Object firstMessage = firstMessageAttribute.get();
        final Attribute messageQueueAttribute;
        final ArrayList messageQueue;
        if (firstMessage == null) {
            messageQueueAttribute = ctx.channel().attr(PROTOCOL_VERSION_MESSAGE_QUEUE_ATTRIBUTE_KEY);
            messageQueue = (ArrayList) messageQueueAttribute.get();
            boolean scheduled = false;
            synchronized (messageQueue) {
                if (messageQueueAttribute.get() != null) {
                    final ProtocolEncoderDecoder.PendingWrite pendingWrite = new ProtocolEncoderDecoder.PendingWrite();
                    pendingWrite.msg = msg;
                    pendingWrite.promise = promise;
                    messageQueue.add(pendingWrite);
                    scheduled = true;
                }
            }

            if (!scheduled) {
                this.doEncode(ctx, msg, promise);
            }
        } else {
            messageQueueAttribute = ctx.channel().attr(PROTOCOL_VERSION_MESSAGE_QUEUE_ATTRIBUTE_KEY);
            messageQueue = (ArrayList) messageQueueAttribute.get();
            if (messageQueue != null) {
                this.sendScheduledMessages(ctx, messageQueueAttribute);
            }

            this.doEncode(ctx, msg, promise);
        }

    }

    private void doEncode(final ChannelHandlerContext ctx, final Object messageObject, final ChannelPromise promise)
        throws IOException, InterruptedException {

        ByteBuf buf = null;

        try {
            buf = ctx.alloc().ioBuffer();

            try {
                if (LOGGER.isTraceEnabled()) {
                    LOGGER.trace("[{}] Encoding [{}] to bytes", this.transportName, messageObject);
                }

                final Attribute<Integer> protocolVersionAttribute = ctx.channel().attr(PROTOCOL_VERSION_ATTRIBUTE_KEY);
                final Integer protocolVersionObj = protocolVersionAttribute.get();
                final int protocolVersion = protocolVersionObj == null ? 4 : protocolVersionObj;
                final ByteBufOutputStream byteBufOutputStream = new ByteBufOutputStream(buf);
                final DataOutputStream dataOutputStream = new DataOutputStream(byteBufOutputStream);
                final EncodingContext encodingContext = new EncodingContext();
                final Attribute<SessionProtocolEncoder> encoderAttribute = ctx.channel().attr(
                    ENCODER_ATTACHMENT_ATTRIBUTE_KEY);
                SessionProtocolEncoder sessionProtocolEncoder = encoderAttribute.get();
                if (sessionProtocolEncoder == null) {
                    if (protocolVersion < 4) {
                        sessionProtocolEncoder = new SessionProtocolEncoder(this.transportName,
                                                                            new DynamicSessionDictionary(),
                                                                            this.codecFactoryAndCache);
                    } else {
                        sessionProtocolEncoder = new SessionProtocolEncoder(this.transportName,
                                                                            this.staticSessionDictionary,
                                                                            this.codecFactoryAndCache);
                    }

                    final SessionProtocolEncoder old = encoderAttribute.setIfAbsent(sessionProtocolEncoder);
                    if (old != null) {
                        sessionProtocolEncoder = old;
                    }
                }

                if (messageObject instanceof RawObject) {
                    final RawObject rawObject = (RawObject) messageObject;
                    final RawData rawData = rawObject.getData(protocolVersion);
                    if (rawData == null) {
                        sessionProtocolEncoder.encodeMessage(protocolVersion,
                                                             dataOutputStream,
                                                             rawObject.getMessage(),
                                                             encodingContext);
                    } else {
                        final Map<Integer, Class> deferredClassIdPositions = rawData.getDeferredClassIdPositions();
                        final byte[] data = (byte[]) rawData.getData();
                        int index = 0;
                        if (deferredClassIdPositions != null && !deferredClassIdPositions.isEmpty()) {
                            final Iterator i$ = deferredClassIdPositions.entrySet().iterator();

                            while (i$.hasNext()) {
                                final Entry<Integer, Class> entry = (Entry) i$.next();
                                final ClassInfo<Short> classInfo = sessionProtocolEncoder.getDictionary().getClassInfo(
                                    entry.getValue(),
                                    encodingContext);
                                if (classInfo == null) {
                                    throw new IllegalArgumentException("Unable to find "
                                                                       + entry.getValue()
                                                                       + " in dictionary");
                                }

                                final int idIndex = entry.getKey();
                                if (index < idIndex) {
                                    dataOutputStream.write(data, index, idIndex - index);
                                    index = idIndex;
                                }

                                if (protocolVersion < 4) {
                                    dataOutputStream.writeShort(classInfo.id);
                                    index += 2;
                                } else {
                                    sessionProtocolEncoder.getDictionary().writeClassId(protocolVersion,
                                                                                        dataOutputStream,

                                                                                        entry.getValue(),
                                                                                        encodingContext);
                                    index += 4;
                                }
                            }
                        }

                        if (index < data.length) {
                            dataOutputStream.write(data, index, data.length - index);
                        }
                    }
                } else {
                    sessionProtocolEncoder.encodeMessage(protocolVersion,
                                                         dataOutputStream,
                                                         (BinaryProtocolMessage) messageObject,
                                                         encodingContext);
                }

                dataOutputStream.flush();
                if (protocolVersion < 4 && (encodingContext.getClassesToMap() != null
                                            && encodingContext.getClassesToMap().size() > 0
                                            || encodingContext.getClassDirectInvocationHandlers() != null
                                               && encodingContext.getClassDirectInvocationHandlers().size() > 0)) {
                    final ClassMappingMessage cmm = new ClassMappingMessage(encodingContext);
                    if (LOGGER.isDebugEnabled()) {
                        LOGGER.debug("[{}] Sending mapping [{}]", this.transportName, cmm);
                    }

                    this.doEncode(ctx, cmm, ctx.newPromise());
                }

                if (LOGGER.isTraceEnabled()) {
                    LOGGER.trace("[{}] Message [{}] encoded to {} bytes",
                                 new Object[]{this.transportName, messageObject, buf.readableBytes()});
                }
            } finally {
                ReferenceCountUtil.release(messageObject);
            }

            if (buf.isReadable()) {
                ctx.write(buf, promise);
            } else {
                buf.release();
                ctx.write(Unpooled.EMPTY_BUFFER, promise);
            }

            buf = null;
        } catch (final EncoderException var33) {
            throw var33;
        } catch (final Throwable var34) {
            throw new EncoderException(var34);
        } finally {
            if (buf != null) {
                buf.release();
            }

        }

    }

    private void sendScheduledMessages(final ChannelHandlerContext ctx,
                                       final Attribute<ArrayList<ProtocolEncoderDecoder.PendingWrite>>
                                           messageQueueAttribute)
        throws Exception {

        final ArrayList<ProtocolEncoderDecoder.PendingWrite> messageQueue = messageQueueAttribute.get();
        synchronized (messageQueue) {
            final Iterator i$ = messageQueue.iterator();

            while (i$.hasNext()) {
                final ProtocolEncoderDecoder.PendingWrite
                    pendingWrite
                    = (ProtocolEncoderDecoder.PendingWrite) i$.next();
                this.doEncode(ctx, pendingWrite.msg, pendingWrite.promise);
            }

            ctx.flush();
            messageQueue.clear();
            messageQueueAttribute.set(null);
        }
    }

    private void failScheduledWrites(final ChannelHandlerContext ctx) {

        final Attribute<ArrayList<ProtocolEncoderDecoder.PendingWrite>> messageQueueAttribute = ctx.channel().attr(
            PROTOCOL_VERSION_MESSAGE_QUEUE_ATTRIBUTE_KEY);
        final ArrayList<ProtocolEncoderDecoder.PendingWrite> messageQueue = messageQueueAttribute.get();
        if (messageQueue != null) {
            synchronized (messageQueue) {
                final Iterator i$ = messageQueue.iterator();

                while (i$.hasNext()) {
                    final ProtocolEncoderDecoder.PendingWrite
                        pendingWrite
                        = (ProtocolEncoderDecoder.PendingWrite) i$.next();
                    ReferenceCountUtil.release(pendingWrite.msg);

                    try {
                        pendingWrite.promise.setFailure(CLOSED_CHANNEL_EXCEPTION);
                    } catch (final Exception var9) {
                        ;
                    }
                }

                messageQueue.clear();
                messageQueueAttribute.set(null);
            }
        }

    }

    private static class PendingWrite {
        public Object msg;
        public ChannelPromise promise;

        private PendingWrite() {

        }
    }
}
