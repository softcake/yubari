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


import org.softcake.yubari.netty.data.DroppableMessageScheduling;
import org.softcake.yubari.netty.map.MapHelper;
import org.softcake.yubari.netty.mina.ClientDisconnectReason;
import org.softcake.yubari.netty.mina.ClientListener;
import org.softcake.yubari.netty.mina.DisconnectedEvent;
import org.softcake.yubari.netty.mina.ISessionStats;
import org.softcake.yubari.netty.mina.InvocationResult;
import org.softcake.yubari.netty.mina.TransportHelper;
import org.softcake.yubari.netty.stream.BlockingBinaryStream;
import org.softcake.yubari.netty.stream.StreamListener;
import org.softcake.yubari.netty.util.StrUtils;

import com.dukascopy.dds4.common.orderedExecutor.OrderedThreadPoolExecutor.OrderedRunnable;
import com.dukascopy.dds4.ping.PingManager;
import com.dukascopy.dds4.ping.PingStats;
import com.dukascopy.dds4.transport.common.mina.DisconnectReason;
import com.dukascopy.dds4.transport.common.protocol.binary.BinaryProtocolMessage;
import com.dukascopy.dds4.transport.msg.system.BinaryPartMessage;
import com.dukascopy.dds4.transport.msg.system.ChildSocketAuthAcceptorMessage;
import com.dukascopy.dds4.transport.msg.system.CurrencyMarket;
import com.dukascopy.dds4.transport.msg.system.DisconnectRequestMessage;
import com.dukascopy.dds4.transport.msg.system.ErrorResponseMessage;
import com.dukascopy.dds4.transport.msg.system.HeartbeatOkResponseMessage;
import com.dukascopy.dds4.transport.msg.system.HeartbeatRequestMessage;
import com.dukascopy.dds4.transport.msg.system.InstrumentableLowMessage;
import com.dukascopy.dds4.transport.msg.system.InvocationRequest;
import com.dukascopy.dds4.transport.msg.system.JSonSerializableWrapper;
import com.dukascopy.dds4.transport.msg.system.MessageGroup;
import com.dukascopy.dds4.transport.msg.system.PrimarySocketAuthAcceptorMessage;
import com.dukascopy.dds4.transport.msg.system.ProtocolMessage;
import com.dukascopy.dds4.transport.msg.system.RequestInProcessMessage;
import com.dukascopy.dds4.transport.msg.system.StreamHeaderMessage;
import com.dukascopy.dds4.transport.msg.system.StreamingStatus;
import com.dukascopy.dds4.transport.msg.types.StreamState;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.SettableFuture;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.ssl.SslHandshakeCompletionEvent;
import io.netty.util.Attribute;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

@Sharable
class ClientProtocolHandler extends SimpleChannelInboundHandler<BinaryProtocolMessage> {
    private static final Logger LOGGER = LoggerFactory.getLogger(ClientProtocolHandler.class);
    private final TransportClientSession clientSession;
    private final long droppableMessagesClientTTL;
    private final ListeningExecutorService eventExecutor;
    private final ListeningExecutorService authEventExecutor;
    private final ListeningExecutorService streamProcessingExecutor;
    private final ListeningExecutorService syncRequestProcessingExecutor;
    private final List<Thread> eventExecutorThreadsForLogging = Collections.synchronizedList(new ArrayList<>());
    private final AtomicLong lastSendingWarningTime = new AtomicLong(System.nanoTime() - 100000000000L);
    private final AtomicLong lastSendingErrorTime = new AtomicLong(System.nanoTime() - 100000000000L);
    private final AtomicLong lastExecutionWarningTime = new AtomicLong(System.nanoTime() - 100000000000L);
    private final AtomicLong lastExecutionErrorTime = new AtomicLong(System.nanoTime() - 100000000000L);
    private final Map<Class<?>, Map<String, DroppableMessageScheduling>>
        lastScheduledDropableMessages
        = new ConcurrentHashMap<>();
    private final boolean logSkippedDroppableMessages;
    private final AtomicBoolean logEventPoolThreadDumpsOnLongExecution;
    private final boolean sendCpuInfoToServer;
    private final ThreadLocal<int[]>
        eventExecutorMessagesCounterThreadLocal
        = ThreadLocal.withInitial(() -> new int[1]);
    private final ThreadLocal<int[]> sentMessagesCounterThreadLocal = ThreadLocal.withInitial(() -> new int[1]);
    private final Map<String, BlockingBinaryStream> streams = new HashMap<>();
    private ClientConnector clientConnector;

    public ClientProtocolHandler(final TransportClientSession clientSession) {

        this.clientSession = clientSession;
        this.droppableMessagesClientTTL = clientSession.getDroppableMessagesClientTTL();
        this.logSkippedDroppableMessages = clientSession.isLogSkippedDroppableMessages();
        this.logEventPoolThreadDumpsOnLongExecution = clientSession.getLogEventPoolThreadDumpsOnLongExecution();
        this.eventExecutor = this.initEventExecutor();
        this.authEventExecutor = this.initAuthEventExecutor();
        this.streamProcessingExecutor = this.initStreamProcessingExecutor();
        this.syncRequestProcessingExecutor = this.initSyncRequestProcessingExecutor();
        this.sendCpuInfoToServer = clientSession.isSendCpuInfoToServer();
    }

