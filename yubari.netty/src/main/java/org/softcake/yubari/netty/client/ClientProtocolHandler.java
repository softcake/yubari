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
import org.softcake.yubari.netty.EventTimeoutChecker;
import org.softcake.yubari.netty.ProtocolVersionNegotiationCompletionEvent;
import org.softcake.yubari.netty.SslCompletionEvent;
import org.softcake.yubari.netty.channel.ChannelAttachment;
import org.softcake.yubari.netty.client.processors.HeartbeatProcessor;
import org.softcake.yubari.netty.client.processors.StreamProcessor;
import org.softcake.yubari.netty.client.tasks.AbstractEventExecutorTask;
import org.softcake.yubari.netty.data.DroppableMessageHandler;
import org.softcake.yubari.netty.data.DroppableMessageHandler2;
import org.softcake.yubari.netty.mina.ClientDisconnectReason;
import org.softcake.yubari.netty.mina.ClientListener;
import org.softcake.yubari.netty.mina.DisconnectedEvent;
import org.softcake.yubari.netty.mina.TransportHelper;

import com.dukascopy.dds4.transport.common.mina.DisconnectReason;
import com.dukascopy.dds4.transport.common.protocol.binary.BinaryProtocolMessage;
import com.dukascopy.dds4.transport.msg.system.BinaryPartMessage;
import com.dukascopy.dds4.transport.msg.system.ChildSocketAuthAcceptorMessage;
import com.dukascopy.dds4.transport.msg.system.DisconnectRequestMessage;
import com.dukascopy.dds4.transport.msg.system.ErrorResponseMessage;
import com.dukascopy.dds4.transport.msg.system.HaloRequestMessage;
import com.dukascopy.dds4.transport.msg.system.HaloResponseMessage;
import com.dukascopy.dds4.transport.msg.system.HeartbeatRequestMessage;
import com.dukascopy.dds4.transport.msg.system.JSonSerializableWrapper;
import com.dukascopy.dds4.transport.msg.system.LoginRequestMessage;
import com.dukascopy.dds4.transport.msg.system.OkResponseMessage;
import com.dukascopy.dds4.transport.msg.system.PrimarySocketAuthAcceptorMessage;
import com.dukascopy.dds4.transport.msg.system.ProtocolMessage;
import com.dukascopy.dds4.transport.msg.system.StreamHeaderMessage;
import com.dukascopy.dds4.transport.msg.system.StreamingStatus;
import com.google.common.util.concurrent.ListeningExecutorService;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.ssl.SslHandshakeCompletionEvent;
import io.netty.util.Attribute;
import io.reactivex.BackpressureStrategy;
import io.reactivex.Completable;
import io.reactivex.Flowable;
import io.reactivex.Observable;
import io.reactivex.Observer;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Consumer;
import io.reactivex.schedulers.Schedulers;
import io.reactivex.subjects.PublishSubject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

@Sharable
public class ClientProtocolHandler extends SimpleChannelInboundHandler<ProtocolMessage> {
    private static final Logger LOGGER = LoggerFactory.getLogger(ClientProtocolHandler.class);
    private static final ThreadLocal<int[]>
        SENT_MESSAGES_COUNTER_THREAD_LOCAL
        = ThreadLocal.withInitial(new Supplier<int[]>() {
        @Override
        public int[] get() {

            return new int[1];
        }
    });
    private static final ThreadLocal<int[]>
        EVENT_EXECUTOR_MESSAGES_COUNTER_THREAD_LOCAL
        = ThreadLocal.withInitial(new Supplier<int[]>() {
        @Override
        public int[] get() {

            return new int[1];
        }
    });
    private final PublishSubject<AbstractConnectingCompletionEvent> authorizationEvent;
    private final TransportClientSession clientSession;
    private final ListeningExecutorService eventExecutor;
    private final ListeningExecutorService authEventExecutor;
    private final ListeningExecutorService streamProcessingExecutor;
    private final ListeningExecutorService syncRequestProcessingExecutor;
    private final List<Thread> eventExecutorThreadsForLogging = Collections.synchronizedList(new ArrayList<>());
    private final boolean logSkippedDroppableMessages;
    private final AtomicBoolean logEventPoolThreadDumpsOnLongExecution;
    private final StreamProcessor streamProcessor;
    private final DroppableMessageHandler droppableMessageHandler;
    DroppableMessageHandler2 messageHandler2;
    private HeartbeatProcessor heartbeatProcessor;
    private IClientConnector clientConnector;
    private PublishSubject<MessageChannelHolder> transportMessageSubject = PublishSubject.create();
    private PublishSubject<Long> authEventPublisher = PublishSubject.create();
    private PublishSubject<DisconnectedEvent> disconnectedEventPublisher = PublishSubject.create();
    private Disposable unknow;

