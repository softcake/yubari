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
import org.softcake.yubari.netty.NettyIoSessionWrapperAdapter;
import org.softcake.yubari.netty.ProtocolVersionNegotiationEvent;
import org.softcake.yubari.netty.authorization.ClientAuthorizationProvider;
import org.softcake.yubari.netty.channel.ChannelAttachment;
import org.softcake.yubari.netty.client.processors.HeartbeatProcessor;
import org.softcake.yubari.netty.client.processors.StreamProcessor;
import org.softcake.yubari.netty.client.tasks.AbstractEventExecutorChannelTask;
import org.softcake.yubari.netty.client.tasks.AbstractEventExecutorTask;
import org.softcake.yubari.netty.client.tasks.ChannelWriteTimeoutChecker;
import org.softcake.yubari.netty.client.tasks.SendingErrorCheck;
import org.softcake.yubari.netty.client.tasks.SendingWarningCheck;
import org.softcake.yubari.netty.data.DroppableMessageHandler;
import org.softcake.yubari.netty.mina.ClientDisconnectReason;
import org.softcake.yubari.netty.mina.ClientListener;
import org.softcake.yubari.netty.mina.DisconnectedEvent;
import org.softcake.yubari.netty.mina.ISessionStats;
import org.softcake.yubari.netty.mina.TransportHelper;

import com.dukascopy.dds4.common.orderedExecutor.OrderedThreadPoolExecutor.OrderedRunnable;
import com.dukascopy.dds4.transport.common.mina.DisconnectReason;
import com.dukascopy.dds4.transport.common.protocol.binary.BinaryProtocolMessage;
import com.dukascopy.dds4.transport.msg.system.BinaryPartMessage;
import com.dukascopy.dds4.transport.msg.system.ChildSocketAuthAcceptorMessage;
import com.dukascopy.dds4.transport.msg.system.CurrencyMarket;
import com.dukascopy.dds4.transport.msg.system.DisconnectRequestMessage;
import com.dukascopy.dds4.transport.msg.system.HeartbeatOkResponseMessage;
import com.dukascopy.dds4.transport.msg.system.HeartbeatRequestMessage;
import com.dukascopy.dds4.transport.msg.system.JSonSerializableWrapper;
import com.dukascopy.dds4.transport.msg.system.MessageGroup;
import com.dukascopy.dds4.transport.msg.system.PrimarySocketAuthAcceptorMessage;
import com.dukascopy.dds4.transport.msg.system.ProtocolMessage;
import com.dukascopy.dds4.transport.msg.system.RequestInProcessMessage;
import com.dukascopy.dds4.transport.msg.system.StreamHeaderMessage;
import com.dukascopy.dds4.transport.msg.system.StreamingStatus;
import com.google.common.util.concurrent.ListeningExecutorService;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.ssl.SslHandshakeCompletionEvent;
import io.netty.util.Attribute;
import io.reactivex.Observable;
import io.reactivex.ObservableEmitter;
import io.reactivex.ObservableOnSubscribe;
import io.reactivex.Single;
import io.reactivex.SingleOnSubscribe;
import io.reactivex.functions.Consumer;
import io.reactivex.schedulers.Schedulers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

@Sharable
public class ClientProtocolHandler extends SimpleChannelInboundHandler<BinaryProtocolMessage> {
    private static final Logger LOGGER = LoggerFactory.getLogger(ClientProtocolHandler.class);
    private final TransportClientSession clientSession;
    private final ListeningExecutorService eventExecutor;
    private final ListeningExecutorService authEventExecutor;
    private final ListeningExecutorService streamProcessingExecutor;
    private final ListeningExecutorService syncRequestProcessingExecutor;
    private final List<Thread> eventExecutorThreadsForLogging = Collections.synchronizedList(new ArrayList<>());
    private final boolean logSkippedDroppableMessages;
    private final AtomicBoolean logEventPoolThreadDumpsOnLongExecution;
    private static final ThreadLocal<int[]>
        sentMessagesCounterThreadLocal
        = ThreadLocal.withInitial(new Supplier<int[]>() {
        @Override
        public int[] get() {

            return new int[1];
        }
    });

