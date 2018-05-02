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

import org.softcake.yubari.netty.mina.TransportHelper;

import com.google.common.util.concurrent.ListeningExecutorService;
import io.reactivex.BackpressureOverflowStrategy;
import io.reactivex.Flowable;
import io.reactivex.Observable;
import io.reactivex.Observer;
import io.reactivex.disposables.Disposable;
import io.reactivex.processors.PublishProcessor;
import io.reactivex.schedulers.Schedulers;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * @author René Neubert
 */
public class RXJavaExample {

    public static final int DEFAULT_EVENT_POOL_SIZE = 3;//Math.max(Runtime.getRuntime().availableProcessors() / 2, 10);
    public static final long DEFAULT_EVENT_POOL_AUTO_CLEANUP_INTERVAL = 0L;
    public static final int DEFAULT_CRITICAL_EVENT_QUEUE_SIZE = 50;
    public static final long DEFAULT_EVENT_EXECUTION_WARNING_DELAY = 100L;
    public static final long DEFAULT_EVENT_EXECUTION_ERROR_DELAY = 1000L;
    private static final Logger LOGGER = LoggerFactory.getLogger(RXJavaExample.class);
    private static final List<Thread> eventExecutorThreadsForLogging = Collections.synchronizedList(new ArrayList<>());
    private static final int SHORT_MESSAGE_LENGTH = 100;
    private static final long LONGER_SLEEP_TIME = 50L;
    private static final long SHORTER_SLEEP_TIME = 5L;
    private static final long DEFAULT_SLEEP_TIME = 10L;
    private PublishProcessor<String> processor = PublishProcessor.create();

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

    private long sleepTime = DEFAULT_SLEEP_TIME;


    public static void main(final String[] args) {

        Observable.just("Hello").delay(2000L, TimeUnit.MILLISECONDS).subscribe(new Observer<String>() {
            @Override
            public void onSubscribe(final Disposable d) {
                LOGGER.info(d.toString());
            }

            @Override
            public void onNext(final String s) {
                LOGGER.info(s);
            }

            @Override
            public void onError(final Throwable e) {

            }

            @Override
            public void onComplete() {

            }
        });


        final RXJavaExample example = new RXJavaExample();

        final PublishProcessor<String> processor = example.getProcessor();

        final Flowable<String> filter = processor.onBackpressureBuffer(5, () -> LOGGER.info("Overflow"),
                                                                       BackpressureOverflowStrategy.DROP_OLDEST).filter(s -> true)
                                                 .map(s -> "Mapped " + s)

                                                 .onBackpressureDrop( s -> LOGGER.info("Dropped {}", s))
                                                 .observeOn(Schedulers.from(example.getExecutor()));


        filter.subscribe(getSubscriber("First", false));
        filter.subscribe(getSubscriber("Second", true));
        example.startMessageCreator();

    }

    private static Subscriber<String> getSubscriber(final String name, final boolean useSleep) {

        return new Subscriber<String>() {
            private Subscription subscription;

            @Override
            public void onSubscribe(final Subscription s) {

                this.subscription = s;
                this.subscription.request(1);
                LOGGER.info("Subscribed subscriber {}", name);
            }

            @Override
            public void onNext(final String s) {

                this.subscription.request(1);
                if (useSleep) {
                    final long nextLong = ThreadLocalRandom.current().nextLong(1000, 2000);
                    try {
                        Thread.sleep(nextLong);
                    } catch (final InterruptedException e) {
                        LOGGER.error("Error occurred in onNext for subscriber {}", name, e);
                    }
                }


                LOGGER.info("{}: {}", name, s);
            }

            @Override
            public void onError(final Throwable t) {

                LOGGER.error("Error occurred in onError for subscriber {}", name, t);
            }

            @Override
            public void onComplete() {

                LOGGER.info("Completed for Subscriber {}", name);
            }
        };
    }

    public PublishProcessor<String> getProcessor() {

        return processor;
    }

    public void startMessageCreator() {

        final Thread thread = new Thread(new MessageCreator(this), "MessageCounter");
        thread.start();
    }

    public void messageReceived(final String msg) throws Exception {

        processor.onNext(msg);

    }

}
