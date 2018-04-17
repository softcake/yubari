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


import org.softcake.yubari.netty.NettyIoSessionWrapperAdapter;
import org.softcake.yubari.netty.ProtocolEncoderDecoder;
import org.softcake.yubari.netty.ProtocolVersionClientNegotiatorHandler;
import org.softcake.yubari.netty.TransportClientSessionStateHandler;
import org.softcake.yubari.netty.authorization.ClientAuthorizationProvider;
import org.softcake.yubari.netty.channel.ChannelTrafficBlocker;
import org.softcake.yubari.netty.client.tasks.MessageListenableFuture;
import org.softcake.yubari.netty.client.tasks.SyncMessageTimeoutChecker;
import org.softcake.yubari.netty.mina.ClientListener;
import org.softcake.yubari.netty.mina.FeedbackEventsConcurrencyPolicy;
import org.softcake.yubari.netty.mina.ISessionStats;
import org.softcake.yubari.netty.mina.IoSessionWrapper;
import org.softcake.yubari.netty.mina.MessageSentListener;
import org.softcake.yubari.netty.mina.RequestListenableFuture;
import org.softcake.yubari.netty.mina.SecurityExceptionHandler;
import org.softcake.yubari.netty.ssl.ClientSSLContextSubscriber;
import org.softcake.yubari.netty.ssl.SSLContextFactory;
import org.softcake.yubari.netty.stream.StreamListener;

import com.dukascopy.dds4.ping.IPingListener;
import com.dukascopy.dds4.ping.PingManager;
import com.dukascopy.dds4.transport.common.protocol.binary.AbstractStaticSessionDictionary;
import com.dukascopy.dds4.transport.common.protocol.binary.BinaryProtocolMessage;
import com.dukascopy.dds4.transport.msg.system.ProtocolMessage;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import io.netty.handler.ssl.SslHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import javax.net.ssl.SSLEngine;

