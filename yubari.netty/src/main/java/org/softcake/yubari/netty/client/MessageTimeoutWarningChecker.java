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
import io.reactivex.Observable;
import io.reactivex.Observer;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Action;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class MessageTimeoutWarningChecker {


    private static final long ERROR_TIME_OFFSET = 5000L;
    private static final int SHORT_MESSAGE_LENGTH = 100;
    final AtomicLong processTime = new AtomicLong(0L);
    final AtomicLong exTime = new AtomicLong(0L);
    AtomicBoolean isReady = new AtomicBoolean(true);
    private static final Logger LOGGER = LoggerFactory.getLogger(MessageTimeoutWarningChecker.class);
    private ProtocolMessage message;
    Disposable observable;
    TransportClient session;
    private static final AtomicLong LAST_EXECUTION_WARNING_TIME = new AtomicLong(System.currentTimeMillis() - 100000L);
    private static final AtomicLong LAST_EXECUTION_ERROR_TIME = new AtomicLong(System.currentTimeMillis() - 100000L);
    public MessageTimeoutWarningChecker() {

        session = new TransportClient();
    }
    private Runnable getRunnableForErrorOrWarningCheck(                                                      final long startTime,
                                                       final boolean isError) {

        return () -> {



            final long nanoTime = System.currentTimeMillis();
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
                    clean();
                }






            }
        };
    }

    private Runnable getRunnableListenerForWarnOrErrorMessage(final boolean isError, final long startTime, final long endTime) {

        return () -> {

            final String shortMessage = StrUtils.toSafeString(message, SHORT_MESSAGE_LENGTH);
            final long executionTime = endTime;
            final boolean isErrorMessage = ((session.getEventExecutionErrorDelay() > 0L) && (executionTime
                                                                                             >= session
                                                                                                 .getEventExecutionErrorDelay()))
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

    public void setMessageAndStartTime(ProtocolMessage msg, long time) {

        if (isReady.compareAndSet(true, false)) {
            processTime.set(time);
            this.message = msg;
            Observable.interval(100L, TimeUnit.MILLISECONDS).doOnDispose(new Action() {
                @Override
                public void run() throws Exception {

                }
            }).subscribe(new Observer<Long>() {
                @Override
                public void onSubscribe(final Disposable d) {

                    observable = d;
                }

                @Override
                public void onNext(final Long aLong) {

                    final long startTime = processTime.get();
                    final long executionTime = exTime.get();
                    if (executionTime > Long.MIN_VALUE) {
                        if (executionTime >= session.getEventExecutionWarningDelay()) {
                            Runnable runnable = getRunnableListenerForWarnOrErrorMessage(false,
                                                                                         startTime, executionTime);

                            runnable.run();
                        }



                       /* long executionTime = exTime - processTime.get();
                        if (executionTime >= 100L) {


                            LOGGER.warn("Execution took : {}ms {}", executionTime, message);
                        }
*/

                        MessageTimeoutWarningChecker.this.exTime.set(Long.MIN_VALUE);
                        processTime.set(0L);
                        isReady.set(true);
                        observable.dispose();

                    } else {

                        getRunnableForErrorOrWarningCheck(startTime, false).run();
                       /* long executionTime = System.currentTimeMillis() - processTime.get();

                        LOGGER.warn("Execution takes too long and is still executing: {}ms {}", executionTime, message);
*/

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

    public void clean() {

        if (observable != null) {
            observable.dispose();
        }

        exTime.set(Long.MIN_VALUE);
        this.message = null;
        this.processTime.set(0L);
        this.isReady.set(true);
    }


    public boolean isReady() {

        return isReady.get();
    }

    public void setStartTime(long time) {

        processTime.set(time);
    }

    public void setComplete(final ProtocolMessage msg) {

        if (msg.equals(this.message)) {

            this.exTime.set(System.currentTimeMillis() - processTime.get());

        }
    }

    public void processComplete(final ProtocolMessage msg) {

        if (msg.equals(this.message)) {
            long executionTime = System.currentTimeMillis() - processTime.getAndSet(0L);
            if (executionTime >= 10L) {
                LOGGER.warn("Execution takes too long: {}ms {}", executionTime, message);
            }

            isReady.set(true);
        }
    }

}