    private static final AtomicInteger nextId = new AtomicInteger(0);
    // Thread local variable containing each thread's ID
    private static final ThreadLocal<Integer> threadId = new ThreadLocal<Integer>() {
        protected Integer initialValue() {

            return nextId.getAndIncrement();
        }
    };

    // Returns the current thread's unique ID, assigning it if necessary
    public static int sentMessagesCounterIncrementAndGet() {

        final int[] ints = sentMessagesCounterThreadLocal.get();
        final int i = ++ints[0];
        return i;
    }

    private final StreamProcessor streamProcessor;
    private final DroppableMessageHandler droppableMessageHandler;
    private HeartbeatProcessor heartbeatProcessor;
    private ClientConnector clientConnector;

    public ClientProtocolHandler(final TransportClientSession session) {

        this.clientSession = PreCheck.notNull(session, "session");
        this.droppableMessageHandler = new DroppableMessageHandler(this.clientSession);
        this.logSkippedDroppableMessages = session.isLogSkippedDroppableMessages();
        this.logEventPoolThreadDumpsOnLongExecution = session.getLogEventPoolThreadDumpsOnLongExecution();
        this.eventExecutor = this.initEventExecutor();
        this.authEventExecutor = this.initAuthEventExecutor();
        this.streamProcessingExecutor = this.initStreamProcessingExecutor();
        this.syncRequestProcessingExecutor = this.initSyncRequestProcessingExecutor();
        this.streamProcessor = new StreamProcessor(clientSession, this.streamProcessingExecutor);
        this.heartbeatProcessor = new HeartbeatProcessor(clientSession);


    }

    public HeartbeatProcessor getHeartbeatProcessor() {

        return heartbeatProcessor;
    }

    public DroppableMessageHandler getDroppableMessageHandler() {

        return droppableMessageHandler;
    }

