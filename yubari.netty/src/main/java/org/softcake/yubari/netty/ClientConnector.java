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

import org.softcake.yubari.netty.mina.ClientDisconnectReason;
import org.softcake.yubari.netty.mina.ClientListener;
import org.softcake.yubari.netty.mina.DisconnectedEvent;
import org.softcake.yubari.netty.mina.IoSessionWrapper;
import org.softcake.yubari.netty.mina.MessageSentListener;
import org.softcake.yubari.netty.mina.RequestListenableFuture;

import com.dukascopy.dds4.ping.IPingListener;
import com.dukascopy.dds4.ping.PingManager;
import com.dukascopy.dds4.transport.common.mina.DisconnectReason;
import com.dukascopy.dds4.transport.msg.system.ChildSocketAuthAcceptorMessage;
import com.dukascopy.dds4.transport.msg.system.HeartbeatOkResponseMessage;
import com.dukascopy.dds4.transport.msg.system.HeartbeatRequestMessage;
import com.dukascopy.dds4.transport.msg.system.PrimarySocketAuthAcceptorMessage;
import com.dukascopy.dds4.transport.msg.system.ProtocolMessage;
import com.google.common.util.concurrent.MoreExecutors;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.util.Attribute;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.nio.channels.ClosedChannelException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * @author The softcake Authors.
 */
@SuppressWarnings("squid:S128")
class ClientConnector extends Thread implements AuthorizationProviderListener {
    private static final long SESSION_CLOSE_WAIT_TIME = 5L;
    private static final String STATE_CHANGED_TO = "[{}] State changed to {}";
    private static final String PRIMARY = "Primary";
    private static final String SECONDARY = "Secondary";
    private static final long TIME_TO_WAIT_FOR_DISCONNECTING = 10000L;
    private static final Logger LOGGER = LoggerFactory.getLogger(ClientConnector.class);
    private final Bootstrap channelBootstrap;
    private final TransportClientSession clientSession;
    private final ClientProtocolHandler protocolHandler;
    private final AtomicReference<ClientState> clientState;
    private final Object stateWaitLock;
    private final IPingListener pingListener;
    private final boolean sendCpuInfoToServer;
    private final AtomicBoolean isExecutorThreadTerminated;
    private final Object executorThreadTerminationLocker;
    private final long maxSubsequentPingFailedCount;
    private final InetSocketAddress address;
    private volatile ClientDisconnectReason disconnectReason;
    private volatile boolean terminating;
    private volatile Channel primaryChannel;
    private ChannelAttachment primarySessionChannelAttachment;
    private volatile Channel childChannel;
    private ChannelAttachment secondarySessionChannelAttachment;
    private ClientAuthorizationProvider authorizationProvider;
    private Long sslHandshakeStartTime;
    private Long protocolVersionNegotiationStartTime;
    private Long authorizationStartTime;
    private PrimarySocketAuthAcceptorMessage primarySocketAuthAcceptorMessage;
    private boolean primarySocketAuthAcceptorMessageSent;
    private ChildSocketAuthAcceptorMessage childSocketAuthAcceptorMessage;

    ClientConnector(final InetSocketAddress address,
                    final Bootstrap channelBootstrap,
                    final TransportClientSession clientSession,
                    final ClientProtocolHandler protocolHandler,
                    final IPingListener pingListener) {

        super((clientSession.getTransportName() != null ? String.format("(%s) ", clientSession.getTransportName()) : "")
              + "ClientConnector");
        this.clientState = new AtomicReference<>(ClientState.DISCONNECTED);
        this.stateWaitLock = new Object();
        this.terminating = false;
        this.isExecutorThreadTerminated = new AtomicBoolean(false);
        this.executorThreadTerminationLocker = new Object();
        this.address = address;
        this.channelBootstrap = channelBootstrap;
        this.clientSession = clientSession;
        this.protocolHandler = protocolHandler;
        this.pingListener = pingListener;
        this.sendCpuInfoToServer = clientSession.isSendCpuInfoToServer();
        this.maxSubsequentPingFailedCount = clientSession.getMaxSubsequentPingFailedCount();
    }

    private static boolean isIoProcessing(final ChannelAttachment attachment, final long pingTimeout, final long now) {

        if (attachment == null) {
            return false;
        } else {
            final long lastIoTime = attachment.getLastIoTime();

            return lastIoTime > 0L && now - lastIoTime < pingTimeout;

        }
    }

    private static Channel setChannelAttributeForSuccessfullyConnection(final ChannelFuture connectFuture,
                                                                        ChannelAttachment attachment) {

        final Channel channel = connectFuture.channel();
        channel.attr(ChannelAttachment.CHANNEL_ATTACHMENT_ATTRIBUTE_KEY)
               .set(attachment);

        return channel;
    }

    private static boolean needToPing(final long now, final ChannelAttachment attachment, final long pingInterval) {

        return !attachment.hasLastPingRequestTime() || now - attachment.getLastPingRequestTime() > pingInterval;

    }

    public void authorizationError(final IoSessionWrapper session, final String errorReason) {


        LOGGER.debug("[{}] Received AUTHORIZATION_ERROR notification from the authorization provider, reason: [{}]",
                     this.clientSession.getTransportName(),
                     errorReason);


        final ClientDisconnectReason reason = new ClientDisconnectReason(DisconnectReason.AUTHORIZATION_FAILED,
                                                                         String.format("Authorization failed, "
                                                                                       + "reason [%s]", errorReason));

        this.tryToSetState(ClientState.AUTHORIZING, ClientState.DISCONNECTING, reason, Boolean.TRUE);

    }

