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
import org.softcake.yubari.netty.mina.TransportHelper;

import com.dukascopy.dds4.transport.common.mina.DisconnectReason;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ConnectTimeoutException;
import io.reactivex.Observable;
import io.reactivex.ObservableEmitter;
import io.reactivex.ObservableOnSubscribe;
import io.reactivex.Observer;
import io.reactivex.Single;
import io.reactivex.SingleOnSubscribe;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Predicate;
import io.reactivex.observables.ConnectableObservable;
import io.reactivex.schedulers.Schedulers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.nio.channels.ClosedChannelException;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author René Neubert
 */
public class ClientSateConnector {
    public static final int DEFAULT_EVENT_POOL_SIZE = 2;
    public static final long DEFAULT_EVENT_POOL_AUTO_CLEANUP_INTERVAL = 0L;
    public static final int DEFAULT_CRITICAL_EVENT_QUEUE_SIZE = 50;
    private static final Logger LOGGER = LoggerFactory.getLogger(ClientSateConnector.class);
    private static final String STATE_CHANGED_TO = "[{}] State changed to {}";
    private static final List<Thread> eventExecutorThreadsForLogging = Collections.synchronizedList(new ArrayList<>());
    private final ConnectableObservable<ClientState> stateObservable;
    private final AtomicReference<ClientState> clientState;
    private final ListeningExecutorService executor = TransportHelper.createExecutor(DEFAULT_EVENT_POOL_SIZE,
                                                                                     DEFAULT_EVENT_POOL_AUTO_CLEANUP_INTERVAL,
                                                                                     DEFAULT_CRITICAL_EVENT_QUEUE_SIZE,
                                                                                     "TransportClientEventExecutorThread",
                                                                                     eventExecutorThreadsForLogging,
                                                                                     "DDS2 Standalone Transport Client",
                                                                                     true);
    private final ExecutorService scheduledExecutorService;
    private final InetSocketAddress address;
    private final Bootstrap channelBootstrap;
    private final TransportClientSession clientSession;
    private final ClientProtocolHandler protocolHandler;
    ObservableEmitter<ClientState> emitter;
    private Disposable disposable;
    private Observer<ClientState> observer;
    private volatile Channel primaryChannel;
    private ChannelAttachment primarySessionChannelAttachment;
    private volatile Channel childChannel;
    private ChannelAttachment childSessionChannelAttachment;
    private long sslHandshakeStartTime = Long.MIN_VALUE;

    public ClientSateConnector(InetSocketAddress address,
                               final Bootstrap channelBootstrap,
                               final TransportClientSession clientSession,
                               final ClientProtocolHandler protocolHandler) {

        final ThreadFactory threadFactory = new ThreadFactoryBuilder().setDaemon(true).setNameFormat(String.format(
            "[%s] TransportClientEventExecutorThread",
            "DDS2 Standalone Transport Client")).build();

        this.scheduledExecutorService = Executors.newSingleThreadExecutor(threadFactory);
        this.clientState = new AtomicReference<>(ClientState.DISCONNECTED);
        this.address = address;
        this.channelBootstrap = channelBootstrap;
        this.clientSession = clientSession;
        this.protocolHandler = protocolHandler;
        this.stateObservable = Observable.create((ObservableOnSubscribe<ClientState>) e -> emitter = e).observeOn(
            Schedulers.from(executor)).doOnError(throwable -> logDisconnectReason(new ClientDisconnectReason(
            DisconnectReason.UNKNOWN,
            "Unexpected error"))).onExceptionResumeNext(Observable.just(ClientState.DISCONNECTING,
                                                                        ClientState.DISCONNECTED)).publish();


    }

    public ClientSateConnector() {

        this(null, null, null, null);

    }

    public static void main(final String[] args) {

        final ClientSateConnector connector = new ClientSateConnector();

        connector.connect();

        try {
            Thread.sleep(3000L);
        } catch (final InterruptedException e) {
            e.printStackTrace();
        }

        connector.error();
        connector.disConnect(new ClientDisconnectReason(DisconnectReason.CONNECTION_PROBLEM,
                                                        "Primary session is not active"));

    }

    public ListeningExecutorService getExecutor() {

        return executor;
    }

