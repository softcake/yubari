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

import org.softcake.yubari.netty.mina.TransportHelper;

import com.google.common.util.concurrent.ListeningExecutorService;
import io.reactivex.Observable;
import io.reactivex.ObservableSource;
import io.reactivex.functions.Consumer;
import io.reactivex.functions.Function;
import io.reactivex.functions.Predicate;
import io.reactivex.schedulers.Schedulers;
import io.reactivex.subjects.PublishSubject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

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

    public ListeningExecutorService getExecutor() {

        return executor;
    }

    private final ListeningExecutorService executor = TransportHelper.createExecutor(DEFAULT_EVENT_POOL_SIZE,
                                                                                     DEFAULT_EVENT_POOL_AUTO_CLEANUP_INTERVAL,
                                                                                     DEFAULT_CRITICAL_EVENT_QUEUE_SIZE,
                                                                                     "TransportClientEventExecutorThread",
                                                                                     eventExecutorThreadsForLogging,
                                                                                     "DDS2 Standalone Transport Client",
                                                                                     true);
    private PublishSubject<Message> messagePublishSubject = PublishSubject.create();

    public static void main(String[] args) {

        RxPublishExample example = new RxPublishExample();
        AtomicLong aLong = new AtomicLong(Long.MIN_VALUE);
        example.getMessagePublishSubject().filter(new Predicate<Message>() {
            @Override
            public boolean test(final Message message) throws Exception {


                LOGGER.info("Message in predicate: {}",message.getMessage());
                return message.getName().equals("A");
            }
        }).doOnNext(new Consumer<Message>() {
            @Override
            public void accept(final Message message) throws Exception {

               aLong.set(message.getCreationTime());
            }
        }).observeOn(Schedulers.from(example.getExecutor())).filter(new Predicate<Message>() {
            @Override
            public boolean test(final Message message) throws Exception {

                final long creationTime = aLong.get();
                if (creationTime == message.getCreationTime()) {
                    return true;
                } else {

                    return false;
                }

            }
        }).doAfterNext(new Consumer<Message>() {
            @Override
            public void accept(final Message message) throws Exception {
                long executionTime =  System.currentTimeMillis() - message.getCreationTime();
                LOGGER.warn("Execution takes too long: {}ms {}",executionTime,message.getMessage());
            }
        }).subscribe(new Consumer<Message>() {
            @Override
            public void accept(final Message message) throws Exception {
                LOGGER.info("Message in first Consumer: {}", message.getMessage());

                final long nextLong = ThreadLocalRandom.current().nextLong(100, 500);
                try {
                    Thread.sleep(nextLong);
                } catch (final InterruptedException e) {
                    LOGGER.error("Error occurred in onNext for subscriber {}", "first", e);
                }

            }
        });

      /*  example.getMessagePublishSubject().subscribeOn(Schedulers.from(example.getExecutor())).subscribe(new Consumer<Message>() {
            @Override
            public void accept(final Message message) throws Exception {

                LOGGER.info("Message in second Consumer: {}", message.getMessage());
            }
        });*/

        example.startMessageCreator();

    }


    public Observable<Message> getMessagePublishSubject() {

        return  messagePublishSubject;

    }

    public void messageReceived(final Message msg) throws Exception {

        messagePublishSubject.onNext(msg);

    }

    public void startMessageCreator() {

        final Thread thread = new Thread(new MessageCreator(this), "MessageCounter");
        thread.start();
    }
}
