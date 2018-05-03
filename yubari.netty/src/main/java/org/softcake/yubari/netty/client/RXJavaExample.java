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
import io.reactivex.ObservableEmitter;
import io.reactivex.ObservableOnSubscribe;
import io.reactivex.ObservableSource;
import io.reactivex.Observer;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Action;
import io.reactivex.functions.Consumer;
import io.reactivex.functions.Function;
import io.reactivex.functions.Predicate;
import io.reactivex.internal.operators.observable.ObservableJust;
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
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

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
    private static final long DEFAULT_TIMEOUT_TIME = 1000L;

    private final ListeningExecutorService executor = TransportHelper.createExecutor(DEFAULT_EVENT_POOL_SIZE,
                                                                                     DEFAULT_EVENT_POOL_AUTO_CLEANUP_INTERVAL,
                                                                                     DEFAULT_CRITICAL_EVENT_QUEUE_SIZE,
                                                                                     "TransportClientEventExecutorThread",
                                                                                     eventExecutorThreadsForLogging,
                                                                                     "DDS2 Standalone Transport Client",
                                                                                     true);
    private PublishProcessor<String> processor = PublishProcessor.create();
    private long sleepTime = DEFAULT_SLEEP_TIME;

    public static void main(final String[] args) {


        final boolean doNotRestartTimerOnInProcessResponse = false;
        final Observable<String> stringObservable = Observable.create(new ObservableOnSubscribe<String>() {
            @Override
            public void subscribe(final ObservableEmitter<String> emitter) throws Exception {

                LOGGER.info("subscribing : ");


                AtomicInteger count = new AtomicInteger();

                AtomicBoolean isResponse = new AtomicBoolean(false);

                AtomicLong inProcessResponseLastTime = new AtomicLong(System.currentTimeMillis());
                Observable.interval(1001L, TimeUnit.MILLISECONDS).subscribe(new Consumer<Long>() {
                    @Override
                    public void accept(final Long aLong) throws Exception {

                        inProcessResponseLastTime.set(System.currentTimeMillis());
                    }
                });

                Observable.timer(2100, TimeUnit.MILLISECONDS).subscribe(new Consumer<Long>() {
                    @Override
                    public void accept(final Long aLong) throws Exception {

                        isResponse.set(true);
                        emitter.onNext("Message");
                        emitter.onComplete();
                    }
                });

                Observable.timer(DEFAULT_TIMEOUT_TIME, TimeUnit.MILLISECONDS)
                          .repeatWhen(new Function<Observable<Object>, ObservableSource<?>>() {
                              @Override
                              public ObservableSource<?> apply(final Observable<Object> objectObservable)
                                  throws Exception {

                                  AtomicLong scheduledTime = new AtomicLong(Long.MIN_VALUE);


                                  return objectObservable.takeWhile(new Predicate<Object>() {
                                      @Override
                                      public boolean test(final Object o) throws Exception {

                                          if (isResponse.get()) {

                                          } else if (doNotRestartTimerOnInProcessResponse) {
                                              emitter.onError(new TimeoutException("Timeout while waiting for "
                                                                                   + "response"));

                                          } else if (inProcessResponseLastTime.get() + DEFAULT_TIMEOUT_TIME
                                                     <= System.currentTimeMillis()) {
                                              emitter.onError(new TimeoutException(
                                                  "Timeout while waiting for response with retry timeout"));

                                          } else {


                                              scheduledTime.set(inProcessResponseLastTime.get() + DEFAULT_TIMEOUT_TIME
                                                                - System.currentTimeMillis());
                                              if (scheduledTime.get() > 0L) {
                                                  LOGGER.info("Retrying {}", scheduledTime.get());
                                                  return true;

                                              } else {
                                                  emitter.onError(new TimeoutException("Unexpecte timeout"));

                                              }
                                          }
                                          return false;
                                      }
                                  }).flatMap((Function<Object, ObservableSource<?>>) o -> Observable.timer(scheduledTime
                                                                                                               .get(),
                                                                                                           TimeUnit
                                                                                                               .MILLISECONDS));
                              }
                          })
                          .subscribe();


            }
        });
        final Observable<Long> empty = Observable.just("empty").timer(1000L, TimeUnit.MILLISECONDS);

        empty.subscribe(new Consumer<Long>() {
            @Override
            public void accept(final Long aLong) throws Exception {

                LOGGER.info(" Message send");
                stringObservable.subscribe(new Observer<String>() {
                    @Override
                    public void onSubscribe(final Disposable d) {

                        LOGGER.info("Subscribe");
                    }

                    @Override
                    public void onNext(final String s) {

                        LOGGER.info(s);
                    }

                    @Override
                    public void onError(final Throwable e) {

                        LOGGER.error("ERROR oocured {}", e.getMessage());
                    }

                    @Override
                    public void onComplete() {

                        LOGGER.info("Complete");
                    }
                });
            }
        });





        //.blockingSubscribe(System.out::println, System.out::println);

       /* Observable.timer(1, TimeUnit.SECONDS)
        .doOnSubscribe(s -> System.out.println("subscribing"))
          .map(new Function<Long, Object>() {
              @Override
              public Object apply(final Long v) throws Exception { throw new RuntimeException(); }
          }).doOnError(new Consumer<Throwable>() {
            @Override
            public void accept(final Throwable throwable) throws Exception {
                System.out.println("delay retry by " + throwable);
            }
        })
        .retryWhen(new Function<Observable<Throwable>, ObservableSource<?>>() {
            @Override
            public ObservableSource<?> apply(final Observable<Throwable> errors) throws Exception {

                AtomicInteger counter = new AtomicInteger();
                return errors.takeWhile(new Predicate<Throwable>() {
                    @Override
                    public boolean test(final Throwable e) throws Exception {

                        return counter.getAndIncrement() != 3;
                    }
                }).flatMap(new Function<Throwable, ObservableSource<? extends Long>>() {
                    @Override
                    public ObservableSource<? extends Long> apply(final Throwable e) throws Exception {

                        System.out.println("delay retry by " + counter.get() + " second(s)");
                        return Observable.timer(counter.get(), TimeUnit.SECONDS);
                    }
                });
            }
        })
        .blockingSubscribe(System.out::println, System.out::println);*/


        Observable.just("Hello").delay(100000, TimeUnit.MILLISECONDS).subscribe(new Observer<String>() {
            @Override
            public void onSubscribe(final Disposable d) {

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

       /* try {
            Thread.sleep(10000L);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }*/

        final RXJavaExample example = new RXJavaExample();

        final PublishProcessor<String> processor = example.getProcessor();

        final Flowable<String> filter = processor.onBackpressureBuffer(5, new Action() {
            @Override
            public void run() throws Exception {

                //  LOGGER.info("Overflow");
            }
        }, BackpressureOverflowStrategy.DROP_OLDEST).filter(s -> true).map(s -> "Mapped " + s)

                                                 .onBackpressureDrop(new Consumer<String>() {
                                                     @Override
                                                     public void accept(final String s) throws Exception {

                                                         //  LOGGER.info("Dropped {}", s);
                                                     }
                                                 }).observeOn(Schedulers.from(example.getExecutor()));


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
                //  LOGGER.info("Subscribed subscriber {}", name);
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


                //  LOGGER.info("{}: {}", name, s);
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