    public void authorized(final String sessionId, final IoSessionWrapper session, final String userName) {

        LOGGER.debug(
            "[{}] Received AUTHORIZED notification from the authorization provider. SessionId [{}], userName [{}]",
            this.clientSession.getTransportName(),
            sessionId,
            userName);


        this.clientSession.setServerSessionId(sessionId);
        this.clientSession.getRemoteCallSupport().setSession(session);
        if (this.tryToSetState(ClientState.AUTHORIZING, ClientState.ONLINE)) {
            this.fireAuthorized();
        }

    }

    void connect() {

        this.tryToSetState(ClientState.DISCONNECTED, ClientState.CONNECTING, Boolean.TRUE);

    }

    void disconnect() {

        this.disconnect(new ClientDisconnectReason(DisconnectReason.CLIENT_APP_REQUEST, "Client application request"));
    }

    void primaryChannelDisconnected() {

        this.disconnect(this.disconnectReason);
    }

    void disconnect(final ClientDisconnectReason disconnectReason) {

        while (true) {
            final ClientState state = this.clientState.get();
            switch (state) {
                case CONNECTING:
                case SSL_HANDSHAKE_WAITING:
                case SSL_HANDSHAKE_SUCCESSFUL:
                case PROTOCOL_VERSION_NEGOTIATION_WAITING:
                case PROTOCOL_VERSION_NEGOTIATION_SUCCESSFUL:
                case AUTHORIZING:
                case ONLINE:

                    if (!this.clientState.compareAndSet(state, ClientState.DISCONNECTING)) {
                        break;
                    }

                    this.disconnectReason = disconnectReason;

                    LOGGER.debug(STATE_CHANGED_TO, this.clientSession.getTransportName(), ClientState.DISCONNECTING);


                    synchronized (this.stateWaitLock) {
                        this.stateWaitLock.notifyAll();
                        return;
                    }

                case DISCONNECTING:
                    return;
                case DISCONNECTED:
                    return;
                default:
                    throw new IllegalArgumentException("Unsupported state " + state);
            }
        }
    }

    void secondaryChannelDisconnected() {


        synchronized (this.stateWaitLock) {
            this.stateWaitLock.notifyAll();
        }
    }

    void sslHandshakeSuccess() {

        if (!this.tryToSetState(ClientState.SSL_HANDSHAKE_WAITING, ClientState.SSL_HANDSHAKE_SUCCESSFUL)) {
            this.tryToSetState(ClientState.CONNECTING, ClientState.SSL_HANDSHAKE_SUCCESSFUL);
        }

    }

    void protocolVersionHandshakeSuccess() {

        if (!this.tryToSetState(ClientState.PROTOCOL_VERSION_NEGOTIATION_WAITING,
                                ClientState.PROTOCOL_VERSION_NEGOTIATION_SUCCESSFUL)
            && !this.tryToSetState(ClientState.CONNECTING,
                                   ClientState.PROTOCOL_VERSION_NEGOTIATION_SUCCESSFUL)
            && !this.tryToSetState(ClientState.SSL_HANDSHAKE_SUCCESSFUL,
                                   ClientState.PROTOCOL_VERSION_NEGOTIATION_SUCCESSFUL)) {
            this.tryToSetState(ClientState.SSL_HANDSHAKE_WAITING,
                               ClientState.PROTOCOL_VERSION_NEGOTIATION_SUCCESSFUL);
        }

    }

