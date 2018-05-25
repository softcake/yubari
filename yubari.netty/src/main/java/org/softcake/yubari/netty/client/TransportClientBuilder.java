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

import org.softcake.yubari.netty.authorization.AnonymousClientAuthorizationProvider;
import org.softcake.yubari.netty.authorization.ClientAuthorizationProvider;
import org.softcake.yubari.netty.mina.ClientListener;
import org.softcake.yubari.netty.mina.SecurityExceptionHandler;
import org.softcake.yubari.netty.mina.ServerAddress;
import org.softcake.yubari.netty.stream.StreamListener;

import com.dukascopy.dds4.ping.IPingListener;
import com.dukascopy.dds4.transport.common.protocol.binary.AbstractStaticSessionDictionary;
import com.google.common.collect.ImmutableMap;
import io.netty.channel.ChannelOption;
import io.netty.channel.DefaultMessageSizeEstimator;
import io.netty.channel.WriteBufferWaterMark;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@SuppressWarnings("unchecked")
public class TransportClientBuilder {
    static final int DEFAULT_TRANSPORT_POOL_SIZE = Math.max(Runtime.getRuntime()
                                                                          .availableProcessors() / 4, 4);
    static final int DEFAULT_EVENT_POOL_SIZE = Math.max(Runtime.getRuntime()
                                                                      .availableProcessors() / 2, 10);
    static final String DEFAULT_JMX_BEAN_NAME = null;
    static final Map<ChannelOption<?>, Object> DEFAULT_CHANNEL_OPTIONS;
    static final Executor DEFAULT_ASYNC_REQUEST_FUTURE_EXECUTOR = Executors.newSingleThreadExecutor();
    static final int DEFAULT_STREAM_CHUNK_PROCESSING_TIMEOUT;
    static final TimeUnit DEFAULT_EVENT_POOL_TERMINATION_TIME_UNIT;
    static final TimeUnit DEFAULT_AUTH_EVENT_POOL_TERMINATION_TIME_UNIT;
    static final TimeUnit DEFAULT_STREAM_PROCESSING_POOL_TERMINATION_TIME_UNIT;
    static final TimeUnit DEFAULT_SYNC_REQUEST_PROCESSING_POOL_TERMINATION_TIME_UNIT;
    public static final Set<String> DEFAULT_SSL_PROTOCOLS;
    static final boolean DEFAULT_USE_FEEDER_SOCKET = true;
    static final long DEFAULT_CONNECTION_TIMEOUT = 15000L;
    static final long DEFAULT_AUTHORIZATION_TIMEOUT = 15000L;
    static final long DEFAULT_PING_INTERVAL = 15000L;
    static final long DEFAULT_PING_TIMEOUT = 10000L;
    static final int DEFAULT_CHILD_CONNECTION_RECONNECT_ATTEMPTS = 3;
    static final long DEFAULT_CHILD_CONNECTION_RECONNECTS_RESET_DELAY = 30000L;
    static final long DEFAULT_DROPPABLE_MESSAGE_SERVER_TTL = 0L;
    static final long DEFAULT_DROPPABLE_MESSAGE_CLIENT_TTL = 0L;
    static final boolean DEFAULT_SKIP_DROPPABLE_MESSAGES = true;
    static final boolean DEFAULT_USE_SSL = false;
    static final long DEFAULT_SSL_HANDSHAKE_TIMEOUT = 15000L;
    static final long DEFAULT_PROTOCOL_VERSION_NEGOTIATION_TIMEOUT = 15000L;
    static final long DEFAULT_EVENT_POOL_AUTO_CLEANUP_INTERVAL = 0L;
    static final int DEFAULT_AUTH_EVENT_POOL_SIZE = 1;
    static final long DEFAULT_AUTH_EVENT_POOL_AUTO_CLEANUP_INTERVAL = 0L;
    static final long DEFAULT_RECONNECT_DELAY = 10000L;
    static final int DEFAULT_MAX_MESSAGE_SIZE_BYTES = 10485760;
    static final int DEFAULT_CRITICAL_EVENT_QUEUE_SIZE = 50;
    static final int DEFAULT_CRITICAL_AUTH_EVENT_QUEUE_SIZE = 10;
    static final long DEFAULT_EVENT_EXECUTION_WARNING_DELAY = 100L;
    static final long DEFAULT_EVENT_EXECUTION_ERROR_DELAY = 1000L;
    static final int DEFAULT_EVENT_EXECUTION_DELAY_CHECK_EVERY_N_TIMES_WARNING = 31;
    static final int DEFAULT_EVENT_EXECUTION_DELAY_CHECK_EVERY_N_TIMES_ERROR = 10;
    static final long DEFAULT_SEND_COMPLETION_WARNING_DELAY = 100L;
    static final long DEFAULT_SEND_COMPLETION_ERROR_DELAY = 1000L;
    static final int DEFAULT_SEND_COMPLETION_DELAY_CHECK_EVERY_N_TIMES_WARNING = 31;
    static final int DEFAULT_SEND_COMPLETION_DELAY_CHECK_EVERY_N_TIMES_ERROR = 10;
    private static final String DEFAULT_USER_AGENT = "NettyTransportClient "
                                                     + TransportClient.getTransportVersion()
                                                     + " - "
                                                     + TransportClient.getLocalIpAddress()
                                                                     .getHostAddress();
    static final boolean DEFAULT_NEED_JMX_BEAN = true;
    static final long DEFAULT_SYNC_MESSAGE_TIMEOUT = 30000L;
    static final boolean DEFAULT_DUPLICATE_SYNC_MESSAGES_TO_CLIENT_LISTENERS = false;
    static final boolean DEFAULT_LOG_SKIPPED_DROPPABLE_MESSAGES = false;
    static final boolean DEFAULT_LOG_EVENT_POOL_THREAD_DUMPS_ON_LONG_EXECUTION = false;
    static final int DEFAULT_STREAM_BUFFER_SIZE = 2097152;
    static final int DEFAULT_STREAM_PROCESSING_POOL_SIZE = 5;
    static final long DEFAULT_STREAM_PROCESSING_POOL_AUTO_CLEANUP_INTERVAL = 0L;
    static final int DEFAULT_CRITICAL_STREAM_PROCESSING_QUEUE_SIZE = 100;
    static final boolean DEFAULT_SEND_CPU_INFO_TO_SERVER = false;
    static final int DEFAULT_SYNC_REQUEST_PROCESSING_POOL_SIZE = 1;
    static final long DEFAULT_SYNC_REQUEST_PROCESSING_POOL_AUTO_CLEANUP_INTERVAL = 0L;
    static final int DEFAULT_SYNC_REQUEST_PROCESSING_QUEUE_SIZE = 100;
    static final long DEFAULT_TERMINATION_MAX_AWAIT_TIMEOUT_IN_MILLIS = 5000L;
    static final long DEFAULT_MAX_SUBSEQUENT_PING_FAILED_COUNT = 3L;
    static final long DEFAULT_EVENT_POOL_TERMINATION_TIME_UNIT_COUNT = 2L;
    static final long DEFAULT_AUTH_EVENT_POOL_TERMINATION_TIME_UNIT_COUNT = 2L;
    static final long DEFAULT_STREAM_PROCESSING_POOL_TERMINATION_TIME_UNIT_COUNT = 2L;
    static final long DEFAULT_SYNC_REQUEST_PROCESSING_POOL_TERMINATION_TIME_UNIT_COUNT = 2L;
    private static final long MAX_PING_TIMEOUT = TimeUnit.HOURS.toMillis(1);
    private static final long MAX_PING_INTERVAL = TimeUnit.HOURS.toMillis(1);

