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


import static org.softcake.yubari.netty.TransportClientBuilder.DEFAULT_ASYNC_REQUEST_FUTURE_EXECUTOR;
import static org.softcake.yubari.netty.TransportClientBuilder.DEFAULT_AUTHORIZATION_TIMEOUT;
import static org.softcake.yubari.netty.TransportClientBuilder.DEFAULT_AUTH_EVENT_POOL_AUTO_CLEANUP_INTERVAL;
import static org.softcake.yubari.netty.TransportClientBuilder.DEFAULT_AUTH_EVENT_POOL_SIZE;
import static org.softcake.yubari.netty.TransportClientBuilder.DEFAULT_AUTH_EVENT_POOL_TERMINATION_TIME_UNIT;
import static org.softcake.yubari.netty.TransportClientBuilder.DEFAULT_AUTH_EVENT_POOL_TERMINATION_TIME_UNIT_COUNT;
import static org.softcake.yubari.netty.TransportClientBuilder.DEFAULT_CHANNEL_OPTIONS;
import static org.softcake.yubari.netty.TransportClientBuilder.DEFAULT_CONNECTION_TIMEOUT;
import static org.softcake.yubari.netty.TransportClientBuilder.DEFAULT_CRITICAL_AUTH_EVENT_QUEUE_SIZE;
import static org.softcake.yubari.netty.TransportClientBuilder.DEFAULT_CRITICAL_EVENT_QUEUE_SIZE;
import static org.softcake.yubari.netty.TransportClientBuilder.DEFAULT_CRITICAL_STREAM_PROCESSING_QUEUE_SIZE;
import static org.softcake.yubari.netty.TransportClientBuilder.DEFAULT_DROPPABLE_MESSAGE_CLIENT_TTL;
import static org.softcake.yubari.netty.TransportClientBuilder.DEFAULT_DROPPABLE_MESSAGE_SERVER_TTL;
import static org.softcake.yubari.netty.TransportClientBuilder.DEFAULT_DUPLICATE_SYNC_MESSAGES_TO_CLIENT_LISTENERS;
import static org.softcake.yubari.netty.TransportClientBuilder.DEFAULT_EVENT_EXECUTION_DELAY_CHECK_EVERY_N_TIMES_ERROR;
import static org.softcake.yubari.netty.TransportClientBuilder
    .DEFAULT_EVENT_EXECUTION_DELAY_CHECK_EVERY_N_TIMES_WARNING;
import static org.softcake.yubari.netty.TransportClientBuilder.DEFAULT_EVENT_EXECUTION_ERROR_DELAY;
import static org.softcake.yubari.netty.TransportClientBuilder.DEFAULT_EVENT_EXECUTION_WARNING_DELAY;
import static org.softcake.yubari.netty.TransportClientBuilder.DEFAULT_EVENT_POOL_AUTO_CLEANUP_INTERVAL;
import static org.softcake.yubari.netty.TransportClientBuilder.DEFAULT_EVENT_POOL_SIZE;
import static org.softcake.yubari.netty.TransportClientBuilder.DEFAULT_EVENT_POOL_TERMINATION_TIME_UNIT;
import static org.softcake.yubari.netty.TransportClientBuilder.DEFAULT_EVENT_POOL_TERMINATION_TIME_UNIT_COUNT;
import static org.softcake.yubari.netty.TransportClientBuilder.DEFAULT_JMX_BEAN_NAME;
import static org.softcake.yubari.netty.TransportClientBuilder.DEFAULT_LOG_EVENT_POOL_THREAD_DUMPS_ON_LONG_EXECUTION;
import static org.softcake.yubari.netty.TransportClientBuilder.DEFAULT_LOG_SKIPPED_DROPPABLE_MESSAGES;
import static org.softcake.yubari.netty.TransportClientBuilder.DEFAULT_MAX_MESSAGE_SIZE_BYTES;
import static org.softcake.yubari.netty.TransportClientBuilder.DEFAULT_MAX_SUBSEQUENT_PING_FAILED_COUNT;
import static org.softcake.yubari.netty.TransportClientBuilder.DEFAULT_NEED_JMX_BEAN;
import static org.softcake.yubari.netty.TransportClientBuilder.DEFAULT_PING_INTERVAL;
import static org.softcake.yubari.netty.TransportClientBuilder.DEFAULT_PING_TIMEOUT;
import static org.softcake.yubari.netty.TransportClientBuilder.DEFAULT_PROTOCOL_VERSION_NEGOTIATION_TIMEOUT;
import static org.softcake.yubari.netty.TransportClientBuilder.DEFAULT_RECONNECT_DELAY;
import static org.softcake.yubari.netty.TransportClientBuilder.DEFAULT_SECONDARY_CONNECTION_RECONNECTS_RESET_DELAY;
import static org.softcake.yubari.netty.TransportClientBuilder.DEFAULT_SECONDARY_CONNECTION_RECONNECT_ATTEMPTS;
import static org.softcake.yubari.netty.TransportClientBuilder.DEFAULT_SEND_COMPLETION_DELAY_CHECK_EVERY_N_TIMES_ERROR;
import static org.softcake.yubari.netty.TransportClientBuilder
    .DEFAULT_SEND_COMPLETION_DELAY_CHECK_EVERY_N_TIMES_WARNING;
import static org.softcake.yubari.netty.TransportClientBuilder.DEFAULT_SEND_COMPLETION_ERROR_DELAY;
import static org.softcake.yubari.netty.TransportClientBuilder.DEFAULT_SEND_COMPLETION_WARNING_DELAY;
import static org.softcake.yubari.netty.TransportClientBuilder.DEFAULT_SEND_CPU_INFO_TO_SERVER;
import static org.softcake.yubari.netty.TransportClientBuilder.DEFAULT_SKIP_DROPPABLE_MESSAGES;
import static org.softcake.yubari.netty.TransportClientBuilder.DEFAULT_SSL_HANDSHAKE_TIMEOUT;
import static org.softcake.yubari.netty.TransportClientBuilder.DEFAULT_SSL_PROTOCOLS;
import static org.softcake.yubari.netty.TransportClientBuilder.DEFAULT_STREAM_BUFFER_SIZE;
import static org.softcake.yubari.netty.TransportClientBuilder.DEFAULT_STREAM_CHUNK_PROCESSING_TIMEOUT;
import static org.softcake.yubari.netty.TransportClientBuilder.DEFAULT_STREAM_PROCESSING_POOL_AUTO_CLEANUP_INTERVAL;
import static org.softcake.yubari.netty.TransportClientBuilder.DEFAULT_STREAM_PROCESSING_POOL_SIZE;
import static org.softcake.yubari.netty.TransportClientBuilder.DEFAULT_STREAM_PROCESSING_POOL_TERMINATION_TIME_UNIT;
import static org.softcake.yubari.netty.TransportClientBuilder
    .DEFAULT_STREAM_PROCESSING_POOL_TERMINATION_TIME_UNIT_COUNT;
