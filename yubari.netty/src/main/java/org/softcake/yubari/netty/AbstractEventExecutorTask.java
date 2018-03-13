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

import com.dukascopy.dds4.common.orderedExecutor.OrderedThreadPoolExecutor.OrderedRunnable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractEventExecutorTask implements OrderedRunnable {
    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractEventExecutorTask.class);
    private final TransportClientSession clientSession;
    private final ClientProtocolHandler clientProtocolHandler;
    private Runnable delayedExecutionTask;
    private long procStartTime;
    private long sleepTime = 10L;

    public AbstractEventExecutorTask(TransportClientSession clientSession, ClientProtocolHandler clientProtocolHandler) {
        this.clientSession = clientSession;
        this.clientProtocolHandler = clientProtocolHandler;
    }

    public void executeInExecutor(final ExecutorService executor) {
        try {
            executor.execute(this);
        } catch (RejectedExecutionException var3) {
            this.procStartTime = System.currentTimeMillis();
            this.delayedExecutionTask = new Runnable() {
                public void run() {
                    try {
                        executor.execute(this);
                    } catch (RejectedExecutionException var4) {
                        long currentTime = System.currentTimeMillis();
                        if (currentTime - AbstractEventExecutorTask.this.procStartTime > AbstractEventExecutorTask.this.clientSession.getEventExecutionErrorDelay()) {
                            AbstractEventExecutorTask.LOGGER.error("[" + AbstractEventExecutorTask.this.clientSession.getTransportName() + "] Event executor queue overloaded" + ", CRITICAL EXECUTION WAIT TIME: " + (currentTime - AbstractEventExecutorTask.this.procStartTime) + "ms, possible application problem or deadlock");
                            AbstractEventExecutorTask.this.clientProtocolHandler.checkAndLogEventPoolThreadDumps();
                            AbstractEventExecutorTask.this.procStartTime = currentTime;
                            AbstractEventExecutorTask.this.sleepTime = 50L;
                        }

                        AbstractEventExecutorTask.this.clientSession.getScheduledExecutorService().schedule(AbstractEventExecutorTask.this.delayedExecutionTask, AbstractEventExecutorTask.this.sleepTime, TimeUnit.MILLISECONDS);
                    }

                }
            };
            this.clientSession.getScheduledExecutorService().schedule(this.delayedExecutionTask, 5L, TimeUnit.MILLISECONDS);
        }

    }
}
