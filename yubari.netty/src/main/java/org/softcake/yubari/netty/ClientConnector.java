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


import org.softcake.yubari.netty.mina.ClientListener;
import org.softcake.yubari.netty.mina.DisconnectedEvent;
import org.softcake.yubari.netty.mina.ExtendedClientDisconnectReason;
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

@SuppressWarnings("squid:S128")
class ClientConnector extends Thread implements AuthorizationProviderListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(ClientConnector.class);
    public static final long SESSION_CLOSE_WAIT_TIME = 5L;
    public static final String STATE_CHANGED_TO = "[{}] State changed to {}";
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
    private InetSocketAddress address;
    private volatile ExtendedClientDisconnectReason disconnectReason;
    private volatile boolean terminating;
    private volatile Channel primarySession;
    private ChannelAttachment primarySessionChannelAttachment;
    private volatile Channel childSession;
    private ChannelAttachment secondarySessionChannelAttachment;
    private ClientAuthorizationProvider authorizationProvider;
    private Long sslHandshakeStartTime;
    private Long protocolVersionNegotiationStartTime;
    private Long authorizationStartTime;
    private PrimarySocketAuthAcceptorMessage primarySocketAuthAcceptorMessage;
    private boolean primarySocketAuthAcceptorMessageSent;
    private ChildSocketAuthAcceptorMessage childSocketAuthAcceptorMessage;

    public ClientConnector(final InetSocketAddress address,
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

    public void authorizationError(final IoSessionWrapper session, final String reason) {

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("[{}] Received AUTHORIZATION_ERROR notification from the authorization provider, reason: [{}]",
                         this.clientSession.getTransportName(),
                         reason);
        }

        this.setState(ClientState.AUTHORIZING,
                      ClientState.DISCONNECTING,
                      new ExtendedClientDisconnectReason(DisconnectReason.AUTHORIZATION_FAILED,
                                                         null,
                                                         "Authorization failed, reason [" + reason + "]",
                                                         null));
    }

    public void authorized(final String sessionId, final IoSessionWrapper session, final String userName) {

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug(
                "[{}] Received AUTHORIZED notification from the authorization provider. SessionId [{}], userName [{}]",
                this.clientSession.getTransportName(),
                sessionId,
                userName);
        }

        this.clientSession.setServerSessionId(sessionId);
        this.clientSession.getRemoteCallSupport().setSession(session);
        if (this.tryToSetState(ClientState.AUTHORIZING, ClientState.ONLINE, null)) {
            this.fireAuthorized();
        }

    }

    void connect() {

        this.setState(ClientState.DISCONNECTED, ClientState.CONNECTING, null);
    }

    void disconnect() {

        this.disconnect(new ExtendedClientDisconnectReason(DisconnectReason.CLIENT_APP_REQUEST,
                                                           null,
                                                           "Client application request",
                                                           null));
    }

    void primaryChannelDisconnected() {

        this.disconnect(this.disconnectReason);
    }

    void disconnect(final ExtendedClientDisconnectReason disconnectReason) {

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

        if (!this.tryToSetState(ClientState.SSL_HANDSHAKE_WAITING, ClientState.SSL_HANDSHAKE_SUCCESSFUL, null)) {
            this.tryToSetState(ClientState.CONNECTING, ClientState.SSL_HANDSHAKE_SUCCESSFUL, null);
        }

    }

    void protocolVersionHandshakeSuccess() {

        if (!this.tryToSetState(ClientState.PROTOCOL_VERSION_NEGOTIATION_WAITING,
                                ClientState.PROTOCOL_VERSION_NEGOTIATION_SUCCESSFUL,
                                null)
            && !this.tryToSetState(ClientState.CONNECTING,
                                   ClientState.PROTOCOL_VERSION_NEGOTIATION_SUCCESSFUL,
                                   null)
            && !this.tryToSetState(ClientState.SSL_HANDSHAKE_SUCCESSFUL,
                                   ClientState.PROTOCOL_VERSION_NEGOTIATION_SUCCESSFUL,
                                   null)) {
            this.tryToSetState(ClientState.SSL_HANDSHAKE_WAITING,
                               ClientState.PROTOCOL_VERSION_NEGOTIATION_SUCCESSFUL,
                               null);
        }

    }

    public void run() {

        while (true) {
            boolean interrupted = false;

            try {
                if (this.terminating) {

                    this.clientState.set(ClientState.DISCONNECTED);
                    this.cleanUp();

                    synchronized (this.executorThreadTerminationLocker) {
                        this.isExecutorThreadTerminated.set(true);
                        this.executorThreadTerminationLocker.notifyAll();
                    }

                    return;
                }


                final ClientState state = this.getClientState();

                final long timeToWait;
                switch (state) {
                    case CONNECTING:
                        this.processConnecting();
                        continue;
                    case SSL_HANDSHAKE_WAITING:
                        if (this.sslHandshakeStartTime + this.clientSession.getSSLHandshakeTimeout()
                            < System.currentTimeMillis()) {
                            LOGGER.info("[{}] SSL handshake timeout", this.clientSession.getTransportName());
                            this.setState(ClientState.SSL_HANDSHAKE_WAITING,
                                          ClientState.DISCONNECTING,
                                          new ExtendedClientDisconnectReason(DisconnectReason.SSL_HANDSHAKE_TIMEOUT,
                                                                             null,
                                                                             "SSL handshake timeout",
                                                                             null));
                            continue;
                        }

                        timeToWait = this.sslHandshakeStartTime + this.clientSession.getSSLHandshakeTimeout()
                                     - System.currentTimeMillis();
                        if (timeToWait <= 0L) {
                            continue;
                        }


                        synchronized (this.stateWaitLock) {
                            if (this.getClientState() == ClientState.SSL_HANDSHAKE_WAITING) {
                                this.stateWaitLock.wait(timeToWait);
                            }
                            continue;
                        }

                    case SSL_HANDSHAKE_SUCCESSFUL:
                        if (this.tryToSetState(ClientState.SSL_HANDSHAKE_SUCCESSFUL,
                                               ClientState.PROTOCOL_VERSION_NEGOTIATION_WAITING,
                                               null)) {
                            this.protocolVersionNegotiationStartTime = System.currentTimeMillis();
                        }
                        continue;
                    case PROTOCOL_VERSION_NEGOTIATION_WAITING:
                        if (this.protocolVersionNegotiationStartTime
                            + this.clientSession.getProtocolVersionNegotiationTimeout() < System.currentTimeMillis()) {
                            LOGGER.info("[{}] Protocol version negotiation timeout",
                                        this.clientSession.getTransportName());
                            this.setState(ClientState.PROTOCOL_VERSION_NEGOTIATION_WAITING,
                                          ClientState.DISCONNECTING,
                                          new ExtendedClientDisconnectReason(DisconnectReason
                                                                                 .PROTOCOL_VERSION_NEGOTIATION_TIMEOUT,
                                                                             null,
                                                                             "Protocol version negotiation "
                                                                             + "timeout",
                                                                             null));
                            continue;
                        }

                        timeToWait = this.protocolVersionNegotiationStartTime
                                     + this.clientSession.getProtocolVersionNegotiationTimeout()
                                     - System.currentTimeMillis();
                        if (timeToWait <= 0L) {
                            continue;
                        }


                        synchronized (this.stateWaitLock) {
                            if (this.getClientState() == ClientState.PROTOCOL_VERSION_NEGOTIATION_WAITING) {
                                this.stateWaitLock.wait(timeToWait);
                            }
                            continue;
                        }

                    case PROTOCOL_VERSION_NEGOTIATION_SUCCESSFUL:
                        if (this.tryToSetState(ClientState.PROTOCOL_VERSION_NEGOTIATION_SUCCESSFUL,
                                               ClientState.AUTHORIZING,
                                               null)) {
                            this.authorizationStartTime = System.currentTimeMillis();
                            this.protocolHandler.handleAuthorization(this.authorizationProvider, this.primarySession);
                        }
                        continue;
                    case AUTHORIZING:
                        if (this.authorizationStartTime + this.clientSession.getAuthorizationTimeout()
                            < System.currentTimeMillis()) {
                            LOGGER.info("[{}] Authorization timeout", this.clientSession.getTransportName());
                            this.setState(ClientState.AUTHORIZING,
                                          ClientState.DISCONNECTING,
                                          new ExtendedClientDisconnectReason(DisconnectReason.AUTHORIZATION_TIMEOUT,
                                                                             null,
                                                                             "Authorization timeout",
                                                                             null));
                            continue;
                        }

                        timeToWait = this.authorizationStartTime + this.clientSession.getAuthorizationTimeout() - System
                            .currentTimeMillis();
                        if (timeToWait <= 0L) {
                            continue;
                        }


                        synchronized (this.stateWaitLock) {
                            if (this.getClientState() == ClientState.AUTHORIZING) {
                                this.stateWaitLock.wait(timeToWait);
                            }
                            continue;
                        }

                    case ONLINE:
                        final boolean sleep = this.processOnline();
                        if (!sleep) {
                            continue;
                        }


                        synchronized (this.stateWaitLock) {
                            if (this.getClientState() == ClientState.ONLINE) {
                                this.stateWaitLock.wait(100L);
                            }
                            continue;
                        }

                    case DISCONNECTING:

                        this.cleanUp();


                        this.setState(ClientState.DISCONNECTING, ClientState.DISCONNECTED);
                        this.fireDisconnected(this.disconnectReason);
                        continue;
                    case DISCONNECTED:

                        synchronized (this.stateWaitLock) {
                            if (this.getClientState() == ClientState.DISCONNECTED) {
                                this.stateWaitLock.wait(10000L);
                            }
                            continue;
                        }

                    default:
                        throw new IllegalArgumentException("Unsupported state " + state);
                }
            } catch (final InterruptedException e) {
                LOGGER.error("Thread is interrupted...", e);
                interrupted = true;
                try {
                    this.clientState.set(ClientState.DISCONNECTED);
                    this.cleanUp();


                } catch (InterruptedException ex) {
                    interrupted = true;
                    LOGGER.error("Thread is interrupted...", ex);
                } finally {
                    synchronized (this.executorThreadTerminationLocker) {
                        this.isExecutorThreadTerminated.set(true);
                        this.executorThreadTerminationLocker.notifyAll();
                    }
                    if (interrupted) {Thread.currentThread().interrupt();}

                }

            } finally {
                synchronized (this.executorThreadTerminationLocker) {
                    this.isExecutorThreadTerminated.set(true);
                    this.executorThreadTerminationLocker.notifyAll();
                }

                if (interrupted) {Thread.currentThread().interrupt();}

            }


        }
    }

    private void processConnecting() throws InterruptedException {

        this.cleanUp();
        this.disconnectReason = null;
        this.authorizationProvider = this.clientSession.getAuthorizationProvider();
        this.authorizationProvider.setListener(this);
        this.primarySessionChannelAttachment = new ChannelAttachment(true);
        this.secondarySessionChannelAttachment = new ChannelAttachment(false);

        if (this.address == null) {
            LOGGER.error(
                "[{}] Address not set in transport and AbstractClientAuthorizationProvider.getAddress returned null",
                this.clientSession.getTransportName());
            this.setState(ClientState.CONNECTING,
                          ClientState.DISCONNECTING,
                          new ExtendedClientDisconnectReason(DisconnectReason.CONNECTION_PROBLEM,
                                                             null,
                                                             "Address is not set in transport client",
                                                             null));

            return;
        }

        LOGGER.debug("[{}] Connecting to [{}]", this.clientSession.getTransportName(), this.address);


        final ChannelFuture connectFuture = this.channelBootstrap.connect(this.address);
        connectFuture.await(this.clientSession.getConnectionTimeout());

        if (connectFuture.isSuccess()) {

            LOGGER.debug("[{}] Successfully connected to [{}]", this.clientSession.getTransportName(), this.address);


            final Channel channel = connectFuture.channel();
            final Attribute<ChannelAttachment>
                attachmentAttribute
                = channel.attr(ChannelAttachment.CHANNEL_ATTACHMENT_ATTRIBUTE_KEY);
            attachmentAttribute.set(this.primarySessionChannelAttachment);
            this.primarySession = channel;

            LOGGER.debug("[{}] primarySession = {}", this.clientSession.getTransportName(), this.primarySession);


            if (this.clientSession.isUseSSL()) {
                if (this.tryToSetState(ClientState.CONNECTING, ClientState.SSL_HANDSHAKE_WAITING, null)) {
                    this.sslHandshakeStartTime = System.currentTimeMillis();
                }
            } else if (this.tryToSetState(ClientState.CONNECTING,
                                          ClientState.PROTOCOL_VERSION_NEGOTIATION_WAITING,
                                          null)) {
                this.protocolVersionNegotiationStartTime = System.currentTimeMillis();
            }
        } else if (connectFuture.isDone() && connectFuture.cause() != null) {
            final Throwable cause = connectFuture.cause();

            LOGGER.debug("[{}] Connect call failed because of {}: {}",
                         this.clientSession.getTransportName(),
                         cause.getClass().getSimpleName(),
                         cause.getMessage());


            if (cause instanceof CertificateException) {
                this.setState(ClientState.CONNECTING,
                              ClientState.DISCONNECTING,
                              new ExtendedClientDisconnectReason(DisconnectReason.CERTIFICATE_EXCEPTION,
                                                                 null,
                                                                 "Certificate exception " + cause.getMessage(),
                                                                 cause));
            } else {
                this.setState(ClientState.CONNECTING,
                              ClientState.DISCONNECTING,
                              new ExtendedClientDisconnectReason(DisconnectReason.CONNECTION_PROBLEM,
                                                                 null,
                                                                 "Unexpected exception " + cause.getMessage(),
                                                                 cause));


            }
        } else {

            LOGGER.debug("[{}] Connect call failed", this.clientSession.getTransportName());


            this.setState(ClientState.CONNECTING,
                          ClientState.DISCONNECTING,
                          new ExtendedClientDisconnectReason(DisconnectReason.CONNECTION_PROBLEM,
                                                             null,
                                                             "Connect call failed",
                                                             null));
        }


    }

    private boolean processOnline() throws InterruptedException {

        if (this.authorizationProvider != null) {
            this.authorizationProvider.cleanUp();
            this.authorizationProvider = null;
        }

        if (this.primarySession != null && this.primarySession.isActive()) {
            this.processPrimarySocketAuthAcceptorMessage();
            if (this.clientSession.getPrimaryConnectionPingInterval() > 0L
                && this.clientSession.getPrimaryConnectionPingTimeout() > 0L) {
                final Attribute<ChannelAttachment> primChannelAttachmentAttribute = this.primarySession.attr(
                    ChannelAttachment.CHANNEL_ATTACHMENT_ATTRIBUTE_KEY);
                final ChannelAttachment primAttachment = primChannelAttachmentAttribute.get();
                final long now = System.currentTimeMillis();
                final boolean needToPing = this.needToPing(now,
                                                           primAttachment,
                                                           this.clientSession.getPrimaryConnectionPingInterval());
                if (needToPing && this.isOnline()) {
                    this.sendPingRequest(this.primarySession,
                                         primAttachment,
                                         this.clientSession.getPrimaryConnectionPingTimeout(),
                                         true);
                }
            }

            if (this.clientSession.isUseFeederSocket() && this.childSocketAuthAcceptorMessage != null) {
                long currentTime;
                if (this.childSession != null && this.childSession.isActive()) {
                    if (this.clientSession.getSecondaryConnectionPingInterval() > 0L
                        && this.clientSession.getSecondaryConnectionPingTimeout() > 0L) {
                        currentTime = System.currentTimeMillis();
                        final boolean needToPing = this.needToPing(currentTime,
                                                                   this.secondarySessionChannelAttachment,
                                                                   this.clientSession
                                                                       .getSecondaryConnectionPingInterval());
                        if (needToPing && this.isOnline()) {
                            this.sendPingRequest(this.childSession,
                                                 this.secondarySessionChannelAttachment,
                                                 this.clientSession.getSecondaryConnectionPingTimeout(),
                                                 false);
                        }
                    }

                    if (this.secondarySessionChannelAttachment.getReconnectAttempt() != 0
                        && (this.secondarySessionChannelAttachment.getLastIoTime()
                            > this.secondarySessionChannelAttachment.getLastConnectAttemptTime()
                            || this.secondarySessionChannelAttachment.getLastConnectAttemptTime()
                               + this.clientSession.getSecondaryConnectionReconnectsResetDelay()
                               < System.currentTimeMillis())) {
                        this.secondarySessionChannelAttachment.setReconnectAttempt(0);
                    }
                } else {
                    if ((long) this.secondarySessionChannelAttachment.getReconnectAttempt()
                        >= this.clientSession.getSecondaryConnectionReconnectAttempts()) {
                        LOGGER.warn("[{}] Child session max connection attempts reached. Disconnecting clientSession",
                                    this.clientSession.getTransportName());
                        if (this.disconnectReason == null) {
                            this.disconnectReason
                                = new ExtendedClientDisconnectReason(DisconnectReason.CONNECTION_PROBLEM,
                                                                     null,
                                                                     "Secondary session max connection attempts "
                                                                     + "reached",
                                                                     null);
                        }

                        this.setState(ClientState.ONLINE, ClientState.DISCONNECTING);
                        return false;
                    }

                    currentTime = System.currentTimeMillis();
                    if (this.secondarySessionChannelAttachment.getLastConnectAttemptTime()
                        + this.clientSession.getReconnectDelay() < currentTime) {
                        if (this.childSession != null) {
                            this.childSession.disconnect();
                            this.childSession.close();
                            this.childSession = null;
                        }

                        currentTime = System.currentTimeMillis();
                        this.secondarySessionChannelAttachment.setLastConnectAttemptTime(currentTime);
                        this.secondarySessionChannelAttachment.resetTimes();
                        this.secondarySessionChannelAttachment.setReconnectAttempt(this.secondarySessionChannelAttachment
                                                                                       .getReconnectAttempt() + 1);
                        if (LOGGER.isDebugEnabled()) {
                            LOGGER.debug("[{}] Trying to connect child session. Attempt {}",
                                         this.clientSession.getTransportName(),
                                         this.secondarySessionChannelAttachment.getReconnectAttempt() + 1);
                        }

                        final ChannelFuture connectFuture = this.channelBootstrap.connect(this.address);
                        connectFuture.await(this.clientSession.getConnectionTimeout());

                        if (connectFuture.isSuccess()) {
                            final Channel channel = connectFuture.channel();
                            final Attribute<ChannelAttachment> channelAttachmentAttribute = channel.attr(
                                ChannelAttachment.CHANNEL_ATTACHMENT_ATTRIBUTE_KEY);
                            channelAttachmentAttribute.set(this.secondarySessionChannelAttachment);
                            final ChannelFuture channelFuture = this.protocolHandler.writeMessage(channel,
                                                                                                  this.childSocketAuthAcceptorMessage);
                            channelFuture.addListener((GenericFutureListener<Future<Void>>) future -> {

                                try {
                                    channelFuture.get();
                                } catch (ExecutionException | InterruptedException e) {
                                    boolean reportException = true;

                                    if (e.getCause() instanceof ClosedChannelException && !isOnline()) {
                                        reportException = false;
                                    }

                                    if (reportException) {
                                        LOGGER.error("[" + clientSession.getTransportName() + "] " + e.getMessage(), e);
                                        disconnect(new ExtendedClientDisconnectReason(DisconnectReason
                                                                                          .CONNECTION_PROBLEM,
                                                                                      null,
                                                                                      "Secondary session error '"
                                                                                      + e.getMessage()
                                                                                      + "' while writting "
                                                                                      + childSocketAuthAcceptorMessage,
                                                                                      e));
                                    }
                                }

                            });
                            this.childSession = channel;
                            return false;
                        }

                        if (connectFuture.isDone() && connectFuture.cause() != null) {
                            final Throwable cause = connectFuture.cause();

                            LOGGER.debug("[{}] Secondary connection connect call failed because of {}: {}",
                                         this.clientSession.getTransportName(),
                                         cause.getClass().getSimpleName(),
                                         cause.getMessage());


                            if (cause instanceof CertificateException) {
                                this.disconnectReason
                                    = new ExtendedClientDisconnectReason(DisconnectReason.CERTIFICATE_EXCEPTION,
                                                                         null,
                                                                         "Secondary connection certificate exception "
                                                                         + cause.getMessage(),
                                                                         cause);
                            }
                        } else {
                            LOGGER.debug("[{}] Secondary connection connect call failed",
                                         this.clientSession.getTransportName());
                        }

                        return false;
                    }
                }
            }

            return true;
        } else {
            LOGGER.warn("[{}] Primary session disconnected. Disconnecting transport client",
                        this.clientSession.getTransportName());
            if (this.disconnectReason == null) {
                this.disconnectReason = new ExtendedClientDisconnectReason(DisconnectReason.CONNECTION_PROBLEM,
                                                                           null,
                                                                           "Primary session is not active",
                                                                           null);
            }

            this.setState(ClientState.ONLINE, ClientState.DISCONNECTING);
            return false;
        }
    }

    private boolean needToPing(final long now, final ChannelAttachment attachment, final long pingInterval) {

        return !attachment.hasLastPingRequestTime() || now - attachment.getLastPingRequestTime() > pingInterval;

    }

    private void processPrimarySocketAuthAcceptorMessage() {

        if (!this.primarySocketAuthAcceptorMessageSent && this.primarySocketAuthAcceptorMessage != null) {
            final ChannelFuture channelFuture = this.protocolHandler.writeMessage(this.primarySession,
                                                                                  this.primarySocketAuthAcceptorMessage);
            channelFuture.addListener((GenericFutureListener<Future<Void>>) future -> {

                try {
                    channelFuture.get();
                } catch (ExecutionException | InterruptedException e) {
                    boolean reportException = true;

                    if (e.getCause() instanceof ClosedChannelException && !isOnline()) {
                        reportException = false;
                    }

                    if (reportException) {
                        LOGGER.error("[" + clientSession.getTransportName() + "] " + e.getMessage(), e);
                        disconnect(new ExtendedClientDisconnectReason(DisconnectReason.CONNECTION_PROBLEM,
                                                                      null,
                                                                      "Primary session error '"
                                                                      + e.getMessage()
                                                                      + "' while writting "
                                                                      + primarySocketAuthAcceptorMessage,
                                                                      e));
                    }
                }

            });
            this.primarySocketAuthAcceptorMessageSent = true;
        }

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

        MessageSentListener messageSentListener = message -> {
            LOGGER.trace("Ping sent in channel, primary:" + " {}", isPrimary);
            pingSocketWriteInterval.set(System.currentTimeMillis() - startTime);
        };

        final RequestListenableFuture future = this.clientSession.sendRequestAsync(pingRequestMessage,
                                                                                   channel,
                                                                                   pingTimeout,
                                                                                   Boolean.TRUE,
                                                                                   messageSentListener);

        LOGGER.trace("[{}] Sending {} connection ping request: {}",
                     this.clientSession.getTransportName(),
                     isPrimary ? "primary" : "secondary",
                     pingRequestMessage);


        future.addListener(() -> {

            /**/
            final long now = System.currentTimeMillis();

            try {
                final ProtocolMessage protocolMessage = future.get();

                if (protocolMessage instanceof HeartbeatOkResponseMessage) {
                    final HeartbeatOkResponseMessage
                        heartbeatOkResponseMessage
                        = (HeartbeatOkResponseMessage) protocolMessage;
                    final long turnOverTime = now - startTime;
                    attachment.pingSuccessfull();
                    final Double systemCpuLoad = sendCpuInfoToServer ? pingManager.getSystemCpuLoad() : null;
                    final Double processCpuLoad = sendCpuInfoToServer ? pingManager.getProcessCpuLoad() : null;
                    pingManager.addPing(turnOverTime,
                                        pingSocketWriteInterval.get(),
                                        systemCpuLoad,
                                        processCpuLoad,
                                        heartbeatOkResponseMessage.getSocketWriteInterval(),
                                        heartbeatOkResponseMessage.getSystemCpuLoad(),
                                        heartbeatOkResponseMessage.getProcessCpuLoad());


                    if (pingListener != null) {
                        pingListener.pingSucceded(turnOverTime,
                                                  pingSocketWriteInterval.get(),
                                                  systemCpuLoad,
                                                  processCpuLoad,
                                                  heartbeatOkResponseMessage.getSocketWriteInterval(),
                                                  heartbeatOkResponseMessage.getSystemCpuLoad(),
                                                  heartbeatOkResponseMessage.getProcessCpuLoad());
                    }


                    LOGGER.trace("{} Synch ping response received: {}, time: {}",
                                 isPrimary ? "primary" : "secondary",
                                 protocolMessage,
                                 turnOverTime);

                } else {
                    attachment.pingFailed();
                    pingManager.pingFailed();
                    ClientConnector.this.safeNotifyPingFailed();

                    LOGGER.debug("Server has returned unknown response type for ping request! Time - {}",
                                 (now - startTime));

                }
            } catch (InterruptedException | ExecutionException e) {

                if (ClientConnector.this.isOnline()) {
                    attachment.pingFailed();
                    pingManager.pingFailed();
                    ClientConnector.this.safeNotifyPingFailed();
                    LOGGER.error("{} session ping failed: {}, timeout: {}, synchRequestId: {}",
                                 isPrimary ? "Primary" : "Secondary",
                                 e.getMessage(),
                                 pingTimeout,
                                 pingRequestMessage.getSynchRequestId());
                }
            } finally {
                ClientConnector.this.checkPingFailed(isPrimary, attachment, pingTimeout, now);
            }

        }, MoreExecutors.newDirectExecutorService());
    }

    private void checkPingFailed(final boolean isPrimary,
                                 final ChannelAttachment attachment,
                                 final long pingTimeout,
                                 final long now) {

        if (!this.isOnline()) {
            return;
        }


        if (this.isPingFailed(attachment, pingTimeout, now)) {
            final Channel channel;
            if (isPrimary) {
                channel = this.primarySession;
                if (channel != null) {
                    channel.disconnect();
                    channel.close();
                }

                LOGGER.warn("[{}] Primary session ping timeout {}ms. Disconnecting...",
                            this.clientSession.getTransportName(),
                            this.clientSession.getPrimaryConnectionPingTimeout());
                if (this.disconnectReason == null) {
                    this.disconnectReason = new ExtendedClientDisconnectReason(DisconnectReason.CONNECTION_PROBLEM,
                                                                               null,
                                                                               "Primary session ping timeout",
                                                                               null);
                }

                this.setState(ClientState.ONLINE, ClientState.DISCONNECTING);

                synchronized (this.stateWaitLock) {
                    this.stateWaitLock.notifyAll();
                }
            } else {
                LOGGER.warn("[{}] Secondary session ping timeout {}ms. Disconnecting secondary session...",
                            this.clientSession.getTransportName(),
                            this.clientSession.getSecondaryConnectionPingTimeout());
                channel = this.childSession;
                if (channel != null) {
                    channel.disconnect();
                    channel.close();
                }
            }
        }

    }

    private boolean isPingFailed(final ChannelAttachment attachment, final long pingTimeout, final long now) {

        final long failedPingCount = attachment.getLastSubsequentFailedPingCount();
        final boolean ioProcessing = this.isIoProcessing(attachment, pingTimeout, now);
        return failedPingCount >= this.maxSubsequentPingFailedCount && !ioProcessing;

    }

    private void safeNotifyPingFailed() {

        if (this.pingListener != null) {
            this.pingListener.pingFailed();
        }
    }

    private void cleanUp() throws InterruptedException {

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("[{}] Closing all sockets and cleaning references", this.clientSession.getTransportName());
        }

        this.closeAllSessions();
        if (this.authorizationProvider != null) {
            this.authorizationProvider.cleanUp();
            this.authorizationProvider = null;
        }

        this.primarySession = null;
        this.childSession = null;
        this.primarySocketAuthAcceptorMessage = null;
        this.childSocketAuthAcceptorMessage = null;
        this.sslHandshakeStartTime = null;
        this.protocolVersionNegotiationStartTime = null;
        this.authorizationStartTime = null;
    }

    private void closeAllSessions() throws InterruptedException {

        if (this.primarySession != null) {
            this.primarySession.disconnect();


            this.primarySession.close().await(SESSION_CLOSE_WAIT_TIME, TimeUnit.SECONDS);


            this.primarySession = null;
        }

        if (this.childSession != null) {
            this.childSession.disconnect();


            this.childSession.close().await(SESSION_CLOSE_WAIT_TIME, TimeUnit.SECONDS);


            this.childSession = null;
        }

    }

    private ClientState getClientState() {

        return this.clientState.get();
    }

    private void setState(final ClientState expected, final ClientState newState) {

        if (!this.clientState.compareAndSet(expected, newState)) {
            if (newState == ClientState.DISCONNECTING) {
                boolean stateChanged = false;

                ClientState state;
                do {
                    state = this.clientState.get();
                    stateChanged = this.clientState.compareAndSet(state, ClientState.DISCONNECTING);
                } while (state != ClientState.DISCONNECTING && state != ClientState.DISCONNECTED && !(stateChanged));

                if (stateChanged && LOGGER.isDebugEnabled()) {
                    LOGGER.debug(STATE_CHANGED_TO, this.clientSession.getTransportName(), newState);
                }
            } else if (this.clientState.get() != newState) {
                throw new IllegalStateException("["
                                                + this.clientSession.getTransportName()
                                                + "] "
                                                + "Expected state ["
                                                + expected.name()
                                                + "], real state ["
                                                + this.clientState.get()
                                                + "]");
            }
        } else if (LOGGER.isDebugEnabled()) {
            LOGGER.debug(STATE_CHANGED_TO, this.clientSession.getTransportName(), newState);
        }


        synchronized (this.stateWaitLock) {
            this.stateWaitLock.notifyAll();
        }
    }

    private void setState(final ClientState expected,
                          final ClientState newState,
                          final ExtendedClientDisconnectReason reason) {

        if (!this.clientState.compareAndSet(expected, newState)) {
            if (newState == ClientState.DISCONNECTING) {
                boolean stateChanged = false;

                ClientState state;
                do {
                    state = this.clientState.get();
                    stateChanged = this.clientState.compareAndSet(state, ClientState.DISCONNECTING);
                } while (state != ClientState.DISCONNECTING && state != ClientState.DISCONNECTED && !(stateChanged));

                if (stateChanged) {
                    this.disconnectReason = reason;

                    LOGGER.debug(STATE_CHANGED_TO, this.clientSession.getTransportName(), newState);

                }
            } else if (this.clientState.get() != newState) {
                throw new IllegalStateException("["
                                                + this.clientSession.getTransportName()
                                                + "] "
                                                + "Expected state ["
                                                + expected.name()
                                                + "], real state ["
                                                + this.clientState.get()
                                                + "]");
            }
        } else {
            this.disconnectReason = reason;

            LOGGER.debug(STATE_CHANGED_TO, this.clientSession.getTransportName(), newState);

        }


        synchronized (this.stateWaitLock) {
            this.stateWaitLock.notifyAll();
        }
    }

    private boolean tryToSetState(final ClientState expected,
                                  final ClientState newState,
                                  final ExtendedClientDisconnectReason reason) {

        if (!this.clientState.compareAndSet(expected, newState)) {
            if (newState == ClientState.DISCONNECTING) {
                boolean stateChanged = false;

                ClientState state;
                do {
                    state = this.clientState.get();
                    stateChanged = this.clientState.compareAndSet(state, ClientState.DISCONNECTING);
                } while (state != ClientState.DISCONNECTING && state != ClientState.DISCONNECTED && !(stateChanged));

                if (stateChanged) {
                    this.disconnectReason = reason;

                    LOGGER.debug(STATE_CHANGED_TO, this.clientSession.getTransportName(), newState);

                }
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

    Channel getPrimarySession() {

        return this.primarySession;
    }

    Channel getChildSession() {

        return this.childSession;
    }

    void setDisconnectReason(final ExtendedClientDisconnectReason disconnectReason) {

        this.disconnectReason = disconnectReason;
    }

    void setChildSocketAuthAcceptorMessage(final ChildSocketAuthAcceptorMessage childSocketAuthAcceptorMessage) {

        this.childSocketAuthAcceptorMessage = childSocketAuthAcceptorMessage;

        synchronized (this.stateWaitLock) {
            this.stateWaitLock.notifyAll();
        }
    }

    void setPrimarySocketAuthAcceptorMessage(final PrimarySocketAuthAcceptorMessage primarySocketAuthAcceptorMessage) {

        this.primarySocketAuthAcceptorMessage = primarySocketAuthAcceptorMessage;

        synchronized (this.stateWaitLock) {
            this.stateWaitLock.notifyAll();
        }
    }

    public void fireDisconnected(ExtendedClientDisconnectReason disconnectReason) {

        this.clientSession.disconnected();
        if (disconnectReason == null) {
            disconnectReason = new ExtendedClientDisconnectReason(DisconnectReason.CONNECTION_PROBLEM,
                                                                  null,
                                                                  "Unknown connection problem",
                                                                  null);
        }

        final CopyOnWriteArrayList<ClientListener> listeners = this.clientSession.getListeners();

        DisconnectedEvent disconnectedEvent = new DisconnectedEvent(clientSession.getTransportClient(),
                                                                    disconnectReason.getDisconnectReason(),
                                                                    disconnectReason.getDisconnectHint(),
                                                                    disconnectReason.getError(),
                                                                    disconnectReason.getDisconnectComments());
        listeners.forEach(clientListener -> protocolHandler.fireDisconnectedEvent(clientListener,
                                                                                  clientSession.getTransportClient(),
                                                                                  disconnectedEvent));


        this.logDisconnectInQueue(disconnectReason);
        this.clientSession.terminate();
    }

    private void logDisconnectInQueue(final ExtendedClientDisconnectReason disconnectReason) {

        final StringBuilder disconnectMessageBuilder = new StringBuilder();
        disconnectMessageBuilder.append("Disconnect task in queue, reason [")
                                .append(disconnectReason.getDisconnectReason())
                                .append("], comments [")
                                .append(disconnectReason.getDisconnectComments())
                                .append("]");
        if (this.clientSession != null) {
            disconnectMessageBuilder.append(", server address [").append(this.clientSession.getAddress()).append(
                "], transport name [").append(this.clientSession.getTransportName()).append("]");
        }

        if (disconnectReason.getError() != null) {
            LOGGER.info(disconnectMessageBuilder.toString(), disconnectReason.getError());
        } else {
            LOGGER.info(disconnectMessageBuilder.toString());
        }

    }

    public void fireAuthorized() {

        final CopyOnWriteArrayList<ClientListener> listeners = this.clientSession.getListeners();

        listeners.forEach(clientListener -> protocolHandler.fireAuthorizedEvent(clientListener,
                                                                                clientSession.getTransportClient()));


        LOGGER.info("Authorize task in queue, server address [{}], transport name [{}]",
                    this.clientSession.getAddress(),
                    this.clientSession.getTransportName());


    }

    public void securityException(final X509Certificate[] chain,
                                  final String authType,
                                  final CertificateException certificateException) {

        LOGGER.error("["
                     + this.clientSession.getTransportName()
                     + "] CERTIFICATE_EXCEPTION: "
                     + certificateException.getMessage(), certificateException);
        if (this.clientSession.getSecurityExceptionHandler() == null
            || !this.clientSession.getSecurityExceptionHandler().isIgnoreSecurityException(chain,
                                                                                           authType,
                                                                                           certificateException)) {
            this.setState(ClientState.SSL_HANDSHAKE_WAITING,
                          ClientState.DISCONNECTING,
                          new ExtendedClientDisconnectReason(DisconnectReason.CERTIFICATE_EXCEPTION,
                                                             null,
                                                             "Certificate exception "
                                                             + certificateException.getMessage(),
                                                             certificateException));
        }

    }

    private boolean isIoProcessing(final ChannelAttachment attachment, final long pingTimeout, final long now) {

        if (attachment == null) {
            return false;
        } else {
            final long lastIoTime = attachment.getLastIoTime();

            return lastIoTime > 0L && now - lastIoTime < pingTimeout;

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
