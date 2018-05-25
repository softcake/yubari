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

package org.softcake.yubari.netty.pinger;

import com.google.common.base.Objects;

public class StatsStruct {
    private double first;
    private double last;
    private double average;
    private double min;
    private double max;
    private long count;

    public StatsStruct() {

    }

    public StatsStruct(final StatsStruct struct) {

        this.first = struct.first;
        this.last = struct.last;
        this.average = struct.average;
        this.min = struct.min;
        this.max = struct.max;
        this.count = struct.count;
    }

    public double getFirst() {

        return this.first;
    }

    public void setFirst(final double first) {

        this.first = first;
    }

    public long getRoundedFirst() {

        return (long) (this.first + 0.5D);
    }

    public double getLast() {

        return this.last;
    }

    public void setLast(final double last) {

        this.last = last;
    }

    public long getRoundedLast() {

        return (long) (this.last + 0.5D);
    }

    public double getAverage() {

        return this.average;
    }

    public void setAverage(final double average) {

        this.average = average;
    }

    public long getRoundedAverage() {

        return (long) (this.average + 0.5D);
    }

    public double getMin() {

        return this.min;
    }

    public void setMin(final double min) {

        this.min = min;
    }

    public long getRoundedMin() {

        return (long) (this.min + 0.5D);
    }

    public double getMax() {

        return this.max;
    }

    public void setMax(final double max) {

        this.max = max;
    }

    public long getRoundedMax() {

        return (long) (this.max + 0.5D);
    }

    public long getCount() {

        return this.count;
    }

    public void setCount(final long count) {

        this.count = count;
    }

    public void incCount() {

        ++this.count;
    }

    @Override
    public boolean equals(final Object o) {

        if (this == o) { return true; }
        if (o == null || getClass() != o.getClass()) { return false; }
        final StatsStruct that = (StatsStruct) o;
        return Double.compare(that.first, first) == 0 &&
               Double.compare(that.last, last) == 0 &&
               Double.compare(that.average, average) == 0 &&
               Double.compare(that.min, min) == 0 &&
               Double.compare(that.max, max) == 0 &&
               count == that.count;
    }

    @Override
    public int hashCode() {

        return Objects.hashCode(first, last, average, min, max, count);
    }

    @Override
    public String toString() {

        return "StatsStruct{" +
               "first=" + first +
               ", last=" + last +
               ", average=" + average +
               ", min=" + min +
               ", max=" + max +
               ", count=" + count +
               '}';
    }

    public void addValue(final double value) {

        final long statsPrevCount = this.count;
        if (statsPrevCount == 0L) {
            this.first = value;
            this.max = value;
            this.min = value;
        }

        this.last = value;
        this.max = Math.max(this.max, value);
        this.min = Math.min(this.min, value);
        final double sum = this.average * (double) statsPrevCount;
        this.average = (sum + value) / (double) (statsPrevCount + 1L);
        ++this.count;
    }
}