    public ClientProtocolHandler(final TransportClientSession session) {

        this.clientSession = PreCheck.notNull(session, "session");
        this.messageHandler2 = new DroppableMessageHandler2(clientSession.getDroppableMessagesClientTTL());
        this.droppableMessageHandler = new DroppableMessageHandler(clientSession.getDroppableMessagesClientTTL());
        this.logSkippedDroppableMessages = session.isLogSkippedDroppableMessages();
        this.logEventPoolThreadDumpsOnLongExecution = session.getLogEventPoolThreadDumpsOnLongExecution();
        this.eventExecutor = this.initEventExecutor();
        this.authEventExecutor = this.initAuthEventExecutor();
        this.streamProcessingExecutor = this.initStreamProcessingExecutor();
        this.syncRequestProcessingExecutor = this.initSyncRequestProcessingExecutor();
        this.streamProcessor = new StreamProcessor(clientSession, this.streamProcessingExecutor);
        this.heartbeatProcessor = new HeartbeatProcessor(clientSession);

        authorizationEvent = PublishSubject.create();

    }

    public static int sentMessagesCounterIncrementAndGet() {

        final int[] ints = SENT_MESSAGES_COUNTER_THREAD_LOCAL.get();
        return ++ints[0];
    }

    public static int eventExecutorMessagesCounterIncrementAndGet() {

        final int[] ints = EVENT_EXECUTOR_MESSAGES_COUNTER_THREAD_LOCAL.get();
        return ++ints[0];
    }

    public Observable<AbstractConnectingCompletionEvent> observeAuthorizationEvent(Executor executor) {

        return authorizationEvent.subscribeOn(Schedulers.from(executor));
    }

    public HeartbeatProcessor getHeartbeatProcessor() {

        return heartbeatProcessor;
    }

    public void setClientConnector(final IClientConnector clientConnector) {

        this.clientConnector = clientConnector;
        this.clientConnector.observe(new Observer<ClientConnector.ClientState>() {
            @Override
            public void onSubscribe(final Disposable d) {

            }

            @Override
            public void onNext(final ClientConnector.ClientState clientState) {

                if (ClientConnector.ClientState.SSL_HANDSHAKE == clientState) {

                } else if (ClientConnector.ClientState.PROTOCOL_VERSION_NEGOTIATION == clientState) {
                    handleAuthorization(clientConnector.getPrimaryChannel());
                } else if (ClientConnector.ClientState.ONLINE == clientState) {
                    LOGGER.info("State in ClientProtocolHandler {}", clientState.name());
                    initPublis();
                   fireAuthorized();

                } else if (ClientConnector.ClientState.DISCONNECTED == clientState) {
                    fireDisconnected();

                }

            }

            @Override
            public void onError(final Throwable e) {

                fireDisconnected();
            }

            @Override
            public void onComplete() {

            }
        });
    }

    private void initPublis() {


        if (this.unknow == null) {
            this.unknow = observeFeedbackMessages("unknow").subscribe(new Consumer<ProtocolMessage>() {
                @Override
                public void accept(final ProtocolMessage protocolMessage) throws Exception {

                    notifyListeners(protocolMessage);
                }
            });
        }

    }

    private void fireDisconnected() {

        ClientDisconnectReason disconnectReason = clientConnector.getDisconnectReason();
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
        disconnectedEventPublisher.onNext(disconnectedEvent);

       /* listeners.forEach(clientListener -> this.fireDisconnectedEvent(clientListener,
                                                                       clientSession.getTransportClient(),
                                                                       disconnectedEvent));*/

        this.clientSession.terminate();
    }