    @Override
    public void run() {

        while (true) {
            boolean interrupted = false;

            try {
                if (this.terminating) {

                    this.clientState.set(ClientState.DISCONNECTED);
                    this.cleanUp();

                    synchronized (this.executorThreadTerminationLocker) {
                        this.isExecutorThreadTerminated.set(Boolean.TRUE);
                        this.executorThreadTerminationLocker.notifyAll();
                    }
                    return;
                }


                final ClientState state = this.getClientState();

                long timeToWait;
                switch (state) {
                    case CONNECTING:
                        this.cleanUp();
                        this.processConnecting();
                        continue;
                    case SSL_HANDSHAKE_WAITING:
                        if (isProcessSSLHandshakeTimeout()) {
                            continue;
                        }

                        timeToWait = this.sslHandshakeStartTime + this.clientSession.getSSLHandshakeTimeout()
                                     - System.currentTimeMillis();


                        waitAndNotifyThreads(timeToWait, ClientState.SSL_HANDSHAKE_WAITING);
                        continue;
                    case SSL_HANDSHAKE_SUCCESSFUL:
                        if (this.tryToSetState(ClientState.SSL_HANDSHAKE_SUCCESSFUL,
                                               ClientState.PROTOCOL_VERSION_NEGOTIATION_WAITING)) {
                            this.protocolVersionNegotiationStartTime = System.currentTimeMillis();
                        }
                        continue;
                    case PROTOCOL_VERSION_NEGOTIATION_WAITING:
                        if (isProcessVersionNegotiationTimeout()) {
                            continue;
                        }

                        timeToWait = this.protocolVersionNegotiationStartTime
                                     + this.clientSession.getProtocolVersionNegotiationTimeout()
                                     - System.currentTimeMillis();


                        waitAndNotifyThreads(timeToWait, ClientState.PROTOCOL_VERSION_NEGOTIATION_WAITING);
                        continue;
                    case PROTOCOL_VERSION_NEGOTIATION_SUCCESSFUL:
                        if (this.tryToSetState(ClientState.PROTOCOL_VERSION_NEGOTIATION_SUCCESSFUL,
                                               ClientState.AUTHORIZING)) {
                            this.authorizationStartTime = System.currentTimeMillis();
                            this.protocolHandler.handleAuthorization(this.authorizationProvider, this.primaryChannel);
                        }
                        continue;
                    case AUTHORIZING:
                        if (isProcessAuthorizationTimeout()) {
                            continue;
                        }

                        timeToWait = this.authorizationStartTime + this.clientSession.getAuthorizationTimeout() - System
                            .currentTimeMillis();

                        waitAndNotifyThreads(timeToWait, ClientState.AUTHORIZING);
                        continue;
                    case ONLINE:
                        if (!this.processOnline()) {
                            continue;
                        }
                        waitAndNotifyThreads(100L, ClientState.ONLINE);
                        continue;
                    case DISCONNECTING:

                        this.cleanUp();
                        this.tryToSetState(ClientState.DISCONNECTING, ClientState.DISCONNECTED, Boolean.TRUE);
                        this.fireDisconnected(this.disconnectReason);

                        continue;
                    case DISCONNECTED:
                        waitAndNotifyThreads(TIME_TO_WAIT_FOR_DISCONNECTING, ClientState.DISCONNECTED);
                        continue;
                    default:
                        throw new IllegalArgumentException("Unsupported state " + state);
                }
            } catch (final InterruptedException e) {

                LOGGER.error("Thread is interrupted: {}", Thread.currentThread().getName(),e);
                interrupted = Boolean.TRUE;
                try {
                    this.clientState.set(ClientState.DISCONNECTED);
                    this.cleanUp();


                } catch (InterruptedException ex) {
                    LOGGER.error("Thread is interrupted: {}", Thread.currentThread().getName(),e);
                    interrupted = Boolean.TRUE;

                }
            } finally {
                synchronized (this.executorThreadTerminationLocker) {
                    this.isExecutorThreadTerminated.set(Boolean.TRUE);
                    this.executorThreadTerminationLocker.notifyAll();
                }

                if (interrupted) {
                    LOGGER.error("Thread is interrupted: {}", Thread.currentThread().getName());
                    Thread.currentThread().interrupt();
                }

            }


        }
    }

    private boolean isProcessAuthorizationTimeout() {

        if (this.authorizationStartTime + this.clientSession.getAuthorizationTimeout()
            < System.currentTimeMillis()) {

            LOGGER.info("[{}] Authorization timeout", this.clientSession.getTransportName());

            this.tryToSetState(ClientState.AUTHORIZING,
                               ClientState.DISCONNECTING,
                               new ClientDisconnectReason(DisconnectReason.AUTHORIZATION_TIMEOUT,
                                                          "Authorization timeout"),
                               Boolean.TRUE);

            return true;
        }

        return false;
    }

    private boolean isProcessVersionNegotiationTimeout() {

        if (this.protocolVersionNegotiationStartTime
            + this.clientSession.getProtocolVersionNegotiationTimeout() < System.currentTimeMillis()) {

            LOGGER.info("[{}] Protocol version negotiation timeout",
                        this.clientSession.getTransportName());


            this.tryToSetState(ClientState.PROTOCOL_VERSION_NEGOTIATION_WAITING,
                               ClientState.DISCONNECTING,
                               new ClientDisconnectReason(DisconnectReason
                                                              .PROTOCOL_VERSION_NEGOTIATION_TIMEOUT,
                                                          "Protocol version "
                                                          + "negotiation timeout"),
                               Boolean.TRUE);

            return true;
        }

        return false;
    }

    private boolean isProcessSSLHandshakeTimeout() {

        if (this.sslHandshakeStartTime + this.clientSession.getSSLHandshakeTimeout()
            < System.currentTimeMillis()) {

            LOGGER.info("[{}] SSL handshake timeout", this.clientSession.getTransportName());

            this.tryToSetState(ClientState.SSL_HANDSHAKE_WAITING,
                               ClientState.DISCONNECTING,
                               new ClientDisconnectReason(DisconnectReason.SSL_HANDSHAKE_TIMEOUT,
                                                          "SSL handshake timeout"),
                               Boolean.TRUE);

            return true;
        }
        return false;
    }

    @SuppressWarnings("squid:S2274")
    private void waitAndNotifyThreads(final long timeToWait, final ClientState state1)
        throws InterruptedException {

        final long waitTime = timeToWait <= 0L ? 0L : timeToWait;
        synchronized (this.stateWaitLock) {
            if (this.getClientState() == state1) {
                this.stateWaitLock.wait(waitTime);
            }

        }
    }

