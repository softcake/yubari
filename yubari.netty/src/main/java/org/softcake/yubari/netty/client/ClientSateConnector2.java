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

import org.softcake.cherry.core.base.PreCheck;
import org.softcake.yubari.netty.channel.ChannelAttachment;
import org.softcake.yubari.netty.client.ClientConnectorStateMachine.Event;
import org.softcake.yubari.netty.mina.ClientDisconnectReason;
import org.softcake.yubari.netty.mina.TransportHelper;
import org.softcake.yubari.netty.ssl.SecurityExceptionEvent;

import com.dukascopy.dds4.transport.common.mina.DisconnectReason;
import com.dukascopy.dds4.transport.msg.system.ChildSocketAuthAcceptorMessage;
import com.dukascopy.dds4.transport.msg.system.PrimarySocketAuthAcceptorMessage;
import com.dukascopy.dds4.transport.msg.system.ProtocolMessage;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ConnectTimeoutException;
import io.reactivex.Observable;
import io.reactivex.Observer;
import io.reactivex.Single;
import io.reactivex.SingleEmitter;
import io.reactivex.SingleOnSubscribe;
import io.reactivex.functions.Consumer;
import io.reactivex.schedulers.Schedulers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.nio.channels.ClosedChannelException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * @author René Neubert
 */
public class ClientSateConnector2 implements SateMachineClient {
    private static final int DEFAULT_EVENT_POOL_SIZE = 2;
    private static final long DEFAULT_EVENT_POOL_AUTO_CLEANUP_INTERVAL = 0L;
    private static final int DEFAULT_CRITICAL_EVENT_QUEUE_SIZE = 50;
    private static final Logger LOGGER = LoggerFactory.getLogger(ClientSateConnector2.class);
    private static final List<Thread> eventExecutorThreadsForLogging = Collections.synchronizedList(new ArrayList<>());
    private static final long SESSION_CLOSE_WAIT_TIME = 5L;

    private final ListeningExecutorService executor = TransportHelper.createExecutor(DEFAULT_EVENT_POOL_SIZE,
                                                                                     DEFAULT_EVENT_POOL_AUTO_CLEANUP_INTERVAL,
                                                                                     DEFAULT_CRITICAL_EVENT_QUEUE_SIZE,
                                                                                     "TransportClientSateExecutorThread",
                                                                                     eventExecutorThreadsForLogging,
                                                                                     "DDS2 Standalone Transport Client",
                                                                                     true);
    private final ExecutorService scheduledExecutorService;
    private final InetSocketAddress address;
    private final Bootstrap channelBootstrap;
    private final TransportClientSession clientSession;
    private final ClientProtocolHandler protocolHandler;
    private ClientConnectorStateMachine sm;
    private boolean primarySocketAuthAcceptorMessageSent;
    private volatile Channel primaryChannel;
    private volatile Channel childChannel;
    private ChannelAttachment primaryChannelAttachment;
    private ChannelAttachment childChannelAttachment;
    private ChildSocketAuthAcceptorMessage childSocketAuthAcceptorMessage;
    private PrimarySocketAuthAcceptorMessage primarySocketAuthAcceptorMessage;
    private boolean childSocketAuthAcceptorMessageSent;
    private ClientDisconnectReason disconnectReason;

    ClientSateConnector2(final InetSocketAddress address,
                         final Bootstrap channelBootstrap,
                         final TransportClientSession clientSession) {

        final ThreadFactory threadFactory = new ThreadFactoryBuilder().setDaemon(true).setNameFormat(String.format(
            "[%s] TransportClientEventExecutorThread",
            "DDS2 Standalone Transport Client")).build();

        this.scheduledExecutorService = Executors.newSingleThreadExecutor(threadFactory);
        this.address = address;
        this.channelBootstrap = channelBootstrap;
        this.clientSession = clientSession;
        this.protocolHandler = clientSession.getProtocolHandler();


        sm = new ClientConnectorStateMachine(this, executor, clientSession);
        sm.connect();
    }



    private static boolean isChannelInActive(final Channel channel) {

        return channel == null || !channel.isActive();
    }

    public ClientDisconnectReason getDisconnectReason() {

        return disconnectReason;
    }

    @Override
    public void onIdleEnter(final ClientState state) {

    }

    @Override
    public void onIdleExit(final ClientState state) {

    }