import static org.softcake.yubari.netty.TransportClientBuilder.DEFAULT_SYNC_MESSAGE_TIMEOUT;
import static org.softcake.yubari.netty.TransportClientBuilder
    .DEFAULT_SYNC_REQUEST_PROCESSING_POOL_AUTO_CLEANUP_INTERVAL;
import static org.softcake.yubari.netty.TransportClientBuilder.DEFAULT_SYNC_REQUEST_PROCESSING_POOL_SIZE;
import static org.softcake.yubari.netty.TransportClientBuilder
    .DEFAULT_SYNC_REQUEST_PROCESSING_POOL_TERMINATION_TIME_UNIT;
import static org.softcake.yubari.netty.TransportClientBuilder
    .DEFAULT_SYNC_REQUEST_PROCESSING_POOL_TERMINATION_TIME_UNIT_COUNT;
import static org.softcake.yubari.netty.TransportClientBuilder.DEFAULT_SYNC_REQUEST_PROCESSING_QUEUE_SIZE;
import static org.softcake.yubari.netty.TransportClientBuilder.DEFAULT_TERMINATION_MAX_AWAIT_TIMEOUT_IN_MILLIS;
import static org.softcake.yubari.netty.TransportClientBuilder.DEFAULT_TRANSPORT_POOL_SIZE;
import static org.softcake.yubari.netty.TransportClientBuilder.DEFAULT_USE_FEEDER_SOCKET;
import static org.softcake.yubari.netty.TransportClientBuilder.DEFAULT_USE_SSL;

import org.softcake.yubari.netty.mina.ClientListener;
import org.softcake.yubari.netty.mina.FeedbackEventsConcurrencyPolicy;
import org.softcake.yubari.netty.mina.ISessionStats;
import org.softcake.yubari.netty.mina.MessageSentListener;
import org.softcake.yubari.netty.mina.ProxyInterfaceFactory;
import org.softcake.yubari.netty.mina.RemoteCallSupport;
import org.softcake.yubari.netty.mina.RequestListenableFuture;
import org.softcake.yubari.netty.mina.SecurityExceptionHandler;
import org.softcake.yubari.netty.mina.ServerAddress;
import org.softcake.yubari.netty.mina.SyncInstrumentsAndAllOtherConcurrencyPolicy;
import org.softcake.yubari.netty.mina.TransportClientJMXBean;
import org.softcake.yubari.netty.stream.StreamListener;

import com.dukascopy.dds4.ping.IPingListener;
import com.dukascopy.dds4.ping.PingManager;
import com.dukascopy.dds4.transport.common.protocol.binary.AbstractStaticSessionDictionary;
import com.dukascopy.dds4.transport.msg.system.ProtocolMessage;
import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import io.netty.channel.ChannelOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.net.ConnectException;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import javax.management.InstanceAlreadyExistsException;
import javax.management.InstanceNotFoundException;
import javax.management.MBeanRegistrationException;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.NotCompliantMBeanException;
import javax.management.ObjectName;

public class TransportClient implements org.softcake.yubari.netty.ITransportClient {
    public static final int PROTOCOL_MESSAGE_EXCEPTION_LENGTH = 400;
    private static final Logger LOGGER = LoggerFactory.getLogger(TransportClient.class);
    private static final AtomicInteger defaultTransportCounter = new AtomicInteger();
    private final CopyOnWriteArrayList<ClientListener> listeners;
    private final boolean logSkippedDroppableMessages;
    private final AtomicBoolean isTerminated = new AtomicBoolean(false);
    private final long syncMessageTimeout;
    private final boolean duplicateSyncMessagesToClientListeners;
    private final Map<Boolean, PingManager> pingManagers = new ConcurrentHashMap<>();
    private final boolean needJmxBean;
    private final String jmxBeanName;
    private final Executor asyncRequestFutureExecutor;
    private final AtomicLong droppedMessageCounter = new AtomicLong(0L);
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
    private final long terminationMaxAwaitTimeoutInMillis;
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
    private ServerAddress address;
    private ClientAuthorizationProvider authorizationProvider;
    private boolean useSSL;
    private long sslHandshakeTimeout;
    private long protocolVersionNegotiationTimeout;
    private boolean useFeederSocket;
    private long connectionTimeout;
    private long authorizationTimeout;
    private int transportPoolSize;
    private int eventPoolSize;
    private long eventPoolAutoCleanupInterval;
    private int authEventPoolSize;
    private long authEventPoolAutoCleanupInterval;
    private int criticalAuthEventQueueSize;
    private long primaryConnectionPingInterval;
    private long secondaryConnectionPingInterval;
    private long primaryConnectionPingTimeout;
    private long secondaryConnectionPingTimeout;
    private int secondaryConnectionReconnectAttempts;
    private long secondaryConnectionReconnectsResetDelay;
    private long droppableMessageServerTTL;
    private long droppableMessageClientTTL;
    private boolean skipDroppableMessages;
    private Map<ChannelOption<?>, Object> channelOptions;
    private String userAgent;
    private String transportName;
    private long reconnectDelay;
    private int maxMessageSizeBytes;
    private int criticalEventQueueSize;
    private long eventExecutionWarningDelay;
    private long eventExecutionErrorDelay;
    private int eventExecutionDelayCheckEveryNTimesWarning;
    private int eventExecutionDelayCheckEveryNTimesError;
    private long sendCompletionWarningDelay;
    private long sendCompletionErrorDelay;
    private int sendCompletionDelayCheckEveryNTimesWarning;
    private int sendCompletionDelayCheckEveryNTimesError;
    private FeedbackEventsConcurrencyPolicy concurrencyPolicy;
    private SecurityExceptionHandler securityExceptionHandler;
    private boolean debugMode;
    private RemoteCallSupport remoteCallSupport = new RemoteCallSupport(new ProxyInterfaceFactory());
    private volatile TransportClientSession transportClientSession;
    private AtomicLong requestId = new AtomicLong(0L);
    private AtomicInteger ticksCounter = new AtomicInteger();
    private AbstractStaticSessionDictionary staticSessionDictionary;
    private ISessionStats sessionStats;
    private IPingListener pingListener;

