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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public abstract class EventTask implements Runnable {
    private static final Logger LOGGER = LoggerFactory.getLogger(EventTask.class);
    protected ITransportClient client;
    protected List<ClientListener> listeners;

    public EventTask(ITransportClient client) {

        this.client = client;
        this.listeners = client.getListeners();
    }

    public List<ClientListener> getListener() {

        return this.listeners;
    }

    public void setListener(List<ClientListener> listeners) {

        this.listeners = listeners;
    }

    public void run() {

        try {
            this.execute();
        } catch (Throwable var2) {
            LOGGER.error("Client listener invocation error", var2);
        }

    }

    public abstract void execute();

    public ITransportClient getClient() {

        return this.client;
    }
}
