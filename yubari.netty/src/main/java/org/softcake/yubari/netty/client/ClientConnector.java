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
import org.softcake.yubari.netty.AbstractConnectingCompletionEvent;
import org.softcake.yubari.netty.AuthorizationCompletionEvent;
import org.softcake.yubari.netty.DefaultChannelWriter;
import org.softcake.yubari.netty.ProtocolVersionNegotiationCompletionEvent;
import org.softcake.yubari.netty.SslCompletionEvent;
import org.softcake.yubari.netty.channel.ChannelAttachment;
import org.softcake.yubari.netty.client.ClientConnectorStateMachine.Event;
import org.softcake.yubari.netty.mina.ClientDisconnectReason;
import org.softcake.yubari.netty.mina.TransportHelper;
import org.softcake.yubari.netty.ssl.SecurityExceptionEvent;

import com.dukascopy.dds4.transport.common.mina.DisconnectReason;
import com.dukascopy.dds4.transport.common.protocol.binary.BinaryProtocolMessage;
import com.dukascopy.dds4.transport.msg.system.ChildSocketAuthAcceptorMessage;
import com.dukascopy.dds4.transport.msg.system.ErrorResponseMessage;
import com.dukascopy.dds4.transport.msg.system.HaloResponseMessage;
import com.dukascopy.dds4.transport.msg.system.LoginRequestMessage;
import com.dukascopy.dds4.transport.msg.system.OkResponseMessage;
import com.dukascopy.dds4.transport.msg.system.PrimarySocketAuthAcceptorMessage;
import com.dukascopy.dds4.transport.msg.system.ProtocolMessage;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.ssl.SslHandshakeCompletionEvent;
import io.reactivex.Completable;
import io.reactivex.Observable;
import io.reactivex.Single;
import io.reactivex.SingleEmitter;
import io.reactivex.SingleOnSubscribe;
import io.reactivex.functions.Consumer;
import io.reactivex.functions.Function;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * @author René Neubert
 */
