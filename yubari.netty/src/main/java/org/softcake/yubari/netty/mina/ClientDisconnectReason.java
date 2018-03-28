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
import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;

public class ClientDisconnectReason {
    private final DisconnectReason disconnectReason;
    private final DisconnectHint disconnectHint;
    private final String disconnectComments;
    private final Throwable error;

    public ClientDisconnectReason(final DisconnectReason disconnectReason,
                                  final DisconnectHint disconnectHint,
                                  final String disconnectComments,
                                  final Throwable error) {

        this.disconnectReason = disconnectReason;
        this.disconnectHint = disconnectHint;
        this.disconnectComments = disconnectComments;
        this.error = error;
    }
    public ClientDisconnectReason(final DisconnectReason disconnectReason,
                                  final DisconnectHint disconnectHint,
                                  final String disconnectComments) {

        this(disconnectReason, disconnectHint, disconnectComments, new Throwable(""));
    }
    public ClientDisconnectReason(final DisconnectReason disconnectReason,
                                  final String disconnectComments,
                                  final Throwable error) {

        this(disconnectReason, null, disconnectComments, error);

    }
    public ClientDisconnectReason(final DisconnectReason disconnectReason,
                                  final String disconnectComments) {

        this(disconnectReason, null, disconnectComments,null);

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
        final ClientDisconnectReason that = (ClientDisconnectReason) o;
        return disconnectReason == that.disconnectReason && disconnectHint == that.disconnectHint && Objects.equal(
            disconnectComments,
            that.disconnectComments) && Objects.equal(error, that.error);
    }

    @Override
    public int hashCode() {

        return Objects.hashCode(disconnectReason, disconnectHint, disconnectComments, error);
    }

    @Override
    public String toString() {

        return MoreObjects.toStringHelper(this).add("disconnectComments", disconnectComments).add("disconnectHint",
                                                                                                  disconnectHint).add(
            "disconnectReason",
            disconnectReason).add("error", error).toString();
    }

}
