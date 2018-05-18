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

import static org.slf4j.LoggerFactory.getLogger;

import org.softcake.yubari.netty.data.DroppableMessageHandler2;
import org.softcake.yubari.netty.mina.TransportHelper;

import com.dukascopy.dds4.transport.msg.system.ProtocolMessage;
import com.google.common.util.concurrent.ListeningExecutorService;
import io.reactivex.BackpressureStrategy;
import io.reactivex.Flowable;
import io.reactivex.Observable;
import io.reactivex.functions.Consumer;
import io.reactivex.functions.Predicate;
import io.reactivex.schedulers.Schedulers;
import io.reactivex.subjects.PublishSubject;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * @author René Neubert
 */
public class RxPublishExample {
    public static final int DEFAULT_EVENT_POOL_SIZE = 3;//Math.max(Runtime.getRuntime().availableProcessors() / 2, 10);
    public static final long DEFAULT_EVENT_POOL_AUTO_CLEANUP_INTERVAL = 0L;
    public static final int DEFAULT_CRITICAL_EVENT_QUEUE_SIZE = 50;
    public static final long DEFAULT_EVENT_EXECUTION_WARNING_DELAY = 100L;
    public static final long DEFAULT_EVENT_EXECUTION_ERROR_DELAY = 1000L;
    private static final Logger LOGGER = getLogger(RXJavaExample.class);
    private static final List<Thread> eventExecutorThreadsForLogging = Collections.synchronizedList(new ArrayList<>());
    private static final int SHORT_MESSAGE_LENGTH = 100;
    private static final long LONGER_SLEEP_TIME = 50L;
    private static final long SHORTER_SLEEP_TIME = 5L;
    private static final long DEFAULT_SLEEP_TIME = 10L;
    private static final long DEFAULT_TIMEOUT_TIME = 1000L;
    private final ListeningExecutorService executor = TransportHelper.createExecutor(DEFAULT_EVENT_POOL_SIZE,
                                                                                     DEFAULT_EVENT_POOL_AUTO_CLEANUP_INTERVAL,
                                                                                     DEFAULT_CRITICAL_EVENT_QUEUE_SIZE,
                                                                                     "TransportClientEventExecutorThread",
                                                                                     eventExecutorThreadsForLogging,
                                                                                     "DDS2 Standalone Transport Client",
                                                                                     true);
    AtomicInteger messagesCounter = new AtomicInteger(0);
    AtomicInteger droppedMessagesCounter = new AtomicInteger(0);
    DroppableMessageHandler2 messageHandler2 = null; //new DroppableMessageHandler2(null);
    AtomicBoolean isSupsended = new AtomicBoolean(Boolean.FALSE);
    PublishSubject<ProtocolMessage> publishSubject = PublishSubject.create();
    TransportClient session = new TransportClient();
    private PublishSubject<ProtocolMessage> messagePublishSubject = PublishSubject.create();
    private static final ThreadLocal<int[]>
        EVENT_EXECUTOR_MESSAGES_COUNTER_THREAD_LOCAL
        = ThreadLocal.withInitial(new Supplier<int[]>() {
        @Override
        public int[] get() {

            return new int[1];
        }
    });
    public static void main(String[] args) {

        RxPublishExample example = new RxPublishExample();

        example.getMessagePublishSubject("First").subscribe(new Consumer<ProtocolMessage>() {
            @Override
            public void accept(final ProtocolMessage message) throws Exception {

                // LOGGER.info("Message in first Consumer: {}", message);

                final long nextLong = ThreadLocalRandom.current().nextLong(50, 200);
                try {
                    Thread.sleep(nextLong);
                } catch (final InterruptedException e) {
                    LOGGER.error("Error occurred in onNext for subscriber {}", "first", e);
                }

            }
        });

        example.getMessagePublishSubject("Second").subscribe(new Consumer<ProtocolMessage>() {
            @Override
            public void accept(final ProtocolMessage protocolMessage) throws Exception {

                // LOGGER.info("Message in second Consumer: {}", protocolMessage);

                final long nextLong = ThreadLocalRandom.current().nextLong(300, 400);
                try {
                    Thread.sleep(nextLong);
                } catch (final InterruptedException e) {
                    LOGGER.error("Error occurred in onNext for subscriber {}", "first", e);
                }
            }
        });

        example.startMessageCreator();

    }