public class TransportClientSession {
    private static final Logger LOGGER = LoggerFactory.getLogger(TransportClientSession.class);
    private final TransportClient transportClient;
    private final InetSocketAddress address;
    private final ClientAuthorizationProvider authorizationProvider;
    private final CopyOnWriteArrayList<ClientListener> listeners;
    private final boolean useSSL;
    private final long sslHandshakeTimeout;
    private final long protocolVersionNegotiationTimeout;
    private final boolean useFeederSocket;
    private final long connectionTimeout;
    private final long authorizationTimeout;
    private final int transportPoolSize;
    private final int eventPoolSize;
    private final long eventPoolAutoCleanupInterval;
    private final int authEventPoolSize;
    private final long authEventPoolAutoCleanupInterval;
    private final int criticalAuthEventQueueSize;
    private final long primaryConnectionPingInterval;
    private final long secondaryConnectionPingInterval;
    private final long primaryConnectionPingTimeout;
    private final long secondaryConnectionPingTimeout;
    private final long secondaryConnectionReconnectAttempts;
    private final long secondaryConnectionReconnectsResetDelay;
    private final long droppableMessagesServerTTL;
    private final long droppableMessagesClientTTL;
    private final boolean logSkippedDroppableMessages;
    private final Map<ChannelOption<?>, Object> channelOptions;
    private final String transportName;
    private final long reconnectDelay;
    private final int maxMessageSizeBytes;
    private final int criticalEventQueueSize;
    private final long eventExecutionWarningDelay;
    private final long eventExecutionErrorDelay;
    private final int eventExecutionDelayCheckEveryNTimesWarning;
    private final int eventExecutionDelayCheckEveryNTimesError;
    private final long sendCompletionWarningDelay;
    private final long sendCompletionErrorDelay;
    private final int sendCompletionDelayCheckEveryNTimesWarning;
    private final int sendCompletionDelayCheckEveryNTimesError;
    private final FeedbackEventsConcurrencyPolicy concurrencyPolicy;
    private final SecurityExceptionHandler securityExceptionHandler;
    private final ChannelHandler protocolEncoderDecoder;
    private final ChannelTrafficBlocker channelTrafficBlocker;
    private final Map<Long, RequestMessageListenableFuture> syncRequests = new ConcurrentHashMap<>();
    private final ISessionStats sessionStats;
    private final IPingListener pingListener;
    private final long syncMessageTimeout;
    private final boolean duplicateSyncMessagesToClientListeners;
    private final AtomicLong droppedMessageCounter;
    private final AtomicBoolean logEventPoolThreadDumpsOnLongExecution;
    private final StreamListener streamListener;
    private final int streamChunkProcessingTimeout;
    private final int streamBufferSize;
    private final int streamProcessingPoolSize;
    private final long streamProcessingPoolAutoCleanupInterval;
    private final int criticalStreamProcessingQueueSize;
    private final boolean sendCpuInfoToServer;
    private final int syncRequestProcessingPoolSize;
    private final long syncRequestProcessingPoolAutoCleanupInterval;
    private final int criticalSyncRequestProcessingQueueSize;
    private final long terminationAwaitMaxTimeoutInMillis;
    private final long maxSubsequentPingFailedCount;
    private final TransportClientSessionStateHandler transportClientSessionStateHandler;
    private final long eventPoolTerminationTimeUnitCount;
    private final TimeUnit eventPoolTerminationTimeUnit;
    private final long authEventPoolTerminationTimeUnitCount;
    private final TimeUnit authEventPoolTerminationTimeUnit;
    private final long streamProcessingPoolTerminationTimeUnitCount;
    private final TimeUnit streamProcessingPoolTerminationTimeUnit;
    private final long syncRequestProcessingPoolTerminationTimeUnitCount;
    private final TimeUnit syncRequestProcessingPoolTerminationTimeUnit;
    private final Set<String> enabledSslProtocols;
    private boolean skipDroppableMessages;
    private Bootstrap channelBootstrap;
    private ProtocolVersionClientNegotiatorHandler protocolVersionClientNegotiatorHandler;
    private ClientProtocolHandler protocolHandler;
    private ClientConnector clientConnector;
    private ScheduledExecutorService scheduledExecutorService;
    private String serverSessionId;
    private IoSessionWrapper sessionWrapper = new NettyIoSessionWrapperAdapter() {
        public Future<Void> write(final Object message) {

            return protocolHandler.writeMessage(this.channel, (BinaryProtocolMessage) message);
        }
    };