    @Override
    public void onConnectingEnter(final ClientState state) {

        if (address == null) {
            LOGGER.error("[{}] Address not set in transport and "
                         + "AbstractClientAuthorizationProvider.getAddress returned null",
                         this.clientSession.getTransportName());

            disconnect(new ClientDisconnectReason(DisconnectReason.CONNECTION_PROBLEM,
                                                  "Address is not set in transport client"));

        } else {

            this.createChannelAttachments();
            this.connectPrimarySession().subscribe();
        }
    }

    private void createChannelAttachments() {

        primaryChannelAttachment = new ChannelAttachment(Boolean.TRUE);
        childChannelAttachment = new ChannelAttachment(Boolean.FALSE);
    }

    @Override
    public void onConnectingExit(final ClientState state) {

    }

    @Override
    public void onSslHandshakeEnter(final ClientState state) {

        processProtocolVersionNegotiationWaiting();
    }

    @Override
    public void onSslHandshakeExit(final ClientState state) {

    }

    @Override
    public void onProtocolVersionNegotiationEnter(final ClientState state) {

        processAuthorizingWaiting();
    }

    @Override
    public void onProtocolVersionNegotiationExit(final ClientState state) {

    }

    @Override
    public void onAuthorizingEnter(final ClientState state) {

        sm.accept(Event.ONLINE);
    }

    @Override
    public void onAuthorizingExit(final ClientState state) {

    }

    @Override
    public void onOnlineEnter(final ClientState state) {

        processOnline().subscribe();
    }

    @Override
    public void onOnlineExit(final ClientState state) {

    }

    @Override
    public void onDisconnectingEnter(final ClientState state) {

        sm.accept(Event.DISCONNECTED);
    }

    @Override
    public void onDisconnectingExit(final ClientState state) {

    }

    @Override
    public void onDisconnectedEnter(final ClientState state) {

        this.logDisconnectReason(disconnectReason);
        shutdown(executor, 5000L, TimeUnit.MILLISECONDS);
    }

    @Override
    public void onDisconnectedExit(final ClientState state) {

    }

    private Observable<ClientState> processOnline() {

        return Observable.interval(100, 500, TimeUnit.MILLISECONDS, Schedulers.from(executor))
                         .map(aLong -> getClientState())
                         .takeWhile(state -> state == ClientState.ONLINE)
                         .doOnNext(state -> processPrimarySessionOnline())
                         .filter(state -> clientSession.isUseFeederSocket())
                         .doOnNext(state -> processChildSessionOnline());


    }

    private void processChildSessionOnline() {

        if (isChannelInActive(this.childChannel)) {

            if (childChannelAttachment.isMaxReconnectAttemptsReached(this.clientSession
                                                                         .getChildConnectionReconnectAttempts())) {
                LOGGER.warn("[{}] Child session max connection attempts reached. Disconnecting clientSession",
                            this.clientSession.getTransportName());
                disconnect(new ClientDisconnectReason(DisconnectReason.CONNECTION_PROBLEM,
                                                      "Child session max connection attempts" + " reached"));

            }

            if (this.childChannelAttachment.getLastConnectAttemptTime() + this.clientSession.getReconnectDelay()
                < System.currentTimeMillis()) {
                this.connectChildSession().subscribe();

            }
        } else {
            processSocketAuthAcceptorMessage(this.childSocketAuthAcceptorMessage).subscribe();
            clientSession.getProtocolHandler().getHeartbeatProcessor().startSendPingChild();
            this.childChannelAttachment.resetReconnectAttemptsIfValid(this.clientSession
                                                                          .getChildConnectionReconnectsResetDelay(),
                                                                      System.currentTimeMillis());
        }
    }

    private void processPrimarySessionOnline() {

        if (isChannelInActive(this.primaryChannel)) {
            LOGGER.warn("[{}] Primary session disconnected. Disconnecting transport client",
                        this.clientSession.getTransportName());


            disconnect(new ClientDisconnectReason(DisconnectReason.CONNECTION_PROBLEM,
                                                  "Primary session is not active"));


        } else {
            processSocketAuthAcceptorMessage(this.primarySocketAuthAcceptorMessage).subscribe();
            protocolHandler.getHeartbeatProcessor().startSendPingPrimary();
        }
    }

