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

package org.softcake.yubari.netty.util;

import com.dukascopy.dds4.ping.StatsStruct;
import com.dukascopy.dds4.transport.common.protocol.mina.ISessionStats;
import com.dukascopy.dds4.transport.msg.system.ProtocolMessage;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class TransportSessionStats implements ISessionStats {
    private ConcurrentHashMap<Class<?>, StatsStruct> serverMsgDropped = new ConcurrentHashMap();
    private ConcurrentHashMap<Class<?>, StatsStruct> serverMsgInClientQueue = new ConcurrentHashMap();
    private ConcurrentHashMap<Class<?>, StatsStruct> serverMsgProcessing = new ConcurrentHashMap();

    public TransportSessionStats() {
    }

    public void messageExecutionStarted(long time, long interval, ProtocolMessage protocolMessage) {
        if (protocolMessage != null) {
            StatsStruct ss = this.get(protocolMessage.getClass(), this.serverMsgInClientQueue);
            ss.addValue((double)interval);
        }
    }

    public Map<Class<?>, StatsStruct> pollServerMsgInClientQueueStats() {
        Map<Class<?>, StatsStruct> result = new HashMap();
        result.putAll(this.serverMsgInClientQueue);
        this.serverMsgInClientQueue.clear();
        return result;
    }

    public void messageExecutionFinished(long time, long interval, ProtocolMessage protocolMessage) {
        if (protocolMessage != null) {
            StatsStruct ss = this.get(protocolMessage.getClass(), this.serverMsgProcessing);
            ss.addValue((double)interval);
        }
    }

    public Map<Class<?>, StatsStruct> pollServerMsgProcessingStats() {
        Map<Class<?>, StatsStruct> result = new HashMap();
        result.putAll(this.serverMsgProcessing);
        this.serverMsgProcessing.clear();
        return result;
    }

    private StatsStruct get(Class<?> clazz, ConcurrentHashMap<Class<?>, StatsStruct> map) {
        StatsStruct result = (StatsStruct)map.get(clazz);
        if (result != null) {
            return result;
        } else {
            StatsStruct ss = new StatsStruct();
            result = (StatsStruct)map.putIfAbsent(clazz, ss);
            return result == null ? ss : result;
        }
    }

    public void messageInExecutionQueue(long timestamp, ProtocolMessage message) {
    }

    public void reset() {
    }

    public void messageProcessingPostponeWarning(long time, long postponeInterval, ProtocolMessage message) {
    }

    public void messageProcessingPostponeError(long now, long postponeInterval, ProtocolMessage message) {
    }

    public void messageDropped(long currentTimeMillis, long interval, ProtocolMessage message) {
        if (message != null) {
            StatsStruct ss = this.get(message.getClass(), this.serverMsgDropped);
            ss.addValue((double)interval);
        }
    }

    public Map<Class<?>, StatsStruct> pollServerMsgDroppedStats() {
        Map<Class<?>, StatsStruct> result = new HashMap();
        result.putAll(this.serverMsgDropped);
        this.serverMsgDropped.clear();
        return result;
    }
}
