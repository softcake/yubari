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

import org.softcake.yubari.netty.client.ClientSateConnector2.ClientState;

import io.reactivex.Single;
import io.reactivex.functions.BiConsumer;
import io.reactivex.functions.Consumer;
import io.reactivex.schedulers.Schedulers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executor;

/**
 * @author René Neubert
 */
public class ClientStateConnector3 {
    private static final Logger LOGGER = LoggerFactory.getLogger(ClientStateConnector3.class);
    private final Executor executor;
    RXStateMachine<ClientSateConnector2, Event, ClientState> sm;

    public ClientStateConnector3(final ClientSateConnector2 clientSateConnector2, final Executor executor) {

        sm = createStates(clientSateConnector2, executor);

        this.executor = executor;
    }

    public static BiConsumer<ClientSateConnector2, RXStateMachine.State<ClientSateConnector2, Event, ClientState>> log(
        final String text) {

        return new BiConsumer<>() {
            @Override
            public void accept(ClientSateConnector2 t1,
                               RXStateMachine.State<ClientSateConnector2, Event, ClientState> state) {

                LOGGER.info("" + t1 + ":" + state + ":" + text);
            }
        };
    }

    private RXStateMachine<ClientSateConnector2, Event, ClientState> createStates(final ClientSateConnector2
                                                                                      clientSateConnector2,
                                                                                  final Executor executor) {


        RXStateMachine.State<ClientSateConnector2, Event, ClientState>
            IDLE
            = new RXStateMachine.State<>(ClientState.IDLE);
        RXStateMachine.State<ClientSateConnector2, Event, ClientState> CONNECTING = new RXStateMachine.State<>(
            ClientState.CONNECTING);

        RXStateMachine.State<ClientSateConnector2, Event, ClientState> SSL_HANDSHAKE = new RXStateMachine.State<>(
            ClientState.SSL_HANDSHAKE);
        RXStateMachine.State<ClientSateConnector2, Event, ClientState>
            PROTOCOL_VERSION_NEGOTIATION
            = new RXStateMachine.State<>(ClientState.PROTOCOL_VERSION_NEGOTIATION);
        RXStateMachine.State<ClientSateConnector2, Event, ClientState> AUTHORIZING = new RXStateMachine.State<>(
            ClientState.AUTHORIZING);
        RXStateMachine.State<ClientSateConnector2, Event, ClientState>
            ONLINE
            = new RXStateMachine.State<>(ClientState.ONLINE);
        RXStateMachine.State<ClientSateConnector2, Event, ClientState> DISCONNECTING = new RXStateMachine.State<>(
            ClientState.DISCONNECTING);
        RXStateMachine.State<ClientSateConnector2, Event, ClientState> DISCONNECTED = new RXStateMachine.State<>(
            ClientState.DISCONNECTED);


        IDLE
            .onEnter((c, s) -> c.onIdleEnter(s.getState()))
            .onExit(log("exit"))
            .transition(Event.CONNECTING, CONNECTING)
            .transition(Event.DISCONNECTING, DISCONNECTING);

        CONNECTING
            .onEnter((c, s) -> c.onConnectingEnter(s.getState()))
            .onExit(log("exit"))
            .transition(Event.SSL_HANDSHAKE_SUCCESSFUL, SSL_HANDSHAKE)
            .transition(Event.PROTOCOL_VERSION_NEGOTIATION_SUCCESSFUL, PROTOCOL_VERSION_NEGOTIATION)
            .transition(Event.DISCONNECTING, DISCONNECTING);

        SSL_HANDSHAKE
            .onEnter((c, s) -> c.onSslHandshakeEnter(s.getState()))
            .onExit(log("exit"))
            .transition(Event.PROTOCOL_VERSION_NEGOTIATION_SUCCESSFUL, PROTOCOL_VERSION_NEGOTIATION)
            .transition(Event.DISCONNECTING, DISCONNECTING);


        PROTOCOL_VERSION_NEGOTIATION.onEnter((c, s) -> c.onProtocolVersionNegotiationEnter(s.getState())).onExit(log(
            "exit")).transition(Event.AUTHORIZING_SUCCESSFUL, AUTHORIZING).transition(Event.DISCONNECTING,
                                                                                      DISCONNECTING);


        AUTHORIZING
            .onEnter((c, s) -> c.onAuthorizingEnter(s.getState()))
            .onExit(log("exit"))
            .transition(Event.ONLINE, ONLINE)
            .transition(Event.DISCONNECTING, DISCONNECTING);

        ONLINE.onEnter((c, s) -> c.onOnlineEnter(s.getState())).onExit(log("exit")).transition(Event.DISCONNECTING,
                                                                                               DISCONNECTING);
        DISCONNECTING
            .onEnter((c, s) -> c.onDisconnectingEnter(s.getState()))
            .onExit(log("exit"))
            .transition(Event.DISCONNECTED, DISCONNECTED);
            /*.transition(Event.SSL_HANDSHAKE_SUCCESSFUL, DISCONNECTED)
            .transition(Event.PROTOCOL_VERSION_NEGOTIATION_SUCCESSFUL, DISCONNECTED);*/
        DISCONNECTED.onEnter(log("enter")).onExit(log("exit"));


        return new RXStateMachine<>(clientSateConnector2, IDLE, executor);


    }

    public void accept(Event e) {

        Single.just(e).subscribeOn(Schedulers.from(executor)).subscribe(new Consumer<Event>() {
            @Override
            public void accept(final Event event) throws Exception {

                sm.accept(e);
            }
        });

    }

    public void connect() {

        sm.connect().subscribe();
    }


    public enum Event {
        CONNECTING,
        SSL_HANDSHAKE_WAITING,
        SSL_HANDSHAKE_SUCCESSFUL,
        PROTOCOL_VERSION_NEGOTIATION_WAITING,
        PROTOCOL_VERSION_NEGOTIATION_SUCCESSFUL,
        AUTHORIZING_WAITING,
        AUTHORIZING_SUCCESSFUL,
        ONLINE,
        DISCONNECTING,
        DISCONNECTED;
    }
}
