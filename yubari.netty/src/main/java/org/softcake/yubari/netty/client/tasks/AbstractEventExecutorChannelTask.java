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

package org.softcake.yubari.netty.client.tasks;

import org.softcake.yubari.netty.client.ClientProtocolHandler;
import org.softcake.yubari.netty.client.TransportClientSession;
import org.softcake.yubari.netty.mina.ISessionStats;
import org.softcake.yubari.netty.util.StrUtils;

import com.dukascopy.dds4.common.orderedExecutor.OrderedThreadPoolExecutor;
import com.dukascopy.dds4.transport.msg.system.ProtocolMessage;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.SettableFuture;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

public abstract class AbstractEventExecutorChannelTask implements OrderedThreadPoolExecutor.OrderedRunnable {
    private static final long ERROR_TIME_OFFSET = 5000000000L;
    private static final int SHORT_MESSAGE_LENGTH = 100;
    private static final long DEFAULT_SLEEP_TIME = 10L;
    private static final long LONGER_SLEEP_TIME = 50L;
    private static final long SHORTER_SLEEP_TIME = 5L;
    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractEventExecutorChannelTask.class);
    private static final AtomicLong LAST_EXECUTION_WARNING_TIME = new AtomicLong(System.nanoTime() - 100000000000L);
    private static final AtomicLong LAST_EXECUTION_ERROR_TIME = new AtomicLong(System.nanoTime() - 100000000000L);
    private static final ThreadLocal<int[]>
        EVENT_EXECUTOR_MESSAGES_COUNTER_THREAD_LOCAL
        = ThreadLocal.withInitial(new Supplier<int[]>() {
        @Override
        public int[] get() {

            return new int[1];
        }
    });
    protected final ProtocolMessage message;
    protected final long currentDropableMessageTime;
    private final Channel channel;
    private final TransportClientSession session;
    private final ClientProtocolHandler protocolHandler;
    private Runnable delayedExecutionTask;
    private long procStartTime;
    private long sleepTime = DEFAULT_SLEEP_TIME;


    protected AbstractEventExecutorChannelTask(final ChannelHandlerContext ctx,
                                               final TransportClientSession clientSession,
                                               final ProtocolMessage message,
                                               final long currentDropableMessageTime) {

        this.channel = ctx.channel();
        this.session = clientSession;
        this.message = message;
        this.currentDropableMessageTime = currentDropableMessageTime;
        this.protocolHandler = clientSession.getProtocolHandler();

    }

    public void executeInExecutor(final ListeningExecutorService executor, final ISessionStats stats) {

        final int[] messagesCounter = EVENT_EXECUTOR_MESSAGES_COUNTER_THREAD_LOCAL.get();
        ++messagesCounter[0];
LOGGER.info(String.valueOf(messagesCounter[0] % this.session.getEventExecutionDelayCheckEveryNTimesError()));
        final ScheduledExecutorService executorService = this.session.getScheduledExecutorService();

        if (executorService.isShutdown() || executorService.isTerminated()) {
            return;
        }

        final boolean checkError = this.session.getEventExecutionErrorDelay() > 0L
                                   && this.session.getEventExecutionDelayCheckEveryNTimesError() > 0
                                   && messagesCounter[0] % this.session.getEventExecutionDelayCheckEveryNTimesError()
                                      == 0;
        final boolean checkWarn = this.session.getEventExecutionWarningDelay() > 0L
                                  && this.session.getEventExecutionDelayCheckEveryNTimesWarning() > 0
                                  && messagesCounter[0] % this.session.getEventExecutionDelayCheckEveryNTimesWarning()
                                     == 0;

        if (checkError) {
            final ListenableFuture<?> future = this.executeInExecutor(executor, true);
            final Runnable runnable = getRunnableForErrorOrWarningCheck(stats,
                                                                        future,
                                                                        System.currentTimeMillis(),
                                                                        Boolean.TRUE);

            executorService.schedule(runnable, this.session.getEventExecutionErrorDelay(), TimeUnit.MILLISECONDS);

        } else if (checkWarn) {
            final ListenableFuture<?> future = this.executeInExecutor(executor, true);
            final Runnable runnable = getRunnableForErrorOrWarningCheck(stats,
                                                                        future,
                                                                        System.currentTimeMillis(),
                                                                        Boolean.FALSE);

            executorService.schedule(runnable, this.session.getEventExecutionWarningDelay(), TimeUnit.MILLISECONDS);
        } else {

            this.executeInExecutor(executor, false);
        }

    }


    private Runnable getRunnableForErrorOrWarningCheck(final ISessionStats stats,
                                                       final ListenableFuture future,
                                                       final long startTime,
                                                       final boolean isError) {

        return () -> {

            if (future.isDone()) {
                return;
            }

            final long nanoTime = System.nanoTime();
            final long lastExecutionWarningOrErrorTime;
            final boolean isSet;

            if (isError) {
                lastExecutionWarningOrErrorTime = LAST_EXECUTION_ERROR_TIME.get();
                isSet = LAST_EXECUTION_ERROR_TIME.compareAndSet(lastExecutionWarningOrErrorTime, nanoTime);
            } else {
                lastExecutionWarningOrErrorTime = LAST_EXECUTION_WARNING_TIME.get();
                isSet = LAST_EXECUTION_WARNING_TIME.compareAndSet(lastExecutionWarningOrErrorTime, nanoTime);
            }

            if (lastExecutionWarningOrErrorTime + ERROR_TIME_OFFSET < nanoTime && isSet) {

                final long now = System.currentTimeMillis();
                final long postponeInterval = now - startTime;
                final String shortMessage = StrUtils.toSafeString(message, SHORT_MESSAGE_LENGTH);

                if (isError) {
                    LOGGER.error("[{}] Event did not execute in timeout time [{}] and is still executing, "
                                 + "CRITICAL EXECUTION WAIT TIME: {}ms, possible application problem or "
                                 + "deadLock, message [{}]",
                                 session.getTransportName(),
                                 session.getEventExecutionErrorDelay(),
                                 postponeInterval,
                                 shortMessage);
                } else {
                    LOGGER.warn("[{}] Event execution takes too long, execution time: {}ms and still executing, "
                                + "possible application problem or deadLock, message [{}]",
                                session.getTransportName(),
                                postponeInterval,
                                shortMessage);
                }

                protocolHandler.checkAndLogEventPoolThreadDumps();

                if (stats != null) {
                    stats.messageProcessingPostponeError(now, postponeInterval, message);
                }

                future.addListener(getRunnableListenerForWarnOrErrorMessage(isError, startTime),
                                   MoreExecutors.newDirectExecutorService());
            }
        };
    }

    private Runnable getRunnableListenerForWarnOrErrorMessage(final boolean isError, final long startTime) {

        return () -> {

            final String shortMessage = StrUtils.toSafeString(message, SHORT_MESSAGE_LENGTH);
            final long executionTime = System.currentTimeMillis() - startTime;
            final boolean isErrorMessage = ((session.getEventExecutionErrorDelay() > 0L)
                                            && (executionTime >= session.getEventExecutionErrorDelay()))
                                           || isError;
            if (isErrorMessage) {
                LOGGER.error("[{}] Event execution took {}ms, critical timeout time {}ms, possible"
                             + " application problem or deadLock, message [{}]",
                             session.getTransportName(),
                             executionTime,
                             session.getEventExecutionErrorDelay(),
                             shortMessage);
            } else {
                LOGGER.warn("[{}] Event execution took more time than expected, execution time: "
                            + "{}ms, possible application problem or deadLock, message [{}]",
                            session.getTransportName(),
                            executionTime,
                            shortMessage);
            }

        };
    }

    private ListenableFuture<?> executeInExecutor(final ListeningExecutorService executor, final boolean withFuture) {

        final SettableFuture future = withFuture ? SettableFuture.create() : null;

        try {
            if (future != null) {
                future.setFuture(executor.submit(this));
            } else {
                executor.execute(this);
            }
        } catch (final RejectedExecutionException e) {

            this.procStartTime = System.currentTimeMillis();
            this.session.getChannelTrafficBlocker().suspend(this.channel);

            if (!session.getLogEventPoolThreadDumpsOnLongExecution().get()) {
                LOGGER.warn("Transport client's [{}, {}] channel reading suspended on [{}]",
                            this.session.getTransportName(),
                            this.session.getAddress(),
                            this.message);
            }

            this.delayedExecutionTask = () -> {

                try {
                    if (future != null) {
                        future.setFuture(executor.submit(AbstractEventExecutorChannelTask.this));
                    } else {
                        executor.execute(AbstractEventExecutorChannelTask.this);
                    }

                    session.getChannelTrafficBlocker().resume(channel);

                    if (!session.getLogEventPoolThreadDumpsOnLongExecution().get()) {
                        LOGGER.warn("Transport client's [{}, {}] channel reading resumed on [{}]",
                                    session.getTransportName(),
                                    session.getAddress(),
                                    message);
                    }
                } catch (final RejectedExecutionException ex) {
                    final long currentTime = System.currentTimeMillis();
                    final long executionTime = currentTime - procStartTime;

                    if (executionTime > session.getEventExecutionErrorDelay()) {
                        LOGGER.error(
                            "[{}] Event executor queue overloaded, CRITICAL EXECUTION WAIT TIME: {}ms, possible "
                            + "application problem or deadLock, message [{}]",
                            session.getTransportName(),
                            executionTime,
                            StrUtils.toSafeString(message, SHORT_MESSAGE_LENGTH));

                        protocolHandler.checkAndLogEventPoolThreadDumps();

                        procStartTime = currentTime;
                        sleepTime = LONGER_SLEEP_TIME;
                    }

                    channel.eventLoop().schedule(delayedExecutionTask, sleepTime, TimeUnit.MILLISECONDS);
                }

            };
            this.channel.eventLoop().schedule(this.delayedExecutionTask,
                                                                            SHORTER_SLEEP_TIME,
                                                                            TimeUnit.MILLISECONDS);
        }

        return future;
    }


}