    public void setClientConnector(final ClientConnector clientConnector) {

        this.clientConnector = clientConnector;
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

    public void terminate() {

        this.shutdown(this.eventExecutor,
                      this.clientSession.getEventPoolTerminationTimeUnitCount(),
                      this.clientSession.getEventPoolTerminationTimeUnit());
        this.shutdown(this.authEventExecutor,
                      this.clientSession.getAuthEventPoolTerminationTimeUnitCount(),
                      this.clientSession.getAuthEventPoolTerminationTimeUnit());
        this.shutdown(this.streamProcessingExecutor,
                      this.clientSession.getStreamProcessingPoolTerminationTimeUnitCount(),
                      this.clientSession.getStreamProcessingPoolTerminationTimeUnit());
        this.shutdown(this.syncRequestProcessingExecutor,
                      this.clientSession.getSyncRequestProcessingPoolTerminationTimeUnitCount(),
                      this.clientSession.getSyncRequestProcessingPoolTerminationTimeUnit());
    }

    private void shutdown(final ListeningExecutorService executor,
                          final long waitTimeUnitCount,
                          final TimeUnit waitTimeUnit) {

        executor.shutdown();

        try {
            if (!executor.awaitTermination(waitTimeUnitCount, waitTimeUnit)) {
                executor.shutdownNow();
            }
        } catch (final InterruptedException e) {

        }

    }

    //TODO RXJava
    protected void channelRead0(final ChannelHandlerContext ctx, final BinaryProtocolMessage msg) throws Exception {

        try {
            final Attribute<ChannelAttachment> channelAttachmentAttribute = ctx.channel()
                                                                               .attr(ChannelAttachment
                                                                                         .CHANNEL_ATTACHMENT_ATTRIBUTE_KEY);
            final ChannelAttachment attachment = channelAttachmentAttribute.get();





            LOGGER.trace("[{}] Message received {}, primary channel: {}",
                         this.clientSession.getTransportName(),
                         msg,
                         attachment.isPrimaryConnection());


            attachment.setLastReadIoTime(System.currentTimeMillis());
            this.processControlRequest(ctx, msg, attachment);
        } catch (final Exception e) {
            LOGGER.error("[" + this.clientSession.getTransportName() + "] " + e.getMessage(), e);
            throw e;
        }
    }

    public void channelInactive(final ChannelHandlerContext ctx) throws Exception {

        super.channelInactive(ctx);
        final Attribute<ChannelAttachment> channelAttachmentAttribute = ctx.channel()
                                                                           .attr(ChannelAttachment
                                                                                     .CHANNEL_ATTACHMENT_ATTRIBUTE_KEY);
        final ChannelAttachment attachment = channelAttachmentAttribute.get();
        if (attachment != null && this.clientConnector != null) {
            if (attachment.isPrimaryConnection()) {
                this.clientConnector.primaryChannelDisconnected();
            } else {
                this.clientConnector.secondaryChannelDisconnected();
            }
        }

    }

    public void userEventTriggered(final ChannelHandlerContext ctx, final Object evt) throws Exception {

        super.userEventTriggered(ctx, evt);
        if (evt instanceof SslHandshakeCompletionEvent && ((SslHandshakeCompletionEvent) evt).isSuccess()) {
            this.clientConnector.sslHandshakeSuccess();
        } else if (evt instanceof ProtocolVersionNegotiationSuccessEvent) {
            this.clientConnector.protocolVersionHandshakeSuccess();
        }

    }

    public void exceptionCaught(final ChannelHandlerContext ctx, final Throwable cause) throws Exception {

        LOGGER.error(cause.getMessage(), cause);

        ctx.close();
    }

    public void handleAuthorization(final ClientAuthorizationProvider authorizationProvider, final Channel session) {

        LOGGER.debug("[{}] Calling authorize on authorization provider", this.clientSession.getTransportName());
        authorizationProvider.authorize(new NettyIoSessionWrapperAdapter(session) {
            public ChannelFuture write(final Object message) {

                return writeMessage(this.channel, (BinaryProtocolMessage) message);
            }
        });
    }

    private void processAuthorizationMessage(final ChannelHandlerContext ctx,
                                             final BinaryProtocolMessage requestMessage) {

        LOGGER.debug("[{}] Sending message [{}] to authorization provider",
                     this.clientSession.getTransportName(),
                     requestMessage);
        this.clientSession.getAuthorizationProvider().messageReceived(new NettyIoSessionWrapperAdapter(ctx.channel()) {
            public ChannelFuture write(final Object message) {

                return writeMessage(this.channel, (BinaryProtocolMessage) message);
            }
        }, (ProtocolMessage) requestMessage);
    }

    void fireAuthorizedEvent(final ClientListener clientListener, final ITransportClient transportClient) {

        final AbstractEventExecutorTask task = new AbstractEventExecutorTask(this.clientSession, this) {
            public void run() {

                try {
                    clientListener.authorized(transportClient);
                } catch (final Throwable e) {
                    LOGGER.error("[" + clientSession.getTransportName() + "] " + e.getMessage(), e);
                    clientConnector.disconnect(new ClientDisconnectReason(DisconnectReason.EXCEPTION_CAUGHT,
                                                                          String.format(
                                                                              "Exception caught while authorized "
                                                                              + "called %s",
                                                                              e.getMessage()),
                                                                          e));
                }

            }

            public Object getOrderKey() {

                return "";
            }
        };
        task.executeInExecutor(this.authEventExecutor);
    }

    void fireDisconnectedEvent(final ClientListener clientListener,
                               final ITransportClient transportClient,
                               final DisconnectedEvent disconnectedEvent) {

        final AbstractEventExecutorTask task = new AbstractEventExecutorTask(this.clientSession, this) {
            public void run() {

                try {
                    terminateStreams();
                    clientListener.disconnected(transportClient, disconnectedEvent);
                } catch (final Throwable e) {
                    LOGGER.error("[{}]", clientSession.getTransportName(), e);
                    clientConnector.disconnect(new ClientDisconnectReason(DisconnectReason.EXCEPTION_CAUGHT,
                                                                          String.format(
                                                                              "Exception caught while disconnected "
                                                                              + "called %s",
                                                                              e.getMessage()),
                                                                          e));
                }

            }

            public Object getOrderKey() {

                return "";
            }
        };
        task.executeInExecutor(this.authEventExecutor);
    }

    public ChannelFuture writeMessage(final Channel channel, final BinaryProtocolMessage responseMessage) {

        final int[] messagesCounter = this.sentMessagesCounterThreadLocal.get();
        ++messagesCounter[0];
        boolean notWritable = false;
        if (!channel.isWritable()) {
            notWritable = true;
        }

        final ChannelFuture channelFuture = channel.writeAndFlush(responseMessage);
        final ChannelAttachment ca = channel.attr(ChannelAttachment.CHANNEL_ATTACHMENT_ATTRIBUTE_KEY).get();
        channelFuture.addListener(ca.getWriteIoListener());
        if (notWritable) {
            if (this.clientSession.getPrimaryConnectionPingTimeout() > 0L) {
                final ChannelWriteTimeoutChecker
                    channelWriteTimeoutChecker
                    = new ChannelWriteTimeoutChecker(this.clientSession, channelFuture);
                final ScheduledFuture<?> scheduledFuture = this.clientSession.getScheduledExecutorService().schedule(
                    channelWriteTimeoutChecker,
                    this.clientSession.getPrimaryConnectionPingTimeout(),
                    TimeUnit.MILLISECONDS);
                channelWriteTimeoutChecker.setScheduledFuture(scheduledFuture);
                channelFuture.addListener(channelWriteTimeoutChecker);
            }

            if (this.clientSession.getSendCompletionErrorDelay() > 0L) {
                this.addErrorTimeoutSendingCheck(channelFuture);
            }
        } else if (this.clientSession.getSendCompletionErrorDelay() > 0L
                   && this.clientSession.getSendCompletionDelayCheckEveryNTimesError() > 0
                   && messagesCounter[0] % this.clientSession.getSendCompletionDelayCheckEveryNTimesError() == 0) {
            this.addErrorTimeoutSendingCheck(channelFuture);
        } else if (this.clientSession.getSendCompletionWarningDelay() > 0L
                   && this.clientSession.getSendCompletionDelayCheckEveryNTimesWarning() > 0
                   && messagesCounter[0] % this.clientSession.getSendCompletionDelayCheckEveryNTimesWarning() == 0) {
            this.addWarningTimeoutSendingCheck(channelFuture);
        }

        return channelFuture;
    }

    private void addWarningTimeoutSendingCheck(final ChannelFuture channelFuture) {

        final long procStartTime = System.currentTimeMillis();
        this.clientSession.getScheduledExecutorService().schedule(new Runnable() {
            public void run() {

                if (!channelFuture.isDone()) {
                    final long nanoTime = System.nanoTime();
                    final long lastSendingWarningTimeLocal = lastSendingWarningTime.get();
                    if (lastSendingWarningTimeLocal + 5000000000L < nanoTime && lastSendingWarningTime.compareAndSet(
                        lastSendingWarningTimeLocal,
                        nanoTime)) {
                        LOGGER.warn(
                            "[{}] Message sending takes too long time to complete: {}m and is still waiting it's turn"
                            + ". Timeout time: {}ms, possible network problem",
                            clientSession.getTransportName(),
                            System.currentTimeMillis() - procStartTime,
                            clientSession.getSendCompletionWarningDelay());
                        channelFuture.addListener(new ChannelFutureListener() {
                            public void operationComplete(final ChannelFuture future) throws Exception {

                                if (future.isSuccess()) {
                                    if (clientSession.getSendCompletionErrorDelay() > 0L
                                        && System.currentTimeMillis() - procStartTime
                                           >= clientSession.getSendCompletionErrorDelay()) {
                                        LOGGER.error(
                                            "[{}] Message sending took {}ms, critical timeout time {}ms, possible "
                                            + "network problem",
                                            clientSession.getTransportName(),
                                            System.currentTimeMillis() - procStartTime,
                                            clientSession.getSendCompletionErrorDelay());
                                    } else {
                                        LOGGER.warn(
                                            "[{}] Message sending took {}ms, timeout time {}ms, possible network "
                                            + "problem",
                                            clientSession.getTransportName(),
                                            System.currentTimeMillis() - procStartTime,
                                            clientSession.getSendCompletionWarningDelay());
                                    }
                                }

                            }
                        });
                    }
                }

            }
        }, this.clientSession.getSendCompletionWarningDelay(), TimeUnit.MILLISECONDS);
    }

    private void addErrorTimeoutSendingCheck(final ChannelFuture channelFuture) {

        final long procStartTime = System.currentTimeMillis();
        this.clientSession.getScheduledExecutorService().schedule(new Runnable() {
            public void run() {

                if (!channelFuture.isDone()) {
                    final long nanoTime = System.nanoTime();
                    final long lastSendingErrorTimeLocal = lastSendingErrorTime.get();
                    if (lastSendingErrorTimeLocal + 1000000000L < nanoTime && lastSendingErrorTime.compareAndSet(
                        lastSendingErrorTimeLocal,
                        nanoTime)) {
                        LOGGER.error(
                            "[{}] Message was not sent in timeout time [{}] and is still waiting it's turn, CRITICAL "
                            + "SEND TIME: {}ms, possible network problem",
                            clientSession.getTransportName(),
                            clientSession.getSendCompletionErrorDelay(),
                            System.currentTimeMillis() - procStartTime);
                        channelFuture.addListener(new ChannelFutureListener() {
                            public void operationComplete(final ChannelFuture future) throws Exception {

                                if (future.isSuccess()) {
                                    LOGGER.error(
                                        "[{}] Message sending took {}ms, critical timeout time {}ms, possible network"
                                        + " problem",
                                        clientSession.getTransportName(),
                                        System.currentTimeMillis() - procStartTime,
                                        clientSession.getSendCompletionErrorDelay());
                                }

                            }
                        });
                    }
                }

            }
        }, this.clientSession.getSendCompletionErrorDelay(), TimeUnit.MILLISECONDS);
    }

    //TODO RxJava
    protected void processControlRequest(final ChannelHandlerContext ctx,
                                         final BinaryProtocolMessage msg,
                                         final ChannelAttachment attachment) {

        if (!(msg instanceof ProtocolMessage)) {
            return;
        }


        final ProtocolMessage requestMessage = (ProtocolMessage) msg;
        if (requestMessage instanceof HeartbeatRequestMessage) {
            try {
                final HeartbeatOkResponseMessage okResponseMessage = new HeartbeatOkResponseMessage();
                okResponseMessage.setRequestTime(((HeartbeatRequestMessage) requestMessage).getRequestTime());
                okResponseMessage.setReceiveTime(System.currentTimeMillis());
                okResponseMessage.setSynchRequestId(requestMessage.getSynchRequestId());
                final PingManager pm = this.clientSession.getPingManager(attachment.isPrimaryConnection());
                final Double systemCpuLoad = this.sendCpuInfoToServer ? pm.getSystemCpuLoad() : null;
                final Double processCpuLoad = this.sendCpuInfoToServer ? pm.getProcessCpuLoad() : null;
                okResponseMessage.setProcessCpuLoad(processCpuLoad);
                okResponseMessage.setSystemCpuLoad(systemCpuLoad);
                okResponseMessage.setAvailableProcessors(pm.getAvailableProcessors());
                PingStats generalStats = pm.getGeneralStats();
                if (generalStats != null) {
                    okResponseMessage.setSocketWriteInterval(generalStats.getInitiatorSocketWriteInterval()
                                                                         .getRoundedLast());
                } else {
                    generalStats = this.clientSession.getPingManager(attachment.isPrimaryConnection())
                                                     .getGeneralStats();
                    if (generalStats != null) {
                        okResponseMessage.setSocketWriteInterval(generalStats.getInitiatorSocketWriteInterval()
                                                                             .getRoundedLast());
                    }
                }

                if (!this.clientSession.isTerminating()) {
                    this.writeMessage(ctx.channel(), okResponseMessage);
                }
            } catch (final Throwable e) {
                LOGGER.error(String.format("[%s] %s", this.clientSession.getTransportName(), e.getMessage()), e);
                final ErrorResponseMessage errorMessage = new ErrorResponseMessage(String.format(
                    "Error occurred while processing the message [%s]. Error message: [%s:%s]",
                    requestMessage,
                    e.getClass().getName(),
                    e.getMessage()));
                errorMessage.setSynchRequestId(requestMessage.getSynchRequestId());
                if (!this.clientSession.isTerminating()) {
                    this.writeMessage(ctx.channel(), errorMessage);
                }
            }
        } else if (requestMessage instanceof DisconnectRequestMessage) {
            final DisconnectRequestMessage message = (DisconnectRequestMessage) requestMessage;
            final DisconnectReason reason = message.getReason() == null
                                            ? DisconnectReason.SERVER_APP_REQUEST
                                            : message.getReason();

            this.clientConnector.setDisconnectReason(new ClientDisconnectReason(reason,
                                                                                message.getHint(),
                                                                                "Disconnect request received"));
        } else if (requestMessage instanceof PrimarySocketAuthAcceptorMessage) {
            this.clientConnector.setPrimarySocketAuthAcceptorMessage((PrimarySocketAuthAcceptorMessage) requestMessage);
        } else if (requestMessage instanceof ChildSocketAuthAcceptorMessage) {
            this.clientConnector.setChildSocketAuthAcceptorMessage((ChildSocketAuthAcceptorMessage) requestMessage);
        } else if (requestMessage instanceof HeartbeatOkResponseMessage) {
            this.processSyncResponse(requestMessage);
        } else if (requestMessage instanceof JSonSerializableWrapper) {
            this.processSerializableRequest(ctx, (JSonSerializableWrapper) requestMessage);
        } else if (msg instanceof BinaryPartMessage) {
            final BinaryPartMessage binaryPart = (BinaryPartMessage) msg;
            this.streamPartReceived(ctx, attachment, binaryPart);
        } else if (msg instanceof StreamHeaderMessage) {
            final StreamHeaderMessage streamHeader = (StreamHeaderMessage) msg;
            this.createStream(ctx, attachment, streamHeader);
        } else if (msg instanceof StreamingStatus) {
            final StreamingStatus status = (StreamingStatus) msg;
            this.streamStatusReceived(status);
        } else if (this.clientConnector.isAuthorizing()) {
            this.processAuthorizationMessage(ctx, msg);
        } else {
            if (requestMessage.getSynchRequestId() != null && this.processSyncResponse(requestMessage)) {
                return;
            }

            this.fireFeedbackMessageReceived(ctx, requestMessage);
        }


    }

    private void streamPartReceived(final ChannelHandlerContext ctx,
                                    final ChannelAttachment attachment,
                                    final BinaryPartMessage binaryPart) {

        this.streamProcessingExecutor.submit(new Runnable() {
            public void run() {

                final BlockingBinaryStream stream = getStream(binaryPart.getStreamId());
                if (stream == null) {
                    final StreamingStatus ss = new StreamingStatus();
                    ss.setStreamId(binaryPart.getStreamId());
                    ss.setState(StreamState.STATE_ERROR);
                    writeMessage(ctx.channel(), ss);
                } else {
                    boolean terminated = false;

                    try {
                        stream.binaryPartReceived(binaryPart);
                    } catch (final IOException var6) {
                        LOGGER.error(var6.getMessage(), var6);

                        terminated = true;
                        stream.ioTerminate(var6.getMessage());
                    }

                    if (binaryPart.isEof() || terminated) {
                        synchronized (streams) {
                            streams.remove(binaryPart.getStreamId());
                        }
                    }
                }

            }
        });
    }

    private void createStream(final ChannelHandlerContext ctx,
                              final ChannelAttachment attachment,
                              final StreamHeaderMessage stream) {

        this.streamProcessingExecutor.submit(() -> {

            final StreamListener streamListener = clientSession.getStreamListener();
            if (streamListener != null) {
                final BlockingBinaryStream bbs = new BlockingBinaryStream(stream.getStreamId(),
                                                                          ctx,
                                                                          clientSession.getStreamBufferSize(),
                                                                          clientSession
                                                                              .getStreamChunkProcessingTimeout());
                synchronized (streams) {
                    streams.put(stream.getStreamId(), bbs);
                }

                streamListener.handleStream(bbs);
            }
        });
    }

    private void streamStatusReceived(final StreamingStatus status) {

        this.streamProcessingExecutor.submit(() -> {

            final BlockingBinaryStream bbs = getStream(status.getStreamId());
            if (bbs != null) {
                safeTerminate(bbs, "Server error " + status.getState());
                synchronized (streams) {
                    streams.remove(bbs.getStreamId());
                }
            }

        });
    }

    private synchronized void terminateStreams() {

        final List<String> st = new ArrayList<>(this.streams.keySet());
        st.forEach(s -> {
            final BlockingBinaryStream bbs = streams.remove(s);
            safeTerminate(bbs, "Connection error");
        });
    }

    private void safeTerminate(final BlockingBinaryStream bbs, final String reason) {

        if (bbs != null) {
            try {
                bbs.ioTerminate(reason);
            } finally {
                try {
                    bbs.close();
                } catch (final IOException e) {
                    LOGGER.error("Failed to close stream " + bbs.getStreamId(), e);

                }

            }

        }
    }

    protected boolean processSyncResponse(final ProtocolMessage message) {

        final RequestMessageTransportListenableFuture synchRequestFuture = this.clientSession.getSyncRequestFuture(
            message.getSynchRequestId());
        final boolean result;
        if (synchRequestFuture != null) {

            if (message instanceof RequestInProcessMessage) {
                result = true;
                synchRequestFuture.setInProcessResponseLastTime(System.currentTimeMillis());
            } else {
                this.syncRequestProcessingExecutor.submit(new OrderedRunnable() {
                    public void run() {

                        try {
                            synchRequestFuture.set(message);
                        } catch (final Throwable var2) {
                            LOGGER.error("Failed to process sync response for " + message, var2);

                        }

                    }

                    public Object getOrderKey() {

                        return clientSession.getConcurrencyPolicy().getConcurrentKey(message);
                    }
                });
                result = !this.clientSession.isDuplicateSyncMessagesToClientListeners();
            }
        } else {
            result = false;
        }

        return result;
    }

    private void processSerializableRequest(final ChannelHandlerContext ctx,
                                            final JSonSerializableWrapper jSonSerializableWrapper) {

        final Serializable data = jSonSerializableWrapper.getData();
        if (data != null) {
            if (data instanceof InvocationResult) {
                final InvocationResult invocationResult = (InvocationResult) data;
                this.clientSession.getRemoteCallSupport().invocationResultReceived(invocationResult);
            } else if (data instanceof InvocationRequest) {
                final InvocationRequest invocationRequest = (InvocationRequest) data;
                final EventExecutorChannelTask task = new EventExecutorChannelTask(ctx,
                                                                                   this.clientSession,
                                                                                   invocationRequest,
                                                                                   0L) {
                    public void run() {

                        try {
                            final JSonSerializableWrapper responseMessage = new JSonSerializableWrapper();
                            final Object impl = clientSession.getRemoteCallSupport().getInterfaceImplementation(
                                invocationRequest.getInterfaceClass());
                            if (impl != null) {
                                InvocationResult resultx;
                                try {
                                    final Serializable
                                        invocationResult
                                        = (Serializable) TransportHelper.invokeRemoteRequest(invocationRequest, impl);
                                    resultx = new InvocationResult(invocationResult, invocationRequest.getRequestId());
                                    resultx.setState(InvocationResult.STATE_OK);
                                    responseMessage.setData(resultx);
                                } catch (final Exception var5) {
                                    resultx = new InvocationResult(null, invocationRequest.getRequestId());
                                    resultx.setState(InvocationResult.STATE_ERROR);
                                    if (var5 instanceof InvocationTargetException) {
                                        resultx.setThrowable(var5.getCause());
                                    } else {
                                        resultx.setThrowable(var5);
                                    }

                                    responseMessage.setData(resultx);
                                }
                            } else {
                                final InvocationResult result = new InvocationResult(null,
                                                                                     invocationRequest.getRequestId());
                                result.setState(InvocationResult.STATE_ERROR);
                                result.setThrowable(new Exception("Client does not provide interface: "
                                                                  + invocationRequest.getInterfaceClass()));
                                responseMessage.setData(result);
                            }

                            writeMessage(ctx.channel(), responseMessage);
                        } catch (final Throwable e) {
                            LOGGER.error("[" + clientSession.getTransportName() + "] " + e.getMessage(), e);
                            clientConnector.disconnect(new ClientDisconnectReason(DisconnectReason.EXCEPTION_CAUGHT,
                                                                                  String.format(
                                                                                      "Exception caught during "
                                                                                      + "processing serialized "
                                                                                      + "request %s",
                                                                                      e.getMessage()),
                                                                                  e));
                        }

                    }

                    public Object getOrderKey() {

                        return clientSession.getConcurrencyPolicy().getConcurrentKey(jSonSerializableWrapper);
                    }
                };
                task.executeInExecutor(this.eventExecutor, null);
            }
        }

    }

    private DroppableMessageScheduling getDroppableScheduling(final ProtocolMessage message, final String instrument) {

        final Class<?> clazz = message.getClass();
        Map<String, DroppableMessageScheduling>
            lastScheduledMessagesMap
            = this.lastScheduledDropableMessages.get(clazz);
        if (lastScheduledMessagesMap == null) {
            lastScheduledMessagesMap = MapHelper.getAndPutIfAbsent(this.lastScheduledDropableMessages,
                                                                   clazz,
                                                                   new MapHelper.IValueCreator<Map<String,
                                                                       DroppableMessageScheduling>>() {
                                                                       @Override
                                                                       public Map<String, DroppableMessageScheduling>
                                                                       create() {

                                                                           return new ConcurrentHashMap<String,
                                                                               DroppableMessageScheduling>();
                                                                       }
                                                                   });
        }

        DroppableMessageScheduling lastScheduledMessageInfo = lastScheduledMessagesMap.get(instrument);
        if (lastScheduledMessageInfo == null) {
            lastScheduledMessageInfo = MapHelper.getAndPutIfAbsent(lastScheduledMessagesMap,
                                                                   instrument,
                                                                   () -> new DroppableMessageScheduling());
        }

        return lastScheduledMessageInfo;
    }

    protected void fireFeedbackMessageReceived(final ChannelHandlerContext ctx, final ProtocolMessage message) {

        final long currentDropableMessageTime = this.clientSession.isSkipDroppableMessages()
                                                ? this.checkAndRecordScheduleForDroppableMessage(message)
                                                : 0L;
        if (message instanceof CurrencyMarket) {
            this.clientSession.tickReceived();
        }

        final CopyOnWriteArrayList<ClientListener> listeners = this.clientSession.getListeners();
        final ISessionStats stats = this.clientSession.getSessionStats();
        final long creationTime;
        if (stats != null) {
            creationTime = System.currentTimeMillis();
            stats.messageInExecutionQueue(creationTime, message);
        } else {
            creationTime = 0L;
        }

        final EventExecutorChannelTask task = new EventExecutorChannelTask(ctx,
                                                                           this.clientSession,
                                                                           message,
                                                                           currentDropableMessageTime) {
            public void run() {

                try {
                    final boolean canProcessCurrentDroppable = canProcessDroppableMessage(this.message,
                                                                                          this.currentDropableMessageTime);
                    if (!canProcessCurrentDroppable) {
                        clientSession.getDroppedMessageCounter().incrementAndGet();
                        if (logSkippedDroppableMessages) {
                            LOGGER.warn("Newer message already has arrived, current processing is skipped <{}>",
                                        this.message);
                        }

                        if (stats != null) {
                            final long droppedTime = System.currentTimeMillis();
                            stats.messageDropped(droppedTime, droppedTime - creationTime, this.message);
                        }

                        return;
                    }
                } catch (final Throwable e) {
                    LOGGER.error("[{}] ", clientSession.getTransportName(), e);

                }

                long beforeExecution = 0L;


                try {

                    if (stats != null) {
                        beforeExecution = System.currentTimeMillis();
                        stats.messageExecutionStarted(beforeExecution, beforeExecution - creationTime, this.message);
                    }

                    if (this.message instanceof MessageGroup) {
                        final MessageGroup group = (MessageGroup) this.message;

                        for (final ProtocolMessage protocolMessage : group.getMessages()) {
                            for (final ClientListener clientListenerx : listeners) {
                                try {
                                    clientListenerx.feedbackMessageReceived(clientSession.getTransportClient(),
                                                                            protocolMessage);
                                } catch (final Throwable e) {
                                    LOGGER.error("[{}] ", clientSession.getTransportName(), e);

                                }
                            }
                        }


                    } else {
                        for (final ClientListener clientListenerx : listeners) {
                            try {
                                clientListenerx.feedbackMessageReceived(clientSession.getTransportClient(),
                                                                        this.message);
                            } catch (final Throwable e) {
                                LOGGER.error("[{}] ", clientSession.getTransportName(), e);

                            }
                        }
                    }


                } catch (final Throwable e) {
                    LOGGER.error("[{}] ", clientSession.getTransportName(), e);


                } finally {

                    if (stats != null) {
                        final long afterExecutionx = System.currentTimeMillis();
                        stats.messageExecutionFinished(afterExecutionx,
                                                       afterExecutionx - beforeExecution,
                                                       this.message);
                    }


                }


            }


            public Object getOrderKey() {

                return clientSession.getConcurrencyPolicy().getConcurrentKey(this.message);
            }
        };
        task.executeInExecutor(this.eventExecutor, stats);
    }

    protected boolean canProcessDroppableMessage(final ProtocolMessage message, final long currentDropableMessageTime) {

        if (currentDropableMessageTime > 0L && message instanceof InstrumentableLowMessage) {
            final InstrumentableLowMessage instrumentable = (InstrumentableLowMessage) message;
            final String instrument = instrumentable.getInstrument();
            if (instrument != null) {
                final DroppableMessageScheduling scheduling = this.getDroppableScheduling(message, instrument);
                final long lastArrivedMessageTime = scheduling.getLastScheduledTime();
                final int scheduledCount = scheduling.getScheduledCount();
                scheduling.executed();
                if (lastArrivedMessageTime - currentDropableMessageTime > this.droppableMessagesClientTTL
                    && scheduledCount > 1) {
                    return false;
                }
            }
        }

        return true;
    }

    private long checkAndRecordScheduleForDroppableMessage(final ProtocolMessage message) {

        final long currentInstrumentableMessageTime;
        if (message instanceof InstrumentableLowMessage) {
            final InstrumentableLowMessage instrumentable = (InstrumentableLowMessage) message;
            final String instrument = instrumentable.getInstrument();
            if (instrument != null && instrumentable.isDropOnTimeout()) {
                if (message instanceof CurrencyMarket) {
                    final CurrencyMarket cm = (CurrencyMarket) message;
                    currentInstrumentableMessageTime = cm.getCreationTimestamp();
                } else {
                    final Long t = message.getTimestamp();
                    currentInstrumentableMessageTime = t == null ? 0L : t;
                }

                if (currentInstrumentableMessageTime > 0L) {
                    final DroppableMessageScheduling scheduling = this.getDroppableScheduling(message, instrument);
                    scheduling.scheduled(currentInstrumentableMessageTime);
                }
            } else {
                currentInstrumentableMessageTime = 0L;
            }
        } else {
            currentInstrumentableMessageTime = 0L;
        }

        return currentInstrumentableMessageTime;
    }

    protected void checkAndLogEventPoolThreadDumps() {

        if (this.logEventPoolThreadDumpsOnLongExecution.get()) {
            final List<Thread> threads = new ArrayList(this.eventExecutorThreadsForLogging);
            final Iterator i$ = threads.iterator();

            while (i$.hasNext()) {
                final Thread thread = (Thread) i$.next();
                final long before = System.currentTimeMillis();
                final StackTraceElement[] stackTrace = thread.getStackTrace();
                LOGGER.warn("Transport client [{}, {}] thread's [{}] stack trace [{}], dump taking costed [{}]ms",
                            this.clientSession.getTransportName(),
                            this.clientSession.getAddress(),
                            thread.getName(),
                            Arrays.toString(stackTrace),
                            System.currentTimeMillis() - before);
            }

        }
    }

    private BlockingBinaryStream getStream(final String streamId) {

        synchronized (this.streams) {
            return this.streams.get(streamId);

        }
    }

    private abstract class EventExecutorChannelTask implements OrderedRunnable {
        protected final ProtocolMessage message;
        protected final long currentDropableMessageTime;
        private final Channel channel;
        private final TransportClientSession clientSession;
        private Runnable delayedExecutionTask;
        private long procStartTime;
        private long sleepTime = 10L;

        public EventExecutorChannelTask(final ChannelHandlerContext ctx,
                                        final TransportClientSession clientSession,
                                        final ProtocolMessage message,
                                        final long currentDropableMessageTime) {

            this.channel = ctx.channel();
            this.clientSession = clientSession;
            this.message = message;
            this.currentDropableMessageTime = currentDropableMessageTime;
        }

        public void executeInExecutor(final ListeningExecutorService executor, final ISessionStats stats) {

            final int[] messagesCounter = eventExecutorMessagesCounterThreadLocal.get();
            ++messagesCounter[0];
            final ListenableFuture future;
            final long procStartTime;
            if (this.clientSession.getEventExecutionErrorDelay() > 0L
                && this.clientSession.getEventExecutionDelayCheckEveryNTimesError() > 0
                && messagesCounter[0] % this.clientSession.getEventExecutionDelayCheckEveryNTimesError() == 0) {
                future = this.executeInExecutor(executor, true);
                procStartTime = System.currentTimeMillis();
                final ScheduledExecutorService
                    scheduledExecutorService
                    = this.clientSession.getScheduledExecutorService();
                if (!scheduledExecutorService.isShutdown() && !scheduledExecutorService.isTerminated()) {

                    scheduledExecutorService.schedule(new Runnable() {
                        public void run() {

                            if (!future.isDone()) {
                                final long nanoTime = System.nanoTime();
                                final long lastExecutionErrorTimeLocal = lastExecutionErrorTime.get();
                                if (lastExecutionErrorTimeLocal + 1000000000L < nanoTime
                                    && lastExecutionErrorTime.compareAndSet(lastExecutionErrorTimeLocal, nanoTime)) {
                                    final long now = System.currentTimeMillis();
                                    final long postponeInterval = now - procStartTime;
                                    LOGGER.error(
                                        "[{}] Event did not execute in timeout time [{}] and is still executing, "
                                        + "CRITICAL EXECUTION WAIT TIME: {}ms, possible application problem or "
                                        + "deadLock, message [{}]",
                                        clientSession.getTransportName(),
                                        clientSession.getEventExecutionErrorDelay(),
                                        postponeInterval,
                                        StrUtils.toSafeString(message, 100));
                                    checkAndLogEventPoolThreadDumps();
                                    if (stats != null) {
                                        stats.messageProcessingPostponeError(now, postponeInterval, message);
                                    }

                                    future.addListener(new Runnable() {
                                        @Override
                                        public void run() {

                                            LOGGER.error(
                                                "[{}] Event execution took {}ms, critical timeout time {}ms, possible"
                                                + " application problem or deadLock, message [{}]",
                                                clientSession.getTransportName(),
                                                System.currentTimeMillis() - procStartTime,
                                                clientSession.getEventExecutionErrorDelay(),
                                                StrUtils.toSafeString(message, 100));
                                        }
                                    }, MoreExecutors.newDirectExecutorService());
                                }
                            }

                        }
                    }, this.clientSession.getEventExecutionErrorDelay(), TimeUnit.MILLISECONDS);
                }
            } else if (this.clientSession.getEventExecutionWarningDelay() > 0L
                       && this.clientSession.getEventExecutionDelayCheckEveryNTimesWarning() > 0
                       && messagesCounter[0] % this.clientSession.getEventExecutionDelayCheckEveryNTimesWarning()
                          == 0) {
                future = this.executeInExecutor(executor, true);
                procStartTime = System.currentTimeMillis();
                this.clientSession.getScheduledExecutorService().schedule(new Runnable() {
                    public void run() {

                        if (!future.isDone()) {
                            final long nanoTime = System.nanoTime();
                            final long lastExecutionWarningTimeLocal = lastExecutionWarningTime.get();
                            if (lastExecutionWarningTimeLocal + 5000000000L < nanoTime
                                && lastExecutionWarningTime.compareAndSet(lastExecutionWarningTimeLocal, nanoTime)) {
                                final long now = System.currentTimeMillis();
                                final long postponeInterval = now - procStartTime;
                                LOGGER.warn(
                                    "[{}] Event execution takes too long, execution time: {}ms and still executing, "
                                    + "possible application problem or deadLock, message [{}]",
                                    clientSession.getTransportName(),
                                    postponeInterval,
                                    StrUtils.toSafeString(message, 100));
                                checkAndLogEventPoolThreadDumps();
                                if (stats != null) {
                                    stats.messageProcessingPostponeError(now, postponeInterval, message);
                                }

                                future.addListener(new Runnable() {
                                    @Override
                                    public void run() {

                                        if (clientSession.getEventExecutionErrorDelay() > 0L
                                            && System.currentTimeMillis() - procStartTime
                                               >= clientSession.getEventExecutionErrorDelay()) {
                                            LOGGER.error(
                                                "[{}] Event execution took {}ms, critical timeout time {}ms, possible"
                                                + " application problem or deadLock, message [{}]",
                                                clientSession.getTransportName(),
                                                System.currentTimeMillis() - procStartTime,
                                                clientSession.getEventExecutionErrorDelay(),
                                                StrUtils.toSafeString(message, 100));
                                        } else {
                                            LOGGER.warn(
                                                "[{}] Event execution took more time than expected, execution time: "
                                                + "{}ms, possible application problem or deadLock, message [{}]",
                                                clientSession.getTransportName(),
                                                System.currentTimeMillis() - procStartTime,
                                                StrUtils.toSafeString(message, 100));
                                        }

                                    }
                                }, MoreExecutors.newDirectExecutorService());
                            }
                        }

                    }
                }, this.clientSession.getEventExecutionWarningDelay(), TimeUnit.MILLISECONDS);
            } else {
                this.executeInExecutor(executor, false);
            }

        }

        private ListenableFuture<?> executeInExecutor(final ListeningExecutorService executor,
                                                      final boolean withFuture) {

           // final AsyncSettableFuture future;
           final SettableFuture future;
            if (withFuture) {
                 future = SettableFuture.create();
                //future = AsyncSettableFuture.create();
            } else {
                future = null;
            }


            try {
                if (future != null) {
                    future.setFuture(executor.submit(this));
                } else {
                    executor.execute(this);
                }
            } catch (final RejectedExecutionException var5) {
                this.procStartTime = System.currentTimeMillis();
                this.clientSession.getChannelTrafficBlocker().suspend(this.channel);
                if (!logEventPoolThreadDumpsOnLongExecution.get()) {
                    LOGGER.warn("Transport client's [{}, {}] channel reading suspended on [{}]",
                                this.clientSession.getTransportName(),
                                this.clientSession.getAddress(),
                                this.message);
                }

                this.delayedExecutionTask = () -> {

                    try {
                        if (future != null) {
                            future.setFuture(executor.submit(EventExecutorChannelTask.this));
                        } else {
                            executor.execute(EventExecutorChannelTask.this);
                        }

                        clientSession.getChannelTrafficBlocker().resume(channel);
                        if (!logEventPoolThreadDumpsOnLongExecution.get()) {
                            LOGGER.warn("Transport client's [{}, {}] channel reading resumed on [{}]",
                                        clientSession.getTransportName(),
                                        clientSession.getAddress(),
                                        message);
                        }
                    } catch (final RejectedExecutionException var4) {
                        final long currentTime = System.currentTimeMillis();
                        if (currentTime - procStartTime > clientSession.getEventExecutionErrorDelay()) {
//                            LOGGER.error(
//                                "[{}] Event executor queue overloaded, CRITICAL EXECUTION WAIT TIME: {}ms, possible "
//                                + "application problem or deadLock, message [{}]",
//                                clientSession.getTransportName(),
//                                (currentTime - procStartTime),
//                                StrUtils.toSafeString(message, 100));
                            checkAndLogEventPoolThreadDumps();
                            procStartTime = currentTime;
                            sleepTime = 50L;
                        }

                        channel.eventLoop().schedule(delayedExecutionTask, sleepTime, TimeUnit.MILLISECONDS);
                    }

                };
                this.channel.eventLoop().schedule(this.delayedExecutionTask, 5L, TimeUnit.MILLISECONDS);
            }

            return future;
        }
    }
}
