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

import org.softcake.yubari.netty.channel.ChannelAttachment;
import org.softcake.yubari.netty.mina.ClientDisconnectReason;
import org.softcake.yubari.netty.mina.ClientListener;
import org.softcake.yubari.netty.mina.DisconnectedEvent;

import com.dukascopy.dds4.transport.common.mina.DisconnectReason;
import com.dukascopy.dds4.transport.msg.system.ChildSocketAuthAcceptorMessage;
import com.dukascopy.dds4.transport.msg.system.PrimarySocketAuthAcceptorMessage;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.util.Attribute;
import io.reactivex.Single;
import io.reactivex.SingleEmitter;
import io.reactivex.SingleOnSubscribe;
import io.reactivex.functions.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.nio.channels.ClosedChannelException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * @author The softcake Authors.
 */
@SuppressWarnings("squid:S128")
public class ClientConnectorOld extends Thread  {
    private static final long SESSION_CLOSE_WAIT_TIME = 5L;
    private static final String STATE_CHANGED_TO = "[{}] State changed to {}";
    private static final long TIME_TO_WAIT_FOR_DISCONNECTING = 10000L;
    private static final Logger LOGGER = LoggerFactory.getLogger(ClientConnectorOld.class);
    private final Bootstrap channelBootstrap;
    private final TransportClientSession clientSession;
    private final ClientProtocolHandler protocolHandler;
    private final AtomicReference<ClientState> clientState;
    private final Object stateWaitLock;
    private final AtomicBoolean isExecutorThreadTerminated;
    private final Object executorThreadTerminationLocker;

    private final InetSocketAddress address;


    private volatile ClientDisconnectReason disconnectReason;
    private volatile boolean terminating;
    private volatile Channel primaryChannel;
    private ChannelAttachment primarySessionChannelAttachment;
    private volatile Channel childChannel;
    private ChannelAttachment childSessionChannelAttachment;
    //private ClientAuthorizationProvider authorizationProvider;
    private Long sslHandshakeStartTime;
    private Long protocolVersionNegotiationStartTime;
    private Long authorizationStartTime;
    private PrimarySocketAuthAcceptorMessage primarySocketAuthAcceptorMessage;
    private boolean primarySocketAuthAcceptorMessageSent;
    private ChildSocketAuthAcceptorMessage childSocketAuthAcceptorMessage;
    private boolean isProcessConnecting = false;

    public ClientConnectorOld(final InetSocketAddress address,
                              final Bootstrap channelBootstrap,
                              final TransportClientSession clientSession,
                              final ClientProtocolHandler protocolHandler) {

        super((clientSession.getTransportName() != null ? String.format("(%s) ", clientSession.getTransportName()) : "")
              + "ClientConnectorOld");
        this.clientState = new AtomicReference<>(ClientState.DISCONNECTED);
        this.stateWaitLock = new Object();
        this.terminating = false;
        this.isExecutorThreadTerminated = new AtomicBoolean(false);
        this.executorThreadTerminationLocker = new Object();
        this.address = address;
        this.channelBootstrap = channelBootstrap;
        this.clientSession = clientSession;
        this.protocolHandler = protocolHandler;


    }

    private synchronized ClientDisconnectReason getDisconnectReason() {

        return this.disconnectReason;
    }

    private static Channel setChannelAttributeForSuccessfullyConnection(final Channel channel,
                                                                        final ChannelAttachment attachment) {

        channel.attr(CHANNEL_ATTACHMENT_ATTRIBUTE_KEY).set(attachment);

        return channel;
    }

    public void authorizationError(final String errorReason) {


        LOGGER.debug("[{}] Received AUTHORIZATION_ERROR notification from the authorization provider, reason: [{}]",
                     this.clientSession.getTransportName(),
                     errorReason);


        final ClientDisconnectReason reason = new ClientDisconnectReason(DisconnectReason.AUTHORIZATION_FAILED,
                                                                         String.format("Authorization failed, "
                                                                                       + "reason [%s]", errorReason));

        this.tryToSetState(ClientState.AUTHORIZING, ClientState.DISCONNECTING, reason, Boolean.TRUE);

    }