    public DroppableMessageHandler2 getMessageHandler2() {

        return messageHandler2;
    }

    public ListeningExecutorService getExecutor() {

        return executor;
    }

    public void suspend() {

        isSupsended.set(Boolean.TRUE);
    }

    public void resume() {

        isSupsended.set(Boolean.FALSE);
    }

    public Observable<ProtocolMessage> getMessagePublishSubject2() {

        return publishSubject.subscribeOn(Schedulers.from(executor));
    }

    public Flowable<ProtocolMessage> getMessagePublishSubject(String name) {

        return Flowable.defer(new Callable<Publisher<? extends ProtocolMessage>>() {
            @Override
            public Publisher<? extends ProtocolMessage> call() throws Exception {

                MessageTimeoutWarningChecker checker = new MessageTimeoutWarningChecker(name, RxPublishExample.this);

                return messagePublishSubject.toFlowable(BackpressureStrategy.LATEST).subscribeOn(Schedulers.from(executor))
                                            .doOnNext(new Consumer<ProtocolMessage>() {
                                                @Override
                                                public void accept(final ProtocolMessage message) throws Exception {
                                                    final int[] messagesCounter = EVENT_EXECUTOR_MESSAGES_COUNTER_THREAD_LOCAL.get();
                                                    ++messagesCounter[0];
                                                    final boolean checkError = session.getEventExecutionErrorDelay()
                                                                               > 0L
                                                                               && session
                                                                                      .getEventExecutionDelayCheckEveryNTimesError()
                                                                                  > 0
                                                                               && messagesCounter[0]
                                                                                  % session
                                                                                      .getEventExecutionDelayCheckEveryNTimesError()
                                                                                  == 0;
                                                    final boolean checkWarn = session.getEventExecutionWarningDelay()
                                                                              > 0L
                                                                              && session
                                                                                     .getEventExecutionDelayCheckEveryNTimesWarning()
                                                                                 > 0
                                                                              && messagesCounter[0]
                                                                                 % session
                                                                                     .getEventExecutionDelayCheckEveryNTimesWarning()
                                                                                 == 0;


                                                    if (checkError) {
                                                        checker.onStart(message,
                                                                        System.currentTimeMillis(),
                                                                        Boolean.TRUE);
                                                    } else if (checkWarn) {
                                                        checker.onStart(message,
                                                                        System.currentTimeMillis(),
                                                                        Boolean.FALSE);
                                                    }

                                                }
                                            })
                                            .onBackpressureDrop(new Consumer<ProtocolMessage>() {
                                                @Override
                                                public void accept(final ProtocolMessage message) throws Exception {

                                                    checker.onOverflow(message);

                                                }
                                            })
                                            .observeOn(Schedulers.from(getExecutor()))
                                            .filter(message -> {


                                                final boolean
                                                    canProcessDroppableMessage
                                                    = getMessageHandler2().canProcessDroppableMessage(message);
                                                if (!canProcessDroppableMessage) {

                                                    checker.onDroppable(message);

                                                }

                                                return canProcessDroppableMessage;


                                            }).doAfterNext(new Consumer<ProtocolMessage>() {
                                                @Override
                                                public void accept(final ProtocolMessage message) throws Exception {

                                                    checker.onComplete(message);



                                                }
                                            }).doOnError(new Consumer<Throwable>() {
                        @Override
                        public void accept(final Throwable throwable) throws Exception {
                            checker.onError(throwable);
                        }
                    });

            }
        });
    }

    public void messageReceived(final ProtocolMessage msg) throws Exception {

        if (isSupsended.get() == Boolean.TRUE) {
            LOGGER.info("Suspended");
            return;
        }

        messagesCounter.getAndIncrement();
        messageHandler2.setCurrentDroppableMessageTime(msg);
        messagePublishSubject.onNext(msg);

    }

    public void startMessageCreator() {

        final Thread thread = new Thread(new MessageCreator(this), "MessageCounter");
        thread.start();
    }
}