    private ListeningExecutorService initEventExecutor() {

        return TransportHelper.createExecutor(this.clientSession.getEventPoolSize(),
                                              this.clientSession.getEventPoolAutoCleanupInterval(),
                                              this.clientSession.getCriticalEventQueueSize(),
                                              "TransportClientEventExecutorThread",
                                              this.eventExecutorThreadsForLogging,
                                              this.clientSession.getTransportName(),
                                              true);

    }

    private ListeningExecutorService initAuthEventExecutor() {

        return TransportHelper.createExecutor(this.clientSession.getAuthEventPoolSize(),
                                              this.clientSession.getAuthEventPoolAutoCleanupInterval(),
                                              this.clientSession.getCriticalAuthEventQueueSize(),
                                              "TransportClientAuthEventExecutorThread",
                                              this.eventExecutorThreadsForLogging,
                                              this.clientSession.getTransportName(),
                                              true);

    }

    private ListeningExecutorService initStreamProcessingExecutor() {

        return TransportHelper.createExecutor(this.clientSession.getStreamProcessingPoolSize(),
                                              this.clientSession.getStreamProcessingPoolAutoCleanupInterval(),
                                              this.clientSession.getCriticalStreamProcessingQueueSize(),
                                              "TransportClientStreamProcessingThread",
                                              this.eventExecutorThreadsForLogging,
                                              this.clientSession.getTransportName(),
                                              false);

    }

    private ListeningExecutorService initSyncRequestProcessingExecutor() {

        return TransportHelper.createExecutor(this.clientSession.getSyncRequestProcessingPoolSize(),
                                              this.clientSession.getSyncRequestProcessingPoolAutoCleanupInterval(),
                                              this.clientSession.getCriticalSyncRequestProcessingQueueSize(),
                                              "TransportClientSyncRequestProcessingThread",
                                              this.eventExecutorThreadsForLogging,
                                              this.clientSession.getTransportName(),
                                              true);

    }

    ExecutorService getEventExecutor() {

        return this.eventExecutor;
    }

    void terminate() {

        this.streamProcessor.terminateStreams();
        TransportHelper.shutdown(this.eventExecutor,
                                 this.clientSession.getEventPoolTerminationTimeUnitCount(),
                                 this.clientSession.getEventPoolTerminationTimeUnit());
        TransportHelper.shutdown(this.authEventExecutor,
                                 this.clientSession.getAuthEventPoolTerminationTimeUnitCount(),
                                 this.clientSession.getAuthEventPoolTerminationTimeUnit());
        TransportHelper.shutdown(this.streamProcessingExecutor,
                                 this.clientSession.getStreamProcessingPoolTerminationTimeUnitCount(),
                                 this.clientSession.getStreamProcessingPoolTerminationTimeUnit());
        TransportHelper.shutdown(this.syncRequestProcessingExecutor,
                                 this.clientSession.getSyncRequestProcessingPoolTerminationTimeUnitCount(),
                                 this.clientSession.getSyncRequestProcessingPoolTerminationTimeUnit());
    }

    //TODO RXJava
    protected void channelRead0(final ChannelHandlerContext ctx, final ProtocolMessage msg) throws Exception {

        final Attribute<ChannelAttachment> channelAttachmentAttribute = ctx.channel().attr(
            CHANNEL_ATTACHMENT_ATTRIBUTE_KEY);
        final ChannelAttachment attachment = channelAttachmentAttribute.get();

        LOGGER.trace("[{}] Message received {}, primary channel: {}",
                     this.clientSession.getTransportName(),
                     msg,
                     attachment.isPrimaryConnection());

        //  ctx.fireChannelRead(msg);
        attachment.setLastReadIoTime(System.currentTimeMillis());
        this.processControlRequest(ctx, msg, attachment);


        //ReferenceCountUtil.release(msg);
    }

