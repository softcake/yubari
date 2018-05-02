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
import io.reactivex.ObservableEmitter;
import io.reactivex.ObservableOnSubscribe;
import io.reactivex.Single;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author René Neubert
 */
public class SynchRequestProcessor {
    private final Map<Long, ObservableEmitter<ProtocolMessage>> syncRequests = new ConcurrentHashMap<>();
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

    public Observable<ProtocolMessage> createNewSynchRequest(final Channel channel, final ProtocolMessage message,
                                                    final long timeout,
                                                    final TimeUnit timeoutUnits) {

        final Long syncRequestId = this.getNextRequestId();
        message.setSynchRequestId(syncRequestId);
        final Single<Boolean> future = this.protocolHandler.writeMessage(channel,
                                                                         message);


        return Observable.create(new ObservableOnSubscribe<ProtocolMessage>() {
            @Override
            public void subscribe(final ObservableEmitter<ProtocolMessage> emitter) throws Exception {

                syncRequests.put(syncRequestId, emitter);
            }
        });
    }

    public boolean processRequest(final ProtocolMessage message) {

        final ObservableEmitter<ProtocolMessage> emitter = this.syncRequests.get(message.getSynchRequestId());



        final boolean result;
        if (emitter != null) {
            emitter.onNext(message);
            if (message instanceof RequestInProcessMessage) {
                result = true;


               // synchRequestFuture.setInProcessResponseLastTime(System.currentTimeMillis());
            } else {
                emitter.onNext(message);


                result = true; //!this.clientSession.isDuplicateSyncMessagesToClientListeners();
            }
        } else {
            result = false;
        }

        return result;

    }
}
