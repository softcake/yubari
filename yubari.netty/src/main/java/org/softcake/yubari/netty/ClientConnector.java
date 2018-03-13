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


import com.dukascopy.dds4.ping.IPingListener;
import com.dukascopy.dds4.ping.PingManager;
import com.dukascopy.dds4.transport.RequestListenableFuture;
import com.dukascopy.dds4.transport.authorization.AuthorizationProviderListener;
import com.dukascopy.dds4.transport.common.mina.ClientListener;
import com.dukascopy.dds4.transport.common.mina.DisconnectReason;
import com.dukascopy.dds4.transport.common.mina.DisconnectedEvent;
import com.dukascopy.dds4.transport.common.mina.ExtendedClientDisconnectReason;
import com.dukascopy.dds4.transport.common.mina.MessageSentListener;
import com.dukascopy.dds4.transport.common.protocol.mina.IoSessionWrapper;
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
import java.util.Iterator;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

@SuppressWarnings("squid:S128")
class ClientConnector extends Thread implements AuthorizationProviderListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(ClientConnector.class);
    private final Bootstrap channelBootstrap;
    private final TransportClientSession clientSession;
    private final ClientProtocolHandler protocolHandler;
    private final AtomicReference<ClientConnector.ClientState> clientState;
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

        super((clientSession.getTransportName() != null ? "(" + clientSession.getTransportName() + ") " : "")
              + "ClientConnector");
        this.clientState = new AtomicReference(ClientConnector.ClientState.DISCONNECTED);
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

        this.setState(ClientConnector.ClientState.AUTHORIZING,
                      ClientConnector.ClientState.DISCONNECTING,
                      new ExtendedClientDisconnectReason(DisconnectReason.AUTHORIZATION_FAILED, null,
                                                         "Authorization failed, reason [" + reason + "]", null));
    }

    public void authorized(final String sessionId, final IoSessionWrapper session, final String userName) {

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug(
                "[{}] Received AUTHORIZED notification from the authorization provider. SessionId [{}], userName [{}]",
                new Object[]{this.clientSession.getTransportName(), sessionId, userName});
        }

        this.clientSession.setServerSessionId(sessionId);
        this.clientSession.getRemoteCallSupport().setSession(session);
        if (this.tryToSetState(ClientConnector.ClientState.AUTHORIZING,
                               ClientConnector.ClientState.ONLINE, null)) {
            this.fireAuthorized();
        }

    }

    void connect() {

        this.setState(ClientConnector.ClientState.DISCONNECTED,
                      ClientConnector.ClientState.CONNECTING, null);
    }

    void disconnect() {

        this.disconnect(new ExtendedClientDisconnectReason(DisconnectReason.CLIENT_APP_REQUEST, null,
                                                           "Client application request", null));
    }

    void primaryChannelDisconnected() {

        this.disconnect(this.disconnectReason);
    }

    void disconnect(final ExtendedClientDisconnectReason disconnectReason) {

        while (true) {
            final ClientConnector.ClientState state;
            switch (state = this.clientState.get()) {
                case CONNECTING:
                case SSL_HANDSHAKE_WAITING:
                case SSL_HANDSHAKE_SUCCESSFUL:
                case PROTOCOL_VERSION_NEGOTIATION_WAITING:
                case PROTOCOL_VERSION_NEGOTIATION_SUCCESSFUL:
                case AUTHORIZING:
                case ONLINE:

                    if (!this.clientState.compareAndSet(state, ClientConnector.ClientState.DISCONNECTING)) {
                        break;
                    }

                    this.disconnectReason = disconnectReason;
                    if (LOGGER.isDebugEnabled()) {
                        LOGGER.debug("[{}] State changed to {}",
                                     this.clientSession.getTransportName(),
                                     ClientConnector.ClientState.DISCONNECTING);
                    }


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

        if (!this.tryToSetState(ClientConnector.ClientState.SSL_HANDSHAKE_WAITING,
                                ClientConnector.ClientState.SSL_HANDSHAKE_SUCCESSFUL, null)) {
            this.tryToSetState(ClientConnector.ClientState.CONNECTING,
                               ClientConnector.ClientState.SSL_HANDSHAKE_SUCCESSFUL, null);
        }

    }

    void protocolVersionHandshakeSuccess() {

        if (!this.tryToSetState(ClientConnector.ClientState.PROTOCOL_VERSION_NEGOTIATION_WAITING,
                                ClientConnector.ClientState.PROTOCOL_VERSION_NEGOTIATION_SUCCESSFUL, null)
            && !this.tryToSetState(ClientConnector.ClientState.CONNECTING,
                                   ClientConnector.ClientState.PROTOCOL_VERSION_NEGOTIATION_SUCCESSFUL, null)
            && !this.tryToSetState(ClientConnector.ClientState.SSL_HANDSHAKE_SUCCESSFUL,
                                   ClientConnector.ClientState.PROTOCOL_VERSION_NEGOTIATION_SUCCESSFUL, null)) {
            this.tryToSetState(ClientConnector.ClientState.SSL_HANDSHAKE_WAITING,
                               ClientConnector.ClientState.PROTOCOL_VERSION_NEGOTIATION_SUCCESSFUL, null);
        }

    }

    public void run() {

        while (true) {
            boolean var63 = false;

            try {
                var63 = true;
                if (!this.terminating) {
                    try {
                        final ClientConnector.ClientState state = this.getClientState();

                        final long timeToWait;
                        switch (state) {
                            case CONNECTING:
                                this.processConnecting();
                                continue;
                            case SSL_HANDSHAKE_WAITING:
                                if (this.sslHandshakeStartTime + this.clientSession.getSSLHandshakeTimeout()
                                    < System.currentTimeMillis()) {
                                    LOGGER.info("[{}] SSL handshake timeout", this.clientSession.getTransportName());
                                    this.setState(ClientConnector.ClientState.SSL_HANDSHAKE_WAITING,
                                                  ClientConnector.ClientState.DISCONNECTING,
                                                  new ExtendedClientDisconnectReason(DisconnectReason
                                                                                         .SSL_HANDSHAKE_TIMEOUT, null,
                                                                                     "SSL handshake timeout", null));
                                    continue;
                                }

                                timeToWait = this.sslHandshakeStartTime + this.clientSession.getSSLHandshakeTimeout()
                                             - System.currentTimeMillis();
                                if (timeToWait <= 0L) {
                                    continue;
                                }


                                synchronized (this.stateWaitLock) {
                                    if (this.getClientState() == ClientConnector.ClientState.SSL_HANDSHAKE_WAITING) {
                                        this.stateWaitLock.wait(timeToWait);
                                    }
                                    continue;
                                }

                            case SSL_HANDSHAKE_SUCCESSFUL:
                                if (this.tryToSetState(ClientConnector.ClientState.SSL_HANDSHAKE_SUCCESSFUL,
                                                       ClientConnector.ClientState.PROTOCOL_VERSION_NEGOTIATION_WAITING,
                                                       null)) {
                                    this.protocolVersionNegotiationStartTime = System.currentTimeMillis();
                                }
                                continue;
                            case PROTOCOL_VERSION_NEGOTIATION_WAITING:
                                if (this.protocolVersionNegotiationStartTime
                                    + this.clientSession.getProtocolVersionNegotiationTimeout()
                                    < System.currentTimeMillis()) {
                                    LOGGER.info("[{}] Protocol version negotiation timeout",
                                                this.clientSession.getTransportName());
                                    this.setState(ClientConnector.ClientState.PROTOCOL_VERSION_NEGOTIATION_WAITING,
                                                  ClientConnector.ClientState.DISCONNECTING,
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
                                    if (this.getClientState()
                                        == ClientConnector.ClientState.PROTOCOL_VERSION_NEGOTIATION_WAITING) {
                                        this.stateWaitLock.wait(timeToWait);
                                    }
                                    continue;
                                }

                            case PROTOCOL_VERSION_NEGOTIATION_SUCCESSFUL:
                                if (this.tryToSetState(ClientConnector.ClientState
                                                           .PROTOCOL_VERSION_NEGOTIATION_SUCCESSFUL,
                                                       ClientConnector.ClientState.AUTHORIZING, null)) {
                                    this.authorizationStartTime = System.currentTimeMillis();
                                    this.protocolHandler.handleAuthorization(this.authorizationProvider,
                                                                             this.primarySession);
                                }
                                continue;
                            case AUTHORIZING:
                                if (this.authorizationStartTime + this.clientSession.getAuthorizationTimeout()
                                    < System.currentTimeMillis()) {
                                    LOGGER.info("[{}] Authorization timeout", this.clientSession.getTransportName());
                                    this.setState(ClientConnector.ClientState.AUTHORIZING,
                                                  ClientConnector.ClientState.DISCONNECTING,
                                                  new ExtendedClientDisconnectReason(DisconnectReason
                                                                                         .AUTHORIZATION_TIMEOUT, null,
                                                                                     "Authorization timeout", null));
                                    continue;
                                }

                                timeToWait = this.authorizationStartTime + this.clientSession.getAuthorizationTimeout()
                                             - System.currentTimeMillis();
                                if (timeToWait <= 0L) {
                                    continue;
                                }


                                synchronized (this.stateWaitLock) {
                                    if (this.getClientState() == ClientConnector.ClientState.AUTHORIZING) {
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
                                    if (this.getClientState() == ClientConnector.ClientState.ONLINE) {
                                        this.stateWaitLock.wait(100L);
                                    }
                                    continue;
                                }

                            case DISCONNECTING:
                                try {
                                    this.cleanUp();
                                } catch (final Exception var69) {
                                    LOGGER.error("["
                                                 + this.clientSession.getTransportName()
                                                 + "] "
                                                 + var69.getMessage(), var69);

                                }

                                this.setState(ClientConnector.ClientState.DISCONNECTING,
                                              ClientConnector.ClientState.DISCONNECTED);
                                this.fireDisconnected(this.disconnectReason);
                                continue;
                            case DISCONNECTED:

                                synchronized (this.stateWaitLock) {
                                    if (this.getClientState() == ClientConnector.ClientState.DISCONNECTED) {
                                        this.stateWaitLock.wait(10000L);
                                    }
                                    continue;
                                }

                            default:
                                throw new IllegalArgumentException("Unsupported state " + state);
                        }
                    } catch (final Throwable var74) {
                        LOGGER.error("State processing error: " + var74.getMessage(), var74);
                        continue;
                    }
                }

                var63 = false;
            } finally {
                if (var63) {
                    boolean var48 = false;

                    try {
                        var48 = true;
                        this.clientState.set(ClientConnector.ClientState.DISCONNECTED);
                        this.cleanUp();
                        var48 = false;
                    } finally {
                        if (var48) {
                            final Object var17 = this.executorThreadTerminationLocker;
                            synchronized (this.executorThreadTerminationLocker) {
                                this.isExecutorThreadTerminated.set(true);
                                this.executorThreadTerminationLocker.notifyAll();
                            }
                        }
                    }

                    final Object var14 = this.executorThreadTerminationLocker;
                    synchronized (this.executorThreadTerminationLocker) {
                        this.isExecutorThreadTerminated.set(true);
                        this.executorThreadTerminationLocker.notifyAll();
                    }
                }
            }

            boolean var33 = false;

            try {
                var33 = true;
                this.clientState.set(ClientConnector.ClientState.DISCONNECTED);
                this.cleanUp();
                var33 = false;
            } finally {
                if (var33) {
                    final Object var11 = this.executorThreadTerminationLocker;
                    synchronized (this.executorThreadTerminationLocker) {
                        this.isExecutorThreadTerminated.set(true);
                        this.executorThreadTerminationLocker.notifyAll();
                    }
                }
            }

            final Object var78 = this.executorThreadTerminationLocker;
            synchronized (this.executorThreadTerminationLocker) {
                this.isExecutorThreadTerminated.set(true);
                this.executorThreadTerminationLocker.notifyAll();
                return;
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

        try {
            final InetSocketAddress newAddress = this.authorizationProvider.getAddress();
            if (newAddress != null) {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("[{}] Got new address from authorization provider {}",
                                 this.clientSession.getTransportName(),
                                 newAddress);
                }

                this.address = newAddress;
            }
        } catch (final Exception var4) {
            LOGGER.error("["
                         + this.clientSession.getTransportName()
                         + "] AbstractClientAuthorizationProvider.getAddress error: "
                         + var4.getMessage(), var4);
            final DisconnectReason reason = DisconnectReason.CONNECTION_PROBLEM;
            this.setState(ClientConnector.ClientState.CONNECTING,
                          ClientConnector.ClientState.DISCONNECTING,
                          new ExtendedClientDisconnectReason(reason, null,
                                                             "Exception during adrres resolving " + var4.getMessage(),
                                                             var4));
            return;
        }

        if (this.address == null) {
            LOGGER.error(
                "[{}] Address not set in transport and AbstractClientAuthorizationProvider.getAddress returned null",
                this.clientSession.getTransportName());
            this.setState(ClientConnector.ClientState.CONNECTING,
                          ClientConnector.ClientState.DISCONNECTING,
                          new ExtendedClientDisconnectReason(DisconnectReason.CONNECTION_PROBLEM, null,
                                                             "Address is not set in transport client", null));
        } else {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("[{}] Connecting to [{}]", this.clientSession.getTransportName(), this.address);
            }

            final ChannelFuture connectFuture = this.channelBootstrap.connect(this.address);
            connectFuture.await(this.clientSession.getConnectionTimeout());
            if (connectFuture.isSuccess()) {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("[{}] Successfully connected to [{}]",
                                 this.clientSession.getTransportName(),
                                 this.address);
                }

                final Channel channel = connectFuture.channel();
                final Attribute<ChannelAttachment>
                    attachmentAttribute
                    = channel.attr(ChannelAttachment.CHANNEL_ATTACHMENT_ATTRIBUTE_KEY);
                attachmentAttribute.set(this.primarySessionChannelAttachment);
                this.primarySession = channel;
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("[{}] primarySession = {}",
                                 this.clientSession.getTransportName(),
                                 this.primarySession);
                }

                if (this.clientSession.isUseSSL()) {
                    if (this.tryToSetState(ClientConnector.ClientState.CONNECTING,
                                           ClientConnector.ClientState.SSL_HANDSHAKE_WAITING, null)) {
                        this.sslHandshakeStartTime = System.currentTimeMillis();
                    }
                } else if (this.tryToSetState(ClientConnector.ClientState.CONNECTING,
                                              ClientConnector.ClientState.PROTOCOL_VERSION_NEGOTIATION_WAITING, null)) {
                    this.protocolVersionNegotiationStartTime = System.currentTimeMillis();
                }
            } else if (connectFuture.isDone() && connectFuture.cause() != null) {
                final Throwable cause = connectFuture.cause();
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("[{}] Connect call failed because of {}: {}",
                                 new Object[]{this.clientSession.getTransportName(),
                                              cause.getClass().getSimpleName(),
                                              cause.getMessage()});
                }

                if (!(cause instanceof CertificateException)
                    && !(cause instanceof javax.security.cert.CertificateException)) {
                    this.setState(ClientConnector.ClientState.CONNECTING,
                                  ClientConnector.ClientState.DISCONNECTING,
                                  new ExtendedClientDisconnectReason(DisconnectReason.CONNECTION_PROBLEM, null,
                                                                     "Unexpected exception " + cause.getMessage(),
                                                                     cause));
                } else {
                    this.setState(ClientConnector.ClientState.CONNECTING,
                                  ClientConnector.ClientState.DISCONNECTING,
                                  new ExtendedClientDisconnectReason(DisconnectReason.CERTIFICATE_EXCEPTION, null,
                                                                     "Certificate exception " + cause.getMessage(),
                                                                     cause));
                }
            } else {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("[{}] Connect call failed", this.clientSession.getTransportName());
                }

                this.setState(ClientConnector.ClientState.CONNECTING,
                              ClientConnector.ClientState.DISCONNECTING,
                              new ExtendedClientDisconnectReason(DisconnectReason.CONNECTION_PROBLEM, null,
                                                                 "Connect call failed", null));
            }

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
                final Attribute<ChannelAttachment>
                    primChannelAttachmentAttribute
                    = this.primarySession.attr(ChannelAttachment.CHANNEL_ATTACHMENT_ATTRIBUTE_KEY);
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
                                                                   this.clientSession.getSecondaryConnectionPingInterval());
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
                                = new ExtendedClientDisconnectReason(DisconnectReason.CONNECTION_PROBLEM, null,
                                                                     "Secondary session max connection attempts "
                                                                     + "reached", null);
                        }

                        this.setState(ClientConnector.ClientState.ONLINE, ClientConnector.ClientState.DISCONNECTING);
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
                            final Attribute<ChannelAttachment>
                                channelAttachmentAttribute
                                = channel.attr(ChannelAttachment.CHANNEL_ATTACHMENT_ATTRIBUTE_KEY);
                            channelAttachmentAttribute.set(this.secondarySessionChannelAttachment);
                            final ChannelFuture channelFuture = this.protocolHandler.writeMessage(channel,
                                                                                                  this.childSocketAuthAcceptorMessage);
                            channelFuture.addListener(new GenericFutureListener<Future<Void>>() {
                                public void operationComplete(final Future<Void> future) throws Exception {

                                    try {
                                        channelFuture.get();
                                    } catch (final Exception var5) {
                                        boolean reportException = true;
                                        if (var5 instanceof ExecutionException) {
                                            final Throwable t = var5.getCause();
                                            if (t instanceof ClosedChannelException
                                                && !ClientConnector.this.isOnline()) {
                                                reportException = false;
                                            }
                                        }

                                        if (reportException) {
                                            ClientConnector.LOGGER.error("["
                                                                         + ClientConnector.this.clientSession
                                                                             .getTransportName()
                                                                         + "] "
                                                                         + var5.getMessage(), var5);
                                            ClientConnector.this.disconnect(new ExtendedClientDisconnectReason(
                                                DisconnectReason.CONNECTION_PROBLEM, null,
                                                "Secondary session error '"
                                                + var5.getMessage()
                                                + "' while writting "
                                                + ClientConnector.this.childSocketAuthAcceptorMessage,
                                                var5));
                                        }
                                    }

                                }
                            });
                            this.childSession = channel;
                            return false;
                        }

                        if (connectFuture.isDone() && connectFuture.cause() != null) {
                            final Throwable cause = connectFuture.cause();
                            if (LOGGER.isDebugEnabled()) {
                                LOGGER.debug("[{}] Secondary connection connect call failed because of {}: {}",
                                             new Object[]{this.clientSession.getTransportName(),
                                                          cause.getClass().getSimpleName(),
                                                          cause.getMessage()});
                            }

                            if (cause instanceof CertificateException
                                || cause instanceof javax.security.cert.CertificateException) {
                                this.disconnectReason
                                    = new ExtendedClientDisconnectReason(DisconnectReason.CERTIFICATE_EXCEPTION, null,
                                                                         "Secondary connection certificate exception "
                                                                         + cause.getMessage(),
                                                                         cause);
                            }
                        } else if (LOGGER.isDebugEnabled()) {
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
                this.disconnectReason = new ExtendedClientDisconnectReason(DisconnectReason.CONNECTION_PROBLEM, null,
                                                                           "Primary session is not active", null);
            }

            this.setState(ClientConnector.ClientState.ONLINE, ClientConnector.ClientState.DISCONNECTING);
            return false;
        }
    }

    private boolean needToPing(final long now, final ChannelAttachment attachment, final long pingInterval) {

        final boolean result = !attachment.hasLastPingRequestTime()
                               || now - attachment.getLastPingRequestTime() > pingInterval;
        return result;
    }

    private void processPrimarySocketAuthAcceptorMessage() {

        if (!this.primarySocketAuthAcceptorMessageSent && this.primarySocketAuthAcceptorMessage != null) {
            final ChannelFuture channelFuture = this.protocolHandler.writeMessage(this.primarySession,
                                                                                  this.primarySocketAuthAcceptorMessage);
            channelFuture.addListener(new GenericFutureListener<Future<Void>>() {
                public void operationComplete(final Future<Void> future) throws Exception {

                    try {
                        channelFuture.get();
                    } catch (final Exception var5) {
                        boolean reportException = true;
                        if (var5 instanceof ExecutionException) {
                            final Throwable t = var5.getCause();
                            if (t instanceof ClosedChannelException && !ClientConnector.this.isOnline()) {
                                reportException = false;
                            }
                        }

                        if (reportException) {
                            ClientConnector.LOGGER.error("["
                                                         + ClientConnector.this.clientSession.getTransportName()
                                                         + "] "
                                                         + var5.getMessage(), var5);
                            ClientConnector.this.disconnect(new ExtendedClientDisconnectReason(DisconnectReason
                                                                                                   .CONNECTION_PROBLEM,
                                                                                               null,
                                                                                               "Primary session error '"
                                                                                               + var5.getMessage()
                                                                                               + "' while writting "
                                                                                               + ClientConnector.this
                                                                                                   .primarySocketAuthAcceptorMessage,
                                                                                               var5));
                        }
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
        final AtomicLong pingSocketWriteInterval = new AtomicLong(9223372036854775807L);
        final RequestListenableFuture f = this.clientSession.sendRequestAsync(pingRequestMessage,
                                                                              channel,
                                                                              pingTimeout,
                                                                              true,
                                                                              new MessageSentListener() {
                                                                                  public void messageSent(
                                                                                      final ProtocolMessage message) {

                                                                                      if (ClientConnector.LOGGER
                                                                                          .isTraceEnabled()) {
                                                                                          ClientConnector.LOGGER.trace(
                                                                                              "Ping sent in channel, "
                                                                                              + "primary: {}",
                                                                                              isPrimary);
                                                                                      }

                                                                                      pingSocketWriteInterval.set(System
                                                                                                                      .currentTimeMillis()
                                                                                                                  -
                                                                                                                  startTime);
                                                                                  }
                                                                              });
        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace("[{}] Sending {} connection ping request: {}",
                         new Object[]{this.clientSession.getTransportName(),
                                      isPrimary ? "primary" : "secondary",
                                      pingRequestMessage});
        }

        f.addListener(new Runnable() {
            public void run() {

                final long now = System.currentTimeMillis();

                try {
                    final ProtocolMessage heartbeatResponseMessage = f.get();
                    if (heartbeatResponseMessage instanceof HeartbeatOkResponseMessage) {
                        final HeartbeatOkResponseMessage okResponse = (HeartbeatOkResponseMessage) heartbeatResponseMessage;
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
                                            okResponse.getSocketWriteInterval(),
                                            okResponse.getSystemCpuLoad(),
                                            okResponse.getProcessCpuLoad());

                        try {
                            if (ClientConnector.this.pingListener != null) {
                                ClientConnector.this.pingListener.pingSucceded(turnOverTime,
                                                                               pingSocketWriteInterval.get(),
                                                                               systemCpuLoad,
                                                                               processCpuLoad,
                                                                               okResponse.getSocketWriteInterval(),
                                                                               okResponse.getSystemCpuLoad(),
                                                                               okResponse.getProcessCpuLoad());
                            }
                        } catch (final Throwable var14) {
                            ClientConnector.LOGGER.error(var14.getLocalizedMessage(), var14);
                        }

                        if (ClientConnector.LOGGER.isTraceEnabled()) {
                            ClientConnector.LOGGER.trace("{} Synch ping response received: {}, time: {}",
                                                         new Object[]{isPrimary ? "primary" : "secondary",
                                                                      heartbeatResponseMessage,
                                                                      turnOverTime});
                        }
                    } else {
                        attachment.pingFailed();
                        pingManager.pingFailed();
                        ClientConnector.this.safeNotifyPingFailed();
                        if (ClientConnector.LOGGER.isDebugEnabled()) {
                            ClientConnector.LOGGER.debug(
                                "Server has returned unknown response type for ping request! Time - " + (now
                                                                                                         - startTime));
                        }
                    }
                } catch (final Exception var15) {
                    if (ClientConnector.this.isOnline()) {
                        attachment.pingFailed();
                        pingManager.pingFailed();
                        ClientConnector.this.safeNotifyPingFailed();
                        ClientConnector.LOGGER.error("{} session ping failed: {}, timeout: {}, synchRequestId: {}",
                                                     new Object[]{isPrimary ? "Primary" : "Secondary",
                                                                  var15.getMessage(),
                                                                  pingTimeout,
                                                                  pingRequestMessage.getSynchRequestId()});
                    }
                } finally {
                    ClientConnector.this.checkPingFailed(isPrimary, attachment, pingTimeout, now);
                }

            }
        }, MoreExecutors.newDirectExecutorService());
    }

    private void checkPingFailed(final boolean isPrimary, final ChannelAttachment attachment, final long pingTimeout, final long now) {

        try {
            if (this.isOnline()) {
                final boolean pingFailed = this.isPingFailed(attachment, pingTimeout, now);
                if (pingFailed) {
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
                            this.disconnectReason
                                = new ExtendedClientDisconnectReason(DisconnectReason.CONNECTION_PROBLEM, null,
                                                                     "Primary session ping timeout", null);
                        }

                        this.setState(ClientConnector.ClientState.ONLINE, ClientConnector.ClientState.DISCONNECTING);
                        final Object var9 = this.stateWaitLock;
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
        } catch (final Throwable var12) {
            LOGGER.error(this.clientSession.getTransportName()
                         + " failed to check ping for "
                         + (isPrimary
                            ? "primary"
                            : "secondary")
                         + " connection", var12);

        }

    }

    private boolean isPingFailed(final ChannelAttachment attachment, final long pingTimeout, final long now) {

        final long failedPingCount = attachment.getLastSubsequentFailedPingCount();
        final boolean ioProcessing = this.isIoProcessing(attachment, pingTimeout, now);
        final boolean result = failedPingCount >= this.maxSubsequentPingFailedCount && !ioProcessing;
        return result;
    }

    private void safeNotifyPingFailed() {

        try {
            if (this.pingListener != null) {
                this.pingListener.pingFailed();
            }
        } catch (final Throwable var2) {
            LOGGER.error(var2.getLocalizedMessage(), var2);
        }

    }

    private void cleanUp() {

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

    private void closeAllSessions() {

        if (this.primarySession != null) {
            this.primarySession.disconnect();

            try {
                this.primarySession.close().await(5L, TimeUnit.SECONDS);
            } catch (final InterruptedException var3) {
                ;
            }

            this.primarySession = null;
        }

        if (this.childSession != null) {
            this.childSession.disconnect();

            try {
                this.childSession.close().await(5L, TimeUnit.SECONDS);
            } catch (final InterruptedException var2) {
                ;
            }

            this.childSession = null;
        }

    }

    private ClientConnector.ClientState getClientState() {

        return this.clientState.get();
    }

    private void setState(final ClientConnector.ClientState expected, final ClientConnector.ClientState newState) {

        if (!this.clientState.compareAndSet(expected, newState)) {
            if (newState == ClientConnector.ClientState.DISCONNECTING) {
                boolean stateChanged = false;

                ClientConnector.ClientState state;
                do {
                    state = this.clientState.get();
                } while (state != ClientConnector.ClientState.DISCONNECTING
                         && state != ClientConnector.ClientState.DISCONNECTED
                         && !(stateChanged = this.clientState.compareAndSet(state,
                                                                            ClientConnector.ClientState.DISCONNECTING)));

                if (stateChanged && LOGGER.isDebugEnabled()) {
                    LOGGER.debug("[{}] State changed to {}", this.clientSession.getTransportName(), newState);
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
            LOGGER.debug("[{}] State changed to {}", this.clientSession.getTransportName(), newState);
        }

        final Object var7 = this.stateWaitLock;
        synchronized (this.stateWaitLock) {
            this.stateWaitLock.notifyAll();
        }
    }

    private void setState(final ClientConnector.ClientState expected,
                          final ClientConnector.ClientState newState,
                          final ExtendedClientDisconnectReason reason) {

        if (!this.clientState.compareAndSet(expected, newState)) {
            if (newState == ClientConnector.ClientState.DISCONNECTING) {
                boolean stateChanged = false;

                ClientConnector.ClientState state;
                do {
                    state = this.clientState.get();
                } while (state != ClientConnector.ClientState.DISCONNECTING
                         && state != ClientConnector.ClientState.DISCONNECTED
                         && !(stateChanged = this.clientState.compareAndSet(state,
                                                                            ClientConnector.ClientState.DISCONNECTING)));

                if (stateChanged) {
                    this.disconnectReason = reason;
                    if (LOGGER.isDebugEnabled()) {
                        LOGGER.debug("[{}] State changed to {}", this.clientSession.getTransportName(), newState);
                    }
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
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("[{}] State changed to {}", this.clientSession.getTransportName(), newState);
            }
        }

        final Object var8 = this.stateWaitLock;
        synchronized (this.stateWaitLock) {
            this.stateWaitLock.notifyAll();
        }
    }

    private boolean tryToSetState(final ClientConnector.ClientState expected,
                                  final ClientConnector.ClientState newState,
                                  final ExtendedClientDisconnectReason reason) {

        if (!this.clientState.compareAndSet(expected, newState)) {
            if (newState == ClientConnector.ClientState.DISCONNECTING) {
                boolean stateChanged = false;

                ClientConnector.ClientState state;
                do {
                    state = this.clientState.get();
                } while (state != ClientConnector.ClientState.DISCONNECTING
                         && state != ClientConnector.ClientState.DISCONNECTED
                         && !(stateChanged = this.clientState.compareAndSet(state,
                                                                            ClientConnector.ClientState.DISCONNECTING)));

                if (stateChanged) {
                    this.disconnectReason = reason;
                    if (LOGGER.isDebugEnabled()) {
                        LOGGER.debug("[{}] State changed to {}", this.clientSession.getTransportName(), newState);
                    }
                }
            } else if (this.clientState.get() != newState) {
                return false;
            }
        } else {
            this.disconnectReason = reason;
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("[{}] State changed to {}", this.clientSession.getTransportName(), newState);
            }
        }

        final Object var8 = this.stateWaitLock;
        synchronized (this.stateWaitLock) {
            this.stateWaitLock.notifyAll();
            return true;
        }
    }

    boolean isOnline() {

        return this.clientState.get() == ClientConnector.ClientState.ONLINE;
    }

    boolean isConnecting() {

        final ClientConnector.ClientState state = this.clientState.get();
        return state == ClientConnector.ClientState.CONNECTING
               || state == ClientConnector.ClientState.SSL_HANDSHAKE_WAITING
               || state == ClientConnector.ClientState.SSL_HANDSHAKE_SUCCESSFUL
               || state == ClientConnector.ClientState.PROTOCOL_VERSION_NEGOTIATION_WAITING
               || state == ClientConnector.ClientState.PROTOCOL_VERSION_NEGOTIATION_SUCCESSFUL
               || state == ClientConnector.ClientState.AUTHORIZING;
    }

    boolean isAuthorizing() {

        return this.clientState.get() == ClientConnector.ClientState.AUTHORIZING;
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
        final Object var2 = this.stateWaitLock;
        synchronized (this.stateWaitLock) {
            this.stateWaitLock.notifyAll();
        }
    }

    void setPrimarySocketAuthAcceptorMessage(final PrimarySocketAuthAcceptorMessage primarySocketAuthAcceptorMessage) {

        this.primarySocketAuthAcceptorMessage = primarySocketAuthAcceptorMessage;
        final Object var2 = this.stateWaitLock;
        synchronized (this.stateWaitLock) {
            this.stateWaitLock.notifyAll();
        }
    }

    public void fireDisconnected(ExtendedClientDisconnectReason disconnectReason) {

        this.clientSession.disconnected();
        if (disconnectReason == null) {
            disconnectReason = new ExtendedClientDisconnectReason(DisconnectReason.CONNECTION_PROBLEM, null,
                                                                  "Unknown connection problem", null);
        }

        final CopyOnWriteArrayList<ClientListener> listeners = this.clientSession.getListeners();
        final Iterator i$ = listeners.iterator();

        while (i$.hasNext()) {
            final ClientListener clientListener = (ClientListener) i$.next();
            this.protocolHandler.fireDisconnectedEvent(clientListener,
                                                       this.clientSession.getTransportClient(),
                                                       new DisconnectedEvent(this.clientSession.getTransportClient(),
                                                                             disconnectReason.getDisconnectReason(),
                                                                             disconnectReason.getDisconnectHint(),
                                                                             disconnectReason.getError(),
                                                                             disconnectReason.getDisconnectComments()));
        }

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
        final Iterator i$ = listeners.iterator();

        while (i$.hasNext()) {
            final ClientListener clientListener = (ClientListener) i$.next();
            this.protocolHandler.fireAuthorizedEvent(clientListener, this.clientSession.getTransportClient());
        }

        if (this.clientSession != null) {
            LOGGER.info("Authorize task in queue, server address [{}], transport name [{}]",
                        new Object[]{this.clientSession.getAddress(), this.clientSession.getTransportName()});
        } else {
            LOGGER.info("Authorize task in queue");
        }

    }

    public void securityException(final X509Certificate[] chain, final String authType, final CertificateException certificateException) {

        LOGGER.error("["
                     + this.clientSession.getTransportName()
                     + "] CERTIFICATE_EXCEPTION: "
                     + certificateException.getMessage(), certificateException);
        if (this.clientSession.getSecurityExceptionHandler() == null) {
            this.setState(ClientConnector.ClientState.SSL_HANDSHAKE_WAITING,
                          ClientConnector.ClientState.DISCONNECTING,
                          new ExtendedClientDisconnectReason(DisconnectReason.CERTIFICATE_EXCEPTION, null,
                                                             "Certificate exception "
                                                             + certificateException.getMessage(),
                                                             certificateException));
        } else if (!this.clientSession.getSecurityExceptionHandler().isIgnoreSecurityException(chain,
                                                                                               authType,
                                                                                               certificateException)) {
            this.setState(ClientConnector.ClientState.SSL_HANDSHAKE_WAITING,
                          ClientConnector.ClientState.DISCONNECTING,
                          new ExtendedClientDisconnectReason(DisconnectReason.CERTIFICATE_EXCEPTION, null,
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
            if (lastIoTime > 0L) {
                final boolean result = now - lastIoTime < pingTimeout;
                return result;
            } else {
                return false;
            }
        }
    }

    boolean isTerminating() {

        return this.terminating;
    }

    void setTerminating(final long terminationAwaitMaxTimeoutInMillis) {

        this.terminating = true;
        Object var3 = this.stateWaitLock;
        synchronized (this.stateWaitLock) {
            this.stateWaitLock.notifyAll();
        }

        if (terminationAwaitMaxTimeoutInMillis > 0L && !this.isExecutorThreadTerminated.get()) {
            var3 = this.executorThreadTerminationLocker;
            synchronized (this.executorThreadTerminationLocker) {
                if (!this.isExecutorThreadTerminated.get()) {
                    try {
                        this.executorThreadTerminationLocker.wait(terminationAwaitMaxTimeoutInMillis);
                    } catch (final InterruptedException var6) {
                        ;
                    }
                }
            }
        }

    }

    public static enum ClientState {
        CONNECTING,
        SSL_HANDSHAKE_WAITING,
        SSL_HANDSHAKE_SUCCESSFUL,
        PROTOCOL_VERSION_NEGOTIATION_WAITING,
        PROTOCOL_VERSION_NEGOTIATION_SUCCESSFUL,
        AUTHORIZING,
        ONLINE,
        DISCONNECTING,
        DISCONNECTED;

        private ClientState() {

        }
    }
}
