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
import org.softcake.yubari.netty.client.ClientConnectorStateMachine.Event;
import org.softcake.yubari.netty.mina.ClientDisconnectReason;
import org.softcake.yubari.netty.mina.TransportHelper;
import org.softcake.yubari.netty.ssl.SecurityExceptionEvent;

import com.dukascopy.dds4.transport.common.mina.DisconnectReason;
import com.dukascopy.dds4.transport.msg.system.ChildSocketAuthAcceptorMessage;
import com.dukascopy.dds4.transport.msg.system.PrimarySocketAuthAcceptorMessage;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ConnectTimeoutException;
import io.reactivex.ObservableEmitter;
import io.reactivex.Observer;
import io.reactivex.Single;
import io.reactivex.SingleEmitter;
import io.reactivex.SingleOnSubscribe;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Consumer;
import io.reactivex.functions.Predicate;
import io.reactivex.schedulers.Schedulers;
import io.reactivex.subjects.PublishSubject;
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
    private static final String STATE_CHANGED_TO = "[{}] State changed to {}";
    private static final List<Thread> eventExecutorThreadsForLogging = Collections.synchronizedList(new ArrayList<>());
    private final PublishSubject<ClientState> stateObservable;
   // private final AtomicReference<ClientState> clientState;
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
    private ObservableEmitter<ClientState> emitter;
    private ClientConnectorStateMachine sm;
    private boolean primarySocketAuthAcceptorMessageSent = false;
    private Disposable disposable;
    private volatile Channel primaryChannel;
    private volatile Channel childChannel;
    private ChannelAttachment primarySessionChannelAttachment;
    private ChannelAttachment childSessionChannelAttachment;
    private ChildSocketAuthAcceptorMessage childSocketAuthAcceptorMessage;
    private PrimarySocketAuthAcceptorMessage primarySocketAuthAcceptorMessage;
    private Disposable onlineThread;


    ClientSateConnector2(InetSocketAddress address,
                         final Bootstrap channelBootstrap,
                         final TransportClientSession clientSession,
                         final ClientProtocolHandler protocolHandler) {

        final ThreadFactory threadFactory = new ThreadFactoryBuilder().setDaemon(true).setNameFormat(String.format(
            "[%s] TransportClientEventExecutorThread",
            "DDS2 Standalone Transport Client")).build();

        this.scheduledExecutorService = Executors.newSingleThreadExecutor(threadFactory);
       // this.clientState = new AtomicReference<>(ClientState.DISCONNECTED);
        this.address = address;
        this.channelBootstrap = channelBootstrap;
        this.clientSession = clientSession;
        this.protocolHandler = protocolHandler;
        this.stateObservable = PublishSubject.create();


        sm = new ClientConnectorStateMachine(this, executor);
        sm.connect();
        /* this.stateObservable = Observable.create((ObservableOnSubscribe<ClientState>) e -> emitter = e).subscribeOn(
            Schedulers.from(executor)).doOnError(throwable -> logDisconnectReason(new ClientDisconnectReason(
            DisconnectReason.UNKNOWN,
            "Unexpected error"))).onExceptionResumeNext(Observable.just(ClientState.DISCONNECTING,
                                                                        ClientState.DISCONNECTED)).publish();*/


    }

    public ClientSateConnector2() {

        this(null, null, null, null);

    }


    @Override
    public void onIdleEnter(ClientState state) {


        stateObservable.toSerialized().onNext(state);
    }

    @Override
    public void onIdleExit(ClientState state) {

    }

    @Override
    public void onConnectingEnter(ClientState state) {


        processConnecting(state)
            .subscribe(channel -> stateObservable.toSerialized().onNext(state));


    }

    @Override
    public void onConnectingExit(ClientState state) {

    }

    @Override
    public void onSslHandshakeEnter(ClientState state) {


        processProtocolVersionNegotiationWaiting();
        stateObservable.toSerialized().onNext(state);
    }

    @Override
    public void onSslHandshakeExit(ClientState state) {

    }

    @Override
    public void onProtocolVersionNegotiationEnter(ClientState state) {


        processAuthorizingWaiting();
        stateObservable.toSerialized().onNext(state);
        //sm.accept(Event.AUTHORIZING);
    }

    @Override
    public void onProtocolVersionNegotiationExit(ClientState state) {

    }

    @Override
    public void onAuthorizingEnter(ClientState state) {


        stateObservable.toSerialized().onNext(state);
        sm.accept(Event.ONLINE);
    }

    @Override
    public void onAuthorizingExit(ClientState state) {

    }

    @Override
    public void onOnlineEnter(ClientState state) {


//        onlineThread = Observable.interval(100, 500, TimeUnit.MILLISECONDS, Schedulers.from(executor))
//                                 .subscribe(new Consumer<Long>() {
//                                     @Override
//                                     public void accept(final Long aLong) throws Exception {
//
//                                         processOnline();
//
//                                     }
//                                 });

        stateObservable.toSerialized().onNext(state);
        processOnline();

    }

    @Override
    public void onOnlineExit(ClientState state) {


    }

    @Override
    public void onDisconnectingEnter(ClientState state) {
       // this.onlineThread.dispose();

        stateObservable.toSerialized().onNext(state);
    }

    @Override
    public void onDisconnectingExit(ClientState state) {

    }

    @Override
    public void onDisconnectedEnter(ClientState state) {


        stateObservable.toSerialized().onNext(state);
    }

    @Override
    public void onDisconnectedExit(ClientState state) {

    }

    private boolean processOnline() {

        if (isChannelInActive(this.primaryChannel)) {
            LOGGER.warn("[{}] Primary session disconnected. Disconnecting transport client",
                        this.clientSession.getTransportName());


            disConnect(new ClientDisconnectReason(DisconnectReason.CONNECTION_PROBLEM,
                                                  "Primary session is not active"));

            return false;

        }

        if (this.processPrimarySocketAuthAcceptorMessage().blockingGet()) {

        }
        protocolHandler.getHeartbeatProcessor().startSendPingPrimary();

        if (!this.clientSession.isUseFeederSocket() || this.childSocketAuthAcceptorMessage == null) {return true;}

        if (!isChannelInActive(this.childChannel)) {
            clientSession.getProtocolHandler().getHeartbeatProcessor().sendPing(Boolean.FALSE);

            if (this.canResetChildChannelReconnectAttempts()) {

                this.childSessionChannelAttachment.setReconnectAttempt(0);
            }
        } else {

            if (this.isMaxChildChannelReconnectAttemptsReached()) {
                LOGGER.warn("[{}] Child session max connection attempts reached. Disconnecting clientSession",
                            this.clientSession.getTransportName());
                disConnect(new ClientDisconnectReason(DisconnectReason.CONNECTION_PROBLEM,
                                                      "Child session max connection attempts" + " reached"));

                return false;
            }


            if (this.childSessionChannelAttachment.getLastConnectAttemptTime() + this.clientSession.getReconnectDelay()
                < System.currentTimeMillis()) {

                return this.connectChildSession().blockingGet();
            }
        }

        return true;

    }

    private static boolean isChannelInActive(final Channel channel) {

        return channel == null || !channel.isActive();
    }

    private Single<Boolean> connectChildSession() {

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


            disConnect(new ClientDisconnectReason(reason,
                                                  String.format("%s Child connection exception %s",
                                                                cause instanceof CertificateException
                                                                ? "Certificate"
                                                                : "Unexpected",
                                                                cause.getMessage()),
                                                  cause));
        });

        final Channel channel = channelSingle.blockingGet();

        childChannel = channel;
        channel.attr(CHANNEL_ATTACHMENT_ATTRIBUTE_KEY).set(childSessionChannelAttachment);

        return protocolHandler.writeMessage(childChannel, childSocketAuthAcceptorMessage)
                              .doOnSuccess(new Consumer<Boolean>() {
                                  @Override
                                  public void accept(final Boolean aBoolean) throws Exception {

                                      clientSession.getProtocolHandler().getHeartbeatProcessor().startSendPingChild();
                                  }
                              })
                              .doOnError(new io.reactivex.functions.Consumer<Throwable>() {
                                  @Override
                                  public void accept(final Throwable e) throws Exception {

                                      if (e.getCause() instanceof ClosedChannelException || !isOnline()) {return;}

                                      LOGGER.error("[{}] ", clientSession.getTransportName(), e);
                                      disConnect(new ClientDisconnectReason(DisconnectReason.CONNECTION_PROBLEM,
                                                                            String.format(
                                                                                "Child session error while writing: %s",
                                                                                childSocketAuthAcceptorMessage),
                                                                            e));
                                  }
                              });

    }


    private boolean canResetChildChannelReconnectAttempts() {

        return this.childSessionChannelAttachment.getReconnectAttempt() != 0
               && (this.childSessionChannelAttachment.getLastIoTime()
                   > this.childSessionChannelAttachment.getLastConnectAttemptTime()
                   || this.childSessionChannelAttachment.getLastConnectAttemptTime()
                      + this.clientSession.getChildConnectionReconnectsResetDelay() < System.currentTimeMillis());
    }

    private boolean isMaxChildChannelReconnectAttemptsReached() {

        return this.childSessionChannelAttachment.getReconnectAttempt()
               >= this.clientSession.getChildConnectionReconnectAttempts();
    }

    void setChildSocketAuthAcceptorMessage(final ChildSocketAuthAcceptorMessage acceptorMessage) {

        this.childSocketAuthAcceptorMessage = acceptorMessage;
        processOnline();

    }

    private Single<Boolean> processPrimarySocketAuthAcceptorMessage() {

        if (this.primarySocketAuthAcceptorMessageSent || this.primarySocketAuthAcceptorMessage == null) {
            return Single.just(false);
        }


        return this.protocolHandler.writeMessage(this.primaryChannel, this.primarySocketAuthAcceptorMessage)
                                   .doOnError(t -> {
                                       if (t.getCause() instanceof ClosedChannelException || !isOnline()) {return;}

                                       LOGGER.error("[{}] ", clientSession.getTransportName(), t);
                                       disConnect(new ClientDisconnectReason(DisconnectReason.CONNECTION_PROBLEM,
                                                                             String.format("Primary session error "
                                                                                           + "while writhing message:"
                                                                                           + " %s",
                                                                                           primarySocketAuthAcceptorMessage),
                                                                             t));
                                   })
                                   .doOnSuccess(new Consumer<Boolean>() {
                                       @Override
                                       public void accept(final Boolean aBoolean) throws Exception {

                                           primarySocketAuthAcceptorMessageSent = true;
                                       }
                                   });

    }

    void setPrimarySocketAuthAcceptorMessage(final PrimarySocketAuthAcceptorMessage acceptorMessage) {

        this.primarySocketAuthAcceptorMessage = acceptorMessage;
        processOnline();
    }

    private Single<Channel> processConnecting(final ClientState state) {

        primarySessionChannelAttachment = new ChannelAttachment(Boolean.TRUE);
        childSessionChannelAttachment = new ChannelAttachment(Boolean.FALSE);

        if (address == null) {
            LOGGER.error("[{}] Address not set in transport and "
                         + "AbstractClientAuthorizationProvider.getAddress returned null",
                         this.clientSession.getTransportName());

            disConnect(new ClientDisconnectReason(DisconnectReason.CONNECTION_PROBLEM,
                                                  "Address is not set in transport client"));
            return Single.never();
        }

        return processConnectingAndGetFuture(address).doOnSuccess(channel -> LOGGER.debug("[{}] primaryChannel = {}",
                                                                                   clientSession.getTransportName(),
                                                                                   channel))
                                              .doOnError(cause -> {
                                                  DisconnectReason reason = cause instanceof CertificateException
                                                                            ? DisconnectReason.CERTIFICATE_EXCEPTION
                                                                            : DisconnectReason.CONNECTION_PROBLEM;

                                                  disConnect(new ClientDisconnectReason(reason,
                                                                                        String.format("%s exception %s",
                                                                                                      cause
                                                                                                          instanceof
                                                                                                          CertificateException
                                                                                                      ? "Certificate"
                                                                                                      : "Unexpected",
                                                                                                      cause
                                                                                                          .getMessage
                                                                                                              ()),
                                                                                        cause));


                                              })
                                              .subscribeOn(Schedulers.from(executor))
                                              .doOnSuccess(channel -> waitForSslAndNegotitation())
                                              .doOnSuccess(channel -> primaryChannel = channel)
                                              .doOnSuccess(ch -> ch.attr(CHANNEL_ATTACHMENT_ATTRIBUTE_KEY).set(primarySessionChannelAttachment));


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

        stateObservable.filter(c -> ClientState.SSL_HANDSHAKE == c)
                       .timeout(this.clientSession.getSSLHandshakeTimeout(),
                                TimeUnit.MILLISECONDS,
                                Schedulers.from(executor))
                       .takeUntil(c -> ClientState.SSL_HANDSHAKE
                                       == c)
                       .doOnError(e -> disConnect(new ClientDisconnectReason(DisconnectReason.SSL_HANDSHAKE_TIMEOUT,
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

        stateObservable.filter(c -> ClientState.PROTOCOL_VERSION_NEGOTIATION == c)
                       .timeout(this.clientSession.getProtocolVersionNegotiationTimeout(),
                                TimeUnit.MILLISECONDS,
                                Schedulers.from(executor))
                       .takeUntil(c -> ClientState.PROTOCOL_VERSION_NEGOTIATION == c)
                       .doOnError(e -> disConnect(new ClientDisconnectReason(DisconnectReason
                                                                                 .PROTOCOL_VERSION_NEGOTIATION_TIMEOUT,
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
              .subscribe(errorReason1 -> disConnect(new ClientDisconnectReason(DisconnectReason.AUTHORIZATION_FAILED,
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

        stateObservable.filter(c -> ClientState.AUTHORIZING == c)
                       .timeout(this.clientSession.getAuthorizationTimeout(),
                                TimeUnit.MILLISECONDS,
                                Schedulers.from(executor))
                       .takeUntil(new Predicate<ClientState>() {
                           @Override
                           public boolean test(final ClientState c) throws Exception {

                               return ClientState.AUTHORIZING == c;
                           }
                       })
                       .doOnError(e -> disConnect(new ClientDisconnectReason(DisconnectReason.AUTHORIZATION_TIMEOUT,
                                                                             "Authorization timeout",
                                                                             e)))
                       .subscribe();

    }

    private void processDisconnect(final ClientState clientState) {


        emitter.onComplete();

    }

    public void error() {

        emitter.onError(new ClosedChannelException());
    }

    private ClientState getClientState() {

        return sm.getState();
    }

    public void connect() {

        sm.accept(Event.CONNECTING);
    }


    public void disConnect(final ClientDisconnectReason disconnectReason) {

        sm.accept(Event.DISCONNECTING);
        logDisconnectReason(disconnectReason);
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

        if (disconnectReason.getError() != null) {
            LOGGER.info(builder.toString(), disconnectReason.getError());
        } else {
            LOGGER.info(builder.toString());
        }

    }

    void observe(final Observer<ClientState> observer) {

        stateObservable.filter(new Predicate<ClientState>() {
            @Override
            public boolean test(final ClientState clientState) throws Exception {

                return true;
            }
        }).subscribe(observer);
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

    void securityException(final X509Certificate[] certificateChain,
                           final String authenticationType,
                           final CertificateException exception) {


    }

    public Consumer<SecurityExceptionEvent> observeSslSecurity() {

        return new Consumer<SecurityExceptionEvent>() {
            @Override
            public void accept(final SecurityExceptionEvent e) throws Exception {

                X509Certificate[] chain = e.getCertificateChain();
                String authType = e.getAuthenticationType();
                CertificateException ex = e.getException();



                LOGGER.error("[{}] CERTIFICATE_EXCEPTION: ", clientSession.getTransportName(), ex);

                if (clientSession.getSecurityExceptionHandler() == null
                    || !clientSession.getSecurityExceptionHandler().isIgnoreSecurityException(chain, authType, ex)) {
                    disConnect(new ClientDisconnectReason(DisconnectReason.CERTIFICATE_EXCEPTION,
                                                          String.format("Certificate exception %s",
                                                                        ex.getMessage()),
                                                          ex));

                }

            }
        };
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
