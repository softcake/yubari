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

package org.softcake.yubari.netty.channel;

import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;

import java.util.concurrent.atomic.AtomicLong;

public class AbstractChannelAttachment {
    private long lastReadIoTime = Long.MIN_VALUE;
    private long lastWriteIoTime = Long.MIN_VALUE;
    private final AtomicLong lastPingRequestTime = new AtomicLong(Long.MIN_VALUE);
    private final AtomicLong lastSubsequentFailedPingCount = new AtomicLong(0L);
    private final AtomicLong writtenMessagesCount = new AtomicLong(0L);
    private final ChannelFutureListener writeListener = new ChannelFutureListener() {
        @Override
        public void operationComplete(final ChannelFuture future) throws Exception {

            AbstractChannelAttachment.this.setLastWriteIoTime(System.currentTimeMillis());
            AbstractChannelAttachment.this.messageWritten();
        }
    };
    private final AtomicLong scheduledServiceMessagesCount = new AtomicLong(0L);
    private final AtomicLong scheduledBusinessMessagesCount = new AtomicLong(0L);
    private final AtomicLong preScheduledDroppableMessagesCount = new AtomicLong(0L);
    private final AtomicLong scheduledDroppableMessagesCount = new AtomicLong(0L);

    public AbstractChannelAttachment() {

    }

    public long getLastReadIoTime() {

        return this.lastReadIoTime;
    }

    public void setLastReadIoTime(long lastReadIoTime) {

        this.lastReadIoTime = lastReadIoTime;
    }

    public long getLastWriteIoTime() {

        return this.lastWriteIoTime;
    }

    public void setLastWriteIoTime(long lastWriteIoTime) {

        this.lastWriteIoTime = lastWriteIoTime;
    }

    public long getLastIoTime() {

        return Math.max(this.lastWriteIoTime, this.lastReadIoTime);
    }

    public ChannelFutureListener getWriteIoListener() {

        return this.writeListener;
    }

    public void messageWritten() {

        this.writtenMessagesCount.incrementAndGet();
    }

    public void businessMessageScheduled() {

        this.scheduledBusinessMessagesCount.incrementAndGet();
    }

    public void droppableMessagePreScheduled(long count) {

        this.preScheduledDroppableMessagesCount.addAndGet(count);
    }

    public void droppableMessageScheduled() {

        this.scheduledDroppableMessagesCount.incrementAndGet();
    }

    public void serviceMessageScheduled() {

        this.scheduledServiceMessagesCount.incrementAndGet();
    }

    public long removeWrittenMessagesCount() {

        return this.writtenMessagesCount.getAndSet(0L);
    }

    public long removeScheduledServiceMessagesCount() {

        return this.scheduledServiceMessagesCount.getAndSet(0L);
    }

    public long removePreScheduledDroppableMessagesCount() {

        return this.preScheduledDroppableMessagesCount.getAndSet(0L);
    }

    public long removeScheduledDroppableMessagesCount() {

        return this.scheduledDroppableMessagesCount.getAndSet(0L);
    }

    public long removeScheduledBusinessMessagesCount() {

        return this.scheduledBusinessMessagesCount.getAndSet(0L);
    }

    public long getLastPingRequestTime() {

        return this.lastPingRequestTime.get();
    }

    public void setLastPingRequestTime(long lastPingRequestTime) {

        this.lastPingRequestTime.set(lastPingRequestTime);
    }

    public boolean hasLastPingRequestTime() {

        return this.lastPingRequestTime.get() != Long.MIN_VALUE;
    }

    public void resetTimes() {

        this.setLastPingRequestTime(Long.MIN_VALUE);
        this.setLastReadIoTime(Long.MIN_VALUE);
        this.setLastWriteIoTime(Long.MIN_VALUE);
        this.lastSubsequentFailedPingCount.set(0L);
    }

    public void pingSuccessful() {

        this.lastSubsequentFailedPingCount.set(0L);
    }

    public long pingFailed() {

        return this.lastSubsequentFailedPingCount.incrementAndGet();
    }

    public long getLastSubsequentFailedPingCount() {

        return this.lastSubsequentFailedPingCount.get();
    }
}