    private void closeAndCleanChildSession() {

        if (this.childChannel != null) {
            this.childChannel.disconnect();
            this.childChannel.close(); //.await(SESSION_CLOSE_WAIT_TIME, TimeUnit.SECONDS);
            this.childChannel = null;
        }

        this.childChannelAttachment.setLastConnectAttemptTime(System.currentTimeMillis());
        this.childChannelAttachment.resetTimes();
        this.childChannelAttachment.incrementReconnectAttempt();
        this.childSocketAuthAcceptorMessageSent = false;
    }

    private void closeAndCleanPrimarySession() {

        if (this.primaryChannel != null) {
            this.primaryChannel.disconnect();
            this.primaryChannel.close();//.await(SESSION_CLOSE_WAIT_TIME, TimeUnit.SECONDS);
            this.primaryChannel = null;
        }

        this.primarySocketAuthAcceptorMessageSent = false;
        this.primarySocketAuthAcceptorMessage = null;
    }

    private Single<Channel> connectPrimarySession() {

        closeAndCleanPrimarySession();
        LOGGER.debug("[{}] Trying to connect primary session. Attempt {}",
                     this.clientSession.getTransportName(),
                     this.primaryChannelAttachment.getReconnectAttempt() + 1);


        return processConnecting(primaryChannelAttachment).doOnSuccess(channel -> waitForSslAndNegotitation());
    }

    private Single<Channel> connectChildSession() {

        closeAndCleanChildSession();
        LOGGER.debug("[{}] Trying to connect child session. Attempt {}",
                     this.clientSession.getTransportName(),
                     this.childChannelAttachment.getReconnectAttempt() + 1);


        return processConnecting(childChannelAttachment);
    }

    void setChildSocketAuthAcceptorMessage(final ChildSocketAuthAcceptorMessage acceptorMessage) {

        this.childSocketAuthAcceptorMessage = PreCheck.notNull(acceptorMessage, "ChildSocketAuthAcceptorMessage");
    }

    private Single<Boolean> processSocketAuthAcceptorMessage(final ProtocolMessage msg) {

        if (msg instanceof PrimarySocketAuthAcceptorMessage) {

            return this.primarySocketAuthAcceptorMessageSent || this.primarySocketAuthAcceptorMessage == null
                   ? Single.just(false)
                   : writeSocketAuthAcceptorMessage(msg, Boolean.TRUE);

        } else if (msg instanceof ChildSocketAuthAcceptorMessage) {
            return this.childSocketAuthAcceptorMessageSent || this.childSocketAuthAcceptorMessage == null ? Single.just(
                false) : writeSocketAuthAcceptorMessage(msg, Boolean.FALSE);

        } else {
            throw new IllegalArgumentException(
                "message must be instance of PrimarySocketAuthAcceptorMessage or ChildSocketAuthAcceptorMessage");
        }

    }

    private Single<Boolean> writeSocketAuthAcceptorMessage(final ProtocolMessage msg, final boolean isPrimary) {

        final Channel channel = isPrimary ? this.primaryChannel : this.childChannel;

        return this.protocolHandler.writeMessage(channel, msg).doOnError(t -> {
            if (t.getCause() instanceof ClosedChannelException || !isOnline()) {return;}

            LOGGER.error("[{}] ", clientSession.getTransportName(), t);
            disconnect(new ClientDisconnectReason(DisconnectReason.CONNECTION_PROBLEM,
                                                  String.format("%s session error while writhing message: %s",
                                                                isPrimary ? "Primary" : "Child",
                                                                msg),
                                                  t));
        }).doOnSuccess(aBoolean -> {

            if (isPrimary) {
                primarySocketAuthAcceptorMessageSent = true;
            } else {
                childSocketAuthAcceptorMessageSent = true;
            }


        });
    }

    void setPrimarySocketAuthAcceptorMessage(final PrimarySocketAuthAcceptorMessage acceptorMessage) {

        this.primarySocketAuthAcceptorMessage = PreCheck.notNull(acceptorMessage, "PrimarySocketAuthAcceptorMessage");
    }