    @Override
    public void channelInactive(final ChannelHandlerContext ctx) throws Exception {

        super.channelInactive(ctx);
        final Attribute<ChannelAttachment> attribute = ctx.channel().attr(CHANNEL_ATTACHMENT_ATTRIBUTE_KEY);
        final ChannelAttachment attachment = attribute.get();
        if (attachment != null && this.clientConnector != null) {
            if (attachment.isPrimaryConnection()) {
                this.clientConnector.primaryChannelDisconnected();
            } else {
                this.clientConnector.childChannelDisconnected();
            }
        }

    }

    @Override
    public void userEventTriggered(final ChannelHandlerContext ctx, final Object evt) throws Exception {

        super.userEventTriggered(ctx, evt);

        if (evt instanceof SslHandshakeCompletionEvent) {
            authorizationEvent.onNext(((SslHandshakeCompletionEvent) evt).isSuccess()
                                      ? SslCompletionEvent.success()
                                      : SslCompletionEvent.failed(((SslHandshakeCompletionEvent) evt).cause()));
        } else if (evt instanceof ProtocolVersionNegotiationCompletionEvent) {
            authorizationEvent.onNext((ProtocolVersionNegotiationCompletionEvent) evt);
        }
    }

    @Override
    public void exceptionCaught(final ChannelHandlerContext ctx, final Throwable cause) throws Exception {

        LOGGER.error(cause.getMessage(), cause);

        ctx.close();
    }

    void handleAuthorization(final Channel session) {

        LOGGER.debug("[{}] Calling authorize on authorization provider", this.clientSession.getTransportName());
        final HaloRequestMessage haloRequestMessage = new HaloRequestMessage();
        haloRequestMessage.setPingable(true);
        haloRequestMessage.setUseragent(this.clientSession.getUserAgent());
        haloRequestMessage.setSecondaryConnectionDisabled(!this.clientSession.isUseFeederSocket());
        haloRequestMessage.setSecondaryConnectionMessagesTTL(this.clientSession.getDroppableMessagesServerTTL());
        haloRequestMessage.setSessionName(this.clientSession.getAuthorizationProvider().getSessionName());
        writeMessage(session, haloRequestMessage).subscribe();

    }

    private void processAuthorizationMessage(final ChannelHandlerContext ctx,
                                             final BinaryProtocolMessage protocolMessage) {

        LOGGER.debug("[{}] Sending message [{}] to authorization provider",
                     this.clientSession.getTransportName(),
                     protocolMessage);

        if (protocolMessage instanceof OkResponseMessage) {
            LOGGER.debug(
                "[{}] Received AUTHORIZED notification from the authorization provider. SessionId [{}], userName [{}]",
                this.clientSession.getTransportName(),
                this.clientSession.getAuthorizationProvider().getSessionId(),
                this.clientSession.getAuthorizationProvider().getLogin());
            this.clientSession.setServerSessionId(this.clientSession.getAuthorizationProvider().getSessionId());
            authorizationEvent.onNext(AuthorizationCompletionEvent.success());
        } else if (protocolMessage instanceof ErrorResponseMessage) {

            final String errorReason = ((ErrorResponseMessage) protocolMessage).getReason();
            LOGGER.error("[{}] Received AUTHORIZATION_ERROR notification from the authorization provider, reason: [{}]",
                         this.clientSession.getTransportName(),
                         errorReason);
            authorizationEvent.onNext(AuthorizationCompletionEvent.failed(new Exception(errorReason)));

        } else if (protocolMessage instanceof HaloResponseMessage) {
            final LoginRequestMessage loginRequestMessage = new LoginRequestMessage();
            loginRequestMessage.setUsername(this.clientSession.getAuthorizationProvider().getLogin());
            loginRequestMessage.setTicket(this.clientSession.getAuthorizationProvider().getTicket());
            loginRequestMessage.setSessionId(this.clientSession.getAuthorizationProvider().getSessionId());
            writeMessage(ctx.channel(), loginRequestMessage).subscribe();
        }
    }

    public void fireAuthorized() {

        final CopyOnWriteArrayList<ClientListener> listeners = this.clientSession.getListeners();
        authEventPublisher.onNext(System.currentTimeMillis());
       /* listeners.forEach(clientListener -> this.fireAuthorizedEvent(clientListener,
                                                                     this.clientSession.getTransportClient()));*/

        LOGGER.info("Authorize in queue, server address [{}], transport name [{}]",
                    this.clientSession.getAddress(),
                    this.clientSession.getTransportName());
    }

