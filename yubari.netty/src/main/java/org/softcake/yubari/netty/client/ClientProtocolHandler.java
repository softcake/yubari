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

import static org.softcake.yubari.netty.TransportAttributeKeys.CHANNEL_ATTACHMENT_ATTRIBUTE_KEY;

import org.softcake.cherry.core.base.PreCheck;
import org.softcake.yubari.netty.DefaultChannelWriter;
import org.softcake.yubari.netty.channel.ChannelAttachment;
import org.softcake.yubari.netty.client.processors.StreamProcessor;
import org.softcake.yubari.netty.data.DroppableMessageHandler;
import org.softcake.yubari.netty.mina.ClientDisconnectReason;
import org.softcake.yubari.netty.mina.DisconnectedEvent;
import org.softcake.yubari.netty.mina.TransportHelper;

import com.dukascopy.dds4.transport.common.mina.DisconnectReason;
import com.dukascopy.dds4.transport.common.protocol.binary.BinaryProtocolMessage;
import com.dukascopy.dds4.transport.msg.system.BinaryPartMessage;
import com.dukascopy.dds4.transport.msg.system.DisconnectRequestMessage;
import com.dukascopy.dds4.transport.msg.system.JSonSerializableWrapper;
import com.dukascopy.dds4.transport.msg.system.ProtocolMessage;
import com.dukascopy.dds4.transport.msg.system.StreamHeaderMessage;
import com.dukascopy.dds4.transport.msg.system.StreamingStatus;
import com.google.common.util.concurrent.ListeningExecutorService;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.util.Attribute;
import io.reactivex.Completable;
import io.reactivex.Observer;
import io.reactivex.disposables.Disposable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

@ChannelHandler.Sharable
public class ClientProtocolHandler extends SimpleChannelInboundHandler<ProtocolMessage> {
    private static final Logger LOGGER = LoggerFactory.getLogger(ClientProtocolHandler.class);
    private final TransportClientSession clientSession;
    private final ListeningExecutorService eventExecutor;
    private final ListeningExecutorService authEventExecutor;
    private final ListeningExecutorService streamProcessingExecutor;
    private final ListeningExecutorService syncRequestProcessingExecutor;
    private final List<Thread> eventExecutorThreadsForLogging = Collections.synchronizedList(new ArrayList<>());
    private final AtomicBoolean logEventPoolThreadDumpsOnLongExecution;
    private final StreamProcessor streamProcessor;
    private final DroppableMessageHandler messageHandler2;
    private IClientConnector clientConnector;

    public ClientProtocolHandler(final TransportClientSession session) {

        this.clientSession = PreCheck.notNull(session, "session");
        this.messageHandler2 = new DroppableMessageHandler(clientSession.getDroppableMessagesClientTTL());
        final boolean logSkippedDroppableMessages = session.isLogSkippedDroppableMessages();
        this.logEventPoolThreadDumpsOnLongExecution = session.getLogEventPoolThreadDumpsOnLongExecution();
        this.eventExecutor = this.initEventExecutor();
        this.authEventExecutor = this.initAuthEventExecutor();
        this.streamProcessingExecutor = this.initStreamProcessingExecutor();
        this.syncRequestProcessingExecutor = this.initSyncRequestProcessingExecutor();
        this.streamProcessor = new StreamProcessor(clientSession, this.streamProcessingExecutor);

    }

    public DroppableMessageHandler getMessageHandler() {

        return messageHandler2;
    }

    public void setClientConnector(final IClientConnector clientConnector) {

        this.clientConnector = clientConnector;
        this.clientConnector.observeClientState().subscribe(new Observer<ClientConnector.ClientState>() {
            @Override
            public void onSubscribe(final Disposable disposable) {

            }

            @Override
            public void onNext(final ClientConnector.ClientState clientState) {

                if (ClientConnector.ClientState.PROTOCOL_VERSION_NEGOTIATION == clientState) {
                } else if (ClientConnector.ClientState.ONLINE == clientState) {
                    fireAuthorized();
                } else if (ClientConnector.ClientState.DISCONNECTED == clientState) {
                    fireDisconnected(new Exception("DISCONNECTED"));
                }

            }

            @Override
            public void onError(final Throwable throwable) {

                fireDisconnected(throwable);
            }

            @Override
            public void onComplete() {

            }
        });
    }