    protected TransportClientSession(final TransportClient transportClient,
                                     final InetSocketAddress address,
                                     final ClientAuthorizationProvider authorizationProvider,
                                     final CopyOnWriteArrayList<ClientListener> listeners,
                                     final boolean useSSL,
                                     final long sslHandshakeTimeout,
                                     final long protocolVersionNegotiationTimeout,
                                     final boolean useFeederSocket,
                                     final long connectionTimeout,
                                     final long authorizationTimeout,
                                     final int transportPoolSize,
                                     final int eventPoolSize,
                                     final long eventPoolAutoCleanupInterval,
                                     final int authEventPoolSize,
                                     final long authEventPoolAutoCleanupInterval,
                                     final int criticalAuthEventQueueSize,
                                     final long primaryConnectionPingInterval,
                                     final long secondaryConnectionPingInterval,
                                     final long primaryConnectionPingTimeout,
                                     final long secondaryConnectionPingTimeout,
                                     final int secondaryConnectionReconnectAttempts,
                                     final long secondaryConnectionReconnectsResetDelay,
                                     final long droppableMessagesServerTTL,
                                     final long droppableMessagesClientTTL,
                                     final boolean skipDroppableMessages,
                                     final boolean logSkippedDroppableMessages,
                                     final Map<ChannelOption<?>, Object> channelOptions,
                                     final String transportName,
                                     final long reconnectDelay,
                                     final int maxMessageSizeBytes,
                                     final int criticalEventQueueSize,
                                     final long eventExecutionWarningDelay,
                                     final long eventExecutionErrorDelay,
                                     final int eventExecutionDelayCheckEveryNTimesWarning,
                                     final int eventExecutionDelayCheckEveryNTimesError,
                                     final long sendCompletionWarningDelay,
                                     final long sendCompletionErrorDelay,
                                     final int sendCompletionDelayCheckEveryNTimesWarning,
                                     final int sendCompletionDelayCheckEveryNTimesError,
                                     final FeedbackEventsConcurrencyPolicy concurrencyPolicy,
                                     final SecurityExceptionHandler securityExceptionHandler,
                                     final AbstractStaticSessionDictionary staticSessionDictionary,
                                     final ISessionStats sessionStats,
                                     final IPingListener pingListener,
                                     final long syncMessageTimeout,
                                     final boolean duplicateSyncMessagesToClientListeners,
                                     final AtomicLong droppedMessageCounter,
                                     final AtomicBoolean logEventPoolThreadDumpsOnLongExecution,
                                     final StreamListener streamListener,
                                     final int streamChunkProcessingTimeout,
                                     final int streamBufferSize,
                                     final int streamProcessingPoolSize,
                                     final long streamProcessingPoolAutoCleanupInterval,
                                     final int criticalStreamProcessingQueueSize,
                                     final boolean sendCpuInfoToServer,
                                     final int syncRequestProcessingPoolSize,
                                     final long syncRequestProcessingPoolAutoCleanupInterval,
                                     final int criticalSyncRequestProcessingQueueSize,
                                     final long terminationAwaitMaxTimeoutInMillis,
                                     final long maxSubsequentPingFailedCount,
                                     final TransportClientSessionStateHandler transportClientSessionStateHandler,
                                     final long eventPoolTerminationTimeUnitCount,
                                     final TimeUnit eventPoolTerminationTimeUnit,
                                     final long authEventPoolTerminationTimeUnitCount,
                                     final TimeUnit authEventPoolTerminationTimeUnit,
                                     final long streamProcessingPoolTerminationTimeUnitCount,
                                     final TimeUnit streamProcessingPoolTerminationTimeUnit,
                                     final long syncRequestProcessingPoolTerminationTimeUnitCount,
                                     final TimeUnit syncRequestProcessingPoolTerminationTimeUnit,
                                     final Set<String> enabledSslProtocols) {

        this.transportClient = transportClient;
        this.address = address;
        this.authorizationProvider = authorizationProvider;
        this.listeners = listeners;
        this.useSSL = useSSL;
        this.sslHandshakeTimeout = sslHandshakeTimeout;
        this.protocolVersionNegotiationTimeout = protocolVersionNegotiationTimeout;
        this.useFeederSocket = useFeederSocket;
        this.connectionTimeout = connectionTimeout;
        this.authorizationTimeout = authorizationTimeout;
        this.transportPoolSize = transportPoolSize;
        this.eventPoolSize = eventPoolSize;
        this.eventPoolAutoCleanupInterval = eventPoolAutoCleanupInterval;
        this.authEventPoolSize = authEventPoolSize;
        this.authEventPoolAutoCleanupInterval = authEventPoolAutoCleanupInterval;
        this.criticalAuthEventQueueSize = criticalAuthEventQueueSize;
        this.primaryConnectionPingInterval = primaryConnectionPingInterval;
        this.secondaryConnectionPingInterval = secondaryConnectionPingInterval;
        this.primaryConnectionPingTimeout = primaryConnectionPingTimeout;
        this.secondaryConnectionPingTimeout = secondaryConnectionPingTimeout;
        this.secondaryConnectionReconnectAttempts = (long) secondaryConnectionReconnectAttempts;
        this.secondaryConnectionReconnectsResetDelay = secondaryConnectionReconnectsResetDelay;
        this.droppableMessagesServerTTL = droppableMessagesServerTTL;
        this.droppableMessagesClientTTL = droppableMessagesClientTTL;
        this.skipDroppableMessages = skipDroppableMessages;
        this.logSkippedDroppableMessages = logSkippedDroppableMessages;
        this.channelOptions = channelOptions;
        this.transportName = transportName;
        this.reconnectDelay = reconnectDelay;
        this.maxMessageSizeBytes = maxMessageSizeBytes;
        this.criticalEventQueueSize = criticalEventQueueSize;
        this.eventExecutionWarningDelay = eventExecutionWarningDelay;
        this.eventExecutionErrorDelay = eventExecutionErrorDelay;
        this.eventExecutionDelayCheckEveryNTimesWarning = eventExecutionDelayCheckEveryNTimesWarning;
        this.eventExecutionDelayCheckEveryNTimesError = eventExecutionDelayCheckEveryNTimesError;
        this.sendCompletionWarningDelay = sendCompletionWarningDelay;
        this.sendCompletionErrorDelay = sendCompletionErrorDelay;
        this.sendCompletionDelayCheckEveryNTimesWarning = sendCompletionDelayCheckEveryNTimesWarning;
        this.sendCompletionDelayCheckEveryNTimesError = sendCompletionDelayCheckEveryNTimesError;
        this.concurrencyPolicy = concurrencyPolicy;
        this.securityExceptionHandler = securityExceptionHandler;
        this.protocolEncoderDecoder = new ProtocolEncoderDecoder(transportName,
                                                                 maxMessageSizeBytes,
                                                                 staticSessionDictionary);
        this.channelTrafficBlocker = new ChannelTrafficBlocker(transportName);
        this.sessionStats = sessionStats;
        this.pingListener = pingListener;
        this.syncMessageTimeout = syncMessageTimeout;
        this.duplicateSyncMessagesToClientListeners = duplicateSyncMessagesToClientListeners;
        this.droppedMessageCounter = droppedMessageCounter;
        this.logEventPoolThreadDumpsOnLongExecution = logEventPoolThreadDumpsOnLongExecution;
        this.streamListener = streamListener;
        this.streamChunkProcessingTimeout = streamChunkProcessingTimeout;
        this.streamBufferSize = streamBufferSize;
        this.streamProcessingPoolSize = streamProcessingPoolSize;
        this.streamProcessingPoolAutoCleanupInterval = streamProcessingPoolAutoCleanupInterval;
        this.criticalStreamProcessingQueueSize = criticalStreamProcessingQueueSize;
        this.sendCpuInfoToServer = sendCpuInfoToServer;
        this.syncRequestProcessingPoolSize = syncRequestProcessingPoolSize;
        this.syncRequestProcessingPoolAutoCleanupInterval = syncRequestProcessingPoolAutoCleanupInterval;
        this.criticalSyncRequestProcessingQueueSize = criticalSyncRequestProcessingQueueSize;
        this.terminationAwaitMaxTimeoutInMillis = terminationAwaitMaxTimeoutInMillis;
        this.maxSubsequentPingFailedCount = maxSubsequentPingFailedCount;
        this.transportClientSessionStateHandler = transportClientSessionStateHandler;
        this.eventPoolTerminationTimeUnitCount = eventPoolTerminationTimeUnitCount;
        this.eventPoolTerminationTimeUnit = eventPoolTerminationTimeUnit;
        this.authEventPoolTerminationTimeUnitCount = authEventPoolTerminationTimeUnitCount;
        this.authEventPoolTerminationTimeUnit = authEventPoolTerminationTimeUnit;
        this.streamProcessingPoolTerminationTimeUnitCount = streamProcessingPoolTerminationTimeUnitCount;
        this.streamProcessingPoolTerminationTimeUnit = streamProcessingPoolTerminationTimeUnit;
        this.syncRequestProcessingPoolTerminationTimeUnitCount = syncRequestProcessingPoolTerminationTimeUnitCount;
        this.syncRequestProcessingPoolTerminationTimeUnit = syncRequestProcessingPoolTerminationTimeUnit;
        this.enabledSslProtocols = enabledSslProtocols;
    }