    public TransportClient() {

        this.listeners = new CopyOnWriteArrayList<>();
        this.useSSL = DEFAULT_USE_SSL;
        this.sslHandshakeTimeout = DEFAULT_SSL_HANDSHAKE_TIMEOUT;
        this.protocolVersionNegotiationTimeout = DEFAULT_PROTOCOL_VERSION_NEGOTIATION_TIMEOUT;
        this.useFeederSocket = DEFAULT_USE_FEEDER_SOCKET;
        this.connectionTimeout = DEFAULT_CONNECTION_TIMEOUT;
        this.authorizationTimeout = DEFAULT_AUTHORIZATION_TIMEOUT;
        this.transportPoolSize = DEFAULT_TRANSPORT_POOL_SIZE;
        this.eventPoolSize = DEFAULT_EVENT_POOL_SIZE;
        this.eventPoolAutoCleanupInterval = DEFAULT_EVENT_POOL_AUTO_CLEANUP_INTERVAL;
        this.authEventPoolSize = DEFAULT_AUTH_EVENT_POOL_SIZE;
        this.authEventPoolAutoCleanupInterval = DEFAULT_AUTH_EVENT_POOL_AUTO_CLEANUP_INTERVAL;
        this.criticalAuthEventQueueSize = DEFAULT_CRITICAL_AUTH_EVENT_QUEUE_SIZE;
        this.primaryConnectionPingInterval = DEFAULT_PING_INTERVAL;
        this.secondaryConnectionPingInterval = DEFAULT_PING_INTERVAL;
        this.primaryConnectionPingTimeout = DEFAULT_PING_TIMEOUT;
        this.secondaryConnectionPingTimeout = DEFAULT_PING_TIMEOUT;
        this.secondaryConnectionReconnectAttempts = DEFAULT_SECONDARY_CONNECTION_RECONNECT_ATTEMPTS;
        this.secondaryConnectionReconnectsResetDelay = DEFAULT_SECONDARY_CONNECTION_RECONNECTS_RESET_DELAY;
        this.droppableMessageServerTTL = DEFAULT_DROPPABLE_MESSAGE_SERVER_TTL;
        this.droppableMessageClientTTL = DEFAULT_DROPPABLE_MESSAGE_CLIENT_TTL;
        this.skipDroppableMessages = DEFAULT_SKIP_DROPPABLE_MESSAGES;
        this.channelOptions = ImmutableMap.copyOf(DEFAULT_CHANNEL_OPTIONS);
        this.userAgent = "NettyTransportClient " + getTransportVersion() + " - " + getLocalIpAddress().getHostAddress();
        String version = this.getClass().getPackage().getImplementationVersion();
        version = version == null ? "SNAPSHOT" : version;
        // this.userAgent = version;
        this.reconnectDelay = DEFAULT_RECONNECT_DELAY;
        this.maxMessageSizeBytes = DEFAULT_MAX_MESSAGE_SIZE_BYTES;
        this.criticalEventQueueSize = DEFAULT_CRITICAL_EVENT_QUEUE_SIZE;
        this.eventExecutionWarningDelay = DEFAULT_EVENT_EXECUTION_WARNING_DELAY;
        this.eventExecutionErrorDelay = DEFAULT_EVENT_EXECUTION_ERROR_DELAY;
        this.eventExecutionDelayCheckEveryNTimesWarning = DEFAULT_EVENT_EXECUTION_DELAY_CHECK_EVERY_N_TIMES_WARNING;
        this.eventExecutionDelayCheckEveryNTimesError = DEFAULT_EVENT_EXECUTION_DELAY_CHECK_EVERY_N_TIMES_ERROR;
        this.sendCompletionWarningDelay = DEFAULT_SEND_COMPLETION_WARNING_DELAY;
        this.sendCompletionErrorDelay = DEFAULT_SEND_COMPLETION_ERROR_DELAY;
        this.sendCompletionDelayCheckEveryNTimesWarning = DEFAULT_SEND_COMPLETION_DELAY_CHECK_EVERY_N_TIMES_WARNING;
        this.sendCompletionDelayCheckEveryNTimesError = DEFAULT_SEND_COMPLETION_DELAY_CHECK_EVERY_N_TIMES_ERROR;
        this.concurrencyPolicy = new SyncInstrumentsAndAllOtherConcurrencyPolicy();
        this.needJmxBean = DEFAULT_NEED_JMX_BEAN;
        this.jmxBeanName = DEFAULT_JMX_BEAN_NAME;
        this.asyncRequestFutureExecutor = DEFAULT_ASYNC_REQUEST_FUTURE_EXECUTOR;
        this.syncMessageTimeout = DEFAULT_SYNC_MESSAGE_TIMEOUT;
        this.duplicateSyncMessagesToClientListeners = DEFAULT_DUPLICATE_SYNC_MESSAGES_TO_CLIENT_LISTENERS;
        this.logSkippedDroppableMessages = DEFAULT_LOG_SKIPPED_DROPPABLE_MESSAGES;
        this.logEventPoolThreadDumpsOnLongExecution = new AtomicBoolean(
            DEFAULT_LOG_EVENT_POOL_THREAD_DUMPS_ON_LONG_EXECUTION);
        this.streamListener = null;
        this.streamChunkProcessingTimeout = DEFAULT_STREAM_CHUNK_PROCESSING_TIMEOUT;
        this.streamBufferSize = DEFAULT_STREAM_BUFFER_SIZE;
        this.streamProcessingPoolSize = DEFAULT_STREAM_PROCESSING_POOL_SIZE;
        this.streamProcessingPoolAutoCleanupInterval = DEFAULT_STREAM_PROCESSING_POOL_AUTO_CLEANUP_INTERVAL;
        this.criticalStreamProcessingQueueSize = DEFAULT_CRITICAL_STREAM_PROCESSING_QUEUE_SIZE;
        this.sendCpuInfoToServer = DEFAULT_SEND_CPU_INFO_TO_SERVER;
        this.syncRequestProcessingPoolSize = DEFAULT_SYNC_REQUEST_PROCESSING_POOL_SIZE;
        this.syncRequestProcessingPoolAutoCleanupInterval = DEFAULT_SYNC_REQUEST_PROCESSING_POOL_AUTO_CLEANUP_INTERVAL;
        this.criticalSyncRequestProcessingQueueSize = DEFAULT_SYNC_REQUEST_PROCESSING_QUEUE_SIZE;
        this.terminationMaxAwaitTimeoutInMillis = DEFAULT_TERMINATION_MAX_AWAIT_TIMEOUT_IN_MILLIS;
        this.maxSubsequentPingFailedCount = DEFAULT_MAX_SUBSEQUENT_PING_FAILED_COUNT;
        this.transportClientSessionStateHandler = null;
        this.eventPoolTerminationTimeUnitCount = DEFAULT_EVENT_POOL_TERMINATION_TIME_UNIT_COUNT;
        this.eventPoolTerminationTimeUnit = DEFAULT_EVENT_POOL_TERMINATION_TIME_UNIT;
        this.authEventPoolTerminationTimeUnitCount = DEFAULT_AUTH_EVENT_POOL_TERMINATION_TIME_UNIT_COUNT;
        this.authEventPoolTerminationTimeUnit = DEFAULT_AUTH_EVENT_POOL_TERMINATION_TIME_UNIT;
        this.streamProcessingPoolTerminationTimeUnitCount = DEFAULT_STREAM_PROCESSING_POOL_TERMINATION_TIME_UNIT_COUNT;
        this.streamProcessingPoolTerminationTimeUnit = DEFAULT_STREAM_PROCESSING_POOL_TERMINATION_TIME_UNIT;
        this.syncRequestProcessingPoolTerminationTimeUnitCount
            = DEFAULT_SYNC_REQUEST_PROCESSING_POOL_TERMINATION_TIME_UNIT_COUNT;
        this.syncRequestProcessingPoolTerminationTimeUnit = DEFAULT_SYNC_REQUEST_PROCESSING_POOL_TERMINATION_TIME_UNIT;
        this.enabledSslProtocols = DEFAULT_SSL_PROTOCOLS;
        this.init();

    }

