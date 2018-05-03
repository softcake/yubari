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

import com.dukascopy.dds4.transport.msg.system.ProtocolMessage;
import io.reactivex.Observable;
import io.reactivex.ObservableEmitter;
import io.reactivex.ObservableOnSubscribe;
import io.reactivex.ObservableSource;
import io.reactivex.Observer;
import io.reactivex.Single;
import io.reactivex.SingleObserver;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Consumer;
import io.reactivex.functions.Function;
import io.reactivex.functions.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author René Neubert
 */
public class RequestHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(RequestHandler.class);
    private final long syncRequestId;
    private ObservableEmitter<ProtocolMessage> emitter;
    private ProtocolMessage responseMessage = null;
    private AtomicBoolean isResponse = new AtomicBoolean(false);
    private long inProcessResponseLastTime = Long.MIN_VALUE;


    RequestHandler(final Long syncRequestId) {

        this.syncRequestId = syncRequestId;

    }


    public ProtocolMessage getResponseMessage() {

        return responseMessage;
    }

    Observable<ProtocolMessage> sendRequest(final Single<Boolean> requestMessage,
                                            final boolean doNotRestartTimerOnInProcessResponse,
                                            final long timeout,
                                            final TimeUnit timeoutUnits) {


        return Observable.create(new ObservableOnSubscribe<ProtocolMessage>() {
            @Override
            public void subscribe(final ObservableEmitter<ProtocolMessage> e) throws Exception {

                emitter = e;
                LOGGER.info("subscribing : ");

                Observable.timer(timeout, timeoutUnits)
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

                                          } else if (inProcessResponseLastTime + timeout
                                                     <= System.currentTimeMillis()) {
                                              emitter.onError(new TimeoutException(
                                                  "Timeout while waiting for response with retry timeout"));

                                          } else {


                                              scheduledTime.set(inProcessResponseLastTime + timeout
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
        }).doOnSubscribe(new Consumer<Disposable>() {
            @Override
            public void accept(final Disposable disposable) throws Exception {

                requestMessage.subscribe(new SingleObserver<Boolean>() {
                    @Override
                    public void onSubscribe(final Disposable d) {

                    }

                    @Override
                    public void onSuccess(final Boolean aBoolean) {

                        LOGGER.info("Message send");
                    }

                    @Override
                    public void onError(final Throwable e) {

                    }
                });
            }
        });


    }

    void onResponse(final ProtocolMessage message) {

        final Long synchRequestId = message.getSynchRequestId();
        if (synchRequestId != null && synchRequestId.equals(this.syncRequestId)) {
            isResponse.set(true);
            emitter.onNext(message);
            emitter.onComplete();
            this.responseMessage = message;
        } else {
            emitter.onError(new IllegalStateException(String.format("SynchRequestId is not valid: %s",
                                                                    synchRequestId)));
        }


    }

    void onRequestInProcess(final long currentTimeMillis) {

        this.inProcessResponseLastTime = currentTimeMillis;

    }
}
