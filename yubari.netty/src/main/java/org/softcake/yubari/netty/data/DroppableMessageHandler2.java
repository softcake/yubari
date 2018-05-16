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

package org.softcake.yubari.netty.data;

import org.softcake.cherry.core.base.PreCheck;
import org.softcake.yubari.netty.client.TransportClientSession;
import org.softcake.yubari.netty.map.MapHelper;

import com.dukascopy.dds4.transport.msg.system.CurrencyMarket;
import com.dukascopy.dds4.transport.msg.system.InstrumentableLowMessage;
import com.dukascopy.dds4.transport.msg.system.ProtocolMessage;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author The softcake authors
 */
public class DroppableMessageHandler2 {
    private final Map<Class<?>, Map<String, DroppableMessageScheduling>>
        lastScheduledDropableMessages
        = new ConcurrentHashMap<>();
    private final TransportClientSession clientSession;
    private final long droppableMessagesClientTTL;

    public DroppableMessageHandler2(final TransportClientSession clientSession) {

        this.clientSession = PreCheck.notNull(clientSession, "clientSession");
        this.droppableMessagesClientTTL = clientSession.getDroppableMessagesClientTTL();
    }

    private DroppableMessageScheduling getDroppableScheduling(final ProtocolMessage message, final String instrument) {

        final Class<?> clazz = message.getClass();
        Map<String, DroppableMessageScheduling>
            lastScheduledMessagesMap
            = this.lastScheduledDropableMessages.get(clazz);
        if (lastScheduledMessagesMap == null) {
            lastScheduledMessagesMap = MapHelper.getAndPutIfAbsent(this.lastScheduledDropableMessages,
                                                                   clazz,
                                                                   ConcurrentHashMap::new);
        }

        DroppableMessageScheduling lastScheduledMessageInfo = lastScheduledMessagesMap.get(instrument);
        if (lastScheduledMessageInfo == null) {
            lastScheduledMessageInfo = MapHelper.getAndPutIfAbsent(lastScheduledMessagesMap,
                                                                   instrument,
                                                                   DroppableMessageScheduling::new);
        }

        return lastScheduledMessageInfo;
    }

    public void setCurrentDroppableMessageTime(final ProtocolMessage message) {
        //TODO
     /*   if (this.clientSession.isSkipDroppableMessages()) {
            return;
        }*/
        final String instrument = getInstrumentIfMessageIsDroppable(message);
        if (instrument.isEmpty()) { return; }

        final Long currentInstrumentableMessageTime = getMessageCreationTime(message);

        if (currentInstrumentableMessageTime > 0L) {
            final DroppableMessageScheduling scheduling = this.getDroppableScheduling(message, instrument);
            scheduling.scheduled(currentInstrumentableMessageTime);
        }
    }


    private String getInstrumentIfMessageIsDroppable(final ProtocolMessage message) {

        if (!(message instanceof InstrumentableLowMessage)) {
            return "";
        }

        final InstrumentableLowMessage instrumentable = (InstrumentableLowMessage) message;
        final String instrument = instrumentable.getInstrument();

        if (instrument == null || !instrumentable.isDropOnTimeout()) {
            return "";
        }
        return instrument;
    }

    private long getMessageCreationTime(final ProtocolMessage message) {

        final String instrument = getInstrumentIfMessageIsDroppable(message);
        if (instrument.isEmpty()) { return 0L; }

        return message instanceof CurrencyMarket
               ? ((CurrencyMarket) message).getCreationTimestamp()
               : message.getTimestamp();


    }

    public boolean canProcessDroppableMessage(final ProtocolMessage message) {
/*   if (this.clientSession.isSkipDroppableMessages()) {
            return true;
        }*/
        final long inProcessMessageCreationTime = getMessageCreationTime(message);
        if (inProcessMessageCreationTime <= 0L) {return true;}


        final InstrumentableLowMessage msg = (InstrumentableLowMessage) message;
        final String instrument = msg.getInstrument();


        final DroppableMessageScheduling scheduling = this.getDroppableScheduling(message, instrument);
        final long lastArrivedMessageTime = scheduling.getLastScheduledTime();
        final int scheduledCount = scheduling.getScheduledCount();
        scheduling.executed();
        long diff = lastArrivedMessageTime - inProcessMessageCreationTime;
        //long diff = inProcessMessageCreationTime - lastArrivedMessageTime;
        return diff <= this.droppableMessagesClientTTL;
             // || scheduledCount <= 1;

    }

}