    private Single<Channel> processConnecting(final ChannelAttachment attachment) {

        final boolean isPrimary = attachment.isPrimaryConnection();

        return processConnectingAndGetFuture(address).doOnSuccess(channel -> LOGGER.debug("[{}] {} channel = {}",
                                                                                          isPrimary
                                                                                          ? "primary"
                                                                                          : "child",
                                                                                          clientSession
                                                                                              .getTransportName(),
                                                                                          channel)).doOnError(cause -> {
            DisconnectReason reason = cause instanceof CertificateException
                                      ? DisconnectReason.CERTIFICATE_EXCEPTION
                                      : DisconnectReason.CONNECTION_PROBLEM;

            disconnect(new ClientDisconnectReason(reason,
                                                  String.format("%s exception %s",
                                                                cause instanceof CertificateException
                                                                ? "Certificate"
                                                                : "Unexpected",
                                                                cause.getMessage()),
                                                  cause));


        }).subscribeOn(Schedulers.from(executor)).doOnSuccess(channel -> {

            if (isPrimary) {
                primaryChannel = channel;
            } else {
                childChannel = channel;
            }
        }).doOnSuccess(ch -> ch.attr(CHANNEL_ATTACHMENT_ATTRIBUTE_KEY).set(attachment));


    }

    private void waitForSslAndNegotitation() {

        if (ClientSateConnector2.this.clientSession.isUseSSL()) {
            processSslHandShakeWaiting();
        } else {
            processProtocolVersionNegotiationWaiting();
        }
    }