    private void processConnecting() throws InterruptedException {


        this.processConnectingPreConditions();

        if (!this.isServerAddressValid()) {
            this.processConnectingInvalidServerAddress();
            return;
        }

        final ChannelFuture connectFuture = this.processConnectingAndGetFuture();

        if (connectFuture.isSuccess()) {

            LOGGER.debug("[{}] Successfully connected to [{}]", this.clientSession.getTransportName(), this.address);
            this.primaryChannel = setChannelAttributeForSuccessfullyConnection(connectFuture,
                                                                               this.primarySessionChannelAttachment);
            LOGGER.debug("[{}] primaryChannel = {}", this.clientSession.getTransportName(), this.primaryChannel);
            this.processConnectOverSSLIfNecessary();

        } else if (connectFuture.isDone() && connectFuture.cause() != null) {
            this.handleConnectingError(connectFuture);

        } else {

            this.handleUnexpectedConnectingError();

        }


    }

    private void processConnectingPreConditions() {

        this.disconnectReason = null;
        this.authorizationProvider = this.clientSession.getAuthorizationProvider();
        this.authorizationProvider.setListener(this);
        this.primarySessionChannelAttachment = new ChannelAttachment(Boolean.TRUE);
        this.secondarySessionChannelAttachment = new ChannelAttachment(Boolean.FALSE);
    }

    private void handleUnexpectedConnectingError() {

        LOGGER.debug("[{}] Connect call failed", this.clientSession.getTransportName());

        this.tryToSetState(ClientState.CONNECTING,
                           ClientState.DISCONNECTING,
                           new ClientDisconnectReason(DisconnectReason.CONNECTION_PROBLEM,
                                                      "Connect call failed"),
                           Boolean.TRUE);
    }

    private void handleConnectingError(final ChannelFuture connectFuture) {

        final Throwable cause = connectFuture.cause();

        LOGGER.debug("[{}] Connect call failed because of {}: {}",
                     this.clientSession.getTransportName(),
                     cause.getClass().getSimpleName(),
                     cause.getMessage());


        this.tryToSetState(ClientState.CONNECTING,
                           ClientState.DISCONNECTING,
                           new ClientDisconnectReason(DisconnectReason.CERTIFICATE_EXCEPTION,
                                                      String.format("%s exception %s",
                                                                    cause instanceof
                                                                        CertificateException
                                                                    ? "Certificate"
                                                                    : "Unexpected",
                                                                    cause.getMessage()), cause),
                           Boolean.TRUE);
    }

    private void processConnectOverSSLIfNecessary() {

        if (this.clientSession.isUseSSL()) {
            if (this.tryToSetState(ClientState.CONNECTING, ClientState.SSL_HANDSHAKE_WAITING)) {
                this.sslHandshakeStartTime = System.currentTimeMillis();
            }
        } else if (this.tryToSetState(ClientState.CONNECTING,
                                      ClientState.PROTOCOL_VERSION_NEGOTIATION_WAITING)) {
            this.protocolVersionNegotiationStartTime = System.currentTimeMillis();
        }
    }

    private ChannelFuture processConnectingAndGetFuture() throws InterruptedException {

        LOGGER.debug("[{}] Connecting to [{}]", this.clientSession.getTransportName(), this.address);


        final ChannelFuture connectFuture = this.channelBootstrap.connect(this.address);
        connectFuture.await(this.clientSession.getConnectionTimeout());
        return connectFuture;
    }

    private void processConnectingInvalidServerAddress() {

        LOGGER.error("[{}] Address not set in transport and "
                     + "AbstractClientAuthorizationProvider.getAddress returned null",
                     this.clientSession.getTransportName());

        this.tryToSetState(ClientState.CONNECTING,
                           ClientState.DISCONNECTING,
                           new ClientDisconnectReason(DisconnectReason.CONNECTION_PROBLEM,
                                                      "Address is not set in transport client"),
                           Boolean.TRUE);
    }

    private boolean isServerAddressValid() {

        return this.address != null;
    }