    void fireAuthorizedEvent(final ClientListener clientListener, final ITransportClient transportClient) {


        final AbstractEventExecutorTask task = new AbstractEventExecutorTask(this.clientSession, this) {
            @Override
            public void run() {

                try {
                    clientListener.authorized(transportClient);
                } catch (final Exception e) {
                    LOGGER.error("[{}] ", clientSession.getTransportName(), e);
                    clientConnector.disconnect(new ClientDisconnectReason(DisconnectReason.EXCEPTION_CAUGHT,
                                                                          String.format(
                                                                              "Exception caught while authorized "
                                                                              + "called %s",
                                                                              e.getMessage()),
                                                                          e));
                }

            }

            public Object getOrderKey() {

                return "";
            }
        };
        task.executeInExecutor(this.authEventExecutor);
    }

    void fireDisconnectedEvent(final ClientListener clientListener,
                               final ITransportClient transportClient,
                               final DisconnectedEvent disconnectedEvent) {

        final AbstractEventExecutorTask task = new AbstractEventExecutorTask(this.clientSession, this) {
            @Override
            public void run() {

                try {

                    clientListener.disconnected(transportClient, disconnectedEvent);
                } catch (final Exception e) {
                    LOGGER.error("[{}] ", clientSession.getTransportName(), e);
                    clientConnector.disconnect(new ClientDisconnectReason(DisconnectReason.EXCEPTION_CAUGHT,
                                                                          String.format(
                                                                              "Exception caught while disconnected "
                                                                              + "called %s",
                                                                              e.getMessage()),
                                                                          e));
                }

            }

            public Object getOrderKey() {

                return "";
            }
        };
        task.executeInExecutor(this.authEventExecutor);
    }

    public Completable writeMessage(final Channel channel, final BinaryProtocolMessage responseMessage) {

        return DefaultChannelWriter.writeMessage(clientSession, channel, responseMessage);
    }

    private void updateChannelAttachment(final Channel channel, final long lastWriteIoTime) {

        final ChannelAttachment ca = channel.attr(CHANNEL_ATTACHMENT_ATTRIBUTE_KEY).get();
        ca.setLastWriteIoTime(lastWriteIoTime);
        ca.messageWritten();
    }


    //TODO RxJava
    void processControlRequest(final ChannelHandlerContext ctx,
                               final ProtocolMessage msg,
                               final ChannelAttachment attachment) {

        if (!(msg instanceof ProtocolMessage)) {
            return;
        }

        final ProtocolMessage requestMessage = (ProtocolMessage) msg;
       /* if (this.processSyncResponse(requestMessage)) {
            return;
        }*/

        if (requestMessage instanceof HeartbeatRequestMessage) {
            this.heartbeatProcessor.process(ctx, attachment, (HeartbeatRequestMessage) requestMessage);
        } else if (requestMessage instanceof DisconnectRequestMessage) {
            this.proccessDisconnectRequestMessage((DisconnectRequestMessage) requestMessage);
        } else if (requestMessage instanceof PrimarySocketAuthAcceptorMessage) {
            // this.clientConnector.setPrimarySocketAuthAcceptorMessage((PrimarySocketAuthAcceptorMessage)
            // requestMessage);
        } else if (requestMessage instanceof ChildSocketAuthAcceptorMessage) {
            // this.clientConnector.setChildSocketAuthAcceptorMessage((ChildSocketAuthAcceptorMessage) requestMessage);
        } else if (requestMessage instanceof JSonSerializableWrapper) {
            throw new UnsupportedOperationException("Do you really need this?");
        } else if (msg instanceof BinaryPartMessage
                   || msg instanceof StreamHeaderMessage
                   || msg instanceof StreamingStatus) {
            this.streamProcessor.process(ctx, msg);
            throw new UnsupportedOperationException("Do you really need this?");
        } else if (this.clientConnector.isConnecting()) {
            this.processAuthorizationMessage(ctx, msg);
        } else {
            this.fireFeedbackMessageReceived(ctx, requestMessage);
        }


    }