    private static final String TLS_V_1 = "TLSv1";
    private static final String TLS_V_1_1 = "TLSv1.1";
    private static final String TLS_V_1_2 = "TLSv1.2";

    static {

        DEFAULT_STREAM_CHUNK_PROCESSING_TIMEOUT = (int) TimeUnit.SECONDS.toMillis(15L);
        DEFAULT_EVENT_POOL_TERMINATION_TIME_UNIT = TimeUnit.SECONDS;
        DEFAULT_AUTH_EVENT_POOL_TERMINATION_TIME_UNIT = TimeUnit.SECONDS;
        DEFAULT_STREAM_PROCESSING_POOL_TERMINATION_TIME_UNIT = TimeUnit.SECONDS;
        DEFAULT_SYNC_REQUEST_PROCESSING_POOL_TERMINATION_TIME_UNIT = TimeUnit.SECONDS;

        final Map<ChannelOption<?>, Object> defaultChannelOptions = new LinkedHashMap<>();
        defaultChannelOptions.put(ChannelOption.SO_REUSEADDR, true);
        defaultChannelOptions.put(ChannelOption.TCP_NODELAY, true);
        defaultChannelOptions.put(ChannelOption.SO_LINGER, -1);
        defaultChannelOptions.put(ChannelOption.CONNECT_TIMEOUT_MILLIS, 15000);
        defaultChannelOptions.put(ChannelOption.WRITE_BUFFER_WATER_MARK, new WriteBufferWaterMark(5120, 10240));
        defaultChannelOptions.put(ChannelOption.MESSAGE_SIZE_ESTIMATOR, new DefaultMessageSizeEstimator(150));
        DEFAULT_CHANNEL_OPTIONS = ImmutableMap.copyOf(defaultChannelOptions);
        final Set<String> sslProtocols = new LinkedHashSet<>();
        sslProtocols.add(TLS_V_1);
        sslProtocols.add(TLS_V_1_1);
        sslProtocols.add(TLS_V_1_2);
        DEFAULT_SSL_PROTOCOLS = Collections.unmodifiableSet(sslProtocols);
    }

