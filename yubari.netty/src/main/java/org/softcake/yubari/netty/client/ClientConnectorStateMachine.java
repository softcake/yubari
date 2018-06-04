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

import org.softcake.yubari.netty.client.ClientConnector.ClientState;

import io.reactivex.Observable;
import io.reactivex.Single;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.BiConsumer;
import io.reactivex.functions.Consumer;
import io.reactivex.schedulers.Schedulers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executor;

/**
 * @author René Neubert
 */
public class ClientConnectorStateMachine {
    private static final Logger LOGGER = LoggerFactory.getLogger(ClientConnectorStateMachine.class);
    private final Executor executor;
    private final TransportClientSession session;
    StateMachine<SateMachineClient, Event, ClientState> sm;

    public ClientConnectorStateMachine(final SateMachineClient clientSateConnector2,
                                       final Executor executor,
                                       TransportClientSession session) {

        this.session = session;
        sm = createStates(clientSateConnector2, executor);

        this.executor = executor;
    }

    public static BiConsumer<SateMachineClient, ClientState> log(final String text) {

        return (c, s) -> LOGGER.info("" + c + ":" + s + ":" + text);
    }

    private StateMachine<SateMachineClient, Event, ClientState> createStates(final SateMachineClient
                                                                                 clientSateConnector2,
                                                                             final Executor executor) {


        final StateMachine.State<SateMachineClient, Event, ClientState>
            IDLE
            = new StateMachine.State<>(ClientState.IDLE);
        final StateMachine.State<SateMachineClient, Event, ClientState> CONNECTING = new StateMachine.State<>(
            ClientState.CONNECTING);

        final StateMachine.State<SateMachineClient, Event, ClientState> SSL_HANDSHAKE = new StateMachine.State<>(
            ClientState.SSL_HANDSHAKE);
        final StateMachine.State<SateMachineClient, Event, ClientState>
            PROTOCOL_VERSION_NEGOTIATION
            = new StateMachine.State<>(ClientState.PROTOCOL_VERSION_NEGOTIATION);
        final StateMachine.State<SateMachineClient, Event, ClientState> AUTHORIZING = new StateMachine.State<>(
            ClientState.AUTHORIZING);
        final StateMachine.State<SateMachineClient, Event, ClientState>
            ONLINE
            = new StateMachine.State<>(ClientState.ONLINE);
        final StateMachine.State<SateMachineClient, Event, ClientState> DISCONNECTING = new StateMachine.State<>(
            ClientState.DISCONNECTING);
        final StateMachine.State<SateMachineClient, Event, ClientState> DISCONNECTED = new StateMachine.State<>(
            ClientState.DISCONNECTED);


        IDLE.onEnter((c, s) -> c.onClientStateEnter(s))
            .transition(Event.CONNECTING, CONNECTING)
            .transition(Event.DISCONNECTING, DISCONNECTING);

        CONNECTING.onEnter((c, s) -> c.onClientStateEnter(s))
                  .transition(Event.SSL_HANDSHAKE_SUCCESSFUL, SSL_HANDSHAKE)
                  .transition(Event.PROTOCOL_VERSION_NEGOTIATION_SUCCESSFUL, PROTOCOL_VERSION_NEGOTIATION)
                  .transition(Event.DISCONNECTING, DISCONNECTING);

        SSL_HANDSHAKE.onEnter((c, s) -> c.onClientStateEnter(s))
                     .transition(Event.PROTOCOL_VERSION_NEGOTIATION_SUCCESSFUL, PROTOCOL_VERSION_NEGOTIATION)
                     .transition(Event.DISCONNECTING, DISCONNECTING);


        PROTOCOL_VERSION_NEGOTIATION.onEnter((c, s) -> c.onClientStateEnter(s))
                                    .transition(Event.AUTHORIZING, AUTHORIZING)
                                    .transition(Event.DISCONNECTING, DISCONNECTING);


        AUTHORIZING.onEnter((c, s) -> c.onClientStateEnter(s))
                   .transition(Event.ONLINE, ONLINE)
                   .transition(Event.DISCONNECTING, DISCONNECTING);

        ONLINE.onEnter((c, s) -> c.onClientStateEnter(s))
              .transition(Event.DISCONNECTING,
                          DISCONNECTING);
        DISCONNECTING.onEnter((c, s) -> c.onClientStateEnter(s))
                     .transition(Event.DISCONNECTED, DISCONNECTED);
        DISCONNECTED.onEnter((c, s) -> c.onClientStateEnter(s))
                    .onExit(log("exit"));


        return new StateMachine<>(clientSateConnector2, IDLE, executor, session.getTransportName());


    }

    public Observable<ClientState> observe() {

        return sm.observe();
    }


    public void accept(final Event e) {

        Disposable disposable = Single.just(e)
                                      .subscribeOn(Schedulers.from(executor))
                                      .subscribe(new Consumer<Event>() {
                                          @Override
                                          public void accept(final Event event) throws Exception {

                                              sm.accept(e);
                                          }
                                      });
    }


    public ClientState getState() {

        return sm.getState();
    }

    void disconnect() {

        sm.disconnect();
    }

    public void connect() {

        Disposable subscribe = sm.connect()
                                 .subscribe();
    }

    public enum Event {
        CONNECTING,
        SSL_HANDSHAKE_SUCCESSFUL,
        PROTOCOL_VERSION_NEGOTIATION_SUCCESSFUL,
        AUTHORIZING,
        ONLINE,
        DISCONNECTING,
        DISCONNECTED;

        Event() {

        }
    }
}