    private boolean processOnline() throws InterruptedException {

        if (this.authorizationProvider != null) {
            this.authorizationProvider.cleanUp();
            this.authorizationProvider = null;
        }

        if (this.primaryChannel == null || !this.primaryChannel.isActive()) {
            LOGGER.warn("[{}] Primary session disconnected. Disconnecting transport client",
                        this.clientSession.getTransportName());
            if (this.disconnectReason == null) {
                this.disconnectReason = new ClientDisconnectReason(DisconnectReason.CONNECTION_PROBLEM,
                                                                   "Primary session is not active");
            }


            this.tryToSetState(ClientState.ONLINE, ClientState.DISCONNECTING, Boolean.TRUE);

            return false;

        }

        this.processPrimarySocketAuthAcceptorMessage();

        if (this.clientSession.getPrimaryConnectionPingInterval() > 0L
            && this.clientSession.getPrimaryConnectionPingTimeout() > 0L) {

            final ChannelAttachment primAttachment
                = this.primaryChannel.attr(ChannelAttachment.CHANNEL_ATTACHMENT_ATTRIBUTE_KEY).get();

            final boolean needToPing = needToPing(System.currentTimeMillis(),
                                                  primAttachment,
                                                  this.clientSession.getPrimaryConnectionPingInterval());
            if (needToPing && this.isOnline()) {
                this.sendPingRequest(this.primaryChannel,
                                     primAttachment,
                                     this.clientSession.getPrimaryConnectionPingTimeout(),
                                     Boolean.TRUE);
            }
        }

        if (!this.clientSession.isUseFeederSocket() || this.childSocketAuthAcceptorMessage == null) {return true;}

        if (this.childChannel != null && this.childChannel.isActive()) {

            if (this.clientSession.getSecondaryConnectionPingInterval() > 0L
                && this.clientSession.getSecondaryConnectionPingTimeout() > 0L) {
                final Attribute<ChannelAttachment>
                    attribute
                    = this.childChannel.attr(ChannelAttachment.CHANNEL_ATTACHMENT_ATTRIBUTE_KEY);
                final ChannelAttachment secAttachment = attribute.get();
                final boolean needToPing = needToPing(System.currentTimeMillis(),
                                                      secAttachment,
                                                      this.clientSession.getSecondaryConnectionPingInterval());
                if (needToPing && this.isOnline()) {
                    this.sendPingRequest(this.childChannel,
                                         secAttachment,
                                         this.clientSession.getSecondaryConnectionPingTimeout(),
                                         Boolean.FALSE);
                }
            }

            if (this.canResetSecondaryChannelReconnetAttempts()) {

                this.secondarySessionChannelAttachment.setReconnectAttempt(0);
            }
        } else {

            if (this.isMaxSecondaryChannelReconnectAttemptsReached()) {
                LOGGER.warn("[{}] Child session max connection attempts reached. Disconnecting clientSession",
                            this.clientSession.getTransportName());
                if (this.disconnectReason == null) {
                    this.disconnectReason = new ClientDisconnectReason(DisconnectReason.CONNECTION_PROBLEM,
                                                                       "Secondary session max connection attempts"
                                                                       + " reached");
                }

                this.tryToSetState(ClientState.ONLINE, ClientState.DISCONNECTING, Boolean.TRUE);

                return false;
            }


            if (this.secondarySessionChannelAttachment.getLastConnectAttemptTime()
                + this.clientSession.getReconnectDelay() < System.currentTimeMillis()) {

                return this.connectChildSession();
            }
        }

        return true;

    }

    private boolean connectChildSession() throws InterruptedException {

        if (this.childChannel != null) {
            this.childChannel.disconnect();
            this.childChannel.close();
            this.childChannel = null;
        }


        this.secondarySessionChannelAttachment.setLastConnectAttemptTime(System.currentTimeMillis());
        this.secondarySessionChannelAttachment.resetTimes();
        this.secondarySessionChannelAttachment.setReconnectAttempt(this.secondarySessionChannelAttachment
                                                                       .getReconnectAttempt()
                                                                   + 1);

        LOGGER.debug("[{}] Trying to connect child session. Attempt {}",
                     this.clientSession.getTransportName(),
                     this.secondarySessionChannelAttachment.getReconnectAttempt() + 1);


        final ChannelFuture connectFuture = this.processConnectingAndGetFuture();

        if (connectFuture.isSuccess()) {
            this.childChannel = setChannelAttributeForSuccessfullyConnection(connectFuture,
                                                                             this.secondarySessionChannelAttachment);

            final ChannelFuture channelFuture = this.protocolHandler.writeMessage(this.childChannel,
                                                                                  this.childSocketAuthAcceptorMessage);
            channelFuture.addListener((GenericFutureListener<Future<Void>>) future -> {

                try {
                    channelFuture.get();
                } catch (ExecutionException | InterruptedException e) {

                    if (!(e.getCause() instanceof ClosedChannelException) && this.isOnline()) {
                        LOGGER.error("[{}] ", this.clientSession.getTransportName(), e);
                        this.disconnect(new ClientDisconnectReason(DisconnectReason.CONNECTION_PROBLEM,
                                                                   String.format(
                                                                       "Secondary session error while writing: "
                                                                       + "%s",
                                                                       this.childSocketAuthAcceptorMessage),
                                                                   e));
                    }
                }

            });

            return true;
        }

        if (connectFuture.isDone() && connectFuture.cause() != null) {


            final Throwable cause = connectFuture.cause();

            LOGGER.debug("[{}] Secondary connection connect call failed because of {}: {}",
                         this.clientSession.getTransportName(),
                         cause.getClass().getSimpleName(),
                         cause.getMessage());


            if (cause instanceof CertificateException) {
                this.disconnectReason = new ClientDisconnectReason(DisconnectReason.CERTIFICATE_EXCEPTION,
                                                                   "Secondary connection certificate exception.",
                                                                   cause);
            }
        } else {
            LOGGER.debug("[{}] Secondary connection connect call failed", this.clientSession.getTransportName());
        }

        return false;
    }

    private boolean isMaxSecondaryChannelReconnectAttemptsReached() {

        return this.secondarySessionChannelAttachment.getReconnectAttempt()
               >= this.clientSession.getSecondaryConnectionReconnectAttempts();
    }

    private boolean canResetSecondaryChannelReconnetAttempts() {

        return this.secondarySessionChannelAttachment.getReconnectAttempt() != 0
               && (this.secondarySessionChannelAttachment.getLastIoTime()
                   > this.secondarySessionChannelAttachment.getLastConnectAttemptTime()
                   || this.secondarySessionChannelAttachment.getLastConnectAttemptTime()
                      + this.clientSession.getSecondaryConnectionReconnectsResetDelay() < System.currentTimeMillis());
    }