    void init() {

        this.protocolVersionClientNegotiatorHandler = new ProtocolVersionClientNegotiatorHandler(this.transportName);
        final ThreadFactory threadFactory = new ThreadFactoryBuilder().setDaemon(true).setNameFormat(String.format(
            "[%s] SyncMessagesTimeouter",
            this.transportName)).build();

        this.scheduledExecutorService = Executors.newScheduledThreadPool(1, threadFactory);
        this.protocolHandler = new ClientProtocolHandler(this);

        final EventLoopGroup nettyEventLoopGroup = new NioEventLoopGroup(this.transportPoolSize, new ThreadFactory() {
            private final AtomicInteger counter = new AtomicInteger();

            public Thread newThread(final Runnable r) {

                return new Thread(r,
                                  String.format("[%s] NettyTransportThread - %d",
                                                transportName,
                                                this.counter.getAndIncrement()));
            }
        });
        this.channelBootstrap = new Bootstrap();
        this.channelBootstrap.group(nettyEventLoopGroup);
        this.channelBootstrap.channel(NioSocketChannel.class);
        this.channelOptions.forEach((key, value) -> this.channelBootstrap.option((ChannelOption) key, value));
        final ClientSSLContextSubscriber subscriber
            = event -> event.subscribe(e -> clientConnector.securityException(e.getCertificateChain(),
                                                                              e.getAuthenticationType(),
                                                                              e.getException()));

        this.channelBootstrap.handler(new ChannelInitializer<SocketChannel>() {
            protected void initChannel(final SocketChannel ch) throws Exception {

                final ChannelPipeline pipeline = ch.pipeline();
                if (useSSL) {
                    final Set<String> sslProtocols = enabledSslProtocols == null || enabledSslProtocols.isEmpty()
                                                     ? TransportClientBuilder.DEFAULT_SSL_PROTOCOLS
                                                     : enabledSslProtocols;
                    final SSLEngine engine = SSLContextFactory.getInstance(false, subscriber, address.getHostName())
                                                              .createSSLEngine();


                    engine.setUseClientMode(true);
                    engine.setEnabledProtocols(sslProtocols.toArray(new String[0]));
                    engine.setEnabledCipherSuites(cleanUpCipherSuites(engine.getSupportedCipherSuites()));
                    pipeline.addLast("ssl", new SslHandler(engine));


                }

                pipeline.addLast("protocol_version_negotiator", protocolVersionClientNegotiatorHandler);
                pipeline.addLast("frame_handler",
                                 new LengthFieldBasedFrameDecoder(maxMessageSizeBytes, 0, 4, 0, 4, true));
                pipeline.addLast("frame_encoder", new LengthFieldPrepender(4, false));
                pipeline.addLast("protocol_encoder_decoder", protocolEncoderDecoder);
                pipeline.addLast("traffic_blocker", channelTrafficBlocker);
                pipeline.addLast("handler", protocolHandler);
            }
        });

        this.clientConnector = new ClientConnector(this.address,
                                                   this.channelBootstrap,
                                                   this,
                                                   this.protocolHandler,
                                                   this.pingListener);
        this.protocolHandler.setClientConnector(this.clientConnector);
        this.clientConnector.start();
    }