    private void fireDisconnected(final Throwable throwable) {

        ClientDisconnectReason disconnectReason = clientConnector.getDisconnectReason();
        this.clientSession.disconnected();

        if (disconnectReason == null) {
            disconnectReason = new ClientDisconnectReason(DisconnectReason.CONNECTION_PROBLEM,
                                                          "Unknown connection problem",
                                                          throwable);
        }
        final DisconnectedEvent disconnectedEvent = new DisconnectedEvent(this.clientSession.getTransportClient(),
                                                                          disconnectReason.getDisconnectReason(),
                                                                          disconnectReason.getDisconnectHint(),
                                                                          disconnectReason.getError(),
                                                                          disconnectReason.getDisconnectComments());
        try {

            this.clientSession.onDisconnected(disconnectedEvent);
        } catch (final Exception e) {
            LOGGER.error("[{}] ", clientSession.getTransportName(), e);
            clientConnector.disconnect(new ClientDisconnectReason(DisconnectReason.EXCEPTION_CAUGHT,
                                                                  String.format("Exception caught while onDisconnected "
                                                                                + "called %s", e.getMessage()),
                                                                  e));
        }

        this.clientSession.terminate();
    }

    private ListeningExecutorService initEventExecutor() {

        return TransportHelper.createExecutor(this.clientSession.getEventPoolSize(),
                                              this.clientSession.getEventPoolAutoCleanupInterval(),
                                              this.clientSession.getCriticalEventQueueSize(),
                                              "TransportClientEventExecutorThread",
                                              this.eventExecutorThreadsForLogging,
                                              this.clientSession.getTransportName(),
                                              true);

    }

    private ListeningExecutorService initAuthEventExecutor() {

        return TransportHelper.createExecutor(this.clientSession.getAuthEventPoolSize(),
                                              this.clientSession.getAuthEventPoolAutoCleanupInterval(),
                                              this.clientSession.getCriticalAuthEventQueueSize(),
                                              "TransportClientAuthEventExecutorThread",
                                              this.eventExecutorThreadsForLogging,
                                              this.clientSession.getTransportName(),
                                              true);

    }

    private ListeningExecutorService initStreamProcessingExecutor() {

        return TransportHelper.createExecutor(this.clientSession.getStreamProcessingPoolSize(),
                                              this.clientSession.getStreamProcessingPoolAutoCleanupInterval(),
                                              this.clientSession.getCriticalStreamProcessingQueueSize(),
                                              "TransportClientStreamProcessingThread",
                                              this.eventExecutorThreadsForLogging,
                                              this.clientSession.getTransportName(),
                                              false);

    }

    private ListeningExecutorService initSyncRequestProcessingExecutor() {

        return TransportHelper.createExecutor(this.clientSession.getSyncRequestProcessingPoolSize(),
                                              this.clientSession.getSyncRequestProcessingPoolAutoCleanupInterval(),
                                              this.clientSession.getCriticalSyncRequestProcessingQueueSize(),
                                              "TransportClientSyncRequestProcessingThread",
                                              this.eventExecutorThreadsForLogging,
                                              this.clientSession.getTransportName(),
                                              true);

    }

    ExecutorService getEventExecutor() {

        return this.eventExecutor;
    }

    void terminate() {

        this.streamProcessor.terminateStreams();
        TransportHelper.shutdown(this.eventExecutor,
                                 this.clientSession.getEventPoolTerminationTimeUnitCount(),
                                 this.clientSession.getEventPoolTerminationTimeUnit());
        TransportHelper.shutdown(this.authEventExecutor,
                                 this.clientSession.getAuthEventPoolTerminationTimeUnitCount(),
                                 this.clientSession.getAuthEventPoolTerminationTimeUnit());
        TransportHelper.shutdown(this.streamProcessingExecutor,
                                 this.clientSession.getStreamProcessingPoolTerminationTimeUnitCount(),
                                 this.clientSession.getStreamProcessingPoolTerminationTimeUnit());
        TransportHelper.shutdown(this.syncRequestProcessingExecutor,
                                 this.clientSession.getSyncRequestProcessingPoolTerminationTimeUnitCount(),
                                 this.clientSession.getSyncRequestProcessingPoolTerminationTimeUnit());
    }

