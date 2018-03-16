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

package org.softcake.yubari.netty.mina;

import com.dukascopy.dds4.transport.common.mina.DisconnectReason;
import com.dukascopy.dds4.transport.msg.system.DisconnectHint;
import com.google.common.base.Objects;

public class ExtendedClientDisconnectReason {
    private final DisconnectReason disconnectReason;
    private final DisconnectHint disconnectHint;
    private final String disconnectComments;
    private final Throwable error;

    public ExtendedClientDisconnectReason(DisconnectReason disconnectReason,
                                          DisconnectHint disconnectHint,
                                          String disconnectComments,
                                          Throwable error) {

        this.disconnectReason = disconnectReason;
        this.disconnectHint = disconnectHint;
        this.disconnectComments = disconnectComments;
        this.error = error;
    }

    public DisconnectReason getDisconnectReason() {

        return this.disconnectReason;
    }

    public String getDisconnectComments() {

        return this.disconnectComments;
    }

    public Throwable getError() {

        return this.error;
    }

    public DisconnectHint getDisconnectHint() {

        return this.disconnectHint;
    }

    @Override
    public boolean equals(final Object o) {

        if (this == o) { return true; }
        if (o == null || getClass() != o.getClass()) { return false; }
        final ExtendedClientDisconnectReason that = (ExtendedClientDisconnectReason) o;
        return disconnectReason == that.disconnectReason && disconnectHint == that.disconnectHint && Objects.equal(
            disconnectComments,
            that.disconnectComments) && Objects.equal(error, that.error);
    }

    @Override
    public int hashCode() {

        return Objects.hashCode(disconnectReason, disconnectHint, disconnectComments, error);
    }

    public String toString() {

        StringBuilder builder = new StringBuilder();
        builder.append("ExtendedClientDisconnectReason [");
        if (this.disconnectReason != null) {
            builder.append("disconnectReason=");
            builder.append(this.disconnectReason);
            builder.append(", ");
        }

        if (this.disconnectHint != null) {
            builder.append("disconnectHint=");
            builder.append(this.disconnectHint);
            builder.append(", ");
        }

        if (this.disconnectComments != null) {
            builder.append("disconnectComments=");
            builder.append(this.disconnectComments);
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
