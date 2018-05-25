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

package org.softcake.yubari.netty.mbean;


import org.softcake.yubari.netty.pinger.PingManager;
import org.softcake.yubari.netty.pinger.PingStats;


import java.text.SimpleDateFormat;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class TransportClientMBean implements ITransportClientMBean {
    private static final TimeZone GMT_TIME_ZONE = TimeZone.getTimeZone("GMT 0");
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss:SSS");
    private final PingManager primPingManager;
    private final PingManager secPingManager;
    private final AtomicLong droppedByTransportClientMessageCounter;
    private final AtomicBoolean logEventPoolThreadDumpsOnLongExecution;

    public TransportClientMBean(final PingManager primPingManager,
                                final PingManager secPingManager,
                                final AtomicLong droppedMessageCounter,
                                final AtomicBoolean logEventPoolThreadDumpsOnLongExecution) {

        this.primPingManager = primPingManager;
        this.secPingManager = secPingManager;
        this.droppedByTransportClientMessageCounter = droppedMessageCounter;
        this.logEventPoolThreadDumpsOnLongExecution = logEventPoolThreadDumpsOnLongExecution;
    }

    public long getPrimLastAveragePing() {

        final PingStats latestPing = this.primPingManager.getLatestStats();
        return latestPing == null ? 0L : latestPing.getPingInterval().getRoundedAverage();

    }

    public long getPrimLastPingTime() {

        return this.primPingManager.getLastPingTime();
    }

    public String getPrimLastPingTimeStr() {

        final long lastPingTime = this.getPrimLastPingTime();
        return DATE_FORMAT.format(lastPingTime);
    }

    public long getPrimLastPing() {

        final PingStats ping = this.primPingManager.getGeneralStats();
       return ping == null ? 0L : ping.getPingInterval().getRoundedLast();

    }

    public long getPrimMinPing() {

        final PingStats ping = this.primPingManager.getGeneralStats();
        return ping == null ? 0L : ping.getPingInterval().getRoundedMin();

    }

    public long getPrimMaxPing() {

        final PingStats ping = this.primPingManager.getGeneralStats();
        return ping == null ? 0L : ping.getPingInterval().getRoundedMax();

    }

    public long getPrimTotalPingCount() {

        final PingStats ping = this.primPingManager.getGeneralStats();
        return ping == null ? 0L : ping.getPingInterval().getCount();

    }

    public long getPrimFirstPing() {

        final PingStats ping = this.primPingManager.getGeneralStats();
        return ping == null ? 0L : ping.getPingInterval().getRoundedFirst();

    }

    public long getPrimAveragePing() {

        final PingStats ping = this.primPingManager.getGeneralStats();
        return ping == null ? 0L : ping.getPingInterval().getRoundedAverage();

    }

    public long getSecLastAveragePing() {

        final PingStats latestPing = this.secPingManager.getLatestStats();
        return latestPing == null ? 0L : latestPing.getPingInterval().getRoundedAverage();

    }

    public long getSecLastPingTime() {

        return this.secPingManager.getLastPingTime();
    }

    public String getSecLastPingTimeStr() {

        final long lastPingTime = this.getSecLastPingTime();
        return DATE_FORMAT.format(lastPingTime);
    }

    public long getSecLastPing() {

        final PingStats ping = this.secPingManager.getGeneralStats();
        return ping == null ? 0L : ping.getPingInterval().getRoundedLast();

    }

    public long getSecMinPing() {

        final PingStats ping = this.secPingManager.getGeneralStats();
        return ping == null ? 0L : ping.getPingInterval().getRoundedMin();

    }

    public long getSecMaxPing() {

        final PingStats ping = this.secPingManager.getGeneralStats();
        return ping == null ? 0L : ping.getPingInterval().getRoundedMax();

    }

    public long getSecTotalPingCount() {

        final PingStats ping = this.secPingManager.getGeneralStats();
        return ping == null ? 0L : ping.getPingInterval().getCount();

    }

    public long getSecFirstPing() {

        final PingStats ping = this.secPingManager.getGeneralStats();
        return ping == null ? 0L : ping.getPingInterval().getRoundedFirst();

    }

    public long getSecAveragePing() {

        final PingStats ping = this.secPingManager.getGeneralStats();
        return ping == null ? 0L : ping.getPingInterval().getRoundedAverage();

    }

    public long getDroppedByTransportClientMessageCount() {

        return this.droppedByTransportClientMessageCounter.get();
    }

    public void setLogEventPoolThreadDumpsOnLongExecution(final boolean logEventPoolThreadDumpsOnLongExecution) {

        this.logEventPoolThreadDumpsOnLongExecution.set(logEventPoolThreadDumpsOnLongExecution);
    }

    public boolean getLogEventPoolThreadDumpsOnLongExecution() {

        return this.logEventPoolThreadDumpsOnLongExecution.get();
    }

    static {
        DATE_FORMAT.setTimeZone(GMT_TIME_ZONE);
    }
}
