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


import org.softcake.yubari.netty.ListeningOrderedThreadPoolExecutor;

import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;

import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class TransportHelper {
    private static final int PROTOCOL_VERSION = 4;

    public TransportHelper() {

    }



    public static ListeningExecutorService createExecutor(final int poolSize,
                                                          final long autoCleanUpInerval,
                                                          final int criticalQueueSize,
                                                          final String threadNamePrefix,
                                                          final String threadBasicName,
                                                          final boolean prestartAllCoreThreads) {

        return createExecutor(poolSize,
                              autoCleanUpInerval,
                              criticalQueueSize,
                              threadNamePrefix,
                              null,
                              threadBasicName,
                              prestartAllCoreThreads);
    }

    public static ListeningExecutorService createExecutor(final int poolSize,
                                                          final long autoCleanUpInerval,
                                                          final int criticalQueueSize,
                                                          final String threadNamePrefix,
                                                          final List<Thread> createdThreads,
                                                          final String threadBasicName,
                                                          final boolean prestartAllCoreThreads) {

        return createExecutor(poolSize,
                              poolSize,
                              autoCleanUpInerval,
                              criticalQueueSize,
                              threadNamePrefix,
                              null,
                              threadBasicName,
                              prestartAllCoreThreads);
    }

    private static ListeningExecutorService createExecutor(final int poolSize,
                                                           final int maxPoolSize,
                                                           final long autoCleanUpInerval,
                                                           final int criticalQueueSize,
                                                           final String threadNamePrefix,
                                                           final List<Thread> createdThreads,
                                                           final String threadBasicName,
                                                           final boolean prestartAllCoreThreads) {

        if (poolSize <= 0) {
            return MoreExecutors.newDirectExecutorService();
        } else {
            final ThreadFactory threadFactory = new ThreadFactory() {
                private AtomicInteger counter = new AtomicInteger(0);

                public Thread newThread(final Runnable r) {

                    final Thread thread = new Thread(r, (threadBasicName != null ? "(" + threadBasicName + ") " : "")
                                                        + threadNamePrefix
                                                        + " - "
                                                        + this.counter.getAndIncrement());
                    if (createdThreads != null) {
                        createdThreads.add(thread);
                    }

                    return thread;
                }
            };

            final ListeningOrderedThreadPoolExecutor executor = new ListeningOrderedThreadPoolExecutor(poolSize,
                                                                                                       maxPoolSize,
                                                                                                       1L,
                                                                                                       TimeUnit.MINUTES, threadFactory ,
                                                                                                       autoCleanUpInerval) {
                protected BlockingQueue<Runnable> newChildExecutorWorkQueue() {

                   return new ArrayBlockingQueue<>(criticalQueueSize);

                }
            }; if (prestartAllCoreThreads) {
                executor.prestartAllCoreThreads();
            }

            return executor;
        }
    }

    public static ListeningExecutorService createListeningExecutor(final int poolSize,
                                                                   final long autoCleanUpInerval,
                                                                   final int criticalQueueSize,
                                                                   final String transportName,
                                                                   final String threadNamePrefix) {

        return createListeningExecutor(poolSize,
                                       poolSize,
                                       autoCleanUpInerval,
                                       criticalQueueSize,
                                       transportName,
                                       threadNamePrefix);
    }

    private static ListeningExecutorService createListeningExecutor(final int corePoolSize,
                                                                    final int maxPoolSize,
                                                                    final long autoCleanUpInerval,
                                                                    final int criticalQueueSize,
                                                                    final String transportName,
                                                                    final String threadNamePrefix) {

        return createExecutor(corePoolSize,
                              maxPoolSize,
                              autoCleanUpInerval,
                              criticalQueueSize,
                              threadNamePrefix,
                              null,
                              transportName,
                              true);
    }


    public static void shutdown(final ListeningExecutorService executor,
                          final long waitTime,
                          final TimeUnit waitTimeUnit) {

        executor.shutdown();

        try {
            if (!executor.awaitTermination(waitTime, waitTimeUnit)) {
                executor.shutdownNow();
            }
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        }

    }
}