    private final Map<ChannelOption<?>, Object> channelOptions;
    private ServerAddress address;
    private ClientAuthorizationProvider authorizationProvider = new AnonymousClientAuthorizationProvider();
    private CopyOnWriteArrayList<ClientListener> listeners;
    private boolean useSSL = DEFAULT_USE_SSL;
    private long sslHandshakeTimeout = DEFAULT_SSL_HANDSHAKE_TIMEOUT;
    private long protocolVersionNegotiationTimeout = DEFAULT_PROTOCOL_VERSION_NEGOTIATION_TIMEOUT;
    private boolean useFeederSocket = DEFAULT_USE_FEEDER_SOCKET;
    private long connectionTimeout = DEFAULT_CONNECTION_TIMEOUT;
    private long authorizationTimeout = DEFAULT_AUTHORIZATION_TIMEOUT;
    private int transportPoolSize;
    private int eventPoolSize;
    private long eventPoolAutoCleanupInterval;
    private int authEventPoolSize;
    private long authEventPoolAutoCleanupInterval;
    private int criticalAuthEventQueueSize;
    private long primaryConnectionPingInterval;
    private long childConnectionPingInterval;
    private long primaryConnectionPingTimeout;
    private long childConnectionPingTimeout;
    private int childConnectionReconnectAttempts;
    private long childConnectionReconnectsResetDelay;
    private long droppableMessageServerTTL;
    private long droppableMessageClientTTL;
    private boolean skipDroppableMessages;
    private boolean logSkippedDroppableMessages;
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
    private SecurityExceptionHandler securityExceptionHandler;
    private AbstractStaticSessionDictionary staticSessionDictionary;
    private boolean needJmxBean;
    private String jmxBeanName;
    private Executor asyncRequestFutureExecutor;
    private IPingListener pingListener;
    private long syncMessageTimeout;
    private boolean duplicateSyncMessagesToClientListeners;
    private AtomicBoolean logEventPoolThreadDumpsOnLongExecution;
    private StreamListener streamListener;
    private int streamChunkProcessingTimeout;
    private int streamBufferSize;
    private int streamProcessingPoolSize;
    private long streamProcessingPoolAutoCleanupInterval;
    private int criticalStreamProcessingQueueSize;
    private boolean sendCpuInfoToServer;
    private int syncRequestProcessingPoolSize;
    private long syncRequestProcessingPoolAutoCleanupInterval;
    private int criticalSyncRequestProcessingQueueSize;
    private long terminationMaxAwaitTimeoutInMillis;
    private long maxSubsequentPingFailedCount;

    private long eventPoolTerminationTimeUnitCount;
    private TimeUnit eventPoolTerminationTimeUnit;
    private long authEventPoolTerminationTimeUnitCount;
    private TimeUnit authEventPoolTerminationTimeUnit;
    private long streamProcessingPoolTerminationTimeUnitCount;
    private TimeUnit streamProcessingPoolTerminationTimeUnit;
    private long syncRequestProcessingPoolTerminationTimeUnitCount;
    private TimeUnit syncRequestProcessingPoolTerminationTimeUnit;
    private Set<String> enabledSslProtocols;

    TransportClientBuilder() {

        this.transportPoolSize = DEFAULT_TRANSPORT_POOL_SIZE;
        this.eventPoolSize = DEFAULT_EVENT_POOL_SIZE;
        this.eventPoolAutoCleanupInterval = DEFAULT_EVENT_POOL_AUTO_CLEANUP_INTERVAL;
        this.authEventPoolSize = DEFAULT_AUTH_EVENT_POOL_SIZE;
        this.authEventPoolAutoCleanupInterval = DEFAULT_AUTH_EVENT_POOL_AUTO_CLEANUP_INTERVAL;
        this.criticalAuthEventQueueSize = DEFAULT_CRITICAL_AUTH_EVENT_QUEUE_SIZE;
        this.primaryConnectionPingInterval = DEFAULT_PING_INTERVAL;
        this.childConnectionPingInterval = DEFAULT_PING_INTERVAL;
        this.primaryConnectionPingTimeout = DEFAULT_PING_TIMEOUT;
        this.childConnectionPingTimeout = DEFAULT_PING_TIMEOUT;
        this.childConnectionReconnectAttempts = DEFAULT_CHILD_CONNECTION_RECONNECT_ATTEMPTS;
        this.childConnectionReconnectsResetDelay = DEFAULT_CHILD_CONNECTION_RECONNECTS_RESET_DELAY;
        this.droppableMessageServerTTL = DEFAULT_DROPPABLE_MESSAGE_SERVER_TTL;
        this.droppableMessageClientTTL = DEFAULT_DROPPABLE_MESSAGE_CLIENT_TTL;
        this.skipDroppableMessages = DEFAULT_SKIP_DROPPABLE_MESSAGES;
        this.logSkippedDroppableMessages = DEFAULT_LOG_SKIPPED_DROPPABLE_MESSAGES;
        this.channelOptions = new LinkedHashMap<>(DEFAULT_CHANNEL_OPTIONS);
        this.userAgent = DEFAULT_USER_AGENT;
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

        this.needJmxBean = DEFAULT_NEED_JMX_BEAN;
        this.jmxBeanName = DEFAULT_JMX_BEAN_NAME;
        this.asyncRequestFutureExecutor = DEFAULT_ASYNC_REQUEST_FUTURE_EXECUTOR;
        this.syncMessageTimeout = DEFAULT_SYNC_MESSAGE_TIMEOUT;
        this.duplicateSyncMessagesToClientListeners = DEFAULT_DUPLICATE_SYNC_MESSAGES_TO_CLIENT_LISTENERS;
        this.logEventPoolThreadDumpsOnLongExecution = new AtomicBoolean(
            DEFAULT_LOG_EVENT_POOL_THREAD_DUMPS_ON_LONG_EXECUTION);
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
    }

