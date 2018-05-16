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

import org.softcake.yubari.netty.util.StrUtils;

import com.dukascopy.dds4.transport.msg.system.ProtocolMessage;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.reactivex.Observable;
import io.reactivex.ObservableEmitter;
import io.reactivex.ObservableOnSubscribe;
import io.reactivex.Observer;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class MessageProcessTimeoutChecker {


    private static final long ERROR_TIME_OFFSET = 5000L;
    private static final int SHORT_MESSAGE_LENGTH = 100;
    private static final Logger LOGGER = LoggerFactory.getLogger(MessageProcessTimeoutChecker.class);
    private final AtomicLong LAST_EXECUTION_WARNING_TIME = new AtomicLong(System.currentTimeMillis() - ThreadLocalRandom
        .current().nextLong(9999L, 49999L));
    private final AtomicLong LAST_EXECUTION_ERROR_TIME = new AtomicLong(System.currentTimeMillis() - ThreadLocalRandom
        .current().nextLong(9999L, 49999L));
    private final AtomicLong startTime = new AtomicLong(0L);
    private final String name;
    private final Channel channel;
    private AtomicBoolean checkError = new AtomicBoolean(false);
    private AtomicBoolean isTimeOutReached = new AtomicBoolean(false);
    private Disposable observable;
    private TransportClientSession session;
    private ProtocolMessage message;
    private Disposable overFlow;
    private ObservableEmitter<ProtocolMessage> emitter;

    public MessageProcessTimeoutChecker( final TransportClientSession clientSession, final Channel ctx,String name) {

        this.name = name;
        session = clientSession;
        this.channel = ctx;
    }

    private Disposable getOverFlowObservable() {

        final AtomicLong procStartTime = new AtomicLong(System.currentTimeMillis());
        return Observable.create((ObservableOnSubscribe<ProtocolMessage>) e -> emitter = e)
                         .observeOn(Schedulers.computation())
                         .timeout(5000L, TimeUnit.MILLISECONDS)
                         .subscribe(protocolMessage -> {

                             final long currentTime = System.currentTimeMillis();
                             final long executionTime = currentTime - procStartTime.get();

                             if (executionTime > session.getEventExecutionErrorDelay()) {
                                 this.session.getChannelTrafficBlocker().suspend(this.channel);
                                 LOGGER.error(
                                     "[{}] Subscriber: [{}] Event executor queue overloaded, CRITICAL EXECUTION WAIT "
                                     + "TIME: {}ms, possible application problem or deadLock, message [{}]",
                                     session.getTransportName(),
                                     name,
                                     executionTime,
                                     StrUtils.toSafeString(protocolMessage, SHORT_MESSAGE_LENGTH));


                                 procStartTime.set(currentTime);
                             }

                         }, throwable ->  this.session.getChannelTrafficBlocker().resume(this.channel));
    }

    private void logIncompleteExecution(final boolean isError, final long startTime, final long now) {

        final long postponeInterval = now - startTime;
        final String shortMessage = StrUtils.toSafeString(this.message, SHORT_MESSAGE_LENGTH);
        final boolean isErrorMessage = ((session.getEventExecutionErrorDelay() > 0L) && (postponeInterval
                                                                                         >= session
                                                                                             .getEventExecutionErrorDelay()))
                                       || isError;


        if (isErrorMessage) {
            LOGGER.error("[{}] Subscriber: [{}] Event did not execute in timeout time {}ms and is still executing, "
                         + "CRITICAL EXECUTION WAIT TIME: {}ms, possible application problem or "
                         + "deadLock, message [{}]",
                         session.getTransportName(),
                         name,
                         session.getEventExecutionErrorDelay(),
                         postponeInterval,
                         shortMessage);
        } else {
            LOGGER.warn("[{}] Subscriber: [{}] Event execution takes too long, running time {}ms and still executing, "
                        + "WARNING EXECUTION WAIT TIME: {}ms, possible application problem or "
                        + "deadLock, message [{}]",
                        session.getTransportName(),
                        name,
                        session.getEventExecutionWarningDelay(),
                        postponeInterval,
                        shortMessage);


        }


    }

    private void logExecutionTime(final boolean isError, final long startTime, final long endTime) {


        final String shortMessage = StrUtils.toSafeString(message, SHORT_MESSAGE_LENGTH);
        final long executionTime = endTime - startTime;
        final boolean isErrorMessage = ((session.getEventExecutionErrorDelay() > 0L) && (executionTime
                                                                                         >= session
                                                                                             .getEventExecutionErrorDelay()))
                                       || isError;
        if (isErrorMessage) {
            LOGGER.error("[{}] Subscriber: [{}] Event execution took {}ms, critical timeout time {}ms, possible"
                         + " application problem or deadLock, message [{}]",
                         session.getTransportName(),
                         name,
                         executionTime,
                         session.getEventExecutionErrorDelay(),
                         shortMessage);
        } else {
            LOGGER.warn("[{}] Subscriber: [{}] Event execution took {}ms, warning timeout time {}ms, possible"
                        + " application problem or deadLock, message [{}]",
                        session.getTransportName(),
                        name,
                        executionTime,
                        session.getEventExecutionWarningDelay(),
                        shortMessage);

        }


    }

    public void onStart(final ProtocolMessage msg, final long time, final boolean checkErrorTimeout) {


        final long lastExecutionWarningOrErrorTime = checkErrorTimeout
                                                     ? LAST_EXECUTION_ERROR_TIME.get()
                                                     : LAST_EXECUTION_WARNING_TIME.get();


        if (lastExecutionWarningOrErrorTime + ERROR_TIME_OFFSET < time) {
            clean();
            this.checkError.set(checkErrorTimeout);
            final boolean set;
            if (checkErrorTimeout) {
                set = LAST_EXECUTION_ERROR_TIME.compareAndSet(lastExecutionWarningOrErrorTime, time);
            } else {
                set = LAST_EXECUTION_WARNING_TIME.compareAndSet(lastExecutionWarningOrErrorTime, time);
            }
            if (!set) {
                return;
            }

            startTime.set(time);
            this.message = msg;
            Observable.interval(this.checkError.get()
                                ? session.getEventExecutionErrorDelay()
                                : session.getEventExecutionWarningDelay(), ERROR_TIME_OFFSET / 2, TimeUnit.MILLISECONDS)
                      .subscribe(new Observer<Long>() {
                          @Override
                          public void onSubscribe(final Disposable d) {

                              observable = d;
                          }

                          @Override
                          public void onNext(final Long aLong) {

                              final long now = System.currentTimeMillis();
                              if (isTimeOutReached.compareAndSet(false, true)) {
                                  logIncompleteExecution(checkError.get(), startTime.get(), now);
                              } else {
                                  logIncompleteExecution(checkError.get(), startTime.get(), now);
                                  clean();
                              }


                          }

                          @Override
                          public void onError(final Throwable e) {

                          }

                          @Override
                          public void onComplete() {

                          }
                      });
        }

    }

    private void clean() {

        if (observable != null) {
            observable.dispose();
        }
        this.observable = null;
        this.message = null;
        this.startTime.set(0L);
        isTimeOutReached.set(false);
        checkError.set(false);
        overFlow = null;
    }


    public void onComplete(final ProtocolMessage msg) {

        if (!msg.equals(this.message)) {
            return;
        }

        if (isTimeOutReached.get() == true) {
            logExecutionTime(checkError.get(), this.startTime.get(), System.currentTimeMillis());
        }
        clean();


    }


    public void onOverflow(final ProtocolMessage message) {

        if (overFlow == null || overFlow.isDisposed()) {
            overFlow = getOverFlowObservable();
        }
        emitter.serialize().onNext(message);
    }

    public void onDroppable(final ProtocolMessage message) {

        if (!message.equals(this.message)) {
            return;
        }
        LOGGER.warn("[{}] Subscriber: [{}] Newer message already has arrived, current processing is skipped [{}]",
                    session.getTransportName(),
                    name,
                    message);

        if (isTimeOutReached.get() == true) {
            logExecutionTime(checkError.get(), this.startTime.get(), System.currentTimeMillis());
        }
        clean();


    }

    public void onError(final Throwable throwable) {

        clean();
    }
}