    private Single<Channel> processConnectingAndGetFuture(final InetSocketAddress address) {

        return Single.create((SingleOnSubscribe<Channel>) e -> {
            channelBootstrap.connect(address).addListener(getDefaultChannelFutureListener(e));
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


    private ChannelFutureListener getDefaultChannelFutureListener(final SingleEmitter<Channel> e) {

        return cf -> {

            if (cf.isSuccess() && cf.isDone()) {
                // Completed successfully
                e.onSuccess(cf.channel());
            } else if (cf.isCancelled() && cf.isDone()) {
                // Completed by cancellation
                e.onError(new CancellationException("cancelled before completed"));
            } else if (cf.isDone() && cf.cause() != null) {
                // Completed with failure
                e.onError(cf.cause());
            } else if (!cf.isDone() && !cf.isSuccess() && !cf.isCancelled() && cf.cause() == null) {
                // Uncompleted
                e.onError(new ConnectTimeoutException());
            } else {
                e.onError(new Exception("Unexpected ChannelFuture state"));
            }
        };
    }

    private void processSslHandShakeWaiting() {

        sm.observe()
          .filter(c -> ClientState.SSL_HANDSHAKE == c)
          .timeout(this.clientSession.getSSLHandshakeTimeout(),
                   TimeUnit.MILLISECONDS,
                   Schedulers.from(executor))
          .takeUntil(c -> ClientState.SSL_HANDSHAKE
                          == c)
          .doOnError(e -> ClientSateConnector2.this.disconnect(new ClientDisconnectReason(DisconnectReason
                                                                                              .SSL_HANDSHAKE_TIMEOUT,
                                                                                          "SSL handshake timeout",
                                                                                          e)))
          .subscribe();
    }

    void sslHandshakeSuccess() {

        if (ClientState.ONLINE != getClientState()) {
            sm.accept(Event.SSL_HANDSHAKE_SUCCESSFUL);
        }
    }

    void protocolVersionNegotiationSuccess() {

        if (this.getClientState() != ClientState.ONLINE) {
            sm.accept(Event.PROTOCOL_VERSION_NEGOTIATION_SUCCESSFUL);
        }
    }


    private void processProtocolVersionNegotiationWaiting() {

        sm.observe()
          .filter(c -> ClientState.PROTOCOL_VERSION_NEGOTIATION == c)
          .timeout(this.clientSession.getProtocolVersionNegotiationTimeout(),
                   TimeUnit.MILLISECONDS,
                   Schedulers.from(executor))
          .takeUntil(c -> ClientState.PROTOCOL_VERSION_NEGOTIATION == c)
          .doOnError(e -> disconnect(new ClientDisconnectReason(DisconnectReason.PROTOCOL_VERSION_NEGOTIATION_TIMEOUT,
                                                                "Protocol version negotiation timeout",
                                                                e)))
          .subscribe();

    }

    void authorizationError(final String errorReason) {

        LOGGER.error("[{}] Received AUTHORIZATION_ERROR notification from the authorization provider, reason: [{}]",
                     this.clientSession.getTransportName(),
                     errorReason);
        Single.just(errorReason)
              .observeOn(Schedulers.from(executor))
              .subscribe(errorReason1 -> disconnect(new ClientDisconnectReason(DisconnectReason.AUTHORIZATION_FAILED,
                                                                               String.format("Authorization failed, "
                                                                                             + "reason [%s]",
                                                                                             errorReason1))));


    }

    void authorizingSuccess(final String sessionId, final String userName) {

        LOGGER.debug(
            "[{}] Received AUTHORIZED notification from the authorization provider. SessionId [{}], userName [{}]",
            this.clientSession.getTransportName(),
            sessionId,
            userName);
        clientSession.setServerSessionId(sessionId);
        sm.accept(Event.AUTHORIZING);
    }

    public Channel getPrimaryChannel() {

        return this.primaryChannel;
    }

    public boolean isOnline() {

        return this.getClientState() == ClientState.ONLINE;
    }

    public Channel getChildChannel() {

        return this.childChannel;
    }

    private void processAuthorizingWaiting() {

        sm.observe()
          .filter(c -> ClientState.AUTHORIZING == c)
          .timeout(this.clientSession.getAuthorizationTimeout(),
                   TimeUnit.MILLISECONDS,
                   Schedulers.from(executor))
          .takeUntil(c -> ClientState.AUTHORIZING
                          == c)
          .doOnError(e -> disconnect(new ClientDisconnectReason(DisconnectReason.AUTHORIZATION_TIMEOUT,
                                                                "Authorization timeout",
                                                                e)))
          .subscribe();

    }

    private ClientState getClientState() {

        return sm.getState();
    }

    public void connect() {

        sm.accept(Event.CONNECTING);
    }

    public void disconnect() {

        this.disconnect(new ClientDisconnectReason(DisconnectReason.CLIENT_APP_REQUEST, "Client application request"));
    }

    public void disconnect(final ClientDisconnectReason reason) {

        this.disconnectReason = reason;
    }

    private void logDisconnectReason(final ClientDisconnectReason disconnectReason) {

        Single.just(disconnectReason).observeOn(Schedulers.from(executor)).subscribe(this::logDisconnectInQueue);
    }

    private void logDisconnectInQueue(final ClientDisconnectReason disconnectReason) {

        if (disconnectReason == null) {
            return;
        }

        final StringBuilder builder = new StringBuilder();
        builder.append("Disconnect now, reason [")
               .append(disconnectReason.getDisconnectReason())
               .append("], comments [")
               .append(disconnectReason.getDisconnectComments())
               .append("]");
        if (this.clientSession != null) {
            builder.append(", server address [")
                   .append(this.clientSession.getAddress())
                   .append("], transport name [")
                   .append(this.clientSession.getTransportName())
                   .append("]");
        }
        final String logMsg = builder.toString();
        if (disconnectReason.getError() != null) {
            LOGGER.info(logMsg, disconnectReason.getError());
        } else {
            LOGGER.info(logMsg);
        }

    }

    void observe(final Observer<ClientState> observer) {

        sm.observe().subscribe(observer);
    }

    void primaryChannelDisconnected() {

        sm.accept(Event.DISCONNECTING);
    }

    void childChannelDisconnected() {

    }

    boolean isConnecting() {

        final ClientState state = this.getClientState();
        return state == ClientState.CONNECTING
               || state == ClientState.SSL_HANDSHAKE
               || state == ClientState.PROTOCOL_VERSION_NEGOTIATION
               || state == ClientState.AUTHORIZING;
    }


    public Consumer<SecurityExceptionEvent> observeSslSecurity() {

        return e -> {

            final X509Certificate[] chain = e.getCertificateChain();
            final String authType = e.getAuthenticationType();
            final CertificateException ex = e.getException();

            LOGGER.error("[{}] CERTIFICATE_EXCEPTION: ", clientSession.getTransportName(), ex);

            if (clientSession.getSecurityExceptionHandler() == null || !clientSession.getSecurityExceptionHandler()
                                                                                     .isIgnoreSecurityException(chain,
                                                                                                                authType,
                                                                                                                ex)) {
                disconnect(new ClientDisconnectReason(DisconnectReason.CERTIFICATE_EXCEPTION,
                                                      String.format("Certificate exception %s", ex.getMessage()),
                                                      ex));

            }

        };
    }

    public void terminate() {

        if (this.getClientState() == ClientState.ONLINE) {
            sm.accept(Event.DISCONNECTING);
        }
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
            Thread.currentThread().interrupt();
        }

    }

    public enum ClientState {
        IDLE,
        CONNECTING,
        SSL_HANDSHAKE,
        PROTOCOL_VERSION_NEGOTIATION,
        AUTHORIZING,
        ONLINE,
        DISCONNECTING,
        DISCONNECTED;

        ClientState() {

        }

    }

}
