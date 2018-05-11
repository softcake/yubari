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
import io.reactivex.functions.BiConsumer;
import io.reactivex.functions.Consumer;
import io.reactivex.schedulers.Schedulers;
import io.reactivex.subjects.PublishSubject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;

public class StateMachine<T, E extends Enum, C extends Enum> implements Consumer<E> {
    private static final Logger LOGGER = LoggerFactory.getLogger(StateMachine.class);
    private final T context;
    private final Executor executor;
    private final String name;
    private final PublishSubject<E> events = PublishSubject.create();
    private final PublishSubject<C> states = PublishSubject.create();
    private volatile State<T, E, C> state;
    private static final String STATE_CHANGED_TO = "[{}] State changed to {}";
    protected StateMachine(final T context, final State<T, E, C> initial) {

        this(context, initial, MoreExecutors.directExecutor(),"");

    }

    protected StateMachine(final T context, final State<T, E, C> initial, final Executor executor, String name) {

        this.state = initial;
        this.context = context;
        this.executor = executor;
        this.name = name;

    }

    public Observable<Void> connect() {

        return Observable.create(sub -> {

            state.enter(context);
            states.onNext(state.getState());
            sub.setDisposable(events.collect(() -> context, new BiConsumer<T, E>() {
                @Override
                public void accept(final T context, final E event) throws Exception {

                    final State<T, E, C> next = state.next(event);
                    if (next != null) {
                        states.onNext(next.getState());
                        LOGGER.info("[{}] State changed to {}", name, next.getState().toString());
                        state.exit(context);
                        state = next;
                        next.enter(context);
                    } else {
                        LOGGER.info("Invalid event : {} current state: {}", event, state);
                    }
                }
            }).subscribeOn(Schedulers.from(executor)).subscribe());
        });
    }

    @Override
    public void accept(final E event) {

        events.toSerialized().onNext(event);
    }

    public Observable<C> observe(){

       return states.subscribeOn(Schedulers.from(executor)).serialize();
    }


    public C getState() {

        return state.getState();
    }

    public static class State<T, E, C> {

        private final C state;
        private final String name;
        private BiConsumer<T, C> enter;
        private BiConsumer<T, C> exit;
        private Map<E, State<T, E, C>> transitions = new HashMap<>();

        public State(final C state) {

            this.state = state;
            this.name = state.toString();
        }

        public C getState() {

            return state;
        }

        public State<T, E, C> onEnter(final BiConsumer<T, C> func) {

            this.enter = func;
            return this;
        }

        public State<T, E, C> onExit(final BiConsumer<T, C> func) {

            this.exit = func;
            return this;
        }

        public State<T, E, C> transition(final E event, final State<T, E, C> state) {

            transitions.put(event, state);
            return this;
        }

        private void enter(final T context) {

            if (enter == null) {
                return;
            }
            try {
                enter.accept(context, state);
            } catch (final Exception e) {
                LOGGER.error("Exception", e);
            }
        }

        private void exit(final T context) {

            if (exit == null) {
                return;
            }
            try {
                exit.accept(context, state);
            } catch (final Exception e) {
                LOGGER.error("Exception", e);
            }
        }


        private State<T, E, C> next(final E event) {

            return transitions.get(event);
        }

        public String toString() {

            return name;
        }
    }

}