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


import org.softcake.yubari.netty.stream.StreamListener;

import com.dukascopy.dds4.ping.IPingListener;
import com.dukascopy.dds4.ping.PingManager;
import com.dukascopy.dds4.transport.FeedbackEventsConcurrencyPolicy;
import com.dukascopy.dds4.transport.RequestListenableFuture;
import com.dukascopy.dds4.transport.client.SecurityExceptionHandler;
import com.dukascopy.dds4.transport.common.mina.ClientListener;
import com.dukascopy.dds4.transport.common.mina.MessageSentListener;
import com.dukascopy.dds4.transport.common.mina.RemoteCallSupport;
import com.dukascopy.dds4.transport.common.mina.ssl.ClientSSLContextListener;
import com.dukascopy.dds4.transport.common.mina.ssl.SSLContextFactory;
import com.dukascopy.dds4.transport.common.protocol.binary.AbstractStaticSessionDictionary;
import com.dukascopy.dds4.transport.common.protocol.binary.BinaryProtocolMessage;
import com.dukascopy.dds4.transport.common.protocol.mina.ISessionStats;
import com.dukascopy.dds4.transport.common.protocol.mina.IoSessionWrapper;
import com.dukascopy.dds4.transport.msg.system.OkResponseMessage;
import com.dukascopy.dds4.transport.msg.system.ProtocolMessage;
import com.dukascopy.dds4.transport.netty.ChannelTrafficBlocker;
import com.dukascopy.dds4.transport.netty.MessageListenableFuture;
import com.dukascopy.dds4.transport.netty.NettyIoSessionWrapperAdapter;
import com.dukascopy.dds4.transport.netty.ProtocolEncoderDecoder;
import com.dukascopy.dds4.transport.netty.RequestMessageTransportListenableFuture;
import com.dukascopy.dds4.transport.netty.SyncMessageTimeoutChecker;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
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
import io.netty.util.concurrent.GenericFutureListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
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

class TransportClientSession implements ClientSSLContextListener {
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
    private final Map<Long, RequestMessageTransportListenableFuture> syncRequests = new ConcurrentHashMap();
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
    private ProtocolVersionClientNegotiatorHandler
        protocolVersionClientNegotiatorHandler
        = new ProtocolVersionClientNegotiatorHandler();
    private ClientProtocolHandler protocolHandler;
    private ClientConnector clientConnector;
    private ScheduledExecutorService scheduledExecutorService;
    private String serverSessionId;
    private IoSessionWrapper sessionWrapper = new NettyIoSessionWrapperAdapter() {
        public Future<Void> write(final Object message) {

            return TransportClientSession.this.protocolHandler.writeMessage(this.channel,
                                                                            (BinaryProtocolMessage) message);
        }
    };