    TransportClient(final ServerAddress address,
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
                    final long droppableMessageServerTTL,
                    final long droppableMessageClientTTL,
                    final boolean skipDroppableMessages,
                    final boolean logSkippedDroppableMessages,
                    final Map<ChannelOption<?>, Object> channelOptions,
                    final String userAgent,
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
                    final boolean needJmxBean,
                    final String jmxBeanName,
                    final Executor asyncRequestFutureExecutor,
                    final IPingListener pingListener,
                    final long syncMessageTimeout,
                    final boolean duplicateSyncMessagesToClientListeners,
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
                    final long terminationMaxAwaitTimeoutInMillis,
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
        this.secondaryConnectionReconnectAttempts = secondaryConnectionReconnectAttempts;
        this.secondaryConnectionReconnectsResetDelay = secondaryConnectionReconnectsResetDelay;
        this.droppableMessageServerTTL = droppableMessageServerTTL;
        this.droppableMessageClientTTL = droppableMessageClientTTL;
        this.skipDroppableMessages = skipDroppableMessages;
        this.logSkippedDroppableMessages = logSkippedDroppableMessages;
        this.channelOptions = channelOptions;
        this.userAgent = userAgent;
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
        this.staticSessionDictionary = staticSessionDictionary;
        this.sessionStats = sessionStats;
        this.needJmxBean = needJmxBean;
        this.jmxBeanName = jmxBeanName;
        this.asyncRequestFutureExecutor = asyncRequestFutureExecutor;
        this.pingListener = pingListener;
        this.syncMessageTimeout = syncMessageTimeout;
        this.duplicateSyncMessagesToClientListeners = duplicateSyncMessagesToClientListeners;
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
        this.terminationMaxAwaitTimeoutInMillis = terminationMaxAwaitTimeoutInMillis;
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
        this.init();
    }

    public static TransportClientBuilder builder() {

        return new TransportClientBuilder();
    }


    public static InetAddress getLocalIpAddress() {

        InetAddress ipv4Local = null;
        InetAddress ipv4Remote = null;
        InetAddress ipv6Local = null;
        InetAddress ipv6Remote = null;


        try {

            // Iterate all NICs (network interface cards)...
            for (final Enumeration ifaces = NetworkInterface.getNetworkInterfaces(); ifaces.hasMoreElements(); ) {
                final NetworkInterface iface = (NetworkInterface) ifaces.nextElement();
                // Iterate all IP addresses assigned to each card...
                for (final Enumeration inetAddrs = iface.getInetAddresses(); inetAddrs.hasMoreElements(); ) {
                    final InetAddress inetAddr = (InetAddress) inetAddrs.nextElement();
                    if (!inetAddr.isLoopbackAddress()) {
                        if (!inetAddr.isSiteLocalAddress() && !inetAddr.isLinkLocalAddress()) {
                            if (inetAddr instanceof Inet6Address) {
                                ipv6Remote = inetAddr;
                            } else {
                                ipv4Remote = inetAddr;
                            }
                        } else if (inetAddr instanceof Inet6Address) {
                            ipv6Local = inetAddr;
                        } else {
                            ipv4Local = inetAddr;
                        }

                    }
                }
            }
            if (ipv4Remote != null) {
                return ipv4Remote;
            }

            if (ipv4Local != null) {
                return ipv4Local;
            }

            if (ipv6Remote != null) {
                return ipv6Remote;
            }

            if (ipv6Local != null) {
                return ipv6Local;
            }

            // At this point, we did not find a non-loopback address.
            // Fall back to returning whatever InetAddress.getLocalHost() returns...
            final InetAddress localHost = InetAddress.getLocalHost();
            if (localHost == null) {
                throw new UnknownHostException("The InetAddress.getLocalHost() method unexpectedly returned null.");
            }
            return localHost;
        } catch (SocketException | UnknownHostException e) {
            LOGGER.error("1. Failed to determine LAN address: ", e);
            try {
                return InetAddress.getByAddress(new byte[]{127, 0, 0, 1});
            } catch (final UnknownHostException var8) {
                LOGGER.error("2. Failed to determine LAN address: ", e);

                return null;
            }
        }
    }

    public static String getTransportVersion() {

        String version = TransportClient.class.getPackage().getImplementationVersion();
        version = version == null ? "SNAPSHOT" : version;
        return version;
    }

    private String checkAndGetJmxName() {

        String result = this.jmxBeanName;
        if (result == null) {
            result = "jmx:type=TransportClient,name=";
            this.checkAndSetTransportName();
            result = result + this.transportName;
        }

        return result;
    }

