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

public class PingResult {
    private final Long pingTime;
    private final Throwable error;

    public PingResult(final Long pingTime, final Throwable error) {
        this.pingTime = pingTime;
        this.error = error;
    }

    public Long getPingTime() {
        return this.pingTime;
    }

    public Throwable getError() {
        return this.error;
    }

    @Override
    public boolean equals(final Object o) {

        if (this == o) { return true; }
        if (o == null || getClass() != o.getClass()) { return false; }
        final PingResult that = (PingResult) o;
        return Objects.equal(pingTime, that.pingTime) &&
               Objects.equal(error, that.error);
    }

    @Override
    public int hashCode() {

        return Objects.hashCode(pingTime, error);
    }

    public String toString() {
        final StringBuilder builder = new StringBuilder();
        builder.append("PingResult [");
        if (this.pingTime != null) {
            builder.append("pingTime=");
            builder.append(this.pingTime);
            builder.append(", ");
        }

        if (this.error != null) {
            builder.append("error=");
            builder.append(this.error);
        }

        builder.append("]");
        return builder.toString();
    }
}
