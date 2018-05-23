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

import org.softcake.yubari.netty.EventTimeoutChecker;
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

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;


/**
 * @author René Neubert
 */
public class MessageChannelHolder {
    private static final long ERROR_TIME_OFFSET = 5000L;
    private static final int SHORT_MESSAGE_LENGTH = 100;
    private static final Logger LOGGER = LoggerFactory.getLogger(MessageChannelHolder.class);
    private static final AtomicLong LAST_EXECUTION_WARNING_TIME = new AtomicLong(System.currentTimeMillis() - 10000L);
    private static final AtomicLong LAST_EXECUTION_ERROR_TIME = new AtomicLong(System.currentTimeMillis() - 10000L);
    private static final AtomicInteger MESSAGE_COUNTER = new AtomicInteger();
    final AtomicBoolean isTimeOutReached = new AtomicBoolean(false);
    final AtomicBoolean checkError = new AtomicBoolean(false);
    final AtomicLong startTime = new AtomicLong(0L);
    private final ChannelHandlerContext ctx;
    private final TransportClientSession session;
    private ProtocolMessage message;
    private Disposable observable;
    private Disposable overFlow;
    private ObservableEmitter<ProtocolMessage> emitter;
    public MessageChannelHolder(TransportClientSession session,
                                final ProtocolMessage message,
                                final ChannelHandlerContext ctx) {

        this.session = session;
        this.message = message;
        this.ctx = ctx;
    }

    private static boolean isTimeToCheck(final int messageCount,
                                         final long eventExecutionDelay,
                                         final int eventExecutionDelayCheckEveryNTimes) {

        return eventExecutionDelay > 0L
               && eventExecutionDelayCheckEveryNTimes > 0
               && messageCount % eventExecutionDelayCheckEveryNTimes == 0;
    }

    public ChannelHandlerContext getCtx() {

        return ctx;
    }

    public ProtocolMessage getMessage() {

        return message;
    }