    private void processPrimarySocketAuthAcceptorMessage() {

        if (this.primarySocketAuthAcceptorMessageSent || this.primarySocketAuthAcceptorMessage == null) {return;}


        final ChannelFuture channelFuture = this.protocolHandler.writeMessage(this.primaryChannel,
                                                                              this.primarySocketAuthAcceptorMessage);
        channelFuture.addListener((GenericFutureListener<Future<Void>>) future -> {

            try {
                channelFuture.get();
            } catch (ExecutionException | InterruptedException e) {

                if (!(e.getCause() instanceof ClosedChannelException) && this.isOnline()) {
                    LOGGER.error("[{}] ", this.clientSession.getTransportName(), e);
                    this.disconnect(new ClientDisconnectReason(DisconnectReason.CONNECTION_PROBLEM,
                                                               String.format("Primary session error "
                                                                             + "while writhing message: %s",
                                                                             this.primarySocketAuthAcceptorMessage),
                                                               e));
                }
            }

        });
        this.primarySocketAuthAcceptorMessageSent = true;

    }

    private void sendPingRequest(final Channel channel,
                                 final ChannelAttachment attachment,
                                 final long pingTimeout,
                                 final boolean isPrimary) {

        final HeartbeatRequestMessage pingRequestMessage = new HeartbeatRequestMessage();
        final long startTime = System.currentTimeMillis();
        attachment.setLastPingRequestTime(startTime);
        pingRequestMessage.setRequestTime(startTime);
        final PingManager pingManager = this.clientSession.getPingManager(isPrimary);
        final AtomicLong pingSocketWriteInterval = new AtomicLong(Long.MAX_VALUE);

        final MessageSentListener messageSentListener = message -> {

            LOGGER.debug("[{}] Ping sent in {} channel.",
                         this.clientSession.getTransportName(),
                         (isPrimary ? PRIMARY : SECONDARY).toUpperCase());
            pingSocketWriteInterval.set(System.currentTimeMillis() - startTime);
        };


        final RequestListenableFuture future = this.clientSession.sendRequestAsync(pingRequestMessage,
                                                                                   channel,
                                                                                   pingTimeout,
                                                                                   Boolean.TRUE,
                                                                                   messageSentListener);

        LOGGER.debug("[{}] Sending {} connection ping request: {}",
                     this.clientSession.getTransportName(),
                     (isPrimary ? PRIMARY : SECONDARY).toLowerCase(),
                     pingRequestMessage);

        final Runnable runnable = () -> {


            final long now = System.currentTimeMillis();

            try {

                final ProtocolMessage protocolMessage = future.get();

                if (protocolMessage instanceof HeartbeatOkResponseMessage) {
                    final HeartbeatOkResponseMessage message = (HeartbeatOkResponseMessage) protocolMessage;
                    final long turnOverTime = now - startTime;

                    attachment.pingSuccessfull();

                    final Double systemCpuLoad = ClientConnector.this.sendCpuInfoToServer
                                                 ? pingManager.getSystemCpuLoad()
                                                 : null;
                    final Double processCpuLoad = ClientConnector.this.sendCpuInfoToServer
                                                  ? pingManager.getProcessCpuLoad()
                                                  : null;
                    pingManager.addPing(turnOverTime,
                                        pingSocketWriteInterval.get(),
                                        systemCpuLoad,
                                        processCpuLoad,
                                        message.getSocketWriteInterval(),
                                        message.getSystemCpuLoad(),
                                        message.getProcessCpuLoad());


                    if (pingListener != null) {
                        pingListener.pingSucceded(turnOverTime,
                                                  pingSocketWriteInterval.get(),
                                                  systemCpuLoad,
                                                  processCpuLoad,
                                                  message.getSocketWriteInterval(),
                                                  message.getSystemCpuLoad(),
                                                  message.getProcessCpuLoad());
                    }


                    LOGGER.debug("{} synchronization ping response received: {}, time: {}",
                                 isPrimary ? PRIMARY : SECONDARY,
                                 protocolMessage,
                                 turnOverTime);

                } else {
                    attachment.pingFailed();
                    pingManager.pingFailed();
                    safeNotifyPingFailed();

                    LOGGER.debug("Server has returned unknown response type for ping request! Time - {}",
                                 (now - startTime));

                }
            } catch (InterruptedException | ExecutionException e) {

                if (isOnline()) {
                    attachment.pingFailed();
                    pingManager.pingFailed();
                    safeNotifyPingFailed();
                    LOGGER.error("{} session ping failed: {}, timeout: {}, syncRequestId: {}",
                                 isPrimary ? PRIMARY : SECONDARY,
                                 e.getMessage(),
                                 pingTimeout,
                                 pingRequestMessage.getSynchRequestId());
                }
            } finally {
                checkPingFailed(isPrimary, attachment, pingTimeout, now);
            }

        };

        future.addListener(runnable, MoreExecutors.newDirectExecutorService());

    }

    private void checkPingFailed(final boolean isPrimary,
                                 final ChannelAttachment attachment,
                                 final long pingTimeout,
                                 final long now) {

        if (!this.isOnline() || !this.isPingFailed(attachment, pingTimeout, now)) {
            return;
        }

        final Channel channel = isPrimary ? this.primaryChannel : this.childChannel;
        if (channel != null) {
            channel.disconnect();
            channel.close();
        }

        LOGGER.warn("[{}] {} session ping timeout {}ms. Disconnecting session...",
                    this.clientSession.getTransportName(),
                    isPrimary ? PRIMARY : SECONDARY,
                    isPrimary
                    ? this.clientSession.getPrimaryConnectionPingTimeout()
                    : this.clientSession.getSecondaryConnectionPingTimeout());


        if (isPrimary) {

            if (this.disconnectReason == null) {
                this.disconnectReason = new ClientDisconnectReason(DisconnectReason.CONNECTION_PROBLEM,
                                                                   "Primary session ping timeout");
            }

            this.tryToSetState(ClientState.ONLINE, ClientState.DISCONNECTING, Boolean.TRUE);

            synchronized (this.stateWaitLock) {
                this.stateWaitLock.notifyAll();
            }
        }

    }