    private void init() {

        this.pingManagers.put(Boolean.TRUE, new PingManager(TimeUnit.MINUTES.toMillis(1L)));
        this.pingManagers.put(Boolean.FALSE, new PingManager(TimeUnit.MINUTES.toMillis(1L)));
        if (this.needJmxBean) {
            try {
                final MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
                mbs.registerMBean(new TransportClientJMXBean(this.getPingManager(true),
                                                             this.getPingManager(false),
                                                             this.droppedMessageCounter,
                                                             this.logEventPoolThreadDumpsOnLongExecution),
                                  new ObjectName(this.checkAndGetJmxName()));
            } catch (final MalformedObjectNameException | NotCompliantMBeanException | MBeanRegistrationException |
                InstanceAlreadyExistsException e) {
                LOGGER.error("Error registering server MBean:", e);
            }
        }

    }

    public List<ClientListener> getListeners() {

        return Collections.unmodifiableList(this.listeners);
    }

    /**
     * @deprecated
     */
    @Deprecated
    public void setListener(final ClientListener listener) {

        this.addListener(listener);

    }

    public void addListener(final ClientListener listener) {

        if (listener != null) {
            this.listeners.add(listener);
        }

    }

    public ClientAuthorizationProvider getAuthorizationProvider() {

        return this.authorizationProvider;
    }

    public void setAuthorizationProvider(final ClientAuthorizationProvider authorizationProvider) {

        this.authorizationProvider = authorizationProvider;
    }

    public boolean isUseSSL() {

        return this.useSSL;
    }

    public void setUseSSL(final boolean useSSL) {

        this.useSSL = useSSL;
    }

    public long getSslHandshakeTimeout() {

        return this.sslHandshakeTimeout;
    }

    public void setSslHandshakeTimeout(final long sslHandshakeTimeout) {

        this.sslHandshakeTimeout = sslHandshakeTimeout;
    }

    public long getProtocolVersionNegotiationTimeout() {

        return this.protocolVersionNegotiationTimeout;
    }

    public void setProtocolVersionNegotiationTimeout(final long protocolVersionNegotiationTimeout) {

        this.protocolVersionNegotiationTimeout = protocolVersionNegotiationTimeout;
    }

    public boolean isUseFeederSocket() {

        return this.useFeederSocket;
    }

    public void setUseFeederSocket(final boolean useFeederSocket) {

        this.useFeederSocket = useFeederSocket;
    }

    public long getConnectionTimeout() {

        return this.connectionTimeout;
    }

    public void setConnectionTimeout(final long connectionTimeout) {

        this.connectionTimeout = connectionTimeout;
        this.channelOptions.put(ChannelOption.CONNECT_TIMEOUT_MILLIS, (int) connectionTimeout);
    }

    public long getAuthorizationTimeout() {

        return this.authorizationTimeout;
    }

    public void setAuthorizationTimeout(final long authorizationTimeout) {

        this.authorizationTimeout = authorizationTimeout;
    }

    public int getTransportPoolSize() {

        return this.transportPoolSize;
    }

    public void setTransportPoolSize(final int transportPoolSize) {

        this.transportPoolSize = transportPoolSize;
    }

    public int getEventPoolSize() {

        return this.eventPoolSize;
    }

    public void setEventPoolSize(final int eventPoolSize) {

        this.eventPoolSize = eventPoolSize;
    }

    public long isEventPoolAutoCleanupInterval() {

        return this.eventPoolAutoCleanupInterval;
    }

    public void setEventPoolAutoCleanupInterval(final long eventPoolAutoCleanupInterval) {

        this.eventPoolAutoCleanupInterval = eventPoolAutoCleanupInterval;
    }

    public long getPrimaryConnectionPingInterval() {

        return this.primaryConnectionPingInterval;
    }

    public void setPrimaryConnectionPingInterval(long primaryConnectionPingInterval) {

        if (primaryConnectionPingInterval < 1L || primaryConnectionPingInterval > 3600000L) {
            primaryConnectionPingInterval = 0L;
        }

        this.primaryConnectionPingInterval = primaryConnectionPingInterval;
    }

    public long getSecondaryConnectionPingInterval() {

        return this.secondaryConnectionPingInterval;
    }

    public void setSecondaryConnectionPingInterval(long secondaryConnectionPingInterval) {

        if (secondaryConnectionPingInterval < 1L || secondaryConnectionPingInterval > 3600000L) {
            secondaryConnectionPingInterval = 0L;
        }

        this.secondaryConnectionPingInterval = secondaryConnectionPingInterval;
    }

    public long getPrimaryConnectionPingTimeout() {

        return this.primaryConnectionPingTimeout;
    }

    public void setPrimaryConnectionPingTimeout(long primaryConnectionPingTimeout) {

        if (primaryConnectionPingTimeout < 1L || primaryConnectionPingTimeout > 3600000L) {
            primaryConnectionPingTimeout = 0L;
        }

        this.primaryConnectionPingTimeout = primaryConnectionPingTimeout;
    }

    public long getSecondaryConnectionPingTimeout() {

        return this.secondaryConnectionPingTimeout;
    }

    public void setSecondaryConnectionPingTimeout(long secondaryConnectionPingTimeout) {

        if (secondaryConnectionPingTimeout < 1L || secondaryConnectionPingTimeout > 3600000L) {
            secondaryConnectionPingTimeout = 0L;
        }

        this.secondaryConnectionPingTimeout = secondaryConnectionPingTimeout;
    }

    public int getSecondaryConnectionReconnectAttempts() {

        return this.secondaryConnectionReconnectAttempts;
    }

    public void setSecondaryConnectionReconnectAttempts(final int secondaryConnectionReconnectAttempts) {

        if (secondaryConnectionReconnectAttempts < 0) {
            throw new IllegalArgumentException("["
                                               + this.transportName
                                               + "] Secondary connection reconnect attempts can't be less than 0. If "
                                               + "you want to disable reconnects set it to 0");
        } else {
            this.secondaryConnectionReconnectAttempts = secondaryConnectionReconnectAttempts;
        }
    }

    public long getSecondaryConnectionReconnectsResetDelay() {

        return this.secondaryConnectionReconnectsResetDelay;
    }

    public void setSecondaryConnectionReconnectsResetDelay(final long secondaryConnectionReconnectsResetDelay) {

        this.secondaryConnectionReconnectsResetDelay = secondaryConnectionReconnectsResetDelay;
    }

    public long getDroppableMessagesServerTTL() {

        return this.droppableMessageServerTTL;
    }

    public void setDroppableMessagesServerTTL(final long droppableMessagesServerTTL) {

        this.droppableMessageServerTTL = droppableMessagesServerTTL;
    }

    public Map<ChannelOption<?>, Object> getChannelOptions() {

        return Collections.unmodifiableMap(this.channelOptions);
    }

