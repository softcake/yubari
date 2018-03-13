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
import org.softcake.yubari.netty.stream.BlockingBinaryStream;
import org.softcake.yubari.netty.stream.StreamListener;
import org.softcake.yubari.netty.util.StrUtils;

import com.dukascopy.dds4.common.orderedExecutor.OrderedThreadPoolExecutor.OrderedRunnable;
import com.dukascopy.dds4.ping.PingManager;
import com.dukascopy.dds4.ping.PingStats;
import com.dukascopy.dds4.transport.common.mina.ClientListener;
import com.dukascopy.dds4.transport.common.mina.DisconnectReason;
import com.dukascopy.dds4.transport.common.mina.DisconnectedEvent;
import com.dukascopy.dds4.transport.common.mina.ExtendedClientDisconnectReason;
import com.dukascopy.dds4.transport.common.mina.ITransportClient;
import com.dukascopy.dds4.transport.common.mina.InvocationResult;
import com.dukascopy.dds4.transport.common.mina.TransportHelper;
import com.dukascopy.dds4.transport.common.protocol.binary.BinaryProtocolMessage;
import com.dukascopy.dds4.transport.common.protocol.mina.ISessionStats;
import com.dukascopy.dds4.transport.msg.system.BinaryPartMessage;
import com.dukascopy.dds4.transport.msg.system.ChildSocketAuthAcceptorMessage;
import com.dukascopy.dds4.transport.msg.system.CurrencyMarket;
import com.dukascopy.dds4.transport.msg.system.DisconnectHint;
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
import com.dukascopy.dds4.transport.netty.AsyncSettableFuture;
import com.dukascopy.dds4.transport.netty.NettyIoSessionWrapperAdapter;
import com.dukascopy.dds4.transport.netty.ProtocolVersionNegotiationSuccessEvent;
import com.dukascopy.dds4.transport.netty.RequestMessageTransportListenableFuture;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
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
    private ClientConnector clientConnector;
    private final ListeningExecutorService eventExecutor;
    private final ListeningExecutorService authEventExecutor;
    private final ListeningExecutorService streamProcessingExecutor;
    private final ListeningExecutorService syncRequestProcessingExecutor;
    private final List<Thread> eventExecutorThreadsForLogging = Collections.synchronizedList(new ArrayList());
    private ThreadLocal<int[]> eventExecutorMessagesCounterThreadLocal = new ThreadLocal<int[]>() {
        protected int[] initialValue() {
            return new int[1];
        }
    };
    private ThreadLocal<int[]> sentMessagesCounterThreadLocal = new ThreadLocal<int[]>() {
        protected int[] initialValue() {
            return new int[1];
        }
    };
    private final AtomicLong lastSendingWarningTime = new AtomicLong(System.nanoTime() - 100000000000L);
    private final AtomicLong lastSendingErrorTime = new AtomicLong(System.nanoTime() - 100000000000L);
    private final AtomicLong lastExecutionWarningTime = new AtomicLong(System.nanoTime() - 100000000000L);
    private final AtomicLong lastExecutionErrorTime = new AtomicLong(System.nanoTime() - 100000000000L);
    private final Map<Class<?>, Map<String, DroppableMessageScheduling>> lastScheduledDropableMessages = new ConcurrentHashMap();
    private final boolean logSkippedDroppableMessages;
    private final AtomicBoolean logEventPoolThreadDumpsOnLongExecution;
    private Map<String, BlockingBinaryStream> streams = new HashMap();
    private final boolean sendCpuInfoToServer;

    public ClientProtocolHandler(TransportClientSession clientSession) {
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

    public void setClientConnector(ClientConnector clientConnector) {
        this.clientConnector = clientConnector;
    }

    private ListeningExecutorService initEventExecutor() {
        ListeningExecutorService executor = TransportHelper.createExecutor(this.clientSession.getEventPoolSize(), this.clientSession.getEventPoolAutoCleanupInterval(), this.clientSession.getCriticalEventQueueSize(), "TransportClientEventExecutorThread", this.eventExecutorThreadsForLogging, this.clientSession.getTransportName(), true);
        return executor;
    }

    private ListeningExecutorService initAuthEventExecutor() {
        ListeningExecutorService executor = TransportHelper.createExecutor(this.clientSession.getAuthEventPoolSize(), this.clientSession.getAuthEventPoolAutoCleanupInterval(), this.clientSession.getCriticalAuthEventQueueSize(), "TransportClientAuthEventExecutorThread", this.eventExecutorThreadsForLogging, this.clientSession.getTransportName(), true);
        return executor;
    }

    private ListeningExecutorService initStreamProcessingExecutor() {
        ListeningExecutorService executor = TransportHelper.createExecutor(this.clientSession.getStreamProcessingPoolSize(), this.clientSession.getStreamProcessingPoolAutoCleanupInterval(), this.clientSession.getCriticalStreamProcessingQueueSize(), "TransportClientStreamProcessingThread", this.eventExecutorThreadsForLogging, this.clientSession.getTransportName(), false);
        return executor;
    }

    private ListeningExecutorService initSyncRequestProcessingExecutor() {
        ListeningExecutorService executor = TransportHelper.createExecutor(this.clientSession.getSyncRequestProcessingPoolSize(), this.clientSession.getSyncRequestProcessingPoolAutoCleanupInterval(), this.clientSession.getCriticalSyncRequestProcessingQueueSize(), "TransportClientSyncRequestProcessingThread", this.eventExecutorThreadsForLogging, this.clientSession.getTransportName(), true);
        return executor;
    }

    ExecutorService getEventExecutor() {
        return this.eventExecutor;
    }

    public void terminate() {
        this.shutdown(this.eventExecutor, this.clientSession.getEventPoolTerminationTimeUnitCount(), this.clientSession.getEventPoolTerminationTimeUnit());
        this.shutdown(this.authEventExecutor, this.clientSession.getAuthEventPoolTerminationTimeUnitCount(), this.clientSession.getAuthEventPoolTerminationTimeUnit());
        this.shutdown(this.streamProcessingExecutor, this.clientSession.getStreamProcessingPoolTerminationTimeUnitCount(), this.clientSession.getStreamProcessingPoolTerminationTimeUnit());
        this.shutdown(this.syncRequestProcessingExecutor, this.clientSession.getSyncRequestProcessingPoolTerminationTimeUnitCount(), this.clientSession.getSyncRequestProcessingPoolTerminationTimeUnit());
    }

    private void shutdown(ListeningExecutorService executor, long waitTimeUnitCount, TimeUnit waitTimeUnit) {
        executor.shutdown();

        try {
            if (!executor.awaitTermination(waitTimeUnitCount, waitTimeUnit)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException var6) {
            ;
        }

    }

    protected void channelRead0(ChannelHandlerContext ctx, BinaryProtocolMessage msg) throws Exception {
        try {
            Attribute<ChannelAttachment> channelAttachmentAttribute = ctx.channel().attr(ChannelAttachment.CHANNEL_ATTACHMENT_ATTRIBUTE_KEY);
            ChannelAttachment attachment = (ChannelAttachment)channelAttachmentAttribute.get();
            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("[{}] Message received {}, primary channel: {}", new Object[]{this.clientSession.getTransportName(), msg, attachment.isPrimaryConnection()});
            }

            attachment.setLastReadIoTime(System.currentTimeMillis());
            this.processControlRequest(ctx, msg, attachment);
        } catch (Exception var5) {
            LOGGER.error("[" + this.clientSession.getTransportName() + "] " + var5.getMessage(), var5);
            throw var5;
        }
    }

    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        super.channelInactive(ctx);
        Attribute<ChannelAttachment> channelAttachmentAttribute = ctx.channel().attr(ChannelAttachment.CHANNEL_ATTACHMENT_ATTRIBUTE_KEY);
        ChannelAttachment attachment = (ChannelAttachment)channelAttachmentAttribute.get();
        if (attachment != null && this.clientConnector != null) {
            if (attachment.isPrimaryConnection()) {
                this.clientConnector.primaryChannelDisconnected();
            } else {
                this.clientConnector.secondaryChannelDisconnected();
            }
        }

    }

    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        super.userEventTriggered(ctx, evt);
        if (evt instanceof SslHandshakeCompletionEvent && ((SslHandshakeCompletionEvent)evt).isSuccess()) {
            this.clientConnector.sslHandshakeSuccess();
        } else if (evt instanceof ProtocolVersionNegotiationSuccessEvent) {
            this.clientConnector.protocolVersionHandshakeSuccess();
        }

    }

    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        LOGGER.error(cause.getMessage(), cause);

        ctx.close();
    }

    public void handleAuthorization(ClientAuthorizationProvider authorizationProvider, Channel session) {
        LOGGER.debug("[{}] Calling authorize on authorization provider", this.clientSession.getTransportName());
        authorizationProvider.authorize(new NettyIoSessionWrapperAdapter(session) {
            public ChannelFuture write(Object message) {
                return ClientProtocolHandler.this.writeMessage(this.channel, (BinaryProtocolMessage)message);
            }
        });
    }

    private void processAuthorizationMessage(ChannelHandlerContext ctx, BinaryProtocolMessage requestMessage) {
        LOGGER.debug("[{}] Sending message [{}] to authorization provider", this.clientSession.getTransportName(), requestMessage);
        this.clientSession.getAuthorizationProvider().messageReceived(new NettyIoSessionWrapperAdapter(ctx.channel()) {
            public ChannelFuture write(Object message) {
                return ClientProtocolHandler.this.writeMessage(this.channel, (BinaryProtocolMessage)message);
            }
        }, (ProtocolMessage)requestMessage);
    }

    void fireAuthorizedEvent(final ClientListener clientListener, final ITransportClient transportClient) {
        AbstractEventExecutorTask task = new AbstractEventExecutorTask(this.clientSession, this) {
            public void run() {
                try {
                    clientListener.authorized(transportClient);
                } catch (Throwable var2) {
                    ClientProtocolHandler.LOGGER.error("[" + ClientProtocolHandler.this.clientSession.getTransportName() + "] " + var2.getMessage(), var2);
                    ClientProtocolHandler.this.clientConnector.disconnect(new ExtendedClientDisconnectReason(DisconnectReason.EXCEPTION_CAUGHT, (DisconnectHint)null, "Exception caught while authorized called " + var2.getMessage(), var2));
                }

            }

            public Object getOrderKey() {
                return "";
            }
        };
        task.executeInExecutor(this.authEventExecutor);
    }

    void fireDisconnectedEvent(final ClientListener clientListener, final ITransportClient transportClient, final DisconnectedEvent disconnectedEvent) {
        AbstractEventExecutorTask task = new AbstractEventExecutorTask(this.clientSession, this) {
            public void run() {
                try {
                    ClientProtocolHandler.this.terminateStreams();
                    clientListener.disconnected(transportClient, disconnectedEvent);
                } catch (Throwable var2) {
                    ClientProtocolHandler.LOGGER.error("[" + ClientProtocolHandler.this.clientSession.getTransportName() + "] " + var2.getMessage(), var2);
                    ClientProtocolHandler.this.clientConnector.disconnect(new ExtendedClientDisconnectReason(DisconnectReason.EXCEPTION_CAUGHT, (DisconnectHint)null, "Exception caught while disconnected called " + var2.getMessage(), var2));
                }

            }

            public Object getOrderKey() {
                return "";
            }
        };
        task.executeInExecutor(this.authEventExecutor);
    }

    public ChannelFuture writeMessage(Channel channel, BinaryProtocolMessage responseMessage) {
        int[] messagesCounter = (int[])this.sentMessagesCounterThreadLocal.get();
        ++messagesCounter[0];
        boolean notWritable = false;
        if (!channel.isWritable()) {
            notWritable = true;
        }

        ChannelFuture channelFuture = channel.writeAndFlush(responseMessage);
        ChannelAttachment ca = (ChannelAttachment)channel.attr(ChannelAttachment.CHANNEL_ATTACHMENT_ATTRIBUTE_KEY).get();
        channelFuture.addListener(ca.getWriteIoListener());
        if (notWritable) {
            if (this.clientSession.getPrimaryConnectionPingTimeout() > 0L) {
                ChannelWriteTimeoutChecker channelWriteTimeoutChecker = new ChannelWriteTimeoutChecker(this.clientSession, channelFuture);
                ScheduledFuture<?> scheduledFuture = this.clientSession.getScheduledExecutorService().schedule(channelWriteTimeoutChecker, this.clientSession.getPrimaryConnectionPingTimeout(), TimeUnit.MILLISECONDS);
                channelWriteTimeoutChecker.setScheduledFuture(scheduledFuture);
                channelFuture.addListener(channelWriteTimeoutChecker);
            }

            if (this.clientSession.getSendCompletionErrorDelay() > 0L) {
                this.addErrorTimeoutSendingCheck(channelFuture);
            }
        } else if (this.clientSession.getSendCompletionErrorDelay() > 0L && this.clientSession.getSendCompletionDelayCheckEveryNTimesError() > 0 && messagesCounter[0] % this.clientSession.getSendCompletionDelayCheckEveryNTimesError() == 0) {
            this.addErrorTimeoutSendingCheck(channelFuture);
        } else if (this.clientSession.getSendCompletionWarningDelay() > 0L && this.clientSession.getSendCompletionDelayCheckEveryNTimesWarning() > 0 && messagesCounter[0] % this.clientSession.getSendCompletionDelayCheckEveryNTimesWarning() == 0) {
            this.addWarningTimeoutSendingCheck(channelFuture);
        }

        return channelFuture;
    }

    private void addWarningTimeoutSendingCheck(final ChannelFuture channelFuture) {
        final long procStartTime = System.currentTimeMillis();
        this.clientSession.getScheduledExecutorService().schedule(new Runnable() {
            public void run() {
                if (!channelFuture.isDone()) {
                    long nanoTime = System.nanoTime();
                    long lastSendingWarningTimeLocal = ClientProtocolHandler.this.lastSendingWarningTime.get();
                    if (lastSendingWarningTimeLocal + 5000000000L < nanoTime && ClientProtocolHandler.this.lastSendingWarningTime.compareAndSet(lastSendingWarningTimeLocal, nanoTime)) {
                        ClientProtocolHandler.LOGGER.warn("[{}] Message sending takes too long time to complete: {}m and is still waiting it's turn. Timeout time: {}ms, possible network problem", new Object[]{ClientProtocolHandler.this.clientSession.getTransportName(), System.currentTimeMillis() - procStartTime, ClientProtocolHandler.this.clientSession.getSendCompletionWarningDelay()});
                        channelFuture.addListener(new ChannelFutureListener() {
                            public void operationComplete(ChannelFuture future) throws Exception {
                                if (future.isSuccess()) {
                                    if (ClientProtocolHandler.this.clientSession.getSendCompletionErrorDelay() > 0L && System.currentTimeMillis() - procStartTime >= ClientProtocolHandler.this.clientSession.getSendCompletionErrorDelay()) {
                                        ClientProtocolHandler.LOGGER.error("[{}] Message sending took {}ms, critical timeout time {}ms, possible network problem", new Object[]{ClientProtocolHandler.this.clientSession.getTransportName(), System.currentTimeMillis() - procStartTime, ClientProtocolHandler.this.clientSession.getSendCompletionErrorDelay()});
                                    } else {
                                        ClientProtocolHandler.LOGGER.warn("[{}] Message sending took {}ms, timeout time {}ms, possible network problem", new Object[]{ClientProtocolHandler.this.clientSession.getTransportName(), System.currentTimeMillis() - procStartTime, ClientProtocolHandler.this.clientSession.getSendCompletionWarningDelay()});
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
                    long nanoTime = System.nanoTime();
                    long lastSendingErrorTimeLocal = ClientProtocolHandler.this.lastSendingErrorTime.get();
                    if (lastSendingErrorTimeLocal + 1000000000L < nanoTime && ClientProtocolHandler.this.lastSendingErrorTime.compareAndSet(lastSendingErrorTimeLocal, nanoTime)) {
                        ClientProtocolHandler.LOGGER.error("[{}] Message was not sent in timeout time [{}] and is still waiting it's turn, CRITICAL SEND TIME: {}ms, possible network problem", new Object[]{ClientProtocolHandler.this.clientSession.getTransportName(), ClientProtocolHandler.this.clientSession.getSendCompletionErrorDelay(), System.currentTimeMillis() - procStartTime});
                        channelFuture.addListener(new ChannelFutureListener() {
                            public void operationComplete(ChannelFuture future) throws Exception {
                                if (future.isSuccess()) {
                                    ClientProtocolHandler.LOGGER.error("[{}] Message sending took {}ms, critical timeout time {}ms, possible network problem", new Object[]{ClientProtocolHandler.this.clientSession.getTransportName(), System.currentTimeMillis() - procStartTime, ClientProtocolHandler.this.clientSession.getSendCompletionErrorDelay()});
                                }

                            }
                        });
                    }
                }

            }
        }, this.clientSession.getSendCompletionErrorDelay(), TimeUnit.MILLISECONDS);
    }

    protected void processControlRequest(ChannelHandlerContext ctx, BinaryProtocolMessage msg, ChannelAttachment attachment) {
        if (msg instanceof ProtocolMessage) {
            ProtocolMessage requestMessage = (ProtocolMessage)msg;
            if (requestMessage instanceof HeartbeatRequestMessage) {
                try {
                    HeartbeatOkResponseMessage okResponseMessage = new HeartbeatOkResponseMessage();
                    okResponseMessage.setRequestTime(((HeartbeatRequestMessage)requestMessage).getRequestTime());
                    okResponseMessage.setReceiveTime(System.currentTimeMillis());
                    okResponseMessage.setSynchRequestId(requestMessage.getSynchRequestId());
                    PingManager pm = this.clientSession.getPingManager(attachment.isPrimaryConnection());
                    Double systemCpuLoad = this.sendCpuInfoToServer ? pm.getSystemCpuLoad() : null;
                    Double processCpuLoad = this.sendCpuInfoToServer ? pm.getProcessCpuLoad() : null;
                    okResponseMessage.setProcessCpuLoad(processCpuLoad);
                    okResponseMessage.setSystemCpuLoad(systemCpuLoad);
                    okResponseMessage.setAvailableProcessors(pm.getAvailableProcessors());
                    PingStats generalStats = pm.getGeneralStats();
                    if (generalStats != null) {
                        okResponseMessage.setSocketWriteInterval(generalStats.getInitiatorSocketWriteInterval().getRoundedLast());
                    } else {
                        generalStats = this.clientSession.getPingManager(attachment.isPrimaryConnection()).getGeneralStats();
                        if (generalStats != null) {
                            okResponseMessage.setSocketWriteInterval(generalStats.getInitiatorSocketWriteInterval().getRoundedLast());
                        }
                    }

                    if (!this.clientSession.isTerminating()) {
                        this.writeMessage(ctx.channel(), okResponseMessage);
                    }
                } catch (Throwable var10) {
                    LOGGER.error("[" + this.clientSession.getTransportName() + "] " + var10.getMessage(), var10);
                    ErrorResponseMessage errorMessage = new ErrorResponseMessage("Error occurred while processing the message [" + requestMessage + "]. Error message: [" + var10.getClass().getName() + ":" + var10.getMessage() + "]");
                    errorMessage.setSynchRequestId(requestMessage.getSynchRequestId());
                    if (!this.clientSession.isTerminating()) {
                        this.writeMessage(ctx.channel(), errorMessage);
                    }
                }
            } else if (requestMessage instanceof DisconnectRequestMessage) {
                DisconnectRequestMessage disconnectRequestMessage = (DisconnectRequestMessage)requestMessage;
                this.clientConnector.setDisconnectReason(new ExtendedClientDisconnectReason(disconnectRequestMessage.getReason() == null ? DisconnectReason.SERVER_APP_REQUEST : disconnectRequestMessage.getReason(), disconnectRequestMessage.getHint(), "Disconnect request received", (Throwable)null));
            } else if (requestMessage instanceof PrimarySocketAuthAcceptorMessage) {
                this.clientConnector.setPrimarySocketAuthAcceptorMessage((PrimarySocketAuthAcceptorMessage)requestMessage);
            } else if (requestMessage instanceof ChildSocketAuthAcceptorMessage) {
                this.clientConnector.setChildSocketAuthAcceptorMessage((ChildSocketAuthAcceptorMessage)requestMessage);
            } else if (requestMessage instanceof HeartbeatOkResponseMessage) {
                this.processSyncResponse(requestMessage);
            } else if (requestMessage instanceof JSonSerializableWrapper) {
                this.processSerializableRequest(ctx, (JSonSerializableWrapper)requestMessage);
            } else if (msg instanceof BinaryPartMessage) {
                BinaryPartMessage binaryPart = (BinaryPartMessage)msg;
                this.streamPartReceived(ctx, attachment, binaryPart);
            } else if (msg instanceof StreamHeaderMessage) {
                StreamHeaderMessage streamHeader = (StreamHeaderMessage)msg;
                this.createStream(ctx, attachment, streamHeader);
            } else if (msg instanceof StreamingStatus) {
                StreamingStatus status = (StreamingStatus)msg;
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

    }

    private void streamPartReceived(final ChannelHandlerContext ctx, ChannelAttachment attachment, final BinaryPartMessage binaryPart) {
        this.streamProcessingExecutor.submit(new Runnable() {
            public void run() {
                BlockingBinaryStream stream = ClientProtocolHandler.this.getStream(binaryPart.getStreamId());
                if (stream == null) {
                    StreamingStatus ss = new StreamingStatus();
                    ss.setStreamId(binaryPart.getStreamId());
                    ss.setState(StreamState.STATE_ERROR);
                    ClientProtocolHandler.this.writeMessage(ctx.channel(), ss);
                } else {
                    boolean terminated = false;

                    try {
                        stream.binaryPartReceived(binaryPart);
                    } catch (IOException var6) {
                        ClientProtocolHandler.LOGGER.error(var6.getMessage(), var6);

                        terminated = true;
                        stream.ioTerminate(var6.getMessage());
                    }

                    if (binaryPart.isEof() || terminated) {
                        synchronized(ClientProtocolHandler.this.streams) {
                            ClientProtocolHandler.this.streams.remove(binaryPart.getStreamId());
                        }
                    }
                }

            }
        });
    }

    private void createStream(final ChannelHandlerContext ctx, ChannelAttachment attachment, final StreamHeaderMessage stream) {
        this.streamProcessingExecutor.submit(new Runnable() {
            public void run() {
                StreamListener streamListener = ClientProtocolHandler.this.clientSession.getStreamListener();
                if (streamListener != null) {
                    BlockingBinaryStream bbs = new BlockingBinaryStream(stream.getStreamId(), ctx, ClientProtocolHandler.this.clientSession.getStreamBufferSize(), ClientProtocolHandler.this.clientSession.getStreamChunkProcessingTimeout());
                    synchronized(ClientProtocolHandler.this.streams) {
                        ClientProtocolHandler.this.streams.put(stream.getStreamId(), bbs);
                    }

                    streamListener.handleStream(bbs);
                }
            }
        });
    }

    private void streamStatusReceived(final StreamingStatus status) {
        this.streamProcessingExecutor.submit(new Runnable() {
            public void run() {
                BlockingBinaryStream bbs = ClientProtocolHandler.this.getStream(status.getStreamId());
                if (bbs != null) {
                    ClientProtocolHandler.this.safeTerminate(bbs, "Server error " + status.getState());
                    synchronized(ClientProtocolHandler.this.streams) {
                        ClientProtocolHandler.this.streams.remove(bbs.getStreamId());
                    }
                }

            }
        });
    }

    private void terminateStreams() {
        List<String> st = new ArrayList();
        Map var2 = this.streams;
        synchronized(this.streams) {
            st.addAll(this.streams.keySet());
            Iterator i$ = st.iterator();

            while(i$.hasNext()) {
                String key = (String)i$.next();
                BlockingBinaryStream bbs = (BlockingBinaryStream)this.streams.remove(key);
                this.safeTerminate(bbs, "Connection error");
            }

        }
    }

    private void safeTerminate(BlockingBinaryStream bbs, String reason) {
        if (bbs != null) {
            try {
                bbs.ioTerminate(reason);
            } finally {
                try {
                    bbs.close();
                } catch (IOException var9) {
                    LOGGER.error("Failed to close stream " + bbs.getStreamId(), var9);

                }

            }

        }
    }

    protected boolean processSyncResponse(final ProtocolMessage message) {
        final RequestMessageTransportListenableFuture synchRequestFuture = this.clientSession.getSyncRequestFuture(message.getSynchRequestId());
        boolean result;
        if (synchRequestFuture != null) {
            if (message instanceof RequestInProcessMessage) {
                result = true;
                synchRequestFuture.setInProcessResponseLastTime(System.currentTimeMillis());
            } else {
                this.syncRequestProcessingExecutor.submit(new OrderedRunnable() {
                    public void run() {
                        try {
                            synchRequestFuture.set(message);
                        } catch (Throwable var2) {
                            ClientProtocolHandler.LOGGER.error("Failed to process sync response for " + message, var2);

                        }

                    }

                    public Object getOrderKey() {
                        return ClientProtocolHandler.this.clientSession.getConcurrencyPolicy().getConcurrentKey(message);
                    }
                });
                result = !this.clientSession.isDuplicateSyncMessagesToClientListeners();
            }
        } else {
            result = false;
        }

        return result;
    }

    private void processSerializableRequest(final ChannelHandlerContext ctx, final JSonSerializableWrapper jSonSerializableWrapper) {
        Serializable data = jSonSerializableWrapper.getData();
        if (data != null) {
            if (data instanceof InvocationResult) {
                InvocationResult invocationResult = (InvocationResult)data;
                this.clientSession.getRemoteCallSupport().invocationResultReceived(invocationResult);
            } else if (data instanceof InvocationRequest) {
                final InvocationRequest invocationRequest = (InvocationRequest)data;
                ClientProtocolHandler.EventExecutorChannelTask task = new ClientProtocolHandler.EventExecutorChannelTask(ctx, this.clientSession, invocationRequest, 0L) {
                    public void run() {
                        try {
                            JSonSerializableWrapper responseMessage = new JSonSerializableWrapper();
                            Object impl = ClientProtocolHandler.this.clientSession.getRemoteCallSupport().getInterfaceImplementation(invocationRequest.getInterfaceClass());
                            if (impl != null) {
                                InvocationResult resultx;
                                try {
                                    Serializable invocationResult = (Serializable)TransportHelper.invokeRemoteRequest(invocationRequest, impl);
                                    resultx = new InvocationResult(invocationResult, invocationRequest.getRequestId());
                                    resultx.setState(0);
                                    responseMessage.setData(resultx);
                                } catch (Exception var5) {
                                    resultx = new InvocationResult((Serializable)null, invocationRequest.getRequestId());
                                    resultx.setState(1);
                                    if (var5 instanceof InvocationTargetException) {
                                        resultx.setThrowable(var5.getCause());
                                    } else {
                                        resultx.setThrowable(var5);
                                    }

                                    responseMessage.setData(resultx);
                                }
                            } else {
                                InvocationResult result = new InvocationResult((Serializable)null, invocationRequest.getRequestId());
                                result.setState(1);
                                result.setThrowable(new Exception("Client does not provide interface: " + invocationRequest.getInterfaceClass()));
                                responseMessage.setData(result);
                            }

                            ClientProtocolHandler.this.writeMessage(ctx.channel(), responseMessage);
                        } catch (Throwable var6) {
                            ClientProtocolHandler.LOGGER.error("[" + ClientProtocolHandler.this.clientSession.getTransportName() + "] " + var6.getMessage(), var6);
                            ClientProtocolHandler.this.clientConnector.disconnect(new ExtendedClientDisconnectReason(DisconnectReason.EXCEPTION_CAUGHT, (DisconnectHint)null, "Exception caught during processing serialized request " + var6.getMessage(), var6));
                        }

                    }

                    public Object getOrderKey() {
                        return ClientProtocolHandler.this.clientSession.getConcurrencyPolicy().getConcurrentKey(jSonSerializableWrapper);
                    }
                };
                task.executeInExecutor(this.eventExecutor, (ISessionStats)null);
            }
        }

    }

    private DroppableMessageScheduling getDroppableScheduling(ProtocolMessage message, String instrument) {
        Class<?> clazz = message.getClass();
        Map<String, DroppableMessageScheduling> lastScheduledMessagesMap = (Map)this.lastScheduledDropableMessages.get(clazz);
        if (lastScheduledMessagesMap == null) {
            lastScheduledMessagesMap = (Map)MapHelper.getAndPutIfAbsent(this.lastScheduledDropableMessages, clazz, new MapHelper.IValueCreator<Map<String, DroppableMessageScheduling>>() {
                public Map<String, DroppableMessageScheduling> create() {
                    return new ConcurrentHashMap();
                }
            });
        }

        DroppableMessageScheduling lastScheduledMessageInfo = (DroppableMessageScheduling)lastScheduledMessagesMap.get(instrument);
        if (lastScheduledMessageInfo == null) {
            lastScheduledMessageInfo = (DroppableMessageScheduling)MapHelper.getAndPutIfAbsent(lastScheduledMessagesMap, instrument, new MapHelper.IValueCreator<DroppableMessageScheduling>() {
                public DroppableMessageScheduling create() {
                    return new DroppableMessageScheduling();
                }
            });
        }

        return lastScheduledMessageInfo;
    }

    protected void fireFeedbackMessageReceived(ChannelHandlerContext ctx, ProtocolMessage message) {
        long currentDropableMessageTime = this.clientSession.isSkipDroppableMessages() ? this.checkAndRecordScheduleForDroppableMessage(message) : 0L;
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

        ClientProtocolHandler.EventExecutorChannelTask task = new ClientProtocolHandler.EventExecutorChannelTask(ctx, this.clientSession, message, currentDropableMessageTime) {
            public void run() {
                try {
                    boolean canProcessCurrentDroppable = ClientProtocolHandler.this.canProcessDroppableMessage(this.message, this.currentDropableMessageTime);
                    if (!canProcessCurrentDroppable) {
                        ClientProtocolHandler.this.clientSession.getDroppedMessageCounter().incrementAndGet();
                        if (ClientProtocolHandler.this.logSkippedDroppableMessages) {
                            ClientProtocolHandler.LOGGER.warn("Newer message already has arrived, current processing is skipped <{}>", this.message);
                        }

                        if (stats != null) {
                            long droppedTime = System.currentTimeMillis();
                            stats.messageDropped(droppedTime, droppedTime - creationTime, this.message);
                        }

                        return;
                    }
                } catch (Throwable var20) {
                    ClientProtocolHandler.LOGGER.error("[" + ClientProtocolHandler.this.clientSession.getTransportName() + "] " + var20.getMessage(), var20);

                }

                long beforeExecution = 0L;
                boolean var17 = false;

                long afterExecution;
                label197: {
                    try {
                        var17 = true;
                        if (stats != null) {
                            beforeExecution = System.currentTimeMillis();
                            stats.messageExecutionStarted(beforeExecution, beforeExecution - creationTime, this.message);
                        }

                        if (this.message instanceof MessageGroup) {
                            MessageGroup group = (MessageGroup)this.message;
                            Iterator i$x = group.getMessages().iterator();

                            while(i$x.hasNext()) {
                                ProtocolMessage m = (ProtocolMessage)i$x.next();
                                Iterator i$xx = listeners.iterator();

                                while(i$xx.hasNext()) {
                                    ClientListener clientListenerx = (ClientListener)i$xx.next();

                                    try {
                                        clientListenerx.feedbackMessageReceived(ClientProtocolHandler.this.clientSession.getTransportClient(), m);
                                    } catch (Throwable var19) {
                                        ClientProtocolHandler.LOGGER.error("[" + ClientProtocolHandler.this.clientSession.getTransportName() + "] " + var19.getMessage(), var19);

                                    }
                                }
                            }

                            var17 = false;
                            break label197;
                        }

                        Iterator i$ = listeners.iterator();

                        while(i$.hasNext()) {
                            ClientListener clientListener = (ClientListener)i$.next();

                            try {
                                clientListener.feedbackMessageReceived(ClientProtocolHandler.this.clientSession.getTransportClient(), this.message);
                            } catch (Throwable var18) {
                                ClientProtocolHandler.LOGGER.error("[" + ClientProtocolHandler.this.clientSession.getTransportName() + "] " + var18.getMessage(), var18);

                            }
                        }

                        var17 = false;
                        break label197;
                    } catch (Throwable var21) {
                        ClientProtocolHandler.LOGGER.error("[" + ClientProtocolHandler.this.clientSession.getTransportName() + "] " + var21.getMessage(), var21);

                        var17 = false;
                    } finally {
                        if (var17) {
                            if (stats != null) {
                                long afterExecutionx = System.currentTimeMillis();
                                stats.messageExecutionFinished(afterExecutionx, afterExecutionx - beforeExecution, this.message);
                            }

                        }
                    }

                    if (stats != null) {
                        afterExecution = System.currentTimeMillis();
                        stats.messageExecutionFinished(afterExecution, afterExecution - beforeExecution, this.message);
                    }

                    return;
                }

                if (stats != null) {
                    afterExecution = System.currentTimeMillis();
                    stats.messageExecutionFinished(afterExecution, afterExecution - beforeExecution, this.message);
                }

            }

            public Object getOrderKey() {
                return ClientProtocolHandler.this.clientSession.getConcurrencyPolicy().getConcurrentKey(this.message);
            }
        };
        task.executeInExecutor(this.eventExecutor, stats);
    }

    protected boolean canProcessDroppableMessage(ProtocolMessage message, long currentDropableMessageTime) {
        if (currentDropableMessageTime > 0L && message instanceof InstrumentableLowMessage) {
            InstrumentableLowMessage instrumentable = (InstrumentableLowMessage)message;
            String instrument = instrumentable.getInstrument();
            if (instrument != null) {
                DroppableMessageScheduling scheduling = this.getDroppableScheduling(message, instrument);
                long lastArrivedMessageTime = scheduling.getLastScheduledTime();
                int scheduledCount = scheduling.getScheduledCount();
                scheduling.executed();
                if (lastArrivedMessageTime - currentDropableMessageTime > this.droppableMessagesClientTTL && scheduledCount > 1) {
                    return false;
                }
            }
        }

        return true;
    }

    private long checkAndRecordScheduleForDroppableMessage(ProtocolMessage message) {
        long currentInstrumentableMessageTime;
        if (message instanceof InstrumentableLowMessage) {
            InstrumentableLowMessage instrumentable = (InstrumentableLowMessage)message;
            String instrument = instrumentable.getInstrument();
            if (instrumentable.isDropOnTimeout() && instrument != null) {
                if (message instanceof CurrencyMarket) {
                    CurrencyMarket cm = (CurrencyMarket)message;
                    currentInstrumentableMessageTime = cm.getCreationTimestamp();
                } else {
                    Long t = message.getTimestamp();
                    currentInstrumentableMessageTime = t == null ? 0L : t;
                }

                if (currentInstrumentableMessageTime > 0L) {
                    DroppableMessageScheduling scheduling = this.getDroppableScheduling(message, instrument);
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
            List<Thread> threads = new ArrayList(this.eventExecutorThreadsForLogging);
            Iterator i$ = threads.iterator();

            while(i$.hasNext()) {
                Thread thread = (Thread)i$.next();
                long before = System.currentTimeMillis();
                StackTraceElement[] stackTrace = thread.getStackTrace();
                LOGGER.warn("Transport client [{}, {}] thread's [{}] stack trace [{}], dump taking costed [{}]ms", new Object[]{this.clientSession.getTransportName(), this.clientSession.getAddress(), thread.getName(), Arrays.toString(stackTrace), System.currentTimeMillis() - before});
            }

        }
    }

    private BlockingBinaryStream getStream(String streamId) {
        BlockingBinaryStream bbs = null;
        Map var3 = this.streams;
        synchronized(this.streams) {
            bbs = (BlockingBinaryStream)this.streams.get(streamId);
            return bbs;
        }
    }

    private abstract class EventExecutorChannelTask implements OrderedRunnable {
        private final Channel channel;
        private final TransportClientSession clientSession;
        protected final ProtocolMessage message;
        protected final long currentDropableMessageTime;
        private Runnable delayedExecutionTask;
        private long procStartTime;
        private long sleepTime = 10L;

        public EventExecutorChannelTask(ChannelHandlerContext ctx, TransportClientSession clientSession, ProtocolMessage message, long currentDropableMessageTime) {
            this.channel = ctx.channel();
            this.clientSession = clientSession;
            this.message = message;
            this.currentDropableMessageTime = currentDropableMessageTime;
        }

        public void executeInExecutor(ListeningExecutorService executor, final ISessionStats stats) {
            int[] messagesCounter = (int[])ClientProtocolHandler.this.eventExecutorMessagesCounterThreadLocal.get();
            ++messagesCounter[0];
            final ListenableFuture future;
            final long procStartTime;
            if (this.clientSession.getEventExecutionErrorDelay() > 0L && this.clientSession.getEventExecutionDelayCheckEveryNTimesError() > 0 && messagesCounter[0] % this.clientSession.getEventExecutionDelayCheckEveryNTimesError() == 0) {
                future = this.executeInExecutor(executor, true);
                procStartTime = System.currentTimeMillis();
                ScheduledExecutorService scheduledExecutorService = this.clientSession.getScheduledExecutorService();
                if (!scheduledExecutorService.isShutdown() && !scheduledExecutorService.isTerminated()) {
                    scheduledExecutorService.schedule(new Runnable() {
                        public void run() {
                            if (!future.isDone()) {
                                long nanoTime = System.nanoTime();
                                long lastExecutionErrorTimeLocal = ClientProtocolHandler.this.lastExecutionErrorTime.get();
                                if (lastExecutionErrorTimeLocal + 1000000000L < nanoTime && ClientProtocolHandler.this.lastExecutionErrorTime.compareAndSet(lastExecutionErrorTimeLocal, nanoTime)) {
                                    long now = System.currentTimeMillis();
                                    long postponeInterval = now - procStartTime;
                                    ClientProtocolHandler.LOGGER.error("[{}] Event did not execute in timeout time [{}] and is still executing, CRITICAL EXECUTION WAIT TIME: {}ms, possible application problem or deadLock, message [{}]", new Object[]{EventExecutorChannelTask.this.clientSession.getTransportName(), EventExecutorChannelTask.this.clientSession.getEventExecutionErrorDelay(), postponeInterval, StrUtils.toSafeString(EventExecutorChannelTask.this.message, 100)});
                                    ClientProtocolHandler.this.checkAndLogEventPoolThreadDumps();
                                    if (stats != null) {
                                        stats.messageProcessingPostponeError(now, postponeInterval, EventExecutorChannelTask.this.message);
                                    }

                                    future.addListener(new Runnable() {
                                        public void run() {
                                            ClientProtocolHandler.LOGGER.error("[{}] Event execution took {}ms, critical timeout time {}ms, possible application problem or deadLock, message [{}]", new Object[]{EventExecutorChannelTask.this.clientSession.getTransportName(), System.currentTimeMillis() - procStartTime, EventExecutorChannelTask.this.clientSession.getEventExecutionErrorDelay(), StrUtils.toSafeString(EventExecutorChannelTask.this.message, 100)});
                                        }
                                    }, MoreExecutors.newDirectExecutorService());
                                }
                            }

                        }
                    }, this.clientSession.getEventExecutionErrorDelay(), TimeUnit.MILLISECONDS);
                }
            } else if (this.clientSession.getEventExecutionWarningDelay() > 0L && this.clientSession.getEventExecutionDelayCheckEveryNTimesWarning() > 0 && messagesCounter[0] % this.clientSession.getEventExecutionDelayCheckEveryNTimesWarning() == 0) {
                future = this.executeInExecutor(executor, true);
                procStartTime = System.currentTimeMillis();
                this.clientSession.getScheduledExecutorService().schedule(new Runnable() {
                    public void run() {
                        if (!future.isDone()) {
                            long nanoTime = System.nanoTime();
                            long lastExecutionWarningTimeLocal = ClientProtocolHandler.this.lastExecutionWarningTime.get();
                            if (lastExecutionWarningTimeLocal + 5000000000L < nanoTime && ClientProtocolHandler.this.lastExecutionWarningTime.compareAndSet(lastExecutionWarningTimeLocal, nanoTime)) {
                                long now = System.currentTimeMillis();
                                long postponeInterval = now - procStartTime;
                                ClientProtocolHandler.LOGGER.warn("[{}] Event execution takes too long, execution time: {}ms and still executing, possible application problem or deadLock, message [{}]", new Object[]{EventExecutorChannelTask.this.clientSession.getTransportName(), postponeInterval, StrUtils.toSafeString(EventExecutorChannelTask.this.message, 100)});
                                ClientProtocolHandler.this.checkAndLogEventPoolThreadDumps();
                                if (stats != null) {
                                    stats.messageProcessingPostponeError(now, postponeInterval, EventExecutorChannelTask.this.message);
                                }

                                future.addListener(new Runnable() {
                                    public void run() {
                                        if (EventExecutorChannelTask.this.clientSession.getEventExecutionErrorDelay() > 0L && System.currentTimeMillis() - procStartTime >= EventExecutorChannelTask.this.clientSession.getEventExecutionErrorDelay()) {
                                            ClientProtocolHandler.LOGGER.error("[{}] Event execution took {}ms, critical timeout time {}ms, possible application problem or deadLock, message [{}]", new Object[]{EventExecutorChannelTask.this.clientSession.getTransportName(), System.currentTimeMillis() - procStartTime, EventExecutorChannelTask.this.clientSession.getEventExecutionErrorDelay(), StrUtils.toSafeString(EventExecutorChannelTask.this.message, 100)});
                                        } else {
                                            ClientProtocolHandler.LOGGER.warn("[{}] Event execution took more time than expected, execution time: {}ms, possible application problem or deadLock, message [{}]", new Object[]{EventExecutorChannelTask.this.clientSession.getTransportName(), System.currentTimeMillis() - procStartTime, StrUtils.toSafeString(EventExecutorChannelTask.this.message, 100)});
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

        private ListenableFuture<?> executeInExecutor(final ListeningExecutorService executor, boolean withFuture) {
            final AsyncSettableFuture future;
            if (withFuture) {
                future = AsyncSettableFuture.create();
            } else {
                future = null;
            }

            try {
                if (future != null) {
                    future.setFuture(executor.submit(this));
                } else {
                    executor.execute(this);
                }
            } catch (RejectedExecutionException var5) {
                this.procStartTime = System.currentTimeMillis();
                this.clientSession.getChannelTrafficBlocker().suspend(this.channel);
                if (!ClientProtocolHandler.this.logEventPoolThreadDumpsOnLongExecution.get()) {
                    ClientProtocolHandler.LOGGER.warn("Transport client's [{}, {}] channel reading suspended on [{}]", new Object[]{this.clientSession.getTransportName(), this.clientSession.getAddress(), this.message});
                }

                this.delayedExecutionTask = new Runnable() {
                    public void run() {
                        try {
                            if (future != null) {
                                future.setFuture(executor.submit(EventExecutorChannelTask.this));
                            } else {
                                executor.execute(EventExecutorChannelTask.this);
                            }

                            EventExecutorChannelTask.this.clientSession.getChannelTrafficBlocker().resume(EventExecutorChannelTask.this.channel);
                            if (!ClientProtocolHandler.this.logEventPoolThreadDumpsOnLongExecution.get()) {
                                ClientProtocolHandler.LOGGER.warn("Transport client's [{}, {}] channel reading resumed on [{}]", new Object[]{EventExecutorChannelTask.this.clientSession.getTransportName(), EventExecutorChannelTask.this.clientSession.getAddress(), EventExecutorChannelTask.this.message});
                            }
                        } catch (RejectedExecutionException var4) {
                            long currentTime = System.currentTimeMillis();
                            if (currentTime - EventExecutorChannelTask.this.procStartTime > EventExecutorChannelTask.this.clientSession.getEventExecutionErrorDelay()) {
                                ClientProtocolHandler.LOGGER.error("[" + EventExecutorChannelTask.this.clientSession.getTransportName() + "] Event executor queue overloaded" + ", CRITICAL EXECUTION WAIT TIME: " + (currentTime - EventExecutorChannelTask.this.procStartTime) + "ms, possible application problem or deadLock, message [" + StrUtils.toSafeString(EventExecutorChannelTask.this.message, 100) + "]");
                                ClientProtocolHandler.this.checkAndLogEventPoolThreadDumps();
                                EventExecutorChannelTask.this.procStartTime = currentTime;
                                EventExecutorChannelTask.this.sleepTime = 50L;
                            }

                            EventExecutorChannelTask.this.channel.eventLoop().schedule(EventExecutorChannelTask.this.delayedExecutionTask, EventExecutorChannelTask.this.sleepTime, TimeUnit.MILLISECONDS);
                        }

                    }
                };
                this.channel.eventLoop().schedule(this.delayedExecutionTask, 5L, TimeUnit.MILLISECONDS);
            }

            return future;
        }
    }
}
