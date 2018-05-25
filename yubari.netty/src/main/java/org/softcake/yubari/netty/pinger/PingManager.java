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


import com.dukascopy.dds4.ping.PingStats;
import com.dukascopy.dds4.ping.StatsStruct;
import com.sun.management.OperatingSystemMXBean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class PingManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(PingManager.class);
    private final long statisticsInterval;
    private final List<PingItem> pings = new ArrayList<>();
    private final Object SYNCH = new Object();
    private OperatingSystemMXBean operatingSystemMXBean;
    private PingStats generalStats;
    private long lastPingTime;


    public PingManager(final long statisticsInterval) {

        this.statisticsInterval = statisticsInterval;
        if (statisticsInterval <= 0L) {
            throw new IllegalArgumentException("param must be positivie " + statisticsInterval);
        }

        final ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
        if (!threadMXBean.isThreadCpuTimeSupported()) {
            LOGGER.info("Thread CPU time monitoring is not supported");
        } else {
            threadMXBean.setThreadCpuTimeEnabled(true);
            this.operatingSystemMXBean
                = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
        }


    }

    public Double getSystemCpuLoad() {


        final double systemCpuLoad = this.operatingSystemMXBean.getSystemCpuLoad();
        if (systemCpuLoad < 0.0D) {
            return null;
        }

        return systemCpuLoad;


    }

    public Double getProcessCpuLoad() {


        final double
            processCpuLoad
            = this.operatingSystemMXBean.getProcessCpuLoad();
        if (processCpuLoad < 0.0D) {
            return null;
        }

        return processCpuLoad;

    }

    public Integer getAvailableProcessors() {


        final int
            cores
            = this.operatingSystemMXBean.getAvailableProcessors();
        if (cores < 0) {
            return null;
        }

        return cores;

    }


    public void pingFailed() {

        this.addPing(null);
    }

    public void addPing(final Long pingInterval) {

        this.addPing(pingInterval, null);
    }

    public void addPing(final Long pingInterval, final Long initiatorSocketWriteInterval) {

        this.addPing(pingInterval,
                     initiatorSocketWriteInterval,
                     null,
                     null,
                     null,
                     null,
                     null);
    }

    public void addPing(final Long pingInterval,
                        final Long initiatorSocketWriteInterval,
                        final Double initiatorSystemCpuLoad,
                        final Double initiatorProcessCpuLoad,
                        final Long opponentSocketWriteInterval,
                        final Double opponentSystemCpuLoad,
                        final Double opponentProcessCpuLoad) {

        final long currentTime = System.currentTimeMillis();
        this.lastPingTime = currentTime;

        synchronized (this.SYNCH) {
            this.cleanUpOldItems(currentTime, this.statisticsInterval);
            final PingItem pi = new PingItem(currentTime,
                                             pingInterval,
                                             initiatorSocketWriteInterval,
                                             initiatorSystemCpuLoad,
                                             initiatorProcessCpuLoad,
                                             opponentSocketWriteInterval,
                                             opponentSystemCpuLoad,
                                             opponentProcessCpuLoad);
            this.pings.add(pi);
            this.generalStats = this.handleNewPing(pi, this.generalStats);
        }
    }

    private PingStats handleNewPing(final PingItem pi, PingStats stats) {

        final double pingInterval = (double) this.getValue(pi.getPingInterval(), 654321L);
        final double initiatorSocketWriteInterval = (double) this.getValue(pi.getInitiatorSocketWriteInterval(),
                                                                           654321L);
        final Double initiatorSystemCpuLoad = pi.getInitiatorSystemCpuLoad();
        final Double initiatorProcessCpuLoad = pi.getInitiatorProcessCpuLoad();
        final Long opponentSocketWriteInterval = pi.getOpponentSocketWriteInterval();
        final Double opponentSystemCpuLoad = pi.getOpponentSystemCpuLoad();
        final Double opponentProcessCpuLoad = pi.getOpponentProcessCpuLoad();
        if (stats == null) {
            stats = new PingStats();
        }

        this.addStats(stats.getPingInterval(), pingInterval);
        this.addStats(stats.getInitiatorSocketWriteInterval(), initiatorSocketWriteInterval);
        this.addStats(stats.getInitiatorSystemCpu(), initiatorSystemCpuLoad);
        this.addStats(stats.getInitiatorProcessCpu(), initiatorProcessCpuLoad);
        this.addStats(stats.getOpponentSocketWriteInterval(), opponentSocketWriteInterval);
        this.addStats(stats.getOpponentSystemCpu(), opponentSystemCpuLoad);
        this.addStats(stats.getOpponentProcessCpu(), opponentProcessCpuLoad);
        return stats;
    }

    private long getValue(final Long value, final long valueIfNull) {

        return value != null ? value : valueIfNull;
    }

    private void addStats(final StatsStruct ss, final Long value) {

        if (value != null) {
            ss.addValue((double) value);
        }

    }

    private void addStats(final StatsStruct ss, final Double value) {

        if (value != null) {
            ss.addValue(value);
        }

    }

    private void cleanUpOldItems(final long currentTime, final long statisticsInterval) {

        final Iterator<PingItem> iterator = this.pings.iterator();

        while (iterator.hasNext()) {
            final PingItem pingItem = iterator.next();
            if (pingItem.getPingTime() >= currentTime - statisticsInterval) {
                break;
            }

            iterator.remove();
        }

    }

    public PingStats getGeneralStats() {

        return this.generalStats == null ? null : new PingStats(this.generalStats);
    }

    public PingStats getLatestStats() {


        final ArrayList pingsCopy;
        synchronized (this.SYNCH) {
            this.cleanUpOldItems(System.currentTimeMillis(), this.statisticsInterval);
            pingsCopy = new ArrayList<>(this.pings);
        }

        PingStats result = null;

        PingItem pi;
        for (final Iterator<PingItem> i$ = pingsCopy.iterator(); i$.hasNext(); result = this.handleNewPing(pi, result)) {
            pi = i$.next();
        }

        return result;
    }

    public long getLastPingTime() {

        return this.lastPingTime;
    }

    public void reset() {
        synchronized (this.SYNCH) {
            this.pings.clear();
            this.generalStats = null;
            this.lastPingTime = 0L;
            this.operatingSystemMXBean = null;
        }
    }
}
