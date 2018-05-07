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

import io.reactivex.functions.BiConsumer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class RXStateMachineTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(RXStateMachineTest.class);

    public enum ClientState {
        IDLE,
        CONNECTING,
        SSL_HANDSHAKE_WAITING,
        SSL_HANDSHAKE_SUCCESSFUL,
        PROTOCOL_VERSION_NEGOTIATION_WAITING,
        PROTOCOL_VERSION_NEGOTIATION_SUCCESSFUL,
        AUTHORIZING_WAITING,
        AUTHORIZING__SUCCESSFUL,
        ONLINE,
        DISCONNECTING,
        DISCONNECTED;

        ClientState() {

        }
    }
    public static enum Event {
        CONNECTING,
        SSL_HANDSHAKE_WAITING,
        SSL_HANDSHAKE_SUCCESSFUL,
        PROTOCOL_VERSION_NEGOTIATION_WAITING,
        PROTOCOL_VERSION_NEGOTIATION_SUCCESSFUL,
        AUTHORIZING_WAITING,
        AUTHORIZING__SUCCESSFUL,
        ONLINE,
        DISCONNECTING,
        DISCONNECTED;
    }

    public static BiConsumer<SomeContext, RXStateMachine.State<SomeContext, Event, ClientState>> log(final String text) {
        return new BiConsumer<>() {
            @Override
            public void accept(SomeContext t1, RXStateMachine.State<SomeContext, Event, ClientState> state) {

                LOGGER.info("" + t1 + ":" + state + ":" + text);
            }
        };
    }

    public static class SomeContext {
        @Override
        public String toString() {
            return "Foo []";
        }
    }

    public static RXStateMachine.State<SomeContext, Event,ClientState> IDLE        = new RXStateMachine.State<>(ClientState.IDLE);
    public static RXStateMachine.State<SomeContext, Event,ClientState> CONNECTING        = new RXStateMachine.State<>(ClientState.CONNECTING);
    public static RXStateMachine.State<SomeContext, Event,ClientState> SSL_HANDSHAKE_WAITING        = new RXStateMachine.State<>(ClientState.SSL_HANDSHAKE_WAITING);
    public static RXStateMachine.State<SomeContext, Event,ClientState> SSL_HANDSHAKE_SUCCESSFUL        = new RXStateMachine.State<>(ClientState.SSL_HANDSHAKE_SUCCESSFUL);
    public static RXStateMachine.State<SomeContext, Event,ClientState> PROTOCOL_VERSION_NEGOTIATION_WAITING        = new RXStateMachine.State<>(ClientState.PROTOCOL_VERSION_NEGOTIATION_WAITING);
    public static RXStateMachine.State<SomeContext, Event,ClientState> PROTOCOL_VERSION_NEGOTIATION_SUCCESSFUL        = new RXStateMachine.State<>(ClientState.PROTOCOL_VERSION_NEGOTIATION_SUCCESSFUL);
    public static RXStateMachine.State<SomeContext, Event,ClientState> AUTHORIZING_WAITING        = new RXStateMachine.State<>(ClientState.AUTHORIZING_WAITING);
    public static RXStateMachine.State<SomeContext, Event,ClientState> AUTHORIZING__SUCCESSFUL        = new RXStateMachine.State<>(ClientState.AUTHORIZING__SUCCESSFUL);
    public static RXStateMachine.State<SomeContext, Event,ClientState> ONLINE        = new RXStateMachine.State<>(ClientState.ONLINE);
    public static RXStateMachine.State<SomeContext, Event,ClientState> DISCONNECTING        = new RXStateMachine.State<>(ClientState.DISCONNECTING);
    public static RXStateMachine.State<SomeContext, Event,ClientState> DISCONNECTED        = new RXStateMachine.State<>(ClientState.DISCONNECTED);


    @BeforeAll
    public static void beforeClass() {
        IDLE
            .onEnter(log("enter"))
            .onExit(log("exit"))
            .transition(Event.CONNECTING, CONNECTING)
            .transition(Event.DISCONNECTING,  DISCONNECTING);

        CONNECTING
            .onEnter(log("enter"))
            .onExit(log("exit"))
            .transition(Event.SSL_HANDSHAKE_WAITING, SSL_HANDSHAKE_WAITING)
            .transition(Event.DISCONNECTING,  DISCONNECTING);

        SSL_HANDSHAKE_WAITING
            .onEnter(log("enter"))
            .onExit(log("exit"))
            .transition(Event.SSL_HANDSHAKE_SUCCESSFUL,    SSL_HANDSHAKE_SUCCESSFUL)
            .transition(Event.DISCONNECTING,  DISCONNECTING);

        SSL_HANDSHAKE_SUCCESSFUL
            .onEnter(log("enter"))
            .onExit(log("exit"))
            .transition(Event.PROTOCOL_VERSION_NEGOTIATION_WAITING, PROTOCOL_VERSION_NEGOTIATION_WAITING)
            .transition(Event.DISCONNECTING,  DISCONNECTING);

        PROTOCOL_VERSION_NEGOTIATION_WAITING
            .onEnter(log("enter"))
            .onExit(log("exit"))
            .transition(Event.PROTOCOL_VERSION_NEGOTIATION_SUCCESSFUL, PROTOCOL_VERSION_NEGOTIATION_SUCCESSFUL)
            .transition(Event.DISCONNECTING,  DISCONNECTING);
        PROTOCOL_VERSION_NEGOTIATION_SUCCESSFUL
            .onEnter(log("enter"))
            .onExit(log("exit"))
            .transition(Event.AUTHORIZING_WAITING, AUTHORIZING_WAITING)
            .transition(Event.DISCONNECTING,  DISCONNECTING);
        AUTHORIZING_WAITING
            .onEnter(log("enter"))
            .onExit(log("exit"))
            .transition(Event.AUTHORIZING__SUCCESSFUL, AUTHORIZING__SUCCESSFUL)
            .transition(Event.DISCONNECTING,  DISCONNECTING);
        AUTHORIZING__SUCCESSFUL
            .onEnter(log("enter"))
            .onExit(log("exit"))
            .transition(Event.ONLINE, ONLINE)
            .transition(Event.DISCONNECTING,  DISCONNECTING);
        ONLINE
            .onEnter(log("enter"))
            .onExit(log("exit"))
            .transition(Event.DISCONNECTING,  DISCONNECTING);
        DISCONNECTING
            .onEnter(log("enter"))
            .onExit(log("exit"))
            .transition(Event.DISCONNECTED,  DISCONNECTED);
        DISCONNECTED
            .onEnter(log("enter"))
            .onExit(log("exit"));
    }
    @Test
    public void test() {

        RXStateMachine<SomeContext, Event,ClientState> sm = new RXStateMachine<>(new SomeContext(), IDLE);

        sm.connect().subscribe();
        /*sm.accept(Event.CONNECTING);
        sm.accept(Event.SSL_HANDSHAKE_WAITING);
        sm.accept(Event.SSL_HANDSHAKE_SUCCESSFUL);
        sm.accept(Event.PROTOCOL_VERSION_NEGOTIATION_WAITING);
        sm.accept(Event.PROTOCOL_VERSION_NEGOTIATION_SUCCESSFUL);
        sm.accept(Event.AUTHORIZING_WAITING);
        sm.accept(Event.AUTHORIZING__SUCCESSFUL);
        sm.accept(Event.ONLINE);
        sm.accept(Event.DISCONNECTING);
        sm.accept(Event.DISCONNECTED);*/
    }
}