    private void proccessDisconnectRequestMessage(final DisconnectRequestMessage requestMessage) {

        final DisconnectReason reason = requestMessage.getReason() == null
                                        ? DisconnectReason.SERVER_APP_REQUEST
                                        : requestMessage.getReason();

        this.clientConnector.disconnect(new ClientDisconnectReason(reason,
                                                                   requestMessage.getHint(),
                                                                   "Disconnect request received"));
    }

    private boolean processSyncResponse(final ProtocolMessage message) {

        final SynchRequestProcessor synchRequestProcessor = this.clientSession.getSynchRequestProcessor();
        return synchRequestProcessor.processRequest(message);
    }

    public Flowable<Long> observeAuthorizedEvent(final String name) {

        return Flowable.defer(() -> {

            EventTimeoutChecker<Long> checker = new EventTimeoutChecker<>(clientSession, name);

            return authEventPublisher.toFlowable(BackpressureStrategy.LATEST).subscribeOn(Schedulers.from(
                authEventExecutor)).doOnNext(new Consumer<Long>() {
                @Override
                public void accept(final Long o) throws Exception {

                    checker.onStart(o, System.currentTimeMillis(), sentMessagesCounterIncrementAndGet());
                }
            })

                                     .onBackpressureDrop(new Consumer<Long>() {
                                         @Override
                                         public void accept(final Long o) throws Exception {

                                             checker.onOverflow(o);
                                         }
                                     }).observeOn(Schedulers.from(authEventExecutor)).doAfterNext(new Consumer<Long>() {
                    @Override
                    public void accept(final Long msg) throws Exception {

                        checker.onComplete(msg);
                    }
                }).doOnError(checker::onError);

        });
    }

    public Flowable<ProtocolMessage> observeFeedbackMessages(String name) {

        return Flowable.defer(() -> {

            final EventTimeoutChecker<ProtocolMessage> checker = new EventTimeoutChecker(clientSession, name);

            return transportMessageSubject.toFlowable(BackpressureStrategy.LATEST)
                                          .subscribeOn(Schedulers.from(eventExecutor))
                                          .doOnNext(message -> checker.onStart(message.getMessage(),
                                                                               System.currentTimeMillis(),
                                                                               sentMessagesCounterIncrementAndGet()))
                                          .onBackpressureDrop(new Consumer<MessageChannelHolder>() {
                                              @Override
                                              public void accept(final MessageChannelHolder holder)
                                                  throws Exception {

                                                  checker.onOverflow(holder.getMessage(), holder.getChannel());
                                              }
                                          })
                                          .map(MessageChannelHolder::getMessage)
                                          .observeOn(Schedulers.from(eventExecutor))
                                          .filter(message -> {
                                              final boolean
                                                  canProcessDroppableMessage
                                                  = messageHandler2.canProcessDroppableMessage(message);
                                              if (!canProcessDroppableMessage) {
                                                  checker.onDroppable(message);

                                              }
                                              return canProcessDroppableMessage;

                                          })
                                          .doAfterNext(checker::onComplete)
                                          .doOnError(checker::onError);

        });
    }


