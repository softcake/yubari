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

import static org.softcake.yubari.netty.TransportAttributeKeys.CHANNEL_ATTACHMENT_ATTRIBUTE_KEY;

import org.softcake.yubari.netty.channel.ChannelAttachment;
import org.softcake.yubari.netty.client.TransportClientSession;

import com.dukascopy.dds4.transport.common.protocol.binary.BinaryProtocolMessage;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ConnectTimeoutException;
import io.reactivex.CompletableEmitter;
import io.reactivex.Observable;
import io.reactivex.Single;
import io.reactivex.SingleOnSubscribe;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Consumer;
import io.reactivex.schedulers.Schedulers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * @author René Neubert
 */
public class DefaultChannelWriter {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultChannelWriter.class);
    private static final ThreadLocal<int[]>
        SENT_MESSAGES_COUNTER_THREAD_LOCAL
        = ThreadLocal.withInitial(new Supplier<int[]>() {
        @Override
        public int[] get() {

            return new int[1];
        }
    });
    private final TransportClientSession clientSession;

    public DefaultChannelWriter(final TransportClientSession clientSession) {

        this.clientSession = clientSession;
    }

    public static int sentMessagesCounterIncrementAndGet() {

        final int[] ints = SENT_MESSAGES_COUNTER_THREAD_LOCAL.get();
        return ++ints[0];
    }

    public static int sentMessagesCounterGet() {

        final int[] ints = SENT_MESSAGES_COUNTER_THREAD_LOCAL.get();
        return ints[0];
    }

    private void updateChannelAttachment(final Channel channel, final long lastWriteIoTime) {

        final ChannelAttachment ca = channel.attr(CHANNEL_ATTACHMENT_ATTRIBUTE_KEY).get();
        ca.setLastWriteIoTime(lastWriteIoTime);
        ca.messageWritten();
    }


    public Single<ChannelFuture> writeMessageAndCheckTimeout(final Channel channel,
                                                             final BinaryProtocolMessage responseMessage,
                                                             final long timeOut,
                                                             final boolean checkErrorTimeout) {

        AtomicReference<ChannelFuture> reference = new AtomicReference<>();
        AtomicInteger msgCount = new AtomicInteger(0);
        AtomicLong processStartTime = new AtomicLong(0L);

        AtomicBoolean isTimeOut = new AtomicBoolean(Boolean.FALSE);
        return Single.create((SingleOnSubscribe<ChannelFuture>) emitter -> {
            processStartTime.set(System.currentTimeMillis());
            final ChannelFuture future = channel.writeAndFlush(responseMessage);
            final Disposable disposable = getTimeoutObservable(future,
                                                               timeOut,
                                                               isTimeOut,
                                                               checkErrorTimeout,
                                                               processStartTime).subscribe(aLong -> {

            }, throwable -> emitter.onError(new Exception("Unexpected error while timeout checking")));

            future.addListener((ChannelFutureListener) cf -> {

                if (cf.isSuccess() && cf.isDone()) {
                    emitter.onSuccess(cf);
                } else if (cf.isCancelled() && cf.isDone()) {
                    // Completed by cancellation
                    emitter.onError(new CancellationException("cancelled before completed"));
                } else if (cf.isDone() && cf.cause() != null) {
                    // Completed with failure
                    emitter.onError(cf.cause());
                } else if (!cf.isDone() && !cf.isSuccess() && !cf.isCancelled() && cf.cause() == null) {
                    // Uncompleted
                    emitter.onError(new ConnectTimeoutException());
                } else {
                    emitter.onError(new Exception("Unexpected ChannelFuture state"));
                }
                disposable.dispose();
            });
        }).doOnSuccess(channelFuture -> logSendingExecutionWarningOrError(isTimeOut,
                                                                          checkErrorTimeout,
                                                                          processStartTime));


    }

    private Observable<Long> getTimeoutObservable(final ChannelFuture future,
                                                  final long timeOut,
                                                  final AtomicBoolean isTimeOut,
                                                  final boolean checkErrorTimeout,
                                                  final AtomicLong processStartTime) {

        return Observable.timer(timeOut, TimeUnit.MILLISECONDS).doOnNext(aLong -> isTimeOut.set(Boolean.TRUE)).filter(
            aLong -> !future.isDone()).doOnNext(aLong -> logStillInExecutionWarningOrError(checkErrorTimeout,
                                                                                           processStartTime));
    }

    private void logStillInExecutionWarningOrError(final boolean checkErrorTimeout, final AtomicLong processStartTime) {

        if (checkErrorTimeout) {
            LOGGER.error("[{}] Message was not sent in timeout time {}ms and is still "

                         + "waiting it's turn, CRITICAL SEND TIME: " + "{}ms, possible " + "network problem",
                         clientSession.getTransportName(),
                         clientSession.getSendCompletionErrorDelay(),
                         System.currentTimeMillis() - processStartTime.get());
        } else {
            LOGGER.warn("[{}] Message sending takes too long time to complete: {}ms and is still waiting it's turn"

                        + ". Timeout time: {}ms, possible network " + "problem",
                        clientSession.getTransportName(),
                        System.currentTimeMillis() - processStartTime.get(),
                        clientSession.getSendCompletionWarningDelay());
        }
    }

    private void logSendingExecutionWarningOrError(final AtomicBoolean isTimeOut,
                                                   final boolean checkErrorTimeout,
                                                   final AtomicLong processStartTime) {

        if (isTimeOut.get()) {
            if (checkErrorTimeout) {
                LOGGER.error("[{}] Message sending took {}ms, critical timeout time {}ms, possible network "
                             + "problem",
                             clientSession.getTransportName(),
                             System.currentTimeMillis() - processStartTime.get(),
                             clientSession.getSendCompletionErrorDelay());
            } else {
                LOGGER.warn("[{}] Message sending took {}ms, timeout time {}ms, possible network " + "problem",
                            clientSession.getTransportName(),
                            System.currentTimeMillis() - processStartTime.get(),
                            clientSession.getSendCompletionWarningDelay());
            }
        }
    }


    private void writeAndCheckTimeout(final Channel channel,
                                      final BinaryProtocolMessage responseMessage,
                                      final CompletableEmitter emitter) {

        final int messagesCounter = sentMessagesCounterIncrementAndGet();
        final ChannelFuture future = channel.writeAndFlush(responseMessage);
        //future.addListener(NettyUtil.getDefaultChannelFutureListener(emitter));
        final long procStartTime = System.currentTimeMillis();
        checkSendingErrorOnNotWritableChannel(channel,
                                              future,
                                              procStartTime).subscribe(new Consumer<TransportClientSession>() {
            @Override
            public void accept(final TransportClientSession transportClientSession) throws Exception {

                transportClientSession.terminate();
            }
        });

        if (isTimeToCheckError(messagesCounter)) {
            checkSendError(procStartTime, future);
        } else if (isTimeToCheckWarning(messagesCounter)) {
            //  checkSendingWarning(procStartTime, future);
        }
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

    private Observable<TransportClientSession> checkSendingErrorOnNotWritableChannel(final Channel channel,
                                                                                     final ChannelFuture future,
                                                                                     final long procStartTime) {

        return Observable.just(future)
                         .filter(channelFuture -> !channel.isWritable())
                         .doOnNext(channelFuture -> {

                             if (clientSession.getSendCompletionErrorDelay() > 0L) {
                                 checkSendError(procStartTime, future);
                             }
                         })
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

    private void checkSendingWarning(final long procStartTime, final ChannelFuture future, final int count) {

        if (!isTimeToCheckWarning(count)) {
            return;
        }
        Single.just(future)
              .subscribeOn(Schedulers.from(clientSession.getScheduledExecutorService()))
              .delay(clientSession.getSendCompletionWarningDelay(),
                     TimeUnit.MILLISECONDS,
                     Schedulers.from(clientSession.getScheduledExecutorService()))

              .filter(channelFuture -> !channelFuture.isDone())
              .doOnSuccess(channelFuture -> LOGGER.warn(
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

}