    public void authorized(final String sessionId, final String userName) {

        LOGGER.debug(
            "[{}] Received AUTHORIZED notification from the authorization provider. SessionId [{}], userName [{}]",
            this.clientSession.getTransportName(),
            sessionId,
            userName);


        this.clientSession.setServerSessionId(sessionId);

        if (this.tryToSetState(ClientState.AUTHORIZING, ClientState.ONLINE)) {
            this.protocolHandler.fireAuthorized();
        }

    }

    void connect() {

        this.tryToSetState(ClientState.DISCONNECTED, ClientState.CONNECTING, Boolean.TRUE);

    }

    public void disconnect() {

        this.disconnect(new ClientDisconnectReason(DisconnectReason.CLIENT_APP_REQUEST, "Client application request"));
    }

    //TODO Event Channel inactive
    void primaryChannelDisconnected() {

        this.disconnect(getDisconnectReason());
    }

    public void disconnect(final ClientDisconnectReason disconnectReason) {

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
                    if (!tryToSetState(state, ClientState.DISCONNECTING, disconnectReason, Boolean.TRUE)) {
                        break;
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
//TODO Event Child Channel disconnect
    void childChannelDisconnected() {


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
            && !this.tryToSetState(ClientState.CONNECTING, ClientState.PROTOCOL_VERSION_NEGOTIATION_SUCCESSFUL)
            && !this.tryToSetState(ClientState.SSL_HANDSHAKE_SUCCESSFUL,
                                   ClientState.PROTOCOL_VERSION_NEGOTIATION_SUCCESSFUL)) {
            this.tryToSetState(ClientState.SSL_HANDSHAKE_WAITING, ClientState.PROTOCOL_VERSION_NEGOTIATION_SUCCESSFUL);
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

                final long timeToWait;
                switch (state) {
                    case CONNECTING:
                        this.cleanUp();
                        this.processConnecting();
                        continue;
                    case SSL_HANDSHAKE_WAITING:
                        if (isProcessSSLHandshakeTimeout()) {
                            continue;
                        }

                        isProcessConnecting = false;
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
                            // this.protocolHandler.handleAuthorization(this.authorizationProvider, this
                            // .primaryChannel);
                            this.protocolHandler.handleAuthorization(this.primaryChannel);
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


                        this.tryToSetState(ClientState.DISCONNECTING, ClientState.DISCONNECTED, Boolean.TRUE);
                        this.fireDisconnected(getDisconnectReason());
                        this.cleanUp();
                        continue;
                    case DISCONNECTED:
                        waitAndNotifyThreads(TIME_TO_WAIT_FOR_DISCONNECTING, ClientState.DISCONNECTED);
                        continue;
                    default:
                        throw new IllegalArgumentException("Unsupported state " + state);
                }
            } catch (final InterruptedException e) {

                LOGGER.error("Thread is interrupted: {}", Thread.currentThread().getName(), e);
                interrupted = Boolean.TRUE;
                try {
                    this.clientState.set(ClientState.DISCONNECTED);
                    this.cleanUp();


                } catch (final InterruptedException ex) {
                    LOGGER.error("Thread is interrupted: {}", Thread.currentThread().getName(), e);
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

        if (this.authorizationStartTime + this.clientSession.getAuthorizationTimeout() < System.currentTimeMillis()) {

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

        if (this.protocolVersionNegotiationStartTime + this.clientSession.getProtocolVersionNegotiationTimeout()
            < System.currentTimeMillis()) {

            LOGGER.info("[{}] Protocol version negotiation timeout", this.clientSession.getTransportName());


            this.tryToSetState(ClientState.PROTOCOL_VERSION_NEGOTIATION_WAITING,
                               ClientState.DISCONNECTING,
                               new ClientDisconnectReason(DisconnectReason.PROTOCOL_VERSION_NEGOTIATION_TIMEOUT,
                                                          "Protocol version " + "negotiation timeout"),
                               Boolean.TRUE);

            return true;
        }

        return false;
    }

    private boolean isProcessSSLHandshakeTimeout() {

        if (this.sslHandshakeStartTime == null) {
            return false;
        }
        if (this.sslHandshakeStartTime + this.clientSession.getSSLHandshakeTimeout() < System.currentTimeMillis()) {

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
    private void waitAndNotifyThreads(final long timeToWait, final ClientState state1) throws InterruptedException {

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
        if (this.isProcessConnecting) {
            return;
        }


this.isProcessConnecting = true;
        final Single<Channel> connectFuture = this.processConnectingAndGetFuture(this.address);
        Single<Channel> channelSingle = connectFuture.doOnSuccess(channel -> LOGGER.debug("[{}] primaryChannel = {}",
                                                                                          clientSession
                                                                                              .getTransportName(),
                                                                                          channel)).doOnError(cause -> {
            DisconnectReason reason = cause instanceof CertificateException
                                      ? DisconnectReason.CERTIFICATE_EXCEPTION
                                      : DisconnectReason.CONNECTION_PROBLEM;


            tryToSetState(ClientState.CONNECTING,
                          ClientState.DISCONNECTING,
                          new ClientDisconnectReason(reason,
                                                     String.format("%s exception %s",
                                                                   cause instanceof CertificateException
                                                                   ? "Certificate"
                                                                   : "Unexpected",
                                                                   cause.getMessage()),
                                                     cause),
                          Boolean.TRUE);
            isProcessConnecting = false;
        });

                     channelSingle.subscribe(channel -> {


        });
        Channel channel = channelSingle.blockingGet();
        primaryChannel = channel;
        channel.attr(CHANNEL_ATTACHMENT_ATTRIBUTE_KEY).set(primarySessionChannelAttachment);
        final Attribute<ChannelAttachment> attribute = channel.attr(CHANNEL_ATTACHMENT_ATTRIBUTE_KEY);
        final ChannelAttachment attachment = attribute.get();

        processConnectOverSSLIfNecessary();
    }

    private void processConnectingPreConditions() {

        resetDisconnectReason();
        // this.authorizationProvider = this.clientSession.getAuthorizationProvider();

        //this.clientSession.getAuthorizationProvider().setListener(this);
        // this.authorizationProvider.setListener(this);
        this.primarySessionChannelAttachment = new ChannelAttachment(Boolean.TRUE);
        this.childSessionChannelAttachment = new ChannelAttachment(Boolean.FALSE);
    }

    private void handleUnexpectedConnectingError() {

        LOGGER.debug("[{}] Connect call failed", this.clientSession.getTransportName());

        this.tryToSetState(ClientState.CONNECTING,
                           ClientState.DISCONNECTING,
                           new ClientDisconnectReason(DisconnectReason.CONNECTION_PROBLEM, "Connect call failed"),
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
                                                                    cause instanceof CertificateException
                                                                    ? "Certificate"
                                                                    : "Unexpected",
                                                                    cause.getMessage()),
                                                      cause),
                           Boolean.TRUE);
    }

    private void processConnectOverSSLIfNecessary() {

        if (this.clientSession.isUseSSL()) {
            if (this.tryToSetState(ClientState.CONNECTING, ClientState.SSL_HANDSHAKE_WAITING)) {
                this.sslHandshakeStartTime = System.currentTimeMillis();

            }
        } else if (this.tryToSetState(ClientState.CONNECTING, ClientState.PROTOCOL_VERSION_NEGOTIATION_WAITING)) {
            this.protocolVersionNegotiationStartTime = System.currentTimeMillis();

        }

    }

  /*  private ChannelFuture processConnectingAndGetFuture() throws InterruptedException {

        LOGGER.debug("[{}] Connecting to [{}]", this.clientSession.getTransportName(), this.address);


        final ChannelFuture connectFuture = this.channelBootstrap.connect(this.address);
        connectFuture.await(this.clientSession.getConnectionTimeout());
        return connectFuture;
    }*/

    private Single<Channel> processConnectingAndGetFuture(final InetSocketAddress address) throws InterruptedException {


        return Single.create(new SingleOnSubscribe<Channel>() {
            @Override
            public void subscribe(final SingleEmitter<Channel> e) throws Exception {

                final ChannelFuture future = channelBootstrap.connect(address);
                future.addListener(NettyUtil.getDefaultChannelFutureListener(e, new Function<ChannelFuture, Channel>
                    () {
                    @Override
                    public Channel apply(final ChannelFuture channelFuture) throws Exception {

                        return channelFuture.channel();
                    }
                }));
            }
        })
                     .doOnSubscribe(disposable -> LOGGER.debug("[{}] Connecting to [{}]",
                                                               clientSession.getTransportName(),
                                                               address))
                     .doOnSuccess(aBoolean -> LOGGER.debug("[{}] Successfully connected to [{}]",
                                                           clientSession.getTransportName(),
                                                           address))
                     .timeout(this.clientSession.getConnectionTimeout(), TimeUnit.MILLISECONDS)
                     .doOnError(cause -> LOGGER.error("[{}] Connect failed because of {}: {}",
                                                      clientSession.getTransportName(),
                                                      cause.getClass().getSimpleName(),
                                                      cause.getMessage()));

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

        /*if (this.authorizationProvider != null) {
            this.authorizationProvider.cleanUp();
            this.authorizationProvider = null;
        }*/
        this.clientSession.getAuthorizationProvider().cleanUp();
        if (this.primaryChannel == null || !this.primaryChannel.isActive()) {
            LOGGER.warn("[{}] Primary session disconnected. Disconnecting transport client",
                        this.clientSession.getTransportName());

            setDisconnectReason(new ClientDisconnectReason(DisconnectReason.CONNECTION_PROBLEM,
                                                           "Primary session is not active"));

            this.tryToSetState(ClientState.ONLINE, ClientState.DISCONNECTING, Boolean.TRUE);
            return false;

        }

        this.processPrimarySocketAuthAcceptorMessage();
        //clientSession.getProtocolHandler().getHeartbeatProcessor().sendPing(Boolean.TRUE);
        clientSession.getProtocolHandler().getHeartbeatProcessor().startSendPingPrimary( this.clientSession.getPrimaryConnectionPingInterval());

        if (!this.clientSession.isUseFeederSocket() || this.childSocketAuthAcceptorMessage == null) {return true;}

        if (this.childChannel != null && this.childChannel.isActive()) {
            //clientSession.getProtocolHandler().getHeartbeatProcessor().sendPing(Boolean.FALSE);
            clientSession.getProtocolHandler().getHeartbeatProcessor().startSendPingChild(this.clientSession.getChildConnectionPingInterval());
            if (this.canResetChildChannelReconnectAttempts()) {

                this.childSessionChannelAttachment.setReconnectAttempt(0);
            }
        } else {

            if (this.isMaxChildChannelReconnectAttemptsReached()) {
                LOGGER.warn("[{}] Child session max connection attempts reached. Disconnecting clientSession",
                            this.clientSession.getTransportName());

                setDisconnectReason(new ClientDisconnectReason(DisconnectReason.CONNECTION_PROBLEM,
                                                               "Child session max connection attempts" + " reached"));
                this.tryToSetState(ClientState.ONLINE, ClientState.DISCONNECTING, Boolean.TRUE);

                return false;
            }


            if (this.childSessionChannelAttachment.getLastConnectAttemptTime() + this.clientSession.getReconnectDelay()
                < System.currentTimeMillis()) {

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


        this.childSessionChannelAttachment.setLastConnectAttemptTime(System.currentTimeMillis());
        this.childSessionChannelAttachment.resetTimes();
        this.childSessionChannelAttachment.incrementReconnectAttempt();

        LOGGER.debug("[{}] Trying to connect child session. Attempt {}",
                     this.clientSession.getTransportName(),
                     this.childSessionChannelAttachment.getReconnectAttempt() + 1);


        final Single<Channel> connectFuture = this.processConnectingAndGetFuture(this.address);


        Single<Channel> channelSingle = connectFuture.doOnSuccess(channel -> LOGGER.debug("[{}] childChannel = {}",
                                                                                          clientSession
                                                                                              .getTransportName(),
                                                                                          channel)).doOnError(cause -> {
            DisconnectReason reason = cause instanceof CertificateException
                                      ? DisconnectReason.CERTIFICATE_EXCEPTION
                                      : DisconnectReason.CONNECTION_PROBLEM;


            tryToSetState(ClientState.CONNECTING,
                          ClientState.DISCONNECTING,
                          new ClientDisconnectReason(reason,
                                                     String.format("%s Child connection exception %s",
                                                                   cause instanceof CertificateException
                                                                   ? "Certificate"
                                                                   : "Unexpected",
                                                                   cause.getMessage()),
                                                     cause),
                          Boolean.TRUE);
        });



                     channelSingle.subscribe(new io.reactivex.functions.Consumer<Channel>() {
            @Override
            public void accept(final Channel channel) throws Exception {



            }
        });
        Channel channel = channelSingle.blockingGet();
        childChannel = channel;
        channel.attr(CHANNEL_ATTACHMENT_ATTRIBUTE_KEY).set(childSessionChannelAttachment);
        final Single<Boolean> observable = protocolHandler.writeMessage(childChannel,
                                                                        childSocketAuthAcceptorMessage);
        Single<Boolean> booleanSingle = observable.doOnError(new io.reactivex.functions.Consumer<Throwable>() {
            @Override
            public void accept(final Throwable e) throws Exception {

                if (e.getCause() instanceof ClosedChannelException || !isOnline()) {return;}

                LOGGER.error("[{}] ", clientSession.getTransportName(), e);
                disconnect(new ClientDisconnectReason(DisconnectReason.CONNECTION_PROBLEM,
                                                      String.format("Child session error while writing: %s",
                                                                    childSocketAuthAcceptorMessage),
                                                      e));
            }
        });
        booleanSingle.subscribe();
        final Attribute<ChannelAttachment> attribute =channel.attr(CHANNEL_ATTACHMENT_ATTRIBUTE_KEY);
        final ChannelAttachment attachment = attribute.get();
       // booleanSingle.blockingGet();

        //TODO
        return true;

    }

    private boolean isMaxChildChannelReconnectAttemptsReached() {

        return this.childSessionChannelAttachment.getReconnectAttempt()
               >= this.clientSession.getChildConnectionReconnectAttempts();
    }

    private boolean canResetChildChannelReconnectAttempts() {

        return this.childSessionChannelAttachment.getReconnectAttempt() != 0
               && (this.childSessionChannelAttachment.getLastIoTime()
                   > this.childSessionChannelAttachment.getLastConnectAttemptTime()
                   || this.childSessionChannelAttachment.getLastConnectAttemptTime()
                      + this.clientSession.getChildConnectionReconnectsResetDelay() < System.currentTimeMillis());
    }

    private void processPrimarySocketAuthAcceptorMessage() {

        if (this.primarySocketAuthAcceptorMessageSent || this.primarySocketAuthAcceptorMessage == null) {return;}


        final Single<Boolean> channelFuture = this.protocolHandler.writeMessage(this.primaryChannel,
                                                                                this.primarySocketAuthAcceptorMessage);
        channelFuture.subscribe(new io.reactivex.functions.Consumer<Boolean>() {
            @Override
            public void accept(final Boolean cf) throws Exception {


            }
        }, new io.reactivex.functions.Consumer<Throwable>() {
            @Override
            public void accept(final Throwable t) throws Exception {

                if (t.getCause() instanceof ClosedChannelException || !ClientConnectorOld.this.isOnline()) {return;}

                LOGGER.error("[{}] ", clientSession.getTransportName(), t);
                ClientConnectorOld.this.disconnect(new ClientDisconnectReason(DisconnectReason.CONNECTION_PROBLEM,
                                                                              String.format("Primary session error "
                                                                                         + "while writhing message: %s",
                                                                                         primarySocketAuthAcceptorMessage),
                                                                              t));
            }
        });


        this.primarySocketAuthAcceptorMessageSent = true;

    }


    private void cleanUp() throws InterruptedException {


        LOGGER.debug("[{}] Closing all sockets and cleaning references", this.clientSession.getTransportName());


        this.closeAllSessions();
/*
        if (this.authorizationProvider != null) {
            this.authorizationProvider.cleanUp();
            this.authorizationProvider = null;
        }*/
        this.clientSession.getAuthorizationProvider().cleanUp();

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

    private boolean tryToSetState(final ClientState expected, final ClientState newState) {

        return this.tryToSetState(expected, newState, null);

    }

    private boolean tryToSetState(final ClientState expected,
                                  final ClientState newState,
                                  final boolean throwExecption) {

        return this.tryToSetState(expected, newState, null, throwExecption);

    }

    private boolean tryToSetState(final ClientState expected,
                                  final ClientState newState,
                                  final ClientDisconnectReason reason) {

        return this.tryToSetState(expected, newState, reason, Boolean.FALSE);

    }

    private boolean tryToSetState(final ClientState expected,
                                  final ClientState newState,
                                  final ClientDisconnectReason reason,
                                  final boolean throwExecption) {

        if (!this.clientState.compareAndSet(expected, newState)) {
            if (newState == ClientState.DISCONNECTING) {


                if (this.isStateChanged()) {
                    setDisconnectReason(disconnectReason);
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
            setDisconnectReason(reason);
            LOGGER.debug(STATE_CHANGED_TO, this.clientSession.getTransportName(), newState);

        }


        synchronized (this.stateWaitLock) {
            this.stateWaitLock.notifyAll();
            return true;
        }
    }

    public boolean isOnline() {

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

    public synchronized void setDisconnectReason(final ClientDisconnectReason disconnectReason) {

        if (disconnectReason != null) {
            this.disconnectReason = disconnectReason;
        }
    }

    public synchronized void resetDisconnectReason() {

        this.disconnectReason = null;
    }

    public void setChildSocketAuthAcceptorMessage(final ChildSocketAuthAcceptorMessage acceptorMessage) {

        this.childSocketAuthAcceptorMessage = acceptorMessage;

        synchronized (this.stateWaitLock) {
            this.stateWaitLock.notifyAll();
        }
    }

    public void setPrimarySocketAuthAcceptorMessage(final PrimarySocketAuthAcceptorMessage acceptorMessage) {

        this.primarySocketAuthAcceptorMessage = acceptorMessage;

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

        final DisconnectedEvent disconnectedEvent = new DisconnectedEvent(this.clientSession.getTransportClient(),
                                                                          disconnectReason.getDisconnectReason(),
                                                                          disconnectReason.getDisconnectHint(),
                                                                          disconnectReason.getError(),
                                                                          disconnectReason.getDisconnectComments());
        listeners.forEach(new Consumer<ClientListener>() {
            @Override
            public void accept(final ClientListener clientListener) {

                ClientConnectorOld.this.protocolHandler.fireDisconnectedEvent(clientListener,
                                                                              ClientConnectorOld.this.clientSession
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

    void securityException(final X509Certificate[] chain, final String authType, final CertificateException e) {

        LOGGER.error("[{}] CERTIFICATE_EXCEPTION: ", this.clientSession.getTransportName(), e);

        if (this.clientSession.getSecurityExceptionHandler() == null
            || !this.clientSession.getSecurityExceptionHandler().isIgnoreSecurityException(chain, authType, e)) {

            final ClientDisconnectReason reason = new ClientDisconnectReason(DisconnectReason.CERTIFICATE_EXCEPTION,
                                                                             String.format("Certificate exception %s",
                                                                                           e.getMessage()),
                                                                             e);

            this.tryToSetState(ClientState.SSL_HANDSHAKE_WAITING, ClientState.DISCONNECTING, reason, Boolean.TRUE);

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
                    } catch (final InterruptedException e) {
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