    private void fireFeedbackMessageReceived(final ChannelHandlerContext ctx, final ProtocolMessage message) {


        //  messageHandler2.setCurrentDroppableMessageTime(message);


        transportMessageSubject.onNext(new MessageChannelHolder(message, ctx.channel()));
        // primaryPublishSubject.onNext(message);
/*


        final long currentDropableMessageTime = this.droppableMessageHandler.getCurrentDropableMessageTime(message);
        if (message instanceof CurrencyMarket) {
            this.clientSession.tickReceived();
        }



        if ((message instanceof CurrencyMarket)) {
            final long timeMillis = System.currentTimeMillis();
            ((CurrencyMarket)  message).setCreationTimestamp(timeMillis);
            LOGGER.error("Timestamp seted: {}", timeMillis);
           LOGGER.error("Timestamp in ClientProtocoll: {}", ((CurrencyMarket)  message).getCreationTimestamp());
        }




        final ISessionStats stats = this.clientSession.getSessionStats();
        final long creationTime;
        if (stats != null) {
            creationTime = System.currentTimeMillis();
            stats.messageInExecutionQueue(creationTime, message);
        } else {
            creationTime = 0L;
        }

        final AbstractEventExecutorChannelTask task = new AbstractEventExecutorChannelTask(ctx,
                                                                                           this.clientSession,
                                                                                           message,
                                                                                           currentDropableMessageTime) {
            public void run() {

                final boolean
                    canProcessCurrentDroppable
                    = droppableMessageHandler.canProcessDroppableMessage(this.message, this.currentDropableMessageTime);
                if (!canProcessCurrentDroppable) {
                    clientSession.getDroppedMessageCounter().incrementAndGet();
                    if (logSkippedDroppableMessages) {
                        LOGGER.warn("Newer message already has arrived, current processing is skipped <{}>",
                                    this.message);
                    }

                    if (stats != null) {
                        final long droppedTime = System.currentTimeMillis();
                        stats.messageDropped(droppedTime, droppedTime - creationTime, this.message);
                    }

                    return;
                }

                long beforeExecution = 0L;

                if (stats != null) {
                    beforeExecution = System.currentTimeMillis();
                    stats.messageExecutionStarted(beforeExecution, beforeExecution - creationTime, this.message);
                }

                if (this.message instanceof MessageGroup) {
                    ((MessageGroup) this.message).getMessages().forEach(ClientProtocolHandler.this::notifyListeners);
                } else {
                    notifyListeners(this.message);
                }

                if (stats != null) {
                    final long afterExecutionx = System.currentTimeMillis();
                    stats.messageExecutionFinished(afterExecutionx, afterExecutionx - beforeExecution, this.message);
                }

            }

            public Object getOrderKey() {

                return clientSession.getConcurrencyPolicy().getConcurrentKey(this.message);
            }
        };
        task.executeInExecutor(this.eventExecutor, stats);*/
    }

    private void notifyListeners(final ProtocolMessage protocolMessage) {

        final CopyOnWriteArrayList<ClientListener> listeners = this.clientSession.getListeners();
        for (final ClientListener listener : listeners) {
            try {
                listener.feedbackMessageReceived(clientSession.getTransportClient(), protocolMessage);
            } catch (final Exception e) {
                LOGGER.error("[{}] ", clientSession.getTransportName(), e);

            }
        }
    }

    public void checkAndLogEventPoolThreadDumps() {

        if (!this.logEventPoolThreadDumpsOnLongExecution.get()) {
            return;
        }

        final List<Thread> threads = new ArrayList<>(this.eventExecutorThreadsForLogging);

        for (final Thread thread : threads) {
            final long before = System.currentTimeMillis();
            final String stackTrace = Arrays.toString(thread.getStackTrace());

            LOGGER.warn("Transport client [{}, {}] thread's [{}] stack trace [{}], dump taking costed [{}]ms",
                        this.clientSession.getTransportName(),
                        this.clientSession.getAddress(),
                        thread.getName(),
                        stackTrace,
                        System.currentTimeMillis() - before);
        }
    }

    public Flowable<DisconnectedEvent> observeDisconnectedEvent(final String name) {

        return Flowable.defer(() -> {

            EventTimeoutChecker<DisconnectedEvent> checker = new EventTimeoutChecker<>(clientSession, name);

            return disconnectedEventPublisher.toFlowable(BackpressureStrategy.LATEST).subscribeOn(Schedulers.from(
                authEventExecutor)).doOnNext(new Consumer<DisconnectedEvent>() {
                @Override
                public void accept(final DisconnectedEvent o) throws Exception {

                    checker.onStart(o, System.currentTimeMillis(), sentMessagesCounterIncrementAndGet());
                }
            })

                                     .onBackpressureDrop(new Consumer<DisconnectedEvent>() {
                                         @Override
                                         public void accept(final DisconnectedEvent o) throws Exception {

                                             checker.onOverflow(o);
                                         }
                                     }).observeOn(Schedulers.from(authEventExecutor)).doAfterNext(new Consumer<DisconnectedEvent>() {
                    @Override
                    public void accept(final DisconnectedEvent msg) throws Exception {

                        checker.onComplete(msg);
                    }
                }).doOnError(checker::onError);

        });
    }



}