    TransportClientSession(final TransportClient transportClient,
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

        this.scheduledExecutorService = Executors.newScheduledThreadPool(1,
                                                                         (new ThreadFactoryBuilder())
                                                                             .setDaemon(true)
                                                                             .setNameFormat("["
                                                                                            + this.transportName
                                                                                            + "] "
                                                                                            + "SyncMessagesTimeouter")
                                                                             .build());
        this.protocolHandler = new ClientProtocolHandler(this);
        final EventLoopGroup nettyEventLoopGroup = new NioEventLoopGroup(this.transportPoolSize, new ThreadFactory() {
            private AtomicInteger counter = new AtomicInteger();

            public Thread newThread(final Runnable r) {

                return new Thread(r,
                                  "["
                                  + TransportClientSession.this.transportName
                                  + "] NettyTransportThread - "
                                  + this.counter.getAndIncrement());
            }
        });
        this.channelBootstrap = new Bootstrap();
        this.channelBootstrap.group(nettyEventLoopGroup);
        this.channelBootstrap.channel(NioSocketChannel.class);


        final Iterator i$ = this.channelOptions.entrySet().iterator();

        while (i$.hasNext()) {
            final Entry<ChannelOption<?>, Object> entry = (Entry) i$.next();
            this.channelBootstrap.option((ChannelOption) entry.getKey(), entry.getValue());
        }

        this.channelBootstrap.handler(new ChannelInitializer<SocketChannel>() {
            protected void initChannel(final SocketChannel ch) throws Exception {

                final ChannelPipeline pipeline = ch.pipeline();
                if (TransportClientSession.this.useSSL) {
                    final Set<String> sslProtocols = TransportClientSession.this.enabledSslProtocols == null
                                                     || TransportClientSession.this.enabledSslProtocols.isEmpty()
                                                     ? TransportClientBuilder.DEFAULT_SSL_PROTOCOLS
                                                     : TransportClientSession.this.enabledSslProtocols;
                    final SSLEngine engine = SSLContextFactory.getInstance(false,
                                                                           TransportClientSession.this,
                                                                           TransportClientSession.this.address
                                                                               .getHostName())
                                                              .createSSLEngine();
                    engine.setUseClientMode(true);
                    engine.setEnabledProtocols(sslProtocols.toArray(new String[sslProtocols.size()]));
                    final List<String>
                        enabledCipherSuites
                        = new ArrayList<>(Arrays.asList(engine.getSupportedCipherSuites()));
                    analyzeCipher(pipeline, engine, enabledCipherSuites);

                }

                pipeline.addLast("protocol_version_negotiator",
                                 TransportClientSession.this.protocolVersionClientNegotiatorHandler);
                pipeline.addLast("frame_handler",
                                 new LengthFieldBasedFrameDecoder(TransportClientSession.this.maxMessageSizeBytes,
                                                                  0,
                                                                  4,
                                                                  0,
                                                                  4,
                                                                  true));
                pipeline.addLast("frame_encoder", new LengthFieldPrepender(4, false));
                pipeline.addLast("protocol_encoder_decoder", TransportClientSession.this.protocolEncoderDecoder);
                pipeline.addLast("traffic_blocker", TransportClientSession.this.channelTrafficBlocker);
                pipeline.addLast("handler", TransportClientSession.this.protocolHandler);
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

    private void analyzeCipher(final ChannelPipeline pipeline,
                               final SSLEngine engine,
                               final List<String> enabledCipherSuites) {

        final Iterator iterator = enabledCipherSuites.iterator();


        while(true) {
            String cipher;
            do {
                if (!iterator.hasNext()) {
                    engine.setEnabledCipherSuites(enabledCipherSuites.toArray(new String[enabledCipherSuites
                        .size()]));
                    pipeline.addLast("ssl", new SslHandler(engine));
                    return;
                }

                cipher = (String) iterator.next();
                LOGGER.info("Cipher= " + cipher);
            } while (!cipher.toUpperCase().contains("EXPORT")
                     && !cipher.toUpperCase().contains("NULL")
                     && !cipher.toUpperCase().contains("ANON")
                     && !cipher.toUpperCase().contains("_DES_")
                     && !cipher.toUpperCase().contains("MD5"));

            iterator.remove();
        }
    }

    void connect() {

        this.clientConnector.connect();
    }

    void disconnect() {

        this.clientConnector.disconnect();
    }

    void disconnected() {

        this.transportClient.disconnected();
    }

    void terminate() {

        if (this.transportClientSessionStateHandler != null) {
            try {
                this.transportClientSessionStateHandler.beforeTerminate();
            } catch (final Throwable var2) {
                LOGGER.warn("[" + this.transportName + "] terminate handler error", var2);
            }
        }

        LOGGER.debug("[" + this.transportName + "] Terminating client session");
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

        if (this.clientConnector.isOnline()) {
            this.protocolHandler.writeMessage(this.clientConnector.getPrimarySession(), message);
            return true;
        } else {
            LOGGER.error("[{}] TransportClient not connected, message: {}", this.transportName, message);
            return false;
        }
    }

    ProtocolMessage sendRequest(final ProtocolMessage message, final long timeout, final TimeUnit timeoutUnits)
        throws InterruptedException, TimeoutException, ConnectException {

        if (this.clientConnector.isOnline()) {
            final Long syncRequestId = this.transportClient.getNextId();
            message.setSynchRequestId(syncRequestId);
            final RequestMessageTransportListenableFuture
                task
                = new RequestMessageTransportListenableFuture(this.transportName,
                                                              syncRequestId,
                                                              this.syncRequests,
                                                              message);
            this.syncRequests.put(syncRequestId, task);
            final ChannelFuture future = this.protocolHandler.writeMessage(this.clientConnector.getPrimarySession(),
                                                                           message);
            task.setChannelFuture(future);
            task.scheduleTimeoutCheck(new SyncMessageTimeoutChecker(this.scheduledExecutorService,
                                                                    task,
                                                                    timeoutUnits.toMillis(timeout),
                                                                    true));

            try {
                return task.get(timeout, timeoutUnits);
            } catch (final ExecutionException var9) {
                if (var9.getCause() instanceof TimeoutException) {
                    throw (TimeoutException) var9.getCause();
                } else if (var9.getCause() instanceof RuntimeException) {
                    throw (RuntimeException) var9.getCause();
                } else if (var9.getCause() instanceof ConnectException) {
                    throw (ConnectException) var9.getCause();
                } else {
                    throw new RuntimeException("[" + this.transportName + "] " + var9.getCause());
                }
            }
        } else {
            throw new ConnectException("[" + this.transportName + "] TransportClient not connected, message: " + message
                .toString(400));
        }
    }

    <V> ListenableFuture<V> sendMessageAsync(final ProtocolMessage message) {

        if (this.clientConnector.isOnline()) {
            final ChannelFuture
                channelFuture
                = this.protocolHandler.writeMessage(this.clientConnector.getPrimarySession(), message);
            return new MessageListenableFuture(channelFuture);
        } else {
            return Futures.immediateFailedFuture(new ConnectException("["
                                                                      + this.transportName
                                                                      + "] TransportClient not connected, message: "
                                                                      + message.toString(400)));
        }
    }

    RequestListenableFuture sendRequestAsync(final ProtocolMessage message) {

        return this.sendRequestAsync(message,
                                     this.clientConnector.getPrimarySession(),
                                     this.syncMessageTimeout,
                                     false, null);
    }

    RequestListenableFuture sendRequestAsync(final ProtocolMessage message,
                                             final Channel channel,
                                             final long timeout,
                                             final boolean doNotRestartTimerOnInProcessResponse,
                                             final MessageSentListener messageSentListener) {

        if (this.clientConnector.isOnline()) {
            final Long syncRequestId = this.transportClient.getNextId();
            message.setSynchRequestId(syncRequestId);
            final RequestMessageTransportListenableFuture
                task
                = new RequestMessageTransportListenableFuture(this.transportName,
                                                              syncRequestId,
                                                              this.syncRequests,
                                                              message);
            this.syncRequests.put(syncRequestId, task);
            final ChannelFuture channelFuture = this.protocolHandler.writeMessage(channel, message);
            if (messageSentListener != null) {
                channelFuture.addListener(new GenericFutureListener<io.netty.util.concurrent.Future<? super Void>>() {
                    public void operationComplete(final io.netty.util.concurrent.Future<? super Void> future)
                        throws Exception {

                        if (channelFuture.isSuccess()) {
                            messageSentListener.messageSent(message);
                        } else if (channelFuture.isDone() && channelFuture.cause() != null) {
                            final Throwable cause = channelFuture.cause();
                            TransportClientSession.LOGGER.error("[{}] Request send failed because of {}: {}",
                                                                new Object[]{TransportClientSession.this
                                                                                 .getTransportName(),
                                                                             cause.getClass().getSimpleName(),
                                                                             cause.getMessage()});
                        } else {
                            TransportClientSession.LOGGER.error("[{}] Request send failed",
                                                                TransportClientSession.this.getTransportName());
                        }

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

    public boolean controlRequest(final ProtocolMessage message, final MessageSentListener messageSentListener) {

        if (!this.clientConnector.isOnline()) {
            return false;
        } else {
            if (messageSentListener != null) {
                final ListenableFuture future = this.sendMessageAsync(message);
                future.addListener(new Runnable() {
                    public void run() {

                        try {
                            final AbstractEventExecutorTask eventTask = new AbstractEventExecutorTask(
                                TransportClientSession.this,
                                TransportClientSession.this.protocolHandler) {
                                public Object getOrderKey() {

                                    return null;
                                }

                                public void run() {

                                    try {
                                        if (future.isDone() && !future.isCancelled()) {
                                            try {
                                                future.get();
                                            } catch (final Exception var2) {
                                                TransportClientSession.LOGGER.error("["
                                                                                    + TransportClientSession.this
                                                                                        .transportName
                                                                                    + "] "
                                                                                    + var2.getMessage(), var2);

                                            }

                                            messageSentListener.messageSent(message);
                                        }
                                    } catch (final Exception var3) {
                                        TransportClientSession.LOGGER.error("["
                                                                            + TransportClientSession.this.transportName
                                                                            + "] "
                                                                            + var3.getMessage(), var3);

                                    }

                                }
                            };
                            eventTask.executeInExecutor(TransportClientSession.this.protocolHandler.getEventExecutor());
                        } catch (final Throwable var2) {
                            TransportClientSession.LOGGER.error("["
                                                                + TransportClientSession.this.transportName
                                                                + "] "
                                                                + var2.getMessage(), var2);

                        }

                    }
                }, MoreExecutors.newDirectExecutorService());
            } else {
                this.sendMessageNaive(message);
            }

            return true;
        }
    }

    public ProtocolMessage controlBlockingRequest(final ProtocolMessage message, final Long timeoutTime)
        throws InterruptedException, IOException {

        if (!this.clientConnector.isOnline()) {
            throw new IOException("[" + this.transportName + "] Primary session is not active");
        } else {
            final ChannelFuture
                channelFuture
                = this.protocolHandler.writeMessage(this.clientConnector.getPrimarySession(), message);
            if (timeoutTime != null && timeoutTime > 0L) {
                try {
                    channelFuture.get(timeoutTime, TimeUnit.MILLISECONDS);
                } catch (final ExecutionException var5) {
                    if (var5.getCause() instanceof TimeoutException) {
                        throw new InterruptedException(var5.getMessage());
                    }

                    if (var5.getCause() instanceof RuntimeException) {
                        throw (RuntimeException) var5.getCause();
                    }

                    throw new IOException("[" + this.transportName + "] " + var5.getMessage(), var5.getCause());
                } catch (final TimeoutException var6) {
                    throw new InterruptedException("[" + this.transportName + "] " + var6.getMessage());
                }
            }

            return new OkResponseMessage();
        }
    }

    public boolean controlRequest(final ProtocolMessage message) {

        return this.controlRequest(message, null);
    }

    public ProtocolMessage controlSynchRequest(final ProtocolMessage message, final Long timeoutTime)
        throws TimeoutException {

        if (!this.clientConnector.isOnline()) {
            throw new IllegalStateException("["
                                            + this.transportName
                                            + "] TransportClient not connected, message: "
                                            + message.toString(400));
        } else if (timeoutTime != null && timeoutTime > 0L) {
            final Long syncRequestId = this.transportClient.getNextId();
            message.setSynchRequestId(syncRequestId);
            final RequestMessageTransportListenableFuture
                task
                = new RequestMessageTransportListenableFuture(this.transportName,
                                                              syncRequestId,
                                                              this.syncRequests,
                                                              message);
            this.syncRequests.put(syncRequestId, task);
            final ChannelFuture future = this.protocolHandler.writeMessage(this.clientConnector.getPrimarySession(),
                                                                           message);
            task.setChannelFuture(future);
            task.scheduleTimeoutCheck(new SyncMessageTimeoutChecker(this.scheduledExecutorService, task, timeoutTime));

            while (true) {
                try {
                    return task.get(timeoutTime, TimeUnit.MILLISECONDS);
                } catch (final TimeoutException var7) {
                    if (task.getInProcessResponseLastTime() + timeoutTime >= System.currentTimeMillis()) {
                        throw var7;
                    }
                } catch (final InterruptedException var8) {
                    throw new TimeoutException("[" + this.transportName + "] Interrupted while waiting for response");
                } catch (final ExecutionException var9) {
                    if (var9.getCause() instanceof TimeoutException) {
                        throw (TimeoutException) var9.getCause();
                    }

                    if (var9.getCause() instanceof RuntimeException) {
                        throw (RuntimeException) var9.getCause();
                    }

                    throw new RuntimeException(var9.getMessage(), var9.getCause());
                }
            }
        } else {
            this.sendMessageNaive(message);
            if (timeoutTime == null) {
                throw new NullPointerException("["
                                               + this.transportName
                                               + "] TimeoutTime parameter is null, but message was still sent");
            } else {
                throw new TimeoutException("[" + this.transportName + "] Timeout is not specified or is zero");
            }
        }
    }

    public void securityException(final X509Certificate[] chain,
                                  final String authType,
                                  final CertificateException certificateException) {

        this.clientConnector.securityException(chain, authType, certificateException);
    }

    IoSessionWrapper getIoSessionWrapper() {

        ((NettyIoSessionWrapperAdapter) this.sessionWrapper).setChannel(this.clientConnector.getPrimarySession());
        return this.sessionWrapper;
    }

    RemoteCallSupport getRemoteCallSupport() {

        return this.transportClient.getRemoteCallSupport();
    }

    RequestMessageTransportListenableFuture getSyncRequestFuture(final Long syncId) {

        return this.syncRequests.get(syncId);
    }

    boolean isOnline() {

        return this.clientConnector.isOnline();
    }

    boolean isTerminating() {

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

    String getTransportName() {

        return this.transportName;
    }

    int getCriticalEventQueueSize() {

        return this.criticalEventQueueSize;
    }

    int getCriticalAuthEventQueueSize() {

        return this.criticalAuthEventQueueSize;
    }

    long getEventExecutionWarningDelay() {

        return this.eventExecutionWarningDelay;
    }

    long getEventExecutionErrorDelay() {

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

    public int getSendCompletionDelayCheckEveryNTimesWarning() {

        return this.sendCompletionDelayCheckEveryNTimesWarning;
    }

    public int getSendCompletionDelayCheckEveryNTimesError() {

        return this.sendCompletionDelayCheckEveryNTimesError;
    }

    FeedbackEventsConcurrencyPolicy getConcurrencyPolicy() {

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

    ClientProtocolHandler getProtocolHandler() {

        return this.protocolHandler;
    }

    ClientConnector getClientConnector() {

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

    protected boolean isDuplicateSyncMessagesToClientListeners() {

        return this.duplicateSyncMessagesToClientListeners;
    }

    protected long getDroppableMessagesClientTTL() {

        return this.droppableMessagesClientTTL;
    }

    public boolean isSkipDroppableMessages() {

        return this.skipDroppableMessages;
    }

    public void setSkipDroppableMessages(final boolean skipDroppableMessages) {

        this.skipDroppableMessages = skipDroppableMessages;
    }

    protected AtomicLong getDroppedMessageCounter() {

        return this.droppedMessageCounter;
    }

    public boolean isLogSkippedDroppableMessages() {

        return this.logSkippedDroppableMessages;
    }

    int getAuthEventPoolSize() {

        return this.authEventPoolSize;
    }

    long getAuthEventPoolAutoCleanupInterval() {

        return this.authEventPoolAutoCleanupInterval;
    }

    InetSocketAddress getAddress() {

        return this.address;
    }

    AtomicBoolean getLogEventPoolThreadDumpsOnLongExecution() {

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

    boolean isSendCpuInfoToServer() {

        return this.sendCpuInfoToServer;
    }

    public int getSyncRequestProcessingPoolSize() {

        return this.syncRequestProcessingPoolSize;
    }

    public long getSyncRequestProcessingPoolAutoCleanupInterval() {

        return this.syncRequestProcessingPoolAutoCleanupInterval;
    }

    public int getCriticalSyncRequestProcessingQueueSize() {

        return this.criticalSyncRequestProcessingQueueSize;
    }

    public long getMaxSubsequentPingFailedCount() {

        return this.maxSubsequentPingFailedCount;
    }

    public long getEventPoolTerminationTimeUnitCount() {

        return this.eventPoolTerminationTimeUnitCount;
    }

    public TimeUnit getEventPoolTerminationTimeUnit() {

        return this.eventPoolTerminationTimeUnit;
    }

    public long getAuthEventPoolTerminationTimeUnitCount() {

        return this.authEventPoolTerminationTimeUnitCount;
    }

    public TimeUnit getAuthEventPoolTerminationTimeUnit() {

        return this.authEventPoolTerminationTimeUnit;
    }

    public long getStreamProcessingPoolTerminationTimeUnitCount() {

        return this.streamProcessingPoolTerminationTimeUnitCount;
    }

    public TimeUnit getStreamProcessingPoolTerminationTimeUnit() {

        return this.streamProcessingPoolTerminationTimeUnit;
    }

    public long getSyncRequestProcessingPoolTerminationTimeUnitCount() {

        return this.syncRequestProcessingPoolTerminationTimeUnitCount;
    }

    public TimeUnit getSyncRequestProcessingPoolTerminationTimeUnit() {

        return this.syncRequestProcessingPoolTerminationTimeUnit;
    }
}
