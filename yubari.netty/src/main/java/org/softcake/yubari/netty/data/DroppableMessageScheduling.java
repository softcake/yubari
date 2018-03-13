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

import com.google.common.base.Objects;

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
    public int hashCode() {

        return Objects.hashCode(lastScheduledTime, scheduledCount);
    }

    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        } else if (obj == null) {
            return false;
        } else if (this.getClass() != obj.getClass()) {
            return false;
        } else {
            DroppableMessageScheduling other = (DroppableMessageScheduling)obj;
            if (this.lastScheduledTime == null) {
                if (other.lastScheduledTime != null) {
                    return false;
                }
            } else if (!this.lastScheduledTime.equals(other.lastScheduledTime)) {
                return false;
            }

            if (this.scheduledCount == null) {
                if (other.scheduledCount != null) {
                    return false;
                }
            } else if (!this.scheduledCount.equals(other.scheduledCount)) {
                return false;
            }

            return true;
        }
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