    private String[] cleanUpCipherSuites(final String[] enabledCipherSuites) {

        return Arrays.stream(enabledCipherSuites).filter(cipher -> !cipher.toUpperCase().contains("EXPORT")
                                                                   && !cipher.toUpperCase().contains("NULL")
                                                                   && !cipher.toUpperCase().contains("ANON")
                                                                   && !cipher.toUpperCase().contains("_DES_")
                                                                   && !cipher.toUpperCase().contains("MD5")).toArray(
            String[]::new);


    }


    public void connect() {

        this.clientConnector.connect();
    }

    void disconnect() {

        this.clientConnector.disconnect();
    }

    public void disconnected() {

        this.transportClient.disconnected();
    }

    public void terminate() {

        if (this.transportClientSessionStateHandler != null) {
            try {
                this.transportClientSessionStateHandler.beforeTerminate();
            } catch (final Throwable var2) {
                LOGGER.warn("[{}] terminate handler error", this.transportName, var2);
            }
        }

        LOGGER.debug("[{}] Terminating client session", this.transportName);
        if (this.clientConnector != null) {
            this.clientConnector.setTerminating(this.terminationAwaitMaxTimeoutInMillis);
        }

        if (this.channelBootstrap != null) {
            final EventLoopGroup group = this.channelBootstrap.group();
            if (group != null) {
                group.shutdownGracefully();
            }
        }

        if (this.protocolHandler != null) {
            this.protocolHandler.terminate();
        }

        if (this.scheduledExecutorService != null) {
            this.scheduledExecutorService.shutdownNow();
        }

        this.syncRequests.clear();
    }

