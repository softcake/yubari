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

package org.softcake.yubari.netty;

import io.reactivex.Observable;
import io.reactivex.Observer;
import io.reactivex.functions.BiConsumer;
import io.reactivex.functions.Consumer;
import io.reactivex.subjects.PublishSubject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;


public class RXStateMachine<T, E extends Enum<E>> implements Consumer<E> {
    private static final Logger LOG = LoggerFactory.getLogger(RXStateMachine.class);


    public static class State<T, E> {
        public E getName() {

            return name;
        }

        private E name;
        private BiConsumer<T, State<T, E>> enter;
        private BiConsumer<T, State<T, E>> exit;
        private Map<E, State<T, E>> transitions = new HashMap<>();

        public State(E name) {
            this.name = name;
        }

        public State<T, E> onEnter(BiConsumer<T, State<T, E>> func) {
            this.enter = func;
            return this;
        }

        public State<T, E> onExit(BiConsumer<T, State<T, E>> func) {
            this.exit = func;
            return this;
        }

        public void enter(T context) {
            try {
                enter.accept(context, this);
            } catch (Exception e) {
                LOG.error("Exception",e);
            }
        }

        public void exit(T context) {
            try {
                exit.accept(context, this);
            } catch (Exception e) {
                LOG.error("Exception",e);
            }
        }

        public State<T, E> transition(E event, State<T, E> state) {
            transitions.put(event, state);
            return this;
        }

        public State<T, E> next(E event) {
            return transitions.get(event);
        }

        public String toString() {
            return name.toString();
        }
    }

    private volatile State<T, E> state;
    private final T context;
    private final PublishSubject<E> events = PublishSubject.create();
    private final PublishSubject<E> publicEvents = PublishSubject.create();
    protected RXStateMachine(T context, State<T, E> initial) {
        this.state = initial;
        this.context = context;
    }

    public Observable<Void> connect() {
        return Observable.create(sub -> {
            state.enter(context);
            publicEvents.onNext(state.getName());
            sub.setDisposable(events.collect(() -> context, (context, event) -> {
                final State<T, E> next = state.next(event);
                if (next != null) {
                    state.exit(context);
                    state = next;
                    next.enter(context);
                }
                else {
                    LOG.info("Invalid event : " + event);
                }
            }).subscribe());
        });
    }
    public void observe(final Observer<E> observer) {
        publicEvents.subscribe(observer);
    }
    @Override
    public void accept(E event) {
        events.onNext(event);
        publicEvents.onNext(event);
    }

    public State<T, E> getState() {
        return state;
    }

}