    public void setChannelOptions(final Map<ChannelOption<?>, Object> channelOptions) {

        this.channelOptions = channelOptions;
        if (channelOptions.containsKey(ChannelOption.CONNECT_TIMEOUT_MILLIS)) {
            this.connectionTimeout = (long) (Integer) channelOptions.get(ChannelOption.CONNECT_TIMEOUT_MILLIS);
        } else {
            channelOptions.put(ChannelOption.CONNECT_TIMEOUT_MILLIS, (int) this.connectionTimeout);
        }

    }

    public String getUserAgent() {

        return this.userAgent;
    }

    public void setUserAgent(final String userAgent) {

        this.userAgent = userAgent;
    }

    public String getTransportName() {

        return this.transportName;
    }

    public void setTransportName(final String transportName) {

        this.transportName = transportName;
    }

    public long getReconnectDelay() {

        return this.reconnectDelay;
    }

    public void setReconnectDelay(final long reconnectDelay) {

        this.reconnectDelay = reconnectDelay;
    }

    public int getMaxMessageSizeBytes() {

        return this.maxMessageSizeBytes;
    }

    public void setMaxMessageSizeBytes(final int maxMessageSizeBytes) {

        this.maxMessageSizeBytes = maxMessageSizeBytes;
    }

    public int getCriticalEventQueueSize() {

        return this.criticalEventQueueSize;
    }

    public void setCriticalEventQueueSize(final int criticalEventQueueSize) {

        this.criticalEventQueueSize = criticalEventQueueSize;
    }

    public long getEventExecutionWarningDelay() {

        return this.eventExecutionWarningDelay;
    }

    public void setEventExecutionWarningDelay(final long eventExecutionWarningDelay) {

        this.eventExecutionWarningDelay = eventExecutionWarningDelay;
    }

    public long getEventExecutionErrorDelay() {

        return this.eventExecutionErrorDelay;
    }

    public void setEventExecutionErrorDelay(final long eventExecutionErrorDelay) {

        this.eventExecutionErrorDelay = eventExecutionErrorDelay;
    }

    public int getEventExecutionDelayCheckEveryNTimesWarning() {

        return this.eventExecutionDelayCheckEveryNTimesWarning;
    }

    public void setEventExecutionDelayCheckEveryNTimesWarning(final int eventExecutionDelayCheckEveryNTimesWarning) {

        this.eventExecutionDelayCheckEveryNTimesWarning = eventExecutionDelayCheckEveryNTimesWarning;
    }

    public int getEventExecutionDelayCheckEveryNTimesError() {

        return this.eventExecutionDelayCheckEveryNTimesError;
    }

    public void setEventExecutionDelayCheckEveryNTimesError(final int eventExecutionDelayCheckEveryNTimesError) {

        this.eventExecutionDelayCheckEveryNTimesError = eventExecutionDelayCheckEveryNTimesError;
    }

    public long getSendCompletionWarningDelay() {

        return this.sendCompletionWarningDelay;
    }

    public void setSendCompletionWarningDelay(final long sendCompletionWarningDelay) {

        this.sendCompletionWarningDelay = sendCompletionWarningDelay;
    }

    public long getSendCompletionErrorDelay() {

        return this.sendCompletionErrorDelay;
    }

    public void setSendCompletionErrorDelay(final long sendCompletionErrorDelay) {

        this.sendCompletionErrorDelay = sendCompletionErrorDelay;
    }

    public int getSendCompletionDelayCheckEveryNTimesWarning() {

        return this.sendCompletionDelayCheckEveryNTimesWarning;
    }

    public void setSendCompletionDelayCheckEveryNTimesWarning(final int sendCompletionDelayCheckEveryNTimesWarning) {

        this.sendCompletionDelayCheckEveryNTimesWarning = sendCompletionDelayCheckEveryNTimesWarning;
    }

    public int getSendCompletionDelayCheckEveryNTimesError() {

        return this.sendCompletionDelayCheckEveryNTimesError;
    }

    public void setSendCompletionDelayCheckEveryNTimesError(final int sendCompletionDelayCheckEveryNTimesError) {

        this.sendCompletionDelayCheckEveryNTimesError = sendCompletionDelayCheckEveryNTimesError;
    }

    public FeedbackEventsConcurrencyPolicy getConcurrencyPolicy() {

        return this.concurrencyPolicy;
    }

    public void setConcurrencyPolicy(final FeedbackEventsConcurrencyPolicy concurrencyPolicy) {

        this.concurrencyPolicy = concurrencyPolicy;
    }

    public SecurityExceptionHandler getSecurityExceptionHandler() {

        return this.securityExceptionHandler;
    }

    public void setSecurityExceptionHandler(final SecurityExceptionHandler securityExceptionHandler) {

        this.securityExceptionHandler = securityExceptionHandler;
    }

    private void checkAndSetTransportName() {

        if (this.transportName == null) {
            this.transportName = "NettyTransportClient-" + defaultTransportCounter.getAndIncrement();
        }

    }