    public ServerAddress getAddress() {

        return this.address;
    }

    public TransportClientBuilder withAddress(final ServerAddress address) {

        this.address = address;
        return this;
    }

    public TransportClientBuilder withAuthorizationProvider(final ClientAuthorizationProvider authorizationProvider) {

        this.authorizationProvider = authorizationProvider;
        return this;
    }

    public TransportClientBuilder withClientListener(final ClientListener clientListener) {

        if (this.listeners == null) {
            this.listeners = new CopyOnWriteArrayList<>();
        }

        this.listeners.add(clientListener);
        return this;
    }

    public TransportClientBuilder withClientListeners(final List<ClientListener> clientListeners) {

        this.listeners = new CopyOnWriteArrayList<>(clientListeners);
        return this;
    }

    public TransportClientBuilder withFeederSocket(final boolean useFeederSocket) {

        this.useFeederSocket = useFeederSocket;
        return this;
    }

    public TransportClientBuilder withConnectionTimeout(final long connectionTimeout) {

        this.connectionTimeout = connectionTimeout;
        this.channelOptions.put(ChannelOption.CONNECT_TIMEOUT_MILLIS, (int) connectionTimeout);
        return this;
    }

    public TransportClientBuilder withAuthorizationTimeout(final long authorizationTimeout) {

        this.authorizationTimeout = authorizationTimeout;
        return this;
    }

    public TransportClientBuilder withTransportPoolSize(final int transportPoolSize) {

        this.transportPoolSize = transportPoolSize;
        return this;
    }

    public TransportClientBuilder withEventPoolSize(final int eventPoolSize) {

        this.eventPoolSize = eventPoolSize;
        return this;
    }

    public TransportClientBuilder withEventPoolAutoCleanupInterval(final long eventPoolAutoCleanupInterval) {

        this.eventPoolAutoCleanupInterval = eventPoolAutoCleanupInterval;
        return this;
    }

    public TransportClientBuilder withPrimaryConnectionPingInterval(long primaryConnectionPingInterval) {

        if (primaryConnectionPingInterval < 1L || primaryConnectionPingInterval > MAX_PING_INTERVAL) {
            primaryConnectionPingInterval = 0L;
        }

        this.primaryConnectionPingInterval = primaryConnectionPingInterval;
        return this;
    }

    public TransportClientBuilder withChildConnectionPingInterval(long childConnectionPingInterval) {

        if (childConnectionPingInterval < 1L || childConnectionPingInterval > MAX_PING_INTERVAL) {
            childConnectionPingInterval = 0L;
        }

        this.childConnectionPingInterval = childConnectionPingInterval;
        return this;
    }

    public TransportClientBuilder withPrimaryConnectionPingTimeout(long primaryConnectionPingTimeout) {

        if (primaryConnectionPingTimeout < 1L || primaryConnectionPingTimeout > MAX_PING_TIMEOUT) {
            primaryConnectionPingTimeout = 0L;
        }

        this.primaryConnectionPingTimeout = primaryConnectionPingTimeout;
        return this;
    }

    public TransportClientBuilder withChildConnectionPingTimeout(long childConnectionPingTimeout) {

        if (childConnectionPingTimeout < 1L || childConnectionPingTimeout > MAX_PING_TIMEOUT) {
            childConnectionPingTimeout = 0L;
        }

        this.childConnectionPingTimeout = childConnectionPingTimeout;
        return this;
    }

    public TransportClientBuilder withChildConnectionReconnectAttempts(final int
                                                                           childConnectionReconnectAttempts) {

        if (childConnectionReconnectAttempts < 0) {
            throw new IllegalArgumentException(
                "Child connection reconnect attempts can't be less than 0. If you want to disable reconnects set "
                + "it to 0");
        } else {
            this.childConnectionReconnectAttempts = childConnectionReconnectAttempts;
            return this;
        }
    }

    public TransportClientBuilder withChildConnectionReconnectsResetDelay(final long
                                                                              childConnectionReconnectsResetDelay) {

        this.childConnectionReconnectsResetDelay = childConnectionReconnectsResetDelay;
        return this;
    }

    public TransportClientBuilder withDroppableMessageServerTTL(final long droppableMessageServerTTL) {

        this.droppableMessageServerTTL = droppableMessageServerTTL;
        return this;
    }

    public <T> TransportClientBuilder withChannelOption(final ChannelOption<T> channelOption, final T value) {

        this.channelOptions.put(channelOption, value);
        return this;
    }

    public TransportClientBuilder withUseSSL(final boolean useSSL) {

        this.useSSL = useSSL;
        return this;
    }

