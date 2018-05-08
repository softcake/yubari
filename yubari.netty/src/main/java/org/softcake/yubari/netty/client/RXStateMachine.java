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

import com.google.common.util.concurrent.MoreExecutors;
import io.reactivex.Observable;
import io.reactivex.ObservableEmitter;
import io.reactivex.ObservableOnSubscribe;
import io.reactivex.Single;
import io.reactivex.functions.BiConsumer;
import io.reactivex.functions.Consumer;
import io.reactivex.schedulers.Schedulers;
import io.reactivex.subjects.PublishSubject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;

public class RXStateMachine<T, E extends Enum, C extends Enum> implements Consumer<E> {
    private static final Logger LOGGER = LoggerFactory.getLogger(RXStateMachine.class);
    private final T context;
    private final Executor executor;
    private final PublishSubject<E> events = PublishSubject.create();
    private volatile State<T, E, C> state;
    protected RXStateMachine(T context, State<T, E, C> initial) {
        this(context, initial, MoreExecutors.directExecutor());
    }
    protected RXStateMachine(T context, State<T, E, C> initial, final Executor executor) {
        this.state = initial;
        this.context = context;
        this.executor = executor;
    }
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
    public static class SomeContext {
        @Override
        public String toString() {
            return "Foo []";
        }
    }

    public static BiConsumer<SomeContext, RXStateMachine.State<SomeContext, Event, ClientState>> log(final String text) {
        return new BiConsumer<>() {
            @Override
            public void accept(SomeContext t1, RXStateMachine.State<SomeContext, Event, ClientState> state) {

                LOGGER.info("" + t1 + ":" + state + ":" + text);
            }
        };
    }
    public static void main(String[] args) {





        RXStateMachine.State<SomeContext, Event,ClientState> IDLE        = new RXStateMachine.State<>(ClientState.IDLE);
        RXStateMachine.State<SomeContext, Event,ClientState> CONNECTING        = new RXStateMachine.State<>(ClientState.CONNECTING);
         RXStateMachine.State<SomeContext, Event,ClientState> SSL_HANDSHAKE_WAITING        = new RXStateMachine.State<>(ClientState.SSL_HANDSHAKE_WAITING);
         RXStateMachine.State<SomeContext, Event,ClientState> SSL_HANDSHAKE_SUCCESSFUL        = new RXStateMachine.State<>(ClientState.SSL_HANDSHAKE_SUCCESSFUL);
         RXStateMachine.State<SomeContext, Event,ClientState> PROTOCOL_VERSION_NEGOTIATION_WAITING        = new RXStateMachine.State<>(ClientState.PROTOCOL_VERSION_NEGOTIATION_WAITING);
         RXStateMachine.State<SomeContext, Event,ClientState> PROTOCOL_VERSION_NEGOTIATION_SUCCESSFUL        = new RXStateMachine.State<>(ClientState.PROTOCOL_VERSION_NEGOTIATION_SUCCESSFUL);
        RXStateMachine.State<SomeContext, Event,ClientState> AUTHORIZING_WAITING        = new RXStateMachine.State<>(ClientState.AUTHORIZING_WAITING);
        RXStateMachine.State<SomeContext, Event,ClientState> AUTHORIZING__SUCCESSFUL        = new RXStateMachine.State<>(ClientState.AUTHORIZING__SUCCESSFUL);
        RXStateMachine.State<SomeContext, Event,ClientState> ONLINE        = new RXStateMachine.State<>(ClientState.ONLINE);
         RXStateMachine.State<SomeContext, Event,ClientState> DISCONNECTING        = new RXStateMachine.State<>(ClientState.DISCONNECTING);
         RXStateMachine.State<SomeContext, Event,ClientState> DISCONNECTED        = new RXStateMachine.State<>(ClientState.DISCONNECTED);



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



            RXStateMachine<SomeContext, Event,ClientState> sm = new RXStateMachine<>(new SomeContext(), IDLE);

            sm.connect().subscribe();
        sm.accept(Event.CONNECTING);
        sm.accept(Event.SSL_HANDSHAKE_WAITING);
        sm.accept(Event.SSL_HANDSHAKE_SUCCESSFUL);
        sm.accept(Event.PROTOCOL_VERSION_NEGOTIATION_WAITING);
        sm.accept(Event.PROTOCOL_VERSION_NEGOTIATION_SUCCESSFUL);
        sm.accept(Event.AUTHORIZING_WAITING);
        sm.accept(Event.AUTHORIZING__SUCCESSFUL);
        sm.accept(Event.ONLINE);
        sm.accept(Event.DISCONNECTING);
        sm.accept(Event.DISCONNECTED);

    }
    public Observable<Void> connect() {



        return Observable.create(new ObservableOnSubscribe<Void>() {
            @Override
            public void subscribe(final ObservableEmitter<Void> sub) throws Exception {

                state.enter(context);

                sub.setDisposable(events.collect(new Callable<T>() {
                    @Override
                    public T call() throws Exception {

                        return context;
                    }
                }, new BiConsumer<T, E>() {
                    @Override
                    public void accept(final T context, final E event) throws Exception {

                        final State<T, E, C> next = state.next(event);
                        if (next != null) {
                            state.exit(context);
                            state = next;
                            next.enter(context);
                        } else {
                            LOGGER.info("Invalid event : {} state: {}", event, state);
                        }
                    }
                }).subscribeOn(Schedulers.from(executor)).subscribe());
            }
        });
    }

    @Override
    public void accept(E event) {
        events.toSerialized().onNext(event);
    }

    public State<T, E, C> getState() {
        return state;
    }

    public static class State<T, E, C> {

        public C getState() {

            return state;
        }

        private final C state;
        private final String name;
        private BiConsumer<T, State<T, E, C>> enter;
        private BiConsumer<T, State<T, E, C>> exit;
        private Map<E, State<T, E, C>> transitions = new HashMap<>();

        public State(C state) {
            this.state = state;
            this.name = state.toString();
        }

        public State<T, E, C> onEnter(BiConsumer<T, State<T, E, C>> func) {
            this.enter = func;
            return this;
        }

        public State<T, E, C> onExit(BiConsumer<T, State<T, E, C>> func) {
            this.exit = func;
            return this;
        }

        public void enter(T context) {
            try {
                enter.accept(context, this);
            } catch (Exception e) {
                LOGGER.error("Exception",e);
            }
        }

        public void exit(T context) {
            try {
                exit.accept(context, this);
            } catch (Exception e) {
                LOGGER.error("Exception",e);
            }
        }

        public State<T, E, C> transition(E event, State<T, E, C> state) {
            transitions.put(event, state);
            return this;
        }

        private State<T, E, C> next(E event) {
            return transitions.get(event);
        }

        public String toString() {
            return name;
        }
    }

}