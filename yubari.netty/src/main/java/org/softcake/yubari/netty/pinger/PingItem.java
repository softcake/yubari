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

import java.text.SimpleDateFormat;
import java.util.Objects;
import java.util.TimeZone;

public class PingItem {
    public static final TimeZone GMT_TIME_ZONE = TimeZone.getTimeZone("GMT 0");
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss:SSS");

    static {
        DATE_FORMAT.setTimeZone(GMT_TIME_ZONE);
    }

    private long pingTime;
    private Long pingInterval;
    private Long initiatorSocketWriteInterval;
    private Double initiatorSystemCpuLoad;
    private Double initiatorProcessCpuLoad;
    private Long opponentSocketWriteInterval;
    private Double opponentSystemCpuLoad;
    private Double opponentProcessCpuLoad;

    public PingItem() {

    }

    public PingItem(final long pingTime,
                    final Long pingInterval,
                    final Long initiatorSocketWriteInterval,
                    final Double initiatorSystemCpuLoad,
                    final Double initiatorProcessCpuLoad,
                    final Long opponentSocketWriteInterval,
                    final Double opponentSystemCpuLoad,
                    final Double opponentProcessCpuLoad) {

        this.pingTime = pingTime;
        this.pingInterval = pingInterval;
        this.initiatorSocketWriteInterval = initiatorSocketWriteInterval;
        this.initiatorSystemCpuLoad = initiatorSystemCpuLoad;
        this.initiatorProcessCpuLoad = initiatorProcessCpuLoad;
        this.opponentSocketWriteInterval = opponentSocketWriteInterval;
        this.opponentSystemCpuLoad = opponentSystemCpuLoad;
        this.opponentProcessCpuLoad = opponentProcessCpuLoad;
    }

    @Override
    public String toString() {

        return "PingItem{" +
               "pingTime=" + DATE_FORMAT.format(this.pingTime) +
               ", pingInterval=" + pingInterval +
               '}';
    }

    public long getPingTime() {

        return this.pingTime;
    }

    public void setPingTime(final long pingTime) {

        this.pingTime = pingTime;
    }

    public Long getPingInterval() {

        return this.pingInterval;
    }

    public void setPingInterval(final Long pingInterval) {

        this.pingInterval = pingInterval;
    }

    public Long getInitiatorSocketWriteInterval() {

        return this.initiatorSocketWriteInterval;
    }

    public void setInitiatorSocketWriteInterval(final Long initiatorSocketWriteInterval) {

        this.initiatorSocketWriteInterval = initiatorSocketWriteInterval;
    }

    public Double getInitiatorSystemCpuLoad() {

        return this.initiatorSystemCpuLoad;
    }

    public void setInitiatorSystemCpuLoad(final Double initiatorSystemCpuLoad) {

        this.initiatorSystemCpuLoad = initiatorSystemCpuLoad;
    }

    public Double getInitiatorProcessCpuLoad() {

        return this.initiatorProcessCpuLoad;
    }

    public void setInitiatorProcessCpuLoad(final Double initiatorProcessCpuLoad) {

        this.initiatorProcessCpuLoad = initiatorProcessCpuLoad;
    }

    public Long getOpponentSocketWriteInterval() {

        return this.opponentSocketWriteInterval;
    }

    public void setOpponentSocketWriteInterval(final Long opponentSocketWriteInterval) {

        this.opponentSocketWriteInterval = opponentSocketWriteInterval;
    }

    public Double getOpponentSystemCpuLoad() {

        return this.opponentSystemCpuLoad;
    }

    public void setOpponentSystemCpuLoad(final Double opponentSystemCpuLoad) {

        this.opponentSystemCpuLoad = opponentSystemCpuLoad;
    }

    public Double getOpponentProcessCpuLoad() {

        return this.opponentProcessCpuLoad;
    }

    public void setOpponentProcessCpuLoad(final Double opponentProcessCpuLoad) {

        this.opponentProcessCpuLoad = opponentProcessCpuLoad;
    }


    @Override
    public boolean equals(final Object o) {

        if (this == o) { return true; }
        if (o == null || getClass() != o.getClass()) { return false; }
        final PingItem pingItem = (PingItem) o;
        return pingTime == pingItem.pingTime &&
               Objects.equals(pingInterval, pingItem.pingInterval) &&
               Objects.equals(initiatorSocketWriteInterval, pingItem.initiatorSocketWriteInterval) &&
               Objects.equals(initiatorSystemCpuLoad, pingItem.initiatorSystemCpuLoad) &&
               Objects.equals(initiatorProcessCpuLoad, pingItem.initiatorProcessCpuLoad) &&
               Objects.equals(opponentSocketWriteInterval, pingItem.opponentSocketWriteInterval) &&
               Objects.equals(opponentSystemCpuLoad, pingItem.opponentSystemCpuLoad) &&
               Objects.equals(opponentProcessCpuLoad, pingItem.opponentProcessCpuLoad);
    }

    @Override
    public int hashCode() {

        return Objects.hash(pingTime,
                            pingInterval,
                            initiatorSocketWriteInterval,
                            initiatorSystemCpuLoad,
                            initiatorProcessCpuLoad,
                            opponentSocketWriteInterval,
                            opponentSystemCpuLoad,
                            opponentProcessCpuLoad);
    }
}
