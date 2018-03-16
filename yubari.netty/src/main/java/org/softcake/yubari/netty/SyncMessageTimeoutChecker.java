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

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class SyncMessageTimeoutChecker implements Runnable {
    private final RequestMessageTransportListenableFuture transportFuture;
    private final ScheduledExecutorService syncMessagesTimer;
    private final long timeoutTime;
    private final boolean doNotRestartTimerOnInProcessResponse;

    public SyncMessageTimeoutChecker(ScheduledExecutorService syncMessagesTimer, RequestMessageTransportListenableFuture transportFuture, long timeoutTime) {
        this(syncMessagesTimer, transportFuture, timeoutTime, false);
    }

    public SyncMessageTimeoutChecker(ScheduledExecutorService syncMessagesTimer, RequestMessageTransportListenableFuture transportFuture, long timeoutTime, boolean doNotRestartTimerOnInProcessResponse) {
        this.syncMessagesTimer = syncMessagesTimer;
        this.transportFuture = transportFuture;
        this.timeoutTime = timeoutTime;
        this.doNotRestartTimerOnInProcessResponse = doNotRestartTimerOnInProcessResponse;
    }

    public void run() {
        if (this.doNotRestartTimerOnInProcessResponse) {
            this.transportFuture.timeout();
        } else if (this.transportFuture.getInProcessResponseLastTime() + this.timeoutTime < System.currentTimeMillis()) {
            this.transportFuture.timeout();
        } else {
            long scheduleTime = System.currentTimeMillis() - this.transportFuture.getInProcessResponseLastTime() + this.timeoutTime;
            if (scheduleTime > 0L) {
                this.transportFuture.scheduleTimeoutCheck(this, scheduleTime, TimeUnit.MILLISECONDS);
            } else {
                this.transportFuture.timeout();
            }
        }

    }

    public ScheduledFuture<?> scheduleCheck() {
        return this.syncMessagesTimer.schedule(this, this.timeoutTime, TimeUnit.MILLISECONDS);
    }

    ScheduledFuture<?> scheduleCheck(long timeout, TimeUnit timeUnit) {
        assert !this.doNotRestartTimerOnInProcessResponse;

        return this.syncMessagesTimer.schedule(this, timeout, timeUnit);
    }
}