    protected void channelRead0(final ChannelHandlerContext ctx, final ProtocolMessage msg) throws Exception {

        final Attribute<ChannelAttachment> channelAttachmentAttribute = ctx.channel().attr(
            CHANNEL_ATTACHMENT_ATTRIBUTE_KEY);
        final ChannelAttachment attachment = channelAttachmentAttribute.get();

        LOGGER.trace("[{}] Message received {}, primary channel: {}",
                     this.clientSession.getTransportName(),
                     msg,
                     attachment.isPrimaryConnection());

        attachment.setLastReadIoTime(System.currentTimeMillis());
        this.processControlRequest(ctx, msg, attachment);
    }

    @Override
    public void exceptionCaught(final ChannelHandlerContext ctx, final Throwable cause) throws Exception {

        LOGGER.error(cause.getMessage(), cause);

        ctx.close();
    }

    public void fireAuthorized() {

        try {
            this.clientSession.onOnline();
        } catch (final Exception e) {
            LOGGER.error("[{}] ", clientSession.getTransportName(), e);
            clientConnector.disconnect(new ClientDisconnectReason(DisconnectReason.EXCEPTION_CAUGHT,
                                                                  String.format("Exception caught while authorized "
                                                                                + "called %s", e.getMessage()),
                                                                  e));
        }

    }

    public Completable writeMessage(final Channel channel, final BinaryProtocolMessage responseMessage) {

        return DefaultChannelWriter.writeMessage(clientSession, channel, responseMessage);
    }

    private void updateChannelAttachment(final Channel channel, final long lastWriteIoTime) {

        final ChannelAttachment ca = channel.attr(CHANNEL_ATTACHMENT_ATTRIBUTE_KEY).get();
        ca.setLastWriteIoTime(lastWriteIoTime);
        ca.messageWritten();
    }

    void processControlRequest(final ChannelHandlerContext ctx,
                               final ProtocolMessage msg,
                               final ChannelAttachment attachment) {

        final ProtocolMessage requestMessage = msg;

        if (requestMessage instanceof DisconnectRequestMessage) {
            this.proccessDisconnectRequestMessage((DisconnectRequestMessage) requestMessage);
        } else if (requestMessage instanceof JSonSerializableWrapper) {
            throw new UnsupportedOperationException("Do you really need this?");
        } else if (msg instanceof BinaryPartMessage
                   || msg instanceof StreamHeaderMessage
                   || msg instanceof StreamingStatus) {
            this.streamProcessor.process(ctx, msg);
            throw new UnsupportedOperationException("Do you really need this?");
        }  else {
            this.fireFeedbackMessageReceived(ctx, requestMessage);
        }
    }

    private void proccessDisconnectRequestMessage(final DisconnectRequestMessage requestMessage) {

        final DisconnectReason reason = requestMessage.getReason() == null
                                        ? DisconnectReason.SERVER_APP_REQUEST
                                        : requestMessage.getReason();

        this.clientConnector.disconnect(new ClientDisconnectReason(reason,
                                                                   requestMessage.getHint(),
                                                                   "Disconnect request received"));
    }

    private void fireFeedbackMessageReceived(final ChannelHandlerContext ctx, final ProtocolMessage message) {

        messageHandler2.setCurrentDroppableMessageTime(message);
        clientSession.onMessageReceived(message);

    }

    public void checkAndLogEventPoolThreadDumps() {

        if (!this.logEventPoolThreadDumpsOnLongExecution.get()) {
            return;
        }

        final List<Thread> threads = new ArrayList<>(this.eventExecutorThreadsForLogging);

        for (final Thread thread : threads) {
            final long before = System.currentTimeMillis();
            final String stackTrace = Arrays.toString(thread.getStackTrace());

            LOGGER.warn("Transport client [{}, {}] thread's [{}] stack trace [{}], dump taking costed [{}]ms",
                        this.clientSession.getTransportName(),
                        this.clientSession.getAddress(),
                        thread.getName(),
                        stackTrace,
                        System.currentTimeMillis() - before);
        }
    }
}