    public synchronized void connect() {

        this.checkAndSetTransportName();
        TransportClientSession clientSession;
        if (this.transportClientSession != null) {
            if (this.transportClientSession.isOnline() || this.transportClientSession.isConnecting()) {
                return;
            }

            clientSession = this.transportClientSession;
            this.transportClientSession = null;
            clientSession.terminate();
        }

        if (this.address == null) {
            throw new NullPointerException(String.format("[%s] address is not set", this.transportName));
        }

        if (this.listeners == null || this.listeners.isEmpty()) {
            throw new NullPointerException(String.format("[%s] ClientListeners are not set", this.transportName));
        }

        if (this.authorizationProvider == null) {
            throw new NullPointerException(String.format("[%s] ClientAuthorizationProvider is not set",
                                                         this.transportName));
        }

        if (this.concurrencyPolicy == null) {
            throw new NullPointerException(String.format("[%s] FeedbackEventsConcurrencyPolicy is not set",
                                                         this.transportName));
        }

        if (this.staticSessionDictionary == null) {
            throw new NullPointerException(String.format("[%s] staticSessionDictionary is not set", this.transportName));
        }

        if (this.maxSubsequentPingFailedCount <= 0L) {
            throw new IllegalArgumentException("maxSubsequentPingFailedCount must be greater than zero");
        }

        this.authorizationProvider.setUserAgent(this.userAgent);
        this.authorizationProvider.setSecondaryConnectionDisabled(!this.useFeederSocket);
        this.authorizationProvider.setDroppableMessageServerTTL(this.droppableMessageServerTTL);
        this.droppedMessageCounter.set(0L);

        clientSession = new TransportClientSession(this,
                                                   this.address.toInetSocketAddress(),
                                                   this.authorizationProvider,
                                                   this.listeners,
                                                   this.useSSL,
                                                   this.sslHandshakeTimeout,
                                                   this.protocolVersionNegotiationTimeout,
                                                   this.useFeederSocket,
                                                   this.connectionTimeout,
                                                   this.authorizationTimeout,
                                                   this.transportPoolSize,
                                                   this.eventPoolSize,
                                                   this.eventPoolAutoCleanupInterval,
                                                   this.authEventPoolSize,
                                                   this.authEventPoolAutoCleanupInterval,
                                                   this.criticalAuthEventQueueSize,
                                                   this.primaryConnectionPingInterval,
                                                   this.secondaryConnectionPingInterval,
                                                   this.primaryConnectionPingTimeout,
                                                   this.secondaryConnectionPingTimeout,
                                                   this.secondaryConnectionReconnectAttempts,
                                                   this.secondaryConnectionReconnectsResetDelay,
                                                   this.droppableMessageServerTTL,
                                                   this.droppableMessageClientTTL,
                                                   this.skipDroppableMessages,
                                                   this.logSkippedDroppableMessages,
                                                   this.channelOptions,
                                                   this.transportName,
                                                   this.reconnectDelay,
                                                   this.maxMessageSizeBytes,
                                                   this.criticalEventQueueSize,
                                                   this.eventExecutionWarningDelay,
                                                   this.eventExecutionErrorDelay,
                                                   this.eventExecutionDelayCheckEveryNTimesWarning,
                                                   this.eventExecutionDelayCheckEveryNTimesError,
                                                   this.sendCompletionWarningDelay,
                                                   this.sendCompletionErrorDelay,
                                                   this.sendCompletionDelayCheckEveryNTimesWarning,
                                                   this.sendCompletionDelayCheckEveryNTimesError,
                                                   this.concurrencyPolicy,
                                                   this.securityExceptionHandler,
                                                   this.staticSessionDictionary,
                                                   this.sessionStats,
                                                   this.pingListener,
                                                   this.syncMessageTimeout,
                                                   this.duplicateSyncMessagesToClientListeners,
                                                   this.droppedMessageCounter,
                                                   this.logEventPoolThreadDumpsOnLongExecution,
                                                   this.streamListener,
                                                   this.streamChunkProcessingTimeout,
                                                   this.streamBufferSize,
                                                   this.streamProcessingPoolSize,
                                                   this.streamProcessingPoolAutoCleanupInterval,
                                                   this.criticalStreamProcessingQueueSize,
                                                   this.sendCpuInfoToServer,
                                                   this.syncRequestProcessingPoolSize,
                                                   this.syncRequestProcessingPoolAutoCleanupInterval,
                                                   this.criticalSyncRequestProcessingQueueSize,
                                                   this.terminationMaxAwaitTimeoutInMillis,
                                                   this.maxSubsequentPingFailedCount,
                                                   this.transportClientSessionStateHandler,
                                                   this.eventPoolTerminationTimeUnitCount,
                                                   this.eventPoolTerminationTimeUnit,
                                                   this.authEventPoolTerminationTimeUnitCount,
                                                   this.authEventPoolTerminationTimeUnit,
                                                   this.streamProcessingPoolTerminationTimeUnitCount,
                                                   this.streamProcessingPoolTerminationTimeUnit,
                                                   this.syncRequestProcessingPoolTerminationTimeUnitCount,
                                                   this.syncRequestProcessingPoolTerminationTimeUnit,
                                                   this.enabledSslProtocols);

        try {
            clientSession.init();
            clientSession.connect();
            this.transportClientSession = clientSession;
        } catch (final Throwable t) {
            clientSession.terminate();
            throw t;
        }


    }

    public String getTransportSessionId() {

        final TransportClientSession transportClientSession = this.transportClientSession;
        return transportClientSession != null ? transportClientSession.getServerSessionId() : null;
    }

    public synchronized void disconnect() {

        if (this.transportClientSession != null) {
            final TransportClientSession transportClientSessionLocal = this.transportClientSession;
            this.transportClientSession = null;
            transportClientSessionLocal.disconnect();
        }

    }

    public boolean isOnline() {

        final TransportClientSession transportClientSessionLocal = this.transportClientSession;
        return transportClientSessionLocal != null && transportClientSessionLocal.isOnline();
    }

    public boolean isConnecting() {

        final TransportClientSession transportClientSessionLocal = this.transportClientSession;
        return transportClientSessionLocal != null && transportClientSessionLocal.isConnecting();
    }

    public synchronized void terminate() {

        this.isTerminated.set(true);
        if (this.transportClientSession != null) {
            final TransportClientSession transportClientSessionLocal = this.transportClientSession;
            this.transportClientSession = null;
            transportClientSessionLocal.terminate();
        }

        this.listeners.clear();
        if (this.needJmxBean) {


            try {
                final MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
                mbs.unregisterMBean(new ObjectName(this.checkAndGetJmxName()));
            } catch (InstanceNotFoundException | MalformedObjectNameException | MBeanRegistrationException e) {
                LOGGER.error("Error unregistered server MBean: ", e);
            }

        }

    }

    public boolean sendMessageNaive(final ProtocolMessage message) {

        final TransportClientSession transportClientSessionLocal = this.transportClientSession;
        if (transportClientSessionLocal != null) {
            return transportClientSessionLocal.sendMessageNaive(message);
        } else {
            LOGGER.error("[{}] TransportClient not connected, message: {}", this.transportName, message);
            return false;
        }
    }

    public ProtocolMessage sendRequest(final ProtocolMessage message, final long timeout, final TimeUnit timeoutUnits)
        throws InterruptedException, TimeoutException, ConnectException, ExecutionException {

        final TransportClientSession transportClientSessionLocal = this.transportClientSession;
        if (transportClientSessionLocal != null) {
            return transportClientSessionLocal.sendRequest(message, timeout, timeoutUnits);
        } else {
            final String exMessage = String.format("[%s] TransportClient not connected, message: %s",
                                                   this.transportName,
                                                   message.toString(PROTOCOL_MESSAGE_EXCEPTION_LENGTH));
            throw new ConnectException(exMessage);
        }
    }

    public <V> ListenableFuture<V> sendMessageAsync(final ProtocolMessage message) {

        final TransportClientSession transportClientSessionLocal = this.transportClientSession;
        if (transportClientSessionLocal != null) {
            return transportClientSessionLocal.sendMessageAsync(message);
        } else {
            final String exMessage = String.format("[%s] TransportClient not connected, message: %s",
                                                   this.transportName,
                                                   message.toString(PROTOCOL_MESSAGE_EXCEPTION_LENGTH));
            return Futures.immediateFailedFuture(new ConnectException(exMessage));
        }
    }

