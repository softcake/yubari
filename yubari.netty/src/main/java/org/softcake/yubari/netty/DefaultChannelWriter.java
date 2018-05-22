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
import io.reactivex.Completable;
import io.reactivex.Observable;
import io.reactivex.Single;
import io.reactivex.disposables.Disposable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author René Neubert
 */
public final class DefaultChannelWriter {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultChannelWriter.class);
    private static final ThreadLocal<int[]>
        SENT_MESSAGES_COUNTER_THREAD_LOCAL
        = ThreadLocal.withInitial(() -> new int[1]);

    private DefaultChannelWriter() {

    }

    public static int sentMessagesCounterIncrementAndGet() {

        final int[] ints = SENT_MESSAGES_COUNTER_THREAD_LOCAL.get();
        return ++ints[0];
    }


    private static void updateChannelAttachment(final Channel channel, final long lastWriteIoTime) {

        final ChannelAttachment ca = channel.attr(CHANNEL_ATTACHMENT_ATTRIBUTE_KEY).get();
        ca.setLastWriteIoTime(lastWriteIoTime);
        ca.messageWritten();
    }

    public static Completable writeMessage(final TransportClientSession session,
                                           final Channel channel,
                                           final BinaryProtocolMessage responseMessage) {

        return writeMessageAndCheckTimeout(session, channel, responseMessage);
    }

    private static Completable writeMessageAndCheckTimeout(final TransportClientSession session,
                                                                     final Channel channel,
                                                                     final BinaryProtocolMessage responseMessage) {

        return Completable.create(emitter -> {
            final long processStartTime = System.currentTimeMillis();

            final boolean isNotWritable = !channel.isWritable();
            final ChannelFuture future = channel.writeAndFlush(responseMessage);
            final AtomicBoolean isTimeOut = new AtomicBoolean(Boolean.FALSE);

            final long timeoutTime = getTimeoutTime(session, sentMessagesCounterIncrementAndGet(), isNotWritable);
            final boolean checkErrorTimeout = timeoutTime == session.getSendCompletionErrorDelay();
            final Disposable disposable;

            if (timeoutTime == 0L) {
                disposable = new Disposable() {
                    @Override
                    public void dispose() {

                    }

                    @Override
                    public boolean isDisposed() {

                        return true;
                    }
                };
            } else {

                disposable = Observable.timer(timeoutTime, TimeUnit.MILLISECONDS)
                                       .map(aLong -> true)
                                       .doOnNext(isTimeOut::set)
                                       .filter(aLong -> !future.isDone())
                                       .doOnNext(aLong -> logStillInExecutionWarningOrError(session,
                                                                                            checkErrorTimeout,
                                                                                            processStartTime))
                                       .subscribe(aLong -> { },
                                                  throwable -> emitter.onError(new Exception(
                                                      "Unexpected error while timeout checking")));


                if (!isNotWritable) {
                    Observable.timer(session.getPrimaryConnectionPingTimeout(), TimeUnit.MILLISECONDS)
                              .map(aLong -> true)
                              .filter(aLong -> !future.isDone())
                              .doOnNext(aLong -> LOGGER.error(
                                  "[{}] Failed to send message in timeout time {}ms, disconnecting",
                                  session.getTransportName(),
                                  session.getPrimaryConnectionPingTimeout()))
                              .subscribe(aLong -> session.terminate(),
                                         throwable -> emitter.onError(new Exception(
                                             "Unexpected error while timeout checking")));
                }
            }

            future.addListener((ChannelFutureListener) cf -> {
                if (cf.isSuccess() && cf.isDone()) {
                    emitter.onComplete();
                    updateChannelAttachment(channel, System.currentTimeMillis());
                    if (isTimeOut.get()) {
                        logSendingExecutionWarningOrError(session, checkErrorTimeout, processStartTime);
                    }

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
        });
    }

    private static long getTimeoutTime(final TransportClientSession session,
                                       final int messageCount,
                                       final boolean channelIsNotWritable) {

        long timeOutTime = 0L;
        if (isTimeToCheckError(session, messageCount)) {
            timeOutTime = session.getSendCompletionErrorDelay();
        } else if (isTimeToCheckWarning(session, messageCount)) {
            timeOutTime = session.getSendCompletionWarningDelay();
        } else if (channelIsNotWritable && session.getSendCompletionErrorDelay() > 0) {
            timeOutTime = session.getSendCompletionErrorDelay();
        }
        return timeOutTime;
    }


    private static void logStillInExecutionWarningOrError(final TransportClientSession session,
                                                          final boolean checkErrorTimeout,
                                                          final long processStartTime) {

        if (checkErrorTimeout) {
            LOGGER.error("[{}] Message was not sent in timeout time {}ms and is still "
                         + "waiting it's turn, CRITICAL SEND TIME: {}ms, possible network problem",
                         session.getTransportName(),
                         session.getSendCompletionErrorDelay(),
                         System.currentTimeMillis() - processStartTime);
        } else {
            LOGGER.warn("[{}] Message sending takes too long time to complete: {}ms and is still waiting it's turn"
                        + ". Timeout time: {}ms, possible network problem",
                        session.getTransportName(),
                        System.currentTimeMillis() - processStartTime,
                        session.getSendCompletionWarningDelay());
        }
    }

    private static void logSendingExecutionWarningOrError(final TransportClientSession session,
                                                          final boolean checkErrorTimeout,
                                                          final long processStartTime) {

        if (checkErrorTimeout) {
            LOGGER.error("[{}] Message sending took {}ms, critical timeout time {}ms, possible network problem",
                         session.getTransportName(),
                         System.currentTimeMillis() - processStartTime,
                         session.getSendCompletionErrorDelay());
        } else {
            LOGGER.warn("[{}] Message sending took {}ms, timeout time {}ms, possible network problem",
                        session.getTransportName(),
                        System.currentTimeMillis() - processStartTime,
                        session.getSendCompletionWarningDelay());
        }

    }


    private static boolean isTimeToCheckWarning(final TransportClientSession session, final int messageCount) {

        return session.getSendCompletionWarningDelay() > 0L
               && session.getSendCompletionDelayCheckEveryNTimesWarning() > 0
               && messageCount % session.getSendCompletionDelayCheckEveryNTimesWarning() == 0;
    }

    private static boolean isTimeToCheckError(final TransportClientSession session, final int messageCount) {

        return session.getSendCompletionErrorDelay() > 0L
               && session.getSendCompletionDelayCheckEveryNTimesError() > 0
               && messageCount % session.getSendCompletionDelayCheckEveryNTimesError() == 0;
    }


}
