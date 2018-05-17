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

package org.softcake.yubari.netty.client;

import org.softcake.yubari.netty.mina.DisconnectedEvent;

import com.dukascopy.dds4.transport.msg.system.ProtocolMessage;

/**
 * @author René Neubert
 */
public class TransportClientEvent {
    private final Type type;

    public Type getType() {

        return type;
    }

    public DisconnectedEvent getDisconnectedEvent() {

        return disconnectedEvent;
    }

    public ITransportClient getClient() {

        return client;
    }

    public ProtocolMessage getMessage() {

        return message;
    }

    private DisconnectedEvent disconnectedEvent;
    private ITransportClient client;
    private ProtocolMessage message;

    public TransportClientEvent(final ITransportClient client, final DisconnectedEvent disconnectedEvent) {

        this(client, null, disconnectedEvent, Type.DISCONNECTED);
    }

    public TransportClientEvent(final ITransportClient client) {

        this(client, null, null, Type.AUTHORIZED);

    }

    public TransportClientEvent(final ITransportClient client, final ProtocolMessage message) {

        this(client, message, null, Type.FEEDBACK);

    }

    private TransportClientEvent(final ITransportClient client,
                                 final ProtocolMessage message,
                                 final DisconnectedEvent disconnectedEvent,
                                 final Type type) {
        this.client = client;
        this.message = message;
        this.disconnectedEvent = disconnectedEvent;
        this.type = type;
    }



    public enum Type {
        AUTHORIZED,
        FEEDBACK,
        DISCONNECTED;

        Type() {

        }
    }
}