    public TransportClientBuilder withSSLHandshakeTimeout(final long sslHandshakeTimeout) {

        this.sslHandshakeTimeout = sslHandshakeTimeout;
        return this;
    }

    public TransportClientBuilder withProtocolVersionNegotiationTimeout(final long protocolVersionNegotiationTimeout) {

        this.protocolVersionNegotiationTimeout = protocolVersionNegotiationTimeout;
        return this;
    }

    public TransportClientBuilder withTransportName(final String transportName) {

        this.transportName = transportName;
        return this;
    }

    public TransportClientBuilder withReconnectDelay(final long reconnectDelay) {

        this.reconnectDelay = reconnectDelay;
        return this;
    }

    public TransportClientBuilder withMaxMessageSizeBytes(final int maxMessageSizeBytes) {

        this.maxMessageSizeBytes = maxMessageSizeBytes;
        return this;
    }

    public TransportClientBuilder withCriticalEventQueueSize(final int criticalEventQueueSize) {

        this.criticalEventQueueSize = criticalEventQueueSize;
        return this;
    }

    public TransportClientBuilder withEventExecutionWarningDelay(final int eventExecutionWarningDelay) {

        this.eventExecutionWarningDelay = (long) eventExecutionWarningDelay;
        return this;
    }

    public TransportClientBuilder withEventExecutionErrorDelay(final int eventExecutionErrorDelay) {

        this.eventExecutionErrorDelay = (long) eventExecutionErrorDelay;
        return this;
    }

    public TransportClientBuilder withEventExecutionDelayCheckEveryNTimesWarning(final int
                                                                                     eventExecutionDelayCheckEveryNTimesWarning) {

        this.eventExecutionDelayCheckEveryNTimesWarning = eventExecutionDelayCheckEveryNTimesWarning;
        return this;
    }

    public TransportClientBuilder withEventExecutionDelayCheckEveryNTimesError(final int
                                                                                   eventExecutionDelayCheckEveryNTimesError) {

        this.eventExecutionDelayCheckEveryNTimesError = eventExecutionDelayCheckEveryNTimesError;
        return this;
    }

    public TransportClientBuilder withSendCompletionWarningDelay(final int sendCompletionWarningDelay) {

        this.sendCompletionWarningDelay = (long) sendCompletionWarningDelay;
        return this;
    }

    public TransportClientBuilder withSendCompletionErrorDelay(final int sendCompletionErrorDelay) {

        this.sendCompletionErrorDelay = (long) sendCompletionErrorDelay;
        return this;
    }

    public TransportClientBuilder withSendCompletionDelayCheckEveryNTimesWarning(final int
                                                                                     sendCompletionDelayCheckEveryNTimesWarning) {

        this.sendCompletionDelayCheckEveryNTimesWarning = sendCompletionDelayCheckEveryNTimesWarning;
        return this;
    }

    public TransportClientBuilder withSendCompletionDelayCheckEveryNTimesError(final int
                                                                                   sendCompletionDelayCheckEveryNTimesError) {

        this.sendCompletionDelayCheckEveryNTimesError = sendCompletionDelayCheckEveryNTimesError;
        return this;
    }



    public TransportClientBuilder withSecurityExceptionHandler(final SecurityExceptionHandler
                                                                   securityExceptionHandler) {

        this.securityExceptionHandler = securityExceptionHandler;
        return this;
    }

    public TransportClientBuilder withStaticSessionDictionary(final AbstractStaticSessionDictionary
                                                                  staticSessionDictionary) {

        this.staticSessionDictionary = staticSessionDictionary;
        return this;
    }


    public TransportClientBuilder withUserAgent(final String userAgent) {

        this.userAgent = userAgent;
        return this;
    }

    public TransportClientBuilder withNeedJmxBean(final boolean needJmxBean) {

        this.needJmxBean = needJmxBean;
        return this;
    }

    public TransportClientBuilder withJmxBeanName(final String jmxBeanName) {

        this.jmxBeanName = jmxBeanName;
        return this;
    }

    public TransportClientBuilder withAsyncRequestFutureExecutor(final Executor asyncRequestFutureExecutor) {

        this.asyncRequestFutureExecutor = asyncRequestFutureExecutor;
        return this;
    }

    public TransportClient build() {

        if (this.listeners == null) {
            this.listeners = new CopyOnWriteArrayList<>();
        }

        return new TransportClient(this.address,
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
                                   this.childConnectionPingInterval,
                                   this.primaryConnectionPingTimeout,
                                   this.childConnectionPingTimeout,
                                   this.childConnectionReconnectAttempts,
                                   this.childConnectionReconnectsResetDelay,
                                   this.droppableMessageServerTTL,
                                   this.droppableMessageClientTTL,
                                   this.skipDroppableMessages,
                                   this.logSkippedDroppableMessages,
                                   this.channelOptions,
                                   this.userAgent,
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

                                   this.securityExceptionHandler,
                                   this.staticSessionDictionary,
                                   this.needJmxBean,
                                   this.jmxBeanName,
                                   this.asyncRequestFutureExecutor,
                                   this.pingListener,
                                   this.syncMessageTimeout,
                                   this.duplicateSyncMessagesToClientListeners,
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
                                   this.eventPoolTerminationTimeUnitCount,
                                   this.eventPoolTerminationTimeUnit,
                                   this.authEventPoolTerminationTimeUnitCount,
                                   this.authEventPoolTerminationTimeUnit,
                                   this.streamProcessingPoolTerminationTimeUnitCount,
                                   this.streamProcessingPoolTerminationTimeUnit,
                                   this.syncRequestProcessingPoolTerminationTimeUnitCount,
                                   this.syncRequestProcessingPoolTerminationTimeUnit,
                                   this.enabledSslProtocols);
    }

