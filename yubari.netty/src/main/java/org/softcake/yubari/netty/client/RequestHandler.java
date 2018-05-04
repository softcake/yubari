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

import org.softcake.cherry.core.base.PreCheck;

import com.dukascopy.dds4.transport.msg.system.ProtocolMessage;
import io.reactivex.Observable;
import io.reactivex.ObservableEmitter;
import io.reactivex.ObservableSource;
import io.reactivex.Single;
import io.reactivex.functions.Consumer;
import io.reactivex.functions.Function;
import io.reactivex.schedulers.Schedulers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author René Neubert
 */
public class RequestHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(RequestHandler.class);
    private final Long syncRequestId;
    private final AtomicBoolean isResponse = new AtomicBoolean(false);
    private final ScheduledExecutorService scheduledExecutorService;
    private ObservableEmitter<ProtocolMessage> emitter;
    private ProtocolMessage responseMessage = null;
    private long inProcessResponseLastTime = Long.MIN_VALUE;

    RequestHandler(final Long syncRequestId, final ScheduledExecutorService scheduledExecutorService) {

        this.syncRequestId = PreCheck.parameterNotNull(syncRequestId, "syncRequestId");
        this.scheduledExecutorService = PreCheck.parameterNotNull(scheduledExecutorService, "scheduledExecutorService");
        PreCheck.expression(this.syncRequestId >= 0L, "syncRequestId must be >= 0L");
    }

    public Long getSyncRequestId() {

        return syncRequestId;
    }

    public ProtocolMessage getResponseMessage() {

        return responseMessage;
    }

    public Observable<ProtocolMessage> sendRequest(final Single<Boolean> requestMessage,
                                                   final boolean doNotRestartTimerOnInProcessResponse,
                                                   final long timeout,
                                                   final TimeUnit timeoutUnits) {

        return sendRequest(requestMessage, doNotRestartTimerOnInProcessResponse, timeout, timeoutUnits, aBoolean -> {});


    }

    public Observable<ProtocolMessage> sendRequest(final Single<Boolean> requestMessage,
                                                   final boolean doNotRestartTimerOnInProcessResponse,
                                                   final long timeout,
                                                   final TimeUnit timeoutUnits,
                                                   final Consumer<Boolean> requestSent) {

        PreCheck.parameterNotNull(requestMessage, "requestMessage");
        PreCheck.parameterNotNull(timeoutUnits, "timeoutUnits");
        return Observable.defer(() -> Observable.create(e -> {
            emitter = e;
            requestMessage.subscribe(new Consumer<Boolean>() {
                @Override
                public void accept(final Boolean aBoolean) throws Exception {

                    requestSent.accept(aBoolean);
                    RequestHandler.this.observeTimeout(Math.max(timeout, 0L),
                                                       timeoutUnits,
                                                       doNotRestartTimerOnInProcessResponse).subscribe();
                }
            });
        }));


    }

    private Observable<Long> observeTimeout(final long timeout,
                                            final TimeUnit timeoutUnits,
                                            final boolean doNotRestartTimerOnInProcessResponse) {

        return Observable.timer(timeout, timeoutUnits, Schedulers.from(scheduledExecutorService)).repeatWhen(
            objectObservable -> {

                final AtomicLong scheduledTime = new AtomicLong(Long.MIN_VALUE);


                return objectObservable.takeWhile(o -> {

                    if (isResponse.get()) {
                        return false;
                    } else if (doNotRestartTimerOnInProcessResponse) {
                        emitter.onError(new TimeoutException("Timeout while waiting for " + "response"));

                    } else if (inProcessResponseLastTime + timeout <= System.currentTimeMillis()) {
                        emitter.onError(new TimeoutException("Timeout while waiting for response with retry timeout"));

                    } else {


                        scheduledTime.set(inProcessResponseLastTime + timeout - System.currentTimeMillis());
                        if (scheduledTime.get() > 0L) {
                            LOGGER.info("Retrying {}", scheduledTime.get());
                            return true;

                        }

                        emitter.onError(new TimeoutException("Unexpecte timeout"));


                    }
                    return false;
                }).flatMap((Function<Object, ObservableSource<?>>) o -> Observable.timer(scheduledTime.get(),
                                                                                         TimeUnit.MILLISECONDS,
                                                                                         Schedulers.from(
                                                                                             scheduledExecutorService)));
            });
    }

    public void onResponse(final ProtocolMessage message) {

        PreCheck.parameterNotNull(message, "message");
        if (this.syncRequestId.equals(message.getSynchRequestId())) {
            isResponse.set(true);
            emitter.onNext(message);
            emitter.onComplete();
            this.responseMessage = message;
        } else {
            emitter.onError(new IllegalStateException(String.format("SynchRequestId is not valid: %s",
                                                                    message.getSynchRequestId())));
        }
    }

    void onRequestInProcess(final long currentTimeMillis) {

        this.inProcessResponseLastTime = currentTimeMillis;

    }
}