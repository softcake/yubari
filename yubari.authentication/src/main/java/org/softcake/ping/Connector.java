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

package org.softcake.ping;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.LinkedList;

public class Connector extends Thread {
    private static final Logger LOGGER = LoggerFactory.getLogger(Connector.class);

    private final LinkedList<Target> pending = new LinkedList<>();

    private Selector selector;

    private volatile boolean shutdown = false;


    public Connector() {

        super("PingConnector");

    }

    @Override
    public void start() {

        if (this.selector == null) {
            try {
                this.selector = Selector.open();
            } catch (IOException e) {
                LOGGER.error("Exception while open selector", e);
            }
        }
        super.start();
    }

    // Initiate a connection sequence to the given target and add the
    // target to the pending-target list
    //
    public void add(final Target target) {


        SocketChannel channel = null;

        try {
            channel = SocketChannel.open();
            channel.configureBlocking(false);
            channel.connect(target.getAddress());

            target.setChannel(channel);
            target.setConnectStart(System.currentTimeMillis());

            synchronized (this.pending) {
                this.pending.add(target);
            }
            if (this.selector == null) {
                this.selector = Selector.open();
            }
            this.selector.wakeup();

        } catch (final Exception e) {
            target.setFailure(e);
            LOGGER.error("Ping to [{}] failed: {}", target.getAddress(), e.getMessage(), e);

            if (channel != null) {
                try {
                    channel.close();
                } catch (Exception ex) {
                    LOGGER.error("Exception while closing socket channel", ex);
                }
            }


            synchronized (this) {
                this.notifyAll();
            }
        }

    }

    // Process any targets in the pending list
    //
    private void processPendingTargets() throws IOException {

        synchronized (this.pending) {
            while (this.pending.size() > 0) {
                final Target t = this.pending.removeFirst();

                try {
                    t.getChannel()
                     .register(this.selector, SelectionKey.OP_CONNECT, t);
                } catch (final IOException e) {
                    t.getChannel()
                     .close();
                    t.setFailure(e);
                    LOGGER.error("Ping to [{}] failed: {}", t.getAddress(), e.getMessage(), e);

                    synchronized (this) {
                        this.notifyAll();
                    }
                }
            }

        }
    }

    // Process keys that have become selected
    //
    private void processSelectedKeys() throws IOException {

        Iterator<SelectionKey> i = this.selector.selectedKeys()
                                                .iterator();

        while (i.hasNext()) {
            final SelectionKey sk = i.next();
            i.remove();

            final Target t = (Target) sk.attachment();
            final SocketChannel sc = (SocketChannel) sk.channel();

            try {
                if (sc.finishConnect()) {
                    sk.cancel();
                    t.setConnectFinish(System.currentTimeMillis());

                    final long pingTime = t.getPingTime();

                    LOGGER.debug("Ping to [{}] resulted in {}ms", t.getAddress(), pingTime);

                    sc.close();
                }
                synchronized (this) {
                    this.notifyAll();
                }

            } catch (final IOException e) {
                sc.close();
                t.setFailure(e);
                LOGGER.error("Ping to [{}] failed: {}", t.getAddress(), e.getMessage(), e);
                synchronized (this) {
                    this.notifyAll();
                }
            }
        }

    }


    public void shutdown() {

        this.shutdown = true;
        this.selector.wakeup();
    }


    public void run() {

        while (true) {
            try {

                final int x = this.selector.select();
                if (x > 0) {
                    this.processSelectedKeys();
                }

                this.processPendingTargets();
                if (this.shutdown) {
                    this.selector.close();
                    return;
                }
            } catch (final IOException var2) {
                var2.printStackTrace();
            }
        }
    }

    public void addAll(final Target[] targets) {

        for (final Target target : targets) {
            add(target);
        }

    }
}
