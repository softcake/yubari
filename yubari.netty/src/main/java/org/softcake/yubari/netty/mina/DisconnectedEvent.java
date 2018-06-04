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


import org.softcake.yubari.netty.client.ITransportClient;

import com.dukascopy.dds4.transport.common.mina.DisconnectReason;
import com.dukascopy.dds4.transport.msg.system.DisconnectHint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.function.Consumer;

public class DisconnectedEvent extends EventTask {
    private static final Logger LOGGER = LoggerFactory.getLogger(DisconnectedEvent.class);
    private final DisconnectReason reason;
    private final DisconnectHint hint;
    private final Throwable error;
    private final String comments;

    public DisconnectedEvent(final ITransportClient client, final DisconnectReason reason) {

        this(client, reason, null, null, null);
    }

    public DisconnectedEvent(final ITransportClient client, final DisconnectReason reason, final Throwable error) {

        this(client, reason, null, error, null);
    }

    public DisconnectedEvent(final ITransportClient client,
                             final DisconnectReason reason,
                             final DisconnectHint hint,
                             final Throwable error,
                             final String comments) {

        super(client);
        this.reason = reason;
        this.hint = hint;
        this.error = error;
        this.comments = comments;
    }

    public void execute() {

        try {
            Thread.sleep(1000L);
        } catch (final InterruptedException var3) {

        }
        this.listeners.forEach(new Consumer<ClientListener>() {
            @Override
            public void accept(final ClientListener clientListener) {

                LOGGER.debug("Disconnecting: {}", clientListener);
            }
        });
    }

    public DisconnectReason getReason() {

        return this.reason;
    }

    public Throwable getError() {

        return this.error;
    }

    public String getComments() {

        return this.comments;
    }

    public DisconnectHint getHint() {

        return this.hint;
    }

    public String toString() {

        final StringBuilder builder = new StringBuilder();
        builder.append("DisconnectedEvent [");
        if (this.reason != null) {
            builder.append("reason=");
            builder.append(this.reason);
            builder.append(", ");
        }

        if (this.hint != null) {
            builder.append("hint=");
            builder.append(this.hint);
            builder.append(", ");
        }

        if (this.error != null) {
            builder.append("error=");
            builder.append(this.error);
            builder.append(", ");
        }

        if (this.comments != null) {
            builder.append("comments=");
            builder.append(this.comments);
        }

        builder.append("]");
        return builder.toString();
    }

    @Override
    public boolean equals(final Object o) {

        if (this == o) { return true; }
        if (o == null || getClass() != o.getClass()) { return false; }
        final DisconnectedEvent that = (DisconnectedEvent) o;
        return reason == that.reason && hint == that.hint && Objects.equals(error, that.error) && Objects.equals(
            comments,
            that.comments);
    }

    @Override
    public int hashCode() {

        return Objects.hash(reason, hint, error, comments);
    }
}