    public String toString() {

        final StringBuilder builder = new StringBuilder();
        builder.append("TransportClientBuilder [");
        if (this.address != null) {
            builder.append("address=");
            builder.append(this.address);
            builder.append(", ");
        }

        if (this.userAgent != null) {
            builder.append("userAgent=");
            builder.append(this.userAgent);
            builder.append(", ");
        }

        if (this.transportName != null) {
            builder.append("transportName=");
            builder.append(this.transportName);
        }

        builder.append("]");
        return builder.toString();
    }

    public ClientAuthorizationProvider getAuthorizationProvider() {

        return this.authorizationProvider;
    }

    public CopyOnWriteArrayList<ClientListener> getListeners() {

        return this.listeners;
    }

    public boolean isUseSSL() {

        return this.useSSL;
    }

    public long getSslHandshakeTimeout() {

        return this.sslHandshakeTimeout;
    }

    public long getProtocolVersionNegotiationTimeout() {

        return this.protocolVersionNegotiationTimeout;
    }

    public boolean isUseFeederSocket() {

        return this.useFeederSocket;
    }

    public long getConnectionTimeout() {

        return this.connectionTimeout;
    }

    public long getAuthorizationTimeout() {

        return this.authorizationTimeout;
    }

    public int getTransportPoolSize() {

        return this.transportPoolSize;
    }

    public int getEventPoolSize() {

        return this.eventPoolSize;
    }

    public long getEventPoolAutoCleanupInterval() {

        return this.eventPoolAutoCleanupInterval;
    }

    public long getPrimaryConnectionPingInterval() {

        return this.primaryConnectionPingInterval;
    }

    public long getChildConnectionPingInterval() {

        return this.childConnectionPingInterval;
    }

    public long getPrimaryConnectionPingTimeout() {

        return this.primaryConnectionPingTimeout;
    }

    public long getChildConnectionPingTimeout() {

        return this.childConnectionPingTimeout;
    }

    public int getChildConnectionReconnectAttempts() {

        return this.childConnectionReconnectAttempts;
    }

    public long getChildConnectionReconnectsResetDelay() {

        return this.childConnectionReconnectsResetDelay;
    }

    public long getDroppableMessagesServerTTL() {

        return this.droppableMessageServerTTL;
    }

    public Map<ChannelOption<?>, Object> getChannelOptions() {

        return this.channelOptions;
    }

    public String getUserAgent() {

        return this.userAgent;
    }

    public String getTransportName() {

        return this.transportName;
    }

    public long getReconnectDelay() {

        return this.reconnectDelay;
    }

    public int getMaxMessageSizeBytes() {

        return this.maxMessageSizeBytes;
    }