    public void setClientConnector(final ClientConnector clientConnector) {

        this.clientConnector = clientConnector;

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
        this.shutdown(this.eventExecutor,
                      this.clientSession.getEventPoolTerminationTimeUnitCount(),
                      this.clientSession.getEventPoolTerminationTimeUnit());
        this.shutdown(this.authEventExecutor,
                      this.clientSession.getAuthEventPoolTerminationTimeUnitCount(),
                      this.clientSession.getAuthEventPoolTerminationTimeUnit());
        this.shutdown(this.streamProcessingExecutor,
                      this.clientSession.getStreamProcessingPoolTerminationTimeUnitCount(),
                      this.clientSession.getStreamProcessingPoolTerminationTimeUnit());
        this.shutdown(this.syncRequestProcessingExecutor,
                      this.clientSession.getSyncRequestProcessingPoolTerminationTimeUnitCount(),
                      this.clientSession.getSyncRequestProcessingPoolTerminationTimeUnit());
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

    //TODO RXJava
    protected void channelRead0(final ChannelHandlerContext ctx, final BinaryProtocolMessage msg) throws Exception {


        final Attribute<ChannelAttachment> channelAttachmentAttribute = ctx.channel().attr(
            CHANNEL_ATTACHMENT_ATTRIBUTE_KEY);
        final ChannelAttachment attachment = channelAttachmentAttribute.get();

        LOGGER.trace("[{}] Message received {}, primary channel: {}",
                     this.clientSession.getTransportName(),
                     msg,
                     attachment.isPrimaryConnection());


        attachment.setLastReadIoTime(System.currentTimeMillis());
        this.processControlRequest(ctx, msg, attachment);

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

        if (evt instanceof SslHandshakeCompletionEvent && ((SslHandshakeCompletionEvent) evt).isSuccess()) {
            this.clientConnector.sslHandshakeSuccess();
        } else if (evt instanceof ProtocolVersionNegotiationEvent
                   && ((ProtocolVersionNegotiationEvent) evt).isSuccess()) {
            this.clientConnector.protocolVersionHandshakeSuccess();
        }
    }

    @Override
    public void exceptionCaught(final ChannelHandlerContext ctx, final Throwable cause) throws Exception {

        LOGGER.error(cause.getMessage(), cause);

        ctx.close();
    }

    void handleAuthorization(final ClientAuthorizationProvider authorizationProvider, final Channel session) {

        LOGGER.debug("[{}] Calling authorize on authorization provider", this.clientSession.getTransportName());
        authorizationProvider.authorize(new NettyIoSessionWrapperAdapter(session) {
            @Override
            public ChannelFuture write(final Object message) {

                return writeMessageOld(this.channel, (BinaryProtocolMessage) message);
            }
        });
    }

    void handleAuthorization(final Channel session) {

        LOGGER.debug("[{}] Calling authorize on authorization provider", this.clientSession.getTransportName());
        this.clientSession.getAuthorizationProvider().authorize(o -> writeMessageOld(session,
                                                                                     (BinaryProtocolMessage) o));


       /* this.clientSession.getAuthorizationProvider().authorize(new NettyIoSessionWrapperAdapter(session) {
            @Override
            public ChannelFuture write(final Object message) {

                return writeMessage(this.channel, (BinaryProtocolMessage) message);
            }
        });*/
    }

    private void processAuthorizationMessage(final ChannelHandlerContext ctx,
                                             final BinaryProtocolMessage requestMessage) {

        LOGGER.debug("[{}] Sending message [{}] to authorization provider",
                     this.clientSession.getTransportName(),
                     requestMessage);
        this.clientSession.getAuthorizationProvider().messageReceived(new NettyIoSessionWrapperAdapter(ctx.channel()) {
            @Override
            public ChannelFuture write(final Object message) {

                return writeMessageOld(this.channel, (BinaryProtocolMessage) message);
            }
        }, (ProtocolMessage) requestMessage);
    }

    public void fireAuthorized() {

        final CopyOnWriteArrayList<ClientListener> listeners = this.clientSession.getListeners();

        listeners.forEach(clientListener -> this.fireAuthorizedEvent(clientListener,
                                                                     this.clientSession.getTransportClient()));


        LOGGER.info("Authorize task in queue, server address [{}], transport name [{}]",
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


    public Single<Boolean> writeMessage(final Channel channel, final BinaryProtocolMessage responseMessage) {

        final int messagesCounter = sentMessagesCounterIncrementAndGet();
        LOGGER.info(Thread.currentThread().getName() + "  : ID: " + messagesCounter);


        return Single.create((SingleOnSubscribe<Boolean>) e -> {
            final ChannelFuture future = channel.writeAndFlush(responseMessage);
            final long procStartTime = System.currentTimeMillis();
            checkSendingErrorOnNotWritableChannel(channel, future).subscribe(new Consumer<TransportClientSession>() {
                @Override
                public void accept(final TransportClientSession transportClientSession) throws Exception {

                    transportClientSession.terminate();
                }
            });
            checkSendingWarning(procStartTime, future);
            checkSendError(procStartTime, future);

            future.addListener((ChannelFutureListener) cf -> {

                if (cf.isSuccess()) {
                    e.onSuccess(true);
                } else if (cf.isCancelled()) {
                    e.onError(new CancellationException("cancelled before completed"));
                } else if (cf.isDone() && cf.cause() != null) {
                    e.onError(cf.cause());
                } else {
                    e.onError(new Exception("Unexpected ChannelFuture state"));
                }
            });
        })
                         .doOnSuccess(aBoolean -> ClientProtocolHandler.this.updateChannelAttachement(channel,
                                                                                                   System
                                                                                                       .currentTimeMillis()))
                         .doOnError(cause -> LOGGER.error("[{}] Message send failed because of {}: {}",
                                                          clientSession.getTransportName(),
                                                          cause.getClass().getSimpleName(),
                                                          cause.getMessage()));
    }

    private Observable<TransportClientSession> checkSendingErrorOnNotWritableChannel(final Channel channel,
                                                                                     final ChannelFuture future) {

        return Observable.just(future)
                         .filter(channelFuture -> !channel.isWritable())
                         .delay(clientSession.getPrimaryConnectionPingTimeout(),
                                TimeUnit.MILLISECONDS,
                                Schedulers.from(clientSession.getScheduledExecutorService()))
                         .filter(channelFuture -> !channelFuture.isDone())
                         .map(channelFuture -> clientSession)
                         .doOnNext(session -> LOGGER.error(
                             "[{}] Failed to send message in timeout time {}ms, disconnecting",
                             session.getTransportName(),
                             session.getPrimaryConnectionPingTimeout()));

    }

    private void checkSendError(final long procStartTime, final ChannelFuture future) {

        Observable.just(future)
                  .delay(clientSession.getSendCompletionErrorDelay(),
                         TimeUnit.MILLISECONDS,
                         Schedulers.from(clientSession.getScheduledExecutorService()))
                  .filter(channelFuture -> !channelFuture.isDone())
                  .doOnNext(channelFuture -> LOGGER.error("[{}] Message was not sent in timeout time {}ms and is still "
                                                          + "waiting it's turn, CRITICAL SEND TIME: {}ms, possible "
                                                          + "network problem",
                                                          clientSession.getTransportName(),
                                                          clientSession.getSendCompletionErrorDelay(),
                                                          System.currentTimeMillis() - procStartTime))
                  .subscribe(channelFuture -> {
                      channelFuture.addListener((ChannelFutureListener) future1 -> {

                          if (future1.isSuccess()) {
                              LOGGER.error(
                                  "[{}] Message sending took {}ms, critical timeout time {}ms, possible network "
                                  + "problem",
                                  clientSession.getTransportName(),
                                  System.currentTimeMillis() - procStartTime,
                                  clientSession.getSendCompletionErrorDelay());
                          }
                      });

                  });
    }

    private void checkSendingWarning(final long procStartTime, final ChannelFuture future) {

        Observable.just(future)
                  .subscribeOn(Schedulers.from(clientSession.getScheduledExecutorService()))
                  .delay(clientSession.getSendCompletionWarningDelay(),
                         TimeUnit.MILLISECONDS,
                         Schedulers.from(clientSession.getScheduledExecutorService()))

                  .filter(channelFuture -> !channelFuture.isDone())
                  .doOnNext(channelFuture -> LOGGER.warn(
                      "[{}] Message sending takes too long time to complete: {}ms and is still waiting it's turn"
                      + ". Timeout time: {}ms, possible network problem",
                      clientSession.getTransportName(),
                      System.currentTimeMillis() - procStartTime,
                      clientSession.getSendCompletionWarningDelay()))
                  .subscribeOn(Schedulers.from(clientSession.getScheduledExecutorService()))
                  .subscribe(channelFuture -> {

                      channelFuture.addListener((ChannelFutureListener) future1 -> {
                          if (future1.isSuccess()) {
                              LOGGER.warn("[{}] Message sending took {}ms, timeout time {}ms, possible network "
                                          + "problem",
                                          clientSession.getTransportName(),
                                          System.currentTimeMillis() - procStartTime,
                                          clientSession.getSendCompletionWarningDelay());
                          }
                      });
                  });
    }

    private void updateChannelAttachement(final Channel channel, final long lastWriteIoTime) {

        final ChannelAttachment ca = channel.attr(CHANNEL_ATTACHMENT_ATTRIBUTE_KEY).get();
        ca.setLastWriteIoTime(lastWriteIoTime);
        ca.messageWritten();
    }

    public ChannelFuture writeMessageOld(final Channel channel, final BinaryProtocolMessage responseMessage) {

        final int messagesCounter = sentMessagesCounterIncrementAndGet();
        LOGGER.info(Thread.currentThread().getName() + "  : ID: " + messagesCounter);

        final boolean notWritable = !channel.isWritable();

        final ChannelFuture channelFuture = channel.writeAndFlush(responseMessage);

        final ChannelAttachment ca = channel.attr(CHANNEL_ATTACHMENT_ATTRIBUTE_KEY).get();

        final ChannelFutureListener listener = new ChannelFutureListener() {
            @Override
            public void operationComplete(final ChannelFuture future) throws Exception {

                Observable.create(new ObservableOnSubscribe<ChannelFuture>() {
                    @Override
                    public void subscribe(final ObservableEmitter<ChannelFuture> emitter) throws Exception {

                        emitter.onNext(future);
                    }
                }).subscribe(new Consumer<ChannelFuture>() {
                    @Override
                    public void accept(final ChannelFuture channelFuture1) throws Exception {

                        LOGGER.info("Hello from RxJava");
                        ca.setLastWriteIoTime(System.currentTimeMillis());
                        ca.messageWritten();

                    }
                });
            }
        };
        channelFuture.addListener(listener);
        //channelFuture.addListener(ca.getWriteIoListener());

        if (notWritable) {

            if (this.clientSession.getPrimaryConnectionPingTimeout() > 0L) {
                final ChannelWriteTimeoutChecker timeoutChecker = new ChannelWriteTimeoutChecker(this.clientSession,
                                                                                                 channelFuture);
                final ScheduledFuture<?> scheduledFuture = this.clientSession.getScheduledExecutorService().schedule(
                    timeoutChecker,
                    this.clientSession.getPrimaryConnectionPingTimeout(),
                    TimeUnit.MILLISECONDS);
                timeoutChecker.setScheduledFuture(scheduledFuture);
                channelFuture.addListener(timeoutChecker);
            }

            if (this.clientSession.getSendCompletionErrorDelay() > 0L) {
                this.scheduleSendErrorCheck(channelFuture);
            }

        } else if (isTimeToCheckError(messagesCounter)) {
            this.scheduleSendErrorCheck(channelFuture);


        } else if (isTimeToCheckWarning(messagesCounter)) {
            this.scheduleSendWarningCheck(channelFuture);
        }

        return channelFuture;
    }

    private boolean isTimeToCheckWarning(final int i) {

        return this.clientSession.getSendCompletionWarningDelay() > 0L
               && this.clientSession.getSendCompletionDelayCheckEveryNTimesWarning() > 0
               && i % this.clientSession.getSendCompletionDelayCheckEveryNTimesWarning() == 0;
    }

    private boolean isTimeToCheckError(final int i) {

        return this.clientSession.getSendCompletionErrorDelay() > 0L
               && this.clientSession.getSendCompletionDelayCheckEveryNTimesError() > 0
               && i % this.clientSession.getSendCompletionDelayCheckEveryNTimesError() == 0;
    }

    private void scheduleSendWarningCheck(final ChannelFuture channelFuture) {

        final SendingWarningCheck warningCheck = new SendingWarningCheck(channelFuture, this.clientSession);
        warningCheck.schedule();
    }

    private void scheduleSendErrorCheck(final ChannelFuture channelFuture) {

        final SendingErrorCheck errorCheck = new SendingErrorCheck(channelFuture, this.clientSession);
        errorCheck.schedule();
    }

    //TODO RxJava
    void processControlRequest(final ChannelHandlerContext ctx,
                               final BinaryProtocolMessage msg,
                               final ChannelAttachment attachment) {

        if (!(msg instanceof ProtocolMessage)) {
            return;
        }

        final ProtocolMessage requestMessage = (ProtocolMessage) msg;
        if (requestMessage.getSynchRequestId() != null && this.processSyncResponse(requestMessage)) {
            return;
        }

        if (requestMessage instanceof HeartbeatRequestMessage) {
            this.heartbeatProcessor.process(ctx, attachment, (HeartbeatRequestMessage) requestMessage);
        } else if (requestMessage instanceof DisconnectRequestMessage) {
            this.proccessDisconnectRequestMessage((DisconnectRequestMessage) requestMessage);
        } else if (requestMessage instanceof PrimarySocketAuthAcceptorMessage) {
            this.clientConnector.setPrimarySocketAuthAcceptorMessage((PrimarySocketAuthAcceptorMessage) requestMessage);
        } else if (requestMessage instanceof ChildSocketAuthAcceptorMessage) {
            this.clientConnector.setChildSocketAuthAcceptorMessage((ChildSocketAuthAcceptorMessage) requestMessage);
        } else if (requestMessage instanceof HeartbeatOkResponseMessage) {
            //  this.processSyncResponse(requestMessage);
        } else if (requestMessage instanceof JSonSerializableWrapper) {

            throw new UnsupportedOperationException("Do you really need this?");
        } else if (msg instanceof BinaryPartMessage
                   || msg instanceof StreamHeaderMessage
                   || msg instanceof StreamingStatus) {
            this.streamProcessor.process(ctx, msg);
            throw new UnsupportedOperationException("Do you really need this?");
        } else if (this.clientConnector.isAuthorizing()) {
            this.processAuthorizationMessage(ctx, msg);
        } else {
            this.fireFeedbackMessageReceived(ctx, requestMessage);
        }


    }

    private void proccessDisconnectRequestMessage(final DisconnectRequestMessage requestMessage) {

        final DisconnectRequestMessage message = requestMessage;
        final DisconnectReason reason = message.getReason() == null
                                        ? DisconnectReason.SERVER_APP_REQUEST
                                        : message.getReason();

        this.clientConnector.setDisconnectReason(new ClientDisconnectReason(reason,
                                                                            message.getHint(),
                                                                            "Disconnect request received"));
    }

    private boolean processSyncResponse(final ProtocolMessage message) {


       final SynchRequestProcessor synchRequestProcessor = this.clientSession.getSynchRequestProcessor();
        return synchRequestProcessor.processRequest(message);
/*
        final RequestMessageListenableFuture
            synchRequestFuture
            = this.clientSession.getSyncRequestFuture(message.getSynchRequestId());
        final boolean result;
        if (synchRequestFuture != null) {

            if (message instanceof RequestInProcessMessage) {
                result = true;
                synchRequestFuture.setInProcessResponseLastTime(System.currentTimeMillis());
            } else {


                this.syncRequestProcessingExecutor.submit(new OrderedRunnable() {
                    public void run() {

                        try {
                            synchRequestFuture.set(message);
                        } catch (final Exception e) {
                            LOGGER.error("Failed to process sync response for " + message, e);

                        }

                    }

                    public Object getOrderKey() {

                        return clientSession.getConcurrencyPolicy().getConcurrentKey(message);
                    }
                });
                result = !this.clientSession.isDuplicateSyncMessagesToClientListeners();
            }
        } else {
            result = false;
        }

        return result;*/
    }


    private void fireFeedbackMessageReceived(final ChannelHandlerContext ctx, final ProtocolMessage message) {


        final long currentDropableMessageTime = this.droppableMessageHandler.getCurrentDropableMessageTime(message);
        if (message instanceof CurrencyMarket) {
            this.clientSession.tickReceived();
        }


       /* if ((message instanceof CurrencyMarket)) {
            final long timeMillis = System.currentTimeMillis();
            ((CurrencyMarket)  message).setCreationTimestamp(timeMillis);
            LOGGER.error("Timestamp seted: {}", timeMillis);
           LOGGER.error("Timestamp in ClientProtocoll: {}", ((CurrencyMarket)  message).getCreationTimestamp());
        }
*/


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
        task.executeInExecutor(this.eventExecutor, stats);
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

}
