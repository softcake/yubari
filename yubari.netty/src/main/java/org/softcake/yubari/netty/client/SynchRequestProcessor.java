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
import io.reactivex.functions.Consumer;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author René Neubert
 */
public class SynchRequestProcessor {
    private final Map<Long,  ObservableEmitter<String>> syncRequests = new ConcurrentHashMap<>();

    public long getNextRequestId() {

       return this.requestId.incrementAndGet();
    }

    private AtomicLong requestId = new AtomicLong(0L);



     public Observable<String> createNewSynchRequest(final ProtocolMessage message, final long timeout, final TimeUnit timeoutUnits){
         final Long syncRequestId = this.getNextRequestId();
         message.setSynchRequestId(syncRequestId);

         return Observable.create(emitter -> syncRequests.put(syncRequestId, emitter));
    }

    public void processRequest(final ProtocolMessage message){

        final ObservableEmitter<String> emitter = this.syncRequests.get(message.getSynchRequestId());
        emitter.onNext("");


    }
}