    boolean sendMessageNaive(final ProtocolMessage message) {

        if (this.isOnline()) {
            this.protocolHandler.writeMessage(this.clientConnector.getPrimaryChannel(), message);
            return true;
        } else {
            LOGGER.error("[{}] TransportClient not connected, message: {}", this.transportName, message);
            return false;
        }
    }

    ProtocolMessage sendRequest(final ProtocolMessage message, final long timeout, final TimeUnit timeoutUnits)
        throws InterruptedException, TimeoutException, ConnectException, ExecutionException {

        if (!this.isOnline()) {
            throw new ConnectException(String.format("[%s] TransportClient not connected, message: %s",
                                                     this.transportName,
                                                     message.toString(400)));
        }

        final Long syncRequestId = this.transportClient.getNextId();
        message.setSynchRequestId(syncRequestId);
        final RequestMessageListenableFuture task = new RequestMessageListenableFuture(this.transportName,
                                                                                       syncRequestId,
                                                                                       this.syncRequests,
                                                                                       message);
        this.syncRequests.put(syncRequestId, task);
        final ChannelFuture future = this.protocolHandler.writeMessage(this.clientConnector.getPrimaryChannel(),
                                                                       message);
        task.setChannelFuture(future);
        task.scheduleTimeoutCheck(new SyncMessageTimeoutChecker(this.scheduledExecutorService,
                                                                task,
                                                                timeoutUnits.toMillis(timeout),
                                                                true));


        return task.get(timeout, timeoutUnits);


    }

    <V> ListenableFuture<V> sendMessageAsync(final ProtocolMessage message) {

        if (this.isOnline()) {
            return new MessageListenableFuture<>(this.protocolHandler.writeMessage(this.clientConnector
                                                                                       .getPrimaryChannel(),
                                                                                   message));
        } else {
            return Futures.immediateFailedFuture(new ConnectException(String.format(
                "[%s] TransportClient not connected, message: %s",
                this.transportName,
                message.toString(400))));
        }
    }

    RequestListenableFuture sendRequestAsync(final ProtocolMessage message) {

        return this.sendRequestAsync(message,
                                     this.clientConnector.getPrimaryChannel(),
                                     this.syncMessageTimeout,
                                     false,
                                     null);
    }

    RequestListenableFuture sendRequestAsync(final ProtocolMessage message,
                                             final Channel channel,
                                             final long timeout,
                                             final boolean doNotRestartTimerOnInProcessResponse,
                                             final MessageSentListener messageSentListener) {

        if (this.isOnline()) {
            final Long syncRequestId = this.transportClient.getNextId();
            message.setSynchRequestId(syncRequestId);
            final RequestMessageListenableFuture task = new RequestMessageListenableFuture(this.transportName,
                                                                                           syncRequestId,
                                                                                           this.syncRequests,
                                                                                           message);
            this.syncRequests.put(syncRequestId, task);
            final ChannelFuture channelFuture = this.protocolHandler.writeMessage(channel, message);
            if (messageSentListener != null) {
                channelFuture.addListener(future -> {

                    if (channelFuture.isSuccess()) {
                        messageSentListener.messageSent(message);
                    } else if (channelFuture.isDone() && channelFuture.cause() != null) {
                        final Throwable cause = channelFuture.cause();
                        LOGGER.error("[{}] Request send failed because of {}: {}",
                                     this.getTransportName(),
                                     cause.getClass().getSimpleName(),
                                     cause.getMessage());
                    } else {
                        LOGGER.error("[{}] Request send failed", this.getTransportName());
                    }

                });
            }

            task.setChannelFuture(channelFuture);
            task.scheduleTimeoutCheck(new SyncMessageTimeoutChecker(this.scheduledExecutorService,
                                                                    task,
                                                                    timeout,
                                                                    doNotRestartTimerOnInProcessResponse));
            return task;
        } else {
            return this.transportClient.createFailedFuture(message);
        }
    }