    public <V> void sendMessageAsync(final ProtocolMessage message, final FutureCallback<V> callback) {

        this.sendMessageAsync(message, callback, this.asyncRequestFutureExecutor);
    }

    public <V> void sendMessageAsync(final ProtocolMessage message,
                                     final FutureCallback<V> callback,
                                     final Executor executor) {

        final ListenableFuture<V> future = this.sendMessageAsync(message);
        Futures.addCallback(future, callback, executor);
    }

    public RequestListenableFuture sendRequestAsync(final ProtocolMessage message) {

        final TransportClientSession transportClientSessionLocal = this.transportClientSession;
        return transportClientSessionLocal != null
               ? transportClientSessionLocal.sendRequestAsync(message)
               : this.createFailedFuture(message);
    }

    /**
     * @deprecated
     */
    @Deprecated
    public void controlRequest(final ProtocolMessage message, final MessageSentListener messageSentListener)
        throws IOException {

        final TransportClientSession transportClientSessionLocal = this.transportClientSession;
        if (transportClientSessionLocal != null) {
            transportClientSessionLocal.controlRequest(message, messageSentListener);
        } else {
            throw new IOException(String.format("[%s] TransportClient not connected",this.transportName));
        }
    }

    /**
     * @deprecated
     */
    @Deprecated
    public ProtocolMessage controlBlockingRequest(final ProtocolMessage message, final Long timeoutTime)
        throws InterruptedException, IOException, TimeoutException, ExecutionException {

        final TransportClientSession transportClientSessionLocal = this.transportClientSession;
        if (transportClientSessionLocal != null) {
            return transportClientSessionLocal.controlBlockingRequest(message, timeoutTime);
        } else {
            throw new IOException(String.format("[%s] TransportClient not connected",this.transportName));
        }
    }

    /**
     * @deprecated
     */
    @Deprecated
    public ProtocolMessage controlRequest(final ProtocolMessage message) {

        final TransportClientSession transportClientSessionLocal = this.transportClientSession;
        if (transportClientSessionLocal != null) {
            transportClientSessionLocal.controlRequest(message);
            return null;
        } else {
            throw new RuntimeException(String.format("[%s] TransportClient not connected",this.transportName));
        }
    }

    /**
     * @deprecated
     */
    @Deprecated
    public ProtocolMessage controlSynchRequest(final ProtocolMessage message, final Long timeoutTime)
        throws TimeoutException {

        final TransportClientSession transportClientSessionLocal = this.transportClientSession;
        if (transportClientSessionLocal != null) {
            return transportClientSessionLocal.controlSynchRequest(message, timeoutTime);
        } else {
            throw new RuntimeException(String.format("[%s] TransportClient not connected",this.transportName));
        }
    }

    RequestListenableFuture createFailedFuture(final ProtocolMessage message) {
        final String exMessage = String.format("[%s] TransportClient not connected, message: %s",
                                               this.transportName,
                                               message.toString(PROTOCOL_MESSAGE_EXCEPTION_LENGTH));
        final RequestMessageTransportListenableFuture
            task
            = new RequestMessageTransportListenableFuture(this.transportName, this.getNextId(), null, message);
        task.setException(new ConnectException(exMessage));
        return task;
    }

    public void exportInterfaceForRemoteCalls(final Class<?> interfaceClass, final Object interfaceImpl) {

        this.remoteCallSupport.exportInterface(interfaceClass, interfaceImpl);
    }

    public <T> T getExportedForRemoteCallsImplementation(final Class<T> interfaceClass) {

        return this.remoteCallSupport.getInterfaceImplementation(interfaceClass);
    }

    public <T> T getRemoteInterface(final Class<T> remoteInterfaceClass,
                                    final long timeout,
                                    final TimeUnit timeoutUnit) {

        final TransportClientSession transportClientSessionLocal = this.transportClientSession;
        if (transportClientSessionLocal != null) {
            return this.remoteCallSupport.getRemoteInterface(remoteInterfaceClass,
                                                             transportClientSessionLocal.getIoSessionWrapper(),
                                                             timeout,
                                                             timeoutUnit);
        } else {
            throw new RuntimeException(String.format("[%s] TransportClient not connected",this.transportName));
        }
    }

    long getNextId() {

        return this.requestId.incrementAndGet();
    }

    void tickReceived() {

        this.ticksCounter.incrementAndGet();
    }

    synchronized void disconnected() {

        if (this.transportClientSession != null) {
            this.transportClientSession = null;
        }

        this.remoteCallSupport.setSession(null);
    }

    RemoteCallSupport getRemoteCallSupport() {

        return this.remoteCallSupport;
    }

    public boolean isDebugMode() {

        return this.debugMode;
    }

    public void setDebugMode(final boolean debugMode) {

        this.debugMode = debugMode;
    }

    TransportClientSession getTransportClientSession() {

        return this.transportClientSession;
    }

    public void removeListener(final ClientListener listener) {

        if (listener != null) {
            this.listeners.remove(listener);
        }

    }

    public boolean isTerminated() {

        return this.isTerminated.get();
    }

    public AbstractStaticSessionDictionary getStaticSessionDictionary() {

        return this.staticSessionDictionary;
    }

    public void setStaticSessionDictionary(final AbstractStaticSessionDictionary staticSessionDictionary) {

        this.staticSessionDictionary = staticSessionDictionary;
    }

    public PingManager getPingManager(final boolean isPrimarySession) {

        return this.pingManagers.get(isPrimarySession);
    }

    public ISessionStats getSessionStats() {

        return this.sessionStats;
    }

    public void setSessionStats(final ISessionStats sessionStats) {

        this.sessionStats = sessionStats;
    }

    public long getDroppableMessagesClientTTL() {

        return this.droppableMessageClientTTL;
    }

    public boolean isSkipDroppableMessages() {

        return this.skipDroppableMessages;
    }

    public void setSkipDroppableMessages(final boolean skipDroppableMessages) {

        this.skipDroppableMessages = skipDroppableMessages;
        final TransportClientSession transportClientSessionLocal = this.transportClientSession;
        if (transportClientSessionLocal != null) {
            transportClientSessionLocal.setSkipDroppableMessages(skipDroppableMessages);
        }

    }

    public ServerAddress getAddress() {

        return this.address;
    }

    public void setAddress(final ServerAddress address) {

        this.address = address;
    }
}
