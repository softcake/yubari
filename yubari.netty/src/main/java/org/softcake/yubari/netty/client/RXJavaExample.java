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
import org.softcake.yubari.netty.util.StrUtils;

import com.google.common.util.concurrent.ListeningExecutorService;
import io.reactivex.BackpressureOverflowStrategy;
import io.reactivex.BackpressureStrategy;
import io.reactivex.Flowable;
import io.reactivex.Observable;
import io.reactivex.ObservableEmitter;
import io.reactivex.ObservableOnSubscribe;
import io.reactivex.Observer;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Action;
import io.reactivex.functions.Consumer;
import io.reactivex.functions.Function;
import io.reactivex.functions.Predicate;
import io.reactivex.processors.PublishProcessor;
import io.reactivex.processors.ReplayProcessor;
import io.reactivex.schedulers.Schedulers;
import io.reactivex.subjects.ReplaySubject;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
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
    PublishProcessor<String> processor = PublishProcessor.create();
   // Flowable<String> stringFlowable = Flowable.fromPublisher(processor);
    final ListeningExecutorService executor = TransportHelper.createExecutor(DEFAULT_EVENT_POOL_SIZE,
                                                                             DEFAULT_EVENT_POOL_AUTO_CLEANUP_INTERVAL,
                                                                             DEFAULT_CRITICAL_EVENT_QUEUE_SIZE,
                                                                             "TransportClientEventExecutorThread",
                                                                             eventExecutorThreadsForLogging,
                                                                             "DDS2 Standalone Transport Client",
                                                                             true);
    final ListeningExecutorService executor2 = TransportHelper.createExecutor(DEFAULT_EVENT_POOL_SIZE,
                                                                              DEFAULT_EVENT_POOL_AUTO_CLEANUP_INTERVAL,
                                                                              DEFAULT_CRITICAL_EVENT_QUEUE_SIZE,
                                                                              "EventExecutorThread",
                                                                              eventExecutorThreadsForLogging,
                                                                              " Transport Client",
                                                                              true);
    ScheduledExecutorService service = new ScheduledThreadPoolExecutor(1);
    private long sleepTime = DEFAULT_SLEEP_TIME;
    private Runnable runna;



    public static void main(String[] args) {

        RXJavaExample example = new RXJavaExample();

        try {
            Thread.sleep(10000L);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        final PublishProcessor<String> processor = example.getProcessor();
        final Flowable<String> filter = processor.filter(new Predicate<String>() {
            @Override
            public boolean test(final String s) throws Exception {


                LOGGER.info("Filter {}", s);
                return true;
            }
        }).onBackpressureBuffer(10, new Action() {
            @Override
            public void run() throws Exception {
                LOGGER.info("Overflow");
            }
        },BackpressureOverflowStrategy.DROP_OLDEST).onBackpressureDrop(new Consumer<String>() {
            @Override
            public void accept( String s) throws Exception {
               s = "-------------------------------------";

                LOGGER.info("Filter {}", s);
            }
        }).map(new Function<String, String>() {
            @Override
            public String apply(final String s) throws Exception {

                return "Mapped " + s;
            }
        });


        filter.subscribe(getSusbsriber());
        filter.subscribe(getSlowSusbsriber());
        example.startMessageCreator();

    }

    private static Subscriber<String> getSusbsriber() {

        return new Subscriber<String>() {
            @Override
            public void onSubscribe(final Subscription s) {

                s.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(final String s) {

                LOGGER.info(" Message replay: {}", s);
            }

            @Override
            public void onError(final Throwable t) {

            }

            @Override
            public void onComplete() {

            }
        };
    }

    private static Subscriber<String> getSlowSusbsriber() {

        return new Subscriber<String>() {
            @Override
            public void onSubscribe(final Subscription s) {

                s.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(final String s) {

                try {
                    Thread.sleep(10000L);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                LOGGER.info(" Message replay slow: {}", s);
            }

            @Override
            public void onError(final Throwable t) {

                LOGGER.error("Error..." ,t);
            }

            @Override
            public void onComplete() {
                LOGGER.error("Complete");
            }
        };
    }

    private static Observer<String> getFirstObserver(final ReplaySubject<String> processor) {


        return new Observer<String>() {

            @Override
            public void onSubscribe(Disposable d) {

                System.out.println(" First onSubscribe : " + d.isDisposed());
            }

            @Override
            public void onNext(String value) {

                LOGGER.info(" First: {}", value);


            }

            @Override
            public void onError(Throwable e) {

                System.out.println(e);
            }

            @Override
            public void onComplete() {

                System.out.println("Complete");
            }
        };
    }

    private static Observer<String> getSecondObserver() {

        return new Observer<String>() {

            @Override
            public void onSubscribe(Disposable d) {

                System.out.println(" Second onSubscribe : " + d.isDisposed());
            }

            @Override
            public void onNext(String value) {

                try {
                    Thread.sleep(10000L);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                LOGGER.info(" Second: {}", value);
            }

            @Override
            public void onError(Throwable e) {

                System.out.println(e);
            }

            @Override
            public void onComplete() {

                System.out.println("Complete");
            }
        };
    }

    public PublishProcessor<String> getProcessor() {

        return processor;
    }

    public void startMessageCreator() {

        Thread thread = new Thread(new MessageCreator(this), "MessageCounter");
        thread.start();
    }

    public void messageReceived(String msg) throws Exception {


        LOGGER.info(msg);


      /*  Observable<String> observable = Observable.create(new ObservableOnSubscribe<String>() {
            @Override
            public void subscribe(final ObservableEmitter<String> subscriber) throws Exception {

                subscriber.onNext(msg);
                //                subscriber.onNext("Beta");
                //                subscriber.onComplete();
            }
        });

        observable.subscribe(new Consumer<String>() {
            @Override
            public void accept(final String s) throws Exception {

                LOGGER.info("1 -" + s);
                //  processor.onNext(s);
            }
        }, new Consumer<Throwable>() {
            @Override
            public void accept(final Throwable throwable) throws Exception {

            }
        }, new Action() {
            @Override
            public void run() throws Exception {

            }
        });*/


        Observable<String> observable = Observable.create(new ObservableOnSubscribe<String>() {
            @Override
            public void subscribe(final ObservableEmitter<String> emitter) throws Exception {
emitter.onNext(msg);
            }
        });

        final Observable<String> observable1 = observable.subscribeOn(Schedulers.from(executor2));

        observable1.subscribe(new Consumer<String>() {
                      @Override
                      public void accept(final String s) throws Exception {

                          LOGGER.info("1 -" + s);
                         processor.onNext(s);


                      }
                  }, new Consumer<Throwable>() {
                      @Override
                      public void accept(final Throwable throwable) throws Exception {

                          LOGGER.error("Error................ {}", throwable.getMessage());
                      }
                  }, new Action() {
                      @Override
                      public void run() throws Exception {

                          LOGGER.debug("_Completed");

                      }
                  });


    }

    public Runnable getDelaedExeutionTas(Runnable task, String message) {

        final long[] procStartTime = {System.currentTimeMillis()};

        this.runna = new Runnable() {
            @Override
            public void run() {

                try {

                    executor.execute(task);


                } catch (final RejectedExecutionException ex) {
                    final long currentTime = System.currentTimeMillis();
                    final long executionTime = currentTime - procStartTime[0];

                    if (executionTime > DEFAULT_EVENT_EXECUTION_ERROR_DELAY) {
                        LOGGER.error("Event executor queue overloaded, CRITICAL EXECUTION WAIT TIME: {}ms, possible "
                                     + "application problem or deadLock, message [{}]",

                                     executionTime, StrUtils.toSafeString(message, SHORT_MESSAGE_LENGTH));


                        procStartTime[0] = currentTime;
                        sleepTime = LONGER_SLEEP_TIME;
                    }


                    service.schedule(runna, SHORTER_SLEEP_TIME, TimeUnit.MILLISECONDS);
                }

            }
        };
        service.schedule(runna, SHORTER_SLEEP_TIME, TimeUnit.MILLISECONDS);
        return runna;
    }
}
