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

package org.softcake.authentication;


import org.softcake.authentication.concurrent.ThreadUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

@SuppressWarnings("squid:S2236")
public class Ping {


    private static final Logger LOGGER = LoggerFactory.getLogger(Ping.class);

    private static final int DAYTIME_PORT = 13;

    private static final int TIMEOUT = 2000;

    private static final int ATTEMPS = 3;


    private static volatile List<PingResult> pingsResults = null;

    private static Connector connector;


    public Ping() {

    }


    private static void addToConnector(Target[] t) {

        for (Target targets : t) {

            ThreadUtils.safeSleep(100);
            connector.add(targets);
        }

    }


    public static Map<String, Long[]> ping(Map<String, InetSocketAddress> addresses)
        throws IOException, InterruptedException {

        pingsResults = Collections.synchronizedList(new ArrayList<>());
        connector = new Connector();
        connector.start();
        LinkedHashMap<String, Target[]> targets = new LinkedHashMap<>();

        addresses.forEach(new BiConsumer<String, InetSocketAddress>() {

            @Override
            public void accept(String s, InetSocketAddress inetSocketAddress) {

                Target[] targetsArr = new Target[ATTEMPS];

                for (int i = 0; i < ATTEMPS; i++) {
                    targetsArr[i] = new Target(inetSocketAddress);
                }

                // Ping.Target[] target = new Ping.Target[]{new Ping.Target(inetSocketAddress)};
                // connector.add(target[0]);
                targets.put(s, targetsArr);
            }
        });

        targets.forEach(new BiConsumer<String, Target[]>() {

            @Override
            public void accept(String s, Target[] targets) {

                addToConnector(targets);
            }
        });

        synchronized (connector) {
            boolean isReady = false;
            long startTime = System.currentTimeMillis();
            Set<String> entries = targets.keySet();
            for (String entry : entries) {
                if (isReady) {
                    break;
                }
                isReady = true;
                Target[] targetArr = targets.get(entry);

                for (int i = 0; i < targetArr.length; ++i) {
                    Target target = targetArr[i];
                    if (target.failure == null && target.connectFinish == Long.MIN_VALUE) {
                        isReady = false;
                    }
                }

                if (!isReady) {
                    long timeOut = startTime + 2000L - System.currentTimeMillis();
                    if (timeOut > 0L) {
                        connector.wait(timeOut);
                    }
                }
            }
        }

        connector.shutdown();
        connector.join();
        LinkedHashMap<String, Long[]> addressPingTime = new LinkedHashMap<>();

        targets.forEach(new BiConsumer<String, Target[]>() {

            @Override
            public void accept(String s, Target[] targetArr) {

                Long[] pingTime = new Long[targetArr.length];
                for (int i = 0; i < targetArr.length; i++) {
                    Target target = targetArr[i];
                    if (target.failure != null) {
                        pingTime[i] = Long.MIN_VALUE;
                    } else if (target.connectFinish != Long.MIN_VALUE) {
                        pingTime[i] = target.connectFinish - target.connectStart;
                    } else {
                        String var25 = "Ping to [" + target.address + "] timed out.";
                        if (isSnapshotVersion()) {
                            LOGGER.debug(var25);
                        }

                        PingResult var26 = new PingResult(false, target.address.toString(), var25);
                        addPingResult(var26);
                        pingTime[i] = Long.MIN_VALUE;
                    }
                }

                addressPingTime.put(s, pingTime);
            }
        });

        try {
		  /* PlatformServiceProvider.getInstance().setPingInfo("ping", pingsResults);
            if(pingsResults != null) {
                pingsResults.clear();
            }*/
        } catch (Throwable var14) {
            LOGGER.error(var14.getMessage(), var14);
        }

        return addressPingTime;
    }


    private static void addPingResult(PingResult pingResult) {

        if (pingsResults != null) {
            pingsResults.add(pingResult);
        } else if (isSnapshotVersion()) {
            LOGGER.error("Cannot add the ping result, the \'pingsResults\' is null.");
        }

    }


    public static void main(String[] args) throws InterruptedException, IOException {

        if (false) {
            System.err.println("Usage: java Ping [port] host...");
        } else {


           /* byte firstArg = 0;
            int port = 13;
            if(Pattern.matches("[0-9]+", args[0])) {
                port = Integer.parseInt(args[0]);
                firstArg = 1;
            }
*/
            Connector connector = new Connector();
            connector.start();
            LinkedList<Target> targets = new LinkedList<>();

          /*  for (int i = firstArg; i < args.length; i++) {
                Target t = new Ping.Target(args[i]);
                targets.add(t);
                connector.add(t);
            }*/
            Target t = new Target("db.aldeso.com", 80);
            targets.add(t);
            connector.add(t);

            synchronized (connector) {
                boolean var14 = false;
                long timeoutStart = System.currentTimeMillis();

                while (!var14 && timeoutStart + 2000L > System.currentTimeMillis()) {
                    var14 = true;
                    Iterator sleep = targets.iterator();

                    while (sleep.hasNext()) {
                        Target target = (Target) sleep.next();
                        if (target.failure == null && target.connectFinish == -9223372036854775808L) {
                            var14 = false;
                        }
                    }

                    if (!var14) {
                        long var15 = timeoutStart + 2000L - System.currentTimeMillis();
                        if (var15 > 0L) {
                            connector.wait();
                        }
                    }
                }
            }

            connector.shutdown();
            connector.join();
            for (Iterator i = targets.iterator(); i.hasNext(); ) {
                Target a = (Target) i.next();
                if (!a.shown) { a.show(); }
            }

        }
    }


