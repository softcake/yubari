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

package org.softcake.yubari.netty.pinger;

import com.dukascopy.dds4.transport.common.protocol.binary.AbstractStaticSessionDictionary;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class Pinger {
    public static final int DEFAULT_PING_DATA_SIZE = 10240;
    public static final long DEFAULT_MAX_PING_TIME_OUT = 10L;
    public static final TimeUnit DEFAULT_MAX_PING_TIME_OUT_UNIT;
    public static final int DEFAULT_MAX_PING_THREADS_COUNT = 10;
    private final List<PingTarget> targets;
    private final int pingDataSizeInBytes;
    private final long pingTimeout;
    private final TimeUnit pingTimeoutTimeUnit;
    private final ThreadPoolExecutor executor;
    private byte[] pingData;
    private final boolean waitForDisconnect;
    private final AbstractStaticSessionDictionary staticSessionDictionary;

    public Pinger(AbstractStaticSessionDictionary staticSessionDictionary, List<PingTarget> targets) {
        this(staticSessionDictionary, targets, DEFAULT_PING_DATA_SIZE, DEFAULT_MAX_PING_TIME_OUT, DEFAULT_MAX_PING_TIME_OUT_UNIT, DEFAULT_MAX_PING_THREADS_COUNT, false);
    }

    public Pinger(AbstractStaticSessionDictionary staticSessionDictionary, List<PingTarget> targets, int pingDataSizeInBytes, long pingTimeout, TimeUnit pingTimeoutTimeUnit, int maxPingThreadsCount, boolean waitForDisconnect) {
        if (staticSessionDictionary == null) {
            throw new IllegalArgumentException("No staticSessionDictionary");
        } else if (targets != null && !targets.isEmpty()) {
            if (pingDataSizeInBytes < 0) {
                throw new IllegalArgumentException("Ping data size in bytes of wrong value " + pingDataSizeInBytes);
            } else if (pingTimeout <= 0L) {
                throw new IllegalArgumentException("Ping timeout of wrong value " + pingTimeout);
            } else if (pingTimeoutTimeUnit == null) {
                throw new IllegalArgumentException("Ping pingTimeoutTimeUnit of wrong value " + pingTimeoutTimeUnit);
            } else {
                long pingTimeoutMillis = pingTimeoutTimeUnit.toMillis(pingTimeout);
                long maxPingTimeoutMillis = DEFAULT_MAX_PING_TIME_OUT_UNIT.toMillis(10L);
                if (pingTimeoutMillis > maxPingTimeoutMillis) {
                    throw new IllegalArgumentException("Ping time out " + pingTimeoutMillis + " is too big, max possible is " + maxPingTimeoutMillis);
                } else if (maxPingThreadsCount <= 0) {
                    throw new IllegalArgumentException("Ping maxPingThreadsCount of wrong value " + maxPingThreadsCount);
                } else {
                    this.staticSessionDictionary = staticSessionDictionary;
                    this.targets = targets;
                    this.pingDataSizeInBytes = pingDataSizeInBytes;
                    this.pingTimeout = pingTimeout;
                    this.pingTimeoutTimeUnit = pingTimeoutTimeUnit;
                    this.waitForDisconnect = waitForDisconnect;
                    int threadCount = Math.min(targets.size(), maxPingThreadsCount);
                    this.executor = new ThreadPoolExecutor(threadCount, threadCount, 1L, TimeUnit.MINUTES, new LinkedBlockingQueue(), new ThreadFactory() {
                        private final AtomicInteger threadCounter = new AtomicInteger(1);

                        public Thread newThread(Runnable r) {
                            Thread thread = new Thread(r, "PingerThread" + this.threadCounter.getAndIncrement());
                            thread.setDaemon(true);
                            thread.setPriority(10);
                            return thread;
                        }
                    });
                    this.pingData = new byte[pingDataSizeInBytes];
                }
            }
        } else {
            throw new IllegalArgumentException("No targets");
        }
    }

    public Map<PingTarget, PingResult> ping() {
        Map<PingTarget, PingResult> result = new LinkedHashMap();
        Map<PingTarget, Future<Long>> futures = new LinkedHashMap();
        final List<Future<?>> transportTerminationFutures = new ArrayList();
        Iterator i$ = this.targets.iterator();


        while(i$.hasNext()) {
            final PingTarget target  = (PingTarget)i$.next();
            futures.put(target, this.executor.submit(new Callable<Long>() {
                public Long call() throws Exception {
                    try {
                        return Pinger.this.doPing(target, transportTerminationFutures);
                    } catch (Throwable var2) {
                        throw new RuntimeException(var2);
                    }
                }
            }));
        }

        Throwable error;
        Long pingTime;
        PingTarget target;
        for(i$ = futures.keySet().iterator(); i$.hasNext(); result.put(target, new PingResult(pingTime, error))) {
            target = (PingTarget)i$.next();
            Future<Long> future = (Future)futures.get(target);
            error = null;
            pingTime = null;

            try {
                pingTime = (Long)future.get(2L * this.pingTimeout, this.pingTimeoutTimeUnit);
            } catch (Throwable var11) {
                error = var11;
            }
        }

        if (this.waitForDisconnect) {
            try {
                this.executor.awaitTermination(this.pingTimeout, this.pingTimeoutTimeUnit);
            } catch (Throwable var10) {
                ;
            }
        }

        this.executor.shutdown();
        return result;
    }

    private Long doPing(PingTarget target, List<Future<?>> transportTerminationFutures) throws Throwable {
        long before = System.currentTimeMillis();
        final Throwable[] exceptions = new Throwable[1];
        final PingClient pingerNettyClient = new PingClient(target, this.staticSessionDictionary,
                                                            (t, reason) -> exceptions[0] = t, this.pingDataSizeInBytes + 1024);
        long timeout = this.pingTimeoutTimeUnit.toMillis(this.pingTimeout);

        Long var10;
        try {
            pingerNettyClient.ping(timeout, this.pingData);
            if (exceptions[0] != null) {
                throw exceptions[0];
            }

            Long result = System.currentTimeMillis() - before;
            var10 = result;
        } catch (Throwable var14) {
            if (exceptions[0] != null) {
                throw exceptions[0];
            }

            throw var14;
        } finally {
            transportTerminationFutures.add(this.executor.submit(pingerNettyClient::disconnect));
        }

        return var10;
    }

    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("Pinger [");
        if (this.targets != null) {
            builder.append("targets=");
            builder.append(this.targets);
        }

        builder.append("]");
        return builder.toString();
    }

    static {
        DEFAULT_MAX_PING_TIME_OUT_UNIT = TimeUnit.SECONDS;
    }
}