    private Observer<ClientState> createObserver() {

        return new Observer<ClientState>() {
            @Override
            public void onSubscribe(final Disposable d) {

            }

            @Override
            public void onNext(final ClientState clientState) {

                LOGGER.info(clientState.name());

                switch (clientState) {
                    case CONNECTING:
                        processConnecting(clientState);
                        break;
                    case SSL_HANDSHAKE_WAITING:
                        processSslHandShakeWaiting(clientState);
                        break;

                    case SSL_HANDSHAKE_SUCCESSFUL:
                        processSslHandShakeSuccessful(clientState);
                        break;

                    case PROTOCOL_VERSION_NEGOTIATION_WAITING:
                        processProtocolVersionNegotiationWaiting(clientState);
                        break;

                    case PROTOCOL_VERSION_NEGOTIATION_SUCCESSFUL:
                        processProtocolVersionNegotiationSuccessful(clientState);
                        break;
                    case AUTHORIZING_WAITING:
                        processAuthorizingWaiting(clientState);
                        break;
                    case AUTHORIZING__SUCCESSFUL:
                        processAuthorizingSuccessful(clientState);
                        break;
                    case ONLINE:
                        processOnline(clientState);
                        break;
                    case DISCONNECTING:
                        processDisconnecting(clientState);
                        break;

                    case DISCONNECTED:
                        processDisconnect(clientState);
                        break;

                    default:
                        throw new IllegalStateException("Unsupported state " + clientState);
                }
            }

            @Override
            public void onError(final Throwable e) {

            }

            @Override
            public void onComplete() {

                LOGGER.info("Completed");
                disposable.dispose();
            }
        };

    }

    private void processConnecting(final ClientState clientState) {


        //this.clientSession.getAuthorizationProvider().setListener(this);
        // this.authorizationProvider.setListener(this);
        this.primarySessionChannelAttachment = new ChannelAttachment(Boolean.TRUE);
        this.childSessionChannelAttachment = new ChannelAttachment(Boolean.FALSE);

        if (this.address == null) {
           /* LOGGER.error("[{}] Address not set in transport and "
                         + "AbstractClientAuthorizationProvider.getAddress returned null",
                         this.clientSession.getTransportName());*/

            disConnect(new ClientDisconnectReason(DisconnectReason.CONNECTION_PROBLEM,
                                                  "Address is not set in transport client"));
            return;
        }

        final Single<Channel> connectFuture = this.processConnectingAndGetFuture(this.address);
        Single<Channel> channelSingle = connectFuture.doOnSuccess(channel -> LOGGER.debug("[{}] primaryChannel = {}",
                                                                                          clientSession
                                                                                              .getTransportName(),
                                                                                          channel)).doOnError(cause -> {
            DisconnectReason reason = cause instanceof CertificateException
                                      ? DisconnectReason.CERTIFICATE_EXCEPTION
                                      : DisconnectReason.CONNECTION_PROBLEM;

            disConnect(new ClientDisconnectReason(reason,
                                                  String.format("%s exception %s",
                                                                cause instanceof CertificateException
                                                                ? "Certificate"
                                                                : "Unexpected",
                                                                cause.getMessage()),
                                                  cause));


        });

        channelSingle.subscribe(channel -> {
            primaryChannel = channel;
            channel.attr(CHANNEL_ATTACHMENT_ATTRIBUTE_KEY).set(primarySessionChannelAttachment);
            // processConnectOverSSLIfNecessary();
            tryToSetState(ClientState.DISCONNECTED, clientState);

            if (this.clientSession.isUseSSL()) {
                stateChangeEvent(ClientState.SSL_HANDSHAKE_WAITING).subscribe();

                this.sslHandshakeStartTime = System.currentTimeMillis();


            } else {
                stateChangeEvent(ClientState.PROTOCOL_VERSION_NEGOTIATION_WAITING).subscribe();
                //  this.protocolVersionNegotiationStartTime = System.currentTimeMillis();

            }

        });
    }


