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

package org.softcake.yubari.netty.client.tasks;

import org.softcake.yubari.netty.client.TransportClientSession;

import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;

import java.util.concurrent.atomic.AtomicLong;

/**
 * @author The softcake authors
 */
public abstract class AbstractSendingCheck implements Runnable {
    private static final long ERROR_TIME_OFFSET = 1_000_000_000L;

    final long procStartTime;
    final TransportClientSession clientSession;
    final ChannelFuture channelFuture;


    AbstractSendingCheck(final ChannelFuture channelFuture, final TransportClientSession clientSession) {

        this.channelFuture = channelFuture;
        this.clientSession = clientSession;
        this.procStartTime = System.currentTimeMillis();
    }

    static boolean isTimeOutReached(final AtomicLong lastSendingTime) {

        final long nanoTime = System.nanoTime();
        final long lastSendingTimeLocal = lastSendingTime.get();
        return lastSendingTimeLocal + ERROR_TIME_OFFSET < nanoTime && lastSendingTime.compareAndSet(
            lastSendingTimeLocal,
            nanoTime);
    }

    @Override
    public void run() {

        process();

    }

    abstract void process();

    abstract void logMessage(final long executionTime);

    abstract ChannelFutureListener getMessageListener(final long executionTime);
    abstract void schedule();
}