    private boolean isPingFailed(final ChannelAttachment attachment, final long pingTimeout, final long now) {

        final long failedPingCount = attachment.getLastSubsequentFailedPingCount();
        final boolean ioProcessing = isIoProcessing(attachment, pingTimeout, now);
        return failedPingCount >= this.maxSubsequentPingFailedCount && !ioProcessing;

    }

    private void safeNotifyPingFailed() {

        if (this.pingListener != null) {
            this.pingListener.pingFailed();
        }
    }

    private void cleanUp() throws InterruptedException {


        LOGGER.debug("[{}] Closing all sockets and cleaning references", this.clientSession.getTransportName());


        this.closeAllSessions();

        if (this.authorizationProvider != null) {
            this.authorizationProvider.cleanUp();
            this.authorizationProvider = null;
        }


        this.primarySocketAuthAcceptorMessage = null;
        this.childSocketAuthAcceptorMessage = null;
        this.sslHandshakeStartTime = null;
        this.protocolVersionNegotiationStartTime = null;
        this.authorizationStartTime = null;
    }

    private void closeAllSessions() throws InterruptedException {

        closePrimarySession();

        closeChildSession();
    }

    private void closeChildSession() throws InterruptedException {

        if (this.childChannel != null) {
            this.childChannel.disconnect();
            this.childChannel.close().await(SESSION_CLOSE_WAIT_TIME, TimeUnit.SECONDS);
            this.childChannel = null;
        }
    }

    private void closePrimarySession() throws InterruptedException {

        if (this.primaryChannel != null) {
            this.primaryChannel.disconnect();
            this.primaryChannel.close().await(SESSION_CLOSE_WAIT_TIME, TimeUnit.SECONDS);
            this.primaryChannel = null;
        }
    }

    private ClientState getClientState() {

        return this.clientState.get();
    }


    private boolean isStateChanged() {

        boolean stateChanged;

        ClientState state;
        do {
            state = this.clientState.get();
            stateChanged = this.clientState.compareAndSet(state, ClientState.DISCONNECTING);
        } while (state != ClientState.DISCONNECTING && state != ClientState.DISCONNECTED && !(stateChanged));
        return stateChanged;
    }

    private boolean tryToSetState(final ClientState expected,
                                  final ClientState newState) {

        return this.tryToSetState(expected, newState, null);

    }

    private boolean tryToSetState(final ClientState expected,
                                  final ClientState newState,
                                  boolean throwExecption) {

        return this.tryToSetState(expected, newState, null, throwExecption);

    }

    private boolean tryToSetState(final ClientState expected,
                                  final ClientState newState,
                                  final ClientDisconnectReason reason) {

        return this.tryToSetState(expected, newState, reason, Boolean.FALSE);

    }

    private boolean tryToSetState(final ClientState expected,
                                  final ClientState newState,
                                  final ClientDisconnectReason reason, boolean throwExecption) {

        if (!this.clientState.compareAndSet(expected, newState)) {
            if (newState == ClientState.DISCONNECTING) {


                if (this.isStateChanged()) {
                    this.disconnectReason = reason;
                    LOGGER.debug(STATE_CHANGED_TO, this.clientSession.getTransportName(), newState);
                }
            } else if (this.clientState.get() != newState && throwExecption) {
                throw new IllegalStateException(String.format("[%s] Expected state [%s], real state [%s]",
                                                              this.clientSession.getTransportName(),
                                                              expected.name(),
                                                              this.clientState.get()));
            } else if (this.clientState.get() != newState) {
                return false;
            }
        } else {
            this.disconnectReason = reason;
            LOGGER.debug(STATE_CHANGED_TO, this.clientSession.getTransportName(), newState);

        }


        synchronized (this.stateWaitLock) {
            this.stateWaitLock.notifyAll();
            return true;
        }
    }

    boolean isOnline() {

        return this.clientState.get() == ClientState.ONLINE;
    }

    boolean isConnecting() {

        final ClientState state = this.clientState.get();
        return state == ClientState.CONNECTING
               || state == ClientState.SSL_HANDSHAKE_WAITING
               || state == ClientState.SSL_HANDSHAKE_SUCCESSFUL
               || state == ClientState.PROTOCOL_VERSION_NEGOTIATION_WAITING
               || state == ClientState.PROTOCOL_VERSION_NEGOTIATION_SUCCESSFUL
               || state == ClientState.AUTHORIZING;
    }

    boolean isAuthorizing() {

        return this.clientState.get() == ClientState.AUTHORIZING;
    }

    public Channel getPrimaryChannel() {

        return this.primaryChannel;
    }

    public Channel getChildChannel() {

        return this.childChannel;
    }

    public void setDisconnectReason(final ClientDisconnectReason disconnectReason) {

        this.disconnectReason = disconnectReason;
    }

