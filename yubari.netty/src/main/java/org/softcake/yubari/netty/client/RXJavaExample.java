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

import com.google.common.collect.Lists;
import com.google.common.util.concurrent.ListeningExecutorService;
import io.reactivex.BackpressureOverflowStrategy;
import io.reactivex.Flowable;
import io.reactivex.Observable;
import io.reactivex.ObservableEmitter;
import io.reactivex.ObservableOnSubscribe;
import io.reactivex.ObservableSource;
import io.reactivex.Observer;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Action;
import io.reactivex.functions.BiFunction;
import io.reactivex.functions.Consumer;
import io.reactivex.functions.Function;
import io.reactivex.functions.Predicate;
import io.reactivex.internal.operators.observable.ObservableJust;
import io.reactivex.processors.PublishProcessor;
import io.reactivex.schedulers.Schedulers;
import io.reactivex.schedulers.TestScheduler;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author René Neubert
 */
@SuppressWarnings("unchecked")
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
    private static final long DEFAULT_TIMEOUT_TIME = 1000L;

    private final ListeningExecutorService executor = TransportHelper.createExecutor(DEFAULT_EVENT_POOL_SIZE,
                                                                                     DEFAULT_EVENT_POOL_AUTO_CLEANUP_INTERVAL,
                                                                                     DEFAULT_CRITICAL_EVENT_QUEUE_SIZE,
                                                                                     "TransportClientEventExecutorThread",
                                                                                     eventExecutorThreadsForLogging,
                                                                                     "DDS2 Standalone Transport Client",
                                                                                     true);
    private PublishProcessor<Message> processor = PublishProcessor.create();
    private long sleepTime = DEFAULT_SLEEP_TIME;

    public static void main(final String[] args) {

        final List<String> items = Lists.newArrayList("a", "b", "c", "d", "e", "f");

        final TestScheduler scheduler = new TestScheduler();

        Observable.fromIterable(items)
                  .flatMap(new Function<String, ObservableSource<? extends String>>() {
                      @Override
                      public ObservableSource<? extends String> apply(final String s) throws Exception {

                          final int delay = new Random().nextInt(10);
                          return Observable.just(s + "x").delay(delay, TimeUnit.SECONDS, scheduler);
                      }
                  })
                  .toList()
                  .doOnSuccess(System.out::println)
                  .subscribe();

        scheduler.advanceTimeBy(1, TimeUnit.MINUTES);


        List<String> words = Arrays.asList(
            "the",
            "quick",
            "brown",
            "fox",
            "jumped",
            "over",
            "the",
            "lazy",
            "dog"
        );

        Observable.just(words)
                  .subscribe(word->System.out.println(word));
        Observable.<List<String>>fromArray(words)
                  .zipWith(Observable.range(1, Integer.MAX_VALUE), new BiFunction<List<String>, Integer, String>() {
                      @Override
                      public String apply(final List<String> string, final Integer count) throws Exception {

                          return String.format("%2d. %s", count, string);
                      }
                  })
                  .subscribe(System.out::println);

        Observable.fromIterable(words)
                  .flatMap(new Function<String, ObservableSource<? extends String>>() {
                      @Override
                      public ObservableSource<? extends String> apply(final String word) throws Exception {

                          return Observable.fromArray(word.split(""));
                      }
                  })
                  .zipWith(Observable.range(1, Integer.MAX_VALUE),
                           (string, count) -> String.format("%2d. %s", count, string))
                  .subscribe(System.out::println);
        final RXJavaExample example = new RXJavaExample();

        final PublishProcessor<Message> processor = example.getProcessor();

        final Flowable<Message> filter = processor.onBackpressureBuffer(5, new Action() {
            @Override
            public void run() throws Exception {

                //  LOGGER.info("Overflow");
            }
        }, BackpressureOverflowStrategy.DROP_OLDEST).onBackpressureDrop(new Consumer<Message>() {
                                                     @Override
                                                     public void accept(final Message s) throws Exception {

                                                           LOGGER.info("Dropped {}", s.getMessage());
                                                     }
                                                 }).observeOn(Schedulers.from(example.getExecutor()));


        filter.toObservable().buffer(10,2).flatMap(new Function<List<Message>, ObservableSource<Message>>() {
            @Override
            public ObservableSource<Message> apply(final List<Message> messages) throws Exception {

                return Observable.fromIterable(messages);
            }
        }).subscribe(getObserver("Second", true));
        filter.subscribe(getSubscriber("First", false));
        example.startMessageCreator();
    }

    private static Subscriber<Message> getSubscriber(final String name, final boolean useSleep) {

        return new Subscriber<Message>() {
            private Subscription subscription;

            @Override
            public void onSubscribe(final Subscription s) {

                this.subscription = s;
                this.subscription.request(1);
                //  LOGGER.info("Subscribed subscriber {}", name);
            }

            @Override
            public void onNext(final Message s) {

                this.subscription.request(1);
                if (useSleep) {
                    final long nextLong = ThreadLocalRandom.current().nextLong(3000, 10000);
                    try {
                        Thread.sleep(nextLong);
                    } catch (final InterruptedException e) {
                        LOGGER.error("Error occurred in onNext for subscriber {}", name, e);
                    }
                }


                  LOGGER.info("{}: {}", name, s.getMessage());
            }

            @Override
            public void onError(final Throwable t) {

                // LOGGER.error("Error occurred in onError for subscriber {}", name, t);
            }

            @Override
            public void onComplete() {

                LOGGER.info("Completed for Subscriber {}", name);
            }
        };
    }
    private static Observer<Message> getObserver(final String name, final boolean useSleep) {

        return new Observer<Message>() {




            @Override
            public void onSubscribe(final Disposable d) {

            }

            @Override
            public void onNext(final Message s) {


                if (useSleep) {
                    final long nextLong = ThreadLocalRandom.current().nextLong(3000, 10000);
                    try {
                        Thread.sleep(nextLong);
                    } catch (final InterruptedException e) {
                        LOGGER.error("Error occurred in onNext for subscriber {}", name, e);
                    }
                }


                LOGGER.info("{}: {}", name, s.getMessage());
            }

            @Override
            public void onError(final Throwable t) {

                // LOGGER.error("Error occurred in onError for subscriber {}", name, t);
            }

            @Override
            public void onComplete() {

                LOGGER.info("Completed for Subscriber {}", name);
            }
        };
    }

    public ListeningExecutorService getExecutor() {

        return executor;
    }

    public PublishProcessor<Message> getProcessor() {

        return processor;
    }

    public void startMessageCreator() {

      /*  final Thread thread = new Thread(new MessageCreator(this), "MessageCounter");
        thread.start();*/
    }

    public void messageReceived(final Message msg) throws Exception {

        final Map<String,Long> dropdmap = new HashMap<>();



        processor.onNext(msg);

    }

}
