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

import java.util.Objects;

public class PingStats {
    private StatsStruct pingInterval = new StatsStruct();
    private StatsStruct initiatorSocketWriteInterval = new StatsStruct();
    private StatsStruct initiatorSystemCpu = new StatsStruct();
    private StatsStruct initiatorProcessCpu = new StatsStruct();
    private StatsStruct opponentSocketWriteInterval = new StatsStruct();
    private StatsStruct opponentSystemCpu = new StatsStruct();
    private StatsStruct opponentProcessCpu = new StatsStruct();

    public PingStats() {

    }

    public PingStats(PingStats stats) {

        this.pingInterval = new StatsStruct(stats.pingInterval);
        this.initiatorSocketWriteInterval = new StatsStruct(stats.initiatorSocketWriteInterval);
        this.opponentSocketWriteInterval = new StatsStruct(this.opponentSocketWriteInterval);
        this.opponentSystemCpu = new StatsStruct(this.opponentSystemCpu);
        this.opponentProcessCpu = new StatsStruct(this.opponentProcessCpu);
        this.initiatorSystemCpu = new StatsStruct(this.initiatorSystemCpu);
        this.initiatorProcessCpu = new StatsStruct(this.initiatorProcessCpu);
    }

    public StatsStruct getPingInterval() {

        return this.pingInterval;
    }

    public void setPingInterval(StatsStruct pingInterval) {

        this.pingInterval = pingInterval;
    }

    public StatsStruct getInitiatorSocketWriteInterval() {

        return this.initiatorSocketWriteInterval;
    }

    public void setInitiatorSocketWriteInterval(StatsStruct initiatorSocketWriteInterval) {

        this.initiatorSocketWriteInterval = initiatorSocketWriteInterval;
    }

    public StatsStruct getOpponentSocketWriteInterval() {

        return this.opponentSocketWriteInterval;
    }

    public void setOpponentSocketWriteInterval(StatsStruct opponentSocketWriteInterval) {

        this.opponentSocketWriteInterval = opponentSocketWriteInterval;
    }

    public StatsStruct getOpponentSystemCpu() {

        return this.opponentSystemCpu;
    }

    public void setOpponentSystemCpu(StatsStruct opponentSystemCpu) {

        this.opponentSystemCpu = opponentSystemCpu;
    }

    public StatsStruct getOpponentProcessCpu() {

        return this.opponentProcessCpu;
    }

    public void setOpponentProcessCpu(StatsStruct opponentProcessCpu) {

        this.opponentProcessCpu = opponentProcessCpu;
    }

    public StatsStruct getInitiatorSystemCpu() {

        return this.initiatorSystemCpu;
    }

    public void setInitiatorSystemCpu(StatsStruct initiatorSystemCpu) {

        this.initiatorSystemCpu = initiatorSystemCpu;
    }

    public StatsStruct getInitiatorProcessCpu() {

        return this.initiatorProcessCpu;
    }

    public void setInitiatorProcessCpu(StatsStruct initiatorProcessCpu) {

        this.initiatorProcessCpu = initiatorProcessCpu;
    }

    @Override
    public boolean equals(final Object o) {

        if (this == o) { return true; }
        if (o == null || getClass() != o.getClass()) { return false; }
        final PingStats pingStats = (PingStats) o;
        return Objects.equals(pingInterval, pingStats.pingInterval) &&
               Objects.equals(initiatorSocketWriteInterval, pingStats.initiatorSocketWriteInterval) &&
               Objects.equals(initiatorSystemCpu, pingStats.initiatorSystemCpu) &&
               Objects.equals(initiatorProcessCpu, pingStats.initiatorProcessCpu) &&
               Objects.equals(opponentSocketWriteInterval, pingStats.opponentSocketWriteInterval) &&
               Objects.equals(opponentSystemCpu, pingStats.opponentSystemCpu) &&
               Objects.equals(opponentProcessCpu, pingStats.opponentProcessCpu);
    }

    @Override
    public int hashCode() {

        return Objects.hash(pingInterval,
                            initiatorSocketWriteInterval,
                            initiatorSystemCpu,
                            initiatorProcessCpu,
                            opponentSocketWriteInterval,
                            opponentSystemCpu,
                            opponentProcessCpu);
    }

    @Override
    public String toString() {

        return "PingStats{" +
               "pingInterval=" + pingInterval +
               ", initiatorSocketWriteInterval=" + initiatorSocketWriteInterval +
               ", initiatorSystemCpu=" + initiatorSystemCpu +
               ", initiatorProcessCpu=" + initiatorProcessCpu +
               ", opponentSocketWriteInterval=" + opponentSocketWriteInterval +
               ", opponentSystemCpu=" + opponentSystemCpu +
               ", opponentProcessCpu=" + opponentProcessCpu +
               '}';
    }
}
