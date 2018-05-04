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
    private final Map<Long, RequestHandler> syncRequests = new ConcurrentHashMap<>();
    private final TransportClientSession session;
    private final ClientProtocolHandler protocolHandler;
    private final ScheduledExecutorService scheduledExecutorService;
    private final AtomicLong requestId = new AtomicLong(0L);

    public SynchRequestProcessor(final TransportClientSession transportClientSession,
                                 final ClientProtocolHandler protocolHandler,
                                 final ScheduledExecutorService scheduledExecutorService) {

        session = transportClientSession;

        this.protocolHandler = protocolHandler;
        this.scheduledExecutorService = scheduledExecutorService;

    }

    private long getNextRequestId() {

        return this.requestId.incrementAndGet();
    }

    public Observable<ProtocolMessage> createNewSyncRequest(final Channel channel,
                                                            final ProtocolMessage message,
                                                            final long timeout,
                                                            final TimeUnit timeoutUnits,
                                                            final boolean doNotRestartTimerOnInProcessResponse) {

        return createNewSyncRequest(channel,
                                    message,
                                    timeout,
                                    timeoutUnits,
                                    doNotRestartTimerOnInProcessResponse,
                                    aBoolean -> {});

    }

    public Observable<ProtocolMessage> createNewSyncRequest(final Channel channel,
                                                            final ProtocolMessage message,
                                                            final long timeout,
                                                            final TimeUnit timeoutUnits,
                                                            final boolean doNotRestartTimerOnInProcessResponse,
                                                            final io.reactivex.functions.Consumer<Boolean> listener) {

        final Long syncRequestId = this.getNextRequestId();
        message.setSynchRequestId(syncRequestId);
        final Single<Boolean> booleanSingle = protocolHandler.writeMessage(channel, message);

        final RequestHandler handler = new RequestHandler(syncRequestId, scheduledExecutorService);
        final Observable<ProtocolMessage> request = handler.sendRequest(booleanSingle,
                                                                        doNotRestartTimerOnInProcessResponse,
                                                                        timeout,
                                                                        timeoutUnits,
                                                                        listener);
        return request.doOnSubscribe(disposable -> syncRequests.put(syncRequestId, handler));
    }

    public boolean processRequest(final ProtocolMessage message) {

        if (message.getSynchRequestId() == null) {
            return false;
        }

        final RequestHandler handler = this.syncRequests.get(message.getSynchRequestId());
        final boolean result;
        if (handler == null) {
            return false;
        }

        if (message instanceof RequestInProcessMessage) {

            handler.onRequestInProcess(System.currentTimeMillis());
            return true;
        } else {
            handler.onResponse(message);
            return !this.session.isDuplicateSyncMessagesToClientListeners();
        }

    }
}