    public static boolean isSnapshotVersion() {

        boolean snapshotVersion = true;

        try {
            // snapshotVersion = AuthorizationClient.getInstance().isSnapshotVersion();
        } catch (Throwable t) {
            LOGGER.error(t.getMessage(), t);
        }

        // return snapshotVersion;
        return true;
    }


    private static class Connector extends Thread {


        private final LinkedList<Target> pending = new LinkedList<>();

        private Selector sel = Selector.open();

        private volatile boolean shutdown = false;


        public Connector() throws IOException {

            this.setName("Connector");
        }


        public void add(Target t) {

            SocketChannel sc = null;

            try {
                sc = SocketChannel.open();
                sc.configureBlocking(false);
                if (Ping.isSnapshotVersion()) {
                    Ping.LOGGER.debug("Initiating pinging connect to " + t.address);
                }

                sc.connect(t.address);
                t.channel = sc;
                t.connectStart = System.currentTimeMillis();
                synchronized (this.pending) {
                    this.pending.add(t);
                }

                this.sel.wakeup();
            } catch (Exception e) {
                if (sc != null) {
                    try {
                        sc.close();
                    } catch (Exception ignored) {
                    }
                }

                if (Ping.isSnapshotVersion()) {
                    Ping.LOGGER.error("Ping to [" + t.address + "] failed: " + e.getMessage(), e);
                }

                String pingResultAsString = "Ping to [" + t.address + "] failed.";
                PingResult pingResult = new PingResult(false, t.address.toString(), pingResultAsString);
                Ping.addPingResult(pingResult);
                t.failure = e;
                synchronized (this) {
                    this.notifyAll();
                }
            }

        }


        private void processPendingTargets() throws IOException {

            synchronized (this.pending) {
                while (this.pending.size() > 0) {
                    Target t = this.pending.removeFirst();

                    try {
                        t.channel.register(this.sel, SelectionKey.OP_CONNECT, t);
                    } catch (IOException e) {
                        t.channel.close();
                        t.failure = e;
                        if (Ping.isSnapshotVersion()) {
                            Ping.LOGGER.error("Ping to [" + t.address + "] failed: " + e.getMessage(), e);
                        }

                        String pingResultAsString = "Ping to [" + t.address + "] failed.";
                        PingResult pingResult = new PingResult(false, t.address.toString(), pingResultAsString);
                        Ping.addPingResult(pingResult);
                        synchronized (this) {
                            this.notifyAll();
                        }
                    }
                }

            }
        }


        private void processSelectedKeys() throws IOException {

            Iterator i = this.sel.selectedKeys().iterator();

            while (i.hasNext()) {
                SelectionKey sk = (SelectionKey) i.next();
                i.remove();
                Target t = (Target) sk.attachment();
                SocketChannel sc = (SocketChannel) sk.channel();

                try {
                    if (sc.finishConnect()) {
                        sk.cancel();
                        t.connectFinish = System.currentTimeMillis();
                        sc.close();
                        String x = "Ping to ["
                                   + t.address
                                   + "] resulted in ["
                                   + (t.connectFinish - t.connectStart)
                                   + "]ms";
                        if (Ping.isSnapshotVersion()) {
                            Ping.LOGGER.debug(x);
                        }

                        PingResult pingResultAsString1 = new PingResult(true,
                                                                        t.address.toString(),
                                                                        t.connectFinish - t.connectStart);
                        pingResultAsString1.setMessage(x);
                        Ping.addPingResult(pingResultAsString1);
                        synchronized (this) {
                            this.notifyAll();
                        }
                    }
                } catch (IOException var12) {
                    sc.close();
                    t.failure = var12;
                    if (Ping.isSnapshotVersion()) {
                        Ping.LOGGER.error("Ping to [" + t.address + "] failed: " + var12.getMessage(), var12);
                    }

                    String pingResultAsString = "Ping to [" + t.address + "] failed.";
                    PingResult pingResult = new PingResult(false, t.address.toString(), pingResultAsString);
                    Ping.addPingResult(pingResult);
                    synchronized (this) {
                        this.notifyAll();
                    }
                }
            }

        }


        public void shutdown() {

            this.shutdown = true;
            this.sel.wakeup();
        }


        public void run() {

            while (true) {
                try {
                    int x = this.sel.select();
                    if (x > 0) {
                        this.processSelectedKeys();
                    }

                    this.processPendingTargets();
                    if (this.shutdown) {
                        this.sel.close();
                        return;
                    }
                } catch (IOException var2) {
                    var2.printStackTrace();
                }
            }
        }
    }


    private static class Target {


        private InetSocketAddress address;


        private SocketChannel channel;

        private Exception failure;

        private long connectStart;

        private long connectFinish = Long.MIN_VALUE;

        private boolean shown = false;


        public Target(String host, int port) {

            try {
                this.address = new InetSocketAddress(InetAddress.getByName(host), port);
            } catch (IOException e) {
                this.failure = e;
            }

        }


        public Target(InetSocketAddress address) {

            this.address = address;
        }


        public void show() {

            String result;
            if (this.connectFinish != Long.MIN_VALUE) {
                result = Long.toString(this.connectFinish - this.connectStart) + "ms";
            } else if (this.failure != null) {
                result = this.failure.toString();
            } else {
                result = "Timed out";
            }

            System.out.println(this.address + " : " + result);
            this.shown = true;
        }
    }
}
