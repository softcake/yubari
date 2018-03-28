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


import org.softcake.yubari.netty.client.tasks.SyncMessageTimeoutChecker;
import org.softcake.yubari.netty.mina.RequestListenableFuture;

import com.dukascopy.dds4.transport.msg.system.ProtocolMessage;
import com.google.common.util.concurrent.AbstractFuture;
import io.netty.channel.ChannelFuture;
import io.netty.util.concurrent.GenericFutureListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class RequestMessageListenableFuture extends AbstractFuture<ProtocolMessage>
    implements RequestListenableFuture {
    private static final Logger LOGGER = LoggerFactory.getLogger(RequestMessageListenableFuture.class);
    private final Object channelFutureLock = new Object();
    private final ProtocolMessage requestMessage;
    private final String transportName;
    private final TransportClient transportClientSession;
    private Long syncRequestId;
    private Map<Long, RequestMessageListenableFuture> syncRequests;
    private long inProcessResponseLastTime = Long.MIN_VALUE;
    private ChannelFuture channelFuture;
    private ScheduledFuture<?> timeoutScheduledFuture;

    public RequestMessageListenableFuture(final String transportName,
                                          final Long syncRequestId,
                                          final Map<Long, RequestMessageListenableFuture>
                                                       syncRequests,
                                          final ProtocolMessage requestMessage) {
        transportClientSession = null;
        this.transportName = transportName;
        this.syncRequestId = syncRequestId;
        this.syncRequests = syncRequests;
        this.requestMessage = requestMessage;
    }
    public RequestMessageListenableFuture(final TransportClient transportClientSession,
                                          final Map<Long, RequestMessageListenableFuture>
                                                       syncRequests,
                                          final ProtocolMessage requestMessage) {
        this.transportName = transportClientSession.getTransportName();
        this.transportClientSession = transportClientSession;
        this.syncRequestId = transportClientSession.getNextId();
        this.syncRequests = syncRequests;
        this.syncRequests.put(syncRequestId, this);
        this.requestMessage = requestMessage;
    }
    public boolean set(final ProtocolMessage value) {

        final boolean result = super.set(value);
        this.cleanup(Boolean.FALSE);
        return result;
    }

    public boolean setException(final Throwable throwable) {

        final boolean result = super.setException(throwable);
        this.cleanup(Boolean.FALSE);
        return result;
    }

    public boolean cancel(final boolean mayInterruptIfRunning) {

        final boolean result = super.cancel(mayInterruptIfRunning);
        this.cleanup(mayInterruptIfRunning);
        return result;
    }

    private void cleanup(final boolean mayInterruptIfRunning) {

        final Map<Long, RequestMessageListenableFuture> localSyncRequests = this.syncRequests;
        final Long syncRequestIdLocal = this.syncRequestId;
        if (localSyncRequests != null && syncRequestIdLocal != null) {
            localSyncRequests.remove(syncRequestIdLocal);
        }

        this.syncRequests = null;
        this.syncRequestId = null;
        if (this.timeoutScheduledFuture != null) {
            this.timeoutScheduledFuture.cancel(false);
        }

        synchronized (this.channelFutureLock) {
            if (this.channelFuture != null) {
                this.channelFuture.cancel(mayInterruptIfRunning);
                this.channelFuture = null;
            }

        }
    }

    public void timeout() {

        synchronized (this.channelFutureLock) {
            if (this.channelFuture != null) {
                this.channelFuture.cancel(true);
                this.channelFuture = null;
            }
        }

        this.setException(new TimeoutException(String.format("[%s] Timeout while waiting for response",
                                                             this.transportName)));
    }

    public void scheduleTimeoutCheck(final SyncMessageTimeoutChecker timeoutChecker) {

        this.timeoutScheduledFuture = timeoutChecker.scheduleCheck();
    }

    public void scheduleTimeoutCheck(final SyncMessageTimeoutChecker timeoutChecker,
                              final long timeout,
                              final TimeUnit timeoutUnits) {

        this.timeoutScheduledFuture = timeoutChecker.scheduleCheck(timeout, timeoutUnits);
    }

    public void setChannelFuture(final ChannelFuture cf) {

        if (cf == null) {
            throw new NullPointerException("Passed channel future is null");
        }

            synchronized (this.channelFutureLock) {
                this.channelFuture = cf;
                cf.addListener((GenericFutureListener<ChannelFuture>) future -> {

                        synchronized (channelFutureLock) {
                            channelFuture = null;
                        }

                        if (!future.isSuccess()) {
                            if (future.isCancelled()) {
                                if (!isCancelled()) {
                                    cancel(Boolean.FALSE);
                                }
                            } else if (future.isDone() && future.cause() != null) {
                                setException(future.cause());
                            } else {
                                setException(new Exception(String.format("[%s] Unexpected future state",
                                                                         transportName)));
                            }
                        }


                });
            }

    }

    public long getInProcessResponseLastTime() {

        return this.inProcessResponseLastTime;
    }

    public void setInProcessResponseLastTime(final long inProcessResponseLastTime) {

        this.inProcessResponseLastTime = inProcessResponseLastTime;
    }

    public ProtocolMessage getRequestMessage() {

        return this.requestMessage;
    }
}
