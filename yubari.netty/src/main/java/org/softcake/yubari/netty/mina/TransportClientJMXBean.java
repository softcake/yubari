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

import com.dukascopy.dds4.ping.PingManager;
import com.dukascopy.dds4.ping.PingStats;

import java.text.SimpleDateFormat;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class TransportClientJMXBean implements ITransportClientJMXBean {
    public static final TimeZone GMT_TIME_ZONE = TimeZone.getTimeZone("GMT 0");
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss:SSS");
    private final PingManager primPingManager;
    private final PingManager secPingManager;
    private final AtomicLong droppedByTransportClientMessageCounter;
    private final AtomicBoolean logEventPoolThreadDumpsOnLongExecution;

    public TransportClientJMXBean(PingManager primPingManager,
                                  PingManager secPingManager,
                                  AtomicLong droppedMessageCounter,
                                  AtomicBoolean logEventPoolThreadDumpsOnLongExecution) {

        this.primPingManager = primPingManager;
        this.secPingManager = secPingManager;
        this.droppedByTransportClientMessageCounter = droppedMessageCounter;
        this.logEventPoolThreadDumpsOnLongExecution = logEventPoolThreadDumpsOnLongExecution;
    }

    public long getPrimLastAveragePing() {

        PingStats latestPing = this.primPingManager.getLatestStats();
        return latestPing == null ? 0L : latestPing.getPingInterval().getRoundedAverage();

    }

    public long getPrimLastPingTime() {

        return this.primPingManager.getLastPingTime();
    }

    public String getPrimLastPingTimeStr() {

        long lastPingTime = this.getPrimLastPingTime();
        return DATE_FORMAT.format(lastPingTime);
    }

    public long getPrimLastPing() {

        PingStats ping = this.primPingManager.getGeneralStats();
       return ping == null ? 0L : ping.getPingInterval().getRoundedLast();

    }

    public long getPrimMinPing() {

        PingStats ping = this.primPingManager.getGeneralStats();
        return ping == null ? 0L : ping.getPingInterval().getRoundedMin();

    }

    public long getPrimMaxPing() {

        PingStats ping = this.primPingManager.getGeneralStats();
        return ping == null ? 0L : ping.getPingInterval().getRoundedMax();

    }

    public long getPrimTotalPingCount() {

        PingStats ping = this.primPingManager.getGeneralStats();
        return ping == null ? 0L : ping.getPingInterval().getCount();

    }

    public long getPrimFirstPing() {

        PingStats ping = this.primPingManager.getGeneralStats();
        return ping == null ? 0L : ping.getPingInterval().getRoundedFirst();

    }

    public long getPrimAveragePing() {

        PingStats ping = this.primPingManager.getGeneralStats();
        return ping == null ? 0L : ping.getPingInterval().getRoundedAverage();

    }

    public long getSecLastAveragePing() {

        PingStats latestPing = this.secPingManager.getLatestStats();
        return latestPing == null ? 0L : latestPing.getPingInterval().getRoundedAverage();

    }

    public long getSecLastPingTime() {

        return this.secPingManager.getLastPingTime();
    }

    public String getSecLastPingTimeStr() {

        long lastPingTime = this.getSecLastPingTime();
        return DATE_FORMAT.format(lastPingTime);
    }

    public long getSecLastPing() {

        PingStats ping = this.secPingManager.getGeneralStats();
        return ping == null ? 0L : ping.getPingInterval().getRoundedLast();

    }

    public long getSecMinPing() {

        PingStats ping = this.secPingManager.getGeneralStats();
        return ping == null ? 0L : ping.getPingInterval().getRoundedMin();

    }

    public long getSecMaxPing() {

        PingStats ping = this.secPingManager.getGeneralStats();
        return ping == null ? 0L : ping.getPingInterval().getRoundedMax();

    }

    public long getSecTotalPingCount() {

        PingStats ping = this.secPingManager.getGeneralStats();
        return ping == null ? 0L : ping.getPingInterval().getCount();

    }

    public long getSecFirstPing() {

        PingStats ping = this.secPingManager.getGeneralStats();
        return ping == null ? 0L : ping.getPingInterval().getRoundedFirst();

    }

    public long getSecAveragePing() {

        PingStats ping = this.secPingManager.getGeneralStats();
        return ping == null ? 0L : ping.getPingInterval().getRoundedAverage();

    }

    public long getDroppedByTransportClientMessageCount() {

        return this.droppedByTransportClientMessageCounter.get();
    }

    public void setLogEventPoolThreadDumpsOnLongExecution(boolean logEventPoolThreadDumpsOnLongExecution) {

        this.logEventPoolThreadDumpsOnLongExecution.set(logEventPoolThreadDumpsOnLongExecution);
    }

    public boolean getLogEventPoolThreadDumpsOnLongExecution() {

        return this.logEventPoolThreadDumpsOnLongExecution.get();
    }

    static {
        DATE_FORMAT.setTimeZone(GMT_TIME_ZONE);
    }
}