    public Channel getChannel() {

        return ctx.channel();
    }
public long getStartTime(){
        return startTime.get();
}
    public MessageChannelHolder onStart() {

        final int messageCount = MESSAGE_COUNTER.getAndIncrement();
        if (isTimeToCheck(messageCount,
                          session.getEventExecutionErrorDelay(),
                          session.getEventExecutionDelayCheckEveryNTimesError())) {
            checkError.set(Boolean.TRUE);
        } else if (isTimeToCheck(messageCount,
                                 session.getEventExecutionWarningDelay(),
                                 session.getEventExecutionDelayCheckEveryNTimesWarning())) {
            checkError.set(Boolean.FALSE);
        } else {
            LOGGER.trace("[{}] It´s not yet time to check timeout on message: [{}]",
                         session.getTransportName(),
                         this.message);
            return this;
        }

        final long lastExecutionWarningOrErrorTime = checkError.get()
                                                     ? LAST_EXECUTION_ERROR_TIME.get()
                                                     : LAST_EXECUTION_WARNING_TIME.get();

        final long time = System.currentTimeMillis();
        if (lastExecutionWarningOrErrorTime + ERROR_TIME_OFFSET < time) {
            //clean();

            final boolean set;
            if (checkError.get()) {
                set = LAST_EXECUTION_ERROR_TIME.compareAndSet(lastExecutionWarningOrErrorTime, time);
            } else {
                set = LAST_EXECUTION_WARNING_TIME.compareAndSet(lastExecutionWarningOrErrorTime, time);
            }
            if (!set) {
                return this;
            }

            startTime.set(time);
            final long firstTime = checkError.get()
                           ? session.getEventExecutionErrorDelay()
                           : session.getEventExecutionWarningDelay();


            LOGGER.info("Timeout {}",firstTime);
            Observable.interval(firstTime, ERROR_TIME_OFFSET / 2, TimeUnit.MILLISECONDS).observeOn(Schedulers.computation())
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
        return this;
    }

    void clean() {

        if (observable != null) {
           // observable.dispose();
        }
        this.observable = null;
       // this.startTime.set(0L);
       // isTimeOutReached.set(false);
      //  checkError.set(false);
      //  overFlow = null;
    }

    private void logIncompleteExecution(final boolean isError, final long startTime, final long now) {

        final long postponeInterval = now - startTime;
        final String shortMessage = StrUtils.toSafeString(this.message, SHORT_MESSAGE_LENGTH);

        if (isErrorMessage(isError, postponeInterval)) {
            LOGGER.error("[{}] Event did not execute in timeout time {}ms and is still executing, "
                         + "CRITICAL EXECUTION WAIT TIME: {}ms, possible application problem or "
                         + "deadLock, message [{}]",
                         session.getTransportName(),
                         session.getEventExecutionErrorDelay(),
                         postponeInterval,
                         shortMessage);
        } else {
            LOGGER.warn("[{}] Event execution takes too long, running time {}ms and still executing, "
                        + "WARNING EXECUTION WAIT TIME: {}ms, possible application problem or "
                        + "deadLock, message [{}]",
                        session.getTransportName(),
                        session.getEventExecutionWarningDelay(),
                        postponeInterval,
                        shortMessage);


        }


    }

       private boolean isErrorMessage(final boolean isError, final long executionTime) {

        return ((session.getEventExecutionErrorDelay() > 0L) && (executionTime
                                                                 >= session.getEventExecutionErrorDelay())) || isError;
    }

    void logExecutionTime(final boolean isError, final long startTime, final long endTime) {

        final long executionTime = endTime - startTime;
        final String shortMessage = StrUtils.toSafeString(message, SHORT_MESSAGE_LENGTH);


        if (isErrorMessage(isError, executionTime)) {
            LOGGER.error("[{}] Event execution took {}ms, critical timeout time {}ms, possible"
                         + " application problem or deadLock, message [{}]",
                         session.getTransportName(),
                         executionTime,
                         session.getEventExecutionErrorDelay(),
                         shortMessage);
        } else {
            LOGGER.warn("[{}] Event execution took {}ms, warning timeout time {}ms, possible"
                        + " application problem or deadLock, message [{}]",
                        session.getTransportName(),
                        executionTime,
                        session.getEventExecutionWarningDelay(),
                        shortMessage);

        }


    }
    public void onDroppable() {

        LOGGER.warn("[{}] Newer message already has arrived, current processing is skipped [{}]",
                    session.getTransportName(),
                    message);

        if (isTimeOutReached.get()) {
            logExecutionTime(checkError.get(), this.startTime.get(), System.currentTimeMillis());
        }
        clean();
    }
    public void onComplete() {


        if (isTimeOutReached.get()) {
            logExecutionTime(checkError.get(), this.startTime.get(), System.currentTimeMillis());
        }
        clean();

    }



    private Disposable getOverFlowObservable() {

        Channel channel = ctx.channel();


        final AtomicLong procStartTime = new AtomicLong(System.currentTimeMillis());
        return Observable.create((ObservableOnSubscribe<ProtocolMessage>) e -> emitter = e)
                         .observeOn(Schedulers.computation())
                         .timeout(5000L, TimeUnit.MILLISECONDS)
                         .subscribe(protocolMessage -> {

                             final long currentTime = System.currentTimeMillis();
                             final long executionTime = currentTime - procStartTime.get();

                             if (executionTime > session.getEventExecutionErrorDelay()) {

                                 this.session.getChannelTrafficBlocker().suspend(channel);
                                 LOGGER.error(
                                     "[{}] Event executor queue overloaded, CRITICAL EXECUTION WAIT "
                                     + "TIME: {}ms, possible application problem or deadLock, message [{}]",
                                     session.getTransportName(),
                                     executionTime,
                                     StrUtils.toSafeString(protocolMessage, SHORT_MESSAGE_LENGTH));


                                 procStartTime.set(currentTime);
                             }

                         }, throwable -> this.session.getChannelTrafficBlocker().resume(channel));
    }

    public void onOverflow() {

        if (overFlow == null || overFlow.isDisposed()) {
            overFlow = getOverFlowObservable();
        }
        emitter.serialize().onNext(message);
    }

    public void onError(final Throwable throwable) {

        clean();
    }



}
