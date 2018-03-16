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

package org.softcake.yubari.netty;


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

public class RequestMessageTransportListenableFuture extends AbstractFuture<ProtocolMessage> implements
    RequestListenableFuture {
    private static final Logger LOGGER = LoggerFactory.getLogger(RequestMessageTransportListenableFuture.class);
    private final Object channelFutureLock = new Object();
    private String transportName;
    private Long syncRequestId;
    private Map<Long, RequestMessageTransportListenableFuture> syncRequests;
    private final ProtocolMessage requestMessage;
    private long inProcessResponseLastTime = -9223372036854775808L;
    private ChannelFuture channelFuture;
    private ScheduledFuture<?> timeoutScheduledFuture;

    public RequestMessageTransportListenableFuture(String transportName, Long syncRequestId, Map<Long, RequestMessageTransportListenableFuture> syncRequests, ProtocolMessage requestMessage) {
        this.transportName = transportName;
        this.syncRequestId = syncRequestId;
        this.syncRequests = syncRequests;
        this.requestMessage = requestMessage;
    }

    public boolean set(ProtocolMessage value) {
        boolean result = super.set(value);
        this.cleanup(false);
        return result;
    }

    public boolean setException(Throwable throwable) {
        boolean result = super.setException(throwable);
        this.cleanup(false);
        return result;
    }

    public boolean cancel(boolean mayInterruptIfRunning) {
        boolean result = super.cancel(mayInterruptIfRunning);
        this.cleanup(mayInterruptIfRunning);
        return result;
    }

    private void cleanup(boolean mayInterruptIfRunning) {
        Map<Long, RequestMessageTransportListenableFuture> localSyncRequests = this.syncRequests;
        Long syncRequestIdLocal = this.syncRequestId;
        if (localSyncRequests != null && syncRequestIdLocal != null) {
            localSyncRequests.remove(syncRequestIdLocal);
        }

        this.syncRequests = null;
        this.syncRequestId = null;
        if (this.timeoutScheduledFuture != null) {
            this.timeoutScheduledFuture.cancel(false);
        }

        Object var4 = this.channelFutureLock;
        synchronized(this.channelFutureLock) {
            if (this.channelFuture != null) {
                this.channelFuture.cancel(mayInterruptIfRunning);
                this.channelFuture = null;
            }

        }
    }

    public void timeout() {
        Object var1 = this.channelFutureLock;
        synchronized(this.channelFutureLock) {
            if (this.channelFuture != null) {
                this.channelFuture.cancel(true);
                this.channelFuture = null;
            }
        }

        this.setException(new TimeoutException("[" + this.transportName + "] Timeout while waiting for response"));
    }

    public void setInProcessResponseLastTime(long inProcessResponseLastTime) {
        this.inProcessResponseLastTime = inProcessResponseLastTime;
    }

    public void scheduleTimeoutCheck(SyncMessageTimeoutChecker timeoutChecker) {
        this.timeoutScheduledFuture = timeoutChecker.scheduleCheck();
    }

    void scheduleTimeoutCheck(SyncMessageTimeoutChecker timeoutChecker, long timeout, TimeUnit timeoutUnits) {
        this.timeoutScheduledFuture = timeoutChecker.scheduleCheck(timeout, timeoutUnits);
    }

    public void setChannelFuture(ChannelFuture cf) {
        if (cf == null) {
            throw new NullPointerException("Passed channel future is null");
        } else {
            Object var2 = this.channelFutureLock;
            synchronized(this.channelFutureLock) {
                this.channelFuture = cf;
                cf.addListener(new GenericFutureListener<ChannelFuture>() {
                    public void operationComplete(ChannelFuture future) throws Exception {
                        try {
                            synchronized(RequestMessageTransportListenableFuture.this.channelFutureLock) {
                                RequestMessageTransportListenableFuture.this.channelFuture = null;
                            }

                            if (!future.isSuccess()) {
                                if (future.isCancelled()) {
                                    if (!RequestMessageTransportListenableFuture.this.isCancelled()) {
                                        RequestMessageTransportListenableFuture.this.cancel(false);
                                    }
                                } else if (future.isDone() && future.cause() != null) {
                                    RequestMessageTransportListenableFuture.this.setException(future.cause());
                                } else {
                                    RequestMessageTransportListenableFuture.this.setException(new Exception("[" + RequestMessageTransportListenableFuture.this.transportName + "] Unexpected future state"));
                                }
                            }
                        } catch (Throwable var5) {
                            RequestMessageTransportListenableFuture.LOGGER.error(var5.getMessage(), var5);

                        }

                    }
                });
            }
        }
    }

    public long getInProcessResponseLastTime() {
        return this.inProcessResponseLastTime;
    }

    public ProtocolMessage getRequestMessage() {
        return this.requestMessage;
    }
}