@ChannelHandler.Sharable
public class ClientConnector extends SimpleChannelInboundHandler<ProtocolMessage>
implements SateMachineClient, IClientConnector {
    private static final int DEFAULT_EVENT_POOL_SIZE = 2;
    private static final long DEFAULT_EVENT_POOL_AUTO_CLEANUP_INTERVAL = 0L;
    private static final int DEFAULT_CRITICAL_EVENT_QUEUE_SIZE = 50;
    private static final Logger LOGGER = LoggerFactory.getLogger(ClientConnector.class);
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
    private final ClientConnectorStateMachine sm;
    private boolean primarySocketAuthAcceptorMessageSent;
    private boolean childSocketAuthAcceptorMessageSent;
    private volatile Channel primaryChannel;
    private volatile Channel childChannel;
    private ChannelAttachment primaryChannelAttachment;
    private ChannelAttachment childChannelAttachment;
    private PrimarySocketAuthAcceptorMessage primarySocketAuthAcceptorMessage;
    private ChildSocketAuthAcceptorMessage childSocketAuthAcceptorMessage;
    private ClientDisconnectReason disconnectReason;

    ClientConnector(final InetSocketAddress address,
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
        this.sm = new ClientConnectorStateMachine(this, this.executor, clientSession);
        this.sm.connect();
    }

    private static boolean isChannelInActive(final Channel channel) {

        return channel == null || !channel.isActive();
    }

    private static void shutdown(final ListeningExecutorService executor,
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

    private void processCompletionEvent(final AbstractConnectingCompletionEvent authorizationEvent) {

        if (authorizationEvent instanceof SslCompletionEvent) {
            this.sslHandshake(authorizationEvent);


        } else if (authorizationEvent instanceof ProtocolVersionNegotiationCompletionEvent) {
            this.protocolVersionNegotiation(authorizationEvent);


        } else if (authorizationEvent instanceof AuthorizationCompletionEvent) {
            this.authorizing(authorizationEvent);
        }
    }

    private void authorizing(final AbstractConnectingCompletionEvent authorizationEvent) {

        if (authorizationEvent.isSuccess()) {
            this.sm.accept(Event.AUTHORIZING);

            return;
        }
        this.disconnect(new ClientDisconnectReason(DisconnectReason.AUTHORIZATION_FAILED,
                                                   String.format("Authorization failed, reason [%s]",
                                                                 authorizationEvent.cause().getMessage())));

    }

    private void protocolVersionNegotiation(final AbstractConnectingCompletionEvent authorizationEvent) {

        if (authorizationEvent.isSuccess()) {
            if (this.getClientState() != ClientState.ONLINE) {
                this.sm.accept(Event.PROTOCOL_VERSION_NEGOTIATION_SUCCESSFUL);
            }
            return;
        }
        this.disconnect(new ClientDisconnectReason(DisconnectReason.UNKNOWN,
                                                   String.format("Protocol version negotiation failed, reason [%s]",
                                                                 authorizationEvent.cause().getMessage())));

    }

    private void sslHandshake(final AbstractConnectingCompletionEvent authorizationEvent) {

        if (authorizationEvent.isSuccess()) {
            if (ClientState.ONLINE != this.getClientState()) {
                this.sm.accept(Event.SSL_HANDSHAKE_SUCCESSFUL);
            }
            return;
        }
        this.disconnect(new ClientDisconnectReason(DisconnectReason.UNKNOWN,
                                                   String.format("SSL handshake failed, reason [%s]",
                                                                 authorizationEvent.cause().getMessage())));

    }

    @Override
    public ClientDisconnectReason getDisconnectReason() {

        return this.disconnectReason;
    }

    @Override
    public void onClientStateEnter(final ClientState state) {

        switch (state) {
            case IDLE:
                break;
            case CONNECTING:
                if (this.address == null) {
                    LOGGER.error("[{}] Address not set in transport and "
                                 + "AbstractClientAuthorizationProvider.getAddress returned null",
                                 this.clientSession.getTransportName());

                    this.disconnect(new ClientDisconnectReason(DisconnectReason.CONNECTION_PROBLEM,
                                                               "Address is not set in transport client"));

                } else {

                    this.createChannelAttachments();
                    this.connectPrimarySession().subscribe();
                }
                break;
            case SSL_HANDSHAKE:
                this.processProtocolVersionNegotiationWaiting();
                break;
            case PROTOCOL_VERSION_NEGOTIATION:
                this.processAuthorizingWaiting();
                break;
            case AUTHORIZING:
                this.sm.accept(Event.ONLINE);
                break;
            case ONLINE:
                this.processOnline().subscribe();
                break;
            case DISCONNECTING:
                this.sm.accept(Event.DISCONNECTED);
                break;
            case DISCONNECTED:
                this.logDisconnectReason(this.disconnectReason);
                shutdown(this.executor, 5000L, TimeUnit.MILLISECONDS);
                break;
            default:
                throw new IllegalArgumentException("Unsupported state " + state);
        }

    }

    private void createChannelAttachments() {

        this.primaryChannelAttachment = new ChannelAttachment(Boolean.TRUE);
        this.childChannelAttachment = new ChannelAttachment(Boolean.FALSE);
    }

    private Observable<ClientState> processOnline() {

        return Observable.interval(100, 500, TimeUnit.MILLISECONDS, Schedulers.from(this.executor))
                         .map(aLong -> this.getClientState())
                         .takeWhile(state -> state == ClientState.ONLINE)
                         .doOnNext(state -> this.processPrimarySessionOnline())
                         .filter(state -> this.clientSession.isUseFeederSocket())
                         .doOnNext(state -> this.processChildSessionOnline());

    }

    private void processChildSessionOnline() {

        if (isChannelInActive(this.childChannel)) {

            if (this.childChannelAttachment.isMaxReconnectAttemptsReached(this.clientSession
                                                                              .getChildConnectionReconnectAttempts())) {
                LOGGER.warn("[{}] Child session max connection attempts reached. Disconnecting clientSession",
                            this.clientSession.getTransportName());
                this.disconnect(new ClientDisconnectReason(DisconnectReason.CONNECTION_PROBLEM,
                                                           "Child session max connection attempts reached"));

            }

            if (this.childChannelAttachment.getLastConnectAttemptTime() + this.clientSession.getReconnectDelay()
                < System.currentTimeMillis()) {
                this.connectChildSession().subscribe();

            }
        } else {
            this.processSocketAuthAcceptorMessage(this.childSocketAuthAcceptorMessage).subscribe();
            this.protocolHandler.getHeartbeatProcessor()
                                .startSendPingChild(this.clientSession.getChildConnectionPingInterval());
            this.childChannelAttachment.resetReconnectAttemptsIfValid(this.clientSession
                                                                          .getChildConnectionReconnectsResetDelay(),
                                                                      System.currentTimeMillis());
        }
    }

    private void processPrimarySessionOnline() {

        if (isChannelInActive(this.primaryChannel)) {
            LOGGER.warn("[{}] Primary session onDisconnected. Disconnecting transport client",
                        this.clientSession.getTransportName());

            this.disconnect(new ClientDisconnectReason(DisconnectReason.CONNECTION_PROBLEM,
                                                       "Primary session is not active"));

        } else {
            this.processSocketAuthAcceptorMessage(this.primarySocketAuthAcceptorMessage).subscribe();
            this.protocolHandler.getHeartbeatProcessor()
                                .startSendPingPrimary(this.clientSession.getPrimaryConnectionPingInterval());
        }
    }

    private void closeAndCleanChildSession() {

        if (this.childChannel != null) {
            this.childChannel.disconnect();
            this.childChannel.close();
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

        this.closeAndCleanPrimarySession();
        LOGGER.debug("[{}] Trying to connect primary session. Attempt {}",
                     this.clientSession.getTransportName(),
                     this.primaryChannelAttachment.getReconnectAttempt() + 1);

        return this.processConnecting(this.primaryChannelAttachment)
                   .doOnSuccess(channel -> this.waitForSslAndNegotiation());
    }

    private Single<Channel> connectChildSession() {

        this.closeAndCleanChildSession();
        LOGGER.debug("[{}] Trying to connect child session. Attempt {}",
                     this.clientSession.getTransportName(),
                     this.childChannelAttachment.getReconnectAttempt() + 1);


        return this.processConnecting(this.childChannelAttachment);
    }

    @Override
    public void setChildSocketAuthAcceptorMessage(final ChildSocketAuthAcceptorMessage acceptorMessage) {

        this.childSocketAuthAcceptorMessage = PreCheck.notNull(acceptorMessage, "ChildSocketAuthAcceptorMessage");
    }

    private Completable processSocketAuthAcceptorMessage(final ProtocolMessage msg) {

        if (msg instanceof PrimarySocketAuthAcceptorMessage) {

            return this.primarySocketAuthAcceptorMessageSent || this.primarySocketAuthAcceptorMessage == null
                   ? Completable.complete()
                   : this.writeSocketAuthAcceptorMessage(msg, Boolean.TRUE);

        } else if (msg instanceof ChildSocketAuthAcceptorMessage) {
            return this.childSocketAuthAcceptorMessageSent || this.childSocketAuthAcceptorMessage == null ? Completable.complete() : this.writeSocketAuthAcceptorMessage(msg, Boolean.FALSE);

        } else {
            throw new IllegalArgumentException(
                "message must be instance of PrimarySocketAuthAcceptorMessage or ChildSocketAuthAcceptorMessage");
        }

    }

    private Completable writeSocketAuthAcceptorMessage(final ProtocolMessage msg, final boolean isPrimary) {

        final Channel channel = isPrimary ? this.primaryChannel : this.childChannel;

        return DefaultChannelWriter.writeMessage(clientSession, channel, msg).doOnError(t -> {
            if (t.getCause() instanceof ClosedChannelException || !this.isOnline()) {return;}

            LOGGER.error("[{}] ", this.clientSession.getTransportName(), t);
            this.disconnect(new ClientDisconnectReason(DisconnectReason.CONNECTION_PROBLEM,
                                                       String.format("%s session error while writhing message: %s",
                                                                     isPrimary ? "Primary" : "Child",
                                                                     msg),
                                                       t));
        }).doOnComplete(() -> socketAuthAcceptorMessageSent(isPrimary)


        );
    }

    private void socketAuthAcceptorMessageSent(final boolean isPrimary) {

        if (isPrimary) {
            this.primarySocketAuthAcceptorMessageSent = true;
        } else {
            this.childSocketAuthAcceptorMessageSent = true;
        }
    }

    @Override
    public void setPrimarySocketAuthAcceptorMessage(final PrimarySocketAuthAcceptorMessage acceptorMessage) {

        this.primarySocketAuthAcceptorMessage = PreCheck.notNull(acceptorMessage, "PrimarySocketAuthAcceptorMessage");
    }

    private Single<Channel> processConnecting(final ChannelAttachment attachment) {

        final boolean isPrimary = attachment.isPrimaryConnection();

        return this.processConnectingAndGetFuture()
                   .doOnSuccess(channel -> LOGGER.debug("[{}] {} channel = {}",
                                                        isPrimary ? "primary" : "child",
                                                        this.clientSession.getTransportName(),
                                                        channel))
                   .doOnError(cause -> {
                       DisconnectReason reason = cause instanceof CertificateException
                                                 ? DisconnectReason.CERTIFICATE_EXCEPTION
                                                 : DisconnectReason.CONNECTION_PROBLEM;

                       this.disconnect(new ClientDisconnectReason(reason,
                                                                  String.format("%s exception %s",
                                                                                cause instanceof CertificateException
                                                                                ? "Certificate"
                                                                                : "Unexpected",
                                                                                cause.getMessage()),
                                                                  cause));


                   })
                   .subscribeOn(Schedulers.from(this.executor))
                   .doOnSuccess(channel -> this.setChannel(isPrimary, channel))
                   .doOnSuccess(new Consumer<Channel>() {
                       @Override
                       public void accept(final Channel ch) throws Exception {

                           ch.attr(CHANNEL_ATTACHMENT_ATTRIBUTE_KEY).set(attachment);
                       }
                   });


    }

    private void setChannel(final boolean isPrimary, final Channel channel) {

        if (isPrimary) {
            this.primaryChannel = channel;
        } else {
            this.childChannel = channel;
        }
    }

    private void waitForSslAndNegotiation() {

        if (ClientConnector.this.clientSession.isUseSSL()) {
            this.processSslHandShakeWaiting();
        } else {
            this.processProtocolVersionNegotiationWaiting();
        }
    }

    private Single<Channel> processConnectingAndGetFuture() {

        return Single.create(new SingleOnSubscribe<Channel>() {
            @Override
            public void subscribe(final SingleEmitter<Channel> e) throws Exception {

                ClientConnector.this.channelBootstrap.connect(ClientConnector.this.address)
                                                     .addListener(NettyUtil.getDefaultChannelFutureListener(

                                                         e, new Function<ChannelFuture, Channel>() {
                                                             @Override
                                                             public Channel apply(final ChannelFuture channelFuture)
                                                                 throws Exception {

                                                                 return channelFuture.channel();
                                                             }
                                                         }));
            }
        })
                     .doOnSubscribe(disposable -> LOGGER.debug("[{}] Connecting to [{}]",
                                                               this.clientSession.getTransportName(),
                                                               this.address))
                     .doOnSuccess(aBoolean -> LOGGER.debug("[{}] Successfully connected to [{}]",
                                                           this.clientSession.getTransportName(),
                                                           this.address))
                     .timeout(this.clientSession.getConnectionTimeout(), TimeUnit.MILLISECONDS)
                     .doOnError(cause -> LOGGER.error("[{}] Connect failed because of {}: {}",
                                                      this.clientSession.getTransportName(),
                                                      cause.getClass().getSimpleName(),
                                                      cause.getMessage()));

    }

    private void processSslHandShakeWaiting() {

        this.sm.observe()
               .filter(c -> ClientState.SSL_HANDSHAKE == c)
               .timeout(this.clientSession.getSSLHandshakeTimeout(),
                        TimeUnit.MILLISECONDS,
                        Schedulers.from(this.executor))
               .takeUntil(c -> ClientState.SSL_HANDSHAKE == c)
               .doOnError(e -> ClientConnector.this.disconnect(new ClientDisconnectReason(DisconnectReason
                                                                                              .SSL_HANDSHAKE_TIMEOUT,
                                                                                          "SSL handshake timeout",
                                                                                          e)))
               .subscribe();
    }

    private void processProtocolVersionNegotiationWaiting() {

        this.sm.observe()
               .filter(c -> ClientState.PROTOCOL_VERSION_NEGOTIATION == c)
               .timeout(this.clientSession.getProtocolVersionNegotiationTimeout(),
                        TimeUnit.MILLISECONDS,
                        Schedulers.from(this.executor))
               .takeUntil(c -> ClientState.PROTOCOL_VERSION_NEGOTIATION == c)
               .doOnError(e -> this.disconnect(new ClientDisconnectReason(DisconnectReason
                                                                              .PROTOCOL_VERSION_NEGOTIATION_TIMEOUT,
                                                                          "Protocol version negotiation timeout",
                                                                          e)))
               .subscribe();

    }

    @Override
    public Channel getPrimaryChannel() {

        return this.primaryChannel;
    }

    @Override
    public boolean isOnline() {

        return this.getClientState() == ClientState.ONLINE;
    }

    @Override
    public Channel getChildChannel() {

        return this.childChannel;
    }

    private void processAuthorizingWaiting() {

        this.sm.observe()
               .filter(c -> ClientState.AUTHORIZING == c)
               .timeout(this.clientSession.getAuthorizationTimeout(),
                        TimeUnit.MILLISECONDS,
                        Schedulers.from(this.executor))
               .takeUntil(c -> ClientState.AUTHORIZING == c)
               .doOnError(e -> this.disconnect(new ClientDisconnectReason(DisconnectReason.AUTHORIZATION_TIMEOUT,
                                                                          "Authorization timeout",
                                                                          e)))
               .subscribe();

    }

    public ClientState getClientState() {

        return this.sm.getState();
    }

    @Override
    public void connect() {

        this.sm.accept(Event.CONNECTING);
    }

    @Override
    public void disconnect() {

        this.disconnect(new ClientDisconnectReason(DisconnectReason.CLIENT_APP_REQUEST, "Client application request"));
    }

    @Override
    public void disconnect(final ClientDisconnectReason reason) {

        this.disconnectReason = reason;
    }

    private void logDisconnectReason(final ClientDisconnectReason reason) {

        Single.just(reason).observeOn(Schedulers.from(this.executor)).subscribe(this::logDisconnectInQueue);
    }

    private void logDisconnectInQueue(final ClientDisconnectReason reason) {

        if (reason == null) {
            return;
        }

        final StringBuilder builder = new StringBuilder();
        builder.append("Disconnect now, reason [").append(reason.getDisconnectReason()).append("], comments [").append(
            reason.getDisconnectComments()).append("]");
        if (this.clientSession != null) {
            builder.append(", server address [")
                   .append(this.clientSession.getAddress())
                   .append("], transport name [")
                   .append(this.clientSession.getTransportName())
                   .append("]");
        }
        final String logMsg = builder.toString();
        if (reason.getError() != null) {
            LOGGER.info(logMsg, reason.getError());
        } else {
            LOGGER.info(logMsg);
        }

    }
    @Override
    public Observable<ClientState> observeClientState() {

       return this.sm.observe();
    }

    @Override
    public void primaryChannelDisconnected() {

        this.sm.accept(Event.DISCONNECTING);
    }

    @Override
    public void childChannelDisconnected() {

    }

    @Override
    public boolean isConnecting() {

        final ClientState state = this.getClientState();
        return state == ClientState.CONNECTING
               || state == ClientState.SSL_HANDSHAKE
               || state == ClientState.PROTOCOL_VERSION_NEGOTIATION
               || state == ClientState.AUTHORIZING;
    }

    @Override
    public Consumer<SecurityExceptionEvent> observeSslSecurity() {

        return e -> {

            final X509Certificate[] chain = e.getCertificateChain();
            final String authType = e.getAuthenticationType();
            final CertificateException ex = e.getException();

            LOGGER.error("[{}] CERTIFICATE_EXCEPTION: ", this.clientSession.getTransportName(), ex);

            if (this.clientSession.getSecurityExceptionHandler() == null
                || !this.clientSession.getSecurityExceptionHandler().isIgnoreSecurityException(chain, authType, ex)) {
                this.disconnect(new ClientDisconnectReason(DisconnectReason.CERTIFICATE_EXCEPTION,
                                                           String.format("Certificate exception %s", ex.getMessage()),
                                                           ex));

            }

        };
    }

    @Override
    public void terminate() {

        if (this.getClientState() == ClientState.ONLINE) {
            this.sm.accept(Event.DISCONNECTING);
        }
    }

    @Override
    protected void channelRead0(final ChannelHandlerContext ctx, final ProtocolMessage msg) throws Exception {

        if (msg instanceof PrimarySocketAuthAcceptorMessage) {
            setPrimarySocketAuthAcceptorMessage((PrimarySocketAuthAcceptorMessage) msg);
        } else if (msg instanceof ChildSocketAuthAcceptorMessage) {
            setChildSocketAuthAcceptorMessage((ChildSocketAuthAcceptorMessage) msg);
        } else {
            if (isConnecting()) {
                processAuthorizationMessage(ctx,msg);
            }


            ctx.fireChannelRead(msg);
        }
    }
    @Override
    public void userEventTriggered(final ChannelHandlerContext ctx, final Object evt) throws Exception {

        super.userEventTriggered(ctx, evt);


        if (evt instanceof SslHandshakeCompletionEvent) {
            processCompletionEvent(((SslHandshakeCompletionEvent) evt).isSuccess()
                                      ? SslCompletionEvent.success()
                                      : SslCompletionEvent.failed(((SslHandshakeCompletionEvent) evt).cause()));
        } else if (evt instanceof ProtocolVersionNegotiationCompletionEvent) {
            processCompletionEvent((ProtocolVersionNegotiationCompletionEvent) evt);
        }
    }
    private void processAuthorizationMessage(final ChannelHandlerContext ctx,
                                             final BinaryProtocolMessage protocolMessage) {

      /*  LOGGER.debug("[{}] Sending message [{}] to authorization provider",
                     this.clientSession.getTransportName(),
                     protocolMessage);*/

        if (protocolMessage instanceof OkResponseMessage) {
            LOGGER.debug(
                "[{}] Received AUTHORIZED notification from the authorization provider. SessionId [{}], userName [{}]",
                this.clientSession.getTransportName(),
                this.clientSession.getAuthorizationProvider().getSessionId(),
                this.clientSession.getAuthorizationProvider().getLogin());
            this.clientSession.setServerSessionId(this.clientSession.getAuthorizationProvider().getSessionId());
            processCompletionEvent(AuthorizationCompletionEvent.success());
            //authorizationEvent.onNext(AuthorizationCompletionEvent.success());
        } else if (protocolMessage instanceof ErrorResponseMessage) {

            final String errorReason = ((ErrorResponseMessage) protocolMessage).getReason();
            LOGGER.error("[{}] Received AUTHORIZATION_ERROR notification from the authorization provider, reason: [{}]",
                         this.clientSession.getTransportName(),
                         errorReason);
            processCompletionEvent(AuthorizationCompletionEvent.failed(new Exception(errorReason)));
           // authorizationEvent.onNext(AuthorizationCompletionEvent.failed(new Exception(errorReason)));

        } else if (protocolMessage instanceof HaloResponseMessage) {
            final LoginRequestMessage loginRequestMessage = new LoginRequestMessage();
            loginRequestMessage.setUsername(this.clientSession.getAuthorizationProvider().getLogin());
            loginRequestMessage.setTicket(this.clientSession.getAuthorizationProvider().getTicket());
            loginRequestMessage.setSessionId(this.clientSession.getAuthorizationProvider().getSessionId());
            DefaultChannelWriter.writeMessage(this.clientSession, ctx.channel(), loginRequestMessage).subscribe();
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
