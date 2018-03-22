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

package org.softcake.yubari.netty.data;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class DroppableMessageScheduling {
    private final AtomicLong lastScheduledTime = new AtomicLong(0L);
    private final AtomicInteger scheduledCount = new AtomicInteger(0);

    public DroppableMessageScheduling() {
    }

    public void scheduled(long time) {
        this.lastScheduledTime.set(time);
        this.scheduledCount.incrementAndGet();
    }

    public void executed() {
        this.scheduledCount.decrementAndGet();
    }

    public long getLastScheduledTime() {
        return this.lastScheduledTime.get();
    }

    public int getScheduledCount() {
        return this.scheduledCount.get();
    }

    @Override
    public boolean equals(final Object o) {

        if (this == o) { return true; }
        if (!(o instanceof DroppableMessageScheduling)) { return false; }

        final DroppableMessageScheduling that = (DroppableMessageScheduling) o;

        if (lastScheduledTime != null
            ? !lastScheduledTime.equals(that.lastScheduledTime)
            : that.lastScheduledTime != null) { return false; }
        return scheduledCount != null ? scheduledCount.equals(that.scheduledCount) : that.scheduledCount == null;
    }

    @Override
    public int hashCode() {

        int result = lastScheduledTime != null ? lastScheduledTime.hashCode() : 0;
        result = 31 * result + (scheduledCount != null ? scheduledCount.hashCode() : 0);
        return result;
    }

    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("DroppableMessageInfo [");
        if (this.lastScheduledTime != null) {
            builder.append("lastScheduledTime=");
            builder.append(this.lastScheduledTime);
            builder.append(", ");
        }

        if (this.scheduledCount != null) {
            builder.append("scheduedCount=");
            builder.append(this.scheduledCount);
        }

        builder.append("]");
        return builder.toString();
    }
}
