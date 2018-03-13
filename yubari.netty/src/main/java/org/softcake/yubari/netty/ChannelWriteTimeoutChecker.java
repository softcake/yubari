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


import io.netty.channel.ChannelFuture;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ScheduledFuture;

class ChannelWriteTimeoutChecker implements Runnable, GenericFutureListener<Future<Object>> {
    private static final Logger LOGGER = LoggerFactory.getLogger(ChannelWriteTimeoutChecker.class);
    private final TransportClientSession transportSession;
    private final ChannelFuture channelFuture;
    private ScheduledFuture<?> scheduledFuture;

    public ChannelWriteTimeoutChecker(TransportClientSession transportSession, ChannelFuture channelFuture) {
        this.transportSession = transportSession;
        this.channelFuture = channelFuture;
    }

    void setScheduledFuture(ScheduledFuture<?> scheduledFuture) {
        this.scheduledFuture = scheduledFuture;
    }

    public void operationComplete(Future<Object> future) throws Exception {
        try {
            this.scheduledFuture.cancel(false);
        } catch (Throwable var3) {
            LOGGER.error(var3.getMessage(), var3);

        }

    }

    public void run() {
        try {
            if (!this.channelFuture.isDone()) {
                LOGGER.warn("[{}] Failed to send message in timeout time, disconnecting", this.transportSession.getTransportName());
                this.transportSession.terminate();
            }
        } catch (Throwable var2) {
            LOGGER.error(var2.getMessage(), var2);

        }

    }
}