    public int getCriticalEventQueueSize() {

        return this.criticalEventQueueSize;
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

    public int getSendCompletionDelayCheckEveryNTimesWarning() {

        return this.sendCompletionDelayCheckEveryNTimesWarning;
    }

    public int getSendCompletionDelayCheckEveryNTimesError() {

        return this.sendCompletionDelayCheckEveryNTimesError;
    }


    public SecurityExceptionHandler getSecurityExceptionHandler() {

        return this.securityExceptionHandler;
    }

    public AbstractStaticSessionDictionary getStaticSessionDictionary() {

        return this.staticSessionDictionary;
    }

    public boolean isNeedJmxBean() {

        return this.needJmxBean;
    }

    public String getJmxBeanName() {

        return this.jmxBeanName;
    }

    public IPingListener getPingListener() {

        return this.pingListener;
    }

    public TransportClientBuilder withPingListener(final IPingListener pingListener) {

        this.pingListener = pingListener;
        return this;
    }

    public long getSyncMessageTimeout() {

        return this.syncMessageTimeout;
    }

    public TransportClientBuilder withSyncMessageTimeout(final long syncMessageTimeout) {

        this.syncMessageTimeout = syncMessageTimeout;
        return this;
    }

    public boolean isDuplicateSyncMessagesToClientListeners() {

        return this.duplicateSyncMessagesToClientListeners;
    }

    public TransportClientBuilder withDuplicateSyncMessagesToClientListeners(final boolean
                                                                                 duplicateSyncMessagesToClientListeners) {

        this.duplicateSyncMessagesToClientListeners = duplicateSyncMessagesToClientListeners;
        return this;
    }

    public long getDroppableMessagesClientTTL() {

        return this.droppableMessageClientTTL;
    }

    public TransportClientBuilder withDroppableMessageClientTTL(final long droppableMessageClientTTL) {

        this.droppableMessageClientTTL = droppableMessageClientTTL;
        return this;
    }

    public boolean isSkipDroppableMessages() {

        return this.skipDroppableMessages;
    }

    public TransportClientBuilder withSkipDroppableMessages(final boolean skipDroppableMessages) {

        this.skipDroppableMessages = skipDroppableMessages;
        return this;
    }

    public boolean isLogSkippedDroppableMessages() {

        return this.logSkippedDroppableMessages;
    }

    public TransportClientBuilder withLogSkippedDroppableMessages(final boolean logSkippedDroppableMessages) {

        this.logSkippedDroppableMessages = logSkippedDroppableMessages;
        return this;
    }

    public int getAuthEventPoolSize() {

        return this.authEventPoolSize;
    }

    public TransportClientBuilder withAuthEventPoolSize(final int authEventPoolSize) {

        this.authEventPoolSize = authEventPoolSize;
        return this;
    }

    public long getAuthEventPoolAutoCleanupInterval() {

        return this.authEventPoolAutoCleanupInterval;
    }

    public TransportClientBuilder withAuthEventPoolAutoCleanup(final long authEventPoolAutoCleanupInterval) {

        this.authEventPoolAutoCleanupInterval = authEventPoolAutoCleanupInterval;
        return this;
    }

    public int getCriticalAuthEventQueueSize() {

        return this.criticalAuthEventQueueSize;
    }

    public TransportClientBuilder withCriticalAuthEventQueueSize(final int criticalAuthEventQueueSize) {

        this.criticalAuthEventQueueSize = criticalAuthEventQueueSize;
        return this;
    }

    public AtomicBoolean getLogEventPoolThreadDumpsOnLongExecution() {

        return this.logEventPoolThreadDumpsOnLongExecution;
    }

    public TransportClientBuilder withLogEventPoolThreadDumpsOnLongExecution(final AtomicBoolean
                                                                                 logEventPoolThreadDumpsOnLongExecution) {

        this.logEventPoolThreadDumpsOnLongExecution = logEventPoolThreadDumpsOnLongExecution;
        return this;
    }

    public StreamListener getStreamListener() {

        return this.streamListener;
    }

    public TransportClientBuilder withStreamListener(final StreamListener streamListener) {

        this.streamListener = streamListener;
        return this;
    }

    public int getStreamChunkProcessingTimeout() {

        return this.streamChunkProcessingTimeout;
    }

    public TransportClientBuilder withStreamChunkProcessingTimeout(final int streamChunkProcessingTimeout) {

        this.streamChunkProcessingTimeout = streamChunkProcessingTimeout;
        return this;
    }

    public int getStreamBufferSize() {

        return this.streamBufferSize;
    }

    public TransportClientBuilder withStreamBufferSize(final int streamBufferSize) {

        this.streamBufferSize = streamBufferSize;
        return this;
    }

    public int getStreamProcessingPoolSize() {

        return this.streamProcessingPoolSize;
    }

    public TransportClientBuilder withStreamProcessingPoolSize(final int streamProcessingPoolSize) {

        this.streamProcessingPoolSize = streamProcessingPoolSize;
        return this;
    }

    public long getStreamProcessingPoolAutoCleanup() {

        return this.streamProcessingPoolAutoCleanupInterval;
    }

    public TransportClientBuilder withStreamProcessingPoolAutoCleanupInterval(final long
                                                                                  streamProcessingPoolAutoCleanupInterval) {

        this.streamProcessingPoolAutoCleanupInterval = streamProcessingPoolAutoCleanupInterval;
        return this;
    }

    public int getCriticalStreamProcessingQueueSize() {

        return this.criticalStreamProcessingQueueSize;
    }

    public TransportClientBuilder withetCriticalStreamProcessingQueueSize(final int criticalStreamProcessingQueueSize) {

        this.criticalStreamProcessingQueueSize = criticalStreamProcessingQueueSize;
        return this;
    }

    public boolean isSendCpuInfoToServer() {

        return this.sendCpuInfoToServer;
    }

    public TransportClientBuilder withSendCpuInfoToServer(final boolean sendCpuInfoToServer) {

        this.sendCpuInfoToServer = sendCpuInfoToServer;
        return this;
    }

    public int getSyncRequestProcessingPoolSize() {

        return this.syncRequestProcessingPoolSize;
    }

    public TransportClientBuilder withSyncRequestProcessingPoolSize(final int syncRequestProcessingPoolSize) {

        this.syncRequestProcessingPoolSize = syncRequestProcessingPoolSize;
        return this;
    }

    public long getSyncRequestProcessingPoolAutoCleanupInterval() {

        return this.syncRequestProcessingPoolAutoCleanupInterval;
    }

    public TransportClientBuilder withSyncRequestProcessingPoolAutoCleanupInterval(final long
                                                                                       syncRequestProcessingPoolAutoCleanupInterval) {

        this.syncRequestProcessingPoolAutoCleanupInterval = syncRequestProcessingPoolAutoCleanupInterval;
        return this;
    }

    public int getCriticalSyncRequestProcessingQueueSize() {

        return this.criticalSyncRequestProcessingQueueSize;
    }

    public TransportClientBuilder withCriticalSyncRequestProcessingQueueSize(final int
                                                                                 criticalSyncRequestProcessingQueueSize) {

        this.criticalSyncRequestProcessingQueueSize = criticalSyncRequestProcessingQueueSize;
        return this;
    }

    public long getTerminationMaxAwaitTimeoutInMillis() {

        return this.terminationMaxAwaitTimeoutInMillis;
    }

    public TransportClientBuilder withTerminationMaxAwaitTimeoutInMillis(final long
                                                                             terminationMaxAwaitTimeoutInMillis) {

        this.terminationMaxAwaitTimeoutInMillis = terminationMaxAwaitTimeoutInMillis;
        return this;
    }

    public long getMaxSubsequentPingFailedCount() {

        return this.maxSubsequentPingFailedCount;
    }

    public TransportClientBuilder withMaxSubsequentPingFailedCount(final long maxSubsequentPingFailedCount) {

        this.maxSubsequentPingFailedCount = maxSubsequentPingFailedCount;
        return this;
    }

    public long getEventPoolTerminationTimeUnitCount() {

        return this.eventPoolTerminationTimeUnitCount;
    }

    public TransportClientBuilder withEventPoolTerminationTimeUnitCount(final long eventPoolTerminationTimeUnitCount) {

        this.eventPoolTerminationTimeUnitCount = eventPoolTerminationTimeUnitCount;
        return this;
    }

    public TimeUnit getEventPoolTerminationTimeUnit() {

        return this.eventPoolTerminationTimeUnit;
    }

    public TransportClientBuilder withEventPoolTerminationTimeUnit(final TimeUnit eventPoolTerminationTimeUnit) {

        this.eventPoolTerminationTimeUnit = eventPoolTerminationTimeUnit;
        return this;
    }

    public long getAuthEventPoolTerminationTimeUnitCount() {

        return this.authEventPoolTerminationTimeUnitCount;
    }

    public TransportClientBuilder withAuthEventPoolTerminationTimeUnitCount(final long
                                                                                authEventPoolTerminationTimeUnitCount) {

        this.authEventPoolTerminationTimeUnitCount = authEventPoolTerminationTimeUnitCount;
        return this;
    }

    public TimeUnit getAuthEventPoolTerminationTimeUnit() {

        return this.authEventPoolTerminationTimeUnit;
    }

    public TransportClientBuilder withAuthEventPoolTerminationTimeUnit(final TimeUnit
                                                                           authEventPoolTerminationTimeUnit) {

        this.authEventPoolTerminationTimeUnit = authEventPoolTerminationTimeUnit;
        return this;
    }

    public long getStreamProcessingPoolTerminationTimeUnitCount() {

        return this.streamProcessingPoolTerminationTimeUnitCount;
    }

    public TransportClientBuilder withStreamProcessingPoolTerminationTimeUnitCount(final long
                                                                                       streamProcessingPoolTerminationTimeUnitCount) {

        this.streamProcessingPoolTerminationTimeUnitCount = streamProcessingPoolTerminationTimeUnitCount;
        return this;
    }

    public TimeUnit getStreamProcessingPoolTerminationTimeUnit() {

        return this.streamProcessingPoolTerminationTimeUnit;
    }

    public TransportClientBuilder withStreamProcessingPoolTerminationTimeUnit(final TimeUnit
                                                                                  streamProcessingPoolTerminationTimeUnit) {

        this.streamProcessingPoolTerminationTimeUnit = streamProcessingPoolTerminationTimeUnit;
        return this;
    }

    public long getSyncRequestProcessingPoolTerminationTimeUnitCount() {

        return this.syncRequestProcessingPoolTerminationTimeUnitCount;
    }

    public TransportClientBuilder withSyncRequestProcessingPoolTerminationTimeUnitCount(final long
                                                                                            syncRequestProcessingPoolTerminationTimeUnitCount) {

        this.syncRequestProcessingPoolTerminationTimeUnitCount = syncRequestProcessingPoolTerminationTimeUnitCount;
        return this;
    }

    public TimeUnit getSyncRequestProcessingPoolTerminationTimeUnit() {

        return this.syncRequestProcessingPoolTerminationTimeUnit;
    }

    public TransportClientBuilder withSyncRequestProcessingPoolTerminationTimeUnit(final TimeUnit
                                                                                       syncRequestProcessingPoolTerminationTimeUnit) {

        this.syncRequestProcessingPoolTerminationTimeUnit = syncRequestProcessingPoolTerminationTimeUnit;
        return this;
    }

    public Set<String> getEnabledSslProtocols() {

        return this.enabledSslProtocols;
    }

    public TransportClientBuilder withEnabledSslProtocols(final Set<String> enabledSslProtocols) {

        this.enabledSslProtocols = Collections.unmodifiableSet(enabledSslProtocols);
        return this;
    }
}
