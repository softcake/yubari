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

import org.softcake.cherry.core.base.PreCheck;

import com.google.common.base.MoreObjects;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class DroppableMessageScheduling {
    private final AtomicLong lastScheduledTime = new AtomicLong(0L);
    private final AtomicInteger scheduledCount = new AtomicInteger(0);

    public DroppableMessageScheduling() {
    }

    public void scheduled(final long time) {

        this.lastScheduledTime.set(PreCheck.notNull(time,"time"));
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
        if (o == null || getClass() != o.getClass()) { return false; }

        final DroppableMessageScheduling that = (DroppableMessageScheduling) o;

        if (lastScheduledTime.get() != (that.lastScheduledTime.get())) { return false; }
        return scheduledCount.get() == that.scheduledCount.get();
    }

    @Override
    public int hashCode() {

        int result = lastScheduledTime.hashCode();
        result = 31 * result + scheduledCount.hashCode();
        return result;
    }

    @Override
    public String toString() {

        return MoreObjects.toStringHelper(this)
                          .add("lastScheduledTime", lastScheduledTime)
                          .add("scheduledCount",
                               scheduledCount)
                          .toString();
    }

}