    private Single<Channel> processConnectingAndGetFuture(final InetSocketAddress address) {


        return Single.create((SingleOnSubscribe<Channel>) e -> {

            final ChannelFuture future = channelBootstrap.connect(address);
            future.addListener((ChannelFutureListener) cf -> {

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
            });
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

    private void processSslHandShakeWaiting(final ClientState clientState) {

        tryToSetState(ClientState.CONNECTING, clientState);

        stateObservable.filter(c -> ClientState.SSL_HANDSHAKE_SUCCESSFUL == c)
                       .timeout(this.clientSession.getSSLHandshakeTimeout(),
                                TimeUnit.MILLISECONDS,
                                Schedulers.from(executor))
                       .subscribe(new Observer<ClientState>() {

                           private Disposable disposable;

                           @Override
                           public void onSubscribe(final Disposable d) {
                               disposable = d;
                           }

                           @Override
                           public void onNext(final ClientState clientState) {
                               disposable.dispose();
                           }

                           @Override
                           public void onError(final Throwable e) {
                               disConnect(new ClientDisconnectReason(DisconnectReason.SSL_HANDSHAKE_TIMEOUT,
                                                                     "SSL handshake timeout",
                                                                     e));
                           }

                           @Override
                           public void onComplete() {
                           }
                       });

    }

    void sslHandshakeSuccess() {
        stateChangeEvent(ClientState.SSL_HANDSHAKE_SUCCESSFUL).subscribe();
       /* Single.just(ClientState.SSL_HANDSHAKE_SUCCESSFUL)
              .observeOn(Schedulers.from(executor))
              .subscribe(state -> stateChangeEvent(state));*/

    }

    void protocolVersionHandshakeSuccess() {

        stateChangeEvent(ClientState.PROTOCOL_VERSION_NEGOTIATION_SUCCESSFUL).subscribe();

       /* Single.just(ClientState.PROTOCOL_VERSION_NEGOTIATION_SUCCESSFUL)
              .observeOn(Schedulers.from(executor))
              .subscribe(ClientSateConnector.this::stateChangeEvent);*/
       /* if (!this.tryToSetState(ClientConnector.ClientState.PROTOCOL_VERSION_NEGOTIATION_WAITING,
                                ClientConnector.ClientState.PROTOCOL_VERSION_NEGOTIATION_SUCCESSFUL)
            && !this.tryToSetState(ClientConnector.ClientState.CONNECTING, ClientConnector.ClientState
            .PROTOCOL_VERSION_NEGOTIATION_SUCCESSFUL)
            && !this.tryToSetState(ClientConnector.ClientState.SSL_HANDSHAKE_SUCCESSFUL,
                                   ClientConnector.ClientState.PROTOCOL_VERSION_NEGOTIATION_SUCCESSFUL)) {
            this.tryToSetState(ClientConnector.ClientState.SSL_HANDSHAKE_WAITING, ClientConnector.ClientState
            .PROTOCOL_VERSION_NEGOTIATION_SUCCESSFUL);
        }*/

    }

    private void processSslHandShakeSuccessful(final ClientState clientState) {

        tryToSetState(ClientState.SSL_HANDSHAKE_WAITING, clientState);
        stateChangeEvent(ClientState.PROTOCOL_VERSION_NEGOTIATION_WAITING).subscribe();
    }

    private void processProtocolVersionNegotiationWaiting(final ClientState clientState) {

        tryToSetState(ClientState.SSL_HANDSHAKE_SUCCESSFUL, clientState);

        stateObservable.filter(c -> ClientState.PROTOCOL_VERSION_NEGOTIATION_SUCCESSFUL == c)
                       .timeout(this.clientSession.getProtocolVersionNegotiationTimeout(),
                                TimeUnit.MILLISECONDS,
                                Schedulers.from(executor))
                       .subscribe(new Observer<ClientState>() {

                           private Disposable disposable;

                           @Override
                           public void onSubscribe(final Disposable d) {
                               disposable = d;
                           }

                           @Override
                           public void onNext(final ClientState clientState) {
                               disposable.dispose();
                           }

                           @Override
                           public void onError(final Throwable e) {
                               disConnect(new ClientDisconnectReason(DisconnectReason.PROTOCOL_VERSION_NEGOTIATION_TIMEOUT,
                                                                     "Protocol version negotiation timeout",e));
                           }

                           @Override
                           public void onComplete() {
                           }
                       });
    }

    private void processProtocolVersionNegotiationSuccessful(final ClientState clientState) {

        tryToSetState(ClientState.PROTOCOL_VERSION_NEGOTIATION_WAITING, clientState);
        stateChangeEvent(ClientState.AUTHORIZING_WAITING).subscribe();
    }


    public void authorizationError(final String errorReason) {


        LOGGER.error("[{}] Received AUTHORIZATION_ERROR notification from the authorization provider, reason: [{}]",
                     this.clientSession.getTransportName(),
                     errorReason);
        Single.just(errorReason)
            .observeOn(Schedulers.from(executor))
            .subscribe(errorReason1 -> disConnect(new ClientDisconnectReason(DisconnectReason.AUTHORIZATION_FAILED,
                                                                     String.format("Authorization failed, "
                                                                + "reason [%s]", errorReason1))));



    }

    public void authorizingSuccess(final String sessionId, final String userName) {
        LOGGER.debug(
            "[{}] Received AUTHORIZED notification from the authorization provider. SessionId [{}], userName [{}]",
            this.clientSession.getTransportName(),
            sessionId,
            userName);
        stateChangeEvent(ClientState.AUTHORIZING__SUCCESSFUL).doOnSuccess(clientState -> clientSession.setServerSessionId(sessionId)).subscribe();
       /* Single.just(ClientState.AUTHORIZING__SUCCESSFUL)
            .observeOn(Schedulers.from(executor))
            .doOnSuccess(clientState -> clientSession.setServerSessionId(sessionId))
            .subscribe(state -> stateChangeEvent(state));*/




    }
    public Channel getPrimaryChannel() {

        return this.primaryChannel;
    }
    public boolean isOnline() {

        return this.clientState.get() == ClientState.ONLINE;
    }

    public Channel getChildChannel() {

        return this.childChannel;
    }
    private void processAuthorizingWaiting(final ClientState clientState) {

        tryToSetState(ClientState.PROTOCOL_VERSION_NEGOTIATION_SUCCESSFUL, clientState);
        stateObservable.filter(c -> ClientState.AUTHORIZING__SUCCESSFUL== c)
            .timeout(this.clientSession.getAuthorizationTimeout(),
                     TimeUnit.MILLISECONDS,
                     Schedulers.from(executor))
            .subscribe(new Observer<ClientState>() {

                private Disposable disposable;

                @Override
                public void onSubscribe(final Disposable d) {
                    disposable = d;
                }

                @Override
                public void onNext(final ClientState clientState) {
                    disposable.dispose();
                }

                @Override
                public void onError(final Throwable e) {
                    disConnect(new ClientDisconnectReason(DisconnectReason.AUTHORIZATION_TIMEOUT,
                        "Authorization timeout",e));
                }

                @Override
                public void onComplete() {
                }
            });

    }
    private void processAuthorizingSuccessful(final ClientState clientState) {

        tryToSetState(ClientState.AUTHORIZING_WAITING, clientState);
        stateChangeEvent(ClientState.ONLINE).subscribe();
    }
    private void processOnline(final ClientState clientState) {

        tryToSetState(ClientState.AUTHORIZING__SUCCESSFUL, clientState);
    }

    private void processDisconnecting(final ClientState clientState) {

        tryToSetState(ClientState.ONLINE, clientState);
        stateChangeEvent(ClientState.DISCONNECTED).subscribe();
    }

    private void processDisconnect(final ClientState clientState) {

        tryToSetState(ClientState.DISCONNECTING, clientState);
        emitter.onComplete();

    }

    public void error() {


        emitter.onError(new ClosedChannelException());

    }

    public ClientState getClientState() {

        return this.clientState.get();
    }

    public void connect() {

        stateObservable.subscribe(createObserver());
        this.disposable = this.stateObservable.connect();
        stateChangeEvent(ClientState.CONNECTING).subscribe();
    }

    public void disConnect(final ClientDisconnectReason disconnectReason) {

        logDisconnectReason(disconnectReason);
        stateChangeEvent(ClientState.DISCONNECTING).subscribe();
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
        /*if (this.clientSession != null) {
            builder.append(", server address [")
                   .append(this.clientSession.getAddress())
                   .append("], transport name [")
                   .append(this.clientSession.getTransportName())
                   .append("]");
        }*/

        if (disconnectReason.getError() != null) {
            LOGGER.info(builder.toString(), disconnectReason.getError());
        } else {
            LOGGER.info(builder.toString());
        }

    }

    public void observe(final Observer<ClientState> observer) {

        stateObservable.filter(new Predicate<ClientState>() {
            @Override
            public boolean test(final ClientState clientState) throws Exception {

                return true;
            }
        }).subscribe(observer);
    }

    public Single<ClientState> stateChangeEvent(final ClientState state) {




       return Single.just(state)
              .subscribeOn(Schedulers.from(executor))
              .doOnSuccess(s ->  emitter.onNext(s));
      /*        .subscribe(state -> stateChangeEvent(state));
        emitter.onNext(state);*/
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

        if (!this.clientState.compareAndSet(expected, newState)) {
            if (newState == ClientState.DISCONNECTING) {


                if (this.isStateChanged()) {
                    LOGGER.debug(STATE_CHANGED_TO, "TransportName", newState);
                }
            } else if (this.clientState.get() != newState) {
                throw new IllegalStateException(String.format("Expected state [%s], real state [%s]",
                                                              expected.name(),
                                                              this.clientState.get()));
            } else if (this.clientState.get() != newState) {
                return false;
            }
        }

        return true;

    }

    public enum ClientState {
        CONNECTING,
        SSL_HANDSHAKE_WAITING,
        SSL_HANDSHAKE_SUCCESSFUL,
        PROTOCOL_VERSION_NEGOTIATION_WAITING,
        PROTOCOL_VERSION_NEGOTIATION_SUCCESSFUL,
        AUTHORIZING_WAITING,
        AUTHORIZING__SUCCESSFUL,
        ONLINE,
        DISCONNECTING,
        DISCONNECTED;

        ClientState() {

        }
    }


}
