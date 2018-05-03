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
import com.dukascopy.dds4.transport.msg.system.RequestInProcessMessage;
import io.netty.channel.Channel;
import io.reactivex.Observable;
import io.reactivex.Observer;
import io.reactivex.Single;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Consumer;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author René Neubert
 */
public class SynchRequestProcessor {
    private final Map<Long, RequestHandler> syncRequests = new ConcurrentHashMap<>();
    private final ClientProtocolHandler protocolHandler;
    private ScheduledExecutorService scheduledExecutorService;
    private AtomicLong requestId = new AtomicLong(0L);

    public SynchRequestProcessor(final ClientProtocolHandler protocolHandler,
                                 final ScheduledExecutorService scheduledExecutorService) {

        this.protocolHandler = protocolHandler;
        this.scheduledExecutorService = scheduledExecutorService;
    }

    public long getNextRequestId() {

        return this.requestId.incrementAndGet();
    }

    public Observable<ProtocolMessage> createNewSynchRequest(final Channel channel,
                                                             final ProtocolMessage message,
                                                             final long timeout,
                                                             final TimeUnit timeoutUnits) {

        final Long syncRequestId = this.getNextRequestId();
        message.setSynchRequestId(syncRequestId);
        final Single<Boolean> booleanSingle = protocolHandler.writeMessage(channel, message);

        RequestHandler handler = new RequestHandler(syncRequestId);
        final Observable<ProtocolMessage> messageObservable = handler.sendRequest(booleanSingle,
                                                                                  false,
                                                                                  timeout,
                                                                                  timeoutUnits);
        messageObservable.doOnSubscribe(new Consumer<Disposable>() {
            @Override
            public void accept(final Disposable disposable) throws Exception {

                syncRequests.put(syncRequestId, handler);
            }
        });



        return messageObservable;
    }

    public boolean processRequest(final ProtocolMessage message) {

        final RequestHandler handler = this.syncRequests.get(message.getSynchRequestId());


        final boolean result;
        if (handler != null) {
            handler.onResponse(message);
            if (message instanceof RequestInProcessMessage) {
                result = true;

                handler.onRequestInProcess(System.currentTimeMillis());
                // synchRequestFuture.setInProcessResponseLastTime(System.currentTimeMillis());
            } else {
                handler.onResponse(message);


                result = true; //!this.clientSession.isDuplicateSyncMessagesToClientListeners();
            }
        } else {
            result = false;
        }

        return result;

    }
}