    public void setChildSocketAuthAcceptorMessage(final ChildSocketAuthAcceptorMessage childSocketAuthAcceptorMessage) {

        this.childSocketAuthAcceptorMessage = childSocketAuthAcceptorMessage;

        synchronized (this.stateWaitLock) {
            this.stateWaitLock.notifyAll();
        }
    }

    public void setPrimarySocketAuthAcceptorMessage(final PrimarySocketAuthAcceptorMessage
                                                        primarySocketAuthAcceptorMessage) {

        this.primarySocketAuthAcceptorMessage = primarySocketAuthAcceptorMessage;

        synchronized (this.stateWaitLock) {
            this.stateWaitLock.notifyAll();
        }
    }

    private void fireDisconnected(ClientDisconnectReason disconnectReason) {

        this.clientSession.disconnected();

        if (disconnectReason == null) {
            disconnectReason = new ClientDisconnectReason(DisconnectReason.CONNECTION_PROBLEM,
                                                          "Unknown connection problem");
        }

        final CopyOnWriteArrayList<ClientListener> listeners = this.clientSession.getListeners();

        DisconnectedEvent disconnectedEvent = new DisconnectedEvent(this.clientSession.getTransportClient(),
                                                                    disconnectReason.getDisconnectReason(),
                                                                    disconnectReason.getDisconnectHint(),
                                                                    disconnectReason.getError(),
                                                                    disconnectReason.getDisconnectComments());
        listeners.forEach(new Consumer<ClientListener>() {
            @Override
            public void accept(final ClientListener clientListener) {

                ClientConnector.this.protocolHandler.fireDisconnectedEvent(clientListener,
                                                                           ClientConnector.this.clientSession
                                                                               .getTransportClient(),
                                                                           disconnectedEvent);
            }
        });


        this.logDisconnectInQueue(disconnectReason);
        this.clientSession.terminate();
    }

    @SuppressWarnings("squid:S2629")
    private void logDisconnectInQueue(final ClientDisconnectReason disconnectReason) {

        final StringBuilder builder = new StringBuilder();
        builder.append("Disconnect task in queue, reason [").append(disconnectReason.getDisconnectReason()).append(
            "], comments [").append(disconnectReason.getDisconnectComments()).append("]");
        if (this.clientSession != null) {
            builder.append(", server address [")
                   .append(this.clientSession.getAddress())
                   .append("], transport name [")
                   .append(this.clientSession.getTransportName())
                   .append("]");
        }

        if (disconnectReason.getError() != null) {
            LOGGER.info(builder.toString(), disconnectReason.getError());
        } else {
            LOGGER.info(builder.toString());
        }

    }

    private void fireAuthorized() {

        final CopyOnWriteArrayList<ClientListener> listeners = this.clientSession.getListeners();

        listeners.forEach(clientListener -> this.protocolHandler.fireAuthorizedEvent(clientListener,
                                                                                     this.clientSession
                                                                                         .getTransportClient()));


        LOGGER.info("Authorize task in queue, server address [{}], transport name [{}]",
                    this.clientSession.getAddress(),
                    this.clientSession.getTransportName());


    }

    void securityException(final X509Certificate[] chain,
                                  final String authType,
                                  final CertificateException e) {

        LOGGER.error("[{}] CERTIFICATE_EXCEPTION: ", this.clientSession.getTransportName(), e);

        if (this.clientSession.getSecurityExceptionHandler() == null
            || !this.clientSession.getSecurityExceptionHandler().isIgnoreSecurityException(chain,
                                                                                           authType,
                                                                                           e)) {

            final ClientDisconnectReason reason = new ClientDisconnectReason(DisconnectReason.CERTIFICATE_EXCEPTION,
                                                                             String.format("Certificate exception %s",
                                                                                           e.getMessage()), e);

            this.tryToSetState(ClientState.SSL_HANDSHAKE_WAITING,
                               ClientState.DISCONNECTING, reason, Boolean.TRUE);

        }

    }

    boolean isTerminating() {

        return this.terminating;
    }

    void setTerminating(final long terminationAwaitMaxTimeoutInMillis) {

        this.terminating = true;

        synchronized (this.stateWaitLock) {
            this.stateWaitLock.notifyAll();
        }

        if (terminationAwaitMaxTimeoutInMillis > 0L && !this.isExecutorThreadTerminated.get()) {

            synchronized (this.executorThreadTerminationLocker) {
                boolean interrupted = false;
                while (!this.isExecutorThreadTerminated.get()) {

                    try {
                        this.executorThreadTerminationLocker.wait(terminationAwaitMaxTimeoutInMillis);
                    } catch (InterruptedException e) {
                        interrupted = true;
                        LOGGER.error("Error occurred...", e);
                    } finally {
                        if (interrupted) {Thread.currentThread().interrupt();}
                    }

                }
            }
        }

    }

    public enum ClientState {
        CONNECTING,
        SSL_HANDSHAKE_WAITING,
        SSL_HANDSHAKE_SUCCESSFUL,
        PROTOCOL_VERSION_NEGOTIATION_WAITING,
        PROTOCOL_VERSION_NEGOTIATION_SUCCESSFUL,
        AUTHORIZING,
        ONLINE,
        DISCONNECTING,
        DISCONNECTED;

        ClientState() {

        }
    }
}