    public IoSessionWrapper getIoSessionWrapper() {

        ((NettyIoSessionWrapperAdapter) this.sessionWrapper).setChannel(this.clientConnector.getPrimaryChannel());
        return this.sessionWrapper;
    }


    RequestMessageListenableFuture getSyncRequestFuture(final Long syncId) {

        return this.syncRequests.get(syncId);
    }

    boolean isOnline() {

        return this.clientConnector.isOnline();
    }

    public boolean isTerminating() {

        return this.clientConnector.isTerminating();
    }

    boolean isConnecting() {

        return this.clientConnector.isConnecting();
    }

    void tickReceived() {

        this.transportClient.tickReceived();
    }

    boolean isDebugMode() {

        return this.transportClient.isDebugMode();
    }

    TransportClient getTransportClient() {


        return this.transportClient;
    }

    ClientAuthorizationProvider getAuthorizationProvider() {

        return this.authorizationProvider;
    }

    CopyOnWriteArrayList<ClientListener> getListeners() {

        return this.listeners;
    }

    long getConnectionTimeout() {

        return this.connectionTimeout;
    }

    long getAuthorizationTimeout() {

        return this.authorizationTimeout;
    }

    int getEventPoolSize() {

        return this.eventPoolSize;
    }

    long getEventPoolAutoCleanupInterval() {

        return this.eventPoolAutoCleanupInterval;
    }

    long getPrimaryConnectionPingInterval() {

        return this.primaryConnectionPingInterval;
    }

    long getSecondaryConnectionPingInterval() {

        return this.secondaryConnectionPingInterval;
    }

    long getPrimaryConnectionPingTimeout() {

        return this.primaryConnectionPingTimeout;
    }

    long getSecondaryConnectionPingTimeout() {

        return this.secondaryConnectionPingTimeout;
    }

    long getReconnectDelay() {

        return this.reconnectDelay;
    }

    boolean isUseFeederSocket() {

        return this.useFeederSocket;
    }

    long getSecondaryConnectionReconnectAttempts() {

        return this.secondaryConnectionReconnectAttempts;
    }

    long getSecondaryConnectionReconnectsResetDelay() {

        return this.secondaryConnectionReconnectsResetDelay;
    }

    long getDroppableMessagesServerTTL() {

        return this.droppableMessagesServerTTL;
    }

    public String getTransportName() {

        return this.transportName;
    }

    int getCriticalEventQueueSize() {

        return this.criticalEventQueueSize;
    }

    int getCriticalAuthEventQueueSize() {

        return this.criticalAuthEventQueueSize;
    }

    public long getEventExecutionWarningDelay() {

        return this.eventExecutionWarningDelay;
    }

    public long getEventExecutionErrorDelay() {

        return this.eventExecutionErrorDelay;
    }

    public int getEventExecutionDelayCheckEveryNTimesWarning() {

        return this.eventExecutionDelayCheckEveryNTimesWarning;
    }

    public int getEventExecutionDelayCheckEveryNTimesError() {

        return this.eventExecutionDelayCheckEveryNTimesError;
    }

    public long getSendCompletionWarningDelay() {

        return this.sendCompletionWarningDelay;
    }

    public long getSendCompletionErrorDelay() {

        return this.sendCompletionErrorDelay;
    }

    int getSendCompletionDelayCheckEveryNTimesWarning() {

        return this.sendCompletionDelayCheckEveryNTimesWarning;
    }

    int getSendCompletionDelayCheckEveryNTimesError() {

        return this.sendCompletionDelayCheckEveryNTimesError;
    }

    public FeedbackEventsConcurrencyPolicy getConcurrencyPolicy() {

        return this.concurrencyPolicy;
    }

    String getServerSessionId() {

        return this.serverSessionId;
    }

    void setServerSessionId(final String serverSessionId) {

        this.serverSessionId = serverSessionId;
    }

    SecurityExceptionHandler getSecurityExceptionHandler() {

        return this.securityExceptionHandler;
    }

    public ClientProtocolHandler getProtocolHandler() {

        return this.protocolHandler;
    }

    public ClientConnector getClientConnector() {

        return this.clientConnector;
    }

    boolean isUseSSL() {

        return this.useSSL;
    }

    long getSSLHandshakeTimeout() {

        return this.sslHandshakeTimeout;
    }

    long getProtocolVersionNegotiationTimeout() {

        return this.protocolVersionNegotiationTimeout;
    }

    public ScheduledExecutorService getScheduledExecutorService() {

        return this.scheduledExecutorService;
    }

    public ChannelTrafficBlocker getChannelTrafficBlocker() {

        return this.channelTrafficBlocker;
    }

    public PingManager getPingManager(final boolean isPrimarySession) {

        return this.transportClient.getPingManager(isPrimarySession);
    }

    public ISessionStats getSessionStats() {

        return this.sessionStats;
    }

    boolean isDuplicateSyncMessagesToClientListeners() {

        return this.duplicateSyncMessagesToClientListeners;
    }

    public long getDroppableMessagesClientTTL() {

        return this.droppableMessagesClientTTL;
    }

    public boolean isSkipDroppableMessages() {

        return this.skipDroppableMessages;
    }

    void setSkipDroppableMessages(final boolean skipDroppableMessages) {

        this.skipDroppableMessages = skipDroppableMessages;
    }

    public AtomicLong getDroppedMessageCounter() {

        return this.droppedMessageCounter;
    }

    boolean isLogSkippedDroppableMessages() {

        return this.logSkippedDroppableMessages;
    }

    int getAuthEventPoolSize() {

        return this.authEventPoolSize;
    }

    long getAuthEventPoolAutoCleanupInterval() {

        return this.authEventPoolAutoCleanupInterval;
    }

    public InetSocketAddress getAddress() {

        return this.address;
    }

    public AtomicBoolean getLogEventPoolThreadDumpsOnLongExecution() {

        return this.logEventPoolThreadDumpsOnLongExecution;
    }

    public StreamListener getStreamListener() {

        return this.streamListener;
    }

    public int getStreamChunkProcessingTimeout() {

        return this.streamChunkProcessingTimeout;
    }

    public int getStreamBufferSize() {

        return this.streamBufferSize;
    }

    int getStreamProcessingPoolSize() {

        return this.streamProcessingPoolSize;
    }

    long getStreamProcessingPoolAutoCleanupInterval() {

        return this.streamProcessingPoolAutoCleanupInterval;
    }

    int getCriticalStreamProcessingQueueSize() {

        return this.criticalStreamProcessingQueueSize;
    }

    public boolean isSendCpuInfoToServer() {

        return this.sendCpuInfoToServer;
    }

    int getSyncRequestProcessingPoolSize() {

        return this.syncRequestProcessingPoolSize;
    }

    long getSyncRequestProcessingPoolAutoCleanupInterval() {

        return this.syncRequestProcessingPoolAutoCleanupInterval;
    }

    int getCriticalSyncRequestProcessingQueueSize() {

        return this.criticalSyncRequestProcessingQueueSize;
    }

    long getMaxSubsequentPingFailedCount() {

        return this.maxSubsequentPingFailedCount;
    }

    long getEventPoolTerminationTimeUnitCount() {

        return this.eventPoolTerminationTimeUnitCount;
    }

    TimeUnit getEventPoolTerminationTimeUnit() {

        return this.eventPoolTerminationTimeUnit;
    }

    long getAuthEventPoolTerminationTimeUnitCount() {

        return this.authEventPoolTerminationTimeUnitCount;
    }

    TimeUnit getAuthEventPoolTerminationTimeUnit() {

        return this.authEventPoolTerminationTimeUnit;
    }

    long getStreamProcessingPoolTerminationTimeUnitCount() {

        return this.streamProcessingPoolTerminationTimeUnitCount;
    }

    TimeUnit getStreamProcessingPoolTerminationTimeUnit() {

        return this.streamProcessingPoolTerminationTimeUnit;
    }

    long getSyncRequestProcessingPoolTerminationTimeUnitCount() {

        return this.syncRequestProcessingPoolTerminationTimeUnitCount;
    }

    TimeUnit getSyncRequestProcessingPoolTerminationTimeUnit() {

        return this.syncRequestProcessingPoolTerminationTimeUnit;
    }
}
